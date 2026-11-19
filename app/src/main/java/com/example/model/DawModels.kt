package com.example.model

enum class DawScreen(val route: String, val label: String) {
    ARRANGEMENT("arrangement_timeline", "Arrange"),
    MIXER("mixer_console", "Mixer"),
    PIANO_ROLL("piano_roll", "Piano Roll"),
    DRUM_MACHINE("drum_machine", "Drums & Indian"),
    VOCAL_EDITOR("vocal_editor", "Vocal Studio"),
    SAMPLER("sampler", "Sampler"),
    PLUGIN_BROWSER("plugin_browser", "Plugins"),
    MASTERING("mastering_studio", "Master"),
    SOUND_LIBRARY("sound_library", "Browser"),
    HOME("home_dashboard", "Projects")
}

enum class ArrangementTool(val label: String, val shortCode: String) {
    ARROW("Arrow", "ARW"),
    RANGE("Range", "RNG"),
    SPLIT("Split", "SPL"),
    ERASER("Eraser", "ERS"),
    PAINT("Paint", "PNT"),
    MUTE("Mute", "MUT"),
    LISTEN("Listen", "LST"),
    ZOOM("Zoom", "ZOM")
}

enum class GridResolution(val label: String, val stepBeats: Double) {
    BAR("1 Bar", 4.0),
    BEAT("1/4", 1.0),
    EIGHTH("1/8", 0.5),
    SIXTEENTH("1/16", 0.25),
    TRIPLET("1/8T", 1.0 / 3.0)
}

enum class BottomEditorTab(val label: String) {
    AUDIO_EDITOR("Audio Editor"),
    PIANO_ROLL("Piano Roll"),
    DRUM_EDITOR("Drum Editor"),
    AUTOMATION("Automation"),
    CHORD_TRACK("Chord Track"),
    EVENT_INSPECTOR("Event Inspector"),
    MIXER_DOCK("Mixer Console")
}

enum class BrowserCategory(val label: String) {
    INSTRUMENTS("Instruments"),
    EFFECTS("Effects"),
    SAMPLES("Samples"),
    LOOPS("Loops"),
    DRUM_KITS("Drum Kits"),
    PRESETS("Presets"),
    PROJECTS("Projects"),
    FILES("Files")
}

enum class MusicalScale(val displayName: String, val intervals: List<Int>, val category: String) {
    MAJOR("Major (Ionian)", listOf(0, 2, 4, 5, 7, 9, 11), "Western"),
    NATURAL_MINOR("Natural Minor", listOf(0, 2, 3, 5, 7, 8, 10), "Western"),
    HARMONIC_MINOR("Harmonic Minor", listOf(0, 2, 3, 5, 7, 8, 11), "Western"),
    MELODIC_MINOR("Melodic Minor", listOf(0, 2, 3, 5, 7, 9, 11), "Western"),
    PENTATONIC("Major Pentatonic", listOf(0, 2, 4, 7, 9), "Western"),
    BLUES("Blues Scale", listOf(0, 3, 5, 6, 7, 10), "Western"),
    RAGA_YAMAN("Raga Yaman (Kalyan)", listOf(0, 2, 4, 6, 7, 9, 11), "Indian Raga"),
    RAGA_BHAIRAV("Raga Bhairav", listOf(0, 1, 4, 5, 7, 8, 11), "Indian Raga"),
    RAGA_BHUPALI("Raga Bhupali", listOf(0, 2, 4, 7, 9), "Indian Raga"),
    RAGA_KAFI("Raga Kafi", listOf(0, 2, 3, 5, 7, 9, 10), "Indian Raga"),
    RAGA_DARBARI("Raga Darbari Kanada", listOf(0, 2, 3, 5, 7, 8, 10), "Indian Raga"),
    RAGA_BILAWAL("Raga Bilawal", listOf(0, 2, 4, 5, 7, 9, 11), "Indian Raga")
}

data class TimeSignature(
    val numerator: Int = 4,
    val denominator: Int = 4,
    val label: String = "4/4"
) {
    companion object {
        val PRESETS = listOf(
            TimeSignature(4, 4, "4/4"),
            TimeSignature(3, 4, "3/4"),
            TimeSignature(6, 8, "6/8"),
            TimeSignature(7, 8, "7/8")
        )
    }
}

enum class TrackType(val label: String) {
    AUDIO("Audio"),
    MIDI("MIDI"),
    INSTRUMENT("Instrument"),
    DRUM("Drum"),
    INDIAN_RHYTHM("Indian Rhythm"),
    VOCAL("Vocal"),
    SAMPLER("Sampler"),
    AUTOMATION("Automation"),
    BUS("Bus"),
    VCA("VCA"),
    FOLDER("Folder"),
    MASTER("Master")
}

