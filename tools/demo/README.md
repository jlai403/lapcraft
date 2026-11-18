# Demo video kit

Builds a 26 second showcase of LapCraft from scripted takes. The app itself is not modified.

The four pillars are capture, replay, analyze, and compare. See `shotlist.md` for the shot card of each beat. The captions in `captions.srt` are the source of truth. `conform.sh` slices them per beat into an SRT and a styled ASS, then burns the ASS into the master. The ASS pins canvas coordinates, Geist, and the left gutter. The same SRT can be uploaded as YouTube captions.

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

The two Suzuka laps tagged W47 must already be in the app, and both must have a replay clip. The primary is `1:36.131` and the reference is `1:34.208`, both from Daily Race C W47.

Beat 01 needs a live stream: the script waits for the `Streaming` label on the Record screen and stops with an error when GT7 is not sending packets. Start a race or time trial on the PS5 first, or record only the playback beats with `--beat '02 04'`.

The last live capture take is archived at `tools/demo/legacy/capture-01.mp4`, cropped from the device panel of the earlier master. `conform.sh` uses it whenever `beats/01.mkv` is missing, so a fresh clone can rebuild both cuts without a console. A new take replaces it once `capture.sh --beat 01` succeeds.

## Record the beats

```sh
mise install
mise run demo:setup
mise exec -- adb devices
mise exec -- ./tools/demo/capture.sh --beat all --dry-run
mise exec -- ./tools/demo/capture.sh --beat all
```

`--dry-run` prints the UI lookups and gestures instead of running them. `--touches on|off` overrides the default, which is on for beat 04 and off for the rest. `--lap` and `--reference` select laps by their exact on-screen times. The defaults are `1:36.131` for the primary and `1:34.208` for the reference. Change them if those laps are replaced.

Taps are found through `uiautomator dump`, matching either UI text or a content description such as `Frame ruler` and `Next brake point`. Coordinates are read from the node bounds, so one script works on a phone and on an emulator at any density. Every dump deletes the previous file first and is rejected when uiautomator reports an error, because a stale tree is what makes a scripted tap look like it missed.

Playback is confirmed by the picture changing, not by the play/pause label: two screenshots are hashed 0.6 s apart, and Play is tapped again only when they match. Tapping on the label alone led to double taps that paused the replay a moment after it started.

The compare screen is landscape only, so beat 04 pins the display with `user_rotation 1` before opening the reference and restores portrait afterwards. On a portrait-locked display the app is letterboxed into a scaled window and every landscape tap lands somewhere else. The Telemetry tab is also visited once before recording, since a cold switch takes about two seconds to lay out the map and the charts.

There is no manual picker step. Both laps already have clips. Beat 01 sits on the Record screen with a live stream. Beat 02 plays the primary lap at 2x from the start line for four seconds, pauses, then steps back through the corner with the ruler. Beat 04 picks the reference, jumps to the first brake point, plays for four seconds and switches to the Telemetry tab.

Record two or three takes per beat. Injected input has about 100 to 300 ms of jitter per command, so the pause and the tab switch land a few frames off between runs. Keep the take where the rewind and the switch read clearly.

Each take is saved to `tools/demo/beats/<beat>.mkv`. `conform.sh` trims the leading second of scrcpy start-up.

## Build the master

```sh
mise exec -- ./tools/demo/conform.sh --layout wide --dry-run
mise exec -- ./tools/demo/conform.sh --layout wide
mise exec -- ./tools/demo/conform.sh --layout tall
mise exec -- ./tools/demo/conform.sh --layout wide --music tools/demo/music.wav
```

`--layout wide` writes `master.mp4` at 1920 by 1080 for YouTube and the website. `--layout tall` writes `master-vertical.mp4` at 1080 by 1920 for the Play Store. Each beat is fitted to a dark blurred backdrop with a phone shadow, hairline border, and a mint progress bar along the bottom. Portrait beats use a device about 1000 px tall in the wide layout and 1400 px in the tall one, with Geist Medium captions in the side gutter on wide and under the device on tall. Beat 04 uses a landscape stage with its caption underneath the device. Chapter labels are Geist Mono in the top-left. There is no end card; the master ends on the compare shot.

The compare segment is trimmed automatically: `conform.sh` finds the frame where beat 04 switches to the Telemetry tab and starts four seconds earlier, so the cut always shows four seconds of replay and five of telemetry. Beat 02 and its analysis share one take, cut at `TURN1_TRIM`.

The audio track is silent unless `--music` is given. Speech and music are added in the edit, never recorded from the device.

Intermediates are kept in `tools/demo/.build/` for debugging.

## Checks before publishing

- Watch the master muted on a phone at arm's length. The captions must be readable with the picture behind them.
- Confirm the caption on the compare beat disappears before the rewind starts.
- Confirm no warning banner, toast, keyboard, or Developer card appears in any take.
- Check the delta in beat 04 against the two lap times. It must read the same way the app reports it.
