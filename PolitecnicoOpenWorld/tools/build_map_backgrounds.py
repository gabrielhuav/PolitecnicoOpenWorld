#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
build_map_backgrounds.py
------------------------
Convierte videos de fondo de combate (MP4) en atlases de frames (filmstrip PNG)
+ JSON de metadatos + audio ambiental OGG, y reescala miniaturas estáticas a
fondos RGB del tamaño de combate.

Diseñado para minSdk=24: NADA de WebP animado. El motor pinta por sub-rects
del atlas. No toca Kotlin ni cablea assets al juego.

PING-PONG: el loop ida-y-vuelta va EMBEBIDO en el atlas
  [0,1,...,n-1, n-2, ..., 1]  →  al reproducir 0→frameCount-1 se rebobina
  visualmente al frame 0 sin salto.

LOGO: se sobrepone abajo-derecha en cada frame/estático para tapar la estrella
de Gemini (esquina inferior derecha del material fuente).

Dependencias: ffmpeg/ffprobe en PATH, Pillow. numpy es opcional (no requerido).

Uso:
  python tools/build_map_backgrounds.py
  python tools/build_map_backgrounds.py --input "C:\\ruta\\a\\material"
  python tools/build_map_backgrounds.py --logo "C:\\ruta\\logoPOW.png"
  python tools/build_map_backgrounds.py --dry-run
