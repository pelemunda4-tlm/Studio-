package com.example.audio

import com.example.model.PluginInstance
import com.example.model.PluginParam
import com.example.model.PluginType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

interface AudioPluginProcessor {
    val type: PluginType
    fun processStereo(
        left: FloatArray,
        right: FloatArray,
        frames: Int,
        sampleRate: Int,
        instance: PluginInstance,
        sidechainMono: FloatArray? = null
    ): Pair<Float, Float> // inputDb, outputDb
}

class BiquadState {
    private var b0 = 1f; private var b1 = 0f; private var b2 = 0f
    private var a1 = 0f; private var a2 = 0f
    private var z1L = 0f; private var z2L = 0f
    private var z1R = 0f; private var z2R = 0f

    fun configurePeak(freq: Float, q: Float, gainDb: Float, sampleRate: Int) {
        val w0 = 2.0 * PI * freq.coerceIn(20f, sampleRate * 0.45f) / sampleRate
        val a = 10.0.pow(gainDb / 40.0)
        val alpha = sin(w0) / (2.0 * q.coerceIn(0.2f, 12f))
        val cosW0 = cos(w0)
        val a0 = 1.0 + alpha / a
        b0 = ((1.0 + alpha * a) / a0).toFloat()
        b1 = ((-2.0 * cosW0) / a0).toFloat()
        b2 = ((1.0 - alpha * a) / a0).toFloat()
        a1 = ((-2.0 * cosW0) / a0).toFloat()
        a2 = ((1.0 - alpha / a) / a0).toFloat()
    }

    fun configureLowShelf(freq: Float, gainDb: Float, sampleRate: Int) {
        val w0 = 2.0 * PI * freq.coerceIn(20f, sampleRate * 0.45f) / sampleRate
        val a = 10.0.pow(gainDb / 40.0)
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / 2.0 * sqrt(2.0)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha
        val a0 = (a + 1) + (a - 1) * cosW0 + twoSqrtAAlpha
        b0 = (a * ((a + 1) - (a - 1) * cosW0 + twoSqrtAAlpha) / a0).toFloat()
        b1 = (2 * a * ((a - 1) - (a + 1) * cosW0) / a0).toFloat()
        b2 = (a * ((a + 1) - (a - 1) * cosW0 - twoSqrtAAlpha) / a0).toFloat()
        a1 = (-2 * ((a - 1) + (a + 1) * cosW0) / a0).toFloat()
        a2 = (((a + 1) + (a - 1) * cosW0 - twoSqrtAAlpha) / a0).toFloat()
    }

    fun configureHighShelf(freq: Float, gainDb: Float, sampleRate: Int) {
        val w0 = 2.0 * PI * freq.coerceIn(20f, sampleRate * 0.45f) / sampleRate
        val a = 10.0.pow(gainDb / 40.0)
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / 2.0 * sqrt(2.0)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha
        val a0 = (a + 1) - (a - 1) * cosW0 + twoSqrtAAlpha
        b0 = (a * ((a + 1) + (a - 1) * cosW0 + twoSqrtAAlpha) / a0).toFloat()
        b1 = (-2 * a * ((a - 1) + (a + 1) * cosW0) / a0).toFloat()
        b2 = (a * ((a + 1) + (a - 1) * cosW0 - twoSqrtAAlpha) / a0).toFloat()
        a1 = (2 * ((a - 1) - (a + 1) * cosW0) / a0).toFloat()
        a2 = (((a + 1) - (a - 1) * cosW0 - twoSqrtAAlpha) / a0).toFloat()
    }

