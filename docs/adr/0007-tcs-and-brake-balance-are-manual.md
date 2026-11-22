# TCS level and brake balance are manual metadata

Status: accepted

GT7's telemetry cannot supply either setting. The packet exposes `Flags` bit 11 (`TCSActive`), which says only that traction control is intervening at that instant, never the 0 to 5 level. There is no brake balance field in any packet. So both are entered by the driver, like the tyre compound.

`LapRecord.tcs` and `LapRecord.brakeBalance` are nullable ints, projected into `LapView` and `LapSummary` and clamped in `LapStore.setTcs` / `setBrakeBalance` to `0..5` and `-5..5`. They are not sample fields, so the telemetry codec and its version are unchanged, and old `lap.json` files decode with `null`. Exports and archives carry them because `lap.json` is copied verbatim.

Entry mirrors the compound. Record stamps every new lap with `SettingsStore.currentTcs` and `currentBrakeBalance`, and Edit lap corrects or clears one lap. Untagged values stay hidden, never guessed.

Display uses short labels: `TCS off` at 0, `TCS n` otherwise, and a signed `BB +n` / `BB -n` / `BB 0`. The player's `Speed` / `Gear` row and the compare row put the label beside the lap's tyre badge, so a value is never separated from the tyre it ran on; the expanded player header repeats them after the compound name.

## Considered options

**Read the TCS-active flag as the level.** Rejected: the bit is momentary. It shows intervention in a corner, not the setting in the setup menu.

**Store it per sample.** Rejected: both are setup values that only change in the pits, and the model would grow for no gain.

**Infer it from the event or car.** Rejected: neither determines the setting, and a wrong guess is worse than an empty row.

## Consequences

- `core/session/LapSetup.kt` owns the ranges and labels; UI reads `tcsLabel`, `brakeBalanceLabel` and `setupLabel`.
- `EditLapSheet` keeps local state for tyre, TCS and brake balance so a committed value updates without closing the sheet.
- The Record card and the library's Edit lap both stamp the same fields, so the library no longer offers a Tyres row that cannot save.
- The `TyreBadge` content description uses the compound's display label, and its test tag survives `clearAndSetSemantics`.
