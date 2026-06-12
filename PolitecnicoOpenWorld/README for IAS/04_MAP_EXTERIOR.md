# 04 · Open World / `features/map_exterior/`

**ES:** El corazón del juego. El `WorldMapViewModel` (Activity-scoped, ~2800 líneas) está **partido en
extensiones** (`WorldMap*.kt`) que agrupan la lógica por tema. El estado es `WorldMapState`.
**EN:** The game's heart. `WorldMapViewModel` (Activity-scoped, ~2800 lines) is **split into
extension partials** (`WorldMap*.kt`) grouping logic by topic. State is `WorldMapState`.

> **Gotcha:** algunas funciones existen como **miembro privado** en `WorldMapViewModel.kt` *y* como
> **extensión** en un parcial con el mismo nombre (p. ej. `startGameLoop`, `updateVisibleRoads`,
> `handleMultiplayerMessage`). En Kotlin el **miembro gana** sobre la extensión. La implementación
> canónica/activa es la de `WorldMapViewModel.kt`; los parciales agrupan lógica y helpers `internal`.
> Verifica ambos al editar. / Some functions exist both as a private member in `WorldMapViewModel.kt`
> and as a same-named extension in a partial. Kotlin's **member wins**; the canonical impl is in
> `WorldMapViewModel.kt`. Check both when editing.

---

## Tabla "Key files" (salta directo aquí) / jump straight here

| Tema / Concern | Archivo / File |
|---|---|
| Game loop, multiplayer, NPCs, ESCOM, policía | `viewmodel/WorldMapViewModel.kt` + parciales |
| Estado UI / UI state | `viewmodel/WorldMapState.kt` |
| Game loop (parcial) | `viewmodel/WorldMapGameLoop.kt` |
| Multiplayer relay/parse | `viewmodel/WorldMapMultiplayer.kt` (+ `WorldMapMultiplayerModels.kt`) |
| Red de calles / road network | `viewmodel/WorldMapRoadNetwork.kt` |
| Routing / waypoints | `viewmodel/WorldMapRouting.kt` |
| ESCOM (puertas, ousted driver) | `viewmodel/WorldMapEscom.kt` |
| Coleccionables (lógica) | `viewmodel/WorldMapCollectiblesLogic.kt` |
| Misc (movimiento, WASTED, HUD vida) | `viewmodel/WorldMapMisc.kt` |
| Render principal (OSM/Google/Leaflet) | `ui/WorldMapScreen.kt` |
| Render nativo osmdroid (fog, over-zoom, NPCs, landmarks) | `ui/NativeOsmMap.kt` |
| HTML de Leaflet (WebView) | `ui/WorldMapLeafletHtml.kt` |
| Intercepción de tiles Leaflet | `ui/CachingWebViewClient.kt` |
| Helpers de dibujo / health bar | `ui/WorldMapDrawingUtils.kt` |
| Sprite jugador / vehículo conducido | `ui/components/PlayerCharacter.kt` |
| Controles (D-Pad/joystick/A-B-X-Y) | `ui/components/GameControllers.kt` |
| Menú anidado de opciones | `ui/components/OptionsMenu.kt` |
| Panel del modo diseñador | `ui/components/Designerpanel.kt` |
| Sprites NPC | `ui/components/CharacterSpriteManager.kt`, `VehicleSpriteManager.kt`, `PoliceSpriteManager.kt` |

---

## Estado / State — `WorldMapState.kt`

```kotlin
enum class MapProvider(displayName) {
  OSM, GOOGLE_MAPS_NATIVE, OSM_WEB, GOOGLE_MAPS, CARTO_DB_DARK, CARTO_DB_LIGHT,
  ESRI, ESRI_SATELLITE, OPEN_TOPO;            // 8 proveedores (web = todos menos OSM y GOOGLE_MAPS_NATIVE)
  val isWebProvider: Boolean
}
enum class RoadSource { LOADING, LOCAL_DB, NETWORK }
enum class TileSource { LOCAL_OSM, LOCAL_CACHE, NETWORK }
data class PoliceShot(from: GeoPoint, to: GeoPoint, at: Long)
```

