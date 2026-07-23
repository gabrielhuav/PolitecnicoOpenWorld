#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Parte una TIRA croma en poses sueltas, quitando los rotulos del generador (2026-07-21).

Pensado para las tiras de metamorfosis, que llegan como una sola imagen con las poses en
fila y rotulos "STEP N" en amarillo encima del croma. Hace tres cosas:

  1. Borra los rotulos amarillos (los repinta de croma) ANTES de detectar nada: si no, se
     cuentan como figura y salen dentro del cuadro.
  2. Separa las poses por columnas de croma vacias.
  3. Las guarda RENUMERADAS en el orden que se pida, para que el dueno audite el orden
     mirando los archivos en vez de descifrar los rotulos originales (que venian salteados:
     1, 3, 4, 9, 5...).

Uso:
    python tools/sf_split_strip.py "<tira.png>" <prefijo> [--derecha-a-izquierda]
Salida: tools/_para_corregir/<prefijo>/<prefijo>_NN.png
"""
from __future__ import annotations

import argparse
import io
import os
import sys

import numpy as np
from PIL import Image
from scipy import ndimage

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_BASE = os.path.join(ROOT, "tools", "_para_corregir")


def sin_rotulos(a: np.ndarray, banda: float = 0.42) -> np.ndarray:
    """Repinta de croma el texto amarillo del generador.

    ⚠️ SOLO en la banda superior. Aplicarlo a toda la imagen se comia la PIEL DE LA CARA.

    Umbral MEDIDO sobre las tiras reales, no estimado:
        rotulo -> g-b mediana 99, b mediana  13
        piel   -> g-b mediana  9, b mediana 171
    `g-b` los separa por un factor de 10. NO se puede exigir `r` alto: el rotulo lleva
    contorno oscuro y su r va de 0 a 253 (mediana 31), que fue justo por lo que el primer
    intento dejaba los rotulos puestos.
    """
    r, g, b = a[..., 0].astype(int), a[..., 1].astype(int), a[..., 2].astype(int)
    amarillo = (g - b > 60) & (b < 120) & (g > 90)
    alto = np.zeros(amarillo.shape, dtype=bool)
    alto[: int(amarillo.shape[0] * banda)] = True
    amarillo &= alto
    if amarillo.any():
        amarillo = ndimage.binary_dilation(amarillo, iterations=4)
        a = a.copy()
        a[amarillo] = [0, 255, 0]
    return a


def main() -> None:
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
    ap = argparse.ArgumentParser()
    ap.add_argument("tira")
    ap.add_argument("prefijo")
    ap.add_argument("--derecha-a-izquierda", action="store_true",
                    help="numera desde la DERECHA (metamorfosis inversa)")
    ap.add_argument("--min-ancho", type=int, default=40)
    args = ap.parse_args()

    a = sin_rotulos(np.asarray(Image.open(args.tira).convert("RGB")))
    r, g, b = a[..., 0].astype(int), a[..., 1].astype(int), a[..., 2].astype(int)
    figura = ~((g > 150) & (g > r + 45) & (g > b + 45))
    figura = ndimage.binary_closing(figura, structure=np.ones((7, 7)))

    # columnas con algo de figura -> cada racha es una pose
    col = figura.sum(axis=0) > 3
    col = ndimage.binary_closing(col, structure=np.ones(25, dtype=bool))
    lbl, n = ndimage.label(col)
    tramos = []
    for sl in ndimage.find_objects(lbl):
        x0, x1 = sl[0].start, sl[0].stop
        if x1 - x0 >= args.min_ancho:
            tramos.append((x0, x1))
    if args.derecha_a_izquierda:
        tramos = list(reversed(tramos))

    out_dir = os.path.join(OUT_BASE, args.prefijo)
    os.makedirs(out_dir, exist_ok=True)
    limpia = Image.fromarray(a.astype(np.uint8), "RGB")
    print("poses detectadas: %d   (orden: %s)"
          % (len(tramos), "derecha->izquierda" if args.derecha_a_izquierda else "izquierda->derecha"))
    for i, (x0, x1) in enumerate(tramos, 1):
        franja = figura[:, x0:x1]
        ys = np.where(franja.any(axis=1))[0]
        y0, y1 = (int(ys[0]), int(ys[-1]) + 1) if len(ys) else (0, a.shape[0])
        m = 12
        caja = (max(0, x0 - m), max(0, y0 - m),
                min(a.shape[1], x1 + m), min(a.shape[0], y1 + m))
        p = os.path.join(out_dir, "%s_%02d.png" % (args.prefijo, i))
        limpia.crop(caja).save(p)
        print("  %02d  x=%4d..%-4d  %dx%d" % (i, x0, x1, caja[2] - caja[0], caja[3] - caja[1]))
    print("\ncarpeta: %s" % out_dir)


if __name__ == "__main__":
    main()
