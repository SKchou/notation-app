package com.notation.layout

/**
 * Standard Music Font Layout (SMuFL) glyph codepoints.
 * Subset covering Tier 1 notation elements.
 *
 * Codepoints follow the SMuFL specification v1.4+, compatible with
 * Bravura, Petaluma, Leland, and other SMuFL-compliant fonts.
 *
 * @see <a href="https://www.w3.org/2019/03/smufl14/tables/">SMuFL Tables</a>
 */
object SMuFL {
    // ─── Clefs ───────────────────────────────────────────────────────
    const val TREBLE_CLEF = '\uE050'
    const val BASS_CLEF = '\uE062'
    const val ALTO_CLEF = '\uE05C'
    /** Tenor clef uses the same glyph as alto, positioned differently. */
    const val TENOR_CLEF = '\uE05C'

    // ─── Noteheads ───────────────────────────────────────────────────
    const val NOTEHEAD_WHOLE = '\uE0A2'
    const val NOTEHEAD_HALF = '\uE0A3'
    const val NOTEHEAD_FILLED = '\uE0A4'
    const val NOTEHEAD_DOUBLE_WHOLE = '\uE0A0'

    // ─── Rests ───────────────────────────────────────────────────────
    const val REST_WHOLE = '\uE4E3'
    const val REST_HALF = '\uE4E4'
    const val REST_QUARTER = '\uE4E5'
    const val REST_EIGHTH = '\uE4E6'
    const val REST_SIXTEENTH = '\uE4E7'
    const val REST_32ND = '\uE4E8'
    const val REST_64TH = '\uE4E9'

    // ─── Flags ───────────────────────────────────────────────────────
    const val FLAG_EIGHTH_UP = '\uE240'
    const val FLAG_EIGHTH_DOWN = '\uE241'
    const val FLAG_16TH_UP = '\uE242'
    const val FLAG_16TH_DOWN = '\uE243'
    const val FLAG_32ND_UP = '\uE244'
    const val FLAG_32ND_DOWN = '\uE245'

    // ─── Accidentals ─────────────────────────────────────────────────
    const val ACCIDENTAL_FLAT = '\uE260'
    const val ACCIDENTAL_NATURAL = '\uE261'
    const val ACCIDENTAL_SHARP = '\uE262'
    const val ACCIDENTAL_DOUBLE_SHARP = '\uE263'
    const val ACCIDENTAL_DOUBLE_FLAT = '\uE264'

    // ─── Dots ────────────────────────────────────────────────────────
    const val AUGMENTATION_DOT = '\uE1E7'

    // ─── Articulations ───────────────────────────────────────────────
    const val ARTICULATION_STACCATO = '\uE4A2'
    const val ARTICULATION_TENUTO = '\uE4A4'
    const val ARTICULATION_ACCENT = '\uE4A0'
    const val ARTICULATION_MARCATO = '\uE4AC'
    const val ARTICULATION_FERMATA = '\uE4C0'

    // ─── Dynamics ────────────────────────────────────────────────────
    const val DYNAMIC_P = '\uE520'
    const val DYNAMIC_M = '\uE521'
    const val DYNAMIC_F = '\uE522'
    const val DYNAMIC_S = '\uE524'
    const val DYNAMIC_Z = '\uE525'

    // ─── Time Signatures ─────────────────────────────────────────────
    const val TIME_SIG_0 = '\uE080'
    const val TIME_SIG_1 = '\uE081'
    const val TIME_SIG_2 = '\uE082'
    const val TIME_SIG_3 = '\uE083'
    const val TIME_SIG_4 = '\uE084'
    const val TIME_SIG_5 = '\uE085'
    const val TIME_SIG_6 = '\uE086'
    const val TIME_SIG_7 = '\uE087'
    const val TIME_SIG_8 = '\uE088'
    const val TIME_SIG_9 = '\uE089'
    const val TIME_SIG_COMMON = '\uE08A'
    const val TIME_SIG_CUT = '\uE08B'

    // ─── Barlines ────────────────────────────────────────────────────
    const val BARLINE_SINGLE = '\uE030'
    const val BARLINE_DOUBLE = '\uE031'
    const val BARLINE_FINAL = '\uE032'
    const val REPEAT_DOT = '\uE044'
}

/**
 * Interface for querying SMuFL font metrics.
 * Provides bounding boxes and anchor points for music glyphs.
 *
 * Implementations should load metrics from the font's metadata JSON file
 * (e.g., bravura_metadata.json) for accurate positioning.
 */
