#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Normaliza a -16 LUFS los clips de voz que se salen de rango (2026-07-21).

Usa loudnorm de ffmpeg en DOS PASADAS (analiza y luego corrige con esos datos): la de una
sola pasada es dinamica y "bombea" el volumen dentro del clip, lo que en un grito corto se
oye fatal. Reencoda a OGG con la misma calidad que el resto del set.

Solo toca los clips CON VOZ que se desvian mas de la tolerancia. Los SFX globales
(golpes, salto, hadouken) se quedan como estan: van a proposito por debajo para no tapar
la voz, y "normalizarlos" los haria competir con ella.

Uso:
    python tools/sf_normalize_voice.py --dry-run
    python tools/sf_normalize_voice.py
"""
from __future__ import annotations

import argparse
import io
import json
import os
import re
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOUNDS = os.path.join(ROOT, "app", "src", "main", "assets", "STREETFIGHTER", "SOUNDS")
REVIEW = os.path.join(ROOT, "tools", "_audio_review")
OBJETIVO, TOL, TP = -16.0, 2.0, -1.5

# Medido por tools/sf_voice_subtitle_audit.py. Solo clips de VOZ.
CANDIDATOS = [
    "special_la_tzitzimime_hurt", "special_charro_attack_1", "special_escomgirl_hurt",
    "special_charro_attack_2", "special_rey_grupero",
]


def medir(path: str):
    p = subprocess.run(
        ["ffmpeg", "-hide_banner", "-i", path, "-af",
         "loudnorm=I=%s:TP=%s:LRA=11:print_format=json" % (OBJETIVO, TP), "-f", "null", "-"],
        capture_output=True, text=True)
    m = re.search(r"\{[^{}]*\"input_i\"[^{}]*\}", p.stderr, re.S)
    return json.loads(m.group(0)) if m else None


def main() -> None:
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    hechos = 0
    for name in CANDIDATOS:
        src = os.path.join(SOUNDS, name + ".ogg")
        if not os.path.exists(src):
            print("  %-32s NO EXISTE" % name)
            continue
        datos = medir(src)
        if not datos:
            print("  %-32s no se pudo medir" % name)
            continue
        antes = float(datos["input_i"])
        if abs(antes - OBJETIVO) <= TOL:
            print("  %-32s %6.1f LUFS  ya esta en rango" % (name, antes))
            continue
        if args.dry_run:
            print("  %-32s %6.1f LUFS  -> normalizaria" % (name, antes))
            continue

        tmp = src + ".tmp.ogg"
        # 2a pasada: se le pasan las medidas reales para que la correccion sea LINEAL
        flt = ("loudnorm=I=%s:TP=%s:LRA=11:measured_I=%s:measured_TP=%s:"
               "measured_LRA=%s:measured_thresh=%s:linear=true:print_format=summary"
               % (OBJETIVO, TP, datos["input_i"], datos["input_tp"],
                  datos["input_lra"], datos["input_thresh"]))
        r = subprocess.run(["ffmpeg", "-y", "-v", "error", "-i", src, "-af", flt,
                            "-c:a", "libvorbis", "-q:a", "5", tmp], capture_output=True, text=True)
        if r.returncode != 0 or not os.path.exists(tmp):
            print("  %-32s FALLO ffmpeg: %s" % (name, r.stderr.strip()[:60]))
            continue
        os.replace(tmp, src)
        despues = float(medir(src)["input_i"])
        # el mp3 de repaso debe reflejar lo mismo que oira el juego
        mp3 = os.path.join(REVIEW, name + ".mp3")
        if os.path.exists(mp3):
            subprocess.run(["ffmpeg", "-y", "-v", "error", "-i", src, "-q:a", "4", mp3],
                           capture_output=True)
        print("  %-32s %6.1f  ->  %6.1f LUFS" % (name, antes, despues))
        hechos += 1

    print("\nnormalizados: %d" % hechos)


if __name__ == "__main__":
    main()
