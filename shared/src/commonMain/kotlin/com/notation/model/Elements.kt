package com.notation.model

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.Serializable

/**
 * A single musical element within a measure voice.
 * Elements are the atomic units of musical content.
 */
@Serializable
sealed interface Element {
    val id: ElementId
    val duration: Duration
    val voice: VoiceId
    val position: RhythmicPosition
}

/**
 * A single note with pitch, duration, and optional markings.
 */
@Serializable
data class Note(
    override val id: ElementId = ElementId.generate(),
    override val duration: Duration,
    override val voice: VoiceId = VoiceId.DEFAULT,
    override val position: RhythmicPosition = RhythmicPosition.START,
    val pitch: Pitch,
    val velocity: Int = 80,
    val tieForward: Boolean = false,
    val articulations: PersistentList<Articulation> = persistentListOf(),
    val dynamic: Dynamic? = null
) : Element

/**
 * A chord — multiple simultaneous pitches with a shared duration.
 */
@Serializable
data class Chord(
    override val id: ElementId = ElementId.generate(),
    override val duration: Duration,
    override val voice: VoiceId = VoiceId.DEFAULT,
    override val position: RhythmicPosition = RhythmicPosition.START,
    val pitches: PersistentList<Pitch>,
    val velocity: Int = 80,
    val tieForward: Boolean = false,
    val articulations: PersistentList<Articulation> = persistentListOf(),
    val dynamic: Dynamic? = null
) : Element

/**
 * A rest — silence for a given duration.
 */
@Serializable
data class Rest(
    override val id: ElementId = ElementId.generate(),
    override val duration: Duration,
    override val voice: VoiceId = VoiceId.DEFAULT,
    override val position: RhythmicPosition = RhythmicPosition.START
) : Element