**`WorldMapState`** — campos por grupo / fields by group (default `mapProvider = OSM_WEB`):
- **Mapa/carga:** `currentLocation, isLoadingLocation, zoomLevel, mapProvider, pendingProvider,
  pendingProviderReady, isMapReady, mapLoadProgress, roadSource, tileSource, isRoadNetworkReady`.
- **Render flags:** `showCacheWidget(=true), showFpsWidget, showRoadNetwork, isUserPanningMap`.
- **Controles/skin:** `controlType, controlsScale, swapControls, selectedSkin, showSkinSelector`.
- **Jugador:** `playerAction, isPlayerFacingRight, isRunning, isDriving, currentVehicleModel,
  currentVehicleColor, vehicleSpeed, vehicleRotation, vehicleIsFirstTimeBoarded`.
- **NPCs/multiplayer:** `npcs, isMultiplayer, playerName`.
- **Diseñador:** `isDesignerMode, selectedLandmarkId, showAssetPicker, landmarks`.
- **Coleccionables/daño:** `activeCollectibles, nearbyCollectible, showClaimedPopupFor,
  interactionPrompt, showWastedScreen`.
- **Wanted/policía:** `wantedLevel(0-5), carjackWarning, policeShots`.
- **Navegación:** `destinationMarker, isTargetingWaypoint, routeWaypoints, showDestinationRoute,
  destinationArrivalThreshold=20.0`.
- **Zombi/interiores:** `showZombiVideo, isZombieHandSpawned, pendingInteriorDestination`.
- **Transiciones:** `showEscomDoorFade, escomDoorFadeComplete, pendingDoorDestination, showShineCTODiscovery`.
- **Metro:** `metroStations, nearbyMetroStation, showMetroFade, metroFadeCompleteStation`.
- **Prefetch offline (solo OSM nativo):** `zonePrefetchActive, zonePrefetchProgress, zoneOfflineReady, zoneOfflineWarning`.
- **Creador de rutas:** `routeDebugWaypoints, isParkingSlotMode, currentWayId(=100)`.

---

## Game loop — `WorldMapGameLoop.kt` / `WorldMapViewModel.startGameLoop`

**ES:** Coroutine en `Dispatchers.Default`, **tick cada 33 ms** (~30 Hz). Arranque: espera ubicación,
carga calles (caché Room → si no, Overpass con backoff exponencial 1s→30s). Luego, por tick:
**EN:** Coroutine on `Dispatchers.Default`, **tick every 33 ms** (~30 Hz). Startup: wait for location,
load roads (Room cache → else Overpass with exponential backoff 1s→30s). Then, per tick:

```
1. ESCOM hand sync: dentro de ESCOM + red lista → spawnear ZombiHand; fuera → borrarla.
2. cada 30 ticks → trySpawningCollectible; siempre → checkCollectibleProximity, checkDestinationArrival.
3. cada 30 ticks (si hay marker) → updateDestinationRoute.
4. si playerAction==SPECIAL → performPlayerAttack (golpe a NPCs, ATTACK_RADIUS=0.00022 ~24 m).
5. si isDriving && !WASTED → física del coche:
     girar ±2°/tick (solo con velocidad), gas→+ACCELERATION (cap MAX_SPEED), freno→-BRAKING_FRICTION
     (reversa hasta -MAX_SPEED/2), inercia al soltar. dx=sin(rot)*v, dy=cos(rot)*v.
     **fuera de la calle (curva/bifurcación):** en vez de chocar y parar, **auto-direcciona** el coche
     hacia la calle (muestrea un punto ~22 m adelante en la red, gira suave hacia ahí) y baja la velocidad
     **proporcional** al desvío (`overshoot`); `maxRoadRadius=0.00004` (más holgado). Así, dejando
     acelerar, sigue la carretera. runOverNpcs(loc, v).
6. applyNpcContactDamage(loc)  // NPCs agresivos en embestida pegan a TU jugador (cada cliente al suyo)
7. si red lista && !WASTED → runPoliceTick(loc)
8. maybeRefetchRoadNetwork(loc); cada 5 ticks → updateVisibleRoads(loc)
9. si red lista, cada 3 ticks → setServerNpcs(npcs remotos) → npcAiManager.updateNpcs(loc, isHost)
     → si soy Host: aplicar pendingDespawns, volcar processedNpcs a remoteEntities → updateNpcsState()
     → enviar PLAYER_UPDATE; si Host: NPC_DESTROY de despawns + NPC_BATCH_UPDATE de NPCs activos (dist<=0.0012)
```
Constantes del VM / VM constants: `MAX_SPEED=0.000017`, `ACCELERATION=0.0000003`,
`BRAKING_FRICTION=0.000001`, `ATTACK_RADIUS=0.00022`, `RUN_OVER_RADIUS=0.00003`,
`NPC_CONTACT_COOLDOWN_MS=900`, `RELENTLESS_HIT_STREAK=6`, `CELL=0.0025` (grid de snap-to-road). El loop está envuelto en
try/catch (relanza `CancellationException`, traga el resto para no crashear). / Loop wrapped in
try/catch (rethrows cancellation, swallows the rest).

