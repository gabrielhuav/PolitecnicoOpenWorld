#!/usr/bin/env python3
"""Construye los 21 audios especiales desde fuentes locales ya descargadas.

El manifiesto v3 fija un corte distinto por personaje, registra hashes y genera el
catálogo de subtítulos con la frase que realmente se oye. No descarga contenido.
"""

from __future__ import annotations

import hashlib
import json
import re
import shutil
import subprocess
import sys
import time
import unicodedata
from datetime import datetime, timezone
from pathlib import Path


TOOLS = Path(__file__).resolve().parent
PROJECT = TOOLS.parent
VOICE_ROOT = TOOLS / "sf_voice_scrape"
CONFIG = VOICE_ROOT / "sf_audio_cuts_v3.json"
WORK = VOICE_ROOT / "out_v3"
ASSET_SOUNDS = PROJECT / "app/src/main/assets/STREETFIGHTER/SOUNDS"
ASSET_DATA = PROJECT / "app/src/main/assets/STREETFIGHTER/DATA"
AUDIT = VOICE_ROOT / "audio_final_audit.json"
SOURCE_CATALOG = VOICE_ROOT / "special_phrases_catalog.json"
EXPECTED_FIGHTERS = {
    "PRANKEDY", "SENOR_TIENDA", "PAPARAZZI_1", "REY_GRUPERO", "PAPARAZZI_5",
    "LAZARO", "ESCOMBOY", "ESCOMGIRL", "ROBOT", "YOALLI_EHECATL", "CHARRO_NEGRO",
    "LA_LLORONA", "LA_TZITZIMIME", "LA_PRESIDENTA", "POLICIA_CDMX",
    "POLICIA_CDMX_HOMBRE", "PARAMEDICO_CRUZ_ROJA", "POLICIA_GRANADERO_HOMBRE",
    "POLICIA_GRANADERO_MUJER", "GRANADERO", "PARAMEDICO",
}


def run(command: list[str]) -> subprocess.CompletedProcess[str]:
    return subprocess.run(command, check=True, text=True, capture_output=True)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def duration(path: Path) -> float:
    result = run([
        "ffprobe", "-v", "error", "-show_entries", "format=duration",
        "-of", "default=noprint_wrappers=1:nokey=1", str(path),
    ])
    return float(result.stdout.strip())


def hud_text(text: str) -> str:
    normalized = unicodedata.normalize("NFKD", text.upper())
    ascii_text = "".join(char for char in normalized if not unicodedata.combining(char))
    return re.sub(r"\s+", " ", re.sub(r"[^A-Z0-9 ]", " ", ascii_text)).strip()


def synthesize_sapi(spec: dict, destination: Path) -> None:
    if sys.platform != "win32":
        raise RuntimeError("La fuente sintética SAPI solo se regenera en Windows")
    voice = str(spec["voice"]).replace("'", "''")
    text = str(spec["text"]).replace("'", "''")
    target = str(destination.resolve()).replace("'", "''")
    rate = int(spec.get("rate", 0))
    script = (
        "Add-Type -AssemblyName System.Speech; "
        "$speaker=New-Object System.Speech.Synthesis.SpeechSynthesizer; "
        f"$speaker.SelectVoice('{voice}'); $speaker.Rate={rate}; "
        f"$speaker.SetOutputToWaveFile('{target}'); $speaker.Speak('{text}'); "
        "$speaker.Dispose()"
    )
    run(["powershell", "-NoProfile", "-Command", script])


def source_for(fighter: str, spec: dict) -> Path:
    synthetic = spec.get("synthetic")
    if synthetic:
        destination = WORK / "sources" / f"{fighter.lower()}_synthetic.wav"
        destination.parent.mkdir(parents=True, exist_ok=True)
        synthesize_sapi(synthetic, destination)
        return destination
    source = (VOICE_ROOT / spec["source"]).resolve()
    if not source.is_relative_to(VOICE_ROOT.resolve()):
        raise ValueError(f"Fuente fuera de sf_voice_scrape: {source}")
    if not source.is_file():
        raise FileNotFoundError(source)
    return source


def build_audio(fighter: str, spec: dict, source: Path, destination: Path) -> None:
    source_duration = duration(source)
    start = float(spec.get("start", 0.0))
    end = float(spec.get("end", source_duration))
    if spec.get("preserve_full_source"):
        start, end = 0.0, source_duration
    if start < 0 or end <= start or end > source_duration + 0.1:
        raise ValueError(f"Corte inválido para {fighter}: {start:.3f}-{end:.3f}/{source_duration:.3f}")

    filters = []
    if spec.get("effect") == "robot":
        filters.extend([
            "highpass=f=140",
            "lowpass=f=4200",
            "tremolo=f=28:d=0.16",
            "aecho=0.8:0.45:24:0.16",
        ])
    filters.extend(["loudnorm=I=-16:TP=-1.5:LRA=9", "alimiter=limit=0.95"])
    destination.parent.mkdir(parents=True, exist_ok=True)
    command = [
        "ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
        "-ss", f"{start:.3f}", "-t", f"{end - start:.3f}", "-i", str(source),
        "-vn", "-ac", "1", "-ar", "44100", "-af", ",".join(filters),
        "-c:a", "libvorbis", "-q:a", "5", str(destination),
    ]
    run(command)


