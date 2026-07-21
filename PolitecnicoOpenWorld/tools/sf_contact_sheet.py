"""Hoja de contacto para QA VISUAL de assets de pelea (2026-07-20).

Dibuja el primer frame (o el central con `mid`) de CADA animacion de un personaje en una
sola imagen etiquetada, para revisar de un vistazo poses rotas/volteadas/fusionadas sin
abrir el juego. Uso:
    python tools/sf_contact_sheet.py <personaje> [first|mid]
    (personaje = nombre del json en assets/STREETFIGHTER/DATA, p. ej. lallorona)
Salida: tools/_contact_sheets/<personaje>_contact_<pick>.png
"""
import json
import os
import sys
from PIL import Image, ImageDraw

ROOT = r"C:\Users\gabri\Documents\GitHub Desktop\PolitecnicoOpenWorld\PolitecnicoOpenWorld\app\src\main\assets\STREETFIGHTER"
char = sys.argv[1]
pick = sys.argv[2] if len(sys.argv) > 2 else "first"  # first | mid

data = json.load(open(os.path.join(ROOT, "DATA", f"{char.lower()}.json"), encoding="utf-8"))
img_path = None
for f in os.listdir(os.path.join(ROOT, "IMAGES")):
    if f.lower() == f"{char.lower()}.png":
        img_path = os.path.join(ROOT, "IMAGES", f)
        break
atlas = Image.open(img_path).convert("RGBA")
frames = data["frames"]
anims = sorted(data["animations"].items())

CELL = 130
cols = 6
rows = (len(anims) + cols - 1) // cols
sheet = Image.new("RGBA", (cols * CELL, rows * (CELL + 14)), (30, 30, 30, 255))
draw = ImageDraw.Draw(sheet)
for i, (key, anim) in enumerate(anims):
    cx, cy = (i % cols) * CELL, (i // cols) * (CELL + 14)
    label = key
    if anim:
        idx = 0 if pick == "first" else len(anim) // 2
        entry = anim[idx]
        fk = entry[0] if isinstance(entry, list) else entry
        fr = frames.get(fk)
        if fr:
            x, y, w, h = fr[0] if isinstance(fr, list) else fr.get("src")
            crop = atlas.crop((x, y, x + w, y + h))
            crop.thumbnail((CELL - 4, CELL - 4))
            sheet.paste(crop, (cx + (CELL - crop.width) // 2, cy + (CELL - crop.height) // 2), crop)
        else:
            label += " (frame?)"
    else:
        label += " (vacia)"
    draw.text((cx + 2, cy + CELL), label[:22], fill=(255, 255, 0, 255))
out_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "_contact_sheets")
os.makedirs(out_dir, exist_ok=True)
out = os.path.join(out_dir, f"{char}_contact_{pick}.png")
sheet.save(out)
print(out)