    fun configureHighPass(freq: Float, q: Float, sampleRate: Int) {
        val w0 = 2.0 * PI * freq.coerceIn(20f, sampleRate * 0.45f) / sampleRate
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * q.coerceIn(0.3f, 8f))
        val a0 = 1.0 + alpha
        b0 = (((1.0 + cosW0) / 2.0) / a0).toFloat()
        b1 = (-(1.0 + cosW0) / a0).toFloat()
        b2 = (((1.0 + cosW0) / 2.0) / a0).toFloat()
        a1 = ((-2.0 * cosW0) / a0).toFloat()
        a2 = ((1.0 - alpha) / a0).toFloat()
    }

    fun configureLowPass(freq: Float, q: Float, sampleRate: Int) {
        val w0 = 2.0 * PI * freq.coerceIn(20f, sampleRate * 0.45f) / sampleRate
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * q.coerceIn(0.3f, 8f))
        val a0 = 1.0 + alpha
        b0 = (((1.0 - cosW0) / 2.0) / a0).toFloat()
        b1 = ((1.0 - cosW0) / a0).toFloat()
        b2 = (((1.0 - cosW0) / 2.0) / a0).toFloat()
        a1 = ((-2.0 * cosW0) / a0).toFloat()
        a2 = ((1.0 - alpha) / a0).toFloat()
    }

    fun processSampleLeft(x: Float): Float {
        val y = b0 * x + z1L
        z1L = b1 * x - a1 * y + z2L
        z2L = b2 * x - a2 * y
        return y
    }

    fun processSampleRight(x: Float): Float {
        val y = b0 * x + z1R
        z1R = b1 * x - a1 * y + z2R
        z2R = b2 * x - a2 * y
        return y
    }
}

object PluginCatalog {

