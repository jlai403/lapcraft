#!/usr/bin/env python3
"""adb and subtitle helpers used by the demo capture scripts."""

import argparse
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

DUMP_PATH = "/sdcard/window.xml"
TIME = r"(\d{2}):(\d{2}):(\d{2}),(\d{3})"


def adb(*args: str) -> str:
    binary = os.environ.get("ADB", "adb")
    serial = os.environ.get("ADB_SERIAL", "")
    command = [binary] + (["-s", serial] if serial else []) + list(args)
    result = subprocess.run(command, capture_output=True, text=True, timeout=15)
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or "adb failed")
    return result.stdout.strip()


def bounds(node: ET.Element) -> tuple[int, int, int, int]:
    boxes = re.findall(r"\[(-?\d+),(-?\d+)\]", node.get("bounds", ""))
    if len(boxes) != 2:
        raise ValueError(f"bad bounds: {node.get('bounds')}")
    (x1, y1), (x2, y2) = [(int(x), int(y)) for x, y in boxes]
    return x1, y1, x2, y2


def find(pattern: str, min_y: int = 0, row: bool = False) -> list[ET.Element]:
    try:
        adb("shell", "rm", "-f", DUMP_PATH)
        output = adb("shell", "uiautomator", "dump", DUMP_PATH)
        if "ERROR" in output:
            return []
        root = ET.fromstring(adb("exec-out", "cat", DUMP_PATH))
    except (subprocess.TimeoutExpired, RuntimeError, ET.ParseError):
        return []
    parents = {child: parent for parent in root.iter() for child in parent}
    hits = []
    for node in root.iter("node"):
        if min_y and bounds(node)[1] < min_y:
            continue
        matched = any(
            node.get(key, "") and re.search(pattern, node.get(key, ""), re.IGNORECASE)
            for key in ("text", "content-desc")
        )
        if not matched:
            continue
        if row:
            while node is not None and node.get("clickable") != "true":
                node = parents.get(node)
            if node is None:
                continue
        hits.append(node)
    return hits


def target(pattern: str, min_y: int = 0, row: bool = False) -> ET.Element:
    hits = find(pattern, min_y, row)
    if not hits:
        raise SystemExit(f"no UI node matches {pattern!r}")
    if len(hits) > 1:
        print(f"warning: {len(hits)} nodes match {pattern!r}, using the first", file=sys.stderr)
    return hits[0]


def wait_for(pattern: str, timeout: float, min_y: int = 0) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if find(pattern, min_y):
            return
        time.sleep(0.4)
    raise SystemExit(f"timed out waiting for {pattern!r}")


def tap(pattern: str, min_y: int = 0, row: bool = False) -> None:
    x1, y1, x2, y2 = bounds(target(pattern, min_y, row))
    x, y = (x1 + x2) // 2, (y1 + y2) // 2
    adb("shell", "input", "tap", str(x), str(y))
    print(f"tap {x},{y}")


def coords(pattern: str, min_y: int = 0, row: bool = False) -> None:
    x1, y1, x2, y2 = bounds(target(pattern, min_y, row))
    print(f"{(x1 + x2) // 2} {(y1 + y2) // 2}")


def swipe(pattern: str, dx: int, duration: int, min_y: int = 0, repeat: int = 1, gap: int = 500) -> None:
    x1, y1, x2, y2 = bounds(target(pattern, min_y))
    y = (y1 + y2) // 2
    span = x2 - x1
    for i in range(max(1, repeat)):
        if abs(dx) >= span // 2:
            if dx < 0:
                start, end = x2 - 8, x1 + 8
            else:
                start, end = x1 + 8, x2 - 8
        else:
            middle = (x1 + x2) // 2
            start = middle + dx // 2 if dx < 0 else middle - dx // 2
            end = start + dx
            start = max(x1 + 8, min(x2 - 8, start))
            end = max(x1 + 8, min(x2 - 8, end))
        adb("shell", "input", "swipe", str(start), str(y), str(end), str(y), str(duration))
        print(f"swipe {start},{y} to {end},{y} in {duration} ms ({i + 1}/{repeat})")
        if i + 1 < repeat:
            time.sleep(gap / 1000.0)


