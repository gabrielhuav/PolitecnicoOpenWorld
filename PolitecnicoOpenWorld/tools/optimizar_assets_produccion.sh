#!/usr/bin/env bash
#
# 🗜️  OPTIMIZAR LOS ASSETS QUE VAN A PRODUCCIÓN
#
# Reduce el peso del AAB (Play Store) y del bundle (App Store) SIN tocar la calidad que se nota.
# Es idempotente: si un archivo ya está convertido, lo salta.
#
#   bash tools/optimizar_assets_produccion.sh --dry-run   # solo dice qué haría y cuánto ahorra
#   bash tools/optimizar_assets_produccion.sh             # lo hace
#
# ⚠️ NECESITA `ffmpeg` y `cwebp`. En el Mac de la sesión 07-30 no estaban, por eso las dos
# conversiones grandes quedaron pendientes. En Windows/Linux:
#     choco install ffmpeg webp     ·     apt install ffmpeg webp     ·     brew install ffmpeg webp
#
# ⚠️ **Corre esto y DESPUÉS JUEGA.** Ninguna de estas conversiones la comprueba un test: el audio
# hay que oírlo (sobre todo el bucle de la música) y las imágenes hay que verlas.
#
# MEDIDO 2026-07-30 · AAB de release: 416 MB · tope de Play: 500 MB de módulo base.
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ASSETS="$RAIZ/app/src/main/assets"
DRY=0
[[ "${1:-}" == "--dry-run" ]] && DRY=1

falta() { ! command -v "$1" >/dev/null 2>&1; }
mb() { echo "scale=1; $1/1048576" | bc; }

echo "═══ Assets: $ASSETS"
[[ $DRY -eq 1 ]] && echo "═══ MODO SIMULACIÓN: no se escribe nada"
echo

# ─────────────────────────────────────────────────────────────────────────────────────────────
# 1) PNG → WebP   (el ahorro más grande: 142 archivos, 83,5 MB medidos el 07-30)
# ─────────────────────────────────────────────────────────────────────────────────────────────
# El proyecto YA usa WebP en 1789 archivos; estos 142 son los que se quedaron fuera.
#
# ⚠️ `-q 90` y no menos: son fondos de edificios y mapas que se ven a pantalla completa. Para
#    SPRITES con transparencia usa `-lossless` si notas halos en los bordes del croma.
echo "── 1) PNG → WebP"
if falta cwebp; then
  echo "   ⏭️  falta 'cwebp' — sáltalo o instala 'webp'"
else
  antes=0; despues=0; n=0
  while IFS= read -r -d '' png; do
    webp="${png%.png}.webp"
    [[ -f "$webp" ]] && continue          # ya convertido
    a=$(stat -c%s "$png" 2>/dev/null || stat -f%z "$png")
    if [[ $DRY -eq 1 ]]; then
      cwebp -quiet -q 90 "$png" -o /tmp/_p.webp 2>/dev/null || continue
      d=$(stat -c%s /tmp/_p.webp 2>/dev/null || stat -f%z /tmp/_p.webp)
    else
      cwebp -quiet -q 90 "$png" -o "$webp" 2>/dev/null || continue
      d=$(stat -c%s "$webp" 2>/dev/null || stat -f%z "$webp")
      rm -f "$png"
    fi
    antes=$((antes+a)); despues=$((despues+d)); n=$((n+1))
  done < <(find "$ASSETS" -iname "*.png" -print0)
  echo "   $n archivos · $(mb $antes) MB → $(mb $despues) MB · ahorro $(mb $((antes-despues))) MB"
  [[ $DRY -eq 0 && $n -gt 0 ]] && echo "   ⚠️ AHORA hay que cambiar las rutas '.png' → '.webp' en el código Kotlin."
fi
echo

# ─────────────────────────────────────────────────────────────────────────────────────────────
# 2) Música de fondo → Ogg Vorbis  (SOLO ANDROID)
# ─────────────────────────────────────────────────────────────────────────────────────────────
# POR QUÉ OGG Y NO AAC, que es lo que usa iOS:
#   Estas pistas se reproducen con `isLooping = true` (ver `SoundManager.kt`). AAC mete unas
#   muestras de relleno al principio y al final del archivo, y en un bucle eso se puede oír como
#   un clic en la costura. **Ogg Vorbis empalma sin hueco.** Y encima pesa menos.
#
# ⚠️ Esto vale porque `SoundManager.kt` es de `:app` — iOS NO usa estas pistas. Para audio que SÍ
#    comparten las dos plataformas, el formato es **.m4a**: iOS no puede leer Ogg (`AVAudioPlayer`
#    no trae el códec) y Android sí lee AAC. Ver `13_ASSETS_Y_TAMANO.md` §3.
echo "── 2) Música de fondo (BGM) → Ogg Vorbis · solo Android"
if falta ffmpeg; then
  echo "   ⏭️  falta 'ffmpeg' — sáltalo o instálalo"
else
  antes=0; despues=0; n=0
  for wav in "$ASSETS"/AUDIO/BGM/*.wav; do
    [[ -e "$wav" ]] || continue
    ogg="${wav%.wav}.ogg"
    a=$(stat -c%s "$wav" 2>/dev/null || stat -f%z "$wav")
    if [[ $DRY -eq 1 ]]; then
      ffmpeg -v quiet -y -i "$wav" -c:a libvorbis -q:a 5 /tmp/_a.ogg
      d=$(stat -c%s /tmp/_a.ogg 2>/dev/null || stat -f%z /tmp/_a.ogg)
    else
      ffmpeg -v quiet -y -i "$wav" -c:a libvorbis -q:a 5 "$ogg"
      d=$(stat -c%s "$ogg" 2>/dev/null || stat -f%z "$ogg")
      rm -f "$wav"
    fi
    antes=$((antes+a)); despues=$((despues+d)); n=$((n+1))
  done
  echo "   $n archivos · $(mb $antes) MB → $(mb $despues) MB · ahorro $(mb $((antes-despues))) MB"
  [[ $DRY -eq 0 && $n -gt 0 ]] && echo "   ⚠️ AHORA cambia '.wav' → '.ogg' en SoundManager.kt (3 rutas) Y ESCUCHA EL BUCLE."
fi
echo

echo "═══ Después de esto: ./gradlew :app:bundleRelease y mira el peso."
echo "═══ El CI avisa a 450 MB y falla a 500 (.github/workflows/android-release.yml)."
