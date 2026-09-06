# ✅ RESULTADO — Android jugado tras la fase 5 (paso 6)

**Escrito el 2026-08-21 desde Windows (Opus 5).** Rama: `ios/verificacion-mac-1.0.0.17`.
Responde a `PROMPT_WINDOWS_verificar_android_fase5.md`. Commits probados: `bf2d6797` (QA 1/9),
`f2f62064` (QA 2/9), `65b09cea` (QA 3/9).

> ## Veredicto: VERDE
>
> **Ninguna regresión atribuible a esta tanda.** Se jugó el mundo abierto en el emulador
> (AVD `Nexus`, API 35) **en español y en inglés**. El fallo que se venía a arreglar —el aviso
> de la horda que no se auto-limpiaba en inglés— **está arreglado y medido**.
>
> Quedan **4 puntos sin jugar** (§7 Prankedy, §8 carjack, §1 coleccionable, §18 Modo Historia).
> Tres se cerraron por lectura de código; el carjack **sigue abierto**. Detalle en §7.

---

## 0. Build y tests

```bash
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :shared:testAndroidHostTest
```

`BUILD SUCCESSFUL`. Leído de los XML (`*/build/test-results/**/*.xml`):
**125 tests en `:app` + 213 en `:shared` = 338, 0 failures, 0 errors.**

⚠️ **Corrección al prompt:** el comando que traía (`:shared:testDebugUnitTest`) **no existe** —
falla a los 28 s con `task 'testDebugUnitTest' not found in project ':shared'`. En el módulo KMP
la tarea se llama **`testAndroidHostTest`** (o `allTests` para los tres targets). Ya está
corregido en `PROMPT_WINDOWS_verificar_android_fase5.md`.

---

## 1. Los 14 textos del mundo (§2.1)

Verificados **en runtime y en los dos idiomas**:

| Texto | Español | Inglés |
|---|---|---|
| `wm_press_x_enter` (puerta ESCOM) | `PRESIONA X PARA ENTRAR` ✅ | `PRESS X TO ENTER` ✅ |
| `wm_prompt_metro` (lleva `%1$s`) | `PRESIONA X PARA ENTRAR A ESTACIÓN POLITÉCNICO` ✅ | `PRESS X TO ENTER STATION POLITÉCNICO` ✅ |
| `wm_horde_approaching` | ✅ | ✅ |
| `toast_car_injected` | — | `🚗 Car injected into MICRO_LANDMARK` ✅ |

**El argumento `%1$s` SÍ llega**: el nombre de la estación aparece en ambos idiomas. Todos los
avisos se auto-limpian a los ~3 s. Menús, HUD, diálogo de teletransporte, pantalla de carga y
tips: todo cambia de idioma correctamente.

🔴 **`wm_press_x_activate_zombie` y `wm_press_x_deactivate_zombie` son INALCANZABLES jugando.**
`spawnEscomItems` (`WorldMapEscomItems.kt`) borra la mano del apocalipsis a propósito y no la
spawnea nunca; el apocalipsis se activa desde Opciones. Son 2 de los 14 y hoy son código muerto:
o se quitan, o se documenta que solo existen por paridad con iOS.

---

## 2. 🐛 El fallo que se arregló (§2.2) — CONFIRMADO

Con el juego **en inglés**, muestreando el aviso durante ~6 min (60 sondas):
`🧟 A HORDE IS APPROACHING!` **aparece y desaparece solo**, una y otra vez, sin quedarse pegado.
En español, igual. El arreglo sirve.

---

## 3. Sprites (§2.4) — sin diferencias

- **Coches** ✅ decenas de coches en colores muy variados; **intermitentes ámbar y los brillos
  especulares del techo/capó SIN teñir** (comprobado ampliando a escala de píxel).
- **Peatones** ✅ camisas de colores distintos, **piel y cara sin teñir**, pelo puesto y de su
  color, y con la animación de caminar.
- **Patrullas** ✅ (por código, no vistas jugando): el único que llama a `tintarCarroceria` en
  Android es `VehicleSpriteManager.getTintedCarNpc`, y el renderer web deriva las patrullas a
  `PoliceSpriteManager.getPoliceCar`, que **no tinta nada**. No pueden salir de color.

Además se comparó constante por constante el tintado viejo contra el movido: **idénticas**
(saturación 0.15; pesos 0.299/0.587/0.114; franja 80–245 con rampas en 130 y 235; referencia 165;
y 50 / 15 / 160 / 45–130 en el de personaje).

---

## 4. Assets por `PowAssets` (§2.5)

| Asset | Estado |
|---|---|
| `CONFIG/exterior_collisions.json` | ✅ **probado jugando**: el jugador choca contra la barda y no la cruza (medido con diff de fotogramas: el mapa deja de moverse al oeste y vuelve a moverse al norte) |
| `CONFIG/navgraphs/escom_navgraph.json` | ✅ **probado jugando**: Modo Diseñador → SPAWN CAR sale con el toast de ÉXITO, no con `toast_error_escom_navgraph` |
| `CONFIG/default_landmarks.json` | 🟡 **no ejercitado en runtime**: `loadLandmarks` solo lee el JSON **si la tabla Room está vacía**, y los emuladores usados ya la tenían sembrada. Lo que sí se vio: los landmarks existen y se pintan (edificio ESCOM con `ROTATION: 25°` y `0.99x`, exactamente los valores del JSON), y el diálogo de teletransporte lista ESCOM y Plaza Torres. Para ejercitar la lectura hay que **borrar los datos de la app** y buscar en el log `Mapa sembrado con éxito desde default_landmarks.json` |

