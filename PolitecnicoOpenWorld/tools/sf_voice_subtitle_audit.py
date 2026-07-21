#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""AUDIT DE SUBTITULOS DE VOZ de HUELUM VS. GOYA (2026-07-21).

SOLO LECTURA sobre los datos curados: NO reescribe voice_phrases.json,
special_phrases.json ni tools/sf_audio_audit_report.md.

  ^^ Esto es deliberado. `sf_audio_audit.py` REGENERA el report, y
  `build_voice_phrases_catalog.py` saca los borradores de Whisper DE ESE REPORT.
  Si se regenera el report en una maquina sin faster-whisper, la columna de
  transcripcion sale vacia y el siguiente build BORRA los 53 drafts ya obtenidos.
  Por eso este audit mide y cruza, pero nunca escribe sobre lo curado.

Produce dos cosas:

  1. tools/_audio_review/_SUBTITULOS_AUDIT.csv
     Una fila por clip con la medicion objetiva + lo que hay curado + tres
     columnas VACIAS (ES_CORREGIDO / EN_CORREGIDO / NOTAS) para que el dueno
     escuche el mp3 hermano y escriba ahi su correccion.

  2. Hallazgos por consola: clips huerfanos, mapeados sin archivo, mp3 que
     faltan, loudness fuera de rango, clips demasiado largos para su evento y
     `subtitle_ms` de special_phrases.json que no cuadra con la duracion real.

Uso:
    python tools/sf_voice_subtitle_audit.py [--csv RUTA]

Para aplicar las correcciones del dueno una vez rellenado el CSV:
    python tools/sf_apply_subtitle_fixes.py <csv>
