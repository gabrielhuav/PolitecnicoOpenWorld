# PROMPT · Fable 5 — implementar TODO lo pendiente del motor SF

> **Pega este archivo completo y empieza a trabajar.** Está escrito para gastar pocos
> tokens: lleva las rutas y los **números de línea ya verificados** para que NO tengas que
> explorar. Sigue el orden; cada paso es independiente y verificable.

## ⚡ Reglas para no quemar créditos

1. **NO leas `StreetFighterViewModel.kt` entero (5 271 líneas).** Lee solo los rangos que se
   indican en cada tarea.
2. **NO re-audites los assets.** Están cerrados y auditados por el dueño
   (`VACIO 0 · MULTI_FIGURA 0 · ESCALA 0 · BORDE 1`). No corras las herramientas de audit.
3. **NO toques `tools/`** salvo que una tarea lo pida. El pipeline de assets está terminado.
4. **NO re-empaquetes atlas.** Todo el arte ya está empacado.
5. Verifica con **un solo** `gradlew` al final de cada bloque, no tras cada edición.

## Rutas

| Qué | Dónde |
|---|---|
| Repo raíz | `C:\Users\gabri\AndroidStudioProjects\PolitecnicoOpenWorld` |
| Proyecto (aquí está `gradlew.bat`) | `...\PolitecnicoOpenWorld\PolitecnicoOpenWorld` |
| ViewModel (VM) | `app\src\main\java\ovh\gabrielhuav\pow\features\streetfighter\viewmodel\StreetFighterViewModel.kt` |
| Screen (SCR) | `app\src\main\java\ovh\gabrielhuav\pow\features\streetfighter\ui\StreetFighterScreen.kt` |

Rama `fix-audio-add-newFightAssets`. Convenciones: `README for IAS\09_CONVENTIONS_GOTCHAS.md`
(MVVM, estado inmutable `_state.update { it.copy(...) }`, strings ES+EN con paridad,
**CRLF en `.kt`**).

---

## BLOQUE 1 · Subtítulos (rápido y de alto impacto)

Hay 64 subtítulos curados en `assets/STREETFIGHTER/DATA/voice_phrases.json` que **NO se ven
en el juego**. Dos causas:

### 1.1 · El delimitador `|` se pierde

29 clips usan `|` para partir el subtítulo en varias líneas sincronizadas con la voz.

- **VM:319** `sfHudSanitize` hace `.replace(Regex("[^A-Z0-9 ]"), " ")` → convierte `|` en espacio.
- **SCR:2907** `sub.split(' ')` parte por espacios, no por `|`.

**Qué hacer:** partir por `|` ANTES de sanitizar, sanitizar cada tramo por separado, y
dibujar **una línea por tramo** en la Screen. El estado lo lleva `specialSubtitleHud`
(**VM:337**, se dibuja en **SCR:2897**).

### 1.2 · Los subtítulos están apagados

**VM:330** `private val voiceSubtitlesEnabled = false`.

La condición para encenderlo era paridad ES+EN, y **ya se cumple: 64 `es` / 64 `en`**.
Ponlo en `true` **después** de 1.1, o se activa perdiendo el formato multilínea.

---

## BLOQUE 2 · Aleatoriedad de poderes (La Presidenta)

Los cuadros YA están empacados. Falta que el motor elija.

### 2.1 · Fatality aleatoria entre dos variantes

`fatality-1..4` (mazo + haz tricolor) y `fatality2-1..4` (libro + dinero) están en el atlas.
Hoy la animación `fatality` usa solo la primera.

**Qué hacer:** al entrar en el estado FATALITY, elegir al azar entre `fatality` y
`fatalityV2`. Requiere que el packer emita también `fatalityV2` — mira
`tools/pack_sf_character.py`, busca `fatality-` (ya existe el bloque de "fatality dedicado").

### 2.2 · Elemento aleatorio

Los `fat_*` de La Presidenta son **un solo poder con 4 elementos** (ULTIMATE / AIR / FIRE /
EARTH) que se elige **al lanzar**, no lo escoge el jugador. Los cuadros aún NO están
importados: pídelos al dueño o impórtalos con `tools/sf_import_fixed_pose.py`.

### 2.3 · Proyectil aleatorio

Hay 3 variantes de destello de energía (`fx-energy-1`, `fx-energy-2`, y una tercera sin
importar). Elegir una al azar al lanzar el proyectil.

