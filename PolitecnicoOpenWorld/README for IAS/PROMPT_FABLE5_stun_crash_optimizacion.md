# PROMPT · Fable 5 — crash, mareo/stun, optimización gama baja y pendientes

> **Este es tu plan de trabajo; síguelo por prioridad.** Los números de línea (verificados el
> 2026-07-22, sobre `StreetFighterViewModel.kt` = **5 633 líneas**) son un punto de partida para
> no explorar a ciegas — **NO son dogma**: se desplazan al editar. **Localiza por el nombre del
> símbolo** que se indica y lee el contexto local antes de cambiar.
>
> Base ya commiteada por Opus 4.8 (compila): subtítulos secuenciados, IA usa fatality/súper al
> llenar barra, IA vs IA pelea en el mapa del peleador con la IA más avanzada. Arrancas sobre eso.

## ⚡ Reglas para no quemar créditos

1. **NO leas el VM entero.** Solo los rangos indicados; localiza por símbolo.
2. **NO re-audites assets** (cerrados por el dueño) salvo donde una tarea lo pida.
3. **Un solo `gradlew compileDebugKotlin testDebugUnitTest`** al final de cada bloque.
4. **Motor compartido por 6 modos, 0 tests de motor.** Cambio en el motor = prueba a mano los 6
   modos (Arcade, VS, IA vs IA, Showcase, Tutorial, Multiplayer) o escribe tests de caracterización
   primero (`SF/PLAN_refactor_motor_compartido.md`). La última regresión grande salió de tocar lo
   compartido sin red.

## Rutas

| Qué | Dónde |
|---|---|
| VM | `app\src\main\java\ovh\gabrielhuav\pow\features\streetfighter\viewmodel\StreetFighterViewModel.kt` |
| Screen (SCR) | `app\src\main\java\ovh\gabrielhuav\pow\features\streetfighter\ui\StreetFighterScreen.kt` |
| Modelos (MDL) | `app\src\main\java\ovh\gabrielhuav\pow\domain\models\streetfighter\SfModels.kt` |
| Hojas compartidas | `app\src\main\java\ovh\gabrielhuav\pow\features\streetfighter\data\SfSharedSheets.kt` |
| Estado UI | `app\src\main\java\ovh\gabrielhuav\pow\features\streetfighter\viewmodel\StreetFighterState.kt` |
| Estado de sesión | `README for IAS\_SESION_ACTUAL.md` (léelo y actualízalo al terminar) |

---

## 🔴 BLOQUE A · CRASH de La Llorona (P0 — bloquea el juego)

**Síntoma (dueño):** seleccionar/pelear con **La Llorona** CRASHEA el juego.

**Lo ya descartado por Opus (no gastes créditos re-verificándolo):**
- Su atlas `DATA\lallorona.json` está **sano**: 228 frames, 53 animaciones, 0 vacías, 0 refs a
  frames inexistentes.
- `alphaFallbackId` (VM ~1289) no lanza (solo devuelve ESCOMBOY/ESCOMGIRL). `changeState` ya
  bloquea entrar a estados sin arte.

**Pistas fuertes (por dónde empezar):**
- La Llorona **no trae PATADA LARGA/OVERHEAD** (movimientos nuevos), así que entra al camino del
  **placeholder ALPHA** (`StreetFighterScreen.kt` ~301 `alphaFallback`, y `usesAlphaFallback` /
  `hasAlphaFallback` en VM ~1290). Ese camino carga un **tercer atlas en RAM** y usa
  `images.getValue(...)` / decodifica `fallbackId.spriteAsset`. Un `getValue` con clave ausente
  lanza `NoSuchElementException`; una decodificación fallida fuera del `runCatching` peta.
