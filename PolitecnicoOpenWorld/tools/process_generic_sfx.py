#!/usr/bin/env python3
"""
process_generic_sfx.py
----------------------
LA_TZITZIMIME y YOALLI_EHECATL son GENÉRICOS:
  - NO se scrapea voz/frase del vídeo
  - SFX sintético propio (ffmpeg) + frase INVENTADA solo para subtítulo HUD
  - Si el usuario deja WAVs de los YT en raw_yt/ o nuevoMaterial, se usan como
    capa de textura (no como speech STT)

YT de referencia (textura SFX, no diálogo):
  LA_TZITZIMIME: Q95g6d-5UgA, cv7epf6iY3U
  YOALLI_EHECATL: Ye4PRm-kebc

Uso:
  python PolitecnicoOpenWorld/tools/process_generic_sfx.py
"""
from __future__ import annotations

import json
import shutil
import subprocess
import sys
from pathlib import Path

TOOLS = Path(__file__).resolve().parent
SCRAPE = TOOLS / "sf_voice_scrape"
REPO = TOOLS.parent.parent
LOCAL = REPO / "nuevoMaterial17JUL"
RAW_YT = SCRAPE / "raw_yt"
OUT = SCRAPE / "out_diarized"
REVIEW = SCRAPE / "REVIEW_MP3_TODOS"

# Frases INVENTADAS (no del scrape)
PHRASES = {
    "LA_TZITZIMIME": {
        "es": "¡Devoren el cielo!",
        "en": "Devour the sky!",
        "hud": "DEVOREN EL CIELO",
        "yt": ["Q95g6d-5UgA", "cv7epf6iY3U"],
        "style": "horror",
    },
    "YOALLI_EHECATL": {
        "es": "¡Soplo del anahuac!",
        "en": "Breath of Anahuac!",
        "hud": "SOPLO DEL ANAHUAC",
        "yt": ["Ye4PRm-kebc", "d1M7uqfsofs"],
        "style": "wind",
    },
}


def ffmpeg() -> str:
    return shutil.which("ffmpeg") or shutil.which("ffmpeg.exe") or "ffmpeg"


def is_silent(path: Path) -> bool:
    try:
        det = subprocess.run(
            [ffmpeg(), "-hide_banner", "-i", str(path), "-af", "volumedetect", "-t", "8", "-f", "null", "-"],
            capture_output=True, text=True, timeout=90,
        )
        err = det.stderr or ""
        return "max_volume: -91" in err or "mean_volume: -91" in err
    except Exception:
        return False


def find_texture(fighter: str, yt_ids: list[str]) -> Path | None:
    # Solo IDs YT del peleadór + nombre del peleadór (NO mezclar tzitzi/yoalli)
    keys = [fighter.lower().replace("_", "")] + [y.lower() for y in yt_ids]
    extra = []
    if fighter == "LA_TZITZIMIME":
        extra = ["tzitzimime", "tzitzi"]
    elif fighter == "YOALLI_EHECATL":
        extra = ["yoalli", "ehecatl"]
    keys = keys + extra
    cands: list[tuple[int, Path]] = []
    for folder in (RAW_YT, LOCAL, SCRAPE / "converted" / fighter / "sources"):
        if not folder.is_dir():
            continue
        for p in folder.iterdir():
            if not p.is_file():
                continue
            n = p.name.lower()
            if p.suffix.lower() not in {".wav", ".mp3", ".m4a", ".webm", ".ogg"}:
                continue
            if p.stat().st_size <= 8000:
                continue
            nflat = n.replace("_", "")
            if not any(k.replace("_", "") in nflat for k in keys):
                continue
            # priority: exact yt id in name > larger file
            prio = 0
            for i, yid in enumerate(yt_ids):
                if yid.lower() in n:
                    prio = 100 - i  # first listed YT preferred
                    break
            if prio == 0:
                prio = 1
            cands.append((prio, p))
    # highest prio first, then size
    cands.sort(key=lambda t: (-t[0], -t[1].stat().st_size))
    for prio, p in cands:
        if is_silent(p):
            print(f"  skip silent texture {p.name}")
            continue
        return p
    return None


