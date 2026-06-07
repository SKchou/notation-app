package com.notation.model

import kotlinx.serialization.Serializable

/**
 * Rhythmic duration of a musical element.
 * Expressed as a note type (whole, half, quarter...) with optional dots and tuplet ratio.
 */
@Serializable
data class Duration(
    val type: DurationType,
    val dots: Int = 0,
    val tupletRatio: TupletRatio? = null
) {
    /**
     * Duration expressed in quarter-note beats.
     * 1.0 = quarter note, 2.0 = half note, 0.5 = eighth note, etc.
     */
    val quarterBeats: Double get() {
        var beats = type.quarterBeats
        var dotValue = beats / 2.0
        repeat(dots) {
            beats += dotValue
            dotValue /= 2.0
        }
        tupletRatio?.let {
            beats *= it.normal.toDouble() / it.actual
        }
        return beats
    }

    companion object {
        val WHOLE = Duration(DurationType.WHOLE)
        val HALF = Duration(DurationType.HALF)
        val QUARTER = Duration(DurationType.QUARTER)
        val EIGHTH = Duration(DurationType.EIGHTH)
        val SIXTEENTH = Duration(DurationType.SIXTEENTH)
    }
}

/** Standard note duration types from maxima down to 128th. */
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
 * Tuplet ratio: [actual] notes in the time of [normal] notes.
 * Example: TupletRatio(3, 2) = triplet (3 in the time of 2).
 */
@Serializable
data class TupletRatio(
    val actual: Int,
    val normal: Int
)

/**
 * Position within a measure, expressed in quarter-note beats from the start.
 */
@Serializable
@JvmInline
value class RhythmicPosition(val quarterBeats: Double) : Comparable<RhythmicPosition> {
    override fun compareTo(other: RhythmicPosition) =
        quarterBeats.compareTo(other.quarterBeats)

    operator fun plus(duration: Duration) =
        RhythmicPosition(quarterBeats + duration.quarterBeats)

    companion object {
        val ZERO = RhythmicPosition(0.0)
    }
}
