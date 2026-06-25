#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Recorta los spritesheets (CON FONDO YA TRANSPARENTE / Photoroom) de los 3 personajes nuevos
(Señor de la Tienda, El Rey de las Bromas, Pepe del Rey) en frames individuales y los exporta como
.webp con la nomenclatura que consume PlayerSkin:

    app/src/main/assets/SPRITES/NPC/<Char>/<Idle|Walk|Run|Special>/<prefix>_<i|w|r|s>_<N>.webp

Pipeline:
  - Usa el ALPHA que ya traen los .png "-Photoroom" (fondo transparente). NO hace flood-fill.
  - idle/walk/run: por REGIÓN (caja fraccionaria) → split_n (n celdas, corte en el valle) → main_block
    (conserva el bloque vertical de la figura, descarta etiquetas) → recorte nativo + margen uniforme
    (la figura llena ~0.865 del lienzo en TODAS las animaciones → tamaño consistente en el juego).
  - special (= "pegar"): las filas de ATAQUE/BROMAS mezclan figura + OBJETOS sueltos (tanque, globo,
    chispa, mist). Por eso el special usa segmentación por FIGURA: gap_runs + filtro de ALTURA
    (descarta objetos bajos) + main_block. Señor usa la fila ESCOBAZO LETAL (golpe real); Pepe la de
    BROMA CON TANQUE; Rey la de BROMAS/ACCIONES.

Uso:
    pip install pillow numpy
    # .png sin recortar (fondo transparente) en app/src/main/assets/SPRITES/NPC/ :
    #   sprites_el_señor_de_la_tienda-Photoroom.png  sprites_pepe_rey_de_las_bromas-Photoroom.png
    #   sprites_el_rey_de_las_bromas-Photoroom.png
    python tools/slice_new_character_sprites.py
