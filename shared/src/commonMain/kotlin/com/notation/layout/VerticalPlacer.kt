package com.notation.layout

import com.notation.model.Part

/**
 * Vertical staff placement engine.
 *
 * Places physical staff lines vertically on the page, respecting the number of
 * staves each instrument requires (e.g. 2 for piano) and the spacing rules
 * defined in [EngravingRules].
 *
 * Staves are placed top-to-bottom starting from the top margin. The distance
 * between staves of different instruments uses [EngravingRules.systemStaffDistance].
 * For multi-staff instruments, additional staves use 75% of that distance to
 * visually group them together.
 *
 * @param rules Engraving parameters controlling staff distances.
 */
class VerticalPlacer(private val rules: EngravingRules) {

    /**
     * Compute vertical positions for all staves of the given parts.
     *
     * @param parts The list of parts (instruments) in score order.
     * @param pageSetup Page dimensions and margins for computing horizontal extents.
     * @return A [StaffLayout] for each physical staff line, in top-to-bottom order.
     */
    fun placeStaves(parts: List<Part>, pageSetup: PageSetup): List<StaffLayout> {
        val leftX = StaffSpaces(pageSetup.marginLeftPt / pageSetup.staffSizePt)
        val rightX = StaffSpaces((pageSetup.widthPt - pageSetup.marginRightPt) / pageSetup.staffSizePt)
        val topMarginSS = StaffSpaces(pageSetup.marginTopPt / pageSetup.staffSizePt)

        val result = mutableListOf<StaffLayout>()
        var currentY = topMarginSS

        for ((partIndex, part) in parts.withIndex()) {
            for (staffIndex in 0 until part.staves) {
                if (result.isNotEmpty()) {
                    // Apply appropriate vertical gap
                    currentY = if (staffIndex > 0) {
                        // Additional staff within the same instrument (e.g. piano LH)
                        currentY + StaffSpaces(rules.systemStaffDistance * 0.75)
                    } else {
                        // First staff of a new instrument
                        currentY + StaffSpaces(rules.systemStaffDistance)
                    }
                }

                result.add(
                    StaffLayout(
                        partId = part.id,
                        staffIndex = staffIndex,
                        topY = currentY,
                        leftX = leftX,
                        rightX = rightX
                    )
                )

                // Account for the staff's own height (4 staff-spaces for 5 lines)
                currentY += StaffSpaces((rules.staffLineCount - 1).toDouble())
            }
        }

        return result
    }

    /**
     * Compute the total height of a system in staff-spaces, given the parts it contains.
     *
     * This is useful for page-breaking calculations where the system height must
     * be known before staves are fully placed.
     *
     * @param parts The parts in the system.
     * @return Total height from the top of the first staff to the bottom of the last.
     */
    fun computeSystemHeight(parts: List<Part>): StaffSpaces {
        if (parts.isEmpty()) return StaffSpaces.ZERO

        var height = 0.0
        var isFirstStaff = true

        for (part in parts) {
            for (staffIndex in 0 until part.staves) {
                if (!isFirstStaff) {
                    height += if (staffIndex > 0) {
                        rules.systemStaffDistance * 0.75
                    } else {
                        rules.systemStaffDistance
                    }
                }
                // Each staff occupies (staffLineCount - 1) staff-spaces
                height += (rules.staffLineCount - 1).toDouble()
                isFirstStaff = false
            }
        }

        return StaffSpaces(height)
    }
}
