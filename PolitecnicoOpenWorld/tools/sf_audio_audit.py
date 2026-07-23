#!/usr/bin/env python3
"""AUDIT MP3->OGG de las voces de HUELUM VS. GOYA (2026-07-20).

Retoma el audit de audio: por cada `special_*.ogg` (y globales) de
`app/src/main/assets/STREETFIGHTER/SOUNDS/`:

  1. Extrae el MAPEO real peleadór/evento parseando `sfVoicePacks` del
     StreetFighterViewModel.kt (fuente de verdad = el código, no los docs).
  2. Mide duración (ffprobe) y F0 mediano (autocorrelación) para clasificar
     VOZ HABLADA / GRITO / SFX y detectar voces con género sospechoso.
  3. Transcribe con faster-whisper (español) como BORRADOR de frase.
  4. Regenera `tools/_audio_review/*.mp3` (para escuchar en cualquier lado).
  5. Escribe `tools/sf_audio_audit_report.md` con la tabla completa y las
     discrepancias (frase inline vs transcripción).

Uso (venv con faster-whisper, p. ej. .codex-tmp/pow-audio-venv):
    python tools/sf_audio_audit.py [--no-transcribe] [--no-mp3]
"""
from __future__ import annotations

import argparse
import datetime as _dt
import os
import re
import subprocess
import sys
import tempfile
import wave

import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOUNDS = os.path.join(ROOT, "app", "src", "main", "assets", "STREETFIGHTER", "SOUNDS")
VM = os.path.join(
    ROOT, "app", "src", "main", "java", "ovh", "gabrielhuav", "pow",
    "features", "streetfighter", "viewmodel", "StreetFighterViewModel.kt",
)
REVIEW_DIR = os.path.join(ROOT, "tools", "_audio_review")
REPORT = os.path.join(ROOT, "tools", "sf_audio_audit_report.md")

EVENTS = ("intro", "attack", "hurt", "power", "win", "loss", "lowHp")
SR = 16000


# ───────────────────────── mapeo desde el VM ─────────────────────────

def _block(text: str, start: int) -> str:
    """Devuelve el texto desde `start` hasta cerrar el paréntesis de SfVoicePack(."""
    i = text.index("(", start)
    depth = 0
    for j in range(i, len(text)):
        if text[j] == "(":
            depth += 1
        elif text[j] == ")":
            depth -= 1
            if depth == 0:
                return text[i:j + 1]
    return text[i:]


def parse_voice_packs() -> tuple[dict[str, list[tuple[str, str]]], dict[str, str]]:
    """Devuelve (archivo -> [(peleadór, evento)]), (archivo -> frase inline)."""
    src = open(VM, encoding="utf-8").read()

    # 1) defs de variables: val nombre = SfVoiceLine(...) / listOf(...) / SfVoicePack(...)
    var_defs: dict[str, str] = {}
    for m in re.finditer(r"val (\w+) = (SfVoicePack|SfVoiceLine|listOf)\(", src):
        var_defs[m.group(1)] = _block(src, m.end() - 1)

    phrases: dict[str, str] = {}
    for m in re.finditer(r'SfVoiceLine\(\s*"([^"]+)"(?:\s*,\s*"([^"]*)")?\s*\)', src):
        if m.group(2):
            phrases[m.group(1)] = m.group(2)

    def lines_of(expr: str) -> list[str]:
        """Archivos de un trozo de código (resolviendo variables 1 nivel)."""
        files = [m.group(1) for m in re.finditer(r'SfVoiceLine\(\s*"([^"]+)"', expr)]
        for var, body in var_defs.items():
            if re.search(rf"\b{var}\b", expr) and var not in ("SfVoicePack",):
                files += [m.group(1) for m in re.finditer(r'SfVoiceLine\(\s*"([^"]+)"', body)]
                # var de tipo SfVoiceLine: _block guarda solo "(...)" sin el nombre → el
                # archivo es el primer literal del cuerpo
                direct = re.match(r'\(\s*"([^"]+)"', body.strip())
                if direct:
                    files.append(direct.group(1))
        return files

    def events_of(pack_body: str) -> dict[str, list[str]]:
        out: dict[str, list[str]] = {}
        # separa por evento: "attack = listOf(...)" o "attack = mAttack"
        for em in re.finditer(rf"({'|'.join(EVENTS)}) = ", pack_body):
            ev = em.group(1)
            rest = pack_body[em.end():]
            # el valor termina en la próxima "otroEvento = " o el fin del pack
            nxt = re.search(rf"(?:{'|'.join(EVENTS)}) = ", rest)
            val = rest[: nxt.start()] if nxt else rest
            out.setdefault(ev, []).extend(lines_of(val))
        return out

    mapping: dict[str, list[tuple[str, str]]] = {}
    # 2) packs por peleadór: "SfFighterId.X to SfVoicePack(" o "SfFighterId.X to var"
    for m in re.finditer(r"SfFighterId\.(\w+) to (SfVoicePack\(|\w+)", src):
        fighter = m.group(1)
        if m.group(2) == "SfVoicePack(":
            body = _block(src, m.start(2) + len("SfVoicePack"))
        else:
            body = var_defs.get(m.group(2), "")
        for ev, files in events_of(body).items():
            for f in files:
                mapping.setdefault(f, []).append((fighter, ev))
    return mapping, phrases


