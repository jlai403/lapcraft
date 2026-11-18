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
REFERENCE="1:35.842"
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
  for attempt in 1 2 3 4 5 6; do
    run adb shell input keyevent 4
    sleep 0.5
    if chrome_visible; then return; fi
  done
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
    02|02b) echo 7 ;;
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
  if (( DRY_RUN )); then
    printf 'DRY scroll to %s\n' "$pattern"
    return
  fi
  for attempt in 1 2 3 4 5 6; do
    if python3 "$PY" wait "$pattern" --timeout 1 >/dev/null 2>&1; then return; fi
    log "scroll $attempt for $pattern"
    run adb shell input swipe 540 "$y1" 540 "$y2" 500
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
    return
  fi
  if python3 "$PY" wait "$pattern" --timeout 1 >/dev/null 2>&1; then
    python3 "$PY" tap "$pattern"
  else
    log "no $pattern control, skipping"
  fi
}

pause_playback() {
  if (( DRY_RUN )); then
    printf 'DRY pause playback\n'
    return
  fi
  if python3 "$PY" wait "^Pause$" --timeout 1 >/dev/null 2>&1; then
    python3 "$PY" tap "^Pause$"
  fi
}

ensure_record() {
  if (( DRY_RUN )); then
    printf 'DRY ensure Record screen\n'
    ui wait "packets/s"
    return
  fi
  tap "^Record$" --min-y 2000
  if ! python3 "$PY" wait "packets/s" --timeout 20 >/dev/null 2>&1; then
    die "no telemetry on the Record screen. Connect to the console first."
  fi
}

beat_01() {
  ensure_record
  record_start 01
  pause 2.2
  tap "^Library$" --min-y 2000
  if (( ! DRY_RUN )); then wait_ui "^All$" --timeout 5; fi
  pause 3.0
  record_stop
}

beat_02() {
  ensure_player
  record_start 02
  pause 0.4
  tap_if_present "^Play$"
  pause 4.1
  record_stop
}

beat_02b() {
  log "Open the lap, tap Add clip, pick the clip, and stop on the sync screen."
  if (( DRY_RUN )); then
    log "DRY waiting for Enter"
  else
    read -r -p "Press Enter when the sync screen is on screen: " _
  fi
  wait_ui "Use this frame"
  record_start 02b
  pause 4.5
  record_stop
}

beat_03() {
  ensure_player
  record_start 03
  pause 0.4
  tap "Next brake point"
  pause 0.8
  pause_playback
  pause 0.8
  swipe "Frame ruler" -160 900
  pause 2.5
  swipe "Frame ruler" -120 600
  pause 3.0
  record_stop
}

beat_04() {
  ensure_player
  tap "^Compare$"
  wait_ui "Compare with"
  local pattern
  pattern="$(time_pattern "$REFERENCE")"
  scroll_to "$pattern" 1400 700
  tap_row "$pattern"
  log "reference lap $REFERENCE selected"
  wait_ui "^Video$"
  record_start 04
  pause 0.4
  tap "Next brake point"
  pause 0.4
  tap_if_present "^Play$"
  pause 3.6
  tap_if_present "^Pause$"
  pause 0.2
  swipe "Frame ruler" -200 800
  pause 3.6
  record_stop
}

usage() {
  log "usage: capture.sh --beat 01|02|02b|03|04|all [--touches on|off] [--lap '1:36.131'] [--reference '1:35.824'] [--serial ip:port] [--dry-run]"
  log "beats: 01 Record and Library, 02 player, 02b sync screen, 03 ruler, 04 compare"
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
if [[ "$BEAT" == all ]]; then BEAT="01 02 02b 03 04"; fi
export ADB_SERIAL

if (( ! DRY_RUN )); then
  command -v "${ADB:-adb}" >/dev/null || die "adb is not on PATH or in ADB"
  command -v scrcpy >/dev/null || die "scrcpy is not on PATH"
  [[ -f "$PY" ]] || die "missing $PY"
fi

for beat in $BEAT; do
  [[ "$beat" == 01 || "$beat" == 02 || "$beat" == 02b || "$beat" == 03 || "$beat" == 04 ]] ||
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