def synth_horror(dst: Path, dur: float = 2.4) -> bool:
    """SFX horror original (sin voz scrapeada)."""
    dst.parent.mkdir(parents=True, exist_ok=True)
    # layered: low drone + noise whoosh + dissonant tones
    cmd = [
        ffmpeg(), "-y", "-hide_banner", "-loglevel", "error",
        "-f", "lavfi", "-i", f"sine=frequency=55:duration={dur}",
        "-f", "lavfi", "-i", f"sine=frequency=82.5:duration={dur}",
        "-f", "lavfi", "-i", f"anoisesrc=d={dur}:c=pink:a=0.35",
        "-filter_complex",
        f"[0][1]amix=inputs=2:duration=first,volume=0.45[d];"
        f"[2]highpass=f=200,lowpass=f=2500,volume=0.55[n];"
        f"[d][n]amix=inputs=2:duration=first,"
        f"aecho=0.8:0.88:60:0.4,asetrate=44100*0.92,aresample=44100,"
        f"afade=t=in:d=0.08,afade=t=out:st={dur-0.25}:d=0.25,"
        f"dynaudnorm,volume=1.35",
        "-t", f"{dur}", "-c:a", "libvorbis", "-q:a", "5", str(dst),
    ]
    try:
        subprocess.run(cmd, check=True, timeout=60)
        return dst.exists() and dst.stat().st_size > 2000
    except Exception as e:
        print("horror synth fail", e)
        return False


def synth_wind(dst: Path, dur: float = 2.2) -> bool:
    """SFX viento/místico original."""
    dst.parent.mkdir(parents=True, exist_ok=True)
    cmd = [
        ffmpeg(), "-y", "-hide_banner", "-loglevel", "error",
        "-f", "lavfi", "-i", f"anoisesrc=d={dur}:c=brown:a=0.5",
        "-f", "lavfi", "-i", f"sine=frequency=220:duration={dur}",
        "-f", "lavfi", "-i", f"sine=frequency=330:duration={dur}",
        "-filter_complex",
        f"[0]highpass=f=300,lowpass=f=2800,volume=0.7[w];"
        f"[1][2]amix=inputs=2:duration=first,volume=0.2,vibrato=f=3:d=0.4[t];"
        f"[w][t]amix=inputs=2:duration=first,"
        f"aecho=0.8:0.75:50:0.25,"
        f"afade=t=in:d=0.1,afade=t=out:st={dur-0.3}:d=0.3,"
        f"dynaudnorm,volume=1.25",
        "-t", f"{dur}", "-c:a", "libvorbis", "-q:a", "5", str(dst),
    ]
    try:
        subprocess.run(cmd, check=True, timeout=60)
        return dst.exists() and dst.stat().st_size > 2000
    except Exception as e:
        print("wind synth fail", e)
        return False


def texture_to_sfx(src: Path, dst: Path, style: str, dur: float = 2.5) -> bool:
    """Usa audio de YT solo como textura; NO extrae 'frase' hablada."""
    dst.parent.mkdir(parents=True, exist_ok=True)
    if style == "horror":
        af = (
            f"highpass=f=80,lowpass=f=4000,asetrate=44100*0.88,aresample=44100,"
            f"aecho=0.8:0.9:70:0.45,tremolo=f=4:d=0.4,"
            f"afade=t=in:d=0.08,afade=t=out:st={dur-0.25}:d=0.25,"
            f"dynaudnorm=f=90:g=12,volume=1.3"
        )
    else:
        af = (
            f"highpass=f=200,lowpass=f=3500,vibrato=f=5:d=0.35,"
            f"aecho=0.8:0.7:45:0.3,"
            f"afade=t=in:d=0.1,afade=t=out:st={dur-0.28}:d=0.28,"
            f"dynaudnorm=f=90:g=12,volume=1.2"
        )
    # take a mid section if long
    try:
        # start ~5s in to skip intros
        subprocess.run(
            [ffmpeg(), "-y", "-hide_banner", "-loglevel", "error",
             "-ss", "5", "-i", str(src), "-t", f"{dur}", "-af", af,
             "-c:a", "libvorbis", "-q:a", "5", str(dst)],
            check=True, timeout=120,
        )
        return dst.exists() and dst.stat().st_size > 2000
    except Exception as e:
        print("texture fail", e)
        return False


