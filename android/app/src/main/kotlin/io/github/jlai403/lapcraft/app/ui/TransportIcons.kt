package io.github.jlai403.lapcraft.app.ui

import androidx.compose.ui.graphics.vector.ImageVector

internal object TransportIcons {
    val Pause: ImageVector = vectorIcon("Pause", "M6 19h4V5H6v14zm8-14v14h4V5h-4z")
    val Rewind: ImageVector = vectorIcon("Rewind", "M11 18V6l-8.5 6 8.5 6zm.5-6l8.5 6V6l-8.5 6z")
    val Forward: ImageVector = vectorIcon("Forward", "M4 18l8.5-6L4 6v12zm9-12v12l8.5-6L13 6z")
    val SkipPrevious: ImageVector = vectorIcon("SkipPrevious", "M6 6h2v12H6zm3.5 6l8.5 6V6z")
    val SkipNext: ImageVector = vectorIcon("SkipNext", "M6 18l8.5-6L6 6v12zM16 6v12h2V6z")
    val Loop: ImageVector = vectorIcon(
        "Loop",
        "M7 7h10v3l4-4-4-4v3H5v6h2V7zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2v4z",
    )
}
