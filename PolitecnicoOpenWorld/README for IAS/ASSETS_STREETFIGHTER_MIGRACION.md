# Migración de assets de "HUELUM VS. GOYA" (modo pelea 1v1: de SF clásico → assets propios de POW)

> **Objetivo:** el modo usa HOY los assets del clon Street Fighter (SOLO en Modo Desarrollador,
> nunca en release público — riesgo de copyright). El motor ya está SEPARADO de los assets:
> reemplazarlos NO toca lógica. Este doc dice exactamente QUÉ generar, en QUÉ formato y CÓMO
> cablearlo. Personaje objetivo de ejemplo: **Prankedy**.

> **⚠️ ACTUALIZACIÓN 2026-07-15 — el modo ya es PÚBLICO → estado del riesgo:**
> **✅ RYU/KEN RESUELTO (2026-07-15e):** sus assets se movieron al source set
> **`app/src/debug/assets/`** → el bundle de Play Store (release) YA NO los incluye; en builds
> de cable (debug) siguen intactos y jugables (BuildConfig.DEBUG + Modo Dev). Los compartidos
> usan `DATA/sf_template.json` (main) como template de cajas/timings. Ver 07/09 §12.
> **Ya propio:** música ("Persecución" de Prankedy), 6 fondos IPN/UNAM y los 12 peleadores POW
> (11 compartidos EN RUNTIME desde los sets del mundo + Prankedy empaquetado).
> **SIGUE VIAJANDO del clon en release (pendiente de esta guía):** ver el checklist definitivo
> de abajo. Nota: `sf_template.json` conserva NÚMEROS (timings/cajas) derivados del clon —
> riesgo bajo (datos, no arte), sustituible cuando exista un template propio.

## ✅ CHECKLIST DEFINITIVO DE COPYRIGHT (2026-07-16) — qué falta cambiar y cómo

**Decisión del dueño: REGENERAR TODO con el generador de imágenes de ChatGPT Plus** (prompts
listos en `GUIA_generacion_assets_SF.md`, "modo simple"). Flujo por pieza: el dueño genera el
PNG con ChatGPT → lo deja en la raíz del repo → la IA lo rebana/empaqueta/cablea (los slicers
de `tools/` ya existen). Estado por archivo, en ORDEN DE PRIORIDAD:

| # | Archivo del clon (en release) | Qué es | Sustituir por | Estado |
|---|---|---|---|---|
| 1 | `decals.png` | ⚡ **LOS PODERES** (fireball del hadouken: 2 frames de vuelo + 3 de impacto) y los **splashes de golpe** (2 filas × 4 frames por fuerza) | Proyectil PROPIO de POW (p. ej. bola de energía "HUELUM") + splashes propios; Prankedy YA tiene su proyectil propio (tanque de gas → confeti), sirve de referencia de formato | 🔴 PENDIENTE (prioridad 1 del dueño) |
| 2 | `hud.png` | Barras de vida, dígitos del timer, icono KO y la **FUENTE arcade A-Z/0-9** (nombres, "X WINS", "RONDA N/PELEA") | HUD propio POR PIEZAS (GUIA §HUD); la fuente es lo más usado — sin ella no hay tags ni banners | 🔴 PENDIENTE |
| 3 | 11 sonidos `.ogg` (`SOUNDS/`) | Golpes light/med/heavy, impactos, land y hadouken | Sonidos propios o CC0 (generador de audio / freesound CC0); mismos NOMBRES de archivo = cero cambios de código | 🔴 PENDIENTE |
| 4 | `kenstage.png` | Muelle de Ken (solo FALLBACK si falla un fondo) | Con 6 fondos propios ya casi no se ve: generar el "escenario ESCOM por capas" (GUIA) o BORRARLO y quitar el fallback clásico del código | 🟡 PENDIENTE (baja exposición) |
| 5 | `shadow.png` | Óvalo de sombra bajo el peleador | Trivial: óvalo propio (hasta dibujable por código) | 🟡 PENDIENTE |
| 6 | `sf_template.json` | NÚMEROS de timings/cajas (derivados del JS del clon) | Template propio cuando se re-tunee el gameplay | 🟢 Riesgo bajo (datos) |
| — | `Ryu.png`/`Ken.png`/`ryu.json`/`ken.json`/`kens-theme.ogg` | Peleadores del clon | — | ✅ FUERA de release (source set debug, 2026-07-15e) |
| — | `winnerText.png` | Letrero viejo de ganador | — | ✅ BORRADO (2026-07-16) |
| — | Música, 6 fondos, 12 peleadores POW | — | — | ✅ YA PROPIOS |

