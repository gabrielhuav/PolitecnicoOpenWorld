# 🧠 MEMORIA DE SESIÓN — estado vivo del trabajo

> **Este archivo se ACTUALIZA en cada sesión y se mantiene CORTO** (objetivo: < 200 líneas).
> Es lo primero que lee una IA nueva. Si crece, se resume: el detalle histórico va a
> `DISENO_ARCADE_SF_POW.md` (modo SF) o al doc del área, no aquí.
>
> **Regla de oro:** si te quedas sin tokens a media tarea, actualiza ESTE archivo ANTES de
> parar. Es lo único que garantiza que la siguiente IA continúe en vez de alucinar.

**Última actualización:** 2026-07-22 (noche) · Fable 5 (grueso) + Opus 4.8 (cierre) · rama `fix-audio-add-newFightAssets`

> ➡️ **AHORA:** Fase 1 del motor **AUDITADA ✅** (extracción idéntica, verificada como datos) y
> Fase 2 **INICIADA** (2a `SfAnimation` + 2b `clampToStage`, +12 tests). ⚠️ **El dueño debe
> Rebuild + `testDebugUnitTest` (esperados 47) + jugar los 6 modos** antes del siguiente
> incremento (Fase 2c: empuje de pushboxes → SfPhysics; luego `SfEngine`, con compilador).
>
> ⚠️ **NOTA DE CRÉDITOS:** Fable 5 agotó su límite mensual a mitad de sesión (~+90 USD) y NO
> alcanzó a cerrar. Opus 4.8 tomó el relevo, RE-auditó los cambios sin compilar que dejó Fable
> (Fase 2a/2b) y confirmó que son espejos exactos y compile-safe (símbolos resueltos, sin dupes).
> Ninguno de los dos pudo compilar (sandbox sin SDK): **la validación real la hace el dueño.**

## 🖥️ Rutas por PC (para la mudanza laptop ↔ escritorio)

| PC | Raíz del PROYECTO (aquí están `gradlew.bat` y `tools/`) |
|---|---|
| **Laptop** (referencia) | `C:\Users\gabri\AndroidStudioProjects\PolitecnicoOpenWorld\PolitecnicoOpenWorld` |
| **Escritorio** | *distinta — COMPLETAR con la real de esa PC* |

⚠️ **Solo cambia el prefijo absoluto.** Todas las rutas de los prompts/docs son **RELATIVAS a la
raíz del proyecto** (p.ej. `tools\...`, `app\src\main\assets\STREETFIGHTER\...`,
`README for IAS\...`), así que funcionan igual en ambas PCs. El GEN de sprites vive FUERA del repo
en `..\newSFAssets\GEN_*`.

**Traspaso a Gemini (escritorio) para las correcciones humanas de audio/sprites:**
`README for IAS\PROMPT_GEMINI_correcciones_humanas_audio_sprites.md`.

---

## 1. Cómo está organizado esto

```
README for IAS/
  _SESION_ACTUAL.md      <- ESTE archivo. Empieza aquí SIEMPRE.
  00_INDEX.md            índice general
  01_ARCHITECTURE.md     arquitectura compartida
  02_DATA_LAYER.md       Room, DAOs, repos, red
  07_OTHER_FEATURES.md   menú, ajustes, coleccionables (+ SF, pendiente de dividir)
  09_CONVENTIONS_GOTCHAS.md   ⚠️ OBLIGATORIO antes de tocar código
  MUNDO/                 🌎 modo 1: mundo libre POW
  SF/                    🥊 modo 2: peleas "Huelum vs. Goya"  (empieza por SF/00_SF_INDEX.md)
  _ARCHIVO/              histórico YA EJECUTADO. Referencia, NO tareas.
```

Los dos modos son independientes: un cambio en `MUNDO/` casi nunca afecta a `SF/`.
Lo compartido (arquitectura, datos, convenciones) vive en la raíz.

## 2. A quién delegar cada tarea

