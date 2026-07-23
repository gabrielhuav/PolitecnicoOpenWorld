#!/usr/bin/env python3
"""
restore_and_fix_attack2.py

Restaura `special_prankedy_attack_2.ogg` desde su fuente original `Prankedy Attack 2.mkv`,
detecta la pausa/hueco mudo y la elimina uniendo las partes habladas de forma continua.
"""
import os
import subprocess
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCES_DIR = os.path.join(os.path.dirname(ROOT), "nuevoMaterial18JUL", "Audios")
SOUNDS_DIR = os.path.join(ROOT, "app", "src", "main", "assets", "STREETFIGHTER", "SOUNDS")
REVIEW_DIR = os.path.join(ROOT, "tools", "_audio_review")

src_mkv = os.path.join(SOURCES_DIR, "Prankedy Attack 2.mkv")
target_ogg = os.path.join(SOUNDS_DIR, "special_prankedy_attack_2.ogg")
target_mp3 = os.path.join(REVIEW_DIR, "special_prankedy_attack_2.mp3")

# Extract WAV from source
full_wav = tempfile.mktemp(suffix=".wav")
subprocess.run([
    "ffmpeg", "-y", "-i", src_mkv, "-vn", "-ac", "2", "-ar", "44100", full_wav
], capture_output=True)

# We want to remove the silent/muted gap in the speech segment:
# Part 1: from start up to 2.20s
# Part 2: from 2.50s to end
seg1 = tempfile.mktemp(suffix=".wav")
seg2 = tempfile.mktemp(suffix=".wav")
joined = tempfile.mktemp(suffix=".wav")
list_file = tempfile.mktemp(suffix=".txt")

subprocess.run(["ffmpeg", "-y", "-i", full_wav, "-to", "2.20", "-c", "copy", seg1], capture_output=True)
subprocess.run(["ffmpeg", "-y", "-ss", "2.50", "-i", full_wav, "-c", "copy", seg2], capture_output=True)

seg1_clean = seg1.replace("\\", "/")
seg2_clean = seg2.replace("\\", "/")
with open(list_file, "w") as f:
    f.write(f"file '{seg1_clean}'\n")
    f.write(f"file '{seg2_clean}'\n")

# Concatenate audio segments
subprocess.run(["ffmpeg", "-y", "-f", "concat", "-safe", "0", "-i", list_file, "-c", "copy", joined], capture_output=True)

# Save final OGG normalized to -16 LUFS
cmd_ogg = [
    "ffmpeg", "-y", "-i", joined,
    "-af", "loudnorm=I=-16:TP=-1.5:LRA=11",
    "-c:a", "libvorbis", "-ar", "44100", "-b:a", "96k", "-ac", "2",
    target_ogg
]
subprocess.run(cmd_ogg, capture_output=True)

# Save review MP3
cmd_mp3 = [
    "ffmpeg", "-y", "-i", target_ogg,
    "-c:a", "libmp3lame", "-b:a", "128k",
    target_mp3
]
subprocess.run(cmd_mp3, capture_output=True)

# Cleanup
for p in [full_wav, seg1, seg2, joined, list_file]:
    if os.path.exists(p):
        os.remove(p)

# Measure final duration
dur = subprocess.run([
    "ffprobe", "-v", "error", "-show_entries", "format=duration",
    "-of", "default=noprint_wrappers=1:nokey=1", target_ogg
], capture_output=True, text=True).stdout.strip()

print(f"[OK] special_prankedy_attack_2.ogg restaurado y corregido sin hueco mudo! Nueva duración: {dur}s")
