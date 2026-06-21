# Class-size separation, low-end perf micro-opts & start of WorldMapViewModel twin de-dup

## Summary
A token-/performance-focused refactor pass for **Politécnico Open World**: splitting oversized classes
(>1000 lines) into cohesive same-package partials, applying zero-behavior rendering micro-optimizations
for low-end devices, and starting the compiler-gated de-duplication of `WorldMapViewModel` member/extension
twins. All changes follow the strict MVVM-per-feature conventions (immutable state via
`_state.update { it.copy(...) }`, Views only observe and emit intents). **Not compiled in this
environment — ready for "Rebuild Project".** New files were created with LF line endings (normalize in
Android Studio: *Edit → Convert line separators → CRLF*, or leave LF — it compiles either way).

## Class separation (smaller files, fewer tokens)
- **`NpcAiManager.kt` ~1419 → ~882 (<1000).** Extracted the two big street/campus movers `moveNpc`
  (~382) and `moveLocalNpc` (~159) into a new **`domain/models/ai/NpcAiManagerTraffic.kt`** as
  `internal fun NpcAiManager.X` extensions. ~15 members that they touch were flipped `private → internal`
  (`cachedNavLandmarks`, `nodeToWays`, `exteriorCollisions`, `parkedTimers`/`parkingCooldowns`/
  `carExitCooldowns`/`landmarkEntranceCooldowns`, `carSpeed`, `PARKING_WAKE_*`, the five `TRAFFIC_AVOID_*`,
  `isNativeWayOverlappingCustom`); companion consts qualified (`NpcAiManager.LANE_OFFSET`,
  `NpcAiManager.FEAR_SPEED_MULT`). The duplicate members were removed (no twin left).
- **`WorldMapScreen.kt` ~2265 → ~1325 (<1500).** The giant `when (mapProvider)` render block now delegates
  all three branches:
  - **Google Maps native** branch → new **`ui/WorldMapScreenGoogle.kt`** (`GoogleMapLayer`, 7 params).
  - **Web / Leaflet-in-WebView** branch → new **`ui/WorldMapScreenWeb.kt`** (`WebMapLayer`, 23 params:
    `cachingClient`, `webViewRef`, `gson`, `coroutineScope`, the base64/size caches, and the per-frame
    guard holders `lastWeb*`/`webLmTick`/`webMetroTick` — all mutable `Array`/`IntArray`/`BooleanArray`
    so the anti-re-send guard state stays alive across frames).
  - OSM branch already delegated to `NativeOsmMap`.
  Both new composables are top-level (no member/extension gotcha). LRU caches and guard holders are passed
  by parameter, never recreated.

