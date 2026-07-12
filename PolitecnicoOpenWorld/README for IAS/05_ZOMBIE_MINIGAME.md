# 05 · Capa de Zombis / Zombie layer (`features/interiores/zombies/`)

> **🆕 Reestructura:** el antiguo `features/zombie_minigame/` ahora es **`features/interiores/zombies/`**
> (subpaquete de la umbrella `interiores`). Los tipos compartidos `DesignerTarget` y `CameraTransform`
> se movieron a `interiores/core/viewmodel/InteriorDesignerModels.kt`, y `PlayerView`/`PlayerHealthBarFixed`/
> `RemotePlayerView` a `interiores/core/ui/InteriorPlayerViews.kt`. La ruta de navegación pasó de
> `"zombie_minigame"` a **`"interiores_zombies"`**. Paquetes nuevos: `Zombie*Kt` = `interiores.zombies.{ui,viewmodel}`;
> designer layers = `interiores.core.ui`. / Was `features/zombie_minigame/`; now `features/interiores/zombies/`
> under the `interiores` umbrella; shared types extracted to `interiores/core/`; route renamed to `interiores_zombies`.

**ES:** Anillo de salas: un **lobby** con puertas a cada edificio de ESCOM (7 edificios). Dentro de un
edificio, puertas EXIT conectan vecinos y una central vuelve al lobby. **Online: zombis e items son
autoritativos del servidor** (`MultiplayerInteriores/`); **offline: simulación local completa**.

