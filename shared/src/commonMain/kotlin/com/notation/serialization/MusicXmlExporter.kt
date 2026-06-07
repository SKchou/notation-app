package com.notation.serialization

import com.notation.model.*

/**
 * Exports a [Score] to MusicXML 4.0 partwise format.
 *
 * Produces a valid XML document with `<score-partwise version="4.0">` as the
 * root element. Uses StringBuilder for efficient string construction.
 *
 * Supports:
 * - Work title and composer from score metadata
 * - Part list with instrument names
 * - Measure attributes: divisions, key signature, time signature, clef
 * - Notes with pitch, duration, type, dots, accidentals, ties, and voice
 * - Chords (subsequent pitches include the `<chord/>` element)
 * - Rests
 */
class MusicXmlExporter {

    companion object {
        /** MusicXML divisions per quarter note. */
        private const val DIVISIONS = 4

        /** Maps [DurationType] to MusicXML `<type>` element text. */
        private val TYPE_NAMES = mapOf(
            DurationType.MAXIMA to "maxima",
            DurationType.LONGA to "long",
            DurationType.BREVE to "breve",
            DurationType.WHOLE to "whole",
            DurationType.HALF to "half",
            DurationType.QUARTER to "quarter",
            DurationType.EIGHTH to "eighth",
            DurationType.SIXTEENTH to "16th",
            DurationType.THIRTY_SECOND to "32nd",
            DurationType.SIXTY_FOURTH to "64th",
            DurationType.ONE_TWENTY_EIGHTH to "128th"
        )

        /** Maps alter values to MusicXML accidental names. */
        private val ACCIDENTAL_NAMES = mapOf(
            -2 to "flat-flat",
            -1 to "flat",
            0 to "natural",
            1 to "sharp",
            2 to "double-sharp"
        )
    }

    /**
     * Exports the given [Score] to a MusicXML 4.0 partwise XML string.
     *
     * @param score The score to export.
     * @return A complete, valid MusicXML document as a string.
     */
    fun export(score: Score): String {
        val sb = StringBuilder()

        // XML declaration
        sb.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        sb.appendLine("<!DOCTYPE score-partwise PUBLIC \"-//Recordare//DTD MusicXML 4.0 Partwise//EN\" \"http://www.musicxml.org/dtds/partwise.dtd\">")
        sb.appendLine("<score-partwise version=\"4.0\">")

        // Work title
        if (score.metadata.title.isNotBlank()) {
            sb.appendLine("  <work>")
            sb.appendLine("    <work-title>${escapeXml(score.metadata.title)}</work-title>")
            sb.appendLine("  </work>")
        }

        // Identification (composer)
        if (score.metadata.composer.isNotBlank()) {
            sb.appendLine("  <identification>")
            sb.appendLine("    <creator type=\"composer\">${escapeXml(score.metadata.composer)}</creator>")
            sb.appendLine("  </identification>")
        }

        // Part list
        sb.appendLine("  <part-list>")
        for (part in score.parts) {
            val partIdAttr = escapeXmlAttr(part.id.value)
            sb.appendLine("    <score-part id=\"$partIdAttr\">")
            sb.appendLine("      <part-name>${escapeXml(part.name)}</part-name>")
            if (part.abbreviation.isNotBlank()) {
                sb.appendLine("      <part-name-display>")
                sb.appendLine("        <display-text>${escapeXml(part.abbreviation)}</display-text>")
                sb.appendLine("      </part-name-display>")
            }
            sb.appendLine("    </score-part>")
        }
        sb.appendLine("  </part-list>")

        // Parts with measures
        for (part in score.parts) {
            val partIdAttr = escapeXmlAttr(part.id.value)
            sb.appendLine("  <part id=\"$partIdAttr\">")

            for ((measureIndex, measure) in part.measures.withIndex()) {
                exportMeasure(sb, measure, measureIndex)
            }

            sb.appendLine("  </part>")
        }

        sb.appendLine("</score-partwise>")
        return sb.toString()
    }

