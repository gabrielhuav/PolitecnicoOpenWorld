#!/usr/bin/env python3
"""
voice_lab.py — Laboratorio de audio para specials POW (sin deepfake de personas reales).

Capacidades:
  - listar candidatos por peleadór
  - normalizar + pitch + FX (shout/horror/radio/robot)
  - juntar 2–3 candidatos (concat con crossfade corto)
  - opcional: Whisper STT si está instalado (`pip install openai-whisper`)
  - exportar preview a tools/sf_voice_scrape/candidates/lab/  (NO assets/)

NO hace:
  - clonación de voz / RVC / Tortoise / ElevenLabs de celebridades
  - install automático a STREETFIGHTER/SOUNDS

Usage (repo root):
  python PolitecnicoOpenWorld/tools/voice_lab.py list
  python PolitecnicoOpenWorld/tools/voice_lab.py process --fighter PAPARAZZI_1 --fx shout
  python PolitecnicoOpenWorld/tools/voice_lab.py process --fighter LA_LLORONA --fx horror --pitch -2
  python PolitecnicoOpenWorld/tools/voice_lab.py merge --fighter PRANKEDY --fx shout
  python PolitecnicoOpenWorld/tools/voice_lab.py stt --fighter PAPARAZZI_1
"""
from __future__ import annotations

import argparse
import json
import math
import shutil
import struct
import subprocess
import sys
import wave
from pathlib import Path

TOOLS = Path(__file__).resolve().parent
SCRAPE = TOOLS / "sf_voice_scrape"
CAND = SCRAPE / "candidates"
LAB = CAND / "lab"
INDEX = CAND / "candidates_index.json"


def ffmpeg() -> str:
    p = shutil.which("ffmpeg") or shutil.which("ffmpeg.exe")
    if not p:
        raise SystemExit("ffmpeg required")
    return p


def load_index() -> dict:
    if not INDEX.is_file():
        return {"totals": {}, "candidates": []}
    return json.loads(INDEX.read_text(encoding="utf-8"))


def list_fighters() -> None:
    idx = load_index()
    print("=== Candidatos por peleadór ===")
    for f, n in sorted(idx.get("totals", {}).items()):
        print(f"  {f}: {n} clips")
    print(f"\nDetalle: {SCRAPE / 'DETALLE_FRASES_POR_PELEADOR.md'}")


def candidates_for(fighter: str) -> list[dict]:
    idx = load_index()
    c = [x for x in idx.get("candidates", []) if x["fighter"] == fighter.upper()]
    return sorted(c, key=lambda x: -x.get("score", 0))


def process_one(
    wav_in: Path,
    wav_out: Path,
    fx: str = "shout",
    pitch: float = 0.0,
    duration: float = 1.05,
) -> bool:
    filters = [
        f"afade=t=in:d=0.04",
        f"afade=t=out:st={max(0.05, duration - 0.1)}:d=0.1",
    ]
    if abs(pitch) > 0.01:
        rate = 44100 * (2 ** (pitch / 12.0))
        filters.append(f"asetrate={rate:.2f},aresample=44100")
    if fx == "shout":
        filters += [
            "highpass=f=150", "lowpass=f=7000",
            "acompressor=threshold=-18dB:ratio=6:attack=5:release=80",
            "aecho=0.8:0.7:35:0.12", "dynaudnorm=f=75:g=12", "volume=1.4",
        ]
    elif fx == "horror":
        filters += [
            "highpass=f=80", "lowpass=f=3800",
            "aecho=0.9:0.85:120|200:0.4|0.25", "vibrato=f=4:d=0.3",
            "dynaudnorm=f=100:g=15", "volume=1.45",
        ]
    elif fx == "radio":
        filters += [
            "highpass=f=400", "lowpass=f=2800",
            "acompressor=threshold=-16dB:ratio=8:attack=3:release=50",
            "dynaudnorm=f=60:g=10", "volume=1.4",
        ]
    elif fx == "robot":
        filters += [
            "tremolo=f=14:d=0.7", "highpass=f=350", "lowpass=f=3200",
            "dynaudnorm=f=70:g=12", "volume=1.45",
        ]
    elif fx == "clean":
        filters += ["highpass=f=100", "lowpass=f=6000", "dynaudnorm=f=90:g=10", "volume=1.2"]
    else:
        filters += ["dynaudnorm=f=80:g=12", "volume=1.3"]

    wav_out.parent.mkdir(parents=True, exist_ok=True)
    # intermediate wav then optional ogg
    cmd = [
        ffmpeg(), "-y", "-hide_banner", "-loglevel", "error",
        "-i", str(wav_in),
        "-af", ",".join(filters),
        "-t", f"{duration:.2f}",
        "-ac", "1", "-ar", "44100",
        str(wav_out),
    ]
    try:
        subprocess.run(cmd, check=True, timeout=60)
        return wav_out.exists()
    except Exception as e:
        print(f"  [fail] {e}")
        return False


