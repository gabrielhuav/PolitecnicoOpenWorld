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
| 2 | `PHASE_INFILTRATE` | `m3_infiltrarse` | 6 granaderos (`M3_GRANADERO_*`, `POLICE_COP` — ver nota de render) patrullan un ANILLO (~60 m) alrededor de la ENCB con arcos deterministas por cubeta de 5 s; 3 paparazzi (`M3_PAPARAZZI_*`, civiles con burbuja 💬 intermitente) merodean afuera. **SIGILO:** a <~21 m de un granadero por >2.5 s → detectado → **MISIÓN FALLIDA** (retry: `RETRY_SPAWN_*` = MISSION1_SPAWN, reinicia desde fase 1). Llegar al CENTRO (<~15 m) → jingle + `mission3EnterEncb=true` (la View navega a `interiores_zombies?startRoom=encb_lobby`) → fase 3. **🆕 BROTE / ZONA DE GUERRA (2026-07-10):** **Prankedy te ESCOLTA** durante toda la infiltración (`ensureM3PrankedyEscort`: spawnCompanion HIRED SIN tocar el objetivo m3_; `runPrankedyTick` lo hace seguirte). El brote **solo se ARMA cuando llegas MUY cerca de la ENCB** (`M3_BROTE_TRIGGER_DEG` ~38 m, one-shot `mission3BroteArmed`) — antes NO hay zombis. Al armarse (`armM3BroteIfClose`) nacen **5 zombis** (`M3_ZOMBIE_*`, `zombieSpriteSet="SPRITES/ZOMBIE/ESTUDIANTE"`) + **4 policías de CONTENCIÓN** (`M3_CONTAIN_*`, `POLICE_COP`) que **cargan contra el zombi más cercano** y lo "someten" (reaparece en el anillo → batalla perpetua) = apocalipsis en curso. `tickM3Brote`: los zombis persiguen a Prankedy humano / policías / al jugador; si un zombi mantiene contacto con Prankedy `M3_CONVERT_MS` (2.5 s) → **CONVERSIÓN**: `convertM3Prankedy` lo reemplaza por el **PRANKEDY zombi** (`M3_PRANKEDY_ZOMBIE`, `zombieSpriteSet="SPRITES/ZOMBIE/PRANKEDY"`), **INMORTAL** (`health=999999`), que te caza a TI; su contacto (y el de cualquier zombi) te hace daño (`takeDamage`, cooldown 1.2 s) → si te mata, **MISIÓN FALLIDA**. Se re-arma en `stopM3PrankedyEscort` (retry / entrar al interior). *(La ENCB interior — fase 3 — ya tiene su propio brote de zombis.)* |
| 3 | `PHASE_ASSAULT` | `m3_recuperar_evidencia` | INTERIOR: la cadena ENCB se siembra con **4 zombis por sala** (`mission3Assault` en el VM de interiores; ignora el gate de `zombieModeActivated`). La **EVIDENCIA** (asset `CAMPAIGN/MISSION3/evidencia_frasco.png`, antes emoji 🧪) aparece en `encb_lab1` (52%/40% de la sala); X la recoge → `onMission3EvidenceRecovered` → `completeMission3Evidence()` + **auto-salida** al mapa (2.6 s). Si sales SIN ella, re-entras volviendo al centro (histéresis `mission3ReentryArmed`: primero aléjate >2× ENTER). |
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

**🆕 (2026-07-12) Cierre narrativo cableado:** al completar la M3 (`completeMission3Evidence`) se
reproduce el cómic **`mission3_outro`** (`IntroPOW23..24`, PLACEHOLDERS — "Esto lo prueba todo." /
"¿Y ahora a QUIÉN se lo llevamos?"), vía `pendingMission3OutroComic` → LaunchedEffect (espera a
salir del interior) → ruta `story_mission3_outro`.

Con la evidencia y un arma, el jugador puede PROBAR el origen del brote… pero la ENCB ya está
perdida y la infección se expande por Zacatenco. Misión 4 natural: defensa/evacuación con
`PARAMEDICO` + primeras hordas en el mundo abierto (instancia apocalipsis). **🆕 Sus cómics ya
están RESERVADOS en `StoryComicCatalog`:** `MISSION4_INTRO_ID` (`IntroPOW25..28`: nadie les cree →
radio médica → primera horda → la paramédica recluta) y `MISSION4_OUTRO_ID` (`IntroPOW29..30`:
evacuación lograda → un evacuado viene MORDIDO, gancho a la M5). Placeholders listos; cablear con
el patrón bandera→LaunchedEffect→ruta al crear la misión.
