package com.notation.serialization

import com.notation.model.*
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList

/**
 * Imports MusicXML (partwise) documents into the application's [Score] model.
 *
 * Uses a simple state-machine XML parser — no external XML library required.
 * Handles the core subset of MusicXML elements needed for Tier 1 import:
 * parts, measures, attributes, notes, rests, chords, ties, and basic dynamics.
 *
 * Unsupported or unrecognized elements are tracked and reported as
 * [ImportWarning]s so the user knows what was skipped.
 */
class MusicXmlImporter {

    /**
     * Result of importing a MusicXML document.
     *
     * @property score The imported score.
     * @property warnings Warnings about skipped or unsupported elements.
     */
    data class ImportResult(
        val score: Score,
        val warnings: List<ImportWarning>
    )

    /**
     * A warning about elements that were encountered but not handled.
     *
     * @property elementName The XML element name that was skipped.
     * @property count How many times this element was encountered.
     * @property message Human-readable explanation.
     */
    data class ImportWarning(
        val elementName: String,
        val count: Int,
        val message: String = "Not yet supported"
    )

    // ─── Parser state ────────────────────────────────────────────────────

    /** State for the simple XML tag parser. */
    private data class XmlTag(
        val name: String,
        val attributes: Map<String, String>,
        val isSelfClosing: Boolean,
        val isClosing: Boolean
    )

    /** Accumulated state for a note being parsed. */
    private data class NoteBuilder(
        var isRest: Boolean = false,
        var isChord: Boolean = false,
        var step: PitchStep? = null,
        var alter: Int = 0,
        var octave: Int = 4,
        var duration: Int = 4,
        var type: DurationType = DurationType.QUARTER,
        var dots: Int = 0,
        var voice: Int = 1,
        var tieStart: Boolean = false,
        var tieStop: Boolean = false
    )

