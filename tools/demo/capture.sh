#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TOOLS="$ROOT/tools/demo"
BEATS="$TOOLS/beats"
PY="$TOOLS/demo.py"

BEAT=""
DRY_RUN=0
TOUCHES_REQUESTED=""
LAP="1:36.131"
REFERENCE="1:34.208"
ADB_SERIAL=""

log() { printf '%s\n' "$*"; }
die() { printf 'error: %s\n' "$*" >&2; exit 1; }

adb() {
  local binary="${ADB:-adb}"
  if [[ -n "$ADB_SERIAL" ]]; then
    command "$binary" -s "$ADB_SERIAL" "$@"
  else
    command "$binary" "$@"
  fi
}

run() {
  if (( DRY_RUN )); then
    printf 'DRY'
    printf ' %q' "$@"
    printf '\n'
    return
  fi
  "$@"
}

ui() { run python3 "$PY" "$@"; }
pause() { log "wait ${1}s"; (( DRY_RUN )) || sleep "$1"; }
tap() { ui tap "$1" "${@:2}"; }
tap_row() { ui tap "$1" --row "${@:2}"; }

time_pattern() { printf '^%s$' "$(printf '%s' "$1" | sed 's/\./\\./g')"; }
wait_ui() { ui wait "$1" "${@:2}"; }
swipe() { ui swipe "$1" --dx "$2" --duration "$3"; }

chrome_visible() {
  python3 "$PY" wait "^Record$" --min-y 2000 --timeout 1 >/dev/null 2>&1 ||
    python3 "$PY" wait "^All$" --timeout 1 >/dev/null 2>&1
}

ensure_chrome() {
  if (( DRY_RUN )); then
    printf 'DRY ensure app in foreground\n'
    return
  fi
  if chrome_visible; then return; fi
  local attempt
  for attempt in 1 2 3; do
    run adb shell input keyevent 4
    sleep 0.5
    if chrome_visible; then return; fi
  done
  run adb shell am start -n io.github.jlai403.lapcraft/.app.MainActivity
  sleep 1.0
  if chrome_visible; then return; fi
  die "Lapcraft is not in the foreground"
}

set_touches() {
  local touch_value=0
  [[ "$TOUCHES" == on ]] && touch_value=1
  run adb shell settings put system show_touches "$touch_value"
}

preflight() {
  if [[ -z "${ADB:-}" ]]; then
    ADB="$(command -v adb || true)"
    [[ -n "$ADB" ]] && export ADB
  fi
  export SDL_VIDEODRIVER=dummy
  run adb shell input keyevent 224
  run adb shell wm dismiss-keyguard
  run adb shell am start -n io.github.jlai403.lapcraft/.app.MainActivity
  ensure_chrome
  run adb shell settings put system pointer_location 0
  run adb shell settings put global stay_awake 1
  run adb shell settings put system screen_off_timeout 600000
  run adb shell cmd notification set_dnd on || true
  run mkdir -p "$BEATS"
}

beat_limit() {
  case "$1" in
    02|04) echo 14 ;;
    *) echo 12 ;;
  esac
}

record_start() {
  local file="$BEATS/$1.mkv" limit
  limit="$(beat_limit "$1")"
  log "recording $file for ${limit}s"
  if (( DRY_RUN )); then
    printf 'DRY scrcpy --no-window --record=%q --no-audio --max-fps=60 --video-bit-rate=80M --time-limit=%s\n' "$file" "$limit"
    pause 0.6
    return
  fi
  local logfile="$BEATS/scrcpy.log"
  if [[ -n "$ADB_SERIAL" ]]; then
    scrcpy --no-window --serial "$ADB_SERIAL" --record="$file" --no-audio --max-fps=60 --video-bit-rate=80M --time-limit="$limit" >"$logfile" 2>&1 &
  else
    scrcpy --no-window --record="$file" --no-audio --max-fps=60 --video-bit-rate=80M --time-limit="$limit" >"$logfile" 2>&1 &
  fi
  CAPTURE_PID=$!
  sleep 1.2
  if ! kill -0 "$CAPTURE_PID" 2>/dev/null; then
    cat "$logfile" >&2
    die "scrcpy exited during startup"
  fi
}

record_stop() {
  if (( DRY_RUN )); then
    log "stop recording"
    return
  fi
  local waited=0
  while kill -0 "$CAPTURE_PID" 2>/dev/null && (( waited < 20 )); do
    sleep 1
    waited=$((waited + 1))
  done
  if kill -0 "$CAPTURE_PID" 2>/dev/null; then
    kill -TERM "$CAPTURE_PID" 2>/dev/null || true
    sleep 1
    kill -KILL "$CAPTURE_PID" 2>/dev/null || true
  fi
  wait "$CAPTURE_PID" 2>/dev/null || true
  CAPTURE_PID=""
  log "recording closed"
}