---

## Routing / snap-to-road — `WorldMapRouting.kt`

**ES:** El jugador **no puede salirse de las calles**. Cada movimiento se valida contra un **índice
espacial en grid** (`Seg` = segmento + `HashMap<celda, List<Seg>>`), corriendo *snap-to-road* en
O(candidatos cercanos).
**EN:** The player **can't leave roads**. Every move is validated against a **spatial grid index**
(`Seg` + `HashMap<cell, List<Seg>>`), running snap-to-road in O(nearby candidates).

- `ensureIndex()` / `candidates(loc): List<Seg>` / `getNearestPointOnNetwork(t): GeoPoint` /
  `project(p, v, w): GeoPoint` (proyección punto-segmento).
- `pack(r, c): Long`, `cell(v): Int` (celda de grid), `CELL` (tamaño de celda).
- **Ruta de waypoints (greedy):** `calculateRouteOnNetwork(from, to, network)`, `buildRoadGraph`,
  `nearestGraphNode`, `findRoadRoute` (BFS/greedy sobre grafo de nodos únicos), `rebuildRoadNodeGrid`,
  `nearbyRoadNodes`. **Claves `Pair<Double,Double>`** (sin allocs de String por paso).
- `updateDestinationRoute()` dibuja polilínea azul punteada; el marker se auto-borra a `destinationArrivalThreshold` (20 m).

> **Pendiente:** el router open-world sigue siendo **greedy** (no A*). El servidor zombi sí usa
> Dijkstra (ver 08). / Open-world router is still greedy (no A*); the zombie server uses Dijkstra.

---

## Red de calles / Road network — `WorldMapRoadNetwork.kt`

- `updateVisibleRoads(location, force)` — filtra ways visibles (throttled a cada 5 ticks).
- `maybeRefetchRoadNetwork(currentLoc)` — re-fetch al alejarse de `lastNetworkFetchLocation`
  (cooldown 5 min); guarda en `RoadNetworkCache`.
- `prefetchCurrentZoneTiles(loc)` — dispara `TilePrefetchManager` (solo OSM nativo, offline).

## Multiplayer — `WorldMapMultiplayer.kt` (+ `WorldMapMultiplayerModels.kt`)

```kotlin
enum class Direction { UP, DOWN, LEFT, RIGHT }
enum class GameAction { A, B, X, Y }
data class MultiplayerPlayer(id, displayName, x/*lon*/, y/*lat*/, action, facingRight, isDriving,
                             carModel, carColor, vehicleRotation, health)
data class MultiplayerNpc(id, x, y, rotation, npcType, ownerId, carModel, carColor,
                          hairId, hairColor, shirtColor, pantsColor, health, isDying, aggroUntil)
```
- `handleMultiplayerMessage(json)` — parsea por `type` y actualiza `remoteEntities`/jugadores.
- `addRemoteEntity(remote)` / `updateNpcsState()` — fusiona NPCs locales + remotos + **policía**
  (`policeManager.activeUnits()` + `remotePolice`) en `uiState.npcs`.
