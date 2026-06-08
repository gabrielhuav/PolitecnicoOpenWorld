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
     snap a la calle: si distToRoad>0.000025 → reubicar en el borde y v*=0.8. runOverNpcs(loc, v).
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
