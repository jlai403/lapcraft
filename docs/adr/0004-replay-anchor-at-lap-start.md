# Replay anchor at the lap start

Status: accepted. Legacy removal is a separate pass.

`ImportSyncScreen` used to ask for the finish-line frame that ends the lap and trim backwards (`trimIn = anchor − lapTime`, `trimOut = anchor`). The picked frame was not self-verifying: any frame in the last second of a lap looked plausible, and a clip holding several laps let the driver be a whole lap out with nothing on screen saying so. The HUD lap timer on that frame reads `lapTime − δ`, so the residual needed arithmetic against the telemetry lap time.

The anchor is now the lap start, the frame where the HUD lap timer restarts near 0:00.0.

- The trim runs forward: `trimIn = anchor − offset`, `trimOut = trimIn + lapTime`, where `offset` is the HUD lap time typed on the anchor frame (0.000 when the frame reads 0:00.000).
- `LapClock.forAnchor(anchorMs, lapTimeMs)` is replaced by `forLapStart(startMs, lapTimeMs)`.
- `SyncValidator` checks the derived finish, meaning telemetry at `lapTime` must land on the line, replacing the vacuous `startTrackPercent = 0f` stub.
- The offset is a screen-local value folded into the stored `leadInMs`; no new persisted field and no format change. `ClipTrimmer` already preserves the sub-frame position as `leadInMs`.

## Consequences

- The picked frame is self-verifying. A start frame reads 0:00.0xx, while a mid-lap frame reads about 0:35.
- The correction is the number printed on the frame (`0:00.019` becomes `0.019`), with no subtraction.
- The clip head is the right lap, and the finish is the checkable endpoint.
- Anchor the first frame at or after the line, never the nearest frame and never the frame before. "After" keeps the residual a small positive number and guarantees the trim contains the whole lap. "Before" drops the lap's last frame.
- Offsets of a whole frame or more are rejected; the driver steps a frame instead.
- `syncOffsetMs` keeps its read path for legacy laps but is never written by the new flow.