> **🆕 Modo INTERIORES expandible (ESCOM, FES, UAM…):** este es el **motor de INTERIORES** de cualquier
> edificio/campus, no sólo ESCOM. La sala donde arranca la sesión la fija el arg de navegación
> **`interiores_zombies?startRoom={id}`** → `ZombieInteriorViewModel.startRoomId` (default
> `ZombieRoomCatalog.LOBBY_ID`). La puerta **"Entrada FES Aragón"** entra con `startRoom=fes_interior`.
>
> **Cómo añadir un campus (recipe):** `ZombieRoomCatalog` expone el helper **`campusRooms(lobbyId,
> lobbyDisplayName, lobbyBackground, buildings)`** (+ `data class BuildingSpec`) que genera **1 lobby
> (zona segura, sin zombis) + N edificios (con zombis)** con las puertas YA cableadas (lobby→edificio,
> edificio→lobby, lobby→mapa). **ESCOM mantiene su anillo bespoke**; los campus nuevos se agregan con un
> `addAll(campusRooms(...))`. **FES** ya está añadido así: lobby `fes_interior` (fondo `FES_Arg_int.webp`)
> + edificio **`fes_edificio`** ("Edificio Principal", **reusa TEMPORALMENTE** el fondo de ESCOM
> `za_edificio.webp`, `zombieCount=4`). El servidor replica el campus (`server.js` ROOMS: `fes_interior`
> LOBBY + `fes_edificio` BUILDING).
>
> **Lógica campus-agnóstica (sin hardcodear el lobby de ESCOM):** `ZombieInteriorViewModel.lobbyForBuilding(id)`
> resuelve el lobby de CADA edificio (puerta entrante); lo usan `spawnAtLobbyDoorFor`, el respawn de WASTED
> y el diálogo "volver al lobby" (`pendingLobbyTarget`). Antes estos clavaban `LOBBY_ID` (ESCOM).
> La **mano/activación de zombis** del lobby sigue siendo de ESCOM (gateada por `LOBBY_ID`): **offline**,
> los edificios sólo siembran zombis con el modo activado, así que la horda de FES se ve **online**
> (el server siembra en `BUILDING`); para FES offline con zombis habría que darle su propia activación.
> **🆕 Cadena STANDALONE del Modo Historia (ENCB).** Además de los campus (lobby + edificios), el catálogo
> registra una **cadena LINEAL de 4 salas sueltas**, todas tipo `LOBBY` (zona segura, `zombieCount=0`):
> `ENCB_LOBBY_ID="encb_lobby"` → `ENCB_SALON1_ID="encb_salon1"` → `ENCB_LAB1_ID="encb_lab1"` →
> `ENCB_LAB2_ID="encb_lab2"` (fondos `INTERIORS/ENCB/ENCB_{lobby,salon1,lab1,lab2}.webp`). Se construyen con el
> helper `encbStoryRoom(id, displayName, background, nextRoomId)`: cada sala lleva **UNA puerta de AVANCE**
> (`ZoneDoor` `TO_BUILDING`, hitbox arriba-centro) hacia la siguiente → al pisarla y pulsar **X**,
> `onInteract` → `goToRoom(next)` (LOBBY→LOBBY, sin diálogo). La **última (`encb_lab2`)** tiene un **waypoint
> final** cuyo `targetRoomId` es el sentinela **`EXIT_TO_STORY_OUTRO`**: `goToRoom` lo intercepta (igual que
> `EXIT_TO_WORLD`), pone `isExitingToStoryOutro=true` y `ZombieGameScreen` invoca `onPlayStoryOutro()` →
> `MainActivity` navega a `story_outro` (cómic `ENCB_OUTRO`, ver 07). **NINGUNA** sala tiene puerta `TO_WORLD`
> → sin flechas/marcadores de escape (flujo "atrapado"; la única salida directa al mapa es
> el menú de Opciones → "Salir al mapa"). Mano zombi, fondo apocalíptico, prompt "Mano Misteriosa" y horda están
> **gateados a `LOBBY_ID`/`BUILDING`**, así que en estas salas no aparecen. Se entra tras la intro con
> `interiores_zombies?startRoom=encb_lobby` (en `MainActivity`, ruta `encb_lobby`); las transiciones internas
> ocurren dentro del mismo `ZombieGameScreen` (mismo VM). El banner **"Objetivo: Investiga qué pasó"** se pinta
> cuando `room.id in ZombieRoomCatalog.ENCB_STORY_ROOM_IDS`. Ver 06/07.
> **🆕 SALÓN DE LA MISIÓN 2 (`ESCOM_SALON_M2_ID="escom_salon_m2"`, 2026-07-03):** sala STANDALONE tipo
> `LOBBY` (fondo reusa `INTERIORS/ENCB/ENCB_salon1.webp`, `playerScaleMul=3f`, ÚNICA puerta = `TO_WORLD`).
> Se entra por la puerta de la ESCOM SOLO en la fase MOCHILA de la Misión 2 (`WorldMapInteractions`
> redirige a `interiores_zombies?startRoom=escom_salon_m2` cuando `mission2Phase==PHASE_BACKPACK`). Está
> **EN CLASES**: `AMBIENT_ROOM_IDS` incluye la sala → `spawnAmbientNpcs` puebla estudiantes IPN/docente.
> **X** = lanzar la **LATA APESTOSA** (`ZombieGameState.mission2StinkThrown`) → el tick usa
> `evacuateAmbientNpcs` (corren a la puerta y desaparecen) en vez de `stepAmbientNpcs`; con el salón VACÍO
> aparece la **mochila** (asset `CAMPAIGN/MISSION2/mochila_prankedy.png` en `mission2BackpackX/Y`; la lata
> usa `CAMPAIGN/MISSION2/lata_apestosa.png`, ambas vía `Mission2GroundSprite`); **X** cerca la recoge
> (`mission2BackpackTaken`) → `ZombieGameScreen` dispara `onMission2BackpackRecovered` →
> `completeMission2Backpack()` en el VM del mundo (cableado en AppNavGraph). `loadRoom` re-arma la escena
> al reentrar (si saliste sin la mochila, vuelve a haber clase). El objetivo del salón lo muestra
> `interiorObjective = M2_RECUPERAR_MOCHILA` (ObjectivesWidget). Ver `CAMPAIGN/02_MISSION_2.md`.
> **🆕 MISIÓN 2 · FASE 1 "ESCONDERSE" EN EL LOBBY (2026-07-08):** la fase 1 se juega DENTRO del
> lobby de ESCOM (`LOBBY_ID`): policías **`m2cop_*`** (skin `POLICIA_CDMX`) patrullan como NPCs
> ambientales (`spawnMission2HideCops`/`stepMission2HideCops` en `ZombieAmbientNpcs.kt`; cada
> ~7 s UNO barre hacia el jugador). Detección: < `HIDE_DETECT_PX` (120 px) sostenido
> `HIDE_DETECT_MS` (2.5 s) → `mission2HideFailed`; aguantar `HIDE_DURATION_MS` (35 s) → se
> rinden y EVACÚAN (reusa `evacuateAmbientNpcs`) → `mission2HideCompleted`. Countdown en el HUD
> (`mission2HideRemainingSec` + string `zgame_hide_countdown`). El armado es en RUNTIME
> (`setMission2Hide`, NO Factory param) y los desenlaces van por callbacks
> `onMission2HideCompleted/Failed` (AppNavGraph → `completeMission2Hide`/`failMission2Hide`).
> Ver 09 (reglas) y `CAMPAIGN/02_MISSION_2.md`.
> **🆕 VIDA UNIVERSITARIA 2.0 (2026-07-08):** `ambientCountFor(room)` = lobby 13 / salón M2 8 /
> default 7; las pláticas siguen **GUIONES coherentes** (`AMBIENT_CONVOS`, elegidos determinista
> por `pairSeed`; línea actual por `talkStartMs`; strings `amb_convo{1..6}_{1..4}` ES+EN) en vez
> de frases sueltas; 2 parejas nacen YA platicando en el lobby; `PAIR_CHANCE_PER_TICK` 0.004→0.009.
> **🆕 MISIÓN 3 · ASALTO A LA ENCB (2026-07-04):** con `mission3Assault=true` (Factory param, lo
> decide AppNavGraph cuando `startRoom=encb_lobby` y `mission3Phase==PHASE_ASSAULT`), la cadena
> ENCB se siembra con **zombis** (`ASSAULT_ZOMBIES_PER_ROOM=4`, IGNORA el gate de
> `zombieModeActivated`) y la **EVIDENCIA 🧪** aparece en `encb_lab1` (`mission3Evidence*` en el
> estado); recogerla (X) → `onMission3EvidenceRecovered` + **auto-salida** al mapa (2.6 s, la
> cadena no tiene puerta TO_WORLD). El banner "Investiga qué pasó" se SUPRIME cuando hay
> `interiorObjective` (el asalto muestra el suyo). **INVENTARIO desbloqueable:** slots usables =
> `state.inventoryUnlockedSlots` (1 → 4 con la mochila de la M2). **ARMA DE FUEGO:** modo RANGED
> bloqueado en campaña sin `hasFirearm` (recompensa M3); candado 🔒 en el menú de armas.
> **🆕 COMBATE CONTRA NPCs AMBIENTALES (2026-07-11, paridad con el exterior):** los estudiantes/
> docentes YA reciben golpes (antes eran intocables). `AmbientNpc` ganó `health/isDying/
> dyingSinceMs/fleeUntilMs`. Melee: `performPlayerAttack` (ZombieCombat.kt) asusta a los cercanos
> en CADA golpe (`scareAmbientNpcs`, radio `AMBIENT_FEAR_RADIUS`=150 px — como `triggerFear`
> exterior) y, si no hay zombi al alcance, daña al más cercano (`hitNearestAmbientNpc`: daño +
> knockback + miedo `AMBIENT_FLEE_MS`=4 s; a 0 HP → `isDying` ~1 s tirado y desaparece). Los
> PROYECTILES también les pegan (tick, `workingAmbient`). Con miedo HUYEN corriendo del jugador
> (rama nueva en `stepAmbientNpcs`; cancela pareja/burbuja). **INMUNES los NPCs de MISIÓN**
> (`isMissionNpc()`: `m2rumor_`/`m2cop_`) para no romper la M2 — misma protección que Prankedy
> HIRED. Render: barrita de vida si está dañado y colapso rotado 90° al morir (ZombieGameScreen).
> Son NPCs LOCALES: nada viaja al servidor.

