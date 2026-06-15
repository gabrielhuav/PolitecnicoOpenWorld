# 03 · Modelos de dominio + IA / Domain models + AI (`domain/models/`)

**ES:** Kotlin puro. **Sin imports de Android** (excepto `org.osmdroid.util.GeoPoint`, que se usa
como tipo de coordenada) **ni UI**. Aquí vive la lógica de IA pura (`NpcAiManager`, `PoliceManager`).
**EN:** Pure Kotlin. **No Android imports** (except `org.osmdroid.util.GeoPoint` used as a coordinate
type) **and no UI**. Pure AI logic lives here (`NpcAiManager`, `PoliceManager`).

---

## Modelos OSM / OSM primitives

```kotlin
data class MapNode(id: Long, lat: Double, lon: Double)
data class MapWay(id: Long, nodes: List<MapNode>, isForCars: Boolean, isForPeople: Boolean)
data class MetroStation(name: String, routes: List<String>, location: GeoPoint)
```

## NPCs / Vehículos

```kotlin
enum class CarModel(dirName, prefix) { SEDAN, SPORT, SUPERCAR, SUV, VAN, WAGON }  // 6 modelos, assets WHITE_*
enum class NpcType(drawableName) { PERSON, CAR, POLICE_CAR, POLICE_COP, ZOMBIE }  // ZOMBIE = apocalipsis global
enum class NpcNavState { MACRO_OSM /*calles reales*/, MICRO_LANDMARK /*dibujo del asset*/, PARKED }
enum class NpcTrait { PASSIVE, COWARD, AGGRESSIVE }   // personalidad asignada al spawn
```

**`Npc`** (`var` mutables para el game loop; campos clave):
`id, type, location: GeoPoint, rotationAngle, speed, currentWay, targetNodeIndex, moveDirection,
carColor: Int, carModel, isRemote, ownerId, isMoving, facingRight, visualConfig:
CharacterVisualConfig?, displayName, health: Float=100, isDying, navState, currentLocalWay,
currentLandmark, fearUntil/fearFromLat/fearFromLon (huida), chatUntil/chatPartnerId (charlas),
trait, aggroUntil (embestida), policeDisembarked, policeCanShoot, policeCarId, policeReturning,
callingUntil, committedTargetNodeIndex: Int? = null, commitmentTicks: Int = 0,
speedVariation: Float = 1.0f (aleatorio 0.8–1.2 al spawn),
isPoliceSkin: Boolean = false (coche tipo CAR que se DIBUJA como patrulla; lo deja el coche al bajarte
de una patrulla robada — la IA lo conduce como tráfico normal y no lo despawnea; cosmético/local)`.

> Los campos de IA (trait, fear*, aggro*, chat*) **no se serializan** al servidor; solo `health`,
> `isDying`, `aggroUntil` viajan en `NPC_BATCH_UPDATE`. / AI fields are not serialized; only
> `health`/`isDying`/`aggroUntil` travel on the wire.

## Personaje / Character

```kotlin
data class CharacterVisualConfig(bodyFolder, bodyPrefix, hairId: Int,
                                 hairColor: Color, shirtColor: Color, pantsColor: Color)
```
Peatones se **ensamblan en runtime** con estos colores (tintado por píxel). / Pedestrians assembled at runtime with per-pixel tinting.

## Landmarks

```kotlin
data class Landmark(id, name, location: GeoPoint, rotationAngle, assetPath,
                    scaleX, scaleY, baseWidthMeters, baseHeightMeters, navGraph: LandmarkNavGraph?)
```
Métodos geo (rotación + escala en metros) / geo helpers:
- `toGlobalGeoPoint(localX, localY): GeoPoint` — local [0,1] → mundo (aplica rotación/escala en metros).
- `toLocalCoordinates(global): Pair<Float,Float>` — inverso.
- `contains(point): Boolean` — ¿el punto cae dentro del rect rotado del edificio?

