#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Auditoria de los atlas de peleador de TITULACIÓN POR COMBATE (DATA/*.json + IMAGES/*.webp).

Comprueba lo que ninguna prueba manual ve: rects que se salen de la hoja, animaciones que
citan cuadros inexistentes, cuadros 100 % transparentes, cuadros repetidos pixel a pixel
(animacion congelada) y estados del motor sin animacion.

USO:
    python tools/audit_sf_fighters.py            # todos los peleadores dedicados
    python tools/audit_sf_fighters.py prankedy   # solo uno
    python tools/audit_sf_fighters.py --strict   # los avisos tambien devuelven codigo != 0

Codigo de salida 0 = sin problemas duros. Pensado para correr despues de
tools/pack_sf_character.py, que es cuando un atlas puede quedar mal sin que se note.

⚠️ 2026-08-03: reescrito. La version anterior buscaba IMAGES/*.png y SfModels.kt en `app/`;
las hojas pasaron a WebP (atlas_to_webp.py) y el modelo se movio a `:shared` en la migracion
KMP, asi que llevaba tiempo sin poder correr.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from collections import defaultdict
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "app" / "src" / "main" / "assets" / "STREETFIGHTER" / "DATA"
IMAGES = ROOT / "app" / "src" / "main" / "assets" / "STREETFIGHTER" / "IMAGES"
MODELS = (
    ROOT / "shared" / "src" / "commonMain" / "kotlin" / "ovh" / "gabrielhuav" / "pow"
    / "domain" / "models" / "streetfighter" / "SfModels.kt"
)

# Cuadros que el motor dibuja POR NOMBRE, sin pasar por una animacion: el renderer de
# proyectiles los pide directo (SfSceneRenderer). Salen como "huerfanos" y no lo son.
FRAMES_SIN_ANIMACION = {"proj-fly-1", "proj-fly-2", "proj-hit-1", "proj-hit-2", "proj-hit-3"}

# SfFrameCatalog SINTETIZA la animacion "stun" a partir de los cuadros stun-1/2/3 porque
# ningun JSON la trae. Que falte NO es un defecto; que los 3 cuadros sean iguales, si.
ANIMACIONES_SINTETIZADAS = {"stun"}


def leer_modelo() -> tuple[list[tuple[str, str]], dict[str, str], dict[str, int]]:
    texto = MODELS.read_text(encoding="utf-8")
    pares = sorted(set(re.findall(
        r'"STREETFIGHTER/DATA/([a-z0-9_]+)\.json",\s*"STREETFIGHTER/IMAGES/([A-Za-z0-9_]+\.webp)"',
        texto,
    )))
    estados = dict(re.findall(r'^\s+([A-Z_0-9]+)\("([a-zA-Z0-9]+)"\),?\s*$', texto, re.MULTILINE))
    # bonusPowerCount por peleador: sin el, los BONUS_POWER_* salen como "faltantes" en los
    # 15 peleadores que a proposito no tienen poderes extra. Se lee del BLOQUE de cada entrada
    # del enum, que va desde su ruta DATA hasta el inicio de la siguiente entrada.
    bonus: dict[str, int] = {}
    inicios = [(m.group(1), m.start()) for m in
               re.finditer(r'"STREETFIGHTER/DATA/([a-z0-9_]+)\.json"', texto)]
    for i, (nombre, pos) in enumerate(inicios):
        fin = inicios[i + 1][1] if i + 1 < len(inicios) else len(texto)
        encontrado = re.search(r"bonusPowerCount\s*=\s*(\d+)", texto[pos:fin])
        # sf_template.json lo comparten varios ids; nos quedamos con el mayor declarado.
        cuenta = int(encontrado.group(1)) if encontrado else 0
        bonus[nombre] = max(bonus.get(nombre, 0), cuenta)
    return pares, estados, bonus


def auditar(nombre: str, hoja: str, estados: dict[str, str], bonus: dict[str, int]) -> dict:
    ruta_json, ruta_img = DATA / f"{nombre}.json", IMAGES / hoja
    r: dict = {"nombre": nombre, "duros": [], "avisos": []}
    if not ruta_json.exists():
        r["duros"].append(f"falta {ruta_json.relative_to(ROOT)}")
        return r
    if not ruta_img.exists():
        r["duros"].append(f"falta {ruta_img.relative_to(ROOT)}")
        return r

    d = json.loads(ruta_json.read_text(encoding="utf-8"))
    cuadros, anims = d.get("frames", {}), d.get("animations", {})
    im = Image.open(ruta_img).convert("RGBA")
    ancho, alto = im.size
    r["hoja"] = f"{ancho}x{alto}"
    r["mb"] = round(ruta_img.stat().st_size / 1_000_000, 1)
    r["cuadros"] = len(cuadros)
    r["anims"] = len(anims)

    # 1) rects fuera de la hoja -> el motor dibujaria basura o se caeria
    fuera = [n for n, f in cuadros.items()
             if f["src"][0] < 0 or f["src"][1] < 0
             or f["src"][0] + f["src"][2] > ancho or f["src"][1] + f["src"][3] > alto]
    if fuera:
        r["duros"].append(f"{len(fuera)} rects fuera de la hoja: {', '.join(sorted(fuera)[:5])}")

    # 2) animaciones que citan cuadros que no existen
    usados: set[str] = set()
    rotas = []
    for clave, pasos in anims.items():
        for paso in pasos:
            usados.add(paso[0])
            if paso[0] not in cuadros:
                rotas.append(f"{clave}->{paso[0]}")
    if rotas:
        r["duros"].append(f"{len(rotas)} pasos citan cuadros inexistentes: {', '.join(rotas[:5])}")

    # 3) cuadros en blanco y cuadros identicos pixel a pixel
    blancos, por_pixeles = [], defaultdict(list)
    for n, f in cuadros.items():
        x, y, w, h = f["src"]
        if n in fuera:
            continue
        recorte = im.crop((x, y, x + w, y + h))
        if recorte.getchannel("A").getextrema()[1] == 0:
            blancos.append(n)
        else:
            por_pixeles[recorte.tobytes()].append(n)
    if blancos:
        r["duros"].append(f"{len(blancos)} cuadros 100% transparentes: {', '.join(sorted(blancos)[:5])}")

    repetidos = [g for g in por_pixeles.values() if len(g) > 1]
    for grupo in repetidos:
        r["avisos"].append(f"cuadros identicos (animacion congelada): {'/'.join(sorted(grupo))}")

    # 4) cuadros que nadie usa (descontando los que dibuja el renderer por nombre)
    huerfanos = sorted(set(cuadros) - usados - FRAMES_SIN_ANIMACION)
    if huerfanos:
        r["avisos"].append(
            f"{len(huerfanos)} cuadros empaquetados que ninguna animacion usa: "
            f"{', '.join(huerfanos[:8])}{'...' if len(huerfanos) > 8 else ''}"
        )

    # 5) estados del motor sin animacion, descontando los sintetizados y los poderes que
    #    este peleador no tiene
    n_bonus = bonus.get(nombre, 0)
    faltantes = []
    for estado, clave in estados.items():
        if clave in anims and anims[clave]:
            continue
        if clave in ANIMACIONES_SINTETIZADAS:
            continue
        m = re.fullmatch(r"BONUS_POWER_(\d+)", estado)
        if m and int(m.group(1)) > n_bonus:
            continue
        faltantes.append(estado)
    if faltantes:
        r["avisos"].append(f"sin animacion: {', '.join(sorted(faltantes))}")

    return r


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("char", nargs="?", help="solo este peleador (p. ej. prankedy)")
    ap.add_argument("--strict", action="store_true", help="los avisos tambien fallan")
    args = ap.parse_args()

    pares, estados, bonus = leer_modelo()
    if args.char:
        pares = [p for p in pares if p[0] == args.char]
        if not pares:
            print(f"'{args.char}' no tiene atlas dedicado en SfModels.kt")
            return 2

    print(f"Peleadores con atlas dedicado: {len(pares)}\n")
    print(f"{'peleador':<24}{'hoja':>12}{'MB':>6}{'cuadros':>9}{'anims':>7}{'duros':>7}{'avisos':>8}")
    print("-" * 73)

    resultados = [auditar(n, h, estados, bonus) for n, h in pares]
    for r in resultados:
        print(f"{r['nombre']:<24}{r.get('hoja', '-'):>12}{r.get('mb', 0):>6}"
              f"{r.get('cuadros', 0):>9}{r.get('anims', 0):>7}"
              f"{len(r['duros']):>7}{len(r['avisos']):>8}")

    duros = [r for r in resultados if r["duros"]]
    avisos = [r for r in resultados if r["avisos"]]

    if duros:
        print("\n=== PROBLEMAS DUROS (el juego se ve mal o se cae) ===")
        for r in duros:
            print(f"\n{r['nombre']}:")
            for linea in r["duros"]:
                print(f"  - {linea}")
    else:
        print("\nSin problemas duros: todo rect cae dentro de su hoja, toda animacion cita "
              "cuadros que existen y no hay cuadros en blanco.")

    if avisos:
        print("\n=== AVISOS (arte incompleta o desperdiciada) ===")
        for r in avisos:
            print(f"\n{r['nombre']}:")
            for linea in r["avisos"]:
                print(f"  - {linea}")

    if duros:
        return 1
    return 1 if (args.strict and avisos) else 0


if __name__ == "__main__":
    sys.exit(main())
