# An event tag snapshots its window

Status: accepted

The library groups laps under their event and keeps lapsed events in a collapsed `Past events` section. Deciding whether an event has lapsed needs its start and end, and neither survives on its own: an `EventTag` stored only `id, kind, label, series, round, carClass`, and the event cache replaces all `gridstats` rows on every refresh, so a Daily Race from three weeks ago is gone from the schedule while its lap keeps the tag. The GT GridStats API exposes only this week, next week and the live Time Trials; it has no past-schedule endpoint, and polling it for history would burn the metered quota.

The tag now carries `startMs` and `endMs` copied from the cached event at the moment it is assigned. Dates are optional with defaults, so existing `lap.json` files decode unchanged and no schema version moves.

## Considered options

**Cache membership only.** Rejected: it works for auto tags until the cache replaces the row, and it misclassifies every manual tag as lapsed because a manual id never appears in the schedule.

**Poll the GridStats API for history.** Rejected: no endpoint exists for past dailies or Time Trials, and the request is metered.

**Keep past events in `EventCache`.** Rejected for lapsed detection (the tag already knows), though it would also work; the window belongs with the lap that used the event.

## Consequences

- `EventTag.status(nowMs, cacheIds)` returns `Current`, `Upcoming`, `Lapsed` or `Unknown`: a tag with a window uses it; an auto tag without one falls back to whether its id is still cached; a manual tag without one stays `Unknown` and is grouped with current events.
- Laps tagged before this change keep working: current-week auto tags stay current through cache membership, and older ones are marked lapsed once the cache drops them. Their header detail falls back to the label, lap count and best lap until they are re-tagged.
- `core/events/EventGroups.kt` groups the library: current events newest first, then `No event` laps, then lapsed events newest-ended first; manual ids key on the normalised label so repeated manual names share a section. The chosen `LapSort` still orders laps inside each section.
- The tag is self-contained, so importing a library archive on another device preserves which events have lapsed.

Implementation: `EventTag.startMs`/`endMs`, `CachedEvent.toTag()` and `EventMatcher.match` copy them, `EventStatus.kt`, `EventGroups.kt`, `LibraryEventSection.kt`.
