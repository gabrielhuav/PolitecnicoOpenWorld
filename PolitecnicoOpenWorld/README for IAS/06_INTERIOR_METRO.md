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

## Metro

Sistema más rico que los interiores genéricos: mapa de la red, hotspots y modo diseñador con waypoints
globales. / Richer than generic interiors: network map, hotspots, designer mode with global waypoints.

- **`ui/MetroStationInteriorScreen.kt`** — interior de una estación (entrada por ruta parametrizada
  `metro_station_interior/{stationName}?spawnX=&spawnY=`).
- **`ui/MetroMapOverlay.kt`** — overlay del mapa de la red de metro (búsqueda, navegación entre estaciones).
- **`viewmodel/MetroInteriorViewModel.kt`** (~600 líneas) + `MetroInteriorState.kt`:

```kotlin
enum class MetroHotspot { ... }     // puntos interactivos dentro de la estación
data class MetroInteriorState( ... )
```

**API destacada / notable API:**
- Movimiento: `moveByAngle`, `moveDirection`, `applyMovement`, `updatePlayer`, `setRunning`.
- Hotspots: `checkHotspots(x, y)`, `interactWithHotspot()`, `closeMetroMap()`, `consumeExitStation()`.
- **Diseñador de matriz** (igual patrón que zombi): `toggleDesignerMode`, `setDesignerTarget`,
  `setDesignerBrushWall`, `paintCellAtWorld`, `saveDesignerMatrix/resetDesignerMatrix`,
  `resizeDesignerMatrixBy`, import/export Uri.
- **Diseñador de puertas:** `selectDoor`, `dragDoor`, `resizeDoor`.
- **Mapa global / global map designer:** `toggleMapDesignerMode`, `toggleMapDesignerMoveMode`,
  `updateMapSearchQuery`, `addGlobalWaypoint(x, y, stationName)`, `selectGlobalWaypointAt`,
  `moveSelectedGlobalWaypointTo/By`, `deleteSelectedGlobalWaypoint`, `saveGlobalWaypoints`,
  `export/importGlobalWaypointsToUri/FromUri`.
- `updateCollisionGrid(rows)`, `Factory(...)`.

**Datos / data:** las estaciones se cargan con `MetroRepository.loadStations(context)` desde
`res/raw/metro` (ver 02). `MetroStation(name, routes, location)`.

---

## Gotchas

- **ES:** Los interiores y el metro son **NavBackStackEntry-scoped**: el estado se reinicia al salir.
  El movimiento es normalizado [0,1]; convertir a píxeles solo en render. / Interiors/metro are
  NavBackStackEntry-scoped; state resets on leave. Movement is normalized [0,1]; convert to pixels only at render.
- **ES:** Las matrices/waypoints del metro se persisten vía los mismos JSON que el resto del diseñador
  (`collision_matrices.json`, `waypoints.json` y waypoints globales). / Metro matrices/waypoints persist
  via the same designer JSON files.
