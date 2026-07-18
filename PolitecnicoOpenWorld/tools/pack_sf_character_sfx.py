#!/usr/bin/env python3
"""
pack_sf_character_sfx.py — Turn scraped voice WAVs into per-fighter special SFX OGGs.

For each SfFighterId with sources in sf_voice_scrape/manifest.json:
  1. Find peak-energy window (~0.85 s) suitable as a "special shout"
  2. Apply pitch / robotize / fade
  3. Normalize loudness
  4. Write special_<fighterid_lower>.ogg into app assets STREETFIGHTER/SOUNDS/

Also writes special_sfx_map.json mapping fighter -> sound key, and a report.

Usage:
  python tools/pack_sf_character_sfx.py
  python tools/pack_sf_character_sfx.py --install  # copy into main assets
"""
from __future__ import annotations

import argparse
import json
import math
import shutil
import struct
import subprocess
import sys
import wave
from pathlib import Path
from typing import Any

TOOLS = Path(__file__).resolve().parent
SCRAPE_DIR = TOOLS / "sf_voice_scrape"
MANIFEST = SCRAPE_DIR / "manifest.json"
OUT_DIR = SCRAPE_DIR / "out"
ASSETS_SOUNDS = (
    TOOLS.parent / "app" / "src" / "main" / "assets" / "STREETFIGHTER" / "SOUNDS"
)

# Full POW roster (must match SfFighterId)
ALL_FIGHTERS = [
    "PRANKEDY", "SENOR_TIENDA", "PAPARAZZI_1", "REY_GRUPERO", "PAPARAZZI_5",
    "LAZARO", "ESCOMBOY", "ESCOMGIRL", "ROBOT", "YOALLI_EHECATL",
    "CHARRO_NEGRO", "LA_LLORONA", "LA_TZITZIMIME", "LA_PRESIDENTA",
    "POLICIA_CDMX", "POLICIA_CDMX_HOMBRE", "PARAMEDICO_CRUZ_ROJA",
    "POLICIA_GRANADERO_HOMBRE", "POLICIA_GRANADERO_MUJER", "GRANADERO", "PARAMEDICO",
]

# Fallback: borrow from related family if no sources
FALLBACKS = {
    "PAPARAZZI_5": "PAPARAZZI_1",
    "POLICIA_CDMX_HOMBRE": "POLICIA_CDMX",
    "POLICIA_GRANADERO_HOMBRE": "POLICIA_CDMX",
    "POLICIA_GRANADERO_MUJER": "POLICIA_CDMX",
    "GRANADERO": "POLICIA_CDMX",
    "PARAMEDICO": "PARAMEDICO_CRUZ_ROJA",
    "YOALLI_EHECATL": "LA_PRESIDENTA",
    "LA_TZITZIMIME": "LA_LLORONA",
    "SENOR_TIENDA": "PRANKEDY",
    "REY_GRUPERO": "PRANKEDY",
    "ESCOMBOY": "PRANKEDY",
    "ESCOMGIRL": "PRANKEDY",
    "LAZARO": "PRANKEDY",
    "ROBOT": "LA_PRESIDENTA",
}


def find_ffmpeg() -> str:
    p = shutil.which("ffmpeg") or shutil.which("ffmpeg.exe")
    if not p:
        raise SystemExit("ffmpeg required")
    return p


