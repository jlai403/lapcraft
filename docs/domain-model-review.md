# LapCraft domain model review

A living record of how the domain model was read, what looked wrong, and what was decided. Append a dated section per review. The glossary is the current vocabulary; the ADRs are the binding decisions. This file is the reasoning between them, kept so a later session does not have to rediscover it.

## 2026-11-15

Scope: the whole model, `core` plus how the app consumes it. Method: read the persisted shapes, the in-memory shapes, and the UI call sites, then check each term against `GLOSSARY.md` and the code.

### Where things stand

| # | Finding | Status |
|---|---|---|
| 1 | Lap identity is a `(sessionId, lapIndex)` pair used in about 20 places | done: `LapId` everywhere. `LapSummary`, `Screen`, `CaptureState.Saved` and the library key all carry a `LapId`; `sameLap` and the hardcoded index are gone. `LapMetadata`/`index` remains only as the legacy migration shape. |
| 2 | Replay readiness is encoded in four overlapping fields with four derived read rules | done: fixed media names, state derived from the directory |
| 3 | Track identity is an editable display string | done: `trackId` is populated at capture (via `TrackCatalog.match`), carried through `LapSummary`, and keys grouping, compare eligibility, fastest-per-track and the ghost. `renameTrack` changes only the label. `TrackNamer` backfills empty ids (VERSION 2). Archives keep the id. |
| 4 | The session no longer describes anything the app does | done: flattened to one directory per lap (ADR 0001) |
| 5 | Two independent clip-to-lap time mappings | done: single `LapClock` in `core/clip`, `PlaybackCursor` and `ClipSync` removed; trim range and sync validation take the clock. |
| 6 | Missing steering is stored as `0f` | accepted, ADR 0003. Open branch `feat/steering-codec-v2`. |
| 7 | Three time bases share `Ms` and `Nanos` names, and lap time has two sources | partly accepted. *Per-lap wall clock is resolved by the flatten*: `LapSaver` writes `capturedAt` per lap, so `LapSort.Newest` orders across laps. Remaining scope is only `LapDetector`'s `currentLapStartNanos`/`currentLapStartObserved` → sealed `CurrentLap` state. |
| 8 | The library row is a parallel definition of a lap, and filters are stringly typed | done: identity half in `refactor/lap-row`; event half in `refactor/event-kind-and-loaded-lap` (`EventTag.kind: EventKind`, `LapFilter.event: EventFilter`, no magic `"none"` string). |
| 9 | Glossary and code disagree on three terms | resolved in the glossary rewrite, except the track name: glossary says *Track id* and avoids *circuit id*, while code and ADR 0002 say `circuitId`. Settled in the docs truth-up branch: rename code to `trackId`/`trackLabel` (the field is dead and unwired, so the rename is free now and a schema migration later). `GhostLap` renamed to `LoadedLap`. |

### What was already sound

Worth keeping, and worth not "cleaning up" later.

- `LapBoundary`, `ClipSync` with `SyncCheck`, `TrimRange`, `RemuxPlanner.alignToKeyframe`. Small, testable, and they name real concepts.
- `TrackIdentifier` with the box gate, racing-line coverage and reverse configuration detection.
- `TrackProgress.Table` and `Place alignment`, which interpolate both ways instead of snapping to a sample.
- `LapDetector`'s plausibility gates and the "did we observe this crossing" flag.
- `LibraryArchive`'s staging directory, id pattern and unsafe-path rejection.
- `DriverProfileStore`'s legacy owner migration.
- `ReplayCompression` resolving a preset into a profile, with the caption and size estimate next to it.

### 1. Lap identity

Problem. A lap is addressed by a pair in `SessionStore` (11 methods), `LapSummary`, three `Screen` variants, `CompareLap`, `GhostLap`, `DriverSection`, `Sharing`, `TrackNamer`, `LapReencoder` and `ReplayStore`. `LapRef` declares the pair and only the export path uses it.

Evidence. `LibraryScreen.kt:505` builds the key by hand, `key = { _, lap -> "${lap.sessionId}:${lap.lapIndex}" }`. `RecordScreen.kt:259` hardcodes the second half, `onOpenLap(capture.sessionId, 1)`, while `LapSaver` writes `index = boundary.lapNumber`, which `LapDetector` sets to `sample.lapCount - 1`. Arm capture on a later lap and View lap opens a lap that does not exist.

Decision. One id. After the flatten, the directory name is the id. `LapSummary` carries it, `Screen` carries it, and `SessionStore` lap methods take it. `CaptureState.Saved` carries it, which removes the hardcoded index.

### 2. Replay readiness

Problem. Whether a lap's video is final is spread across `videoFile`, `sourceFile`, `needsCut` and `leadInMs`. Two of those mean "not final", and each screen resolves the clip with a slightly different expression. `needsCut` is never set true by production code, only by tests, yet four read sites branch on it.

Evidence. `SessionStore.kt:84`, `LapPlayerScreen.kt:186`, `LapCompareScreen.kt:756`, `ImportSyncScreen.kt:186`. `LapSummary` derives `hasReplay` and `replayPending` separately from the same fields.

Decision. Fixed media names inside the lap directory. `replay.mp4` means attached, `source.mp4` means pending, neither means none. The record keeps only `leadInMs` and `syncOffsetMs`. Delete `needsCut`, `videoFile` and `sourceFile`.

### 3. Track identity

Problem. The identifier resolves a lap to an official circuit id plus a display name, then capture discards the id. The library groups by the trimmed lowercase label and uses that for fastest-per-track, the ghost, filters and the compare gate, so a rename or a different candidate label splits one circuit.

