package com.notation.model

import kotlinx.serialization.Serializable

/**
 * Musical pitch representation with note name, accidental, and octave.
 */
@Serializable
data class Pitch(
    val step: PitchStep,
    val alter: Int = 0,
    val octave: Int = 4
) {
    /** Convert this pitch to a MIDI note number (0–127). */
    fun toMidiNote(): Int {
        val baseNote = when (step) {
            PitchStep.C -> 0
            PitchStep.D -> 2
            PitchStep.E -> 4
            PitchStep.F -> 5
            PitchStep.G -> 7
            PitchStep.A -> 9
            PitchStep.B -> 11
        }
        return (octave + 1) * 12 + baseNote + alter
    }

    companion object {
        /**
         * Create a Pitch from a MIDI note number.
         * Uses sharps for accidentals by default.
         */
        fun fromMidiNote(midiNote: Int): Pitch {
            val octave = (midiNote / 12) - 1
            val noteInOctave = midiNote % 12
            return when (noteInOctave) {
                0 -> Pitch(PitchStep.C, 0, octave)
                1 -> Pitch(PitchStep.C, 1, octave)
                2 -> Pitch(PitchStep.D, 0, octave)
                3 -> Pitch(PitchStep.D, 1, octave)
                4 -> Pitch(PitchStep.E, 0, octave)
                5 -> Pitch(PitchStep.F, 0, octave)
                6 -> Pitch(PitchStep.F, 1, octave)
                7 -> Pitch(PitchStep.G, 0, octave)
                8 -> Pitch(PitchStep.G, 1, octave)
                9 -> Pitch(PitchStep.A, 0, octave)
                10 -> Pitch(PitchStep.A, 1, octave)
                11 -> Pitch(PitchStep.B, 0, octave)
                else -> Pitch(PitchStep.C, 0, octave)
            }
        }

        val MIDDLE_C = Pitch(PitchStep.C, 0, 4)
    }
}

/**
 * The seven diatonic pitch steps.
 */
@Serializable
enum class PitchStep {
    C, D, E, F, G, A, B
}

/**
 * Rhythmic duration of a musical element.
 */
@Serializable
data class Duration(
    val type: DurationType,
    val dots: Int = 0
) {
    /**
     * Duration expressed in quarter-note beats.
     * A quarter note = 1.0, half = 2.0, whole = 4.0, eighth = 0.5, etc.
     */
    val quarterBeats: Double
        get() {
            val base = type.quarterBeats
            var total = base
            var dotValue = base
            repeat(dots) {
                dotValue /= 2.0
                total += dotValue
            }
            return total
        }

    companion object {
        val WHOLE = Duration(DurationType.WHOLE)
        val HALF = Duration(DurationType.HALF)
        val QUARTER = Duration(DurationType.QUARTER)
        val EIGHTH = Duration(DurationType.EIGHTH)
        val SIXTEENTH = Duration(DurationType.SIXTEENTH)
    }
}

/**
 * Base duration types without dots.
 */
@Serializable
enum class DurationType(val quarterBeats: Double) {
    MAXIMA(32.0),
    LONGA(16.0),
    BREVE(8.0),
    WHOLE(4.0),
    HALF(2.0),
    QUARTER(1.0),
    EIGHTH(0.5),
    SIXTEENTH(0.25),
    THIRTY_SECOND(0.125),
    SIXTY_FOURTH(0.0625),
    ONE_TWENTY_EIGHTH(0.03125)
}

/**
 * Time signature (e.g., 4/4, 3/4, 6/8).
 */
@Serializable
data class TimeSignature(
    val beats: Int = 4,
    val beatType: Int = 4
) {
    /** Number of quarter-note beats per measure. */
    val quarterBeatsPerMeasure: Double
        get() = beats.toDouble() * (4.0 / beatType.toDouble())

    companion object {
        val COMMON_TIME = TimeSignature(4, 4)
        val CUT_TIME = TimeSignature(2, 2)
        val WALTZ_TIME = TimeSignature(3, 4)
    }
}

/**
 * Key signature with fifths-based representation.
 * Positive fifths = sharps, negative = flats, zero = C major / A minor.
 */
@Serializable
data class KeySignature(
    val fifths: Int = 0,
    val mode: KeyMode = KeyMode.MAJOR
) {
    companion object {
        val C_MAJOR = KeySignature(0, KeyMode.MAJOR)
        val A_MINOR = KeySignature(0, KeyMode.MINOR)
        val G_MAJOR = KeySignature(1, KeyMode.MAJOR)
        val F_MAJOR = KeySignature(-1, KeyMode.MAJOR)
    }
}

/**
 * Key mode (major or minor).
 */
@Serializable
enum class KeyMode {
    MAJOR, MINOR
}

/**
 * Clef type for a staff.
 */
@Serializable
data class Clef(
    val sign: ClefSign = ClefSign.G,
    val line: Int = 2
) {
    companion object {
        val TREBLE = Clef(ClefSign.G, 2)
        val BASS = Clef(ClefSign.F, 4)
        val ALTO = Clef(ClefSign.C, 3)
        val TENOR = Clef(ClefSign.C, 4)
    }
}

/**
 * Clef sign types.
 */
@Serializable
enum class ClefSign {
    G, F, C, PERCUSSION, TAB
}

/**
 * Musical articulations that can be applied to notes.
 */
@Serializable
enum class Articulation {
    STACCATO,
    STACCATISSIMO,
    TENUTO,
    ACCENT,
    MARCATO,
    LEGATO,
    FERMATA
}

/**
 * Dynamic markings with approximate MIDI velocity values.
 */
@Serializable
enum class Dynamic(val velocity: Int) {
    PPPP(10),
    PPP(20),
    PP(36),
    P(50),
    MP(64),
    MF(80),
    F(96),
    FF(112),
    FFF(120),
    FFFF(127)
}

/**
 * Position within a measure, expressed in quarter-note beats from the start.
 */
@JvmInline
@Serializable
value class RhythmicPosition(val beats: Double) {
    companion object {
        val START = RhythmicPosition(0.0)
    }
}
