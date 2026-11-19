package com.example.data

import android.util.Log
import com.example.audio.AudioAnalysisReport
import com.example.model.ChordEvent
import com.example.model.DawProject
import com.example.model.MidiNote
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "AiMusicAssistantRepo"

data class AiMusicSuggestion(
    val title: String,
    val summary: String,
    val suggestedChords: List<ChordEvent> = emptyList(),
    val suggestedMelody: List<MidiNote> = emptyList(),
    val suggestedRhythmSteps: List<Int> = emptyList(),
    val mixingTips: List<String> = emptyList(),
    val masteringTips: List<String> = emptyList(),
    val detectedSections: List<String> = emptyList(),
    val separationPlan: String = "",
    val noiseAdvice: String = ""
)

class AiMusicAssistantRepository {

    private val assistantSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Short title of the musical suggestion"),
            "summary" to Schema.string(description = "Concise producer summary"),
            "chordSymbols" to Schema.array(
                items = Schema.string(),
                description = "Up to 4 editable chord symbols matching the project key/scale"
            ),
            "melodyMidiPitches" to Schema.array(
                items = Schema.integer(),
                description = "Up to 8 MIDI note numbers (48..84) for an editable melody hook"
            ),
            "rhythmAccentSteps" to Schema.array(
                items = Schema.integer(),
                description = "16 step velocities (0..100) for an editable percussion groove"
            ),
            "mixingTips" to Schema.array(
                items = Schema.string(),
                description = "Actionable non-destructive mixing suggestions"
            ),
            "masteringTips" to Schema.array(
                items = Schema.string(),
                description = "Actionable mastering target suggestions"
            ),
            "sections" to Schema.array(
                items = Schema.string(),
                description = "Song section labels such as Intro, Mukhda, Antara, Bridge"
            ),
            "separationPlan" to Schema.string(description = "Vocal vs instrument stem separation DSP recommendation"),
            "noiseAdvice" to Schema.string(description = "Noise floor cleanup suggestion")
        ),
        optionalProperties = listOf("separationPlan", "noiseAdvice")
    )

    private val structuredModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = assistantSchema
            }
        )
    }

    suspend fun generateStudioSuggestion(
        userPrompt: String,
        project: DawProject,
        audioReport: AudioAnalysisReport? = null
    ): Result<AiMusicSuggestion> {
        return try {
            val contextPrompt = buildString {
                appendLine("You are the optional AI Music Assistant inside Trueworld Music Studio DAW.")
                appendLine("Always provide editable suggestions; never claim to overwrite original audio.")
                appendLine("Project: '${project.title}', BPM=${project.bpm}, TimeSig=${project.timeSignature.label}, Key=${project.musicalKey}, Scale=${project.scale.displayName}.")
                appendLine("Tracks (${project.tracks.size}): ${project.tracks.joinToString { "${it.name}(${it.type.label}, vol=${(it.volume * 100).toInt()}%)" }}.")
                appendLine("Mastering Target: ${project.masteringConfig.presetName}, Target LUFS=${project.masteringConfig.targetLufs}.")
                if (audioReport != null) {
                    appendLine("Measured Audio DSP Analysis: BPM=${audioReport.detectedBpm}, Key=${audioReport.detectedKey} ${audioReport.detectedScale.displayName}, Peak=${audioReport.peakDb}dB, Integrated LUFS=${audioReport.integratedLufs}, NoiseFloor=${audioReport.noiseFloorDb}dB, TranscribedMidiNotes=${audioReport.transcribedMidiNotes.size}.")
                }
                appendLine("User request: $userPrompt")
            }

            val response = withTimeout(60_000L) {
                structuredModel.generateContent(contextPrompt)
            }
            val rawJson = response.text ?: throw IllegalStateException("AI Assistant returned an empty response.")
            val obj = JSONObject(rawJson)

            val chordArr = obj.optJSONArray("chordSymbols")
            val chords = mutableListOf<ChordEvent>()
            if (chordArr != null) {
                for (i in 0 until chordArr.length()) {
                    val sym = chordArr.optString(i, "C")
                    chords.add(
                        ChordEvent(
                            id = "ai_ch_$i",
                            symbol = sym,
                            midiNotes = chordSymbolToMidi(sym),
                            beatPosition = i * 4.0,
                            durationBeats = 4.0
                        )
                    )
                }
            }

            val melArr = obj.optJSONArray("melodyMidiPitches")
            val melody = mutableListOf<MidiNote>()
            if (melArr != null) {
                for (i in 0 until melArr.length()) {
                    val pitch = melArr.optInt(i, 60).coerceIn(36, 96)
                    melody.add(
                        MidiNote(
                            id = "ai_mel_$i",
                            pitch = pitch,
                            startBeat = i * 0.5,
                            durationBeats = 0.5,
                            velocity = 100
                        )
                    )
                }
            }

            val stepArr = obj.optJSONArray("rhythmAccentSteps")
            val steps = mutableListOf<Int>()
            if (stepArr != null) {
                for (i in 0 until stepArr.length()) {
                    steps.add(stepArr.optInt(i, 0).coerceIn(0, 100))
                }
            }

            val mixArr = obj.optJSONArray("mixingTips")
            val mixTips = if (mixArr != null) (0 until mixArr.length()).map { mixArr.getString(it) } else emptyList()

            val masterArr = obj.optJSONArray("masteringTips")
            val masterTips = if (masterArr != null) (0 until masterArr.length()).map { masterArr.getString(it) } else emptyList()

            val secArr = obj.optJSONArray("sections")
            val sections = if (secArr != null) (0 until secArr.length()).map { secArr.getString(it) } else emptyList()

            Result.success(
                AiMusicSuggestion(
                    title = obj.optString("title", "AI Studio Suggestion"),
                    summary = obj.optString("summary", ""),
                    suggestedChords = chords,
                    suggestedMelody = melody,
                    suggestedRhythmSteps = steps,
                    mixingTips = mixTips,
                    masteringTips = masterTips,
                    detectedSections = sections,
                    separationPlan = obj.optString("separationPlan", ""),
                    noiseAdvice = obj.optString("noiseAdvice", "")
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI generation timed out", e)
            Result.failure(Exception("AI Music Assistant timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI generation failed", e)
            Result.failure(e)
        }
    }

    private fun chordSymbolToMidi(symbol: String): List<Int> {
        val rootMap = mapOf(
            "C" to 60, "C#" to 61, "Db" to 61, "D" to 62, "D#" to 63, "Eb" to 63,
            "E" to 64, "F" to 65, "F#" to 66, "Gb" to 66, "G" to 67, "G#" to 68,
            "Ab" to 68, "A" to 69, "A#" to 70, "Bb" to 70, "B" to 71
        )
        val clean = symbol.trim()
        val rootKey = if (clean.length >= 2 && (clean[1] == '#' || clean[1] == 'b')) clean.substring(0, 2) else clean.take(1)
        val root = rootMap[rootKey] ?: 60
        return when {
            clean.contains("m7", ignoreCase = false) -> listOf(root, root + 3, root + 7, root + 10)
            clean.contains("maj7", ignoreCase = true) -> listOf(root, root + 4, root + 7, root + 11)
            clean.contains("m", ignoreCase = false) && !clean.contains("maj") -> listOf(root, root + 3, root + 7)
            clean.contains("7") -> listOf(root, root + 4, root + 7, root + 10)
            else -> listOf(root, root + 4, root + 7)
        }
    }
}
