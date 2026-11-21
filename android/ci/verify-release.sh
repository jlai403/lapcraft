#!/usr/bin/env bash
# Verify that a published release APK is not a debug build.
# Usage: ci/verify-release.sh path/to/app-stable.apk [path/to/app-preview.apk ...]
set -euo pipefail

if [[ $# -eq 0 ]]; then
  echo "usage: $0 <apk>..." >&2
  exit 2
fi

# SHA-256 of the public AOSP debug certificate (android/ci/debug.keystore).
DEBUG_KEY_SHA256="44d7752796ba72b17d7103f8fb4eb452877cd70e678f48d799b302bf47160cb9"

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
build_tools=""
if [[ -n "$sdk" && -d "$sdk/build-tools" ]]; then
  build_tools="$(find "$sdk/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)"
fi
apksigner="${build_tools:+$build_tools/apksigner}"
aapt2="${build_tools:+$build_tools/aapt2}"
[[ -x "$apksigner" ]] || apksigner="$(command -v apksigner || true)"
[[ -x "$aapt2" ]] || aapt2="$(command -v aapt2 || true)"
if [[ ! -x "$apksigner" || ! -x "$aapt2" ]]; then
  echo "apksigner/aapt2 not found; set ANDROID_HOME or add build-tools to PATH" >&2
  exit 2
fi

tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT

for apk in "$@"; do
  echo "checking $apk"
  if ! "$apksigner" verify --print-certs "$apk" >"$tmp" 2>&1; then
    cat "$tmp" >&2
    exit 1
  fi
  if grep -q "$DEBUG_KEY_SHA256" "$tmp"; then
    echo "FAIL: $apk is signed with the public debug key" >&2
    exit 1
  fi
  if "$aapt2" dump xmltree --file AndroidManifest.xml "$apk" 2>/dev/null \
      | grep -q 'debuggable(0x0101000f)=true'; then
    echo "FAIL: $apk has android:debuggable=true" >&2
    exit 1
  fi
  echo "  OK"
done
echo "all release APKs verified"
