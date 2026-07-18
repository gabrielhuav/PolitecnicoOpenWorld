#!/usr/bin/env python3
"""
export_short_clear_sfx.py — Solo frases CORTAS y CLARAS (no vídeo completo).

Reglas (SF special):
  - Duración objetivo 0.65–1.05 s (hard max 1.15 s)
  - Recorte a envolvente de habla (quita silencio / relleno al inicio y fin)
  - Filtro de claridad: RMS, ZCR, energía media (rechaza silencio, beds de música)
  - Elige el MEJOR pico por peleadór; opcionalmente top-3 candidatos
  - Escribe OGG en sf_voice_scrape/out_short/  (NO instala en assets)

Uso (repo root):
  python PolitecnicoOpenWorld/tools/export_short_clear_sfx.py
"""
from __future__ import annotations

import json
import math
import shutil
import struct
import subprocess
import wave
from pathlib import Path

TOOLS = Path(__file__).resolve().parent
SCRAPE = TOOLS / "sf_voice_scrape"
CONVERTED = SCRAPE / "converted"
OUT = SCRAPE / "out_short"
CAND = OUT / "candidates"
TMP = SCRAPE / "_tmp_short"

# SF special length — never ship long beds
MIN_DUR = 0.55
TARGET_DUR = 0.90
MAX_DUR = 1.05

# Clarity gates (reject filler / silence / pure music)
MIN_RMS = 0.045
MIN_SPEECH_SCORE = 0.012
MUSIC_ZCR_MAX = 0.035  # very low ZCR + high RMS → likely sustained music
MUSIC_RMS = 0.38

ARCADE = [
    "ESCOMBOY", "ESCOMGIRL", "ROBOT", "PRANKEDY", "SENOR_TIENDA",
    "PAPARAZZI_1", "PAPARAZZI_5", "REY_GRUPERO", "PARAMEDICO_CRUZ_ROJA",
    "POLICIA_CDMX_HOMBRE", "POLICIA_CDMX", "POLICIA_GRANADERO_HOMBRE",
    "POLICIA_GRANADERO_MUJER", "CHARRO_NEGRO", "LA_LLORONA",
    "LA_TZITZIMIME", "YOALLI_EHECATL", "LA_PRESIDENTA",
]

# Prefer canon locals / high-signal folders when ranking sources
SOURCE_BONUS = {
    "local_": 0.35,
    "CANON": 0.30,
    "raw_curated": 0.25,
    "raw_phrases_v2": 0.15,
    "Prankedy": 0.20,
    "PAPARAZZI": 0.25,
    "llorona": 0.15,
    "senor_tienda": 0.15,
    "tienda": 0.10,
}

# Soft styles for packing (same idea as curate)
STYLE = {
    "PRANKEDY": "shout",
    "PAPARAZZI_1": "shout",
    "PAPARAZZI_5": "shout",
    "SENOR_TIENDA": "shout",
    "REY_GRUPERO": "shout",
    "ESCOMBOY": "shout",
    "ESCOMGIRL": "shout",
    "LA_PRESIDENTA": "speech",
    "YOALLI_EHECATL": "horror",
    "LA_LLORONA": "horror",
    "LA_TZITZIMIME": "horror",
    "CHARRO_NEGRO": "metal",
    "ROBOT": "robot",
    "POLICIA_CDMX": "radio",
    "POLICIA_CDMX_HOMBRE": "radio",
    "POLICIA_GRANADERO_HOMBRE": "radio",
    "POLICIA_GRANADERO_MUJER": "radio",
    "PARAMEDICO_CRUZ_ROJA": "medic",
}


def ffmpeg() -> str:
    return shutil.which("ffmpeg") or shutil.which("ffmpeg.exe") or "ffmpeg"


def read_mono(path: Path, max_sec: float = 420.0) -> tuple[list[float], int]:
    with wave.open(str(path), "rb") as w:
        ch, sw, sr, n = w.getnchannels(), w.getsampwidth(), w.getframerate(), w.getnframes()
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


def rms(chunk: list[float]) -> float:
    if not chunk:
        return 0.0
    return math.sqrt(sum(x * x for x in chunk) / len(chunk))


