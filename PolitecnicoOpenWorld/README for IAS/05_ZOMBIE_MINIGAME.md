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
> **`interiores_zombies?startRoom={id}`** → `ZombieGameViewModel.startRoomId` (default
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
> **Lógica campus-agnóstica (sin hardcodear el lobby de ESCOM):** `ZombieGameViewModel.lobbyForBuilding(id)`
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

## Key files

| Tema / Concern | Archivo / File |
|---|---|
| Lógica/estado/red | `viewmodel/ZombieGameViewModel.kt` (~1070 líneas) |
| Tick (offline/online, movimiento zombi, knockback) | `viewmodel/ZombieGameTick.kt` |
| Constantes de gameplay | `viewmodel/ZombieGameConstants.kt` |
| Estado UI | `viewmodel/ZombieGameState.kt` |
| Modelos de red (cliente) | `viewmodel/Zombienetmodels.kt` |
| Render + cámara + FX de daño | `ui/ZombieGameScreen.kt` |
| HUD (vida, menú de arma, toasts) | `ui/ZombieHud.kt` |
| Sprites zombi | `ui/ZombieSpriteManager.kt` |
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
designerMode, designerRows, designerBrushWall, designerDirty, designerTarget(MATRIX/WAYPOINTS),
designerDoors, selectedDoorIndex`.

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

## `ZombieGameViewModel.kt` — API

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

**Puzzle de llave + inventario (ENCB_lab1) / key puzzle:** `spawnLab1Keys(room)` siembra 5 llaves
ELIGIENDO celdas CAMINABLES (no `#`) directamente de `room.collisionMatrix` (la matriz `encb_lab1` de
`assets/collision_matrices.json`; `CollisionMatrixRepository.readStore` ahora hace MERGE asset+local para
que la matriz de fábrica SIEMPRE se cargue). `KeyDrop`, assets `CAMPAIGN/KEYS/`, correcta `LLave4.png`;
`onInteract` RECOGE la
llave cercana (`nearbyKeyId`) al inventario (`inventoryKeys`, 1 slot usable) y, en la puerta
`EXIT_NEXT` de lab1, PRUEBA la del inventario (correcta → `lab1KeyFound=true` abre; incorrecta → se
descarta). Render `KeyGroundItem` (suelo) / `InventoryKeyIcon` (slot, imagen real). Se GUARDA en
`GameSaveData` (`inventoryKeys`/`lab1KeyFound`) vía `WorldMapViewModel.currentInteriorInventory/…Lab1KeyFound`.

**Movimiento / UI:** `moveByAngle(angleRad)`, `moveDirection(direction)`, `applyMovement(...)`,
`setRunning(running)`, `onInteract()`, `confirmExitToLobby/dismissExitToLobby`,
`triggerWastedSequence()`, `showVictory()`, `consumeExit()`, `onZombieCinematicDismissed()`.

**Modo diseñador / designer:** `toggleDesignerMode()`, `setDesignerTarget(target)`,
`setDesignerBrushWall(wall)`, `paintCellAtWorld(x, y)`, `resizeDesignerMatrixBy(dCols, dRows)`,
`saveDesignerMatrix/resetDesignerMatrix`, puertas (`selectDoorAtWorld`, `moveSelectedDoorToWorld`,
`saveDesignerWaypoints`, `resetDesignerWaypoints`), import/export a Uri
(`exportMatricesToUri/importMatricesFromUri`, `exportWaypointsToUri/importWaypointsFromUri`),
`defaultDesignerRows(room)`. `Factory(...)`.

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
- FX de daño: screen shake, viñeta roja que **escala con HP perdido** (`damagePulseTrigger`), pulso de
  vida baja, knockback a zombis, recoil del jugador. Iluminación dinámica en interiores oscuros.
- Pantallas WASTED / Victory. SkillEffects dibujados como iconos Canvas puros.
- **🆕 Botonera arriba-derecha:** Ajustes (siempre) + el menú de **Opciones**. **"Elegir personaje"**
  (selector de skin, `wm_choose_character` → `toggleSkinSelector`) **ya NO es un botón suelto**: es el
  **primer ítem del menú de Opciones**. El banner de OBJETIVO de la cadena ENCB sigue arriba-centro
  (`ENCB_STORY_ROOM_IDS`).
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
