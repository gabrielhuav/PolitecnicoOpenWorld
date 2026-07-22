#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Extrae un TROZO de una hoja croma para que el dueno lo corrija a mano (2026-07-21).

Cuando dos o tres poses estan tan pegadas que el slicer no puede separarlas bien, la
solucion mas rapida no es pelear con el algoritmo: es que el dueno las separe a mano en
Paint. Esta herramienta le da exactamente el trozo que necesita, con el FONDO VERDE
ORIGINAL intacto (mismo #00FF00 de la hoja), para que al devolverlo el pipeline lo
reconozca sin cambiar nada.

Uso:
    python tools/sf_extract_sheet_region.py lallorona 29 --blobs 3 4 5
    python tools/sf_extract_sheet_region.py lapresidenta 29 --blobs 3 4

Los numeros de blob son los que imprime `slice_sf_chroma_sheets.py ... --list`.
Salida: tools/_para_corregir/<char>_hoja<NN>_blobs<a-b>.png

DEVOLVER el archivo corregido a la MISMA ruta y luego:
    python tools/sf_apply_fixed_region.py <archivo>
"""
from __future__ import annotations

import argparse
import io
import os
import re
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from slice_sf_chroma_sheets import SHEETS, detect, merge_fragments, maybe_split, split_groups

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(os.path.dirname(ROOT), "newSFAssets")
OUT_DIR = os.path.join(ROOT, "tools", "_para_corregir")

CARPETAS = {
    "charronegro": "CharroNegro", "escomboy": "ESCOMBOY", "escomgirl": "ESCOMGIRL",
    "robot": "ESCOMROBOT", "lallorona": "LaLlorona", "lapresidenta": "LaPresidenta",
    "latzitzimime": "LaTzitzimime", "paparazzi1": "Paparazzi1", "paparazzi5": "Paparazzi5",
    "paramedicocruzroja": "ParamedicoCruzRoja", "policiacdmx": "PoliciaFemeninoCDMX",
    "policiacdmxhombre": "PoliciaMasculinoCDMX",
    "policiagranaderohombre": "PoliciaGranaderoMasculinoCDMX",
    "policiagranaderomujer": "PoliciaGranaderoFemeninoCDMX",
    "prankedy": "Prankedy", "reygrupero": "ReyGrupero", "senortienda": "SenorTienda",
    "yoalliehecatl": "YoalliEhecatl",
}


def hoja_de(char: str, num: int) -> str:
    d = os.path.join(ASSETS, CARPETAS[char])
    for f in sorted(os.listdir(d)):
        if re.search(r"_%02d_" % num, f) and f.endswith(".png") and ".BAK." not in f:
            return os.path.join(d, f)
    raise SystemExit("no encuentro la hoja %02d de %s" % (num, char))


def main() -> None:
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
    ap = argparse.ArgumentParser()
    ap.add_argument("char")
    ap.add_argument("sheet", type=int)
    ap.add_argument("--blobs", type=int, nargs="+", required=True)
    ap.add_argument("--margen", type=int, default=40)
    args = ap.parse_args()

    path = hoja_de(args.char, args.sheet)
    (nameA, nA, _, _), (nameB, nB, _, _) = SHEETS[args.sheet]
    im, lbl, raw, bands = detect(path, close=5)
    A, B, _ = split_groups(bands, nA, nB)
    A = merge_fragments(A, nA)
    B = merge_fragments(B, nB)
    A = maybe_split(A, lbl, raw, nA)
    B = maybe_split(B, lbl, raw, nB)
    todos = A + B

    sel = [todos[i] for i in args.blobs if i < len(todos)]
    if len(sel) != len(args.blobs):
        raise SystemExit("solo hay %d blobs en esa hoja" % len(todos))

    m = args.margen
    x0 = max(0, min(b[0] for b in sel) - m)
    y0 = max(0, min(b[1] for b in sel) - m)
    x1 = min(im.width, max(b[2] for b in sel) + m)
    y1 = min(im.height, max(b[3] for b in sel) + m)

    os.makedirs(OUT_DIR, exist_ok=True)
    name = "%s_hoja%02d_blobs%s.png" % (
        args.char, args.sheet, "-".join(str(b) for b in args.blobs))
    out = os.path.join(OUT_DIR, name)
    # Se recorta de la hoja ORIGINAL: el fondo verde y los pixeles quedan tal cual.
    im.crop((x0, y0, x1, y1)).save(out)

    print("recortado de : %s" % os.path.basename(path))
    print("region       : x=%d..%d  y=%d..%d  (%dx%d px)" % (x0, x1, y0, y1, x1 - x0, y1 - y0))
    print("blobs         : %s" % ", ".join(
        "#%d x=%d..%d (ancho %d)" % (i, b[0], b[2], b[2] - b[0])
        for i, b in zip(args.blobs, sel)))
    print()
    print("ARCHIVO PARA CORREGIR A MANO:")
    print("  %s" % out)
    print()
    print("Separa las poses moviendolas horizontalmente y RELLENA el hueco con el MISMO")
    print("verde (#00FF00). Conserva el tamano del lienzo. Al devolverlo, avisa para")
    print("re-insertarlo en la hoja y re-recortar.")


if __name__ == "__main__":
    main()