**EN:** Ring of rooms: a **lobby** with doors to each ESCOM building (7 buildings). Inside a building,
EXIT doors connect neighbors and a central door returns to the lobby. **Online: zombies and items are
server-authoritative** (`MultiplayerInteriores/`); **offline: full local simulation**.

Modelos de dominio (ZombieEntity, SkillEffect, ZombieRoom, CollisionMatrix…) → ver **03**.

> **🆕 NPCs civiles + coords server (online):** el servidor de interiores spawnea ~6 **civiles** por
> sala que **deambulan/huyen** de los zombis y, si los atrapan, **se convierten en zombi**. Viajan en
> `ZOMBIE_STATE.npcs` (`NetInteriorNpc`); el cliente los guarda en `ZombieGameState.interiorNpcs` (reusa
> `RemoteZombiePlayer`) y los **renderiza con `RemotePlayerView`** (figura humana, sin sprites nuevos).
> Además el **movimiento del jugador es validado por el servidor**: si cae en pared, llega `PLAYER_CORRECT`
> y `handleServerMessage` ajusta `playerX/playerY`. Detalle servidor → ver **08**. El servidor ahora se
> llama **`MultiplayerInteriores/`** (antes `MultiplayerZombie/`).

---

## Arquitectura: DOS motores de interiores (no confundir)
- **`features/interiores/escom/viewmodel/InteriorViewModel.kt`** = interiores **simples** basados en grid
  (auditorio, biblioteca, cafetería, canchas…), **sin zombis**. (Por eso el VM de abajo NO puede llamarse
  `InteriorViewModel`: ese nombre ya está tomado.)
- **`features/interiores/zombies/viewmodel/ZombieInteriorViewModel.kt`** = interior de **supervivencia con
  ZOMBIS** (salas de edificio ESCOM + FES, en píxeles, con combate/MP/puzzle de llave). La **lógica de
  INTERIOR** (salas, movimiento, puertas, red, llave) vive en el VM; la **CAPA ZOMBI** está separada en
  `ZombieCombat.kt` (combate) y `ZombieGameTick.kt` (simulación). Hoy el modo zombi en interiores solo
  corre en ESCOM/FES.