    fun defaultParams(type: PluginType): List<PluginParam> = when (type) {
        PluginType.EQ_3_BAND -> listOf(
            PluginParam("low", "Low Gain", 0f, -12f, 12f, "dB"),
            PluginParam("mid", "Mid Gain", 0f, -12f, 12f, "dB"),
            PluginParam("high", "High Gain", 0f, -12f, 12f, "dB")
        )
        PluginType.EQ_5_BAND -> listOf(
            PluginParam("b1", "60 Hz", 0f, -12f, 12f, "dB"),
            PluginParam("b2", "250 Hz", 0f, -12f, 12f, "dB"),
            PluginParam("b3", "1 kHz", 0f, -12f, 12f, "dB"),
            PluginParam("b4", "4 kHz", 0f, -12f, 12f, "dB"),
            PluginParam("b5", "12 kHz", 0f, -12f, 12f, "dB")
        )
        PluginType.EQ_8_PARAMETRIC -> listOf(
            PluginParam("b1", "45 Hz", 0f, -12f, 12f, "dB"),
            PluginParam("b2", "120 Hz", 0f, -12f, 12f, "dB"),
            PluginParam("b3", "320 Hz", 0f, -12f, 12f, "dB"),
            PluginParam("b4", "800 Hz", 0f, -12f, 12f, "dB"),
            PluginParam("b5", "1.8 kHz", 0f, -12f, 12f, "dB"),
            PluginParam("b6", "3.6 kHz", 0f, -12f, 12f, "dB"),
            PluginParam("b7", "7.5 kHz", 0f, -12f, 12f, "dB"),
            PluginParam("b8", "14 kHz", 0f, -12f, 12f, "dB")
        )
        PluginType.DYNAMIC_EQ -> listOf(
            PluginParam("freq", "Target Freq", 2400f, 100f, 10000f, "Hz"),
            PluginParam("thresh", "Threshold", -18f, -40f, 0f, "dB"),
            PluginParam("range", "Dynamic Cut", -4f, -12f, 6f, "dB")
        )
        PluginType.HIGH_PASS_FILTER -> listOf(
            PluginParam("cutoff", "Cutoff", 85f, 20f, 1200f, "Hz"),
            PluginParam("resonance", "Resonance", 0.7f, 0.4f, 4.0f, "Q")
        )
        PluginType.LOW_PASS_FILTER -> listOf(
            PluginParam("cutoff", "Cutoff", 14000f, 400f, 20000f, "Hz"),
            PluginParam("resonance", "Resonance", 0.7f, 0.4f, 4.0f, "Q")
        )
        PluginType.COMPRESSOR -> listOf(
            PluginParam("thresh", "Threshold", -18f, -48f, 0f, "dB"),
            PluginParam("ratio", "Ratio", 4.0f, 1.0f, 20.0f, ":1"),
            PluginParam("attack", "Attack", 12f, 1f, 100f, "ms"),
            PluginParam("release", "Release", 120f, 20f, 800f, "ms"),
            PluginParam("makeup", "Makeup", 2.0f, 0f, 18f, "dB")
        )
        PluginType.MULTIBAND_COMPRESSOR -> listOf(
            PluginParam("lowDrive", "Low Comp", 0.35f, 0f, 1f, ""),
            PluginParam("midDrive", "Mid Comp", 0.40f, 0f, 1f, ""),
            PluginParam("highDrive", "High Comp", 0.30f, 0f, 1f, ""),
            PluginParam("makeup", "Output", 1.5f, -6f, 12f, "dB")
        )
        PluginType.LIMITER -> listOf(
            PluginParam("ceiling", "Ceiling", -0.5f, -12f, 0f, "dBTP"),
            PluginParam("gain", "Input Drive", 3.0f, 0f, 18f, "dB")
        )
        PluginType.EXPANDER -> listOf(
            PluginParam("thresh", "Threshold", -36f, -60f, -6f, "dB"),
            PluginParam("ratio", "Ratio", 2.0f, 1.0f, 6.0f, ":1")
        )
        PluginType.NOISE_GATE -> listOf(
            PluginParam("thresh", "Threshold", -45f, -72f, -12f, "dB"),
            PluginParam("release", "Release", 90f, 10f, 500f, "ms")
        )
        PluginType.TRANSIENT_SHAPER -> listOf(
            PluginParam("attack", "Attack Punch", 0.35f, -1f, 1f, ""),
            PluginParam("sustain", "Sustain Body", 0.1f, -1f, 1f, "")
        )
        PluginType.DE_ESSER -> listOf(
            PluginParam("freq", "Sibilance Freq", 6500f, 3500f, 10500f, "Hz"),
            PluginParam("thresh", "Threshold", -24f, -45f, 0f, "dB"),
            PluginParam("amount", "Reduction", 0.55f, 0f, 1f, "")
        )
        PluginType.HALL_REVERB, PluginType.ROOM_REVERB, PluginType.PLATE_REVERB, PluginType.SPRING_REVERB -> listOf(
            PluginParam("size", "Room Size", if (type == PluginType.HALL_REVERB) 0.78f else 0.48f, 0.1f, 0.98f, ""),
            PluginParam("damp", "Damping", 0.42f, 0.05f, 0.95f, ""),
            PluginParam("predelay", "Pre-Delay", 18f, 0f, 120f, "ms")
        )
        PluginType.STEREO_DELAY, PluginType.PING_PONG_DELAY, PluginType.TAPE_ECHO -> listOf(
            PluginParam("timeMs", "Delay Time", 320f, 20f, 950f, "ms"),
            PluginParam("feedback", "Feedback", 0.38f, 0f, 0.88f, ""),
            PluginParam("tone", "Tone LP", 6500f, 800f, 16000f, "Hz")
        )
        PluginType.CHORUS, PluginType.FLANGER, PluginType.PHASER -> listOf(
            PluginParam("rate", "LFO Rate", 0.8f, 0.1f, 8.0f, "Hz"),
            PluginParam("depth", "Depth", 0.55f, 0f, 1f, ""),
            PluginParam("feedback", "Feedback", 0.35f, 0f, 0.85f, "")
        )
        PluginType.DISTORTION, PluginType.SATURATION, PluginType.EXCITER -> listOf(
            PluginParam("drive", "Drive", 0.42f, 0f, 1f, ""),
            PluginParam("tone", "Tone", 0.65f, 0f, 1f, "")
        )
        PluginType.BIT_CRUSHER -> listOf(
            PluginParam("bits", "Bit Depth", 10f, 2f, 24f, "bit"),
            PluginParam("downsample", "Downsample", 3f, 1f, 24f, "x")
        )
        PluginType.STEREO_WIDENER -> listOf(
            PluginParam("width", "Stereo Width", 1.35f, 0f, 2.0f, "x"),
            PluginParam("haasMs", "Haas Spread", 6.0f, 0f, 20f, "ms")
        )
    }

