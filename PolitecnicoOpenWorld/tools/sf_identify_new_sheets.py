#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""IDENTIFICA y RENOMBRA las hojas croma NUEVAS (20-29) de cada personaje.

Los nombres de descarga de ChatGPT ("ChatGPT Image ... (7).png") no dicen qué hoja son.
ChatGPT las entrega EN EL ORDEN del prompt, así que el índice de descarga (1..10) mapea a
las hojas 20..29 — pero eso hay que VERIFICARLO, no asumirlo: la herramienta recorta los
títulos amarillos de cada hoja y arma una TIRA por personaje para revisarla de un vistazo.

  (Se intentó identificar por template-matching de los títulos y NO es fiable: la fuente y
  el tamaño del rótulo cambian entre hojas y personajes. La verificación es visual.)

USO:
  # 1) generar las tiras de títulos (no toca nada) y ver qué personaje está incompleto:
  python tools/sf_identify_new_sheets.py <raiz> --titles
  # 2) renombrar al catálogo canónico (solo carpetas con las 10 hojas):
  python tools/sf_identify_new_sheets.py <raiz> --apply
  # 3) carpeta incompleta: dar el mapeo explícito tras mirar su tira
  python tools/sf_identify_new_sheets.py <raiz>/LaLlorona --apply --map 20,21,22,23,24,25,27,28,29

