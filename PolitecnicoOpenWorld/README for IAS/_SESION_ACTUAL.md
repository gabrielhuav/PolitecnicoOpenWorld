# 🧠 MEMORIA DE SESIÓN — estado vivo del trabajo

> **Este archivo se ACTUALIZA en cada sesión y se mantiene CORTO** (objetivo: < 200 líneas).
> Es lo primero que lee una IA nueva. Si crece, se resume: el detalle histórico va a
> `DISENO_ARCADE_SF_POW.md` (modo SF) o al doc del área, no aquí.
>
> **Regla de oro:** si te quedas sin tokens a media tarea, actualiza ESTE archivo ANTES de
> parar. Es lo único que garantiza que la siguiente IA continúe en vez de alucinar.

**Última actualización:** 2026-07-22 · Fable 5 · rama `fix-audio-add-newFightAssets`

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

## 3. Qué se hizo en esta sesión (2026-07-22, Fable 5 — plan `PROMPT_FABLE5_motor_pendiente.md`)

1. **🗣️ SUBTÍTULOS DE VOZ ENCENDIDOS** (`voiceSubtitlesEnabled = true`, VM). Fix del
   delimitador `|`: `setVoiceSubtitle` sanea POR TRAMO y re-une con `|`. Detalle:
   `SF/DISENO_ARCADE_SF_POW.md` §2026-07-22. **(El renderizado lo ajustó Opus después — ver §🔧.)**
2. **🇬🇧 Track `en` de `voice_phrases.json` COMPLETO:** ~33 campos que seguían en español
   traducidos (tramos `|` y censuras conservados; onomatopeyas intactas). Verificado:
   64 `es` curados, 0 `en` con rasgos ES, 0 tramos desiguales, JSON válido, CRLF.
3. **🎓 Tutorial (`combos.json`, SOLO datos):** +`b_crouchchain` (cadena baja) y +`b_meter`
   (medidor: sube al conectar Y recibir; súper y FATALITY lo consumen entero — verificado en
   VM). La lección FATALITY YA existía (universal `fatality`) — el plan estaba desactualizado ahí.
4. **Bloques 2/3 del plan (azar de poderes + metamorfosis nuevas): NO se tocaron** — siguen
   bloqueados por el pipeline de arte (ver TRABAJO FUTURO abajo). Guard de degradación
   verificado intacto (`tryBonusPower` → `animations[jsKey].isNullOrEmpty()`).
5. Docs sincronizados (00, 07, SF/QA, SF/AUDIT_VOCES banner, SF/AUDIO_INVENTARIO, traspaso,
   README público EN+ES). ⚠️ **Sin compilar** (sesión sin Android Studio): falta
   `gradlew compileDebugKotlin testDebugUnitTest` + Rebuild + dispositivo.

### 🔧 Follow-up Opus 4.8 (2026-07-22, tras validar la compilación de Fable — compila OK)

6. **Subtítulos SECUENCIADOS:** se muestra UN tramo `|` A LA VEZ, repartiendo la ventana
   `[start,until]` entre los tramos (sincronía con la voz), en vez de todas las líneas juntas.
   Nuevo campo `specialSubtitleStartMs` (VM `setVoiceSubtitle` + `update()` + Screen). Un
   subtítulo nuevo **reemplaza** al anterior (un solo slot de estado: nunca se superponen).
7. **IA usa FATALITY/SÚPER al llenar la barra:** `cpuNewMove` — probabilidad que **escala** con
   `cpuIntensity` (fatality 0.45→0.95, súper 0.35→0.90; `nightmare` = tope), ya no gateada por el
   binario `aggressive`. El fatality sigue siendo dos tiempos (dash→RUN→soltar) pero ahora se
   inicia mucho más seguido.
8. **PENDIENTE → Fable 5 (siguiente):** MAREO/STUN — barra de aturdimiento + estado STUN +
   estrellas + TODOS los modos (con sync de red) + dureza moderada. Prompt autocontenido aparte.

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

`stun-1==stun-2==stun-3` en **los 18**; `bonus-7/8/9/10` estáticos en `lapresidenta`;
`run-4==run-5` en 4; `forwards-3==forwards-4` en 3; `throw-2==throw-3` en 3;
`super-4==super-5` en `charronegro` y `senortienda`.
→ Hay que mirar la hoja fuente de cada uno: si solo trae una pose, no hay arreglo sin arte nuevo.

### 🔵 P2b · Motor compartido entre modos (PLANIFICADO, no empezado)

Medido: `StreetFighterViewModel.kt` tiene **5 271 líneas**, **65 funciones públicas** y
**7 banderas de modo consultadas 104 veces**. Los modos YA comparten un solo motor (no hay
duplicación que borrar), pero lo hacen con `if (showcaseMode)` esparcidos, así que añadir un
modo obliga a tocar el archivo entero. Y **el motor de pelea tiene 0 tests**.

Plan por fases en `SF/PLAN_refactor_motor_compartido.md`. **No empezar por la fase 2 sin la
fase 1**: extraer el motor sin pruebas es cómo se metió la última regresión grande.
→ Fase 1 (tests): **Opus 4.8**. Fases 2-4 (extracción y gama baja): **Sol 5.6 / Fable 5**.

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
