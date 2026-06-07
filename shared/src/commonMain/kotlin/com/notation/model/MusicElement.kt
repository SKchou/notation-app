package com.notation.model

import kotlinx.serialization.Serializable
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import com.benasher44.uuid.uuid4

/**
 * Stable, unique identity for any music element in the score.
 * Used for selection, undo/redo targeting, and cross-referencing.
 */
@Serializable
@JvmInline
value class ElementId(val value: String = uuid4().toString())

/**
 * Sealed hierarchy of all musical events that occupy rhythmic time.
 * Each element knows its own duration and position within its measure.
 */
@Serializable
sealed interface MusicElement {
    val id: ElementId
    val duration: Duration
    val position: RhythmicPosition
    val articulations: PersistentList<Articulation>
    val dynamics: Dynamic?
    val expressions: PersistentList<Expression>
}

/** A single pitched note. */
@Serializable
data class Note(
    override val id: ElementId = ElementId(),
    override val duration: Duration,
    override val position: RhythmicPosition,
    override val articulations: PersistentList<Articulation> = persistentListOf(),
    override val dynamics: Dynamic? = null,
    override val expressions: PersistentList<Expression> = persistentListOf(),
    val pitch: Pitch,
    val tieForward: Boolean = false,
    val beam: BeamGroup? = null,
    val tuplet: TupletGroup? = null,
    val lyrics: PersistentList<LyricSyllable> = persistentListOf(),
    val stemDirection: StemDirection = StemDirection.AUTO,
    val noteHead: NoteHead = NoteHead.NORMAL
) : MusicElement

/** Multiple pitches sounding simultaneously with the same duration. */
@Serializable
data class Chord(
    override val id: ElementId = ElementId(),
    override val duration: Duration,
    override val position: RhythmicPosition,
    override val articulations: PersistentList<Articulation> = persistentListOf(),
    override val dynamics: Dynamic? = null,
    override val expressions: PersistentList<Expression> = persistentListOf(),
    val pitches: PersistentList<Pitch>,  // Sorted low to high
    val stemDirection: StemDirection = StemDirection.AUTO,
    val tieForward: Boolean = false,
    val beam: BeamGroup? = null,
    val tuplet: TupletGroup? = null,
    val lyrics: PersistentList<LyricSyllable> = persistentListOf()
) : MusicElement

/** A rest (silence) of a given duration. */
@Serializable
data class Rest(
    override val id: ElementId = ElementId(),
    override val duration: Duration,
    override val position: RhythmicPosition,
    override val articulations: PersistentList<Articulation> = persistentListOf(),
    override val dynamics: Dynamic? = null,
    override val expressions: PersistentList<Expression> = persistentListOf(),
    val isFullMeasure: Boolean = false
) : MusicElement
