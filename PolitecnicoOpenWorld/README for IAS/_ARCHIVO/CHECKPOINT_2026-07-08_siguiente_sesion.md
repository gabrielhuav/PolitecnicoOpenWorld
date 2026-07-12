# CHECKPOINT 2026-07-08 + PROMPT para la SIGUIENTE SESIÓN (otro LLM / otra cuenta)

> **Para qué:** retomar el trabajo EXACTAMENTE donde quedó esta sesión (Fable 5, tokens limitados).
> (A) checkpoint de lo implementado hoy, (B) advertencias/estado, (C) PROMPT listo para copiar/pegar.

---

## A. CHECKPOINT — qué se implementó (2026-07-08)

Detalle completo en los docs actualizados (09 §nuevos bullets, 04, 05, CAMPAIGN/02, README público).
Índice:

1. **MISIÓN 2 · FASE 1 "ESCONDERSE" REDISEÑADA — ahora DENTRO del lobby de la ESCOM** (petición
   del dueño: la persecución se sentía fuera de lugar en el mapa global).
   - EXTERIOR (`WorldMapMission2.kt`): `tickM2Hide` ELIMINADO; el `when` de `PHASE_HIDE` queda
     vacío; nuevas `completeMission2Hide()` (avanza a RUMOR + 🎯) y `failMission2Hide()`
     (showMissionFailed). El 🎯 de `m2_esconderse_policia` apunta a la puerta de la ESCOM.
   - INTERIOR: policías `m2cop_*` (skin `POLICIA_CDMX`) dentro de `ambientNpcs`.
     `spawnMission2HideCops`/`stepMission2HideCops` (**ZombieAmbientNpcs.kt**), detección +
     countdown + rendición/evacuación (**ZombieGameTick.kt**), armado RUNTIME
     `setMission2Hide(enabled)` + vars transitorias (**ZombieInteriorViewModel.kt**), flags
     `mission2Hide*` (**ZombieGameState.kt**), HUD countdown + LaunchedEffects
     (**ZombieGameScreen.kt**), cálculo de `mission2Hide` + callbacks (**AppNavGraph.kt**).
   - Constantes nuevas en píxeles (**Mission2.kt**): `HIDE_DETECT_PX=120`, `HIDE_DETECT_MS=2500`,
     `HIDE_DURATION_MS=35000`, `HIDE_COP_SPEED_PX=3.4`, `HIDE_SWEEP_EVERY_MS=7000` (las viejas
     `HIDE_*_DEG` se eliminaron).
   - Strings: `obj_m2_esconderse_desc` reescrito, `zgame_hide_countdown`, `amb_cop_search_1/2`,
     `amb_cop_alert` (ES+EN).