- **Zone Host:** soy Host dentro de ~400 m; el menor `sessionId` cede en solapamiento; solo el Host
  corre la IA y emite `NPC_BATCH_UPDATE`. Mensajes de red: ver **08**.

## ESCOM / Misc / Collectibles (parciales)

- `WorldMapEscom.kt`: `spawnEscomDoors()`, `isInsideEscom(lat, lon)`, `spawnOustedDriver(carLocation)`
  (conductor desalojado al robar coche ocupado, aparece ~2 m).
- `WorldMapMisc.kt`: `startMovementAction(isMovingRight?)`, `startHealthBarTimer(delayMillis)`,
  `triggerWastedSequence()` (congela movimiento, fantasma alpha 0.3, respawn ~80 m del lugar de
  muerte **dentro de la zona ya cacheada**, snapeado a calle — sin teleport a ESCOM ni descarga).
- `WorldMapCollectiblesLogic.kt`: `trySpawningCollectible` (1 cada ~1s, 300–600 m, snapeado),
  `checkCollectibleProximity` (prompt a 15 m, X recoge).

## API pública del ViewModel (para la View) / public VM API (selección)

`connectToMultiplayer(url, name)`, `disconnectFromMultiplayer()`, `moveCharacter(direction)`,
`moveCharacterByAngle(angleRad)`, `updateActionState(action, isPressed)`, conducción
(`steerLeft/steerRight/accelerate/brake(pressed)`), `onInteractButtonPressed()`,
`setMapProvider/requestMapProvider/commitMapProvider/cancelPendingProvider`, zoom
(`zoomIn/zoomOut/onMapZoomChanged/zoomToPlayer`), cámara (`centerOnPlayer/onMapPanStart/onMapPanEnd`),
landmarks (`addLandmarkAtPlayer/move/rotate/scaleX/scaleY/deleteSelectedLandmark/save +
export/importLandmarksToUri/FromUri`), `toggleDesignerMode/showAssetPicker/selectLandmark`,
teleport (`teleportTo(lat,lon)`, `teleportToMetroStation(name)`, `toggleTeleportMenu`),
`takeDamage(amount)`, `heal(amount)`, `onClaimCollectiblePressed`, widgets (`toggleCacheWidget/FpsWidget`).

**Wanted/policía (internal):** `raiseWantedLevel(amount=1)`, `tickWantedDecay(now)`,
`anyAggressorAdjacent(loc, now)`, `handleCarjack(driving, aggressorAdjacent, now)`,
`forceExitVehicle()`, `runPoliceTick(location)`, `fireImpactEffect()`.

---

## Rendering

### `WorldMapScreen.kt` (selector de renderer / renderer switch)
Elige render según `mapProvider`: **OSM nativo** (`NativeOsmMap`), **Google nativo** (Maps Compose),
o **Web** (WebView + Leaflet con HTML de `WorldMapLeafletHtml`). El **fog Compose (`Canvas`) solo se
usa para Google nativo** (OSM nativo y web tienen su propio fog). / Compose `Canvas` fog is used
**only** for the Google native renderer.

### `NativeOsmMap.kt` (osmdroid)
- **`FogOverlay`** anclado al jugador cada frame; rect del tamaño de pantalla a pie, sobredimensionado
  a la diagonal **solo al conducir** (rotación). Evita rellenar ~10× el área cada frame.
- **Over-zoom z20–22:** `MapTileApproximater` + `setMaxZoomLevel(22)` escala z19 (OSM solo sirve tiles
  reales hasta z19). Default OSM = zoom máx 22.
- **Lambda `update` ~30 Hz — mantener barata** (ver 09): landmarks `GroundOverlay` solo re-`setPosition`/
  `setImage` cuando cambia su firma (`landmarkSigCache`); las puertas (`DOORS/`) sí refrescan cada frame;
  ~160 marcadores de metro **culleados por viewport** (`Marker.isEnabled`).
