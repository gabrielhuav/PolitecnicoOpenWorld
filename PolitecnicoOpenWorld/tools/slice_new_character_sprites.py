#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Recorta los spritesheets de los 3 personajes nuevos (Señor de la Tienda, El Rey de las Bromas,
Pepe del Rey) en frames individuales, QUITA EL FONDO NEGRO con transparencia y los exporta como
.webp con la nomenclatura que consume PlayerSkin:

    app/src/main/assets/SPRITES/NPC/<Char>/<Idle|Walk|Run|Special>/<prefix>_<i|w|r|s>_<N>.webp

Pipeline (resolución-independiente, ya validado con los 3 sheets):
  1) FONDO por FLOOD-FILL desde los bordes (NO umbral global): conserva el chaleco/jersey OSCURO
     (su negro INTERIOR no toca el borde). Imprescindible para el Señor (chaleco negro) y el Rey
     (jersey navy) sobre fondo negro.
  2) Por animación defines una REGIÓN (caja fraccionaria x0,y0,x1,y1) y el nº de frames. Dentro,
     se parte en EXACTAMENTE n celdas (split_n) cortando en el VALLE más profundo de cada frontera
     → corta en los huecos aunque las figuras casi se toquen (ciclos de carrera).
  3) Por cada celda se conserva el BLOQUE VERTICAL contiguo más grande (la figura) y se descartan
     tiras separadas arriba/abajo (etiquetas de texto) — main_block.
  4) NORMALIZACIÓN por personaje: MISMA escala (limitada por el frame más alto/ancho) y PIES
     alineados a una base común → las animaciones no "rebotan" de tamaño.

Uso:
    pip install pillow numpy scipy
    # coloca los 3 .png (sin recortar) en app/src/main/assets/SPRITES/NPC/ con estos nombres:
    #   sprites_el_señor_de_la_tienda.png  sprites_pepe_rey_de_las_bromas.png  sprites_el_rey_de_las_bromas.png
    python tools/slice_new_character_sprites.py
    # montajes de revisión en tools/_debug/