def to_mp3(src: Path, dst: Path) -> bool:
    dst.parent.mkdir(parents=True, exist_ok=True)
    try:
        subprocess.run(
            [ffmpeg(), "-y", "-hide_banner", "-loglevel", "error",
             "-i", str(src), "-ac", "1", "-ar", "44100", "-b:a", "192k", str(dst)],
            check=True, timeout=60,
        )
        return dst.exists()
    except Exception:
        return False


def process_one(fighter: str, meta: dict) -> dict:
    print(f"\n=== {fighter} (generic SFX, invented phrase) ===")
    out_dir = OUT / fighter
    out_dir.mkdir(parents=True, exist_ok=True)
    ogg = out_dir / f"special_{fighter.lower()}.ogg"
    style = meta["style"]
    tex = find_texture(fighter, meta["yt"])
    used = "synth"
    ok = False
    if tex:
        # skip silent dumps (mean ≈ -91 dB)
        try:
            import subprocess as sp
            det = sp.run(
                [ffmpeg(), "-hide_banner", "-i", str(tex), "-af", "volumedetect", "-f", "null", "-"],
                capture_output=True, text=True, timeout=60,
            )
            silent = "max_volume: -91" in (det.stderr or "")
        except Exception:
            silent = False
        if silent:
            print(f"  skip silent texture {tex.name}")
        else:
            print(f"  texture file (no speech scrape): {tex.name}")
            ok = texture_to_sfx(tex, ogg, style)
            if ok:
                used = f"texture:{tex.name}"
    if not ok:
        print("  synthesizing original SFX...")
        if style == "horror":
            ok = synth_horror(ogg)
        else:
            ok = synth_wind(ogg)
        used = "synth_original"
    # wav copy for review tools
    wav = out_dir / "special_voice.wav"
    if ok:
        subprocess.run(
            [ffmpeg(), "-y", "-hide_banner", "-loglevel", "error",
             "-i", str(ogg), "-ac", "1", "-ar", "44100", str(wav)],
            check=False, timeout=60,
        )
    info = {
        "fighter": fighter,
        "ready": ok,
        "source_mode": used,
        "speech_from_scrape": False,
        "phrase_origin": "original_invented",
        "phrase_es": meta["es"],
        "phrase_en": meta["en"],
        "phrase_hud": meta["hud"],
        "youtube_ids": meta["yt"],
        "ogg": str(ogg) if ok else None,
    }
    (out_dir / "meta.json").write_text(json.dumps(info, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"  phrase ES (invented): {meta['es']}")
    print(f"  [{ 'OK' if ok else 'FAIL' }] {used}")
    return info


def main() -> int:
    results = {}
    for fid, meta in PHRASES.items():
        results[fid] = process_one(fid, meta)

    # patch review MP3 for these two
    REVIEW.mkdir(parents=True, exist_ok=True)
    dest = REVIEW / "01_phrases_best"
    dest.mkdir(parents=True, exist_ok=True)
    flat = REVIEW / "00_TODOS_PLANOS"
    flat.mkdir(parents=True, exist_ok=True)
    texts = REVIEW / "04_srt_and_text"
    texts.mkdir(parents=True, exist_ok=True)
    for fid, r in results.items():
        if not r.get("ogg"):
            continue
        slug = r["phrase_hud"].replace(" ", "_")
        name = f"{fid}_special__{slug}.mp3"
        mp3 = dest / name
        if to_mp3(Path(r["ogg"]), mp3):
            shutil.copy2(mp3, flat / name)
        (texts / f"{fid}.txt").write_text(
            f"FIGHTER: {fid}\n"
            f"MODE: generic SFX (NO voice scrape)\n"
            f"ES (invented): {r['phrase_es']}\n"
            f"EN (invented): {r['phrase_en']}\n"
            f"HUD: {r['phrase_hud']}\n"
            f"SOURCE: {r['source_mode']}\n"
            f"YT refs (texture only): {', '.join(r['youtube_ids'])}\n",
            encoding="utf-8",
        )

    # rebuild full pack
    subprocess.run([sys.executable, str(TOOLS / "build_special_phrases_pack.py")], check=False)
    print("\nDone. Generics use invented phrases + original/texture SFX only.")
    return 0 if all(r.get("ready") for r in results.values()) else 1


if __name__ == "__main__":
    raise SystemExit(main())
