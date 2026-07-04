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

### Hecho además — ETAPA 2 · pasos 2-4 ✅ COMPLETA (2026-07-04, compilación PENDIENTE)
- Checkpoint del paso 1 verificado por el dueño: Rebuild verde + navegación/waypoints funcionando.
- ⚠️ El wrapper de Gradle está ROTO en consola (`Unable to access jarfile ...gradle-wrapper.jar`,
  falta `gradle/wrapper/gradle-wrapper.jar`): **correr los tests DESDE Android Studio** (clic
  derecho sobre `RoadRouterTest`/`GameSaveDataTest`/`MissionCatalogTest` → Run) hasta restaurar el
  jar (p. ej. `gradle wrapper` desde una instalación local o copiarlo de otro proyecto).
- **La cadena de routing quedó SIN gemelos (era el "NO-TOCAR" del 09 §12, ya actualizado):**
  - Miembro `updateDestinationRoute` (VIVO, único ya): delega en `roadRouter.route(...)` con
    conversión LatLng↔GeoPoint (matiz documentado: snap de extremos sin pase-libre por landmarks).
  - ELIMINADOS con tombstone: miembros `calculateRouteOnNetwork` + `nearbyRoadNodes` (VM) y
    extensiones muertas `updateDestinationRoute` + `calculateRouteOnNetwork` + `nearbyRoadNodes`
    (WorldMapRouting.kt). Grep verificado: 0 referencias vivas (los hits de UI web son la función
    JS homónima de WorldMapLeafletHtml, no Kotlin).
  - Anotado: micro-opt de la extensión muerta (keys `Pair` en vez de `String` en visited/distinct)
    puede aplicarse al RoadRouter DESPUÉS, con tests en verde (cambio separado).

### ✅ CHECKPOINT COMPILACIÓN #2 — VERDE (confirmado por el dueño)
- Rebuild OK, **44/44 tests OK**, navegación OK. **ETAPA 2 CERRADA.**

### Hecho además — ETAPA 5 · lote 1 (2026-07-04, compilación PENDIENTE = ⏸️ CHECKPOINT #3)
- Micro-opt del RoadRouter aplicada: `visitedNodes`/`distinct` por `LatLng` (igualdad estructural)
  en vez de Strings concatenados — cero allocs de String por paso; contrato intacto (los 14 tests
  deben seguir verdes SIN tocarlos).
- Código muerto eliminado (inventario detekt verificado por grep — estaba MUY desactualizado, ver
  nota nueva al inicio de `PENDIENTE_calidad.md`): `isGoingVeryFast` (VM), `hasTriggeredNativePan`
  (WorldMapScreen), `INTERACT_RADIUS_METERS` local (WorldMapCollectiblesLogic), `newTarget`
  (NpcAiManagerTraffic). Último `printStackTrace` → `Log.w` (MetroMapOverlay).
- Lo que QUEDA de detekt: solo params-de-firma + 8 issues menores + regla de loops (detalle en
  PENDIENTE_calidad.md). Recomendado: regenerar inventario con un run real de detekt + baseline.

### ✅ CHECKPOINT COMPILACIÓN #3 — VERDE (confirmado por el dueño)

### Hecho además — ETAPA 3 · manager 1/6: `DesignerManager` ✅ (2026-07-04, compilación PENDIENTE)
**La FACHADA `combine` quedó instalada — es la técnica para TODOS los managers siguientes:**
- `viewmodel/DesignerManager.kt` (NUEVO): posee `DesignerEditState` (6 campos del editor de
  Debug Interiores: `showInteriorDebugOverlay` + `debugEditTool/Walls/Blocks/NavPed/NavCar`)
  con `MutableStateFlow` propio. Lógica pura, sin Android.
- `WorldMapViewModel.kt`: `internal val designerManager = DesignerManager()` +
  **`uiState` ya NO es `_uiState.asStateFlow()`**: es
  `combine(_uiState, designerManager.state) { base, d -> base.copy(6 campos) }.stateIn(viewModelScope, Eagerly, _uiState.value)`.
  La UI ve el MISMO `WorldMapState` (no se tocó ni una View). Imports añadidos:
  `combine`/`stateIn`/`SharingStarted`.
- `WorldMapDebugEditor.kt`: reescrito — las extensiones CONSERVAN SU FIRMA y delegan en el
  manager; export/import se quedan aquí (mezclan `exteriorCollisions` del estado base + I/O).
- `WorldMapInteractions.toggleInteriorDebugOverlay` → delega en el manager.
- `WorldMapState.kt`: los 6 campos anotados con ⚠️ "LOS POSEE DesignerManager — no escribirlos
  con `_uiState.update`" (la fachada los sobreescribiría).
