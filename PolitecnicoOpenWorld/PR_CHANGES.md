# Map UX, rendering parity, and main-menu fixes

## Summary

This PR improves the exterior world map across the native (osmdroid) and web
(Leaflet) renderers, fixes several UI issues in portrait/landscape, makes NPC
and player sizing consistent, and ties the main-menu version label to the
build configuration. The goal is visual parity between renderers and a more
playable, GTA-like feel while staying within the project's performance limits.

## Main menu

- **Version label bound to Gradle.** The footer now shows
  `v${BuildConfig.VERSION_NAME} - ESCOM Edition` instead of a hard-coded
  string, so it always reflects `versionName` from the app-level Gradle config.
- **Title never wraps/clips.** Added an `AutoResizeText` composable used for
  `POLITÉCNICO` / `OPEN WORLD`. It forces a single line and auto-shrinks the
  font to fit any screen width, so letters can no longer drop to a new line on
  taller/narrower devices. Letter spacing is expressed in `em` so it scales
  with the font.

## Native (osmdroid) map

- **Higher zoom enabled.** `setMaxZoomLevel(22.0)` (and `setMinZoomLevel(14.0)`)
  plus a `MapTileApproximater` so zoom levels 20–22 are scaled from the real
  z19 tiles instead of showing a blank map.
- **Reliable tile loading.** The loading screens (initial entry and
  post-teleport) now actually download a tile grid around the player at
  **z19 (max real)** and **z17 (medium)** into the Room cache, matching the
  "download the map first" intent. The native entry path previously only
  simulated progress without downloading anything.
- **Default to maximum zoom.** `ZOOM_GAMEPLAY_OSM` is now `22.0`.

## Web (Leaflet) map

- **No player drift on pinch-zoom.** A user pinch-zoom now enters the same
  exploration/anchored state as panning (with pointer-down detection so
  programmatic/menu zoom and auto-follow are excluded), so the player no longer
  moves with the gesture and snaps back.
- **NPC sizing matches native.** Pedestrians and vehicles are now sized from
  real-world meters via pixels-per-meter (same basis as the native renderer)
  instead of a zoom-based formula.

## Fog of war

- **Anchored to the player.** The fog was a screen-centered Compose overlay,
  so it stayed glued to the screen center while the player stayed on the map.
  It is now rendered inside each map:
  - Native: a `FogOverlay` (osmdroid `Overlay`) projected onto the player's
    real position every frame.
  - Web: a `#fog` HTML overlay redrawn on every Leaflet `move`/`zoom` event.
  - The Compose Canvas fog is now used only for the Google Maps native renderer.
- **Driving rotation fix.** While driving, the native map canvas rotates; the
  fog rectangle is now oversized to the screen diagonal so it fully covers the
  screen at any rotation angle (no triangular gaps in the corners).

## Center-on-player control

- The existing **"Centrar en jugador"** menu entry now evolves into a sub-menu
  with **"Centrar en jugador"** and **"Hacer zoom en el jugador"** (recenter +
  max zoom) when the user has changed the zoom; otherwise it stays a single
  action.
- **Off-center movement input.** When the map is not centered, the first tap on
  the left-side movement controls (D-pad/joystick) recenters on the player
  (without changing zoom) instead of moving the player off-screen.

## Controls / layout

- **Landscape menu is scrollable and capped.** The expanded Options/Mapa menu
  (with nested submenus) is capped to ~68% of screen height and scrolls, so it
  no longer overflows or collides with the controls in landscape, and all
  items remain reachable.
- **Right control shifts for the menu.** When the Options menu is open in
  landscape, the right-side control (D-pad / PS4 diamond, or whichever sits on
  the right when controls are swapped) animates left so the menu doesn't cover
  it, returning to place when the menu closes.

## Sizing / readability

- **Bigger, consistent sizes** across native, web, and the player:
  pedestrians ≈ 1.3 m, vehicles ≈ 4.0 m. The player's on-foot and driving
  sprites were updated to match the NPCs (previously 0.9 m / 2.5 m).
- **Larger NPC health bars** on both renderers (native bar height raised; web
  bar enlarged) so damage is visible when hitting an NPC.

## Gameplay realism

- **Ousted driver spawns next to the car.** When jacking an occupied vehicle,
  the displaced driver now appears ~2 m from the car (was ~7 m), as if stepping
  out of the door.

## NPC combat (GTA-lite)

- **Personalities (configurable).** Each NPC is `AGGRESSIVE` or `COWARD` at spawn;
  the aggressive share is `NpcAiManager.aggressiveRatio` (default `0.5`). NPCs never
  attack unprovoked.
- **Retaliation.** Hitting an aggressive NPC triggers a guaranteed counter-hit
  (~450 ms later, if you're within `ATTACK_RADIUS`) plus a straight-line chase
  (`aggroUntil` / `moveAggroNpc`). Cowards flee (`fearUntil`); aggressive NPCs are
  immune to fear.
- **Relentless mode.** 3+ consecutive hits on one NPC (`RELENTLESS_HIT_STREAK`) makes
  it implacable — it keeps chasing and hitting you (`startRelentlessAttacker`) until
  you or it dies.
- **Run-over** while driving (speed-scaled damage, deaths broadcast `NPC_DESTROY`).
- **Two-way traffic** (randomized spawn direction + right-side lane offset).
- **Feedback & death.** Persistent HUD health bar (zombie-style), red damage-flash on
  every hit, low-HP red aura, and a 💥 burst. The global WASTED screen freezes
  movement, ghost-fades the player, and respawns **inside the already-downloaded
  zone** (~80 m from death, snapped to road) instead of teleporting to ESCOM.
- **Multiplayer.** `MultiplayerNpc` carries `health`/`isDying`/`aggroUntil`;
  `Multiplayer/server.js` relays them (spread) and clamps health (v3.1). Each client
  applies damage to its own player.
- **Future work:** car-vs-car collisions.

## Notes

- At zoom 22 the native basemap is over-zoomed from z19 tiles, so the map
  imagery will look blurry while sprites stay sharp. The default zoom can be
  lowered (e.g. 20–21) with a one-line change if preferred.
- Fog and NPC sizing were intentionally tuned slightly larger than real-world
  scale for visibility.
