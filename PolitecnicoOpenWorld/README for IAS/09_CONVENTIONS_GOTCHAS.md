# 09 · Convenciones, Gotchas y Protocolo de docs / Conventions, Gotchas & Doc protocol

**ES:** Lee esto **antes de editar**. Romper una de estas reglas suele introducir regresiones (sobre
todo de rendimiento en gama baja) o desincronizar la documentación.
**EN:** Read this **before editing**. Breaking one of these usually causes regressions (especially
low-end performance) or doc drift.

---

## 0. Archivos GRANDES (>1000 líneas) — plan de separación

> ### ✅ ESTADO ACTUAL (2026-06-21) — esto MANDA sobre el historial de abajo
>
> **Solo 5 archivos pasan de 1000 líneas; solo 1 pasa de 1500:**
>
> | Archivo | Líneas | ¿Separar? |
> |---|---:|---|
> | `WorldMapViewModel.kt` | **2114** | Único >1500. Lo que queda grande dentro (`startGameLoop` ~490, `handleInteraction`, `trySpawningCollectible`/`checkCollectibleProximity`, `moveCharacter*`) puede extraerse a parciales NUEVOS sin gemelo, pero NO urge. |
> | `NativeOsmMap.kt` | 1460 | No (renderer Canvas cohesivo). |
> | `WorldMapScreen.kt` | 1326 | No (raíz Compose ya partida en Overlays/Controls/Google/Web). |
> | `MainActivity.kt` | 1064 | Opcional (NavHost). Subió un poco por la i18n verbosa. |
> | `ZombieGameScreen.kt` | 1035 | No. Subió por la i18n. |
>
> **✅ DE-DUP DE GEMELOS miembro-vs-extensión: COMPLETA (2026-06-21).** Los 8 pares quedaron resueltos
> (detalle en `§12 (registro de de-dup, consolidado aquí)`): **de-dup limpio** = `startHealthBarTimer`, `applyRoadNetwork`,
> `spawnOustedDriver`, `triggerWastedSequence`, `addRemoteEntity`, `maybeRefetchRoadNetwork`,
> `updateVisibleRoads`. **Fusionados** (se activó lógica buena que estaba muerta) = `handleMultiplayerMessage`
> (3 bugfixes: isRemote sync, daño en hilo Main, miedo al combate) y `startGameLoop` (audio del game loop:
> caminar/correr/coche/zombi). **NO TOCAR** (revertido, cadena de routing interdependiente) =
> `updateDestinationRoute`+`calculateRouteOnNetwork`. `WorldMapGameLoop.kt` quedó como **tombstone**
> (startGameLoop volvió a ser solo miembro, con el audio fusionado). Ya NO hay gemelos divergentes vivos.
>
> **✅ i18n player-facing COMPLETA** (main_menu, settings, map_exterior, campaign, interiores incl. transit
> y diseñadores, MainActivity). Pendiente menor: `CampaignObjective.title/description` → `@StringRes`. Ver §i18n.
>
> *(El historial cronológico de las pasadas de separación queda abajo como registro; los tamaños y el estado
> de de-dup de ARRIBA son los vigentes.)*

**🆕 Progreso (2026-06-20):** `WorldMapViewModel.kt` bajó de ~3050 a **~2600** líneas extrayendo 4
parciales nuevos cohesivos (sin gemelo miembro, solo tocan `internal`/`public`): `WorldMapCombat.kt`
(combate: `performPlayerAttack`/`runOverNpcs`/`provokeApocalypsePolice`/`applyNpcContactDamage`/
`startRelentlessAttacker`), `WorldMapCampaign.kt` (`setStorySpawn`), `WorldMapTeleport.kt`
(`teleportTo`/`teleportToMetroStation`/`loadMetroStations`/`teleportToMetrobusStation`/
`loadMetrobusStations`/`toggleTeleportMenu`) y `WorldMapShineCTO.kt` (`spawnShineCTOMarker`/ShineCTO/
fade de puerta ESCOM). Imports explícitos añadidos en `MainActivity.kt` (6) y `WorldMapScreen.kt` (3).
El combate no tenía call-sites externos (solo el game loop miembro lo llama → resuelve a la extensión).

**🆕 Progreso (2026-06-20, 2ª pasada Compose):** `WorldMapScreen.kt` bajó de ~2470 a **~2354** líneas
extrayendo los overlays/diálogos superpuestos (pantalla WASTED, vídeo zombi, prompts, diálogo Prankedy,
popup de coleccionable, fades de puerta ESCOM/metro/metrobús) al composable `WorldMapOverlays` en
`ui/WorldMapScreenOverlays.kt` (mismo paquete `ui`, ~174 líneas). `WorldMapScreen` solo lo invoca:
`WorldMapOverlays(uiState, viewModel, onNavigateToInterior)`. Sin gotcha miembro/extensión (son
composables top-level); las extensiones del VM usadas se importan en el archivo nuevo
(`onHirePrankedy`/`dismissPrankedyDialog`/`onEscomDoorFadeComplete`). MVVM intacto.

**🆕 Progreso (2026-06-20, 3ª pasada):** dos extracciones más:
- `WorldMapScreen.kt` bajó de ~2354 a **~2255** líneas extrayendo el bloque de controles (vals de
  layout escala/padding según orientación + botón "Salir del apocalipsis" + fila inferior de D-pad/
  joystick/acciones, incl. la pulsación larga de Y/△ → `yButtonHoldJob`) al composable
  `BoxScope.WorldMapControls` en `ui/WorldMapScreenControls.kt`. Se invoca dentro del `Box` principal:
  `WorldMapControls(uiState, viewModel, optionsExpanded)`. `yButtonHoldJob` se movió al nuevo archivo.
  Sin gotcha miembro/extensión (composable top-level); importa la extensión `toggleTeleportMenu`.
- `ZombieGameViewModel.kt` bajó de ~1370 a **~1120** líneas extrayendo TODO el Modo Diseñador (matriz
  de colisión + waypoints: ~16 funciones) a `viewmodel/ZombieGameDesigner.kt` como **extensiones** del
  VM (solo tocan `internal`/`public`: `_state`, `currentRoom()`, `applicationContext`, `viewModelScope`;
  las consts de rejilla del companion se referencian cualificadas `ZombieGameViewModel.MIN_GRID`…).
  Se ELIMINARON los miembros (no queda gemelo). `ui/ZombieGameScreen.kt` importa las 15 extensiones
  usadas — incl. referencias acotadas `viewModel::paintCellAtWorld` (Kotlin permite `::` a extensiones).

**🆕 Progreso (2026-06-20, 4ª pasada):**
- `NativeOsmMap.kt` ~1615→**1457**: `renderPrankedyOnMap`→`ui/NativeOsmMapPrankedy.kt` y la clase
  `FogOverlay`→`ui/NativeOsmMapFog.kt` (ambas `internal`, mismo paquete `ui`).
