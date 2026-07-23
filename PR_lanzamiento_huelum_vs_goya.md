# Huelum vs. Goya — full fighting mode + campaign fixes and engine refactor

**Branch:** `fix-audio-add-newFightAssets` → `main`
**What this merge ships:** debug APK (GitHub Release) + **signed AAB to Play Store, `alpha` track = closed testing** (NOT production; promoting to production is a manual step in Play Console).

> This PR bundles the accumulated work on the **Huelum vs. Goya** mode (Street Fighter POW) plus fixes to the main campaign. It is intentionally large: it closes the cycle to ship to closed testing.

**Version:** `1.0.0.13` (`versionCode` auto-increments in CI via `APP_VERSION_CODE = 1000 + run_number`).

---

## 1. 🎨 Character poses & implementation (new art)

- **Full 3rd Strike moveset (sheets 20–29):** dash/backdash, high/low block, **parry** high/low, crouching attacks, anti-air, **sweep**, air attacks, **long kick**, **overhead**, **grab → throw**, taunt, and **Super Art** with meter. 179/180 sheets sliced and packed for all 18 fighters.
- **La Llorona** gets her own **long kick + overhead** → she no longer uses the ALPHA placeholder (borrowed art), which **removes her crash and an extra atlas in RAM**.
- **La Presidenta:** **FATALITY with dedicated art** + `bonusPower1` (SPECIAL ULTIMATE) from the owner's cutouts; **metamorphosis** strips separated, renumbered and in real order ("STEP" labels removed with a measured threshold).
- **Slicing fixes closed** by the owner: Paramédico Cruz Roja `super-6` (no longer a character-less wave), poses the slicer split in two now merged (Rey Grupero, Policía CDMX Hombre), sheet 29, global green despill, body anchoring by "feet touch the floor".
- **Hitboxes** (head/body) regenerated for every frame with the new detector.
- Known frozen animations are documented as art debt (non-blocking).

## 2. 🔊 Audio & subtitles

- **Voice subtitles turned ON** (`voiceSubtitlesEnabled = true`): `voice_phrases.json` with **64 curated `es` lines** + a **complete `en` track** (real translation; shouts/onomatopoeia preserved). The `|` delimiter now splits the subtitle into **sequenced segments** synced with the voice (one line per segment + word-wrap).
- **Real i18n fix:** the English translation of the special never showed (it used a fixed `phraseEs`).
- **Voice normalization** plus a voices/subtitles audit with dedicated tooling.
- **Policeman's intro line** ("It's forbidden to drink in public", ~15 s) is **no longer cut off** by another voice from the same fighter or by the round start.
- **Human corrections already applied by the owner** (Paparazzi 5 carrying a Paparazzi 1 clip, Señor de la Tienda with Prankedy's voice, La Tzitzimime mis-sliced / off-timing).

> Non-blocking follow-up (post-launch, → Gemini): trimming ~29 long clips and adjusting 5 clips outside −16 ±2 LUFS.

## 3. 🧠 Engine refactor (Phase 1 complete & audited + Phase 2 started)

The **pure logic** was extracted from `StreetFighterViewModel` into `domain/models/streetfighter/` (no Android, JVM-testable). The VM **delegates via aliases → identical behavior**.

| Pure piece | What left the VM |
|---|---|
| `SfStateMachine` | `validFrom` table + sub-lists + `knockdownStates` |
| `SfDamage` | base damage, `ATTACK_META`, chip, `resolvedDamage` (block/combo) |
| `SfPhysics` | one-tick kinematics (`step`) + `clampToStage` |
| `SfAnimation` | animation advance (frameIndex/timer/shouldAdvance/isCompleted) |
| `sfUsableBonusPowerCount`, `SfBox` | usable powers + collision geometry |

- **47 characterization tests** green (before: 0 covering the engine).
- **Audit:** the extraction was verified **as data** against the original VM (67 `validFrom` targets, sub-lists and formulas identical; 0 residual copies).
- Status: separation **NOT finished** (no `SfEngine`/`SfGameMode` yet; the VM still uses mode flags). **Does not block launch** — it's an internal maintainability refactor; the game already works. Phases 3–5 remain as post-launch cleanup.

## 4. 📱 Low-end optimization

- **Fighter atlases at ½ resolution** on low-end (`inSampleSize=2`, RGB_565): ~73 MB → ~18 MB of RAM per fighter, without changing on-screen size.
- **Heavy decoding moved to `Dispatchers.IO`** under the LOADING overlay (`SfFightAssets`): no more "freezes for a few seconds" when entering a fight; transforming mid-fight **no longer re-decodes** anything.
- **Anti-OOM shielding:** every atlas decode wrapped in `runCatching` (failure → degrades, doesn't crash); the ALPHA placeholder is always half-resolution.
- **AAB:** lossless WebP dropped IMAGES from ~102 → **88.6 MB** (the gate validates < 500 MB).

## 5. 🎮 Gameplay & UI (summary)

- **Dizzy/STUN** classic (dizzy bar + procedural stars over `stun-3`); **super meter** glows when full and slowly decays when you stop landing hits. All modes, multiplayer included.
- **La Presidenta's metamorphosis:** Round 1 only, starts with **full health**, and **persists** across rounds.
- **Navigation:** leaving a fight returns to **that mode's** character select.
- **Tutorial:** labels use the real controls, **pulsing highlight** of the step's button, and inverted layout (buttons on top / combo sheet below); **3-2-1** countdowns in fights and between lessons.
- **"Neon Arcade" controls** (L1/L2 · R1/R2 triggers), pause screen with POW logo.
- **AI:** uses fatality/super when the meter fills; AI vs AI fights on the correct map; arcade only drops a rung after 3 straight losses.
- Fine fixes: joystick dead zone (crouch no longer sticks), auto-facing after a cross-up, blocked normals = 0 damage.

---

## ✅ Verification

- **Static (local, no SDK):** balanced braces vs `main`, consistent CRLF/line endings, symbols resolve in the new pieces, clean tree, branch in sync with `origin`.
- **Automated (run by this PR):** the **`PR Quality Gate`** workflow runs `:app:assembleDebug :app:testDebugUnitTest` (**compiles the app + runs the 47 tests**) and **detekt** with a baseline. **Both are hard gates: do not merge if they're red.**
- **Manual (owner, pending):** play the **6 modes** (Arcade, VS, AI vs AI, Showcase, Tutorial, Multiplayer) with the Release's debug APK. CI does not cover the playtest.

### Checklist before merging
- [ ] PR Quality Gate green (compiles + 47 tests + detekt — includes the 9 new-debt fixes from the engine refactor).
- [ ] 6 modes played OK (focus: La Llorona, stun in multiplayer, metamorphosis R1/R2, policeman intro, navigation).

### Versioning (now automated)
- This release ships as **`1.0.0.13`** (single source: `build.gradle.kts`; the YAML/whatsnew read it, no more hardcoding).
- After a real merge, the **`bump-version`** job auto-commits `1.0.0.14` back to `main`, then `15`, `16`… with no manual edits.
- ⚠️ For the auto-bump to push, `main` must allow **github-actions** to push (if it's a protected branch, grant Actions a bypass in *Settings → Branches*; otherwise the bump job fails with 403 — the release still ships).

> To skip the automatic Play upload and only get the signed AAB: add the **`manual-play-upload`** label to the PR.
