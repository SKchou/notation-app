package com.notation.layout

import kotlin.math.sqrt

/** Axis for displacement during collision resolution. */
enum class Axis { HORIZONTAL, VERTICAL }

/**
 * Immutable bounding box in staff-space coordinates.
 * Used throughout the layout engine for spatial queries, collision detection,
 * and positioning of musical elements.
 */
data class BoundingBox(
    val x: StaffSpaces,
    val y: StaffSpaces,
    val width: StaffSpaces,
    val height: StaffSpaces
) {
    /** Right edge X coordinate. */
    val right: StaffSpaces get() = x + width

    /** Bottom edge Y coordinate. */
    val bottom: StaffSpaces get() = y + height

    /** Horizontal center. */
    val centerX: StaffSpaces get() = StaffSpaces(x.value + width.value / 2.0)

    /** Vertical center. */
    val centerY: StaffSpaces get() = StaffSpaces(y.value + height.value / 2.0)

    /** Check if this box intersects another. */
    fun intersects(other: BoundingBox): Boolean =
        x < other.right && right > other.x &&
        y < other.bottom && bottom > other.y

    /** Check if this box contains a point. */
    fun contains(point: StaffSpacePoint): Boolean =
        point.x >= x && point.x <= right &&
        point.y >= y && point.y <= bottom

    /** Expand the box by padding on all sides. */
    fun expand(padding: StaffSpaces): BoundingBox = BoundingBox(
        x = x - padding,
        y = y - padding,
        width = StaffSpaces(width.value + padding.value * 2.0),
        height = StaffSpaces(height.value + padding.value * 2.0)
    )

    /** Compute the union (smallest enclosing box) of this box and another. */
    fun union(other: BoundingBox): BoundingBox {
        val minX = minOf(x, other.x)
        val minY = minOf(y, other.y)
        val maxX = maxOf(right, other.right)
        val maxY = maxOf(bottom, other.bottom)
        return BoundingBox(minX, minY, maxX - minX, maxY - minY)
    }

    /** Distance from the center of this box to a point. */
    fun centerDistanceTo(point: StaffSpacePoint): Double {
        val dx = centerX.value - point.x.value
        val dy = centerY.value - point.y.value
        return sqrt(dx * dx + dy * dy)
    }

    companion object {
        /** An empty bounding box at the origin. */
        val EMPTY = BoundingBox(StaffSpaces.ZERO, StaffSpaces.ZERO, StaffSpaces.ZERO, StaffSpaces.ZERO)
    }
}
