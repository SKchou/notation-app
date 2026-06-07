package com.notation.layout

import com.notation.model.*

/**
 * Converts semantic music elements into concrete [GlyphDrawCommand]s.
 *
 * This is the final stage of the layout pipeline where abstract positions
 * are turned into renderable primitives: SMuFL glyphs for noteheads, rests,
 * clefs, and time signatures; lines for staff lines, stems, and barlines;
 * and curves for ties and slurs.
 *
 * @param metrics Glyph metrics provider for bounding boxes and anchor points.
 */
class GlyphEmitter(private val metrics: GlyphMetrics) {

    // ─── Staff Lines ────────────────────────────────────────────────────

    /**
     * Emit the 5 horizontal staff lines for the given staff.
     *
     * @param staff The staff layout defining position and extent.
     * @return Five horizontal [GlyphDrawCommand.Line]s.
     */
    fun emitStaffLines(staff: StaffLayout): List<GlyphDrawCommand> {
        return (0 until 5).map { lineIndex ->
            val y = staff.topY + StaffSpaces(lineIndex.toDouble())
            GlyphDrawCommand.Line(
                startX = staff.leftX,
                startY = y,
                endX = staff.rightX,
                endY = y,
                thickness = StaffSpaces(0.1),
                zOrder = -10 // Staff lines render behind everything
            )
        }
    }

    // ─── Notes ──────────────────────────────────────────────────────────

    /**
     * Emit all glyphs for a single note: notehead, stem, flag, accidental, and dots.
     *
     * @param note The note to render.
     * @param x Horizontal position in staff-spaces.
     * @param staff The staff this note is placed on.
     * @param clef The current clef, used for pitch-to-Y mapping.
     * @return Draw commands for the complete note.
     */
    fun emitNote(
        note: Note,
        x: StaffSpaces,
        staff: StaffLayout,
        clef: Clef
    ): List<GlyphDrawCommand> {
        val commands = mutableListOf<GlyphDrawCommand>()
        val noteY = pitchToY(note.pitch, clef, staff)
        val noteheadCodepoint = noteheadForDuration(note.duration.type)

        // Notehead
        commands.add(
            GlyphDrawCommand.SMuFLGlyph(
                codepoint = noteheadCodepoint,
                x = x,
                y = noteY,
                zOrder = 10
            )
        )

        // Ledger lines
        commands.addAll(emitLedgerLines(noteY, x, staff))

        // Stem (not for whole notes or breves)
        if (note.duration.type != DurationType.WHOLE &&
            note.duration.type != DurationType.BREVE &&
            note.duration.type != DurationType.LONGA &&
            note.duration.type != DurationType.MAXIMA
        ) {
            val stemUp = determineStemDirection(note.stemDirection, noteY, staff)
            commands.addAll(emitStem(noteheadCodepoint, x, noteY, stemUp))

            // Flag (only for unbeamed eighth notes and shorter)
            if (note.beam == null) {
                flagCodepoint(note.duration.type, stemUp)?.let { flag ->
                    val stemEnd = stemEndY(noteY, stemUp)
                    commands.add(
                        GlyphDrawCommand.SMuFLGlyph(
                            codepoint = flag,
                            x = if (stemUp) x + metrics.advanceWidth(noteheadCodepoint) - StaffSpaces(0.12)
                            else x,
                            y = stemEnd,
                            zOrder = 8
                        )
                    )
                }
            }
        }

        // Accidental
        if (note.pitch.alter != 0) {
            val accCodepoint = accidentalCodepoint(note.pitch.alter)
            val accWidth = metrics.advanceWidth(accCodepoint)
            commands.add(
                GlyphDrawCommand.SMuFLGlyph(
                    codepoint = accCodepoint,
                    x = x - accWidth - StaffSpaces(0.15),
                    y = noteY,
                    zOrder = 9
                )
            )
        }

        // Augmentation dots
        if (note.duration.dots > 0) {
            val dotStartX = x + metrics.advanceWidth(noteheadCodepoint) + StaffSpaces(0.3)
            val dotY = snapDotToSpace(noteY, staff)
            for (dotIndex in 0 until note.duration.dots) {
                commands.add(
                    GlyphDrawCommand.SMuFLGlyph(
                        codepoint = SMuFL.AUGMENTATION_DOT,
                        x = dotStartX + StaffSpaces(dotIndex * 0.5),
                        y = dotY,
                        zOrder = 9
                    )
                )
            }
        }

        return commands
    }

