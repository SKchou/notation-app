package com.notation.model

import kotlin.test.Test
import kotlin.test.assertEquals

class DurationTest {

    @Test
    fun testQuarterBeatsCalculation() {
        // Basic durations
        assertEquals(4.0, Duration.WHOLE.quarterBeats)
        assertEquals(2.0, Duration.HALF.quarterBeats)
        assertEquals(1.0, Duration.QUARTER.quarterBeats)
        assertEquals(0.5, Duration.EIGHTH.quarterBeats)

        // Dotted durations
        val dottedHalf = Duration(DurationType.HALF, dots = 1)
        assertEquals(3.0, dottedHalf.quarterBeats)

        val doubleDottedQuarter = Duration(DurationType.QUARTER, dots = 2)
        // Quarter (1.0) + Eighth (0.5) + Sixteenth (0.25) = 1.75
        assertEquals(1.75, doubleDottedQuarter.quarterBeats)

        // Tuplets
        val tripletQuarter = Duration(DurationType.QUARTER, tupletRatio = TupletRatio(3, 2))
        // 3 in the time of 2. A normal quarter is 1 beat.
        // A triplet quarter should take 2/3 of a beat.
        assertEquals(2.0 / 3.0, tripletQuarter.quarterBeats)
    }

    @Test
    fun testRhythmicPositionMath() {
        val pos = RhythmicPosition.ZERO
        
        val pos2 = pos + Duration.QUARTER
        assertEquals(1.0, pos2.quarterBeats)

        val pos3 = pos2 + Duration.HALF
        assertEquals(3.0, pos3.quarterBeats)
    }
}
