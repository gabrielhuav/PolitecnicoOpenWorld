# README for IAS — Índice / Index

> **ES:** Esta carpeta es el **contexto comprimido** de *Politécnico Open World (POW)* para
> pasárselo a un asistente de IA (Gemini, Claude Free, etc.) **en lugar de subir todo el
> código**. Cada archivo documenta una parte del proyecto con detalle profundo: propósito,
> archivos, clases, funciones con firmas reales, campos de estado, constantes, protocolo de
> red y *gotchas*. Con leer esto basta para programar nuevas funcionalidades sin ver el código.
>
> **EN:** This folder is the **compressed context** of *Politécnico Open World (POW)*, meant to
> be handed to an AI assistant (Gemini, Claude Free, etc.) **instead of uploading the whole
> codebase**. Each file documents a part of the project in depth: purpose, files, classes,
> functions with real signatures, state fields, constants, wire protocol, and gotchas. Reading
> this is enough to build new features without seeing the source.

---

## Cómo usar / How to use

**ES (ORDEN DE LECTURA para una IA nueva — sobre todo si es poco potente):**
1. `GUIA_mantenimiento_no_senior.md` ← EMPIEZA AQUÍ (las 7 reglas + chuleta + qué NO hacer).
2. **`10_ARQUITECTURA_SEPARACION.md`** (dónde vive cada cosa) + `09_CONVENTIONS_GOTCHAS.md` COMPLETO.
3. El doc del feature que vayas a tocar (03-08 / CAMPAIGN) y su tabla "Key files".
4. Si vas a REFACTORIZAR: `CHECKPOINT_SENIOR_refactor.md` (programa 2026-07-04 TERMINADO Y
   AUDITADO: managers+fachada, Hilt, tests, detekt — ahí está la receta y lo que NO se movió).
   Los `PLAN_*.md` y demás docs de `_ARCHIVO/` están ✅ EJECUTADOS: referencia histórica, NO tareas.
5. Pídele la tarea y dile que **siga el MVVM y las convenciones del archivo 09** (incluida la
   política de comentarios y los campos "⚠️ LO POSEE XManager").
6. Si el asistente necesita un archivo concreto, búscalo en la tabla "Key files" (archivo 04/05)
   y pásale solo ese.
7. **Tras cualquier cambio, actualiza estos docs (00–10)** y, si es user-facing, el README **público** de la raíz del repo (ver 09). Los **216 tests** (112 en `:app` + 104 en `:shared`) deben seguir en verde.

**EN:**
1. Upload/paste this whole folder (or just the relevant files) to the assistant.
2. Give it the task and tell it to **follow MVVM and the conventions in file 09**.
3. If it needs a specific source file, find it in the "Key files" table (file 04/05) and pass
   only that one.
4. **After any change, update these docs (00–10)** and, if user-facing, the **public** root README (see 09).

---

## Mapa de archivos / File map

> **🆕 (2026-07-21) La carpeta está dividida por ARQUITECTURA.** El juego tiene **dos modos
> principales** y su documentación ya no se mezcla:
>
> | Carpeta | Modo |
> |---|---|
> | **`MUNDO/`** | 🌎 **Mundo libre POW** — open world, misiones, zombis, interiores, servidores |
> | **`SF/`** | 🥊 **Peleas "Huelum vs. Goya"** — empieza por `SF/00_SF_INDEX.md` |
> | raíz | lo COMPARTIDO por ambos (arquitectura, datos, convenciones) |
>
> **➡️ Empieza SIEMPRE por [`_SESION_ACTUAL.md`](_SESION_ACTUAL.md):** estado vivo del trabajo,
> qué está pendiente y a qué IA conviene delegar cada cosa.

