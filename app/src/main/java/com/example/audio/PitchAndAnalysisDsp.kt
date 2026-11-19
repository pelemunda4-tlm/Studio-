package com.example.audio

import com.example.model.MidiNote
import com.example.model.MusicalScale
import com.example.model.PitchPoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class AudioAnalysisReport(
    val detectedBpm: Double,
    val detectedKey: String,
    val detectedScale: MusicalScale,
    val peakDb: Float,
    val rmsDb: Float,
    val integratedLufs: Float,
    val noiseFloorDb: Float,
    val transcribedMidiNotes: List<MidiNote>
)

object PitchAndAnalysisDsp {

    private val NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    /**
     * YIN fundamental frequency estimator (F0 in Hz), accurate across 60 Hz .. 1100 Hz vocals/instruments.
     */
    fun detectPitchHz(frame: FloatArray, sampleRate: Int): Float {
        val n = frame.size
        if (n < 256) return 0f
        var energy = 0f
        for (s in frame) energy += s * s
        val rms = sqrt(energy / n)
        if (rms < 0.012f) return 0f

        val half = n / 2
        val minTau = (sampleRate / 1100).coerceAtLeast(16)
        val maxTau = (sampleRate / 65).coerceAtMost(half - 2)
        if (maxTau <= minTau) return 0f

        val diff = FloatArray(maxTau + 1)
        for (tau in 1..maxTau) {
            var sum = 0f
            for (j in 0 until half) {
                val d = frame[j] - frame[j + tau]
                sum += d * d
            }
            diff[tau] = sum
        }

        val cmnd = FloatArray(maxTau + 1)
        cmnd[0] = 1f
        var runningSum = 0f
        for (tau in 1..maxTau) {
            runningSum += diff[tau]
            cmnd[tau] = if (runningSum > 1e-9f) diff[tau] * tau / runningSum else 1f
        }

        val threshold = 0.16f
        var bestTau = -1
        for (tau in minTau until maxTau) {
            if (cmnd[tau] < threshold) {
                var t = tau
                while (t + 1 < maxTau && cmnd[t + 1] < cmnd[t]) {
                    t++
                }
                bestTau = t
                break
            }
        }
        if (bestTau < 0) {
            var minVal = Float.MAX_VALUE
            for (tau in minTau until maxTau) {
                if (cmnd[tau] < minVal) {
                    minVal = cmnd[tau]
                    bestTau = tau
                }
            }
            if (minVal > 0.38f) return 0f
        }

        val betterTau = if (bestTau in (minTau + 1) until (maxTau - 1)) {
            val s0 = cmnd[bestTau - 1]
            val s1 = cmnd[bestTau]
            val s2 = cmnd[bestTau + 1]
            val denom = 2f * (2f * s1 - s2 - s0)
            if (abs(denom) > 1e-6f) bestTau + (s2 - s0) / denom else bestTau.toFloat()
        } else {
            bestTau.toFloat()
        }

        return if (betterTau > 0f) sampleRate / betterTau else 0f
    }

    fun hzToMidiFloat(hz: Float): Float {
        if (hz <= 0f) return 0f
        return (69.0 + 12.0 * (ln(hz.toDouble() / 440.0) / ln(2.0))).toFloat()
    }

    fun midiToHz(midi: Float): Float {
        return (440.0 * 2.0.pow(((midi - 69f) / 12f).toDouble())).toFloat()
    }

    fun analyzePitchContour(
        samples: FloatArray,
        sampleRate: Int,
        rootMidi: Int = 0,
        scale: MusicalScale = MusicalScale.MAJOR
    ): List<PitchPoint> {
        val windowSize = 2048
        val hop = 1024
        if (samples.size < windowSize) return emptyList()
        val points = mutableListOf<PitchPoint>()
        val window = FloatArray(windowSize)
        var idx = 0
        while (idx + windowSize <= samples.size) {
            System.arraycopy(samples, idx, window, 0, windowSize)
            val f0 = detectPitchHz(window, sampleRate)
            val timeSec = idx.toFloat() / sampleRate
            if (f0 > 0f) {
                val detectedMidi = hzToMidiFloat(f0)
                val targetMidi = snapMidiToScale(detectedMidi, rootMidi % 12, scale).toFloat()
                points.add(PitchPoint(timeSec, detectedMidi, targetMidi, 0f, true))
            } else {
                points.add(PitchPoint(timeSec, 60f, 60f, 0f, false))
            }
            idx += hop
        }
        return points
    }

