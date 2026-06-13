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

## ShineCTO easter egg (`features/shinecto/`)

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
