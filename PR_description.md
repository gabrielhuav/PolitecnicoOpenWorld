A large refactor pass for **Politécnico Open World**: full **internationalization (ES + EN)**, **game
audio** wired across the open world and interiors, the **complete de-duplication** of `WorldMapViewModel`
member/extension twins, oversized-class separation, zero-behavior low-end rendering micro-optimizations,
settings UX, package reorg, and a docs cleanup. All changes follow strict MVVM-per-feature
(immutable state via `_state.update { it.copy(...) }`; Views only observe and emit intents).
**Not compiled in this environment — ready for "Rebuild Project".** Source files keep CRLF; new files were
created LF (normalize in Android Studio or leave — both compile).

---

## 🌐 Internationalization (i18n) — complete for all player-facing text
- Base language is Spanish in `res/values/strings.xml`; English in `res/values-en/strings.xml`, with
  **1:1 key parity** (verified). The in-app language selector (Settings → Interface) was already wired
  via `LocaleHelper`; this pass externalizes the remaining hardcoded UI strings so it actually localizes.
- Migrated, feature by feature: **`main_menu`, `settings`** (already done) + **`map_exterior`** (HUD,
  mission-failed screen, objective widget, carjack tip, Prankedy hire dialog, save game), **`campaign`**
  (Story Mode + comic intro, incl. the developer comic-box editor), **`interiores`** — zombie survival
  HUD/prompts/objective, ESCOM rooms, **metro/metrobús transit screens** (destination picker + station
  headers) and the **matrix/waypoint designers** (MATRIZ/WAYPOINTS/PARED/BORRAR/COL/FIL/etc., shared
  `int_*` keys), and **`MainActivity`** save/load dialogs.
- Conventions followed (file 09 §i18n): `stringResource(R.string.x[, args])` in Composables,
  `getString(...)` in the Activity, `stringResource` hoisted to a `val` before `onClick` lambdas,
  positional args `%1$s`/`%1$d`, **apostrophes escaped as `\'`** and `<` as `&lt;` in XML (otherwise AAPT2
  fails with *"Can not extract resource from ParsedResource"*).
- ~80 new keys this PR, all in both `values/` and `values-en/`.
- **Still pending (minor):** `CampaignObjective.title/description` are plain `String`s in the model; full
  i18n there means moving them to `@StringRes` (compiler-gated, deferred).

## 🔊 Game audio
- **SoundPool async-load fix (`SoundManager`).** `playWalk()/playRun()/playCar()` latched the `0` that
  `SoundPool.play()` returns when a sample hasn't finished loading (loads are async, no ready-listener),
  so an early call (walking at spawn) left the loop **silent forever**. They now only latch a **valid**
  stream id (`> 0`) and retry on `0`.
- **Open-world game-loop audio.** Footsteps / running / car engine / nearby-zombie audio existed only in
  a dead `startGameLoop` extension. It was **merged into the live member** (see twin de-dup below). The
  car sound was also fixed to play **only while the player is driving** (the original ambient-traffic
  trigger fired on any nearby moving car NPC → it sounded like a car while walking).
- **Interior audio + cross-loop coordination.** The open-world game loop is Activity-scoped and keeps
  running inside interiors; its per-tick `stopWalk()` was **stomping interior footstep audio**. Added a
  `worldMapForeground` gate (set by `WorldMapScreen` via `DisposableEffect`, which also stops the world
  sounds on exit) so the loop only manages audio on the map. Footstep audio drivers were added to the
  interiors that had none — simple ESCOM rooms + ShineCTO (`InteriorPlayerViews.PlayerView`) and
  metro/metrobús stations (their player sprites); the zombie interior already had one.

