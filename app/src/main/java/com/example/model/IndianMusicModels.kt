package com.example.model

enum class IndianInstrument(
    val id: String,
    val displayName: String,
    val category: String,
    val baseFreqHz: Float,
    val isPercussion: Boolean,
    val articulations: List<String>
) {
    SAMBALPURI_MANDAR(
        "sambalpuri_mandar",
        "Sambalpuri Mandar",
        "Folk Percussion",
        88f,
        true,
        listOf("Baya Bass", "Dayan Slap", "Rim Ta")
    ),
    NAGPURI_MANDAR(
        "nagpuri_mandar",
        "Nagpuri Mandar",
        "Folk Percussion",
        104f,
        true,
        listOf("Dha", "Na", "Ti")
    ),
    DHOLAK(
        "dholak",
        "Dholak",
        "Percussion",
        96f,
        true,
        listOf("Ghe", "Ta", "Chapak")
    ),
    TABLA(
        "tabla",
        "Tabla",
        "Classical Percussion",
        130f,
        true,
        listOf("Dha", "Na", "Tin", "Ge")
    ),
    NISHAN(
        "nishan",
        "Nishan",
        "Tribal Percussion",
        72f,
        true,
        listOf("Boom", "Accent")
    ),
    TASHA(
        "tasha",
        "Tasha",
        "Processional Percussion",
        310f,
        true,
        listOf("Crack", "Roll")
    ),
    DHAMSA(
        "dhamsa",
        "Dhamsa",
        "Tribal Bass",
        58f,
        true,
        listOf("Sub Hit", "Rim")
    ),
    FLUTE(
        "flute",
        "Flute",
        "Woodwind",
        523.25f,
        false,
        listOf("Sustain", "Staccato")
    ),
    HARMONIUM(
        "harmonium",
        "Harmonium",
        "Reed Keyboard",
        261.63f,
        false,
        listOf("Chord", "Lead")
    ),
    SITAR(
        "sitar",
        "Sitar",
        "Plucked String",
        220.0f,
        false,
        listOf("Da", "Ra", "Meend")
    ),
    BANSURI(
        "bansuri",
        "Bansuri",
        "Bamboo Flute",
        440.0f,
        false,
        listOf("Breath", "Gamak")
    ),
    MANJIRA(
        "manjira",
        "Manjira",
        "Metallic Percussion",
        2450f,
        true,
        listOf("Chime", "Mute")
    ),
    KANJARI(
        "kanjari",
        "Kanjari",
        "Frame Percussion",
        210f,
        true,
        listOf("Slap", "Jingle")
    )
}

enum class RhythmStyle(val displayName: String, val defaultBpm: Double, val timeSig: TimeSignature) {
    SAMBALPURI_FOLK("Sambalpuri Folk Rhythm", 116.0, TimeSignature(4, 4, "4/4")),
    NAGPURI_FOLK("Nagpuri Folk Rhythm", 124.0, TimeSignature(4, 4, "4/4")),
    JHUMAR("Jhumar Rhythm", 108.0, TimeSignature(6, 8, "6/8")),
    BOLLYWOOD("Bollywood Rhythm", 102.0, TimeSignature(4, 4, "4/4")),
    CHRISTIAN_WORSHIP("Christian Worship Rhythm", 74.0, TimeSignature(4, 4, "4/4")),
    CUSTOM("Custom Rhythm", 120.0, TimeSignature(7, 8, "7/8"))
}

data class RhythmLane(
    val instrument: IndianInstrument,
    val steps: List<Float>, // 0f = off, 0.1f..1.0f = velocity
    val muted: Boolean = false
)

data class RhythmPattern(
    val id: String,
    val name: String,
    val style: RhythmStyle,
    val timeSignature: TimeSignature,
    val bpm: Double,
    val stepCount: Int = 16,
    val swing: Float = 0.0f,
    val lanes: List<RhythmLane>,
    val isPreset: Boolean = false
)
