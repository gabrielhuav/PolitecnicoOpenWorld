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

### Estado vigente (2026-07-21f) — LÉEME PRIMERO

> **🆕 2026-07-21f (Opus 4.8) — La Llorona: cerrados los 3 recortes malos que quedaban:**
> - **`crouchTurn`**: era ESPEJO, no recorte. `{"flipX": true}` en `crouch-turn-1/2/3` del
>   `_frame_meta.json` de la staging → packer → `SfFrameCatalog` → `StreetFighterScreen`.
>   **Cero Kotlin nuevo**: mecanismo genérico que ya usaban 4 peleadores.
> - **`hurtHead` / `hit-face-1`**: la hoja 09 vieja tenía 14 poses SOLAPADAS y el slicer metía
>   **4 figuras en un solo cuadro**. Hoja regenerada (vieja en `.BAK.png`) → `HURT HEAD 3/3 OK`.
> - **`superArt` `super-4/5/6`**: ya estaba bien en la staging; solo faltaba **empaquetar**.
> - ⚠️ **Trampa del slicer**: `SHEETS` lo comparten los 18 peleadores. La hoja 09 nueva de
>   La Llorona trae **3** poses de HURT HEAD (no 14), así que se añadió `SHEET_OVERRIDES`
>   por `(personaje, hoja)` en vez de tocar la tabla global. **Deuda asumida:** con 3 poses
>   para 4 cuadros, `hit-face-2` y `hit-face-3` son el mismo pixel; si se regenera la hoja
>   con 4+ poses separadas, actualizar el override y quitar la duplicación.
> - **Tarea 2 resuelta**: `super-7` como efecto puro **solo en ESCOMBOY** (Policía CDMX
>   Hombre y Rey Grupero SÍ llevan personaje). No es fallo de recorte, es el arte.
> - ⚠️ **detekt no está a 0**: 5 smells PREEXISTENTES (`CachingWebViewClient`, `NpcAiManager`,
>   `RoadRouter`, `CatSpriteManager` ×2), ajenos a este cambio. Corregir el "0 smells" del
>   traspaso anterior.

### Estado 2026-07-21d

> **🆕 2026-07-21d (Fable) — FATALITY, coleccionables de peleador, WebP y audit MP:**
> - **💀 FATALITY 18/18**: secuencia cinemática compuesta con arte EXISTENTE (súper → su
>   poder → remate → pose). Comando propio: **súper EN CARRERA** con medidor lleno. Daño 70,
>   derriba y el atacante **cruza al otro lado**. En la IA y en el tutorial.
> - **🏆 Arcade DIFÍCIL ya da algo**: el coleccionable del rival (Coleccionables → pestaña
>   **PELEADORES**); "Ver Historia" → "Próximamente". Sin migración de Room (prefijo de id).
> - **📦 WebP lossless**: IMAGES 102 → **88.6 MB**.
> - **🌐 MP**: 2 bugs REALES corregidos (el daño de fatality/súper/agarre no viajaba bien).
>   ⚠️ Falta sincronizar `superMeter` (cosmético).
> - **🗂️ Auditoría visual**: `tools/_audit_sheets/` con TODAS las animaciones de los 18,
>   `_FATALITIES.png` y `_RESUMEN.png`.
> - ⚠️ **Pendiente**: La Tzitzimime/Yoalli pueden tener bonus powers sin recortar.

### Estado 2026-07-21c

