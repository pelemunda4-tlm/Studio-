package com.example.data

import android.content.Context
import android.net.Uri
import com.example.audio.MultitrackAudioEngine
import com.example.audio.WavAiffCodec
import com.example.model.DawProject
import com.example.model.RhythmPattern
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ProjectArchiveManager {

    fun exportProjectZip(
        outputStream: OutputStream,
        project: DawProject,
        activePattern: RhythmPattern?,
        audioEngine: MultitrackAudioEngine
    ) {
        val (mixL, mixR) = audioEngine.bounceProjectToStereo(project, activePattern, bars = 4)
        val mixWav = WavAiffCodec.encodeWavBytes(mixL, mixR, audioEngine.sampleRate, float32 = false)

        ZipOutputStream(outputStream).use { zip ->
            zip.putNextEntry(ZipEntry("project.json"))
            zip.write(ProjectSerializer.projectToJson(project).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            if (activePattern != null) {
                zip.putNextEntry(ZipEntry("rhythm_pattern.json"))
                zip.write(ProjectSerializer.patternToJson(activePattern).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            zip.putNextEntry(ZipEntry("stems/master_bounce.wav"))
            zip.write(mixWav)
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("manifest.txt"))
            val manifest = buildString {
                appendLine("TRUEWORLD MUSIC STUDIO - PROJECT ARCHIVE")
                appendLine("Brand: Trueworld Entertainment Official")
                appendLine("Project: ${project.title}")
                appendLine("BPM: ${project.bpm} | Key: ${project.musicalKey} ${project.scale.displayName}")
                appendLine("Time Signature: ${project.timeSignature.label}")
                appendLine("Tracks: ${project.tracks.size}")
            }
            zip.write(manifest.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    fun importProjectZip(inputStream: InputStream): Pair<DawProject?, RhythmPattern?> {
        var project: DawProject? = null
        var pattern: RhythmPattern? = null
        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when (entry.name) {
                    "project.json" -> {
                        val json = zip.readBytes().toString(Charsets.UTF_8)
                        project = ProjectSerializer.projectFromJson(json)
                    }
                    "rhythm_pattern.json" -> {
                        val json = zip.readBytes().toString(Charsets.UTF_8)
                        pattern = ProjectSerializer.patternFromJson(json)
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return project to pattern
    }

    fun relinkMissingClips(
        context: Context,
        project: DawProject,
        clipId: String,
        newUri: Uri,
        audioEngine: MultitrackAudioEngine
    ): DawProject {
        val decoded = context.contentResolver.openInputStream(newUri)?.use { stream ->
            WavAiffCodec.decodeAudioStream(stream)
        } ?: return project

        audioEngine.registerAudioBuffer(clipId, decoded.left, decoded.right)
        val preview = buildWaveformPreview(decoded.left, 48)
        val updatedTracks = project.tracks.map { track ->
            track.copy(
                clips = track.clips.map { clip ->
                    if (clip.id == clipId) {
                        clip.copy(
                            sourcePath = newUri.toString(),
                            missingSource = false,
                            waveformPreview = preview
                        )
                    } else clip
                }
            )
        }
        return project.copy(tracks = updatedTracks)
    }

    fun buildWaveformPreview(samples: FloatArray, buckets: Int = 48): List<Float> {
        if (samples.isEmpty()) return List(buckets) { 0f }
        val step = (samples.size / buckets).coerceAtLeast(1)
        return List(buckets) { idx ->
            val start = idx * step
            val end = (start + step).coerceAtMost(samples.size)
            var maxVal = 0f
            for (i in start until end) {
                val a = kotlin.math.abs(samples[i])
                if (a > maxVal) maxVal = a
            }
            maxVal.coerceIn(0f, 1f)
        }
    }
}
