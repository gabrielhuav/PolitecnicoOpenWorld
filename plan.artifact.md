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
| Nested options menu (menu of menus, recursive) | `features/map_exterior/ui/components/OptionsMenu.kt` (`OptionMenuGroup.items: List<OptionEntry>`) |
| Map rendering (OSM/Google/Leaflet WebView) | `features/map_exterior/ui/WorldMapScreen.kt` (Leaflet HTML built in `WorldMapLeafletHtml.kt`) |
| Native osmdroid rendering (NPCs, landmarks, **player-anchored fog overlay**, over-zoom) | `features/map_exterior/ui/NativeOsmMap.kt` |
| Sprite/health-bar drawing helpers (NPC health bar size lives here) | `features/map_exterior/ui/WorldMapDrawingUtils.kt` |
| Player sprite / driven-vehicle rendering + sizing | `features/map_exterior/ui/components/PlayerCharacter.kt` |
| Leaflet tile interception | `features/map_exterior/ui/CachingWebViewClient.kt` |
| NPC population / spawn / movement / adoption (client-side) | `domain/models/ai/NpcAiManager.kt` |
| Wanted level / police AI (spawn, road-snapped chase, disembark, carjack, retreat) | `domain/models/ai/PoliceManager.kt` (driven by `WorldMapViewModel.runPoliceTick`) |
| Patrol sprite (no-repaint `POLICE_TOPDOWN`) | `features/map_exterior/ui/components/PoliceSpriteManager.kt`; cop = 👮 emoji via `emojiToDrawable` in `WorldMapDrawingUtils.kt` |
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
| **Unified offline tile cache (native OSM)** | `data/cache/RoomTileModuleProvider.kt` (osmdroid module → Room, browser UA) |
| **Per-zone tile prefetch (offline, ~2km)** | `data/cache/TilePrefetchManager.kt` + `WorldMapRoadNetwork.kt::prefetchCurrentZoneTiles` |
| **Open-world server (v2)** | `Multiplayer/server.js` |
| **Zombie-minigame server (authoritative)** | `MultiplayerZombie/server.js` |

## 5. Domain concepts an editor must respect

- **Snap-to-road:** the player can't leave the road network; movement is validated
  against a spatial grid index (`Seg` + `HashMap<cell, segments>`), O(nearby).
- **Road cache:** ~2 km cells, 7-day TTL, LRU of 20 cells, **atomic** zone+ways+nodes
  insert (`@Transaction`), 5-min re-fetch cooldown.
- **Tile cache:** per-provider, ~8k tiles max, key = normalized URL hashed with
  SHA-256; **writes are atomic** (count→evict→insert in one `@Transaction`).
  - **Native OSM (osmdroid) now shares this SAME Room cache** via
    `RoomTileModuleProvider` (bucket "osm"): reads Room first, else downloads with
    a *browser* User-Agent and persists. osmdroid's built-in downloader (UA =
    packageName) was being throttled by the public OSM server — that was why the
    native map "didn't load new zones" while the Web providers did. On entering a
    road cell, `TilePrefetchManager` proactively caches the ~2km zone (zooms 16-18)
    so it's fully offline; prefetch is **non-blocking** and warns if incomplete.
  - **Native over-zoom (z20-22):** OSM only serves real tiles up to z19, so
    `NativeOsmMap` adds an osmdroid `MapTileApproximater` and `setMaxZoomLevel(22)`
    to *scale* z19 tiles for the extra levels (otherwise the map went blank above
    z19). The loading screens (`downloadOsmNativeForEntry`, used on entry AND after
    teleport) now download a grid at **z19 (max real) + z17 (medium)** into Room so
    the over-zoom has source tiles. Default OSM gameplay zoom is the **max (22)**.
- **Fog of war is player-anchored, per renderer:** it must follow the player's
  real position during scroll/zoom (not stay screen-centered). Native: a
  `FogOverlay` (`Overlay`) in `NativeOsmMap` projected onto the player each frame,
  with the covering rect oversized to the screen diagonal so map rotation while
  driving leaves no corner gaps. Web: a `#fog` HTML div (inside `#map-wrapper`)
  redrawn on every Leaflet `move`/`zoom`. The Compose `Canvas` fog in
  `WorldMapScreen` is now used **only** for the Google Maps native renderer.
- **NPC/player sizing is real-meter based and unified across renderers:**
  pedestrians ≈ 1.3 m, vehicles ≈ 4.0 m (slightly larger-than-real for
  visibility). Native sizes live in `NativeOsmMap`, web in `WorldMapLeafletHtml`
  (`updateNpcs`, computed from pixels-per-meter), and the player's own on-foot/
  driving sprite in `PlayerCharacter` — keep all three in sync when retuning.
