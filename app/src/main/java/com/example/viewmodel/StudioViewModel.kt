package com.example.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioAnalysisReport
import com.example.audio.IndianInstrumentSynth
import com.example.audio.MasteringProcessor
import com.example.audio.MidiCompositionTools
import com.example.audio.MultitrackAudioEngine
import com.example.audio.PitchAndAnalysisDsp
import com.example.audio.PluginCatalog
import com.example.audio.WavAiffCodec
import com.example.data.*
import com.example.model.*
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.UUID

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class StudioViewModel(
    private val appContext: Context,
    private val currentUserId: String,
    private val cloudRepo: CloudProjectRepository,
    private val aiRepo: AiMusicAssistantRepository,
    private val prefsRepo: StudioPreferencesRepository,
    private val localDb: TrueworldDatabase
) : ViewModel() {

    val audioEngine = MultitrackAudioEngine(appContext)
    private val studioDao = localDb.studioDao()

    private val _project = MutableStateFlow(ProjectSerializer.defaultProject())
    val project: StateFlow<DawProject> = _project.asStateFlow()

    private val _selectedTrackId = MutableStateFlow("trk_indian")
    val selectedTrackId: StateFlow<String> = _selectedTrackId.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _metronomeOn = MutableStateFlow(false)
    val metronomeOn: StateFlow<Boolean> = _metronomeOn.asStateFlow()

    private val _timelineZoom = MutableStateFlow(1.0f)
    val timelineZoom: StateFlow<Float> = _timelineZoom.asStateFlow()

    private val _activeTool = MutableStateFlow(ArrangementTool.ARROW)
    val activeTool: StateFlow<ArrangementTool> = _activeTool.asStateFlow()
    fun setActiveTool(tool: ArrangementTool) { _activeTool.value = tool }

    private val _presetPatterns = MutableStateFlow(ProjectSerializer.createPresetPatterns())
    private val _activePattern = MutableStateFlow(_presetPatterns.value.first())
    val activePattern: StateFlow<RhythmPattern> = _activePattern.asStateFlow()
    val presetPatterns: StateFlow<List<RhythmPattern>> = _presetPatterns.asStateFlow()

    private val _samplerPads = MutableStateFlow(createDefaultSamplerPads())
    val samplerPads: StateFlow<List<SamplerPad>> = _samplerPads.asStateFlow()

    private val _selectedSamplerPadId = MutableStateFlow("pad_0")
    val selectedSamplerPadId: StateFlow<String> = _selectedSamplerPadId.asStateFlow()

    private val _vocalChain = MutableStateFlow(VocalChainType.BOLLYWOOD_VOCAL)
    val vocalChain: StateFlow<VocalChainType> = _vocalChain.asStateFlow()

    private val _vocalParams = MutableStateFlow(VocalChainType.BOLLYWOOD_VOCAL.defaultParams)
    val vocalParams: StateFlow<VocalProcessingParams> = _vocalParams.asStateFlow()

    private val _vocalTakes = MutableStateFlow<List<VocalTake>>(emptyList())
    val vocalTakes: StateFlow<List<VocalTake>> = _vocalTakes.asStateFlow()

    private val _undoHistory = MutableStateFlow<List<UndoHistoryItem>>(emptyList())
    val undoHistory: StateFlow<List<UndoHistoryItem>> = _undoHistory.asStateFlow()
    private val undoSnapshots = ArrayDeque<Pair<UndoHistoryItem, String>>()
    private val redoSnapshots = ArrayDeque<Pair<UndoHistoryItem, String>>()

    private val _autosaveRecovery = MutableStateFlow<AutosaveRecoveryEntity?>(null)
    val autosaveRecovery: StateFlow<AutosaveRecoveryEntity?> = _autosaveRecovery.asStateFlow()

    private val _aiState = MutableStateFlow<UiState<AiMusicSuggestion>?>(null)
    val aiState: StateFlow<UiState<AiMusicSuggestion>?> = _aiState.asStateFlow()

    private val _audioReport = MutableStateFlow<AudioAnalysisReport?>(null)
    val audioReport: StateFlow<AudioAnalysisReport?> = _audioReport.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _lastShareCode = MutableStateFlow<String?>(null)
    val lastShareCode: StateFlow<String?> = _lastShareCode.asStateFlow()

    val settings: StateFlow<StudioSettings> = prefsRepo.settingsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        StudioSettings()
    )

    val localProjects: StateFlow<List<LocalProjectEntity>> = studioDao.observeProjects().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        emptyList()
    )

    val localPatterns: StateFlow<List<CustomPatternEntity>> = studioDao.observeCustomPatterns().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        emptyList()
    )

    val soundAssets: StateFlow<List<SoundAssetEntity>> = studioDao.observeSoundAssets().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        emptyList()
    )

    val cloudProjects: StateFlow<UiState<List<CloudProject>>> = if (currentUserId.isBlank()) {
        MutableStateFlow<UiState<List<CloudProject>>>(UiState.Success(emptyList())).asStateFlow()
    } else {
        cloudRepo.observeCloudProjects(currentUserId)
            .map<List<CloudProject>, UiState<List<CloudProject>>> { UiState.Success(it) }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                val root = fsErr ?: error
                Log.w("StudioVM", "Cloud projects error", root)
                emit(UiState.Error(root.message ?: "Failed to sync cloud projects"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)
    }

    val sharedPatterns: StateFlow<UiState<List<CloudSharedPattern>>> = if (currentUserId.isBlank()) {
        MutableStateFlow<UiState<List<CloudSharedPattern>>>(UiState.Success(emptyList())).asStateFlow()
    } else {
        cloudRepo.observeSharedPatterns()
            .map<List<CloudSharedPattern>, UiState<List<CloudSharedPattern>>> { UiState.Success(it) }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                val root = fsErr ?: error
                emit(UiState.Error(root.message ?: "Failed to load community rhythm patterns"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)
    }

    private val _selectedProjectVersions = MutableStateFlow<List<CloudProjectVersion>>(emptyList())
    val selectedProjectVersions: StateFlow<List<CloudProjectVersion>> = _selectedProjectVersions.asStateFlow()

    init {
        audioEngine.currentProject = _project.value
        audioEngine.activePattern = _activePattern.value
        seedDemoVocalTake()

        viewModelScope.launch {
            settings.collect { s ->
                audioEngine.setBufferSize(s.bufferSizeFrames)
                audioEngine.set64BitSumming(s.precision64BitSumming)
            }
        }
        viewModelScope.launch {
            val saved = studioDao.getAutosave()
            if (saved != null) _autosaveRecovery.value = saved
        }
        viewModelScope.launch {
            audioEngine.recordedSamplesFlow.filterNotNull().collect { samples ->
                processNewRecording(samples)
                audioEngine.clearRecordedSamples()
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(15_000L)
                if (settings.value.autosaveEnabled) {
                    val p = _project.value
                    studioDao.saveAutosave(
                        AutosaveRecoveryEntity(
                            slotId = 1,
                            projectId = p.id,
                            projectTitle = p.title,
                            payloadJson = ProjectSerializer.projectToJson(p)
                        )
                    )
                }
            }
        }
    }

    private fun seedDemoVocalTake() {
        val sr = audioEngine.sampleRate
        val len = sr * 2
        val pcm = FloatArray(len) { i ->
            val t = i.toFloat() / sr
            val freq = if (t < 1.0f) 293.66f else 329.63f // D4 -> E4
            kotlin.math.sin(2.0 * Math.PI * freq * t).toFloat() * 0.45f
        }
        val contour = PitchAndAnalysisDsp.analyzePitchContour(pcm, sr, 2, _project.value.scale)
        val wave = ProjectArchiveManager.buildWaveformPreview(pcm, 64)
        audioEngine.registerAudioBuffer("vocal_take_1", pcm, pcm)
        _vocalTakes.value = listOf(
            VocalTake("vocal_take_1", "Take 1 (Reference)", sr, 2.0f, wave, contour, true)
        )
    }

    private fun createDefaultSamplerPads(): List<SamplerPad> {
        val sr = 48000
        return listOf(
            IndianInstrument.SAMBALPURI_MANDAR,
            IndianInstrument.DHOLAK,
            IndianInstrument.TABLA,
            IndianInstrument.SITAR,
            IndianInstrument.BANSURI,
            IndianInstrument.MANJIRA,
            IndianInstrument.NISHAN,
            IndianInstrument.TASHA
        ).mapIndexed { idx, inst ->
            val (l, r) = IndianInstrumentSynth.synthesizeInstrumentHit(inst, sr, 0.9f)
            val id = "pad_$idx"
            audioEngine.registerAudioBuffer(id, l, r)
            val preview = ProjectArchiveManager.buildWaveformPreview(l, 48)
            val slices = MidiCompositionTools.autoSliceTransients(l, 0.25f, 8)
            SamplerPad(
                id = id,
                name = inst.displayName,
                formatLabel = "DSP 32f",
                sampleRate = sr,
                totalSamples = l.size,
                waveformPreview = preview,
                slices = slices,
                loopEndSample = l.size,
                rootMidiNote = 48 + idx * 2,
                keyLow = 36 + idx * 4,
                keyHigh = 39 + idx * 4
            )
        }
    }

    private fun commitProjectEdit(description: String, transform: (DawProject) -> DawProject) {
        val current = _project.value
        val snapshot = ProjectSerializer.projectToJson(current)
        val entry = UndoHistoryItem(UUID.randomUUID().toString(), description, System.currentTimeMillis())
        undoSnapshots.addFirst(entry to snapshot)
        if (undoSnapshots.size > 40) undoSnapshots.removeLast()
        redoSnapshots.clear()
        _undoHistory.value = undoSnapshots.map { it.first }

        val updated = transform(current)
        _project.value = updated
        audioEngine.currentProject = updated
    }

    fun undo() {
        val prev = undoSnapshots.removeFirstOrNull() ?: return
        val curJson = ProjectSerializer.projectToJson(_project.value)
        redoSnapshots.addFirst(prev.first to curJson)
        _undoHistory.value = undoSnapshots.map { it.first }
        ProjectSerializer.projectFromJson(prev.second)?.let { restored ->
            _project.value = restored
            audioEngine.currentProject = restored
            _statusBanner.value = "Undid: ${prev.first.description}"
        }
    }

    fun redo() {
        val next = redoSnapshots.removeFirstOrNull() ?: return
        val curJson = ProjectSerializer.projectToJson(_project.value)
        undoSnapshots.addFirst(next.first to curJson)
        _undoHistory.value = undoSnapshots.map { it.first }
        ProjectSerializer.projectFromJson(next.second)?.let { restored ->
            _project.value = restored
            audioEngine.currentProject = restored
            _statusBanner.value = "Redid: ${next.first.description}"
        }
    }

    fun togglePlay() {
        if (_isPlaying.value) {
            audioEngine.pause()
            _isPlaying.value = false
        } else {
            audioEngine.start()
            _isPlaying.value = true
        }
    }

    fun pauseTransport() {
        audioEngine.pause()
        _isPlaying.value = false
    }

    fun stopTransport() {
        audioEngine.stop()
        _isPlaying.value = false
        _isRecording.value = false
    }

    fun rewindTransport() {
        val nextBeat = (audioEngine.playheadBeat.value - 4.0).coerceAtLeast(0.0)
        audioEngine.seekToBeat(nextBeat)
    }

    fun forwardTransport() {
        val nextBeat = (audioEngine.playheadBeat.value + 4.0).coerceAtMost(64.0)
        audioEngine.seekToBeat(nextBeat)
    }

    fun toggleRecord() {
        if (_isRecording.value) {
            audioEngine.stopRecording()
            _isRecording.value = false
        } else {
            audioEngine.startRecording()
            _isRecording.value = true
            if (!_isPlaying.value) {
                audioEngine.start()
                _isPlaying.value = true
            }
        }
    }

    fun toggleLoop() = commitProjectEdit("Toggle Loop") { it.copy(loopEnabled = !it.loopEnabled) }

    fun toggleSnap() = commitProjectEdit("Toggle Snap") { it.copy(snapEnabled = !it.snapEnabled) }

    fun setGridResolution(res: GridResolution) = commitProjectEdit("Grid ${res.label}") {
        it.copy(gridResolution = res)
    }

    fun toggleMetronome() {
        val next = !_metronomeOn.value
        _metronomeOn.value = next
        audioEngine.metronomeEnabled = next
    }

    fun updateBpm(bpm: Double) {
        val clamped = bpm.coerceIn(40.0, 240.0)
        commitProjectEdit("Set BPM ${clamped.toInt()}") { it.copy(bpm = clamped) }
        _activePattern.value = _activePattern.value.copy(bpm = clamped)
        audioEngine.activePattern = _activePattern.value
    }

    fun updateKeyAndScale(key: String, scale: MusicalScale) {
        commitProjectEdit("Change Key $key ${scale.displayName}") {
            it.copy(musicalKey = key, scale = scale)
        }
    }

    fun updateTimeSignature(ts: TimeSignature) {
        commitProjectEdit("Time Signature ${ts.label}") { it.copy(timeSignature = ts) }
        val newSteps = (ts.numerator * 4).coerceIn(8, 28)
        val p = _activePattern.value
        val updatedLanes = p.lanes.map { lane ->
            val adjusted = List(newSteps) { idx -> lane.steps.getOrElse(idx) { 0f } }
            lane.copy(steps = adjusted)
        }
        _activePattern.value = p.copy(timeSignature = ts, stepCount = newSteps, lanes = updatedLanes)
        audioEngine.activePattern = _activePattern.value
    }

    fun selectTrack(trackId: String) {
        _selectedTrackId.value = trackId
    }

    fun addTrack(type: TrackType) {
        val colors = mapOf(
            TrackType.AUDIO to 0xFF22C55EL,
            TrackType.MIDI to 0xFFA855F7L,
            TrackType.INSTRUMENT to 0xFFA855F7L,
            TrackType.DRUM to 0xFF14B8A6L,
            TrackType.INDIAN_RHYTHM to 0xFFF97316L,
            TrackType.VOCAL to 0xFF22C55EL,
            TrackType.SAMPLER to 0xFF14B8A6L,
            TrackType.AUTOMATION to 0xFFF97316L,
            TrackType.BUS to 0xFFF59E0BL,
            TrackType.VCA to 0xFF6B7585L,
            TrackType.FOLDER to 0xFF2F8BFFL
        )
        val id = "trk_${UUID.randomUUID().toString().take(6)}"
        val newTrack = DawTrack(
            id = id,
            name = "${type.label} ${_project.value.tracks.size + 1}",
            type = type,
            colorArgb = colors[type] ?: 0xFF22C55EL,
            instrumentName = if (type == TrackType.INDIAN_RHYTHM || type == TrackType.DRUM) "Sambalpuri Mandar" else "Harmonium"
        )
        commitProjectEdit("Add ${type.label} Track") {
            it.copy(tracks = it.tracks + newTrack)
        }
        _selectedTrackId.value = id
    }

    fun reorderTracks(fromIndex: Int, toIndex: Int) {
        val tracks = _project.value.tracks.toMutableList()
        if (fromIndex !in tracks.indices || toIndex !in tracks.indices || fromIndex == toIndex) return
        val moved = tracks.removeAt(fromIndex)
        tracks.add(toIndex, moved)
        commitProjectEdit("Reorder ${moved.name}") { it.copy(tracks = tracks) }
    }

    fun updateTrack(trackId: String, label: String = "Update Track", transform: (DawTrack) -> DawTrack) {
        commitProjectEdit(label) { proj ->
            proj.copy(tracks = proj.tracks.map { if (it.id == trackId) transform(it) else it })
        }
    }

    fun freezeSelectedTrack(trackId: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val ok = audioEngine.freezeTrack(_project.value, trackId)
            if (ok) {
                commitProjectEdit("Freeze Track") { proj ->
                    proj.copy(tracks = proj.tracks.map { if (it.id == trackId) it.copy(frozen = !it.frozen) else it })
                }
                _statusBanner.value = "Track freeze toggled."
            }
        }
    }

    // Plugin Rack Actions
    fun addPluginToSelectedTrack(type: PluginType) {
        val inst = PluginCatalog.createInstance("plg_${UUID.randomUUID().toString().take(6)}", type)
        updateTrack(_selectedTrackId.value, "Insert ${type.displayName}") { tr ->
            tr.copy(plugins = tr.plugins + inst)
        }
    }

    fun updatePluginInstance(trackId: String, updated: PluginInstance) {
        updateTrack(trackId, "Edit ${updated.type.displayName}") { tr ->
            tr.copy(plugins = tr.plugins.map { if (it.id == updated.id) updated else it })
        }
    }

    fun removePluginFromTrack(trackId: String, pluginId: String) {
        updateTrack(trackId, "Remove Plugin") { tr ->
            tr.copy(plugins = tr.plugins.filterNot { it.id == pluginId })
        }
    }

    // Piano Roll / MIDI Actions
    fun toggleMidiNote(pitch: Int, startBeat: Double, durationBeats: Double = 0.5) {
        val trackId = _selectedTrackId.value
        val track = _project.value.tracks.firstOrNull { it.id == trackId } ?: return
        val existing = track.midiNotes.firstOrNull {
            it.pitch == pitch && kotlin.math.abs(it.startBeat - startBeat) < 0.2
        }
        if (existing != null) {
            updateTrack(trackId, "Remove MIDI Note") {
                it.copy(midiNotes = it.midiNotes.filterNot { n -> n.id == existing.id })
            }
        } else {
            audioEngine.triggerMidiNotePreview(track.instrumentName, pitch)
            val note = MidiNote("mn_${UUID.randomUUID().toString().take(6)}", pitch, startBeat, durationBeats, 102)
            updateTrack(trackId, "Add MIDI Note") { it.copy(midiNotes = it.midiNotes + note) }
        }
    }

    fun quantizeSelectedTrackMidi() {
        val id = _selectedTrackId.value
        updateTrack(id, "Quantize MIDI") { it.copy(midiNotes = MidiCompositionTools.quantizeNotes(it.midiNotes)) }
    }

    fun humanizeSelectedTrackMidi() {
        val id = _selectedTrackId.value
        updateTrack(id, "Humanize MIDI") { it.copy(midiNotes = MidiCompositionTools.humanizeNotes(it.midiNotes)) }
    }

    fun randomizeSelectedTrackVelocities() {
        val id = _selectedTrackId.value
        updateTrack(id, "Randomize Velocity") { it.copy(midiNotes = MidiCompositionTools.randomizeVelocities(it.midiNotes)) }
    }

    fun arpeggiateSelectedTrackMidi() {
        val id = _selectedTrackId.value
        updateTrack(id, "Arpeggiate MIDI") { it.copy(midiNotes = MidiCompositionTools.arpeggiateNotes(it.midiNotes)) }
    }

    // Indian Rhythm & Step Sequencer Actions
    fun selectRhythmPattern(pattern: RhythmPattern) {
        _activePattern.value = pattern
        audioEngine.activePattern = pattern
    }

    fun togglePatternStep(instrument: IndianInstrument, stepIndex: Int) {
        val p = _activePattern.value
        val updatedLanes = p.lanes.map { lane ->
            if (lane.instrument == instrument) {
                val nextSteps = lane.steps.toMutableList()
                if (stepIndex in nextSteps.indices) {
                    nextSteps[stepIndex] = if (nextSteps[stepIndex] > 0.05f) 0f else 0.9f
                }
                lane.copy(steps = nextSteps)
            } else lane
        }
        val next = p.copy(lanes = updatedLanes, isPreset = false)
        _activePattern.value = next
        audioEngine.activePattern = next
        if ((updatedLanes.firstOrNull { it.instrument == instrument }?.steps?.getOrNull(stepIndex) ?: 0f) > 0f) {
            audioEngine.triggerIndianInstrument(instrument, 0.9f)
        }
    }

    fun addIndianInstrumentLane(instrument: IndianInstrument) {
        val p = _activePattern.value
        if (p.lanes.any { it.instrument == instrument }) return
        val newLane = RhythmLane(instrument, List(p.stepCount) { 0f })
        val next = p.copy(lanes = p.lanes + newLane, isPreset = false)
        _activePattern.value = next
        audioEngine.activePattern = next
    }

    fun saveAndShareCurrentPattern(name: String, sharePublicly: Boolean) {
        val current = _activePattern.value.copy(
            id = if (_activePattern.value.isPreset) "pat_${UUID.randomUUID().toString().take(8)}" else _activePattern.value.id,
            name = name.ifBlank { _activePattern.value.name },
            isPreset = false
        )
        _activePattern.value = current
        audioEngine.activePattern = current
        viewModelScope.launch {
            val json = ProjectSerializer.patternToJson(current)
            studioDao.upsertPattern(
                CustomPatternEntity(
                    id = current.id,
                    name = current.name,
                    style = current.style.displayName,
                    timeSignature = current.timeSignature.label,
                    bpm = current.bpm,
                    patternJson = json
                )
            )
            if (currentUserId.isBlank()) {
                _statusBanner.value = "Pattern '${current.name}' saved locally to studio library."
            } else {
                val res = cloudRepo.saveAndSharePattern(current, sharePublicly)
                _statusBanner.value = res.fold(
                    onSuccess = { if (sharePublicly) "Pattern saved & shared to community!" else "Pattern saved privately to cloud." },
                    onFailure = { "Saved locally. Cloud sync error: ${it.localizedMessage}" }
                )
            }
        }
    }

    // Sampler Actions
    fun selectSamplerPad(padId: String) {
        _selectedSamplerPadId.value = padId
    }

    fun triggerSamplerPad(pad: SamplerPad, midiNote: Int = pad.rootMidiNote) {
        val buf = audioEngine.getAudioBuffer(pad.id) ?: (FloatArray(0) to FloatArray(0))
        val (l, r) = IndianInstrumentSynth.renderSamplerPadVoice(pad, buf.first, buf.second, midiNote, 0.92f, audioEngine.sampleRate)
        audioEngine.triggerVoice(l, r)
    }

    fun updateSamplerPad(pad: SamplerPad) {
        _samplerPads.value = _samplerPads.value.map { if (it.id == pad.id) pad else it }
    }

    fun importAudioToSampler(uri: Uri, displayName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val decoded = appContext.contentResolver.openInputStream(uri)?.use {
                WavAiffCodec.decodeAudioStream(it)
            }
            if (decoded == null) {
                _statusBanner.value = "Unsupported audio file. Please choose WAV or AIFF."
                return@launch
            }
            val padId = _selectedSamplerPadId.value
            audioEngine.registerAudioBuffer(padId, decoded.left, decoded.right)
            val preview = ProjectArchiveManager.buildWaveformPreview(decoded.left, 48)
            val slices = MidiCompositionTools.autoSliceTransients(decoded.left, 0.25f, 12)
            _samplerPads.value = _samplerPads.value.map { pad ->
                if (pad.id == padId) {
                    pad.copy(
                        name = displayName.substringBeforeLast(".").take(24),
                        sourceUri = uri.toString(),
                        formatLabel = decoded.formatLabel,
                        sampleRate = decoded.sampleRate,
                        totalSamples = decoded.left.size,
                        waveformPreview = preview,
                        slices = slices,
                        loopStartSample = 0,
                        loopEndSample = decoded.left.size
                    )
                } else pad
            }
            studioDao.upsertSoundAsset(
                SoundAssetEntity(
                    id = UUID.randomUUID().toString(),
                    name = displayName,
                    uriString = uri.toString(),
                    formatLabel = decoded.formatLabel,
                    sampleRate = decoded.sampleRate,
                    durationSec = decoded.left.size.toFloat() / decoded.sampleRate
                )
            )
            _statusBanner.value = "Imported $displayName (${decoded.formatLabel}) into Sampler."
        }
    }

    // Vocal Studio Actions
    fun selectVocalChain(chain: VocalChainType) {
        _vocalChain.value = chain
        _vocalParams.value = chain.defaultParams
    }

    fun updateVocalParams(params: VocalProcessingParams) {
        _vocalParams.value = params
    }

    fun updatePitchPointOffset(takeId: String, pointIdx: Int, semitonesOffset: Float) {
        _vocalTakes.value = _vocalTakes.value.map { take ->
            if (take.id == takeId) {
                val updatedCurve = take.pitchCurve.mapIndexed { i, pt ->
                    if (i == pointIdx) pt.copy(manualOffsetSemitones = semitonesOffset.coerceIn(-12f, 12f)) else pt
                }
                take.copy(pitchCurve = updatedCurve)
            } else take
        }
    }

    private fun processNewRecording(samples: FloatArray) {
        viewModelScope.launch(Dispatchers.Default) {
            val sr = audioEngine.sampleRate
            val takeId = "vocal_take_${_vocalTakes.value.size + 1}"
            audioEngine.registerAudioBuffer(takeId, samples, samples)
            val contour = PitchAndAnalysisDsp.analyzePitchContour(samples, sr, 0, _project.value.scale)
            val wave = ProjectArchiveManager.buildWaveformPreview(samples, 64)
            val take = VocalTake(
                id = takeId,
                name = "Take ${_vocalTakes.value.size + 1}",
                sampleRate = sr,
                durationSec = samples.size.toFloat() / sr,
                waveform = wave,
                pitchCurve = contour,
                activeComp = true
            )
            _vocalTakes.value = _vocalTakes.value + take
            val report = PitchAndAnalysisDsp.analyzeAudioBuffer(samples, sr)
            _audioReport.value = report

            val vocalTrack = _project.value.tracks.firstOrNull { it.type == TrackType.VOCAL }
            if (vocalTrack != null) {
                val clip = AudioClip(
                    id = takeId,
                    name = take.name,
                    startBeat = 0.0,
                    lengthBeats = (take.durationSec * _project.value.bpm / 60.0).coerceAtLeast(1.0),
                    waveformPreview = wave
                )
                updateTrack(vocalTrack.id, "Record Vocal Clip") { it.copy(clips = it.clips + clip) }
            }
            _statusBanner.value = "Recorded ${take.name} (${String.format("%.1f", take.durationSec)}s)"
        }
    }

    fun auditionVocalTake(take: VocalTake, isolateMode: Boolean? = null) {
        val buf = audioEngine.getAudioBuffer(take.id) ?: return
        val (l, r) = when (isolateMode) {
            true -> PitchAndAnalysisDsp.isolateStemPreview(buf.first, buf.second, take.sampleRate, true)
            false -> PitchAndAnalysisDsp.isolateStemPreview(buf.first, buf.second, take.sampleRate, false)
            null -> buf
        }
        audioEngine.triggerVoice(l, r)
    }

    // Mastering Actions
    fun updateMasteringConfig(config: MasteringConfig) {
        commitProjectEdit("Update Mastering") { it.copy(masteringConfig = config) }
    }

    fun applyMasteringPreset(preset: MasteringConfig) {
        commitProjectEdit("Mastering: ${preset.presetName}") { it.copy(masteringConfig = preset) }
    }

    // Arrangement Actions
    fun setTimelineZoom(zoom: Float) {
        _timelineZoom.value = zoom.coerceIn(0.5f, 3.0f)
    }

    fun addMarker(label: String, beatPos: Double) {
        commitProjectEdit("Add Marker") {
            it.copy(markers = it.markers + TimelineMarker("mk_${UUID.randomUUID().toString().take(6)}", label, beatPos))
        }
    }

    fun addSongSection(name: String, startBar: Int, lengthBars: Int) {
        commitProjectEdit("Add Section $name") {
            it.copy(sections = it.sections + SongSection("sec_${UUID.randomUUID().toString().take(6)}", name, startBar, lengthBars, 0xFF00E5FFL))
        }
    }

    fun updateLoopRange(startBeat: Double, endBeat: Double) {
        val safeStart = startBeat.coerceIn(0.0, 60.0)
        val safeEnd = endBeat.coerceIn(safeStart + 1.0, 64.0)
        commitProjectEdit("Loop ${safeStart.toInt()}–${safeEnd.toInt()}b") {
            it.copy(loopStartBeat = safeStart, loopEndBeat = safeEnd)
        }
    }

    private fun snapBeat(beat: Double): Double {
        val proj = _project.value
        if (!proj.snapEnabled) return beat.coerceIn(0.0, 60.0)
        val step = proj.gridResolution.stepBeats.coerceAtLeast(0.125)
        return (kotlin.math.round(beat / step) * step).coerceIn(0.0, 60.0)
    }

    fun moveAudioClip(trackId: String, clipId: String, newStartBeat: Double) {
        val snapped = snapBeat(newStartBeat)
        updateTrack(trackId, "Move Clip") { tr ->
            tr.copy(clips = tr.clips.map { if (it.id == clipId) it.copy(startBeat = snapped) else it })
        }
    }

    fun resizeAudioClip(trackId: String, clipId: String, newLengthBeats: Double) {
        val step = if (_project.value.snapEnabled) _project.value.gridResolution.stepBeats else 0.25
        val snappedLen = (kotlin.math.round(newLengthBeats / step) * step).coerceIn(0.5, 32.0)
        updateTrack(trackId, "Resize Clip") { tr ->
            tr.copy(clips = tr.clips.map { if (it.id == clipId) it.copy(lengthBeats = snappedLen) else it })
        }
    }

    fun splitAudioClip(trackId: String, clipId: String, splitBeat: Double) {
        updateTrack(trackId, "Split Clip") { tr ->
            val target = tr.clips.firstOrNull { it.id == clipId } ?: return@updateTrack tr
            val relativeSplit = (snapBeat(splitBeat) - target.startBeat).coerceIn(0.5, target.lengthBeats - 0.5)
            if (target.lengthBeats < 1.0) return@updateTrack tr
            val firstHalf = target.copy(
                lengthBeats = relativeSplit,
                name = "${target.name} (A)"
            )
            val secondHalf = target.copy(
                id = "clip_${UUID.randomUUID().toString().take(6)}",
                name = "${target.name} (B)",
                startBeat = target.startBeat + relativeSplit,
                lengthBeats = (target.lengthBeats - relativeSplit).coerceAtLeast(0.5)
            )
            tr.copy(clips = tr.clips.flatMap { if (it.id == clipId) listOf(firstHalf, secondHalf) else listOf(it) })
        }
    }

    fun toggleClipMuted(trackId: String, clipId: String) {
        updateTrack(trackId, "Mute Clip") { tr ->
            tr.copy(clips = tr.clips.map { if (it.id == clipId) it.copy(muted = !it.muted) else it })
        }
    }

    fun updateClipGain(trackId: String, clipId: String, gainDb: Float) {
        updateTrack(trackId, "Clip Gain") { tr ->
            tr.copy(clips = tr.clips.map { if (it.id == clipId) it.copy(gainDb = gainDb.coerceIn(-24f, 12f)) else it })
        }
    }

    fun paintAtBeat(trackId: String, beat: Double) {
        val tr = _project.value.tracks.firstOrNull { it.id == trackId } ?: return
        val snapped = snapBeat(beat)
        if (tr.type == TrackType.MIDI || tr.type == TrackType.INSTRUMENT) {
            toggleMidiNote(62, snapped, 1.0)
        } else {
            val newClip = AudioClip(
                id = "clip_${UUID.randomUUID().toString().take(6)}",
                name = "Region ${(tr.clips.size + 1)}",
                startBeat = snapped,
                lengthBeats = 4.0,
                waveformPreview = listOf(0.3f, 0.7f, 0.5f, 0.85f, 0.45f, 0.75f, 0.6f, 0.4f, 0.8f, 0.55f, 0.65f, 0.35f)
            )
            updateTrack(trackId, "Paint Region") { track -> track.copy(clips = track.clips + newClip) }
        }
    }

    fun addTempoPoint(beat: Double, bpm: Double) {
        val clampedBpm = bpm.coerceIn(40.0, 240.0)
        val safeBeat = snapBeat(beat)
        commitProjectEdit("Tempo ${clampedBpm.toInt()} BPM") { proj ->
            val filtered = proj.tempoTrack.filterNot { kotlin.math.abs(it.beatPosition - safeBeat) < 0.25 }
            proj.copy(
                bpm = if (safeBeat == 0.0) clampedBpm else proj.bpm,
                tempoTrack = (filtered + TempoPoint(safeBeat, clampedBpm)).sortedBy { it.beatPosition }
            )
        }
    }

    fun dropBrowserItemToTrack(
        category: BrowserCategory,
        itemName: String,
        targetTrackId: String?,
        beatPosition: Double
    ) {
        val track = _project.value.tracks.firstOrNull { it.id == targetTrackId }
            ?: _project.value.tracks.firstOrNull { it.id == _selectedTrackId.value }
            ?: _project.value.tracks.firstOrNull()
            ?: return
        val beat = snapBeat(beatPosition)

        when (category) {
            BrowserCategory.INSTRUMENTS -> {
                IndianInstrument.entries.firstOrNull { it.displayName == itemName }?.let {
                    audioEngine.triggerIndianInstrument(it, 0.92f)
                } ?: audioEngine.triggerMidiNotePreview(itemName, 60)
                updateTrack(track.id, "Assign $itemName") { it.copy(instrumentName = itemName) }
                _statusBanner.value = "Loaded $itemName on ${track.name}"
            }
            BrowserCategory.EFFECTS -> {
                val pType = PluginType.entries.firstOrNull { it.displayName == itemName } ?: return
                val inst = PluginCatalog.createInstance("plg_${UUID.randomUUID().toString().take(6)}", pType)
                updateTrack(track.id, "Drop ${pType.displayName}") { it.copy(plugins = it.plugins + inst) }
                _statusBanner.value = "Inserted ${pType.displayName} onto ${track.name}"
            }
            BrowserCategory.SAMPLES, BrowserCategory.LOOPS -> {
                val newClip = AudioClip(
                    id = "clip_${UUID.randomUUID().toString().take(6)}",
                    name = itemName,
                    startBeat = beat,
                    lengthBeats = if (category == BrowserCategory.LOOPS) 4.0 else 2.0,
                    waveformPreview = listOf(0.4f, 0.85f, 0.6f, 0.95f, 0.5f, 0.75f, 0.9f, 0.45f, 0.7f, 0.8f, 0.55f, 0.35f)
                )
                updateTrack(track.id, "Drop $itemName") { it.copy(clips = it.clips + newClip) }
                _statusBanner.value = "Dropped $itemName onto ${track.name} @ Beat ${beat.toInt()}"
            }
            BrowserCategory.DRUM_KITS -> {
                _presetPatterns.value.firstOrNull { it.name == itemName }?.let { pat ->
                    selectRhythmPattern(pat)
                    _statusBanner.value = "Loaded Drum Kit: ${pat.name}"
                }
            }
            BrowserCategory.PRESETS -> {
                VocalChainType.entries.firstOrNull { it.displayName == itemName }?.let {
                    selectVocalChain(it)
                    _statusBanner.value = "Applied Vocal Preset: ${it.displayName}"
                } ?: MasteringProcessor.MASTERING_PRESETS.firstOrNull { it.presetName == itemName }?.let {
                    applyMasteringPreset(it)
                    _statusBanner.value = "Applied Mastering Preset: ${it.presetName}"
                }
            }
            BrowserCategory.PROJECTS -> {
                createProjectFromTemplate(itemName)
            }
            BrowserCategory.FILES -> Unit
        }
    }

    fun duplicateAudioClip(trackId: String, clipId: String) {
        updateTrack(trackId, "Duplicate Clip") { tr ->
            val src = tr.clips.firstOrNull { it.id == clipId } ?: return@updateTrack tr
            val copy = src.copy(
                id = "clip_${UUID.randomUUID().toString().take(6)}",
                name = "${src.name} (Copy)",
                startBeat = (src.startBeat + src.lengthBeats).coerceAtMost(60.0)
            )
            tr.copy(clips = tr.clips + copy)
        }
    }

    fun deleteAudioClip(trackId: String, clipId: String) {
        updateTrack(trackId, "Delete Clip") { tr ->
            tr.copy(clips = tr.clips.filterNot { it.id == clipId })
        }
    }

    fun addAutomationPoint(trackId: String, beat: Double, normalizedValue: Float) {
        val clampedBeat = (kotlin.math.round(beat * 2.0) / 2.0).coerceIn(0.0, 32.0)
        val clampedVal = normalizedValue.coerceIn(0.05f, 1.0f)
        updateTrack(trackId, "Edit Automation") { tr ->
            val existing = tr.automationLanes.firstOrNull { it.targetParam == "volume" }
                ?: AutomationLane("auto_${UUID.randomUUID().toString().take(6)}", "volume", true, emptyList())
            val filteredPts = existing.points.filterNot { kotlin.math.abs(it.beatPosition - clampedBeat) < 0.25 }
            val nextPts = (filteredPts + AutomationPoint(clampedBeat, clampedVal)).sortedBy { it.beatPosition }
            val others = tr.automationLanes.filterNot { it.targetParam == "volume" }
            tr.copy(automationLanes = others + existing.copy(points = nextPts))
        }
    }

    fun updateMidiNoteVelocity(trackId: String, noteId: String, velocity: Int) {
        updateTrack(trackId, "Edit Note Velocity") { tr ->
            tr.copy(midiNotes = tr.midiNotes.map {
                if (it.id == noteId) it.copy(velocity = velocity.coerceIn(1, 127)) else it
            })
        }
    }

    fun toggleQuickChannelPlugin(trackId: String, type: PluginType) {
        updateTrack(trackId, "Toggle ${type.displayName}") { tr ->
            val existing = tr.plugins.firstOrNull { it.type == type }
            if (existing == null) {
                tr.copy(plugins = tr.plugins + PluginCatalog.createInstance("plg_${UUID.randomUUID().toString().take(6)}", type))
            } else {
                tr.copy(plugins = tr.plugins.map { if (it.id == existing.id) it.copy(bypassed = !it.bypassed) else it })
            }
        }
    }

    fun updateSendLevel(trackId: String, busId: String, busName: String, level: Float) {
        updateTrack(trackId, "Adjust $busName") { tr ->
            val cur = tr.sends.firstOrNull { it.targetBusId == busId }
                ?: SendRouting(busId, busName, 0f, false)
            val others = tr.sends.filterNot { it.targetBusId == busId }
            tr.copy(sends = others + cur.copy(level = level.coerceIn(0f, 1f)))
        }
    }

    // AI Music Assistant Actions
    fun askAiAssistant(prompt: String) {
        viewModelScope.launch {
            _aiState.value = UiState.Loading
            val takeBuf = _vocalTakes.value.lastOrNull()?.let { audioEngine.getAudioBuffer(it.id)?.first }
            val report = takeBuf?.let {
                withContext(Dispatchers.Default) { PitchAndAnalysisDsp.analyzeAudioBuffer(it, audioEngine.sampleRate) }
            }
            if (report != null) _audioReport.value = report

            val res = aiRepo.generateStudioSuggestion(prompt, _project.value, report)
            _aiState.value = res.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.localizedMessage ?: "AI Assistant error") }
            )
        }
    }

    fun applyAiChordsToProject(chords: List<ChordEvent>) {
        if (chords.isEmpty()) return
        commitProjectEdit("Apply AI Chords") { it.copy(chordTrack = chords) }
        _statusBanner.value = "Applied ${chords.size} editable chords to Chord Track."
    }

    fun applyAiMelodyToTrack(notes: List<MidiNote>) {
        if (notes.isEmpty()) return
        val targetTrack = _project.value.tracks.firstOrNull { it.id == _selectedTrackId.value && it.type == TrackType.MIDI }
            ?: _project.value.tracks.firstOrNull { it.type == TrackType.MIDI }
            ?: return
        updateTrack(targetTrack.id, "Insert AI Melody") { it.copy(midiNotes = notes) }
        _statusBanner.value = "Inserted ${notes.size} editable MIDI notes into ${targetTrack.name}."
    }

    fun applyAiRhythmSteps(steps: List<Int>) {
        if (steps.isEmpty()) return
        val p = _activePattern.value
        val firstLane = p.lanes.firstOrNull() ?: return
        val normalized = List(p.stepCount) { idx -> (steps.getOrElse(idx) { 0 } / 100f).coerceIn(0f, 1f) }
        val next = p.copy(lanes = p.lanes.mapIndexed { i, lane -> if (i == 0) lane.copy(steps = normalized) else lane }, isPreset = false)
        _activePattern.value = next
        audioEngine.activePattern = next
        _statusBanner.value = "Loaded editable AI groove onto ${firstLane.instrument.displayName}."
    }

    // Cloud & Local Project Management
    fun saveProjectLocallyAndCloud(versionLabel: String = "Manual Save") {
        viewModelScope.launch {
            val nextProj = _project.value.copy(versionNumber = _project.value.versionNumber + 1)
            _project.value = nextProj
            audioEngine.currentProject = nextProj
            val json = ProjectSerializer.projectToJson(nextProj)

            studioDao.upsertProject(
                LocalProjectEntity(
                    id = nextProj.id,
                    title = nextProj.title,
                    bpm = nextProj.bpm,
                    musicalKey = nextProj.musicalKey,
                    scaleName = nextProj.scale.displayName,
                    trackCount = nextProj.tracks.size,
                    versionNumber = nextProj.versionNumber,
                    payloadJson = json
                )
            )
            studioDao.insertVersion(
                LocalVersionEntity(
                    projectId = nextProj.id,
                    versionNumber = nextProj.versionNumber,
                    label = versionLabel,
                    payloadJson = json
                )
            )
            if (currentUserId.isBlank()) {
                _statusBanner.value = "Saved '${nextProj.title}' v${nextProj.versionNumber} locally."
            } else {
                val res = cloudRepo.backupProject(nextProj, versionLabel)
                _statusBanner.value = res.fold(
                    onSuccess = { "Backed up '${nextProj.title}' v${nextProj.versionNumber} locally & to Cloud." },
                    onFailure = { "Saved locally. Cloud backup error: ${it.localizedMessage}" }
                )
            }
        }
    }

    fun createProjectFromTemplate(templateName: String) {
        val created = ProjectSerializer.createProjectFromTemplate(templateName)
        _project.value = created
        audioEngine.currentProject = created
        _statusBanner.value = "Loaded template: $templateName"
    }

    fun loadProjectFromJson(json: String, label: String) {
        ProjectSerializer.projectFromJson(json)?.let { loaded ->
            _project.value = loaded
            audioEngine.currentProject = loaded
            _statusBanner.value = "Loaded: $label"
        }
    }

    fun restoreAutosave() {
        val rec = _autosaveRecovery.value ?: return
        loadProjectFromJson(rec.payloadJson, "Recovered ${rec.projectTitle}")
        _autosaveRecovery.value = null
    }

    fun dismissAutosave() {
        _autosaveRecovery.value = null
        viewModelScope.launch { studioDao.clearAutosave() }
    }

    fun shareCurrentProject() {
        if (currentUserId.isBlank()) {
            _statusBanner.value = "Use Export ZIP for offline sharing, or sign in with Google to generate a cloud share code."
            return
        }
        viewModelScope.launch {
            val res = cloudRepo.shareProject(_project.value)
            res.onSuccess { code ->
                _lastShareCode.value = code
                _statusBanner.value = "Project shared! Code: $code"
            }.onFailure { err ->
                _statusBanner.value = "Share failed: ${err.localizedMessage}"
            }
        }
    }

    fun importSharedProjectByCode(code: String) {
        if (currentUserId.isBlank()) {
            _statusBanner.value = "Sign in with Google on the Dashboard to fetch cloud share codes."
            return
        }
        viewModelScope.launch {
            val res = cloudRepo.fetchSharedProject(code)
            res.onSuccess { loaded ->
                _project.value = loaded
                audioEngine.currentProject = loaded
                _statusBanner.value = "Imported shared session '${loaded.title}'!"
            }.onFailure { err ->
                _statusBanner.value = "Import failed: ${err.localizedMessage}"
            }
        }
    }

    fun loadProjectVersions(projectId: String) {
        viewModelScope.launch {
            cloudRepo.observeProjectVersions(currentUserId, projectId).catch { }.collect {
                _selectedProjectVersions.value = it
            }
        }
    }

    fun exportProjectZip(outputStream: OutputStream) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ProjectArchiveManager.exportProjectZip(outputStream, _project.value, _activePattern.value, audioEngine)
                _statusBanner.value = "Exported ZIP archive with project JSON & 32f/16b mixdown WAV."
            } catch (e: Exception) {
                _statusBanner.value = "ZIP Export failed: ${e.localizedMessage}"
            }
        }
    }

    fun exportMixdownWav(outputStream: OutputStream) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (l, r) = audioEngine.bounceProjectToStereo(_project.value, _activePattern.value, bars = 4)
                WavAiffCodec.writeWavStream(outputStream, l, r, audioEngine.sampleRate, float32 = false)
                _statusBanner.value = "Bounced Master Stereo WAV (${audioEngine.sampleRate} Hz)."
            } catch (e: Exception) {
                _statusBanner.value = "WAV bounce failed: ${e.localizedMessage}"
            }
        }
    }

    fun relinkAudioClip(clipId: String, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = ProjectArchiveManager.relinkMissingClips(appContext, _project.value, clipId, uri, audioEngine)
            _project.value = updated
            audioEngine.currentProject = updated
            _statusBanner.value = "Relinked missing audio clip successfully."
        }
    }

    // Settings & Shortcuts
    fun setBufferSize(frames: Int) {
        viewModelScope.launch { prefsRepo.setBufferSize(frames) }
    }

    fun set64BitSumming(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.set64BitSumming(enabled) }
    }

    fun updateShortcut(action: String, keyCombo: String) {
        viewModelScope.launch { prefsRepo.updateShortcut(action, keyCombo) }
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    override fun onCleared() {
        super.onCleared()
        audioEngine.release()
    }
}