def screenshot(out: str) -> None:
    binary = os.environ.get("ADB", "adb")
    serial = os.environ.get("ADB_SERIAL", "")
    command = [binary] + (["-s", serial] if serial else []) + ["exec-out", "screencap", "-p"]
    result = subprocess.run(command, capture_output=True, timeout=30)
    if result.returncode != 0:
        raise RuntimeError(result.stderr.decode("utf-8", "replace").strip() or "screencap failed")
    Path(out).parent.mkdir(parents=True, exist_ok=True)
    Path(out).write_bytes(result.stdout)
    print(f"screenshot {out} ({len(result.stdout)} bytes)")


def to_ms(match: re.Match[str], offset: int) -> int:
    hours, minutes, seconds, millis = (int(match.group(offset + i)) for i in range(4))
    return ((hours * 60 + minutes) * 60 + seconds) * 1000 + millis


def fmt(ms: int) -> str:
    hours, rest = divmod(max(ms, 0), 3_600_000)
    minutes, rest = divmod(rest, 60_000)
    seconds, millis = divmod(rest, 1000)
    return f"{hours:02d}:{minutes:02d}:{seconds:02d},{millis:03d}"


def ass_time(ms: int) -> str:
    hours, rest = divmod(max(ms, 0), 3_600_000)
    minutes, rest = divmod(rest, 60_000)
    seconds, millis = divmod(rest, 1000)
    return f"{hours}:{minutes:02d}:{seconds:02d}.{millis // 10:02d}"


STYLE_FORMAT = (
    "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, "
    "OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, "
    "ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, "
    "Alignment, MarginL, MarginR, MarginV, Encoding"
)
EVENT_FORMAT = (
    "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text"
)


def ass_document(
    cues: list[tuple[int, int, str]],
    font_size: int,
    margin: int,
    chapter: str,
    length_ms: int,
    width: int,
    height: int,
    align: int,
    margin_v: int,
    chapter_margin: int,
) -> str:
    caption_style = (
        "Style: Caption,Geist Medium,"
        f"{font_size},&H00FCFAF8,&H00FCFAF8,&H00000000,&H00000000,"
        f"0,0,0,0,100,100,8,0,1,0,0,{align},{margin},0,{margin_v},1"
    )
    chapter_style = (
        "Style: Chapter,Geist Mono Medium,20,&H00B0BEC5,&H00B0BEC5,"
        "&H00000000,&H00000000,0,0,0,0,100,100,2,0,1,0,0,7,"
        f"{margin},0,{chapter_margin},1"
    )
    number, _, label = chapter.partition(" ")
    chapter_text = "{\\c&H0099D334&}" + number + "{\\c&H00B0BEC5&}"
    if label:
        chapter_text += " / " + label

    lines = [
        "[Script Info]",
        "ScriptType: v4.00+",
        f"PlayResX: {width}",
        f"PlayResY: {height}",
        "WrapStyle: 2",
        "ScaledBorderAndShadow: no",
        "",
        "[V4+ Styles]",
        STYLE_FORMAT,
        caption_style,
        chapter_style,
        "",
        "[Events]",
        EVENT_FORMAT,
        f"Dialogue: 0,0:00:00.00,{ass_time(length_ms)},Chapter,,0,0,0,,{chapter_text}",
    ]
    for start, end, text in cues:
        escaped = text.replace("\n", "\\N")
        lines.append(
            f"Dialogue: 0,{ass_time(start)},{ass_time(end)},Caption,,0,0,0,,"
            "{\\fad(150,150)}" + escaped
        )
    return "\n".join(lines) + "\n"


