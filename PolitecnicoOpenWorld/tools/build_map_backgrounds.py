#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
build_map_backgrounds.py
------------------------
Convierte videos de fondo de combate (MP4) en atlases de frames (filmstrip WebP)
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
DEFAULT_STATIC_OUT = REPO_ROOT / "additional_assets" / "STREETFIGHTER" / "generated_static_backgrounds"

# ---------------------------------------------------------------------------
# Parámetros de pipeline (CAPADO PARA GAMA BAJA)
# ---------------------------------------------------------------------------

# CAP DURO de textura en GPUs de gama baja (Android): 2048 px por lado.
# Los atlas >2048 no se muestran en muchos equipos (por eso fallaban).
MAX_ATLAS_SIDE = 2048

# Frame ~448-480 px de ancho, aspecto 16:9 FIJO (crop-to-fill, sin letterbox).
# 480×270 × grid 4×7 = 1920×1890 ≤ 2048 (cabe con ~28 frames ping-pong).
TARGET_FRAME_WIDTH = 480
TARGET_FRAME_HEIGHT = 270  # 16:9 exacto
# Los videos fuente duran ~10 s. Antes se tomaban 15 cuadros contiguos a 12 fps:
# solo 1.25 s del centro, donde muchos clips apenas se mueven y parecían congelados.
# Ahora se muestrean 5 s repartidos y el atlas se reproduce a 6 fps: movimiento visible
# sin aumentar el número de cuadros ni el peso del paquete.
SOURCE_SAMPLE_SPAN_SECONDS = 5.0
PLAYBACK_FPS = 6.0
# ~24-30 frames finales con ping-pong (2n-2): n=15 → 28 frames
TARGET_FINAL_FRAMES = 28
STATIC_SIZE = (1900, 850)  # ≤2048 por lado; convención de fondos estáticos
THUMB_WIDTH = 256  # miniatura de un frame para el selector de mapa
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


