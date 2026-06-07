package com.notation.state

import com.notation.model.*
import kotlinx.serialization.Serializable

/**
 * All mutations to the score are expressed as immutable command objects.
 * Commands are the ONLY way to modify the score state.
 *
 * Each command describes a single logical operation. The [CommandExecutor]
 * processes commands to produce a new [Score] and a reverse command for undo.
 */
@Serializable
sealed interface ScoreCommand {
    /** Human-readable description of this command (shown in undo/redo menus). */
    val description: String

    /** Insert a new note at the specified position. */
    @Serializable
    data class InsertNote(
        val partId: PartId,
        val measureIndex: Int,
        val pitch: Pitch,
        val duration: Duration,
        val voice: VoiceId,
        val position: RhythmicPosition,
        val velocity: Int = 80,
        override val description: String = "Insert Note"
    ) : ScoreCommand

    /** Delete elements by their IDs. */
    @Serializable
    data class DeleteElements(
        val elementIds: Set<ElementId>,
        override val description: String = "Delete"
    ) : ScoreCommand

    /** Change the pitch of a note or chord. */
    @Serializable
    data class ChangePitch(
        val elementId: ElementId,
        val newPitch: Pitch,
        override val description: String = "Change Pitch"
    ) : ScoreCommand

    /** Change the duration of an element. */
    @Serializable
    data class ChangeDuration(
        val elementId: ElementId,
        val newDuration: Duration,
        override val description: String = "Change Duration"
    ) : ScoreCommand

    /** Transpose selected elements by an interval in semitones. */
    @Serializable
    data class Transpose(
        val elementIds: Set<ElementId>,
        val semitones: Int,
        override val description: String = "Transpose"
    ) : ScoreCommand

    /** Insert empty measures after a given measure. */
    @Serializable
    data class InsertMeasure(
        val afterMeasure: Int,
        val count: Int = 1,
        val timeSignature: TimeSignature? = null,
        override val description: String = "Insert Measure"
    ) : ScoreCommand

    /** Delete measures by range (inclusive start and end). */
    @Serializable
    data class DeleteMeasures(
        val measureRange: IntRange,
        override val description: String = "Delete Measures"
    ) : ScoreCommand

    /** Change the time signature at a given measure. */
    @Serializable
    data class ChangeTimeSignature(
        val measureNumber: Int,
        val newTimeSig: TimeSignature,
        override val description: String = "Change Time Signature"
    ) : ScoreCommand

    /** Change the key signature at a given measure. */
    @Serializable
    data class ChangeKeySignature(
        val measureNumber: Int,
        val newKeySig: KeySignature,
        override val description: String = "Change Key Signature"
    ) : ScoreCommand

    /** Add or change an articulation on an element. */
    @Serializable
    data class AddArticulation(
        val elementId: ElementId,
        val articulation: Articulation,
        override val description: String = "Add Articulation"
    ) : ScoreCommand

    /** Remove an articulation from an element. */
    @Serializable
    data class RemoveArticulation(
        val elementId: ElementId,
        val articulation: Articulation,
        override val description: String = "Remove Articulation"
    ) : ScoreCommand

    /** Set the dynamic on an element (null to remove). */
    @Serializable
    data class SetDynamic(
        val elementId: ElementId,
        val dynamic: Dynamic?,
        override val description: String = "Set Dynamic"
    ) : ScoreCommand

    /** Toggle tie forward on a note. */
    @Serializable
    data class ToggleTie(
        val elementId: ElementId,
        override val description: String = "Toggle Tie"
    ) : ScoreCommand

    /** Update score metadata (title, composer, tempo, etc.). */
    @Serializable
    data class UpdateMetadata(
        val newMetadata: ScoreMetadata,
        override val description: String = "Update Metadata"
    ) : ScoreCommand

    /** A batch of commands executed atomically as a single undo unit. */
    @Serializable
    data class Batch(
        val commands: List<ScoreCommand>,
        override val description: String
    ) : ScoreCommand
}
