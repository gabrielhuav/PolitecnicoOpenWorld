# 07 · Menú, Ajustes, ShineCTO, Coleccionables / Menu, Settings, ShineCTO, Collectibles

---

## Menú principal / Main menu (`features/main_menu/`)

### `viewmodel/MainMenuViewModel.kt` + `MainMenuState.kt`
- `MainMenuState` incluye `mapProvider (default CARTO_VOYAGER)`, `showCacheWidget`, `showFpsWidget`,
  `showMultiplayerDialog`, `playerName`, estado del warm-up.
- API: `onStartGame()`, `setMapProvider(provider)`, `toggleCacheWidget/FpsWidget`,
  `updateShowMultiplayerDialog(show)`, `updatePlayerName(name)`, `onMultiplayerPressed()`,
  `cancelWarmup()`, `dismissWarmupError()`.

### `ui/ServerWarmupManager.kt` (paquete `data.network`)
**ES:** Render (tier gratis) duerme los servidores. Al tocar **MULTIJUGADOR**, hace `GET <server>/status`
por HTTPS **antes del diálogo de nombre**, bloqueando con un spinner cancelable hasta `200 OK` (reintenta
en timeout). Pings exitosos se cachean 60 s.
**EN:** Render free tier sleeps servers. On tapping **MULTIPLAYER**, polls `GET <server>/status` over
HTTPS **before the name dialog**, blocking with a cancellable spinner until `200 OK` (retry on timeout).
Successful pings cached for 60 s.

### `ui/MainMenuScreen.kt`
Menú principal; título ligado a `BuildConfig.VERSION_NAME` con auto-shrink que nunca parte de línea.
**Botones (renombrados):** `menu_start_game` ahora es **"MUNDO LIBRE"** (open world sin campaña, spawn por
defecto) y `menu_load_game` es **"MODO HISTORIA"** (antes deshabilitado; ahora navega a `story_mode` vía
`onNavigateToStory`).

---

## Modo Historia / Campaña (`features/main_menu/`)

**ES:** Pantalla de campaña accesible desde **"MODO HISTORIA"** (ruta `story_mode`). Muestra el **prólogo**
(brote del Politécnico: Prankedy crea por accidente una sustancia corrosiva en la ENCB), un **selector de
escuela** y **"CARGAR PARTIDA"** (habilitado solo si hay una partida guardada). "COMENZAR" pasa por la
**intro** (`story_intro/{schoolId}`, "Listo para Iniciar") antes de entrar al mundo.
**EN:** Campaign screen from **"STORY MODE"** (`story_mode`): prologue + school picker + **"LOAD GAME"**
(enabled only if a save exists). "START" goes through the **intro** (`story_intro/{schoolId}`) first.

### `viewmodel/StoryModeViewModel.kt` + `StoryModeState.kt`
- Alcance **NavBackStackEntry** (se instancia con `viewModel(factory = Factory(context))`, así re-lee el
  guardado cada vez que se entra). `StoryModeState`: `selectedSchoolId` + `hasSave`/`savedSchoolId`/`savedAt`.
- API: `selectSchool(id)` (ignora escuelas no disponibles), `selectedSchool()`, `savedSchool(): CampaignSchool?`.
- Lee la partida de **`data/repository/CampaignRepository.kt`** (SharedPreferences `pow_campaign`:
  `saveCampaign(schoolId)`/`hasSave`/`getSavedSchoolId`/`getSavedAt`/`clearCampaign`).

### 🆕 Sistema de guardado COMPLETO en JSON con SLOTS — `data/repository/SaveGameRepository.kt`
**ES:** `CampaignRepository` (prefs) solo dice QUÉ escuela. El **estado completo** se guarda en **JSON con
hasta `SLOT_COUNT`=5 SLOTS** (`filesDir/pow_campaign_save_<n>.json`):
`GameSaveData(schoolId, lat, lon, health, wantedLevel, isDriving, isDrivingPoliceCar, vehicleModel,
vehicleColor, skin, nearbyNpcs: List<SavedNpc>, objectiveId, objectiveDone, savedAt)`. API del repo:
`save(slot,data)`, `load(slot)`, `hasSave(slot)`, `anySave()`, `firstEmptySlot()`, `summaries(): List<SaveSlotSummary>`, `clear(slot)`.
- **Selector de slots:** `features/main_menu/ui/SaveSlotsDialog.kt` (`SaveSlotsMode.LOAD`/`SAVE`) muestra los 5
  slots con escuela + fecha. Lo hospeda **`MainActivity`** a nivel de Activity (un diálogo de GUARDAR común a
  mapa e interiores; otro de CARGAR en `story_mode`).