    // ─── Rests ──────────────────────────────────────────────────────────

    /**
     * Emit the glyph for a rest.
     *
     * @param rest The rest to render.
     * @param x Horizontal position in staff-spaces.
     * @param staff The staff this rest is placed on.
     * @return Draw command for the rest glyph.
     */
    fun emitRest(
        rest: Rest,
        x: StaffSpaces,
        staff: StaffLayout
    ): List<GlyphDrawCommand> {
        val restCodepoint = restForDuration(rest.duration.type)
        // Rests are vertically centered on the staff (middle line = line index 2)
        val restY = staff.topY + StaffSpaces(2.0)

        val commands = mutableListOf<GlyphDrawCommand>()
        commands.add(
            GlyphDrawCommand.SMuFLGlyph(
                codepoint = restCodepoint,
                x = x,
                y = restY,
                zOrder = 5
            )
        )

        // Augmentation dots for rests
        if (rest.duration.dots > 0) {
            val dotStartX = x + metrics.advanceWidth(restCodepoint) + StaffSpaces(0.3)
            val dotY = restY - StaffSpaces(0.5) // dots go slightly above center
            for (dotIndex in 0 until rest.duration.dots) {
                commands.add(
                    GlyphDrawCommand.SMuFLGlyph(
                        codepoint = SMuFL.AUGMENTATION_DOT,
                        x = dotStartX + StaffSpaces(dotIndex * 0.5),
                        y = dotY,
                        zOrder = 6
                    )
                )
            }
        }

        return commands
    }

    // ─── Chords ─────────────────────────────────────────────────────────

    /**
     * Emit glyphs for a chord: multiple noteheads sharing a single stem.
     *
     * @param chord The chord to render.
     * @param x Horizontal position in staff-spaces.
     * @param staff The staff this chord is placed on.
     * @param clef The current clef for pitch mapping.
     * @return Draw commands for the complete chord.
     */
    fun emitChord(
        chord: Chord,
        x: StaffSpaces,
        staff: StaffLayout,
        clef: Clef
    ): List<GlyphDrawCommand> {
        if (chord.pitches.isEmpty()) return emptyList()

        val commands = mutableListOf<GlyphDrawCommand>()
        val noteheadCodepoint = noteheadForDuration(chord.duration.type)

        // Compute Y positions for all pitches
        val pitchYs = chord.pitches.map { pitch -> pitchToY(pitch, clef, staff) }
        val lowestY = pitchYs.maxByOrNull { it.value }!! // largest Y = lowest on page
        val highestY = pitchYs.minByOrNull { it.value }!! // smallest Y = highest on page

        // Stem direction based on the average position
        val avgY = StaffSpaces(pitchYs.sumOf { it.value } / pitchYs.size)
        val stemUp = determineStemDirection(chord.stemDirection, avgY, staff)

        // Emit noteheads (with second-interval displacement)
        val sortedPitchYs = pitchYs.sorted()
        var prevY: StaffSpaces? = null
        for (noteY in if (stemUp) sortedPitchYs else sortedPitchYs.reversed()) {
            val displaced = prevY != null &&
                    kotlin.math.abs(noteY.value - prevY!!.value) < 1.0
            val noteX = if (displaced) {
                if (stemUp) x + metrics.advanceWidth(noteheadCodepoint)
                else x - metrics.advanceWidth(noteheadCodepoint)
            } else x

            commands.add(
                GlyphDrawCommand.SMuFLGlyph(
                    codepoint = noteheadCodepoint,
                    x = noteX,
                    y = noteY,
                    zOrder = 10
                )
            )
            commands.addAll(emitLedgerLines(noteY, noteX, staff))
            prevY = noteY
        }

        // Stem
        if (chord.duration.type != DurationType.WHOLE &&
            chord.duration.type != DurationType.BREVE
        ) {
            val stemBaseY = if (stemUp) lowestY else highestY
            val stemTipY = stemEndY(if (stemUp) highestY else lowestY, stemUp)

            val stemX = if (stemUp) {
                x + metrics.advanceWidth(noteheadCodepoint) - StaffSpaces(0.06)
            } else {
                x + StaffSpaces(0.06)
            }

            commands.add(
                GlyphDrawCommand.Line(
                    startX = stemX,
                    startY = stemBaseY,
                    endX = stemX,
                    endY = stemTipY,
                    thickness = StaffSpaces(0.12),
                    zOrder = 8
                )
            )

            // Flag
            if (chord.beam == null) {
                flagCodepoint(chord.duration.type, stemUp)?.let { flag ->
                    commands.add(
                        GlyphDrawCommand.SMuFLGlyph(
                            codepoint = flag,
                            x = stemX,
                            y = stemTipY,
                            zOrder = 8
                        )
                    )
                }
            }
        }

        return commands
    }