## Key files

| Tema / Concern | Archivo / File |
|---|---|
| Lógica/estado/red INTERIOR (salas, movimiento, puertas, puzzle llave, networking) | `viewmodel/ZombieInteriorViewModel.kt` (~996 líneas; RENOMBRADO desde ZombieGameViewModel) |
| 🆕 CAPA ZOMBI — combate (melee, disparo, muerte+drop, efectos/skills) | `viewmodel/ZombieCombat.kt` (NUEVO, refactor — extensiones del VM) |
| Tick (offline/online, movimiento zombi, knockback) | `viewmodel/ZombieGameTick.kt` |
| 🆕 Modo Diseñador (matriz colisión + waypoints: pintar/redimensionar, mover puertas, guardar/exportar/importar) | `viewmodel/ZombieGameDesigner.kt` (NUEVO, refactor — extensiones del VM) |
| Constantes de gameplay | `viewmodel/ZombieGameConstants.kt` |
| Estado UI | `viewmodel/ZombieGameState.kt` |
| Modelos de red (cliente) | `viewmodel/Zombienetmodels.kt` |
| Render + cámara + FX de daño | `ui/ZombieGameScreen.kt` |
| HUD (vida, menú de arma, toasts) | `ui/ZombieHud.kt` |
| Sprites zombi | `ui/ZombieSpriteManager.kt` |
| 🆕 Autos aparcados del lobby (render escenografía) | `ui/ParkedCarsLayer.kt` |
| 🆕 Calibrador del estacionamiento (UI, solo dev) | `ui/ParkingDesignerTool.kt` (`ParkingTuneTool`) |
| 🆕 Estacionamiento: fuente única de datos (multi-campus) | `domain/models/map/CampusParkingCatalog.kt` |
| Diseñador de matriz/waypoints (capas) | `ui/components/Collisionmatrixdesignerlayer.kt`, `WaypointDesignerLayer.kt` |

---

## Estado / State — `ZombieGameState.kt`

`currentRoomIndex, pendingSpawnX/Y, playerX, playerY, playerHealth(=100), playerAction,
isPlayerFacingRight, isRunning, showPlayerHealthBar, damagePulseTrigger, aimDirX, aimDirY,
zombies: List<ZombieEntity>, items: List<SkillItem>, projectiles: List<Projectile>, totalZombies,
zombiesRemaining, activeEffects: List<ActiveEffect>, effectToast, combatMode(MELEE/RANGED),
showWeaponMenu, showVictoryScreen, showWastedScreen, isExitingToWorld, showExitToLobbyDialog,
showExitGuide, nearbyDoorLabel, nearbyItemId, pickupToast,
keys: List<KeyDrop>, nearbyKeyId, lab1KeyFound, keyMessage, showInventory, inventoryKeys: List<String>,
controlType(=JOYSTICK), controlsScale,
swapControls, isLoading, remotePlayers, zombieModeActivated, showZombieCinematic,
designerMode, designerRows, designerBrush(WALL/OCCLUDER/ERASE), designerDirty, designerTarget(MATRIX/WAYPOINTS),
designerDoors, selectedDoorIndex`.

> **🆕 Pincel del diseñador de matriz = enum `DesignerBrush { WALL, OCCLUDER, ERASE }`** (antes era
> `designerBrushWall: Boolean`). `setDesignerBrush(brush)` (extensión en `ZombieGameDesigner.kt`) y
> `paintCellAtWorld` pintan `'#'` / `'^'` / `'.'`. La toolbar tiene 3 botones: **PARED** (rojo), **OBJETO**
> (azul, `'^'`) y **BORRAR** (verde). `CollisionMatrixDesignerLayer` dibuja `'#'` en rojo y `'^'` en azul.

```kotlin
enum class DesignerTarget { MATRIX, WAYPOINTS }
data class CameraTransform(offsetX, offsetY, scale)   // zoom-aware, ContentScale.Crop equivalente
```

## Constantes / Constants — `ZombieGameConstants.kt` (todas `internal const`)

