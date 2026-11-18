# Design

LapCraft uses a dark, high-contrast sports-app look: bold headlines, dense data cards, pill controls, and a persistent bottom tab bar. The reference points are sports score and fantasy apps — dark surfaces, vivid accent colors, and glanceable numbers — without copying any specific brand.

This document is the source of truth for visual tokens and recurring UI patterns. Change the tokens in `Theme.kt` first, then the screens.

## Principles

1. **Data first.** Lap times, speed, and state are the loudest things on screen.
2. **One accent per meaning.** Green is primary/active, blue is speed, amber is RPM and secondary data, red is error or brake.
3. **Dark only for v0.1.** No light theme until the data-viz palette is validated on track.
4. **Big targets, short labels.** Primary actions are 50 dp pills; secondary actions are 44 dp.
5. **Cards, not dividers.** Group related content into 20–22 dp rounded surfaces rather than dense tables.
6. **Calm motion.** Material defaults only. Library rows do not animate item placement: the animated shift moved a row while the test was clicking it, so the rows reflow instantly.

## Color tokens

All tokens live in `ui/Theme.kt`. Screens must use `MaterialTheme.colorScheme.*`; do not hardcode hex values outside the theme and chart palette.

| Token | Hex | Use |
|---|---|---|
| `primary` | `#34D399` | active tab, primary buttons, best lap, live status, throttle |
| `onPrimary` | `#04130D` | text/icons on mint |
| `primaryContainer` | `#0B3B2C` | reserved for tonal fills |
| `secondary` | `#38BDF8` | speed values and speed trace, tires |
| `onSecondary` | `#04131C` | text/icons on cyan |
| `tertiary` | `#FBBF24` | RPM, gear, tertiary stat chips |
| `onTertiary` | `#241800` | text/icons on amber |
| `background` | `#0D1114` | app background |
| `surface` | `#12181C` | cards, bottom bar |
| `surfaceVariant` | `#1A2226` | chips, segmented tracks, back button, inputs |
| `onSurface` | `#F8FAFC` | primary text |
| `onSurfaceVariant` | `#94A3B8` | secondary text, icons, map line |
| `outline` | `#2B3540` | inactive dots, borders |
| `Hairline` | `#2C3944` | library row dividers |
| `RowPressed` | `#12191E` | pressed lap row |
| `outlineVariant` | `#1E262B` | dividers, chart axis |
| `error` | `#F43F5E` | errors, brake trace, destructive actions |
| `errorContainer` | `#3B0E1B` | warning banners |
| `onErrorContainer` | `#FFD7E0` | warning text |
| `GhostSlate` | `#94A3B8` | muted text (secondary text, icons, map line) |
| `AccentAmber` | `#FBBF24` | reference lap in comparison charts and map, gear trace |
| `CrispWhite` | `#F8FAFC` | car body, map marker dot, highlights |
| `onBackground` | `#F4F7FA` | text over background |

System bars are transparent with light icons (`enableEdgeToEdge` + `SystemBarStyle.dark`).

### App icon and system chrome

The launcher icon is an illustrated mascot on an orange disc. It stays on the launcher: the palette does not adopt its orange, and no mark or wordmark appears inside the app.

- Adaptive icon: a full-bleed foreground over `#0D1114`, plus a vector monochrome layer (`drawable/ic_launcher_monochrome`) for Android 13 themed icons.
- Status bar: `drawable/ic_stat_lapcraft`, a 24 dp white trace, which the telemetry notification sets as its small icon.
- `Theme.LapCraft` paints the launch window and the splash background `#0D1114`. The API 31 splash draws `drawable/splash_icon`, generated at 288 dp for every density from `docs/branding/launcher-icon-master.jpg`, so it is not upscaled from the 108 dp adaptive icon.

## Typography

