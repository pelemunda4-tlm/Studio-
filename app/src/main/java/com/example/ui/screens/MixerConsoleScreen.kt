package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.*
import com.example.ui.components.RotaryStudioKnob
import com.example.ui.components.SpectrumAndMeterBridge
import com.example.ui.theme.*
import java.util.UUID

@Composable
fun MixerConsoleScreen(
    project: DawProject,
    selectedTrackId: String,
    trackMeters: Map<String, Float>,
    masterMeters: MeterSnapshot,
    onSelectTrack: (String) -> Unit,
    onAddTrack: (TrackType) -> Unit,
    onUpdateTrack: (String, String, (DawTrack) -> DawTrack) -> Unit,
    onFreezeTrack: (String) -> Unit,
    onOpenPluginRack: (String) -> Unit,
    onToggleQuickPlugin: (String, PluginType) -> Unit = { _, _ -> },
    onUpdateSend: (String, String, String, Float) -> Unit = { _, _, _, _ -> },
    compactMode: Boolean = false
) {
    val busTracks = remember(project.tracks) { project.tracks.filter { it.type == TrackType.BUS } }
    val vcaTracks = remember(project.tracks) { project.tracks.filter { it.type == TrackType.VCA } }
    var showMeterBridge by remember { mutableStateOf(!compactMode) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Horizontal Console Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "HORIZONTAL MIXER",
                style = MaterialTheme.typography.titleSmall,
                color = AmberWarm,
                fontWeight = FontWeight.Bold
            )
            OutlinedButton(
                onClick = { onAddTrack(TrackType.BUS) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.height(30.dp).testTag("mixer_add_bus_btn")
            ) { Text("+ Bus", style = MaterialTheme.typography.labelSmall) }
            OutlinedButton(
                onClick = { onAddTrack(TrackType.VCA) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.height(30.dp).testTag("mixer_add_vca_btn")
            ) { Text("+ VCA", style = MaterialTheme.typography.labelSmall) }
            OutlinedButton(
                onClick = { onAddTrack(TrackType.FOLDER) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.height(30.dp)
            ) { Text("+ Folder", style = MaterialTheme.typography.labelSmall) }
            FilterChip(
                selected = showMeterBridge,
                onClick = { showMeterBridge = !showMeterBridge },
                label = { Text("Meters", style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(30.dp)
            )
            AssistChip(
                onClick = {
                    onUpdateTrack(selectedTrackId, "Write Volume Automation") { tr ->
                        val lane = AutomationLane(
                            id = "auto_${UUID.randomUUID().toString().take(6)}",
                            targetParam = "volume",
                            points = listOf(
                                AutomationPoint(0.0, 0.65f),
                                AutomationPoint(4.0, 0.90f),
                                AutomationPoint(8.0, 0.72f),
                                AutomationPoint(12.0, 0.95f)
                            )
                        )
                        tr.copy(automationLanes = tr.automationLanes.filterNot { it.targetParam == "volume" } + lane)
                    }
                },
                label = { Text("Auto Sweep", style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(30.dp)
            )
        }

        if (showMeterBridge) {
            SpectrumAndMeterBridge(meter = masterMeters)
        }

        // Side-by-Side Horizontal Channel Strips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            project.tracks.forEach { track ->
                val isSelected = track.id == selectedTrackId
                val outDb = if (track.type == TrackType.MASTER) {
                    maxOf(masterMeters.peakLeftDb, masterMeters.peakRightDb)
                } else {
                    trackMeters[track.id] ?: -60f
                }
                val inDb = (outDb - (1f - track.volume) * 8f).coerceIn(-60f, 0f)

                HorizontalMixerStripCard(
                    track = track,
                    isSelected = isSelected,
                    inputDb = inDb,
                    outputDb = outDb,
                    allTracks = project.tracks,
                    busTracks = busTracks,
                    vcaTracks = vcaTracks,
                    onSelect = { onSelectTrack(track.id) },
                    onUpdateTrack = { label, transform -> onUpdateTrack(track.id, label, transform) },
                    onToggleQuickPlugin = { type -> onToggleQuickPlugin(track.id, type) },
                    onUpdateSend = { busId, busName, lvl -> onUpdateSend(track.id, busId, busName, lvl) },
                    onFreeze = { onFreezeTrack(track.id) },
                    onOpenPlugins = {
                        onSelectTrack(track.id)
                        onOpenPluginRack(track.id)
                    }
                )
            }
        }
    }
}

@Composable
fun HorizontalMixerStripCard(
    track: DawTrack,
    isSelected: Boolean,
    inputDb: Float,
    outputDb: Float,
    allTracks: List<DawTrack>,
    busTracks: List<DawTrack>,
    vcaTracks: List<DawTrack>,
    onSelect: () -> Unit,
    onUpdateTrack: (String, (DawTrack) -> DawTrack) -> Unit,
    onToggleQuickPlugin: (PluginType) -> Unit,
    onUpdateSend: (String, String, Float) -> Unit,
    onFreeze: () -> Unit,
    onOpenPlugins: () -> Unit
) {
    val trackColor = Color(track.colorArgb)
    var showSidechainMenu by remember { mutableStateOf(false) }

    val eqActive = track.plugins.any {
        !it.bypassed && it.type.category == PluginCategory.EQUALIZERS
    }
    val compActive = track.plugins.any {
        !it.bypassed && (it.type == PluginType.COMPRESSOR || it.type == PluginType.MULTIBAND_COMPRESSOR)
    }
    val reverbSend = track.sends.firstOrNull { it.targetBusId == "bus_reverb" }?.level ?: 0f
    val delaySend = track.sends.firstOrNull { it.targetBusId == "bus_delay" }?.level ?: 0f

    Surface(
        color = if (isSelected) ElevatedRack else ConsoleSurface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .width(152.dp)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) trackColor else ConsoleBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onSelect() }
            .testTag("mixer_strip_${track.id}")
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(trackColor)
            )
            Text(
                text = track.name,
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold
            )

            // Quick EQ & Compressor Toggles + FX Count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                StripQuickButton(
                    label = "EQ",
                    active = eqActive,
                    activeColor = CyanSignal,
                    onClick = { onToggleQuickPlugin(PluginType.EQ_3_BAND) },
                    modifier = Modifier.weight(1f)
                )
                StripQuickButton(
                    label = "COMP",
                    active = compActive,
                    activeColor = AmberWarm,
                    onClick = { onToggleQuickPlugin(PluginType.COMPRESSOR) },
                    modifier = Modifier.weight(1f)
                )
                StripQuickButton(
                    label = "FX${track.plugins.size}",
                    active = track.plugins.isNotEmpty(),
                    activeColor = VioletMidi,
                    onClick = onOpenPlugins,
                    modifier = Modifier.weight(1f)
                )
            }

            // Reverb & Delay Sends
            if (track.type != TrackType.MASTER) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Reverb", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text("${(reverbSend * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = CyanSignal)
                    }
                    Slider(
                        value = reverbSend,
                        onValueChange = { onUpdateSend("bus_reverb", "Reverb Bus", it) },
                        valueRange = 0f..1f,
                        modifier = Modifier.height(20.dp)
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Delay", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text("${(delaySend * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = VioletMidi)
                    }
                    Slider(
                        value = delaySend,
                        onValueChange = { onUpdateSend("bus_delay", "Delay Bus", it) },
                        valueRange = 0f..1f,
                        modifier = Modifier.height(20.dp)
                    )
                }

                // Sidechain & Routing Pill
                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        color = PanelSurface,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showSidechainMenu = true }
                    ) {
                        Text(
                            text = track.sidechainSourceId?.let { scId ->
                                "SC: ${allTracks.firstOrNull { it.id == scId }?.name?.take(8) ?: "On"}"
                            } ?: if (track.busTargetId != null && track.busTargetId != "bus_master") {
                                "Bus: ${busTracks.firstOrNull { it.id == track.busTargetId }?.name?.take(7) ?: "Bus"}"
                            } else if (track.vcaGroupId != null) {
                                "VCA: ${vcaTracks.firstOrNull { it.id == track.vcaGroupId }?.name?.take(7) ?: "VCA"}"
                            } else "SC: None • ${if (track.mono) "MONO" else "ST"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                    DropdownMenu(expanded = showSidechainMenu, onDismissRequest = { showSidechainMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(if (track.mono) "Switch to Stereo" else "Switch to Mono") },
                            onClick = {
                                showSidechainMenu = false
                                onUpdateTrack("Mono/Stereo") { it.copy(mono = !it.mono) }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Clear Sidechain") },
                            onClick = {
                                showSidechainMenu = false
                                onUpdateTrack("Clear Sidechain") { it.copy(sidechainSourceId = null) }
                            }
                        )
                        allTracks.filter { it.id != track.id && it.type != TrackType.MASTER }.forEach { src ->
                            DropdownMenuItem(
                                text = { Text("Sidechain <- ${src.name}") },
                                onClick = {
                                    showSidechainMenu = false
                                    onUpdateTrack("Sidechain ${src.name}") { it.copy(sidechainSourceId = src.id) }
                                }
                            )
                        }
                    }
                }
            }

            // Pan Knob
            RotaryStudioKnob(
                label = "Pan",
                value = track.pan,
                min = -1f,
                max = 1f,
                accentColor = trackColor,
                knobSize = 40.dp,
                onValueChange = { p -> onUpdateTrack("Pan") { it.copy(pan = p) } }
            )

            // Input Meter + Vertical Volume Fader + Output Meter
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                VerticalChannelMeter("IN", inputDb, CyanSignal)

                // Interactive Vertical Fader Track
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(64.dp).fillMaxHeight()
                ) {
                    Text("${(track.volume * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .width(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ObsidianBg)
                            .border(1.dp, ConsoleBorder, RoundedCornerShape(8.dp))
                            .pointerInput(track.id) {
                                detectVerticalDragGestures { change, dragAmount ->
                                    change.consume()
                                    val nextVol = (track.volume - dragAmount / 130f).coerceIn(0f, 1.2f)
                                    onUpdateTrack("Volume") { it.copy(volume = nextVol) }
                                }
                            },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        val norm = (track.volume / 1.2f).coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(norm)
                                .background(trackColor.copy(alpha = 0.45f))
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = (norm * 58f).dp)
                                .fillMaxWidth()
                                .height(10.dp)
                                .background(trackColor, RoundedCornerShape(4.dp))
                        )
                    }
                    Text("${outputDb.toInt()}dB", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }

                VerticalChannelMeter("OUT", outputDb, EmeraldMeter)
            }

            // Mute / Solo / Arm / Freeze
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                StripQuickButton("M", track.muted, CrimsonClip, {
                    onUpdateTrack("Mute") { it.copy(muted = !it.muted) }
                }, Modifier.weight(1f))
                StripQuickButton("S", track.soloed, AmberWarm, {
                    onUpdateTrack("Solo") { it.copy(soloed = !it.soloed) }
                }, Modifier.weight(1f))
                if (track.type != TrackType.MASTER) {
                    StripQuickButton("R", track.armed, CrimsonClip, {
                        onUpdateTrack("Arm") { it.copy(armed = !it.armed) }
                    }, Modifier.weight(1f))
                    StripQuickButton("F", track.frozen, CyanSignal, onFreeze, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun VerticalChannelMeter(label: String, db: Float, tint: Color) {
    val norm = ((db + 60f) / 60f).coerceIn(0f, 1f)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxHeight()) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Canvas(
            modifier = Modifier
                .weight(1f)
                .width(9.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(ObsidianBg)
        ) {
            val fillH = size.height * norm
            drawRect(
                brush = Brush.verticalGradient(listOf(CrimsonClip, YellowMeter, tint)),
                topLeft = Offset(0f, size.height - fillH),
                size = Size(size.width, fillH)
            )
        }
    }
}

@Composable
private fun StripQuickButton(
    label: String,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (active) activeColor else PanelSurface,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
            .height(28.dp)
            .border(1.dp, if (active) activeColor else ConsoleBorder, RoundedCornerShape(6.dp))
            .clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) ObsidianBg else TextSecondary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
