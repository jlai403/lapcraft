# Track identity is the circuit id, not the label

Status: accepted

`TrackIdentifier` matches a lap against the bundled circuit signatures and returns an official circuit id with a display name, but capture stored only the name, and the library groups, filters and gates compare on the trimmed lowercase label. Renaming a lap, or landing on a different candidate name, splits one circuit into two groups and disables compare and the ghost lap. A lap record now carries `circuitId` and `circuitLabel`: the identifier's official id when a signature matched, empty otherwise. Grouping, fastest-per-track, compare eligibility and the ghost key on `circuitId`. The label stays user-editable and is used for display only.

## Considered options

Normalise the label harder and keep grouping on it. Rejected because the label is the user's text; any normalisation rule still lets one circuit appear twice, and a rename remains a silent regrouping.

Identify the circuit when listing the library. Rejected because a row would need its telemetry decoded, and the answer was already known at capture.

## Consequences

- A lap that matched a signature keeps its group through renames.
- A lap with no match continues to group by its normalised label, so renaming it moves it to a new group. That is correct: the app never knew which circuit it was.
- Archives carry the circuit id, so an exported lap keeps its identity after import.
- The id is persisted, so changing this later means reading and rewriting every lap record.
