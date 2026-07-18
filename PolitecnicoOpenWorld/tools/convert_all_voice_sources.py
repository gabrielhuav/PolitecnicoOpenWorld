#!/usr/bin/env python3
"""
convert_all_voice_sources.py — Convertir y organizar TODO el audio disponible
para peleadors SF (sin instalar en assets).

Pipeline:
  1) Inventariar locales (nuevoMaterial17JUL), extracted/, raw_yt/, raw_curated/, raw/
  2) Convertir a mono 44.1k WAV en converted/<FIGHTER>/sources/
  3) Extraer top-K picos de habla a converted/<FIGHTER>/clips/
  4) Escribir converted/CONVERSION_STATUS.md

No toca app/src/main/assets.
"""
from __future__ import annotations

import hashlib
import json
import math
import shutil
import struct
import subprocess
import wave
from pathlib import Path

TOOLS = Path(__file__).resolve().parent
SCRAPE = TOOLS / "sf_voice_scrape"
REPO = TOOLS.parent.parent
OUT = SCRAPE / "converted"
EXTRACTED = SCRAPE / "extracted"
RAW_YT = SCRAPE / "raw_yt"
RAW_CUR = SCRAPE / "raw_curated"
RAW = SCRAPE / "raw"
LOCAL = REPO / "nuevoMaterial17JUL"

# Mapeo fuente → peleadór(es) que pueden usarla
SOURCE_MAP: list[tuple[str, str, str]] = [
    # (fighter, label, path relative to REPO or absolute under SCRAPE)
]

# Local materials
LOCAL_MAP = {
    "PRANKEDY": [
        "Prankedy0Actual.mp3", "Prankedy1.mp3", "Prankedy2.mp3",
        "Prankedy3.mp3", "Prankedy4.mp3", "Prankedy5Actual.mp3", "Prankedy6Actual.mp3",
    ],
    "PAPARAZZI_1": ["PAPARAZZI 1!! BROMA.mp3"],
    # Pap5 must be official video — no local exclusive yet
    "SENOR_TIENDA": [],  # filled when YT lands; share prankedy only as last resort marked DERIVED
}

# extracted prefix → fighter
EXTRACTED_PREFIXES = {
    "PRANKEDY": "PRANKEDY",
    "PAPARAZZI_1": "PAPARAZZI_1",
    "PAPARAZZI_5": "PAPARAZZI_5",
    "SENOR_TIENDA": "SENOR_TIENDA",
    "REY_GRUPERO": "REY_GRUPERO",
    "LA_PRESIDENTA": "LA_PRESIDENTA",
    "LA_LLORONA": "LA_LLORONA",
    "LA_TZITZIMIME": "LA_TZITZIMIME",
    "YOALLI_EHECATL": "YOALLI_EHECATL",
    "CHARRO_NEGRO": "CHARRO_NEGRO",
    "PARAMEDICO_CRUZ_ROJA": "PARAMEDICO_CRUZ_ROJA",
    "PARAMEDICO": "PARAMEDICO",
    "POLICIA_CDMX_HOMBRE": "POLICIA_CDMX_HOMBRE",
    "POLICIA_CDMX": "POLICIA_CDMX",
    "POLICIA_GRANADERO_HOMBRE": "POLICIA_GRANADERO_HOMBRE",
    "POLICIA_GRANADERO_MUJER": "POLICIA_GRANADERO_MUJER",
    "ESCOMBOY": "ESCOMBOY",
    "ESCOMGIRL": "ESCOMGIRL",
    "ROBOT": "ROBOT",
    "LAZARO": "LAZARO",
    "GRANADERO": "GRANADERO",
}

