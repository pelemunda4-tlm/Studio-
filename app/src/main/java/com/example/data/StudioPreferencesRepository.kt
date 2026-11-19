package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.studioDataStore by preferencesDataStore(name = "trueworld_studio_prefs")

data class StudioSettings(
    val bufferSizeFrames: Int = 256,
    val precision64BitSumming: Boolean = true,
    val autosaveEnabled: Boolean = true,
    val shortcutsMap: Map<String, String> = DEFAULT_SHORTCUTS
) {
    companion object {
        val DEFAULT_SHORTCUTS = mapOf(
            "Play / Pause" to "Space",
            "Toggle Record" to "R",
            "Toggle Loop" to "L",
            "Metronome" to "M",
            "Undo Last Edit" to "Ctrl+Z",
            "Open AI Assistant" to "Ctrl+K"
        )
    }
}

class StudioPreferencesRepository(private val context: Context) {
    private val keyBuffer = intPreferencesKey("buffer_size_frames")
    private val key64Bit = booleanPreferencesKey("precision_64_bit")
    private val keyAutosave = booleanPreferencesKey("autosave_enabled")
    private val keyShortcuts = stringPreferencesKey("custom_shortcuts_kv")

    val settingsFlow: Flow<StudioSettings> = context.studioDataStore.data.map { prefs ->
        val rawShortcuts = prefs[keyShortcuts]
        val parsedShortcuts = if (rawShortcuts.isNullOrBlank()) {
            StudioSettings.DEFAULT_SHORTCUTS
        } else {
            rawShortcuts.split("|").mapNotNull { entry ->
                val parts = entry.split("=")
                if (parts.size == 2) parts[0] to parts[1] else null
            }.toMap().ifEmpty { StudioSettings.DEFAULT_SHORTCUTS }
        }
        StudioSettings(
            bufferSizeFrames = prefs[keyBuffer] ?: 256,
            precision64BitSumming = prefs[key64Bit] ?: true,
            autosaveEnabled = prefs[keyAutosave] ?: true,
            shortcutsMap = parsedShortcuts
        )
    }

    suspend fun setBufferSize(frames: Int) {
        context.studioDataStore.edit { it[keyBuffer] = frames }
    }

    suspend fun set64BitSumming(enabled: Boolean) {
        context.studioDataStore.edit { it[key64Bit] = enabled }
    }

    suspend fun setAutosaveEnabled(enabled: Boolean) {
        context.studioDataStore.edit { it[keyAutosave] = enabled }
    }

    suspend fun updateShortcut(action: String, keyCombo: String) {
        context.studioDataStore.edit { prefs ->
            val current = prefs[keyShortcuts]?.split("|")?.mapNotNull {
                val p = it.split("="); if (p.size == 2) p[0] to p[1] else null
            }?.toMap()?.toMutableMap() ?: StudioSettings.DEFAULT_SHORTCUTS.toMutableMap()
            current[action] = keyCombo
            prefs[keyShortcuts] = current.entries.joinToString("|") { "${it.key}=${it.value}" }
        }
    }
}
