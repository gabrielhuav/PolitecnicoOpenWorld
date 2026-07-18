#!/usr/bin/env python3
"""
scrape_phrases_v2.py — Phrase-first multi-source scrape for POW SF voice candidates.

- Reads tools/sf_voice_scrape/catalog_phrases_v2.json
- Downloads YouTube (yt-dlp + optional browser cookies) and uses local/extracted
- Extracts top-K ~1.1s speech windows per source
- Writes candidates/ + FINDINGS_REPORT.md
- Does NOT install into app assets (approval gate)

Usage (repo root):
  python PolitecnicoOpenWorld/tools/scrape_phrases_v2.py
  python PolitecnicoOpenWorld/tools/scrape_phrases_v2.py --fighters PRANKEDY,PAPARAZZI_1,PAPARAZZI_5
  python PolitecnicoOpenWorld/tools/scrape_phrases_v2.py --cookies-browser chrome
"""
from __future__ import annotations

import argparse
import glob as globmod
import hashlib
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
SCRAPE = TOOLS / "sf_voice_scrape"
CATALOG = SCRAPE / "catalog_phrases_v2.json"
EXTRACTED = SCRAPE / "extracted"
CAND_DIR = SCRAPE / "candidates"
RAW_PHRASE = SCRAPE / "raw_phrases_v2"
REPO = TOOLS.parent.parent  # repo root


def ffmpeg() -> str:
    p = shutil.which("ffmpeg") or shutil.which("ffmpeg.exe")
    if not p:
        raise SystemExit("ffmpeg required")
    return p


def ytdlp() -> str:
    p = shutil.which("yt-dlp") or shutil.which("yt-dlp.exe")
    if not p:
        raise SystemExit("yt-dlp required")
    return p


def uid(s: str) -> str:
    return hashlib.sha1(s.encode()).hexdigest()[:10]


def read_mono(path: Path, max_sec: float = 300.0) -> tuple[list[float], int]:
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


