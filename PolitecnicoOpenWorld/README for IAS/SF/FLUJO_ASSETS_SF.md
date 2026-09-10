# FLUJO DE TRABAJO · Assets de pelea de TITULACIÓN POR COMBATE (2026-07-21)

> Receta COMPLETA y repetible: de las imágenes que entrega ChatGPT/Sol 5.6 hasta el
> personaje jugando en el APK. Sustituye a improvisar rutas y comandos cada vez.
> Guías relacionadas: `GUIA_regeneracion_sprites_croma.md` (hojas 01-19, arte desde cero) y
> `PROMPT_SOL56_TANDAS_NUEVAS.md` (el prompt de las hojas 20-29).

## 0. Mapa de carpetas (dónde vive cada cosa)

| Etapa | Ruta |
|---|---|
| Hojas ORIGINALES por personaje (01-29) | `newSFAssets/<Personaje>/<Personaje>_NN_Slug.png` |
| Descargas SIN identificar (tandas nuevas) | una subcarpeta por personaje en cualquier lado; el paso 1 las renombra y consolida |
| Recortes intermedios (256², pies en 128,224) | `newSFAssets/GEN_<...>_intermedio/<char>/` |
| Atlas + JSON FINALES (lo que usa el juego) | `app/src/main/assets/STREETFIGHTER/IMAGES/<Titulo>.png` + `DATA/<char>.json` |
| QA visual | `tools/_contact_sheets/` |

⚠️ El GEN **nunca** debe quedar dentro de `app/src/main/assets/` al terminar (viaja al APK).
Por eso todas las herramientas aceptan `--gen` apuntando a la carpeta intermedia externa.

## 1. Identificar y renombrar las hojas nuevas

ChatGPT las entrega con nombres inútiles (`ChatGPT Image ... (7).png`) pero **en el orden del
prompt**: la descarga 1..10 = hojas 20..29. La herramienta recorta los títulos amarillos de
cada hoja para VERIFICARLO a ojo (no lo asume en silencio):

```bash
# a) revisar: genera tools/_contact_sheets/titulos_<personaje>.png y lista incompletas
python tools/sf_identify_new_sheets.py <raiz_descargas> --titles

# b) renombrar + consolidar junto a las hojas 01-19
python tools/sf_identify_new_sheets.py <raiz_descargas> --apply --move-to newSFAssets

# c) carpeta INCOMPLETA: mapeo explícito tras mirar su tira
python tools/sf_identify_new_sheets.py <raiz>/LaLlorona --apply --move-to newSFAssets \
    --map 20,21,22,23,24,25,27,28,29
```

- Los nombres de carpeta que no coinciden con los canónicos se resuelven en
  `FOLDER_ALIASES` (p. ej. `Granadera Mujer` → `PoliciaGranaderoFemeninoCDMX`).
- Cada movimiento queda en `tools/_contact_sheets/rename_manifest.json` **para deshacerlo**.
- ⚠️ NO se usa template-matching de los títulos: la fuente y el tamaño del rótulo cambian
  entre hojas y personajes (una hoja llegó con "TAUNT" en fuente diminuta y otra rotuló
  "CROUCH ANTIAIR" en vez de "CROUCH HEAVY PUNCH"). La verificación es VISUAL.

## 2. Recortar y empaquetar

La escala de cada personaje la fija su hoja 01 (`_scale.json` en el GEN), así que las hojas
nuevas se calibran contra ella y **no hay que reprocesar 01-19**:

```bash
python tools/slice_new_sheets_batch.py --gen <raiz_GEN>
# opciones: --only escomboy,prankedy   --no-pack (solo recortar)
```

Hace, por personaje: recorta las hojas 20-29 (`slice_sf_chroma_sheets.py`) y re-empaqueta
atlas+JSON (`pack_sf_character.py`). El mapeo carpeta→clave vive en `CHAR_KEYS`.

## 3. QA visual (antes de dar nada por bueno)

```bash
python tools/sf_contact_sheet.py <char> mid     # 1 frame por animación, etiquetado
```
Sale en `tools/_contact_sheets/<char>_contact_mid.png`. Sirve para cazar de un vistazo poses
volteadas, fusionadas o de otro personaje. **Este QA encontró dos bugs reales** (ver §5).

## 4. Verificación de código

```bash
./gradlew compileDebugKotlin        # el motor no debe romperse
./gradlew testDebugUnitTest         # tests en verde
detekt-cli --config config/detekt/detekt.yml --input <archivos tocados>
```

## 5. Trampas conocidas (aprendidas a golpes)

1. **Hojas que NO son animación del personaje sino un GUION.** Los `bonusPower1-6` de La
   Presidenta traen 1 cuadro de ella + 3 SOLO del proyectil + 1 de ella. Animar los 5 la hacía
   DESAPARECER 3 cuadros. Se declaran en `BONUS_PROJECTILE_POWERS` (pack_sf_character.py):
   la animación usa solo sus poses y los cuadros 2-4 se dibujan como proyectil propio del
   poder (`SfFireball.bonusPower`).
2. **Hojas con rejilla propia.** La hoja 12 de La Llorona es 4×3 (fila 3 = los efectos) y el
   slicer estándar metió 2 cuadros de su CUERPO como `proj-*` → "se lanzaba a sí misma".
   Arreglado con `tools/fix_llorona_projectile.py`, que detecta los efectos **por silueta**
   (la rejilla fija fallaba: los efectos están agrupados, no repartidos en columnas iguales).
3. **Conteo de cuadros ≠ rótulo.** El slicer tolera ±1 cuadro repitiendo el vecino central;
   avisa con "AVISO: conteo distinto". Faltantes mayores abortan a propósito.
4. **Poses que NO deben normalizarse a una altura fija** (barrida horizontal, ser lanzado,
   levantarse, super art con efectos): van con `None` en `SF_POSE_TARGET_H`.
5. **Los títulos amarillos son parte del PNG** que genera ChatGPT — no son los assets de
   fuente del juego (esos son `IMAGES/sf_hud_pow.png`).

## 6. Cuando FALTA una hoja

No bloquea: el motor tiene **placeholder ALPHA**. Si a un peleador le falta la animación de
un movimiento nuevo, se dibuja con la hoja del estudiante de su mismo género (ESCOMBOY /
ESCOMGIRL) en **silueta negra pixelada** con el rótulo **"ALPHA"** encima (fuente del HUD),
y el movimiento **se puede jugar igual**. Ver `alphaFallbackId`/`usesAlphaFallback` en el VM
y `AlphaFallback` en la Screen.

Hoy solo falta: **`LaLlorona_26_PatadaLarga_Overhead`** (sus `longKick`/`overhead` salen ALPHA).
