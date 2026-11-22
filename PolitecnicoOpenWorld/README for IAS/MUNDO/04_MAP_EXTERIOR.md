# 04 · Open World / `features/map_exterior/`

**ES:** El corazón del juego. El `WorldMapViewModel` (Activity-scoped, ~1426 líneas tras el refactor 2026-06-22) está **partido en
extensiones** (`WorldMap*.kt`) que agrupan la lógica por tema. El estado es `WorldMapState`.
**EN:** The game's heart. `WorldMapViewModel` (Activity-scoped, ~1426 lines after the 2026-06-22 refactor) is **split into
extension partials** (`WorldMap*.kt`) grouping logic by topic. State is `WorldMapState`.

> **🆕 Refactor de tamaño (2026-06-20):** se extrajeron 4 parciales nuevos del VM (bajó de ~3050 a
> ~2600 líneas): `WorldMapCombat.kt` (combate), `WorldMapCampaign.kt` (`setStorySpawn`),
> `WorldMapTeleport.kt` (teleport + Metro/Metrobús) y `WorldMapShineCTO.kt` (easter egg + fade de
> puerta). Todas tocan SOLO miembros `internal`/`public`; el ESTADO sigue en el VM. Los call-sites
> fuera del paquete `viewmodel` (MainActivity, WorldMapScreen) llevan import explícito. Ver 09 §0.

> **Gotcha (patrón histórico — los gemelos concretos YA se de-duplicaron el 2026-06-21):** en Kotlin,
> si una función existe como **miembro privado** en `WorldMapViewModel.kt` Y como **extensión** del mismo
> nombre en un parcial, el **miembro GANA** y la extensión queda muerta. Los 8 gemelos que había
> (`startGameLoop`, `updateVisibleRoads`, `handleMultiplayerMessage`, `addRemoteEntity`,
> `maybeRefetchRoadNetwork`, `triggerWastedSequence`, `spawnOustedDriver`, `updateDestinationRoute`) ya
> se resolvieron (ver `09 §12` y 09 §0/§12): cada función tiene UNA sola implementación
> viva. **El patrón sigue existiendo** (los parciales son extensiones), así que si creas una función
> nueva NO la dupliques como miembro+extensión. / The member-vs-extension twin pattern: member wins,
> extension dies. The 8 twins that existed were all de-duplicated on 2026-06-21 (see `09 §12`).

## Proveedores sin credenciales (2026-11-05)

