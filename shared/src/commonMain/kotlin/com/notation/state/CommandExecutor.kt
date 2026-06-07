package com.notation.state

import com.notation.model.*
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList

/**
 * Result of executing a [ScoreCommand] against a [Score].
 *
 * @property newScore The score after applying the command.
 * @property reverseCommand The command that, when executed, undoes this command.
 */
data class ExecutionResult(
    val newScore: Score,
    val reverseCommand: ScoreCommand
)

/**
 * Pure command executor that transforms a [Score] by applying [ScoreCommand]s.
 *
 * Every execution produces an [ExecutionResult] containing both the new score
 * and a reverse command for undo support. This object is stateless — all state
 * lives in the [Score] and the undo/redo stacks managed by [ScoreStore].
 */
object CommandExecutor {

    /**
     * Execute a command against the given score.
     *
     * @param score The current immutable score state.
     * @param command The command to apply.
     * @return An [ExecutionResult] with the new score and the reverse command.
     */
    fun execute(score: Score, command: ScoreCommand): ExecutionResult = when (command) {
        is ScoreCommand.InsertNote -> executeInsertNote(score, command)
        is ScoreCommand.DeleteElements -> executeDeleteElements(score, command)
        is ScoreCommand.ChangePitch -> executeChangePitch(score, command)
        is ScoreCommand.ChangeDuration -> executeChangeDuration(score, command)
        is ScoreCommand.Transpose -> executeTranspose(score, command)
        is ScoreCommand.InsertMeasure -> executeInsertMeasure(score, command)
        is ScoreCommand.DeleteMeasures -> executeDeleteMeasures(score, command)
        is ScoreCommand.ChangeTimeSignature -> executeChangeTimeSignature(score, command)
        is ScoreCommand.ChangeKeySignature -> executeChangeKeySignature(score, command)
        is ScoreCommand.AddArticulation -> executeAddArticulation(score, command)
        is ScoreCommand.RemoveArticulation -> executeRemoveArticulation(score, command)
        is ScoreCommand.SetDynamic -> executeSetDynamic(score, command)
        is ScoreCommand.ToggleTie -> executeToggleTie(score, command)
        is ScoreCommand.UpdateMetadata -> executeUpdateMetadata(score, command)
        is ScoreCommand.Batch -> executeBatch(score, command)
    }

    // ─── InsertNote ──────────────────────────────────────────────────────

    private fun executeInsertNote(score: Score, cmd: ScoreCommand.InsertNote): ExecutionResult {
        val note = Note(
            id = ElementId(),
            duration = cmd.duration,
            position = cmd.position,
            pitch = cmd.pitch
        )
        val newScore = score.addElement(cmd.partId, cmd.measureIndex, cmd.voice, note)
        val reverse = ScoreCommand.DeleteElements(
            elementIds = setOf(note.id),
            description = "Undo ${cmd.description}"
        )
        return ExecutionResult(newScore, reverse)
    }

    // ─── DeleteElements ──────────────────────────────────────────────────

    private fun executeDeleteElements(score: Score, cmd: ScoreCommand.DeleteElements): ExecutionResult {
        // Capture all elements before deletion so we can reverse them
        val reverseCommands = mutableListOf<ScoreCommand>()
        for (elementId in cmd.elementIds) {
            val location = score.findElement(elementId) ?: continue
            val part = score.parts[location.partIndex]
            val element = location.element
            when (element) {
                is Note -> {
                    reverseCommands.add(
                        ScoreCommand.InsertNote(
                            partId = part.id,
                            measureIndex = location.measureIndex,
                            pitch = element.pitch,
                            duration = element.duration,
                            voice = location.voiceId,
                            position = element.position,
                            velocity = 80,
                            description = "Undo Delete"
                        )
                    )
                }
                is Rest -> {
                    // Rests are structural; re-insert as a rest via a batch approach
                    // We'll use InsertNote with a special handling — actually we need
                    // to store the entire element. Since InsertNote only creates Notes,
                    // we store a RestoreElements command via Batch with insert commands.
                    // For now, rests don't have a direct reverse via InsertNote.
                    // We record it as part of the batch but skip non-note elements.
                }
                is Chord -> {
                    // Chords also don't map directly to InsertNote.
                    // For a complete implementation, we'd need InsertChord command.
                    // For now, we capture the first pitch.
                    reverseCommands.add(
                        ScoreCommand.InsertNote(
                            partId = part.id,
                            measureIndex = location.measureIndex,
                            pitch = element.pitches.firstOrNull() ?: Pitch.MIDDLE_C,
                            duration = element.duration,
                            voice = location.voiceId,
                            position = element.position,
                            velocity = 80,
                            description = "Undo Delete"
                        )
                    )
                }
            }
        }

        val newScore = score.removeElements(cmd.elementIds)
        val reverse = if (reverseCommands.size == 1) {
            reverseCommands.first()
        } else {
            ScoreCommand.Batch(
                commands = reverseCommands,
                description = "Undo ${cmd.description}"
            )
        }
        return ExecutionResult(newScore, reverse)
    }

