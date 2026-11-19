package com.example.audio

import com.example.model.IndianInstrument
import com.example.model.SamplerPad
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

class SynthVoice(
    val left: FloatArray,
    val right: FloatArray,
    var cursor: Int = 0
) {
    val active: Boolean get() = cursor < left.size
}

object IndianInstrumentSynth {

    fun synthesizeInstrumentHit(
        instrument: IndianInstrument,
        sampleRate: Int,
        velocity: Float = 0.85f,
        articulationIdx: Int = 0,
        midiPitch: Int? = null
    ): Pair<FloatArray, FloatArray> {
        val vel = velocity.coerceIn(0.05f, 1.0f)
        val durationSec = if (instrument.isPercussion) 0.55f else 0.85f
        val n = (sampleRate * durationSec).toInt()
        val outL = FloatArray(n)
        val outR = FloatArray(n)

        val baseFreq = if (midiPitch != null) {
            PitchAndAnalysisDsp.midiToHz(midiPitch.toFloat())
        } else {
            instrument.baseFreqHz * when (articulationIdx) {
                1 -> 1.45f
                2 -> 1.9f
                else -> 1.0f
            }
        }

        val rng = Random(instrument.ordinal * 97 + articulationIdx * 31)

        when (instrument) {
            IndianInstrument.SAMBALPURI_MANDAR,
            IndianInstrument.NAGPURI_MANDAR,
            IndianInstrument.DHOLAK,
            IndianInstrument.TABLA -> {
                val isBass = articulationIdx == 0
                val startFreq = if (isBass) baseFreq * 1.9f else baseFreq * 2.5f
                val endFreq = if (isBass) baseFreq * 0.88f else baseFreq * 1.65f
                val decayRate = if (isBass) 7.5f else 13.5f
                var phase = 0.0
                var overPhase = 0.0
                for (i in 0 until n) {
                    val t = i.toFloat() / sampleRate
                    val pitchEnv = exp(-t * 28f)
                    val freq = endFreq + (startFreq - endFreq) * pitchEnv
                    phase += 2.0 * PI * freq / sampleRate
                    overPhase += 2.0 * PI * (freq * if (instrument == IndianInstrument.TABLA) 2.0 else 1.58) / sampleRate
                    val env = exp(-t * decayRate)
                    val slapNoise = if (t < 0.015f) (rng.nextFloat() * 2f - 1f) * (1f - t / 0.015f) * 0.45f else 0f
                    val body = sin(phase).toFloat() * 0.78f + sin(overPhase).toFloat() * (if (isBass) 0.18f else 0.36f)
                    val sample = tanh((body + slapNoise) * env * vel * 1.25f) * 0.82f
                    val pan = if (isBass) -0.12f else 0.18f
                    outL[i] = sample * (0.5f - pan * 0.5f) * 1.3f
                    outR[i] = sample * (0.5f + pan * 0.5f) * 1.3f
                }
            }
            IndianInstrument.NISHAN,
            IndianInstrument.DHAMSA -> {
                val decay = if (instrument == IndianInstrument.DHAMSA) 4.8f else 6.5f
                var phase = 0.0
                for (i in 0 until n) {
                    val t = i.toFloat() / sampleRate
                    val freq = baseFreq + baseFreq * 1.6f * exp(-t * 36f)
                    phase += 2.0 * PI * freq / sampleRate
                    val env = exp(-t * decay)
                    val skinHit = if (t < 0.02f) (rng.nextFloat() * 2f - 1f) * exp(-t * 140f) * 0.55f else 0f
                    val sig = tanh((sin(phase).toFloat() * 0.9f + skinHit) * env * vel * 1.4f) * 0.88f
                    outL[i] = sig
                    outR[i] = sig
                }
            }
            IndianInstrument.TASHA,
            IndianInstrument.KANJARI -> {
                var phase = 0.0
                for (i in 0 until n) {
                    val t = i.toFloat() / sampleRate
                    val freq = baseFreq * (1f + 0.3f * exp(-t * 50f))
                    phase += 2.0 * PI * freq / sampleRate
                    val env = exp(-t * 18f)
                    val rattle = (rng.nextFloat() * 2f - 1f) * exp(-t * 22f) * 0.55f
                    val jingle = if (instrument == IndianInstrument.KANJARI) {
                        sin(2.0 * PI * 5400.0 * t).toFloat() * exp(-t * 14f) * 0.28f
                    } else 0f
                    val sig = (sin(phase).toFloat() * 0.45f * env + rattle + jingle) * vel * 0.8f
                    outL[i] = sig * 0.9f
                    outR[i] = sig * 1.0f
                }
            }
            IndianInstrument.MANJIRA -> {
                val ratios = floatArrayOf(1.0f, 1.54f, 2.31f, 3.12f, 4.18f)
                for (i in 0 until n) {
                    val t = i.toFloat() / sampleRate
                    val env = exp(-t * (if (articulationIdx == 1) 18f else 5.2f))
                    var sum = 0f
                    for (r in ratios) {
                        sum += sin(2.0 * PI * baseFreq * r * t).toFloat()
                    }
                    val sig = (sum / ratios.size) * env * vel * 0.65f
                    outL[i] = sig * 0.85f
                    outR[i] = sig * 1.0f
                }
            }
            IndianInstrument.FLUTE,
            IndianInstrument.BANSURI -> {
                var phase = 0.0
                for (i in 0 until n) {
                    val t = i.toFloat() / sampleRate
                    val atk = (t / 0.06f).coerceIn(0f, 1f)
                    val rel = ((durationSec - t) / 0.14f).coerceIn(0f, 1f)
                    val env = atk * rel
                    val vib = 1f + 0.012f * sin(2.0 * PI * 5.3 * t).toFloat()
                    phase += 2.0 * PI * baseFreq * vib / sampleRate
                    val breath = (rng.nextFloat() * 2f - 1f) * 0.07f
                    val tone = sin(phase).toFloat() * 0.72f + sin(phase * 2.0).toFloat() * 0.18f + breath
                    val sig = tone * env * vel * 0.72f
                    outL[i] = sig * 0.94f
                    outR[i] = sig
                }
            }
            IndianInstrument.HARMONIUM -> {
                var p1 = 0.0; var p2 = 0.0
                for (i in 0 until n) {
                    val t = i.toFloat() / sampleRate
                    val atk = (t / 0.025f).coerceIn(0f, 1f)
                    val rel = ((durationSec - t) / 0.12f).coerceIn(0f, 1f)
                    p1 += 2.0 * PI * baseFreq / sampleRate
                    p2 += 2.0 * PI * (baseFreq * 1.0035f) / sampleRate
                    val reed1 = (sin(p1) + 0.45 * sin(2 * p1) + 0.25 * sin(3 * p1) + 0.12 * sin(4 * p1)).toFloat()
                    val reed2 = (sin(p2) + 0.40 * sin(2 * p2)).toFloat()
                    val sig = tanh((reed1 + reed2) * 0.35f) * atk * rel * vel * 0.65f
                    outL[i] = sig
                    outR[i] = sig * 0.95f
                }
            }
            IndianInstrument.SITAR -> {
                // Karplus-Strong plucked string with jawari harmonic buzz
                val delayLen = (sampleRate / baseFreq).toInt().coerceIn(20, 2000)
                val ring = FloatArray(delayLen) { rng.nextFloat() * 2f - 1f }
                var idx = 0
                var prev = 0f
                for (i in 0 until n) {
                    val t = i.toFloat() / sampleRate
                    val current = ring[idx]
                    val avg = 0.496f * (current + prev)
                    prev = current
                    ring[idx] = avg
                    idx = (idx + 1) % delayLen
                    val jawari = tanh(avg * 2.2f) * 0.65f + sin(2.0 * PI * baseFreq * 2.0 * t).toFloat() * exp(-t * 4.5f) * 0.18f
                    val sig = jawari * exp(-t * 1.9f) * vel * 0.78f
                    outL[i] = sig
                    outR[i] = sig * 0.92f
                }
            }
        }
        return outL to outR
    }

