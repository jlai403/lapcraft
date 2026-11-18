# Demo video kit

Builds a 24 second showcase of LapCraft from scripted takes. The app itself is not modified.

The four pillars are capture, replay, analyze, and compare. See `shotlist.md` for the shot card of each beat. The captions in `captions.srt` are the source of truth. `conform.sh` slices them per beat into an SRT and a styled ASS, then burns the ASS into the master. The ASS pins 1920x1080 coordinates, Geist, and the left gutter. The same SRT can be uploaded as YouTube captions.

## Requirements

The repo pins scrcpy and ffmpeg in `mise.toml`. Run `mise install` on the machine that will build the master. adb comes from the OS package, since the mise Android SDK plugin installs `sdkmanager` but not `platform-tools`.

```sh
# macOS
brew install android-platform-tools
# Debian or Ubuntu
sudo apt install android-tools-adb
# Arch
sudo pacman -S android-tools
```

Python 3 is the only other requirement. The helper uses the standard library.

Run `mise run demo:setup` after `mise install`. The ubi backend installs the scrcpy binary but not `scrcpy-server`, which scrcpy pushes to the phone at the start of every take. The task downloads the matching server file.

A phone with adb access is needed for the takes. `capture.sh` runs scrcpy with `--no-window` and sets `SDL_VIDEODRIVER=dummy`, so the recording machine needs no display. Preflight wakes the phone, dismisses a swipe-only keyguard, and launches Lapcraft if it is not already in front. A PIN or biometric lock still has to be dismissed beforehand. The console connection must be made on the Record screen before the run; launching the app does not restore it. `capture.sh` also exports `ADB` so a mise-installed scrcpy can find the system adb.

For wireless adb, `adb mdns services` prints the current serial, such as `adb-55231FDCR008EW-AAt5uJ._adb-tls-connect._tcp`. That name stays stable across port changes, so it is a good value for `--serial`.

The development container has no USB or network path to the phone, so no take can be recorded here. Both scripts have a `--dry-run` mode that prints every command without touching a device. Run the scripts through `mise exec --` unless your shell has `mise activate`, because they call `adb` and `scrcpy` from `PATH`.

For a TCP/IP connection, enable Wireless debugging in Developer options, then pair and connect from the recording machine:

```sh
adb pair <phone-ip>:<pair-port>
adb connect <phone-ip>:5555
adb devices
```

On Android 10 or older, run `adb tcpip 5555` over USB first, then `adb connect`. Pass the connected address with `--serial`, for example `mise exec -- ./tools/demo/capture.sh --beat 01 --serial 192.168.1.50:5555`. Use it whenever more than one device appears in `adb devices`, so scrcpy and the UI helper target the same phone.

The two Suzuka laps tagged W47 must already be in the app, and both must have a replay clip. The console is connected for beat 01, but no lap is captured during the take.

## Record the beats

```sh
mise install
mise run demo:setup
mise exec -- adb devices
mise exec -- ./tools/demo/capture.sh --beat all --dry-run
mise exec -- ./tools/demo/capture.sh --beat all
```

`--dry-run` prints the UI lookups and gestures instead of running them. `--touches on|off` overrides the default, which is on for beats 03 and 04 and off for the rest. `--lap` and `--reference` select laps by their exact on-screen times. The defaults are `1:36.131` for the primary and `1:35.842` for the reference. Change them if those laps are replaced.

Taps are found through `uiautomator dump`, matching either UI text or a content description such as `Frame ruler` and `Next brake point`. Coordinates are read from the node bounds, so one script works on a phone and on an emulator at any density.

There is no manual picker step. Both laps already have clips. Beat 01 switches Library to Record and back. Beat 03 jumps to a brake point, plays for two seconds, then rewinds with the ruler. Beat 04 opens compare, picks the reference lap, jumps to a brake point, and scrubs through the corner.

Record two or three takes per beat. Injected input has about 100 to 300 ms of jitter per command, so the pause and rewind will land a few frames off between runs. Keep the take where the rewind reads clearly.

Each take is saved to `tools/demo/beats/<beat>.mkv` with about 0.6 s of pre-roll. `conform.sh` trims that pre-roll.

## Build the master

```sh
mise exec -- ./tools/demo/conform.sh --dry-run
mise exec -- ./tools/demo/conform.sh
mise exec -- ./tools/demo/conform.sh --short --dry-run
mise exec -- ./tools/demo/conform.sh --short
mise exec -- ./tools/demo/conform.sh --music tools/demo/music.wav
```

Each beat is fitted to a dark blurred 1920 by 1080 backdrop with a phone shadow, hairline border, and a mint progress bar along the bottom. Portrait beats use a 1000 px tall device and 46 px Geist Medium captions in the left gutter. Beat 04 uses a 1200 px wide device and 32 px captions. Chapter labels are Geist Mono in the top-left. There is no end card; the master ends on the compare shot.

The audio track is silent unless `--music` is given. Speech and music are added in the edit, never recorded from the device.

Intermediates are kept in `tools/demo/.build/` for debugging. `master.mp4` and `master-15s.mp4` are written to this directory.

## Checks before publishing

- Watch the master muted on a phone at arm's length. The captions must be readable with the picture behind them.
- Confirm the caption on the compare beat disappears before the rewind starts.
- Confirm no warning banner, toast, keyboard, or Developer card appears in any take.
- Check the delta in beat 04 against the two lap times. It must read the same way the app reports it.
