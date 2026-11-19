package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.audio.AudioAnalysisReport
import com.example.data.*
import com.example.model.DawScreen
import com.example.ui.auth.onGoogleSignInClicked
import com.example.ui.auth.signOutStudio
import com.example.ui.components.AiAssistantDialog
import com.example.ui.components.ResizableStudioSplitPane
import com.example.ui.components.StudioSettingsDialog
import com.example.ui.components.StudioTransportBar
import com.example.ui.screens.*
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.viewmodel.StudioViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Must configure Firebase App Check before setContent or any Firebase SDK calls
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enterImmersiveLandscape()
        setContent {
            TrueworldStudioTheme {
                AppNavigation()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enterImmersiveLandscape()
        }
    }

    private fun enterImmersiveLandscape() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppNavigation() {
    val currentUser by remember { Firebase.auth.authStateFlow() }
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    val uidKey = currentUser?.uid ?: "local_studio"
    val displayName = currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Producer"

    key(uidKey) {
        val appContext = LocalContext.current.applicationContext
        val studioViewModel: StudioViewModel = viewModel(
            key = uidKey,
            factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return StudioViewModel(
                        appContext = appContext,
                        currentUserId = currentUser?.uid.orEmpty(),
                        cloudRepo = CloudProjectRepository(appContext),
                        aiRepo = AiMusicAssistantRepository(),
                        prefsRepo = StudioPreferencesRepository(appContext),
                        localDb = TrueworldDatabase.getInstance(appContext)
                    ) as T
                }
            }
        )
        StudioWorkspaceRoot(
            viewModel = studioViewModel,
            userName = displayName,
            isSignedIn = currentUser != null
        )
    }
}