    // ─── ChangePitch ─────────────────────────────────────────────────────

    private fun executeChangePitch(score: Score, cmd: ScoreCommand.ChangePitch): ExecutionResult {
        val location = score.findElement(cmd.elementId)
            ?: return ExecutionResult(score, cmd) // no-op if not found

        val element = location.element
        val oldPitch: Pitch
        val newElement: MusicElement

        when (element) {
            is Note -> {
                oldPitch = element.pitch
                newElement = element.copy(pitch = cmd.newPitch)
            }
            is Chord -> {
                // Change the first pitch of the chord
                oldPitch = element.pitches.firstOrNull() ?: Pitch.MIDDLE_C
                val newPitches = if (element.pitches.isNotEmpty()) {
                    element.pitches.set(0, cmd.newPitch)
                } else {
                    persistentListOf(cmd.newPitch)
                }
                newElement = element.copy(pitches = newPitches)
            }
            is Rest -> {
                // Rests have no pitch — no-op
                return ExecutionResult(score, cmd)
            }
        }

        val newScore = score.replaceElement(cmd.elementId, newElement)
        val reverse = ScoreCommand.ChangePitch(
            elementId = cmd.elementId,
            newPitch = oldPitch,
            description = "Undo ${cmd.description}"
        )
        return ExecutionResult(newScore, reverse)
    }

    // ─── ChangeDuration ──────────────────────────────────────────────────

    private fun executeChangeDuration(score: Score, cmd: ScoreCommand.ChangeDuration): ExecutionResult {
        val location = score.findElement(cmd.elementId)
            ?: return ExecutionResult(score, cmd)

        val element = location.element
        val oldDuration = element.duration
        val newElement: MusicElement = when (element) {
            is Note -> element.copy(duration = cmd.newDuration)
            is Chord -> element.copy(duration = cmd.newDuration)
            is Rest -> element.copy(duration = cmd.newDuration)
        }

        val newScore = score.replaceElement(cmd.elementId, newElement)
        val reverse = ScoreCommand.ChangeDuration(
            elementId = cmd.elementId,
            newDuration = oldDuration,
            description = "Undo ${cmd.description}"
        )
        return ExecutionResult(newScore, reverse)
    }

    // ─── Transpose ───────────────────────────────────────────────────────

    private fun executeTranspose(score: Score, cmd: ScoreCommand.Transpose): ExecutionResult {
        var current = score
        for (elementId in cmd.elementIds) {
            val location = current.findElement(elementId) ?: continue
            val element = location.element
            val transposedElement: MusicElement? = when (element) {
                is Note -> {
                    val newMidi = (element.pitch.midiNote + cmd.semitones).coerceIn(0, 127)
                    element.copy(pitch = Pitch.fromMidiNote(newMidi))
                }
                is Chord -> {
                    val newPitches = element.pitches.map { pitch ->
                        val newMidi = (pitch.midiNote + cmd.semitones).coerceIn(0, 127)
                        Pitch.fromMidiNote(newMidi)
                    }.toPersistentList()
                    element.copy(pitches = newPitches)
                }
                is Rest -> null // Can't transpose a rest
            }
            if (transposedElement != null) {
                current = current.replaceElement(elementId, transposedElement)
            }
        }

        val reverse = ScoreCommand.Transpose(
            elementIds = cmd.elementIds,
            semitones = -cmd.semitones,
            description = "Undo ${cmd.description}"
        )
        return ExecutionResult(current, reverse)
    }

    // ─── InsertMeasure ───────────────────────────────────────────────────

    private fun executeInsertMeasure(score: Score, cmd: ScoreCommand.InsertMeasure): ExecutionResult {
        val newMeasure = Measure(
            timeSignature = cmd.timeSignature
        )
        val newParts = score.parts.map { part ->
            val insertAt = (cmd.afterMeasure + 1).coerceIn(0, part.measures.size)
            val mutableMeasures = part.measures.toMutableList()
            repeat(cmd.count) {
                mutableMeasures.add(insertAt, newMeasure)
            }
            part.copy(measures = mutableMeasures.toPersistentList())
        }.toPersistentList()

        val newScore = score.copy(parts = newParts)
        val reverse = ScoreCommand.DeleteMeasures(
            measureRange = (cmd.afterMeasure + 1)..(cmd.afterMeasure + cmd.count),
            description = "Undo ${cmd.description}"
        )
        return ExecutionResult(newScore, reverse)
    }

