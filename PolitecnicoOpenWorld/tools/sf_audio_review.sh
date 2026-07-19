#!/usr/bin/env bash
# sf_audio_review.sh — inventario + revisión de los audios de HUELUM VS. GOYA.
#
# 1) Lista todos los .ogg/.mp3 de STREETFIGHTER/SOUNDS con su tamaño y a qué categoría/peleadór
#    pertenecen (según SfVoicePack del VM y la convención special_<id>).
# 2) Convierte cada voz a .mp3 en una carpeta de revisión para escucharlas rápido.
#
# Uso:
#   bash tools/sf_audio_review.sh            # solo inventario (no convierte)
#   bash tools/sf_audio_review.sh --mp3      # además convierte a mp3 en tools/_audio_review/
#
# Requiere ffmpeg en PATH.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
SOUNDS="$HERE/../app/src/main/assets/STREETFIGHTER/SOUNDS"
REVIEW="$HERE/_audio_review"
DO_MP3="${1:-}"

echo "== INVENTARIO SOUNDS ($(du -sh "$SOUNDS" | cut -f1)) =="
echo
echo "-- GLOBALES (compartidos por TODOS; golpear/recibir golpe/land/hadouken) — NO borrar --"
for k in light-attack medium-attack heavy-attack \
         light-punch-hit medium-punch-hit heavy-punch-hit \
         light-kick-hit medium-kick-hit heavy-kick-hit land hadouken; do
  f="$SOUNDS/$k.ogg"; [ -f "$f" ] && printf "  %8s  %s\n" "$(du -h "$f"|cut -f1)" "$k.ogg"
done
echo
echo "-- MÚSICA (lobby + batalla por dificultad) --"
for f in "$SOUNDS"/prankedy_*.mp3; do [ -f "$f" ] && printf "  %8s  %s\n" "$(du -h "$f"|cut -f1)" "$(basename "$f")"; done
echo
echo "-- PACKS DE VOZ por evento (policías + Paparazzi 1) --"
for f in "$SOUNDS"/special_pol_h_* "$SOUNDS"/special_pol_m_* "$SOUNDS"/special_gr_* "$SOUNDS"/special_papz1_*; do
  [ -f "$f" ] && printf "  %8s  %s\n" "$(du -h "$f"|cut -f1)" "$(basename "$f")"
done
echo
echo "-- VOZ DE SPECIAL por peleadór (special_<id>.ogg; suena en su poder especial) --"
for f in "$SOUNDS"/special_*.ogg; do
  b="$(basename "$f")"
  case "$b" in special_pol_*|special_gr_*|special_papz1_*) continue;; esac
  printf "  %8s  %s\n" "$(du -h "$f"|cut -f1)" "$b"
done
echo
echo "-- AMBIENTE amb_*.ogg (NO usado por el juego — BORRAR si aparece) --"
ls "$SOUNDS"/amb_*.ogg 2>/dev/null | while read -r f; do printf "  %8s  %s\n" "$(du -h "$f"|cut -f1)" "$(basename "$f")"; done || echo "  (ninguno — bien)"

if [ "$DO_MP3" = "--mp3" ]; then
  echo; echo "== Convirtiendo voces a mp3 para revisión en: $REVIEW =="
  mkdir -p "$REVIEW"
  for f in "$SOUNDS"/special_*.ogg; do
    out="$REVIEW/$(basename "${f%.ogg}").mp3"
    ffmpeg -y -i "$f" -c:a libmp3lame -b:a 128k "$out" >/dev/null 2>&1 && echo "  ok $(basename "$out")"
  done
  echo "Listo. Escucha los .mp3 en $REVIEW y borra/recorta los que sobren."
fi
