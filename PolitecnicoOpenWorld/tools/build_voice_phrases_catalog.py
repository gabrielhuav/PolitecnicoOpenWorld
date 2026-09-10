#!/usr/bin/env python3
"""Genera/actualiza el CATALOGO DE FRASES por clip de voz (2026-07-20).

Salida: app/src/main/assets/STREETFIGHTER/DATA/voice_phrases.json
  { "clips": { "<archivo sin .ogg>": {"es": "...", "en": "...", "draft": "..."} } }

- "es"/"en": la frase CURADA que se mostrara de subtitulo (el dueno la escribe/valida).
  Vacia = ese clip no muestra subtitulo (gritos/SFX no lo necesitan).
- "draft": transcripcion Whisper de referencia (NUNCA se muestra en el juego).

Fuentes: la tabla de tools/sf_audio_audit_report.md (columnas frase inline + Whisper).
MERGE: si el JSON ya existe, los "es"/"en" NO vacios existentes SE CONSERVAN (el dueno
manda); solo se refrescan los drafts y se agregan clips nuevos. Regenerar el reporte
primero si cambiaron los .ogg: python tools/sf_audio_audit.py
"""
import json
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REPORT = os.path.join(ROOT, "tools", "sf_audio_audit_report.md")
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "STREETFIGHTER", "DATA", "voice_phrases.json")

# Alucinaciones tipicas de Whisper sobre musica/gritos: no sirven ni de borrador.
JUNK = ("amara.org", "suscríbete", "suscribete", "dale like")


def main() -> None:
    rows = []
    for line in open(REPORT, encoding="utf-8"):
        m = re.match(r"\| `([^`]+)\.ogg` \| [\d.]+ \| \d+ \| \d+ \| [^|]+ \| [^|]+ \| ([^|]*) \| ([^|]*) \|", line)
        if m:
            file, inline, draft = m.group(1), m.group(2).strip(), m.group(3).strip()
            rows.append((file, "" if inline == "—" else inline, "" if draft == "—" else draft))

    existing = {}
    if os.path.exists(OUT):
        existing = json.load(open(OUT, encoding="utf-8")).get("clips", {})

    clips = {}
    for file, inline, draft in rows:
        if not file.startswith("special_"):
            continue  # SFX globales: nunca llevan subtitulo
        if any(j in draft.lower() for j in JUNK):
            draft = ""
        old = existing.get(file, {})
        clips[file] = {
            "es": old.get("es") or inline,   # lo curado por el dueno manda
            "en": old.get("en", ""),
            "draft": draft,
        }

    payload = {
        "_readme": (
            "FRASES por clip de voz de TITULACIÓN POR COMBATE. Escribe/valida 'es' (y 'en' para "
            "paridad) escuchando tools/_audio_review/<clip>.mp3; 'draft' es Whisper (solo "
            "referencia). 'es' vacia = sin subtitulo. Al terminar de curar: poner "
            "voiceSubtitlesEnabled = true en StreetFighterViewModel. Regenerable con "
            "tools/build_voice_phrases_catalog.py (conserva lo curado)."
        ),
        "clips": dict(sorted(clips.items())),
    }
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)
    curated = sum(1 for c in clips.values() if c["es"])
    print(f"{OUT}: {len(clips)} clips ({curated} con frase 'es', resto por curar)")


if __name__ == "__main__":
    main()
