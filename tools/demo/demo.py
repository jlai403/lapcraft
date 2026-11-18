#!/usr/bin/env python3
"""adb and subtitle helpers used by the demo capture scripts."""

import argparse
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

DUMP_PATH = "/sdcard/window.xml"
TIME = r"(\d{2}):(\d{2}):(\d{2}),(\d{3})"


def adb(*args: str) -> str:
    return subprocess.check_output(["adb", *args], text=True).strip()


def bounds(node: ET.Element) -> tuple[int, int, int, int]:
    boxes = re.findall(r"\[(-?\d+),(-?\d+)\]", node.get("bounds", ""))
    if len(boxes) != 2:
        raise ValueError(f"bad bounds: {node.get('bounds')}")
    (x1, y1), (x2, y2) = [(int(x), int(y)) for x, y in boxes]
    return x1, y1, x2, y2


def find(pattern: str) -> list[ET.Element]:
    adb("shell", "uiautomator", "dump", DUMP_PATH)
    root = ET.fromstring(adb("exec-out", "cat", DUMP_PATH))
    hits = []
    for node in root.iter("node"):
        for key in ("text", "content-desc"):
            value = node.get(key, "")
            if value and re.search(pattern, value, re.IGNORECASE):
                hits.append(node)
                break
    return hits


def target(pattern: str) -> ET.Element:
    hits = find(pattern)
    if not hits:
        raise SystemExit(f"no UI node matches {pattern!r}")
    if len(hits) > 1:
        print(f"warning: {len(hits)} nodes match {pattern!r}, using the first", file=sys.stderr)
    return hits[0]


def wait_for(pattern: str, timeout: float) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if find(pattern):
            return
        time.sleep(0.4)
    raise SystemExit(f"timed out waiting for {pattern!r}")


def tap(pattern: str) -> None:
    x1, y1, x2, y2 = bounds(target(pattern))
    x, y = (x1 + x2) // 2, (y1 + y2) // 2
    adb("shell", "input", "tap", str(x), str(y))
    print(f"tap {x},{y}")


def swipe(pattern: str, dx: int, duration: int) -> None:
    x1, y1, x2, y2 = bounds(target(pattern))
    y = (y1 + y2) // 2
    middle = (x1 + x2) // 2
    start = middle + dx // 2 if dx < 0 else middle - dx // 2
    end = start + dx
    start = max(x1 + 8, min(x2 - 8, start))
    end = max(x1 + 8, min(x2 - 8, end))
    adb("shell", "input", "swipe", str(start), str(y), str(end), str(y), str(duration))
    print(f"swipe {start},{y} to {end},{y} in {duration} ms")


def to_ms(match: re.Match[str], offset: int) -> int:
    hours, minutes, seconds, millis = (int(match.group(offset + i)) for i in range(4))
    return ((hours * 60 + minutes) * 60 + seconds) * 1000 + millis


def fmt(ms: int) -> str:
    hours, rest = divmod(max(ms, 0), 3_600_000)
    minutes, rest = divmod(rest, 60_000)
    seconds, millis = divmod(rest, 1000)
    return f"{hours:02d}:{minutes:02d}:{seconds:02d},{millis:03d}"


def slice_srt(source: Path, start: float, duration: float, out: Path) -> None:
    start_ms = round(start * 1000)
    end_ms = start_ms + round(duration * 1000)
    cues = []
    for block in re.split(r"\n\s*\n", source.read_text(encoding="utf-8").strip()):
        match = re.search(TIME + r"\s*-->\s*" + TIME, block)
        if match is None:
            continue
        cue_start = to_ms(match, 1)
        cue_end = to_ms(match, 5)
        if cue_end <= start_ms or cue_start >= end_ms:
            continue
        lines = block.splitlines()
        arrow = next(i for i, line in enumerate(lines) if "-->" in line)
        text = "\n".join(lines[arrow + 1 :]).strip()
        cues.append(
            f"{len(cues) + 1}\n"
            f"{fmt(cue_start - start_ms)} --> {fmt(min(cue_end, end_ms) - start_ms)}\n"
            f"{text}"
        )
    out.write_text("\n\n".join(cues) + "\n" if cues else "", encoding="utf-8")
    print(f"wrote {out} with {len(cues)} cue(s)")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)

    wait = commands.add_parser("wait", help="wait for a UI node")
    wait.add_argument("pattern")
    wait.add_argument("--timeout", type=float, default=12)

    tap_cmd = commands.add_parser("tap", help="tap a UI node")
    tap_cmd.add_argument("pattern")

    swipe_cmd = commands.add_parser("swipe", help="drag a UI node horizontally")
    swipe_cmd.add_argument("pattern")
    swipe_cmd.add_argument("--dx", type=int, required=True)
    swipe_cmd.add_argument("--duration", type=int, default=800)

    commands.add_parser("dump", help="print the current uiautomator tree")

    cut = commands.add_parser("slice-srt", help="cut one beat out of the master subtitles")
    cut.add_argument("source", type=Path)
    cut.add_argument("start", type=float)
    cut.add_argument("duration", type=float)
    cut.add_argument("out", type=Path)

    args = parser.parse_args()
    if args.command == "wait":
        wait_for(args.pattern, args.timeout)
    elif args.command == "tap":
        tap(args.pattern)
    elif args.command == "swipe":
        swipe(args.pattern, args.dx, args.duration)
    elif args.command == "dump":
        adb("shell", "uiautomator", "dump", DUMP_PATH)
        print(adb("exec-out", "cat", DUMP_PATH))
    elif args.command == "slice-srt":
        slice_srt(args.source, args.start, args.duration, args.out)


if __name__ == "__main__":
    main()