# ───────────────────────── análisis de señal ─────────────────────────

def decode_wav(path: str) -> np.ndarray:
    tmp = tempfile.mktemp(suffix=".wav")
    subprocess.run(
        ["ffmpeg", "-y", "-v", "error", "-i", path, "-ac", "1", "-ar", str(SR), tmp],
        check=True,
    )
    with wave.open(tmp, "rb") as w:
        data = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float32)
    os.remove(tmp)
    return data / 32768.0


def duration_of(path: str) -> float:
    out = subprocess.run(
        ["ffprobe", "-v", "error", "-show_entries", "format=duration",
         "-of", "default=noprint_wrappers=1:nokey=1", path],
        capture_output=True, text=True, check=True,
    )
    return float(out.stdout.strip())


def f0_stats(x: np.ndarray) -> tuple[float, int]:
    """(F0 mediano en Hz o 0, % de frames con voz periódica)."""
    frame, hop = 1024, 512
    fmin, fmax = 60, 400
    f0s = []
    total = 0
    for i in range(0, len(x) - frame, hop):
        total += 1
        seg = x[i:i + frame]
        seg = seg - seg.mean()
        if np.abs(seg).max() < 0.02:
            continue
        corr = np.correlate(seg, seg, mode="full")[frame - 1:]
        corr /= corr[0] + 1e-9
        lo, hi = SR // fmax, SR // fmin
        if corr[lo:hi].size == 0:
            continue
        peak = int(np.argmax(corr[lo:hi])) + lo
        if corr[peak] >= 0.3:
            f0s.append(SR / peak)
    if not f0s:
        return 0.0, 0
    return float(np.median(f0s)), 100 * len(f0s) // max(total, 1)


def classify(f0: float, voiced_pct: int, transcript: str) -> str:
    if voiced_pct < 8 or f0 == 0.0:
        return "SFX"
    if transcript and len(transcript.split()) >= 3:
        return "VOZ HABLADA"
    return "GRITO/EXCLAMACION"


# ───────────────────────── main ─────────────────────────