    fun synthesizeMidiNote(
        instrumentName: String,
        midiPitch: Int,
        durationSec: Float,
        sampleRate: Int,
        velocity: Float
    ): Pair<FloatArray, FloatArray> {
        val indianMatch = IndianInstrument.entries.firstOrNull {
            it.displayName.equals(instrumentName, ignoreCase = true) ||
                instrumentName.contains(it.displayName, ignoreCase = true)
        }
        if (indianMatch != null) {
            return synthesizeInstrumentHit(indianMatch, sampleRate, velocity, 0, midiPitch)
        }

        val freq = PitchAndAnalysisDsp.midiToHz(midiPitch.toFloat())
        val totalSec = durationSec.coerceIn(0.12f, 2.5f)
        val n = (sampleRate * totalSec).toInt()
        val outL = FloatArray(n)
        val outR = FloatArray(n)
        var p1 = 0.0
        var p2 = 0.0
        val detune = if (instrumentName.contains("Pad", true) || instrumentName.contains("Synth", true)) 1.005 else 1.0015
        for (i in 0 until n) {
            val t = i.toFloat() / sampleRate
            val atk = (t / 0.015f).coerceIn(0f, 1f)
            val decay = exp(-t * (if (instrumentName.contains("Pad", true)) 1.2f else 3.2f))
            val rel = ((totalSec - t) / 0.08f).coerceIn(0f, 1f)
            p1 += 2.0 * PI * freq / sampleRate
            p2 += 2.0 * PI * (freq * detune) / sampleRate
            val wave = (sin(p1) + 0.35 * sin(2 * p1) + 0.25 * sin(p2)).toFloat() * 0.52f
            val s = wave * atk * decay * rel * velocity.coerceIn(0.05f, 1f) * 0.65f
            outL[i] = s
            outR[i] = s
        }
        return outL to outR
    }