- **NPCs (open world, client-side):** up to 40 around the player; pedestrians vs 6
  car models; per-pixel tinting; proximity spawn / distance despawn; **adoption**
  snaps server-inherited NPCs to the nearest way. Spawn uses a **per-way bbox
  pre-filter** before the per-node distance check. The open-world server does NOT
  simulate NPCs — the Zone Host runs the AI on the client and the server relays.
- **NPC reactions (GTA-lite, cheap):** each NPC gets a `trait` (`AGGRESSIVE`/`COWARD`)
  rolled at spawn via `NpcAiManager.rollTrait()`. The aggressive fraction is **configurable**
  via `NpcAiManager.aggressiveRatio` (default `0.3`); the rest are cowards.
  - *Provoke-only:* NPCs never attack unprovoked. Hitting one (or carjacking it) makes
    AGGRESSIVE ones retaliate; COWARDS flee (`fearUntil`). AGGRESSIVE NPCs are **immune to
    fear** (skipped in `applyPendingFear`) — "no les da miedo".
  - *Retaliation* (`WorldMapViewModel.performPlayerAttack`): a hit AGGRESSIVE survivor gets
    `aggroUntil` (chases via `NpcAiManager.moveAggroNpc`, ignoring the road graph) **and** a
    DETERMINISTIC counter-hit after ~450 ms if you're within `ATTACK_RADIUS` (the chase/
    contact-loop detection alone proved unreliable, so the counter is guaranteed).
  - *Relentless:* `npcHitStreak` counts consecutive hits per NPC; at `RELENTLESS_HIT_STREAK`
    (6) the NPC becomes implacable (`relentlessNpcs` + `startRelentlessAttacker`): refreshes
    its aggro and hits you every `NPC_CONTACT_COOLDOWN_MS` until you die or it dies.
  - *Run-over* (`runOverNpcs`): while driving, pedestrians within `RUN_OVER_RADIUS` take
    speed-scaled damage; deaths broadcast `NPC_DESTROY`, witnesses `triggerFear`.
  - *Contact damage* (`applyNpcContactDamage`): NOT host-gated — each client damages **its
    own** player from nearby aggro NPCs.
  - **Two-way traffic:** spawn direction randomized + right-side `LANE_OFFSET`.
  - **Death/respawn** (`triggerWastedSequence`): clears combat state and respawns ~80 m from
    the death spot **inside the already-cached zone** (snapped to road via
    `getNearestPointOnNetwork`) — no ESCOM teleport / new tile download. Movement & vehicle
    freeze during WASTED; the avatar fades to a ghost (alpha 0.3).
  - **Multiplayer:** `MultiplayerNpc` carries `health` + `isDying` + `aggroUntil`; the Host
    simulates and `Multiplayer/server.js` relays them via `{...npc}` spread (clamps health,
    v3.1). Each client applies contact/counter damage to its own player; player HP rides in
    `PLAYER_UPDATE`.
  - **Feedback:** persistent HUD health bar (top-left, zombie-style), a red damage-flash
    vignette on each hit (driven by `damagePulseTrigger`), the low-HP red aura
    (`LowHealthAura`, <35 HP), and a "💥" burst (`impactEffectTrigger`).
  - **Size parity:** native NPC sprite sizing uses `uiState.zoomLevel` (NOT live
    `view.zoomLevelDouble`) so NPCs match the player/vehicle scale.
  - **No melee through a car:** while `isDriving`, NPC contact/counter damage is skipped;
    aggressive NPCs adjacent to your car can instead trigger a carjack (see Police).
