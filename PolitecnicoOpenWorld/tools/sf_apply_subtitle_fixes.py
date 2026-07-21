#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Aplica a voice_phrases.json las correcciones que el dueno escribio en el CSV.

Flujo de trabajo completo:

  1. python tools/sf_voice_subtitle_audit.py
       -> tools/_audio_review/_SUBTITULOS_AUDIT.csv  (+ los .mp3 hermanos)
  2. El dueno escucha cada <clip>.mp3 y rellena SOLO tres columnas del CSV:
       ES_CORREGIDO / EN_CORREGIDO / NOTAS
  3. python tools/sf_apply_subtitle_fixes.py
       -> vuelca ES_CORREGIDO/EN_CORREGIDO a los campos es/en de voice_phrases.json
  4. python tools/sf_voice_subtitle_audit.py   (regenera el CSV ya con lo aplicado)

Reglas:
  - Solo se tocan los clips con algo escrito en ES_CORREGIDO o EN_CORREGIDO.
  - Una celda con el literal `-` marca "este clip NO lleva subtitulo" (grito/SFX)
    y deja el campo VACIO a proposito.
  - Los `draft` de Whisper NUNCA se tocan: son referencia, no se muestran en juego.
  - --dry-run enseña el cambio sin escribir.
"""
from __future__ import annotations

import argparse
import collections
import csv
import io
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
VOICE_JSON = os.path.join(
    ROOT, "app", "src", "main", "assets", "STREETFIGHTER", "DATA", "voice_phrases.json")
DEFAULT_CSV = os.path.join(ROOT, "tools", "_audio_review", "_SUBTITULOS_AUDIT.csv")

NO_SUBTITLE = "-"


def main() -> None:
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
    ap = argparse.ArgumentParser()
    ap.add_argument("csv_path", nargs="?", default=DEFAULT_CSV)
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    data = json.load(open(VOICE_JSON, encoding="utf-8"),
                     object_pairs_hook=collections.OrderedDict)
    clips = data["clips"]

    changes, unknown = [], []
    with open(args.csv_path, encoding="utf-8-sig", newline="") as fh:
        for row in csv.DictReader(fh):
            name = (row.get("clip") or "").strip()
            if not name:
                continue
            if name not in clips:
                # Un clip nuevo en disco todavia sin entrada: lo creamos vacio.
                if (row.get("ES_CORREGIDO") or row.get("EN_CORREGIDO") or "").strip():
                    clips[name] = collections.OrderedDict(es="", en="", draft="")
                else:
                    unknown.append(name)
                    continue
            for col, field in (("ES_CORREGIDO", "es"), ("EN_CORREGIDO", "en")):
                val = (row.get(col) or "").strip()
                if not val:
                    continue
                new = "" if val == NO_SUBTITLE else val
                old = clips[name].get(field, "")
                if new != old:
                    clips[name][field] = new
                    changes.append((name, field, old, new))

    for name, field, old, new in changes:
        print("%-38s %s: %r -> %r" % (name, field, old[:40], new[:40]))
    if unknown:
        print("\n(sin entrada y sin correccion, ignorados: %d)" % len(unknown))

    if not changes:
        print("Sin cambios que aplicar.")
        return
    if args.dry_run:
        print("\n--dry-run: NO se escribio nada. %d cambios pendientes." % len(changes))
        return

    with open(VOICE_JSON, "w", encoding="utf-8") as fh:
        json.dump(data, fh, ensure_ascii=False, indent=2)
        fh.write("\n")

    con_es = sum(1 for v in clips.values() if v.get("es", "").strip())
    con_en = sum(1 for v in clips.values() if v.get("en", "").strip())
    print("\n%d cambios aplicados a voice_phrases.json" % len(changes))
    print("paridad: es=%d  en=%d  (de %d clips)" % (con_es, con_en, len(clips)))
    if con_es != con_en:
        print("AVISO: falta paridad ES/EN; la convencion 09 la exige antes de activar "
              "voiceSubtitlesEnabled.")


if __name__ == "__main__":
    main()
