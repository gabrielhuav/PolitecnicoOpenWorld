# Politécnico Open World (POW)

> 🇬🇧 **English version below** · 🇪🇸 [Saltar a la versión en español](#-versión-en-español)

---

## 🇬🇧 English Version

**Politécnico Open World (POW)** is an Android 2D top-down exploration app built on top of real-world maps. The player navigates the actual streets of their surroundings (with initial focus on the ESCOM / Zacatenco area in Mexico City) using **OpenStreetMap** cartographic data, sharing the world with procedural NPCs (pedestrians and vehicles) and with other players connected to a real-time server. The ESCOM campus also hosts an embedded zombie survival minigame with interior buildings, melee/ranged combat and a power-up system.

The project is built entirely with **Kotlin + Jetpack Compose**, follows a strict **MVVM** pattern organized by *features*, and delegates persistent-world logic to a standalone Node.js server.

### ⚙️ Architecture

The repository contains two complementary projects:

```text
.
├── PolitecnicoOpenWorld/   # Android client (Kotlin + Compose)
└── Multiplayer/            # Game server (Node.js + WebSocket, dockerized)
```

#### MVVM at a glance

Every feature in the client follows the same three-layer split:

- **Model** (`domain/models/`): immutable data classes (`Npc`, `MapWay`, `Landmark`, `ZombieEntity`, `CharacterVisualConfig`...) and pure-logic helpers like `NpcAiManager`. No Android dependencies, no UI.
- **ViewModel** (`features/<name>/viewmodel/`): holds a single `MutableStateFlow<State>` exposed as a read-only `StateFlow`, drives game loops with coroutines and orchestrates repositories. Examples: `WorldMapViewModel`, `ZombieGameViewModel`, `InteriorViewModel`, `SettingsViewModel`, `CollectiblesViewModel`, `MainMenuViewModel`.
- **View** (`features/<name>/ui/`): pure Compose screens that observe state via `collectAsState()` and only emit user intents back to the ViewModel. Screens never access repositories or DAOs directly.

All cross-cutting concerns (Room, network, preferences) live in `data/` and are injected into ViewModels through `ViewModelProvider.Factory` instances co-located with each ViewModel. Top-level ViewModels (`WorldMapViewModel`, `SettingsViewModel`, `CollectiblesViewModel`) are scoped to the Activity so they survive navigation; interior and zombie ViewModels are scoped to their `NavBackStackEntry` so they reset when the player leaves.

#### Android client — feature-based organization

```text
app/src/main/java/ovh/gabrielhuav/pow/
│
├── data/                                    # ─── Data layer ───
│   ├── cache/
│   │   ├── RoadNetworkCache.kt              # LRU of OSM road zones (~2km cells, 7-day TTL)
│   │   └── TileCache.kt                     # LRU of map tiles (per provider, 8K max)
│   ├── local/room/
│   │   ├── PowDatabase.kt                   # @Database v8, 6 entities, MIGRATION_7_8
│   │   ├── dao/
│   │   │   ├── RoadNetworkDao.kt            # Atomic insertZoneWithData() transaction
│   │   │   ├── MapTileDao.kt                # Tile cache CRUD + LRU eviction
│   │   │   ├── LandmarkDao.kt               # User-editable buildings (designer mode)
│   │   │   └── CollectibleDao.kt            # 6 lore collectibles with Flow observation
│   │   └── entity/
│   │       ├── RoadEntities.kt              # RoadZone + RoadWay + RoadNode (FK cascade)
│   │       ├── TileEntities.kt              # MapTileEntity (BLOB + composite PK)
│   │       ├── LandmarkEntity.kt            # Buildings with scale, rotation, asset path
│   │       └── CollectibleEntity.kt         # id, name, description, assetPath, isCollected
│   ├── network/
│   │   └── WebSocketManager.kt              # OkHttp WS (no timeouts, 25s ping)
│   └── repository/
│       ├── OverpassRepository.kt            # Overpass API (2km radius, 45s timeout)
│       ├── SettingsRepository.kt            # SharedPreferences for controls
│       └── CollectibleRepository.kt         # Seeds 6 default items, exposes Flow
│
├── domain/                                  # ─── Model layer (pure Kotlin) ───
│   └── models/
│       ├── ActiveCollectible.kt             # Runtime instance of a collectible on the map
│       ├── CharacterVisualConfig.kt         # Hair/shirt/pants config for assembled NPCs
│       ├── EscomBuildings.kt                # InteriorBuilding enum (6 ESCOM buildings)
│       ├── Landmark.kt                      # Domain model used by the map
│       ├── LandmarkAssetCatalog.kt          # JSON catalog of buildable assets
│       ├── MapNode.kt / MapWay.kt           # OSM primitives
│       ├── Npc.kt / NpcType.kt              # NPC + CarModel enum (6 models)
│       ├── TeleportCatalog.kt               # Fixed teleport destinations
│       ├── ai/
│       │   └── NpcAiManager.kt              # 40-NPC population, spawn/despawn, road-adoption
│       └── zombie/
│           ├── ZombieModels.kt              # ZombieEntity, SkillEffect, SkillItem,
│           │                                # Projectile, CombatMode, ZombieRoom, ZoneDoor
│           └── ZombieRoomCatalog.kt         # Lobby + 6 building rooms with door layout
│
├── features/                                # ─── Feature modules (View + ViewModel) ───
│   ├── main_menu/
│   │   ├── ui/
│   │   │   ├── MainMenuScreen.kt            # 5 menu buttons + multiplayer dialog
│   │   │   └── CollectiblesScreen.kt        # Inventory grid with claim popup
│   │   └── viewmodel/
│   │       ├── MainMenuViewModel.kt         # MainMenuState (dialog, name input)
│   │       └── CollectiblesViewModel.kt     # Observes Room Flow → StateFlow
│   │
│   ├── map_exterior/                        # Core open world
│   │   ├── ui/
│   │   │   ├── WorldMapScreen.kt            # Map render for OSM / Google / Web
│   │   │   ├── CachingWebViewClient.kt      # Intercepts Leaflet tile requests
│   │   │   └── components/
│   │   │       ├── GameControllers.kt              # D-Pad, Joystick, action buttons,
│   │   │       │                                   # VehicleSteering, VehiclePedals
│   │   │       ├── PlayerCharacter.kt              # Animated sprite with health bar
│   │   │       ├── CharacterRenderer.kt            # DrawScope helper
│   │   │       ├── CharacterSpriteManager.kt       # Smart per-pixel tinting + LRU
│   │   │       ├── VehicleSpriteManager.kt         # 48-frame rotation per car model
│   │   │       ├── NpcRenderWrapper.kt             # Fade-out on death
│   │   │       ├── PlayerAction.kt                 # IDLE/WALK/RUN/SPECIAL enum
│   │   │       ├── AssetPickerDialog.kt            # Designer mode: pick building asset
│   │   │       ├── DesignerPanel.kt                # Move/rotate/scale/save controls
│   │   │       └── CollectibleClaimDialog.kt       # Themed popup on pickup
│   │   └── viewmodel/
│   │       ├── WorldMapViewModel.kt         # Game loop, multiplayer, NPCs, ESCOM logic
│   │       └── WorldMapState.kt             # MapProvider enum + ~30 state fields
│   │
│   ├── interior/                            # 6 ESCOM building interiors
│   │   ├── ui/
│   │   │   ├── InteriorScreenBase.kt        # Shared Composable: background + player + DPad
│   │   │   ├── AuditorioScreen.kt
│   │   │   ├── BibliotecaScreen.kt
│   │   │   ├── CafeteriaScreen.kt
│   │   │   ├── EdificioScreen.kt
│   │   │   ├── EstacionamientoScreen.kt
│   │   │   └── PalapasScreen.kt
│   │   └── viewmodel/
│   │       ├── InteriorViewModel.kt         # Normalized [0,1] movement, collision-aware
│   │       ├── InteriorState.kt
│   │       └── CollisionGrid.kt             # 20×30 walkable matrix, with emptyWithBorder()
│   │
│   ├── zombie_minigame/                     # Embedded survival minigame
│   │   ├── ui/
│   │   │   ├── ZombieGameScreen.kt          # Canvas-based world render + camera
│   │   │   ├── ZombieHud.kt                 # HUD, doors, SkillItem icons (pure Canvas)
│   │   │   └── ZombieSpriteManager.kt       # 9-frame zombie walk animation LRU
│   │   └── viewmodel/
│   │       ├── ZombieGameViewModel.kt       # AI, projectiles, effects, victory/defeat
│   │       └── ZombieGameState.kt           # State + CameraTransform
│   │
│   └── settings/
│       ├── ui/
│       │   └── SettingsScreen.kt            # Tabs: Map / Controls / Gameplay / Interface
│       ├── viewmodel/
│       │   ├── SettingsViewModel.kt         # Persists via SettingsRepository
│       │   └── SettingsState.kt
│       └── models/
│           ├── ControlType.kt               # DPAD / JOYSTICK
│           └── SettingsCategory.kt          # Sealed class for tab navigation
│
├── ui/theme/                                # Material 3 theme
└── MainActivity.kt                          # Single-Activity with Compose NavHost
```

The client follows a **Single-Activity** architecture with Compose-based navigation (`NavHost`) and **nine** destinations: `main_menu`, `world_map`, `settings`, `collectibles`, the six interior routes (`interior_auditorio`, `interior_biblioteca`, `interior_cafeteria`, `interior_edificio`, `interior_estacionamiento`, `interior_palapas`) and the `zombie_minigame` route.

### 🗺️ Map System

POW supports **eight map providers** that can be hot-swapped from the Settings screen:

| Provider | Mode | Notes |
|---|---|---|
| OSMDroid (Native) | Native render | Highest zoom (up to 21) |
| Google Maps (Native) | Google Maps SDK | Uses `MAPS_API_KEY` from manifest |
| OpenStreetMap (Web) | WebView + Leaflet | |
| Google Maps (Web) | WebView + Leaflet | |
| CartoDB Dark / Light | WebView + Leaflet | Video-game aesthetic |
| Esri World Street | WebView + Leaflet | |
| Esri Satellite | WebView + Leaflet | Real aerial view |
| OpenTopoMap | WebView + Leaflet | Terrain and contour lines |

Web modes render through **Leaflet** inside a `WebView` and are intercepted by a `CachingWebViewClient` that caches each tile in Room (`MapTileEntity`) using the normalized URL as key (stripping load-balancing subdomains and volatile parameters before hashing with SHA-256). This allows offline play in any previously visited area.

#### Road network cache

The road network needed to anchor movement and NPCs is fetched from the **Overpass API** and persisted in Room with the following strategy:

- **Cell-based granularity:** the world surface is divided into ~2 km × 2 km cells. Each downloaded cell covers a ~2 km radius around the player.
- **7-day TTL** before marking a cell as expired.
- **LRU of 20 cells** maximum: when full, the oldest cell is dropped.
- **Atomic transaction** (`@Transaction`) when inserting zone + ways + nodes, preventing corrupt states if the process dies mid-write.
- **Re-fetch cooldown:** once a zone is downloaded, Overpass won't be queried again for 5 minutes even if the player keeps crossing it.

The player cannot step off the road network: every movement is validated against a **spatial grid index** (`Seg` + `HashMap<cell, segments>`) that runs *snap-to-road* in O(nearby candidates) instead of O(n) over all streets.

### 🏛️ Landmarks and Designer Mode

The map is populated with editable buildings. The pipeline lives in three places:

- **`LandmarkCatalogManager`** (`domain/models/`) loads `assets/buildings_catalog.json` at startup, defining every placeable asset (display name, asset path, base size in meters, default scale).
- **`LandmarkDao` + `LandmarkEntity`** persist the user's placements (position, rotation 0-360°, scale 0.05-3.0×) across sessions.
- **Designer Mode** (toolbar toggle) activates `DesignerPanel`: arrow buttons for fine movement (±0.0001°), sliders for rotation and scale, plus **JSON export/import** to share map configurations as files.

On first launch, `assets/default_landmarks.json` seeds the database with the ESCOM campus layout.

### 🎁 Collectibles System

Six lore-themed collectibles (IPN logo, UAM motto, ESIME shout, ETS exam, ESCOM laptop, "Apuntes de Leyenda") are seeded into Room by `CollectibleRepository.initializeDefaultCollectiblesIfNeeded()`. The game loop spawns one uncollected item every ~1 s within 300–600 m of the player, snapped to the road network. Approaching within 15 m shows a "PRESS X TO PICK UP" prompt; pressing X marks the item as collected and shows a `CollectibleClaimDialog` with the lore description. The inventory screen reads from a `Flow<List<CollectibleEntity>>`, so claimed items appear in color while uncollected ones stay grayed out with "???".

A special seventh item — the **Zombie Hand** (`Objeto Misterioso ESCOM`) — only spawns when the player is inside the ESCOM bounding box. Interacting with it triggers a video cutscene (`Carga_Mod_Zombi.mp4`) and then navigates to the zombie minigame.

### 🚶 NPCs and Vehicles

`NpcAiManager` maintains a population of up to **40 NPCs** around the player, split into two types:

- **Pedestrians:** walk on pedestrian ways (`footway`, `pedestrian`, `path`, `residential`...). Each one is assembled at runtime by combining a base body, a hair sprite (`hair_1`...`hair_4`), and three random colors (hair, shirt, pants) using a **smart per-pixel tinting** technique that preserves skin tones (saturation filter) and separates shirt from pants by luminance.
- **Vehicles:** 6 models (`SEDAN`, `SPORT`, `SUPERCAR`, `SUV`, `VAN`, `WAGON`), each with 48 rotation frames (one every 7.5°). Color is also applied per-pixel, preserving headlights, turn signals, and rims via saturation + luminance analysis.

Behavior includes proximity-based spawning, distance-based despawning (>35 m equivalent), node-to-node navigation with smooth transitions between connected ways, and an **adoption** system: if an NPC enters the zone without an assigned street (because it was inherited from the server), it is automatically snapped to the nearest way before being moved.

#### Combat against NPCs

Pressing **B** triggers a special attack with 2.4 s cooldown that punches the nearest NPC within ~17 m. NPCs have 100 HP and take 15 damage per punch, displaying a contextual health bar in `NpcRenderWrapper`. On death, the NPC fades out over 1 s and is removed. Damage against remote players is routed through a `PLAYER_DAMAGE` WebSocket message so authority stays on the victim's client.

#### Entering and exiting vehicles

Pressing the **X** button near a vehicle "takes it": the NPC disappears from the list and the player is rendered as that car, with free 360° rotation based on movement angle. Pressing X again leaves the car parked as an NPC at the current position. The first time a player boards a vehicle, an ousted driver is spawned next to it.

### 🧭 Navigation and Waypoints

The player can drop a destination marker on any map point: tapping the green "Waypoint" button enters targeting mode (a red crosshair centers on the screen), then "Set Destination" confirms the location. The ViewModel runs a greedy road-graph search (`calculateRouteOnNetwork`) over a spatial grid of unique nodes and renders the path as a dashed blue polyline. The marker auto-clears when the player gets within 20 m.

### 🧟 Zombie Minigame

Entering the zombie minigame puts the player in a circular **ring of 7 rooms**: a lobby (`Campus ESCOM` croquis) with 6 doors leading to each ESCOM building. Inside a building, EXIT doors on the left and right connect to the previous/next building in the ring, while a central door returns to the lobby (with confirmation dialog to avoid accidental exits).

- **Camera system** (`CameraTransform`): zoom-aware, follows the player, clamps to world bounds, and uses `max(viewW/worldW, viewH/worldH)` so the background fills the screen without distortion (mathematical equivalent of `ContentScale.Crop`).
- **AI:** zombies pathfind toward the player with collision-aware sliding (X-axis then Y-axis fallback). They animate through 9 walk frames at ~140 ms per frame.
- **Dual combat mode:** the **Y button** held for 500 ms opens a weapon menu to toggle between **MELEE** (punch, 120 px radius, 34 dmg) and **RANGED** (projectiles at 22 px/tick, 50 dmg, 350 ms cooldown). The **B button** executes the attack.
- **SkillEffect drops:** dying zombies drop one of six effects with 45% probability, rendered as **pure Canvas icons** (no asset loading) — hourglass for slow-time, red triangle for traps (`ADRENALINA_ZOMBI`, `FURIA_ZOMBI`), green cross for buffs (`CURA_TOTAL`, `DEBILIDAD_ZOMBI`, `FUERZA_BRUTA`).
- **Dynamic lighting:** in buildings (dark interiors), a yellow aura follows the player and a green toxic aura follows each living zombie, drawn via `Brush.radialGradient` inside the camera transform so they pan and zoom with the world.
- **Death loop:** dying in a building triggers a WASTED animation and respawns the player at the lobby door of the building they died in, with HP restored. Clearing all zombies shows a "Congratulations" overlay; using the world-exit door returns to the open world.

### 🌐 Real-Time Multiplayer

The server (`Multiplayer/server.js`) is a **Node.js + Express + ws** process that holds player and NPC state in memory and ships as a Docker image. The public instance used by the Android client is hosted on **[Render](https://render.com/)**, which builds the container straight from the repository and exposes a public WebSocket endpoint that gets injected into the build via `BuildConfig.MULTIPLAYER_SERVER_URL`.

#### Authority model: *Zone Host*

Instead of centralizing all simulation on the server (slow) or leaving it 100% to the client (incoherent), POW distributes authority via the **Zone Host** pattern:

- Every newly connected client is **Host by default** within a ~400 m radius. The server sends an initial `ROLE_UPDATE` immediately after `SESSION_INIT` so the client actually knows it's Host and can start spawning NPCs.
- If two hosts enter the same radius, the one with the lower `sessionId` yields authority. The server notifies the change with a `ROLE_UPDATE` message.
- Only the host runs the NPC AI in its zone and publishes `NPC_BATCH_UPDATE` every 100 ms. Other clients receive them passively.
- When a host yields or disconnects, its NPCs are **not destroyed**: they remain on the server with their last position, and the next host **adopts** them, re-attaching them to the local road network.

#### Robustness

- **Heartbeat:** ping every 30 s; after 6 consecutive failures (3 minutes of silence) the connection is terminated.
- **Orphan NPC garbage collector:** NPCs not updated in >15 s are deleted and announced to all clients.
- **Periodic master sync:** every 5 s the server broadcasts the list of live NPC IDs so clients can reconcile local state and clean up ghosts.
- **Colors serialized as ARGB Int** (not as the internal `ULong` of `Compose Color`) to avoid corrupted `ColorSpace` when rehydrating remote NPCs.

The client uses **OkHttp WebSocket** with `readTimeout`/`writeTimeout` set to 0 (no limit) and a `pingInterval` of 25 s to survive mobile networks with aggressive NAT.

#### Render free-tier warm-up

Render's free plan suspends idle services and takes up to ~50 s to wake them. To avoid timing out on the first WebSocket attempt, `ServerWarmupManager` (`data/network/`) polls `<server>/status` over HTTPS as soon as the user taps **MULTIJUGADOR** in the main menu — *before* the name dialog appears. While the warm-up is in flight, a non-dismissable spinner with a live elapsed-seconds counter and a **CANCEL** button blocks the menu. The dialog only opens once the server replies `200 OK`; on timeout (~60 s total budget) a **RETRY** prompt is shown. Successful pings are cached for 60 s so reopening the menu skips the wait.

### 🎮 Controls and UI

- **Two movement styles:** classic D-Pad or **virtual joystick** with free 360° rotation, switchable from Settings.
- **Adaptive scaling:** controls resize between 60% and 140%, with a dynamic ceiling of 100% in portrait mode to avoid eating the screen.
- **Left-handed mode:** movement and action controls can be swapped sides.
- **Gamepad-style action buttons** (A, B, X, Y): run, special attack, interact (vehicles, collectibles, doors), and the secondary slot used for the teleport menu (hold 3 s) in the open world or the weapon-mode toggle (hold 0.5 s) in the zombie minigame.
- **Vehicle controls:** in driving mode the D-Pad/joystick is replaced by `VehicleSteeringController` (left/right) and `VehiclePedalsController` (gas, brake, exit Y).
- **Optional diagnostic HUD:**
  - **Cache widget:** indicates whether streets come from the network, from Room, or are still loading; same for tiles.
  - **FPS widget:** real-time graphics performance meter.
- Preferences (control type, scale, swap) persist in `SharedPreferences` via `SettingsRepository`.

### 🚀 Tech Stack

| Layer | Technology |
|---|---|
| UI | Jetpack Compose, Material 3, Navigation Compose |
| Native map | osmdroid + Google Maps Compose |
| Web map | WebView + Leaflet 1.9.4 |
| Persistence | Room (v8, MIGRATION_7_8 + `fallbackToDestructiveMigration`) |
| Network | OkHttp (WebSocket), HttpURLConnection (Overpass + tiles) |
| Geolocation | Google Play Services — Fused Location Provider |
| Concurrency | Coroutines + Flow / SharedFlow / StateFlow |
| Serialization | Gson |
| Server | Node.js 18, Express, ws, Docker |
| Hosting | [Render](https://render.com/) (auto-deploy from Dockerfile) |

### 🐳 Deploying the multiplayer server

There are two ways to run the server:

**Local (Docker, for development):**

```bash
cd Multiplayer
docker compose up -d
```

**Production:** the canonical instance is deployed on **[Render](https://render.com/)** as a Web Service that auto-builds from the `Multiplayer/Dockerfile` on every push to the main branch. Render handles TLS termination, public DNS, and exposes the WebSocket endpoint over `wss://`.

In either case, the server listens on port **8080** with two endpoints:

- `GET /status` — live status (connected players, active NPCs, timestamp).
- `WS /` — game WebSocket channel.

The server URL is injected into the Android client at compile time via `BuildConfig.MULTIPLAYER_SERVER_URL` (Gradle variable).

### 📍 Current Status

What **works today** in this repository: OSM/Google/Web map navigation with snap-to-road, persistent street and tile caching, procedural NPCs (pedestrians and 6 car models), melee combat against NPCs and remote players, multiplayer with zone-delegated authority, vehicle driving, configurable controls, 8 map providers, **editable landmarks with JSON import/export (Designer Mode)**, **6 lore collectibles with persistent inventory**, **waypoint navigation with road-graph routing**, **6 ESCOM interior buildings** with collision matrices, and a **full zombie survival minigame** (7 rooms, dual combat, 6 power-ups, dynamic lighting, WASTED/Victory screens).

What is **not yet** implemented: A*-based pathfinding (current router is a greedy graph walk), local Bluetooth multiplayer, and content-rich interior collision matrices (today all 6 interiors use `CollisionGrid.emptyWithBorder()`).

---
---

## 🇪🇸 Versión en Español

**Politécnico Open World (POW)** es una aplicación Android de exploración 2D con vista *top-down* construida sobre mapas del mundo real. El jugador se desplaza por las calles reales de su ubicación (con foco inicial en la zona ESCOM / Zacatenco) usando datos cartográficos de **OpenStreetMap**, comparte el mundo con NPCs procedurales (peatones y vehículos) y con otros jugadores conectados a un servidor en tiempo real. El campus de ESCOM además alberga un minijuego embebido de supervivencia contra zombis con interiores, combate cuerpo a cuerpo / a distancia y un sistema de power-ups.

El proyecto está construido íntegramente en **Kotlin + Jetpack Compose**, sigue un patrón **MVVM** estricto organizado por *features* y delega la lógica de mundo persistente a un servidor Node.js independiente.

### ⚙️ Arquitectura

El repositorio contiene dos proyectos complementarios:

```text
.
├── PolitecnicoOpenWorld/   # Cliente Android (Kotlin + Compose)
└── Multiplayer/            # Servidor de juego (Node.js + WebSocket, dockerizado)
```

#### MVVM de un vistazo

Cada *feature* del cliente sigue la misma división en tres capas:

- **Model** (`domain/models/`): data classes inmutables (`Npc`, `MapWay`, `Landmark`, `ZombieEntity`, `CharacterVisualConfig`...) y helpers de lógica pura como `NpcAiManager`. Sin dependencias de Android, sin UI.
- **ViewModel** (`features/<nombre>/viewmodel/`): contiene un único `MutableStateFlow<State>` expuesto como `StateFlow` de solo lectura, ejecuta los bucles de juego con coroutines y orquesta los repositorios. Ejemplos: `WorldMapViewModel`, `ZombieGameViewModel`, `InteriorViewModel`, `SettingsViewModel`, `CollectiblesViewModel`, `MainMenuViewModel`.
- **View** (`features/<nombre>/ui/`): pantallas Compose puras que observan el estado con `collectAsState()` y solo emiten intenciones de usuario al ViewModel. Las vistas nunca acceden a repositorios ni DAOs directamente.

Las preocupaciones transversales (Room, red, preferencias) viven en `data/` y se inyectan a los ViewModels mediante instancias de `ViewModelProvider.Factory` co-ubicadas con cada ViewModel. Los ViewModels de nivel superior (`WorldMapViewModel`, `SettingsViewModel`, `CollectiblesViewModel`) están scopeados a la Activity para sobrevivir a la navegación; los ViewModels de interior y de zombis están scopeados a su `NavBackStackEntry` para que se reseteen cuando el jugador sale.

#### Cliente Android — organización por *features*

```text
app/src/main/java/ovh/gabrielhuav/pow/
│
├── data/                                    # ─── Capa de datos ───
│   ├── cache/
│   │   ├── RoadNetworkCache.kt              # LRU de zonas OSM (celdas ~2km, TTL 7 días)
│   │   └── TileCache.kt                     # LRU de tiles (por proveedor, máx 8K)
│   ├── local/room/
│   │   ├── PowDatabase.kt                   # @Database v8, 6 entidades, MIGRATION_7_8
│   │   ├── dao/
│   │   │   ├── RoadNetworkDao.kt            # insertZoneWithData() en transacción atómica
│   │   │   ├── MapTileDao.kt                # CRUD de caché de tiles + evicción LRU
│   │   │   ├── LandmarkDao.kt               # Edificios editables (modo diseñador)
│   │   │   └── CollectibleDao.kt            # 6 coleccionables de lore con Flow
│   │   └── entity/
│   │       ├── RoadEntities.kt              # RoadZone + RoadWay + RoadNode (FK cascade)
│   │       ├── TileEntities.kt              # MapTileEntity (BLOB + PK compuesta)
│   │       ├── LandmarkEntity.kt            # Edificios con escala, rotación, asset path
│   │       └── CollectibleEntity.kt         # id, name, description, assetPath, isCollected
│   ├── network/
│   │   └── WebSocketManager.kt              # WebSocket OkHttp (sin timeouts, ping 25s)
│   └── repository/
│       ├── OverpassRepository.kt            # Overpass API (radio 2km, timeout 45s)
│       ├── SettingsRepository.kt            # SharedPreferences para controles
│       └── CollectibleRepository.kt         # Siembra 6 items por defecto, expone Flow
│
├── domain/                                  # ─── Capa de modelo (Kotlin puro) ───
│   └── models/
│       ├── ActiveCollectible.kt             # Instancia en tiempo real de un coleccionable
│       ├── CharacterVisualConfig.kt         # Config de cabello/playera/pantalón para NPCs
│       ├── EscomBuildings.kt                # Enum InteriorBuilding (6 edificios de ESCOM)
│       ├── Landmark.kt                      # Modelo de dominio usado por el mapa
│       ├── LandmarkAssetCatalog.kt          # Catálogo JSON de assets construibles
│       ├── MapNode.kt / MapWay.kt           # Primitivas de OSM
│       ├── Npc.kt / NpcType.kt              # NPC + enum CarModel (6 modelos)
│       ├── TeleportCatalog.kt               # Destinos fijos de teletransporte
│       ├── ai/
│       │   └── NpcAiManager.kt              # Población de 40 NPCs, spawn/despawn, adopción
│       └── zombie/
│           ├── ZombieModels.kt              # ZombieEntity, SkillEffect, SkillItem,
│           │                                # Projectile, CombatMode, ZombieRoom, ZoneDoor
│           └── ZombieRoomCatalog.kt         # Lobby + 6 cuartos de edificios con puertas
│
├── features/                                # ─── Módulos de feature (Vista + ViewModel) ───
│   ├── main_menu/
│   │   ├── ui/
│   │   │   ├── MainMenuScreen.kt            # 5 botones de menú + diálogo multijugador
│   │   │   └── CollectiblesScreen.kt        # Inventario en grid con popup de detalle
│   │   └── viewmodel/
│   │       ├── MainMenuViewModel.kt         # MainMenuState (diálogo, input de nombre)
│   │       └── CollectiblesViewModel.kt     # Observa Flow de Room → StateFlow
│   │
│   ├── map_exterior/                        # Núcleo del open world
│   │   ├── ui/
│   │   │   ├── WorldMapScreen.kt            # Render del mapa para OSM / Google / Web
│   │   │   ├── CachingWebViewClient.kt      # Intercepta peticiones de tiles de Leaflet
│   │   │   └── components/
│   │   │       ├── GameControllers.kt              # D-Pad, Joystick, botones de acción,
│   │   │       │                                   # VehicleSteering, VehiclePedals
│   │   │       ├── PlayerCharacter.kt              # Sprite animado con barra de vida
│   │   │       ├── CharacterRenderer.kt            # Helper de DrawScope
│   │   │       ├── CharacterSpriteManager.kt       # Tintado inteligente por píxel + LRU
│   │   │       ├── VehicleSpriteManager.kt         # 48 frames de rotación por modelo
│   │   │       ├── NpcRenderWrapper.kt             # Desvanecimiento al morir
│   │   │       ├── PlayerAction.kt                 # Enum IDLE/WALK/RUN/SPECIAL
│   │   │       ├── AssetPickerDialog.kt            # Diseñador: seleccionar edificio
│   │   │       ├── DesignerPanel.kt                # Controles mover/rotar/escalar/guardar
│   │   │       └── CollectibleClaimDialog.kt       # Popup temático al recoger
│   │   └── viewmodel/
│   │       ├── WorldMapViewModel.kt         # Game loop, multijugador, NPCs, lógica ESCOM
│   │       └── WorldMapState.kt             # Enum MapProvider + ~30 campos de estado
│   │
│   ├── interior/                            # 6 interiores de edificios de ESCOM
│   │   ├── ui/
│   │   │   ├── InteriorScreenBase.kt        # Composable compartido: fondo + jugador + DPad
│   │   │   ├── AuditorioScreen.kt
│   │   │   ├── BibliotecaScreen.kt
│   │   │   ├── CafeteriaScreen.kt
│   │   │   ├── EdificioScreen.kt
│   │   │   ├── EstacionamientoScreen.kt
│   │   │   └── PalapasScreen.kt
│   │   └── viewmodel/
│   │       ├── InteriorViewModel.kt         # Movimiento normalizado [0,1] con colisiones
│   │       ├── InteriorState.kt
│   │       └── CollisionGrid.kt             # Matriz caminable 20×30, con emptyWithBorder()
│   │
│   ├── zombie_minigame/                     # Minijuego de supervivencia embebido
│   │   ├── ui/
│   │   │   ├── ZombieGameScreen.kt          # Render del mundo en Canvas + cámara
│   │   │   ├── ZombieHud.kt                 # HUD, puertas, iconos SkillItem (Canvas puro)
│   │   │   └── ZombieSpriteManager.kt       # LRU de animación de 9 frames del zombi
│   │   └── viewmodel/
│   │       ├── ZombieGameViewModel.kt       # IA, proyectiles, efectos, victoria/derrota
│   │       └── ZombieGameState.kt           # Estado + CameraTransform
│   │
│   └── settings/
│       ├── ui/
│       │   └── SettingsScreen.kt            # Tabs: Mapa / Controles / Jugabilidad / Interfaz
│       ├── viewmodel/
│       │   ├── SettingsViewModel.kt         # Persiste vía SettingsRepository
│       │   └── SettingsState.kt
│       └── models/
│           ├── ControlType.kt               # DPAD / JOYSTICK
│           └── SettingsCategory.kt          # Sealed class para navegación por tabs
│
├── ui/theme/                                # Tema Material 3
└── MainActivity.kt                          # Single-Activity con NavHost de Compose
```

El cliente sigue una arquitectura **Single-Activity** con navegación basada en `NavHost` de Compose y **nueve** destinos: `main_menu`, `world_map`, `settings`, `collectibles`, las seis rutas de interior (`interior_auditorio`, `interior_biblioteca`, `interior_cafeteria`, `interior_edificio`, `interior_estacionamiento`, `interior_palapas`) y la ruta `zombie_minigame`.

### 🗺️ Sistema de Mapas

POW soporta **ocho proveedores de mapas** intercambiables en caliente desde Ajustes:

| Proveedor | Modo | Notas |
|---|---|---|
| OSMDroid (Nativo) | Render nativo | Mayor zoom (hasta 21) |
| Google Maps (Nativo) | Google Maps SDK | Usa `MAPS_API_KEY` del manifest |
| OpenStreetMap (Web) | WebView + Leaflet | |
| Google Maps (Web) | WebView + Leaflet | |
| CartoDB Oscuro / Claro | WebView + Leaflet | Estética de videojuego |
| Esri World Street | WebView + Leaflet | |
| Esri Satélite | WebView + Leaflet | Vista aérea real |
| OpenTopoMap | WebView + Leaflet | Relieve y curvas de nivel |

Los modos Web se renderizan con **Leaflet** dentro de un `WebView` y son interceptados por un `CachingWebViewClient` que cachea cada tile en Room (`MapTileEntity`) usando la URL normalizada como clave (eliminando subdominios de balanceo y parámetros volátiles antes de hashear con SHA-256). Esto permite jugar sin conexión cualquier zona previamente visitada.

#### Caché de red de calles

La red de calles necesaria para anclar movimiento y NPCs se obtiene de la **Overpass API** y se persiste en Room con la siguiente estrategia:

- **Granularidad por celda:** la superficie del mundo se divide en celdas de ~2 km × 2 km. Cada celda descargada cubre un radio de ~2 km alrededor del jugador.
- **TTL de 7 días** antes de marcar la celda como expirada.
- **LRU de 20 celdas** máximo: al llenarse, se descarta la más antigua.
- **Transacción atómica** (`@Transaction`) al insertar zona + ways + nodos para evitar estados corruptos si el proceso muere a media escritura.
- **Re-fetch cooldown:** una vez descargada una zona, no se vuelve a pedir a Overpass durante 5 minutos aunque el jugador la siga cruzando.

El jugador no puede salirse de las vías: cada movimiento es validado contra un **índice espacial en grid** (`Seg` + `HashMap<celda, segmentos>`) que ejecuta *snap-to-road* en O(candidatos cercanos) en lugar de O(n) sobre todas las calles.

### 🏛️ Landmarks y Modo Diseñador

El mapa se puebla con edificios editables. El pipeline vive en tres lugares:

- **`LandmarkCatalogManager`** (`domain/models/`) carga `assets/buildings_catalog.json` al arranque, definiendo cada asset colocable (nombre visible, ruta del asset, tamaño base en metros, escala por defecto).
- **`LandmarkDao` + `LandmarkEntity`** persisten las colocaciones del usuario (posición, rotación 0-360°, escala 0.05-3.0×) entre sesiones.
- **Modo Diseñador** (toggle en la barra) activa el `DesignerPanel`: botones de flecha para movimiento fino (±0.0001°), sliders de rotación y escala, más **exportar/importar JSON** para compartir configuraciones del mapa como archivos.

En el primer arranque, `assets/default_landmarks.json` siembra la base de datos con el campus de ESCOM.

### 🎁 Sistema de Coleccionables

Seis coleccionables temáticos de lore (logo IPN, lema UAM, "Huélum" de ESIME, examen ETS, laptop ESCOM, "Apuntes de Leyenda") se siembran en Room mediante `CollectibleRepository.initializeDefaultCollectiblesIfNeeded()`. El game loop spawnea un item no recogido cada ~1 s en un radio de 300-600 m del jugador, ajustado a la red de calles. Al acercarse a menos de 15 m aparece el prompt "PRESIONA X PARA RECOGER"; al pulsar X se marca el item como recogido y se muestra un `CollectibleClaimDialog` con la descripción de lore. La pantalla de inventario lee de un `Flow<List<CollectibleEntity>>`, así que los items recogidos aparecen en color mientras los no recogidos quedan en gris con "???".

Un séptimo item especial — la **Mano Zombi** (`Objeto Misterioso ESCOM`) — solo aparece cuando el jugador está dentro del bounding box de ESCOM. Interactuar con ella dispara una cinemática (`Carga_Mod_Zombi.mp4`) y luego navega al minijuego de zombis.

### 🚶 NPCs y Vehículos

`NpcAiManager` mantiene una población de hasta **40 NPCs** alrededor del jugador, repartidos en dos tipos:

- **Peatones:** caminan sobre vías peatonales (`footway`, `pedestrian`, `path`, `residential`...). Cada uno se ensambla en tiempo real combinando un cuerpo base, un sprite de cabello (`hair_1`...`hair_4`) y tres colores aleatorios (cabello, playera, pantalón) usando una técnica de **tintado inteligente por píxel** que respeta la piel (filtro por saturación) y separa playera/pantalón por luminancia.
- **Vehículos:** 6 modelos (`SEDAN`, `SPORT`, `SUPERCAR`, `SUV`, `VAN`, `WAGON`) con 48 frames de rotación cada uno (uno cada 7.5°). El color se aplica también por píxel preservando luces, intermitentes y rines mediante un análisis de saturación + luminancia.

El comportamiento incluye spawn por proximidad, despawn por distancia (>35 m equivalentes), navegación nodo-a-nodo con transición suave entre ways conectadas, y un sistema de **adopción**: si un NPC entra a la zona sin tener calle asignada (porque fue heredado del servidor), se le engancha automáticamente a la vía más cercana antes de moverlo.

#### Combate contra NPCs

Pulsar **B** dispara un ataque especial con cooldown de 2.4 s que golpea al NPC más cercano dentro de ~17 m. Los NPCs tienen 100 HP y reciben 15 puntos de daño por golpe, mostrando una barra de vida contextual en `NpcRenderWrapper`. Al morir, el NPC se desvanece durante 1 s y se elimina. El daño contra jugadores remotos se enruta vía un mensaje `PLAYER_DAMAGE` por WebSocket para que la autoridad quede en el cliente de la víctima.

#### Subirse y bajarse de vehículos

Pulsando el botón **X** cerca de un vehículo, el jugador lo "toma": el NPC desaparece de la lista y el jugador pasa a renderizarse como ese coche, con rotación libre en 360° según el ángulo de movimiento. Volver a pulsar X deja el coche estacionado como NPC en la posición actual. La primera vez que un jugador se sube a un vehículo, se spawnea un conductor expulsado junto a él.

### 🧭 Navegación y Waypoints

El jugador puede colocar un marcador de destino en cualquier punto del mapa: el botón verde de "Waypoint" entra al modo apuntado (una cruz roja se centra en pantalla), y "Establecer Destino" confirma la ubicación. El ViewModel ejecuta una búsqueda greedy sobre el grafo de calles (`calculateRouteOnNetwork`) usando un grid espacial de nodos únicos y dibuja la ruta como una polilínea azul punteada. El marcador se auto-elimina cuando el jugador llega a 20 m.

### 🧟 Minijuego de Zombis

Entrar al minijuego mete al jugador en un **anillo circular de 7 cuartos**: un lobby (croquis `Campus ESCOM`) con 6 puertas hacia cada edificio de ESCOM. Dentro de un edificio, las puertas EXIT izquierda y derecha conectan con el edificio anterior/siguiente del anillo, mientras que una puerta central regresa al lobby (con diálogo de confirmación para evitar salidas accidentales).

- **Sistema de cámara** (`CameraTransform`): consciente del zoom, sigue al jugador, se ajusta a los límites del mundo y usa `max(viewW/worldW, viewH/worldH)` para que el fondo llene la pantalla sin deformarse (equivalente matemático de `ContentScale.Crop`).
- **IA:** los zombis hacen pathfinding hacia el jugador con sliding consciente de colisiones (intento eje X, fallback eje Y). Se animan a través de 9 frames de caminata a ~140 ms por frame.
- **Modo de combate dual:** el **botón Y** mantenido 500 ms abre un menú de armas para alternar entre **MELEE** (puñetazo, radio 120 px, 34 de daño) y **RANGED** (proyectiles a 22 px/tick, 50 de daño, cooldown 350 ms). El **botón B** ejecuta el ataque.
- **Drops de SkillEffect:** los zombis al morir sueltan uno de seis efectos con 45% de probabilidad, renderizados como **iconos vectoriales puros con Canvas** (sin cargar assets) — reloj de arena para ralentizar tiempo, triángulo rojo para trampas (`ADRENALINA_ZOMBI`, `FURIA_ZOMBI`), cruz verde para buffs (`CURA_TOTAL`, `DEBILIDAD_ZOMBI`, `FUERZA_BRUTA`).
- **Iluminación dinámica:** en los edificios (interiores oscuros), un aura amarilla sigue al jugador y un aura verde tóxica sigue a cada zombi vivo, dibujadas con `Brush.radialGradient` dentro de la transformación de cámara para que se desplacen y escalen con el mundo.
- **Loop de muerte:** morir en un edificio dispara una animación WASTED y respawnea al jugador en la puerta del lobby correspondiente al edificio donde murió, con HP restaurado. Acabar con todos los zombis muestra un overlay "Congratulations"; usar la puerta de salida al mundo regresa al open world.

### 🌐 Multijugador en Tiempo Real

El servidor (`Multiplayer/server.js`) es un proceso **Node.js + Express + ws** que mantiene en memoria el estado de jugadores y NPCs y se distribuye como imagen Docker. La instancia pública que usa el cliente Android está hospedada en **[Render](https://render.com/)**, que construye el contenedor directamente desde el repositorio y expone un endpoint WebSocket público que se inyecta en el build mediante `BuildConfig.MULTIPLAYER_SERVER_URL`.

#### Modelo de autoridad: *Zone Host*

En lugar de centralizar toda la simulación en el servidor (lento) o dejarla 100% al cliente (incoherente), POW reparte la autoridad mediante el patrón **Host de Zona**:

- Cada cliente recién conectado es **Host por defecto** dentro de un radio de ~400 m. El servidor envía un `ROLE_UPDATE` inicial inmediatamente después de `SESSION_INIT` para que el cliente sepa que es Host y pueda empezar a spawnear NPCs.
- Si dos hosts entran en el mismo radio, el de menor `sessionId` cede la autoridad. El servidor notifica el cambio con un mensaje `ROLE_UPDATE`.
- Solo el host ejecuta la IA de los NPCs en su zona y publica `NPC_BATCH_UPDATE` cada 100 ms. Los demás clientes los reciben pasivamente.
- Cuando un host cede o se desconecta, sus NPCs **no se destruyen**: quedan en el servidor con su última posición y el siguiente host los **adopta**, reenganchándolos a la red de calles local.

#### Robustez

- **Heartbeat:** ping cada 30 s; tras 6 fallos consecutivos (3 minutos sin respuesta) se termina la conexión.
- **Garbage collector de NPCs huérfanos:** NPCs sin actualizar en >15 s se eliminan y se anuncian a todos los clientes.
- **Master sync periódico:** cada 5 s el servidor difunde la lista de IDs de NPCs vivos para que los clientes reconcilien su estado local y limpien fantasmas.
- **Colores serializados como Int ARGB** (no como `ULong` interno de `Compose Color`) para evitar `ColorSpace` corrupto al rehidratar NPCs remotos.

El cliente usa **OkHttp WebSocket** con `readTimeout`/`writeTimeout` en 0 (sin límite) y `pingInterval` de 25 s para sobrevivir a redes móviles con NAT agresivo.

#### Warm-up del plan gratuito de Render

El plan gratuito de Render suspende los servicios inactivos y tarda hasta ~50 s en despertarlos. Para no agotar el timeout en el primer intento de WebSocket, `ServerWarmupManager` (`data/network/`) hace polling al `/status` por HTTPS apenas el usuario toca **MULTIJUGADOR** en el menú principal — *antes* de que aparezca el diálogo de nombre. Mientras dura el warm-up, un spinner no descartable con contador de segundos en vivo y botón **CANCELAR** bloquea el menú. El diálogo solo se abre cuando el servidor responde `200 OK`; si hay timeout (~60 s de presupuesto total) se muestra una opción de **REINTENTAR**. Los pings exitosos se cachean por 60 s, así que reabrir el menú salta la espera.

### 🎮 Controles e Interfaz

- **Dos estilos de movimiento:** D-Pad clásico o **Joystick virtual** con rotación libre 360°, configurables desde Ajustes.
- **Escala adaptativa:** los controles se redimensionan entre 60% y 140%, con un límite dinámico de 100% en modo retrato para no comerse la pantalla.
- **Modo zurdo:** los controles de movimiento y acción se pueden intercambiar de lado.
- **Botones de acción** estilo gamepad (A, B, X, Y): correr, ataque especial, interactuar (vehículos, coleccionables, puertas) y el slot secundario, usado para el menú de teletransporte (mantener 3 s) en el open world o para alternar modo de arma (mantener 0.5 s) en el minijuego de zombis.
- **Controles de vehículo:** en modo conducción el D-Pad/joystick se reemplaza por `VehicleSteeringController` (izquierda/derecha) y `VehiclePedalsController` (gas, freno, salir Y).
- **HUD de diagnóstico opcional:**
  - **Widget de caché:** indica si las calles vienen de la red, de Room o si están cargando; idem para los tiles.
  - **Widget de FPS:** medidor de rendimiento gráfico en tiempo real.
- Las preferencias (tipo de control, escala, swap) se persisten en `SharedPreferences` vía `SettingsRepository`.

### 🚀 Stack Tecnológico

| Capa | Tecnología |
|---|---|
| UI | Jetpack Compose, Material 3, Navigation Compose |
| Mapa nativo | osmdroid + Google Maps Compose |
| Mapa web | WebView + Leaflet 1.9.4 |
| Persistencia | Room (v8, MIGRATION_7_8 + `fallbackToDestructiveMigration`) |
| Red | OkHttp (WebSocket), HttpURLConnection (Overpass + tiles) |
| Geolocalización | Google Play Services — Fused Location Provider |
| Concurrencia | Coroutines + Flow / SharedFlow / StateFlow |
| Serialización | Gson |
| Servidor | Node.js 18, Express, ws, Docker |
| Hosting | [Render](https://render.com/) (auto-deploy desde Dockerfile) |

### 🐳 Desplegar el servidor multijugador

Hay dos formas de levantar el servidor:

**Local (Docker, para desarrollo):**

```bash
cd Multiplayer
docker compose up -d
```

**Producción:** la instancia canónica está desplegada en **[Render](https://render.com/)** como un Web Service que se reconstruye automáticamente desde el `Multiplayer/Dockerfile` en cada push a la rama principal. Render se encarga del TLS, el DNS público y expone el endpoint WebSocket por `wss://`.

En ambos casos el servidor escucha en el puerto **8080** con dos endpoints:

- `GET /status` — estado en vivo (jugadores conectados, NPCs activos, timestamp).
- `WS /` — canal WebSocket del juego.

La URL del servidor se inyecta al cliente Android en tiempo de compilación mediante `BuildConfig.MULTIPLAYER_SERVER_URL` (variable de Gradle).

### 📍 Estado actual

Lo que **funciona hoy** en el código de este repositorio: navegación sobre OSM/Google/Web con snap-to-road, caché persistente de calles y tiles, NPCs procedurales (peatones y 6 modelos de coches), combate cuerpo a cuerpo contra NPCs y jugadores remotos, multijugador con autoridad delegada por zona, conducción de vehículos, controles configurables, 8 proveedores de mapas, **landmarks editables con import/export JSON (Modo Diseñador)**, **6 coleccionables de lore con inventario persistente**, **navegación por waypoints con routing sobre grafo de calles**, **6 interiores de edificios de ESCOM** con matrices de colisión, y un **minijuego completo de supervivencia contra zombis** (7 cuartos, combate dual, 6 power-ups, iluminación dinámica, pantallas WASTED/Victoria).

Lo que **no** está implementado todavía: pathfinding con A* (el router actual es una búsqueda greedy sobre el grafo), multijugador local por Bluetooth, y matrices de colisión ricas en contenido para los interiores (hoy los 6 interiores usan `CollisionGrid.emptyWithBorder()`).
