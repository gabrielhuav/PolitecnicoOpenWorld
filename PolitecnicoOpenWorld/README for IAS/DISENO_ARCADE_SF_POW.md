# DISEÑO · MODO ARCADE "HUELUM VS. GOYA" — versión POW completa (2026-07-16)

> **Para la próxima sesión de IA.** Decisiones CERRADAS con el dueño (abajo). Implementación
> EN CURSO. Leer antes: 07 §HUELUM VS. GOYA + `AUDIT_SF_MULTIPLAYER.md`. Convenciones: 09
> (MVVM, estado inmutable con `_state.update { it.copy(...) }`, strings ES+EN con paridad,
> CRLF, Read para verificar). Los BUGS del modo (stun-lock, revancha, servidor LAN) viven en
> `PENDIENTES_SF_2026-07-16.md` y NO dependen de esto.

## Objetivo

Modo arcade estilo Street Fighter, 100% POW: quitar copyright, **todos los personajes y
mapas bloqueados**, se desbloquean **al derrotar rivales** en una escalera de dificultad
creciente, terminando en un **penúltimo jefe** y un **jefe final** (análogos a Urien/Gill).

## Roster — estado REAL (fuente: enum `SfFighterId` + flag `isAlpha`)

**① Copyright — QUITAR del enum:** `RYU`, `KEN`. Eliminar de `SfFighterId`,
`selectableFighters`, `sanitizeNetFighter`, `classicFightersUnlocked` y sus assets debug.

**② POW bien implementados (arte dedicado, SIN badge ALPHA) — 8. Forman la ESCALERA:**
Prankedy · El Señor de la Tienda · Paparazzi 1 · Paparazzi 5 · Rey Grupero · Policía CDMX ·
Policía CDMX (Hombre) · Paramédico Cruz Roja.

**③ POW ALPHA (poses de plantilla, badge "ALPHA") — 6.** Cambio de plan: `ESCOMBOY`,
`ESCOMGIRL`, `ROBOT` y `GRANADERO` ahora SÍ entran a la escalera como PLACEHOLDER (arte
pendiente). Siguen fuera: `LAZARO` y `PARAMEDICO` (ALPHA). Arte final:
`GUIA_generacion_assets_SF.md`.

**④ Personajes a ELIMINAR del todo:** `REY_BROMAS` y `PEPE_REY` (skins ya COMENTADOS en
`features/map_exterior/ui/components/PlayerSkin.kt`) + su comentario en `SfModels.kt`.
⚠️ NO tocar la narrativa de `Mission2.kt`: ahí "Rey de las Bromas" es un TÍTULO de la
historia (Rey Grupero compite por él), no el personaje.

> ⚠️ El roster CRECERÁ ("estamos por implementar más personajes"). Todo data-driven.

## DECISIONES CERRADAS (2026-07-16, con el dueño)

1. **Persistencia: LOCAL (SharedPreferences)**, mínima. El proyecto solo tiene **Firebase
   Auth (Google Sign-In)** — NO hay Firestore/Realtime DB y no se guardan contraseñas. El
   progreso del arcade se guarda en el dispositivo como la campaña. (Nube = futuro opcional.)
2. **Inicio: desbloqueados SOLO los 3 estudiantes** `ESCOMBOY` / `ESCOMGIRL` / `ROBOT`; el
   jugador elige UNO de ellos. Todo lo demás con candado. (Prankedy ya NO es inicial: pasa a
   ser el JEFE FINAL.)
3. **El jugador ELIGE su peleador** de entre los DESBLOQUEADOS (estilo SF).
4. **Escalera FIJA de 10 peleas** (ver sección "ESCALERA" abajo). **Semifinal = Rey Grupero**,
   **FINAL = Prankedy**. (Cambió respecto a la versión anterior: antes Rey Grupero era el
   final; ahora es la semifinal y Prankedy es el jefe final.)
5. **Dificultad: HÍBRIDA.** El jugador elige una base (BASICA/NORMAL/AVANZADA, reusa el enum
   `SfCpuDifficulty` ya implementado) y la curva SUBE hacia los jefes.
6. **Al PERDER: retrocede 1 pelea** (repite la anterior y vuelve a avanzar). No reinicia toda
   la escalera.
7. **Mapas (6): desbloqueo LIGADO AL RIVAL** — vencer a un rival desbloquea SU mapa asociado.
   FIJOS: **Queso IPN** = mapa inicial ya desbloqueado / primera pelea; **CU UNAM** = mapa del
   **jefe final (Prankedy)**. Los intermedios se desbloquean según salga cada rival
   (aleatorio). Candado **🔒 en la esquina superior** de los mapas bloqueados en el selector
   (motiva a seguir jugando).
8. **Mapa "Ciudad Universitaria UNAM" = renombrar "Biblioteca UNAM"** (mismo asset
   `fondo_UNAM_bibliotecaCentral_1.png`, solo cambia el nombre visible). Está en CU.

