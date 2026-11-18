# Demo video shot list

26 seconds at 1920 by 1080, and the same cut at 1080 by 1920. Four beats, no end card. The compare shot is the last frame.

The two laps are Joey's Suzuka laps from Daily Race C W47. The primary is the slower lap, 1:36.131. The reference is 1:34.208, from YT Sammynake. Both have clips. The console is connected during beat 01, but no lap is captured.

## 00 Hero, 0 to 2

App icon over the near-black backdrop, the `LapCraft` wordmark, and `Find the tenth.`, fading in and out.

## 01 Capture, 2 to 7

Record screen with a live stream: `Streaming`, `packets/s`, `GT7 lap`, speed, and the Capture card. The take is archived at `tools/demo/legacy/capture-01.mp4` because the live session no longer exists; `conform.sh` uses it when `beats/01.mkv` is absent.

Caption `Capture telemetry from every hot lap.` runs from 2.3 to 6.0. Touches off.

## 02 Replay, 7 to 11

Open the primary lap and play at 2x from the start line for four seconds.

Caption `Replay your laps on your phone.` runs from 7.3 to 11.0. Touches off.

## 03 Analyze, 11 to 17

Pause on the corner and drag the ruler back through it twice. Avoid the figure-8 crossing: `TrackProgress.align` searches 10 m behind to 30 m ahead, and its full path search can pick the wrong branch at the crossing.

Caption `Analyze each lap with telemetry on the replay.` runs from 11.3 to 16.5. Touches off.

## 04 Compare, 17 to 26

Open compare from the player's `Compare` pill and pick 1:34.208 as the reference. Jump to the first brake point, play four seconds through the corner, then switch to the Telemetry tab for the map and the five charts.

Caption `Compare your laps on one clock.` runs from 17.3 to 24.0. Touches on. The last two seconds have no caption.

## Before the shoot

- Settings console address matches the PS5. A mismatch shows a warning banner over the cards.
- GT7 is on track so the Record screen reads `Streaming` with a speed, otherwise beat 01 stops with an error.
- Both laps have a clip, or the reference pane shows an `Add clip` prompt.
- Stable channel build so the Developer card is absent from Settings.
- Do not complete a lap while the Record screen is live.

## Verification

Watch each take before conforming. Check that the touch dot is visible in beat 04 and absent in beats 01 and 02, that the replay clock is moving in beats 02 and 04, and that the delta is readable in beat 04 with no toast on screen. Watch the master muted at arm's length: if the captions are hard to read, raise the font size in `conform.sh` rather than moving them over the app.
