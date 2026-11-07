#!/usr/bin/env python3
"""Render the three PR #188 voices with a lupine growl, preserving their timing.

Requires ffmpeg (with rubberband), ffprobe, NumPy and SciPy. Speech sources are
read from the immutable PR commit, never from the already processed assets.
Animal sources and their license record live in newSFAssets/CharroNegro/audio_sources.
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
import wave

import numpy as np
from scipy.signal import butter, sosfilt

ROOT = Path(__file__).resolve().parents[2]
SOURCE_REF = "b191a6f8ddec7d6bf4fa0b3b03c4b8b89b656301"
ASSETS = "PolitecnicoOpenWorld/app/src/main/assets/STREETFIGHTER/SOUNDS"
ANIMALS = ROOT / "newSFAssets/CharroNegro/audio_sources"
SR = 44100
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


def decode(path: Path, filters: str = "anull") -> np.ndarray:
    return np.frombuffer(run("ffmpeg", "-v", "error", "-i", str(path),
                             "-af", filters, "-ar", str(SR), "-ac", "1",
                             "-f", "f32le", "-"), dtype="<f4").astype(np.float64)


def rms_match(samples: np.ndarray, level: float) -> np.ndarray:
    return samples * (level / max(1e-8, np.sqrt(np.mean(samples ** 2))))


def envelope(samples: np.ndarray) -> np.ndarray:
    smoothing = butter(2, 28, fs=SR, output="sos")
    return np.maximum(0, sosfilt(smoothing, np.abs(samples)))


def tile_crossfade(samples: np.ndarray, length: int) -> np.ndarray:
    """Repeat short growls with 120 ms overlaps, avoiding hard loop boundaries."""
    overlap = min(int(SR * .12), len(samples) // 4)
    result = samples.copy()
    ramp = np.linspace(0, 1, overlap)
    while len(result) < length:
        result[-overlap:] = result[-overlap:] * (1-ramp) + samples[:overlap] * ramp
        result = np.concatenate((result, samples[overlap:]))
    return result[:length]


def animal_voice(source: Path, duration: float, index: int) -> np.ndarray:
    count = round(duration * SR)
    speech = decode(source, "rubberband=pitch=0.88:tempo=1:formant=shifted")
    speech = np.pad(speech, (0, max(0, count-len(speech))))[:count]
    speech = rms_match(speech, .14)
    recording = decode(ANIMALS / "wolf_howls_fws.ogg", "highpass=f=120,lowpass=f=7000")
    start = int((.15, 5.0, 12.0)[index] * SR)
    howl = rms_match(recording[start:start+count], .14)
    growl = decode(ANIMALS / f"wolf_growl_{'03' if index == 1 else '02'}.mp3",
                   "highpass=f=90,lowpass=f=6000")
    growl = rms_match(tile_crossfade(growl, count), .14)
    articulation = np.clip(envelope(speech) / .11, 0, 1.7)

    # A filter-bank vocoder transfers the phrase's changing resonances onto the
    # actual howl. The carrier retains a wolf's natural pitch and harmonics.
    vocoded = np.zeros(count)
    edges = (120, 200, 320, 500, 800, 1200, 1800, 2600, 3800, 5500, 8000)
    for low, high in zip(edges, edges[1:]):
        band = butter(2, (low, high), btype="bandpass", fs=SR, output="sos")
        modulator = envelope(sosfilt(band, speech))
        carrier = sosfilt(band, howl)
        carrier /= max(.003, np.sqrt(np.mean(carrier ** 2)))
        vocoded += carrier * modulator
    vocoded = rms_match(vocoded, .14)

    # The howl remains exposed briefly at both ends to make the species audible;
    # inside the phrase the animal layers follow the rhythm of the syllables.
    seconds = np.arange(count) / SR
    exposed = np.maximum(np.clip((.85-seconds)/.45, 0, 1),
                         np.clip((seconds-(duration-.75))/.40, 0, 1))
    mix = (.22 * speech + .65 * vocoded + .55 * growl * articulation
           + howl * (.22 * articulation + .95 * exposed))
    fade_in = min(round(.012 * SR), count)
    fade_out = min(round(.030 * SR), count)
    mix[:fade_in] *= np.linspace(0, 1, fade_in)
    mix[-fade_out:] *= np.linspace(1, 0, fade_out)
    if not np.isfinite(mix).all():
        raise ValueError("Non-finite animal voice samples")
    return mix * (.85 / max(1e-8, np.max(np.abs(mix))))


def render(source: Path, target: Path, work: Path) -> dict:
    info = probe(source)
    duration = float(info["format"]["duration"])
    wet = work / "wet.wav"
    samples = animal_voice(source, duration, FILES.index(target.name))
    with wave.open(str(wet), "wb") as output_wav:
        output_wav.setnchannels(1)
        output_wav.setsampwidth(2)
        output_wav.setframerate(SR)
        output_wav.writeframes((samples * 32767).astype("<i2").tobytes())
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
    report = {
        "source_ref": args.source_ref, "profile": "wolf_vocoder_and_vocalizations",
        "animal_sources": {
            name: hashlib.sha256((ANIMALS / name).read_bytes()).hexdigest()
            for name in ("wolf_howls_fws.ogg", "wolf_growl_02.mp3", "wolf_growl_03.mp3")
        },
        "clips": [],
    }
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
