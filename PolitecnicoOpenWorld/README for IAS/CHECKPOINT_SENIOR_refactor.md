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

### En curso
- ⏸️ **CHECKPOINT COMPILACIÓN #1** — pedido al usuario: `Rebuild Project` +
  `gradlew.bat testDebugUnitTest` (o ▶ en las clases de test desde AS). TODO debe quedar en verde
  ANTES de empezar la Etapa 2. Si un test del RoadRouter falla: el golden master se copió mal —
  compara contra el miembro del VM (líneas ~1288-1383) y corrige el ROUTER (no el VM).

### Coming next (orden estricto)
2. Etapa 2 paso 1: VM usa `roadRouter.nearbyNodes` (borrar gemelos de `nearbyRoadNodes`) → compilar.
3. Etapa 2 paso 2: `rebuildRoadNodeGrid` → `roadRouter.buildNodeGrid` → compilar.
4. Etapa 2 paso 3: `calculateRouteOnNetwork` → `roadRouter.route` → compilar.
5. Etapa 2 paso 4: `updateDestinationRoute` queda ÚNICO (borrar el gemelo) → compilar + prueba manual
   de navegación (marcar destino, ruta dibujada, TP).
6. Etapa 3 (DesignerManager primero) según su plan; 1 manager por checkpoint.

## Reglas para la IA que retome esto
- Lee `09_CONVENTIONS_GOTCHAS.md` COMPLETO antes de tocar código (miembro-vs-extensión, CRLF,
  verificación con Read — no bash —, truncación del sandbox, protocolo de docs).
- El `RoadRouter` puro debe permanecer IDÉNTICO en comportamiento al miembro hasta que el de-dup
  termine; cualquier mejora algorítmica va DESPUÉS, con los tests en verde como base.
- `nearestPointOnNetwork` puro NO incluye el pase-libre por landmarks (es estado del VM): el wrapper
  del VM (`WorldMapRouting.getNearestPointOnNetwork`) conserva ese check y delega la geometría.
- No agrupar pasos de de-dup: UNO por compilación del usuario.
- Actualiza ESTE archivo al terminar cada paso (Hecho / En curso / Coming next).