- **Guardado MANUAL** desde el ítem **"Guardar partida"** — disponible en el menú de Opciones del **mapa
  global Y en interiores** (`ZombieGameScreen`); ambos llaman `onRequestSaveGame` → abre el selector y eliges
  slot. **AUTO-GUARDADO** al salir/cerrar escribe en el **slot activo** (`campaignSlot`), solo si `inCampaign`.
- **API del VM (extensiones en `viewmodel/WorldMapSaveGame.kt`):** `saveGame(context, slot)`,
  `loadGame(context, slot): Boolean`, `buildSaveData(schoolId)`, `restoreSaveData(data)`,
  `setCampaignObjective(obj)`, `checkObjectiveProgress(loc)`. Campos del VM: `campaignSchoolId`, `inCampaign`,
  `campaignSlot` (los fija `MainActivity`).
- **"COMENZAR"** ocupa el **primer slot vacío** (no pisa otras partidas), fija el objetivo de la Misión 1 y
  spawnea en ESCOM. **"CARGAR PARTIDA"** abre el selector de slots y restaura el estado completo del slot
  elegido (posición/vida/buscado/vehículo/skin/objetivo + NPCs cercanos).

### 🆕 Intro como CÓMIC + Objetivos (Misión 1) 
**ES:** `StoryIntroScreen` ahora es un **visor de cómic**: muestra los 8 paneles de `StoryComicCatalog`
(`assets/STORY/INTRO/IntroPOW1..8.webp`, imágenes **HORIZONTALES** → la pantalla **fuerza orientación
landscape** mientras dura la intro vía `requestedOrientation` y la restaura al salir; `MainActivity` declara
`configChanges` para que el giro no recree la Activity). Tienen un **recuadro blanco** donde el código dibuja
el `text` de cada panel. Navegas tocando la mitad derecha (siguiente) / izquierda (anterior); **"Saltar"** salta toda la intro;
en el último panel (IntroPOW8), tocar → **INICIAR**: guarda la partida, fija spawn ESCOM + objetivo, y
**transiciona al primer interior JUGABLE de la campaña: el Lobby de la ENCB** (ruta `encb_lobby`), en vez
de ir directo al mundo. El lobby **reusa el motor de salas** (`ZombieGameScreen` con
`startRoom=ZombieRoomCatalog.ENCB_LOBBY_ID`, ver 05): mismos controles/cámara/colisiones/aura que el lobby
de ESCOM, pero sala `LOBBY` **sin zombis, sin mano zombi y sin puertas/waypoints** (`doors=emptyList()`),
con el banner **"Objetivo: Investiga qué pasó"** superpuesto. El lobby es la entrada de una **cadena LINEAL de
4 salas** `encb_lobby → encb_salon1 → encb_lab1 → encb_lab2` (todas LOBBY, fondos `INTERIORS/ENCB/*.webp`): cada
una tiene UNA puerta de AVANCE (waypoint X → `goToRoom(next)`), ninguna tiene salida al mapa entre medias (flujo
"atrapado"), y el banner de objetivo se mantiene en las 4 (`ZombieRoomCatalog.ENCB_STORY_ROOM_IDS`). Las
transiciones internas ocurren en el mismo `ZombieGameScreen`/VM. La navegación a la intro usa
`popUpTo("main_menu") { inclusive = true }`, lo que **destruye `StoryIntroScreen` y libera los bitmaps
IntroPOW1..8**. Si una imagen falta, se muestra un panel oscuro con el texto (no crashea).
- **🆕 OUTRO / 2ª parte de la intro (`ENCB_OUTRO`):** el **waypoint final de `encb_lab2`** (X) cierra la
  exploración y reanuda la narrativa: `goToRoom(EXIT_TO_STORY_OUTRO)` → `onPlayStoryOutro` → ruta `story_outro`,
  que **reusa `StoryIntroScreen`** con `sequenceId = StoryComicCatalog.ENCB_OUTRO_ID` (paneles
  `STORY/INTRO/IntroPOW9..11.webp`, vía la nueva `StoryComicCatalog.sequence(id)`). Al ser otra pantalla, la
  **UI de juego (joysticks/indicadores/objetivo) queda oculta**. Al terminar `IntroPOW11` (o "Saltar"/"Volver")
  **`MainActivity` llama `setStorySpawn(19.5001588, -99.1450298)` (coords EXCLUSIVAS de la ENCB, solo aquí)** y
  entra al mundo: `navigate("world_map") { popUpTo("story_outro"){inclusive=true} }`. `setStorySpawn` activa
  `inCampaign=true`. El **MUNDO LIBRE** del menú NO se altera: `onNavigateToMap` fuerza `inCampaign=false` y
  `fetchCurrentLocation`→`updateInitialLocation(SPAWN_ESCOM_LAT/LON)` (spawn ESCOM canónico intacto).