## ESCALERA — 11 peleas (CERRADA con el dueño 2026-07-16)

Implementada en `domain/models/streetfighter/SfArcadeLadder.kt` (`build(player, rng)`).
El jugador elige uno de {ESCOMBOY, ESCOMGIRL, ROBOT}. Luego:

1-2. Los **otros 2 estudiantes** que NO elegiste — orden ALEATORIO.
3-5. **Paramédico Cruz Roja**, **El Señor de la Tienda**, **Paparazzi 1** — orden ALEATORIO.
6-9. **ORDEN FIJO:** Policía CDMX (Hombre) → Policía CDMX (Mujer=`POLICIA_CDMX`) →
     Granadero → Granadero. *(Son 4 peleas → escalera de 11.)*
10.  **SEMIFINAL: Rey Grupero.**
11.  **FINAL: Prankedy.**

**Placeholders (decisión del dueño):** los escalones 8 y 9 usan el ÚNICO `GRANADERO` ALPHA
como marcador de "Granadero Hombre" y "Granadero Mujer" hasta que el dueño cree esos
spreadsheets (ya en proceso). ESCOMBOY/ESCOMGIRL/ROBOT también son ALPHA (poses de
plantilla) pero jugables. Cuando lleguen los assets, se sustituyen SIN tocar la lógica.
Bien hechos ya: Prankedy, Señor Tienda, Paparazzi 1, Paparazzi 5, Rey Grupero, Policía CDMX,
Policía CDMX (Hombre), Paramédico Cruz Roja. (Paparazzi 5 NO está en la escalera.)

## Mapas — estado actual (`SfTheme.kt` → `fullBackgrounds`, 6)

ESCOM · Queso IPN · ESIME Azcapotzalco · CECyT 9 · CECyT 2 · Biblioteca UNAM→**CU UNAM**.
Los locked salen con candado; el fondo del muelle SF queda de fallback.

## Cambios técnicos (archivos)

- `domain/models/streetfighter/SfModels.kt`: quitar RYU/KEN del enum; limpiar comentario
  REY_BROMAS/PEPE.
- `features/map_exterior/ui/components/PlayerSkin.kt`: borrar el bloque comentado
  REY_BROMAS/PEPE_REY.
- `features/streetfighter/data/SfTheme.kt`: renombrar "Biblioteca UNAM" → "Ciudad
  Universitaria UNAM".
- **Nuevo** `data/repository/SfArcadeRepository.kt`: SharedPreferences `pow_sf_arcade` —
  set de peleadores desbloqueados, set de mapas desbloqueados, índice de escalón (progreso).
  API tipo: `unlockedFighters()`, `unlockFighter(id)`, `unlockedMaps()`, `unlockMap(file)`,
  `ladderStep()`/`setLadderStep(n)`, `reset()`. Modelo: `CampaignRepository` (07 §Historia).
- `features/streetfighter/viewmodel/StreetFighterState.kt`: modo arcade, escalón actual,
  rival actual, sets desbloqueados, base de dificultad, flags fin/ending.
- `features/streetfighter/viewmodel/StreetFighterViewModel.kt`: montar la escalera
  data-driven (pesos → rival aleatorio; jefes fijos), avanzar al ganar, retroceder 1 al
  perder, curva de dificultad híbrida, desbloquear peleador+mapa del rival vencido y
  persistir, `selectableFighters` = solo desbloqueados fuera del arcade. Arcade SOLO offline.
- `features/streetfighter/ui/StreetFighterScreen.kt`: entrada "ARCADE", selector con
  **candados** (peleadores y mapas, 🔒 esquina superior), secuencia de rivales, pantalla de
  continue/retroceso y ENDING al ganar la final.
- `res/values/strings.xml` + `res/values-en/strings.xml`: strings nuevos, paridad ES+EN.

## PENDIENTE de que lo dé el dueño

- **Assets:** SOLO falta **ROBOT** (sigue ALPHA `sf_template`+`RUNTIME/Robot.png`). ✅ Ya con arte
  dedicado y VERIFICADOS (2026-07-17, pull "Refactor SF 4-5/9"): ESCOMBOY, ESCOMGIRL,
  `POLICIA_GRANADERO_HOMBRE` y `POLICIA_GRANADERO_MUJER` (JSON+PNG propios, sin `isAlpha`). La
  escalera ya los usa (escalones 8-9 = los Granadero reales; el placeholder `GRANADERO` salió).
  ⚠️ Ojo: `escomboy/escomgirl/policiagranaderomujer.json` son byte-idénticos entre sí (comparten
  la caja de Rey Grupero) → revisar en dispositivo que hurt/hit-boxes calcen con cada sprite.