    fun snapMidiToScale(midiFloat: Float, rootPitchClass: Int, scale: MusicalScale): Int {
        val rounded = midiFloat.roundToInt()
        val pitchClass = ((rounded - rootPitchClass) % 12 + 12) % 12
        if (pitchClass in scale.intervals) return rounded
        var bestNote = rounded
        var bestDist = 12
        for (offset in -6..6) {
            val candidate = rounded + offset
            val pc = ((candidate - rootPitchClass) % 12 + 12) % 12
            if (pc in scale.intervals && abs(offset) < bestDist) {
                bestDist = abs(offset)
                bestNote = candidate
            }
        }
        return bestNote.coerceIn(21, 108)
    }

    fun estimateBpm(samples: FloatArray, sampleRate: Int): Double {
        if (samples.size < sampleRate) return 120.0
        val hop = 512
        val numFrames = samples.size / hop
        if (numFrames < 16) return 120.0
        val onset = FloatArray(numFrames)
        var prevEnergy = 0f
        for (f in 0 until numFrames) {
            var e = 0f
            val base = f * hop
            for (k in 0 until hop) {
                val s = samples[base + k]
                e += s * s
            }
            onset[f] = max(0f, e - prevEnergy)
            prevEnergy = e
        }
        val framesPerSec = sampleRate.toDouble() / hop
        val minLag = (framesPerSec * 60.0 / 185.0).toInt().coerceAtLeast(2)
        val maxLag = (framesPerSec * 60.0 / 65.0).toInt().coerceAtMost(numFrames / 2)
        if (maxLag <= minLag) return 120.0

        var bestLag = minLag
        var bestScore = -1.0
        for (lag in minLag..maxLag) {
            var corr = 0.0
            for (i in 0 until numFrames - lag) {
                corr += onset[i] * onset[i + lag]
            }
            if (corr > bestScore) {
                bestScore = corr
                bestLag = lag
            }
        }
        val rawBpm = 60.0 * framesPerSec / bestLag
        return ((rawBpm * 2.0).roundToInt() / 2.0).coerceIn(60.0, 185.0)
    }

    fun analyzeAudioBuffer(samples: FloatArray, sampleRate: Int): AudioAnalysisReport {
        val contour = analyzePitchContour(samples, sampleRate)
        val bpm = estimateBpm(samples, sampleRate)
        val chroma = FloatArray(12)
        var peak = 0f
        var sumSq = 0.0
        var minBlockRms = Float.MAX_VALUE

        val block = 512
        var i = 0
        while (i + block <= samples.size) {
            var bSq = 0.0
            for (k in 0 until block) {
                val s = samples[i + k]
                val a = abs(s)
                if (a > peak) peak = a
                sumSq += s * s
                bSq += s * s
            }
            val bRms = sqrt(bSq / block).toFloat()
            if (bRms > 1e-6f && bRms < minBlockRms) minBlockRms = bRms
            i += block
        }

        val rms = if (samples.isNotEmpty()) sqrt(sumSq / samples.size).toFloat() else 0f
        for (pt in contour) {
            if (pt.voiced) {
                val pc = ((pt.detectedMidi.roundToInt() % 12) + 12) % 12
                chroma[pc] += 1f
            }
        }
        val rootIdx = chroma.indices.maxByOrNull { chroma[it] } ?: 0
        val minorScore = chroma[(rootIdx + 3) % 12]
        val majorScore = chroma[(rootIdx + 4) % 12]
        val scale = if (minorScore > majorScore) MusicalScale.NATURAL_MINOR else MusicalScale.MAJOR

        val midiNotes = contourToMidiNotes(contour, bpm)
        val peakDb = linearToDb(peak)
        val rmsDb = linearToDb(rms)
        val lufs = (rmsDb - 0.691f).coerceIn(-70f, 0f)
        val noiseFloorDb = if (minBlockRms < Float.MAX_VALUE) linearToDb(minBlockRms) else -65f

        return AudioAnalysisReport(
            detectedBpm = bpm,
            detectedKey = NOTE_NAMES[rootIdx],
            detectedScale = scale,
            peakDb = peakDb,
            rmsDb = rmsDb,
            integratedLufs = lufs,
            noiseFloorDb = noiseFloorDb,
            transcribedMidiNotes = midiNotes
        )
    }

