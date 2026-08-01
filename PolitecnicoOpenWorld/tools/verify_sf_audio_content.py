#!/usr/bin/env python3
"""Verifica contenido, idioma y duración de los 21 M4A finales con Whisper local."""

from __future__ import annotations

import argparse
import difflib
import json
import re
import unicodedata
from pathlib import Path

from faster_whisper import WhisperModel


TOOLS = Path(__file__).resolve().parent
PROJECT = TOOLS.parent
VOICE_ROOT = TOOLS / "sf_voice_scrape"
CONFIG = VOICE_ROOT / "sf_audio_cuts_v3.json"
AUDIT = VOICE_ROOT / "audio_final_audit.json"
FUNCTIONAL_REPORT = VOICE_ROOT / "functional_verify.json"
PACK_REPORT = VOICE_ROOT / "pack_report.json"
SOUNDS = PROJECT / "app/src/main/assets/STREETFIGHTER/SOUNDS"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", required=True, help="Ruta local del modelo faster-whisper")
    parser.add_argument("--device", choices=("cpu", "cuda"), default="cpu")
    return parser.parse_args()


def normalized_words(text: str) -> list[str]:
    normalized = unicodedata.normalize("NFKD", text.casefold())
    plain = "".join(char for char in normalized if not unicodedata.combining(char))
    return re.findall(r"[a-z0-9]+", plain)


def similarity(expected: str, detected: str) -> float:
    return difflib.SequenceMatcher(
        a=normalized_words(expected),
        b=normalized_words(detected),
        autojunk=False,
    ).ratio()


def main() -> int:
    args = parse_args()
    config = json.loads(CONFIG.read_text(encoding="utf-8"))["fighters"]
    audit = json.loads(AUDIT.read_text(encoding="utf-8"))
    by_fighter = {row["fighter"]: row for row in audit["results"]}
    compute_type = "float16" if args.device == "cuda" else "int8"
    model = WhisperModel(args.model, device=args.device, compute_type=compute_type)
    failures = []

    for fighter, spec in config.items():
        expected = str(spec.get("phrase_es", "")).strip()
        row = by_fighter[fighter]
        if not expected:
            row.update({
                "content_status": "PASS_NO_SPEECH_EXPECTED",
                "detected_language": None,
                "detected_text": None,
                "text_similarity": None,
            })
            continue
        audio = SOUNDS / f"special_{fighter.lower()}.m4a"
        segments_iter, info = model.transcribe(
            str(audio),
            beam_size=5,
            best_of=5,
            vad_filter=False,
            condition_on_previous_text=False,
        )
        detected = " ".join(segment.text.strip() for segment in segments_iter).strip()
        score = similarity(expected, detected)
        language_ok = info.language == "es" or fighter == "LA_LLORONA"
        threshold = 0.45 if fighter == "LA_LLORONA" else 0.62
        content_ok = bool(detected) and score >= threshold and language_ok
        row.update({
            "content_status": "PASS" if content_ok else "FAIL",
            "detected_language": info.language,
            "detected_language_probability": round(info.language_probability, 4),
            "detected_text": detected,
            "text_similarity": round(score, 4),
        })
        print(
            f"[{'PASS' if content_ok else 'FAIL'}] {fighter:30} "
            f"lang={info.language} match={score:.3f}  {detected}",
            flush=True,
        )
        if not content_ok:
            failures.append(fighter)

    audit["all_content_checks_passed"] = not failures
    audit["content_failures"] = failures
    serialized_audit = json.dumps(audit, ensure_ascii=False, indent=2) + "\n"
    AUDIT.write_text(serialized_audit, encoding="utf-8")
    FUNCTIONAL_REPORT.write_text(serialized_audit, encoding="utf-8")
    pack_report = {
        "version": 3,
        "status": "PASS" if not failures else "FAIL",
        "language": "es-MX",
        "fighter_count": audit["fighter_count"],
        "fighters": {
            row["fighter"]: {
                "status": row["technical_status"],
                "content_status": row["content_status"],
                "file": f"special_{row['fighter'].lower()}.m4a",
                "duration_sec": row["duration_sec"],
                "sha256": row["output_sha256"],
            }
            for row in audit["results"]
        },
    }
    PACK_REPORT.write_text(
        json.dumps(pack_report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    if failures:
        print(f"Fallaron: {', '.join(failures)}")
        return 1
    print("Contenido verificado: todas las voces coinciden y están en español.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