```kotlin
data class LandmarkAssetTemplate(id, displayName, assetPath, defaultScale, baseWidthMeters, baseHeightMeters)
object LandmarkCatalogManager { var availableAssets; fun loadCatalog(context) }  // lee buildings_catalog.json
// LocalNavModels.kt: LocalNode, LocalWay, LandmarkNavGraph  (navegación micro dentro de un asset)
```

## ESCOM / Edificios interiores

```kotlin
enum class InteriorBuilding(id, displayName, location: GeoPoint, backgroundAsset, routeName) {
  AUDITORIO, BIBLIOTECA, CAFETERIA, EDIFICIO, ESTACIONAMIENTO, PALAPAS, CANCHAS_FUTBOL;
  companion object { fun fromId(id): InteriorBuilding? }
}
object EscomBoundingBox {                 // detección "estoy en ESCOM"
  const CENTER_LAT = 19.50456; const CENTER_LON = -99.14674; const HALF_OFFSET = 0.001 // ~111 m
  val topLeft/topRight/bottomRight/bottomLeft: GeoPoint
}
```

## Teletransporte / Easter egg / Coleccionables

```kotlin
object TeleportCatalog { val zones = [ ESCOM, Plaza Torres, FES ARAGON, Coyote Neza,
  Shine CTO, Deportivo Béisbol, Deportivo Fútbol, Estadio Azteca ] }   // TeleportZone(name, lat, lon)
object ShineCTOLocation { LAT=19.459049; LON=-99.163251; TRIGGER_RADIUS=0.00015; MARKER_* }
enum class ShineCTOFloor
data class ActiveCollectible(id, name, description, assetPath, latitude, longitude)
```

---

## `domain/models/ai/NpcAiManager.kt` — IA del open world (cliente) / open-world AI (client-side)

**ES:** Mantiene hasta **40 NPCs** alrededor del jugador. **Corre en el cliente** (en el Zone Host);
el servidor open world **no** simula NPCs (solo relaya). Expone `npcs: StateFlow<List<Npc>>`.
**EN:** Maintains up to **40 NPCs** around the player. **Runs on the client** (on the Zone Host); the
open-world server does **not** simulate NPCs (it only relays). Exposes `npcs: StateFlow<List<Npc>>`.

**Constantes clave / key constants:**
```
CAR_SPEED=0.000008  PERSON_SPEED=0.0000015        // por tick (grados)
FEAR_RADIUS=0.0018 (~180m)  FEAR_DURATION_MS=4500  FEAR_SPEED_MULT=3.8
CHAT_DISTANCE=0.00035  CHAT_DURATION_MS=5000  CHAT_CHANCE=0.012
LANE_OFFSET=0.000024                              // tráfico doble sentido (offset a la derecha)
AGGRO_DURATION_MS=6000  AGGRO_SPEED_MULT=5  AGGRO_STOP_DIST=0.000018(~2m)  AGGRO_VISION_RADIUS=0.0005
aggressiveRatio = 0.3 (configurable)              // fracción de NPCs AGGRESSIVE; resto COWARD
NPC_OUTFITS (lazy), CAR_COLORS: IntArray
```

**API principal / main API:**
- `setLandmarks(landmarks)` — precomputa `cachedNavLandmarks` (lista con `navGraph != null`, **una sola
  vez**, no por NPC/tick — ver 09). / precomputes nav-landmark list once.
- `updateRoadNetwork(network)` — calcula bbox por way (`cachedWayBoxes`) e índice; pre-filtro O(1). Además
  fija `urbanFactor` (densidad de calles → "ciudad", ver abajo).

