# PLAN — De-duplicar la cadena de ROUTING (con tests de respaldo PRIMERO)

> Estado: TRABAJO FUTURO (requiere compilador). Generado en la sesión de noche 2026-06-24.
> Roadmap ANALISIS §9.3. NO ejecutado a ciegas: aquí está el plan paso a paso y verificable.

## 0. Por qué este refactor es PELIGROSO (contexto)
La cadena de routing es el último gemelo **miembro-vs-extensión** vivo y está marcada **NO-TOCAR** en
`09 §12`: de-duplicar la cabeza re-enlazaba TODA la cadena y rompía la navegación. Hoy convive:
- **MIEMBRO (vivo, gana)** en `WorldMapViewModel.kt`:
  - `updateDestinationRoute()`            (~línea 1218)
  - `calculateRouteOnNetwork(from,to,network)` (~1248)
  - `rebuildRoadNodeGrid(network)`        (~1286)
  - `nearbyRoadNodes(point)`              (~1301)
- **EXTENSIÓN (muerta, diverge)** en `WorldMapRouting.kt`: las MISMAS 4 firmas como
  `internal fun WorldMapViewModel.X(...)` (líneas 162/192/334/349) + `getNearestPointOnNetwork` (86,
  ESTA ya es única: no tiene gemelo miembro).
La extensión trae lógica VIEJA (con bugs ya corregidos en el miembro). Al llamarse DESDE la clase gana el
miembro; la extensión queda muerta pero **confunde** y es una bomba: si alguien borra el miembro, se
activa la extensión buggy. Sin red de tests, de-duplicar a ciegas = regresión silenciosa en navegación.

## 1. Objetivo
Dejar UNA sola implementación canónica de cada función de routing, SIN gemelo, **sin cambiar el
comportamiento** observable, respaldada por tests que fijen ese comportamiento.

## 2. ORDEN OBLIGATORIO: tests ANTES de tocar la cadena
### 2.1 Extraer el ALGORITMO puro (lo testeable) — extracción conservadora
La cadena mezcla algoritmo (A*/snap/grid sobre `List<MapWay>`) con estado del VM (`currentLocation`,
`destination`, el grid cacheado, `roadNetwork`). Para testear sin Android:
- Crear `domain/usecases/RoadRouter.kt` (Kotlin PURO, sin `Context`/VM): recibe primitivos/estructuras
  simples y devuelve la ruta. Firma propuesta:
  - `class RoadRouter` con:
    - `fun buildNodeGrid(network: List<MapWay>): NodeGrid`
    - `fun nearbyNodes(grid: NodeGrid, point: LatLng): List<LatLng>`
    - `fun nearestPointOnNetwork(network: List<MapWay>, point: LatLng): LatLng`
    - `fun route(network: List<MapWay>, from: LatLng, to: LatLng): List<LatLng>`
  - Usar un `data class LatLng(lat: Double, lon: Double)` propio (o reutilizar uno ya puro del dominio)
    para NO depender de `GeoPoint` (OSMDroid) en el test.
- El cuerpo se COPIA del MIEMBRO (la versión viva/correcta), traduciendo `GeoPoint`->`LatLng` y los
  campos de estado a parámetros. NO tocar todavía el VM ni la extensión (esta fase no cambia runtime).

### 2.2 Tests del algoritmo (golden master / characterization)
En `app/src/test/.../RoadRouterTest.kt` (JUnit puro):
- **Red lineal:** una sola calle recta de N nodos -> `route(a,b)` devuelve los nodos en orden; longitud
  monótona; `nearestPointOnNetwork` de un punto al lado cae sobre el segmento.
- **Red en T / rejilla:** ruta entre dos extremos elige el camino correcto; sin nodos sueltos repetidos.
- **Sin conexión:** objetivo aislado -> contrato actual (lista vacía o fallback; fíjalo según el miembro).
- **Grid:** `buildNodeGrid` + `nearbyNodes` devuelven los nodos dentro del radio esperado.
- Estos tests CONGELAN el comportamiento del MIEMBRO (cópialo tal cual). Si luego el de-dup cambia algo,
  el test se pone rojo = regresión detectada.

## 3. De-dup propiamente (CON compilador, 1 función por ciclo)
Proceso por función (el de `09 §12`): leer miembro + extensión -> DIFERENCIAR -> revisar la cascada (qué
llama y si esos callees son gemelos) -> elegir el canónico -> borrar el otro -> COMPILAR + PROBAR.

Orden recomendado (de hoja a raíz, para no re-enlazar la cadena de golpe):
1. **`nearbyRoadNodes`** (hoja): hacer que el VM use `roadRouter.nearbyNodes(grid, ...)`; borrar el
   miembro y la extensión. Test: navegación sigue marcando ruta.
2. **`rebuildRoadNodeGrid`** (hoja): idem -> `roadRouter.buildNodeGrid`. Guardar el `NodeGrid` en el
   estado/campo del VM (igual que hoy). Borrar gemelos.
3. **`calculateRouteOnNetwork`**: delega en `roadRouter.route(...)`. Borrar gemelos.
4. **`updateDestinationRoute`** (cabeza): orquesta (lee `destination`/`currentLocation`, llama a
   `roadRouter.route`, escribe la polilínea al estado). Queda como ÚNICO miembro (o única extensión),
   sin gemelo. Borrar el otro.
5. `getNearestPointOnNetwork` ya es única (extensión) -> solo redirigir a `roadRouter.nearestPointOnNetwork`
   si quieres centralizar; opcional.

Regla dura: **una función por compilación**; tras cada una, Rebuild + probar navegación a mano (marcar
destino, conducir, TP). No agrupar (no se bisecaría una regresión). Mantén `WorldMapRouting.kt` como sede
de las extensiones supervivientes o muévelas al `RoadRouter`; lo importante es que **no quede ningún par**.

## 4. Verificación
- Sin compilador (de noche): balance Kotlin-aware del/los archivo(s), EOL CRLF, refs.
- Con compilador (mañana/AS): `testDebugUnitTest` (RoadRouter verde) + Rebuild + prueba manual de
  navegación (destino marcado, ruta dibujada en los 3 renderers, TP/atasco). Buscar 0 referencias colgantes.
- Grep de control: que NO queden 2 definiciones de la misma firma (`grep -rn "fun .*calculateRouteOnNetwork"`).

## 5. Qué necesita compilador (no se puede de noche)
Todo el paso 3 (borrar miembros/ extensiones y re-enlazar) y correr los tests. La fase 2 (escribir
`RoadRouter` + sus tests COPIANDO el algoritmo del miembro) se puede ADELANTAR de noche como extracción
aditiva NO conectada (no cambia runtime), pero NO se hizo aquí para no introducir una 3ª copia del
algoritmo sin poder compilarla; queda como primer paso listo para AS.

## 6. Riesgos
- El `NodeGrid` y el snap dependen de la densidad real OSM; los tests sintéticos cubren la lógica, no la
  calibración -> conserva la prueba MANUAL de navegación como gate final.
- No re-introducir empujones de posición ni tocar `maybeRefetchRoadNetwork`/`applyRoadNetwork` (ya
  de-dup; ver 09 §12).
