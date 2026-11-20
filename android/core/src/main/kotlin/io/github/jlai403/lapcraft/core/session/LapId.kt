package io.github.jlai403.lapcraft.core.session

import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class LapId(val value: String) {
    override fun toString(): String = value
}