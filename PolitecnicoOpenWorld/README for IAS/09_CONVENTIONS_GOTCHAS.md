# 09 · Convenciones, Gotchas y Protocolo de docs / Conventions, Gotchas & Doc protocol

**ES:** Lee esto **antes de editar**. Romper una de estas reglas suele introducir regresiones (sobre
todo de rendimiento en gama baja) o desincronizar la documentación.
**EN:** Read this **before editing**. Breaking one of these usually causes regressions (especially
low-end performance) or doc drift.

---

## 1. Convenciones MVVM / MVVM conventions

- Estado **siempre** como copia inmutable: `_state.update { it.copy(...) }`. Nunca mutar estado Compose
  directamente. / Always immutable copies; never mutate Compose state directly.
- Views: solo `collectAsState()` + emitir intenciones. **Nunca** tocan repos/DAOs. / Views never touch repos/DAOs.
- DI manual con `Factory` co-localizada. ViewModels top-level = Activity-scoped; interior/zombi/metro/
  shinecto = NavBackStackEntry-scoped (ver 01). / Manual DI; scoping per file 01.
- **Comentarios y strings en español** (incluidos los dos `server.js`). Mantener ese estilo salvo que se
  pida lo contrario. / Comments/strings in Spanish; keep that style.

## 2. Controles "staged" / Staged controls

Cambios de control (tipo/escala/swap) viven en campos `temp*` y **solo** se aplican en
`saveControlsSettings()`; `discardControlsChanges()` al salir. **No** cablear ediciones directo al estado
comprometido. / Control edits stay in `temp*`, commit only on SAVE.

## 3. Snap-to-road y cachés / Snap-to-road & caches

- El jugador **no puede salir de las calles**: todo movimiento se valida contra el grid espacial (`Seg`
  + `HashMap<celda, segmentos>`), O(cercanos). / Player can't leave roads.
- **Caché de calles:** celdas ~2 km, TTL 7 días, LRU 20, inserción **atómica** (`@Transaction`), cooldown
  re-fetch 5 min.
- **Caché de tiles:** por proveedor, ~8k máx, key = URL normalizada SHA-256, **escritura atómica**
  (count→evict→insert en un `@Transaction`). El **OSM nativo comparte esta misma caché** vía
  `RoomTileModuleProvider` (UA de navegador). / Tile writes atomic; native OSM shares the same cache.
- **Over-zoom nativo z20–22:** `MapTileApproximater` + `setMaxZoomLevel(22)` escala z19 (OSM solo da
  tiles reales hasta z19). Las pantallas de carga prefetch z19 + z17 a Room.

## 4. Fog de guerra anclado al jugador / Player-anchored fog (por renderer)

- **OSM nativo:** `FogOverlay` proyectado al jugador cada frame; rect = pantalla a pie, diagonal solo al
  conducir (rotación). No rellenar ~10× el área cada frame.
- **Web:** div `#fog` dentro de `#map-wrapper` (rota con el mapa); `drawFog` **cachea el gradiente** y no
  re-rasteriza si no cambia.
- **Compose `Canvas` fog:** **solo** para Google nativo.

## 5. Tamaño en metros reales unificado / Unified real-meter sizing

Peatones ≈ 1.3 m, vehículos ≈ 4.0 m (un poco más grandes que real, por visibilidad). Tres fuentes a
mantener en sync al re-tunear: `NativeOsmMap` (nativo), `WorldMapLeafletHtml.updateNpcs` (web,
px-por-metro), `PlayerCharacter` (jugador a pie/conduciendo). El sprite nativo usa `uiState.zoomLevel`
(NO `view.zoomLevelDouble`) para coincidir con el jugador. / Three sizing sources to keep in sync.

## 6. Rendimiento gama baja (≤2 GB / Android 7–9) — NO regresar / low-end perf — do NOT regress

- **`nativeDrawableCache`** (en `WorldMapScreen`, usado por `NativeOsmMap`) es **LRU por orden de acceso**
  (`LinkedHashMap` + `removeEldestEntry`, cap ~384). Nunca volver a `mutableMapOf`: sus claves embeben
  health/zoom/frame → crece hasta **OOM**.
- Sprite managers (`Character`, `Vehicle`, `Police`, `Zombie`) exponen `clearCaches()`, liberados por
  **`MainActivity.onTrimMemory`** bajo presión de memoria.
- `buildDoorEffectBitmap` reusa **un** `Bitmap`/`Canvas`/`Paint` por fuente (no asigna por frame). Iconos
  de bala/🔫/📞 de policía **cacheados por tamaño** en `nativeDrawableCache`.
- `NpcAiManager.setLandmarks` precomputa `cachedNavLandmarks` (no `.filter { navGraph!=null }` por NPC/tick).
  El spawner usa **bbox pre-filter** O(1) (`cachedWayBoxes`) antes del check por nodo.