- **🆕 Prankedy ACOMPAÑANTE (solo campaña ENCB):** al entrar al mundo en la ENCB,
  `WorldMapPrankedy.maybeSpawnPrankedyCompanion` (en el game loop, gateado por `inCampaign` && vecindario ENCB,
  bandera `prankedyCompanionActivated` re-armada por `setStorySpawn`) enciende a Prankedy en fase **`HIRED`**
  (`spawnCompanion`): te **sigue** con animaciones `p_walk`/`p_run` (sin atacarte) y fija el objetivo
  **`MissionCatalog.ESCOLTAR_PRANKEDY`** → el widget muestra **"Lleva a un lugar seguro a Prankedy"**. En
  MUNDO LIBRE no aparece (sigue el Prankedy hostil manual del menú de Opciones). Ver 03 (fase HIRED) y 04.
- **🆕 Línea GPS de campaña (ENCB → ESCOM):** al encender el acompañante, `maybeSpawnPrankedyCompanion`
  calcula con **A*** (`findRoadRoute`, sobre la red vial) una ruta de la ENCB a la ESCOM ("lugar seguro") y la
  guarda en **`WorldMapState.campaignRouteWaypoints`**. Se dibuja como **línea ROJA sólida** por encima de las
  teselas y **por debajo de personajes/HUD**: en OSM nativo es un `Polyline` rojo (tag `route_overlay_tag+900`,
  `overlays.add(0,…)`); en web/Leaflet la función JS **`updateCampaignRoute`** (`WorldMapLeafletHtml`) dibuja un
  `L.polyline` rojo en el overlayPane. **Desaparece** cuando el jugador entra a ~100 m de la ESCOM
  (`maybeHideCampaignRouteNearEscom` vacía la lista en el game loop). Solo en campaña.
- **🆕 Editor in-game del cuadro de texto:** como el recuadro blanco está a distinta altura por panel, el botón
  **"Editar"** activa un editor para **mover** (arrastrar o Subir/Bajar), **redimensionar** (Alto ±) y cambiar
  el **tamaño de letra** (Letra ±) del cuadro, **por panel**. Se persiste en
  **`data/repository/StoryLayoutRepository.kt`** (`StoryBoxLayout(topFrac, heightFrac, fontSp)` en
  SharedPreferences `pow_story_layout`); "Guardar" guarda ese panel, "Todas" aplica a todos. También ajusta
  el **ancho** (`Ancho ±`, `boxWidthFrac`) — el cuadro va centrado. Los defaults viven en `ComicPanel`
  (`boxTopFrac/boxHeightFrac/boxWidthFrac/fontSp`). **⚠️ El ajuste se guarda SOLO en el dispositivo**
  (SharedPreferences), no en el repo: por eso hay un botón **"Exportar"** que vuelca TODOS los paneles a un
  **JSON** (vía selector de archivo) y además escribe en **Logcat** (tag `STORY_LAYOUT`) las líneas
  `ComicPanel(...)` listas para PEGAR como defaults en `StoryComicCatalog.kt` (así la config queda en el código).
- **Objetivos:** `domain/models/CampaignMission.kt` (`CampaignObjective`, `MissionCatalog.first = ir_encb`,
  "Ve a la ENCB"). Al COMENZAR se fija el objetivo; el **game loop** (`checkObjectiveProgress`, solo si
  `inCampaign`) lo marca cumplido al entrar en `arriveRadiusMeters` del destino. **Widget de Objetivos
  SIEMPRE visible** (`ui/components/ObjectivesWidget.kt`, HUD arriba-izquierda con título + distancia). El
  objetivo se guarda/restaura en `GameSaveData`. ⚠️ Las coords de la ENCB en `MissionCatalog` son aproximadas.

