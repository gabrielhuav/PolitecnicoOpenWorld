# plan.artifact.md — LLM Context for Politécnico Open World (POW)

> Purpose: give an AI assistant the minimum-but-complete mental model of this
> repository so it can navigate, reason about, and safely modify the code without
> re-deriving the architecture each time. This is a machine-oriented companion to
> `README.md` (which targets humans).

## 1. What this project is

POW is an **Android 2D top-down exploration game** built on **real-world maps**
(OpenStreetMap). The player walks/drives real streets (initial focus: ESCOM /
Zacatenco, Mexico City), sharing the world with procedural NPCs (pedestrians +
vehicles) and other players over a real-time server. The ESCOM campus hosts an
embedded **zombie survival minigame** with building interiors and melee/ranged
combat.

- **Language/UI:** Kotlin + Jetpack Compose + Material 3.
- **Architecture:** strict **MVVM**, organized **by feature**.
- **Servers:** **two** standalone **Node.js + ws** processes (both dockerized,
  hosted on Render):
  - `Multiplayer/` — open-world server (v2), injected via
    `BuildConfig.MULTIPLAYER_SERVER_URL`.
  - `MultiplayerZombie/` — zombie-minigame server with **authoritative zombies**,
    injected via `BuildConfig.ZOMBIE_SERVER_URL`.
- **Min package root:** `ovh.gabrielhuav.pow`.

## 2. Repo shape

```
.
├── PolitecnicoOpenWorld/        # Android client (this repo)
│   └── app/src/main/java/ovh/gabrielhuav/pow/
│       ├── data/      # Room DB, caches, network, repositories
│       ├── domain/    # pure Kotlin models + AI (no Android deps)
│       ├── features/  # feature modules: <name>/ui + <name>/viewmodel
│       ├── ui/theme/  # Material 3 theme
│       └── MainActivity.kt# single Activity + Compose NavHost
├── Multiplayer/                 # Open-world Node.js server (v2: AOI + host throttle + rate-limit)
└── MultiplayerZombie/           # Zombie-minigame Node.js server (authoritative zombie AI)
```

Note: the two server directories are siblings of the Android project; they may
not always be present in a given checkout.

## 3. MVVM contract (follow this when adding code)

Every feature splits into three layers:

- **Model** (`domain/models/`): immutable `data class`es + pure logic helpers
  (`NpcAiManager`). No Android imports, no UI.
- **ViewModel** (`features/<name>/viewmodel/`): holds ONE
  `MutableStateFlow<State>` exposed as read-only `StateFlow`; drives game loops
  with coroutines; orchestrates repositories. State is an immutable `data class`
  updated via `_state.update { it.copy(...) }`.
- **View** (`features/<name>/ui/`): pure Compose; observes via `collectAsState()`;
  only emits intents back to the ViewModel. Views never touch repositories/DAOs.

Top-level ViewModels (`WorldMapViewModel`, `SettingsViewModel`,
`CollectiblesViewModel`) are **Activity-scoped** (survive navigation). Interior and
zombie ViewModels are **NavBackStackEntry-scoped** (reset on leave). DI is manual
via co-located `ViewModelProvider.Factory` instances.

## 4. Key files (where to look first)