- **Lambda `update` de osmdroid corre ~30 Hz → mantenerla barata:** landmarks estáticos solo
  re-`setPosition`/`setImage` cuando cambia su firma (`landmarkSigCache`); puertas `DOORS/` sí cada frame;
  ~160 marcadores de metro culleados por viewport (`Marker.isEnabled`).

## 7. Mapa web `#map-wrapper` / web map wrapper

**Dinámico:** `100vw×100vh` a pie; al conducir crece a un cuadrado del tamaño de la **diagonal en
PÍXELES** (NO `vmax`/`calc` — daban **esquinas negras** en WebViews viejas). Cambia vía
`setMapOversize(driving)` (llama `map.invalidateSize`), invocado cada frame (JS no-opea repetidos vía
`_driving`). **No** dejarlo permanentemente sobredimensionado: a `300vw×300vh` renderizaba ~9× los tiles
(el costo dominante de FPS al moverse). Follow-camera con `map.panBy` (no `setView`).

## 8. Render web NPC types / web renderer NPC types

`updateNpcs` (en `WorldMapLeafletHtml`) solo dibuja imágenes para `type` `"CAR"`/`"MODULAR"`; el resto cae
a SVG. Por eso **`POLICE_CAR` debe ir como base64 car-image (`type="CAR"`)** vía `PoliceSpriteManager`, y
`POLICE_COP` como **👮 base64 (`type="MODULAR"`)**. Patrullas fuera de la fog se dibujan con
`updatePolice(playerLat, playerLng, data)` (🚓 + línea), empujado solo si `wantedLevel>0` (y una vez más
para limpiar). El submenú "Ir a…" se **eliminó**: ESCOM es la 1ª `TeleportCatalog.zones` y el teleport
GPS es el 1er item del diálogo *Puntos de Teletransporte*. El submenú **"Zoom (acercar/alejar)" del menú
Mapa también se eliminó**: el zoom es solo por pinch (los `zoomIn/zoomOut` del VM siguen existiendo pero
sin UI). El **Modo Diseñador en web** tiene paridad con los nativos: lápices ✏️ por landmark
(`refreshDesignerControls` en el HTML; `notifyLandmarkSelected/Moved` en `MapJsBridge` →
`selectLandmark`/`moveLandmarkTo`); el seleccionado se tinta vía `setSelectedLandmark`.

## 9. Default provider = `CARTO_VOYAGER` (no persistido)

El nativo osmdroid (default z22 + reescalado z19) es el render más pesado en equipos débiles, así que el
default arranca en **CARTO Voyager (web)**, que sirve tiles reales hasta **z20** (más nitidez de calles
que OSM Web, que topa en z19). `changeTileUrl(url, maxNative)` recrea la capa Leaflet si cambia el
`maxNativeZoom` por proveedor (CARTO=20, OSM/ESRI=19, OPEN_TOPO=17, Google=20) y **no-opea** si la URL no
cambió (antes redibujaba cada frame). **No se persiste**: el default es solo el estado inicial en
`SettingsState`, `WorldMapState` y `MainMenuState` (todos `CARTO_VOYAGER`). **Google nativo** sigue al
jugador con `cameraPositionState.move()` (NO `animate()`). **Web** re-envía landmarks solo cuando cambia
la lista (guard por referencia + heartbeat ~45 frames).

## 10. Policía / NPCs (autoridad)

- **NPCs civiles:** IA en el cliente (Zone Host). El servidor open world **no** simula; relaya. El
  servidor v3 conserva el roster (huérfanos adoptables) — ver 08.
- **Policía:** la simula y posee **el jugador buscado** (no el Host), porque debe perseguirlo a él. Vive
  en `PoliceManager`, **no** en `remoteEntities`; se fusiona aparte en `uiState.npcs`. Se relaya
  (`POLICE_BATCH_UPDATE`/`POLICE_DESTROY`) sin entrar al roster.
- **Sin melee a través del coche:** si `isDriving`, se omite daño de contacto/contraataque de NPC; en su
  lugar puede dispararse un carjack.
- **NPCs provoke-only:** nunca atacan sin provocación. AGGRESSIVE contraatacan (`aggroUntil` + contra-golpe
  determinista ~450 ms); COWARD huyen (`fearUntil`); AGGRESSIVE son inmunes al miedo. 3+ golpes seguidos
  (`npcHitStreak` ≥ `RELENTLESS_HIT_STREAK`=6) → NPC implacable.

## 11. Colisiones zombi cliente⇄servidor / collision matrices client⇄server

El cliente (`ZombieRoomCatalog`) y el servidor zombi **deben coincidir** en filas/cols. El Modo Diseñador
exporta `collision_matrices.json` en el formato exacto que lee el servidor (`loadMatrixOverrides`). Las
matrices por defecto son **border-only** hasta reemplazarse.

## 12. Otros / Misc

- Room **v8** con `MIGRATION_7_8` + destructive fallback. Cambio de esquema → nueva migración + bump.
- Prefs vía `SettingsRepository` (SharedPreferences), no Room.
- Menú de opciones anidado (`OptionsMenu`): grupos *o* items a cualquier profundidad; height-capped
  (~68%) + scrollable (landscape-safe); el control de la derecha se desliza al abrirlo.