def zcr(chunk: list[float]) -> float:
    if len(chunk) < 4:
        return 0.0
    return sum(1 for a, b in zip(chunk[::2], chunk[1::2]) if (a >= 0) != (b >= 0)) / max(1, len(chunk) // 2)


def mid_energy(chunk: list[float]) -> float:
    if len(chunk) < 8:
        return 0.0
    diffs = [abs(chunk[j + 1] - chunk[j]) for j in range(0, len(chunk) - 1, 5)]
    return math.sqrt(sum(d * d for d in diffs) / max(1, len(diffs)))


def speech_score(chunk: list[float]) -> float:
    r = rms(chunk)
    if r < 0.012:
        return 0.0
    z = zcr(chunk)
    m = mid_energy(chunk)
    score = r * (0.35 + z * 2.4) * (0.25 + m * 5.0)
    # penalize sustained music beds
    if r > MUSIC_RMS and z < MUSIC_ZCR_MAX:
        score *= 0.35
    # penalize almost-silence with noise
    if r < 0.03:
        score *= 0.5
    return score


def tight_trim(samples: list[float], sr: int, thr: float = 0.035) -> list[float]:
    """Remove leading/trailing near-silence so only the clear burst remains."""
    if not samples:
        return samples
    win = max(1, int(0.012 * sr))
    env = []
    for i in range(0, len(samples), win):
        env.append(rms(samples[i : i + win]))
    # find first / last frames above threshold relative to peak
    peak = max(env) if env else 0.0
    gate = max(thr, peak * 0.12)
    first = 0
    last = len(env) - 1
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
    chunk = samples[a:b]
    # hard cap / pad to target band
    max_n = int(MAX_DUR * sr)
    min_n = int(MIN_DUR * sr)
    if len(chunk) > max_n:
        # keep highest-energy subwindow
        win_n = max_n
        hop = max(1, win_n // 8)
        best_i, best_r = 0, -1.0
        for i in range(0, len(chunk) - win_n + 1, hop):
            rr = rms(chunk[i : i + win_n])
            if rr > best_r:
                best_r, best_i = rr, i
        chunk = chunk[best_i : best_i + win_n]
    if len(chunk) < min_n and len(samples) >= min_n:
        # small pad from original if trim was too aggressive
        return samples[:min_n] if a == 0 else samples[a : a + min_n]
    return chunk


def normalize(chunk: list[float], peak_target: float = 0.92) -> list[float]:
    peak = max(1e-6, max(abs(x) for x in chunk))
    return [x / peak * peak_target for x in chunk]


def find_peaks(samples: list[float], sr: int, top_k: int = 8) -> list[tuple[float, float, list[float]]]:
    """Return list of (t0, score, tight_chunk) for best speech bursts."""
    win = int(TARGET_DUR * sr)
    if len(samples) < int(MIN_DUR * sr):
        return []
    if len(samples) <= win:
        chunk = tight_trim(samples, sr)
        sc = speech_score(chunk)
        if sc < MIN_SPEECH_SCORE or rms(chunk) < MIN_RMS:
            return []
        return [(0.0, sc, normalize(chunk))]

    hop = max(1, win // 8)
    # skip logo/intro 0.2s when long
    i0 = int(0.15 * sr) if len(samples) > sr * 5 else 0
    scored: list[tuple[float, int]] = []
    for i in range(i0, len(samples) - win, hop):
        sc = speech_score(samples[i : i + win])
        if sc < MIN_SPEECH_SCORE * 0.6:
            continue
        scored.append((sc, i))
    scored.sort(reverse=True)

    out: list[tuple[float, float, list[float]]] = []
    used: list[int] = []
    for sc, i in scored:
        if any(abs(i - u) < win * 0.9 for u in used):
            continue
        raw = samples[i : i + win]
        chunk = tight_trim(raw, sr)
        sc2 = speech_score(chunk)
        r = rms(chunk)
        if sc2 < MIN_SPEECH_SCORE or r < MIN_RMS:
            continue
        if len(chunk) < int(MIN_DUR * sr) * 0.85:
            continue
        used.append(i)
        out.append((i / sr, sc2, normalize(chunk)))
        if len(out) >= top_k:
            break
    return out


def source_priority(path: Path) -> float:
    s = str(path).replace("\\", "/")
    bonus = 0.0
    for key, val in SOURCE_BONUS.items():
        if key in s:
            bonus = max(bonus, val)
    # deprioritize known-silent / wrong robot salvage force
    name = path.name.lower()
    if "force" in name and "robot" in s.lower():
        bonus -= 0.5
    if "salvage_robot_force" in name:
        bonus -= 0.5
    return bonus


def collect_wavs(fighter: str) -> list[Path]:
    """
    Solo WAV ya cortos / candidatos.
    NUNCA escanear sources/ completos (vídeos enteros convertidos).
    """
    paths: list[Path] = []
    clip_dir = CONVERTED / fighter / "clips"
    if clip_dir.is_dir():
        # top clips by file size (proxy for energy after normalize) — cap 24
        clips = sorted(clip_dir.glob("*.wav"), key=lambda p: -p.stat().st_size)[:24]
        paths.extend(clips)

    # cortes previos de curate/pack (~1s)
    for folder in (SCRAPE / "_tmp_curate", SCRAPE / "_tmp_verify", SCRAPE / "_tmp_pack"):
        if not folder.is_dir():
            continue
        for p in folder.glob("*.wav"):
            n = p.name.upper()
            if fighter in n or fighter.replace("_", "") in n.replace("_", ""):
                paths.append(p)
            elif f"SPECIAL_{fighter}" in n or p.stem.upper() == f"SPECIAL_{fighter}":
                paths.append(p)

    cand = SCRAPE / "candidates" / fighter
    if cand.is_dir():
        for p in list(cand.rglob("*.wav"))[:12]:
            paths.append(p)

    # ROBOT salvage only if almost no clips
    if fighter == "ROBOT":
        rob = CONVERTED / "ROBOT" / "clips"
        if rob.is_dir():
            paths.extend(rob.glob("salvage_*.wav"))

    # hard reject anything that looks like a full video dump (> ~4s ≈ 350KB mono 44k)
    # allow up to ~3s for safety
    max_bytes = 350_000
    seen = set()
    uniq = []
    for p in paths:
        try:
            rp = str(p.resolve())
            sz = p.stat().st_size
        except Exception:
            continue
        if rp in seen or sz < 4000:
            continue
        if sz > max_bytes:
            # skip full-length sources — never use as final
            continue
        seen.add(rp)
        uniq.append(p)
    return uniq


def style_filters(style: str, duration: float) -> str:
    fade_in, fade_out = 0.03, 0.08
    parts = [
        f"afade=t=in:st=0:d={fade_in}",
        f"afade=t=out:st={max(0.05, duration - fade_out)}:d={fade_out}",
    ]
    if style == "shout":
        parts += [
            "highpass=f=150", "lowpass=f=7200",
            "acompressor=threshold=-18dB:ratio=6:attack=5:release=80",
            "dynaudnorm=f=75:g=12", "volume=1.45",
        ]
    elif style == "speech":
        parts += [
            "highpass=f=100", "lowpass=f=6000",
            "acompressor=threshold=-20dB:ratio=4:attack=8:release=100",
            "dynaudnorm=f=90:g=10", "volume=1.25",
        ]
    elif style == "horror":
        parts += [
            "highpass=f=80", "lowpass=f=4500",
            "aecho=0.8:0.88:60:0.3",
            "asetrate=44100*0.92,aresample=44100",
            "dynaudnorm=f=100:g=10", "volume=1.3",
        ]
    elif style == "metal":
        parts += [
            "highpass=f=120", "lowpass=f=5000",
            "asetrate=44100*0.88,aresample=44100",
            "aecho=0.8:0.8:45:0.25",
            "dynaudnorm=f=80:g=12", "volume=1.35",
        ]
    elif style == "robot":
        parts += [
            "highpass=f=200", "lowpass=f=3500",
            "vibrato=f=9:d=0.35",
            "flanger=delay=6:depth=3:regen=0.3:speed=0.7",
            "dynaudnorm=f=70:g=12", "volume=1.4",
        ]
    elif style == "radio":
        parts += [
            "highpass=f=300", "lowpass=f=3200",
            "acompressor=threshold=-16dB:ratio=5:attack=4:release=60",
            "dynaudnorm=f=80:g=12", "volume=1.35",
        ]
    elif style == "medic":
        parts += [
            "highpass=f=180", "lowpass=f=5500",
            "dynaudnorm=f=85:g=11", "volume=1.3",
        ]
    else:
        parts += ["highpass=f=120", "dynaudnorm=f=80:g=12", "volume=1.3"]
    return ",".join(parts)


def to_ogg(src_wav: Path, out_ogg: Path, style: str, duration: float) -> bool:
    out_ogg.parent.mkdir(parents=True, exist_ok=True)
    af = style_filters(style, duration)
    cmd = [
        ffmpeg(), "-y", "-hide_banner", "-loglevel", "error",
        "-i", str(src_wav),
        "-af", af,
        "-t", f"{min(duration, MAX_DUR):.3f}",
        "-c:a", "libvorbis", "-q:a", "4",
        str(out_ogg),
    ]
    try:
        subprocess.run(cmd, check=True, timeout=60)
        return out_ogg.exists() and out_ogg.stat().st_size > 2500
    except Exception as e:
        print(f"  [ogg fail] {out_ogg.name}: {e}")
        return False


def probe_dur(path: Path) -> float:
    try:
        r = subprocess.run(
            [ffmpeg().replace("ffmpeg", "ffprobe") if False else "ffprobe",
             "-v", "error", "-show_entries", "format=duration",
             "-of", "default=noprint_wrappers=1:nokey=1", str(path)],
            capture_output=True, text=True, timeout=20,
        )
        return float(r.stdout.strip() or 0)
    except Exception:
        # fallback wave
        try:
            with wave.open(str(path), "rb") as w:
                return w.getnframes() / float(w.getframerate())
        except Exception:
            return 0.0


def process_fighter(fighter: str) -> dict:
    wavs = collect_wavs(fighter)
    best: list[dict] = []
    style = STYLE.get(fighter, "shout")
    TMP.mkdir(parents=True, exist_ok=True)
    CAND.mkdir(parents=True, exist_ok=True)

    for path in wavs:
        try:
            # Solo clips ya cortos (≤ ~3s). Nunca re-escanear vídeo completo.
            samples, sr = read_mono(path, max_sec=3.5)
        except Exception as e:
            print(f"  read fail {path.name}: {e}")
            continue
        if rms(samples) < 0.01:
            continue  # silence
        # Si ya es un clip ~1s, no hace falta hop-search completo: trim + score
        dur_src = len(samples) / sr
        if dur_src <= 1.35:
            chunk = tight_trim(samples, sr)
            if len(chunk) > int(MAX_DUR * sr):
                # keep highest-energy MAX_DUR window
                win_n = int(MAX_DUR * sr)
                hop = max(1, win_n // 6)
                bi, br = 0, -1.0
                for i in range(0, max(1, len(chunk) - win_n + 1), hop):
                    rr = rms(chunk[i : i + win_n])
                    if rr > br:
                        br, bi = rr, i
                chunk = chunk[bi : bi + win_n]
            sc = speech_score(chunk)
            peaks = [(0.0, sc, normalize(chunk))] if sc >= MIN_SPEECH_SCORE and rms(chunk) >= MIN_RMS else []
        else:
            peaks = find_peaks(samples, sr, top_k=2)
        prio = source_priority(path)
        for t0, sc, chunk in peaks:
            dur = len(chunk) / sr
            if dur > MAX_DUR + 0.05:
                chunk = chunk[: int(MAX_DUR * sr)]
                dur = MAX_DUR
            if dur < MIN_DUR * 0.8:
                continue
            total = sc + prio
            best.append({
                "score": total,
                "speech": sc,
                "t0": t0,
                "dur": dur,
                "src": str(path),
                "chunk": chunk,
                "sr": sr,
                "prio": prio,
            })

    best.sort(key=lambda x: -x["score"])
    # unique by approximate time+src to avoid near-dupes
    picked: list[dict] = []
    for b in best:
        if any(
            abs(b["t0"] - p["t0"]) < 0.4 and Path(b["src"]).name == Path(p["src"]).name
            for p in picked
        ):
            continue
        # also reject identical speech scores from pap1/pap5 shared when fighter is pap5 and src is pap1 exclusive? handled elsewhere
        picked.append(b)
        if len(picked) >= 3:
            break

    result = {
        "fighter": fighter,
        "n_sources_scanned": len(wavs),
        "n_candidates": len(picked),
        "ready": False,
        "ogg": None,
        "duration": None,
        "score": None,
        "source": None,
        "gap": None,
        "alts": [],
    }

    if not picked:
        result["gap"] = "sin picos claros (silencio / relleno / música)"
        print(f"  [NO] {fighter}: sin picos claros")
        return result

    # write top-3 candidate wavs + best ogg
    for i, p in enumerate(picked):
        tag = "best" if i == 0 else f"alt{i}"
        wav_path = CAND / f"{fighter.lower()}_{tag}.wav"
        write_mono(wav_path, p["chunk"], p["sr"])
        ogg_path = CAND / f"{fighter.lower()}_{tag}.ogg"
        ok = to_ogg(wav_path, ogg_path, style, min(p["dur"], MAX_DUR))
        entry = {
            "tag": tag,
            "wav": str(wav_path.relative_to(SCRAPE)),
            "ogg": str(ogg_path.relative_to(SCRAPE)) if ok else None,
            "dur": round(p["dur"], 3),
            "score": round(p["score"], 4),
            "speech": round(p["speech"], 4),
            "t0": round(p["t0"], 2),
            "src": Path(p["src"]).name,
        }
        if i == 0 and ok:
            final = OUT / f"special_{fighter.lower()}.ogg"
            shutil.copy2(ogg_path, final)
            result["ready"] = True
            result["ogg"] = str(final.relative_to(SCRAPE))
            result["duration"] = round(probe_dur(final) or p["dur"], 3)
            result["score"] = entry["score"]
            result["source"] = entry["src"]
            print(f"  [OK] {fighter}: {result['duration']:.2f}s score={entry['score']:.3f} ← {entry['src']}")
        else:
            result["alts"].append(entry)

    if fighter == "PAPARAZZI_5":
        # flag if best source is clearly the shared Pap1 file
        src_l = (result.get("source") or "").lower()
        if "paparazzi_1" in src_l or "e504959652ba" in src_l or "broma" in src_l:
            result["gap"] = "clip corto OK pero fuente = Pap1 (falta AEmVeK88HIs exclusivo)"
            # still ready as short audio, but marked
    if fighter == "ROBOT" and result["ready"] and result.get("score", 0) < 0.05:
        result["gap"] = "salvage corto; ideal SFX robot real"

    return result


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    CAND.mkdir(parents=True, exist_ok=True)
    print("=== Export short clear special SFX (no full videos) ===")
    print(f"Target duration: {MIN_DUR:.2f}–{MAX_DUR:.2f}s  → {OUT}")
    report = {}
    for f in ARCADE:
        print(f"-- {f}")
        report[f] = process_fighter(f)
        # drop huge chunk blobs from json
        report[f].pop("chunk", None)

    ready = sum(1 for f in ARCADE if report[f].get("ready"))
    lines = [
        "# Short clear special SFX (pre-install)",
        "",
        "> Generado por `export_short_clear_sfx.py`.",
        "> **Solo recortes cortos y claros** — no vídeos completos.",
        f"> Duración objetivo: **{MIN_DUR:.2f}–{MAX_DUR:.2f} s**.",
        "> **NO** copiado a `app/.../assets`.",
        "",
        f"## Ready: **{ready}/{len(ARCADE)}**",
        "",
        "| Peleador | Dur (s) | Score | Ready | Fuente del pico | Gap |",
        "|----------|---------|-------|-------|-----------------|-----|",
    ]
    for f in ARCADE:
        r = report[f]
        lines.append(
            f"| {f} | {r.get('duration') or '—'} | {r.get('score') or '—'} | "
            f"{'YES' if r.get('ready') else 'NO'} | `{r.get('source') or '—'}` | {r.get('gap') or ''} |"
        )
    lines += [
        "",
        "## Reglas aplicadas",
        "- Pico de **habla** (RMS + ZCR + mid-energy), no bed musical",
        "- **Tight trim** de silencio al inicio/fin",
        "- Hard max **1.05 s** por OGG final",
        "- Top-3 candidatos en `out_short/candidates/`",
        "- Finales en `out_short/special_<id>.ogg`",
        "",
        "## Gaps que siguen (fuente, no duración)",
        "- PAPARAZZI_5: falta vídeo oficial exclusivo `AEmVeK88HIs`",
        "- ROBOT: fuentes raw en silencio; solo salvage corto",
        "- YouTube bloqueado → no se pueden bajar partes de más vistas online",
        "",
        "Cuando apruebes: copiar `out_short/special_*.ogg` → assets (paso de implementación).",
    ]
    (OUT / "SHORT_CLIPS_REPORT.md").write_text("\n".join(lines), encoding="utf-8")
    # json without non-serializable
    clean = {}
    for f, r in report.items():
        clean[f] = {k: v for k, v in r.items() if k != "chunk"}
    (OUT / "short_report.json").write_text(json.dumps(clean, indent=2), encoding="utf-8")
    print(f"\nReady {ready}/{len(ARCADE)} short OGGs → {OUT}")
    print(f"Report: {OUT / 'SHORT_CLIPS_REPORT.md'}")


if __name__ == "__main__":
    main()