> **🆕 2026-07-21c (Fable) — tutorial paso a paso, poses recuperadas y GAMA BAJA:**
> - **⚡ CRÍTICO gama baja:** los atlas llegaron a 2560×7680 = **73 MB de RAM por peleador**
>   en ARGB_8888. Ahora se decodifican a **1/2** en gama baja (`sheetFor(..., sampleSize)`
>   + `sheetScale` en la View) → ~18 MB. ⚠️ IMAGES creció 82→**102 MB**: revisar margen de
>   Play antes del próximo release (WebP lossless daría −25 %, sin aplicar).
> - **Poses recuperadas:** `correr`, `idle-relaxed` y `talk` se recortaban y se tiraban a
>   `_extra/`; ahora son estados (**RUN** con dash-run, se puede saltar/atacar corriendo).
> - **Dificultad por personaje que ESCALA:** `cpuStyleForLevel` acentúa el perfil de cada
>   peleador con el escalón (zoner→más poderes, rusher→más presión y combos).
> - **Tutorial paso a paso:** 21 lecciones básicas (una por movimiento), chips del color del
>   botón real y cartel de error "lo que hiciste → lo que tocaba".

### Estado 2026-07-21b

> **🆕 2026-07-21b (Fable) — COMBOS + TUTORIAL + fix del menú:**
> - **Catálogo de combos data-driven:** `assets/STREETFIGHTER/DATA/combos.json` (10
>   universales + 1 de firma por personaje) → `SfCombos.kt`. Lo usan la **IA** (encola y
>   ejecuta rutas completas) y el **tutorial** (valida paso a paso).
> - **"COMBOS Y TUTORIAL"** en el menú del modo (junto a Práctica / IA vs IA, siempre
>   visible): hoja con TODOS los controles y combos + **tutorial guiado con validación**
>   contra un **muñeco inerte** (sin reloj y sin que el muñeco pierda vida).
> - **Fix menú principal:** las etiquetas ALPHA/BETA ya no pueden saltar de renglón
>   (se quitó el offset vertical negativo + `maxLines=1` en los rótulos). Falta confirmarlo
>   en un S24 real.
> - Verificado: compila, tests en verde, detekt 0 smells, strings ES+EN con paridad.

### Estado 2026-07-21

> **🆕 2026-07-21 (Fable) — MOVESET 3rd Strike COMPLETO (assets + motor):**
> - **179/180 hojas nuevas (20-29) recortadas y empacadas** para los 18 peleadores.
>   Flujo repetible: **`FLUJO_ASSETS_SF.md`** (⭐ empezar por ahí para cualquier asset).
> - **21 estados nuevos jugables:** dash/backdash, bloqueo alto-bajo, **parry**, golpes
>   agachado, antiaéreo, **barrida** (derriba), aéreos, **patada larga**, **overhead**
>   (rompe guardia baja), **agarre→lanzamiento**, burla, **Super Art** con medidor.
>   Tabla de controles y reglas en `DISENO_ARCADE_SF_POW.md` §2026-07-21 y 07 §MOVESET.
> - **Placeholder ALPHA:** si falta una hoja, el movimiento se juega igual con el arte del
>   estudiante del mismo género en silueta negra + rótulo "ALPHA".
> - **Arreglados:** proyectil de La Llorona (se lanzaba a sí misma) y `bonusPower1-6` de
>   La Presidenta (desaparecía 3 cuadros).
> - Verificado: compila, tests en verde, detekt 0 smells. **Falta dispositivo.**

### Estado 2026-07-20

