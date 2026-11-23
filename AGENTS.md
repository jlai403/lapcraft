# AGENTS.md for LapCraft

Android app for GT7 telemetry-only lap review. Kotlin + Jetpack Compose, `minSdk 29`,
`targetSdk 36`, package `io.github.jlai403.lapcraft`.

## Commands

Full check before merging:

```sh
cd android && ANDROID_HOME=/opt/android-sdk ./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Site (Astro + Svelte, Cloudflare Pages):

```sh
cd site
bun install --frozen-lockfile
bun run verify          # tsc --noEmit + astro build
bun run dev             # local dev server
./tools/site-assets.sh  # regenerate media/stills/og from demo takes
./tools/site-assets.sh --check  # budget + all-intra check
```

Deploy is `.github/workflows/site.yml`, path-filtered to `site/**` and `tools/site-assets.sh`. Production on push to `main`, preview on PR, both under the `website` GitHub environment (`CLOUDFLARE_API_TOKEN` secret; `CLOUDFLARE_ACCOUNT_ID`, `PUBLIC_GTM_ID`, `PUBLIC_CF_BEACON_TOKEN` variables). Hosted as a Cloudflare Worker with static assets (`site/wrangler.jsonc`, `wrangler deploy`), not Pages; same-repo PRs upload a version with `wrangler versions upload`, fork PRs skip the deploy steps. The deploy runs a globally installed `wrangler` pinned to the `devDependencies` version, never `bunx`, so PR-controlled packages never see the token; `bun audit` gates the build. Custom domain `lapcraft.jlai.ca`.

Focused unit tests:

```sh
cd android && ANDROID_HOME=/opt/android-sdk ./gradlew :app:testDebugUnitTest --tests '*LapPlayerScreenTest'
```

## Modules

- `:core` holds pure Kotlin: samples, sessions, playback cursor, clip trim math, track identification. No Android UI.
- `:app` holds Compose UI, capture service, replay import/playback, settings, updater.
- `site/` holds the marketing site: Astro 5 static, Svelte 5 islands, Bun, TypeScript 7. Logic lives in `src/lib/*.ts` (type checked); `.astro` and `.svelte` files are not type checked in CI. Two islands: `ScrollVideo` (scroll-scrubbed demo) and `ConsentBanner` (gates GTM). Compare section uses real screenshots with a vanilla tab switcher (`src/lib/compare.ts`); full demo opens in a native `<dialog>` (`src/lib/demo.ts`). Tokens in `src/styles/tokens.css` mirror `DESIGN.md`. `public/_headers` carries nosniff, the referrer and permissions policies, HSTS, and `Content-Security-Policy: frame-ancestors 'none'`; add a fuller CSP only when the inline scripts have hashes. `PUBLIC_GTM_ID` and `PUBLIC_CF_BEACON_TOKEN` are format-validated in `src/lib/consent.ts` before they reach an inline script or selector. Assets come from `tools/site-assets.sh`. `/guide` is a static page at `site/src/pages/guide.astro`; its screenshots live in `site/src/assets/how-to/`, with the PS5 IP redacted in `capture-ready.png`. The footer source link is an inline GitHub mark plus a `GitHub` label. Site download CTAs point at the latest stable release (`https://github.com/jlai403/lapcraft/releases/latest`), with `All releases` as the secondary link. The `jlai.ca` Cloudflare zone injects a first-party Google tag gateway script into every HTML page on `lapcraft.jlai.ca`, so Google Analytics loads before consent; the banner and privacy copy say so, and only Google Tag Manager is consent-gated.
- The walkthrough pins its phone stage beside the copy on desktop and in the top `46vh` on mobile (`order: -1`, `position: sticky`, copy scrolls underneath). `showcase.ts` moves the IntersectionObserver band to the lower half at `max-width: 900px` and activates the step with the highest `intersectionRatio`, rebuilding the observer when the breakpoint flips. The compare step turns the phone 90deg and counter-rotates the shot so the app stays upright; mobile inline figures are the no-JS and reduced-motion fallback only.

## Key conventions

- Telemetry-only: no screen capture, MediaProjection, phone audio, Remote Play, or burned-in overlays.
- Launcher icon: adaptive `mipmap-anydpi-v26/ic_launcher*.xml` (full-bleed foreground over `#0D1114`, vector monochrome layer in `drawable/ic_launcher_monochrome.xml`) plus legacy PNGs; master art and the 512 px store icon live in `docs/branding/`. The status bar glyph is `drawable/ic_stat_lapcraft.xml`, and `Theme.LapCraft` (`values/themes.xml`, `values-v31/themes.xml`) paints the window and splash `#0D1114`. The API 31 splash uses `drawable-*/splash_icon.webp`, generated at 288 dp by `tools/generate-splash-icon.py` from `docs/branding/launcher-icon-master.jpg`; regenerate it when the master changes.
- The PS5 IPv4 address is edited only in Settings. Record reads the saved address, and its `Connect` pill reads `Set address` (opens Settings) when none is set. The Settings Console row shows the saved value or `Not set`; tapping it opens a dialog with the IP field (which can also clear it). Stream failures and packets from another host render in one `WarningBanner` above the cards. The address lives only in `SettingsStore` (not `CaptureUiState`); `RecordScreen` and `App` read it on composition so a change in Settings is visible on the next visit without a restart.
- Settings is ordered Display & playback → Console → Replay compression → Driver profile → Library → Event tags → Developer → About. Every setting uses `SettingRow` / `SettingToggleRow` (`ui/Components.kt`); toggles use `toggleable` with a display-only `Switch`. Updates live inside the Developer card and render only when `Developer tools` is on; on `stable` builds there is no manual update entry (the launch check and update dialog remain).
- Local-first: no accounts, analytics, or background cloud calls.
- UI copy and docs use sentence case and plain words. No em dashes, curly quotes, or benefit framing; see `DESIGN.md` under Copy and tone.
- `PacketAParser` reads the steering wheel angle from packet B/C at `0x128` and stores it in `Sample.steering` as `-1..1` (+ = left); packet A has none.
- Tyre compound, traction control and brake balance are manual lap metadata: GT7's telemetry carries none of them (packet C has no compound; flags bit 11 is only TCS-active, and there is no brake balance field). They live on `LapRecord.tyre` (`core/tire/TyreCompound`, 13 codes), `tcs` (0..5) and `brakeBalance` (-5..+5), are projected into `LapView`/`LapSummary`, and are stamped onto new laps from `SettingsStore.currentTyre`/`currentTcs`/`currentBrakeBalance` (Record screen) or set per lap in `EditLapSheet`. `TyreBadge` is hidden when untagged; labels are `TCS off`/`TCS n` and `BB +n`. The player's Speed/Gear row shows the badge and `TCS n · BB +n` by the speed, the compare Speed/Gear row shows each lap's beside its own badge, and the expanded player header repeats them. No sample codec change. `docs/adr/0006` and `0007`.
- The lap HUD traces the last 2 s before the cursor. The F1 wheel under the tire tiles is hidden for laps with no steering samples.
- Replay clips live under `laps/<id>/replay.mp4` (new layout) or `sessions/<id>/laps/lap-NNN.mp4` (legacy, migrated on first launch); telemetry is `lap.gt7lap` (new) or `lap-NNN.gt7lap` (legacy).
- The frame ruler only scrubs: a tap does nothing (no seek, no pause), and a horizontal drag past
  the touch slop steps frames from the playhead the drag started on. There is no jump-to-place.
- Sync sets the **lap start**: the frame where the car crosses the line, plus an optional typed
  offset (`0.019` = 19 ms) that pulls the trim start onto the line when the HUD timer is counting.
  `LapClock.forLapStart` and `core/clip/SyncOffset`. The sync footer reads `Clip current / end`
  plus a second `Lap` row.
- Library is a flat feed: no cards/radii, `Hairline` dividers, `RowPressed` for pressed rows.
- The library's primary nav is the kind tab row (`All · Daily · Time Trial · World Series`,
  `SegmentedChoice` with equal-width segments; the tab font shrinks 14→11 sp to fit and the row
  falls back to scrollable wrap-content when even 11 sp doesn't); it sets `EventFilter.Kind`.
  There is no event pill: kind lives in the tabs and one specific event in the feed's group headers.
  The filter row is `All tracks`, `All cars`, sort, then `Clear`, each pill carrying a vendored
  funnel or sort icon (`ui/FilterIcons.kt`); the two filter pills scroll horizontally, while the
  icon-only sort button (`GhostIconPill`, 44 dp) and `Clear` are pinned outside the scroll.
  `LapFilter.car` matches `carName` exactly; `LapFilter.eventId` and
  `EventFilter.None` remain in `core` but the UI no longer sets them. There is no replay filter
  (`LapFilter` has no `hasReplay`); the row badge carries replay state.
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
- `ClipTrimmer` sees samples in decode order, where PTS is not monotonic across B-frames. It writes
  every sample from the keyframe to the trim end and stops only a guard second past it
  (`core/clip/TrimTail`); stopping at the first late sample cut the last 1-3 frames of the lap.
- The clip window is the lap's **real span**, not GT7's reported lap time. GT7 counts 60ths of a
  second while a 59.94 Hz capture runs ~0.1 % slower, so `endTickNanos - startTickNanos` is 65-123 ms
  longer on a real lap; trimming to `lapTimeMs` dropped the line-crossing frames. `LapView.lapSpanMs`
  (guarded, falls back to `lapTimeMs` on missing ticks) feeds `LapClock.spanMs`, so `trimOutMs`,
  `endClipMs`, the frame-step end and the sync screen's suggested start and finish frame all use it.
  `lapTimeMs` stays the display value: `LapClock.lapMs(clipMs)` scales real ms back to GT7's timer for
  the footer readout, while `telemetryMs` stays real ms for sample and track lookups. Clips imported
  before this fix are still short; re-import them from the PS5 capture.
- `LapSummary` carries `videoBytes`, `telemetryBytes`, and `pendingBytes`, computed in
  `LapStore.summaries()`. `EditLapSheet` shows them under a `Data` section.
- The lap player, compare screen, and sync screen all use `SeekParameters.EXACT`. `ScrubSeeker`
  waits for a rendered frame (`SCRUB_EXACT_WAIT_MS` 120 ms while scrubbing, `EXACT_FRAME_WAIT_MS`
  400 ms while anchoring) and keeps at most one seek in flight.
- The lap player uses a `TextureView`-backed `PlayerView` (`res/layout/view_lap_player.xml`) so the
  telemetry HUD can draw over the video; a `SurfaceView` hides Compose overlays.
- A lap with no clip still plays: `LapPlayerScreen` runs a synthetic lap clock (`clockMs` advanced by
  `withFrameNanos`, scaled by the speed, looping or stopping at the lap end), `cursorMs` reads it when
  `hasClip` is false, and the footer is always composed with `PlaybackFooter(frameSteps = hasClip)`
  hiding the ±1 buttons. The no-clip card order is Telemetry, Track map, then the `Replay` prompt.
- There is no scrub thumbnail cache; the ruler seeks the clip directly. `SettingsStore.developerTools`
  exposes `Re-encode clip` in `EditLapSheet`, which rewrites a lap at 0.1 s keyframes
  (`LapReencoder`, `ReplayKeyframes.Frame`) and deletes the old clip plus any `lap-NNN.preview/` leftovers.
- `EditLapSheet` offers `Remove` for any lap with a replay (saved or pending sync); it calls
  `LapStore.removeReplay`, which deletes `replay.mp4`/`source.mp4`/`raw.mp4` and scrub artifacts and
  resets `leadInMs`, while keeping telemetry and metadata. The lap becomes clip-less.
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
  which reads the bundled `android/app/src/main/assets/car-catalog.json` (generated by `tools/fetch-car-catalog.sh`
  with `GRIDSTATS_API_KEY`; ~10 metered calls, 7 s spacing). Refresh the asset when GT7 adds cars.
  `LapStore` (with `LapId`/`LapRecord`) is lap storage; `lapStore(context)` returns one. Legacy sessions are migrated once by `LayoutMigration` (sessions -> laps, convert-and-delete); no `SessionStore`.
- Event tags: `core/events` (EventTag, CachedEvent, EventMatcher, EventDates, EventRepair), `app/events` (EventCache, EventRefresh,
  GridStatsEventParser, PolyphonyCdnParser, PolyphonyRegion, EventTagger). Settings toggle `eventTags`; refresh from
  Settings or from the edit sheet's empty state. `Refresh events` forces both feeds even while the
  toggle is off, and `EventCacheFile.lastFetchedAtMs` (newest of the schedule and GridStats stamps)
  drives the card's last-updated line. Daily labels carry the ISO week (`Daily Race A · W47`)
  while ids stay week-free; Time Trials label by track (`Time Trial · Suzuka`). GridStats dates a
  daily week Monday 00:00 to Sunday 23:59:59 UTC, but GT7 rolls it at 07:00 UTC, so
  `GridStatsEventParser` anchors the window with `sportModeWeekStart`/`sportModeWeekEnd`
  (Monday 07:00 to the next Monday 06:59:59). `EventCache.replaceGridstats` keeps the outgoing
  week until that real end, and `EventRefresh.repairDailyTags` rewrites snapshotted daily tags to
  the same window. The library runs it on every load from the laps it already has, so the repair
  needs no network; a GridStats refresh runs it too. Tags saved before windows existed get theirs
  from the `begin_date` at the end of the id, and `EventGroups` normalizes a daily tag's window and
  reads it from any lap in the group, so a legacy tag cannot drag the group into Past events. The
  edit sheet merges events already tagged in the library into its suggestions, so an older week
  stays pickable after the feed rolls; suggestions that contain the lap's capture time rank first.
  The edit sheet's event
  field suggests cached events and stores free text as a manual Championship tag. `LapView.event` /
  `LapSummary.event`; `LapFilter`/`LapSort` event dimensions.
- The Polyphony schedule is fetched from the region folder chosen by the device country
  (`PolyphonyRegion`: Europe 2, Asia 4, Oceania 5, North America 6, South America 7, Europe for
  unknown). The folder is not the JSON `region_id`, and choosing the wrong one marks a live round as
  lapsed when another region's slots end. Both league entries for a round merge into one window
  (min start, max end), and a folder change counts as stale so the next refresh refetches.
  `EventTag.repairedFrom` remaps auto championship tags to the new folder by series, round and lap
  capture time, so a region change does not leave old tags in Past events. Range labels use the device
  zone (`EventDates.eventRangeLabel`) while ISO week labels stay UTC.
- Library grouping: `LapSummary.eventGroups` (`core/events/EventGroups.kt`) orders current events,
  the `No event` group (every untagged lap, however old), then lapsed events; `EventTag.status`
  (`EventStatus.kt`) resolves current / upcoming / lapsed / unknown. `EventTag.startMs`/`endMs` are
  snapshotted at tag time, so a lapsed event keeps its window after the schedule refresh; auto tags
  without a window fall back to cache
  membership, manual tags without one stay `Unknown` unless their newest lap predates the week, in
  which case they are lapsed. `LibraryEventSection.kt` renders
  `CurrentEventHeader` (title + `series · dates · class · track · laps · Best`) and the
  `PastEventsHeader` collapse row. Wrap each lazy row in `key(lap.lapId.value)` and avoid
  `Modifier.padding`/extra conditionals before `LapCard`: both trigger a Compose closure hoist that
  makes every row's `onOpen` use the last lap. The event label is gone from `LapCard`; the header owns it.
- Acknowledgements: always-visible row at the bottom of Settings (in the About card) links to GT GridStats (DriverProfileLinks.gridStatsHome).
- Build: `org.gradle.caching` and configuration cache are on; app unit tests run with 2 forks and a
  1 GB heap (`android/app/build.gradle.kts`). Inner loop is `:app:assembleDebug` (~3 s) plus targeted
  `--tests`; `:app:lintDebug` (~25 s) is CI/pre-merge only. TrackMap's root carries `testTag("track-map")`.
  Build types: `debug` (dev), `stable` (production id, UPDATE_CHANNEL=stable), `preview` (id suffix `.preview`).
  Preview icons and label live in `android/app/src/preview/res`; regenerate with `tools/generate-preview-icon.py`.
- Device tests live in `android/app/src/androidTest` (Espresso + Compose, `AndroidJUnitRunner`). They are
  device-gated (no emulator/`/dev/kvm` in this environment): run with
  `:app:connectedDebugAndroidTest` or a Gradle managed device once `/dev/kvm` is exposed.

## Library transfer

- `LibraryArchive` (`core/.../session/LibraryArchive.kt`) writes a zip: `library.json` with `format`, `exportedAt`, `laps`, `bytes`, an optional `profiles.json` with the cached GT Grid profiles for the exported laps' drivers, then one `library/<id>/` per lap holding `session.json`, `lap.json`, the `.gt7lap`, and any clip or pending source the lap references.
- Entry id is the session id when that session holds one lap, otherwise `<sessionId>-l<NNN>`; ids must match `[A-Za-z0-9][A-Za-z0-9._-]{0,63}` so an imported directory can never be mistaken for staging.
- Import is merge-only: existing ids are skipped, corrupt entries are counted and dropped, unsafe paths throw. Missing clip references are cleared and the lap still imports.
- `.mp4` entries are written at deflate level 0; telemetry and JSON use the default level.
- SAF plumbing lives in `app/.../share/LibraryTransfer.kt`; the UI is `LibrarySection` (Settings) and `ExportLapsSheet`; the empty library has its own import action.
- Changing `applicationId` loses `filesDir`: export from the old package before renaming, then import into the new one.

## Seed laps

- `android/app/src/main/assets/seed-laps.zip` carries two unmarked Suzuka laps (Dallara SF23,
  1:35.842 and 1:36.131) with 720p60 HEVC clips, generated by `tools/make-seed-laps.sh`
  from an exported library zip. `tools/seed/` is gitignored (the export contains a PSN).
- `app/seed/SeedLaps.kt` imports the archive once via `LibraryArchive.import` when the library
  is empty and `SettingsStore.seedVersion < 1`, then stamps `capturedAt` at import time so the
  laps land in the `No event` group. `previewEmptyLibrary` suppresses seeding. No marking is
  applied and there is no restore action; deleting them is final.
- The tool clears `driverPsnId`, `event`, `profiles.json`, `source.mp4` and `raw.mp4`, and
  re-encodes `replay.mp4` to 720p60 CRF 34, GOP 12, AAC 96k. `--check` validates the committed
  asset without ffmpeg; the asset is stored (not deflated) in the APK via `noCompress += "zip"`.

## Compare

- `LapCompareScreen` is landscape-only: it requests `SCREEN_ORIENTATION_LANDSCAPE` on entry and
  restores the previous value in `onDispose`; portrait pauses playback behind a rotate prompt.
- The primary lap (first opened or first picked) owns the clock. Only its player calls `play()`;
  the reference is muted and seeked frame-exactly to the primary's place on track via `ScrubSeeker`.
- The screen is two tabs under a 44 dp chrome row (back, `Video`/`Telemetry`, delta). `Video` shows
  both panes, each with `TelemetryHud` at the top centre and `CompareReadoutRow` underneath; the
  panes carry no text overlay. GT7's own HUD is in the frame, so the lap and cursor times live in
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
  `Primary`, and a third pick replaces the reference. A reference must share the primary's track and,
  when the primary is tagged, its event: `LapSummary.canCompareWith` scopes both this feed (it narrows
  after the primary pick) and the player's `Compare with` dialog.
- Entry points are `Screen.Compare(...)`; the player's `Compare` pill and the library's compare action
  both route there, and `Add clip` returns through `ImportSync(returnToCompare = ...)`.

## Domain model (current)

- Lap identity is a `LapId` (directory name under `files/laps/`); `LapSummary`, `Screen` and `CaptureState.Saved` all carry it. `LapSummary` is a projection of `LapRecord`, keyed by `trackId`.
- Replay state is derived from files present (`replay.mp4`/`source.mp4`), not fields; `LapRecord` keeps only `leadInMs` and `syncOffsetMs`.
- Clip-to-lap mapping is a single `LapClock` (`core/clip`); `PlaybackCursor` and `ClipSync` are gone.
- Track identity: `LapRecord.trackId` (official circuit id, empty when unidentified) + `trackLabel` (editable). `trackKey` falls back to the normalised label only when `trackId` is empty. `renameTrack` changes only the label.
- `Sample.steering: Float?` (null = not recorded); codec version 2 with a presence byte; `packetId` is not persisted. Readers use `hasSteering()` (window scan for non-null) instead of thresholds.
- Event tags: `EventTag.kind: EventKind`, `LapFilter.event: EventFilter` (sealed All/None/Kind). No magic `"none"` string. `EventTag.matchKey` is the event identity for grouping and compare scope: `id`, except manual tags, which use their normalised label.
- `LapDetector` exposes a sealed `CurrentLap` (None/FromLine/MidLap) replacing the two volatile fields. `LapBoundary` carries `lapTimeSource`; `LapTimeSource` lives in `core/lap`.
- `LoadedLap` (renamed from `GhostLap`) is "a lap with its samples decoded"; ghost/reference are roles.
- Storage helpers: `lapStore(context)`; legacy sessions are migrated once by `LayoutMigration` (sessions -> laps, convert-and-delete). `LapView` is the UI projection of `LapRecord`.
- New module file: `core/.../clip/LapClock.kt`, `core/.../clip/SyncOffset.kt`, `core/.../clip/FrameTimeline.kt`, `core/.../session/LapView.kt`, `core/.../session/LayoutMigration.kt`, `core/.../events/EventKind.kt`, `core/.../events/EventFilter.kt`, `core/.../lap/LapTimeSource.kt`.

## Demo video kit

- `mise.toml` pins scrcpy and ffmpeg for the demo kit and has a `demo:setup` task that downloads `scrcpy-server`, which the ubi binary omits. adb comes from the OS package because the mise Android SDK plugin installs `sdkmanager` rather than `platform-tools`. Run the scripts with `mise exec --` unless the shell has `mise activate`.
- `tools/demo/` holds the showcase video kit: `shotlist.md` (hero card plus four beats in 26 seconds), `captions.srt` (master captions), `capture.sh` (scripted adb takes found via `uiautomator dump`), `conform.sh` (both layouts: dark backdrop, phone shadow and border, Geist Medium captions, Geist Mono chapters, mint progress bar, no end card), `demo.py` (adb and SRT helpers), `legacy/` for archived takes, and `beats/` plus `.build/` for intermediates.
- Record beats with `./tools/demo/capture.sh --beat 01|02|04|all [--touches on|off] [--lap '1:36.131'] [--reference '1:34.208'] [--serial ip:port] [--dry-run]`. `--serial` targets one device for adb and scrcpy, needed for wireless adb or multiple devices. scrcpy runs with `--no-window` and `SDL_VIDEODRIVER=dummy`, so capture is headless, and preflight exports `ADB` so mise scrcpy finds the system adb. Preflight wakes the phone, dismisses a swipe-only keyguard, and launches the app if it is not in front. Beat 01 needs a live stream and stops with an error unless the Record screen reads `Streaming`; beat 02 plays at 2x for four seconds then scrubs the ruler, and its analysis beat is cut from the same take; beat 04 opens compare, plays the first corner, and switches to the Telemetry tab. Touches default on for 04 only.
- Playback is verified by hashing two screenshots, never by the play/pause label: `uiautomator dump` can fail, leave the previous tree on disk, and make the script tap Play twice. `demo.py` deletes the dump first and rejects its error output. Compare is landscape only, so beat 04 pins `user_rotation 1` and warms the Telemetry tab before recording.
- Build with `./tools/demo/conform.sh --layout wide|tall [--music file.wav] [--dry-run]`; output is `tools/demo/master.mp4` (1920x1080) or `master-vertical.mp4` (1080x1920). Beat 04's trim is found by detecting the tab switch in the take, so the cut always shows four seconds of replay and five of telemetry. `demo.py slice-srt` generates a styled ASS beside each sliced SRT so libass uses fixed canvas coordinates and the bundled Geist font.
- The master uses the two Suzuka laps tagged W47, both with clips, primary `1:36.131` and reference `1:34.208`. The live capture take no longer exists, so beat 01 is the archived `legacy/capture-01.mp4` cropped from the earlier master and held as a still; `conform.sh` falls back to it when `beats/01.mkv` is missing.
- The hero uses `drawable-xxxhdpi/splash_icon.webp` (artwork on transparency, cropped to its middle two thirds), not `docs/branding/ic_launcher_playstore.png`: that file carries an opaque `#14171C` plate, which shows as a dark square and gives the glow a hard edge. The glow is a round pool of accent light plus a tight bloom of the artwork, blurred so the 8-bit ramp does not band.