**Población dinámica / dynamic population (gama + ciudad + ajuste del usuario):** `maxActiveNpcs`/
`maxTotalNpcs`/`carPopulationRatio` se escalan por `popFactor = deviceTierFactor × urbanFactor ×
userPopulationFactor`:
- **`userPopulationFactor`** (0.4–1.6): lo fija el usuario en **Ajustes → Jugabilidad** (slider "Cantidad
  de NPCs"); lo persiste `SettingsRepository.getNpcDensity()` y lo aplica `WorldMapViewModel.setNpcDensity` /
  el `Factory`. Ver 07.
- **`deviceTierFactor`** (gama del teléfono): lo fija `WorldMapViewModel.Factory.computeDeviceTierFactor`
  desde `ActivityManager` (RAM total + `isLowRamDevice`): ~0.6 (≤2 GB), 1.0 (≤4 GB), 1.3 (≤6 GB), 1.5 (más).
- **`urbanFactor`** (detección de "ciudad", GRATIS): `(network.size / 140).coerceIn(0.6, 1.8)` — la
  densidad de la red OSM ya descargada es el proxy de urbanización (céntrico = muchas más vías en ~2 km).
  Se recalcula al viajar a una zona nueva. En ciudad sube población **y tráfico** (`carPopulationRatio`).
  *(No usa APIs de tráfico de pago; podría refinarse con tags OSM `landuse`/`place` en el futuro.)*
- `setServerNpcs(npcs)` / `getServerNpcs()` / `setRemoteNpcs(remoteList)` — sincroniza con el roster.
- `updateNpcs(playerLocation, isHost)` — **tick principal**: despawn lejanos, cap de población, spawn
  por proximidad (bbox pre-filter), estacionamiento en landmarks, charlas, miedo, y `moveNpc` por NPC.
  **Mejoras de tráfico realista en `moveNpc`:**
  1. **Compromiso de intersección:** si el auto está cerca de una bifurcación (múltiples candidatos
     para `targetNodeIndex`), elige uno y lo fija en `committedTargetNodeIndex` con
     `commitmentTicks = 10`. Mientras `commitmentTicks > 0`, NO re-evalúa la ruta, evitando el
     "temblor" indeciso en esquinas.
  2. **Evitación de alcance (rear-end avoidance):** antes de mover, mide la distancia al auto NPC
     más cercano *delante* en el mismo carril. Si está a < `SAFE_FOLLOWING_DIST` (~8 m), reduce
     su velocidad (frena) proporcionalmente para no chocar por detrás.
  3. **Variación de velocidad:** la velocidad base `CAR_SPEED` se multiplica por `speedVariation`
     (aleatorio 0.8–1.2 al spawn) para que no todos los autos vayan exactamente a la misma velocidad.
  4. **Rebase del jugador (anti-órbita):** al esquivar al jugador el coche persigue un carrot local, así que
     ahora avanza `targetNodeIndex` en cuanto REBASA el nodo base (`avoidingPlayer` + producto punto en el
     return de `moveNpc`); sin esto se daba la vuelta y orbitaba sin rebasar (ver 09).
- **Topes base de población:** `maxActiveNpcs`/`maxTotalNpcs` no-zombi = **10/22** (antes 18/38; densidad más realista al caminar; `SPAWN_SCAN_MS`=900 y máx 2 spawns/escaneo), siguen
  escalando por `popFactor` (ver 09).
- `triggerFear(lat, lon)` — marca evento de miedo; los COWARD cercanos huyen (`applyPendingFear`).
- `rollTrait()` — PASSIVE/COWARD/AGGRESSIVE según `aggressiveRatio`.
- `moveAggroNpc(...)` — embestida hacia el jugador (ignora el grafo de calles).
- `pendingDespawns: MutableList<String>` — IDs a borrar; el game loop los emite como `NPC_DESTROY`.

**Adopción / Adoption:** un NPC remoto cuyo `ownerId` no es el mío es **adoptado** por el Host más
cercano (`moveNpc` reconstruye su ruta). Así el mundo nunca queda sin simular. / A remote NPC whose
`ownerId` isn't mine is adopted by the nearest Host.

### 🧟 Modo Zombi Global / Global Zombie Mode (apocalipsis)

**ES:** Sub-modo del open world (distinto del minijuego). Se activa con `globalZombieMode` (lo pone el
game loop cada tick desde `WorldMapState.globalZombieMode`). Los zombis son `NpcType.ZOMBIE`,
simulados por el **Zone Host** dentro de `updateNpcs` y replicados por `NPC_BATCH_UPDATE`.
**EN:** Open-world sub-mode (distinct from the minigame). Toggled via `globalZombieMode`. Zombies are
`NpcType.ZOMBIE`, simulated by the Zone Host in `updateNpcs`, replicated via `NPC_BATCH_UPDATE`.

Constantes: `ZOMBIE_SPEED_MULT=3.5`, `ZOMBIE_VISION=0.0009 (~100m)`, `ZOMBIE_CONTACT_DIST=0.00003 (~3m)`,
`ZOMBIE_BITE_DAMAGE=8`, `ZOMBIE_BITE_COOLDOWN_MS=1000`, `MAX_ZOMBIES=35`, `INITIAL_ZOMBIE_SEED=25`,
`HUMAN_CONVERT_DELAY_MS`. En modo zombi: `maxActiveNpcs=45`, `maxTotalNpcs=90`, `carPopulationRatio=0`
(sin autos nuevos).

Lógica (rama `if (globalZombieMode)` en `updateNpcs`, corre en el Host):
1. **Seed:** mientras `zombies < INITIAL_ZOMBIE_SEED`, spawnea 1 zombi/tick con `spawnNpcOnRoad(...)?.copy(type=ZOMBIE, trait=AGGRESSIVE, visualConfig=null)`. **`visualConfig=null` es obligatorio** o los renderers lo dibujan como humano (ver 04/09).
2. **Huida:** cada humano con un zombi dentro de `FEAR_RADIUS` recibe `fearUntil`/`fearFrom*` apuntando al zombi (reusa el sistema de miedo → `moveNpc` lo hace huir).
3. **Contagio:** humano con `health<=0 && isDying` y `now > fearUntil` → `copy(type=ZOMBIE, health=100, visualConfig=null)` si `zombies < MAX_ZOMBIES` (si no, despawn).
4. **`moveZombieNpc(npc, network, now, playerLat, playerLon)`** — persecución directa **fuera de la calle**
   (como `moveAggroNpc`). **El jugador es objetivo PRIORITARIO:** su distancia se PONDERA (×0.45, `bestScore`)
   para que los zombis lo prefieran aunque un humano huya algo más cerca → "vienen por ti". Usa `realDist`
   (distancia real) para la visión (`ZOMBIE_VISION`) y el contacto. Muerde al humano a `ZOMBIE_CONTACT_DIST`
   (cooldown `chatUntil`); al jugador no lo muerde aquí (lo hace `applyNpcContactDamage` en el VM, ver 04).

> El daño al jugador (mordida) NO está en `NpcAiManager`: lo aplica `WorldMapViewModel.applyNpcContactDamage` en cada cliente (ver 04). / Player bite damage is applied per-client in `applyNpcContactDamage` (see 04).

#### Roles de zombi / Zombie roles (`ZombieRole`)

```kotlin
enum class ZombieRole { NORMAL, RUNNER, TANK, SCOUT }   // campos en Npc: zombieRole, maxHealth, screamUntil
```
**Campos de ESQUIVE (Midnight Club) en `Npc`:** `dodgeUntil: Long`, `dodgeDirLat/dodgeDirLon: Double`.
Mientras `now < dodgeUntil`, `updateNpcs` mueve al peatón en `dodgeDir` a `personSpeed*10` **sin snap a la
calle** (sidestep animado, no teletransporte). Lo dispara el Host en `WorldMapViewModel.runOverNpcs`; al
expirar, `moveNpc` lo re-engancha a la vía. No se replican (la **posición** ya viaja en `MultiplayerNpc`).
Bajo costo: **palette swap** (tinte por `ColorMatrix` en `MapZombieSpriteManager`) sobre el MISMO asset
`z_walk`, + velocidad/vida/tamaño. Helpers estáticos en el companion: `rollZombieRole()` (pesos:
~22% Runner, ~12% Tank, ~8% Scout, resto Normal), `maxHealthForRole()` (Runner 15, Normal 30, Tank 60,
Scout 15; con `PLAYER_PUNCH_DAMAGE=15` → ~1/2/4/1 golpes), `speedMulForRole()` (Runner ×1.6, Tank ×0.55,
Scout ×1.5).

| Rol | Tinte | Velocidad | Vida | Comportamiento |
|---|---|---|---|---|
| RUNNER "Corredor" | rojizo | rápido | baja (1 golpe) | persigue y muerde, normal pero veloz |
| TANK "Tanque" | verdoso oscuro | lento | alta (~4 golpes), +grande | persigue y muerde |
| SCOUT "Explorador" | amarillento | rápido | baja | **NO ataca**: corre al humano más cercano, **grita** (`screamUntil` → burbuja 📢, alarma de horda) y **huye** en dirección opuesta ~4.5 s |
| NORMAL | — | normal | media (2 golpes) | persigue y muerde |

Rol asignado en el **seed** y en la **conversión** (humano contagiado toma rol aleatorio). Se replica por
`NPC_BATCH_UPDATE` (`zombieRole`, `screamUntil`); el `maxHealth` se **deriva** del rol en el cliente
remoto (`addRemoteEntity`), no viaja por el cable. El SCOUT se excluye del daño por contacto al jugador.

#### 🌊 Hordas migratorias / Migratory hordes

**ES:** Evento de asedio dinámico. Cada `HORDE_INTERVAL_MS` (20 s), si hay un **punto de calor**
(≥ `HORDE_MIN_HUMANS`=4 humanos dentro de `simRadius`), el **Host** spawnea una **oleada** de
`HORDE_SIZE`=10 zombis (`spawnNpcOnRoad`, acotado a `maxTotalNpcs`). Como cada zombi auto-persigue al
humano más cercano, la oleada **converge sobre el cúmulo** → asedio. Lo calcula el Host (ya simula la IA
de NPCs); se replica por `NPC_BATCH_UPDATE` como cualquier zombi (funciona en SP y MP). `hordeIncomingAt`
avisa al VM para el HUD ("🧟 ¡UNA HORDA SE ACERCA!"). El SCOUT (alarma viviente) es el aviso *in-game*.
**EN:** Dynamic siege. Every `HORDE_INTERVAL_MS` (20s), if there's a heat point (≥4 humans within
`simRadius`), the **Host** spawns a wave of 10 zombies that converge on the cluster (each chases nearest
human). Host-computed (it runs NPC AI), replicated via `NPC_BATCH_UPDATE` (works SP + MP). `hordeIncomingAt`
drives a HUD warning.

