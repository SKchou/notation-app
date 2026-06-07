package com.notation.model

import kotlinx.serialization.Serializable

/**
 * Represents a musical pitch with step (C-B), alteration (sharps/flats), and octave.
 * Uses scientific pitch notation: C4 = middle C.
 */
@Serializable
data class Pitch(
    val step: PitchStep,
    val alter: Int = 0,           // -2 (double flat) to +2 (double sharp)
    val octave: Int               // Scientific pitch: C4 = middle C
) : Comparable<Pitch> {
    /** MIDI note number (0-127). */
    val midiNote: Int get() = (octave + 1) * 12 + step.semitoneOffset + alter

    /** Staff position relative to middle C (C4 = 0, D4 = 1, B3 = -1). */
    val staffPosition: Int get() = (octave - 4) * 7 + step.ordinal

    override fun compareTo(other: Pitch) = midiNote.compareTo(other.midiNote)

    companion object {
        /** Create a Pitch from a MIDI note number. Assumes no accidentals (natural spelling). */
        fun fromMidiNote(midiNote: Int): Pitch {
            val octave = (midiNote / 12) - 1
            val semitone = midiNote % 12
            val (step, alter) = SEMITONE_TO_PITCH[semitone]
            return Pitch(step, alter, octave)
        }

        private val SEMITONE_TO_PITCH = arrayOf(
            PitchStep.C to 0,   // 0
            PitchStep.C to 1,   // 1  C#
            PitchStep.D to 0,   // 2
            PitchStep.E to -1,  // 3  Eb
            PitchStep.E to 0,   // 4
            PitchStep.F to 0,   // 5
            PitchStep.F to 1,   // 6  F#
            PitchStep.G to 0,   // 7
            PitchStep.A to -1,  // 8  Ab
            PitchStep.A to 0,   // 9
            PitchStep.B to -1,  // 10 Bb
            PitchStep.B to 0    // 11
        )

        val MIDDLE_C = Pitch(PitchStep.C, 0, 4)
    }
}

/** The seven natural pitch names. */
@Serializable
enum class PitchStep(val semitoneOffset: Int) {
    C(0), D(2), E(4), F(5), G(7), A(9), B(11)
}

/** Defines a valid pitch range for an instrument. */
@Serializable
data class PitchRange(
    val lowest: Pitch,
    val highest: Pitch
) {
    fun contains(pitch: Pitch): Boolean =
        pitch.midiNote in lowest.midiNote..highest.midiNote
}