    /**
     * Exports a single measure to the StringBuilder.
     */
    private fun exportMeasure(sb: StringBuilder, measure: Measure, measureIndex: Int) {
        sb.appendLine("    <measure number=\"${measure.number}\">")

        // Attributes block (only if there are attributes to write)
        val hasAttributes = measureIndex == 0 ||
            measure.timeSignature != null ||
            measure.keySignature != null ||
            measure.clef != null

        if (hasAttributes) {
            sb.appendLine("      <attributes>")

            // Divisions (always on first measure)
            if (measureIndex == 0) {
                sb.appendLine("        <divisions>$DIVISIONS</divisions>")
            }

            // Key signature
            measure.keySignature?.let { keySig ->
                sb.appendLine("        <key>")
                sb.appendLine("          <fifths>${keySig.fifths}</fifths>")
                sb.appendLine("          <mode>${keySig.mode.name.lowercase()}</mode>")
                sb.appendLine("        </key>")
            }

            // Time signature
            measure.timeSignature?.let { timeSig ->
                sb.appendLine("        <time>")
                sb.appendLine("          <beats>${timeSig.beats}</beats>")
                sb.appendLine("          <beat-type>${timeSig.beatType}</beat-type>")
                sb.appendLine("        </time>")
            }

            // Clef
            measure.clef?.let { clef ->
                val (sign, line) = clefToSignAndLine(clef)
                sb.appendLine("        <clef>")
                sb.appendLine("          <sign>$sign</sign>")
                sb.appendLine("          <line>$line</line>")
                sb.appendLine("        </clef>")
            }

            sb.appendLine("      </attributes>")
        }

        // Collect all elements from all voices, sorted by position then voice
        val allElements = mutableListOf<Pair<VoiceId, MusicElement>>()
        for ((voiceId, voice) in measure.voices) {
            for (element in voice.elements) {
                allElements.add(voiceId to element)
            }
        }
        allElements.sortBy { it.second.position.quarterBeats }

        // Export elements
        for ((voiceId, element) in allElements) {
            when (element) {
                is Note -> exportNote(sb, element, voiceId)
                is Chord -> exportChord(sb, element, voiceId)
                is Rest -> exportRest(sb, element, voiceId)
            }
        }

        // Barline
        if (measure.barlineEnd != Barline.NORMAL) {
            exportBarline(sb, measure.barlineEnd)
        }

        sb.appendLine("    </measure>")
    }

    /**
     * Exports a single [Note] element.
     */
    private fun exportNote(sb: StringBuilder, note: Note, voiceId: VoiceId) {
        sb.appendLine("      <note>")
        exportPitch(sb, note.pitch)
        exportDuration(sb, note.duration)

        // Tie
        if (note.tieForward) {
            sb.appendLine("        <tie type=\"start\"/>")
        }

        sb.appendLine("        <voice>${voiceId.value}</voice>")
        exportType(sb, note.duration)

        // Dots
        repeat(note.duration.dots) {
            sb.appendLine("        <dot/>")
        }

        // Accidental
        if (note.pitch.alter != 0) {
            val accName = ACCIDENTAL_NAMES[note.pitch.alter] ?: "natural"
            sb.appendLine("        <accidental>$accName</accidental>")
        }

        // Tie notation
        if (note.tieForward) {
            sb.appendLine("        <notations>")
            sb.appendLine("          <tied type=\"start\"/>")
            sb.appendLine("        </notations>")
        }

        sb.appendLine("      </note>")
    }

    /**
     * Exports a [Chord] element. The first pitch is a normal `<note>`,
     * and subsequent pitches include the `<chord/>` marker.
     */
    private fun exportChord(sb: StringBuilder, chord: Chord, voiceId: VoiceId) {
        for ((pitchIndex, pitch) in chord.pitches.withIndex()) {
            sb.appendLine("      <note>")

            // Subsequent notes in a chord get the <chord/> element
            if (pitchIndex > 0) {
                sb.appendLine("        <chord/>")
            }

            exportPitch(sb, pitch)
            exportDuration(sb, chord.duration)

            // Tie
            if (chord.tieForward) {
                sb.appendLine("        <tie type=\"start\"/>")
            }

            sb.appendLine("        <voice>${voiceId.value}</voice>")
            exportType(sb, chord.duration)

            // Dots
            repeat(chord.duration.dots) {
                sb.appendLine("        <dot/>")
            }

            // Accidental
            if (pitch.alter != 0) {
                val accName = ACCIDENTAL_NAMES[pitch.alter] ?: "natural"
                sb.appendLine("        <accidental>$accName</accidental>")
            }

            // Tie notation
            if (chord.tieForward) {
                sb.appendLine("        <notations>")
                sb.appendLine("          <tied type=\"start\"/>")
                sb.appendLine("        </notations>")
            }

            sb.appendLine("      </note>")
        }
    }

