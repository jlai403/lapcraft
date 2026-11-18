# Demo video kit

Builds a 40 second showcase of LapCraft from scripted takes. The app itself is not modified.

The four pillars are capture, replay, analyze, and compare. See `shotlist.md` for the shot card of each beat. The captions in `captions.srt` are the source of truth. `conform.sh` slices them per beat and burns them into the master, and the same file can be uploaded as YouTube captions.

## Requirements

A phone with adb access, scrcpy, ffmpeg, and Python 3. None of these are installed in the development container, so no take can be recorded here. The scripts have a `--dry-run` mode that prints every command without touching a device.

The two Suzuka laps tagged W47 must already be in the app, and both must have a replay clip. The console is connected for beat 01, but no lap is captured during the take.

## Record the beats

```sh
adb devices
./tools/demo/capture.sh --beat 01
./tools/demo/capture.sh --beat 02
./tools/demo/capture.sh --beat 02b
./tools/demo/capture.sh --beat 03
./tools/demo/capture.sh --beat 04
./tools/demo/capture.sh --beat all --dry-run
```

`--dry-run` prints the UI lookups and gestures instead of running them. `--touches on|off` overrides the default, which is on for beats 03 and 04 and off for the rest. `--reference` sets the lap time text used to pick the reference in beat 04; the default `1:` chooses the first candidate.

Taps are found through `uiautomator dump`, matching either UI text or a content description such as `Frame ruler` and `Next brake point`. Coordinates are read from the node bounds, so one script works on a phone and on an emulator at any density.

Beat 02b is the one manual step. Open the lap, tap `Add clip`, pick the clip, stop on the sync screen, and press Enter. The system document picker is not our UI and its layout varies by device.

Record two or three takes per beat. Injected input has about 100 to 300 ms of jitter per command, so the pause and rewind in beat 04 will land a few frames off between runs. Keep the take where the rewind starts just after the caption leaves at 32.0.

Each take is saved to `tools/demo/beats/<beat>.mkv` with about 0.6 s of pre-roll. `conform.sh` trims that pre-roll.

## Build the master

```sh
./tools/demo/conform.sh --dry-run
./tools/demo/conform.sh
./tools/demo/conform.sh --short --dry-run
./tools/demo/conform.sh --short
./tools/demo/conform.sh --music tools/demo/music.wav
```

Each beat is fitted to a blurred 1920 by 1080 backdrop and padded to a constant frame rate, so the cuts need no transition. Portrait beats use a 1000 px tall device and 44 px captions in the left gutter. Beat 04 uses a 1200 px wide device and 30 px captions. A generated end card closes the master.

The audio track is silent unless `--music` is given. Speech and music are added in the edit, never recorded from the device.

Intermediates are kept in `tools/demo/.build/` for debugging. `master.mp4` and `master-15s.mp4` are written to this directory.

## Checks before publishing

- Watch the master muted on a phone at arm's length. The captions must be readable with the picture behind them.
- Confirm the caption on the compare beat disappears before the rewind starts.
- Confirm no warning banner, toast, keyboard, or Developer card appears in any take.
- Check the delta in beat 04 against the two lap times. It must read the same way the app reports it.
