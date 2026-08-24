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
   Si vas a tocar código de las DOS plataformas: **`11_SEPARACION_IOS_ANDROID.md`**.
3. El doc del feature que vayas a tocar (03-08 / CAMPAIGN) y su tabla "Key files".
4. Si vas a REFACTORIZAR: `CHECKPOINT_SENIOR_refactor.md` (programa 2026-07-04 TERMINADO Y
   AUDITADO: managers+fachada, Hilt, tests, detekt — ahí está la receta y lo que NO se movió).
   Los `PLAN_*.md` y demás docs de `_ARCHIVO/` están ✅ EJECUTADOS: referencia histórica, NO tareas.
5. Pídele la tarea y dile que **siga el MVVM y las convenciones del archivo 09** (incluida la
   política de comentarios y los campos "⚠️ LO POSEE XManager").
6. Si el asistente necesita un archivo concreto, búscalo en la tabla "Key files" (archivo 04/05)
   y pásale solo ese.
7. **Tras cualquier cambio, actualiza estos docs (00–13)** y, si es user-facing, el README **público** de la raíz del repo (ver 09). Los **429 tests** (119 `:app` + 155 `:shared` Android + 155 `:shared` iOS) deben seguir en verde.

**EN:**
1. Upload/paste this whole folder (or just the relevant files) to the assistant.
2. Give it the task and tell it to **follow MVVM and the conventions in file 09**.
3. If it needs a specific source file, find it in the "Key files" table (file 04/05) and pass
   only that one.
4. **After any change, update these docs (00–13)** and, if user-facing, the **public** root README (see 09).

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
> | **Conocimiento estable** | `00`–`13`, `MUNDO/`, `SF/`, `../iosApp/README.md` | Se ACTUALIZA cuando cambia el código. No lleva historia de sesiones. |
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
| 11 | `11_SEPARACION_IOS_ANDROID.md` | 🧭 **iOS y Android: dónde va cada cosa. EMPIEZA AQUÍ si vas a tocar código que corre en las dos.** Árbol de decisión (¿commonMain, `expect/actual` o Controller?), las 10 costuras que existen, los 4 controllers, dónde vive la navegación de cada plataforma, qué se queda en `:app` y por qué, gama baja, y **las 6 trampas que solo se ven abriendo el simulador** (§8bis). **Escrito para alguien que acaba de entrar al equipo.** |
| — | `PLAYSTORE_formulario_seguridad_datos.md` | 🛡️ **Play Store: formulario de Seguridad de los datos + políticas.** Valores EXACTOS aprobados, errores que nos rechazaron y checklist antes de cada envío. **Léelo antes de tocar la ficha o subir versión.** |
| — | `PLAN_MIGRACION_KMP.md` | 🍏 **Migración a Kotlin Multiplatform / iOS — el plan global.** Acoplamiento MEDIDO, estado de las libs KMP, decisión del mapa y qué NO se puede portar. **Estado (07-30): el modo pelea CORRE ENTERO en iOS, con menú, Ajustes y Coleccionables.** Queda pulir y decidir firma/App Store; el mundo abierto es trabajo futuro. |
| — | `SETUP_PC_NUEVA.md` | 🖥️ **Poner el repo a compilar en una PC Windows nueva.** Los 4 archivos que NO viajan por git (⚠️ `gradle-wrapper.jar` bloquea hasta `gradlew`), la prueba de humo y cómo se verifica iOS desde Windows. |
| 12 | `12_PLAN_MUNDO_ABIERTO_iOS.md` | 🌎🍏 **Portar el MUNDO ABIERTO a iOS — plan medido.** Cuánto es (30 302 líneas), los 2 bloqueadores contados (osmdroid y `R.string`) y las 8 fases en orden, con **la cadena exacta que bloquea el `WorldMapViewModel`** y por qué se paró donde se paró. El tamaño y los límites de tienda están en el doc 13. **Léelo antes de tocar `map_exterior` o `interiores`.** |
| 13 | `13_ASSETS_Y_TAMANO.md` | 🗜️ **Qué se sube a cada tienda y cuánto puede pesar.** Los límites REALES verificados en la fuente (**Play: 500 MB de módulo base ← el que aprieta · App Store: 4 GB, los 200 MB son solo un aviso**), dónde está el peso del AAB medido, **qué formato de audio va en cada sitio y por qué** (Ogg solo-Android por el bucle; `.m4a` para lo compartido porque iOS no lee Ogg) y el plan de adelgazamiento con `tools/optimizar_assets_produccion.sh`. **Léelo antes de añadir cualquier asset.** |
| — | `../iosApp/README.md` | 🍏 **El proyecto Xcode.** Cómo compilarlo (⚠️ el framework de Kotlin NO se construye solo), qué assets entran en el bundle y qué ajustes de Xcode no se tocan. **Para añadir una pantalla a iOS se toca `PowAppIos.kt`, no este proyecto.** |

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
  Tabla peleador→mapa: **`SF/SF_STAGES_MAPS_UNLOCK.md`**.