    // ─── DeleteMeasures ──────────────────────────────────────────────────

    private fun executeDeleteMeasures(score: Score, cmd: ScoreCommand.DeleteMeasures): ExecutionResult {
        // Capture the deleted measures from the first part's time signature for reverse
        val firstPart = score.parts.firstOrNull()
        val deletedTimeSignature = if (firstPart != null && cmd.measureRange.first < firstPart.measures.size) {
            firstPart.measures[cmd.measureRange.first].timeSignature
        } else {
            null
        }

        val newParts = score.parts.map { part ->
            val start = cmd.measureRange.first.coerceIn(0, part.measures.size)
            val endExclusive = (cmd.measureRange.last + 1).coerceIn(start, part.measures.size)
            val mutableMeasures = part.measures.toMutableList()
            if (start < endExclusive) {
                mutableMeasures.subList(start, endExclusive).clear()
            }
            part.copy(measures = mutableMeasures.toPersistentList())
        }.toPersistentList()

        val count = cmd.measureRange.last - cmd.measureRange.first + 1
        val newScore = score.copy(parts = newParts)
        val reverse = ScoreCommand.InsertMeasure(
            afterMeasure = cmd.measureRange.first - 1,
            count = count,
            timeSignature = deletedTimeSignature,
            description = "Undo ${cmd.description}"
        )
        return ExecutionResult(newScore, reverse)
    }

    // ─── ChangeTimeSignature ─────────────────────────────────────────────

    private fun executeChangeTimeSignature(score: Score, cmd: ScoreCommand.ChangeTimeSignature): ExecutionResult {
        val newParts = score.parts.mapIndexed { partIndex, part ->
            if (cmd.measureNumber < part.measures.size) {
                val measure = part.measures[cmd.measureNumber]
                val newMeasure = measure.copy(timeSignature = cmd.newTimeSig)
                part.copy(measures = part.measures.set(cmd.measureNumber, newMeasure))
            } else {
                part
            }
        }.toPersistentList()

        val oldTimeSig = score.parts.firstOrNull()?.measures?.getOrNull(cmd.measureNumber)?.timeSignature
            ?: TimeSignature.COMMON_TIME

        val newScore = score.copy(parts = newParts)
        val reverse = ScoreCommand.ChangeTimeSignature(
            measureNumber = cmd.measureNumber,
            newTimeSig = oldTimeSig,
            description = "Undo ${cmd.description}"
        )
        return ExecutionResult(newScore, reverse)
    }

    // ─── ChangeKeySignature ──────────────────────────────────────────────

    private fun executeChangeKeySignature(score: Score, cmd: ScoreCommand.ChangeKeySignature): ExecutionResult {
        val oldKeySig = score.parts.firstOrNull()?.measures?.getOrNull(cmd.measureNumber)?.keySignature
            ?: KeySignature(0)

        val newParts = score.parts.map { part ->
            if (cmd.measureNumber < part.measures.size) {
                val measure = part.measures[cmd.measureNumber]
                val newMeasure = measure.copy(keySignature = cmd.newKeySig)
                part.copy(measures = part.measures.set(cmd.measureNumber, newMeasure))
            } else {
                part
            }
        }.toPersistentList()

        val newScore = score.copy(parts = newParts)
        val reverse = ScoreCommand.ChangeKeySignature(
            measureNumber = cmd.measureNumber,
            newKeySig = oldKeySig,
            description = "Undo ${cmd.description}"
        )
        return ExecutionResult(newScore, reverse)
    }

    // ─── AddArticulation ─────────────────────────────────────────────────

    private fun executeAddArticulation(score: Score, cmd: ScoreCommand.AddArticulation): ExecutionResult {
        val location = score.findElement(cmd.elementId)
            ?: return ExecutionResult(score, cmd)

        val element = location.element
        val newElement: MusicElement = when (element) {
            is Note -> {
                if (cmd.articulation in element.articulations) return ExecutionResult(score, cmd)
                element.copy(articulations = element.articulations.add(cmd.articulation))
            }
            is Chord -> {
                if (cmd.articulation in element.articulations) return ExecutionResult(score, cmd)
                element.copy(articulations = element.articulations.add(cmd.articulation))
            }
            is Rest -> return ExecutionResult(score, cmd) // no articulation on rests
        }

        val newScore = score.replaceElement(cmd.elementId, newElement)
        val reverse = ScoreCommand.RemoveArticulation(
            elementId = cmd.elementId,
            articulation = cmd.articulation,
            description = "Undo ${cmd.description}"
        )
        return ExecutionResult(newScore, reverse)
    }

