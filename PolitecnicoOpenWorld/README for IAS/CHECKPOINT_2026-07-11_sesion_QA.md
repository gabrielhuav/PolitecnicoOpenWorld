# CHECKPOINT 2026-07-11 · Sesión "QA Misión 2/3 + SF + Cómics"

> **Para qué:** lista VERIFICABLE de TODO lo implementado en esta sesión, para corroborar cambio
> por cambio contra el código (rama `feat/new-routes-for-NPCs-and-actions`). Commits que la cubren:
> **`b2ae64e`** ("Implement Mission 2 9/9" = assets + sprite sets + brote M3) y **`cbd772a`**
> ("QA Changes for Mission 2 1/9" = fixes de QA + cómics + SF + i18n). El árbol quedó limpio
> (todo commiteado). **Nada se pudo compilar/probar en el entorno del asistente** (falta
> `gradle-wrapper.jar`); el dueño compiló y confirmó "compila". Verificación en dispositivo pendiente.

---

## 1. Misión 2 — LATA y MOCHILA con arte propio (antes emojis)

- **Assets nuevos:** `app/src/main/assets/CAMPAIGN/MISSION2/lata_apestosa.png`, `mochila_prankedy.png`
  (recortados de `lata_Prankedy.png`/`mochila_Prankedy.png`, fondo negro → transparente).
- **`features/interiores/zombies/ui/ZombieGameScreen.kt`:** composable **`StoryGroundSprite`**
  (carga PNG submuestreado, cae al emoji si falla) reemplaza `Text("🥫")`, `Text("💨")`, `Text("🎒")`.
  La lata ya trae su vapor integrado (se eliminó el 💨 separado).
- **`ZombieGameState.kt`:** comentarios de `mission2Stink*`/`mission2Backpack*` actualizados.
- **Verificar:** en el salón (fase MOCHILA) la lata y la mochila se ven como sprite, no emoji.

## 2. Zombis del EXTERIOR con arte propio por-NPC (`Npc.zombieSpriteSet`)

- **Assets:** `SPRITES/ZOMBIE/ESTUDIANTE/z_walk_1..9.webp` y `SPRITES/ZOMBIE/PRANKEDY/z_walk_1..9.webp`
  (recortados de `spreadsheet_estudiantes_zombies_1.png` y `spreadsheet_Prankedy_zombie_1.png`;
  volteados a mirar DERECHA, altura normalizada, pies alineados; **la hoja `_2` borrosa NO se usó**).
- **`domain/models/map/Npc.kt`:** campo nuevo `val zombieSpriteSet: String? = null` (local, no serializado).
- **`features/map_exterior/ui/components/MapZombieSpriteManager.kt`:** usa `npc.zombieSpriteSet ?: "SPRITES/ZOMBIE"`
  como ruta base; OMITE el tinte por rol cuando hay set propio.
- **3 renderers** (`NativeOsmMap.kt`, `WorldMapScreenWeb.kt`, `WorldMapScreenGoogle.kt`): sufijo
  `_S${npc.zombieSpriteSet}` en su cache key (para no colisionar bitmaps).
- **Verificar:** el zombi del brote (M2 fase 3) se ve como "estudiante zombi" verde y camina.

## 3. Misión 3 — frasco de evidencia + BROTE/conversión de Prankedy (feature nuevo)

- **Asset generado:** `CAMPAIGN/MISSION3/evidencia_frasco.png` (matraz Erlenmeyer con muestra verde).
  `ZombieGameScreen.kt`: la evidencia `🧪` ahora usa `StoryGroundSprite`.
- **`features/map_exterior/viewmodel/WorldMapViewModel.kt`:** campos nuevos
  `mission3PrankedyDetectSinceMs`, `mission3PrankedyConverted`, `mission3ZombieHitCooldownMs`.
