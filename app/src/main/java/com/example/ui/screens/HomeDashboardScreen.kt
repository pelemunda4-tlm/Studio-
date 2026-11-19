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
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AutosaveRecoveryEntity
import com.example.data.LocalProjectEntity
import com.example.model.CloudProject
import com.example.model.CloudProjectVersion
import com.example.model.DawProject
import com.example.model.DawScreen
import com.example.ui.theme.*
import com.example.viewmodel.UiState

@Composable
fun HomeDashboardScreen(
    project: DawProject,
    userName: String,
    isSignedIn: Boolean,
    autosave: AutosaveRecoveryEntity?,
    localProjects: List<LocalProjectEntity>,
    cloudProjectsState: UiState<List<CloudProject>>,
    selectedVersions: List<CloudProjectVersion>,
    lastShareCode: String?,
    onRestoreAutosave: () -> Unit,
    onDismissAutosave: () -> Unit,
    onCreateFromTemplate: (String) -> Unit,
    onSaveBackup: (String) -> Unit,
    onLoadJsonProject: (String, String) -> Unit,
    onLoadVersions: (String) -> Unit,
    onShareProject: () -> Unit,
    onImportShareCode: (String) -> Unit,
    onExportZip: () -> Unit,
    onExportWav: () -> Unit,
    onNavigateScreen: (DawScreen) -> Unit,
    onSignInOptional: () -> Unit,
    onSignOut: () -> Unit
) {
    var shareCodeInput by remember { mutableStateOf("") }
    var versionLabelInput by remember { mutableStateOf("Studio Mixdown") }
    var showVersionDialogFor by remember { mutableStateOf<CloudProject?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(horizontal = 14.dp)
            .testTag("home_dashboard_list"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 14.dp)
    ) {
        // Hero Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, ConsoleBorder, RoundedCornerShape(18.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_studio_hero),
                    contentDescription = "Trueworld Studio Hero",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(ObsidianBg.copy(alpha = 0.92f), ObsidianBg.copy(alpha = 0.35f))
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(18.dp)
                ) {
                    Text(
                        text = "TRUEWORLD ENTERTAINMENT OFFICIAL",
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberWarm
                    )
                    Text(
                        text = "Welcome, $userName",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Active Session: ${project.title} • ${project.bpm.toInt()} BPM • ${project.musicalKey} ${project.scale.displayName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onNavigateScreen(DawScreen.ARRANGEMENT) },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanSignal, contentColor = ObsidianBg),
                            modifier = Modifier.testTag("open_arrangement_btn")
                        ) {
                            Icon(Icons.Rounded.ViewTimeline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Open Timeline")
                        }
                        if (isSignedIn) {
                            OutlinedButton(onClick = onSignOut) {
                                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = "Sign out", modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Sign Out")
                            }
                        } else {
                            OutlinedButton(onClick = onSignInOptional) {
                                Icon(Icons.Rounded.CloudSync, contentDescription = "Optional Cloud Sync", modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Cloud Sync")
                            }
                        }
                    }
                }
            }
        }

        // Autosave Crash / Session Recovery Banner
        if (autosave != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AmberMuted.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, AmberWarm, RoundedCornerShape(14.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Project Recovery Available", style = MaterialTheme.typography.titleMedium, color = AmberWarm)
                            Text("Unsaved autosave for '${autosave.projectTitle}'", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = onDismissAutosave) { Text("Dismiss") }
                            Button(
                                onClick = onRestoreAutosave,
                                colors = ButtonDefaults.buttonColors(containerColor = AmberWarm, contentColor = ObsidianBg)
                            ) { Text("Recover") }
                        }
                    }
                }
            }
        }

        // Quick Workspace Launchers (All 10 DAW Modules)
        item {
            Text("Studio Workspaces", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DawScreen.entries.filter { it != DawScreen.HOME }.forEach { screen ->
                    Surface(
                        color = PanelSurface,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .border(1.dp, ConsoleBorder, RoundedCornerShape(12.dp))
                            .clickable { onNavigateScreen(screen) }
                            .testTag("quick_nav_${screen.route}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Tune, contentDescription = null, tint = CyanSignal, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(screen.label, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                        }
                    }
                }
            }
        }

        // Project Templates
        item {
            Text("Project Templates", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(
                    "Sambalpuri Folk Production",
                    "Bollywood Cinematic",
                    "Christian Worship Live",
                    "Modern Studio Beat"
                ).forEach { tpl ->
                    ElevatedCard(
                        colors = CardDefaults.elevatedCardColors(containerColor = ElevatedRack),
                        modifier = Modifier
                            .width(210.dp)
                            .border(1.dp, ConsoleBorder, RoundedCornerShape(14.dp))
                            .clickable { onCreateFromTemplate(tpl) }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Icon(Icons.Rounded.LibraryMusic, contentDescription = null, tint = SaffronIndian)
                            Spacer(Modifier.height(6.dp))
                            Text(tpl, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text("Tap to initialize session", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                    }
                }
            }
        }

        // Backup, Export (ZIP & WAV), and Cloud Collaboration
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = PanelSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ConsoleBorder, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Cloud Backup, ZIP Export & Sharing", style = MaterialTheme.typography.titleLarge, color = CyanSignal)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = versionLabelInput,
                            onValueChange = { versionLabelInput = it },
                            label = { Text("Version Label") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { onSaveBackup(versionLabelInput) },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldMeter, contentColor = ObsidianBg),
                            modifier = Modifier.testTag("backup_cloud_btn")
                        ) {
                            Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (isSignedIn) "Backup v${project.versionNumber + 1}" else "Save v${project.versionNumber + 1}")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = onExportZip, modifier = Modifier.weight(1f).testTag("export_zip_btn")) {
                            Icon(Icons.Rounded.FolderZip, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Export ZIP")
                        }
                        OutlinedButton(onClick = onExportWav, modifier = Modifier.weight(1f).testTag("export_wav_btn")) {
                            Icon(Icons.Rounded.AudioFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Bounce WAV")
                        }
                        OutlinedButton(onClick = onShareProject, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Share")
                        }
                    }

                    if (!lastShareCode.isNullOrBlank()) {
                        Text(
                            text = "Share Code: $lastShareCode",
                            style = MaterialTheme.typography.labelMedium,
                            color = AmberWarm
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = shareCodeInput,
                            onValueChange = { shareCodeInput = it },
                            label = { Text("Import Share Code (e.g. TW-...)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { if (shareCodeInput.isNotBlank()) onImportShareCode(shareCodeInput) },
                            enabled = shareCodeInput.isNotBlank()
                        ) { Text("Load") }
                    }
                }
            }
        }

        // Cloud Projects & Version History
        item {
            Text("Cloud Project Library", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        }
        when (cloudProjectsState) {
            is UiState.Loading -> item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = CyanSignal)
                    Spacer(Modifier.width(10.dp))
                    Text("Syncing cloud studio sessions...", color = TextSecondary)
                }
            }
            is UiState.Error -> item {
                Text("Cloud Sync Notice: ${cloudProjectsState.message}", color = CrimsonClip, style = MaterialTheme.typography.bodyMedium)
            }
            is UiState.Success -> {
                val list = cloudProjectsState.data
                if (!isSignedIn) {
                    item {
                        Surface(
                            color = ConsoleSurface,
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
                                Text(
                                    text = "Local Studio Mode active (no login required). Optional Google Sign-In enables cloud backup & sharing.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                                )
                                OutlinedButton(onClick = onSignInOptional) {
                                    Text("Sign In")
                                }
                            }
                        }
                    }
                } else if (list.isEmpty()) {
                    item {
                        Text("No cloud backups yet. Tap 'Backup' above to save your session.", color = TextSecondary)
                    }
                } else {
                    items(list, key = { it.id }) { cp ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = PanelSurface),
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
                                    Text(cp.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                    Text(
                                        "${cp.bpm.toInt()} BPM • ${cp.musicalKey} • ${cp.trackCount} Tracks • v${cp.versionNumber}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(onClick = {
                                        onLoadVersions(cp.id)
                                        showVersionDialogFor = cp
                                    }) { Text("History") }
                                    Button(onClick = { onLoadJsonProject(cp.snapshotPayload, cp.title) }) {
                                        Text("Open")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Local Offline Library
        if (localProjects.isNotEmpty()) {
            item {
                Text("Local Offline Library", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            }
            items(localProjects, key = { it.id }) { lp ->
                Surface(
                    color = ConsoleSurface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ConsoleBorder, RoundedCornerShape(12.dp))
                        .clickable { onLoadJsonProject(lp.payloadJson, lp.title) }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(lp.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text("${lp.bpm.toInt()} BPM • ${lp.musicalKey} • v${lp.versionNumber}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                        Icon(Icons.Rounded.FolderOpen, contentDescription = "Open Local Project", tint = CyanSignal)
                    }
                }
            }
        }
    }

    showVersionDialogFor?.let { cp ->
        AlertDialog(
            onDismissRequest = { showVersionDialogFor = null },
            containerColor = PanelSurface,
            title = { Text("Version History: ${cp.title}", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (selectedVersions.isEmpty()) {
                        Text("Loading version snapshots...", color = TextSecondary)
                    } else {
                        selectedVersions.forEach { ver ->
                            Surface(
                                color = ElevatedRack,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onLoadJsonProject(ver.snapshotPayload, "${cp.title} v${ver.versionNumber}")
                                        showVersionDialogFor = null
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("v${ver.versionNumber}: ${ver.label}", color = TextPrimary)
                                    Text("Restore", color = CyanSignal)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showVersionDialogFor = null }) { Text("Close") }
            }
        )
    }
}
