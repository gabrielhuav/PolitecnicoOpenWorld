#!/usr/bin/env python3
"""
fix_attack_1_restore.py

Restaura `special_prankedy_attack_1.ogg` desde su fuente original `Prankedy Attack 1.mkv`
a su duración correcta de 2.71s y regenera su MP3 de revisión.
"""
import os
import subprocess

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCES_DIR = os.path.join(os.path.dirname(ROOT), "nuevoMaterial18JUL", "Audios")
SOUNDS_DIR = os.path.join(ROOT, "app", "src", "main", "assets", "STREETFIGHTER", "SOUNDS")
REVIEW_DIR = os.path.join(ROOT, "tools", "_audio_review")

src_mkv = os.path.join(SOURCES_DIR, "Prankedy Attack 1.mkv")
target_ogg = os.path.join(SOUNDS_DIR, "special_prankedy_attack_1.ogg")
target_mp3 = os.path.join(REVIEW_DIR, "special_prankedy_attack_1.mp3")

# Duration sequence for Attack 1:
# Original mkv duration: 6.91s
# Trim 1: start=2.0s, cut_end=0.1s -> dur=4.81s
# Trim 2: cut_end=1.0s -> dur=3.81s
# Trim 3: keep 75% -> dur=2.86s
# Trim 4: cut 5% end -> dur=2.71s (start=2.0s, target_dur=2.71s)

cmd_ogg = [
    "ffmpeg", "-y", "-ss", "2.00", "-i", src_mkv,
    "-t", "2.71",
    "-af", "loudnorm=I=-16:TP=-1.5:LRA=11",
    "-c:a", "libvorbis", "-ar", "44100", "-b:a", "96k", "-ac", "2",
    target_ogg
]
subprocess.run(cmd_ogg, capture_output=True)

cmd_mp3 = [
    "ffmpeg", "-y", "-i", target_ogg,
    "-c:a", "libmp3lame", "-b:a", "128k",
    target_mp3
]
subprocess.run(cmd_mp3, capture_output=True)

print(f"[OK] special_prankedy_attack_1.ogg restaurado a 2.71s.")