default_touches() {
  case "$1" in
    03|04) echo on ;;
    *) echo off ;;
  esac
}

ensure_library() {
  if (( DRY_RUN )); then
    printf 'DRY ensure Library\n'
    return
  fi
  if python3 "$PY" wait "^All$" --timeout 1 >/dev/null 2>&1; then return; fi
  local attempt
  for attempt in 1 2 3 4; do
    run adb shell input keyevent 4
    sleep 0.4
    if python3 "$PY" wait "^All$" --timeout 1 >/dev/null 2>&1; then return; fi
  done
  die "could not reach Library"
}

scroll_to() {
  local pattern="$1" y1="${2:-1900}" y2="${3:-600}" attempt
  local size w h rotation
  size="$(adb shell wm size | sed 's/.*: //')"
  w="${size%x*}"
  h="${size#*x}"
  rotation="$(adb shell settings get system user_rotation | tr -dc '0-9')"
  rotation="${rotation:-0}"
  local cx
  if [[ "$rotation" == 1 || "$rotation" == 3 ]]; then
    y1=$(( h * 80 / 100 ))
    y2=$(( h * 25 / 100 ))
    cx=$(( h / 2 ))
  else
    cx=$(( w / 2 ))
  fi
  if (( DRY_RUN )); then
    printf 'DRY scroll to %s\n' "$pattern"
    return
  fi
  for attempt in 1 2 3 4 5 6; do
    if python3 "$PY" wait "$pattern" --timeout 1 >/dev/null 2>&1; then return; fi
    log "scroll $attempt for $pattern"
    run adb shell input swipe "$cx" "$y1" "$cx" "$y2" 500
    sleep 0.5
  done
  die "could not find $pattern after scrolling"
}

ensure_player() {
  if (( DRY_RUN )); then
    printf 'DRY ensure player on %s\n' "$LAP"
    return
  fi
  local pattern
  pattern="$(time_pattern "$LAP")"
  if python3 "$PY" wait "$pattern" --timeout 1 >/dev/null 2>&1 &&
     python3 "$PY" wait "Frame ruler" --timeout 1 >/dev/null 2>&1; then return; fi
  ensure_library
  scroll_to "$pattern"
  tap_row "$pattern"
  wait_ui "Frame ruler"
  log "primary lap $LAP is open"
}

tap_if_present() {
  local pattern="$1"
  if (( DRY_RUN )); then
    printf 'DRY tap if present %s\n' "$pattern"
    return 0
  fi
  if python3 "$PY" wait "$pattern" --timeout 1 >/dev/null 2>&1; then
    python3 "$PY" tap "$pattern"
    return 0
  fi
  return 1
}

control_xy() {
  if (( DRY_RUN )); then
    printf '540 2306'
    return
  fi
  python3 "$PY" coords "$1" "${@:2}"
}

tap_xy() {
  run adb shell input tap $1 $2
}

screen_hash() {
  adb exec-out screencap -p | md5sum | cut -d' ' -f1
}

# Playback is confirmed by the picture changing, not by the play/pause label:
# uiautomator dumps lag behind a tap and a stale label looks like a failed start,
# which is what made the script tap Play a second time and pause the replay.
screen_moving() {
  local first second
  first="$(screen_hash)"
  sleep 0.6
  second="$(screen_hash)"
  [[ "$first" != "$second" ]]
}

ensure_playing() {
  local attempt
  for attempt in 1 2 3; do
    if (( DRY_RUN )); then
      printf 'DRY ensure playing\n'
      return
    fi
    if screen_moving; then return; fi
    log "playback still, tapping play (attempt $attempt)"
    tap_if_present "^Play$" || log "no play control found"
    sleep 0.4
  done
  die "playback did not start"
}

restart_app() {
  if (( DRY_RUN )); then
    printf 'DRY restart app\n'
    return
  fi
  run adb shell am force-stop io.github.jlai403.lapcraft
  sleep 0.6
  run adb shell am start -n io.github.jlai403.lapcraft/.app.MainActivity
  sleep 2.0
  ensure_chrome
}

set_rotation() {
  local target="$1"
  if (( DRY_RUN )); then
    printf 'DRY rotate to %s\n' "$target"
    return
  fi
  run adb shell settings put system accelerometer_rotation 0
  run adb shell settings put system user_rotation "$target"
  sleep 1.5
}

set_speed() {
  local target="$1" attempt
  if (( DRY_RUN )); then
    printf 'DRY set speed %s\n' "$target"
    return
  fi
  for attempt in 1 2 3; do
    if python3 "$PY" wait "^$target$" --timeout 1 >/dev/null 2>&1; then return; fi
    tap "^\\+$"
    sleep 0.4
  done
  die "could not set playback speed to $target"
}

