# PROMPT DE TRASPASO (cambio de cuenta/PC) — 2026-07-17d

> Pega TODO esto como primer mensaje en la nueva sesión. La otra PC ya tiene el proyecto.
> NO puedes compilar: entrega listo para Rebuild Project.

## A. PROMPT DEL PROYECTO (permanente)

Estás ayudándome con "Politécnico Open World" (POW), juego Android 2D top-down sobre mapas
reales (Kotlin + Jetpack Compose + MVVM estricto por feature).

RUTAS (esta PC):
- Repo raíz: `C:\Users\gabri\AndroidStudioProjects\PolitecnicoOpenWorld`
- Proyecto Android: `...\PolitecnicoOpenWorld\PolitecnicoOpenWorld`
- Contexto IAs: `...\PolitecnicoOpenWorld\PolitecnicoOpenWorld\README for IAS`

CONTEXTO: "README for IAS" (00–09 + docs de trabajo) reemplaza al código; léela antes de tocar.
Modo pelea "HUELUM VS. GOYA": lee `07_OTHER_FEATURES.md` §HUELUM VS. GOYA + `DISENO_ARCADE_SF_POW.md`
+ `AUDIT_SF_MULTIPLAYER.md`. Si necesitas un .kt concreto, pídemelo; no inventes contenido.

REGLAS: MVVM y convenciones/gotchas del 09. Estado inmutable (`_state.update { it.copy() }`);
Views solo `collectAsState()` + intenciones. Comentarios/strings en español; UI en `res/values(-en)`
con PARIDAD ES+EN. Gotcha miembro-vs-extensión: gana el MIEMBRO. Conserva CRLF en .kt; verifica
balance de llaves. No compilas: listo para Rebuild. Al terminar: protocolo de docs del 09.
Respuestas concisas.

## B. RUTAS QUE VAS A NECESITAR (dentro del proyecto Android `app\src\main\`)

- UI modo pelea: `java\ovh\gabrielhuav\pow\features\streetfighter\ui\StreetFighterScreen.kt`
- VM: `...\features\streetfighter\viewmodel\StreetFighterViewModel.kt` (+ `StreetFighterState.kt`)
- Modelos: `...\domain\models\streetfighter\SfModels.kt` (enum `SfFighterId`, `SfCpuDifficulty`,
  `SfFighterState`, `SfBox`) y `SfArcadeLadder.kt`
- Tema/HUD: `...\features\streetfighter\data\SfTheme.kt`; frames: `SfFrameCatalog.kt`, `SfSharedSheets.kt`
- Guardado arcade: `...\data\repository\SfArcadeRepository.kt`; ajustes: `data\repository\SettingsRepository.kt`
- Menú principal (botón destacado): `...\features\main_menu\ui\MainMenuScreen.kt`
- Strings: `app\src\main\res\values\strings.xml` y `values-en\strings.xml`
- Assets pelea: `app\src\main\assets\STREETFIGHTER\{IMAGES,DATA,SOUNDS}\`
- Arte fuente + pipeline: `newSFAssets\<Personaje>\` (raíz repo) + `PolitecnicoOpenWorld\tools\`
  (`slice_sf_chroma_sheets.py`, `pack_sf_character.py`). Deps: PIL, numpy, scipy.

## C. ESTADO ACTUAL del modo pelea (2026-07-17)

- **Roster:** RYU/KEN eliminados. Estudiantes ESCOMBOY/ESCOMGIRL/ROBOT (jugables, NO enemigos).
  Con arte propio: Prankedy, Señor Tienda, Paparazzi 1/5, Rey Grupero, Policías CDMX H/M,
  Granaderos H/M, Paramédico CR, Charro Negro, La Llorona, La Tzitzímime, Yoalli Ehécatl, La Presidenta.
- **ARCADE (`SfArcadeLadder`, 15 peleas):** 1 Paramédico CR · 2-4 {Paparazzi1, Paparazzi5, Señor
  Tienda} azar · 5-6 {Rey Grupero, Prankedy} azar · 7-10 policías (fijo) · 11-12 {Charro, Llorona}
  azar · 13 Tzitzímime · 14 Yoalli · 15 La Presidenta (FINAL). Desbloquea peleadores+mapas
  (guardado LOCAL `SfArcadeRepository`). Dificultad FIJA que sube: NORMAL 1-7, AVANZADA 8-12/jefes,
  PESADILLA final; `cpuIntensity` piso 0.25→1.0.
- **Menú de modos** (`SfModeMenuOverlay`): ARCADE / PRÁCTICA / MULTIJUGADOR. Práctica = elegir
  dificultad (BASICA/NORMAL/AVANZADA/PESADILLA), no desbloquea. Modo Desarrollador (`devUnlockAll`)
  desbloquea todo. Toggle "Mostrar hitboxes" en Ajustes.
- **HUD/copyright:** `sf_hud_pow.png` (fuente+barra+KO+timer) y `sf_decals_pow.png` reemplazan a
  hud.png/decals.png; Ken.png y kenstage.png fuera. Pendiente: sonidos (`hadouken.ogg` + golpes).

## D. TAREAS — ver detalle en `DISENO_ARCADE_SF_POW.md` §PENDIENTE

- [x] **ARCADE por defecto al entrar** (2026-07-17e): al abrir SF sale directo el selector de
  peleador del arcade; el menú de modos (PRÁCTICA/MULTIJUGADOR) se abre con "↕ Otros modos".
  (`StreetFighterScreen.kt`: `arcadeSetup=true`/`sfMenu=false` por defecto + `backText`.)
- [x] **Bloqueados con identidad OCULTA** (2026-07-17e): `CharacterCard` pinta los bloqueados con
  su animación PIXELADA (`pixelateBitmap`, 12 px) + silueta NEGRA (`ColorFilter.tint`) y nombre
  "???"; sin badge ALPHA. Se revela al desbloquear.
- [x] **Rival visible/claro** (2026-07-17e): el ocultamiento es SOLO en el selector; los rivales
  en pelea se dibujan normal. Pelea 1 = Paramédico Cruz Roja.
- [ ] **Combate estilo SF original (GRANDE, sig. tarea):** más controles/motions, combos/cancels,
  fluidez. Toca input (`onJoystickMove`/`onAttackPressed`/`onKickPressed`) + `buildCpuInput` +
  máquina de estados del VM (`SfFighterState`). **Definir el set de moves con el dueño ANTES.**

## E. Al terminar
Protocolo de docs del 09: 07 §HUELUM VS. GOYA + `DISENO_ARCADE_SF_POW.md` + README público EN+ES.
Verifica con Read, CRLF y balance de llaves. Listo para Rebuild.