- **Roster:** 18 dedicados. NO arcade: Lázaro / Granadero / Paramédico (alpha+shared).
- **Presidenta** ≤1/4 vida → metamorfosis. **Gama baja:** tick ~30 fps, atlas ≤2048, CARGANDO.

### 📱 Qué corre en cada plataforma (MEDIDO 2026-07-30)

| Modo | Android | iOS | Nota |
|---|:---:|:---:|---|
| Menú principal, Ajustes, Coleccionables | ✅ | ✅ | Misma pantalla de `commonMain`, distinto Controller |
| 🥊 Huelum vs. Goya — arcade, práctica, IA vs IA | ✅ | ✅ | Verificado en simulador: pelea, audio, guardado, modo desarrollador |
| 🥊 Multijugador (Render / BT / LAN / P2P) | ✅ | 🚧 | RFCOMM no existe en iOS; WebRTC y UDP multicast no se portaron |
| 🌎 Mundo libre, interiores, zombis | ✅ | 🚧 | **En curso.** Ver `12_PLAN_MUNDO_ABIERTO_iOS.md` |
| 📖 Modo Historia | ✅ | 🚧 | Va con el mundo abierto |

**🚧 = el botón SE PINTA en iOS pero avisa en vez de navegar.** Es a propósito, para poder comparar
los dos menús mientras se porta el mundo. ⚠️ **Se apaga con `MODOS_EN_OBRAS_VISIBLES = false` antes
de subir a la App Store** (Apple rechaza funciones anunciadas que no funcionan).

⚠️ **Quien decide esta tabla es `PowModos.kt`**, no la UI, y hay TRES preguntas distintas:
`disponible()` (¿se juega?), `enObras()` (¿se pinta sin jugarse?) y `sePinta()` (¿aparece?).
Añadir un modo a `modosDe(IOS)` **no lo porta**. Detalle en `11_SEPARACION_IOS_ANDROID.md` §5.

### Docs de trabajo / Working docs (no son 00–13)

> ⚠️ **La RUTA de esta tabla importa.** Casi ninguno de estos archivos está en la raíz de la
> carpeta: viven en `SF/`, `MUNDO/` o `_ARCHIVO/`. Buscarlos por el nombre suelto no los encuentra.