| Dificultad | IA | Cuándo |
|---|---|---|
| **Alta** | **Sol 5.6** · **Fable 5** | Refactors grandes, cambios que tocan varios módulos, diseño de sistemas nuevos, depurar algo que ya falló dos veces, cambios en el slicer o el packer (afectan a los 18 peleadores a la vez). |
| **Media** | **Opus 4.8** | Features acotadas, auditorías, herramientas de `tools/`, arreglar un bug localizado, documentación técnica, cambios de un solo módulo. |
| **Baja** | **Gemini 3.6** | Regenerar assets con un pipeline que YA existe, recortar/normalizar audio con instrucciones exactas, aplicar correcciones de un CSV, mover archivos, tareas repetitivas y bien especificadas. |

**Antes de delegar, escribe la tarea con:** rutas absolutas, el comando exacto, cómo se
verifica que salió bien, y qué NO debe tocar. Sin eso, cualquier IA improvisa.

## 3. Qué se hizo en esta sesión (2026-07-22 NOCHE, Fable 5 — `PROMPT_TRASPASO_FABLE.md`)

1. **✅ Parte A · AUDITORÍA de la Fase 1 (Opus): APROBADA.**
   - `validFrom` comparada **como datos** (script) entre el VM previo al commit `Fase 1a` y
     `SfStateMachine`: **67 destinos, 5 sub-listas y knockdownStates IDÉNTICOS**.
   - `Fase 1b-1e`: espejos exactos línea a línea (forAttack, CHIP_ATTACK_STATES, ATTACK_META
     21 entradas, sfUsableBonusPowerCount, SfPhysics.step, resolvedDamage + constantes de
     combo con los mismos valores). `Fase 1f` solo añade tests.
   - VM actual: alias en su sitio, **0 copias residuales**, árbol limpio.
   - Conteo MEDIDO: **32 tests nuevos** + 3 previos = **35** (los docs decían "~30/~35").
     Son caracterización real (literales fijados), no tautologías.
   - Lo que NO pude hacer aquí (sandbox sin SDK): compilar/correr tests y jugar los 6 modos —
     el traspaso dice que el dueño ya los probó y nada cambió desde entonces.
2. **Parte B · Fase 2 INICIADA (2 incrementos espejo, mismo patrón alias de la Fase 1):**
   - **2a `SfAnimation` (nuevo, puro):** frameIndex (wrap), frameTimerMs, shouldAdvance
     (delay<=0 = FREEZE/TRANSITION), isCompleted (fix último-frame-sin-−1). El VM conserva
     `withAnimationFrame`/`updateAnimation`/`isAnimationCompleted` como ENVOLTORIOS (mismos
     call sites). +8 tests (`SfAnimationTest`).
   - **2b `SfPhysics.clampToStage`:** espejo de `clampFighterToStage` (NaN/∞ → centro/piso,
     coerce X/Y, tope de aire `STAGE_AIR_CEILING=220f`); `STAGE_X_MIN/MAX` movidos del
     companion del VM a `SfConstants` (alias conservados). +4 tests en `SfPhysicsTest`.
   - Archivos: SfAnimation.kt (nuevo), SfPhysics.kt, SfModels.kt, VM, SfAnimationTest.kt
     (nuevo), SfPhysicsTest.kt. Verificado estático: llaves vs HEAD, CRLF/LF consistentes.
3. ⚠️ **PENDIENTE DEL DUEÑO antes del siguiente incremento:** Rebuild +
   `testDebugUnitTest` (**esperados 47 = 35 + 12 nuevos**) + detekt (baseline 5) + jugar
   los 6 modos (la animación y el clamp tocan TODOS).
4. **Siguiente (por orden):** Fase 2c = núcleo de `updateStageConstraints` (empuje de
   pushboxes, viewport) a SfPhysics con firma pura `(f, opp, camX, dt, pushboxes) → par`;
   después el esqueleto `SfEngine` y la Fase 3 (modos como estrategia) — esas dos EXIGEN
   sesión con compilador (tocan audio/red/StateFlow) o incrementos con Rebuild del dueño
   entre cada uno.

### ❓ ¿Está COMPLETA la separación del motor? ¿Se puede lanzar a producción? (medido 2026-07-22 noche)

**Separación del motor: NO está completa.** Medido, no supuesto:
- `SfEngine` / `SfGameMode` **NO existen** todavía.
- `StreetFighterViewModel.kt` sigue en **5 643 líneas**.
- Las **7 banderas de modo se consultan ~142 veces** en el VM (showcase 23, online 62,
  gauntlet 18, audioShowcase 15, aiVsAi 11, arcade 8, tutorial 5).
