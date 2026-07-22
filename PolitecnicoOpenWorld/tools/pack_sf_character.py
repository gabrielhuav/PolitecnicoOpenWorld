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

# 🆕 (2026-07-21) TANDAS 5-8 (hojas 20-29): moveset estilo SF III 3rd Strike.
# clave de animacion del motor -> (prefijo de cuadro, numero de cuadros, delays)
# Solo se empaquetan/animan los que EXISTAN en el GEN del personaje: quien no tenga la
# hoja simplemente no gana ese estado y el motor lo ignora (nunca entra a el).
NEW_MOVE_ANIMATIONS = {
    "dashForward":      ("dash", 4, [3, 3, 4, 4]),
    "dashBackward":     ("backdash", 4, [3, 3, 4, 4]),
    "blockHigh":        ("block-high", 4, [3, 4, 6, 4]),
    "blockLow":         ("block-low", 4, [3, 4, 6, 4]),
    "parryHigh":        ("parry-high", 3, [3, 5, 4]),
    "parryLow":         ("parry-low", 3, [3, 5, 4]),
    "crouchPunch":      ("crouch-punch", 4, [3, 4, 5, 4]),
    "crouchKick":       ("crouch-kick", 4, [3, 4, 5, 4]),
    "crouchHeavyPunch": ("crouch-hp", 5, [4, 4, 6, 5, 5]),
    "sweep":            ("sweep", 6, [4, 4, 6, 6, 5, 5]),
    "airPunch":         ("air-punch", 4, [3, 4, 5, 4]),
    "airKick":          ("air-kick", 4, [3, 4, 5, 4]),
    "longKick":         ("long-kick", 7, [4, 4, 5, 7, 6, 5, 5]),
    "overhead":         ("overhead", 5, [5, 5, 7, 6, 5]),
    "grab":             ("grab", 2, [4, 6]),
    "throw":            ("throw", 6, [4, 5, 6, 6, 6, 8]),
    "taunt":            ("taunt", 6, [6, 6, 6, 6, 6, 8]),
    "thrown":           ("thrown", 6, [4, 5, 5, 6, 6, 10]),
    "getUp":            ("getup", 6, [5, 5, 5, 5, 5, 5]),
    "superArt":         ("super", 8, [5, 5, 6, 7, 8, 7, 6, 10]),
    "hurtCrouch":       ("hurt-crouch", 4, [5, 6, 6, 6]),
    # 🆕 (2026-07-21) Poses que YA se recortaban pero se tiraban a _extra/: la CARRERA
    # (hoja 03) y las de intro sin guardia (hoja 18). Ahora son estados del motor.
    "run":              ("run", 8, [3, 3, 3, 3, 3, 3, 3, 3]),
    "idleRelaxed":      ("idle-relaxed", 6, [8, 8, 8, 8, 8, 8]),
    "talk":             ("talk", 4, [7, 7, 7, 7]),
}
NEW_MOVE_KEYS = [f"{prefix}-{i}"
                 for prefix, count, _ in NEW_MOVE_ANIMATIONS.values()
                 for i in range(1, count + 1)]

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
    "policiagranaderohombre": {
        "light":  {"frame": 2, "offset": [50, -58], "scale": 0.70},
        "medium": {"frame": 2, "offset": [64, -58], "scale": 1.00},
        "heavy":  {"frame": 2, "offset": [78, -58], "scale": 1.30},
    },
    "escomboy": {
        "light":  {"frame": 2, "offset": [48, -54], "scale": 0.70},
        "medium": {"frame": 2, "offset": [62, -55], "scale": 1.00},
        "heavy":  {"frame": 2, "offset": [76, -56], "scale": 1.30},
    },
    "escomgirl": {
        "light":  {"frame": 2, "offset": [48, -54], "scale": 0.70},
        "medium": {"frame": 2, "offset": [62, -55], "scale": 1.00},
        "heavy":  {"frame": 2, "offset": [76, -56], "scale": 1.30},
    },
    "policiagranaderomujer": {
        "light":  {"frame": 2, "offset": [48, -54], "scale": 0.70},
        "medium": {"frame": 2, "offset": [62, -55], "scale": 1.00},
        "heavy":  {"frame": 2, "offset": [76, -56], "scale": 1.30},
    },
    "robot": {
        "light":  {"frame": 2, "offset": [48, -54], "scale": 0.70},
        "medium": {"frame": 2, "offset": [62, -55], "scale": 1.00},
        "heavy":  {"frame": 2, "offset": [76, -56], "scale": 1.30},
    },
    "yoalliehecatl": {
        "light":  {"frame": 2, "offset": [48, -58], "scale": 0.70},
        "medium": {"frame": 2, "offset": [62, -58], "scale": 1.00},
        "heavy":  {"frame": 2, "offset": [76, -58], "scale": 1.30},
    },
    "charronegro": {
        "light":  {"frame": 2, "offset": [48, -58], "scale": 0.70},
        "medium": {"frame": 2, "offset": [62, -58], "scale": 1.00},
        "heavy":  {"frame": 2, "offset": [76, -58], "scale": 1.30},
    },
    "latzitzimime": {
        "light":  {"frame": 2, "offset": [48, -62], "scale": 0.70},
        "medium": {"frame": 2, "offset": [62, -62], "scale": 1.00},
        "heavy":  {"frame": 2, "offset": [76, -62], "scale": 1.30},
    },
    "lapresidenta": {
        "light":  {"frame": 2, "offset": [48, -52], "scale": 0.70},
        "medium": {"frame": 2, "offset": [62, -54], "scale": 1.00},
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

DERIVED_BONUS_POWERS = {
    # La metamorfosis canónica ya contiene las cinco etapas completas. Yoalli reutiliza
    # esos cuadros en orden inverso para regresar a La Presidenta sin duplicar arte.
    ("yoalliehecatl", 10): ("lapresidenta", 11, True),
}

# 🆕 (2026-07-21) PODERES DE PROYECTIL: hojas dibujadas como GUION (no como animación del
# personaje). Sus 5 cuadros son: 1 = personaje lanzando, 2-3-4 = SOLO el proyectil/efecto
# viajando e impactando (mazo, libro, bolsa de dinero…), 5 = personaje en seguimiento.
# Si se animaran los 5, el personaje DESAPARECE 3 cuadros (bug reportado en La Presidenta).
# Aquí la animación usa solo 1 y 5; los cuadros 2-4 siguen empacados y la View los dibuja
# como el PROYECTIL de ese poder (SfFireball.bonusPower).
# Los poderes NO listados animan sus 5 cuadros (el personaje sale en todos: aura/rayo).
BONUS_PROJECTILE_POWERS = {
    "lapresidenta": {1, 2, 3, 4, 5, 6},
}

# 🆕 (2026-07-21) PODERES BONUS "RÁPIDOS": el dueño audita la hoja y decide que solo unos
# cuadros concretos sirven. Caso real: la hoja de bonus 3/4/5 de La Tzitzimime trae los
# primeros cuadros dibujados con OTRO personaje (Yoalli), aunque estén bien recortados.
# En vez de tirar el poder entero o esperar arte nueva, se anima solo la parte válida y
# queda como un poder corto y seco.
# Formato: {personaje: {nº de poder: [nº de cuadro válido, ...]}}
BONUS_FAST_POWERS = {
    "latzitzimime": {
        4: [5],       # 1-4 son Yoalli; solo la pose final es de La Tzitzimime
        5: [4, 5],
    },
}

# 🆕 (2026-07-21) PODERES BONUS RETIRADOS: la hoja trae arte de OTRO personaje y no hay
# ningun cuadro aprovechable. Se deja de emitir su animacion.
# Es SEGURO no emitirla: StreetFighterViewModel.kt:2077 comprueba
# `animations[state.jsKey].isNullOrEmpty()` antes de entrar al estado, asi que el poder
# simplemente no se dispara (no deja al personaje congelado). Los cuadros siguen en el
# atlas; solo desaparece la animacion.
BONUS_REMOVED_POWERS = {
    "latzitzimime": {3},   # bonus-3-* esta dibujado con Yoalli, no con La Tzitzimime
}


def derived_bonus_path(char_name, key, gen_root):
    match = re.fullmatch(r"bonus-(\d+)-(\d+)", key)
    if not match:
        return None
    power, frame = (int(value) for value in match.groups())
    source = DERIVED_BONUS_POWERS.get((char_name, power))
    if source is None:
        return None
    source_char, source_power, reverse = source
    source_frame = 6 - frame if reverse else frame
    return os.path.join(gen_root, source_char, f"bonus-{source_power}-{source_frame}.png")


def frame_source_path(char_name, key, char_gen_dir, gen_root):
    return derived_bonus_path(char_name, key, gen_root) or os.path.join(
        char_gen_dir, filename_for_key(key))


def dedicated_animations(template, bonus_powers=0, unique_hurt_frames=False,
                         projectile_powers=frozenset(), available_keys=frozenset(),
                         all_frame_keys=frozenset(), fast_powers=None,
                         removed_powers=frozenset()):
    """Animaciones completas para arte croma; conserva estados/timings del motor."""
    fast_powers = fast_powers or {}
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
    if unique_hurt_frames:
        # La Llorona sí trae cuatro poses distintas por reacción; el template clásico
        # repetía las primeras y hacía que el Showcase pareciera congelado.
        out["hurtHeadLight"] = animation_with_transition(
            [f"hit-face-{i}" for i in range(1, 5)], [8, 6, 6, 6])
        out["hurtHeadMedium"] = animation_with_transition(
            [f"hit-face-{i}" for i in range(1, 5)], [8, 7, 7, 9])
        out["hurtBodyLight"] = animation_with_transition(
            [f"hit-stomach-{i}" for i in range(1, 4)], [8, 8, 10])
        out["hurtBodyMedium"] = animation_with_transition(
            [f"hit-stomach-{i}" for i in range(1, 5)], [8, 7, 7, 9])
    for power in range(1, bonus_powers + 1):
        if power in removed_powers:
            continue
        fast = fast_powers.get(power)
        if fast:
            # Poder corto: solo los cuadros que el dueño validó. Se sostienen para que el
            # poder siga durando lo suficiente como para leerse en pantalla.
            keys = [f"bonus-{power}-{i}" for i in fast]
            out[f"bonusPower{power}"] = animation_with_transition(
                keys, [34 // len(keys)] * len(keys))
        elif power in projectile_powers:
            # Solo las 2 poses del personaje (1 = lanza, 5 = seguimiento), sostenidas para
            # conservar la duración total del poder. Los cuadros 2-4 son el proyectil.
            out[f"bonusPower{power}"] = animation_with_transition(
                [f"bonus-{power}-1", f"bonus-{power}-5"], [12, 22])
        else:
            out[f"bonusPower{power}"] = animation_with_transition(
                [f"bonus-{power}-{i}" for i in range(1, 6)], [5, 7, 10, 12, 18])
    # 🆕 (2026-07-21) Movimientos nuevos: solo si TODOS sus cuadros existen en el GEN.
    for anim, (prefix, count, delays) in NEW_MOVE_ANIMATIONS.items():
        keys = [f"{prefix}-{i}" for i in range(1, count + 1)]
        if all(k in available_keys for k in keys):
            out[anim] = animation_with_transition(keys, delays)
    # 🆕 (2026-07-21) FATALITY: no es arte nueva, es una SECUENCIA CINEMÁTICA compuesta con
    # cuadros que el personaje YA tiene. Guion: concentración → ejecución de la súper →
    # desata su poder propio → remate → pose de victoria. Solo se genera si existen las
    # piezas; si falta alguna, el peleador simplemente no tiene fatality.
    fatality = fatality_animation(available_keys, all_frame_keys)
    if fatality:
        out["fatality"] = fatality
    return out


def fatality_animation(available_keys, all_frame_keys):
    """Secuencia del fatality a partir de cuadros existentes (None si no alcanza)."""
    def have(key):
        return key in available_keys or key in all_frame_keys

    charge = [k for k in ("super-1", "super-2", "super-3") if have(k)]
    strike = [k for k in ("super-4", "super-5", "super-6") if have(k)]
    if not charge or not strike:
        return None  # sin SUPER ART no hay fatality
    # El poder PROPIO del personaje: su bonus power 1 si lo tiene, si no su special.
    power = [k for k in ("bonus-1-1", "bonus-1-5") if have(k)]
    if not power:
        power = [k for k in ("special-1", "special-3", "special-5") if have(k)]
    finish = [k for k in ("super-7", "super-8") if have(k)]
    pose = [k for k in ("victory-1", "victory-3") if have(k)]

    keys = charge + strike + power + finish + pose
    if len(keys) < 6:
        return None
    # Ritmo cinematográfico: arranque lento, golpes rápidos, remate sostenido.
    delays = []
    for i, _ in enumerate(keys):
        if i < len(charge):
            delays.append(7)
        elif i < len(charge) + len(strike):
            delays.append(4)
        elif i < len(charge) + len(strike) + len(power):
            delays.append(8)
        else:
            delays.append(10)
    return animation_with_transition(keys, delays)

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
    # 🆕 (2026-07-21) Poses de las hojas 20-29. El template no las tiene, asi que cada una
    # hereda la caja clasica mas parecida; el 2o valor dice si conserva HITBOX (golpea).
    # Sin esto se empacarian sin cajas: invulnerables y sin poder pegar.
    new_move = re.match(
        r"(dash|backdash|block-high|block-low|parry-high|parry-low|crouch-punch|crouch-kick|"
        r"crouch-hp|sweep|air-punch|air-kick|long-kick|overhead|grab|throw|taunt|thrown|"
        r"getup|super|hurt-crouch|run|idle-relaxed)-(\d+)$", key)
    if new_move:
        prefix, idx = new_move.group(1), int(new_move.group(2))
        # Defensivas / movilidad / reacciones: hurtbox prestada, nunca hitbox.
        if prefix == "dash":
            return "forwards-3", False
        if prefix == "backdash":
            return "backwards-3", False
        if prefix in ("block-high", "parry-high", "taunt", "idle-relaxed"):
            return "idle-1", False
        # Correr comparte la caja de caminar (mismo cuerpo, más rápido)
        if prefix == "run":
            return f"forwards-{min(idx, 6)}", False
        if prefix in ("block-low", "parry-low", "hurt-crouch"):
            return "crouch-3", False
        if prefix == "thrown":
            return f"fall-{min(idx, 5)}", False
        if prefix == "getup":
            # Se levanta: del suelo (fall) a la guardia (idle)
            return ("fall-4", False) if idx <= 2 else (("crouch-3", False) if idx <= 4 else ("idle-1", False))
        if prefix == "throw":
            # El daño del lanzamiento lo aplica la logica, no una hitbox por cuadro
            return "idle-1", False
        # Ofensivas: hitbox SOLO en los cuadros activos (contacto), como en las clasicas.
        if prefix == "crouch-punch":
            return "light-punch-2", idx in (2, 3)
        if prefix == "crouch-kick":
            return "light-kick-2", idx in (2, 3)
        if prefix == "crouch-hp":
            return "heavy-punch-1", idx in (3, 4)
        if prefix == "sweep":
            return "heavy-kick-3", idx in (3, 4)
        if prefix == "air-punch":
            return "light-punch-2", idx in (2, 3)
        if prefix == "air-kick":
            return "light-kick-2", idx in (2, 3)
        if prefix == "long-kick":
            return "heavy-kick-3", idx in (4, 5)
        if prefix == "overhead":
            return "heavy-punch-1", idx in (3, 4)
        if prefix == "grab":
            return "light-punch-2", idx == 2
        if prefix == "super":
            return "special-3", idx in (4, 5, 6)
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
    # 🆕 (2026-07-21) Cuadros de las hojas 20-29 presentes en el GEN de ESTE personaje
    new_move_keys = [k for k in NEW_MOVE_KEYS
                     if os.path.exists(os.path.join(char_gen_dir, filename_for_key(k)))]
    bonus_keys = []
    bonus_power_count = 0
    power = 1
    while True:
        keys = [f"bonus-{power}-{i}" for i in range(1, 6)]
        present = [os.path.exists(frame_source_path(char_name, key, char_gen_dir, gen_root))
                   for key in keys]
        if all(present):
            bonus_keys.extend(keys)
            bonus_power_count = power
            power += 1
        elif any(present):
            print(f"Error: bonusPower{power} esta incompleto.")
            sys.exit(1)
        else:
            break
    all_keys = frame_keys + extra_keys + existing_proj + bonus_keys + new_move_keys
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
        derived_path = derived_bonus_path(char_name, key, gen_root)
        if derived_path:
            filename = os.path.basename(derived_path)
            file_path = derived_path
        elif key == "jump-start/land":
            filename = "jump-start-land-1.png"
            file_path = os.path.join(char_gen_dir, filename)
        elif key in ("stun-1", "stun-2"):
            filename = "stun-3.png"
            file_path = os.path.join(char_gen_dir, filename)
        elif light_punch_fallback and key.startswith("light-punch-"):
            filename = key.replace("light-punch", "med-punch") + ".png"
            file_path = os.path.join(char_gen_dir, filename)
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
        
        if key.startswith("bonus-"):
            ref_key, keep_hit = "special-3", False
        else:
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
    # 🆕 (2026-07-21) WebP LOSSLESS en vez de PNG: mismos pixeles visibles y ~25 % menos de
    # peso (los atlas crecieron mucho con las hojas 20-29 y el AAB va justo bajo el limite
    # de 500 MB de Play). SfFighterId.spriteAsset apunta a .webp. Si quedara un .png viejo
    # del mismo personaje se borra, para no duplicar peso en el APK.
    os.makedirs(IMAGES_DIR, exist_ok=True)
    out_sheet_path = os.path.join(IMAGES_DIR, f"{char_title}.webp")
    sheet_img.save(out_sheet_path, "WEBP", lossless=True, quality=100, method=6)
    legacy_png = os.path.join(IMAGES_DIR, f"{char_title}.png")
    if os.path.exists(legacy_png):
        os.remove(legacy_png)
    print(f"Saved sprite sheet to: {out_sheet_path} (size: {sheet_w}x{sheet_h})")
    
    # Save the JSON data
    out_json = {
        "frames": packed_frames,
        "animations": dedicated_animations(
            ryu_animations,
            bonus_power_count,
            unique_hurt_frames=char_name == "lallorona",
            projectile_powers=BONUS_PROJECTILE_POWERS.get(char_name, frozenset()),
            available_keys=frozenset(new_move_keys),
            all_frame_keys=frozenset(all_keys),
            fast_powers=BONUS_FAST_POWERS.get(char_name, {}),
            removed_powers=BONUS_REMOVED_POWERS.get(char_name, frozenset()),
        ),
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