> **🆕 2026-07-20 (Fable) — post-release 1.0.0.12 EN PRODUCCIÓN (Play Store, CI/CD verde):**
> - **PENDIENTES vivos → `PENDIENTES_2026-07-20.md`** (dueño: videos UAM, sprites tandas 5–8
>   con Sol 5.6, oído de audios escom, curar frases; código: probar combos en dispositivo).
> - **🥊 COMBOS 3rd Strike (1er corte):** chain/special cancels + contador "N GOLPES" +
>   escalado de daño. Ver 07 §COMBOS + `DISENO_ARCADE_SF_POW.md` §2026-07-20.
> - **Audit MP3→OGG retomado:** `tools/sf_audio_audit.py` → `tools/sf_audio_audit_report.md`
>   (mapeo real, duración, pitch, transcripción Whisper) + `_audio_review/*.mp3` regenerados.
>   Frase win de La Presidenta corregida. Escom* flagged para oído del dueño.
> - **QA visual de assets:** `tools/sf_contact_sheet.py` + hojas de los 17 peleadores en
>   `tools/_contact_sheets/` (frames de La Llorona empacados están DE FRENTE).
> - **Frases de voz — LUGAR ÚNICO:** `assets/STREETFIGHTER/DATA/voice_phrases.json`
>   (por clip: `es`/`en` curables + `draft` Whisper). Cargador `SfVoicePhrases.kt` +
>   hook en `emitVoiceLines` LISTOS pero APAGADOS (`voiceSubtitlesEnabled=false`).
>   Regenerable con `tools/build_voice_phrases_catalog.py` (conserva lo curado).
> - **Prompt de assets nuevos (hojas 20–29):** `PROMPT_SOL56_TANDAS_NUEVAS.md`.
> - **Purga:** `GUIA_generacion_assets_SF.md` (fondo negro, superada) → `_ARCHIVO/`.

> **ES:** El estado del modo pelea y del resto del juego vive en **00–09** (sobre todo
> **07 §HUELUM VS. GOYA**). Los prompts/checkpoints/pendientes de sesiones pasadas están en
> **`_ARCHIVO/`** → **NO hace falta leerlos** para retomar trabajo (son histórico), salvo el
> **prompt de traspaso IA** si vas a arreglar la CPU.
> **EN:** Live state is in **00–09** (esp. **07 §HUELUM VS. GOYA**). Past prompts under
> **`_ARCHIVO/`** — except the AI handoff prompt if fixing CPU.

**HUELUM VS. GOYA — lo reciente (resumen vivo, detalle en 07 + docs de trabajo):**
- Modos: **ARCADE** (default) / PRÁCTICA / **IA VS IA** / MULTIJUGADOR (Render / BT / LAN).
- **Arcade:** peleadór → **Fácil/Medio/Difícil** → escalera 15. Mapas = hogar del **rival** +
  luz (día / noche / apocalipsis). Tabla peleadór→mapa: **`SF_STAGES_MAPS_UNLOCK.md`**.
- **Desbloqueos:** peleadór + **3 luces** de su mapa (`SfArcadeRepository`); práctica/MP host
  solo mapas desbloqueados.
- **Roster arcade:** 18 dedicados. **NO** arcade: Lázaro / Granadero genérico / Paramédico
  genérico (alpha+shared).
- **Gama baja:** tick ~30 fps, atlas ≤2048, thumbs, CARGANDO, sesión arcade al pausar.
- **SFX especiales:** 21/21 en assets (pack viejo). **Nueva pasada diarizada** en
  `tools/sf_voice_scrape/out_diarized/` + catálogo `DATA/special_phrases.json` + subtítulos HUD
  (`emitSpecialVoice`). Fuentes YT y links: **`SF_SPECIAL_VOICES_SFX.md`** (2026-07-18).
  **Deepfake lab: NO hecho.** Pap5 oficial age-gate pendiente. Lázaro sin frase special.
- **Presidenta** ≤1/4 vida → meta real a **Yoalli** (50% HP).
- **IA 2026-07-18i:** reescritura (clinch break, mundo-space, smartCpuDecision, watchdog).
  Si en dispositivo aún falla: **`_ARCHIVO/PROMPT_traspaso_IA_CPU_2026-07-18.md`**.
  Handoff Fable: **`_ARCHIVO/PROMPT_traspaso_Fable_2026-07-18_voces.md`**
  (**prioridad = IA quietos/mismo ataque + Showcase**; voces solo contexto al final).
- **✅ IA + Showcase 2026-07-18j (Fable):** ofensiva por ESTADO real (no intención), clinch
  con roles asimétricos, `variedCpuAttack` (sin repetir golpe); showcase COMPLETO (giros,
  HURT, KO, VICTORY, metamorfosis) + **auditoría estática** de anims/frames/.ogg → reporte.
  Detalle: 07 §HUELUM + `DISENO_ARCADE_SF_POW.md` §18j. **Pendiente: Rebuild + dispositivo.**
