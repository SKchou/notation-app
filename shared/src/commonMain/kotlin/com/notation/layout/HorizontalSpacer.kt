package com.notation.layout

import com.notation.model.*
import kotlin.math.pow

/**
 * Horizontal spacing engine for music notation.
 *
 * Implements cube-root proportional spacing (Gould convention) with a spring-rod
 * justification model. Each rhythmic position is assigned a "spring" whose natural
 * length is proportional to the cube root of its rhythmic duration. When the system
 * is justified to a target width, excess (or deficit) space is distributed
 * proportionally to each spring's compliance (inverse of spring constant).
 *
 * @param rules Engraving parameters controlling base spacing and minimums.
 * @param metrics Glyph metrics for computing minimum column widths.
 */
class HorizontalSpacer(
    private val rules: EngravingRules,
    private val metrics: GlyphMetrics
) {
    /**
     * A single spacing column representing one unique rhythmic position across all parts.
     *
     * @param rhythmicPosition The beat position within the measure range.
     * @param naturalWidth The proportional (pre-justification) width in staff-spaces.
     * @param minimumWidth The hard minimum width required by glyph extents and padding.
     * @param springConstant Stiffness of the spring at this column (1 / naturalWidth).
     * @param elements Map of part IDs to the music elements occurring at this position.
     */
    data class SpacingColumn(
        val rhythmicPosition: RhythmicPosition,
        val naturalWidth: StaffSpaces,
        val minimumWidth: StaffSpaces,
        val springConstant: Double,
        val elements: Map<PartId, List<MusicElement>>
    )

    /**
     * Compute justified spacing columns for the given measure range across all parts.
     *
     * The algorithm:
     * 1. Collect every unique [RhythmicPosition] that any element occupies across all
     *    parts in the given measure range.
     * 2. For each position compute the natural width (cube-root proportional to the
     *    shortest duration at that position) and the minimum width (notehead + padding).
     * 3. Justify columns to [targetSystemWidth] using a spring-rod model: excess space
     *    is distributed proportionally to each spring's compliance (1/springConstant).
     *
     * @param score The full score to space.
     * @param measures Zero-based index range of measures to include.
     * @param targetSystemWidth The width the columns must be justified to.
     * @return Ordered list of spacing columns, sorted by rhythmic position.
     */
    fun computeSpacing(
        score: Score,
        measures: IntRange,
        targetSystemWidth: StaffSpaces
    ): List<SpacingColumn> {
        // 1. Collect all unique rhythmic positions and the elements at each
        val positionMap = mutableMapOf<RhythmicPosition, MutableMap<PartId, MutableList<MusicElement>>>()

        var cumulativeOffset = 0.0 // beat offset for stacking measures end-to-end
        for (measureIndex in measures) {
            for (part in score.parts) {
                if (measureIndex !in part.measures.indices) continue
                val measure = part.measures[measureIndex]
                for ((_, voice) in measure.voices) {
                    for (element in voice.elements) {
                        val absolutePosition = RhythmicPosition(
                            element.position.quarterBeats + cumulativeOffset
                        )
                        val partMap = positionMap.getOrPut(absolutePosition) { mutableMapOf() }
                        partMap.getOrPut(part.id) { mutableListOf() }.add(element)
                    }
                }
            }
            // Advance cumulative offset by the measure's time-signature duration
            val effectiveTimeSig = resolveTimeSignature(score, measureIndex)
            cumulativeOffset += effectiveTimeSig.quarterBeatsPerMeasure
        }

        if (positionMap.isEmpty()) return emptyList()

        // 2. Sort positions and compute per-column spacing
        val sortedPositions = positionMap.keys.sorted()
        val rawColumns = sortedPositions.mapIndexed { index, position ->
            val elementsAtPos = positionMap[position]!!

            // Shortest duration at this position drives the natural width
            val shortestDuration = elementsAtPos.values.flatten()
                .minOf { it.duration.quarterBeats }
                .coerceAtLeast(0.0625) // floor at 64th note to avoid zero

            val naturalWidth = StaffSpaces(
                rules.baseSpacing * shortestDuration.pow(1.0 / 3.0)
            )

            // Minimum width = widest notehead glyph at this position + padding
            val minGlyphWidth = elementsAtPos.values.flatten().maxOfOrNull { element ->
                noteheadAdvanceWidth(element)
            } ?: StaffSpaces(0.0)
            val minimumWidth = StaffSpaces(
                maxOf(minGlyphWidth.value + rules.minimumNoteSpacing * 0.5, rules.minimumNoteSpacing)
            )

            val springConstant = if (naturalWidth.value > 0.0) {
                1.0 / naturalWidth.value
            } else {
                1.0
            }

            SpacingColumn(
                rhythmicPosition = position,
                naturalWidth = naturalWidth,
                minimumWidth = minimumWidth,
                springConstant = springConstant,
                elements = elementsAtPos.mapValues { it.value.toList() }
            )
        }

        // 3. Justify via spring-rod model
        return justify(rawColumns, targetSystemWidth)
    }

    /**
     * Distributes excess (or deficit) space among columns proportionally to each
     * spring's compliance (1 / springConstant). Ensures no column shrinks below
     * its minimum width.
     */
    private fun justify(
        columns: List<SpacingColumn>,
        targetWidth: StaffSpaces
    ): List<SpacingColumn> {
        if (columns.isEmpty()) return columns

        val totalNaturalWidth = columns.sumOf { it.naturalWidth.value }
        val excess = targetWidth.value - totalNaturalWidth

        if (excess == 0.0) return columns

        // Total compliance
        val totalCompliance = columns.sumOf { 1.0 / it.springConstant }
        if (totalCompliance == 0.0) return columns

        return columns.map { column ->
            val compliance = 1.0 / column.springConstant
            val share = excess * (compliance / totalCompliance)
            val newWidth = StaffSpaces(
                maxOf(column.naturalWidth.value + share, column.minimumWidth.value)
            )
            column.copy(naturalWidth = newWidth)
        }
    }

    /**
     * Determines the advance width of the primary notehead glyph for an element,
     * used to compute minimum column width.
     */
    private fun noteheadAdvanceWidth(element: MusicElement): StaffSpaces {
        val codepoint = when (element) {
            is Note -> noteheadCodepoint(element.duration.type)
            is Chord -> noteheadCodepoint(element.duration.type)
            is Rest -> restCodepoint(element.duration.type)
        }
        return metrics.advanceWidth(codepoint)
    }

    /** Maps a duration type to the appropriate SMuFL notehead codepoint. */
    private fun noteheadCodepoint(type: DurationType): Char = when (type) {
        DurationType.WHOLE -> SMuFL.NOTEHEAD_WHOLE
        DurationType.BREVE, DurationType.MAXIMA, DurationType.LONGA -> SMuFL.NOTEHEAD_DOUBLE_WHOLE
        DurationType.HALF -> SMuFL.NOTEHEAD_HALF
        else -> SMuFL.NOTEHEAD_FILLED
    }

    /** Maps a duration type to the appropriate SMuFL rest codepoint. */
    private fun restCodepoint(type: DurationType): Char = when (type) {
        DurationType.WHOLE, DurationType.BREVE, DurationType.LONGA, DurationType.MAXIMA ->
            SMuFL.REST_WHOLE
        DurationType.HALF -> SMuFL.REST_HALF
        DurationType.QUARTER -> SMuFL.REST_QUARTER
        DurationType.EIGHTH -> SMuFL.REST_EIGHTH
        DurationType.SIXTEENTH -> SMuFL.REST_SIXTEENTH
        DurationType.THIRTY_SECOND -> SMuFL.REST_32ND
        DurationType.SIXTY_FOURTH -> SMuFL.REST_64TH
        DurationType.ONE_TWENTY_EIGHTH -> SMuFL.REST_64TH // fallback
    }

    /**
     * Resolves the effective time signature for a given measure index by walking
     * backwards to find the most recent explicit time signature.
     */
    private fun resolveTimeSignature(score: Score, measureIndex: Int): TimeSignature {
        val part = score.parts.firstOrNull() ?: return TimeSignature.COMMON_TIME
        for (i in measureIndex downTo 0) {
            if (i in part.measures.indices) {
                part.measures[i].timeSignature?.let { return it }
            }
        }
        return TimeSignature.COMMON_TIME
    }
}
