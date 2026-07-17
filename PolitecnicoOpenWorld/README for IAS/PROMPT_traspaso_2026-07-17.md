# PROMPT DE TRASPASO (cambio de cuenta/PC) — 2026-07-17

> Copia y pega TODO este archivo como primer mensaje en la nueva sesión (la otra PC ya tiene
> el proyecto). Después súbeme el/los .kt que te pida. NO puedo compilar: entrego listo para
> Rebuild Project.

## A. PROMPT DEL PROYECTO (permanente)

Estás ayudándome con "Politécnico Open World" (POW), un juego Android 2D top-down sobre mapas
reales (Kotlin + Jetpack Compose + MVVM estricto por feature).

RUTAS: repo raíz `C:\Users\gabri\AndroidStudioProjects\PolitecnicoOpenWorld` (incluye servidores
Multiplayer/, MultiplayerInteriores/, MultiplayerSF/ y assets sueltos); proyecto Android en
`...\PolitecnicoOpenWorld\PolitecnicoOpenWorld`; contexto para IAs en `...\README for IAS`.

CONTEXTO: "README for IAS" (00–09 + docs de trabajo) es el contexto COMPLETO y reemplaza al
código; léela antes de proponer cambios. Para el modo pelea "HUELUM VS. GOYA" lee además
`07_OTHER_FEATURES.md` §HUELUM VS. GOYA, `DISENO_ARCADE_SF_POW.md`, `AUDIT_SF_MULTIPLAYER.md`,
`GUIA_regeneracion_sprites_croma.md` y `GUIA_generacion_assets_SF.md`. Si necesitas un .kt
concreto, pídemelo; no inventes contenido.

REGLAS: MVVM y convenciones/gotchas del 09 (incluido su protocolo de docs). Estado inmutable
(`_state.update { it.copy(...) }`); las Views solo `collectAsState()` + intenciones. Comentarios
y strings en español; strings de UI en `res/values(-en)` con PARIDAD ES+EN. Gotcha
miembro-vs-extensión: gana el MIEMBRO. Conserva CRLF en .kt existentes y verifica balance de
llaves. Servidores Node: `node --check`, Render FREE (warmup GET /status). No puedes compilar:
entrega listo para Rebuild. AL TERMINAR cambios de comportamiento: protocolo de docs del 09
(00–09 + README público bilingüe EN+ES). Respuestas concisas y directas.

## B. ESTADO ACTUAL del modo pelea (al 2026-07-17)

- **Personajes jugables: COMPLETOS.** RYU/KEN eliminados del enum. Estudiante/Estudianta y
  Granadero Hombre/Mujer (`POLICIA_GRANADERO_HOMBRE/MUJER`) ya con arte dedicado. **Solo falta
  terminar ROBOT** (sigue ALPHA: `sf_template.json` + `RUNTIME/Robot.png` + `isAlpha=true`;
  cuando tenga `robot.json`+PNG dedicado, quítale `isAlpha`/`sharedSet`).
- **Modo ARCADE (campaña, 11 peleas, `SfArcadeLadder`):** desbloquea personajes y mapas; guarda
  LOCAL (`SfArcadeRepository`, SharedPreferences). Escalera: 1-2 estudiantes (azar), 3-5
  Paramédico CR/Señor Tienda/Paparazzi 1 (azar), 6-9 Policía CDMX H/M + Granadero H/M (fijo),
  10 semifinal Rey Grupero, 11 final Prankedy. Al perder retrocede 1 pelea.
- **Modo PRÁCTICA** (peleador→rival→dificultad→mapa): NO desbloquea nada.
- **Dificultades:** BASICA/NORMAL/AVANZADA/**PESADILLA** (combos sin parar, esquiva, castiga).
  IA por FASES en arcade (`cpuIntensity` 0→1). Final del arcade = PESADILLA.
- **Modo Desarrollador** (Ajustes → `getDeveloperMode` → `devUnlockAll()`): desbloquea TODO.
- **Preview del selector:** ralentizado + anima IDLE + `walkForwards`.
- Detalle y pendientes de diseño: `DISENO_ARCADE_SF_POW.md`. Bugs de red (stun-lock, revancha,
  servidor LAN): `PENDIENTES_SF_2026-07-16.md` (siguen abiertos, no bloquean el arcade).

## C. TAREA TOP: BUG — los personajes cambian de TAMAÑO al hacer acciones

> **Estado 2026-07-17:** YA existe un TOGGLE de diagnóstico (Ajustes → "Mostrar hitboxes",
> estilo Minecraft) que dibuja las cajas push/hurt/hit sobre los peleadores (`drawFighter` +
> `drawWorldBox`). ⚠️ La NORMALIZACIÓN que "fuerza el asset a su caja" (la hizo Sol 5.6 en otra
> sesión) **NO está en este repo** — hay que traerla (pull/rama) o reimplementarla. El toggle
> sirve para verla/depurarla.

**Síntoma (reportado por el dueño):** al ejecutar acciones, los peleadores "crecen/encogen".
Ejemplo claro: al pegarle al **Señor de la Tienda**, encoge/cambia de tamaño en la pose de golpe.

**Causa raíz (analizada):** `drawFighter` → `drawSpriteAnchored` (en `StreetFighterScreen.kt`)
dibuja cada cuadro con `dstSize = src.w × src.h × scale`, anclado en `origin` (los pies). Para
peleadores con arte DEDICADO, `src`/`origin` vienen del JSON del personaje; si algunas poses
(sobre todo hit/hurt/ataques) se generaron con la FIGURA a distinta escala dentro de su celda,
la figura aparenta cambiar de tamaño aunque los pies queden anclados. Ya existe un parche
PARCIAL: `SfFighterId.hurtScale` reescala SOLO estados HURT y por-personaje — pero Señor de la
Tienda (y otros) tienen `hurtScale = 1f` (sin corrección). Ver la nota del enum en `SfModels.kt`
y `GUIA_regeneracion_sprites_croma.md`.

**Opciones de fix (elige conmigo):**
- **(a) Assets (fix real):** regenerar las poses inconsistentes a la MISMA altura de figura
  dentro de su celda (pipeline croma). Es trabajo de arte del dueño.
- **(b) Código general (recomendado si no se regeneran assets):** en `SfFrameCatalog` (al
  cargar, cacheado por personaje) medir los límites OPACOS de la figura en cada `src` y guardar
  un `spriteScale` + `origin` ajustados por-cuadro para normalizar la ALTURA de la figura;
  aplicarlo en `drawFighter` extendiendo el `spriteScale` que HOY solo corre en HURT a TODOS los
  estados. Costo: decodificar la hoja una vez por personaje. ⚠️ Verificar que no "flote" ni
  vibre; probar con Señor de la Tienda, estudiantes y granaderos.
- **(c) Rápido/parcial:** ajustar `hurtScale` por-personaje para los que encogen al recibir
  golpes (medido: figura idle ÷ figura hit).

**Archivos:** `features/streetfighter/ui/StreetFighterScreen.kt` (`drawFighter`,
`drawSpriteAnchored`), `features/streetfighter/data/SfFrameCatalog.kt`,
`domain/models/streetfighter/SfModels.kt` (`hurtScale`).

## D. Al terminar

Protocolo de docs del 09: 07 §HUELUM VS. GOYA + `DISENO_ARCADE_SF_POW.md` + README público
EN+ES. Verificar con Read, CRLF y balance de llaves. Listo para Rebuild.
