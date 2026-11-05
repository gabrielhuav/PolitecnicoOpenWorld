#!/usr/bin/env python3
"""Construye y valida un peleador en staging; no borra ni publica archivos.

Desde la raiz del repo: python PolitecnicoOpenWorld/tools/sf_fighter_pipeline.py --help
"""
import argparse
import importlib.util
import json
import re
import subprocess
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
TOOLS = Path(__file__).resolve().parent
ASSETS = ROOT / "PolitecnicoOpenWorld/app/src/main/assets/STREETFIGHTER"
UPRIGHT = ("idle-", "forwards-", "backwards-", "light-punch-", "med-punch-",
           "heavy-punch-", "light-kick-", "med-kick-", "heavy-kick-",
           "hit-face-", "hit-stomach-", "stun-")


def read_recipe(path):
    recipe = json.loads(Path(path).read_text(encoding="utf-8"))
    if not re.fullmatch(r"[a-z0-9_]+", recipe["character"]):
        raise ValueError("Id de personaje invalido")
    if not re.fullmatch(r"[A-Za-z0-9_]+", recipe["title"]):
        raise ValueError("Titulo de atlas invalido")
    return recipe


def cell(atlas, frame):
    x, y, w, h = frame["src"]
    return atlas.crop((x, y, x + w, y + h))


def is_projectile(key):
    return key.startswith(("proj-", "bonus-proj-", "fx-"))


def normalize_packed(atlas, data):
    result = Image.new("RGBA", atlas.size)
    for key, frame in data["frames"].items():
        target = 100 if key.startswith(UPRIGHT) and "turn" not in key else None
        normalized = place(cell(atlas, frame), target, is_projectile(key))
        result.alpha_composite(normalized, tuple(frame["src"][:2]))
        frame["origin"] = [128, 128] if is_projectile(key) else [128, 224]
    return result


def place(image, target_height=None, projectile=False, target_width=None):
    image = image.copy()
    image.putalpha(image.getchannel("A").point(lambda alpha: alpha if alpha > 16 else 0))
    bbox = image.getchannel("A").getbbox()
    if bbox is None:
        raise ValueError("No se puede normalizar un cuadro vacio")
    image = image.crop(bbox)
    if target_width is not None:
        target_height = max(1, round(image.height * target_width / image.width))
    if target_height is not None:
        size = (max(1, round(image.width * target_height / image.height)), target_height)
        # RGBa premultiplica antes de interpolar; evita halos del fondo en el borde.
        image = image.convert("RGBa").resize(size, Image.Resampling.LANCZOS).convert("RGBA")
        image.putalpha(image.getchannel("A").point(lambda alpha: alpha if alpha > 16 else 0))
        image = image.crop(image.getchannel("A").getbbox())
        if image.height != target_height:
            # Un borde de alfa tenue puede desaparecer al interpolar. Ajuste final de
            # como maximo unos pixeles, sin introducir otro halo de interpolacion.
            image = image.resize((max(1, round(image.width * target_height / image.height)),
                                  target_height), Image.Resampling.NEAREST)
    x = 128 - image.width // 2
    y = 128 - image.height // 2 if projectile else 224 - image.height
    if x < 1 or y < 1 or x + image.width >= 256 or y + image.height >= 256:
        raise ValueError("El arte no cabe sin cortar: regenera la pose")
    canvas = Image.new("RGBA", (256, 256))
    canvas.alpha_composite(image, (x, y))
    return canvas