NOTA: si re-exportas el arte, ajusta las CAJAS (regions) abajo y revisa los montajes.
"""
import os, sys
import numpy as np
from PIL import Image
from scipy import ndimage

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.normpath(os.path.join(HERE, "..", "app", "src", "main", "assets", "SPRITES", "NPC"))
DEBUG = os.path.join(HERE, "_debug")

BLACK = 24           # luminancia <= esto = candidato a FONDO (se borra solo si toca el borde)
OUT = 256            # lienzo cuadrado de salida
BOTTOM_MARGIN = 0.05 # margen inferior (pies)
ANIM = {"idle": ("Idle", "i"), "walk": ("Walk", "w"), "run": ("Run", "r"), "special": ("Special", "s")}

# region = (x0, y0, x1, y1, count) en FRACCIÓN del sheet.
CONFIG = {
    "senor_tienda": {"sheet": "sprites_el_señor_de_la_tienda.png", "char": "SenorTienda", "prefix": "st",
        "regions": {"idle": (0.005, 0.045, 0.275, 0.270, 3), "walk": (0.295, 0.045, 0.665, 0.270, 4),
                    "run": (0.005, 0.290, 0.675, 0.505, 8), "special": (0.005, 0.525, 0.625, 0.740, 6)}},
    "pepe_rey": {"sheet": "sprites_pepe_rey_de_las_bromas.png", "char": "PepeRey", "prefix": "pr",
        "regions": {"idle": (0.0, 0.025, 0.570, 0.240, 3), "walk": (0.0, 0.250, 0.580, 0.455, 4),
                    "run": (0.0, 0.455, 1.0, 0.640, 8), "special": (0.0, 0.680, 1.0, 0.880, 5)}},
    "rey_bromas": {"sheet": "sprites_el_rey_de_las_bromas.png", "char": "ReyBromas", "prefix": "rb",
        "regions": {"idle": (0.0, 0.045, 0.330, 0.305, 3), "walk": (0.0, 0.325, 0.665, 0.515, 6),
                    "run": (0.0, 0.540, 0.665, 0.710, 8), "special": (0.0, 0.720, 0.665, 0.965, 6)}},
}


def rmbg(img):
    r = np.array(img.convert("RGBA")); rgb = r[:, :, :3].astype(np.int32)
    lum = 0.299*rgb[:, :, 0] + 0.587*rgb[:, :, 1] + 0.114*rgb[:, :, 2]
    lbl, n = ndimage.label(lum <= BLACK)
    border = set(np.unique(np.concatenate([lbl[0, :], lbl[-1, :], lbl[:, 0], lbl[:, -1]]))); border.discard(0)
    r[np.isin(lbl, list(border)), 3] = 0
    return r


def split_n(region, k):
    prof = (region[:, :, 3] > 0).sum(axis=0).astype(float); cols = np.where(prof > 0)[0]
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


def main_block(cell):
    """Bloque vertical contiguo más alto (la figura); descarta tiras separadas (etiquetas)."""
    rows = (cell[:, :, 3] > 0).any(axis=1); runs = []; y = 0; H = len(rows)
    while y < H:
        if rows[y]:
            y0 = y
            while y < H and rows[y]: y += 1
            runs.append((y0, y))
        else:
            y += 1
    if not runs: return None
    y0, y1 = max(runs, key=lambda r: r[1]-r[0]); sub = cell[y0:y1]
    xs = np.where((sub[:, :, 3] > 0).any(axis=0))[0]
    return None if len(xs) == 0 else sub[:, xs[0]:xs[-1]+1]


def process(key):
    cfg = CONFIG[key]; path = os.path.join(ASSETS, cfg["sheet"])
    if not os.path.exists(path):
        print("FALTA:", path); return
    img = Image.open(path); W, H = img.size; arr = rmbg(img)
    print(f"=== {key} ({W}x{H}) -> {cfg['char']} ===")
    frames = {}; maxh = maxw = 1
    for anim, (x0, y0, x1, y1, cnt) in cfg["regions"].items():
        reg = arr[int(y0*H):int(y1*H), int(x0*W):int(x1*W)]
        runs = split_n(reg, cnt); print(f"  {anim:8s} {len(runs)}/{cnt}")
        lst = []
        for (cx0, cx1) in runs:
            cr = main_block(reg[:, cx0:cx1])
            if cr is not None and cr.shape[0] > 4:
                lst.append(cr); maxh = max(maxh, cr.shape[0]); maxw = max(maxw, cr.shape[1])
        frames[anim] = lst
    scale = min((OUT*(1-2*BOTTOM_MARGIN))/maxh, (OUT*0.96)/maxw); base = int(OUT*(1-BOTTOM_MARGIN))
    os.makedirs(DEBUG, exist_ok=True)
    for anim, lst in frames.items():
        sd, lt = ANIM[anim]; od = os.path.join(ASSETS, cfg["char"], sd); os.makedirs(od, exist_ok=True)
        mont = []
        for n, cr in enumerate(lst, 1):
            ch, cw = cr.shape[:2]; nw, nh = max(1, int(cw*scale)), max(1, int(ch*scale))
            im = Image.fromarray(cr, "RGBA").resize((nw, nh), Image.LANCZOS)
            cv = Image.new("RGBA", (OUT, OUT), (0, 0, 0, 0)); cv.paste(im, ((OUT-nw)//2, base-nh), im)
            cv.save(os.path.join(od, f"{cfg['prefix']}_{lt}_{n}.webp"), "WEBP", quality=92, method=6); mont.append(cv)
        if mont:
            mo = Image.new("RGBA", (OUT*len(mont), OUT), (30, 30, 30, 255))
            for i, c in enumerate(mont): mo.paste(c, (i*OUT, 0), c)
            mo.convert("RGB").save(os.path.join(DEBUG, f"{key}_{anim}.png"))


if __name__ == "__main__":
    for k in (sys.argv[1:] or list(CONFIG.keys())):
        process(k)
    print("Montajes de revisión en:", DEBUG)