- **Asociación mapa ↔ rival** de los escalones 2-10 (qué mapa desbloquea cada rival). Fijos:
  escalón 1 = Queso IPN; escalón 11 (final, Prankedy) = CU UNAM. Intermedios hoy `null` (TBD).
- Detalle del ENDING (pantalla/texto/recompensa) al ganar la final.

## Estado de implementación

- [x] Quitar REY_BROMAS/PEPE (skins comentados) — hecho 2026-07-16 (PlayerSkin.kt + comentario SfModels.kt; Mission2 intacto).
- [x] Renombrar Biblioteca UNAM → CU UNAM — hecho (SfTheme.kt, nombre visible).
- [x] `SfArcadeRepository` (persistencia local, archivo aislado) — hecho (`data/repository/SfArcadeRepository.kt`; defaults: ESCOMBOY/ESCOMGIRL/ROBOT + mapa Queso). Falta CABLEARLO al VM.
- [x] `SfArcadeLadder` (modelo de 11 peleas, aislado) — hecho (`domain/models/streetfighter/SfArcadeLadder.kt`).
- [x] Quitar RYU/KEN del enum — hecho 2026-07-17. Fuera de `SfFighterId`, `selectableFighters`,
  `selectCharacter` (default) y `sanitizeNetFighter` (borrada); `classicFightersUnlocked` +
  import `SettingsRepository` eliminados. Parse de red defensivo (`valueOf`→null→PRANKEDY).
- [x] IA POR FASES — hecho 2026-07-17. Campo `cpuIntensity` 0f..1f: en el arcade sube 0→1 con el
  avance de la escalera (pelea 1 = 0, final = 1) y acelera la cadencia de decisión (hasta ~45%)
  + sube agresividad/bloqueo/poderes. En VS es 0 → comportamiento idéntico al de siempre.
- [x] Estado + VM — hecho 2026-07-17. `StreetFighterState` (campos arcade + enum `SfArcadeOutcome`).
  VM: `startArcade`/`startArcadeStep`/`arcadeContinue`/`arcadeRetry`/`arcadeExit`/`handleArcadeMatchEnd`
  + `arcadeDifficulty` (híbrida) + hook en `endRound`. `selectableFighters()` pasó a FUNCIÓN y
  devuelve solo DESBLOQUEADOS; `lockedFighters()`/`unlockedMaps()` para los candados.
- [x] Screen — hecho 2026-07-17. Botón ARCADE en el selector; setup (peleador desbloqueado +
  dificultad base); `ArcadeResultOverlay` (GANASTE/PERDISTE/CAMPEÓN) que reemplaza el menú de
  fin en arcade; fondo desde `arcadeMapFile`; candado 🔒 en `CharacterCard` y `StageCard`.
  + strings ES+EN (`sf_arcade_*`, `sf_back`).

## Añadidos 2026-07-17b

- **Dificultad `PESADILLA`** (4ª): combos casi constantes, esquiva, castiga; el FINAL del arcade
  (Prankedy) la usa (rampa `arcadeDifficulty`: final +2, semifinal/2ª mitad +1). Opción en el
  selector + strings `sf_diff_nightmare(_desc)` ES+EN.
- **Modo Desarrollador** (`devUnlockAll()` = `getDeveloperMode()`): desbloquea TODOS los
  personajes y mapas (sin candados) en selector y práctica.
- **Modos:** ARCADE = campaña con desbloqueos; el flujo peleador→rival→**dificultad**→mapa es
  PRÁCTICA y **NO desbloquea nada** (solo `handleArcadeMatchEnd` desbloquea, y solo en arcade).
- **Escalera 8-9** ya usa los Granadero reales (`POLICIA_GRANADERO_HOMBRE/MUJER`).
- **Preview del selector** más lento + anima IDLE + `walkForwards`.

## ⚠️ Implicaciones / límites del build actual (para la próxima sesión)

- **Roster libre (VS y online) ahora = SOLO desbloqueados.** Al inicio son los 3 estudiantes;
  el resto se gana en el arcade. Los NO participantes del arcade (Paparazzi 5, Lázaro,
  Paramédico ALPHA) hoy quedan sin vía de desbloqueo → invisibles en VS hasta sumarlos al
  esquema. Modo Desarrollador sigue añadiendo RYU/KEN.
- **Desbloqueo de MAPAS mínimo:** como la asociación mapa↔rival intermedia sigue pendiente,
  hoy solo se desbloquea Queso (default) y CU UNAM (al ganar la final). Definir el resto.
- **Verificado:** CRLF, llaves balanceadas en los 4 .kt, strings ES+EN con paridad, refs OK.
  Falta SOLO Rebuild + prueba en dispositivo.

## Protocolo al implementar (09)

Docs 07 (§HUELUM VS. GOYA) + este doc (marcar avance / borrarlo al terminar) + README
público raíz (EN **y** ES). Arcade es offline → NO toca red. Verificar con Read, balance de
llaves y CRLF. Listo para Rebuild.