ARCADE = [
    "ESCOMBOY", "ESCOMGIRL", "ROBOT", "PRANKEDY", "SENOR_TIENDA",
    "PAPARAZZI_1", "PAPARAZZI_5", "REY_GRUPERO", "PARAMEDICO_CRUZ_ROJA",
    "POLICIA_CDMX_HOMBRE", "POLICIA_CDMX", "POLICIA_GRANADERO_HOMBRE",
    "POLICIA_GRANADERO_MUJER", "CHARRO_NEGRO", "LA_LLORONA",
    "LA_TZITZIMIME", "YOALLI_EHECATL", "LA_PRESIDENTA",
]


def ffmpeg() -> str:
    return shutil.which("ffmpeg") or shutil.which("ffmpeg.exe") or "ffmpeg"


def to_wav(src: Path, dst: Path) -> bool:
    if dst.exists() and dst.stat().st_size > 5000:
        return True
    dst.parent.mkdir(parents=True, exist_ok=True)
    try:
        subprocess.run(
            [ffmpeg(), "-y", "-hide_banner", "-loglevel", "error",
             "-i", str(src), "-vn", "-ac", "1", "-ar", "44100", "-c:a", "pcm_s16le", str(dst)],
            check=True, timeout=300,
        )
        return dst.exists() and dst.stat().st_size > 5000
    except Exception as e:
        print(f"  [fail] {src.name}: {e}")
        return False


