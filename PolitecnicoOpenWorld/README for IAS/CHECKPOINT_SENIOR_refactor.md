# CHECKPOINT VIVO — Programa "calidad senior" (multi-sesión)

> **Qué es esto:** documento de estado CONTINUO del programa de refactor hacia calidad de
> producción senior. Se actualiza EN CADA PASO. Si una sesión se corta (tokens/energía), la
> siguiente IA retoma AQUÍ. Regla de oro: **el repo debe quedar compilable en cada checkpoint**;
> los pasos de riesgo se hacen de UNO en UNO y el usuario compila cuando se le pide
> (marcador: `⏸️ CHECKPOINT COMPILACIÓN`).

## Objetivo global (acordado con el dueño, 2026-07-04)
Cerrar las 3 brechas señaladas en la evaluación de calidad: (1) suite de tests que corra,
(2) retirar el patrón de gemelos miembro/extensión restante (cadena de routing),
(3) descomponer el god-object `WorldMapViewModel` en managers con estado propio.
Además: perf de gama baja, higiene (detekt/EOL/tamaños) y mantenibilidad para devs/IAs no-senior.
El dueño compila y prueba SOLO cuando se le pide; intervención mínima.

## Mapa de etapas (los planes detallados YA existen — NO reinventar)
| Etapa | Plan de referencia | Estado |
|---|---|---|
| 1. Red de tests de lógica pura (RoadRouter golden-master + guardado + catálogos) | `PLAN_dedup_routing.md` §2 | 🔨 EN CURSO (esta sesión) |
| ⏸️ CHECKPOINT COMPILACIÓN #1: Rebuild + `testDebugUnitTest` | — | pendiente |
| 2. De-dup cadena de routing (1 función por compilación, hoja→raíz) | `PLAN_dedup_routing.md` §3 | pendiente |
| 3. Descomponer VM en managers con sub-estado (fachada `combine`) — empezar por `DesignerManager` | `PLAN_descomponer_WorldMapViewModel.md` | pendiente |
| 4. DI con Hilt (1 VM por PR; WorldMapViewModel al final) | `PLAN_DI_hilt.md` | pendiente |
| 5. Deuda detekt ALTA/MEDIA + baseline bloqueante | `PENDIENTE_calidad.md` | pendiente |
| 6. Perf gama baja (pasada dirigida) + higiene EOL/tamaños | 09 §6 | pendiente |
| 7. Guía de mantenimiento para devs/IAs no-senior | (nuevo doc) | pendiente |

## Estado de la SESIÓN ACTUAL (2026-07-04, sesión senior-1)
### Hecho
- Leídos y adoptados los 4 planes existentes (dedup_routing, descomponer_VM, DI_hilt, PENDIENTE_calidad).
- **Etapa 1 COMPLETA (pendiente de verificar en compilación):**
  - `app/src/main/java/.../domain/usecases/RoadRouter.kt` (NUEVO): algoritmo de routing PURO
    (LatLng/NodeGrid/route/nearestPointOnNetwork/buildNodeGrid/nearbyNodes), copia 1:1 del MIEMBRO
    canónico del VM + la parte geométrica de `getNearestPointOnNetwork`. **NO cableado al runtime**
    (extracción aditiva de riesgo cero). Constantes documentadas (SEG_CELL_DEG 0.0025,
    NODE_GRID_SIZE_DEG 0.001, ARRIVE_EPS 0.0005, MAX_HOP 0.003, MAX_STEPS 20).
  - `app/src/test/.../domain/usecases/RoadRouterTest.kt` (NUEVO): 14 tests golden-master (red vacía,
    calle lineal en orden, red en T, destino desconectado, límite de salto, snap/proyección/clamp,
    dedup de nodos, vecindario 3×3 + fallback, distancia). Aserciones con tolerancia donde el clamp
    t=1 introduce épsilon de doble precisión (NO endurecer a igualdad exacta).
  - `app/src/test/.../data/repository/GameSaveDataTest.kt` (NUEVO): caracterización del guardado —
    primitivos ausentes → 0/false, y el gotcha Gson (lista ausente = NULL runtime pese al default
    Kotlin) PROBADO: si algún día el parser cambia, el test avisa que el coalesce puede retirarse.
  - `MissionCatalogTest.kt`: +3 tests (cadena de desbloqueo M1→M2→M3, `missionIdForObjective`,
    `firstObjectiveOf`).

