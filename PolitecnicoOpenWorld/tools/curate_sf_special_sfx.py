#!/usr/bin/env python3
"""
curate_sf_special_sfx.py — Manual, fight-game-quality special SFX per character.

Unlike blind peak-picking, this recipe book picks:
  - iconic shout / phrase windows from real Prankedy / Paparazzi / Presidenta media
  - eerie processed clips for ghosts (Llorona, Charro, Tzitzimime, Yoalli)
  - institutional / character-flavored FX for police, paramedics, students, robot

Each clip is ~0.75–1.1 s, compressed, faded — SF special length.

Usage (repo root):
  python PolitecnicoOpenWorld/tools/curate_sf_special_sfx.py
  python PolitecnicoOpenWorld/tools/curate_sf_special_sfx.py --install
"""
from __future__ import annotations

import argparse
import math
import shutil
import struct
import subprocess
import sys
import wave
from pathlib import Path
from typing import Callable

TOOLS = Path(__file__).resolve().parent
SCRAPE = TOOLS / "sf_voice_scrape"
EXTRACTED = SCRAPE / "extracted"
CURATED_RAW = SCRAPE / "raw_curated"
OUT = SCRAPE / "out_curated"
ASSETS = TOOLS.parent / "app" / "src" / "main" / "assets" / "STREETFIGHTER" / "SOUNDS"
REPO = TOOLS.parent.parent  # PolitecnicoOpenWorld repo root
LOCAL = REPO / "nuevoMaterial17JUL"


def ffmpeg() -> str:
    p = shutil.which("ffmpeg") or shutil.which("ffmpeg.exe")
    if not p:
        raise SystemExit("ffmpeg required")
    return p


def ensure_wav_from_any(src: Path, dst: Path) -> Path:
    """Convert mp3/webm/etc to mono 44.1k wav if needed."""
    if dst.exists() and dst.stat().st_size > 1000:
        return dst
    dst.parent.mkdir(parents=True, exist_ok=True)
    if src.suffix.lower() == ".wav":
        # re-encode to mono 44100 for consistency
        subprocess.run(
            [ffmpeg(), "-y", "-hide_banner", "-loglevel", "error",
             "-i", str(src), "-ac", "1", "-ar", "44100", "-c:a", "pcm_s16le", str(dst)],
            check=True, timeout=120,
        )
        return dst
    subprocess.run(
        [ffmpeg(), "-y", "-hide_banner", "-loglevel", "error",
         "-i", str(src), "-ac", "1", "-ar", "44100", "-c:a", "pcm_s16le", str(dst)],
        check=True, timeout=180,
    )
    return dst