```
PLAYER_WALK_STEP=7  PLAYER_RUN_STEP=13  PLAYER_RADIUS=28
ZOMBIE_SPEED=1.3  ZOMBIE_FRAME_COUNT=9  ZOMBIE_FRAME_INTERVAL_MS=140  ZOMBIE_RADIUS=30
STALKER_WALK_FRAME_COUNT=4  STALKER_ATTACK_FRAME_COUNT=4  STALKER_ATTACK_DIST=85
CONTACT_DIST=44  ZOMBIE_DAMAGE=12  ZOMBIE_DAMAGE_COOLDOWN_MS=3000  LOBBY_REGEN_PER_TICK=0.35
PLAYER_PUNCH_DAMAGE=34  PLAYER_ATTACK_RADIUS=120  PLAYER_ATTACK_COOLDOWN_MS=600
MELEE_KNOCKBACK=46  PROJECTILE_KNOCKBACK=34  PLAYER_RECOIL=10
PROJECTILE_SPEED=22  PROJECTILE_LIFETIME_MS=1500  PROJECTILE_DAMAGE=50  PROJECTILE_HIT_RADIUS=36
RANGED_COOLDOWN_MS=350  Y_HOLD_FOR_MENU_MS=500
INVENTORY_UNLOCKED_SLOTS=1  INVENTORY_TOTAL_SLOTS=4
SPAWN_RADIUS_MIN=280  SPAWN_RADIUS_MAX=520  TICK_MS=33  ITEM_PICKUP_DIST=70  RETURN_SPAWN_OFFSET=40
EXIT_GUIDE_DURATION_MS=2000  SKILL_DROP_CHANCE=0.45
SLOW_ZOMBIE_FACTOR=0.45  FAST_ZOMBIE_FACTOR=1.9  ZOMBIE_DMG_FURY_FACTOR=2.0
ZOMBIE_DMG_WEAK_FACTOR=0.4  PLAYER_DMG_BRUTE_FACTOR=2.2  NET_SEND_INTERVAL_MS=100
```

## Efectos (SkillEffect) → multiplicadores / effect → multipliers

| Efecto | Trap | Dur (ms) | Efecto en gameplay |
|---|---|---|---|
| `CURA_TOTAL` | no | 0 | Cura instantánea |
| `RELOJ_ARENA` | no | 8000 | Zombis lentos (`SLOW_ZOMBIE_FACTOR` 0.45) |
| `ADRENALINA_ZOMBI` | **sí** | 7000 | Zombis rápidos (`FAST` 1.9) — trampa |
| `FURIA_ZOMBI` | **sí** | 7000 | Daño zombi ×2 (`FURY`) — trampa |
| `DEBILIDAD_ZOMBI` | no | 8000 | Daño zombi ×0.4 (`WEAK`) |
| `FUERZA_BRUTA` | no | 9000 | Daño jugador ×2.2 (`BRUTE`, vía `playerDamageFactor()`) |

---

## Tick — `ZombieGameTick.kt`

`tick()` (cada `TICK_MS`): manda `sendPlayerUpdate(now)` SIEMPRE primero; si hay pantalla
bloqueante o modo diseñador → no simula; si `isMultiplayer` → `tickOnline` else `tickOffline`.

**`tickOffline(s, now)`** (simulación local):
```
- expira efectos; calcula speedFactor (RELOJ_ARENA/ADRENALINA) y dmgFactor (FURIA/DEBILIDAD).
- por cada zombi vivo: moveZombie(...); si dist<=CONTACT_DIST y pasó cooldown → daño al jugador (+pulse).
- proyectiles: avanzan; si pegan a un zombi → daño*playerDamageFactor + knockback; si muere → muerte diferida 1s.
- si vida<=0 → triggerWastedSequence().
- item cercano (<=ITEM_PICKUP_DIST) → nearbyItemId.
- _state.update(zombies, projectiles, playerHealth clamp 0..100, zombiesRemaining, ...).
```

**`tickOnline(s, now)`** (servidor autoritativo):
```
- proyectiles: al impactar a un zombi del servidor → sendZombieDamage(id, dmg); NO se mueve el zombi local.
- daño de contacto local (vida es local), con contactCooldown por zombi.
- regen de lobby: en LOBBY_ID y vida<100 → +LOBBY_REGEN_PER_TICK.
- NO tocar zombies/items (autoritativos). Solo actualiza projectiles/vida/pulse/nearbyItem/efectos.
```

**`moveZombie(z, px, py, now, room, speedFactor)`** — persecución directa con **sliding por eje**:
normaliza (dx,dy); `step = ZOMBIE_SPEED*speedFactor` si dist>CONTACT_DIST*0.7, si no 0; intenta
(tx,ty), si bloqueado prueba (tx, y) o (x, ty); STALKER ataca a `STALKER_ATTACK_DIST` (anim distinta);
avanza frame cada `ZOMBIE_FRAME_INTERVAL_MS`.

**`knockbackZombie(zx, zy, fromX, fromY, room, dist)`** — empuja al zombi alejándolo del origen, con
el mismo sliding por eje, respetando colisiones.

---

## `ZombieInteriorViewModel.kt` — API