Salida de tiras: tools/_contact_sheets/titulos_<personaje>.png
"""
from __future__ import annotations

import argparse
import glob
import json
import os
import re
import sys

import numpy as np
from PIL import Image, ImageDraw

# Catálogo canónico (ver README for IAS/PROMPT_SOL56_TANDAS_NUEVAS.md)
SHEET_CATALOG = {
    20: ("Dash_Backdash", "DASH FORWARD + BACKDASH"),
    21: ("BlockAlto_BlockBajo", "BLOCK ALTO + BLOCK BAJO"),
    22: ("ParryAlto_ParryBajo", "PARRY ALTO + PARRY BAJO"),
    23: ("CrouchPunch_CrouchKick", "CROUCH PUNCH + CROUCH KICK"),
    24: ("CrouchAntiaereo_Barrida", "CROUCH HEAVY PUNCH + BARRIDA"),
    25: ("AirPunch_AirKick", "AIR PUNCH + AIR KICK"),
    26: ("PatadaLarga_Overhead", "PATADA LARGA + OVERHEAD"),
    27: ("AgarreLanzamiento_Taunt", "AGARRE Y LANZAMIENTO + TAUNT"),
    28: ("SerLanzado_Levantarse", "SER LANZADO + LEVANTARSE"),
    29: ("SuperArt_DanoAgachado", "SUPER ART + DAÑO AGACHADO"),
}
CANONICAL_ORDER = list(range(20, 30))
OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "_contact_sheets")

# Las carpetas de la tanda nueva no siempre se llaman igual que las CANONICAS de
# newSFAssets/<Personaje>/ (donde viven las hojas 01-19). El nombre canonico manda: es el
# prefijo de los archivos y el que espera el resto del pipeline.
FOLDER_ALIASES = {
    "Granadera Mujer": "PoliciaGranaderoFemeninoCDMX",
    "Granadero Hombre": "PoliciaGranaderoMasculinoCDMX",
    "ParemedicoCruzRoja": "ParamedicoCruzRoja",   # typo del nombre de descarga
    "PoliciaHombre": "PoliciaMasculinoCDMX",
    "PoliciaMujerCDMX": "PoliciaFemeninoCDMX",
}


def canonical_name(folder_name: str) -> str:
    return FOLDER_ALIASES.get(folder_name, folder_name)


def download_index(path: str) -> int:
    m = re.search(r"\((\d+)\)", os.path.basename(path))
    return int(m.group(1)) if m else 0


def sheets_of(folder: str) -> list[str]:
    return sorted(glob.glob(os.path.join(folder, "*.png")), key=download_index)


def title_crops(path: str) -> list[Image.Image]:
    """Recortes de los títulos amarillos (umbrales bajos: hay rótulos muy chicos)."""
    im = Image.open(path).convert("RGB")
    a = np.asarray(im).astype(int)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    yellow = (r > 195) & (g > 185) & (b < 110) & (np.abs(r - g) < 60)
    rows = np.where(yellow.sum(axis=1) > 5)[0]
    out = []
    if len(rows) == 0:
        return out
    for grp in np.split(rows, np.where(np.diff(rows) > 12)[0] + 1):
        if len(grp) < 3:
            continue
        y0, y1 = max(0, grp[0] - 3), min(a.shape[0], grp[-1] + 4)
        cols = np.where(yellow[y0:y1].sum(axis=0) > 0)[0]
        if len(cols) == 0:
            continue
        out.append(im.crop((max(0, cols[0] - 3), y0, min(a.shape[1], cols[-1] + 4), y1)))
    return out


def write_titles(folder: str) -> str:
    """Tira con los títulos de cada hoja (para verificar el orden a ojo)."""
    files = sheets_of(folder)
    char = os.path.basename(folder.rstrip(os.sep))
    W, TH = 1000, 26
    rows = [(download_index(f), title_crops(f)) for f in files]
    height = sum(max(1, len(c)) * (TH + 3) + 10 for _, c in rows) + 30
    sheet = Image.new("RGB", (W, height), (20, 20, 20))
    d = ImageDraw.Draw(sheet)
    # La fuente bitmap por defecto de PIL es latin-1: nada de guiones largos/acentos aquí.
    d.text((6, 6), f"{char}: {len(files)} hojas (orden de descarga = 20..29)",
           fill=(255, 255, 255))
    y = 26
    for idx, crops in rows:
        d.text((6, y + 6), f"[{idx}]", fill=(255, 255, 255))
        if not crops:
            d.text((60, y + 6), "(sin títulos detectados)", fill=(255, 80, 80))
            y += TH + 6
            continue
        for c in crops:
            scale = min((W - 70) / max(c.width, 1), TH / max(c.height, 1))
            c2 = c.resize((max(1, int(c.width * scale)), max(1, int(c.height * scale))),
                          Image.Resampling.LANCZOS)
            sheet.paste(c2, (60, y))
            y += c2.height + 3
        y += 9
    os.makedirs(OUT_DIR, exist_ok=True)
    out = os.path.join(OUT_DIR, f"titulos_{char.replace(' ', '_')}.png")
    sheet.crop((0, 0, W, min(y + 10, sheet.height))).save(out)
    return out


def apply_names(folder: str, mapping: list[int], move_to: str | None = None) -> list[dict]:
    """Renombra al catálogo canónico. Con `move_to`, además consolida en
    newSFAssets/<Personaje>/ (donde ya viven las hojas 01-19).
    Devuelve el manifiesto de movimientos (para poder deshacerlos)."""
    files = sheets_of(folder)
    raw = os.path.basename(folder.rstrip(os.sep))
    char = canonical_name(raw)
    if len(files) != len(mapping):
        sys.exit(f"{raw}: {len(files)} hojas pero el mapeo trae {len(mapping)}. "
                 f"Revisa tools/_contact_sheets/titulos_{raw}.png y pasa --map explícito.")
    dest_dir = folder
    if move_to:
        dest_dir = os.path.join(move_to, char)
        if not os.path.isdir(dest_dir):
            sys.exit(f"No existe la carpeta canónica {dest_dir} (¿nombre distinto? "
                     f"añádelo a FOLDER_ALIASES)")
    moves = []
    for src, num in zip(files, mapping):
        dst = os.path.join(dest_dir, f"{char}_{num}_{SHEET_CATALOG[num][0]}.png")
        if os.path.abspath(src) == os.path.abspath(dst):
            continue
        if os.path.exists(dst):
            sys.exit(f"Ya existe {dst}; abortado para no sobrescribir.")
        os.replace(src, dst)
        moves.append({"from": src, "to": dst})
    where = f" -> {dest_dir}" if move_to else ""
    print(f"{raw}: {len(files)} hojas como {char}_{mapping[0]}..{mapping[-1]}{where}")
    return moves


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("path", help="Raíz con subcarpetas por personaje, o una carpeta suelta")
    ap.add_argument("--titles", action="store_true", help="Solo generar tiras de títulos")
    ap.add_argument("--apply", action="store_true", help="Renombrar al catálogo canónico")
    ap.add_argument("--map", default=None,
                    help="Hojas explícitas separadas por coma (carpetas incompletas)")
    ap.add_argument("--move-to", default=None,
                    help="Raíz newSFAssets: consolida junto a las hojas 01-19")
    args = ap.parse_args()

    subdirs = sorted(d for d in glob.glob(os.path.join(args.path, "*")) if os.path.isdir(d))
    folders = subdirs or [args.path]
    explicit = [int(v) for v in args.map.split(",")] if args.map else None
    if explicit and len(folders) > 1:
        sys.exit("--map solo aplica a UNA carpeta de personaje")

    incomplete = []
    manifest = []
    for folder in folders:
        char = os.path.basename(folder.rstrip(os.sep))
        files = sheets_of(folder)
        out = write_titles(folder)
        flag = "" if len(files) == 10 else f"  <-- {len(files)} hojas (INCOMPLETA)"
        print(f"{char}: {len(files)} hojas -> {out}{flag}")
        if len(files) != 10 and not explicit:
            incomplete.append(char)
            continue
        if args.apply:
            manifest += apply_names(folder, explicit or CANONICAL_ORDER, args.move_to)
    if manifest:
        # Manifiesto = poder DESHACER el renombrado/movimiento sin adivinar
        path = os.path.join(OUT_DIR, "rename_manifest.json")
        old = json.load(open(path, encoding="utf-8")) if os.path.exists(path) else []
        with open(path, "w", encoding="utf-8") as f:
            json.dump(old + manifest, f, ensure_ascii=False, indent=2)
        print(f"\nManifiesto (para deshacer): {path}")
    if incomplete:
        print("\nINCOMPLETAS (renombrar con --map tras mirar su tira): " + ", ".join(incomplete))
    if not args.apply:
        print("\n(modo revisión: nada se renombró; usa --apply cuando las tiras cuadren)")


if __name__ == "__main__":
    main()
