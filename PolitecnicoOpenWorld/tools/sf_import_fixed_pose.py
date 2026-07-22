#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Importa una pose corregida A MANO al formato estandar del pipeline (2026-07-21).

Cuando el slicer no logra separar dos poses pegadas, el dueno las arregla en Paint sobre
el recorte croma y devuelve o bien UNA pose por archivo, o bien varias poses fundidas en
una sola imagen (cuando en realidad eran un unico asset). Esta herramienta convierte esa
imagen suelta en un cuadro que cumple EXACTAMENTE el mismo contrato que produce el
slicer, para que el packer no note la diferencia.

CONTRATO DE UN CUADRO (el mismo de slice_sf_chroma_sheets.py):
  - lienzo 256x256 RGBA transparente
  - pies apoyados en y=224, figura centrada en x=128
  - escala tomada de GEN/<char>/_scale.json (la que fijo la hoja 01), asi el personaje
    NO cambia de tamano entre animaciones
  - el alfa sale de la mascara CRUDA del croma (sin verde interior) y se limpia el
    derrame verde del borde

Uso:
    python tools/sf_import_fixed_pose.py <char> <archivo.png> <clave> [<clave> ...]

Varias claves = la MISMA imagen se escribe en todas (una pose que ocupa varios cuadros).

    python tools/sf_import_fixed_pose.py lallorona tools/_para_corregir/lallorona_1.png super-4
    python tools/sf_import_fixed_pose.py escomboy  ...escomboy.png super-5 super-6 super-7
"""
from __future__ import annotations

import argparse
import io
import json
import os
import sys

import numpy as np
from PIL import Image
from scipy import ndimage

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from slice_sf_chroma_sheets import dense_body_center_x  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GEN = os.path.join(os.path.dirname(ROOT), "newSFAssets",
                   "GEN_prankedy_senortienda_rey_paparazzi_fullcombat_intermedio")
CANVAS, FEET_Y, CX = 256, 224, 128


def cut_chroma(path: str, despill: bool = True) -> Image.Image:
    """Figura = NO croma verde, con limpieza AGRESIVA del verde residual.

    El slicer solo limpia 2 px de contorno, y eso basta para una silueta dura. Pero un
    AURA CIRCULAR tiene el borde suavizado: esos pixeles quedan fuera del umbral del
    croma (siguen siendo "figura") y sobreviven como HALO VERDE. Aqui se hacen dos pasadas:

      1. Recorte del alfa: lo que es croma casi puro se descarta aunque el antialias lo
         haya aclarado un poco (umbral mas permisivo que el del slicer).
      2. DESPILL global sobre TODA la figura, no solo el borde: allí donde el verde
         domina sobre rojo y azul se le baja al maximo de los otros dos canales. Es el
         mismo truco del croma de video: quita el tinte sin tocar los colores legitimos
         (un verde real del dibujo tiene g alto PERO tambien r o b altos).
    """
    im = Image.open(path).convert("RGB")
    a = np.asarray(im).astype(int)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]

    # 1) mascara: croma con tolerancia amplia para no dejar el halo del antialias
    croma = (g > 150) & (g > r + 45) & (g > b + 45)
    mask = ~croma
    mask = ndimage.binary_fill_holes(
        ndimage.binary_closing(mask, structure=np.ones((5, 5))))
    # descarta motas sueltas que quedan del fondo
    lbl, n = ndimage.label(mask, structure=np.ones((3, 3)))
    if n > 1:
        tam = ndimage.sum(mask, lbl, range(1, n + 1))
        mask = np.isin(lbl, [i + 1 for i, s in enumerate(tam) if s >= 0.02 * tam.max()])

    rgb = np.asarray(im).copy()
    if despill:
        # 2) despill: g no puede superar al mayor de r/b alli donde el verde tiñe
        techo = np.maximum(r, b)
        tenido = mask & (g > techo)
        rgb[..., 1] = np.where(tenido, techo, g).astype(np.uint8)

    out = Image.fromarray(np.dstack([rgb, (mask * 255).astype(np.uint8)]), "RGBA")
    bb = out.getbbox()
    return out.crop(bb) if bb else out


def place(img: Image.Image, scale: float, anchor_body: bool = True) -> Image.Image:
    """Coloca la pose en el lienzo estandar.

    ⚠️ `anchor_body` es lo que evita el fallo clasico de las poses con poder: centrar la
    CAJA ENTERA (cuerpo + haz) deja al personaje descolocado hacia el lado contrario al
    efecto, y en el motor se ve que "salta" al lanzar. `dense_body_center_x` se queda con
    el primer grupo denso de columnas — el cuerpo — e ignora el efecto que sale de el.
    """
    body_cx = dense_body_center_x(img) * scale if anchor_body else None
    w = max(1, int(round(img.width * scale)))
    h = max(1, int(round(img.height * scale)))
    img = img.resize((w, h), Image.Resampling.LANCZOS)
    cv = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    x = int(round(CX - body_cx)) if body_cx is not None else (CX - w // 2)
    cv.paste(img, (x, FEET_Y - h), img)
    return cv


def main() -> None:
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
    ap = argparse.ArgumentParser()
    ap.add_argument("char")
    ap.add_argument("imagen")
    ap.add_argument("claves", nargs="+")
    ap.add_argument("--gen", default=GEN)
    args = ap.parse_args()

    gen_dir = os.path.join(args.gen, args.char)
    scale_file = os.path.join(gen_dir, "_scale.json")
    if not os.path.exists(scale_file):
        raise SystemExit("falta %s (procesa antes la hoja 01)" % scale_file)
    scale = json.load(open(scale_file, encoding="utf-8"))["scale"]

    fig = cut_chroma(args.imagen)
    frame = place(fig, scale)
    alto_visible = frame.getchannel("A").getbbox()
    alto = alto_visible[3] - alto_visible[1] if alto_visible else 0

    for k in args.claves:
        dest = os.path.join(gen_dir, k + ".png")
        frame.save(dest)
        print("  %-12s -> %s" % (k, os.path.relpath(dest, os.path.dirname(ROOT))))
    print("  figura recortada %dx%d  ->  lienzo 256x256, alto visible %d px"
          % (fig.width, fig.height, alto))


if __name__ == "__main__":
    main()
