#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TOOLS="$ROOT/tools/demo"
BEATS="$TOOLS/beats"
PY="$TOOLS/demo.py"

BEAT=""
DRY_RUN=0
TOUCHES_REQUESTED=""
REFERENCE="1:"
ADB_SERIAL=""

log() { printf '%s\n' "$*"; }
die() { printf 'error: %s\n' "$*" >&2; exit 1; }

adb() {
  if [[ -n "$ADB_SERIAL" ]]; then
    command adb -s "$ADB_SERIAL" "$@"
  else
    command adb "$@"
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
tap() { ui tap "$1"; }
wait_ui() { ui wait "$1"; }
swipe() { ui swipe "$1" --dx "$2" --duration "$3"; }

preflight() {
  local touch_value=0
  [[ "$TOUCHES" == on ]] && touch_value=1
  if [[ -z "${ADB:-}" ]]; then
    ADB="$(command -v adb || true)"
    [[ -n "$ADB" ]] && export ADB
  fi
  export SDL_VIDEODRIVER=dummy
  run adb shell input keyevent 224
  run adb shell settings put system pointer_location 0
  run adb shell settings put system show_touches "$touch_value"
  run adb shell settings put global stay_awake 1
  run adb shell settings put system screen_off_timeout 600000
  run adb shell cmd notification set_dnd on || true
  run mkdir -p "$BEATS"
}

record_start() {
  local file="$BEATS/$1.mkv"
  log "recording $file"
  if (( DRY_RUN )); then
    printf 'DRY scrcpy --no-window --record=%q --no-audio --max-fps=60 --bit-rate=80M\n' "$file"
    pause 0.6
    return
  fi
  if [[ -n "$ADB_SERIAL" ]]; then
    scrcpy --no-window --serial "$ADB_SERIAL" --record="$file" --no-audio --max-fps=60 --bit-rate=80M >/dev/null 2>&1 &
  else
    scrcpy --no-window --record="$file" --no-audio --max-fps=60 --bit-rate=80M >/dev/null 2>&1 &
  fi
  CAPTURE_PID=$!
  sleep 0.6
}

record_stop() {
  if (( DRY_RUN )); then
    log "stop recording"
    return
  fi
  kill -INT "$CAPTURE_PID" 2>/dev/null || true
  wait "$CAPTURE_PID" 2>/dev/null || true
  CAPTURE_PID=""
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

ensure_player() {
  if (( DRY_RUN )); then
    printf 'DRY ensure player\n'
    return
  fi
  if python3 "$PY" wait "Frame ruler" --timeout 1 >/dev/null 2>&1; then return; fi
  ensure_library
  wait_ui "^[0-9]:[0-9]{2}\\.[0-9]{3}"
  tap "^[0-9]:[0-9]{2}\\.[0-9]{3}"
  wait_ui "Frame ruler"
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

beat_01() {
  wait_ui "packets/s"
  record_start 01
  pause 3.9
  tap "^Library$"
  pause 5.1
  record_stop
}

beat_02() {
  ensure_player
  record_start 02
  pause 0.4
  tap "^Play$"
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
  tap "$REFERENCE"
  wait_ui "^Video$"
  record_start 04
  pause 0.4
  tap "Next brake point"
  pause 0.4
  tap "^Play$"
  pause 3.6
  tap "^Pause$"
  pause 0.2
  swipe "Frame ruler" -200 800
  pause 3.6
  record_stop
}

usage() {
  log "usage: capture.sh --beat 01|02|02b|03|04|all [--touches on|off] [--reference '1:49'] [--serial ip:port] [--dry-run]"
  log "beats: 01 Record and Library, 02 player, 02b sync screen, 03 ruler, 04 compare"
}

while (( $# > 0 )); do
  case "$1" in
    --beat) BEAT="${2:-}"; shift 2 ;;
    --touches) TOUCHES_REQUESTED="${2:-}"; shift 2 ;;
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
  command -v adb >/dev/null || die "adb is not on PATH"
  command -v scrcpy >/dev/null || die "scrcpy is not on PATH"
  [[ -f "$PY" ]] || die "missing $PY"
fi

for beat in $BEAT; do
  [[ "$beat" == 01 || "$beat" == 02 || "$beat" == 02b || "$beat" == 03 || "$beat" == 04 ]] ||
    die "unknown beat $beat"
done

for beat in $BEAT; do
  TOUCHES="${TOUCHES_REQUESTED:-$(default_touches "$beat")}"
  log "beat $beat, touches $TOUCHES"
  preflight
  "beat_$beat"
done
