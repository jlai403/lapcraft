package io.github.jlai403.lapcraft.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.jlai403.lapcraft.app.replay.ClipFrame
import kotlinx.coroutines.delay

@Composable
fun ClipFilmstrip(
    frames: List<ClipFrame>,
    currentMs: Long,
    onPick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val currentIndex = frames.indexOfMinBy { kotlin.math.abs(it.timeMs - currentMs) }
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) listState.animateScrollToItem(currentIndex.coerceAtMost((frames.size - 1).coerceAtLeast(0)))
    }
    LazyRow(
        state = listState,
        modifier = modifier.testTag("sync-filmstrip"),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(frames) { _, frame ->
            val current = kotlin.math.abs(frame.timeMs - currentMs) < 750
            Box(
                modifier = Modifier
                    .size(width = 112.dp, height = 26.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onPick(frame.timeMs) },
            ) {
                androidx.compose.foundation.Image(
                    bitmap = frame.bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                )
                if (current) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .size(width = 112.dp, height = 2.dp)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}

private fun List<ClipFrame>.indexOfMinBy(selector: (ClipFrame) -> Long): Int {
    if (isEmpty()) return -1
    var best = 0
    var bestVal = selector(this[0])
    for (i in 1 until size) {
        val v = selector(this[i])
        if (v < bestVal) { bestVal = v; best = i }
    }
    return best
}