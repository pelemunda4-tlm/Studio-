package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.audio.AudioAnalysisReport
import com.example.model.*
import com.example.ui.components.RotaryStudioKnob
import com.example.ui.components.WaveformCanvas
import com.example.ui.theme.*

@Composable
fun VocalEditorScreen(
    project: DawProject,
    vocalChain: VocalChainType,
    vocalParams: VocalProcessingParams,
    vocalTakes: List<VocalTake>,
    isRecording: Boolean,
    audioReport: AudioAnalysisReport?,
    onToggleRecord: () -> Unit,
    onSelectChain: (VocalChainType) -> Unit,
    onUpdateParams: (VocalProcessingParams) -> Unit,
    onUpdatePitchOffset: (String, Int, Float) -> Unit,
    onAuditionTake: (VocalTake, Boolean?) -> Unit
) {
    var selectedTakeIdx by remember(vocalTakes.size) { mutableIntStateOf((vocalTakes.size - 1).coerceAtLeast(0)) }
    val activeTake = vocalTakes.getOrNull(selectedTakeIdx)
    var selectedPointIdx by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header + Record + Comping Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("ADVANCED VOCAL STUDIO", style = MaterialTheme.typography.labelSmall, color = CoralVocal)
                Text("${vocalChain.displayName} • ${project.musicalKey} ${project.scale.displayName}", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            }
            Button(
                onClick = onToggleRecord,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) CrimsonClip else CoralVocal,
                    contentColor = ObsidianBg
                ),
                modifier = Modifier.testTag("vocal_record_btn")
            ) {
                Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isRecording) "Stop Rec" else "Record Vocal")
            }
        }

        // Vocal Comping Take Selector + DSP Stem Audition
        if (vocalTakes.isNotEmpty() && activeTake != null) {
            Surface(
                color = PanelSurface,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ConsoleBorder, RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Takes (Comping):", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        vocalTakes.forEachIndexed { idx, take ->
                            FilterChip(
                                selected = idx == selectedTakeIdx,
                                onClick = { selectedTakeIdx = idx },
                                label = { Text("${take.name} (${String.format("%.1f", take.durationSec)}s)") }
                            )
                        }
                    }

                    WaveformCanvas(
                        waveform = activeTake.waveform,
                        accentColor = CoralVocal,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .background(ObsidianBg, RoundedCornerShape(8.dp))
                            .padding(4.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onAuditionTake(activeTake, null) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Play Take")
                        }
                        OutlinedButton(
                            onClick = { onAuditionTake(activeTake, true) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Vocal Isolate") }
                        OutlinedButton(
                            onClick = { onAuditionTake(activeTake, false) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Music Bed") }
                    }
                }
            }
        }

        // Audio Analysis Readout (BPM, Key, Noise Floor)
        if (audioReport != null) {
            Surface(
                color = ConsoleSurface,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ConsoleBorder, RoundedCornerShape(10.dp))
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Detected: ${audioReport.detectedKey} ${audioReport.detectedScale.displayName}", style = MaterialTheme.typography.labelSmall, color = CyanSignal)
                    Text("${audioReport.detectedBpm.toInt()} BPM", style = MaterialTheme.typography.labelSmall, color = AmberWarm)
                    Text("Noise Floor: ${audioReport.noiseFloorDb.toInt()} dB", style = MaterialTheme.typography.labelSmall, color = CoralVocal)
                }
            }
        }

        // Piano Roll Pitch Detection & Manual Pitch Correction Display
        if (activeTake != null && activeTake.pitchCurve.isNotEmpty()) {
            val safeIdx = selectedPointIdx.coerceIn(0, activeTake.pitchCurve.lastIndex)
            val currentPt = activeTake.pitchCurve[safeIdx]

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
                        Text("Piano Roll Pitch Detection & Note Correction", style = MaterialTheme.typography.titleMedium, color = CyanSignal)
                        Text(
                            text = "Note #${safeIdx + 1}: ${String.format("%.1f", currentPt.detectedMidi)} -> ${String.format("%.1f", currentPt.targetMidi + currentPt.manualOffsetSemitones)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = AmberWarm
                        )
                    }

                    // Pitch Piano Roll Canvas
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .background(ObsidianBg, RoundedCornerShape(8.dp))
                            .border(1.dp, ConsoleBorder, RoundedCornerShape(8.dp))
                    ) {
                        val pts = activeTake.pitchCurve
                        val minMidi = 48f
                        val maxMidi = 84f
                        val range = maxMidi - minMidi

                        // Semitone grid lines
                        for (m in 48..84 step 2) {
                            val y = size.height - ((m - minMidi) / range) * size.height
                            drawLine(ConsoleBorder.copy(alpha = 0.45f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                        }

                        val stepX = size.width / pts.size.coerceAtLeast(1)
                        val detectedPath = Path()
                        val correctedPath = Path()
                        pts.forEachIndexed { idx, pt ->
                            val x = idx * stepX + stepX * 0.5f
                            val yDet = (size.height - ((pt.detectedMidi - minMidi) / range) * size.height).coerceIn(4f, size.height - 4f)
                            val target = pt.targetMidi + pt.manualOffsetSemitones
                            val yCor = (size.height - ((target - minMidi) / range) * size.height).coerceIn(4f, size.height - 4f)
                            if (idx == 0) {
                                detectedPath.moveTo(x, yDet)
                                correctedPath.moveTo(x, yCor)
                            } else {
                                detectedPath.lineTo(x, yDet)
                                correctedPath.lineTo(x, yCor)
                            }
                            if (idx == safeIdx) {
                                drawCircle(AmberWarm, radius = 6.dp.toPx(), center = Offset(x, yCor))
                            }
                        }
                        drawPath(detectedPath, color = CoralVocal.copy(alpha = 0.7f), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                        drawPath(correctedPath, color = CyanSignal, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                    }

                    // Select segment & manual pitch edit slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Segment:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Slider(
                            value = safeIdx.toFloat(),
                            onValueChange = { selectedPointIdx = it.toInt().coerceIn(0, activeTake.pitchCurve.lastIndex) },
                            valueRange = 0f..activeTake.pitchCurve.lastIndex.toFloat().coerceAtLeast(1f),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Manual Pitch:", style = MaterialTheme.typography.labelSmall, color = CyanSignal)
                        Slider(
                            value = currentPt.manualOffsetSemitones,
                            onValueChange = { off -> onUpdatePitchOffset(activeTake.id, safeIdx, off) },
                            valueRange = -12f..12f,
                            modifier = Modifier.weight(1f)
                        )
                        Text("${String.format("%+.1f", currentPt.manualOffsetSemitones)} st", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                    }
                }
            }
        }

        // 7 Editable Professional Vocal Chains
        Text("Vocal Chains (Editable Presets)", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VocalChainType.entries.forEach { chain ->
                FilterChip(
                    selected = vocalChain == chain,
                    onClick = { onSelectChain(chain) },
                    label = { Text(chain.displayName) },
                    modifier = Modifier.testTag("vocal_chain_${chain.name}")
                )
            }
        }
        Text(vocalChain.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)

        // Vocal DSP Parameter Knobs (Pitch, Formant, Vibrato, Timing, Breath, De-Esser, Noise Reduction)
        Surface(
            color = PanelSurface,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ConsoleBorder, RoundedCornerShape(14.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Pitch, Formant, Vibrato & Timing", style = MaterialTheme.typography.titleSmall, color = CyanSignal, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    RotaryStudioKnob("Retune", vocalParams.retuneSpeed * 100f, 0f, 100f, "%", CoralVocal, {
                        onUpdateParams(vocalParams.copy(retuneSpeed = it / 100f))
                    })
                    RotaryStudioKnob("Formant", vocalParams.formantShiftSemitones, -6f, 6f, "st", CyanSignal, {
                        onUpdateParams(vocalParams.copy(formantShiftSemitones = it))
                    })
                    RotaryStudioKnob("Vibrato", vocalParams.vibratoDepth * 100f, 0f, 100f, "%", VioletMidi, {
                        onUpdateParams(vocalParams.copy(vibratoDepth = it / 100f))
                    })
                    RotaryStudioKnob("Timing", vocalParams.timingTightness * 100f, 0f, 100f, "%", AmberWarm, {
                        onUpdateParams(vocalParams.copy(timingTightness = it / 100f))
                    })
                }

                HorizontalDivider(color = ConsoleBorder)
                Text("Breath Control, De-Esser & Noise Reduction", style = MaterialTheme.typography.titleSmall, color = AmberWarm, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    RotaryStudioKnob("Breath", vocalParams.breathReduction * 100f, 0f, 100f, "%", TealSampler, {
                        onUpdateParams(vocalParams.copy(breathReduction = it / 100f))
                    })
                    RotaryStudioKnob("De-Esser", vocalParams.deEsserAmount * 100f, 0f, 100f, "%", CoralVocal, {
                        onUpdateParams(vocalParams.copy(deEsserAmount = it / 100f))
                    })
                    RotaryStudioKnob("Denoise", vocalParams.noiseReductionAmount * 100f, 0f, 100f, "%", EmeraldMeter, {
                        onUpdateParams(vocalParams.copy(noiseReductionAmount = it / 100f))
                    })
                    RotaryStudioKnob("Double", vocalParams.doubleVocalMix * 100f, 0f, 100f, "%", VioletMidi, {
                        onUpdateParams(vocalParams.copy(doubleVocalMix = it / 100f))
                    })
                }

                HorizontalDivider(color = ConsoleBorder)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Harmony Generation", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text("Interval: +${vocalParams.harmonyIntervalSemitones} semitones", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                    Switch(
                        checked = vocalParams.harmonyEnabled,
                        onCheckedChange = { onUpdateParams(vocalParams.copy(harmonyEnabled = it)) }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(3, 4, 5, 7, 12).forEach { semi ->
                        FilterChip(
                            selected = vocalParams.harmonyIntervalSemitones == semi,
                            onClick = { onUpdateParams(vocalParams.copy(harmonyIntervalSemitones = semi, harmonyEnabled = true)) },
                            label = { Text("+$semi st") }
                        )
                    }
                }
            }
        }
    }
}