- **Wanted level / Police (GTA-style, `domain/models/ai/PoliceManager.kt`):** punching a
  civilian raises `WorldMapState.wantedLevel` (0–5, HUD stars; `raiseWantedLevel`), decays
  after a no-crime grace and clears on death. Unlike civilian NPCs (simulated by the Zone
  Host), **the wanted player owns and simulates their own police** (`runPoliceTick`, every
  game-loop tick) because the police must chase *that* player. Police live in `PoliceManager`
  (NOT in `remoteEntities`, so the NPC pipeline never touches them) and are merged into
  `uiState.npcs` by `updateNpcsState` (own `policeManager.activeUnits()` + `remotePolice`).
  - **Types:** `NpcType.POLICE_CAR` (renders via `PoliceSpriteManager`, no-repaint
    `VEHICLES/POLICE_TOPDOWN`, 48 frames) and `NpcType.POLICE_COP` (no person asset → 👮
    `emojiToDrawable`). Extra `Npc` fields: `policeDisembarked`, `policeCanShoot`,
    `policeCarId`, `policeReturning`.
  - **Spawn/scale:** patrols spawn far (`SPAWN_RING ≈ 265 m`, snapped to a car road node),
    count scales with stars (`desiredCarsFor`: 1★→1 … 5★→8).
  - **Roads respected:** every patrol/cop step is snapped to the network via the `snap`
    lambda (`getNearestPointOnNetwork`) so they don't cut through buildings; rotation follows
    travel direction (same convention as civilian car sprites — NO +270 offset).
  - **On foot:** patrol stops at `ARRIVE_DIST` and drops 2–3 cops that chase/punch and, at
    `policeCanShoot` (2★+), shoot within `SHOOT_RANGE`.
  - **In a car:** cops re-board (`policeReturning` → run to `policeCarId`, board at
    `BOARD_DIST`) and chase by patrol; a patrol that reaches your car (`ADJACENT_DIST`) drops
    cops who, while you're stopped, trigger a **carjack** (`PoliceTick.adjacentThreat` →
    `handleCarjack` → HUD `carjackWarning` → `forceExitVehicle` after `CARJACK_MS`). No direct
    damage while driving.
  - **Retreat:** at `wantedLevel == 0` (incl. on death) police are NOT deleted instantly —
    they flee away until `RETREAT_DESPAWN`, emitting `POLICE_DESTROY`.
  - **Waypoint:** while a `POLICE_CAR` is OUTSIDE the fog, `NativeOsmMap` draws a 🚓 marker
    **plus a route line** from the player (remembered caches, NOT view tags — adding new
    `R.id`s shifted the fragile `id+100`/`id+400` derived-tag hack and caused a
    `Polyline→Marker` `ClassCastException`); inside the fog the real patrol asset draws.
  - **Multiplayer:** broadcasts `POLICE_BATCH_UPDATE` (throttled ~8 Hz) and `POLICE_DESTROY`;
    `server.js` (v3.2) relays both (AOI) WITHOUT adding them to the persistent roster; remote
    clients store them in `remotePolice` and purge by staleness (5 s).
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
  shrink it back. The `#fog` overlay also lives inside `#map-wrapper` (so it
  rotates with the map); container-point coordinates from `latLngToContainerPoint`
  align with it.
- The nested **Options menu** is height-capped (~68% screen) and scrollable so it
  doesn't overflow / collide with controls in landscape; the right-side game
  control slides left while it's open. `OptionsMenu` entries may be groups *or*
  items at any depth ("menu of menus"); the "Centrar en jugador" entry becomes a
  group with "Hacer zoom en el jugador" when the user has zoomed off the default.
- With the map off-center (`isUserPanningMap`), the left movement controls
  (`moveCharacter`/`moveCharacterByAngle`) **recenter on the player** (no zoom
  change) on first tap instead of moving blindly off-screen.
- Room DB is currently `@Database` v8 with `MIGRATION_7_8` + destructive fallback.
- Persisted prefs go through `SettingsRepository` (SharedPreferences), not Room.
- The **client and both servers must agree on collision matrices**: the Designer
  Mode exports `collision_matrices.json` in the exact shape the zombie server
  reads; default matrices are border-only and must match rows/cols on both sides
  until replaced.

## 7. Current state (what works)

OSM/Google/Web navigation with snap-to-road; persistent street + tile caching;
procedural NPCs (pedestrians + 6 cars) with **personality traits, run-over-while-driving,
aggressive retaliation/carjack reactions, and two-way traffic**; **GTA-style wanted level / police
(0–5 stars, road-snapped patrol pursuit, 👮 cops that punch & shoot, vehicle chases + carjack, retreat
on death; multiplayer-replicated)**; melee combat vs NPCs and remote players;
zone-delegated open-world multiplayer (server v2: AOI + host throttle + rate-limit
+ sanitization + ghost GC); vehicle driving; configurable controls; 8 map
providers; editable landmarks with JSON import/export (Designer Mode); 6 lore
collectibles with persistent inventory; waypoint navigation with greedy road-graph
routing; 6 ESCOM interiors; native OSM now offline-unified with the Web tile cache + per-zone prefetch,
**over-zoom to z22 (scaled from z19) with loading-screen z19/z17 prefetch, default max zoom**;
**player-anchored fog-of-war on native + web (driving-rotation safe)**; **real-meter NPC/player
sizing unified across renderers**; **landscape-safe scrollable Options menu**; main-menu version
bound to `BuildConfig.VERSION_NAME` with auto-shrinking title; full zombie survival minigame (lobby + 7 buildings,
dual combat, 6 power-ups, dynamic lighting, WASTED/Victory screens, damage feedback
FX) with **online mode backed by a dedicated authoritative zombie server**
(`MultiplayerZombie/`: flow-field + LOS + separation AI) and a collision Designer
Mode; a ShineCTO easter-egg interior.

