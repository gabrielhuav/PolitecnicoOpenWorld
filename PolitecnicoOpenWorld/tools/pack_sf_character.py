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
import argparse
import re
from PIL import Image, ImageChops, ImageStat

TARGET_BODY_H = 100.0

# Las poses erguidas de juego deben conservar exactamente el mismo zoom visible.
# Specials, saltos, agachado, victoria y KO quedan fuera: su silueta cambia por
# postura u objetos/efectos y normalizar su caja completa deformaria al personaje.
FIXED_UPRIGHT_PREFIXES = (
    "idle-", "forwards-", "backwards-",
    "light-punch-", "med-punch-", "heavy-punch-",
    "light-kick-", "med-kick-", "heavy-kick-",
    "hit-face-", "hit-stomach-", "stun-",
)

# Directories
HERE = os.path.dirname(os.path.abspath(__file__))
BASE_DIR = os.path.normpath(os.path.join(HERE, ".."))
GEN_DIR = os.path.join(BASE_DIR, "app/src/main/assets/STREETFIGHTER/GEN")
DATA_DIR = os.path.join(BASE_DIR, "app/src/main/assets/STREETFIGHTER/DATA")
IMAGES_DIR = os.path.join(BASE_DIR, "app/src/main/assets/STREETFIGHTER/IMAGES")

# Cuadros adicionales de los sets croma dedicados. Las claves que ya existen en el
# template conservan su posicion; estas se agregan al final sin afectar a los peleadores
# compartidos que siguen usando sf_template.json en runtime.
DEDICATED_EXTRA_KEYS = (
    [f"light-punch-{i}" for i in range(3, 5)] +
    [f"med-punch-{i}" for i in range(4, 7)] +
    [f"heavy-punch-{i}" for i in range(2, 7)] +
    [f"light-kick-{i}" for i in range(3, 7)] +
    [f"med-kick-{i}" for i in range(2, 6)] +
    ["heavy-kick-6", "jump-start-2"] +
    [f"jump-land-{i}" for i in range(1, 4)] +
    [f"jump-back-{i}" for i in range(1, 8)] +
    [f"special-light-{i}" for i in range(1, 6)] +
    [f"special-medium-{i}" for i in range(1, 6)] +
    ["special-5"]
)

# Posicion/escala del efecto segun el objeto real de cada personaje. El frame es
# cero-based dentro de la animacion especial y ya no queda hardcodeado en Kotlin.
PROJECTILE_PROFILES = {
    "prankedy": {
        "light":  {"frame": 2, "offset": [58, -54], "scale": 0.75},
        "medium": {"frame": 2, "offset": [66, -55], "scale": 1.00},
        "heavy":  {"frame": 2, "offset": [76, -57], "scale": 1.25},
    },
    "senortienda": {
        "light":  {"frame": 2, "offset": [45, -18], "scale": 0.70},
        "medium": {"frame": 2, "offset": [60, -20], "scale": 1.00},
        "heavy":  {"frame": 2, "offset": [76, -22], "scale": 1.25},
    },
    "reygrupero": {
        "light":  {"frame": 2, "offset": [55, -70], "scale": 0.75},
        "medium": {"frame": 2, "offset": [66, -71], "scale": 1.00},
        "heavy":  {"frame": 2, "offset": [78, -72], "scale": 1.30},
    },
    "paparazzi1": {
        "light":  {"frame": 2, "offset": [48, -64], "scale": 0.70},
        "medium": {"frame": 2, "offset": [61, -66], "scale": 1.00},
        "heavy":  {"frame": 2, "offset": [74, -68], "scale": 1.25},
    },
    "paparazzi5": {
        "light":  {"frame": 2, "offset": [48, -68], "scale": 0.70},
        "medium": {"frame": 2, "offset": [61, -69], "scale": 1.00},
        "heavy":  {"frame": 2, "offset": [74, -70], "scale": 1.25},
    },
    "policiacdmx": {
        "light":  {"frame": 2, "offset": [42, -54], "scale": 0.70},
        "medium": {"frame": 2, "offset": [58, -56], "scale": 1.00},
        "heavy":  {"frame": 2, "offset": [76, -58], "scale": 1.30},
    },
    "policiacdmxhombre": {
        "light":  {"frame": 2, "offset": [42, -54], "scale": 0.70},
        "medium": {"frame": 2, "offset": [58, -56], "scale": 1.00},
        "heavy":  {"frame": 2, "offset": [76, -58], "scale": 1.30},
    },
    "paramedicocruzroja": {
        "light":  {"frame": 2, "offset": [46, -52], "scale": 0.70},
        "medium": {"frame": 2, "offset": [60, -54], "scale": 1.00},
        "heavy":  {"frame": 2, "offset": [76, -56], "scale": 1.30},
    },
}