def crop_to_fill(im: Image.Image, size: Tuple[int, int]) -> Image.Image:
    """
    Cover + crop centrado a `size` (RGB). TODOS los frames/estáticos del mismo
    pipeline salen con el MISMO aspecto (sin letterbox ni tamaños raros).
    """
    im = im.convert("RGB")
    tw, th = size
    w, h = im.size
    if w <= 0 or h <= 0:
        raise ValueError("imagen vacía")
    scale = max(tw / w, th / h)
    nw, nh = max(1, round(w * scale)), max(1, round(h * scale))
    im = im.resize((nw, nh), Image.Resampling.LANCZOS)
    left = max(0, (nw - tw) // 2)
    top = max(0, (nh - th) // 2)
    return im.crop((left, top, left + tw, top + th))


def resize_frame(
    im: Image.Image,
    size: Tuple[int, int] = (TARGET_FRAME_WIDTH, TARGET_FRAME_HEIGHT),
) -> Image.Image:
    """Downscale crop-to-fill a tamaño de frame FIJO (~480×270, 16:9)."""
    return crop_to_fill(im, size)


def resize_static(im: Image.Image, size: Tuple[int, int] = STATIC_SIZE) -> Image.Image:
    """
    Reescala a ~1900x850 RGB sin alpha (cover + crop centrado).
    Coincide con la convención de fondos estáticos del juego (≤2048 por lado).
    """
    return crop_to_fill(im, size)


def save_thumb(im: Image.Image, out_path: Path, width: int = THUMB_WIDTH) -> Path:
    """
    Miniatura de UN frame (~width px de ancho, aspecto conservado, con logo ya
    aplicado en `im`). Naming: <archivo sin .png>_thumb.png
    """
    w, h = im.size
    tw = width
    th = max(1, int(round(h * (tw / max(1, w)))))
    thumb = im.convert("RGB").resize((tw, th), Image.Resampling.LANCZOS)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    thumb.save(out_path, format="PNG", optimize=True)
    thumb.close()
    return out_path


def thumb_path_for(main_asset: Path) -> Path:
    """fondo_foo_anim.webp -> fondo_foo_anim_thumb.png."""
    return main_asset.with_name(main_asset.stem + "_thumb.png")


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
    Extrae `unique_count` frames a `fps` desde start_sec.
    ffmpeg hace un scale ancho (más barato); Pillow aplica crop-to-fill al tamaño FIJO
    (TARGET_FRAME_WIDTH × TARGET_FRAME_HEIGHT) para unificar aspecto en todos los mapas.
    """
    pattern = work_dir / "frame_%04d.png"
    duration = unique_count / fps
    # Scale a un ancho algo mayor que el target; el crop final fija 16:9 exacto
    scale_w = max(TARGET_FRAME_WIDTH, 640)
    vf = f"fps={fps},scale={scale_w}:-2:flags=lanczos"
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
    atlas_path = images_out / f"fondo_{slug}_anim.webp"
    json_path = images_out / f"fondo_{slug}_anim.json"
    audio_path = sounds_out / f"amb_{slug}.ogg"

    thumb_out = thumb_path_for(atlas_path)
    log(f"\n[VIDEO] {video.name}")
    log(f"  slug     = {slug}")
    log(f"  atlas    = {atlas_path}")
    log(f"  json     = {json_path}")
    log(f"  thumb    = {thumb_out}")
    log(f"  audio    = {audio_path}")
    log("  loop     = PING-PONG embebido en atlas (ida + vuelta)")
    log(f"  cap      = atlas ≤ {MAX_ATLAS_SIDE}px | frame {TARGET_FRAME_WIDTH}x{TARGET_FRAME_HEIGHT}")

    if dry_run:
        stats.rows.append(
            RowSummary(mapa=video.stem, slug=slug, kind="video", notes="dry-run")
        )
        return

    duration = probe_duration(video)
    # ~24–30 frames finales: con ping-pong, n + (n-2) = 2n-2 ≈ TARGET_FINAL_FRAMES
    target_final = max(24, min(30, TARGET_FINAL_FRAMES))
    unique_count = max(2, int(round((target_final + 2) / 2)))

    segment_dur = min(duration, SOURCE_SAMPLE_SPAN_SECONDS)
    source_sample_fps = unique_count / max(segment_dur, 0.1)
    start_sec = max(0.0, (duration - segment_dur) / 2.0)

    log(
        f"  duración={duration:.2f}s | únicos={unique_count} repartidos en "
        f"{segment_dur:.2f}s ({source_sample_fps:.2f}fps fuente) | "
        f"playback={PLAYBACK_FPS:.1f}fps | start={start_sec:.2f}s | "
        f"logo={'sí' if logo else 'no'}"
    )

    with tempfile.TemporaryDirectory(prefix="map_bg_") as tmp:
        work = Path(tmp)
        frames = extract_sampled_frames(
            video, work, source_sample_fps, unique_count, start_sec
        )
        # 1) PING-PONG embebido  2) LOGO en cada frame del loop  3) atlas ≤2048
        loop_frames = ping_pong(frames)
        branded: List[Image.Image] = [paste_logo(fr, logo) for fr in loop_frames]

        fw, fh = branded[0].size
        # Seguridad: todos los frames deben ser idénticos (crop-to-fill fijo)
        for fr in branded:
            if fr.size != (fw, fh):
                raise RuntimeError(f"frame size mismatch: {fr.size} != {(fw, fh)}")
        cols, rows = choose_grid(len(branded), fw, fh, MAX_ATLAS_SIDE)
        atlas = pack_atlas(branded, cols, rows)
        if atlas.size[0] > MAX_ATLAS_SIDE or atlas.size[1] > MAX_ATLAS_SIDE:
            raise RuntimeError(
                f"atlas {atlas.size} supera CAP {MAX_ATLAS_SIDE} "
                f"(fw={fw} fh={fh} frames={len(branded)} grid={cols}x{rows})"
            )

        images_out.mkdir(parents=True, exist_ok=True)
        atlas.save(
            atlas_path,
            format="WEBP",
            lossless=True,
            quality=100,
            method=6,
            exact=True,
        )

        meta = {
            "frameWidth": fw,
            "frameHeight": fh,
            "cols": cols,
            "rows": rows,
            "frameCount": len(branded),
            "fps": PLAYBACK_FPS,
            "loop": True,
            "pingPong": True,
            "slug": slug,
            "source": video.name,
            "maxAtlasSide": MAX_ATLAS_SIDE,
            "logoApplied": logo is not None,
            "sourceSampleSpanSeconds": segment_dur,
        }
        json_path.write_text(
            json.dumps(meta, indent=2, ensure_ascii=False) + "\n",
            encoding="utf-8",
        )
        # Miniatura = primer frame del loop (ya con logo)
        save_thumb(branded[0], thumb_out)
        log(
            f"  atlas {atlas.size[0]}x{atlas.size[1]} | "
            f"grid {cols}x{rows} | frames={len(branded)} (ping-pong) | "
            f"{kb(atlas_path):.0f} KB | thumb {kb(thumb_out):.0f} KB"
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
                notes=f"thumb={kb(thumb_out):.0f}KB",
            )
        )

        for fr in frames + loop_frames + branded:
            try:
                fr.close()
            except Exception:
                pass
        atlas.close()


def ken_burns_frames(im: Image.Image, unique_count: int) -> List[Image.Image]:
    """
    Genera `unique_count` frames 480×270 con paneo suave (Ken Burns) a partir de
    una foto fija, para que TODOS los escenarios tengan atlas animado aunque no
    haya video. El recorte se mueve lentamente en horizontal (±6%).
    """
    base = crop_to_fill(im, (TARGET_FRAME_WIDTH, TARGET_FRAME_HEIGHT))
    # Trabajar a 1.12× para poder panear sin letterbox
    big_w = int(TARGET_FRAME_WIDTH * 1.12)
    big_h = int(TARGET_FRAME_HEIGHT * 1.12)
    big = crop_to_fill(im, (big_w, big_h))
    frames: List[Image.Image] = []
    max_dx = big_w - TARGET_FRAME_WIDTH
    for i in range(unique_count):
        t = i / max(1, unique_count - 1)  # 0..1
        # ida suave (luego ping-pong cierra la vuelta)
        dx = int(round(max_dx * t))
        dy = (big_h - TARGET_FRAME_HEIGHT) // 2
        fr = big.crop((dx, dy, dx + TARGET_FRAME_WIDTH, dy + TARGET_FRAME_HEIGHT))
        frames.append(fr.convert("RGB"))
    big.close()
    base.close()
    return frames


def process_static(
    image: Path,
    images_out: Path,
    static_out: Path,
    logo: Optional[Image.Image],
    dry_run: bool,
    stats: BuildStats,
) -> None:
    """
    Foto sin video → (1) PNG estático 1900×850 + thumb  y  (2) atlas ANIMADO
    con paneo Ken Burns (mismo pipeline que video: ping-pong, logo, ≤2048).
    El juego usa el `_anim` para pelea; el `_1` queda de respaldo.
    """
    slug = to_slug(image.stem)
    out_path = static_out / f"fondo_{slug}_1.png"
    thumb_static = thumb_path_for(out_path)
    atlas_path = images_out / f"fondo_{slug}_anim.webp"
    json_path = images_out / f"fondo_{slug}_anim.json"
    thumb_anim = thumb_path_for(atlas_path)

    log(f"\n[STATIC→ANIM] {image.name}")
    log(f"  slug   = {slug}")
    log(f"  still  = {out_path}")
    log(f"  atlas  = {atlas_path}")
    log(f"  logo   = {'sí' if logo else 'no'}")

    if dry_run:
        stats.rows.append(
            RowSummary(mapa=image.stem, slug=slug, kind="static", notes="dry-run→anim")
        )
        return

    target_final = max(24, min(30, TARGET_FINAL_FRAMES))
    unique_count = max(2, int(round((target_final + 2) / 2)))
    frame_count = 0
    atlas_wh = "-"

    with Image.open(image) as im:
        # --- estático de respaldo ---
        still = resize_static(im)
        still = paste_logo(still, logo)
        static_out.mkdir(parents=True, exist_ok=True)
        still.save(out_path, format="PNG", optimize=True)
        save_thumb(still, thumb_static)

        # --- animado Ken Burns (crop-to-fill 480×270 + ping-pong + logo) ---
        frames = ken_burns_frames(im, unique_count)
        branded = [paste_logo(fr, logo) for fr in frames]
        loop_frames = ping_pong(branded)
        frame_count = len(loop_frames)
        fw, fh = loop_frames[0].size
        cols, rows = choose_grid(frame_count, fw, fh, MAX_ATLAS_SIDE)
        atlas = pack_atlas(loop_frames, cols, rows)
        if atlas.size[0] > MAX_ATLAS_SIDE or atlas.size[1] > MAX_ATLAS_SIDE:
            raise RuntimeError(f"atlas estático {atlas.size} > {MAX_ATLAS_SIDE}")
        images_out.mkdir(parents=True, exist_ok=True)
        atlas.save(
            atlas_path,
            format="WEBP",
            lossless=True,
            quality=100,
            method=6,
            exact=True,
        )
        atlas_wh = f"{atlas.size[0]}x{atlas.size[1]}"
        meta = {
            "frameWidth": fw,
            "frameHeight": fh,
            "cols": cols,
            "rows": rows,
            "frameCount": frame_count,
            "fps": PLAYBACK_FPS,
            "loop": True,
            "pingPong": True,
            "slug": slug,
            "source": image.name,
            "maxAtlasSide": MAX_ATLAS_SIDE,
            "kenBurns": True,
            "logoApplied": logo is not None,
        }
        json_path.write_text(
            json.dumps(meta, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
        )
        save_thumb(loop_frames[0], thumb_anim)
        log(
            f"  still {still.size[0]}x{still.size[1]} {kb(out_path):.0f}KB | "
            f"atlas {atlas_wh} frames={frame_count} {kb(atlas_path):.0f}KB"
        )
        for fr in frames + branded + loop_frames:
            try:
                fr.close()
            except Exception:
                pass
        still.close()
        atlas.close()

    stats.rows.append(
        RowSummary(
            mapa=image.stem,
            slug=slug,
            kind="static",
            atlas_wh=atlas_wh,
            frames=str(frame_count),
            atlas_kb=f"{kb(atlas_path):.0f}",
            static_kb=f"{kb(out_path):.0f}",
            notes="kenBurns+still",
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
    log(f"CAP atlas ≤ {MAX_ATLAS_SIDE}px | frame {TARGET_FRAME_WIDTH}x{TARGET_FRAME_HEIGHT}")
    log("PING-PONG: embebido en cada atlas (frameCount ≈ 2*únicos - 2).")
    log("LOGO: aplicado abajo-derecha en cada frame del atlas y en cada estático.")
    log("THUMB: <archivo>_thumb.png (~256 px) para el selector de mapa.")


def parse_theme_backgrounds() -> List[str]:
    """
    Extrae los `file` de SfTheme.fullBackgrounds desde SfTheme.kt
    (sin compilar; solo regex). Orden de aparición en el tema.
    """
    theme_path = (
        Path(__file__).resolve().parents[1]
        / "app"
        / "src"
        / "main"
        / "java"
        / "ovh"
        / "gabrielhuav"
        / "pow"
        / "features"
        / "streetfighter"
        / "data"
        / "SfTheme.kt"
    )
    if not theme_path.is_file():
        log(f"AVISO: no se encontró SfTheme.kt en {theme_path}")
        return []
    text = theme_path.read_text(encoding="utf-8")
    # SfStageBg("fondo_....webp", "Nombre")
    return re.findall(r'SfStageBg\(\s*"([^"]+\.(?:png|webp))"', text)


def diagnose_theme_backgrounds(images_out: Path) -> List[str]:
    """
    Por cada escenario de SfTheme.fullBackgrounds:
      - el atlas .webp (o el legado .png) existe
      - si es atlas (_anim): ≤2048 por lado + JSON consistente
        (cols*frameW == ancho, rows*frameH == alto, cols*rows >= frameCount)
      - miniatura _thumb.png existe (aviso si falta)
    Devuelve lista de fallos (strings).
    """
    files = parse_theme_backgrounds()
    log("\n" + "=" * 100)
    log(f"DIAGNÓSTICO SfTheme.fullBackgrounds ({len(files)} escenarios) — cap {MAX_ATLAS_SIDE}")
    log("=" * 100)
    fails: List[str] = []
    ok_n = 0
    for fname in files:
        asset = images_out / fname
        issues: List[str] = []
        if not asset.is_file():
            issues.append("ASSET AUSENTE")
            fails.append(f"{fname}: ASSET AUSENTE")
            log(f"  FAIL  {fname}: ASSET AUSENTE")
            continue

        try:
            with Image.open(asset) as im:
                w, h = im.size
        except Exception as e:
            issues.append(f"no se pudo abrir ({e})")
            fails.append(f"{fname}: {issues[-1]}")
            log(f"  FAIL  {fname}: {issues[-1]}")
            continue

        is_anim = "_anim." in fname
        thumb = thumb_path_for(asset)
        thumb_note = f"thumb={'sí' if thumb.is_file() else 'NO'}"

        if is_anim:
            if w > MAX_ATLAS_SIDE or h > MAX_ATLAS_SIDE:
                issues.append(f"atlas {w}x{h} > {MAX_ATLAS_SIDE}")
            json_path = asset.with_suffix(".json")
            if not json_path.is_file():
                issues.append("JSON AUSENTE")
            else:
                try:
                    meta = json.loads(json_path.read_text(encoding="utf-8"))
                    fw = int(meta["frameWidth"])
                    fh = int(meta["frameHeight"])
                    cols = int(meta["cols"])
                    rows = int(meta["rows"])
                    fc = int(meta["frameCount"])
                    if cols * fw != w:
                        issues.append(f"cols*frameW={cols * fw} != ancho={w}")
                    if rows * fh != h:
                        issues.append(f"rows*frameH={rows * fh} != alto={h}")
                    if cols * rows < fc:
                        issues.append(f"cols*rows={cols * rows} < frameCount={fc}")
                except Exception as e:
                    issues.append(f"JSON inválido ({e})")
        else:
            if w > MAX_ATLAS_SIDE or h > MAX_ATLAS_SIDE:
                issues.append(f"estático {w}x{h} > {MAX_ATLAS_SIDE}")

        if not thumb.is_file():
            issues.append("thumb ausente")

        if issues:
            fails.append(f"{fname}: {'; '.join(issues)}")
            log(f"  FAIL  {fname} ({w}x{h}, {kb(asset):.0f}KB) — {'; '.join(issues)}")
        else:
            ok_n += 1
            log(f"  OK    {fname} ({w}x{h}, {kb(asset):.0f}KB, {thumb_note})")

    log("-" * 100)
    log(f"Diagnóstico: {ok_n} OK | {len(fails)} FAIL de {len(files)}")
    if fails:
        log("Fallaban / fallan:")
        for f in fails:
            log(f"  · {f}")
    return fails


def main(argv: Optional[Sequence[str]] = None) -> int:
    # PowerShell/Windows puede heredar cp1252 y fallar al imprimir “≤”, “→” o emojis.
    # El pipeline no debe abortar antes de procesar assets por el encoding de la consola.
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    if hasattr(sys.stderr, "reconfigure"):
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
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
        "--static-out",
        type=Path,
        default=DEFAULT_STATIC_OUT,
        help=f"Salida auxiliar fuera del proyecto Android (default: {DEFAULT_STATIC_OUT})",
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
    static_out: Path = args.static_out.resolve()
    logo_path: Path = args.logo.resolve()

    log("build_map_backgrounds.py")
    log(f"  input      = {input_dir}")
    log(f"  images_out = {images_out}")
    log(f"  sounds_out = {sounds_out}")
    log(f"  static_out = {static_out}")
    log(f"  logo       = {logo_path}")
    log(f"  dry_run    = {args.dry_run}")
    log(f"  max_atlas  = {MAX_ATLAS_SIDE}px | frame {TARGET_FRAME_WIDTH}x{TARGET_FRAME_HEIGHT}")
    log(f"  frames~    = {TARGET_FINAL_FRAMES} (ping-pong) | thumb_w={THUMB_WIDTH}")

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
            process_static(im, images_out, static_out, logo_img, args.dry_run, stats)
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

    # Diagnóstico post-build de los escenarios referenciados en SfTheme.fullBackgrounds
    if not args.dry_run:
        diag_fails = diagnose_theme_backgrounds(images_out)
    else:
        diag_fails = []

    if logo_img is not None:
        try:
            logo_img.close()
        except Exception:
            pass

    if errors:
        log(f"\nCompletado con {len(errors)} error(es) de build.")
        return 1
    if diag_fails:
        log(f"\nBuild OK pero diagnóstico con {len(diag_fails)} fallo(s) de tema.")
        return 1
    log("\nListo.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