- Con el mapa descentrado (`isUserPanningMap`), los controles de movimiento **recentran** en el jugador en
  el 1er toque (sin cambiar zoom).
- **Errores en cascada "Unresolved reference"** tras un merge → sospecha de **un** archivo con llaves/
  paréntesis desbalanceados, no de muchos símbolos faltantes (ver 01).
- **Refactor de tamaño (parciales NUEVOS):** `WorldMapViewModel.kt` bajó de ~3400 a ~2600 líneas
  extrayendo bloques SIN gemelo de extensión a `WorldMapProviders.kt` (proveedores/tiles/compuertas),
  `WorldMapDesigner.kt` (landmarks/diseñador) y `WorldMapWanted.kt` (wanted/policía/carjack). El
  ESTADO sigue en el VM (`providerPreloadJob`, `mapPrepStarted`, `escomNavGraph`, `carjackStartTime`…);
  los parciales solo tienen lógica. Los call-sites FUERA del paquete `viewmodel` (UI, MainActivity)
  necesitan **import explícito** de cada extensión. Además se ELIMINARON los duplicados miembro de
  `updateNpcsState` (gemelo idéntico en `WorldMapMultiplayer.kt`) y de `ensureIndex`/`candidates`/
  `getNearestPointOnNetwork`/`project` (el gemelo de `WorldMapRouting.kt` se sincronizó ANTES con el
  check de landmarks que solo tenía el miembro) — esas extensiones son ahora la única implementación.
  Pendiente (con compilador a la mano): sincronizar y de-duplicar los pares grandes
  (`startGameLoop`, `handleMultiplayerMessage`/`addRemoteEntity`, `updateVisibleRoads`,
  `applyRoadNetwork`, `maybeRefetchRoadNetwork`, `spawnOustedDriver`, `triggerWastedSequence`,
  `startHealthBarTimer`), donde el MIEMBRO sigue siendo el canónico.
- **Gotcha de parciales (¡importante!):** funciones duplicadas como **miembro privado** en
  `WorldMapViewModel.kt` + **extensión** del mismo nombre en `WorldMap*.kt`. Cuando se llaman desde
  DENTRO de la clase (caso de `startGameLoop()`, invocado en `WorldMapViewModel.kt:399`), **gana el
  miembro**; la extensión queda **muerta**. Caso real: el game loop miembro NO llamaba a
  `applyNpcContactDamage`/`runOverNpcs` (solo la extensión muerta) → ni NPCs agresivos ni zombis hacían
  daño. **Edita el miembro, no la extensión.** Verifica ambos.
- **Render de zombis — orden de ramas:** los 3 renderers (OSM nativo, Google nativo, web) revisan
  `npc.visualConfig != null` ANTES que `NpcType.ZOMBIE`. Un zombi con `visualConfig` se dibuja como
  humano. Solución doble: el **seed pone `visualConfig=null`** (`NpcAiManager`) **y** la rama de peatón
  lleva la guarda `&& npc.type != ZOMBIE`. No quitar ninguna.
- **Sprites web async — `cacheKey` acotado:** el render web genera el base64 en una coroutine. Si el
  `cacheKey` incluye un frame sin acotar (p. ej. `(timeMs/220).toInt()`), cada frame crea un key nuevo y
  la imagen nunca se registra a tiempo → no se ve. Acota el frame (`% 9` para `z_walk`). El nativo no
  sufre esto porque genera el drawable síncrono.
- **Modo zombi global = `Multiplayer/server.js`, NO `MultiplayerInteriores/`:** el apocalipsis ocurre sobre
  el mapa del mundo abierto (conexión `MULTIPLAYER_SERVER_URL`). `MultiplayerInteriores/` es el minijuego de
  interiores (otra conexión, `INTERIORS_SERVER_URL`). Son dos sistemas separados.
- **Instancing del mundo abierto (Normal vs Apocalipsis):** el apocalipsis ya **NO** es un flag global; es
  una **instancia** (`ws.instance` en el servidor). Todo el relay/roster va **acotado por instancia**
  (`broadcastToOthers`/`broadcastToNearby` filtran por `instOf`; cada NPC lleva `instance`; AOI/GC/cap/
  sync/Host por instancia). El cliente cambia de mundo con **`JOIN_INSTANCE`** (no `ZOMBIE_MODE_SET`, que
  fue eliminado) y limpia `remoteEntities` al cambiar. **No re-introducir un broadcast global** del
  apocalipsis: rompería la separación (los de "normal" no deben ver zombis).
- **Rename:** el servidor de interiores es `MultiplayerInteriores/` (antes `MultiplayerZombie/`) y su URL
  es `BuildConfig.INTERIORS_SERVER_URL` (antes `ZOMBIE_SERVER_URL`).