def cmd_process(fighter: str, fx: str, pitch: float, top: int) -> None:
    cands = candidates_for(fighter)
    if not cands:
        print(f"No candidates for {fighter}. Run scrape_phrases_v2.py first.")
        return
    out_dir = LAB / fighter.upper()
    out_dir.mkdir(parents=True, exist_ok=True)
    meta = []
    for i, c in enumerate(cands[:top]):
        src = SCRAPE / c["file"].replace("\\", "/")
        if not src.is_file():
            # try relative
            src = Path(c["file"])
            if not src.is_file():
                src = SCRAPE / Path(c["file"]).name
        if not src.is_file():
            print(f"  miss {c['file']}")
            continue
        out = out_dir / f"lab_{c['phrase_id']}_n{i}_pitch{pitch}_{fx}.wav"
        if process_one(src, out, fx=fx, pitch=pitch):
            print(f"  [ok] {out.relative_to(SCRAPE)}")
            meta.append({**c, "lab_file": str(out.relative_to(SCRAPE)), "fx": fx, "pitch": pitch})
    (out_dir / "lab_meta.json").write_text(json.dumps(meta, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"Previews en {out_dir} (NO assets)")


def cmd_merge(fighter: str, fx: str, pitch: float) -> None:
    """Concat top 2 candidates with 40ms crossfade → one longer then cut to 1.1s mid."""
    cands = candidates_for(fighter)
    if len(cands) < 2:
        print("Need ≥2 candidates to merge; falling back to process top1")
        cmd_process(fighter, fx, pitch, top=1)
        return
    paths = []
    for c in cands[:2]:
        src = SCRAPE / c["file"].replace("\\", "/")
        if src.is_file():
            paths.append(src)
    if len(paths) < 2:
        print("Could not resolve 2 wav paths")
        return
    out_dir = LAB / fighter.upper()
    out_dir.mkdir(parents=True, exist_ok=True)
    concat_list = out_dir / "_concat.txt"
    # normalize each to temp then concat
    temps = []
    for i, p in enumerate(paths):
        t = out_dir / f"_t{i}.wav"
        process_one(p, t, fx="clean", pitch=0, duration=1.0)
        temps.append(t)
    # filter_complex acrossfade
    out = out_dir / f"lab_merge_{fx}_p{pitch}.wav"
    cmd = [
        ffmpeg(), "-y", "-hide_banner", "-loglevel", "error",
        "-i", str(temps[0]), "-i", str(temps[1]),
        "-filter_complex",
        f"[0][1]acrossfade=d=0.05:c1=tri:c2=tri,afade=t=in:d=0.04,afade=t=out:st=0.95:d=0.1",
        "-t", "1.1", "-ac", "1", "-ar", "44100", str(out),
    ]
    # pitch after if needed
    try:
        subprocess.run(cmd, check=True, timeout=60)
        if abs(pitch) > 0.01 or fx != "clean":
            out2 = out_dir / f"lab_merge_final_{fx}_p{pitch}.wav"
            process_one(out, out2, fx=fx, pitch=pitch, duration=1.05)
            print(f"  [merge] {out2.relative_to(SCRAPE)}")
        else:
            print(f"  [merge] {out.relative_to(SCRAPE)}")
    except Exception as e:
        print(f"  [merge fail] {e}")


def cmd_stt(fighter: str) -> None:
    """Optional Whisper transcription of top candidates."""
    try:
        import whisper  # type: ignore
    except ImportError:
        print(
            "Whisper no instalado.\n"
            "  pip install openai-whisper\n"
            "Luego: python tools/voice_lab.py stt --fighter PAPARAZZI_1\n"
            "Esto LOCALIZA frases en el audio; no es deepfake."
        )
        return
    model = whisper.load_model("base")
    cands = candidates_for(fighter)[:5]
    out_dir = LAB / fighter.upper()
    out_dir.mkdir(parents=True, exist_ok=True)
    results = []
    for c in cands:
        src = SCRAPE / c["file"].replace("\\", "/")
        if not src.is_file():
            continue
        print(f"  STT {src.name}…")
        r = model.transcribe(str(src), language="es")
        text = (r.get("text") or "").strip()
        print(f"    → {text}")
        results.append({**c, "transcript": text})
    (out_dir / "stt.json").write_text(json.dumps(results, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"Guardado {out_dir / 'stt.json'}")


def main() -> int:
    ap = argparse.ArgumentParser(description="POW voice lab (no celebrity deepfake)")
    sub = ap.add_subparsers(dest="cmd", required=True)

    sub.add_parser("list", help="List candidate counts")

    p = sub.add_parser("process", help="Normalize/pitch/FX top candidates")
    p.add_argument("--fighter", required=True)
    p.add_argument("--fx", default="shout", choices=["shout", "horror", "radio", "robot", "clean"])
    p.add_argument("--pitch", type=float, default=0.0)
    p.add_argument("--top", type=int, default=3)

    m = sub.add_parser("merge", help="Crossfade top 2 candidates")
    m.add_argument("--fighter", required=True)
    m.add_argument("--fx", default="shout")
    m.add_argument("--pitch", type=float, default=0.0)

    s = sub.add_parser("stt", help="Whisper STT on top candidates (optional dep)")
    s.add_argument("--fighter", required=True)

    args = ap.parse_args()
    if args.cmd == "list":
        list_fighters()
    elif args.cmd == "process":
        cmd_process(args.fighter, args.fx, args.pitch, args.top)
    elif args.cmd == "merge":
        cmd_merge(args.fighter, args.fx, args.pitch)
    elif args.cmd == "stt":
        cmd_stt(args.fighter)
    return 0


if __name__ == "__main__":
    sys.exit(main())
