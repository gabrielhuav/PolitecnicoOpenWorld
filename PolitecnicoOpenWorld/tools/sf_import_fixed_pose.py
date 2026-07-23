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
# 🆕 (2026-07-21) Detector NUEVO: el del slicer elegia "el primer grupo de columnas densas"
# y en los cuadros de poder se quedaba con el EFECTO, que es mas grande y brillante que la
# figura. El nuevo usa que el personaje PISA EL SUELO y los efectos flotan.
from sf_body_detect import body_center_x as dense_body_center_x  # noqa: E402
from sf_body_detect import body_height as _body_height  # noqa: E402

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

    # 🆕 (2026-07-21) ROTULOS del generador ("SPECIAL ULTIMATE", "SPECIAL FIRE"...) escritos
    # en AMARILLO sobre el croma. No son croma (r alto), asi que la mascara los tomaria como
    # figura y acabarian dentro del cuadro. El amarillo puro (r y g altos, b bajo) no aparece
    # en estos personajes salvo en los rotulos, y solo se borra en la BANDA SUPERIOR, que es
    # donde el generador los pone: asi un detalle dorado del cuerpo nunca se toca.
    amarillo = (r > 170) & (g > 150) & (b < 110) & (np.abs(r - g) < 90)
    banda = np.zeros_like(amarillo)
    banda[: int(amarillo.shape[0] * 0.30)] = True
    rotulo = amarillo & banda
    if rotulo.any():
        rotulo = ndimage.binary_dilation(rotulo, iterations=3)
        a[rotulo] = [0, 255, 0]          # se repinta de croma y desaparece
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

    rgb[rotulo] = [0, 0, 0] if rotulo.any() else rgb[rotulo]
    out = Image.fromarray(np.dstack([rgb, (mask * 255).astype(np.uint8)]), "RGBA")
    bb = out.getbbox()
    return out.crop(bb) if bb else out


def body_height(img: Image.Image) -> int:   # noqa: F811  (delega en sf_body_detect)
    return _body_height(img)


def _body_height_viejo(img: Image.Image) -> int:
    """Alto del CUERPO (sin contar el efecto), para poder escalar por el personaje.

    Escalar por la caja completa descuadra las poses con poder: en un cuadro donde el haz
    ocupa el doble de alto que la figura, el personaje sale diminuto; en el cuadro vecino
    sin efecto, enorme. Al lanzar se veria "crecer". Se mide la misma franja de columnas
    densas que usa `dense_body_center_x`.
    """
    a = np.asarray(img.convert("RGBA"))[..., 3] > 8
    if not a.any():
        return img.height
    counts = a.sum(axis=0)
    dense = counts >= max(3.0, float(counts.max()) * 0.45)
    grupos, ini = [], None
    for x, on in enumerate(dense):
        if on and ini is None:
            ini = x
        if ini is not None and (not on or x == len(dense) - 1):
            fin = x if not on else x + 1
            if fin - ini >= 2:
                grupos.append((ini, fin))
            ini = None
    if not grupos:
        return img.height
    x0, x1 = grupos[0]
    ys = np.where(a[:, x0:x1].any(axis=1))[0]
    return int(ys[-1] - ys[0] + 1) if len(ys) else img.height


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
    ap.add_argument("--alto-cuerpo", type=float, default=None,
                    help="escala para que el CUERPO mida estos px (p.ej. 100). Sin esto "
                         "usa la escala global de _scale.json, que mide la caja entera.")
    args = ap.parse_args()

    gen_dir = os.path.join(args.gen, args.char)
    scale_file = os.path.join(gen_dir, "_scale.json")
    if not os.path.exists(scale_file):
        raise SystemExit("falta %s (procesa antes la hoja 01)" % scale_file)
    scale = json.load(open(scale_file, encoding="utf-8"))["scale"]

    fig = cut_chroma(args.imagen)
    bh = body_height(fig)
    if args.alto_cuerpo:
        scale = args.alto_cuerpo / max(bh, 1)
    frame = place(fig, scale)
    alto_visible = frame.getchannel("A").getbbox()
    alto = alto_visible[3] - alto_visible[1] if alto_visible else 0

    for k in args.claves:
        dest = os.path.join(gen_dir, k + ".png")
        frame.save(dest)
        print("  %-12s -> %s" % (k, os.path.relpath(dest, os.path.dirname(ROOT))))
    print("  recorte %dx%d (cuerpo %d px)  ->  256x256, cuerpo final %d px, caja %d px"
          % (fig.width, fig.height, bh, round(bh * scale), alto))


if __name__ == "__main__":
    main()
