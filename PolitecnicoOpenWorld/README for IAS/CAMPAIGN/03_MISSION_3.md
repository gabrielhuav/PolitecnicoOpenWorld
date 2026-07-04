# CAMPAIGN · 03 · Misión 3 — "Regreso a la ENCB" / Mission 3 — "Return to the ENCB"

> **Logline:** Tras el llamado de refuerzos, la ENCB es **zona de cuarentena**: granaderos
> acordonan el perímetro y los paparazzi husmean afuera. El jugador se **infiltra evadiendo el
> cordón** (sigilo), entra a la cadena de salas de la ENCB — ahora **con zombis** (primer combate
> interior de campaña) — y recupera la **evidencia del laboratorio**. Recompensa: la **PRIMERA
> ARMA DE FUEGO** (desbloquea el modo A DISTANCIA en interiores de campaña).

> Fuente: `mission3/Mission3.kt` (constantes/objetivos), `WorldMapMission3.kt` (fases exteriores),
> motor de interiores (asalto). Implementada 2026-07-04. Estado: ✅. Se SIGUE desde el REGISTRO DE
> MISIONES (Opciones → "Misiones"); requiere Misión 2 completada.

## 1. Fases / Phases

| # | Fase (`mission3Phase`) | Objetivo (id) | Mecánica |
|---|---|---|---|
| 1 | `PHASE_TRAVEL` | `m3_ir_encb` | 🎯 a la ENCB. A <~100 m (`APPROACH_DEG`) se ARMA el cordón → fase 2. |
| 2 | `PHASE_INFILTRATE` | `m3_infiltrarse` | 6 granaderos (`M3_GRANADERO_*`, `POLICE_COP` — ver nota de render) patrullan un ANILLO (~60 m) alrededor de la ENCB con arcos deterministas por cubeta de 5 s; 3 paparazzi (`M3_PAPARAZZI_*`, civiles con burbuja 💬 intermitente) merodean afuera. **SIGILO:** a <~21 m de un granadero por >2.5 s → detectado → **MISIÓN FALLIDA** (retry: `RETRY_SPAWN_*` = MISSION1_SPAWN, reinicia desde fase 1). Llegar al CENTRO (<~15 m) → jingle + `mission3EnterEncb=true` (la View navega a `interiores_zombies?startRoom=encb_lobby`) → fase 3. |
| 3 | `PHASE_ASSAULT` | `m3_recuperar_evidencia` | INTERIOR: la cadena ENCB se siembra con **4 zombis por sala** (`mission3Assault` en el VM de interiores; ignora el gate de `zombieModeActivated`). La **EVIDENCIA 🧪** aparece en `encb_lab1` (52%/40% de la sala); X la recoge → `onMission3EvidenceRecovered` → `completeMission3Evidence()` + **auto-salida** al mapa (2.6 s). Si sales SIN ella, re-entras volviendo al centro (histéresis `mission3ReentryArmed`: primero aléjate >2× ENTER). |
| 4 | `PHASE_DONE` | — | `hasFirearm=true` (persistido) + `completedMissions += mission3`. |

## 2. Recompensa: primera arma de fuego / First firearm

`GameSaveData.hasFirearm`. En CAMPAÑA, el modo **A DISTANCIA (RANGED)** de interiores está
**BLOQUEADO** (candado 🔒 en el menú de armas; `selectCombatMode` lo rechaza con aviso) hasta
completar esta misión. Fuera de campaña/multijugador `firearmUnlocked=true` (sin cambios).

## 3. Datos técnicos / Technical data

Constantes en `Mission3.kt`: `ENCB` (19.5001588, -99.1450298), `CORDON_RING_DEG` 0.00055,
`DETECT_DEG` 0.00019 / `DETECT_MS` 2500, `ENTER_DEG` 0.00014, `ASSAULT_ZOMBIES_PER_ROOM` 4.
Persistencia: `GameSaveData.mission3Phase` + `hasFirearm` (primitivos → compatibles con saves
viejos). Muerte en `m3_*` = misión fallida (WorldMapMisc). Strings `obj_m3_*`,
`zgame_evidence_prompt`, `mission3_title/desc` (ES+EN).

**⚠️ Nota de render:** los granaderos usan el sprite de policía 👮 (`POLICE_COP`) porque el
exterior aún no tiene ruta de render "premade" (la skin `GRANADERO` es de interiores). Cuando
exista esa ruta, cambiarles el visual.

## 4. Gancho a la Misión 4 / Hook

Con la evidencia y un arma, el jugador puede PROBAR el origen del brote… pero la ENCB ya está
perdida y la infección se expande por Zacatenco. Misión 4 natural: defensa/evacuación con
`PARAMEDICO` + primeras hordas en el mundo abierto (instancia apocalipsis).
