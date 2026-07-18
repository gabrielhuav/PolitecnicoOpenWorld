#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Recorta los sprite sheets COMPLETOS de un NPC (1 personaje por imagen: walk / run / fight)
y los normaliza al ESTANDAR de POW para los NPCs ambientales de interior.

ESTANDAR (NO CAMBIAR — para que TODOS los NPCs midan igual y se animen suaves):
  - Lienzo FIJO  CW x CH  = 193 x 249 px.
  - La figura se ESCALA para que su ALTO de CAMINAR sea STD_BODY_H (200 px), usando el
    MISMO factor para todas las animaciones del personaje (preserva proporciones de poses).
  - Alineada: centrada horizontal, PIES ABAJO (margen PAD/2). Asi no "salta" al animar.
  - walkBodyFraction = STD_BODY_H / CH = 0.803  -> va TAL CUAL en la entrada PlayerSkin.

ENTRADA: 3 hojas PNG con fondo TRANSPARENTE, una por animacion, en grid LIMPIO (figuras
separadas, sin tocarse). Los nombres deben contener 'walk', 'run' (o 'wrun') y 'fight'.

SALIDA: <out_dir>/<Walk|Run|Special|Idle>/<prefix><w|r|s|i>_<N>.webp
        (Idle = copia del frame 1 de caminar.)

USO:
  pip install pillow numpy scipy
  python tools/slice_npc_standard.py <sheets_dir> <name_filter> <out_dir> <prefix>

  <name_filter> = subcadena para filtrar el personaje cuando hay VARIOS en la carpeta
                  (p.ej. "ipn 7-"). Usa "" si solo hay un personaje en la carpeta.

EJEMPLOS:
  # estudiante IPN 7 (su carpeta solo tiene sus 3 hojas):
  python tools/slice_npc_standard.py \
    app/src/main/assets/SPRITES/NPC/NPCS/NPCSIPN/Ipn7/spreadsheet "" \
    app/src/main/assets/SPRITES/NPC/NPCS/NPCSIPN/Ipn7 ipn7_
  # dos personajes en una misma carpeta -> filtra por nombre:
  python tools/slice_npc_standard.py <dir> "ipn 8-" <out_Ipn8> ipn8_