def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--no-transcribe", action="store_true")
    ap.add_argument("--no-mp3", action="store_true")
    args = ap.parse_args()

    mapping, phrases = parse_voice_packs()
    files = sorted(f for f in os.listdir(SOUNDS) if f.endswith(".ogg"))

    whisper = None
    if not args.no_transcribe:
        from faster_whisper import WhisperModel
        whisper = WhisperModel("small", device="cpu", compute_type="int8")

    if not args.no_mp3:
        os.makedirs(REVIEW_DIR, exist_ok=True)
        for old in os.listdir(REVIEW_DIR):
            os.remove(os.path.join(REVIEW_DIR, old))

    rows = []
    for name in files:
        path = os.path.join(SOUNDS, name)
        base = name[:-4]
        dur = duration_of(path)
        x = decode_wav(path)
        f0, voiced = f0_stats(x)
        transcript = ""
        if whisper and base.startswith("special_"):
            segments, _ = whisper.transcribe(path, language="es", vad_filter=False)
            transcript = " ".join(s.text.strip() for s in segments).strip()
        uses = mapping.get(base, [])
        rows.append({
            "file": name, "dur": dur, "f0": f0, "voiced": voiced,
            "uses": uses, "phrase": phrases.get(base, ""),
            "transcript": transcript,
            "kind": classify(f0, voiced, transcript) if base.startswith("special_") else "SFX GLOBAL",
        })
        if not args.no_mp3:
            subprocess.run(
                ["ffmpeg", "-y", "-v", "error", "-i", path, "-codec:a", "libmp3lame",
                 "-b:a", "128k", os.path.join(REVIEW_DIR, base + ".mp3")],
                check=True,
            )
        print(f"  {name} ({dur:.2f}s) OK", flush=True)

    today = _dt.date.today().isoformat()
    with open(REPORT, "w", encoding="utf-8") as f:
        f.write(f"# AUDIT MP3→OGG de voces SF — generado {today} por `tools/sf_audio_audit.py`\n\n")
        f.write("> Escucha los `.mp3` de `tools/_audio_review/` (mismo nombre base). ")
        f.write("`F0` mediano orienta el género de la voz (♂ ~80-160 Hz, ♀ ~160-300+; gritos suben). ")
        f.write("La transcripción Whisper (modelo small) es BORRADOR: curar a oído antes de usarla de subtítulo.\n\n")
        f.write("## ⚠️ Revisar PRIMERO (escom: ¿voz correcta?)\n\n")
        f.write("| Archivo | Dur | F0 | Uso | Nota |\n|---|---|---|---|---|\n")
        for r in rows:
            if "escom" in r["file"]:
                uses = "; ".join(f"{fi}:{ev}" for fi, ev in r["uses"]) or "(sin uso en packs)"
                f.write(f"| `{r['file']}` | {r['dur']:.2f}s | {r['f0']:.0f} Hz | {uses} | {r['kind']} |\n")
        f.write("\n## Tabla completa\n\n")
        f.write("| Archivo | Dur | F0 (Hz) | Voz % | Tipo | Peleadór:evento | Frase inline (VM) | Transcripción Whisper |\n")
        f.write("|---|---|---|---|---|---|---|---|\n")
        for r in rows:
            uses = "; ".join(f"{fi}:{ev}" for fi, ev in r["uses"])
            if not uses:
                uses = "fallback/global"
            f.write(
                f"| `{r['file']}` | {r['dur']:.2f} | {r['f0']:.0f} | {r['voiced']} | {r['kind']} | {uses} | "
                f"{r['phrase'] or '—'} | {r['transcript'] or '—'} |\n"
            )
        f.write("\n## Posibles discrepancias frase inline vs audio\n\n")
        any_mismatch = False
        for r in rows:
            if r["phrase"] and r["transcript"]:
                a = re.sub(r"\W+", " ", r["phrase"].lower()).strip()
                b = re.sub(r"\W+", " ", r["transcript"].lower()).strip()
                if a[:20] not in b and b[:20] not in a:
                    any_mismatch = True
                    f.write(f"- `{r['file']}`: inline «{r['phrase']}» vs Whisper «{r['transcript']}»\n")
        if not any_mismatch:
            f.write("(ninguna obvia)\n")
    print(f"\nReporte: {REPORT}\nMP3 de escucha: {REVIEW_DIR}")


if __name__ == "__main__":
    sys.exit(main())