> ### 🧠 Cómo funciona la memoria entre IAs (LÉELO SI VAS A TRABAJAR AQUÍ)
>
> A este proyecto entran **varias IAs distintas** (Opus, Fable, Sol, Gemini…) y **ninguna
> recuerda nada** de otra sesión ni de otro asistente. La memoria del proyecto es esta carpeta,
> y se divide en tres capas con reglas distintas:
>
> | Capa | Archivo | Regla |
> |---|---|---|
> | **Memoria de trabajo** | `_SESION_ACTUAL.md` | **Ventana de 2 días · máx. 200 líneas.** Lo único que se lee SIEMPRE. Lo que pase de ahí se purga. |
> | **Conocimiento estable** | `00`–`09`, `MUNDO/`, `SF/` | Se ACTUALIZA cuando cambia el código. No lleva historia de sesiones. |
> | **Histórico** | `_ARCHIVO/` | Solo lectura, para arqueología. **NO son tareas.** |
>
> **La regla que hace que esto funcione:** `_SESION_ACTUAL.md` se lee en cada arranque, así que
> cada KB de más se paga en tokens **todas las veces**. Mantenerlo corto no es estética: es lo
> que deja presupuesto para trabajar. Al cerrar sesión, **purga a `_ARCHIVO/` lo que ya pasó de
> 2 días** y deja solo el estado vivo. El procedimiento exacto está en su propia cabecera.

### Compartido (raíz)

| # | Archivo / File | Contenido / Contents |
|---|---|---|
| — | `_SESION_ACTUAL.md` | **🧠 MEMORIA COMPARTIDA ENTRE IAs.** Ver el recuadro de abajo: es el ÚNICO punto de traspaso entre asistentes. **Ventana de 2 días, techo de 200 líneas.** Se actualiza SIEMPRE antes de cerrar. |
| 00 | `00_INDEX.md` | Este índice + prompt de reuso / This index + reuse prompt |
| 01 | `01_ARCHITECTURE.md` | Visión general, MVVM, navegación, build, stack / Overview, MVVM, navigation, build, stack |
| 02 | `02_DATA_LAYER.md` | Room (DB v8), DAOs, entidades, cachés, repos, red / Room, DAOs, entities, caches, repos, network |
| 07 | `07_OTHER_FEATURES.md` | Menú principal, ajustes, ShineCTO, coleccionables (+ 🥊 SF; ⚠️ su parte de SF debería migrar a `SF/`) |
| 09 | `09_CONVENTIONS_GOTCHAS.md` | Convenciones, reglas de gama baja, protocolo de actualización de docs |
| 10 | `10_ARQUITECTURA_SEPARACION.md` | 🧭 **¿EN QUÉ ARCHIVO TOCO ESTO?** Mapa de la separación tras el refactor de la Fase 5: los 2 módulos, MVVM, el patrón PARCIAL, tabla de "quiero cambiar X → archivo Y" y los 6 errores que más caro salen. **Pensado para que hasta una IA pequeña pueda trabajar aquí.** |
| — | `PLAYSTORE_formulario_seguridad_datos.md` | 🛡️ **Play Store: formulario de Seguridad de los datos + políticas.** Valores EXACTOS aprobados, errores que nos rechazaron y checklist antes de cada envío. **Léelo antes de tocar la ficha o subir versión.** |
| — | `ARRANQUE_MAC_iOS.md` | 🍏 **EMPIEZA AQUÍ si estás en el MAC.** Guion exacto de la primera compilación de `:shared` para iOS: rutas, JDK, comandos y dónde va a fallar. Las Fases 1-4 se hicieron en Windows, donde iOS NO compila. |
| — | `PLAN_MIGRACION_KMP.md` | 🍏 **Migración a Kotlin Multiplatform / iOS — el plan global.** Acoplamiento MEDIDO, estado de las libs KMP, decisión del mapa y qué NO se puede portar. **Estado: Fases 0-4 COMPLETAS y verdes; falta la 5 (la UI) y la 6 (assets).** |
| — | `SETUP_PC_NUEVA.md` | 🖥️ **Poner el repo a compilar en una PC Windows nueva.** Los 4 archivos que NO viajan por git (⚠️ `gradle-wrapper.jar` bloquea hasta `gradlew`), la prueba de humo y cómo se verifica iOS desde Windows. |
| — | `PLAN_SF_EN_iOS.md` | 🥊🍏 **Cómo hacer que el modo pelea corra en iOS** — el trozo concreto de la Fase 5. Bloqueadores contados archivo por archivo y 6 pasos, cada uno verificable en el simulador. ⚠️ **Se ejecuta EN EL MAC.** |

