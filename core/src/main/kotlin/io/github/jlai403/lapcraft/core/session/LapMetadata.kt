package io.github.jlai403.lapcraft.core.session

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LapMetadata(
    val schemaVersion: Int = 1,
    val index: Int,
    val lapTimeMs: Int,
    val startNanos: Long,
    val endNanos: Long,
    val sampleCount: Int,
    val videoFile: String,
    @SerialName("videoStartOffsetMs")
    val leadInMs: Int,
    val syncOffsetMs: Int = 0,
    val needsCut: Boolean = false,
    val sourceFile: String? = null,
    val driverPsnId: String? = null,
    val event: io.github.jlai403.lapcraft.core.events.EventTag? = null,
)