    // ─── Barlines ───────────────────────────────────────────────────────

    /**
     * Emit a barline at the given X position.
     *
     * @param x Horizontal position in staff-spaces.
     * @param staff The staff the barline spans.
     * @param barline The barline type.
     * @return Draw commands for the barline.
     */
    fun emitBarline(
        x: StaffSpaces,
        staff: StaffLayout,
        barline: Barline
    ): List<GlyphDrawCommand> {
        val topY = staff.topY
        val bottomY = staff.topY + StaffSpaces(4.0)

        return when (barline) {
            Barline.NORMAL -> listOf(
                GlyphDrawCommand.Line(
                    startX = x, startY = topY,
                    endX = x, endY = bottomY,
                    thickness = StaffSpaces(0.16),
                    zOrder = 5
                )
            )
            Barline.DOUBLE -> listOf(
                GlyphDrawCommand.Line(
                    startX = x - StaffSpaces(0.4), startY = topY,
                    endX = x - StaffSpaces(0.4), endY = bottomY,
                    thickness = StaffSpaces(0.16),
                    zOrder = 5
                ),
                GlyphDrawCommand.Line(
                    startX = x, startY = topY,
                    endX = x, endY = bottomY,
                    thickness = StaffSpaces(0.16),
                    zOrder = 5
                )
            )
            Barline.FINAL -> listOf(
                GlyphDrawCommand.Line(
                    startX = x - StaffSpaces(0.6), startY = topY,
                    endX = x - StaffSpaces(0.6), endY = bottomY,
                    thickness = StaffSpaces(0.16),
                    zOrder = 5
                ),
                GlyphDrawCommand.Line(
                    startX = x, startY = topY,
                    endX = x, endY = bottomY,
                    thickness = StaffSpaces(0.5),
                    zOrder = 5
                )
            )
            Barline.REPEAT_START -> listOf(
                GlyphDrawCommand.Line(
                    startX = x, startY = topY,
                    endX = x, endY = bottomY,
                    thickness = StaffSpaces(0.5),
                    zOrder = 5
                ),
                GlyphDrawCommand.Line(
                    startX = x + StaffSpaces(0.6), startY = topY,
                    endX = x + StaffSpaces(0.6), endY = bottomY,
                    thickness = StaffSpaces(0.16),
                    zOrder = 5
                ),
                GlyphDrawCommand.SMuFLGlyph(
                    codepoint = SMuFL.REPEAT_DOT,
                    x = x + StaffSpaces(0.9),
                    y = topY + StaffSpaces(1.5),
                    zOrder = 6
                ),
                GlyphDrawCommand.SMuFLGlyph(
                    codepoint = SMuFL.REPEAT_DOT,
                    x = x + StaffSpaces(0.9),
                    y = topY + StaffSpaces(2.5),
                    zOrder = 6
                )
            )
            Barline.REPEAT_END -> listOf(
                GlyphDrawCommand.SMuFLGlyph(
                    codepoint = SMuFL.REPEAT_DOT,
                    x = x - StaffSpaces(1.3),
                    y = topY + StaffSpaces(1.5),
                    zOrder = 6
                ),
                GlyphDrawCommand.SMuFLGlyph(
                    codepoint = SMuFL.REPEAT_DOT,
                    x = x - StaffSpaces(1.3),
                    y = topY + StaffSpaces(2.5),
                    zOrder = 6
                ),
                GlyphDrawCommand.Line(
                    startX = x - StaffSpaces(0.6), startY = topY,
                    endX = x - StaffSpaces(0.6), endY = bottomY,
                    thickness = StaffSpaces(0.16),
                    zOrder = 5
                ),
                GlyphDrawCommand.Line(
                    startX = x, startY = topY,
                    endX = x, endY = bottomY,
                    thickness = StaffSpaces(0.5),
                    zOrder = 5
                )
            )
            Barline.REPEAT_BOTH -> {
                // Combine repeat end + repeat start at the same position
                emitBarline(x - StaffSpaces(0.3), staff, Barline.REPEAT_END) +
                        emitBarline(x + StaffSpaces(0.3), staff, Barline.REPEAT_START)
            }
        }
    }