def read_wav_mono(path: Path) -> tuple[list[float], int]:
    with wave.open(str(path), "rb") as w:
        ch = w.getnchannels()
        sr = w.getframerate()
        n = w.getnframes()
        sw = w.getsampwidth()
        raw = w.readframes(n)
    if sw != 2:
        # re-read via ffmpeg if needed
        raise ValueError(f"expected 16-bit wav: {path}")
    samples = struct.unpack("<" + "h" * (len(raw) // 2), raw)
    if ch == 1:
        mono = [s / 32768.0 for s in samples]
    else:
        mono = []
        for i in range(0, len(samples), ch):
            frame = samples[i : i + ch]
            mono.append(sum(frame) / (ch * 32768.0))
    return mono, sr


def peak_window(samples: list[float], sr: int, win_s: float = 0.85) -> tuple[int, int]:
    """Return sample [start, end) of highest RMS energy window."""
    win = max(1, int(sr * win_s))
    if len(samples) <= win:
        return 0, len(samples)
    # skip first 0.15s (often silence/logo) and last 0.1s
    start0 = min(int(sr * 0.15), max(0, len(samples) - win - 1))
    end0 = max(start0 + 1, len(samples) - int(sr * 0.1))
    hop = max(1, win // 8)
    best_i, best_e = start0, -1.0
    i = start0
    while i + win <= end0:
        chunk = samples[i : i + win]
        e = math.sqrt(sum(x * x for x in chunk) / len(chunk))
        # slight preference for mid-file speech over music beds
        if e > best_e:
            best_e, best_i = e, i
        i += hop
    return best_i, best_i + win


def write_temp_wav(samples: list[float], sr: int, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    ints = [max(-32767, min(32767, int(s * 32767))) for s in samples]
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(sr)
        w.writeframes(struct.pack("<" + "h" * len(ints), *ints))


def ffmpeg_process(
    ffmpeg: str,
    src_wav: Path,
    out_ogg: Path,
    pitch_semitones: float = 0,
    robotize: bool = False,
    duration: float = 0.9,
) -> bool:
    """Cut is already done; apply pitch/FX/normalize -> ogg vorbis."""
    out_ogg.parent.mkdir(parents=True, exist_ok=True)
    filters: list[str] = []
    # fade in/out
    fade = min(0.08, duration / 4)
    filters.append(f"afade=t=in:st=0:d={fade}")
    filters.append(f"afade=t=out:st={max(0, duration - fade)}:d={fade}")
    if abs(pitch_semitones) > 0.01:
        # rubberband if available, else asetrate+aresample
        rate = 44100 * (2 ** (pitch_semitones / 12.0))
        filters.append(f"asetrate={rate:.2f},aresample=44100")
    if robotize:
        # Vocoder-ish without afftfilt (often silent on short clips): ring-mod + vibrato
        filters.append("vibrato=f=8:d=0.4")
        filters.append("flanger=delay=8:depth=4:regen=0.4:speed=0.6")
        filters.append("bandpass=f=900:width_type=h:w=600")
    # combat punch: light highpass + compressor-ish
    filters.append("highpass=f=120")
    filters.append("lowpass=f=6500")
    filters.append("dynaudnorm=f=75:g=15")
    filters.append("volume=1.35")
    af = ",".join(filters)
    cmd = [
        ffmpeg, "-y", "-hide_banner", "-loglevel", "error",
        "-i", str(src_wav),
        "-af", af,
        "-t", f"{duration:.3f}",
        "-c:a", "libvorbis",
        "-q:a", "4",
        str(out_ogg),
    ]
    try:
        subprocess.run(cmd, check=True, timeout=60)
        return out_ogg.exists() and out_ogg.stat().st_size > 500
    except (subprocess.CalledProcessError, subprocess.TimeoutExpired) as e:
        print(f"  [pack fail] {out_ogg.name}: {e}")
        return False


def synth_fallback(ffmpeg: str, fighter: str, out_ogg: Path, seed: int) -> bool:
    """Distinct synthetic special when no voice source exists."""
    # unique-ish pitch per fighter name
    base = 220 + (seed % 18) * 20
    dur = 0.75
    # layered tones + noise whoosh
    cmd = [
        ffmpeg, "-y", "-hide_banner", "-loglevel", "error",
        "-f", "lavfi", "-i", f"sine=frequency={base}:duration={dur}",
        "-f", "lavfi", "-i", f"sine=frequency={base*1.5:.1f}:duration={dur}",
        "-f", "lavfi", "-i", f"anoisesrc=d={dur}:c=pink:a=0.15",
        "-filter_complex",
        f"[0][1]amix=inputs=2:duration=first,volume=0.5[a];"
        f"[a][2]amix=inputs=2:duration=first,"
        f"afade=t=in:d=0.05,afade=t=out:st={dur-0.12}:d=0.12,"
        f"highpass=f=200,lowpass=f=4000,dynaudnorm,volume=1.2",
        "-c:a", "libvorbis", "-q:a", "4",
        str(out_ogg),
    ]
    try:
        subprocess.run(cmd, check=True, timeout=30)
        return out_ogg.exists()
    except (subprocess.CalledProcessError, subprocess.TimeoutExpired):
        return False


def pack_fighter(
    ffmpeg: str,
    fighter: str,
    sources: list[dict[str, Any]],
    tmp_dir: Path,
) -> Path | None:
    if not sources:
        return None
    # Prefer: high priority, youtube > local music beds, then usable length
    scored: list[tuple[float, dict[str, Any]]] = []
    for src in sources:
        wav_path = Path(src["path"])
        if not wav_path.is_file():
            continue
        size = wav_path.stat().st_size
        pri = int(src.get("priority", 0))
        src_kind = str(src.get("source", ""))
        kind_boost = 0
        if src_kind == "youtube":
            kind_boost = 500_000
        elif src_kind == "x":
            kind_boost = 200_000
        elif src_kind == "local":
            # local MP3s often full tracks/music — still useful but lower than speech clips
            kind_boost = 50_000
        # Cap size influence so a 20 MB track doesn't beat a 7 MB speech section
        size_score = min(size, 9_000_000) // 1000
        scored.append((pri * 100_000 + kind_boost + size_score, src))
    scored.sort(key=lambda t: -t[0])

    out = OUT_DIR / f"special_{fighter.lower()}.ogg"
    for _, src in scored:
        wav_path = Path(src["path"])
        try:
            samples, sr = read_wav_mono(wav_path)
        except Exception as e:
            print(f"  [skip read] {wav_path.name}: {e}")
            continue
        if len(samples) < sr * 0.2:
            continue
        a, b = peak_window(samples, sr, win_s=0.95)
        chunk = samples[a:b]
        rms = math.sqrt(sum(x * x for x in chunk) / max(1, len(chunk)))
        if rms < 0.01:
            # nearly silent window — try mid-file fixed slice
            mid = len(samples) // 3
            chunk = samples[mid : mid + int(sr * 0.95)]
            if not chunk:
                continue
        peak = max(1e-6, max(abs(x) for x in chunk))
        chunk = [x / peak * 0.9 for x in chunk]
        tmp = tmp_dir / f"{fighter}_cut.wav"
        write_temp_wav(chunk, sr, tmp)
        pitch = float(src.get("pitch_semitones") or 0)
        robot = bool(src.get("robotize", False))
        if ffmpeg_process(ffmpeg, tmp, out, pitch_semitones=pitch, robotize=robot, duration=0.95):
            # Reject tiny/broken packs and try next source
            if out.stat().st_size < 5000 and len(scored) > 1:
                print(f"  [retry] {fighter} tiny pack from {wav_path.name} ({out.stat().st_size} B)")
                continue
            print(f"  [ok] {fighter} <- {wav_path.name} pitch={pitch} robot={robot} -> {out.name} ({out.stat().st_size} B)")
            return out
    return None


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--install", action="store_true", help="Copy OGGs into app assets")
    args = ap.parse_args()

    if not MANIFEST.is_file():
        print("No manifest — run scrape_sf_voices.py first")
        return 1

    man = json.loads(MANIFEST.read_text(encoding="utf-8"))
    by_f: dict[str, list[dict[str, Any]]] = {}
    for e in man.get("entries", []):
        by_f.setdefault(e["fighter"].upper(), []).append(e)

    ffmpeg = find_ffmpeg()
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    tmp_dir = SCRAPE_DIR / "_tmp_pack"
    tmp_dir.mkdir(parents=True, exist_ok=True)

    produced: dict[str, str] = {}
    report: dict[str, Any] = {"fighters": {}}

    # first pass with direct sources
    for fighter in ALL_FIGHTERS:
        srcs = by_f.get(fighter, [])
        out = pack_fighter(ffmpeg, fighter, srcs, tmp_dir)
        if out:
            produced[fighter] = out.name
            report["fighters"][fighter] = {"status": "packed", "file": out.name, "sources": len(srcs)}
        else:
            report["fighters"][fighter] = {"status": "pending", "sources": len(srcs)}

    # fallbacks: reuse relative fighter's wav with extra pitch
    for fighter in ALL_FIGHTERS:
        if fighter in produced:
            continue
        donor = FALLBACKS.get(fighter)
        if donor and by_f.get(donor):
            # clone donor entries with pitch shift based on name
            extra = abs(hash(fighter)) % 5 + 1
            sign = 1 if (hash(fighter) % 2 == 0) else -1
            cloned = []
            for e in by_f[donor]:
                c = dict(e)
                c["pitch_semitones"] = float(c.get("pitch_semitones") or 0) + sign * extra
                if fighter == "ROBOT":
                    c["robotize"] = True
                cloned.append(c)
            out = pack_fighter(ffmpeg, fighter, cloned, tmp_dir)
            if out:
                produced[fighter] = out.name
                report["fighters"][fighter] = {
                    "status": "fallback",
                    "from": donor,
                    "file": out.name,
                }

    # last resort synth
    for fighter in ALL_FIGHTERS:
        if fighter in produced:
            continue
        out = OUT_DIR / f"special_{fighter.lower()}.ogg"
        if synth_fallback(ffmpeg, fighter, out, seed=abs(hash(fighter))):
            produced[fighter] = out.name
            report["fighters"][fighter] = {"status": "synth", "file": out.name}
            print(f"  [synth] {fighter}")

    key_map = {f: f"special_{f.lower()}" for f in produced}
    map_path = OUT_DIR / "special_sfx_map.json"
    map_path.write_text(json.dumps(key_map, indent=2), encoding="utf-8")
    report_path = SCRAPE_DIR / "pack_report.json"
    report["produced"] = produced
    report["key_map"] = key_map
    report_path.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")

    if args.install:
        ASSETS_SOUNDS.mkdir(parents=True, exist_ok=True)
        for f, name in produced.items():
            src = OUT_DIR / name
            dst = ASSETS_SOUNDS / name
            shutil.copy2(src, dst)
            print(f"  [install] {dst}")
        # map JSON stays in tools/sf_voice_scrape/out/ (not needed at runtime)

    print(f"\n=== Packed {len(produced)}/{len(ALL_FIGHTERS)} fighters ===")
    print(f"Out: {OUT_DIR}")
    print(f"Report: {report_path}")
    return 0 if len(produced) == len(ALL_FIGHTERS) else 2


if __name__ == "__main__":
    sys.exit(main())