**Fase 2 (calidad, no copyright):** regenerar con ChatGPT las poses reales de los 10 peleadores
ALPHA (hoja de referencia por personaje → `slice_sf_reference_sheet.py` → empaquetar como
Prankedy) — sustituye las poses aproximadas y permite quitar `hurtScale`.

## 1. Dónde está la separación (no tocar lógica al migrar)

| Capa | Archivo | Qué sabe |
|---|---|---|
| Motor (física/estados/colisiones) | `StreetFighterViewModel.kt` + `SfModels.kt` | NADA de assets: lee frames/cajas del JSON y emite claves de sonido |
| Frame data por personaje | `assets/STREETFIGHTER/DATA/{ryu,ken}.json` | recortes, orígenes, cajas y animaciones |
| Personajes | `SfFighterId` (SfModels.kt) | ruta del sprite sheet + ruta del JSON |
| Tema visual/sonoro | `data/SfTheme.kt` (`SF_CLASSIC_THEME`) | escenario, HUD, sombra, splashes, proyectil, sonidos, música |
| View | `ui/StreetFighterScreen.kt` | solo dibuja lo que el tema/JSON le dicen |

**Migrar = (a) nuevo sprite sheet + JSON por personaje → cambiar `SfFighterId`; (b) nuevo
`SfTheme` con los demás assets → cambiar la constante en la View. Cero lógica.**

## 2. Inventario de assets actuales (qué hay que sustituir)

`app/src/main/assets/STREETFIGHTER/`
- `IMAGES/Ryu.png`, `Ken.png` — sprite sheets de personaje (PNG transparente, poses sueltas, pixel art)
- `IMAGES/kenstage.png` — escenario (fondo lejano 768×176, elemento medio "barco", piso 896×56+16, decoración)
- `IMAGES/hud.png` — HUD (barra de vida 145×11, dígitos 14×16 y 10×10, tags de nombre, icono KO 32×14)
- `IMAGES/shadow.png` — sombra elíptica 43×9
- `IMAGES/decals.png` — splashes de impacto (3 fuerzas × 4 frames × 2 colores)
- `IMAGES/winnerText.png` — "X WINS" (1 fila de 70×9 por personaje, separadas 11 px)
- `SOUNDS/*.ogg` — 3 golpes al aire, 6 impactos (puño/patada × fuerza), aterrizaje, especial, música
- `DATA/{ryu,ken}.json` — frame data (formato abajo)

## 3. Formato del JSON de personaje (el contrato del motor)

```json
{
  "frames": {
    "idle-1": { "src": [x,y,w,h], "origin": [ox,oy], "push": [x,y,w,h],
                 "hurt": [[cabeza],[cuerpo],[piernas]], "hit": [x,y,w,h] },
    ...
  },
  "animations": { "idle": [["idle-1",4],["idle-2",4],...], ... }
}
```
- `src` = recorte en el PNG. `origin` = píxel del ancla (los PIES, centro) dentro del recorte.
- Cajas relativas al ancla, MIRANDO A LA DERECHA (el motor espeja). `hit` solo en frames que pegan.
- `animations`: delays en frames de 60 fps (4 ≈ 67 ms); `0` = congelar, `-1` = fin/transición.
- Claves de animación obligatorias (30): las de `SfFighterState.jsKey`.

## 4. Poses que necesita UN personaje (77 sprites; lista para generar)

Mirando SIEMPRE a la derecha, pies al piso, sobre fondo transparente:

| Animación | Sprites | Notas |
|---|---|---|
| idle | idle-1..4 | guardia en reposo (ciclo) |
| caminar adelante / atrás | forwards-1..6, backwards-1..6 | ciclos de 6 |
| salto vertical | jump-up-1..6 | ascenso→caída |
| salto con giro | jump-roll-1..7 | adelante (atrás lo reusa invertido) |
| impulso/aterrizaje | jump-start/land (1) | flexión de rodillas |
| agacharse | crouch-1..3 | de pie→en cuclillas |
| voltearse | idle-turn-1..3, crouch-turn-1..3 | |
| puño ligero | light-punch-1..2 | jab |
| puño medio/fuerte | med-punch-1..3, heavy-punch-1 | fuerte reusa med-punch |
| patada ligera/media | light-kick-1..2, med-kick-1 | (arrancan desde med-punch-1) |
| patada fuerte | heavy-kick-1..5 | roundhouse completo |
| golpe en cara | hit-face-1..4 | reacción cabeza |
| golpe en estómago | hit-stomach-1..4 | reacción cuerpo |
| aturdido | stun-3 (1) | |
| caída KO | fall-1..5 | tumbado al final |
| victoria | victory-1..4 | celebración |
| especial (proyectil) | special-1..4 | pose de lanzamiento (broma/granada de confeti para Prankedy) |
| **proyectil** | 2 frames volando + 3 de impacto | hoy sale de Ken.png; en el tema POW irá en el sheet del personaje o aparte |

## 5. Pipeline recomendado (el que MENOS trabajo manual requiere)

Para añadir a **Prankedy** (o cualquier otro personaje) al juego usando este pipeline automático:

1. **Generación de Cuadros Individuales:**
   Los cuadros de referencia se encuentran en `app/src/main/assets/SPRITES/NPC/PrankedyPlayable/` (estructurados en `Idle`, `Walk`, `Run` y `Special`). 
   Mediante un script se extraen, se ajusta su escala a una altura homogénea de ~100 px, y se colocan centrados en un lienzo de `256x256` con los pies tocando exactamente `y=224` (para animaciones estáticas/caminar) o con su centro de gravedad en el centro para rotaciones en el aire (salto con giro).
   Los frames individuales resultantes se guardan en:
   `app/src/main/assets/STREETFIGHTER/GEN/prankedy/`
   
2. **Empaquetado automático con `tools/pack_sf_character.py`:**
   Este script toma los cuadros de `GEN/prankedy/` y arma automáticamente el sprite sheet final y su JSON compatible con el motor.
   Ejecuta:
   ```bash
   python tools/pack_sf_character.py prankedy Prankedy
   ```
   Esto produce:
   * **Sprite sheet:** `app/src/main/assets/STREETFIGHTER/IMAGES/Prankedy.png` (cuadrícula regular de 10 columnas con todas las poses del personaje y proyectiles).
   * **Frame data JSON:** `app/src/main/assets/STREETFIGHTER/DATA/prankedy.json` (reutiliza los tiempos de animación y las cajas físicas de colisión `push`, `hurt` y `hit` de `ryu.json`).

3. **Registro en el Código (SfModels.kt):**
   Añade el nuevo ID de personaje en la enumeración `SfFighterId` apuntando a sus respectivos assets empaquetados:
   ```kotlin
   PRANKEDY("STREETFIGHTER/DATA/prankedy.json", "STREETFIGHTER/IMAGES/Prankedy.png")
   ```
   Y configúralo en `StreetFighterState` o actívalo como personaje jugable.

4. **Escenario/HUD/sonidos:** crea `POW_THEME` (copia de `SF_CLASSIC_THEME` con otras rutas/recortes) y cámbialo en `StreetFighterScreen`. Sugerencia de escenario POW: la explanada de ESCOM.

5. **Sonidos:** 12 .ogg cortos (<1 s salvo música). Se pueden grabar/generar libres; mismos nombres de clave o cambia `soundKeys` en el tema.

## 6. Prompt para QWEN (generación por imagen; UNA animación por petición)