#### 👮 Policía del apocalipsis / apocalypse police (caza-zombis)

**ES:** El Host spawnea NPCs `POLICE_COP` (prob. `POLICE_SPAWN_CHANCE` cada `POLICE_SPAWN_INTERVAL_MS`,
máx `POLICE_HUNTER_MAX`) que **cazan al zombi más cercano**: `movePoliceHunter` lo persigue y **dispara**
(`POLICE_SHOOT_DAMAGE` a `POLICE_SHOOT_DIST`, cooldown `chatUntil`) — el disparo se registra en
`pendingPoliceShots` para que el VM lo pinte como **bala visible**; si no hay zombis, patrulla (`moveNpc`).
Los **zombis también muerden a los cops** (están en la lista de víctimas de `moveZombieNpc` junto a los
civiles; un cop a 0 de vida → `movePoliceHunter` lo despawnea). **No tocan al jugador** salvo que estén
**provocados** (`aggroUntil > now`, lo fija el VM `provokeApocalypsePolice` ~33 m): entonces persiguen al
jugador (daño en el VM). Render 👮 (POLICE_COP), replicado por `NPC_BATCH_UPDATE`. Ver 04.

---

## `domain/models/ai/PoliceManager.kt` — Nivel de búsqueda estilo GTA / GTA-style wanted level