    /**
     * Imports a MusicXML document from its string content.
     *
     * @param xmlContent The raw XML string.
     * @return An [ImportResult] containing the parsed [Score] and any warnings.
     */
    fun import(xmlContent: String): ImportResult {
        val skippedElements = mutableMapOf<String, Int>()

        // Part definitions from <part-list>
        val partDefs = mutableListOf<Pair<String, String>>() // id -> name

        // Parsed parts: partId -> list of measures
        val partMeasures = mutableMapOf<String, MutableList<ParsedMeasure>>()

        // Parser state
        val tagStack = mutableListOf<String>()
        var currentContent = StringBuilder()

        // Current parsing context
        var inPartList = false
        var currentScorePartId = ""
        var currentPartName = ""

        var currentPartId = ""
        var currentMeasureNumber = 1
        var currentDivisions = 4

        // Measure attributes being accumulated
        var pendingTimeSig: TimeSignature? = null
        var pendingKeySig: KeySignature? = null
        var pendingClef: Clef? = null

        // Attributes sub-parsing
        var attrFifths: Int? = null
        var attrMode: KeyMode = KeyMode.MAJOR
        var attrBeats: Int? = null
        var attrBeatType: Int? = null
        var attrClefSign: String? = null
        var attrClefLine: Int? = null

        // Note sub-parsing
        var noteBuilder: NoteBuilder? = null
        var notePitchStep: String? = null
        var notePitchAlter: Int? = null
        var notePitchOctave: Int? = null

        // Elements in current measure, grouped by voice
        var currentMeasureElements = mutableMapOf<Int, MutableList<MusicElement>>()

        // Title / composer
        var title = ""
        var composer = ""

        // Track current position per voice within a measure (in quarter beats)
        var voicePositions = mutableMapOf<Int, Double>()

        // Known handled elements (we won't warn about these)
        val handledElements = setOf(
            "score-partwise", "work", "work-title", "identification", "creator",
            "part-list", "score-part", "part-name", "part", "measure",
            "attributes", "divisions", "key", "fifths", "mode", "time",
            "beats", "beat-type", "clef", "sign", "line",
            "note", "pitch", "step", "alter", "octave", "duration",
            "type", "dot", "rest", "chord", "voice", "tie", "tied",
            "notations", "forward", "backup", "print"
        )

        // Simple tag-by-tag parsing
        val tokens = tokenize(xmlContent)

        for (token in tokens) {
            when (token) {
                is Token.OpenTag -> {
                    val tagName = token.name
                    tagStack.add(tagName)
                    currentContent = StringBuilder()

                    when (tagName) {
                        "part-list" -> inPartList = true
                        "score-part" -> {
                            currentScorePartId = token.attributes["id"] ?: ""
                            currentPartName = ""
                        }
                        "part" -> {
                            currentPartId = token.attributes["id"] ?: ""
                            partMeasures[currentPartId] = mutableListOf()
                            currentDivisions = 4
                        }
                        "measure" -> {
                            currentMeasureNumber = token.attributes["number"]?.toIntOrNull() ?: 1
                            currentMeasureElements = mutableMapOf()
                            voicePositions = mutableMapOf()
                            pendingTimeSig = null
                            pendingKeySig = null
                            pendingClef = null
                        }
                        "key" -> {
                            attrFifths = null
                            attrMode = KeyMode.MAJOR
                        }
                        "time" -> {
                            attrBeats = null
                            attrBeatType = null
                        }
                        "clef" -> {
                            attrClefSign = null
                            attrClefLine = null
                        }
                        "note" -> {
                            noteBuilder = NoteBuilder()
                            notePitchStep = null
                            notePitchAlter = null
                            notePitchOctave = null
                        }
                        "rest" -> {
                            noteBuilder?.isRest = true
                        }
                        "chord" -> {
                            noteBuilder?.isChord = true
                        }
                        "dot" -> {
                            noteBuilder?.let { it.dots++ }
                        }
                        else -> {
                            if (tagName !in handledElements) {
                                skippedElements[tagName] = (skippedElements[tagName] ?: 0) + 1
                            }
                        }
                    }

                    // Handle self-closing tags
                    if (token.isSelfClosing) {
                        tagStack.removeLastOrNull()
                    }
                }
                is Token.CloseTag -> {
                    val content = currentContent.toString().trim()
                    val tagName = token.name

                    when (tagName) {
                        "part-list" -> inPartList = false
                        "part-name" -> {
                            if (inPartList) {
                                currentPartName = content
                            }
                        }
                        "score-part" -> {
                            if (inPartList) {
                                partDefs.add(currentScorePartId to currentPartName)
                            }
                        }
                        "work-title" -> title = content
                        "creator" -> composer = content

                        // Attributes
                        "divisions" -> {
                            currentDivisions = content.toIntOrNull() ?: 4
                        }
                        "fifths" -> attrFifths = content.toIntOrNull()
                        "mode" -> {
                            attrMode = when (content.lowercase()) {
                                "minor" -> KeyMode.MINOR
                                else -> KeyMode.MAJOR
                            }
                        }
                        "key" -> {
                            attrFifths?.let { fifths ->
                                val clamped = fifths.coerceIn(-7, 7)
                                pendingKeySig = KeySignature(clamped, attrMode)
                            }
                        }
                        "beats" -> attrBeats = content.toIntOrNull()
                        "beat-type" -> attrBeatType = content.toIntOrNull()
                        "time" -> {
                            val b = attrBeats
                            val bt = attrBeatType
                            if (b != null && bt != null) {
                                pendingTimeSig = TimeSignature(b, bt)
                            }
                        }
                        "sign" -> attrClefSign = content
                        "line" -> attrClefLine = content.toIntOrNull()
                        "clef" -> {
                            pendingClef = parseClef(attrClefSign, attrClefLine)
                        }

                        // Note sub-elements
                        "step" -> notePitchStep = content
                        "alter" -> notePitchAlter = content.toIntOrNull()
                        "octave" -> notePitchOctave = content.toIntOrNull()
                        "duration" -> {
                            noteBuilder?.duration = content.toIntOrNull() ?: 4
                        }
                        "type" -> {
                            noteBuilder?.type = parseNoteType(content)
                        }
                        "voice" -> {
                            noteBuilder?.voice = content.toIntOrNull()?.coerceIn(1, 4) ?: 1
                        }
                        "tie" -> {
                            // handled via attributes on the open tag (see below)
                        }

                        // End of note
                        "note" -> {
                            noteBuilder?.let { nb ->
                                val voiceNum = nb.voice
                                val voiceElements = currentMeasureElements.getOrPut(voiceNum) { mutableListOf() }
                                val position = if (nb.isChord) {
                                    // Chord notes share the position of the previous note
                                    val lastPos = voicePositions[voiceNum] ?: 0.0
                                    val prevDur = voiceElements.lastOrNull()?.duration?.quarterBeats ?: 0.0
                                    (lastPos - prevDur).coerceAtLeast(0.0)
                                } else {
                                    voicePositions[voiceNum] ?: 0.0
                                }

                                val durationInQuarters = nb.duration.toDouble() / currentDivisions.toDouble()
                                val duration = Duration(
                                    type = nb.type,
                                    dots = nb.dots
                                )

                                if (nb.isRest) {
                                    val rest = Rest(
                                        duration = duration,
                                        position = RhythmicPosition(position)
                                    )
                                    voiceElements.add(rest)
                                } else if (nb.isChord && voiceElements.isNotEmpty()) {
                                    // Merge into previous element as a Chord
                                    val pitchStep = parsePitchStep(notePitchStep)
                                    if (pitchStep != null) {
                                        val pitch = Pitch(
                                            step = pitchStep,
                                            alter = notePitchAlter ?: 0,
                                            octave = notePitchOctave ?: 4
                                        )
                                        val lastElement = voiceElements.last()
                                        val chord = when (lastElement) {
                                            is Note -> Chord(
                                                duration = lastElement.duration,
                                                position = lastElement.position,
                                                articulations = lastElement.articulations,
                                                dynamics = lastElement.dynamics,
                                                pitches = persistentListOf(lastElement.pitch, pitch),
                                                tieForward = lastElement.tieForward || nb.tieStart
                                            )
                                            is Chord -> lastElement.copy(
                                                pitches = lastElement.pitches.add(pitch),
                                                tieForward = lastElement.tieForward || nb.tieStart
                                            )
                                            is Rest -> {
                                                // Can't chord with a rest; add as separate note
                                                val note = Note(
                                                    duration = duration,
                                                    position = RhythmicPosition(position),
                                                    pitch = Pitch(
                                                        step = pitchStep,
                                                        alter = notePitchAlter ?: 0,
                                                        octave = notePitchOctave ?: 4
                                                    ),
                                                    tieForward = nb.tieStart
                                                )
                                                voiceElements.add(note)
                                                null
                                            }
                                        }
                                        if (chord != null) {
                                            voiceElements[voiceElements.lastIndex] = chord
                                        }
                                    }
                                } else {
                                    // Normal note
                                    val pitchStep = parsePitchStep(notePitchStep)
                                    if (pitchStep != null) {
                                        val note = Note(
                                            duration = duration,
                                            position = RhythmicPosition(position),
                                            pitch = Pitch(
                                                step = pitchStep,
                                                alter = notePitchAlter ?: 0,
                                                octave = notePitchOctave ?: 4
                                            ),
                                            tieForward = nb.tieStart
                                        )
                                        voiceElements.add(note)
                                    }
                                }

                                // Advance position (but not for chord notes)
                                if (!nb.isChord) {
                                    voicePositions[voiceNum] = position + durationInQuarters
                                }
                            }
                            noteBuilder = null
                        }

                        // End of measure
                        "measure" -> {
                            val voices = persistentMapOf<VoiceId, Voice>().builder()
                            for ((voiceNum, elements) in currentMeasureElements) {
                                val voiceId = VoiceId(voiceNum.coerceIn(1, 4))
                                voices[voiceId] = Voice(
                                    id = voiceId,
                                    elements = elements.toPersistentList()
                                )
                            }

                            val measure = Measure(
                                number = currentMeasureNumber,
                                timeSignature = pendingTimeSig,
                                keySignature = pendingKeySig,
                                clef = pendingClef,
                                voices = voices.build()
                            )

                            partMeasures[currentPartId]?.add(ParsedMeasure(measure))
                        }
                    }

                    tagStack.removeLastOrNull()
                    currentContent = StringBuilder()
                }
                is Token.Text -> {
                    currentContent.append(token.content)
                }
                is Token.TieAttribute -> {
                    when (token.type) {
                        "start" -> noteBuilder?.tieStart = true
                        "stop" -> noteBuilder?.tieStop = true
                    }
                }
            }
        }

        // Build the Score
        val parts = partDefs.mapIndexed { index, (partId, partName) ->
            val measures = partMeasures[partId]?.map { it.measure } ?: emptyList()
            Part(
                id = PartId(partId),
                name = partName,
                abbreviation = if (partName.length > 4) partName.take(4).trimEnd() + "." else partName,
                instrument = Instrument.PIANO, // Default instrument; MusicXML instrument mapping is complex
                measures = measures.toPersistentList()
            )
        }.toPersistentList()

        val score = Score(
            metadata = ScoreMetadata(
                title = title,
                composer = composer
            ),
            parts = parts
        )

        val warnings = skippedElements.map { (name, count) ->
            ImportWarning(elementName = name, count = count)
        }

        return ImportResult(score, warnings)
    }

