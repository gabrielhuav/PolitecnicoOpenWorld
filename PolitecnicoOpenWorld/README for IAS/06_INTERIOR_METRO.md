# 06 · Interiores + Metro / Interiors + Metro (`features/interiores/escom/`)

> **🆕 Reestructura:** el antiguo `features/interior/` ahora es **`features/interiores/escom/`** (subpaquete
> de la umbrella `interiores`, pensado para crecer a más universidades además de ESCOM). El metro importa
> los tipos compartidos `DesignerTarget`/`CameraTransform` y los designer layers desde **`interiores.core`**
> (antes los tomaba de `zombie_minigame`). / Was `features/interior/`; now `features/interiores/escom/`;
> shared designer types/layers come from `interiores.core`.

**ES:** Interiores 2D simples (sin zombis): 7 edificios de ESCOM + 2 deportivos + estaciones de metro.
Cada uno es una pantalla Compose sobre un fondo, con movimiento validado por una `CollisionGrid`.
**EN:** Simple 2D interiors (no zombies): 7 ESCOM buildings + 2 sports venues + metro stations. Each is
a Compose screen over a background, with movement validated by a `CollisionGrid`.

---

## CollisionGrid — `viewmodel/CollisionGrid.kt`

```kotlin
class CollisionGrid(val grid: Array<IntArray>) {       // 0=libre, !=0=bloqueado
  fun isWalkable(normalizedX: Float, normalizedY: Float): Boolean
  fun cellValueAt(normalizedX: Float, normalizedY: Float): Int
  companion object { fun emptyWithBorder(): CollisionGrid }   // por defecto: solo borde
}
```
**ES:** Los interiores usan `emptyWithBorder()` por defecto (matrices de colisión ricas = pendiente).
**EN:** Interiors use `emptyWithBorder()` by default (content-rich collision matrices = not yet implemented).

---

## Interiores genéricos / Generic interiors

- **`ui/InteriorScreenBase.kt`** — pantalla base reutilizable. Las pantallas concretas (`AuditorioScreen`,
  `BibliotecaScreen`, `CafeteriaScreen`, `EdificioScreen`, `EstacionamientoScreen`, `PalapasScreen`,
  `CanchasFutbolScreen`, `DeportivoBeisScreen`, `DeportivoFutbolScreen`, **`FesInteriorScreen`**) son
  envoltorios finos que pasan el `backgroundAsset` / config y delegan en la base. El **botón de salida**
  de la base llama `onExit` → `popBackStack("world_map")` (vuelves al mapa global en la misma posición).
- **🆕 Modo Historia ENCB (cadena lineal)** — NO usa este motor simple, sino el **motor de salas**
  (`ZombieGameScreen`, ver 05): 4 salas tipo `LOBBY` encadenadas `encb_lobby → encb_salon1 → encb_lab1 →
  encb_lab2` (`ZombieRoomCatalog.encbStoryRoom(...)`, fondos `INTERIORS/ENCB/*.webp`, sin zombis/mano). Cada
  sala tiene UNA puerta de AVANCE (X → `goToRoom(next)`); ninguna tiene salida al mapa (flujo atrapado). Banner
  "Objetivo: Investiga qué pasó" en todas (`ENCB_STORY_ROOM_IDS`). Entrada: `interiores_zombies?startRoom=encb_lobby`.
- **`viewmodel/InteriorViewModel.kt`** (NavBackStackEntry-scoped, `Factory`):
  - Estado: `InteriorState` (posición del jugador, facing, running, etc.).
  - `moveByAngle(angleRad)`, `moveDirection(direction)`, `applyMovement(newX, newY, dxForFacing)`,
    `updatePlayer(x, y, dxForFacing)`, `setRunning(running)`, `setInitialPosition(x, y)`.
  - Movimiento normalizado [0,1], validado contra la `CollisionGrid` (`isWalkable`).

