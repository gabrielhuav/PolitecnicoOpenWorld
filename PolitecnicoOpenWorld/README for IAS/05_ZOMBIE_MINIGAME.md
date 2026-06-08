# 05 · Minijuego Zombi / Zombie minigame (`features/zombie_minigame/`)

**ES:** Anillo de salas: un **lobby** con puertas a cada edificio de ESCOM (7 edificios). Dentro de un
edificio, puertas EXIT conectan vecinos y una central vuelve al lobby. **Online: zombis e items son
autoritativos del servidor** (`MultiplayerInteriores/`); **offline: simulación local completa**.
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
showExitGuide, nearbyDoorLabel, nearbyItemId, pickupToast, controlType(=JOYSTICK), controlsScale,
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
`selectCombatMode(mode)`, `onSecondaryPressed/Released` (Y mantenido 500 ms → menú de arma),
`setSpecial(pressed)` (B), `dismissWeaponMenu()`.

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
