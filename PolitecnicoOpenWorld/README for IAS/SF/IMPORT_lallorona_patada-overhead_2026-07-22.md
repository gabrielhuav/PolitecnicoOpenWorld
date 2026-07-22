# Importación de PATADA LARGA + OVERHEAD de La Llorona (2026-07-22)

**Qué:** La Llorona era la única peleadora sin `longKick` ni `overhead` propios → usaba el
placeholder **ALPHA** (silueta del estudiante), que sumaba un **3er atlas** en RAM y era el
sospechoso del **crash P0 por OOM** de sus peleas. El dueño entregó el arte en chroma; se importó
y ahora tiene ambos movimientos. **Con esto ya no dispara el atlas ALPHA** (verificado: 0
movimientos nuevos ausentes) → cierra el crash de raíz y baja el pico de memoria en gama baja.

**Fuente:** `tools/_para_corregir/lallorona_patada-larga_overhead_croma_IMPORTADO.png`
(hoja chroma verde, 2 filas rotuladas: `PATADA LARGA` = 7 poses, `OVERHEAD` = 5 poses).

## Pipeline (reproducible)

Rutas: el GEN canónico de La Llorona (el que coincide con el atlas: 228 → 240 frames) es
`../newSFAssets/GEN_prankedy_senortienda_rey_paparazzi_fullcombat_intermedio/lallorona`
(FUERA del repo). El packer nombra `longKick` de `long-kick-1..7` y `overhead` de `overhead-1..5`
(ver `tools/pack_sf_character.py:70-71`).

1. **Recorte del sheet** en 12 poses chroma individuales (una por archivo), excluyendo los
   rótulos amarillos: detección de blobs de figura con `scipy.ndimage.label` (figura = no-verde y
   no-amarillo), 2 filas por y-centro, orden izquierda→derecha, recorte con padding. Salida:
   `long-kick-1..7.png`, `overhead-1..5.png`.
2. **Importar cada pose** al GEN (centra pies en y=224 y usa `_scale.json` para escala uniforme):
   ```
   python tools/sf_import_fixed_pose.py lallorona <pose>.png <clave>
   # claves: long-kick-1..7, overhead-1..5
   ```
3. **Re-empacar SOLO La Llorona:**
   ```
   python tools/pack_sf_character.py lallorona LaLlorona --gen ../newSFAssets/GEN_prankedy_senortienda_rey_paparazzi_fullcombat_intermedio
   ```
   Regenera `assets/STREETFIGHTER/DATA/lallorona.json` + `IMAGES/LaLlorona.webp`.

## Verificación (hecha)
- `lallorona.json`: 240 frames (antes 228); `longKick` 8 (7+transición), `overhead` 6 (5+transición).
- `git status`: SOLO `lallorona.json` + `LaLlorona.webp` (nada más re-empacado).
- Visual: frames consistentes con su estilo (pies alineados, escala uniforme).
- Falta: **verlo en dispositivo** (la pelea de La Llorona y IA vs IA con ella).

⚠️ Si se re-empaca de nuevo, usar SIEMPRE ese `--gen` (el fullcombat de 240 frames). Otro GEN
(p.ej. `GEN_lallorona_intermedio`, 123 frames) produciría un atlas incompleto.
