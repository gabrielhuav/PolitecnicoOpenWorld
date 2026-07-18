#!/usr/bin/env python3
"""
build_special_phrases_pack.py
-----------------------------
Empaqueta AUDIO + FRASE (texto ES/EN) para specials SF.

- Lee special_phrases_catalog.json (frases canónicas / genéricas)
- Toma mejores clips de out_phrases/candidates (frases completas)
- Escribe:
    tools/sf_voice_scrape/out_phrases_labeled/
      special_<id>.ogg
      special_<id>.wav
      labels.json
    app/.../STREETFIGHTER/DATA/special_phrases.json  (runtime i18n)
    tools/sf_voice_scrape/REVIEW_MP3_TODOS/  (MP3 con nombre + frase)

NO instala special_*.ogg en SOUNDS/ hasta aprobación.
LÁZARO excluido.

Uso:
  python PolitecnicoOpenWorld/tools/build_special_phrases_pack.py
"""
from __future__ import annotations

import json
import re
import shutil
import subprocess
import unicodedata
from pathlib import Path

TOOLS = Path(__file__).resolve().parent
SCRAPE = TOOLS / "sf_voice_scrape"
CATALOG = SCRAPE / "special_phrases_catalog.json"
PHRASES_OUT = SCRAPE / "out_phrases"
CAND = PHRASES_OUT / "candidates"
LABELED = SCRAPE / "out_phrases_labeled"
REVIEW = SCRAPE / "REVIEW_MP3_TODOS"
ASSETS_DATA = TOOLS.parent / "app" / "src" / "main" / "assets" / "STREETFIGHTER" / "DATA"
ASSETS_SOUNDS = TOOLS.parent / "app" / "src" / "main" / "assets" / "STREETFIGHTER" / "SOUNDS"


def ffmpeg() -> str:
    return shutil.which("ffmpeg") or shutil.which("ffmpeg.exe") or "ffmpeg"


def ffprobe() -> str:
    return shutil.which("ffprobe") or shutil.which("ffprobe.exe") or "ffprobe"


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


def to_mp3(src: Path, dst: Path) -> bool:
    dst.parent.mkdir(parents=True, exist_ok=True)
    try:
        subprocess.run(
            [ffmpeg(), "-y", "-hide_banner", "-loglevel", "error",
             "-i", str(src), "-ac", "1", "-ar", "44100", "-b:a", "192k", str(dst)],
            check=True, timeout=60,
        )
        return dst.exists() and dst.stat().st_size > 1500
    except Exception:
        return False


def slugify(text: str) -> str:
    t = unicodedata.normalize("NFKD", text)
    t = "".join(c for c in t if not unicodedata.combining(c))
    t = re.sub(r"[^A-Za-z0-9]+", "_", t).strip("_").upper()
    return t[:48] or "FRASE"


def hud_ascii(text: str) -> str:
    """Fuente SF HUD solo A-Z 0-9 espacio."""
    t = unicodedata.normalize("NFKD", text.upper())
    t = "".join(c for c in t if not unicodedata.combining(c))
    t = re.sub(r"[^A-Z0-9 ]+", " ", t)
    t = re.sub(r"\s+", " ", t).strip()
    return t


def pick_audio(fighter: str) -> Path | None:
    """Prefer diarized voice (correct speaker), then complete phrases, then assets."""
    # 1) Diarización: voz del personaje (principal / 2º / pitch alto)
    diar = SCRAPE / "out_diarized" / fighter / f"special_{fighter.lower()}.ogg"
    if diar.is_file() and diar.stat().st_size > 2000:
        return diar
    diar_wav = SCRAPE / "out_diarized" / fighter / "special_voice.wav"
    if diar_wav.is_file() and diar_wav.stat().st_size > 5000:
        return diar_wav
    # 2) Frases completas previas
    best = CAND / f"{fighter.lower()}_best.ogg"
    if best.is_file() and best.stat().st_size > 2000:
        return best
    for tag in ("best", "alt1", "alt2"):
        p = CAND / f"{fighter.lower()}_{tag}.ogg"
        if p.is_file() and p.stat().st_size > 2000:
            return p
    p = PHRASES_OUT / f"special_{fighter.lower()}.ogg"
    if p.is_file():
        return p
    # short pipeline fallback
    p = SCRAPE / "out_short" / f"special_{fighter.lower()}.ogg"
    if p.is_file():
        return p
    p = ASSETS_SOUNDS / f"special_{fighter.lower()}.ogg"
    if p.is_file():
        return p
    return None


