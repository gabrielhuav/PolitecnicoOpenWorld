# DISEÑO · MODO ARCADE "HUELUM VS. GOYA" — versión POW completa (2026-07-16)

> **Estado: release candidate 1/9 (2026-07-18).** Decisiones cerradas con el dueño; audio,
> arte pendiente de esta pasada y auditoría completa de campaña implementados. Leer antes:
> 07 §HUELUM VS. GOYA + `AUDIT_SF_MULTIPLAYER.md`. Convenciones: 09
> (MVVM, estado inmutable con `_state.update { it.copy(...) }`, strings ES+EN con paridad,
> CRLF, Read para verificar). Los BUGS del modo (stun-lock, revancha, servidor LAN) viven en
> `PENDIENTES_SF_2026-07-16.md` y NO dependen de esto.

## Fix 2026-07-18g (Claude) — regresiones de IA/input

- `isAnimationCompleted` (VM) ahora da por terminada la animación al llegar al último frame,
  no solo con el frame `-1`. Corrige que el JUGADOR quedara atascado en ARCADE (estudiantes
  ALPHA sin `-1`) y que la CPU se congelara. No afecta animaciones bien formadas.
- Watchdog ofensivo de la CPU cubre cualquier distancia (antes solo `<150 px`): fuerza
  acercarse (lejos) o atacar/clinch (en rango) → IA vs IA ya no "camina y se mira".
- Agresividad fina de AVANZADA/PESADILLA: afinar contra grabación IA vs IA (no a ciegas).

## Fix 2026-07-18h (Claude) — red de seguridad + autojuego (gauntlet)

- `watchStuck`: desatasca estados transitorios cuya animación no termina (asset sin frame -1) →
  arregla "se pegan y no se mueven"; registra el asset roto. `watchStalemate`: detecta peleas
  sin daño >12 s y pica a la IA. Log en logcat `SF-DIAG` + `diagnosticsReport()`.
- Autojuego (gauntlet): `startGauntletRoundRobin` (todos vs todos) y `startGauntletArcade`
  (escalera en orden rotando peleador). Encadena peleas IA vs IA (tope 60 s c/u), progreso +
  DETENER, y al final escribe un .txt en getExternalFilesDir + reporte en pantalla. Sirve para
  cazar assets rotos: cuando salga el reporte, corregir en la siguiente pasada.

## Cambios 2026-07-18m (Fable) — desbloqueo por dificultad, dev-tools, flechas P1/P2, dificultad sin escenario

- **Desbloqueo por dificultad ELEGIDA** (`handleArcadeMatchEnd`, key = `arcadeChosenDifficulty`):
  FÁCIL (BASICA) → SOLO el mapa del rival; MEDIO (NORMAL) → el PELEADÓR + su mapa (mínimo para
  tener al personaje); DIFÍCIL (AVANZADA/apocalíptica) → NADA por ahora (próx.: animaciones/
  poderes). La escalera siempre avanza al ganar. Antes: cualquier victoria desbloqueaba peleadór+mapa.
- **"ELIGE DIFICULTAD" sin escenario:** los strings `sf_arcade_diff_*_desc` + `_maps_hint` ya NO
  dicen día/noche/apocalíptica; ahora describen el AI y el desbloqueo (ES+EN).
- **Flechas P1/P2 en el selector:** `SelectArrowHeader` + params `allyId`/`showPickArrow` en
  `CharacterSelectOverlay`. Al elegir al P2/rival (práctica + IA vs IA): flecha AZUL "P1 ▼" sobre
  el ya elegido y flecha ROJA "P2 ▼" sobre el resaltado (además de la animación del card).
- **Autojuego/Showcase = SOLO Modo Desarrollador:** los 4 botones (todos vs todos, 9 campañas,
  showcase animaciones/sonidos, showcase audios) se envuelven en `if (devMode)` dentro de
  `SfModeMenuOverlay` (param `devMode = viewModel.devUnlockAll()`), bajo header
  `sf_dev_tools_header`. Los usuarios normales solo ven Arcade/Práctica/IA vs IA/Multijugador.

## Fix 2026-07-18j (Fable) — IA variada + showcase completo con auditoría estática

- IA "quietos/mismo ataque": `cpuLastOffenseMs` ahora se alimenta del ESTADO real (atacando),
  no de la intención (los inputs descartados por cooldown/validFrom/HURT contaban como
  ofensiva); con pasividad >2× límite el golpe es OBLIGATORIO en rango (≤~2 s sin acción).
  `cpuClinchBreak` en IA vs IA con ROLES asimétricos (índice+tiempo: uno golpea, otro se
  separa) — antes ambos rodaban la misma tabla y quedaban pegados. `variedCpuAttack`
  (no repite la firma fuerza×tipo anterior) sustituye a `randomCpuAttack`; special lejano
  con fuerza al azar.
