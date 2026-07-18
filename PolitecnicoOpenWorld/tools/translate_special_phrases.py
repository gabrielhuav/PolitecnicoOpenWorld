#!/usr/bin/env python3
"""
translate_special_phrases.py — ayuda a corregir/traducir frases special.

Lee tools/sf_voice_scrape/special_phrases_catalog.json
Escribe/actualiza phrase_en a partir de phrase_es (si --auto).
Con --from-stt aplica un JSON de STT {fighter: {es, en?}}.

Uso:
  python PolitecnicoOpenWorld/tools/translate_special_phrases.py --list
  python PolitecnicoOpenWorld/tools/translate_special_phrases.py --set LA_LLORONA "¡Ay, mis hijos!" "Oh, my children!"
  python PolitecnicoOpenWorld/tools/build_special_phrases_pack.py   # regenerar runtime + MP3
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

CATALOG = Path(__file__).resolve().parent / "sf_voice_scrape" / "special_phrases_catalog.json"

# Traducciones de referencia (puedes ampliar)
AUTO_EN = {
    "¡Ey, mira esto!": "Hey, check this out!",
    "¡Paparazzi, paparazzi!": "Paparazzi, paparazzi!",
    "¡Cámara, cámara!": "Camera, camera!",
    "¡Fuera de mi tienda!": "Get out of my store!",
    "¡Que suene la cumbia!": "Crank up the cumbia!",
    "¡Ay, mis hijos!": "Oh, my children!",
    "¡Buenos días!": "Good morning!",
    "¡No hay escape!": "There is no escape!",
    "¡El viento obedece!": "The wind obeys!",
    "¡Nadie me detiene!": "No one can stop me!",
    "¡Vamos ESCOM!": "Let's go ESCOM!",
    "¡No te confíes!": "Don't get cocky!",
    "Sistema activado": "System activated",
    "¡Urgencias!": "Emergency!",
    "¡A salvar vidas!": "Saving lives!",
    "¡Alto, policía!": "Freeze, police!",
    "¡Deténgase!": "Stop right there!",
    "¡Dispérsense!": "Disperse!",
    "¡Área acordonada!": "Area sealed!",
    "¡Formación!": "Fall in!",
}


def load() -> dict:
    return json.loads(CATALOG.read_text(encoding="utf-8"))


def save(data: dict) -> None:
    CATALOG.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--list", action="store_true")
    ap.add_argument("--set", nargs=3, metavar=("FIGHTER", "ES", "EN"))
    ap.add_argument("--auto", action="store_true", help="Rellena EN desde tabla AUTO_EN")
    args = ap.parse_args()
    data = load()
    phrases = data["phrases"]

    if args.list:
        for k, v in phrases.items():
            print(f"{k:28} ES={v['phrase_es']!r:40} EN={v['phrase_en']!r}")
        return

    if args.set:
        fid, es, en = args.set
        if fid not in phrases:
            raise SystemExit(f"unknown fighter {fid}")
        phrases[fid]["phrase_es"] = es
        phrases[fid]["phrase_en"] = en
        phrases[fid]["phrase_hud"] = (
            es.upper()
            .replace("Á", "A").replace("É", "E").replace("Í", "I")
            .replace("Ó", "O").replace("Ú", "U").replace("Ñ", "N")
            .replace("¡", "").replace("!", "").replace(",", "").replace(".", "")
        )
        save(data)
        print(f"updated {fid}")
        return

    if args.auto:
        n = 0
        for k, v in phrases.items():
            es = v.get("phrase_es", "")
            if es in AUTO_EN:
                v["phrase_en"] = AUTO_EN[es]
                n += 1
        save(data)
        print(f"auto-translated {n} entries")
        return

    ap.print_help()


if __name__ == "__main__":
    main()
