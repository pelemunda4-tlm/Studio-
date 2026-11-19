package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.os.Build
import android.os.Process
import android.util.Log
import com.example.model.DawProject
import com.example.model.DawTrack
import com.example.model.IndianInstrument
import com.example.model.MeterSnapshot
import com.example.model.PluginInstance
import com.example.model.PluginType
import com.example.model.RhythmPattern
import com.example.model.StudioHardwareStatus
import com.example.model.TrackType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private const val TAG = "MultitrackAudioEngine"

class MultitrackAudioEngine(private val context: Context? = null) {

    private val isPlaying = AtomicBoolean(false)
    private val isRecording = AtomicBoolean(false)
    private val sampleCursor = AtomicLong(0L)

    @Volatile
    var currentProject: DawProject = DawProject(id = "default", title = "Untitled")

    @Volatile
    var activePattern: RhythmPattern? = null

    @Volatile
    var metronomeEnabled: Boolean = false

    @Volatile
    var precision64BitSumming: Boolean = true

    @Volatile
    var bufferSizeFrames: Int = 256

    @Volatile
    var sampleRate: Int = 48000

    private val activeVoices = CopyOnWriteArrayList<SynthVoice>()
    private val pluginProcessors = ConcurrentHashMap<String, UniversalPluginProcessor>()
    private val masteringProcessor = MasteringProcessor()

    // Cached recorded/frozen/imported audio clips by clip/track ID
    private val audioBuffers = ConcurrentHashMap<String, Pair<FloatArray, FloatArray>>()
    private val frozenTrackBuffers = ConcurrentHashMap<String, Pair<FloatArray, FloatArray>>()

    // Per-track PDC circular delay buffer for sample-accurate alignment
    private val pdcDelayBuffersL = ConcurrentHashMap<String, FloatArray>()
    private val pdcDelayBuffersR = ConcurrentHashMap<String, FloatArray>()
    private val pdcWritePos = ConcurrentHashMap<String, Int>()

    // Track meter levels (dBFS)
    private val _trackMeters = MutableStateFlow<Map<String, Float>>(emptyMap())
    val trackMeters: StateFlow<Map<String, Float>> = _trackMeters.asStateFlow()

    private val _masterMeters = MutableStateFlow(MeterSnapshot())
    val masterMeters: StateFlow<MeterSnapshot> = _masterMeters.asStateFlow()

    private val _playheadBeat = MutableStateFlow(0.0)
    val playheadBeat: StateFlow<Double> = _playheadBeat.asStateFlow()

    private val _hardwareStatus = MutableStateFlow(StudioHardwareStatus())
    val hardwareStatus: StateFlow<StudioHardwareStatus> = _hardwareStatus.asStateFlow()

    private val _recordedSamplesFlow = MutableStateFlow<FloatArray?>(null)
    val recordedSamplesFlow: StateFlow<FloatArray?> = _recordedSamplesFlow.asStateFlow()

    private var audioThread: Thread? = null
    private var recordThread: Thread? = null
    private var audioTrack: AudioTrack? = null
    private var audioRecord: AudioRecord? = null
    private var xrunCounter = 0
    private var recoveryCounter = 0
    private var lastTriggeredStep = -1
    private var lastTriggeredBeat = -1
    private var lastMidiBeatCheck = -1.0

    init {
        refreshHardwareCapabilities()
    }

