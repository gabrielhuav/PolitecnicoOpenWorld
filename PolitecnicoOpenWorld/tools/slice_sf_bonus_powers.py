#!/usr/bin/env python3
"""Recorta filas croma 5x3 de poderes extra Grok a cuadros SF 256x256.

Cada invocacion toma filas consecutivas de una hoja 1360x768 y las guarda como
``bonus-<poder>-<cuadro>.png``. La escala es unica por fila y se fija con el primer
cuadro (personaje base = 100 px), de modo que efectos y metamorfosis conservan su
crecimiento real sin cambiar el zoom corporal cuadro por cuadro.
"""
import argparse
import os
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage

from slice_sf_chroma_sheets import place_sf


def cut_cell(image, column, row, sheet_rows):
    width, height = image.size
    x0 = round(column * width / 5)
    x1 = round((column + 1) * width / 5)
    y0 = round(row * height / sheet_rows)
    y1 = round((row + 1) * height / sheet_rows)
    rgb = np.asarray(image)[y0:y1, x0:x1].copy()
    r, g, b = (rgb[..., i].astype(int) for i in range(3))
    mask = ~((g > 150) & (r < 165) & (b < 165) & (g > r + 25) & (g > b + 25))

    # El JPG agrega ruido y cada fila trae un titulo amarillo. Conserva particulas
    # de efectos, pero elimina componentes diminutos y texto horizontal en la cabecera.
    labels, _ = ndimage.label(mask, structure=np.ones((3, 3)))
    clean = np.zeros_like(mask)
    for index, sl in enumerate(ndimage.find_objects(labels), start=1):
        if sl is None:
            continue
        yy0, yy1, xx0, xx1 = sl[0].start, sl[0].stop, sl[1].start, sl[1].stop
        area = int((labels[sl] == index).sum())
        component_h = yy1 - yy0
        component_w = xx1 - xx0
        if area < 35:
            continue
        if yy0 < 48 and component_h < 28:
            continue
        if (xx0 < 5 or xx1 > mask.shape[1] - 5) and component_w < 35:
            continue
        clean[sl] |= labels[sl] == index

    edge = clean & ~ndimage.binary_erosion(clean, iterations=2)
    spill = edge & (g > r + 30) & (g > b + 30)
    rgb[..., 1] = np.where(spill, (r + b) // 2, g).astype(np.uint8)
    rgba = Image.fromarray(np.dstack([rgb, (clean * 255).astype(np.uint8)]), "RGBA")
    bbox = rgba.getbbox()
    return rgba.crop(bbox) if bbox else rgba


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("sheet")
    parser.add_argument("char")
    parser.add_argument("start_power", type=int)
    parser.add_argument("--rows", type=int, default=3)
    parser.add_argument("--sheet-rows", type=int, choices=(2, 3), default=3)
    parser.add_argument("--gen", required=True)
    args = parser.parse_args()

    image = Image.open(args.sheet).convert("RGB")
    if image.size != (1360, 768):
        raise SystemExit(f"Hoja bonus inesperada {image.size}; se requiere 1360x768")
    if args.rows < 1 or args.rows > 3:
        raise SystemExit("--rows debe estar entre 1 y 3")

    output = Path(args.gen) / args.char
    output.mkdir(parents=True, exist_ok=True)
    for row in range(args.rows):
        frames = [cut_cell(image, column, row, args.sheet_rows) for column in range(5)]
        if any(frame.getbbox() is None for frame in frames):
            raise SystemExit(f"Fila {row + 1}: cuadro vacio; revisar hoja")
        base_height = frames[0].height
        scale = 100.0 / base_height
        # No permitas que un efecto ancho se corte; reduce la fila completa si hace falta.
        max_w = max(frame.width * scale for frame in frames)
        max_h = max(frame.height * scale for frame in frames)
        scale *= min(1.0, 244.0 / max_w, 220.0 / max_h)
        power = args.start_power + row
        for frame_index, frame in enumerate(frames, start=1):
            placed = place_sf(frame, scale, anchor_body=True)
            placed.save(output / f"bonus-{power}-{frame_index}.png")
        print(f"bonusPower{power}: 5 cuadros, escala fija x{scale:.4f}")


if __name__ == "__main__":
    main()
