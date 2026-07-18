#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Rebana una HOJA DE REFERENCIA de personaje (estilo "sprites Prankedy.png": secciones
IDLE/CAMINAR/CORRER/SALTO/AGACHARSE/ATAQUE/DANO/DERRIBO/VICTORIA sobre fondo negro)
y genera los 77 cuadros de pelea en STREETFIGHTER/GEN/<char>/ (256x256, pies en 128,224).
El fondo se separa por SILUETA (cierre morfologico + relleno de huecos: la ropa negra
pura queda DENTRO del contorno). Uso:
    python3 tools/slice_sf_reference_sheet.py "<hoja.png>" <char>
Luego: python3 tools/pack_sf_character.py <char> <Titulo>
"""
import os, sys
import numpy as np
from PIL import Image
from scipy import ndimage

CANVAS, FEET_Y, CX, TARGET_H = 256, 224, 128, 100.0

def detect(src_path, cut_frac=0.797, min_h=50):
    im = Image.open(src_path).convert('RGB')
    a = np.asarray(im).astype(int)
    s = a.sum(axis=2)
    base = s > 24
    base[:, int(im.width * cut_frac):] = False
    filled = ndimage.binary_fill_holes(ndimage.binary_closing(base, structure=np.ones((5, 5))))
    lbl, _ = ndimage.label(filled, structure=np.ones((3, 3)))
    blobs = []
    for i, sl in enumerate(ndimage.find_objects(lbl)):
        if sl is None: continue
        y0, y1, x0, x1 = sl[0].start, sl[0].stop, sl[1].start, sl[1].stop
        if y1 - y0 < min_h or x1 - x0 < 25: continue
        if int((lbl[sl] == i + 1).sum()) < 1400: continue
        blobs.append((x0, y0, x1, y1, i + 1))
    blobs.sort(key=lambda b: (b[1] + b[3]) / 2)
    bands = []
    for b in blobs:
        cy = (b[1] + b[3]) / 2
        if bands and abs(cy - bands[-1][0]) < 80:
            bands[-1][1].append(b)
            bands[-1][0] = sum((x[1] + x[3]) / 2 for x in bands[-1][1]) / len(bands[-1][1])
        else:
            bands.append([cy, [b]])
    return im, lbl, [sorted(band[1], key=lambda b: b[0]) for band in bands]

def cut(im, lbl, blob):
    x0, y0, x1, y1, bid = blob
    rgb = np.asarray(im)[y0:y1, x0:x1]
    alpha = ((lbl[y0:y1, x0:x1] == bid) * 255).astype(np.uint8)
    out = np.dstack([rgb, alpha])
    return Image.fromarray(out, 'RGBA')

def place(img, scale, rot=0.0, anchor='feet', stretch_x=1.0):
    w = max(1, int(round(img.width * scale * stretch_x)))
    h = max(1, int(round(img.height * scale)))
    img = img.resize((w, h), Image.Resampling.LANCZOS)
    if rot:
        img = img.rotate(rot, expand=True, resample=Image.Resampling.BICUBIC)
    canvas = Image.new('RGBA', (CANVAS, CANVAS), (0, 0, 0, 0))
    x = CX - img.width // 2
    y = (170 - img.height // 2) if anchor == 'center' else (FEET_Y - img.height)
    canvas.paste(img, (x, y), img)
    return canvas

def main(src_path, char):
    im, lbl, bands = detect(src_path)
    for i, bs in enumerate(bands):
        print(f'banda {i}: {len(bs)} blobs')
    if len(bands) < 5:
        sys.exit('ERROR: se esperaban >= 5 bandas (idle+caminar / correr / salto+agacharse / ataque / dano+derribo+victoria)')

    b0, b1, b2, b3, b4 = bands[0], bands[1], bands[2], bands[3], bands[4]
    idle = b0[:3]; walk = b0[3:9]
    jump = b2[:4]; crouch = b2[-3:]
    atk = b3
    # banda 4: 2 de dano + 3 de derribo + hasta 3 de victoria
    dano = b4[:2]; derribo = b4[2:5]; victoria = b4[5:] or b4[-2:]

    # ESCALA POR SECCION: la hoja fuente dibuja cada seccion a tamano distinto
    # (idle ~193 px, caminar ~181, salto ~140, ataque ~140, dano ~153...). Cada
    # seccion se normaliza para que su pose DE PIE mida TARGET_H; agacharse y el
    # derribo HEREDAN la escala de su seccion hermana (deben verse bajitos).
    h = lambda b: b[3] - b[1]
    s_idle = TARGET_H / h(idle[0])
    s_walk = TARGET_H / max(h(b) for b in walk)
    s_jump = (TARGET_H * 1.02) / max(h(b) for b in jump)
    s_crouch = s_jump                     # misma banda/escala de dibujo que SALTO
    s_atk = TARGET_H / max(h(b) for b in atk)
    s_dano = TARGET_H / max(h(b) for b in dano)
    s_derribo = s_dano                    # misma banda/escala de dibujo que DANO
    s_vict = TARGET_H / max(h(b) for b in victoria)
    C = lambda b: cut(im, lbl, b)
    pick = lambda lst, i: lst[min(i, len(lst) - 1)]

    frames = {}
    for i in range(3): frames[f'idle-{i+1}'] = place(C(idle[i]), s_idle)
    frames['idle-4'] = place(C(idle[1]), s_idle)
    for i in range(6):
        frames[f'forwards-{i+1}'] = place(C(pick(walk, i)), s_walk)
        frames[f'backwards-{i+1}'] = place(C(pick(walk, 5 - i)), s_walk)
    frames['jump-start-land-1'] = place(C(jump[0]), s_jump)
    for i, j in enumerate([1, 2, 2, 2, 3, 3]): frames[f'jump-up-{i+1}'] = place(C(pick(jump, j)), s_jump)
    for i in range(7): frames[f'jump-roll-{i+1}'] = place(C(pick(jump, 2)), s_jump, rot=-(i * 51), anchor='center')
    for i in range(3):
        frames[f'crouch-{i+1}'] = place(C(crouch[i]), s_crouch)
        frames[f'idle-turn-{i+1}'] = place(C(idle[2 - i]), s_idle)
    for i, j in enumerate([2, 1, 2]): frames[f'crouch-turn-{i+1}'] = place(C(crouch[j]), s_crouch)
    # Ataques = secuencia del ataque especial (tanque/prop del personaje)
    for i, j in enumerate([2, 3]): frames[f'light-punch-{i+1}'] = place(C(pick(atk, j)), s_atk)
    for i, j in enumerate([1, 2, 3]): frames[f'med-punch-{i+1}'] = place(C(pick(atk, j)), s_atk)
    frames['heavy-punch-1'] = place(C(pick(atk, 4)), s_atk)
    for i, j in enumerate([3, 4]): frames[f'light-kick-{i+1}'] = place(C(pick(atk, j)), s_atk)
    frames['med-kick-1'] = place(C(pick(atk, 4)), s_atk)
    for i in range(5): frames[f'heavy-kick-{i+1}'] = place(C(pick(atk, i)), s_atk)
    for i, j in enumerate([0, 0, 1, 0]): frames[f'hit-face-{i+1}'] = place(C(pick(dano, j)), s_dano)
    for i, j in enumerate([1, 1, 1, 1]): frames[f'hit-stomach-{i+1}'] = place(C(pick(dano, j)), s_dano)
    frames['stun-3'] = place(C(pick(dano, 1)), s_dano)
    for i, j in enumerate([0, 0, 1, 2, 2]): frames[f'fall-{i+1}'] = place(C(pick(derribo, j)), s_derribo)
    for i, j in enumerate([0, 1, 2, 1]): frames[f'victory-{i+1}'] = place(C(pick(victoria, j)), s_vict)
    for i, j in enumerate([0, 1, 2, 3]): frames[f'special-{i+1}'] = place(C(pick(atk, j)), s_atk)

    here = os.path.dirname(os.path.abspath(__file__))
    out = os.path.join(here, '..', 'app/src/main/assets/STREETFIGHTER/GEN', char)
    os.makedirs(out, exist_ok=True)
    for k, img in frames.items():
        img.save(os.path.join(out, f'{k}.png'), 'PNG')
    print(f'{char}: {len(frames)} cuadros escritos en {out}')

if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