| Concern | File |
|---|---|
| Navigation / entry point | `MainActivity.kt` (Compose `NavHost`, 9+ routes) |
| Open-world game loop, multiplayer, NPCs, ESCOM logic | `features/map_exterior/viewmodel/WorldMapViewModel.kt` (+ `WorldMap*.kt` partials: GameLoop, Multiplayer, RoadNetwork, Routing, Escom, Collectibles, Misc) |
| Open-world UI state | `features/map_exterior/viewmodel/WorldMapState.kt` |
| Map rendering (OSM/Google/Leaflet WebView) | `features/map_exterior/ui/WorldMapScreen.kt` (Leaflet HTML built in `WorldMapLeafletHtml.kt`) |
| Leaflet tile interception | `features/map_exterior/ui/CachingWebViewClient.kt` |
| NPC population / spawn / movement / adoption (client-side) | `domain/models/ai/NpcAiManager.kt` |
| Zombie minigame logic | `features/zombie_minigame/viewmodel/ZombieGameViewModel.kt` (+ `ZombieGameTick.kt`, `ZombieGameConstants.kt`) |
| Zombie minigame state | `features/zombie_minigame/viewmodel/ZombieGameState.kt` |
| Zombie net models (client) | `features/zombie_minigame/viewmodel/Zombienetmodels.kt` |
| Zombie rendering + camera + damage FX | `features/zombie_minigame/ui/ZombieGameScreen.kt` |
| Zombie rooms / doors / collision | `domain/models/zombie/ZombieModels.kt`, `ZombieRoomCatalog.kt` |
| Zombie collision-matrix persistence | `data/repository/CollisionMatrixRepository.kt` (`collision_matrices.json`) |
| Settings (map/controls/gameplay/interface) | `features/settings/{ui,viewmodel}/...` |
| Settings persistence | `data/repository/SettingsRepository.kt` (SharedPreferences) |
| Tile cache (Room) | `data/cache/TileCache.kt` + `data/local/room/dao/MapTileDao.kt` |
| Road-network cache (Room) | `data/cache/RoadNetworkCache.kt` + `RoadNetworkDao.kt` |
| Multiplayer warm-up (Render) | `features/main_menu/ui/ServerWarmupManager.kt` (package `data.network`) |
| **Open-world server (v2)** | `Multiplayer/server.js` |
| **Zombie-minigame server (authoritative)** | `MultiplayerZombie/server.js` |

## 5. Domain concepts an editor must respect

- **Snap-to-road:** the player can't leave the road network; movement is validated
  against a spatial grid index (`Seg` + `HashMap<cell, segments>`), O(nearby).
- **Road cache:** ~2 km cells, 7-day TTL, LRU of 20 cells, **atomic** zone+ways+nodes
  insert (`@Transaction`), 5-min re-fetch cooldown.
- **Tile cache:** per-provider, ~8k tiles max, key = normalized URL hashed with
  SHA-256; **writes are atomic** (count→evict→insert in one `@Transaction`).
- **NPCs (open world, client-side):** up to 40 around the player; pedestrians vs 6
  car models; per-pixel tinting; proximity spawn / distance despawn; **adoption**
  snaps server-inherited NPCs to the nearest way. Spawn uses a **per-way bbox
  pre-filter** before the per-node distance check. The open-world server does NOT
  simulate NPCs — the Zone Host runs the AI on the client and the server relays.
- **Zone-Host open-world multiplayer authority (`Multiplayer/server.js`, v2):**
  each client is Host within ~400 m; lower `sessionId` wins on overlap; only the
  Host runs NPC AI and emits `NPC_BATCH_UPDATE`; orphaned NPCs are adopted, not
  destroyed. v2 hardening: **AOI relay** (NPC spawn/update/batch only to clients
  within ~2 km of the emitting Host; global messages — `PLAYER_UPDATE`,
  `PLAYER_DAMAGE`, `NPC_DESTROY`, `DISCONNECT`, sync — stay global), **throttled
  Host election** (≤ every 200 ms/client), **per-socket rate limit** (1 s sliding
  window) + max message size, **input sanitization** (finite coords, bounded
  damage), and **ghost-player GC** (periodic, not only on close). Colors serialized
  as ARGB Int (not Compose `Color` ULong).