| Archivo / File | Contenido / Contents |
|---|---|
| **`SF/FLUJO_ASSETS_SF.md`** | **⭐ FLUJO COMPLETO de assets de pelea: identificar → recortar → empacar → QA + trampas conocidas.** |
| `GUIA_mantenimiento_no_senior.md` | **EMPEZAR AQUÍ si eres IA/dev nuevo:** 7 reglas, chuleta, qué NO hacer. *(sí está en la raíz)* |
| `SF/DISENO_ARCADE_SF_POW.md` | Diseño + avance del ARCADE POW (escalera, desbloqueos, IA por fases). |
| **`SF/SF_STAGES_MAPS_UNLOCK.md`** | **16 mapas × 3 luces, peleador→hogar (tabla dueño), desbloqueos MP.** |
| **`SF/QA_SF_STAGES_2026-07-18.md`** | **QA de fondos (dueño): encuadre `SfBgFraming` (5 mapas nuevos ✅ + salto), pendientes: sombra Isla Muñecas, video día FES Acatlán, subtítulos que se salen.** |
| **`SF/AUDIO_INVENTARIO_SF.md`** | **Qué audio tiene cada peleador, globales vs por-peleador, qué borrar, y el fix de tamaño AAB (atlas lossless→lossy 221→82 MB). Script `tools/sf_audio_review.sh`.** |
| **`MUNDO/PROMPT_panoramico_todos_los_mapas.md`** | **Trabajo FINAL diferido: aplicar el encuadre panorámico + salto a los 16 mapas, uno por uno. Prompt autónomo con todo lo necesario.** |
| `SF/AUDIT_SF_MULTIPLAYER.md` | Multijugador 1v1: protocolo, server `MultiplayerSF/`, BT/LAN. |
| `SF/ASSETS_STREETFIGHTER_MIGRACION.md` | Pipeline assets pelea (JSON, pack, migración SF→POW). |
| **`SF/SF_SPECIAL_VOICES_SFX.md`** | Voces/SFX v3: 21/21 español, cortes locales, Whisper, hashes, `MediaPlayer`; v1/v2 queda histórico. ✅ **2026-07-22: subtítulos de frases ACTIVOS** (`voiceSubtitlesEnabled=true`; frases curadas en `voice_phrases.json`, 64 `es` + `en` completo). |
| `SF/GUIA_regeneracion_sprites_croma.md` | Regenerar sprites croma pelea+mundo (proceso VIGENTE de recorte). |
| **`SF/PROMPT_SOL56_TANDAS_NUEVAS.md`** | **⭐ Prompt de las 10 hojas NUEVAS (20–29, moveset 3rd Strike) por personaje + rutas de assets + estándar de calidad.** |
| `MUNDO/NPC_SPRITES_PIPELINE.md` | Recorte estándar de NPCs del mundo. |
| **`PROMPT_WINDOWS_verificar_android_y_release.md`** | **⭐ TRASPASO VIVO (07-31): verificar que Android sigue igual tras los 80 commits de KMP, y publicar.** Lo de más riesgo (datos guardados, los 3 gestores de IA), el checklist de release y el plan de adelgazar el AAB. *(raíz)* |
| **`PROMPT_MAC_orientacion_y_puente_js.md`** | **⭐ TRASPASO VIVO (08-17) → MAC: probar en el simulador las dos costuras nuevas de iOS** (forzar horizontal + la vuelta del puente JS → Kotlin). Se escribieron desde Windows y **la mitad de Swift nunca se ha compilado**. Qué probar, en qué orden y qué significa cada fallo. *(raíz)* |
| `CHECKPOINT_SENIOR_refactor.md` | Receta del patrón manager/Hilt/detekt (referencia). *(raíz)* |
| `MUNDO/CAMPAIGN/` | Guion Modo Historia (misiones 1–3 + side). |
| `_ARCHIVO/` | **Histórico. NO son tareas.** Incluye los guiones de iOS ya ejecutados (`ARRANQUE_MAC_iOS.md`, `PLAN_SF_EN_iOS.md`, `PROMPT_MAC_navegacion_iOS.md`), `PENDIENTES_2026-07-20.md` y los prompts de traspaso a Gemini/GPT/Fable. |

---

## Datos rápidos / Quick facts

- **Package root:** `ovh.gabrielhuav.pow`
- **Lenguaje / Language:** Kotlin + Jetpack Compose + Material 3
- **Arquitectura / Architecture:** MVVM estricto por *feature* / strict MVVM by feature
- **Servidores / Servers:** 2× Node.js + `ws` (open world `Multiplayer/`, zombi `MultiplayerInteriores/`), dockerizados en Render
- **Room DB:** versión 8 (`MIGRATION_7_8` + destructive fallback)
- **336 archivos Kotlin / Kotlin files**, ~66k líneas (MEDIDO 2026-07-30), repartidos así:

  | Source set | Archivos | Líneas | Corre en |
  |---|---:|---:|---|
  | `shared/commonMain` | 101 | 20 576 | Android **+ iOS** |
  | `shared/androidMain` | 16 | 2 543 | Android |
  | `shared/iosMain` | 14 | **695** | iOS |
  | `app/src/main` | 205 | 42 632 | Android |

  **9 archivos pasan de 1000 líneas** (tabla con módulo en `10 §8`); solo 2 están en `:shared`.
  ⚠️ **`commonMain` tiene 0 imports de `android.*`** y eso no es negociable: es lo que hace que iOS
  compile.
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
> terminar, dime qué líneas de estos docs (00–13) hay que actualizar (y del README público de la
> raíz si el cambio es user-facing)."
>
> **EN:** "Read the `README for IAS` folder (that is the full context of the POW project).
> Implement <task> following the MVVM pattern and the conventions in file 09. Don't ask me for
> more code unless a file referenced in the 'Key files' tables (04/05) is missing. When done,
> tell me which lines of these docs (00–13) to update (and the public root README if user-facing)."

---

## Relación con los otros docs / Relationship to the other docs

**ES:** La RAÍZ del repo tiene un `README.md` **público** (bilingüe, orientado a humanos) que es la
visión general. Esta carpeta (`00`–`11`) es la versión **granular y por archivo** para alimentar a un
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
`SF/DISENO_ARCADE_SF_POW.md`.
