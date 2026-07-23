#!/usr/bin/env python3
"""
sf_apply_simple_subtitles.py

Lee `tools/_audio_review/SUBTITULOS_FACIL.csv` separando únicamente por la PRIMERA coma
para conservar comas dentro de las frases (ej. "Uuu, uuu...").
Traduce frases de español a inglés y actualiza `app/src/main/assets/STREETFIGHTER/DATA/voice_phrases.json`.
"""
import os
import json
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CSV_PATH = os.path.join(ROOT, "tools", "_audio_review", "SUBTITULOS_FACIL.csv")
VOICE_PHRASES_JSON = os.path.join(ROOT, "app", "src", "main", "assets", "STREETFIGHTER", "DATA", "voice_phrases.json")

# Traducciones manuales/reglas directas para gritos y frases
TRANSLATIONS = {
    "Grrr": "Grrr",
    "¡Arrrg!": "Arrrg!",
    "Auuuuuu...": "Auuuuuu...",
    "Uuu, uuu...": "Ooo, ooo...",
    "¡Yip, yip!": "Yip, yip!",
    "Buenas joven, ¿si sabe porque lo detuvimos?": "Afternoon, kid. Do you know why we pulled you over?",
    "¡Está prohibido beber en la vía pública!": "Drinking in public is prohibited!",
    "¿Se cree más chingón que nosotros o qué joven?": "You think you're tougher than us or what, kid?",
    "Si dices policía, me comprometí como mujer a que la ciudadanía sintiera una mejor seguridad": "If you say police - as a woman I committed to making citizens feel safer",
    "Porque patria se escribe con A de mujer": "Because 'homeland' is spelled with an A - the A of woman",
    "No olvides que saber primeros auxilios marca la diferencia y salva vidas.": "Don't forget: knowing first aid makes the difference and saves lives.",
    "¡Ya me agarraste de tu puerquito!": "You've made me your punching bag!"
}

def translate_es_to_en(text: str) -> str:
    text_clean = text.strip()
    if not text_clean or text_clean == "-":
        return ""
    if text_clean in TRANSLATIONS:
        return TRANSLATIONS[text_clean]
    
    # Si es una onomatopeya o exclamación simple, quitar ¡!
    res = text_clean.replace("¡", "").replace("!", "")
    return res

def main():
    if not os.path.exists(CSV_PATH):
        print(f"Error: {CSV_PATH} no existe.")
        return

    with open(VOICE_PHRASES_JSON, "r", encoding="utf-8") as f:
        json_data = json.load(f)

    clips_data = json_data.get("clips", {})
    updated_count = 0
    curated_es_count = 0

    with open(CSV_PATH, "r", encoding="utf-8") as f:
        lines = f.readlines()

    # Ignorar encabezado si existe
    if lines and "audio_mp3" in lines[0]:
        lines = lines[1:]

    for line in lines:
        line_str = line.strip()
        if not line_str:
            continue
        
        # Separar ÚNICAMENTE por la primera coma
        parts = line_str.split(",", 1)
        audio_file = parts[0].strip()
        es_text = parts[1].strip() if len(parts) > 1 else ""

        # Omitir SFX globales que no son clips special_
        if not audio_file.startswith("special_"):
            continue

        clip_key = os.path.splitext(audio_file)[0]
        
        # Si el usuario escribió un texto
        if es_text and es_text != "-":
            en_text = translate_es_to_en(es_text)
            if clip_key not in clips_data:
                clips_data[clip_key] = {"es": "", "en": "", "draft": ""}
            
            clips_data[clip_key]["es"] = es_text
            clips_data[clip_key]["en"] = en_text
            updated_count += 1
            curated_es_count += 1
            print(f"  [+] {clip_key}: es='{es_text}' | en='{en_text}'")
        else:
            # Si se dejó vacío o se puso "-", la frase en es/en queda vacía
            if clip_key in clips_data:
                clips_data[clip_key]["es"] = ""
                clips_data[clip_key]["en"] = ""

    json_data["clips"] = clips_data

    with open(VOICE_PHRASES_JSON, "w", encoding="utf-8") as f:
        json.dump(json_data, f, indent=2, ensure_ascii=False)
        f.write("\n")

    print(f"\n[OK] Proceso completado. Total clips en el JSON: {len(clips_data)}. Clips curados con texto: {curated_es_count}")

if __name__ == "__main__":
    main()
