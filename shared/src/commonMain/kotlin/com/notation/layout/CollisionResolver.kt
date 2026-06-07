package com.notation.layout

/**
 * Collision resolution engine for music notation layout.
 *
 * Resolves overlapping elements by displacing lower-priority items along their
 * preferred axis. Elements are settled in descending priority order — noteheads
 * are placed first and never moved; lower-priority items like dynamics, lyrics,
 * and tuplet brackets are displaced to avoid overlap.
 *
 * @param rules Engraving parameters (minimum clearance values).
 */
class CollisionResolver(private val rules: EngravingRules) {

    /**
     * Priority ranking for collision resolution.
     *
     * Elements with higher [rank] are settled first and will not be displaced.
     * Lower-ranked elements must move to avoid higher-ranked ones.
     */
    enum class CollisionPriority(val rank: Int) {
        NOTEHEAD(100),
        STEM(95),
        BEAM(90),
        FLAG(85),
        ACCIDENTAL(80),
        AUGMENTATION_DOT(75),
        ARTICULATION(60),
        SLUR(55),
        TIE(50),
        DYNAMIC(40),
        HAIRPIN(35),
        LYRIC(30),
        TUPLET_BRACKET(20),
        FINGERING(15),
        STAFF_TEXT(10)
    }

    /**
     * An element positioned in layout space, tagged with collision metadata.
     *
     * @param element The source layout element.
     * @param bounds Current bounding box (may be mutated during resolution).
     * @param priority Determines settling order — higher ranks are immovable.
     * @param displacementAxis Preferred axis for displacement when a collision occurs.
     */
    data class PositionedElement(
        val element: LayoutElement,
        val bounds: BoundingBox,
        val priority: CollisionPriority,
        val displacementAxis: Axis
    )

    /** Maximum displacement iterations per element before giving up. */
    private val maxIterations = 50

    /**
     * Resolve collisions among the given positioned elements.
     *
     * Algorithm:
     * 1. Sort elements by priority descending (highest priority first).
     * 2. Iterate through elements. Each element is checked against all
     *    previously settled elements for bounding-box intersection (with
     *    minimum clearance padding).
     * 3. If a collision is found, the current element is displaced along its
     *    preferred [Axis] by the overlap amount plus clearance.
     * 4. The process repeats for up to [maxIterations] per element.
     *
     * @param elements The elements to resolve.
     * @return A new list of [PositionedElement]s with updated bounding boxes.
     */
    fun resolve(elements: List<PositionedElement>): List<PositionedElement> {
        if (elements.size <= 1) return elements

        val sorted = elements.sortedByDescending { it.priority.rank }
        val settled = mutableListOf<PositionedElement>()
        val clearance = StaffSpaces(rules.minimumClearance)

        for (element in sorted) {
            var current = element
            var iterations = 0

            while (iterations < maxIterations) {
                val collision = findCollision(current, settled, clearance)
                if (collision == null) break

                current = displace(current, collision, clearance)
                iterations++
            }

            settled.add(current)
        }

        return settled
    }

    /**
     * Finds the first settled element that collides with [element].
     * Bounding boxes are expanded by [clearance] before testing.
     */
    private fun findCollision(
        element: PositionedElement,
        settled: List<PositionedElement>,
        clearance: StaffSpaces
    ): PositionedElement? {
        val expandedBounds = element.bounds.expand(clearance)
        return settled.firstOrNull { other ->
            expandedBounds.intersects(other.bounds.expand(clearance))
        }
    }

    /**
     * Displace [element] away from [obstacle] along [element]'s preferred axis.
     *
     * For [Axis.HORIZONTAL]: moves the element to the right of the obstacle.
     * For [Axis.VERTICAL]: moves the element below the obstacle.
     */
    private fun displace(
        element: PositionedElement,
        obstacle: PositionedElement,
        clearance: StaffSpaces
    ): PositionedElement {
        val newBounds = when (element.displacementAxis) {
            Axis.HORIZONTAL -> {
                // Compute horizontal overlap and shift right
                val overlapRight = obstacle.bounds.right + clearance - element.bounds.x
                val overlapLeft = element.bounds.right + clearance - obstacle.bounds.x

                if (overlapRight.value <= overlapLeft.value) {
                    // Move right
                    element.bounds.copy(
                        x = obstacle.bounds.right + clearance
                    )
                } else {
                    // Move left
                    element.bounds.copy(
                        x = obstacle.bounds.x - element.bounds.width - clearance
                    )
                }
            }
            Axis.VERTICAL -> {
                // Compute vertical overlap and shift down
                val overlapBelow = obstacle.bounds.bottom + clearance - element.bounds.y
                val overlapAbove = element.bounds.bottom + clearance - obstacle.bounds.y

                if (overlapBelow.value <= overlapAbove.value) {
                    // Move down
                    element.bounds.copy(
                        y = obstacle.bounds.bottom + clearance
                    )
                } else {
                    // Move up
                    element.bounds.copy(
                        y = obstacle.bounds.y - element.bounds.height - clearance
                    )
                }
            }
        }

        return element.copy(bounds = newBounds)
    }
}