La configuración de teselas y zoom nativo vive en el modelo compartido `MapProvider`.
El default es OSM_WEB. CARTO sirve marcas de agua sin una clave desde septiembre de 2026
([documentación oficial](https://carto.com/basemaps/apikey/index.html)); no tratar HTTP 200
como prueba de que ese mapa sea válido. Los valores antiguos CARTO y Google se conservan
para compatibilidad, pero `keyFreeProvider` los migra a OSM_WEB. Ajustes solo ofrece OSM,
OSM Web, Esri (calles/satélite) y OpenTopoMap en esta distribución sin credenciales.
Precarga y WebView usan la misma URL y límite de zoom; OpenTopo se limita a z17.
Si una capa Web falla al cargar teselas, Leaflet cambia a OSM y recuerda la solicitud
para evitar volver a pedir el proveedor fallido en cada frame. Una precarga fallida
no se presenta como lista: ofrece OSM. Las URLs nuevas no reutilizan la caché CARTO
con marcas de agua; no se elimina la caché ni los datos del usuario.

---

## Tabla "Key files" (salta directo aquí) / jump straight here

| Tema / Concern | Archivo / File |
|---|---|
| Game loop, multiplayer, NPCs, ESCOM, policía | `viewmodel/WorldMapViewModel.kt` + parciales |
| Proveedores de mapa + descarga/compuertas de tiles | `viewmodel/WorldMapProviders.kt` (NUEVO, refactor) |
| Modo Diseñador / landmarks (Room, edición, import/export) | `viewmodel/WorldMapDesigner.kt` (NUEVO, refactor) |
| Nivel de búsqueda + policía propia + carjack | `viewmodel/WorldMapWanted.kt` (NUEVO, refactor) |
| 🆕 Combate (melee jugador, atropello, daño contacto NPC/zombi, implacable) | `viewmodel/WorldMapCombat.kt` (NUEVO, refactor) |
| 🆕 Campaña/Modo Historia: punto de entrada del spawn (`setStorySpawn`) | `viewmodel/WorldMapCampaign.kt` (NUEVO, refactor) |
| 🆕 Teletransporte: gate de TP + estaciones Metro/Metrobús | `viewmodel/WorldMapTeleport.kt` (NUEVO, refactor) |
| 🆕 Easter egg ShineCTO + fade de puerta ESCOM | `viewmodel/WorldMapShineCTO.kt` (NUEVO, refactor) |
| 🆕 Guardado/carga de partida (Modo Historia, JSON) | `viewmodel/WorldMapSaveGame.kt` + `data/repository/SaveGameRepository.kt` |
| 🆕 Editor del Debug Interiores (líneas rojas/verdes/naranjas) | `viewmodel/WorldMapDebugEditor.kt` + `ui/components/InteriorDebugEditorPanel.kt` |
| Estado UI / UI state | `viewmodel/WorldMapState.kt` (⚠️ campos anotados "LOS POSEE XManager" = fachada combine) |
| 🆕 Managers con SUB-ESTADO propio (Etapa 3 calidad senior; el VM compone `uiState` con `combine`) | `viewmodel/DesignerManager.kt`, `CollectiblesManager.kt`, `CombatManager.kt`, `WantedManager.kt`, `TransitTeleportManager.kt`, `CampaignManager.kt` (tests en `app/src/test/.../viewmodel/*ManagerTest.kt`) |
| 🆕 DI con Hilt (BD/cachés/repos) | `di/AppModule.kt`; VMs `@HiltViewModel`/`@AssistedInject` (ver 01 + `_ARCHIVO/PLAN_DI_hilt.md`) |
| Game loop | `WorldMapViewModel.startGameLoop()` (MIEMBRO; `viewmodel/WorldMapGameLoop.kt` = tombstone tras de-dup 2026-06-21) |
| Multiplayer relay/parse | `viewmodel/WorldMapMultiplayer.kt` (+ `WorldMapMultiplayerModels.kt`) |
| Red de calles / road network | `viewmodel/WorldMapRoadNetwork.kt` |
| Routing / waypoints | `viewmodel/WorldMapRouting.kt` |
| ESCOM (puertas, ousted driver) | `viewmodel/WorldMapEscom.kt` |
| Coleccionables (lógica) | `viewmodel/WorldMapCollectiblesLogic.kt` |
| Misc (startMovementAction, secuencia WASTED, timer barra de vida) | `viewmodel/WorldMapMisc.kt` |
| 🆕 Cámara/zoom (auto/manual, pinch, pan) + toggles de widgets | `viewmodel/WorldMapCameraUi.kt` (+ `autoZoomTarget` en `WorldMapState.kt`) (NUEVO, refactor — extensiones) |
| 🆕 Ajustes (densidad/LOD NPCs) + skin | `viewmodel/WorldMapSettings.kt` (NUEVO, refactor — extensiones) |
| 🆕 Interacción del jugador (abordar/bajar vehículo, puerta/metro/coleccionable, reclamar objeto, teleport directo, toggle apocalipsis zombi) | `viewmodel/WorldMapInteractions.kt` (NUEVO 2026-06-22 — extensiones) |
| 🆕 Salida segura de vehículo (candidatos, fallback y coche abandonado) | `viewmodel/VehicleExitPlan.kt` + `viewmodel/WorldMapVehicleExit.kt` |
| 🆕 Salud del jugador (daño/curación/barra de vida) | `viewmodel/WorldMapHealth.kt` (NUEVO 2026-06-22 — extensiones) |
| 🆕 Items de ESCOM + inyección de coche dinámico (Diseñador) | `viewmodel/WorldMapEscomItems.kt` (NUEVO 2026-06-22 — extensiones) |
| 🆕 Movimiento del jugador a pie/joystick + aduana de colisión + snap-to-road + rescate anti-atasco | `viewmodel/WorldMapMovement.kt` (NUEVO 2026-06-22 — extensiones) |
| Render principal (selector de renderer: OSM/Google/Web) | `ui/WorldMapScreen.kt` (~1325) |
| 🆕 Rama de render Google Maps nativo (`GoogleMapLayer`) | `ui/WorldMapScreenGoogle.kt` (NUEVO, refactor — composable top-level; cachés LRU por parámetro) |
| 🆕 Rama de render WEB / Leaflet en WebView (`WebMapLayer`) | `ui/WorldMapScreenWeb.kt` (NUEVO, refactor — composable top-level; cachés base64 + holders de guarda por-frame por parámetro) |
| 🆕 Overlays/diálogos superpuestos (WASTED, vídeo zombi, prompts, diálogo Prankedy, popup coleccionable, fades puerta ESCOM/metro/metrobús) | `ui/WorldMapScreenOverlays.kt` (NUEVO, refactor) |
| 🆕 Controles en pantalla (D-pad/joystick/acciones, conducción con el MISMO diamante Xbox, salir apocalipsis, pulsación larga Y) | `ui/WorldMapScreenControls.kt` (NUEVO, refactor — `BoxScope.WorldMapControls`) |
| Render nativo osmdroid (fog, over-zoom, NPCs, landmarks) | `ui/NativeOsmMap.kt` |
| 🆕 Render de Prankedy en OSM nativo (+ proyectil) | `ui/NativeOsmMapPrankedy.kt` (NUEVO, refactor — `renderPrankedyOnMap`) |
| 🆕 Overlay de neblina (fog of war) OSM nativo | `ui/NativeOsmMapFog.kt` (NUEVO, refactor — `class FogOverlay`) |
| 🆕 Entrada a interiores data-driven (puerta→ruta) | `domain/models/InteriorEntryCatalog.kt` (NUEVO; lo usa `handleInteraction`) |
| 🆕 IA de NPCs: movers zombi/policía, aggro, seguimiento, distancia | `domain/models/ai/NpcAiManagerMovement.kt` (NUEVO, refactor — extensiones de `NpcAiManager`) |
| 🆕 IA de NPCs: movers de calles/campus (`moveNpc`/`moveLocalNpc`: tráfico, compromiso de intersección, esquive, parking, navGraph) | `domain/models/ai/NpcAiManagerTraffic.kt` (NUEVO, refactor — extensiones de `NpcAiManager`) |
| HTML de Leaflet (WebView) | `ui/WorldMapLeafletHtml.kt` |
| Intercepción de tiles Leaflet | `ui/CachingWebViewClient.kt` |
| Helpers de dibujo / health bar | `ui/WorldMapDrawingUtils.kt` |
| Sprite jugador / vehículo conducido | `ui/components/PlayerCharacter.kt` |
| Controles (D-Pad/joystick/A-B-X-Y) | `ui/components/GameControllers.kt` |
| 🆕 Botón estilo POW compartido (esquinas cortadas + vino; lo usa TITULACIÓN POR COMBATE) | `ui/components/PowButton.kt` |
| Menú anidado de opciones | `ui/components/OptionsMenu.kt` |
| Panel del modo diseñador | `ui/components/Designerpanel.kt` |
| Sprites NPC | `ui/components/CharacterSpriteManager.kt`, `VehicleSpriteManager.kt`, `PoliceSpriteManager.kt` |
| NPC especial Prankedy (IA/VM/render) | `domain/models/ai/PrankedyManager.kt` (ver 03), `viewmodel/WorldMapPrankedy.kt`, `ui/components/PrankedySpriteManager.kt` |
| 🆕 MISIÓN 2 "El rumor" (máquina de fases: esconderse/rumor/brote/plática/mochila) | `viewmodel/WorldMapMission2.kt` (guion/constantes en `domain/models/campaign/mission2/Mission2.kt`; ver `CAMPAIGN/02_MISSION_2.md`). ⚠️ 2026-07-08: la fase 1 "esconderse" ya NO corre aquí — se juega DENTRO del lobby (interiores; `completeMission2Hide`/`failMission2Hide` son los callbacks). Ver 09. |
| 🆕 MISIÓN 3 "Regreso a la ENCB" (viaje/cordón-sigilo/asalto) | `viewmodel/WorldMapMission3.kt` (NUEVO; constantes en `domain/models/campaign/mission3/Mission3.kt`; ver `CAMPAIGN/03_MISSION_3.md`) |
| 🆕 REGISTRO/SELECTOR de misiones (estilo Witcher) | `viewmodel/WorldMapMissionLog.kt` + `ui/components/MissionLogDialog.kt` (catálogo `MissionCatalog.missions`). 🆕 2026-07-04b: el diálogo se hospeda a nivel **AppNavGraph** (`MissionLogHost`, patrón SaveSlotsDialog) → sirve al mapa Y a interiores; REJUGAR ✔ completadas sin tocar el progreso (`replayingMissionId` transitorio); Modo Dev = seguir 🔒 + "TP al objetivo". Ver 09. |
| 🆕 MISIONES SECUNDARIAS (side1 entrega / side2 contención) | `viewmodel/WorldMapSideMissions.kt` (tick; SIN fase persistida: el id del objetivo ES el estado) + `domain/models/campaign/side/SideMissions.kt` (coords/objetivos). Badge SECUNDARIA en el registro. Ver `CAMPAIGN/04_SIDE_MISSIONS.md` y 09. |
| 🆕 EVENTOS DINÁMICOS del mundo (vida urbana: conversación / persecución / mini-brote) | `viewmodel/WorldMapDynamicEvents.kt` (tick del game loop; actores `DYN_*` en `dynamicEventNpcs`, scriptados beeline, no atacables). Mini-brote más probable de NOCHE y con la M3 completada. |
| 🆕 (2026-07-08) VIDA DE CAMPUS ESCOM (estudiantes ambientales del mapa global) | `viewmodel/WorldMapCampusLife.kt` (NUEVO): ~10 estudiantes `CAMPUS_*` dentro del bbox de la ESCOM — deambulan + corrillos de 3 platicando (💬 alternada). Determinista por cubetas de tiempo (sin estado por NPC). Lista `campusNpcs`; excluidos del guardado; pausados en escolta/chase M1 y zombi global. Ver 09. |
| 🆕 ECONOMÍA (dinero del jugador) | `viewmodel/WorldMapEconomy.kt` (`addMoney`, `MissionRewards`, `COLLECTIBLE_MONEY`). `WorldMapState.playerMoney` + chip 💵; persistido en `GameSaveData.playerMoney`. Recompensa SOLO la 1ª vez (hook en `markMissionCompleted`). |
| 🆕 CICLO DÍA/NOCHE | `viewmodel/WorldMapDayNight.kt` (reloj epoch: 1 min real = 1 h juego, ciclo 24 min; `gameHour`/`nightAlpha` en el estado). Velo nocturno = capa Compose renderer-agnóstica en `WorldMapScreen` + chip 🕐. |

---

## Estado / State — `WorldMapState.kt`

```kotlin
enum class MapProvider(displayName) {
  OSM, GOOGLE_MAPS_NATIVE, CARTO_VOYAGER, OSM_WEB, GOOGLE_MAPS, CARTO_DB_DARK,
  CARTO_DB_LIGHT, ESRI, ESRI_SATELLITE, OPEN_TOPO; // 9 proveedores (web = todos menos OSM y GOOGLE_MAPS_NATIVE)
  val isWebProvider: Boolean
}
// El render web pasa maxNativeZoom por proveedor a Leaflet (CARTO=20, OSM/ESRI=19,
// OPEN_TOPO=17, Google=20): a partir de ahí Leaflet escala (over-zoom).
enum class RoadSource { LOADING, LOCAL_DB, NETWORK }
enum class TileSource { LOCAL_OSM, LOCAL_CACHE, NETWORK }
data class PoliceShot(from: GeoPoint, to: GeoPoint, at: Long)
```

**`WorldMapState`** — campos por grupo / fields by group (default `mapProvider = CARTO_VOYAGER`):
- **Mapa/carga:** `currentLocation, isLoadingLocation, zoomLevel, mapProvider, pendingProvider,
  pendingProviderReady, isMapReady, mapLoadProgress, roadSource, tileSource, isRoadNetworkReady`.
- **Render flags:** `showCacheWidget(=true), showFpsWidget, showZoomWidget (widget de nivel de zoom,
  Ajustes→Interfaz), showSpeedometer (=true; velocímetro km/h, solo al conducir), showCoordsWidget
  (widget de coordenadas X/Y/Z, Ajustes→Interfaz, default oculto; X=lon, Y=lat, Z="GLOBAL"),
  showRoadNetwork, isUserPanningMap`.
- **Zoom por estado (constantes en `WorldMapState.kt`):** `ZOOM_ON_FOOT=22`, `ZOOM_DRIVING=21`;
  `autoZoomTarget(isDriving)` elige uno según si conduces (la velocidad ya no mueve la cámara; se eliminó
  `ZOOM_DRIVING_FAST`). `updateAutoZoom()` corre cada tick del game loop y solo actúa cuando CAMBIA el
  objetivo automático (`lastAutoZoomTarget`), así el pinch manual se respeta.
  **Suavizado de zoom:** `WorldMapState` ahora tiene `targetZoomLevel` y `zoomTransitionTicks`.
  `updateAutoZoom()` fija `targetZoomLevel` en vez de cambiar `zoomLevel` de golpe. El game loop
  interpola `zoomLevel` hacia `targetZoomLevel` (10 % de la distancia restante por tick) para
  evitar el "shock" visual al robar/salir de un auto.
- **Cámara fija norte-arriba (2026-11-10, estilo GTA Chinatown Wars):** el mapa NUNCA gira ni se adelanta: siempre centrado en el jugador. El zoom depende SOLO de si conduces (`autoZoomTarget`: a pie `ZOOM_ON_FOOT=22`, manejando `ZOOM_DRIVING=21`), no de la velocidad; `updateAutoZoom()` reasigna `targetZoomLevel` solo al subir/bajar de un vehículo (el pinch manual se respeta) e interpola `zoomLevel` 10 % por tick. El COCHE es lo que rota: `PlayerCharacter` usa `vehicleSpriteHeading(vehicleRotation)` (shared, la misma fórmula que el coche abandonado y los NPC) con frames de 7.5°. Se descartaron la cámara que gira con el coche, el zoom por velocidad y el look-ahead porque mareaban (video 2026-11-10). ⚠️ `PlayerCharacter` reaplica su `modifier` a la caja exterior Y a la imagen del coche: no pasar offsets por ahí.

  `ZOOM_GAMEPLAY_WEB=19` quedó SOLO como nivel máx. de tiles reales que se pre-descargan en web.
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
- **Metro:** `metroStations, nearbyMetroStation, showMetroFade, metroFadeCompleteStation`. **🆕 Zona de
  interacción:** la proximidad del metro usa `METRO_INTERACT_RADIUS_METERS=30.0` y la del **metrobús**
  `METROBUS_INTERACT_RADIUS_METERS=18.0` (más chico). Constantes de nivel de archivo
  en `WorldMapState.kt`; 4× los 15 m de coleccionables/puertas) para metro **y metrobús**, porque el logo de
  Overpass a veces cae sobre un edificio inaccesible por calle y con snap-to-road no se podía pisar. Esa zona
  se **DIBUJA** (círculo naranja translúcido) en **web** (`updateMetro`, `L.circle`) y **OSM nativo**
  (`Polygon.pointsAsCircle`, tag `route_overlay_tag+600`, culleada por viewport, bajo el icono). Google nativo = pendiente.
- **Prefetch offline (solo OSM nativo):** `zonePrefetchActive, zonePrefetchProgress, zoneOfflineReady, zoneOfflineWarning`.
- **Creador de rutas:** `routeDebugWaypoints, isParkingSlotMode, currentWayId(=100)`.
- **🆕 Economía / día-noche (2026-07-08):** `playerMoney` (persistido en `GameSaveData.playerMoney`;
  +$25 por coleccionable, recompensas por misión vía `MissionRewards`), `gameHour` (0-23) y
  `nightAlpha` (0..0.38, velo nocturno). Ver `WorldMapEconomy.kt` / `WorldMapDayNight.kt`.

---

## Game loop — `WorldMapGameLoop.kt` / `WorldMapViewModel.startGameLoop`

**ES:** Coroutine en `Dispatchers.Default`, **tick cada 33 ms** (~30 Hz). Arranque: espera ubicación,
carga calles (caché Room → celda estática `datos-calles` → Overpass con respaldos y backoff → malla
local transitable si falla). La malla no se persiste y `maybeRefetchRoadNetwork` conserva los reintentos
de calles reales en segundo plano. La cobertura inicial y la generación mensual se documentan en
`tools/calles/README.md`. Luego, por tick:
**EN:** Coroutine on `Dispatchers.Default`, **tick every 33 ms** (~30 Hz). Startup: wait for location,
load roads (Room cache → static `datos-calles` cell → Overpass backups with backoff → traversable local
grid on failure). The grid is not persisted and `maybeRefetchRoadNetwork` keeps retrying real roads in
the background. Initial coverage and monthly generation are documented in `tools/calles/README.md`. Then,
per tick:

```
1. ESCOM sync: dentro de ESCOM + red lista → `spawnEscomItems` (ya **NO** spawnea la mano; solo marca el flag); fuera → limpia.
2. cada 30 ticks → trySpawningCollectible; siempre → checkCollectibleProximity, checkDestinationArrival.
3. cada 30 ticks (si hay marker) → updateDestinationRoute.
4. si playerAction==SPECIAL → performPlayerAttack (golpe a NPCs Y jugadores remotos, ATTACK_RADIUS=0.00008 ~9 m, cuerpo a cuerpo).
   Si golpea a un jugador remoto, envía `PLAYER_ATTACK_HIT { targetId, damage }` al servidor.
5. si isDriving && !WASTED → física del coche:
     girar ±2°/tick (solo con velocidad), gas→+ACCELERATION (cap MAX_SPEED), freno→-BRAKING_FRICTION
     (reversa hasta -MAX_SPEED/2), inercia al soltar. dx=sin(rot)*v, dy=cos(rot)*v.
     **fuera de la calle (curva/bifurcación):** en vez de chocar y parar, **auto-direcciona** el coche
     hacia la calle (muestrea un punto ~22 m adelante en la red, gira suave hacia ahí) y baja la velocidad
     **proporcional** al desvío (`overshoot`); `maxRoadRadius=0.00004` (más holgado). Así, dejando
     acelerar, sigue la carretera. runOverNpcs(loc, v).
6. applyNpcContactDamage(loc)  // NPCs agresivos en embestida pegan a TU jugador (cada cliente al suyo)
7. si red lista && !WASTED → runPoliceTick(loc)
7b. 🆕 tras el `when` de misiones (escolta/chase/M2/M3/SECUNDARIAS): si mundo completo && !WASTED →
    runDynamicEventsTick(loc) (eventos ambientales); siempre → updateDayNightTick() (throttle ~1 Hz)
8. maybeRefetchRoadNetwork(loc); cada 5 ticks → updateVisibleRoads(loc)
9. si red lista, cada 3 ticks → setServerNpcs(npcs remotos) → npcAiManager.updateNpcs(loc, isHost)
     → si soy Host: aplicar pendingDespawns, volcar processedNpcs a remoteEntities → updateNpcsState()
     → enviar PLAYER_UPDATE; si Host: NPC_DESTROY de despawns + NPC_BATCH_UPDATE de NPCs activos (dist<=0.0012)
```
Constantes del VM / VM constants: `MAX_SPEED=0.000017`, `ACCELERATION=0.0000003`,
`BRAKING_FRICTION=0.000001`, `ATTACK_RADIUS=0.00008`, `RUN_OVER_RADIUS=0.00003`,
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

> **🆕 ZONAS LIBRES (campus): ESCOM y ENCB.** Dentro del bounding box de ESCOM (`ESCOM_BASE_LAT/LON` ±
> `ESCOM_OFFSET`) o de la **ENCB** (`ENCB_BASE_LAT=19.5001588`, `ENCB_BASE_LON=-99.1450298`, `ENCB_OFFSET=0.0012`,
> ~130 m) se **suspende el snap-to-road**: `moveCharacter`/`moveCharacterByAngle` consultan
> `isFreeMovementZone(lat,lon)` (= `isInsideEscom || isInsideEncb`, miembros `internal`) y mueven al jugador a la
> coordenada `(x,y)` libre (tras la aduana de choque de bardas). **Prankedy** hace lo mismo: `runPrankedyTick`
> calcula `freeZone` desde la posición del jugador y, si está en campus, **apaga el snap** (`snapToRoad` devuelve
> el punto sin tocar) → persigue en **línea recta (steer-to-target)** a `p_run`. El estado es **per-tick**: al
> salir del perímetro vuelve el snap normal (salvo que entre al box de ESCOM, también libre). Ver 03 (Prankedy) y 07.
>
> **🆕 Regla visual simétrica (ESCOM y ENCB):** `updateVisibleRoads(location, force)` comprueba al inicio
> `isFreeMovementZone(location)`; si es true **vacía `_roadNetworkFlow` (emptyList) y hace `return`** (salta el
> filtro en `Dispatchers.Default`), de modo que **dentro de cualquiera de los dos campus NO se pintan las líneas
> de calles** (las amarillas de Overpass). Al salir del box recupera el filtrado normal. Misma condición que el
> movimiento libre, así ambas zonas quedan limpias.

- `ensureIndex()` / `candidates(loc): List<Seg>` / `getNearestPointOnNetwork(t): GeoPoint` /
  `project(p, v, w): GeoPoint` (proyección punto-segmento).
- `pack(r, c): Long`, `cell(v): Int` (celda de grid), `CELL` (tamaño de celda).
- **Ruta de waypoints (greedy):** `calculateRouteOnNetwork(from, to, network)`, `buildRoadGraph`,
  `nearestGraphNode`, `findRoadRoute` (BFS/greedy sobre grafo de nodos únicos), `rebuildRoadNodeGrid`,
  `nearbyRoadNodes`. **Claves `Pair<Double,Double>`** (sin allocs de String por paso).
- `updateDestinationRoute()` dibuja polilínea azul punteada; el marker se auto-borra a `destinationArrivalThreshold` (20 m).
- **🆕 Rescate anti-atasco (TP a metro sobre edificio):** `moveCharacter`/`moveCharacterByAngle` (MIEMBROS) tienen
  un guard que NO mueve si la calle más cercana al destino está a `> MAX_SNAP_DISTANCE_DEG` (~33 m) — evita "TP a
  calle al azar" durante recargas de red. **Pero** si el TP deja al jugador ATRAPADO lejos de toda calle (estación
  de metro encima de un edificio), ese guard lo dejaba inmóvil tras una "pared invisible". Fix: `rescueIfStuckOffNetwork(loc, step)`
  (miembro privado) — si la posición ACTUAL también está a `> MAX_SNAP` de la red (atasco real, no glitch), al moverse
  arrastra al jugador `step*4` hacia la calle más cercana hasta engancharlo. En juego normal (jugador sobre la calle)
  no se dispara. Ver 09.

