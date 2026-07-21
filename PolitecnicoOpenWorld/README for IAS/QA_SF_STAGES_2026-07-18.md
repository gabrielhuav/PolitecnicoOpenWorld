# QA · Fondos/escenarios de "HUELUM VS. GOYA" (feedback del dueño, 2026-07-18)

> Revisión del dueño en dispositivo de los fondos de pelea (atlas `_anim` + estáticos).
> Cada peleadór ya tiene su mapa hogar y funciona (ver `SF_STAGES_MAPS_UNLOCK.md` +
> `SfStageCatalog.homeStage`). Aquí quedan los AJUSTES pendientes. Se corrige de a uno:
> el dueño aprueba y pasa el siguiente.

## Cómo se dibujan los fondos (para saber qué se puede tocar por CÓDIGO)

`StreetFighterScreen.kt` → `drawAnimatedBackground` / `drawFullBackground`: el fondo se escala
para que su ALTO entre en la escena (`SCENE_HEIGHT`=224) y el ancho sobrante panea con la
cámara (parallax). Los peleadores pisan en `STAGE_FLOOR` (218). Si el "piso" de la imagen no
queda cerca de 218, parecen flotar.

**🆕 Encuadre por escenario (2026-07-18):** `SfBgFraming(zoom, offsetY)` + tabla
`SF_BG_FRAMING` (match por SUBSTRING del archivo → cubre las 3 luces). `zoom`>1 amplía el
fondo ANCLÁNDOLO AL PISO (recorta cielo arriba); `offsetY` (unidades de escena, + baja la
imagen) afina. **NO regenera el asset**, solo cambia el dibujo. Es el primer recurso para
"se ven cuadrados / flotando" sin volver a generar el atlas.

## Mapas YA APROBADOS (NO tocar) — día/noche_1/noche_2

ESCOM · Queso IPN · ESIME Azcapotzalco · CECyT 9 · CECyT 2 · CU/Biblioteca UNAM ·
FES Acatlán · UAM Azcapotzalco · Isla de las Muñecas · Mictlán · Campos de Agave.

## Problemas GENERALES (a barrer mapa por mapa)

1. **Falta panorámica:** varios fondos se ven muy cuadrados; deberían leerse más panorámicos.
   Recurso 1 (código): subir `zoom` en `SF_BG_FRAMING`. Recurso 2 (assets): regenerar el
   atlas con recorte 16:9 más ancho (pipeline `tools/build_map_backgrounds.py`).
2. **Peleadores "volando":** en algunos mapas el piso queda alto → parecen en el aire. Bajar
   con `zoom` anclado al piso (+ `offsetY` si hace falta) antes de regenerar nada.

## Ajustes PUNTUALES pendientes

- **Facultad de Medicina (3 variantes) — ✅ CORREGIDO 2026-07-18 (código):** se veía cuadrado
  y los peleadores "en el cielo". Añadido `"facultad_medicina" to SfBgFraming(zoom = 1.35f)`
  en `SF_BG_FRAMING` (cubre día/noche_1/noche_2 por substring). **Verificar en dispositivo** y,
  si el piso no queda exacto, afinar `zoom` y/o `offsetY` (no regenerar el asset todavía).
- **Isla de las Muñecas (3 variantes) — ✅ CORREGIDO 2026-07-19 (código):** los peleadores están sobre el agua → se les da una **sombra más prolongada (1.8x)** y se dibuja una **plataforma de madera** (plank) flotante bajo sus pies en `drawShadow`. Resuelto por código sin alterar el fondo.
- **FES Acatlán — variante de DÍA:** ya está bien, pero se podría **regenerar un video de día
  que NO se mueva** (las imágenes/frames del atlas son muy distintas entre sí y "saltan").
  Es trabajo de asset (regenerar el atlas de día con frames coherentes o 1 frame estático).
  PENDIENTE.
- **🆕 UAM Azcapotzalco y UAM Cuajimalpa (pedido del dueño 2026-07-20):** quiere ACTUALIZARLOS.
  Verificado: los 6 atlas actuales YA vienen de sus videos más recientes en disco
  (`fondo_uam_*_anim.json` → source `UAM Azcapo*.mp4` de 17JUL y `UAM Cuajimala New*.mp4` de
  18JUL, 28 frames ping-pong). **BLOQUEADO en el dueño: faltan videos nuevos.** Cuando los
  suelte, correr `tools/build_map_backgrounds.py` (mismo pipeline; limpiar los stems como en
  QA §MATERIAL NUEVO).

## Subtítulos de frases (se salen de pantalla) — ✅ CORREGIDO 2026-07-18q

Resuelto: subtítulo **multilínea (máx 3)** con word-wrap ~24 chars, fuente `sizeMul` 0.6,
anclado abajo (ver `StreetFighterScreen.kt` bloque "Subtítulo del special" + DISENO §18q).
⚠️ OJO: los subtítulos de FRASES están APAGADOS (`voiceSubtitlesEnabled=false`) hasta
re-transcribir las frases — ver `SF_SPECIAL_VOICES_SFX.md` (caja inicial) y
`tools/sf_audio_audit_report.md` (transcripciones borrador 2026-07-20).

