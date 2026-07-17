#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Packer script for Street Fighter characters in Politecnico Open World (POW).
Combines individual 256x256 PNG frames from STREETFIGHTER/GEN/<char_name>
into a single sprite sheet STREETFIGHTER/IMAGES/<CharName>.png, and generates
STREETFIGHTER/DATA/<char_name>.json matching the motor contract (reusing
ryu.json timings and boxes).
"""
import os
import json
import sys
import statistics
from PIL import Image, ImageChops, ImageStat

TARGET_BODY_H = 100.0

# Directories
HERE = os.path.dirname(os.path.abspath(__file__))
BASE_DIR = os.path.normpath(os.path.join(HERE, ".."))
GEN_DIR = os.path.join(BASE_DIR, "app/src/main/assets/STREETFIGHTER/GEN")
DATA_DIR = os.path.join(BASE_DIR, "app/src/main/assets/STREETFIGHTER/DATA")
IMAGES_DIR = os.path.join(BASE_DIR, "app/src/main/assets/STREETFIGHTER/IMAGES")

def pack_character(char_name, char_title):
    print(f"Packing character: {char_name} ({char_title})")
    
    char_gen_dir = os.path.join(GEN_DIR, char_name)
    if not os.path.exists(char_gen_dir):
        print(f"Error: Directory not found: {char_gen_dir}")
        sys.exit(1)
        
    # Template de cajas/timings: sf_template.json (main; ryu.json vive ahora en el
    # source set DEBUG por copyright — fallback por si se corre en un checkout viejo)
    ryu_json_path = os.path.join(DATA_DIR, "sf_template.json")
    if not os.path.exists(ryu_json_path):
        ryu_json_path = os.path.join(DATA_DIR, "ryu.json")
    if not os.path.exists(ryu_json_path):
        print(f"Error: Template file not found: {ryu_json_path}")
        sys.exit(1)
        
    with open(ryu_json_path, "r", encoding="utf-8") as f:
        ryu_data = json.load(f)
        
    ryu_frames = ryu_data["frames"]
    ryu_animations = ryu_data["animations"]
    
    # 77 character frames in order of ryu.json
    frame_keys = list(ryu_frames.keys())
    
    # Extra projectile frames (not in ryu.json, but generated and to be packed)
    proj_keys = [
        "proj-fly-1", "proj-fly-2",
        "proj-hit-1", "proj-hit-2", "proj-hit-3"
    ]
    
    # Solo empaqueta los proyectiles que EXISTEN en GEN; si el personaje no tiene
    # proj-*, NO entran al JSON y el motor usa el fireball del tema (fallback).
    existing_proj = [k for k in proj_keys if os.path.exists(os.path.join(char_gen_dir, f"{k}.png"))]
    all_keys = frame_keys + existing_proj
    num_frames = len(all_keys)

    # Algunas hojas de LIGHT PUNCH traen dos cuadros casi identicos a la guardia: el
    # boton X funciona, pero visualmente parece no hacer nada. Si la diferencia media es
    # minima, reutilizamos los dos primeros cuadros del MEDIUM PUNCH REFINED como jab
    # corto. Es preferible a una pose HANDGUN porque no introduce un arma en un golpe.
    light_paths = [os.path.join(char_gen_dir, f"light-punch-{i}.png") for i in (1, 2)]
    medium_paths = [os.path.join(char_gen_dir, f"med-punch-{i}.png") for i in (1, 2)]
    light_punch_fallback = False
    if all(os.path.exists(p) for p in light_paths + medium_paths):
        a = Image.open(light_paths[0]).convert("RGBA")
        b = Image.open(light_paths[1]).convert("RGBA")
        diff_mean = sum(ImageStat.Stat(ImageChops.difference(a, b)).mean) / 4.0
        light_punch_fallback = diff_mean < 1.5
        if light_punch_fallback:
            print(f"Puño X: LIGHT casi inmovil (dif. {diff_mean:.2f}); uso MEDIUM 1/2 como jab corto.")

    # Invariante de tamano para TODOS los personajes dedicados: el recortador ya deja
    # el idle a 100 px, y el packer lo vuelve a imponer como ultima defensa antes del APK.
    idle_heights = []
    for key in ("idle-1", "idle-2", "idle-3", "idle-4"):
        path = os.path.join(char_gen_dir, f"{key}.png")
        if os.path.exists(path):
            bbox = Image.open(path).convert("RGBA").getbbox()
            if bbox:
                idle_heights.append(bbox[3] - bbox[1])
    if not idle_heights:
        print("Error: no hay cuadros idle opacos para normalizar el tamano.")
        sys.exit(1)
    idle_heights.sort()
    idle_median = float(idle_heights[len(idle_heights) // 2])
    pack_scale = TARGET_BODY_H / idle_median
    print(f"Escala visual comun: idle mediano {idle_median:.1f}px -> {TARGET_BODY_H:.1f}px (x{pack_scale:.4f})")

    meta_path = os.path.join(char_gen_dir, "_frame_meta.json")
    frame_meta = {}
    if os.path.exists(meta_path):
        with open(meta_path, "r", encoding="utf-8") as f:
            frame_meta = json.load(f)
    
    # 10 columns grid layout
    cols = 10
    rows = (num_frames + cols - 1) // cols
    
    sheet_w = cols * 256
    sheet_h = rows * 256
    
    sheet_img = Image.new("RGBA", (sheet_w, sheet_h), (0, 0, 0, 0))
    packed_frames = {}
    packed_heights = {}
    
    for idx, key in enumerate(all_keys):
        col = idx % cols
        row = idx // cols
        src_x = col * 256
        src_y = row * 256
        
        # Map key to filename in GEN folder
        if key == "jump-start/land":
            filename = "jump-start-land-1.png"
        elif key in ("stun-1", "stun-2"):
            filename = "stun-3.png"
        elif light_punch_fallback and key in ("light-punch-1", "light-punch-2"):
            filename = key.replace("light-punch", "med-punch") + ".png"
        else:
            filename = f"{key}.png"
            
        file_path = os.path.join(char_gen_dir, filename)
        if not os.path.exists(file_path):
            print(f"Warning: File not found: {file_path}. Using fallback empty cell.")
            img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
        else:
            img = Image.open(file_path).convert("RGBA")
            if img.size != (256, 256):
                print(f"Warning: {filename} size is {img.size}, expected (256, 256). Resizing.")
                img = img.resize((256, 256), Image.Resampling.LANCZOS)
            if not key.startswith("proj-") and abs(pack_scale - 1.0) > 0.0001:
                bbox = img.getbbox()
                if bbox:
                    crop = img.crop(bbox)
                    w = max(1, int(round(crop.width * pack_scale)))
                    h = max(1, int(round(crop.height * pack_scale)))
                    crop = crop.resize((w, h), Image.Resampling.LANCZOS)
                    center_x = 128 + (((bbox[0] + bbox[2]) / 2.0) - 128) * pack_scale
                    bottom_y = 224 + (bbox[3] - 224) * pack_scale
                    normalized = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
                    normalized.paste(crop, (int(round(center_x - w / 2.0)), int(round(bottom_y - h))), crop)
                    img = normalized
                
        sheet_img.paste(img, (src_x, src_y), img)
        bbox = img.getbbox()
        packed_heights[key] = (bbox[3] - bbox[1]) if bbox else 0
        
        # Build frame data JSON entry
        entry = {
            "src": [src_x, src_y, 256, 256],
            "origin": [128, 224]
        }
        
        if key in frame_keys:
            # Copy box definitions if present in ryu.json
            ref_frame = ryu_frames[key]
            if "push" in ref_frame:
                entry["push"] = ref_frame["push"]
            if "hurt" in ref_frame:
                entry["hurt"] = ref_frame["hurt"]
            if "hit" in ref_frame:
                entry["hit"] = ref_frame["hit"]
        else:
            # Projectiles are centered
            entry["origin"] = [128, 128]
        if frame_meta.get(key, {}).get("flipX"):
            entry["flipX"] = True
            
        packed_frames[key] = entry

    # Guardia contra la regresion que motivo la normalizacion: en una pelea, pasar de
    # Idle a caminar adelante/atras no puede cambiar el zoom del personaje. El lienzo
    # siempre es 256², pero tambien comprobamos la altura VISIBLE de esas secuencias.
    for prefix in ("idle", "forwards", "backwards"):
        heights = [h for key, h in packed_heights.items()
                   if key.startswith(prefix + "-") and "turn" not in key and h > 0]
        if not heights:
            continue
        median_h = float(statistics.median(heights))
        if abs(median_h - TARGET_BODY_H) > 2.0:
            print(f"Error: {prefix} mediano={median_h:.1f}px; debe quedar en {TARGET_BODY_H:.1f}px.")
            sys.exit(1)
        print(f"Tamano SF {prefix:9s}: mediana {median_h:.1f}px OK")
        
    # Save the packed sprite sheet
    os.makedirs(IMAGES_DIR, exist_ok=True)
    out_sheet_path = os.path.join(IMAGES_DIR, f"{char_title}.png")
    sheet_img.save(out_sheet_path, "PNG")
    print(f"Saved sprite sheet to: {out_sheet_path} (size: {sheet_w}x{sheet_h})")
    
    # Save the JSON data
    out_json = {
        "frames": packed_frames,
        "animations": ryu_animations
    }
    
    out_json_path = os.path.join(DATA_DIR, f"{char_name}.json")
    with open(out_json_path, "w", encoding="utf-8") as f:
        json.dump(out_json, f, separators=(",", ":"))
    print(f"Saved frame data JSON to: {out_json_path}")
    
if __name__ == "__main__":
    char_name = "prankedy"
    char_title = "Prankedy"
    if len(sys.argv) > 2:
        char_name = sys.argv[1]
        char_title = sys.argv[2]
    pack_character(char_name, char_title)
