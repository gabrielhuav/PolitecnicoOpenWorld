#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Rebana las hojas de referencia de los 4 peleadores POW (reygrupero/senortienda/
paparazzi1/paparazzi5.png, raiz del repo externo) con MAPEO POR PERSONAJE:
cada hoja salio de ChatGPT con secciones distintas, asi que aqui se define QUE
seccion alimenta QUE animacion (y las que faltan se aproximan con rotaciones).
Reusa cut/place de slice_sf_reference_sheet. Salida: STREETFIGHTER/GEN/<char>/.
"""
import os, sys
import numpy as np
from PIL import Image
from scipy import ndimage
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from slice_sf_reference_sheet import cut, place, TARGET_H

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT_EXT = os.path.normpath(os.path.join(HERE, '..', '..'))   # raiz del repo externo
GEN = os.path.join(HERE, '..', 'app/src/main/assets/STREETFIGHTER/GEN')

def detect_all(src, min_h):
    im = Image.open(src).convert('RGB')
    a = np.asarray(im).astype(int)
    s = a.sum(axis=2)
    filled = ndimage.binary_fill_holes(ndimage.binary_closing(s > 24, structure=np.ones((5, 5))))
    lbl, _ = ndimage.label(filled, structure=np.ones((3, 3)))
    blobs = []
    for i, sl in enumerate(ndimage.find_objects(lbl)):
        if sl is None: continue
        y0, y1, x0, x1 = sl[0].start, sl[0].stop, sl[1].start, sl[1].stop
        if y1 - y0 < min_h or x1 - x0 < 22: continue
        if int((lbl[sl] == i + 1).sum()) < 900: continue
        blobs.append((x0, y0, x1, y1, i + 1))
    return im, lbl, blobs

def in_box(b, box):
    cx, cy = (b[0] + b[2]) / 2, (b[1] + b[3]) / 2
    return box[0] <= cx <= box[2] and box[1] <= cy <= box[3]

class Sheet:
    def __init__(self, src, boxes, min_h=36):
        self.im, self.lbl, blobs = detect_all(src, min_h)
        self.sec = {}
        for name, (box, n) in boxes.items():
            bs = sorted([b for b in blobs if in_box(b, box)], key=lambda b: b[0])
            if len(bs) != n:
                print(f'  AVISO {os.path.basename(src)} seccion {name}: esperaba {n}, hay {len(bs)} (alturas {[b[3]-b[1] for b in bs]})')
            self.sec[name] = bs
        self.scale = {}

    def calc_scales(self, standing, inherit):
        for name in standing:
            self.scale[name] = TARGET_H / max(b[3] - b[1] for b in self.sec[name])
        for name, ref in inherit.items():
            self.scale[name] = self.scale[ref]

    def F(self, sec, i, rot=0.0, anchor='feet', scale_mul=1.0, stretch_x=1.0):
        bs = self.sec[sec]
        b = bs[min(i, len(bs) - 1)]
        return place(cut(self.im, self.lbl, b), self.scale[sec] * scale_mul, rot=rot, anchor=anchor, stretch_x=stretch_x)

def std_extras(fr, S, idle_sec='idle', jump_sec=None, jump_idx=2):
    """Aproximaciones comunes: saltos desde 'jump_sec' (o correr), giros, reversa de caminata."""
    for i in range(6):
        fr[f'backwards-{i+1}'] = fr[f'forwards-{6-i}']
    for i in range(3):
        fr[f'idle-turn-{i+1}'] = fr[f'idle-{3-i}']
        fr[f'crouch-turn-{i+1}'] = fr['crouch-3']
    fr['idle-4'] = fr['idle-2']
    if jump_sec:
        for i, j in enumerate([0, 1, 2, 2, 3, 3]):
            fr[f'jump-up-{i+1}'] = S.F(jump_sec, j)
        for i in range(7):
            fr[f'jump-roll-{i+1}'] = S.F(jump_sec, jump_idx, rot=-(i * 51), anchor='center')

def rot_hurt_fall(fr, S, sec='idle'):
    """Danio/caida aproximados con el idle inclinado (pendiente hoja 2 con poses reales)."""
    for i, r in enumerate([8, 12, 14, 10]): fr[f'hit-face-{i+1}'] = S.F(sec, 0, rot=r)
    for i, r in enumerate([-6, -10, -12, -8]): fr[f'hit-stomach-{i+1}'] = S.F(sec, 1, rot=r)
    fr['stun-3'] = S.F(sec, 1, rot=16)
    for i, r in enumerate([20, 45, 70, 90, 90]): fr[f'fall-{i+1}'] = S.F(sec, 0, rot=r)

def save(fr, char):
    out = os.path.join(GEN, char); os.makedirs(out, exist_ok=True)
    for k, img in fr.items(): img.save(os.path.join(out, f'{k}.png'), 'PNG')
    print(f'{char}: {len(fr)} cuadros -> GEN/{char}')

# ─── PAPARAZZI 1 y 5 (siguieron la plantilla; TOMAR FOTO arrodillado = agacharse) ───
def paparazzi(char):
    src = os.path.join(ROOT_EXT, f'{char}.png')
    boxes = {
        'idle':      ((0,   30, 350, 250), 3),
        'walk':      ((360, 30, 1080, 250), 6),
        'salto':     ((0,  450, 500, 650), 4),
        'preguntar': ((600, 450, 1050, 650), 3),
        'responder': ((0,  650, 330, 850), 3),
        'foto':      ((360, 650, 950, 850), 4),
        'dano':      ((0,  850, 340, 1024), 3),
        'derribo':   ((340, 850, 880, 1024), 3),
        'victoria':  ((880, 850, 1250, 1024), 3),
    }
    S = Sheet(src, boxes)
    S.calc_scales(standing=['idle', 'walk', 'salto', 'preguntar', 'responder', 'victoria'],
                  inherit={'foto': 'responder', 'dano': 'idle', 'derribo': 'idle'})
    fr = {}
    for i in range(3): fr[f'idle-{i+1}'] = S.F('idle', i)
    for i in range(6): fr[f'forwards-{i+1}'] = S.F('walk', i)
    fr['jump-start-land-1'] = S.F('salto', 0)
    fr['crouch-1'] = S.F('foto', 0); fr['crouch-2'] = S.F('foto', 1); fr['crouch-3'] = S.F('foto', 2)
    # golpes con microfono (preguntar = jab) y camara (foto = flash)
    for i, j in enumerate([0, 1]): fr[f'light-punch-{i+1}'] = S.F('preguntar', j)
    for i, j in enumerate([0, 1, 2]): fr[f'med-punch-{i+1}'] = S.F('responder', j)
    fr['heavy-punch-1'] = S.F('preguntar', 1)
    for i, j in enumerate([1, 2]): fr[f'light-kick-{i+1}'] = S.F('responder', j)
    fr['med-kick-1'] = S.F('responder', 2)
    for i, j in enumerate([0, 1, 2, 1, 0]): fr[f'heavy-kick-{i+1}'] = S.F('preguntar', j)
    for i, j in enumerate([0, 1, 2, 1]): fr[f'hit-face-{i+1}'] = S.F('dano', j)
    for i, j in enumerate([1, 2, 2, 1]): fr[f'hit-stomach-{i+1}'] = S.F('dano', j)
    fr['stun-3'] = S.F('dano', 2)
    for i, j in enumerate([0, 0, 1, 2, 2]): fr[f'fall-{i+1}'] = S.F('derribo', j)
    for i, j in enumerate([0, 1, 2, 1]): fr[f'victory-{i+1}'] = S.F('victoria', j)
    for i, j in enumerate([0, 1, 3, 3]): fr[f'special-{i+1}'] = S.F('foto', j)  # ¡flashazo!
    std_extras(fr, S, jump_sec='salto')
    save(fr, char)

# ─── SENOR DE LA TIENDA (sin salto/agacharse/danio/derribo: se aproximan) ───
def senortienda():
    src = os.path.join(ROOT_EXT, 'senortienda.png')
    boxes = {
        'idle':     ((0,   20, 420, 270), 3),
        'walk':     ((440, 20, 1030, 270), 4),
        'run':      ((0,  280, 1030, 500), 8),
        'acciones': ((0,  530, 980, 750), 6),
        'poses':    ((0,  780, 930, 1024), 7),
        'escobazo': ((940, 780, 1536, 1024), 5),
    }
    S = Sheet(src, boxes)
    # Blobs ANCHOS del escobazo = dos figuras fusionadas (golpeador+golpeado): fuera
    S.sec['escobazo'] = [b for b in S.sec['escobazo'] if (b[2] - b[0]) <= 1.35 * (b[3] - b[1])]
    print(f"  escobazo utilizable (solo 1 figura): {len(S.sec['escobazo'])}")
    S.calc_scales(standing=['idle', 'walk', 'run', 'acciones', 'poses', 'escobazo'], inherit={})
    fr = {}
    for i in range(3): fr[f'idle-{i+1}'] = S.F('idle', i, stretch_x=1.15)
    for i, j in enumerate([0, 1, 2, 3, 2, 1]): fr[f'forwards-{i+1}'] = S.F('walk', j)
    fr['jump-start-land-1'] = S.F('escobazo', 1, scale_mul=0.92)
    # agacharse aproximado: barrida baja del escobazo
    fr['crouch-1'] = S.F('escobazo', 1, scale_mul=0.9)
    fr['crouch-2'] = S.F('escobazo', 3, scale_mul=0.85)
    fr['crouch-3'] = S.F('escobazo', 3, scale_mul=0.8)
    # golpes = ESCOBAZO LETAL + acciones
    for i, j in enumerate([2, 3]): fr[f'light-punch-{i+1}'] = S.F('acciones', j)
    for i, j in enumerate([0, 2, 4]): fr[f'med-punch-{i+1}'] = S.F('escobazo', j)
    fr['heavy-punch-1'] = S.F('escobazo', 2)
    for i, j in enumerate([4, 5]): fr[f'light-kick-{i+1}'] = S.F('acciones', j)
    fr['med-kick-1'] = S.F('escobazo', 3)
    for i in range(5): fr[f'heavy-kick-{i+1}'] = S.F('escobazo', i)
    rot_hurt_fall(fr, S)
    for i, j in enumerate([2, 3, 6, 3]): fr[f'victory-{i+1}'] = S.F('poses', j)  # pulgar/saludo
    for i, j in enumerate([0, 1, 2, 3]): fr[f'special-{i+1}'] = S.F('escobazo', j)
    std_extras(fr, S, jump_sec='run')
    save(fr, 'senortienda')

# ─── REY GRUPERO (sin salto/agacharse/danio/derribo; resbalon de platano = caida) ───
def reygrupero():
    src = os.path.join(ROOT_EXT, 'reygrupero.png')
    boxes = {
        'idle':   ((0,   30, 420, 320), 3),
        'camara': ((430, 30, 1060, 320), 4),
        'walk':   ((0,  330, 950, 550), 7),
        'run':    ((0,  560, 1050, 770), 8),
        'bromas': ((0,  780, 1080, 1024), 6),
        'extras': ((1060, 570, 1536, 800), 3),
        'extras2':((1130, 800, 1536, 1024), 2),
    }
    S = Sheet(src, boxes)
    S.calc_scales(standing=['idle', 'camara', 'walk', 'run', 'bromas', 'extras', 'extras2'], inherit={})
    fr = {}
    # stretch_x: el idle fuente esta dibujado mas GRANDE que el resto de secciones y al
    # normalizar por ALTURA queda flaco; se ensancha para igualar el ancho de la caminata
    for i in range(3): fr[f'idle-{i+1}'] = S.F('idle', i, stretch_x=1.25)
    for i in range(6): fr[f'forwards-{i+1}'] = S.F('walk', i)
    fr['jump-start-land-1'] = S.F('bromas', 3, scale_mul=0.92)   # agachado con cubeta
    fr['crouch-1'] = S.F('bromas', 3, scale_mul=0.9)
    fr['crouch-2'] = S.F('bromas', 3, scale_mul=0.82)
    fr['crouch-3'] = S.F('bromas', 3, scale_mul=0.78)
    # golpes: senalar/megafono/spray/globo
    for i, j in enumerate([0, 1]): fr[f'light-punch-{i+1}'] = S.F('bromas', j)
    for i, j in enumerate([0, 2, 1]): fr[f'med-punch-{i+1}'] = S.F('bromas', j)
    fr['heavy-punch-1'] = S.F('bromas', 2)
    for i, j in enumerate([5, 1]): fr[f'light-kick-{i+1}'] = S.F('bromas', j)
    fr['med-kick-1'] = S.F('bromas', 5)
    for i, j in enumerate([0, 1, 2, 5, 1]): fr[f'heavy-kick-{i+1}'] = S.F('bromas', j)
    # danio aproximado (idle inclinado) + CAIDA = resbalon con el platano
    for i, r in enumerate([8, 12, 14, 10]): fr[f'hit-face-{i+1}'] = S.F('idle', 0, rot=r)
    for i, r in enumerate([-6, -10, -12, -8]): fr[f'hit-stomach-{i+1}'] = S.F('idle', 1, rot=r)
    fr['stun-3'] = S.F('idle', 1, rot=16)
    for i, r in enumerate([0, 25, 55, 90, 90]): fr[f'fall-{i+1}'] = S.F('bromas', 4, rot=r)
    for i, j in enumerate([0, 1, 2, 0]): fr[f'victory-{i+1}'] = [S.F('extras', 0), S.F('extras', 2), S.F('extras2', 1), S.F('extras', 0)][i]
    for i, j in enumerate([0, 1, 2, 3]): fr[f'special-{i+1}'] = S.F('camara', j)  # broma con camara
    std_extras(fr, S, jump_sec='run')
    save(fr, 'reygrupero')

if __name__ == '__main__':
    paparazzi('paparazzi1')
    paparazzi('paparazzi5')
    senortienda()
    reygrupero()
