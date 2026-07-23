#!/usr/bin/env python3
"""
process_user_audio_edits.py

1. Realiza los recortes específicos de audio en `SOUNDS/*.ogg`:
   - `special_escomboy_attack.ogg`: primeros 2.5 seg.
   - `special_escomgirl_attack.ogg`: recortar últimos 0.25 seg.
   - `special_escomgirl_hurt.ogg`: recortar últimos 0.25 seg.
   - `special_la_presidenta_power.ogg`: recortar primeros 0.4 seg.
   - `special_paparazzi_5_hurt.ogg`: recortar primeros 0.25 seg.
   - `special_prankedy_attack_1.ogg`: recortar primeros 2.0 seg y últimos 0.1 seg.

2. Reemplaza `special_rey_grupero.ogg` por una copia de `special_rey_grupero_power.ogg`.

3. Procesa `SUBTITULOS_FACIL.csv` soportando el delimitador `|` y traduciendo los segmentos a inglés.

4. Actualiza las copias `.mp3` en `tools/_audio_review/`.
"""
import os
import subprocess
import shutil
import json
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOUNDS_DIR = os.path.join(ROOT, "app", "src", "main", "assets", "STREETFIGHTER", "SOUNDS")
REVIEW_DIR = os.path.join(ROOT, "tools", "_audio_review")
CSV_PATH = os.path.join(ROOT, "tools", "_audio_review", "SUBTITULOS_FACIL.csv")
VOICE_PHRASES_JSON = os.path.join(ROOT, "app", "src", "main", "assets", "STREETFIGHTER", "DATA", "voice_phrases.json")

def get_duration(file_path):
    cmd = [
        "ffprobe", "-v", "error", "-show_entries", "format=duration",
        "-of", "default=noprint_wrappers=1:nokey=1", file_path
    ]
    res = subprocess.run(cmd, capture_output=True, text=True)
    try:
        return float(res.stdout.strip())
    except ValueError:
        return 0.0

def trim_audio(filename, start_offset=0.0, cut_end_offset=0.0, fixed_duration=None):
    ogg_path = os.path.join(SOUNDS_DIR, filename)
    if not os.path.exists(ogg_path):
        print(f"No existe: {ogg_path}")
        return

    dur = get_duration(ogg_path)
    if dur <= 0:
        return

    if fixed_duration is not None:
        target_dur = fixed_duration
    else:
        target_dur = max(0.1, dur - start_offset - cut_end_offset)

    temp_path = ogg_path + ".tmp.ogg"
    
    cmd = [
        "ffmpeg", "-y", "-ss", str(start_offset), "-i", ogg_path,
        "-t", str(target_dur),
        "-c:a", "libvorbis", "-ar", "44100", "-b:a", "96k", "-ac", "2",
        temp_path
    ]
    
    res = subprocess.run(cmd, capture_output=True)
    if res.returncode == 0:
        os.replace(temp_path, ogg_path)
        new_dur = get_duration(ogg_path)
        print(f"[TRIM] Recortado {filename}: de {dur:.2f}s -> {new_dur:.2f}s")
        
        # Actualizar MP3 de review
        mp3_name = os.path.splitext(filename)[0] + ".mp3"
        mp3_path = os.path.join(REVIEW_DIR, mp3_name)
        subprocess.run([
            "ffmpeg", "-y", "-i", ogg_path, "-c:a", "libmp3lame", "-b:a", "128k", mp3_path
        ], capture_output=True)
    else:
        if os.path.exists(temp_path):
            os.remove(temp_path)
        print(f"Error recortando {filename}: {res.stderr.decode('utf-8', errors='ignore')}")

def replace_rey_grupero():
    src_ogg = os.path.join(SOUNDS_DIR, "special_rey_grupero_power.ogg")
    dst_ogg = os.path.join(SOUNDS_DIR, "special_rey_grupero.ogg")
    if os.path.exists(src_ogg):
        shutil.copy2(src_ogg, dst_ogg)
        print(f"[REPLACE] Reemplazado special_rey_grupero.ogg con special_rey_grupero_power.ogg")
        
        # Copiar MP3 también
        src_mp3 = os.path.join(REVIEW_DIR, "special_rey_grupero_power.mp3")
        dst_mp3 = os.path.join(REVIEW_DIR, "special_rey_grupero.mp3")
        if os.path.exists(src_mp3):
            shutil.copy2(src_mp3, dst_mp3)

