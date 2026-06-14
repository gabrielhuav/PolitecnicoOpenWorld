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
  `CanchasFutbolScreen`, `DeportivoBeisScreen`, `DeportivoFutbolScreen`) son envoltorios finos que pasan
  el `backgroundAsset` / config y delegan en la base.
- **`viewmodel/InteriorViewModel.kt`** (NavBackStackEntry-scoped, `Factory`):
  - Estado: `InteriorState` (posición del jugador, facing, running, etc.).
  - `moveByAngle(angleRad)`, `moveDirection(direction)`, `applyMovement(newX, newY, dxForFacing)`,
    `updatePlayer(x, y, dxForFacing)`, `setRunning(running)`, `setInitialPosition(x, y)`.
  - Movimiento normalizado [0,1], validado contra la `CollisionGrid` (`isWalkable`).

**Entrada / entry:** desde el open world, una puerta ESCOM (`spawnEscomDoors`) dispara
`pendingDoorDestination` → fade (`showEscomDoorFade`) → navega a `interior_<id>` (ver mapa de rutas en 01).
Cada `InteriorBuilding` define su `routeName`, `location` y `backgroundAsset` (ver 03).

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
