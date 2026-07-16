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
from PIL import Image

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
    
    # 10 columns grid layout
    cols = 10
    rows = (num_frames + cols - 1) // cols
    
    sheet_w = cols * 256
    sheet_h = rows * 256
    
    sheet_img = Image.new("RGBA", (sheet_w, sheet_h), (0, 0, 0, 0))
    packed_frames = {}
    
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
                
        sheet_img.paste(img, (src_x, src_y), img)
        
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
            
        packed_frames[key] = entry
        
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
