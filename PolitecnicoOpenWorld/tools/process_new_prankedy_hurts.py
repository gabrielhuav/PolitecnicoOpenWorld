#!/usr/bin/env python3
"""
process_new_prankedy_hurts.py

Procesa los nuevos archivos de audio mkv de Prankedy hurt recibidos:
1. `prankedy hurt 1.mkv`: recortar primeros 1.5 seg -> special_prankedy_hurt_1.ogg / mp3
2. `prankedy hurt 2.mkv`: recortar primeros 0.5 seg y último 0.1 seg -> special_prankedy_hurt_2.ogg / mp3
3. `prankedy hurt 3.mkv`: recortar primeros 0.6 seg -> special_prankedy_hurt_3.ogg / mp3
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

def process_hurt(source_name, target_name, start_offset=0.0, cut_end_offset=0.0):
    src_path = os.path.join(SOURCES_DIR, source_name)
    if not os.path.exists(src_path):
        print(f"[ERROR] No existe archivo fuente: {src_path}")
        return

    dur = get_duration(src_path)
    if dur <= 0:
        print(f"[ERROR] Duración inválida en {src_path}")
        return

    target_dur = max(0.1, dur - start_offset - cut_end_offset)
    ogg_target = os.path.join(SOUNDS_DIR, target_name + ".ogg")
    mp3_target = os.path.join(REVIEW_DIR, target_name + ".mp3")

    # 1. Convertir a OGG normalizado a -16 LUFS
    cmd_ogg = [
        "ffmpeg", "-y", "-ss", str(start_offset), "-i", src_path,
        "-t", str(target_dur),
        "-af", "loudnorm=I=-16:TP=-1.5:LRA=11",
        "-c:a", "libvorbis", "-ar", "44100", "-b:a", "96k", "-ac", "2",
        ogg_target
    ]
    res_ogg = subprocess.run(cmd_ogg, capture_output=True)
    if res_ogg.returncode != 0:
        print(f"[ERROR] Falló conversión OGG para {target_name}: {res_ogg.stderr.decode('utf-8', errors='ignore')}")
        return

    final_dur = get_duration(ogg_target)
    print(f"[OK] {target_name}.ogg creado ({final_dur:.2f}s, start={start_offset}s)")

    # 2. Generar MP3 para auditoría
    cmd_mp3 = [
        "ffmpeg", "-y", "-i", ogg_target,
        "-c:a", "libmp3lame", "-b:a", "128k",
        mp3_target
    ]
    subprocess.run(cmd_mp3, capture_output=True)

def main():
    print("--- Procesando nuevos audios Prankedy Hurt ---")
    process_hurt("prankedy hurt 1.mkv", "special_prankedy_hurt_1", start_offset=1.5)
    process_hurt("prankedy hurt 2.mkv", "special_prankedy_hurt_2", start_offset=0.5, cut_end_offset=0.1)
    process_hurt("prankedy hurt 3.mkv", "special_prankedy_hurt_3", start_offset=0.6)

if __name__ == "__main__":
    main()
