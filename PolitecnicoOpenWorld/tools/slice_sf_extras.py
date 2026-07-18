#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Integra las hojas "extras <char>.png" (SALTO 4 / AGACHARSE 3 / DANO 3 / DERRIBO 3,
una banda por seccion) SOBREESCRIBIENDO las poses aproximadas de GEN/<char>/
(saltos, agacharse, danio y caida). Luego: pack_sf_character.py <char> <Titulo>.
"""
import os, sys
import numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from slice_sf_custom_sheets import detect_all, ROOT_EXT, GEN
from slice_sf_reference_sheet import cut, place, TARGET_H

def bands_of(blobs):
    blobs = sorted(blobs, key=lambda b: ((b[1] + b[3]) / 2, b[0]))
    bands = []
    for b in blobs:
        cy = (b[1] + b[3]) / 2
        if bands and abs(cy - bands[-1][0]) < 80:
            bands[-1][1].append(b)
            bands[-1][0] = sum((x[1] + x[3]) / 2 for x in bands[-1][1]) / len(bands[-1][1])
        else:
            bands.append([cy, [b]])
    return [sorted(bs, key=lambda b: b[0]) for _, bs in bands]

def merge_x(bs, gap=25):
    """Fusiona blobs contiguos en X (figuras partidas). Conserva TODOS los ids
    de etiqueta para que el alfa del recorte incluya ambas mitades."""
    out = []
    for b in bs:
        b = (b[0], b[1], b[2], b[3], [b[4]] if not isinstance(b[4], list) else b[4])
        if out and b[0] - out[-1][2] < gap:
            p = out[-1]
            out[-1] = (min(p[0], b[0]), min(p[1], b[1]), max(p[2], b[2]), max(p[3], b[3]), p[4] + b[4])
        else:
            out.append(b)
    return out

def apply_extras(char):
    src = os.path.join(ROOT_EXT, f'extras {char}.png')
    im, lbl, blobs = detect_all(src, 36)
    bands = bands_of(blobs)
    assert len(bands) >= 4, f'{char}: se esperaban 4 bandas, hay {len(bands)}'
    salto, crouch, dano = bands[0], bands[1], bands[2]
    derribo = merge_x(bands[3])
    print(f'{char}: salto {len(salto)} · agacharse {len(crouch)} · dano {len(dano)} · derribo {len(derribo)}')

    h = lambda b: b[3] - b[1]
    med = lambda bs: sorted(h(b) for b in bs)[len(bs) // 2]
    s_salto = TARGET_H / med(salto)
    s_crouch = TARGET_H / h(crouch[0])       # crouch-1 ~ de pie
    s_dano = TARGET_H / med(dano)
    s_derribo = s_dano

    # Recorte con alfa = UNION de ids (los blobs fusionados traen lista de ids)
    def C(b):
        ids = b[4] if isinstance(b[4], list) else [b[4]]
        x0, y0, x1, y1 = b[:4]
        from PIL import Image as _I
        rgb = np.asarray(im)[y0:y1, x0:x1]
        region = lbl[y0:y1, x0:x1]
        alpha = (np.isin(region, ids) * 255).astype(np.uint8)
        return _I.fromarray(np.dstack([rgb, alpha]), 'RGBA')
    pick = lambda lst, i: lst[min(i, len(lst) - 1)]
    fr = {}
    fr['jump-start-land-1'] = place(C(salto[0]), s_salto)
    for i, j in enumerate([0, 1, 2, 2, 3, 3]): fr[f'jump-up-{i+1}'] = place(C(pick(salto, j)), s_salto)
    for i in range(7): fr[f'jump-roll-{i+1}'] = place(C(pick(salto, 2)), s_salto, rot=-(i * 51), anchor='center')
    for i in range(3):
        fr[f'crouch-{i+1}'] = place(C(pick(crouch, i)), s_crouch)
    for i, j in enumerate([2, 1, 2]): fr[f'crouch-turn-{i+1}'] = place(C(pick(crouch, j)), s_crouch)
    for i, j in enumerate([0, 0, 1, 0]): fr[f'hit-face-{i+1}'] = place(C(pick(dano, j)), s_dano)
    for i, j in enumerate([1, 2, 2, 1]): fr[f'hit-stomach-{i+1}'] = place(C(pick(dano, j)), s_dano)
    fr['stun-3'] = place(C(pick(dano, 2)), s_dano)
    for i, j in enumerate([0, 0, 1, 2, 2]): fr[f'fall-{i+1}'] = place(C(pick(derribo, j)), s_derribo)

    out = os.path.join(GEN, char)
    for k, img in fr.items(): img.save(os.path.join(out, f'{k}.png'), 'PNG')
    print(f'{char}: {len(fr)} poses REALES sobreescritas en GEN/{char}')

if __name__ == '__main__':
    apply_extras('senortienda')
    apply_extras('reygrupero')
