package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.SamplerPad
import com.example.ui.components.RotaryStudioKnob
import com.example.ui.components.WaveformCanvas
import com.example.ui.theme.*

@Composable
fun SamplerScreen(
    pads: List<SamplerPad>,
    selectedPadId: String,
    onSelectPad: (String) -> Unit,
    onTriggerPad: (SamplerPad, Int) -> Unit,
    onUpdatePad: (SamplerPad) -> Unit,
    onImportWavOrAiff: () -> Unit
) {
    val activePad = pads.firstOrNull { it.id == selectedPadId } ?: pads.first()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("PRO SAMPLER & DRUM KIT BUILDER", style = MaterialTheme.typography.labelSmall, color = TealSampler)
                Text("${activePad.name} (${activePad.formatLabel})", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onImportWavOrAiff,
                    modifier = Modifier.testTag("sampler_import_btn")
                ) {
                    Icon(Icons.Rounded.AudioFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Import WAV/AIFF")
                }
                Button(
                    onClick = { onTriggerPad(activePad, activePad.rootMidiNote) },
                    colors = ButtonDefaults.buttonColors(containerColor = TealSampler, contentColor = ObsidianBg),
                    modifier = Modifier.testTag("sampler_preview_btn")
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Preview")
                }
            }
        }

        // Pad Grid (8 Drum / Multisample Pads)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            pads.forEach { pad ->
                val selected = pad.id == activePad.id
                Surface(
                    color = if (selected) ElevatedRack else PanelSurface,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .width(120.dp)
                        .border(if (selected) 1.5.dp else 1.dp, if (selected) TealSampler else ConsoleBorder, RoundedCornerShape(10.dp))
                        .clickable {
                            onSelectPad(pad.id)
                            onTriggerPad(pad, pad.rootMidiNote)
                        }
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(pad.name, style = MaterialTheme.typography.labelLarge, color = TextPrimary, maxLines = 1)
                        Text("${pad.slices.size} slices • ${pad.formatLabel}", style = MaterialTheme.typography.labelSmall, color = TealSampler)
                    }
                }
            }
        }

        // Waveform Display + Slice Markers
        Card(
            colors = CardDefaults.cardColors(containerColor = ConsoleSurface),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ConsoleBorder, RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                WaveformCanvas(
                    waveform = activePad.waveformPreview,
                    accentColor = TealSampler,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = activePad.reverse,
                        onClick = { onUpdatePad(activePad.copy(reverse = !activePad.reverse)) },
                        label = { Text("Reverse") }
                    )
                    FilterChip(
                        selected = activePad.loopEnabled,
                        onClick = { onUpdatePad(activePad.copy(loopEnabled = !activePad.loopEnabled)) },
                        label = { Text("Loop Points") }
                    )
                    activePad.slices.forEach { slice ->
                        AssistChip(
                            onClick = { onTriggerPad(activePad, activePad.rootMidiNote + slice.index) },
                            label = { Text(slice.label) }
                        )
                    }
                }
            }
        }

        // Pitch Shift, Time Stretch & ADSR Envelope
        Card(
            colors = CardDefaults.cardColors(containerColor = PanelSurface),
            modifier = Modifier.fillMaxWidth().border(1.dp, ConsoleBorder, RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Pitch, Time Stretch & ADSR Envelope", style = MaterialTheme.typography.titleMedium, color = CyanSignal)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RotaryStudioKnob("Pitch", activePad.pitchSemitones, -12f, 12f, "st", TealSampler, {
                        onUpdatePad(activePad.copy(pitchSemitones = it))
                    })
                    RotaryStudioKnob("Stretch", activePad.timeStretchRatio, 0.5f, 2.0f, "x", CyanSignal, {
                        onUpdatePad(activePad.copy(timeStretchRatio = it))
                    })
                    RotaryStudioKnob("Attack", activePad.attackMs, 1f, 500f, "ms", AmberWarm, {
                        onUpdatePad(activePad.copy(attackMs = it))
                    })
                    RotaryStudioKnob("Decay", activePad.decayMs, 10f, 1200f, "ms", AmberWarm, {
                        onUpdatePad(activePad.copy(decayMs = it))
                    })
                    RotaryStudioKnob("Sustain", activePad.sustainLevel, 0f, 1f, "", EmeraldMeter, {
                        onUpdatePad(activePad.copy(sustainLevel = it))
                    })
                    RotaryStudioKnob("Release", activePad.releaseMs, 10f, 1500f, "ms", VioletMidi, {
                        onUpdatePad(activePad.copy(releaseMs = it))
                    })
                }
            }
        }

        // Multisample Keyboard & Velocity Mapping
        Card(
            colors = CardDefaults.cardColors(containerColor = PanelSurface),
            modifier = Modifier.fillMaxWidth().border(1.dp, ConsoleBorder, RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Multisample Keyboard & Velocity Mapping", style = MaterialTheme.typography.titleMedium, color = VioletMidi)
                Text(
                    "Root MIDI: ${activePad.rootMidiNote} | Key Zone: ${activePad.keyLow}..${activePad.keyHigh} | Vel Layer: ${activePad.velLow}..${activePad.velHigh}",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(-5, 0, 4, 7, 12).forEach { offset ->
                        OutlinedButton(onClick = { onTriggerPad(activePad, activePad.rootMidiNote + offset) }) {
                            Text("Key ${if (offset >= 0) "+$offset" else "$offset"}")
                        }
                    }
                }
            }
        }
    }
}