## ♻️ WorldMapViewModel twin de-dup — **complete (all 8 pairs)**
Many large `WorldMapViewModel` members were duplicated as **dead extensions** in the `WorldMap*.kt`
partials; Kotlin's **member wins** for in-class calls, so the extension was dead and often **diverged**.
Resolved one pair per compile cycle (tested in-app between each):
- **Clean de-dup** (sync the extension to reproduce the member exactly, delete the member):
  `startHealthBarTimer`, `applyRoadNetwork`, `spawnOustedDriver`, `triggerWastedSequence`,
  `addRemoteEntity` (the member also replicated remote zombie role + health/death/aggro/scream),
  `maybeRefetchRoadNetwork` (member rebuilds the routing grid + A* graph), `updateVisibleRoads`
  (member uses a circular radius + anti-race re-check; ⚠️ the extension's param is `location` vs the
  member's `playerLoc` — all call-sites are positional, verified).
- **Merged** (the dead extension carried real fixes that were never running — activated with sign-off):
  - `handleMultiplayerMessage`: `MASTER_SYNC_CHECK` now only cleans `isRemote` NPCs (stops host-spawned
    NPCs from flickering), `PLAYER_DAMAGE` routes `takeDamage` to the **Main thread** (it mutates Compose
    state while the handler runs on IO — a real race) and triggers combat-fear; kept the member's
    `safeDisplayName` fallback.
  - `startGameLoop`: merged the **audio** block into the member (member keeps all its driving/campaign/
    traffic logic; no cascade rebinding). `WorldMapGameLoop.kt` is now a tombstone.
- **Reverted / "do not touch":** `updateDestinationRoute` + `calculateRouteOnNetwork`. De-duping the head
  re-bound the whole interdependent routing chain (`→ getNearestPointOnNetwork / nearbyRoadNodes / …`)
  to the extension versions and **broke navigation**; reverted to the working member-canonical state.
- **Result:** no live divergent twins remain. `WorldMapViewModel.kt` **~2467 → ~2114** lines.

## ✂️ Class separation (smaller files, fewer tokens) — current state
Only 5 files exceed 1000 lines, only 1 exceeds 1500:
`WorldMapViewModel.kt` (2114), `NativeOsmMap.kt` (1460), `WorldMapScreen.kt` (1326), `MainActivity.kt`
(1064), `ZombieGameScreen.kt` (1035). Earlier in this branch:
- **`NpcAiManager.kt` ~1419 → ~882** — `moveNpc`/`moveLocalNpc` → new `NpcAiManagerTraffic.kt`
  (`internal fun NpcAiManager.X` extensions; ~15 members flipped `private → internal`; members removed).
- **`WorldMapScreen.kt` ~2265 → ~1325** — the `when (mapProvider)` render block delegates all 3 branches:
  Google native → `ui/WorldMapScreenGoogle.kt` (`GoogleMapLayer`), Web/Leaflet → `ui/WorldMapScreenWeb.kt`
  (`WebMapLayer`, 23 params incl. per-frame guard holders), OSM → `NativeOsmMap`. Top-level composables,
  caches/holders passed by parameter (never recreated).

## ⚡ Low-end rendering micro-optimizations (zero behavior change)
- **`NativeOsmMapFog.kt`:** the fog `RadialGradient` re-allocated an `IntArray` + `FloatArray` every
  `draw()` (~30 Hz); both are now reused fields (`RadialGradient` copies them on construction).
- **`NativeOsmMap.kt` update lambda:** density read once into `screenDensity`; four sites re-fetched
  `displayMetrics.density` per frame (one **shadowed** the cached value). All use the cached one now.
- **`NpcAiManager`:** the three per-tick spawn checks rebuilt the full ways list via
  `cachedWayBoxes.get().map { it.way }`; now precomputed once into `cachedWaysFiltered` in
  `updateRoadNetwork`.
- **Rejected as unsafe-without-compiler (09 §6):** web JS → template literals (it lives in Kotlin `"""…"""`
  → `${…}` is Kotlin interpolation), reference-caching the time-predicate NPC filters (stale markers),
  moving `configureOsmdroid` off the main thread (startup ordering).

## ⚙️ Settings UX
- **Compact landscape mode** in `SettingsScreen.kt` for short screens (`screenHeightDp <= 380`): shrinks
  top-bar/title/paddings/category font and sidebar items. Portrait and large-landscape unchanged (every
  value is `if (compactLand) … else <original>`).
- **One-tap "Optimize for my device"** (Settings → Gameplay, `onOptimizeForDevice` wired in `MainActivity`):
  NPC density to minimum + both emoji toggles ON, persisted and applied live, with a confirmation toast.

## 📦 Project structure — `domain/models/map/` package
- The 15 loose files under `domain/models/` were moved (Android Studio **Refactor → Move**, ~308 refs)
  into **`domain.models.map`** (`Npc`/`NpcType`/`CarModel`, `MapWay`/`MapNode`, `Landmark`, metro/metrobús
  stations, `ActiveCollectible`, collisions, ESCOM buildings, `CharacterVisualConfig`, `TeleportCatalog`,
  `ShineCTOLocation`, `InteriorEntryCatalog`, `Landmarkassetcatalog`). Subpackages `ai/`/`campaign/`/`zombie/`
  unchanged. **Gotcha:** AS's Move skips files open/unsaved — `WorldMapState.kt` was open and its FQNs were
  left stale (broke `MainActivity`); fixed by hand.

## 📚 Docs (`README for IAS/`)
- Updated **`09_CONVENTIONS_GOTCHAS.md`** (§0 current-state sizes + de-dup-complete summary, §6 perf, §i18n
  complete, §12 de-dup record), **`04_MAP_EXTERIOR.md`** (Key files; game loop = member, `WorldMapGameLoop.kt`
  = tombstone), **`00_INDEX.md`** (quick facts, working-docs).
- New working docs: **`ANALISIS_codigo.md`** (size/duplication/MVVM/perf/i18n report) and
  **`REVISION_repo.md`** (repo + security review — signing keystore confirmed **never committed** across
  155 commits / 11 branches; `.gitignore` complete; no hardcoded secrets in the Node servers).
- **Cleanup:** removed redundant copies inside the folder — `README.md` (136 KB) and `plan.artifact.md`
  (44 KB) were superseded by the granular `00`–`09`; the de-dup log was consolidated into `09 §12` and its
  scratch file removed. The **root `README.md`** now points to `README for IAS/` for detailed docs. A clean
  reusable entry prompt (`PROMPT_*`) was added.

## 🧪 Notes / test focus
- **Not compiled here — please Rebuild** and sanity-check both native (OSM/Google) and web renderers.
- **i18n:** switch ES ⇄ EN in Settings and spot-check menus, world-map HUD, mission-failed, Prankedy
  dialog, zombie HUD, metro/metrobús screens, and save/load dialogs.
- **Audio:** footsteps should play **everywhere** (open world, ESCOM rooms, metro/metrobús, zombie
  interior) without one context muting another; car engine only while driving.
- **De-dup (member-vs-extension):** test the affected behaviors — police chase/A* pathing + yellow road
  lines after entering/teleporting (`applyRoadNetwork`/`maybeRefetchRoadNetwork`/`updateVisibleRoads`),
  death → respawn / mission-failed (`triggerWastedSequence`), carjack ousted-driver reaction
  (`spawnOustedDriver`), waypoint navigation (the routing chain was intentionally **left as-is**), and
  multiplayer (remote NPC health bars + zombie roles, damage handling) for `handleMultiplayerMessage`.
- New files are **LF** — normalize in Android Studio if your repo enforces CRLF.
