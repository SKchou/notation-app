package com.notation.layout

import kotlin.jvm.JvmInline
import kotlin.math.roundToInt

/**
 * Resolution-independent coordinate in staff-space units.
 * One staff-space equals the distance between two adjacent staff lines.
 * This is the fundamental unit for all layout calculations.
 */
@JvmInline
value class StaffSpaces(val value: Double) : Comparable<StaffSpaces> {
    operator fun plus(other: StaffSpaces) = StaffSpaces(value + other.value)
    operator fun minus(other: StaffSpaces) = StaffSpaces(value - other.value)
    operator fun times(scalar: Double) = StaffSpaces(value * scalar)
    operator fun div(scalar: Double) = StaffSpaces(value / scalar)
    operator fun unaryMinus() = StaffSpaces(-value)
    override fun compareTo(other: StaffSpaces) = value.compareTo(other.value)

    companion object {
        val ZERO = StaffSpaces(0.0)
    }
}

/**
 * A point in staff-space coordinates.
 * Used for positioning music elements relative to a staff.
 */
data class StaffSpacePoint(
    val x: StaffSpaces,
    val y: StaffSpaces
)

/**
 * Absolute position on the virtual page, in points (1/72 inch).
 * Used after converting from staff-space coordinates for rendering.
 */
data class PagePoint(val x: Double, val y: Double)

/**
 * Defines a staff line position for pitch mapping.
 * Measured in half-spaces from the top staff line:
 * - 0 = top line (F5 in treble clef)
 * - 1 = first space from top
 * - 2 = second line from top
 * - etc.
 */
@JvmInline
value class StaffPosition(val halfSpaces: Int) : Comparable<StaffPosition> {
    override fun compareTo(other: StaffPosition) = halfSpaces.compareTo(other.halfSpaces)

    /** Convert to Y coordinate in staff-spaces (0 = top line). */
    fun toStaffSpaces(): StaffSpaces = StaffSpaces(halfSpaces.toDouble() / 2.0)
}

/**
 * Transforms between coordinate tiers:
 * - Staff-space (abstract) → Page points (1/72 inch) → Screen pixels
 *
 * The staff size in points determines the physical rendering size.
 * Zoom and scroll offsets are applied for screen coordinate mapping.
 */
data class CoordinateTransform(
    /** Rastral size in points. Default ~7mm, standard engraving size. */
    val staffSizePt: Double = 20.0,
    /** Current zoom level. 1.0 = 100%. */
    val zoomFactor: Double = 1.0,
    /** Horizontal scroll offset in page points. */
    val scrollOffsetX: Double = 0.0,
    /** Vertical scroll offset in page points. */
    val scrollOffsetY: Double = 0.0
) {
    /** Convert staff-spaces to page points. */
    fun staffSpaceToPoints(ss: StaffSpaces): Double = ss.value * staffSizePt

    /** Convert page points to staff-spaces. */
    fun pointsToStaffSpace(pt: Double): StaffSpaces = StaffSpaces(pt / staffSizePt)

    /** Overall scale factor for the canvas. */
    val canvasScale: Double get() = staffSizePt * zoomFactor

    /** Convert a staff-space point to a page point. */
    fun toPagePoint(ssPoint: StaffSpacePoint): PagePoint = PagePoint(
        x = staffSpaceToPoints(ssPoint.x),
        y = staffSpaceToPoints(ssPoint.y)
    )

    /** Convert a page point back to staff-space coordinates (inverse). */
    fun toStaffSpacePoint(pagePoint: PagePoint): StaffSpacePoint = StaffSpacePoint(
        x = pointsToStaffSpace(pagePoint.x),
        y = pointsToStaffSpace(pagePoint.y)
    )

    /** Convert a screen pixel coordinate to staff-space, accounting for zoom + scroll. */
    fun screenToStaffSpace(screenX: Double, screenY: Double): StaffSpacePoint {
        val pageX = (screenX / zoomFactor) + scrollOffsetX
        val pageY = (screenY / zoomFactor) + scrollOffsetY
        return StaffSpacePoint(
            x = pointsToStaffSpace(pageX),
            y = pointsToStaffSpace(pageY)
        )
    }

    /** Snap a Y-coordinate to the nearest staff position (line or space). */
    fun snapToStaffPosition(y: StaffSpaces): StaffPosition {
        val halfSpaces = (y.value * 2.0).roundToInt()
        return StaffPosition(halfSpaces)
    }
}