# Correcciones anatomicas detectadas por QA. No se mide la caja alfa completa porque
# incluiria objetos (escoba, tanque, megafono) y efectos, y terminaria encogiendo al
# personaje. La escala se aplica alrededor del origen/pies. Un `_frame_meta.json` puede
# declarar `scale` por cuadro para futuros assets sin cambiar este script.
QA_FRAME_SCALE_OVERRIDES = {
    "reygrupero": {
        # La hoja SPECIAL HEAVY cambia el zoom en el arranque y la recuperacion.
        "special-1": 0.92,
        "special-5": 0.91,
    },
}

def filename_for_key(key):
    return "jump-start-land-1.png" if key == "jump-start/land" else f"{key}.png"

def scale_around_origin(img, factor):
    """Escala la figura manteniendo el origen de combate: centro X y pies Y=224."""
    bbox = img.getbbox()
    if not bbox or abs(factor - 1.0) <= 0.0001:
        return img
    crop = img.crop(bbox)
    w = max(1, int(round(crop.width * factor)))
    h = max(1, int(round(crop.height * factor)))
    crop = crop.resize((w, h), Image.Resampling.LANCZOS)
    center_x = 128 + (((bbox[0] + bbox[2]) / 2.0) - 128) * factor
    bottom_y = 224 + (bbox[3] - 224) * factor
    normalized = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    normalized.paste(crop, (int(round(center_x - w / 2.0)),
                            int(round(bottom_y - h))), crop)
    return normalized

def requires_fixed_upright_height(key):
    return "turn" not in key and key.startswith(FIXED_UPRIGHT_PREFIXES)

def animation_with_transition(keys, delays):
    assert len(keys) == len(delays)
    return [[key, delay] for key, delay in zip(keys, delays)] + [[keys[-1], -1]]

def dedicated_animations(template):
    """Animaciones completas para arte croma; conserva estados/timings del motor."""
    out = json.loads(json.dumps(template))
    out["lightPunch"] = animation_with_transition(
        [f"light-punch-{i}" for i in range(1, 5)], [2, 2, 4, 3])
    out["mediumPunch"] = animation_with_transition(
        [f"med-punch-{i}" for i in range(1, 7)], [1, 2, 4, 4, 3, 3])
    out["heavyPunch"] = animation_with_transition(
        [f"heavy-punch-{i}" for i in range(1, 7)], [3, 3, 6, 7, 9, 10])
    out["lightKick"] = animation_with_transition(
        [f"light-kick-{i}" for i in range(1, 7)], [2, 2, 5, 4, 3, 2])
    out["mediumKick"] = animation_with_transition(
        [f"med-kick-{i}" for i in range(1, 6)], [4, 5, 10, 6, 5])
    out["heavyKick"] = animation_with_transition(
        [f"heavy-kick-{i}" for i in range(1, 7)], [2, 4, 7, 8, 8, 7])
    out["jumpStart"] = [["jump-start/land", 3], ["jump-start-2", -1]]
    out["jumpLand"] = [["jump-land-1", 2], ["jump-land-2", 3],
                       ["jump-land-3", 5], ["jump-land-3", -1]]
    out["jumpBackwards"] = [[f"jump-back-{i}", delay] for i, delay in
                            zip(range(1, 8), [15, 3, 3, 3, 3, 3, 0])]
    out["special1Light"] = animation_with_transition(
        [f"special-light-{i}" for i in range(1, 6)], [2, 8, 2, 10, 20])
    out["special1Medium"] = animation_with_transition(
        [f"special-medium-{i}" for i in range(1, 6)], [4, 10, 4, 12, 24])
    out["special1Heavy"] = animation_with_transition(
        [f"special-{i}" for i in range(1, 6)], [5, 10, 5, 15, 30])
    # Los tres cuadros STUN ya existen en la hoja 10: las reacciones fuertes los
    # recorren completos en vez de congelar tres veces el ultimo.
    out["hurtHeadHeavy"] = [
        ["hit-face-3", 15], ["hit-face-3", 7], ["hit-face-4", 4],
        ["stun-1", 3], ["stun-2", 3], ["stun-3", 9], ["stun-3", -1],
    ]
    out["hurtBodyHeavy"] = [
        ["hit-stomach-2", 15], ["hit-stomach-2", 3], ["hit-stomach-3", 4],
        ["hit-stomach-4", 4], ["stun-1", 3], ["stun-2", 3],
        ["stun-3", 9], ["stun-3", -1],
    ]
    return out

