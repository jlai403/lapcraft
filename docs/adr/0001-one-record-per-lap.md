# One record per lap

Status: accepted, implemented

The app captures, reviews, exports and deletes one lap at a time, but storage still follows the screen-capture era: `sessions/<id>/session.json` holds a list of laps, each lap's record is written twice, to the session file and to `laps/lap-NNN.json`, and a lap can only be addressed as a `(sessionId, lapIndex)` pair. Store each lap as its own directory, `files/laps/<lapId>/`, holding `lap.json`, `lap.gt7lap`, and media under fixed names. The directory name is the lap id. `sessions/` is migrated once by `LayoutMigration` (convert-and-delete); no `SessionStore` or `SessionWriter` remains. Legacy decode types are quarantined under `core/session/legacy/`.

## Considered options

Keep the container and enforce a one-lap invariant. Rejected because the pair identity, the second copy of every lap record, and the shared-file rules all remain, and none of them describes anything the app does. A capture run that groups laps is a real concept only if the app ever captures several laps at once; a nullable `captureRunId` on each lap restores it without a container.

Identify a lap by a uuid inside a session object stored on disk. Rejected because the directory name is already unique and stable, and a second id can disagree with it.

## Consequences

- Media names are fixed: `replay.mp4` is the attached replay, `source.mp4` is an imported clip awaiting sync, `raw.mp4` is a clip written before this change. The record therefore drops `videoFile`, `sourceFile` and `needsCut`, and the replay state is what the directory contains.
- First launch migrates existing sessions. A one-lap session keeps its id as the lap id; a multi-lap session splits into `<sessionId>-l<NNN>`, the rule `LibraryArchive.archiveId` already applies. Each lap takes the session's track label, car and creation time.
- Legacy media maps by state. `needsCut` becomes `source.mp4`; a video file equal to `rawFile` becomes `raw.mp4`; any other video file becomes `replay.mp4` with its lead-in and sync offset.
- A downgrade cannot read the new layout. The migration stages each lap directory, renames it into place, and removes the old session directory only once every lap in it exists, but there is no reverse migration.
- Library archives move to format 2, one entry per lap id. Format 1 stays importable, since existing exports are the only copy some users have.
- `SessionMetadata` and `SessionWriter` leave the public API. `SessionStore` remains only as the legacy session-shaped reader used by import and migration; new captures go through `LapStore`. Session fields with no reader in the current app (`audioFile`, `keepRaw`, `recordingStartNanos`) are read only by the migration and never written again.
