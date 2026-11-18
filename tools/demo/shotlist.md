# Demo video shot list

30 seconds at 1920 by 1080. Four beats, then a two second end card.

The two laps are Joey's Suzuka laps from Daily Race C W47. The primary is the slower lap, 1:36.131. The reference is 1:35.842. Both have clips. The console is connected during beat 01, but no lap is captured.

## 01 Capture, 0 to 7

| Time | Shot |
|---|---|
| 0 to 1.5 | Library with the W47 header and the laps. |
| 1.5 to 4 | Record screen. `packets/s`, `GT7 lap`, speed, stream summary, Capture card. |
| 4 to 7 | Back to the Library. |

Caption `Capture telemetry from every hot lap.` runs from 0.3 to 4.0. Touches off.

## 02 Replay, 7 to 11

Open the primary lap, play for two seconds, then pause on the HUD over the replay.

Caption `Replay your laps on your phone.` runs from 7.3 to 11.0. Touches off.

## 03 Analyze, 11 to 18

Tap Next brake point to jump to the corner, then drag the ruler back through it twice. Avoid the figure-8 crossing: `TrackProgress.align` searches 10 m behind to 30 m ahead, and its full path search can pick the wrong branch at the crossing.

Caption `Analyze each lap with telemetry on the replay.` runs from 11.3 to 18.0. Touches on.

## 04 Compare, 18 to 28

Open compare from the player's `Compare` pill and pick 1:35.842 as the reference. Then tap Next brake point to reach the first corner and use the ruler to move through it. No playback in this beat.

Caption `Compare your laps on one clock.` runs from 18.3 to 26.0. Touches on.

## Before the shoot

- Settings console address matches the PS5. A mismatch shows a warning banner over the cards.
- Both laps have a clip, or the reference pane shows an `Add clip` prompt.
- Stable channel build so the Developer card is absent from Settings.
- Do not complete a lap while the Record screen is live.

## Verification

Watch each take before conforming. Check that the touch dot is visible in beats 03 and 04 and absent in 01 and 02. Check that the delta is readable in beat 04 and that no toast is on screen. Watch the master muted at arm's length: if the captions are hard to read, raise the font size in `conform.sh` rather than moving them over the app.
