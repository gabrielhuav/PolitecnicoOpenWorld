#!/usr/bin/env python3
"""
trim_prankedy_hurts.py

Aplica los recortes de refinamiento solicitados para los 4 audios de Prankedy hurt:
1. `special_prankedy_hurt_1`: mantener solo el 65% inicial.
2. `special_prankedy_hurt_2`: quitar los últimos 0.1 seg.
3. `special_prankedy_hurt_3`: quitar los primeros 0.15 seg.
4. `special_prankedy_hurt_4`: quitar el primer 20% y el último 5%.
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

def trim_file(filename, start_percent=0.0, end_percent=0.0, start_sec=0.0, end_sec=0.0, keep_first_percent=None):
    ogg_path = os.path.join(SOUNDS_DIR, filename)
    if not os.path.exists(ogg_path):
        print(f"[ERROR] No existe {ogg_path}")
        return

    dur = get_duration(ogg_path)
    if dur <= 0:
        return

    if keep_first_percent is not None:
        start_time = 0.0
        target_dur = dur * keep_first_percent
    else:
        start_time = start_sec + (dur * start_percent)
        cut_end = end_sec + (dur * end_percent)
        target_dur = max(0.1, dur - start_time - cut_end)

    temp_path = ogg_path + ".tmp.ogg"
    
    cmd = [
        "ffmpeg", "-y", "-ss", str(start_time), "-i", ogg_path,
        "-t", str(target_dur),
        "-c:a", "libvorbis", "-ar", "44100", "-b:a", "96k", "-ac", "2",
        temp_path
    ]
    
    res = subprocess.run(cmd, capture_output=True)
    if res.returncode == 0:
        os.replace(temp_path, ogg_path)
        new_dur = get_duration(ogg_path)
        print(f"[OK] {filename}: {dur:.2f}s -> {new_dur:.2f}s (start={start_time:.2f}s, dur={target_dur:.2f}s)")
        
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
    print("--- Refinando recortes de Prankedy Hurt ---")
    # 1. special_prankedy_hurt_1: mantener 65% inicial
    trim_file("special_prankedy_hurt_1.ogg", keep_first_percent=0.65)

    # 2. special_prankedy_hurt_2: quitar últimos 0.1 seg
    trim_file("special_prankedy_hurt_2.ogg", end_sec=0.1)

    # 3. special_prankedy_hurt_3: quitar primeros 0.15 seg
    trim_file("special_prankedy_hurt_3.ogg", start_sec=0.15)

    # 4. special_prankedy_hurt_4: quitar primer 20% y último 5%
    trim_file("special_prankedy_hurt_4.ogg", start_percent=0.20, end_percent=0.05)

if __name__ == "__main__":
    main()
