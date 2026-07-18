#!/usr/bin/env python3
"""
export_complete_phrases.py — Recorta FRASES COMPLETAS desde fuentes ORIGINALES.

Problema anterior: ventanas fijas ~1s cortaban gritos/frases a la mitad.
Solución:
  1) Usar SOLO sources/ (WAV mono de vídeo/mp3 original) — no clips ya recortados
  2) Detectar islas de habla por envolvente RMS
  3) Unir huecos cortos de silencio (misma frase)
  4) Expandir al silencio real al inicio/fin (frase completa)
  5) Preferir 1.5–5.5 s; hard max 7 s (nunca el vídeo entero)
  6) Exportar best + alt1 + alt2 WAV/OGG y carpeta REVIEW_MP3

Uso (repo root):
  python PolitecnicoOpenWorld/tools/export_complete_phrases.py
"""
from __future__ import annotations

import json
import math
import shutil
import struct
import subprocess
import wave
from pathlib import Path

import numpy as np

TOOLS = Path(__file__).resolve().parent
SCRAPE = TOOLS / "sf_voice_scrape"
CONVERTED = SCRAPE / "converted"
OUT = SCRAPE / "out_phrases"
CAND = OUT / "candidates"
REVIEW = SCRAPE / "REVIEW_MP3_TODOS"
TMP = SCRAPE / "_tmp_phrases"

# Frase completa (no pico de 1s)
MIN_DUR = 1.40
PREF_MIN = 1.80
PREF_MAX = 4.50
MAX_DUR = 7.00

# Envolvente
FRAME_MS = 25
HOP_MS = 12
# Silencio corto dentro de la misma frase (ms)
GAP_MERGE_MS = 320
# Padding natural alrededor de la frase
PAD_PRE_MS = 80
PAD_POST_MS = 120

MIN_RMS = 0.028
MUSIC_ZCR_MAX = 0.032
MUSIC_RMS = 0.42

ARCADE = [
    "ESCOMBOY", "ESCOMGIRL", "ROBOT", "PRANKEDY", "SENOR_TIENDA",
    "PAPARAZZI_1", "PAPARAZZI_5", "REY_GRUPERO", "PARAMEDICO_CRUZ_ROJA",
    "POLICIA_CDMX_HOMBRE", "POLICIA_CDMX", "POLICIA_GRANADERO_HOMBRE",
    "POLICIA_GRANADERO_MUJER", "CHARRO_NEGRO", "LA_LLORONA",
    "LA_TZITZIMIME", "YOALLI_EHECATL", "LA_PRESIDENTA",
]
# extras that may have sources
EXTRA = ["LAZARO", "GRANADERO", "PARAMEDICO"]

SOURCE_BONUS_KEYS = (
    ("local_", 0.40),
    ("Prankedy", 0.30),
    ("PAPARAZZI", 0.30),
    ("raw_curated", 0.28),
    ("raw_phrases", 0.15),
    ("llorona", 0.20),
    ("senor_tienda", 0.25),
    ("tienda", 0.20),
    ("liveclip", 0.15),
    ("clip1", 0.10),
)


def ffmpeg() -> str:
    return shutil.which("ffmpeg") or shutil.which("ffmpeg.exe") or "ffmpeg"


def ffprobe() -> str:
    return shutil.which("ffprobe") or shutil.which("ffprobe.exe") or "ffprobe"


def read_mono_np(path: Path, max_sec: float = 600.0) -> tuple[np.ndarray, int]:
    with wave.open(str(path), "rb") as w:
        ch, sw, sr, n = w.getnchannels(), w.getsampwidth(), w.getframerate(), w.getnframes()
        n = min(n, int(sr * max_sec))
        raw = w.readframes(n)
    if sw != 2:
        raise ValueError(f"need 16-bit pcm: {path}")
    data = np.frombuffer(raw, dtype=np.int16).astype(np.float32)
    if ch > 1:
        data = data.reshape(-1, ch).mean(axis=1)
    data /= 32768.0
    return data, sr


def write_mono(path: Path, samples: np.ndarray | list[float], sr: int) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    arr = np.asarray(samples, dtype=np.float32)
    ints = np.clip(arr * 32767.0, -32767, 32767).astype(np.int16)
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(sr)
        w.writeframes(ints.tobytes())


def rms_np(chunk: np.ndarray) -> float:
    if chunk.size == 0:
        return 0.0
    return float(np.sqrt(np.mean(chunk * chunk)))


