# CAMPAIGN · 02 · Misión 2 — "El rumor" / Mission 2 — "The rumor"

> **Logline:** A salvo dentro de la ESCOM tras la persecución, el jugador debe **perder a la policía**
> que los busca a él y a Prankedy, y al **buscar pistas** por el campus descubre el **rumor zombie**
> (ENCB/Zacatenco), presencia el **primer brote público** (la policía somete al primer zombie y la
> radio pide refuerzos en la ENCB), confronta a **Prankedy** — que confiesa la competencia contra el
> **REY GRUPERO** por ser el auténtico "Rey de las Bromas" — y recupera su **mochila** vaciando un
> salón en clases con la **LATA APESTOSA**.

> Fuente: `mission2/Mission2.kt` (guion + constantes), `WorldMapMission2.kt` (máquina de fases),
> `ZombieRoomCatalog.ESCOM_SALON_M2_ID` + `ZombieGameTick/ZombieAmbientNpcs` (salón). Implementada
> 2026-07-03. Estado: ✅.

---

## 1. Dónde engancha / Where it hooks

La Misión 1 termina con `INGRESAR_ESCOM` cumplida (el jugador ENTRÓ al interior). La Misión 2 se
SIGUE desde el registro de misiones (Opciones → "Misiones"; `startMission2Story` fija la fase 1 y
el objetivo `m2_esconderse_policia`). 🆕 2026-07-08: la **fase 1 se juega DENTRO del lobby de la
ESCOM** (motor de interiores) — coherente con la narrativa (acabas de refugiarte ahí y la policía
entra a buscarte); el 🎯 exterior apunta a la puerta para guiarte a ENTRAR si estás en el campus.
Las fases 2–4 ocurren en la **zona libre** del campus ESCOM (beeline, sin snap a calles) y la
fase 5 en el interior (salón).

## 2. Las 5 fases / The five phases

| # | Fase (`mission2Phase`) | Objetivo (id) | Mecánica |
|---|---|---|---|
| 1 | `PHASE_HIDE` | `m2_esconderse_policia` | 🆕 (2026-07-08) DENTRO del **lobby de la ESCOM**: 4 policías `m2cop_*` (skin `POLICIA_CDMX`) entran por la puerta y patrullan la sala (cada ~7 s UNO barre hacia ti). Si te tienen a <120 px por >2.5 s → te reconocen → **MISIÓN FALLIDA** (sales al mapa, REINTENTAR). Aguanta **35 s** (countdown en HUD) → se rinden, corren a la puerta y se van → cumplido (`completeMission2Hide` avanza a fase 2). Armado runtime vía `setMission2Hide`; ver 05/09. |
| 2 | `PHASE_RUMOR` | `m2_pista_rumor` | 2 estudiantes (`M2_RUMOR_A/B`) frente a frente en `RUMOR_*`. A <~18 m la conversación AVANZA (1 línea/3.4 s, subtítulos `storyConvo*` + burbuja 💬 `talkingUntil` del que habla); alejarse la PAUSA (se retoma en la línea donde iba). 7 líneas (`RUMOR_LINES`) → cumplido. |
| 3 | `PHASE_BROTE` | `m2_pista_brote` | Evento por ETAPAS (`mission2EventStage`) en `BROTE_*`: 0 armado (3 civiles + el "por convertirse", aún con look normal) → al acercarse a <~45 m: 1 CONVERSIÓN (type=ZOMBIE, `visualConfig=null` ⚠️ gotcha de render, con arte propio `zombieSpriteSet="SPRITES/ZOMBIE/ESTUDIANTE"` = "estudiante zombi") + grito; el zombie persigue civiles y los civiles huyen → 2 llegan 2 policías corriendo desde la entrada → 3 SOMETIDO + RADIO (4 líneas `RADIO_LINES`: "refuerzos en la ENCB… TODO el personal") → 4 se lo LLEVAN a la entrada y desaparecen (tope 12 s) → cumplido. |
| 4 | `PHASE_TALK` | `m2_hablar_prankedy` | Prankedy reaparece ESTÁTICO en `PRANKEDY_*` (spawnCompanion+warpTo; el game loop NO corre `runPrankedyTick` en esta fase). A <~13 m corre la plática (10 líneas `PRANKEDY_LINES`): la culpa de la broma, la competencia con el **REY GRUPERO** ("¡Vámonos a la v... wey!"), la mochila en un salón y la LATA APESTOSA. Al terminar se despide y se esconde (deactivate). |
| 5 | `PHASE_BACKPACK` | `m2_recuperar_mochila` | La puerta de la ESCOM REDIRIGE al salón `escom_salon_m2` (`WorldMapInteractions`, solo en esta fase y solo la ruta default de ESCOM). Salón EN CLASES (NPCs ambientales IPN/docente). X = lanzar **LATA APESTOSA** (queda tirada con su vapor integrado, asset `CAMPAIGN/MISSION2/lata_apestosa.png`; `mission2StinkX/Y`) → los alumnos EVACÚAN corriendo a la puerta (`evacuateAmbientNpcs`) → aparece la **mochila** (asset `CAMPAIGN/MISSION2/mochila_prankedy.png`) → X la recoge → `onMission2BackpackRecovered` → `completeMission2Backpack()` → `PHASE_DONE`. |

