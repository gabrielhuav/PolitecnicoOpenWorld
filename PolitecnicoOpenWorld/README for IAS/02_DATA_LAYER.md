# 02 · Capa de datos / Data layer (`data/`)

**ES:** Todas las preocupaciones transversales (Room, red, preferencias) viven aquí y se inyectan
en los ViewModels vía `Factory`. Las Views nunca acceden a esto directamente.
**EN:** All cross-cutting concerns (Room, network, prefs) live here and are injected into
ViewModels via `Factory`. Views never touch this directly.

---

## Room — `data/local/room/`

### `PowDatabase.kt` — `@Database` v8

- 6 entidades / entities: `RoadZoneEntity`, `RoadWayEntity`, `RoadNodeEntity`, `MapTileEntity`,
  `LandmarkEntity`, `CollectibleEntity`.
- `MIGRATION_7_8` + `fallbackToDestructiveMigration`.
- Singleton: `PowDatabase.getInstance(context)`. DB file: `filesDir/databases/pow_roads.db`.
- DAOs: `roadNetworkDao()`, `mapTileDao()`, `landmarkDao()`, `collectibleDao()`.

### Entidades / Entities (`entity/`)

```kotlin
@Entity("road_zones")  RoadZoneEntity(cellKey, downloadedAtMs, wayCount, ...)
@Entity("road_ways")   RoadWayEntity(wayId, cellKey, isForCars, isForPeople)        // FK→zone
@Entity("road_nodes")  RoadNodeEntity(wayId, cellKey, nodeId, position, latInt, lonInt) // lat/lon ×1_000_000
@Entity("map_tiles")   MapTileEntity(provider, urlKey, data: ByteArray, createdAtMs)
@Entity("landmarks")   LandmarkEntity(id, name, latitude, longitude, assetPath,
                                      scaleFactor, rotationAngle/*0-360*/, scaleX, scaleY)
@Entity("collectibles") CollectibleEntity(id, name, description, assetPath, isCollected)
```

### DAOs

**`RoadNetworkDao`** (transacciones atómicas / atomic transactions):
- `getZone(cellKey)`, `getWaysForZone(cellKey)`, `getNodesForZone(cellKey)`
- `insertZoneWithData(zone, ways, nodes)` — **`@Transaction`** (zona+ways+nodos en una sola)
- `deleteZonesBefore(beforeMs)`, `getZoneCount()`, `getTotalWayCount()`

**`MapTileDao`**:
- `getTileData(provider, urlKey): ByteArray?`, `insertTile(tile)`, `getCount(provider): Long`
- `deleteOldestTiles(provider, limit)` — evict LRU por `createdAtMs ASC`
- `putTileAtomic(tile, maxTilesPerProvider, evictBatch)` — **`@Transaction`**: cuenta → evict si
  excede → inserta (evita corrupción si el proceso muere a media escritura). / count → evict → insert atomically.

**`LandmarkDao`**: insert/update/delete + `getAll`, `getById`, `getInBounds(minLat,maxLat,minLon,maxLon)`, count.

**`CollectibleDao`**: `getAllCollectiblesFlow(): Flow<List<...>>`, `getUncollected`, `markCollected(id)`, insert, count.

---

## Cachés / Caches — `data/cache/`

### `RoadNetworkCache.kt`
- LRU de zonas de calles OSM. Celdas de ~2 km (`CELL_SIZE_DEG`), **TTL 7 días**, **LRU de 20 celdas**.
- `get(lat, lon): List<MapWay>?` (null si caduca/ausente), `put(lat, lon, ways)`, stats.
- `cellKey(lat, lon)` = `floor(lat/CELL)`,`floor(lon/CELL)`. Reconstituye `MapWay`/`MapNode` desde entidades.

### `TileCache.kt`
- Envuelve `MapTileDao`. Por proveedor, **~8k tiles máx**. `getTileByUrl(provider, urlKey)`,
  `putTileByUrl(provider, urlKey, data)` (usa `putTileAtomic`), `getStats(provider)`.

