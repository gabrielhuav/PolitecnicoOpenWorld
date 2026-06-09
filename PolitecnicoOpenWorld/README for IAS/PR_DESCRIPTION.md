# Zombie Apocalypse Mode, multiplayer instancing, interior NPCs & server rename

## Summary

This PR adds a full **global Zombie Apocalypse mode** to the open-world map (distinct from the existing
ESCOM interior mini-game), introduces **multiplayer instancing** so "normal" players are never affected
by the apocalypse, renames the interior mini-game server, and adds **server-authoritative civilian NPCs**
inside interiors. It also fixes several rendering/gameplay bugs found along the way.

All gameplay/AI stays client-side on the **Zone Host** (consistent with the open-world architecture); the
Node servers only relay. The two servers are split **by world / coordinate system**, not by theme:
`Multiplayer/` = open-world map (lat/lon), `MultiplayerInteriores/` = interior rooms (fractional `[0,1]`).

---

## ✨ Features

### 1. Global Zombie Mode (apocalypse on the open-world map)
- New `NpcType.ZOMBIE`, simulated by the Zone Host in `NpcAiManager` and replicated through the existing
  `NPC_BATCH_UPDATE` (carries `npcType`/`health`/`isDying`).
- Zombies chase the nearest human/player off-road (`moveZombieNpc`), bite humans, and **infect** them on
  kill (dead human → new zombie), so the apocalypse spreads. Humans flee using the existing fear system.
- Activation: a fixed **"Mano del Apocalipsis"** hand in ESCOM, a new **Options-menu toggle that works
  anywhere**, and a floating **exit button**.
- Player participates: zombies deal contact damage; melee (B) and run-over kill them; hitting a surviving
  zombie **knocks it back** ~7–8 m.

### 2. Zombie roles (low-RAM palette swaps)
- `ZombieRole { NORMAL, RUNNER, TANK, SCOUT }` — same `z_walk` asset, tinted per role via `ColorMatrix`
  in `MapZombieSpriteManager` (no new sprites). Per-role `maxHealth`, speed and size.
  - **Runner** (reddish): faster, low HP. **Tank** (dark green): slower, tankier, bigger.
  - **Scout** (yellow): does **not** attack — runs to the nearest human, **screams** (📢 bubble) and flees:
    a living alarm that a horde is coming.
- Roles assigned on spawn and on infection; replicated via `NPC_BATCH_UPDATE` (`zombieRole`, `screamUntil`);
  `maxHealth` is derived from role on remote clients (no extra wire field).

### 3. Migratory hordes (dynamic siege)
- Every `HORDE_INTERVAL_MS` (20 s), if a **heat point** exists (≥ `HORDE_MIN_HUMANS` humans within sim
  radius), the Host spawns a **wave** of `HORDE_SIZE` zombies that converge on the cluster (each chases the
  nearest human). Host-computed (it runs NPC AI), replicated like any zombie — works in SP and MP. A HUD
  warning fires via `hordeIncomingAt`.

### 4. Multiplayer instancing — Normal vs Apocalypse
- The open-world server is now **sharded by `ws.instance`** (`"normal"` / `"apocalipsis"`). Clients in one
  instance never see the other's players or NPCs/zombies.
- **All relay & roster are instance-scoped:** `broadcastToOthers`/`broadcastToNearby` (AOI) and a new
  `broadcastToInstance` filter by `instOf(client)`; every NPC is tagged with `instance`; AOI/GC/cap/
  `SYNC_ALL_NPCS`/`MASTER_SYNC_CHECK` and Host election are per-instance.
- New `JOIN_INSTANCE` message moves a player between worlds (sends a clean roster of the new instance).
  **`ZOMBIE_MODE_SET` (the old global flag) was removed** — toggling the apocalypse now just switches
  instance (`WorldMapViewModel.setZombieInstance`, which clears `remoteEntities`).
- Interiors were already instanced by `roomId`, so that separation existed there already.

### 5. Server rename: `MultiplayerZombie/` → `MultiplayerInteriores/`
- It is the **interior mini-game** server, not "the zombie server". Directory, internal docker/package
  names, and `BuildConfig.ZOMBIE_SERVER_URL` → **`INTERIORS_SERVER_URL`** all renamed (Gradle +
  `ZombieGameScreen.kt`). The global apocalypse stays on the **open-world** server.

### 6. Interior civilian NPCs + server-managed coordinates
- `MultiplayerInteriores/server.js` now spawns ~6 **civilians** per building room that **wander and flee**
  from the nearest zombie and **turn into a zombie** if caught (`spawnRoom`, `zombieTick`). They travel in
  `ZOMBIE_STATE.npcs` (`NetInteriorNpc`); the client stores them in `ZombieGameState.interiorNpcs` (reusing
  `RemoteZombiePlayer`) and renders them with the existing `RemotePlayerView` (no new sprites).
- **Server-authoritative player coords:** `PLAYER_UPDATE` is validated against the room collision matrix
  (`isBlocked`); a wall-clipping position is rejected and corrected via a new `PLAYER_CORRECT` message.

