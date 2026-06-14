# PR: `interiores` umbrella + always-spawn-at-ESCOM + server mode + asset naming

> Content ready to be used as the Pull Request description on GitHub.
> Three changes: (1) the interior features are merged under a single **`features/interiores/`**
> umbrella with a **core / escom / zombies / shinecto** split (the zombie layer is now
> separated from the interiors, and the structure is ready to host **more universities**);
> (2) the player now **always spawns at the ESCOM teleport point**; (3) interior **assets are
> renamed to English/snake_case**, and the interiors client now sends an explicit **`mode`**
> field so the servers can tell global map / interiors / zombie mode apart.

---

## 1. `interiores` umbrella restructure

`features/zombie_minigame/`, `features/interior/` and `features/shinecto/` were merged under
**`features/interiores/`** with four sub-packages:

- **`core/`** — shared, university-agnostic building blocks, extracted so the other interiors no
  longer depend on the zombie package:
  - `core/viewmodel/InteriorDesignerModels.kt` → `DesignerTarget`, `CameraTransform`
    (moved out of `ZombieGameState.kt`).
  - `core/ui/InteriorPlayerViews.kt` → `PlayerView`, `PlayerHealthBarFixed`, `RemotePlayerView`
    + the private `PlayerSkin.playerViewPath` (moved out of `ZombieHud.kt`).
  - `core/ui/CollisionMatrixDesignerLayer.kt`, `WaypointDesignerLayer.kt` (the shared designer layers).
- **`escom/`** — the simple 2D ESCOM interiors + metro (was `features/interior/`). This is the
  per-university package; **adding another university = a new `interiores/<uni>/` reusing `core`**.
- **`zombies/`** — the zombie survival layer (was `features/zombie_minigame/`).
- **`shinecto/`** — the ShineCTO easter-egg interior (was `features/shinecto/`).

`CollisionMatrixRepository.kt` (its package was already `data.repository`) was physically moved to
`data/repository/` to match. The nav route **`zombie_minigame` → `interiores_zombies`**. All package
declarations, imports and `MainActivity` nav were updated; the old empty feature folders were removed.

## 2. Always spawn at ESCOM

`MainActivity` now **always** starts the player at the **ESCOM teleport point**
(`SPAWN_ESCOM_LAT = 19.504603`, `SPAWN_ESCOM_LON = -99.145985`, i.e. `TeleportCatalog.zones[0]`),
no longer the device GPS. `fetchCurrentLocation()` and its fallback just call
`updateInitialLocation(SPAWN_ESCOM_LAT, SPAWN_ESCOM_LON)`. To restore GPS spawning, re-enable the
`getCurrentLocation(HIGH_ACCURACY)` read in `fetchCurrentLocation()`.

## 3. Server mode detection (global / interiors / zombies)

So the back end can tell the three modes apart:

- **Open world** already shards by instance via `JOIN_INSTANCE` → `"normal"` (global map) vs
  `"apocalipsis"` (global zombie mode).
- **Interiors** client (`ZombieGameViewModel`) now sends an explicit **`mode`** field in
  `JOIN_ROOM` **and** `PLAYER_UPDATE`: `"interiores"` (calm lobby) or `"zombies"` (building horde,
  or lobby with the apocalypse on), computed by `currentNetMode()`
  (`room.type == BUILDING || zombieModeActivated`).

> ⚠️ The two `server.js` files (`Multiplayer/`, `MultiplayerInteriores/`) are **sibling repos, not
> in this checkout**, so they're not modified here. The exact server-side spec (read `instance` +
> `mode` to route/scope players) is documented in `README for IAS/08_SERVERS.md`.

## 4. Asset naming → English/snake_case

Renamed interior/character asset folders & files (typos + Spanish → English) and **every string
reference** in code and the JSON catalogs (`buildings_catalog.json`, `default_landmarks.json`):

