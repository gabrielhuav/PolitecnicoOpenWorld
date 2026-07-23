#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Convierte los atlas DEDICADOS de peleador de PNG a WebP LOSSLESS (2026-07-21).

Motivo: con las hojas 20-29 los atlas crecieron a ~102 MB en disco y el AAB de release
tenia solo ~40 MB de margen bajo el limite de 500 MB de Play. WebP lossless conserva los
PIXELES EXACTOS (se verifica con hash RGBA) y ahorra ~25 %.

⚠️ Solo toca los atlas de PELEADOR (los que referencia SfFighterId.spriteAsset). NO toca
fondos (ya son webp), HUD, sombras ni splashes.

USO:
    python tools/atlas_to_webp.py            # convierte y verifica
    python tools/atlas_to_webp.py --dry-run  # solo reporta el ahorro
"""
from __future__ import annotations

import argparse
import hashlib
import os

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
IMAGES = os.path.join(ROOT, "app", "src", "main", "assets", "STREETFIGHTER", "IMAGES")

# Atlas de peleador: los .png que NO son fondo/HUD/decorado.
SKIP_PREFIXES = ("fondo_", "sf_hud", "sf_decals", "shadow", "splash")


def is_fighter_atlas(name: str) -> bool:
    if not name.endswith(".png"):
        return False
    lower = name.lower()
    return not any(lower.startswith(p) for p in SKIP_PREFIXES)


def visual_hash(path: str) -> str:
    """Hash de lo que se VE: alfa exacto + RGB solo donde el pixel es visible.

    WebP lossless NORMALIZA el RGB bajo los pixeles totalmente transparentes (alfa 0), asi
    que comparar el buffer RGBA crudo da falso negativo aunque la imagen sea identica. Lo
    que debe coincidir es el canal alfa y el color de los pixeles que se pintan.
    """
    a = np.asarray(Image.open(path).convert("RGBA")).copy()
    alpha = a[..., 3]
    a[alpha == 0] = 0            # ignora el RGB invisible (WebP lo pone a cero)
    return hashlib.sha256(a.tobytes()).hexdigest()


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    total_before = total_after = 0
    converted, failed = [], []
    for name in sorted(os.listdir(IMAGES)):
        if not is_fighter_atlas(name):
            continue
        src = os.path.join(IMAGES, name)
        dst = os.path.join(IMAGES, name[:-4] + ".webp")
        before = os.path.getsize(src)
        img = Image.open(src).convert("RGBA")
        if args.dry_run:
            tmp = dst + ".tmp"
            img.save(tmp, "WEBP", lossless=True, quality=100, method=6)
            after = os.path.getsize(tmp)
            os.remove(tmp)
        else:
            img.save(dst, "WEBP", lossless=True, quality=100, method=6)
            # Verificacion OBLIGATORIA: los pixeles deben ser identicos, no "parecidos".
            if visual_hash(src) != visual_hash(dst):
                failed.append(name)
                os.remove(dst)
                continue
            after = os.path.getsize(dst)
            os.remove(src)
            converted.append(name)
        total_before += before
        total_after += after
        print(f"  {name:34s} {before / 1048576:6.2f} MB -> {after / 1048576:6.2f} MB")

    print(f"\nTOTAL {total_before / 1048576:.1f} MB -> {total_after / 1048576:.1f} MB "
          f"(ahorro {(total_before - total_after) / 1048576:.1f} MB)")
    if failed:
        raise SystemExit(f"HASH DISTINTO en: {', '.join(failed)} (no se convirtieron)")
    if converted:
        print(f"Convertidos {len(converted)} atlas. Actualiza SfFighterId.spriteAsset a .webp")


if __name__ == "__main__":
    main()
