# PLAN — Descomponer el god-object `WorldMapViewModel` (managers/use-cases con sub-estado)

> Estado: TRABAJO FUTURO (requiere compilador). Sesión de noche 2026-06-24. Roadmap ANALISIS §9.2.

## 0. Diagnóstico
- `WorldMapViewModel.kt` ~1.4k líneas (~86 KB) + **~29 parciales** `WorldMap*.kt` que son **EXTENSIONES de
  la MISMA clase**. La separación bajó el TAMAÑO pero NO el ACOPLAMIENTO: todo comparte UN `_state`
  (`WorldMapState`, **111 campos** en ~20 secciones) y los internals del VM.
- YA existen managers de dominio bien hechos (patrón a replicar): `NpcAiManager`, `PoliceManager`,
  `PrankedyManager`, `CampaignEscortPolice`, `OverpassRepository`, `TilePrefetchManager`,
  `WebSocketManager`. El VM los compone. La meta es llevar el RESTO de la lógica del `_state` gigante a
  managers/use-cases con **su propio sub-estado**, e inyectarlos.

## 1. Meta
El VM solo **orquesta y compone**: mantiene los managers, conecta eventos y EXPONE `uiState` combinando
los sub-estados. La lógica vive en managers testeables (idealmente puros o con deps inyectadas).

## 2. La técnica clave: `WorldMapState` como FACHADA combinada (migración sin tocar las Views)
Hoy las Views observan `uiState: StateFlow<WorldMapState>`. Para migrar SIN romperlas:
- Cada manager expone `StateFlow<XSubState>`.
- El VM construye `uiState` con `combine(combatState, navState, collectiblesState, ...) { ... ->
  WorldMapState(...) }` (o mantiene `WorldMapState` como agregador de sub-estados). La FORMA que ve la UI
  no cambia -> se puede migrar grupo por grupo sin tocar `WorldMapScreen*`/overlays.
- Cuando todos los grupos estén fuera, `WorldMapState` queda como simple composición (o se elimina a favor
  de sub-estados expuestos por separado).

## 3. Mapa de extracción (grupo de estado -> manager + sub-estado)
| Manager / UseCase nuevo | Parciales origen | Sub-estado (campos de WorldMapState) |
|---|---|---|
| `CombatManager` | WorldMapCombat.kt | playerHealth, impactEffectTrigger, FX/throttle de impacto |
| `RoutingUseCase` (RoadRouter, ver PLAN_dedup_routing) | WorldMapRouting.kt | NAVEGACIÓN/MARCADOR DE DESTINO |
| `CollectiblesManager` | WorldMapCollectiblesLogic.kt, WorldMapEscomItems.kt | coleccionables activos, popup, items ESCOM |
| `CampaignManager` | WorldMapCampaign.kt, WorldMapCampaignPolice.kt, WorldMapPrankedy.kt, WorldMapSaveGame.kt (parte historia) | objetivo de campaña, showMissionContinueDialog, pendingResumeMissionId, escolta |
| `WantedManager` (envuelve PoliceManager) | WorldMapWanted.kt | NIVEL DE BÚSQUEDA (estrellas), carjack |
| `TransitTeleportManager` | WorldMapTeleport.kt, WorldMapEscom.kt | Metro/Metrobús stations, ESCOM door transition, teleport |
| `DesignerManager` (dev-only) | WorldMapDesigner.kt, WorldMapDebugEditor.kt | MODO DISEÑADOR, EDITOR DEBUG INTERIORES |
| `ProvidersManager` | WorldMapProviders.kt, WorldMapCameraUi.kt | zoom de juego, proveedor de tiles, cámara |
| (ya managers) | NpcAiManager/PoliceManager/Prankedy/Transit | — |

## 4. Orden incremental (1 manager por PR, detrás de la fachada combinada)
Empezar por lo MÁS AISLADO (menos cruces con el resto):
1. **`DesignerManager`** (dev-only, no toca el game loop del jugador) — riesgo mínimo, prueba la técnica
   de combine().
2. **`CollectiblesManager`** (entradas/salidas claras; ya tenía gemelos de-dup).
3. **`CombatManager`** (depende de NpcAiManager/PoliceManager pero la API es acotada).
4. **`WantedManager`** y **`TransitTeleportManager`**.
5. **`CampaignManager`** (más enredado con misión/persecución).
6. **`RoutingUseCase`** AL FINAL — depende de `PLAN_dedup_routing.md` (la cadena NO-TOCAR). Hasta entonces
   `startGameLoop` (~490 líneas) se queda como está porque LLAMA a esa cadena.

Por cada paso: crear `XManager` con `MutableStateFlow<XState>`; MOVER la lógica del parcial + los campos
del `WorldMapState` a `XState`; en el VM, instanciar el manager y recomponer `uiState` con `combine`;
**borrar** la lógica/los campos viejos del VM (no dejar gemelos). Compilar + test + prueba manual.

## 5. Verificación
- Sin compilador: balance Kotlin-aware por archivo, EOL CRLF, 0 refs colgantes, grep de que no quedan
  campos/funciones duplicados.
- Con compilador: Rebuild + `testDebugUnitTest` (los managers extraídos ya admiten tests unitarios — ese
  es medio punto del refactor) + prueba manual del flujo afectado.
- MVVM: las Views siguen SOLO observando `uiState` y emitiendo intenciones; ningún acceso nuevo a repos
  desde `ui/`.

## 6. Qué necesita compilador (no se hace de noche)
Casi todo: mover campos de estado, introducir `combine`, reescribir los call-sites de `_state.update` en
~29 parciales, e inyectar managers. Es exactamente el tipo de cambio que sin compilador deja el build roto
toda la noche. Por eso aquí queda el PLAN; el primer paso concreto y aislado (`DesignerManager`) está listo
para hacerse en Android Studio.

## 7. Gotchas a respetar (09 §12)
- **No crear NUEVOS gemelos miembro-vs-extensión.** Al mover lógica a un manager, ELIMINA el miembro/parcial.
- Estado siempre inmutable: `_state.update { it.copy(...) }` (o el del sub-estado).
- Cuidado con la truncación del sandbox al tocar el VM (~86 KB): editar en AS y verificar la COLA/cierre de
  clase tras cada cambio (la herramienta Read ve el archivo real; bash sirve copias truncadas).
- Inyección: ver `PLAN_DI_hilt.md` (los managers nuevos encajan como `@Inject`/módulos cuando llegue Hilt).
