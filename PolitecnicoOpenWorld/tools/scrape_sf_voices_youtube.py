#!/usr/bin/env python3
"""
scrape_sf_voices_youtube.py — YouTube / multi-source voice scrape via yt-dlp.

Downloads AUDIO (or short sections) for each fighter in
tools/sf_voice_scrape/catalog_youtube_voices.json, extracts mono WAV,
and MERGES entries into tools/sf_voice_scrape/manifest.json
(so pack_sf_character_sfx.py can use X + local + YouTube together).

Usage (repo root):
  python PolitecnicoOpenWorld/tools/scrape_sf_voices_youtube.py
  python PolitecnicoOpenWorld/tools/scrape_sf_voices_youtube.py --max-per-fighter 2
  python PolitecnicoOpenWorld/tools/scrape_sf_voices_youtube.py --fighters PRANKEDY,LA_PRESIDENTA
"""
from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Any

TOOLS = Path(__file__).resolve().parent
SCRAPE_DIR = TOOLS / "sf_voice_scrape"
CATALOG = SCRAPE_DIR / "catalog_youtube_voices.json"
RAW_DIR = SCRAPE_DIR / "raw_yt"
EXTRACTED_DIR = SCRAPE_DIR / "extracted"
MANIFEST = SCRAPE_DIR / "manifest.json"


def find_tool(name: str) -> str:
    p = shutil.which(name) or shutil.which(f"{name}.exe")
    if not p:
        raise SystemExit(f"{name} not found on PATH")
    return p


def uid_for(s: str) -> str:
    return hashlib.sha1(s.encode("utf-8")).hexdigest()[:12]


