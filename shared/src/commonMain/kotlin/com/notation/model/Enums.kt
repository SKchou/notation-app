package com.notation.model

import kotlinx.serialization.Serializable

/**
 * Standard musical clefs with their staff position of middle C.
 * Staff position is measured in half-spaces below the top staff line.
 */
@Serializable
enum class Clef(val staffPositionOfMiddleC: Int) {
    TREBLE(10),
    BASS(2),
    ALTO(6),
    TENOR(8),
    PERCUSSION(6)
}

/**
 * Key signature defined by the number of sharps or flats (circle of fifths).
 * Negative = flats, positive = sharps. Range: -7 to +7.
 */
@Serializable
data class KeySignature(
    val fifths: Int,
    val mode: KeyMode = KeyMode.MAJOR
) {
    init {
        require(fifths in -7..7) { "fifths must be in -7..7, got $fifths" }
    }

    /** Number of accidentals displayed in the key signature. */
    val accidentalCount: Int get() = kotlin.math.abs(fifths)

    /** Whether this key signature uses flats (true) or sharps (false). Zero returns false. */
    val isFlat: Boolean get() = fifths < 0
}

/** Mode of a key signature. */
@Serializable
enum class KeyMode {
    MAJOR, MINOR
}

/**
 * Time signature defining beats per measure and the beat unit.
 * Example: 4/4 = beats=4, beatType=4.
 */
@Serializable
data class TimeSignature(
    val beats: Int,
    val beatType: Int
) {
    /** Total duration of one measure in quarter-note beats. */
    val quarterBeatsPerMeasure: Double get() = beats * (4.0 / beatType)

    companion object {
        /** 4/4 time. */
        val COMMON_TIME = TimeSignature(4, 4)
        /** 2/2 time (alla breve). */
        val CUT_TIME = TimeSignature(2, 2)
        /** 3/4 time. */
        val WALTZ = TimeSignature(3, 4)
        /** 6/8 time. */
        val SIX_EIGHT = TimeSignature(6, 8)
    }
}

/** Types of barlines that can appear at measure boundaries. */
@Serializable
enum class Barline {
    NORMAL, DOUBLE, FINAL, REPEAT_START, REPEAT_END, REPEAT_BOTH
}

/** Articulation marks that affect how individual notes are performed. */
@Serializable
enum class Articulation {
    STACCATO, STACCATISSIMO, TENUTO, ACCENT, MARCATO, FERMATA, LEGATO
}

/**
 * Dynamic markings indicating volume level.
 * Each has a corresponding MIDI velocity value.
 */
@Serializable
enum class Dynamic(val velocity: Int) {
    PPPP(8),
    PPP(20),
    PP(31),
    P(42),
    MP(53),
    MF(64),
    F(80),
    FF(96),
    FFF(112),
    FFFF(120),
    SF(112),
    SFZ(112),
    FP(96),
    SFP(112)
}

/** Expressive markings and ornaments. */
@Serializable
enum class Expression {
    CRESCENDO, DECRESCENDO, LEGATO_LINE, TRILL, TURN, MORDENT
}

/** Direction of note stems. */
@Serializable
enum class StemDirection {
    UP, DOWN, AUTO
}

/** Visual appearance of note heads. */
@Serializable
enum class NoteHead {
    NORMAL, DIAMOND, CROSS, SLASH, TRIANGLE
}

/** Accidental symbols that modify pitch. */
@Serializable
enum class Accidental {
    DOUBLE_FLAT, FLAT, NATURAL, SHARP, DOUBLE_SHARP
}

/** Position of a note within a beam group. */
@Serializable
enum class BeamPosition {
    START, CONTINUE, END
}

/**
 * Grouping of notes under a single beam.
 * All notes sharing the same [groupId] are beamed together.
 */
@Serializable
data class BeamGroup(
    val groupId: String,
    val position: BeamPosition
)

/**
 * Grouping of notes into a tuplet.
 * All notes sharing the same [groupId] form one tuplet bracket.
 */
@Serializable
data class TupletGroup(
    val groupId: String,
    val ratio: TupletRatio,
    val showNumber: Boolean = true,
    val showBracket: Boolean = true
)

/**
 * A syllable of lyrics attached to a note.
 * Multiple verses are supported via [verseNumber].
 */
@Serializable
data class LyricSyllable(
    val text: String,
    val syllabic: Syllabic,
    val verseNumber: Int = 1
)

/** How a lyric syllable connects to adjacent syllables in a word. */
@Serializable
enum class Syllabic {
    SINGLE, BEGIN, MIDDLE, END
}

/**
 * Directions are score-wide annotations that affect performance
 * but don't occupy rhythmic time themselves.
 */
@Serializable
sealed interface Direction

/** A tempo marking specifying beats per minute. */
@Serializable
data class TempoMark(
    val bpm: Double,
    val beatType: DurationType = DurationType.QUARTER,
    val text: String = ""
) : Direction

/** A rehearsal mark (e.g., "A", "B", "Coda"). */
@Serializable
data class RehearsalMark(
    val text: String
) : Direction

/** A free-text direction annotation. */
@Serializable
data class TextDirection(
    val text: String
) : Direction

/**
 * Indicates where a system break occurs in the layout.
 * Optionally also a page break.
 */
@Serializable
data class SystemBreak(
    val afterMeasure: Int,
    val pageBreak: Boolean = false
)

/**
 * Transposition interval for transposing instruments.
 * [diatonic] = number of diatonic steps, [chromatic] = number of semitones.
 */
@Serializable
data class Transposition(
    val diatonic: Int = 0,
    val chromatic: Int = 0
) {
    companion object {
        /** Concert pitch (no transposition). */
        val CONCERT = Transposition(0, 0)
    }
}
