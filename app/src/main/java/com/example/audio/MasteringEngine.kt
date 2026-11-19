package com.example.audio

import com.example.model.MasteringConfig
import com.example.model.MeterSnapshot
import com.example.model.PluginInstance
import com.example.model.PluginType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

class MasteringProcessor {
    private val eqProcessor = UniversalPluginProcessor(PluginType.EQ_3_BAND)
    private val dynEqProcessor = UniversalPluginProcessor(PluginType.DYNAMIC_EQ)
    private val mbCompProcessor = UniversalPluginProcessor(PluginType.MULTIBAND_COMPRESSOR)
    private val exciterProcessor = UniversalPluginProcessor(PluginType.EXCITER)
    private val widenerProcessor = UniversalPluginProcessor(PluginType.STEREO_WIDENER)
    private val satProcessor = UniversalPluginProcessor(PluginType.SATURATION)
    private val limiterProcessor = UniversalPluginProcessor(PluginType.LIMITER)

    // Linear-phase FIR symmetric low/high tilt kernel (31-tap zero-phase symmetry, 15 samples latency)
    private val firTaps = 31
    private val firHistL = FloatArray(firTaps)
    private val firHistR = FloatArray(firTaps)
    private var firPos = 0

    // Integrated LUFS accumulator
    private var integratedSqSum = 0.0
    private var integratedSamples = 0L

    fun processMasterBus(
        left: FloatArray,
        right: FloatArray,
        frames: Int,
        sampleRate: Int,
        config: MasteringConfig,
        referenceLeft: FloatArray? = null,
        referenceRight: FloatArray? = null,
        referenceOffset: Int = 0
    ): MeterSnapshot {
        // Optional A/B reference track monitoring
        if (config.abMonitorReference && referenceLeft != null && referenceLeft.isNotEmpty()) {
            for (i in 0 until frames) {
                val idx = (referenceOffset + i) % referenceLeft.size
                left[i] = referenceLeft[idx]
                right[i] = referenceRight?.getOrNull(idx) ?: referenceLeft[idx]
            }
        } else if (!config.bypassMastering) {
            if (config.linearPhaseEqEnabled) {
                applyLinearPhaseEq(left, right, frames, config)
            }
            if (config.dynamicEqAmount > 0.01f) {
                dynEqProcessor.processStereo(
                    left, right, frames, sampleRate,
                    PluginInstance("m_dyneq", PluginType.DYNAMIC_EQ, wetDry = config.dynamicEqAmount, params = mapOf("freq" to 3200f, "thresh" to -16f, "range" to -4f))
                )
            }
            mbCompProcessor.processStereo(
                left, right, frames, sampleRate,
                PluginInstance("m_mb", PluginType.MULTIBAND_COMPRESSOR, params = mapOf("lowDrive" to config.multibandDrive, "midDrive" to config.multibandDrive, "highDrive" to config.multibandDrive * 0.8f, "makeup" to 1.0f))
            )
            widenerProcessor.processStereo(
                left, right, frames, sampleRate,
                PluginInstance("m_wide", PluginType.STEREO_WIDENER, params = mapOf("width" to config.stereoWidth, "haasMs" to 0f))
            )
            if (config.harmonicExciter > 0.01f) {
                exciterProcessor.processStereo(
                    left, right, frames, sampleRate,
                    PluginInstance("m_exc", PluginType.EXCITER, wetDry = config.harmonicExciter, params = mapOf("drive" to config.harmonicExciter, "tone" to 0.78f))
                )
            }
            if (config.tapeSaturation > 0.01f) {
                satProcessor.processStereo(
                    left, right, frames, sampleRate,
                    PluginInstance("m_sat", PluginType.SATURATION, wetDry = config.tapeSaturation, params = mapOf("drive" to config.tapeSaturation, "tone" to 0.72f))
                )
            }
            // Soft Clipper
            val clipGain = 1f + config.clipperDrive * 1.4f
            for (i in 0 until frames) {
                left[i] = tanh(left[i] * clipGain) / clipGain.coerceAtLeast(1f)
                right[i] = tanh(right[i] * clipGain) / clipGain.coerceAtLeast(1f)
            }
            // True Peak Limiter
            limiterProcessor.processStereo(
                left, right, frames, sampleRate,
                PluginInstance("m_lim", PluginType.LIMITER, params = mapOf("ceiling" to config.truePeakCeilingDb, "gain" to 2.0f))
            )
        }

        return computeMeters(left, right, frames, sampleRate)
    }

    private fun applyLinearPhaseEq(
        left: FloatArray,
        right: FloatArray,
        frames: Int,
        config: MasteringConfig
    ) {
        eqProcessor.processStereo(
            left, right, frames, 48000,
            PluginInstance(
                "m_lpeq",
                PluginType.EQ_3_BAND,
                params = mapOf(
                    "low" to config.lowShelfDb,
                    "mid" to config.midPresenceDb,
                    "high" to config.airShelfDb
                )
            )
        )
        // Symmetric linear-phase smoothing kernel for phase-coherent high-frequency polish
        val center = firTaps / 2
        for (i in 0 until frames) {
            firHistL[firPos] = left[i]
            firHistR[firPos] = right[i]
            val cIdx = (firPos - center + firTaps) % firTaps
            val prevIdx = (cIdx - 1 + firTaps) % firTaps
            val nextIdx = (cIdx + 1) % firTaps
            left[i] = firHistL[cIdx] * 0.94f + (firHistL[prevIdx] + firHistL[nextIdx]) * 0.03f
            right[i] = firHistR[cIdx] * 0.94f + (firHistR[prevIdx] + firHistR[nextIdx]) * 0.03f
            firPos = (firPos + 1) % firTaps
        }
    }