- `NpcAiManager.kt` ~1619→**1419**: cluster de movimiento/geometría (`moveZombieNpc`, `movePoliceHunter`,
  `moveAggroNpc`, `carFollowScale`, `pointToLineDist`, `calculateDistance`) →
  `domain/models/ai/NpcAiManagerMovement.kt` como **extensiones** `internal fun NpcAiManager.X`. Para que
  las extensiones vieran el estado, se pasaron a `internal` 5 miembros (`serverNpcs`, `personSpeed`,
  `aggroPlayerLat/Lon`, `moveNpc`); los miembros del companion se cualifican
  (`NpcAiManager.speedMulForRole`/`CAR_FOLLOW_DISTANCE`/`AGGRO_STOP_DIST`/`AGGRO_SPEED_MULT`). Eran
  todos `private` → sin call-sites externos; los internos resuelven a la extensión del mismo paquete.
- 🆕 `domain/models/InteriorEntryCatalog.kt` (registro puerta→ruta) + `handleInteraction` ahora
  data-driven (añadir edificio enterable = 1 entrada). 🆕 Reorg de **campaña** a `features/campaign/`
  (StoryMode) + `domain/models/campaign/` (CampaignObjective, MissionCatalog **fachada**, SchoolCatalog,
  StoryComicCatalog) + `domain/models/campaign/mission1/Mission1.kt`. 🆕 Reorg de **assets** (ver
  `PROPUESTA_reorg_assets.md`): AUDIO/SPRITES/TRANSIT/CONFIG/INTERIORS/VIDEO + iconos a `SPRITES/ICONS`.

**🆕 Progreso (2026-06-20, 5ª pasada):** `WorldMapViewModel.kt` ~2584→**~2503** extrayendo dos parciales
nuevos (extensiones, sin gemelo): `WorldMapCameraUi.kt` (zoom automático/manual, pinch `onMapZoomChanged`,
`centerOnPlayer`/`zoomToPlayer`, pan, y toggles de widgets; campos `autoZoomMode`/`targetZoomLevel`
pasados a `internal`) y `WorldMapSettings.kt` (densidad/LOD de NPCs + skin). Call-sites externos importan
las extensiones (MainActivity, WorldMapScreen, NativeOsmMap, MapJsBridge). **Tope práctico de extracción
fácil alcanzado:** lo que queda grande en el VM (startGameLoop ~440, handleMultiplayerMessage,
addRemoteEntity, updateVisibleRoads, updateDestinationRoute, triggerWastedSequence) son **pares con
gemelo en parciales** (miembro canónico) y/o llaman a muchos `private` → su separación = de-duplicar los
gemelos, que requiere COMPILADOR (ver lista de pares pendientes en §12). No tocar sin Android Studio.

**🆕 Progreso (2026-06-20, 6ª pasada):** `NpcAiManager.kt` ~1419→**~882** extrayendo los dos movers
de calles GRANDES (`moveNpc` ~382 + `moveLocalNpc` ~159) a `domain/models/ai/NpcAiManagerTraffic.kt`
como **extensiones** `internal fun NpcAiManager.X`. Para que las extensiones vieran el estado se
pasaron a `internal` ~15 miembros antes `private` (`cachedNavLandmarks`, `nodeToWays`,
`exteriorCollisions`, `parkedTimers`/`parkingCooldowns`/`carExitCooldowns`/`landmarkEntranceCooldowns`,
`carSpeed`, `PARKING_WAKE_MIN_MS`/`PARKING_WAKE_MAX_MS`, los 5 `TRAFFIC_AVOID_*`,
`isNativeWayOverlappingCustom`); los consts del companion se cualifican (`NpcAiManager.LANE_OFFSET`,
`NpcAiManager.FEAR_SPEED_MULT`). Se ELIMINARON los miembros (no queda gemelo: gana el miembro). Los
call-sites son del mismo paquete `ai` (`updateNpcs` miembro + `moveZombieNpc`/`movePoliceHunter`
extensiones en `NpcAiManagerMovement.kt`) → resuelven a la extensión sin imports nuevos. El archivo
nuevo quedó en LF (normalizar fin de línea en Android Studio o dejar LF, compila igual).

**🆕 Progreso (2026-06-21, 7ª pasada Compose):** `WorldMapScreen.kt` ~2255→**~1817** extrayendo la
rama **Google Maps nativo** del `when (mapProvider)` (CAPA 1: MAPA) al composable top-level
`GoogleMapLayer` en `ui/WorldMapScreenGoogle.kt` (~458 líneas movidas). Captura solo 7 símbolos →
parámetros: `uiState`, `viewModel`, `context`, `roadNetwork`, `allCollectibles`, `landmarkBitmapCache`,
`googleMapsIconCache` (las cachés LRU se pasan, no se recrean). Sin gotcha miembro/extensión (composable
top-level); las extensiones del VM usadas se importan (`onMapZoomChanged`/`selectLandmark`/
`moveSelectedLandmark`); helpers/consts del mismo paquete (`npcVisionRadiusMeters`/`npcWithinRadius`/
`emojiToDrawable`/`drawHealthBarOnDrawable`/`ExactSizeDrawable`/`NPC_FOG_VISION_METERS`) se ven sin import.
Archivo nuevo en LF.

**🆕 Progreso (2026-06-21, 8ª pasada Compose):** `WorldMapScreen.kt` ~1817→**~1325** extrayendo la
rama **WEB** (`else ->`, Leaflet en `AndroidView`/`WebView`, ~520 líneas) al composable top-level
`WebMapLayer` en `ui/WorldMapScreenWeb.kt`. Captura 23 símbolos → parámetros (más que Google): además
de `uiState`/`viewModel`/`context`/`roadNetwork`/`allCollectibles`, pasa `cachingClient`
(`CachingWebViewClient`), `webViewRef`, `gson`, `coroutineScope`, las cachés base64/tamaños
(`base64Cache`/`widthCache`/`heightCache`/`registeredWebImages`) y los **holders de guarda por-frame**
(`lastWebNpcHolder`/`lastWebLandmarkHolder`/`webLmTick`/`lastWebMetroHolder`/`webMetroTick`/`lastWebIpOn`/
`lastWebIpLm`/`lastWebIpColl`/`lastWebPoliceHolder`/`lastWebZombieHolder`) — son `Array`/`IntArray`/
`BooleanArray` mutables, así que el estado de guarda SIGUE vivo entre frames (no se rompe el anti-reenvío).
`buildHtml`/`MapJsBridge`/`CachingWebViewClient`/`NpcWebPayload`/`LandmarkWebPayload` son del mismo paquete
(sin import); OJO: el archivo nuevo SÍ necesita `import kotlinx.coroutines.launch` (las coroutines de
generación de base64). Archivo nuevo en LF. **WorldMapScreen ya <1500 ✅.** Las 3 ramas del `when` ahora
delegan: `NativeOsmMap` / `GoogleMapLayer` / `WebMapLayer`.

