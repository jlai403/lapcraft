package io.github.jlai403.lapcraft.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.jlai403.lapcraft.app.settings.ReplayAudio
import io.github.jlai403.lapcraft.app.settings.ReplayCodec
import io.github.jlai403.lapcraft.app.settings.ReplayKeyframes
import io.github.jlai403.lapcraft.app.settings.ReplayPreset
import io.github.jlai403.lapcraft.app.settings.ReplayResolution
import io.github.jlai403.lapcraft.app.settings.audioCaption
import io.github.jlai403.lapcraft.app.settings.codecCaption
import io.github.jlai403.lapcraft.app.settings.keyframesCaption
import io.github.jlai403.lapcraft.app.settings.presetCaption
import io.github.jlai403.lapcraft.app.settings.resolutionCaption

@Composable
internal fun ReplayCompressionControls(
    preset: ReplayPreset,
    keyframes: ReplayKeyframes,
    audio: ReplayAudio,
    resolution: ReplayResolution,
    codec: ReplayCodec,
    highlightChanges: Boolean,
    onPresetChange: (ReplayPreset) -> Unit,
    onKeyframesChange: (ReplayKeyframes) -> Unit,
    onAudioChange: (ReplayAudio) -> Unit,
    onResolutionChange: (ReplayResolution) -> Unit,
    onCodecChange: (ReplayCodec) -> Unit,
    modifier: Modifier = Modifier,
) {
    var presetMenu by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box {
            SettingRow(
                label = "Preset",
                supporting = if (preset != ReplayPreset.Balanced) "Changed: ${presetCaption(preset)}" else presetCaption(preset),
                value = preset.label,
                modifier = Modifier.testTag("replay-preset"),
                trailing = { Chevron() },
                onClick = { presetMenu = true },
            )
            DropdownMenu(expanded = presetMenu, onDismissRequest = { presetMenu = false }) {
                ReplayPreset.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            onPresetChange(option)
                            presetMenu = false
                        },
                    )
                }
            }
        }
        if (preset != ReplayPreset.Original) {
            SettingRow(
                label = "Advanced",
                value = "Resolution · Codec · Keyframes · Audio",
                modifier = Modifier.testTag("replay-advanced"),
                trailing = { Chevron() },
                onClick = { advanced = !advanced },
            )
            if (advanced) {
                SettingChoice(
                    label = "Resolution",
                    options = ReplayResolution.entries,
                    selected = resolution,
                    optionLabel = { it.label },
                    caption = resolutionCaption(resolution),
                    changed = highlightChanges && resolution != ReplayResolution.FullHd,
                    modifier = Modifier.testTag("replay-resolution"),
                    onSelect = onResolutionChange,
                )
                SettingChoice(
                    label = "Codec",
                    options = ReplayCodec.entries,
                    selected = codec,
                    optionLabel = { it.label },
                    caption = codecCaption(codec),
                    changed = highlightChanges && codec != ReplayCodec.Hevc,
                    modifier = Modifier.testTag("replay-codec"),
                    onSelect = onCodecChange,
                )
                SettingChoice(
                    label = "Keyframes",
                    options = ReplayKeyframes.entries,
                    selected = keyframes,
                    optionLabel = { it.label },
                    caption = keyframesCaption(keyframes),
                    changed = highlightChanges && keyframes != ReplayKeyframes.Frame,
                    modifier = Modifier.testTag("replay-keyframes"),
                    onSelect = onKeyframesChange,
                )
                SettingChoice(
                    label = "Audio",
                    options = ReplayAudio.entries,
                    selected = audio,
                    optionLabel = { it.label },
                    caption = audioCaption(audio),
                    changed = highlightChanges && audio != ReplayAudio.Stereo,
                    modifier = Modifier.testTag("replay-audio"),
                    onSelect = onAudioChange,
                )
            }
        }
    }
}

@Composable
private fun Chevron() {
    Icon(
        imageVector = Icons.Filled.KeyboardArrowDown,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun <T> SettingChoice(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    caption: String,
    changed: Boolean,
    modifier: Modifier = Modifier,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            Box {
                GhostPill(
                    text = optionLabel(selected),
                    onClick = { open = true },
                    trailingIcon = Icons.Filled.KeyboardArrowDown,
                )
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(optionLabel(option)) },
                            onClick = {
                                onSelect(option)
                                open = false
                            },
                        )
                    }
                }
            }
        }
        Text(
            if (changed) "Changed: $caption" else caption,
            style = MaterialTheme.typography.bodySmall,
            color = if (changed) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