def speech_peaks(samples: list[float], sr: int, win_s: float = 1.1, top_k: int = 4) -> list[tuple[float, float]]:
    """Return list of (start_sec, score) for best non-overlapping speech windows."""
    win = int(win_s * sr)
    if len(samples) < win:
        peak = max(1e-6, max(abs(x) for x in samples))
        return [(0.0, peak)]
    hop = max(1, win // 6)
    scored: list[tuple[float, int]] = []
    for i in range(0, len(samples) - win, hop):
        chunk = samples[i : i + win]
        rms = math.sqrt(sum(x * x for x in chunk) / len(chunk))
        if rms < 0.02:
            continue
        zc = sum(1 for a, b in zip(chunk[::2], chunk[1::2]) if (a >= 0) != (b >= 0)) / max(1, len(chunk) // 2)
        diffs = [abs(chunk[j + 1] - chunk[j]) for j in range(0, len(chunk) - 1, 5)]
        mid = math.sqrt(sum(d * d for d in diffs) / max(1, len(diffs)))
        score = rms * (0.35 + zc * 2.2) * (0.25 + mid * 5.0)
        if rms > 0.45 and zc < 0.03:
            score *= 0.4  # likely music bed
        scored.append((score, i))
    scored.sort(reverse=True)
    picked: list[tuple[float, float]] = []
    used: list[int] = []
    for score, i in scored:
        if any(abs(i - u) < win * 0.85 for u in used):
            continue
        used.append(i)
        picked.append((i / sr, score))
        if len(picked) >= top_k:
            break
    return picked


def to_wav(media: Path, wav: Path) -> bool:
    if wav.exists() and wav.stat().st_size > 2000:
        return True
    wav.parent.mkdir(parents=True, exist_ok=True)
    try:
        subprocess.run(
            [ffmpeg(), "-y", "-hide_banner", "-loglevel", "error",
             "-i", str(media), "-vn", "-ac", "1", "-ar", "44100", "-c:a", "pcm_s16le", str(wav)],
            check=True, timeout=180,
        )
        return wav.exists() and wav.stat().st_size > 2000
    except Exception as e:
        print(f"  [ffmpeg fail] {e}")
        return False


def download_youtube(vid: str, out_base: Path, cookies_browser: str | None) -> Path | None:
    for ext in (".wav", ".m4a", ".webm", ".mp3", ".opus"):
        if (out_base.with_suffix(ext)).exists() and out_base.with_suffix(ext).stat().st_size > 5000:
            return out_base.with_suffix(ext)
    RAW_PHRASE.mkdir(parents=True, exist_ok=True)
    url = f"https://www.youtube.com/watch?v={vid}"
    cmd = [
        ytdlp(), "--no-playlist", "-f", "bestaudio/best",
        "-x", "--audio-format", "wav", "--audio-quality", "0",
        "-o", str(out_base) + ".%(ext)s",
        "--no-warnings", "--retries", "2",
    ]
    if cookies_browser:
        cmd += ["--cookies-from-browser", cookies_browser]
    cmd.append(url)
    print(f"  [yt] {vid} …")
    try:
        r = subprocess.run(cmd, capture_output=True, text=True, timeout=240)
        if r.returncode != 0:
            err = (r.stderr or r.stdout or "")[-300:]
            print(f"  [yt fail] {err}")
            return None
    except subprocess.TimeoutExpired:
        print("  [yt timeout]")
        return None
    matches = list(out_base.parent.glob(out_base.name + ".*"))
    matches = [m for m in matches if m.is_file() and m.stat().st_size > 5000]
    if not matches:
        return None
    best = max(matches, key=lambda p: p.stat().st_size)
    print(f"  [yt ok] {best.name} ({best.stat().st_size // 1024} KB)")
    return best


def resolve_local(path_str: str) -> Path | None:
    p = Path(path_str)
    if p.is_file():
        return p.resolve()
    for base in (REPO, SCRAPE, TOOLS, Path.cwd()):
        c = (base / path_str).resolve()
        if c.is_file():
            return c
        c2 = (base / path_str.replace("../", "")).resolve()
        if c2.is_file():
            return c2
    name = Path(path_str).name
    c = REPO / "nuevoMaterial17JUL" / name
    return c if c.is_file() else None


def resolve_glob(pattern: str) -> list[Path]:
    hits = list(EXTRACTED.glob(pattern))
    if hits:
        return sorted(hits, key=lambda p: -p.stat().st_size)
    # also under scrape
    hits = list(SCRAPE.glob("**/" + pattern))
    return sorted(hits, key=lambda p: -p.stat().st_size)[:5]


def harvest_wav(
    fighter: str,
    phrase_id: str,
    src_label: str,
    wav: Path,
    top_k: int,
    report: list[dict[str, Any]],
) -> int:
    try:
        samples, sr = read_mono(wav)
    except Exception as e:
        print(f"  [read fail] {wav.name}: {e}")
        return 0
    peaks = speech_peaks(samples, sr, win_s=1.1, top_k=top_k)
    n = 0
    out_dir = CAND_DIR / fighter / phrase_id
    out_dir.mkdir(parents=True, exist_ok=True)
    for k, (t0, score) in enumerate(peaks):
        a = int(t0 * sr)
        b = a + int(1.1 * sr)
        chunk = samples[a:b]
        if not chunk:
            continue
        peak = max(1e-6, max(abs(x) for x in chunk))
        chunk = [x / peak * 0.9 for x in chunk]
        rms = math.sqrt(sum(x * x for x in chunk) / len(chunk))
        if rms < 0.02:
            continue
        name = f"{src_label}__peak{k}_t{t0:.1f}s_s{score:.3f}.wav"
        out = out_dir / name
        write_mono(out, chunk, sr)
        report.append({
            "fighter": fighter,
            "phrase_id": phrase_id,
            "source": src_label,
            "file": str(out.relative_to(SCRAPE)),
            "t0": round(t0, 2),
            "score": round(score, 4),
            "rms": round(rms, 4),
            "bytes": out.stat().st_size,
        })
        n += 1
        print(f"  [cand] {fighter}/{phrase_id} {name} rms={rms:.3f}")
    return n


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--fighters", default="", help="Comma list or empty=all in catalog")
    ap.add_argument("--cookies-browser", default="", help="chrome|edge|firefox for yt-dlp")
    ap.add_argument("--top-k", type=int, default=3)
    ap.add_argument("--max-sources-per-phrase", type=int, default=4)
    args = ap.parse_args()

    only = {s.strip().upper() for s in args.fighters.split(",") if s.strip()}
    cat = json.loads(CATALOG.read_text(encoding="utf-8"))
    cookies = args.cookies_browser.strip() or None
    CAND_DIR.mkdir(parents=True, exist_ok=True)
    RAW_PHRASE.mkdir(parents=True, exist_ok=True)

    findings: list[dict[str, Any]] = []
    source_status: list[dict[str, Any]] = []
    totals: dict[str, int] = {}

    for fighter, data in cat["fighters"].items():
        if only and fighter not in only:
            continue
        print(f"\n=== {fighter} ===")
        for phrase in data.get("phrases", []):
            pid = phrase["phrase_id"]
            sources = sorted(phrase.get("sources", []), key=lambda s: -int(s.get("priority", 0)))
            used = 0
            for src in sources:
                if used >= args.max_sources_per_phrase:
                    break
                kind = src.get("kind", "")
                wav_path: Path | None = None
                label = ""

                if kind == "youtube":
                    vid = src["id"]
                    base = RAW_PHRASE / f"{fighter}_{vid}"
                    media = download_youtube(vid, base, cookies)
                    st = {"fighter": fighter, "phrase": pid, "kind": "youtube", "id": vid,
                          "title": src.get("title", ""), "ok": media is not None}
                    source_status.append(st)
                    if not media:
                        continue
                    wav_path = RAW_PHRASE / f"{fighter}_{vid}.wav"
                    if not to_wav(media, wav_path):
                        continue
                    label = f"yt_{vid}"
                elif kind == "local":
                    p = resolve_local(src["path"])
                    st = {"fighter": fighter, "phrase": pid, "kind": "local",
                          "path": src["path"], "ok": p is not None}
                    source_status.append(st)
                    if not p:
                        print(f"  [miss local] {src['path']}")
                        continue
                    wav_path = RAW_PHRASE / f"{fighter}_local_{uid(str(p))}.wav"
                    if not to_wav(p, wav_path):
                        continue
                    label = f"local_{p.stem[:20]}"
                elif kind == "local_extracted":
                    hits = resolve_glob(src["glob"])
                    st = {"fighter": fighter, "phrase": pid, "kind": "extracted",
                          "glob": src["glob"], "ok": bool(hits), "n": len(hits)}
                    source_status.append(st)
                    if not hits:
                        print(f"  [miss extracted] {src['glob']}")
                        continue
                    # use top 2 largest non-silent if possible
                    for h in hits[:2]:
                        if used >= args.max_sources_per_phrase:
                            break
                        label = f"ext_{h.stem[:24]}"
                        n = harvest_wav(fighter, pid, label, h, args.top_k, findings)
                        if n:
                            used += 1
                            totals[fighter] = totals.get(fighter, 0) + n
                    continue
                elif kind in ("x_note", "youtube_search"):
                    source_status.append({
                        "fighter": fighter, "phrase": pid, "kind": kind,
                        "note": src.get("note") or src.get("query"), "ok": False,
                        "skipped": "manual / use extracted",
                    })
                    continue
                else:
                    continue

                if wav_path and wav_path.is_file():
                    n = harvest_wav(fighter, pid, label, wav_path, args.top_k, findings)
                    if n:
                        used += 1
                        totals[fighter] = totals.get(fighter, 0) + n

    # Write reports
    (CAND_DIR / "candidates_index.json").write_text(
        json.dumps({"totals": totals, "candidates": findings, "sources": source_status},
                   indent=2, ensure_ascii=False),
        encoding="utf-8",
    )
    write_findings_md(cat, totals, findings, source_status)
    print(f"\n=== Done. Candidates: {sum(totals.values())} across {len(totals)} fighters ===")
    print(f"Report: {CAND_DIR / 'FINDINGS_REPORT.md'}")
    return 0 if findings else 1


def write_findings_md(
    cat: dict,
    totals: dict[str, int],
    findings: list[dict],
    source_status: list[dict],
) -> None:
    lines = [
        "# Hallazgos scrape frases v2 (sin instalar en POW)",
        "",
        "> Generado por `scrape_phrases_v2.py`. **No** copiado a `assets/`.",
        "> El dueño aprueba → luego `curate` / install.",
        "",
        "## Metodología",
        "- Frase-first multi-fuente (`catalog_phrases_v2.json`)",
        "- Paparazzi 1/5 con vídeos **oficiales distintos** del canal Prankedy",
        "- Playlist Paparazzi completa catalogada (20 entradas)",
        "- Top-K picos por speech-score por fuente",
        "",
        "## Totales de candidatos por peleadór",
        "",
        "| Peleador | # clips candidato |",
        "|----------|-------------------|",
    ]
    for f, n in sorted(totals.items()):
        lines.append(f"| {f} | {n} |")
    if not totals:
        lines.append("| *(ninguno — revisa cookies YT / locales)* | 0 |")

    lines += ["", "## Fuentes intentadas (ok/fail)", ""]
    yt_ok = [s for s in source_status if s.get("kind") == "youtube" and s.get("ok")]
    yt_fail = [s for s in source_status if s.get("kind") == "youtube" and not s.get("ok")]
    loc_ok = [s for s in source_status if s.get("kind") in ("local", "extracted") and s.get("ok")]
    lines.append(f"- YouTube OK: **{len(yt_ok)}** · FAIL (bot/403): **{len(yt_fail)}**")
    lines.append(f"- Local/extracted OK: **{len(loc_ok)}**")
    lines.append("")
    if yt_ok:
        lines.append("### YouTube descargados")
        for s in yt_ok:
            lines.append(f"- `{s.get('id')}` — {s.get('title')} → **{s.get('fighter')}** / {s.get('phrase')}")
    if yt_fail:
        lines.append("")
        lines.append("### YouTube fallidos (probar `--cookies-browser chrome`)")
        for s in yt_fail[:25]:
            lines.append(f"- `{s.get('id')}` — {s.get('title')} ({s.get('fighter')})")

    lines += ["", "## Canon Paparazzi (playlist @Prankedy)", ""]
    for e in cat.get("paparazzi_playlist_ids", {}).get("entries", []):
        mark = ""
        if e["n"] == 1:
            mark = " ← **PAPARAZZI_1**"
        if e["n"] == 5:
            mark = " ← **PAPARAZZI_5**"
        views = e.get("views") or "?"
        lines.append(f"- #{e['n']}: `{e['id']}` {e['title']} (~{views} views){mark}")

    lines += ["", "## Mejores candidatos (top score por peleadór)", ""]
    by_f: dict[str, list] = {}
    for c in findings:
        by_f.setdefault(c["fighter"], []).append(c)
    for f, lst in sorted(by_f.items()):
        lst = sorted(lst, key=lambda x: -x["score"])[:5]
        lines.append(f"### {f}")
        for c in lst:
            lines.append(
                f"- score={c['score']:.3f} rms={c['rms']:.3f} t={c['t0']}s  "
                f"`{c['file']}`  (phrase={c['phrase_id']}, src={c['source']})"
            )
        lines.append("")

    lines += [
        "## Siguiente paso (humano)",
        "1. Revisar `tools/sf_voice_scrape/candidates/<FIGHTER>/`",
        "2. Decir qué phrase_id/peak usar por peleadór",
        "3. Solo entonces: curate/install a `special_*.ogg`",
        "",
    ]
    (CAND_DIR / "FINDINGS_REPORT.md").write_text("\n".join(lines), encoding="utf-8")


if __name__ == "__main__":
    sys.exit(main())