- **`features/map_exterior/viewmodel/WorldMapMission3.kt`:** en la fase 2 (INFILTRACIÓN) del cordón:
  - Constantes `M3_ZOMBIE_COUNT=3`, `M3_ZOMBIE_RING_DEG`, `M3_ZOMBIE_SPEED`, `M3_ZOMBIE_CONTACT_DEG`,
    `M3_CONVERT_MS=2500`, `M3_ZOMBIE_DAMAGE=14`, `M3_ZOMBIE_HIT_COOLDOWN_MS=1200`, `M3_PRANKEDY_ZOMBIE_HP=999999`.
  - Spawn de 3 zombis (`M3_ZOMBIE_*`, arte ESTUDIANTE) que coexisten con el sigilo de granaderos.
  - `ensureM3PrankedyEscort` (Prankedy te escolta SIN pisar el objetivo m3_), `tickM3Brote` (zombis
    persiguen a Prankedy/jugador + daño por contacto), `convertM3Prankedy` (Prankedy → **PRANKEDY zombi
    INMORTAL** que te persigue; si te mata → misión fallida), `stopM3PrankedyEscort` (limpieza/re-arme).
  - `clearMission3Story` y la entrada al ASALTO llaman `stopM3PrankedyEscort`.
- **Verificar:** cerca de la ENCB aparecen zombis + Prankedy escolta; si un zombi lo alcanza ~2.5 s
  se convierte y te persigue hasta matarte (misión fallida); al reintentar se re-arma.
- **Docs:** `CAMPAIGN/03_MISSION_3.md` fase 2/3 actualizada.

## 4. Fixes de QA (commit `cbd772a`)

### 4a. Traslape de textos en M2 fase 1 (lobby)
`ZombieGameScreen.kt`: el **widget de objetivo** y el **countdown** ("aguanta X s") se metieron en un
mismo `Column` apilado (antes eran 2 `Box` con posición fija que se encimaban con texto de varias líneas).

### 4b. NPCs del lobby ESCOM (`features/interiores/zombies/viewmodel/ZombieAmbientNpcs.kt`)
- **Skins:** `escomboy`/`escomgirl` (personajes JUGABLES) FUERA del pool ambiental; los estudiantes
  del rumor usan `EST_H1`/`EST_M1` (NPC). Pool = `IPN_1..6 + RND_1, DOC_1, EST_H1, EST_M1`.
- **Parejas muy separadas:** `TALK_START_DIST` 78→46 y al iniciar la plática cada NPC hace **snap a
  `TALK_GAP` frente a frente**. Estudiantes del rumor: separación 70→44 px.
- **Movimiento errático:** el anti-amontonamiento re-elegía objetivo CADA tick; ahora con **cooldown**
  (`CROWD_DIST=35`, `CROWD_RETARGET_MS=1500`).

### 4c. Separación de misiones + bug de reintento M3→M1
- **`features/map_exterior/viewmodel/WorldMapMissionLog.kt`:** helper `cancelOtherCampaignMissionsRuntime(keep)`
  (internal) que cancela el runtime de las OTRAS misiones (policía M1 + M2 + M3 + secundarias) sin tocar
  las fases persistidas. Se llama en **`selectCampaignMission`** y **`replayCampaignMission`**.
- **`features/map_exterior/viewmodel/WorldMapSaveGame.kt`:** `retryCampaignMission` también llama
  `cancelOtherCampaignMissionsRuntime(keep = missionIdForObjective(failedObjId))`.
- **Verificar:** cambiar/TP (dev) a otra misión y fallarla → REINTENTAR reinicia la MISMA misión, no la M1.

### 4d. Guía de la mochila (M2)
`features/map_exterior/viewmodel/WorldMapMission2.kt`: al entrar a la fase MOCHILA, prompt explícito
("Ve a la ESCOM y ENTRA… lanza la LATA APESTOSA"). (La puerta ya redirigía al salón; faltaba el aviso.)

## 5. Street Fighter — 3 fixes de jugabilidad + i18n

- **Cross-up** (`features/streetfighter/viewmodel/StreetFighterViewModel.kt`): al **aterrizar** el
  peleador ENCARA de inmediato al rival (antes solo giraba en IDLE/CROUCH → quedabas volteado tras
  brincar por encima y "adelante" apuntaba lejos).
- **Joystick/combos** (mismo VM): `JOYSTICK_IDLE_MS` 150→100 (menos lag al soltar); `HADOUKEN_WINDOW_MS`
  800→1100 y `isHadoukenSequence` ahora es **cuarto de círculo tolerante** (basta ABAJO→ADELANTE; la
  diagonal ↘ dejó de ser obligatoria).