def slice_srt(
    source: Path,
    start: float,
    duration: float,
    out: Path,
    font_size: int,
    margin: int,
    chapter: str,
    width: int,
    height: int,
    align: int,
    margin_v: int,
    chapter_margin: int,
) -> None:
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
        cues.append((cue_start - start_ms, min(cue_end, end_ms) - start_ms, text))
    out.write_text(
        "\n\n".join(
            f"{index}\n{fmt(cue_start)} --> {fmt(cue_end)}\n{text}"
            for index, (cue_start, cue_end, text) in enumerate(cues, 1)
        )
        + ("\n" if cues else ""),
        encoding="utf-8",
    )
    ass = out.with_suffix(".ass")
    ass.write_text(
        ass_document(
            cues, font_size, margin, chapter, round(duration * 1000),
            width, height, align, margin_v, chapter_margin,
        ),
        encoding="utf-8",
    )
    print(f"wrote {out} and {ass} with {len(cues)} cue(s)")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)

    wait = commands.add_parser("wait", help="wait for a UI node")
    wait.add_argument("pattern")
    wait.add_argument("--timeout", type=float, default=12)
    wait.add_argument("--min-y", type=int, default=0)

    tap_cmd = commands.add_parser("tap", help="tap a UI node")
    tap_cmd.add_argument("pattern")
    tap_cmd.add_argument("--min-y", type=int, default=0)
    tap_cmd.add_argument("--row", action="store_true", help="tap the clickable ancestor row")

    coords_cmd = commands.add_parser("coords", help="print the centre of a UI node")
    coords_cmd.add_argument("pattern")
    coords_cmd.add_argument("--min-y", type=int, default=0)
    coords_cmd.add_argument("--row", action="store_true")

    swipe_cmd = commands.add_parser("swipe", help="drag a UI node horizontally")
    swipe_cmd.add_argument("pattern")
    swipe_cmd.add_argument("--min-y", type=int, default=0)
    swipe_cmd.add_argument("--dx", type=int, required=True)
    swipe_cmd.add_argument("--duration", type=int, default=800)
    swipe_cmd.add_argument("--repeat", type=int, default=1, help="number of identical drags")
    swipe_cmd.add_argument("--gap", type=int, default=500, help="pause between drags in ms")

    commands.add_parser("dump", help="print the current uiautomator tree")

    shot_cmd = commands.add_parser("screenshot", help="save a PNG of the device screen")
    shot_cmd.add_argument("out")

    cut = commands.add_parser("slice-srt", help="cut one beat out of the master subtitles")
    cut.add_argument("source", type=Path)
    cut.add_argument("start", type=float)
    cut.add_argument("duration", type=float)
    cut.add_argument("out", type=Path)
    cut.add_argument("--font-size", type=int, default=44)
    cut.add_argument("--margin", type=int, default=80)
    cut.add_argument("--chapter", default="01 CAPTURE")
    cut.add_argument("--width", type=int, default=1920)
    cut.add_argument("--height", type=int, default=1080)
    cut.add_argument("--align", type=int, default=4)
    cut.add_argument("--margin-v", type=int, default=0)
    cut.add_argument("--chapter-margin", type=int, default=54)

    args = parser.parse_args()
    if args.command == "wait":
        wait_for(args.pattern, args.timeout, args.min_y)
    elif args.command == "tap":
        tap(args.pattern, args.min_y, args.row)
    elif args.command == "coords":
        coords(args.pattern, args.min_y, args.row)
    elif args.command == "swipe":
        swipe(args.pattern, args.dx, args.duration, args.min_y, args.repeat, args.gap)
    elif args.command == "screenshot":
        screenshot(args.out)
    elif args.command == "dump":
        adb("shell", "uiautomator", "dump", DUMP_PATH)
        print(adb("exec-out", "cat", DUMP_PATH))
    elif args.command == "slice-srt":
        slice_srt(
            args.source, args.start, args.duration, args.out,
            args.font_size, args.margin, args.chapter,
            args.width, args.height, args.align, args.margin_v, args.chapter_margin,
        )


if __name__ == "__main__":
    main()
