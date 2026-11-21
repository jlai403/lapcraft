# Tyre compound is manual metadata

Status: accepted

GT7's telemetry carries no tyre compound. The UDP packet (A, B, ~ and C, up to 368 bytes) sends tyre temperatures and, in C, surface type and car category, but never the fitted compound, wear or pressure. Every community parser agrees. So the compound shown on a lap must come from the driver.

The compound is stored on `LapRecord.tyre` as a nullable `TyreCompound` (two-letter GT7 code plus a hardness tier) and projected into `LapView` and `LapSummary`. It is not part of the sample codec: it is lap metadata, so the telemetry version stays at 2 and old `lap.json` files decode with `null`. Imports and archives carry it automatically because `lap.json` is copied verbatim.

Entry is through two flows. Record stamps every new lap with the current tyres chosen in the Stream card, so a stint needs one tap instead of one per lap. Edit lap corrects a single lap, including clearing the tag. The badge is hidden when a lap is untagged, so untagged laps never show a wrong compound.

## Considered options

**Read it from telemetry.** Rejected: the field does not exist in any packet format, and screen capture or OCR of the replay would break the telemetry-only rule.

**Infer it from the event or car.** Rejected: Daily Races and Time Trials do not reliably publish their tyre rule, and guessing a compound is worse than showing none.

**Per-sample compound.** Rejected: the compound only changes in the pits, and a lap that includes a pit stop is a different case. One value per lap keeps the model small and the badge unambiguous.

## Consequences

- `TyreCompound` (core/tire) carries the code, label and tier; `TyreTier` drives the badge colour (hard white, medium yellow, soft red, intermediate green, wet blue, dirt tan, snow ice).
- `LapStore.setTyre` persists a change; the Record setting is read at save time, not at service construction.
- `TyreBadge` shows the code in a filled circle with a content description, so colour is never the only signal.
- Untagged laps and laps captured before this change show no badge.
- The lap player shows the badge next to the lap time and the full name in the expanded header; the compare screen shows one badge per pane in the readout row and one per lap in the Speed/Gear row.