def speech_score_np(chunk: np.ndarray) -> float:
    r = rms_np(chunk)
    if r < 0.012:
        return 0.0
    # zero-crossing rate
    s = chunk[::2]
    if s.size < 4:
        z = 0.0
    else:
        z = float(np.mean((s[:-1] >= 0) != (s[1:] >= 0)))
    # mid energy via diffs
    d = np.diff(chunk[::6])
    m = float(np.sqrt(np.mean(d * d))) if d.size else 0.0
    score = r * (0.35 + z * 2.3) * (0.25 + m * 4.5)
    if r > MUSIC_RMS and z < MUSIC_ZCR_MAX:
        score *= 0.30
    return score


def envelope_np(samples: np.ndarray, sr: int) -> tuple[np.ndarray, int, int]:
    frame = max(1, int(sr * FRAME_MS / 1000.0))
    hop = max(1, int(sr * HOP_MS / 1000.0))
    if samples.size < frame:
        return np.array([rms_np(samples)], dtype=np.float32), frame, hop
    # vectorized-ish: stride tricks for RMS
    n_frames = 1 + (samples.size - frame) // hop
    # build index matrix would be huge; use simple loop on frame RMS (still fast in numpy chunks)
    env = np.empty(n_frames, dtype=np.float32)
    for i in range(n_frames):
        a = i * hop
        chunk = samples[a : a + frame]
        env[i] = np.sqrt(np.mean(chunk * chunk))
    return env, frame, hop


def adaptive_gate(env: np.ndarray) -> float:
    if env.size == 0:
        return 0.05
    p20 = float(np.percentile(env, 20))
    p50 = float(np.percentile(env, 50))
    p90 = float(np.percentile(env, 90))
    gate = max(0.022, p20 * 2.8, p50 * 1.15)
    gate = min(gate, max(0.035, p90 * 0.25))
    return gate


def speech_regions(env: np.ndarray, hop: int, sr: int, frame: int) -> list[tuple[int, int]]:
    gate = adaptive_gate(env)
    speech = env >= gate
    gap_frames = max(1, int((GAP_MERGE_MS / 1000.0) * sr / hop))
    merged = speech.copy()
    i = 0
    n = merged.size
    while i < n:
        if not merged[i]:
            j = i
            while j < n and not merged[j]:
                j += 1
            if i > 0 and j < n and (j - i) <= gap_frames:
                merged[i:j] = True
            i = j
        else:
            i += 1

    regions: list[tuple[int, int]] = []
    i = 0
    while i < n:
        if not merged[i]:
            i += 1
            continue
        j = i
        while j < n and merged[j]:
            j += 1
        a = i * hop
        b = j * hop + frame
        regions.append((a, b))
        i = j
    return regions