- Su escenario es `ISLA_MUNECAS` (`SfStageCatalog:99/51`, `fondo_islamunecas_anim.webp`).
  **Verifica que ese webp (día/noche/apocalipsis) EXISTE** en `assets\STREETFIGHTER\IMAGES\`; un
  fondo ausente revienta en `loadStageBackground`/`getValue`.

**Cómo hacerlo bien:** **reproduce con logcat** (necesitas el stacktrace real — no adivines el
fix). Filtra por la excepción al entrar a su pelea. Con el stacktrace, el fix suele ser un
`getValue` → `get()` con fallback, o envolver la carga del ALPHA en el `runCatching` que ya existe.
Prueba después: seleccionarla, pelear contra ella, y IA vs IA con ella en ambos lados.

---

## 🟠 BLOQUE B · Optimización GAMA BAJA (el encargo grande del dueño)

**Contexto (dueño):** en gama alta funciona de maravilla; solo **se traba unos segundos al
cargar** (p. ej. al disparar la metamorfosis de La Presidenta). **El problema real es la gama
baja.** Hay que optimizar para que no se trabe ni crashee por memoria ahí.

**Lo que ya existe y debes respetar/extender:**
- Bandera `lowEnd` (se pasa a `loadStageBackground(..., lowEnd = lowEnd)` en SCR ~369 y a la carga
  de atlas). En gama baja ya se usa `inSampleSize=2` (submuestreo) y RGB_565.
- El overlay "CARGANDO" (`assetsLoading`) ya evita el freeze de UI al decodificar (~6 MB de atlas)
  moviendo la decodificación a `Dispatchers.IO` (SCR ~360-372).
- ⚠️ El `.aab` NO baja refactorizando código: el peso está en los atlas (88.6 MB de IMAGES).

**Qué atacar (medir antes con un perfil de memoria/tiempos en un equipo gama baja o emulador):**
1. **El "traba unos segundos" al cargar** un peleador/atlas grande (La Presidenta con metamorfosis
   tiene muchos frames). Precargar/mostrar el overlay de carga también para el ATLAS del peleador,
   no solo el fondo. Ver dónde se decodifican los sprites del peleador (`SfFrameCatalog.load`).
2. **Presupuesto de RAM en gama baja:** el ALPHA es un 3er atlas simultáneo; en gama baja
   considera submuestrearlo más agresivo o liberar atlas que no están en pantalla.
3. Reusar bitmaps / liberar (`recycle`) los que ya no se usan entre peleas (arcade encadena
   muchas). Revisar fugas de bitmap.

Detalle y plan de fases del motor en `SF/PLAN_refactor_motor_compartido.md` y notas P2b de
`_SESION_ACTUAL.md`. **Mide en gama baja**; no optimices a ciegas.

---

## 🟡 BLOQUE C · Mareo / STUN + pulido del medidor de súper

Dueño: quiere el mecanismo clásico de **aturdimiento (mareo con estrellitas)** de SF, y que la
barra de súper **brille al llenarse** y **decaiga** si no aciertas. Confirmado: **todos los modos
(incluido multijugador)** y **dureza MODERADA**. No quiere la animación simple de "hurt".

### C.1 · Medidor de súper (ya existe, solo pulir)
- Se dibuja en `SCR ~2853-2872`: azul `0xFF3AA6FF`, dorado `0xFFFFD700` al llenarse.
- **Falta:** efecto de **brillo/pulso** cuando `frac >= 1f` (súper lista) y **decaimiento lento**
  del `superMeter` si el peleador deja de acertar golpes.
- Carga: `chargeSuper` (VM ~2316) con `SUPER_METER_ON_HIT/ON_TAKE/ON_BLOCK` (`SfModels:47-50`,
  `SUPER_METER_MAX=100`). El decaimiento va en el tick por frame (ver `updateFighter` VM ~1411 /
  `finishFighterUpdate`), con un temporizador de "último golpe conectado" para no decaer si acaba
  de pegar.

### C.2 · Barra de MAREO (nueva)
- **Molde a copiar:** `parryStunUntilMs` (VM ~654 decl., ~1416 congela al peleador, ~2446 set,
  reset en ~4830/~5556). El mareo es ese mismo "congelar N ms" pero disparado por una barra.
- **Arte disponible:** NO hay sprite de "estrellitas". Sí existe `stun-3` (idle inclinado 16°)
  para los 18 (`SfSharedSheets.kt:211`, alias `stun-1/2 → stun-3`). Usa `stun-3` como pose de
  mareado **+ estrellas PROCEDURALES** dibujadas en la Screen (Canvas, como el contador de combo
  en `SCR ~2874`) orbitando la cabeza. Eso es "mejor que el hurt simple" sin pipeline de arte.
- **Mecánica (moderada):** medidor `dizzyMeter` (0..MAX) en `SfFighter` (`SfModels` junto a
  `superMeter:511`) que **sube al RECIBIR** golpes (proporcional al daño) y **baja si dejan de
  pegar** (decae en el tick tras ~1.5 s sin recibir). Al llenarse → estado **STUN** (nuevo
  `SfFighterState`, o reusar un flag + `stun-3`) que **congela** al peleador ~2 s (no se mueve),
  luego vacía el medidor. Añade constantes en `SfConstants`.
- **Multijugador (obligatorio, con cuidado):** el súper se sincroniza en `applyRemoteSnapshot`
  (VM ~5301-5324, `rs.meter` ~5311; se envía ~5378). El **estado STUN viaja gratis** si lo modelas
  como `SfFighterState` (el `state` ya se sincroniza, VM ~5319). El `dizzyMeter` cada peer lo
  calcula sobre SU propio peleador con los golpes que resuelve localmente; si necesitas mandarlo,
  añádelo al snapshot como el `meter` (opcional, defensivo para clientes viejos). **Prueba que P1
  y P2 no se desincronicen** (quién está aturdido debe coincidir).
- **Degradación:** un peleador sin `stun-3` (no debería pasar, lo generan las hojas compartidas)
  simplemente no muestra pose especial; el freeze sigue valiendo.

---

## 🟢 BLOQUE D · Metamorfosis de La Presidenta entre rondas (necesita decisión del dueño)

**Síntoma (dueño):** la transformación Presidenta ➜ Yoalli (50 % HP) **solo ocurre en la primera
pelea; en la segunda regresa a ser La Presidenta**.

**Diagnóstico de Opus (anclas):** al transformarse, `completePresidentaMetamorphosis` (VM ~2116)
pone `id = YOALLI` y `metamorphosed = true` y ella **se queda** así. En `resetRound` (VM ~5533) el
peleador se reconstruye con `base.cpu.copy(id = s.cpu.id, ...)`: **conserva el `id` actual pero NO
copia `metamorphosed`** (vuelve al `false` del `base`), y **no hay memoria de la identidad
original** (`LA_PRESIDENTA`) una vez transformada. De ahí el estado inconsistente entre rondas.

**⚠️ PREGUNTA AL DUEÑO antes de tocar** (comportamiento deseado): ¿cada ronda debe **reiniciar a
La Presidenta** y re-transformar al bajar a 50 % HP (lo clásico), o la transformación debe
**persistir** el resto de la pelea? Según la respuesta:
- *Reiniciar cada ronda:* en `resetRound`, restaurar el `id` ORIGINAL del peleador (guárdalo al
  empezar la pelea) y `metamorphosed = false`.
- *Persistir:* copiar también `metamorphosed = s.cpu.metamorphosed` y mantener el `id` transformado.

Ojo con `completeYoalliMetamorphosis` (VM ~2127) y el trigger (VM ~2622: `d.id == LA_PRESIDENTA &&
!d.metamorphosed`). No lo confundas con las metamorfosis NUEVAS aún sin implementar (Bloque de arte).

---

## 🔵 BLOQUE E · Voz de intro del Policía hombre se corta

**Síntoma (dueño):** la intro *"Está prohibido beber en vía pública"* (`special_pol_h_intro`,
~15 s) **debe terminar de reproducirse** y hoy se corta.

**Pista:** la intro se emite en VM ~1080 (`introVoiceSent`), y `introVoiceSent` se resetea en
`resetRound` (VM ~5561). Revisa si otra voz (ataque/hurt) **interrumpe** la intro por usar un
canal de audio único (`_soundEvents` / reproductor), o si el clip se detiene al terminar el banner
de ronda. Que la intro no la pise otra voz hasta terminar (o al menos que no la corte el inicio de
la pelea). Verifica también que el `.ogg` no esté recortado corto (ver Bloque de items humanos).

---

## 🟣 BLOQUE G · Navegación y Tutorial (localizados, del dueño)

### G.1 · Volver al selector del MISMO modo
Al salir de un modo (Práctica, IA vs IA, etc.) te manda al selector de personaje **de Arcade**;
debería regresar al selector **de ese mismo modo** (no tan atrás). Ancla: `backToCharacterSelect`
(VM ~4133) hace `_state.value = StreetFighterState()` (default = selector genérico/arcade), y
pierde el modo. Hay que **recordar el modo** (arcade/práctica/IAvsIA/…) y restaurar SU selector en
vez del default. Mira también el grafo de menús por encima del VM (quién decide qué selector se
muestra) — puede que el "modo" viva ahí.

### G.2 · Tutorial — tres cosas
1. **Mensaje "ESO NO ERA" desactualizado.** El error dinámico es `tutorialError =
   "<hiciste> → <tocaba>"` (VM ~4055, se pinta en `SfTutorialOverlay` ~89/182, pasado desde
   `SCR ~584`). El rótulo estático "ESO NO ERA" está en el overlay/Screen. **Pregunta al dueño el
   texto correcto** (o hazlo referir a los controles ACTUALES) — no inventes la redacción.
2. **Resaltar el botón que toca presionar** ahora mismo: que **brille / se agrande / anime** el
   botón del paso actual del tutorial para que el jugador sepa cuál es. UI en el overlay + los
   controles en pantalla (`SfTutorialOverlay.kt` + los botones táctiles del `StreetFighterScreen`).
   `tutorialSteps`/`tutorialStepIndex` (VM ~3945) dicen qué acción toca.
3. **Invertir el layout del tutorial:** hoy la hoja de combos está arriba y los botones abajo;
   **nadie lee la hoja**. Poner **los botones ARRIBA** (lo que hay que presionar) y la **hoja de
   combos ABAJO**. Cambio de orden en la composición del tutorial (`SfTutorialOverlay.kt` /
   `StreetFighterScreen.kt`).

*(Ya HECHO por Opus, no lo repitas: arcade ahora retrocede escalón solo tras 3 derrotas SEGUIDAS;
IA vs IA pelea en el mapa del peleador con la IA más avanzada; subtítulos por tramo; IA usa
fatality/súper al llenar la barra.)*

## ⚪ BLOQUE F · TODO LO PENDIENTE (deriva por dificultad)

Lee `_SESION_ACTUAL.md` §3-§4. Resumen de lo que sigue abierto y a quién va:

- **Audio (P0):** 29 clips demasiado largos + 5 fuera de −16±2 LUFS → **Gemini 3.6** con los
  segundos exactos del dueño. 2 clips no normalizables sin comprimir (ver traspaso). Faltan
  attack/hurt en 6 peleadores → **el dueño graba**.
- **Bloques de ARTE sin importar (fatality V2 + metamorfosis nuevas de La Presidenta):** requieren
  chroma+recorte+import (`sf_import_fixed_pose.py`) + extender el packer + re-empacar. Fuentes en
  `tools\_para_corregir\`. **El dueño decide cuándo.** Ver `PROMPT_FABLE5_motor_pendiente.md`.
- **Animaciones congeladas** (`stun-1==2==3` en los 18, etc., P2): mirar la hoja fuente; si solo
  trae una pose, no hay arreglo sin arte nuevo.
- **Tests de caracterización del motor (Fase 1):** Opus 4.8. Detekt: 5 smells preexistentes
  (baseline correcto, NO `--build-upon-default-config`) → Gemini 3.6.

---

## 🙋 ITEMS QUE REQUIEREN INTERVENCIÓN HUMANA (el dueño; NO los "arregles" tú)

Documentados aquí para que no se pierdan. Fable puede AYUDAR a localizar, pero el corte/regrabación
lo hace el dueño:

1. **Paparazzi 5 tiene un audio que en realidad es de Paparazzi 1.** Hay que rastrear cuál es, en
   **mp3 (`tools/_audio_review/`) + ogg (`assets/STREETFIGHTER/SOUNDS/`) + subtítulo
   (`voice_phrases.json`)**. (Ayuda posible: comparar las frases curadas de `special_paparazzi_5*`
   vs `special_papz1_*` para identificar el intruso.)
2. **Señor de la tienda: un audio donde brevemente sale la voz de Prankedy.** Recortar ese clip
   (humano). Identificar cuál de `special_senor_tienda*`.
3. **La Tzitzimime mal recortada + audios a destiempo.** Hacer **audit a su spreadsheet** (tooling
   → Opus/Gemini) y el dueño la recorta a mano. Sus `.ogg` (`special_la_tzitzimime_*`) están a
   destiempo → recorte humano.

---

## Verificación (antes de cerrar cualquier bloque)

```
.\gradlew.bat compileDebugKotlin testDebugUnitTest
```
```
..\detekt-cli-1.23.8\bin\detekt-cli.bat --config "config\detekt\detekt.yml" --input "app\src\main\java"
```
(NO `--build-upon-default-config`; baseline = 5 smells preexistentes.) `git status` solo lo que
tocaste. **Actualiza `_SESION_ACTUAL.md`** antes de terminar, con lo hecho y lo que quede futuro.

## Última regla
**Verifica los resúmenes ajenos antes de repetirlos.** En este proyecto varios informes de "todo
al 100 %" resultaron falsos al medirlos. **Mide.**