### `ui/StoryModeScreen.kt` + `ui/StoryIntroScreen.kt`
- `StoryModeScreen`: prólogo + tarjetas de escuela (`SchoolCard`) + "CARGAR PARTIDA" (on solo con guardado;
  reanuda en la escuela guardada vía `onLoadCampaign`) + "COMENZAR" (`onStartCampaign` → navega a la intro) +
  "VOLVER". Usa `windowInsetsPadding(WindowInsets.systemBars)` para no chocar con la barra de navegación.
- `StoryIntroScreen` ("Listo para Iniciar"): **placeholder** narrativo (futuros banners/sprites del prólogo).
  Al **INICIAR** (`onBegin`) `MainActivity` **guarda** la partida (`campaignRepository.saveCampaign(school.id)`),
  fija el spawn (`setStorySpawn`) + objetivo y navega a **`encb_lobby`** (Lobby ENCB) con `popUpTo("main_menu")
  { inclusive = true }`; el lobby sale a `world_map`. "CARGAR PARTIDA" entra directo a `world_map` **sin** guardar de nuevo.
- El guardado lo escribe **`MainActivity`** (punto de DI), no las Views. La partida ligera (escuela) va a
  `CampaignRepository`; el **estado completo** (posición/vida/buscado/vehículo/skin/NPCs) va al JSON de
  `SaveGameRepository` (ver "Sistema de guardado COMPLETO" arriba).

### `domain/models/SchoolCatalog.kt`
`CampaignSchool(id, displayName, latitude, longitude, available)` + `object SchoolCatalog.schools`.
Solo **ESCOM** está `available = true` (= `TeleportCatalog.zones[0]`); **FES Aragón** y **UAM** quedan en
desarrollo (`available = false`, deshabilitadas en la UI). `displayName` es nombre propio (no se traduce).

### `WorldMapViewModel.setStorySpawn(lat, lon)` (miembro)
Fuerza el punto de aparición de la campaña y re-arma las compuertas de carga
(`isMapReady`/`isRoadNetworkReady`/`npcsWarmedUp = false`) para descargar el mundo alrededor de la escuela
elegida. A diferencia de `updateInitialLocation` (gateada por `isLoadingLocation`, ya consumida en
`MainActivity.onCreate`), **no** está gateada. Sin gemelo de extensión.

---

## Coleccionables / Collectibles (`features/main_menu/`)

- **`viewmodel/CollectiblesViewModel.kt`** (Activity-scoped, `Factory(context)`): lee
  `CollectibleRepository.allCollectiblesFlow` → inventario reactivo.
- **`ui/CollectiblesScreen.kt`**: pantalla de inventario (ruta `collectibles`).
- **Lógica de spawn/recogida** está en el open world: `WorldMapCollectiblesLogic.kt` (ver 04). 6
  coleccionables de lore sembrados en Room (`CollectibleRepository`). Una **Mano Zombi** especial solo
  aparece dentro del bounding box de ESCOM y dispara la cinemática al minijuego.
- En el open world: spawn 1 cada ~1 s a 300–600 m (snapeado a calle), prompt a 15 m, **X** recoge,
  diálogo temático (`CollectibleClaimDialog.kt`).

---

## Ajustes / Settings (`features/settings/`)

### Modelos
```kotlin
enum class ControlType { DPAD, JOYSTICK }                 // models/ControlType.kt
enum class SettingsCategory { ... }                       // models/SettingsCategory.kt (pestañas)
```

### `viewmodel/SettingsViewModel.kt` + `SettingsState.kt`
**ES:** Pestañas: Mapa / Controles / Gameplay / Interfaz. **Los controles son "staged":** los cambios
viven en campos `temp*` y **solo se aplican al pulsar GUARDAR**; salir descarta.
**EN:** Tabs: Map / Controls / Gameplay / Interface. **Controls are staged:** changes live in `temp*`
fields and **only apply on SAVE**; leaving discards.

- `selectCategory(category)`, `changeMapProvider(provider)`, `toggleCacheWidget/FpsWidget(enabled)`,
  `toggleZoomWidget(enabled)` y `toggleSpeedometer(enabled)` (ambos persisten; pestaña Interfaz:
  widget de nivel de zoom en vivo + velocímetro km/h visible solo al conducir, default activado).
