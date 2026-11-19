package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.*
import com.example.data.LocalProjectEntity
import com.example.data.SoundAssetEntity
import com.example.ui.components.ResizableStudioSplitPane
import com.example.ui.components.VerticalResizableSplitPane
import com.example.ui.components.WaveformCanvas
import com.example.ui.theme.*
import java.util.UUID

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArrangementTimelineScreen(
    project: DawProject,
    selectedTrackId: String,
    playheadBeat: Double,
    timelineZoom: Float,
    trackMeters: Map<String, Float> = emptyMap(),
    masterMeters: MeterSnapshot = MeterSnapshot(),
    showLeftPanel: Boolean = true,
    showBottomPanel: Boolean = true,
    showRightPanel: Boolean = true,
    presetPatterns: List<RhythmPattern> = emptyList(),
    soundAssets: List<SoundAssetEntity> = emptyList(),
    localProjects: List<LocalProjectEntity> = emptyList(),
    activePattern: RhythmPattern = RhythmPattern("default", "Default", RhythmStyle.SAMBALPURI_FOLK, TimeSignature(4, 4), 120.0, 16, 0.0f, emptyList(), false),
    onSelectTrack: (String) -> Unit,
    onAddTrack: (TrackType) -> Unit,
    onUpdateTrack: (String, String, (DawTrack) -> DawTrack) -> Unit,
    onFreezeTrack: (String) -> Unit,
    onSetZoom: (Float) -> Unit,
    onAddMarker: (String, Double) -> Unit,
    onAddSection: (String, Int, Int) -> Unit,
    onUpdateLoopRange: (Double, Double) -> Unit = { _, _ -> },
    onMoveClip: (String, String, Double) -> Unit = { _, _, _ -> },
    onDuplicateClip: (String, String) -> Unit = { _, _ -> },
    onDeleteClip: (String, String) -> Unit = { _, _ -> },
    onAddAutomationPoint: (String, Double, Float) -> Unit = { _, _, _ -> },
    onSeekBeat: (Double) -> Unit,
    onOpenPianoRoll: (String) -> Unit,
    onOpenVocalEditor: () -> Unit = {},
    onOpenPluginRack: (String) -> Unit = {},
    onRelinkClip: (String) -> Unit,
    onToggleQuickPlugin: (String, PluginType) -> Unit = { _, _ -> },
    onUpdateSend: (String, String, String, Float) -> Unit = { _, _, _, _ -> },
    onUpdateClipGain: (String, String, Float) -> Unit = { _, _, _ -> },
    onSplitClip: (String, String, Double) -> Unit = { _, _, _ -> },
    onToggleClipMuted: (String, String) -> Unit = { _, _ -> },
    onDropBrowserItem: (BrowserCategory, String, String?, Double) -> Unit = { _, _, _, _ -> },
    onToggleLeftInspector: () -> Unit = {},
    onToggleRightBrowser: () -> Unit = {},
    onToggleBottomEditor: () -> Unit = {},
    onPreviewIndianInstrument: (IndianInstrument) -> Unit = {},
    onPreviewMidiPitch: (String, Int) -> Unit = { _, _ -> },
    onSelectKeyAndScale: (String, MusicalScale) -> Unit = { _, _ -> },
    onToggleMidiNote: (Int, Double) -> Unit = { _, _ -> },
    onUpdateMidiNoteVelocity: (String, String, Int) -> Unit = { _, _, _ -> },
    onQuantizeMidi: () -> Unit = {},
    onHumanizeMidi: () -> Unit = {},
    onRandomizeMidiVel: () -> Unit = {},
    onArpeggiateMidi: () -> Unit = {},
    onToggleDrumStep: (IndianInstrument, Int) -> Unit = { _, _ -> },
    onTriggerDrumInstrument: (IndianInstrument) -> Unit = {},
    onPreviewChord: (ChordEvent) -> Unit = {},
    onAddChordEvent: (String, List<Int>, Double) -> Unit = { _, _, _ -> }
) {
    val selectedTrack = project.tracks.firstOrNull { it.id == selectedTrackId } ?: project.tracks.firstOrNull()
    var bottomEditorTab by remember { mutableStateOf(BottomEditorTab.PIANO_ROLL) }

    val timelineContent: @Composable () -> Unit = {
        MultitrackTimelineCenterPane(
            project = project,
            selectedTrackId = selectedTrackId,
            playheadBeat = playheadBeat,
            timelineZoom = timelineZoom,
            onSelectTrack = onSelectTrack,
            onAddTrack = onAddTrack,
            onUpdateTrack = onUpdateTrack,
            onFreezeTrack = onFreezeTrack,
            onSetZoom = onSetZoom,
            onAddMarker = onAddMarker,
            onAddSection = onAddSection,
            onUpdateLoopRange = onUpdateLoopRange,
            onMoveClip = onMoveClip,
            onDuplicateClip = onDuplicateClip,
            onDeleteClip = onDeleteClip,
            onAddAutomationPoint = onAddAutomationPoint,
            onSeekBeat = onSeekBeat,
            onOpenPianoRoll = onOpenPianoRoll,
            onOpenVocalEditor = onOpenVocalEditor,
            onRelinkClip = onRelinkClip
        )
    }

    val centerAndBottomContent: @Composable () -> Unit = {
        if (showBottomPanel) {
            VerticalResizableSplitPane(
                topPane = timelineContent,
                bottomPane = {
                    BottomEditorPanel(
                        project = project,
                        selectedTrack = selectedTrack,
                        selectedClipId = selectedTrack?.clips?.firstOrNull()?.id,
                        activeTab = bottomEditorTab,
                        playheadBeat = playheadBeat,
                        activePattern = activePattern,
                        trackMeters = trackMeters,
                        masterMeters = masterMeters,
                        onSelectTab = { bottomEditorTab = it },
                        onClosePanel = onToggleBottomEditor,
                        onSelectTrack = onSelectTrack,
                        onAddTrack = onAddTrack,
                        onUpdateTrack = onUpdateTrack,
                        onFreezeTrack = onFreezeTrack,
                        onOpenPluginRack = onOpenPluginRack,
                        onToggleQuickPlugin = onToggleQuickPlugin,
                        onUpdateSend = onUpdateSend,
                        onUpdateClipGain = onUpdateClipGain,
                        onSplitClip = onSplitClip,
                        onToggleClipMuted = onToggleClipMuted,
                        onRelinkClip = onRelinkClip,
                        onAddAutomationPoint = onAddAutomationPoint,
                        onSelectKeyAndScale = onSelectKeyAndScale,
                        onToggleMidiNote = onToggleMidiNote,
                        onUpdateMidiNoteVelocity = onUpdateMidiNoteVelocity,
                        onQuantizeMidi = onQuantizeMidi,
                        onHumanizeMidi = onHumanizeMidi,
                        onRandomizeMidiVel = onRandomizeMidiVel,
                        onArpeggiateMidi = onArpeggiateMidi,
                        onPreviewMidiPitch = onPreviewMidiPitch,
                        onToggleDrumStep = onToggleDrumStep,
                        onTriggerDrumInstrument = onTriggerDrumInstrument,
                        onPreviewChord = onPreviewChord,
                        onAddChordEvent = onAddChordEvent
                    )
                },
                initialTopFraction = 0.58f
            )
        } else {
            timelineContent()
        }
    }

    val leftPlusCenter: @Composable () -> Unit = {
        if (showLeftPanel) {
            ResizableStudioSplitPane(
                firstPane = {
                    LeftTrackInspectorPanel(
                        project = project,
                        selectedTrack = selectedTrack,
                        onUpdateTrack = onUpdateTrack,
                        onToggleQuickPlugin = { trId, pType -> onToggleQuickPlugin(trId, pType) },
                        onOpenPluginBrowser = { onOpenPluginRack(selectedTrackId) },
                        onCollapse = onToggleLeftInspector
                    )
                },
                secondPane = centerAndBottomContent,
                initialRatio = 0.22f,
                minWidthForSplit = 360.dp,
                preferSecondWhenCollapsed = true
            )
        } else {
            centerAndBottomContent()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TrackWorkspaceBg)
    ) {
        if (showRightPanel) {
            ResizableStudioSplitPane(
                firstPane = leftPlusCenter,
                secondPane = {
                    RightSideBrowserPanel(
                        selectedTrack = selectedTrack,
                        presetPatterns = presetPatterns,
                        soundAssets = soundAssets,
                        localProjects = localProjects,
                        onStartDragItem = { _ -> },
                        onMoveDragItem = { _ -> },
                        onEndDragItem = {},
                        onQuickInsertItem = { cat, itemName ->
                            onDropBrowserItem(cat, itemName, selectedTrackId, playheadBeat)
                        },
                        onPreviewIndianInstrument = onPreviewIndianInstrument,
                        onPreviewMidiPitch = onPreviewMidiPitch,
                        onImportAudioFile = {},
                        onLoadLocalProject = { _ -> },
                        onCollapse = onToggleRightBrowser
                    )
                },
                initialRatio = 0.74f,
                minWidthForSplit = 480.dp,
                preferSecondWhenCollapsed = false
            )
        } else {
            leftPlusCenter()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MultitrackTimelineCenterPane(
    project: DawProject,
    selectedTrackId: String,
    playheadBeat: Double,
    timelineZoom: Float,
    onSelectTrack: (String) -> Unit,
    onAddTrack: (TrackType) -> Unit,
    onUpdateTrack: (String, String, (DawTrack) -> DawTrack) -> Unit,
    onFreezeTrack: (String) -> Unit,
    onSetZoom: (Float) -> Unit,
    onAddMarker: (String, Double) -> Unit,
    onAddSection: (String, Int, Int) -> Unit,
    onUpdateLoopRange: (Double, Double) -> Unit,
    onMoveClip: (String, String, Double) -> Unit,
    onDuplicateClip: (String, String) -> Unit,
    onDeleteClip: (String, String) -> Unit,
    onAddAutomationPoint: (String, Double, Float) -> Unit,
    onSeekBeat: (Double) -> Unit,
    onOpenPianoRoll: (String) -> Unit,
    onOpenVocalEditor: () -> Unit,
    onRelinkClip: (String) -> Unit
) {
    var showClipLauncher by remember { mutableStateOf(false) }
    var showAddTrackMenu by remember { mutableStateOf(false) }
    var contextTrackId by remember { mutableStateOf<String?>(null) }
    var clipContextPair by remember { mutableStateOf<Pair<String, AudioClip>?>(null) }
    var automationOverlayVisible by remember { mutableStateOf(true) }
    val timelineScroll = rememberScrollState()
    val density = LocalDensity.current

    val totalTimelineBeats = 16.0
    val laneWidthDp = (520f * timelineZoom).dp
    val pxPerBeat = with(density) { (laneWidthDp.toPx() / totalTimelineBeats.toFloat()).coerceAtLeast(12f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(6.dp)
            .pointerInput(timelineZoom) {
                detectTransformGestures { _, _, zoom, _ ->
                    if (kotlin.math.abs(zoom - 1f) > 0.02f) {
                        onSetZoom((timelineZoom * zoom).coerceIn(0.6f, 2.8f))
                    }
                }
            }
    ) {
        // Timeline Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box {
                Button(
                    onClick = { showAddTrackMenu = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CyanSignal, contentColor = ObsidianBg),
                    modifier = Modifier.height(30.dp).testTag("add_track_btn")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Track", style = MaterialTheme.typography.labelSmall)
                }
                DropdownMenu(expanded = showAddTrackMenu, onDismissRequest = { showAddTrackMenu = false }) {
                    TrackType.entries.filter { it != TrackType.MASTER }.forEach { t ->
                        DropdownMenuItem(
                            text = { Text("Add ${t.label} Track") },
                            onClick = {
                                onAddTrack(t)
                                showAddTrackMenu = false
                            }
                        )
                    }
                }
            }

            FilterChip(
                selected = showClipLauncher,
                onClick = { showClipLauncher = !showClipLauncher },
                label = { Text(if (showClipLauncher) "Timeline" else "Clips", style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(30.dp)
            )
            FilterChip(
                selected = automationOverlayVisible,
                onClick = { automationOverlayVisible = !automationOverlayVisible },
                label = { Text("Auto", style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(30.dp)
            )
            OutlinedButton(
                onClick = { onAddMarker("Drop ${playheadBeat.toInt()}", playheadBeat) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(30.dp)
            ) { Text("+Marker", style = MaterialTheme.typography.labelSmall) }
            OutlinedButton(
                onClick = { onAddSection("Bridge", project.sections.sumOf { it.lengthBars } + 1, 4) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(30.dp)
            ) { Text("+Section", style = MaterialTheme.typography.labelSmall) }

            // Loop Range Selector Pill
            Surface(
                color = ConsoleSurface,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.border(1.dp, ConsoleBorder, RoundedCornerShape(6.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Loop: ${project.loopStartBeat.toInt()}–${project.loopEndBeat.toInt()}b", style = MaterialTheme.typography.labelSmall, color = CyanSignal)
                    Text("-", color = AmberWarm, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clickable {
                        onUpdateLoopRange(project.loopStartBeat, (project.loopEndBeat - 4.0).coerceAtLeast(4.0))
                    }.padding(horizontal = 3.dp))
                    Text("+", color = AmberWarm, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clickable {
                        onUpdateLoopRange(project.loopStartBeat, (project.loopEndBeat + 4.0).coerceAtMost(32.0))
                    }.padding(horizontal = 3.dp))
                }
            }

            Icon(Icons.Rounded.ZoomIn, contentDescription = "Timeline Zoom", tint = TextSecondary, modifier = Modifier.size(16.dp))
            Slider(
                value = timelineZoom,
                onValueChange = onSetZoom,
                valueRange = 0.6f..2.8f,
                modifier = Modifier.width(90.dp).height(24.dp)
            )
        }

        Spacer(Modifier.height(4.dp))

        // Global Arranger / Chord / Loop & Scrubbable Bar Ruler
        Surface(
            color = ConsoleSurface,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ConsoleBorder, RoundedCornerShape(8.dp))
        ) {
            Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    project.sections.forEach { sec ->
                        Surface(
                            color = Color(sec.colorArgb).copy(alpha = 0.22f),
                            shape = RoundedCornerShape(5.dp),
                            modifier = Modifier
                                .border(1.dp, Color(sec.colorArgb), RoundedCornerShape(5.dp))
                                .clickable { onSeekBeat((sec.startBar - 1) * project.timeSignature.numerator.toDouble()) }
                        ) {
                            Text(
                                text = "${sec.name}(${sec.lengthBars}b)",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    project.chordTrack.forEach { ch ->
                        Surface(
                            color = ElevatedRack,
                            shape = RoundedCornerShape(5.dp),
                            modifier = Modifier.clickable { onSeekBeat(ch.beatPosition) }
                        ) {
                            Text(ch.symbol, style = MaterialTheme.typography.labelSmall, color = AmberWarm, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                    project.markers.forEach { mk ->
                        Text("${mk.label}@${mk.beatPosition.toInt()}b", style = MaterialTheme.typography.labelSmall, color = CyanSignal, modifier = Modifier.clickable { onSeekBeat(mk.beatPosition) })
                    }
                }

                // Scrubbable Timeline Ruler + Loop Highlight + Playhead
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("RULER", style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = Modifier.width(116.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(22.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(ObsidianBg)
                            .horizontalScroll(timelineScroll)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(laneWidthDp)
                                .fillMaxHeight()
                                .pointerInput(pxPerBeat) {
                                    detectTapGestures { offset ->
                                        onSeekBeat((offset.x / pxPerBeat).toDouble().coerceIn(0.0, totalTimelineBeats))
                                    }
                                }
                        ) {
                            // Loop Selection Highlight
                            val loopStartFrac = (project.loopStartBeat / totalTimelineBeats).toFloat().coerceIn(0f, 1f)
                            val loopLenFrac = ((project.loopEndBeat - project.loopStartBeat) / totalTimelineBeats).toFloat().coerceIn(0.05f, 1f)
                            if (project.loopEnabled) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(loopLenFrac)
                                        .fillMaxHeight(0.35f)
                                        .offset(x = laneWidthDp * loopStartFrac)
                                        .background(CyanSignal.copy(alpha = 0.4f))
                                )
                            }
                            Row(modifier = Modifier.fillMaxSize()) {
                                for (bar in 1..4) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .border(0.5.dp, ConsoleBorder),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text("Bar $bar", style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.padding(start = 4.dp))
                                    }
                                }
                            }
                            val phFrac = (playheadBeat / totalTimelineBeats).toFloat().coerceIn(0f, 0.99f)
                            Box(
                                modifier = Modifier
                                    .offset(x = laneWidthDp * phFrac)
                                    .width(2.dp)
                                    .fillMaxHeight()
                                    .background(AmberWarm)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        if (showClipLauncher) {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(project.tracks.filter { it.type != TrackType.MASTER && it.type != TrackType.VCA }, key = { it.id }) { track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PanelSurface, RoundedCornerShape(8.dp))
                            .border(1.dp, ConsoleBorder, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(track.name, style = MaterialTheme.typography.labelMedium, color = Color(track.colorArgb), modifier = Modifier.width(108.dp))
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Scene A", "Scene B", "Scene C", "Scratch").forEachIndexed { idx, s ->
                                OutlinedButton(onClick = { onSeekBeat(idx * 4.0) }, modifier = Modifier.height(30.dp)) {
                                    Text(s, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Multitrack Horizontal Lanes with Synchronized Scroll, Drag-and-Drop Clips, Automation Curve, Long Press & Double Tap
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                items(project.tracks, key = { it.id }) { track ->
                    val isSelected = track.id == selectedTrackId
                    val trackColor = Color(track.colorArgb)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isSelected) ElevatedRack else PanelSurface, RoundedCornerShape(8.dp))
                            .border(if (isSelected) 1.5.dp else 1.dp, if (isSelected) trackColor else ConsoleBorder, RoundedCornerShape(8.dp))
                            .padding(6.dp)
                            .combinedClickable(
                                onClick = { onSelectTrack(track.id) },
                                onDoubleClick = {
                                    onSelectTrack(track.id)
                                    if (track.type == TrackType.VOCAL) onOpenVocalEditor() else onOpenPianoRoll(track.id)
                                },
                                onLongClick = { contextTrackId = track.id }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Compact Left Track Header
                        Column(modifier = Modifier.width(110.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(trackColor))
                                Spacer(Modifier.width(4.dp))
                                Text(track.name, style = MaterialTheme.typography.labelMedium, color = TextPrimary, maxLines = 1)
                            }
                            Text(track.type.label, style = MaterialTheme.typography.labelSmall, color = if (track.frozen) CyanSignal else TextSecondary)
                            Spacer(Modifier.height(2.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                TimelineMiniToggle("M", track.muted, CrimsonClip) { onUpdateTrack(track.id, "Mute") { it.copy(muted = !it.muted) } }
                                TimelineMiniToggle("S", track.soloed, AmberWarm) { onUpdateTrack(track.id, "Solo") { it.copy(soloed = !it.soloed) } }
                                TimelineMiniToggle("R", track.armed, CrimsonClip) { onUpdateTrack(track.id, "Arm") { it.copy(armed = !it.armed) } }
                            }
                        }

                        Spacer(Modifier.width(6.dp))

                        // Horizontal Track Lane (Scrollable + Drag Audio Clips + Automation Curve + Playhead)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(60.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(ObsidianBg)
                                .border(1.dp, ConsoleBorder, RoundedCornerShape(6.dp))
                                .horizontalScroll(timelineScroll)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(laneWidthDp)
                                    .fillMaxHeight()
                                    .pointerInput(track.id, automationOverlayVisible, pxPerBeat) {
                                        if (automationOverlayVisible) {
                                            detectTapGestures { offset ->
                                                val beat = (offset.x / pxPerBeat).toDouble()
                                                val normVal = (1f - offset.y / size.height.coerceAtLeast(1)).coerceIn(0.1f, 1f)
                                                onAddAutomationPoint(track.id, beat, normVal)
                                            }
                                        }
                                    }
                            ) {
                                // Audio Clips (Drag horizontally to move, Long press for context menu, Double tap to edit)
                                track.clips.forEach { clip ->
                                    var dragDeltaPx by remember(clip.id, clip.startBeat) { mutableFloatStateOf(0f) }
                                    val startFrac = ((clip.startBeat + dragDeltaPx / pxPerBeat) / totalTimelineBeats).toFloat().coerceIn(0f, 0.9f)
                                    val lenFrac = (clip.lengthBeats / totalTimelineBeats).toFloat().coerceIn(0.08f, 1f)

                                    Surface(
                                        color = trackColor.copy(alpha = 0.24f),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier
                                            .fillMaxWidth(lenFrac)
                                            .fillMaxHeight(0.86f)
                                            .align(Alignment.CenterStart)
                                            .offset(x = laneWidthDp * startFrac)
                                            .border(1.dp, trackColor, RoundedCornerShape(6.dp))
                                            .pointerInput(clip.id, pxPerBeat) {
                                                detectDragGestures(
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        dragDeltaPx += dragAmount.x
                                                    },
                                                    onDragEnd = {
                                                        val newBeat = clip.startBeat + (dragDeltaPx / pxPerBeat)
                                                        dragDeltaPx = 0f
                                                        onMoveClip(track.id, clip.id, newBeat)
                                                    },
                                                    onDragCancel = { dragDeltaPx = 0f }
                                                )
                                            }
                                            .combinedClickable(
                                                onClick = {
                                                    onSelectTrack(track.id)
                                                    if (clip.missingSource) onRelinkClip(clip.id)
                                                },
                                                onDoubleClick = {
                                                    onSelectTrack(track.id)
                                                    if (track.type == TrackType.VOCAL) onOpenVocalEditor() else clipContextPair = track.id to clip
                                                },
                                                onLongClick = { clipContextPair = track.id to clip }
                                            )
                                    ) {
                                        Column(modifier = Modifier.padding(3.dp)) {
                                            Text(
                                                text = if (clip.missingSource) "RELINK: ${clip.name}" else clip.name,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (clip.missingSource) CrimsonClip else TextPrimary,
                                                maxLines = 1
                                            )
                                            WaveformCanvas(
                                                waveform = clip.waveformPreview.ifEmpty { List(24) { 0.35f } },
                                                accentColor = trackColor,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }

                                // MIDI Note Preview Block (Double-tap to edit in Piano Roll)
                                if (track.midiNotes.isNotEmpty()) {
                                    Surface(
                                        color = trackColor.copy(alpha = 0.20f),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier
                                            .fillMaxWidth(0.65f)
                                            .fillMaxHeight(0.86f)
                                            .align(Alignment.CenterStart)
                                            .border(1.dp, trackColor, RoundedCornerShape(6.dp))
                                            .combinedClickable(
                                                onClick = { onSelectTrack(track.id) },
                                                onDoubleClick = {
                                                    onSelectTrack(track.id)
                                                    onOpenPianoRoll(track.id)
                                                },
                                                onLongClick = { contextTrackId = track.id }
                                            )
                                    ) {
                                        Column(modifier = Modifier.padding(4.dp)) {
                                            Text("${track.instrumentName} (${track.midiNotes.size} notes)", style = MaterialTheme.typography.labelSmall, color = TextPrimary, maxLines = 1)
                                            Canvas(modifier = Modifier.fillMaxSize()) {
                                                track.midiNotes.take(24).forEach { n ->
                                                    val nx = ((n.startBeat / 10.0) * size.width).toFloat().coerceIn(0f, size.width - 8f)
                                                    val ny = ((1f - (n.pitch - 48f) / 36f) * size.height).coerceIn(2f, size.height - 4f)
                                                    drawRect(VioletMidi, topLeft = Offset(nx, ny), size = androidx.compose.ui.geometry.Size(14f, 4f))
                                                }
                                            }
                                        }
                                    }
                                }

                                // Track Automation Curve Overlay
                                val volLane = track.automationLanes.firstOrNull { it.targetParam == "volume" }
                                if (automationOverlayVisible && volLane != null && volLane.points.isNotEmpty()) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val path = Path()
                                        volLane.points.forEachIndexed { idx, pt ->
                                            val x = ((pt.beatPosition / totalTimelineBeats) * size.width).toFloat().coerceIn(0f, size.width)
                                            val y = ((1f - pt.normalizedValue) * size.height).coerceIn(4f, size.height - 4f)
                                            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                            drawCircle(AmberWarm, radius = 3.5.dp.toPx(), center = Offset(x, y))
                                        }
                                        drawPath(path, color = AmberWarm, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                                    }
                                }

                                // Playhead Cursor
                                val phFrac = (playheadBeat / totalTimelineBeats).toFloat().coerceIn(0f, 0.99f)
                                Box(
                                    modifier = Modifier
                                        .offset(x = laneWidthDp * phFrac)
                                        .width(1.5.dp)
                                        .fillMaxHeight()
                                        .background(AmberWarm)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Track Context Menu Dialog (Long-press)
    contextTrackId?.let { tid ->
        val tr = project.tracks.firstOrNull { it.id == tid }
        if (tr != null) {
            AlertDialog(
                onDismissRequest = { contextTrackId = null },
                containerColor = PanelSurface,
                title = { Text("Track Context: ${tr.name}", color = TextPrimary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onFreezeTrack(tr.id); contextTrackId = null }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (tr.frozen) "Unfreeze Track" else "Freeze & Bounce Track")
                        }
                        OutlinedButton(onClick = { onUpdateTrack(tr.id, "Mono/Stereo") { it.copy(mono = !it.mono) }; contextTrackId = null }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (tr.mono) "Switch to Stereo" else "Switch to Mono")
                        }
                        OutlinedButton(onClick = { onAddAutomationPoint(tr.id, playheadBeat, 0.75f); contextTrackId = null }, modifier = Modifier.fillMaxWidth()) {
                            Text("Add Volume Automation Point")
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { contextTrackId = null }) { Text("Close") } }
            )
        }
    }

    // Audio Clip Context Menu Dialog (Long-press / Double-tap)
    clipContextPair?.let { (tid, clip) ->
        AlertDialog(
            onDismissRequest = { clipContextPair = null },
            containerColor = PanelSurface,
            title = { Text("Clip Actions: ${clip.name}", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Position: Beat ${String.format("%.2f", clip.startBeat)} • Length: ${clip.lengthBeats} beats", color = TextSecondary)
                    Button(onClick = { onDuplicateClip(tid, clip.id); clipContextPair = null }, modifier = Modifier.fillMaxWidth()) {
                        Text("Duplicate Clip")
                    }
                    OutlinedButton(onClick = { onRelinkClip(clip.id); clipContextPair = null }, modifier = Modifier.fillMaxWidth()) {
                        Text("Replace / Relink WAV/AIFF")
                    }
                    OutlinedButton(onClick = { onDeleteClip(tid, clip.id); clipContextPair = null }, modifier = Modifier.fillMaxWidth()) {
                        Text("Delete Clip", color = CrimsonClip)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { clipContextPair = null }) { Text("Close") } }
        )
    }
}

@Composable
private fun TimelineMiniToggle(label: String, active: Boolean, activeColor: Color, onClick: () -> Unit) {
    Surface(
        color = if (active) activeColor else ElevatedRack,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier
            .size(22.dp)
            .border(1.dp, if (active) activeColor else ConsoleBorder, RoundedCornerShape(4.dp))
            .clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = if (active) ObsidianBg else TextSecondary)
        }
    }
}