- **Zombie minigame (`MultiplayerZombie/server.js`, Phase 1 — server-authoritative
  zombies):** ring of rooms (lobby + 7 ESCOM buildings); camera uses
  `max(viewW/worldW, viewH/worldH)` (ContentScale.Crop equivalent); dual combat
  (MELEE/RANGED via Y-hold menu, B to attack); 6 SkillEffect drops rendered as pure
  Canvas icons; collision via fractional `collisionMatrix` (`isBlockedFrac`, O(1)).
  - **Client loop branches:** `tickOffline` (full local simulation, local zombies
    with axis-slide pathfinding) vs `tickOnline` (zombies/items are authoritative
    from the server via `ZOMBIE_STATE`; client only owns its HP, projectiles,
    contact-damage cooldown; sends `ZOMBIE_DAMAGE`/`ITEM_PICKUP` requests).
  - **Server AI v2:** a **shared Dijkstra flow-field** (one distance-to-player map
    per target cell, reused by every zombie chasing that player, cached ~250 ms via
    a binary `MinHeap`), **line-of-sight** straight-line pursuit when unobstructed,
    **separation steering**, and a **wander fallback** for disconnected regions.
    Wire coords are fractional `[0,1]`; `ZOMBIE_STATE` JSON is unchanged (AI lives
    in non-serialized fields). Collision matrices come from `collision_matrices.json`.

## 6. Conventions / gotchas

- State updates are always immutable copies; never mutate Compose state directly.
- Settings **controls** are staged in `temp*` fields and only committed on SAVE
  (`saveControlsSettings()`); `discardControlsChanges()` on exit. Do not wire
  control edits straight into committed state.
- Comments/strings in this codebase are predominantly Spanish; keep that style for
  consistency unless asked otherwise. The two `server.js` files are also Spanish-
  commented; mirror that.
- The Leaflet map lives inside a WebView; the `#map-wrapper` is intentionally
  oversized (`300vw × 300vh`, centered) so rotation never reveals gaps. Don't
  shrink it back.
- Room DB is currently `@Database` v8 with `MIGRATION_7_8` + destructive fallback.
- Persisted prefs go through `SettingsRepository` (SharedPreferences), not Room.
- The **client and both servers must agree on collision matrices**: the Designer
  Mode exports `collision_matrices.json` in the exact shape the zombie server
  reads; default matrices are border-only and must match rows/cols on both sides
  until replaced.

## 7. Current state (what works)

OSM/Google/Web navigation with snap-to-road; persistent street + tile caching;
procedural NPCs (pedestrians + 6 cars); melee combat vs NPCs and remote players;
zone-delegated open-world multiplayer (server v2: AOI + host throttle + rate-limit
+ sanitization + ghost GC); vehicle driving; configurable controls; 8 map
providers; editable landmarks with JSON import/export (Designer Mode); 6 lore
collectibles with persistent inventory; waypoint navigation with greedy road-graph
routing; 6 ESCOM interiors; full zombie survival minigame (lobby + 7 buildings,
dual combat, 6 power-ups, dynamic lighting, WASTED/Victory screens, damage feedback
FX) with **online mode backed by a dedicated authoritative zombie server**
(`MultiplayerZombie/`: flow-field + LOS + separation AI) and a collision Designer
Mode; a ShineCTO easter-egg interior.

## 8. Not yet implemented

A*-based pathfinding for the **open-world** router (it is a greedy graph walk —
note the zombie server already uses a Dijkstra flow-field); local Bluetooth
multiplayer; content-rich interior collision matrices (interiors use
`CollisionGrid.emptyWithBorder()`); floating damage numbers + hit particles;
lobby door coordinate re-tuning.

## 9. Build / run

- Open in Android Studio; **Build → Rebuild Project**.
- Server URLs injected via Gradle → `BuildConfig.MULTIPLAYER_SERVER_URL`
  (open world) and `BuildConfig.ZOMBIE_SERVER_URL` (zombie minigame).
- Servers (separate, both listen on container `:8080`, `GET /status`, `WS /`):
  - Open world: `cd Multiplayer && docker compose up -d` (host `:8080`).
  - Zombie minigame: `cd MultiplayerZombie && docker compose up -d`
    (host `:8081` → container `:8080`).

## 10. Note for AI agents working in this repo

When the build shows cascading *"Unresolved reference"* errors after a merge,
suspect a **single structurally-broken file** (missing/extra brace, duplicated
`)`), not many independent errors — fixing the broken class usually clears all of
them. Verify brace/parenthesis balance per file (ignoring string/comment contents)
before assuming a symbol is genuinely missing. The two `server.js` files are plain
Node.js (no build step); validate them with `node --check server.js`.
