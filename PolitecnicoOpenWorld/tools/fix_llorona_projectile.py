#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""FIX del PROYECTIL de La Llorona (2026-07-21).

Sintoma: su poder especial "se lanzaba a ella misma rotada". Causa: su hoja 12
(`LaLlorona_12_SpecialHeavy_Extra.png`) NO tiene el formato estandar de 2 grupos; es una
rejilla 4x3 (fila 1 = ella lanzando, fila 2 = ella flotando, fila 3 = los 4 EFECTOS).
`slice_sf_chroma_sheets.py` partio los grupos por bandas y metio 2 cuadros de su CUERPO en
`proj-*`, asi que el motor dibujaba su figura como proyectil.

Este script extrae SOLO la fila 3 y escribe los 5 cuadros de proyectil en el GEN de
La Llorona (proyectiles centrados en 128,128; ~55 px como los demas personajes):

    proj-fly-1 = cadena        (viaja)
    proj-fly-2 = zarpazo       (viaja, alterna con la cadena)
    proj-hit-1 = orbe          (impacto)
    proj-hit-2 = zarpazo       (impacto)
    proj-hit-3 = estela        (disipacion)

USO:
    python tools/fix_llorona_projectile.py --gen <raiz_GEN>
    python tools/pack_sf_character.py lallorona LaLlorona --gen <raiz_GEN>
"""
from __future__ import annotations

import argparse
import os

from PIL import Image

import numpy as np
from scipy import ndimage

from slice_sf_chroma_sheets import place_sf

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SHEET = os.path.join(os.path.dirname(REPO), "newSFAssets", "LaLlorona",
                     "LaLlorona_12_SpecialHeavy_Extra.png")
TARGET_H = 55.0     # alto objetivo del efecto (los de los demas personajes miden 52-56 px)
FX_BAND = 0.66      # los efectos viven en el ultimo tercio de la hoja
MIN_FX_H = 100      # descarta tiras finas (bajos de vestido de la fila 2, subrayados)

# indice del efecto en la fila (izq->der) -> cuadro de proyectil.
# Fila de efectos: 0 = estela, 1 = zarpazo, 2 = cadena, 3 = orbe.
MAPPING = {
    "proj-fly-1": 2,   # cadena (viaja)
    "proj-fly-2": 1,   # zarpazo (viaja, alterna con la cadena)
    "proj-hit-1": 3,   # orbe (impacto)
    "proj-hit-2": 1,   # zarpazo (impacto)
    "proj-hit-3": 0,   # estela (disipacion)
}


def effect_cells(image: Image.Image) -> list[Image.Image]:
    """Recorta los efectos de la ultima fila por SILUETA (no por rejilla fija).

    La hoja NO reparte los cuadros en columnas regulares: los 4 efectos estan
    agrupados, asi que un corte en 4 columnas iguales mezclaba vecinos.
    """
    a = np.asarray(image).astype(int)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    # El croma de esta hoja viene de un JPG (sucio): dominancia de canal, no umbral plano.
    mask = ~((g > 100) & (r < 175) & (b < 175) & (g > r + 25) & (g > b + 25))
    mask[:int(a.shape[0] * FX_BAND), :] = False
    closed = ndimage.binary_closing(mask, structure=np.ones((5, 5)))
    lbl, _ = ndimage.label(closed, structure=np.ones((3, 3)))
    boxes = []
    for i, sl in enumerate(ndimage.find_objects(lbl)):
        if sl is None:
            continue
        y0, y1, x0, x1 = sl[0].start, sl[0].stop, sl[1].start, sl[1].stop
        if (y1 - y0) < MIN_FX_H or int((lbl[sl] == i + 1).sum()) < 2000:
            continue
        boxes.append((x0, y0, x1, y1))
    boxes.sort()
    cells = []
    for x0, y0, x1, y1 in boxes:
        cell = image.crop((x0, y0, x1, y1)).convert("RGBA")
        px = np.asarray(cell).copy()
        rr, gg, bb = (px[..., i].astype(int) for i in range(3))
        chroma = (gg > 100) & (rr < 175) & (bb < 175) & (gg > rr + 25) & (gg > bb + 25)
        px[..., 3] = np.where(chroma, 0, 255).astype(np.uint8)
        out = Image.fromarray(px, "RGBA")
        bb2 = out.getbbox()
        cells.append(out.crop(bb2) if bb2 else out)
    return cells


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--gen", required=True, help="Raiz GEN (contiene lallorona/)")
    ap.add_argument("--sheet", default=SHEET)
    args = ap.parse_args()

    out_dir = os.path.join(args.gen, "lallorona")
    if not os.path.isdir(out_dir):
        raise SystemExit(f"No existe {out_dir}")
    image = Image.open(args.sheet).convert("RGB")
    cells = effect_cells(image)
    if len(cells) != 4:
        raise SystemExit(f"Esperaba 4 efectos en la ultima fila y encontre {len(cells)}; "
                         f"revisa la hoja {os.path.basename(args.sheet)}")

    for key, idx in MAPPING.items():
        cell = cells[idx]
        scale = TARGET_H / cell.height
        # center=True: los proyectiles se anclan en (128,128), no en los pies
        place_sf(cell, scale, center=True).save(os.path.join(out_dir, f"{key}.png"))
        print(f"{key}: efecto #{idx} de la fila, {cell.size} x{scale:.3f}")
    print(f"\nListo. Ahora: python tools/pack_sf_character.py lallorona LaLlorona --gen {args.gen}")


if __name__ == "__main__":
    main()
