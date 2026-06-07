package com.notation.serialization

import com.notation.model.Score
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

/**
 * Internal JSON serialization format for saving and loading scores.
 *
 * Wraps the [Score] in a [SaveFile] envelope that includes format version,
 * application version, and a timestamp. This enables forward-compatible
 * migrations when the data model evolves.
 */
object InternalFormat {

    /** Current serialization format version. Increment when making breaking changes. */
    const val CURRENT_VERSION = 1

    /**
     * Pre-configured [Json] instance for internal format serialization.
     *
     * - `prettyPrint`: Human-readable output for debugging and version control.
     * - `encodeDefaults = false`: Omit default values to keep files compact.
     * - `ignoreUnknownKeys`: Forward compatibility with newer format versions.
     * - `classDiscriminator = "type"`: Polymorphic type key for sealed hierarchies.
     */
    private val json = Json {
        prettyPrint = true
        encodeDefaults = false
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }

    /**
     * Envelope for a saved score file.
     *
     * @property formatVersion The format version used to serialize this file.
     * @property appVersion The application version that produced this file.
     * @property savedAt Epoch milliseconds when the file was saved.
     * @property score The musical score data.
     */
    @Serializable
    data class SaveFile(
        val formatVersion: Int = CURRENT_VERSION,
        val appVersion: String,
        val savedAt: Long,
        val score: Score
    )

    /**
     * Serializes a [Score] to a JSON string in the internal format.
     *
     * @param score The score to serialize.
     * @param appVersion Application version string to embed in the file.
     * @return A pretty-printed JSON string.
     */
    fun saveToJson(score: Score, appVersion: String = "1.0.0"): String {
        val saveFile = SaveFile(
            formatVersion = CURRENT_VERSION,
            appVersion = appVersion,
            savedAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
            score = score
        )
        return json.encodeToString(saveFile)
    }

    /**
     * Deserializes a [Score] from a JSON string in the internal format.
     *
     * If the file was saved with an older format version, migration logic
     * is applied to upgrade it to the current version.
     *
     * @param jsonString The JSON string to deserialize.
     * @return The deserialized [Score].
     * @throws IllegalArgumentException if the format version is unsupported.
     */
    fun loadFromJson(jsonString: String): Score {
        val saveFile = json.decodeFromString<SaveFile>(jsonString)
        val migrated = migrateIfNeeded(saveFile)
        return migrated.score
    }

    /**
     * Applies any necessary migrations to bring an older [SaveFile] up to
     * [CURRENT_VERSION].
     *
     * Currently a no-op since we only have version 1. Future migrations
     * should be added as sequential `when` branches.
     */
    private fun migrateIfNeeded(saveFile: SaveFile): SaveFile {
        var current = saveFile

        when (current.formatVersion) {
            CURRENT_VERSION -> { /* Already at current version, no migration needed */ }
            // Future migrations:
            // 1 -> {
            //     current = migrateV1toV2(current)
            //     current = current.copy(formatVersion = 2)
            // }
            else -> {
                if (current.formatVersion > CURRENT_VERSION) {
                    throw IllegalArgumentException(
                        "File format version ${current.formatVersion} is newer than " +
                            "supported version $CURRENT_VERSION. Please update the application."
                    )
                }
                throw IllegalArgumentException(
                    "Unsupported file format version: ${current.formatVersion}"
                )
            }
        }

        return current
    }
}