### 7. Dynamic NPC population (device tier + free "city" detection)
- NPC counts and car ratio now scale by `popFactor = deviceTierFactor × urbanFactor` in `NpcAiManager`:
  - **`deviceTierFactor`** — phone hardware tier from `ActivityManager` total RAM / `isLowRamDevice`
    (≈0.6 for ≤2 GB, 1.0 ≤4 GB, 1.3 ≤6 GB, 1.5 otherwise), set in `WorldMapViewModel.Factory`. Fewer NPCs
    on weak devices, more on flagships.
  - **`urbanFactor`** — **free city detection** from the **density of the already-downloaded OSM road
    network** (`network.size / 140`, clamped `0.6–1.8`), recomputed in `updateRoadNetwork` as you travel.
    Denser streets ⇒ more NPCs **and more traffic** (`carPopulationRatio`). No paid traffic APIs; can be
    refined later with OSM `landuse`/`place` tags.

### 8. Varied Overtaking Mechanics & Driving Physics
- Improved player driving collision logic with NPC cars to be varied and realistic:
  - **Stopped / Slow (<9% max speed)**: NPC cars smoothly dodge the player by pushing themselves to the side.
  - **Normal to Max speed (straight line)**: The player's car automatically and smoothly dodges NPC cars sideways without leaving the lane (predictive sidestep), with a 1.5s auto-centering magnet to seamlessly return to the center of the road without latching onto forks.
  - **Intentional crashes**: Steering manually during an auto-dodge instantly cancels collision immunity, resulting in a Toretto-style shove with an impact effect (💥).
- **Pedestrian Hitboxes & Crime**: Reduced pedestrian run-over hitbox to 0.4x (harder to hit). Killing a pedestrian with a car now correctly awards a +1 wanted star.
- **Dynamic Acceleration & Reverse**: Acceleration scales inversely with current speed. Reverse steering rotates 50% faster without inverting controls, allowing agile 3-point turns.

---

## 🐛 Bug fixes
- **Zombie sprites not rendering** on OSM-native / Google-native / Web: the seed kept `visualConfig`, so
  the pedestrian render branch (checked before `ZOMBIE`) drew zombies as humans. Fixed by nulling
  `visualConfig` on seed/infection **and** guarding the pedestrian branch with `&& type != ZOMBIE` in all 3
  renderers. Web also required bounding the animation frame in the async base64 `cacheKey` to `% 9`.
- **Zombies dealt no damage:** the active (member) game loop in `WorldMapViewModel` never called
  `applyNpcContactDamage`/`runOverNpcs` (only the shadowed extension did). Added both calls.
- **💥 impact FX spam / on death:** added a throttle to `fireImpactEffect`, and `takeDamage`/
  `applyNpcContactDamage` now ignore damage while dead / on the WASTED screen (no 💥 on the killing blow).
- **Zombie waypoints** (🧟 + red dashed line outside the fog) now also render on **Web (Leaflet
  `updateZombies`)** and **Google native**, not only OSM-native.

---

## 🔌 Wire protocol changes

**Open world (`Multiplayer/server.js`)**
- `+ JOIN_INSTANCE { instance }` (replaces `ZOMBIE_MODE_SET`, removed).
- `MultiplayerNpc` gains `zombieRole`, `screamUntil` (role replication); all relay/roster scoped by instance.

**Interiors (`MultiplayerInteriores/server.js`)**
- `ZOMBIE_STATE` now includes `npcs: NetInteriorNpc[]` (civilians).
- `+ PLAYER_CORRECT { x, y }` (server-side player collision correction).

---

## 🗂️ Main files changed
- **Client:** `domain/models/Npc.kt` (ZombieRole, fields), `domain/models/ai/NpcAiManager.kt` (zombie AI,
  roles, hordes), `features/map_exterior/viewmodel/WorldMapViewModel.kt` (toggle→instance, damage calls,
  horde warning, knockback, 💥 throttle), `WorldMapMultiplayerModels.kt`, `ui/NativeOsmMap.kt`,
  `ui/WorldMapScreen.kt`, `ui/WorldMapLeafletHtml.kt`, `ui/WorldMapDrawingUtils.kt`,
  `ui/components/MapZombieSpriteManager.kt`, zombie-minigame `ZombieGameViewModel/State/Screen` +
  `Zombienetmodels.kt`, `app/build.gradle.kts`.
- **Servers:** `Multiplayer/server.js` (instancing), `MultiplayerInteriores/server.js` (rename + civilians +
  player collision).
- **Docs:** `README for IAS/` (03, 04, 05, 08, 09) updated to match.

---

## ✅ Testing
- Servers: `node --check Multiplayer/server.js` and `node --check MultiplayerInteriores/server.js`.
- Client: **Build → Rebuild Project** in Android Studio.
- Manual: toggle apocalypse (hand / menu / exit button); verify normal-instance players never see zombies;
  zombie roles render with tints; hordes spawn periodically near human clusters; interiors show civilians
  that flee/convert; wall-clipping is corrected.

## ⚠️ Notes / follow-ups
- Hordes are **Host-computed** (the open-world server intentionally does not simulate NPCs). The persistent
  roster (v3) could compute cross-instance heat later if desired.
- Balance constants (role HP/speed, horde size/interval, fear vs zombie speed) are centralized in
  `NpcAiManager` and easy to tune.