- Patrulla 🚓 fuera de la fog: marcador + línea de ruta (cachés recordadas, **NO** view tags — añadir
  `R.id`s rompía un hack `id+100`/`id+400` y causaba `ClassCastException`).

### `WorldMapLeafletHtml.kt` (Web)
- `#map-wrapper` **dinámico**: `100vw×100vh` a pie; al conducir crece a un cuadrado del tamaño de la
  **diagonal en PÍXELES** (no `vmax`/`calc` — daban esquinas negras en WebViews viejas). `setMapOversize(driving)`.
- Follow-camera con **`map.panBy`** por frame (no `setView`, que hace `_resetView` completo); `setView`
  solo al cambiar zoom o teleport. Tile layer: `keepBuffer:3`, `updateWhenIdle:false`.
- `updateNpcs`: solo dibuja imágenes para `type` `"CAR"`/`"MODULAR"`; el resto cae a SVG. Por eso
  **`POLICE_CAR` se serializa como imagen base64 (type="CAR")** vía `PoliceSpriteManager`, y `POLICE_COP`
  como **👮 base64 (type="MODULAR")**. `updatePolice(playerLat, playerLng, data)` dibuja 🚓+línea fuera de la fog.
- `#fog` div dentro de `#map-wrapper` (rota con el mapa); `drawFog` **cachea el string del gradiente** y
  no re-rasteriza si no cambia.

### `CachingWebViewClient.kt`
Intercepta cada tile de Leaflet y lo cachea en Room. Key = URL normalizada (quita subdominios de
balanceo + parámetros volátiles) hasheada con SHA-256. Permite juego offline en zonas visitadas.

### Sprites (managers singleton, con `clearCaches()` — liberados en `onTrimMemory`)
- `CharacterSpriteManager` — peatones ensamblados con tintado por píxel.
- `VehicleSpriteManager` — 6 modelos × 48 frames de rotación.
- `PoliceSpriteManager` — asset `VEHICLES/POLICE_TOPDOWN` sin repintar (48 frames). Cop = emoji 👮 vía
  `emojiToDrawable` en `WorldMapDrawingUtils.kt`.
- **`nativeDrawableCache`** (declarado en `WorldMapScreen`, usado por `NativeOsmMap`) es un **LRU por
  orden de acceso** (cap ~384); sus claves embeben salud/zoom/frame → nunca volver a `mutableMapOf` (OOM).
- `MapZombieSpriteManager` (`ui/components/`) — carga `ZOMBIS_MOD/z_walk/z_walk_1..9.webp` (9 frames),
  `getZombieDrawable(context, npc, timeMs, scale)`. Liberado en `onTrimMemory`.
- **LOD de emojis (gama baja):** si `uiState.npcEmojiLod` (Ajustes→Jugabilidad), `NativeOsmMap` dibuja los
  NPCs a **>40 m** del jugador como un **emoji barato** (🧍/🚗/🧟/👮 cacheado por tipo+tamaño) en vez del
  sprite/bitmap completo; solo los muy cercanos llevan el asset. Recorta el costo de render. **Solo OSM
  nativo** por ahora (web/Google = trabajo futuro).

---

## 🧟 Modo Zombi Global / Global Zombie Mode

**ES:** Apocalipsis sobre el mapa del mundo abierto (distinto del minijuego de interiores). Toggle
global replicado en multijugador. Modelos + IA del zombi → ver **03**.
**EN:** Apocalypse on the open-world map (distinct from the interior minigame). Global toggle, replicated
in multiplayer. Zombie models + AI → see **03**.