    // ─── Clefs ──────────────────────────────────────────────────────────

    /**
     * Emit a clef glyph.
     *
     * @param clef The clef type to render.
     * @param x Horizontal position.
     * @param staff The staff to place the clef on.
     * @return Draw command for the clef glyph.
     */
    fun emitClef(
        clef: Clef,
        x: StaffSpaces,
        staff: StaffLayout
    ): List<GlyphDrawCommand> {
        val (codepoint, yOffset) = when (clef) {
            Clef.TREBLE -> SMuFL.TREBLE_CLEF to StaffSpaces(3.0) // sits on 2nd line
            Clef.BASS -> SMuFL.BASS_CLEF to StaffSpaces(1.0)     // sits on 4th line
            Clef.ALTO -> SMuFL.ALTO_CLEF to StaffSpaces(2.0)     // centered on 3rd line
            Clef.TENOR -> SMuFL.TENOR_CLEF to StaffSpaces(1.0)   // sits on 4th line
            Clef.PERCUSSION -> SMuFL.ALTO_CLEF to StaffSpaces(2.0) // fallback glyph
        }

        return listOf(
            GlyphDrawCommand.SMuFLGlyph(
                codepoint = codepoint,
                x = x,
                y = staff.topY + yOffset,
                zOrder = 15
            )
        )
    }

    // ─── Time Signatures ────────────────────────────────────────────────

    /**
     * Emit a time signature (numerator above, denominator below the middle line).
     *
     * @param timeSig The time signature to render.
     * @param x Horizontal position.
     * @param staff The staff to place the time signature on.
     * @return Draw commands for the time signature digits.
     */
    fun emitTimeSignature(
        timeSig: TimeSignature,
        x: StaffSpaces,
        staff: StaffLayout
    ): List<GlyphDrawCommand> {
        val commands = mutableListOf<GlyphDrawCommand>()

        // Numerator digits — centered on the upper half of the staff (line 1)
        val numY = staff.topY + StaffSpaces(1.0)
        commands.addAll(emitTimeSigDigits(timeSig.beats, x, numY))

        // Denominator digits — centered on the lower half of the staff (line 3)
        val denomY = staff.topY + StaffSpaces(3.0)
        commands.addAll(emitTimeSigDigits(timeSig.beatType, x, denomY))

        return commands
    }

    // ─── Private Helpers ────────────────────────────────────────────────

    /** Emit SMuFL digit glyphs for a time-signature number. */
    private fun emitTimeSigDigits(
        number: Int,
        x: StaffSpaces,
        y: StaffSpaces
    ): List<GlyphDrawCommand> {
        val digits = number.toString()
        return digits.mapIndexed { index, digitChar ->
            GlyphDrawCommand.SMuFLGlyph(
                codepoint = timeSigDigitCodepoint(digitChar - '0'),
                x = x + StaffSpaces(index * 1.2),
                y = y,
                zOrder = 15
            )
        }
    }