enum class PluginCategory(val label: String) {
    EQUALIZERS("Equalizers"),
    DYNAMICS("Dynamics"),
    TIME_EFFECTS("Time Effects"),
    CREATIVE_EFFECTS("Creative Effects")
}

enum class PluginType(
    val displayName: String,
    val category: PluginCategory,
    val latencySamples: Int = 0
) {
    // Equalizers (6)
    EQ_3_BAND("3-Band EQ", PluginCategory.EQUALIZERS),
    EQ_5_BAND("5-Band EQ", PluginCategory.EQUALIZERS),
    EQ_8_PARAMETRIC("8-Band Parametric EQ", PluginCategory.EQUALIZERS),
    DYNAMIC_EQ("Dynamic EQ", PluginCategory.EQUALIZERS, 64),
    HIGH_PASS_FILTER("High-Pass Filter", PluginCategory.EQUALIZERS),
    LOW_PASS_FILTER("Low-Pass Filter", PluginCategory.EQUALIZERS),

    // Dynamics (7)
    COMPRESSOR("Compressor", PluginCategory.DYNAMICS),
    MULTIBAND_COMPRESSOR("Multiband Compressor", PluginCategory.DYNAMICS, 128),
    LIMITER("Limiter", PluginCategory.DYNAMICS, 64),
    EXPANDER("Expander", PluginCategory.DYNAMICS),
    NOISE_GATE("Noise Gate", PluginCategory.DYNAMICS),
    TRANSIENT_SHAPER("Transient Shaper", PluginCategory.DYNAMICS),
    DE_ESSER("De-Esser", PluginCategory.DYNAMICS),

    // Time Effects (7)
    HALL_REVERB("Hall Reverb", PluginCategory.TIME_EFFECTS),
    ROOM_REVERB("Room Reverb", PluginCategory.TIME_EFFECTS),
    PLATE_REVERB("Plate Reverb", PluginCategory.TIME_EFFECTS),
    SPRING_REVERB("Spring Reverb", PluginCategory.TIME_EFFECTS),
    STEREO_DELAY("Stereo Delay", PluginCategory.TIME_EFFECTS),
    PING_PONG_DELAY("Ping Pong Delay", PluginCategory.TIME_EFFECTS),
    TAPE_ECHO("Tape Echo", PluginCategory.TIME_EFFECTS),

    // Creative Effects (8)
    CHORUS("Chorus", PluginCategory.CREATIVE_EFFECTS),
    FLANGER("Flanger", PluginCategory.CREATIVE_EFFECTS),
    PHASER("Phaser", PluginCategory.CREATIVE_EFFECTS),
    DISTORTION("Distortion", PluginCategory.CREATIVE_EFFECTS),
    SATURATION("Saturation", PluginCategory.CREATIVE_EFFECTS),
    EXCITER("Exciter", PluginCategory.CREATIVE_EFFECTS),
    BIT_CRUSHER("Bit Crusher", PluginCategory.CREATIVE_EFFECTS),
    STEREO_WIDENER("Stereo Widener", PluginCategory.CREATIVE_EFFECTS)
}

data class PluginParam(
    val key: String,
    val label: String,
    val value: Float,
    val min: Float,
    val max: Float,
    val unit: String = ""
)

data class PluginInstance(
    val id: String,
    val type: PluginType,
    val presetName: String = "Default",
    val bypassed: Boolean = false,
    val wetDry: Float = 1.0f,
    val params: Map<String, Float> = emptyMap(),
    val inputMeterDb: Float = -60f,
    val outputMeterDb: Float = -60f
)

data class AutomationPoint(
    val beatPosition: Double,
    val normalizedValue: Float
)

data class AutomationLane(
    val id: String,
    val targetParam: String,
    val visible: Boolean = true,
    val points: List<AutomationPoint> = emptyList()
)

data class MidiNote(
    val id: String,
    val pitch: Int, // 21 (A0) .. 108 (C8)
    val startBeat: Double,
    val durationBeats: Double,
    val velocity: Int = 100, // 1..127
    val probability: Float = 1.0f, // 0..1
    val laneIndex: Int = 0
)

data class MidiCcEvent(
    val ccNumber: Int,
    val beatPosition: Double,
    val value: Int // 0..127
)

data class AudioClip(
    val id: String,
    val name: String,
    val startBeat: Double,
    val lengthBeats: Double,
    val gainDb: Float = 0f,
    val sourcePath: String = "",
    val waveformPreview: List<Float> = emptyList(),
    val missingSource: Boolean = false,
    val muted: Boolean = false
)

data class SendRouting(
    val targetBusId: String,
    val targetBusName: String,
    val level: Float = 0.25f,
    val preFader: Boolean = false
)