@Composable
fun StudioWorkspaceRoot(
    viewModel: StudioViewModel,
    userName: String,
    isSignedIn: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val project by viewModel.project.collectAsStateWithLifecycle()
    val selectedTrackId by viewModel.selectedTrackId.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val metronomeOn by viewModel.metronomeOn.collectAsStateWithLifecycle()
    val timelineZoom by viewModel.timelineZoom.collectAsStateWithLifecycle()
    val activeTool by viewModel.activeTool.collectAsStateWithLifecycle()
    val activePattern by viewModel.activePattern.collectAsStateWithLifecycle()
    val presetPatterns by viewModel.presetPatterns.collectAsStateWithLifecycle()
    val samplerPads by viewModel.samplerPads.collectAsStateWithLifecycle()
    val selectedPadId by viewModel.selectedSamplerPadId.collectAsStateWithLifecycle()
    val vocalChain by viewModel.vocalChain.collectAsStateWithLifecycle()
    val vocalParams by viewModel.vocalParams.collectAsStateWithLifecycle()
    val vocalTakes by viewModel.vocalTakes.collectAsStateWithLifecycle()
    val undoHistory by viewModel.undoHistory.collectAsStateWithLifecycle()
    val autosave by viewModel.autosaveRecovery.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    val audioReport by viewModel.audioReport.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val lastShareCode by viewModel.lastShareCode.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val localProjects by viewModel.localProjects.collectAsStateWithLifecycle()
    val localPatterns by viewModel.localPatterns.collectAsStateWithLifecycle()
    val soundAssets by viewModel.soundAssets.collectAsStateWithLifecycle()
    val cloudProjectsState by viewModel.cloudProjects.collectAsStateWithLifecycle()
    val sharedPatternsState by viewModel.sharedPatterns.collectAsStateWithLifecycle()
    val selectedVersions by viewModel.selectedProjectVersions.collectAsStateWithLifecycle()

    val playheadBeat by viewModel.audioEngine.playheadBeat.collectAsStateWithLifecycle()
    val trackMeters by viewModel.audioEngine.trackMeters.collectAsStateWithLifecycle()
    val masterMeters by viewModel.audioEngine.masterMeters.collectAsStateWithLifecycle()
    val hardwareStatus by viewModel.audioEngine.hardwareStatus.collectAsStateWithLifecycle()

    var currentScreen by remember { mutableStateOf(DawScreen.ARRANGEMENT) }
    var showLeftPanel by remember { mutableStateOf(false) }
    var showBottomPanel by remember { mutableStateOf(true) }
    var showRightPanel by remember { mutableStateOf(false) }
    var panelDefaultsApplied by remember { mutableStateOf(false) }
    var showAiDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showUndoDialog by remember { mutableStateOf(false) }
    var pendingRelinkClipId by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusBanner) {
        statusBanner?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    BackHandler(enabled = currentScreen != DawScreen.ARRANGEMENT) {
        currentScreen = DawScreen.ARRANGEMENT
    }

    // Runtime Microphone Permission for Vocal / Audio Recording
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.toggleRecord()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Microphone permission denied. Grant audio access in Settings to record live vocals.")
            }
        }
    }

    fun requestRecordWithPermission() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.toggleRecord()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // SAF OpenDocument for WAV/AIFF import & clip relinking
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val relinkId = pendingRelinkClipId
            pendingRelinkClipId = null
            if (relinkId != null) {
                viewModel.relinkAudioClip(relinkId, uri)
            } else {
                val name = resolveDisplayName(context, uri) ?: "Imported Audio.wav"
                viewModel.importAudioToSampler(uri, name)
            }
        } else {
            pendingRelinkClipId = null
        }
    }

    // SAF CreateDocument for ZIP Export
    val zipExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { os ->
                viewModel.exportProjectZip(os)
            }
        }
    }

    // SAF CreateDocument for WAV Mixdown Bounce
    val wavExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/wav")
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { os ->
                viewModel.exportMixdownWav(os)
            }
        }
    }

    Scaffold(
        containerColor = ObsidianBg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.displayCutout
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(ObsidianBg)
        ) {
            val isTabletWide = maxWidth >= 900.dp && maxHeight >= 500.dp
            LaunchedEffect(isTabletWide) {
                if (!panelDefaultsApplied) {
                    showLeftPanel = isTabletWide
                    showBottomPanel = true
                    showRightPanel = isTabletWide
                    panelDefaultsApplied = true
                }
            }

            Row(modifier = Modifier.fillMaxSize()) {
                // Compact Desktop Vertical Studio Dock Rail (All Landscape Widths: 16:9 & 16:10)
                DesktopLandscapeNavRail(
                    currentScreen = currentScreen,
                    onSelectScreen = { currentScreen = it }
                )

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    // Studio One Inspired Main Toolbar & Transport Bar
                    StudioMainToolbar(
                        project = project,
                        activeTool = activeTool,
                        canUndo = undoHistory.isNotEmpty(),
                        canRedo = true,
                        showPanelToggles = currentScreen == DawScreen.ARRANGEMENT,
                        showLeftInspector = showLeftPanel,
                        showBottomEditor = showBottomPanel,
                        showBottomMixer = false,
                        showRightBrowser = showRightPanel,
                        onSelectTool = { viewModel.setActiveTool(it) },
                        onToggleSnap = { viewModel.toggleSnap() },
                        onSelectGrid = { viewModel.setGridResolution(it) },
                        onSelectTimeSignature = { viewModel.updateTimeSignature(it) },
                        onBpmDelta = { delta -> viewModel.updateBpm(project.bpm + delta) },
                        onUndo = { viewModel.undo() },
                        onRedo = { viewModel.redo() },
                        onSaveProject = { viewModel.saveProjectLocallyAndCloud("Manual Save") },
                        onOpenUndoHistory = { showUndoDialog = true },
                        onOpenAiAssistant = { showAiDialog = true },
                        onOpenSettings = {
                            viewModel.audioEngine.refreshHardwareCapabilities()
                            showSettingsDialog = true
                        },
                        onToggleLeftInspector = { showLeftPanel = !showLeftPanel },
                        onToggleBottomEditor = { showBottomPanel = !showBottomPanel },
                        onToggleBottomMixer = {},
                        onToggleRightBrowser = { showRightPanel = !showRightPanel }
                    )
                    StudioTransportBar(
                        project = project,
                        playheadBeat = playheadBeat,
                        isPlaying = isPlaying,
                        isRecording = isRecording,
                        metronomeOn = metronomeOn,
                        hardwareStatus = hardwareStatus,
                        onRewind = { viewModel.rewindTransport() },
                        onForward = { viewModel.forwardTransport() },
                        onPlay = { viewModel.togglePlay() },
                        onPause = { viewModel.pauseTransport() },
                        onStop = { viewModel.stopTransport() },
                        onRecord = { requestRecordWithPermission() },
                        onToggleLoop = { viewModel.toggleLoop() },
                        onToggleMetronome = { viewModel.toggleMetronome() },
                        onOpenSettings = {
                            viewModel.audioEngine.refreshHardwareCapabilities()
                            showSettingsDialog = true
                        }
                    )

                    // Main Desktop Workspace Area
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        val renderPrimary: @Composable () -> Unit = {
                            RenderDawScreen(
                                screen = currentScreen,
                                userName = userName,
                                isSignedIn = isSignedIn,
                                viewModel = viewModel,
                                project = project,
                                selectedTrackId = selectedTrackId,
                                playheadBeat = playheadBeat,
                                timelineZoom = timelineZoom,
                                showLeftPanel = showLeftPanel,
                                showBottomPanel = showBottomPanel,
                                showRightPanel = showRightPanel,
                                activePattern = activePattern,
                                presetPatterns = presetPatterns,
                                samplerPads = samplerPads,
                                selectedPadId = selectedPadId,
                                vocalChain = vocalChain,
                                vocalParams = vocalParams,
                                vocalTakes = vocalTakes,
                                isRecording = isRecording,
                                audioReport = audioReport,
                                autosave = autosave,
                                localProjects = localProjects,
                                localPatterns = localPatterns,
                                soundAssets = soundAssets,
                                cloudProjectsState = cloudProjectsState,
                                sharedPatternsState = sharedPatternsState,
                                selectedVersions = selectedVersions,
                                lastShareCode = lastShareCode,
                                trackMeters = trackMeters,
                                masterMeters = masterMeters,
                                onNavigate = { currentScreen = it },
                                onToggleRecord = { requestRecordWithPermission() },
                                onImportAudio = {
                                    pendingRelinkClipId = null
                                    audioPickerLauncher.launch(arrayOf("audio/*"))
                                },
                                onRelinkClip = { clipId ->
                                    pendingRelinkClipId = clipId
                                    audioPickerLauncher.launch(arrayOf("audio/*"))
                                },
                                onExportZip = {
                                    zipExportLauncher.launch("${project.title.replace(" ", "_")}_Trueworld.zip")
                                },
                                onExportWav = {
                                    wavExportLauncher.launch("${project.title.replace(" ", "_")}_Master.wav")
                                },
                                onSignInOptional = {
                                    onGoogleSignInClicked(
                                        context = context,
                                        credentialManager = credentialManager,
                                        onAuthSuccess = {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Google Cloud Sync connected!")
                                            }
                                        },
                                        onAuthError = { msg ->
                                            scope.launch { snackbarHostState.showSnackbar(msg) }
                                        },
                                        scope = scope
                                    )
                                },
                                onSignOut = {
                                    signOutStudio(credentialManager, scope)
                                },
                                onToggleLeftInspector = { showLeftPanel = !showLeftPanel },
                                onToggleRightBrowser = { showRightPanel = !showRightPanel },
                                onToggleBottomEditor = { showBottomPanel = !showBottomPanel }
                            )
                        }

                        // Arrangement already hosts its own 5-zone desktop dockable workstation
                        if (isTabletWide && currentScreen != DawScreen.ARRANGEMENT && currentScreen != DawScreen.MIXER && currentScreen != DawScreen.HOME) {
                            ResizableStudioSplitPane(
                                firstPane = renderPrimary,
                                secondPane = {
                                    MixerConsoleScreen(
                                        project = project,
                                        selectedTrackId = selectedTrackId,
                                        trackMeters = trackMeters,
                                        masterMeters = masterMeters,
                                        onSelectTrack = { viewModel.selectTrack(it) },
                                        onAddTrack = { viewModel.addTrack(it) },
                                        onUpdateTrack = { id, lbl, tr -> viewModel.updateTrack(id, lbl, tr) },
                                        onFreezeTrack = { viewModel.freezeSelectedTrack(it) },
                                        onOpenPluginRack = { trId ->
                                            viewModel.selectTrack(trId)
                                            currentScreen = DawScreen.PLUGIN_BROWSER
                                        },
                                        onToggleQuickPlugin = { trId, pType -> viewModel.toggleQuickChannelPlugin(trId, pType) },
                                        onUpdateSend = { trId, busId, busName, lvl -> viewModel.updateSendLevel(trId, busId, busName, lvl) }
                                    )
                                }
                            )
                        } else {
                            renderPrimary()
                        }
                    }
                }
            }
        }
    }

    if (showAiDialog) {
        AiAssistantDialog(
            aiState = aiState,
            onDismiss = { showAiDialog = false },
            onAsk = { prompt -> viewModel.askAiAssistant(prompt) },
            onApplyChords = { chords -> viewModel.applyAiChordsToProject(chords) },
            onApplyMelody = { notes -> viewModel.applyAiMelodyToTrack(notes) },
            onApplyRhythm = { steps -> viewModel.applyAiRhythmSteps(steps) }
        )
    }

    if (showSettingsDialog) {
        StudioSettingsDialog(
            hardwareStatus = hardwareStatus,
            settings = settings,
            onSelectBuffer = { viewModel.setBufferSize(it) },
            onToggle64Bit = { viewModel.set64BitSumming(it) },
            onRefreshHardware = { viewModel.audioEngine.refreshHardwareCapabilities() },
            onUpdateShortcut = { action, combo -> viewModel.updateShortcut(action, combo) },
            onDismiss = { showSettingsDialog = false }
        )
    }

    if (showUndoDialog) {
        AlertDialog(
            onDismissRequest = { showUndoDialog = false },
            containerColor = PanelSurface,
            title = { Text("Undo / Redo Edit History", color = TextPrimary) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (undoHistory.isEmpty()) {
                        Text("No edits recorded yet in this session.", color = TextSecondary)
                    } else {
                        undoHistory.forEach { item ->
                            Surface(
                                color = ElevatedRack,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "• ${item.description}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.undo() }, enabled = undoHistory.isNotEmpty()) {
                        Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Undo")
                    }
                    OutlinedButton(onClick = { viewModel.redo() }) {
                        Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Redo")
                    }
                    Button(onClick = { showUndoDialog = false }) { Text("Close") }
                }
            }
        )
    }
}