    /** Map a digit (0-9) to its SMuFL time-signature codepoint. */
    private fun timeSigDigitCodepoint(digit: Int): Char = when (digit) {
        0 -> SMuFL.TIME_SIG_0
        1 -> SMuFL.TIME_SIG_1
        2 -> SMuFL.TIME_SIG_2
        3 -> SMuFL.TIME_SIG_3
        4 -> SMuFL.TIME_SIG_4
        5 -> SMuFL.TIME_SIG_5
        6 -> SMuFL.TIME_SIG_6
        7 -> SMuFL.TIME_SIG_7
        8 -> SMuFL.TIME_SIG_8
        9 -> SMuFL.TIME_SIG_9
        else -> SMuFL.TIME_SIG_0
    }

    /**
     * Convert a [Pitch] to a Y coordinate in staff-spaces.
     *
     * The Y position is calculated from the pitch's diatonic distance from
     * middle C, adjusted by the clef's middle-C staff position.
     *
     * @param pitch The pitch to convert.
     * @param clef The active clef.
     * @param staff The staff providing the top-line Y reference.
     * @return Absolute Y coordinate in staff-spaces.
     */
    private fun pitchToY(pitch: Pitch, clef: Clef, staff: StaffLayout): StaffSpaces {
        // staffPosition gives diatonic steps from C4: C4=0, D4=1, E4=2, etc.
        // clef.staffPositionOfMiddleC is in half-spaces from top line
        val halfSpacesFromTop = clef.staffPositionOfMiddleC - pitch.staffPosition
        return staff.topY + StaffSpaces(halfSpacesFromTop / 2.0)
    }

    /**
     * Determine the stem direction for a note.
     *
     * If the note has an explicit direction, use it. Otherwise, notes on or
     * above the middle staff line (line index 2) get stems down; notes below
     * get stems up.
     */
    private fun determineStemDirection(
        requested: StemDirection,
        noteY: StaffSpaces,
        staff: StaffLayout
    ): Boolean {
        return when (requested) {
            StemDirection.UP -> true
            StemDirection.DOWN -> false
            StemDirection.AUTO -> {
                val middleLineY = staff.topY + StaffSpaces(2.0)
                noteY.value >= middleLineY.value // below middle → stem up
            }
        }
    }

    /** Emit a stem line from the notehead to the stem tip. */
    private fun emitStem(
        noteheadCodepoint: Char,
        x: StaffSpaces,
        noteY: StaffSpaces,
        stemUp: Boolean
    ): List<GlyphDrawCommand> {
        val stemLength = StaffSpaces(3.5)
        return if (stemUp) {
            val anchorPoint = metrics.stemUpSE(noteheadCodepoint)
            val stemX = x + (anchorPoint?.x ?: metrics.advanceWidth(noteheadCodepoint)) - StaffSpaces(0.06)
            listOf(
                GlyphDrawCommand.Line(
                    startX = stemX, startY = noteY,
                    endX = stemX, endY = noteY - stemLength,
                    thickness = StaffSpaces(0.12),
                    zOrder = 8
                )
            )
        } else {
            val anchorPoint = metrics.stemDownNW(noteheadCodepoint)
            val stemX = x + (anchorPoint?.x ?: StaffSpaces.ZERO) + StaffSpaces(0.06)
            listOf(
                GlyphDrawCommand.Line(
                    startX = stemX, startY = noteY,
                    endX = stemX, endY = noteY + stemLength,
                    thickness = StaffSpaces(0.12),
                    zOrder = 8
                )
            )
        }
    }

    /** Compute the Y position of the stem tip (end away from notehead). */
    private fun stemEndY(noteY: StaffSpaces, stemUp: Boolean): StaffSpaces {
        val stemLength = StaffSpaces(3.5)
        return if (stemUp) noteY - stemLength else noteY + stemLength
    }

    /** Select the SMuFL notehead codepoint for a given duration type. */
    private fun noteheadForDuration(type: DurationType): Char = when (type) {
        DurationType.WHOLE -> SMuFL.NOTEHEAD_WHOLE
        DurationType.BREVE, DurationType.LONGA, DurationType.MAXIMA -> SMuFL.NOTEHEAD_DOUBLE_WHOLE
        DurationType.HALF -> SMuFL.NOTEHEAD_HALF
        else -> SMuFL.NOTEHEAD_FILLED
    }