    fun presetsFor(type: PluginType): Map<String, Map<String, Float>> = when (type) {
        PluginType.EQ_3_BAND -> mapOf(
            "Default" to mapOf("low" to 0f, "mid" to 0f, "high" to 0f),
            "Warm Vocal" to mapOf("low" to -2.5f, "mid" to 2.2f, "high" to 3.0f),
            "Punchy Drum Bus" to mapOf("low" to 3.5f, "mid" to -1.5f, "high" to 2.8f)
        )
        PluginType.COMPRESSOR -> mapOf(
            "Default" to mapOf("thresh" to -18f, "ratio" to 4f, "attack" to 12f, "release" to 120f, "makeup" to 2f),
            "Mandar Snap" to mapOf("thresh" to -22f, "ratio" to 6f, "attack" to 20f, "release" to 85f, "makeup" to 4f),
            "Vocal Glue" to mapOf("thresh" to -16f, "ratio" to 3.2f, "attack" to 8f, "release" to 140f, "makeup" to 3f)
        )
        PluginType.HALL_REVERB -> mapOf(
            "Default" to mapOf("size" to 0.78f, "damp" to 0.42f, "predelay" to 18f),
            "Worship Cathedral" to mapOf("size" to 0.92f, "damp" to 0.30f, "predelay" to 32f),
            "Cinematic Brass" to mapOf("size" to 0.86f, "damp" to 0.38f, "predelay" to 24f)
        )
        else -> mapOf(
            "Default" to defaultParams(type).associate { it.key to it.value },
            "Studio Polish" to defaultParams(type).associate {
                it.key to (it.value + (it.max - it.min) * 0.1f).coerceIn(it.min, it.max)
            },
            "Creative Push" to defaultParams(type).associate {
                it.key to (it.value + (it.max - it.min) * 0.22f).coerceIn(it.min, it.max)
            }
        )
    }

    fun createInstance(id: String, type: PluginType, presetName: String = "Default"): PluginInstance {
        val presets = presetsFor(type)
        val params = presets[presetName] ?: defaultParams(type).associate { it.key to it.value }
        return PluginInstance(
            id = id,
            type = type,
            presetName = presetName,
            bypassed = false,
            wetDry = 1.0f,
            params = params
        )
    }
}

class UniversalPluginProcessor(override val type: PluginType) : AudioPluginProcessor {
    private val biquads = Array(8) { BiquadState() }
    private var env = 0f
    private var envLow = 0f
    private var envMid = 0f
    private var envHigh = 0f
    private var lfoPhase = 0f
    private var holdCounter = 0

    // Stereo circular buffer for delays/reverbs/chorus/flanger/widener (up to 1 sec @ 96kHz)
    private val delayBufL = FloatArray(96000)
    private val delayBufR = FloatArray(96000)
    private var delayWritePos = 0

    // Bit crusher hold
    private var crushCounter = 0
    private var crushHoldL = 0f
    private var crushHoldR = 0f

    // Phaser allpass states
    private val apL = FloatArray(4)
    private val apR = FloatArray(4)