- Las **10 funciones núcleo del tick** siguen privadas en el VM.
- HECHO: Fase 1 (red de seguridad, auditada) + Fase 2a (`SfAnimation`) + 2b (`clampToStage`).
- FALTA: el resto de Fase 2, **Fase 3 (modos como estrategia = la meta del dueño)**, 4 y 5.

**PERO la separación NO bloquea producción.** Es un refactor INTERNO de mantenibilidad
("añadir un modo = clase nueva en vez de tocar todo el archivo"), no de correctitud. El juego
YA funciona: los 6 modos + gama baja están probados OK por el dueño. **Lo que lanza a
producción es que el juego esté CORRECTO, no cómo esté organizado el código.**

**Lo que SÍ bloquea el lanzamiento AHORA (checklist real, corto):**
1. **Compilar + tests:** ni Fable ni Opus pudieron (sandbox sin SDK). El dueño: `.\gradlew.bat
   compileDebugKotlin testDebugUnitTest` (esperados **47**) + detekt (baseline 5, sin
   `--build-upon-default-config`).
2. **Jugar los 6 modos** para confirmar (a) los cambios de esta tanda (stun, metamorfosis,
   navegación, Llorona, intro) y (b) que la extracción 2a/2b NO cambió nada — el timing de
   animación y el clamp del escenario tocan TODOS los modos.
3. El `.aab` NO baja con el refactor (el peso son los atlas, 88.6 MB) — no esperar eso.

**Recomendación:** se PUEDE lanzar con el motor en Fase 1 + 2-parcial una vez pase el
Rebuild+playtest. Las fases restantes del motor son limpieza POST-lanzamiento, mejor hechas
incrementalmente CON compilador a la mano (tocan audio/red/StateFlow). No condicionar el
lanzamiento a ellas.

## 3b. Sesión anterior (2026-07-22 PM, Fable 5 — plan `PROMPT_FABLE5_stun_crash_optimizacion.md`)

*(La sesión AM del mismo día — subtítulos ON + track EN + lecciones — y el follow-up de Opus
quedaron commiteados; detalle en `SF/DISENO_ARCADE_SF_POW.md` §2026-07-22 y 00_INDEX.)*

1. **🔴 Bloque A · Crash La Llorona (P0), análisis estático + blindaje.** Confirmado que a su
   JSON le faltan SOLO `longKick`/`overhead` → es la ÚNICA que activa el camino ALPHA (3er
   atlas completo en RAM). Sospechoso principal: pico de RAM (~186 MB de hojas) + `sheetFor`
   lanzando `error()`/OOM SIN runCatching en la composición. Blindado: cada atlas en
   `runCatching` (falla → se omite, drawFighter degrada), y el atlas ALPHA (silueta negra)
   SIEMPRE a media resolución con escala propia (`AlphaFallback.sheetScale`, ~63→16 MB).
   Fondos `islamunecas` verificados: existen los 3. **Falta confirmar con logcat/dispositivo.**
2. **🟠 Bloque B · Gama baja/carga:** TODO lo pesado de una pelea (atlas de ambos peleadores,
   ALPHA y el escaneo de alturas) se decodifica ahora en `Dispatchers.IO` bajo el overlay
   CARGANDO (`SfFightAssets` + `fightIds`), no en el hilo de UI → adiós al "se traba unos
   segundos". `fightIds` es un SET con ambas identidades de la metamorfosis: transformarse a
   media pelea YA NO re-decodifica nada (esa era la trabada de La Presidenta). El `recycle()`
   explícito entre peleas se DESCARTÓ sin medición (riesgo de dibujar bitmap reciclado).
