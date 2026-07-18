#!/usr/bin/env python3
"""
process_charro_nahual.py — Charro Negro / nahual SFX

Fuentes canónicas (orden de preferencia):
  https://www.youtube.com/watch?v=0bYg6FmCjhE
  https://www.youtube.com/watch?v=xlYlM4WgqYU
  https://www.youtube.com/watch?v=DK5ZgKoM8Xw  (Sonido Nahual)

No scrapea "frase hablada"; SFX + subtítulo inventado:
  «¡El nahual despierta!» / The nahual awakens!
"""
from __future__ import annotations

import json
import shutil
import subprocess
import sys
from pathlib import Path

import numpy as np

TOOLS = Path(__file__).resolve().parent
SCRAPE = TOOLS / "sf_voice_scrape"
REPO = TOOLS.parent.parent
LOCAL = REPO / "nuevoMaterial17JUL"
RAW_YT = SCRAPE / "raw_yt"
OUT = SCRAPE / "out_diarized" / "CHARRO_NEGRO"

YT_IDS = ["0bYg6FmCjhE", "xlYlM4WgqYU", "DK5ZgKoM8Xw"]
PHRASE = {
    "es": "¡El nahual despierta!",
    "en": "The nahual awakens!",
    "hud": "EL NAHUAL DESPIERTA",
}


def ffmpeg() -> str:
    return shutil.which("ffmpeg") or shutil.which("ffmpeg.exe") or "ffmpeg"


def try_download(yt_id: str) -> Path | None:
    RAW_YT.mkdir(parents=True, exist_ok=True)
    wav = RAW_YT / f"CHARRO_NEGRO_{yt_id}.wav"
    if wav.is_file() and wav.stat().st_size > 5000:
        return wav
    out = str(RAW_YT / f"CHARRO_NEGRO_{yt_id}.%(ext)s")
    url = f"https://www.youtube.com/watch?v={yt_id}"
    cmd = [
        sys.executable, "-m", "yt_dlp",
        "--js-runtimes", "node", "--remote-components", "ejs:github",
        "--cookies-from-browser", "firefox",
        "-f", "bestaudio/best", "-x", "--audio-format", "wav", "--audio-quality", "0",
        "-o", out, url,
    ]
    print("download", yt_id, flush=True)
    try:
        subprocess.run(cmd, check=False, timeout=180)
    except Exception as e:
        print("  fail", e)
    if wav.is_file() and wav.stat().st_size > 5000:
        return wav
    return None


def collect_sources() -> list[Path]:
    found: list[Path] = []
    for yid in YT_IDS:
        p = try_download(yid)
        if p:
            found.append(p)
    # any other local charro/nahual
    for folder in (RAW_YT, LOCAL):
        if not folder.is_dir():
            continue
        for p in folder.iterdir():
            if not p.is_file():
                continue
            n = p.name.lower()
            if p.suffix.lower() not in {".wav", ".mp3", ".m4a", ".webm"}:
                continue
            if ("charro" in n or "nahual" in n) and p.stat().st_size > 5000:
                if p not in found:
                    found.append(p)
    return found


def read_wav(path: Path) -> tuple[np.ndarray, int]:
    import wave
    # ensure mono 44.1
    tmp = OUT / "_tmp_read.wav"
    OUT.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        [ffmpeg(), "-y", "-hide_banner", "-loglevel", "error",
         "-i", str(path), "-ac", "1", "-ar", "44100", "-c:a", "pcm_s16le", str(tmp)],
        check=True, timeout=120,
    )
    with wave.open(str(tmp), "rb") as w:
        sr = w.getframerate()
        n = w.getnframes()
        raw = w.readframes(n)
    data = np.frombuffer(raw, dtype=np.int16).astype(np.float32) / 32768.0
    return data, sr