- **Estado:** `WorldMapState.globalZombieMode: Boolean`. El game loop hace `npcAiManager.globalZombieMode = uiState.globalZombieMode` cada tick.
- **Activación:** (a) mano **"Mano del Apocalipsis"** fija en ESCOM (`handleInteraction()` → `toggleGlobalZombieMode()`); (b) **ítem de menú "Activar/Desactivar Apocalipsis"** (Opciones) que funciona en cualquier lugar; (c) **botón flotante de salida** cuando está activo (`exitGlobalZombieMode()`).
- **VM API:** `toggleGlobalZombieMode()` (flip + broadcast `ZOMBIE_MODE_SET`), `exitGlobalZombieMode()`.
- **Daño al jugador (¡crítico!):** los zombis muerden vía `applyNpcContactDamage(location)` y el atropello vía `runOverNpcs(finalLoc, speed)`. **Estas dos llamadas viven en el game loop MIEMBRO de `WorldMapViewModel.kt`** (el activo). La extensión `WorldMapGameLoop.kt` también las tiene pero está **sombreada/muerta** — editar solo el miembro (ver 09).
- **Multijugador:** el Host simula los zombis y los replica por `NPC_BATCH_UPDATE` (conserva `npcType=ZOMBIE` + health/isDying). El toggle viaja en `ZOMBIE_MODE_SET` (global) — relayado por `Multiplayer/server.js` (NO por `MultiplayerInteriores/`). `addRemoteEntity` reconstruye el zombi remoto con `visualConfig=null` y `speed=PERSON_SPEED` (animan). Ver **08**.

### Render del zombi / zombie rendering (3 renderers)

Los **3** renderers revisan `npc.visualConfig != null` ANTES que `ZOMBIE`, así que la rama de peatón
lleva la guarda **`&& npc.type != NpcType.ZOMBIE`** y el seed pone `visualConfig=null`. Si no, el zombi
se dibuja como humano. / All 3 renderers check `visualConfig != null` before `ZOMBIE`, so the pedestrian
branch carries `&& npc.type != ZOMBIE` and the seed nulls `visualConfig`.

- **OSM nativo** (`NativeOsmMap`): drawable de `MapZombieSpriteManager` → `ExactSizeDrawable` (~1.3 m).
- **Google nativo** (`WorldMapScreen`): mismo drawable → `BitmapDescriptor` (en los dos `when`).
- **Web** (`WorldMapScreen`): base64 `type="MODULAR"`. **El frame del `cacheKey` DEBE ser `% 9`** (no `(timeMs/220).toInt()` sin acotar) — si no, cada frame crea un key nuevo y la imagen base64 async nunca se registra → no se ve (ver 09).

**Roles (palette swap):** los 3 renderers incluyen `npc.zombieRole` en el `cacheKey`, aplican el tinte
(en `MapZombieSpriteManager.getZombieDrawable`), un `roleSizeMul` (Tank 1.45×, Runner 0.9×) y pasan
`npc.maxHealth` a `drawHealthBarOnDrawable` (barra proporcional por rol; en web la vida se normaliza a
0-100 en el payload). Burbuja del SCOUT: 📢 sobre la cabeza mientras `screamUntil > now` (render nativo
`screamCache`, espejo del 📞 `callingUntil`). Roles + comportamiento → ver **03**.

- **💥 FX de impacto:** `fireImpactEffect()` tiene **throttle** (`IMPACT_EFFECT_THROTTLE_MS=900`) para que
  las mordidas de zombi/NPC no lo spameen en el centro de la pantalla. Además `takeDamage` y
  `applyNpcContactDamage` **ignoran el daño si `showWastedScreen` o `playerHealth<=0`** (si no, los zombis
  sobre el cadáver re-disparaban el 💥 al morir), y el 💥 **no** se muestra en el golpe mortal.
- **Knockback de zombi:** al golpear (B) un zombi que sobrevive, `performPlayerAttack` lo **empuja** ~7-8 m
  alejándolo del jugador (recoil visible; el Host lo retoma desde `remoteEntities`).
