# 0004 — Replay anchor at the lap start

## Status

Accepted (pass 1 of the replay-anchor rework; legacy removal is a separate pass).

## Context

`ImportSyncScreen` used to ask for the **finish-line** frame that ends the lap and trim
backwards (`trimIn = anchor − lapTime`, `trimOut = anchor`). That gives a correct clip, but
the picked frame is not self-verifying: any frame in the last second of a lap looks plausible,
and a clip holding several laps lets the user be a whole lap out with nothing on screen saying
so. The HUD lap timer on that frame reads `lapTime − δ`, so the residual needs arithmetic
against the telemetry lap time.

## Decision

Anchor the **lap start** instead: the frame where the HUD lap timer restarts near 0:00.0.

- The trim runs forward: `trimIn = anchor − offset`, `trimOut = trimIn + lapTime`, where
  `offset` is the HUD lap time typed on the anchor frame (0.000 when the frame reads 0:00.000).
- `LapClock.forAnchor(anchorMs, lapTimeMs)` is replaced by `forLapStart(startMs, lapTimeMs)`.
- `SyncValidator` checks the **derived finish** (telemetry at `lapTime` must land on the line),
  replacing the vacuous `startTrackPercent = 0f` stub.
- The offset is a screen-local value folded into the stored `leadInMs`; no new persisted field
  and no format change. `ClipTrimmer` already preserves the sub-frame position as `leadInMs`.

## Consequences

- The picked frame is self-verifying: a start frame reads 0:00.0xx, and a mid-lap frame reads
  ~0:35, so a wrong pick is obvious without other information.
- The correction is the number printed on the frame (`0:00.019 → 0.019`), with no subtraction.
- The clip head is the right lap; the finish is the checkable endpoint.
- Frame granularity: anchor the **first frame at or after** the line, never the nearest frame
  and never the frame before. "After" keeps the residual a small positive number and guarantees
  the trim contains the whole lap; "before" silently drops the lap's last frame.
- Offsets of a whole frame or more are rejected; the user steps a frame instead.
- `syncOffsetMs` keeps its read path for legacy laps but is never written by the new flow.