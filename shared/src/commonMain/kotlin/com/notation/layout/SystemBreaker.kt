package com.notation.layout

import com.notation.model.*

/**
 * Computes system breaks (how measures are grouped into horizontal systems)
 * and page breaks (how systems are grouped onto pages).
 *
 * Uses a greedy algorithm: measures are accumulated until their combined
 * natural width exceeds the usable page width, then a break is inserted.
 * Explicit [SystemBreak] markers from the score are always honoured.
 *
 * @param rules Engraving parameters for system/page spacing.
 * @param pageSetup Page dimensions used for available width/height calculations.
 */
class SystemBreaker(
    private val rules: EngravingRules,
    private val pageSetup: PageSetup
) {
    /**
     * Result of system-break computation: a measure range and its assigned page.
     *
     * @param measureRange The zero-based index range of measures in this system.
     * @param pageIndex The zero-based page this system is assigned to.
     */
    data class SystemBreakResult(
        val measureRange: IntRange,
        val pageIndex: Int
    )

    /**
     * Compute system and page breaks for the entire score.
     *
     * @param score The score to break into systems.
     * @param spacer A [HorizontalSpacer] used to estimate measure widths.
     * @return Ordered list of [SystemBreakResult]s covering all measures.
     */
    fun computeBreaks(score: Score, spacer: HorizontalSpacer): List<SystemBreakResult> {
        if (score.measureCount == 0) return emptyList()

        val explicitBreaks = score.systems.map { it.afterMeasure }.toSet()
        val explicitPageBreaks = score.systems
            .filter { it.pageBreak }
            .map { it.afterMeasure }
            .toSet()

        val usableWidth = pageSetup.usableWidthSS

        // Phase 1: group measures into systems
        val systems = mutableListOf<IntRange>()
        var systemStart = 0
        var accumulatedWidth = 0.0

        for (measureIndex in 0 until score.measureCount) {
            val measureWidth = estimateMeasureWidth(score, spacer, measureIndex)
            val wouldExceed = accumulatedWidth + measureWidth > usableWidth.value && measureIndex > systemStart

            // Check for explicit break after the *previous* measure
            val previousMeasure = if (measureIndex > 0) {
                score.parts.firstOrNull()?.measures?.getOrNull(measureIndex - 1)
            } else null
            val explicitBreakHere = previousMeasure != null &&
                    previousMeasure.number in explicitBreaks

            if (wouldExceed || explicitBreakHere) {
                systems.add(systemStart until measureIndex)
                systemStart = measureIndex
                accumulatedWidth = 0.0
            }

            accumulatedWidth += measureWidth
        }

        // Final system
        if (systemStart < score.measureCount) {
            systems.add(systemStart until score.measureCount)
        }

        // Phase 2: assign systems to pages
        val systemHeight = computeSystemHeight(score)
        val usableHeight = pageSetup.usableHeightSS
        val results = mutableListOf<SystemBreakResult>()
        var currentPage = 0
        var accumulatedHeight = 0.0

        for ((index, system) in systems.withIndex()) {
            val heightNeeded = systemHeight.value +
                    if (index > 0 && accumulatedHeight > 0.0) rules.systemDistance else 0.0

            // Check for explicit page break
            val lastMeasureNumber = score.parts.firstOrNull()
                ?.measures?.getOrNull(system.last)?.number ?: 0
            val forcePageBreak = lastMeasureNumber in explicitPageBreaks && index < systems.lastIndex

            if (accumulatedHeight + heightNeeded > usableHeight.value && accumulatedHeight > 0.0) {
                currentPage++
                accumulatedHeight = 0.0
            }

            results.add(SystemBreakResult(system, currentPage))
            accumulatedHeight += heightNeeded

            if (forcePageBreak) {
                currentPage++
                accumulatedHeight = 0.0
            }
        }

        return results
    }

    /**
     * Estimate the natural width of a single measure by summing its
     * spacing column natural widths, plus measure padding and barline width.
     */
    private fun estimateMeasureWidth(
        score: Score,
        spacer: HorizontalSpacer,
        measureIndex: Int
    ): Double {
        val columns = spacer.computeSpacing(
            score,
            measureIndex..measureIndex,
            StaffSpaces(1000.0) // large target → columns stay at natural width
        )

        val contentWidth = columns.sumOf { it.naturalWidth.value }
        val paddingWidth = rules.measureLeftPadding + rules.measureRightPadding + rules.barlineWidth

        // Add width for clef/time signature if present in this measure
        val measure = score.parts.firstOrNull()?.measures?.getOrNull(measureIndex)
        var attributeWidth = 0.0
        if (measure?.clef != null) attributeWidth += 3.0 + rules.clefPadding
        if (measure?.timeSignature != null) attributeWidth += 2.5 + rules.timeSignaturePadding
        if (measure?.keySignature != null) {
            attributeWidth += measure.keySignature.accidentalCount * 0.8 + rules.keySignaturePadding
        }

        return maxOf(contentWidth + paddingWidth + attributeWidth, rules.minimumNoteSpacing * 2.0)
    }

    /**
     * Compute the height of one system (all staves + gaps between them).
     */
    private fun computeSystemHeight(score: Score): StaffSpaces {
        if (score.parts.isEmpty()) return StaffSpaces(4.0) // single staff default

        var height = 0.0
        var isFirstStaff = true

        for (part in score.parts) {
            for (staffIndex in 0 until part.staves) {
                if (!isFirstStaff) {
                    height += if (staffIndex > 0) {
                        rules.systemStaffDistance * 0.75
                    } else {
                        rules.systemStaffDistance
                    }
                }
                height += (rules.staffLineCount - 1).toDouble()
                isFirstStaff = false
            }
        }

        return StaffSpaces(height)
    }
}