**ES:** El **jugador buscado simula y posee** su propia policía (no el Zone Host), porque debe
perseguirlo a él. La policía **no** vive en `remoteEntities`; se fusiona en `uiState.npcs` aparte.
Driven por `WorldMapViewModel.runPoliceTick`.
**EN:** The **wanted player owns and simulates** their own police (not the Zone Host), since it must
chase *that* player. Police do **not** live in `remoteEntities`; they're merged into `uiState.npcs`
separately. Driven by `WorldMapViewModel.runPoliceTick`.

**Constantes / constants:**
```
MAX_CARS=8   SPAWN_RING=0.0024(~265m)   ARRIVE_DIST=0.00045(~50m, se bajan cops a pie)
CAR_SPEED=0.0000185   COP_SPEED=0.0000038   COP_STOP_DIST=0.000018
PUNCH_DIST=0.00003(~3m)  PUNCH_DAMAGE=5  PUNCH_COOLDOWN_MS=1400
SHOOT_RANGE=0.0002(~22m)  SHOOT_DAMAGE=4  SHOOT_COOLDOWN_MS=2400   // dispara a 2★+
ADJACENT_DIST=0.00009(~10m, carjack)  BOARD_DIST=0.0001  RECALL_DIST=0.0006  RETREAT_DESPAWN=0.0026(~290m)
ROUTE_TTL_MS=1500  STUCK_TIME_MS=1500  WAYPOINT_REACH=0.00012  MIN_PROGRESS=0.00025  DETOUR_DIST=0.0013
desiredCarsFor(wantedLevel): 1★→1 … 5★→8
```

