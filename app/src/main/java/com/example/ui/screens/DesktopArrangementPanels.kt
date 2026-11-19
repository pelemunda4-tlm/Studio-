package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.audio.MasteringProcessor
import com.example.data.LocalProjectEntity
import com.example.data.SoundAssetEntity
import com.example.model.*
import com.example.ui.components.RotaryStudioKnob
import com.example.ui.theme.*

data class BrowserDragPayload(
    val category: BrowserCategory,
    val itemName: String,
    val rootPosition: Offset
)

@Composable
fun LeftTrackInspectorPanel(
    project: DawProject,
    selectedTrack: DawTrack?,
    onUpdateTrack: (String, String, (DawTrack) -> DawTrack) -> Unit,
    onToggleQuickPlugin: (String, PluginType) -> Unit,
    onOpenPluginBrowser: () -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showInputMenu by remember { mutableStateOf(false) }
    var showOutputMenu by remember { mutableStateOf(false) }
    var editingTrackName by remember { mutableStateOf(false) }

    Surface(
        color = ConsoleSurface,
        modifier = modifier
            .fillMaxHeight()
            .border(1.dp, ConsoleBorder)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Rounded.Tune, contentDescription = null, tint = StudioBlue, modifier = Modifier.size(15.dp))
                    Text("Inspector", style = MaterialTheme.typography.labelLarge, color = TextPrimary, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onCollapse, modifier = Modifier.size(22.dp)) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = "Collapse Inspector", tint = TextSecondary, modifier = Modifier.size(16.dp))
                }
            }

            HorizontalDivider(color = ConsoleBorder, modifier = Modifier.padding(vertical = 4.dp))

            if (selectedTrack == null) {
                Text("Select a track to inspect controls.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            } else {
                val trackColor = Color(selectedTrack.colorArgb)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Track Header Card: Name, Color, Icon
                    Surface(
                        color = PanelSurface,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, trackColor.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                            .clickable { editingTrackName = true }
                    ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(trackIconForType(selectedTrack.type), contentDescription = selectedTrack.type.label, tint = trackColor, modifier = Modifier.size(17.dp))
                                Text(
                                    text = selectedTrack.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Text("${selectedTrack.type.label} • ${selectedTrack.instrumentName}", style = MaterialTheme.typography.labelSmall, color = trackColor)

                            // Track Color Swatches
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Color:", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                listOf(0xFF22C55EL, 0xFFA855F7L, 0xFFF97316L, 0xFF2F8BFFL, 0xFF14B8A6L, 0xFFF43F5EL).forEach { col ->
                                    Box(
                                        modifier = Modifier
                                            .size(13.dp)
                                            .clip(CircleShape)
                                            .background(Color(col))
                                            .border(if (selectedTrack.colorArgb == col) 2.dp else 0.dp, TextPrimary, CircleShape)
                                            .clickable { onUpdateTrack(selectedTrack.id, "Track Color") { it.copy(colorArgb = col) } }
                                    )
                                }
                            }
                        }
                    }

                    // M / S / R / Mon Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        InspectorButton("M", selectedTrack.muted, CrimsonClip, Modifier.weight(1f)) {
                            onUpdateTrack(selectedTrack.id, "Mute") { it.copy(muted = !it.muted) }
                        }
                        InspectorButton("S", selectedTrack.soloed, AmberWarm, Modifier.weight(1f)) {
                            onUpdateTrack(selectedTrack.id, "Solo") { it.copy(soloed = !it.soloed) }
                        }
                        InspectorButton("R", selectedTrack.armed, CrimsonClip, Modifier.weight(1f)) {
                            onUpdateTrack(selectedTrack.id, "Record Arm") { it.copy(armed = !it.armed) }
                        }
                        InspectorButton("Mon", selectedTrack.monitoring, StudioBlue, Modifier.weight(1f)) {
                            onUpdateTrack(selectedTrack.id, "Monitor") { it.copy(monitoring = !it.monitoring) }
                        }
                    }

                    // Volume & Pan Knobs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        RotaryStudioKnob(
                            label = "Volume",
                            value = selectedTrack.volume * 100f,
                            min = 0f,
                            max = 120f,
                            unit = "%",
                            accentColor = trackColor,
                            knobSize = 38.dp,
                            onValueChange = { v -> onUpdateTrack(selectedTrack.id, "Volume") { it.copy(volume = v / 100f) } }
                        )
                        RotaryStudioKnob(
                            label = "Pan",
                            value = selectedTrack.pan,
                            min = -1f,
                            max = 1f,
                            accentColor = StudioBlue,
                            knobSize = 38.dp,
                            onValueChange = { p -> onUpdateTrack(selectedTrack.id, "Pan") { it.copy(pan = p) } }
                        )
                    }

                    // Input & Output Routing
                    Text("I/O Routing", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontWeight = FontWeight.Bold)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            color = TrackWorkspaceBg,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, ConsoleBorder, RoundedCornerShape(4.dp))
                                .clickable { showInputMenu = true }
                        ) {
                            Text("In: ${selectedTrack.inputChannel}", style = MaterialTheme.typography.labelSmall, color = TextPrimary, modifier = Modifier.padding(6.dp))
                        }
                        DropdownMenu(expanded = showInputMenu, onDismissRequest = { showInputMenu = false }) {
                            listOf("In 1", "In 1-2", "Mic In 1", "USB Ch 1-2", "MIDI All", "Drum Bus").forEach { inp ->
                                DropdownMenuItem(
                                    text = { Text(inp) },
                                    onClick = {
                                        onUpdateTrack(selectedTrack.id, "Input $inp") { it.copy(inputChannel = inp) }
                                        showInputMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            color = TrackWorkspaceBg,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, ConsoleBorder, RoundedCornerShape(4.dp))
                                .clickable { showOutputMenu = true }
                        ) {
                            Text("Out: ${selectedTrack.outputChannel}", style = MaterialTheme.typography.labelSmall, color = StudioBlue, modifier = Modifier.padding(6.dp))
                        }
                        DropdownMenu(expanded = showOutputMenu, onDismissRequest = { showOutputMenu = false }) {
                            listOf("Main Out", "Drum & Folk Bus", "Vocal Bus", "Music Bus", "Submix L/R").forEach { outp ->
                                DropdownMenuItem(
                                    text = { Text(outp) },
                                    onClick = {
                                        onUpdateTrack(selectedTrack.id, "Output $outp") { it.copy(outputChannel = outp) }
                                        showOutputMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // Inserts
                    Text("Inserts (${selectedTrack.plugins.size})", style = MaterialTheme.typography.labelSmall, color = AmberWarm, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AssistChip(onClick = { onToggleQuickPlugin(selectedTrack.id, PluginType.EQ_3_BAND) }, label = { Text("+EQ") }, modifier = Modifier.height(26.dp))
                        AssistChip(onClick = { onToggleQuickPlugin(selectedTrack.id, PluginType.COMPRESSOR) }, label = { Text("+Comp") }, modifier = Modifier.height(26.dp))
                        AssistChip(onClick = { onToggleQuickPlugin(selectedTrack.id, PluginType.HALL_REVERB) }, label = { Text("+Verb") }, modifier = Modifier.height(26.dp))
                    }
                    selectedTrack.plugins.forEach { plg ->
                        Surface(
                            color = ElevatedRack,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.fillMaxWidth().clickable { onToggleQuickPlugin(selectedTrack.id, plg.type) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(plg.type.displayName, style = MaterialTheme.typography.labelSmall, color = if (plg.bypassed) TextMuted else TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (plg.bypassed) "BYP" else "ON", style = MaterialTheme.typography.labelSmall, color = if (plg.bypassed) CrimsonClip else AudioGreen)
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = onOpenPluginBrowser,
                        contentPadding = PaddingValues(6.dp),
                        modifier = Modifier.fillMaxWidth().height(30.dp)
                    ) {
                        Text("Open Full FX Rack", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }

    if (editingTrackName && selectedTrack != null) {
        var nameInput by remember(selectedTrack.id) { mutableStateOf(selectedTrack.name) }
        AlertDialog(
            onDismissRequest = { editingTrackName = false },
            containerColor = PanelSurface,
            title = { Text("Rename Track", color = TextPrimary) },
            text = {
                OutlinedTextField(value = nameInput, onValueChange = { nameInput = it }, singleLine = true, label = { Text("Track Name") })
            },
            confirmButton = {
                Button(onClick = {
                    if (nameInput.isNotBlank()) onUpdateTrack(selectedTrack.id, "Rename Track") { it.copy(name = nameInput.trim()) }
                    editingTrackName = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingTrackName = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun RightSideBrowserPanel(
    selectedTrack: DawTrack?,
    presetPatterns: List<RhythmPattern>,
    soundAssets: List<SoundAssetEntity>,
    localProjects: List<LocalProjectEntity>,
    onStartDragItem: (BrowserDragPayload) -> Unit,
    onMoveDragItem: (Offset) -> Unit,
    onEndDragItem: () -> Unit,
    onQuickInsertItem: (BrowserCategory, String) -> Unit,
    onPreviewIndianInstrument: (IndianInstrument) -> Unit,
    onPreviewMidiPitch: (String, Int) -> Unit,
    onImportAudioFile: () -> Unit,
    onLoadLocalProject: (LocalProjectEntity) -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf(BrowserCategory.INSTRUMENTS) }

    Surface(
        color = ConsoleSurface,
        modifier = modifier
            .fillMaxHeight()
            .border(1.dp, ConsoleBorder)
            .testTag("right_browser_panel")
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = AmberWarm, modifier = Modifier.size(15.dp))
                    Text("BROWSER", style = MaterialTheme.typography.labelLarge, color = TextPrimary, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onCollapse, modifier = Modifier.size(22.dp)) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = "Collapse Browser", tint = TextSecondary, modifier = Modifier.size(16.dp))
                }
            }

            // 8 Category Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                BrowserCategory.entries.forEach { cat ->
                    val active = selectedCategory == cat
                    Surface(
                        color = if (active) StudioBlueMuted else PanelSurface,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .border(1.dp, if (active) StudioBlue else ConsoleBorder, RoundedCornerShape(4.dp))
                            .clickable { selectedCategory = cat }
                            .testTag("browser_cat_${cat.name}")
                    ) {
                        Text(
                            text = cat.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) TextPrimary else TextSecondary,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Text(
                text = "Tap to load • Hold & drag onto Timeline",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                when (selectedCategory) {
                    BrowserCategory.INSTRUMENTS -> {
                        val synths = listOf("Grand Piano", "Warm Synth Pad", "Bass Pluck", "Bansuri", "Sitar", "Harmonium")
                        items(synths) { name ->
                            DraggableBrowserRow(
                                category = BrowserCategory.INSTRUMENTS,
                                title = name,
                                subtitle = "Melodic / Synth",
                                accent = VioletMidi,
                                onTap = {
                                    onPreviewMidiPitch(name, 60)
                                    onQuickInsertItem(BrowserCategory.INSTRUMENTS, name)
                                },
                                onStartDrag = onStartDragItem,
                                onMoveDrag = onMoveDragItem,
                                onEndDrag = onEndDragItem
                            )
                        }
                        items(IndianInstrument.entries, key = { it.id }) { inst ->
                            DraggableBrowserRow(
                                category = BrowserCategory.INSTRUMENTS,
                                title = inst.displayName,
                                subtitle = inst.category,
                                accent = SaffronIndian,
                                onTap = {
                                    onPreviewIndianInstrument(inst)
                                    onQuickInsertItem(BrowserCategory.INSTRUMENTS, inst.displayName)
                                },
                                onStartDrag = onStartDragItem,
                                onMoveDrag = onMoveDragItem,
                                onEndDrag = onEndDragItem
                            )
                        }
                    }
                    BrowserCategory.EFFECTS -> {
                        items(PluginType.entries, key = { it.name }) { plg ->
                            DraggableBrowserRow(
                                category = BrowserCategory.EFFECTS,
                                title = plg.displayName,
                                subtitle = plg.category.label,
                                accent = StudioBlue,
                                onTap = { onQuickInsertItem(BrowserCategory.EFFECTS, plg.displayName) },
                                onStartDrag = onStartDragItem,
                                onMoveDrag = onMoveDragItem,
                                onEndDrag = onEndDragItem
                            )
                        }
                    }
                    BrowserCategory.SAMPLES -> {
                        val builtInSamples = listOf("Mandar Hit One-Shot", "Tabla Na Stroke", "Acoustic Strum Stems", "Vocal Breath Layer", "Sub 808 Boom") +
                            soundAssets.map { it.name }
                        items(builtInSamples.distinct()) { sampleName ->
                            DraggableBrowserRow(
                                category = BrowserCategory.SAMPLES,
                                title = sampleName,
                                subtitle = "Audio Sample",
                                accent = AudioGreen,
                                onTap = { onQuickInsertItem(BrowserCategory.SAMPLES, sampleName) },
                                onStartDrag = onStartDragItem,
                                onMoveDrag = onMoveDragItem,
                                onEndDrag = onEndDragItem
                            )
                        }
                    }
                    BrowserCategory.LOOPS -> {
                        val loops = listOf(
                            "Sambalpuri Dalkhai Loop 116",
                            "Nagpuri Theth Groove 124",
                            "Bollywood Dholak Loop 102",
                            "Acoustic Rhythm Loop 116",
                            "Worship Pad Swell 74"
                        )
                        items(loops) { loopName ->
                            DraggableBrowserRow(
                                category = BrowserCategory.LOOPS,
                                title = loopName,
                                subtitle = "4-Bar Loop",
                                accent = AudioGreen,
                                onTap = { onQuickInsertItem(BrowserCategory.LOOPS, loopName) },
                                onStartDrag = onStartDragItem,
                                onMoveDrag = onMoveDragItem,
                                onEndDrag = onEndDragItem
                            )
                        }
                    }
                    BrowserCategory.DRUM_KITS -> {
                        items(presetPatterns, key = { it.id }) { pat ->
                            DraggableBrowserRow(
                                category = BrowserCategory.DRUM_KITS,
                                title = pat.name,
                                subtitle = "${pat.timeSignature.label} • ${pat.lanes.size} drums",
                                accent = AutomationOrange,
                                onTap = { onQuickInsertItem(BrowserCategory.DRUM_KITS, pat.name) },
                                onStartDrag = onStartDragItem,
                                onMoveDrag = onMoveDragItem,
                                onEndDrag = onEndDragItem
                            )
                        }
                    }
                    BrowserCategory.PRESETS -> {
                        items(VocalChainType.entries, key = { it.name }) { vc ->
                            DraggableBrowserRow(
                                category = BrowserCategory.PRESETS,
                                title = vc.displayName,
                                subtitle = "Vocal Chain",
                                accent = CoralVocal,
                                onTap = { onQuickInsertItem(BrowserCategory.PRESETS, vc.displayName) },
                                onStartDrag = onStartDragItem,
                                onMoveDrag = onMoveDragItem,
                                onEndDrag = onEndDragItem
                            )
                        }
                        items(MasteringProcessor.MASTERING_PRESETS, key = { it.presetName }) { mp ->
                            DraggableBrowserRow(
                                category = BrowserCategory.PRESETS,
                                title = mp.presetName,
                                subtitle = "Mastering Preset",
                                accent = StudioBlue,
                                onTap = { onQuickInsertItem(BrowserCategory.PRESETS, mp.presetName) },
                                onStartDrag = onStartDragItem,
                                onMoveDrag = onMoveDragItem,
                                onEndDrag = onEndDragItem
                            )
                        }
                    }
                    BrowserCategory.PROJECTS -> {
                        val templates = listOf("Sambalpuri Folk Production", "Bollywood Cinematic", "Christian Worship Live")
                        items(templates) { tpl ->
                            DraggableBrowserRow(
                                category = BrowserCategory.PROJECTS,
                                title = tpl,
                                subtitle = "Project Template",
                                accent = StudioBlue,
                                onTap = { onQuickInsertItem(BrowserCategory.PROJECTS, tpl) },
                                onStartDrag = onStartDragItem,
                                onMoveDrag = onMoveDragItem,
                                onEndDrag = onEndDragItem
                            )
                        }
                        items(localProjects, key = { it.id }) { lp ->
                            Surface(
                                color = PanelSurface,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, ConsoleBorder, RoundedCornerShape(6.dp))
                                    .clickable { onLoadLocalProject(lp) }
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(lp.title, style = MaterialTheme.typography.labelMedium, color = TextPrimary, maxLines = 1)
                                    Text("v${lp.versionNumber} • ${lp.bpm.toInt()} BPM", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                }
                            }
                        }
                    }
                    BrowserCategory.FILES -> {
                        item {
                            Button(
                                onClick = onImportAudioFile,
                                colors = ButtonDefaults.buttonColors(containerColor = AudioGreen, contentColor = TrackWorkspaceBg),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.AudioFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Import WAV / AIFF File", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        items(soundAssets, key = { it.id }) { asset ->
                            DraggableBrowserRow(
                                category = BrowserCategory.SAMPLES,
                                title = asset.name,
                                subtitle = "${asset.formatLabel} • ${asset.sampleRate} Hz",
                                accent = TealSampler,
                                onTap = { onQuickInsertItem(BrowserCategory.SAMPLES, asset.name) },
                                onStartDrag = onStartDragItem,
                                onMoveDrag = onMoveDragItem,
                                onEndDrag = onEndDragItem
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DraggableBrowserRow(
    category: BrowserCategory,
    title: String,
    subtitle: String,
    accent: Color,
    onTap: () -> Unit,
    onStartDrag: (BrowserDragPayload) -> Unit,
    onMoveDrag: (Offset) -> Unit,
    onEndDrag: () -> Unit
) {
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }

    Surface(
        color = PanelSurface,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ConsoleBorder, RoundedCornerShape(6.dp))
            .onGloballyPositioned { coords -> rootOrigin = coords.positionInRoot() }
            .pointerInput(category, title) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { startOffset ->
                        onStartDrag(BrowserDragPayload(category, title, rootOrigin + startOffset))
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onMoveDrag(dragAmount)
                    },
                    onDragEnd = { onEndDrag() },
                    onDragCancel = { onEndDrag() }
                )
            }
            .clickable { onTap() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelSmall, color = TextPrimary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = accent, maxLines = 1)
            }
            Icon(Icons.Rounded.AddCircleOutline, contentDescription = "Add $title", tint = accent, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun InspectorButton(
    label: String,
    active: Boolean,
    activeColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = if (active) activeColor else TrackWorkspaceBg,
        shape = RoundedCornerShape(4.dp),
        modifier = modifier
            .height(26.dp)
            .border(1.dp, if (active) activeColor else ConsoleBorder, RoundedCornerShape(4.dp))
            .clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) TrackWorkspaceBg else TextSecondary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

fun trackIconForType(type: TrackType): ImageVector = when (type) {
    TrackType.AUDIO -> Icons.Rounded.GraphicEq
    TrackType.MIDI -> Icons.Rounded.Piano
    TrackType.INSTRUMENT -> Icons.Rounded.MusicNote
    TrackType.DRUM, TrackType.INDIAN_RHYTHM -> Icons.Rounded.Album
    TrackType.VOCAL -> Icons.Rounded.Mic
    TrackType.SAMPLER -> Icons.Rounded.GridView
    TrackType.AUTOMATION -> Icons.Rounded.Timeline
    TrackType.BUS -> Icons.Rounded.AccountTree
    TrackType.VCA -> Icons.Rounded.Tune
    TrackType.FOLDER -> Icons.Rounded.Folder
    TrackType.MASTER -> Icons.Rounded.Equalizer
}
