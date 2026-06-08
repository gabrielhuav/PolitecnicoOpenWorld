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
GPS es el 1er item del diálogo *Puntos de Teletransporte*.

## 9. Default provider = `OSM_WEB` (no persistido)

El nativo osmdroid (default z22 + reescalado z19) es el render más pesado en equipos débiles, así que el
default arranca en **OSM Web**. **No se persiste**: el default es solo el estado inicial en
`SettingsState`, `WorldMapState` y `MainMenuState` (todos `OSM_WEB`). **Google nativo** sigue al jugador
con `cameraPositionState.move()` (NO `animate()`). **Web** re-envía landmarks solo cuando cambia la lista
(guard por referencia + heartbeat ~45 frames).

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
- **Gotcha de parciales:** funciones duplicadas como miembro privado + extensión (`WorldMap*.kt`). El
  miembro de `WorldMapViewModel.kt` gana; verifica ambos (ver 04).

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
