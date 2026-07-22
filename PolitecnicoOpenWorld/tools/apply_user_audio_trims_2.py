#!/usr/bin/env python3
"""
apply_user_audio_trims_2.py

Aplica la segunda ronda de recortes específicos solicitados:
1. `special_escomgirl_attack.ogg`: recortar el último 0.1 seg.
2. `special_la_presidenta_power.ogg`: recortar los primeros 0.25 seg.
3. `special_paparazzi_5_hurt.ogg`: recortar a la mitad, conservar la segunda mitad.
4. `special_prankedy_attack_1.ogg`: quitar el último 1.0 seg.

Actualiza los archivos .ogg en SOUNDS/ y regenera los .mp3 en tools/_audio_review/.
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

def trim_clip(filename, start_offset=0.0, cut_end_offset=0.0, use_second_half=False):
    ogg_path = os.path.join(SOUNDS_DIR, filename)
    if not os.path.exists(ogg_path):
        print(f"[ERROR] No existe {ogg_path}")
        return

    dur = get_duration(ogg_path)
    if dur <= 0:
        return

    if use_second_half:
        start_time = dur / 2.0
        target_dur = dur - start_time
    else:
        start_time = start_offset
        target_dur = max(0.1, dur - start_offset - cut_end_offset)

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
        print(f"[TRIM] {filename}: {dur:.2f}s -> {new_dur:.2f}s (start={start_time:.2f}s, dur={target_dur:.2f}s)")
        
        # Regenerar MP3 de review
        mp3_name = os.path.splitext(filename)[0] + ".mp3"
        mp3_path = os.path.join(REVIEW_DIR, mp3_name)
        subprocess.run([
            "ffmpeg", "-y", "-i", ogg_path, "-c:a", "libmp3lame", "-b:a", "128k", mp3_path
        ], capture_output=True)
    else:
        if os.path.exists(temp_path):
            os.remove(temp_path)
        print(f"[ERROR] Falló recorte {filename}: {res.stderr.decode('utf-8', errors='ignore')}")

def main():
    print("--- Recortando archivos de audio según instrucciones ---")
    # 1. special_escomgirl_attack: recortar el último 0.1 seg
    trim_clip("special_escomgirl_attack.ogg", cut_end_offset=0.1)

    # 2. special_la_presidenta_power: recortar los primeros 0.25 seg
    trim_clip("special_la_presidenta_power.ogg", start_offset=0.25)

    # 3. special_paparazzi_5_hurt: recortar a la mitad, usar la última mitad
    trim_clip("special_paparazzi_5_hurt.ogg", use_second_half=True)

    # 4. special_prankedy_attack_1: quitar el último segundo
    trim_clip("special_prankedy_attack_1.ogg", cut_end_offset=1.0)

if __name__ == "__main__":
    main()
