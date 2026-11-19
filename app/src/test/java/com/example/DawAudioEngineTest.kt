package com.example

import com.example.audio.*
import com.example.data.ProjectSerializer
import com.example.model.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class DawAudioEngineTest {

    @Test
    fun all28Plugins_processStereoAudioWithoutNaN() {
        assertEquals(28, PluginType.entries.size)
        val sr = 48000
        val frames = 512

        for (type in PluginType.entries) {
            val left = FloatArray(frames) { i -> sin(2.0 * PI * 440.0 * i / sr).toFloat() * 0.5f }
            val right = FloatArray(frames) { i -> sin(2.0 * PI * 440.0 * i / sr).toFloat() * 0.5f }
            val sidechain = FloatArray(frames) { 0.25f }
            val instance = PluginCatalog.createInstance("test_${type.name}", type)
            val processor = UniversalPluginProcessor(type)

            val (inDb, outDb) = processor.processStereo(left, right, frames, sr, instance, sidechain)
            assertFalse("NaN input meter for $type", inDb.isNaN())
            assertFalse("NaN output meter for $type", outDb.isNaN())
            assertTrue("Non-finite sample output for $type", left.all { it.isFinite() } && right.all { it.isFinite() })
        }
    }

    @Test
    fun all13IndianInstruments_synthesizeAudibleSignal() {
        assertEquals(13, IndianInstrument.entries.size)
        val sr = 48000
        for (inst in IndianInstrument.entries) {
            val (l, r) = IndianInstrumentSynth.synthesizeInstrumentHit(inst, sr, velocity = 0.9f)
            assertTrue("${inst.displayName} returned empty buffer", l.isNotEmpty() && l.size == r.size)
            val peak = l.maxOf { abs(it) }
            assertTrue("${inst.displayName} peak too quiet: $peak", peak > 0.02f)
        }
    }

    @Test
    fun multitrackEngine_rendersWith64BitSummingPdcAndMastering() {
        val engine = MultitrackAudioEngine(context = null)
        engine.set64BitSumming(true)
        val proj = ProjectSerializer.defaultProject()
        val pat = ProjectSerializer.createPresetPatterns().first()

        // Trigger a Sambalpuri Mandar voice + render audio blocks
        engine.triggerIndianInstrument(IndianInstrument.SAMBALPURI_MANDAR, 0.95f)
        val outL = FloatArray(512)
        val outR = FloatArray(512)
        val snapshot = engine.renderAudioBlock(proj, pat, 0L, 512, outL, outR, offlineMode = true)

        assertTrue("Master output should have audio signal", outL.any { abs(it) > 0.001f })
        assertTrue("True peak should be finite", snapshot.truePeakDbTp.isFinite())
        assertTrue("Correlation in valid range", snapshot.correlation in -1f..1f)

        // Freeze + Bounce verification
        val trackId = proj.tracks.first().id
        assertTrue(engine.freezeTrack(proj, trackId))
        val (bounceL, bounceR) = engine.bounceProjectToStereo(proj, pat, bars = 1)
        assertTrue(bounceL.isNotEmpty() && bounceL.size == bounceR.size)
    }

    @Test
    fun wavCodec_roundTrip16BitAnd32Float() {
        val sr = 48000
        val left = FloatArray(1024) { i -> sin(2.0 * PI * 330.0 * i / sr).toFloat() * 0.6f }
        val right = FloatArray(1024) { i -> sin(2.0 * PI * 495.0 * i / sr).toFloat() * 0.4f }

        val wav16 = WavAiffCodec.encodeWavBytes(left, right, sr, float32 = false)
        val dec16 = WavAiffCodec.decodeAudioStream(ByteArrayInputStream(wav16))
        assertNotNull(dec16)
        assertEquals(sr, dec16!!.sampleRate)
        assertEquals(1024, dec16.left.size)

        val wav32 = WavAiffCodec.encodeWavBytes(left, right, sr, float32 = true)
        val dec32 = WavAiffCodec.decodeAudioStream(ByteArrayInputStream(wav32))
        assertNotNull(dec32)
        assertEquals("WAV 32f", dec32!!.formatLabel)
        assertEquals(left[250], dec32.left[250], 0.001f)
    }

    @Test
    fun pitchDetector_detects440HzWithinTolerance() {
        val sr = 48000
        val buf = FloatArray(2048) { i -> sin(2.0 * PI * 440.0 * i / sr).toFloat() * 0.7f }
        val hz = PitchAndAnalysisDsp.detectPitchHz(buf, sr)
        assertTrue("Expected ~440 Hz, got $hz", hz in 434f..446f)
    }

    @Test
    fun midiTools_chordsScalesQuantizeAndArpeggiatorWork() {
        assertEquals("C", MidiCompositionTools.detectChordFromPitches(listOf(60, 64, 67)))
        assertEquals("Am7", MidiCompositionTools.detectChordFromPitches(listOf(57, 60, 64, 67)))
        assertTrue(MidiCompositionTools.isPitchInScale(66, "C", MusicalScale.RAGA_YAMAN)) // F# (Tivra Ma)

        val unquantized = listOf(MidiNote("n1", 60, 0.22, 0.48, 100))
        val quantized = MidiCompositionTools.quantizeNotes(unquantized, 0.25)
        assertEquals(0.25, quantized.first().startBeat, 0.001)

        val arp = MidiCompositionTools.arpeggiateNotes(
            listOf(MidiNote("a", 60, 0.0, 1.0), MidiNote("b", 64, 0.0, 1.0), MidiNote("c", 67, 0.0, 1.0))
        )
        assertTrue(arp.size >= 4)
    }
}