    /**
     * Exports a [Rest] element.
     */
    private fun exportRest(sb: StringBuilder, rest: Rest, voiceId: VoiceId) {
        sb.appendLine("      <note>")
        sb.appendLine("        <rest/>")
        exportDuration(sb, rest.duration)
        sb.appendLine("        <voice>${voiceId.value}</voice>")
        exportType(sb, rest.duration)

        // Dots
        repeat(rest.duration.dots) {
            sb.appendLine("        <dot/>")
        }

        sb.appendLine("      </note>")
    }

    /**
     * Writes a `<pitch>` element with step, alter, and octave.
     */
    private fun exportPitch(sb: StringBuilder, pitch: Pitch) {
        sb.appendLine("        <pitch>")
        sb.appendLine("          <step>${pitch.step.name}</step>")
        if (pitch.alter != 0) {
            sb.appendLine("          <alter>${pitch.alter}</alter>")
        }
        sb.appendLine("          <octave>${pitch.octave}</octave>")
        sb.appendLine("        </pitch>")
    }

    /**
     * Writes a `<duration>` element.
     * Duration in MusicXML divisions: quarterBeats * DIVISIONS.
     */
    private fun exportDuration(sb: StringBuilder, duration: Duration) {
        val xmlDuration = (duration.quarterBeats * DIVISIONS).toInt()
        sb.appendLine("        <duration>$xmlDuration</duration>")
    }

    /**
     * Writes a `<type>` element for the note's visual duration type.
     */
    private fun exportType(sb: StringBuilder, duration: Duration) {
        val typeName = TYPE_NAMES[duration.type] ?: "quarter"
        sb.appendLine("        <type>$typeName</type>")
    }

    /**
     * Maps a [Clef] enum to MusicXML sign and line values.
     */
    private fun clefToSignAndLine(clef: Clef): Pair<String, Int> = when (clef) {
        Clef.TREBLE -> "G" to 2
        Clef.BASS -> "F" to 4
        Clef.ALTO -> "C" to 3
        Clef.TENOR -> "C" to 4
        Clef.PERCUSSION -> "percussion" to 3
    }

    /**
     * Exports a non-normal barline.
     */
    private fun exportBarline(sb: StringBuilder, barline: Barline) {
        when (barline) {
            Barline.DOUBLE -> {
                sb.appendLine("      <barline location=\"right\">")
                sb.appendLine("        <bar-style>light-light</bar-style>")
                sb.appendLine("      </barline>")
            }
            Barline.FINAL -> {
                sb.appendLine("      <barline location=\"right\">")
                sb.appendLine("        <bar-style>light-heavy</bar-style>")
                sb.appendLine("      </barline>")
            }
            Barline.REPEAT_START -> {
                sb.appendLine("      <barline location=\"left\">")
                sb.appendLine("        <bar-style>heavy-light</bar-style>")
                sb.appendLine("        <repeat direction=\"forward\"/>")
                sb.appendLine("      </barline>")
            }
            Barline.REPEAT_END -> {
                sb.appendLine("      <barline location=\"right\">")
                sb.appendLine("        <bar-style>light-heavy</bar-style>")
                sb.appendLine("        <repeat direction=\"backward\"/>")
                sb.appendLine("      </barline>")
            }
            Barline.REPEAT_BOTH -> {
                sb.appendLine("      <barline location=\"right\">")
                sb.appendLine("        <bar-style>light-heavy</bar-style>")
                sb.appendLine("        <repeat direction=\"backward\"/>")
                sb.appendLine("      </barline>")
            }
            Barline.NORMAL -> { /* Already handled above */ }
        }
    }

    /**
     * Escapes special XML characters in text content.
     */
    private fun escapeXml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    /**
     * Escapes special XML characters in attribute values.
     */
    private fun escapeXmlAttr(text: String): String = escapeXml(text)
}