Two bundled OFL families, both from [Geist](https://github.com/vercel/geist-font) v1.9.2:

| Family | Weights | Use |
|---|---|---|
| **Geist Sans** | Regular, Medium, SemiBold, Bold, Black | all interface text: titles, body, labels, buttons, tabs |
| **Geist Mono** | Regular, Medium, SemiBold, Bold | numeric values only: lap times, speed, gear, RPM, packets/s, sync offsets |

Rules:

- UI copy is Geist Sans. Never set body paragraphs in mono.
- Standalone numbers use `NumericText` (Components.kt), which applies `GeistMonoFamily`.
- Mono values are fixed-width, so digits do not shift as speed, RPM, or the cursor changes.
- Font files live in `res/font/`; the license is `licenses/Geist-OFL.txt`.

| Style | Family | Size | Weight | Use |
|---|---|---:|---|---|
| `displaySmall` | Sans | 34 sp | Black, -0.6 tracking | lap time hero on a library card, set in mono by `NumericText` |
| `headlineMedium` | Sans | 25 sp | Black, -0.3 | screen titles, `ScreenHeader` |
| `titleLarge` | Sans | 20 sp | Bold | card titles, section numbers |
| `titleMedium` | Sans | 16 sp | SemiBold | row titles, supporting emphasis |
| `bodyLarge` | Sans | 16 sp | Normal | long copy |
| `bodyMedium` | Sans | 14 sp | Normal | standard copy, dates |
| `bodySmall` | Sans | 12 sp | Normal | metadata, subtitles, hints |
| `labelLarge` | Sans | 14 sp | SemiBold | buttons, tabs |
| `labelMedium` | Sans | 12 sp | SemiBold, 0.4 tracking | uppercase section labels, chips |
| `labelSmall` | Sans | 11 sp | Medium, 0.5 tracking | compact labels, legends, ruler ticks |
| `NumericText` | Mono | inherits | inherits | lap times, stat values, cursor, telemetry row |

Line heights are set in `Theme.kt` to Geist metrics (for example 25/30 for `headlineMedium`, 20/26 for `titleLarge`). Adjust type sizes and line heights together.

Rules:

- Screen titles are `headlineMedium`, one line, ellipsized.
- Numeric emphasis uses `titleLarge` or `titleMedium` plus an accent color.
- Section labels are uppercase `labelMedium` in `onSurfaceVariant`; helper `SectionLabel` does this.
- Body copy is sentence case. No exclamation marks.

## Shape and spacing

| Token | Radius | Use |
|---|---:|---|
| `extraSmall` | 8 dp | small inline blocks |
| `small` | 12 dp | inputs, toggle rows |
| `medium` | 16 dp | chips, stat blocks |
| `large` | 22 dp | cards, video frame |
| `extraLarge` | 28 dp | reserved |

Layout:

- Screen padding: **20 dp horizontal, 8 dp vertical**.
- Section gap: **14 dp** (Library, Record, Settings, Session); **12 dp** inside the player.
- Card padding: **16 dp**; library lap rows use **14 dp vertical and 20 dp horizontal**, with no card surface.
- Row gap: **8–10 dp**; chip padding **14 × 12 dp**.
- Primary pill: **50 dp** tall, 24 dp horizontal padding, `CircleShape`.
- Secondary/ghost pill: **44 dp** tall, 18 dp horizontal padding.
- Back button: **40 dp** circle on `surfaceVariant`.

Containers:

- Root is `Surface(background)` → `windowInsetsPadding(WindowInsets.safeDrawing)` → `Column`.
- Top-level screens get the bottom tab bar; detail screens do not.
- The tab bar uses `NavigationBar` with zero window insets, because the root already handles safe areas. Never add per-screen status-bar padding.
- Lists use `LazyColumn` with 14 dp gaps (Library) or 10 dp (laps).

## Components

Defined in `ui/Components.kt` unless noted.

| Component | Contract |
|---|---|
| `ScreenHeader` | optional 40 dp back button, one-line title, optional subtitle, trailing action row |
| `PrimaryPill` | one main action per screen; green fill, dark text, 50 dp |
| `GhostPill` | secondary actions; outlined, 44 dp; pass `contentColor = error` for destructive |
| `StatChip` | compact metric block: accent value over muted label, 16 dp radius |
| `SegmentedChoice` | equal-width pill segments inside a `CircleShape` track; selected segment is green |
| `SectionCard` | 22 dp surface card with 16 dp padding, an optional uppercase title, and an optional trailing action |
| `SectionLabel` | uppercase muted label for groups inside a card |
| `EditRow` | form row: uppercase label, a one-line ellipsised value, and actions flush to the right edge; every field in the edit sheet uses it |
| `StatusDot` | 10 dp circle: green when live, outline when idle |
| `FrameRuler` | 64 dp surface strip with a fixed green needle; frame ticks, half-second labels, and horizontal drag at 6 dp per frame. A tap does nothing; a drag past the touch slop steps frames from the playhead it started on. The strip follows the finger, so drag right to rewind and left to move forward. The drag reports total frames from the playhead it started on; the ruler and time readout jump to the requested frame at once while seeks are coalesced to one in flight, and one haptic tick fires per step unless disabled |
| `LapCard` | flat feed row, no radius or surface box: leading 32 dp flag column, name and short date, track, hero lap time, car and replay state; tap opens, swipe edits or deletes, long press and custom actions repeat both |
| Swipe actions | full-height action background under the card, Gmail-style; horizontal drag clamped to 60% of the card width; Edit pinned left on a right swipe, Delete pinned right on a left swipe; below the 35% threshold the card springs back to zero, and it always returns to the default state; never dismisses the card |
| `PullToRefreshBox` | library list gesture; the spinner follows the refresh state |
| `DriverPickerRow` | flag, nickname, PSN with `DR/SR · updated` when cached, Change menu; pass `onOpenLink` to add GT GridStats and GTSH-Rank, `onRemoveProfile` for a per-driver `×` |
| `EditLapSheet` | modal bottom sheet for track, event, driver, replay and data, built from `EditRow`; edits commit immediately and the footer is `Done` |
| `ReplaySourcePill` | one pill that opens Files or the PlayStation App |
| `Replay source menu` | pill with a trailing chevron; dropdown rows for Files and PlayStation App, each with a one-line subtitle |
| `AppBottomBar` (App.kt) | Library / Record / Settings tabs, green indicator, no elevation |

Dialogs use Material 3 `AlertDialog`. Destructive confirmation labels are red; cancel stays neutral.

## Screens

### Library

- Header: “Library”, subtitle `N laps · M tracks · Best M:SS.mmm` (hidden when the library is empty or previewing), owner chip (`🇬🇧 Nickname ▾`) opening GT GridStats, GTSH-Rank, and Edit profile. A profile is optional; without one the chip reads `Add GT profile`.
- Toolbar: an equal-width kind tab row — `All · Daily · Time Trial · World Series` — that shrinks the tab font (14 → 11 sp) until the longest label fits and falls back to a scrollable row at large font scales — selecting `EventFilter.Kind` (All resets it), then `All tracks ▾`, the event pill (All events / Untagged / cached events of the selected kind), the sort pill, and `Clear` while a filter is active. The toolbar hides when the library is empty or previewing. An empty kind tab explains itself: with event tags off it points at Settings, otherwise it suggests tagging laps via Edit lap, and both offer `Show all laps`.
- Pull to refresh replaces the refresh button. The spinner follows the gesture and the manual reload only, not the automatic reload after an edit.
- Event sections: the feed is grouped by event tag. Each current event opens with a header — the event label (`Daily Race A · W47`, `Time Trial · Suzuka`, `World Series · R3`), then `series · Nov 16 – Nov 23 · Gr.4 · Suzuka · 2 laps · Best 1:23.456`. The header is not interactive for current events; the laps follow. Laps with no tag captured this week collect under a `No event` header.
- Past events: lapsed events (and laps captured before the current ISO week, including untagged ones) collapse into one `Past events · N laps · M events` row below the current sections, where M counts only real events. Tapping expands it into each lapsed event's header and laps; tapping again collapses it. Everything else starts expanded.
- Card: flat feed row with no surface box or radius. Leading flag column, then the driver name and short date; the track; the 34 sp mono lap time with the `Fastest lap` label or a `+0.412` delta; the car with `Replay` or `Sync pending`. The whole row opens the lap. The event label is not repeated on the card; the section header owns it.
- Rows are separated by a 1 dp full-bleed `Hairline` (#33414F) divider; there is no divider after the last lap. Row backgrounds stay on `background`, so the feed is one flat surface.
- Fastest lap is computed per track across every lap, ignores the active filter, and only appears when the track has two or more laps. The other laps on that track show the delta.
- Every card shows the lap's driver in the hero row: flag and nickname, the PSN when the profile is unknown, or `Driver not set` in muted text.
- Swipe right opens Edit. Swipe left opens the delete confirmation, and the card springs back. Long press and the TalkBack custom actions offer the same two.
- Edit sheet: every field is an `EditRow` — a label, a value, and one or more pills flush to the right edge, matching the driver picker. Track shows the name plus `Rename` (unknown laps open straight in the field), the event row is a text field with cached-event suggestions and a `Refresh events` pill when the cache is empty, the driver row is the shared picker without profile links (each saved friend carries a `×` to remove them), the replay row holds `Add a replay` / `Replace replay` / `Resume sync`, and a data row shows `N MB total` with the replay/pending/telemetry breakdown. Edits commit as you make them; the footer is `Done`.
- Empty state: `No laps yet`, the Record instruction, and `Go to Record`. A filtered empty list adds `Clear filters`.
- Developer: Settings → Developer → `Developer tools` exposes maintenance actions such as `Re-encode clip` in the Edit lap sheet; `Preview empty library` renders the empty state over a banner with `Turn off` while the laps stay on disk. Preview channel only.

### Record

- Header: `Record`, subtitle `Connect to GT7, then capture laps.`
- The console address lives in Settings. The screen has two cards.
- `Stream` card: live dot, health line (`Streaming` / `Waiting for the first packet` / `Packets stopped` / `Packet rate is low`), a summary line (`59.8 packets/s · last packet 12 ms ago`), then three stat chips (packets/s, GT7 lap, km/h or mph from Settings), and the `Connect` / `Disconnect` pill in the card header. With no saved address the pill reads `Set address` and opens Settings. `Disconnect` is disabled while a lap is capturing. While disconnected, one hint names the next step (`Connect to start listening.` or `GT7 sends telemetry only while a race or time trial is on screen.`).
- `Show details` reveals the car name (via `CarCatalog`, falling back to the code), the track state, pedals, accepted packets and decode errors, received datagrams with the foreign count, the laps captured plus GT7's own counter, the last five events, and `Copy diagnostics`. Packet ID, flags hex and the last decode failure appear only in the copied text, which also carries version, build number and commit.
- Warnings render in one `WarningBanner` above the cards: the service error, and packets arriving from another address. The foreign-address banner is visible without expanding details.
- `Capture` card: `Capture this lap` is disabled until the stream is connected and the start line has been observed; the helper line states the next action. `Cancel` appears while capturing. A saved lap shows `Lap saved` with the mono lap time and `Capture another lap` / `View lap`.

### Player

- Header: `Lap N · M:SS.mmm` with a chevron. Collapsed shows only the title; tapping expands track, driver flag/name, car, lap stats (top/min/avg), and the vs-ghost comparison. A `Compare` pill opens a picker of other laps on the same track; it is disabled when there is nothing to compare. One overflow menu: `Share video`, `Share screenshot`, `Export CSV`, `Edit lap`.
- Video: 16:9, black, clipped to 22 dp; the stock Media3 controller is hidden. The lap player uses a `TextureView`-backed `PlayerView` so the telemetry HUD can draw over the video. A telemetry HUD overlays the top centre with the last 2 s of throttle/brake traces when enabled in Settings.
- Replay management lives in the `Edit lap` sheet: the lap time as the header, track, event, driver, add/replace replay, resume a pending sync, and the lap's data volume (replay + telemetry). The main screen keeps only `Add replay` when no clip exists. With Developer tools on, the sheet adds `Re-encode clip`, which rebuilds the clip with a 0.1 s keyframe interval for frame-exact scrubbing and deletes the old clip, its temporaries and any scrub cache. The sheet stays open while the transcode runs, showing a spinner and `Re-encoding…`, then `Re-encoded with 0.1 s keyframes.` (or a failure line, with the clip unchanged).
- New imports are trimmed and then compacted with the Settings replay profile (default 1080p HEVC, ~5 Mbps, 0.1 s keyframes, stereo; ~35% larger than 0.5 s keyframes). If compaction fails the trimmed clip is kept and a `Compaction failed` toast appears.
- The sync screen scrolls as one column — clip, lap start, offset, replay quality — and ends with `Discard` / `Save clip` under a hairline, so the save action is never pinned over the content.
- Missing video: no video pane; the screen opens on the telemetry card and the track-map card, then a `Replay` card (`No replay attached`, a one-sentence explanation, and the source pill). The footer stays: play, ruler scrub, brake jumps, speed and loop run off a synthetic lap clock (the cursor lerps between 60 Hz samples), and the `−1`/`+1` frame steps are hidden because there are no frames.
- Playback footer (sticky): a labelled `clock / end` readout in mono (e.g. `Lap 0:12.431 / 1:30.000`) with the Loop toggle and the `− / +` speed stepper on the right, the frame ruler, then one centred transport row: `−1` frame, skip to the previous brake point, play/pause, skip to the next brake point, `+1` frame. Frame steps repeat while held. The ruler is the only scrubber and shows a progress track, event dots, and pinch zoom. A tap does nothing; drags and the release seek the exact frame, with at most one seek in flight. The readout and telemetry follow the requested frame immediately. On the sync screen the footer gains a second `Lap` row naming the lap time to sync against, and its clock reads `Clip`.
- Scrolling content above the footer: with a clip — video, telemetry card with trace toggles, track-map card; without one — telemetry, track map, then the `Replay` card. The rendered frame (or the synthetic clock) drives the charts and map, including while paused.
- Speed changes last until you leave the lap screen; the default comes from Settings.

### Compare

- Landscape only. Entering the screen requests landscape; leaving restores the previous orientation. Portrait hides the content behind a `Rotate to landscape` prompt and pauses playback.
- Chrome: one 44 dp row, no title — back button left, `Video` / `Telemetry` tabs centre, `Δ ±0.342 s` right (green ahead, red behind). Primary = the lap that was open or picked first.
- **Video tab:** two panes side by side, black, clipped to 18 dp, filling their 16:9 boxes (445 × 250 dp on a Pixel 10). The panes carry no text: GT7's own HUD is recorded into the frame, so the only overlay is the 2 s throttle/brake HUD at the top centre when `Telemetry HUD` is on, and the 30 dp readout strip sits underneath: `THR`/`BRK`/`RPM` mini bars, a 13 dp 2×2 tyre heatmap (corner letters in the content descriptions), and a 26 dp steering wheel. Each pane's accessibility label names the lap, its lap time, and the cursor time. A pane without a replay shows `No clip yet` and an `Add clip` pill.
- **Telemetry tab:** no readout strip. The track map sits in a fixed 320 dp pane (capped at 42 % of the width), clipped to its bounds, with lap 1's marker pinned to the pane centre (follow mode, pedal-coloured primary, solid slate reference, both cursors, horizontal legend); both driving lines are drawn at 1.5 dp. The map supports pinch zoom and two-finger pan (0.6×–8×) with a double-tap reset and opens at maximum zoom in follow mode. Five charts take the rest: each with the primary in cyan and the reference solid slate, a shared ±N s time axis centred on the cursor (clamped at the lap ends) and one cursor line. The `Speed`/`Gear` row sits above the charts, showing lap 1 / reference values plus a signed delta (speed delta mint when lap 1 is faster, red when slower; gear delta mint in a higher gear, red in a lower one, muted when equal). All five charts are always visible (no scroll); the legend sits above them. The window is configurable in Settings (±2/±3/±5/±10 s, default ±5).
- Lap 1 owns the clock. Lap 2 is muted, never plays, and is seeked frame-exactly to the primary's **place on track** on every rendered frame.
- Place alignment: the reference's samples are projected onto lap 1's driven path, resampled every 2.5 m, searching only 10 m behind to 30 m ahead of the previous answer so a track that crosses itself cannot capture the wrong branch. The axis never runs backwards. Video seek, chart traces, map dots and `Δ` all read that one axis, so a corner is compared with the same corner even when the two lines differ; when the path cannot be built the screen falls back to percent of each lap's own distance.
- Footer: one 48 dp row — `−1 ⏮ ▶ ⏭ +1`, a compact frame ruler, a `1×` button that cycles the rate, and a loop icon. Switching tabs never pauses or seeks.

### Settings

- Header: “Settings”, subtitle “Console, profile, units, and updates”.
- Console card: IP field.
- Driver profile card: owner PSN with `Fetch profile` / `Refresh profile` and `Remove`, the cached ratings with their fetch date (`DR (B) · SR (S) · updated Nov 3`), a `Saved drivers` list where every cached friend carries its own `Remove` (confirmed, laps keep the PSN), a `Refresh profiles (N)` action for missing or week-old ratings, and the optional GT GridStats API key (`Save key` / `Clear`).
- Replay compression card: `Preset` (Original / Balanced / Compact), and while not Original, `Resolution` (1080p/720p), `Codec` (HEVC/H.264), `Keyframes` (Frame 0.1 s / Fast 0.5 s / Standard 1 s / Sparse 2 s), and `Audio` (Stereo 96 kbps / Mono 64 kbps / Remove). Defaults: Balanced, 1080p, HEVC, Frame, Stereo. Each control shows a caption describing its effect; a non-default choice is prefixed `Changed:` and uses the primary colour. These are defaults; the import screen can override them per lap.
- Display card: `Imperial units`, `Frame ruler haptics`, and `Telemetry HUD` toggle rows, plus a `Default playback speed` dropdown (0.5×/1×/1.5×/2×); haptics are on by default, HUD is on by default.
- Updates card: channel-aware status line (`Checks the rolling debug build once a day.`, `You are on the latest build.`, `Version X (build N) is available.`), then one action: `Check for updates`, `Download X`, `Downloading N%`, `Install update`, or `Installing…`. Failures use `error` color and name the next action.
- Build card: version name, version code, update channel (`preview`/`stable`), CI build number (or `local`), and short commit SHA.
- When an update is found, a launch dialog offers `Download` / `Later`; Settings remains the place to retry or install.
- Toggle rows are fully clickable; the switch is display-only.

## Charts and track map

`ui/TelemetryCharts.kt`.

- Throttle/brake: green and red on one 0–255 chart (top).
- Speed: blue trace with gear overlaid as an amber step trace, each scaled to its own peak (second).
- RPM chart removed; RPM remains a value bar.
- X axis: a ±5 s window around the cursor, centred and clamped at the lap ends, labelled `±5 s`; time and progress interpolate between samples. Laps with no track positions still plot by lap time.
- A reference lap (when present) overlays the same traces as solid lines; maxima cover both laps.
- Compare charts (`CompareCharts`) use one chart per metric (`BRK`, `THR`, `SPD`, `RPM`, `GEAR`): primary solid cyan (`AccentCyan`), reference solid amber (`AccentAmber`), shared cursor.
- Axis: 1 px `outlineVariant`; cursor: 3 px `onSurface`.
- Trace stroke: 2 px. Chart heights: 52 dp.
- Map: coloured 1.5 px segments by pedal state, intensity lerped from muted grey to mint (throttle) or red (brake) by pedal strength; 12 px crisp-white dot at the cursor, 200 dp tall. A dashed grey ghost lap overlays the fastest lap on the same track; compare replaces it with a solid amber (`AccentAmber`) reference line drawn at 1.5 px and an open ring at its cursor.
- Turn markers: the ruler marks brake, throttle, apex, and exit events. The map colours segments by pedal strength but draws no turn-in dots.
- Map chips: `Follow` is on by default and centres on the car, puts the heading up, anchors the car near the bottom, squashes the vertical axis 0.35× for a driver's-perspective tilt, and zooms to the next ~2 s; `Ghost` toggles the reference lap; `Reset` returns to fit-all. The legend is a vertical list of brake, throttle, and ghost under the title, above the map.
- Downsample traces to roughly one point per horizontal pixel; map to about 400 points.
- Readout: three 44 dp bar rows (THR green, BRK red, RPM amber) with labels above and values right, no speed/gear text and no per-bar sparklines; the 2×2 tire heatmap (32 dp tiles, blue cold → green optimal → red hot) sits to the right, vertically centered, with a 40 dp F1 steering wheel under it that turns with the recorded wheel angle (laps recorded before steering was parsed show no wheel). Labels use `labelSmall`; values use Geist Mono.
- Values and the map dot interpolate between the two samples that bracket the cursor time, including tyre temperatures and fuel; gear snaps to the nearest sample. The cursor line stays on the video frame.

## Recording feedback outside the app

While Chiaki-Up is fullscreen the Record tab is not visible, and v0.1 has no overlay.

- Capture hygiene: Chiaki-Up's in-stream bar (On-Screen Controls, Touchpad only, display mode) and its touch layout are part of the captured display. The record steps tell the driver to swipe from the top edge, turn On-Screen Controls off, and let the bar fade before driving. The app cannot hide another app's controls, so this stays a documented setup step, not a feature.

- The telemetry notification is the pre-recording surface: `GT7 stream · 59 pps` or `GT7 stream · waiting`, with a `Disconnect` action.
- The recording notification is the live surface once armed: collapsed shows `Recording lap 3 · 59 pps · 0 err`; expanded (`BigTextStyle`) shows packet age, packet ID, car code, flags, lap, last lap, and the last event. Updates once per second, silent, `setOnlyAlertOnce`.
- Both notifications are `IMPORTANCE_LOW`; two persistent entries while recording is accepted for v0.1.
- Short text toasts are limited to transitions before the MP4 rolls: `GT7 stream OK · 60 pps` and `GT7 telemetry lost`. Rate-limited to one every 3 seconds.
- Nothing is drawn while recording: a toast is a window on the captured display, so it would appear in the lap video. Lap confirmation lives in the notification and the library. Errors during recording surface in the notification only.
- The notification is `IMPORTANCE_LOW` and silent, so it never heads-up over Chiaki-Up; it appears only when the driver pulls the shade.
- The Record card remains the full diagnostics view before arming and after stopping.

## Copy and tone

- Terse, factual, sentence case: “Arm recording”, “Your laps, ready to review”, “Video not cut yet”.
- Avoid jargon the driver does not need; say “packets/s” and “lap”, not decoder internals.
- Errors name the next action (“Could not open UDP 33740. Another app may be using it.”).

## Accessibility

- Minimum touch targets: 44 dp for ghost pills, 50 dp for primary, 40 dp for the icon-only back button.
- Every icon-only control needs a `contentDescription` (“Back” today).
- Do not rely on color alone: pair accents with labels or numbers.
- Keep text contrast at or above Material 3 defaults; muted text is for metadata only.
- The app is edge-to-edge; all content must stay inside `safeDrawing`.

## Implementation rules

- Tokens: add colors, type, and shapes in `Theme.kt`; screens consume `MaterialTheme`.
- Reuse `Components.kt` before creating a one-off card or button.
- New top-level destination: add a tab in `App.kt` and a `Screen` case; new detail screen: no tab, include a back action.
- Keep UI copy that tests assert on: “Library”, “No laps yet”, “Go to Record”, “Fastest lap”, “Driver not set”, “Edit lap”, “Open”, “Share MP4”, “Export CSV”, “Speed”, “Track map”, “Lap N · time”, “Video not cut yet”, “Keep raw recording”, “720p60”.
- Compose UI tests run on the JVM via Robolectric (`:app:testDebugUnitTest`), so visual changes should keep semantics stable.

## File map

| File | Purpose |
|---|---|
| `ui/Theme.kt` | color, type, shape tokens |
| `ui/Components.kt` | shared header, pills, chips, cards, segmented control |
| `ui/App.kt` | tab state, safe-area root, bottom navigation |
| `ui/LibraryScreen.kt` | flat lap feed, compare mode, filters |
| `ui/RecordScreen.kt` | telemetry + capture arm |
| `ui/LapPlayerScreen.kt` | video, sync, charts, map |
| `ui/LapCompareScreen.kt` | landscape two-lap comparison, track-progress sync |
| `ui/ImportPickScreen.kt` / `ImportSyncScreen.kt` | clip attach and sync |
| `ui/SettingsScreen.kt` | capture and unit settings |
| `ui/TelemetryCharts.kt` | traces and track map |
| `ui/EditLapSheet.kt` / `LapCard.kt` | lap editing and row presentation |
| `ui/DriverPickerRow.kt` / `AddDriverDialog.kt` / `DriverProfileSection.kt` | driver picker, add-driver dialog, Settings profile card |
| `res/font/` | bundled Geist Sans and Geist Mono TTF files |
| `licenses/Geist-OFL.txt` | Geist OFL-1.1 license |
| `MainActivity.kt` | edge-to-edge system bars, permissions, service wiring |

## Not in v0.1

- Light theme or dynamic color.
- Custom icon set beyond Material Icons Core.
- Motion/transition choreography.
- Tablet-specific layouts.
