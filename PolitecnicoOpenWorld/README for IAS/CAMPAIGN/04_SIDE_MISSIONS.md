# CAMPAIGN · 04 · Misiones SECUNDARIAS / Side quests (side1, side2)

> **Logline:** las primeras misiones SECUNDARIAS del registro (badge SECUNDARIA,
> `CampaignMissionInfo.side=true`). Cortas, de mundo abierto, con recompensa en **DINERO**
> (economía nueva, 2026-07-08). Coherentes con la escalada de la infección (regla rectora).
>
> Fuente: `domain/models/campaign/side/SideMissions.kt` (coords/objetivos/constantes) +
> `viewmodel/WorldMapSideMissions.kt` (tick). Implementadas 2026-07-08. ⚠️ Pendiente Rebuild.

## 1. Diseño común

- **SIN fase persistida:** el ID del objetivo activo ES el estado (se guarda como cualquier
  objetivo en `GameSaveData.objectiveId`; `MissionCatalog.byId` resuelve `s1_*`/`s2_*`).
  Dejar de seguir a medias = al re-seguir se REINICIA (son cortas; decisión de diseño).
- **Sin "MISIÓN FALLIDA":** morir respawnea normal y la secundaria continúa.
- **Recompensa:** `MissionRewards` (side1 $150, side2 $250) pagada por `markMissionCompleted`
  SOLO la primera vez (replays no duplican). Las principales también pagan (M1 $200, M2 $300, M3 $500).
- **Actores:** `sideMissionNpcs` (prefijo `SM_`, escenografía) fusionados por `updateNpcsState`;
  EXCLUIDOS del snapshot de guardado (prefijos `SM_`/`SMZ_` en `buildSaveData`).

## 2. side1 · "Suministros médicos" (requiere Misión 2)

Tras el rumor y el primer brote, un paramédico necesita material. Dos objetivos POR LLEGADA
(radio 25 m → los cumple `checkObjectiveProgress`; el tick solo encadena):

| # | id | Qué pasa |
|---|---|---|
| 1 | `s1_recoger_botiquin` | Ir al punto de recogida (`S1_PICKUP_*`, NO de la ESCOM). |
| 2 | `s1_entregar_botiquin` | Llevarlo al paramédico (`S1_DELIVER_*`, ~1 km → invita a conducir). El paramédico espera ahí (NPC `SM_PARAMEDICO`, civil con "bata blanca" — sin asset premade exterior aún, misma limitación que los granaderos M3) con burbuja 💬. |

## 3. side2 · "Contención en Zacatenco" (requiere Misión 3)

La infección se extiende: reportan infectados sueltos al oeste del campus.

| # | id | Qué pasa |
|---|---|---|
| 1 | `s2_ir_zona` | Llegada a `S2_ZONE_*` (radio 35 m). |
| 2 | `s2_eliminar_infectados` | Se siembran **5 zombis REALES** (`NpcAiManager.SIDE_ZOMBIE_PREFIX` = `SMZ_`, 35 HP) en `remoteEntities` → **atacables** con B y simulados por el mover zombi **sin apocalipsis** (gate ampliado en `NpcAiManager.updateNpcs`; ver 09). El tick cuenta vivos ("🧟 Quedan N…"); 0 vivos → completada. Idempotente: al CARGAR en esta fase se re-siembran. |

## 4. Datos técnicos

Constantes en `SideMissions.kt`: `S1_PICKUP` (19.50840, -99.14930) · `S1_DELIVER`
(19.49890, -99.13980) · `S2_ZONE` (19.50230, -99.15120) · `S2_ZOMBIE_COUNT=5` /
`S2_ZOMBIE_HEALTH=35` / `TRANSITION_MS=2000`. Strings: `side1/2_title/_desc`,
`obj_s1_*`, `obj_s2_*`, `mlog_side_badge` (ES+EN). Registro: entradas `side1`/`side2` en
`MissionCatalog.missions` (requieren mission2/mission3); `selectCampaignMission`/
`replayCampaignMission`/`endMissionReplay` con ramas side; limpieza en el `else` del game
loop y en `setStorySpawn` (`clearSideMissions`).