`PRINCIPAL→MAIN`, `INTERIORES→INTERIORS`, `LUGARES→PLACES`, `ZOMBIS_MOD→ZOMBIES_MOD`,
`coleccionables→collectibles`, `metroCDMX→metro_cdmx`, `assetsNPC/cabello→assetsNPC/hair`,
`assetsNPC/otherPlayer→assetsNPC/other_player` (incl. the `bodyFolder = "other_player"` literal),
`Prankedy/p_atack→p_attack` (typo), `shineCTO→shine_cto`, `deportivomiguelaleman→deportivo_miguel_aleman`,
`plazalindavista→plaza_lindavista`, `ZOMBIES_MOD/interiores→interiors`, `zombi_hand→zombie_hand`,
`Carga_Mod_Zombi→load_zombie_mod`, `metro_cdmx/mapa.png→map.png`.

All 43 static asset-path literals + the dynamic (`"$folder/…"`) paths were verified to resolve to
existing files.

**Deferred on purpose** (runtime-only failure mode; needs the app to verify the art loads): the
camelCase skin sub-folders (`MAIN/escomgirlIdle`, `MAIN/lazaroWalk`…, built in `PlayerSkin` from
`"${skinFolder}Idle"`) and the ~500 already-English vehicle/police frame files
(`White_*_CLEAN_All_###`). Tracked in `09_CONVENTIONS_GOTCHAS.md`.

---

## 5. Key changes (old → new)

| Area | Old | New |
|---|---|---|
| **Interior packages** | `features/{zombie_minigame, interior, shinecto}/` (zombies coupled to interiors/metro) | One **`features/interiores/`** umbrella: `core` / `escom` / `zombies` / `shinecto`; metro & shinecto depend on `core`, not zombies |
| **Shared types** | `DesignerTarget`/`CameraTransform` in `ZombieGameState`; `PlayerView*` in `ZombieHud` | Extracted to `interiores/core/{viewmodel,ui}` |
| **Nav route** | `"zombie_minigame"` | `"interiores_zombies"` |
| **Spawn** | Device GPS (fallback ESCOM) | **Always** ESCOM teleport point |
| **Server mode** | Implicit (roomId / instance only) | Explicit `mode` field on interiors `JOIN_ROOM`/`PLAYER_UPDATE`; open-world `instance` unchanged |
| **Assets** | Spanish + typos (`PRINCIPAL`, `LUGARES`, `p_atack`, `coleccionables`…) | English/snake_case across code + JSON |

---

## 📁 Touched files
**New:** `features/interiores/core/viewmodel/InteriorDesignerModels.kt`,
`features/interiores/core/ui/InteriorPlayerViews.kt`.
**Moved (with package/import rewrites):** all of `features/interiores/{core,escom,zombies,shinecto}/**`
(from the three old feature folders); `data/repository/CollisionMatrixRepository.kt`.
**Modified:** `MainActivity.kt` (spawn + nav + imports), `features/interiores/zombies/viewmodel/ZombieGameViewModel.kt`
(`mode` field + `currentNetMode()`), `features/interiores/zombies/{viewmodel/ZombieGameState.kt, ui/ZombieHud.kt, ui/ZombieGameScreen.kt}`
(shared-type imports), `features/map_exterior/{viewmodel/WorldMapViewModel.kt, ui/WorldMapScreen.kt}`
(route + assets), `features/interiores/escom/**` and many others (asset-path strings),
`app/src/main/assets/**` (renames), `assets/{buildings_catalog,default_landmarks}.json`.
**Docs:** `README.md` (EN+ES), `plan.artifact.md`, `README for IAS/{01,05,06,07,08,09}.md`.

---

## ✅ How to test
1. **Rebuild Project** in Android Studio (no compile here).
2. Launch → you **spawn at ESCOM** regardless of device GPS.
3. Enter an ESCOM door / the interiors flow → it navigates to the `interiores_zombies` route and the
   zombie layer works (lobby + buildings, offline & online).
4. Metro and **ShineCTO** still render (they now use the shared `core` player views).
5. Sprites/backgrounds load with the renamed assets (player skins, NPCs, collectibles, metro, ESCOM
   interiors, zombie buildings, Prankedy `p_attack`).
6. Online: the interiors client sends `mode` in `JOIN_ROOM`/`PLAYER_UPDATE` (apply the server-side
   handling from `08_SERVERS.md` in the `Multiplayer*` repos).

> ⚠️ Validate with **Rebuild** in Android Studio. The `server.js` changes live in the sibling
> `Multiplayer/` and `MultiplayerInteriores/` repos (not in this checkout).
