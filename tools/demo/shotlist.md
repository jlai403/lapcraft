# Demo video shot list

Four pillars, nine seconds each, then a four second end card. The master is 1920 by 1080.

The two laps are Joey's Suzuka laps tagged W47. Both have a replay clip. The console is connected in pillar 1, but no lap is captured during the take.

## 01 Capture, 0 to 9

The console stays in GT7 on Suzuka. Cross the start line and park past turn 1 so the GT7 lap chip reads 1 and the Capture card reads `Saves this lap when you cross the line again`. Do not complete a lap during the take.

| Time | Shot |
|---|---|
| 0 to 4.5 | Record screen. `packets/s`, `GT7 lap`, speed, stream summary, Capture card. Keep details collapsed. |
| 4.5 to 9 | Library. The W47 event header with `Best`, then the two rows. |

Caption `Capture telemetry from every hot lap.` runs from 0.5 to 5.0. Touches off.

## 02 Replay, 9 to 18

| Time | Shot |
|---|---|
| 9 to 13.5 | Lap player at 1x with the HUD over the replay. Starts paused, then Play. |
| 13.5 to 18 | Sync screen after the clip is picked. The lap start on the line and the offset field visible. |

Caption `Replay your laps on your phone.` runs from 9.3 to 14.0. Touches off. The document picker is the one manual step: `capture.sh --beat 02b` waits for Enter before it records.

## 03 Analyze, 18 to 27

Open the lap, pause on the hairpin or 130R, then drag the ruler back twice. Avoid the figure-8 crossing: `TrackProgress.align` searches 10 m behind to 30 m ahead, and its full path search can pick the wrong branch at the crossing.

Caption `Analyze each lap with telemetry on the replay.` runs from 18.3 to 23.0. Touches on.

## 04 Compare, 27 to 36

The screen rotates itself to landscape. Enter compare from the player's `Compare` pill and pick the other lap.

| Time | Action |
|---|---|
| 27.4 | Tap Next brake point. Both panes land on the turn 1 braking point. |
| 28.0 | Tap Play. One clock drives both panes. |
| 31.8 | Tap Pause. |
| 32.0 | Drag the ruler left about 200 px over 800 ms to rewind. |
| 34.4 | Hold the rewound frame with no caption on screen. |

Pick the pair with the larger delta. If the gap is under about 0.3 s, use the ±10 s chart window in Settings so the traces separate.

Caption `Compare your laps on one clock.` runs from 27.3 to 32.0. Touches on.

## Before the shoot

- Settings console address matches the PS5. A mismatch shows a warning banner over the cards.
- Both laps have a clip, or the reference pane shows an `Add clip` prompt.
- Stable channel build so the Developer card is absent from Settings.
- No full lap completed during pillar 1, or the library gains a third row.

## Verification

Watch each take before conforming. Check that the touch dot is visible in beats 03 and 04 and absent in 01 and 02. Check that the delta is readable in beat 04 and that no toast is on screen. Watch the master muted at arm's length: if the captions are hard to read, raise the font size in `conform.sh` rather than moving the captions over the app.