def reference_frame_key(key):
    """Devuelve la caja clasica mas cercana a la pose nueva y si conserva hitbox."""
    m = re.match(r"(light-punch|med-punch|heavy-punch|light-kick|med-kick|heavy-kick)-(\d+)$", key)
    if m:
        prefix, idx = m.group(1), int(m.group(2))
        if prefix == "light-punch":
            return ("light-punch-2", idx == 3) if idx in (2, 3) else ("light-punch-1", False)
        if prefix == "med-punch":
            return ("med-punch-3", idx in (3, 4)) if idx in (2, 3, 4, 5) else ("med-punch-1", False)
        if prefix == "heavy-punch":
            return ("heavy-punch-1", idx in (3, 4)) if idx in (2, 3, 4, 5) else ("med-punch-1", False)
        if prefix == "light-kick":
            return ("light-kick-2", idx in (3, 4)) if idx in (2, 3, 4, 5) else ("light-kick-1", False)
        if prefix == "med-kick":
            return ("med-kick-1", idx == 3) if idx in (2, 3, 4) else ("light-kick-1", False)
        if prefix == "heavy-kick":
            if idx in (3, 4): return "heavy-kick-3", True
            if idx == 2: return "heavy-kick-2", False
            if idx == 5: return "heavy-kick-4", False
            return "heavy-kick-1", False
    if key == "jump-start-2" or key.startswith("jump-land-"):
        return "jump-start/land", False
    if key.startswith("jump-back-"):
        idx = int(key.rsplit("-", 1)[1])
        return f"jump-roll-{idx}", False
    if key.startswith("special-light-") or key.startswith("special-medium-"):
        idx = int(key.rsplit("-", 1)[1])
        return f"special-{min(idx, 4)}", False
    if key == "special-5":
        return "special-4", False
    return key, True