def best_burst(x: np.ndarray, sr: int, prefer_dur: float = 2.8) -> tuple[np.ndarray, float, float]:
    """Loudest window; short files used whole (capped)."""
    dur = len(x) / sr
    if dur <= 6.5:
        peak = float(np.max(np.abs(x))) + 1e-9
        return x / peak * 0.92, 0.0, dur
    win = int(prefer_dur * sr)
    hop = max(1, win // 10)
    best_i, best_r = 0, -1.0
    for i in range(0, len(x) - win, hop):
        c = x[i : i + win]
        r = float(np.sqrt(np.mean(c * c)))
        # slight boost for higher spectral energy (less pure silence)
        if r > best_r:
            best_r, best_i = r, i
    chunk = x[best_i : best_i + win]
    peak = float(np.max(np.abs(chunk))) + 1e-9
    return chunk / peak * 0.92, best_i / sr, prefer_dur


def write_wav(path: Path, samples: np.ndarray, sr: int) -> None:
    import wave
    path.parent.mkdir(parents=True, exist_ok=True)
    ints = np.clip(samples * 32767.0, -32767, 32767).astype(np.int16)
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(sr)
        w.writeframes(ints.tobytes())


def to_ogg(src: Path, dst: Path, dur: float) -> bool:
    # dark / metal nahual flavor
    fade = min(0.18, max(0.06, dur * 0.12))
    af = (
        f"highpass=f=70,lowpass=f=5500,asetrate=44100*0.90,aresample=44100,"
        f"aecho=0.8:0.85:55:0.35,"
        f"afade=t=in:d=0.04,afade=t=out:st={max(0.1, dur - fade)}:d={fade},"
        f"dynaudnorm=f=80:g=12,volume=1.4"
    )
    try:
        subprocess.run(
            [ffmpeg(), "-y", "-hide_banner", "-loglevel", "error",
             "-i", str(src), "-af", af, "-t", f"{min(dur, 6.0):.3f}",
             "-c:a", "libvorbis", "-q:a", "5", str(dst)],
            check=True, timeout=60,
        )
        return dst.exists() and dst.stat().st_size > 2000
    except Exception as e:
        print("ogg fail", e)
        return False


def score_source(path: Path) -> float:
    """Prefer canon YT ids and louder clips."""
    n = path.name
    score = path.stat().st_size / 1e6
    for i, yid in enumerate(YT_IDS):
        if yid in n:
            score += 50 - i * 5
    return score


def main() -> int:
    print("=== Charro Negro / Nahual ===")
    print("YT:", ", ".join(YT_IDS))
    sources = collect_sources()
    if not sources:
        print("No sources. Drop files as raw_yt/CHARRO_NEGRO_<id>.wav")
        return 2
    sources = sorted(sources, key=score_source, reverse=True)
    print("sources:", [p.name for p in sources])

    best = None  # (score, meta, chunk, sr)
    for path in sources:
        try:
            x, sr = read_wav(path)
        except Exception as e:
            print("read fail", path.name, e)
            continue
        rms = float(np.sqrt(np.mean(x * x)))
        if rms < 0.005:
            print("silent", path.name)
            continue
        chunk, t0, dur = best_burst(x, sr)
        sc = float(np.sqrt(np.mean(chunk * chunk))) * (1.0 + 0.02 * score_source(path))
        print(f"  {path.name}: rms={rms:.3f} pick {dur:.2f}s @ {t0:.1f}s score={sc:.3f}")
        if best is None or sc > best[0]:
            best = (sc, {"src": path.name, "t0": round(t0, 2), "dur": round(dur, 3)}, chunk, sr)

    if not best:
        print("no usable audio")
        return 1

    sc, meta, chunk, sr = best
    OUT.mkdir(parents=True, exist_ok=True)
    cut = OUT / "special_voice.wav"
    write_wav(cut, chunk, sr)
    ogg = OUT / "special_charro_negro.ogg"
    ok = to_ogg(cut, ogg, float(meta["dur"]))

    # copy full preferred sources into converted
    conv = SCRAPE / "converted" / "CHARRO_NEGRO" / "sources"
    conv.mkdir(parents=True, exist_ok=True)
    for path in sources[:3]:
        try:
            dst = conv / f"nahual_{path.stem}.wav"
            if path.suffix.lower() == ".wav":
                shutil.copy2(path, dst)
            else:
                subprocess.run(
                    [ffmpeg(), "-y", "-hide_banner", "-loglevel", "error",
                     "-i", str(path), "-ac", "1", "-ar", "44100", str(dst)],
                    check=False, timeout=120,
                )
        except Exception:
            pass

    info = {
        "fighter": "CHARRO_NEGRO",
        "youtube_ids": YT_IDS,
        "source": meta["src"],
        "t0": meta["t0"],
        "dur": meta["dur"],
        "score": round(sc, 4),
        "phrase_es": PHRASE["es"],
        "phrase_en": PHRASE["en"],
        "phrase_hud": PHRASE["hud"],
        "ogg": str(ogg) if ok else None,
        "ready": ok,
    }
    (OUT / "meta.json").write_text(json.dumps(info, indent=2, ensure_ascii=False), encoding="utf-8")
    print("BEST:", info)

    print("Rebuilding pack...")
    subprocess.run([sys.executable, str(TOOLS / "build_special_phrases_pack.py")], check=False)
    print(f"OK → {ogg}")
    print(f"Review → {SCRAPE / 'REVIEW_MP3_TODOS'}")
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