### 🌎 `MUNDO/` — mundo libre POW

| # | Archivo / File | Contenido / Contents |
|---|---|---|
| 03 | `MUNDO/03_DOMAIN_MODELS.md` | Modelos puros + IA (NpcAiManager, PoliceManager, PrankedyManager) + modelos zombi |
| 04 | `MUNDO/04_MAP_EXTERIOR.md` | Open world: WorldMapViewModel + parciales, estado, render, policía |
| 05 | `MUNDO/05_ZOMBIE_MINIGAME.md` | Minijuego zombi: VM, tick offline/online, constantes, render, diseñador |
| 06 | `MUNDO/06_INTERIOR_METRO.md` | Interiores ESCOM + metro + CollisionGrid |
| 08 | `MUNDO/08_SERVERS.md` | Servidores Node.js (open world v3 + zombi) + protocolo de red |
| — | `MUNDO/CAMPAIGN/` | Campaña: overview + misiones 1-3 + secundarias |
| — | `MUNDO/NPC_SPRITES_PIPELINE.md` | Pipeline de sprites de NPC |

### 🥊 `SF/` — peleas "Huelum vs. Goya"

Índice propio en **`SF/00_SF_INDEX.md`** (diseño, assets gráficos, audio, herramientas y las
reglas que más caro han salido). En el CÓDIGO el modo se sigue llamando `street_fighter`/`Sf*`.

### Estado vigente — dónde mirar

> **El estado VIVO vive en [`_SESION_ACTUAL.md`](_SESION_ACTUAL.md).** Empieza SIEMPRE ahí.
>
> El changelog acumulado (2026-07-20 → 07-22c: moveset 3rd Strike, fatality, combos, tutorial,
> subtítulos, STUN, crash de La Llorona, gama baja, WebP, motor Fase 1/2a/2b…) se archivó en
> [`_ARCHIVO/HISTORIAL_changelog_00_INDEX.md`](_ARCHIVO/HISTORIAL_changelog_00_INDEX.md) para
> no pagarlo en tokens en cada sesión. El diseño de SF está en `SF/DISENO_ARCADE_SF_POW.md`.

**HUELUM VS. GOYA — lo estable:**
- Modos: **ARCADE** (default) / PRÁCTICA / **IA VS IA** / MULTIJUGADOR (Render / BT / LAN / P2P).
- **Arcade:** peleador → Fácil/Medio/Difícil → escalera 15. Mapas = hogar del **rival** + luz.
  Tabla peleador→mapa: **`SF_STAGES_MAPS_UNLOCK.md`**.
- **Roster:** 18 dedicados. NO arcade: Lázaro / Granadero / Paramédico (alpha+shared).
- **Presidenta** ≤1/4 vida → metamorfosis. **Gama baja:** tick ~30 fps, atlas ≤2048, CARGANDO.

### Docs de trabajo / Working docs (no son 00–09)

