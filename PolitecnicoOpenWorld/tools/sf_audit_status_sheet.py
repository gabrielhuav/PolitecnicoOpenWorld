#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""HOJA DE ESTADO del audit manual del dueno (2026-07-21).

Dibuja SOLO los cuadros que el dueno senalo a ojo, cada uno con su estado actual:

  CORREGIDO  ya se re-recorto y re-empaqueto; el cuadro que sale aqui es el NUEVO
  PENDIENTE  sigue igual que cuando lo reporto
  REVISAR    el dueno decide (no es un fallo tecnico sino una decision de arte)

Asi el dueno no vuelve a recorrer las 18 hojas completas: mira esta y ve de un vistazo
que se arreglo y que no. Los cuadros salen del ATLAS REAL, no de los intermedios.

La tabla REPORTES se mantiene a mano, en sincronia con
`README for IAS/SF/AUDIT_MANUAL_DUENO.md`.

Uso:
    python tools/sf_audit_status_sheet.py
Salida: tools/_audit_sheets/_ESTADO_CORRECCIONES.png
"""
from __future__ import annotations

import io
import json
import os
import sys

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SF = os.path.join(ROOT, "app", "src", "main", "assets", "STREETFIGHTER")
DATA, IMAGES = os.path.join(SF, "DATA"), os.path.join(SF, "IMAGES")
OUT = os.path.join(ROOT, "tools", "_audit_sheets", "_ESTADO_CORRECCIONES.png")

OK = (120, 230, 140, 255)
PEND = (255, 130, 130, 255)
REV = (255, 205, 100, 255)

# (peleador, [cuadros], estado, nota del dueno / causa)
REPORTES = [
    # ── C1: dos figuras apiladas en hit-face. ARREGLADO en el slicer y re-empaquetado ──
    ("escomboy", ["hit-face-1", "hit-face-2", "hit-face-3", "hit-face-4"], "CORREGIDO",
     "hurtHead L/M/H venian dobles (C1)"),
    ("escomgirl", ["hit-face-1", "hit-face-2", "hit-face-3", "hit-face-4"], "CORREGIDO",
     "hurtHead L/M/H venian dobles (C1)"),
    ("senortienda", ["hit-face-1", "hit-face-2", "hit-face-3", "hit-face-4"], "CORREGIDO",
     "hurtHead L/M/H venian dobles (C1)"),
    ("yoalliehecatl", ["hit-face-1", "hit-face-2", "hit-face-3", "hit-face-4"], "CORREGIDO",
     "hurtHead L/M/H venian dobles (C1)"),
    # ── La Tzitzimime: poderes bonus recortados a la parte valida ──
    ("latzitzimime", ["bonus-4-5"], "CORREGIDO", "bonusPower4 = solo este cuadro"),
    ("latzitzimime", ["bonus-5-4", "bonus-5-5"], "CORREGIDO", "bonusPower5 = solo estos dos"),
    # ── C2: cortes que atraviesan el efecto. PENDIENTE ──
    ("lallorona", ["super-4", "super-5", "super-6"], "PENDIENTE", "fatality y superArt (C2)"),
    ("lapresidenta", ["super-4", "super-5"], "PENDIENTE", "fatality y superArt (C2)"),
    ("paparazzi1", ["super-6"], "PENDIENTE", "falta recorte a la DERECHA, minimo (C2)"),
    ("paparazzi5", ["super-6"], "PENDIENTE", "falta recorte ANTES, muy leve (C2)"),
    ("policiacdmxhombre", ["super-6"], "PENDIENTE", "fatality y superArt (C2)"),
    ("reygrupero", ["super-6", "super-7"], "REVISAR",
     "los 2 completan UN asset: deberian ir juntos"),
    ("paramedicocruzroja", ["special-4", "super-6"], "REVISAR",
     "super-6 es onda pura; encimar con el anterior"),
    # ── C3: el poder se sale del lienzo por la derecha. PENDIENTE ──
    ("lapresidenta", ["bonus-7-1", "bonus-8-1", "bonus-9-1", "bonus-10-1"], "PENDIENTE",
     "cortados por la DERECHA (C3)"),
    ("lapresidenta", ["bonus-1-1", "bonus-3-1"], "REVISAR",
     "solo anima el 1er cuadro; el resto pierde el poder"),
    ("lapresidenta", ["bonus-5-1"], "REVISAR",
     "bien recortado pero el poder sale incompleto: sin aislar"),
]

COLORES = {"CORREGIDO": OK, "PENDIENTE": PEND, "REVISAR": REV}


def main() -> None:
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
    cache: dict[str, tuple] = {}

    def load(char):
        if char not in cache:
            d = json.load(open(os.path.join(DATA, char + ".json"), encoding="utf-8"))
            name = next(f for f in os.listdir(IMAGES)
                        if os.path.splitext(f)[0].lower() == char.lower())
            cache[char] = (d, Image.open(os.path.join(IMAGES, name)).convert("RGBA"))
        return cache[char]

    CELL, LBL = 150, 330
    cols = max(len(f) for _, f, _, _ in REPORTES)
    W = LBL + cols * CELL
    H = len(REPORTES) * (CELL + 8) + 40
    cv = Image.new("RGBA", (W, H), (28, 28, 42, 255))
    d = ImageDraw.Draw(cv)
    d.text((10, 12), "ESTADO DE LAS CORRECCIONES DEL AUDIT MANUAL  -  "
                     "verde=CORREGIDO  rojo=PENDIENTE  ambar=REVISAR",
           fill=(235, 235, 245, 255))

    faltan = []
    for r, (char, frames, estado, nota) in enumerate(REPORTES):
        y = 40 + r * (CELL + 8)
        col = COLORES[estado]
        d.rectangle([0, y, W - 1, y + CELL + 6], outline=(70, 70, 100, 255))
        d.rectangle([0, y, 6, y + CELL + 6], fill=col)
        d.text((14, y + 8), char, fill=(255, 255, 255, 255))
        d.text((14, y + 26), estado, fill=col)
        d.text((14, y + 46), nota[:44], fill=(180, 180, 200, 255))
        data, at = load(char)
        for c, k in enumerate(frames):
            x = LBL + c * CELL
            if k not in data["frames"]:
                d.text((x + 8, y + CELL // 2), "NO EXISTE", fill=PEND)
                faltan.append((char, k))
                continue
            fx, fy, fw, fh = data["frames"][k]["src"]
            t = at.crop((fx, fy, fx + fw, fy + fh)).resize(
                (CELL - 8, CELL - 8), Image.Resampling.LANCZOS)
            cv.alpha_composite(t, (x + 4, y + 4))
            d.text((x + 6, y + CELL - 12), k, fill=(150, 210, 255, 255))

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    cv.convert("RGB").save(OUT)
    n = {e: sum(1 for _, _, s, _ in REPORTES if s == e) for e in COLORES}
    print("filas: %d  (CORREGIDO %d / PENDIENTE %d / REVISAR %d)"
          % (len(REPORTES), n["CORREGIDO"], n["PENDIENTE"], n["REVISAR"]))
    if faltan:
        print("cuadros inexistentes:", faltan)
    print(OUT)


if __name__ == "__main__":
    main()
