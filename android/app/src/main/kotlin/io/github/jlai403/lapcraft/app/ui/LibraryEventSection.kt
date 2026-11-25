package io.github.jlai403.lapcraft.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.jlai403.lapcraft.core.events.LapEventGroup
import io.github.jlai403.lapcraft.core.events.eventRangeLabel

@Composable
fun CurrentEventHeader(group: LapEventGroup, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Text(
            group.tag?.label ?: "No event",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            eventDetail(group),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun PastEventsHeader(
    lapCount: Int,
    eventCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (eventCount == 0) "Past events · $lapCount lap${if (lapCount == 1) "" else "s"}"
            else "Past events · $lapCount lap${if (lapCount == 1) "" else "s"} · $eventCount event${if (eventCount == 1) "" else "s"}",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun eventDetail(group: LapEventGroup): String {
    val tag = group.tag
    val parts = mutableListOf<String>()
    tag?.let { t ->
        t.series?.takeIf { !t.label.contains(it, ignoreCase = true) }?.let { parts += it }
    }
    val start = tag?.startMs
    val end = tag?.endMs
    if (start != null && end != null && end > start) {
        parts += eventRangeLabel(start, end)
    }
    tag?.carClass?.let { parts += it }
    val tracks = group.laps.map { it.trackLabel }.distinct()
    if (tracks.size == 1) parts += tracks.first() else parts += "${tracks.size} tracks"
    parts += "${group.laps.size} lap${if (group.laps.size == 1) "" else "s"}"
    parts += "Best ${lapTimeLabel(group.bestLapMs)}"
    return parts.joinToString(" · ")
}