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

**ES:**
1. Sube/pega esta carpeta completa (o solo los archivos relevantes) al asistente.
2. Pídele la tarea y dile que **siga el MVVM y las convenciones del archivo 09**.
3. Si el asistente necesita un archivo concreto, búscalo en la tabla "Key files" (archivo 04/05)
   y pásale solo ese.
4. **Tras cualquier cambio, actualiza estos docs (00–09)** y, si es user-facing, el README **público** de la raíz del repo (ver 09).

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
| 07 | `07_OTHER_FEATURES.md` | Menú principal, ajustes, ShineCTO, coleccionables |
| 08 | `08_SERVERS.md` | Servidores Node.js (open world v3 + zombi) + protocolo de red |
| 09 | `09_CONVENTIONS_GOTCHAS.md` | Convenciones, reglas de gama baja, protocolo de actualización de docs |

### Docs de trabajo / Working docs (no son 00–09; tareas en curso)

| Archivo / File | Contenido / Contents |
|---|---|
| `PROMPT_nueva_optimizacion.md` | Prompt maestro de reuso: contexto + estado actual + reglas del entorno. Cópialo al iniciar una sesión con una IA y adjunta esta carpeta. |
| `ANALISIS_codigo.md` | **(2026-06-21)** Informe: clases grandes, duplicación (metro⇄metrobús), MVVM, i18n migrado vs pendiente, perf, mejoras priorizadas por valor/esfuerzo. |
| `REVISION_repo.md` | **(2026-06-21)** Revisión de TODO el repo: raíz, servidores Node (solo revisión), seguridad (keystore NO comprometido), `.gitignore`, hardening de deploy. |

---

## Datos rápidos / Quick facts

- **Package root:** `ovh.gabrielhuav.pow`
- **Lenguaje / Language:** Kotlin + Jetpack Compose + Material 3
- **Arquitectura / Architecture:** MVVM estricto por *feature* / strict MVVM by feature
- **Servidores / Servers:** 2× Node.js + `ws` (open world `Multiplayer/`, zombi `MultiplayerInteriores/`), dockerizados en Render
- **Room DB:** versión 8 (`MIGRATION_7_8` + destructive fallback)
- **~120 archivos Kotlin / Kotlin files**, ~36k líneas / lines (2026-06-21). Solo 5 archivos >1000:
  `WorldMapViewModel`(2114), `NativeOsmMap`(1460), `WorldMapScreen`(1326), `MainActivity`(1064), `ZombieGameScreen`(1035)
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
