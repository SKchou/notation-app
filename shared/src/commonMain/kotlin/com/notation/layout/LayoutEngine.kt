package com.notation.layout

import com.notation.model.*

/**
 * Main orchestrator for the music notation layout pipeline.
 *
 * Takes a [Score] and produces a list of [LayoutPage]s ready for rendering.
 * The pipeline stages are:
 *
 * 1. **System Breaking** — group measures into systems and systems into pages.
 * 2. **Horizontal Spacing** — compute justified column widths per system.
 * 3. **Vertical Placement** — position staves vertically on each page.
 * 4. **Glyph Emission** — convert music elements into renderable draw commands.
 *
 * @param engravingRules Engraving parameters for spacing, collision, and sizing.
 * @param glyphMetrics Font metrics for glyph bounding boxes and anchor points.
 */
class LayoutEngine(
    private val engravingRules: EngravingRules = EngravingRules.DEFAULT,
    private val glyphMetrics: GlyphMetrics = DefaultGlyphMetrics()
) {
    /**
     * Lay out the entire score into renderable pages.
     *
     * @param score The score to lay out.
     * @param pageSetup Page dimensions and margins.
     * @return A list of [LayoutPage]s, each containing systems of draw commands.
     *         Returns a single empty page for empty scores.
     */
    fun layout(score: Score, pageSetup: PageSetup = PageSetup()): List<LayoutPage> {
        if (score.parts.isEmpty() || score.measureCount == 0) {
            return listOf(LayoutPage(pageIndex = 0, systems = emptyList()))
        }

        val spacer = HorizontalSpacer(engravingRules, glyphMetrics)
        val verticalPlacer = VerticalPlacer(engravingRules)
        val emitter = GlyphEmitter(glyphMetrics)
        val breaker = SystemBreaker(engravingRules, pageSetup)

        // Step 1: Compute system and page breaks
        val breaks = breaker.computeBreaks(score, spacer)
        if (breaks.isEmpty()) {
            return listOf(LayoutPage(pageIndex = 0, systems = emptyList()))
        }

        // Step 2: Build systems
        val systemsByPage = mutableMapOf<Int, MutableList<LayoutSystem>>()

        for (breakResult in breaks) {
            val measureRange = breakResult.measureRange
            val pageIndex = breakResult.pageIndex

            // Horizontal spacing for this system
            val columns = spacer.computeSpacing(
                score, measureRange, pageSetup.usableWidthSS
            )

            // Vertical placement
            val staves = verticalPlacer.placeStaves(score.parts.toList(), pageSetup)

            // Emit glyphs
            val allGlyphs = mutableListOf<GlyphDrawCommand>()

            // Staff lines
            for (staff in staves) {
                allGlyphs.addAll(emitter.emitStaffLines(staff))
            }

            // Measure attributes and notes per staff
            var xCursor = staves.firstOrNull()?.leftX ?: StaffSpaces.ZERO

            // First-system indent
            if (breakResult == breaks.firstOrNull()) {
                xCursor += StaffSpaces(engravingRules.firstSystemIndent)
            }

            // Walk through measures in this system
            for (measureIndex in measureRange) {
                val measureLeftX = xCursor
                xCursor += StaffSpaces(engravingRules.measureLeftPadding)

                // Emit clef / time signature / key signature at start of measure
                for (staff in staves) {
                    val part = score.partById(staff.partId) ?: continue
                    if (measureIndex !in part.measures.indices) continue
                    val measure = part.measures[measureIndex]

                    // Clef (only emit for the matching staff index)
                    if (measure.clef != null && staff.staffIndex == 0) {
                        allGlyphs.addAll(emitter.emitClef(measure.clef, xCursor, staff))
                    }
                }

                // Advance past clef if any measure in this column has one
                val anyClef = score.parts.any { part ->
                    measureIndex in part.measures.indices &&
                            part.measures[measureIndex].clef != null
                }
                if (anyClef) {
                    xCursor += StaffSpaces(3.0 + engravingRules.clefPadding)
                }

                // Time signature
                for (staff in staves) {
                    val part = score.partById(staff.partId) ?: continue
                    if (measureIndex !in part.measures.indices) continue
                    val measure = part.measures[measureIndex]

                    if (measure.timeSignature != null) {
                        allGlyphs.addAll(
                            emitter.emitTimeSignature(measure.timeSignature, xCursor, staff)
                        )
                    }
                }

                val anyTimeSig = score.parts.any { part ->
                    measureIndex in part.measures.indices &&
                            part.measures[measureIndex].timeSignature != null
                }
                if (anyTimeSig) {
                    xCursor += StaffSpaces(2.5 + engravingRules.timeSignaturePadding)
                }

                // Compute spacing columns for this measure
                val measureColumns = columns.filter { col ->
                    // Filter to columns belonging to this measure's rhythmic range
                    val measureStartBeat = measureStartBeat(score, measureIndex)
                    val measureEndBeat = measureStartBeat + effectiveTimeSig(score, measureIndex).quarterBeatsPerMeasure
                    col.rhythmicPosition.quarterBeats >= measureStartBeat &&
                            col.rhythmicPosition.quarterBeats < measureEndBeat
                }

                // Emit music elements at each column position
                for (column in measureColumns) {
                    for (staff in staves) {
                        val elementsForPart = column.elements[staff.partId] ?: continue
                        val clef = resolveClef(score, staff.partId, measureIndex)

                        for (element in elementsForPart) {
                            val glyphs = when (element) {
                                is Note -> emitter.emitNote(element, xCursor, staff, clef)
                                is Chord -> emitter.emitChord(element, xCursor, staff, clef)
                                is Rest -> emitter.emitRest(element, xCursor, staff)
                            }
                            allGlyphs.addAll(glyphs)
                        }
                    }
                    xCursor += column.naturalWidth
                }

                // Ensure minimum measure width
                val measuredWidth = xCursor.value - measureLeftX.value
                if (measuredWidth < engravingRules.minimumNoteSpacing * 2.0) {
                    xCursor = measureLeftX + StaffSpaces(engravingRules.minimumNoteSpacing * 2.0)
                }

                xCursor += StaffSpaces(engravingRules.measureRightPadding)

                // Barline at end of measure
                for (staff in staves) {
                    val part = score.partById(staff.partId) ?: continue
                    if (measureIndex !in part.measures.indices) continue
                    val measure = part.measures[measureIndex]
                    allGlyphs.addAll(emitter.emitBarline(xCursor, staff, measure.barlineEnd))
                }

                xCursor += StaffSpaces(engravingRules.barlineWidth)
            }

            // Compute system bounds
            val systemBounds = computeSystemBounds(staves, xCursor)

            val system = LayoutSystem(
                measureRange = measureRange,
                staves = staves,
                glyphs = allGlyphs,
                bounds = systemBounds
            )

            systemsByPage.getOrPut(pageIndex) { mutableListOf() }.add(system)
        }

        // Step 3: Build pages
        return systemsByPage.entries
            .sortedBy { it.key }
            .map { (pageIndex, systems) ->
                LayoutPage(pageIndex = pageIndex, systems = systems)
            }
    }

    /** Compute the cumulative beat offset for a measure index. */
    private fun measureStartBeat(score: Score, measureIndex: Int): Double {
        var beat = 0.0
        val part = score.parts.firstOrNull() ?: return 0.0
        for (i in 0 until measureIndex) {
            if (i in part.measures.indices) {
                beat += effectiveTimeSig(score, i).quarterBeatsPerMeasure
            }
        }
        return beat
    }

    /** Resolve the effective time signature at a given measure index. */
    private fun effectiveTimeSig(score: Score, measureIndex: Int): TimeSignature {
        val part = score.parts.firstOrNull() ?: return TimeSignature.COMMON_TIME
        for (i in measureIndex downTo 0) {
            if (i in part.measures.indices) {
                part.measures[i].timeSignature?.let { return it }
            }
        }
        return TimeSignature.COMMON_TIME
    }

    /** Resolve the effective clef for a part at a given measure. */
    private fun resolveClef(score: Score, partId: PartId, measureIndex: Int): Clef {
        val part = score.partById(partId) ?: return Clef.TREBLE
        for (i in measureIndex downTo 0) {
            if (i in part.measures.indices) {
                part.measures[i].clef?.let { return it }
            }
        }
        return part.instrument.clefs.firstOrNull() ?: Clef.TREBLE
    }

    /** Compute the bounding box enclosing all staves in the system. */
    private fun computeSystemBounds(
        staves: List<StaffLayout>,
        rightEdge: StaffSpaces
    ): BoundingBox {
        if (staves.isEmpty()) return BoundingBox.EMPTY

        val minY = staves.minOf { it.topY }
        val maxY = staves.maxOf { it.topY + StaffSpaces(4.0) }
        val minX = staves.minOf { it.leftX }

        return BoundingBox(
            x = minX,
            y = minY,
            width = rightEdge - minX,
            height = maxY - minY
        )
    }
}
