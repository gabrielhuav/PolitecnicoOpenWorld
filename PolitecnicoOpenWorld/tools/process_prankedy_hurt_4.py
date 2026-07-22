#!/usr/bin/env python3
"""
process_prankedy_hurt_4.py

Procesa `prankedy hurt 4.mkv`:
Recorta los primeros 0.6 seg y el último 0.1 seg.
Genera `special_prankedy_hurt_4.ogg` (SOUNDS/) y `special_prankedy_hurt_4.mp3` (_audio_review/).
"""
import os
import subprocess

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCES_DIR = os.path.join(os.path.dirname(ROOT), "nuevoMaterial18JUL", "Audios")
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

def process_hurt_4():
    src_path = os.path.join(SOURCES_DIR, "prankedy hurt 4.mkv")
    if not os.path.exists(src_path):
        print(f"[ERROR] No existe archivo fuente: {src_path}")
        return

    dur = get_duration(src_path)
    start_offset = 0.6
    cut_end_offset = 0.1
    target_dur = max(0.1, dur - start_offset - cut_end_offset)

    ogg_target = os.path.join(SOUNDS_DIR, "special_prankedy_hurt_4.ogg")
    mp3_target = os.path.join(REVIEW_DIR, "special_prankedy_hurt_4.mp3")

    cmd_ogg = [
        "ffmpeg", "-y", "-ss", str(start_offset), "-i", src_path,
        "-t", str(target_dur),
        "-af", "loudnorm=I=-16:TP=-1.5:LRA=11",
        "-c:a", "libvorbis", "-ar", "44100", "-b:a", "96k", "-ac", "2",
        ogg_target
    ]
    res_ogg = subprocess.run(cmd_ogg, capture_output=True)
    if res_ogg.returncode == 0:
        final_dur = get_duration(ogg_target)
        print(f"[OK] special_prankedy_hurt_4.ogg creado ({final_dur:.2f}s, start={start_offset}s)")

        cmd_mp3 = [
            "ffmpeg", "-y", "-i", ogg_target,
            "-c:a", "libmp3lame", "-b:a", "128k",
            mp3_target
        ]
        subprocess.run(cmd_mp3, capture_output=True)
    else:
        print(f"[ERROR] Falló conversión OGG: {res_ogg.stderr.decode('utf-8', errors='ignore')}")

if __name__ == "__main__":
    process_hurt_4()
