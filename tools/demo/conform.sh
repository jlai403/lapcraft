#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TOOLS="$ROOT/tools/demo"
BEATS="$TOOLS/beats"
WORK="$TOOLS/.build"
PY="$TOOLS/demo.py"
FONTDIR="$ROOT/app/src/main/res/font"
ICON="$ROOT/docs/branding/ic_launcher_playstore.png"
PRE_ROLL=0.5
TURN1_TRIM=4.5
DURATION=26

CAPTIONS="$TOOLS/captions.srt"
LAYOUT=wide
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

layout_vars() {
  if [[ "$LAYOUT" == tall ]]; then
    W=1080
    H=1920
    PORTRAIT_SCALE="-2:1400"
    LANDSCAPE_SCALE="1000:-2"
    CAPTION_FONT=54
    LANDSCAPE_FONT=44
    MARGIN=48
    CAPTION_ALIGN=2
    CAPTION_MARGINV=90
    CHAPTER_MARGINV=70
    ICON_SIZE=440
    TITLE_SIZE=96
    TAGLINE_SIZE=48
    ICON_Y=-160
    TITLE_Y=150
    TAGLINE_Y=250
    OUTPUT="$TOOLS/master-vertical.mp4"
  else
    W=1920
    H=1080
    PORTRAIT_SCALE="-2:1000"
    LANDSCAPE_SCALE="1200:-2"
    CAPTION_FONT=46
    LANDSCAPE_FONT=32
    MARGIN=80
    CAPTION_ALIGN=4
    CAPTION_MARGINV=0
    CHAPTER_MARGINV=54
    ICON_SIZE=360
    TITLE_SIZE=88
    TAGLINE_SIZE=40
    ICON_Y=-90
    TITLE_Y=150
    TAGLINE_Y=230
    OUTPUT="$TOOLS/master.mp4"
  fi
}

segment() {
  local take_name="$1" name="$2" start="$3" length="$4" layout="$5" label="$6"
  local take="$BEATS/$take_name.mkv"
  local trim="${7:-$PRE_ROLL}"
  if [[ ! -f "$take" && -f "$TOOLS/legacy/capture-$take_name.mp4" ]]; then
    # Archived take: already framed on the device, no scrcpy pre-roll to drop.
    take="$TOOLS/legacy/capture-$take_name.mp4"
    [[ -n "${7:-}" ]] || trim=0
  fi
  [[ -f "$take" || $DRY_RUN -eq 1 ]] || die "missing $take, run capture.sh first"

  local scale font_size margin align margin_v
  if [[ "$layout" == landscape ]]; then
    scale="$LANDSCAPE_SCALE"
    font_size="$LANDSCAPE_FONT"
    margin="$MARGIN"
    # The landscape stage fills the height, so its caption sits underneath
    # instead of in the side gutter, where it would run onto the frame.
    align=1
    margin_v=90
    [[ "$LAYOUT" == tall ]] && align=2
  else
    scale="$PORTRAIT_SCALE"
    font_size="$CAPTION_FONT"
    margin="$MARGIN"
    align="$CAPTION_ALIGN"
    margin_v="$CAPTION_MARGINV"
  fi

  local srt="$WORK/$name.srt"
  run python3 "$PY" slice-srt "$CAPTIONS" "$start" "$length" "$srt" \
    --font-size "$font_size" --margin "$margin" --chapter "$label" \
    --width "$W" --height "$H" --align "$align" \
    --margin-v "$margin_v" --chapter-margin "$CHAPTER_MARGINV"
  local ass="$WORK/$name.ass"

  local fg
  if [[ "$name" == 02 ]]; then
    fg="[fg]scale=$scale,crop=w=iw*0.94:h=ih*0.94:x='(in_w-out_w)*t/$length':y='(in_h-out_h)*t/$length'[phone];"
  else
    fg="[fg]scale=$scale[phone];"
  fi

  local filter
  filter="[0:v]trim=start=$trim:duration=$length,setpts=PTS-STARTPTS,tpad=stop_mode=clone:stop_duration=$length,trim=duration=$length,fps=30,split=2[bg][fg];"
  filter+="[bg]scale=$W:$H:force_original_aspect_ratio=increase,crop=$W:$H,boxblur=24:2,eq=brightness=-0.4:saturation=0.35[back];"
  filter+="$fg"
  filter+="[phone]drawbox=x=0:y=0:w=iw:h=ih:color=0x2C3944@0.9:t=2,split=2[main][shadowsrc];"
  filter+="[shadowsrc]gblur=sigma=14,colorchannelmixer=aa=0.5[shadow];"
  filter+="[back][shadow]overlay=(W-w)/2+10:(H-h)/2+16[withshadow];"
  filter+="[withshadow][main]overlay=(W-w)/2:(H-h)/2[stage];"
  filter+="color=c=0x34D399:s=${W}x4:r=30:d=$length,scale=w='iw*t/$length':h=4:eval=frame[pb];"
  filter+="[stage][pb]overlay=0:H-4[withbar];"
  filter+="[withbar]subtitles=filename='$ass':fontsdir='$FONTDIR'[out]"

  run ffmpeg -y -i "$take" -filter_complex "$filter" -map '[out]' \
    -c:v libx264 -crf 18 -preset medium -pix_fmt yuv420p -an "$WORK/$name.mp4"
  printf "file '%s'\n" "$WORK/$name.mp4" >> "$WORK/list.txt"
}