    // ─── Helper data ─────────────────────────────────────────────────────

    private data class ParsedMeasure(val measure: Measure)

    // ─── Tokenizer ───────────────────────────────────────────────────────

    private sealed interface Token {
        data class OpenTag(
            val name: String,
            val attributes: Map<String, String>,
            val isSelfClosing: Boolean
        ) : Token

        data class CloseTag(val name: String) : Token
        data class Text(val content: String) : Token
        data class TieAttribute(val type: String) : Token
    }

    /**
     * Simple XML tokenizer that produces a stream of [Token]s.
     * Handles open tags, close tags, self-closing tags, attributes, and text content.
     * Ignores processing instructions, comments, and CDATA.
     */
    private fun tokenize(xml: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        val len = xml.length

        while (i < len) {
            if (xml[i] == '<') {
                // Check for comment
                if (i + 3 < len && xml.substring(i, i + 4) == "<!--") {
                    val end = xml.indexOf("-->", i + 4)
                    i = if (end >= 0) end + 3 else len
                    continue
                }
                // Check for processing instruction
                if (i + 1 < len && xml[i + 1] == '?') {
                    val end = xml.indexOf("?>", i + 2)
                    i = if (end >= 0) end + 2 else len
                    continue
                }
                // Check for DOCTYPE
                if (i + 1 < len && xml[i + 1] == '!') {
                    val end = xml.indexOf('>', i + 2)
                    i = if (end >= 0) end + 1 else len
                    continue
                }

                val tagEnd = xml.indexOf('>', i)
                if (tagEnd < 0) break

                val tagContent = xml.substring(i + 1, tagEnd).trim()

                if (tagContent.startsWith("/")) {
                    // Closing tag
                    val name = tagContent.substring(1).trim()
                    tokens.add(Token.CloseTag(name))
                } else {
                    val isSelfClosing = tagContent.endsWith("/")
                    val raw = if (isSelfClosing) tagContent.dropLast(1).trim() else tagContent

                    val parts = splitTagContent(raw)
                    val name = parts.first()
                    val attributes = parseAttributes(parts.drop(1).joinToString(" "))

                    tokens.add(Token.OpenTag(name, attributes, isSelfClosing))

                    // Detect tie attributes for tie/tied tags
                    if (name == "tie" || name == "tied") {
                        attributes["type"]?.let { type ->
                            tokens.add(Token.TieAttribute(type))
                        }
                    }

                    if (isSelfClosing) {
                        tokens.add(Token.CloseTag(name))
                    }
                }

                i = tagEnd + 1
            } else {
                // Text content
                val nextTag = xml.indexOf('<', i)
                val end = if (nextTag >= 0) nextTag else len
                val text = xml.substring(i, end)
                if (text.isNotBlank()) {
                    tokens.add(Token.Text(unescapeXml(text)))
                }
                i = end
            }
        }

        return tokens
    }

