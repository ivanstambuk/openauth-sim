#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import math
import os
import re
import shutil
import subprocess
import sys
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


SSIM_PATTERN = re.compile(r"All:(?P<value>[0-9.]+)")


@dataclass(frozen=True)
class Screenshot:
    protocol: str
    state: str
    file: Path

    @property
    def key(self) -> str:
        return f"{self.protocol}::{self.state}"


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def load_manifest(run_dir: Path) -> dict[str, Any]:
    manifest_path = run_dir / "manifest.json"
    if not manifest_path.exists():
        raise FileNotFoundError(f"Missing manifest.json under {run_dir}")
    return json.loads(manifest_path.read_text(encoding="utf-8"))


def read_screenshots(manifest: dict[str, Any]) -> list[Screenshot]:
    screenshots = []
    for entry in manifest.get("screenshots", []):
        screenshots.append(
            Screenshot(
                protocol=str(entry.get("protocol", "")),
                state=str(entry.get("state", "")),
                file=Path(str(entry.get("file", ""))),
            )
        )
    return screenshots


def require_ffmpeg() -> str:
    ffmpeg = shutil.which("ffmpeg")
    if not ffmpeg:
        raise RuntimeError("ffmpeg is required for triage artifacts but was not found on PATH")
    return ffmpeg


def normalized_filter(width: int, height: int) -> str:
    return (
        f"scale={width}:{height}:force_original_aspect_ratio=decrease,"
        f"pad={width}:{height}:(ow-iw)/2:(oh-ih)/2:color=0x000000,"
        "setsar=1"
    )


def compute_ssim(ffmpeg: str, baseline: Path, current: Path, width: int, height: int) -> tuple[float | None, str | None]:
    filter_complex = (
        f"[0:v]{normalized_filter(width, height)}[a];"
        f"[1:v]{normalized_filter(width, height)}[b];"
        "[a][b]ssim"
    )
    cmd = [
        ffmpeg,
        "-hide_banner",
        "-nostats",
        "-loglevel",
        "info",
        "-i",
        str(baseline),
        "-i",
        str(current),
        "-filter_complex",
        filter_complex,
        "-f",
        "null",
        "-",
    ]
    completed = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, check=False)
    output = completed.stderr or ""
    if completed.returncode != 0:
        return None, output.strip() or "ffmpeg ssim failed"

    match = SSIM_PATTERN.search(output)
    if not match:
        return None, "Unable to parse SSIM output"

    try:
        value = float(match.group("value"))
    except ValueError:
        return None, "Invalid SSIM value"
    return value, None


def create_pair_image(ffmpeg: str, baseline: Path, current: Path, out_path: Path, width: int, height: int) -> tuple[bool, str | None]:
    out_path.parent.mkdir(parents=True, exist_ok=True)
    filter_complex = (
        f"[0:v]{normalized_filter(width, height)}[left];"
        f"[1:v]{normalized_filter(width, height)}[right];"
        "[left][right]hstack=inputs=2"
    )
    cmd = [
        ffmpeg,
        "-hide_banner",
        "-nostats",
        "-loglevel",
        "error",
        "-y",
        "-i",
        str(baseline),
        "-i",
        str(current),
        "-filter_complex",
        filter_complex,
        "-frames:v",
        "1",
        str(out_path),
    ]
    completed = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, check=False)
    if completed.returncode != 0:
        return False, (completed.stderr or "").strip() or "ffmpeg pair image failed"
    return True, None


def create_thumbnail(ffmpeg: str, source: Path, out_path: Path, width: int, height: int) -> tuple[bool, str | None]:
    out_path.parent.mkdir(parents=True, exist_ok=True)
    cmd = [
        ffmpeg,
        "-hide_banner",
        "-nostats",
        "-loglevel",
        "error",
        "-y",
        "-i",
        str(source),
        "-vf",
        normalized_filter(width, height),
        "-frames:v",
        "1",
        str(out_path),
    ]
    completed = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, check=False)
    if completed.returncode != 0:
        return False, (completed.stderr or "").strip() or "ffmpeg thumbnail failed"
    return True, None