| Archivo / File | Contenido / Contents |
|---|---|
| **`FLUJO_ASSETS_SF.md`** | **⭐ FLUJO COMPLETO de assets de pelea: identificar → recortar → empacar → QA + trampas conocidas.** |
| **`PENDIENTES_2026-07-20.md`** | **⭐ Lista VIVA de deudas post-release 1.0.0.12 (dueño vs código).** |
| `GUIA_mantenimiento_no_senior.md` | **EMPEZAR AQUÍ si eres IA/dev nuevo:** 7 reglas, chuleta, qué NO hacer. |
| `DISENO_ARCADE_SF_POW.md` | Diseño + avance del ARCADE POW (escalera, desbloqueos, IA por fases). |
| **`SF_STAGES_MAPS_UNLOCK.md`** | **16 mapas × 3 luces, peleadór→hogar (tabla dueño), desbloqueos MP.** |
| **`QA_SF_STAGES_2026-07-18.md`** | **QA de fondos (dueño): encuadre `SfBgFraming` (5 mapas nuevos ✅ + salto), pendientes: sombra Isla Muñecas, video día FES Acatlán, subtítulos que se salen.** |
| **`AUDIO_INVENTARIO_SF.md`** | **Qué audio tiene cada peleadór, globales vs por-peleadór, qué borrar, y el fix de tamaño AAB (atlas lossless→lossy 221→82 MB). Script `tools/sf_audio_review.sh`.** |
| **`PROMPT_panoramico_todos_los_mapas.md`** | **Trabajo FINAL diferido: aplicar el encuadre panorámico + salto a los 16 mapas, uno por uno. Prompt autónomo con todo lo necesario.** |
| `AUDIT_SF_MULTIPLAYER.md` | Multijugador 1v1: protocolo, server `MultiplayerSF/`, BT/LAN. |
| `ASSETS_STREETFIGHTER_MIGRACION.md` | Pipeline assets pelea (JSON, pack, migración SF→POW). |
| **`SF_SPECIAL_VOICES_SFX.md`** | Voces/SFX v3: 21/21 español, cortes locales, Whisper, hashes, `MediaPlayer`; v1/v2 queda histórico. ✅ **2026-07-22: subtítulos de frases ACTIVOS** (`voiceSubtitlesEnabled=true`; frases curadas en `voice_phrases.json`, 64 `es` + `en` completo). |
| `GUIA_regeneracion_sprites_croma.md` | Regenerar sprites croma pelea+mundo (proceso VIGENTE de recorte). |
| **`PROMPT_SOL56_TANDAS_NUEVAS.md`** | **⭐ Prompt de las 10 hojas NUEVAS (20–29, moveset 3rd Strike) por personaje + rutas de assets + estándar de calidad.** |
| `NPC_SPRITES_PIPELINE.md` | Recorte estándar de NPCs del mundo. |
| `CHECKPOINT_SENIOR_refactor.md` | Receta del patrón manager/Hilt/detekt (referencia). |
| `CAMPAIGN/` | Guion Modo Historia (misiones 1–3 + side). |
| `_ARCHIVO/` | Histórico + prompts: **`PROMPT_traspaso_Gemini_2026-07-18_audio.md`** (SIGUIENTE SESIÓN: sistema de voces + pendiente "ZA ZA" grito masculino + policías), `PROMPT_traspaso_GPT56_2026-07-18_git_audio.md`, `PROMPT_traspaso_Fable_2026-07-18_voces.md`. |

---

## Datos rápidos / Quick facts

- **Package root:** `ovh.gabrielhuav.pow`
- **Lenguaje / Language:** Kotlin + Jetpack Compose + Material 3
- **Arquitectura / Architecture:** MVVM estricto por *feature* / strict MVVM by feature
- **Servidores / Servers:** 2× Node.js + `ws` (open world `Multiplayer/`, zombi `MultiplayerInteriores/`), dockerizados en Render
- **Room DB:** versión 8 (`MIGRATION_7_8` + destructive fallback)
- **~229 archivos Kotlin / Kotlin files**, ~47k líneas / lines (2026-07-16). 8 archivos >1000 (ninguno >2100):
  `StreetFighterViewModel`(~2145, creció con multijugador BT/LAN+rondas+red SESIÓN 4+IA con 3 dificultades),
  `ZombieGameScreen`(1591), `WorldMapViewModel`(1583), `WorldMapScreen`(1460), `NativeOsmMap`(1458),
  `StreetFighterScreen`(~1767 con rondas/LAN/selector de dificultad), `ZombieInteriorViewModel`(1137), `AppNavGraph`(1093)