- **Paparazzi encogen al golpear** (PARCHE TEMPORAL, data-driven): campo **`hurtScale`** en
  `domain/models/streetfighter/SfModels.kt` (`SfFighterId`), solo `PAPARAZZI_1=1.43f` y `PAPARAZZI_5=1.37f`
  (medidos; los demás ALPHA están bien). `features/streetfighter/ui/StreetFighterScreen.kt`:
  `drawSpriteAnchored` acepta `spriteScale`; `drawFighter` lo aplica **solo en estados HURT**. TODO:
  quitar al regenerar esos sprites al tamaño correcto.
- **i18n:** 14 strings `sf_*` en `res/values/strings.xml` (ES) + `res/values-en/strings.xml` (EN);
  `StreetFighterScreen.kt` usa `stringResource` para todo el texto de UI (selector, PAUSA/Continuar,
  menú fin de pelea, diálogo de salir, "Al azar", "Cambiar peleador", nota ALPHA).

## 6. Menú de misiones — botón "cerrar" arriba
`features/map_exterior/ui/components/MissionLogDialog.kt`: el título ahora es una `Row` con un **✕**
que cierra el registro (además del botón "CERRAR" de abajo).

## 7. Cómics M2→M3 (CABLEADO + PLACEHOLDERS)

Patrón idéntico al cómic de la M1 (bandera de estado → `LaunchedEffect` en AppNavGraph → ruta con `StoryIntroScreen`).
- **`domain/models/campaign/StoryComicCatalog.kt`:** 2 secuencias nuevas `MISSION2_BACKPACK_INTRO_ID`
  (paneles `IntroPOW16..18`) y `MISSION3_INTRO_ID` (`IntroPOW19..22`), con texto real (chilango).
- **Assets PLACEHOLDER:** `assets/STORY/INTRO/IntroPOW16..22.webp` (1920×1080, rotulados con la escena que
  deben mostrar + zona blanca para el texto). **HAY QUE SUSTITUIRLOS por el arte real.**
- **`features/map_exterior/viewmodel/WorldMapState.kt`:** banderas `pendingMission2BackpackComic`,
  `pendingMission3IntroComic`.
- **`features/map_exterior/viewmodel/WorldMapMission2.kt`:** se setean en `advanceM2Phase` (TALK→BACKPACK)
  y `completeMission2Backpack`; funciones `consumeMission2BackpackComic`/`consumeMission3IntroComic`.
- **`AppNavGraph.kt`:** 2 rutas (`story_mission2_backpack`, `story_mission3_intro`) + 2 `LaunchedEffect`
  (el de M3 ESPERA a estar en el mapa, no en el salón) + 3 imports. Al terminar el Beat B se **sigue la M3**.
- **Verificar:** hablar con Prankedy → cómic "La mochila" → salón → mochila → salir → cómic "Regreso a
  la ENCB" → M3 seguida.

---

## 8. Assets nuevos (resumen)

| Ruta | Qué es |
|---|---|
| `CAMPAIGN/MISSION2/lata_apestosa.png`, `mochila_prankedy.png` | Items M2 (antes emoji) |
| `CAMPAIGN/MISSION3/evidencia_frasco.png` | Evidencia M3 (generado) |
| `SPRITES/ZOMBIE/ESTUDIANTE/z_walk_1..9.webp` | Estudiante zombi (brote M2) |
| `SPRITES/ZOMBIE/PRANKEDY/z_walk_1..9.webp` | Prankedy zombi (conversión M3) |
| `STORY/INTRO/IntroPOW16..22.webp` | **PLACEHOLDERS** de cómic M2→M3 (reemplazar) |

## 9. Deuda / pendientes conocidos

- **PLACEHOLDERS de cómic** `IntroPOW16..22.webp` → generar arte real (escenas y textos ya definidos en
  `StoryComicCatalog`).
- **`SfFighterId.hurtScale`** = parche temporal; quitar al regenerar los sprites HURT de paparazzi.
- **Granaderos (M3)** siguen con sprite de policía genérico (falta ruta de render "premade" para HUMANOS
  en el exterior; solo se implementó la de ZOMBIS). Se OFRECIÓ teñirlos (ColorMatrix) — no implementado.
- **i18n:** se tradujo el SF completo. Otros textos de UI hardcodeados en español siguen pendientes; los
  **DIÁLOGOS de historia** (cómics, líneas de Prankedy, rumores) van en español POR CONVENCIÓN (no tocar).