def create_montage_from_sequence(ffmpeg: str, images: list[Path], out_path: Path, columns: int, width: int, height: int) -> tuple[bool, str | None]:
    if not images:
        return False, "No images supplied"

    rows = int(math.ceil(len(images) / columns))
    target_count = columns * rows
    padded = list(images)
    while len(padded) < target_count:
        padded.append(images[-1])

    temp_dir = out_path.parent / f".tmp-montage-{out_path.stem}"
    if temp_dir.exists():
        shutil.rmtree(temp_dir)
    temp_dir.mkdir(parents=True, exist_ok=True)

    try:
        for index, src in enumerate(padded):
            dst = temp_dir / f"{index:03d}.png"
            try:
                os.symlink(src, dst)
            except OSError:
                shutil.copyfile(src, dst)

        filter_chain = f"tile={columns}x{rows}:padding=8:margin=8"
        cmd = [
            ffmpeg,
            "-hide_banner",
            "-nostats",
            "-loglevel",
            "error",
            "-y",
            "-framerate",
            "1",
            "-i",
            str(temp_dir / "%03d.png"),
            "-vf",
            filter_chain,
            "-frames:v",
            "1",
            str(out_path),
        ]
        completed = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, check=False)
        if completed.returncode != 0:
            return False, (completed.stderr or "").strip() or "ffmpeg montage failed"
        return True, None
    finally:
        shutil.rmtree(temp_dir, ignore_errors=True)


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate triage artifacts for operator console snapshot runs.")
    parser.add_argument("--current", required=True, help="Snapshot run directory containing manifest.json")
    parser.add_argument("--baseline", help="Baseline snapshot run directory containing manifest.json")
    parser.add_argument("--top", type=int, default=12, help="Number of top changes to include in montage (default: 12)")
    parser.add_argument("--ssim-width", type=int, default=640, help="Normalization width for SSIM comparisons (default: 640)")
    parser.add_argument("--ssim-height", type=int, default=360, help="Normalization height for SSIM comparisons (default: 360)")
    args = parser.parse_args()

    current_dir = Path(args.current).resolve()
    baseline_dir = Path(args.baseline).resolve() if args.baseline else None
    out_dir = current_dir / "triage"
    out_dir.mkdir(parents=True, exist_ok=True)

    ffmpeg = require_ffmpeg()

    current_manifest = load_manifest(current_dir)
    current_shots = read_screenshots(current_manifest)

    baseline_map: dict[str, Screenshot] = {}
    if baseline_dir:
        try:
            baseline_manifest = load_manifest(baseline_dir)
            for shot in read_screenshots(baseline_manifest):
                baseline_map[shot.key] = shot
        except FileNotFoundError:
            baseline_dir = None

    entries: list[dict[str, Any]] = []
    for shot in current_shots:
        entry: dict[str, Any] = {
            "protocol": shot.protocol,
            "state": shot.state,
            "file": str(shot.file),
        }

        baseline_shot = baseline_map.get(shot.key)
        if baseline_shot:
            entry["baselineFile"] = str(baseline_shot.file)
            ssim_value, error = compute_ssim(
                ffmpeg=ffmpeg,
                baseline=baseline_shot.file,
                current=shot.file,
                width=args.ssim_width,
                height=args.ssim_height,
            )
            if ssim_value is not None:
                entry["ssim"] = ssim_value
                entry["diffScore"] = 1.0 - ssim_value
            if error:
                entry["error"] = error
        else:
            entry["baselineFile"] = None
            entry["ssim"] = None
            entry["diffScore"] = None

        entries.append(entry)

    diff_entries = [e for e in entries if isinstance(e.get("diffScore"), (int, float))]
    diff_entries.sort(key=lambda e: float(e.get("diffScore") or 0.0), reverse=True)
    for rank, entry in enumerate(diff_entries, start=1):
        entry["rank"] = rank

    top = diff_entries[: max(0, int(args.top))]

    pairs_dir = out_dir / "pairs"
    pair_images: list[Path] = []
    for entry in top:
        baseline_path = Path(str(entry["baselineFile"]))
        current_path = Path(str(entry["file"]))
        safe_state = str(entry["state"]).replace("/", "-")
        out_path = pairs_dir / f"{entry['rank']:03d}--{entry['protocol']}--{safe_state}.png"
        ok, error = create_pair_image(ffmpeg, baseline_path, current_path, out_path, width=480, height=270)
        if ok:
            pair_images.append(out_path)
        else:
            entry["pairError"] = error

    montage_path: Path | None = None
    if pair_images:
        montage_path = out_dir / "top-changes.png"
        cols = 2
        ok, error = create_montage_from_sequence(ffmpeg, pair_images, montage_path, columns=cols, width=0, height=0)
        if not ok:
            montage_path = None
            (out_dir / "top-changes.error.txt").write_text(error or "montage failed", encoding="utf-8")

    interactive_states = [s for s in current_shots if s.file.name.endswith("--result.png")]
    thumb_dir = out_dir / "thumbnails"
    interactive_thumbs: list[Path] = []
    for index, shot in enumerate(interactive_states):
        thumb_path = thumb_dir / f"{index:03d}--{shot.protocol}--{shot.state}.png"
        ok, _ = create_thumbnail(ffmpeg, shot.file, thumb_path, width=480, height=270)
        if ok:
            interactive_thumbs.append(thumb_path)

    interactive_montage_path: Path | None = None
    if interactive_thumbs:
        interactive_montage_path = out_dir / "current-interactive.png"
        cols = 3
        ok, error = create_montage_from_sequence(ffmpeg, interactive_thumbs, interactive_montage_path, columns=cols, width=0, height=0)
        if not ok:
            interactive_montage_path = None
            (out_dir / "current-interactive.error.txt").write_text(error or "montage failed", encoding="utf-8")

    triage_payload: dict[str, Any] = {
        "generatedAt": now_iso(),
        "currentRunDir": str(current_dir),
        "baselineRunDir": str(baseline_dir) if baseline_dir else None,
        "topCount": len(top),
        "topChangesMontage": str(montage_path) if montage_path else None,
        "interactiveMontage": str(interactive_montage_path) if interactive_montage_path else None,
        "entries": entries,
    }

    (out_dir / "triage.json").write_text(json.dumps(triage_payload, indent=2) + "\n", encoding="utf-8")
    sys.stdout.write(f"Wrote triage artifacts to {out_dir}\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