interface GlyphMetrics {
    /** Get the bounding box of a glyph in staff-space units. */
    fun boundingBox(codepoint: Char): BoundingBox

    /** Get the stem-up attachment point (south-east corner of notehead). */
    fun stemUpSE(codepoint: Char): StaffSpacePoint?

    /** Get the stem-down attachment point (north-west corner of notehead). */
    fun stemDownNW(codepoint: Char): StaffSpacePoint?

    /** Get the advance width of a glyph in staff-spaces. */
    fun advanceWidth(codepoint: Char): StaffSpaces
}

/**
 * Default glyph metrics based on Bravura reference values.
 *
 * These are approximate measurements suitable for layout prototyping.
 * A production implementation would load the actual bravura_metadata.json file
 * and parse the glyphBBoxes, glyphAnchors, and glyphAdvanceWidths tables.
 */
class DefaultGlyphMetrics : GlyphMetrics {
    override fun boundingBox(codepoint: Char): BoundingBox {
        return when (codepoint) {
            SMuFL.NOTEHEAD_FILLED, SMuFL.NOTEHEAD_HALF -> BoundingBox(
                x = StaffSpaces.ZERO, y = StaffSpaces(-0.5),
                width = StaffSpaces(1.18), height = StaffSpaces(1.0)
            )
            SMuFL.NOTEHEAD_WHOLE -> BoundingBox(
                x = StaffSpaces.ZERO, y = StaffSpaces(-0.5),
                width = StaffSpaces(1.66), height = StaffSpaces(1.0)
            )
            SMuFL.NOTEHEAD_DOUBLE_WHOLE -> BoundingBox(
                x = StaffSpaces.ZERO, y = StaffSpaces(-0.5),
                width = StaffSpaces(2.04), height = StaffSpaces(1.0)
            )
            SMuFL.TREBLE_CLEF -> BoundingBox(
                x = StaffSpaces.ZERO, y = StaffSpaces(-4.0),
                width = StaffSpaces(2.68), height = StaffSpaces(8.0)
            )
            SMuFL.BASS_CLEF -> BoundingBox(
                x = StaffSpaces.ZERO, y = StaffSpaces(-1.0),
                width = StaffSpaces(2.34), height = StaffSpaces(3.5)
            )
            SMuFL.ALTO_CLEF -> BoundingBox(
                x = StaffSpaces.ZERO, y = StaffSpaces(-2.0),
                width = StaffSpaces(2.78), height = StaffSpaces(4.0)
            )
            SMuFL.REST_WHOLE -> BoundingBox(
                x = StaffSpaces.ZERO, y = StaffSpaces(-0.5),
                width = StaffSpaces(1.5), height = StaffSpaces(0.5)
            )
            SMuFL.REST_HALF -> BoundingBox(
                x = StaffSpaces.ZERO, y = StaffSpaces(0.0),
                width = StaffSpaces(1.5), height = StaffSpaces(0.5)
            )
            SMuFL.REST_QUARTER -> BoundingBox(
                x = StaffSpaces.ZERO, y = StaffSpaces(-1.5),
                width = StaffSpaces(1.06), height = StaffSpaces(3.0)
            )
            SMuFL.REST_EIGHTH -> BoundingBox(
                x = StaffSpaces.ZERO, y = StaffSpaces(-1.0),
                width = StaffSpaces(1.1), height = StaffSpaces(2.0)
            )
            SMuFL.REST_SIXTEENTH -> BoundingBox(
                x = StaffSpaces.ZERO, y = StaffSpaces(-1.5),
                width = StaffSpaces(1.36), height = StaffSpaces(3.0)
            )
            SMuFL.REST_32ND -> BoundingBox(
                x = StaffSpaces.ZERO, y = StaffSpaces(-2.0),
                width = StaffSpaces(1.5), height = StaffSpaces(4.0)
            )
            SMuFL.REST_64TH -> BoundingBox(
                x = StaffSpaces.ZERO, y = StaffSpaces(-2.5),
                width = StaffSpaces(1.5), height = StaffSpaces(5.0)
            )
            SMuFL.ACCIDENTAL_SHARP -> BoundingBox(
                x = StaffSpaces.ZERO, y = StaffSpaces(-1.4),
                width = StaffSpaces(0.96), height = StaffSpaces(2.8)
            )
            SMuFL.ACCIDENTAL_FLAT -> BoundingBox(
                x = StaffSpaces.ZERO, y = StaffSpaces(-1.76),
                width = StaffSpaces(0.78), height = StaffSpaces(2.26)
            )
            SMuFL.ACCIDENTAL_NATURAL -> BoundingBox(
                x = StaffSpaces.ZERO, y = StaffSpaces(-1.36),
                width = StaffSpaces(0.6), height = StaffSpaces(2.72)
            )
            SMuFL.ACCIDENTAL_DOUBLE_SHARP -> BoundingBox(
                x = StaffSpaces.ZERO, y = StaffSpaces(-0.5),
                width = StaffSpaces(1.0), height = StaffSpaces(1.0)
            )
            SMuFL.ACCIDENTAL_DOUBLE_FLAT -> BoundingBox(
                x = StaffSpaces.ZERO, y = StaffSpaces(-1.76),
                width = StaffSpaces(1.56), height = StaffSpaces(2.26)
            )
            SMuFL.FLAG_EIGHTH_UP, SMuFL.FLAG_EIGHTH_DOWN -> BoundingBox(
                x = StaffSpaces.ZERO, y = StaffSpaces(-0.5),
                width = StaffSpaces(1.14), height = StaffSpaces(3.0)
            )
            SMuFL.FLAG_16TH_UP, SMuFL.FLAG_16TH_DOWN -> BoundingBox(
                x = StaffSpaces.ZERO, y = StaffSpaces(-0.5),
                width = StaffSpaces(1.14), height = StaffSpaces(4.0)
            )
            SMuFL.FLAG_32ND_UP, SMuFL.FLAG_32ND_DOWN -> BoundingBox(
                x = StaffSpaces.ZERO, y = StaffSpaces(-0.5),
                width = StaffSpaces(1.14), height = StaffSpaces(5.0)
            )
            SMuFL.AUGMENTATION_DOT -> BoundingBox(
                x = StaffSpaces.ZERO, y = StaffSpaces(-0.2),
                width = StaffSpaces(0.4), height = StaffSpaces(0.4)
            )
            SMuFL.ARTICULATION_STACCATO -> BoundingBox(
                x = StaffSpaces.ZERO, y = StaffSpaces(-0.2),
                width = StaffSpaces(0.4), height = StaffSpaces(0.4)
            )
            SMuFL.ARTICULATION_ACCENT -> BoundingBox(
                x = StaffSpaces.ZERO, y = StaffSpaces(-0.5),
                width = StaffSpaces(1.24), height = StaffSpaces(1.0)
            )
            SMuFL.ARTICULATION_TENUTO -> BoundingBox(
                x = StaffSpaces.ZERO, y = StaffSpaces(-0.1),
                width = StaffSpaces(1.2), height = StaffSpaces(0.2)
            )
            SMuFL.ARTICULATION_MARCATO -> BoundingBox(
                x = StaffSpaces.ZERO, y = StaffSpaces(-1.0),
                width = StaffSpaces(0.8), height = StaffSpaces(1.0)
            )
            SMuFL.ARTICULATION_FERMATA -> BoundingBox(
                x = StaffSpaces.ZERO, y = StaffSpaces(-1.8),
                width = StaffSpaces(1.6), height = StaffSpaces(1.8)
            )
            else -> BoundingBox(
                x = StaffSpaces.ZERO, y = StaffSpaces(-0.5),
                width = StaffSpaces(1.0), height = StaffSpaces(1.0)
            )
        }
    }

    override fun stemUpSE(codepoint: Char): StaffSpacePoint? {
        return when (codepoint) {
            SMuFL.NOTEHEAD_FILLED, SMuFL.NOTEHEAD_HALF -> StaffSpacePoint(
                x = StaffSpaces(1.18), y = StaffSpaces(-0.168)
            )
            SMuFL.NOTEHEAD_WHOLE -> StaffSpacePoint(
                x = StaffSpaces(1.66), y = StaffSpaces(-0.168)
            )
            else -> null
        }
    }

    override fun stemDownNW(codepoint: Char): StaffSpacePoint? {
        return when (codepoint) {
            SMuFL.NOTEHEAD_FILLED, SMuFL.NOTEHEAD_HALF -> StaffSpacePoint(
                x = StaffSpaces(0.0), y = StaffSpaces(0.168)
            )
            SMuFL.NOTEHEAD_WHOLE -> StaffSpacePoint(
                x = StaffSpaces(0.0), y = StaffSpaces(0.168)
            )
            else -> null
        }
    }

    override fun advanceWidth(codepoint: Char): StaffSpaces {
        return boundingBox(codepoint).width
    }
}
