#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ASSET="$ROOT/android/app/src/main/assets/seed-laps.zip"
BUDGET=27
CHECK=0
INPUT=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --check) CHECK=1; shift ;;
        --budget) BUDGET="$2"; shift 2 ;;
        --help|-h)
            cat <<EOF
Usage: $0 <export.zip> [--budget MB]
       $0 --check [--budget MB]
Generates android/app/src/main/assets/seed-laps.zip from an exported library zip,
or validates the committed asset with --check.
EOF
            exit 0 ;;
        *) if [[ -z "$INPUT" ]]; then INPUT="$1"; shift; else echo "unexpected arg: $1" >&2; exit 2; fi ;;
    esac
done

if [[ $CHECK -eq 1 ]]; then
    python3 - "$ASSET" "$BUDGET" <<'PY'
import json, os, sys, zipfile
asset, budget_mb = sys.argv[1], int(sys.argv[2])
budget = budget_mb * 1024 * 1024
assert os.path.isfile(asset), f"missing asset: {asset}"
with zipfile.ZipFile(asset) as z:
    names = z.namelist()
    assert "library.json" in names, "missing library.json"
    manifest = json.loads(z.read("library.json"))
    assert manifest.get("format") == 2, f"bad format: {manifest.get('format')}"
    assert manifest.get("laps") == 2, f"expected 2 laps, got {manifest.get('laps')}"
    lap_ids = {n.split("/")[1] for n in names if n.startswith("library/") and n.count("/") >= 2}
    assert len(lap_ids) == 2, f"expected 2 lap dirs, got {len(lap_ids)}: {lap_ids}"
    for lid in sorted(lap_ids):
        assert lid.startswith("seed-"), f"bad lap id: {lid}"
        rec = json.loads(z.read(f"library/{lid}/lap.json"))
        assert rec.get("driverPsnId") is None, f"{lid} still has a driver PSN"
        assert rec.get("event") is None, f"{lid} still has an event tag"
        assert rec.get("trackId") == "6e378b", f"{lid} bad trackId"
        assert rec.get("lapTimeMs", 0) > 0, f"{lid} bad lapTimeMs"
        assert f"library/{lid}/lap.gt7lap" in names, f"{lid} missing telemetry"
        telem = z.read(f"library/{lid}/lap.gt7lap")
        assert telem[:4] == b"G7LP", f"{lid} bad telemetry header"
        assert f"library/{lid}/replay.mp4" in names, f"{lid} missing replay"
        mp4_size = z.getinfo(f"library/{lid}/replay.mp4").file_size
        assert mp4_size < 14 * 1024 * 1024, f"{lid} replay too large: {mp4_size}"
    assert "profiles.json" not in names, "profiles.json must not ship"
    for n in names:
        assert not n.endswith("source.mp4"), f"source.mp4 must not ship: {n}"
        assert not n.endswith("raw.mp4"), f"raw.mp4 must not ship: {n}"
    total = sum(z.getinfo(n).file_size for n in names)
    assert total <= budget, f"asset too large: {total} > {budget}"
    assert z.testzip() is None
print(f"OK: {len(lap_ids)} laps, {total/1024/1024:.2f} MB, under {budget_mb} MB budget")
PY
    exit 0
fi

[[ -n "$INPUT" ]] || { echo "usage: $0 <export.zip> | --check" >&2; exit 2; }
[[ -f "$INPUT" ]] || { echo "input not found: $INPUT" >&2; exit 2; }

python3 - "$INPUT" "$ASSET" "$BUDGET" <<'PY'
import json, os, shutil, subprocess, sys, tempfile, zipfile

