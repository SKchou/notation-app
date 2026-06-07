package com.notation.layout

import kotlinx.serialization.Serializable

/**
 * Centralized engraving parameters controlling all aspects of music notation layout.
 *
 * These values follow standard engraving conventions as described in
 * Elaine Gould's "Behind Bars" and Ted Ross's "The Art of Music Engraving".
 * All spatial values are in staff-space units unless otherwise noted.
 */
@Serializable
data class EngravingRules(
    // ─── Horizontal Spacing ───────────────────────────────────────────
    /** Base width for a quarter note, in staff-spaces. */
    val baseSpacing: Double = 1.6,

    /** Minimum distance between any two adjacent noteheads, in staff-spaces. */
    val minimumNoteSpacing: Double = 1.2,

    /** Extra padding after a clef, in staff-spaces. */
    val clefPadding: Double = 0.5,

    /** Extra padding after a key signature, in staff-spaces. */
    val keySignaturePadding: Double = 0.5,

    /** Extra padding after a time signature, in staff-spaces. */
    val timeSignaturePadding: Double = 0.5,

    /** Width allocated for a barline, in staff-spaces. */
    val barlineWidth: Double = 0.5,

    /** Left margin within each measure, in staff-spaces. */
    val measureLeftPadding: Double = 0.3,

    /** Right margin within each measure, in staff-spaces. */
    val measureRightPadding: Double = 0.3,

    // ─── Collision Avoidance ──────────────────────────────────────────
    /** Minimum clearance between any two bounding boxes, in staff-spaces. */
    val minimumClearance: Double = 0.25,

    /** Extra horizontal clearance for accidentals to avoid notehead overlap. */
    val accidentalClearance: Double = 0.15,

    /** Minimum space between an articulation and its parent notehead. */
    val articulationClearance: Double = 0.3,

    /** Minimum space between a dynamic marking and the staff. */
    val dynamicClearance: Double = 0.5,

    // ─── Stems & Beams ───────────────────────────────────────────────
    /** Default stem length in staff-spaces (Gould: 3.5 spaces). */
    val defaultStemLength: Double = 3.5,

    /** Minimum stem length when shortened to avoid collisions. */
    val minimumStemLength: Double = 2.5,

    /** Maximum stem length before forced beam break. */
    val maximumStemLength: Double = 5.0,

    /** Stem line thickness, in staff-spaces. */
    val stemThickness: Double = 0.12,

    /** Beam thickness, in staff-spaces. */
    val beamThickness: Double = 0.5,

    /** Distance between beams for 16th, 32nd notes etc., in staff-spaces. */
    val beamSpacing: Double = 0.25,

    /** Maximum beam slant angle in staff-spaces per staff-space horizontal distance. */
    val maxBeamSlant: Double = 0.5,

    // ─── Staff & System ──────────────────────────────────────────────
    /** Number of lines per staff. */
    val staffLineCount: Int = 5,

    /** Staff line thickness, in staff-spaces. */
    val staffLineThickness: Double = 0.1,

    /** Distance between staves within a system, in staff-spaces. */
    val systemStaffDistance: Double = 8.0,

    /** Distance between systems on a page, in staff-spaces. */
    val systemDistance: Double = 12.0,

    /** Indent for the first system on the first page, in staff-spaces. */
    val firstSystemIndent: Double = 5.0,

    /** Distance between barline end and next measure content. */
    val barlineToNoteDistance: Double = 1.0,

    // ─── Ledger Lines ────────────────────────────────────────────────
    /** Extension of ledger lines beyond the notehead on each side. */
    val ledgerLineExtension: Double = 0.4,

    /** Thickness of ledger lines, in staff-spaces. */
    val ledgerLineThickness: Double = 0.16,

    // ─── Ties & Slurs ────────────────────────────────────────────────
    /** Minimum tie/slur curvature height, in staff-spaces. */
    val minTieCurvature: Double = 0.4,

    /** Maximum tie/slur curvature height, in staff-spaces. */
    val maxTieCurvature: Double = 2.0,

    /** Tie/slur line thickness, in staff-spaces. */
    val tieThickness: Double = 0.12,

    // ─── Tuplets ─────────────────────────────────────────────────────
    /** Distance of tuplet bracket from noteheads. */
    val tupletBracketOffset: Double = 1.5,

    /** Tuplet bracket line thickness. */
    val tupletBracketThickness: Double = 0.12,

    // ─── Grace Notes ─────────────────────────────────────────────────
    /** Scale factor for grace note glyphs (relative to normal notes). */
    val graceNoteScale: Double = 0.6,

    /** Spacing between grace notes, in staff-spaces. */
    val graceNoteSpacing: Double = 0.8
) {
    companion object {
        /** Standard engraving rules suitable for most scores. */
        val DEFAULT = EngravingRules()
    }
}
