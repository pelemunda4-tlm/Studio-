package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.*
import com.example.ui.components.WaveformCanvas
import com.example.ui.theme.*
import java.util.UUID

@Composable
fun BottomEditorPanel(
    project: DawProject,
    selectedTrack: DawTrack?,
    selectedClipId: String?,
    activeTab: BottomEditorTab,
    playheadBeat: Double,
    activePattern: RhythmPattern,
    trackMeters: Map<String, Float>,
    masterMeters: MeterSnapshot,
    onSelectTab: (BottomEditorTab) -> Unit,
    onClosePanel: () -> Unit,
    onSelectTrack: (String) -> Unit,
    onAddTrack: (TrackType) -> Unit,
    onUpdateTrack: (String, String, (DawTrack) -> DawTrack) -> Unit,
    onFreezeTrack: (String) -> Unit,
    onOpenPluginRack: (String) -> Unit,
    onToggleQuickPlugin: (String, PluginType) -> Unit,
    onUpdateSend: (String, String, String, Float) -> Unit,
    onUpdateClipGain: (String, String, Float) -> Unit,
    onSplitClip: (String, String, Double) -> Unit,
    onToggleClipMuted: (String, String) -> Unit,
    onRelinkClip: (String) -> Unit,
    onAddAutomationPoint: (String, Double, Float) -> Unit,
    onSelectKeyAndScale: (String, MusicalScale) -> Unit,
    onToggleMidiNote: (Int, Double) -> Unit,
    onUpdateMidiNoteVelocity: (String, String, Int) -> Unit,
    onQuantizeMidi: () -> Unit,
    onHumanizeMidi: () -> Unit,
    onRandomizeMidiVel: () -> Unit,
    onArpeggiateMidi: () -> Unit,
    onPreviewMidiPitch: (String, Int) -> Unit,
    onToggleDrumStep: (IndianInstrument, Int) -> Unit,
    onTriggerDrumInstrument: (IndianInstrument) -> Unit,
    onPreviewChord: (ChordEvent) -> Unit,
    onAddChordEvent: (String, List<Int>, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeTrack = selectedTrack ?: project.tracks.firstOrNull()
    val activeClip = activeTrack?.clips?.firstOrNull { it.id == selectedClipId }
        ?: activeTrack?.clips?.firstOrNull()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ConsoleSurface)
            .border(1.dp, ConsoleBorder)
    ) {
        // Studio One Lower Zone Header Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PanelSurface)
                .border(0.5.dp, ConsoleBorder)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "EDITOR",
                style = MaterialTheme.typography.labelSmall,
                color = StudioBlue,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 4.dp)
            )
            BottomEditorTab.entries.forEach { tab ->
                val selected = activeTab == tab
                val accent = when (tab) {
                    BottomEditorTab.AUDIO_EDITOR -> AudioGreen
                    BottomEditorTab.PIANO_ROLL -> VioletMidi
                    BottomEditorTab.DRUM_EDITOR -> SaffronIndian
                    BottomEditorTab.AUTOMATION -> AutomationOrange
                    BottomEditorTab.CHORD_TRACK -> StudioBlue
                    BottomEditorTab.EVENT_INSPECTOR -> CyanSignal
                    BottomEditorTab.MIXER_DOCK -> AmberWarm
                }
                Surface(
                    color = if (selected) accent.copy(alpha = 0.22f) else ConsoleSurface,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .border(1.dp, if (selected) accent else ConsoleBorder, RoundedCornerShape(4.dp))
                        .clickable { onSelectTab(tab) }
                        .testTag("bottom_tab_${tab.name}")
                ) {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) accent else TextSecondary,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClosePanel, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "Collapse Bottom Editor", tint = TextSecondary, modifier = Modifier.size(15.dp))
            }
        }

        // Tab Body Content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(TrackWorkspaceBg)
        ) {
            when (activeTab) {
                BottomEditorTab.AUDIO_EDITOR -> AudioEditorPane(
                    track = activeTrack,
                    clip = activeClip,
                    playheadBeat = playheadBeat,
                    onUpdateClipGain = onUpdateClipGain,
                    onSplitClip = onSplitClip,
                    onToggleClipMuted = onToggleClipMuted,
                    onRelinkClip = onRelinkClip
                )
                BottomEditorTab.PIANO_ROLL -> PianoRollScreen(
                    project = project,
                    selectedTrackId = activeTrack?.id.orEmpty(),
                    playheadBeat = playheadBeat,
                    onSelectKeyAndScale = onSelectKeyAndScale,
                    onSelectInstrument = { trId, inst ->
                        onUpdateTrack(trId, "Instrument $inst") { it.copy(instrumentName = inst) }
                    },
                    onToggleNote = onToggleMidiNote,
                    onUpdateNoteVelocity = onUpdateMidiNoteVelocity,
                    onQuantize = onQuantizeMidi,
                    onHumanize = onHumanizeMidi,
                    onRandomizeVelocity = onRandomizeMidiVel,
                    onArpeggiate = onArpeggiateMidi,
                    onPreviewPitch = { pitch ->
                        onPreviewMidiPitch(activeTrack?.instrumentName ?: "Grand Piano", pitch)
                    }
                )
                BottomEditorTab.DRUM_EDITOR -> DrumEditorDockPane(
                    pattern = activePattern,
                    onToggleStep = onToggleDrumStep,
                    onTriggerInstrument = onTriggerDrumInstrument
                )
                BottomEditorTab.AUTOMATION -> AutomationEditorPane(
                    track = activeTrack,
                    playheadBeat = playheadBeat,
                    onAddPoint = onAddAutomationPoint,
                    onClearPoints = { trId ->
                        onUpdateTrack(trId, "Clear Automation") { tr ->
                            tr.copy(automationLanes = tr.automationLanes.filterNot { it.targetParam == "volume" })
                        }
                    }
                )
                BottomEditorTab.CHORD_TRACK -> ChordTrackEditorPane(
                    project = project,
                    onSelectKeyAndScale = onSelectKeyAndScale,
                    onPreviewChord = onPreviewChord,
                    onAddChord = onAddChordEvent
                )
                BottomEditorTab.EVENT_INSPECTOR -> EventInspectorPane(
                    track = activeTrack,
                    clip = activeClip,
                    project = project
                )
                BottomEditorTab.MIXER_DOCK -> MixerConsoleScreen(
                    project = project,
                    selectedTrackId = activeTrack?.id.orEmpty(),
                    trackMeters = trackMeters,
                    masterMeters = masterMeters,
                    onSelectTrack = onSelectTrack,
                    onAddTrack = onAddTrack,
                    onUpdateTrack = onUpdateTrack,
                    onFreezeTrack = onFreezeTrack,
                    onOpenPluginRack = onOpenPluginRack,
                    onToggleQuickPlugin = onToggleQuickPlugin,
                    onUpdateSend = onUpdateSend,
                    compactMode = true
                )
            }
        }
    }
}