def pack_character(char_name, char_title, gen_root=GEN_DIR):
    print(f"Packing character: {char_name} ({char_title})")
    
    char_gen_dir = os.path.join(gen_root, char_name)
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
    extra_keys = [k for k in DEDICATED_EXTRA_KEYS
                  if os.path.exists(os.path.join(char_gen_dir, filename_for_key(k)))]
    all_keys = frame_keys + extra_keys + existing_proj
    num_frames = len(all_keys)

    # Algunas hojas de LIGHT PUNCH traen dos cuadros casi identicos a la guardia: el
    # boton X funciona, pero visualmente parece no hacer nada. Si la diferencia media es
    # minima, reutilizamos los dos primeros cuadros del MEDIUM PUNCH REFINED como jab
    # corto. Es preferible a una pose HANDGUN porque no introduce un arma en un golpe.
    light_paths = [os.path.join(char_gen_dir, f"light-punch-{i}.png") for i in range(1, 5)]
    medium_paths = [os.path.join(char_gen_dir, f"med-punch-{i}.png") for i in range(1, 5)]
    light_punch_fallback = False
    if all(os.path.exists(p) for p in light_paths + medium_paths):
        a = Image.open(light_paths[0]).convert("RGBA")
        diff_mean = max(
            sum(ImageStat.Stat(ImageChops.difference(a, Image.open(p).convert("RGBA"))).mean) / 4.0
            for p in light_paths[1:]
        )
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
        elif light_punch_fallback and key.startswith("light-punch-"):
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
                img = scale_around_origin(img, pack_scale)
            # Ajuste fino de zoom corporal: primero metadata del recorte y, si no existe,
            # el perfil de QA conocido. Efectos/proyectiles independientes no pasan aqui.
            frame_scale = frame_meta.get(key, {}).get(
                "scale", QA_FRAME_SCALE_OVERRIDES.get(char_name, {}).get(key, 1.0))
            if not key.startswith("proj-") and abs(float(frame_scale) - 1.0) > 0.0001:
                img = scale_around_origin(img, float(frame_scale))
            # Defensa final por CUADRO. Las fuentes IA cambian el zoom incluso dentro
            # de una misma accion; el lienzo 256x256 por si solo no evita ese efecto.
            if requires_fixed_upright_height(key):
                bbox = img.getbbox()
                if bbox and bbox[3] > bbox[1]:
                    img = scale_around_origin(img, TARGET_BODY_H / (bbox[3] - bbox[1]))
                
        sheet_img.paste(img, (src_x, src_y), img)
        bbox = img.getbbox()
        packed_heights[key] = (bbox[3] - bbox[1]) if bbox else 0
        
        # Build frame data JSON entry
        entry = {
            "src": [src_x, src_y, 256, 256],
            "origin": [128, 224]
        }
        
        ref_key, keep_hit = reference_frame_key(key)
        if ref_key in ryu_frames:
            # Copia la caja clasica mas cercana. En secuencias expandidas solo los
            # cuadros de contacto conservan hitbox; preparacion/recuperacion no pegan.
            ref_frame = ryu_frames[ref_key]
            if "push" in ref_frame:
                entry["push"] = ref_frame["push"]
            if "hurt" in ref_frame:
                entry["hurt"] = ref_frame["hurt"]
            if keep_hit and "hit" in ref_frame:
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
    for prefix in (
        "idle", "forwards", "backwards",
        "light-punch", "med-punch", "heavy-punch",
        "light-kick", "med-kick", "heavy-kick",
        "hit-face", "hit-stomach", "stun",
    ):
        heights = [h for key, h in packed_heights.items()
                   if key.startswith(prefix + "-") and "turn" not in key and h > 0]
        if not heights:
            continue
        min_h, max_h = min(heights), max(heights)
        if min_h < TARGET_BODY_H - 1.0 or max_h > TARGET_BODY_H + 1.0:
            print(f"Error: {prefix} rango={min_h}-{max_h}px; debe quedar en {TARGET_BODY_H:.1f}px.")
            sys.exit(1)
        print(f"Tamano SF {prefix:12s}: rango {min_h}-{max_h}px OK")
        
    # Save the packed sprite sheet
    os.makedirs(IMAGES_DIR, exist_ok=True)
    out_sheet_path = os.path.join(IMAGES_DIR, f"{char_title}.png")
    sheet_img.save(out_sheet_path, "PNG")
    print(f"Saved sprite sheet to: {out_sheet_path} (size: {sheet_w}x{sheet_h})")
    
    # Save the JSON data
    out_json = {
        "frames": packed_frames,
        "animations": dedicated_animations(ryu_animations),
        "events": {"projectile": PROJECTILE_PROFILES.get(char_name, {})},
    }
    
    out_json_path = os.path.join(DATA_DIR, f"{char_name}.json")
    with open(out_json_path, "w", encoding="utf-8") as f:
        json.dump(out_json, f, separators=(",", ":"))
    print(f"Saved frame data JSON to: {out_json_path}")
    
if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("char", nargs="?", default="prankedy")
    parser.add_argument("title", nargs="?", default="Prankedy")
    parser.add_argument("--gen", default=GEN_DIR,
                        help="Raiz GEN externa; evita dejar intermedios dentro de assets")
    args = parser.parse_args()
    pack_character(args.char, args.title, args.gen)