**API:**
- `update(playerLat, playerLon, wantedLevel, playerInVehicle, now, snap, pathfind): PoliceTick`
  — núcleo: spawnea patrullas (escala con estrellas), las mueve **pegadas a la red** (`snap` =
  `getNearestPointOnNetwork`), suelta 2–3 cops, golpea/dispara, maneja carjack y retirada.
- `PoliceTick(units, damage, impact, destroyedIds, adjacentThreat, shots: List<Pair<GeoPoint,GeoPoint>>)`.
- `activeUnits(): List<Npc>`, `playerHitPolice(lat, lon, radius, damage): List<String>`, `clearAll()`.
- `boardPatrol(id): Npc?` — el jugador SE SUBE a una patrulla: saca la unidad (POLICE_CAR) y la devuelve;
  el VM la convierte en su vehículo, difunde `POLICE_DESTROY` y pone `wantedLevel=MAX` (5★). Ver 04/09.
- Internos: `stepOnRoad(...)` (un paso snapeado + rotación según dirección), `advanceAlong(...)`
  (sigue una ruta cacheada con TTL/atasco/desvío), `carRoute`/`carRouteIdx` por unidad.

**Comportamiento / behavior:** patrulla fuera de la fog → waypoint 🚓 + línea de ruta; dentro de la
fog → asset real. En auto, cops re-abordan (`policeReturning`) y pueden bajarte (carjack). A
`wantedLevel==0` (incl. muerte) **se retiran** (no se borran de golpe), emitiendo `POLICE_DESTROY`.

---

## `domain/models/ai/PrankedyManager.kt` — NPC especial Prankedy / special hostile NPC

**ES:** Kotlin puro (como `PoliceManager`). NPC **hostil** "rey de las bromas": persigue al jugador y
le lanza un **tanque de gas**, pero si otro NPC está agrediendo al jugador, **se pone de su lado** y
ataca a ese NPC. Lo simula el VM cada tick (`runPrankedyTick`), local a cada cliente (como la policía).
**No** se contrata (el flujo de "contratar" se eliminó). / **EN:** Pure Kotlin. **Hostile** NPC that
chases the player and throws a gas tank, but **sides with you** to attack any NPC aggressing you.
VM-driven per tick, client-local. Not hireable.

