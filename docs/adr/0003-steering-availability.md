# A lap records whether it has steering data

Status: accepted

GT7 reports the steering angle in some packets only, so a lap can have no steering at all. `Sample.steering` defaults to `0f`, which means both "straight" and "not recorded", and three readers guess with two different thresholds. A genuinely straight lap is misread as unrecorded and silently gets a curvature fallback in the lap events, and the steering wheel is hidden. The sample record makes the absence explicit: `steering` is nullable, written as NaN in the `.gt7lap` sample so the record size does not change, and the UI asks the lap once whether any sample carries an angle.

## Considered options

A `hasSteering` flag on the lap record. Rejected because the flag can disagree with the samples it describes, and the samples already know.

A numeric sentinel such as `-2f`. Rejected because it repeats the same ambiguity with a different number and no type change.

## Consequences

- The telemetry codec moves to version 2. Version 1 files cannot distinguish a straight lap from an unrecorded one, so the loader keeps today's value test for them; the next save writes version 2.
- The curvature fallback in the lap events becomes an explicit "no steering recorded" branch rather than a threshold test on values.
- Interpolating a sample window handles a missing angle on either side, and never invents one.
