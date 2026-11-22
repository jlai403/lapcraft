package io.github.jlai403.lapcraft.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun SetupRow(
    label: String,
    value: String,
    unset: Boolean,
    onChange: () -> Unit,
    changeTag: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (unset) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        Spacer(Modifier.weight(1f))
        GhostPill(
            text = if (unset) "Set" else "Change",
            onClick = onChange,
            modifier = Modifier.testTag(changeTag),
        )
    }
}

internal fun tcsRowValue(tcs: Int?): String = when {
    tcs == null -> "Not set"
    tcs <= 0 -> "Off"
    else -> tcs.toString()
}

internal fun brakeBalanceRowValue(balance: Int?): String =
    balance?.let { if (it > 0) "+$it" else it.toString() } ?: "Not set"

@Composable
internal fun SetupPickerDialog(
    title: String,
    levels: Iterable<Int>,
    selected: Int?,
    label: (Int) -> String,
    onPick: (Int) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                levels.forEach { level ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(level) }
                            .padding(vertical = 8.dp)
                            .testTag("setup-pick-$level"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            label(level),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected == level) {
                            Text(
                                "Current",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClear) {
                Text("Clear", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}
