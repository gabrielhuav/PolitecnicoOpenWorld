#!/usr/bin/env python3
"""
diarize_special_voices.py
-------------------------
Separar VOCES en las fuentes originales y recortar la del personaje correcto.

Algoritmo:
  1) VAD por energía (islas de habla)
  2) Features acústicas por segmento (pitch, ZCR, bandas, RMS)
  3) Clustering (KMeans / agglomerative) → N hablantes
  4) Ranking por TIEMPO TOTAL de habla (la más repetida = principal)
  5) Asignación de rol:
       PRANKEDY          → rank 0 (principal)
       PAPARAZZI_1/5     → rank 1 si hay ≥2 voces (segundo principal = paparazzi)
       SENOR_TIENDA      → rank 1 si hay ≥2 (el señor, no Prankedy)
       REY_GRUPERO       → rank 0
       LA_LLORONA        → voz con pitch medio más alto (femenina) entre top-2
       LA_PRESIDENTA     → voz con pitch medio más alto (Claudia, mujer)
  6) De la voz elegida: mejor frase completa (1.5–6 s)
  7) Export WAV/OGG + report + REVIEW MP3

Uso (repo root):
  python PolitecnicoOpenWorld/tools/diarize_special_voices.py
"""
from __future__ import annotations

import json
import math
import shutil
import struct
import subprocess
import wave
from collections import defaultdict
from pathlib import Path

import numpy as np
from sklearn.cluster import AgglomerativeClustering, KMeans
from sklearn.preprocessing import StandardScaler

TOOLS = Path(__file__).resolve().parent
SCRAPE = TOOLS / "sf_voice_scrape"
CONVERTED = SCRAPE / "converted"
OUT = SCRAPE / "out_diarized"
REVIEW = SCRAPE / "REVIEW_MP3_TODOS"
LOCAL = TOOLS.parent.parent / "nuevoMaterial17JUL"
CATALOG = SCRAPE / "special_phrases_catalog.json"

# Solo peleadors que requieren scrape real de voz
CANON = [
    "PRANKEDY",
    "PAPARAZZI_1",
    "PAPARAZZI_5",
    "SENOR_TIENDA",
    "REY_GRUPERO",
    "LA_LLORONA",
    "LA_PRESIDENTA",
    "CHARRO_NEGRO",  # nahual — YT DK5ZgKoM8Xw
]

# Rol de voz: 0 = más hablante, 1 = segundo, "pitch_high" = femenina preferida
ROLE = {
    "PRANKEDY": 0,
    "PAPARAZZI_1": 1,       # en vídeo Pap: Prankedy rank0, Paparazzi rank1
    "PAPARAZZI_5": 1,
    "SENOR_TIENDA": 1,      # Prankedy rank0, señor rank1
    "REY_GRUPERO": 0,
    "LA_LLORONA": "pitch_high",
    "LA_PRESIDENTA": "pitch_high",
    "CHARRO_NEGRO": 0,      # SFX nahual / voz principal del clip
}

MIN_SEG = 0.45
MAX_SEG_SCAN = 420.0  # sec per file
PHRASE_MIN = 1.5
PHRASE_MAX = 5.5
FRAME_MS = 25
HOP_MS = 10


def ffmpeg() -> str:
    return shutil.which("ffmpeg") or shutil.which("ffmpeg.exe") or "ffmpeg"


def ffprobe() -> str:
    return shutil.which("ffprobe") or shutil.which("ffprobe.exe") or "ffprobe"


def read_wav(path: Path, max_sec: float = MAX_SEG_SCAN) -> tuple[np.ndarray, int]:
    with wave.open(str(path), "rb") as w:
        ch, sw, sr, n = w.getnchannels(), w.getsampwidth(), w.getframerate(), w.getnframes()
        n = min(n, int(sr * max_sec))
        raw = w.readframes(n)
    if sw != 2:
        raise ValueError(f"16-bit only: {path}")
    data = np.frombuffer(raw, dtype=np.int16).astype(np.float32)
    if ch > 1:
        data = data.reshape(-1, ch).mean(axis=1)
    data /= 32768.0
    return data, sr