3. **🟡 Bloque C · MAREO/STUN + medidor:** nuevo `SfFighterState.STUN` (AL FINAL del enum:
   viaja como `enum.name`, retro-compatible) + `dizzyMeter` en `SfFighter` (sube al RECIBIR,
   tope 25/golpe, decae tras 1.5 s; lleno → STUN 2 s con pose `stun-3` + estrellitas
   PROCEDURALES en Canvas + barra naranja/roja bajo la de súper). La animación "stun" se
   SINTETIZA en `SfFrameCatalog` (los JSON traen frames pero no la anim). Súper: halo dorado
   + pulso al llenarse y decaimiento LENTO tras 4 s sin conectar (la barra LLENA no decae).
   Todo en `applyMeterDecay` (tick, TODOS los modos; online solo el peleador LOCAL — el STUN
   remoto llega por el `state` del snapshot; el `dizzyMeter` remoto NO viaja: cosmético).
4. **🟢 Bloque D · Metamorfosis (decisión del dueño):** SOLO en el ROUND 1; al transformarse
   arranca con VIDA LLENA (antes 50%); y PERSISTE entre rondas (`resetRound` copia
   `metamorphosed`; `tryPresidentaMetamorphosis` gateado a `roundNumber == 1`).
5. **🔵 Bloque E · Intro del policía:** un clip `_intro` ya NO lo corta ninguna otra voz del
   mismo peleador, y mientras suena las voces nuevas de ese peleador se SALTAN (playSfSpecial).
6. **🟣 Bloque G · Navegación y tutorial:** al salir de una pelea se vuelve al selector del
   MISMO modo (`lastLaunchedMode` en la Screen; antes el LaunchedEffect forzaba Arcade);
   etiquetas del tutorial referidas a los CONTROLES actuales (X/Y/B/A/P/G/T/S + gesto);
   el botón del paso actual BRILLA/PULSA (`SfTutorialButtonGlow` en FighterXboxButtons y
   FighterNewMoveButtons); panel invertido: chips de botones ARRIBA, título/pista ABAJO.
7. ⚠️ **Sin compilar** (sandbox sin SDK/Java 17): queda **Rebuild + testDebugUnitTest +
   detekt (baseline 5) + prueba de los 6 modos** en Android Studio del dueño. Verificado
   estáticamente: llaves balanceadas vs HEAD, CRLF 100%, `git status` solo los 5 .kt tocados
   (SfModels, SfFrameCatalog, SfTutorialOverlay, StreetFighterScreen, StreetFighterViewModel).

### 🧪 QUÉ PROBAR EN DISPOSITIVO (checklist del dueño)

- **La Llorona:** 🆕 (2026-07-22, Opus) se importó su arte de **PATADA LARGA + OVERHEAD** (el
  dueño la entregó en chroma), así que **ya NO carga el atlas ALPHA** (0 movimientos sin arte) →
  crash resuelto de raíz + menos RAM. Ver `SF/IMPORT_lallorona_patada-overhead_2026-07-22.md`.
  Probar: seleccionarla, pelear contra ella e IA vs IA, y ver que su patada larga/overhead se
  vean bien. (El blindaje de Fable —decode en runCatching— se queda como red.)
- **STUN:** recibir ~5-6 golpes seguidos sin bloquear → mareo (estrellitas, congelado 2 s);
  pegarle al mareado lo despierta. En MULTIPLAYER: que P1 y P2 vean al MISMO aturdido.
- **Metamorfosis:** round 1 al 25% → Yoalli con vida llena; rounds 2+ sigue Yoalli y sin
  re-transformación. Sin trabada al transformarse.
- **Intro policía hombre:** "Está prohibido beber…" completa aunque empiece la pelea.
- **Navegación:** salir de Práctica/IA vs IA/Arcade → selector del MISMO modo; tutorial → hoja.
- **Súper:** brillo al llenarse; sin conectar ~4 s decae (si no está llena).
- 🆕 (2026-07-22, Opus) **UI:** (1) hoja de combos → los 3 botones (PROBAR/CAMBIAR/VOLVER) ahora
  ARRIBA de la hoja; (2) "CARGANDO" centrado + **cuenta 3-2-1 → PELEA** antes de cada round
  (`ROUND_INTRO_MS` 1800→3600); (3) **cuenta 3-2-1 entre lecciones** del tutorial (ya no salta el
  "ESE NO ERA"); (4) menú: **ALPHA→PRE-ALPHA** (ES+EN) + badges con transparencia pulsante 50-75 %.
  (5) controles rediseñados "Neón Arcade" (L1/L2 · R1/R2) y (6) pausa con logo POW. TODO compila.