TRANSLATION_MAP = {
    "Grrr": "Grrr",
    "¡Arrrg!": "Arrrg!",
    "Auuuuuu...": "Auuuuuu...",
    "Uuu, uuu...": "Ooo, ooo...",
    "¡Yip, yip!": "Yip, yip!",
    "Aauuuggg...": "Aauuuggg...",
    "¡Kiaa!": "Kiaa!",
    "¡Agh!": "Agh!",
    "¡Aaaah...agh!": "Aaaah...agh!",
    "¡Jajaja!": "Hahaha!",
    "¡Hoy estamos hablando de Box!": "Today we are talking about Boxing!",
    "¡Jjjjjrrr...Jjjjjrrr...Jjjj": "Jjjjjrrr...Jjjjjrrr...Jjjj",
    "¡Aggggh!": "Aggggh!",
    "¡Ahhh-aggh!": "Ahhh-aggh!",
    "¡Auhhhhh-aggggh!": "Auhhhhh-aggggh!",
    "¿Dóndeee eestán mis hijooos?": "Whereeee are my childreeen?",
    "Ayyyyy": "Ayyyyy",
    "¡Ayyyyyy mis hiiijooos!": "Ayyyyyy my childreeen!",
    "¡ZA-ZA!": "ZA-ZA!",
    "¿Qué te pasa?": "What's wrong with you?",
    "Hijo de la c... Ya te dije que no me estés molestando": "Son of a b... I told you to stop bothering me",
    "¿Eh?": "Huh?",
    "Bueno... ¿Me vas a dejar de molestar o qué?": "Well... Are you going to stop bothering me or what?",
    "Cabrón... Ya te dije que me dejes de estar chingando ¿No?": "Bastard... I already told you to stop messing with me, right?",
    "Que te calmaras ya… Párale a tu desmadre.": "Cool down already... Stop your nonsense.",
    "No olvides que saber primeros auxilios": "Don't forget: knowing first aid",
    "marca la diferencia y salva vidas": "makes the difference and saves lives",
    "Buenas... joven...": "Afternoon... kid...",
    "¿Sí sabe por qué lo detuvimos": "Do you know why we pulled you over",
    "¡AGGGGh!": "AGGGGh!",
    "¡Ya estáte,": "Stop it,",
    "ya estátee!": "stop it already!",
    "Por eso cálmate,": "So calm down,",
    "¡relájate!,": "relax!,",
    "¡ya…!": "enough...!",
    "Trabajamos permanentemente": "We work permanently",
    "en strategies de seguridad vial.": "on road safety strategies.",
    "A ver...": "Let's see...",
    "voltéate, voltéate...": "turn around, turn around...",
    "voltéate... Hazme c...": "turn around... Pay attention...",
    "¿Y qué, me vas a pegar o solo te vas a desvestir?": "So what, are you gonna hit me or just take off your clothes?",
    "¿Qué pasó...?": "What happened...?",
    "¿Y cómo o qué?": "So how or what?",
    "¿Qué va a hacer o qué órale?": "What are you gonna do or what, come on?",
    "Nos agarramos aquí a putazos": "We throw hands right here",
    "órale de una vez.": "come on, right now.",
    "-Uuuu..": "-Uuuu..",
    "-¿U qué?": "-What?",
    "¿U qué pend....?": "What the h...?",
    "Te pones al pedo todavía...": "You still act tough...",
    "No se sienta un c... eh mi perro,": "Don't act like a b... my dog,",
    "que ahorita lo...": "cause right now...",
    "ahorita lo aterrizo, eh.": "right now I'll bring you down.",
    "Esque yo podré ser ciego, pero...": "I might be blind, but...",
    "sí me doy cuenta cuando alguien es miserable, eh.": "I do notice when someone is miserable.",
    "Oye hermano...": "Hey brother...",
    "¿Me regalas una selfie?": "Can I get a selfie?",
    "Nmms... C...": "No way...",
    "Corre bien rápido este wey...": "This guy runs super fast...",
    "¡Vámonos alv, wey, vámonos alv!": "Let's get the f... outta here, man, let me out!",
    "Sistema activado, objetivo localizado.": "System activated, target located.",
    "La base de datos de virus...": "The virus database...",
    "ha sido actualizada.": "has been updated.",
    "Sí, ¿sabes qué?": "Yeah, you know what?",
    "Retírate, wey, o te rompo tu madre...": "Back off, man, or I'll beat you up...",
    "así derecho...": "straight up...",
    "Retírate, cabrón.": "Back off, bastard.",
    "Ora...": "Hey...",
    "hasta exigente te pones pen...": "even demanding you get, dumb...",
    "¿Otra vez vienes a chingar la madre c...?": "Coming to mess around again...?",
    "¡Ya dejar de chingar!": "Stop messing around!",
    "Ya estoy hasta la ptm...": "I'm fed up with this s...",
    "¿Ya me agarraste de tu puerquito, verdaa?": "You've made me your punching bag, right?",
    "Llégale,": "Get outta here,",
    "Ya estuvo, cabrón,": "That's enough, bastard,",
    "ahora sí. Vente para acá,": "now for real. Come over here,",
    "vamos a rompernos la madre.": "let's throw hands.",
    "¿O vas a salir otra vez como pinche mariquita,": "Or are you gonna run off again like a scaredy cat,",
    "wey, que sales corriendo,": "man, running away,",
    "cabrón?": "bastard?",
    "Ya, hermano, ya...": "Alright, brother, alright...",
    "Cálmate, ya.": "Calm down now.",
    "Hermano, ya cálmate": "Brother, calm down already",
    "Ya cálmate, ya.": "Calm down already.",
    "Cálmate, ya": "Calm down already",
    "Cálmate, ya, hermano": "Calm down already, brother",
    "¡Me vas a dejar inválido!": "You're gonna cripple me!",
    "¡Aaaah!": "Aaaah!",
    "La broma, pues, terminó mal...": "So the prank ended badly...",
    "¡Ummhh!": "Ummhh!",
    "Tengo... fracturado un ojo...": "I have... a fractured eye...",
    "aquí tengo también una marca del golpe...": "right here I also have a mark from the hit...",
    "Fshhh...": "Fshhh...",
    "¡Graaah!": "Graaah!",
    "¡Hraaah!": "Hraaah!",
    "¡Gaaah!": "Gaaah!",
    "¡Gah!": "Gah!"
}