    override fun processStereo(
        left: FloatArray,
        right: FloatArray,
        frames: Int,
        sampleRate: Int,
        instance: PluginInstance,
        sidechainMono: FloatArray?
    ): Pair<Float, Float> {
        var inPeak = 0f
        for (i in 0 until frames) {
            inPeak = max(inPeak, max(abs(left[i]), abs(right[i])))
        }
        val inDb = PitchAndAnalysisDsp.linearToDb(inPeak)
        if (instance.bypassed) return inDb to inDb

        val dryL = FloatArray(frames)
        val dryR = FloatArray(frames)
        System.arraycopy(left, 0, dryL, 0, frames)
        System.arraycopy(right, 0, dryR, 0, frames)

        val p = instance.params
        when (type) {
            PluginType.EQ_3_BAND -> {
                biquads[0].configureLowShelf(160f, p["low"] ?: 0f, sampleRate)
                biquads[1].configurePeak(1200f, 1.0f, p["mid"] ?: 0f, sampleRate)
                biquads[2].configureHighShelf(5500f, p["high"] ?: 0f, sampleRate)
                for (i in 0 until frames) {
                    var l = biquads[0].processSampleLeft(left[i])
                    l = biquads[1].processSampleLeft(l)
                    left[i] = biquads[2].processSampleLeft(l)
                    var r = biquads[0].processSampleRight(right[i])
                    r = biquads[1].processSampleRight(r)
                    right[i] = biquads[2].processSampleRight(r)
                }
            }
            PluginType.EQ_5_BAND -> {
                val freqs = floatArrayOf(60f, 250f, 1000f, 4000f, 12000f)
                val keys = arrayOf("b1", "b2", "b3", "b4", "b5")
                for (b in 0..4) biquads[b].configurePeak(freqs[b], 1.1f, p[keys[b]] ?: 0f, sampleRate)
                for (i in 0 until frames) {
                    var l = left[i]; var r = right[i]
                    for (b in 0..4) {
                        l = biquads[b].processSampleLeft(l)
                        r = biquads[b].processSampleRight(r)
                    }
                    left[i] = l; right[i] = r
                }
            }
            PluginType.EQ_8_PARAMETRIC -> {
                val freqs = floatArrayOf(45f, 120f, 320f, 800f, 1800f, 3600f, 7500f, 14000f)
                val keys = arrayOf("b1", "b2", "b3", "b4", "b5", "b6", "b7", "b8")
                for (b in 0..7) biquads[b].configurePeak(freqs[b], 1.3f, p[keys[b]] ?: 0f, sampleRate)
                for (i in 0 until frames) {
                    var l = left[i]; var r = right[i]
                    for (b in 0..7) {
                        l = biquads[b].processSampleLeft(l)
                        r = biquads[b].processSampleRight(r)
                    }
                    left[i] = l; right[i] = r
                }
            }
            PluginType.DYNAMIC_EQ -> {
                val freq = p["freq"] ?: 2400f
                val thresh = PitchAndAnalysisDsp.dbToLinear(p["thresh"] ?: -18f)
                val rangeDb = p["range"] ?: -4f
                for (i in 0 until frames) {
                    val lvl = max(abs(left[i]), abs(right[i]))
                    env = 0.995f * env + 0.005f * lvl
                    val over = ((env - thresh) / max(thresh, 1e-4f)).coerceIn(0f, 1f)
                    if (i % 16 == 0) biquads[0].configurePeak(freq, 1.4f, rangeDb * over, sampleRate)
                    left[i] = biquads[0].processSampleLeft(left[i])
                    right[i] = biquads[0].processSampleRight(right[i])
                }
            }
            PluginType.HIGH_PASS_FILTER -> {
                biquads[0].configureHighPass(p["cutoff"] ?: 85f, p["resonance"] ?: 0.7f, sampleRate)
                for (i in 0 until frames) {
                    left[i] = biquads[0].processSampleLeft(left[i])
                    right[i] = biquads[0].processSampleRight(right[i])
                }
            }
            PluginType.LOW_PASS_FILTER -> {
                biquads[0].configureLowPass(p["cutoff"] ?: 14000f, p["resonance"] ?: 0.7f, sampleRate)
                for (i in 0 until frames) {
                    left[i] = biquads[0].processSampleLeft(left[i])
                    right[i] = biquads[0].processSampleRight(right[i])
                }
            }
            PluginType.COMPRESSOR -> {
                val threshDb = p["thresh"] ?: -18f
                val ratio = (p["ratio"] ?: 4f).coerceAtLeast(1f)
                val atk = exp(-1.0 / (sampleRate * ((p["attack"] ?: 12f) / 1000.0))).toFloat()
                val rel = exp(-1.0 / (sampleRate * ((p["release"] ?: 120f) / 1000.0))).toFloat()
                val makeup = PitchAndAnalysisDsp.dbToLinear(p["makeup"] ?: 2f)
                for (i in 0 until frames) {
                    val det = sidechainMono?.getOrNull(i)?.let { abs(it) } ?: max(abs(left[i]), abs(right[i]))
                    env = if (det > env) atk * env + (1f - atk) * det else rel * env + (1f - rel) * det
                    val envDb = PitchAndAnalysisDsp.linearToDb(env)
                    val gainDb = if (envDb > threshDb) -(envDb - threshDb) * (1f - 1f / ratio) else 0f
                    val gain = PitchAndAnalysisDsp.dbToLinear(gainDb) * makeup
                    left[i] *= gain
                    right[i] *= gain
                }
            }
            PluginType.MULTIBAND_COMPRESSOR -> {
                biquads[0].configureLowPass(240f, 0.707f, sampleRate)
                biquads[1].configureHighPass(3200f, 0.707f, sampleRate)
                val lowD = 1f + (p["lowDrive"] ?: 0.35f) * 2.5f
                val midD = 1f + (p["midDrive"] ?: 0.40f) * 2.5f
                val highD = 1f + (p["highDrive"] ?: 0.30f) * 2.5f
                val makeup = PitchAndAnalysisDsp.dbToLinear(p["makeup"] ?: 1.5f)
                for (i in 0 until frames) {
                    val lLow = biquads[0].processSampleLeft(left[i])
                    val rLow = biquads[0].processSampleRight(right[i])
                    val lHi = biquads[1].processSampleLeft(left[i])
                    val rHi = biquads[1].processSampleRight(right[i])
                    val lMid = left[i] - lLow - lHi
                    val rMid = right[i] - rLow - rHi
                    envLow = 0.995f * envLow + 0.005f * max(abs(lLow), abs(rLow))
                    envMid = 0.992f * envMid + 0.008f * max(abs(lMid), abs(rMid))
                    envHigh = 0.99f * envHigh + 0.01f * max(abs(lHi), abs(rHi))
                    val gL = 1f / (1f + envLow * lowD)
                    val gM = 1f / (1f + envMid * midD)
                    val gH = 1f / (1f + envHigh * highD)
                    left[i] = (lLow * gL + lMid * gM + lHi * gH) * makeup
                    right[i] = (rLow * gL + rMid * gM + rHi * gH) * makeup
                }
            }
            PluginType.LIMITER -> {
                val ceil = PitchAndAnalysisDsp.dbToLinear(p["ceiling"] ?: -0.5f)
                val drive = PitchAndAnalysisDsp.dbToLinear(p["gain"] ?: 3f)
                for (i in 0 until frames) {
                    val l = left[i] * drive
                    val r = right[i] * drive
                    val peak = max(abs(l), abs(r))
                    env = if (peak > env) peak else 0.9992f * env + 0.0008f * peak
                    val g = if (env > ceil && env > 1e-6f) ceil / env else 1f
                    left[i] = (l * g).coerceIn(-ceil, ceil)
                    right[i] = (r * g).coerceIn(-ceil, ceil)
                }
            }
            PluginType.EXPANDER -> {
                val threshDb = p["thresh"] ?: -36f
                val ratio = p["ratio"] ?: 2.0f
                for (i in 0 until frames) {
                    val det = max(abs(left[i]), abs(right[i]))
                    env = 0.994f * env + 0.006f * det
                    val db = PitchAndAnalysisDsp.linearToDb(env)
                    val gainDb = if (db < threshDb) (db - threshDb) * (ratio - 1f) else 0f
                    val gain = PitchAndAnalysisDsp.dbToLinear(gainDb.coerceAtLeast(-40f))
                    left[i] *= gain; right[i] *= gain
                }
            }
            PluginType.NOISE_GATE -> {
                val thresh = PitchAndAnalysisDsp.dbToLinear(p["thresh"] ?: -45f)
                val rel = exp(-1.0 / (sampleRate * ((p["release"] ?: 90f) / 1000.0))).toFloat()
                for (i in 0 until frames) {
                    val det = max(abs(left[i]), abs(right[i]))
                    if (det >= thresh) {
                        holdCounter = (sampleRate * 0.025f).toInt()
                        env = 1f
                    } else if (holdCounter > 0) {
                        holdCounter--
                    } else {
                        env *= rel
                    }
                    left[i] *= env; right[i] *= env
                }
            }
            PluginType.TRANSIENT_SHAPER -> {
                val atkAmt = p["attack"] ?: 0.35f
                val susAmt = p["sustain"] ?: 0.1f
                for (i in 0 until frames) {
                    val det = max(abs(left[i]), abs(right[i]))
                    envLow = 0.985f * envLow + 0.015f * det // fast
                    envHigh = 0.999f * envHigh + 0.001f * det // slow
                    val transient = max(0f, envLow - envHigh) * 4f
                    val gain = (1f + transient * atkAmt + envHigh * susAmt).coerceIn(0.25f, 2.5f)
                    left[i] *= gain; right[i] *= gain
                }
            }
            PluginType.DE_ESSER -> {
                biquads[0].configurePeak(p["freq"] ?: 6500f, 2.0f, 12f, sampleRate)
                val thresh = PitchAndAnalysisDsp.dbToLinear(p["thresh"] ?: -24f)
                val amount = p["amount"] ?: 0.55f
                for (i in 0 until frames) {
                    val sib = max(abs(biquads[0].processSampleLeft(left[i])), abs(biquads[0].processSampleRight(right[i])))
                    env = 0.99f * env + 0.01f * sib
                    val reduce = if (env > thresh) (1f - amount * ((env - thresh) / (env + 1e-5f))).coerceIn(0.2f, 1f) else 1f
                    left[i] *= reduce; right[i] *= reduce
                }
            }
            PluginType.HALL_REVERB, PluginType.ROOM_REVERB, PluginType.PLATE_REVERB, PluginType.SPRING_REVERB -> {
                val size = (p["size"] ?: 0.6f).coerceIn(0.1f, 0.96f)
                val damp = (p["damp"] ?: 0.4f).coerceIn(0.05f, 0.95f)
                val predelaySamples = ((p["predelay"] ?: 18f) * sampleRate / 1000f).toInt().coerceIn(1, 8000)
                val tap1 = (predelaySamples + (sampleRate * 0.029f * size).toInt()).coerceIn(1, delayBufL.size - 1)
                val tap2 = (predelaySamples + (sampleRate * 0.043f * size).toInt()).coerceIn(1, delayBufL.size - 1)
                val tap3 = (predelaySamples + (sampleRate * 0.067f * size).toInt()).coerceIn(1, delayBufL.size - 1)
                val fb = (0.35f + size * 0.52f).coerceAtMost(0.88f)
                for (i in 0 until frames) {
                    val r1 = (delayWritePos - tap1 + delayBufL.size) % delayBufL.size
                    val r2 = (delayWritePos - tap2 + delayBufL.size) % delayBufL.size
                    val r3 = (delayWritePos - tap3 + delayBufL.size) % delayBufL.size
                    val tailL = (delayBufL[r1] + delayBufR[r2] * 0.8f + delayBufL[r3] * 0.6f) * 0.42f
                    val tailR = (delayBufR[r1] + delayBufL[r2] * 0.8f + delayBufR[r3] * 0.6f) * 0.42f
                    envLow = damp * envLow + (1f - damp) * tailL
                    envHigh = damp * envHigh + (1f - damp) * tailR
                    delayBufL[delayWritePos] = left[i] + envLow * fb
                    delayBufR[delayWritePos] = right[i] + envHigh * fb
                    delayWritePos = (delayWritePos + 1) % delayBufL.size
                    left[i] = tailL; right[i] = tailR
                }
            }
            PluginType.STEREO_DELAY, PluginType.PING_PONG_DELAY, PluginType.TAPE_ECHO -> {
                val delaySamples = ((p["timeMs"] ?: 320f) * sampleRate / 1000f).toInt().coerceIn(64, delayBufL.size - 1)
                val fb = (p["feedback"] ?: 0.38f).coerceIn(0f, 0.88f)
                biquads[0].configureLowPass(p["tone"] ?: 6500f, 0.7f, sampleRate)
                val pingPong = type == PluginType.PING_PONG_DELAY
                val tape = type == PluginType.TAPE_ECHO
                for (i in 0 until frames) {
                    val readIdx = (delayWritePos - delaySamples + delayBufL.size) % delayBufL.size
                    var dL = biquads[0].processSampleLeft(delayBufL[readIdx])
                    var dR = biquads[0].processSampleRight(delayBufR[readIdx])
                    if (tape) {
                        dL = tanh(dL * 1.25f)
                        dR = tanh(dR * 1.25f)
                    }
                    delayBufL[delayWritePos] = left[i] + (if (pingPong) dR else dL) * fb
                    delayBufR[delayWritePos] = (if (pingPong) 0f else right[i]) + dR * fb
                    delayWritePos = (delayWritePos + 1) % delayBufL.size
                    left[i] = dL; right[i] = dR
                }
            }
            PluginType.CHORUS, PluginType.FLANGER -> {
                val rate = p["rate"] ?: 0.8f
                val depth = p["depth"] ?: 0.55f
                val fb = if (type == PluginType.FLANGER) (p["feedback"] ?: 0.35f) else 0.1f
                val baseMs = if (type == PluginType.CHORUS) 16f else 2.5f
                val modMs = if (type == PluginType.CHORUS) 7f else 2.0f
                val phaseInc = (2.0 * PI * rate / sampleRate).toFloat()
                for (i in 0 until frames) {
                    lfoPhase = (lfoPhase + phaseInc) % (2f * PI.toFloat())
                    val mod = 0.5f + 0.5f * sin(lfoPhase)
                    val dSamp = ((baseMs + mod * depth * modMs) * sampleRate / 1000f).toInt().coerceIn(2, delayBufL.size - 1)
                    val readIdx = (delayWritePos - dSamp + delayBufL.size) % delayBufL.size
                    val wL = delayBufL[readIdx]
                    val wR = delayBufR[readIdx]
                    delayBufL[delayWritePos] = left[i] + wL * fb
                    delayBufR[delayWritePos] = right[i] + wR * fb
                    delayWritePos = (delayWritePos + 1) % delayBufL.size
                    left[i] = wL; right[i] = wR
                }
            }
            PluginType.PHASER -> {
                val rate = p["rate"] ?: 0.6f
                val depth = p["depth"] ?: 0.65f
                val fb = p["feedback"] ?: 0.4f
                val phaseInc = (2.0 * PI * rate / sampleRate).toFloat()
                for (i in 0 until frames) {
                    lfoPhase = (lfoPhase + phaseInc) % (2f * PI.toFloat())
                    val coeff = (0.25f + 0.65f * depth * (0.5f + 0.5f * sin(lfoPhase))).coerceIn(0.05f, 0.95f)
                    var xL = left[i] + apL[3] * fb
                    var xR = right[i] + apR[3] * fb
                    for (s in 0..3) {
                        val yL = -coeff * xL + apL[s]
                        apL[s] = xL + coeff * yL
                        xL = yL
                        val yR = -coeff * xR + apR[s]
                        apR[s] = xR + coeff * yR
                        xR = yR
                    }
                    left[i] = xL; right[i] = xR
                }
            }
            PluginType.DISTORTION, PluginType.SATURATION, PluginType.EXCITER -> {
                val drive = 1f + (p["drive"] ?: 0.42f) * (if (type == PluginType.DISTORTION) 14f else 4.5f)
                val toneFreq = 1800f + (p["tone"] ?: 0.65f) * 12000f
                biquads[0].configureLowPass(toneFreq, 0.7f, sampleRate)
                for (i in 0 until frames) {
                    val sL = tanh(left[i] * drive)
                    val sR = tanh(right[i] * drive)
                    if (type == PluginType.EXCITER) {
                        left[i] = left[i] + (sL - left[i]) * 0.35f
                        right[i] = right[i] + (sR - right[i]) * 0.35f
                    } else {
                        left[i] = biquads[0].processSampleLeft(sL)
                        right[i] = biquads[0].processSampleRight(sR)
                    }
                }
            }
            PluginType.BIT_CRUSHER -> {
                val bits = (p["bits"] ?: 10f).coerceIn(2f, 24f)
                val step = (p["downsample"] ?: 3f).toInt().coerceIn(1, 32)
                val levels = 2f.pow(bits - 1f)
                for (i in 0 until frames) {
                    if (crushCounter % step == 0) {
                        crushHoldL = (left[i] * levels).toInt() / levels
                        crushHoldR = (right[i] * levels).toInt() / levels
                    }
                    crushCounter++
                    left[i] = crushHoldL; right[i] = crushHoldR
                }
            }
            PluginType.STEREO_WIDENER -> {
                val width = (p["width"] ?: 1.35f).coerceIn(0f, 2.0f)
                val haasSamples = ((p["haasMs"] ?: 6.0f) * sampleRate / 1000f).toInt().coerceIn(0, 2000)
                for (i in 0 until frames) {
                    delayBufR[delayWritePos] = right[i]
                    val delayedR = delayBufR[(delayWritePos - haasSamples + delayBufR.size) % delayBufR.size]
                    delayWritePos = (delayWritePos + 1) % delayBufR.size
                    val mid = 0.5f * (left[i] + delayedR)
                    val side = 0.5f * (left[i] - delayedR) * width
                    left[i] = (mid + side).coerceIn(-1f, 1f)
                    right[i] = (mid - side).coerceIn(-1f, 1f)
                }
            }
        }

        val wet = instance.wetDry.coerceIn(0f, 1f)
        val dry = 1f - wet
        var outPeak = 0f
        for (i in 0 until frames) {
            left[i] = (dryL[i] * dry + left[i] * wet).coerceIn(-1.5f, 1.5f)
            right[i] = (dryR[i] * dry + right[i] * wet).coerceIn(-1.5f, 1.5f)
            outPeak = max(outPeak, max(abs(left[i]), abs(right[i])))
        }
        return inDb to PitchAndAnalysisDsp.linearToDb(outPeak)
    }
}