## 8. Not yet implemented

A*-based pathfinding for the **open-world** router (it is a greedy graph walk —
note the zombie server already uses a Dijkstra flow-field); local Bluetooth
multiplayer; content-rich interior collision matrices (interiors use
`CollisionGrid.emptyWithBorder()`); floating damage numbers + hit particles;
lobby door coordinate re-tuning; **car-vs-car collisions** (planned: cull-radius-only
circle-overlap + speed reduction, no physics engine).

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

## 11. Update protocol — KEEP THESE TWO FILES CURRENT (read first, write last)

This file (`plan.artifact.md`) and `README.md` are the **single source of truth**
passed to any assistant instead of the whole codebase. They only stay useful if
they are updated **in the same change** that touches the code. Treat them as part
of the deliverable, not as documentation written afterwards.

### 11.1 Workflow for implementing a feature or fix (no full context needed)

1. **Load context:** read `plan.artifact.md` (this file) + `README.md`. That is the
   complete mental model — do not ask for the rest of the repo unless a step below
   says a referenced file is missing.
2. **Locate:** use the "Key files" table (§4) to jump straight to the file(s) the
   task concerns. If the table lacks an entry for the area you touch, that is a
   signal the map is stale — add the entry as part of this change.
3. **Respect the contracts:** MVVM split (§3), immutable state copies, staged
   controls, snap-to-road, atomic caches, collision-matrix agreement client⇄server,
   Spanish comments (§6). Do not violate a gotcha in §6 without noting why.
4. **Implement** the smallest change that satisfies the request.
5. **Verify:** Kotlin → Android Studio *Rebuild Project*; servers →
   `node --check server.js`. Check brace/paren balance per file (§10).
6. **UPDATE THE DOCS (mandatory):** apply the checklist in §11.2 before considering
   the task done.

### 11.2 Doc-update checklist (run on EVERY change that alters behavior)

Ask: *would an assistant reading only these two files now be wrong or surprised?*
If yes, update both files. Specifically:

| If you changed… | Update in this file | Update in README.md |
|---|---|---|
| A new/renamed file or feature module | §2 repo shape, §4 Key files | Architecture tree + relevant section |
| Behavior of NPCs, routing, caches, combat, zombies | §5 domain concepts | The matching feature section (EN **and** ES) |
| A convention, scoping rule, or gotcha | §6 conventions | — (or relevant section if user-visible) |
| Something now works that didn't | §7 current state | "Current Status" → "Works today" / "Funciona hoy" |
| You implemented an item from "Not yet implemented" | §8 (remove it) | "Not yet implemented" / "No implementado aún" (remove it) |
| New build step, env var, server, port | §9 build/run | "Tech Stack" / "Deploying" sections |
| Room schema (new entity/migration) | §6 (DB version) | Persistence row of Tech Stack |
| Wire protocol / message types | §5 multiplayer | "Real-Time Multiplayer" (EN + ES) |

Rules of thumb:
- **README.md is bilingual (EN + ES).** Every user-facing change must be reflected
  in **both** language sections — they must not drift apart.
- Keep the "Recent Changes / Cambios Recientes" block in README as a short rolling
  log: add the new change at the top, prune entries older than ~2-3 releases.
- Keep §7 (works) and §8 (not yet) here mutually exclusive — moving a line from §8
  to §7 is usually the only edit needed when a feature lands.
- If a fact appears in both files, change it in both. A contradiction between
  `README.md` and `plan.artifact.md` is a bug.
- Prefer editing existing lines over appending; these files must stay
  minimum-but-complete, not grow unbounded.

### 11.3 Definition of done

A change is complete only when: code compiles/validates, **and** both context files
describe the new reality, **and** §7/§8 here plus README "Current Status" agree. If
you cannot update the docs, the task is not finished — say so explicitly.

### 11.4 One-line prompt to reuse this workflow

> "Read `plan.artifact.md` + `README.md` (that's the full context). Implement
> <task> following the MVVM / §6 conventions, then update both files per §11.2 so
> they stay the single source of truth. Don't ask me for more of the repo unless a
> file referenced in §4 is missing."
