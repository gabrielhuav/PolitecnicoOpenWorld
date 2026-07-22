#!/usr/bin/env python3
"""
apply_prankedy_attack_refinements.py

Aplica las correcciones solicitadas a las pistas de Prankedy:
1. `special_prankedy_attack_1`: mantener 75% inicial.
2. `special_prankedy_attack_2`: remueve el hueco mudo interno (2.20s a 2.50s) y une el audio de forma fluida.
3. `special_prankedy_attack_3`: quitar primer 10%.
4. `special_prankedy_attack_4`: quitar primer 10%.
5. `special_prankedy_power`: quitar primer 10%.
6. `special_prankedy_win`: quitar primer 10%.
7. `special_prankedy_loss`: quitar primer 10%.
8. `special_prankedy_lowhp`: quitar primer 10%.

Actualiza los .ogg en SOUNDS/ y regenera los .mp3 en tools/_audio_review/.
"""
import os
import subprocess
import tempfile

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

def update_mp3(filename):
    ogg_path = os.path.join(SOUNDS_DIR, filename)
    mp3_name = os.path.splitext(filename)[0] + ".mp3"
    mp3_path = os.path.join(REVIEW_DIR, mp3_name)
    subprocess.run([
        "ffmpeg", "-y", "-i", ogg_path, "-c:a", "libmp3lame", "-b:a", "128k", mp3_path
    ], capture_output=True)

def trim_clip(filename, keep_first_percent=None, trim_start_percent=None):
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
    elif trim_start_percent is not None:
        start_time = dur * trim_start_percent
        target_dur = dur - start_time
    else:
        return

    temp_path = ogg_path + ".tmp.ogg"
    cmd = [
        "ffmpeg", "-y", "-ss", f"{start_time:.3f}", "-i", ogg_path,
        "-t", f"{target_dur:.3f}",
        "-c:a", "libvorbis", "-ar", "44100", "-b:a", "96k", "-ac", "2",
        temp_path
    ]
    
    res = subprocess.run(cmd, capture_output=True)
    if res.returncode == 0:
        os.replace(temp_path, ogg_path)
        new_dur = get_duration(ogg_path)
        print(f"[TRIM] {filename}: {dur:.2f}s -> {new_dur:.2f}s (start={start_time:.2f}s)")
        update_mp3(filename)
    else:
        if os.path.exists(temp_path):
            os.remove(temp_path)
        print(f"[ERROR] Falló recorte de {filename}")

def fix_attack_2():
    filename = "special_prankedy_attack_2.ogg"
    ogg_path = os.path.join(SOUNDS_DIR, filename)
    if not os.path.exists(ogg_path):
        return

    dur = get_duration(ogg_path)
    print(f"Fixing {filename} (duración original: {dur:.2f}s)...")

    # Cortar partes 0.0 -> 2.20s y 2.50s -> end en WAV para evitar desfase de contenedores
    seg1 = tempfile.mktemp(suffix=".wav")
    seg2 = tempfile.mktemp(suffix=".wav")
    joined = tempfile.mktemp(suffix=".wav")
    list_file = tempfile.mktemp(suffix=".txt")

    subprocess.run(["ffmpeg", "-y", "-i", ogg_path, "-to", "2.20", "-c:a", "pcm_s16le", "-ar", "44100", "-ac", "2", seg1], capture_output=True)
    subprocess.run(["ffmpeg", "-y", "-ss", "2.50", "-i", ogg_path, "-c:a", "pcm_s16le", "-ar", "44100", "-ac", "2", seg2], capture_output=True)

    seg1_clean = seg1.replace("\\", "/")
    seg2_clean = seg2.replace("\\", "/")
    with open(list_file, "w") as f:
        f.write(f"file '{seg1_clean}'\n")
        f.write(f"file '{seg2_clean}'\n")

    # Concatenar los 2 fragmentos WAV
    subprocess.run(["ffmpeg", "-y", "-f", "concat", "-safe", "0", "-i", list_file, "-c", "copy", joined], capture_output=True)

    # Convertir el WAV concatenado al OGG final normalizado
    cmd_concat = [
        "ffmpeg", "-y", "-i", joined,
        "-af", "loudnorm=I=-16:TP=-1.5:LRA=11",
        "-c:a", "libvorbis", "-ar", "44100", "-b:a", "96k", "-ac", "2",
        ogg_path + ".tmp.ogg"
    ]
    res = subprocess.run(cmd_concat, capture_output=True)
    
    # Limpieza
    for p in [seg1, seg2, joined, list_file]:
        if os.path.exists(p):
            os.remove(p)

    if res.returncode == 0:
        os.replace(ogg_path + ".tmp.ogg", ogg_path)
        new_dur = get_duration(ogg_path)
        print(f"[FIXED] {filename}: hueco mudo eliminado! Nueva duración: {new_dur:.2f}s")
        update_mp3(filename)
    else:
        print(f"[ERROR] Falló al unir segmentos de {filename}")

def main():
    print("--- Refinando audios de Prankedy ---")
    
    # 1. special_prankedy_attack_1: mantener 75% inicial
    trim_clip("special_prankedy_attack_1.ogg", keep_first_percent=0.75)

    # 2. special_prankedy_attack_2: unir frase quitando silencio interno
    fix_attack_2()

    # 3-8. Quitar 10% inicial a attack_3, attack_4, power, win, loss, lowhp
    clips_10percent = [
        "special_prankedy_attack_3.ogg",
        "special_prankedy_attack_4.ogg",
        "special_prankedy_power.ogg",
        "special_prankedy_win.ogg",
        "special_prankedy_loss.ogg",
        "special_prankedy_lowhp.ogg"
    ]

    for clip in clips_10percent:
        trim_clip(clip, trim_start_percent=0.10)

if __name__ == "__main__":
    main()