**🆕 Progreso (2026-06-21, reorg domain/models — package-move con Android Studio):** los **15 archivos
planos** que colgaban directo de `domain/models/` se movieron al subpaquete **`domain.models.map`**
(carpeta `domain/models/map/`): `ActiveCollectible`, `CharacterVisualConfig`, `EscomBuildings`
(incl. `InteriorBuilding`/`EscomBoundingBox`), `ExteriorCollisionsConfig` (incl. `CollisionWall`/
`CollisionPolygon`), `InteriorEntryCatalog`, `Landmark`, `Landmarkassetcatalog`, `MapNode`, `MapWay`,
`MetroStation`, `MetrobusStation`, `Npc` (incl. `CarModel`/`NpcType`/`NpcTrait`/`ZombieRole`/`NpcNavState`),
`ShineCTOLocation`, `TeleportCatalog`. Los subpaquetes `ai/ campaign/ zombie/` se quedan igual. Se hizo
con **Refactor → Move** de Android Studio (actualiza las ~308 referencias atómicamente; NO es viable a
mano sin compilador, ver §0 abajo). **⚠️ GOTCHA NUEVO:** el Move de AS **SE SALTA los archivos que están
ABIERTOS/sin guardar** en el editor → `WorldMapState.kt` quedó con sus imports/FQN viejos
(`domain.models.MetroStation`…) y rompió `MainActivity` (`station.name` sin resolver). Fix: actualizar a
mano sus refs a `.map` **respetando** las de `ai.`/`campaign.` (que NO se movieron). Al hacer un
package-move en AS: **cierra/guarda todos los archivos antes**, y tras compilar arregla cualquier
`Unresolved reference` restante (mismo patrón: añadir `.map`).

**⚠️⚠️ GOTCHA CRÍTICO — NO arregles los stragglers de un package-move con un Find&Replace "a secas":**
intentar `domain.models.` → `domain.models.map.` en todo el proyecto es un DESASTRE: (1) **dobla** los que
ya estaban bien (`domain.models.map.X` → `domain.models.map.map.X`), (2) **prefija los subpaquetes**
(`domain.models.ai/campaign/zombie` → `…map.ai`…) y hasta los `package`. Y si encima el Replace corre en
**case-INSENSITIVE** (default peligroso), un correctivo como `domain\.models\.map\.map` → `domain.models.map`
**se come el `.Map`/`.Zombie` de los tipos** y los muta: `MapWay`→`mapWay`, `MapNode`→`mapNode`,
`ZombieRole`→`zombieRole` (pasó de verdad; horas perdidas). **Reglas para package-moves:** (a) hazlos con
**Refactor → Move** de AS (no a mano); (b) si AS deja stragglers, arréglalos **uno por uno a mano** o con un
Find&Replace de **TEXTO PLANO** sobre el string EXACTO roto (p. ej. `domain.models.mapWay` →
`domain.models.map.MapWay`), nunca con un prefijo genérico; (c) si usas regex, **CASE-SENSITIVE + grupo de
captura**: `domain\.models\.([A-Z]\w*)` → `domain.models.map.$1` (ese solo toca tipos en Mayúscula y nunca
los subpaquetes en minúscula). Verifica al final con búsquedas que den 0: `domain.models.map.map`,
`domain.models.mapWay`, `domain.models.mapNode`, `domain.models.zombieRole`.