- **✅ Showcase v2 2026-07-18k (Fable, feedback dueño):** avance automático + botón SALTAR
  (`sf_showcase_skip`), mapa HOGAR por peleadór (`gauntletMapFile`), audio en pasos forzados
  (hits/KO/voz en VICTORY-metamorfosis; VICTORY con voz también en pelea real), fix salto
  perdido. Su nota de audio pendiente es histórica; Release 1/9 abajo la supera. Detalle: 07
  §HUELUM + DISENO §18k.
- **✅ Release 1/9 2026-07-18 (Sol):** audio final **21/21 solo español**, cortes locales de
  duración individual, contenido hablado verificado con Whisper y hashes reproducibles; Lázaro
  incluido, Presidenta 8.3 s y banda Granadero completa 27.5 s. Los especiales largos migran a
  `MediaPlayer`. La nota “deepfake pendiente” de 18k queda SUPERADA: se usan voces auténticas de
  las fuentes locales y síntesis únicamente para el Robot ficticio. Arte: HURT únicos de Llorona
  + metamorfosis inversa Yoalli→Presidenta. Auditoría IA: 9 campañas/135 peleas aceleradas y 600
  configuraciones estructurales. Play Store: `versionCode 12`, `versionName 1.0.0.12`.
- **✅ Hotfix de entrega 1.0.0.12 (Sol):** el primer upload llegó firmado pero Play rechazó
  `base` por superar 500 MB. Los 48 atlas de mapas pasaron PNG→WebP lossless con hash RGBA
  idéntico y 29 fondos fijos sin referencias se archivaron en `_ORPHAN_ASSETS`; AAB real
  **434.47 MiB**, `base` comprimido **433.84 MiB**. SALTAR termina peleador+timer. CI usa
  Actions Node 24, `tracks`, notas ES/EN y entrega AAB firmado; label `manual-play-upload`
  omite solo el upload automático.

- **✅ Hotfix animación/showcase/Llorona (Sol):** los 48 fondos se regeneraron como WebP
  lossless con 15 cuadros únicos distribuidos en 5 s y playback 6 fps: **16 día + 16 noche +
  16 noche tenebrosa**, todos con `logoPOW.png` aplicado por frame. Los auxiliares fijos salen a
  `additional_assets/`, fuera del proyecto Android compilable. Showcase separa siguiente
  animación/personaje, velocidad 1×/2×/4×, repetición de voz y recorrido audio-only 21/21.
  La hoja 09 de La Llorona ahora separa 14 poses pegadas; `hit-face-*` ya no contiene dos
  cuerpos y el validador detecta cuerpos fusionados. `bundleRelease` final: **438.52 MiB**,
  `base` comprimido **459.16 MB**, margen Play **40.84 MB**.

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
| **`SF_SPECIAL_VOICES_SFX.md`** | Voces/SFX v3: 21/21 español, cortes locales, Whisper, hashes, `MediaPlayer`; v1/v2 queda histórico. **⚠️ TRABAJO FUTURO (humano): subtítulos de frases DESACTIVADOS** (`voiceSubtitlesEnabled=false`) porque los audios nuevos ya no coinciden con el texto — re-transcribir las 21 frases y reactivar. Ver la caja al inicio de ese doc. |
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

**Actualización SF 2026-07-18 (Fix 18l):** la IA de `StreetFighterViewModel` es compartida por
Arcade/VS/IA-vs-IA/Autoplay; se corrigió el aterrizaje eterno en `JUMP_*`, la orientación tras
cruces, hitboxes BODY/LEGS, variedad/defensa/cooldowns y el bucle de hit-stun. El auditor ahora
falla por pasividad o timeout y valida 306 cruces everyone-vs-everyone; detalle en `07` y
`DISENO_ARCADE_SF_POW.md`.