> **Pendiente:** el router open-world sigue siendo **greedy** (no A*). El servidor zombi sí usa
> Dijkstra (ver 08). / Open-world router is still greedy (no A*); the zombie server uses Dijkstra.

---

## Red de calles / Road network — `WorldMapRoadNetwork.kt`

- **Inicio degradado:** `OfflineRoadNetwork.kt` crea cuatro corredores locales para peatones y autos,
  centrados en la ubicación inicial, cuando Overpass no entrega una red antes del límite de arranque.
  `RoadSource.OFFLINE_FALLBACK` lo expone como **Malla local** en el widget; permite entrar al sandbox
  sin afirmar que son datos descargados.
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
  **Nuevo:** procesa `PLAYER_ATTACK_HIT` (reenviado por el servidor) para aplicar daño al jugador
  local si fue golpeado por otro jugador en melee.
- `addRemoteEntity(remote)` / `updateNpcsState()` — fusiona NPCs locales + remotos + **policía**
  (`policeManager.activeUnits()` + `remotePolice`) en `uiState.npcs`.
- **Zone Host:** soy Host dentro de ~400 m; el menor `sessionId` cede en solapamiento; solo el Host
  corre la IA y emite `NPC_BATCH_UPDATE`. Mensajes de red: ver **08**.