@Composable
private fun RenderDawScreen(
    screen: DawScreen,
    userName: String,
    isSignedIn: Boolean,
    viewModel: StudioViewModel,
    project: com.example.model.DawProject,
    selectedTrackId: String,
    playheadBeat: Double,
    timelineZoom: Float,
    showLeftPanel: Boolean,
    showBottomPanel: Boolean,
    showRightPanel: Boolean,
    activePattern: com.example.model.RhythmPattern,
    presetPatterns: List<com.example.model.RhythmPattern>,
    samplerPads: List<com.example.model.SamplerPad>,
    selectedPadId: String,
    vocalChain: com.example.model.VocalChainType,
    vocalParams: com.example.model.VocalProcessingParams,
    vocalTakes: List<com.example.model.VocalTake>,
    isRecording: Boolean,
    audioReport: AudioAnalysisReport?,
    autosave: AutosaveRecoveryEntity?,
    localProjects: List<LocalProjectEntity>,
    localPatterns: List<CustomPatternEntity>,
    soundAssets: List<SoundAssetEntity>,
    cloudProjectsState: com.example.viewmodel.UiState<List<com.example.model.CloudProject>>,
    sharedPatternsState: com.example.viewmodel.UiState<List<com.example.model.CloudSharedPattern>>,
    selectedVersions: List<com.example.model.CloudProjectVersion>,
    lastShareCode: String?,
    trackMeters: Map<String, Float>,
    masterMeters: com.example.model.MeterSnapshot,
    onNavigate: (DawScreen) -> Unit,
    onToggleRecord: () -> Unit,
    onImportAudio: () -> Unit,
    onRelinkClip: (String) -> Unit,
    onExportZip: () -> Unit,
    onExportWav: () -> Unit,
    onSignInOptional: () -> Unit,
    onSignOut: () -> Unit,
    onToggleLeftInspector: () -> Unit,
    onToggleRightBrowser: () -> Unit,
    onToggleBottomEditor: () -> Unit
) {
    when (screen) {
        DawScreen.HOME -> HomeDashboardScreen(
            project = project,
            userName = userName,
            isSignedIn = isSignedIn,
            autosave = autosave,
            localProjects = localProjects,
            cloudProjectsState = cloudProjectsState,
            selectedVersions = selectedVersions,
            lastShareCode = lastShareCode,
            onRestoreAutosave = { viewModel.restoreAutosave() },
            onDismissAutosave = { viewModel.dismissAutosave() },
            onCreateFromTemplate = { viewModel.createProjectFromTemplate(it) },
            onSaveBackup = { viewModel.saveProjectLocallyAndCloud(it) },
            onLoadJsonProject = { json, lbl -> viewModel.loadProjectFromJson(json, lbl) },
            onLoadVersions = { viewModel.loadProjectVersions(it) },
            onShareProject = { viewModel.shareCurrentProject() },
            onImportShareCode = { viewModel.importSharedProjectByCode(it) },
            onExportZip = onExportZip,
            onExportWav = onExportWav,
            onNavigateScreen = onNavigate,
            onSignInOptional = onSignInOptional,
            onSignOut = onSignOut
        )
        DawScreen.ARRANGEMENT -> ArrangementTimelineScreen(
            project = project,
            selectedTrackId = selectedTrackId,
            playheadBeat = playheadBeat,
            timelineZoom = timelineZoom,
            trackMeters = trackMeters,
            masterMeters = masterMeters,
            showLeftPanel = showLeftPanel,
            showBottomPanel = showBottomPanel,
            showRightPanel = showRightPanel,
            presetPatterns = presetPatterns,
            soundAssets = soundAssets,
            localProjects = localProjects,
            activePattern = activePattern,
            onSelectTrack = { viewModel.selectTrack(it) },
            onAddTrack = { viewModel.addTrack(it) },
            onUpdateTrack = { id, lbl, tr -> viewModel.updateTrack(id, lbl, tr) },
            onFreezeTrack = { viewModel.freezeSelectedTrack(it) },
            onSetZoom = { viewModel.setTimelineZoom(it) },
            onAddMarker = { lbl, beat -> viewModel.addMarker(lbl, beat) },
            onAddSection = { name, bar, len -> viewModel.addSongSection(name, bar, len) },
            onUpdateLoopRange = { startB, endB -> viewModel.updateLoopRange(startB, endB) },
            onMoveClip = { trId, clipId, startB -> viewModel.moveAudioClip(trId, clipId, startB) },
            onDuplicateClip = { trId, clipId -> viewModel.duplicateAudioClip(trId, clipId) },
            onDeleteClip = { trId, clipId -> viewModel.deleteAudioClip(trId, clipId) },
            onAddAutomationPoint = { trId, beat, norm -> viewModel.addAutomationPoint(trId, beat, norm) },
            onSeekBeat = { viewModel.audioEngine.seekToBeat(it) },
            onOpenPianoRoll = { trId ->
                viewModel.selectTrack(trId)
                onNavigate(DawScreen.PIANO_ROLL)
            },
            onOpenVocalEditor = { onNavigate(DawScreen.VOCAL_EDITOR) },
            onOpenPluginRack = { trId ->
                viewModel.selectTrack(trId)
                onNavigate(DawScreen.PLUGIN_BROWSER)
            },
            onRelinkClip = onRelinkClip,
            onToggleQuickPlugin = { trId, pType -> viewModel.toggleQuickChannelPlugin(trId, pType) },
            onUpdateSend = { trId, busId, busName, lvl -> viewModel.updateSendLevel(trId, busId, busName, lvl) },
            onUpdateClipGain = { trId, clipId, gain -> viewModel.updateClipGain(trId, clipId, gain) },
            onSplitClip = { trId, clipId, beat -> viewModel.splitAudioClip(trId, clipId, beat) },
            onToggleClipMuted = { trId, clipId -> viewModel.toggleClipMuted(trId, clipId) },
            onDropBrowserItem = { cat, name, trId, beat -> viewModel.dropBrowserItemToTrack(cat, name, trId, beat) },
            onToggleLeftInspector = onToggleLeftInspector,
            onToggleRightBrowser = onToggleRightBrowser,
            onToggleBottomEditor = onToggleBottomEditor,
            onPreviewIndianInstrument = { inst -> viewModel.audioEngine.triggerIndianInstrument(inst, 0.92f) },
            onPreviewMidiPitch = { inst, pitch -> viewModel.audioEngine.triggerMidiNotePreview(inst, pitch) },
            onSelectKeyAndScale = { k, s -> viewModel.updateKeyAndScale(k, s) },
            onToggleMidiNote = { pitch, beat -> viewModel.toggleMidiNote(pitch, beat) },
            onUpdateMidiNoteVelocity = { trId, nId, vel -> viewModel.updateMidiNoteVelocity(trId, nId, vel) },
            onQuantizeMidi = { viewModel.quantizeSelectedTrackMidi() },
            onHumanizeMidi = { viewModel.humanizeSelectedTrackMidi() },
            onRandomizeMidiVel = { viewModel.randomizeSelectedTrackVelocities() },
            onArpeggiateMidi = { viewModel.arpeggiateSelectedTrackMidi() },
            onToggleDrumStep = { inst, step -> viewModel.togglePatternStep(inst, step) },
            onTriggerDrumInstrument = { viewModel.audioEngine.triggerIndianInstrument(it, 0.9f) }
        )
        DawScreen.PIANO_ROLL -> PianoRollScreen(
            project = project,
            selectedTrackId = selectedTrackId,
            playheadBeat = playheadBeat,
            onSelectKeyAndScale = { k, s -> viewModel.updateKeyAndScale(k, s) },
            onSelectInstrument = { trId, inst ->
                viewModel.updateTrack(trId, "Instrument $inst") { it.copy(instrumentName = inst) }
            },
            onToggleNote = { pitch, beat -> viewModel.toggleMidiNote(pitch, beat) },
            onUpdateNoteVelocity = { trId, nId, vel -> viewModel.updateMidiNoteVelocity(trId, nId, vel) },
            onQuantize = { viewModel.quantizeSelectedTrackMidi() },
            onHumanize = { viewModel.humanizeSelectedTrackMidi() },
            onRandomizeVelocity = { viewModel.randomizeSelectedTrackVelocities() },
            onArpeggiate = { viewModel.arpeggiateSelectedTrackMidi() },
            onPreviewPitch = { pitch ->
                val inst = project.tracks.firstOrNull { it.id == selectedTrackId }?.instrumentName ?: "Grand Piano"
                viewModel.audioEngine.triggerMidiNotePreview(inst, pitch)
            }
        )
        DawScreen.DRUM_MACHINE -> DrumMachineScreen(
            activePattern = activePattern,
            presetPatterns = presetPatterns,
            onSelectPattern = { viewModel.selectRhythmPattern(it) },
            onToggleStep = { inst, step -> viewModel.togglePatternStep(inst, step) },
            onTriggerInstrument = { inst -> viewModel.audioEngine.triggerIndianInstrument(inst, 0.92f) },
            onAddInstrumentLane = { inst -> viewModel.addIndianInstrumentLane(inst) },
            onChangeTimeSignature = { ts -> viewModel.updateTimeSignature(ts) },
            onSaveAndSharePattern = { name, pub -> viewModel.saveAndShareCurrentPattern(name, pub) }
        )
        DawScreen.SAMPLER -> SamplerScreen(
            pads = samplerPads,
            selectedPadId = selectedPadId,
            onSelectPad = { viewModel.selectSamplerPad(it) },
            onTriggerPad = { pad, note -> viewModel.triggerSamplerPad(pad, note) },
            onUpdatePad = { viewModel.updateSamplerPad(it) },
            onImportWavOrAiff = onImportAudio
        )
        DawScreen.MIXER -> MixerConsoleScreen(
            project = project,
            selectedTrackId = selectedTrackId,
            trackMeters = trackMeters,
            masterMeters = masterMeters,
            onSelectTrack = { viewModel.selectTrack(it) },
            onAddTrack = { viewModel.addTrack(it) },
            onUpdateTrack = { id, lbl, tr -> viewModel.updateTrack(id, lbl, tr) },
            onFreezeTrack = { viewModel.freezeSelectedTrack(it) },
            onOpenPluginRack = { trId ->
                viewModel.selectTrack(trId)
                onNavigate(DawScreen.PLUGIN_BROWSER)
            },
            onToggleQuickPlugin = { trId, pType -> viewModel.toggleQuickChannelPlugin(trId, pType) },
            onUpdateSend = { trId, busId, busName, lvl -> viewModel.updateSendLevel(trId, busId, busName, lvl) }
        )
        DawScreen.VOCAL_EDITOR -> VocalEditorScreen(
            project = project,
            vocalChain = vocalChain,
            vocalParams = vocalParams,
            vocalTakes = vocalTakes,
            isRecording = isRecording,
            audioReport = audioReport,
            onToggleRecord = onToggleRecord,
            onSelectChain = { viewModel.selectVocalChain(it) },
            onUpdateParams = { viewModel.updateVocalParams(it) },
            onUpdatePitchOffset = { id, idx, off -> viewModel.updatePitchPointOffset(id, idx, off) },
            onAuditionTake = { take, iso -> viewModel.auditionVocalTake(take, iso) }
        )
        DawScreen.PLUGIN_BROWSER -> PluginBrowserScreen(
            project = project,
            selectedTrackId = selectedTrackId,
            onSelectTrack = { viewModel.selectTrack(it) },
            onInsertPlugin = { viewModel.addPluginToSelectedTrack(it) },
            onUpdatePlugin = { trId, inst -> viewModel.updatePluginInstance(trId, inst) },
            onRemovePlugin = { trId, pId -> viewModel.removePluginFromTrack(trId, pId) }
        )
        DawScreen.MASTERING -> MasteringStudioScreen(
            config = project.masteringConfig,
            masterMeters = masterMeters,
            onSelectPreset = { viewModel.applyMasteringPreset(it) },
            onUpdateConfig = { viewModel.updateMasteringConfig(it) },
            onImportReference = onImportAudio,
            onBounceWav = onExportWav
        )
        DawScreen.SOUND_LIBRARY -> SoundLibraryScreen(
            project = project,
            soundAssets = soundAssets,
            localPatterns = localPatterns,
            sharedPatternsState = sharedPatternsState,
            onPreviewInstrument = { inst -> viewModel.audioEngine.triggerIndianInstrument(inst, 0.92f) },
            onLoadLocalPattern = { lp ->
                ProjectSerializer.patternFromJson(lp.patternJson)?.let {
                    viewModel.selectRhythmPattern(it)
                    onNavigate(DawScreen.DRUM_MACHINE)
                }
            },
            onLoadCloudPattern = { sp ->
                ProjectSerializer.patternFromJson(sp.stepsJson)?.let {
                    viewModel.selectRhythmPattern(it)
                    onNavigate(DawScreen.DRUM_MACHINE)
                }
            },
            onImportAudioFile = onImportAudio,
            onRelinkClip = onRelinkClip
        )
    }
}