## 3. Fallo y reintento / Fail & retry

- **Te reconocen (fase 1)** o **mueres en cualquier fase** (`triggerWastedSequence`, ids `m2_*`) →
  pantalla **MISIÓN FALLIDA**. **REINTENTAR** (`retryCampaignMission`, rama `m2_`) respawnea en el
  **checkpoint** (`RETRY_SPAWN_*` = spawn canónico ESCOM) y re-arma la misión COMPLETA desde la
  fase 1 (`mission2Phase=0` + INGRESAR_ESCOM done → `maybeStartMission2Story` la reinicia).

## 4. Persistencia (JSON) / Persistence

- `GameSaveData.mission2Phase` (Int, default 0 → compatible con guardados viejos). Se escribe en
  `buildSaveData` y se restaura en `restoreSaveData`; los ACTORES no se guardan (cada tick de fase
  es idempotente y re-spawnea los suyos si faltan). `buildSaveData` **EXCLUYE** los NPCs de misión
  (`M2_*`/`CAMPAIGN_COP_*`/`ESCOM_FLOOD_*`) del snapshot de NPCs cercanos (evita duplicados).

## 5. Datos técnicos / Technical data

**Coordenadas (constantes en `Mission2.kt`; X=lon, Y=lat; todas dentro del bbox del campus):**
`POLICE_SEARCH` (19.50480, -99.14660; hoy solo la usa la fase 3 — llegada/retirada de policías) ·
`RUMOR` (19.50412, -99.14700) · `BROTE` (19.50396, -99.14614) · `PRANKEDY` (19.50422, -99.14652) ·
`RETRY_SPAWN` (19.504603, -99.145985). **Fase 1 (interior, píxeles):** `HIDE_COP_COUNT=4`,
`HIDE_DETECT_PX=120`, `HIDE_DETECT_MS=2500`, `HIDE_DURATION_MS=35000`, `HIDE_COP_SPEED_PX=3.4`,
`HIDE_SWEEP_EVERY_MS=7000`.

**Archivos:** `domain/models/campaign/mission2/Mission2.kt` (fases/coords/umbrales/diálogos),
`viewmodel/WorldMapMission2.kt` (tick), `WorldMapState.storyConvoSpeaker/Text` + overlay de
subtítulos (`WorldMapScreenOverlays`), `ZombieRoomCatalog.ESCOM_SALON_M2_ID` (sala, fondo reusa
`INTERIORS/ENCB/ENCB_salon1.webp`), `ZombieGameState.mission2*`, `ZombieAmbientNpcs.evacuateAmbientNpcs`,
`ZombieGameTick` (mochila), `ZombieInteriorViewModel.onInteract` (lata/mochila), `AppNavGraph`
(objetivo del salón + callback). **Strings:** `obj_m2_*`, `zgame_stink_prompt`, `zgame_backpack_prompt`
(ES+EN). Los DIÁLOGOS van hardcodeados en español (convención de textos de historia).

**⚠️ Nomenclatura:** al crearse esta misión, el código viejo de la persecución final (que se llamaba
"Misión 2") se renombró `mission2*` → **`mission1Chase*`** (ver `09` §12 y `00_OVERVIEW`).

## 6. Gancho a la Misión 3 / Hook to Mission 3

El jugador tiene la mochila y sabe que el brote escala en la **ENCB** ("todo el personal policial"
fue llamado allá). ✅ La Misión 3 ("Regreso a la ENCB": cordón de granaderos + asalto interior con
zombis + evidencia → primera arma de fuego) está implementada — ver `03_MISSION_3.md`. **Extras de
esta misión (2026-07-04):** la mochila ahora **DESBLOQUEA los 4 slots** del inventario
(`inventoryUnlockedSlots`), y las misiones 2/3 se SIGUEN desde el **registro de misiones**
(Opciones → "Misiones"; ya no arrancan solas — ver `00_OVERVIEW`).