## Release

- Work in a branch, open a PR, squash-merge. `main` is protected by a ruleset (no direct pushes, no force-push, no deletion) and requires the `android-test` and `android-build` checks. Merging publishes a per-build preview release.
- The updater only checks versions: `UpdateManager` reads `latest.json` and `openReleasePage` opens the GitHub release in the browser. There is no install-packages permission, so Play Protect has nothing to flag; downloads and installs stay with the browser.
- `versionCode = 1000 + git commit count`; `versionName = 0.2.<commit count>`. A stable `v*` tag must equal its version name (`v0.2.<count>`) so Obtainium and the version check can compare.
- Each push to `main` publishes a prerelease `v0.2.<count>` carrying `LapCraft-preview.apk` plus `latest.json`, and refreshes a rolling `preview-latest` release that carries `LapCraft-preview.apk` and `latest.json` for the in-app check. Older previews are pruned to the last 10.
- A stable release is a promotion, not a tag push: Actions → `promote` (dropdown picks the newest prerelease of a minor, e.g. `v0.2`, or type an exact tag override) rebuilds that commit as `assembleStable` (UPDATE_CHANNEL=stable), uploads `LapCraft.apk` plus the versioned APK and `latest.json`, and flips the prerelease flag. Its versionCode matches the preview build, so it installs over it.
- Two installable packages: `io.github.jlai403.lapcraft` (stable) and `io.github.jlai403.lapcraft.preview` (preview). Build variants `assembleStable` and `assemblePreview`; preview resources live in `android/app/src/preview/res` (label and icon).
- Release builds are signed with a private key held in the `android` GitHub **environment** (secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_PASSWORD`; `KEY_ALIAS` is a repo variable defaulting to `lapcraft`). The `android` environment must carry a deployment branch policy for `main` and `v*` tags (repo setting, not code), so PRs and feature branches build the debug variant only and never see the key. `android:debuggable` is false on both release variants, and `assembleStable`/`assemblePreview` fail fast without the key. `ci/verify-release.sh` rejects any APK signed with the public debug key or marked debuggable. Release certificate SHA-256: `8e483b6f6d1f51c8cd6513db7bf20220e0b6159f299827c6aa49b2c7fca74c76` (DN `CN=LapCraft, O=jlai403, C=CA`). First generated 2026-11-21. A new key cannot install over an older debug-key build, so the first release after this change needs an uninstall (export the library first).
- Installers and updaters (F-Droid, Play, Obtainium) handle their own updates; the in-app check stays quiet for them.`