**Fases / phases** (`PrankedyState.kt`): `PrankedyPhase` = `NOT_HIRED` (= activo/hostil, estado vivo),
`DEAD` (timer de respawn); **🆕 `HIRED` = modo ACOMPAÑANTE del Modo Historia** (campaña ENCB): te sigue de
forma pacífica (sin atacarte ni lanzar el tanque). Se entra con **`spawnCompanion(nearPlayer, roadNetwork, now)`**
(= `spawn` + `phase=HIRED`); en `tick(...,playerRunning)`, si `phase==HIRED` corre **`tickFollow`** en vez
de `tickCombat`. **Seguimiento optimizado:** se detiene MUY cerca (`FOLLOW_STOP_DIST`≈3 m, para no rezagarse),
**iguala la velocidad del jugador** (corre `p_run`/`RUN_SPEED` si `playerRunning` **o** si está lejos
—`FOLLOW_WALK_DIST`≈12 m—, si no camina `p_walk`); **ROAD-ONLY ESTRICTO** (snap a la red vial SIEMPRE, sin
atajos por césped/edificios); la **orientación** se calcula con el **vector de movimiento real**
(`atan2(dLat,dLon)` → voltea el sprite hacia donde avanza, ya no parece caminar al revés).
`PrankedyAnimState` = `IDLE, WALK, RUN,
RUN_TANQUE, ATTACK`. La activación (solo `inCampaign` && vecindario ENCB) la hace
`WorldMapPrankedy.maybeSpawnPrankedyCompanion` (ver 04/07).

**Constantes clave / key constants:**
```
ATTACK_RADIUS=0.00007(~8m)   ATTACK_ANIM_MS=800(windup p_attack)   PROJECTILE_FLIGHT_MS=900
IMPACT_RADIUS=0.00005(~5.5m, esquivable)   AGGRO_DETECT_RADIUS=0.00045(~50m, detecta agresor)
RUN_TANQUE_SPEED=0.0000075   ATTACK_DAMAGE_PROJECTILE=22   ATTACK_COOLDOWN_MS=2800
MAX_HEALTH=80   RESPAWN_COOLDOWN_MS=60000   ENEMY_CONTACT_(RADIUS/DAMAGE/COOLDOWN_MS)
```

**API / lógica:**
- `tick(playerLoc, npcs, isDriving, now, roadNetwork, snapToRoad?)` → `PrankedyTickResult(hitNpcId,
  projectileDamage, justDied, hitPlayer)`. Si `isDriving`, se oculta (IDLE).
- **Spawn robusto** (`spawn`/`findSpawnPoint`): 3 niveles → nodo de calle a ~35 m → nodo de calle más
  cercano → offset aleatorio (siempre aparece), sobre la red viaria.
- **Combate:** `findAggressorNearPlayer` (NPC con `aggroUntil>now` o `ZOMBIE` a ≤50 m **del jugador**;
  **NO** incluye `POLICE_COP` — antes los perseguía y se alejaba de ti al llegar la policía)
  → si existe, objetivo = ese NPC; si no, objetivo = el **jugador**.
- **Correa (leash) `LEASH_MAX`≈33 m:** Prankedy SIEMPRE se mantiene cerca de ti. Si quedó más lejos que
  `LEASH_MAX` del jugador, IGNORA al agresor y vuelve a tu lado (objetivo = jugador). Así no "se aleja".
- **Anti-traba:** al correr, si el `snap` a la calle no acerca al objetivo, usa el paso DIRECTO ese tick;
  y si lleva > `STUCK_TIME_MS` (1.5 s) sin avanzar (`STUCK_EPS`) estando lejos, `relocateNear` lo reubica
  cerca del jugador sobre calle **sin curarlo** (no usa `spawn`, que resetea vida). Ver 09. Corre con tanque (`RUN_TANQUE`); al
  llegar a `ATTACK_RADIUS` hace el **windup** (`p_attack`, 800 ms quieto) y AL TERMINAR lanza el tanque
  (`p_objeto`). El proyectil viaja y al caer **solo pega si el objetivo sigue dentro de `IMPACT_RADIUS`**
  (esquivable si te mueves; `hitPlayer` → `takeDamage` en el VM).
- **Snap a la calle:** el VM le pasa `snapToRoad = getNearestPointOnNetwork`; todo su movimiento se
  engancha a la red (no atraviesa edificios).
