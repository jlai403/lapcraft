# AGENTS.md — LapCraft

Android app for GT7 telemetry-only lap review. Kotlin + Jetpack Compose, `minSdk 29`,
`targetSdk 36`, package `io.github.jlai403.lapcraft`.

## Commands

Full check before merging:

```sh
ANDROID_HOME=/opt/android-sdk ./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Focused unit tests:

```sh
ANDROID_HOME=/opt/android-sdk ./gradlew :app:testDebugUnitTest --tests '*LapPlayerScreenTest'
```

## Modules

- `:core` — pure Kotlin: samples, sessions, playback cursor, clip trim math, track identification. No Android UI.
- `:app` — Compose UI, capture service, replay import/playback, settings, updater.

## Key conventions

- Telemetry-only: no screen capture, MediaProjection, phone audio, Remote Play, or burned-in overlays.
- Local-first: no accounts, analytics, or background cloud calls.
- `PacketAParser` reads the steering wheel angle from packet B/C at `0x128` and stores it in `Sample.steering` as `-1..1` (+ = left); packet A has none.
- The lap HUD traces the last 2 s before the cursor. The F1 wheel under the tire tiles is hidden for laps with no steering samples.
- Replay clips live under `laps/<id>/replay.mp4` (new layout) or `sessions/<id>/laps/lap-NNN.mp4` (legacy, migrated on first launch); telemetry is `lap.gt7lap` (new) or `lap-NNN.gt7lap` (legacy).
- The frame ruler only scrubs: a tap does nothing (no seek, no pause), and a horizontal drag past
  the touch slop steps frames from the playhead the drag started on. There is no jump-to-place.
- Sync sets the **lap start**: the frame where the car crosses the line, plus an optional typed
  offset (`0.019` = 19 ms) that pulls the trim start onto the line when the HUD timer is counting.
  `LapClock.forLapStart` and `core/clip/SyncOffset`. The sync footer reads `Clip current / end`
  plus a second `Lap` row.
- Library is a flat feed: no cards/radii, `Hairline` dividers, `RowPressed` for pressed rows.
- Every field in the edit sheet is an `EditRow` (label, one-line value, actions flush right); edits commit
  as they are made and the footer is `Done`, so `onSaveTrack` must persist without dismissing the sheet.
- `DESIGN.md` is the visual/behaviour reference; `docs/specs/2026-11-10-lapcraft.md` is the functional spec. The domain model lives in `docs/adr/` (ADRs) and `docs/domain-model-review.md` (living review log); `GLOSSARY.md` is the vocabulary.
## Replay storage

- New imports are trimmed, then compacted with the Settings replay profile before `attachClip`.
- Settings live in `SettingsStore`: `replayPreset` (default `Balanced`), `replayKeyframes` (`Frame`,
  0.1 s), `replayAudio` (`Stereo`), `replayResolution` (`FullHd`), `replayCodec` (`Hevc`). The 0.1 s
  GOP keeps exact seeks to six 60 fps frames at ~35 % larger files; `estimateReplayBytes` includes
  that premium.
- `app/.../settings/ReplayCompression.kt` defines the enums, resolved `ReplayProfile`, captions, and size estimates.
- `ReplayCompressionControls` (`app/.../ui/`) is shared by Settings (defaults) and the sync screen (per-lap override, session-only, with a live `≈ N MB` estimate).
- `app/.../replay/VideoTranscoder.kt` re-encodes with Media3 Transformer (`media3-transformer:1.5.1`).
  On failure the trimmed original is kept and the save shows a "Compaction failed" toast.
- `app/.../replay/ClipFrameTimes.kt` reads a clip's real frame PTS via `MediaExtractor`; frame stepping
  (`FrameTimeline`) and trim snapping use it, falling back to the estimated duration when unavailable.
- `LapSummary` carries `videoBytes`, `telemetryBytes`, and `pendingBytes`, computed in
  `LapStore.summaries()`. `EditLapSheet` shows them under a `Data` section.
- The lap player, compare screen, and sync screen all use `SeekParameters.EXACT`. `ScrubSeeker`
  waits for a rendered frame (`SCRUB_EXACT_WAIT_MS` 120 ms while scrubbing, `EXACT_FRAME_WAIT_MS`
  400 ms while anchoring) and keeps at most one seek in flight.
- The lap player uses a `TextureView`-backed `PlayerView` (`res/layout/view_lap_player.xml`) so the
  telemetry HUD can draw over the video; a `SurfaceView` hides Compose overlays.
- There is no scrub thumbnail cache; the ruler seeks the clip directly. `SettingsStore.developerTools`
  exposes `Re-encode clip` in `EditLapSheet`, which rewrites a lap at 0.1 s keyframes
  (`LapReencoder`, `ReplayKeyframes.Frame`) and deletes the old clip plus any `lap-NNN.preview/` leftovers.
- `LapStore.deleteScrubArtifacts` removes a lap's `.tmp`/`.bak`/`.compact.mp4`/`.reencode.mp4`
  temporaries and preview directories; `deleteReplayArtifacts` also removes the clip.
- Typography: `labelSmall` is defined in `Theme.kt` (Geist Sans 11 sp); `NumericText` is mono with
  zero tracking. Do not use an undefined Material label style on Compose screens.
- GT GridStats: optional user-supplied API key lives in `DriverProfileStore.apiKey` (app-private prefs,
  never exported). `GridStatsProfileClient(keyProvider)` uses the JSON API when a key is set (DR/SR)
  and falls back to the public HTML page otherwise, which only carries nickname + flag. `fetch()`
  stamps `DriverProfile.fetchedAtMs`; `ratingLabel()`/`ratingWithAgeLabel()` (`app/profile/DriverRating.kt`)
  render it, and Settings offers `Refresh profiles (N)` for missing or week-old ratings. Removing a
  driver only clears the cached profile (`DriverProfileStore.remove`), never the lap's `driverPsnId`.
  Car names resolve via `app/catalog/CarCatalog`,
  which reads the bundled `app/src/main/assets/car-catalog.json` (generated by `tools/fetch-car-catalog.sh`
  with `GRIDSTATS_API_KEY`; ~10 metered calls, 7 s spacing). Refresh the asset when GT7 adds cars.
  `LapStore` (with `LapId`/`LapRecord`) is lap storage; `lapStore(context)` returns one. Legacy sessions are migrated once by `LayoutMigration` (sessions -> laps, convert-and-delete); no `SessionStore`.
- Event tags: `core/events` (EventTag, CachedEvent, EventMatcher, EventDates), `app/events` (EventCache, EventRefresh,
  GridStatsEventParser, PolyphonyCdnParser, EventTagger). Settings toggle `eventTags`; refresh from
  Settings or from the edit sheet's empty state. Daily labels carry the ISO week (`Daily Race A · W47`)
  while ids stay week-free; Time Trials label by track (`Time Trial · Suzuka`). The edit sheet's event
  field suggests cached events and stores free text as a manual Championship tag. `LapView.event` /
  `LapSummary.event`; `LapFilter`/`LapSort` event dimensions.
- Library grouping: `LapSummary.eventGroups` (`core/events/EventGroups.kt`) orders current events,
  the `No event` group, then lapsed ones; `EventTag.status` (`EventStatus.kt`) resolves current /
  upcoming / lapsed / unknown. `EventTag.startMs`/`endMs` are snapshotted at tag time, so a lapsed
  event keeps its window after the schedule refresh; auto tags without a window fall back to cache
  membership, manual tags without one stay `Unknown`. `LibraryEventSection.kt` renders
  `CurrentEventHeader` (title + `series · dates · class · track · laps · Best`) and the
  `PastEventsHeader` collapse row. Wrap each lazy row in `key(lap.lapId.value)` and avoid
  `Modifier.padding`/extra conditionals before `LapCard`: both trigger a Compose closure hoist that
  makes every row's `onOpen` use the last lap. The event label is gone from `LapCard`; the header owns it.
- Acknowledgements: Settings card at the bottom links to GT GridStats (DriverProfileLinks.gridStatsHome).
- Build: `org.gradle.caching` and configuration cache are on; app unit tests run with 2 forks and a
  1 GB heap (`app/build.gradle.kts`). Inner loop is `:app:assembleDebug` (~3 s) plus targeted
  `--tests`; `:app:lintDebug` (~25 s) is CI/pre-merge only. TrackMap's root carries `testTag("track-map")`.
- Device tests live in `app/src/androidTest` (Espresso + Compose, `AndroidJUnitRunner`). They are
  device-gated (no emulator/`/dev/kvm` in this environment): run with
  `:app:connectedDebugAndroidTest` or a Gradle managed device once `/dev/kvm` is exposed.

## Library transfer

- `LibraryArchive` (`core/.../session/LibraryArchive.kt`) writes a zip: `library.json` with `format`, `exportedAt`, `laps`, `bytes`, an optional `profiles.json` with the cached GT Grid profiles for the exported laps' drivers, then one `library/<id>/` per lap holding `session.json`, `lap.json`, the `.gt7lap`, and any clip or pending source the lap references.
- Entry id is the session id when that session holds one lap, otherwise `<sessionId>-l<NNN>`; ids must match `[A-Za-z0-9][A-Za-z0-9._-]{0,63}` so an imported directory can never be mistaken for staging.
- Import is merge-only: existing ids are skipped, corrupt entries are counted and dropped, unsafe paths throw. Missing clip references are cleared and the lap still imports.
- `.mp4` entries are written at deflate level 0; telemetry and JSON use the default level.
- SAF plumbing lives in `app/.../share/LibraryTransfer.kt`; the UI is `LibrarySection` (Settings) and `ExportLapsSheet`; the empty library has its own import action.
- Changing `applicationId` loses `filesDir`: export from the old package before renaming, then import into the new one.

## Compare

- `LapCompareScreen` is landscape-only: it requests `SCREEN_ORIENTATION_LANDSCAPE` on entry and
  restores the previous value in `onDispose`; portrait pauses playback behind a rotate prompt.
- The primary lap (first opened or first picked) owns the clock. Only its player calls `play()`;
  the reference is muted and seeked frame-exactly to the primary's place on track via `ScrubSeeker`.
- The screen is two tabs under a 44 dp chrome row (back, `Video`/`Telemetry`, delta). `Video` shows
  both panes, each with `TelemetryHud` at the top centre and `CompareReadoutRow` underneath; the
  panes carry no text overlay — GT7's own HUD is in the frame, so the lap and cursor times live in
  the pane's accessibility label (`compare-cursor-<tag>`). `Telemetry` shows the map in a fixed
  320 dp pane (capped at 42 % of the width) with the map canvas clipped and lap 1's marker pinned
  to the pane centre; `CompareCharts` fills the rest, with no readout strip.
- `TrackProgress.Table` caches cumulative distance per lap and interpolates both ways:
  `percentAt(timeMs)` brackets by time, `timeAtPercent(percent, lapTimeMs)` brackets by distance.
  Never snap to the nearest sample.
- `TrackProgress.align` puts the reference on lap 1's driven path (2.5 m polyline, 10 m behind to
  30 m ahead of the previous answer, monotone, full-path search past 50 m). It returns null on a
  path broken by a GT7 reset, and the screen then falls back to `timeAtPercent`. The video seek, the
  reference chart axis, the map dot and `Δ` all read this one alignment.
- Compare charts are one per metric (`BRK/THR/SPD/RPM/GEAR`) with the primary in `AccentCyan` and
  the reference solid `AccentAmber`; each chart shows a ±5 s window around the cursor (centred,
  clamped at the lap ends) instead of the whole lap. The map keeps pedal colouring on the primary
  and uses solid amber for the reference, both drawn as 1.5 dp lines. Cyan/amber are
  Telemetry-tab only.
- A single `Speed`/`Gear` row sits above the charts on both the lap player (primary only) and the
  compare Telemetry tab (lap 1 / reference, in cyan and amber), driven by the cursor sample.
- The Telemetry tab has no video surface, so its cursor reads the player clock (`positionMs`) at
  60 fps instead of the last rendered frame; the Video tab keeps frame-exact scrubbing.
- The track map supports pinch zoom and two-finger pan on both screens, with a double-tap reset
  (0.6×–8×); it opens at maximum zoom in follow mode, with a crisp-white car marker. The five
  compare charts are always fully visible (no scroll).
- The chart window (`±2/±3/±5/±10 s`) is configurable in Settings → Display; the default is ±5 s.
- The compare `Speed`/`Gear` row shows lap 1 / reference values plus a signed delta: speed delta
  is mint when lap 1 is faster and red when slower; gear delta is mint in a higher gear and red in
  a lower one (muted when equal).
- `PlaybackFooter(compact = true)` is the shared footer for the compare screen only: one 48 dp
  row with the frame steps on the outside of the skip buttons, a 40 dp ruler, a `1×` rate cycle, and a
  loop icon. The sync screen and the single-lap player keep the taller layout with a labelled clock
  readout (`Clip`/`Lap` `current / end`, plus a second `Lap` row on the sync screen). All three
  screens step via `ScrubSeeker` + `scrubTargetMs`. `StepButton` reads its callback through
  `rememberUpdatedState`, since the pointer-input gesture coroutine is only restarted when its keys
  or handler class change.
- Library compare mode lives in `LibraryScreen`: header pill, row checkboxes, the first pick labelled
  `Primary`, same-track picks only, and a third pick replaces the reference.
- Entry points are `Screen.Compare(...)`; the player's `Compare` pill and the library's compare action
  both route there, and `Add clip` returns through `ImportSync(returnToCompare = ...)`.

## Domain model (current)

- Lap identity is a `LapId` (directory name under `files/laps/`); `LapSummary`, `Screen` and `CaptureState.Saved` all carry it. `LapSummary` is a projection of `LapRecord`, keyed by `trackId`.
- Replay state is derived from files present (`replay.mp4`/`source.mp4`), not fields; `LapRecord` keeps only `leadInMs` and `syncOffsetMs`.
- Clip-to-lap mapping is a single `LapClock` (`core/clip`); `PlaybackCursor` and `ClipSync` are gone.
- Track identity: `LapRecord.trackId` (official circuit id, empty when unidentified) + `trackLabel` (editable). `trackKey` falls back to the normalised label only when `trackId` is empty. `renameTrack` changes only the label.
- `Sample.steering: Float?` (null = not recorded); codec version 2 with a presence byte; `packetId` is not persisted. Readers use `hasSteering()` (window scan for non-null) instead of thresholds.
- Event tags: `EventTag.kind: EventKind`, `LapFilter.event: EventFilter` (sealed All/None/Kind). No magic `"none"` string.
- `LapDetector` exposes a sealed `CurrentLap` (None/FromLine/MidLap) replacing the two volatile fields. `LapBoundary` carries `lapTimeSource`; `LapTimeSource` lives in `core/lap`.
- `LoadedLap` (renamed from `GhostLap`) is "a lap with its samples decoded"; ghost/reference are roles.
- Storage helpers: `lapStore(context)`; legacy sessions are migrated once by `LayoutMigration` (sessions -> laps, convert-and-delete). `LapView` is the UI projection of `LapRecord`.
- New module file: `core/.../clip/LapClock.kt`, `core/.../clip/SyncOffset.kt`, `core/.../clip/FrameTimeline.kt`, `core/.../session/LapView.kt`, `core/.../session/LayoutMigration.kt`, `core/.../events/EventKind.kt`, `core/.../events/EventFilter.kt`, `core/.../lap/LapTimeSource.kt`.

## Release

- Work in a branch, merge to `main`, push. Pushing `main` triggers CI and preview publish.
- `versionCode = 1000 + git commit count`; `v*` tags are stable builds.