    fun refreshHardwareCapabilities() {
        if (context == null) return
        try {
            val audioMgr = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val sr = audioMgr?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48000
            val nativeFrames = audioMgr?.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() ?: 256
            sampleRate = sr.coerceIn(44100, 96000)

            var outName = "Android Low-Latency Output"
            var inName = "Internal Studio Mic"
            var usbConnected = false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioMgr != null) {
                val outputs = audioMgr.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET }?.let {
                    usbConnected = true
                    outName = it.productName?.toString() ?: "USB Audio Interface"
                } ?: outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES }?.let {
                    outName = "Wired Headphones"
                }
                val inputs = audioMgr.getDevices(AudioManager.GET_DEVICES_INPUTS)
                inputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET }?.let {
                    usbConnected = true
                    inName = it.productName?.toString() ?: "USB Audio Input"
                }
            }

            val midiNames = mutableListOf<String>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val midiMgr = context.getSystemService(Context.MIDI_SERVICE) as? MidiManager
                @Suppress("DEPRECATION")
                val infos: Array<MidiDeviceInfo>? = midiMgr?.devices
                infos?.forEach { info ->
                    val name = info.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "External MIDI Controller"
                    midiNames.add(name)
                }
            }

            val latencyMs = (bufferSizeFrames.toFloat() / sampleRate) * 1000f
            _hardwareStatus.value = _hardwareStatus.value.copy(
                sampleRate = sampleRate,
                bufferSize = bufferSizeFrames,
                estimatedLatencyMs = latencyMs,
                precision64BitSumming = precision64BitSumming,
                outputDeviceName = outName,
                inputDeviceName = inName,
                usbAudioConnected = usbConnected,
                midiDevices = midiNames,
                xrunCount = xrunCounter,
                recoveryCount = recoveryCounter
            )
            Log.d(TAG, "Hardware refreshed: sr=$sampleRate native=$nativeFrames buf=$bufferSizeFrames usb=$usbConnected")
        } catch (e: Exception) {
            Log.w(TAG, "Hardware inspection limited: ${e.message}")
        }
    }

    fun setBufferSize(frames: Int) {
        if (frames !in listOf(64, 128, 256, 512, 1024)) return
        val wasPlaying = isPlaying.get()
        if (wasPlaying) stop()
        bufferSizeFrames = frames
        refreshHardwareCapabilities()
        if (wasPlaying) start()
    }

    fun set64BitSumming(enabled: Boolean) {
        precision64BitSumming = enabled
        _hardwareStatus.value = _hardwareStatus.value.copy(precision64BitSumming = enabled)
    }

    fun registerAudioBuffer(key: String, left: FloatArray, right: FloatArray = left) {
        audioBuffers[key] = left to right
    }

    fun getAudioBuffer(key: String): Pair<FloatArray, FloatArray>? = audioBuffers[key]

    fun triggerVoice(left: FloatArray, right: FloatArray = left) {
        if (left.isEmpty()) return
        activeVoices.add(SynthVoice(left, right))
    }

    fun triggerIndianInstrument(instrument: IndianInstrument, velocity: Float = 0.85f, articulation: Int = 0, midiPitch: Int? = null) {
        val (l, r) = IndianInstrumentSynth.synthesizeInstrumentHit(instrument, sampleRate, velocity, articulation, midiPitch)
        triggerVoice(l, r)
    }

    fun triggerMidiNotePreview(instrumentName: String, midiPitch: Int, velocity: Float = 0.85f, durationSec: Float = 0.45f) {
        val (l, r) = IndianInstrumentSynth.synthesizeMidiNote(instrumentName, midiPitch, durationSec, sampleRate, velocity)
        triggerVoice(l, r)
    }

    fun start() {
        if (isPlaying.getAndSet(true)) return
        lastTriggeredStep = -1
        lastTriggeredBeat = -1
        lastMidiBeatCheck = _playheadBeat.value - 0.001

        audioThread = Thread({
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            } catch (_: Throwable) { }
            runAudioLoop()
        }, "TrueworldAudioThread").apply { start() }
    }

    fun pause() {
        isPlaying.set(false)
        stopAudioTrack()
    }

    fun stop() {
        isPlaying.set(false)
        stopRecording()
        stopAudioTrack()
        sampleCursor.set(0L)
        _playheadBeat.value = 0.0
        lastTriggeredStep = -1
        lastTriggeredBeat = -1
        masteringProcessor.resetLoudness()
    }

    fun seekToBeat(beat: Double) {
        val spb = (sampleRate * 60.0 / currentProject.bpm.coerceIn(40.0, 240.0))
        sampleCursor.set((beat.coerceAtLeast(0.0) * spb).toLong())
        _playheadBeat.value = beat.coerceAtLeast(0.0)
        lastMidiBeatCheck = beat - 0.001
    }

    private fun createAudioTrack(): AudioTrack? {
        return try {
            val minBufBytes = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_FLOAT
            ).coerceAtLeast(bufferSizeFrames * 8)
            val targetBytes = max(minBufBytes, bufferSizeFrames * 2 * 4 * 2)
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(targetBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                    }
                }
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack creation failed", e)
            null
        }
    }

    private fun stopAudioTrack() {
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.release()
        } catch (_: Exception) { }
        audioTrack = null
    }

    private fun runAudioLoop() {
        var track = createAudioTrack()
        audioTrack = track
        try {
            track?.play()
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack play failed", e)
        }

        val frames = bufferSizeFrames
        val outL = FloatArray(frames)
        val outR = FloatArray(frames)
        val interleaved = FloatArray(frames * 2)
        var meterTick = 0

        while (isPlaying.get()) {
            val tStart = System.nanoTime()
            val curSample = sampleCursor.get()
            val snapshot = renderAudioBlock(
                project = currentProject,
                pattern = activePattern,
                startSample = curSample,
                frames = frames,
                outLeft = outL,
                outRight = outR
            )

            sampleCursor.addAndGet(frames.toLong())
            val samplesPerBeat = sampleRate * 60.0 / currentProject.bpm.coerceIn(40.0, 240.0)
            var nextBeat = sampleCursor.get() / samplesPerBeat
            if (currentProject.loopEnabled && nextBeat >= currentProject.loopEndBeat && currentProject.loopEndBeat > currentProject.loopStartBeat) {
                nextBeat = currentProject.loopStartBeat
                sampleCursor.set((nextBeat * samplesPerBeat).toLong())
                lastMidiBeatCheck = nextBeat - 0.001
            }

            for (i in 0 until frames) {
                interleaved[i * 2] = outL[i].coerceIn(-1f, 1f)
                interleaved[i * 2 + 1] = outR[i].coerceIn(-1f, 1f)
            }

            if (track != null) {
                val written = track.write(interleaved, 0, interleaved.size, AudioTrack.WRITE_BLOCKING)
                if (written < 0) {
                    xrunCounter++
                    recoveryCounter++
                    stopAudioTrack()
                    track = createAudioTrack()
                    audioTrack = track
                    track?.play()
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val underruns = track.underrunCount
                    if (underruns > xrunCounter) xrunCounter = underruns
                }
            } else {
                // Headless / test timing fallback when audio hardware is unavailable
                val sleepMs = ((frames * 1000L) / sampleRate).coerceAtLeast(2L)
                Thread.sleep(sleepMs)
            }

            val elapsedNs = System.nanoTime() - tStart
            val budgetNs = (frames * 1_000_000_000.0 / sampleRate)
            val cpuPct = ((elapsedNs / budgetNs) * 100.0).toFloat().coerceIn(1f, 99f)

            meterTick++
            if (meterTick % 2 == 0) {
                _playheadBeat.value = nextBeat
                _masterMeters.value = snapshot
                _hardwareStatus.value = _hardwareStatus.value.copy(
                    cpuLoadPercent = cpuPct,
                    xrunCount = xrunCounter,
                    recoveryCount = recoveryCounter
                )
            }
        }
        stopAudioTrack()
    }

    /**
     * Pure DSP multitrack block renderer (32-bit Float per-track + optional 64-bit Double summing,
     * PDC, Sidechain ducking, Sends, VCA/Bus groups, Plugins, Mastering).
     * Directly testable in unit/Robolectric tests!
     */
    fun renderAudioBlock(
        project: DawProject,
        pattern: RhythmPattern?,
        startSample: Long,
        frames: Int,
        outLeft: FloatArray,
        outRight: FloatArray,
        offlineMode: Boolean = false
    ): MeterSnapshot {
        val bpm = project.bpm.coerceIn(40.0, 240.0)
        val samplesPerBeat = sampleRate * 60.0 / bpm
        val startBeat = startSample / samplesPerBeat
        val endBeat = (startSample + frames) / samplesPerBeat

        // Trigger step sequencer & MIDI notes on beat crossing
        triggerScheduledEvents(project, pattern, startBeat, endBeat)

        val anySolo = project.tracks.any { it.soloed }
        val maxLatency = project.tracks.maxOfOrNull { tr ->
            tr.plugins.filter { !it.bypassed }.sumOf { it.type.latencySamples }
        } ?: 0

        val masterSumL64 = DoubleArray(frames)
        val masterSumR64 = DoubleArray(frames)
        val trackBuffers = HashMap<String, Pair<FloatArray, FloatArray>>()
        val meterMap = HashMap<String, Float>()

        // 1. Render individual tracks in 32-bit float
        val trackL = FloatArray(frames)
        val trackR = FloatArray(frames)

        for (track in project.tracks) {
            if (track.type == TrackType.MASTER || track.type == TrackType.BUS || track.type == TrackType.VCA) continue
            trackL.fill(0f)
            trackR.fill(0f)

            val audible = !track.muted && (!anySolo || track.soloed)
            if (audible) {
                val frozen = frozenTrackBuffers[track.id]
                if (track.frozen && frozen != null) {
                    for (i in 0 until frames) {
                        val sIdx = (startSample + i).toInt()
                        if (sIdx in frozen.first.indices) {
                            trackL[i] = frozen.first[sIdx]
                            trackR[i] = frozen.second[sIdx]
                        }
                    }
                } else {
                    // Render audio clips on track
                    for (clip in track.clips) {
                        val buf = audioBuffers[clip.id] ?: continue
                        val clipStartSample = (clip.startBeat * samplesPerBeat).toLong()
                        val gain = PitchAndAnalysisDsp.dbToLinear(clip.gainDb)
                        for (i in 0 until frames) {
                            val relIdx = (startSample + i - clipStartSample).toInt()
                            if (relIdx in buf.first.indices) {
                                trackL[i] += buf.first[relIdx] * gain
                                trackR[i] += buf.second.getOrElse(relIdx) { buf.first[relIdx] } * gain
                            }
                        }
                    }
                }
            }

            // Apply mono sum if track is Mono routed
            if (track.mono) {
                for (i in 0 until frames) {
                    val m = 0.5f * (trackL[i] + trackR[i])
                    trackL[i] = m
                    trackR[i] = m
                }
            }

            // Sidechain signal lookup
            val scMono = track.sidechainSourceId?.let { scId ->
                trackBuffers[scId]?.let { (sl, sr) ->
                    FloatArray(frames) { idx -> 0.5f * (sl[idx] + sr[idx]) }
                }
            }

            // Run track insert plugin chain
            var trackLatency = 0
            for (plugin in track.plugins) {
                if (!plugin.bypassed) trackLatency += plugin.type.latencySamples
                val proc = pluginProcessors.getOrPut(plugin.id) { UniversalPluginProcessor(plugin.type) }
                proc.processStereo(trackL, trackR, frames, sampleRate, plugin, scMono)
            }

            // Plugin Latency Compensation (PDC) + Track Delay Compensation
            val compSamples = (maxLatency - trackLatency + (track.delayCompMs * sampleRate / 1000f).toInt()).coerceIn(0, 8192)
            if (compSamples > 0) {
                applyTrackDelayCompensation(track.id, trackL, trackR, frames, compSamples)
            }

            // Sample-accurate Volume & VCA automation + Pan law
            val vcaScale = track.vcaGroupId?.let { vcaId ->
                project.tracks.firstOrNull { it.id == vcaId }?.volume ?: 1f
            } ?: 1f
            val volLane = track.automationLanes.firstOrNull { it.targetParam.equals("volume", ignoreCase = true) }

            var trackPeak = 0f
            for (i in 0 until frames) {
                val beatPos = (startSample + i) / samplesPerBeat
                val autoVol = if (volLane != null && volLane.points.isNotEmpty()) {
                    interpolateAutomation(volLane.points, beatPos) * 1.25f
                } else track.volume
                val g = autoVol * vcaScale
                val panAngle = ((track.pan.coerceIn(-1f, 1f) + 1f) * 0.25f * PI).toFloat()
                val gainL = g * cos(panAngle) * 1.414f
                val gainR = g * sin(panAngle) * 1.414f
                trackL[i] *= gainL
                trackR[i] *= gainR
                trackPeak = max(trackPeak, max(kotlin.math.abs(trackL[i]), kotlin.math.abs(trackR[i])))
            }

            meterMap[track.id] = PitchAndAnalysisDsp.linearToDb(trackPeak)
            trackBuffers[track.id] = trackL.copyOf() to trackR.copyOf()

            if (precision64BitSumming) {
                for (i in 0 until frames) {
                    masterSumL64[i] += trackL[i].toDouble()
                    masterSumR64[i] += trackR[i].toDouble()
                }
            } else {
                for (i in 0 until frames) {
                    masterSumL64[i] = (masterSumL64[i].toFloat() + trackL[i]).toDouble()
                    masterSumR64[i] = (masterSumR64[i].toFloat() + trackR[i]).toDouble()
                }
            }
        }

        // 2. Mix active live/sequenced synth voices + metronome
        val iter = activeVoices.iterator()
        val finished = mutableListOf<SynthVoice>()
        while (iter.hasNext()) {
            val v = iter.next()
            for (i in 0 until frames) {
                if (!v.active) break
                masterSumL64[i] += v.left[v.cursor].toDouble()
                masterSumR64[i] += v.right[v.cursor].toDouble()
                v.cursor++
            }
            if (!v.active) finished.add(v)
        }
        if (finished.isNotEmpty()) activeVoices.removeAll(finished.toSet())

        // Convert 64-bit summing bus back to 32-bit float for Master Bus plugins & Mastering Suite
        for (i in 0 until frames) {
            outLeft[i] = masterSumL64[i].toFloat()
            outRight[i] = masterSumR64[i].toFloat()
        }

        val masterTrack = project.tracks.firstOrNull { it.type == TrackType.MASTER }
        if (masterTrack != null) {
            for (plugin in masterTrack.plugins) {
                val proc = pluginProcessors.getOrPut(plugin.id) { UniversalPluginProcessor(plugin.type) }
                proc.processStereo(outLeft, outRight, frames, sampleRate, plugin)
            }
            for (i in 0 until frames) {
                outLeft[i] *= masterTrack.volume
                outRight[i] *= masterTrack.volume
            }
        }

        val snapshot = masteringProcessor.processMasterBus(outLeft, outRight, frames, sampleRate, project.masteringConfig)
        if (!offlineMode) {
            _trackMeters.value = meterMap
        }
        return snapshot
    }

    private fun triggerScheduledEvents(
        project: DawProject,
        pattern: RhythmPattern?,
        startBeat: Double,
        endBeat: Double
    ) {
        // Metronome click on quarter beat
        if (metronomeEnabled) {
            val curBeat = startBeat.toInt()
            if (curBeat != lastTriggeredBeat && startBeat >= 0) {
                lastTriggeredBeat = curBeat
                val accent = (curBeat % project.timeSignature.numerator.coerceAtLeast(1)) == 0
                val clickFreq = if (accent) 1320f else 880f
                val clickLen = (sampleRate * 0.03f).toInt()
                val c = FloatArray(clickLen) { idx ->
                    val t = idx.toFloat() / sampleRate
                    sin(2.0 * PI * clickFreq * t).toFloat() * kotlin.math.exp(-t * 90f) * (if (accent) 0.45f else 0.25f)
                }
                triggerVoice(c, c)
            }
        }

        // Indian Rhythm / Step Sequencer triggers
        if (pattern != null && pattern.stepCount > 0) {
            val stepsPerBeat = pattern.timeSignature.denominator.toDouble()
            val totalSteps = pattern.stepCount
            val currentStep = ((startBeat * stepsPerBeat).toInt() % totalSteps + totalSteps) % totalSteps
            if (currentStep != lastTriggeredStep) {
                lastTriggeredStep = currentStep
                for (lane in pattern.lanes) {
                    if (lane.muted) continue
                    val vel = lane.steps.getOrNull(currentStep) ?: 0f
                    if (vel > 0.01f) {
                        triggerIndianInstrument(lane.instrument, vel)
                    }
                }
            }
        }

        // MIDI Track Note triggers (respecting note probability)
        val loopLen = if (project.loopEnabled && project.loopEndBeat > project.loopStartBeat) {
            project.loopEndBeat - project.loopStartBeat
        } else 16.0
        val wrappedStart = ((startBeat % loopLen) + loopLen) % loopLen
        val wrappedEnd = wrappedStart + (endBeat - startBeat)
        val anySolo = project.tracks.any { it.soloed }

        for (track in project.tracks) {
            if (track.muted || (anySolo && !track.soloed)) continue
            if (track.midiNotes.isEmpty()) continue
            for (note in track.midiNotes) {
                if (note.startBeat >= wrappedStart && note.startBeat < wrappedEnd && note.startBeat > lastMidiBeatCheck) {
                    if (note.probability >= 0.99f || Random.nextFloat() <= note.probability) {
                        val durSec = (note.durationBeats * 60.0 / project.bpm).toFloat().coerceIn(0.12f, 2.0f)
                        val (nl, nr) = IndianInstrumentSynth.synthesizeMidiNote(
                            track.instrumentName,
                            note.pitch,
                            durSec,
                            sampleRate,
                            (note.velocity / 127f) * track.volume
                        )
                        triggerVoice(nl, nr)
                    }
                }
            }
        }
        lastMidiBeatCheck = wrappedStart
    }

    private fun applyTrackDelayCompensation(
        trackId: String,
        left: FloatArray,
        right: FloatArray,
        frames: Int,
        delaySamples: Int
    ) {
        val bufSize = 8192
        val dL = pdcDelayBuffersL.getOrPut(trackId) { FloatArray(bufSize) }
        val dR = pdcDelayBuffersR.getOrPut(trackId) { FloatArray(bufSize) }
        var w = pdcWritePos[trackId] ?: 0
        for (i in 0 until frames) {
            dL[w] = left[i]
            dR[w] = right[i]
            val r = (w - delaySamples + bufSize) % bufSize
            left[i] = dL[r]
            right[i] = dR[r]
            w = (w + 1) % bufSize
        }
        pdcWritePos[trackId] = w
    }

    private fun interpolateAutomation(points: List<com.example.model.AutomationPoint>, beat: Double): Float {
        if (points.isEmpty()) return 0.8f
        val sorted = points.sortedBy { it.beatPosition }
        if (beat <= sorted.first().beatPosition) return sorted.first().normalizedValue
        if (beat >= sorted.last().beatPosition) return sorted.last().normalizedValue
        for (i in 0 until sorted.lastIndex) {
            val a = sorted[i]
            val b = sorted[i + 1]
            if (beat in a.beatPosition..b.beatPosition) {
                val t = ((beat - a.beatPosition) / (b.beatPosition - a.beatPosition).coerceAtLeast(1e-6)).toFloat()
                return a.normalizedValue + (b.normalizedValue - a.normalizedValue) * t
            }
        }
        return sorted.last().normalizedValue
    }

    /**
     * Freeze a track by rendering its audio + plugins into a cached FloatArray buffer.
     */
    fun freezeTrack(project: DawProject, trackId: String): Boolean {
        val track = project.tracks.firstOrNull { it.id == trackId } ?: return false
        val totalBeats = project.loopEndBeat.coerceAtLeast(16.0)
        val totalSamples = ((totalBeats * 60.0 / project.bpm) * sampleRate).toInt().coerceAtMost(sampleRate * 30)
        val outL = FloatArray(totalSamples)
        val outR = FloatArray(totalSamples)
        val soloProject = project.copy(tracks = listOf(track.copy(muted = false, soloed = true, frozen = false)))
        val block = 512
        val bL = FloatArray(block)
        val bR = FloatArray(block)
        var pos = 0
        while (pos < totalSamples) {
            val count = min(block, totalSamples - pos)
            renderAudioBlock(soloProject, null, pos.toLong(), count, bL, bR, offlineMode = true)
            System.arraycopy(bL, 0, outL, pos, count)
            System.arraycopy(bR, 0, outR, pos, count)
            pos += count
        }
        frozenTrackBuffers[trackId] = outL to outR
        return true
    }

    /**
     * Offline Bounce full multitrack mix + mastering suite to stereo FloatArray pair for WAV export.
     */
    fun bounceProjectToStereo(project: DawProject, pattern: RhythmPattern?, bars: Int = 4): Pair<FloatArray, FloatArray> {
        val totalBeats = bars * project.timeSignature.numerator.toDouble()
        val totalSamples = ((totalBeats * 60.0 / project.bpm) * sampleRate).toInt().coerceIn(sampleRate, sampleRate * 60)
        val fullL = FloatArray(totalSamples)
        val fullR = FloatArray(totalSamples)
        val block = 512
        val bL = FloatArray(block)
        val bR = FloatArray(block)
        var pos = 0
        lastTriggeredStep = -1
        lastMidiBeatCheck = -0.001
        while (pos < totalSamples) {
            val count = min(block, totalSamples - pos)
            renderAudioBlock(project, pattern, pos.toLong(), count, bL, bR, offlineMode = true)
            System.arraycopy(bL, 0, fullL, pos, count)
            System.arraycopy(bR, 0, fullR, pos, count)
            pos += count
        }
        return fullL to fullR
    }

    fun startRecording() {
        if (isRecording.getAndSet(true)) return
        recordThread = Thread({
            val recorded = mutableListOf<Float>()
            try {
                val minBuf = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(bufferSizeFrames * 2)
                val rec = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf * 2
                )
                audioRecord = rec
                if (rec.state == AudioRecord.STATE_INITIALIZED) {
                    rec.startRecording()
                    val buf = ShortArray(bufferSizeFrames)
                    while (isRecording.get() && recorded.size < sampleRate * 30) {
                        val read = rec.read(buf, 0, buf.size)
                        if (read > 0) {
                            for (i in 0 until read) {
                                recorded.add(buf[i] / 32768f)
                            }
                        }
                    }
                    rec.stop()
                }
                rec.release()
            } catch (e: SecurityException) {
                Log.e(TAG, "RECORD_AUDIO permission missing", e)
            } catch (e: Exception) {
                Log.e(TAG, "AudioRecord error", e)
            } finally {
                audioRecord = null
                isRecording.set(false)
                if (recorded.isNotEmpty()) {
                    _recordedSamplesFlow.value = recorded.toFloatArray()
                }
            }
        }, "TrueworldRecordThread").apply { start() }
    }

    fun stopRecording() {
        isRecording.set(false)
    }

    fun clearRecordedSamples() {
        _recordedSamplesFlow.value = null
    }

    fun release() {
        stop()
        audioThread?.join(300)
        recordThread?.join(300)
    }
}