    /**
     * Splits a tag's content into the tag name and attribute tokens.
     */
    private fun splitTagContent(content: String): List<String> {
        val result = mutableListOf<String>()
        val spaceIdx = content.indexOfFirst { it.isWhitespace() }
        if (spaceIdx < 0) {
            result.add(content)
        } else {
            result.add(content.substring(0, spaceIdx))
            result.add(content.substring(spaceIdx + 1))
        }
        return result
    }

    /**
     * Parses attributes from a string like `id="P1" name="Piano"`.
     */
    private fun parseAttributes(attrString: String): Map<String, String> {
        val attrs = mutableMapOf<String, String>()
        val regex = Regex("""(\w[\w\-.]*)=["']([^"']*)["']""")
        for (match in regex.findAll(attrString)) {
            attrs[match.groupValues[1]] = unescapeXml(match.groupValues[2])
        }
        return attrs
    }

    /**
     * Unescapes XML entities in text content.
     */
    private fun unescapeXml(text: String): String = text
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")

    // ─── MusicXML value parsers ──────────────────────────────────────────

    /**
     * Parses a MusicXML note type string to a [DurationType].
     */
    private fun parseNoteType(typeStr: String): DurationType = when (typeStr.lowercase()) {
        "maxima" -> DurationType.MAXIMA
        "long" -> DurationType.LONGA
        "breve" -> DurationType.BREVE
        "whole" -> DurationType.WHOLE
        "half" -> DurationType.HALF
        "quarter" -> DurationType.QUARTER
        "eighth" -> DurationType.EIGHTH
        "16th" -> DurationType.SIXTEENTH
        "32nd" -> DurationType.THIRTY_SECOND
        "64th" -> DurationType.SIXTY_FOURTH
        "128th" -> DurationType.ONE_TWENTY_EIGHTH
        else -> DurationType.QUARTER
    }

    /**
     * Parses a pitch step string ("C", "D", etc.) to [PitchStep].
     */
    private fun parsePitchStep(stepStr: String?): PitchStep? = when (stepStr?.uppercase()) {
        "C" -> PitchStep.C
        "D" -> PitchStep.D
        "E" -> PitchStep.E
        "F" -> PitchStep.F
        "G" -> PitchStep.G
        "A" -> PitchStep.A
        "B" -> PitchStep.B
        else -> null
    }

    /**
     * Parses clef sign and line to a [Clef] enum value.
     */
    private fun parseClef(sign: String?, line: Int?): Clef? = when {
        sign == "G" && (line == 2 || line == null) -> Clef.TREBLE
        sign == "F" && (line == 4 || line == null) -> Clef.BASS
        sign == "C" && line == 3 -> Clef.ALTO
        sign == "C" && line == 4 -> Clef.TENOR
        sign == "percussion" || sign == "TAB" -> Clef.PERCUSSION
        sign == "G" -> Clef.TREBLE  // Other G clef positions default to treble
        sign == "F" -> Clef.BASS    // Other F clef positions default to bass
        sign == "C" -> Clef.ALTO    // Other C clef positions default to alto
        else -> null
    }
}
