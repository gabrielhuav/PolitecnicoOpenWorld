#!/usr/bin/env python3
"""
process_paparazzi5.py — Paparazzi 5 oficial

Fuente canónica (obligatoria, distinta de Pap1):
  https://www.youtube.com/watch?v=AEmVeK88HIs

Vídeo AGE-RESTRICTED: yt-dlp necesita cookies de cuenta YouTube logueada
(Chrome/Edge con sesión). Si falla, deja el archivo en:

  nuevoMaterial17JUL/PAPARAZZI_5_AEmVeK88HIs.mp3
  o raw_yt/PAPARAZZI_5_AEmVeK88HIs.wav

Pipeline:
  - diariza hablantes → rank 1 = paparazzi (segundo principal; rank 0 = Prankedy)
  - exporta special a out_diarized/PAPARAZZI_5/
  - rebuild pack/review

Uso:
  python PolitecnicoOpenWorld/tools/process_paparazzi5.py
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
OUT = SCRAPE / "out_diarized" / "PAPARAZZI_5"
YT_ID = "AEmVeK88HIs"
YT_URL = f"https://www.youtube.com/watch?v={YT_ID}"

# Import diarize helpers
sys.path.insert(0, str(TOOLS))
from diarize_special_voices import (  # type: ignore
    speech_segments,
    segment_features,
    cluster_speakers,
    rank_speakers,
    pick_role,
    best_phrase_from_speaker,
    write_wav,
    to_ogg,
    ensure_wav,
)


def ffmpeg() -> str:
    return shutil.which("ffmpeg") or shutil.which("ffmpeg.exe") or "ffmpeg"


def find_source() -> Path | None:
    candidates = [
        LOCAL / f"PAPARAZZI_5_{YT_ID}.mp3",
        LOCAL / f"PAPARAZZI_5_{YT_ID}.wav",
        LOCAL / "PAPARAZZI_5_OFFICIAL.mp3",
        LOCAL / "PAPARAZZI_5_OFFICIAL.wav",
        RAW_YT / f"PAPARAZZI_5_{YT_ID}.wav",
        RAW_YT / f"PAPARAZZI_5_{YT_ID}.mp3",
        RAW_YT / f"PAPARAZZI_5_{YT_ID}.webm",
        RAW_YT / f"PAPARAZZI_5_{YT_ID}.m4a",
    ]
    for p in candidates:
        if p.is_file() and p.stat().st_size > 50_000:
            return p
    for folder in (LOCAL, RAW_YT):
        if not folder.is_dir():
            continue
        for p in folder.iterdir():
            n = p.name.lower()
            if p.is_file() and YT_ID.lower() in n and p.suffix.lower() in {
                ".mp3", ".wav", ".m4a", ".webm", ".mp4"
            }:
                if p.stat().st_size > 50_000:
                    return p
    return None


def try_download() -> Path | None:
    RAW_YT.mkdir(parents=True, exist_ok=True)
    out = str(RAW_YT / f"PAPARAZZI_5_{YT_ID}.%(ext)s")
    attempts = [
        ["--cookies-from-browser", "firefox", "--js-runtimes", "node",
         "--remote-components", "ejs:github", "--age-limit", "99",
         "--extractor-args", "youtube:player_client=web,web_embedded"],
        ["--cookies-from-browser", "chrome", "--js-runtimes", "node", "--age-limit", "99"],
        ["--cookies-from-browser", "edge", "--js-runtimes", "node", "--age-limit", "99"],
    ]
    for extra in attempts:
        cmd = [
            sys.executable, "-m", "yt_dlp", *extra,
            "-f", "bestaudio/best", "-x", "--audio-format", "wav", "--audio-quality", "0",
            "-o", out, YT_URL,
        ]
        print("try:", " ".join(extra[:4]), flush=True)
        try:
            subprocess.run(cmd, check=False, timeout=240)
        except Exception as e:
            print("  fail", e)
        p = RAW_YT / f"PAPARAZZI_5_{YT_ID}.wav"
        if p.is_file() and p.stat().st_size > 50_000:
            return p
    return None


def process(src: Path) -> dict:
    print(f"Processing {src} ({src.stat().st_size} bytes)")
    cache = OUT / "_cache"
    wav = ensure_wav(src, cache)
    if not wav:
        raise RuntimeError("convert failed")

    # reuse diarize read
    import wave
    with wave.open(str(wav), "rb") as w:
        ch, sw, sr, n = w.getnchannels(), w.getsampwidth(), w.getframerate(), w.getnframes()
        # limit 12 min
        n = min(n, int(sr * 720))
        raw = w.readframes(n)
    data = np.frombuffer(raw, dtype=np.int16).astype(np.float32)
    if ch > 1:
        data = data.reshape(-1, ch).mean(axis=1)
    data /= 32768.0

    segs = speech_segments(data, sr)
    print(f"segments: {len(segs)}")
    feats, keep = [], []
    for a, b in segs:
        if b - a > int(8 * sr):
            b = a + int(8 * sr)
        c = data[a:b]
        if c.size < int(0.3 * sr):
            continue
        feats.append(segment_features(c, sr))
        keep.append((a, b))

    role = 1  # paparazzi = segundo principal
    if len(feats) >= 2:
        F = np.stack(feats)
        labels = cluster_speakers(F, max_k=3)
        speakers = rank_speakers(labels, keep, sr, F)
        chosen = pick_role(speakers, role)
        print("speakers:", [(s["total_sec"], s["mean_pitch"]) for s in speakers], "chosen", chosen)
        phrase = best_phrase_from_speaker(data, sr, keep, chosen["idxs"]) if chosen else None
    else:
        speakers = []
        phrase = None
        if keep:
            a, b = keep[0]
            phrase = (data[a:b], a / sr, (b - a) / sr)

    if not phrase:
        # fallback loudest 3s
        win = int(3.0 * sr)
        hop = max(1, win // 8)
        bi, br = 0, -1.0
        for i in range(0, max(1, len(data) - win), hop):
            r = float(np.sqrt(np.mean(data[i:i + win] ** 2)))
            if r > br:
                br, bi = r, i
        chunk = data[bi:bi + win]
        peak = float(np.max(np.abs(chunk))) + 1e-9
        phrase = (chunk / peak * 0.92, bi / sr, 3.0)
        print("fallback peak window")

    chunk, t0, dur = phrase
    if isinstance(chunk, np.ndarray):
        peak = float(np.max(np.abs(chunk))) + 1e-9
        chunk = chunk / peak * 0.92

    OUT.mkdir(parents=True, exist_ok=True)
    cut = OUT / "special_voice.wav"
    write_wav(cut, chunk, sr if sr else 44100)
    # resample note: write_wav uses given sr
    ogg = OUT / "special_paparazzi_5.ogg"
    ok = to_ogg(cut, ogg, float(dur))

    # also place into converted sources for future
    conv = SCRAPE / "converted" / "PAPARAZZI_5" / "sources"
    conv.mkdir(parents=True, exist_ok=True)
    shutil.copy2(wav, conv / f"official_{YT_ID}.wav")

    meta = {
        "fighter": "PAPARAZZI_5",
        "youtube_id": YT_ID,
        "youtube_url": YT_URL,
        "source": str(src),
        "t0": round(float(t0), 2),
        "dur": round(float(dur), 3),
        "role": "rank1_paparazzi",
        "n_speakers": len(speakers) if speakers else 0,
        "phrase_es": "¡Cámara, cámara!",
        "phrase_en": "Camera, camera!",
        "ogg": str(ogg) if ok else None,
        "ready": ok,
    }
    (OUT / "meta.json").write_text(json.dumps(meta, indent=2, ensure_ascii=False), encoding="utf-8")
    print(json.dumps(meta, ensure_ascii=False, indent=2))
    return meta


def main() -> int:
    print("=== PAPARAZZI_5 official ===")
    print(YT_URL)
    src = find_source()
    if not src:
        print("No local file — trying yt-dlp (age-restricted)...")
        src = try_download()
    if not src:
        print(
            "\n[AGE-GATE] No se pudo bajar AEmVeK88HIs (confirm age + cookies).\n"
            "Descarga el audio manualmente y guárdalo como:\n"
            f"  {LOCAL / f'PAPARAZZI_5_{YT_ID}.mp3'}\n"
            f"  o {RAW_YT / f'PAPARAZZI_5_{YT_ID}.wav'}\n"
            "Luego: python PolitecnicoOpenWorld/tools/process_paparazzi5.py\n"
        )
        (SCRAPE / "PAPARAZZI_5_PENDING.md").write_text(
            f"# Paparazzi 5 pendiente\n\n- URL: {YT_URL}\n"
            f"- Drop: `nuevoMaterial17JUL/PAPARAZZI_5_{YT_ID}.mp3`\n"
            f"- Luego: `python PolitecnicoOpenWorld/tools/process_paparazzi5.py`\n",
            encoding="utf-8",
        )
        Start = False
        try:
            import webbrowser
            webbrowser.open(YT_URL)
        except Exception:
            pass
        return 2

    meta = process(src)
    print("Rebuilding pack...")
    subprocess.run([sys.executable, str(TOOLS / "build_special_phrases_pack.py")], check=False)
    return 0 if meta.get("ready") else 1


if __name__ == "__main__":
    raise SystemExit(main())
