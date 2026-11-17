package io.github.jlai403.lapcraft.core.session.legacy

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Reads the `session.json` layout that predates `laps/<id>/`; migration is the only consumer. */
object SessionJsonCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun decodeSession(text: String): SessionMetadata = json.decodeFromString(text)
}