## ESCOM / Misc / Collectibles (parciales)

- `WorldMapEscom.kt`: `spawnEscomDoors()`, `isInsideEscom(lat, lon)`, `spawnOustedDriver(carLocation)`
  (conductor desalojado al robar coche ocupado, aparece ~2 m).
- **🆕 Estacionamiento compartido exterior↔lobby:** los nodos `isParkingSlot` de `escom_navgraph.json` (coords
  locales 0-1) son la FUENTE ÚNICA: el exterior spawnea autos NPC ahí, y el **lobby interior** dibuja los mismos
  autos como escenografía (`ParkedCarsLayer`, ver 05) vía `domain/models/map/CampusParkingCatalog`. Un solo JSON
  para ambas vistas; expandible a otras universidades con una línea en el catálogo.
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
teleport (`teleportTo(lat,lon)`, `teleportToMetroStation(name)`, `teleportToMetrobusStation(name)`,
`toggleTeleportMenu`; 🆕 el diálogo de **Puntos de Teletransporte** —en `WorldMapScreen`, solo Modo
Desarrollador— tiene secciones **COLAPSABLES** "🚇 Metro" y "🚌 Metrobús" (▶/▼): por defecto cerradas,
al pulsarlas despliegan su buscador + estaciones (`metroStations`/`metrobusStations`) → `teleportTo*Station`,
para que la lista de TP no crezca de más),
`takeDamage(amount)`, `heal(amount)`, `onClaimCollectiblePressed`, widgets (`toggleCacheWidget/FpsWidget`).

> **`onInteractButtonPressed` (botón Y, MIEMBRO):** sube/baja del coche. 🆕 (2026-07-13) A PIE el
> toque corto de Y actúa **al SOLTAR**; **MANTENER Y ~450 ms abre el INVENTARIO del mapa**
> (`WorldInventoryDialog`, solo lectura: los mismos objetos que interiores vía
> `currentInteriorInventory`; `showWorldInventory` + `toggleWorldInventory`/`worldInventoryUnlockedSlots`
> en `WorldMapInteractions.kt`; hold en `WorldMapScreenControls`). `INTERACT_RADIUS` bajó
> **0.00018→0.00008 (~9 m)**: te subía a autos "medio lejos". Si no hay coche civil (CAR) en
> `remoteEntities` dentro de `INTERACT_RADIUS`, intenta **subir a una PATRULLA** (POLICE_CAR) vía
> `policeManager.boardPatrol(id)`: la roba, difunde `POLICE_DESTROY` y fija `wantedLevel=5` (robar una
> patrulla = máximo nivel de búsqueda). **Skin conducible:** `isDrivingPoliceCar` en `WorldMapState` hace
> que `PlayerCharacter` dibuje el asset de `PoliceSpriteManager` (overlay común a los 3 renderers).
> **Al bajarte**, el coche queda como `type=CAR` con `Npc.isPoliceSkin=true` (no POLICE_CAR, que la IA
> despawnearía): los 3 renderers lo dibujan como patrulla con `type==POLICE_CAR || isPoliceSkin`, y es
> re-abordable (al re-subir, `isDrivingPoliceCar = carNpc.isPoliceSkin`). Ver 09.

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

> **🆕 Refactor (2026-06-20):** los overlays/diálogos superpuestos (pantalla WASTED, vídeo zombi,
> prompts de interacción, diálogo de Prankedy, popup de coleccionable y los fades de puerta ESCOM/
> metro/metrobús) se extrajeron al composable `WorldMapOverlays` en `ui/WorldMapScreenOverlays.kt`
> (mismo paquete). `WorldMapScreen` ahora solo los invoca (`WorldMapOverlays(uiState, viewModel,
> onNavigateToInterior)`). MVVM intacto. Ver 09 §0.

> **Culling de NPCs = borde del fog:** `NPC_CULL_MARGIN_M=0` (antes 15) → los 3 renderers (que usan
> `npcVisionRadiusMeters() = NPC_FOG_VISION_METERS + margen`) dibujan civiles SOLO dentro de los 70 m del
> fog (antes hasta 85 m → "veo NPCs fuera del fog"). Policía fuera del fog = waypoint 🚓 (`wantedLevel>0`),
> Prankedy = render propio sin culling → **ambos siempre visibles**. Ver 09.

### `NativeOsmMap.kt` (osmdroid)
- **`FogOverlay`** anclado al jugador cada frame; rect del tamaño de pantalla siempre (cámara norte-arriba:
  `mapOrientation = 0f`, ya no se sobredimensiona por rotación). `FogOverlay.rotated` quedó sin uso (siempre `false`). Evita rellenar ~10× el área cada frame.
- **Over-zoom z20–22:** `MapTileApproximater` + `setMaxZoomLevel(22)` escala z19 (OSM solo sirve tiles
  reales hasta z19). Default OSM = zoom máx 22.
- **Lambda `update` ~30 Hz — mantener barata** (ver 09): landmarks `GroundOverlay` solo re-`setPosition`/
  `setImage` cuando cambia su firma (`landmarkSigCache`); las puertas (`DOORS/`) sí refrescan cada frame;
  ~160 marcadores de metro con el **icono `metro_cdmx/icon.webp`** (24 dp, `ExactSizeDrawable`), **culleados
  por viewport** (`Marker.isEnabled`).
- **Icono del Metro en los 3 renderers (paridad):** cada estación de `metroStations` (cargado de
  `res/raw/metro.json`) muestra `metro_cdmx/icon.webp`. OSM nativo = `Marker` 24 dp; **web** = `updateMetro`
  en `WorldMapLeafletHtml` (img fija ~26 px vía `file:///android_asset/`, push con guarda desde
  `WorldMapScreen`, `lastWebMetroHolder` + heartbeat); **Google nativo** = `Marker` 24 dp con
  `BitmapDescriptor` cacheado. Tamaño FIJO en pantalla (no metros), como los demás POIs de metro.
  **🆕 Zona de interacción visible (60 m):** además del icono, web (`metroZones`/`L.circle` en `updateMetro`) y
  OSM nativo (`metroZoneCache`/`Polygon.pointsAsCircle`, tag `+600`) dibujan un **círculo naranja translúcido**
  del tamaño de `METRO_INTERACT_RADIUS_METERS` bajo el logo, para que se vea desde dónde se puede entrar aunque
  la estación caiga sobre un edificio inaccesible por calles. Google nativo = pendiente.
  **🆕 METROBÚS en el mapa global:** el OSM nativo ya dibujaba sus estaciones; ahora el **web** también
  (`updateMetrobus` en `WorldMapLeafletHtml` + push en `WorldMapScreenWeb`, icono `TRANSIT/METROBUS/icon.png` en
  ROJO + su círculo de zona). Antes el web NO marcaba el metrobús → no se sabía dónde entrar. Google nativo = pendiente (solo metro).
- Patrulla 🚓 fuera de la fog: marcador + línea de ruta (cachés recordadas, **NO** view tags — añadir
  `R.id`s rompía un hack `id+100`/`id+400` y causaba `ClassCastException`).

### `WorldMapLeafletHtml.kt` (Web)
- `#map-wrapper` **fijo**: `100vw×100vh` siempre. La cámara es norte-arriba: el mapa no gira (solo el sprite del
  coche, vía `vehicleSpriteHeading`), así que ya no hay `setMapOversize`/`setMapRotation`/`applyVehicleViewport`. `WebVehicleRenderPlan` se eliminó; si el usuario panea, el único avatar auxiliar es el marcador Leaflet (`updatePlayerMarker(lat, lng, isUserPanningMap)`).
- Follow-camera con **`map.panBy`** por frame (no `setView`, que hace `_resetView` completo); `setView`
  solo al cambiar zoom o teleport. Tile layer: `keepBuffer:3`, `updateWhenIdle:false`.
- `updateNpcs`: solo dibuja imágenes para `type` `"CAR"`/`"MODULAR"`; el resto cae a SVG. Por eso
  **`POLICE_CAR` se serializa como imagen base64 (type="CAR")** vía `PoliceSpriteManager`, y `POLICE_COP`
  como **👮 base64 (type="MODULAR")**. `updatePolice(playerLat, playerLng, data)` dibuja 🚓+línea fuera de la fog.
- `#fog` div dentro de `#map-wrapper` (que ya no rota); `drawFog` **cachea el string del gradiente** y
  no re-rasteriza si no cambia.

### `CachingWebViewClient.kt`
Intercepta cada tile de Leaflet y lo cachea en Room. Key = URL normalizada (quita subdominios de
balanceo + parámetros volátiles) hasheada con SHA-256. Permite juego offline en zonas visitadas.