**Guarda que ya existe y debes respetar:** **VM:2075** `tryBonusPower` comprueba
`animations[state.jsKey].isNullOrEmpty()` antes de entrar al estado. Un poder sin animación
simplemente no se dispara. `onBonusPowerPressed` ya salta los poderes sin animación.

---

## BLOQUE 3 · Metamorfosis (2 secuencias nuevas)

**Spec completa y ya validada por el dueño: `README for IAS\SF\SPEC_metamorfosis_lapresidenta.md`.**
Léela: trae el orden exacto y las poses.

Resumen:
- **Larga (9 poses)**, Yoalli ➜ Presidenta: al **iniciar la última pelea del arcade**, tras
  derrotar a Yoalli. Secuencia completa `_01`…`_09`, sin azar.
- **Corta (3 pasos)**, ➜ Presidenta: al seleccionarla **fuera de arcade**.
  `random(_01.._04)` → `_05` → `_06`.

⚠️ **No la confundas con la que ya existe.** Hoy hay `BONUS_POWER_11` +
`completePresidentaMetamorphosis` (**VM:1705**): Presidenta ➜ Yoalli a mitad de combate
(50 % HP). Las dos nuevas van en **sentido contrario** y se disparan **al empezar**.

Las poses están recortadas y numeradas en `tools\_para_corregir\metamorfosis_*\`, pero
**aún no importadas al atlas**.

---

## BLOQUE 4 · Tutorial

`assets/STREETFIGHTER/DATA/combos.json` (15 KB) ya tiene el catálogo, y el tutorial ya existe
con 21 lecciones. **Falta:**

- Lección del **FATALITY**: cómo se saca (súper **EN CARRERA** con el medidor lleno).
- Lecciones de los movimientos nuevos (hojas 20-29) que no tengan.
- Explicar el **medidor de súper**, que ya existe: `SfFighter.superMeter` + `superReady`
  (`SfModels.kt:511-523`), y ya se dibuja (**SCR:2858**). **NO hay que crear UI nueva.**

---

## ⚠️ BLOQUE 0 — léelo antes de tocar el motor

`StreetFighterViewModel.kt`: **5 271 líneas, 65 funciones públicas, 7 banderas de modo
consultadas 104 veces, CERO tests.** Los 6 modos (Arcade, VS, IA vs IA, Showcase, Tutorial,
Multiplayer) comparten ese archivo.

**La última regresión grande de este proyecto** salió de tocar algo compartido sin red de
seguridad (una sesión re-recortó las 520 hojas y hubo que revertir).

Plan medido por fases: `README for IAS\SF\PLAN_refactor_motor_compartido.md`.
**Recomendación fuerte:** antes de los bloques 2 y 3, escribe los tests de caracterización de
la Fase 1 (física de un tick, `changeState`, `damageForAttack`, poderes bonus). Son **riesgo
cero** porque es pura adición, y son lo único que detecta si rompes uno de los 6 modos.

Si decides saltártelos, **prueba a mano los 6 modos** tras cada bloque.

---

## Verificación

```
.\gradlew.bat compileDebugKotlin testDebugUnitTest
```

```
..\detekt-cli-1.23.8\bin\detekt-cli.bat --config "config\detekt\detekt.yml" --input "app\src\main\java"
```

⚠️ **NO** uses `--build-upon-default-config`: sube el conteo a 16 con reglas que el repo no
adoptó. El baseline real son **5 smells preexistentes** (`CachingWebViewClient`,
`NpcAiManager`, `RoadRouter`, `CatSpriteManager` ×2). Varios docs dicen "0 smells" y es falso.

`git status` debe mostrar **solo** lo que tocaste. Si aparece un peleador que no estabas
tocando, algo se re-empaquetó de más → `git checkout -- <ruta>`.

**Actualiza `README for IAS\_SESION_ACTUAL.md` antes de terminar**, aunque te quedes a medias.
Es lo único que garantiza que la siguiente sesión continúe en vez de alucinar.

## Última regla

**Verifica los resúmenes ajenos antes de repetirlos.** En este proyecto varios informes de
"está todo al 100 %" resultaron falsos al medirlos — incluido uno de "100 % normalizado a
−16 LUFS" que sigue circulando y no es cierto. Mide.