- `DesignerManagerTest.kt` (NUEVO, 7 tests): overlay/tool, commit de wall (pares consecutivos),
  block (mínimo 3 puntos), strokes ignorados, undo por herramienta, clear conserva modo, import
  reemplaza. **Total esperado: 51 tests.**
- Verificado por grep: NADIE lee esos campos vía `_uiState.value` ni los escribe fuera del
  manager; la única lectura síncrona externa de `uiState.value` es `mapProvider` (no afectada).

### ✅ CHECKPOINT COMPILACIÓN #4 — VERDE (confirmado por el dueño, 2026-07-04)
Rebuild OK, 51/51 tests, editor de debug y juego normal OK. **La fachada combine está PROBADA
en runtime.** El manager 1/6 (Designer) queda cerrado.

---

## 🤝 HANDOFF → SESIÓN OPUS 4.8 (el dueño delega el resto del programa)

**Contexto del relevo:** la sesión Fable 5 ejecutó Etapas 1, 2, 5-grueso y el manager 1/6 de la
Etapa 3 (todo VERDE). Opus 4.8 continúa desde aquí. El dueño volverá al final solo a verificar.

### RECETA PROBADA para extraer un manager (así se hizo DesignerManager — replicar tal cual)
1. **Delimita el grupo**: elige los campos de `WorldMapState` del grupo (léelos en el archivo,
   están seccionados con `─── TÍTULO ───`). Grep de CADA campo en `app/src/main/java`:
   clasifica hits en (a) writers `_uiState.update { it.copy(campo…`, (b) reads VM-internos
   `_uiState.value.campo`, (c) reads de UI vía `uiState.campo` (estos NO se tocan).
2. **Crea `XManager.kt`** en `features/map_exterior/viewmodel/`: `data class XSubState(campos con
   los MISMOS defaults que WorldMapState)` + `MutableStateFlow` privado + `StateFlow` público +
   métodos con la lógica movida. SIN dependencias de Android/VM (lógica pura → testeable). Si una
   función mezcla sub-estado con estado base/IO/Context, la extensión se queda en el VM y solo
   delega la parte del sub-estado (ejemplo: export/import en WorldMapDebugEditor.kt).
3. **VM**: `internal val xManager = XManager()` ANTES de `uiState`; añade su flow al `combine`
   (hay overloads tipados hasta 5 flows; con más, anida: `combine(combine(a,b,c){...}, d, e)` o
   usa el vararg con Array). El lambda hace `base.copy(campos del manager)`.
4. **Reescribe las extensiones** del parcial correspondiente para DELEGAR conservando FIRMA
   (las Views NO se tocan). Los reads VM-internos (b) pasan a `xManager.state.value.campo`.
5. **Anota los campos en WorldMapState**: "⚠️ LOS POSEE XManager — no escribirlos con
   _uiState.update" (la fachada los sobreescribe; un write directo sería ignorado = bug sordo).
6. **Verifica por grep** (debe dar 0): `_uiState.value.<campo>` y `it.copy(<campo>` fuera del
   manager. Verifica cada edit con Read (no bash).
7. **Tests JVM del manager** (patrón `DesignerManagerTest`) + actualiza ESTE doc +
   ⏸️ CHECKPOINT (Rebuild + tests + prueba manual del feature migrado).

### Orden de managers restantes (Etapa 3, 1 por ⏸️ checkpoint)
2. **CollectiblesManager** — parciales `WorldMapCollectiblesLogic.kt`/`WorldMapEscomItems.kt`;
   grupo COLECCIONABLES de WorldMapState (activos, popup, nearbyCollectible…). ⚠️ el game loop
   MIEMBRO llama `checkCollectibleProximity`/`trySpawningCollectible` y hay estado no-UI
   (`_escomItems` flow aparte, `isSpawningCollectible`): mueve SOLO el sub-estado UI del grupo;
   los flows/ítems ESCOM pueden quedarse si el corte se vuelve confuso — corte limpio > dogma.
3. **CombatManager** — `WorldMapCombat.kt` + `WorldMapHealth.kt` (vida/impactos/rachas). ⚠️ La
   vida se lee en CADA tick y en triggerWastedSequence (miembro): migra reads con cuidado.
4. **WantedManager** — `WorldMapWanted.kt` (estrellas/carjack; envuelve PoliceManager).
5. **TransitTeleportManager** — `WorldMapTeleport.kt` + fades/nearby de metro/metrobús. ⚠️
   `teleportTo` resetea campos de OTROS grupos (ver 09): esa orquestación se queda en el VM.
6. **CampaignManager** — el más enredado (misiones 1-3, registro, replay): AL FINAL, puede
   dividirse en 2 checkpoints (estado de registro/replay primero, fases de misión después).
- Regla transversal: si un grupo resulta tener demasiados writers entrelazados con el game loop
  (p. ej. posición/vehículo), NO forzar su extracción — documentar por qué y seguir. El objetivo
  es acoplamiento bajo REAL, no el 100% de campos migrados.

