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

### `ui/StoryModeScreen.kt` + `ui/StoryIntroScreen.kt`
- `StoryModeScreen`: prólogo + tarjetas de escuela (`SchoolCard`) + "CARGAR PARTIDA" (on solo con guardado;
  reanuda en la escuela guardada vía `onLoadCampaign`) + "COMENZAR" (`onStartCampaign` → navega a la intro) +
  "VOLVER". Usa `windowInsetsPadding(WindowInsets.systemBars)` para no chocar con la barra de navegación.
- `StoryIntroScreen` ("Listo para Iniciar"): **placeholder** narrativo (futuros banners/sprites del prólogo).
  Al **INICIAR** (`onBegin`) `MainActivity` **guarda** la partida (`campaignRepository.saveCampaign(school.id)`),
  fija el spawn (`setStorySpawn`) y navega a `world_map`. "CARGAR PARTIDA" hace lo mismo **sin** guardar de nuevo.
- El guardado lo escribe **`MainActivity`** (punto de DI), no las Views. Por ahora la partida guarda solo la
  escuela (cuando exista progreso de Misión 1 se añaden campos a `CampaignRepository`).

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