Evidence. `TrackCatalog.kt:24` returns only `analyze(samples).name`. `LapSummary.kt:54` defines `trackKey` from the label. `LibraryScreen.kt:224` and `LapPlayerScreen.kt:175` gate compare on it. `SessionStore.rename` rewrites it.

Decision. Persist `trackId` and `trackLabel`, key grouping on the id, fall back to the normalised label only when no signature matched. ADR 0002.

### 4. Session versus lap

Problem. The spec still describes a session as a capture run owning laps, but capture has been one lap at a time since `4a61138`, and every new session holds exactly one lap. What remains session-scoped is the circuit, the car, the creation time, and a set of fields from the raw-recording era: `rawFile`, `audioFile`, `keepRaw`, `rawDeleted`, `recordingStartNanos`, and a second `syncOffsetMs` that nothing reads.

Evidence. `docs/specs/2026-11-10-pocket-sim-engineer.md:56` ("one session containing one lap; legacy multi-lap sessions remain readable"). `SessionWriter.kt:52` writes the session offset into each lap. `LapSaver.kt:34` writes `rawFile = ""`. The lap record is also written twice, to `session.json:laps[]` and to `laps/lap-NNN.json`, and `LibraryArchive.importStaged` (`:174-186`) resolves the disagreement in favour of the file copy without documenting it.

Decision. Flatten. One directory per lap, `files/laps/<lapId>/`. Re-adding a capture run later is a nullable `captureRunId` per lap. ADR 0001.

### 5. Clip to lap time

Problem. `PlaybackCursor` maps clip time to lap time using `leadInMs` and `syncOffsetMs`. `ClipSync` maps the same two ways using an anchor plus lap duration. The player uses the first, the sync screen and trim use the second, and both clamp silently. Nothing states which one governs a lap that was re-synced.

Evidence. `PlaybackCursor.kt:8-17`, `ClipSync.kt:10-14`, `TrimPlan.forSync`.

Recommendation. One `LapClock(leadInMs, syncOffsetMs, lapTimeMs)` owning both directions, with `ClipSync`'s anchor expressed as `clock.clipMs(lapTimeMs)`. Worth doing when the sync screen is next touched, not before.

### 6. Steering availability

Problem. `Sample.steering` defaults to `0f`, which means both straight and not recorded, and three readers guess with two thresholds.

Evidence. `Sample.kt:24`, `CompareReadout.kt:55` (`0.001f`), `TelemetryCharts.kt:102` (`0.001f`), `LapEvents.kt:50` (`0.01f`) with a curvature fallback at `:86`.

Decision. Nullable steering in the sample file, codec version 2, availability asked once per lap. ADR 0003.

### 7. Time bases

Problem. `Sample.timeNanos` is a monotonic tick, `LapMetadata.startNanos` and `endNanos` persist that same tick basis, and `LapSummary.createdAt` is wall-clock session time. All three are named like timestamps or durations. `LapBoundary.lapTimeMs` also has two sources, the game's report and the measured span, and the detector silently prefers the report.

Evidence. `LapDetector.kt:59-67`. `SessionStore.kt:97` gives every lap its session's `createdAt`, so `LapSort.Newest` cannot order two laps of a legacy session on their own.

Decision, partly taken. The lap record renames the pair to `startTickNanos` and `endTickNanos`, and adds `lapTimeSource` (`Reported` or `Measured`). Still open: a per-lap wall clock, and replacing the detector's `currentLapStartNanos` plus `currentLapStartObserved` with a `CurrentLap` sealed state.

### 8. Library row and filters

Problem. `LapSummary` is a second definition of a lap, carrying derived booleans and the session's creation time. Event filtering takes a `String?` and matches the literal `"none"` to mean untagged, while `EventTag.kind` is a `String` whose legal values live in a comment.

Evidence. `LapSummary.kt:36-37`, `LibraryScreen.kt:335` and `:731`, `EventTag.kt:9`.

Recommendation. Make the row a projection of the lap record once the flatten lands, with no derived video flags. Replace `eventKind` with `sealed EventFilter { Any, None, Kind(EventKind) }` and `EventTag.kind` with `EventKind`. Small change, compile-time safety.

### 9. Glossary and code drift

- **Session** described a container the app no longer uses. Removed from the glossary, kept only as a legacy layout name in ADR 0001.
- **Place alignment** exists in the glossary as `TrackProgress.align` and `Alignment`, and nowhere is it called that. The glossary entry now names the code path.
- **Ghost** is `LoadedLap` in code now; ghost and reference remain roles, not types. Done in `refactor/event-kind-and-loaded-lap`.
- New terms added: lap id, pending replay, track, track id, track label, sample, steering angle.

### Deliberately not doing

- Value objects inside `Sample` (`Position`, `Tyres`, `Pedals`). The codec and interpolation would both change for readability alone.
- Renaming `SessionStore` and the `sessions/` directory without the layout migration. Superseded by the flatten.
- Dropping `packetId` from the sample file. It is wire dedupe only, but the saving is four bytes a sample and the codec change is already taken by ADR 0003. Fold it in there if convenient, otherwise leave it.
- An ADR for exact seeks. The reasoning already sits in `AGENTS.md` and `DESIGN.md`, and nothing is about to change it.

Note: spec `docs/specs/2026-11-10-pocket-sim-engineer.md` §9.2 and §9.5 still describe the legacy session layout and have not been rewritten for the flatten.

### Follow-up order agreed

1. Docs: ADR 0001, 0002, 0003 and the glossary. Done.
2. Branch `refactor/lap-record`: identity, flatten, migration, archive format 2, replay state, app call sites, `AGENTS.md` and spec §9.2 and §9.5. Done (merged).
3. Branch for `trackId`. Done (merged).
4. Branch for nullable steering and codec version 2. Open.
5. Opportunistic: `LapClock`, detector state, `EventKind`, `LoadedLap`. Open.
