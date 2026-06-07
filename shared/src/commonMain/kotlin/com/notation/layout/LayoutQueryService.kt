package com.notation.layout

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Spatial query service for laid-out music notation.
 *
 * Provides hit-testing, nearest-staff-position snapping, region queries,
 * and measure-based filtering over the rendered layout. This is the primary
 * interface for interactive features like selection, cursor placement,
 * and tooltip display.
 *
 * @param pages The fully laid-out pages to query against.
 */
class LayoutQueryService(private val pages: List<LayoutPage>) {

    /** Flattened list of all layout elements across all pages and systems. */
    private val allElements: List<LayoutElement> = pages.flatMap { page ->
        page.systems.flatMap { system ->
            buildLayoutElements(system)
        }
    }

    /** All staff layouts across all pages, for staff-position snapping. */
    private val allStaves: List<StaffLayout> = pages.flatMap { page ->
        page.systems.flatMap { it.staves }
    }

    /**
     * Find the layout element nearest to the given point, within a search radius.
     *
     * Performs a linear scan over all elements, expanding each bounding box
     * by [radius] and testing for containment. Among all candidates, returns
     * the one whose bounding-box center is closest to [point].
     *
     * @param point The query point in staff-space coordinates.
     * @param radius Search radius in staff-spaces. Default 0.5 (half a staff-space).
     * @return The nearest [LayoutElement], or null if nothing is within range.
     */
    fun hitTest(
        point: StaffSpacePoint,
        radius: StaffSpaces = StaffSpaces(0.5)
    ): LayoutElement? {
        var nearest: LayoutElement? = null
        var nearestDistance = Double.MAX_VALUE

        for (element in allElements) {
            val expanded = element.bounds.expand(radius)
            if (expanded.contains(point)) {
                val distance = element.bounds.centerDistanceTo(point)
                if (distance < nearestDistance) {
                    nearestDistance = distance
                    nearest = element
                }
            }
        }

        return nearest
    }

    /**
     * Snap a point to the nearest staff position (line or space).
     *
     * Finds the closest staff vertically, then rounds the Y coordinate to
     * the nearest half staff-space to get a [StaffPosition].
     *
     * @param point The query point in staff-space coordinates.
     * @return The nearest [StaffPosition] on the closest staff.
     */
    fun nearestStaffPosition(point: StaffSpacePoint): StaffPosition {
        if (allStaves.isEmpty()) return StaffPosition(0)

        // Find the closest staff by vertical distance
        val closestStaff = allStaves.minByOrNull { staff ->
            val staffCenter = staff.topY + StaffSpaces(2.0)
            abs(point.y.value - staffCenter.value)
        }!!

        // Compute position relative to staff top line
        val relativeY = point.y - closestStaff.topY
        val halfSpaces = (relativeY.value * 2.0).roundToInt()
        return StaffPosition(halfSpaces)
    }

    /**
     * Find all layout elements whose bounding boxes intersect the given rectangle.
     *
     * @param rect The query rectangle in staff-space coordinates.
     * @return All [LayoutElement]s that overlap with [rect].
     */
    fun elementsInRegion(rect: BoundingBox): List<LayoutElement> {
        return allElements.filter { element ->
            element.bounds.intersects(rect)
        }
    }

    /**
     * Find all layout elements that belong to a specific measure.
     *
     * Searches through all systems to find those containing the given measure
     * number, then returns the elements built from those systems.
     *
     * @param measureNumber The 1-based measure number to query.
     * @return All [LayoutElement]s that belong to the specified measure.
     */
    fun elementsInMeasure(measureNumber: Int): List<LayoutElement> {
        // measureNumber is 1-based; system.measureRange is 0-based indices
        val measureIndex = measureNumber - 1

        return pages.flatMap { page ->
            page.systems
                .filter { system -> measureIndex in system.measureRange }
                .flatMap { system -> buildLayoutElements(system) }
        }
    }

    /**
     * Build [LayoutElement] wrappers from a system's glyph draw commands.
     *
     * Since the layout pipeline produces flat glyph lists rather than
     * per-element grouped output, this constructs synthetic LayoutElements
     * from each glyph command with approximate bounding boxes.
     *
     * In a production system, the LayoutEngine would emit LayoutElements
     * directly; this is a bridge for query functionality.
     */
    private fun buildLayoutElements(system: LayoutSystem): List<LayoutElement> {
        val elements = mutableListOf<LayoutElement>()

        // Create a LayoutElement for each SMuFL glyph in the system
        // (Lines are structural and typically don't need hit-testing)
        for (glyph in system.glyphs) {
            when (glyph) {
                is GlyphDrawCommand.SMuFLGlyph -> {
                    val glyphBounds = BoundingBox(
                        x = glyph.x,
                        y = glyph.y - StaffSpaces(0.5),
                        width = StaffSpaces(1.0),
                        height = StaffSpaces(1.0)
                    )

                    // Determine the partId from the closest staff
                    val staff = system.staves.minByOrNull { staff ->
                        abs(glyph.y.value - (staff.topY.value + 2.0))
                    }
                    val partId = staff?.partId ?: system.staves.firstOrNull()?.partId

                    if (partId != null) {
                        elements.add(
                            LayoutElement(
                                elementId = com.notation.model.ElementId(),
                                partId = partId,
                                voiceId = com.notation.model.VoiceId(1),
                                bounds = glyphBounds,
                                glyphs = listOf(glyph),
                                sourceElement = com.notation.model.Rest(
                                    duration = com.notation.model.Duration.QUARTER,
                                    position = com.notation.model.RhythmicPosition.ZERO
                                )
                            )
                        )
                    }
                }
                is GlyphDrawCommand.Line -> {
                    // Skip lines for hit-testing (staff lines, stems, barlines)
                }
                is GlyphDrawCommand.Curve -> {
                    // Skip curves for basic hit-testing
                }
            }
        }

        return elements
    }
}