- **Atropello estilo Midnight Club (`runOverNpcs`):** el peatón (PERSON) **esquiva con un sidestep
  ANIMADO, no un teletransporte**. `runOverNpcs` NO mueve al peatón: solo marca un **estado de esquive**
  (`dodgeUntil = now + DODGE_MS`, `dodgeDirLat/Lon` = perpendicular hacia el lado al que ya se inclina) y
  el **Host lo anima** en `NpcAiManager.updateNpcs` (rama `npc.dodgeUntil > now`: se mueve `personSpeed*10`
  perpendicular, **sin snap a la calle**; al expirar, `moveNpc` lo re-engancha → "salta a un lado y
  regresa"). El esquive es **predictivo**: solo si el peatón está **DELANTE** del coche (producto punto con
  el avance > 0) y dentro de `DODGE_TRIGGER_RADIUS` (~7-8 m). Solo lo atropellas yendo **casi a fondo**
  (`spd >= RUN_OVER_EXTREME_SPEED = MAX_SPEED*0.92`) y dentro de `RUN_OVER_RADIUS`. Los **zombis NO
  esquivan** (atropellables siempre). Chocar/rozar un **auto NPC** (`CAR`/`POLICE_CAR`, `CAR_BUMP_RADIUS`)
  lo **empuja a un lado** (rebasas, tipo Toretto, `shove`) + 💥, sin daño al jugador. El 💥 solo salta en
  atropello real o choque de auto (no al esquivar). El estado de esquive vive en `remoteEntities`, se
  reconstruye en `setServerNpcs` y la posición animada se replica vía `MultiplayerNpc` (no hace falta
  añadir campos de esquive al modelo de red).
- **Daño moderado de mordida:** `applyNpcContactDamage` aplica la mordida de zombi con un **cooldown
  GLOBAL** (`ZOMBIE_BITE_TO_PLAYER_MS=650`, `lastZombieBiteMs`) → como mucho una mordida cada ~650 ms
  (`ZOMBIE_BITE_TO_PLAYER_DMG=6`) aunque te rodee una horda. El SCOUT no muerde.
- **Sin "se busca" en apocalipsis:** `raiseWantedLevel` es **no-op** si `globalZombieMode` → pegarle a un
  zombi/civil (o **robar un auto**) NO invoca a la policía del sistema de delitos.
- **Policía del apocalipsis (caza-zombis):** el Host spawnea NPCs `POLICE_COP` (probabilidad, máx
  `POLICE_HUNTER_MAX`) que **persiguen y disparan al zombi más cercano** (`movePoliceHunter` en
  `NpcAiManager`; ayudan a los civiles). **Disparos VISIBLES:** `movePoliceHunter` acumula los disparos en
  `npcAiManager.pendingPoliceShots`; el game loop los vuelca a `uiState.policeShots` (balas, expiran 450 ms
  vía `runPoliceTick`). Los **zombis también muerden a los policías** (escaramuza: los cops pueden caer).
  **Te atacan SOLO si los provocas:** `provokeApocalypsePolice` (VM) les pone `aggroUntil` cuando (a) los
  golpeas o (b) agredes a un civil con un poli **literalmente enfrente** (~33 m). Provocados, persiguen al
  jugador y dañan vía `applyNpcContactDamage` (`isAggroCop`). Render 👮, replicados por `NPC_BATCH_UPDATE`.
- **Hordas migratorias:** el game loop lee `npcAiManager.hordeIncomingAt` y, en cada nueva oleada, muestra
  un aviso en el HUD (vía `interactionPrompt`). La lógica (punto de calor + spawn) está en `NpcAiManager`
  (ver 03). El SCOUT es el aviso *in-game* de que viene una horda.
- **Instancing (apocalipsis = instancia):** `toggleGlobalZombieMode()` → `setZombieInstance(apocalypse)`
  envía `JOIN_INSTANCE` ("apocalipsis"/"normal"), limpia `remoteEntities` y deja que el servidor reenvíe
  el roster del nuevo mundo. Los de "normal" no ven zombis. **No** queda `ZOMBIE_MODE_SET` (eliminado). Ver 08.

### Waypoints de zombi (fuera del fog) / zombie waypoints (outside fog)

🧟 + línea **roja** punteada jugador→zombi para zombis fuera de `NPC_FOG_VISION_METERS`, en modo
apocalipsis. Implementado en los **3** proveedores: OSM nativo (`NativeOsmMap`, `zombieWpCache`/`zombieRouteCache`),
web (Leaflet `updateZombies(...)` + push en `WorldMapScreen`), y Google nativo (Marker 🧟 + Polyline roja en el composable `GoogleMap`).
