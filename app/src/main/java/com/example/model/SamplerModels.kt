package com.example.model

data class SampleSlice(
    val id: String,
    val index: Int,
    val startSample: Int,
    val endSample: Int,
    val label: String
)

data class SamplerPad(
    val id: String,
    val name: String,
    val sourceUri: String = "",
    val formatLabel: String = "WAV 32f",
    val sampleRate: Int = 48000,
    val totalSamples: Int = 0,
    val waveformPreview: List<Float> = emptyList(),
    val slices: List<SampleSlice> = emptyList(),
    val loopEnabled: Boolean = false,
    val loopStartSample: Int = 0,
    val loopEndSample: Int = 0,
    val reverse: Boolean = false,
    val pitchSemitones: Float = 0f,
    val timeStretchRatio: Float = 1.0f,
    val attackMs: Float = 5f,
    val decayMs: Float = 180f,
    val sustainLevel: Float = 0.75f,
    val releaseMs: Float = 220f,
    val rootMidiNote: Int = 60,
    val keyLow: Int = 36,
    val keyHigh: Int = 84,
    val velLow: Int = 1,
    val velHigh: Int = 127
)