compare_trim() {
  local take="$BEATS/04.mkv" cut
  if (( DRY_RUN )) || [[ ! -f "$take" ]]; then
    printf '%s' "$PRE_ROLL"
    return
  fi
  cut="$(ffmpeg -v info -i "$take" -vf "select='gt(scene,0.25)',showinfo" -an -f null - 2>&1 |
    grep -oE 'pts_time:[0-9.]+' | head -1 | cut -d: -f2)"
  if [[ -z "$cut" ]]; then
    log "no tab switch found in 04, using $PRE_ROLL"
    printf '%s' "$PRE_ROLL"
    return
  fi
  # Four seconds of replay before the cut to the telemetry tab.
  awk -v cut="$cut" 'BEGIN { t = cut - 4.0; if (t < 0) t = 0; printf "%.3f", t }'
}

hero() {
  local title_y="(h-text_h)/2+$TITLE_Y"
  local tag_y="(h-text_h)/2+$TAGLINE_Y"
  local icon_y="(H-h)/2+$ICON_Y"
  local filter
  filter="[0:v]scale=$ICON_SIZE:-1,format=rgba,split=2[icon][glowsrc];"
  filter+="[glowsrc]scale=iw*1.4:ih*1.4,gblur=sigma=50,colorchannelmixer=aa=0.30[glow];"
  filter+="[1:v][glow]overlay=(W-w)/2:$icon_y[gl];"
  filter+="[gl][icon]overlay=(W-w)/2:$icon_y[withicon];"
  filter+="[withicon]drawtext=fontfile='$FONTDIR/geist_semibold.ttf':text='LapCraft':fontsize=$TITLE_SIZE:fontcolor=0xF8FAFC:x=(w-text_w)/2:y=$title_y:expansion=none,"
  filter+="drawtext=fontfile='$FONTDIR/geist_regular.ttf':text='Find the tenth.':fontsize=$TAGLINE_SIZE:fontcolor=0x94A3B8:x=(w-text_w)/2:y=$tag_y:expansion=none,"
  filter+="fade=t=in:st=0:d=0.25,fade=t=out:st=1.75:d=0.25[out]"
  run ffmpeg -y -loop 1 -t 2 -i "$ICON" -f lavfi -i "color=c=0x0D1114:s=${W}x${H}:r=30:d=2" \
    -filter_complex "$filter" -map '[out]' -shortest \
    -c:v libx264 -crf 18 -preset medium -pix_fmt yuv420p -an "$WORK/hero.mp4"
  printf "file '%s'\n" "$WORK/hero.mp4" >> "$WORK/list.txt"
}

usage() {
  log "usage: conform.sh [--layout wide|tall] [--music file.wav] [--dry-run]"
  log "writes master.mp4 for wide, master-vertical.mp4 for tall"
}

while (( $# > 0 )); do
  case "$1" in
    --layout) LAYOUT="${2:-}"; shift 2 ;;
    --music) MUSIC="${2:-}"; shift 2 ;;
    --dry-run) DRY_RUN=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) die "unknown option $1" ;;
  esac
done
[[ "$LAYOUT" == wide || "$LAYOUT" == tall ]] || die "layout must be wide or tall"
layout_vars

if (( ! DRY_RUN )); then
  command -v ffmpeg >/dev/null || die "ffmpeg is not on PATH"
  [[ -f "$ICON" ]] || die "missing $ICON"
fi

mkdir -p "$WORK"
: > "$WORK/list.txt"

hero
segment 01 01 2 5 portrait "01 CAPTURE"
segment 02 02 7 4 portrait "02 REPLAY"
segment 02 03 11 6 portrait "03 ANALYZE" "$TURN1_TRIM"
segment 04 04 17 9 landscape "04 COMPARE" "$(compare_trim)"

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