2. **"TP al objetivo" (Modo Dev) ARREGLADO** (`WorldMapMissionLog.kt` + `MissionLogDialog.kt`):
   ahora SIGUE la misión primero (`selectCampaignMission(force=true)`, o `replayCampaignMission`
   si está ✔) y teletransporta al objetivo de la FASE actual + prompt "🧪 TP al objetivo: …".
   En interiores: botón deshabilitado + pista `mlog_tp_exit_first` (ES+EN); extensión no-op si
   `currentInteriorRoomId != null`. (Síntoma que reportó el dueño: "te hace tp según pero te
   quedas ahí mismo" — le pasaba al usarlo desde un interior.)
3. **VIDA ESCOM (interior)** (`ZombieAmbientNpcs.kt`): `ambientCountFor(room)` (lobby 13, salón
   M2 8, default 7); pláticas con GUIONES COHERENTES (`AMBIENT_CONVOS`, 6 guiones de 4 líneas +
   3 cortos con frases clásicas; determinista por `pairSeed`, línea por `AmbientNpc.talkStartMs`
   nuevo campo); 2 parejas nacen platicando en el lobby; `PAIR_CHANCE_PER_TICK` 0.004→0.009;
   emparejador ignora `m2cop_*`. Strings `amb_convo{1..6}_{1..4}` (ES+EN).
4. **VIDA ESCOM (exterior)**: **`WorldMapCampusLife.kt` (NUEVO)** — ~10 estudiantes `CAMPUS_*`
   en el bbox de la ESCOM: deambulan por cubetas de 14 s + corrillo de 3 platicando por ventana
   de 45 s (todo determinista, sin estado por NPC). Cableado: campo `campusNpcs` +
   `runCampusLifeTick(location)` en el game loop (**WorldMapViewModel.kt**), fusión en
   `updateNpcsState` (**WorldMapMultiplayer.kt**), exclusión `CAMPUS_` en `buildSaveData`
   (**WorldMapSaveGame.kt**). Pausado en escolta/chase M1 y zombi global.
5. **Docs sincronizados**: 09 (2 bullets nuevos 2026-07-08 + nota TP), 04 (tabla Key files),
   05 (fase 1 lobby + vida 2.0), CAMPAIGN/02 (fase 1 reescrita), README público (EN+ES).

## B. ADVERTENCIAS / ESTADO

- **NADA se ha compilado** (la sesión no tuvo compilador): falta `Rebuild Project` en Android
  Studio + prueba en dispositivo. Puntos a vigilar al compilar:
  - `ZombieGameTick.kt`: usa `hypot` + `M2COP_PREFIX` (mismo paquete) + alias `m2c`.
  - `MissionLogDialog.kt` lee `viewModel.currentInteriorRoomId` (internal, mismo módulo).
  - `WorldMapCampusLife.kt`: nueva extensión `campusSlotOf` (NO llamarla `indexOf`).
- Quedan pendientes de sesiones anteriores (aún sin commit): gate dev de la mano zombi del lobby,
  skin dialogs, matriz de colisiones — ver `git status`.
- QUÉ PROBAR en dispositivo: (1) M1 completa → seguir M2 desde el registro estando DENTRO del
  lobby → deben entrar 4 policías; aguantar 35 s → rendición → salir → 🎯 rumor. (2) Dejarse ver
  de cerca → MISIÓN FALLIDA → REINTENTAR → re-entrar al lobby re-arma. (3) TP al objetivo con
  Modo Dev en cada misión (desde el MAPA). (4) Lobby: parejas platicando con guiones coherentes.
  (5) Campus: corrillos de 3 con 💬. (6) Guardar/cargar en fase 1 dentro del lobby.

## C. PROMPT PARA LA SIGUIENTE SESIÓN (copiar/pegar tal cual)

```
Lee la carpeta `README for IAS` (contexto COMPLETO del proyecto POW) y en especial
`CHECKPOINT_2026-07-08_siguiente_sesion.md` (estado actual). Sigue MVVM y TODAS las
convenciones/gotchas del archivo 09 (miembro vs extensión, CRLF, verificación con Read
—no bash— de archivos editados, protocolo de docs al terminar, strings ES+EN con paridad,
no puedes compilar: entrega listo para Rebuild).

CONTEXTO INMEDIATO: el 2026-07-08 se rediseñó la fase 1 de la Misión 2 (ahora se juega
DENTRO del lobby de la ESCOM con policías m2cop_*), se arregló "TP al objetivo" del modo
dev (ahora sigue la misión primero; deshabilitado en interiores) y se añadió vida a la
ESCOM (lobby: 13 NPCs + guiones de plática coherentes; campus: WorldMapCampusLife.kt).
NADA está compilado aún.

TAREAS (en este orden):
1. Si el dueño reporta errores del Rebuild, arréglalos primero (ver "puntos a vigilar"
   del checkpoint B).
2. <SIGUIENTE TAREA QUE PIDA EL DUEÑO>

AL TERMINAR: aplica el PROTOCOLO DE DOCS del 09 (docs 00-09 + CAMPAIGN + README público
EN+ES) y dime qué probar en el dispositivo.
```
