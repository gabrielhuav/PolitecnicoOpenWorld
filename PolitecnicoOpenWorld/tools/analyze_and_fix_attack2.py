#!/usr/bin/env python3
"""
analyze_and_fix_attack2.py

Inspecciona el audio `special_prankedy_attack_2.ogg` para detectar silencios/huecos muteados
internos y eliminar el bache juntando los segmentos hablados de forma fluida.
"""
import os
import subprocess
import tempfile
import wave
import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOUNDS_DIR = os.path.join(ROOT, "app", "src", "main", "assets", "STREETFIGHTER", "SOUNDS")
REVIEW_DIR = os.path.join(ROOT, "tools", "_audio_review")

ogg_path = os.path.join(SOUNDS_DIR, "special_prankedy_attack_2.ogg")

# Convert OGG to temp WAV 16kHz
tmp_wav = tempfile.mktemp(suffix=".wav")
subprocess.run(["ffmpeg", "-y", "-v", "error", "-i", ogg_path, "-ac", "1", "-ar", "16000", tmp_wav], check=True)

with wave.open(tmp_wav, "rb") as w:
    sr = w.getframerate()
    frames = w.readframes(w.getnframes())
    signal = np.frombuffer(frames, dtype=np.int16).astype(np.float32) / 32768.0

os.remove(tmp_wav)

total_duration = len(signal) / sr
print(f"Duración total: {total_duration:.3f} s")

# Detect silent frames (RMS < 0.015) in 50ms windows
win_size = int(sr * 0.05) # 50ms
energies = []
times = []

for i in range(0, len(signal) - win_size, win_size):
    chunk = signal[i:i+win_size]
    rms = np.sqrt(np.mean(chunk**2))
    t = i / sr
    energies.append(rms)
    times.append(t)

# Find quiet/silent spans in the middle of speech (excluding start/end padding)
silent_spans = []
in_silence = False
start_t = 0.0

for t, rms in zip(times, energies):
    if rms < 0.01 and not in_silence:
        in_silence = True
        start_t = t
    elif rms >= 0.01 and in_silence:
        in_silence = False
        duration_silence = t - start_t
        if duration_silence > 0.15 and start_t > 0.3 and t < (total_duration - 0.3):
            silent_spans.append((start_t, t, duration_silence))

print("Huecos silenciosos internos encontrados:")
for s, e, d in silent_spans:
    print(f"  Silencio desde {s:.2f}s hasta {e:.2f}s (Duración: {d:.2f}s)")
