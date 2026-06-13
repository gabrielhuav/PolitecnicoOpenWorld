# Politécnico Open World (POW)

> 🇬🇧 **English version below** · 🇪🇸 [Saltar a la versión en español](#-versión-en-español)

---

## 🇬🇧 English Version

**Politécnico Open World (POW)** is an Android 2D top-down exploration app built on top of real-world maps. The player navigates the actual streets of their surroundings (with initial focus on the ESCOM / Zacatenco area in Mexico City) using **OpenStreetMap** cartographic data, sharing the world with procedural NPCs (pedestrians and vehicles) and with other players connected to a real-time server. The ESCOM campus also hosts an embedded zombie survival minigame with interior buildings, melee/ranged combat and a power-up system — now backed by its own authoritative server.

The project is built entirely with **Kotlin + Jetpack Compose**, follows a strict **MVVM** pattern organized by *features*, and delegates persistent-world logic to **two standalone Node.js servers**: one for the open world and a new dedicated one for the zombie minigame.

### 🔄 Recent Changes

The latest integration work centers on the **multiplayer back end**, which now spans two servers, plus the previously bundled feature/performance PRs:

- **Smooth zoom transitions + realistic traffic AI + online player melee** — (1) **Zoom no longer shocks:** stealing or exiting a car now smoothly interpolates the zoom level over ~300 ms (`targetZoomLevel` + per-tick lerp in the game loop) instead of snapping instantly. (2) **Traffic AI no longer shakes at corners:** `NpcAiManager.moveNpc` now implements **intersection commitment** (locks the chosen path for ~10 ticks, preventing indecisive re-evaluation), **rear-end collision avoidance** (cars brake if the one ahead is too close), and **speed variation** (random 0.8–1.2 multiplier per car) for much more realistic driving. (3) **Online player melee fixed:** `performPlayerAttack` now checks distance against remote players; if hit, it sends the existing `PLAYER_DAMAGE` message to the server, which relays it to the victim to apply damage (attacker-authoritative, low latency). It also ensures `displayName` is never blank to correctly identify remote players.

- **NPC overtake redesigned (NPC-frame avoidance, no position shoves)** — shoving the NPC's POSITION away from the stopped player caused orbits/oscillations (shove → its road-following pulled it back → shove again) and excessive opening. The shove was REMOVED; `NpcAiManager.moveNpc` now offsets the car's **target** by ~one lane while the player is in its path (radius ~13 m, path width ±5.5 m, ~5.5 m hysteresis after passing) and the normal smoothing merges it back — open, pass, close, never touching positions.
- **Driving-dodge bounds + full teleport loading screen + WorldMapViewModel refactor** — (1) the player's auto-dodge no longer visually leaves the road (per-tick lateral cap + overtake radius 0.00006→0.000045) and NPC cars shoved while passing you are never pushed beyond ~2.8 m off the road (force 0.08→0.03), so they stop "opening up" after overtaking. (2) The load gate now ALSO waits for the **NPC warm-up** (`npcsWarmedUp`, ~5 AI cycles) and **pre-decodes nearby landmark bitmaps** during the loading screen — after a TP you spawn with buildings AND NPCs already placed ("Colocando NPCs y edificios…" phase). (3) **Refactor:** `WorldMapViewModel.kt` went from ~3,400 to ~2,600 lines by extracting `WorldMapProviders.kt`, `WorldMapDesigner.kt` and `WorldMapWanted.kt` (state stays in the VM) and de-duplicating member/extension twins (`updateNpcsState`, routing helpers — the Routing extension was synced first since it was stale). External call sites now import the extensions. See `PR_CHANGES.md` for the full PR description.
- **Car-duplication fix + teleport load ordering + speedometer calibration** — (1) spamming **Y** to board/exit could **duplicate the car**: the AI's stale snapshot re-inserted the just-boarded car (main-thread vs game-loop race); boarding now has a **450 ms debounce** plus **tombstones** (`boardedCarTombstones`, ~10 s) that the AI write-back and `NPC_BATCH_UPDATE` ignore, and the id is broadcast as `NPC_DESTROY`. (2) **Teleport ordering**: NPC simulation/spawning is now gated on `isRoadNetworkReady && isMapReady`, so after a TP the order is always tiles → roads → NPCs; chained TPs are **rejected while the world is still loading** ("⏳" prompt) and old-zone NPCs are cleared on TP. (3) The **speedometer is calibrated** to feel believable: MAX_SPEED maps to 120 km/h (the avatar's raw geographic speed is ~204 km/h by design).
- **State-based auto zoom + speedometer + NPC car heading fix** — (1) gameplay zoom now follows the player's state: **22 on foot**, **21 driving**, dropping to **20 at very high speed** (≥85% of MAX_SPEED, back to 21 below 65%; `updateAutoZoom()` in the game loop only acts on mode TRANSITIONS, so manual pinch is respected in between). (2) New toggleable **speedometer widget** (Settings → Interface, default ON): km/h while driving, color-coded by speed. (3) **NPC cars no longer drive at a wrong angle**: their sprite used the smoothed heading but the car MOVED along the raw angle to its target — in corners/dodges it visibly slid sideways; cars now move along their sprite heading (smoothing 0.30).
- **CARTO Voyager default + web designer pencil + stability fixes** — (1) new default provider **`CARTO_VOYAGER`** (web; real tiles up to **z20** vs OSM's z19 → sharper streets; `changeTileUrl(url, maxNative)` now recreates the Leaflet layer per-provider and no-ops on unchanged URLs). (2) **Designer Mode works on the web renderer**: draggable ✏️ pencils per landmark (select + move via `MapJsBridge.notifyLandmarkSelected/Moved` → `selectLandmark`/`moveLandmarkTo`), matching native parity. (3) The **"Zoom (acercar/alejar)" submenu was removed** (pinch-only zoom) and a toggleable **zoom-level widget** was added (Settings → Interface, persisted). (4) Gameplay settings: the old switch was renamed **"Optimizar dibujado de NPCs"** (`npcEmojiLod`, distance LOD) and a new **"Optimizar para gama baja"** (`npcFullEmoji`) renders **ALL NPCs as emojis in all three renderers**. (5) Stability: snap-to-road no longer **teleports** the player to a far road when the refreshed network doesn't cover their spot (`MAX_SNAP_DISTANCE_DEG` guard); GPS spawn/teleport uses a **fresh `getCurrentLocation(HIGH_ACCURACY)`** instead of the stale `lastLocation` cache; the web map self-heals stuck `isZooming`/`isExplorationMode` flags (the "controls move but the skin doesn't" freeze); ESCOM **campus AI repopulates** when you return (it used to despawn at ~310 m but only re-arm beyond ~2.2 km); the road-network refetch now also rebuilds the routing grid + A* graph.
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

**Works today:** OSM/Google/Web navigation with snap-to-road, persistent street and tile caching (atomic writes), native over-zoom to z22 (scaled from z19, with z19/z17 loading-screen prefetch and max-zoom default), player-anchored fog of war on native + web (driving-rotation safe), real-meter NPC/player sizing unified across renderers, landscape-safe scrollable Options menu, procedural NPCs (bbox-prefiltered spawn) with personality traits, run-over-while-driving, aggressive retaliation/carjack reactions and two-way traffic, **realistic traffic AI (intersection commitment, rear-end avoidance, speed variation)**, **smooth zoom transitions when entering/exiting vehicles**, **working melee combat vs NPCs AND remote players in online mode**, zone-delegated open-world multiplayer (v2: AOI + host throttle + rate-limit + sanitization), vehicle driving, staged configurable controls, 8 map providers, editable landmarks with JSON import/export, 6 lore collectibles, waypoint routing, 6 ESCOM interiors, a full zombie survival minigame (lobby + 7 buildings, dual combat, 6 power-ups, dynamic lighting, damage-feedback FX, lobby regen, WASTED/Victory screens) with a **dedicated authoritative zombie server** (flow-field + LOS + separation AI), collision Designer Mode, and a ShineCTO easter-egg interior. A behavior-preserving **low-end (≤2 GB / Android 7–9) GC & memory pass** keeps long sessions stable on weak devices: an LRU-bounded native drawable cache, a reused door-glow bitmap, size-cached police bullet/🔫/📞 icons, once-filtered nav landmarks, and sprite caches freed on `onTrimMemory`.

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

- **Transiciones de zoom suaves + IA de tráfico realista + melee online entre jugadores** — (1) **El zoom ya no shockea:** robar o salir de un auto ahora interpola suavemente el nivel de zoom durante ~300 ms (`targetZoomLevel` + lerp por tick en el game loop) en vez de cambiar de golpe. (2) **La IA de tráfico ya no tiembla en las esquinas:** `NpcAiManager.moveNpc` ahora implementa **compromiso de intersección** (bloquea el camino elegido por ~10 ticks, evitando re-evaluaciones indecisas), **evitación de alcance** (los autos frenan si el de adelante está muy cerca) y **variación de velocidad** (multiplicador aleatorio 0.8–1.2 por auto) para una conducción mucho más realista. (3) **Melee online entre jugadores arreglado:** `performPlayerAttack` ahora también checkea distancia contra jugadores remotos; si golpea a uno, envía el mensaje existente `PLAYER_DAMAGE` al servidor, que lo reenvía a la víctima para aplicar daño (autoritativo del atacante, baja latencia). Se asegura que `displayName` nunca sea blank para identificar correctamente a los jugadores remotos.

- **Rebase de NPCs rediseñado (esquive en el marco del NPC, sin empujones de posición)** — empujar la POSICIÓN del NPC lejos del jugador detenido causaba órbitas/oscilaciones (empuje → su road-following lo regresaba → empuje otra vez) y aperturas excesivas. Se ELIMINÓ el empujón; ahora `NpcAiManager.moveNpc` desplaza **el objetivo** del coche ~un carril mientras el jugador esté en su trayectoria (radio ~13 m, ancho ±5.5 m, histéresis ~5.5 m tras rebasar) y el smoothing normal lo reincorpora — abre, pasa, cierra, sin tocar posiciones.
- **Esquives acotados + pantalla de carga completa tras TP + refactor del WorldMapViewModel** — (1) el auto-esquive del jugador ya no se sale visualmente de la carretera (cap lateral por tick + radio de rebase 0.00006→0.000045) y los coches NPC empujados al rebasarte nunca se alejan a más de ~2.8 m de la red (fuerza 0.08→0.03): dejan de "abrirse" tras pasarte. (2) El gate de carga ahora TAMBIÉN espera el **warm-up de NPCs** (`npcsWarmedUp`, ~5 ciclos de IA) y **pre-decodifica los bitmaps de las construcciones cercanas** durante la pantalla de carga — tras un TP apareces con edificios Y NPCs ya colocados (fase "Colocando NPCs y edificios…"). (3) **Refactor:** `WorldMapViewModel.kt` pasó de ~3,400 a ~2,600 líneas extrayendo `WorldMapProviders.kt`, `WorldMapDesigner.kt` y `WorldMapWanted.kt` (el estado sigue en el VM) y de-duplicando gemelos miembro/extensión (`updateNpcsState`, helpers de routing — la extensión de Routing se sincronizó primero porque estaba desactualizada). Los call-sites externos ahora importan las extensiones. Ver `PR_CHANGES.md` para la descripción completa del PR.
- **Fix de duplicación de autos + orden de carga del teleport + calibración del velocímetro** — (1) spamear **Y** para subir/bajar podía **duplicar el coche**: el snapshot viejo de la IA re-insertaba el coche recién abordado (carrera main-thread vs game loop); ahora hay **debounce de 450 ms** + **tombstones** (`boardedCarTombstones`, ~10 s) que el volcado de la IA y `NPC_BATCH_UPDATE` ignoran, y el id se difunde como `NPC_DESTROY`. (2) **Orden de carga tras teleport**: la simulación/spawn de NPCs está gateada por `isRoadNetworkReady && isMapReady` → siempre tiles → calles → NPCs; los TPs encadenados se **rechazan mientras el mundo carga** (aviso "⏳") y al teletransportarte se limpian los NPCs de la zona vieja. (3) El **velocímetro quedó calibrado** para sentirse creíble: MAX_SPEED se mapea a 120 km/h (la velocidad geográfica cruda del avatar es ~204 km/h por diseño).
- **Zoom automático por estado + velocímetro + arreglo del ángulo de los coches NPC** — (1) el zoom de juego ahora sigue el estado del jugador: **22 a pie**, **21 conduciendo**, y baja a **20 a muy alta velocidad** (≥85% de MAX_SPEED, vuelve a 21 bajo el 65%; `updateAutoZoom()` en el game loop solo actúa en TRANSICIONES de modo, así el pinch manual se respeta entre cambios). (2) Nuevo **widget velocímetro** activable (Ajustes → Interfaz, default activado): km/h al conducir, con color según la velocidad. (3) **Los coches NPC ya no se manejan con un ángulo incorrecto**: el sprite usaba el heading suavizado pero el coche se MOVÍA con el ángulo crudo a su objetivo — en curvas/esquives se veía deslizándose de lado; ahora los coches se mueven en la dirección de su sprite (smoothing 0.30).
- **CARTO Voyager por defecto + lápiz del diseñador en web + arreglos de estabilidad** — (1) nuevo proveedor por defecto **`CARTO_VOYAGER`** (web; teselas reales hasta **z20** vs z19 de OSM → calles más nítidas; `changeTileUrl(url, maxNative)` recrea la capa Leaflet por proveedor y no-opea si la URL no cambió). (2) **El Modo Diseñador ya funciona en el renderer web**: lápices ✏️ arrastrables por landmark (seleccionar + mover vía `MapJsBridge.notifyLandmarkSelected/Moved` → `selectLandmark`/`moveLandmarkTo`), con paridad nativa. (3) Se **eliminó el submenú "Zoom (acercar/alejar)"** (el zoom es solo con pinch) y se agregó un **widget de nivel de zoom** activable (Ajustes → Interfaz, persistido). (4) Jugabilidad: el switch anterior se renombró a **"Optimizar dibujado de NPCs"** (`npcEmojiLod`, LOD por distancia) y se agregó **"Optimizar para gama baja"** (`npcFullEmoji`) que dibuja **TODOS los NPCs como emojis en los tres renderers**. (5) Estabilidad: el snap-to-road ya **no teletransporta** al jugador a una calle lejana cuando la red recargada no cubre su zona (guarda `MAX_SNAP_DISTANCE_DEG`); el GPS de spawn/teleport usa una lectura **fresca `getCurrentLocation(HIGH_ACCURACY)`** en vez de la caché `lastLocation`; el mapa web se auto-repara si `isZooming`/`isExplorationMode` quedan atorados (el bug de "me muevo con los controles pero el skin no"); la **IA del campus de ESCOM se repuebla** al volver (se despawneaba a ~310 m pero solo se re-armaba a ~2.2 km); el re-fetch de la red vial ahora también reconstruye el grid de routing + grafo A*.
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

**Funciona hoy:** navegación OSM/Google/Web con snap-to-road, caché persistente de calles y tiles (escritura atómica), over-zoom nativo a z22 (escalado desde z19, con precarga z19/z17 en la pantalla de carga y zoom máximo por defecto), niebla de guerra anclada al jugador en nativo + web (segura ante la rotación al conducir), tamaños en metros reales de NPC/jugador unificados entre renderizadores, menú de Opciones apto para horizontal con scroll, NPCs procedurales (spawn pre-filtrado por bbox) con personalidades, atropello al conducir, contraataque agresivo/reacciones al robo de coche y tráfico en doble sentido, **IA de tráfico realista (compromiso de intersección, evitación de alcance, variación de velocidad)**, **transiciones de zoom suaves al subir/bajar de vehículos**, **combate cuerpo a cuerpo funcional contra NPCs Y jugadores remotos en modo online**, multijugador del open world con autoridad por zona (v2: AOI + throttle de host + rate-limit + saneamiento), conducción de vehículos, controles configurables en estado temporal, 8 proveedores de mapas, landmarks editables con import/export JSON, 6 coleccionables, routing por waypoints, 6 interiores de ESCOM, un minijuego completo de supervivencia contra zombis (lobby + 7 edificios, combate dual, 6 power-ups, iluminación dinámica, efectos de daño, regen en lobby, pantallas WASTED/Victoria) con un **servidor de zombis dedicado y autoritativo** (IA de campo de flujo + LOS + separación), Modo Diseñador de colisión y un interior easter-egg ShineCTO. Un **pase de GC y memoria para gama baja (≤2 GB / Android 7–9)** sin cambiar el comportamiento mantiene estables las sesiones largas en equipos modestos: caché de drawables nativa acotada por LRU, bitmap de brillo de puerta reutilizado, iconos de bala/🔫/📞 de la policía cacheados por tamaño, landmarks de navegación filtrados una sola vez y cachés de sprites liberadas en `onTrimMemory`.

**No implementado aún:** pathfinding con A* para el router del open world (sigue siendo greedy; nótese que el servidor de zombis ya usa un campo de flujo de Dijkstra), multijugador local por Bluetooth, matrices de colisión ricas para interiores, números de daño flotantes / partículas de impacto, reajuste de coordenadas de puertas del lobby, y colisiones coche-coche (planeado: solapamiento de círculos acotado al radio de culling + reducción de velocidad, sin motor de física).


### 🔁 Mantener Esta Documentación al Día

`README.md` y `plan.artifact.md` son la **única fuente de verdad** que le pasamos a cualquier asistente (humano o IA) *en lugar de* todo el código — así nunca tenemos que volver a explicar el proyecto. Solo son confiables si se actualizan **en el mismo cambio** que toca el código, nunca después.

El checklist detallado está en **`plan.artifact.md` §11**. En resumen, en cada cambio que altere el comportamiento:

- Actualiza **ambos** archivos (`README.md` es bilingüe — refleja los cambios visibles para el usuario en las secciones en inglés **y** en español para que no se desincronicen).
- Agrega el cambio al inicio de **Cambios Recientes**; poda entradas de más de ~2–3 versiones.
- Mueve lo terminado de **No implementado aún** a **Funciona hoy** (y las listas espejo en `plan.artifact.md` §7/§8).
- Un dato que viva en ambos archivos debe cambiarse en ambos — una contradicción entre ellos es un bug.

**Definición de terminado:** el código compila/valida *y* ambos archivos describen la nueva realidad. Si la documentación no se actualizó, la tarea no está terminada.