- **Población de NPCs dinámica (gama + ciudad + usuario):** `NpcAiManager` escala `maxActiveNpcs`/
  `maxTotalNpcs`/`carPopulationRatio` por `popFactor = deviceTierFactor × urbanFactor × userPopulationFactor`.
  `deviceTierFactor` lo fija el `Factory` desde RAM (no persiste); `urbanFactor` sale de la **densidad de la
  red OSM** en `updateRoadNetwork` (proxy GRATIS de ciudad, se recalcula al viajar); `userPopulationFactor`
  es el slider de **Ajustes→Jugabilidad** (`getNpcDensity`). **No** hardcodees los topes otra vez: ajústalos
  vía estos factores.
- **LOD de emojis (`npcEmojiLod`, "Optimizar dibujado de NPCs") = SOLO OSM nativo (por ahora):**
  `uiState.npcEmojiLod` solo lo respeta `NativeOsmMap` (NPCs >40 m → emoji). Web (Leaflet) y Google
  nativo **aún no** lo aplican. En cambio, **`npcFullEmoji` ("Optimizar para gama baja") SÍ aplica en
  los TRES renderers**: TODOS los NPCs como emoji (OSM nativo fuerza `useEmojiLod`; web envía un base64
  por emoji cacheado como `full_emoji_*`; Google usa la rama `GM_FULL_EMOJI_*`).
- **Anti-duplicación de autos (botón Y):** subir/bajar tiene **debounce de 450 ms**
  (`lastVehicleToggleMs`) y al abordar se registra el id en **`boardedCarTombstones`** (~10 s):
  el volcado de `processedNpcs` del game loop y `addRemoteEntity` (NPC_BATCH_UPDATE) IGNORAN esos
  ids — sin esto, el snapshot viejo de la IA re-insertaba el coche recién abordado (carrera
  main-thread vs loop) y spamear Y lo duplicaba. Además el abordaje manda el id a
  `pendingDespawns` (NPC_DESTROY para los demás clientes).
- **IA de tráfico — compromiso de intersección y realismo:** en `NpcAiManager.moveNpc`, los autos
  ahora tienen `committedWayId` y `commitmentTicks`. Al llegar a una esquina/bifurcación,
  eligen un camino y lo **bloquean por ~15 ticks**. NO re-calculan el target cada tick, lo que
  elimina el "temblor" indeciso. Además, implementan **evitación de alcance** (frenado de emergencia
  si el auto de adelante está a <8m, y seguimiento realista a ~27m) y **variación de velocidad**
  (`speedVariation` 0.8–1.2) para un tráfico más realista. El Host simula esto y lo replica vía
  `NPC_BATCH_UPDATE` (posición/rotación).
- **Combate melee online (jugador vs jugador):** `performPlayerAttack` ahora también checkea
  distancia contra `remoteEntities` (otros jugadores). Si golpea a uno, envía el mensaje existente
  `PLAYER_DAMAGE { targetId, damage }` al servidor. El servidor reenvía el mensaje globalmente,
  y la víctima (quien tenga ese `targetId`) aplica `takeDamage`. Es **autoritativo del atacante**
  para el melee (igual que el daño a NPCs). Se asegura que `displayName` nunca sea blank para
  identificar correctamente a los jugadores remotos. No re-introducir validación server-side para
  melee para no añadir latencia.
- **Orden de carga del mundo / gate de teleport:** la simulación/spawn de NPCs del game loop está
  gateada por `isRoadNetworkReady && isMapReady` → tras un teleport (ambos flags en false) el orden
  es SIEMPRE tiles → calles → NPCs. `teleportTo` además **rechaza TPs encadenados** mientras el
  mundo no esté completo (muestra "⏳ Espera…" vía `interactionPrompt`) y **limpia los NPCs** de la
  zona vieja (`remoteEntities` sin displayName + `setServerNpcs(emptyList())`).
- **Velocímetro CALIBRADO, no físico:** el avatar se desplaza a ~204 km/h geográficos reales a
  MAX_SPEED (movimiento acelerado del juego); el widget mapea linealmente MAX_SPEED → **120 km/h**
  para que se sienta creíble. Si retocas MAX_SPEED, revisa el mapeo en `WorldMapScreen`.
- **Heading de coches NPC = sprite (no el ángulo crudo):** en los dos movers de `NpcAiManager`
  (calles OSM y navGraph de campus) el sprite usa `smoothedAngle` pero el coche se MOVÍA con el
  ángulo crudo al objetivo → en curvas/esquives se veía "manejando de lado". Los `CAR` se mueven
  en la dirección de su sprite SOLO con desvío pequeño (`|diff| < 50°`); con desvío grande avanzan
  DIRECTO al objetivo. ¡OJO!: mover SIEMPRE por el heading suavizado causa el bug clásico de
  pure-pursuit (con desvío grande y radio de giro insuficiente el coche ORBITA su objetivo en
  círculos — pasó junto al jugador cuando el esquive de tráfico movía el objetivo). No quitar el
  umbral.
