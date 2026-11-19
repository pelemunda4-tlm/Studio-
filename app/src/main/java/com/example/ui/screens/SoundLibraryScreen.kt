package com.example.ui.screens

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
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.CustomPatternEntity
import com.example.data.SoundAssetEntity
import com.example.model.*
import com.example.ui.theme.*
import com.example.viewmodel.UiState

@Composable
fun SoundLibraryScreen(
    project: DawProject,
    soundAssets: List<SoundAssetEntity>,
    localPatterns: List<CustomPatternEntity>,
    sharedPatternsState: UiState<List<CloudSharedPattern>>,
    onPreviewInstrument: (IndianInstrument) -> Unit,
    onLoadLocalPattern: (CustomPatternEntity) -> Unit,
    onLoadCloudPattern: (CloudSharedPattern) -> Unit,
    onImportAudioFile: () -> Unit,
    onRelinkClip: (String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val allClips = remember(project.tracks) {
        project.tracks.flatMap { tr -> tr.clips.map { tr.name to it } }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("TRUEWORLD SOUND & RHYTHM LIBRARY", style = MaterialTheme.typography.labelSmall, color = SaffronIndian)
                Text("Instruments, Community Grooves & Assets", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            }
            OutlinedButton(
                onClick = onImportAudioFile,
                modifier = Modifier.testTag("library_import_btn")
            ) {
                Icon(Icons.Rounded.AudioFile, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Import WAV/AIFF")
            }
        }

        // Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = selectedTab == 0, onClick = { selectedTab = 0 }, label = { Text("Indian Instruments (13)") })
            FilterChip(selected = selectedTab == 1, onClick = { selectedTab = 1 }, label = { Text("Shared Rhythm Patterns") })
            FilterChip(selected = selectedTab == 2, onClick = { selectedTab = 2 }, label = { Text("Audio Assets & Relink") })
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    items(IndianInstrument.entries, key = { it.id }) { inst ->
                        Surface(
                            color = PanelSurface,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, ConsoleBorder, RoundedCornerShape(12.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(inst.displayName, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "${inst.category} • ${inst.baseFreqHz.toInt()} Hz • Articulations: ${inst.articulations.joinToString()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SaffronIndian
                                    )
                                }
                                Button(
                                    onClick = { onPreviewInstrument(inst) },
                                    colors = ButtonDefaults.buttonColors(containerColor = SaffronIndian, contentColor = ObsidianBg),
                                    modifier = Modifier.testTag("preview_inst_${inst.id}")
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Audition ${inst.displayName}", modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Play")
                                }
                            }
                        }
                    }
                }
                1 -> {
                    if (localPatterns.isNotEmpty()) {
                        item {
                            Text("Local Saved Custom Patterns", style = MaterialTheme.typography.titleMedium, color = CyanSignal)
                        }
                        items(localPatterns, key = { it.id }) { lp ->
                            Surface(
                                color = ConsoleSurface,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, ConsoleBorder, RoundedCornerShape(12.dp))
                                    .clickable { onLoadLocalPattern(lp) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(lp.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                        Text("${lp.style} • ${lp.timeSignature} • ${lp.bpm.toInt()} BPM", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                    }
                                    Text("Load", color = CyanSignal, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }

                    item {
                        Text("Community Cloud Rhythm Patterns", style = MaterialTheme.typography.titleMedium, color = AmberWarm)
                    }
                    when (sharedPatternsState) {
                        is UiState.Loading -> item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = CyanSignal)
                                Spacer(Modifier.width(8.dp))
                                Text("Loading community rhythms...", color = TextSecondary)
                            }
                        }
                        is UiState.Error -> item {
                            Text("Cloud Rhythm Error: ${sharedPatternsState.message}", color = CrimsonClip)
                        }
                        is UiState.Success -> {
                            val list = sharedPatternsState.data
                            if (list.isEmpty()) {
                                item {
                                    Text("No shared patterns yet. Share your pattern from Drums & Indian Rhythm Studio!", color = TextSecondary)
                                }
                            } else {
                                items(list, key = { it.id }) { sp ->
                                    Surface(
                                        color = PanelSurface,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, ConsoleBorder, RoundedCornerShape(12.dp))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(sp.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                                Text(
                                                    text = "${sp.style} • ${sp.timeSignatureNum}/${sp.timeSignatureDen} • ${sp.bpm.toInt()} BPM • by ${sp.authorName}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = TextSecondary
                                                )
                                            }
                                            Button(onClick = { onLoadCloudPattern(sp) }) { Text("Load") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    item {
                        Text("Imported WAV / AIFF Library (${soundAssets.size})", style = MaterialTheme.typography.titleMedium, color = TealSampler)
                    }
                    if (soundAssets.isEmpty()) {
                        item {
                            Text("No imported WAV/AIFF assets yet. Tap 'Import WAV/AIFF' to load audio.", color = TextSecondary)
                        }
                    } else {
                        items(soundAssets, key = { it.id }) { asset ->
                            Surface(
                                color = PanelSurface,
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
                                    Column {
                                        Text(asset.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                        Text("${asset.formatLabel} • ${asset.sampleRate} Hz • ${String.format("%.1f", asset.durationSec)}s", style = MaterialTheme.typography.labelSmall, color = TealSampler)
                                    }
                                    Icon(Icons.Rounded.CheckCircle, contentDescription = "Ready", tint = EmeraldMeter)
                                }
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(6.dp))
                        Text("Project Audio Clip Relinking (${allClips.size})", style = MaterialTheme.typography.titleMedium, color = AmberWarm)
                    }
                    items(allClips, key = { it.second.id }) { (trackName, clip) ->
                        Surface(
                            color = ConsoleSurface,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, if (clip.missingSource) CrimsonClip else ConsoleBorder, RoundedCornerShape(10.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${clip.name} ($trackName)", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                    Text(
                                        text = if (clip.missingSource) "Missing source — relink WAV/AIFF" else "Linked • ${String.format("%.1f", clip.lengthBeats)} beats",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (clip.missingSource) CrimsonClip else TextSecondary
                                    )
                                }
                                OutlinedButton(onClick = { onRelinkClip(clip.id) }) {
                                    Text("Relink")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