### Sprites (managers singleton, con `clearCaches()` — liberados en `onTrimMemory`)
- `CharacterSpriteManager` — peatones ensamblados con tintado por píxel.
- `VehicleSpriteManager` — modelos de `CarModel`, base blanca tintada por píxel. 🆕 **Extensible:** cada
  `CarModel` (en `domain/models/map/Npc.kt`) lleva `dirName/prefix/frameCount(=48)/tintable(=true)` y
  `collisionDimensions` (caja física, deliberadamente menor que el sprite). **Añadir un
  vehículo = 1 línea en el enum + su carpeta de frames en `assets/SPRITES/VEHICLES/<dirName>/`** (`<prefix>000..0NN.webp`).
  `tintable=false` = asset ya coloreado (se dibuja sin palette swap; ignora `carColor`). El NOMBRE del enum es el id
  que viaja por red (`CarModel.valueOf`) y guardado: AÑADE libremente, NO renombres/elimines entradas. Los nuevos
  modelos aparecen solos donde se itere `CarModel.entries` (tráfico `…random()`, estacionamiento del lobby).
- `PoliceSpriteManager` — asset `VEHICLES/POLICE_TOPDOWN` sin repintar (48 frames). Cop = emoji 👮 vía
  `emojiToDrawable` en `WorldMapDrawingUtils.kt`.
- **`nativeDrawableCache`** (declarado en `WorldMapScreen`, usado por `NativeOsmMap`) es un **LRU por
  orden de acceso** (cap ~384); sus claves embeben salud/zoom/frame → nunca volver a `mutableMapOf` (OOM).
- `MapZombieSpriteManager` (`ui/components/`) — carga `ZOMBIES_MOD/z_walk/z_walk_1..9.webp` (9 frames),
  `getZombieDrawable(context, npc, timeMs, scale)`. Liberado en `onTrimMemory`.
- `PrankedySpriteManager` (`ui/components/`) — sprites del **NPC especial Prankedy**: frames `.webp` de
  `assetsNPC/Prankedy/{p_idle,p_walk,p_run,p_run_tanque,p_attack}` + proyectil `p_objeto`, con `LruCache`
  + emoji de fallback. Render en **OSM nativo** (`renderPrankedyOnMap` en `NativeOsmMap`, ~1.3 m como un
  peatón, **sin** indicador flotante, proyectil interpolado encima de la niebla) **y web** (`updatePrankedy`/
  `updatePrankedyProjectile` en `WorldMapLeafletHtml`, base64 por frame empujado desde `WorldMapScreen`).
  Google nativo = pendiente. IA/comportamiento → ver 03.
- **🆕 Línea GPS de campaña (Modo Historia):** ruta A* `findRoadRoute(ENCB, ESCOM)` (en `WorldMapRouting.kt`,
  sobre la red vial) → `WorldMapState.campaignRouteWaypoints`, dibujada como **`Polyline` VERDE VIVO (`#00E676`, gruesa)** en OSM nativo
  (`NativeOsmMap`, tag `route_overlay_tag+900`, `overlays.add(0,…)` → sobre teselas, bajo personajes/HUD) y en
  web (JS `updateCampaignRoute` en `WorldMapLeafletHtml`). La activa `maybeSpawnPrankedyCompanion` y la oculta
  `maybeHideCampaignRouteNearEscom` (~100 m de ESCOM) en `WorldMapPrankedy.kt`. Google nativo = pendiente. Ver 07.
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
- **Activación:** (a) **ítem de menú "Activar/Desactivar Apocalipsis"** (Opciones) que funciona en cualquier lugar; (b) **botón flotante de salida** cuando está activo (`exitGlobalZombieMode()`). *(La antigua mano "Mano del Apocalipsis" en ESCOM fue ELIMINADA — `spawnEscomItems` ya no la crea; ver 09.)*
- **Debug Interiores (`showInteriorDebugOverlay`, menú Mapa):** además de los puntos de edificios + bbox de ESCOM, dibuja los **caminos del `navGraph`** (VERDE=peatonal, NARANJA=autos) y las formas de `exterior_collisions.json`: polígonos/barreras físicas en **ROJO** y `NAV_GUIDE` en **AZUL**. Las cuatro clases del modelo llevan `@Serializable`: si falta esa anotación, la carga atrapa el error y deja el mundo sin colisiones. El polígono `EDIFICIO_ESCOM_TECHO_CENTRAL` protege el techo central reproducido en Motorola; (2026-11-16: ya no es un rectángulo recto sino un cuadrilátero girado ~25° que sigue el techo del edificio central; los demás edificios del campus, incluido el conector y el ala este, siguen SIN colisión); cubre peatón, vehículo y tráfico mediante el resolvedor compartido. `exteriorCollisions` se expone en `WorldMapState`. OSM nativo (`NativeOsmMap`) + web (`updateInteriorPaths` recibe `{paths, blocks, walls}`); Google nativo pendiente. Ver 09.
- **🆕 EDITOR del Debug Interiores (`WorldMapDebugEditor.kt` + `InteriorDebugEditorPanel.kt`):** con el overlay activo aparece una **barra horizontal abajo** (los controles de movimiento se **ocultan** al editar) para **EDITAR** las líneas **DIBUJANDO con el dedo** (estilo Paint): eliges herramienta `DebugEditTool` (WALL=barda roja, BLOCK=zona roja, NAV_PED=verde peatonal, NAV_CAR=naranja autos) y **arrastras** sobre el mapa → línea (WALL/NAV_*) o rectángulo (BLOCK); con herramienta activa el mapa NO panea (touch consumido), con `NONE` vuelve a panear. **Deshacer** (último trazo) / **Limpiar** / **Exportar/Importar JSON** (formato `exterior_collisions.json` + sección `navPaths`). El dibujo es una **capa Compose sobre el mapa** (`InteriorDebugDrawSurface`, en `WorldMapScreen`) que funciona con **cualquier renderer** (web/OSM/Google) — clave porque el proveedor por defecto es WEB (Leaflet): captura el gesto desde el `ACTION_DOWN` (`awaitEachGesture`+`consume()`) para que el mapa NO panee, convierte pantalla↔geo con Web Mercator (`256·densidad·2^zoom`, centro = jugador) y commitea con `commitDebugStroke`. En modo debug el mapa **se mantiene centrado en el jugador** (la capa asume ese centro). Estado nuevo en `WorldMapState`: `debugEditTool`, `debugEditWalls`, `debugEditBlocks`, `debugEditNavPed`, `debugEditNavCar`.
- **🆕 Guardado de partida (Modo Historia):** API del VM en `WorldMapSaveGame.kt` (`saveGame`/`loadGame`/`buildSaveData`/`restoreSaveData`) + `SaveGameRepository` (JSON). Campos del VM `campaignSchoolId`/`inCampaign`. Ver 07/09. **Fix de `setStorySpawn`:** ahora se comporta como un teletransporte (`gateMapDownloadAfterTeleport()` + reset de `lastNetworkFetchLocation`/`lastFetchAttemptMs`/`npcWarmupCycles`) para que "COMENZAR/CARGAR" SÍ carguen y suelten al jugador (antes `prepareMapForEntry`, idempotente por `mapPrepStarted`, dejaba la 2ª entrada cargando para siempre). Ver 09.
- **VM API:** `toggleGlobalZombieMode()` (flip + broadcast `ZOMBIE_MODE_SET`), `exitGlobalZombieMode()`.
- **Daño al jugador (¡crítico!):** los zombis muerden vía `applyNpcContactDamage(location)` y el atropello vía `runOverNpcs(finalLoc, speed)`. **Viven en el game loop, que es el MIEMBRO `startGameLoop()` de `WorldMapViewModel.kt`.** (2026-06-21: el gemelo de `WorldMapGameLoop.kt` se de-duplicó — ese archivo es ahora un **tombstone**; `startGameLoop` es solo miembro, e incluye además el **audio** fusionado: caminar/correr/coche/zombi.) Editar el game loop = editar el miembro en `WorldMapViewModel.kt`.
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
  esquivan** (atropellables siempre). Cada coche usa un **rectángulo orientado en metros**
  (`VehicleFootprint`), no un radio: tráfico solo choca si las carrocerías se tocan, se separa por el
  vector mínimo y reduce la velocidad del jugador a 35 % + 💥. El 💥 solo salta en atropello real o
  choque de auto (no al esquivar). El estado de esquive vive en `remoteEntities`, se
  reconstruye en `setServerNpcs` y la posición animada se replica vía `MultiplayerNpc` (no hace falta
  añadir campos de esquive al modelo de red).
- **Obstáculos y descenso:** al conducir, `vehiclePathObstacle` barre el rectángulo cada ≤0.5 m contra
  edificios y bardas; se conserva la última posición válida, sin knockback artificial. Al bajar,
  `exitVehicle` deja el coche en su centro y prueba primero el lado del conductor, luego copiloto y
  diagonales traseras; el carjack forzado amplía a un anillo de 1.7–3 m. Si no hay hueco manual, el
  jugador permanece dentro y recibe un aviso localizado. El spawner de estacionamiento también rechaza
  una carrocería que cubra al jugador. Debug: Logcat `VehicleCollision` emite `traffic`, `building`,
  `wall` o `exit-blocked`.