"""
from __future__ import annotations

import argparse
import csv
import io
import json
import os
import re
import subprocess
import sys
import tempfile
import wave

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sf_audio_audit import parse_voice_packs  # noqa: E402  (mismo directorio)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOUNDS = os.path.join(ROOT, "app", "src", "main", "assets", "STREETFIGHTER", "SOUNDS")
DATA = os.path.join(ROOT, "app", "src", "main", "assets", "STREETFIGHTER", "DATA")
REVIEW = os.path.join(ROOT, "tools", "_audio_review")
VOICE_JSON = os.path.join(DATA, "voice_phrases.json")
SPECIAL_JSON = os.path.join(DATA, "special_phrases.json")

FIGHTER_ENUM = os.path.join(
    ROOT, "app", "src", "main", "java", "ovh", "gabrielhuav", "pow",
    "domain", "models", "streetfighter", "SfModels.kt")

SR = 16000
TARGET_LUFS = -16.0
LUFS_TOL = 2.0
# Un grito de ataque/dano que dura mas que esto pisa la animacion y se solapa con
# el siguiente golpe. Los eventos narrativos (intro/win/loss/power) si pueden ser largos.
MAX_S_BY_EVENT = {"attack": 2.5, "hurt": 2.5, "lowHp": 3.0}


def _duration(path: str) -> float:
    out = subprocess.run(
        ["ffprobe", "-v", "error", "-show_entries", "format=duration",
         "-of", "default=noprint_wrappers=1:nokey=1", path],
        capture_output=True, text=True, check=True)
    return float(out.stdout.strip())


def _decode(path: str) -> np.ndarray:
    tmp = tempfile.mktemp(suffix=".wav")
    subprocess.run(["ffmpeg", "-y", "-v", "error", "-i", path,
                    "-ac", "1", "-ar", str(SR), tmp], check=True)
    with wave.open(tmp, "rb") as w:
        data = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float32)
    os.remove(tmp)
    return data / 32768.0


def _loudness(path: str) -> float | None:
    """LUFS integrado real (K-weighted) via el analisis de loudnorm."""
    p = subprocess.run(
        ["ffmpeg", "-hide_banner", "-i", path, "-af",
         "loudnorm=I=-16:TP=-1.5:LRA=11:print_format=json", "-f", "null", "-"],
        capture_output=True, text=True)
    m = re.search(r"\{[^{}]*\"input_i\"[^{}]*\}", p.stderr, re.S)
    if not m:
        return None
    try:
        return float(json.loads(m.group(0))["input_i"])
    except (ValueError, KeyError):
        return None


def fighter_ids() -> list[str]:
    """Ids del enum SfFighterId (para reconstruir la clave `special_<id>`)."""
    src = open(FIGHTER_ENUM, encoding="utf-8").read()
    body = src[src.index("enum class SfFighterId"):]
    body = body[body.index("{") + 1:]
    # Entradas del enum: MAYUSCULAS_CON_GUION seguidas de "(" al principio de linea.
    return re.findall(r"^\s{4}([A-Z][A-Z0-9_]*)\s*\(", body, re.M)


def _edges(sig: np.ndarray, thresh: float = 0.01) -> tuple[float, float]:
    """Silencio al principio y al final, en segundos."""
    loud = np.abs(sig) > thresh
    if not loud.any():
        return 0.0, 0.0
    idx = np.flatnonzero(loud)
    return idx[0] / SR, (len(sig) - 1 - idx[-1]) / SR


def suggest(row: dict) -> str:
    """Accion propuesta para el clip. Es una HIPOTESIS: la confirma el oido del dueno.

    Whisper devolviendo vacio en un clip corto es indicio (no prueba) de que ahi no hay
    habla sino un grito: esos no necesitan subtitulo. Se marcan como propuesta, nunca se
    aplican solos.
    """
    if row["tipo"] == "SFX_GLOBAL":
        return "—  (SFX global, no lleva subtitulo)"
    acciones = []
    if "LARGO_" in row["estado"]:
        acciones.append("**RECORTAR** (%ss)" % row["dur_s"])
    if "LOUDNESS" in row["estado"]:
        acciones.append("**NORMALIZAR** (%s LUFS)" % row["lufs"])
    if row["es_actual"].strip():
        acciones.append("verificar que el texto case con el audio")
    elif row["draft_whisper"].strip():
        acciones.append("CURAR `es` (hay draft)")
    else:
        acciones.append("¿grito? -> poner `-` si no lleva subtitulo")
    return "; ".join(acciones)


def write_markdown(path: str, rows: list, findings: list) -> None:
    voces = [r for r in rows if r["tipo"] != "SFX_GLOBAL"]
    por_peleador: dict[str, list] = {}
    for r in voces:
        por_peleador.setdefault(r["peleador"], []).append(r)

    out = []
    out.append("# AUDIT de VOCES y SUBTITULOS — HUELUM VS. GOYA\n")
    out.append("> Generado por `tools/sf_voice_subtitle_audit.py`. **No editar a mano:** "
               "se regenera. Las correcciones se escriben en "
               "`tools/_audio_review/_SUBTITULOS_AUDIT.csv` (columnas `ES_CORREGIDO` / "
               "`EN_CORREGIDO` / `NOTAS`) y se aplican con "
               "`tools/sf_apply_subtitle_fixes.py`.\n")
    out.append("Para escuchar cada clip: `tools/_audio_review/<clip>.mp3`.\n")
    out.append("\n## Resumen\n")
    out.append("| métrica | valor |\n|---|---|")
    out.append("| clips con voz | %d |" % len(voces))
    out.append("| con subtítulo `es` curado | %d |" %
               sum(1 for r in voces if r["es_actual"].strip()))
    out.append("| sin subtítulo `es` | %d |" %
               sum(1 for r in voces if not r["es_actual"].strip()))
    out.append("| hay draft de Whisper para curar | %d |" %
               sum(1 for r in voces if r["draft_whisper"].strip()))
    out.append("| sin draft (probable grito) | %d |" %
               sum(1 for r in voces if not r["draft_whisper"].strip()))
    out.append("| a RECORTAR (largos) | %d |" %
               sum(1 for r in voces if "LARGO_" in r["estado"]))
    out.append("| a NORMALIZAR | %d |" %
               sum(1 for r in voces if "LOUDNESS" in r["estado"]))

    for fighter in sorted(por_peleador):
        out.append("\n## %s\n" % fighter)
        out.append("| clip (.mp3) | evento | dur | LUFS | draft Whisper | `es` actual | acción propuesta |")
        out.append("|---|---|---|---|---|---|---|")
        for r in sorted(por_peleador[fighter], key=lambda x: x["clip"]):
            out.append("| `%s` | %s | %ss | %s | %s | %s | %s |" % (
                r["clip"], r["evento"], r["dur_s"], r["lufs"] or "—",
                (r["draft_whisper"] or "—").replace("|", "/")[:60],
                (r["es_actual"] or "—").replace("|", "/")[:50],
                suggest(r)))

    if findings:
        out.append("\n## Hallazgos estructurales\n")
        for f in findings:
            out.append("- %s" % f)

    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\r\n") as fh:
        fh.write("\n".join(out) + "\n")


def main() -> None:
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
    ap = argparse.ArgumentParser()
    ap.add_argument("--csv", default=os.path.join(REVIEW, "_SUBTITULOS_AUDIT.csv"))
    # El CSV vive en tools/_audio_review/, que esta en .gitignore: sirve para trabajar
    # pero NO viaja por git. El .md si se versiona, para que la siguiente sesion (o el
    # dueno desde el movil) tenga el audit aunque no tenga la carpeta local.
    ap.add_argument("--md", default=os.path.join(
        ROOT, "README for IAS", "SF", "AUDIT_VOCES_SUBTITULOS.md"))
    args = ap.parse_args()

    mapping, inline = parse_voice_packs()
    clips = json.load(open(VOICE_JSON, encoding="utf-8"))["clips"]
    specials = json.load(open(SPECIAL_JSON, encoding="utf-8"))["fighters"]

    on_disk = sorted(f[:-4] for f in os.listdir(SOUNDS) if f.endswith(".ogg"))
    disk_set = set(on_disk)

    # Un clip puede sonar por TRES rutas distintas; solo la primera lleva subtitulo por clip.
    #   VOZ      -> esta en sfVoicePacks (emitVoiceLines -> voice_phrases.json)
    #   SPECIAL  -> `special_<id>` que emite specialSfxKey() como fallback del poder
    #   SFX      -> golpes/salto/hadouken globales: no son voz y NUNCA llevan subtitulo
    special_keys = {"special_" + i.lower() for i in fighter_ids()}
    extra_voice = {"special_male_attack_grunt"}  # grito generico via maleGruntClip

    def kind_of(name: str) -> str:
        if name in mapping:
            return "VOZ"
        if name in special_keys:
            return "SPECIAL"
        if name in extra_voice:
            return "VOZ_GENERICA"
        if not name.startswith("special_"):
            return "SFX_GLOBAL"
        return "HUERFANO"

    rows, findings = [], []

    for name in on_disk:
        path = os.path.join(SOUNDS, name + ".ogg")
        dur = _duration(path)
        sig = _decode(path)
        lufs = _loudness(path)
        peak = 20 * np.log10(max(float(np.abs(sig).max()), 1e-9))
        head, tail = _edges(sig)

        owners = mapping.get(name, [])
        fighters = "|".join(sorted({f for f, _ in owners}))
        events = "|".join(sorted({e for _, e in owners}))
        if not owners:
            # No esta en sfVoicePacks, pero puede sonar igual por otra ruta: `special_<id>`
            # es el fallback del poder (specialSfxKey) y el grunt es el grito generico.
            if name in special_keys:
                fighters, events = name[len("special_"):].upper(), "power (fallback)"
            elif name in extra_voice:
                fighters, events = "(generico hombres)", "attack (fallback)"
            else:
                fighters = events = "(SIN MAPEO)"

        entry = clips.get(name, {})
        es, en, draft = entry.get("es", ""), entry.get("en", ""), entry.get("draft", "")

        kind = kind_of(name)
        habla = kind in ("VOZ", "SPECIAL", "VOZ_GENERICA")

        estado = []
        if kind == "HUERFANO":
            estado.append("HUERFANO")
        # Los SFX globales (golpes, salto, hadouken) NO son voz: ni subtitulo ni
        # objetivo de -16 LUFS (van a proposito por debajo para no tapar la voz).
        if habla:
            if name not in clips:
                estado.append("SIN_ENTRADA_JSON")
            if not es.strip():
                estado.append("FALTA_ES")
            if es.strip() and not en.strip():
                estado.append("FALTA_EN")
            if lufs is not None and abs(lufs - TARGET_LUFS) > LUFS_TOL:
                estado.append("LOUDNESS_%+.1f" % (lufs - TARGET_LUFS))
            if peak > -0.5:
                estado.append("PEAK_%.1fdB" % peak)
            for _, ev in owners:
                lim = MAX_S_BY_EVENT.get(ev)
                if lim and dur > lim:
                    estado.append("LARGO_%s_%.1fs" % (ev, dur))
                    break
            if head > 0.35:
                estado.append("SILENCIO_INI_%.1fs" % head)
        if not os.path.exists(os.path.join(REVIEW, name + ".mp3")):
            estado.append("SIN_MP3")

        rows.append({
            "clip": name, "tipo": kind, "peleador": fighters, "evento": events,
            "dur_s": "%.2f" % dur,
            "lufs": "" if lufs is None else "%.1f" % lufs,
            "peak_db": "%.1f" % peak,
            "sil_ini_s": "%.2f" % head, "sil_fin_s": "%.2f" % tail,
            "estado": ",".join(estado) or "OK",
            "draft_whisper": draft, "es_actual": es, "en_actual": en,
            "frase_inline_kotlin": inline.get(name, ""),
            "ES_CORREGIDO": "", "EN_CORREGIDO": "", "NOTAS": "",
        })

    for name in mapping:
        if name not in disk_set:
            findings.append("MAPEADO EN KOTLIN PERO NO EXISTE EL .ogg: %s" % name)
    for name in clips:
        if name not in disk_set:
            findings.append("EN voice_phrases.json PERO NO EXISTE EL .ogg: %s" % name)

    # subtitle_ms de special_phrases.json contra la duracion medida
    for fid, meta in specials.items():
        audio = meta.get("audio", "")
        base = audio[:-4] if audio.endswith(".ogg") else audio
        if base not in disk_set:
            findings.append("special_phrases[%s].audio no existe: %s" % (fid, audio))
            continue
        real_ms = _duration(os.path.join(SOUNDS, base + ".ogg")) * 1000
        declared = meta.get("subtitle_ms", 0)
        if abs(declared - real_ms) > 1500:
            findings.append(
                "special_phrases[%s].subtitle_ms=%d pero el audio dura %d ms (dif %+d)"
                % (fid, declared, real_ms, declared - real_ms))

    os.makedirs(os.path.dirname(args.csv), exist_ok=True)
    # utf-8-sig: Excel en Windows abre el CSV con acentos correctos sin importar nada.
    with open(args.csv, "w", encoding="utf-8-sig", newline="") as fh:
        w = csv.DictWriter(fh, fieldnames=list(rows[0].keys()))
        w.writeheader()
        w.writerows(rows)

    write_markdown(args.md, rows, findings)

    voces = [r for r in rows if r["tipo"] in ("VOZ", "SPECIAL", "VOZ_GENERICA")]
    print("clips .ogg totales      : %d" % len(rows))
    for k in ("VOZ", "SPECIAL", "VOZ_GENERICA", "SFX_GLOBAL", "HUERFANO"):
        n = sum(1 for r in rows if r["tipo"] == k)
        if n:
            print("  %-14s      : %d" % (k, n))
    print("-- sobre los %d clips CON VOZ --" % len(voces))
    print("  sin subtitulo es      : %d" % sum(1 for r in voces if "FALTA_ES" in r["estado"]))
    print("  es sin traducir en    : %d" % sum(1 for r in voces if "FALTA_EN" in r["estado"]))
    print("  loudness fuera de +-%.0f LUFS: %d" % (LUFS_TOL, sum(1 for r in voces if "LOUDNESS" in r["estado"])))
    print("  al borde del clipping : %d" % sum(1 for r in voces if "PEAK_" in r["estado"]))
    print("  largos para su evento : %d" % sum(1 for r in voces if "LARGO_" in r["estado"]))
    print("  sin mp3 de repaso     : %d" % sum(1 for r in rows if "SIN_MP3" in r["estado"]))
    print("  limpios               : %d" % sum(1 for r in voces if r["estado"] == "OK"))
    if findings:
        print("\nHALLAZGOS:")
        for f in findings:
            print("  - %s" % f)
    print("\nCSV: %s" % args.csv)


if __name__ == "__main__":
    main()
