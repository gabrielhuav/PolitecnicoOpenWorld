#!/usr/bin/env python3
"""
apply_final_audio_trims.py

1. Ejecuta los 3 recortes finales solicitados:
   - `special_prankedy_attack_1.ogg`: quitar último 5% de duración.
   - `special_prankedy_attack_3.ogg`: quitar último 2% de duración.
   - `special_prankedy_lowhp.ogg`: quitar último 10% de duración.

2. Regenera los MP3 en `tools/_audio_review/`.

3. Re-ordena y actualiza `tools/_audio_review/SUBTITULOS_FACIL.csv` conservando
   TODOS los subtítulos en español ingresados por el usuario e incluyendo los 4 hurt de Prankedy
   (hurt_1, hurt_2, hurt_3, hurt_4) para que pueda agregarles su subtítulo.
"""
import os
import subprocess
import glob

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOUNDS_DIR = os.path.join(ROOT, "app", "src", "main", "assets", "STREETFIGHTER", "SOUNDS")
REVIEW_DIR = os.path.join(ROOT, "tools", "_audio_review")
CSV_PATH = os.path.join(ROOT, "tools", "_audio_review", "SUBTITULOS_FACIL.csv")

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

def trim_last_percent(filename, percent):
    ogg_path = os.path.join(SOUNDS_DIR, filename)
    if not os.path.exists(ogg_path):
        print(f"[ERROR] No existe {ogg_path}")
        return

    dur = get_duration(ogg_path)
    if dur <= 0:
        return

    target_dur = dur * (1.0 - percent)
    temp_path = ogg_path + ".tmp.ogg"

    cmd = [
        "ffmpeg", "-y", "-i", ogg_path,
        "-t", f"{target_dur:.3f}",
        "-c:a", "libvorbis", "-ar", "44100", "-b:a", "96k", "-ac", "2",
        temp_path
    ]
    res = subprocess.run(cmd, capture_output=True)
    if res.returncode == 0:
        os.replace(temp_path, ogg_path)
        new_dur = get_duration(ogg_path)
        print(f"[OK] {filename}: {dur:.2f}s -> {new_dur:.2f}s (quitado {percent*100:.0f}%)")

        mp3_name = os.path.splitext(filename)[0] + ".mp3"
        mp3_path = os.path.join(REVIEW_DIR, mp3_name)
        subprocess.run([
            "ffmpeg", "-y", "-i", ogg_path, "-c:a", "libmp3lame", "-b:a", "128k", mp3_path
        ], capture_output=True)
    else:
        if os.path.exists(temp_path):
            os.remove(temp_path)
        print(f"[ERROR] Falló recorte de {filename}")

def update_csv():
    # Leer subtítulos existentes
    existing_subtitles = {}
    if os.path.exists(CSV_PATH):
        with open(CSV_PATH, "r", encoding="utf-8") as f:
            lines = f.readlines()
        if lines and "audio_mp3" in lines[0]:
            lines = lines[1:]
        for line in lines:
            line_str = line.strip()
            if not line_str:
                continue
            parts = line_str.split(",", 1)
            f_name = parts[0].strip()
            text = parts[1].strip() if len(parts) > 1 else ""
            existing_subtitles[f_name] = text

    # Obtener todos los MP3 de review
    mp3_files = sorted([os.path.basename(p) for p in glob.glob(os.path.join(REVIEW_DIR, "*.mp3"))])
    
    # Asegurar que hadouken.mp3 vaya primero si existe
    if "hadouken.mp3" in mp3_files:
        mp3_files.remove("hadouken.mp3")
        mp3_files = ["hadouken.mp3"] + mp3_files

    # Escribir CSV actualizado
    with open(CSV_PATH, "w", encoding="utf-8") as f:
        f.write("audio_mp3,subtitulo_espanol\n")
        for mp3 in mp3_files:
            sub = existing_subtitles.get(mp3, "")
            f.write(f"{mp3},{sub}\n")

    print(f"[CSV] {CSV_PATH} actualizado y ordenado ({len(mp3_files)} archivos).")

def main():
    print("--- 1. Aplicando recortes finales ---")
    trim_last_percent("special_prankedy_attack_1.ogg", 0.05)
    trim_last_percent("special_prankedy_attack_3.ogg", 0.02)
    trim_last_percent("special_prankedy_lowhp.ogg", 0.10)

    print("\n--- 2. Actualizando CSV fácil de subtítulos ---")
    update_csv()

if __name__ == "__main__":
    main()