src, asset, budget_mb = sys.argv[1], sys.argv[2], int(sys.argv[3])
budget = budget_mb * 1024 * 1024
tmp = tempfile.mkdtemp()
try:
    in_root = os.path.join(tmp, "in")
    os.makedirs(in_root)
    with zipfile.ZipFile(src) as z:
        z.extractall(in_root)
    laps_root = os.path.join(in_root, "library")
    assert os.path.isdir(laps_root), "export has no library/ directory"
    lap_dirs = sorted(d for d in os.listdir(laps_root)
                      if os.path.isdir(os.path.join(laps_root, d)))
    assert len(lap_dirs) == 2, f"expected exactly 2 laps, got {len(lap_dirs)}"

    records = []
    for d in lap_dirs:
        with open(os.path.join(laps_root, d, "lap.json")) as f:
            records.append((d, json.load(f)))
    records.sort(key=lambda x: x[1].get("lapTimeMs", 0))

    seed_root = os.path.join(tmp, "seed")
    os.makedirs(os.path.join(seed_root, "library"))
    entries = []
    total = 0
    for idx, (orig_dir, rec) in enumerate(records, start=1):
        sid = f"seed-suzuka-{idx:02d}"
        target = os.path.join(seed_root, "library", sid)
        os.makedirs(target)
        new_rec = dict(rec)
        new_rec["driverPsnId"] = None
        new_rec["event"] = None
        new_rec["capturedAt"] = "2026-11-01T00:00:00Z"
        if not new_rec.get("trackId"):
            new_rec["trackId"] = "6e378b"
        if not new_rec.get("trackLabel"):
            new_rec["trackLabel"] = "Suzuka Circuit"
        with open(os.path.join(target, "lap.json"), "w") as f:
            json.dump(new_rec, f, separators=(",", ":"))

        shutil.copyfile(os.path.join(laps_root, orig_dir, "lap.gt7lap"),
                        os.path.join(target, "lap.gt7lap"))

        src_mp4 = os.path.join(laps_root, orig_dir, "replay.mp4")
        dst_mp4 = os.path.join(target, "replay.mp4")
        assert os.path.isfile(src_mp4), f"missing replay.mp4 for {orig_dir}"
        subprocess.run([
            "ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
            "-i", src_mp4,
            "-vf", "scale=1280:-2,fps=60",
            "-c:v", "libx265", "-preset", "medium", "-crf", "34",
            "-g", "12", "-keyint_min", "12", "-sc_threshold", "0",
            "-tag:v", "hvc1", "-pix_fmt", "yuv420p",
            "-c:a", "aac", "-b:a", "96k",
            "-movflags", "+faststart", "-fps_mode", "cfr",
            dst_mp4,
        ], check=True)

        lj = os.path.getsize(os.path.join(target, "lap.json"))
        te = os.path.getsize(os.path.join(target, "lap.gt7lap"))
        mp = os.path.getsize(dst_mp4)
        entries.append((sid, lj, te, mp))
        total += lj + te + mp
        print(f"{sid}: lap.json {lj}  telemetry {te}  replay {mp/1024/1024:.2f} MB")

    os.makedirs(os.path.dirname(asset), exist_ok=True)
    with zipfile.ZipFile(asset, "w") as z:
        manifest = {"format": 2, "exportedAt": "2026-11-01T00:00:00Z",
                    "laps": len(entries), "bytes": total}
        z.writestr("library.json", json.dumps(manifest, separators=(",", ":")))
        for sid, *_ in entries:
            target = os.path.join(seed_root, "library", sid)
            z.write(os.path.join(target, "lap.json"),
                    f"library/{sid}/lap.json", zipfile.ZIP_DEFLATED)
            z.write(os.path.join(target, "lap.gt7lap"),
                    f"library/{sid}/lap.gt7lap", zipfile.ZIP_DEFLATED)
            info = zipfile.ZipInfo(f"library/{sid}/replay.mp4")
            info.compress_type = zipfile.ZIP_STORED
            with open(os.path.join(target, "replay.mp4"), "rb") as f:
                z.writestr(info, f.read())

    print(f"asset: {asset}  total {total/1024/1024:.2f} MB")
    if total > budget:
        print(f"ERROR: over budget {budget/1024/1024:.0f} MB", file=sys.stderr)
        sys.exit(1)
finally:
    shutil.rmtree(tmp, ignore_errors=True)
PY