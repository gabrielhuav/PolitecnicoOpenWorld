#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Rebana las hojas NUEVAS de personaje con FONDO CROMA VERDE (#00FF00) del formato
"2 grupos por hoja" (Prankedy_01..19) y produce:

  A) Cuadros del MODO PELEA -> app/src/main/assets/STREETFIGHTER/GEN/<char>/
     (256x256, transparente, pies en (128,224), de pie ~100 px; listos para
      tools/pack_sf_character.py <char> <Titulo>)
  B) Cuadros del MUNDO -> GEN/WORLD_<char>/{Idle,Walk,Run,Special,Talk}/<prefix><k>_<n>.webp
     (lienzo 512x512; con --world-ref iguala tamano de figura y linea de pies al set actual)

USO:
  python3 tools/slice_sf_chroma_sheets.py <hoja.png> <char> [--sheet-num N] [--list]
      [--world-ref app/src/main/assets/SPRITES/NPC/PrankedyPlayable] [--world-prefix pk_]

Procesa la hoja 01 PRIMERO (fija la escala del personaje en GEN/<char>/_scale.json).
Robustez: el ALFA sale de la mascara CRUDA (~verde) — sin verde interior; los grupos
se separan por FILAS (bandas), tolerando que GPT dibuje algun cuadro de mas/menos;
para efectos DISPERSOS (confeti) reintenta con cierre morfologico grande.
"""
import os, sys, json, re, argparse
import numpy as np
from PIL import Image, ImageOps
from scipy import ndimage

CANVAS, FEET_Y, CX, TARGET_H = 256, 224, 128, 100.0
W_CANVAS = 512
WORLD_TARGET_H = 360.0
WORLD_FEET_Y = 456
ORIENTATION_SWITCH_RATIO = 0.82
ORIENTATION_MIN_GAIN = 0.004

# Altura visible objetivo por TIPO DE POSE. Las hojas generadas no conservan siempre
# el mismo zoom entre acciones (un Walk puede venir 15-25 % mas pequeno que el Idle).
# Calibrar cada secuencia evita que el personaje cambie de escala al empezar a moverse,
# sin inflar las poses que intencionalmente son bajas o giran hasta quedar horizontales.
SF_POSE_TARGET_H = {
    "CROUCH": 70.0,
    "CROUCH TURN": 65.0,
    "JUMP START": 85.0,
    "JUMP LAND": 85.0,
    "HURT HEAD": 100.0,
    "HURT BODY": 100.0,
    "STUN": 100.0,
    "KO": None,
    "PROJECTILE": None,
}

def nums(p, n): return ["%s-%d" % (p, i) for i in range(1, n + 1)]

# Por hoja: (label, cuadros esperados, (targets SF, modo pick), destino mundo)
SHEETS = {
    1:  [("IDLE",           6, (nums("idle", 4), "even"),        None),
         ("IDLE TURN",      4, (nums("idle-turn", 3), "even"),   None)],
    2:  [("CROUCH",         9, (nums("crouch", 3), "first_half"),None),
         ("CROUCH TURN",    4, (nums("crouch-turn", 3), "even"), None)],
    3:  [("CAMINAR",        6, (nums("forwards", 6), "even"),    ("Walk", "w")),
         ("CORRER",         8, (None, None),                     ("Run", "r"))],
    4:  [("JUMP START",     2, (["jump-start-land-1", "jump-start-2"], "even"), None),
         ("JUMP LAND",      3, (nums("jump-land", 3), "even"), None)],
    5:  [("JUMP UP",        6, (nums("jump-up", 6), "even"),     None),
         ("JUMP FORWARD",   7, (nums("jump-roll", 7), "even"),   None)],
    6:  [("JUMP BACKWARD",  7, (nums("jump-back", 7), "even"), None),
         ("LIGHT PUNCH",    4, (nums("light-punch", 4), "even"), None)],
    7:  [("MEDIUM PUNCH",   6, (nums("med-punch", 6), "even"),   None),
         ("HEAVY PUNCH",    6, (nums("heavy-punch", 6), "even"), None)],
    8:  [("LIGHT KICK",     6, (nums("light-kick", 6), "even"), None),
         ("MEDIUM KICK",    5, (nums("med-kick", 5), "even"), None)],
    9:  [("HEAVY KICK",     6, (nums("heavy-kick", 6), "even"),  None),
         ("HURT HEAD",     14, (nums("hit-face", 4), "first_half"),    None)],
    10: [("HURT BODY",     13, (nums("hit-stomach", 4), "first_half"), None),
         ("STUN",           3, (nums("stun", 3), "even"),       None)],
    11: [("SPECIAL LIGHT",  5, (nums("special-light", 5), "even"), None),
         ("SPECIAL MEDIUM", 5, (nums("special-medium", 5), "even"), None)],
    12: [("SPECIAL HEAVY",  5, (nums("special", 5), "even"),     ("Special", "s")),
         ("PROJECTILE",     5, (nums("proj-fly", 2) + nums("proj-hit", 3), "even"), None)],
    13: [("VICTORY",        6, (nums("victory", 4), "even"),     None),
         ("KO",             6, (nums("fall", 5), "even"),        None)],
    14: [("MEDIUM PUNCH REFINED", 6, (nums("med-punch", 6), "even"), None),
         ("HEAVY PUNCH REFINED",  6, (nums("heavy-punch", 6), "even"), None)],
    15: [("MEDIUM KICK REFINED",  5, (nums("med-kick", 5), "even"), None),
         ("HEAVY KICK REFINED",   6, (nums("heavy-kick", 6), "even"),None)],
    16: [("HANDGUN READY",  5, (None, None), None),
         ("HANDGUN AIM",    5, (None, None), None)],
    17: [("RIFLE READY",    6, (None, None), None),
         ("RIFLE AIM",      6, (None, None), None)],
    18: [("IDLE RELAXED",   6, (None, None), ("Idle", "i")),
         ("TALK",           4, (None, None), ("Talk", "t"))],
    19: [("WALK BACKWARD",  6, (nums("backwards", 6), "even"),   None),
         ("HANDGUN WALK",   6, (None, None),                     None)],
}
# Grupos con targets SF None se guardan en GEN/<char>/_extra/ (capas de armas,
# jump land, specials L/M, talk...): nada se tira.

def detect(path, close=5):
    im = Image.open(path).convert("RGB")
    a = np.asarray(im).astype(int)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    raw = ~((g > 170) & (r < 140) & (b < 140))       # figura = NO croma (con tolerancia AA)
    fig = ndimage.binary_fill_holes(
        ndimage.binary_closing(raw, structure=np.ones((close, close))))
    lbl, _ = ndimage.label(fig, structure=np.ones((3, 3)))
    blobs = []
    for i, sl in enumerate(ndimage.find_objects(lbl)):
        if sl is None: continue
        y0, y1, x0, x1 = sl[0].start, sl[0].stop, sl[1].start, sl[1].stop
        if y1 - y0 < 50 or x1 - x0 < 25: continue    # filtra titulos amarillos chicos
        if int((lbl[sl] == i + 1).sum()) < 1400: continue
        blobs.append((x0, y0, x1, y1, i + 1))
    # bandas por fila
    blobs.sort(key=lambda bl: (bl[1] + bl[3]) / 2)
    bands = []
    for bl in blobs:
        cy = (bl[1] + bl[3]) / 2
        if bands and abs(cy - bands[-1][0]) < 90:
            bands[-1][1].append(bl)
            bands[-1][0] = sum((x[1] + x[3]) / 2 for x in bands[-1][1]) / len(bands[-1][1])
        else:
            bands.append([cy, [bl]])
    bands = [sorted(b[1], key=lambda bl: bl[0]) for b in bands]
    return im, lbl, raw, bands

def split_groups(bands, nA, nB):
    """Grupo A = primeras bandas. Corte exacto si se puede; si no, el mas cercano a nA."""
    sizes = [len(b) for b in bands]
    best, bestd = 1, 10 ** 9
    for cut_i in range(1, len(bands)):
        d = abs(sum(sizes[:cut_i]) - nA)
        if d < bestd: best, bestd = cut_i, d
    A = [bl for b in bands[:best] for bl in b]
    B = [bl for b in bands[best:] for bl in b]
    return A, B, (len(A) != nA or len(B) != nB)

def maybe_split(grp, lbl, raw, n_expected):
    """Si el grupo trae MENOS blobs de los esperados y hay uno anormalmente ancho
    (cuadros fusionados por confeti/efectos), lo parte en el valle de densidad."""
    grp = sorted(grp, key=lambda bl: bl[0])
    while len(grp) < n_expected:
        widths = sorted(bl[2] - bl[0] for bl in grp)
        med = widths[len(widths) // 2]
        cand = max(grp, key=lambda bl: bl[2] - bl[0])
        if (cand[2] - cand[0]) < 1.6 * med: break
        x0, y0, x1, y1, bid = cand
        m = (lbl[y0:y1, x0:x1] == bid) & raw[y0:y1, x0:x1]
        cols = m.sum(axis=0)
        lo, hi = int(len(cols) * 0.25), int(len(cols) * 0.75)
        cutx = lo + int(np.argmin(cols[lo:hi]))
        def tight(c0, c1):
            sub = cols[c0:c1]
            nz = np.nonzero(sub)[0]
            if len(nz) == 0: return None
            return (x0 + c0 + int(nz[0]), y0, x0 + c0 + int(nz[-1]) + 1, y1, bid)
        a, b = tight(0, cutx), tight(cutx, len(cols))
        if not a or not b: break
        grp.remove(cand); grp += [a, b]
        grp = sorted(grp, key=lambda bl: bl[0])
    return grp

def cut(im, lbl, raw, blob):
    x0, y0, x1, y1, bid = blob
    rgb = np.asarray(im)[y0:y1, x0:x1].copy()
    mask = (lbl[y0:y1, x0:x1] == bid) & raw[y0:y1, x0:x1]   # alfa CRUDO: sin verde interior
    edge = mask & ~ndimage.binary_erosion(mask, iterations=2)
    r, g, b = rgb[..., 0].astype(int), rgb[..., 1].astype(int), rgb[..., 2].astype(int)
    spill = edge & (g > r + 30) & (g > b + 30)
    rgb[..., 1] = np.where(spill, (r + b) // 2, g).astype(np.uint8)
    out = np.dstack([rgb, (mask * 255).astype(np.uint8)])
    img = Image.fromarray(out, "RGBA")
    bb = img.getbbox()
    return img.crop(bb) if bb else img

def pick(frames, k, mode):
    n = len(frames)
    if mode == "first": return frames[:k]
    if mode == "mid":   return [frames[n // 2]] if k == 1 else pick(frames, k, "even")
    # Ataque de dos cuadros = preparacion + CONTACTO. "even" elegia primero y
    # ultimo, que suelen ser dos guardias iguales; por eso X parecia no golpear.
    if mode == "attack2" and k == 2 and n >= 3:
        return [frames[0], frames[n // 2]]
    if mode == "first_half":
        return pick(frames[:max(k, (n + 1) // 2)], k, "even")
    if n <= k: return list(frames)
    idx = [round(i * (n - 1) / (k - 1)) for i in range(k)] if k > 1 else [n // 2]
    return [frames[i] for i in idx]

def orientation_probe(img, flip=False):
    """Descriptor visual pequeno para comparar continuidad entre cuadros de KO.

    No intenta reconocer cara/cabello (eso no generaliza a robots o personajes futuros):
    compara silueta+color del cuadro nuevo tal cual y espejado contra el cuadro anterior.
    """
    rgba = img.convert("RGBA")
    if flip:
        rgba = ImageOps.mirror(rgba)
    bb = rgba.getbbox()
    rgba = rgba.crop(bb) if bb else rgba
    rgba.thumbnail((56, 56), Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    canvas.alpha_composite(rgba, ((64 - rgba.width) // 2, (64 - rgba.height) // 2))
    return np.asarray(canvas, dtype=np.float32) / 255.0

def detect_orientation_flips(frames):
    """Detecta un cambio de orientacion accidental dentro de una secuencia KO.

    El primer cuadro fija la orientacion. En cada transicion solo cambia la paridad si
    espejar mejora claramente la continuidad; una vez cambiado, conserva el espejo hasta
    que otra transicion fuerte indique lo contrario. Devuelve un bool por cuadro.
    """
    if not frames:
        return []
    flips = [False]
    parity = False
    prev = orientation_probe(frames[0], parity)
    for frame in frames[1:]:
        keep = orientation_probe(frame, parity)
        toggle = orientation_probe(frame, not parity)
        keep_cost = float(np.mean((prev - keep) ** 2))
        toggle_cost = float(np.mean((prev - toggle) ** 2))
        if toggle_cost < keep_cost * ORIENTATION_SWITCH_RATIO and keep_cost - toggle_cost > ORIENTATION_MIN_GAIN:
            parity = not parity
            current = toggle
        else:
            current = keep
        flips.append(parity)
        prev = current
    return flips

def update_frame_meta(gen_dir, names, flips):
    path = os.path.join(gen_dir, "_frame_meta.json")
    meta = json.load(open(path, encoding="utf-8")) if os.path.exists(path) else {}
    for name, flip in zip(names, flips):
        if flip:
            meta[name] = {"flipX": True}
        else:
            meta.pop(name, None)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(meta, f, indent=2, sort_keys=True)

def place_sf(img, scale, center=False):
    w = max(1, int(round(img.width * scale)))
    h = max(1, int(round(img.height * scale)))
    img = img.resize((w, h), Image.Resampling.LANCZOS)
    cv = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    y = (CANVAS // 2 - h // 2) if center else (FEET_Y - h)   # proyectiles: origen (128,128)
    cv.paste(img, (CX - w // 2, y), img)
    return cv

def place_world(img, scale, feet_y):
    w = max(1, int(round(img.width * scale)))
    h = max(1, int(round(img.height * scale)))
    img = img.resize((w, h), Image.Resampling.LANCZOS)
    cv = Image.new("RGBA", (W_CANVAS, W_CANVAS), (0, 0, 0, 0))
    cv.paste(img, (W_CANVAS // 2 - w // 2, feet_y - h), img)
    return cv

def sequence_scale(frames, target_h, fallback):
    """Una escala fija por secuencia, nunca una escala distinta por cuadro.

    Asi se corrige el zoom inconsistente entre hojas y se conserva el rebote natural
    dentro de Walk/Run/Idle. ``target_h=None`` mantiene la escala fisica del Idle para
    proyectiles y KO, cuyas siluetas no deben forzarse a una altura vertical.
    """
    if target_h is None or not frames:
        return fallback
    med = float(np.median([f.height for f in frames]))
    return target_h / med if med > 0 else fallback

def ref_world_metrics(ref_dir):
    idle = os.path.join(ref_dir, "Idle")
    f = sorted(os.listdir(idle))[0]
    im = Image.open(os.path.join(idle, f)).convert("RGBA")
    a = np.asarray(im)[..., 3] > 8
    ys, _ = np.where(a)
    return int(ys.max() - ys.min() + 1), int(ys.max()) + 1

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("sheet"); ap.add_argument("char")
    ap.add_argument("--sheet-num", type=int, default=None)
    ap.add_argument("--list", action="store_true")
    ap.add_argument("--gen", default="app/src/main/assets/STREETFIGHTER/GEN")
    ap.add_argument("--world-ref", default=None)
    ap.add_argument("--world-prefix", default="pk_")
    args = ap.parse_args()

    m = re.search(r"_(\d{1,2})_", os.path.basename(args.sheet))
    num = args.sheet_num or (int(m.group(1)) if m else None)
    if num not in SHEETS:
        sys.exit("No se qué hoja es (usa --sheet-num 1..19). Detectado: %s" % num)
    (nameA, nA, sfA, wA), (nameB, nB, sfB, wB) = SHEETS[num]

    # cierre 5 normal; si el conteo no cuadra (efectos dispersos), reintenta con 25
    im, lbl, raw, bands = detect(args.sheet, close=5)
    total = sum(len(b) for b in bands)
    if total != nA + nB:
        im2, lbl2, raw2, bands2 = detect(args.sheet, close=25)
        if abs(sum(len(b) for b in bands2) - (nA + nB)) < abs(total - (nA + nB)):
            im, lbl, raw, bands = im2, lbl2, raw2, bands2
            total = sum(len(b) for b in bands)

    A, B, warn = split_groups(bands, nA, nB)
    A = maybe_split(A, lbl, raw, nA)
    B = maybe_split(B, lbl, raw, nB)
    warn = (len(A) != nA or len(B) != nB)
    msg = "Hoja %02d: %s %d/%d + %s %d/%d" % (num, nameA, len(A), nA, nameB, len(B), nB)
    # Solo ASCII: la consola clasica de Windows usa cp1252 y no puede imprimir "⚠".
    # El aviso no debe abortar justo las hojas cuyo conteo flexible estamos tolerando.
    print(msg + ("  AVISO: conteo distinto al esperado (continuo con los que hay)" if warn else "  OK"))
    if args.list:
        for i, bl in enumerate(A + B):
            print("  blob %02d: x=%d y=%d w=%d h=%d" % (i, bl[0], bl[1], bl[2] - bl[0], bl[3] - bl[1]))
        return
    for (label, targets), grp in ((("A", sfA), A), (("B", sfB), B)):
        t = targets[0]
        projectile_short_ok = num == 12 and label == "B" and len(grp) == 4 and len(t or []) == 5
        if t and len(grp) < len(t) and not projectile_short_ok:
            sys.exit("Grupo %s tiene %d cuadros y necesita >= %d (%s). Regenera la hoja." %
                     (label, len(grp), len(t), ", ".join(t)))

    framesA = [cut(im, lbl, raw, bl) for bl in A]
    framesB = [cut(im, lbl, raw, bl) for bl in B]

    gen_dir = os.path.join(args.gen, args.char)
    os.makedirs(gen_dir, exist_ok=True)
    scale_file = os.path.join(gen_dir, "_scale.json")
    if num == 1:
        med = float(np.median([f.height for f in framesA]))
        scale = TARGET_H / med
        with open(scale_file, "w", encoding="utf-8") as f:
            json.dump({"scale": scale, "targetHeight": TARGET_H}, f)

        # Contrato del mundo. Cada animacion se calibra a la MISMA altura objetivo al
        # procesarla: las hojas de origen suelen traer distinto zoom aunque el personaje
        # sea el mismo. La escala queda fija dentro de la secuencia (no por cuadro).
        world_dir = os.path.join(args.gen, "WORLD_" + args.char)
        os.makedirs(world_dir, exist_ok=True)
        ref_h, feet = WORLD_TARGET_H, WORLD_FEET_Y
        with open(os.path.join(world_dir, "_world_scale.json"), "w", encoding="utf-8") as f:
            json.dump({"feetY": feet, "targetHeight": ref_h,
                       "strategy": "perAnimationMedian"}, f)
    elif os.path.exists(scale_file):
        scale = json.load(open(scale_file))["scale"]
    else:
        sys.exit("Falta %s: procesa primero la hoja 01 (fija la escala)." % scale_file)

    for (label, _, (targets, mode), world), group in ((SHEETS[num][0], framesA), (SHEETS[num][1], framesB)):
        sf_target_h = SF_POSE_TARGET_H.get(label, TARGET_H)
        if targets:
            if label == "PROJECTILE" and len(group) == 4 and len(targets) == 5:
                # Dos cuadros de vuelo + tres de impacto. Con cuatro efectos, el segundo
                # sirve tambien como primer impacto para conservar el fade completo.
                chosen = [group[0], group[1], group[1], group[2], group[3]]
            else:
                chosen = pick(group, len(targets), mode)
            # La escala se calcula sobre los cuadros que realmente se exportan. En
            # HURT se elige la primera mitad; medir tambien la mitad descartada era la
            # causa de que algunas reacciones quedaran hasta 25 % mas grandes.
            sf_scale = sequence_scale(chosen, sf_target_h, scale)
            # Crouch necesita una progresion explicita: algunas hojas dibujan el primer
            # cuadro mas grande y el ultimo con otro zoom. 90 -> 80 -> 68 evita que el
            # personaje primero crezca y luego parezca reducirse al flexionar las piernas.
            frame_scales = [sf_scale] * len(chosen)
            if label == "CROUCH":
                crouch_heights = [90.0, 80.0, 68.0]
                frame_scales = [target / fr.height if fr.height > 0 else sf_scale
                                for target, fr in zip(crouch_heights, chosen)]
            elif label == "CROUCH TURN":
                frame_scales = [68.0 / fr.height if fr.height > 0 else sf_scale for fr in chosen]
            elif label in ("HURT HEAD", "HURT BODY", "STUN"):
                frame_scales = [TARGET_H / fr.height if fr.height > 0 else sf_scale for fr in chosen]
            for name, fr, frame_scale in zip(targets, chosen, frame_scales):
                place_sf(fr, frame_scale, center=name.startswith("proj-")).save(
                    os.path.join(gen_dir, name + ".png"))
            if label == "KO":
                flips = detect_orientation_flips(chosen)
                update_frame_meta(gen_dir, targets, flips)
                flipped = [name for name, flip in zip(targets, flips) if flip]
                print("ORI %-22s -> flipX: %s" % (label, ", ".join(flipped) if flipped else "ninguno"))
            print("SF  %-22s -> %s" % (label, ", ".join(targets)))
        else:
            sf_scale = sequence_scale(group, sf_target_h, scale)
            ex = os.path.join(gen_dir, "_extra", label.lower().replace(" ", "-"))
            os.makedirs(ex, exist_ok=True)
            for i, fr in enumerate(group, 1):
                place_sf(fr, sf_scale).save(os.path.join(ex, "%02d.png" % i))
            print("SF  %-22s -> _extra/ (%d cuadros)" % (label, len(group)))
        if world:
            folder, letter = world
            wdir = os.path.join(args.gen, "WORLD_" + args.char, folder)
            os.makedirs(wdir, exist_ok=True)
            world_scale_file = os.path.join(args.gen, "WORLD_" + args.char, "_world_scale.json")
            if not os.path.exists(world_scale_file):
                sys.exit("Falta %s: procesa primero la hoja 01 (fija la escala mundial)." % world_scale_file)
            world_meta = json.load(open(world_scale_file, encoding="utf-8"))
            feet = world_meta["feetY"]
            wscale = sequence_scale(group, world_meta["targetHeight"], scale)
            for i, fr in enumerate(group, 1):
                place_world(fr, wscale, feet).save(
                    os.path.join(wdir, "%s%s_%d.webp" % (args.world_prefix, letter, i)), lossless=True)
            print("POW %-22s -> WORLD_%s/%s/ (%d)" % (label, args.char, folder, len(group)))

if __name__ == "__main__":
    main()