def read_mono(path: Path, max_sec: float | None = 240.0) -> tuple[list[float], int]:
    """Read mono float samples; optionally cap duration for huge WAVs."""
    with wave.open(str(path), "rb") as w:
        ch, sw, sr, n = w.getnchannels(), w.getsampwidth(), w.getframerate(), w.getnframes()
        if max_sec is not None:
            n = min(n, int(sr * max_sec))
        raw = w.readframes(n)
    if sw != 2:
        raise ValueError(f"need 16-bit: {path}")
    samples = struct.unpack("<" + "h" * (len(raw) // 2), raw)
    if ch == 1:
        mono = [s / 32768.0 for s in samples]
    else:
        mono = [sum(samples[i : i + ch]) / (ch * 32768.0) for i in range(0, len(samples), ch)]
    return mono, sr


def write_mono(path: Path, samples: list[float], sr: int) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    ints = [max(-32767, min(32767, int(s * 32767))) for s in samples]
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(sr)
        w.writeframes(struct.pack("<" + "h" * len(ints), *ints))


def slice_sec(samples: list[float], sr: int, start: float, dur: float) -> list[float]:
    a = max(0, int(start * sr))
    b = min(len(samples), a + int(dur * sr))
    chunk = samples[a:b]
    if not chunk:
        raise ValueError(f"empty slice @ {start}s")
    # soft normalize to ~0.9 peak
    peak = max(1e-6, max(abs(x) for x in chunk))
    return [x / peak * 0.9 for x in chunk]


def best_speech_window(
    samples: list[float],
    sr: int,
    dur: float = 0.95,
    prefer_mid: bool = True,
    start_search: float = 0.3,
    max_scan_sec: float = 180.0,
) -> list[float]:
    """
    Prefer windows with high RMS but NOT pure sub-bass music:
    score = rms * speechiness (ZCR + mid energy).
    Caps scan length for huge files (full YT downloads).
    """
    win = int(dur * sr)
    if len(samples) <= win:
        peak = max(1e-6, max(abs(x) for x in samples))
        return [x / peak * 0.9 for x in samples]

    hop = max(1, win // 10)
    i0 = int(start_search * sr)
    # Scan up to max_scan_sec, preferring first half of content (hooks / reactions)
    max_end = min(len(samples) - win, int(max_scan_sec * sr))
    i1 = max(i0 + 1, max_end)
    best_score, best_i = -1.0, i0
    for i in range(i0, i1, hop):
        chunk = samples[i : i + win]
        rms = math.sqrt(sum(x * x for x in chunk) / len(chunk))
        if rms < 0.02:
            continue
        zc = sum(1 for a, b in zip(chunk[::2], chunk[1::2]) if (a >= 0) != (b >= 0)) / max(1, len(chunk) // 2)
        diffs = [abs(chunk[j + 1] - chunk[j]) for j in range(0, len(chunk) - 1, 6)]
        mid = math.sqrt(sum(d * d for d in diffs) / max(1, len(diffs)))
        score = rms * (0.4 + zc * 2.5) * (0.3 + mid * 5.0)
        if prefer_mid and rms > 0.4 and zc < 0.04:
            score *= 0.45  # likely continuous music bed
        if score > best_score:
            best_score, best_i = score, i
    return slice_sec(samples, sr, best_i / sr, dur)


def run_ffmpeg_fx(
    ff: str,
    src_wav: Path,
    out_ogg: Path,
    style: str,
    pitch_st: float = 0.0,
    duration: float = 0.95,
) -> bool:
    """
    style:
      shout  — punchy fight special (Prankedy, students, paparazzi)
      speech — presidenta / institutional
      horror — ghosts (reverb, dark)
      metal  — charro / grim
      robot  — robotic
      radio  — police radio-ish
      medic  — clean urgent
    """
    out_ogg.parent.mkdir(parents=True, exist_ok=True)
    filters: list[str] = []
    fade_in, fade_out = 0.04, 0.1
    filters.append(f"afade=t=in:st=0:d={fade_in}")
    filters.append(f"afade=t=out:st={max(0.05, duration - fade_out)}:d={fade_out}")

    if abs(pitch_st) > 0.01:
        rate = 44100 * (2 ** (pitch_st / 12.0))
        filters.append(f"asetrate={rate:.2f},aresample=44100")

    if style == "shout":
        # Fight-game shout: bright, compressed, slight slapback
        filters += [
            "highpass=f=150",
            "lowpass=f=7000",
            "acompressor=threshold=-18dB:ratio=6:attack=5:release=80",
            "aecho=0.8:0.7:40:0.15",
            "dynaudnorm=f=75:g=12",
            "volume=1.55",
        ]
    elif style == "speech":
        filters += [
            "highpass=f=100",
            "lowpass=f=5500",
            "acompressor=threshold=-20dB:ratio=4:attack=10:release=100",
            "dynaudnorm=f=90:g=10",
            "volume=1.4",
        ]
    elif style == "horror":
        # Dark, wet, terrifying
        filters += [
            "highpass=f=80",
            "lowpass=f=4000",
            "aecho=0.9:0.85:120|180|240:0.4|0.3|0.2",
            "vibrato=f=4.5:d=0.35",
            "acompressor=threshold=-16dB:ratio=5:attack=8:release=120",
            "dynaudnorm=f=100:g=15",
            "volume=1.5",
            "lowpass=f=3500",
        ]
    elif style == "metal":
        # Charro: deep, ominous
        filters += [
            "highpass=f=60",
            "lowpass=f=4500",
            "aecho=0.85:0.8:90:0.35",
            "acompressor=threshold=-18dB:ratio=5:attack=8:release=100",
            "dynaudnorm=f=80:g=12",
            "volume=1.45",
        ]
    elif style == "robot":
        filters += [
            "tremolo=f=14:d=0.75",
            "highpass=f=350",
            "lowpass=f=3200",
            "aecho=0.7:0.6:25:0.25",
            "dynaudnorm=f=70:g=12",
            "volume=1.5",
        ]
    elif style == "radio":
        filters += [
            "highpass=f=400",
            "lowpass=f=2800",
            "acompressor=threshold=-16dB:ratio=8:attack=3:release=50",
            "aecho=0.6:0.5:18:0.2",
            "dynaudnorm=f=60:g=10",
            "volume=1.5",
        ]
    elif style == "medic":
        filters += [
            "highpass=f=180",
            "lowpass=f=5000",
            "acompressor=threshold=-18dB:ratio=5:attack=5:release=70",
            "dynaudnorm=f=75:g=12",
            "volume=1.45",
        ]
    else:
        filters += ["dynaudnorm=f=80:g=12", "volume=1.4"]

    cmd = [
        ff, "-y", "-hide_banner", "-loglevel", "error",
        "-i", str(src_wav),
        "-af", ",".join(filters),
        "-t", f"{duration:.3f}",
        "-c:a", "libvorbis", "-q:a", "5",
        str(out_ogg),
    ]
    try:
        subprocess.run(cmd, check=True, timeout=60)
        return out_ogg.exists() and out_ogg.stat().st_size >= 4000
    except (subprocess.CalledProcessError, subprocess.TimeoutExpired) as e:
        print(f"  [fx fail] {out_ogg.name}: {e}")
        return False


def resolve_source(*candidates: Path) -> Path | None:
    for c in candidates:
        if c and c.is_file() and c.stat().st_size > 2000:
            return c
    return None


def prepare_sources(ff: str) -> dict[str, Path]:
    """Ensure key WAV paths exist (convert local MP3s / curated downloads)."""
    cache = SCRAPE / "curated_wav"
    cache.mkdir(parents=True, exist_ok=True)
    paths: dict[str, Path] = {}

    # Local materials
    mapping = {
        "prankedy_local0": LOCAL / "Prankedy0Actual.mp3",
        "prankedy_local1": LOCAL / "Prankedy1.mp3",
        "prankedy_local2": LOCAL / "Prankedy2.mp3",
        "prankedy_local3": LOCAL / "Prankedy3.mp3",
        "prankedy_local4": LOCAL / "Prankedy4.mp3",
        "paparazzi_local": LOCAL / "PAPARAZZI 1!! BROMA.mp3",
    }
    for key, mp3 in mapping.items():
        if mp3.is_file():
            wav = cache / f"{key}.wav"
            try:
                ensure_wav_from_any(mp3, wav)
                paths[key] = wav
            except Exception as e:
                print(f"  [skip convert] {mp3.name}: {e}")

    # Prefer extracted YT/X if present
    for key, pattern in [
        ("prankedy_yt_taco", "PRANKEDY_yt_5e945aaa8908.wav"),
        ("prankedy_yt_tank", "PRANKEDY_yt_1fa502bcf5d1.wav"),
        ("prankedy_x0", "PRANKEDY_3ecc414bf085.wav"),
        ("prankedy_x2", "PRANKEDY_922df1bc6030.wav"),
        ("paparazzi_x", "PAPARAZZI_1_e504959652ba.wav"),
        ("paparazzi_yt", "PAPARAZZI_1_yt_03ea5deaaf02.wav"),
        ("llorona_ay", "LA_LLORONA_yt_c4b0e2e925af.wav"),
        ("llorona_ay2", "LA_LLORONA_yt_024c70c231fa.wav"),
        ("llorona_story", "LA_LLORONA_a3787164ce72.wav"),
        ("llorona_movie", "LA_LLORONA_725ec6918f7e.wav"),
        ("presidenta_x", "LA_PRESIDENTA_e181ce2526d8.wav"),
        ("presidenta_x2", "LA_PRESIDENTA_3b82730cf868.wav"),
        ("charro_x", "CHARRO_NEGRO_45fe6beec278.wav"),
        ("charro_yt", "CHARRO_NEGRO_yt_f48b65d8f8af.wav"),
        ("charro_yt2", "CHARRO_NEGRO_yt_02b0dec468a6.wav"),
        ("senor_yt", "SENOR_TIENDA_yt_8f3b9d0426ea.wav"),
        ("senor_x", "SENOR_TIENDA_922df1bc6030.wav"),
        ("policia_yt", "POLICIA_CDMX_yt_cffa4ce37bdd.wav"),
        ("policia_x", "POLICIA_CDMX_8f62da8e6209.wav"),
        ("policia_h", "POLICIA_CDMX_HOMBRE_yt_bbde68ab4b40.wav"),
        ("paramed_yt", "PARAMEDICO_CRUZ_ROJA_yt_dfd3dd161ca9.wav"),
        ("paramed_x", "PARAMEDICO_CRUZ_ROJA_254b51c193d6.wav"),
        ("paramed2", "PARAMEDICO_yt_e21a7fe5c2df.wav"),
        ("escom_yt", "ESCOMBOY_yt_2662043961b3.wav"),
        ("escomg_yt", "ESCOMGIRL_yt_1189d81d0c35.wav"),
        ("rey_yt", "REY_GRUPERO_yt_018a205705f6.wav"),
        ("rey_x", "REY_GRUPERO_6d54c426b444.wav"),
        ("gran_yt", "GRANADERO_yt_c9d87f46fd1f.wav"),
        ("gran_h_yt", "POLICIA_GRANADERO_HOMBRE_yt_d06f4e44d075.wav"),
        ("gran_m_yt", "POLICIA_GRANADERO_MUJER_yt_8920332391b3.wav"),
        ("lazaro_yt", "LAZARO_yt_c021f225bb46.wav"),
    ]:
        p = EXTRACTED / pattern
        if p.is_file():
            paths[key] = p

    # Curated full downloads (YouTube audio-only)
    for key, name in [
        ("prankedy_taco_full", "prankedy_taco.wav"),
        ("prankedy_tank_full", "prankedy_tank.wav"),
        ("llorona_ay_full", "llorona_ay.wav"),
        ("senor_tienda_full", "senor_tienda.wav"),
        ("charro_draw", "charro_draw.wav"),
    ]:
        p = CURATED_RAW / name
        if p.is_file() and p.stat().st_size > 5000:
            paths[key] = p

    return paths


Recipe = tuple  # (fighter, source_key, start_or_None, dur, style, pitch, note)


def build_recipes(paths: dict[str, Path]) -> list[dict]:
    """
    Hand-curated recipes. start=None → speech-aware auto window.
    Timestamps from RMS/speech analysis of scraped material.
    """
    R: list[dict] = []

    def add(fighter: str, key: str, start: float | None, dur: float, style: str,
            pitch: float = 0.0, note: str = "", alt_keys: list[str] | None = None):
        R.append({
            "fighter": fighter,
            "key": key,
            "alt_keys": alt_keys or [],
            "start": start,
            "dur": dur,
            "style": style,
            "pitch": pitch,
            "note": note,
        })

    # ── PRANKEDY: iconic loud shout moments (YT broma / tanque / local) ──
    add("PRANKEDY", "prankedy_taco_full", None, 1.05, "shout", 0,
        "Prankedy — grito/reacción de broma viral (YT full, ventana de voz)",
        ["prankedy_yt_taco", "prankedy_tank_full", "prankedy_yt_tank", "prankedy_x0"])

    # ── PAPARAZZI: "¡broma!" energy — loudest speech windows ──
    add("PAPARAZZI_1", "paparazzi_local", 33.5, 1.05, "shout", 0,
        "Paparazzi 1 — frase/grito de BROMA (MP3 local icónico)",
        ["paparazzi_yt", "paparazzi_x"])
    add("PAPARAZZI_5", "paparazzi_local", 46.5, 1.05, "shout", -2.5,
        "Paparazzi 5 — otra frase de la broma, tono más grave",
        ["paparazzi_x", "paparazzi_yt"])

    # ── SENOR TIENDA / REY GRUPERO: universo Prankedy ──
    add("SENOR_TIENDA", "senor_tienda_full", None, 1.05, "shout", -4.0,
        "Señor Tienda — voz grave (broma Prankedy universo)",
        ["senor_yt", "senor_x", "prankedy_x2"])
    add("REY_GRUPERO", "rey_yt", None, 1.0, "shout", -3.0,
        "Rey Grupero — grito grave estilo grupero",
        ["rey_x", "prankedy_local3", "prankedy_tank_full"])

    # ── LA PRESIDENTA: voz real de clip X (oficial) ──
    add("LA_PRESIDENTA", "presidenta_x", 57.5, 1.1, "speech", 0,
        "Presidenta — frase hablada (X @Claudiashein)",
        ["presidenta_x2"])
    # Yoalli: spectral/horror (not political voice) for mythical form
    add("YOALLI_EHECATL", "llorona_ay_full", 11.5, 1.1, "horror", 4.0,
        "Yoalli — lamento místico tenebroso",
        ["llorona_ay", "llorona_ay2", "presidenta_x"])

    # ── LA LLORONA: "Ay mis hijos" audio corto icónico ──
    add("LA_LLORONA", "llorona_ay_full", 11.8, 1.15, "horror", -1.0,
        "La Llorona — lamento 'Ay mis hijos' (audio icónico YT)",
        ["llorona_ay", "llorona_ay2", "llorona_story"])
    add("LA_TZITZIMIME", "llorona_ay_full", 11.5, 1.15, "horror", -8.0,
        "Tzitzimime — lamento monstruoso más grave y húmedo",
        ["llorona_movie", "llorona_story", "llorona_ay"])

    # ── CHARRO NEGRO: narración tenebrosa ──
    add("CHARRO_NEGRO", "charro_x", 94.0, 1.15, "metal", -2.5,
        "Charro Negro — voz oscura de leyenda (tenebroso)",
        ["charro_yt", "charro_yt2", "charro_draw"])

    # ── POLICÍA / GRANADEROS ──
    add("POLICIA_CDMX", "policia_yt", None, 0.95, "radio", 0,
        "Policía CDMX — voz institucional tipo radio",
        ["policia_x"])
    add("POLICIA_CDMX_HOMBRE", "policia_h", None, 0.95, "radio", -1.0,
        "Policía hombre — radio grave",
        ["policia_yt", "policia_x"])
    add("POLICIA_GRANADERO_HOMBRE", "gran_h_yt", None, 0.95, "radio", -3.0,
        "Granadero H — radio pesado",
        ["gran_yt", "policia_x"])
    add("POLICIA_GRANADERO_MUJER", "gran_m_yt", None, 0.95, "radio", 3.0,
        "Granadera — radio agudo",
        ["policia_yt"])
    add("GRANADERO", "gran_yt", None, 0.95, "radio", -2.5,
        "Granadero — operativo",
        ["gran_h_yt", "policia_x"])

    # ── PARAMÉDICOS ──
    add("PARAMEDICO_CRUZ_ROJA", "paramed_yt", None, 0.95, "medic", 0,
        "Cruz Roja — voz/anuncio institucional",
        ["paramed_x", "paramed2"])
    add("PARAMEDICO", "paramed2", None, 0.95, "medic", 1.0,
        "Paramédico — urgente",
        ["paramed_yt", "paramed_x"])

    # ── ESTUDIANTES / ROBOT / LÁZARO ──
    add("ESCOMBOY", "escom_yt", None, 0.95, "shout", 2.0,
        "ESCOM boy — grito joven",
        ["prankedy_yt_taco", "prankedy_x0"])
    add("ESCOMGIRL", "escomg_yt", None, 0.95, "shout", 4.0,
        "ESCOM girl — grito agudo",
        ["paparazzi_yt", "prankedy_yt_taco"])
    add("ROBOT", "presidenta_x2", 30.0, 1.0, "robot", 0,
        "Robot estudiantx — voz procesada",
        ["presidenta_x", "prankedy_x0"])
    add("LAZARO", "lazaro_yt", None, 0.95, "shout", -1.0,
        "Lázaro — grito pelea",
        ["prankedy_x0", "prankedy_yt_tank"])

    return R


def materialize_clip(
    paths: dict[str, Path],
    recipe: dict,
    tmp: Path,
) -> Path | None:
    keys = [recipe["key"]] + list(recipe.get("alt_keys") or [])
    for key in keys:
        src = paths.get(key)
        if not src or not src.is_file():
            continue
        try:
            samples, sr = read_mono(src)
        except Exception as e:
            print(f"  [read fail] {key}: {e}")
            continue
        # skip near-silent sources
        rms_all = math.sqrt(sum(x * x for x in samples[::max(1, len(samples)//5000)]) / 5000)
        if rms_all < 0.005:
            print(f"  [silent] {key} rms={rms_all:.4f}")
            continue
        try:
            if recipe["start"] is not None:
                chunk = slice_sec(samples, sr, float(recipe["start"]), float(recipe["dur"]))
                # if slice is quiet, fall back to speech window
                rms = math.sqrt(sum(x * x for x in chunk) / len(chunk))
                if rms < 0.02:
                    print(f"  [quiet slice] {key}@{recipe['start']}s → auto speech")
                    chunk = best_speech_window(samples, sr, dur=float(recipe["dur"]))
            else:
                chunk = best_speech_window(samples, sr, dur=float(recipe["dur"]))
        except Exception as e:
            print(f"  [slice fail] {key}: {e}")
            continue
        rms = math.sqrt(sum(x * x for x in chunk) / len(chunk))
        if rms < 0.015:
            print(f"  [weak] {key} chunk rms={rms:.4f}")
            continue
        out = tmp / f"{recipe['fighter']}_cut.wav"
        write_mono(out, chunk, sr)
        print(f"  [cut] {recipe['fighter']} <- {key} rms={rms:.3f} note={recipe['note'][:50]}")
        return out
    return None


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--install", action="store_true")
    args = ap.parse_args()

    ff = ffmpeg()
    OUT.mkdir(parents=True, exist_ok=True)
    tmp = SCRAPE / "_tmp_curate"
    tmp.mkdir(parents=True, exist_ok=True)

    print("=== Preparing sources ===")
    paths = prepare_sources(ff)
    print(f"  {len(paths)} source keys ready")
    for k, p in sorted(paths.items()):
        print(f"    {k}: {p.name} ({p.stat().st_size//1024} KB)")

    recipes = build_recipes(paths)
    report: dict[str, dict] = {}
    ok_n = 0

    print("\n=== Curating specials ===")
    for r in recipes:
        fighter = r["fighter"]
        cut = materialize_clip(paths, r, tmp)
        if not cut:
            print(f"  [FAIL] {fighter} — no usable source")
            report[fighter] = {"status": "fail", "note": r["note"]}
            continue
        out = OUT / f"special_{fighter.lower()}.ogg"
        if run_ffmpeg_fx(ff, cut, out, r["style"], pitch_st=r["pitch"], duration=min(1.15, r["dur"] + 0.05)):
            print(f"  [OK] {fighter} style={r['style']} pitch={r['pitch']} -> {out.stat().st_size} B")
            report[fighter] = {
                "status": "ok",
                "style": r["style"],
                "pitch": r["pitch"],
                "note": r["note"],
                "bytes": out.stat().st_size,
            }
            ok_n += 1
        else:
            report[fighter] = {"status": "fx_fail", "note": r["note"]}

    report_path = SCRAPE / "curate_report.json"
    import json
    report_path.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")

    if args.install:
        ASSETS.mkdir(parents=True, exist_ok=True)
        for ogg in OUT.glob("special_*.ogg"):
            dst = ASSETS / ogg.name
            shutil.copy2(ogg, dst)
            print(f"  [install] {dst.name}")

    print(f"\n=== Curated {ok_n}/{len(recipes)} specials ===")
    print(f"Report: {report_path}")
    return 0 if ok_n == len(recipes) else 2


if __name__ == "__main__":
    sys.exit(main())
