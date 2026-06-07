package com.notation.model

import com.benasher44.uuid.uuid4
import kotlinx.serialization.Serializable

/**
 * Type-safe identifier for a musical element (note, rest, chord).
 */
@JvmInline
@Serializable
value class ElementId(val value: String) {
    companion object {
        fun generate(): ElementId = ElementId(uuid4().toString())
    }
}

/**
 * Type-safe identifier for a part/instrument.
 */
@JvmInline
@Serializable
value class PartId(val value: String) {
    companion object {
        fun generate(): PartId = PartId(uuid4().toString())
    }
}

/**
 * Type-safe identifier for a voice within a staff.
 */
@JvmInline
@Serializable
value class VoiceId(val value: Int) {
    companion object {
        val DEFAULT = VoiceId(1)
    }
}

/**
 * Type-safe identifier for a score document.
 */
@JvmInline
@Serializable
value class ScoreId(val value: String) {
    companion object {
        fun generate(): ScoreId = ScoreId(uuid4().toString())
    }
}