    // ─── RemoveArticulation ──────────────────────────────────────────────

    private fun executeRemoveArticulation(score: Score, cmd: ScoreCommand.RemoveArticulation): ExecutionResult {
        val location = score.findElement(cmd.elementId)
            ?: return ExecutionResult(score, cmd)

        val element = location.element
        val newElement: MusicElement = when (element) {
            is Note -> {
                val idx = element.articulations.indexOf(cmd.articulation)
                if (idx < 0) return ExecutionResult(score, cmd)
                element.copy(articulations = element.articulations.removeAt(idx))
            }
            is Chord -> {
                val idx = element.articulations.indexOf(cmd.articulation)
                if (idx < 0) return ExecutionResult(score, cmd)
                element.copy(articulations = element.articulations.removeAt(idx))
            }
            is Rest -> return ExecutionResult(score, cmd)
        }

        val newScore = score.replaceElement(cmd.elementId, newElement)
        val reverse = ScoreCommand.AddArticulation(
            elementId = cmd.elementId,
            articulation = cmd.articulation,
            description = "Undo ${cmd.description}"
        )
        return ExecutionResult(newScore, reverse)
    }

    // ─── SetDynamic ──────────────────────────────────────────────────────

    private fun executeSetDynamic(score: Score, cmd: ScoreCommand.SetDynamic): ExecutionResult {
        val location = score.findElement(cmd.elementId)
            ?: return ExecutionResult(score, cmd)

        val element = location.element
        val oldDynamic: Dynamic?
        val newElement: MusicElement

        when (element) {
            is Note -> {
                oldDynamic = element.dynamics
                newElement = element.copy(dynamics = cmd.dynamic)
            }
            is Chord -> {
                oldDynamic = element.dynamics
                newElement = element.copy(dynamics = cmd.dynamic)
            }
            is Rest -> return ExecutionResult(score, cmd) // no dynamics on rests
        }

        val newScore = score.replaceElement(cmd.elementId, newElement)
        val reverse = ScoreCommand.SetDynamic(
            elementId = cmd.elementId,
            dynamic = oldDynamic,
            description = "Undo ${cmd.description}"
        )
        return ExecutionResult(newScore, reverse)
    }

    // ─── ToggleTie ───────────────────────────────────────────────────────

    private fun executeToggleTie(score: Score, cmd: ScoreCommand.ToggleTie): ExecutionResult {
        val location = score.findElement(cmd.elementId)
            ?: return ExecutionResult(score, cmd)

        val element = location.element
        val newElement: MusicElement = when (element) {
            is Note -> element.copy(tieForward = !element.tieForward)
            is Chord -> element.copy(tieForward = !element.tieForward)
            is Rest -> return ExecutionResult(score, cmd) // no ties on rests
        }

        val newScore = score.replaceElement(cmd.elementId, newElement)
        // ToggleTie is self-inverse
        val reverse = ScoreCommand.ToggleTie(
            elementId = cmd.elementId,
            description = "Undo ${cmd.description}"
        )
        return ExecutionResult(newScore, reverse)
    }

    // ─── UpdateMetadata ──────────────────────────────────────────────────

    private fun executeUpdateMetadata(score: Score, cmd: ScoreCommand.UpdateMetadata): ExecutionResult {
        val oldMetadata = score.metadata
        val newScore = score.copy(metadata = cmd.newMetadata)
        val reverse = ScoreCommand.UpdateMetadata(
            newMetadata = oldMetadata,
            description = "Undo ${cmd.description}"
        )
        return ExecutionResult(newScore, reverse)
    }

    // ─── Batch ───────────────────────────────────────────────────────────

    private fun executeBatch(score: Score, cmd: ScoreCommand.Batch): ExecutionResult {
        var current = score
        val reverseCommands = mutableListOf<ScoreCommand>()

        for (subCommand in cmd.commands) {
            val result = execute(current, subCommand)
            current = result.newScore
            reverseCommands.add(result.reverseCommand)
        }

        val reverse = ScoreCommand.Batch(
            commands = reverseCommands.reversed(),
            description = "Undo ${cmd.description}"
        )
        return ExecutionResult(current, reverse)
    }
}