- **Índice de barreras y seguimiento de la auditoría (2026-11-16):** `ExteriorCollisionsConfig.barrierIndex` (`BarrierIndex.kt`, propiedad `by lazy` no serializada) guarda solo los polígonos/bardas con rol BARRIER y su caja en grados. `obstacleAt` y `resolveVehiclePath` descartan por caja SIN asignar memoria lo que está lejos (≈50× más rápido lejos de ESCOM en JVM; dentro del edificio igual); la geometría exacta es la misma y `BarrierIndexEquivalenceTest` la compara contra la referencia sin índice (`obstacleAtUnindexed`/`resolveVehiclePathUnindexed`). Si añades un campo que cambie qué cuenta como barrera, actualiza el índice. `resolveTrafficImpact`: si el punto previo ya solapa, el jugador sale por el vector mínimo y el tráfico NO se desplaza (antes se separaba ≈2×). `selectForcedVehicleExit` añade el tramo "fuera de la carrocería Y fuera de todo edificio BARRIER" (`ExteriorCollisionsConfig.isInsideBarrier`) antes del último recurso. Pendiente (necesita teléfono): precalentar en segundo plano los frames vecinos del sprite del jugador, solo si `gfxinfo` mide tirones al girar.
- **Resolvedor exterior único (P2):** `VehicleCollisionGeometry.kt` concentra las formas físicas: círculo para avatares/peatones y rectángulo orientado para coches. `resolveCirclePath` y `resolveVehiclePath` hacen el barrido estático y devuelven la última posición válida y el motivo. Solo `BARRIER` bloquea; los 48 `CARRIL_*` heredados se clasifican como `NAV_GUIDE`, por lo que guían tráfico sin crear paredes invisibles. Botones y joystick usan el mismo guardia de jugador a pie; tráfico usa el mismo barrido OBB. En builds debug, Logcat `WorldCollision` añade actor, colisionador, motivo y destino bloqueado.
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

### Choque coche-coche (contacto persistente)

`runOverNpcs` (WorldMapCombat.kt) separa **física** de **evento**: mientras haya contacto con un vehiculo, el jugador vuelve al ultimo punto seguro y frena (x0.35) en cada tick, pero el impacto (efecto, miedo, log) se emite una sola vez por par gracias a `VehicleContactLatch` (shared, `RELEASE_MS = 400`). Los candidatos se ordenan por id (`firstContactingId`) porque `remoteEntities` es un `ConcurrentHashMap`. Coches `PARKED` nunca se desplazan.

### Rumbo seguro al subir a un coche (`safeBoardingHeading`)

Al subir (`WorldMapInteractions.kt`: civil y patrulla) el rumbo inicial ya no es siempre `rotationAngle + 90`: `ExteriorCollisionsConfig?.safeBoardingHeading(position, preferido, dimensiones)` (`shared/.../domain/models/map/VehicleBoardingHeading.kt`) conserva el preferido si el coche puede avanzar 3 m sin tocar una barrera y, si no, busca el rumbo libre más cercano girando de 15° en 15° (alternando lados). Sin configuración o con todo bloqueado devuelve el preferido. Motivo: un coche aparcado de cara a un edificio arrancaba con `reason=building` en cada ciclo y acelerar no lo movía (medido: 80 líneas, 0 m; ahora avanza ~7 m). Pruebas: `VehicleBoardingHeadingTest`.

### Escenarios de depuración por adb (solo build debug)

`DebugScenarioReceiver.kt` (registrado en `MainActivity.onCreate`, se anula en `onDestroy`) y `WorldMapDebugScenarios.kt` (`WorldMapViewModel.applyDebugScenario`) permiten jugar sin multitoque. Con el mundo ya abierto: `adb shell am broadcast -a ovh.gabrielhuav.pow.DEBUG_SCENARIO --es name <escenario> [extras]`. En release NO se registra ni hace nada (`BuildConfig.DEBUG`).

| `name` | Qué hace |
|---|---|
| `controls` | Solo aplica los controles (ver extras). |
| `drive_open` | Pone al jugador conduciendo. Extras: `lat`, `lon`, `heading`, `speedPct` (0-100 de `MAX_SPEED`). |
| `board_facing_building` | Crea un coche aparcado de cara al edificio del caso DEV-08 y sube a él (comprueba el rumbo corregido). El coche mira a 296° la pared este de "ESCOM techo 3" (los techos del arte), 7.5 m al este del cruce de esa pared con su latitud: la carrocería no toca el edificio pero 3 m adelante sí, así que `safeBoardingHeading` corrige el rumbo (medido con las colisiones reales: ~356°). A ≤ 6 m la carrocería ya toca, todo sale bloqueado y se devuelve el preferido sin corregir; a ≥ 9.5 m no hay pared. ⚠️ Hasta el 2026-11-21 el punto quedaba 4 m DENTRO de ese techo y la prueba no comprobaba nada. Tras subir, la aduana de calle arrastra el coche hacia la vía más cercana (el punto está a ~13 m de una). |
| `steal_parked` | Lleva al jugador junto al coche aparcado más cercano y lo roba (estrella y conductor desalojado). |
| `overlay` | Activa (`--es on 0` apaga) el overlay de colisiones "Debug Interiores": rojo = físico, azul = guía. Abre encima el editor de líneas (botón Exit para cerrarlo). Con `lat`/`lon` mueve ahí al jugador a pie. |
| `footprints` | Activa (`on 0` apaga) el prototipo de mapa cenital estilizado. Extras: `lat`, `lon`, `zoom`, `fpstyle`. |

Controles mantenidos hasta que se cambien: `--es gas 1|0`, `--es brake 1|0` (con el coche parado, frenar da reversa), `--es steer left|right|none`. `--es trace <s>` (1–30) registra cada 250 ms posición, velocidad, rumbo y metros a la vía de coches más cercana (`roadM`). El resultado se escribe en el logcat con la etiqueta `DebugScenario`. Ejemplo de vuelta en círculo: `--es name drive_open --es speedPct 60 --es gas 1 --es steer right`. ⚠️ Si el coche sigue rodando, el siguiente `drive_open` puede perderse (el ciclo de juego escribe encima): frena antes o mándalo dos veces.

### Calentamiento de sprites del coche del jugador

`PlayerCharacter.kt` (rama conductor) lanza un `LaunchedEffect` en `Dispatchers.Default` que pide a `VehicleSpriteManager.getTintedCarNpc` (o `PoliceSpriteManager.getPoliceCar`) los 48 giros, de los más cercanos al actual hacia fuera. Sin él cada rumbo nuevo decodificaba, escalaba y tintaba en el hilo principal. Medido en un Motorola One Fusion (debug, 4 arranques en frío por lado): huecos > 250 ms entre cuadros 11.5 → 6.75 por ventana y huecos > 600 ms de ~17 a ~6. Es una muestra pequeña; en release el efecto no se ha medido (no hay escenarios en release).

### Logs de depuración limitados

`logVehicleCollision` (`WorldMapVehicleExit.kt`) escribe como mucho una línea cada 500 ms por tipo de motivo (antes, una por ciclo: 16-20 por segundo); el log `VehicleViewport` de `WorldMapScreen.kt` ya no incluye la rotación, así que solo se escribe en cambios de modo.


### ⚠️ Orden de inicialización: `textoCarjack` antes del `init`

En `WorldMapViewModel` el texto del aviso de carjack se precarga en un `init` con `viewModelScope.launch { textoCarjack = getString(...) }`. La propiedad `textoCarjack` DEBE declararse antes de ese `init`: Kotlin ejecuta inicializadores e `init` en orden de declaración, y si la carga termina sin suspender, un `= ""` declarado después borraba el texto y el aviso salía como un rectángulo rojo vacío (DEV-05). `WorldMapScreen` además no pinta el aviso si el texto está en blanco.

**Deslizar por el borde de la calle (2026-11-16, DEV-09):** el coche solo circula a ≤ `maxRoadRadius` (0.00004° ≈ 4.4 m) de una vía `isForCars` (`WorldMapViewModel`, aduana de calle). Antes, al rozar el borde frenaba ×0.6 por tick y se jalaba 30 % hacia la calzada: con el gas pulsado quedaba clavado (el HUD marcaba 15–21 km/h sin avanzar) o se arrastraba de lado. Ahora, si el punto previo estaba dentro (≤ `ROAD_SLIDE_ENTRY_FACTOR`×radio), `slideAlongCorridor` (`shared/.../RoadCorridorSlide.kt`) lo deja en el borde en la misma dirección y el coche avanza a lo largo de la calle; solo pierde velocidad de frente (`ROAD_SLIDE_BASE_KEEP` = 0.85 + 0.15×proporción avanzada). Si venía de fuera (p. ej. subió en un estacionamiento) se conserva el jalón de reincorporación. Medido en el Motorola (traza `--es trace <s>` de los escenarios): velocidad contra el borde 4.4e-7 → 1.6–6e-6 (deg/tick). Pruebas: `RoadCorridorSlideTest`.

