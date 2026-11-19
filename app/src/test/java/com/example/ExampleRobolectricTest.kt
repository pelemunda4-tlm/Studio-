package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.audio.MasteringProcessor
import com.example.data.ProjectSerializer
import com.example.model.IndianInstrument
import com.example.model.MeterSnapshot
import com.example.model.PluginType
import com.example.model.VocalChainType
import com.example.ui.screens.MasteringStudioScreen
import com.example.ui.screens.MixerConsoleScreen
import com.example.ui.screens.PluginBrowserScreen
import com.example.ui.screens.VocalEditorScreen
import com.example.ui.theme.TrueworldStudioTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun appName_matchesTrueworldStudio() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Trueworld Studio", appName)
    }

    @Test
    fun pluginBrowser_displaysAndInsertsBuiltInPlugin() {
        val project = ProjectSerializer.defaultProject()
        var inserted: PluginType? = null

        composeRule.setContent {
            TrueworldStudioTheme {
                PluginBrowserScreen(
                    project = project,
                    selectedTrackId = project.tracks.first().id,
                    onSelectTrack = {},
                    onInsertPlugin = { inserted = it },
                    onUpdatePlugin = { _, _ -> },
                    onRemovePlugin = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithTag("plugin_browser_list")
            .performScrollToNode(androidx.compose.ui.test.hasTestTag("insert_plugin_EQ_3_BAND"))
        composeRule.onNodeWithTag("insert_plugin_EQ_3_BAND").assertIsDisplayed()
        composeRule.onNodeWithTag("insert_plugin_EQ_3_BAND").performClick()
        assertEquals(PluginType.EQ_3_BAND, inserted)
    }

    @Test
    fun mixerConsole_rendersTracksAndAddsBus() {
        val project = ProjectSerializer.defaultProject()
        var addedBus = false

        composeRule.setContent {
            TrueworldStudioTheme {
                MixerConsoleScreen(
                    project = project,
                    selectedTrackId = project.tracks.first().id,
                    trackMeters = emptyMap(),
                    masterMeters = MeterSnapshot(),
                    onSelectTrack = {},
                    onAddTrack = { if (it.name == "BUS") addedBus = true },
                    onUpdateTrack = { _, _, _ -> },
                    onFreezeTrack = {},
                    onOpenPluginRack = {}
                )
            }
        }

        composeRule.onNodeWithTag("mixer_add_bus_btn").performClick()
        assertTrue(addedBus)
    }

    @Test
    fun vocalEditor_switchesToSambalpuriVocalChain() {
        val project = ProjectSerializer.defaultProject()
        var chosenChain: VocalChainType? = null

        composeRule.setContent {
            TrueworldStudioTheme {
                VocalEditorScreen(
                    project = project,
                    vocalChain = VocalChainType.CLEAN_VOCAL,
                    vocalParams = VocalChainType.CLEAN_VOCAL.defaultParams,
                    vocalTakes = emptyList(),
                    isRecording = false,
                    audioReport = null,
                    onToggleRecord = {},
                    onSelectChain = { chosenChain = it },
                    onUpdateParams = {},
                    onUpdatePitchOffset = { _, _, _ -> },
                    onAuditionTake = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithTag("vocal_chain_SAMBALPURI_VOCAL").performClick()
        assertEquals(VocalChainType.SAMBALPURI_VOCAL, chosenChain)
    }

    @Test
    fun masteringStudio_displaysPresetsAndTriggersBounce() {
        var bounceCalled = false
        composeRule.setContent {
            TrueworldStudioTheme {
                MasteringStudioScreen(
                    config = MasteringProcessor.MASTERING_PRESETS.first(),
                    masterMeters = MeterSnapshot(),
                    onSelectPreset = {},
                    onUpdateConfig = {},
                    onImportReference = {},
                    onBounceWav = { bounceCalled = true }
                )
            }
        }

        composeRule.onNodeWithTag("mastering_bounce_btn").performClick()
        assertTrue(bounceCalled)
        assertEquals(13, IndianInstrument.entries.size)
    }

    @Test
    fun projectSerializer_roundTripPreservesSessionAndPatterns() {
        val proj = ProjectSerializer.defaultProject()
        val json = ProjectSerializer.projectToJson(proj)
        val restored = ProjectSerializer.projectFromJson(json)
        assertEquals(proj.title, restored?.title)
        assertEquals(proj.tracks.size, restored?.tracks?.size)
        assertEquals(proj.bpm, restored?.bpm ?: 0.0, 0.01)

        val pat = ProjectSerializer.createPresetPatterns().first()
        val patJson = ProjectSerializer.patternToJson(pat)
        val restoredPat = ProjectSerializer.patternFromJson(patJson)
        assertEquals(pat.name, restoredPat?.name)
        assertEquals(pat.lanes.size, restoredPat?.lanes?.size)
    }

    @Test
    fun homeDashboard_rendersAndOperatesWithoutLogin() {
        val proj = ProjectSerializer.defaultProject()
        var navigatedToArrangement = false
        var savedLocally = false

        composeRule.setContent {
            TrueworldStudioTheme {
                com.example.ui.screens.HomeDashboardScreen(
                    project = proj,
                    userName = "Producer",
                    isSignedIn = false,
                    autosave = null,
                    localProjects = emptyList(),
                    cloudProjectsState = com.example.viewmodel.UiState.Success(emptyList()),
                    selectedVersions = emptyList(),
                    lastShareCode = null,
                    onRestoreAutosave = {},
                    onDismissAutosave = {},
                    onCreateFromTemplate = {},
                    onSaveBackup = { savedLocally = true },
                    onLoadJsonProject = { _, _ -> },
                    onLoadVersions = {},
                    onShareProject = {},
                    onImportShareCode = {},
                    onExportZip = {},
                    onExportWav = {},
                    onNavigateScreen = { if (it == com.example.model.DawScreen.ARRANGEMENT) navigatedToArrangement = true },
                    onSignInOptional = {},
                    onSignOut = {}
                )
            }
        }

        composeRule.onNodeWithTag("open_arrangement_btn").performClick()
        assertTrue(navigatedToArrangement)
        composeRule.onNodeWithTag("home_dashboard_list")
            .performScrollToNode(androidx.compose.ui.test.hasTestTag("backup_cloud_btn"))
        composeRule.onNodeWithTag("backup_cloud_btn").performClick()
        assertTrue(savedLocally)
    }

    @Test
    @Config(qualifiers = "w1024dp-h576dp-land")
    fun arrangementTimeline_rendersDesktopLandscapePanelsAndQuantizesMidi() {
        val proj = ProjectSerializer.defaultProject()
        var quantized = false
        var addedTrack = false

        composeRule.setContent {
            TrueworldStudioTheme {
                com.example.ui.screens.ArrangementTimelineScreen(
                    project = proj,
                    selectedTrackId = proj.tracks.first().id,
                    playheadBeat = 0.0,
                    timelineZoom = 1.0f,
                    showLeftPanel = true,
                    showBottomPanel = true,
                    showRightPanel = true,
                    onSelectTrack = {},
                    onAddTrack = { addedTrack = true },
                    onUpdateTrack = { _, _, _ -> },
                    onFreezeTrack = {},
                    onSetZoom = {},
                    onAddMarker = { _, _ -> },
                    onAddSection = { _, _, _ -> },
                    onSeekBeat = {},
                    onOpenPianoRoll = {},
                    onRelinkClip = {},
                    onQuantizeMidi = { quantized = true }
                )
            }
        }

        composeRule.onNodeWithTag("add_track_btn").assertIsDisplayed()
        composeRule.onNodeWithText("Inspector").assertIsDisplayed()
        composeRule.onNodeWithText("VEL").assertIsDisplayed()
        composeRule.onNodeWithTag("midi_quantize_btn").performClick()
        assertTrue(quantized)
        composeRule.onNodeWithTag("bottom_tab_MIXER_DOCK").performClick()
        composeRule.onNodeWithTag("mixer_add_bus_btn").performClick()
        assertTrue(addedTrack)
    }
}
