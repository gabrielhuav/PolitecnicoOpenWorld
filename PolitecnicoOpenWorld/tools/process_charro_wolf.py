#!/usr/bin/env python3
"""Render the three PR #188 voices with a lupine growl, preserving their timing.

Requires ffmpeg (with rubberband) and ffprobe. Sources are read from the immutable
PR commit, never from the already processed assets. No downloaded animal samples.
Run from any directory; --output-dir is required to make installation explicit.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE_REF = "b191a6f8ddec7d6bf4fa0b3b03c4b8b89b656301"
ASSETS = "PolitecnicoOpenWorld/app/src/main/assets/STREETFIGHTER/SOUNDS"
FILES = (
    "special_charro_hurt_1.m4a",
    "special_charro_negro.m4a",
    "special_charro_negro_win.m4a",
)


def run(*args: str) -> bytes:
    return subprocess.run(args, check=True, capture_output=True).stdout


def probe(path: Path) -> dict:
    return json.loads(run("ffprobe", "-v", "error", "-show_streams", "-show_format",
                          "-of", "json", str(path)))


def loudness(path: Path) -> dict:
    result = subprocess.run(
        ["ffmpeg", "-hide_banner", "-i", str(path), "-af",
         "loudnorm=I=-16:TP=-2:LRA=11:print_format=json", "-f", "null", "-"],
        check=True, capture_output=True, text=True,
    )
    return json.loads(re.search(r'\{[^{}]*"input_i"[^{}]*\}', result.stderr).group())


def render(source: Path, target: Path, work: Path) -> dict:
    info = probe(source)
    duration = float(info["format"]["duration"])
    # The user prefers a very bestial voice over clear articulation. A dominant
    # throat layer and a rasp layer roughen the lower speech. All keep the tempo.
    graph = (
        "[0:a]aresample=44100,highpass=f=65,asplit=3[speech][throat][rasp];"
        "[speech]rubberband=pitch=0.70:tempo=1:formant=shifted,"
        "lowpass=f=5000[voice];"
        "[throat]rubberband=pitch=0.47:tempo=1:formant=shifted,"
        "highpass=f=65,lowpass=f=1800,volume=5,asoftclip=type=tanh,"
        "tremolo=f=37:d=0.68[growl];"
        "[rasp]rubberband=pitch=0.88:tempo=1:formant=shifted,"
        "highpass=f=550,lowpass=f=3800,volume=6,asoftclip=type=tanh[snarl];"
        "[voice][growl][snarl]amix=inputs=3:weights='0.40 0.60 0.18':normalize=0,"
        "highpass=f=65,lowpass=f=7000,"
        f"apad,atrim=duration={duration:.9f},"
        f"afade=t=in:d=0.008,afade=t=out:st={max(0, duration-0.025):.9f}:d=0.025[out]"
    )
    wet = work / "wet.wav"
    run("ffmpeg", "-y", "-v", "error", "-i", str(source), "-filter_complex", graph,
        "-map", "[out]", "-c:a", "pcm_f32le", str(wet))
    measured = loudness(wet)
    normalization = (
        "loudnorm=I=-16:TP=-2:LRA=11:linear=true:"
        f"measured_I={measured['input_i']}:measured_TP={measured['input_tp']}:"
        f"measured_LRA={measured['input_lra']}:measured_thresh={measured['input_thresh']}:"
        f"offset={measured['target_offset']}"
    )
    candidate = work / target.name
    run("ffmpeg", "-y", "-v", "error", "-i", str(wet), "-af", normalization,
        "-t", f"{duration:.9f}",
        "-ar", "44100", "-ac", "1", "-c:a", "aac", "-b:a", "128k",
        "-movflags", "+faststart", str(candidate))
    output = probe(candidate)
    final = loudness(candidate)
    delta = float(output["format"]["duration"]) - duration
    if abs(delta) > 0.025:
        raise ValueError(f"Duration changed by {delta:.3f}s: {target.name}")
    if abs(float(final["input_i"]) + 16) > 1:
        raise ValueError(f"Loudness outside -16 +/- 1 LUFS: {final}")
    if float(final["input_tp"]) > -1.5:
        raise ValueError(f"Insufficient peak headroom: {final}")
    stream = output["streams"][0]
    if (stream["codec_name"], stream["sample_rate"], stream["channels"]) != ("aac", "44100", 1):
        raise ValueError(f"Unexpected encoding: {stream}")
    target.write_bytes(candidate.read_bytes())
    return {
        "file": target.name,
        "source_sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
        "output_sha256": hashlib.sha256(target.read_bytes()).hexdigest(),
        "source_seconds": duration,
        "output_seconds": float(output["format"]["duration"]),
        "lufs": float(final["input_i"]),
        "true_peak_dbtp": float(final["input_tp"]),
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-ref", default=SOURCE_REF)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    report = {"source_ref": args.source_ref, "profile": "very_bestial", "clips": []}
    with tempfile.TemporaryDirectory(prefix="charro-wolf-") as temporary:
        work = Path(temporary)
        for filename in FILES:
            source = work / ("source_" + filename)
            source.write_bytes(run("git", "-C", str(ROOT), "show", f"{args.source_ref}:{ASSETS}/{filename}"))
            entry = render(source, args.output_dir / filename, work)
            report["clips"].append(entry)
            print(json.dumps(entry))
    (args.output_dir / "charro_wolf_report.json").write_text(
        json.dumps(report, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
