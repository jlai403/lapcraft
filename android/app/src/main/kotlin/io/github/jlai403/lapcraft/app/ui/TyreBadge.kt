package io.github.jlai403.lapcraft.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jlai403.lapcraft.core.tire.TyreCompound
import io.github.jlai403.lapcraft.core.tire.TyreTier

@Composable
fun TyreBadge(
    compound: TyreCompound,
    size: Dp = 20.dp,
    modifier: Modifier = Modifier,
) {
    val background = tyreColor(compound.tier)
    val content = tyreContentColor(compound.tier)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .testTag("tyre-badge")
            .clearAndSetSemantics {
                contentDescription = "${compound.label} tyres"
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            compound.code,
            color = content,
            fontSize = (size.value * 0.42f).sp,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
        )
    }
}

fun tyreColor(tier: TyreTier): Color = when (tier) {
    TyreTier.Hard -> TyreHard
    TyreTier.Medium -> TyreMedium
    TyreTier.Soft -> TyreSoft
    TyreTier.Intermediate -> TyreIntermediate
    TyreTier.Wet -> TyreWet
    TyreTier.Dirt -> TyreDirt
    TyreTier.Snow -> TyreSnow
}

fun tyreContentColor(tier: TyreTier): Color = when (tier) {
    TyreTier.Hard, TyreTier.Medium, TyreTier.Snow -> Color(0xFF0D1114)
    else -> Color(0xFFF8FAFC)
}

@Composable
fun TyrePickerDialog(
    selected: TyreCompound?,
    onPick: (TyreCompound) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text("Tyres") },
        text = {
            androidx.compose.foundation.layout.Column(
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
            ) {
                listOf(
                    "Comfort" to listOf(TyreCompound.COMFORT_HARD, TyreCompound.COMFORT_MEDIUM, TyreCompound.COMFORT_SOFT),
                    "Sports" to listOf(TyreCompound.SPORTS_HARD, TyreCompound.SPORTS_MEDIUM, TyreCompound.SPORTS_SOFT),
                    "Racing" to listOf(TyreCompound.RACING_HARD, TyreCompound.RACING_MEDIUM, TyreCompound.RACING_SOFT, TyreCompound.INTERMEDIATE, TyreCompound.WET),
                    "Off-road" to listOf(TyreCompound.DIRT, TyreCompound.SNOW),
                ).forEach { (group, items) ->
                    androidx.compose.material3.Text(
                        group,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    items.forEach { compound ->
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.clickable { onPick(compound) },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
                        ) {
                            TyreBadge(compound = compound, size = 18.dp)
                            androidx.compose.material3.Text(
                                compound.label,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            if (selected == compound) {
                                androidx.compose.material3.Text(
                                    "Current",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onClear) {
                androidx.compose.material3.Text("Clear", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                androidx.compose.material3.Text("Done")
            }
        },
    )
}