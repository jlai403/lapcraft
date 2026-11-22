# LapCraft

Android app for Gran Turismo 7 that captures one lap of telemetry and pairs it with a single-lap replay for review.

## Lap capture

**Lap**:
One measured GT7 lap: its telemetry, lap time, optional replay, and the driver and event assigned to it. It is the unit the app stores, exports and deletes.
_Avoid_: run, stint, entry, session

**Lap id**:
The name of a lap's own directory, and the only way the app refers to a lap.
_Avoid_: session id, lap key, lap reference

**Capture**:
Recording a lap's telemetry, saved at the next start/finish crossing.
_Avoid_: record, log

## Telemetry

**Sample**:
One 60 Hz telemetry reading, holding position, speed, pedals, gear, engine and tyre values.
_Avoid_: frame, tick, datum

**Steering angle**:
The wheel input GT7 reports for some packets, normalised to -1..1 with positive to the left. A lap either recorded it or did not, and a missing angle is not a straight wheel.
_Avoid_: steering value, wheel angle, input

## Library

**Library archive**:
A zip of the selected laps, one directory per lap, each with its clip and telemetry.
_Avoid_: backup file, export bundle

**Import (library)**:
Merging laps from an archive, skipping any id that already exists.
_Avoid_: restore, sync

## Video

**Clip**:
A video file brought in from the PS App or a share sheet, before it is synced.
_Avoid_: video, file

**Replay**:
The single-lap video attached to a lap.
_Avoid_: video, clip once attached

**Pending replay**:
An imported clip waiting to be synced to its lap; the lap has video work outstanding until it is trimmed and attached.
_Avoid_: source, raw clip, unprocessed video

**Import**:
Copying a clip into app storage.
_Avoid_: upload, download

**Sync**:
Aligning a clip to its lap with a lap start and a confirmed start frame.
_Avoid_: align, calibrate

**Lap start**:
The frame where the car crosses the line and the lap begins. The clip runs one lap forward from it. When the HUD timer is counting up (0:00.xxx) on that frame, typing the time pulls the start onto the line; when it shows a lap time or the fastest-lap flash, the frame alone is the lap start.
_Avoid_: anchor, sync point

**Start frame**:
Resolved clip time of the lap's first frame: `lap start − offset`. The replay's first frame, after the timer's offset is applied.
_Avoid_: clip start, sync start

**Sync offset**:
The HUD lap time shown on the anchor frame, typed as seconds.milliseconds (e.g. 0.019). It measures how far past the line the frame landed, and is folded into the stored lead-in.
_Avoid_: frame nudge, residual

**Lead-in**:
Clip time before the start frame, kept when the keyframe cut lands early.
_Avoid_: pre-roll, video start offset

**Trim**:
Cutting a synced clip to one lap, from the start frame to the finish frame.
_Avoid_: cut, crop

**Attach**:
Saving a trimmed clip as the lap's replay.
_Avoid_: add, link

## Track

**Track**:
The circuit a lap was driven on.
_Avoid_: circuit, course, map

**Track id**:
The identity of a track, taken from the signature it matched, or empty when the app could not identify it.
_Avoid_: track key, circuit id, slug

**Track label**:
The user-facing track name; editable, and never an identity.
_Avoid_: track name, display name

**Track progress**:
Cumulative distance along the racing line, normalised to 0-100 % of the lap; the charts' x axis.
_Avoid_: position, lap percentage, distance axis

**Place alignment**:
Matching two laps by position along lap 1's driven path, so the same corner is compared when the lines differ.
_Avoid_: distance alignment, track position, sync

## Review

**Playhead**:
The video position within a clip or replay, measured in clip time.
_Avoid_: cursor, position

**Clip time**:
Elapsed time from the clip's first frame.
_Avoid_: video time

**Lap time**:
Elapsed time from one start/finish crossing to the next; the telemetry timeline. GT7 reports it at the crossing, and when that report is missing or implausible the app measures the sample span.
_Avoid_: race time, lap duration

**Telemetry cursor**:
The lap-time position drawn on the charts and track map.
_Avoid_: playhead, marker

**Ghost**:
The fastest lap on the same track, drawn as a dashed grey line on the track map and summarised in the expanded header.
_Avoid_: reference lap, compare

**Primary lap**:
The first lap opened or picked for a comparison; it owns the clock and the cursor.
_Avoid_: main lap, base lap

**Reference lap**:
The second lap in a comparison. It is seeked to the primary's track progress, frame exactly, and never plays.
_Avoid_: ghost, comparison lap, secondary lap

**Compare**:
Reviewing two same-track laps side by side under one playback control.
_Avoid_: versus screen, split view

**Frame step**:
Moving the playhead by exactly one video frame.
_Avoid_: scrub, nudge

**Frame ruler**:
The draggable scale that moves the playhead a frame at a time.
_Avoid_: jog wheel, dial

**Transport**:
The play/pause and ±10-second controls.
_Avoid_: player, controls

## Driver

**Driver**:
The person who drove a lap.
_Avoid_: player, user

**Driver profile**:
A driver's PSN ID, GT nickname, country flag, and the DR/SR rating cached from GT GridStats.
_Avoid_: account, avatar, card

**Owner**:
The driver using this install; the default for new captures.
_Avoid_: me, main user, primary profile

**Profile book**:
The local set of the owner and every cached driver profile.
_Avoid_: contacts, address book

**Assigned / unassigned lap**:
Whether a lap has a driver profile.
_Avoid_: set/unset, linked

## Event

**Event**:
A dated GT7 competition a lap was driven in: a Daily Race, a Time Trial, or a championship round.
_Avoid_: race type, session

**Event tag**:
The event recorded on a lap, kept as a snapshot (id, kind, label, window) so it survives a schedule refresh.
_Avoid_: label, category

**Event window**:
The start and end timestamps copied onto a tag when it is assigned, so the library can tell a current event from a lapsed one after the cache drops it. Legacy and manual tags have no window.
_Avoid_: schedule, dates

**Lapsed event**:
An event whose window has ended. Lapsed events collect under `Past events` in the library. An auto tag without a window counts as lapsed once its id leaves the event cache. Untagged laps captured before the current ISO week (and manual tags whose newest lap predates it) also count as lapsed.
_Avoid_: expired, old event

**Auto tag**:
An event tag assigned by the matcher from time, track and car rather than chosen by the driver.
_Avoid_: inferred tag, guess

**Week**:
The seven-day rotation a Daily Race belongs to, named by its ISO number (`W47`) and the Monday it started.
_Avoid_: round, week of year

**Rating**:
A driver's DR and SR values cached from GT GridStats, with the date they were fetched.
_Avoid_: rank, grade

## Tyre

**Tyre compound**:
The compound of the tyres on the car for a lap, recorded as a two-letter GT7 code (CH, RM, CS, ...) with a hardness tier (hard, medium, soft) that drives the badge colour. GT7's telemetry does not carry it, so it is always entered by the driver, either as the current tyres in Record or per lap in Edit lap.
_Avoid_: tire type, rubber

## Lap setup

**Traction control (TCS)**:
The GT7 traction control level for a lap, 0 (off) to 5. GT7's telemetry carries only whether TCS is intervening, never the setting, so the driver enters it in Record or per lap in Edit lap.
_Avoid_: assist level, TCS active

**Brake balance**:
The car's brake balance setting for a lap, -5 to +5 as shown in GT7. It is not in the telemetry, so the driver enters it like the tyre compound.
_Avoid_: brake bias