## Low-end rendering micro-optimizations (zero behavior change)
- **`NativeOsmMapFog.kt`:** the fog `RadialGradient` re-allocated an `IntArray` + `FloatArray` on **every
  `draw()` (~30 Hz)**. Both are now reused as fields (`fogColors`, `fogStops`); `RadialGradient` copies
  their contents on construction, so mutating `fogStops[1]` per frame is safe. (The `RadialGradient`
  itself must still be recreated each frame — its center is the player's moving pixel.)
- **`NativeOsmMap.kt` update lambda (~30 Hz):** density is read once per frame into `screenDensity`; four
  sites re-fetched `context.resources.displayMetrics.density` inside the loop (police/zombie/objective
  waypoints + the metrobus marker, the latter with a `val screenDensity` that **shadowed** the cached
  one). All now use the cached value.
- **`NpcAiManager` per-tick allocation removed:** the three spawn checks (zombie seed / horde / police)
  each rebuilt the full ways list via `cachedWayBoxes.get().map { it.way }`. It's now precomputed once when
  the network is set (`cachedWaysFiltered` in `updateRoadNetwork`, derived from the same `boxes`); the three
  sites just read the field. Identical content (ways with non-empty nodes), zero behavior change.
- **Already well-tuned (left as-is):** the native OSM renderer allocates no `Paint`/`Path`/`Rect` per frame
  (cached Markers/Overlays + `nativeDrawableCache` LRU); `PlayerCharacter` uses `remember` + bitmap caches
  and `Color(0x…)` is a value class (no allocation). Prior passes already tuned the big levers (NPC caps via
  `popFactor`, emoji LOD, per-renderer fog, web re-send guards).
- **Rejected as unsafe-without-compiler (documented in 09 §6):** converting the web JS to template
  literals (that JS lives inside Kotlin `"""…"""`, where `${…}` is captured by Kotlin interpolation →
  would break); reference-caching the time-predicate NPC filters (talk/scream/call — would leave stale
  markers); moving `configureOsmdroid` I/O off the main thread (changes startup ordering).

## WorldMapViewModel twin de-dup (started — compiler-in-the-loop, one pair per cycle)
Many large `WorldMapViewModel` members are duplicated as **dead extensions** in the `WorldMap*.kt`
partials; the **member wins** for in-class calls, so the extension is dead and may have **diverged**.
De-dup = make the extension reproduce the member exactly, then delete the member. Done this pass:
- **`startHealthBarTimer`** — member and extension (`WorldMapMisc.kt`) were **identical** → member deleted.
- **`applyRoadNetwork`** — they had **diverged**: the member also built the police A* graph
  (`buildRoadGraph`) and painted roads with the **snapped** location; the dead extension omitted
  `buildRoadGraph` (would have broken police pathfinding) and prefetched tiles the member didn't.
  The extension (`WorldMapRoadNetwork.kt`) was **synced to reproduce the member exactly**, then the member
  was deleted. `WorldMapViewModel.kt` ~2503 → ~2467.

## UX — small-screen settings (landscape)
- **`SettingsScreen.kt` now has a compact landscape mode.** On short screens (`screenHeightDp <= 380`) in
  landscape the fixed-size chrome looked cramped/cut. A `compactLand` flag shrinks the top-bar padding +
  title, the landscape outer/content paddings, the category-title font + spacer, the "VOLVER" button
  height/font, and the sidebar `CategoryItem` paddings/icon/font. Portrait and large-landscape are
  **unchanged** (every value is `if (compactLand) … else <original>`, `CategoryItem.compact` defaults to
  false, and non-compact font is `TextUnit.Unspecified` = the previous default). Content is already
  scrollable, so this just decompresses the layout.

## Settings — one-tap "Optimize for my device" preset
- New button in **Settings → Gameplay** (`SettingsScreen.GameplaySettings`, `onOptimizeForDevice` callback
  wired in `MainActivity`) that applies the lightest low-end settings in one tap: NPC density to the minimum
  (`NPC_DENSITY_MIN`) + both emoji toggles ON. Persists in settings **and** applies live to the map; shows a
  confirmation toast. New strings `settings_optimize_device` / `_desc` / `_applied` in ES + EN.

## Project structure — `domain/models/map/` package
- The 15 loose files that sat directly under `domain/models/` were moved (via **Android Studio Refactor →
  Move**, ~308 references updated atomically) into the **`domain.models.map`** package: `Npc`/`NpcType`/
  `CarModel`, `MapWay`/`MapNode`, `Landmark`, `MetroStation`/`MetrobusStation`, `ActiveCollectible`,
  `ExteriorCollisionsConfig`/`CollisionWall`/`CollisionPolygon`, `EscomBuildings`/`InteriorBuilding`,
  `CharacterVisualConfig`, `TeleportCatalog`, `ShineCTOLocation`, `InteriorEntryCatalog`,
  `Landmarkassetcatalog`. Subpackages `ai/`, `campaign/`, `zombie/` unchanged.
- **Gotcha:** AS's Move skips files that are open/unsaved in the editor — `WorldMapState.kt` was open, so its
  imports/FQNs were left stale and broke `MainActivity` (`station.name` unresolved). Fixed by hand
  (its moved-type refs → `.map`, keeping `ai.`/`campaign.` refs intact).

## Docs
- `README for IAS/09_CONVENTIONS_GOTCHAS.md` §0 (split progress, 6th/7th/8th passes), §6 (perf micro-opts +
  rejected list), §12 (twin de-dup process + remaining pairs); `04_MAP_EXTERIOR.md` (Key files:
  `NpcAiManagerTraffic.kt`, `WorldMapScreenGoogle.kt`, `WorldMapScreenWeb.kt`); `plan.artifact.md`;
  `PROMPT_nueva_sesion.md`.

## Notable files
`domain/models/ai/NpcAiManager.kt`, `domain/models/ai/NpcAiManagerTraffic.kt` (new),
`features/map_exterior/ui/NativeOsmMapFog.kt`, `features/map_exterior/ui/NativeOsmMap.kt`,
`features/map_exterior/ui/WorldMapScreen.kt`, `features/map_exterior/ui/WorldMapScreenGoogle.kt` (new),
`features/map_exterior/ui/WorldMapScreenWeb.kt` (new),
`features/map_exterior/viewmodel/WorldMapViewModel.kt`,
`features/map_exterior/viewmodel/WorldMapRoadNetwork.kt`,
`features/map_exterior/viewmodel/WorldMapMisc.kt`, plus `README for IAS` docs.

## Notes / test focus
- **Member-vs-extension gotcha:** the live implementations are the members in `WorldMapViewModel.kt`; the
  extensions in the partials were the dead twins. Two of those twins were de-duped this pass — **test the
  affected behavior in-app:** police chase/shoot and A* pathing, and that yellow road lines appear right
  after entering/teleporting to a zone (both touch `applyRoadNetwork`).
- New files (`NpcAiManagerTraffic.kt`, `WorldMapScreenGoogle.kt`, `WorldMapScreenWeb.kt`) are **LF** —
  normalize in Android Studio if your repo enforces CRLF.
- Not compiled here — please **Rebuild** and sanity-check both native (OSM/Google) and web renderers, the
  fog, NPC/vehicle sprites, and the de-duped VM paths.
- **Remaining twin de-dup is pending** (one pair per compile cycle): `updateVisibleRoads` (⚠️ signature
  divergence: member param `playerLoc` vs extension `location`), `updateDestinationRoute`,
  `triggerWastedSequence`, `spawnOustedDriver`, `maybeRefetchRoadNetwork`, `addRemoteEntity`, and the
  giants `handleMultiplayerMessage` (~165) and `startGameLoop` (~440).
