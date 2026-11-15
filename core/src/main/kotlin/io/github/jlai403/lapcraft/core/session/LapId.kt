package io.github.jlai403.lapcraft.core.session

import kotlinx.serialization.Serializable

/**
 * The identity of one lap. After the layout migration this is the name of the
 * lap's own directory under `files/laps/`; no container is involved.
 */
@JvmInline
@Serializable
value class LapId(val value: String) {
    override fun toString(): String = value
}