### 🙋 ITEMS QUE REQUIEREN AL DUEÑO (no los toca ninguna IA sin ti)
- **Paparazzi 5** tiene un audio que es de **Paparazzi 1** → rastrear en mp3 + ogg + subtítulo.
- **Señor de la tienda:** un audio donde sale brevemente la voz de **Prankedy** → recortar.
- **La Tzitzimime** mal recortada + audios a destiempo → audit a su spreadsheet (tooling) + recorte
  a mano del dueño.

### 📋 TRABAJO FUTURO anotado en esta sesión (NO forzar sin lo que falta)

- **Azar de poderes La Presidenta (Bloque 2):** requiere importar `suoerFatV2_*` / `fat_*` /
  destellos extra de `tools\_para_corregir\ORIGINALES_lapresidenta_bonus\` con
  `sf_import_fixed_pose.py` + extender `fatality_keys`/`fx_keys` del packer + re-empacar SOLO
  La Presidenta. Recién entonces, lógica de azar CON fallback a la única variante.
- **Metamorfosis nuevas (Bloque 3):** larga 9 poses (última pelea del arcade, tras vencer a
  Yoalli) y corta 6 poses (al seleccionarla fuera de arcade). Spec:
  `SF/SPEC_metamorfosis_lapresidenta.md`. Poses en `tools\_para_corregir\metamorfosis_*\`
  SIN importar (aún con rótulo "STEP N"). Mismo pipeline que el Bloque 2.
- **Tests de caracterización Fase 1** (`SF/PLAN_refactor_motor_compartido.md`): el dueño
  decidió dejarlos como futuro → **Opus 4.8** según la tabla de delegación.
- **Bloqueo BAJO y parry BAJO sin lección de tutorial:** no existen como `SfComboAction` ni
  en `stateForAction` → añadirlos toca motor (hacerlo junto con los tests).

## 4. PENDIENTE — por prioridad

### ✅ ASSETS CERRADOS (2026-07-21, auditados por el dueño)

El dueño revisó las hojas y **dio los assets por buenos**. El audit automático queda en
`VACIO 0 · MULTI_FIGURA 0 · ESCALA 0 · BORDE 1`. El único BORDE es `lapresidenta/fatality-4`
y no es un fallo: el haz del súper es más ancho que los 256 px del lienzo.

Quedan detalles menores que el dueño considera **no bloqueantes**: unos pocos cuadros de
La Presidenta y algún hitbox afinable.

### 🔴 P0 · AUDIO — es el trabajo activo (subtítulos ✅ HECHOS 2026-07-22)

✅ **Subtítulos RESUELTOS:** 64 `es` curados + `en` completo en `voice_phrases.json` y
`voiceSubtitlesEnabled = true` (render por tramos `|`). Falta solo verlos en dispositivo.

Queda el AUDIO (ver `SF/PROMPT_traspaso_audio_subtitulos.md`, parte vigente):

- **29 clips demasiado largos** para su evento (el peor: `special_escomboy_attack`, 10.70 s
  para un grito de golpe). Se recortan del original, no hay que regrabar.
  → **Gemini 3.6** con los segundos exactos que diga el dueño.
- **5 clips fuera de −16 ±2 LUFS.** → **Gemini 3.6**.
- **Faltan `attack`/`hurt`** en 6 peleadores. → El dueño graba.

⚠️ **No repitas el resumen que dice "100 % normalizados a −16 LUFS y sin faltantes".** Está
medido y es falso: de los 80 `.ogg` solo **69 son voz** (11 son SFX globales), y hay **2
clips que NO pueden normalizarse** sin comprimir (`special_charro_attack_2` con 0.0 dB de
margen y `special_rey_grupero` con 1.3 de los 2.4 dB que necesita). Detalle en el traspaso.

### 🟢 P2 · Animaciones congeladas (el arte está bien, el cuadro se repite)

`stun-1==stun-2==stun-3` en **los 18** (⚠️ desde 2026-07-22 el estado STUN USA esos cuadros
vía la anim "stun" sintetizada — si llega arte nuevo de mareo, se verá solo);
`bonus-7/8/9/10` estáticos en `lapresidenta`;
`run-4==run-5` en 4; `forwards-3==forwards-4` en 3; `throw-2==throw-3` en 3;
`super-4==super-5` en `charronegro` y `senortienda`.
→ Hay que mirar la hoja fuente de cada uno: si solo trae una pose, no hay arreglo sin arte nuevo.

### 🔵 P2b · Motor compartido — ✅ Fase 1 HECHA y AUDITADA; Fase 2 INICIADA (2a+2b); sigue 2c

🆕 (2026-07-22 noche, Fable) Auditoría de la Fase 1 APROBADA (extracción idéntica, verificada
como datos) y Fase 2a (`SfAnimation`) + 2b (`SfPhysics.clampToStage`) hechas con el mismo
patrón de alias. **47 tests esperados.** Ver §3. Lo de abajo queda como referencia del plan:

**Fase 1 (red de seguridad) COMPLETA:** la lógica pura del `StreetFighterViewModel` se extrajo a
`domain/models/streetfighter/` (`SfStateMachine`, `SfDamage`, `SfPhysics`, `sfUsableBonusPowerCount`)
con **~30 tests de caracterización verdes** (antes 0). El VM la usa por alias/delegación →
comportamiento idéntico. Commits `SF motor Fase 1a`…`1e`.

**Continuar (Fases 2-5):** extraer el `SfEngine` puro (Fase 2), modos como estrategia (Fase 3, la
meta: modo nuevo = clase nueva), gama baja medida (Fase 4) y detekt/KDoc (Fase 5). Cada pieza que se
mueva YA tiene su test de la Fase 1 como red. Prompt de auditoría + continuación:
`SF/PROMPT_FABLE_auditar_y_continuar_motor.md`. **Validar los 6 modos (incl. multiplayer) en cada
paso.** Plan completo: `SF/PLAN_refactor_motor_compartido.md`.
→ Auditar Fase 1 + Fases 2-4: **Fable 5 / Sol 5.6**. Fase 5 (detekt/KDoc): **Gemini 3.6**.

⚠️ El `.aab` NO baja refactorizando código: el peso está en los atlas (88.6 MB de IMAGES).

### ⚪ P3 · Bloqueado en el dueño (rescatado de `PENDIENTES_2026-07-20.md`, ya archivado)

- **Mapas UAM Azcapotzalco y Cuajimalpa**: los 6 atlas actuales YA salen de los vídeos más
  recientes en disco. **Faltan vídeos nuevos**; al llegar, `tools/build_map_backgrounds.py`.
- **Arte V2 de La Presidenta + metamorfosis nuevas**: fuentes croma en `tools\_para_corregir\`
  sin importar (ver TRABAJO FUTURO en §3). El dueño decide cuándo se importa.

### ⚪ P3 · Deuda conocida

- `07_OTHER_FEATURES.md` (87 KB) mezcla menú/ajustes con el modo SF. Su parte de SF debería
  migrar a `SF/`. → **Opus 4.8**.
- **detekt NO está a 0**: 5 smells preexistentes (`CachingWebViewClient`, `NpcAiManager`,
  `RoadRouter`, `CatSpriteManager` ×2). Varios docs afirman "0 smells" y es **falso**.
  → **Gemini 3.6**.
- `lallorona`: `hit-face-2 == hit-face-3` (3 poses de arte para 4 cuadros). Deuda ACEPTADA
  por el dueño. Si se regenera la hoja 09 con 4+ poses, quitar la duplicación.
- La Tzitzimime y Yoalli tienen bonus powers sin recortar.

## 5. Verificación antes de cerrar CUALQUIER sesión

```bash
.\gradlew.bat compileDebugKotlin testDebugUnitTest
```

```bash
..\detekt-cli-1.23.8\bin\detekt-cli.bat --config "config\detekt\detekt.yml" --input "app\src\main\java"
```

⚠️ **NO** uses `--build-upon-default-config` en detekt: sube el conteo a 16 porque añade
reglas que el repo no adoptó. El baseline correcto son 5 smells preexistentes.

`git status` debe mostrar **solo** lo que tocaste a propósito. Si aparece un peleador que no
tocabas, algo se re-recortó de más → `git checkout -- <ruta>`.

Y **actualiza este archivo** antes de terminar.
