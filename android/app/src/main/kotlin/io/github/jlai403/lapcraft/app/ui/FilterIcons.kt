package io.github.jlai403.lapcraft.app.ui

import androidx.compose.ui.graphics.vector.ImageVector

internal object FilterIcons {
    // filter_alt: a funnel reads as "filter"; Material's filter_list and sort share one glyph.
    val Filter: ImageVector = vectorIcon(
        "Filter",
        "M4.25 5.61C6.27 8.2 10 13 10 13v6c0 .55.45 1 1 1h2c.55 0 1-.45 1-1v-6s3.72-4.8 " +
            "5.74-7.39c.51-.66.04-1.61-.79-1.61H5.04c-.83 0-1.3.95-.79 1.61z",
    )
    val Sort: ImageVector = vectorIcon(
        "Sort",
        "M16 17.01V10h-2v7.01h-3L15 21l4-3.99h-3zM9 3L5 6.99h3V14h2V6.99h3L9 3z",
    )
    // tune: the view options (grouping + sort) glyph.
    val View: ImageVector = vectorIcon(
        "View",
        "M3 17v2h6v-2H3zM3 5v2h10V5H3zm10 16v-2h8v-2h-8v-2h-2v6h2zM7 9v2H3v2h4v2h2V9H7zm14 4v-2H11v2h10zm-6-4h2V7h4V5h-4V3h-2v6z",
    )
}