def ensure_wav(src: Path, cache: Path) -> Path | None:
    if src.suffix.lower() == ".wav" and src.stat().st_size > 30_000:
        return src
    cache.mkdir(parents=True, exist_ok=True)
    safe = "".join(c if c.isalnum() or c in "-_" else "_" for c in src.stem)[:55]
    dst = cache / f"{safe}.wav"
    if dst.exists() and dst.stat().st_size > 30_000:
        return dst
    try:
        subprocess.run(
            [ffmpeg(), "-y", "-hide_banner", "-loglevel", "error",
             "-i", str(src), "-vn", "-ac", "1", "-ar", "16000", "-c:a", "pcm_s16le", str(dst)],
            check=True, timeout=400,
        )
        return dst if dst.exists() and dst.stat().st_size > 30_000 else None
    except Exception as e:
        print(f"  convert fail {src.name}: {e}")
        return None


def write_wav(path: Path, samples: np.ndarray, sr: int) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    ints = np.clip(samples * 32767.0, -32767, 32767).astype(np.int16)
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(sr)
        w.writeframes(ints.tobytes())


def to_ogg(src: Path, dst: Path, dur: float) -> bool:
    dst.parent.mkdir(parents=True, exist_ok=True)
    fade = min(0.12, max(0.05, dur * 0.1))
    af = (
        f"afade=t=in:d=0.04,afade=t=out:st={max(0.1, dur - fade)}:d={fade},"
        f"highpass=f=90,lowpass=f=7000,dynaudnorm=f=90:g=12,volume=1.25"
    )
    try:
        subprocess.run(
            [ffmpeg(), "-y", "-hide_banner", "-loglevel", "error",
             "-i", str(src), "-af", af, "-t", f"{dur:.3f}",
             "-c:a", "libvorbis", "-q:a", "5", str(dst)],
            check=True, timeout=60,
        )
        return dst.exists() and dst.stat().st_size > 2500
    except Exception:
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


# ---------- DSP / features ----------