**Red / network (online):** `connectIfNeeded()`, `sendJoinRoom()`, `handleServerMessage(json)`,
`upsertRemote(m)`, `pushRemotePlayersToState()`, `sendPlayerUpdate(now)`,
`sendZombieDamage(zombieId, damage)`, `sendItemPickup(itemId)`, `applyServerZombieState(msg)`.
Coordenadas del servidor son **fraccionarias [0,1]** → el cliente las convierte a píxeles de la sala.
Mensajes → ver **08**.

**Salas / rooms:** `currentRoom()`, `loadRoom(index)`, `goToRoom(targetRoomId)`,
`exitToWorld()=goToRoom(EXIT_TO_WORLD)`, `spawnAtLobbyDoorFor(fromBuildingId)`,
`spawnAroundPlayer(px, py, room)`, `isWalkable(x, y)`, `updateDoorPrompt(px, py)`.

**Combate / combat:** `performPlayerAttack()` (melee, `PLAYER_ATTACK_RADIUS` 120, daño 34),
`fireProjectile()`, `onZombieDeath(dead)` (drop de item 45%), `applyEffect(effect)` /
`applyEffectByName(name)` / `effectFromName(name)`, `hasEffect(e)`, `playerDamageFactor()`,
`selectCombatMode(mode)`, `setSpecial(pressed)` (B), `dismissWeaponMenu()`.

**Controles (interiores) / interior controls:** **A** = `onRun(pressed)` (MANTENER + moverse = correr,
momentáneo; queda libre al estar quieto). **Y** mantenido 500 ms (`onSecondaryPressed/Released`) abre el
**MENÚ COMBINADO**: arriba el MODO DE GOLPE (`selectCombatMode`, melee/ranged) y abajo el INVENTARIO;
`dismissInventory()` cierra. (Ya NO hay menú de armas separado; `showWeaponMenu`/`onPrimary*` quedaron muertos.)

**Puzzle de llave + inventario (Misión 1, ENCB_lab1 → lab2) / key puzzle:** `spawnLab1Keys(room)` siembra 5
llaves en **ENCB_lab1** ELIGIENDO celdas CAMINABLES (no `#`) de `room.collisionMatrix` (matriz `encb_lab1` de
`assets/collision_matrices.json`; `CollisionMatrixRepository.readStore` hace MERGE asset+local). `KeyDrop`
(assets `CAMPAIGN/KEYS/`, correcta `LLave4.png`, **campo `missionId`=`KeyDrop.MISSION_1`="mission1"**). **🆕 FLUJO
(2026-06-22):** `onInteract` RECOGE la llave cercana (`nearbyKeyId`) al inventario (`inventoryKeys`, 1 slot). La
puerta `EXIT_NEXT` de **lab1 es AVANCE LIBRE** (ya no prueba ahí). La PRUEBA es en **ENCB_lab2** desde el INVENTARIO
(mantén Y): botón **Probar** → `testInventoryKey(entry)` solo "abre" en lab2 → correcta marca `lab1KeyFound=true`
(se conserva la llave); incorrecta avisa. La puerta del fondo de lab2 (`EXIT_NEXT` → `EXIT_TO_STORY_OUTRO`, dispara
la 2ª secuencia de cómic) está **GATEADA** (`onInteract` bloquea con aviso si `!lab1KeyFound`).
**🆕 Inventario identificable por MISIÓN + reglas de UI (2026-06-22 PM):** cada entrada de `inventoryKeys` se
guarda como **`"missionId|assetPath"`** (`KeyDrop.inventoryEntry/entryAsset/entryMission`; compat: sin `|` ⇒
MISSION_1) → la llave es identificable por misión y eso **se persiste** en la partida (reuso de assets a futuro).
**DESECHAR = MANTENER PULSADA la llave** en su slot (`detectTapGestures(onLongPress)` → `onDiscardKey(entry)`); ya
NO hay botón Desechar. Regla (en `discardInventoryKey`): una llave **incorrecta** se desecha SIEMPRE; la
**correcta** solo **DESPUÉS de usarla** para abrir la puerta (`lab1KeyFound`), antes NO. El botón **Probar**
solo se muestra mientras `!lab1KeyFound`. El inventario se cierra con un **✕ en la esquina superior derecha**.
Render `KeyGroundItem` (suelo) / `InventoryKeyIcon(entryAsset(...))` (slot). Se GUARDA en `GameSaveData`
(`inventoryKeys`/`lab1KeyFound`) vía `WorldMapViewModel.currentInteriorInventory/…Lab1KeyFound` (`LaunchedEffect`
en `ZombieGameScreen` → `onInteriorProgress` persiste CADA cambio).

**Movimiento / UI:** `moveByAngle(angleRad)`, `moveDirection(direction)`, `applyMovement(...)`,
`setRunning(running)`, `onInteract()`, `confirmExitToLobby/dismissExitToLobby`,
`triggerWastedSequence()`, `showVictory()`, `consumeExit()`, `onZombieCinematicDismissed()`.

