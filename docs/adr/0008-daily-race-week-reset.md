# Daily races run to the Sport Mode reset

A Daily Race week is not the calendar week in the feed. GT GridStats dates each daily from Monday 00:00 to Sunday 23:59:59 UTC, but GT7 keeps the races live until the Sport Mode reset at 07:00 UTC on Monday. Parsing the feed dates as instants ended every daily seven hours early: laps driven on Sunday evening were rejected by the time match, and the library marked the event lapsed before the game rolled it.

`GridStatsEventParser` now derives the window from the begin date with `sportModeWeekStart` (that date at 07:00 UTC) and `sportModeWeekEnd` (seven days minus one second). The raw end is ignored. Time Trials and championship rounds carry real instants and are unchanged.

Two follow-ons keep the fix consistent across a roll:

- `EventCache.replaceGridstats` keeps an outgoing gridstats row until its stored end, so the hours between the feed's 00:00 roll and the game's 07:00 roll still match. Rows replaced by a fresh id are dropped rather than duplicated.
- `EventRefresh.repairDailyTags` rewrites snapshotted daily tags, since a tag stores the window it was created with. An auto tag whose lap falls outside the corrected week is cleared; a manually picked tag keeps its event with the corrected window. Tags that predate windows get theirs from the `begin_date` at the end of the id. The library calls the repair on every load with the lap list it already has, so no network is needed. The GridStats refresh calls it as well.
- `EventGroups` takes the window from any lap in the group, not just the first, so one legacy tag cannot make a current event look lapsed.

## Considered options

**Parse the feed date in the device zone.** Rejected: 07:00 UTC is a global reset, so a device-zone window is wrong for every player outside the Mountain offset, and it drifts with daylight saving.

**Tolerate the gap in `EventTag.status`.** Rejected: a grace period hides the lapsed state but still shows the wrong window and still refuses the Sunday lap at tag time.

## Consequences

- Laps driven Sunday 17:00 to 24:00 in Mountain Time now match the daily they were driven in.
- A daily tag displays the same start date as before (the UTC date of the Monday), so existing labels and ids do not churn.
- Existing daily tags pick up the corrected window on the next successful GridStats refresh.
- The weekly reset hour is a single constant in `core/events/EventDates.kt`; if Polyphony moves it, one value changes.
