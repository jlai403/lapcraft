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

CAPTIONS="$TOOLS/captions.srt"
OUTPUT="$TOOLS/master.mp4"
DURATION=40
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

  local scale chapter_x chapter_y font_size margin
  if [[ "$layout" == landscape ]]; then
    scale="1200:-2"
    chapter_x=60
    chapter_y=60
    font_size=30
    margin=60
  else
    scale="-2:1000"
    chapter_x=80
    chapter_y=80
    font_size=44
    margin=80
  fi

  local srt="$WORK/$name.srt"
  run python3 "$PY" slice-srt "$CAPTIONS" "$start" "$length" "$srt"

  local filter
  filter="[0:v]trim=start=$PRE_ROLL:duration=$length,setpts=PTS-STARTPTS,fps=30,split=2[bg][fg];"
  filter+="[bg]scale=1920:1080:force_original_aspect_ratio=increase,crop=1920:1080,boxblur=24:2,eq=brightness=-0.25:saturation=0.6[back];"
  filter+="[fg]scale=$scale[phone];"
  filter+="[back][phone]overlay=(W-w)/2:(H-h)/2[stage];"
  filter+="[stage]subtitles=filename='$srt':fontsdir='$FONTDIR':force_style='FontName=Geist,FontSize=$font_size,PrimaryColour=&H00FCFAF8,Outline=0,Shadow=0,Alignment=4,MarginL=$margin,MarginV=0'[subs];"
  filter+="[subs]drawtext=fontfile='$FONT':text='$label':fontsize=26:fontcolor=0x94A3B8:x=$chapter_x:y=$chapter_y:expansion=none[out]"

  run ffmpeg -y -i "$take" -filter_complex "$filter" -map '[out]' \
    -c:v libx264 -crf 18 -preset medium -pix_fmt yuv420p -an "$WORK/$name.mp4"
  printf "file '%s'\n" "$WORK/$name.mp4" >> "$WORK/list.txt"
}

endcard() {
  local filter
  filter="drawtext=fontfile='$FONT':text='LapCraft':fontsize=104:fontcolor=0xF8FAFC:x=(w-text_w)/2:y=(h-text_h)/2-90:expansion=none,"
  filter+="drawtext=fontfile='$FONT':text='Free, open source':fontsize=34:fontcolor=0x94A3B8:x=(w-text_w)/2:y=(h-text_h)/2+50:expansion=none"
  run ffmpeg -y -f lavfi -i "color=c=0x0D1114:s=1920x1080:r=30:d=4" -vf "$filter" \
    -c:v libx264 -crf 18 -preset medium -pix_fmt yuv420p -an "$WORK/end.mp4"
  printf "file '%s'\n" "$WORK/end.mp4" >> "$WORK/list.txt"
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
  segment 01 0 9 portrait "01 CAPTURE"
  segment 02 9 4.5 portrait "02 REPLAY"
  segment 02b 13.5 4.5 portrait "02 REPLAY"
  segment 03 18 9 portrait "03 ANALYZE"
  segment 04 27 9 landscape "04 COMPARE"
  endcard
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
