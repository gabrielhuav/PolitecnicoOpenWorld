# feat(characters): 9 new playable test characters (dev-only) + sprite-slicing tooling

**Branch:** `feat/personajes-npc-jugables-bromas`

## Summary

Adds **9 new playable characters** as **developer-only test skins** (selectable from *"Choose Character"*, just like Lázaro). Each character is a full top-down avatar with **Idle / Walk / Run / Special** animations, cut from hand-authored sprite sheets, background removed, and normalized so every skin renders at the same on-screen body size.

This also generalizes `PlayerSkin` so skins can live under `SPRITES/NPC/` (next to Prankedy) instead of only `SPRITES/PLAYER/`, fixes two interior screens that ignored the selected skin, and makes the character-picker thumbnails fill their box uniformly.

> These characters are **temporary test skins** gated behind Developer Mode. They let us validate the art/animation integration in-engine before wiring any of them into gameplay (NPCs, missions, etc.).

## New characters

| Character | Idle | Walk | Run | Special |
|---|---|---|---|---|
| Señor de la Tienda | 3 | 4 | 8 | Broom hit (*escobazo*) |
| El Rey de las Bromas | 3 | 6 | 8 | Pranks (spray / megaphone / balloon) |
| Pepe del Rey de las Bromas | 3 | 4 | 8 | Gas-tank swing |
| Prankedy | 3 | 9 | 8 | Attack (reuses existing Prankedy sprites) |
| Paparazzi #1 | 3 | 6 | 10 | Take photo |
| Paparazzi #5 | 3 | 7 | 10 | Take photo |
| Policía CDMX | 4 | 7 | 11 | Shoot |
| Granadero (riot police) | 3 | 8 | 11 | Shield bash |
| Paramédico | 4 | 6 | 8 | Radio call |

## Technical changes

### `PlayerSkin` — base-path generalization (`features/map_exterior/ui/components/PlayerSkin.kt`)
- New `basePath` field (default `"SPRITES/PLAYER/"`). New skins set `basePath = "SPRITES/NPC/"` and a `skinFolder` ending in `/` (e.g. `"SenorTienda/"`) so the path builders resolve to `SPRITES/NPC/<Char>/<Idle|Walk|Run|Special>/<prefix>_<i|w|r|s>_<N>.webp` (Prankedy-style layout). The 4 original skins are unchanged.
- 9 new enum entries. Each uses `walkBodyFraction = 0.865` because the frames are cut at a **uniform scale** (the figure fills ~0.865 of its canvas in every animation), so all animations render at the same body height and don't shrink when running — consistent with the unified player-size standard (`PLAYER_BODY_STANDARD_DP`).

### Sprite-slicing tooling (`tools/`)
- `tools/slice_new_character_sprites.py` and `tools/_slice5.py`: deterministic, resolution-independent slicers (PIL + NumPy).
  - Use the **already-transparent alpha** from the source PNGs (no flood-fill).
  - Per-animation regions → split into N frames (cut at the deepest valley so frames touching in run cycles still separate) → keep the figure's vertical block (drops text labels) → native-resolution crop + uniform margin + foot alignment.
  - **Special / "attack" rows** mix the figure with loose objects (tank, balloon, spark, shield) and sometimes two figures touching; these use figure-aware segmentation: drop short loose objects, split wide runs that contain two figures, keep the figure + its held prop.
  - Emits review montages to `tools/_debug*/` for visual QA.

### Dev-mode gating + interiors
- `SkinSelectorDialog.kt`: the dev-only set now includes Lázaro **and the 9 new skins**; they only appear in *Choose Character* when Developer Mode is on.
- `ZombieGameScreen.kt`: the **interior** character selector now receives `developerMode`, so the dev skins are selectable inside interiors (previously only on the world map).
- `ShineCTOScreen.kt`: now passes the selected skin to `PlayerView` (it was hardcoded to Lázaro). The metro / metrobús / zombie interiors already read the saved skin via `getPlayerSkin()`.

### Character-picker thumbnails
- `SkinSelectorDialog.kt`: thumbnails are **trimmed to their opaque content** (`trimToOpaque`) before display, so every preview fills the card uniformly (skins with large transparent margins no longer look tiny).

## Files changed
- `app/src/main/java/ovh/gabrielhuav/pow/features/map_exterior/ui/components/PlayerSkin.kt`
- `app/src/main/java/ovh/gabrielhuav/pow/features/map_exterior/ui/SkinSelectorDialog.kt`
- `app/src/main/java/ovh/gabrielhuav/pow/features/interiores/zombies/ui/ZombieGameScreen.kt`
- `app/src/main/java/ovh/gabrielhuav/pow/features/interiores/shinecto/ui/ShineCTOScreen.kt`
- `app/src/main/assets/SPRITES/NPC/<Char>/{Idle,Walk,Run,Special}/*.webp` — new sliced frames for the 9 characters (Prankedy copied into `PrankedyPlayable/`).
- `tools/slice_new_character_sprites.py`, `tools/_slice5.py` — slicers.

## How to test
1. Enable **Developer Mode** (Settings).
2. World map → Options → **Choose Character** → pick any new character → verify Idle / Walk / Run / Special on the global map.
3. Enter an interior (zombie lobby / ESCOM / ShineCTO) and confirm the same character renders there (not Lázaro).
4. With Developer Mode **off**, confirm the test characters are hidden from the selector.

## Notes / follow-ups
- These are **test skins only** — not yet used as NPCs or in any mission.
- **Police/paparazzi sheets re-sliced from complete sources.** Paparazzi #1/#5, Policía CDMX and Granadero were re-cut from the final, non-truncated sheets the artist placed in `assets/SPRITES/NPC/sprites juntos/`. Granadero now uses the **real riot-police sheet** (shields) — `idle 3 / walk 6 / run 8 / Shield-bash 4` — replacing the earlier placeholder (it had mislabeled CDMX content). CDMX special is **Disparar (3 frames)**. Frame counts in `PlayerSkin` updated to match.
- **Walk/Run re-sliced with mass-peak segmentation (no more "doubles").** The even-grid slicer assumed the sheet labels (e.g. "CORRER 8 FRAMES") were accurate; the art actually has variable, higher frame counts (e.g. CDMX run = 11), so an even split jammed two figures into one cell. Walk/Run now use per-figure **peak detection** (torso mass-peak → cut at the valley between peaks), robust to touching figures and detached limbs, with each row's right edge clamped just before the portrait/palette panel. Final counts: CDMX `w7/r11`, Granadero `w8/r11`, Paparazzi #1 `r10`, Paparazzi #5 `w7/r10`, Paramédico `w6` (re-cut from the complete sheet). Idle/Special were already correct and untouched.
- **Orphan frames to prune before merge** (sandbox can't delete; index > frame count, not referenced by `PlayerSkin`, harmless): `PoliciaCDMX/Special/pcd_s_4..5`, `Granaderos/Idle/gra_i_4`, `Granaderos/Special/gra_s_5`, `PaparazziN1/Walk/pn1_w_7..8`, `Paramedico/Special/pmd_s_4`.
- The original source sheets (`sprites_*-Photoroom.png`, `sprites_*.png`) are still under `assets/SPRITES/NPC/` **and `assets/SPRITES/NPC/sprites juntos/`**; **move/delete both before merge** so they don't bloat the APK.
- UI strings for these dev-only skins are not localized (display names only). If any of these graduate to real characters, migrate strings to `values/` + `values-en/` and re-measure body fractions.