    /** Select the SMuFL rest codepoint for a given duration type. */
    private fun restForDuration(type: DurationType): Char = when (type) {
        DurationType.WHOLE, DurationType.BREVE, DurationType.LONGA, DurationType.MAXIMA ->
            SMuFL.REST_WHOLE
        DurationType.HALF -> SMuFL.REST_HALF
        DurationType.QUARTER -> SMuFL.REST_QUARTER
        DurationType.EIGHTH -> SMuFL.REST_EIGHTH
        DurationType.SIXTEENTH -> SMuFL.REST_SIXTEENTH
        DurationType.THIRTY_SECOND -> SMuFL.REST_32ND
        DurationType.SIXTY_FOURTH, DurationType.ONE_TWENTY_EIGHTH -> SMuFL.REST_64TH
    }

    /** Select the SMuFL flag codepoint, or null if the duration doesn't have a flag. */
    private fun flagCodepoint(type: DurationType, stemUp: Boolean): Char? = when (type) {
        DurationType.EIGHTH -> if (stemUp) SMuFL.FLAG_EIGHTH_UP else SMuFL.FLAG_EIGHTH_DOWN
        DurationType.SIXTEENTH -> if (stemUp) SMuFL.FLAG_16TH_UP else SMuFL.FLAG_16TH_DOWN
        DurationType.THIRTY_SECOND -> if (stemUp) SMuFL.FLAG_32ND_UP else SMuFL.FLAG_32ND_DOWN
        DurationType.SIXTY_FOURTH, DurationType.ONE_TWENTY_EIGHTH ->
            if (stemUp) SMuFL.FLAG_32ND_UP else SMuFL.FLAG_32ND_DOWN // fallback
        else -> null
    }

    /** Map an alteration value to the SMuFL accidental codepoint. */
    private fun accidentalCodepoint(alter: Int): Char = when (alter) {
        -2 -> SMuFL.ACCIDENTAL_DOUBLE_FLAT
        -1 -> SMuFL.ACCIDENTAL_FLAT
        1 -> SMuFL.ACCIDENTAL_SHARP
        2 -> SMuFL.ACCIDENTAL_DOUBLE_SHARP
        else -> SMuFL.ACCIDENTAL_NATURAL
    }

    /**
     * Emit ledger lines above or below the staff for notes outside the staff range.
     */
    private fun emitLedgerLines(
        noteY: StaffSpaces,
        noteX: StaffSpaces,
        staff: StaffLayout
    ): List<GlyphDrawCommand> {
        val commands = mutableListOf<GlyphDrawCommand>()
        val extension = StaffSpaces(0.4)
        val staffTop = staff.topY
        val staffBottom = staff.topY + StaffSpaces(4.0)
        val lineX1 = noteX - extension
        val lineX2 = noteX + StaffSpaces(1.18) + extension // notehead width + extension

        // Ledger lines above the staff
        var y = staffTop - StaffSpaces(1.0)
        while (y >= noteY - StaffSpaces(0.25)) {
            commands.add(
                GlyphDrawCommand.Line(
                    startX = lineX1, startY = y,
                    endX = lineX2, endY = y,
                    thickness = StaffSpaces(0.16),
                    zOrder = 5
                )
            )
            y -= StaffSpaces(1.0)
        }

        // Ledger lines below the staff
        y = staffBottom + StaffSpaces(1.0)
        while (y <= noteY + StaffSpaces(0.25)) {
            commands.add(
                GlyphDrawCommand.Line(
                    startX = lineX1, startY = y,
                    endX = lineX2, endY = y,
                    thickness = StaffSpaces(0.16),
                    zOrder = 5
                )
            )
            y += StaffSpaces(1.0)
        }

        return commands
    }

    /**
     * Snap a dot Y-position to the nearest space (not on a line).
     * If the note is on a line, the dot moves up by half a staff-space.
     */
    private fun snapDotToSpace(noteY: StaffSpaces, staff: StaffLayout): StaffSpaces {
        val relativeY = noteY - staff.topY
        // If relativeY is close to an integer, the note is on a line → move dot up
        val fractional = relativeY.value - kotlin.math.floor(relativeY.value)
        return if (fractional < 0.25 || fractional > 0.75) {
            // On a line — shift dot up by half a space
            noteY - StaffSpaces(0.5)
        } else {
            noteY
        }
    }
}