def build(recipe, output):
    images, data_dir = output / "IMAGES", output / "DATA"
    images.mkdir(parents=True, exist_ok=True)
    data_dir.mkdir(parents=True, exist_ok=True)
    if recipe["mode"] == "chroma":
        gen = output / "GEN"
        for sheet in recipe["sheets"]:
            command = [sys.executable, str(TOOLS / "slice_sf_chroma_sheets.py"),
                       str(ROOT / sheet), recipe["character"], "--gen", str(gen)]
            dry = subprocess.run(command + ["--list"], capture_output=True, text=True,
                                 check=True)
            print(dry.stdout)
            if "AVISO" in dry.stdout:
                raise ValueError("Conteo inesperado: revisar hoja antes de recortar")
            subprocess.run(command, check=True)
        spec = importlib.util.spec_from_file_location("sf_packer", TOOLS / "pack_sf_character.py")
        packer = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(packer)
        packer.IMAGES_DIR, packer.DATA_DIR = str(images), str(data_dir)
        packer.pack_character(recipe["character"], recipe["title"], str(gen))
        atlas_path = images / (recipe["title"] + ".webp")
        data_path = data_dir / (recipe["character"] + ".json")
        data = json.loads(data_path.read_text(encoding="utf-8"))
        result = normalize_packed(Image.open(atlas_path).convert("RGBA"), data)
        result.save(atlas_path, lossless=True, quality=100, method=6)
        data_path.write_text(json.dumps(data, separators=(",", ":")), encoding="utf-8")
        return
    if recipe["mode"] != "atlas-repair":
        raise ValueError("Modo desconocido")
    data = json.loads((ROOT / recipe["source_data"]).read_text(encoding="utf-8"))
    atlas = Image.open(ROOT / recipe["source_atlas"]).convert("RGBA")
    repairs = Image.open(ROOT / recipe["repair_sheet"]).convert("RGBA")
    columns, rows = recipe["repair_grid"]
    repaired = recipe["replacement_cells"]
    result = Image.new("RGBA", atlas.size)
    for key, frame in data["frames"].items():
        image = cell(atlas, frame)
        target = 100 if key.startswith(UPRIGHT) and "turn" not in key else None
        if key in repaired:
            index = repaired[key]
            left = (index % columns) * repairs.width // columns
            top = (index // columns) * repairs.height // rows
            right = (index % columns + 1) * repairs.width // columns
            bottom = (index // columns + 1) * repairs.height // rows
            image = repairs.crop((left, top, right, bottom))
            # El generador deja polvo casi invisible en zonas transparentes.
            # Se conserva la fuente intacta y se limpia solo al recortar.
            image.putalpha(image.getchannel("A").point(lambda alpha: alpha if alpha > 16 else 0))
            target = recipe["replacement_heights"].get(key, 100)
        projectile = is_projectile(key)
        target = recipe.get("frame_heights", {}).get(key, target)
        normalized = place(image, target, projectile, recipe.get("frame_widths", {}).get(key))
        result.alpha_composite(normalized, tuple(frame["src"][:2]))
        frame["origin"] = [128, 128] if projectile else [128, 224]
    result.save(images / (recipe["title"] + ".webp"), lossless=True, quality=100, method=6)
    (data_dir / (recipe["character"] + ".json")).write_text(
        json.dumps(data, separators=(",", ":")), encoding="utf-8")


def check(recipe, assets, report):
    errors = []
    data = json.loads((assets / "DATA" / (recipe["character"] + ".json")).read_text(encoding="utf-8"))
    atlas = Image.open(assets / "IMAGES" / (recipe["title"] + ".webp")).convert("RGBA")
    frames = data["frames"]
    if not frames or not data["animations"].get("idle"):
        errors.append("Faltan cuadros o animacion idle")
    used = set()
    preview = Image.new("RGB", (8 * 144, ((len(frames) + 7) // 8) * 152), "#303040")
    draw = ImageDraw.Draw(preview)
    for index, (key, frame) in enumerate(frames.items()):
        x, y, w, h = frame["src"]
        if (w, h) != (256, 256) or min(x, y) < 0 or x + w > atlas.width or y + h > atlas.height:
            errors.append(key + ": celda fuera del atlas o distinta de 256x256")
            continue
        if (x, y) in used:
            errors.append(key + ": celda compartida por dos claves")
        used.add((x, y))
        projectile = is_projectile(key)
        if frame.get("origin") != ([128, 128] if projectile else [128, 224]):
            errors.append(key + ": origen incorrecto")
        image = cell(atlas, frame)
        bbox = image.getchannel("A").getbbox()
        if bbox is None:
            errors.append(key + ": cuadro vacio")
            continue
        left, top, right, bottom = bbox
        if left == 0 or top == 0 or right == 256 or bottom == 256:
            errors.append(key + ": arte toca el borde; posible recorte incompleto")
        if not projectile and bottom != 224:
            errors.append(key + ": pies fuera de y=224")
        expected_height = recipe.get("frame_heights", {}).get(key)
        expected_width = recipe.get("frame_widths", {}).get(key)
        if expected_height is None and key.startswith(UPRIGHT) and "turn" not in key:
            expected_height = 100
        if expected_height is not None and bottom - top != expected_height:
            errors.append(key + ": altura distinta de la calibracion de cuerpo")
        if expected_width is not None and abs(right - left - expected_width) > 1:
            errors.append(key + ": longitud distinta de la calibracion de cuerpo")
        if key.startswith("talk-") and bottom - top < 80:
            errors.append(key + ": posible busto sin cuerpo completo")
        if abs((left + right) / 2 - 128) > 1 and not projectile:
            errors.append(key + ": silueta descentrada")
        px, py = index % 8 * 144, index // 8 * 152
        small = image.resize((128, 128))
        preview.paste(small, (px + 8, py), small)
        draw.line((px, py + 112, px + 143, py + 112), fill="#59665a")
        draw.text((px + 2, py + 130), key, fill="white")
    for name, animation in data["animations"].items():
        for key, duration in animation:
            if key not in frames:
                errors.append(name + ": referencia inexistente " + key)
            if not isinstance(duration, int) or duration < -1:
                errors.append(name + ": duracion invalida")
    report.mkdir(parents=True, exist_ok=True)
    preview.save(report / (recipe["character"] + "-contact.png"))
    result = {"character": recipe["character"], "frames": len(frames),
              "animations": len(data["animations"]), "errors": errors}
    (report / (recipe["character"] + "-report.json")).write_text(
        json.dumps(result, indent=2, ensure_ascii=False), encoding="utf-8")
    print(json.dumps(result, indent=2, ensure_ascii=True))
    return bool(errors)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("build", "check"))
    parser.add_argument("recipe", type=Path)
    parser.add_argument("--output", type=Path, default=ROOT / ".codex-tmp/sf-preview")
    parser.add_argument("--assets", type=Path, default=ASSETS)
    parser.add_argument("--report", type=Path, default=ROOT / ".codex-tmp/sf-qa")
    args = parser.parse_args()
    recipe = read_recipe(args.recipe)
    if args.command == "build":
        if args.output.resolve().is_relative_to((ROOT / "PolitecnicoOpenWorld/app/src").resolve()):
            parser.error("Construye en staging fuera de app/src; revisa antes de integrar")
        build(recipe, args.output)
        return check(recipe, args.output, args.report)
    return check(recipe, args.assets, args.report)


if __name__ == "__main__":
    sys.exit(main())