- Staged: `changeControlType(type)`, `changeControlsScale(scale)`, `toggleSwapControls(swap)` → escriben
  `tempControlType/tempControlsScale/tempSwapControls`.
- `saveControlsSettings()` (commit + persiste vía `SettingsRepository` + empuja al mapa),
  `discardControlsChanges()` (al salir).
- `toggleRoadNetwork(show)`. `Factory(context)`.
- **Jugabilidad / Gameplay:** `changeNpcDensity(v: Float)` (0.4–1.6, persiste al instante),
  `toggleNpcEmojiLod(b)` y `toggleNpcFullEmoji(b)`. La pestaña **Jugabilidad** tiene:
  - **"Cantidad de NPCs"** (slider) → `SettingsState.npcDensity` → `WorldMapViewModel.setNpcDensity` →
    `NpcAiManager.userPopulationFactor` (se combina con gama del teléfono + densidad urbana, ver 03).
  - **"Optimizar dibujado de NPCs"** (switch; antes se llamaba "Optimizar para gama baja") → `npcEmojiLod`
    → `WorldMapState.npcEmojiLod` → render LOD de emojis (NPCs lejanos como 🧍🚗🧟, ver 04).
    Default = `isLowRamDevice` (`SettingsRepository`).
  - **"Optimizar para gama baja"** (switch NUEVO) → `npcFullEmoji` → `WorldMapState.npcFullEmoji` →
    TODOS los NPCs como emoji 🧍🚗🧟👮 sin importar distancia, en los TRES renderers (OSM nativo,
    Google nativo y web). Default = false.
  - Todos persisten en `SettingsRepository` (`getNpcDensity`/`getNpcEmojiLod`/`getNpcFullEmoji`) y se
    aplican **en vivo** al mapa desde `MainActivity` (llaman a `settingsViewModel` + `worldMapViewModel`).

### `ui/SettingsScreen.kt`
Pestañas + sliders. Escala adaptativa 60%–140% (cap 100% en portrait), swap de zurdos, botones A/B/X/Y.
`GameplaySettings` (Jugabilidad): slider de cantidad de NPCs + switches "Optimizar dibujado de NPCs"
(LOD) y "Optimizar para gama baja" (emoji total). `DiagnosticWidgetsSetting` (Interfaz): widgets de
caché, FPS, **zoom** (nivel de zoom actual en vivo) y **velocímetro** (km/h al conducir).

---

## ShineCTO easter egg (`features/interiores/shinecto/`)

> **🆕 Reestructura:** antes `features/shinecto/`; ahora **`features/interiores/shinecto/`** (subpaquete de
> la umbrella `interiores`). Usa `PlayerView`/`PlayerHealthBarFixed` desde **`interiores.core.ui`** (antes de
> `zombie_minigame`). Su asset es `PLACES/shine_cto/` (antes `LUGARES/shineCTO/`). / Now under the `interiores`
> umbrella; shared player views come from `interiores.core.ui`.

**ES:** Interior easter-egg accesible al acercarse a `ShineCTOLocation` (lat 19.459049, lon -99.163251,
`TRIGGER_RADIUS` 0.00015). Mini-juego social de bebidas.
**EN:** Easter-egg interior reached by approaching `ShineCTOLocation`. Social drinks mini-game.

### `viewmodel/ShineCTOViewModel.kt` + `ShineCTOState.kt`
```kotlin
data class ShineCTOState( ... )
data class ActiveDrink( ... )
enum class ShineCTOInteractable(val label: String) { ... }
```
- Movimiento: `moveByAngle`, `moveDirection`, `applyMovement`, `effectiveStep(s)`, `setRunning`, `setSpecial`.
- Interacción: `updateNearbyInteractable(px, py)`, `onInteract(): Boolean`, `consumeDrink()`,
  `claimShineCollectible()`, `dismissShineClaimedPopup()`. Spawns: `spawnInitialDrinks`,
  `randomDrinkPosition(currentDrinks)`. `Factory(...)`.

### UI
- `ui/ShineCTOScreen.kt` — interior (ruta `shinecto_interior`).
- `ui/EasterEggDiscoveryDialog.kt` — diálogo de descubrimiento (disparado por `showShineCTODiscovery`
  en `WorldMapState`).

---

## Tema / Theme (`ui/theme/`)
`Color.kt`, `Theme.kt`, `Type.kt` — Material 3. Sin lógica de negocio.
