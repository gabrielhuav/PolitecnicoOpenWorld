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
| 1. Red de tests de lógica pura (RoadRouter golden-master + guardado + catálogos) | `PLAN_dedup_routing.md` §2 | ✅ HECHA (CHECKPOINT #1 verde) |
| 2. De-dup cadena de routing (1 función por compilación, hoja→raíz) | `PLAN_dedup_routing.md` §3 | ✅ HECHA (CHECKPOINT #2 verde) |
| 3. Descomponer VM en managers con sub-estado (fachada `combine`) | `PLAN_descomponer_WorldMapViewModel.md` | ✅ HECHA (6/6: Designer/Collectibles/Combat/Wanted/TransitTeleport/Campaign·registro; fase de misión = corte limpio. CHECKPOINTS #4-#9 verdes) |
| 4. DI con Hilt (1 VM por PR; WorldMapViewModel al final) | `PLAN_DI_hilt.md` | ✅ IMPLEMENTADA (9 VMs migradas de golpe a petición del dueño; ⏸️ CHECKPOINT #10 pend. de compilar) |
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

### Hecho además — ETAPA 3 · manager 2/6: `CollectiblesManager` ✅ (2026-07-04 sesión Opus 4.8, compilación PENDIENTE)
Segunda aplicación de la RECETA combine (idéntica a Designer). **CORTE:** el manager posee SOLO
el sub-estado UI del grupo COLECCIONABLES; los ítems ESCOM / flags de spawn NO-UI se quedan en el VM.
- `viewmodel/CollectiblesManager.kt` (NUEVO, LF): posee `CollectiblesUiState` = 3 campos
  (`activeCollectibles`, `nearbyCollectible`, `showClaimedPopupFor`) con `MutableStateFlow` propio.
  Métodos puros: `setActive`/`addActive`/`clearActive`, `setNearby`/`clearNearby`,
  `claim(item)` (vacía activos+cercano y fija el popup **atómicamente**), `dismissClaimedPopup`.
- **NO migrados a propósito** (siguen en el VM, no-UI / enredados con game loop y zona ESCOM):
  `_escomItems` (flow aparte), `isZombieHandSpawned`, `isSpawningCollectible` (AtomicBoolean).
  Documentado en el header del manager y en el aviso del CHECKPOINT — corte limpio > dogma.
- `WorldMapViewModel.kt`: `internal val collectiblesManager = CollectiblesManager()` +
  el `combine` pasó a **3 flows** `combine(_uiState, designerManager.state, collectiblesManager.state)`
  y el `base.copy(...)` vuelca los 3 campos. La UI ve el MISMO `WorldMapState` (0 Views tocadas).
- Extensiones que ORQUESTAN (mezclan Context/IO/otros grupos) se quedan en el VM y DELEGAN la
  parte del sub-estado, conservando FIRMA:
  - `WorldMapCollectiblesLogic.kt`: `trySpawningCollectible`/`checkCollectibleProximity` — los
    reads VM-internos pasaron a `collectiblesManager.state.value.*`; los writes de metro/metrobús
    se **partieron** (`_uiState.update{...}` para el campo base + `collectiblesManager.clearNearby()`).
  - `WorldMapInteractions.kt`: `onClaimCollectiblePressed` → `collectiblesManager.claim(...)` +
    `_uiState.update{interactionPrompt}`; `dismissClaimedPopup` delega; reads de `nearbyCollectible`
    migrados.
  - `WorldMapShineCTO.kt`: `spawnShineCTOMarker` → `addActive`; `onShineCTODiscoveryConfirmed`/
    `onEscomDoorFadeComplete` → `clearNearby()` + copy base sin el campo.
- `WorldMapState.kt`: los 3 campos anotados ⚠️ "LOS POSEE CollectiblesManager — no escribir con
  `_uiState.update`".
- `CollectiblesManagerTest.kt` (NUEVO, LF, 7 tests): estado inicial, setActive reemplaza, addActive
  persiste, clearActive no toca radar, set/clear nearby, claim atómico, dismiss. **Total esperado: 58 tests.**
- Verificado por grep: asignaciones de los 3 campos SOLO en el manager + la fachada; 0 reads por
  `_uiState.value.<campo>`; sin gemelos miembro de las funciones tocadas. Edits verificados con Read.
- ⚠️ Nota de tamaño: el VM quedó en ~1524 líneas (la fachada + comentarios suman; la lógica ya vivía
  en parciales, no en el VM). Sobre el objetivo blando de 1500 por 24 líneas — se reducirá al migrar
  más grupos. No es bloqueante.

### ✅ CHECKPOINT COMPILACIÓN #5 — VERDE (confirmado por el dueño, 2026-07-04)
Rebuild OK + tests + coleccionables/metro/puerta/ShineCTO OK. Manager 2/6 (Collectibles) cerrado.
Sin parpadeo en las transiciones metro/puerta. Se procede al manager 3/6 (CombatManager).

### Hecho además — ETAPA 3 · manager 3/6: `CombatManager` ✅ (2026-07-04 sesión Opus 4.8, compilación PENDIENTE)
**VARIANTE DEL PATRÓN (importante para los siguientes):** el estado de VIDA/FX de impacto NO son
campos de `WorldMapState` (no van por el `combine`): son **Compose `mutableStateOf`** que las Views
leen DIRECTO (`viewModel.playerHealth`/`.showHealthBar`/`.damagePulseTrigger`/`.impactEffectTrigger`).
Para NO tocar ninguna View, el backing store se mudó al manager y el VM conserva **miembros
DELEGANTES** (get/`internal set` → manager). Compose sigue recomponiendo (el getter del VM lee el
State del manager dentro del @Composable). Es el análogo de la fachada combine para estado
Compose-directo (no-StateFlow).
- `viewmodel/CombatManager.kt` (NUEVO, LF): posee `playerHealth`, `maxPlayerHealth`, `showHealthBar`,
  `damagePulseTrigger`, `impactEffectTrigger` (mutableStateOf) + el **throttle del 💥** (`fireImpactEffect`
  con **reloj inyectable** `clockMs` → testeable en JVM; `IMPACT_THROTTLE_MS=900`).
- `WorldMapViewModel.kt`: `internal val combatManager = CombatManager()` + los 4 estados y
  `maxPlayerHealth` pasan a **propiedades delegantes**; `fireImpactEffect()` delega. Se BORRARON las
  declaraciones backing + `lastImpactEffectMs`/`IMPACT_EFFECT_THROTTLE_MS` del VM y los 3 imports de
  Compose ya sin uso (`mutableStateOf`/`getValue`/`setValue`).
- **NO tocado (a propósito):** la LÓGICA de combate (`WorldMapCombat.kt`) y de vida (`WorldMapHealth.kt`,
  `takeDamage`/`heal`), el temporizador `startHealthBarTimer`/`healthBarJob` (usa viewModelScope),
  `triggerWastedSequence`, `respawnImmunityUntilMs` y los internals de combate (`lastAttackTime`,
  `npcHitStreak`, `relentlessNpcs`, `npcContactCooldowns`, `lastZombieBiteMs`, constantes) → siguen en
  el VM y escriben los estados vía los nombres delegados (0 cambios en esos archivos). Cero Views tocadas.
- `CombatManagerTest.kt` (NUEVO, LF, 3 tests): estado inicial (100 HP, sin FX) + throttle del 💥
  (throttled dentro de la ventana, vuelve a disparar al cumplirse `IMPACT_THROTTLE_MS`). **Total esperado: 61 tests.**
- Verificado por grep: 0 backing `by mutableStateOf` de estos campos en el VM; 0 referencias al throttle
  viejo; imports Compose sin uso retirados. Edits verificados con Read; clase cierra OK (~1525 líneas).

### ✅ CHECKPOINT COMPILACIÓN #6 — VERDE (confirmado por el dueño, 2026-07-04)
Rebuild OK + tests + vida/💥 reactivos OK. Manager 3/6 (Combat) cerrado. **Siguiente: manager 4/6
`WantedManager`** (sesión nueva). El dueño continúa el programa en otra conversación desde aquí.

### Hecho además — ETAPA 3 · manager 4/6: `WantedManager` ✅ (2026-07-04 sesión Opus 4.8, compilación PENDIENTE = ⏸️ CHECKPOINT #7)
Cuarta aplicación de la RECETA combine (como Collectibles: los 3 campos SON de WorldMapState y los
lee la UI). **CORTE:** el manager posee el SUB-ESTADO + los TIMERS + las CONSTANTES del subsistema;
la ORQUESTACIÓN (red/policía/Context/IO) se queda en el VM y solo DELEGA los writes.
- `viewmodel/WantedManager.kt` (NUEVO, LF): posee `WantedSubState` = 3 campos (`wantedLevel`,
  `carjackWarning`, `policeShots`) con `MutableStateFlow` propio + los timers `lastCrimeTime`/
  `lastWantedDecayTime`/`carjackStartTime` (antes @Volatile en el VM) + constantes `MAX_WANTED_LEVEL`/
  `WANTED_DECAY_GRACE_MS`/`WANTED_DECAY_STEP_MS`/`CARJACK_MS`. Métodos PUROS con `now` inyectado por
  parámetro (testeables en JVM): `raiseWantedLevel`, `tickWantedDecay`, `setWantedLevel`, `setMaxWanted`,
  `clearWanted`, `armCarjack`/`clearCarjack` (máquina de carjack), `addPoliceShots`,
  `mergeAndPrunePoliceShots`.
- **NO migrado a propósito** (se queda en el VM, orquestación enredada con red/policía/IO/otros grupos):
  `runPoliceTick` (PoliceManager.update, snap/pathfind, daño, broadcast WS, purga remota),
  `anyAggressorAdjacent` (recorre remoteEntities), `handleCarjack` (lee vehicleSpeed/MAX_SPEED,
  getLocalizedString, forceExitVehicle), `forceExitVehicle`. También `lastPoliceBroadcast`/
  `POLICE_BROADCAST_MS`/`REMOTE_POLICE_STALE_MS`/`CARJACK_ADJ_RADIUS`. El guard del apocalipsis
  (`globalZombieMode`) se queda en la extensión `raiseWantedLevel` del VM (necesita el estado base).
- `WorldMapViewModel.kt`: `internal val wantedManager = WantedManager()` + el `combine` pasó a **4 flows**
  `combine(_uiState, designerManager.state, collectiblesManager.state, wantedManager.state)` y el
  `base.copy(...)` vuelca los 3 campos. La UI ve el MISMO `WorldMapState` (0 Views tocadas). Se
  ELIMINARON del VM `MAX_WANTED_LEVEL`/`WANTED_DECAY_*`/`CARJACK_MS` + `lastCrimeTime`/`lastWantedDecayTime`/
  `carjackStartTime` (tombstones). El write de `policeShots` del game loop (policía del apocalipsis)
  → `wantedManager.addPoliceShots(...)`.
- Writers ENTRELAZADOS rerouteados (varios combinados con OTROS grupos → se PARTIERON como en Collectibles):
  - `WorldMapWanted.kt`: `raiseWantedLevel`/`tickWantedDecay` delegan; `runPoliceTick` lee
    `wantedManager.state.value.wantedLevel` y usa `mergeAndPrunePoliceShots`; `handleCarjack` usa
    `armCarjack`/`clearCarjack`. `forceExitVehicle`/`anyAggressorAdjacent` sin cambios de estado wanted.
  - `WorldMapSaveGame.kt`: `buildSaveData` lee `wantedManager.state.value.wantedLevel` (⚠️ ANTES leía
    `_uiState.value.wantedLevel`, que con la fachada daría SIEMPRE 0 → habría guardado 0 estrellas);
    `restoreSaveData` → `wantedManager.setWantedLevel(data.wantedLevel)` (sacado del copy).
  - `WorldMapMisc.kt` (WASTED): ambos branches → `wantedManager.clearWanted()` (resetea nivel+aviso+timer).
  - `WorldMapTeleport.kt`: reset de TP → `wantedManager.clearWanted()` (sacado del copy grande).
  - `WorldMapInteractions.kt` (abordar patrulla): → `wantedManager.setMaxWanted(nowMs)` (5★ + marca delito).
  - `WorldMapCampaignPolice.kt`: los 3 sets (1/1/0) → `wantedManager.setWantedLevel(...)` (idempotente:
    StateFlow no reemite si no cambia → sustituye a los `if (wantedLevel != 1)`).
- `WorldMapState.kt`: los 3 campos anotados ⚠️ "LOS POSEE WantedManager — no escribir con `_uiState.update`".
- `WantedManagerTest.kt` (NUEVO, LF, 10 tests): estado inicial, subida+tope, decaimiento (gracia + paso
  que escala con el nivel), setWantedLevel coacción, setMaxWanted marca delito, clearWanted, máquina de
  carjack (arma→dispara a CARJACK_MS), clearCarjack, addPoliceShots, merge+prune por TTL. **Total esperado: 71 tests.**
- Verificado por grep: 0 `_uiState.value.{wantedLevel,carjackWarning,policeShots}`, 0 `it.copy(<campo>` de
  estos 3 fuera del manager+fachada, 0 refs vivas a los miembros removidos del VM (solo tombstones/comentarios).
  Edits verificados con Read; balance de llaves OK.

### ✅ CHECKPOINT COMPILACIÓN #7 — VERDE (confirmado por el dueño, 2026-07-04)
Rebuild OK + tests + wanted/policía/carjack/guardado-carga OK. Manager 4/6 (Wanted) cerrado.
**Siguiente: manager 5/6 `TransitTeleportManager`** (`WorldMapTeleport.kt` + fades/nearby de metro/
metrobús; ⚠️ `teleportTo` resetea campos de OTROS grupos — esa orquestación se queda en el VM).

### Hecho además — ETAPA 3 · manager 5/6: `TransitTeleportManager` ✅ (2026-07-04 sesión Opus 4.8, compilación PENDIENTE = ⏸️ CHECKPOINT #8)
Quinta aplicación de la RECETA combine. **CORTE:** el manager posee los **11 campos** UI de las
TRANSICIONES DE PANTALLA (todos de WorldMapState, los lee la UI); la ORQUESTACIÓN (teleport gate,
proximidad, repos IO, routing de puerta) se queda en el VM y delega. El `combine` pasó a **5 flows**
(overload tipado; con más habría que anidar).
- `viewmodel/TransitTeleportManager.kt` (NUEVO, LF): posee `TransitTeleportSubState` = 11 campos:
  `showTeleportMenu`; Metro (`metroStations`/`nearbyMetroStation`/`showMetroFade`/`metroFadeCompleteStation`);
  Metrobús (idem 4); Puerta ESCOM (`showEscomDoorFade`/`escomDoorFadeComplete`/`pendingDoorDestination`).
  Métodos puros: `setTeleportMenu`, `setMetro(bus)Stations`, `setNearbyMetro(bus)`, `beginMetro(bus)Fade`,
  `onMetro(bus)FadeComplete(): Boolean` (devuelve si disparó → el VM limpia el interactionPrompt de OTRO
  grupo), `consumeMetro(bus)FadeComplete`, `beginEscomDoorFade`, `onEscomDoorFadeComplete`,
  `consumeEscomDoorNavigation(): String?`, `clearTransitOnTeleport` (menú + fades/nearby; NO toca catálogos
  ni la puerta ESCOM).
- **NO migrado a propósito** (orquestación en el VM): `teleportTo` (gate del mundo, limpia NPCs, resetea
  compuertas + wanted + transit vía `clearTransitOnTeleport`), `teleportToLocation`, `checkCollectibleProximity`
  (proximidad mezclada con coleccionables + interactionPrompt temporizado), `handleInteraction`, los repos
  `MetroRepository`/`MetrobusRepository` (IO) y el routing `InteriorEntryCatalog` de la puerta.
- `WorldMapViewModel.kt`: `internal val transitTeleportManager = TransitTeleportManager()` + `combine` a
  **5 flows** con los 11 campos en `base.copy`. Los 4 miembros de fade-complete (`onMetroFadeComplete`/
  `consumeMetroFadeComplete`/`onMetrobusFadeComplete`/`consumeMetrobusFadeComplete`, llamados por la UI)
  delegan y limpian el interactionPrompt.
- Writers rerouteados (varios combinados con interactionPrompt/otros grupos → PARTIDOS):
  - `WorldMapTeleport.kt`: `toggleTeleportMenu`/load/teleportTo* delegan; el reset de `teleportTo` → un
    solo `clearTransitOnTeleport()` (se sacaron 7 campos del copy grande); reads de catálogos vía el manager.
  - `WorldMapCollectiblesLogic.kt` (`checkCollectibleProximity`): reads de catálogo/cercanía y set/clear de
    estación cercana → manager; el `interactionPrompt` temporizado con coroutine se queda en `_uiState`.
  - `WorldMapInteractions.kt`: `handleInteraction` (beginMetro(bus)Fade), puerta (`beginEscomDoorFade`),
    `teleportToLocation` (setTeleportMenu(false) fuera del copy).
  - `WorldMapShineCTO.kt`: `onEscomDoorFadeComplete` (delega + limpia interactionPrompt + collectibles),
    `consumeEscomDoorNavigation` → `manager.consumeEscomDoorNavigation()`.
- `WorldMapState.kt`: los 11 campos + `showTeleportMenu` anotados ⚠️ "LOS POSEE TransitTeleportManager".
- `TransitTeleportManagerTest.kt` (NUEVO, LF, 8 tests): estado inicial, menú, catálogos, set/clear cercanía,
  flujo de fade metro (solo dispara con estación cercana), flujo metrobús, flujo puerta ESCOM (begin→complete→
  consume), clearTransitOnTeleport (limpia menú/fades pero conserva catálogos y puerta). **Total esperado: 81 tests.**
- Verificado por grep: 0 `_uiState.value.<campo>` de los 11, 0 `it.copy(<campo>` fuera del manager+fachada.
  Edits verificados con Read; balance OK.

### ✅ CHECKPOINT COMPILACIÓN #8 — VERDE (confirmado por el dueño, 2026-07-04)
Rebuild OK + tests + metro/metrobús/teleport/puerta ESCOM OK (sin el bug de "salir del metro → metrobús").
Manager 5/6 (TransitTeleport) cerrado. **Siguiente: manager 6/6 `CampaignManager`** (el más enredado:
misiones 1-3, registro, replay; puede dividirse en 2 checkpoints). Con eso cierra la Etapa 3.

### Hecho además — ETAPA 3 · manager 6/6 · PARTE A: `CampaignManager` (registro) ✅ (2026-07-04 sesión Opus 4.8, compilación PENDIENTE = ⏸️ CHECKPOINT #9)
El grupo CAMPAÑA es el más enredado → se parte en 2 (como anticipaba el plan). **PARTE A = REGISTRO**,
autocontenido; **PARTE B = FASE DE MISIÓN**, pendiente (ver abajo).
- **Medición del blast radius (por eso se parte):** el REGISTRO (`showMissionLog`+`completedMissions`) tiene
  **3 writers** limpios; el estado de FASE (`currentObjective`/`objectiveDone`/`storyConvo*`/
  `pendingMission1ChaseIntro`/`mission3EnterEncb`/`campaignRouteWaypoints`/`showMissionFailed`) tiene **~26
  writes en 8 archivos**, escritos en casi CADA tick de misión (WorldMapMission2/3.kt, WorldMapCampaignPolice.kt,
  WorldMapSaveGame.kt…) → entrelazado con el GAME LOOP.
- `viewmodel/CampaignManager.kt` (NUEVO, LF): posee `CampaignSubState` = 2 campos (`showMissionLog`,
  `completedMissions`). Métodos puros: `setShowMissionLog`, `isCompleted`, `markCompleted` (IDEMPOTENTE —
  regla dura del REJUGAR), `setCompletedMissions` (restaurar guardado).
- `WorldMapViewModel.kt`: `internal val campaignManager = CampaignManager()`. **El `combine` llegó al límite
  de 5 flows tipados → se ANIDÓ:** el 5º argumento combina `(transit, campaign)` en un `Pair` y el lambda lo
  DESESTRUCTURA (`val (transit, campaign) = transitAndCampaign`). Sigue 100% tipado, sin casts. Los 2 campos
  se vuelcan en `base.copy`.
- Writers/reads rerouteados:
  - `WorldMapMissionLog.kt`: `toggleMissionLog`→`setShowMissionLog`; `missionLogStatus` usa
    `campaignManager.isCompleted(...)`; `markMissionCompleted`→`markCompleted` (el reset de `replayingMissionId`
    se queda). La ORQUESTACIÓN (`selectCampaignMission`/`replayCampaignMission`/`unfollowActiveMission`/
    `endMissionReplay`/`devTeleportToMissionObjective`) NO se toca — lee/escribe el objetivo de FASE (VM).
  - `WorldMapSaveGame.kt`: `buildSaveData` lee `campaignManager.isCompleted(...)` + `.state.value.completedMissions`
    (⚠️ ANTES leía `_uiState.value` → con la fachada daría lista vacía); `restoreSaveData` →
    `campaignManager.setCompletedMissions(restoredCompleted)` (sacado del copy).
- `WorldMapState.kt`: los 2 campos anotados ⚠️ "LOS POSEE CampaignManager (Parte A)".
- `CampaignManagerTest.kt` (NUEVO, LF, 5 tests): inicial, toggle, markCompleted idempotente, isCompleted,
  setCompletedMissions reemplaza. **Total esperado: 86 tests.**
- Verificado por grep: 0 `_uiState.value.(showMissionLog|completedMissions)`, 0 `it.copy(<campo>` fuera del
  manager+fachada. Edits verificados con Read; combine anidado balanceado.

### ✅ CHECKPOINT COMPILACIÓN #9 — VERDE (confirmado por el dueño, 2026-07-04)
Rebuild OK + tests + registro (seguir/dejar de seguir/rejugar/completar; guardar-cargar conserva
completadas) OK. Manager 6/6 · Parte A (CampaignManager · registro) cerrado.

### ✅ ETAPA 3 · manager 6/6 · PARTE B — CORTE LIMPIO (decisión del dueño, 2026-07-04)
El estado de FASE de misión (`currentObjective`/`objectiveDone`/`storyConvoSpeaker`/`storyConvoText`/
`pendingMission1ChaseIntro`/`mission3EnterEncb`/`campaignRouteWaypoints`/`showMissionFailed`) **NO se migra
a un manager**: se queda en el VM (parciales `WorldMapCampaign.kt`/`WorldMapMission2.kt`/`WorldMapMission3.kt`/
`WorldMapCampaignPolice.kt`). **Razón (corte limpio > dogma):** son ~26 writes en 8 archivos, escritos en casi
CADA tick de la máquina de misiones (game loop). Mover esos campos tras una fachada NO baja el acoplamiento
real — la lógica de misión SEGUIRÍA siendo orquestación del VM (lee/escribe posición, NPCs, policía, red,
Prankedy, save) y solo delegaría los writes, añadiendo indirección sin desenganchar nada. El objetivo del
programa es "acoplamiento bajo REAL, no el 100% de campos migrados" (regla transversal). Documentado también
en 09 §Managers. **La Parte A (registro) sí valía**: era estado UI autocontenido con 3 writers.

### 🏁 ETAPA 3 — CERRADA (2026-07-04)
6/6 managers con sub-estado propio detrás de la fachada `combine` (anidada): Designer ✅, Collectibles ✅,
Combat ✅ (delegación Compose), Wanted ✅, TransitTeleport ✅, Campaign·registro ✅ (+ fase = corte limpio
documentado). 86 tests en verde. **Siguiente: ETAPA 4 · Hilt** (`PLAN_DI_hilt.md`, 1 VM por PR,
WorldMapViewModel AL FINAL).

### Hecho — ETAPA 4 · DI con Hilt (2026-07-04 sesión Opus 4.8, compilación PENDIENTE = ⏸️ CHECKPOINT #10)
⚠️ **A PETICIÓN EXPRESA DEL DUEÑO se hizo TODA la etapa de una vez (no 1 VM por PR); él compila al final.**
Hilt es 100% codegen KSP → NO se pudo validar sin compilar. Migradas las **9 VMs** + infra:
- **Gradle:** `libs.versions.toml` (hilt=`2.57.1` ⚠️ CONFIRMAR/ajustar en el 1er sync a la matriz compatible
  con Kotlin 2.2/KSP 2.3.2/AGP 9; hilt-navigation-compose=`1.2.0`); plugin `com.google.dagger.hilt.android`
  en root (apply false) y `app/`; deps `hilt-android` + `ksp(hilt-compiler)` + `hilt-navigation-compose`.
- **App/Activity:** `@HiltAndroidApp` en `PowApplication`; `@AndroidEntryPoint` en `MainActivity` (los 3
  `by viewModels()` perdieron su Factory → Hilt los provee; WorldMapVM sigue **Activity-scoped**).
- **Módulo `di/AppModule.kt`** (`@InstallIn(SingletonComponent)`): provee `PowDatabase` (vía su getInstance),
  `RoadNetworkCache`/`TileCache`/`CollectibleRepository` (de sus DAOs) y `SettingsRepository`/`CampaignRepository`/
  `SaveGameRepository` (`@ApplicationContext`). SoundManager/OverpassRepository NO (singleton getInstance / interno).
- **6 VMs de deps inyectables → `@HiltViewModel @Inject`** (Factory borrado): `SettingsViewModel`,
  `CollectiblesViewModel`, `StoryModeViewModel`, `MainMenuViewModel`, `ShineCTOViewModel` (AndroidViewModel),
  `WorldMapViewModel` (AndroidViewModel; el post-init `deviceTierFactor`/`userPopulationFactor` del viejo
  Factory se movió a un `init{}` DESPUÉS de declarar `npcAiManager`). Call-sites → `hiltViewModel()` /
  `by viewModels()`; `WorldMapScreen` sigue recibiendo el VM Activity-scoped desde AppNavGraph (default
  `hiltViewModel()` solo fallback).
- **3 VMs con args de navegación → `@HiltViewModel(assistedFactory=…)` + `@AssistedInject` + `@AssistedFactory`**:
  `InteriorViewModel` (@Assisted `collisionGrid`; 10 pantallas), `TransitInteriorViewModel` (config/station/
  spawnX/spawnY; los 2 Float con qualifier `"spawnX"/"spawnY"`; 2 pantallas), `ZombieInteriorViewModel`
  (8 @Assisted; qualifiers en los 3 String y 3 Boolean; 1 pantalla). Pantallas →
  `hiltViewModel<VM, VM.Factory>(creationCallback = { it.create(args) })`.
- Verificado por grep: 0 `.Factory(` vivos, 0 `viewModel(factory=`, 0 subclases de `InteriorViewModel`.
  Quedan imports/vals sin uso (`viewModel`, `context`) → SOLO warnings, no rompen el build.

### ✅ CHECKPOINT COMPILACIÓN #10 — VERDE (confirmado por el dueño, 2026-07-04)
Rebuild OK (KSP generó los componentes Hilt) + tests + arranque de la app + pantallas migradas OK.
**ETAPA 4 · Hilt CERRADA.** Las 9 VMs vía Hilt (@HiltViewModel / @AssistedInject); WorldMapVM Activity-scoped
sin regresión de recarga. **Siguiente: ETAPA 5 · detekt baseline** (PENDIENTE_calidad.md).

### ⏸️ (histórico) CHECKPOINT COMPILACIÓN #10 — riesgos vigilados (ETAPA 4 · Hilt COMPLETA)
**RIESGOS a vigilar al compilar (por orden de probabilidad):**
1. **Versión de Hilt:** si el sync/compilación se queja de compat con Kotlin 2.2/KSP 2.3.2/AGP 9, subir
   `hilt` en `libs.versions.toml` a la última de la matriz Hilt del momento (sigue por KSP, NO añadir kapt).
2. **API de `hiltViewModel(creationCallback)`** (assisted): requiere `hilt-navigation-compose` ≥ 1.2.0. Si la
   firma difiere, ajustar la llamada `hiltViewModel<VM, VM.Factory>(creationCallback = { it.create(...) })`.
3. **Qualifiers @Assisted**: los nombres del constructor y del `@AssistedFactory.create` DEBEN coincidir
   (Transit: spawnX/spawnY; Zombie: serverUrl/playerName/startRoomId/lab1KeyFound/firearmUnlocked/mission3Assault).
4. **Runtime (compila pero probar):** que volver de Ajustes NO recargue el mapa (WorldMapVM Activity-scope OK).
Pedir al dueño: Rebuild (KSP genera componentes) + `testDebugUnitTest` (86) + arrancar la app y entrar a CADA
tipo de pantalla migrada (menú, ajustes, historia, coleccionables, mapa, interiores ESCOM, metro/metrobús,
zombis, ShineCTO). Hilt falla en COMPILACIÓN con mensajes claros si falta un binding.

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
2. ~~**CollectiblesManager**~~ ✅ HECHO 2026-07-04 (Opus 4.8), pendiente CHECKPOINT #5. Se movió SOLO
   el sub-estado UI (`activeCollectibles`/`nearbyCollectible`/`showClaimedPopupFor`); `_escomItems`,
   `isZombieHandSpawned`, `isSpawningCollectible` se quedaron en el VM (no-UI). Ver "Hecho además" arriba.
3. ~~**CombatManager**~~ ✅ HECHO 2026-07-04 (Opus 4.8), pend. CHECKPOINT #6. Estado vida/💥 (Compose
   mutableStateOf) movido al manager con **miembros delegantes** en el VM (Views intactas); la LÓGICA
   de combate/vida y el temporizador se quedaron en el VM. Ver "Hecho además" arriba. **Lección para
   4-6:** si el estado del grupo es Compose-directo (no WorldMapState), usa delegación get/set en vez
   de la fachada combine.
4. ~~**WantedManager**~~ ✅ HECHO 2026-07-04 (Opus 4.8), pend. CHECKPOINT #7. Fachada combine (3 campos
   `wantedLevel`/`carjackWarning`/`policeShots`) + timers + constantes en el manager; la orquestación
   (`runPoliceTick`/`handleCarjack`/`anyAggressorAdjacent`/`forceExitVehicle`) se quedó en el VM y delega.
   Ver "Hecho además" arriba. **Lección para 5-6:** cuando el grupo tiene MUCHOS writers entrelazados con
   otros grupos (WASTED/teleport/abordar/campaña), se PARTEN los `it.copy` combinados (manager + base) —
   como en Collectibles — y se rerutea CADA read síncrono interno (ojo `buildSaveData` leía `_uiState.value`).
5. ~~**TransitTeleportManager**~~ ✅ HECHO 2026-07-04 (Opus 4.8), pend. CHECKPOINT #8. Fachada combine (11
   campos: menú TP + metro + metrobús + puerta ESCOM); `combine` a 5 flows. La orquestación (`teleportTo`
   con su reset multi-grupo vía `clearTransitOnTeleport`, proximidad, repos IO) se quedó en el VM. Ver
   "Hecho además" arriba. **Lección para 6:** los fade-complete devuelven Boolean para que el VM limpie los
   campos de OTROS grupos (interactionPrompt); `clearTransitOnTeleport` NO toca catálogos ni la puerta ESCOM.
6. **CampaignManager** — el más enredado (misiones 1-3, registro, replay). Se divide en 2:
   - ✅ **PARTE A HECHA 2026-07-04 (Opus 4.8)**, pend. CHECKPOINT #9: registro (`showMissionLog`+
     `completedMissions`) en el manager; `combine` anidado (6º flow vía Pair). Ver "Hecho además".
   - ⏳ **PARTE B (pendiente):** estado de FASE de misión (~26 tick-writers). Decisión en el verde de #9:
     migrar en checkpoint dedicado O documentar corte limpio (dejarlo en el VM). **Lección:** el `combine`
     ya está al límite anidado; si se migran más campos, van al combine interno `(transit, campaign, …)`.
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
