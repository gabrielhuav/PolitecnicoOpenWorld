#!/usr/bin/env python3
"""Build La Planchada's single combat atlas and derived free-roam frames.

Sources are new RGBA sheets; only timings/boxes come from the dedicated moveset
contract. Nothing is installed until --install is explicitly supplied after QA.
"""
import argparse
import json
import shutil
from pathlib import Path

from PIL import Image

from sf_fighter_pipeline import ROOT, ASSETS, build, cell, check, read_recipe

RECIPE = Path(__file__).resolve().parent / "sf_fighters/laplanchada.json"
WORLD_ACTIONS = {
    "Idle": ("idle-relaxed", "i", 6),
    "Walk": ("forwards", "w", 6),
    "Run": ("run", "r", 8),
    "Special": ("special", "s", 5),
}


def world_sprite(image):
    image = image.copy()
    image.putalpha(image.getchannel("A").point(lambda alpha: alpha if alpha > 16 else 0))
    bbox = image.getchannel("A").getbbox()
    if bbox is None:
        raise ValueError("No se puede derivar un cuadro de mundo vacio")
    image = image.crop(bbox)
    # One scale for every action: a100 px standing SF body becomes360 px.
    # Bent/run/special poses retain their calibrated anatomy instead of growing.
    size = (max(1, round(image.width * 3.6)), max(1, round(image.height * 3.6)))
    image = image.convert("RGBa").resize(size, Image.Resampling.LANCZOS).convert("RGBA")
    image.putalpha(image.getchannel("A").point(lambda alpha: alpha if alpha > 16 else 0))
    image = image.crop(image.getchannel("A").getbbox())
    if image.width >= 510 or image.height >= 455:
        raise ValueError("La pose de mundo no cabe sin recorte; revisar el arte")
    result = Image.new("RGBA", (512, 512))
    result.alpha_composite(image, (256 - image.width // 2, 456 - image.height))
    return result


def build_world(output):
    data = json.loads((output / "DATA/laplanchada.json").read_text(encoding="utf-8"))
    atlas = Image.open(output / "IMAGES/LaPlanchada.webp").convert("RGBA")
    destination = output / "SPRITES/NPC/LaPlanchada"
    count = 0
    for action, (prefix, short, frames) in WORLD_ACTIONS.items():
        folder = destination / action
        folder.mkdir(parents=True, exist_ok=True)
        for index in range(1, frames + 1):
            image = world_sprite(cell(atlas, data["frames"][f"{prefix}-{index}"]))
            image.save(folder / f"lpl_{short}_{index}.webp", lossless=True, quality=100, method=6)
            count += 1
    return count


def check_world(output):
    errors = []
    data = json.loads((output / "DATA/laplanchada.json").read_text(encoding="utf-8"))
    atlas = Image.open(output / "IMAGES/LaPlanchada.webp").convert("RGBA")
    for action, (prefix, short, frames) in WORLD_ACTIONS.items():
        for index in range(1, frames + 1):
            path = output / "SPRITES/NPC/LaPlanchada" / action / f"lpl_{short}_{index}.webp"
            image = Image.open(path).convert("RGBA")
            bbox = image.getchannel("A").getbbox()
            if image.size != (512, 512) or bbox is None:
                errors.append(str(path) + ": lienzo o transparencia incorrectos")
            else:
                sf_box = cell(atlas, data["frames"][f"{prefix}-{index}"]).getchannel("A").getbbox()
                expected = (round((sf_box[2] - sf_box[0]) * 3.6), round((sf_box[3] - sf_box[1]) * 3.6))
                actual = (bbox[2] - bbox[0], bbox[3] - bbox[1])
                if (bbox[3] != 456 or abs((bbox[0] + bbox[2]) / 2 - 256) > 1
                        or any(abs(a - e) > 1 for a, e in zip(actual, expected))):
                    errors.append(str(path) + ": escala o anclaje fuera del estandar")
    return errors


def install(output):
    for folder, name in (("DATA", "laplanchada.json"), ("IMAGES", "LaPlanchada.webp")):
        shutil.copyfile(output / folder / name, ASSETS / folder / name)
    source = output / "SPRITES/NPC/LaPlanchada"
    target = ROOT / "PolitecnicoOpenWorld/app/src/main/assets/SPRITES/NPC/LaPlanchada"
    shutil.copytree(source, target, dirs_exist_ok=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ROOT / ".codex-tmp/la-planchada")
    parser.add_argument("--install", action="store_true", help="Copiar solo despues de revisar el QA visual")
    args = parser.parse_args()
    if args.output.resolve().is_relative_to((ROOT / "PolitecnicoOpenWorld/app/src").resolve()):
        parser.error("Construye en staging fuera de app/src")
    recipe = read_recipe(RECIPE)
    build(recipe, args.output)
    if check(recipe, args.output, args.output / "qa"):
        return 1
    count = build_world(args.output)
    errors = check_world(args.output)
    report = {"world_frames": count, "errors": errors}
    (args.output / "qa/laplanchada-world-report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2))
    if errors:
        return 1
    if args.install:
        install(args.output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