- Showcase COMPLETO: `showcaseExtraStates` (giros, 6 HURT, KO, VICTORY) + metamorfosis de
  La Presidenta, aplicados con `forceShowcaseState` (bypass validFrom; reporta anims
  inexistentes). `showcaseStepMs`=2000 (> stuckLimitMs), cap por pasos
  (`gauntletFightCapCurMs`), timer congelado y golpes sin daño en showcase (solo SFX/splash).
- Auditoría ESTÁTICA: `auditFighterAssets` (anims faltantes/vacías por `SfFighterState.jsKey`,
  frames rotos, `special_<id>.ogg`) + `auditThemeSounds` (SFX del tema + música) → mismo
  reporte .txt/overlay. Detalle en 07 §HUELUM VS. GOYA.

## Fix 2026-07-18k (Fable) — showcase v2 (feedback del dueño en dispositivo)

- Ritmo: avance automático al terminar la animación (ambos IDLE + paso disparado + ≥400 ms) y
  botón "Saltar animación" (`skipShowcaseStep`, `sf_showcase_skip`, `state.showcaseRunning`).
- Fix salto perdido: los one-shot esperan IDLE para disparar (`showcaseInput(now, SfFighter)`).
- Mapas: cada pelea del autojuego en el mapa HOGAR del peleadór (`state.gauntletMapFile`;
  showcase=día, gauntlet=apocalipsis); la Screen lo prioriza en `effectiveBgFile`.
- Audio: pasos forzados con .ogg reutilizados (HURT→hit por fuerza, KO→heavy-kick-hit,
  VICTORY/metamorfosis→voz `special_<id>.ogg`, solo idx 0); VICTORY con voz también en pelea
  real. El estado de audio pendiente de esta pasada queda superado por Release 1/9 abajo.

## Release 2026-07-18 · Launch Relase 1/9 (Sol) — audio/arte/IA cerrados

- Audio regenerado desde las fuentes locales para los 21 ids, solo español, duraciones por
  contenido (2.37–13.8 s hablado; banda Granadero completa 27.5 s). `MediaPlayer` reproduce los
  specials largos completos; `SoundPool` conserva efectos breves. Presidenta auténtica 8.3 s,
  Lázaro incluido y Robot ficticio sintetizado. Whisper verifica texto/idioma de cada voz.
- Pipeline reproducible: `transcribe_sf_voice_sources.py` → `sf_audio_cuts_v3.json` →
  `build_sf_audio_v3.py` → `verify_sf_audio_content.py`; reportes v3 con hashes, 21/21 PASS.
  No se re-scrapeó YouTube ni se borraron `raw_yt/`/`out_diarized/`.
- La Llorona: HURT repetidos regenerados con 3–4 poses únicas. Yoalli: `bonusPower10` invierte
  la metamorfosis Presidenta P11 y convierte Yoalli→Presidenta conservando HP.
- Auditoría IA ejecutable: 9 campañas completas, 135 peleas, dificultad/intensidad/mapa reales,
  aceleradas solo en el bot QA. Test puro: 600 configuraciones aleatorias + presencia de assets.
- CI: errores `ComplexCondition`/`UnusedParameter` corregidos; detekt bloqueante local PASS;
  debug APK, unit tests y release AAB compilan con AGP/Gradle actuales. Versión 1.0.0.12.

## Hotfix de entrega 2026-07-18 — límite Play, skip real y AAB manual

- El primer upload de 1.0.0.12 llegó con firma/versionCode válidos, pero Play rechazó el módulo
  `base` por superar 500 MB comprimidos.
- Los 48 atlas de escenario pasaron de PNG a WebP lossless exacto; se verificó hash RGBA idéntico
  por archivo. Los 29 fondos fijos heredados sin referencias runtime se movieron a
  `_ORPHAN_ASSETS/STREETFIGHTER/legacy_static_backgrounds` (preservados, fuera del AAB).
- Resultado real de `bundleRelease`: AAB **434.47 MiB**, `base` comprimido **433.84 MiB
  (454.91 MB)**, margen preventivo **45.09 MB** bajo el límite decimal. Las 21 voces y los
  WAV generales no se recomprimieron.
- `skipShowcaseStep` marca completado el bloque del peleador y su timer; el tick siguiente
  encadena al siguiente peleador sin esperar el resto del guion.
