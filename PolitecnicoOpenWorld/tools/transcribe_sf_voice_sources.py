#!/usr/bin/env python3
"""Transcribe las fuentes locales de voces de Street Fighter sin re-descargarlas.

La salida conserva segmentos y palabras con tiempos para que cada corte final pueda
justificarse contra lo que realmente se oye. El archivo se actualiza después de cada
fuente: una ejecución larga puede reanudarse sin perder trabajo.
"""

from __future__ import annotations

import argparse
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

import av
from faster_whisper import WhisperModel


SCRIPT_DIR = Path(__file__).resolve().parent
VOICE_DIR = SCRIPT_DIR / "sf_voice_scrape"
DEFAULT_OUTPUT = VOICE_DIR / "transcripts_sources.json"
AUDIO_EXTENSIONS = {".m4a", ".mp3", ".mp4", ".ogg", ".wav", ".webm"}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--source-dir",
        action="append",
        default=[],
        help="Carpeta relativa a sf_voice_scrape; repetible (por defecto: raw_yt).",
    )
    parser.add_argument("--model", default="large-v3-turbo")
    parser.add_argument("--device", choices=("auto", "cpu", "cuda"), default="auto")
    parser.add_argument("--compute-type", default=None)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--name-contains", help="Procesar solo nombres que contengan este texto.")
    parser.add_argument(
        "--max-duration",
        type=float,
        default=600.0,
        help="Omitir fuentes redundantes mayores a estos segundos (por defecto: 600).",
    )
    parser.add_argument("--force", action="store_true", help="Volver a transcribir entradas existentes.")
    return parser.parse_args()


def relative_source(path: Path) -> str:
    return path.resolve().relative_to(VOICE_DIR.resolve()).as_posix()


def discover_sources(source_dirs: list[str], name_contains: str | None) -> list[Path]:
    requested = source_dirs or ["raw_yt"]
    sources: list[Path] = []
    for source_dir in requested:
        root = (VOICE_DIR / source_dir).resolve()
        if not root.is_relative_to(VOICE_DIR.resolve()):
            raise ValueError(f"La fuente sale de sf_voice_scrape: {source_dir}")
        if not root.is_dir():
            raise FileNotFoundError(root)
        for path in root.rglob("*"):
            if path.is_file() and path.suffix.lower() in AUDIO_EXTENSIONS:
                if name_contains and name_contains.casefold() not in path.name.casefold():
                    continue
                sources.append(path)
    return sorted(set(sources), key=lambda item: relative_source(item).casefold())


def load_cache(output: Path) -> dict:
    if not output.exists():
        return {"version": 1, "language": "es", "entries": {}}
    return json.loads(output.read_text(encoding="utf-8"))


def save_cache(output: Path, cache: dict) -> None:
    cache["updated_at"] = datetime.now(timezone.utc).isoformat()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(cache, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def media_duration(source: Path) -> float:
    with av.open(str(source)) as container:
        if container.duration is not None:
            return container.duration / av.time_base
        audio_stream = next(stream for stream in container.streams if stream.type == "audio")
        return float(audio_stream.duration * audio_stream.time_base)


def make_model(model_name: str, device: str, compute_type: str | None) -> WhisperModel:
    selected_device = "cuda" if device == "auto" else device
    selected_compute = compute_type or ("float16" if selected_device == "cuda" else "int8")
    try:
        return WhisperModel(model_name, device=selected_device, compute_type=selected_compute)
    except Exception as error:
        if device != "auto":
            raise
        print(f"CUDA no disponible ({error}); se usará CPU int8.", file=sys.stderr, flush=True)
        return WhisperModel(model_name, device="cpu", compute_type="int8")


def transcribe(model: WhisperModel, source: Path) -> dict:
    segments_iter, info = model.transcribe(
        str(source),
        language="es",
        beam_size=5,
        best_of=5,
        vad_filter=True,
        vad_parameters={"min_silence_duration_ms": 350},
        word_timestamps=True,
        condition_on_previous_text=False,
    )
    segments = []
    for segment in segments_iter:
        words = [
            {
                "start": round(word.start, 3),
                "end": round(word.end, 3),
                "text": word.word,
                "probability": round(word.probability, 4),
            }
            for word in (segment.words or [])
        ]
        segments.append(
            {
                "start": round(segment.start, 3),
                "end": round(segment.end, 3),
                "text": segment.text.strip(),
                "avg_logprob": round(segment.avg_logprob, 4),
                "no_speech_prob": round(segment.no_speech_prob, 4),
                "words": words,
            }
        )
    return {
        "duration": round(info.duration, 3),
        "duration_after_vad": round(info.duration_after_vad, 3),
        "language_probability": round(info.language_probability, 4),
        "text": " ".join(segment["text"] for segment in segments).strip(),
        "segments": segments,
    }


def main() -> int:
    args = parse_args()
    sources = discover_sources(args.source_dir, args.name_contains)
    if not sources:
        print("No se encontraron fuentes de audio.", file=sys.stderr)
        return 2

    output = args.output.resolve()
    cache = load_cache(output)
    model_path = Path(args.model)
    cache["model"] = model_path.name if model_path.exists() else args.model
    cache["source_policy"] = "local_only_no_rescrape"
    entries = cache.setdefault("entries", {})
    skipped = cache.setdefault("skipped_sources", {})
    eligible = []
    for source in sources:
        duration = media_duration(source)
        key = relative_source(source)
        if duration > args.max_duration:
            skipped[key] = {
                "duration": round(duration, 3),
                "reason": f"duración mayor al límite de {args.max_duration:g} s",
            }
        else:
            eligible.append(source)
            skipped.pop(key, None)
    pending = [source for source in eligible if args.force or relative_source(source) not in entries]
    print(f"Fuentes: {len(sources)}; pendientes: {len(pending)}", flush=True)
    save_cache(output, cache)
    if not pending:
        return 0

    model = make_model(args.model, args.device, args.compute_type)
    for index, source in enumerate(pending, start=1):
        key = relative_source(source)
        print(f"[{index}/{len(pending)}] {key}", flush=True)
        try:
            entries[key] = transcribe(model, source)
            entries[key]["size_bytes"] = source.stat().st_size
        except Exception as error:
            entries[key] = {"error": f"{type(error).__name__}: {error}"}
            print(f"ERROR: {error}", file=sys.stderr, flush=True)
        save_cache(output, cache)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