def load_json(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as f:
        return json.load(f)


def ytdlp_download(
    ytdlp: str,
    ffmpeg: str,
    url: str,
    out_base: Path,
    section: str | None,
) -> Path | None:
    """
    Download audio for url into out_base.wav (or out_base.ext then convert).
    Returns path to a media file ready for ffmpeg extract, or None.
    """
    out_base.parent.mkdir(parents=True, exist_ok=True)
    # Prefer existing wav/mp3/m4a/webm/opus
    for ext in (".wav", ".mp3", ".m4a", ".webm", ".opus", ".ogg", ".mp4"):
        cand = out_base.with_suffix(ext)
        if cand.exists() and cand.stat().st_size > 5000:
            print(f"  [skip] have {cand.name}")
            return cand

    # yt-dlp template: force audio extract to m4a/webm then we wav it
    tmpl = str(out_base) + ".%(ext)s"
    cmd = [
        ytdlp,
        "--no-playlist",
        "--ffmpeg-location", str(Path(ffmpeg).parent),
        "-x",
        "--audio-format", "wav",
        "--audio-quality", "0",
        "-o", tmpl,
        "--no-warnings",
        "--retries", "3",
        "--fragment-retries", "3",
        "--socket-timeout", "30",
    ]
    if section:
        # *start-end  e.g. *0:30-1:20
        sec = section if section.startswith("*") else f"*{section}"
        cmd += ["--download-sections", sec, "--force-keyframes-at-cuts"]
    # For ytsearchN: only first result unless search_pick handled externally
    if url.startswith("ytsearch"):
        # keep as-is; yt-dlp returns N results — we take first via --playlist-end 1
        cmd += ["--playlist-end", "1"]

    cmd.append(url)
    print(f"  [yt-dlp] {url[:80]}..." if len(url) > 80 else f"  [yt-dlp] {url}")
    if section:
        print(f"           section={section}")
    try:
        r = subprocess.run(cmd, capture_output=True, text=True, timeout=300)
        if r.returncode != 0:
            err = (r.stderr or r.stdout or "")[-400:]
            print(f"  [fail ytdlp] {err}")
            return None
    except subprocess.TimeoutExpired:
        print("  [fail] timeout")
        return None

    # Find produced file
    matches = list(out_base.parent.glob(out_base.name + ".*"))
    matches = [m for m in matches if m.suffix.lower() in {
        ".wav", ".mp3", ".m4a", ".webm", ".opus", ".ogg", ".mp4", ".mkv"
    } and m.stat().st_size > 2000]
    if not matches:
        # sometimes extension differs
        matches = sorted(out_base.parent.glob(out_base.name + "*"), key=lambda p: -p.stat().st_size)
        matches = [m for m in matches if m.is_file() and m.stat().st_size > 2000]
    if not matches:
        print("  [fail] no output file")
        return None
    best = max(matches, key=lambda p: p.stat().st_size)
    print(f"  [ok] {best.name} ({best.stat().st_size} B)")
    return best


def to_wav(ffmpeg: str, media: Path, wav_out: Path) -> bool:
    if wav_out.exists() and wav_out.stat().st_size > 1000:
        return True
    if media.suffix.lower() == ".wav" and media.resolve() != wav_out.resolve():
        shutil.copy2(media, wav_out)
        return True
    cmd = [
        ffmpeg, "-y", "-hide_banner", "-loglevel", "error",
        "-i", str(media),
        "-vn", "-ac", "1", "-ar", "44100", "-c:a", "pcm_s16le",
        str(wav_out),
    ]
    try:
        subprocess.run(cmd, check=True, timeout=120)
        ok = wav_out.exists() and wav_out.stat().st_size > 1000
        if ok:
            print(f"  [wav] {wav_out.name} ({wav_out.stat().st_size} B)")
        return ok
    except (subprocess.CalledProcessError, subprocess.TimeoutExpired) as e:
        print(f"  [ffmpeg fail] {e}")
        return False


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--max-per-fighter", type=int, default=2)
    ap.add_argument("--fighters", type=str, default="")
    args = ap.parse_args()

    only = {s.strip().upper() for s in args.fighters.split(",") if s.strip()}
    cat = load_json(CATALOG)
    ytdlp = find_tool("yt-dlp")
    ffmpeg = find_tool("ffmpeg")
    RAW_DIR.mkdir(parents=True, exist_ok=True)
    EXTRACTED_DIR.mkdir(parents=True, exist_ok=True)

    entries: list[dict[str, Any]] = []
    counts: dict[str, int] = {}

    # Sort by priority desc
    items = sorted(cat.get("youtube", []), key=lambda x: -int(x.get("priority", 0)))

    for src in items:
        fighter = str(src["fighter"]).upper()
        if only and fighter not in only:
            continue
        if counts.get(fighter, 0) >= args.max_per_fighter:
            continue

        url = src["url"]
        uid = uid_for(url + "|" + fighter + "|" + str(src.get("section", "")))
        out_base = RAW_DIR / f"{fighter}_{uid}"
        media = ytdlp_download(ytdlp, ffmpeg, url, out_base, src.get("section"))
        if not media:
            continue
        wav = EXTRACTED_DIR / f"{fighter}_yt_{uid}.wav"
        if not to_wav(ffmpeg, media, wav):
            continue

        entries.append({
            "fighter": fighter,
            "source": "youtube",
            "path": str(wav),
            "raw": str(media),
            "priority": int(src.get("priority", 50)) + 20,  # boost YT over thin X clips
            "pitch_semitones": src.get("pitch_semitones", 0),
            "robotize": bool(src.get("robotize", False)),
            "url": url,
            "title": src.get("title", ""),
            "section": src.get("section"),
        })
        counts[fighter] = counts.get(fighter, 0) + 1
        print(f"  → {fighter} count={counts[fighter]}")

    # Merge into existing manifest (from X scrape) if present
    existing: list[dict[str, Any]] = []
    old_counts: dict[str, int] = {}
    if MANIFEST.is_file():
        man = load_json(MANIFEST)
        existing = list(man.get("entries", []))
        old_counts = dict(man.get("counts", {}))

    # Drop previous youtube entries for fighters we refreshed (optional: keep all)
    merged = [e for e in existing if e.get("source") != "youtube"]
    merged.extend(entries)

    new_counts: dict[str, int] = {}
    for e in merged:
        f = e["fighter"].upper()
        new_counts[f] = new_counts.get(f, 0) + 1

    out_man = {
        "scraped_at": "2026-07-18",
        "sources": ["local", "x", "youtube"],
        "counts": new_counts,
        "entries": merged,
        "youtube_new": counts,
    }
    MANIFEST.write_text(json.dumps(out_man, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"\n=== Merged manifest: {MANIFEST} ===")
    print(f"YouTube new: {counts}")
    print(f"Total fighters: {len(new_counts)} entries: {len(merged)}")
    return 0 if entries else 1


if __name__ == "__main__":
    sys.exit(main())