def install_asset(generated: Path, destination: Path) -> None:
    """Instala de forma atómica; Windows puede retener brevemente un OGG recién auditado."""
    destination.parent.mkdir(parents=True, exist_ok=True)
    pending = destination.with_suffix(".pending.ogg")
    shutil.copyfile(generated, pending)
    for attempt in range(20):
        try:
            pending.replace(destination)
            return
        except PermissionError:
            if attempt == 19:
                raise
            time.sleep(0.1)


def main() -> int:
    manifest = json.loads(CONFIG.read_text(encoding="utf-8"))
    fighters = manifest["fighters"]
    if set(fighters) != EXPECTED_FIGHTERS:
        missing = sorted(EXPECTED_FIGHTERS - set(fighters))
        extra = sorted(set(fighters) - EXPECTED_FIGHTERS)
        raise ValueError(f"El manifiesto no cubre los 21 peleadores. Faltan={missing}; sobran={extra}")

    WORK.mkdir(parents=True, exist_ok=True)
    ASSET_SOUNDS.mkdir(parents=True, exist_ok=True)
    runtime = {
        "version": 3,
        "default_lang": "es",
        "supported_langs": ["es", "en"],
        "audio_language": "es-MX",
        "fighters": {},
    }
    results = []
    for fighter, spec in fighters.items():
        source = source_for(fighter, spec)
        output_name = f"special_{fighter.lower()}.ogg"
        generated = WORK / "generated" / output_name
        destination = ASSET_SOUNDS / output_name
        build_audio(fighter, spec, source, generated)
        install_asset(generated, destination)
        output_duration = duration(destination)
        expected_duration = duration(source) if spec.get("preserve_full_source") else (
            float(spec.get("end", duration(source))) - float(spec.get("start", 0.0))
        )
        if abs(output_duration - expected_duration) > 0.15:
            raise ValueError(
                f"Duración inesperada en {fighter}: {output_duration:.3f}s, esperada {expected_duration:.3f}s",
            )
        phrase_es = str(spec.get("phrase_es", "")).strip()
        phrase_en = str(spec.get("phrase_en", phrase_es)).strip()
        if phrase_es:
            runtime["fighters"][fighter] = {
                "phrase_es": phrase_es,
                "phrase_en": phrase_en or phrase_es,
                "phrase_hud": hud_text(phrase_es),
                "audio": output_name,
                "subtitle_ms": min(20000, max(1200, round(output_duration * 1000 + 400))),
                "tier": spec["tier"],
            }
        results.append({
            "fighter": fighter,
            "tier": spec["tier"],
            "source": spec.get("source", "synthetic:windows_sapi"),
            "source_sha256": sha256(source),
            "start_sec": float(spec.get("start", 0.0)),
            "end_sec": float(spec.get("end", duration(source))),
            "preserve_full_source": bool(spec.get("preserve_full_source")),
            "phrase_es": phrase_es or None,
            "duration_sec": round(output_duration, 3),
            "output": output_name,
            "output_bytes": destination.stat().st_size,
            "output_sha256": sha256(destination),
            "technical_status": "PASS",
        })
        print(f"[PASS] {fighter:30} {output_duration:6.2f}s  {destination.stat().st_size / 1024:7.1f} KiB")

    (ASSET_DATA / "special_phrases.json").write_text(
        json.dumps(runtime, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    source_catalog = {
        "version": 3,
        "notes": (
            "Catálogo canónico de cortes locales. El audio final es únicamente español; "
            "phrase_en se conserva solo para subtítulos traducidos."
        ),
        "source_policy": "local_only_no_rescrape",
        "exclude_fighters": [],
        "fighters": fighters,
    }
    SOURCE_CATALOG.write_text(
        json.dumps(source_catalog, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    report = {
        "version": 3,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "language": "es-MX",
        "source_policy": "local_only_no_rescrape",
        "fighter_count": len(results),
        "spoken_subtitle_count": len(runtime["fighters"]),
        "all_technical_checks_passed": all(row["technical_status"] == "PASS" for row in results),
        "results": results,
    }
    AUDIT.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Catálogo: {ASSET_DATA / 'special_phrases.json'}")
    print(f"Catálogo de fuentes: {SOURCE_CATALOG}")
    print(f"Auditoría: {AUDIT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
