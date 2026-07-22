#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""CAJAS DE CABEZA Y CUERPO de TODOS los cuadros (2026-07-21).

Para cada cuadro empaquetado dibuja dos rectangulos tipo hitbox:

  AMARILLO = CABEZA   VERDE = CUERPO   CIAN = x=128 (lo que asume el motor)

Sirve para comprobar de un vistazo que el motor "encuentra" bien al personaje incluso en
poses con poderes exagerados, donde el efecto es mas grande que la figura y el centro de
la caja completa no tiene nada que ver con donde esta el cuerpo.

Como se deciden las cajas (sin reconocer anatomia, para que valga en robots y espectros):
  CUERPO  el primer grupo de columnas con soporte vertical alto — la masa solida. Los
          haces, auras y destellos forman grupos posteriores y quedan fuera.
  CABEZA  dentro del cuerpo, desde su fila mas alta hasta que el ancho de la silueta se
          ensancha de golpe: ese salto son los hombros.

Uso:
    python tools/sf_audit_hitboxes.py                # los 18
    python tools/sf_audit_hitboxes.py --only lallorona
Salida: tools/_audit_hitboxes/<char>.png
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

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SF = os.path.join(ROOT, "app", "src", "main", "assets", "STREETFIGHTER")
DATA, IMAGES = os.path.join(SF, "DATA"), os.path.join(SF, "IMAGES")
OUT = os.path.join(ROOT, "tools", "_audit_hitboxes")
SKIP = {"sf_template", "special_phrases", "voice_phrases", "combos"}
CX, FEET_Y = 128, 224


def body_box(alpha: np.ndarray):
    """Caja del CUERPO: primer grupo de columnas densas (ignora el efecto)."""
    vis = alpha > 8
    if not vis.any():
        return None
    counts = vis.sum(axis=0)
    dense = counts >= max(3.0, float(counts.max()) * 0.45)
    grupos, ini = [], None
    for x, on in enumerate(dense):
        if on and ini is None:
            ini = x
        if ini is not None and (not on or x == len(dense) - 1):
            fin = x if not on else x + 1
            if fin - ini >= 2:
                grupos.append((ini, fin))
            ini = None
    if not grupos:
        xs = np.where(vis.any(axis=0))[0]
        grupos = [(int(xs[0]), int(xs[-1]) + 1)]
    x0, x1 = grupos[0]
    franja = vis[:, x0:x1]
    ys = np.where(franja.any(axis=1))[0]
    if not len(ys):
        return None
    return x0, int(ys[0]), x1, int(ys[-1]) + 1


def head_box(alpha: np.ndarray, body):
    """Caja de la CABEZA: desde lo alto del cuerpo hasta que la silueta se ensancha."""
    x0, y0, x1, y1 = body
    vis = alpha[:, x0:x1] > 8
    anchos = []
    for y in range(y0, min(y1, y0 + int((y1 - y0) * 0.6))):
        xs = np.where(vis[y])[0]
        anchos.append(int(xs[-1] - xs[0] + 1) if len(xs) else 0)
    if not anchos:
        return None
    base = np.median([a for a in anchos[:max(3, len(anchos) // 6)] if a > 0] or [1])
    corte = len(anchos)
    for i, a in enumerate(anchos):
        if a > base * 1.6:          # salto de ancho = hombros
            corte = i
            break
    corte = max(corte, 4)
    sub = vis[y0:y0 + corte]
    xs = np.where(sub.any(axis=0))[0]
    if not len(xs):
        return None
    return x0 + int(xs[0]), y0, x0 + int(xs[-1]) + 1, y0 + corte


def main() -> None:
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
    ap = argparse.ArgumentParser()
    ap.add_argument("--only")
    args = ap.parse_args()

    chars = sorted(os.path.basename(p)[:-5] for p in glob.glob(os.path.join(DATA, "*.json")))
    chars = [c for c in chars if c not in SKIP and (not args.only or c == args.only)]
    os.makedirs(OUT, exist_ok=True)

    for char in chars:
        data = json.load(open(os.path.join(DATA, char + ".json"), encoding="utf-8"))
        nm = next(f for f in os.listdir(IMAGES)
                  if os.path.splitext(f)[0].lower() == char.lower())
        at = Image.open(os.path.join(IMAGES, nm)).convert("RGBA")
        keys = sorted(data["frames"])

        C, cols = 140, 14
        rows_n = (len(keys) + cols - 1) // cols
        cv = Image.new("RGBA", (C * cols, (C + 16) * rows_n), (24, 24, 36, 255))
        d = ImageDraw.Draw(cv)
        sin_cuerpo = []
        for i, k in enumerate(keys):
            x, y, w, h = data["frames"][k]["src"]
            cell = at.crop((x, y, x + w, y + h))
            a = np.asarray(cell)[..., 3]
            px, py = (i % cols) * C, (i // cols) * (C + 16)
            cv.alpha_composite(cell.resize((C, C), Image.Resampling.NEAREST), (px, py + 16))
            s = C / 256.0
            d.line([px + CX * s, py + 16, px + CX * s, py + 16 + C], fill=(70, 190, 230, 120))
            bb = body_box(a)
            if bb is None:
                sin_cuerpo.append(k)
            else:
                bx0, by0, bx1, by1 = bb
                d.rectangle([px + bx0 * s, py + 16 + by0 * s,
                             px + bx1 * s, py + 16 + by1 * s], outline=(110, 235, 130, 255))
                hb = head_box(a, bb)
                if hb:
                    hx0, hy0, hx1, hy1 = hb
                    d.rectangle([px + hx0 * s, py + 16 + hy0 * s,
                                 px + hx1 * s, py + 16 + hy1 * s],
                                outline=(255, 225, 90, 255))
            d.text((px + 3, py + 3), k[:19], fill=(200, 200, 225, 255))
        p = os.path.join(OUT, char + ".png")
        cv.convert("RGB").save(p)
        print("%-24s %3d cuadros%s" % (
            char, len(keys),
            "   SIN CUERPO: %s" % ", ".join(sin_cuerpo[:4]) if sin_cuerpo else ""))

    print("\nAMARILLO=cabeza  VERDE=cuerpo  CIAN=x=128 (referencia del motor)")
    print("carpeta: %s" % OUT)


if __name__ == "__main__":
    main()