Tras correrlo, imprime los frame counts + walkBodyFraction listos para la entrada
PlayerSkin. Ver README for IAS/NPC_SPRITES_PIPELINE.md para el cableado de codigo.
"""
import sys, os
import numpy as np
from PIL import Image
from scipy import ndimage

# ───────────────────────── ESTANDAR POW (NO CAMBIAR) ─────────────────────────
CW, CH = 193, 249          # lienzo fijo (px)
STD_BODY_H = 200.0         # alto objetivo de la figura de CAMINAR
PAD = 18                   # margen; pies se anclan a  CH - nh - PAD//2
WALK_BODY_FRACTION = round(STD_BODY_H / CH, 3)   # 0.803 -> PlayerSkin.walkBodyFraction


def _cluster(vals, gap):
    vals = sorted(vals); out = []; cur = [vals[0]]
    for v in vals[1:]:
        if v - cur[-1] <= gap: cur.append(v)
        else: out.append(cur); cur = [v]
    out.append(cur)
    return [sum(c) // len(c) for c in out]


def detect_frames(png):
    """Cada figura = componente conexa por alpha. Orden de lectura: fila (y), luego x.
    Descarta ruido (muy chico) y blobs GIGANTES (fusiones/sheet completo)."""
    im = Image.open(png).convert("RGBA"); arr = np.array(im); A = arr[:, :, 3]; H, W = A.shape
    lab, _ = ndimage.label(A > 30); objs = ndimage.find_objects(lab); bl = []
    for s in objs:
        if not s: continue
        y0, y1, x0, x1 = s[0].start, s[0].stop, s[1].start, s[1].stop
        h, w = y1 - y0, x1 - x0
        if h >= 70 and w >= 25 and h < 0.6 * H and w < 0.5 * W:
            bl.append((y0, y1, x0, x1))
    rows = _cluster([(b[0] + b[1]) // 2 for b in bl], 60)
    bl.sort(key=lambda b: (min(range(len(rows)), key=lambda i: abs((b[0] + b[1]) // 2 - rows[i])),
                           (b[2] + b[3]) // 2))
    out = []
    for (y0, y1, x0, x1) in bl:
        sub = arr[y0:y1, x0:x1]; m = sub[:, :, 3] > 25
        ys = np.where(m.any(1))[0]; xs = np.where(m.any(0))[0]
        if len(ys) and len(xs):
            out.append(sub[ys[0]:ys[-1] + 1, xs[0]:xs[-1] + 1])   # recorte tight de la figura
    return out


def place(crop, scale):
    """Escala la figura (factor del personaje) y la pega centrada / pies-abajo en el lienzo
    estandar. Clamp por si una pose se sale (brazo levantado / zancada ancha)."""
    nw = max(1, int(round(crop.shape[1] * scale))); nh = max(1, int(round(crop.shape[0] * scale)))
    f = min(1.0, (CW - 2) / nw, (CH - PAD // 2 - 2) / nh); nw = max(1, int(nw * f)); nh = max(1, int(nh * f))
    im = Image.fromarray(crop, "RGBA").resize((nw, nh))
    cv = Image.new("RGBA", (CW, CH), (0, 0, 0, 0))
    cv.alpha_composite(im, ((CW - nw) // 2, CH - nh - PAD // 2))
    return cv


def find_sheets(sheets_dir, name_filter):
    res = {}; nf = name_filter.lower()
    for f in os.listdir(sheets_dir):
        lf = f.lower()
        if not lf.endswith(".png") or (nf and nf not in lf): continue
        for key in ("walk", "run", "fight"):
            if key in lf: res[key] = os.path.join(sheets_dir, f)   # 'run' tambien matchea 'wrun'
    return res


def slice_character(sheets_dir, name_filter, out_dir, prefix):
    sh = find_sheets(sheets_dir, name_filter)
    missing = [a for a in ("walk", "run", "fight") if a not in sh]
    if missing:
        raise SystemExit(f"Faltan hojas {missing} en {sheets_dir} (filtro '{name_filter}')")
    data = {a: detect_frames(sh[a]) for a in ("walk", "run", "fight")}
    if not data["walk"]:
        raise SystemExit("No detecte figuras de WALK (revisa transparencia / grid)")
    scale = STD_BODY_H / np.median([c.shape[0] for c in data["walk"]])
    for a, sub, lt in (("walk", "Walk", "w"), ("run", "Run", "r"), ("fight", "Special", "s")):
        os.makedirs(os.path.join(out_dir, sub), exist_ok=True)
        for i, crop in enumerate(data[a], 1):
            place(crop, scale).save(os.path.join(out_dir, sub, f"{prefix}{lt}_{i}.webp"),
                                    "WEBP", quality=88, method=4)
    os.makedirs(os.path.join(out_dir, "Idle"), exist_ok=True)
    place(data["walk"][0], scale).save(os.path.join(out_dir, "Idle", f"{prefix}i_1.webp"),
                                       "WEBP", quality=88, method=4)
    w, r, s = len(data["walk"]), len(data["run"]), len(data["fight"])
    print(f"OK  {out_dir}")
    print(f"    walk={w}  run={r}  special={s}")
    print(f"    PlayerSkin -> idleFrames = 1, walkFrames = {w}, runFrames = {r}, "
          f"specialFrames = {s},  walkBodyFraction = {WALK_BODY_FRACTION}f")
    return (w, r, s)


if __name__ == "__main__":
    if len(sys.argv) != 5:
        print("uso: python slice_npc_standard.py <sheets_dir> <name_filter> <out_dir> <prefix>")
        sys.exit(1)
    slice_character(sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4])
