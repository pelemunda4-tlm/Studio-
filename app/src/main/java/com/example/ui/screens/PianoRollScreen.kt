package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.audio.MidiCompositionTools
import com.example.model.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PianoRollScreen(
    project: DawProject,
    selectedTrackId: String,
    playheadBeat: Double = 0.0,
    onSelectKeyAndScale: (String, MusicalScale) -> Unit,
    onSelectInstrument: (String, String) -> Unit,
    onToggleNote: (Int, Double) -> Unit,
    onUpdateNoteVelocity: (String, String, Int) -> Unit = { _, _, _ -> },
    onQuantize: () -> Unit,
    onHumanize: () -> Unit,
    onRandomizeVelocity: () -> Unit,
    onArpeggiate: () -> Unit,
    onPreviewPitch: (Int) -> Unit
) {
    val activeTrack = project.tracks.firstOrNull { it.id == selectedTrackId }
        ?: project.tracks.firstOrNull { it.type == TrackType.MIDI }
        ?: project.tracks.first()

    // Full 88-Key range (21 = A0 .. 108 = C8), centered near C4 on initial display
    val all88Keys = remember { (108 downTo 21).toList() }
    val keyListState = rememberLazyListState(initialFirstVisibleItemIndex = 34)
    val horizontalScrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    var rollZoom by remember { mutableFloatStateOf(1.0f) }
    val steps = remember { (0 until 32).map { it * 0.5 } } // 16 beats in 1/8th grid
    val cellWidthDp = (38f * rollZoom).dp

    val detectedChord = remember(activeTrack.midiNotes) {
        MidiCompositionTools.detectChordFromPitches(activeTrack.midiNotes.map { it.pitch })
    }
    var selectedLane by remember { mutableIntStateOf(0) }
    var midiCcValue by remember { mutableFloatStateOf(88f) }
    var showScaleMenu by remember { mutableStateOf(false) }
    var showInstrumentMenu by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<MidiNote?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        // Horizontal Note Editing Tools & Scale/Instrument Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("PIANO ROLL", style = MaterialTheme.typography.labelSmall, color = VioletMidi, fontWeight = FontWeight.Bold)

            Box {
                FilledTonalButton(
                    onClick = { showScaleMenu = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp).testTag("scale_selector_btn")
                ) {
                    Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${project.musicalKey} • ${project.scale.displayName}", style = MaterialTheme.typography.labelSmall)
                }
                DropdownMenu(expanded = showScaleMenu, onDismissRequest = { showScaleMenu = false }) {
                    MusicalScale.entries.forEach { sc ->
                        DropdownMenuItem(
                            text = { Text("${sc.category}: ${sc.displayName}") },
                            onClick = {
                                onSelectKeyAndScale(project.musicalKey, sc)
                                showScaleMenu = false
                            }
                        )
                    }
                }
            }

            Box {
                OutlinedButton(
                    onClick = { showInstrumentMenu = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(activeTrack.instrumentName, style = MaterialTheme.typography.labelSmall)
                }
                DropdownMenu(expanded = showInstrumentMenu, onDismissRequest = { showInstrumentMenu = false }) {
                    listOf("Bansuri", "Sitar", "Harmonium", "Flute", "Grand Piano", "Warm Synth Pad", "Bass Pluck").forEach { inst ->
                        DropdownMenuItem(
                            text = { Text(inst) },
                            onClick = {
                                onSelectInstrument(activeTrack.id, inst)
                                showInstrumentMenu = false
                            }
                        )
                    }
                }
            }

            Surface(
                color = AmberMuted.copy(alpha = 0.5f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.border(1.dp, AmberWarm, RoundedCornerShape(6.dp))
            ) {
                Text(
                    text = "Chord: $detectedChord",
                    style = MaterialTheme.typography.labelSmall,
                    color = AmberWarm,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }

            AssistChip(onClick = onQuantize, label = { Text("Quantize") }, modifier = Modifier.height(30.dp).testTag("midi_quantize_btn"))
            AssistChip(onClick = onHumanize, label = { Text("Humanize") }, modifier = Modifier.height(30.dp))
            AssistChip(onClick = onRandomizeVelocity, label = { Text("Vel Rnd") }, modifier = Modifier.height(30.dp))
            AssistChip(onClick = onArpeggiate, label = { Text("Arp") }, modifier = Modifier.height(30.dp))
            FilterChip(
                selected = selectedLane == 0,
                onClick = { selectedLane = if (selectedLane == 0) 1 else 0 },
                label = { Text("Lane ${selectedLane + 1}") },
                modifier = Modifier.height(30.dp)
            )
            // Quick Octave Jump
            listOf("C5" to 24, "C4" to 36, "C3" to 48).forEach { (lbl, idx) ->
                OutlinedButton(
                    onClick = { coroutineScope.launch { keyListState.scrollToItem(idx) } },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) { Text(lbl, style = MaterialTheme.typography.labelSmall) }
            }
            Icon(Icons.Rounded.ZoomIn, contentDescription = "Piano Roll Zoom", tint = TextSecondary, modifier = Modifier.size(16.dp))
            Slider(
                value = rollZoom,
                onValueChange = { rollZoom = it },
                valueRange = 0.6f..2.2f,
                modifier = Modifier.width(85.dp).height(24.dp)
            )
        }

        Spacer(Modifier.height(4.dp))

        // Center Area: Left Piano Keys + Center Horizontally Scrollable / Pinch-Zoomable MIDI Grid
        LazyColumn(
            state = keyListState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .border(1.dp, ConsoleBorder, RoundedCornerShape(8.dp))
                .background(ConsoleSurface)
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (kotlin.math.abs(zoom - 1f) > 0.02f) {
                            rollZoom = (rollZoom * zoom).coerceIn(0.6f, 2.2f)
                        }
                    }
                }
        ) {
            items(all88Keys, key = { it }) { pitch ->
                val noteName = MidiCompositionTools.midiToNoteName(pitch)
                val isSharp = noteName.contains("#")
                val inScale = MidiCompositionTools.isPitchInScale(pitch, project.musicalKey, project.scale)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .border(0.5.dp, ConsoleBorder.copy(alpha = 0.35f)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Piano Key
                    Box(
                        modifier = Modifier
                            .width(58.dp)
                            .fillMaxHeight()
                            .background(
                                when {
                                    isSharp -> ObsidianBg
                                    inScale -> ElevatedRack
                                    else -> PanelSurface
                                }
                            )
                            .border(0.5.dp, ConsoleBorder)
                            .clickable { onPreviewPitch(pitch) }
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = noteName,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (inScale) CyanSignal else TextMuted,
                            fontWeight = if (noteName.startsWith("C") && !isSharp) FontWeight.Bold else FontWeight.Normal
                        )
                    }

                    // Center MIDI Grid Row (Synchronized Horizontal Scroll)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .horizontalScroll(horizontalScrollState)
                    ) {
                        steps.forEach { beat ->
                            val matchedNote = activeTrack.midiNotes.firstOrNull {
                                it.pitch == pitch && kotlin.math.abs(it.startBeat - beat) < 0.2
                            }
                            val isPlayhead = kotlin.math.abs(playheadBeat - beat) < 0.25
                            Box(
                                modifier = Modifier
                                    .width(cellWidthDp)
                                    .fillMaxHeight()
                                    .background(
                                        when {
                                            matchedNote != null -> VioletMidi.copy(alpha = (matchedNote.velocity / 127f).coerceIn(0.45f, 1f))
                                            isPlayhead -> CyanSignal.copy(alpha = 0.16f)
                                            inScale -> CyanSignal.copy(alpha = 0.05f)
                                            else -> ObsidianBg.copy(alpha = 0.72f)
                                        }
                                    )
                                    .border(
                                        0.5.dp,
                                        if (beat % 1.0 == 0.0) ConsoleBorder else ConsoleBorder.copy(alpha = 0.35f)
                                    )
                                    .combinedClickable(
                                        onClick = { onToggleNote(pitch, beat) },
                                        onDoubleClick = { if (matchedNote != null) editingNote = matchedNote },
                                        onLongClick = { if (matchedNote != null) editingNote = matchedNote }
                                    )
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // Bottom Velocity Lane + CC1 Expression Control
        Surface(
            color = PanelSurface,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .border(1.dp, ConsoleBorder, RoundedCornerShape(8.dp))
        ) {
            Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier
                        .width(58.dp)
                        .fillMaxHeight()
                        .background(ConsoleSurface)
                        .padding(4.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("VEL", style = MaterialTheme.typography.labelSmall, color = AmberWarm, fontWeight = FontWeight.Bold)
                    Text("CC:${midiCcValue.toInt()}", style = MaterialTheme.typography.labelSmall, color = CyanSignal)
                }

                // Synchronized Velocity Bars
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .horizontalScroll(horizontalScrollState)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    steps.forEach { beat ->
                        val noteAtBeat = activeTrack.midiNotes.firstOrNull { kotlin.math.abs(it.startBeat - beat) < 0.2 }
                        val velFrac = (noteAtBeat?.velocity ?: 0) / 127f
                        Box(
                            modifier = Modifier
                                .width(cellWidthDp)
                                .fillMaxHeight()
                                .border(0.5.dp, ConsoleBorder.copy(alpha = 0.3f))
                                .clickable(enabled = noteAtBeat != null) {
                                    noteAtBeat?.let { n ->
                                        val nextVel = if (n.velocity >= 115) 64 else (n.velocity + 20).coerceAtMost(127)
                                        onUpdateNoteVelocity(activeTrack.id, n.id, nextVel)
                                    }
                                },
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            if (noteAtBeat != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.7f)
                                        .fillMaxHeight(velFrac.coerceIn(0.15f, 1f))
                                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                        .background(AmberWarm)
                                )
                            }
                        }
                    }
                }

                // Compact CC1 Slider on right side of Velocity Lane
                Column(
                    modifier = Modifier.width(120.dp).padding(horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("CC1 Mod", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Slider(
                        value = midiCcValue,
                        onValueChange = { midiCcValue = it },
                        valueRange = 0f..127f,
                        modifier = Modifier.height(24.dp)
                    )
                }
            }
        }
    }

    editingNote?.let { note ->
        var velSlider by remember(note.id) { mutableFloatStateOf(note.velocity.toFloat()) }
        AlertDialog(
            onDismissRequest = { editingNote = null },
            containerColor = PanelSurface,
            title = { Text("Edit Note: ${MidiCompositionTools.midiToNoteName(note.pitch)}", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Start Beat: ${note.startBeat} | Velocity: ${velSlider.toInt()}", color = TextSecondary)
                    Slider(value = velSlider, onValueChange = { velSlider = it }, valueRange = 1f..127f)
                }
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateNoteVelocity(activeTrack.id, note.id, velSlider.toInt())
                    editingNote = null
                }) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = {
                    onToggleNote(note.pitch, note.startBeat)
                    editingNote = null
                }) { Text("Delete Note", color = CrimsonClip) }
            }
        )
    }
}
