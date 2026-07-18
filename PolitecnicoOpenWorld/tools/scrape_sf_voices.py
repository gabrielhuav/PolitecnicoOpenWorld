#!/usr/bin/env python3
"""
scrape_sf_voices.py — Deep X/Twitter voice scrape + local material ingest for POW SF specials.

Reads tools/sf_voice_scrape/catalog_x_voices.json (built from live X search of
@Prankedyy, @Claudiashein, @SSC_CDMX, La Llorona, Charro Negro, Cruz Roja, etc.),
downloads video media, extracts mono WAV audio, and writes a manifest for pack_sf_character_sfx.py.

Usage (from repo root or tools/):
  python tools/scrape_sf_voices.py
  python tools/scrape_sf_voices.py --max-per-fighter 3
  python tools/scrape_sf_voices.py --fighters PRANKEDY,LA_PRESIDENTA

Does NOT require Twitter API keys: uses public video.twimg.com CDN URLs from the catalog.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

TOOLS = Path(__file__).resolve().parent
SCRAPE_DIR = TOOLS / "sf_voice_scrape"
CATALOG = SCRAPE_DIR / "catalog_x_voices.json"
RAW_DIR = SCRAPE_DIR / "raw"
EXTRACTED_DIR = SCRAPE_DIR / "extracted"
MANIFEST = SCRAPE_DIR / "manifest.json"
# Repo root = parent of PolitecnicoOpenWorld/ (android project folder's parent)
REPO_ROOT = TOOLS.parent.parent


def find_ffmpeg() -> str:
    for name in ("ffmpeg", "ffmpeg.exe"):
        p = shutil.which(name)
        if p:
            return p
    raise SystemExit("ffmpeg not found on PATH — required to extract audio")


def load_catalog() -> dict[str, Any]:
    with CATALOG.open(encoding="utf-8") as f:
        return json.load(f)


def resolve_local(path_str: str) -> Path:
    p = Path(path_str)
    if p.is_file():
        return p.resolve()
    # catalog paths are relative to REPO_ROOT or SCRAPE_DIR
    for base in (REPO_ROOT, SCRAPE_DIR, TOOLS, Path.cwd()):
        cand = (base / path_str).resolve()
        if cand.is_file():
            return cand
        # also try stripping leading ../
        cand2 = (base / path_str.replace("../", "")).resolve()
        if cand2.is_file():
            return cand2
    # known local folder names
    name = Path(path_str).name
    for folder in (REPO_ROOT / "nuevoMaterial17JUL", REPO_ROOT):
        cand = folder / name
        if cand.is_file():
            return cand.resolve()
    return (REPO_ROOT / "nuevoMaterial17JUL" / name)


def uid_for(url_or_path: str) -> str:
    return hashlib.sha1(url_or_path.encode("utf-8")).hexdigest()[:12]


def download(url: str, dest: Path, timeout: int = 90) -> bool:
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.exists() and dest.stat().st_size > 10_000:
        print(f"  [skip] already have {dest.name} ({dest.stat().st_size} B)")
        return True
    print(f"  [dl] {url[:90]}...")
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) POW-SF-VoiceScrape/1.0",
            "Accept": "*/*",
            "Referer": "https://x.com/",
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            data = resp.read()
        if len(data) < 1000:
            print(f"  [warn] tiny response {len(data)} B — skip")
            return False
        dest.write_bytes(data)
        print(f"  [ok] {dest.name} ({len(data)} B)")
        return True
    except (urllib.error.URLError, TimeoutError, OSError) as e:
        print(f"  [fail] {e}")
        return False


def extract_audio(ffmpeg: str, media: Path, wav_out: Path) -> bool:
    wav_out.parent.mkdir(parents=True, exist_ok=True)
    if wav_out.exists() and wav_out.stat().st_size > 1000:
        return True
    cmd = [
        ffmpeg, "-y", "-hide_banner", "-loglevel", "error",
        "-i", str(media),
        "-vn",
        "-ac", "1",
        "-ar", "44100",
        "-c:a", "pcm_s16le",
        str(wav_out),
    ]
    try:
        subprocess.run(cmd, check=True, timeout=180)
        ok = wav_out.exists() and wav_out.stat().st_size > 1000
        if ok:
            print(f"  [wav] {wav_out.name} ({wav_out.stat().st_size} B)")
        return ok
    except (subprocess.CalledProcessError, subprocess.TimeoutExpired) as e:
        print(f"  [ffmpeg fail] {e}")
        return False


def main() -> int:
    ap = argparse.ArgumentParser(description="Scrape/download SF character voice sources")
    ap.add_argument("--max-per-fighter", type=int, default=4)
    ap.add_argument("--fighters", type=str, default="", help="Comma list of SfFighterId names")
    ap.add_argument("--skip-download", action="store_true")
    args = ap.parse_args()

    only = {s.strip().upper() for s in args.fighters.split(",") if s.strip()}
    cat = load_catalog()
    ffmpeg = find_ffmpeg()
    RAW_DIR.mkdir(parents=True, exist_ok=True)
    EXTRACTED_DIR.mkdir(parents=True, exist_ok=True)

    entries: list[dict[str, Any]] = []
    counts: dict[str, int] = {}

    def accept(fighter: str) -> bool:
        if only and fighter not in only:
            return False
        return counts.get(fighter, 0) < args.max_per_fighter

    # --- local MP3s first (highest priority) ---
    for src in sorted(cat.get("local_sources", []), key=lambda x: -int(x.get("priority", 0))):
        fighter = str(src["fighter"]).upper()
        if not accept(fighter):
            continue
        path = resolve_local(src["path"])
        if not path.is_file():
            # try repo-relative nuevoMaterial
            alt = REPO_ROOT / "nuevoMaterial17JUL" / Path(src["path"]).name
            if alt.is_file():
                path = alt
            else:
                print(f"  [miss local] {src['path']} -> {path}")
                continue
        uid = uid_for(str(path))
        dest = RAW_DIR / f"{fighter}_{uid}{path.suffix.lower()}"
        if not dest.exists():
            shutil.copy2(path, dest)
            print(f"  [local] {path.name} -> {dest.name}")
        wav = EXTRACTED_DIR / f"{fighter}_{uid}.wav"
        if extract_audio(ffmpeg, dest, wav):
            entries.append({
                "fighter": fighter,
                "source": "local",
                "path": str(wav),
                "raw": str(dest),
                "priority": int(src.get("priority", 50)),
                "pitch_semitones": src.get("pitch_semitones", 0),
                "robotize": bool(src.get("robotize", False)),
                "notes": src.get("notes", ""),
                "origin": str(path),
            })
            counts[fighter] = counts.get(fighter, 0) + 1

    # --- X videos ---
    for src in sorted(cat.get("x_videos", []), key=lambda x: -int(x.get("priority", 0))):
        fighter = str(src["fighter"]).upper()
        if not accept(fighter):
            continue
        url = src["url"]
        uid = uid_for(url)
        raw = RAW_DIR / f"{fighter}_{uid}.mp4"
        if not args.skip_download:
            if not download(url, raw):
                continue
        elif not raw.exists():
            continue
        wav = EXTRACTED_DIR / f"{fighter}_{uid}.wav"
        if extract_audio(ffmpeg, raw, wav):
            entries.append({
                "fighter": fighter,
                "source": "x",
                "path": str(wav),
                "raw": str(raw),
                "priority": int(src.get("priority", 50)),
                "pitch_semitones": src.get("pitch_semitones", 0),
                "robotize": bool(src.get("robotize", False)),
                "post_id": src.get("post_id"),
                "author": src.get("author"),
                "text": src.get("text", ""),
                "url": url,
            })
            counts[fighter] = counts.get(fighter, 0) + 1

    manifest = {
        "scraped_at": cat.get("_meta", {}).get("scraped_at"),
        "counts": counts,
        "entries": entries,
    }
    MANIFEST.write_text(json.dumps(manifest, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"\n=== Manifest: {MANIFEST} ===")
    print(f"Fighters with audio: {len(counts)}")
    for k, v in sorted(counts.items()):
        print(f"  {k}: {v}")
    print(f"Total entries: {len(entries)}")
    return 0 if entries else 1


if __name__ == "__main__":
    sys.exit(main())