**Modo diseñador / designer (🆕 ahora en `ZombieGameDesigner.kt` como EXTENSIONES del VM, no miembros):**
`toggleDesignerMode()`, `setDesignerTarget(target)`, `setDesignerBrushWall(wall)`,
`paintCellAtWorld(x, y)`, `resizeDesignerMatrixBy(dCols, dRows)`, `saveDesignerMatrix/resetDesignerMatrix`,
puertas (`selectDoorAtWorld`, `moveSelectedDoorToWorld`, `saveDesignerWaypoints`, `resetDesignerWaypoints`),
import/export a Uri (`exportMatricesToUri/importMatricesFromUri`, `exportWaypointsToUri/importWaypointsFromUri`),
`defaultDesignerRows(room)`. Como son extensiones, `ui/ZombieGameScreen.kt` las **importa explícitamente**
(15 imports) — incluidas las referencias acotadas `viewModel::paintCellAtWorld` (Kotlin permite `::` a
extensiones). No quedó gemelo miembro. `Factory(...)` sigue en el VM.

## Modelos de red cliente / client net models — `Zombienetmodels.kt`

```kotlin
data class RemoteZombiePlayer(id, displayName, x/*px*/, y/*px*/, action: PlayerAction, facingRight, health)
data class NetZombie(id, x/*frac*/, y/*frac*/, health, maxHealth, facingRight, frameIndex, isDying, isLootCarrier)
data class NetItem(id, x/*frac*/, y/*frac*/, effect: String)
data class ZombieServerMessage(type, sessionId, id, displayName, roomId, zone, x, y, action,
  facingRight, health, players: List<...>?, zombies: List<NetZombie>?, items: List<NetItem>?,
  totalZombies, effect, zombieId, cleared)   // Gson laxo: ausentes → null
```

## Render — `ZombieGameScreen.kt` / `ZombieHud.kt`
- `CameraTransform` consciente del zoom, clamp a límites, `max(viewW/worldW, viewH/worldH)`.
- **🆕 CAPA DE OCLUSIÓN (profundidad):** tras dibujar al jugador (y dentro de `!designerMode`), una `Canvas`
  redibuja el trozo del fondo de las celdas `'^'` de `room.collisionMatrix` ENCIMA del jugador cuando el
  OBJETO está DELANTE (su base al sur de `state.playerY`). Las celdas `'^'` contiguas se agrupan en objetos
  (4-conexo, `computeOccluders`, memoizado por matriz) para compartir la **Y-base** (borde inferior) → y-sort
  por objeto, no por celda. Coste ~0 en salas sin `'^'` (no compone la capa). Ocluye al **jugador local**
  (no a zombis/remotos; ampliable). Los `HUD`/controles se dibujan después → nunca los tapa.
- FX de daño: screen shake, viñeta roja que **escala con HP perdido** (`damagePulseTrigger`), pulso de
  vida baja, knockback a zombis, recoil del jugador. Iluminación dinámica en interiores oscuros.
- Pantallas WASTED / Victory. SkillEffects dibujados como iconos Canvas puros.
- **🆕 Botonera arriba-derecha:** Ajustes (siempre) + el menú de **Opciones**. **"Elegir personaje"**
  (selector de skin, `wm_choose_character` → `toggleSkinSelector`) **ya NO es un botón suelto**: es el
  **primer ítem del menú de Opciones** (aquí SÍ visible para el jugador; el del MAPA GLOBAL pasó a ser
  solo de Modo Desarrollador, 2026-07-04b). 🆕 2º ítem (solo campaña): **"Misiones"**
  (`wm_opt_missions` → callback `onRequestMissionLog`, non-null solo en campaña) abre el REGISTRO DE
  MISIONES global (`MissionLogHost` a nivel AppNavGraph; ver 09). El banner de OBJETIVO de la cadena
  ENCB sigue arriba-centro (`ENCB_STORY_ROOM_IDS`).
- **🆕 Orientación SIEMPRE landscape in-game (solo por RUTA):** el juego (mapa global, interiores y cómics) se
  fuerza a horizontal; solo los **menús de ruta** (`main_menu`, `story_mode`, `settings`, `collectibles`)
  permiten vertical. ÚNICA fuente de verdad: **`MainActivity`** por destino de navegación
  (`NavController.OnDestinationChangedListener`, `requestedOrientation = SCREEN_ORIENTATION_SENSOR_LANDSCAPE`
  in-game; `UNSPECIFIED` en menús). **El menú de Opciones in-game NO cambia la orientación** (es un overlay
  dentro de la ruta de juego, no una ruta): se probó rotar al abrirlo pero resultó molesto, así que las
  pantallas **NO** fijan orientación. Para rotar (incl. Ajustes) se usa su propia RUTA. Ver 09.
