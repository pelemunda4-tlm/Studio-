package com.example.data

import com.example.audio.PluginCatalog
import com.example.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object ProjectSerializer {

    fun projectToJson(project: DawProject): String {
        val root = JSONObject()
        root.put("id", project.id)
        root.put("title", project.title)
        root.put("bpm", project.bpm)
        root.put("tsNum", project.timeSignature.numerator)
        root.put("tsDen", project.timeSignature.denominator)
        root.put("tsLabel", project.timeSignature.label)
        root.put("musicalKey", project.musicalKey)
        root.put("scale", project.scale.name)
        root.put("loopEnabled", project.loopEnabled)
        root.put("loopStartBeat", project.loopStartBeat)
        root.put("loopEndBeat", project.loopEndBeat)
        root.put("snapEnabled", project.snapEnabled)
        root.put("gridResolution", project.gridResolution.name)
        root.put("versionNumber", project.versionNumber)
        root.put("notesSummary", project.notesSummary)

        val tracksArr = JSONArray()
        for (t in project.tracks) {
            val tj = JSONObject()
            tj.put("id", t.id)
            tj.put("name", t.name)
            tj.put("type", t.type.name)
            tj.put("colorArgb", t.colorArgb)
            tj.put("volume", t.volume.toDouble())
            tj.put("pan", t.pan.toDouble())
            tj.put("muted", t.muted)
            tj.put("soloed", t.soloed)
            tj.put("armed", t.armed)
            tj.put("monitoring", t.monitoring)
            tj.put("frozen", t.frozen)
            tj.put("mono", t.mono)
            tj.put("inputChannel", t.inputChannel)
            tj.put("outputChannel", t.outputChannel)
            tj.put("delayCompMs", t.delayCompMs.toDouble())
            tj.put("vcaGroupId", t.vcaGroupId ?: "")
            tj.put("busTargetId", t.busTargetId ?: "")
            tj.put("folderId", t.folderId ?: "")
            tj.put("sidechainSourceId", t.sidechainSourceId ?: "")
            tj.put("instrumentName", t.instrumentName)

            val pluginsArr = JSONArray()
            for (p in t.plugins) {
                val pj = JSONObject()
                pj.put("id", p.id)
                pj.put("type", p.type.name)
                pj.put("presetName", p.presetName)
                pj.put("bypassed", p.bypassed)
                pj.put("wetDry", p.wetDry.toDouble())
                val paramsObj = JSONObject()
                p.params.forEach { (k, v) -> paramsObj.put(k, v.toDouble()) }
                pj.put("params", paramsObj)
                pluginsArr.put(pj)
            }
            tj.put("plugins", pluginsArr)

            val notesArr = JSONArray()
            for (n in t.midiNotes) {
                notesArr.put(JSONObject().apply {
                    put("id", n.id)
                    put("pitch", n.pitch)
                    put("startBeat", n.startBeat)
                    put("durationBeats", n.durationBeats)
                    put("velocity", n.velocity)
                    put("probability", n.probability.toDouble())
                    put("laneIndex", n.laneIndex)
                })
            }
            tj.put("midiNotes", notesArr)

            val clipsArr = JSONArray()
            for (c in t.clips) {
                clipsArr.put(JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("startBeat", c.startBeat)
                    put("lengthBeats", c.lengthBeats)
                    put("gainDb", c.gainDb.toDouble())
                    put("sourcePath", c.sourcePath)
                    put("missingSource", c.missingSource)
                    put("muted", c.muted)
                })
            }
            tj.put("clips", clipsArr)
            tracksArr.put(tj)
        }
        root.put("tracks", tracksArr)

        val chordsArr = JSONArray()
        for (c in project.chordTrack) {
            chordsArr.put(JSONObject().apply {
                put("id", c.id)
                put("symbol", c.symbol)
                put("beatPosition", c.beatPosition)
                put("durationBeats", c.durationBeats)
                put("midiNotes", JSONArray(c.midiNotes))
            })
        }
        root.put("chordTrack", chordsArr)

        val sectionsArr = JSONArray()
        for (s in project.sections) {
            sectionsArr.put(JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("startBar", s.startBar)
                put("lengthBars", s.lengthBars)
                put("colorArgb", s.colorArgb)
            })
        }
        root.put("sections", sectionsArr)

        val markersArr = JSONArray()
        for (m in project.markers) {
            markersArr.put(JSONObject().apply {
                put("id", m.id)
                put("label", m.label)
                put("beatPosition", m.beatPosition)
            })
        }
        root.put("markers", markersArr)

        val tempoArr = JSONArray()
        for (tp in project.tempoTrack) {
            tempoArr.put(JSONObject().apply {
                put("beatPosition", tp.beatPosition)
                put("bpm", tp.bpm)
            })
        }
        root.put("tempoTrack", tempoArr)

        val m = project.masteringConfig
        root.put("mastering", JSONObject().apply {
            put("presetName", m.presetName)
            put("linearPhaseEqEnabled", m.linearPhaseEqEnabled)
            put("lowShelfDb", m.lowShelfDb.toDouble())
            put("midPresenceDb", m.midPresenceDb.toDouble())
            put("airShelfDb", m.airShelfDb.toDouble())
            put("dynamicEqAmount", m.dynamicEqAmount.toDouble())
            put("multibandDrive", m.multibandDrive.toDouble())
            put("stereoWidth", m.stereoWidth.toDouble())
            put("harmonicExciter", m.harmonicExciter.toDouble())
            put("tapeSaturation", m.tapeSaturation.toDouble())
            put("clipperDrive", m.clipperDrive.toDouble())
            put("truePeakCeilingDb", m.truePeakCeilingDb.toDouble())
            put("targetLufs", m.targetLufs.toDouble())
        })

        return root.toString()
    }

    fun projectFromJson(jsonStr: String): DawProject? {
        return try {
            val root = JSONObject(jsonStr)
            val scaleName = root.optString("scale", MusicalScale.MAJOR.name)
            val scale = MusicalScale.entries.firstOrNull { it.name == scaleName } ?: MusicalScale.MAJOR
            val tracks = mutableListOf<DawTrack>()
            val tracksArr = root.optJSONArray("tracks") ?: JSONArray()
            for (i in 0 until tracksArr.length()) {
                val tj = tracksArr.getJSONObject(i)
                val tType = TrackType.entries.firstOrNull { it.name == tj.optString("type") } ?: TrackType.AUDIO

                val plugins = mutableListOf<PluginInstance>()
                val pArr = tj.optJSONArray("plugins") ?: JSONArray()
                for (j in 0 until pArr.length()) {
                    val pj = pArr.getJSONObject(j)
                    val pType = PluginType.entries.firstOrNull { it.name == pj.optString("type") } ?: continue
                    val paramsMap = mutableMapOf<String, Float>()
                    val pObj = pj.optJSONObject("params")
                    pObj?.keys()?.forEach { k -> paramsMap[k] = pObj.optDouble(k, 0.0).toFloat() }
                    plugins.add(
                        PluginInstance(
                            id = pj.optString("id", UUID.randomUUID().toString()),
                            type = pType,
                            presetName = pj.optString("presetName", "Default"),
                            bypassed = pj.optBoolean("bypassed", false),
                            wetDry = pj.optDouble("wetDry", 1.0).toFloat(),
                            params = paramsMap
                        )
                    )
                }

                val notes = mutableListOf<MidiNote>()
                val nArr = tj.optJSONArray("midiNotes") ?: JSONArray()
                for (j in 0 until nArr.length()) {
                    val nj = nArr.getJSONObject(j)
                    notes.add(
                        MidiNote(
                            id = nj.optString("id", "n_$j"),
                            pitch = nj.optInt("pitch", 60),
                            startBeat = nj.optDouble("startBeat", 0.0),
                            durationBeats = nj.optDouble("durationBeats", 1.0),
                            velocity = nj.optInt("velocity", 100),
                            probability = nj.optDouble("probability", 1.0).toFloat(),
                            laneIndex = nj.optInt("laneIndex", 0)
                        )
                    )
                }

                val clips = mutableListOf<AudioClip>()
                val cArr = tj.optJSONArray("clips") ?: JSONArray()
                for (j in 0 until cArr.length()) {
                    val cj = cArr.getJSONObject(j)
                    clips.add(
                        AudioClip(
                            id = cj.optString("id", "c_$j"),
                            name = cj.optString("name", "Audio Clip"),
                            startBeat = cj.optDouble("startBeat", 0.0),
                            lengthBeats = cj.optDouble("lengthBeats", 4.0),
                            gainDb = cj.optDouble("gainDb", 0.0).toFloat(),
                            sourcePath = cj.optString("sourcePath", ""),
                            missingSource = cj.optBoolean("missingSource", false),
                            muted = cj.optBoolean("muted", false)
                        )
                    )
                }

                tracks.add(
                    DawTrack(
                        id = tj.optString("id", "trk_$i"),
                        name = tj.optString("name", "Track ${i + 1}"),
                        type = tType,
                        colorArgb = tj.optLong("colorArgb", 0xFF22C55EL),
                        volume = tj.optDouble("volume", 0.8).toFloat(),
                        pan = tj.optDouble("pan", 0.0).toFloat(),
                        muted = tj.optBoolean("muted", false),
                        soloed = tj.optBoolean("soloed", false),
                        armed = tj.optBoolean("armed", false),
                        monitoring = tj.optBoolean("monitoring", false),
                        frozen = tj.optBoolean("frozen", false),
                        mono = tj.optBoolean("mono", false),
                        inputChannel = tj.optString("inputChannel", "In 1-2"),
                        outputChannel = tj.optString("outputChannel", "Main Out"),
                        delayCompMs = tj.optDouble("delayCompMs", 0.0).toFloat(),
                        vcaGroupId = tj.optString("vcaGroupId").ifBlank { null },
                        busTargetId = tj.optString("busTargetId").ifBlank { null },
                        folderId = tj.optString("folderId").ifBlank { null },
                        sidechainSourceId = tj.optString("sidechainSourceId").ifBlank { null },
                        plugins = plugins,
                        midiNotes = notes,
                        clips = clips,
                        instrumentName = tj.optString("instrumentName", "Grand Piano")
                    )
                )
            }

            val chords = mutableListOf<ChordEvent>()
            val chArr = root.optJSONArray("chordTrack") ?: JSONArray()
            for (i in 0 until chArr.length()) {
                val cj = chArr.getJSONObject(i)
                val mArr = cj.optJSONArray("midiNotes") ?: JSONArray()
                val mList = (0 until mArr.length()).map { mArr.getInt(it) }
                chords.add(
                    ChordEvent(
                        id = cj.optString("id", "ch_$i"),
                        symbol = cj.optString("symbol", "C"),
                        midiNotes = mList,
                        beatPosition = cj.optDouble("beatPosition", 0.0),
                        durationBeats = cj.optDouble("durationBeats", 4.0)
                    )
                )
            }

            val sections = mutableListOf<SongSection>()
            val sArr = root.optJSONArray("sections") ?: JSONArray()
            for (i in 0 until sArr.length()) {
                val sj = sArr.getJSONObject(i)
                sections.add(
                    SongSection(
                        id = sj.optString("id", "sec_$i"),
                        name = sj.optString("name", "Section"),
                        startBar = sj.optInt("startBar", 1),
                        lengthBars = sj.optInt("lengthBars", 4),
                        colorArgb = sj.optLong("colorArgb", 0xFF00E5FFL)
                    )
                )
            }

            val markers = mutableListOf<TimelineMarker>()
            val mArr = root.optJSONArray("markers") ?: JSONArray()
            for (i in 0 until mArr.length()) {
                val mj = mArr.getJSONObject(i)
                markers.add(
                    TimelineMarker(
                        id = mj.optString("id", "mk_$i"),
                        label = mj.optString("label", "Marker"),
                        beatPosition = mj.optDouble("beatPosition", 0.0)
                    )
                )
            }

            val tempos = mutableListOf<TempoPoint>()
            val tpArr = root.optJSONArray("tempoTrack") ?: JSONArray()
            for (i in 0 until tpArr.length()) {
                val tpj = tpArr.getJSONObject(i)
                tempos.add(TempoPoint(tpj.optDouble("beatPosition", 0.0), tpj.optDouble("bpm", 116.0)))
            }
            val gridName = root.optString("gridResolution", GridResolution.EIGHTH.name)
            val gridRes = GridResolution.entries.firstOrNull { it.name == gridName } ?: GridResolution.EIGHTH

            val mj = root.optJSONObject("mastering")
            val mastering = if (mj != null) {
                MasteringConfig(
                    presetName = mj.optString("presetName", "Streaming (-14 LUFS)"),
                    linearPhaseEqEnabled = mj.optBoolean("linearPhaseEqEnabled", true),
                    lowShelfDb = mj.optDouble("lowShelfDb", 0.5).toFloat(),
                    midPresenceDb = mj.optDouble("midPresenceDb", 0.8).toFloat(),
                    airShelfDb = mj.optDouble("airShelfDb", 1.2).toFloat(),
                    dynamicEqAmount = mj.optDouble("dynamicEqAmount", 0.35).toFloat(),
                    multibandDrive = mj.optDouble("multibandDrive", 0.4).toFloat(),
                    stereoWidth = mj.optDouble("stereoWidth", 1.12).toFloat(),
                    harmonicExciter = mj.optDouble("harmonicExciter", 0.25).toFloat(),
                    tapeSaturation = mj.optDouble("tapeSaturation", 0.30).toFloat(),
                    clipperDrive = mj.optDouble("clipperDrive", 0.2).toFloat(),
                    truePeakCeilingDb = mj.optDouble("truePeakCeilingDb", -1.0).toFloat(),
                    targetLufs = mj.optDouble("targetLufs", -14.0).toFloat()
                )
            } else MasteringConfig()

            DawProject(
                id = root.optString("id", UUID.randomUUID().toString()),
                title = root.optString("title", "Untitled Studio Session"),
                bpm = root.optDouble("bpm", 116.0),
                timeSignature = TimeSignature(
                    root.optInt("tsNum", 4),
                    root.optInt("tsDen", 4),
                    root.optString("tsLabel", "4/4")
                ),
                musicalKey = root.optString("musicalKey", "C"),
                scale = scale,
                tracks = tracks,
                sections = sections,
                markers = markers,
                chordTrack = chords,
                tempoTrack = tempos.ifEmpty { listOf(TempoPoint(0.0, root.optDouble("bpm", 116.0))) },
                loopEnabled = root.optBoolean("loopEnabled", true),
                loopStartBeat = root.optDouble("loopStartBeat", 0.0),
                loopEndBeat = root.optDouble("loopEndBeat", 16.0),
                snapEnabled = root.optBoolean("snapEnabled", true),
                gridResolution = gridRes,
                masteringConfig = mastering,
                versionNumber = root.optInt("versionNumber", 1),
                notesSummary = root.optString("notesSummary", "")
            )
        } catch (_: Exception) {
            null
        }
    }

    fun patternToJson(pattern: RhythmPattern): String {
        val obj = JSONObject()
        obj.put("id", pattern.id)
        obj.put("name", pattern.name)
        obj.put("style", pattern.style.name)
        obj.put("tsNum", pattern.timeSignature.numerator)
        obj.put("tsDen", pattern.timeSignature.denominator)
        obj.put("bpm", pattern.bpm)
        obj.put("stepCount", pattern.stepCount)
        obj.put("swing", pattern.swing.toDouble())
        val lanesArr = JSONArray()
        for (lane in pattern.lanes) {
            val lObj = JSONObject()
            lObj.put("instrument", lane.instrument.name)
            lObj.put("muted", lane.muted)
            lObj.put("steps", JSONArray(lane.steps.map { it.toDouble() }))
            lanesArr.put(lObj)
        }
        obj.put("lanes", lanesArr)
        return obj.toString()
    }

    fun patternFromJson(jsonStr: String): RhythmPattern? {
        return try {
            val obj = JSONObject(jsonStr)
            val style = RhythmStyle.entries.firstOrNull { it.name == obj.optString("style") } ?: RhythmStyle.CUSTOM
            val num = obj.optInt("tsNum", 4)
            val den = obj.optInt("tsDen", 4)
            val lanes = mutableListOf<RhythmLane>()
            val arr = obj.optJSONArray("lanes") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val lo = arr.getJSONObject(i)
                val inst = IndianInstrument.entries.firstOrNull { it.name == lo.optString("instrument") } ?: continue
                val sArr = lo.optJSONArray("steps") ?: JSONArray()
                val steps = (0 until sArr.length()).map { sArr.optDouble(it, 0.0).toFloat() }
                lanes.add(RhythmLane(inst, steps, lo.optBoolean("muted", false)))
            }
            RhythmPattern(
                id = obj.optString("id", UUID.randomUUID().toString()),
                name = obj.optString("name", "Custom Pattern"),
                style = style,
                timeSignature = TimeSignature(num, den, "$num/$den"),
                bpm = obj.optDouble("bpm", 116.0),
                stepCount = obj.optInt("stepCount", 16),
                swing = obj.optDouble("swing", 0.0).toFloat(),
                lanes = lanes
            )
        } catch (_: Exception) {
            null
        }
    }

    fun createPresetPatterns(): List<RhythmPattern> = listOf(
        RhythmPattern(
            id = "preset_sambalpuri",
            name = "Sambalpuri Dalkhai Folk",
            style = RhythmStyle.SAMBALPURI_FOLK,
            timeSignature = TimeSignature(4, 4, "4/4"),
            bpm = 116.0,
            stepCount = 16,
            lanes = listOf(
                RhythmLane(IndianInstrument.SAMBALPURI_MANDAR, listOf(1f, 0f, 0f, 0.7f, 0f, 0.8f, 0f, 0f, 1f, 0f, 0.6f, 0f, 0.9f, 0f, 0.7f, 0f)),
                RhythmLane(IndianInstrument.NISHAN, listOf(0.95f, 0f, 0f, 0f, 0.85f, 0f, 0f, 0f, 0.95f, 0f, 0f, 0f, 0.85f, 0f, 0.6f, 0f)),
                RhythmLane(IndianInstrument.TASHA, listOf(0f, 0.6f, 0.7f, 0f, 0.8f, 0f, 0.7f, 0.5f, 0f, 0.6f, 0.7f, 0f, 0.85f, 0.6f, 0.7f, 0.8f)),
                RhythmLane(IndianInstrument.MANJIRA, listOf(0.8f, 0f, 0.5f, 0f, 0.8f, 0f, 0.5f, 0f, 0.8f, 0f, 0.5f, 0f, 0.8f, 0f, 0.5f, 0f))
            ),
            isPreset = true
        ),
        RhythmPattern(
            id = "preset_nagpuri",
            name = "Nagpuri Theth Groove",
            style = RhythmStyle.NAGPURI_FOLK,
            timeSignature = TimeSignature(4, 4, "4/4"),
            bpm = 124.0,
            stepCount = 16,
            lanes = listOf(
                RhythmLane(IndianInstrument.NAGPURI_MANDAR, listOf(1f, 0f, 0.6f, 0.85f, 0f, 0f, 0.9f, 0f, 1f, 0f, 0.6f, 0.85f, 0f, 0.7f, 0.9f, 0f)),
                RhythmLane(IndianInstrument.DHAMSA, listOf(0.95f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0.9f, 0f, 0f, 0f, 0.8f, 0f, 0f, 0f)),
                RhythmLane(IndianInstrument.KANJARI, listOf(0f, 0.5f, 0.8f, 0f, 0.7f, 0.5f, 0.8f, 0f, 0f, 0.5f, 0.8f, 0f, 0.7f, 0.6f, 0.8f, 0.5f)),
                RhythmLane(IndianInstrument.MANJIRA, listOf(0.75f, 0f, 0.6f, 0f, 0.75f, 0f, 0.6f, 0f, 0.75f, 0f, 0.6f, 0f, 0.75f, 0f, 0.6f, 0f))
            ),
            isPreset = true
        ),
        RhythmPattern(
            id = "preset_jhumar",
            name = "Jhumar 6/8 Folk Taal",
            style = RhythmStyle.JHUMAR,
            timeSignature = TimeSignature(6, 8, "6/8"),
            bpm = 108.0,
            stepCount = 12,
            lanes = listOf(
                RhythmLane(IndianInstrument.SAMBALPURI_MANDAR, listOf(1f, 0f, 0.65f, 0.85f, 0f, 0.6f, 1f, 0f, 0.65f, 0.9f, 0.5f, 0.75f)),
                RhythmLane(IndianInstrument.DHOLAK, listOf(0.9f, 0f, 0f, 0.8f, 0f, 0f, 0.9f, 0f, 0f, 0.8f, 0.6f, 0f)),
                RhythmLane(IndianInstrument.MANJIRA, listOf(0.8f, 0f, 0.6f, 0.8f, 0f, 0.6f, 0.8f, 0f, 0.6f, 0.8f, 0f, 0.6f))
            ),
            isPreset = true
        ),
        RhythmPattern(
            id = "preset_bollywood",
            name = "Bollywood Dholak & Tabla Pop",
            style = RhythmStyle.BOLLYWOOD,
            timeSignature = TimeSignature(4, 4, "4/4"),
            bpm = 102.0,
            stepCount = 16,
            lanes = listOf(
                RhythmLane(IndianInstrument.DHOLAK, listOf(1f, 0f, 0f, 0.75f, 0f, 0f, 0.85f, 0f, 1f, 0f, 0.6f, 0f, 0.9f, 0f, 0.7f, 0f)),
                RhythmLane(IndianInstrument.TABLA, listOf(0.85f, 0f, 0.6f, 0f, 0.9f, 0.5f, 0f, 0.7f, 0.85f, 0f, 0.6f, 0f, 0.9f, 0.6f, 0.7f, 0f)),
                RhythmLane(IndianInstrument.KANJARI, listOf(0f, 0.4f, 0.75f, 0.4f, 0f, 0.4f, 0.75f, 0.4f, 0f, 0.4f, 0.75f, 0.4f, 0f, 0.4f, 0.75f, 0.6f))
            ),
            isPreset = true
        ),
        RhythmPattern(
            id = "preset_worship",
            name = "Christian Worship Anthem",
            style = RhythmStyle.CHRISTIAN_WORSHIP,
            timeSignature = TimeSignature(4, 4, "4/4"),
            bpm = 74.0,
            stepCount = 16,
            lanes = listOf(
                RhythmLane(IndianInstrument.DHAMSA, listOf(1f, 0f, 0f, 0f, 0f, 0f, 0.75f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
                RhythmLane(IndianInstrument.TABLA, listOf(0f, 0f, 0.6f, 0f, 0.85f, 0f, 0f, 0.5f, 0f, 0f, 0.6f, 0f, 0.85f, 0f, 0.6f, 0f)),
                RhythmLane(IndianInstrument.MANJIRA, listOf(0.7f, 0f, 0.5f, 0f, 0.7f, 0f, 0.5f, 0f, 0.7f, 0f, 0.5f, 0f, 0.7f, 0f, 0.5f, 0f))
            ),
            isPreset = true
        )
    )

    fun createProjectFromTemplate(templateName: String): DawProject {
        return when (templateName) {
            "Sambalpuri Folk Production" -> defaultProject(
                title = "Sambalpuri Folk Master",
                bpm = 116.0,
                key = "D",
                scale = MusicalScale.RAGA_BHUPALI
            )
            "Bollywood Cinematic" -> defaultProject(
                title = "Bollywood Cinematic Score",
                bpm = 96.0,
                key = "G",
                scale = MusicalScale.RAGA_YAMAN
            )
            "Christian Worship Live" -> defaultProject(
                title = "Worship Live Arrangement",
                bpm = 74.0,
                key = "C",
                scale = MusicalScale.MAJOR
            )
            else -> defaultProject(
                title = "Trueworld Studio Session",
                bpm = 118.0,
                key = "A",
                scale = MusicalScale.NATURAL_MINOR
            )
        }
    }

    fun defaultProject(
        title: String = "Trueworld Studio Session",
        bpm: Double = 116.0,
        key: String = "D",
        scale: MusicalScale = MusicalScale.RAGA_YAMAN
    ): DawProject {
        val trkAudioLead = DawTrack(
            id = "trk_audio_guitar",
            name = "Acoustic Guitar Stem",
            type = TrackType.AUDIO,
            colorArgb = 0xFF22C55EL, // Green Audio Track
            volume = 0.82f,
            pan = -0.10f,
            inputChannel = "In 1",
            outputChannel = "Main Out",
            busTargetId = "bus_drums",
            clips = listOf(
                AudioClip(
                    id = "clip_gtr_1",
                    name = "Strum Intro",
                    startBeat = 0.0,
                    lengthBeats = 4.0,
                    waveformPreview = listOf(0.2f, 0.7f, 0.5f, 0.85f, 0.4f, 0.9f, 0.6f, 0.45f, 0.75f, 0.55f, 0.8f, 0.35f, 0.6f, 0.7f, 0.4f, 0.3f)
                ),
                AudioClip(
                    id = "clip_gtr_2",
                    name = "Hook Riff",
                    startBeat = 4.0,
                    lengthBeats = 8.0,
                    waveformPreview = listOf(0.5f, 0.85f, 0.7f, 0.95f, 0.6f, 0.8f, 0.9f, 0.5f, 0.75f, 0.9f, 0.65f, 0.8f, 0.45f, 0.7f, 0.55f, 0.4f)
                )
            ),
            automationLanes = listOf(
                AutomationLane(
                    id = "auto_gtr_vol",
                    targetParam = "volume",
                    visible = true,
                    points = listOf(
                        AutomationPoint(0.0, 0.65f),
                        AutomationPoint(4.0, 0.85f),
                        AutomationPoint(8.0, 0.78f),
                        AutomationPoint(12.0, 0.92f)
                    )
                )
            ),
            plugins = listOf(
                PluginCatalog.createInstance("p_gtr_eq", PluginType.EQ_5_BAND, "Warm Body")
            )
        )
        val trkIndian = DawTrack(
            id = "trk_indian",
            name = "Sambalpuri Drums",
            type = TrackType.INDIAN_RHYTHM,
            colorArgb = 0xFFF97316L, // Orange Rhythm/Automation Accent
            volume = 0.85f,
            inputChannel = "Drum Bus",
            outputChannel = "Drum & Folk Bus",
            busTargetId = "bus_drums",
            plugins = listOf(
                PluginCatalog.createInstance("p_ind_eq", PluginType.EQ_3_BAND, "Punchy Drum Bus"),
                PluginCatalog.createInstance("p_ind_comp", PluginType.COMPRESSOR, "Mandar Snap")
            )
        )
        val trkBansuri = DawTrack(
            id = "trk_bansuri",
            name = "Bansuri Lead",
            type = TrackType.MIDI,
            colorArgb = 0xFFA855F7L, // Purple MIDI Track
            volume = 0.76f,
            pan = -0.15f,
            inputChannel = "MIDI Ch 1",
            outputChannel = "Main Out",
            instrumentName = "Bansuri",
            busTargetId = "bus_music",
            plugins = listOf(
                PluginCatalog.createInstance("p_ban_eq", PluginType.HIGH_PASS_FILTER),
                PluginCatalog.createInstance("p_ban_rev", PluginType.HALL_REVERB, "Worship Cathedral")
            ),
            midiNotes = listOf(
                MidiNote("mn_1", 62, 0.0, 1.0, 102),
                MidiNote("mn_2", 66, 1.0, 1.0, 98),
                MidiNote("mn_3", 69, 2.0, 1.5, 108),
                MidiNote("mn_4", 67, 3.5, 0.5, 92),
                MidiNote("mn_5", 66, 4.0, 2.0, 104),
                MidiNote("mn_6", 62, 6.0, 2.0, 96)
            )
        )
        val trkSitar = DawTrack(
            id = "trk_sitar",
            name = "Sitar & Harmonium",
            type = TrackType.INSTRUMENT,
            colorArgb = 0xFFA855F7L, // Purple MIDI/Instrument Track
            volume = 0.72f,
            pan = 0.22f,
            inputChannel = "MIDI All",
            outputChannel = "Main Out",
            sidechainSourceId = "trk_indian",
            instrumentName = "Sitar",
            busTargetId = "bus_music",
            plugins = listOf(
                PluginCatalog.createInstance("p_sit_del", PluginType.PING_PONG_DELAY),
                PluginCatalog.createInstance("p_sit_cmp", PluginType.COMPRESSOR)
            ),
            midiNotes = listOf(
                MidiNote("sn_1", 50, 0.0, 2.0, 95),
                MidiNote("sn_2", 57, 2.0, 2.0, 95),
                MidiNote("sn_3", 54, 4.0, 2.0, 98),
                MidiNote("sn_4", 50, 6.0, 2.0, 92)
            )
        )
        val trkVocal = DawTrack(
            id = "trk_vocal",
            name = "Lead Vocal",
            type = TrackType.VOCAL,
            colorArgb = 0xFF22C55EL, // Green Audio/Vocal Track
            volume = 0.84f,
            armed = true,
            monitoring = true,
            inputChannel = "Mic In 1",
            outputChannel = "Main Out",
            busTargetId = "bus_vocal",
            clips = listOf(
                AudioClip(
                    id = "clip_voc_1",
                    name = "Vocal Take 1",
                    startBeat = 2.0,
                    lengthBeats = 6.0,
                    waveformPreview = listOf(0.15f, 0.55f, 0.82f, 0.65f, 0.9f, 0.45f, 0.7f, 0.88f, 0.6f, 0.35f, 0.75f, 0.5f)
                )
            ),
            plugins = listOf(
                PluginCatalog.createInstance("p_voc_de", PluginType.DE_ESSER),
                PluginCatalog.createInstance("p_voc_cmp", PluginType.COMPRESSOR, "Vocal Glue"),
                PluginCatalog.createInstance("p_voc_plt", PluginType.PLATE_REVERB)
            )
        )
        val trkSampler = DawTrack(
            id = "trk_sampler",
            name = "Pro Sampler Kit",
            type = TrackType.DRUM,
            colorArgb = 0xFF14B8A6L,
            volume = 0.78f,
            inputChannel = "Pads 1-8",
            outputChannel = "Drum & Folk Bus",
            busTargetId = "bus_drums",
            plugins = listOf(
                PluginCatalog.createInstance("p_smp_sat", PluginType.SATURATION)
            )
        )
        val busDrums = DawTrack(
            id = "bus_drums",
            name = "Drum & Folk Bus",
            type = TrackType.BUS,
            colorArgb = 0xFFF59E0BL,
            volume = 0.90f,
            plugins = listOf(PluginCatalog.createInstance("p_bus_par", PluginType.MULTIBAND_COMPRESSOR))
        )
        val vcaMain = DawTrack(
            id = "vca_all",
            name = "VCA All Music",
            type = TrackType.VCA,
            colorArgb = 0xFF6B7585L,
            volume = 1.0f
        )
        val master = DawTrack(
            id = "bus_master",
            name = "Master Out",
            type = TrackType.MASTER,
            colorArgb = 0xFF2F8BFFL,
            volume = 0.95f
        )

        return DawProject(
            id = "proj_${UUID.randomUUID().toString().take(8)}",
            title = title,
            bpm = bpm,
            timeSignature = TimeSignature(4, 4, "4/4"),
            musicalKey = key,
            scale = scale,
            tracks = listOf(trkAudioLead, trkIndian, trkBansuri, trkSitar, trkVocal, trkSampler, busDrums, vcaMain, master),
            sections = listOf(
                SongSection("sec_1", "Intro", 1, 2, 0xFF2F8BFFL),
                SongSection("sec_2", "Verse / Mukhda", 3, 4, 0xFF22C55EL),
                SongSection("sec_3", "Chorus / Antara", 7, 4, 0xFFA855F7L)
            ),
            markers = listOf(
                TimelineMarker("mk_1", "Start", 0.0),
                TimelineMarker("mk_2", "Mandar Drop", 8.0),
                TimelineMarker("mk_3", "Bansuri Solo", 12.0)
            ),
            chordTrack = listOf(
                ChordEvent("ch_1", "Dmaj7", listOf(62, 66, 69, 73), 0.0, 4.0),
                ChordEvent("ch_2", "Bm7", listOf(59, 62, 66, 69), 4.0, 4.0),
                ChordEvent("ch_3", "Gmaj7", listOf(55, 59, 62, 66), 8.0, 4.0),
                ChordEvent("ch_4", "A7", listOf(57, 61, 64, 67), 12.0, 4.0)
            ),
            tempoTrack = listOf(
                TempoPoint(0.0, bpm),
                TempoPoint(8.0, (bpm + 4.0).coerceAtMost(200.0))
            ),
            scratchPads = listOf(
                ScratchPad("sp_1", "Idea Pad A", emptyList(), emptyList())
            ),
            playlist = listOf(
                PlaylistItem("pl_1", "sec_1", 1, true),
                PlaylistItem("pl_2", "sec_2", 2, true),
                PlaylistItem("pl_3", "sec_3", 2, true)
            )
        )
    }
}
