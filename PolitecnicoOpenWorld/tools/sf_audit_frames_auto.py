#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""AUDIT AUTOMATICO de los cuadros ya empaquetados (2026-07-21).

Complementa `tools/sf_audit_sheets.py`: aquella dibuja las hojas para que las mire un
humano; esta las REVISA sola y senala que celdas merecen esa mirada. Lee el mismo origen
que el juego (`assets/STREETFIGHTER/DATA/*.json` + `IMAGES/*.webp`), no los intermedios.

Detecta la familia de defectos que ya nos ha mordido antes:

  MULTI_FIGURA  varias siluetas separadas en UNA celda de 256 px. Es el fallo de
                `hit-face-1` de La Llorona (4 figuras en un cuadro). Se ignora en poses
                que legitimamente tienen efectos sueltos (proyectil, super, poderes).
  DUPLICADO     dos cuadros de la MISMA animacion con pixeles identicos: el slicer se
                quedo corto y `pick()` repitio uno.
  VACIO         celda sin pixeles visibles.
  ESCALA        un cuadro se sale >25 % de la altura mediana de su animacion (el
                personaje "crece" o "encoge" a media animacion).
  BORDE         la figura toca el borde de la celda: casi siempre viene cortada.

Uso:
    python tools/sf_audit_frames_auto.py [--only lallorona] [--verbose]