- **🆕 Panel del Diseñador movible/redimensionable:** `DesignerToolbar` lleva un **asa "⠿ Mover"** (arrástrala;
  toca = recentrar) y botones **−/+** que escalan el panel (`graphicsLayer`, 0.5–1×) para que no tape la sala.
- **🆕 Diseñador — acciones SIEMPRE accesibles:** el **botón de SALIR** del modo diseñador está SIEMPRE
  visible (IconButton rojo arriba-derecha, junto a Ajustes), porque la toolbar inferior se recortaba en
  MATRIZ. Además, en `DesignerToolbar` las **acciones (Guardar/Reset · Exportar/Importar/Salir) van ANCLADAS
  abajo, FUERA del scroll**; solo el bloque del medio (MATRIZ/WAYPOINTS, pincel, tamaño) es desplazable
  (`Column(weight(1f, fill=false).verticalScroll)`), así Guardar/Exportar nunca se ocultan.
- **🆕 Animación de ATAQUE acotada (RANGED):** al **disparar moviéndote**, la animación de ataque se quedaba
  pegada (move() reescribía `SPECIAL` en bucle y su reset era condicional). Ahora `SPECIAL` de RANGED dura una
  **ventana de tiempo** (`attackAnimUntilMs`, ~200 ms); `move()` solo mantiene `SPECIAL` en MELEE mientras
  sostienes el botón, o en RANGED dentro de la ventana. Al detenerte vuelve a IDLE salvo MELEE sostenido.

---

## 🚗 Autos aparcados en el lobby (escenografía + calibrador)

**ES:** El lobby de un campus usa el MISMO asset top-down que el mapa global (ESCOM = `building_escom.webp`), así
que una coordenada local 0-1 cae igual en ambos. La función está **SEPARADA en 3 piezas** (no todo en un archivo):

- **`domain/models/map/CampusParkingCatalog.kt`** — FUENTE ÚNICA de datos: `assetMatch → {navGraphAsset,
  baseWidthMeters, baseHeightMeters}` + extensión `LandmarkNavGraph.parkingSlots()` → `ParkingSlot(localX, localY,
  dirX, dirY)` (dir = nodo previo→cajón). Solo lo lee el interior; el exterior queda intacto.
- **`features/interiores/zombies/ui/ParkedCarsLayer.kt`** — ESCENOGRAFÍA (render + carga). Un auto por slot
  `isParkingSlot`, sin colisión ni IA. Posición = `cam.offset + local*worldW/H*cam.scale`; tamaño por metros
  (`CAR_FOOTPRINT_METERS/baseWidthMeters*worldWidth`). Carga+teñido (`VehicleSpriteManager`) en `Dispatchers.IO`
  vía `produceState`. **Rotación BASE heredada del global:** cada auto arranca con la MISMA orientación que deriva
  el exterior (`NpcAiManager.spawnParkedCar`: sentido del carril), calculada en el marco del PNG sin rotar
  (`atan2(dirY*baseH, dirX*baseW)`). Encima se aplica una transformación de GRUPO + volteo por auto.
- **`features/interiores/zombies/ui/ParkingDesignerTool.kt`** — UI de CALIBRACIÓN (solo Modo Desarrollador). Se
  abre desde el botón "Diseñador" → **selector** (Colisiones/Waypoints | **Estacionamiento**). Edita una
  transformación de grupo estilo PowerPoint (rotar pivote, girar c/auto sobre su eje, mover fino/grueso, escalar)
  + **voltear ↑↓ por auto TOCÁNDOLO** (para islas con autos en sentidos opuestos), con **EXPORTAR** (SAF JSON:
  `headingDeg/selfRotationDeg/offsetX/Y/scale/flipped`) y **CERRAR**. Al diseñar NO se cullea (lote 100% poblado).
  El estado de calibración vive en `ZombieGameScreen` (provisional; se afinará/persistirá después).

**EXPANDIBLE (FES, UAM…):** añadir una universidad = 1 línea en `CampusParkingCatalog.campuses` (asset + navGraph
+ baseW/H) + su navGraph en `assets/CONFIG/navgraphs/` + usar su asset top-down como fondo del landmark exterior Y
del lobby. Sin tocar el VM ni el exterior.

**EN:** A campus lobby reuses the SAME asset as the world map (0-1 local coords map identically). Split in 3:
`CampusParkingCatalog` (single data source), `ParkedCarsLayer` (scenery render; each car inherits the global's
lane rotation as its base), `ParkingDesignerTool` (dev calibration UI: group transform + per-car ↑↓ flip + export).
The exterior is untouched — only the interior reads the catalog.
