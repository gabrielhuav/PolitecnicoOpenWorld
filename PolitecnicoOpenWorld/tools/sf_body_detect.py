#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Detector de CUERPO compartido (2026-07-21).

Sustituye a la heuristica de "primer grupo de columnas densas", que fallaba justo donde
mas importa: en los cuadros de poder, donde el efecto es MAS GRANDE Y MAS BRILLANTE que
la figura. Con esa regla, en `bonus-1-3` de La Presidenta se elegia la estela morada como
"cuerpo", se anclaba en x=128 y el personaje acababa FUERA del lienzo.

La senal buena: **el personaje PISA EL SUELO y los efectos FLOTAN**. Los cuadros del
pipeline apoyan los pies en y=224, asi que el cuerpo es la masa que llega abajo del todo;
un haz, un aura o una explosion casi nunca tocan esa linea a lo ancho.

Algoritmo:
  1. Se toman las columnas cuyo contenido llega a la franja inferior de la figura.
  2. Se agrupan las contiguas y se elige el grupo con mas masa: ese es el cuerpo.
  3. Si nada toca el suelo (proyectiles sueltos, poses aereas), se cae a la heuristica
     antigua para no romper esos casos.
"""
from __future__ import annotations

import numpy as np
from PIL import Image

SUELO_FRAC = 0.12     # ultimo 12 % de la figura = "esta pisando"
HUECO_MAX = 6         # columnas vacias que se toleran dentro del mismo cuerpo


def _grupos(mask_cols: np.ndarray, hueco: int = HUECO_MAX):
    grupos, ini, vacias = [], None, 0
    for x, on in enumerate(mask_cols):
        if on:
            if ini is None:
                ini = x
            vacias = 0
        elif ini is not None:
            vacias += 1
            if vacias > hueco:
                grupos.append((ini, x - vacias + 1))
                ini, vacias = None, 0
    if ini is not None:
        grupos.append((ini, len(mask_cols)))
    return grupos


def body_box(img):
    """(x0, y0, x1, y1) del CUERPO, ignorando efectos. None si el cuadro esta vacio."""
    a = np.asarray(img.convert("RGBA"))[..., 3] > 8
    if not a.any():
        return None
    ys = np.where(a.any(axis=1))[0]
    y_top, y_bot = int(ys[0]), int(ys[-1])
    alto = y_bot - y_top + 1

    # 1) columnas que llegan al suelo de la figura
    franja = a[max(y_top, y_bot - max(2, int(alto * SUELO_FRAC))): y_bot + 1]
    pisan = franja.any(axis=0)

    grupos = _grupos(pisan)
    if grupos:
        # 2) el grupo con mas pixeles totales, no el mas ancho: una estela larga y fina
        #    puede rozar el suelo, pero pesa mucho menos que un cuerpo.
        x0, x1 = max(grupos, key=lambda g: int(a[:, g[0]:g[1]].sum()))
    else:
        # 3) nada pisa: pose aerea o efecto suelto -> heuristica antigua
        counts = a.sum(axis=0)
        dense = counts >= max(3.0, float(counts.max()) * 0.45)
        gs = _grupos(dense, hueco=1)
        if not gs:
            xs = np.where(a.any(axis=0))[0]
            return int(xs[0]), y_top, int(xs[-1]) + 1, y_bot + 1
        x0, x1 = gs[0]

    ys2 = np.where(a[:, x0:x1].any(axis=1))[0]
    return x0, int(ys2[0]), x1, int(ys2[-1]) + 1


def body_center_x(img) -> float:
    bb = body_box(img)
    if bb is None:
        return img.width / 2.0
    x0, _, x1, _ = bb
    col = (np.asarray(img.convert("RGBA"))[..., 3] > 8)[:, x0:x1].sum(axis=0).astype(float)
    xs = np.arange(x0, x1, dtype=float)
    return float((xs * col).sum() / col.sum()) if col.sum() > 0 else (x0 + x1) / 2.0


def body_height(img) -> int:
    bb = body_box(img)
    return (bb[3] - bb[1]) if bb else img.height
