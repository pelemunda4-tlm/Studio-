package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.*
import com.example.ui.theme.*

@Composable
fun DrumMachineScreen(
    activePattern: RhythmPattern,
    presetPatterns: List<RhythmPattern>,
    onSelectPattern: (RhythmPattern) -> Unit,
    onToggleStep: (IndianInstrument, Int) -> Unit,
    onTriggerInstrument: (IndianInstrument) -> Unit,
    onAddInstrumentLane: (IndianInstrument) -> Unit,
    onChangeTimeSignature: (TimeSignature) -> Unit,
    onSaveAndSharePattern: (String, Boolean) -> Unit
) {
    var showAddInstrumentMenu by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var patternNameInput by remember(activePattern.name) { mutableStateOf(activePattern.name) }
    var sharePublicly by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Indian Music Production Header Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(124.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, ConsoleBorder, RoundedCornerShape(14.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_indian_studio_banner),
                    contentDescription = "Indian Music Production Studio",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.horizontalGradient(listOf(ObsidianBg.copy(alpha = 0.9f), Color.Transparent)))
                )
                Column(modifier = Modifier.align(Alignment.CenterStart).padding(14.dp)) {
                    Text("INDIAN MUSIC & RHYTHM WORKSTATION", style = MaterialTheme.typography.labelSmall, color = SaffronIndian)
                    Text(activePattern.name, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                    Text("${activePattern.style.displayName} • ${activePattern.timeSignature.label} • ${activePattern.bpm.toInt()} BPM", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            }
        }

        // Rhythm Presets & Time Signatures
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                presetPatterns.forEach { preset ->
                    FilterChip(
                        selected = preset.id == activePattern.id,
                        onClick = { onSelectPattern(preset) },
                        label = { Text(preset.style.displayName) }
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Time Sig:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                TimeSignature.PRESETS.forEach { ts ->
                    FilterChip(
                        selected = activePattern.timeSignature.label == ts.label,
                        onClick = { onChangeTimeSignature(ts) },
                        label = { Text(ts.label) }
                    )
                }
                FilterChip(
                    selected = activePattern.timeSignature.label == "5/8",
                    onClick = { onChangeTimeSignature(TimeSignature(5, 8, "5/8")) },
                    label = { Text("5/8 Custom") }
                )

                Box {
                    OutlinedButton(onClick = { showAddInstrumentMenu = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Instrument")
                    }
                    DropdownMenu(expanded = showAddInstrumentMenu, onDismissRequest = { showAddInstrumentMenu = false }) {
                        IndianInstrument.entries.forEach { inst ->
                            DropdownMenuItem(
                                text = { Text("${inst.displayName} (${inst.category})") },
                                onClick = {
                                    onAddInstrumentLane(inst)
                                    showAddInstrumentMenu = false
                                }
                            )
                        }
                    }
                }

                Button(
                    onClick = { showSaveDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = SaffronIndian, contentColor = ObsidianBg),
                    modifier = Modifier.testTag("save_share_pattern_btn")
                ) {
                    Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save/Share")
                }
            }
        }

        // Step Sequencer Matrix
        items(activePattern.lanes, key = { it.instrument.id }) { lane ->
            Card(
                colors = CardDefaults.cardColors(containerColor = PanelSurface),
                modifier = Modifier.fillMaxWidth().border(1.dp, ConsoleBorder, RoundedCornerShape(10.dp))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onTriggerInstrument(lane.instrument) }
                        ) {
                            Icon(Icons.Rounded.MusicNote, contentDescription = "Audition ${lane.instrument.displayName}", tint = SaffronIndian, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(lane.instrument.displayName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Spacer(Modifier.width(6.dp))
                            Text("(Tap to Hit)", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        }
                        Text(lane.instrument.category, style = MaterialTheme.typography.labelSmall, color = CyanSignal)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        lane.steps.forEachIndexed { idx, vel ->
                            val active = vel > 0.05f
                            val isBeatHead = idx % 4 == 0
                            Box(
                                modifier = Modifier
                                    .size(width = 34.dp, height = 38.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        when {
                                            active -> SaffronIndian
                                            isBeatHead -> ElevatedRack
                                            else -> ObsidianBg
                                        }
                                    )
                                    .border(
                                        1.dp,
                                        if (active) AmberWarm else ConsoleBorder,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable { onToggleStep(lane.instrument, idx) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${idx + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (active) ObsidianBg else TextMuted
                                )
                            }
                        }
                    }
                }
            }
        }

        // All 13 Indian Instruments Live Performance Pads
        item {
            Text("All 13 Indian Virtual Instruments (Live Pads)", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IndianInstrument.entries.forEach { inst ->
                    Surface(
                        color = ElevatedRack,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .width(122.dp)
                            .border(1.dp, SaffronIndian.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .clickable { onTriggerInstrument(inst) }
                            .testTag("indian_pad_${inst.id}")
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(inst.displayName, style = MaterialTheme.typography.labelLarge, color = TextPrimary, maxLines = 1)
                            Text(inst.category, style = MaterialTheme.typography.labelSmall, color = AmberWarm, maxLines = 1)
                        }
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            containerColor = PanelSurface,
            title = { Text("Save & Share Custom Rhythm", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = patternNameInput,
                        onValueChange = { patternNameInput = it },
                        label = { Text("Pattern Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = sharePublicly, onCheckedChange = { sharePublicly = it })
                        Text("Share publicly with Trueworld Producers", color = TextPrimary)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onSaveAndSharePattern(patternNameInput, sharePublicly)
                    showSaveDialog = false
                }) { Text("Save Pattern") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
            }
        )
    }
}
