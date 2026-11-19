package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.AiMusicSuggestion
import com.example.data.StudioSettings
import com.example.model.*
import com.example.ui.theme.*
import com.example.viewmodel.UiState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun StudioMainToolbar(
    project: DawProject,
    activeTool: ArrangementTool,
    canUndo: Boolean,
    canRedo: Boolean,
    showPanelToggles: Boolean,
    showLeftInspector: Boolean,
    showBottomEditor: Boolean,
    showBottomMixer: Boolean,
    showRightBrowser: Boolean,
    onSelectTool: (ArrangementTool) -> Unit,
    onToggleSnap: () -> Unit,
    onSelectGrid: (GridResolution) -> Unit,
    onSelectTimeSignature: (TimeSignature) -> Unit,
    onBpmDelta: (Double) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSaveProject: () -> Unit,
    onOpenUndoHistory: () -> Unit,
    onOpenAiAssistant: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleLeftInspector: () -> Unit,
    onToggleBottomEditor: () -> Unit,
    onToggleBottomMixer: () -> Unit,
    onToggleRightBrowser: () -> Unit
) {
    var showGridMenu by remember { mutableStateOf(false) }
    var showTimeSigMenu by remember { mutableStateOf(false) }

    Surface(
        color = ConsoleSurface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ConsoleBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Application Logo + Project Name
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.padding(end = 2.dp)
            ) {
                Surface(
                    color = StudioBlue,
                    shape = RoundedCornerShape(5.dp),
                    modifier = Modifier.size(26.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "TW",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                Column(modifier = Modifier.widthIn(max = 128.dp)) {
                    Text(
                        text = "TRUEWORLD STUDIO",
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioBlue,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = project.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Save, Undo, Redo
            IconButton(
                onClick = onSaveProject,
                modifier = Modifier.size(30.dp).testTag("toolbar_save_btn")
            ) {
                Icon(Icons.Rounded.Save, contentDescription = "Save Project", tint = AudioGreen, modifier = Modifier.size(17.dp))
            }
            IconButton(
                onClick = onUndo,
                enabled = canUndo,
                modifier = Modifier.size(30.dp).testTag("toolbar_undo_btn")
            ) {
                Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "Undo", tint = if (canUndo) TextPrimary else TextMuted, modifier = Modifier.size(17.dp))
            }
            IconButton(
                onClick = onRedo,
                enabled = canRedo,
                modifier = Modifier.size(30.dp).testTag("toolbar_redo_btn")
            ) {
                Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = "Redo", tint = if (canRedo) TextPrimary else TextMuted, modifier = Modifier.size(17.dp))
            }
            Surface(
                color = PanelSurface,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.border(1.dp, ConsoleBorder, RoundedCornerShape(4.dp)).clickable { onOpenUndoHistory() }
            ) {
                Text("Hist", style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp))
            }

            // 8 Arrangement Editing Tools
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(TrackWorkspaceBg, RoundedCornerShape(6.dp))
                    .border(1.dp, ConsoleBorder, RoundedCornerShape(6.dp))
                    .padding(horizontal = 3.dp, vertical = 2.dp)
            ) {
                ArrangementTool.entries.forEach { tool ->
                    val selected = activeTool == tool
                    Surface(
                        color = if (selected) StudioBlue else Color.Transparent,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .clickable { onSelectTool(tool) }
                            .testTag("tool_${tool.name}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = iconForTool(tool),
                                contentDescription = "${tool.label} Tool",
                                tint = if (selected) TextPrimary else TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = tool.shortCode,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) TextPrimary else TextMuted,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // Snap & Grid Resolution
            DockTogglePill(
                label = if (project.snapEnabled) "SNAP ON" else "SNAP",
                active = project.snapEnabled,
                activeColor = StudioBlue,
                onClick = onToggleSnap,
                tag = "toolbar_snap_btn"
            )
            Box {
                Surface(
                    color = PanelSurface,
                    shape = RoundedCornerShape(5.dp),
                    modifier = Modifier
                        .border(1.dp, ConsoleBorder, RoundedCornerShape(5.dp))
                        .clickable { showGridMenu = true }
                        .testTag("toolbar_grid_btn")
                ) {
                    Text(
                        text = "Grid: ${project.gridResolution.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
                DropdownMenu(expanded = showGridMenu, onDismissRequest = { showGridMenu = false }) {
                    GridResolution.entries.forEach { res ->
                        DropdownMenuItem(
                            text = { Text("Grid ${res.label}") },
                            onClick = {
                                onSelectGrid(res)
                                showGridMenu = false
                            }
                        )
                    }
                }
            }

            // BPM & Time Signature
            Surface(
                color = TrackWorkspaceBg,
                shape = RoundedCornerShape(5.dp),
                modifier = Modifier.border(1.dp, ConsoleBorder, RoundedCornerShape(5.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("-", color = TextSecondary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clickable { onBpmDelta(-1.0) }.padding(horizontal = 2.dp))
                    Text("${project.bpm.toInt()} BPM", color = AmberWarm, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text("+", color = TextSecondary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clickable { onBpmDelta(1.0) }.padding(horizontal = 2.dp))
                    Box {
                        Text(
                            text = project.timeSignature.label,
                            color = StudioBlue,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { showTimeSigMenu = true }.padding(horizontal = 3.dp)
                        )
                        DropdownMenu(expanded = showTimeSigMenu, onDismissRequest = { showTimeSigMenu = false }) {
                            TimeSignature.PRESETS.forEach { ts ->
                                DropdownMenuItem(
                                    text = { Text(ts.label) },
                                    onClick = {
                                        onSelectTimeSignature(ts)
                                        showTimeSigMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Dock Panel Toggles (Inspector / Editor / Mixer / Browser)
            if (showPanelToggles) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    DockTogglePill("INSP", showLeftInspector, StudioBlue, onToggleLeftInspector, "toggle_left_panel_btn")
                    DockTogglePill("EDIT", showBottomEditor, VioletMidi, onToggleBottomEditor, "toggle_bottom_panel_btn")
                    DockTogglePill("MIX", showBottomMixer, AudioGreen, onToggleBottomMixer, "toggle_mixer_dock_btn")
                    DockTogglePill("BRW", showRightBrowser, AmberWarm, onToggleRightBrowser, "toggle_right_panel_btn")
                }
            }

            // AI Assistant & Audio Settings
            IconButton(
                onClick = onOpenAiAssistant,
                modifier = Modifier.size(30.dp).testTag("open_ai_assistant_btn")
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = "AI Music Assistant", tint = VioletMidi, modifier = Modifier.size(17.dp))
            }
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(30.dp).testTag("open_studio_settings_btn")
            ) {
                Icon(Icons.Rounded.Tune, contentDescription = "Audio Settings", tint = TextSecondary, modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
fun StudioTransportBar(
    project: DawProject,
    playheadBeat: Double,
    isPlaying: Boolean,
    isRecording: Boolean,
    metronomeOn: Boolean,
    hardwareStatus: StudioHardwareStatus,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onRecord: () -> Unit,
    onToggleLoop: () -> Unit,
    onToggleMetronome: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val beatsPerBar = project.timeSignature.numerator.coerceAtLeast(1)
    val barNum = (playheadBeat / beatsPerBar).toInt() + 1
    val beatInBar = (playheadBeat % beatsPerBar).toInt() + 1
    val tick = (((playheadBeat % 1.0) * 960).toInt()).coerceIn(0, 959)
    val timecode = String.format("%03d:%d:%03d", barNum, beatInBar, tick)

    Surface(
        color = ConsoleSurface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ConsoleBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Rewind, Forward, Stop, Play, Pause, Record
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilledIconButton(
                    onClick = onRewind,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = PanelSurface, contentColor = TextPrimary),
                    modifier = Modifier.size(32.dp).testTag("transport_rewind_btn")
                ) {
                    Icon(Icons.Rounded.FastRewind, contentDescription = "Rewind", modifier = Modifier.size(17.dp))
                }
                FilledIconButton(
                    onClick = onForward,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = PanelSurface, contentColor = TextPrimary),
                    modifier = Modifier.size(32.dp).testTag("transport_forward_btn")
                ) {
                    Icon(Icons.Rounded.FastForward, contentDescription = "Forward", modifier = Modifier.size(17.dp))
                }
                FilledIconButton(
                    onClick = onStop,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = PanelSurface, contentColor = TextPrimary),
                    modifier = Modifier.size(32.dp).testTag("transport_stop_btn")
                ) {
                    Icon(Icons.Rounded.Stop, contentDescription = "Stop", modifier = Modifier.size(17.dp))
                }
                FilledIconButton(
                    onClick = onPlay,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isPlaying) AudioGreen else ElevatedRack,
                        contentColor = if (isPlaying) TrackWorkspaceBg else TextPrimary
                    ),
                    modifier = Modifier.size(34.dp).testTag("transport_play_btn")
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", modifier = Modifier.size(19.dp))
                }
                FilledIconButton(
                    onClick = onPause,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (!isPlaying && playheadBeat > 0.0) StudioBlueMuted else PanelSurface,
                        contentColor = TextPrimary
                    ),
                    modifier = Modifier.size(32.dp).testTag("transport_pause_btn")
                ) {
                    Icon(Icons.Rounded.Pause, contentDescription = "Pause", modifier = Modifier.size(17.dp))
                }
                FilledIconButton(
                    onClick = onRecord,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isRecording) CrimsonClip else PanelSurface,
                        contentColor = TextPrimary
                    ),
                    modifier = Modifier.size(32.dp).testTag("transport_record_btn")
                ) {
                    Icon(Icons.Rounded.FiberManualRecord, contentDescription = "Record Audio", modifier = Modifier.size(17.dp))
                }
            }

            // Loop & Metronome
            DockTogglePill("LOOP", project.loopEnabled, StudioBlue, onToggleLoop, "transport_loop_btn")
            DockTogglePill("CLICK", metronomeOn, AmberWarm, onToggleMetronome, "transport_click_btn")

            // Studio One Position Counter & Tempo Display
            Surface(
                color = TrackWorkspaceBg,
                shape = RoundedCornerShape(5.dp),
                modifier = Modifier.border(1.dp, ConsoleBorder, RoundedCornerShape(5.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = timecode,
                        style = MaterialTheme.typography.labelLarge,
                        color = StudioBlue,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${project.bpm.toInt()} BPM • ${project.timeSignature.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary
                    )
                    Text(
                        text = "${project.musicalKey} ${project.scale.displayName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            // Loop Marker Range + Audio Engine Status
            Text(
                text = "Loop ${project.loopStartBeat.toInt()}–${project.loopEndBeat.toInt()}b",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Surface(
                color = TrackWorkspaceBg,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .border(1.dp, ConsoleBorder, RoundedCornerShape(4.dp))
                    .clickable { onOpenSettings() }
            ) {
                Text(
                    text = "${hardwareStatus.bufferSize} smp • ${String.format("%.1f", hardwareStatus.estimatedLatencyMs)} ms • ${if (hardwareStatus.precision64BitSumming) "64-bit" else "32-bit"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = AudioGreen,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                )
            }
        }
    }
}

private fun iconForTool(tool: ArrangementTool) = when (tool) {
    ArrangementTool.ARROW -> Icons.Rounded.NearMe
    ArrangementTool.RANGE -> Icons.Rounded.SelectAll
    ArrangementTool.SPLIT -> Icons.Rounded.ContentCut
    ArrangementTool.ERASER -> Icons.Rounded.Backspace
    ArrangementTool.PAINT -> Icons.Rounded.Brush
    ArrangementTool.MUTE -> Icons.Rounded.VolumeOff
    ArrangementTool.LISTEN -> Icons.Rounded.Headphones
    ArrangementTool.ZOOM -> Icons.Rounded.ZoomIn
}

@Composable
private fun DockTogglePill(
    label: String,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    tag: String
) {
    Surface(
        color = if (active) activeColor.copy(alpha = 0.22f) else PanelSurface,
        shape = RoundedCornerShape(5.dp),
        modifier = Modifier
            .border(1.dp, if (active) activeColor else ConsoleBorder, RoundedCornerShape(5.dp))
            .clickable { onClick() }
            .testTag(tag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) activeColor else TextSecondary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun RotaryStudioKnob(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    unit: String = "",
    accentColor: Color = CyanSignal,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    knobSize: Dp = 46.dp
) {
    val norm = ((value - min) / (max - min).coerceAtLeast(1e-5f)).coerceIn(0f, 1f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(2.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(knobSize)
                .pointerInput(min, max) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val delta = -dragAmount.y / 130f
                        val nextNorm = (norm + delta).coerceIn(0f, 1f)
                        onValueChange(min + nextNorm * (max - min))
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2f - 3.dp.toPx()
                val center = Offset(size.width / 2f, size.height / 2f)
                drawArc(
                    color = ConsoleBorder,
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = accentColor,
                    startAngle = 135f,
                    sweepAngle = 270f * norm,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                )
                drawCircle(color = ElevatedRack, radius = radius - 5.dp.toPx(), center = center)
                val angleRad = ((135f + 270f * norm) * PI / 180.0)
                val pointerEnd = Offset(
                    center.x + (radius - 7.dp.toPx()) * cos(angleRad).toFloat(),
                    center.y + (radius - 7.dp.toPx()) * sin(angleRad).toFloat()
                )
                drawLine(accentColor, center, pointerEnd, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1)
        Text(
            text = "${String.format("%.1f", value)}$unit",
            style = MaterialTheme.typography.labelSmall,
            color = TextPrimary,
            maxLines = 1
        )
    }
}

@Composable
fun SpectrumAndMeterBridge(
    meter: MeterSnapshot,
    modifier: Modifier = Modifier
) {
    Surface(
        color = ObsidianBg,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, ConsoleBorder, RoundedCornerShape(10.dp))
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("LUFS: ${String.format("%.1f", meter.integratedLufs)}", style = MaterialTheme.typography.labelSmall, color = CyanSignal)
                Text("TP: ${String.format("%.1f", meter.truePeakDbTp)} dBTP", style = MaterialTheme.typography.labelSmall, color = if (meter.truePeakDbTp > -0.3f) CrimsonClip else AmberWarm)
                Text("CORR: ${String.format("%.2f", meter.correlation)}", style = MaterialTheme.typography.labelSmall, color = EmeraldMeter)
            }
            Spacer(Modifier.height(4.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                val bands = meter.spectrumBands
                val count = bands.size.coerceAtLeast(1)
                val barW = size.width / count
                for (i in 0 until count) {
                    val v = bands[i].coerceIn(0f, 1f)
                    val h = (v * size.height).coerceAtLeast(2f)
                    val brush = Brush.verticalGradient(listOf(CrimsonClip, AmberWarm, CyanSignal))
                    drawRoundRect(
                        brush = brush,
                        topLeft = Offset(i * barW + 1.5f, size.height - h),
                        size = Size((barW - 3f).coerceAtLeast(2f), h),
                        cornerRadius = CornerRadius(3f, 3f)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            StereoMeterRow("L", meter.peakLeftDb, meter.rmsLeftDb)
            Spacer(Modifier.height(2.dp))
            StereoMeterRow("R", meter.peakRightDb, meter.rmsRightDb)
        }
    }
}

@Composable
fun StereoMeterRow(channel: String, peakDb: Float, rmsDb: Float) {
    val peakNorm = ((peakDb + 60f) / 60f).coerceIn(0f, 1f)
    val rmsNorm = ((rmsDb + 60f) / 60f).coerceIn(0f, 1f)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(channel, style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = Modifier.width(18.dp))
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(PanelSurface)
        ) {
            drawRect(color = CyanSignal.copy(alpha = 0.35f), size = Size(size.width * peakNorm, size.height))
            drawRect(brush = Brush.horizontalGradient(listOf(EmeraldMeter, YellowMeter, CrimsonClip)), size = Size(size.width * rmsNorm, size.height))
        }
        Spacer(Modifier.width(6.dp))
        Text("${peakDb.toInt()}dB", style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.width(38.dp))
    }
}

@Composable
fun WaveformCanvas(
    waveform: List<Float>,
    accentColor: Color = CyanSignal,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (waveform.isEmpty()) return@Canvas
        val midY = size.height / 2f
        val step = size.width / waveform.size.coerceAtLeast(1)
        waveform.forEachIndexed { idx, amp ->
            val x = idx * step + step * 0.5f
            val h = (amp.coerceIn(-1f, 1f) * midY * 0.9f)
            drawLine(
                color = accentColor.copy(alpha = 0.85f),
                start = Offset(x, midY - kotlin.math.abs(h).coerceAtLeast(2f)),
                end = Offset(x, midY + kotlin.math.abs(h).coerceAtLeast(2f)),
                strokeWidth = (step * 0.55f).coerceIn(1.5f, 5f),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun ResizableStudioSplitPane(
    firstPane: @Composable () -> Unit,
    secondPane: @Composable () -> Unit,
    initialRatio: Float = 0.62f,
    minWidthForSplit: Dp = 440.dp,
    preferSecondWhenCollapsed: Boolean = false
) {
    var splitRatio by remember { mutableFloatStateOf(initialRatio) }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (maxWidth < minWidthForSplit) {
            if (preferSecondWhenCollapsed) secondPane() else firstPane()
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                val safeRatio = splitRatio.coerceIn(0.18f, 0.82f)
                Box(modifier = Modifier.weight(safeRatio).fillMaxHeight()) {
                    firstPane()
                }
                Box(
                    modifier = Modifier
                        .width(8.dp)
                        .fillMaxHeight()
                        .background(ConsoleSurface)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                splitRatio = (splitRatio + dragAmount.x / 900f).coerceIn(0.18f, 0.82f)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(38.dp)
                            .clip(CircleShape)
                            .background(CyanSignal.copy(alpha = 0.65f))
                    )
                }
                Box(modifier = Modifier.weight((1f - safeRatio).coerceAtLeast(0.18f)).fillMaxHeight()) {
                    secondPane()
                }
            }
        }
    }
}

@Composable
fun VerticalResizableSplitPane(
    topPane: @Composable () -> Unit,
    bottomPane: @Composable () -> Unit,
    initialTopFraction: Float = 0.58f
) {
    var topFraction by remember { mutableFloatStateOf(initialTopFraction) }
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(topFraction.coerceIn(0.28f, 0.78f)).fillMaxWidth()) {
            topPane()
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(9.dp)
                .background(ConsoleSurface)
                .border(0.5.dp, ConsoleBorder)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        topFraction = (topFraction + dragAmount.y / 650f).coerceIn(0.28f, 0.78f)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(CyanSignal.copy(alpha = 0.7f))
            )
        }
        Box(modifier = Modifier.weight((1f - topFraction).coerceIn(0.22f, 0.72f)).fillMaxWidth()) {
            bottomPane()
        }
    }
}

@Composable
fun AiAssistantDialog(
    aiState: UiState<AiMusicSuggestion>?,
    onDismiss: () -> Unit,
    onAsk: (String) -> Unit,
    onApplyChords: (List<ChordEvent>) -> Unit,
    onApplyMelody: (List<MidiNote>) -> Unit,
    onApplyRhythm: (List<Int>) -> Unit
) {
    var promptText by remember { mutableStateOf("Suggest an editable Sambalpuri fusion chord progression, melody hook, and mix polish") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PanelSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = VioletMidi)
                Spacer(Modifier.width(8.dp))
                Text("AI Music Assistant", color = TextPrimary)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Provides non-destructive, editable chord, melody, rhythm, mix & mastering suggestions powered by Gemini.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                OutlinedTextField(
                    value = promptText,
                    onValueChange = { promptText = it },
                    label = { Text("Ask AI Assistant") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AssistChip(onClick = { onAsk("Suggest 4 chords and a bansuri melody in the project key") }, label = { Text("Chords & Melody") })
                    AssistChip(onClick = { onAsk("Analyze vocal take key, BPM, noise floor, and stem separation tips") }, label = { Text("Analyze Audio") })
                    AssistChip(onClick = { onAsk("Suggest an Indian folk 16-step rhythm groove and drum bus mix") }, label = { Text("Rhythm & Mix") })
                    AssistChip(onClick = { onAsk("Give mastering target suggestions for streaming and club release") }, label = { Text("Mastering") })
                }

                when (aiState) {
                    is UiState.Loading -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = CyanSignal)
                            Spacer(Modifier.width(10.dp))
                            Text("Analyzing session with Gemini...", color = TextSecondary)
                        }
                    }
                    is UiState.Error -> {
                        Text("AI Error: ${aiState.message}", color = CrimsonClip, style = MaterialTheme.typography.bodyMedium)
                    }
                    is UiState.Success -> {
                        val s = aiState.data
                        Surface(
                            color = ConsoleSurface,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().border(1.dp, ConsoleBorder, RoundedCornerShape(10.dp))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(s.title, style = MaterialTheme.typography.titleMedium, color = CyanSignal)
                                Text(s.summary, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                if (s.suggestedChords.isNotEmpty()) {
                                    Text("Chords: ${s.suggestedChords.joinToString(" -> ") { it.symbol }}", color = AmberWarm, style = MaterialTheme.typography.labelMedium)
                                    Button(onClick = { onApplyChords(s.suggestedChords) }) { Text("Apply Editable Chords") }
                                }
                                if (s.suggestedMelody.isNotEmpty()) {
                                    Text("Melody: ${s.suggestedMelody.size} MIDI notes", color = VioletMidi, style = MaterialTheme.typography.labelMedium)
                                    OutlinedButton(onClick = { onApplyMelody(s.suggestedMelody) }) { Text("Insert Editable Melody") }
                                }
                                if (s.suggestedRhythmSteps.isNotEmpty()) {
                                    OutlinedButton(onClick = { onApplyRhythm(s.suggestedRhythmSteps) }) { Text("Load Groove into Sequencer") }
                                }
                                s.mixingTips.forEach { tip ->
                                    Text("• Mix: $tip", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                }
                                s.masteringTips.forEach { tip ->
                                    Text("• Master: $tip", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                }
                                if (s.separationPlan.isNotBlank()) {
                                    Text("Stem Separation Plan: ${s.separationPlan}", style = MaterialTheme.typography.bodyMedium, color = TealSampler)
                                }
                                if (s.noiseAdvice.isNotBlank()) {
                                    Text("Noise Analysis: ${s.noiseAdvice}", style = MaterialTheme.typography.bodyMedium, color = CoralVocal)
                                }
                            }
                        }
                    }
                    null -> {}
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAsk(promptText) },
                colors = ButtonDefaults.buttonColors(containerColor = CyanSignal, contentColor = ObsidianBg)
            ) {
                Text("Generate Suggestion")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun StudioSettingsDialog(
    hardwareStatus: StudioHardwareStatus,
    settings: StudioSettings,
    onSelectBuffer: (Int) -> Unit,
    onToggle64Bit: (Boolean) -> Unit,
    onRefreshHardware: () -> Unit,
    onUpdateShortcut: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PanelSurface,
        title = { Text("Audio Engine, Hardware & Shortcuts", color = TextPrimary) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Buffer Size (Samples)", style = MaterialTheme.typography.titleMedium, color = CyanSignal)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                ) {
                    listOf(64, 128, 256, 512, 1024).forEach { buf ->
                        FilterChip(
                            selected = settings.bufferSizeFrames == buf,
                            onClick = { onSelectBuffer(buf) },
                            label = { Text("$buf") }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("64-Bit Precision Summing", color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
                        Text("Internal 32-bit float tracks + 64-bit master summing", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(checked = settings.precision64BitSumming, onCheckedChange = onToggle64Bit)
                }

                HorizontalDivider(color = ConsoleBorder)
                Text("Hardware & MIDI Devices", style = MaterialTheme.typography.titleMedium, color = AmberWarm)
                Text("Output: ${hardwareStatus.outputDeviceName} (${hardwareStatus.sampleRate} Hz)", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Text("Input: ${hardwareStatus.inputDeviceName}", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Text("USB Audio Interface: ${if (hardwareStatus.usbAudioConnected) "Connected" else "Standby"}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Text("External MIDI: ${if (hardwareStatus.midiDevices.isEmpty()) "None detected" else hardwareStatus.midiDevices.joinToString()}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Text("XRuns: ${hardwareStatus.xrunCount} | Recoveries: ${hardwareStatus.recoveryCount}", style = MaterialTheme.typography.labelSmall, color = EmeraldMeter)
                OutlinedButton(onClick = onRefreshHardware) { Text("Rescan Audio/MIDI Hardware") }

                HorizontalDivider(color = ConsoleBorder)
                Text("Customizable Shortcuts", style = MaterialTheme.typography.titleMedium, color = VioletMidi)
                settings.shortcutsMap.forEach { (action, key) ->
                    var editKey by remember(key) { mutableStateOf(key) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(action, color = TextPrimary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        OutlinedTextField(
                            value = editKey,
                            onValueChange = {
                                editKey = it
                                onUpdateShortcut(action, it)
                            },
                            singleLine = true,
                            modifier = Modifier.width(105.dp)
                        )
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } }
    )
}
