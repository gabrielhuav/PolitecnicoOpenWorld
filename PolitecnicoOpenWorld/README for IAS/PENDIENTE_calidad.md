# PENDIENTE — Deuda de calidad detekt + camino a gate BLOQUEANTE

> Generado 2026-06-24 tras el primer run de `pr-quality-gate.yml`. detekt corre ahora en modo
> ADVISORY (`continue-on-error: true`): reporta pero NO bloquea el merge. Los tests SI bloquean.
> Aqui queda la deuda y como volver detekt bloqueante cuando se limpie (o con baseline).

## Estado
- `detekt` (CLI 1.23.8, `--build-upon-default-config` + `config/detekt/detekt.yml`) reporta **~76 issues**
  PREEXISTENTES (no de los tests de esta tanda). Por eso es advisory: bloquear por deuda vieja frenaria
  todo PR. Plan: quemar la deuda por categoria (PRs chicos) y/o commitear un baseline, luego bloquear.

## Como volver detekt BLOQUEANTE (cuando quieras)
Opcion A — Baseline (rapido, recomendado para adoptar el linter sin limpiar todo ya):
1. Genera el baseline UNA vez (perdona lo actual, exige el estandar solo a codigo NUEVO):
   `./detekt-cli-1.23.8/bin/detekt-cli --create-baseline --baseline PolitecnicoOpenWorld/config/detekt/baseline.xml --config PolitecnicoOpenWorld/config/detekt/detekt.yml --build-upon-default-config --input PolitecnicoOpenWorld/app/src/main/java`
2. Commitea `config/detekt/baseline.xml` (el workflow ya lo usa si existe).
3. Quita `continue-on-error: true` del job `detekt` en `.github/workflows/pr-quality-gate.yml`.
Opcion B — Limpia la deuda (abajo) y luego quita `continue-on-error: true` sin baseline.

## Deuda por categoria (76 issues) — prioridad sugerida

### ALTA (corrige; son señal real)
- **PrintStackTrace (14)** -> reemplazar `e.printStackTrace()` por `Log.w(TAG, msg, e)`:
  TransitInteriorViewModel (469,483,491,500,508,520), VehicleSpriteManager:49, PoliceSpriteManager:43,
  MetroRepository:40, MetrobusRepository:32, y 1 mas en Transit (107? ver run).
- **Codigo MUERTO — UnusedPrivateMember/Property/Parameter (~18)** -> borrar:
  - **WorldMapViewModel:1286 `rebuildRoadNodeGrid` (MIEMBRO sin uso) = confirma el GEMELO de routing**
    (ver `PLAN_dedup_routing.md`: el miembro esta muerto, gana la extension). Util como prueba del smell.
  - WorldMapViewModel:607 `isGoingVeryFast`, :1257 `step`; WorldMapRouting:203 `step`;
    WorldMapCollectiblesLogic:97 `INTERACT_RADIUS_METERS`; NpcAiManager:540 `g`;
    NpcAiManagerTraffic:95 `newTarget`; WorldMapScreen:332 `hasTriggeredNativePan`;
    ShineCTOViewModel `drinkSafeZones`; ShineCTOScreen `pSizeDp`,`shape`;
    PrankedyManager param `now` (458,552,555); WorldMapCampaignPolice params `playerLoc`/`door`;
    WorldMapEscomItems params `roadNetwork`/`cantidad`; WorldMapWidgets param `context`.

### MEDIA (trivial, bajo riesgo)
- **MayBeConst (5)** -> `val` -> `const val`: ZombieGameScreen (116,117), MetrobusStationInteriorScreen:61,
  MetroStationInteriorScreen:66, PrankedySpriteManager (67,68,69).
- **EmptyCatchBlock (8)** -> nombrar la excepcion (`catch (_: Exception)`) o loguear:
  TransitInteriorViewModel (85,107,119,128), MetroStationInteriorScreen (96,101,106).
- **ImplicitDefaultLocale (3)** -> `String.format(Locale.US, ...)`: Designerpanel (192,229), ObjectivesWidget:59.
- **UseRequire (2)** -> `require(...)` en vez de `throw IllegalArgumentException`: SettingsViewModel:148,
  WorldMapViewModel:118.
- **FunctionOnlyReturningConstant (2)** -> declarar constante: PrankedyManager (552 isHireable, 555 hireableInSeconds).
- **ComplexCondition (1)** -> extraer/simplificar o subir umbral: CachingWebViewClient:118.

### BAJA (ruido de estilo en un juego; considera DESACTIVAR la regla en detekt.yml)
- **LoopWithTooManyJumpStatements (~22)** -> los game-loops/movers legitimamente usan varios break/continue.
  Si molesta, `LoopWithTooManyJumpStatements: active: false` en detekt.yml (recomendado para este codebase).
  Archivos: NpcAiManager (333,819), NpcAiManagerTraffic (286,489), NpcAiManagerMovement:171, PoliceManager
  (103,260), CampaignEscortPolice (104,236), ZombieGameTick (108,181), WorldMapRouting (203,301,308),
  WorldMapViewModel:1257, WorldMapWanted:48, WorldMapCombat (234,314,355), CharacterSpriteManager:225,
  InteriorDebugDrawSurface:92, VehicleSpriteManager:76, WorldMapScreen:509, OverpassRepository:135.

## Notas
- Varios "Unused* / params no usados" salen de firmas de interfaces/overrides o de refactors a medias; al
  borrarlos revisa que no rompan un contrato (compilador). Hazlo en PRs pequeños y verificables.
- El `rebuildRoadNodeGrid` muerto NO lo toques aislado: entra en `PLAN_dedup_routing.md` (cadena NO-TOCAR).