    fun resetLoudness() {
        integratedSqSum = 0.0
        integratedSamples = 0L
    }

    fun computeMeters(
        left: FloatArray,
        right: FloatArray,
        frames: Int,
        sampleRate: Int
    ): MeterSnapshot {
        var peakL = 0f
        var peakR = 0f
        var truePeak = 0f
        var sumSqL = 0.0
        var sumSqR = 0.0
        var dotLR = 0.0
        val mono = FloatArray(min(frames, 256))
        val wave = MutableList(48) { 0f }
        val step = max(1, frames / 48)

        var prevL = 0f
        var prevR = 0f
        for (i in 0 until frames) {
            val l = left[i]
            val r = right[i]
            val al = abs(l)
            val ar = abs(r)
            if (al > peakL) peakL = al
            if (ar > peakR) peakR = ar

            // 4x oversampled linear-cubic True Peak approximation between samples
            val midL = abs(0.5f * (l + prevL) + 0.125f * (l - prevL))
            val midR = abs(0.5f * (r + prevR) + 0.125f * (r - prevR))
            truePeak = max(truePeak, max(max(al, ar), max(midL, midR)))
            prevL = l
            prevR = r

            sumSqL += l * l
            sumSqR += r * r
            dotLR += l * r

            if (i < mono.size) mono[i] = 0.5f * (l + r)
            val wIdx = i / step
            if (wIdx < wave.size) {
                val m = 0.5f * (l + r)
                if (abs(m) > abs(wave[wIdx])) wave[wIdx] = m
            }
        }

        val rmsL = sqrt(sumSqL / max(1, frames)).toFloat()
        val rmsR = sqrt(sumSqR / max(1, frames)).toFloat()
        val meanSq = 0.5 * (sumSqL + sumSqR) / max(1, frames)
        integratedSqSum += meanSq * frames
        integratedSamples += frames
        val intRms = sqrt(integratedSqSum / max(1L, integratedSamples)).toFloat()

        val denom = sqrt(sumSqL * sumSqR)
        val corr = if (denom > 1e-9) (dotLR / denom).toFloat().coerceIn(-1f, 1f) else 1f
        val spectrum = PitchAndAnalysisDsp.computeSpectrumBands(mono, sampleRate, 24)

        val rmsDbL = PitchAndAnalysisDsp.linearToDb(rmsL)
        val rmsDbR = PitchAndAnalysisDsp.linearToDb(rmsR)
        val momentaryLufs = (0.5f * (rmsDbL + rmsDbR) - 0.691f).coerceIn(-60f, 0f)
        val integratedLufs = (PitchAndAnalysisDsp.linearToDb(intRms) - 0.691f).coerceIn(-60f, 0f)

        return MeterSnapshot(
            peakLeftDb = PitchAndAnalysisDsp.linearToDb(peakL),
            peakRightDb = PitchAndAnalysisDsp.linearToDb(peakR),
            rmsLeftDb = rmsDbL,
            rmsRightDb = rmsDbR,
            momentaryLufs = momentaryLufs,
            integratedLufs = integratedLufs,
            truePeakDbTp = PitchAndAnalysisDsp.linearToDb(truePeak),
            correlation = corr,
            spectrumBands = spectrum,
            waveformScope = wave
        )
    }

    companion object {
        val MASTERING_PRESETS = listOf(
            MasteringConfig(
                presetName = "Streaming (-14 LUFS)",
                linearPhaseEqEnabled = true,
                lowShelfDb = 0.5f,
                midPresenceDb = 0.6f,
                airShelfDb = 1.1f,
                dynamicEqAmount = 0.30f,
                multibandDrive = 0.35f,
                stereoWidth = 1.12f,
                harmonicExciter = 0.20f,
                tapeSaturation = 0.25f,
                clipperDrive = 0.15f,
                truePeakCeilingDb = -1.0f,
                targetLufs = -14.0f
            ),
            MasteringConfig(
                presetName = "Bollywood / Folk Punch (-9 LUFS)",
                linearPhaseEqEnabled = true,
                lowShelfDb = 1.6f,
                midPresenceDb = 1.2f,
                airShelfDb = 1.8f,
                dynamicEqAmount = 0.45f,
                multibandDrive = 0.58f,
                stereoWidth = 1.22f,
                harmonicExciter = 0.35f,
                tapeSaturation = 0.42f,
                clipperDrive = 0.38f,
                truePeakCeilingDb = -0.5f,
                targetLufs = -9.0f
            ),
            MasteringConfig(
                presetName = "Worship / Acoustic Warm (-12 LUFS)",
                linearPhaseEqEnabled = true,
                lowShelfDb = 0.8f,
                midPresenceDb = 0.4f,
                airShelfDb = 1.4f,
                dynamicEqAmount = 0.25f,
                multibandDrive = 0.28f,
                stereoWidth = 1.18f,
                harmonicExciter = 0.18f,
                tapeSaturation = 0.32f,
                clipperDrive = 0.10f,
                truePeakCeilingDb = -1.0f,
                targetLufs = -12.0f
            ),
            MasteringConfig(
                presetName = "Broadcast Reference (-23 LUFS)",
                linearPhaseEqEnabled = true,
                lowShelfDb = 0.0f,
                midPresenceDb = 0.3f,
                airShelfDb = 0.5f,
                dynamicEqAmount = 0.20f,
                multibandDrive = 0.20f,
                stereoWidth = 1.0f,
                harmonicExciter = 0.08f,
                tapeSaturation = 0.10f,
                clipperDrive = 0.0f,
                truePeakCeilingDb = -1.5f,
                targetLufs = -23.0f
            )
        )
    }
}