### Resultado CHECKPOINT COMPILACIÓN #1 (2026-07-04)
- ✅ `Rebuild Project` en VERDE (Etapa 1 compila).
- ⚠️ **`testDebugUnitTest` AÚN NO CORRIÓ**: en PowerShell el comando lleva `.\` →
  **`.\gradlew.bat testDebugUnitTest`** (o ▶ sobre las clases de test en AS). El dueño lo correrá
  después. **Deben estar en VERDE antes de ejecutar los pasos 2-4 de abajo.** Si un test del
  RoadRouter falla: el golden master se copió mal — compara contra el miembro del VM
  (`calculateRouteOnNetwork`/`nearbyRoadNodes`, ~1318-1376) y corrige el ROUTER (no el VM).

### Hecho además — ETAPA 2 · paso 1 (2026-07-04, compilación PENDIENTE)
- **De-dup de `rebuildRoadNodeGrid`** (gemelo IDÉNTICO; el miembro además estaba MUERTO):
  - `WorldMapViewModel.kt`: ELIMINADO el miembro privado `rebuildRoadNodeGrid` (private →
    invisible para extensiones; nada en la clase lo llamaba; detekt lo confirmaba). Tombstone en
    su lugar (~línea 1359). AÑADIDO `internal val roadRouter = RoadRouter()` junto a
    `roadNetworkNodeGrid` (~línea 176-179).
  - `WorldMapRouting.kt` (~334): la extensión (ÚNICA implementación ya) DELEGA en
    `roadRouter.buildNodeGrid(network)` + conversión LatLng→GeoPoint (solo al reconstruir la red).
  - Call-sites verificados con grep: solo `WorldMapRoadNetwork.kt` (109/165/193) → extensión. ✔

### Coming next (orden estricto — próxima sesión)
0. ⏸️ Pedir al dueño: `Rebuild Project` + `.\gradlew.bat testDebugUnitTest` (todo verde). Prueba
   manual corta: entrar al mundo (la rejilla de nodos se reconstruye al cargar calles) y marcar un
   destino → la ruta se dibuja.
1. **Paso 2+3 JUNTOS — `calculateRouteOnNetwork` → `roadRouter.route`:** en el miembro VIVO
   `updateDestinationRoute` (VM ~1288; lo llama `placeDestinationMarker`) sustituir
   `calculateRouteOnNetwork(currentLoc, destination, roadNetwork)` por
   `roadRouter.route(roadNetwork, LatLng(currentLoc.latitude, currentLoc.longitude), LatLng(destination.latitude, destination.longitude)).map { GeoPoint(it.lat, it.lon) }`
   (import de LatLng/RoadRouter o FQN). Después BORRAR 4 funciones: miembro
   `calculateRouteOnNetwork` (VM ~1318) + miembro `nearbyRoadNodes` (VM ~1364, solo lo llamaba ese
   miembro) + extensiones MUERTAS `calculateRouteOnNetwork` (WorldMapRouting ~192) y
   `nearbyRoadNodes` (~349, solo la llamaba esa extensión muerta). Dejar tombstones. ⚠️ Matiz: el
   RoadRouter no aplica el pase-libre por landmarks al snapear extremos (el miembro usaba
   `getNearestPointOnNetwork` que sí) — cosmético para la polilínea, pero verificar en la prueba
   manual marcando destino con el jugador PARADO SOBRE un landmark. → Rebuild + tests + navegación.
2. **Paso 4 — `updateDestinationRoute` único:** queda el par miembro private (VM ~1288, VIVO desde
   `placeDestinationMarker`) vs extensión internal (WorldMapRouting ~162, la usan call-sites FUERA
   de la clase: hacer grep primero y DIFERENCIAR ambas versiones). Canónico = el MIEMBRO; hacerlo
   `internal`, sincronizar cualquier divergencia útil, borrar la extensión. → Rebuild + prueba
   manual completa (destino, conducir por ruta, TP, atasco/rescate).
3. Actualizar 09 §12 (la cadena de routing deja de ser NO-TOCAR; sin gemelos) + este archivo.
4. Etapa 3 (DesignerManager primero) según `PLAN_descomponer_WorldMapViewModel.md`; 1 manager por
   checkpoint. Luego Etapas 4-7 de la tabla.

## Reglas para la IA que retome esto
- Lee `09_CONVENTIONS_GOTCHAS.md` COMPLETO antes de tocar código (miembro-vs-extensión, CRLF,
  verificación con Read — no bash —, truncación del sandbox, protocolo de docs).
- El `RoadRouter` puro debe permanecer IDÉNTICO en comportamiento al miembro hasta que el de-dup
  termine; cualquier mejora algorítmica va DESPUÉS, con los tests en verde como base.
- `nearestPointOnNetwork` puro NO incluye el pase-libre por landmarks (es estado del VM): el wrapper
  del VM (`WorldMapRouting.getNearestPointOnNetwork`) conserva ese check y delega la geometría.
- No agrupar pasos de de-dup: UNO por compilación del usuario.
- Actualiza ESTE archivo al terminar cada paso (Hecho / En curso / Coming next).