ensure_streaming() {
  if (( DRY_RUN )); then
    printf 'DRY ensure streaming\n'
    return
  fi
  if python3 "$PY" wait "^Streaming$" --timeout 1 >/dev/null 2>&1; then return; fi
  tap_if_present "^(Connect|Disconnect)$" || true
  if python3 "$PY" wait "^Streaming$" --timeout 8 >/dev/null 2>&1; then return; fi
  die "GT7 is not sending telemetry: start a race or time trial on the PS5, then rerun --beat 01"
}

beat_01() {
  if (( ! DRY_RUN )); then
    set_rotation 0
    ensure_library
    wait_ui "packets/s" --timeout 5 || {
      tap "^Record$" --min-y 2000
      wait_ui "packets/s" --timeout 20
    }
    ensure_streaming
  fi
  record_start 01
  pause 1.2
  tap "^Record$" --min-y 2000
  if (( ! DRY_RUN )); then wait_ui "packets/s" --timeout 20; fi
  pause 2.0
  tap "^Library$" --min-y 2000
  if (( ! DRY_RUN )); then wait_ui "^All$" --timeout 5; fi
  pause 2.0
  record_stop
}

beat_02() {
  restart_app
  set_rotation 0
  ensure_player
  set_speed "2×"
  local play_xy
  play_xy="$(control_xy "^Play$")"
  record_start 02
  tap_xy $play_xy
  ensure_playing
  pause 4.0
  tap_xy $play_xy
  pause 0.3
  swipe "Frame ruler" -200 900
  pause 2.0
  swipe "Frame ruler" -140 700
  pause 2.0
  record_stop
}

beat_04() {
  restart_app
  ensure_player
  tap "^Compare$"
  wait_ui "Compare with"
  # Compare is landscape-only: on a portrait-locked display the screen is
  # letterboxed into a scaled window and every tap lands somewhere else.
  set_rotation 1
  tap_row "$(time_pattern "$REFERENCE")"
  log "reference lap $REFERENCE selected"
  wait_ui "^Video$"
  pause 2.0
  local play_xy brake_xy telemetry_xy video_xy
  play_xy="$(control_xy "^Play$")"
  brake_xy="$(control_xy "Next brake point")"
  telemetry_xy="$(control_xy "^Telemetry$")"
  video_xy="$(control_xy "^Video$")"
  # Lay out the map and charts once before the take: on a cold switch they take
  # about two seconds to compose, which would land on the wrong frame.
  tap_xy $telemetry_xy
  pause 2.0
  tap_xy $video_xy
  pause 1.0
  record_start 04
  tap_xy $brake_xy
  pause 0.5
  tap_xy $play_xy
  ensure_playing
  pause 3.8
  tap_xy $telemetry_xy
  pause 5.0
  record_stop
  set_rotation 0
}

usage() {
  log "usage: capture.sh --beat 01|02|04|all [--touches on|off] [--lap '1:36.131'] [--reference '1:35.842'] [--serial ip:port] [--dry-run]"
  log "beats: 01 Library and Record, 02 2x replay and ruler, 04 compare turn 1 to turn 2"
}

while (( $# > 0 )); do
  case "$1" in
    --beat) BEAT="${2:-}"; shift 2 ;;
    --touches) TOUCHES_REQUESTED="${2:-}"; shift 2 ;;
    --lap) LAP="${2:-}"; shift 2 ;;
    --reference) REFERENCE="${2:-}"; shift 2 ;;
    --serial) ADB_SERIAL="${2:-}"; shift 2 ;;
    --dry-run) DRY_RUN=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) die "unknown option $1" ;;
  esac
done

[[ -n "$BEAT" ]] || { usage; die "missing --beat"; }
if [[ "$BEAT" == all ]]; then BEAT="01 02 04"; fi
export ADB_SERIAL

if (( ! DRY_RUN )); then
  command -v "${ADB:-adb}" >/dev/null || die "adb is not on PATH or in ADB"
  command -v scrcpy >/dev/null || die "scrcpy is not on PATH"
  [[ -f "$PY" ]] || die "missing $PY"
fi

for beat in $BEAT; do
  [[ "$beat" == 01 || "$beat" == 02 || "$beat" == 04 ]] ||
    die "unknown beat $beat"
done

preflight

for beat in $BEAT; do
  TOUCHES="${TOUCHES_REQUESTED:-$(default_touches "$beat")}"
  log "beat $beat, touches $TOUCHES"
  set_touches
  run adb shell input keyevent 224
  run adb shell wm dismiss-keyguard
  run adb shell am start -n io.github.jlai403.lapcraft/.app.MainActivity
  ensure_chrome
  "beat_$beat"
done
