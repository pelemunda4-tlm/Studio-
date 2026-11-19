package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.audio.MasteringProcessor
import com.example.model.MasteringConfig
import com.example.model.MeterSnapshot
import com.example.ui.components.RotaryStudioKnob
import com.example.ui.components.SpectrumAndMeterBridge
import com.example.ui.theme.*

@Composable
fun MasteringStudioScreen(
    config: MasteringConfig,
    masterMeters: MeterSnapshot,
    onSelectPreset: (MasteringConfig) -> Unit,
    onUpdateConfig: (MasteringConfig) -> Unit,
    onImportReference: () -> Unit,
    onBounceWav: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header + A/B Reference + Bypass + Bounce
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("MASTERING STUDIO SUITE", style = MaterialTheme.typography.labelSmall, color = AmberWarm)
                Text(config.presetName, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            }
            Button(
                onClick = onBounceWav,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldMeter, contentColor = ObsidianBg),
                modifier = Modifier.testTag("mastering_bounce_btn")
            ) {
                Icon(Icons.Rounded.AudioFile, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Bounce WAV")
            }
        }

        // Editable Mastering Presets
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MasteringProcessor.MASTERING_PRESETS.forEach { preset ->
                FilterChip(
                    selected = config.presetName == preset.presetName,
                    onClick = { onSelectPreset(preset) },
                    label = { Text(preset.presetName) }
                )
            }
        }

        // A/B Reference Monitoring & Bypass Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = config.bypassMastering,
                onClick = { onUpdateConfig(config.copy(bypassMastering = !config.bypassMastering)) },
                label = { Text(if (config.bypassMastering) "Chain Bypassed" else "Chain Active") }
            )
            FilterChip(
                selected = config.abMonitorReference,
                onClick = { onUpdateConfig(config.copy(abMonitorReference = !config.abMonitorReference)) },
                label = { Text(if (config.abMonitorReference) "Monitor: Reference" else "Monitor: Master") }
            )
            OutlinedButton(onClick = onImportReference) {
                Text("Load Ref Track")
            }
        }

        // Loudness Analysis & Spectrum Bridge
        SpectrumAndMeterBridge(meter = masterMeters)

        Surface(
            color = ConsoleSurface,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ConsoleBorder, RoundedCornerShape(10.dp))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Target: ${String.format("%.1f", config.targetLufs)} LUFS",
                    style = MaterialTheme.typography.labelLarge,
                    color = AmberWarm
                )
                Text(
                    text = "Integrated: ${String.format("%.1f", masterMeters.integratedLufs)} LUFS",
                    style = MaterialTheme.typography.labelLarge,
                    color = CyanSignal
                )
                Text(
                    text = "Ceiling: ${String.format("%.1f", config.truePeakCeilingDb)} dBTP",
                    style = MaterialTheme.typography.labelLarge,
                    color = EmeraldMeter
                )
            }
        }

        // Module 1: Linear Phase EQ & Dynamic EQ
        Surface(
            color = PanelSurface,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ConsoleBorder, RoundedCornerShape(14.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Linear Phase EQ & Dynamic EQ", style = MaterialTheme.typography.titleSmall, color = CyanSignal, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = config.linearPhaseEqEnabled,
                        onCheckedChange = { onUpdateConfig(config.copy(linearPhaseEqEnabled = it)) }
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RotaryStudioKnob("Low Shelf", config.lowShelfDb, -6f, 6f, "dB", CyanSignal, {
                        onUpdateConfig(config.copy(lowShelfDb = it))
                    })
                    RotaryStudioKnob("Presence", config.midPresenceDb, -6f, 6f, "dB", CyanSignal, {
                        onUpdateConfig(config.copy(midPresenceDb = it))
                    })
                    RotaryStudioKnob("Air Shelf", config.airShelfDb, -6f, 6f, "dB", CyanSignal, {
                        onUpdateConfig(config.copy(airShelfDb = it))
                    })
                    RotaryStudioKnob("Dyn EQ", config.dynamicEqAmount * 100f, 0f, 100f, "%", AmberWarm, {
                        onUpdateConfig(config.copy(dynamicEqAmount = it / 100f))
                    })
                }
            }
        }

        // Module 2: Multiband Comp, Stereo Imager, Harmonic Exciter, Tape Saturation
        Surface(
            color = PanelSurface,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ConsoleBorder, RoundedCornerShape(14.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Multiband Comp, Stereo Imager, Exciter & Tape", style = MaterialTheme.typography.titleSmall, color = AmberWarm, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RotaryStudioKnob("MB Comp", config.multibandDrive * 100f, 0f, 100f, "%", AmberWarm, {
                        onUpdateConfig(config.copy(multibandDrive = it / 100f))
                    })
                    RotaryStudioKnob("Width", config.stereoWidth, 0.5f, 1.8f, "x", VioletMidi, {
                        onUpdateConfig(config.copy(stereoWidth = it))
                    })
                    RotaryStudioKnob("Exciter", config.harmonicExciter * 100f, 0f, 100f, "%", CoralVocal, {
                        onUpdateConfig(config.copy(harmonicExciter = it / 100f))
                    })
                    RotaryStudioKnob("Tape Sat", config.tapeSaturation * 100f, 0f, 100f, "%", SaffronIndian, {
                        onUpdateConfig(config.copy(tapeSaturation = it / 100f))
                    })
                }
            }
        }

        // Module 3: Clipper & True Peak Limiter
        Surface(
            color = PanelSurface,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ConsoleBorder, RoundedCornerShape(14.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Soft Clipper & True Peak Limiter", style = MaterialTheme.typography.titleSmall, color = EmeraldMeter, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RotaryStudioKnob("Clipper", config.clipperDrive * 100f, 0f, 100f, "%", CrimsonClip, {
                        onUpdateConfig(config.copy(clipperDrive = it / 100f))
                    })
                    RotaryStudioKnob("TP Ceiling", config.truePeakCeilingDb, -3f, 0f, "dB", EmeraldMeter, {
                        onUpdateConfig(config.copy(truePeakCeilingDb = it))
                    })
                    RotaryStudioKnob("Target", config.targetLufs, -24f, -6f, "LUFS", CyanSignal, {
                        onUpdateConfig(config.copy(targetLufs = it))
                    })
                }
            }
        }
    }
}
