package io.github.jlai403.lapcraft.core.events

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EventKindCodecTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Wrapper(val event: EventTag)

    @Test
    fun `legacy daily string decodes to Daily`() {
        val decoded = json.decodeFromString<EventTag>(
            """{"id":"x","kind":"daily","label":"Daily A"}"""
        )
        assertEquals(EventKind.Daily, decoded.kind)
    }

    @Test
    fun `legacy time-trial string decodes to TimeTrial`() {
        val decoded = json.decodeFromString<EventTag>(
            """{"id":"x","kind":"time-trial","label":"TT"}"""
        )
        assertEquals(EventKind.TimeTrial, decoded.kind)
    }

    @Test
    fun `legacy championship string decodes to Championship`() {
        val decoded = json.decodeFromString<EventTag>(
            """{"id":"x","kind":"championship","label":"World Series"}"""
        )
        assertEquals(EventKind.Championship, decoded.kind)
    }

    @Test
    fun `re-encode keeps hyphenated serial name`() {
        val encoded = json.encodeToString(
            EventTag.serializer(),
            EventTag(id = "x", kind = EventKind.TimeTrial, label = "TT"),
        )
        assertTrue(encoded.contains("\"kind\":\"time-trial\""), encoded)
    }

    @Test
    fun `cached event legacy string decodes`() {
        val decoded = json.decodeFromString<CachedEvent>(
            """{"id":"c","kind":"championship","label":"R1","startMs":1,"endMs":2,"source":"polyphony-cdn","fetchedAtMs":3}"""
        )
        assertEquals(EventKind.Championship, decoded.kind)
    }
}
