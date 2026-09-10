#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Ciclo COMPLETO de un peleador de TITULACIÓN POR COMBATE, en un solo comando.

Encadena lo que hasta ahora habia que invocar a mano ~32 veces por personaje:

    hojas croma de newSFAssets/<Carpeta>/   (las que genera ChatGPT)
      -> tools/slice_sf_chroma_sheets.py    (una vez por hoja, la 01 SIEMPRE primero)
      -> tools/pack_sf_character.py         (arma IMAGES/<Titulo>.png + DATA/<char>.json)
      -> tools/atlas_to_webp.py             (WebP lossless, ~25 % menos)
      -> tools/audit_sf_fighters.py <char>  (verifica que el atlas quedo sano)

USO:
    python tools/build_sf_character.py CharroNegro charronegro "CharroNegro"
    python tools/build_sf_character.py CharroNegro charronegro "CharroNegro" --dry-run
    python tools/build_sf_character.py CharroNegro charronegro "CharroNegro" --solo-hojas 1,10

  <Carpeta>  nombre de la carpeta dentro de newSFAssets/ (mayusculas como esten)
  <char>     clave interna en minusculas, la de DATA/<char>.json
  <Titulo>   nombre del PNG en IMAGES/, sin extension

POR QUE LA HOJA 01 VA PRIMERO: fija la escala del personaje en <gen>/<char>/_scale.json y
todas las demas se calibran contra ella. Si se procesa otra antes, el personaje cambia de
tamano al cambiar de accion. El slicer detecta el numero de hoja del nombre del archivo
(<Personaje>_NN_Grupo_Grupo.png), asi que basta con ordenarlas.

Los intermedios van a newSFAssets/GEN_<char>_intermedio/ y NO a assets/: la regla 09 §12 es
no committear los cuadros sueltos dentro del APK.
"""
from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NEW_ASSETS = ROOT.parent / "newSFAssets"
TOOLS = ROOT / "tools"


def hojas_de(carpeta: Path) -> list[tuple[int, Path]]:
    """Hojas ordenadas por su numero (<Personaje>_NN_...). La 01 queda primera sola."""
    encontradas = []
    for p in sorted(carpeta.glob("*.png")):
        m = re.search(r"_(\d{1,2})_", p.name)
        if m:
            encontradas.append((int(m.group(1)), p))
    return sorted(encontradas, key=lambda t: t[0])


def correr(cmd: list[str], dry: bool) -> bool:
    print(f"\n$ {' '.join(cmd)}")
    if dry:
        return True
    r = subprocess.run(cmd, cwd=ROOT)
    if r.returncode != 0:
        print(f"!! fallo con codigo {r.returncode}", file=sys.stderr)
        return False
    return True


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("carpeta", help="carpeta dentro de newSFAssets/ (p. ej. CharroNegro)")
    ap.add_argument("char", help="clave interna en minusculas (p. ej. charronegro)")
    ap.add_argument("titulo", help="nombre del PNG en IMAGES/ (p. ej. CharroNegro)")
    ap.add_argument("--gen", default=None, help="raiz de intermedios; por omision newSFAssets/GEN_<char>_intermedio")
    ap.add_argument("--solo-hojas", default=None, help="lista de numeros de hoja, p. ej. 1,10,29")
    ap.add_argument("--saltar-webp", action="store_true", help="deja el atlas en PNG")
    ap.add_argument("--dry-run", action="store_true", help="imprime los comandos y no ejecuta nada")
    args = ap.parse_args()

    origen = NEW_ASSETS / args.carpeta
    if not origen.is_dir():
        print(f"No existe {origen}", file=sys.stderr)
        return 2

    gen = Path(args.gen) if args.gen else (NEW_ASSETS / f"GEN_{args.char}_intermedio")

    todas = hojas_de(origen)
    if not todas:
        print(f"No encontre hojas con formato <Personaje>_NN_... en {origen}", file=sys.stderr)
        return 2

    if args.solo_hojas:
        querido = {int(x) for x in args.solo_hojas.split(",")}
        hojas = [(n, p) for n, p in todas if n in querido]
        # Aun asi la 01 tiene que ir primero si esta pedida, y hojas_de ya la deja delante.
        if not hojas:
            print(f"Ninguna de las hojas {sorted(querido)} esta en {origen}", file=sys.stderr)
            return 2
        if 1 not in querido and not (gen / args.char / "_scale.json").exists():
            print("Aviso: no vas a procesar la hoja 01 y no hay _scale.json previo; la escala "
                  "del personaje no esta fijada y los cuadros pueden salir de otro tamano.",
                  file=sys.stderr)
    else:
        hojas = todas

    print(f"Peleador : {args.char} ({args.titulo})")
    print(f"Hojas    : {len(hojas)} de {len(todas)} en {origen.relative_to(ROOT.parent)}")
    print(f"Intermed.: {gen.relative_to(ROOT.parent)}")
    print(f"Orden    : {', '.join(str(n) for n, _ in hojas)}")

    for num, ruta in hojas:
        cmd = [sys.executable, str(TOOLS / "slice_sf_chroma_sheets.py"),
               str(ruta), args.char, "--sheet-num", str(num), "--gen", str(gen)]
        if not correr(cmd, args.dry_run):
            print(f"\nSe detuvo en la hoja {num} ({ruta.name}). No sigo: empaquetar con cuadros "
                  f"incompletos produce un atlas que parece bueno y no lo es.", file=sys.stderr)
            return 1

    if not correr([sys.executable, str(TOOLS / "pack_sf_character.py"),
                   args.char, args.titulo, "--gen", str(gen)], args.dry_run):
        return 1

    if not args.saltar_webp:
        if not correr([sys.executable, str(TOOLS / "atlas_to_webp.py")], args.dry_run):
            return 1

    if not correr([sys.executable, str(TOOLS / "audit_sf_fighters.py"), args.char], args.dry_run):
        print("\nEl atlas se armo pero la auditoria encontro problemas duros. Revisalos antes "
              "de commitear.", file=sys.stderr)
        return 1

    print(f"\nListo: {args.char}. Revisa el resumen de la auditoria de arriba.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
