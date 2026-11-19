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
import com.example.audio.PluginCatalog
import com.example.model.*
import com.example.ui.components.RotaryStudioKnob
import com.example.ui.components.StereoMeterRow
import com.example.ui.theme.*

@Composable
fun PluginBrowserScreen(
    project: DawProject,
    selectedTrackId: String,
    onSelectTrack: (String) -> Unit,
    onInsertPlugin: (PluginType) -> Unit,
    onUpdatePlugin: (String, PluginInstance) -> Unit,
    onRemovePlugin: (String, String) -> Unit
) {
    val activeTrack = project.tracks.firstOrNull { it.id == selectedTrackId } ?: project.tracks.firstOrNull()
    var selectedCategory by remember { mutableStateOf<PluginCategory?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredPlugins = remember(selectedCategory, searchQuery) {
        PluginType.entries.filter { pt ->
            val catOk = selectedCategory == null || pt.category == selectedCategory
            val qOk = searchQuery.isBlank() || pt.displayName.contains(searchQuery, ignoreCase = true)
            catOk && qOk
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(12.dp)
            .testTag("plugin_browser_list"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Track selector strip
        item {
            Text("TRACK INSERT RACK & 28 BUILT-IN PLUGINS", style = MaterialTheme.typography.labelSmall, color = CyanSignal)
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                project.tracks.forEach { tr ->
                    FilterChip(
                        selected = tr.id == activeTrack?.id,
                        onClick = { onSelectTrack(tr.id) },
                        label = { Text("${tr.name} (${tr.plugins.size})") }
                    )
                }
            }
        }

        // Active Track Insert Plugins GUI (Preset, In/Out Meter, Wet/Dry, Bypass, Knobs)
        if (activeTrack != null) {
            item {
                val totalPdc = activeTrack.plugins.filter { !it.bypassed }.sumOf { it.type.latencySamples }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${activeTrack.name} Inserts", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                    Text("PDC Latency: $totalPdc smp", style = MaterialTheme.typography.labelSmall, color = AmberWarm)
                }
            }
            if (activeTrack.plugins.isEmpty()) {
                item {
                    Surface(
                        color = PanelSurface,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ConsoleBorder, RoundedCornerShape(12.dp))
                    ) {
                        Text(
                            text = "No plugins inserted on ${activeTrack.name} yet. Tap '+ Insert' on any plugin below.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            } else {
                items(activeTrack.plugins, key = { it.id }) { plugin ->
                    PluginRackUnitCard(
                        plugin = plugin,
                        onUpdate = { updated -> onUpdatePlugin(activeTrack.id, updated) },
                        onRemove = { onRemovePlugin(activeTrack.id, plugin.id) }
                    )
                }
            }
        }

        // Plugin Browser Search & Category Filters
        item {
            HorizontalDivider(color = ConsoleBorder, modifier = Modifier.padding(vertical = 4.dp))
            Text("Expandable Plugin Browser (${PluginType.entries.size} Built-in Processors)", style = MaterialTheme.typography.titleMedium, color = AmberWarm)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search EQ, Dynamics, Reverb, Delay, Creative FX...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text("All (${PluginType.entries.size})") }
                )
                PluginCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat.label) }
                    )
                }
            }
        }

        // Catalog Items
        items(filteredPlugins, key = { it.name }) { pluginType ->
            val catColor = when (pluginType.category) {
                PluginCategory.EQUALIZERS -> CyanSignal
                PluginCategory.DYNAMICS -> AmberWarm
                PluginCategory.TIME_EFFECTS -> VioletMidi
                PluginCategory.CREATIVE_EFFECTS -> CoralVocal
            }
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
                        Text(pluginType.displayName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(
                            text = "${pluginType.category.label}${if (pluginType.latencySamples > 0) " • ${pluginType.latencySamples} smp PDC" else " • Zero-Latency"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = catColor
                        )
                    }
                    Button(
                        onClick = { onInsertPlugin(pluginType) },
                        colors = ButtonDefaults.buttonColors(containerColor = catColor, contentColor = ObsidianBg),
                        modifier = Modifier.testTag("insert_plugin_${pluginType.name}")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Insert")
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginRackUnitCard(
    plugin: PluginInstance,
    onUpdate: (PluginInstance) -> Unit,
    onRemove: () -> Unit
) {
    val paramDefs = remember(plugin.type) { PluginCatalog.defaultParams(plugin.type) }
    val presets = remember(plugin.type) { PluginCatalog.presetsFor(plugin.type) }
    var showPresetMenu by remember { mutableStateOf(false) }

    Surface(
        color = if (plugin.bypassed) ConsoleSurface else ElevatedRack,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (plugin.bypassed) ConsoleBorder else CyanSignal.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .testTag("plugin_unit_${plugin.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(plugin.type.displayName, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text(plugin.type.category.label, style = MaterialTheme.typography.labelSmall, color = CyanSignal)
                }

                // Preset Dropdown
                Box {
                    OutlinedButton(onClick = { showPresetMenu = true }) {
                        Text(plugin.presetName, maxLines = 1)
                        Icon(Icons.Rounded.ArrowDropDown, contentDescription = "Presets")
                    }
                    DropdownMenu(expanded = showPresetMenu, onDismissRequest = { showPresetMenu = false }) {
                        presets.forEach { (presetName, presetParams) ->
                            DropdownMenuItem(
                                text = { Text(presetName) },
                                onClick = {
                                    showPresetMenu = false
                                    onUpdate(plugin.copy(presetName = presetName, params = presetParams))
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.width(6.dp))

                // Bypass Toggle
                FilledTonalIconButton(
                    onClick = { onUpdate(plugin.copy(bypassed = !plugin.bypassed)) },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (plugin.bypassed) CrimsonClip else EmeraldMeter,
                        contentColor = ObsidianBg
                    )
                ) {
                    Icon(Icons.Rounded.PowerSettingsNew, contentDescription = "Bypass Plugin")
                }

                IconButton(onClick = onRemove) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove Plugin", tint = TextSecondary)
                }
            }

            // Input & Output Meters
            StereoMeterRow("IN", plugin.inputMeterDb, plugin.inputMeterDb - 3f)
            StereoMeterRow("OUT", plugin.outputMeterDb, plugin.outputMeterDb - 3f)

            // Wet/Dry Control
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Wet/Dry", style = MaterialTheme.typography.labelSmall, color = AmberWarm)
                Slider(
                    value = plugin.wetDry,
                    onValueChange = { w -> onUpdate(plugin.copy(wetDry = w)) },
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f)
                )
                Text("${(plugin.wetDry * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
            }

            // Parameter Knobs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                paramDefs.forEach { def ->
                    val currentVal = plugin.params[def.key] ?: def.value
                    RotaryStudioKnob(
                        label = def.label,
                        value = currentVal,
                        min = def.min,
                        max = def.max,
                        unit = def.unit,
                        accentColor = if (plugin.bypassed) TextMuted else CyanSignal,
                        onValueChange = { nextVal ->
                            val updatedParams = plugin.params.toMutableMap().apply {
                                put(def.key, nextVal)
                            }
                            onUpdate(plugin.copy(params = updatedParams))
                        }
                    )
                }
            }
        }
    }
}
