#!/usr/bin/env python3
"""
trim_prankedy_hurts_round2.py

Aplica los recortes finales para los audios de Prankedy hurt:
1. `special_prankedy_hurt_1`: quitar el último 5% de duración.
2. `special_prankedy_hurt_2`: quitar el último 5% de duración.
3. `special_prankedy_hurt_3`: quitar el último 5% de duración.
"""
import os
import subprocess

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOUNDS_DIR = os.path.join(ROOT, "app", "src", "main", "assets", "STREETFIGHTER", "SOUNDS")
REVIEW_DIR = os.path.join(ROOT, "tools", "_audio_review")

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

def trim_last_5_percent(filename):
    ogg_path = os.path.join(SOUNDS_DIR, filename)
    if not os.path.exists(ogg_path):
        print(f"[ERROR] No existe {ogg_path}")
        return

    dur = get_duration(ogg_path)
    if dur <= 0:
        return

    target_dur = dur * 0.95
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
        print(f"[OK] {filename}: {dur:.2f}s -> {new_dur:.2f}s")

        mp3_name = os.path.splitext(filename)[0] + ".mp3"
        mp3_path = os.path.join(REVIEW_DIR, mp3_name)
        subprocess.run([
            "ffmpeg", "-y", "-i", ogg_path, "-c:a", "libmp3lame", "-b:a", "128k", mp3_path
        ], capture_output=True)
    else:
        if os.path.exists(temp_path):
            os.remove(temp_path)
        print(f"[ERROR] Falló recorte de {filename}")

def main():
    print("--- Recortando último 5% a Prankedy Hurt 1, 2 y 3 ---")
    trim_last_5_percent("special_prankedy_hurt_1.ogg")
    trim_last_5_percent("special_prankedy_hurt_2.ogg")
    trim_last_5_percent("special_prankedy_hurt_3.ogg")

if __name__ == "__main__":
    main()
