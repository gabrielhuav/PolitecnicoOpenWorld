#!/usr/bin/env python3
"""Valida el contrato final de un personaje generado por hojas croma.

Uso desde la raiz del proyecto Android:
  python tools/validate_sf_chroma_character.py <char> <TituloSheet> <CarpetaMundo> <prefijo>

Ejemplo:
  python tools/validate_sf_chroma_character.py policiagranaderohombre \
      PoliciaGranaderoHombre PoliciaGranaderoMasculinoCDMX pgm_

Protagonista con carpetas antiguas escomboyIdle/escomboyWalk:
  python tools/validate_sf_chroma_character.py escomboy EscomBoy escomboy escomboy_ \
      --world-base SPRITES/PLAYER --flat-world-folders
"""

import argparse
import json
import statistics
import sys
from pathlib import Path

from PIL import Image


WORLD_COUNTS = {"Idle": 6, "Walk": 6, "Run": 8, "Special": 5, "Talk": 4}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("char", help="id minusculo del JSON, por ejemplo policiagranaderohombre")
    ap.add_argument("title", help="nombre PascalCase del PNG, por ejemplo PoliciaGranaderoHombre")
    ap.add_argument("world_folder", help="carpeta bajo SPRITES/NPC")
    ap.add_argument("prefix", help="prefijo de frames del mundo, con guion bajo final")
    ap.add_argument("--assets-root", default="app/src/main/assets")
    ap.add_argument("--world-base", default="SPRITES/NPC",
                    help="base relativa a assets; usa SPRITES/PLAYER para protagonistas")
    ap.add_argument("--flat-world-folders", action="store_true",
                    help="convencion antigua <folder>Idle/<folder>Walk en vez de <folder>/Idle")
    args = ap.parse_args()

    assets = Path(args.assets_root)
    sheet_path = assets / "STREETFIGHTER" / "IMAGES" / (args.title + ".png")
    json_path = assets / "STREETFIGHTER" / "DATA" / (args.char + ".json")
    world_base = assets / Path(args.world_base)
    world = world_base / args.world_folder
    errors = []

    if not sheet_path.is_file():
        errors.append("Falta sheet: %s" % sheet_path)
    elif Image.open(sheet_path).size != (2560, 3328):
        errors.append("Sheet no mide 2560x3328: %s" % (Image.open(sheet_path).size,))

    data = None
    if not json_path.is_file():
        errors.append("Falta JSON: %s" % json_path)
    else:
        data = json.loads(json_path.read_text(encoding="utf-8"))
        frames = data.get("frames", {})
        animations = data.get("animations", {})
        projectiles = [key for key in frames if key.startswith("proj-")]
        events = data.get("events", {}).get("projectile", {})
        if len(frames) != 123:
            errors.append("JSON tiene %d frames; deben ser 123" % len(frames))
        if len(animations) != 30:
            errors.append("JSON tiene %d animaciones; deben ser 30" % len(animations))
        if len(projectiles) != 5:
            errors.append("JSON tiene %d proj-*; deben ser 5" % len(projectiles))
        if set(events) != {"light", "medium", "heavy"}:
            errors.append("events.projectile debe declarar light/medium/heavy")

    print("SF sheet:", sheet_path)
    if data:
        flips = sorted(key for key, value in data["frames"].items() if value.get("flipX"))
        print("SF JSON : %d frames, %d animaciones, flipX=%s" %
              (len(data["frames"]), len(data["animations"]), flips or "ninguno"))

    for animation, expected in WORLD_COUNTS.items():
        folder = (world_base / (args.world_folder + animation)
                  if args.flat_world_folders else world / animation)
        files = sorted(folder.glob("*.webp")) if folder.is_dir() else []
        if len(files) != expected:
            errors.append("%s tiene %d cuadros; debe tener %d" % (folder, len(files), expected))
        heights = []
        for path in files:
            if not path.name.startswith(args.prefix):
                errors.append("Prefijo incorrecto: %s" % path)
            image = Image.open(path).convert("RGBA")
            if image.size != (512, 512):
                errors.append("Lienzo no es 512x512: %s = %s" % (path, image.size))
                continue
            bbox = image.getchannel("A").getbbox()
            if bbox is None:
                errors.append("Cuadro transparente: %s" % path)
                continue
            heights.append(bbox[3] - bbox[1])
            if bbox[3] != 456:
                errors.append("Pies fuera de Y=456: %s termina en %d" % (path, bbox[3]))
        median = statistics.median(heights) if heights else 0
        # Special puede exceder 360 por rayos/objetos; las otras acciones deben conservar cuerpo base.
        if animation != "Special" and heights and not 340 <= median <= 380:
            errors.append("Mediana corporal anormal en %s: %.1f px" % (animation, median))
        print("POW %-7s: %d/%d, mediana alfa %.1f px" %
              (animation, len(files), expected, median))

    if (assets / "STREETFIGHTER" / "GEN").exists():
        errors.append("STREETFIGHTER/GEN sigue dentro de assets y entraria al APK")

    if errors:
        print("\nVALIDACION FALLIDA:")
        for error in errors:
            print("-", error)
        return 1
    print("\nVALIDACION OK: contrato croma completo.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