> Pixel art sprite sheet, estilo arcade 16-bit de juego de peleas 2D. Personaje: **Prankedy,
> [DESCRIPCIÓN OFICIAL DEL PERSONAJE POW: p. ej. bromista con sudadera guinda del IPN, sonrisa
> traviesa, guantes, tenis]**. Genera la animación **[NOMBRE: p. ej. "caminar hacia adelante,
> ciclo de 6 cuadros"]** en **[N] cuadros**, dispuestos en una fila horizontal, cada cuadro en una
> celda EXACTA de **256×256 px**, fondo TRANSPARENTE. El personaje mira SIEMPRE a la DERECHA, mide
> ~100 px de alto y sus PIES tocan siempre el píxel y=224 centrados en x=128 de su celda. Paleta
> consistente entre cuadros, contorno oscuro de 1 px, sin sombra en el piso (la pone el motor),
> sin texto ni marcas. Vista lateral de juego de peleas.

Repite por animación (tabla §4). Para hacerlos A MANO con ChatGPT: mismo prompt pero pide 1 cuadro
por imagen y descríbele la pose exacta ("jab con el brazo izquierdo extendido a la altura de la
cara…"); luego recórtalos al lienzo 256×256 con los pies en (128,224) — GIMP/Aseprite.

## 7. Estado (2026-07-10)
- Motor y tema separados ✅. Controles = joystick + diamante Xbox de POW ✅
  (X puño ligero · Y medio · B fuerte · A patada con fuerza según joystick).
- **🆕 PRANKEDY ES EL JUGABLE (P1)** ✅: pipeline §5 ejecutado (80 frames en `GEN/prankedy/`,
  sheet `IMAGES/Prankedy.png` 2560×2304 en rejilla 10×9 de 256², `DATA/prankedy.json` con las 30
  animaciones y cajas heredadas de ryu). `SfFighterId.PRANKEDY` + `StreetFighterState.player`.
  El VM/View cargan frame data y sheets POR IDENTIDAD (cache perezoso; ya no hay ryu/ken fijos).
  Su especial usa sus frames **`proj-*` propios** (tanque de gas + confeti): el render los toma
  del JSON del DUEÑO del proyectil si existen, si no cae al fireball del tema. `winnerRows` es
  por personaje: si gana Prankedy no se dibuja el "RYU WINS" (texto propio pendiente en POW_THEME).
  Referencia de estilo para MÁS assets: **`sprites Prankedy.png`** (raíz del repo externo).
- **🆕 SELECTOR + ROSTER DE 7 (2026-07-10b):** selección de personaje pre-pelea; jugables Ryu,
  Ken, Prankedy, El Señor de la Tienda, Paparazzi 1, Paparazzi 5 y Rey Grupero. Los 4 nuevos se
  generaron SIN arte nuevo con **`tools/gen_sf_frames_from_npc.py`** (77 poses aproximadas desde
  su set NPC Idle/Walk/Run/Special: walk→caminatas, run→saltos/volteretas rotadas, special→golpes
  /victoria, idle inclinado/aplastado→reacciones/caídas/agacharse) + `pack_sf_character.py`.
  Por eso llevan **badge ALPHA**: sus poses se irán reemplazando con arte dedicado (prompt §6).
- **🆕 ARTE DEDICADO INTEGRADO (2026-07-10c):** los 5 peleadores POW ya usan HOJAS DE REFERENCIA
  reales (raíz del repo externo: `sprites Prankedy.png`, `paparazzi1/5.png`, `senortienda.png`,
  `reygrupero.png` + `extras senortienda/reygrupero.png` con salto/agacharse/daño/derribo).
  Rebanadores: `tools/slice_sf_reference_sheet.py` (hoja plantilla, escala POR SECCIÓN),
  `tools/slice_sf_custom_sheets.py` (mapeo por personaje: cajas por sección, filtro de blobs
  de 2 figuras) y `tools/slice_sf_extras.py` (hojas extra que SOBREESCRIBEN las poses aproximadas;
  merge de blobs partidos con alfa por unión de ids). Todas las poses de pie ≈100 px, pies en
  (128,224); agachado ~72-80 px, tendido ~40 px.
- Falta: escenario/HUD/sonidos (`POW_THEME`), afinar cajas si el alcance se siente raro (usan
  las de ryu), tags de nombre + filas de winner para los personajes POW, i18n in-game.
  Nota: la hoja de Rey Grupero NO es pixel art (estilo semi-realista); regenerar si desentona.