def frame_rms(x: np.ndarray, sr: int) -> tuple[np.ndarray, int, int]:
    frame = max(1, int(sr * FRAME_MS / 1000))
    hop = max(1, int(sr * HOP_MS / 1000))
    n = 1 + max(0, (len(x) - frame) // hop)
    env = np.empty(n, dtype=np.float32)
    for i in range(n):
        a = i * hop
        c = x[a : a + frame]
        env[i] = float(np.sqrt(np.mean(c * c))) if c.size else 0.0
    return env, frame, hop


def speech_segments(x: np.ndarray, sr: int) -> list[tuple[int, int]]:
    env, frame, hop = frame_rms(x, sr)
    if env.size == 0:
        return []
    p20 = float(np.percentile(env, 20))
    p90 = float(np.percentile(env, 90))
    gate = max(0.018, p20 * 2.5, min(0.08, p90 * 0.22))
    speech = env >= gate
    # merge gaps ≤ 280ms
    gap = max(1, int(0.28 * sr / hop))
    merged = speech.copy()
    i = 0
    n = merged.size
    while i < n:
        if not merged[i]:
            j = i
            while j < n and not merged[j]:
                j += 1
            if i > 0 and j < n and (j - i) <= gap:
                merged[i:j] = True
            i = j
        else:
            i += 1
    segs = []
    i = 0
    min_samp = int(MIN_SEG * sr)
    while i < n:
        if not merged[i]:
            i += 1
            continue
        j = i
        while j < n and merged[j]:
            j += 1
        a = i * hop
        b = min(len(x), j * hop + frame)
        if b - a >= min_samp:
            segs.append((a, b))
        i = j
    return segs


def estimate_pitch(chunk: np.ndarray, sr: int) -> float:
    """Autocorrelation pitch estimate (Hz); 0 if unvoiced."""
    if chunk.size < sr // 20:
        return 0.0
    # downsample for speed
    step = max(1, sr // 8000)
    y = chunk[::step]
    sr2 = sr // step
    y = y - y.mean()
    if float(np.std(y)) < 1e-4:
        return 0.0
    # FFT autocorr
    n = 1
    while n < 2 * len(y):
        n *= 2
    f = np.fft.rfft(y, n)
    ac = np.fft.irfft(f * np.conj(f))[: len(y)]
    ac = ac / (ac[0] + 1e-9)
    # pitch range 80–400 Hz
    min_lag = max(1, int(sr2 / 400))
    max_lag = min(len(ac) - 1, int(sr2 / 80))
    if max_lag <= min_lag:
        return 0.0
    region = ac[min_lag:max_lag]
    lag = int(np.argmax(region)) + min_lag
    if ac[lag] < 0.25:
        return 0.0
    return float(sr2 / lag)


def band_energies(chunk: np.ndarray, sr: int, n_bands: int = 8) -> np.ndarray:
    # rFFT magnitude → equal log-ish bands
    n = 1
    while n < len(chunk):
        n *= 2
    spec = np.abs(np.fft.rfft(chunk, n))
    freqs = np.fft.rfftfreq(n, 1.0 / sr)
    # bands 100–4000 Hz
    edges = np.geomspace(100, min(4000, sr / 2 - 1), n_bands + 1)
    out = np.zeros(n_bands, dtype=np.float32)
    for i in range(n_bands):
        m = (freqs >= edges[i]) & (freqs < edges[i + 1])
        if np.any(m):
            out[i] = float(np.log1p(np.mean(spec[m] ** 2)))
    return out


def segment_features(chunk: np.ndarray, sr: int) -> np.ndarray:
    r = float(np.sqrt(np.mean(chunk * chunk)))
    z = float(np.mean((chunk[:-1] >= 0) != (chunk[1:] >= 0))) if chunk.size > 2 else 0.0
    # spectral centroid
    n = 1
    while n < len(chunk):
        n *= 2
    spec = np.abs(np.fft.rfft(chunk, n)) + 1e-9
    freqs = np.fft.rfftfreq(n, 1.0 / sr)
    cent = float(np.sum(freqs * spec) / np.sum(spec))
    pitch = estimate_pitch(chunk, sr)
    bands = band_energies(chunk, sr)
    # duration cue (log)
    dur = math.log1p(len(chunk) / sr)
    return np.concatenate([
        np.array([r, z, cent / 4000.0, pitch / 400.0, dur], dtype=np.float32),
        bands,
    ])


def cluster_speakers(feats: np.ndarray, max_k: int = 3) -> np.ndarray:
    """Return label per segment. k chosen 2..max_k by inertia elbow-ish."""
    n = feats.shape[0]
    if n < 3:
        return np.zeros(n, dtype=int)
    Xs = StandardScaler().fit_transform(feats)
    best_labels = np.zeros(n, dtype=int)
    best_score = -1e18
    for k in range(2, min(max_k, n) + 1):
        try:
            # Agglomerative is stable for small n
            if n <= 80:
                model = AgglomerativeClustering(n_clusters=k, linkage="ward")
                labels = model.fit_predict(Xs)
            else:
                model = KMeans(n_clusters=k, n_init=8, random_state=42)
                labels = model.fit_predict(Xs)
            # score: prefer separated clusters with decent size
            sizes = np.bincount(labels, minlength=k).astype(float)
            if sizes.min() < max(2, n * 0.05):
                continue
            # silhouette-like: mean distance to other centers
            centers = np.array([Xs[labels == i].mean(axis=0) for i in range(k)])
            # inter-center min distance
            dmin = 1e9
            for i in range(k):
                for j in range(i + 1, k):
                    dmin = min(dmin, float(np.linalg.norm(centers[i] - centers[j])))
            score = dmin * math.log1p(k) - 0.1 * k
            if score > best_score:
                best_score = score
                best_labels = labels
        except Exception:
            continue
    return best_labels


def rank_speakers(labels: np.ndarray, segs: list[tuple[int, int]], sr: int, feats: np.ndarray) -> list[dict]:
    """List of speakers sorted by total speech time desc."""
    by = defaultdict(list)
    for i, lab in enumerate(labels):
        by[int(lab)].append(i)
    speakers = []
    for lab, idxs in by.items():
        total = sum((segs[i][1] - segs[i][0]) for i in idxs) / sr
        pitches = [float(feats[i][3] * 400.0) for i in idxs]  # denormalize pitch feature
        pitches = [p for p in pitches if p > 60]
        speakers.append({
            "label": lab,
            "n_segs": len(idxs),
            "total_sec": total,
            "mean_pitch": float(np.mean(pitches)) if pitches else 0.0,
            "idxs": idxs,
        })
    speakers.sort(key=lambda s: (-s["total_sec"], -s["n_segs"]))
    return speakers


def pick_role(speakers: list[dict], role) -> dict | None:
    if not speakers:
        return None
    if role == "pitch_high":
        # Prefer female-ish pitch among speakers with enough speech
        voiced = [s for s in speakers if s["mean_pitch"] >= 140]
        pool = voiced if voiced else speakers
        return max(pool, key=lambda s: (s["mean_pitch"], s["total_sec"]))
    if isinstance(role, int):
        if role == 0:
            return speakers[0]
        # rank 1+ = "segundo principal" (Paparazzi / Señor de la tienda)
        # No elegir clusters mudos/ruido (pitch~0): buscar el siguiente con voz humana
        if len(speakers) == 1:
            return speakers[0]
        for s in speakers[1:]:
            if s["mean_pitch"] >= 85 and s["total_sec"] >= 1.0:
                return s
        # fallback: el de menor tiempo entre top-2 con algo de energía
        return speakers[min(role, len(speakers) - 1)]
    return speakers[0]


def best_phrase_from_speaker(
    x: np.ndarray, sr: int, segs: list[tuple[int, int]], idxs: list[int]
) -> tuple[np.ndarray, float, float] | None:
    """Merge nearby segments of same speaker and pick best complete phrase window."""
    # build continuous islands for this speaker
    times = sorted((segs[i][0], segs[i][1]) for i in idxs)
    if not times:
        return None
    islands = []
    ca, cb = times[0]
    gap_max = int(0.35 * sr)
    for a, b in times[1:]:
        if a - cb <= gap_max:
            cb = max(cb, b)
        else:
            islands.append((ca, cb))
            ca, cb = a, b
    islands.append((ca, cb))

    candidates = []
    for a, b in islands:
        dur = (b - a) / sr
        if dur < PHRASE_MIN * 0.85:
            continue
        if dur > PHRASE_MAX:
            # take loudest PHRASE_MAX window inside
            win = int(PHRASE_MAX * sr)
            hop = max(1, win // 6)
            best_i, best_r = a, -1.0
            for i in range(a, max(a + 1, b - win), hop):
                c = x[i : i + win]
                r = float(np.sqrt(np.mean(c * c)))
                if r > best_r:
                    best_r, best_i = r, i
            a, b = best_i, best_i + win
            dur = PHRASE_MAX
        chunk = x[a:b].copy()
        # pad small
        peak = float(np.max(np.abs(chunk))) + 1e-9
        chunk = chunk / peak * 0.92
        r = float(np.sqrt(np.mean(chunk * chunk)))
        # speechiness
        z = float(np.mean((chunk[:-1] >= 0) != (chunk[1:] >= 0))) if chunk.size > 2 else 0
        score = r * (0.4 + z * 2.0) * min(dur, 4.0)
        candidates.append((score, a / sr, dur, chunk))
    if not candidates:
        return None
    candidates.sort(key=lambda t: -t[0])
    sc, t0, dur, chunk = candidates[0]
    return chunk, t0, dur


def collect_sources(fighter: str) -> list[Path]:
    paths: list[Path] = []
    src = CONVERTED / fighter / "sources"
    if src.is_dir():
        for p in sorted(src.glob("*.wav"), key=lambda q: -q.stat().st_size):
            # skip huge multi-hour presidenta live for speed unless small enough
            if fighter == "LA_PRESIDENTA" and p.stat().st_size > 80_000_000:
                continue  # prefer clip1-sized
            if p.stat().st_size > 20_000:
                paths.append(p)
    # locals
    if fighter == "PRANKEDY" and LOCAL.is_dir():
        paths = list(LOCAL.glob("Prankedy*.mp3")) + paths
    if fighter == "PAPARAZZI_1" and LOCAL.is_dir():
        paths = list(LOCAL.glob("*PAPARAZZI*")) + list(LOCAL.glob("*paparazzi*")) + paths
    if fighter == "CHARRO_NEGRO":
        # Nahual canon: YT DK5ZgKoM8Xw
        for folder in (LOCAL, SCRAPE / "raw_yt", SCRAPE / "converted" / "CHARRO_NEGRO" / "sources"):
            if not folder.is_dir():
                continue
            for p in folder.glob("*"):
                n = p.name.lower()
                if p.is_file() and ("nahual" in n or "dk5zgkom8xw" in n):
                    paths.insert(0, p)
    # curated
    for name in ("prankedy_taco.wav", "prankedy_tank.wav", "senor_tienda.wav", "llorona_ay.wav"):
        p = SCRAPE / "raw_curated" / name
        if not p.is_file():
            continue
        if fighter == "PRANKEDY" and "prankedy" in name:
            paths.insert(0, p)
        if fighter == "SENOR_TIENDA" and "senor" in name:
            paths.insert(0, p)
        if fighter == "LA_LLORONA" and "llorona" in name:
            paths.insert(0, p)

    # dedupe
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
    # limit
    return uniq[:6]


def process_fighter(fighter: str) -> dict:
    print(f"\n=== {fighter} (role={ROLE.get(fighter)}) ===", flush=True)
    sources = collect_sources(fighter)
    print(f"  sources: {len(sources)}", flush=True)
    cache = OUT / "_cache" / fighter
    role = ROLE.get(fighter, 0)

    best_overall = None  # (score, meta, chunk, sr)
    speaker_reports = []

    for path in sources:
        wav = ensure_wav(path, cache)
        if not wav:
            continue
        try:
            # 16k for speed after convert; or native
            x, sr = read_wav(wav, max_sec=MAX_SEG_SCAN if fighter != "LA_PRESIDENTA" else 180.0)
        except Exception as e:
            print(f"  read fail {path.name}: {e}")
            continue
        if float(np.sqrt(np.mean(x * x))) < 0.008:
            print(f"  silent {path.name}")
            continue
        segs = speech_segments(x, sr)
        if len(segs) < 2:
            print(f"  few segs {path.name}: {len(segs)}")
            # single speaker fallback
            if segs:
                a, b = segs[0]
                chunk = x[a:b]
                if (b - a) / sr >= PHRASE_MIN:
                    peak = float(np.max(np.abs(chunk))) + 1e-9
                    chunk = chunk / peak * 0.92
                    sc = float(np.sqrt(np.mean(chunk * chunk)))
                    cand = (sc, {
                        "src": path.name, "t0": a / sr, "dur": (b - a) / sr,
                        "role": "single", "speakers": 1,
                    }, chunk, sr)
                    if best_overall is None or cand[0] > best_overall[0]:
                        best_overall = cand
            continue

        feats = []
        keep_segs = []
        for a, b in segs:
            # cap segment length for feature stability
            if b - a > int(8 * sr):
                b = a + int(8 * sr)
            c = x[a:b]
            if c.size < int(0.3 * sr):
                continue
            feats.append(segment_features(c, sr))
            keep_segs.append((a, b))
        if len(feats) < 2:
            continue
        F = np.stack(feats)
        labels = cluster_speakers(F, max_k=3 if fighter in ("PAPARAZZI_1", "PAPARAZZI_5", "SENOR_TIENDA", "PRANKEDY") else 2)
        speakers = rank_speakers(labels, keep_segs, sr, F)
        chosen = pick_role(speakers, role)
        print(
            f"  {path.name}: segs={len(keep_segs)} speakers={len(speakers)} "
            + " | ".join(f"spk{s['label']}={s['total_sec']:.1f}s pitch={s['mean_pitch']:.0f}" for s in speakers),
            flush=True,
        )
        speaker_reports.append({
            "file": path.name,
            "speakers": [
                {"label": s["label"], "total_sec": round(s["total_sec"], 2),
                 "n_segs": s["n_segs"], "mean_pitch": round(s["mean_pitch"], 1),
                 "rank": i}
                for i, s in enumerate(speakers)
            ],
            "chosen_rank": speakers.index(chosen) if chosen in speakers else 0,
            "role": role,
        })
        if not chosen:
            continue
        phrase = best_phrase_from_speaker(x, sr, keep_segs, chosen["idxs"])
        if not phrase:
            continue
        chunk, t0, dur = phrase
        sc = float(np.sqrt(np.mean(chunk * chunk))) * (1.0 + 0.05 * chosen["total_sec"])
        # prefer multi-speaker success for pap/tienda (rank1 only valid if ≥2)
        if role == 1 and len(speakers) < 2:
            sc *= 0.35  # penalize if couldn't separate
        if role == 1 and chosen["mean_pitch"] < 85:
            sc *= 0.4  # likely music/noise cluster, not paparazzi/señor
        if role == "pitch_high":
            # boost real female pitch (~160–280 typical for speech)
            p = chosen["mean_pitch"]
            if p >= 170:
                sc *= 2.2
            elif p >= 150:
                sc *= 1.5
            elif p < 160:
                sc *= 0.25  # male-ish / wrong for Claudia/Llorona
        # prefer phrases that are long enough to hear a full line
        if meta_dur := (len(chunk) / sr):
            if meta_dur >= 2.2:
                sc *= 1.15
            if meta_dur < 1.6:
                sc *= 0.75
        # bonus: known good source names
        low = path.name.lower()
        if fighter == "LA_LLORONA" and "llorona" in low:
            sc *= 1.6
        if fighter == "SENOR_TIENDA" and "senor" in low:
            sc *= 1.2
        if fighter == "PAPARAZZI_1" and ("paparazzi" in low or "broma" in low):
            sc *= 1.15
        cand = (sc, {
            "src": path.name,
            "t0": round(t0, 2),
            "dur": round(dur, 3),
            "role": role,
            "speakers": len(speakers),
            "chosen_pitch": round(chosen["mean_pitch"], 1),
            "chosen_total_sec": round(chosen["total_sec"], 2),
        }, chunk, sr)
        print(f"    → phrase {dur:.2f}s @ t={t0:.1f} score={sc:.3f} pitch={chosen['mean_pitch']:.0f}", flush=True)
        if best_overall is None or cand[0] > best_overall[0]:
            best_overall = cand

    result = {
        "fighter": fighter,
        "ready": False,
        "meta": None,
        "speaker_reports": speaker_reports,
        "gap": None,
    }
    if not best_overall:
        result["gap"] = "no voice phrase extracted"
        print(f"  [NO] {fighter}")
        return result

    sc, meta, chunk, sr = best_overall
    # resample to 44100 for consistency if needed
    if sr != 44100:
        # linear resample
        t_old = np.linspace(0, 1, num=len(chunk), endpoint=False)
        n_new = int(len(chunk) * 44100 / sr)
        t_new = np.linspace(0, 1, num=n_new, endpoint=False)
        chunk = np.interp(t_new, t_old, chunk).astype(np.float32)
        sr = 44100

    out_dir = OUT / fighter
    out_dir.mkdir(parents=True, exist_ok=True)
    wav_path = out_dir / "special_voice.wav"
    write_wav(wav_path, chunk, sr)
    ogg_path = out_dir / f"special_{fighter.lower()}.ogg"
    ok = to_ogg(wav_path, ogg_path, meta["dur"])
    # also alt speakers dump for review
    result["ready"] = ok
    result["meta"] = meta
    result["wav"] = str(wav_path)
    result["ogg"] = str(ogg_path) if ok else None
    result["score"] = round(sc, 4)
    if role == 1 and meta.get("speakers", 0) < 2:
        result["gap"] = "solo 1 voz separada — rank2 (paparazzi/señor) puede ser incorrecto"
    src_u = (meta.get("src") or "").upper()
    if fighter == "PAPARAZZI_5" and (
        "E504959652BA" in src_u or "PAPARAZZI_1" in src_u or "BROMA" in src_u
    ):
        result["gap"] = ((result.get("gap") or "") + " | fuente puede ser Pap1 (falta AEmVeK88HIs)").strip(" |")
    print(f"  [OK] {fighter}: {meta['dur']:.2f}s from {meta['src']} @ {meta['t0']}s speakers={meta['speakers']}", flush=True)
    return result


def load_catalog_phrases() -> dict:
    if CATALOG.is_file():
        return json.loads(CATALOG.read_text(encoding="utf-8")).get("phrases", {})
    return {}


def build_review(results: dict) -> None:
    phrases = load_catalog_phrases()
    if REVIEW.exists():
        shutil.rmtree(REVIEW)
    for sub in ("00_TODOS_PLANOS", "01_diarized_voices", "02_speaker_report", "03_text_es_en"):
        (REVIEW / sub).mkdir(parents=True)

    lines = ["# Diarización de voces (principal = más habla)", ""]
    for fid in CANON:
        r = results.get(fid, {})
        meta = r.get("meta") or {}
        ph = phrases.get(fid, {})
        es = ph.get("phrase_es", "")
        en = ph.get("phrase_en", "")
        hud = ph.get("phrase_hud", "")
        lines.append(f"## {fid}")
        lines.append(f"- ready: {r.get('ready')} score={r.get('score')}")
        lines.append(f"- audio: {meta.get('src')} t0={meta.get('t0')}s dur={meta.get('dur')}s speakers={meta.get('speakers')}")
        lines.append(f"- role: {ROLE.get(fid)} pitch={meta.get('chosen_pitch')} total_voice={meta.get('chosen_total_sec')}s")
        lines.append(f"- frase ES: {es}")
        lines.append(f"- frase EN: {en}")
        lines.append(f"- gap: {r.get('gap') or '—'}")
        lines.append("")
        (REVIEW / "02_speaker_report" / f"{fid}.json").write_text(
            json.dumps(r.get("speaker_reports", []), indent=2), encoding="utf-8",
        )
        (REVIEW / "03_text_es_en" / f"{fid}.txt").write_text(
            f"FIGHTER: {fid}\nES: {es}\nEN: {en}\nHUD: {hud}\n"
            f"SRC: {meta.get('src')}\nT0: {meta.get('t0')}\nDUR: {meta.get('dur')}\n"
            f"SPEAKERS: {meta.get('speakers')}\nROLE: {ROLE.get(fid)}\n"
            f"PITCH: {meta.get('chosen_pitch')}\nGAP: {r.get('gap') or '—'}\n",
            encoding="utf-8",
        )
        ogg = r.get("ogg")
        if ogg and Path(ogg).is_file():
            slug = "".join(c if c.isalnum() else "_" for c in es.upper())[:40] or "VOICE"
            name = f"{fid}_voice__{slug}.mp3"
            dst = REVIEW / "01_diarized_voices" / name
            if to_mp3(Path(ogg), dst):
                shutil.copy2(dst, REVIEW / "00_TODOS_PLANOS" / name)

    (REVIEW / "DIARIZE_REPORT.md").write_text("\n".join(lines), encoding="utf-8")
    (OUT / "diarize_results.json").write_text(
        json.dumps({k: {kk: vv for kk, vv in v.items() if kk != "chunk"} for k, v in results.items()},
                   indent=2, ensure_ascii=False, default=str),
        encoding="utf-8",
    )
    (REVIEW / "LEEME.txt").write_text(
        "Voces diarizadas: se separan hablantes y se elige la correcta.\n"
        "PRANKEDY = voz #1 (más habla)\n"
        "PAPARAZZI_1/5 = voz #2 (segundo principal)\n"
        "SENOR_TIENDA = voz #2 (el señor)\n"
        "LLORONA / PRESIDENTA = pitch alto (femenina)\n"
        "Escucha 01_diarized_voices y lee 03_text_es_en.\n",
        encoding="utf-8",
    )
    print(f"\nREVIEW → {REVIEW.resolve()}")


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    print("=== Diarize special voices (rank by speech time) ===", flush=True)
    results = {}
    for f in CANON:
        results[f] = process_fighter(f)
    build_review(results)
    ready = sum(1 for f in CANON if results[f].get("ready"))
    print(f"\nReady {ready}/{len(CANON)} diarized voices")


if __name__ == "__main__":
    main()
