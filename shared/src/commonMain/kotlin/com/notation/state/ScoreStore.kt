package com.notation.state

import com.notation.model.Score
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for score state with undo/redo support.
 *
 * All mutations to the score flow through [dispatch], which executes a [ScoreCommand]
 * via [CommandExecutor], captures the reverse command for undo, and updates all
 * reactive state flows. The undo/redo stacks are bounded by [MAX_UNDO_DEPTH].
 *
 * @param initialScore The starting score state.
 */
class ScoreStore(initialScore: Score) {

    companion object {
        /** Maximum number of undo entries retained before oldest entries are discarded. */
        const val MAX_UNDO_DEPTH = 200
    }

    /**
     * An entry on the undo or redo stack, containing the human-readable description
     * and the command that reverses the operation.
     */
    private data class UndoEntry(
        val description: String,
        val reverseCommand: ScoreCommand
    )

    private val _score = MutableStateFlow(initialScore)

    /** Reactive stream of the current [Score] state. */
    val score: StateFlow<Score> = _score.asStateFlow()

    private val _canUndo = MutableStateFlow(false)

    /** Whether there is at least one operation that can be undone. */
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)

    /** Whether there is at least one operation that can be redone. */
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val _undoDescription = MutableStateFlow<String?>(null)

    /** Human-readable description of the next undo operation, or null if empty. */
    val undoDescription: StateFlow<String?> = _undoDescription.asStateFlow()

    private val _redoDescription = MutableStateFlow<String?>(null)

    /** Human-readable description of the next redo operation, or null if empty. */
    val redoDescription: StateFlow<String?> = _redoDescription.asStateFlow()

    private val undoStack = ArrayDeque<UndoEntry>()
    private val redoStack = ArrayDeque<UndoEntry>()

    /**
     * Executes a [ScoreCommand] against the current score state.
     *
     * The command is executed via [CommandExecutor]. If it produces a non-null result,
     * the new score is published, the reverse command is pushed onto the undo stack,
     * and the redo stack is cleared.
     *
     * @param command The command to execute.
     */
    fun dispatch(command: ScoreCommand) {
        val currentScore = _score.value
        val result = CommandExecutor.execute(currentScore, command)

        _score.value = result.newScore

        // Push the reverse command onto the undo stack
        undoStack.addLast(UndoEntry(
            description = command.description,
            reverseCommand = result.reverseCommand
        ))

        // Trim undo stack if it exceeds max depth
        while (undoStack.size > MAX_UNDO_DEPTH) {
            undoStack.removeFirst()
        }

        // Any new action invalidates the redo history
        redoStack.clear()

        updateUndoRedoState()
    }

    /**
     * Undoes the most recent operation by executing its reverse command.
     *
     * The reverse command's own reverse is pushed onto the redo stack so
     * the operation can be re-applied later.
     */
    fun undo() {
        if (undoStack.isEmpty()) return

        val entry = undoStack.removeLast()
        val currentScore = _score.value
        val result = CommandExecutor.execute(currentScore, entry.reverseCommand)

        _score.value = result.newScore

        // Push to redo with the reverse of the reverse (so redo re-applies the original)
        redoStack.addLast(UndoEntry(
            description = entry.description,
            reverseCommand = result.reverseCommand
        ))

        updateUndoRedoState()
    }

    /**
     * Redoes the most recently undone operation by executing its reverse command.
     *
     * The reverse command's own reverse is pushed back onto the undo stack.
     */
    fun redo() {
        if (redoStack.isEmpty()) return

        val entry = redoStack.removeLast()
        val currentScore = _score.value
        val result = CommandExecutor.execute(currentScore, entry.reverseCommand)

        _score.value = result.newScore

        // Push back to undo
        undoStack.addLast(UndoEntry(
            description = entry.description,
            reverseCommand = result.reverseCommand
        ))

        // Trim undo stack if it exceeds max depth
        while (undoStack.size > MAX_UNDO_DEPTH) {
            undoStack.removeFirst()
        }

        updateUndoRedoState()
    }

    /**
     * Returns an immutable snapshot of the current score.
     */
    fun currentScore(): Score = _score.value

    /**
     * Synchronizes the canUndo/canRedo and description state flows
     * with the current stack state.
     */
    private fun updateUndoRedoState() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
        _undoDescription.value = undoStack.lastOrNull()?.description
        _redoDescription.value = redoStack.lastOrNull()?.description
    }
}
