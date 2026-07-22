#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""AUDIT DE ANCLAJE: ¿donde cae el CUERPO y la CABEZA de cada cuadro? (2026-07-21)

El motor dibuja cada cuadro con su origen en (128, 224). Si el CUERPO del personaje no
esta sobre esa vertical, en pelea se ve que el peleador "salta" de lado al lanzar un
poder — aunque el cuadro por si solo se vea perfecto. Esta herramienta lo hace visible.

Sobre cada cuadro pinta:
  - linea CIAN vertical en x=128  -> donde el motor cree que esta el personaje
  - linea ROJA vertical            -> donde esta de verdad el centro del CUERPO
    (dense_body_center_x: primer grupo denso de columnas, ignorando el efecto)
  - linea VERDE horizontal en y=224 -> la linea de pies del contrato
  - marca AMARILLA                  -> parte alta de la cabeza detectada
El desfase cuerpo-vs-centro se rotula en px. Mas de ~12 px se ve en el motor.

Uso:
    python tools/sf_audit_anchors.py                 # todos, solo cuadros desviados
    python tools/sf_audit_anchors.py --only lallorona --todos
Salida: tools/_audit_anclajes/<char>.png
"""
from __future__ import annotations

import argparse
import glob
import io
import json
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from slice_sf_chroma_sheets import dense_body_center_x  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SF = os.path.join(ROOT, "app", "src", "main", "assets", "STREETFIGHTER")
DATA, IMAGES = os.path.join(SF, "DATA"), os.path.join(SF, "IMAGES")
OUT = os.path.join(ROOT, "tools", "_audit_anclajes")
SKIP = {"sf_template", "special_phrases", "voice_phrases", "combos"}
CX, FEET_Y = 128, 224
TOLERANCIA = 12          # px de desfase que ya se nota en el motor
CENTRADOS = ("proj-",)   # estos van centrados en (128,128), no sobre la linea de pies


def cabeza_de(alpha: np.ndarray, bx0: int, bx1: int) -> int | None:
    """Fila mas alta con pixeles dentro de la franja del cuerpo."""
    franja = alpha[:, max(0, bx0):max(1, bx1)]
    ys = np.where(franja.any(axis=1))[0]
    return int(ys[0]) if len(ys) else None


def main() -> None:
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
    ap = argparse.ArgumentParser()
    ap.add_argument("--only")
    ap.add_argument("--todos", action="store_true",
                    help="dibuja TODOS los cuadros, no solo los desviados")
    args = ap.parse_args()

    chars = sorted(os.path.basename(p)[:-5] for p in glob.glob(os.path.join(DATA, "*.json")))
    chars = [c for c in chars if c not in SKIP and (not args.only or c == args.only)]
    os.makedirs(OUT, exist_ok=True)

    resumen = []
    for char in chars:
        data = json.load(open(os.path.join(DATA, char + ".json"), encoding="utf-8"))
        nm = next(f for f in os.listdir(IMAGES)
                  if os.path.splitext(f)[0].lower() == char.lower())
        at = Image.open(os.path.join(IMAGES, nm)).convert("RGBA")

        filas = []
        for key, meta in sorted(data["frames"].items()):
            if key.startswith(CENTRADOS):
                continue
            x, y, w, h = meta["src"]
            cell = at.crop((x, y, x + w, y + h))
            alpha = np.asarray(cell)[..., 3]
            if not (alpha > 8).any():
                continue
            bcx = dense_body_center_x(cell)
            desfase = bcx - CX
            if not args.todos and abs(desfase) <= TOLERANCIA:
                continue
            filas.append((key, cell, bcx, desfase))

        if not filas:
            resumen.append((char, 0, len(data["frames"])))
            continue

        C, cols = 190, 6
        rows_n = (len(filas) + cols - 1) // cols
        cv = Image.new("RGBA", (C * cols, (C + 30) * rows_n), (26, 26, 40, 255))
        d = ImageDraw.Draw(cv)
        for i, (key, cell, bcx, desfase) in enumerate(filas):
            cxp, cyp = (i % cols) * C, (i // cols) * (C + 30)
            t = cell.resize((C, C), Image.Resampling.NEAREST)
            cv.alpha_composite(t, (cxp, cyp + 30))
            k = C / 256.0
            # referencia del motor
            d.line([cxp + CX * k, cyp + 30, cxp + CX * k, cyp + 30 + C], fill=(90, 220, 255, 255))
            d.line([cxp, cyp + 30 + FEET_Y * k, cxp + C, cyp + 30 + FEET_Y * k],
                   fill=(110, 230, 130, 255))
            # cuerpo real
            d.line([cxp + bcx * k, cyp + 30, cxp + bcx * k, cyp + 30 + C], fill=(255, 80, 80, 255))
            a = np.asarray(cell)[..., 3] > 8
            top = cabeza_de(a, int(bcx) - 20, int(bcx) + 20)
            if top is not None:
                d.line([cxp + bcx * k - 10, cyp + 30 + top * k,
                        cxp + bcx * k + 10, cyp + 30 + top * k], fill=(255, 230, 90, 255))
            d.text((cxp + 5, cyp + 4), key[:24], fill=(210, 210, 235, 255))
            d.text((cxp + 5, cyp + 17), "cuerpo %+d px" % round(desfase),
                   fill=(255, 120, 120, 255))
        p = os.path.join(OUT, char + ".png")
        cv.convert("RGB").save(p)
        resumen.append((char, len(filas), len(data["frames"])))

    print("cuadros con el CUERPO fuera de x=128 (tolerancia %d px)\n" % TOLERANCIA)
    total = 0
    for char, n, tot in sorted(resumen, key=lambda r: -r[1]):
        total += n
        if n:
            print("  %-24s %3d de %3d   -> _audit_anclajes/%s.png" % (char, n, tot, char))
        else:
            print("  %-24s   0 de %3d   OK" % (char, tot))
    print("\ntotal desviados: %d" % total)
    print("carpeta: %s" % OUT)


if __name__ == "__main__":
    main()