### `RoomTileModuleProvider.kt` (osmdroid → Room)
**ES:** Módulo de osmdroid que hace que el **mapa nativo OSM lea/escriba la MISMA caché Room** que
los proveedores Web (bucket `"osm"`). Lee Room primero; si falta, **descarga con User-Agent de
navegador** y persiste. (El descargador interno de osmdroid usa UA = packageName y el servidor
público de OSM lo estrangulaba → el nativo "no cargaba zonas nuevas".)
**EN:** osmdroid module so the **native OSM map reads/writes the SAME Room cache** as the Web
providers (bucket `"osm"`). Reads Room first; else **downloads with a browser User-Agent** and
persists. (osmdroid's built-in downloader uses UA = packageName and was throttled by public OSM.)
- URL canónica / canonical URL: `https://tile.openstreetmap.org/$z/$x/$y.png`, key = SHA-256.

### `TilePrefetchManager.kt`
- Pre-descarga la zona actual (~2 km, **zooms 16–18**) a Room para juego **offline**. No bloqueante.
- `isRunning()`, descarga en grid alrededor de (lat,lon,radiusMeters); reporta progreso 0..1 y si quedó incompleta.
- `lonLatToTile(lon, lat, z)` (slippy map), `download(url)`. Disparado por `WorldMapRoadNetwork.prefetchCurrentZoneTiles`.

---

## Red / Network — `data/network/`

### `WebSocketManager.kt`
- OkHttp WebSocket. **Compartido por ambos servidores** (open world y zombi), instanciado con la URL.
- `connect()`, `disconnect()`, `isConnected()`, `sendMessage(message: String)`.
- `messagesFlow: SharedFlow<String>` — emite cada mensaje crudo entrante. Sin timeouts; ping 25 s.

---

## Repositorios / Repositories — `data/repository/`

| Repo | Rol / Role |
|---|---|
| `OverpassRepository` | Llama a la **Overpass API** (radio 2 km, timeout 45 s). `fetchRoadNetwork(lat, lon): List<MapWay>`. Regex `CAR_REGEX`/`PEOPLE_REGEX` clasifica `highway`. Parsea JSON a `MapNode`/`MapWay`. |
| `SettingsRepository` | **SharedPreferences** (no Room). Controles (`saveControlsSettings(type, scale, swap)`, getters), skin del jugador (`PlayerSkin`), `showRoadNetwork`. `SCALE_MIN..SCALE_MAX` clamp. |
| `CollectibleRepository` | Siembra 6 coleccionables por defecto si la tabla está vacía. `allCollectiblesFlow: Flow<List<CollectibleEntity>>`. |
| `MetroRepository` | `object`. `loadStations(context): List<MetroStation>` desde `res/raw/metro` (GeoJSON: name, routes, coords). |
| `WaypointRepository` | `object`. Persiste puertas de salas zombi (`ZoneDoor`) en `waypoints.json`. `DoorDef`↔`ZoneDoor`, `Store(version, rooms: Map<roomId, List<DoorDef>>)`, `load/save/exportJson/importJson/clear`. |
| `CollisionMatrixRepository` | Matrices de colisión zombi en `collision_matrices.json` (mismo formato que lee el servidor zombi — ver 05/08). |

> **ES:** Nota: existe también `features/zombie_minigame/ui/components/Collisionmatrixrepository.kt`
> (duplicado por refactor); el canónico es `data/repository/CollisionMatrixRepository.kt`.
> **EN:** Note: there is also a duplicate under `zombie_minigame/ui/components/`; the canonical one
> is `data/repository/CollisionMatrixRepository.kt`.

---

## Gotchas de datos / Data gotchas

- **Escritura de tiles SIEMPRE atómica** (`putTileAtomic`): no insertar/evict por separado. / Tile writes always atomic.
- **Coordenadas de nodos se guardan como Long ×1_000_000** (`latInt`, `lonInt`) — convertir al leer. / Node coords stored as Long ×1e6.
- El proveedor de mapa **no se persiste**; el default `OSM_WEB` es solo el estado inicial (ver 04/09). / Map provider not persisted.
- Room es **v8**; cualquier cambio de esquema → nueva migración + bump de versión + actualizar docs. / Schema change → new migration + version bump + update docs.