    fun contourToMidiNotes(contour: List<PitchPoint>, bpm: Double): List<MidiNote> {
        if (contour.isEmpty()) return emptyList()
        val notes = mutableListOf<MidiNote>()
        val beatsPerSec = bpm / 60.0
        var activePitch = -1
        var startBeat = 0.0
        var lastTime = 0f

        for (pt in contour) {
            if (!pt.voiced) {
                if (activePitch >= 0) {
                    val dur = max(0.25, (pt.timeSec - lastTime) * beatsPerSec + 0.25)
                    notes.add(
                        MidiNote(
                            id = "a2m_${notes.size}",
                            pitch = activePitch.coerceIn(21, 108),
                            startBeat = startBeat,
                            durationBeats = dur,
                            velocity = 96
                        )
                    )
                    activePitch = -1
                }
            } else {
                val quantized = pt.detectedMidi.roundToInt().coerceIn(21, 108)
                if (activePitch < 0) {
                    activePitch = quantized
                    startBeat = pt.timeSec * beatsPerSec
                    lastTime = pt.timeSec
                } else if (abs(quantized - activePitch) >= 1) {
                    val dur = max(0.25, (pt.timeSec - lastTime) * beatsPerSec)
                    notes.add(
                        MidiNote(
                            id = "a2m_${notes.size}",
                            pitch = activePitch,
                            startBeat = startBeat,
                            durationBeats = dur,
                            velocity = 96
                        )
                    )
                    activePitch = quantized
                    startBeat = pt.timeSec * beatsPerSec
                    lastTime = pt.timeSec
                }
            }
        }
        return notes.take(64)
    }

    /**
     * Compute 32 logarithmic frequency bands using lightweight Goertzel resonators for real-time Spectrum Analyzer.
     */
    fun computeSpectrumBands(monoBlock: FloatArray, sampleRate: Int, bands: Int = 32): List<Float> {
        if (monoBlock.isEmpty()) return List(bands) { 0f }
        val n = monoBlock.size
        val result = MutableList(bands) { 0f }
        val minFreq = 45.0
        val maxFreq = min(16000.0, sampleRate * 0.45)

        for (b in 0 until bands) {
            val frac = b.toDouble() / (bands - 1).coerceAtLeast(1)
            val freq = minFreq * (maxFreq / minFreq).pow(frac)
            val k = (0.5 + (n * freq / sampleRate)).toInt().coerceAtLeast(1)
            val omega = 2.0 * PI * k / n
            val coeff = 2.0 * cos(omega)
            var q0: Double
            var q1 = 0.0
            var q2 = 0.0
            for (i in 0 until n) {
                q0 = coeff * q1 - q2 + monoBlock[i]
                q2 = q1
                q1 = q0
            }
            val magSq = max(0.0, q1 * q1 + q2 * q2 - q1 * q2 * coeff)
            val mag = sqrt(magSq) / (n * 0.5)
            val db = (20.0 * log10(mag + 1e-6)).toFloat()
            result[b] = ((db + 60f) / 60f).coerceIn(0f, 1f)
        }
        return result
    }

    /**
     * Non-destructive vocal/instrument separation preview using Mid/Side + vocal-band filtering.
     */
    fun isolateStemPreview(
        left: FloatArray,
        right: FloatArray,
        sampleRate: Int,
        isolateVocals: Boolean
    ): Pair<FloatArray, FloatArray> {
        val n = min(left.size, right.size)
        val outL = FloatArray(n)
        val outR = FloatArray(n)
        val hpAlpha = expCoeff(160f, sampleRate)
        val lpAlpha = expCoeff(4800f, sampleRate)
        var hpPrev = 0f
        var hpInPrev = 0f
        var lpState = 0f

        for (i in 0 until n) {
            val mid = 0.5f * (left[i] + right[i])
            val side = 0.5f * (left[i] - right[i])
            val hp = hpAlpha * (hpPrev + mid - hpInPrev)
            hpPrev = hp
            hpInPrev = mid
            lpState += (1f - lpAlpha) * (hp - lpState)
            val vocalCore = lpState

            if (isolateVocals) {
                val v = (vocalCore * 1.15f).coerceIn(-1f, 1f)
                outL[i] = v
                outR[i] = v
            } else {
                outL[i] = (left[i] - vocalCore * 0.85f + side * 0.35f).coerceIn(-1f, 1f)
                outR[i] = (right[i] - vocalCore * 0.85f - side * 0.35f).coerceIn(-1f, 1f)
            }
        }
        return outL to outR
    }

    private fun expCoeff(cutoffHz: Float, sampleRate: Int): Float {
        val rc = 1.0 / (2.0 * PI * cutoffHz)
        val dt = 1.0 / sampleRate
        return (rc / (rc + dt)).toFloat()
    }

    fun linearToDb(linear: Float): Float {
        return (20f * log10(max(linear, 1e-6f))).coerceIn(-60f, 6f)
    }

    fun dbToLinear(db: Float): Float {
        return 10f.pow(db / 20f)
    }
}