@Composable
private fun AudioEditorPane(
    track: DawTrack?,
    clip: AudioClip?,
    playheadBeat: Double,
    onUpdateClipGain: (String, String, Float) -> Unit,
    onSplitClip: (String, String, Double) -> Unit,
    onToggleClipMuted: (String, String) -> Unit,
    onRelinkClip: (String) -> Unit
) {
    if (track == null || clip == null) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Text("Select or drop an Audio Region in the Arrangement Timeline to edit its waveform, gain, and split points.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("AUDIO EDITOR: ${clip.name}", style = MaterialTheme.typography.labelLarge, color = AudioGreen, fontWeight = FontWeight.Bold)
            Text("Start: ${String.format("%.2f", clip.startBeat)}b • Len: ${String.format("%.2f", clip.lengthBeats)}b", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            OutlinedButton(
                onClick = {
                    val splitPoint = if (playheadBeat > clip.startBeat && playheadBeat < clip.startBeat + clip.lengthBeats) playheadBeat else clip.startBeat + clip.lengthBeats / 2.0
                    onSplitClip(track.id, clip.id, splitPoint)
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp)
            ) { Text("Split Clip", style = MaterialTheme.typography.labelSmall) }
            OutlinedButton(
                onClick = { onToggleClipMuted(track.id, clip.id) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp)
            ) { Text(if (clip.muted) "Unmute Clip" else "Mute Clip", style = MaterialTheme.typography.labelSmall) }
            OutlinedButton(
                onClick = { onRelinkClip(clip.id) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp)
            ) { Text("Relink WAV", style = MaterialTheme.typography.labelSmall) }
        }

        // High-Contrast Waveform Display
        Surface(
            color = ObsidianBg,
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(82.dp)
                .border(1.dp, if (clip.muted) ConsoleBorder else AudioGreen, RoundedCornerShape(6.dp))
        ) {
            WaveformCanvas(
                waveform = clip.waveformPreview.ifEmpty { List(32) { 0.45f } },
                accentColor = if (clip.muted) TextMuted else AudioGreen,
                modifier = Modifier.fillMaxSize().padding(8.dp)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Clip Gain: ${String.format("%+.1f", clip.gainDb)} dB", style = MaterialTheme.typography.labelMedium, color = TextPrimary, modifier = Modifier.width(120.dp))
            Slider(
                value = clip.gainDb,
                onValueChange = { onUpdateClipGain(track.id, clip.id, it) },
                valueRange = -24f..12f,
                modifier = Modifier.weight(1f).height(26.dp)
            )
        }
    }
}

@Composable
private fun DrumEditorDockPane(
    pattern: RhythmPattern,
    onToggleStep: (IndianInstrument, Int) -> Unit,
    onTriggerInstrument: (IndianInstrument) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("DRUM EDITOR: ${pattern.name} (${pattern.timeSignature.label})", style = MaterialTheme.typography.labelMedium, color = SaffronIndian, fontWeight = FontWeight.Bold)
        pattern.lanes.forEach { lane ->
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Surface(
                    color = PanelSurface,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .width(118.dp)
                        .border(1.dp, ConsoleBorder, RoundedCornerShape(4.dp))
                        .clickable { onTriggerInstrument(lane.instrument) }
                ) {
                    Text(
                        text = lane.instrument.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp)
                    )
                }
                lane.steps.forEachIndexed { idx, vel ->
                    val on = vel > 0.05f
                    Box(
                        modifier = Modifier
                            .size(width = 24.dp, height = 24.dp)
                            .background(if (on) AutomationOrange else ConsoleSurface, RoundedCornerShape(3.dp))
                            .border(1.dp, if (idx % 4 == 0) StudioBlue.copy(alpha = 0.7f) else ConsoleBorder, RoundedCornerShape(3.dp))
                            .clickable { onToggleStep(lane.instrument, idx) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AutomationEditorPane(
    track: DawTrack?,
    playheadBeat: Double,
    onAddPoint: (String, Double, Float) -> Unit,
    onClearPoints: (String) -> Unit
) {
    if (track == null) return
    val lane = track.automationLanes.firstOrNull { it.targetParam == "volume" }
    val points = lane?.points.orEmpty()

    Column(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("AUTOMATION: ${track.name} (Volume Curve — Tap Grid to Add Node)", style = MaterialTheme.typography.labelMedium, color = AutomationOrange, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = { onAddPoint(track.id, playheadBeat, 0.8f) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(26.dp)
                ) { Text("+Node @ Playhead", style = MaterialTheme.typography.labelSmall) }
                OutlinedButton(
                    onClick = { onClearPoints(track.id) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(26.dp)
                ) { Text("Clear", style = MaterialTheme.typography.labelSmall, color = CrimsonClip) }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(ObsidianBg, RoundedCornerShape(6.dp))
                .border(1.dp, AutomationOrange.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                .pointerInput(track.id) {
                    detectTapGestures { offset ->
                        val beat = ((offset.x / size.width.coerceAtLeast(1)) * 16.0).toDouble()
                        val normVal = (1f - offset.y / size.height.coerceAtLeast(1)).coerceIn(0.05f, 1f)
                        onAddPoint(track.id, beat, normVal)
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                for (b in 1..15) {
                    val gx = (b / 16f) * size.width
                    drawLine(ConsoleBorder.copy(alpha = 0.45f), Offset(gx, 0f), Offset(gx, size.height), strokeWidth = 1f)
                }
                if (points.isNotEmpty()) {
                    val path = Path()
                    points.forEachIndexed { idx, pt ->
                        val x = ((pt.beatPosition / 16.0) * size.width).toFloat().coerceIn(0f, size.width)
                        val y = ((1f - pt.normalizedValue) * size.height).toFloat().coerceIn(6f, size.height - 6f)
                        if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        drawCircle(AutomationOrange, radius = 4.5.dp.toPx(), center = Offset(x, y))
                    }
                    drawPath(path, color = AutomationOrange, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
                }
            }
        }
    }
}

@Composable
private fun ChordTrackEditorPane(
    project: DawProject,
    onSelectKeyAndScale: (String, MusicalScale) -> Unit,
    onPreviewChord: (ChordEvent) -> Unit,
    onAddChord: (String, List<Int>, Double) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(10.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("CHORD TRACK (${project.musicalKey} ${project.scale.displayName})", style = MaterialTheme.typography.labelLarge, color = StudioBlue, fontWeight = FontWeight.Bold)
            val nextBeat = (project.chordTrack.maxOfOrNull { it.beatPosition + it.durationBeats } ?: 0.0)
            AssistChip(onClick = { onAddChord("Gmaj7", listOf(55, 59, 62, 66), nextBeat) }, label = { Text("+Gmaj7") })
            AssistChip(onClick = { onAddChord("A7", listOf(57, 61, 64, 67), nextBeat) }, label = { Text("+A7") })
            AssistChip(onClick = { onAddChord("Bm7", listOf(59, 62, 66, 69), nextBeat) }, label = { Text("+Bm7") })
            AssistChip(onClick = { onSelectKeyAndScale("D", MusicalScale.RAGA_YAMAN) }, label = { Text("Yaman D") })
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            project.chordTrack.forEach { chord ->
                Surface(
                    color = PanelSurface,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .border(1.dp, StudioBlue, RoundedCornerShape(6.dp))
                        .clickable { onPreviewChord(chord) }
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(chord.symbol, style = MaterialTheme.typography.titleMedium, color = AmberWarm, fontWeight = FontWeight.Bold)
                        Text("Beat ${chord.beatPosition.toInt()} • ${chord.durationBeats.toInt()}b", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun EventInspectorPane(
    track: DawTrack?,
    clip: AudioClip?,
    project: DawProject
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(10.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("EVENT INSPECTOR", style = MaterialTheme.typography.labelLarge, color = CyanSignal, fontWeight = FontWeight.Bold)
        if (track != null) {
            Text("Track: ${track.name} (${track.type.label}) • Input: ${track.inputChannel} -> ${track.outputChannel}", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Text("Instrument: ${track.instrumentName} • MIDI Notes: ${track.midiNotes.size} • Audio Regions: ${track.clips.size} • Inserts: ${track.plugins.size}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
        if (clip != null) {
            Text("Selected Event: ${clip.name} | Start Beat: ${clip.startBeat} | Length: ${clip.lengthBeats}b | Gain: ${clip.gainDb} dB | Muted: ${clip.muted}", style = MaterialTheme.typography.labelSmall, color = AudioGreen)
        }
        Text("Snap: ${if (project.snapEnabled) "Enabled (${project.gridResolution.label})" else "Off"} • Tempo Points: ${project.tempoTrack.size}", style = MaterialTheme.typography.labelSmall, color = AmberWarm)
    }
}