- **Rebase de NPCs = esquive en el MARCO del NPC (NO empujones de posición):** el viejo "shove"
  desde el loop del jugador (desplazar la POSICIÓN del NPC perpendicular al jugador) causaba
  órbitas/oscilaciones alrededor del jugador detenido (empuje → su road-following lo regresaba →
  empuje otra vez). Se ELIMINÓ. Ahora `NpcAiManager.moveNpc` desplaza **el OBJETIVO** del coche
  un carril (`TRAFFIC_AVOID_*`: radio ~13 m, ancho de trayectoria ±5.5 m, apertura máx ~3.3 m,
  histéresis de ~5.5 m tras rebasar) mientras el jugador esté en su trayectoria; al pasarlo el
  offset se apaga y el smoothing lo reincorpora. La geometría es auto-reforzante (el lado elegido
  se estabiliza solo). **No re-introducir empujones de posición** — cualquier esquive nuevo debe
  mover objetivos/rumbos, no posiciones.
- **Rebase de NPCs — AVANCE de nodo al esquivar (anti-órbita):** mientras un coche esquiva al jugador,
  `moveNpc` persigue un carrot LOCAL (~9 m), por lo que `dist` nunca baja de `actualSpeed` y el
  `targetNodeIndex` NO avanzaba: al apagarse el esquive el nodo base quedaba DETRÁS y el coche se daba
  la vuelta hacia el jugador → bucle/órbita ("rara vez me rebasan"). Fix: una bandera `avoidingPlayer`
  y, en el return final, si el coche ya REBASÓ el nodo base (producto punto `(baseTarget - newPos)·paso < 0`),
  se avanza `targetNodeIndex` aunque no haya "alcanzado" el carrot. Así sigue su ruta y te rebasa de
  verdad. No quitar la condición (sin ella vuelve el bucle).