def read_mono(path: Path, max_sec: float = 360.0):
    with wave.open(str(path), "rb") as w:
        ch, sw, sr, n = w.getnchannels(), w.getsampwidth(), w.getframerate(), w.getnframes()
        n = min(n, int(sr * max_sec))
        raw = w.readframes(n)
    samples = struct.unpack("<" + "h" * (len(raw) // 2), raw)
    if ch == 1:
        mono = [s / 32768.0 for s in samples]
    else:
        mono = [sum(samples[i:i + ch]) / (ch * 32768.0) for i in range(0, len(samples), ch)]
    return mono, sr


def write_mono(path: Path, samples, sr: int):
    path.parent.mkdir(parents=True, exist_ok=True)
    ints = [max(-32767, min(32767, int(s * 32767))) for s in samples]
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(sr)
        w.writeframes(struct.pack("<" + "h" * len(ints), *ints))


def tight_trim(samples, sr, thr=0.035):
    """Quita silencio/relleno al inicio y fin — solo el burst claro."""
    if not samples:
        return samples
    win = max(1, int(0.012 * sr))
    env = []
    for i in range(0, len(samples), win):
        chunk = samples[i : i + win]
        env.append(math.sqrt(sum(x * x for x in chunk) / max(1, len(chunk))))
    peak = max(env) if env else 0.0
    gate = max(thr, peak * 0.12)
    first, last = 0, len(env) - 1
    for i, e in enumerate(env):
        if e >= gate:
            first = i
            break
    for i in range(len(env) - 1, -1, -1):
        if env[i] >= gate:
            last = i
            break
    a = max(0, first * win - int(0.02 * sr))
    b = min(len(samples), (last + 1) * win + int(0.04 * sr))
    return samples[a:b]


def peaks(samples, sr, win_s=0.95, top_k=4):
    """Picos de HABLA cortos (~0.95s). No exporta vídeo completo."""
    win = int(win_s * sr)
    if len(samples) < win:
        return [(0.0, 0.0)] if samples else []
    hop = max(1, win // 8)
    # saltar logo/intro en fuentes largas
    i0 = int(0.15 * sr) if len(samples) > sr * 5 else 0
    scored = []
    for i in range(i0, len(samples) - win, hop):
        chunk = samples[i:i + win]
        rms = math.sqrt(sum(x * x for x in chunk) / len(chunk))
        if rms < 0.025:  # más estricto: nada de relleno bajo
            continue
        zc = sum(1 for a, b in zip(chunk[::2], chunk[1::2]) if (a >= 0) != (b >= 0)) / max(1, len(chunk) // 2)
        diffs = [abs(chunk[j + 1] - chunk[j]) for j in range(0, len(chunk) - 1, 5)]
        mid = math.sqrt(sum(d * d for d in diffs) / max(1, len(diffs)))
        score = rms * (0.35 + zc * 2.2) * (0.25 + mid * 5.0)
        if rms > 0.40 and zc < 0.035:  # bed musical
            score *= 0.35
        scored.append((score, i))
    scored.sort(reverse=True)
    out, used = [], []
    for score, i in scored:
        if any(abs(i - u) < win * 0.9 for u in used):
            continue
        used.append(i)
        out.append((i / sr, score))
        if len(out) >= top_k:
            break
    return out


def fighter_from_name(name: str) -> str | None:
    upper = name.upper()
    # longest prefix first
    for pref in sorted(EXTRACTED_PREFIXES.keys(), key=len, reverse=True):
        if upper.startswith(pref):
            return EXTRACTED_PREFIXES[pref]
    return None


def collect_sources() -> dict[str, list[tuple[str, Path, str]]]:
    """fighter -> list of (label, path, quality: CANON|GOOD|DERIVED)"""
    out: dict[str, list[tuple[str, Path, str]]] = {f: [] for f in ARCADE}
    out.setdefault("LAZARO", [])
    out.setdefault("GRANADERO", [])
    out.setdefault("PARAMEDICO", [])
    out.setdefault("ROBOT", [])

    # locals
    if LOCAL.is_dir():
        for f, names in LOCAL_MAP.items():
            for n in names:
                p = LOCAL / n
                if p.is_file():
                    out[f].append((f"local_{p.stem}", p, "CANON" if "PAPARAZZI" in n.upper() or "Prankedy" in n else "GOOD"))

        # assign all Prankedy mp3 also as GOOD for prankedy
        for p in LOCAL.glob("Prankedy*.mp3"):
            if not any(x[1] == p for x in out["PRANKEDY"]):
                out["PRANKEDY"].append((f"local_{p.stem}", p, "GOOD"))
        pap = list(LOCAL.glob("*PAPARAZZI*")) + list(LOCAL.glob("*paparazzi*"))
        for p in pap:
            if p.is_file() and not any(x[1] == p for x in out["PAPARAZZI_1"]):
                out["PAPARAZZI_1"].append((f"local_{p.stem}", p, "CANON"))

    # extracted / raw_yt / raw_curated / raw
    for folder in (EXTRACTED, RAW_YT, RAW_CUR, RAW, SCRAPE / "raw_phrases_v2"):
        if not folder.is_dir():
            continue
        for p in folder.iterdir():
            if not p.is_file():
                continue
            if p.suffix.lower() not in {".wav", ".mp3", ".m4a", ".webm", ".mp4", ".ogg"}:
                continue
            # skip tiny silent fails
            if p.stat().st_size < 8000:
                continue
            fid = fighter_from_name(p.name)
            # curated named files
            low = p.name.lower()
            if "paparazzi1" in low or "paparazzi_1" in low:
                fid = "PAPARAZZI_1"
            elif "paparazzi5" in low or "paparazzi_5" in low:
                fid = "PAPARAZZI_5"
            elif "tienda" in low or "senor" in low:
                fid = "SENOR_TIENDA"
            elif "llorona" in low or "llorona_ay" in low:
                fid = "LA_LLORONA"
            elif "charro" in low:
                fid = "CHARRO_NEGRO"
            elif "cruzroja" in low or "cruz_roja" in low:
                fid = "PARAMEDICO_CRUZ_ROJA"
            elif "prankedy" in low or "taco" in low or "tanque" in low or "drivethru" in low:
                fid = fid or "PRANKEDY"
            if not fid:
                continue
            q = "GOOD"
            if "yt_" in p.name or p.parent.name in ("raw_yt", "raw_curated", "raw_phrases_v2"):
                q = "GOOD"
            if p.suffix.lower() == ".mp3" and "PAPARAZZI" in p.name.upper():
                q = "CANON"
            out.setdefault(fid, []).append((f"{p.parent.name}_{p.stem}"[:40], p, q))

    # dedupe by resolved path
    for f in list(out.keys()):
        seen = set()
        uniq = []
        for lab, path, q in out[f]:
            rp = str(path.resolve())
            if rp in seen:
                continue
            seen.add(rp)
            uniq.append((lab, path, q))
        out[f] = uniq
    return out


def convert_and_clip(sources: dict) -> dict:
    status = {}
    for fighter, items in sorted(sources.items()):
        if fighter not in ARCADE and fighter not in ("LAZARO", "GRANADERO", "PARAMEDICO", "ROBOT"):
            continue
        src_dir = OUT / fighter / "sources"
        clip_dir = OUT / fighter / "clips"
        src_dir.mkdir(parents=True, exist_ok=True)
        clip_dir.mkdir(parents=True, exist_ok=True)
        clips = []
        sources_ok = []
        for lab, path, quality in items:
            safe = "".join(c if c.isalnum() or c in "-_" else "_" for c in lab)[:50]
            wav = src_dir / f"{safe}.wav"
            if not to_wav(path, wav):
                continue
            sources_ok.append({"label": lab, "from": str(path), "wav": str(wav.relative_to(OUT)), "quality": quality, "bytes": wav.stat().st_size})
            try:
                samples, sr = read_mono(wav)
            except Exception as e:
                print(f"  read fail {wav.name}: {e}")
                continue
            for k, (t0, score) in enumerate(peaks(samples, sr, win_s=0.95, top_k=4)):
                a = int(t0 * sr)
                b = a + int(0.95 * sr)
                chunk = samples[a:b]
                if not chunk:
                    continue
                # recorte al burst claro + hard max 1.05s
                chunk = tight_trim(chunk, sr)
                max_n = int(1.05 * sr)
                if len(chunk) > max_n:
                    chunk = chunk[:max_n]
                if len(chunk) < int(0.5 * sr):
                    continue
                peak = max(1e-6, max(abs(x) for x in chunk))
                chunk = [x / peak * 0.9 for x in chunk]
                rms = math.sqrt(sum(x * x for x in chunk) / len(chunk))
                if rms < 0.04:  # solo audio claro
                    continue
                cpath = clip_dir / f"{safe}__peak{k}_t{t0:.1f}_s{score:.3f}.wav"
                write_mono(cpath, chunk, sr)
                clips.append({
                    "file": str(cpath.relative_to(OUT)),
                    "source": lab,
                    "quality": quality,
                    "t0": round(t0, 2),
                    "score": round(score, 4),
                    "rms": round(rms, 4),
                    "dur": round(len(chunk) / sr, 3),
                })
                print(f"  [clip] {fighter} {cpath.name} q={quality} rms={rms:.3f} dur={len(chunk)/sr:.2f}s")
        status[fighter] = {
            "sources": sources_ok,
            "n_sources": len(sources_ok),
            "n_clips": len(clips),
            "clips": sorted(clips, key=lambda x: -x["score"])[:12],
            "ready": len(clips) >= 2,
            "gap": None if len(clips) >= 2 else "need more/better sources",
        }
        # special gaps
        if fighter == "PAPARAZZI_5" and not any("paparazzi5" in s["label"].lower() or "AEmVeK" in s.get("from", "") for s in sources_ok):
            status[fighter]["gap"] = "FALTA vídeo oficial Paparazzi 5 (AEmVeK88HIs) — no hay fuente exclusiva en disco"
            status[fighter]["ready"] = False
        if fighter == "SENOR_TIENDA" and not any("tienda" in s["label"].lower() or "gzXw" in s.get("from", "") for s in sources_ok):
            # if only derived from prankedy extract with SENOR in name
            if status[fighter]["n_clips"] == 0:
                status[fighter]["gap"] = "FALTA serie Señor de la Tienda (gzXw_4kfZBo y playlist) — YT bloqueado"
                status[fighter]["ready"] = False
    return status


def write_status(status: dict, sources: dict) -> None:
    lines = [
        "# Estado de conversión de audios (pre-implementación)",
        "",
        "> Generado por `convert_all_voice_sources.py`. **NO** instalado en el APK.",
        "",
        "## Bloqueo YouTube",
        "En este entorno **yt-dlp no puede bajar** vídeos canónicos (bot / age-gate).",
        "Lo convertido sale de: `nuevoMaterial17JUL/`, `extracted/`, `raw_yt/`, `raw_curated/`, `raw/`.",
        "",
        "## Checklist peleadors arcade",
        "",
        "| Peleador | Fuentes WAV | Clips | Ready | Gap |",
        "|----------|-------------|-------|-------|-----|",
    ]
    for f in ARCADE:
        st = status.get(f, {"n_sources": 0, "n_clips": 0, "ready": False, "gap": "sin datos"})
        ready = "YES" if st.get("ready") else "NO"
        gap = st.get("gap") or ""
        lines.append(f"| {f} | {st.get('n_sources', 0)} | {st.get('n_clips', 0)} | {ready} | {gap} |")

    lines += ["", "## Detalle por peleadór", ""]
    for f in ARCADE:
        st = status.get(f)
        if not st:
            lines.append(f"### {f}\n- **SIN FUENTES**\n")
            continue
        lines.append(f"### {f}")
        lines.append(f"- Ready: **{st['ready']}** · sources={st['n_sources']} · clips={st['n_clips']}")
        if st.get("gap"):
            lines.append(f"- Gap: {st['gap']}")
        lines.append("- Fuentes convertidas:")
        for s in st.get("sources", [])[:8]:
            lines.append(f"  - [{s['quality']}] `{s['label']}` ← `{Path(s['from']).name}` ({s['bytes']//1024} KB)")
        lines.append("- Mejores clips:")
        for c in st.get("clips", [])[:5]:
            lines.append(f"  - score={c['score']:.3f} rms={c['rms']:.3f} t={c['t0']}s `{c['file']}`")
        lines.append("")

    missing_yt = [
        "PAPARAZZI_5 → https://www.youtube.com/watch?v=AEmVeK88HIs",
        "SENOR_TIENDA → https://www.youtube.com/watch?v=gzXw_4kfZBo (+ playlist PLnYLFE_wg_9_G6W2h55Er6W2SkDVsTD40)",
        "PAPARAZZI_1 oficial (opcional si local basta) → https://www.youtube.com/watch?v=7Ug2kyQeRHQ",
        "PRANKEDY tanque → https://www.youtube.com/watch?v=zreqHXngBAw",
    ]
    lines += ["## Descargas manuales para cerrar gaps", ""]
    lines.append("Pon los archivos en `nuevoMaterial17JUL/` con nombres claros y re-ejecuta este script:")
    for m in missing_yt:
        lines.append(f"- {m}")
    lines += [
        "",
        "Ejemplo nombres:",
        "- `PAPARAZZI_5_OFFICIAL.mp3`",
        "- `SENOR_TIENDA_visitando.mp3`",
        "- `SENOR_TIENDA_adios.mp3`",
        "",
        "```bash",
        "python PolitecnicoOpenWorld/tools/convert_all_voice_sources.py",
        "```",
        "",
    ]
    (OUT / "CONVERSION_STATUS.md").write_text("\n".join(lines), encoding="utf-8")
    (OUT / "status.json").write_text(json.dumps(status, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"Wrote {OUT / 'CONVERSION_STATUS.md'}")


def main():
    print("=== Collect sources ===")
    sources = collect_sources()
    for f, items in sorted(sources.items()):
        if items:
            print(f"  {f}: {len(items)} files")
    print("=== Convert + clip ===")
    status = convert_and_clip(sources)
    write_status(status, sources)
    ready = sum(1 for f in ARCADE if status.get(f, {}).get("ready"))
    print(f"\nReady {ready}/{len(ARCADE)} arcade fighters with ≥2 clips")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
