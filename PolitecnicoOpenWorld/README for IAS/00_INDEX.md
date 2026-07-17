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
2. Este índice (mapa de archivos) + `09_CONVENTIONS_GOTCHAS.md` COMPLETO.
3. El doc del feature que vayas a tocar (03-08 / CAMPAIGN) y su tabla "Key files".
4. Si vas a REFACTORIZAR: `CHECKPOINT_SENIOR_refactor.md` (programa 2026-07-04 TERMINADO Y
   AUDITADO: managers+fachada, Hilt, tests, detekt — ahí está la receta y lo que NO se movió).
   Los `PLAN_*.md` y demás docs de `_ARCHIVO/` están ✅ EJECUTADOS: referencia histórica, NO tareas.
5. Pídele la tarea y dile que **siga el MVVM y las convenciones del archivo 09** (incluida la
   política de comentarios y los campos "⚠️ LO POSEE XManager").
6. Si el asistente necesita un archivo concreto, búscalo en la tabla "Key files" (archivo 04/05)
   y pásale solo ese.
7. **Tras cualquier cambio, actualiza estos docs (00–09)** y, si es user-facing, el README **público** de la raíz del repo (ver 09). Los tests (84, `app/src/test`) deben seguir en verde.

**EN:**
1. Upload/paste this whole folder (or just the relevant files) to the assistant.
2. Give it the task and tell it to **follow MVVM and the conventions in file 09**.
3. If it needs a specific source file, find it in the "Key files" table (file 04/05) and pass
   only that one.
4. **After any change, update these docs (00–09)** and, if user-facing, the **public** root README (see 09).

---

## Mapa de archivos / File map

| # | Archivo / File | Contenido / Contents |
|---|---|---|
| 00 | `00_INDEX.md` | Este índice + prompt de reuso / This index + reuse prompt |
| 01 | `01_ARCHITECTURE.md` | Visión general, MVVM, navegación, build, stack / Overview, MVVM, navigation, build, stack |
| 02 | `02_DATA_LAYER.md` | Room (DB v8), DAOs, entidades, cachés, repos, red / Room, DAOs, entities, caches, repos, network |
| 03 | `03_DOMAIN_MODELS.md` | Modelos puros + IA (NpcAiManager, PoliceManager, PrankedyManager) + modelos zombi |
| 04 | `04_MAP_EXTERIOR.md` | Open world: WorldMapViewModel + parciales, estado, render, policía |
| 05 | `05_ZOMBIE_MINIGAME.md` | Minijuego zombi: VM, tick offline/online, constantes, render, diseñador |
| 06 | `06_INTERIOR_METRO.md` | Interiores ESCOM + metro + CollisionGrid |
| 07 | `07_OTHER_FEATURES.md` | Menú principal, ajustes, ShineCTO, coleccionables, 🥊 HUELUM VS. GOYA (modo pelea 1v1; en CÓDIGO los ids siguen siendo street_fighter/Sf*) |
| 08 | `08_SERVERS.md` | Servidores Node.js (open world v3 + zombi) + protocolo de red |
| 09 | `09_CONVENTIONS_GOTCHAS.md` | Convenciones, reglas de gama baja, protocolo de actualización de docs |

### Docs de trabajo / Working docs (no son 00–09)

