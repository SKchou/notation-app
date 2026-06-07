package com.notation.model

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentListOf

/**
 * Identifies a voice within a staff (1-4 per staff, standard notation convention).
 */
@Serializable
@JvmInline
value class VoiceId(val value: Int) {
    init { require(value in 1..4) { "VoiceId must be 1..4, got $value" } }
}

/**
 * A voice is a sequence of musical elements that share a single rhythmic stream.
 * Multiple voices on the same staff allow independent rhythms (e.g., soprano + alto).
 */
@Serializable
data class Voice(
    val id: VoiceId,
    val elements: PersistentList<MusicElement>
) {
    /** Total duration of all elements in this voice, in quarter beats. */
    val totalBeats: Double get() = elements.sumOf { it.duration.quarterBeats }
}

/**
 * A single measure (bar) of music.
 * Contains up to 4 voices of musical elements, plus optional attribute changes.
 */
@Serializable
data class Measure(
    val number: Int,
    val timeSignature: TimeSignature? = null,   // null = inherited from previous measure
    val keySignature: KeySignature? = null,      // null = inherited
    val clef: Clef? = null,                      // Mid-measure clef change
    val voices: PersistentMap<VoiceId, Voice> = persistentMapOf(),
    val barlineEnd: Barline = Barline.NORMAL
) {
    /** Get or create a voice. */
    fun voiceOrEmpty(voiceId: VoiceId): Voice =
        voices[voiceId] ?: Voice(voiceId, persistentListOf())

    /** Find an element by ID across all voices. Returns the element and its voice ID. */
    fun findElement(elementId: ElementId): Pair<VoiceId, MusicElement>? {
        for ((voiceId, voice) in voices) {
            val element = voice.elements.firstOrNull { it.id == elementId }
            if (element != null) return voiceId to element
        }
        return null
    }
}
