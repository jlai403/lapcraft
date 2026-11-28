#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)/site"
MASTER="$ROOT/../tools/demo/master.mp4"
BEAT02="$ROOT/../tools/demo/beats/02.mkv"
BEAT04="$ROOT/../tools/demo/beats/04.mkv"

ASSETS="$ROOT/src/assets"
MEDIA="$ROOT/public/media"

mkdir -p "$ASSETS" "$MEDIA"

FORCE=0
CHECK=0
for arg in "$@"; do
  case "$arg" in
    --force) FORCE=1 ;;
    --check) CHECK=1 ;;
  esac
done

sz() { stat -c%s "$1" 2>/dev/null || stat -f%z "$1"; }

if [ "$CHECK" -eq 0 ]; then
  # scrub-portrait.mp4: 8s all-intra from the raw vertical take, 540x1212, no audio
  if [ "$FORCE" -eq 1 ] || [ ! -f "$MEDIA/scrub-portrait.mp4" ]; then
    echo "scrub-portrait.mp4"
    ffmpeg -y -hide_banner -loglevel error \
      -ss 1 -t 8 -i "$BEAT02" \
      -an -vf "fps=30,scale=540:-2" \
      -c:v libx264 -profile:v high -pix_fmt yuv420p \
      -g 1 -keyint_min 1 -sc_threshold 0 \
      -crf 26 -movflags +faststart \
      "$MEDIA/scrub-portrait.mp4"
    ffmpeg -y -hide_banner -loglevel error -ss 0 -i "$MEDIA/scrub-portrait.mp4" -frames:v 1 -q:v 3 "$MEDIA/scrub-portrait-poster.jpg"
  fi

  # demo.mp4: full 26s, 1280, CRF 24 (used by the Watch the full demo dialog)
  if [ "$FORCE" -eq 1 ] || [ ! -f "$MEDIA/demo.mp4" ]; then
    echo "demo.mp4"
    ffmpeg -y -hide_banner -loglevel error \
      -i "$MASTER" -an -vf "scale=1280:-2" \
      -c:v libx264 -crf 24 -preset slow -movflags +faststart -pix_fmt yuv420p \
      "$MEDIA/demo.mp4"
    ffmpeg -y -hide_banner -loglevel error -ss 2 -i "$MASTER" -frames:v 1 -q:v 3 "$MEDIA/demo-poster.jpg"
  fi

  STILLS="$ROOT/../tools/demo/stills"

  # stills
  for name in lap-player charts; do
    out="$ASSETS/$name.png"
    if [ "$FORCE" -eq 1 ] || [ ! -f "$out" ]; then
      echo "$name.png"
      if [ -f "$STILLS/$name.png" ]; then
        ffmpeg -y -hide_banner -loglevel error -i "$STILLS/$name.png" -frames:v 1 -vf "scale=720:-2" "$out"
      else
        case "$name" in
          lap-player) ffmpeg -y -hide_banner -loglevel error -ss 3.5 -i "$BEAT02" -frames:v 1 -vf "scale=720:-2" "$out" ;;
          charts) ffmpeg -y -hide_banner -loglevel error -ss 8.0 -i "$BEAT02" -frames:v 1 -vf "scale=720:-2" "$out" ;;
        esac
      fi
    fi
  done

  # compare stills at 1440 wide
  for name in compare-video compare-telemetry; do
    out="$ASSETS/$name.png"
    if [ "$FORCE" -eq 1 ] || [ ! -f "$out" ]; then
      echo "$name.png"
      if [ -f "$STILLS/$name.png" ]; then
        ffmpeg -y -hide_banner -loglevel error -i "$STILLS/$name.png" -frames:v 1 -vf "scale=1440:-2" "$out"
      else
        ffmpeg -y -hide_banner -loglevel error -ss 2 -i "$BEAT04" -frames:v 1 -vf "scale=1440:-2" "$out"
      fi
    fi
  done

  # capture still from the Record screen
  if [ "$FORCE" -eq 1 ] || [ ! -f "$ASSETS/capture.png" ]; then
    echo "capture.png"
    if [ -f "$STILLS/capture.png" ]; then
      ffmpeg -y -hide_banner -loglevel error -i "$STILLS/capture.png" -frames:v 1 -vf "scale=540:-2" "$ASSETS/capture.png"
    else
      CAPTURE="$ROOT/../tools/demo/legacy/capture-01.mp4"
      if [ -f "$CAPTURE" ]; then
        ffmpeg -y -hide_banner -loglevel error -ss 2 -i "$CAPTURE" -frames:v 1 -vf "scale=540:-2" "$ASSETS/capture.png"
      fi
    fi
  fi

  # icon exports
  ICON="$ROOT/../docs/branding/ic_launcher_playstore.png"
  for spec in "512:icon-512.png" "128:icon-128.png" "180:apple-touch-icon.png" "32:favicon-32.png"; do
    IFS=: read -r size name <<< "$spec"
    out="$ROOT/public/$name"
    if [ "$FORCE" -eq 1 ] || [ ! -f "$out" ]; then
      echo "$name"
      ffmpeg -y -hide_banner -loglevel error -i "$ICON" -vf "scale=${size}:${size}" "$out"
    fi
  done
fi

# Check mode
if [ "$CHECK" -eq 1 ]; then
  echo "Checking asset budgets..."
  fail=0
  for f in scrub-portrait.mp4; do
    s=$(sz "$MEDIA/$f"); echo "  $f: $s bytes (max 3145728)"; [ "$s" -le 3145728 ] || fail=1
  done
  for f in "$ASSETS"/*.png; do
    s=$(sz "$f"); echo "  $(basename $f): $s bytes (max 800000)"; [ "$s" -le 800000 ] || fail=1
  done
  total=$(ffprobe -v error -count_frames -select_streams v:0 -show_entries stream=nb_read_frames -of csv=p=0 "$MEDIA/scrub-portrait.mp4")
  keys=$(ffprobe -v error -count_frames -select_streams v:0 -show_entries stream=nb_read_frames -of csv=p=0 -skip_frame nokey "$MEDIA/scrub-portrait.mp4")
  echo "  scrub-portrait frames: $total, keyframes: $keys"; [ "$total" = "$keys" ] || fail=1
  dur=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$MEDIA/scrub-portrait.mp4")
  echo "  scrub-portrait duration: $dur"
  exit $fail
fi

echo "Done."