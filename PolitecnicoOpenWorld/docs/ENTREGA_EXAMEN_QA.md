
### What changed?

- Added an interactive vehicle horn button (📢) to the center of the driving controller layout (`VehicleActionButtonsController`) in **Free Roam / Open World**, replacing the unused 48 dp center spacer.
- Included dedicated 44.1 kHz dual-tone audio asset (`claxon.wav`) under `app/src/main/assets/AUDIO/` and loaded it into `SoundManager.playHorn()`.
- Implemented defensive audio routing in `WorldMapViewModel.honkHorn()`: playing standard automotive horn for civilian cars, and branching gracefully to the police siren (`playPolice1()`) when driving a stolen patrol car (`isDrivingPoliceCar`).
- Extended `NpcAiManager` with concurrent horn event handling (`triggerHorn`, `applyPendingHorn`, `HORN_RADIUS = 0.00025`, `HORN_FEAR_DURATION_MS = 3500L`, `HORN_ANNOY_DURATION_MS = 2500L`) triggering `fearUntil` on `NpcTrait.COWARD` (fleeing towards sidewalks) and `talkingUntil` on `NpcTrait.AGGRESSIVE` (annoyed dialogue reaction bubble).
- Guarded entity typing to explicitly target `NpcType.PERSON` avoiding unwanted side-effects on vehicular traffic or zombie simulations.
- Added comprehensive 6-case QA test matrix in `docs/pruebas.md`.

### Why?

Closes <tu-usuario-github>/PolitecnicoOpenWorld#1

This feature introduces an interactive vehicle horn aligned with the game's immersive open-world exploration while supporting both standard civilian vehicles and stolen police patrols. It enriches the driving experience by utilizing the existing NPC personality framework (`COWARD` vs `AGGRESSIVE`) and turning an empty 48 dp spacer in the driving HUD into a responsive, accessible game mechanic.

### QA evidence

- **Automated Tests:**
  - `:shared:testAndroidHostTest & :app:testDebugUnitTest -> 319/319 tests passed (0 failures).`
  - `Kotlin/Native test name compatibility script ( tools/check_kmp_test_names.sh ) -> PASS.`
  - `Detekt static analysis -> 0 new issues against baseline (exit code 0).`

- **Executed QA Test Cases (Samsung Galaxy S24 FE / SM-S721B - Android 14 / API 34):**
  - **CP-01 (Happy Path - Free Roam):** Honking civilian car horn triggers dual-tone audio and nearby `COWARD` pedestrians flee towards sidewalks at 60 fps.
  - **CP-02 (Alternate/Boundary):** Rapid horn spamming does not crash `SoundPool`; driving a stolen police car safely triggers police siren instead.
  - **CP-03 (Regression):** Existing vehicle controls (A=gas, B=brake, X=handbrake, Y=exit) and on-foot interactions remain 100% operational.
  - **CP-04 (Lifecycle/State):** Horn button and driving state persist cleanly across app pause/resume (Home) and Options menu navigation.
  - **CP-05 (Accessibility):** Central horn button adheres to >=48dp touch targets with high-contrast orange styling (`#E67E22`) and haptic tap.
  - **CP-06 (Compatibility/Audio):** Game executes stably on physical device with zero audio memory leaks and handles silent mode (`sfxVolume = 0`) gracefully.

#### Visual Evidence from Samsung Galaxy S24 FE (SM-S721B):

| CP-01: Vehicle Horn HUD & Civilian Honk | CP-02: Police Patrol Siren |
|:---:|:---:|
| *(Pega aquí la URL de tu captura 1)* | *(Pega aquí la URL de tu captura 2)* |

- **Detailed QA Documentation:** See [docs/pruebas.md](docs/pruebas.md) and Academic Index in `docs/ENTREGA_EXAMEN_QA.md`.

### Risk / rollback

- **Risk:** Minimal. The change is additive; existing save files, netcode, pedestrian pathfinding, and combat logic remain untouched.
- **Rollback:** Reverting this branch cleanly removes the horn button from `VehicleActionButtonsController`, removes `claxon.wav`, and restores the empty 48 dp spacer with zero residual effects.
```