**Salir de un edificio aunque la calle quede detrás (2026-11-21):** la aduana de calle se aplica ANTES del barrido estático, y fuera del corredor jala el destino hasta ~4.4 m por tick hacia la vía más cercana. Si el coche ya estaba encimado en un edificio (centro dentro, aparición o teletransporte) y esa vía quedaba detrás del edificio, cada tick apuntaba hacia dentro, `sweepVehiclePath` lo bloqueaba y la velocidad volvía a 0: ni avanzaba ni daba reversa (medido con el punto viejo de DEV-08, 39 m de la vía: 0 m en 5 s con gas y en 3 s de reversa). Ahora `resolveVehicleDrivingStep` (`WorldMapMovement.kt`) reintenta con el paso puro del rumbo cuando el destino de la calle choca y la carrocería arrancó encimada. El barrido solo acepta pasos que aumentan la distancia con signo al obstáculo, así que el reintento nunca deja entrar más: solo salir hacia donde apunta el coche (o en reversa). Ya fuera rige la aduana normal; si la vía sigue detrás del edificio el coche se queda en el borde y hay que bajarse (a pie sí se sale). Pruebas: `VehicleDrivingStepTest`.

## Prototipo: mapa cenital estilizado (estilo Chinatown Wars) — 2026-11-16

- **Datos:** `assets/CONFIG/escom_osm_footprints.json` (44 formas de OpenStreetMap, ODbL: 30 edificios, canchas, estacionamientos, parques). Modelo y parser en `domain/models/map/CampusFootprints.kt` (`:shared`).
- **Render:** `ui/components/CampusFootprintLayer.kt`, una capa Compose SOBRE el mapa (techo, paredes sur, sombra y contorno) con la misma proyección Mercator que `InteriorDebugDrawSurface`. Activa con el escenario debug `--es name footprints [--es lat .. --es lon .. --es zoom ..]` (`on=0` la apaga); no hay botón de usuario todavía.
- **Medición (Motorola One Fusion, conduciendo, `gfxinfo`):** sin capa mediana 17-18 ms; con capa completa 29-30 ms (p90 40 ms). Grabar la geometría en una `GraphicsLayer` NO mejoró nada: el costo es de GPU, no de construir paths. Atribución: contornos ≈ 5 ms, sombra ≈ 3 ms, techos ≈ 4 ms, paredes ≈ 1 ms.
- **Estilo final del prototipo:** sin contornos ni sombra (paredes en tono más oscuro), SIN suelo (estacionamiento/canchas/parques no se pintan: opacos tapaban toda la pantalla), capas semitransparentes (alpha 0.8) y **una `GraphicsLayer` por edificio**; la del edificio que contiene al jugador se funde a transparente (250 ms) para ver lo que hay dentro. Se enciende desde Opciones → "Mapa estilizado" (o `--es name footprints`). Costo medido en el Motorola conduciendo: mediana 14-25 ms con capa vs 16-17 sin ella (≈ +3-4 ms, medición ruidosa). Los extras `--es fpstyle <bits>` (1 contornos, 2 sombra, 4 sin paredes, 8 suelo) son solo para medir.
- ⚠️ **La altura visual se limita a 1.5 m** (`MAX_VISUAL_RISE_METERS`): la colisión usa la huella en el suelo, y con los ~10 m reales el techo dibujado se levantaba hacia arriba y el jugador, que frena en el borde de la huella, se veía ENCIMA del techo y cruzando sus paredes. Además las huellas son de OSM y pueden no coincidir al metro con los edificios del mapa satelital: la colisión sigue a OSM, no a la imagen.
- ⚠️ **Nunca grabar cada capa con el tamaño de todo el campus** (~15000×10000 px): con alpha Android pidió un búfer fuera de pantalla de ese tamaño y el Motorola One Fusion (3.8 GB de RAM) llegó a reiniciarse. Cada capa mide lo que su edificio y usa `CompositingStrategy.ModulateAlpha`.
- Aún no sustituye al mapa satelital; solo lo tapa (con velo) donde hay huellas.

## Colisiones de edificios desde las huellas de OSM — 2026-11-16

- **Qué bloquea:** `cargarColisionesConHuellas()` (`CampusFootprints.kt`) = `exterior_collisions.json` + un polígono `BARRIER` por cada `building` de `escom_osm_footprints.json` (nombre `OSM_<id>_<nombre>`; no entran techos sueltos, canchas, estacionamientos ni parques). Lo usa `WorldMapViewModel.loadExteriorCollisions()`; el polígono a mano `EDIFICIO_ESCOM_TECHO_CENTRAL` se conserva. **iOS sigue con `cargarColisionesExteriores()`** (su `chocaAlMoverse` no sabe salir de un solape): no portar sin probar en simulador.
- **Salida a pie (`resolveCirclePath`):** si el actor ya está dentro de un edificio (spawn, salida de un interior: los marcadores PALAPAS, BIBLIOTECA, CANCHAS y AUDITORIO caen sobre techos), camina libre hasta quedar fuera; si solo está pegado a un borde, cada paso debe alejarse del obstáculo más cercano. Una vez fuera, no puede volver a entrar. Antes un actor dentro de un polígono quedaba clavado para siempre.
- **Rendimiento:** `circleObstacleAt` ahora descarta por caja con `BarrierIndex` (antes recorría todos los polígonos y reasignaba listas en cada muestra). En el Motorola, conduciendo, la mediana sigue en 15-18 ms.
- **Ojo:** el overlay "Debug Interiores" y su export incluyen estos polígonos (viven en `uiState.exteriorCollisions`); no pegar ese export de vuelta sobre `exterior_collisions.json` sin quitar los `OSM_*`.
- **Pruebas:** `CampusCollisionsTest` (shared) y `CampusFootprintAssetTest` (assets reales: ninguna entrada de interior queda sin salida a pie).

## Lag al caminar: fuga de peatones del campus — 2026-11-16

- **Síntoma:** a pie en la plaza el juego iba cada vez más lento (36 ms de mediana y p90 85 ms tras unos minutos; a zoom 19 ~4 fps) y el proceso de render del WebView llegaba a ~1 GB de RAM, hasta dejar de responder a DevTools. Con el juego recién abierto la misma caminata iba a 19 ms.
- **Causa:** `NpcAiManager` excluía a los `CAMPUS_PED_*` de TODOS los cupos (`activeCount`, `totalCount`, `nonParkedAlive`, recorte de exceso), así que `nonParkedAlive() < maxTotalNpcs` era siempre verdadero y cada escaneo de spawn (900 ms) añadía 1-3 estudiantes; solo se retiraban a > `despawnDistance` (~310 m), que en el campus no ocurre. Medido por DevTools: `npcMarkers` 57 → 114 → 193 en ~6 min con el jugador parado, uno por NPC en el DOM de Leaflet (más DOM, más capas, más `updateNpcs`).
- **Arreglo:** tope propio `maxCampusPeds = campusPedestrianCap(popFactor)` (36 × densidad, entre 8 y 100) y recorte de los más lejanos si se pasa (`CampusPedestrianCap.kt`, usado en `NpcAiManager.updateNpcs`). Probado en `CampusPedestrianCapTest`.
- **Resultado (Motorola, plaza, joystick mantenido):** zoom 22: 16-17 ms de mediana (p90 24-28); zoom 20: 22 ms; zoom 19: 24 ms (antes 200-250 ms). Estable tras 8+ min; el render del WebView queda en ~200-250 MB. NPC enviados al WebView: ~45 (antes 158 → 193 y subiendo).
- **Cómo medir (método reutilizable):** `adb forward tcp:9222 localabstract:webview_devtools_remote_<pid>` y envolver con contadores las funciones JS (`updateNpcs`, `updateCollectibles`, `updateMapView`, `map.panBy`…); `Object.keys(npcMarkers).length` y `__lastNpcData` para comparar lo enviado con lo pintado; `dumpsys gfxinfo` con el joystick mantenido (`input motionevent DOWN/MOVE/UP`). Medir SIEMPRE con la misma antigüedad de sesión: el lag crecía con el tiempo.
- **Pendiente (menor, ya sin urgencia):** `updateNpcs` y `updateCollectibles` se llaman a ~30 Hz aunque nada cambie (≈ 150 ms/s y ≈ 35 ms/s de JS en el WebView); guardarlos por cambio o limitar a ~15 Hz daría margen extra.

## Escala del mundo: personas y coches en metros reales — 2026-11-16

- **Antes (medido en el Motorola, 280 dpi, donde 1 dp = 1 px CSS del WebView):** jugador a pie ≈ 0.8 m (fijo en pantalla, 23.5 dp, no seguía el zoom), peatones NPC ≈ 1.0 m de cuerpo (caja 1.3 m × 0.74 de cuerpo opaco) y sedán ≈ 3.4 m visibles (caja 4.0 m × 0.84). Las personas medían la mitad de lo real frente a coches y edificios.
- **Ahora (una sola fuente, `domain/models/map/WorldScale.kt`):** cuerpo de persona `PERSON_BODY_METERS = 1.7` (caja del peatón `PERSON_BOX_METERS` = 1.7 / 0.74 ≈ 2.3 m) y caja de coche `CAR_BOX_METERS = 5.0` (sedán ≈ 4.2 m visibles). Lo usan el WebView (`WorldMapLeafletHtml.kt`, interpolado en el JS), el jugador (`PlayerCharacter.kt`, cuerpo = 1.7 m / m-por-dp del zoom, con tope 14-64 dp) y el mapa nativo (`NativeOsmMap`, `NativeOsmMapPrankedy`). `metersPerDp`/`dpForMeters` convierten metros a dp.
- **Colisión de coches:** `CarModel.collisionDimensions` escalada ×1.25 (sedán 4.5×2.0 m …) para seguir al sprite. Colisión a pie sin cambios (radio 0.35 m).
- **Velocidad a pie:** sin cambios (7.8 m/s medidos); con personas el doble de grandes cubre menos cuerpos por segundo.
- **Efecto del zoom:** a zoom 22 el jugador mide ~48 dp (≈ 84 px en el Motorola); a zoom 21 ~24 dp, que coincide con el tamaño fijo anterior; a zoom ≤ 20 llega al tope mínimo de 14 dp. Si se quiere que a pie se vea más mundo, evaluar `ZOOM_ON_FOOT = 21`.
- **Pendiente:** ZombiHand/otros sprites propios (gatos, policías) conservan su tamaño; el mapa nativo de Google (`WorldMapScreenGoogle`) no usa estas constantes todavía; el aparcamiento de coches más grandes en cajones estrechos no se midió.

