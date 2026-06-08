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
enum class NpcType(drawableName) { PERSON, CAR, POLICE_CAR, POLICE_COP }
enum class NpcNavState { MACRO_OSM /*calles reales*/, MICRO_LANDMARK /*dibujo del asset*/, PARKED }
enum class NpcTrait { PASSIVE, COWARD, AGGRESSIVE }   // personalidad asignada al spawn
```

**`Npc`** (`var` mutables para el game loop; campos clave):
`id, type, location: GeoPoint, rotationAngle, speed, currentWay, targetNodeIndex, moveDirection,
carColor: Int, carModel, isRemote, ownerId, isMoving, facingRight, visualConfig:
CharacterVisualConfig?, displayName, health: Float=100, isDying, navState, currentLocalWay,
currentLandmark, fearUntil/fearFromLat/fearFromLon (huida), chatUntil/chatPartnerId (charlas),
trait, aggroUntil (embestida), policeDisembarked, policeCanShoot, policeCarId, policeReturning,
callingUntil`.

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
- `updateRoadNetwork(network)` — calcula bbox por way (`cachedWayBoxes`) e índice; pre-filtro O(1).
- `setServerNpcs(npcs)` / `getServerNpcs()` / `setRemoteNpcs(remoteList)` — sincroniza con el roster.
- `updateNpcs(playerLocation, isHost)` — **tick principal**: despawn lejanos, cap de población, spawn
  por proximidad (bbox pre-filter), estacionamiento en landmarks, charlas, miedo, y `moveNpc` por NPC.
- `triggerFear(lat, lon)` — marca evento de miedo; los COWARD cercanos huyen (`applyPendingFear`).
- `rollTrait()` — PASSIVE/COWARD/AGGRESSIVE según `aggressiveRatio`.
- `moveAggroNpc(...)` — embestida hacia el jugador (ignora el grafo de calles).
- `pendingDespawns: MutableList<String>` — IDs a borrar; el game loop los emite como `NPC_DESTROY`.

**Adopción / Adoption:** un NPC remoto cuyo `ownerId` no es el mío es **adoptado** por el Host más
cercano (`moveNpc` reconstruye su ruta). Así el mundo nunca queda sin simular. / A remote NPC whose
`ownerId` isn't mine is adopted by the nearest Host.

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
- Internos: `stepOnRoad(...)` (un paso snapeado + rotación según dirección), `advanceAlong(...)`
  (sigue una ruta cacheada con TTL/atasco/desvío), `carRoute`/`carRouteIdx` por unidad.

**Comportamiento / behavior:** patrulla fuera de la fog → waypoint 🚓 + línea de ruta; dentro de la
fog → asset real. En auto, cops re-abordan (`policeReturning`) y pueden bajarte (carjack). A
`wantedLevel==0` (incl. muerte) **se retiran** (no se borran de golpe), emitiendo `POLICE_DESTROY`.

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