### Después de la Etapa 3
- **Etapa 4 · Hilt**: seguir `PLAN_DI_hilt.md` al pie (PR 1 infra → módulos → 1 VM simple →
  resto → WorldMapViewModel AL FINAL con scope de Activity verificado). 1 PR por ⏸️ checkpoint.
- **Etapa 5 · resto**: regenerar inventario detekt (el viejo estaba desactualizado, ver
  PENDIENTE_calidad.md), quitar params sin uso (revisando call-sites), crear baseline y quitar
  `continue-on-error` del workflow.
- **Etapa 6 · perf gama baja**: pasada dirigida por 09 §6 (verificar que no se introdujeron
  allocations por frame — la fachada combine añade 1 copy por emisión, ya aceptado; revisar
  cachés LRU intactos). Cambios SOLO puntuales y documentados.
- **Cierre**: protocolo de docs 09 §13 completo (01 arquitectura managers+fachada, 04 tabla Key
  files con los XManager, 09 convención nueva "campos poseídos por managers", README EN+ES) +
  informe final al dueño con TODO lo que debe probar.

### PROMPT para la nueva conversación con Opus 4.8 (copiar/pegar tal cual)
```
Estás ayudándome con "Politécnico Open World" (POW), un juego Android 2D top-down sobre mapas
reales (Kotlin + Jetpack Compose + MVVM estricto). La carpeta "README for IAS" es el contexto
COMPLETO del proyecto y reemplaza al código.

LEE EN ESTE ORDEN antes de tocar nada:
1. README for IAS/GUIA_mantenimiento_no_senior.md (reglas de supervivencia)
2. README for IAS/09_CONVENTIONS_GOTCHAS.md COMPLETO (gotchas: miembro-vs-extensión, CRLF,
   verificar edits con Read —nunca bash—, truncación del sandbox, protocolo de docs)
3. README for IAS/CHECKPOINT_SENIOR_refactor.md ← FUENTE DE VERDAD del programa en curso
   (estado, receta probada del patrón manager+fachada combine, orden de managers, handoff)
4. PLAN_descomponer_WorldMapViewModel.md, PLAN_DI_hilt.md, PENDIENTE_calidad.md

TU MISIÓN: terminar el programa "calidad senior" desde donde quedó (Etapa 3 manager 2/6 =
CollectiblesManager). Etapas: 3 (managers 2-6) → 4 (Hilt) → 5 resto (detekt baseline) → 6
(perf gama baja) → cierre de docs. Sigue la RECETA y el ORDEN del CHECKPOINT_SENIOR_refactor.md.

FLUJO DE TRABAJO (no negociable):
- Yo NO sé programar a tu nivel: tú haces todo; yo SOLO compilo y pruebo cuando me lo pidas.
- Trabaja en pasos que dejen el repo SIEMPRE compilable. En cada ⏸️ CHECKPOINT pídeme:
  Rebuild Project + tests desde Android Studio (clic derecho en app/src/test → Run; el
  gradlew.bat de consola está roto: falta gradle/wrapper/gradle-wrapper.jar) + una prueba
  manual CONCRETA del feature tocado. Espera mi "verde" antes de seguir.
- Actualiza CHECKPOINT_SENIOR_refactor.md al terminar CADA paso (Hecho / Coming next): si la
  sesión se corta, la siguiente retoma ahí sin perder nada.
- Al terminar TODO: protocolo de docs del 09 §13 (docs 00-09 + README público EN+ES) + informe
  final con todo lo que debo probar en el dispositivo.
- Comentarios y strings de UI en español; strings nuevos ES+EN en paridad.
- Hay 51 tests en verde (RoadRouterTest, GameSaveDataTest, MissionCatalogTest,
  DesignerManagerTest + previos): NUNCA deben ponerse rojos sin justificación escrita.
```

## Reglas para la IA que retome esto
- Lee `09_CONVENTIONS_GOTCHAS.md` COMPLETO antes de tocar código (miembro-vs-extensión, CRLF,
  verificación con Read — no bash —, truncación del sandbox, protocolo de docs).
- El `RoadRouter` puro debe permanecer IDÉNTICO en comportamiento al miembro hasta que el de-dup
  termine; cualquier mejora algorítmica va DESPUÉS, con los tests en verde como base.
- `nearestPointOnNetwork` puro NO incluye el pase-libre por landmarks (es estado del VM): el wrapper
  del VM (`WorldMapRouting.getNearestPointOnNetwork`) conserva ese check y delega la geometría.
- No agrupar pasos de de-dup: UNO por compilación del usuario.
- Actualiza ESTE archivo al terminar cada paso (Hecho / En curso / Coming next).