def translate_segment(segment):
    s = segment.strip()
    if not s or s == "-":
        return ""
    if s in TRANSLATION_MAP:
        return TRANSLATION_MAP[s]
    # Limpiar exclamaciones simples si no hay mapeo directo
    return s.replace("¡", "").replace("!", "")

def translate_full_multiline(text):
    if not text or text == "-":
        return ""
    parts = text.split("|")
    translated_parts = [translate_segment(p) for p in parts]
    return " | ".join(translated_parts)

def process_subtitles():
    if not os.path.exists(CSV_PATH):
        return

    with open(VOICE_PHRASES_JSON, "r", encoding="utf-8") as f:
        json_data = json.load(f)

    clips_data = json_data.get("clips", {})
    count = 0

    with open(CSV_PATH, "r", encoding="utf-8") as f:
        lines = f.readlines()

    if lines and "audio_mp3" in lines[0]:
        lines = lines[1:]

    for line in lines:
        line_str = line.strip()
        if not line_str:
            continue
        
        parts = line_str.split(",", 1)
        audio_file = parts[0].strip()
        es_text = parts[1].strip() if len(parts) > 1 else ""

        if not audio_file.startswith("special_"):
            continue

        clip_key = os.path.splitext(audio_file)[0]
        
        if es_text and es_text != "-":
            en_text = translate_full_multiline(es_text)
            if clip_key not in clips_data:
                clips_data[clip_key] = {"es": "", "en": "", "draft": ""}
            clips_data[clip_key]["es"] = es_text
            clips_data[clip_key]["en"] = en_text
            count += 1
        else:
            if clip_key in clips_data:
                clips_data[clip_key]["es"] = ""
                clips_data[clip_key]["en"] = ""

    json_data["clips"] = clips_data

    with open(VOICE_PHRASES_JSON, "w", encoding="utf-8") as f:
        json.dump(json_data, f, indent=2, ensure_ascii=False)
        f.write("\n")

    print(f"[SUBTITLES] Subtítulos actualizados en voice_phrases.json: {count} clips configurados.")

def main():
    print("--- 1. Ejecutando recortes de audio ---")
    trim_audio("special_escomboy_attack.ogg", fixed_duration=2.5)
    trim_audio("special_escomgirl_attack.ogg", cut_end_offset=0.25)
    trim_audio("special_escomgirl_hurt.ogg", cut_end_offset=0.25)
    trim_audio("special_la_presidenta_power.ogg", start_offset=0.4)
    trim_audio("special_paparazzi_5_hurt.ogg", start_offset=0.25)
    trim_audio("special_prankedy_attack_1.ogg", start_offset=2.0, cut_end_offset=0.1)

    print("\n--- 2. Reemplazando audio de Rey Grupero ---")
    replace_rey_grupero()

    print("\n--- 3. Procesando subtítulos con delimitador | ---")
    process_subtitles()

if __name__ == "__main__":
    main()
