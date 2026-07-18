#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Genera los 77 cuadros de pelea (STREETFIGHTER/GEN/<char>/) a partir del set NPC
estandar de POW (SPRITES/NPC/<Folder>/{Idle,Walk,Run,Special}/*.webp), con la
MISMA tecnica del pipeline de Prankedy: lienzo 256x256 transparente, personaje
mirando a la DERECHA, ~100 px de alto, pies en (128, 224). Las poses que el set
NPC no tiene (golpes/reacciones/caidas) se APROXIMAN con rotaciones/aplastados
de los cuadros existentes -> por eso estos peleadores son version ALPHA.
Luego correr: python3 tools/pack_sf_character.py <char> <Titulo>
"""
import os
import re
import sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
BASE = os.path.normpath(os.path.join(HERE, ".."))
NPC_DIR = os.path.join(BASE, "app/src/main/assets/SPRITES/NPC")
GEN_DIR = os.path.join(BASE, "app/src/main/assets/STREETFIGHTER/GEN")

CANVAS = 256
FEET_Y = 224
CENTER_X = 128
TARGET_H = 100.0

def load_anim(folder):
    """Carga los .webp de una carpeta ordenados por su sufijo numerico."""
    if not os.path.isdir(folder):
        return []
    files = [f for f in os.listdir(folder) if f.lower().endswith((".webp", ".png"))]
    def num(f):
        m = re.search(r"_(\d+)\.\w+$", f)
        return int(m.group(1)) if m else 0
    files.sort(key=num)
    imgs = [Image.open(os.path.join(folder, f)).convert("RGBA") for f in files]
    # Filtra cuadros DEGENERADOS (fuentes corruptas, p. ej. rg_w_4.webp de 4x13 px):
    # se descarta todo cuadro cuyo alto recortado sea < 40% del maximo del set.
    heights = [(im.getbbox()[3] - im.getbbox()[1]) if im.getbbox() else 0 for im in imgs]
    hmax = max(heights) if heights else 0
    return [im for im, h in zip(imgs, heights) if hmax == 0 or h >= hmax * 0.4]

def pick(lst, i):
    """Elemento i (0-based) con ciclo si la lista es mas corta."""
    return lst[i % len(lst)]

def make_frame(img, scale, rot=0.0, squash_y=1.0, dx=0, anchor="feet", lift=0):
    """Recorta, escala, rota y ancla un cuadro fuente en el lienzo 256x256."""
    bbox = img.getbbox()
    if bbox:
        img = img.crop(bbox)
    w = max(1, int(round(img.width * scale)))
    h = max(1, int(round(img.height * scale * squash_y)))
    img = img.resize((w, h), Image.Resampling.LANCZOS)
    if rot:
        img = img.rotate(rot, expand=True, resample=Image.Resampling.BICUBIC)
    canvas = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    if anchor == "center":
        x = CENTER_X - img.width // 2 + dx
        y = 170 - img.height // 2
    else:
        x = CENTER_X - img.width // 2 + dx
        y = FEET_Y - img.height - lift
    canvas.paste(img, (x, y), img)
    return canvas

def generate(npc_folder, char, flip=False):
    # "PLAYER:<skin>" = set de jugador (SPRITES/PLAYER/<skin>{Idle,Walk,Run,Special}/);
    # sin prefijo = set NPC estandar (SPRITES/NPC/<Folder>/{Idle,Walk,Run,Special}/).
    # flip=True espeja horizontalmente (SF exige personaje mirando a la DERECHA;
    # lazaro y escomboy estan dibujados hacia la IZQUIERDA).
    if npc_folder.startswith("PLAYER:"):
        skin = npc_folder.split(":", 1)[1]
        pbase = os.path.join(BASE, "app/src/main/assets/SPRITES/PLAYER")
        def adir(a):
            return os.path.join(pbase, skin + a)
    else:
        src_dir = os.path.join(NPC_DIR, npc_folder)
        def adir(a):
            return os.path.join(src_dir, a)
    idle = load_anim(adir("Idle"))
    walk = load_anim(adir("Walk"))
    run = load_anim(adir("Run"))
    special = load_anim(adir("Special"))
    if flip:
        idle = [im.transpose(Image.Transpose.FLIP_LEFT_RIGHT) for im in idle]
        walk = [im.transpose(Image.Transpose.FLIP_LEFT_RIGHT) for im in walk]
        run = [im.transpose(Image.Transpose.FLIP_LEFT_RIGHT) for im in run]
        special = [im.transpose(Image.Transpose.FLIP_LEFT_RIGHT) for im in special]
    if not idle:
        sys.exit(f"ERROR: {npc_folder} no tiene Idle/")
    if not walk:
        walk = run or idle
    if not run:
        run = walk
    if not special:
        special = idle

    # Escala UNICA por personaje (proporciones consistentes entre cuadros)
    b = idle[0].getbbox()
    h0 = (b[3] - b[1]) if b else idle[0].height
    scale = TARGET_H / h0

    out_dir = os.path.join(GEN_DIR, char)
    os.makedirs(out_dir, exist_ok=True)
    frames = {}

    def F(img, **kw):
        return make_frame(img, scale, **kw)

    # ---- neutros ----
    frames["idle-1"] = F(pick(idle, 0))
    frames["idle-2"] = F(pick(idle, 1))
    frames["idle-3"] = F(pick(idle, 2))
    frames["idle-4"] = F(pick(idle, 1))
    for i in range(6):
        frames[f"forwards-{i+1}"] = F(pick(walk, i))
        frames[f"backwards-{i+1}"] = F(pick(walk, len(walk) - 1 - (i % len(walk))))
    frames["jump-start-land-1"] = F(pick(idle, 0), squash_y=0.85)
    for i in range(6):
        frames[f"jump-up-{i+1}"] = F(pick(run, i))
    # Voltereta: cuadro de carrera rotado (giro hacia adelante, sentido horario)
    for i in range(7):
        frames[f"jump-roll-{i+1}"] = F(pick(run, 2), rot=-(i * 51), anchor="center")
    frames["crouch-1"] = F(pick(idle, 0), squash_y=0.85)
    frames["crouch-2"] = F(pick(idle, 1), squash_y=0.70)
    frames["crouch-3"] = F(pick(idle, 2), squash_y=0.58)
    for i in range(3):
        frames[f"idle-turn-{i+1}"] = F(pick(idle, 2 - i))
        frames[f"crouch-turn-{i+1}"] = F(pick(idle, 2 - i), squash_y=0.58)

    # ---- ataques (aproximados con Special/Walk) ----
    frames["light-punch-1"] = F(pick(special, 0))
    frames["light-punch-2"] = F(pick(special, 1))
    frames["med-punch-1"] = F(pick(special, 0))
    frames["med-punch-2"] = F(pick(special, 1))
    frames["med-punch-3"] = F(pick(special, 2))
    frames["heavy-punch-1"] = F(pick(special, len(special) - 1))
    frames["light-kick-1"] = F(pick(special, 1))
    frames["light-kick-2"] = F(pick(special, 2))
    frames["med-kick-1"] = F(pick(special, len(special) - 1))
    for i in range(5):
        frames[f"heavy-kick-{i+1}"] = F(pick(special, i))

    # ---- reacciones (idle inclinado/aplastado) ----
    frames["hit-face-1"] = F(pick(idle, 0), rot=8, dx=-4)
    frames["hit-face-2"] = F(pick(idle, 1), rot=12, dx=-6)
    frames["hit-face-3"] = F(pick(idle, 2), rot=14, dx=-8)
    frames["hit-face-4"] = F(pick(idle, 1), rot=10, dx=-6)
    frames["hit-stomach-1"] = F(pick(idle, 1), rot=-6, dx=-4, squash_y=0.92)
    frames["hit-stomach-2"] = F(pick(idle, 1), rot=-10, dx=-6, squash_y=0.88)
    frames["hit-stomach-3"] = F(pick(idle, 2), rot=-12, dx=-6, squash_y=0.85)
    frames["hit-stomach-4"] = F(pick(idle, 2), rot=-8, dx=-4, squash_y=0.85)
    frames["stun-3"] = F(pick(idle, 1), rot=16)
    frames["fall-1"] = F(pick(idle, 0), rot=20, dx=-6)
    frames["fall-2"] = F(pick(idle, 0), rot=45, dx=-10)
    frames["fall-3"] = F(pick(idle, 0), rot=70, dx=-14)
    frames["fall-4"] = F(pick(idle, 0), rot=90)
    frames["fall-5"] = F(pick(idle, 0), rot=90)

    # ---- victoria / especial ----
    for i in range(4):
        frames[f"victory-{i+1}"] = F(pick(special, i))
        frames[f"special-{i+1}"] = F(pick(special, i))

    for key, img in frames.items():
        img.save(os.path.join(out_dir, f"{key}.png"), "PNG")
    print(f"{char}: {len(frames)} cuadros -> {out_dir}")

if __name__ == "__main__":
    # (carpeta NPC, nombre GEN)
    targets = [
        ("SenorTienda", "senortienda"),
        ("PaparazziN1", "paparazzi1"),
        ("PaparazziN5", "paparazzi5"),
        ("ReyGrupero", "reygrupero"),
    ]
    if len(sys.argv) > 2:
        targets = [(sys.argv[1], sys.argv[2])]
    for t in targets:
        # (carpeta, char) o (carpeta, char, flip)
        generate(t[0], t[1], flip=(len(t) > 2 and bool(t[2])) or "flip" in sys.argv[3:])