- **Población de NPCs — topes base bajados:** `NpcAiManager.maxActiveNpcs`/`maxTotalNpcs` base no-zombi
  pasaron de 26/55 a **18/38** (siguen escalando por `popFactor`) para reducir la saturación ("se generan
  muchos NPCs"). Ajusta densidad por estos topes/factores, no hardcodeando otra cosa.
- **Culling de NPCs = borde del fog (`NPC_CULL_MARGIN_M=0`):** antes era +15 m sobre `NPC_FOG_VISION_METERS`
  (70 m), así que los civiles se dibujaban hasta 85 m, FUERA de la zona despejada ("veo NPCs fuera del fog").
  A 0 m el culling de sprites (los 3 renderers usan `npcVisionRadiusMeters`) coincide EXACTO con el borde
  del fog. La policía fuera del fog sigue como waypoint 🚓 (handoff limpio en 70 m mientras `wantedLevel>0`)
  y Prankedy tiene render propio sin culling → **Prankedy y policía siempre visibles**. No volver a subir
  el margen (re-aparecen NPCs fuera del fog).
- **Subirse a PATRULLAS (POLICE_CAR) = 5★:** las patrullas las posee `PoliceManager` (no `remoteEntities`).
  `PoliceManager.boardPatrol(id)` saca la unidad y la devuelve; `onInteractButtonPressed` (MIEMBRO del VM),
  si no hay coche civil cerca, busca una patrulla en `policeManager.activeUnits()` dentro de `INTERACT_RADIUS`,
  la aborda, difunde `POLICE_DESTROY` y fija `wantedLevel = MAX_WANTED_LEVEL`. **Skin de patrulla
  conducible:** `WorldMapState.isDrivingPoliceCar` (se pone al abordar, se limpia al bajarse/carjack/morir);
  `PlayerCharacter` dibuja el asset de `PoliceSpriteManager.getPoliceCar` en vez del coche tintado. El
  avatar conductor es un overlay Compose común a los 3 renderers → un solo cambio cubre OSM/Google/web.
  El flag DEBE limpiarse en TODAS las salidas (`onInteractButtonPressed` else, `forceExitVehicle`,
  `triggerWastedSequence`) o conducirías un auto civil con skin de patrulla.
- **Patrulla ABANDONADA conserva el skin (`Npc.isPoliceSkin`):** al bajarte de una patrulla robada el
  coche que queda NO puede ser `type=POLICE_CAR`: los coches abandonados se re-simulan por la IA y un
  POLICE_CAR no casa con ninguna vía en `moveNpc` → `return null` → **se despawnea**. Solución: el coche
  abandonado es `type=CAR` con `isPoliceSkin=true` (lo ponen `onInteractButtonPressed` else y
  `forceExitVehicle`). La IA lo conduce como tráfico normal y los **3 renderers** lo dibujan como patrulla
  cuando `type==POLICE_CAR || isPoliceSkin`. Es re-abordable por el filtro civil (sigue siendo CAR); al
  re-subirte, `isDrivingPoliceCar = carNpc.isPoliceSkin`. No genera waypoint falso (el de policía filtra
  por `type==POLICE_CAR`). `isPoliceSkin` NO viaja en `MultiplayerNpc` (cosmético/local).
- **Prankedy SIEMPRE cerca de ti (leash + no caza cops):** `findAggressorNearPlayer` ya **no** incluye
  `POLICE_COP` (los perseguía y se alejaba al llegar la policía) y hay una **correa** (`LEASH_MAX`≈33 m):
  si Prankedy quedó más lejos que eso del jugador, ignora al agresor y su objetivo pasa a ser el jugador
  (regresa a tu lado). No re-añadir cops al detector ni quitar la correa.
- **Prankedy ANTI-TRABA (igual que la policía):** el `snap` a la calle podía dejarlo "pegado" a un nodo
  sin avanzar ("se traba y ya no te hace nada"). Dos defensas en `PrankedyManager`: (1) al correr, si el
  punto snapeado NO acerca al objetivo, usa el paso DIRECTO ese tick; (2) si lleva > `STUCK_TIME_MS`
  (1.5 s) sin moverse > `STUCK_EPS` mientras está lejos, `relocateNear` lo reubica sobre calle cerca del
  jugador **SIN curarlo** (conserva vida/estado). No usar `spawn()` para reubicar (resetea la vida).
- **NPC "invisible" que te golpea (peatón sin sprite):** si `CharacterSpriteManager.getModularNpcDrawable`
  devuelve `null` (frames del asset aún no cargados, p. ej. tras un TP o tras `onTrimMemory`), el peatón
  se simulaba (y te pegaba) pero no se veía. Causas por renderer: **OSM nativo** cacheaba un drawable
  **transparente** bajo el `cacheKey` (invisible permanente); **web** hacía `if(!cachedImg) return` (no
  creaba el marcador). Fix: **nunca dejar un NPC invisible**. Nativo: si el sprite es null NO se cachea el
  fallo (se reintenta) y se muestra emoji 🧍 (`PED_FALLBACK_*`). Web: fallback 🧍/🚗 mientras se genera el
  base64, y al llegar el sprite el marcador se recrea. Google nativo ya caía a `defaultMarker()` (visible).
  Regla: cualquier rama de render de NPC debe terminar en algo VISIBLE, nunca en transparente/return.
- **Mano del Apocalipsis ELIMINADA de ESCOM:** `spawnEscomItems` ya NO crea el collectible
  `global_zombie_hand`. El modo zombi global se activa SOLO por Opciones → "Activar/Desactivar
  Apocalipsis" (y el botón flotante de salida). El game loop sigue llamando a `spawnEscomItems` al entrar
  a ESCOM, pero ahora solo marca `isZombieHandSpawned=true` (sin mano). No re-spawnear la mano.
- **"Debug Interiores" dibuja el navGraph + zonas NO caminables:** el overlay (`showInteriorDebugOverlay`)
  además de los puntos de edificios + bbox de ESCOM, dibuja: (a) **caminos del `navGraph`** de cada landmark
  (VERDE = `isForPeople`, NARANJA = `isForCars`), y (b) las **colisiones** de `exterior_collisions.json`:
  **polígonos ROJOS translúcidos** = `exteriorCollisions.polygons` (zonas donde NO se puede caminar, p. ej. el
  edificio ESCOM) + **líneas ROJAS** = `walls` (bardas). `exteriorCollisions` antes era privado del VM; ahora
  se expone en `WorldMapState` (`loadExteriorCollisions` hace `_uiState.update`) para poder dibujarlo.
  Implementado en **OSM nativo** (`NativeOsmMap`: `Polygon`/`Polyline` cacheados, rebuild solo si cambian
  landmarks o colisiones) y **web** (`updateInteriorPaths` recibe `{paths, blocks, walls}`; el VM convierte
  `localX/localY → global` con `toGlobalGeoPoint`). Google nativo = pendiente (como el resto del overlay).
- **Zoom automático por estado + SUAVIZADO:** `updateAutoZoom()` (miembro del VM, llamado cada tick del game
  loop miembro) cambia `targetZoomLevel` SOLO en transiciones: a pie `ZOOM_ON_FOOT=22`, conduciendo
  `ZOOM_DRIVING=21`, ≥85% MAX_SPEED `ZOOM_DRIVING_FAST=20` (vuelve a 21 bajo el 65%). El pinch del
  usuario se respeta entre transiciones. **NO cambies `zoomLevel` directamente en transiciones;**
  el game loop interpola `zoomLevel` hacia `targetZoomLevel` (~1 nivel por tick) para evitar el
  "shock" visual brusco al robar/salir de un auto. No volver a clavar el zoom web a 19 (`setMapProvider`
  ahora capea a 22; `ZOOM_GAMEPLAY_WEB=19` es solo el nivel de pre-descarga de tiles).
- **Fixes de estabilidad (TP/skin/ESCOM):** (a) el snap-to-road de `moveCharacter`/`moveCharacterByAngle`
  **ignora el movimiento** si la calle más cercana está a > `MAX_SNAP_DISTANCE_DEG` (0.0003 ≈ 33 m) — antes
  TELETRANSPORTABA al jugador a una calle al azar cuando la red recién recargada no cubría su zona; (b) el
  GPS usa `getCurrentLocation(PRIORITY_HIGH_ACCURACY)` con fallback a `lastLocation` (que es caché y mandaba
  a ubicaciones viejas); (c) el JS web tiene watchdog anti-congelamiento (`isZooming` >1.5 s se auto-resetea;
  `isExplorationMode` sin dedo en pantalla se limpia en `updateMapView`) — causa del "me muevo pero el skin
  no"; (d) `NpcAiManager` **re-puebla** campus (ESCOM) si está marcado como poblado pero sin NPCs vivos
  (cooldown 30 s) — antes los NPCs se despawneaban a ~310 m pero el campus solo se "des-poblaba" a ~2.2 km;
  (e) el re-fetch de red (miembro) ahora también reconstruye `rebuildRoadNodeGrid` + `buildRoadGraph`.
- **Prankedy (NPC especial) — loop miembro + paridad de render + toggle:** su tick/spawn deben correr en
  el **game loop MIEMBRO** de `WorldMapViewModel.kt` (la extensión `WorldMapGameLoop.kt` está sombreada →
  no corría: "no aparecía en el mapa"). Se dibuja en **OSM nativo Y web** (solo nativo no basta: el default
  es web/CARTO). El estado vive en `WorldMapState.prankedyEnabled` (item de Opciones "Activar/Desactivar
  Prankedy", **default OFF**); `checkPrankedySpawn`/`runPrankedyTick` no-opean si está apagado. El tanque
  (`p_objeto`) **solo pega si sigues dentro de `IMPACT_RADIUS`** al caer (esquivable). El sprite va a ~1.3 m
  (tamaño peatón), sin emoji flotante. IA → 03; render → 04; PR → `PR_CHANGES_EN.md`.

- **Umbrella `interiores` (reestructura grande):** `features/zombie_minigame/`, `features/interior/` y
  `features/shinecto/` se fusionaron bajo **`features/interiores/`** con 4 subpaquetes: **`core`**
  (compartido: `DesignerTarget`+`CameraTransform` en `core/viewmodel/InteriorDesignerModels.kt`;
  `PlayerView`/`PlayerHealthBarFixed`/`RemotePlayerView`+`playerViewPath` en `core/ui/InteriorPlayerViews.kt`;
  designer layers en `core/ui`), **`escom`** (interiores simples + metro, antes `interior/`), **`zombies`**
  (antes `zombie_minigame/`) y **`shinecto`**. Objetivo: separar zombis de interiores y poder añadir más
  universidades. El metro y shinecto ya **NO** importan del paquete de zombis (toman lo compartido de `core`).
  El `CollisionMatrixRepository` (su package ya era `data.repository`) se movió físicamente a `data/repository/`.
  Ruta de nav `"zombie_minigame"` → **`"interiores_zombies"`**. Al añadir una universidad nueva, crea
  `interiores/<uni>/` reutilizando `core`. **No** re-acoplar `escom`/`shinecto` a `zombies`.
- **Spawn FIJO en ESCOM:** `MainActivity` ahora arranca SIEMPRE en el punto del teletransporte "ESCOM"
  (`SPAWN_ESCOM_LAT=19.504603`, `SPAWN_ESCOM_LON=-99.145985`, = `TeleportCatalog.zones[0]`), ignorando el
  GPS real (`fetchCurrentLocation`/fallback ya solo llaman a `updateInitialLocation(ESCOM)`). Para volver al
  spawn por GPS, restablece la lectura de `getCurrentLocation` en `fetchCurrentLocation`.
- **Detección de modo en servidores:** el cliente de interiores (`ZombieGameViewModel`) envía un campo
  **`mode`** ("interiores" = lobby tranquilo | "zombies" = edificio/horda o lobby con apocalipsis;
  `currentNetMode()`) en `JOIN_ROOM` y `PLAYER_UPDATE`. El mundo abierto ya distingue **mapa global**
  (instancia `"normal"`) de **zombies global** (instancia `"apocalipsis"`) vía `JOIN_INSTANCE`. Los
  `server.js` (no incluidos en este checkout) deben leer estas señales para enrutar/contabilizar por modo
  (ver 08). No re-introducir un flag global de apocalipsis (rompe el instancing).
- **Convención de nombres de assets (inglés/snake_case):** se renombraron carpetas/archivos con español o
  typos: `PRINCIPAL→MAIN`, `INTERIORES→INTERIORS`, `LUGARES→PLACES`, `ZOMBIS_MOD→ZOMBIES_MOD`,
  `coleccionables→collectibles`, `metroCDMX→metro_cdmx`, `assetsNPC/cabello→assetsNPC/hair`,
  `assetsNPC/otherPlayer→assetsNPC/other_player` (¡también el literal `bodyFolder="other_player"`!),
  `Prankedy/p_atack→p_attack` (typo; prefix `p_attack_`), `shineCTO→shine_cto`,
  `deportivomiguelaleman→deportivo_miguel_aleman`, `plazalindavista→plaza_lindavista`,
  `ZOMBIES_MOD/interiores→interiors`, `zombi_hand→zombie_hand`, `Carga_Mod_Zombi→load_zombie_mod`,
  `metro_cdmx/mapa.png→map.png`. **PENDIENTE (alto riesgo, requiere la app para verificar el arte):** las
  SUBcarpetas de skin camelCase (`MAIN/escomgirlIdle`, `MAIN/lazaroWalk`…, construidas en `PlayerSkin` con
  `"${skinFolder}Idle"`) y los ~500 frames de vehículos (`White_*_CLEAN_All_###`, ya en inglés). Toda ruta de
  asset es un STRING (literal o `"$folder/..."`); un rename mal hecho falla en RUNTIME, no al compilar —
  verifica que cada literal resuelva a un archivo existente.

- **i18n / internacionalización (strings.xml + cambio de idioma):** los textos de UI se externalizan a
  recursos. **Base = español** en `res/values/strings.xml`; **inglés** en `res/values-en/strings.xml`
  (paridad 1:1 de claves). Para añadir un idioma (p. ej. ruso) basta con crear `res/values-ru/strings.xml`
  y sumar su etiqueta a `LocaleHelper.SUPPORTED` (el selector de Ajustes lo muestra solo). Reglas:
  - En Composables usa `stringResource(R.string.clave[, args])`; en no-Composables (Activity) `getString(...)`.
    Para textos en lambdas `onClick` (no son Composables) **hoistea** el `stringResource` a un `val` antes.
  - **Formato:** args posicionales `%1$s`/`%1$d`; un `%` literal en una cadena CON args va como `%%`.
  - **Modelos/enums** que llevaban texto (p. ej. `SettingsCategory.title`) pasan a `@StringRes titleRes`
    y la View resuelve con `stringResource`. Los `displayName` técnicos/propios (MapProvider, ControlType)
    se dejan como están (nombres propios).
  - **Cambio de idioma sin AppCompat:** `i18n/LocaleHelper.wrap(ctx, tag)` envuelve el Context en
    `MainActivity.attachBaseContext`; la elección se persiste en `SettingsRepository.get/saveLanguage`
    ("" = sistema) y el selector (`SettingsScreen` → Interfaz) **recrea la Activity** al cambiar.
  - **Migración por feature (fases):** ya migrados `main_menu` y `settings` (patrón). Pendiente: el resto
    de features (los grandes `map_exterior`/`interiores.zombies` concentran la mayoría de strings). NO migrar
    rutas de assets, tipos de mensaje de red (`"PLAYER_UPDATE"`…), tags de log ni URLs: NO son texto de UI.
  - **Claves:** `snake_case`, prefijadas por feature (`menu_*`, `settings_*`). Toda clave nueva va en
    `values/` **y** `values-en/` (una contradicción/ausencia = bug; mantén la paridad).

---

## 13. PROTOCOLO DE ACTUALIZACIÓN DE DOCS / DOC UPDATE PROTOCOL (obligatorio / mandatory)

**ES:** `README.md` + `plan.artifact.md` + **esta carpeta** son la **única fuente de verdad** que se le
pasa a un asistente en vez de todo el código. Solo sirven si se actualizan **en el mismo cambio** que
toca el código. Trátalos como parte del entregable.

**EN:** `README.md` + `plan.artifact.md` + **this folder** are the **single source of truth** handed to an
assistant instead of the whole codebase. They only work if updated **in the same change** that touches the
code. Treat them as part of the deliverable.

### Checklist (en CADA cambio que altere comportamiento / on EVERY behavior change)

| Si cambiaste… / If you changed… | Actualiza aquí / Update here |
|---|---|
| Archivo/feature nuevo o renombrado | `01` árbol/rutas, `04`/`05` tabla Key files, README árbol, plan §2/§4 |
| Comportamiento de NPCs/routing/cachés/combate/zombis | doc del feature (`03`–`06`), README (EN **y** ES), plan §5 |
| Una convención/regla/gotcha | `09`, plan §6 |
| Algo que antes no funcionaba ya funciona | README "Current Status"→Works, plan §7 (quita de §8) |
| Protocolo de red / tipos de mensaje | `08` tablas, README Multiplayer (EN+ES), plan §5 |
| Esquema Room (entidad/migración) | `02`, README Tech Stack, plan §6 (DB version) |
| Nuevo build step / env var / puerto | `01`/`08`, README Deploying, plan §9 |

### Reglas / rules
- **README.md es bilingüe (EN+ES):** todo cambio user-facing va en **ambas** secciones.
- "Recent Changes / Cambios Recientes" = log corto: añade arriba, poda lo de >2–3 releases.
- Si un dato vive en varios archivos, cámbialo en todos. **Una contradicción es un bug.**
- Edita líneas existentes antes que añadir; estos docs deben quedar **mínimos-pero-completos**.

### Definición de "hecho" / Definition of done
El cambio está completo solo cuando: **el código compila/valida** (Android Studio Rebuild; servidores
`node --check server.js`) **y** los tres conjuntos de docs describen la nueva realidad y concuerdan. Si no
puedes actualizar los docs, **la tarea no está terminada — dilo explícitamente.**
