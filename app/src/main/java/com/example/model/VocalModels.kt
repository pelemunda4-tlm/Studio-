package com.example.model

enum class VocalChainType(
    val displayName: String,
    val description: String,
    val defaultParams: VocalProcessingParams
) {
    CLEAN_VOCAL(
        "Clean Vocal",
        "Transparent studio clarity with gentle compression and soft room air.",
        VocalProcessingParams(
            retuneSpeed = 0.35f,
            formantShiftSemitones = 0f,
            vibratoDepth = 0.2f,
            vibratoRateHz = 5.2f,
            timingTightness = 0.25f,
            harmonyEnabled = false,
            harmonyIntervalSemitones = 4,
            doubleVocalMix = 0.12f,
            breathReduction = 0.30f,
            deEsserAmount = 0.40f,
            noiseReductionAmount = 0.35f,
            highPassHz = 85f,
            presenceBoostDb = 2.5f,
            reverbSend = 0.18f,
            delaySend = 0.08f
        )
    ),
    BOLLYWOOD_VOCAL(
        "Bollywood Vocal",
        "Silky upper-mid presence, lush plate reverb, stereo delay tail, and tight pitch polish.",
        VocalProcessingParams(
            retuneSpeed = 0.65f,
            formantShiftSemitones = 0.2f,
            vibratoDepth = 0.35f,
            vibratoRateHz = 5.6f,
            timingTightness = 0.45f,
            harmonyEnabled = true,
            harmonyIntervalSemitones = 4,
            doubleVocalMix = 0.28f,
            breathReduction = 0.45f,
            deEsserAmount = 0.55f,
            noiseReductionAmount = 0.45f,
            highPassHz = 100f,
            presenceBoostDb = 4.5f,
            reverbSend = 0.42f,
            delaySend = 0.26f
        )
    ),
    CHRISTIAN_WORSHIP_VOCAL(
        "Christian Worship Vocal",
        "Warm intimate chest tone with expansive hall ambience and supportive octave/fifth harmony.",
        VocalProcessingParams(
            retuneSpeed = 0.45f,
            formantShiftSemitones = 0f,
            vibratoDepth = 0.25f,
            vibratoRateHz = 5.0f,
            timingTightness = 0.30f,
            harmonyEnabled = true,
            harmonyIntervalSemitones = 7,
            doubleVocalMix = 0.34f,
            breathReduction = 0.35f,
            deEsserAmount = 0.45f,
            noiseReductionAmount = 0.40f,
            highPassHz = 90f,
            presenceBoostDb = 3.2f,
            reverbSend = 0.54f,
            delaySend = 0.30f
        )
    ),
    SAMBALPURI_VOCAL(
        "Sambalpuri Vocal",
        "Punchy folk mid-forward cut to sit cleanly over Sambalpuri Mandar and Nishan percussion.",
        VocalProcessingParams(
            retuneSpeed = 0.55f,
            formantShiftSemitones = 0f,
            vibratoDepth = 0.30f,
            vibratoRateHz = 5.8f,
            timingTightness = 0.50f,
            harmonyEnabled = false,
            harmonyIntervalSemitones = 5,
            doubleVocalMix = 0.24f,
            breathReduction = 0.40f,
            deEsserAmount = 0.50f,
            noiseReductionAmount = 0.50f,
            highPassHz = 110f,
            presenceBoostDb = 4.0f,
            reverbSend = 0.28f,
            delaySend = 0.22f
        )
    ),
    NAGPURI_VOCAL(
        "Nagpuri Vocal",
        "Crisp energetic regional vocal chain with tape echo throw and tight dynamic control.",
        VocalProcessingParams(
            retuneSpeed = 0.60f,
            formantShiftSemitones = 0f,
            vibratoDepth = 0.28f,
            vibratoRateHz = 5.5f,
            timingTightness = 0.55f,
            harmonyEnabled = true,
            harmonyIntervalSemitones = 3,
            doubleVocalMix = 0.26f,
            breathReduction = 0.42f,
            deEsserAmount = 0.48f,
            noiseReductionAmount = 0.48f,
            highPassHz = 105f,
            presenceBoostDb = 3.8f,
            reverbSend = 0.32f,
            delaySend = 0.28f
        )
    ),
    CINEMATIC_VOCAL(
        "Cinematic Vocal",
        "Wide dramatic formant character, deep hall decay, and harmonic choir doubling.",
        VocalProcessingParams(
            retuneSpeed = 0.40f,
            formantShiftSemitones = -0.4f,
            vibratoDepth = 0.20f,
            vibratoRateHz = 4.6f,
            timingTightness = 0.30f,
            harmonyEnabled = true,
            harmonyIntervalSemitones = 12,
            doubleVocalMix = 0.45f,
            breathReduction = 0.50f,
            deEsserAmount = 0.52f,
            noiseReductionAmount = 0.55f,
            highPassHz = 80f,
            presenceBoostDb = 3.0f,
            reverbSend = 0.68f,
            delaySend = 0.35f
        )
    ),
    PODCAST_VOICE(
        "Podcast Voice",
        "Broadcast-ready dry proximity punch with strong noise reduction, gate, and de-essing.",
        VocalProcessingParams(
            retuneSpeed = 0.0f,
            formantShiftSemitones = 0f,
            vibratoDepth = 0.0f,
            vibratoRateHz = 5.0f,
            timingTightness = 0.0f,
            harmonyEnabled = false,
            harmonyIntervalSemitones = 0,
            doubleVocalMix = 0.0f,
            breathReduction = 0.72f,
            deEsserAmount = 0.65f,
            noiseReductionAmount = 0.78f,
            highPassHz = 95f,
            presenceBoostDb = 2.8f,
            reverbSend = 0.0f,
            delaySend = 0.0f
        )
    )
}

data class VocalProcessingParams(
    val retuneSpeed: Float = 0.5f,
    val formantShiftSemitones: Float = 0f,
    val vibratoDepth: Float = 0.2f,
    val vibratoRateHz: Float = 5.2f,
    val timingTightness: Float = 0.3f,
    val harmonyEnabled: Boolean = false,
    val harmonyIntervalSemitones: Int = 4,
    val doubleVocalMix: Float = 0.2f,
    val breathReduction: Float = 0.4f,
    val deEsserAmount: Float = 0.45f,
    val noiseReductionAmount: Float = 0.45f,
    val highPassHz: Float = 95f,
    val presenceBoostDb: Float = 3.0f,
    val reverbSend: Float = 0.25f,
    val delaySend: Float = 0.15f
)

data class PitchPoint(
    val timeSec: Float,
    val detectedMidi: Float,
    val targetMidi: Float,
    val manualOffsetSemitones: Float = 0f,
    val voiced: Boolean = true
)

data class VocalTake(
    val id: String,
    val name: String,
    val sampleRate: Int,
    val durationSec: Float,
    val waveform: List<Float>,
    val pitchCurve: List<PitchPoint>,
    val activeComp: Boolean = true
)
