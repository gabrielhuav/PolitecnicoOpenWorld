# Playable male skin + character picker, gameplay/rendering fixes, ESCOM key puzzle & inventory

## Summary
A batch of campaign, rendering and UX work for **Politécnico Open World**, all following the strict
MVVM-per-feature conventions (immutable state via `_state.update { it.copy(...) }`, Views only observe
and emit intents). Could not be compiled in this environment — **ready for "Rebuild Project"**.

## Characters & skins
- **New playable male skin "Estudiante" (`escomboy`)** added to `PlayerSkin` (folders
  `MAIN/escomboy{Idle|Walk|Run|Special}/`, frames 16/25/16/16). Shows up automatically in the skin
  selector and is saved/restored by enum name.
- **Per-skin sizing fixed.** Because each skin's character occupies a different fraction of its canvas,
  `PlayerSkin` gained `renderScale` (escomboy `1.8f`) and a **precomputed** `walkBodyFraction`
  (lazaro 0.61 / escomgirl 0.94 / robot 0.62 / escomboy 0.41). Using a static reference removed the
  **first-cycle size jitter** (the old async `walkRefFrac` started `null`, so frames changed size on the
  first animation loop).
- **New-game character picker** (`NewGameCharacterDialog`): starting a campaign first asks for the
  character — **Hombre (ESCOM BOY) / Mujer (ESCOM GIRL) / No binario (ROBOT)**. **LÁZARO** is only
  selectable with **Developer Mode** on (also filtered in the in-game "Cambiar Skin" selector). Each
  preview is normalized so the three render the same size.

## World map / rendering
- **Player avatar keeps a fixed on-screen size** on foot (`exactPersonDp = 38.dp`), decoupled from zoom;
  NPCs/vehicles keep real-meter sizing.
- **Melee reach** (`WorldMapViewModel.ATTACK_RADIUS`) `0.00022` → `0.00008` (~24 m → ~9 m).
- **"Zoom to player" no longer bounces back** — `zoomToPlayer()` now also sets `targetZoomLevel`.
- **Parked cars hidden only when far out** (`zoomLevel < 16.5`, was 18.5 which hid them at entry).
- **Parked cars no longer starve NPC/traffic spawning** — the ESCOM lot has 81 slots; those parked cars
  used to count against `maxTotalNpcs`/`maxActiveNpcs`, blocking pedestrians/traffic. They're now excluded
  from the budget (`nonParkedAlive()`), like campaign-route NPCs.
- **Parking cars reload** after returning from the ESCOM interior (empty-but-populated lot repopulates
  immediately, bypassing the cooldown).

## Loading
- **Loading gate waits until the scene is actually ready** — it polls (`sceneReady`) until nearby
  landmark/building bitmaps are decoded **and** NPCs/cars are seeded, with a 30 s safety timeout and
  `POW_DBG "gate:"` diagnostics.

## Campaign (Mission 1)
- **Prankedy stops following** once you enter the ESCOM door (HIRED companion deactivated).
- **Objectives widget inside the ESCOM interior**: after Mission 1 the lobby shows a new objective
  **"Busca pistas en la ESCOM"** (`MissionCatalog.BUSCAR_PISTAS_ESCOM`); the exterior objective stays
  "Ingresa a la ESCOM, Cumplido".
- **Zombie-mode hand** in the ESCOM lobby is now **Developer-Mode only**.

## ENCB_lab1 key puzzle + inventory (Mission 1 interiors)
- Entering `encb_lab1` scatters **5 keys** at random **walkable** spots (`assets/CAMPAIGN/KEYS/`, new
  `KeyDrop` model; positions validated against the collision matrix / `nearestWalkableSpawn`).
- Walking over a key shows a prompt; **action picks it up** into the **inventory** (1 usable slot,
  rendered with the **real key image**). At the **locked** advance door you press action to **try** the
  held key: the **correct** one (`LLave4.png`) opens it; a wrong one is discarded so you fetch another.
- **Inventory UI** (`showInventory`) opens by **holding Y** — several slots, only slot 1 unlocked, the
  rest shown red/locked for future missions.
- **Controls swapped in interiors:** weapon menu now opens by **holding A** (tap A = toggle run),
  inventory by **holding Y** (`onPrimaryPressed/Released`, `onSecondaryPressed/Released`).
- **Saved in game saves:** `GameSaveData.inventoryKeys` + `lab1KeyFound`, bridged via
  `WorldMapViewModel.currentInteriorInventory/…Lab1KeyFound` and restored into `ZombieGameViewModel` on
  load (seeded before the first `loadRoom`).

## UX
- **"Nueva partida · elige slot"** hides the 2 reserved auto-save slots (`SaveSlotsDialog(hideAutoSlots)`).

## Notable files
`PlayerSkin.kt`, `PlayerCharacter.kt`, `NewGameCharacterDialog.kt`, `SkinSelectorDialog.kt`,
`WorldMapScreen.kt`, `WorldMapViewModel.kt`, `NativeOsmMap.kt`, `NpcAiManager.kt`, `CampaignMission.kt`,
`ZombieGameScreen.kt`, `ZombieHud.kt`, `ZombieGameViewModel.kt`, `ZombieGameState.kt`,
`ZombieGameConstants.kt`, `ZombieRoomCatalog.kt`, `KeyDrop.kt`, `MainActivity.kt`,
`SaveGameRepository.kt`, `WorldMapSaveGame.kt`, plus `README for IAS` docs (README EN+ES, 05, 07).

## Notes
- **Member-vs-extension gotcha:** the live game loop is the **member** in `WorldMapViewModel.kt`
  (`WorldMapGameLoop.kt` is dead); edits were applied to the members.
- No `IntroPOW*Boy.webp` assets yet → escomboy's Story-Mode comic falls back to the default (male) panels.
- If a skin's walk art changes, re-measure its `walkBodyFraction`.
- Not compiled here — please **Rebuild** and test the ENCB key flow, the A/Y controls, and save/load
  mid-puzzle.
