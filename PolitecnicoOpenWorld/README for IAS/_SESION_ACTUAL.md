# 🧠 MEMORIA DE SESIÓN — estado vivo del trabajo

> **Este archivo se ACTUALIZA en cada sesión y se mantiene CORTO** (objetivo: < 200 líneas).
> Es lo primero que lee una IA nueva. Si crece, se resume: el detalle histórico va a
> `DISENO_ARCADE_SF_POW.md` (modo SF) o al doc del área, no aquí.
>
> **Regla de oro:** si te quedas sin tokens a media tarea, actualiza ESTE archivo ANTES de
> parar. Es lo único que garantiza que la siguiente IA continúe en vez de alucinar.

**Última actualización:** 2026-07-21 · Opus 4.8 · rama `fix-audio-add-newFightAssets`

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

## 3. Qué se hizo en esta sesión (2026-07-21)

1. **La Llorona — cerrados los 3 recortes malos.** `crouchTurn` era espejo (`flipX` en
   `_frame_meta.json`); `hit-face-1` traía 4 figuras (hoja 09 regenerada); `super-4/5/6` ya
   estaba bien en la staging y solo faltaba empaquetar. Detalle: `SF/DISENO_ARCADE_SF_POW.md`
   §2026-07-21f.
2. **Audit de voces y subtítulos.** Herramientas `tools/sf_voice_subtitle_audit.py` (SOLO
   LECTURA) y `tools/sf_apply_subtitle_fixes.py`. Corregido un bug de i18n real:
   `emitSpecialVoice` usaba `phraseEs` fijo y la traducción inglesa no se mostraba nunca.
3. **Audit automático de cuadros** (`tools/sf_audit_frames_auto.py`) y **fix de fusión
   vertical en el slicer**: `merge_fragments` comparaba solo la X, así que en las hojas con
   HURT HEAD en dos filas fusionaba cada pose con la de abajo. Verificado sobre las 520 hojas:
   0 regresiones.
4. **Reorganización de `README for IAS`** en `MUNDO/` + `SF/` + este archivo.

## 4. PENDIENTE — por prioridad

### 🔴 P0 · Bug visible en juego

**`hit-face-1..4` con dos figuras apiladas** en `senortienda`, `escomboy`, `escomgirl`,
`yoalliehecatl`. Al recibir un golpe a la cara sale un duplicado fantasma.

- El fix del slicer YA está commiteado y verificado, pero **está inerte**: falta re-recortar
  y re-empaquetar. `escomboy` y `yoalliehecatl` ya dan `HURT HEAD 14/14 OK`.
  → **Delegar a: Opus 4.8** (re-slice + re-pack + verificación visual de esos dos).
- `senortienda` y `escomgirl` siguen mal (15/14): sus hojas 09 traen **16 poses, no 14**, con
  un par pegado. **Necesitan hoja regenerada** o ajustar `SHEET_OVERRIDES` a 16.
  → **Decisión del dueño.**

### 🟡 P1 · Audio y subtítulos

- **62 de 69 clips sin subtítulo.** Requiere el OÍDO del dueño; el flujo está montado
  (ver `SF/PROMPT_traspaso_audio_subtitulos.md`). → **Gemini 3.6** una vez el dueño dé el texto.
- **29 clips demasiado largos** para su evento (el peor: `special_escomboy_attack`, 10.70 s
  para un grito de golpe). Se recortan del original, no hay que regrabar.
  → **Gemini 3.6** con los segundos exactos que diga el dueño.
- **5 clips fuera de −16 ±2 LUFS.** → **Gemini 3.6**.
- **Faltan `attack`/`hurt`** en 6 peleadores. → El dueño graba.
- ⚠️ **No actives `voiceSubtitlesEnabled`** hasta que haya paridad ES+EN completa.

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
- **Voces**: 62 de 69 clips sin subtítulo. Requiere el oído del dueño.

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
