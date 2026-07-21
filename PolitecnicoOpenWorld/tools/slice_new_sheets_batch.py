#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Recorta EN LOTE las hojas nuevas (20-29) de todos los personajes y re-empaqueta.

Flujo completo de la tanda 5-8 (ver README for IAS/FLUJO_ASSETS_SF.md):
    1. sf_identify_new_sheets.py  -> renombra/consolida en newSFAssets/<Personaje>/
    2. ESTE script                -> recorta 20-29 al GEN y re-empaqueta atlas+JSON
    3. sf_contact_sheet.py        -> QA visual

La escala de cada personaje ya esta fijada por su hoja 01 (`_scale.json` en el GEN), asi
que las hojas nuevas se calibran contra ella: NO hay que reprocesar 01-19.

USO:
  python tools/slice_new_sheets_batch.py --gen <raiz_GEN> [--assets <raiz newSFAssets>]
      [--only escomboy,prankedy] [--no-pack]
"""
from __future__ import annotations

import argparse
import glob
import os
import re
import subprocess
import sys

TOOLS = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(TOOLS)

# Carpeta de arte (newSFAssets/<Personaje>) -> clave del personaje en GEN/DATA
CHAR_KEYS = {
    "CharroNegro": ("charronegro", "CharroNegro"),
    "ESCOMBOY": ("escomboy", "Escomboy"),
    "ESCOMGIRL": ("escomgirl", "Escomgirl"),
    "ESCOMROBOT": ("robot", "Robot"),
    "LaLlorona": ("lallorona", "LaLlorona"),
    "LaPresidenta": ("lapresidenta", "LaPresidenta"),
    "LaTzitzimime": ("latzitzimime", "LaTzitzimime"),
    "Paparazzi1": ("paparazzi1", "Paparazzi1"),
    "Paparazzi5": ("paparazzi5", "Paparazzi5"),
    "ParamedicoCruzRoja": ("paramedicocruzroja", "ParamedicoCruzRoja"),
    "PoliciaFemeninoCDMX": ("policiacdmx", "PoliciaCDMX"),
    "PoliciaGranaderoFemeninoCDMX": ("policiagranaderomujer", "PoliciaGranaderoMujer"),
    "PoliciaGranaderoMasculinoCDMX": ("policiagranaderohombre", "PoliciaGranaderoHombre"),
    "PoliciaMasculinoCDMX": ("policiacdmxhombre", "PoliciaCDMXHombre"),
    "Prankedy": ("prankedy", "Prankedy"),
    "ReyGrupero": ("reygrupero", "ReyGrupero"),
    "SenorTienda": ("senortienda", "SenorTienda"),
    "YoalliEhecatl": ("yoalliehecatl", "YoalliEhecatl"),
}
NEW_SHEETS = range(20, 30)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--gen", required=True, help="Raiz GEN con <char>/_scale.json")
    ap.add_argument("--assets", default=os.path.join(os.path.dirname(REPO), "newSFAssets"))
    ap.add_argument("--only", default=None, help="Lista de claves de personaje separadas por coma")
    ap.add_argument("--no-pack", action="store_true", help="Solo recortar, sin re-empaquetar")
    args = ap.parse_args()

    only = {v.strip() for v in args.only.split(",")} if args.only else None
    slicer = os.path.join(TOOLS, "slice_sf_chroma_sheets.py")
    packer = os.path.join(TOOLS, "pack_sf_character.py")

    ok, failed, missing = 0, [], []
    for folder, (key, title) in sorted(CHAR_KEYS.items()):
        if only and key not in only:
            continue
        char_dir = os.path.join(args.assets, folder)
        if not os.path.isdir(char_dir):
            print(f"[SKIP] {folder}: no existe {char_dir}")
            continue
        if not os.path.exists(os.path.join(args.gen, key, "_scale.json")):
            print(f"[SKIP] {key}: falta _scale.json (procesa antes su hoja 01)")
            continue
        print(f"\n=== {folder} ({key}) ===")
        for num in NEW_SHEETS:
            hits = glob.glob(os.path.join(char_dir, f"*_{num}_*.png"))
            if not hits:
                missing.append(f"{key}:{num}")
                print(f"  hoja {num}: FALTA")
                continue
            cmd = [sys.executable, slicer, hits[0], key, "--gen", args.gen,
                   "--sheet-num", str(num)]
            res = subprocess.run(cmd, capture_output=True, text=True, cwd=REPO)
            head = (res.stdout or res.stderr).strip().splitlines()
            if res.returncode != 0:
                failed.append(f"{key}:{num}")
                print(f"  hoja {num}: ERROR -> {head[-1] if head else res.returncode}")
            else:
                ok += 1
                print(f"  hoja {num}: {head[0] if head else 'ok'}")
        if not args.no_pack:
            res = subprocess.run([sys.executable, packer, key, title, "--gen", args.gen],
                                 capture_output=True, text=True, cwd=REPO)
            state = "OK" if res.returncode == 0 else "ERROR"
            print(f"  pack {key}: {state}")
            if res.returncode != 0:
                failed.append(f"pack:{key}")
                print("   " + (res.stderr or res.stdout).strip().splitlines()[-1])

    print(f"\n=== RESUMEN === hojas recortadas OK: {ok}")
    if missing:
        print(f"hojas FALTANTES ({len(missing)}): {', '.join(missing)}")
    if failed:
        print(f"FALLOS ({len(failed)}): {', '.join(failed)}")


if __name__ == "__main__":
    main()