- **🆕 Assets COMPARTIDOS SF⇄mundo (2026-07-15/17):** 3 de los 22 peleadores de "HUELUM VS. GOYA"
  se arman EN RUNTIME desde los sets del mundo (`SPRITES/PLAYER|NPC/`) — sin sheets duplicados
  en el APK: Lázaro, Granadero y Paramédico. Los otros 17 POW tienen hojas croma
  dedicadas; Ryu/Ken **BORRADOS del repo (2026-07-26)** — eran huérfanos (no están en `SfFighterId`).
  Ver 07 y 09 §12.
- **Default map provider:** `CARTO_VOYAGER` (web, tiles reales hasta z20 / real tiles up to z20; no persistido / not persisted)
- **Auth / Autenticación:** Firebase Auth (Google Sign-In) en `data/auth/` (`AuthManager`, `AuthSession`).
  Obligatoria para multijugador; local/Modo Historia sin login. Ambos servidores verifican el ID token
  (`auth.js`, modo suave por `AUTH_REQUIRED`). Política de privacidad: `policy_en_es.html` (raíz del repo;
  publicada en GitHub Pages, enlazada desde Ajustes → Cuenta).
- **Comentarios del código en español / Code comments in Spanish** (mantener / keep that style)

---

## Prompt de reuso / Reuse prompt

> **ES:** "Lee la carpeta `README for IAS` (ese es todo el contexto del proyecto POW).
> Implementa <tarea> siguiendo el patrón MVVM y las convenciones del archivo 09. No me pidas
> más código a menos que un archivo citado en las tablas 'Key files' (04/05) falte. Al
> terminar, dime qué líneas de estos docs (00–09) hay que actualizar (y del README público de la
> raíz si el cambio es user-facing)."
>
> **EN:** "Read the `README for IAS` folder (that is the full context of the POW project).
> Implement <task> following the MVVM pattern and the conventions in file 09. Don't ask me for
> more code unless a file referenced in the 'Key files' tables (04/05) is missing. When done,
> tell me which lines of these docs (00–09) to update (and the public root README if user-facing)."

---

## Relación con los otros docs / Relationship to the other docs

**ES:** La RAÍZ del repo tiene un `README.md` **público** (bilingüe, orientado a humanos) que es la
visión general. Esta carpeta (`00`–`09`) es la versión **granular y por archivo** para alimentar a un
asistente de IA: incluye firmas de funciones, campos de estado, pseudocódigo y *gotchas* que el README
público no lista. *(Antes había aquí copias `README.md` (136 KB) y `plan.artifact.md` redundantes con
`00`–`09`; se eliminaron el 2026-06-22 para no mantener triplicado.)* Si hay contradicción, **el código
manda**; luego sincroniza estos docs y, si es user-facing, el README público de la raíz.

**EN:** The repo ROOT has a **public** `README.md` (bilingual, human-oriented) = the overview. This folder
(`00`–`09`) is the **granular, per-file** version meant to be fed to an AI assistant: it includes function
signatures, state fields, pseudocode and gotchas the public README doesn't. *(Redundant `README.md` (136 KB)
and `plan.artifact.md` copies were removed from here on 2026-06-22.)* On contradiction, **the code wins**;
then sync these docs and, if user-facing, the public root README.

**Actualización SF 2026-07-18 (Fix 18l):** la IA de `StreetFighterViewModel` es compartida por
Arcade/VS/IA-vs-IA/Autoplay; se corrigió el aterrizaje eterno en `JUMP_*`, la orientación tras
cruces, hitboxes BODY/LEGS, variedad/defensa/cooldowns y el bucle de hit-stun. El auditor ahora
falla por pasividad o timeout y valida 306 cruces everyone-vs-everyone; detalle en `07` y
`DISENO_ARCADE_SF_POW.md`.