- CI migra a Actions Node 24, `tracks: alpha`, notas ES/EN y validación 500 MB. El AAB firmado
  se guarda como artefacto; `manual-play-upload` permite la entrega manual sin doble publicación.

## Hotfix 2026-07-18 · fondos realmente animados + QA acelerado + La Llorona

- Pipeline de mapas: 15 cuadros fuente repartidos en 5 s, playback 6 fps y ping-pong de 28
  pasos. Matriz cerrada 16×3: día, noche y noche tenebrosa; logo POW aplicado por fotograma.
  Los auxiliares se escriben en `additional_assets/`, fuera del proyecto Android interno.
- Showcase: salto de animación (mismo personaje), salto de personaje, velocidad 1×/2×/4×,
  repetir voz y showcase audio-only de los 21 OGG con duración real.
- La Llorona: la hoja 09 fusionaba 14 figuras HURT HEAD en siete blobs. El slicer calcula el
  ancho esperado de pose, separa 14/14 y selecciona cuatro cuadros completos; el validador
  rechaza cuerpos HURT anormalmente anchos. Atlas final: 123 frames, 30 animaciones.
- Verificación release AGP 9.3/Gradle 9.5/JBR: AAB 438.52 MiB, `base` 459.16 MB comprimidos,
  margen 40.84 MB bajo Play. `additional_assets` tiene cero entradas en el bundle.

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
8. **Mapa "Ciudad Universitaria UNAM" = renombrar "Biblioteca UNAM"** (asset runtime
   `fondo_unam_biblioteca_cu_anim.webp`; el PNG fijo histórico quedó archivado). Está en CU.

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

## Añadidos 2026-07-17d (implementado)

- **La Llorona INTEGRADA:** pipeline corrido (19 hojas croma → `slice_sf_chroma_sheets.py` →
  `pack_sf_character.py lallorona LaLlorona`) → `IMAGES/LaLlorona.png` + `DATA/lallorona.json`
  (incluye proyectil de la hoja 12, que era JPG → sprite algo tosco; regenerar como PNG para
  nitidez). Enum `LA_LLORONA`. Escalera **15 peleas**: bloque 11-12 = {Charro, Llorona} azar.
- **Copyright SF fuera del HUD:** `hud.png`→`sf_hud_pow.png` (fuente+barra+KO+timer), `decals.png`
  →`sf_decals_pow.png`, `Ken.png`(fireball) y `kenstage.png` eliminados. Los 3 SF + 5 fuente
  `ChatGPT*.png` se movieron a `newSFAssets/_hud_src/`. Falta reemplazar sonidos (`hadouken.ogg`
  + golpes). Atlas armados por script (chroma-key + recorte + normalización).
- **Botón menú principal** (`MainMenuScreen.FeaturedStreetFighterButton`): destacado + animado
  (pulso, brillo dorado que barre, borde/sombra que laten), tag `◆ MODO COMBATE ◆`.
- **Menú de modos POW** al entrar (`SfModeMenuOverlay`): ARCADE (principal) / PRÁCTICA /
  **IA VS IA** / MULTIJUGADOR. Arcade = SOLO eliges peleador (dificultad FIJA, `startArcade(playerId)` sin base).
- **Dificultad arcade (2026-07-18g):** el jugador elige **Fácil / Medio / Difícil** al
  arrancar (`ArcadeDifficultyOverlay` → `startArcade(id, difficulty)`). Mapas = **hogar del
  rival** (`SfStageCatalog.homeStage` + `mapForRival`) + iluminación:
  Fácil→día, Medio→noche_1, Difícil→noche_2. IA base = elegida; jefes/final +1/+2 ordinal.
  `cpuIntensity` piso 0.20 → 1.0 en la final.
- **Tabla peleadór→mapa (dueño, 2026-07-18h):** ver **`SF_STAGES_MAPS_UNLOCK.md`**.
  REY_GRUPERO → FES Aragón; ESCOMGIRL → ESCOM; etc. Desbloquear peleadór desbloquea las 3
  luces de su mapa (`SfArcadeRepository.unlockFighter` → práctica + MP host BT/LAN/Render).
- **🆕 IA VS IA (2026-07-18):** CPU vs CPU a PESADILLA + `cpuIntensity = 1f`. Controles
  ocultos, solo "Salir". **⚠️ REGRESIÓN reportada:** a menudo dejan de pelear; ver
  `_ARCHIVO/PROMPT_traspaso_IA_CPU_2026-07-18.md` (prioridad siguiente sesión).