**ES:** Archivos AÚN candidatos a dividir (al 2026-06-21): `WorldMapViewModel.kt` (~2503, único >1500;
muy separado, resto = de-dup de gemelos CON COMPILADOR), `WorldMapScreen.kt` (~1325, **<1500 ✅**; opcional:
builder del menú Opciones),
`NativeOsmMap.kt` (~1457, parcialmente separado), `NpcAiManager.kt` (~882, **<1000 ✅**; si se quiere
seguir, queda el cluster de spawn `spawnNpcOnRoad`/`spawnParkedCar`/`spawnCampusPedestrian`/
`getAvailableParkingSlots` y `updateNpcs` ~370), `ZombieGameViewModel.kt`
(~1120, ya parcialmente separado). Plan SEGURO: extraer SOLO bloques cohesivos cuyas funciones toquen
exclusivamente miembros `internal`/`public` (o pasar a `internal` los `private` que necesiten, como en
NpcAiManager), a nuevos `WorldMap*.kt`/`ZombieGame*.kt`/`NpcAiManager*.kt`, verificando que NO existan
gemelos miembro (gana el miembro) y conservando CRLF. Para `WorldMapScreen`/`NativeOsmMap` (Compose),
extraer composables/clases por sección. Pasos pequeños y verificables, uno por archivo.

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
- **🆕 Micro-opts GC (2026-06-20, 7ª pasada, SIN cambio de comportamiento):**
  - `FogOverlay.draw` (~30 Hz): el `IntArray`/`FloatArray` del `RadialGradient` se **reutilizan** como
    campos (`fogColors`/`fogStops`); `RadialGradient` COPIA su contenido al construirse, así que mutar
    `fogStops[1]` cada frame es seguro. Antes asignaba 2 arrays nuevos por frame. **El propio
    `RadialGradient` sí se recrea cada frame** (el centro = píxel del jugador cambia); no se puede cachear
    sin reintroducir overdraw. No volver a `intArrayOf(...)`/`floatArrayOf(...)` inline en `draw`.
  - `NativeOsmMap.update`: la densidad se lee UNA vez por frame en `screenDensity` (línea ~259). Cuatro
    sitios re-llamaban `context.resources.displayMetrics.density` (waypoints policía/zombi/objetivo +
    marcador metrobús, este último con un `val screenDensity` que **sombreaba** el de arriba) → ahora todos
    usan el `screenDensity` cacheado. Usar SIEMPRE `screenDensity`, no re-leer `displayMetrics` dentro del loop.
  - **🆕 (8ª pasada, 2026-06-21) `NpcAiManager` tick:** `cachedWayBoxes.get().map { it.way }` se
    materializaba la lista COMPLETA de vías en cada chequeo de spawn (zombi/horda/policía) por tick. Ahora
    se precomputa UNA vez al fijar la red (`cachedWaysFiltered` en `updateRoadNetwork`, derivado de los
    mismos `boxes`) y los 3 sitios solo leen `cachedWaysFiltered.get()`. Mismo contenido (vías con nodos
    no vacíos), cero comportamiento.
  - **Ya bien optimizado (NO tocar a ciegas):** el render OSM nativo NO asigna `Paint`/`Path`/`Rect` por
    frame (usa Markers/Overlays cacheados + `nativeDrawableCache` LRU); `PlayerCharacter` usa `remember` +
    cachés de bitmaps y `Color(0x…)` es value-class (sin allocation). El grueso del perf de gama baja ya
    estaba afinado (caps de NPC por `popFactor`, LOD de emojis, fog por renderer, guards de reenvío web).
  - **Descartado (NO seguro sin compilador / cambiaría comportamiento):** convertir el JS de
    `WorldMapLeafletHtml` a *template literals* (`` `${x}` ``) — ese JS vive dentro de Kotlin `"""..."""` y
    `${...}` lo captura la interpolación de Kotlin → rompe. Cachear por referencia los
    `npcs.filter{...}.map{it.id}.toSet()` por-frame de NPCs que hablan/gritan/llaman: su predicado depende
    del tiempo (`nowB`), cambia cada frame, así que cachear por referencia de `uiState.npcs` DEJARÍA
    marcadores obsoletos (regresión). Guardar `updateTalkBubbles([])`/`updateRoads('[]')` cuando van vacíos:
    el array vacío es justo lo que LIMPIA los marcadores; saltarlo deja burbujas/calles fantasma. Mover
    `configureOsmdroid` (I/O de `mkdirs`/SharedPrefs en main thread) a background: cambia el orden de
    arranque (osmdroid debe configurarse antes del 1er render) → requiere app para verificar.

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
  `WorldMapDesigner.kt` (landmarks/diseñador), `WorldMapWanted.kt` (wanted/policía/carjack) y —2ª
  tanda, 2026-06-20— `WorldMapCombat.kt` (combate), `WorldMapCampaign.kt` (`setStorySpawn`),
  `WorldMapTeleport.kt` (teleport+metro) y `WorldMapShineCTO.kt` (easter egg+fade puerta) (ver §0). El
  ESTADO sigue en el VM (`providerPreloadJob`, `mapPrepStarted`, `escomNavGraph`, `carjackStartTime`…);
  los parciales solo tienen lógica. Los call-sites FUERA del paquete `viewmodel` (UI, MainActivity)
  necesitan **import explícito** de cada extensión. Además se ELIMINARON los duplicados miembro de
  `updateNpcsState` (gemelo idéntico en `WorldMapMultiplayer.kt`) y de `ensureIndex`/`candidates`/
  `getNearestPointOnNetwork`/`project` (el gemelo de `WorldMapRouting.kt` se sincronizó ANTES con el
  check de landmarks que solo tenía el miembro) — esas extensiones son ahora la única implementación.
  **✅ De-dup de gemelos COMPLETA (2026-06-21).** Los 8 pares quedaron resueltos (registro completo en
  `§12 (registro de de-dup, consolidado aquí)`). Resumen: **de-dup limpio** (sincronizar extensión al miembro y borrar el
  miembro) = `startHealthBarTimer`, `applyRoadNetwork`, `spawnOustedDriver`, `triggerWastedSequence`,
  `addRemoteEntity`, `maybeRefetchRoadNetwork`, `updateVisibleRoads`. **Fusionados** (se activó lógica
  buena que estaba muerta, con OK del dueño) = `handleMultiplayerMessage` (isRemote sync + daño en hilo
  Main + miedo al combate) y `startGameLoop` (audio del game loop). **NO TOCAR / revertido** =
  `updateDestinationRoute`+`calculateRouteOnNetwork` (cadena de routing interdependiente: de-duplicar la
  cabeza re-enlazaba toda la cadena y rompía la navegación → se dejó con miembro canónico + extensión
  muerta inofensiva). `WorldMapGameLoop.kt` es ahora un tombstone (startGameLoop = solo miembro con audio).
  **Proceso usado (por si hay que de-dup futuros gemelos):** (1) leer miembro + extensión, (2) DIFERENCIAR,
  (3) revisar TODA la cascada (qué llama y si esos callees son gemelos divergentes — fue lo que rompió
  el par de routing), (4) sincronizar/fusionar la extensión y borrar el miembro, (5) COMPILAR + PROBAR.
  Un par por ciclo; no agrupar sin compilar (no se bisecaría una regresión).
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
  pasaron de 26/55 → 18/38 → **10/22** (siguen escalando por `popFactor`) para una densidad más realista
  ("se generan muchos NPCs"). Además la **aparición es gradual**: `SPAWN_SCAN_MS`=900 ms (antes 500) y
  **máx 2 spawns por escaneo** (antes 4). Ajusta densidad por estos topes/factores, no hardcodeando otra cosa.
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
  "shock" visual brusco al robar/salir de un auto. **GOTCHA:** cualquier acción que fije `zoomLevel`
  a mano (`zoomIn/zoomOut/onMapZoomChanged/zoomToPlayer`) DEBE fijar TAMBIÉN `targetZoomLevel`, o el
  `updateAutoZoom()` del siguiente tick arrastra el zoom de vuelta y "rebota" (era el bug de "Hacer
  zoom en el jugador" tras un zoom out). No volver a clavar el zoom web a 19 (`setMapProvider`
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

- **Menú: "MUNDO LIBRE" vs "MODO HISTORIA" + spawn de campaña:** el 1er botón del menú
  (`menu_start_game` = **MUNDO LIBRE**) es el open world sin campaña (spawn por defecto, ESCOM).
  El 2º (`menu_load_game` = **MODO HISTORIA**) navega a `story_mode` (`StoryModeScreen`): prólogo +
  selector de escuela (`SchoolCatalog`, solo ESCOM `available=true`) + "CARGAR PARTIDA". "COMENZAR"
  pasa por la **intro** `story_intro/{schoolId}` (`StoryIntroScreen`, "Listo para Iniciar", placeholder);
  al **INICIAR**, `MainActivity` **guarda** la partida (`CampaignRepository.saveCampaign`) y arranca el
  mundo. "CARGAR PARTIDA" se habilita solo si `CampaignRepository.hasSave()` y reanuda en la escuela
  guardada (sin re-guardar). El **guardado lo escribe `MainActivity`** (punto de DI), no las Views.
  Para el spawn usa `WorldMapViewModel.setStorySpawn(lat, lon)` — **NO `updateInitialLocation`**: está
  gateada por `isLoadingLocation` (ya consumida en `onCreate`), así que no movería el spawn.
  `setStorySpawn` es miembro sin gemelo de extensión y re-arma `isMapReady`/`isRoadNetworkReady`/
  `npcsWarmedUp`. Para habilitar FES Aragón/UAM: pon `available=true` en `SchoolCatalog` (sus coords ya
  alimentan el spawn). `StoryModeViewModel` usa `Factory(context)` para leer la partida (NavBackStackEntry
  → re-lee al entrar). Las pantallas de campaña usan `windowInsetsPadding(WindowInsets.systemBars)`.
- **FIX "se queda cargando" al COMENZAR/CARGAR (Modo Historia):** `prepareMapForEntry()` es **idempotente**
  (gateada por `mapPrepStarted`, de la Activity, persiste entre navegaciones). Si ya entraste al mundo una vez
  (p. ej. MUNDO LIBRE), re-armar `isMapReady=false` en `setStorySpawn` NO volvía a descargar los tiles → la
  compuerta **nunca se soltaba**. Fix: `setStorySpawn` ahora se comporta como **teletransporte** (resetea
  `lastNetworkFetchLocation`/`lastFetchAttemptMs`/`npcWarmupCycles`, pone `inCampaign=true` y llama
  `gateMapDownloadAfterTeleport()`, que SÍ re-descarga y pone `isMapReady=true` sin depender de
  `mapPrepStarted`). No volver a confiar solo en `prepareMapForEntry` para el spawn de campaña.
- **Guardado de partida (JSON) = `SaveGameRepository`, NO `CampaignRepository`:** `CampaignRepository`
  (prefs) solo guarda ESCUELA + fecha (habilita "CARGAR PARTIDA"). El **estado completo** (posición, vida,
  nivel de búsqueda, vehículo, skin, NPCs cercanos) va a un **JSON** (`filesDir/pow_campaign_save.json`) vía
  `SaveGameRepository` + las extensiones `WorldMapSaveGame.kt` (`saveGame`/`loadGame`/`buildSaveData`/
  `restoreSaveData`, **sin gemelo miembro**). Guardado **MANUAL** (ítem "Guardar partida" del menú Opciones) y
  **AUTO al salir/cerrar** (solo si `WorldMapViewModel.inCampaign`; MUNDO LIBRE lo pone en false y NO guarda).
  `MainActivity` es el punto de DI (fija `campaignSchoolId`/`inCampaign` y dispara el auto-guardado).
- **Editor del Debug Interiores (líneas rojas/verdes/naranjas):** con `showInteriorDebugOverlay` activo,
  `InteriorDebugEditorPanel` (barra horizontal abajo; los controles de movimiento se **ocultan** al editar) +
  `WorldMapDebugEditor.kt` (extensiones, sin gemelo miembro) EDITAN la geometría **DIBUJANDO con el dedo**
  (estilo Paint): `DebugEditTool` WALL/BLOCK/NAV_PED/NAV_CAR; **arrastre = línea o rectángulo**; deshacer/
  limpiar/exportar/importar JSON. **El dibujo es una CAPA COMPOSE por encima del mapa (`InteriorDebugDrawSurface`
  en `WorldMapScreen`), NO código de un renderer concreto** — antes estaba en `NativeOsmMap` (osmdroid) y por eso
  **no funcionaba con el proveedor por defecto, que es WEB (CARTO/Leaflet)**: el mapa se movía en vez de dibujar.
  La capa Compose va sobre el renderer (web/OSM/Google) y bajo los botones/panel; con `tool != NONE` consume el
  gesto **desde el ACTION_DOWN** (`awaitEachGesture` + `consume()`) para que el mapa NO panee; con NONE deja pasar.
  Convierte pantalla↔coordenadas con **Web Mercator** (`256·densidad·2^zoom`) **asumiendo centro = jugador**, así
  que en modo debug el mapa **se mantiene centrado en el jugador** (`!isUserPanningMap || showInteriorDebugOverlay`).
  Commitea con `commitDebugStroke`. La geometría vive en `WorldMapState.debugEdit*`. (OSM nativo conserva además su
  overlay propio de geometría editada en tags +700/+710, redundante con la capa Compose pero inofensivo.)
- **Interiores expandible por campus (NO hardcodear el lobby de ESCOM):** el motor de Interiores
  (`interiores.zombies`) sirve para cualquier campus. Salas nuevas vía `ZombieRoomCatalog.campusRooms(...)`
  (`addAll` por campus; ESCOM = anillo bespoke, no lo toques). La sala inicial la elige la ruta
  **`interiores_zombies?startRoom={id}`** → `ZombieGameViewModel.startRoomId`. El VM debe ser
  **campus-agnóstico**: usa `lobbyForBuilding(buildingId)` (no `roomById(LOBBY_ID)`) para spawn de retorno,
  respawn de WASTED y el diálogo "volver al lobby" (`pendingLobbyTarget`). **Cliente y servidor de
  Interiores deben coincidir** en ids/tipos/matrices (`ROOMS` en `MultiplayerInteriores/server.js`:
  LOBBY = relay sin zombis, BUILDING = `ensureRoomState` siembra). La **mano/activación de zombis** del
  lobby sigue gateada por `LOBBY_ID` (sólo ESCOM): offline los edificios sólo siembran zombis con el modo
  activado; FES tiene horda **online** (el server siembra en BUILDING). La transición open-world↔interior
  **no** toca `Multiplayer/server.js`: el `WorldMapViewModel` (Activity-scoped) + el back stack preservan
  conexión y coordenadas.
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
  - **Migración por feature (fases) — actualizado 2026-06-21:**
    - **YA migrados:** `main_menu`, `settings`; **`map_exterior` (player-facing):** misión fallida
      (`wm_mission_failed/_retry_mission/_exit_to_menu`), tip carjack, guardar partida (`wm_opt_save_game`),
      widget de objetivo (`wm_objective_label/_done`, `wm_dist_km/_m`), descripción completa de Prankedy
      (`wm_prankedy_desc_full`); **paneles Modo Desarrollador** Debug Interiores (`dbg_*`) y diseñador de
      landmarks (ANCHO/ALTO → `wm_designer_width/_height`); **`campaign`** (`StoryModeScreen` slot
      `story_choose_slot`; `StoryIntroScreen`: `story_intro_back/_skip/_start`, hints `story_tap_*`, editor
      de cómic `story_ed_*`); **`interiores` (player-facing):** ZombieHud (`zhud_inv_empty/_has_key`,
      `cd_key`), ZombieGameScreen (`zgame_key_prompt`, `zgame_objective_investigate`, guardar reusa
      `wm_opt_save_game`), InteriorScreenBase (`cd_exit`), InteriorPlayerViews (`cd_player`,
      `cd_player_remote`); **`MainActivity`** diálogos guardar/cargar (`save_dialog_load/_new_slot`,
      `save_toast_saved`). Paridad ES+EN verificada (Read, no `grep` de bash que TRUNCA `strings.xml`).
    - **✅ TAMBIÉN YA migrados (2026-06-21):** transit player-facing (`MetrobusMapOverlay` selección de
      destino — reusa claves del Metro ya migrado + nuevas `cd_metrobus_map`/`int_metrobus_select_dest`/
      `int_save_waypoints`/`int_metrobus_select_station`; `MetrobusStationInteriorScreen` header
      `int_metrobus_header`, `int_press_x_door`, `cd_exit`) y los **diseñadores de matrices**
      (metro/metrobús/zombi): MATRIZ/WAYPOINTS/PARED/BORRAR/GUARDAR + ANCHO/ALTO/COL/FIL + hints →
      claves compartidas `int_matrix`/`int_waypoints`/`int_wall`/`int_erase`/`int_save`/`int_w_minus`/
      `int_w_plus`/`int_h_minus`/`int_h_plus`/`int_col_minus`/`int_col_plus`/`int_row_minus`/`int_row_plus`/
      `int_grid_paint`/`int_designer`/`int_move_handle` (+ reuso de `int_drag_door`/`int_touch_door`/
      `int_size_grid`/`int_waypoint_size`/`int_save_unsaved`/`ig_reset`/`ig_export`/`ig_import`/`ig_exit`).
      ZombieHud (`zhud_inventory`, `cd_zombie`). **i18n player-facing = COMPLETA.**
    - **PENDIENTE menor:** `CampaignObjective.title/description` siguen como `String` en el modelo
      (i18n total = pasarlos a `@StringRes` + resolver en la View; esfuerzo medio, requiere compilar).
    - **NO migrar:** rutas de assets, tipos de mensaje de red (`"PLAYER_UPDATE"`…), tags de log, URLs,
      claves de caché de sprites, unidades (`km/h`, `HP`), comparaciones por dato (`"Objeto Misterioso ESCOM"`),
      ni `displayName` propios (skins `Lázaro`/`Robot Estudiantx`, `Shine CTO`, `PRANKEDY`). `CampaignObjective.
      title/description` siguen como `String` en el modelo (i18n completo = pasarlos a `@StringRes`, cambio de
      medio esfuerzo que requiere compilar).
  - **Claves:** `snake_case`, prefijadas por feature (`menu_*`, `settings_*`). Toda clave nueva va en
    `values/` **y** `values-en/` (una contradicción/ausencia = bug; mantén la paridad).
- **🆕 Control por defecto = `JOYSTICK`:** lo fija `SettingsRepository.getControlType()` (default JOYSTICK,
  antes DPAD). Como TODOS los VMs leen el tipo de ahí al iniciar (`WorldMapViewModel`, `ZombieGameViewModel`,
  `InteriorViewModel`, `MetroInteriorViewModel`, `ShineCTOViewModel`), basta ese cambio; los defaults de los
  `*State` (`WorldMapState`, `SettingsState.controlType`/`tempControlType`) se pusieron en JOYSTICK por
  coherencia del primer frame. DPAD sigue eligible en Ajustes → Controles (no se persiste hasta GUARDAR).
- **🆕 Controles a la MISMA ALTURA (global = interiores):** la fila de controles del mapa global
  (`WorldMapScreen`) se igualó a la de interiores (`ZombieHud`): `sidePadding 8/32`, `bottomPadding 32/20`,
  `maxScale 0.95/1.3` (portrait/landscape) **+ `.systemBarsPadding()`** en el `Row`. Si retocas una, ajusta
  la otra para que no se desincronicen.
- **🆕 Orientación = SOLO por RUTA (las pantallas NO la fijan):** la orientación la decide ÚNICAMENTE
  `MainActivity` por destino de navegación (in-game = `SENSOR_LANDSCAPE`; menús de ruta `main_menu`/
  `story_mode`/`settings`/`collectibles` = `UNSPECIFIED`). **El menú de Opciones in-game NO cambia la
  orientación.** (Se probó un `LaunchedEffect(optionsExpanded)` que rotaba al abrir Opciones, pero al usuario
  le resultó molesto y se REVIRTIÓ: rotar = solo en una RUTA de menú, p. ej. Ajustes.) No re-añadir overrides
  de orientación a nivel de pantalla. Ver 05.
  - **🆕 Excepción `fromGame` en Ajustes:** Ajustes se abre desde el menú (vertical OK) y desde el JUEGO
    (debe seguir horizontal). Se resuelve SIN tocar las pantallas: la ruta es `settings?fromGame={fromGame}`
    (BoolType, default false). El menú principal navega a `settings` (fromGame=false → `UNSPECIFIED`); el juego
    navega a `settings?fromGame=true` (→ `SENSOR_LANDSCAPE`). El `OnDestinationChangedListener` lee
    `arguments?.getBoolean("fromGame")` del Bundle del destino y, si es true, NO trata Ajustes como menú
    vertical. Sigue siendo "orientación por RUTA" (el arg es parte de la ruta). ⚠️ Por el `?fromGame=...`, los
    chequeos de ruta exacta deben usar `route?.startsWith("settings")` (ya ajustado en `onNavigateBack`).
- **🆕 NPCs de IA = `remoteEntities` es la FUENTE DE VERDAD (no `serverNpcs`):** en el game loop, cada
  ~3 ticks `setServerNpcs(remoteEntities.filter{displayName vacío})` **CLEAR+refill** la lista del motor
  desde `remoteEntities`; tras simular, el host vuelca `getServerNpcs()` de vuelta a `remoteEntities` y
  `updateNpcsState()` los pinta. Por eso, para INYECTAR NPCs persistentes hay que meterlos en
  `remoteEntities` (con `displayName` vacío); meterlos solo en `serverNpcs` se borra al siguiente re-sync.
  `NpcAiManager.addServerNpcs(list)` existe para sembrarlos sin esperar un tick, pero la persistencia vive
  en `remoteEntities`. Single-player: `isServerDelegatedHost=true` (default) → la simulación corre.
- **🆕 60 NPCs que CAMINAN por la ruta roja de campaña (`WorldMapCampaignRouteNpcs.kt`):** desde
  `campaignRouteWaypoints` se arma un `MapWay` virtual (ids negativos para no chocar con OSM,
  `isForPeople=true`) y 60 `Npc` con `currentWay`=esa ruta. `moveNpc` los lleva nodo a nodo; al no haber
  vías conectadas en los extremos (ids negativos no están en `nodeToWays`), **invierten la dirección** →
  van y vienen. Llevan id con prefijo `NpcAiManager.ROUTE_NPC_PREFIX` (`"CAMPAIGN_ROUTE_"`) que los deja
  **EXENTOS del despawn por distancia y del cull por `maxTotalNpcs`** (si no, se borraban lejos del
  jugador); fuera del `simRadius` se ponen `isMoving=false` (no “caminan en el sitio”) y si `moveNpc`
  devuelve null NO se despawnean (se quedan quietos). Disparo: automático en `maybeSpawnPrankedyCompanion`
  (escolta) y manual con el botón del panel Debug Interiores (`toggleCampaignRouteNpcsDebug`). Se limpian
  en `maybeHideCampaignRouteNearEscom` y en `clearCampaignPolice`.
- **🆕 REMATE Misión 2: la policía se reúne donde Prankedy SE METIÓ:** al entrar Prankedy a la ESCOM se
  guarda su posición exacta en `mission2PrankedyExitPoint`; `runMission2Tick` pasa ESE punto a
  `startResolution` (antes pasaba la puerta del objetivo, unos metros más allá). Se resetea en
  `startMission2`/`clearCampaignPolice`.
- **🆕 Panel Debug Interiores movible + Salir (`InteriorDebugEditorPanel`):** el editor de líneas de
  colisión del mapa global ahora es movible/redimensionable/scroll (mismo patrón que el panel del
  diseñador de matrices: asa con `detectDragGestures`, `graphicsLayer` scale −/+, `heightIn(max=90%)` +
  `verticalScroll`) y tiene botón **"Salir"** (`onExit` → `setDebugEditTool(NONE)` +
  `toggleInteriorDebugOverlay(false)`). Aloja además el botón de debug de los NPCs de ruta.
- **🆕 AUTENTICACIÓN (Firebase Auth + Google Sign-In):** el **multijugador** (y, a futuro, los logros)
  exige iniciar sesión; el **juego local y el Modo Historia NO**. Piezas:
  `data/auth/AuthManager.kt` (login Google→Firebase, token, signOut, **deleteAccount**) y
  `data/auth/AuthSession.kt` (singleton con `uid`/`idToken`; lo lee `WebSocketManager` para mandar la
  cabecera `Authorization: Bearer <token>` en el handshake → ambos servidores la verifican). El **gate**
  vive en `MainMenuScreen` (botón MULTIJUGADOR: si no hay sesión, abre el selector de Google y al volver
  continúa el flujo) y en **Ajustes → Cuenta** (`SettingsCategory.Account` + `AccountSettings`):
  iniciar/cerrar sesión y **"Eliminar mi cuenta y datos"** (borra la cuenta en Firebase + datos locales
  vía `onAccountDeleted` en `MainActivity`: limpia slots de `SaveGameRepository` + `CampaignRepository`).
  `AuthManager` es **DEFENSIVO**: sin `google-services.json` no crashea (devuelve null/false), pero el
  build de Gradle SÍ requiere el json (plugin `com.google.gms.google-services` aplicado). El UID de
  Firebase reemplaza al UUID de dispositivo como id de jugador (`myPlayerUUID`). **Gotcha:** el web client
  id se lee DINÁMICO (`getIdentifier("default_web_client_id")`) para que el código compile aunque el
  recurso aún no exista. Ver 08 (verificación en servidores).
  - **Extras de UX/robustez:** `MainActivity` llama `authManager.refreshToken{}` ANTES de
    `connectToMultiplayer` (el ID token caduca ~1 h). El menú muestra un **chip de sesión**
    ("Conectado: …" / "Modo local"). El **nombre de jugador** se recuerda en
    `SettingsRepository.get/savePlayerName` (se prellena; si hay sesión y está vacío, usa el nombre de
    Google). **`PowApplication`** (registrada en el Manifest) inicializa Firebase temprano y a prueba de
    fallos. Cancelar el selector de Google (códigos 12501/16) NO muestra error. El enlace a la política de
    privacidad (`R.string.settings_privacy_url`) vive en Ajustes → Cuenta. Plantillas de entorno de los
    servidores en `Multiplayer/.env.example` y `MultiplayerInteriores/.env.example`.
  - **⚠️ Secretos / no commitear:** `app/google-services.json`, `*.jks` (llave de firma), el JSON del
    service account de firebase-admin y `secrets.properties` están en `.gitignore` (verificado). El service
    account NO va al repo: vive como variable `FIREBASE_SERVICE_ACCOUNT` en Render. Ningún secreto debe
    entrar en código fuente ni en estos docs.
- **🆕 Modo Desarrollador (`developerMode`, Ajustes → Interfaz, default oculto):** switch persistente con el
  mismo patrón que los widgets (`SettingsRepository.get/saveDeveloperMode`, `SettingsState.developerMode`,
  `SettingsViewModel.toggleDeveloperMode`, wired en `MainActivity.onDeveloperModeToggled`). Revela botones de
  prueba que con el modo APAGADO quedan ocultos para el jugador. **Cableado (cada pantalla lee
  `SettingsRepository(context).getDeveloperMode()` UNA vez al entrar):**
  - `StoryIntroScreen`: oculta el botón **"Editar"** (editor del cuadro de texto del cómic).
  - `ZombieGameScreen` (interiores, menú Opciones): oculta **"Diseñador"**; y **"Salir al mapa"** solo cuando
    la sala está en la cadena `ZombieRoomCatalog.ENCB_STORY_ROOM_IDS` (Misión 1) → `developerMode || !inMission1`.
  - `WorldMapScreen` (mundo, menú Opciones): oculta **"Teletransportarse"**, el grupo **"Diseñador / Debug"**
    (Modo Diseñador + Debug Interiores + Agregar asset), **"Activar/Desactivar Apocalipsis"** y el toggle de
    **Prankedy**.
  Como se lee con `remember` al entrar, el cambio aplica al re-entrar a la pantalla (no en vivo). Strings
  `settings_developer_mode`/`_desc` (es+en).
- **🆕 Widget de coordenadas X/Y/Z (`showCoordsWidget`, Ajustes → Interfaz, default oculto):** composable
  reusable `CoordsWidget(x,y,z)` en `GameControllers.kt`. Z = "dónde": **GLOBAL** en el mundo abierto
  (`WorldMapScreen`, X=lon, Y=lat), o el **nombre de la sala/interior** en interiores (`ZombieHud` con
  `roomName`, `InteriorScreenBase` con `title`; X/Y = posición del jugador). Toggle con el mismo patrón que
  zoom/velocímetro: `SettingsRepository.get/saveShowCoordsWidget`, campo en `SettingsState`/`WorldMapState`/
  `ZombieGameState`/`InteriorState`, push en vivo al mapa desde `MainActivity`. Métro/Métrobus/ShineCTO aún
  no lo muestran (mismo patrón si se desea: añadir `showCoordsWidget` a su `*State` + `CoordsWidget` al HUD).
  **`CoordsWidget` es un CHIP DE UNA SOLA LÍNEA** con el MISMO estilo/tamaño que `CacheChip` (fondo
  `Black α0.72`, `RoundedCornerShape(20.dp)`, `padding(h10,v5)`, punto 8.dp, texto 11sp Medium): así TODOS
  los widgets de Interfaz quedan uniformes en altura (antes era un bloque de 3 líneas y se veía más alto).
- **🆕 Volumen separado música/efectos (Ajustes → Audio):** persistido en `SettingsRepository`
  (`get/saveMusicVolume`/`SfxVolume`, default 1.0), `SettingsState.musicVolume`/`sfxVolume`,
  `SettingsViewModel.changeMusicVolume`/`changeSfxVolume`. **`SoundManager` es la autoridad de audio:**
  `setMusicVolume` (→ `MediaPlayer.setVolume` en las 4 pistas) y `setSfxVolume` (multiplica cada `play()`
  del `SoundPool` por `sfxVolume` y reajusta los streams en loop con `setVolume`). `SoundManager.init` LEE
  el volumen del repo y lo aplica al arrancar; `MainActivity` lo empuja en vivo al cambiar el slider. Si
  añades un nuevo `play()`, multiplica su volumen por `sfxVolume` (si no, ese efecto ignora el slider).
- **🆕 TP entre salas = puerta↔puerta (`ZombieGameViewModel.goToRoom`):** al cambiar de sala se spawnea
  JUNTO a la puerta del cuarto DESTINO cuyo `targetRoomId == fromRoom.id` (no en el centro). Cubre la cadena
  ENCB (Continuar↔Regresar) y los vecinos de edificios. **⚠️ El spawn se DESPLAZA ~30% hacia el centro
  (`k=0.30f`), NO sobre el hitbox de la puerta:** `onInteract` dispara la puerta cuyo hitbox CONTIENE al
  jugador, así que spawnear encima hacía que la siguiente X re-disparara esa puerta y te REGRESARA (rebote
  lobby↔salón → "Continuar" no avanzaba). No quitar el desplazamiento. EXCEPCIÓN: "lobby → edificio" mantiene
  spawn central + siembra de zombis; "edificio → lobby" sigue con `spawnAtLobbyDoorFor`. Ver 06.
- **🆕 Menús de pantalla completa vs barra del sistema:** las pantallas de menú (p. ej. `CollectiblesScreen`)
  deben usar `systemBarsPadding()` para que sus botones (p. ej. "VOLVER AL MENÚ") no queden tapados por la
  barra de gestos/navegación del teléfono. (Las de campaña ya usaban `windowInsetsPadding`.)
- **🆕 Nombres de escuela de campaña = institución:** `SchoolCatalog` muestra `IPN` (`id="escom"`) y `UNAM`
  (`id="fes_aragon"`); los `id` NO cambian (alimentan spawn/guardado). Botón `story_start = "NUEVA PARTIDA"`.
- **🆕 Morir en una MISIÓN de campaña = MISIÓN FALLIDA (edita el MIEMBRO, no la extensión):**
  `triggerWastedSequence` existe como **miembro privado** en `WorldMapViewModel.kt` (ACTIVO) **y** como
  extensión en `WorldMapMisc.kt` (sombreada/muerta). La lógica "si `inCampaign` && objetivo
  `ESCOLTAR_PRANKEDY`/`INGRESAR_ESCOM` → WASTED breve y `showMissionFailed=true` (REINTENTAR recarga el
  checkpoint)" vivía SOLO en la extensión → no corría: morir respawneaba normal frente a ESCOM y el jugador
  podía dejarse matar para saltarse la escolta. Fix: la rama de misión fallida está ahora en el **miembro**.
  Clásico gotcap miembro vs extensión (ver arriba): **edita el miembro**.
- **🆕 Ruta GPS de campaña = VERDE VIVO (no roja):** la línea ENCB→ESCOM (`campaignRouteWaypoints`) se pintaba
  ROJA y se confundía con la ruta de destino (azul) y las líneas del debug (rojo/naranja). Ahora es **verde
  vivo `#00E676`, gruesa** (`NativeOsmMap` `strokeWidth=16f`; Leaflet `weight 9`). Es "la ruta a seguir".
- **🆕 Resize de la MATRIZ del diseñador (interiores) = ANCLADO:** en `DesignerToolbar` (`ZombieGameScreen`)
  el bloque de tamaño (texto + `COL ±`/`FIL ±`) se sacó del scroll del medio y va **anclado abajo** (solo en
  modo MATRIZ): en pantallas bajas quedaba al final del scroll y se recortaba → "desapareció el resize".
- **🆕 Coords FIJAS de la campaña (constantes en `MissionCatalog`):** el Modo Historia usa puntos fijos en vez
  de relativos al jugador (X=lon, Y=lat). `MISSION1_SPAWN` (19.50102, -99.14421) = entrada al mapa global tras
  el outro = CHECKPOINT de la escolta (MainActivity lo usa en `setStorySpawn`). `ESCOM_FORCEWALK` (19.50500,
  -99.14596, radio 50 m). Los policías de la Misión 2 salen de `MISSION2_POLICE_SPAWN` (19.50488, -99.14569,
  en `WorldMapCampaignPolice`) y la multitud civil de `CROWD_SPAWN` (19.50512, -99.14625). Para reubicar algo,
  cambia la constante (no hardcodees en otra parte).
- **🆕 Reintento de misión = CHECKPOINT, no la posición guardada:** `retryCampaignMission` para la escolta
  (`ESCOLTAR_PRANKEDY`) hace `setStorySpawn(MISSION1_SPAWN)` (no `loadGame`, que restauraba el START en ESCOM y
  por eso "te teleportaba a ESCOM"). Captura el objetivo ANTES de cualquier `loadGame`.
- **🆕 Coche obligado a pie cerca de ESCOM (Misión):** en la física del coche (game loop MIEMBRO), a <=50 m de
  `ESCOM_FORCEWALK` y con objetivo escolta/ingreso, `forceWalkNearEscom` BLOQUEA el avance (gas) y anula la
  velocidad positiva → SOLO reversa, para forzar bajarse y entrar a pie. No quitar el gate del objetivo (si no,
  bloquearía el coche en mundo libre).
- **🆕 Prankedy se SUBE al coche contigo (`prankedyBoarding`):** al abordar un coche con Prankedy ACOMPAÑANTE
  (HIRED), `onInteractButtonPressed` pone `prankedyBoarding=true`; la física del coche NO avanza (speed=0) y
  `runPrankedyTick` pasa `isDriving=false` al `tick` (Prankedy corre a pie hasta ti). Al llegar a <=5 m
  (`PRANKEDY_BOARD_DIST_M`) o si murió, se limpia el flag (se "sube" → se oculta) y el coche ya avanza. El flag
  se limpia también al bajarte. `runPrankedyTick` corre cada tick AUNQUE conduzcas (no gateado por isDriving),
  por eso el abordaje se completa.
- **🆕 Joystick en MODO MANEJO:** `VehicleJoystickController` (dirige izq/der por el eje X, press/release). En
  `WorldMapScreen` la rama de conducción usa joystick si `controlType==JOYSTICK`, si no las flechitas
  (`VehicleDPadController`). Gas/freno siguen en el diamante PS4.
- **🆕 Multitud civil de ESCOM (Misión 2) = 50+ desde punto fijo:** `updateEscomCrowd` ahora spawnea desde
  `CROWD_SPAWN` (no la puerta), `CROWD_MAX=55`, intervalo 150 ms; se alejan, se despawnean al salir del fog y se
  reemplazan por nuevos. (Ojo gama baja: son NPCs PERSON; si pesa, baja `CROWD_MAX`.) **🆕 La multitud camina
  HACIA `MISSION2_POLICE_SPAWN`** (~80%, por `id.hashCode()%5`) → multitud y policías van en direcciones OPUESTAS.
- **🆕 Misión 2 (INGRESAR_ESCOM) se cumple con el PROMPT de la puerta:** `checkObjectiveProgress` marca el
  objetivo cumplido EN CUANTO `nearbyCollectible` es un `escom_door_*` (prompt "Presiona X para entrar a la
  ESCOM", ~20 m), sin tener que pegarse ni pulsar X. El X sigue ENTRANDO al interior.
- **🆕 Prankedy entra a la ESCOM LENTO y visible (Misión 2):** `runMission2PrankedyEscape` ahora camina
  (`playerRunning=false`), SIN snap a calles (beeline a la puerta, que está fuera de la vía → ya no "nunca
  llega"), umbral de entrada `MISSION2_PRANKEDY_ENTER_DEG=0.00006` (~6.6 m, casi pegado) y pausa 2.4 s.
- **🆕 Movimiento LIBRE del jugador sobre assets/landmarks (`isOnLandmark`):** `moveCharacter`/
  `moveCharacterByAngle` suspenden el snap a calles si el jugador está SOBRE el footprint de un landmark
  (caja `baseW/H × escala`). **Es SOLO para el jugador**: `isOnLandmark` NO se mete en `isFreeMovementZone`, así
  las calles SIGUEN dibujándose y los NPCs SIGUEN atados a la malla vial. (En zonas ESCOM/ENCB ya era libre.)
- **🆕 NavGraph sin `isForCars`/`isForPeople` → `normalizeNavGraph`:** Gson NO aplica los defaults de Kotlin a
  campos AUSENTES del JSON. `escom_navgraph.json` no trae `isForCars`/`isForPeople` en las `ways` → llegaban
  `false` y los autos del estacionamiento NO casaban con ningún carril (`matchType` en `NpcAiManager`) →
  "no surten efecto". `normalizeNavGraph` (se aplica al cargar en `loadLandmarks` y `spawnDynamicCarInEscom`)
  re-clasifica por convención de id (id<200=autos, id>=200=peatonal) las ways sin clasificar. Mismo gotcha que
  el coalesce de `scaleX/scaleY` en `loadLandmarks`. (Los nodos de slot usan `isParkingSlot`.)

---

## 13. PROTOCOLO DE ACTUALIZACIÓN DE DOCS / DOC UPDATE PROTOCOL (obligatorio / mandatory)

**ES:** Esta carpeta (`00`–`09` + docs de trabajo) es la **única fuente de verdad** que se le pasa a un
asistente en vez de todo el código (el README **público** de la raíz es la visión general para humanos).
Solo sirve si se actualiza **en el mismo cambio** que toca el código. Trátala como parte del entregable.

**EN:** This folder (`00`–`09` + working docs) is the **single source of truth** handed to an assistant
instead of the whole codebase (the **public** root README is the human-facing overview). It only works if
updated **in the same change** that touches the code. Treat it as part of the deliverable.

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