## NPC y edificios: colisión de NPC y personajes más grandes — 2026-11-16

- **Síntoma:** los NPC atravesaban los edificios. Medido por DevTools (muestreo de `lat/lng` de cada NPC del WebView contra las huellas OSM, jugador parado en la plaza): **46 % de las observaciones de NPC caían dentro de un edificio** (peatones del campus, estudiantes `CAMPUS_0..9`, gatos, conversaciones dinámicas).
- **Causas:** (1) `moveLocalNpc` (peatones del campus, gatos y coches que salen del estacionamiento) nunca llamaba al resolvedor estático; (2) el navGraph peatonal se dibujó sin conocer los edificios de OSM: 14 de 158 nodos y ~126 m de ~1270 m de vía caen dentro (las vías de coches y los 81 cajones están limpios, ≥ 13 m); (3) los 10 estudiantes `CAMPUS_*` (`WorldMapCampusLife.kt`) nacían y caminaban en línea recta por todo el recuadro del campus; (4) aggro, zombis, cazadores y esquiva iban en línea recta sin colisión; (5) `moveNpc` ponía `speed = 0.0` tras un bloqueo y el NPC quedaba congelado para siempre; (6) hueco en la regla `escaping` de `resolveCirclePath` (un actor que solo rozaba el borde podía entrar al edificio).
- **Arreglos:** `NpcStaticCollision.kt` (`localWayBlocked`, `walkableLocalWays`, `localNodeFree`, `collided`): las vías que tocan un edificio no se usan ni para aparecer ni para elegir ruta; `moveLocalNpc` resuelve cada paso y, al topar, conserva el último punto seguro y da la vuelta; `moveNpc` ya no congela; aggro, zombis, cazadores y esquiva pasan por `collided`; `CAMPUS_*` usan puntos libres de edificios (`campusFreePoint`) y un paso con colisión que rodea (`campusStep`); `startedInside` cierra el hueco de `escaping`; `exteriorCollisions` del manager es `@Volatile`.
- **Resultado (Motorola, mismo protocolo):** **0 de 359 observaciones dentro de edificios** (antes 46 %; tras solo el arreglo compartido, 0.7 % por actores de eventos). Rendimiento caminando sin cambio (ver abajo).
- **Pendiente (escenas cortas de guion, sin colisión):** `CampaignEscortPolice`, `PrankedyManager`, misión 3, persecución de la misión 1, eventos dinámicos `DYN_*` y el empuje de zombis (`WorldMapCombat.kt`). Quedaron 4 observaciones sueltas de `DYN_*` en la medición intermedia.
- **Personajes más grandes (decisión de diseño):** `CHARACTER_VISUAL_SCALE = 1.3` en `WorldScale.kt`: jugador y peatones se dibujan con un cuerpo de ≈ 2.2 m (`PERSON_VISUAL_BODY_METERS`); los coches no cambian (la relación coche/persona baja de 2.5 a 1.9). El zoom máximo sigue en 22 (Leaflet `maxZoom: 22`, pinch y OSM nativo lo limitan). `PLAYER_BODY_MAX_DP` 64 → 84; radios de colisión a pie 0.35 → 0.45 m (jugador) y 0.3 → 0.4 m (NPC) para seguir al sprite.
- **Cómo medir "NPC dentro de edificios":** `adb forward` al WebView, envolver `updateNpcs` para guardar `__lastNpcData` y contar con un punto-en-polígono contra `escom_osm_footprints.json` (script usado: muestreo cada 4 s durante ~2 min con el jugador parado en la plaza).

## Colisión del edificio ESCOM desde la IMAGEN del campus — 2026-11-16

- **Problema:** las huellas de OSM no coincidían con el campus que se ve: el campus es una imagen propia (`BUILDINGS/IPN/building_escom.webp`, 1857×2275, colocada y rotada 25.8° sobre el mapa) y OSM dejaba sin colisión el bloque central con paneles solares y desplazaba las alas. El jugador y los NPC caminaban "sobre el techo".
- **Solución:** los techos se extraen de la imagen por color (techo naranja + paneles azules que tocan techo; celdas de 4 px ≈ 0.45 m; cierre/apertura morfológica de radio ≈ 0.9 m; se rellenan huecos < 80 m² y los patios grandes se parten en franjas verticales para que cada pieza sea un polígono simple), se traza el CONTORNO de cada pieza y se simplifica (Douglas-Peucker, ≈ 0.7 m) a **polígonos de n lados** (31, 11 y 52 vértices, ≈ 10 500 m²; antes 43 rectángulos). Se guardan en `escom_osm_footprints.json` con `tags.source = "art"` (`ESCOM techo N`, ids 9000000+). Los edificios de OSM cuyo centroide cae dentro de la imagen se descartan; el resto de OSM (CIC, Plaza Torres…) se conserva. Se descartan componentes < 1000 m² (canchas, bancas).
- **Regenerar:** `tools/escom_art_footprints/` (`extract_polys.js` se evalúa por DevTools con el juego abierto → `polys.json`; `python art_to_footprints.py [apply]`). Guarda además las huellas OSM originales.
- **Validación:** 0 de 158 nodos peatonales y 0 de 186 de coche del navGraph caen dentro de los techos (con OSM caían 14 peatonales); el spawn de la plaza queda libre; NPC dentro de edificios: 0.3 % (solo actores de eventos dinámicos). Pruebas: `CampusFootprintAssetTest` (navGraph fuera de los techos, spawn libre, un techo bloquea).
- **⚠️ Trampa de la rotación:** la imagen se DIBUJA con la rotación contraria a `Landmark.toGlobalGeoPoint` (se comprobó comparando las 4 esquinas reales del `<img>` en el DOM con `latLngToContainerPoint`: coinciden con `-rotationAngle`), pero las vías del navGraph se dibujaron con el diseñador sobre el mapa, es decir en el sistema de `toGlobalGeoPoint` (+rot). Por eso las coordenadas de IMAGEN (techos) se convierten con `-rot` y los nodos del navGraph con `+rot`. Quien convierta píxeles de la imagen con `toGlobalGeoPoint` obtendrá formas giradas al revés (los contornos salen inclinados al lado contrario de los edificios).
- **⚠️ Cierre morfológico:** con radio 3 (≈ 5 m) se rellenaban los pasillos entre alas y el spawn de la plaza quedaba dentro de un techo; con radio 1 no.
- **Se eliminó** el polígono a mano `EDIFICIO_ESCOM_TECHO_CENTRAL` de `exterior_collisions.json`: tapaba un pasillo por donde pasan rutas de NPC; lo sustituyen los techos de la imagen.
- **Pendiente:** los marcadores de entrada a interiores (AUDITORIO, BIBLIOTECA, CANCHAS, PALAPAS, CAFETERÍA) caen dentro de techos; se sale caminando (regla `escaping`) y se alcanzan desde el borde. Otras imágenes de campus (CIC, Plaza Torres) siguen con OSM; el mismo método aplica.

## iOS: colisión de edificios y NPC — 2026-11-16

- **Estado antes:** `MapaMundoIos` cargaba `cargarColisionesExteriores()` y movía al jugador con `chocaAlMoverse()` (solo miraba el DESTINO). Al retirar el polígono a mano del techo central, esa carga ya no traía ningún edificio (solo carriles de guía): **en iOS no había colisión de edificios**. Además el `NpcAiManager` de iOS nunca recibía las colisiones (`setExteriorCollisions`), así que los NPC atravesaban todo.
- **Ahora:** `cargarColisionesConHuellas()` (bardas + techos de la imagen + huellas de OSM), `cerebro.setExteriorCollisions(colisiones)`, y el jugador avanza con `avanzarConColision()` (`commonMain`, `ColisionesExteriores.kt`): mismo `resolveCirclePath` que Android, hasta el último punto libre; quien aparece dentro de un edificio camina libre hasta salir. El radio del cuerpo sale de `PLAYER_FOOT_COLLIDER_METERS` (`WorldScale.kt`), que también usa Android.
- **Bundle:** el script de Xcode ya copia TODA la carpeta `assets/CONFIG` (`rsync`), así que `escom_osm_footprints.json` llega a iOS sin tocar el proyecto.
- **Pruebas:** `AvanzarConColisionTest` (commonTest, corre también en `:shared:testAndroidHostTest`). iOS compila (`compileKotlinIosSimulatorArm64`); **NO se probó en simulador ni en un iPhone** (no hay Mac aquí): conviene comprobar a mano que el jugador frena en los techos y que los NPC no los cruzan.
