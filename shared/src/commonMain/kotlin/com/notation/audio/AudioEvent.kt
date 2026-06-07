package com.notation.audio

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a timed audio event within a score, positioned by beat.
 *
 * All audio events are [Comparable] by their [beatPosition], which enables
 * straightforward chronological sorting of heterogeneous event streams.
 */
@Serializable
sealed interface AudioEvent : Comparable<AudioEvent> {
    /** The beat position (in quarter-note beats from the score start) of this event. */
    val beatPosition: Double

    override fun compareTo(other: AudioEvent): Int =
        this.beatPosition.compareTo(other.beatPosition)

    /**
     * A MIDI note-on event: begins sounding a pitch.
     *
     * @property beatPosition Beat position relative to score start.
     * @property channel MIDI channel (0-15).
     * @property midiNote MIDI note number (0-127).
     * @property velocity Attack velocity (0-127).
     */
    @Serializable
    @SerialName("NoteOn")
    data class NoteOn(
        override val beatPosition: Double,
        val channel: Int,
        val midiNote: Int,
        val velocity: Int
    ) : AudioEvent

    /**
     * A MIDI note-off event: stops sounding a pitch.
     *
     * @property beatPosition Beat position relative to score start.
     * @property channel MIDI channel (0-15).
     * @property midiNote MIDI note number (0-127).
     */
    @Serializable
    @SerialName("NoteOff")
    data class NoteOff(
        override val beatPosition: Double,
        val channel: Int,
        val midiNote: Int
    ) : AudioEvent

    /**
     * A tempo change event, altering the playback speed.
     *
     * @property beatPosition Beat position where the tempo takes effect.
     * @property bpm Beats per minute.
     */
    @Serializable
    @SerialName("TempoChange")
    data class TempoChange(
        override val beatPosition: Double,
        val bpm: Double
    ) : AudioEvent

    /**
     * A MIDI control change event (e.g., sustain pedal, volume).
     *
     * @property beatPosition Beat position relative to score start.
     * @property channel MIDI channel (0-15).
     * @property controller MIDI controller number (0-127).
     * @property value Controller value (0-127).
     */
    @Serializable
    @SerialName("ControlChange")
    data class ControlChange(
        override val beatPosition: Double,
        val channel: Int,
        val controller: Int,
        val value: Int
    ) : AudioEvent

    /**
     * A MIDI program change event (instrument selection).
     *
     * @property beatPosition Beat position relative to score start.
     * @property channel MIDI channel (0-15).
     * @property program MIDI program number (0-127).
     */
    @Serializable
    @SerialName("ProgramChange")
    data class ProgramChange(
        override val beatPosition: Double,
        val channel: Int,
        val program: Int
    ) : AudioEvent
}