- **🆕 Fondos cap 2048 + thumbs (2026-07-18):** atlas ≤2048 (gama baja), frames ~480×270
  crop-to-fill, ~28 frames ping-pong, `_thumb.png` ~256 px. Tool `build_map_backgrounds.py`.
- **🆕 Selector de mapa (`SfStageSelectOverlay.kt`, 2026-07-18b):** preview **estático** en
  todas las tarjetas; **solo el focused** anima (un frame del atlas, no el filmstrip);
  confirmar con botón. Lógica separada del Canvas de combate.

## PENDIENTE — siguiente sesión

> ✅ Hechos (2026-07-17e…18h): arcade por defecto; bloqueados ???; rival visible; mapas×3;
> Fácil/Medio/Difícil; peleadór→mapa dueño; unlock peleadór→mapa; specials 21/21.
> Parches parciales de movimiento/IA (IDLE_TURN, stale approach) — **no suficientes**.

### 🔥 P0 — IA de pelea (traspaso Claude 4.8)

Ver **`_ARCHIVO/PROMPT_traspaso_IA_CPU_2026-07-18.md`**.

1. Jugador a veces **no se mueve** en arcade (input / estados / push).
2. **IA vs IA** deja de pelear (solo camina).
3. CPU **menos agresiva** que antes del anti-spam (upgrade real, no solo rates al azar).
4. Mantener: 1 fireball activa, sin muro de proyectiles, sin salir de stage.

### P1 — Combate “SF original” (grande, después de P0)

Más motions/combos/cancels/fluidez. Definir set con el dueño antes de implementar.

## Añadidos 2026-07-17c

- **Escalera NUEVA (14 peleas; 15 con La Llorona)** en `SfArcadeLadder`: 1 Paramédico CR · 2-4
  {Paparazzi1, Paparazzi5, Señor Tienda} azar · 5-6 {Rey Grupero, Prankedy} azar · 7-10 policías
  (fijo) · 11 Charro Negro *(+ La Llorona azar cuando tenga assets → sube a 15)* · 12 Tzitzímime
  · 13 Yoalli Ehécatl · **14 La Presidenta (FINAL, PESADILLA)**. Los estudiantes YA NO son
  enemigos. `arcadeDifficulty`: final = PESADILLA, jefes (Tzitzímime/Yoalli) = AVANZADA, resto
  base +1 en la 2ª mitad.
- **Copyright:** `fireballImage` (Ken.png) ELIMINADO (todos tienen `proj-*` propios);
  **`kenstage.png`** quitado de `imageFiles` + `drawScene` (⚠️ **borrar el archivo físico
  `assets/STREETFIGHTER/IMAGES/kenstage.png`**, no se pudo desde la sesión). Pendiente del dueño:
  reemplazar la fuente + 3 piezas del HUD (barra/KO/timer) y el `hadouken.ogg`; sonidos de golpe
  se quedan con nota. La Llorona: faltan assets empacados (hoja croma _12 = SPECIAL HEAVY).

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

## Fix 18l — IA compartida, aterrizaje y anti-bucle (2026-07-18)

- **Causa raíz del pegado/timeout:** `updateStageConstraints` limita `y` exactamente a
  `STAGE_FLOOR`, pero el handler de `JUMP_UP/FORWARD/BACKWARD` solo aterrizaba con `y > floor`.
  El peleador quedaba para siempre en `JUMP_*` aunque visualmente estuviera abajo. Ahora aterriza
  con `y >= floor && velocityY >= 0`, y los tres estados aéreos están cubiertos por `watchStuck`.
- `buildCpuInput` usa un solo motor en VS, Arcade, IA vs IA y Autoplay. `repairCpuFacing` se ejecuta
  para cualquier CPU antes de leer `forward/backward`; dificultad controla cadencia/defensa y
  `CpuStyle` solo sesga presión o poderes según el personaje.
- La colisión cuerpo a cuerpo continúa buscando BODY/LEGS si HEAD no traslapa. La CPU no intenta
  golpes cortos fuera de `CPU_MELEE_DIST`; tras pasividad fuerza acercamiento real.
- Variedad/justicia: memoria de 3 golpes, cooldown separado para special/bonus, defensa reactiva y
  ventana `COMBO_ESCAPE_MS` tras 3 impactos rápidos para impedir cadenas de poder sin salida.
- Auditoría: cada estancamiento y ronda decidida por tiempo es un problema explícito; el `.txt`
  incluye rondas por KO y por tiempo. Validación principal: **Autoplay everyone vs everyone**
  (18×17 = 306 combates) en emulador, seguida por las 9 campañas por dificultad.