"""
import os, sys
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.normpath(os.path.join(HERE, "..", "app", "src", "main", "assets", "SPRITES", "NPC"))
DEBUG = os.path.join(HERE, "_debug3")

TH = 30                       # alpha > TH = contenido (para segmentar)
PT, PB, PS = 0.10, 0.06, 0.08 # margen uniforme (frac. del tamaño de la figura)
SPECIAL_HFRAC = 0.45          # en special, un "run" es FIGURA si su alto >= esto * alto_region

ANIM = {"idle": ("Idle", "i"), "walk": ("Walk", "w"), "run": ("Run", "r"), "special": ("Special", "s")}

# region = (x0, y0, x1, y1, count). En 'special', count es solo informativo (se filtran objetos).
CONFIG = {
    "senor_tienda": {"sheet": "sprites_el_señor_de_la_tienda-Photoroom.png", "char": "SenorTienda", "prefix": "st",
        "regions": {"idle": (0.005, 0.045, 0.275, 0.270, 3), "walk": (0.295, 0.045, 0.665, 0.270, 4),
                    "run": (0.005, 0.290, 0.675, 0.505, 8), "special": (0.585, 0.782, 1.000, 0.992, 5)}},  # ESCOBAZO
    "pepe_rey": {"sheet": "sprites_pepe_rey_de_las_bromas-Photoroom.png", "char": "PepeRey", "prefix": "pr",
        "regions": {"idle": (0.0, 0.025, 0.570, 0.240, 3), "walk": (0.0, 0.258, 0.580, 0.455, 4),
                    "run": (0.0, 0.436, 1.0, 0.585, 8), "special": (0.0, 0.620, 1.0, 0.838, 5)}},           # TANQUE
    "rey_bromas": {"sheet": "sprites_el_rey_de_las_bromas-Photoroom.png", "char": "ReyBromas", "prefix": "rb",
        "regions": {"idle": (0.0, 0.045, 0.330, 0.305, 3), "walk": (0.0, 0.325, 0.665, 0.515, 6),
                    "run": (0.0, 0.540, 0.665, 0.710, 8), "special": (0.0, 0.776, 0.665, 0.965, 6)}},       # BROMAS
}


def split_n(mask, k):
    prof = mask.sum(axis=0).astype(float); cols = np.where(prof > 0)[0]
    if len(cols) == 0: return []
    a, b = cols[0], cols[-1] + 1
    if k <= 1: return [(a, b)]
    step = (b - a) / k; win = max(2, int(step * 0.40)); cuts = [a]
    for i in range(1, k):
        c = int(a + i*step); lo, hi = max(a+1, c-win), min(b-1, c+win)
        if hi > lo: c = lo + int(np.argmin(prof[lo:hi]))
        cuts.append(c)
    cuts.append(b)
    return [(cuts[i], cuts[i+1]) for i in range(k)]


def gap_runs(mask, min_gap):
    col = mask.any(axis=0); w = len(col); runs = []; x = 0
    while x < w:
        if col[x]:
            x0 = x
            while x < w and col[x]: x += 1
            xe = x; g = 0
            while xe < w and not col[xe]: g += 1; xe += 1
            if 0 < g < min_gap and xe < w: x = xe; continue
            runs.append((x0, x))
        else:
            x += 1
    return runs


def main_block(cell, mask):
    rows = mask.any(axis=1); runs = []; y = 0; Hh = len(rows)
    while y < Hh:
        if rows[y]:
            y0 = y
            while y < Hh and rows[y]: y += 1
            runs.append((y0, y))
        else:
            y += 1
    if not runs: return None
    a, b = max(runs, key=lambda r: r[1]-r[0]); sub = cell[a:b]; sm = mask[a:b]
    xs = np.where(sm.any(axis=0))[0]
    return None if len(xs) == 0 else sub[:, xs[0]:xs[-1]+1]


def emit(cr, prefix, lt, idx, od, mont):
    fh, fw = cr.shape[:2]
    top, bot, side = int(fh*PT), int(fh*PB), int(fw*PS)
    cv = np.zeros((fh+top+bot, fw+2*side, 4), np.uint8); cv[top:top+fh, side:side+fw] = cr
    Image.fromarray(cv, "RGBA").save(os.path.join(od, f"{prefix}_{lt}_{idx}.webp"), "WEBP", quality=95, method=6)
    mont.append(Image.fromarray(cv, "RGBA"))


def process(key):
    cfg = CONFIG[key]; path = os.path.join(ASSETS, cfg["sheet"])
    if not os.path.exists(path):
        print("FALTA:", path); return
    img = Image.open(path).convert("RGBA"); W, H = img.size
    arr = np.array(img); mask = arr[:, :, 3] > TH
    os.makedirs(DEBUG, exist_ok=True)
    print(f"=== {key} ({W}x{H}) -> {cfg['char']} ===")
    for anim, (x0, y0, x1, y1, cnt) in cfg["regions"].items():
        sd, lt = ANIM[anim]; od = os.path.join(ASSETS, cfg["char"], sd); os.makedirs(od, exist_ok=True)
        reg = arr[int(y0*H):int(y1*H), int(x0*W):int(x1*W)]; rmask = mask[int(y0*H):int(y1*H), int(x0*W):int(x1*W)]
        Rh = reg.shape[0]; mont = []; idx = 0
        if anim == "special":
            # Segmentación por FIGURA: gap_runs + filtro de ALTURA (descarta objetos sueltos como
            # tanque/globo/chispa/mist) + SEPARA runs anchos (figuras pegadas, p.ej. spray+megáfono
            # del Rey) + main_block (quita etiquetas). Robusto para las filas de ATAQUE/BROMAS.
            figs = []
            for (a, b) in gap_runs(rmask, max(3, int(reg.shape[1]*0.012))):
                sm = rmask[:, a:b]; ys = np.where(sm.any(axis=1))[0]
                if len(ys) and (ys[-1]-ys[0]+1) >= SPECIAL_HFRAC*Rh:
                    figs.append((a, b))
            if figs:
                single = min(b-a for a, b in figs)
                for (a, b) in figs:
                    n = max(1, round((b-a) / (single*1.25)))
                    subs = split_n(rmask[:, a:b], n) if n > 1 else [(0, b-a)]
                    for (sa, sb) in subs:
                        cr = main_block(reg[:, a+sa:a+sb], rmask[:, a+sa:a+sb])
                        if cr is None or cr.shape[0] < 5: continue
                        idx += 1; emit(cr, cfg["prefix"], lt, idx, od, mont)
        else:
            for (cx0, cx1) in split_n(rmask, cnt):
                cr = main_block(reg[:, cx0:cx1], rmask[:, cx0:cx1])
                if cr is None or cr.shape[0] < 5: continue
                idx += 1; emit(cr, cfg["prefix"], lt, idx, od, mont)
        print(f"  {anim:8s} {idx} frames")
        if mont:
            mh = max(m.height for m in mont); mw = sum(m.width for m in mont)
            mo = Image.new("RGBA", (mw, mh), (120, 120, 120, 255)); x = 0
            for m in mont: mo.paste(m, (x, mh-m.height), m); x += m.width
            mo.convert("RGB").save(os.path.join(DEBUG, f"{key}_{anim}.png"))


if __name__ == "__main__":
    for k in (sys.argv[1:] or list(CONFIG.keys())):
        process(k)
    print("Montajes de revisión en:", DEBUG)