    fun renderSamplerPadVoice(
        pad: SamplerPad,
        sourceLeft: FloatArray,
        sourceRight: FloatArray,
        midiNote: Int,
        velocity: Float,
        sampleRate: Int
    ): Pair<FloatArray, FloatArray> {
        if (sourceLeft.isEmpty()) {
            return synthesizeMidiNote("Sampler Synth", midiNote, 0.45f, sampleRate, velocity)
        }
        val pitchFactor = 2.0.pow(((midiNote - pad.rootMidiNote + pad.pitchSemitones) / 12.0))
        val stretch = pad.timeStretchRatio.coerceIn(0.4f, 2.5f)
        val effectiveRate = (pitchFactor / stretch).coerceIn(0.25, 4.0)
        val outLen = ((sourceLeft.size / effectiveRate).toInt()).coerceIn(512, sampleRate * 4)
        val outL = FloatArray(outLen)
        val outR = FloatArray(outLen)

        val atkSamples = ((pad.attackMs / 1000f) * sampleRate).toInt().coerceAtLeast(1)
        val decSamples = ((pad.decayMs / 1000f) * sampleRate).toInt().coerceAtLeast(1)
        val relSamples = ((pad.releaseMs / 1000f) * sampleRate).toInt().coerceAtLeast(1)

        for (i in 0 until outLen) {
            val srcPos = if (pad.reverse) {
                (sourceLeft.size - 1) - i * effectiveRate
            } else {
                i * effectiveRate
            }
            val idx0 = srcPos.toInt().coerceIn(0, sourceLeft.size - 1)
            val idx1 = (idx0 + 1).coerceIn(0, sourceLeft.size - 1)
            val frac = (srcPos - idx0).toFloat().coerceIn(0f, 1f)
            val sL = sourceLeft[idx0] * (1f - frac) + sourceLeft[idx1] * frac
            val sR = sourceRight.getOrElse(idx0) { sL } * (1f - frac) + sourceRight.getOrElse(idx1) { sL } * frac

            val env = when {
                i < atkSamples -> i.toFloat() / atkSamples
                i < atkSamples + decSamples -> 1f - (1f - pad.sustainLevel) * ((i - atkSamples).toFloat() / decSamples)
                i > outLen - relSamples -> pad.sustainLevel * ((outLen - i).toFloat() / relSamples).coerceIn(0f, 1f)
                else -> pad.sustainLevel
            }
            outL[i] = sL * env * velocity
            outR[i] = sR * env * velocity
        }
        return outL to outR
    }
}