| Archivo / File | Contenido / Contents |
|---|---|
| `GUIA_mantenimiento_no_senior.md` | **(2026-07-04) EMPEZAR AQUÍ si eres una IA/dev nuevo:** las 7 reglas que rompen el juego, flujo de trabajo estándar, chuleta de "dónde vive cada cosa", qué NO hacer sin compilador y cómo pedir compilaciones al dueño. |
| *(estado actual)* | **Para RETOMAR (2026-07-16):** el estado vigente vive en los docs 00–09 (el modo pelea en **07 §HUELUM VS. GOYA** + `AUDIT_SF_MULTIPLAYER.md`). Los checkpoints de sesiones pasadas están ✅ ejecutados y viven en `_ARCHIVO/`. |
| `PENDIENTES_SF_2026-07-16.md` | **🆕 PENDIENTES REALES del modo pelea (2026-07-16):** ① IA con dificultad ✅ hecha (falta probar); ② BUG stun-lock online/BT (machacar botón mata sin defensa) con diseño de fix; ③ BUG sincronización de la REVANCHA online; ④ SERVIDOR LOCAL: autodescubrir la sala por UDP + pedir código (no teclear IP). **EMPEZAR AQUÍ la próxima sesión de SF.** |
| `PROMPT_traspaso_2026-07-17.md` | **🆕 PROMPT para cambiar de cuenta/PC (2026-07-17):** pégalo como primer mensaje en la nueva sesión. Estado actual del modo pelea (arcade, PESADILLA, modo desarrollador, personajes completos salvo Robot) + **TAREA TOP: bug de cambio de TAMAÑO de los peleadores al hacer acciones** (causa raíz + 3 opciones de fix). |
| `DISENO_ARCADE_SF_POW.md` | **🆕 DISEÑO del modo ARCADE POW (2026-07-16):** quitar copyright (RYU/KEN), TODOS los personajes bloqueados y se desbloquean al derrotarlos en una escalera de dificultad creciente; penúltimo = Paramédico Cruz Roja, final = Rey Grupero (provisional); escalera solo con los 8 bien implementados (ALPHA excluidos). Roster real, cambios por archivo y decisiones pendientes. **Nada implementado aún.** |
| `CHECKPOINT_SENIOR_refactor.md` | **✅ (2026-07-04) Programa "calidad senior" TERMINADO Y AUDITADO** (tests golden-master → de-dup routing → 6 managers + fachada combine → Hilt → detekt bloqueante → perf auditada). Para futuros refactors: la RECETA del patrón manager vive aquí. |
| `ASSETS_STREETFIGHTER_MIGRACION.md` | **(2026-07-09)** Modo pelea 1v1: qué assets del clon SF hay que sustituir por assets propios de POW (Prankedy…), formato del JSON de personaje (77 poses), pipeline de empaquetado y prompts para QWEN/ChatGPT. |
| `AUDIT_SF_MULTIPLAYER.md` | **🆕 (2026-07-11)** Multijugador 1v1 del modo pelea: audit del server `MultiplayerSF/` (QWEN corregido), protocolo completo, decisiones de autoridad, deploy GRATIS en Render y qué probar. |
| `GUIA_generacion_assets_SF.md` | **(2026-07-10)** Guía MANUAL cuadro-por-cuadro (QWEN no pudo; se harán en ChatGPT): las 17 poses malas de Prankedy, los 13 ataques de cada ALPHA, escenario ESCOM por capas, HUD por piezas, sonidos, prompts y orden de integración. |
| `NPC_SPRITES_PIPELINE.md` | Cómo recortar sprite sheets de NPCs al ESTÁNDAR (193×249, body 200, fracción 0.803) + cableado (`PlayerSkin`/`AMBIENT_SKINS`/`devOnlySkins`) + gotchas. Script: `tools/slice_npc_standard.py`. Léelo para agregar más docentes/estudiantes/genéricos en otra PC. |
| `CAMPAIGN/` (carpeta) | **Guion de la campaña (Modo Historia): `00_OVERVIEW.md` (fantasía = simulación de infección zombi + GTA), `01..03_MISSION_N.md` (Misiones 1-3, ✅) y `04_SIDE_MISSIONS.md` (secundarias side1/side2 con recompensa en DINERO). Futuras misiones = `0N_MISSION_N.md`.** |
| `_ARCHIVO/` (carpeta) | **Docs históricos ✅ EJECUTADOS o SUPERADOS** (los `PLAN_*` del programa senior, `PENDIENTE_calidad`, `ANALISIS_codigo`, `PROMPT_nueva_optimizacion` y los checkpoints viejos). El código los cita por nombre como registro; **NO son tareas pendientes**. Ver su `README.md`. |

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
- **🆕 Assets COMPARTIDOS SF⇄mundo (2026-07-15/17):** 3 de los 18 peleadores de "HUELUM VS. GOYA"
  se arman EN RUNTIME desde los sets del mundo (`SPRITES/PLAYER|NPC/`) — sin sheets duplicados
  en el APK: Lázaro, Granadero y Paramédico. Los otros 13 POW tienen hojas croma
  dedicadas; Ryu/Ken solo existen en debug. Ver 07 y 09 §12.
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