Cero excepciones de `PowAssets` en logcat en ~3 h de sesión.

---

## 5. Caché de calles (§2.6) — idéntica a iOS

```
MISS (no existe): 975_-4958
GUARDADO OK: 975_-4958 → 2512 ways, 11946 nodos
HIT: 975_-4958 → 2512 ways (0h de antigüedad)
HIT: 975_-4958 → 2512 ways (1h de antigüedad)   ← ya con el proceso reiniciado
```

Los mismos 2512 ways que se midieron en el simulador.

---

## 6. Que no se rompió lo de antes (§2.7)

- **Población de NPCs**: `maxTotalNpcs=39`, el mismo número que iOS. ✅
- **TITULACIÓN POR COMBATE**: pelea completa (selección de luchador → dificultad → combate con barras,
  contador de combos y temporizador → pantalla de fin). ✅

---

## 7. Lo que NO se jugó, y por qué

| Punto | Por qué | Cómo quedó |
|---|---|---|
| §2.3-7 **Prankedy cayó** | Mataba al jugador antes de caer (80 HP y es hostil) | 🟡 Cerrado por código: `showPrankedyHireDialog = false` va **fuera** del `launch` y **antes** del texto (`WorldMapPrankedy.kt`), que es justo lo que pedía el prompt. El aviso usa el mismo `interactionPrompt` probado 4 veces |
| §2.3-8 **carjack** | Pide un NPC `PERSON` con aggro pegado **y** el coche a menos del 25 % de `MAX_SPEED`; no se dio | 🔴 **SIGUE ABIERTO.** Es el único texto que se precarga (`textoCarjack` en el `init`) y el único cuyo modo de fallo (salir vacío) no se descarta leyendo |
| §2.1-1 **`PRESS X TO PICKUP`** | Los coleccionables spawnean a 300–600 m aleatorios | 🟡 Cerrado por código: misma llamada `getString(Res.string.…)` y mismo punto de publicación que la puerta ESCOM, probada en ES e IN |
| §2.7-18 **Modo Historia** | No se entró | 🔴 Sigue abierto (era "por encima") |
| §2.1-3 **mano zombi** | **Imposible**: la función se eliminó del juego | ⚫ No aplica |

---

## 8. Dos cosas que se vieron y que **no** son de esta tanda

Las dos se comprobaron contra `main` antes de anotarlas.

1. **`🚨 ¡Un infectado atacó a alguien en la calle!` sale siempre en español** aunque el juego esté
   en inglés, y **no se auto-limpia** (se quedó fijo más de 5 min hasta que otro aviso lo pisó). Es
   un literal a pelo en `WorldMapDynamicEvents.kt:237`, **idéntico en `main`**. No es regresión,
   pero es exactamente el mismo bug que se acaba de arreglar en la horda y no está entre los 14.
2. **`🚗`/`🧍` emoji en vez de sprite justo después de un teletransporte.** Es el respaldo
   documentado del HTML ("FIX NPC invisible"). Una vez duró ~5 min con los coches del
   estacionamiento; las demás veces se resolvió en segundos. `WorldMapScreenWeb.kt` y
   `WorldMapLeafletHtml.kt` son **byte a byte iguales a `main`**, y el emulador iba con
   `tile memory limits exceeded`.

Y una tercera, sin veredicto: el coche robado dentro del estacionamiento se quedó a **0 km/h**
empujando el joystick. Encaja con la aduana de choque contra los coches aparcados
(`isCollisionDetected` → `vehicleSpeed = 0` en cada tick), no se dio por fallo.

---

## 9. Recetas para quien siga (medidas; cuestan tiempo si se redescubren)

- **`interactionPrompt` vive solo ~3 s.** Sondear con `uiautomator dump` NO sirve (cada volcado
  tarda ~5 s y te pierdes la ventana). Lo que funciona: **ráfaga de `adb exec-out screencap -p`**,
  una por acción, y montar las tiras. El aviso solo se dispara cuando el objetivo cercano
  **CAMBIA** → hay que **salir del radio y volver a entrar** (metro 30 m, puerta ESCOM 20 m,
  coleccionables 15 m).
- **Llegar a cualquier coordenada sin caminar:** `adb emu geo fix <lon> <lat>` (repetir 3 veces
  **con la app ya abierta**; si se fija antes de lanzarla, el primer TP se va a Mountain View) y
  luego *Opciones → Teletransportarse → Ir a tu Ubicación (GPS)*.
- **El panel de sliders (arriba a la derecha) es un TOGGLE** y con él abierto los botones A/B/X/Y
  se corren a la izquierda y el joystick deja de recibir los swipes (el mapa se panea). Confirmar
  con screenshot antes de cada ráfaga de movimiento.
- **En Modo Diseñador desaparece el joystick** (los taps van al mapa).
- **El AVD `Nexus` no aguanta esta app mucho rato**: el `system_server` se reinició 2 veces en
  ~2 h (WebView + teselas). No confundirlo con un crash de POW — no hubo ningún `FATAL` de
  `ovh.gabrielhuav.pow` en toda la sesión.

---

## 10. Sigue sin medirse

- 🔴 Los **2 ANR** del renderer **OSM nativo** tras ~13 min. No se tocaron aquí ni se compararon
  contra `main`. Toda esta sesión fue con el proveedor **web** (CARTO Voyager), que es el default.
- ⚠️ **API ≤ 32**: el SDK de esta máquina no tiene imagen de sistema ≤ 32, así que la costura de
  idioma de API 24–32 (`LocaleHelper.wrap`, sin `LocaleManager`) **sigue sin probarse**. En API 35
  el idioma por aplicación funciona y `composeResources` lo sigue.
