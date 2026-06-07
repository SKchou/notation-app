package com.notation.state

import com.notation.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CommandExecutorTest {

    @Test
    fun testInsertNote() {
        val score = Score.create(
            title = "Test",
            parts = listOf("Piano" to Instrument.PIANO),
            measureCount = 1
        )
        val partId = score.parts[0].id

        val cmd = ScoreCommand.InsertNote(
            partId = partId,
            measureIndex = 0,
            pitch = Pitch.MIDDLE_C,
            duration = Duration.QUARTER,
            voice = VoiceId(1),
            position = RhythmicPosition.ZERO
        )

        val result = CommandExecutor.execute(score, cmd)
        
        // Verify state change
        val newScore = result.newScore
        val voice = newScore.parts[0].measures[0].voices[VoiceId(1)]
        assertNotNull(voice)
        assertEquals(1, voice.elements.size)
        val note = voice.elements[0] as Note
        assertEquals(Pitch.MIDDLE_C, note.pitch)

        // Verify reverse command
        assertTrue(result.reverseCommand is ScoreCommand.DeleteElements)
        val reverseCmd = result.reverseCommand as ScoreCommand.DeleteElements
        assertTrue(reverseCmd.elementIds.contains(note.id))
    }

    @Test
    fun testChangePitchUndo() {
        val score = Score.create(
            title = "Test",
            parts = listOf("Piano" to Instrument.PIANO)
        )
        val partId = score.parts[0].id
        
        // Initial insert
        val insertResult = CommandExecutor.execute(score, ScoreCommand.InsertNote(
            partId = partId,
            measureIndex = 0,
            pitch = Pitch.MIDDLE_C,
            duration = Duration.QUARTER,
            voice = VoiceId(1),
            position = RhythmicPosition.ZERO
        ))
        
        val scoreWithNote = insertResult.newScore
        val noteId = scoreWithNote.parts[0].measures[0].voices[VoiceId(1)]!!.elements[0].id

        // Change pitch
        val changePitchResult = CommandExecutor.execute(scoreWithNote, ScoreCommand.ChangePitch(
            elementId = noteId,
            newPitch = Pitch(PitchStep.D, 0, 4)
        ))
        
        val newNote = changePitchResult.newScore.findElement(noteId)!!.element as Note
        assertEquals(PitchStep.D, newNote.pitch.step)

        // Undo
        val undoResult = CommandExecutor.execute(changePitchResult.newScore, changePitchResult.reverseCommand)
        val revertedNote = undoResult.newScore.findElement(noteId)!!.element as Note
        assertEquals(Pitch.MIDDLE_C, revertedNote.pitch)
    }
}