"""

from __future__ import annotations

import argparse
import json
import math
import re
import shutil
import subprocess
import sys
import tempfile
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, List, Optional, Sequence, Set, Tuple

from PIL import Image

# ---------------------------------------------------------------------------
# Rutas por defecto (absolutas, idempotentes)
# ---------------------------------------------------------------------------

REPO_ROOT = Path(__file__).resolve().parents[2]  # .../PolitecnicoOpenWorld (outer)
DEFAULT_INPUT = REPO_ROOT / "nuevoMaterial17JUL"
DEFAULT_LOGO = DEFAULT_INPUT / "logoPOW.png"
APP_ASSETS = (
    Path(__file__).resolve().parents[1]
    / "app"
    / "src"
    / "main"
    / "assets"
    / "STREETFIGHTER"
)
DEFAULT_IMAGES_OUT = APP_ASSETS / "IMAGES"
DEFAULT_SOUNDS_OUT = APP_ASSETS / "SOUNDS"

# ---------------------------------------------------------------------------
# Parámetros de pipeline
# ---------------------------------------------------------------------------

TARGET_FRAME_WIDTH = 640  # ~16:9 => ~640x360
SAMPLE_FPS = 12.0
LOOP_SECONDS = 2.75  # centro del rango 2.5–3.0 s
# Frames finales deseados tras ping-pong ≈ SAMPLE_FPS * LOOP_SECONDS ≈ 33
MAX_ATLAS_SIDE = 4096  # límite de textura en GPUs viejas
STATIC_SIZE = (1900, 850)  # tamaño aproximado de fondos actuales
AUDIO_BITRATE = "96k"
LOGO_WIDTH_FRAC = 0.14  # ~14% del ancho del frame (cubre la estrella Gemini)
LOGO_MARGIN_FRAC = 0.02  # margen ~2% desde bordes inferior/derecho

VIDEO_EXTS = {".mp4", ".mov", ".webm", ".mkv"}
IMAGE_EXTS = {".png", ".jpg", ".jpeg", ".webp", ".bmp"}
# Extensiones de audio / basura que se ignoran
SKIP_EXTS = {".mp3", ".wav", ".ogg", ".flac", ".m4a", ".txt", ".md"}

# Archivos a ignorar siempre (basura / logo / copias de assets)
IGNORE_NAMES_EXACT = {
    "image (1).jpg",
    "image (1).jpeg",
    "image (1).png",
    "logopow.png",  # comparación en lower
}


# ---------------------------------------------------------------------------
# Utilidades
# ---------------------------------------------------------------------------

def log(msg: str = "") -> None:
    print(msg, flush=True)


def require_ffmpeg() -> None:
    """Aborta con mensaje claro si ffmpeg/ffprobe no están en PATH."""
    missing = [bin_ for bin_ in ("ffmpeg", "ffprobe") if shutil.which(bin_) is None]
    if missing:
        log("ERROR: faltan herramientas en PATH: " + ", ".join(missing))
        log("Instala ffmpeg (https://ffmpeg.org/) y vuelve a ejecutar.")
        sys.exit(1)


def to_slug(stem: str) -> str:
    """
    Nombre de archivo -> snake_case sin espacios.
    "CECYT 2" -> cecyt_2
    "UNAM Biblioteca CU" -> unam_biblioteca_cu
    "Queso IPN" -> queso_ipn
    "ESIME AZC" -> esime_azc
    "IslaMunecas" -> islamunecas
    "Fes Acatlan" -> fes_acatlan
    "UAM Azcapo Noche 1" -> uam_azcapo_noche_1
    """
    s = normalize_stem(stem).lower()
    s = re.sub(r"[^a-z0-9]+", "_", s)
    s = re.sub(r"_+", "_", s).strip("_")
    return s or "unnamed"


def normalize_stem(stem: str) -> str:
    """
    Normaliza stems para emparejar tipográficos y typos conocidos.
    - "Escome ..." -> "Escom ..." (typo de ESCOM)
    - "Noche1" / "Noche2" -> "Noche 1" / "Noche 2"
    - colapsa espacios múltiples
    """
    s = stem.strip()
    # Typo Escome -> Escom (ESCOM)
    s = re.sub(r"(?i)^escome\b", "Escom", s)
    # "Noche1" / "Noche2" sin espacio -> "Noche 1" / "Noche 2"
    s = re.sub(r"(?i)\bnoche\s*([12])\b", r"Noche \1", s)
    s = re.sub(r"\s+", " ", s).strip()
    return s


def run_cmd(cmd: Sequence[str], *, check: bool = True) -> subprocess.CompletedProcess:
    """Ejecuta un comando y devuelve el CompletedProcess; lanza si falla."""
    return subprocess.run(
        list(cmd),
        check=check,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )


def probe_duration(video: Path) -> float:
    """Duración del video en segundos (ffprobe)."""
    cp = run_cmd(
        [
            "ffprobe",
            "-v", "error",
            "-show_entries", "format=duration",
            "-of", "default=noprint_wrappers=1:nokey=1",
            str(video),
        ]
    )
    try:
        return float(cp.stdout.strip())
    except ValueError:
        return 0.0


def has_audio_stream(video: Path) -> bool:
    """True si el contenedor trae al menos un stream de audio."""
    cp = run_cmd(
        [
            "ffprobe",
            "-v", "error",
            "-select_streams", "a",
            "-show_entries", "stream=index",
            "-of", "csv=p=0",
            str(video),
        ],
        check=False,
    )
    return bool(cp.stdout.strip())


def choose_grid(frame_count: int, fw: int, fh: int, max_side: int = MAX_ATLAS_SIDE) -> Tuple[int, int]:
    """
    Elige (cols, rows) para empaquetar frame_count celdas de fw×fh
    sin superar max_side en ningún lado. Prioriza poco desperdicio y aspecto ~1.
    """
    if frame_count <= 0:
        raise ValueError("frame_count debe ser > 0")
    max_cols = max(1, max_side // fw)
    max_rows = max(1, max_side // fh)
    if max_cols * max_rows < frame_count:
        raise RuntimeError(
            f"No caben {frame_count} frames de {fw}x{fh} en atlas "
            f"<={max_side}px (máx {max_cols}x{max_rows}={max_cols * max_rows})"
        )

    best: Optional[Tuple[int, int, float]] = None  # cols, rows, score
    for cols in range(1, max_cols + 1):
        rows = math.ceil(frame_count / cols)
        if rows > max_rows:
            continue
        atlas_w = cols * fw
        atlas_h = rows * fh
        if atlas_w > max_side or atlas_h > max_side:
            continue
        waste = cols * rows - frame_count
        aspect = max(atlas_w, atlas_h) / max(1, min(atlas_w, atlas_h))
        score = waste * 10.0 + aspect
        if best is None or score < best[2]:
            best = (cols, rows, score)

    if best is None:
        raise RuntimeError("No se encontró malla de atlas válida")
    return best[0], best[1]


def ping_pong(frames: List[Image.Image]) -> List[Image.Image]:
    """
    Embebe loop PING-PONG en la secuencia (ida + vuelta sin duplicar extremos):
      [0, 1, 2, ..., n-1, n-2, ..., 1]
    Al reproducir 0 → frameCount-1 y volver a 0 el empalme es suave
    (último frame de la ida = n-1; el ciclo siguiente arranca en 0, y la
    vuelta ya recorre n-2…1 antes de cerrar).
    """
    if len(frames) <= 1:
        return list(frames)
    forward = list(frames)
    # reverse sin el último (ya en forward) ni el primero (evita doble en el empalme)
    reverse = list(reversed(frames[1:-1])) if len(frames) > 2 else []
    return forward + reverse


def resize_frame(im: Image.Image, target_w: int = TARGET_FRAME_WIDTH) -> Image.Image:
    """Downscale manteniendo aspecto (~16:9 -> ~640x360). RGB sin alpha."""
    im = im.convert("RGB")
    w, h = im.size
    if w <= 0 or h <= 0:
        raise ValueError("imagen vacía")
    if w == target_w:
        return im
    new_h = max(1, round(h * (target_w / w)))
    return im.resize((target_w, new_h), Image.Resampling.LANCZOS)


def resize_static(im: Image.Image, size: Tuple[int, int] = STATIC_SIZE) -> Image.Image:
    """
    Reescala a ~1900x850 RGB sin alpha (cover + crop centrado).
    Coincide con la convención de fondos estáticos del juego.
    """
    im = im.convert("RGB")
    tw, th = size
    w, h = im.size
    scale = max(tw / w, th / h)
    nw, nh = max(1, round(w * scale)), max(1, round(h * scale))
    im = im.resize((nw, nh), Image.Resampling.LANCZOS)
    left = max(0, (nw - tw) // 2)
    top = max(0, (nh - th) // 2)
    return im.crop((left, top, left + tw, top + th))


def load_logo(logo_path: Path) -> Optional[Image.Image]:
    """Carga el logo (RGBA si tiene alfa; si no, RGB). None si no existe."""
    if logo_path is None or not logo_path.is_file():
        return None
    im = Image.open(logo_path)
    im.load()
    if im.mode in ("RGBA", "LA") or (im.mode == "P" and "transparency" in im.info):
        return im.convert("RGBA")
    return im.convert("RGB")


def paste_logo(
    base: Image.Image,
    logo: Optional[Image.Image],
    width_frac: float = LOGO_WIDTH_FRAC,
    margin_frac: float = LOGO_MARGIN_FRAC,
) -> Image.Image:
    """
    Sobrepone el logo en la esquina inferior derecha para tapar la estrella
    de Gemini. Escala a ~width_frac del ancho del frame; margen ~margin_frac.
    - Si el logo tiene alfa: se respeta (paste con máscara).
    - Si es RGB: se pega como recuadro opaco.
    Devuelve una imagen RGB nueva (no muta la original de forma destructiva
    si base ya es RGB copiada).
    """
    if logo is None:
        return base.convert("RGB") if base.mode != "RGB" else base

    out = base.convert("RGB")
    bw, bh = out.size
    logo_w = max(1, int(round(bw * width_frac)))
    # Mantener aspecto del logo
    lw, lh = logo.size
    logo_h = max(1, int(round(lh * (logo_w / max(1, lw)))))
    # No dejar que el logo sea más alto que ~40% del frame
    max_h = max(1, int(bh * 0.40))
    if logo_h > max_h:
        logo_h = max_h
        logo_w = max(1, int(round(lw * (logo_h / max(1, lh)))))

    scaled = logo.resize((logo_w, logo_h), Image.Resampling.LANCZOS)
    margin_x = max(0, int(round(bw * margin_frac)))
    margin_y = max(0, int(round(bh * margin_frac)))
    x = max(0, bw - logo_w - margin_x)
    y = max(0, bh - logo_h - margin_y)

    if scaled.mode == "RGBA":
        # Pegar con canal alfa por encima del fondo
        out.paste(scaled, (x, y), scaled)
    else:
        out.paste(scaled.convert("RGB"), (x, y))
    return out


def pack_atlas(frames: List[Image.Image], cols: int, rows: int) -> Image.Image:
    """Empaqueta frames en malla cols×rows (celdas vacías en negro)."""
    fw, fh = frames[0].size
    atlas = Image.new("RGB", (cols * fw, rows * fh), (0, 0, 0))
    for i, fr in enumerate(frames):
        c = i % cols
        r = i // cols
        if r >= rows:
            break
        if fr.size != (fw, fh):
            fr = fr.resize((fw, fh), Image.Resampling.LANCZOS)
        atlas.paste(fr.convert("RGB"), (c * fw, r * fh))
    return atlas


def kb(path: Path) -> float:
    if not path.is_file():
        return 0.0
    return path.stat().st_size / 1024.0


def should_ignore_file(path: Path) -> bool:
    """True si el archivo no es material de escenario (basura, logo, copias)."""
    name_lower = path.name.lower()
    if name_lower in IGNORE_NAMES_EXACT:
        return True
    # Logo de marca (entrada, no escenario)
    if name_lower.startswith("logo") and path.suffix.lower() in IMAGE_EXTS:
        return True
    # Copias de fondos ya existentes del juego (fondo_IPN_*, etc.)
    if name_lower.startswith("fondo_"):
        return True
    return False


# ---------------------------------------------------------------------------
# Procesamiento por tipo
# ---------------------------------------------------------------------------

@dataclass
class RowSummary:
    mapa: str          # nombre legible (stem original o slug)
    slug: str
    kind: str          # "video" | "static"
    atlas_wh: str = "-"
    frames: str = "-"
    atlas_kb: str = "-"
    audio_kb: str = "-"
    static_kb: str = "-"
    notes: str = ""


@dataclass
class BuildStats:
    rows: List[RowSummary] = field(default_factory=list)


def extract_sampled_frames(
    video: Path, work_dir: Path, fps: float, unique_count: int, start_sec: float
) -> List[Image.Image]:
    """
    Extrae `unique_count` frames a `fps` desde start_sec, downscale a TARGET_FRAME_WIDTH.
    Usa ffmpeg scale + fps filter; escribe PNGs temporales y los carga con Pillow.
    """
    pattern = work_dir / "frame_%04d.png"
    duration = unique_count / fps
    vf = f"fps={fps},scale={TARGET_FRAME_WIDTH}:-2:flags=lanczos"
    cmd = [
        "ffmpeg", "-y",
        "-ss", f"{start_sec:.3f}",
        "-t", f"{duration:.3f}",
        "-i", str(video),
        "-vf", vf,
        "-frames:v", str(unique_count),
        "-q:v", "2",
        str(pattern),
    ]
    cp = run_cmd(cmd, check=False)
    if cp.returncode != 0:
        log(f"  ffmpeg frames stderr: {cp.stderr[-800:]}")
        raise RuntimeError(f"ffmpeg falló extrayendo frames de {video.name}")

    paths = sorted(work_dir.glob("frame_*.png"))
    if not paths:
        raise RuntimeError(f"No se extrajeron frames de {video.name}")

    frames: List[Image.Image] = []
    for p in paths[:unique_count]:
        with Image.open(p) as im:
            frames.append(resize_frame(im.copy()))
    return frames


def extract_audio_ogg(video: Path, out_ogg: Path) -> bool:
    """
    Extrae pista de audio a OGG Vorbis ~96 kbps.
    Solo genera el archivo; no cablea a nada del juego.
    Devuelve True si se escribió el archivo.
    """
    if not has_audio_stream(video):
        log("  (sin stream de audio; se omite .ogg)")
        return False
    out_ogg.parent.mkdir(parents=True, exist_ok=True)
    cmd = [
        "ffmpeg", "-y",
        "-i", str(video),
        "-vn",
        "-c:a", "libvorbis",
        "-b:a", AUDIO_BITRATE,
        str(out_ogg),
    ]
    cp = run_cmd(cmd, check=False)
    if cp.returncode != 0 or not out_ogg.is_file():
        log(f"  AVISO: no se pudo extraer audio: {cp.stderr[-400:]}")
        return False
    return True


def process_video(
    video: Path,
    images_out: Path,
    sounds_out: Path,
    logo: Optional[Image.Image],
    dry_run: bool,
    stats: BuildStats,
) -> None:
    slug = to_slug(video.stem)
    atlas_path = images_out / f"fondo_{slug}_anim.png"
    json_path = images_out / f"fondo_{slug}_anim.json"
    audio_path = sounds_out / f"amb_{slug}.ogg"

    log(f"\n[VIDEO] {video.name}")
    log(f"  slug     = {slug}")
    log(f"  atlas    = {atlas_path}")
    log(f"  json     = {json_path}")
    log(f"  audio    = {audio_path}")
    log("  loop     = PING-PONG embebido en atlas (ida + vuelta)")

    if dry_run:
        stats.rows.append(
            RowSummary(mapa=video.stem, slug=slug, kind="video", notes="dry-run")
        )
        return

    duration = probe_duration(video)
    # ~30–36 frames finales: con ping-pong, n + (n-2) = 2n-2 ≈ target
    target_final = int(round(SAMPLE_FPS * LOOP_SECONDS))  # ~33
    target_final = max(30, min(36, target_final))
    unique_count = max(2, int(round((target_final + 2) / 2)))

    segment_dur = unique_count / SAMPLE_FPS
    if duration > segment_dur + 0.1:
        start_sec = max(0.0, (duration - segment_dur) / 2.0)
    else:
        start_sec = 0.0
        unique_count = max(2, min(unique_count, int(duration * SAMPLE_FPS) or 2))

    log(
        f"  duración={duration:.2f}s | únicos={unique_count} @ {SAMPLE_FPS}fps "
        f"| start={start_sec:.2f}s | logo={'sí' if logo else 'no'}"
    )

    with tempfile.TemporaryDirectory(prefix="map_bg_") as tmp:
        work = Path(tmp)
        frames = extract_sampled_frames(
            video, work, SAMPLE_FPS, unique_count, start_sec
        )
        # 1) PING-PONG embebido  2) LOGO en cada frame del loop  3) atlas
        loop_frames = ping_pong(frames)
        branded: List[Image.Image] = [paste_logo(fr, logo) for fr in loop_frames]

        fw, fh = branded[0].size
        cols, rows = choose_grid(len(branded), fw, fh)
        atlas = pack_atlas(branded, cols, rows)

        images_out.mkdir(parents=True, exist_ok=True)
        atlas.save(atlas_path, format="PNG", optimize=True)

        meta = {
            "frameWidth": fw,
            "frameHeight": fh,
            "cols": cols,
            "rows": rows,
            "frameCount": len(branded),
            "fps": SAMPLE_FPS,
            "loop": True,
            "pingPong": True,
            "slug": slug,
            "source": video.name,
        }
        json_path.write_text(
            json.dumps(meta, indent=2, ensure_ascii=False) + "\n",
            encoding="utf-8",
        )
        log(
            f"  atlas {atlas.size[0]}x{atlas.size[1]} | "
            f"grid {cols}x{rows} | frames={len(branded)} (ping-pong) | "
            f"{kb(atlas_path):.0f} KB"
        )

        audio_ok = extract_audio_ogg(video, audio_path)
        if audio_ok:
            log(f"  audio   {kb(audio_path):.0f} KB")

        stats.rows.append(
            RowSummary(
                mapa=video.stem,
                slug=slug,
                kind="video",
                atlas_wh=f"{atlas.size[0]}x{atlas.size[1]}",
                frames=str(len(branded)),
                atlas_kb=f"{kb(atlas_path):.0f}",
                audio_kb=f"{kb(audio_path):.0f}" if audio_ok else "—",
            )
        )

        for fr in frames + loop_frames + branded:
            try:
                fr.close()
            except Exception:
                pass
        atlas.close()


def process_static(
    image: Path,
    images_out: Path,
    logo: Optional[Image.Image],
    dry_run: bool,
    stats: BuildStats,
) -> None:
    slug = to_slug(image.stem)
    out_path = images_out / f"fondo_{slug}_1.png"

    log(f"\n[STATIC] {image.name}")
    log(f"  slug  = {slug}")
    log(f"  out   = {out_path}")
    log(f"  logo  = {'sí' if logo else 'no'}")

    if dry_run:
        stats.rows.append(
            RowSummary(mapa=image.stem, slug=slug, kind="static", notes="dry-run")
        )
        return

    with Image.open(image) as im:
        out = resize_static(im)
        out = paste_logo(out, logo)
        images_out.mkdir(parents=True, exist_ok=True)
        out.save(out_path, format="PNG", optimize=True)
        log(f"  size  = {out.size[0]}x{out.size[1]} RGB | {kb(out_path):.0f} KB")
        out.close()

    stats.rows.append(
        RowSummary(
            mapa=image.stem,
            slug=slug,
            kind="static",
            static_kb=f"{kb(out_path):.0f}",
            notes=f"{STATIC_SIZE[0]}x{STATIC_SIZE[1]}",
        )
    )


def _stem_key(path: Path) -> str:
    """Clave de emparejamiento: stem normalizado en minúsculas."""
    return normalize_stem(path.stem).lower()


def collect_inputs(input_dir: Path) -> Tuple[List[Path], List[Path]]:
    """
    Lista videos y miniaturas SIN video correspondiente (mismo stem normalizado).
    Aplica limpieza: image(1), duplicados Noche1/Noche 1, Escome->Escom,
    ignora logo y copias fondo_*. No falla por stems ambiguos: avisa y sigue.
    """
    videos: List[Path] = []
    video_keys: Set[str] = set()

    for p in sorted(input_dir.iterdir()):
        if not p.is_file():
            continue
        if should_ignore_file(p):
            if p.suffix.lower() in VIDEO_EXTS | IMAGE_EXTS:
                log(f"(ignorado) {p.name}")
            continue
        ext = p.suffix.lower()
        if ext in SKIP_EXTS:
            continue
        if ext in VIDEO_EXTS:
            key = _stem_key(p)
            if key in video_keys:
                log(f"AVISO: video duplicado por stem '{key}': {p.name} (se omite)")
                continue
            videos.append(p)
            video_keys.add(key)

    # Candidatos de imagen estática (sin video emparejado)
    # Preferencia al deduplicar por slug: "Noche 1" > "Noche1", .png > .jpg
    candidates: Dict[str, Path] = {}  # slug -> path elegido
    slug_sources: Dict[str, List[str]] = {}

    def preference_score(path: Path) -> Tuple[int, int, int]:
        """Mayor = mejor. Prioriza espacio en 'Noche N', luego png, luego nombre corto."""
        stem = path.stem
        has_space_noche = 1 if re.search(r"(?i)noche\s+[12]", stem) else 0
        is_png = 1 if path.suffix.lower() == ".png" else 0
        # Preferir nombres con espacios "naturales" sobre colapsados
        return (has_space_noche, is_png, -len(path.name))

    for p in sorted(input_dir.iterdir()):
        if not p.is_file():
            continue
        if should_ignore_file(p):
            continue
        ext = p.suffix.lower()
        if ext not in IMAGE_EXTS:
            continue
        key = _stem_key(p)
        # Si hay video con el mismo stem normalizado → miniatura, no estático
        if key in video_keys:
            log(f"(omitida miniatura con video) {p.name}")
            continue

        slug = to_slug(p.stem)
        slug_sources.setdefault(slug, []).append(p.name)
        prev = candidates.get(slug)
        if prev is None or preference_score(p) > preference_score(prev):
            if prev is not None:
                log(
                    f"AVISO: stem ambiguo/duplicado slug='{slug}': "
                    f"prefiero '{p.name}' sobre '{prev.name}'"
                )
            candidates[slug] = p
        else:
            log(
                f"AVISO: stem ambiguo/duplicado slug='{slug}': "
                f"me quedo con '{prev.name}', omito '{p.name}'"
            )

    images = [candidates[s] for s in sorted(candidates.keys())]
    return videos, images


def print_summary(stats: BuildStats) -> None:
    log("\n" + "=" * 100)
    log("RESUMEN — videos (atlas + json + audio)")
    log("=" * 100)
    header = (
        f"{'mapa':<32} {'slug':<28} {'atlas WxH':<12} "
        f"{'frames':>6} {'kb':>7} {'audio':>7}"
    )
    log(header)
    log("-" * 100)
    for r in stats.rows:
        if r.kind != "video":
            continue
        log(
            f"{r.mapa:<32} {r.slug:<28} {r.atlas_wh:<12} "
            f"{r.frames:>6} {r.atlas_kb:>7} {r.audio_kb:>7}"
            + (f"  {r.notes}" if r.notes else "")
        )

    statics = [r for r in stats.rows if r.kind == "static"]
    log("\n" + "=" * 100)
    log("SOLO ESTÁTICOS (sin video) — fondo_<slug>_1.png")
    log("=" * 100)
    if not statics:
        log("(ninguno)")
    else:
        log(f"{'mapa':<32} {'slug':<28} {'kb':>7}  notas")
        log("-" * 100)
        for r in statics:
            log(
                f"{r.mapa:<32} {r.slug:<28} {r.static_kb:>7}  "
                f"{r.notes}"
            )

    n_v = sum(1 for r in stats.rows if r.kind == "video")
    n_s = len(statics)
    log("=" * 100)
    log(f"Total: {n_v} videos (atlas+json+audio) | {n_s} solo-estáticos")
    log("PING-PONG: embebido en cada atlas (frameCount ≈ 2*únicos - 2).")
    log("LOGO: aplicado abajo-derecha en cada frame del atlas y en cada estático.")


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = argparse.ArgumentParser(
        description="Genera atlases animados y fondos estáticos para mapas SF."
    )
    parser.add_argument(
        "--input",
        type=Path,
        default=DEFAULT_INPUT,
        help=f"Carpeta de material (default: {DEFAULT_INPUT})",
    )
    parser.add_argument(
        "--images-out",
        type=Path,
        default=DEFAULT_IMAGES_OUT,
        help=f"Salida IMAGES (default: {DEFAULT_IMAGES_OUT})",
    )
    parser.add_argument(
        "--sounds-out",
        type=Path,
        default=DEFAULT_SOUNDS_OUT,
        help=f"Salida SOUNDS (default: {DEFAULT_SOUNDS_OUT})",
    )
    parser.add_argument(
        "--logo",
        type=Path,
        default=DEFAULT_LOGO,
        help=f"Logo POW para tapar marca Gemini (default: {DEFAULT_LOGO})",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Solo listar qué se procesaría, sin escribir archivos.",
    )
    args = parser.parse_args(argv)

    input_dir: Path = args.input.resolve()
    images_out: Path = args.images_out.resolve()
    sounds_out: Path = args.sounds_out.resolve()
    logo_path: Path = args.logo.resolve()

    log("build_map_backgrounds.py")
    log(f"  input      = {input_dir}")
    log(f"  images_out = {images_out}")
    log(f"  sounds_out = {sounds_out}")
    log(f"  logo       = {logo_path}")
    log(f"  dry_run    = {args.dry_run}")

    if not input_dir.is_dir():
        log(f"ERROR: no existe el directorio de entrada: {input_dir}")
        return 1

    require_ffmpeg()

    logo_img = load_logo(logo_path)
    if logo_img is None:
        log(f"AVISO: no se encontró logo en {logo_path}; se generará SIN marca POW")
    else:
        log(f"  logo size = {logo_img.size} mode={logo_img.mode}")

    videos, images = collect_inputs(input_dir)
    log(f"\nEncontrados: {len(videos)} video(s), {len(images)} estático(s) sin video")

    stats = BuildStats()
    errors: List[str] = []

    for v in videos:
        try:
            process_video(v, images_out, sounds_out, logo_img, args.dry_run, stats)
        except Exception as e:
            log(f"  ERROR en {v.name}: {e}")
            errors.append(f"{v.name}: {e}")
            stats.rows.append(
                RowSummary(
                    mapa=v.stem,
                    slug=to_slug(v.stem),
                    kind="video",
                    notes=f"ERROR: {e}",
                )
            )

    for im in images:
        try:
            process_static(im, images_out, logo_img, args.dry_run, stats)
        except Exception as e:
            log(f"  ERROR en {im.name}: {e}")
            errors.append(f"{im.name}: {e}")
            stats.rows.append(
                RowSummary(
                    mapa=im.stem,
                    slug=to_slug(im.stem),
                    kind="static",
                    notes=f"ERROR: {e}",
                )
            )

    print_summary(stats)

    if logo_img is not None:
        try:
            logo_img.close()
        except Exception:
            pass

    if errors:
        log(f"\nCompletado con {len(errors)} error(es).")
        return 1
    log("\nListo.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
