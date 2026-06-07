package com.notation.model

import kotlinx.serialization.Serializable
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.toPersistentList

/**
 * Metadata about the score (title, composer, etc.).
 */
@Serializable
data class ScoreMetadata(
    val title: String = "",
    val composer: String = "",
    val arranger: String = "",
    val copyright: String = "",
    val createdAt: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
)

/**
 * Top-level container for an entire musical score.
 * Immutable — all mutations produce new instances via copy().
 */
@Serializable
data class Score(
    val metadata: ScoreMetadata = ScoreMetadata(),
    val parts: PersistentList<Part> = persistentListOf(),
    val systems: PersistentList<SystemBreak> = persistentListOf(),
    val globalDirections: PersistentList<Direction> = persistentListOf()
) {
    /** Total number of measures (all parts share the same measure count). */
    val measureCount: Int get() = parts.firstOrNull()?.measures?.size ?: 0

    /** Find a part by its ID. */
    fun partById(partId: PartId): Part? = parts.firstOrNull { it.id == partId }

    /** Find any element by its ID across all parts. Returns (PartId, measureIndex, VoiceId, element). */
    fun findElement(elementId: ElementId): FindResult? {
        for (part in parts) {
            val found = part.findElement(elementId)
            if (found != null) {
                return FindResult(part.id, found.first, found.second, found.third)
            }
        }
        return null
    }

    /**
     * Replace a specific element by its ID with a new element.
     * Returns a new Score with the replacement applied.
     */
    fun replaceElement(elementId: ElementId, newElement: MusicElement): Score {
        val result = findElement(elementId) ?: return this
        return replaceElementInPart(result.partId, result.measureIndex, result.voiceId, elementId, newElement)
    }

    /**
     * Remove elements by their IDs.
     * Returns a new Score with the elements removed.
     */
    fun removeElements(elementIds: Set<ElementId>): Score {
        val newParts = parts.mutate { partsList ->
            for (i in partsList.indices) {
                val part = partsList[i]
                val newMeasures = part.measures.mutate { measuresList ->
                    for (j in measuresList.indices) {
                        val measure = measuresList[j]
                        val newVoices = measure.voices.entries.associate { (voiceId, voice) ->
                            val filtered = voice.elements.removeAll { it.id in elementIds }
                            voiceId to voice.copy(elements = filtered)
                        }.let { map ->
                            persistentMapOf<VoiceId, Voice>().putAll(map)
                        }
                        measuresList[j] = measure.copy(voices = newVoices)
                    }
                }
                partsList[i] = part.copy(measures = newMeasures)
            }
        }
        return copy(parts = newParts)
    }

    /**
     * Add an element to a specific voice in a specific measure of a specific part.
     */
    fun addElement(partId: PartId, measureIndex: Int, voiceId: VoiceId, element: MusicElement): Score {
        val newParts = parts.mutate { partsList ->
            val partIndex = partsList.indexOfFirst { it.id == partId }
            if (partIndex == -1) return@mutate
            val part = partsList[partIndex]
            val newMeasures = part.measures.mutate { measuresList ->
                val measure = measuresList[measureIndex]
                val voice = measure.voiceOrEmpty(voiceId)
                val newElements = voice.elements.add(element)
                    .sortedBy { it.position.quarterBeats }
                    .toPersistentList()
                val newVoice = voice.copy(elements = newElements)
                val newVoices = measure.voices.put(voiceId, newVoice)
                measuresList[measureIndex] = measure.copy(voices = newVoices)
            }
            partsList[partIndex] = part.copy(measures = newMeasures)
        }
        return copy(parts = newParts)
    }

    private fun replaceElementInPart(
        partId: PartId,
        measureIndex: Int,
        voiceId: VoiceId,
        elementId: ElementId,
        newElement: MusicElement
    ): Score {
        val newParts = parts.mutate { partsList ->
            val partIndex = partsList.indexOfFirst { it.id == partId }
            if (partIndex == -1) return@mutate
            val part = partsList[partIndex]
            val newMeasures = part.measures.mutate { measuresList ->
                val measure = measuresList[measureIndex]
                val voice = measure.voices[voiceId] ?: return@mutate
                val newElements = voice.elements.mutate { elementsList ->
                    val elemIndex = elementsList.indexOfFirst { it.id == elementId }
                    if (elemIndex != -1) elementsList[elemIndex] = newElement
                }
                val newVoices = measure.voices.put(voiceId, voice.copy(elements = newElements))
                measuresList[measureIndex] = measure.copy(voices = newVoices)
            }
            partsList[partIndex] = part.copy(measures = newMeasures)
        }
        return copy(parts = newParts)
    }

    /** Result of finding an element in the score. */
    data class FindResult(
        val partId: PartId,
        val measureIndex: Int,
        val voiceId: VoiceId,
        val element: MusicElement
    )

    companion object {
        /** Create an empty score with the given number of empty measures for each part. */
        fun create(
            title: String = "Untitled",
            parts: List<Pair<String, Instrument>>,
            measureCount: Int = 1,
            timeSignature: TimeSignature = TimeSignature.COMMON_TIME,
            keySignature: KeySignature = KeySignature(0)
        ): Score {
            val scoreParts = parts.mapIndexed { index, (name, instrument) ->
                val measures = (1..measureCount).map { measureNum ->
                    Measure(
                        number = measureNum,
                        timeSignature = if (measureNum == 1) timeSignature else null,
                        keySignature = if (measureNum == 1) keySignature else null,
                        clef = if (measureNum == 1) instrument.clefs.firstOrNull() else null
                    )
                }.toPersistentList()
                Part(
                    id = PartId("part_${index + 1}"),
                    name = name,
                    abbreviation = name.take(4).trimEnd() + ".",
                    instrument = instrument,
                    staves = if (instrument.clefs.size > 1) 2 else 1,
                    measures = measures
                )
            }.toPersistentList()

            return Score(
                metadata = ScoreMetadata(title = title),
                parts = scoreParts
            )
        }
    }
}
