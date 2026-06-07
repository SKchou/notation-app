package com.notation.audio

import com.notation.model.*

/**
 * Converts a [Score] into a chronologically-sorted list of [AudioEvent]s
 * suitable for playback or MIDI export.
 *
 * The scheduler walks through every part, measure, voice, and element to
 * produce NoteOn/NoteOff pairs with correct beat positions, velocities
 * derived from dynamic markings, and durations adjusted for articulations.
 * It also emits [AudioEvent.ProgramChange] events at beat 0 for each part.
 *
 * @param score The score to schedule.
 */
class EventScheduler(private val score: Score) {

    companion object {
        /** Default MIDI velocity when no dynamic marking is present. */
        private const val DEFAULT_VELOCITY = 80

        /** Default duration multiplier (percentage of written duration that actually sounds). */
        private const val DEFAULT_DURATION_FACTOR = 0.85

        /** Articulation-specific duration factors. */
        private val ARTICULATION_FACTORS = mapOf(
            Articulation.STACCATO to 0.50,
            Articulation.STACCATISSIMO to 0.30,
            Articulation.TENUTO to 0.90,
            Articulation.LEGATO to 1.02
        )
    }

    /**
     * Schedules the entire score and returns a sorted list of all audio events.
     *
     * @return All audio events in chronological order.
     */
    fun scheduleAll(): List<AudioEvent> {
        val events = mutableListOf<AudioEvent>()
        val measureStartBeats = computeMeasureStartBeats()

        // Emit initial ProgramChange for each part at beat 0
        for (part in score.parts) {
            events.add(
                AudioEvent.ProgramChange(
                    beatPosition = 0.0,
                    channel = part.instrument.midiChannel,
                    program = part.instrument.midiProgram
                )
            )
        }

        // Schedule all elements in all parts
        for (part in score.parts) {
            schedulePart(part, measureStartBeats, events)
        }

        return events.sorted()
    }

    /**
     * Schedules only the events that fall within the given beat range (inclusive start,
     * exclusive end).
     *
     * @param startBeat The beginning of the range (inclusive), in quarter-note beats.
     * @param endBeat The end of the range (exclusive), in quarter-note beats.
     * @return Audio events within the specified range, sorted chronologically.
     */
    fun scheduleRange(startBeat: Double, endBeat: Double): List<AudioEvent> {
        return scheduleAll().filter { it.beatPosition >= startBeat && it.beatPosition < endBeat }
    }

    /**
     * Computes the cumulative beat offset for the start of each measure index.
     *
     * Walks through the score's measures using time signatures — when a measure
     * has an explicit time signature, that becomes the "active" signature for
     * subsequent measures. The beat offset of measure *i* is the sum of all
     * preceding measures' quarter-beats-per-measure.
     *
     * @return A map from measure index (0-based) to cumulative beat offset.
     */
    private fun computeMeasureStartBeats(): Map<Int, Double> {
        val result = mutableMapOf<Int, Double>()
        var cumulativeBeats = 0.0
        var activeTimeSig = TimeSignature.COMMON_TIME

        // Use the first part to determine measure count and time signatures
        val referencePart = score.parts.firstOrNull() ?: return result

        for ((index, measure) in referencePart.measures.withIndex()) {
            // Update active time signature if this measure declares one
            measure.timeSignature?.let { activeTimeSig = it }

            result[index] = cumulativeBeats
            cumulativeBeats += activeTimeSig.quarterBeatsPerMeasure
        }

        return result
    }

    /**
     * Schedules all elements in a single part, appending events to [events].
     */
    private fun schedulePart(
        part: Part,
        measureStartBeats: Map<Int, Double>,
        events: MutableList<AudioEvent>
    ) {
        val channel = part.instrument.midiChannel

        for ((measureIndex, measure) in part.measures.withIndex()) {
            val measureStart = measureStartBeats[measureIndex] ?: continue

            for ((_, voice) in measure.voices) {
                for (element in voice.elements) {
                    when (element) {
                        is Note -> scheduleNote(element, measureStart, channel, events)
                        is Chord -> scheduleChord(element, measureStart, channel, events)
                        is Rest -> { /* Rests produce no audio events */ }
                    }
                }
            }
        }
    }

    /**
     * Schedules NoteOn and NoteOff events for a single note.
     */
    private fun scheduleNote(
        note: Note,
        measureStart: Double,
        channel: Int,
        events: MutableList<AudioEvent>
    ) {
        val onBeat = measureStart + note.position.quarterBeats
        val durationBeats = note.duration.quarterBeats * articulationFactor(note.articulations)
        val offBeat = onBeat + durationBeats
        val velocity = velocityForElement(note)

        events.add(AudioEvent.NoteOn(onBeat, channel, note.pitch.midiNote, velocity))
        events.add(AudioEvent.NoteOff(offBeat, channel, note.pitch.midiNote))
    }

    /**
     * Schedules NoteOn and NoteOff events for every pitch in a chord.
     */
    private fun scheduleChord(
        chord: Chord,
        measureStart: Double,
        channel: Int,
        events: MutableList<AudioEvent>
    ) {
        val onBeat = measureStart + chord.position.quarterBeats
        val durationBeats = chord.duration.quarterBeats * articulationFactor(chord.articulations)
        val offBeat = onBeat + durationBeats
        val velocity = velocityForElement(chord)

        for (pitch in chord.pitches) {
            events.add(AudioEvent.NoteOn(onBeat, channel, pitch.midiNote, velocity))
            events.add(AudioEvent.NoteOff(offBeat, channel, pitch.midiNote))
        }
    }

    /**
     * Determines the articulation-adjusted duration factor for an element.
     *
     * If multiple articulations are present, the one with the most extreme
     * (smallest) factor wins, since articulations like staccatissimo override
     * longer sustains. If no recognized articulation is present, returns
     * the default factor (85%).
     */
    private fun articulationFactor(articulations: List<Articulation>): Double {
        if (articulations.isEmpty()) return DEFAULT_DURATION_FACTOR

        var factor = DEFAULT_DURATION_FACTOR
        for (articulation in articulations) {
            val artFactor = ARTICULATION_FACTORS[articulation]
            if (artFactor != null && artFactor < factor) {
                factor = artFactor
            }
            // Legato overrides to extend (only if no shortening articulation is present)
            if (articulation == Articulation.LEGATO && factor >= DEFAULT_DURATION_FACTOR) {
                factor = ARTICULATION_FACTORS[Articulation.LEGATO]!!
            }
        }
        return factor
    }

    /**
     * Determines the MIDI velocity for an element based on its dynamic marking.
     * Falls back to [DEFAULT_VELOCITY] if no dynamic is set.
     */
    private fun velocityForElement(element: MusicElement): Int {
        return element.dynamics?.velocity ?: DEFAULT_VELOCITY
    }
}
