package com.notation.model

import kotlinx.serialization.Serializable
import kotlinx.collections.immutable.PersistentList

/** Unique identifier for a part (instrument) in the score. */
@Serializable
@JvmInline
value class PartId(val value: String)

/**
 * Defines an instrument's capabilities and MIDI mapping.
 */
@Serializable
data class Instrument(
    val midiProgram: Int,
    val midiChannel: Int,
    val range: PitchRange,
    val clefs: List<Clef>,
    val soundFontPreset: String? = null
) {
    companion object {
        val PIANO = Instrument(
            midiProgram = 0,
            midiChannel = 0,
            range = PitchRange(
                lowest = Pitch(PitchStep.A, 0, 0),
                highest = Pitch(PitchStep.C, 0, 8)
            ),
            clefs = listOf(Clef.TREBLE, Clef.BASS)
        )
        val VIOLIN = Instrument(
            midiProgram = 40,
            midiChannel = 1,
            range = PitchRange(
                lowest = Pitch(PitchStep.G, 0, 3),
                highest = Pitch(PitchStep.A, 0, 7)
            ),
            clefs = listOf(Clef.TREBLE)
        )
        val CELLO = Instrument(
            midiProgram = 42,
            midiChannel = 2,
            range = PitchRange(
                lowest = Pitch(PitchStep.C, 0, 2),
                highest = Pitch(PitchStep.E, 0, 6)
            ),
            clefs = listOf(Clef.BASS, Clef.TENOR)
        )
        val FLUTE = Instrument(
            midiProgram = 73,
            midiChannel = 3,
            range = PitchRange(
                lowest = Pitch(PitchStep.C, 0, 4),
                highest = Pitch(PitchStep.D, 0, 7)
            ),
            clefs = listOf(Clef.TREBLE)
        )
    }
}

/**
 * A part represents one instrument/performer in the score.
 * Contains all measures for that instrument across the entire piece.
 */
@Serializable
data class Part(
    val id: PartId,
    val name: String,
    val abbreviation: String,
    val instrument: Instrument,
    val staves: Int = 1,
    val measures: PersistentList<Measure>,
    val transposition: Transposition = Transposition.CONCERT
) {
    /** Get a measure by its 1-based number. */
    fun measureByNumber(number: Int): Measure? =
        measures.firstOrNull { it.number == number }

    /** Find an element by ID across all measures. */
    fun findElement(elementId: ElementId): Triple<Int, VoiceId, MusicElement>? {
        for ((index, measure) in measures.withIndex()) {
            val found = measure.findElement(elementId)
            if (found != null) return Triple(index, found.first, found.second)
        }
        return null
    }
}
