# Politécnico Open World (POW)

> 🇬🇧 **English version below** · 🇪🇸 [Saltar a la versión en español](#-versión-en-español)

---

## 🇬🇧 English Version

**Politécnico Open World (POW)** is an Android 2D top-down exploration app built on top of real-world maps. The player navigates the actual streets of their surroundings (with initial focus on the ESCOM / Zacatenco area in Mexico City) using **OpenStreetMap** cartographic data, sharing the world with procedural NPCs (pedestrians and vehicles) and with other players connected to a real-time server. The ESCOM campus also hosts an embedded zombie survival minigame with interior buildings, melee/ranged combat and a power-up system.

The project is built entirely with **Kotlin + Jetpack Compose**, follows a strict **MVVM** pattern organized by *features*, and delegates persistent-world logic to a standalone Node.js server.

### 🔄 Recent Changes

The latest integration branch repaired a broken merge and bundled several feature/performance PRs:

- **Atomic tile-cache writes** — tile persistence now counts, evicts (LRU) and inserts inside a single Room `@Transaction`, preventing corruption if the process dies mid-write.
- **Zombie minigame balance** — shorter zombie contact-attack range and gradual **health regeneration in the lobby** (safe zone) up to 100 HP.
- **Damage feedback FX** — screen shake on hit, a red damage vignette/flash whose intensity **scales with lost HP**, a low-HP pulse, **zombie knockback** (melee + projectiles, collision-aware) and **player recoil** on firing with per-axis position correction.
- **Staged control settings** — control changes (type/scale/swap) are now held in a temporary state and only affect gameplay after pressing **SAVE**.
- **Map rotation fixes** — dark OSMDroid background and an oversized, centered Leaflet wrapper so no "gaps" appear when the map rotates in driving mode.
- **Performance** — per-*way* bounding-box pre-filter in the NPC spawner and `Pair`-based routing keys (no per-step string allocations).

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
│   │   └── TileCache.kt                     # LRU of map tiles (per provider, 8K max, atomic writes)
│   ├── local/room/
│   │   ├── PowDatabase.kt                   # @Database v8, 6 entities, MIGRATION_7_8
│   │   ├── dao/
│   │   │   ├── RoadNetworkDao.kt            # Atomic insertZoneWithData() transaction
│   │   │   ├── MapTileDao.kt                # Tile cache CRUD + atomic putTileAtomic() (count/evict/insert)
│   │   │   ├── LandmarkDao.kt               # User-editable buildings (designer mode)
│   │   │   └── CollectibleDao.kt            # 6 lore collectibles with Flow observation
│   │   └── entity/...                       # RoadEntities, TileEntities, LandmarkEntity, CollectibleEntity
│   ├── network/
│   │   └── WebSocketManager.kt              # OkHttp WS (no timeouts, 25s ping)
│   └── repository/
│       ├── OverpassRepository.kt            # Overpass API (2km radius, 45s timeout)
│       ├── SettingsRepository.kt            # SharedPreferences for controls
│       └── CollectibleRepository.kt         # Seeds 6 default items, exposes Flow
│
├── domain/                                  # ─── Model layer (pure Kotlin) ───
│   └── models/
│       ├── CharacterVisualConfig.kt         # Hair/shirt/pants config for assembled NPCs
│       ├── EscomBuildings.kt                # InteriorBuilding enum (6 ESCOM buildings)
│       ├── Landmark.kt / LandmarkAssetCatalog.kt
│       ├── MapNode.kt / MapWay.kt           # OSM primitives
│       ├── Npc.kt / NpcType.kt              # NPC + CarModel enum (6 models)
│       ├── ai/NpcAiManager.kt              # 40-NPC population, bbox-prefiltered spawn, adoption
│       └── zombie/
│           ├── ZombieModels.kt              # ZombieEntity, SkillEffect, Projectile, ZombieRoom, ZoneDoor
│           └── ZombieRoomCatalog.kt         # Lobby + 6 building rooms with door layout
│
├── features/                                # ─── Feature modules (View + ViewModel) ───
│   ├── main_menu/                           # menu + multiplayer dialog + warm-up
│   ├── map_exterior/                        # core open world (WorldMapScreen/ViewModel/State)
│   ├── interior/                            # 6 ESCOM building interiors
│   ├── zombie_minigame/                     # embedded survival minigame
│   ├── shinecto/                            # ShineCTO easter-egg interior
│   └── settings/                            # Map / Controls / Gameplay / Interface tabs
│
├── ui/theme/                                # Material 3 theme
└── MainActivity.kt                          # Single-Activity with Compose NavHost
```

The client follows a **Single-Activity** architecture with Compose-based navigation (`NavHost`). Destinations include `main_menu`, `world_map`, `settings`, `collectibles`, the six interior routes, the `zombie_minigame` route and the `shinecto_interior` route.

### 🗺️ Map System

POW supports **eight map providers** that can be hot-swapped from the Settings screen:

| Provider | Mode | Notes |
|---|---|---|
| OSMDroid (Native) | Native render | Highest zoom (up to 21); dark background to avoid rotation gaps |
| Google Maps (Native) | Google Maps SDK | Uses `MAPS_API_KEY` from manifest |
| OpenStreetMap (Web) | WebView + Leaflet | |
| Google Maps (Web) | WebView + Leaflet | |
| CartoDB Dark / Light | WebView + Leaflet | Video-game aesthetic |
| Esri World Street | WebView + Leaflet | |
| Esri Satellite | WebView + Leaflet | Real aerial view |
| OpenTopoMap | WebView + Leaflet | Terrain and contour lines |

Web modes render through **Leaflet** inside a `WebView` and are intercepted by a `CachingWebViewClient` that caches each tile in Room (`MapTileEntity`) using the normalized URL as key (stripping load-balancing subdomains and volatile parameters before hashing with SHA-256). This allows offline play in any previously visited area. The Leaflet `#map-wrapper` is intentionally oversized (`300vw × 300vh`, centered) so its inscribed circle covers the screen diagonal at any rotation angle — no "gaps"/artifacts appear when the map rotates in driving mode.

#### Tile & road-network caching

- **Tiles:** per-provider LRU (~8k max). Writes are **atomic**: a single Room `@Transaction` counts the provider's tiles, evicts the oldest (LRU) if over the limit, and inserts the new tile — preventing corrupt states if the process dies mid-write.
- **Road network (Overpass):** ~2 km cells, **7-day TTL**, **LRU of 20 cells**, atomic zone+ways+nodes insert (`@Transaction`), and a 5-minute re-fetch cooldown.

The player cannot step off the road network: every movement is validated against a **spatial grid index** (`Seg` + `HashMap<cell, segments>`) that runs *snap-to-road* in O(nearby candidates).

### 🏛️ Landmarks and Designer Mode

The map is populated with editable buildings: a JSON asset catalog (`buildings_catalog.json`), persisted placements in `LandmarkEntity` (position, rotation 0-360°, scale 0.05-3.0×), and a **Designer Mode** panel with fine movement, rotation/scale sliders and JSON export/import. `assets/default_landmarks.json` seeds the ESCOM layout on first launch.

### 🎁 Collectibles System

Six lore-themed collectibles are seeded into Room. The game loop spawns one uncollected item every ~1 s within 300–600 m of the player, snapped to roads. Approaching within 15 m shows a pickup prompt; pressing X collects it and shows a themed dialog. The inventory reads from a `Flow<List<CollectibleEntity>>`. A special **Zombie Hand** item only spawns inside the ESCOM bounding box and triggers a cutscene into the zombie minigame.

### 🚶 NPCs and Vehicles

`NpcAiManager` maintains up to **40 NPCs** around the player: **pedestrians** (assembled at runtime with per-pixel tinting) and **vehicles** (6 models, 48 rotation frames each). Behavior includes proximity spawning, distance despawn, node-to-node navigation and an **adoption** system for server-inherited NPCs. The spawner now precomputes a **per-way bounding box** and uses an O(1) bbox pre-filter before the expensive per-node distance check, reducing CPU. Pressing **B** triggers a special attack (~17 m, 15 dmg/punch); pressing **X** near a vehicle boards it for free 360° driving.

### 🧭 Navigation and Waypoints

The player can drop a destination marker; the ViewModel runs a greedy road-graph search (`calculateRouteOnNetwork`) over a spatial grid of unique nodes and renders a dashed blue polyline. Routing uses `Pair<Double, Double>` keys for visited/distinct nodes (no per-step string allocations). The marker auto-clears within 20 m.

### 🧟 Zombie Minigame

A circular **ring of 7 rooms**: a lobby with 6 doors to each ESCOM building. Inside a building, EXIT doors connect to neighbors and a central door returns to the lobby.

- **Camera** (`CameraTransform`): zoom-aware, clamps to bounds, uses `max(viewW/worldW, viewH/worldH)` (ContentScale.Crop equivalent).
- **AI:** collision-aware pathfinding (axis slide), 9-frame walk animation. **Contact-attack range was reduced** for fairer melee.
- **Lobby regeneration:** while in the lobby below 100 HP, the player heals gradually each tick up to 100.
- **Dual combat:** hold **Y** (500 ms) to toggle MELEE/RANGED; **B** attacks.
- **Damage feedback:** screen shake on hit, a red **damage vignette/flash that scales with lost HP**, a **low-HP pulse**, **zombie knockback** on melee/projectile hits (collision-aware), and **player recoil** on firing with per-axis position correction so the player never clips through walls.
- **SkillEffect drops:** six effects (45% chance) drawn as pure Canvas icons.
- **Dynamic lighting** in dark interiors; **WASTED/Victory** screens.

### 🌐 Real-Time Multiplayer

The server (`Multiplayer/server.js`) is a **Node.js + Express + ws** process (dockerized, hosted on Render). It distributes authority via the **Zone Host** pattern (each client is Host within ~400 m; lower `sessionId` yields on overlap; only the Host runs NPC AI and emits `NPC_BATCH_UPDATE`; orphaned NPCs are adopted, not destroyed). Robustness: heartbeats, orphan-NPC GC, periodic master sync, ARGB-Int color serialization. The client uses OkHttp WebSocket (no read/write timeouts, 25 s ping).

#### Render free-tier warm-up

`ServerWarmupManager` polls `<server>/status` over HTTPS the moment the user taps **MULTIJUGADOR** — before the name dialog — blocking with a cancellable spinner until the server replies `200 OK` (with a retry on timeout). Successful pings are cached for 60 s.

### 🎮 Controls and UI

- **Two movement styles:** D-Pad or virtual joystick (360°), switchable from Settings.
- **Adaptive scaling** (60%–140%, capped at 100% in portrait), **left-handed swap**, gamepad-style A/B/X/Y buttons.
- **Staged control settings:** changes to control type, scale and swap are held in a temporary state and only affect gameplay after pressing **SAVE** (then committed, persisted and pushed to the map). Leaving Settings discards unsaved changes.
- **Optional diagnostic HUD:** cache widget and FPS widget. Preferences persist via `SettingsRepository` (SharedPreferences).

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

```bash
cd Multiplayer
docker compose up -d   # local (development)
```

Production is a Render Web Service auto-built from `Multiplayer/Dockerfile`. The server listens on port **8080** (`GET /status`, `WS /`); the URL is injected at compile time via `BuildConfig.MULTIPLAYER_SERVER_URL`.

### 📍 Current Status

**Works today:** OSM/Google/Web navigation with snap-to-road, persistent street and tile caching (atomic writes), procedural NPCs (bbox-prefiltered spawn), melee combat vs NPCs and remote players, zone-delegated multiplayer, vehicle driving, staged configurable controls, 8 map providers, editable landmarks with JSON import/export, 6 lore collectibles, waypoint routing, 6 ESCOM interiors, a full zombie survival minigame (7 rooms, dual combat, 6 power-ups, dynamic lighting, damage-feedback FX, lobby regen, WASTED/Victory screens), and a ShineCTO easter-egg interior.

**Not yet implemented:** A*-based pathfinding (current router is greedy), local Bluetooth multiplayer, content-rich interior collision matrices, a dedicated zombie-minigame multiplayer server, floating damage numbers / hit particles, and lobby door coordinate re-tuning.

---
---

## 🇪🇸 Versión en Español

**Politécnico Open World (POW)** es una aplicación Android de exploración 2D con vista *top-down* construida sobre mapas del mundo real. El jugador se desplaza por las calles reales de su ubicación (con foco inicial en la zona ESCOM / Zacatenco) usando datos de **OpenStreetMap**, comparte el mundo con NPCs procedurales (peatones y vehículos) y con otros jugadores conectados a un servidor en tiempo real. El campus de ESCOM alberga además un minijuego embebido de supervivencia contra zombis con interiores, combate cuerpo a cuerpo / a distancia y un sistema de power-ups.

El proyecto está construido íntegramente en **Kotlin + Jetpack Compose**, sigue un patrón **MVVM** estricto organizado por *features* y delega la lógica de mundo persistente a un servidor Node.js independiente.

### 🔄 Cambios Recientes

La última rama de integración reparó un merge roto e incorporó varios PRs de funcionalidad/rendimiento:

- **Escritura atómica de la caché de tiles** — la persistencia ahora cuenta, hace evict (LRU) e inserta dentro de una sola transacción de Room (`@Transaction`), evitando corrupción si el proceso muere a media escritura.
- **Balance del minijuego zombi** — menor rango de ataque por contacto y **regeneración gradual de vida en el lobby** (zona segura) hasta 100 HP.
- **Efectos de daño** — *screen shake* al recibir golpes, viñeta/flash rojo cuya intensidad **escala con la vida perdida**, pulso de vida baja, **knockback a los zombis** (melee + proyectiles, consciente de colisiones) y **recoil del jugador** al disparar con corrección de posición por eje.
- **Controles en estado temporal** — los cambios de control (tipo/escala/swap) se quedan en estado temporal y solo afectan al juego al presionar **GUARDAR**.
- **Arreglo de rotación del mapa** — fondo oscuro en OSMDroid y wrapper de Leaflet sobredimensionado y centrado para que no se vean "huecos" al rotar en modo conducción.
- **Rendimiento** — pre-filtro por *bounding box* de cada *way* en el spawner de NPCs y claves de routing basadas en `Pair` (sin allocs de string por paso).

### ⚙️ Arquitectura

El repositorio contiene dos proyectos complementarios:

```text
.
├── PolitecnicoOpenWorld/   # Cliente Android (Kotlin + Compose)
└── Multiplayer/            # Servidor de juego (Node.js + WebSocket, dockerizado)
```

#### MVVM de un vistazo

Cada *feature* del cliente se divide en tres capas:

- **Model** (`domain/models/`): data classes inmutables y helpers de lógica pura (`NpcAiManager`). Sin dependencias de Android ni UI.
- **ViewModel** (`features/<nombre>/viewmodel/`): un único `MutableStateFlow<State>` expuesto como `StateFlow` de solo lectura; ejecuta los bucles de juego con coroutines y orquesta repositorios.
- **View** (`features/<nombre>/ui/`): pantallas Compose puras que observan con `collectAsState()` y solo emiten intenciones al ViewModel. Nunca acceden a repositorios ni DAOs.

Las preocupaciones transversales (Room, red, preferencias) viven en `data/` y se inyectan vía `ViewModelProvider.Factory`. Los ViewModels de nivel superior están scopeados a la Activity; los de interior y zombi a su `NavBackStackEntry`.

### 🗺️ Sistema de Mapas

POW soporta **ocho proveedores de mapas** intercambiables en caliente desde Ajustes (OSMDroid nativo con fondo oscuro para evitar huecos al rotar, Google nativo, y seis modos Web con Leaflet: OSM, Google, CartoDB Oscuro/Claro, Esri Street, Esri Satélite, OpenTopoMap).

Los modos Web se renderizan con **Leaflet** dentro de un `WebView` interceptado por un `CachingWebViewClient` que cachea cada tile en Room usando la URL normalizada como clave (hash SHA-256). El `#map-wrapper` de Leaflet está intencionalmente sobredimensionado (`300vw × 300vh`, centrado) para que su círculo inscrito cubra la diagonal de la pantalla en cualquier ángulo de rotación, evitando "huecos".

#### Caché de tiles y de red de calles

- **Tiles:** LRU por proveedor (~8k máx). La escritura es **atómica**: una sola transacción de Room cuenta los tiles del proveedor, hace evict del más viejo (LRU) si excede el máximo, e inserta el nuevo tile, evitando estados corruptos si el proceso muere a media escritura.
- **Red de calles (Overpass):** celdas de ~2 km, **TTL de 7 días**, **LRU de 20 celdas**, inserción atómica de zona+ways+nodos (`@Transaction`) y cooldown de re-fetch de 5 minutos.

El jugador no puede salirse de las vías: cada movimiento se valida contra un **índice espacial en grid** (`Seg` + `HashMap<celda, segmentos>`) que ejecuta *snap-to-road* en O(candidatos cercanos).

### 🏛️ Landmarks y Modo Diseñador

Edificios editables con catálogo JSON (`buildings_catalog.json`), colocaciones persistidas (posición, rotación 0-360°, escala 0.05-3.0×) y un **Modo Diseñador** con movimiento fino, sliders de rotación/escala e import/export JSON. `assets/default_landmarks.json` siembra el campus de ESCOM al primer arranque.

### 🎁 Sistema de Coleccionables

Seis coleccionables de lore sembrados en Room. El game loop spawnea uno no recogido cada ~1 s en 300–600 m del jugador, ajustado a las calles. A menos de 15 m aparece el prompt; al pulsar X se recoge y se muestra un diálogo temático. El inventario lee de un `Flow<List<CollectibleEntity>>`. Una **Mano Zombi** especial solo aparece dentro del bounding box de ESCOM y dispara la cinemática hacia el minijuego.

### 🚶 NPCs y Vehículos

`NpcAiManager` mantiene hasta **40 NPCs**: peatones (ensamblados en tiempo real con tintado por píxel) y vehículos (6 modelos, 48 frames de rotación). Incluye spawn por proximidad, despawn por distancia, navegación nodo-a-nodo y **adopción** de NPCs heredados del servidor. El spawner ahora precomputa un **bounding box por way** y usa un pre-filtro O(1) por bbox antes del costoso check por nodo, reduciendo CPU. **B** dispara un ataque especial (~17 m, 15 de daño); **X** cerca de un vehículo lo aborda para conducir en 360°.

### 🧭 Navegación y Waypoints

El jugador coloca un marcador de destino; el ViewModel ejecuta una búsqueda greedy sobre el grafo de calles (`calculateRouteOnNetwork`) y dibuja una polilínea azul punteada. El routing usa claves `Pair<Double, Double>` para nodos visitados/únicos (sin allocs de string por paso). El marcador se auto-elimina a 20 m.

### 🧟 Minijuego de Zombis

Anillo circular de **7 cuartos**: un lobby con 6 puertas a cada edificio de ESCOM, con puertas EXIT entre vecinos y una central de regreso al lobby.

- **Cámara** (`CameraTransform`): consciente del zoom, se ajusta a límites, usa `max(viewW/worldW, viewH/worldH)`.
- **IA:** pathfinding con sliding por eje y animación de 9 frames. **El rango de ataque por contacto se redujo** para un melee más justo.
- **Regeneración en el lobby:** estando en el lobby por debajo de 100 HP, el jugador se cura gradualmente por tick hasta 100.
- **Combate dual:** mantener **Y** (500 ms) alterna MELEE/RANGED; **B** ataca.
- **Efectos de daño:** *screen shake* al recibir golpes, **viñeta/flash rojo que escala con la vida perdida**, **pulso de vida baja**, **knockback a los zombis** en golpes/proyectiles (consciente de colisiones) y **recoil del jugador** al disparar con corrección de posición por eje para no atravesar paredes.
- **Drops de SkillEffect:** seis efectos (45%) dibujados como iconos de Canvas puros.
- **Iluminación dinámica** en interiores oscuros; pantallas **WASTED/Victoria**.

### 🌐 Multijugador en Tiempo Real

El servidor (`Multiplayer/server.js`) es un proceso **Node.js + Express + ws** dockerizado en Render. Reparte autoridad con el patrón **Host de Zona** (cada cliente es Host en ~400 m; el de menor `sessionId` cede; solo el Host ejecuta la IA de NPCs y publica `NPC_BATCH_UPDATE`; los NPCs huérfanos se adoptan). Robustez: heartbeats, GC de NPCs huérfanos, master sync periódico, colores como Int ARGB. El cliente usa OkHttp WebSocket (sin timeouts de lectura/escritura, ping 25 s).

#### Warm-up del plan gratuito de Render

`ServerWarmupManager` hace polling a `<server>/status` por HTTPS apenas el usuario toca **MULTIJUGADOR** — antes del diálogo de nombre — bloqueando con un spinner cancelable hasta que el server responde `200 OK` (con reintento por timeout). Los pings exitosos se cachean 60 s.

### 🎮 Controles e Interfaz

- **Dos estilos de movimiento:** D-Pad o joystick virtual (360°), configurables.
- **Escala adaptativa** (60%–140%, tope 100% en retrato), **modo zurdo**, botones A/B/X/Y.
- **Controles en estado temporal:** los cambios de tipo, escala y swap se quedan en estado temporal y solo afectan al juego al presionar **GUARDAR** (entonces se sincronizan, persisten y notifican al mapa). Salir de Ajustes descarta los cambios no guardados.
- **HUD de diagnóstico opcional:** widget de caché y de FPS. Las preferencias persisten vía `SettingsRepository` (SharedPreferences).

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

```bash
cd Multiplayer
docker compose up -d   # local (desarrollo)
```

En producción es un Web Service de Render que se reconstruye desde `Multiplayer/Dockerfile`. Escucha en el puerto **8080** (`GET /status`, `WS /`); la URL se inyecta en compilación vía `BuildConfig.MULTIPLAYER_SERVER_URL`.

### 📍 Estado actual

**Funciona hoy:** navegación OSM/Google/Web con snap-to-road, caché persistente de calles y tiles (escritura atómica), NPCs procedurales (spawn pre-filtrado por bbox), combate cuerpo a cuerpo contra NPCs y jugadores remotos, multijugador con autoridad por zona, conducción de vehículos, controles configurables en estado temporal, 8 proveedores de mapas, landmarks editables con import/export JSON, 6 coleccionables, routing por waypoints, 6 interiores de ESCOM, un minijuego completo de supervivencia contra zombis (7 cuartos, combate dual, 6 power-ups, iluminación dinámica, efectos de daño, regen en lobby, pantallas WASTED/Victoria) y un interior easter-egg ShineCTO.

**No implementado aún:** pathfinding con A* (el router actual es greedy), multijugador local por Bluetooth, matrices de colisión ricas para interiores, servidor multijugador dedicado del minijuego zombi, números de daño flotantes / partículas de impacto, y reajuste de coordenadas de puertas del lobby.