## 🆕 MATERIAL NUEVO PROCESADO (2026-07-18k, Fable) — `nuevoMaterial18JUL/`

El dueño regeneró los videos de 5 mapas. Procesados con el pipeline existente
`tools/build_map_backgrounds.py` (frames→atlas WebP ping-pong, **logo POW sobre la marca
Gemini**, cap ≤2048, thumb, `amb_<slug>.ogg`). Regenerados los **15 atlas** (día/noche_1/noche_2):
**Facultad de Medicina, FES Aragón, Pirámide del Sol, UAM Cuajimalpa, Zócalo**.

- Los nombres fuente traían ruido (`NEW`, `SOLNEW`, typo `Cuajimala`→`Cuajimalpa`, `pramide`→
  `piramide`): se copiaron a un temp con stems limpios para que `to_slug` diera los slugs
  EXACTOS del catálogo. NO se tocaron los otros 11 mapas.
- WebP escrito con `method=4` (en vez de 6) SOLO por velocidad en el sandbox: sigue lossless,
  archivos ~10-15% más grandes (aceptable, <cap). Si se re-corre el pipeline oficial quedará en 6.
- **Framing panorámico** (`SF_BG_FRAMING` en `StreetFighterScreen.kt`): zoom anclado al piso a
  los 5 NUEVOS (FacMed 1.35; Fes Aragón/Pirámide/Cuajimalpa/Zócalo 1.30). Tuneable en dispositivo.
  Los 11 confirmados NO llevan framing (quedan como estaban).

## 🆕 MÚSICA POR PROGRESIÓN de Prankedy (2026-07-18k, Fable)

Los `.mp3` "Prankedy*" del material nuevo son PISTAS DE MÚSICA (no voces). Decisión del dueño:
están ordenadas por dificultad; `Prankedy5Actual`=lobby; el resto escala con el nivel de pelea;
`Prankedy0Actual` era duplicado de `Prankedy6Actual` (descartado). Colocadas en `SOUNDS/`
(re-encode 96 kbps): `prankedy_lobby.mp3` + `prankedy_battle_1..5.mp3` (1..4 = Prankedy1..4,
5 = Prankedy6Actual).

Cableado en `SfTheme` (`lobbyMusic`, `battleMusic`) + selector en `StreetFighterScreen.kt`
(`musicFileForState`): SELECTOR → lobby; PELEA → pista por nivel (arcade = `arcadeStep/arcadeTotal`;
práctica = `cpuDifficulty`; IA vs IA = la más dura). El MediaPlayer se recarga al cambiar de
pista (entrar a pelea / subir de escalón). Fallback a `musicFile` si las listas están vacías.

⚠️ PENDIENTE de afinar con el dueño: dijo "le toca como ~3 canciones a cada pelea". La versión
actual cambia la pista SEGÚN EL NIVEL (una por rango de dificultad, no 3 secuenciales por combate).
Si quiere rotación de varias pistas DENTRO de un mismo combate, es un refinamiento aparte
(playlist secuencial con listener de fin de pista).

## 🆕 Parallax vertical de SALTO (2026-07-18m, Fable)

`drawAnimatedBackground`/`drawFullBackground` reciben `jumpFrac` (0..1, según la altura del
peleadór más alto; apex ≈ 90 px). En mapas CON zoom (headroom de cielo recortado) el fondo BAJA
hasta `headroom` al brincar → se ve "más arriba" del mapa. En mapas SIN zoom (los 11 confirmados)
`headroom=0` → sin efecto. Sólo aplica a los mapas con `SfBgFraming` (los nuevos). Tuneable con
el divisor `/90f` en `drawScene` si se quiere más/menos recorrido.

## Panorámico en TODOS los mapas — ✅ APLICADO (2026-07-18ñ, por pedido del dueño)

Los 16 mapas ya están en `SF_BG_FRAMING` con `zoom` (Facultad Medicina 1.35; los otros 15 a
1.30), así que TODOS son panorámicos + con parallax de salto. **Queda pendiente el AFINADO por
mapa en dispositivo:** el zoom ideal depende de dónde cae el piso en cada atlas; si alguno se
recorta de más o queda con peleadores flotando, ajustar SU zoom/offsetY (una línea por mapa,
ver `PROMPT_panoramico_todos_los_mapas.md`). El sistema es tuneable sin regenerar assets.

## Orden de trabajo (acordado con el dueño)

1. ✅ Facultad de Medicina (código de framing) + regeneración de los 5 mapas nuevos + música.
2. El dueño pasa las demás correcciones una por una tras aprobar cada fix (sombra Isla Muñecas,
   video día FES Acatlán, subtítulos multilínea).
