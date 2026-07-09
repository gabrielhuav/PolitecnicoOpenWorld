# Migración de assets del modo PELEA 1v1 (de SF clásico → assets propios de POW)

> **Objetivo:** el modo usa HOY los assets del clon Street Fighter (SOLO en Modo Desarrollador,
> nunca en release público — riesgo de copyright). El motor ya está SEPARADO de los assets:
> reemplazarlos NO toca lógica. Este doc dice exactamente QUÉ generar, en QUÉ formato y CÓMO
> cablearlo. Personaje objetivo de ejemplo: **Prankedy**.

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

1. **Genera cada pose en un LIENZO FIJO de 256×256** (transparente), personaje ocupando ~60–110 px
   de alto como Ryu, **pies siempre en el mismo píxel (128, 224)**, mirando a la DERECHA.
   Un PNG por pose, nombrado con la clave (`idle-1.png`, `forwards-3.png`, …).
2. **Empaqueta** con un script (hermano de `tools/convert_streetfighter_frames.py`): acomoda los
   256×256 en una rejilla → `src` = celda, `origin` = `[128, 224]` fijo → escribe `prankedy.json`
   **REUSANDO de `ryu.json` las `animations` (timings) y las cajas `push/hurt/hit`** (funcionan si
   la silueta es de proporciones similares; se afinan después frame por frame si hace falta).
3. Añade el personaje: en `SfFighterId` → `PRANKEDY("STREETFIGHTER/DATA/prankedy.json",
   "STREETFIGHTER/IMAGES/Prankedy.png")` y úsalo en `StreetFighterState`.
4. Escenario/HUD/sonidos: crea `POW_THEME` (copia de `SF_CLASSIC_THEME` con otras rutas/recortes)
   y cámbialo en `StreetFighterScreen`. Sugerencia de escenario POW: la explanada de ESCOM.
5. Sonidos: 12 .ogg cortos (<1 s salvo música). Se pueden grabar/generar libres; mismos nombres
   de clave o cambia `soundKeys` en el tema.

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

## 7. Estado (2026-07-09)
- Motor y tema separados ✅. Controles = joystick + diamante Xbox de POW ✅
  (X puño ligero · Y medio · B fuerte · A patada con fuerza según joystick).
- Falta: generar assets POW (este doc), `POW_THEME`, packer `tools/pack_sf_character.py`, i18n in-game.
