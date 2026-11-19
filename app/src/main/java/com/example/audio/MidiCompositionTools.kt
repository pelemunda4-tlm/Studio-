package com.example.audio

import com.example.model.MidiNote
import com.example.model.MusicalScale
import com.example.model.SampleSlice
import kotlin.math.abs
import kotlin.math.round
import kotlin.random.Random

object MidiCompositionTools {

    private val NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    fun midiToNoteName(midi: Int): String {
        val name = NOTE_NAMES[((midi % 12) + 12) % 12]
        val octave = (midi / 12) - 1
        return "$name$octave"
    }

    fun isPitchInScale(pitch: Int, rootKey: String, scale: MusicalScale): Boolean {
        val rootIdx = NOTE_NAMES.indexOf(rootKey).takeIf { it >= 0 } ?: 0
        val pc = ((pitch - rootIdx) % 12 + 12) % 12
        return pc in scale.intervals
    }

    fun detectChordFromPitches(pitches: List<Int>): String {
        if (pitches.isEmpty()) return "No Chord"
        val uniquePc = pitches.map { ((it % 12) + 12) % 12 }.distinct().sorted()
        if (uniquePc.size == 1) return NOTE_NAMES[uniquePc.first()]

        // Check 4-note chords first across all candidate roots
        for (root in uniquePc) {
            val rel = uniquePc.map { (it - root + 12) % 12 }.toSet()
            val name = NOTE_NAMES[root]
            when {
                rel.containsAll(setOf(0, 4, 7, 11)) -> return "${name}maj7"
                rel.containsAll(setOf(0, 3, 7, 10)) -> return "${name}m7"
                rel.containsAll(setOf(0, 4, 7, 10)) -> return "${name}7"
            }
        }
        // Fallback to triads
        for (root in uniquePc) {
            val rel = uniquePc.map { (it - root + 12) % 12 }.toSet()
            val name = NOTE_NAMES[root]
            when {
                rel.containsAll(setOf(0, 4, 7)) -> return name
                rel.containsAll(setOf(0, 3, 7)) -> return "${name}m"
                rel.containsAll(setOf(0, 5, 7)) -> return "${name}sus4"
                rel.containsAll(setOf(0, 2, 7)) -> return "${name}sus2"
                rel.containsAll(setOf(0, 3, 6)) -> return "${name}dim"
            }
        }
        return "${NOTE_NAMES[uniquePc.first()]}5 / Dyad"
    }

    fun quantizeNotes(notes: List<MidiNote>, gridBeats: Double = 0.25): List<MidiNote> {
        if (gridBeats <= 0.0) return notes
        return notes.map { n ->
            val qStart = round(n.startBeat / gridBeats) * gridBeats
            val qDur = maxOf(gridBeats, round(n.durationBeats / gridBeats) * gridBeats)
            n.copy(startBeat = qStart.coerceAtLeast(0.0), durationBeats = qDur)
        }
    }

    fun humanizeNotes(notes: List<MidiNote>, timingJitterBeats: Double = 0.04, velocityJitter: Int = 14): List<MidiNote> {
        val rng = Random(System.currentTimeMillis())
        return notes.map { n ->
            val dStart = (rng.nextDouble() * 2.0 - 1.0) * timingJitterBeats
            val dVel = rng.nextInt(-velocityJitter, velocityJitter + 1)
            n.copy(
                startBeat = (n.startBeat + dStart).coerceAtLeast(0.0),
                velocity = (n.velocity + dVel).coerceIn(20, 127)
            )
        }
    }

    fun randomizeVelocities(notes: List<MidiNote>, minVel: Int = 64, maxVel: Int = 122): List<MidiNote> {
        val rng = Random(System.currentTimeMillis())
        return notes.map { it.copy(velocity = rng.nextInt(minVel, maxVel + 1).coerceIn(1, 127)) }
    }

    fun arpeggiateNotes(notes: List<MidiNote>, stepBeats: Double = 0.25, upDown: Boolean = true): List<MidiNote> {
        if (notes.isEmpty()) return emptyList()
        val sortedPitches = notes.map { it.pitch }.distinct().sorted()
        val pattern = if (upDown && sortedPitches.size > 2) {
            sortedPitches + sortedPitches.dropLast(1).drop(1).reversed()
        } else sortedPitches
        val baseStart = notes.minOf { it.startBeat }
        val count = maxOf(notes.size, pattern.size * 2)
        return List(count) { idx ->
            MidiNote(
                id = "arp_${System.nanoTime()}_$idx",
                pitch = pattern[idx % pattern.size],
                startBeat = baseStart + idx * stepBeats,
                durationBeats = stepBeats * 0.9,
                velocity = if (idx % pattern.size == 0) 112 else 90
            )
        }
    }

    fun autoSliceTransients(samples: FloatArray, threshold: Float = 0.28f, maxSlices: Int = 16): List<SampleSlice> {
        if (samples.size < 512) return emptyList()
        val slicePoints = mutableListOf(0)
        val minGap = (samples.size / maxSlices).coerceAtLeast(512)
        var lastIdx = 0
        var i = 128
        while (i < samples.size - 128 && slicePoints.size < maxSlices) {
            if (i - lastIdx >= minGap && abs(samples[i]) > threshold) {
                slicePoints.add(i)
                lastIdx = i
                i += minGap / 2
            }
            i += 32
        }
        return slicePoints.mapIndexed { idx, start ->
            val end = slicePoints.getOrNull(idx + 1) ?: samples.size
            SampleSlice(
                id = "sl_$idx",
                index = idx,
                startSample = start,
                endSample = end,
                label = "Chop ${idx + 1}"
            )
        }
    }
}