- **Daño/muerte:** `takeDamage()`; a 0 HP → `DEAD`, oculto, respawn a los 60 s. El jugador lo golpea
  (`performPlayerAttack` → `takeDamage` si está a ≤`ATTACK_RADIUS`); los NPCs hostiles en contacto
  también lo lastiman.
- **Toggle:** `deactivate()` lo quita del mapa; lo gobierna `WorldMapState.prankedyEnabled` (item de
  Opciones "Activar/Desactivar Prankedy", **default OFF**); `checkPrankedySpawn`/`runPrankedyTick`
  no-opean si está apagado.

> Render (sprites `assetsNPC/Prankedy/` + proyectil) en **OSM nativo y web (Leaflet)** — ver 04.
> El tick/spawn corren desde el **game loop MIEMBRO** de `WorldMapViewModel.kt` (no la extensión muerta).

---

## `domain/models/zombie/` — Modelos del minijuego / minigame models

```kotlin
enum class ZombieType { NORMAL, STALKER }
data class ZombieEntity(id, x:Float, y:Float, health=100, maxHealth=100, facingRight, frameIndex,
                        isDying, lastFrameAdvanceMs, lastDamageToPlayerMs, isLootCarrier, type, isAttacking)
enum class SkillEffect(displayName, isTrap, durationMs, assetKey) {
  CURA_TOTAL(trap=false,0), RELOJ_ARENA(false,8000), ADRENALINA_ZOMBI(trap=true,7000),
  FURIA_ZOMBI(trap=true,7000), DEBILIDAD_ZOMBI(false,8000), FUERZA_BRUTA(false,9000) }   // 6 efectos
data class SkillItem(id, x, y, effect: SkillEffect, collected)
data class ActiveEffect(effect, expiresAtMs)
data class Projectile(id, x, y, dirX, dirY, bornAtMs)
enum class CombatMode { MELEE, RANGED }
enum class ZoneType { LOBBY, BUILDING }
enum class DoorKind { GENERIC, EXIT_NEXT, EXIT_PREV, TO_BUILDING, TO_WORLD }

class CollisionMatrix(rows: List<String>) {   // '#'=pared, '.'=libre
  numRows, numCols; fun isBlockedFrac(fx, fy): Boolean   // O(1) por celda fraccionaria
}
data class ZombieRoom(id, type: ZoneType, backgroundAsset, displayName, zoom=2.2f,
                      playerSpawnFrac, zombieCount, gridCols?, collisionGridFrac: List<NormRect>, ...) {
  fun isBlockedFrac(fx, fy); fun isBlockedPixel(x, y)
}
data class ZoneDoor(hitboxFrac: NormRect, targetRoomId, label, kind: DoorKind)
data class NormPoint(x, y); data class NormRect(left, top, right, bottom){ toWorldRect, centerXFrac, centerYFrac }
data class WorldRect(left, top, right, bottom){ contains, centerX, centerY }
```

### `ZombieRoomCatalog.kt`
```kotlin
object ZombieRoomCatalog {
  const LOBBY_ID = "lobby_campus";  const EXIT_TO_WORLD = "__WORLD__"
  BUILDING_MATRIX = CollisionMatrix(borderOnly(cols=30, rows=20))   // por defecto solo borde
  LOBBY_MATRIX    = CollisionMatrix(borderOnly(cols=30, rows=30))
  val rooms: List<ZombieRoom>      // lobby + 7 edificios en anillo (buildingOrder, next/prev)
  fun roomById(id); fun indexOfRoom(id); fun init(context)   // init carga assets/bitmaps
}
```
**ES:** Las matrices por defecto son **solo borde** y deben coincidir filas/cols con las del servidor
zombi hasta reemplazarse (ver 08). El Modo Diseñador exporta `collision_matrices.json` con el mismo
formato que lee el servidor. / **EN:** Default matrices are border-only and must match rows/cols with the
zombie server until replaced (see 08). Designer Mode exports `collision_matrices.json` in the exact
shape the server reads.
