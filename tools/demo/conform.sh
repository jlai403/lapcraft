#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TOOLS="$ROOT/tools/demo"
BEATS="$TOOLS/beats"
WORK="$TOOLS/.build"
PY="$TOOLS/demo.py"
FONT="$ROOT/app/src/main/res/font/geist_regular.ttf"
FONTDIR="$ROOT/app/src/main/res/font"
PRE_ROLL=0.6

trim_for() {
  echo "$PRE_ROLL"
}

CAPTIONS="$TOOLS/captions.srt"
OUTPUT="$TOOLS/master.mp4"
DURATION=24
SHORT=0
DRY_RUN=0
MUSIC=""

log() { printf '%s\n' "$*"; }
die() { printf 'error: %s\n' "$*" >&2; exit 1; }

run() {
  if (( DRY_RUN )); then
    printf 'DRY'
    printf ' %q' "$@"
    printf '\n'
    return
  fi
  "$@"
}

segment() {
  local name="$1" start="$2" length="$3" layout="$4" label="$5"
  local take="$BEATS/$name.mkv"
  [[ -f "$take" || $DRY_RUN -eq 1 ]] || die "missing $take, run capture.sh first"

  local scale font_size margin fg
  if [[ "$layout" == landscape ]]; then
    scale="1200:-2"
    font_size=32
    margin=60
  else
    scale="-2:1000"
    font_size=46
    margin=80
  fi

  local trim
  trim="$(trim_for "$name")"

  local srt="$WORK/$name.srt"
  run python3 "$PY" slice-srt "$CAPTIONS" "$start" "$length" "$srt" \
    --font-size "$font_size" --margin "$margin" --chapter "$label"
  local ass="$WORK/$name.ass"

  if [[ "$name" == 02 ]]; then
    fg="[fg]scale=$scale,crop=w=iw*0.94:h=ih*0.94:x='(in_w-out_w)*t/$length':y='(in_h-out_h)*t/$length'[phone];"
  else
    fg="[fg]scale=$scale[phone];"
  fi

  local filter
  filter="[0:v]trim=start=$trim:duration=$length,setpts=PTS-STARTPTS,tpad=stop_mode=clone:stop_duration=$length,trim=duration=$length,fps=30,split=2[bg][fg];"
  filter+="[bg]scale=1920:1080:force_original_aspect_ratio=increase,crop=1920:1080,boxblur=24:2,eq=brightness=-0.4:saturation=0.35[back];"
  filter+="$fg"
  filter+="[phone]drawbox=x=0:y=0:w=iw:h=ih:color=0x2C3944@0.9:t=2,split=2[main][shadowsrc];"
  filter+="[shadowsrc]gblur=sigma=14,colorchannelmixer=aa=0.5[shadow];"
  filter+="[back][shadow]overlay=(W-w)/2+10:(H-h)/2+16[withshadow];"
  filter+="[withshadow][main]overlay=(W-w)/2:(H-h)/2[stage];"
  filter+="color=c=0x34D399:s=1920x4:r=30:d=$length,scale=w='iw*t/$length':h=4:eval=frame[pb];"
  filter+="[stage][pb]overlay=0:H-4[withbar];"
  filter+="[withbar]subtitles=filename='$ass':fontsdir='$FONTDIR'[out]"

  run ffmpeg -y -i "$take" -filter_complex "$filter" -map '[out]' \
    -c:v libx264 -crf 18 -preset medium -pix_fmt yuv420p -an "$WORK/$name.mp4"
  printf "file '%s'\n" "$WORK/$name.mp4" >> "$WORK/list.txt"
}

usage() {
  log "usage: conform.sh [--short] [--music file.wav] [--dry-run]"
  log "writes tools/demo/master.mp4, or master-15s.mp4 with --short"
}

while (( $# > 0 )); do
  case "$1" in
    --short) SHORT=1; shift ;;
    --music) MUSIC="${2:-}"; shift 2 ;;
    --dry-run) DRY_RUN=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) die "unknown option $1" ;;
  esac
done

if (( ! DRY_RUN )); then
  command -v ffmpeg >/dev/null || die "ffmpeg is not on PATH"
  [[ -f "$FONT" ]] || die "missing $FONT"
fi

mkdir -p "$WORK"
: > "$WORK/list.txt"

if (( SHORT )); then
  CAPTIONS="$TOOLS/captions-15s.srt"
  DURATION=15
  OUTPUT="$TOOLS/master-15s.mp4"
  segment 01 0 3.8 portrait "01 CAPTURE"
  segment 02 3.8 3.7 portrait "02 REPLAY"
  segment 03 7.5 3.5 portrait "03 ANALYZE"
  segment 04 11 4 landscape "04 COMPARE"
else
  segment 01 0 5 portrait "01 CAPTURE"
  segment 02 5 4 portrait "02 REPLAY"
  segment 03 9 6 portrait "03 ANALYZE"
  segment 04 15 9 landscape "04 COMPARE"
fi

run ffmpeg -y -f concat -safe 0 -i "$WORK/list.txt" -c copy "$WORK/joined.mp4"

if [[ -n "$MUSIC" ]]; then
  run ffmpeg -y -i "$WORK/joined.mp4" -stream_loop -1 -i "$MUSIC" \
    -filter_complex "[1:a]atrim=0=$DURATION,volume=0.2[a]" \
    -map 0:v -map '[a]' -c:v copy -c:a aac -t "$DURATION" -movflags +faststart "$OUTPUT"
else
  run ffmpeg -y -i "$WORK/joined.mp4" -f lavfi -i "anullsrc=r=48000:cl=stereo" \
    -map 0:v -map 1:a -c:v copy -c:a aac -t "$DURATION" -movflags +faststart "$OUTPUT"
fi

log "wrote $OUTPUT"