def main() -> None:
    cat = json.loads(CATALOG.read_text(encoding="utf-8"))
    exclude = set(cat.get("exclude_fighters") or [])
    phrases = cat["phrases"]

    if LABELED.exists():
        shutil.rmtree(LABELED)
    LABELED.mkdir(parents=True)
    (LABELED / "audio").mkdir()
    (LABELED / "srt").mkdir()

    runtime = {
        "version": 1,
        "default_lang": "es",
        "supported_langs": ["es", "en"],
        "exclude_fighters": list(exclude),
        "fighters": {},
    }

    rows = []
    for fighter, meta in phrases.items():
        if fighter in exclude:
            print(f"skip excluded {fighter}")
            continue
        audio = pick_audio(fighter)
        es = meta["phrase_es"]
        en = meta["phrase_en"]
        hud = meta.get("phrase_hud") or hud_ascii(es)
        dur = probe_dur(audio) if audio else 0.0
        entry = {
            "fighter_id": fighter,
            "tier": meta.get("tier"),
            "phrase_es": es,
            "phrase_en": en,
            "phrase_hud": hud,
            "intent": meta.get("intent"),
            "gender": meta.get("gender"),
            "gap": meta.get("gap"),
            "needs_official_yt": bool(meta.get("needs_official_yt")),
            "audio_file": f"special_{fighter.lower()}.ogg",
            "duration_sec": round(dur, 3) if dur else None,
            "subtitle_ms": int((dur if dur > 0.5 else 2.5) * 1000) + 400,
            "has_audio_pack": audio is not None,
            "audio_source_path": str(audio) if audio else None,
        }
        runtime["fighters"][fighter] = {
            "phrase_es": es,
            "phrase_en": en,
            "phrase_hud": hud,
            "audio": f"special_{fighter.lower()}.ogg",
            "subtitle_ms": entry["subtitle_ms"],
            "tier": meta.get("tier"),
        }

        if audio:
            dst_ogg = LABELED / "audio" / f"special_{fighter.lower()}.ogg"
            shutil.copy2(audio, dst_ogg)
            # also wav for review tools
            dst_wav = LABELED / "audio" / f"special_{fighter.lower()}.wav"
            try:
                subprocess.run(
                    [ffmpeg(), "-y", "-hide_banner", "-loglevel", "error",
                     "-i", str(audio), "-ac", "1", "-ar", "44100", str(dst_wav)],
                    check=True, timeout=60,
                )
            except Exception:
                pass
            # mini srt for review
            srt = (
                "1\n"
                f"00:00:00,000 --> 00:00:{int(min(dur, 9)):02d},{int((min(dur, 9) % 1) * 1000):03d}\n"
                f"{es}\n\n"
                "2\n"
                f"00:00:00,000 --> 00:00:{int(min(dur, 9)):02d},{int((min(dur, 9) % 1) * 1000):03d}\n"
                f"{en}\n"
            )
            (LABELED / "srt" / f"{fighter.lower()}.srt").write_text(srt, encoding="utf-8")

        rows.append(entry)
        status = "OK" if audio else "NO_AUDIO"
        print(f"[{status}] {fighter}: «{es}» / «{en}»  dur={dur:.2f}s", flush=True)

    (LABELED / "labels.json").write_text(json.dumps(rows, indent=2, ensure_ascii=False), encoding="utf-8")
    ASSETS_DATA.mkdir(parents=True, exist_ok=True)
    out_json = ASSETS_DATA / "special_phrases.json"
    out_json.write_text(json.dumps(runtime, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"Wrote runtime catalog → {out_json}")

    # ---- REVIEW MP3 with phrase in filename ----
    if REVIEW.exists():
        shutil.rmtree(REVIEW)
    for sub in (
        "00_TODOS_PLANOS",
        "01_diarized_voices",   # famosos: voz separada (principal / 2º)
        "01_phrases_best",
        "02_alts_if_any",
        "03_curated_en_assets",
        "04_srt_and_text",
        "02_speaker_report",
    ):
        (REVIEW / sub).mkdir(parents=True)

    CANON = {
        "PRANKEDY", "PAPARAZZI_1", "PAPARAZZI_5", "SENOR_TIENDA",
        "REY_GRUPERO", "LA_LLORONA", "LA_PRESIDENTA",
    }
    diar_root = SCRAPE / "out_diarized"

    for entry in rows:
        fid = entry["fighter_id"]
        slug = slugify(entry["phrase_es"])
        src = LABELED / "audio" / entry["audio_file"]
        if not src.is_file():
            (REVIEW / "04_srt_and_text" / f"{fid}_SIN_AUDIO.txt").write_text(
                f"{entry['phrase_es']}\n{entry['phrase_en']}\nGAP: {entry.get('gap')}\n",
                encoding="utf-8",
            )
            continue
        name = f"{fid}_special__{slug}.mp3"
        # Canon → carpeta diarized; genéricos → phrases_best
        sub = "01_diarized_voices" if fid in CANON else "01_phrases_best"
        dst = REVIEW / sub / name
        if to_mp3(src, dst):
            shutil.copy2(dst, REVIEW / "00_TODOS_PLANOS" / name)
        # speaker report if diarized
        rep = diar_root / "diarize_results.json"
        if fid in CANON and rep.is_file():
            try:
                dj = json.loads(rep.read_text(encoding="utf-8"))
                if fid in dj:
                    (REVIEW / "02_speaker_report" / f"{fid}.json").write_text(
                        json.dumps(dj[fid].get("speaker_reports", dj[fid]), indent=2, ensure_ascii=False, default=str),
                        encoding="utf-8",
                    )
            except Exception:
                pass
        (REVIEW / "04_srt_and_text" / f"{fid}.txt").write_text(
            f"FIGHTER: {fid}\n"
            f"ES: {entry['phrase_es']}\n"
            f"EN: {entry['phrase_en']}\n"
            f"HUD: {entry['phrase_hud']}\n"
            f"DUR: {entry.get('duration_sec')}s\n"
            f"TIER: {entry.get('tier')}\n"
            f"GAP: {entry.get('gap') or '—'}\n"
            f"GENDER: {entry.get('gender')}\n"
            f"DIARIZED: {fid in CANON}\n"
            f"AUDIO_SRC: {entry.get('audio_source_path')}\n",
            encoding="utf-8",
        )

    # alts
    for p in CAND.glob("*_alt*.ogg"):
        stem = p.stem  # escomboy_alt1
        if "_" not in stem:
            continue
        char, tag = stem.rsplit("_", 1)
        fid = char.upper()
        if fid in exclude:
            continue
        meta = phrases.get(fid, {})
        slug = slugify(meta.get("phrase_es", tag))
        dst = REVIEW / "02_alts_if_any" / f"{fid}_special_{tag}__{slug}.mp3"
        if to_mp3(p, dst):
            shutil.copy2(dst, REVIEW / "00_TODOS_PLANOS" / f"02_{dst.name}")

    # assets existing
    if ASSETS_SOUNDS.is_dir():
        for p in ASSETS_SOUNDS.glob("special_*.ogg"):
            fid = p.stem.replace("special_", "").upper()
            if fid in exclude:
                continue
            meta = phrases.get(fid, {})
            slug = slugify(meta.get("phrase_es", "ASSETS"))
            dst = REVIEW / "03_curated_en_assets" / f"{fid}_special_EN_ASSETS__{slug}.mp3"
            if to_mp3(p, dst):
                shutil.copy2(dst, REVIEW / "00_TODOS_PLANOS" / f"03_{dst.name}")

    readme = f"""# REVIEW MP3 — voz correcta + FRASE (subtítulo)

## Cómo se elige la VOZ (famosos)
1. Se detectan segmentos de habla en el vídeo/fuente original
2. Se separan en hablantes (clustering por timbre/pitch)
3. Ranking por TIEMPO de habla (la que más se repite = principal)
4. Asignación:
   - PRANKEDY = voz #1 (más habla)
   - PAPARAZZI_1 / PAPARAZZI_5 = voz #2 (segundo principal)
   - SENOR_TIENDA = voz #2 (el señor, no Prankedy)
   - REY_GRUPERO = voz #1
   - LA_LLORONA / LA_PRESIDENTA = pitch más alto (femenina)

Script: `tools/diarize_special_voices.py`

## Carpetas
- `00_TODOS_PLANOS` — todos los MP3
- `01_diarized_voices` — famosos con voz separada + frase en el nombre
- `01_phrases_best` — genéricos
- `02_alts_if_any` — alternativas
- `02_speaker_report` — JSON de hablantes por archivo
- `03_curated_en_assets` — lo ya en el APK
- `04_srt_and_text` — ES + EN + HUD

## Nombre
`PERSONAJE_special__FRASE_SLUG.mp3`

## Excluidos
- LÁZARO

## Gaps
- PAPARAZZI_5: falta vídeo oficial AEmVeK88HIs (puede reusar Pap1)
- SENOR_TIENDA: mejor con playlist canónica YT
- LA_PRESIDENTA: verificar Claudia (mujer)
- Generics: no diarizados (OK genéricos)

## Runtime
`app/.../STREETFIGHTER/DATA/special_phrases.json`
"""
    (REVIEW / "LEEME.txt").write_text(readme, encoding="utf-8")
    # copy diarize report if present
    dr = diar_root / "diarize_results.json"
    if dr.is_file():
        shutil.copy2(dr, REVIEW / "DIARIZE_RESULTS.json")
    (LABELED / "INDEX.md").write_text(
        "# Phrases labeled\n\n" + "\n".join(
            f"- **{r['fighter_id']}**: ES `{r['phrase_es']}` | EN `{r['phrase_en']}` | audio={r['has_audio_pack']}"
            for r in rows
        ),
        encoding="utf-8",
    )
    print(f"\nREVIEW → {REVIEW.resolve()}")
    print(f"Labeled → {LABELED.resolve()}")
    print(f"Runtime JSON → {out_json}")


if __name__ == "__main__":
    main()