def clamp_phrase(samples: np.ndarray, sr: int, a: int, b: int) -> tuple[int, int] | None:
    n = samples.size
    pre = int(PAD_PRE_MS / 1000.0 * sr)
    post = int(PAD_POST_MS / 1000.0 * sr)
    a = max(0, a - pre)
    b = min(n, b + post)
    dur = (b - a) / sr
    if dur < MIN_DUR * 0.85:
        return None
    if dur <= MAX_DUR:
        return a, b
    win = int(min(PREF_MAX, MAX_DUR) * sr)
    hop = max(1, win // 8)
    best_i, best_sc = a, -1.0
    end_lim = min(b, n) - win
    if end_lim <= a:
        return a, min(n, a + win)
    for i in range(a, end_lim + 1, hop):
        sc = speech_score_np(samples[i : i + win])
        if sc > best_sc:
            best_sc, best_i = sc, i
    return best_i, best_i + win


def normalize(chunk: np.ndarray, peak_target: float = 0.92) -> np.ndarray:
    peak = float(np.max(np.abs(chunk))) if chunk.size else 1e-6
    peak = max(peak, 1e-6)
    return chunk / peak * peak_target


def find_complete_phrases(samples: np.ndarray, sr: int, top_k: int = 6) -> list[dict]:
    if samples.size < int(MIN_DUR * sr):
        if rms_np(samples) < MIN_RMS:
            return []
        return [{
            "t0": 0.0,
            "dur": samples.size / sr,
            "score": speech_score_np(samples),
            "chunk": normalize(samples),
        }]

    env, frame, hop = envelope_np(samples, sr)
    regions = speech_regions(env, hop, sr, frame)

    candidates = []
    for a, b in regions:
        b = min(samples.size, b)
        clamped = clamp_phrase(samples, sr, a, b)
        if not clamped:
            continue
        a2, b2 = clamped
        chunk = samples[a2:b2]
        dur = chunk.size / sr
        if dur < MIN_DUR * 0.9 or dur > MAX_DUR + 0.05:
            continue
        r = rms_np(chunk)
        if r < MIN_RMS:
            continue
        sc = speech_score_np(chunk)
        if sc < 0.008:
            continue
        if PREF_MIN <= dur <= PREF_MAX:
            sc *= 1.25
        elif dur < PREF_MIN:
            sc *= 0.85
        elif dur > PREF_MAX:
            sc *= 0.90
        candidates.append({
            "t0": a2 / sr,
            "dur": dur,
            "score": sc,
            "rms": r,
            "chunk": normalize(chunk),
            "a": a2,
            "b": b2,
        })

    if len(candidates) < 2:
        win = int(3.2 * sr)
        hop_s = max(1, win // 3)
        for i in range(0, max(1, samples.size - win), hop_s):
            chunk = samples[i : i + win]
            sc = speech_score_np(chunk)
            if sc < 0.01 or rms_np(chunk) < MIN_RMS:
                continue
            candidates.append({
                "t0": i / sr,
                "dur": win / sr,
                "score": sc * 0.9,
                "rms": rms_np(chunk),
                "chunk": normalize(chunk),
                "a": i,
                "b": i + win,
            })

    candidates.sort(key=lambda x: -x["score"])
    picked = []
    for c in candidates:
        if any(abs(c["t0"] - p["t0"]) < 1.2 for p in picked):
            continue
        picked.append(c)
        if len(picked) >= top_k:
            break
    return picked


def source_bonus(path: Path) -> float:
    s = str(path).replace("\\", "/")
    bonus = 0.0
    for key, val in SOURCE_BONUS_KEYS:
        if key in s:
            bonus = max(bonus, val)
    name = path.name.lower()
    if "force" in name and "robot" in s.lower():
        bonus -= 0.4
    # prefer larger (more content) slightly but not huge beds only
    try:
        mb = path.stat().st_size / (1024 * 1024)
        if 1.0 <= mb <= 25:
            bonus += 0.05
    except Exception:
        pass
    return bonus


def collect_sources(fighter: str) -> list[Path]:
    """Fuentes ORIGINALES (sources/), no clips de 1s."""
    paths: list[Path] = []
    src_dir = CONVERTED / fighter / "sources"
    if src_dir.is_dir():
        for p in src_dir.glob("*.wav"):
            if p.stat().st_size < 20_000:
                continue
            # skip pure silence tiny salvage dumps if tiny
            paths.append(p)

    # raw originals not yet in converted (fallback)
    for folder in (
        SCRAPE / "extracted",
        SCRAPE / "raw_curated",
        SCRAPE / "raw_phrases_v2",
        SCRAPE / "raw_yt",
        SCRAPE / "raw",
    ):
        if not folder.is_dir():
            continue
        for p in folder.iterdir():
            if not p.is_file():
                continue
            if p.suffix.lower() not in {".wav", ".mp3", ".m4a", ".mp4", ".webm"}:
                continue
            up = p.name.upper()
            if not up.startswith(fighter) and fighter not in up:
                # curated names
                low = p.name.lower()
                if fighter == "SENOR_TIENDA" and ("tienda" in low or "senor" in low):
                    pass
                elif fighter == "LA_LLORONA" and "llorona" in low:
                    pass
                elif fighter == "PRANKEDY" and ("prankedy" in low or "taco" in low or "tanque" in low):
                    pass
                elif fighter == "PAPARAZZI_1" and ("paparazzi" in low and "5" not in low):
                    pass
                elif fighter == "PAPARAZZI_5" and "paparazzi" in low:
                    pass
                else:
                    continue
            if p.stat().st_size < 20_000:
                continue
            paths.append(p)

    # local materials
    local = TOOLS.parent.parent / "nuevoMaterial17JUL"
    if local.is_dir():
        if fighter == "PRANKEDY":
            paths.extend(local.glob("Prankedy*.mp3"))
        if fighter == "PAPARAZZI_1":
            paths.extend(local.glob("*PAPARAZZI*"))
            paths.extend(local.glob("*paparazzi*"))

    # ROBOT salvage full-ish if sources silent
    if fighter == "ROBOT":
        for p in (SCRAPE / "converted" / "ROBOT" / "clips").glob("salvage_*.wav"):
            paths.append(p)
        for p in (SCRAPE / "_tmp_verify", SCRAPE / "out_curated"):
            pass
        v = SCRAPE / "_tmp_verify" / "special_robot.wav"
        if v.is_file():
            paths.append(v)

    # dedupe + prefer wav already mono converted
    seen = set()
    uniq = []
    for p in paths:
        try:
            rp = str(p.resolve())
        except Exception:
            continue
        if rp in seen:
            continue
        seen.add(rp)
        uniq.append(p)

    # sort: converted sources first, larger files with bonus names first
    def rank(p: Path):
        bonus = source_bonus(p)
        in_src = 1 if "sources" in p.parts else 0
        return (-in_src, -bonus, -p.stat().st_size)

    uniq.sort(key=rank)
    # cap sources scanned per fighter for speed
    return uniq[:10]


def ensure_wav(src: Path, cache_dir: Path) -> Path | None:
    if src.suffix.lower() == ".wav" and src.stat().st_size > 20_000:
        # might already be mono 44.1; use as-is
        return src
    cache_dir.mkdir(parents=True, exist_ok=True)
    dst = cache_dir / ("".join(c if c.isalnum() or c in "-_" else "_" for c in src.stem)[:50] + ".wav")
    if dst.exists() and dst.stat().st_size > 20_000:
        return dst
    try:
        subprocess.run(
            [ffmpeg(), "-y", "-hide_banner", "-loglevel", "error",
             "-i", str(src), "-vn", "-ac", "1", "-ar", "44100", "-c:a", "pcm_s16le", str(dst)],
            check=True, timeout=300,
        )
        return dst if dst.exists() and dst.stat().st_size > 20_000 else None
    except Exception as e:
        print(f"  convert fail {src.name}: {e}")
        return None


def to_ogg(src_wav: Path, out_ogg: Path, duration: float) -> bool:
    out_ogg.parent.mkdir(parents=True, exist_ok=True)
    fade_out = min(0.12, duration * 0.12)
    fade_in = 0.04
    af = (
        f"afade=t=in:st=0:d={fade_in},"
        f"afade=t=out:st={max(0.1, duration - fade_out)}:d={fade_out},"
        f"highpass=f=100,lowpass=f=7000,"
        f"dynaudnorm=f=90:g=12,volume=1.2"
    )
    try:
        subprocess.run(
            [ffmpeg(), "-y", "-hide_banner", "-loglevel", "error",
             "-i", str(src_wav), "-af", af, "-t", f"{duration:.3f}",
             "-c:a", "libvorbis", "-q:a", "5", str(out_ogg)],
            check=True, timeout=60,
        )
        return out_ogg.exists() and out_ogg.stat().st_size > 3000
    except Exception as e:
        print(f"  ogg fail {out_ogg.name}: {e}")
        return False


def to_mp3(src: Path, dst: Path) -> bool:
    dst.parent.mkdir(parents=True, exist_ok=True)
    try:
        subprocess.run(
            [ffmpeg(), "-y", "-hide_banner", "-loglevel", "error",
             "-i", str(src), "-ac", "1", "-ar", "44100", "-b:a", "192k", str(dst)],
            check=True, timeout=60,
        )
        return dst.exists() and dst.stat().st_size > 2000
    except Exception:
        return False


def probe_dur(path: Path) -> float:
    try:
        r = subprocess.run(
            [ffprobe(), "-v", "error", "-show_entries", "format=duration",
             "-of", "default=noprint_wrappers=1:nokey=1", str(path)],
            capture_output=True, text=True, timeout=20,
        )
        return float(r.stdout.strip() or 0)
    except Exception:
        return 0.0


def process_fighter(fighter: str) -> dict:
    print(f"-- {fighter}", flush=True)
    sources = collect_sources(fighter)
    print(f"   sources: {len(sources)}", flush=True)
    cache = TMP / fighter
    all_phrases: list[dict] = []

    for path in sources:
        wav = ensure_wav(path, cache)
        if not wav:
            continue
        try:
            # scan up to 8 min of original full source
            samples, sr = read_mono_np(wav, max_sec=480.0)
        except Exception as e:
            print(f"   read fail {path.name}: {e}")
            continue
        if rms_np(samples) < 0.008:
            print(f"   skip silent {path.name}")
            continue
        phrases = find_complete_phrases(samples, sr, top_k=5)
        bonus = source_bonus(path)
        for ph in phrases:
            all_phrases.append({
                **ph,
                "score": ph["score"] + bonus,
                "src": str(path),
                "src_name": path.name,
                "sr": sr,
                "bonus": bonus,
            })
        print(f"   {path.name}: {len(phrases)} phrases  peak_score={max((p['score'] for p in phrases), default=0):.3f}", flush=True)

    all_phrases.sort(key=lambda x: -x["score"])
    # global de-dupe by time+source
    picked = []
    for p in all_phrases:
        if any(
            Path(p["src"]).name == Path(q["src"]).name and abs(p["t0"] - q["t0"]) < 1.0
            for q in picked
        ):
            continue
        # also skip near-identical duration/score from same content hash path size
        picked.append(p)
        if len(picked) >= 3:
            break

    result = {
        "fighter": fighter,
        "n_sources": len(sources),
        "ready": False,
        "variants": [],
        "gap": None,
    }

    if not picked:
        result["gap"] = "sin frases completas detectadas"
        print(f"  [NO] {fighter}")
        return result

    CAND.mkdir(parents=True, exist_ok=True)
    OUT.mkdir(parents=True, exist_ok=True)
    tags = ["best", "alt1", "alt2"]
    for i, p in enumerate(picked):
        tag = tags[i]
        base = f"{fighter.lower()}_{tag}"
        wav_path = CAND / f"{base}.wav"
        write_mono(wav_path, p["chunk"], p["sr"])
        dur = p["dur"]
        ogg_path = CAND / f"{base}.ogg"
        ok = to_ogg(wav_path, ogg_path, dur)
        if i == 0 and ok:
            final = OUT / f"special_{fighter.lower()}.ogg"
            shutil.copy2(ogg_path, final)
            result["ready"] = True
        entry = {
            "tag": tag,
            "dur": round(dur, 3),
            "t0": round(p["t0"], 2),
            "score": round(p["score"], 4),
            "src": p["src_name"],
            "wav": str(wav_path.relative_to(SCRAPE)),
            "ogg": str(ogg_path.relative_to(SCRAPE)) if ok else None,
        }
        result["variants"].append(entry)
        print(
            f"  [{tag}] {fighter}: {dur:.2f}s @ t={p['t0']:.1f}s score={p['score']:.3f} ← {p['src_name']}",
            flush=True,
        )

    if fighter == "PAPARAZZI_5":
        srcs = " ".join(v["src"].lower() for v in result["variants"])
        if "paparazzi_1" in srcs or "e504959652ba" in srcs or "broma" in srcs:
            result["gap"] = "frases completas OK pero fuente puede ser Pap1 (falta AEmVeK88HIs)"
    if fighter == "ROBOT" and result["ready"] and all(v["dur"] < 1.6 for v in result["variants"]):
        result["gap"] = "solo salvage corto; fuentes raw en silencio"

    return result


def build_review_mp3(report: dict) -> Path:
    """Carpeta REVIEW_MP3_TODOS con frases completas + assets existentes."""
    if REVIEW.exists():
        shutil.rmtree(REVIEW)
    for sub in ("00_TODOS_PLANOS", "01_phrases_best", "02_phrases_alts", "03_curated_en_assets", "04_pack_old"):
        (REVIEW / sub).mkdir(parents=True, exist_ok=True)

    n = 0
    # 01 best from out_phrases
    for p in OUT.glob("special_*.ogg"):
        fid = p.stem.replace("special_", "").upper()
        dst = REVIEW / "01_phrases_best" / f"{fid}_special_best.mp3"
        if to_mp3(p, dst):
            n += 1

    # 02 all candidates
    for p in CAND.glob("*.ogg"):
        # name: prankedy_best.ogg
        stem = p.stem
        if "_" in stem:
            char, tag = stem.rsplit("_", 1)
            dst = REVIEW / "02_phrases_alts" / f"{char.upper()}_special_{tag}.mp3"
        else:
            dst = REVIEW / "02_phrases_alts" / f"{stem.upper()}.mp3"
        if to_mp3(p, dst):
            n += 1

    # 03 assets (ya implementados)
    assets = TOOLS.parent / "app" / "src" / "main" / "assets" / "STREETFIGHTER" / "SOUNDS"
    if assets.is_dir():
        for p in assets.glob("special_*.ogg"):
            fid = p.stem.replace("special_", "").upper()
            dst = REVIEW / "03_curated_en_assets" / f"{fid}_special_EN_ASSETS.mp3"
            if to_mp3(p, dst):
                n += 1

    # 04 old pack
    old = SCRAPE / "out"
    if old.is_dir():
        for p in old.glob("special_*.ogg"):
            fid = p.stem.replace("special_", "").upper()
            dst = REVIEW / "04_pack_old" / f"{fid}_special_pack_old.mp3"
            if to_mp3(p, dst):
                n += 1

    # flat
    flat = REVIEW / "00_TODOS_PLANOS"
    for folder in ("01_phrases_best", "02_phrases_alts", "03_curated_en_assets", "04_pack_old"):
        for p in (REVIEW / folder).glob("*.mp3"):
            shutil.copy2(p, flat / f"{folder}__{p.name}")

    readme = f"""# REVIEW MP3 — frases COMPLETAS (recorte desde fuentes originales)

## Importante
Los cortes anteriores (~1s) partían las frases. Esta pasada:
- usa **fuentes originales** (`converted/*/sources/`, raw, locales)
- detecta **islas de habla** y une huecos cortos
- exporta **frases completas** (aprox. {MIN_DUR:.1f}–{MAX_DUR:.1f} s, preferido {PREF_MIN:.1f}–{PREF_MAX:.1f} s)
- **NO** empaqueta el vídeo entero

## Carpetas
- `00_TODOS_PLANOS` — todo junto para escuchar
- `01_phrases_best` — mejor frase completa por peleadór (NUEVA)
- `02_phrases_alts` — best + alt1 + alt2
- `03_curated_en_assets` — lo ya en el APK
- `04_pack_old` — pack viejo

## Nombres
`PERSONAJE_special_VARIANTE.mp3`  (acción = special)

Listos frases nuevas: {sum(1 for f,r in report.items() if r.get('ready'))}
"""
    (REVIEW / "LEEME.txt").write_text(readme, encoding="utf-8")

    # duration index
    lines = ["# Índice de duraciones (frases nuevas)", ""]
    for p in sorted((REVIEW / "01_phrases_best").glob("*.mp3")):
        d = probe_dur(p)
        lines.append(f"- {p.name}: **{d:.2f}s**")
    (REVIEW / "DURACIONES.md").write_text("\n".join(lines), encoding="utf-8")
    print(f"Review MP3 written: {REVIEW}  (files exported ~{n}+flat)", flush=True)
    return REVIEW


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    CAND.mkdir(parents=True, exist_ok=True)
    TMP.mkdir(parents=True, exist_ok=True)
    print("=== Complete phrases from ORIGINAL sources ===", flush=True)
    print(f"Duration band: {MIN_DUR:.1f}–{MAX_DUR:.1f}s (prefer {PREF_MIN:.1f}–{PREF_MAX:.1f}s)", flush=True)

    report = {}
    for f in ARCADE + EXTRA:
        # only process if has any source material
        if not (CONVERTED / f / "sources").exists() and f not in ARCADE:
            # still try ARCADE always
            pass
        report[f] = process_fighter(f)

    ready = sum(1 for f in ARCADE if report.get(f, {}).get("ready"))
    lines = [
        "# Frases completas (desde fuentes originales)",
        "",
        f"> Duración: **{MIN_DUR:.1f}–{MAX_DUR:.1f} s** (preferido {PREF_MIN:.1f}–{PREF_MAX:.1f}).",
        "> No se empaqueta el vídeo completo; solo la frase entera.",
        "> **NO** instalado en assets.",
        "",
        f"## Ready {ready}/{len(ARCADE)}",
        "",
        "| Peleador | Dur best | t0 | Score | Fuente | Gap |",
        "|----------|----------|----|-------|--------|-----|",
    ]
    for f in ARCADE:
        r = report.get(f, {})
        v = (r.get("variants") or [{}])[0]
        lines.append(
            f"| {f} | {v.get('dur', '—')} | {v.get('t0', '—')} | {v.get('score', '—')} | "
            f"`{v.get('src', '—')}` | {r.get('gap') or ''} |"
        )
    (OUT / "PHRASES_REPORT.md").write_text("\n".join(lines), encoding="utf-8")
    clean = {}
    for f, r in report.items():
        clean[f] = {k: v for k, v in r.items()}
    (OUT / "phrases_report.json").write_text(json.dumps(clean, indent=2), encoding="utf-8")

    review = build_review_mp3(report)
    print(f"\nReady {ready}/{len(ARCADE)} complete phrases → {OUT}", flush=True)
    print(f"REVIEW MP3 → {review.resolve()}", flush=True)


if __name__ == "__main__":
    main()
