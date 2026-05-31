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
- **Server:** standalone **Node.js + ws** process (dockerized, hosted on Render),
  injected at build time via `BuildConfig.MULTIPLAYER_SERVER_URL`.
- **Min package root:** `ovh.gabrielhuav.pow`.

## 2. Repo shape

```
PolitecnicoOpenWorld/            # Android client (this repo)
├── app/src/main/java/ovh/gabrielhuav/pow/
│   ├── data/          # Room DB, caches, network, repositories
│   ├── domain/        # pure Kotlin models + AI (no Android deps)
│   ├── features/      # feature modules: <name>/ui + <name>/viewmodel
│   ├── ui/theme/      # Material 3 theme
│   └── MainActivity.kt# single Activity + Compose NavHost
└── Multiplayer/       # Node.js game server (separate; not always in this checkout)
```

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
| Open-world game loop, multiplayer, NPCs, ESCOM logic | `features/map_exterior/viewmodel/WorldMapViewModel.kt` |
| Open-world UI state | `features/map_exterior/viewmodel/WorldMapState.kt` |
| Map rendering (OSM/Google/Leaflet WebView) | `features/map_exterior/ui/WorldMapScreen.kt` (Leaflet HTML built in `buildHtml(...)`) |
| Leaflet tile interception | `features/map_exterior/ui/CachingWebViewClient.kt` |
| NPC population / spawn / movement / adoption | `domain/models/ai/NpcAiManager.kt` |
| Zombie minigame logic | `features/zombie_minigame/viewmodel/ZombieGameViewModel.kt` |
| Zombie minigame state | `features/zombie_minigame/viewmodel/ZombieGameState.kt` |
| Zombie rendering + camera + damage FX | `features/zombie_minigame/ui/ZombieGameScreen.kt` |
| Zombie rooms / doors / collision | `domain/models/zombie/ZombieModels.kt`, `ZombieRoomCatalog.kt` |
| Settings (map/controls/gameplay/interface) | `features/settings/{ui,viewmodel}/...` |
| Settings persistence | `data/repository/SettingsRepository.kt` (SharedPreferences) |
| Tile cache (Room) | `data/cache/TileCache.kt` + `data/local/room/dao/MapTileDao.kt` |
| Road-network cache (Room) | `data/cache/RoadNetworkCache.kt` + `RoadNetworkDao.kt` |
| Multiplayer warm-up (Render) | `features/main_menu/ui/ServerWarmupManager.kt` (package `data.network`) |

## 5. Domain concepts an editor must respect

- **Snap-to-road:** the player can't leave the road network; movement is validated
  against a spatial grid index (`Seg` + `HashMap<cell, segments>`), O(nearby).
- **Road cache:** ~2 km cells, 7-day TTL, LRU of 20 cells, **atomic** zone+ways+nodes
  insert (`@Transaction`), 5-min re-fetch cooldown.
- **Tile cache:** per-provider, ~8k tiles max, key = normalized URL hashed with
  SHA-256; **writes are atomic** (count→evict→insert in one `@Transaction`).
- **NPCs:** up to 40 around the player; pedestrians vs 6 car models; per-pixel
  tinting; proximity spawn / distance despawn; **adoption** snaps server-inherited
  NPCs to the nearest way. Spawn now uses a **per-way bbox pre-filter** before the
  per-node distance check.
- **Zone-Host multiplayer authority:** each client is Host within ~400 m; lower
  `sessionId` wins on overlap; only the Host runs NPC AI and emits
  `NPC_BATCH_UPDATE`; orphaned NPCs are adopted, not destroyed. Colors serialized
  as ARGB Int (not Compose `Color` ULong).
- **Zombie minigame:** ring of 7 rooms (lobby + 6 ESCOM buildings); camera uses
  `max(viewW/worldW, viewH/worldH)` (ContentScale.Crop equivalent); dual combat
  (MELEE/RANGED via Y-hold menu, B to attack); 6 SkillEffect drops rendered as pure
  Canvas icons; collision via fractional `collisionMatrix` (`isBlockedFrac`, O(1)).

## 6. Conventions / gotchas

- State updates are always immutable copies; never mutate Compose state directly.
- Settings **controls** are staged in `temp*` fields and only committed on SAVE
  (`saveControlsSettings()`); `discardControlsChanges()` on exit. Do not wire
  control edits straight into committed state.
- Comments/strings in this codebase are predominantly Spanish; keep that style for
  consistency unless asked otherwise.
- The Leaflet map lives inside a WebView; the `#map-wrapper` is intentionally
  oversized (`300vw × 300vh`, centered) so rotation never reveals gaps. Don't
  shrink it back.
- Room DB is currently `@Database` v8 with `MIGRATION_7_8` + destructive fallback.
- Persisted prefs go through `SettingsRepository` (SharedPreferences), not Room.

## 7. Current state (what works)

OSM/Google/Web navigation with snap-to-road; persistent street + tile caching;
procedural NPCs (pedestrians + 6 cars); melee combat vs NPCs and remote players;
zone-delegated multiplayer; vehicle driving; configurable controls; 8 map
providers; editable landmarks with JSON import/export (Designer Mode); 6 lore
collectibles with persistent inventory; waypoint navigation with greedy road-graph
routing; 6 ESCOM interiors; full zombie survival minigame (7 rooms, dual combat,
6 power-ups, dynamic lighting, WASTED/Victory screens, damage feedback FX); a
ShineCTO easter-egg interior.

## 8. Not yet implemented

A*-based pathfinding (router is a greedy graph walk); local Bluetooth multiplayer;
content-rich interior collision matrices (interiors use
`CollisionGrid.emptyWithBorder()`); the zombie-minigame dedicated multiplayer
server (PR #59 server/warm-up portion); floating damage numbers + hit particles;
lobby door coordinate re-tuning (PR #60).

## 9. Build / run

- Open in Android Studio; **Build → Rebuild Project**.
- Server URL injected via Gradle → `BuildConfig.MULTIPLAYER_SERVER_URL`
  (and `ZOMBIE_SERVER_URL` for the zombie minigame, when applicable).
- Multiplayer server (separate): `cd Multiplayer && docker compose up -d`
  (listens on `:8080`, `GET /status`, `WS /`).

## 10. Note for AI agents working in this repo

When the build shows cascading *"Unresolved reference"* errors after a merge,
suspect a **single structurally-broken file** (missing/extra brace, duplicated
`)`), not many independent errors — fixing the broken class usually clears all of
them. Verify brace/parenthesis balance per file (ignoring string/comment contents)
before assuming a symbol is genuinely missing.
