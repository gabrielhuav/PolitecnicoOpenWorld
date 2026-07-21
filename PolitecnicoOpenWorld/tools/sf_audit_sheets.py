#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""HOJAS DE AUDITORIA de los assets YA IMPLEMENTADOS (2026-07-21).

Genera, para que el dueno revise de un vistazo lo que de verdad quedo dentro del juego:

  1. `<char>_TODO.png`      : UNA imagen por personaje con TODAS sus animaciones (una fila
                              por animacion, todos sus cuadros en orden). Es el inventario
                              real: si algo se ve mal aqui, se ve mal en el juego.
  2. `_FATALITIES.png`      : la secuencia del fatality de los 18 personajes, una fila cada
                              uno, para juzgar si la cinematica "sale bien".
  3. `_RESUMEN.png`         : un cuadro por personaje con su idle, para ver el roster.

Todo sale de los ATLAS y JSON REALES de `assets/STREETFIGHTER/` (no de los recortes
intermedios), asi que refleja exactamente lo que carga el juego.

USO:
    python tools/sf_audit_sheets.py                 # todos
    python tools/sf_audit_sheets.py --only prankedy
Salida: tools/_audit_sheets/
"""
from __future__ import annotations

import argparse
import glob
import json
import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SF = os.path.join(ROOT, "app", "src", "main", "assets", "STREETFIGHTER")
DATA, IMAGES = os.path.join(SF, "DATA"), os.path.join(SF, "IMAGES")
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "_audit_sheets")
SKIP = {"sf_template", "special_phrases", "voice_phrases", "combos"}
CELL, LABEL_W = 112, 150


def load(char: str):
    data = json.load(open(os.path.join(DATA, f"{char}.json"), encoding="utf-8"))
    atlas = None
    for ext in (".webp", ".png"):
        for f in os.listdir(IMAGES):
            if f.lower() == char.lower() + ext:
                atlas = Image.open(os.path.join(IMAGES, f)).convert("RGBA")
                break
        if atlas:
            break
    return data, atlas


def frame_img(atlas, data, key, box=CELL):
    fr = data["frames"].get(key)
    if not fr:
        return None
    src = fr[0] if isinstance(fr, list) else fr.get("src")
    x, y, w, h = src
    crop = atlas.crop((x, y, x + w, y + h))
    bb = crop.getbbox()
    if bb:
        crop = crop.crop(bb)
    crop.thumbnail((box - 6, box - 6))
    return crop


def anim_keys(data, anim):
    out = []
    for entry in data["animations"].get(anim, []):
        key = entry[0] if isinstance(entry, list) else entry
        out.append(key)
    return out


def sheet_for_char(char: str, only_anim: str | None = None) -> str | None:
    data, atlas = load(char)
    if atlas is None:
        return None
    anims = sorted(a for a, v in data["animations"].items() if v)
    if only_anim:
        anims = [a for a in anims if a == only_anim]
        if not anims:
            return None
    widest = max((len(anim_keys(data, a)) for a in anims), default=1)
    img = Image.new("RGBA", (LABEL_W + widest * CELL + 10, len(anims) * (CELL + 16) + 10),
                    (22, 22, 26, 255))
    d = ImageDraw.Draw(img)
    for r, anim in enumerate(anims):
        y = 6 + r * (CELL + 16)
        d.text((6, y + CELL // 2), anim[:20], fill=(255, 214, 74, 255))
        for c, key in enumerate(anim_keys(data, anim)):
            crop = frame_img(atlas, data, key)
            x = LABEL_W + c * CELL
            if crop is None:
                d.text((x + 6, y + CELL // 2), "FALTA", fill=(255, 60, 60, 255))
                continue
            img.paste(crop, (x + (CELL - crop.width) // 2, y + (CELL - crop.height) // 2), crop)
            d.text((x + 2, y + CELL + 2), f"{c}:{key}"[:18], fill=(150, 210, 255, 255))
    os.makedirs(OUT, exist_ok=True)
    name = f"{char}_TODO.png" if not only_anim else f"{char}_{only_anim}.png"
    path = os.path.join(OUT, name)
    img.save(path)
    return path


def fatality_sheet(chars: list[str]) -> str:
    rows = []
    for char in chars:
        data, atlas = load(char)
        if atlas is None:
            continue
        keys = anim_keys(data, "fatality")
        if keys:
            rows.append((char, data, atlas, keys))
    widest = max((len(k) for _, _, _, k in rows), default=1)
    img = Image.new("RGBA", (LABEL_W + widest * CELL + 10, len(rows) * (CELL + 16) + 30),
                    (18, 12, 20, 255))
    d = ImageDraw.Draw(img)
    d.text((8, 8), "FATALITY / PODER SUPER ESPECIAL - secuencia por personaje",
           fill=(255, 120, 120, 255))
    for r, (char, data, atlas, keys) in enumerate(rows):
        y = 26 + r * (CELL + 16)
        d.text((6, y + CELL // 2), char[:20], fill=(255, 214, 74, 255))
        for c, key in enumerate(keys):
            crop = frame_img(atlas, data, key)
            x = LABEL_W + c * CELL
            if crop is None:
                continue
            img.paste(crop, (x + (CELL - crop.width) // 2, y + (CELL - crop.height) // 2), crop)
            d.text((x + 2, y + CELL + 2), f"{c}:{key}"[:18], fill=(255, 170, 170, 255))
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, "_FATALITIES.png")
    img.save(path)
    return path


def roster_sheet(chars: list[str]) -> str:
    cols, box = 6, 150
    rows = (len(chars) + cols - 1) // cols
    img = Image.new("RGBA", (cols * box, rows * (box + 18) + 24), (22, 22, 26, 255))
    d = ImageDraw.Draw(img)
    d.text((8, 6), "ROSTER - idle de cada peleador implementado", fill=(255, 214, 74, 255))
    for i, char in enumerate(chars):
        data, atlas = load(char)
        if atlas is None:
            continue
        keys = anim_keys(data, "idle")
        crop = frame_img(atlas, data, keys[0], box) if keys else None
        x, y = (i % cols) * box, 24 + (i // cols) * (box + 18)
        if crop:
            img.paste(crop, (x + (box - crop.width) // 2, y + (box - crop.height) // 2), crop)
        d.text((x + 4, y + box), char[:20], fill=(150, 210, 255, 255))
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, "_RESUMEN.png")
    img.save(path)
    return path


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", default=None)
    args = ap.parse_args()
    chars = sorted(
        os.path.basename(f)[:-5] for f in glob.glob(os.path.join(DATA, "*.json"))
        if os.path.basename(f)[:-5] not in SKIP
    )
    if args.only:
        chars = [c for c in chars if c in args.only.split(",")]
    for char in chars:
        path = sheet_for_char(char)
        print(f"  {char:26s} -> {os.path.basename(path) if path else 'SIN ATLAS'}")
    print(f"\n{fatality_sheet(chars)}")
    print(roster_sheet(chars))
    print(f"\nCarpeta de auditoria: {OUT}")


if __name__ == "__main__":
    main()
