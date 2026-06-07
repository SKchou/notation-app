package com.notation.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PitchTest {

    @Test
    fun testMidiNoteCalculation() {
        // C4 (Middle C) = MIDI 60
        val middleC = Pitch(PitchStep.C, 0, 4)
        assertEquals(60, middleC.midiNote)

        // A4 = MIDI 69
        val a4 = Pitch(PitchStep.A, 0, 4)
        assertEquals(69, a4.midiNote)

        // C#4 = MIDI 61
        val cSharp4 = Pitch(PitchStep.C, 1, 4)
        assertEquals(61, cSharp4.midiNote)

        // Bb3 = MIDI 58
        val bFlat3 = Pitch(PitchStep.B, -1, 3)
        assertEquals(58, bFlat3.midiNote)
    }

    @Test
    fun testFromMidiNote() {
        assertEquals(Pitch(PitchStep.C, 0, 4), Pitch.fromMidiNote(60))
        assertEquals(Pitch(PitchStep.A, 0, 4), Pitch.fromMidiNote(69))
        
        // Default spelling for MIDI 61 is C#
        assertEquals(Pitch(PitchStep.C, 1, 4), Pitch.fromMidiNote(61))
        
        // Default spelling for MIDI 58 is Bb
        assertEquals(Pitch(PitchStep.B, -1, 3), Pitch.fromMidiNote(58))
    }

    @Test
    fun testStaffPosition() {
        val middleC = Pitch(PitchStep.C, 0, 4)
        assertEquals(0, middleC.staffPosition)

        val d4 = Pitch(PitchStep.D, 0, 4)
        assertEquals(1, d4.staffPosition)

        val b3 = Pitch(PitchStep.B, 0, 3)
        assertEquals(-1, b3.staffPosition)

        val c5 = Pitch(PitchStep.C, 0, 5)
        assertEquals(7, c5.staffPosition)
    }

    @Test
    fun testPitchComparison() {
        val c4 = Pitch(PitchStep.C, 0, 4)
        val d4 = Pitch(PitchStep.D, 0, 4)
        val cSharp4 = Pitch(PitchStep.C, 1, 4)
        
        assertTrue(c4 < d4)
        assertTrue(c4 < cSharp4)
        assertTrue(cSharp4 < d4)
    }
}