**Entrada / entry (genérica, NO solo ESCOM):** cualquier landmark cuyo `assetPath` contenga `DOORS/` se
vuelve una puerta interactuable. `checkCollectibleProximity` (MIEMBRO del VM) lo detecta como
`nearbyCollectible` con `id="escom_door_<landmarkId>"` dentro de `ESCOM_DOOR_INTERACT_RADIUS` (~20 m) y
muestra "PRESIONA X PARA ENTRAR". Al pulsar **X**, `handleInteraction` (MIEMBRO) enruta por el **nombre**
del landmark (substring, tolerante a acentos/espacios): `…Béisbol→interior_deportivo_beis`,
`…Fútbol→interior_deportivo_futbol`, `…FES→interior_fes`, y el resto (puertas ESCOM) → `interiores_zombies`.
Luego: fade (`showEscomDoorFade`) → `onEscomDoorFadeComplete` → `MainActivity` lee `consumeEscomDoorNavigation()`
→ navega a la ruta. **FES Aragón** usa el **motor de INTERIORES** (`interiores_zombies`, mismos controles,
opciones, botones de acción y HUD ZONA/vida/MODO) pero arranca en **SU PROPIA sala**: la puerta
**"Entrada FES Aragón"** (`WorldMapDesigner` la backfillea/reubica junto al TP de FES si falta o está lejos;
assetPath `DOORS/ESCOM_DOOR.webp`) → **`interiores_zombies?startRoom=fes_interior`** → **campus FES**:
lobby `ZombieRoomCatalog.FES_ID` (fondo `FES_Arg_int.webp`, zona segura, salida al mapa) **con una puerta a
su edificio `fes_edificio`** ("Edificio Principal", copia temporal del de ESCOM, con zombis online). El
campus se genera con el helper expandible `campusRooms(...)`. Ver 05 (Interiores expandible) y 08 (server).
La pantalla simple `FesInteriorScreen`
(ruta `interior_fes`, `InteriorScreenBase`) **existe pero la puerta YA NO la usa**: queda reservada.
Cada `InteriorBuilding` (incl. `FES_INTERIOR`) define su `routeName`, `location` y `backgroundAsset` (ver 03).

---

## Metro / Metrobús (TRANSPORTE UNIFICADO)

Sistema más rico que los interiores genéricos: mapa de la red, hotspots y modo diseñador con waypoints
globales. / Richer than generic interiors: network map, hotspots, designer mode with global waypoints.

> **🆕 ORIENTACIÓN del Metrobús (2026-06-22):** los assets del interior del Metrobús son **VERTICALES**
> (`inside.png`/`bus1`/`bus2` = 1168×1347, `mapa.png` 2551×3402), a diferencia del metro (vehículos 2816×1536,
> horizontal). Por eso la ruta `metrobus_station_interior` corre en **PORTRAIT** (`SCREEN_ORIENTATION_SENSOR_PORTRAIT`
> en el listener de `MainActivity`); en horizontal el fondo se recortaba a una franja ("todo vertical"). El metro
> sigue en landscape. Si algún día se rehace el arte del metrobús en horizontal, quitar esa excepción.
>
> **🆕 UNIFICACIÓN Metro⇄Metrobús (2026-06-22):** la lógica/estado del interior, que antes estaba
> **DUPLICADA** en `MetroInteriorViewModel`(700)+`MetrobusInteriorViewModel`(655) y sus dos States, se
> fusionó en **`viewmodel/TransitInteriorViewModel.kt`** (un solo VM, toda la lógica una vez) +
> **`viewmodel/TransitInteriorState.kt`** (un solo State, nombres neutros: `isVehicle1Animating`,
> `showTransitMap`, `allStations`…; los alias de compat se RETIRARON 2026-06-23 — ver B), **dirigidos por
> `viewmodel/TransitSystemConfig.kt`** (catálogo `TransitSystems.METRO`/`.METROBUS`: assets, prefs, repo,
> spawn, offset de torniquete `turnstileBoardDeltaY`, strings, branding/eje de animación). Los modelos
> `MetroStation`/`MetrobusStation` implementan la interfaz común **`domain/models/map/TransitStation`**.
> **Añadir Suburbano/Mexibús = nueva entrada en `TransitSystems` + assets + ruta** (no se duplica lógica).
> Los 4 VMs/States viejos (tombstones) se BORRARON 2026-06-23 (B). Las dos pantallas/overlays SIGUEN separadas (su
> render difiere: metro=vídeo+animación vertical, metrobús=gradiente+horizontal) pero usan el VM/State/config
> unificados; parametrizar una sola pantalla/overlay por config es **🔮 TRABAJO FUTURO** (pospuesto 2026-06-23): se hará
> cuando el **Metrobús funcione bien** y se agreguen más sistemas (Suburbano/Mexibús…); hoy NO conviene colapsar alrededor
> de una implementación con bugs. Ver ANALISIS §2.2/§8 (item A).

- **`ui/MetroStationInteriorScreen.kt`** / **`ui/MetrobusStationInteriorScreen.kt`** — interior de una estación
  (ruta `{key}_station_interior/{stationName}?spawnX=&spawnY=`). Crean el VM con
  `TransitInteriorViewModel.Factory(context, TransitSystems.METRO|METROBUS, stationName, spawnX, spawnY)`.
- **`ui/MetroMapOverlay.kt`** / **`ui/MetrobusMapOverlay.kt`** — overlay del mapa de la red (búsqueda, navegación).
- **`viewmodel/TransitInteriorViewModel.kt`** + `TransitInteriorState.kt` + `TransitSystemConfig.kt`:

```kotlin
enum class TransitHotspot { TAQUILLA, TORNIQUETES, ANDEN, SALIR_TORNIQUETES, SALIDA }
data class TransitInteriorState( /* solo nombres neutros (alias de compat retirados) */ )
data class TransitSystemConfig( /* assets, prefs, repo, spawn, deltas, strings, branding */ )
object TransitSystems { val METRO; val METROBUS }
```

**API destacada / notable API:**
- Movimiento: `moveByAngle`, `moveDirection`, `applyMovement`, `updatePlayer`, `setRunning`.
- Hotspots: `checkHotspots(x, y)`, `interactWithHotspot()`, `closeTransitMap()`, `onVehicle1AnimationFinished()`, `consumeExitStation()`. (Los alias `closeMetroMap`/`onMetro1AnimationFinished`… se retiraron, B.)
- **Diseñador de matriz** (igual patrón que zombi): `toggleDesignerMode`, `setDesignerTarget`,
  `setDesignerBrushWall`, `paintCellAtWorld`, `saveDesignerMatrix/resetDesignerMatrix`,
  `resizeDesignerMatrixBy`, import/export Uri.
- **Diseñador de puertas:** `selectDoor`, `dragDoor`, `resizeDoor`.
- **Mapa global / global map designer:** `toggleMapDesignerMode`, `toggleMapDesignerMoveMode`,
  `updateMapSearchQuery`, `addGlobalWaypoint(x, y, stationName)`, `selectGlobalWaypointAt`,
  `moveSelectedGlobalWaypointTo/By`, `deleteSelectedGlobalWaypoint`, `saveGlobalWaypoints`,
  `export/importGlobalWaypointsToUri/FromUri`.
- `updateCollisionGrid(rows)`, `Factory(...)`.

**🆕 Skin del jugador (2026-06-22):** el sprite del interior respeta la SKIN ELEGIDA (hombre/mujer/robot…), no
Lázaro fijo. `TransitInteriorState.selectedSkin` se lee de `SettingsRepository.getPlayerSkin()` al crear el VM, y
`Metro/MetrobusPlayerSprite` usan `skin.idlePath/walkPath/runPath/specialPath(frame)` + sus frame counts. *(Nota:
estas pantallas dibujan con `fillMaxSize`, sin la normalización por `walkBodyFraction` que SÍ tiene el motor de
salas — si las skins se ven de distinto tamaño aquí, replicar ese ajuste; ver 09 §5.)*

**Datos / data:** las estaciones se cargan con `config.loadStations(context)` (apunta a
`MetroRepository`/`MetrobusRepository`, `res/raw/metro|metrobus`, ver 02). `MetroStation`/`MetrobusStation`
implementan `TransitStation(name, routes, location)`.

---

## Gotchas

- **ES:** Los interiores y el metro son **NavBackStackEntry-scoped**: el estado se reinicia al salir.
  El movimiento es normalizado [0,1]; convertir a píxeles solo en render. / Interiors/metro are
  NavBackStackEntry-scoped; state resets on leave. Movement is normalized [0,1]; convert to pixels only at render.
- **ES:** Las matrices/waypoints del metro se persisten vía los mismos JSON que el resto del diseñador
  (`collision_matrices.json`, `waypoints.json` y waypoints globales). / Metro matrices/waypoints persist
  via the same designer JSON files.
- **🆕 Widget de coordenadas (X/Y/Z):** `InteriorScreenBase` (interiores simples ESCOM) y `ZombieHud`
  (motor de interiores) muestran `CoordsWidget` si `state.showCoordsWidget` (leído del repo al entrar):
  X/Y = posición del jugador, **Z = nombre del interior/sala** (`title` / `roomName`). Toggle en
  Ajustes → Interfaz. Metro/Metrobús/ShineCTO aún no lo muestran. Ver 04/07/09.
- **🆕 TP puerta↔puerta entre salas (cadena ENCB y vecinos):** `ZombieGameViewModel.goToRoom` ya no
  spawnea en el centro del cuarto destino: busca en el cuarto DESTINO la puerta cuyo `targetRoomId` es el
  cuarto de ORIGEN y spawnea **junto a ella, DESPLAZADO ~30% hacia el centro** (no sobre el hitbox: si
  spawneas encima, la siguiente X dispara esa puerta y te REGRESA → rebote, "Continuar" no avanzaba). Así,
  "Continuar →" en el cuarto N te deja junto a la "← Regresar" del N+1 (y viceversa). Se EXCLUYE "lobby → edificio" (esa
  entrada conserva su spawn central + siembra de zombis). El caso "edificio → lobby" sigue usando
  `spawnAtLobbyDoorFor`. Ver 09.