"""
from __future__ import annotations

import argparse
import glob
import io
import json
import os
import sys

import numpy as np
from PIL import Image
from scipy import ndimage

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SF = os.path.join(ROOT, "app", "src", "main", "assets", "STREETFIGHTER")
DATA, IMAGES = os.path.join(SF, "DATA"), os.path.join(SF, "IMAGES")
SKIP = {"sf_template", "special_phrases", "voice_phrases", "combos"}

# Poses donde VARIAS siluetas sueltas son arte correcto (efectos, auras, destellos):
# no se marcan como MULTI_FIGURA.
FX_PREFIXES = ("proj-", "super-", "bonus-", "special-", "hadouken")
ALPHA_MIN = 8          # un pixel cuenta como visible a partir de aqui
MIN_BLOB_FRAC = 0.12   # una silueta "de verdad" pesa al menos esto del blob mayor
SCALE_TOL = 0.25

# La altura solo se compara en animaciones DE PIE, donde el personaje no debe cambiar de
# tamano. En ko/thrown/sweep/crouch/dash/jump la silueta cambia A PROPOSITO (tumbado,
# agachado, en el aire), y medirlas producia cientos de avisos falsos.
ERGUIDAS = (
    "idle", "walk", "forwards", "backwards", "run",
    "lightPunch", "medPunch", "heavyPunch", "lightKick", "medKick", "heavyKick",
    "hurtHead", "hurtBody", "stun", "blockHigh", "parryHigh", "taunt",
)


def load(char: str):
    data = json.load(open(os.path.join(DATA, char + ".json"), encoding="utf-8"))
    for ext in (".webp", ".png"):
        for f in os.listdir(IMAGES):
            if f.lower() == char.lower() + ext:
                return data, Image.open(os.path.join(IMAGES, f)).convert("RGBA")
    return data, None


def frame_alpha(atlas: Image.Image, rect: list) -> np.ndarray:
    x, y, w, h = rect
    return np.asarray(atlas.crop((x, y, x + w, y + h)))[..., 3]


def analyze(alpha: np.ndarray) -> dict:
    vis = alpha > ALPHA_MIN
    n = int(vis.sum())
    if n == 0:
        return {"empty": True, "blobs": 0, "h": 0, "w": 0, "edge": False}
    lbl, cnt = ndimage.label(vis, structure=np.ones((3, 3)))
    sizes = ndimage.sum(vis, lbl, range(1, cnt + 1))
    big = int((sizes >= sizes.max() * MIN_BLOB_FRAC).sum()) if cnt else 0
    ys, xs = np.where(vis)
    H, W = vis.shape
    edge = bool(xs.min() <= 1 or xs.max() >= W - 2 or ys.min() <= 1 or ys.max() >= H - 2)
    return {"empty": False, "blobs": big, "h": int(ys.max() - ys.min() + 1),
            "w": int(xs.max() - xs.min() + 1), "edge": edge}


def audit_char(char: str, verbose: bool) -> list:
    data, atlas = load(char)
    if atlas is None:
        return ["%s: NO se encontro el atlas" % char]
    frames, anims = data["frames"], data.get("animations", {})

    stats, digest = {}, {}
    for key, meta in frames.items():
        a = frame_alpha(atlas, meta["src"])
        stats[key] = analyze(a)
        digest[key] = hash(a.tobytes())

    issues = []
    for key, st in sorted(stats.items()):
        if st["empty"]:
            issues.append(("VACIO", char, key, "celda sin pixeles visibles"))
            continue
        if st["blobs"] >= 2 and not key.startswith(FX_PREFIXES):
            issues.append(("MULTI_FIGURA", char, key,
                           "%d siluetas separadas en la celda" % st["blobs"]))
        # Los cuadros de efecto (aura, haz, proyectil, poder) SALEN del cuerpo a proposito
        # y tocan el borde sin estar cortados: solo se avisa en poses normales.
        if st["edge"] and not key.startswith(FX_PREFIXES):
            issues.append(("BORDE", char, key,
                           "la figura toca el borde (%dx%d)" % (st["w"], st["h"])))

    # Duplicados y escala se juzgan DENTRO de cada animacion: repetir un cuadro entre
    # animaciones distintas es normal (comparten poses), dentro de una no.
    for anim, seq in anims.items():
        keys = [k for k, _ in seq if k in stats]
        seen = {}
        for k in keys:
            if digest[k] in seen and seen[digest[k]] != k:
                issues.append(("DUPLICADO", char, "%s: %s == %s" % (anim, seen[digest[k]], k),
                               "cuadros identicos en la misma animacion"))
            seen.setdefault(digest[k], k)
        hs = [stats[k]["h"] for k in keys if not stats[k]["empty"]]
        if anim.startswith(ERGUIDAS) and len(hs) >= 3:
            med = float(np.median(hs))
            for k in keys:
                h = stats[k]["h"]
                if h and med and abs(h - med) / med > SCALE_TOL:
                    issues.append(("ESCALA", char, "%s: %s" % (anim, k),
                                   "alto %d vs mediana %d de la animacion" % (h, int(med))))

    if verbose:
        print("%-24s %3d cuadros, %2d animaciones -> %d avisos"
              % (char, len(frames), len(anims), len(issues)))
    return issues


def problem_sheet(char: str, issues: list, out_dir: str) -> str | None:
    """Dibuja SOLO los cuadros senalados, con su motivo. Una hoja de 20 celdas se revisa
    en segundos; la `<char>_TODO.png` de 7000 px hay que recorrerla entera."""
    from PIL import ImageDraw

    keys = []
    for kind, c, key, why in issues:
        if c != char or kind == "DUPLICADO":
            continue  # el duplicado se ve mejor en la hoja comparativa, no aqui
        base = key.split(": ")[-1]
        if base not in keys:
            keys.append((base, kind, why))
    keys = [k for k in keys if isinstance(k, tuple)]
    if not keys:
        return None

    data, atlas = load(char)
    cell, cols = 200, 6
    rows_n = (len(keys) + cols - 1) // cols
    cv = Image.new("RGBA", (cell * cols, (cell + 34) * rows_n), (32, 32, 48, 255))
    d = ImageDraw.Draw(cv)
    for i, (key, kind, why) in enumerate(keys):
        if key not in data["frames"]:
            continue
        cx, cy = (i % cols) * cell, (i // cols) * (cell + 34)
        x, y, w, h = data["frames"][key]["src"]
        t = atlas.crop((x, y, x + w, y + h)).resize((cell, cell), Image.Resampling.LANCZOS)
        cv.alpha_composite(t, (cx, cy + 34))
        d.rectangle([cx, cy, cx + cell - 1, cy + cell + 33], outline=(80, 80, 120, 255))
        d.text((cx + 5, cy + 4), key[:28], fill=(255, 235, 120, 255))
        d.text((cx + 5, cy + 18), ("%s %s" % (kind, why))[:34], fill=(255, 130, 130, 255))
    path = os.path.join(out_dir, "_PROBLEMAS_%s.png" % char)
    cv.convert("RGB").save(path)
    return path


def main() -> None:
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
    ap = argparse.ArgumentParser()
    ap.add_argument("--only")
    ap.add_argument("--verbose", action="store_true")
    ap.add_argument("--sheets", action="store_true",
                    help="dibuja _PROBLEMAS_<char>.png con SOLO los cuadros senalados")
    ap.add_argument("--out", default=os.path.join(
        os.path.dirname(os.path.abspath(__file__)), "_audit_sheets"))
    args = ap.parse_args()

    chars = sorted(os.path.basename(p)[:-5] for p in glob.glob(os.path.join(DATA, "*.json")))
    chars = [c for c in chars if c not in SKIP and (not args.only or c == args.only)]

    allissues, sheets = [], []
    for c in chars:
        issues = audit_char(c, args.verbose)
        allissues += issues
        if args.sheets:
            os.makedirs(args.out, exist_ok=True)
            p = problem_sheet(c, issues, args.out)
            if p:
                sheets.append(p)

    by_kind: dict[str, list] = {}
    for kind, char, key, why in allissues:
        by_kind.setdefault(kind, []).append((char, key, why))

    print("\n=== RESUMEN (%d peleadores) ===" % len(chars))
    for kind in ("VACIO", "MULTI_FIGURA", "DUPLICADO", "ESCALA", "BORDE"):
        print("  %-14s %d" % (kind, len(by_kind.get(kind, []))))

    for kind in ("VACIO", "MULTI_FIGURA", "DUPLICADO", "ESCALA", "BORDE"):
        rows = by_kind.get(kind, [])
        if not rows:
            continue
        print("\n--- %s (%d) ---" % (kind, len(rows)))
        for char, key, why in rows:
            print("  %-22s %-42s %s" % (char, key, why))

    if sheets:
        print("\n=== HOJAS DE PROBLEMAS (%d) ===" % len(sheets))
        for p in sheets:
            print("  %s" % p)


if __name__ == "__main__":
    main()
