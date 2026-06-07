package com.notation.layout

import com.notation.model.*

/**
 * A fully laid-out page of music, ready for rendering.
 * Contains all systems that fit on this page.
 */
data class LayoutPage(
    /** Zero-based page index. */
    val pageIndex: Int,
    /** Systems of music laid out on this page. */
    val systems: List<LayoutSystem>
)

/**
 * A horizontal band of music spanning one or more measures across all parts.
 * A system contains staves for all visible parts and the draw commands
 * needed to render everything within it.
 */
data class LayoutSystem(
    /** Range of measure indices included in this system. */
    val measureRange: IntRange,
    /** Staff layouts for each part/staff in this system. */
    val staves: List<StaffLayout>,
    /** All glyph draw commands for this system. */
    val glyphs: List<GlyphDrawCommand>,
    /** Bounding box enclosing the entire system. */
    val bounds: BoundingBox
)

/**
 * Layout information for a single staff within a system.
 * Provides the vertical position and horizontal extent of the staff.
 */
data class StaffLayout(
    /** The part this staff belongs to. */
    val partId: PartId,
    /** Staff index within the part (0 for single-staff, 0/1 for piano). */
    val staffIndex: Int,
    /** Y-coordinate of the top line of this staff. */
    val topY: StaffSpaces,
    /** X-coordinate of the left edge. */
    val leftX: StaffSpaces,
    /** X-coordinate of the right edge. */
    val rightX: StaffSpaces
)

/**
 * A positioned musical element with its bounding box and draw commands.
 * This is the output of layout for a single notation element.
 */
data class LayoutElement(
    /** Unique ID of the source element. */
    val elementId: ElementId,
    /** Part this element belongs to. */
    val partId: PartId,
    /** Voice this element belongs to. */
    val voiceId: VoiceId,
    /** Bounding box in staff-space coordinates. */
    val bounds: BoundingBox,
    /** Draw commands to render this element. */
    val glyphs: List<GlyphDrawCommand>,
    /** Reference to the original music element. */
    val sourceElement: MusicElement
)

/**
 * Sealed interface for all drawing primitives emitted by the layout engine.
 * The renderer consumes these commands to draw music notation.
 */
sealed interface GlyphDrawCommand {
    /** Z-ordering for draw commands. Higher values render on top. */
    val zOrder: Int

    /**
     * A single SMuFL glyph to be drawn at a position.
     * The codepoint references a character in the SMuFL-compliant music font.
     */
    data class SMuFLGlyph(
        /** SMuFL codepoint for the glyph. */
        val codepoint: Char,
        /** X position in staff-spaces. */
        val x: StaffSpaces,
        /** Y position in staff-spaces (0 = top staff line). */
        val y: StaffSpaces,
        /** Scale factor (1.0 = normal size). */
        val scale: Double = 1.0,
        /** Color to render the glyph. */
        val color: GlyphColor = GlyphColor.BLACK,
        /** Z-order for layering. */
        override val zOrder: Int = 0
    ) : GlyphDrawCommand

    /**
     * A straight line segment (used for stems, barlines, staff lines, ledger lines).
     */
    data class Line(
        val startX: StaffSpaces,
        val startY: StaffSpaces,
        val endX: StaffSpaces,
        val endY: StaffSpaces,
        /** Line thickness in staff-spaces. */
        val thickness: StaffSpaces,
        val color: GlyphColor = GlyphColor.BLACK,
        override val zOrder: Int = 0
    ) : GlyphDrawCommand

    /**
     * A cubic Bézier curve (used for ties, slurs, and phrase marks).
     * Defined by start point (p0), two control points (cp1, cp2), and end point (p3).
     */
    data class Curve(
        val p0x: StaffSpaces, val p0y: StaffSpaces,
        val cp1x: StaffSpaces, val cp1y: StaffSpaces,
        val cp2x: StaffSpaces, val cp2y: StaffSpaces,
        val p3x: StaffSpaces, val p3y: StaffSpaces,
        /** Curve stroke thickness in staff-spaces. */
        val thickness: StaffSpaces,
        val color: GlyphColor = GlyphColor.BLACK,
        override val zOrder: Int = 0
    ) : GlyphDrawCommand
}

/**
 * Colors available for rendering music notation elements.
 * Each color provides RGBA components in the 0.0–1.0 range.
 */
enum class GlyphColor(
    /** Red component (0.0–1.0). */
    val r: Float,
    /** Green component (0.0–1.0). */
    val g: Float,
    /** Blue component (0.0–1.0). */
    val b: Float,
    /** Alpha component (0.0–1.0). */
    val a: Float
) {
    BLACK(0f, 0f, 0f, 1f),
    RED(1f, 0f, 0f, 1f),
    BLUE(0f, 0f, 1f, 1f),
    GRAY(0.5f, 0.5f, 0.5f, 1f),
    SELECTION_BLUE(0.2f, 0.4f, 0.9f, 0.7f)
}

/**
 * Page dimensions and margins for layout computation.
 * Defaults to A4 portrait with 1-inch margins.
 */
data class PageSetup(
    /** Page width in points (1/72 inch). A4 = 595pt. */
    val widthPt: Double = 595.0,
    /** Page height in points. A4 = 842pt. */
    val heightPt: Double = 842.0,
    /** Top margin in points. */
    val marginTopPt: Double = 72.0,
    /** Bottom margin in points. */
    val marginBottomPt: Double = 72.0,
    /** Left margin in points. */
    val marginLeftPt: Double = 72.0,
    /** Right margin in points. */
    val marginRightPt: Double = 72.0,
    /** Staff size in points. Determines the conversion factor to staff-spaces. */
    val staffSizePt: Double = 20.0
) {
    /**
     * Usable width for music content, in staff-space units.
     * Computed from page width minus left and right margins, converted to staff-spaces.
     */
    val usableWidthSS: StaffSpaces
        get() = StaffSpaces((widthPt - marginLeftPt - marginRightPt) / staffSizePt)

    /**
     * Usable height for music content, in staff-space units.
     * Computed from page height minus top and bottom margins, converted to staff-spaces.
     */
    val usableHeightSS: StaffSpaces
        get() = StaffSpaces((heightPt - marginTopPt - marginBottomPt) / staffSizePt)
}