data class DawTrack(
    val id: String,
    val name: String,
    val type: TrackType,
    val colorArgb: Long,
    val volume: Float = 0.8f,
    val pan: Float = 0.0f,
    val muted: Boolean = false,
    val soloed: Boolean = false,
    val armed: Boolean = false,
    val monitoring: Boolean = false,
    val frozen: Boolean = false,
    val mono: Boolean = false,
    val inputChannel: String = "In 1-2",
    val outputChannel: String = "Main Out",
    val delayCompMs: Float = 0f,
    val vcaGroupId: String? = null,
    val busTargetId: String? = "bus_master",
    val folderId: String? = null,
    val sidechainSourceId: String? = null,
    val sends: List<SendRouting> = emptyList(),
    val plugins: List<PluginInstance> = emptyList(),
    val clips: List<AudioClip> = emptyList(),
    val midiNotes: List<MidiNote> = emptyList(),
    val midiCcEvents: List<MidiCcEvent> = emptyList(),
    val automationLanes: List<AutomationLane> = emptyList(),
    val instrumentName: String = "Grand Piano"
)

data class SongSection(
    val id: String,
    val name: String,
    val startBar: Int,
    val lengthBars: Int,
    val colorArgb: Long
)

data class TimelineMarker(
    val id: String,
    val label: String,
    val beatPosition: Double
)

data class ChordEvent(
    val id: String,
    val symbol: String,
    val midiNotes: List<Int>,
    val beatPosition: Double,
    val durationBeats: Double
)

data class TempoPoint(
    val beatPosition: Double,
    val bpm: Double
)

data class ScratchPad(
    val id: String,
    val title: String,
    val notes: List<MidiNote>,
    val chords: List<ChordEvent>
)

data class PlaylistItem(
    val id: String,
    val sectionId: String,
    val repeatCount: Int = 1,
    val active: Boolean = true
)

data class MasteringConfig(
    val presetName: String = "Streaming (-14 LUFS)",
    val linearPhaseEqEnabled: Boolean = true,
    val lowShelfDb: Float = 0.5f,
    val midPresenceDb: Float = 0.8f,
    val airShelfDb: Float = 1.2f,
    val dynamicEqAmount: Float = 0.35f,
    val multibandDrive: Float = 0.4f,
    val stereoWidth: Float = 1.12f,
    val harmonicExciter: Float = 0.25f,
    val tapeSaturation: Float = 0.30f,
    val clipperDrive: Float = 0.2f,
    val truePeakCeilingDb: Float = -1.0f,
    val targetLufs: Float = -14.0f,
    val bypassMastering: Boolean = false,
    val abMonitorReference: Boolean = false
)

data class MeterSnapshot(
    val peakLeftDb: Float = -60f,
    val peakRightDb: Float = -60f,
    val rmsLeftDb: Float = -60f,
    val rmsRightDb: Float = -60f,
    val momentaryLufs: Float = -60f,
    val integratedLufs: Float = -60f,
    val truePeakDbTp: Float = -60f,
    val correlation: Float = 1.0f,
    val spectrumBands: List<Float> = List(32) { 0f },
    val waveformScope: List<Float> = List(64) { 0f }
)

data class StudioHardwareStatus(
    val sampleRate: Int = 48000,
    val bufferSize: Int = 256,
    val estimatedLatencyMs: Float = 5.3f,
    val cpuLoadPercent: Float = 12.0f,
    val xrunCount: Int = 0,
    val recoveryCount: Int = 0,
    val precision64BitSumming: Boolean = true,
    val outputDeviceName: String = "Android Low-Latency Audio",
    val inputDeviceName: String = "Built-in Studio Mic",
    val usbAudioConnected: Boolean = false,
    val midiDevices: List<String> = emptyList()
)

data class UndoHistoryItem(
    val id: String,
    val description: String,
    val timestampMs: Long
)

data class DawProject(
    val id: String,
    val title: String,
    val bpm: Double = 116.0,
    val timeSignature: TimeSignature = TimeSignature(4, 4, "4/4"),
    val musicalKey: String = "C",
    val scale: MusicalScale = MusicalScale.MAJOR,
    val tracks: List<DawTrack> = emptyList(),
    val sections: List<SongSection> = emptyList(),
    val markers: List<TimelineMarker> = emptyList(),
    val chordTrack: List<ChordEvent> = emptyList(),
    val tempoTrack: List<TempoPoint> = emptyList(),
    val scratchPads: List<ScratchPad> = emptyList(),
    val playlist: List<PlaylistItem> = emptyList(),
    val loopEnabled: Boolean = true,
    val loopStartBeat: Double = 0.0,
    val loopEndBeat: Double = 16.0,
    val snapEnabled: Boolean = true,
    val gridResolution: GridResolution = GridResolution.EIGHTH,
    val masteringConfig: MasteringConfig = MasteringConfig(),
    val versionNumber: Int = 1,
    val notesSummary: String = ""
)
