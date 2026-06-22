# Politécnico Open World (POW)

> 🇬🇧 **English version below** · 🇪🇸 [Saltar a la versión en español](#-versión-en-español)

> 📚 **Documentación técnica detallada / Detailed technical docs:** la documentación profunda por área
> (arquitectura, capa de datos, modelos, mapa exterior, minijuego zombi, interiores/metro, servidores,
> convenciones y *gotchas*) vive en la carpeta **[`PolitecnicoOpenWorld/README for IAS/`](PolitecnicoOpenWorld/README%20for%20IAS/)**
> (archivos `00_INDEX` → `09`). Está pensada para pasársela a un asistente de IA en vez de subir todo el
> código. Este README es la visión general; para firmas de funciones, estado, pseudocódigo y reglas del
> proyecto, ve a esa carpeta. / Deep per-area docs (architecture, data, models, open-world map, zombie
> minigame, interiors/metro, servers, conventions & gotchas) live in **`README for IAS/`** (`00_INDEX`–`09`).

---

## 🇬🇧 English Version

**Politécnico Open World (POW)** is an Android 2D top-down exploration app built on top of real-world maps. The player navigates the actual streets of their surroundings (with initial focus on the ESCOM / Zacatenco area in Mexico City) using **OpenStreetMap** cartographic data, sharing the world with procedural NPCs (pedestrians and vehicles) and with other players connected to a real-time server. The ESCOM campus also hosts an embedded zombie survival minigame with interior buildings, melee/ranged combat and a power-up system — now backed by its own authoritative server.

The project is built entirely with **Kotlin + Jetpack Compose**, follows a strict **MVVM** pattern organized by *features*, and delegates persistent-world logic to **two standalone Node.js servers**: one for the open world and a new dedicated one for the zombie minigame.

### 🔄 Recent Changes

The latest integration work centers on the **multiplayer back end**, which now spans two servers, plus the previously bundled feature/performance PRs:

- **Interiors mode expandable per campus (ESCOM, FES, UAM…)** — entering interiors is generalized so any campus can have a lobby + buildings (`ZombieRoomCatalog.campusRooms(...)`; ESCOM keeps its bespoke ring, new campuses are one `addAll`). The ViewModel is campus-agnostic (`lobbyForBuilding` + `pendingLobbyTarget`). **FES Aragón**: the **"Entrada FES Aragón"** door → `interiores_zombies?startRoom=fes_interior` → FES lobby (safe zone) **with a door to its building `fes_edificio`** ("Edificio Principal", temporarily reusing ESCOM's interior, zombies online). Both servers support the transition (`MultiplayerInteriores/server.js` gets the FES rooms; open-world `Multiplayer/server.js` unchanged — the Activity-scoped VM + back stack preserve your connection and coordinates). The FES door sits next to the FES teleport point.
- **Story Mode (campaign) + main-menu rename** — the main menu's first button is now **"FREE ROAM"** (the open world, formerly "Start Game") and the second is **"STORY MODE"** (formerly the disabled "Load Game"), which opens a new campaign screen (`story_mode`). It shows the **prologue** (Politécnico outbreak: at the ENCB, Prankedy accidentally creates a corrosive, latex-piercing substance) and a **school picker** — only **ESCOM** is playable for now; **FES Aragón** and **UAM** appear but are disabled (in development). **"START"** goes through a **"Ready to Start"** intro (`story_intro`, narrative placeholder); pressing START there **saves the campaign** (`CampaignRepository`) and enters the world via `setStorySpawn(...)`. **"LOAD GAME"** is enabled once a save exists and resumes at the saved school. School data in `domain/models/SchoolCatalog.kt`.
- **Fix: invisible pedestrians that could still hit you** — after teleporting (or under memory pressure) a pedestrian's sprite could fail to load and the NPC rendered as a fully transparent marker — so you'd get punched by an "invisible man" (e.g. the driver you just carjacked). Now no NPC is ever invisible: native OSM falls back to a 🧍 emoji and retries the real sprite (instead of caching a transparent placeholder), and the web renderer shows a 🧍/🚗 placeholder until the sprite is ready and then swaps it in. (Google native already showed a default pin.)
- **Prankedy un-sticks, ESCOM zombie hand removed, interior-paths debug** — (1) **Prankedy no longer gets stuck on the roads** (it was snapping to a road node and freezing, so it "stopped doing anything"): it now steps directly when road-snapping doesn't make progress, and relocates next to you if it stays stuck (without healing). (2) **The "Mano del Apocalipsis" (zombie hand) in ESCOM was removed** — global zombie mode is now toggled only from Options → "Activar/Desactivar Apocalipsis". (3) **"Debug Interiores" now shows ESCOM's walkable paths AND no-walk zones**: it draws the landmark nav-graph (green = on-foot paths, orange = car paths) plus the collision areas from `exterior_collisions.json` (translucent **red polygons** = where you can't walk, e.g. the ESCOM building; **red lines** = walls), on top of the building dots/bbox, on native OSM and web.
- **Prankedy stays close + stolen patrols keep their skin** — (1) **Prankedy now always stays near you**: it no longer chases police cops (which dragged it away when the police arrived) and has a ~33 m leash — if it ends up too far it returns to your side instead of wandering off. (2) **A stolen patrol you abandon now stays a patrol**: the dropped car keeps the police look (new `Npc.isPoliceSkin` flag; it stays a `CAR` so traffic AI keeps driving it instead of despawning it, and all 3 renderers draw it as a patrol). You can re-board it and keep driving with the police skin.
- **Metro station icons on the map (all 3 renderers)** — every station in `res/raw/metro.json` now shows the **CDMX Metro "M" icon** (`assets/metroCDMX/icon.webp`) at its location. Native OSM already did; now the **web (Leaflet)** renderer draws it via a new `updateMetro` function (sent once per change + heartbeat) and **Google Maps (Native)** draws a per-station marker. Fixed on-screen size (~24 dp), matching the existing native metro markers.
- **Traffic AI, NPC density/fog & boarding patrols** — (1) **Car NPCs now overtake you** instead of circling: while dodging the player a car chased a local carrot and never advanced its road node, so when the dodge ended the base node was *behind* it and the car U-turned back toward you (orbit/loop, "rarely overtake"); it now advances the node as soon as it passes it (`avoidingPlayer` + dot-product in `moveNpc`). (2) **Fewer NPCs**: base non-zombie caps lowered 26/55 → **18/38** (still scaled by `popFactor`). (3) **No more NPCs outside the fog**: the NPC cull margin was +15 m over the 70 m fog (civilians drew up to 85 m); `NPC_CULL_MARGIN_M` is now **0**, so sprites are culled exactly at the fog edge. **Police and Prankedy stay always visible** (patrols show a 🚓 waypoint outside the fog; Prankedy has its own render). (4) **You can now board patrol cars** (POLICE_CAR): boarding steals the patrol, sets the wanted level to **5★**, and you drive it with the **police skin** (`isDrivingPoliceCar` → `PlayerCharacter` renders the patrol asset across all 3 renderers).
- **Driving rotation black-corners fixed + walking FPS (web)** — the dynamic map-container resize used `vmax`/`calc` units, which are unreliable on old Android 7–9 WebViews, so the container didn't enlarge while driving and the rotated map showed **black corners**; it is now sized in **pixels** (the screen diagonal). Also, the **fog of war stopped re-rasterizing every frame**: `drawFog` repainted a full-screen radial gradient on every map `move`, but while following the player the gradient is identical — it's now cached and only repainted when it actually changes (walking-FPS win).
- **Web map container shrunk from ~9× to 1× screen on foot (big FPS win while moving)** — the Leaflet `#map-wrapper` was a fixed `300vw × 300vh` (~9× the screen area) **at all times**, so the WebView rendered and re-composited ~9× the tiles — the real reason FPS cratered while walking/driving (re-compositing that huge layer on every move). It is now **screen-sized (`100vw × 100vh`) on foot** and only grows to a centered, **pixel-diagonal-sized** square **while driving** (where the map rotates and needs the margin), via `setMapOversize`. Roughly **~8× fewer tiles** on foot.
- **Web follow-camera no longer tanks FPS while moving** — the web map followed the player with `setView` every frame (a full Leaflet `_resetView` that repositions every tile + marker); it now uses `panBy` (a cheap CSS-transform pan) for same-zoom follow, falling back to `setView` only on zoom change or teleport. The tile layer also keeps more surrounding tiles loaded and loads them *while moving* (`keepBuffer: 3`, `updateWhenIdle: false`), so panning shows fewer gray tiles. (Per-pixel car tinting on spawn is still a known cost — future work is asset-based car variants.)
- **Web police parity + teleport-menu cleanup** — in the web renderer, **cops now render as the 👮 emoji** (they were falling back to a generic green SVG), and **approaching patrols show their 🚓 waypoint + dashed route line** from the player while outside the fog (a new Leaflet `updatePolice` function), matching native OSM. **Menu**: the redundant **"Ir a…" submenu was removed** ("Ir a ESCOM" already lives in *Teletransportarse…*), and **"Ir a tu Ubicación (GPS)"** is now the **first entry of the Teleport Points list**.
- **Default provider → OpenStreetMap (Web); Google/web render tuned (low-end)** — the app now starts on **OSM Web** instead of native OSMDroid, sidestepping the native over-zoom (z19→z22 tile up-scaling) that tanked FPS on weak devices; the provider isn't persisted, so this is purely the new default (still switchable in Settings). **Google Maps (Native)** now `move()`s the follow-camera instead of `animate()`-ing it on every ~30 Hz location update (no more camera-animation thrash). **OSM Web**: landmark layers are re-serialized/re-sent to the WebView **only when they change** (plus a periodic heartbeat), instead of `gson.toJson` + `evaluateJavascript` every frame.
- **Render perf + web patrol fix (low-end follow-up)** — **Native OSM**: trimmed the per-frame osmdroid overlay work — static landmark `GroundOverlay`s no longer re-`setPosition`/`setImage` every frame (only when their transform signature changes; animated doors still refresh), the ~160 metro-station markers are **viewport-culled** (off-screen ones disabled so osmdroid skips drawing them), and the **fog of war** no longer fills a rect ~10× the screen area every frame while on foot (the screen-diagonal oversize is kept only while driving, when the map rotates). **Web**: police **patrols now render their real sprite** instead of a placeholder SVG — `POLICE_CAR` was falling through to the generic drawable branch; it is now generated via `PoliceSpriteManager`, cached as a base64 image and sent like a car. *(Note: at very high over-zoom on ≤2 GB devices the tile up-scaling + sprite/fog fill rate is still the dominant cost — see the zoom/quality tradeoff.)*
- **Low-end optimization pass (≤2 GB RAM / Android 7–9, behavior-preserving)** — pure internal GC/memory wins, **no gameplay change** (same NPC counts, effects, zoom and numbers): the native marker drawable cache (`nativeDrawableCache`) is now an **access-order LRU** instead of an unbounded map — its keys embed health/zoom/frame, so long sessions used to grow it until an **OOM** on low-RAM devices. The **door-glow effect reuses one bitmap/canvas/paints per source** instead of allocating a fresh `Bitmap` every frame, and the **police bullet / 🔫 / 📞 marker icons are cached by size** instead of rebuilt each frame during chases — both eliminate per-frame bitmap garbage. `NpcAiManager` now **caches the nav-graph-filtered landmark list once** (`cachedNavLandmarks`, computed in `setLandmarks`) instead of re-filtering it per NPC per tick. Finally, `MainActivity.onTrimMemory` **frees the sprite caches** (Character/Vehicle/Police/Zombie) under memory pressure (`clearCaches()` added to the three managers that lacked it).
- **Wanted level / Police (GTA-style)** — punching civilians raises a **0–5 star wanted level** (HUD). **Patrol cars** (`PoliceManager` + `PoliceSpriteManager`, no-repaint `POLICE_TOPDOWN` asset) spawn far (more per star) and chase you **on the roads** (each step snaps to the network); outside the fog they show a **🚓 waypoint + route line**, inside the fog the real asset. Patrols drop **2–3 cops** (👮 emoji) that punch and **shoot at 2★+**. In a car the cops chase by patrol and can **pull you out** if you stop (no damage while driving); on death the police **retreat** instead of vanishing. The wanted player **owns/simulates** the police and broadcasts `POLICE_BATCH_UPDATE` / `POLICE_DESTROY` (relayed by `server.js`, not stored in the roster). Aggressive NPCs were toned down (lower `aggressiveRatio`, slower chase, relentless needs 6 hits) and **can't melee you through a car**.
- **NPC combat — retaliation, relentless mode, HUD & death** — NPCs only react when provoked: hit one and **aggressive** NPCs hit back (a guaranteed counter ~450 ms after your punch, plus a chase), while **cowards flee** and aggressive ones are **fear-immune**. The aggressive share is **configurable** (`NpcAiManager.aggressiveRatio`, default half). Land **3+ consecutive hits** on an NPC and it becomes **relentless** — it won't stop hitting you until you (or it) dies. Added a **persistent HUD health bar** (zombie-style), a **red damage-flash** on every hit, the WASTED screen now **freezes movement + ghost-fades** the player, and **respawn happens inside the already-downloaded zone** (~80 m from death, snapped to road) instead of teleporting to ESCOM — saving resources. Multiplayer: NPC `health`/`isDying`/`aggroUntil` are replicated (`server.js` relays them).
- **GTA-lite NPC AI — multiplayer + polish** — the AI now **replicates to multiplayer**: NPC `health`/`isDying` are sent in `NPC_BATCH_UPDATE` so every client sees health bars and run-over/hit deaths (`Multiplayer/server.js` relays them and clamps health, v3.1). Added a **"💥" collision/impact effect** so getting hit or running someone over is noticeable, made **two-way traffic lanes more visible** (larger right-side offset), and fixed the **player vehicle/avatar sizing** to match NPCs exactly (both now use the same zoom source). Contact damage from aggressive NPCs is tuned to be reliable.
- **GTA-lite NPC AI (low-end optimized, host-only)** — NPCs now have a **personality trait** (`PASSIVE`/`COWARD`/`AGGRESSIVE`, weighted at spawn). You can **run over pedestrians** while driving (speed-scaled damage; witnesses panic). **Carjacking an occupied car** makes the ousted driver react by trait: cowards flee, aggressive ones **chase and punch you**. **Aggressive NPCs hit back** for a few seconds when you melee them and they survive. **Two-way traffic** is enabled (randomized spawn direction + existing right-side lane offset). All checks are cull-radius-bound with early-outs and the AI fields aren't serialized. *(Car-vs-car collisions are intentionally left as future work.)*
- **Map UX & rendering parity pass** — (1) **player-anchored fog of war**: the fog now follows the player's real position during scroll/zoom instead of staying screen-centered — native uses an osmdroid overlay (rect oversized to the screen diagonal so it stays correct under driving rotation), web uses a `#fog` div redrawn on each Leaflet `move`/`zoom`; the Compose fog is kept only for Google native. (2) **Native over-zoom to z22** scaled from z19 via a `MapTileApproximater`, with loading screens prefetching **z19 + z17** tiles and OSM defaulting to **max zoom**. (3) **Web pinch-zoom no longer drags the player** (only user pinch enters exploration). (4) **Unified real-meter sizing** for NPCs and the player across renderers (pedestrians ≈ 1.3 m, vehicles ≈ 4.0 m), **larger NPC health bars**. (5) **Landscape-safe Options menu** (height-capped + scrollable; right-side control slides aside while open). (6) **"Centrar en jugador"** evolves into a sub-menu with **"Hacer zoom en el jugador"** when zoomed; with the map off-center the left movement controls recenter (no zoom). (7) **Ousted driver** spawns next to the jacked car (~2 m). (8) **Main-menu version** bound to `BuildConfig.VERSION_NAME` with an auto-shrinking title that never wraps.
- **Teleport now gates on map download** — teleporting (Go to ESCOM / Go to your GPS location) no longer drops you instantly: it re-arms the load gate (`isMapReady=false`) and downloads the new area's tiles for the active provider before unlocking free movement (native OSM stores real tiles to Room for offline; web warms the CDN so the WebView+cache fill in; Google native shows a brief gate). Runs in parallel with the street reload — `worldReady = streets ready && map ready`.
- **Map controls cleanup** — removed osmdroid's duplicate native zoom buttons (zoom now lives only in the nested *Mapa* menu); **"Center on player" is always available**; **"Go to…" nested submenu** with *Go to ESCOM* and *Go to your GPS location* (teleports the avatar to the device's real GPS position — e.g. back home — not to the avatar's current spot). `OptionsMenu` now supports arbitrarily nested groups.
- **Native OSM map fixed + unified offline cache** — the native osmdroid map now reads/writes the **same Room tile cache** as the Web providers (`RoomTileModuleProvider`, bucket `osm`), downloading with a browser User-Agent. osmdroid's built-in downloader (UA = package name) was being throttled by the public OSM server, which is why the native map *failed to load new zones* while Web worked. A `TilePrefetchManager` now proactively downloads the current ~2 km zone (zooms 16-18) to the local DB so it plays **fully offline** after visiting (non-blocking, warns if incomplete). **Fog of war is now always rendered** (previously hidden while panning).
- **Dedicated zombie-minigame server** (`MultiplayerZombie/`) — zombies are now **authoritative on the server** (Phase 1). Their AI uses a **shared Dijkstra flow-field** (one distance-to-player map per target cell, reused by every zombie chasing that player and cached ~250 ms), **line-of-sight** straight-line pursuit when no walls block the path, and **separation steering** so the horde never stacks on a single pixel; a **wander fallback** keeps disconnected zombies from freezing. The `ZOMBIE_STATE` wire format is unchanged (all AI lives in non-serialized internal fields).
- **Open-world server hardened to v2** (`Multiplayer/`) — **Area of Interest (AOI)** relay so `NPC_SPAWN/UPDATE/BATCH` only reach clients near the emitting Host (global messages like `PLAYER_UPDATE`, `PLAYER_DAMAGE`, `NPC_DESTROY`, `DISCONNECT` and sync stay global), **throttled Host election** (re-evaluated at most every 200 ms per client), **per-socket rate limiting** (sliding 1 s window, anti-flood), **input sanitization** (finite coordinates, bounded damage, max message size) and **ghost-player GC** (not only on socket close).
- **Atomic tile-cache writes** — tile persistence counts, evicts (LRU) and inserts inside a single Room `@Transaction`, preventing corruption if the process dies mid-write.
- **Zombie minigame balance** — shorter zombie contact-attack range and gradual **health regeneration in the lobby** (safe zone) up to 100 HP.
- **Damage feedback FX** — screen shake on hit, a red damage vignette/flash whose intensity **scales with lost HP**, a low-HP pulse, **zombie knockback** (melee + projectiles, collision-aware) and **player recoil** on firing with per-axis position correction.
- **Staged control settings** — control changes (type/scale/swap) are held in a temporary state and only affect gameplay after pressing **SAVE**.
- **Map rotation fixes** — dark OSMDroid background and an oversized, centered Leaflet wrapper so no "gaps" appear when the map rotates in driving mode.
- **Performance** — per-*way* bounding-box pre-filter in the NPC spawner and `Pair`-based routing keys (no per-step string allocations).

### ⚙️ Architecture

The repository contains three complementary projects:

```text
.
├── PolitecnicoOpenWorld/   # Android client (Kotlin + Compose)
├── Multiplayer/            # Open-world game server (Node.js + WebSocket, v2, dockerized)
└── MultiplayerZombie/      # Zombie-minigame server (authoritative zombies, dockerized)
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
│   │   └── WebSocketManager.kt              # OkHttp WS (no timeouts, 25s ping) — shared by both servers
│   └── repository/
│       ├── OverpassRepository.kt            # Overpass API (2km radius, 45s timeout)
│       ├── SettingsRepository.kt            # SharedPreferences for controls
│       ├── CollectibleRepository.kt         # Seeds 6 default items, exposes Flow
│       └── CollisionMatrixRepository.kt     # Zombie collision matrices JSON (designer mode)
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
│           └── ZombieRoomCatalog.kt         # Lobby + 7 building rooms with door layout
│
├── features/                                # ─── Feature modules (View + ViewModel) ───
│   ├── main_menu/                           # menu + multiplayer dialog + warm-up
│   ├── map_exterior/                        # core open world (WorldMapScreen/ViewModel/State)
│   ├── interior/                            # 6 ESCOM building interiors
│   ├── zombie_minigame/                     # embedded survival minigame (offline + online)
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

> Note: this open-world NPC AI runs **client-side** (on the Zone Host) — the open-world server only arbitrates host roles and relays messages. The zombie minigame, by contrast, now runs its zombie AI **server-side** (see below).

### 🚓 Wanted Level / Police (GTA-style)

Punching civilians raises a **wanted level** (0–5 stars, HUD). While wanted, **patrol cars** (`PoliceManager` + `PoliceSpriteManager`, using the no-repaint `VEHICLES/POLICE_TOPDOWN` asset) spawn far away — more cars per star — and chase you, **respecting the streets** (each step snaps to the road network). Outside the fog they're shown as a 🚓 waypoint with a route line; inside the fog the real patrol asset is drawn. On arrival a patrol drops **2–3 cops** (rendered as 👮 emoji, no person asset) that chase and punch, and **shoot at 2★+**. Board a car and the cops chase by patrol and can **pull you out** if you stop (no damage while driving); on death the police **retreat** instead of vanishing. The wanted player **simulates and owns** their police and broadcasts `POLICE_BATCH_UPDATE` / `POLICE_DESTROY`; other clients only render them.

### 🧭 Navigation and Waypoints

The player can drop a destination marker; the ViewModel runs a greedy road-graph search (`calculateRouteOnNetwork`) over a spatial grid of unique nodes and renders a dashed blue polyline. Routing uses `Pair<Double, Double>` keys for visited/distinct nodes (no per-step string allocations). The marker auto-clears within 20 m.

### 🧟 Zombie Minigame

A circular **ring of rooms**: a lobby with doors to each ESCOM building. Inside a building, EXIT doors connect to neighbors and a central door returns to the lobby.

- **Camera** (`CameraTransform`): zoom-aware, clamps to bounds, uses `max(viewW/worldW, viewH/worldH)` (ContentScale.Crop equivalent).
- **Online vs offline:** the `ZombieGameViewModel` runs a single game loop that branches into `tickOffline` (full local simulation) or `tickOnline`. **In multiplayer, zombies and items are authoritative on the dedicated server** (`MultiplayerZombie/`): the client renders `ZOMBIE_STATE` snapshots, sends `ZOMBIE_DAMAGE` / `ITEM_PICKUP` requests, and keeps only local concerns (its own HP, projectiles, contact-damage cooldown). Offline, the client simulates zombies locally with collision-aware axis-slide pathfinding.
- **Contact-attack range was reduced** for fairer melee.
- **Lobby regeneration:** while in the lobby below 100 HP, the player heals gradually each tick up to 100.
- **Dual combat:** hold **Y** (500 ms) to toggle MELEE/RANGED; **B** attacks.
- **Damage feedback:** screen shake on hit, a red **damage vignette/flash that scales with lost HP**, a **low-HP pulse**, **zombie knockback** on melee/projectile hits (collision-aware), and **player recoil** on firing with per-axis position correction so the player never clips through walls.
- **SkillEffect drops:** six effects (45% chance) drawn as pure Canvas icons.
- **Collision Designer Mode:** an in-game editor paints the per-room collision matrix on top of the room art, persists it to `collision_matrices.json`, and can export/import the same JSON the server reads.
- **Dynamic lighting** in dark interiors; **WASTED/Victory** screens.

### 🌐 Real-Time Multiplayer

POW now ships **two independent Node.js + Express + ws servers**, both dockerized and hosted on Render, sharing the same `WebSocketManager` on the client.

#### Open-world server — `Multiplayer/server.js` (v2)

Distributes authority via the **Zone Host** pattern: each client is Host within ~400 m, the lower `sessionId` yields on overlap, and only the Host runs NPC AI and emits `NPC_BATCH_UPDATE`; orphaned NPCs are adopted, not destroyed. The v2 hardening adds:

- **Area of Interest (AOI):** `NPC_SPAWN/UPDATE/BATCH` are relayed only to clients near the emitting Host (~2 km radius), drastically cutting bandwidth and serialization work when players are dispersed. Messages that must always arrive (`PLAYER_UPDATE`, `PLAYER_DAMAGE`, `NPC_DESTROY`, `DISCONNECT`, sync) stay global to avoid ghost entities.
- **Throttled Host election:** the Host role is re-evaluated at most every 200 ms per client, not on every `PLAYER_UPDATE`.
- **Per-socket rate limiting:** messages are dropped if a client floods (sliding 1 s window), plus a max accepted message size.
- **Input sanitization:** finite coordinates, finite and bounded damage, sanitized re-broadcast.
- **Ghost-player GC:** stale players are reaped periodically (not only on socket close), alongside heartbeats, orphan-NPC GC and periodic master sync. Colors are serialized as ARGB Int.
- **Police relay (v3.2):** `POLICE_BATCH_UPDATE` (AOI) and `POLICE_DESTROY` (global) are relayed but **not** stored in the persistent NPC roster — the police are transient and per-player (owned by the wanted client), so each client purges them by staleness.

#### Zombie-minigame server — `MultiplayerZombie/server.js` (Phase 1)

Runs the **zombie simulation authoritatively** per room and broadcasts `ZOMBIE_STATE`. Its AI v2:

- **Shared flow-field (Dijkstra map):** instead of an A* per zombie, the server computes **one** distance-to-player field over the whole matrix (per target cell) and every zombie chasing that player follows the gradient downhill. Cost is O(number of players) fields per room, each cached ~250 ms.
- **Line-of-sight (LOS):** if a zombie can see the player with no walls between, it moves in a straight line (smoother, no field lookup); the field is used only when an obstacle is in the way.
- **Separation steering:** zombies gently push each other apart for a more natural horde.
- **Fallback wander:** zombies in a disconnected region (no finite field distance, no LOS) wander instead of freezing.

Coordinates on the wire are fractional `[0,1]` (the client converts to pixels). Collision matrices come from `collision_matrices.json` (the same format the Designer Mode exports), with neutral border-only defaults that must stay identical (rows/cols) to the client's matrices until replaced.

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
| Servers | Node.js 18, Express, ws, Docker (open world + zombie minigame) |
| Hosting | [Render](https://render.com/) (auto-deploy from Dockerfile) |

### 🐳 Deploying the multiplayer servers

```bash
# Open-world server (port 8080)
cd Multiplayer
docker compose up -d

# Zombie-minigame server (host port 8081 → container 8080)
cd MultiplayerZombie
docker compose up -d
```

Production runs two Render Web Services, each auto-built from its own `Dockerfile`. Both listen on container port **8080** (`GET /status`, `WS /`). The open-world URL is injected at compile time via `BuildConfig.MULTIPLAYER_SERVER_URL`; the zombie-minigame URL via `BuildConfig.ZOMBIE_SERVER_URL`.

### 📍 Current Status

**Works today:** OSM/Google/Web navigation with snap-to-road, persistent street and tile caching (atomic writes), native over-zoom to z22 (scaled from z19, with z19/z17 loading-screen prefetch and max-zoom default), player-anchored fog of war on native + web (driving-rotation safe), real-meter NPC/player sizing unified across renderers, landscape-safe scrollable Options menu, procedural NPCs (bbox-prefiltered spawn) with personality traits, run-over-while-driving, aggressive retaliation/carjack reactions and two-way traffic, melee combat vs NPCs and remote players, zone-delegated open-world multiplayer (v2: AOI + host throttle + rate-limit + sanitization), vehicle driving, staged configurable controls, 8 map providers, editable landmarks with JSON import/export, 6 lore collectibles, waypoint routing, 6 ESCOM interiors, a full zombie survival minigame (lobby + 7 buildings, dual combat, 6 power-ups, dynamic lighting, damage-feedback FX, lobby regen, WASTED/Victory screens) with a **dedicated authoritative zombie server** (flow-field + LOS + separation AI), collision Designer Mode, and a ShineCTO easter-egg interior. A behavior-preserving **low-end (≤2 GB / Android 7–9) GC & memory pass** keeps long sessions stable on weak devices: an LRU-bounded native drawable cache, a reused door-glow bitmap, size-cached police bullet/🔫/📞 icons, once-filtered nav landmarks, and sprite caches freed on `onTrimMemory`.

**Not yet implemented:** A*-based pathfinding for the open-world router (still a greedy graph walk; note the zombie server already uses a Dijkstra flow-field), local Bluetooth multiplayer, content-rich interior collision matrices, floating damage numbers / hit particles, lobby door coordinate re-tuning, and car-vs-car collisions (planned: cull-radius circle-overlap + speed reduction, no physics engine).

### 🔁 Keeping This Documentation Current

`README.md` and `plan.artifact.md` are the **single source of truth** we hand to any assistant (human or AI) *instead of* the whole codebase — so we never have to re-explain the project. They are only trustworthy if they are updated **in the same change** that touches the code, never afterwards.

The detailed checklist lives in **`plan.artifact.md` §11**. In short, on every change that alters behavior:

- Update **both** files (`README.md` is bilingual — reflect user-facing changes in the English **and** Spanish sections so they don't drift).
- Add the change to the top of **Recent Changes**; prune entries older than ~2–3 releases.
- Move finished items from **Not yet implemented** into **Works today** (and the mirror lists in `plan.artifact.md` §7/§8).
- A fact that lives in both files must be changed in both — a contradiction between them is a bug.

**Definition of done:** code compiles/validates *and* both files describe the new reality. If the docs aren't updated, the task isn't finished.

---
---

## 🇪🇸 Versión en Español

**Politécnico Open World (POW)** es una aplicación Android de exploración 2D con vista *top-down* construida sobre mapas del mundo real. El jugador se desplaza por las calles reales de su ubicación (con foco inicial en la zona ESCOM / Zacatenco) usando datos de **OpenStreetMap**, comparte el mundo con NPCs procedurales (peatones y vehículos) y con otros jugadores conectados a un servidor en tiempo real. El campus de ESCOM alberga además un minijuego embebido de supervivencia contra zombis con interiores, combate cuerpo a cuerpo / a distancia y un sistema de power-ups — ahora respaldado por su propio servidor autoritativo.

El proyecto está construido íntegramente en **Kotlin + Jetpack Compose**, sigue un patrón **MVVM** estricto organizado por *features* y delega la lógica de mundo persistente a **dos servidores Node.js independientes**: uno para el open world y uno nuevo, dedicado, para el minijuego de zombis.

### 🔄 Cambios Recientes

El último trabajo de integración se centra en el **back end multijugador**, que ahora abarca dos servidores, además de los PRs de funcionalidad/rendimiento previos:

- **Modo Interiores expandible por campus (ESCOM, FES, UAM…)** — entrar a interiores se generalizó para que cualquier campus tenga lobby + edificios (`ZombieRoomCatalog.campusRooms(...)`; ESCOM mantiene su anillo bespoke, los nuevos son un `addAll`). El ViewModel es campus-agnóstico (`lobbyForBuilding` + `pendingLobbyTarget`). **FES Aragón**: la puerta **"Entrada FES Aragón"** → `interiores_zombies?startRoom=fes_interior` → lobby FES (zona segura) **con una puerta a su edificio `fes_edificio`** ("Edificio Principal", reusa TEMPORALMENTE el interior de ESCOM, con zombis online). Ambos servidores soportan la transición (`MultiplayerInteriores/server.js` recibe las salas FES; el del mundo abierto `Multiplayer/server.js` no cambia — el VM Activity-scoped + back stack preservan conexión y coordenadas). La puerta FES queda junto al teletransporte de FES.
- **Modo Historia (campaña) + renombre del menú** — el primer botón del menú ahora es **"MUNDO LIBRE"** (el open world, antes "Iniciar Juego") y el segundo es **"MODO HISTORIA"** (antes el deshabilitado "Cargar Partida"), que abre una nueva pantalla de campaña (`story_mode`). Muestra el **prólogo** (brote del Politécnico: en la ENCB, Prankedy crea por accidente una sustancia corrosiva capaz de penetrar el látex) y un **selector de escuela** — solo **ESCOM** es jugable por ahora; **FES Aragón** y **UAM** aparecen pero deshabilitadas (en desarrollo). **"COMENZAR"** pasa por una intro **"Listo para Iniciar"** (`story_intro`, placeholder narrativo); al **INICIAR** ahí se **guarda la partida** (`CampaignRepository`) y se entra al mundo vía `setStorySpawn(...)`. **"CARGAR PARTIDA"** se habilita cuando hay guardado y reanuda en la escuela guardada. Escuelas en `domain/models/SchoolCatalog.kt`.
- **Fix: peatones invisibles que aún podían golpearte** — tras un teletransporte (o bajo presión de memoria) el sprite de un peatón podía no cargar y el NPC se dibujaba como un marcador totalmente transparente — así te golpeaba un "hombre invisible" (p. ej. el conductor al que le acabas de robar el coche). Ahora ningún NPC queda invisible: OSM nativo cae a un emoji 🧍 y reintenta el sprite real (en vez de cachear un placeholder transparente), y el renderer web muestra un 🧍/🚗 hasta que el sprite está listo y entonces lo cambia. (Google nativo ya mostraba un pin por defecto.)
- **Prankedy se destraba, se quita la mano zombie de ESCOM y debug de caminos** — (1) **Prankedy ya no se traba en las calles** (se pegaba a un nodo y se quedaba quieto, "ya no te hacía nada"): ahora avanza directo cuando el snap a la calle no progresa, y si sigue atascado se reubica a tu lado (sin curarse). (2) **Se eliminó la "Mano del Apocalipsis" (mano zombie) de ESCOM** — el modo zombi global se activa solo desde Opciones → "Activar/Desactivar Apocalipsis". (3) **"Debug Interiores" ahora muestra los caminos transitables Y las zonas no caminables de ESCOM**: dibuja el nav-graph de los landmarks (verde = caminos a pie, naranja = caminos de autos) más las colisiones de `exterior_collisions.json` (**polígonos rojos** translúcidos = donde NO se puede caminar, p. ej. el edificio de ESCOM; **líneas rojas** = bardas), sobre los puntos/bbox, en OSM nativo y web.
- **Prankedy se queda cerca + las patrullas robadas conservan su skin** — (1) **Prankedy ahora siempre está cerca de ti**: ya no persigue a los policías (lo que lo alejaba al llegar la policía) y tiene una correa de ~33 m — si queda muy lejos, regresa a tu lado en vez de alejarse. (2) **Una patrulla robada que abandonas sigue siendo patrulla**: el coche que dejas conserva el aspecto de patrulla (nuevo flag `Npc.isPoliceSkin`; se queda como `CAR` para que la IA de tráfico lo siga conduciendo en vez de despawnearlo, y los 3 renderers lo dibujan como patrulla). Puedes volver a subirte y seguir conduciéndola con el skin de policía.
- **Iconos de estación de Metro en el mapa (los 3 renderers)** — cada estación de `res/raw/metro.json` ahora muestra el **icono "M" del Metro CDMX** (`assets/metroCDMX/icon.webp`) en su ubicación. El OSM nativo ya lo hacía; ahora el renderer **web (Leaflet)** lo dibuja con una nueva función `updateMetro` (se envía una vez por cambio + heartbeat) y **Google Maps (Nativo)** dibuja un marcador por estación. Tamaño fijo en pantalla (~24 dp), igual que los marcadores de metro nativos.
- **IA de tráfico, densidad/niebla de NPCs y subirse a patrullas** — (1) **Los autos NPC ahora te rebasan** en vez de orbitar: al esquivar al jugador el coche perseguía un *carrot* local y nunca avanzaba su nodo de la calle, así que al terminar el esquive el nodo base quedaba *detrás* y el coche daba media vuelta hacia ti (bucle/órbita, "rara vez me rebasan"); ahora avanza el nodo en cuanto lo rebasa (`avoidingPlayer` + producto punto en `moveNpc`). (2) **Menos NPCs**: topes base no-zombi bajados 26/55 → **18/38** (siguen escalando por `popFactor`). (3) **Ya no se ven NPCs fuera de la niebla**: el margen de culling era +15 m sobre los 70 m de niebla (los civiles se dibujaban hasta 85 m); `NPC_CULL_MARGIN_M` ahora es **0**, así los sprites se recortan justo en el borde de la niebla. **La policía y Prankedy siguen siempre visibles** (las patrullas muestran waypoint 🚓 fuera de la niebla; Prankedy tiene su propio render). (4) **Ya te puedes subir a las patrullas** (POLICE_CAR): al subir robas la patrulla, el nivel de búsqueda sube a **5★** y la conduces con el **skin de policía** (`isDrivingPoliceCar` → `PlayerCharacter` dibuja el asset de patrulla en los 3 renderers).
- **Arreglo de bordes negros al rotar conduciendo + FPS caminando (web)** — el redimensionado dinámico del contenedor usaba unidades `vmax`/`calc`, poco fiables en WebViews viejas de Android 7–9, así que el contenedor no se agrandaba al conducir y el mapa rotado mostraba **esquinas negras**; ahora se dimensiona en **píxeles** (la diagonal de la pantalla). Además, la **neblina dejó de re-rasterizarse en cada frame**: `drawFog` repintaba un degradado radial a pantalla completa en cada `move` del mapa, pero siguiendo al jugador el degradado es idéntico — ahora se cachea y solo se repinta cuando cambia de verdad (salto de FPS al caminar).
- **El contenedor del mapa web pasó de ~9× a 1× la pantalla a pie (gran salto de FPS al moverse)** — el `#map-wrapper` de Leaflet era un fijo `300vw × 300vh` (~9× el área de pantalla) **siempre**, así que el WebView renderizaba y re-componía ~9× las teselas — la verdadera razón de que los FPS se desplomaran al caminar/conducir (recomponer esa capa enorme en cada movimiento). Ahora es del **tamaño de la pantalla (`100vw × 100vh`) a pie** y solo crece a un cuadrado centrado del **tamaño de la diagonal (en px)** **al conducir** (cuando el mapa rota y necesita el margen), vía `setMapOversize`. Aproximadamente **~8× menos teselas** a pie.
- **La cámara de seguimiento web ya no hunde los FPS al moverte** — el mapa web seguía al jugador con `setView` cada frame (un `_resetView` completo de Leaflet que reposiciona todas las teselas y marcadores); ahora usa `panBy` (un *pan* por transform, barato) para el seguimiento al mismo zoom, y solo recurre a `setView` al cambiar el zoom o al teletransportarse. La capa de teselas además mantiene más teselas alrededor y las carga *mientras te mueves* (`keepBuffer: 3`, `updateWhenIdle: false`), así el paneo muestra menos teselas grises. (El tintado de coches por píxel al aparecer sigue siendo un costo conocido — trabajo futuro: variantes de coche por asset.)
- **Paridad de policía en web + limpieza del menú de teletransporte** — en el renderizador web, los **policías a pie ahora se dibujan como emoji 👮** (antes caían a un SVG verde genérico) y las **patrullas que se acercan muestran su waypoint 🚓 + línea de ruta punteada** desde el jugador cuando están fuera de la neblina (nueva función Leaflet `updatePolice`), igual que en OSM nativo. **Menú**: se **quitó el submenú redundante "Ir a…"** ("Ir a ESCOM" ya está en *Teletransportarse…*) y **"Ir a tu Ubicación (GPS)"** es ahora la **primera opción de la lista de Puntos de Teletransporte**.
- **Proveedor por defecto → OpenStreetMap (Web); render Google/web afinado (gama baja)** — la app arranca en **OSM Web** en vez del OSMDroid nativo, evitando el over-zoom nativo (reescalado de teselas z19→z22) que hundía los FPS en equipos débiles; el proveedor no se persiste, así que esto es solo el nuevo default (cambiable en Ajustes). **Google Maps (Nativo)** ahora hace `move()` de la cámara de seguimiento en vez de `animate()` en cada actualización de posición (~30 Hz), eliminando el "thrash" de animación de cámara. **OSM Web**: las capas de landmarks se re-serializan/re-envían al WebView **solo cuando cambian** (más un heartbeat periódico), en vez de `gson.toJson` + `evaluateJavascript` en cada frame.
- **Rendimiento de render + arreglo de patrullas en web (seguimiento gama baja)** — **OSM nativo**: se recortó el trabajo por frame del overlay de osmdroid — los `GroundOverlay` de landmarks ESTÁTICOS ya no re-aplican `setPosition`/`setImage` cada frame (solo cuando cambia su firma de transformación; las puertas animadas siguen refrescando), las ~160 estaciones de metro se **cullean por viewport** (las de fuera se deshabilitan y osmdroid no las dibuja), y la **niebla de guerra** ya no rellena un rect de ~10× el área de pantalla cada frame a pie (el sobredimensionado a la diagonal solo se usa al conducir, cuando el mapa rota). **Web**: las **patrullas ahora muestran su asset real** en vez de un SVG genérico — `POLICE_CAR` caía a la rama del drawable genérico; ahora se genera con `PoliceSpriteManager`, se cachea como imagen base64 y se envía como coche. *(Nota: con over-zoom muy alto en equipos de ≤2 GB, el re-escalado de teselas + el fill rate de sprites/niebla sigue siendo el costo dominante — ver el compromiso zoom/calidad.)*
- **Pase de optimización para gama baja (≤2 GB RAM / Android 7–9, sin cambiar el comportamiento)** — solo mejoras internas de GC/memoria, **sin tocar la jugabilidad** (mismos NPCs, efectos, zoom y números): la caché de drawables del render nativo (`nativeDrawableCache`) ahora es un **LRU por orden de acceso** en vez de un mapa ilimitado — sus claves incluyen salud/zoom/frame, así que en sesiones largas crecía hasta provocar un **OOM** en equipos de poca RAM. El **efecto de brillo de las puertas reutiliza un único bitmap/canvas/paints por fuente** en vez de asignar un `Bitmap` nuevo cada frame, y los **iconos de bala / 🔫 / 📞 de la policía se cachean por tamaño** en lugar de recrearse cada frame durante las persecuciones — ambos eliminan basura de bitmaps por frame. `NpcAiManager` ahora **cachea una sola vez la lista de landmarks con navGraph** (`cachedNavLandmarks`, calculada en `setLandmarks`) en vez de refiltrarla por cada NPC y tick. Por último, `MainActivity.onTrimMemory` **libera las cachés de sprites** (Character/Vehicle/Police/Zombie) bajo presión de memoria (se añadió `clearCaches()` a los tres managers que no lo tenían).
- **Nivel de búsqueda / Policía (estilo GTA)** — golpear civiles sube un **nivel de búsqueda de 0–5 estrellas** (HUD). Aparecen **patrullas** (`PoliceManager` + `PoliceSpriteManager`, asset `POLICE_TOPDOWN` sin repintar) **lejos** (más por estrella) que te persiguen **por las calles** (cada paso se ajusta a la red); fuera de la neblina muestran un **waypoint 🚓 + línea de ruta**, dentro de la neblina el asset real. Las patrullas sueltan **2–3 policías** (emoji 👮) que golpean y **disparan a 2★+**. En auto, los policías te siguen en patrulla y pueden **bajarte del vehículo** si te detienes (sin daño mientras conduces); al morir, la policía **se retira** en vez de desaparecer. El jugador buscado **posee/simula** su policía y difunde `POLICE_BATCH_UPDATE` / `POLICE_DESTROY` (reenviados por `server.js`, sin guardarse en el roster). Se bajó la agresividad de los NPCs (menor `aggressiveRatio`, persecución más lenta, implacable a los 6 golpes) y ya **no te golpean a través del auto**.
- **Combate de NPCs — contraataque, modo implacable, HUD y muerte** — los NPCs solo reaccionan si los provocas: al golpear a uno, los **agresivos** te devuelven el golpe (un contraataque garantizado ~450 ms tras tu puñetazo, además de perseguirte), mientras que los **cobardes huyen** y los agresivos son **inmunes al miedo**. La proporción de agresivos es **configurable** (`NpcAiManager.aggressiveRatio`, por defecto la mitad). Si le das **3 o más golpes seguidos** a un NPC, se vuelve **implacable**: no deja de pegarte hasta que mueras (o muera él). Se añadió una **barra de vida fija en el HUD** (estilo zombis), un **destello rojo** en cada golpe, la pantalla WASTED ahora **congela el movimiento y deja al jugador como fantasma**, y el **respawn ocurre dentro de la zona ya descargada** (~80 m del lugar de muerte, pegado a la calle) en vez de teletransportar a ESCOM — para ahorrar recursos. Multijugador: se replican `health`/`isDying`/`aggroUntil` de los NPCs (`server.js` los reenvía).
- **IA de NPCs estilo GTA — multijugador + pulido** — la IA ahora **se replica en multijugador**: `health`/`isDying` de los NPCs viajan en `NPC_BATCH_UPDATE` para que todos los clientes vean barras de vida y muertes por atropello/golpes (`Multiplayer/server.js` los reenvía y satura la vida, v3.1). Se agregó un **efecto de colisión "💥"** para que se note cuando te pegan o atropellas, se hicieron **más visibles los carriles de doble sentido** (mayor desplazamiento a la derecha), y se **corrigió el tamaño del vehículo/avatar del jugador** para que coincida con los NPCs (ambos usan la misma fuente de zoom). El daño por contacto de NPCs agresivos quedó afinado para ser fiable.
- **IA de NPCs estilo GTA (optimizada para gama baja, solo en el host)** — los NPCs tienen una **personalidad** (`PASSIVE`/`COWARD`/`AGGRESSIVE`, peso aleatorio al spawn). Puedes **atropellar peatones** conduciendo (daño según la velocidad; los testigos huyen). Al **robar un coche ocupado**, el conductor desalojado reacciona según su rasgo: los cobardes huyen, los agresivos **te persiguen y te golpean**. Los **NPCs agresivos contraatacan** unos segundos si los golpeas y sobreviven. **Tráfico en doble sentido** activado (dirección de spawn aleatoria + el desplazamiento de carril a la derecha ya existente). Todo con cortes tempranos acotados al radio de culling y sin serializar los campos de IA. *(Las colisiones coche-coche quedan como trabajo futuro a propósito.)*
- **Mejoras de UX del mapa y paridad de renderizado** — (1) **niebla de guerra anclada al jugador**: la niebla ahora sigue la posición real del jugador al desplazar/hacer zoom en lugar de quedarse fija al centro de la pantalla — el nativo usa un overlay de osmdroid (rect sobredimensionado a la diagonal para que se vea bien con la rotación al conducir), el web usa un div `#fog` redibujado en cada `move`/`zoom` de Leaflet; la niebla Compose queda solo para Google nativo. (2) **Over-zoom nativo a z22** escalado desde z19 con un `MapTileApproximater`, y las pantallas de carga precargan teselas **z19 + z17**, con OSM en **zoom máximo por defecto**. (3) **El pinch-zoom web ya no arrastra al jugador** (solo el pinch del usuario entra en exploración). (4) **Tamaños unificados en metros reales** para NPCs y jugador en todos los renderizadores (peatones ≈ 1.3 m, vehículos ≈ 4.0 m) y **barras de vida de NPC más grandes**. (5) **Menú de Opciones apto para horizontal** (altura acotada + scroll; el control de la derecha se desplaza mientras está abierto). (6) **"Centrar en jugador"** evoluciona a un submenú con **"Hacer zoom en el jugador"** al hacer zoom; con el mapa descentrado los controles de movimiento de la izquierda recentran (sin zoom). (7) **El conductor desalojado** aparece junto al coche robado (~2 m). (8) **La versión del menú principal** se liga a `BuildConfig.VERSION_NAME` con un título que se autoajusta y nunca se parte de línea.
- **El teletransporte ahora espera la descarga del mapa** — teletransportarse (Ir a ESCOM / Ir a tu Ubicación GPS) ya no te suelta al instante: re-activa la compuerta de carga (`isMapReady=false`) y descarga las teselas de la nueva zona del proveedor activo antes de dejarte mover (OSM nativo guarda teselas reales en Room para offline; web calienta el CDN para que el WebView+caché se llenen; Google nativo muestra una compuerta breve). Corre en paralelo a la recarga de calles — `worldReady = calles listas && mapa listo`.
- **Limpieza de controles del mapa** — se quitaron los botones de zoom nativos duplicados de osmdroid (el zoom vive solo en el menú anidado *Mapa*); **"Centrar en jugador" siempre disponible**; **submenú anidado "Ir a…"** con *Ir a ESCOM* e *Ir a tu Ubicación (GPS)* (teletransporta el avatar a la posición GPS real del dispositivo —p. ej. de vuelta a casa— no a donde está el avatar). `OptionsMenu` ya soporta grupos anidados a cualquier nivel.
- **Mapa OSM nativo arreglado + caché offline unificada** — el mapa nativo (osmdroid) ahora lee/escribe la **misma caché Room** que las versiones Web (`RoomTileModuleProvider`, bucket `osm`), descargando con User-Agent de navegador. El descargador interno de osmdroid (UA = nombre de paquete) era estrangulado por el servidor público de OSM; por eso el nativo *no cargaba zonas nuevas* y la Web sí. Un `TilePrefetchManager` pre-descarga la zona actual (~2 km, zooms 16-18) a la BD local para jugar **100% offline** tras visitar (no bloqueante, avisa si queda incompleta). **El fog of war ahora se dibuja siempre** (antes se ocultaba al mover el mapa).
- **Servidor dedicado del minijuego zombi** (`MultiplayerZombie/`) — los zombis ahora son **autoritativos en el servidor** (Fase 1). Su IA usa un **campo de flujo de Dijkstra compartido** (un mapa de distancia-al-jugador por celda objetivo, reutilizado por todos los zombis que persiguen a ese jugador y cacheado ~250 ms), **línea de vista** (persecución en línea recta cuando no hay paredes de por medio) y **separación** (los zombis se empujan suavemente para no apilarse); un **fallback de deambular** evita que los zombis desconectados se congelen. El formato `ZOMBIE_STATE` en el cable no cambia (toda la IA vive en campos internos no serializados).
- **Servidor del open world endurecido a v2** (`Multiplayer/`) — reenvío por **Área de Interés (AOI)** para que `NPC_SPAWN/UPDATE/BATCH` solo lleguen a clientes cercanos al Host emisor (los mensajes globales como `PLAYER_UPDATE`, `PLAYER_DAMAGE`, `NPC_DESTROY`, `DISCONNECT` y sync siguen siendo globales), **elección de Host con throttle** (se reevalúa como mucho cada 200 ms por cliente), **rate-limit por socket** (ventana deslizante de 1 s, anti-flood), **saneamiento de entrada** (coordenadas finitas, daño acotado, tamaño máximo de mensaje) y **GC de jugadores fantasma** (no solo al cerrar el socket).
- **Escritura atómica de la caché de tiles** — la persistencia cuenta, hace evict (LRU) e inserta dentro de una sola transacción de Room (`@Transaction`), evitando corrupción si el proceso muere a media escritura.
- **Balance del minijuego zombi** — menor rango de ataque por contacto y **regeneración gradual de vida en el lobby** (zona segura) hasta 100 HP.
- **Efectos de daño** — *screen shake* al recibir golpes, viñeta/flash rojo cuya intensidad **escala con la vida perdida**, pulso de vida baja, **knockback a los zombis** (melee + proyectiles, consciente de colisiones) y **recoil del jugador** al disparar con corrección de posición por eje.
- **Controles en estado temporal** — los cambios de control (tipo/escala/swap) se quedan en estado temporal y solo afectan al juego al presionar **GUARDAR**.
- **Arreglo de rotación del mapa** — fondo oscuro en OSMDroid y wrapper de Leaflet sobredimensionado y centrado para que no se vean "huecos" al rotar en modo conducción.
- **Rendimiento** — pre-filtro por *bounding box* de cada *way* en el spawner de NPCs y claves de routing basadas en `Pair` (sin allocs de string por paso).

### ⚙️ Arquitectura

El repositorio contiene tres proyectos complementarios:

```text
.
├── PolitecnicoOpenWorld/   # Cliente Android (Kotlin + Compose)
├── Multiplayer/            # Servidor del open world (Node.js + WebSocket, v2, dockerizado)
└── MultiplayerZombie/      # Servidor del minijuego zombi (zombis autoritativos, dockerizado)
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

> Nota: esta IA de NPCs del open world corre en el **cliente** (en el Host de zona) — el servidor del open world solo arbitra el rol de Host y reenvía mensajes. El minijuego de zombis, en cambio, ahora corre su IA de zombis **en el servidor** (ver abajo).

### 🚓 Nivel de Búsqueda / Policía (estilo GTA)

Golpear civiles sube un **nivel de búsqueda** (0–5 estrellas, en el HUD). Mientras estés buscado, aparecen **patrullas** (`PoliceManager` + `PoliceSpriteManager`, con el asset `VEHICLES/POLICE_TOPDOWN` sin repintar) **lejos** de ti — más patrullas por estrella — y te persiguen **respetando las calles** (cada paso se ajusta a la red de carreteras). Fuera de la neblina se marcan con un waypoint 🚓 y una línea de ruta; dentro de la neblina se ve el asset real de la patrulla. Al llegar, la patrulla suelta **2–3 policías** (emoji 👮, no hay asset de persona) que te persiguen y golpean, y **disparan a 2★+**. Si te subes a un auto, los policías te siguen en patrulla y pueden **bajarte del vehículo** si te detienes (sin daño mientras conduces); al morir, la policía **se retira** en lugar de desaparecer de golpe. El jugador buscado **simula y posee** su propia policía y difunde `POLICE_BATCH_UPDATE` / `POLICE_DESTROY`; los demás clientes solo la renderizan.

### 🧭 Navegación y Waypoints

El jugador coloca un marcador de destino; el ViewModel ejecuta una búsqueda greedy sobre el grafo de calles (`calculateRouteOnNetwork`) y dibuja una polilínea azul punteada. El routing usa claves `Pair<Double, Double>` para nodos visitados/únicos (sin allocs de string por paso). El marcador se auto-elimina a 20 m.

### 🧟 Minijuego de Zombis

Anillo circular de cuartos: un lobby con puertas a cada edificio de ESCOM, con puertas EXIT entre vecinos y una central de regreso al lobby.

- **Cámara** (`CameraTransform`): consciente del zoom, se ajusta a límites, usa `max(viewW/worldW, viewH/worldH)`.
- **Online vs offline:** el `ZombieGameViewModel` ejecuta un único game loop que se ramifica en `tickOffline` (simulación local completa) o `tickOnline`. **En multijugador, zombis e items son autoritativos del servidor dedicado** (`MultiplayerZombie/`): el cliente renderiza los snapshots `ZOMBIE_STATE`, envía peticiones `ZOMBIE_DAMAGE` / `ITEM_PICKUP` y solo conserva lo local (su HP, proyectiles, cooldown de daño por contacto). En offline, el cliente simula los zombis localmente con pathfinding por matriz y sliding por eje.
- **El rango de ataque por contacto se redujo** para un melee más justo.
- **Regeneración en el lobby:** estando en el lobby por debajo de 100 HP, el jugador se cura gradualmente por tick hasta 100.
- **Combate dual:** mantener **Y** (500 ms) alterna MELEE/RANGED; **B** ataca.
- **Efectos de daño:** *screen shake* al recibir golpes, **viñeta/flash rojo que escala con la vida perdida**, **pulso de vida baja**, **knockback a los zombis** en golpes/proyectiles (consciente de colisiones) y **recoil del jugador** al disparar con corrección de posición por eje para no atravesar paredes.
- **Drops de SkillEffect:** seis efectos (45%) dibujados como iconos de Canvas puros.
- **Modo Diseñador de colisión:** un editor in-game pinta la matriz de colisión de cada sala sobre el dibujo del cuarto, la persiste en `collision_matrices.json` y permite exportar/importar el mismo JSON que lee el servidor.
- **Iluminación dinámica** en interiores oscuros; pantallas **WASTED/Victoria**.

### 🌐 Multijugador en Tiempo Real

POW ahora incluye **dos servidores Node.js + Express + ws independientes**, ambos dockerizados y hospedados en Render, compartiendo el mismo `WebSocketManager` en el cliente.

#### Servidor del open world — `Multiplayer/server.js` (v2)

Reparte autoridad con el patrón **Host de Zona**: cada cliente es Host en ~400 m, el de menor `sessionId` cede en solapamientos, y solo el Host ejecuta la IA de NPCs y publica `NPC_BATCH_UPDATE`; los NPCs huérfanos se adoptan, no se destruyen. El endurecimiento v2 añade:

- **Área de Interés (AOI):** `NPC_SPAWN/UPDATE/BATCH` se reenvían solo a clientes cercanos al Host emisor (~2 km), recortando drásticamente ancho de banda y serialización con jugadores dispersos. Los mensajes que deben llegar siempre (`PLAYER_UPDATE`, `PLAYER_DAMAGE`, `NPC_DESTROY`, `DISCONNECT`, sync) se mantienen globales para no dejar entidades fantasma.
- **Elección de Host con throttle:** el rol de Host se reevalúa como mucho cada 200 ms por cliente, no en cada `PLAYER_UPDATE`.
- **Rate-limit por socket:** se descartan mensajes si un cliente inunda (ventana deslizante de 1 s), más un tamaño máximo de mensaje aceptado.
- **Saneamiento de entrada:** coordenadas finitas, daño finito y acotado, reenvío saneado.
- **GC de jugadores fantasma:** los jugadores obsoletos se limpian periódicamente (no solo al cerrar el socket), junto con heartbeats, GC de NPCs huérfanos y master sync periódico. Los colores se serializan como Int ARGB.
- **Relay de policía (v3.2):** `POLICE_BATCH_UPDATE` (con AOI) y `POLICE_DESTROY` (global) se reenvían pero **no** se guardan en el roster persistente de NPCs — la policía es transitoria y por-jugador (la posee el cliente buscado), así que cada cliente la purga por "staleness".

#### Servidor del minijuego zombi — `MultiplayerZombie/server.js` (Fase 1)

Ejecuta la **simulación de zombis de forma autoritativa** por sala y difunde `ZOMBIE_STATE`. Su IA v2:

- **Campo de flujo compartido (mapa de Dijkstra):** en vez de un A* por zombi, el servidor calcula **un** campo de distancia-al-jugador sobre toda la matriz (por celda objetivo) y todos los zombis que persiguen a ese jugador siguen el gradiente cuesta abajo. Coste O(número de jugadores) campos por sala, cacheados ~250 ms.
- **Línea de vista (LOS):** si un zombi ve al jugador sin paredes de por medio, va en línea recta (más fluido, sin tocar el campo); el campo solo se usa cuando hay un obstáculo.
- **Separación (steering):** los zombis se empujan suavemente para una horda más natural.
- **Fallback de deambular:** los zombis en una zona desconectada (sin distancia finita en el campo y sin LOS) deambulan en vez de congelarse.

Las coordenadas en el cable son fraccionarias `[0,1]` (el cliente convierte a píxeles). Las matrices de colisión vienen de `collision_matrices.json` (el mismo formato que exporta el Modo Diseñador), con valores neutros (solo borde) que deben mantenerse idénticos (filas/columnas) a las del cliente mientras no se sustituyan.

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
| Servidores | Node.js 18, Express, ws, Docker (open world + minijuego zombi) |
| Hosting | [Render](https://render.com/) (auto-deploy desde Dockerfile) |

### 🐳 Desplegar los servidores multijugador

```bash
# Servidor del open world (puerto 8080)
cd Multiplayer
docker compose up -d

# Servidor del minijuego zombi (puerto host 8081 → contenedor 8080)
cd MultiplayerZombie
docker compose up -d
```

En producción son dos Web Services de Render, cada uno reconstruido desde su propio `Dockerfile`. Ambos escuchan en el puerto **8080** del contenedor (`GET /status`, `WS /`). La URL del open world se inyecta en compilación vía `BuildConfig.MULTIPLAYER_SERVER_URL`; la del minijuego zombi vía `BuildConfig.ZOMBIE_SERVER_URL`.

### 📍 Estado actual

**Funciona hoy:** navegación OSM/Google/Web con snap-to-road, caché persistente de calles y tiles (escritura atómica), over-zoom nativo a z22 (escalado desde z19, con precarga z19/z17 en la pantalla de carga y zoom máximo por defecto), niebla de guerra anclada al jugador en nativo + web (segura ante la rotación al conducir), tamaños en metros reales de NPC/jugador unificados entre renderizadores, menú de Opciones apto para horizontal con scroll, NPCs procedurales (spawn pre-filtrado por bbox) con personalidades, atropello al conducir, contraataque agresivo/reacciones al robo de coche y tráfico en doble sentido, combate cuerpo a cuerpo contra NPCs y jugadores remotos, multijugador del open world con autoridad por zona (v2: AOI + throttle de host + rate-limit + saneamiento), conducción de vehículos, controles configurables en estado temporal, 8 proveedores de mapas, landmarks editables con import/export JSON, 6 coleccionables, routing por waypoints, 6 interiores de ESCOM, un minijuego completo de supervivencia contra zombis (lobby + 7 edificios, combate dual, 6 power-ups, iluminación dinámica, efectos de daño, regen en lobby, pantallas WASTED/Victoria) con un **servidor de zombis dedicado y autoritativo** (IA de campo de flujo + LOS + separación), Modo Diseñador de colisión y un interior easter-egg ShineCTO. Un **pase de GC y memoria para gama baja (≤2 GB / Android 7–9)** sin cambiar el comportamiento mantiene estables las sesiones largas en equipos modestos: caché de drawables nativa acotada por LRU, bitmap de brillo de puerta reutilizado, iconos de bala/🔫/📞 de la policía cacheados por tamaño, landmarks de navegación filtrados una sola vez y cachés de sprites liberadas en `onTrimMemory`.

**No implementado aún:** pathfinding con A* para el router del open world (sigue siendo greedy; nótese que el servidor de zombis ya usa un campo de flujo de Dijkstra), multijugador local por Bluetooth, matrices de colisión ricas para interiores, números de daño flotantes / partículas de impacto, reajuste de coordenadas de puertas del lobby, y colisiones coche-coche (planeado: solapamiento de círculos acotado al radio de culling + reducción de velocidad, sin motor de física).


### 🔁 Mantener Esta Documentación al Día

`README.md` y `plan.artifact.md` son la **única fuente de verdad** que le pasamos a cualquier asistente (humano o IA) *en lugar de* todo el código — así nunca tenemos que volver a explicar el proyecto. Solo son confiables si se actualizan **en el mismo cambio** que toca el código, nunca después.

El checklist detallado está en **`plan.artifact.md` §11**. En resumen, en cada cambio que altere el comportamiento:

- Actualiza **ambos** archivos (`README.md` es bilingüe — refleja los cambios visibles para el usuario en las secciones en inglés **y** en español para que no se desincronicen).
- Agrega el cambio al inicio de **Cambios Recientes**; poda entradas de más de ~2–3 versiones.
- Mueve lo terminado de **No implementado aún** a **Funciona hoy** (y las listas espejo en `plan.artifact.md` §7/§8).
- Un dato que viva en ambos archivos debe cambiarse en ambos — una contradicción entre ellos es un bug.

**Definición de terminado:** el código compila/valida *y* ambos archivos describen la nueva realidad. Si la documentación no se actualizó, la tarea no está terminada.