@Composable
private fun DesktopLandscapeNavRail(
    currentScreen: DawScreen,
    onSelectScreen: (DawScreen) -> Unit
) {
    Surface(
        color = ConsoleSurface,
        modifier = Modifier
            .width(58.dp)
            .fillMaxHeight()
            .border(1.dp, ConsoleBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 4.dp, horizontal = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            DawScreen.entries.forEach { screen ->
                val selected = currentScreen == screen
                Surface(
                    color = if (selected) CyanMuted else PanelSurface,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .border(1.dp, if (selected) CyanSignal else ConsoleBorder, RoundedCornerShape(8.dp))
                        .clickable { onSelectScreen(screen) }
                        .testTag("nav_rail_${screen.route}")
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            iconForScreen(screen),
                            contentDescription = screen.label,
                            tint = if (selected) CyanSignal else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = screen.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) TextPrimary else TextSecondary,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private fun iconForScreen(screen: DawScreen): ImageVector = when (screen) {
    DawScreen.HOME -> Icons.Rounded.Home
    DawScreen.ARRANGEMENT -> Icons.Rounded.ViewTimeline
    DawScreen.PIANO_ROLL -> Icons.Rounded.Piano
    DawScreen.DRUM_MACHINE -> Icons.Rounded.MusicNote
    DawScreen.SAMPLER -> Icons.Rounded.GridView
    DawScreen.MIXER -> Icons.Rounded.Equalizer
    DawScreen.VOCAL_EDITOR -> Icons.Rounded.Mic
    DawScreen.PLUGIN_BROWSER -> Icons.Rounded.Tune
    DawScreen.MASTERING -> Icons.Rounded.GraphicEq
    DawScreen.SOUND_LIBRARY -> Icons.Rounded.LibraryMusic
}

private fun resolveDisplayName(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    } catch (_: Exception) {
        uri.lastPathSegment
    }
}
