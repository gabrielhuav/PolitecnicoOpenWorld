# 07 · Menú, Ajustes, ShineCTO, Coleccionables / Menu, Settings, ShineCTO, Collectibles

---

## Menú principal / Main menu (`features/main_menu/`)

### `viewmodel/MainMenuViewModel.kt` + `MainMenuState.kt`
- `MainMenuState` incluye `mapProvider (default CARTO_VOYAGER)`, `showCacheWidget`, `showFpsWidget`,
  `showMultiplayerDialog`, `playerName`, estado del warm-up.
- API: `onStartGame()`, `setMapProvider(provider)`, `toggleCacheWidget/FpsWidget`,
  `updateShowMultiplayerDialog(show)`, `updatePlayerName(name)`, `onMultiplayerPressed()`,
  `cancelWarmup()`, `dismissWarmupError()`.

### `ui/ServerWarmupManager.kt` (paquete `data.network`)
**ES:** Render (tier gratis) duerme los servidores. Al tocar **MULTIJUGADOR**, hace `GET <server>/status`
por HTTPS **antes del diálogo de nombre**, bloqueando con un spinner cancelable hasta `200 OK` (reintenta
en timeout). Pings exitosos se cachean 60 s.
**EN:** Render free tier sleeps servers. On tapping **MULTIPLAYER**, polls `GET <server>/status` over
HTTPS **before the name dialog**, blocking with a cancellable spinner until `200 OK` (retry on timeout).
Successful pings cached for 60 s.

### `ui/MainMenuScreen.kt`
Menú principal; título ligado a `BuildConfig.VERSION_NAME` con auto-shrink que nunca parte de línea.
**Botones (renombrados):** `menu_start_game` ahora es **"MUNDO LIBRE"** (open world sin campaña, spawn por
defecto) y `menu_load_game` es **"MODO HISTORIA"** (antes deshabilitado; ahora navega a `story_mode` vía
`onNavigateToStory`).
**🆕 Botón "HUELUM VS. GOYA" (`menu_street_fighter`, 2026-07-09 · PÚBLICO desde 2026-07-15):**
SIEMPRE visible (el gate por Modo Desarrollador se INVIRTIÓ: ahora el dev mode solo desbloquea a
RYU/KEN dentro del selector, ver §GATE INVERTIDO abajo); navega a la ruta `street_fighter`
(callback `onNavigateToStreetFighter` con default `{}`).

---

## 🥊 HUELUM VS. GOYA — modo de pelea 1v1 (`features/streetfighter/`) — 🆕 2026-07-09 · PÚBLICO desde 2026-07-15 (RYU/KEN dev-only)

> **NOMBRES:** en UI/UX el modo se llama **"HUELUM VS. GOYA"** (strings `menu_street_fighter`
> ES+EN); en CÓDIGO se conserva el nombre técnico (paquete `features/streetfighter/`, ruta
> `street_fighter`, clases `Sf*`/`StreetFighter*`) — NO renombrar el código. Los docs usan el
> nombre de UI; "Street Fighter" a secas se refiere al CLON original del que se portó el motor.

**ES:** Port FIEL del clon JS `StreetFighter-main/` (hermano del repo): pelea 1v1 clásica **Ryu
(jugador) vs Ken (CPU)** con los **sprites, escenario, HUD y sonidos originales**. Los assets viven en
**`app/src/main/assets/STREETFIGHTER/`** (`IMAGES/` 7 png, `SOUNDS/` 12 ogg, `DATA/` ryu.json+ken.json).
Los JSON se generaron **automáticamente desde Ryu.js/Ken.js** (77/78 frames con recorte+origen+pushbox+
hurtbox+hitbox POR FRAME y las 30 animaciones con sus frame-delays); regenerables con el conversor
**`tools/convert_streetfighter_frames.py`** (parsea el JS con ast; ajusta las rutas si mueves los repos). *(La 1ª iteración fue un port del
fork StreetFighter-Maths con quiz; se descartó — el tombstone `SfMathQuiz.kt` se BORRÓ el 2026-07-16.)*
**EN:** Faithful port of the sibling `StreetFighter-main/` JS clone: classic Ryu vs Ken (CPU) with the
original sprites/stage/HUD/sounds; per-frame boxes and all 30 animations converted to JSON from the JS.

| Tema / Concern | Archivo / File |
|---|---|
| Modelos puros (constantes, enums de estados 1:1 con el JS, SfBox, snapshots, SfInput) | `domain/models/streetfighter/SfModels.kt` |
| Carga del frame data JSON (Gson, cache estático; 🆕 remap a rejilla runtime para compartidos) | `features/streetfighter/data/SfFrameCatalog.kt` |
| 🆕 HOJAS COMPARTIDAS con el mundo (armado runtime desde SPRITES/*, normaliza lienzos heterogéneos, LRU 3) | `features/streetfighter/data/SfSharedSheets.kt` |
| 🆕 Transporte común del multijugador (interfaz; WS online / BT / LAN local) | `features/streetfighter/data/SfNetTransport.kt`, `SfMatchClient.kt`, `SfStreamPeer.kt` (base de stream), `SfBtClient.kt`, `SfLanClient.kt` |
| 🆕 TEMA intercambiable (escenario/HUD/sombra/splashes/proyectil/sonidos como DATOS; hoy `SF_CLASSIC_THEME`) | `features/streetfighter/data/SfTheme.kt` |
| Estado UI (peleadores, fireballs, splashes, cámara, timer, fin de pelea) | `features/streetfighter/viewmodel/StreetFighterState.kt` |
| VM `@HiltViewModel` (port de Fighter.js/BattleScene.js/Fireball.js: máquina de 30 estados, animación por frame-delays, cajas por frame, hit-freeze 15 frames, hadouken ↓↘→+P, IA CPU, timer 99, sonidos por SharedFlow) | `features/streetfighter/viewmodel/StreetFighterViewModel.kt` |
| View (Canvas: sprite sheets con flip por ancla, escenario de Ken con parallax/bandera/barco, sombras, HUD de hud.png, winnerText; SoundPool + tema de Ken en loop; joystick POW + 6 botones) | `features/streetfighter/ui/StreetFighterScreen.kt` |

- **Reloj de juego VIRTUAL:** `gameNow` solo avanza si no hay pausa/diálogo → los timers absolutos
  (animaciones, timer, hit-freeze) NO se desplazan al pausar. `requestAnimationFrame` → coroutine 16 ms.
- **Controles (2026-07-09b, MISMO diamante Xbox de POW):** joystick (←→ caminar, ↑ saltar, ↓ agacharse;
  secuencia ↓ ↘ → + puño = **especial/hadouken**, ventana 800 ms) + diamante A/B/X/Y idéntico a
  `ActionButtonsController`: **X = puño ligero · Y = puño medio · B = puño fuerte · A = patada**
  (fuerza de la patada según el joystick: neutro = ligera, adelante = media, atrás = fuerte;
  `onKickPressed` en el VM). El joystick no emite release → timeout 150 ms.
- **🆕 SEPARACIÓN motor⇄assets (2026-07-09b):** la View ya no conoce recortes/rutas — todo viene del
  `SfTheme` (data). Migrar a assets propios de POW (Prankedy, escenario ESCOM, sin copyright) = nuevos
  PNG+JSON+tema, CERO lógica. **Receta completa + prompts de generación (QWEN/ChatGPT):**
  `ASSETS_STREETFIGHTER_MIGRACION.md` (esta carpeta).
- **🆕 PRANKEDY JUGABLE (2026-07-10):** P1 ya es **`SfFighterId.PRANKEDY`** (sheet
  `IMAGES/Prankedy.png` + `DATA/prankedy.json`, generados con el pipeline del doc de migración);
  la CPU sigue siendo Ken. VM y View cargan frame data/sheets **por identidad** (cache perezoso).
  Su especial lanza la **broma del tanque** con confeti: frames `proj-*` del JSON del dueño
  (fallback al fireball del tema). `winnerRows` por personaje (Prankedy aún sin fila → sin texto).
- **🆕 PRANKEDY REGENERADO POR CROMA (2026-07-16):** sus 19 hojas originales alimentan un único
  arte para pelea y mundo mediante `tools/slice_sf_chroma_sheets.py` (orden obligatorio 01→19;
  14/15 refinan y sobrescriben puños/patadas). SF queda en 82 frames/30 animaciones, incluido el
  proyectil de confeti; el mundo usa Idle relajado 6, Walk 6, Run 8, Special 5 y Talk 4. Fuente e
  intermedios viven fuera del APK en `newSFAssets/`; receta: `GUIA_regeneracion_sprites_croma.md`.
- **🆕 SEÑOR DE LA TIENDA REGENERADO POR CROMA (2026-07-16):** deja `sharedSet` y usa
  `IMAGES/SenorTienda.png` + `DATA/senortienda.json` propios (82 frames/30 animaciones/proyectil
  de barrido-polvo); mundo Idle 6, Walk 6, Run 8, Special 5, Talk 4. Prankedy y Tienda ya no son
  ALPHA. Ambos dedicados fuerzan cuerpo 100 px en SF; mundo croma = 512²/cuerpo base 360 px.
  El detector genérico de continuidad KO marca `fall-4/fall-5.flipX` y la UI los espeja al tenderse.
- **🆕 REY GRUPERO + PAPARAZZI 1 REGENERADOS POR CROMA (2026-07-16):** ambos dejan
  `sharedSet` y usan hojas/JSON propios de 82 frames y 30 animaciones. Sus sets del mundo quedan
  uniformes: Idle 6, Walk 6, Run 8, Special 5 y Talk 4, todos en lienzo 512² con cuerpo base
  360 px. En SF, idle/caminatas = 100 px y el agachado usa alturas fijas 90→80→68 px sin
  encoger cabeza/torso. Paparazzi acepta PROJECTILE de 4 efectos y duplica con seguridad el
  cuadro central para completar 2 de vuelo + 3 de impacto. Ambos pierden badge ALPHA.
- **🆕 SELECTOR DE PERSONAJE + 7 JUGABLES (2026-07-10b):** el modo arranca en
  `inCharacterSelect=true` (el reloj de juego NO corre) con un overlay de tarjetas
  (`CharacterSelectOverlay`; preview = recorte `idle-1` vía **BitmapRegionDecoder** + trim de
  transparencia — NO se decodifican los 7 sheets completos). Roster: Ryu, Ken, **Prankedy, El
  Señor de la Tienda, Paparazzi 1, Paparazzi 5 y Rey Grupero** (hoy solo Paparazzi 5 conserva
  **badge ALPHA** dentro de ese bloque, `SfFighterId.isAlpha`; Prankedy/Tienda/Paparazzi 1/Rey
  ya tienen poses completas). `selectCharacter(id)` arranca la pelea (CPU = Ken,
  o Ryu si eliges a Ken); el menú de fin ganó **"Cambiar personaje"** (`backToCharacterSelect`).
  Los 4 nuevos se generaron originalmente con **`tools/gen_sf_frames_from_npc.py`** (77 poses desde el
  set NPC estándar Idle/Walk/Run/Special, con rotaciones/aplastados para golpes/reacciones/caídas
  y filtro de cuadros corruptos — `rg_w_4.webp` era 4×13 px) + `pack_sf_character.py` (parcheado:
  ya NO empaqueta `proj-*` inexistentes → esos personajes usan el fireball del tema). Prankedy,
  Tienda, Paparazzi 1 y Rey ya sustituyeron esas aproximaciones por sus hojas croma dedicadas.
- **🆕 RENOMBRE + MEJORAS ONLINE (2026-07-11b):** el modo se llama **"HUELUM VS. GOYA"**
  (solo strings user-facing; los ids internos siguen siendo street_fighter/Sf*). Online ganó
  **SALA PÚBLICA** (lista de espera; el server empareja con `QUICK_MATCH`) y **resumen de
  partidas activas**. Botones con estilo POW (`PowButton`, esquinas cortadas + vino).
  Offline: ahora también se ELIGE AL RIVAL (flujo de 3 pasos: peleador → rival → mapa;
  `selectCharacter(id, rivalId?)`). Detalle: `AUDIT_SF_MULTIPLAYER.md` (banner "SESIÓN 2").
- **🆕 SESIÓN 3 ONLINE (2026-07-15) — los 6 pendientes del AUDIT resueltos:** la LISTA DE
  ESPERA pública muestra el resumen (`sf_mp_rooms_summary`; estado `activeRooms`/`queueCount`)
  y las **salas activas como TARJETAS tocables** (las 'waiting' con hueco te unen directo:
  `joinRoomFromQueue` = `CANCEL_QUEUE`+`JOIN_ROOM`), con refresh de `LIST_ROOMS` cada 5 s;
  `cancelOnline` avisa (`CANCEL_QUEUE`+`LEAVE_ROOM`) antes de cerrar el WS; **`PowButton` se
  movió COMPARTIDO a `map_exterior/ui/components/PowButton.kt`**; sweep visual vino/dorado
  (`OutlinedTextField`, `TextButton`, diálogo de salida). Server: `JOIN_ROOM`/`CREATE_ROOM`
  sacan de la cola pública y el matchmaker no empareja a quien ya está en sala. Detalle:
  `AUDIT_SF_MULTIPLAYER.md` (banner "SESIÓN 3").
- **🆕 SESIÓN 3b (2026-07-15) — LOBBY con APROBACIÓN + BLUETOOTH local:** (1) tocar una sala
  de la lista ahora **SOLICITA unirse** (estilo AoE2): el HOST ve ACEPTAR/RECHAZAR
  (`REQUEST_JOIN`/`RESPOND_JOIN`/`JOIN_REJECTED`; unirse por CÓDIGO sigue directo).
  (2) **Multijugador por BLUETOOTH sin internet:** interfaz común
  **`data/SfNetTransport.kt`** (el VM solo habla con `transport`) + **`data/SfBtClient.kt`**
  (RFCOMM, UUID fijo; el HOST genera localmente los mensajes del relay). Sección
  "BLUETOOTH (sin internet)" en el menú 🌐: ANFITRIÓN (visible+accept) / BUSCAR RIVAL
  (emparejados+discovery). Permisos BT en Manifest (SCAN con `neverForLocation`;
  runtime solo al tocar la sección, Android 12+; SIN foreground service). Detalle y
  protocolo: `AUDIT_SF_MULTIPLAYER.md` (banner "SESIÓN 3b").
- **🆕 MULTIJUGADOR 1v1 (2026-07-11):** 3er servidor **`MultiplayerSF/`** (relay puro en
  Render FREE; salas por código de 4 letras). Cada cliente simula a SU peleador; el rival
  llega por red (~15 Hz) y el daño lo aplica el RECEPTOR (decide bloqueo con su estado real).
  Cliente: `data/SfMatchClient.kt` (OkHttp WS + warmup del free tier) + `SfOnlineStatus` en el
  estado + botón 🌐 en el selector (crear/unir), el anfitrión elige el mapa, countdown 3-2-1
  del server, revancha bilateral y victoria por abandono. `BuildConfig.SF_SERVER_URL`.
  **Detalle completo + protocolo + cómo desplegar: `AUDIT_SF_MULTIPLAYER.md`.**
- **🆕 MÚSICA de Prankedy + SELECTOR DE MAPA (2026-07-10e/f):** el tema del clon SF se
  reemplazó por **`SOUNDS/prankedy-persecucion.mp3`** ("Persecución", la de sus videos; vol 0.3).
  `SfTheme.fullBackgrounds` = lista de `SfStageBg(file, name)` con 6 fondos de pantalla completa
  (**ESCOM, Queso IPN, ESIME Azcapotzalco, CECyT 9, CECyT 2, Biblioteca UNAM** en
  `IMAGES/fondo_*.png`). La selección pre-pelea ahora es en **2 pasos**: PELEADOR → **MAPA**
  (`StageSelectOverlay`: miniaturas submuestreadas `inSampleSize=8` + tarjeta "Al azar" 🎲 +
  "← Cambiar peleador"; el flujo vive en la View — `pendingFighter`/`chosenBgFile` — porque el
  fondo es solo presentación). Se decodifica SOLO el fondo elegido; se dibuja con **parallax de
  cámara** (`drawFullBackground`) y sin las capas del muelle clásico. Lista vacía = escenario
  clásico (fallback).
- **🆕 BLOQUEO estilo SF (2026-07-10d):** caminar HACIA ATRÁS = cubrirse. En `applyAttackHit`
  (VM), si el defensor está en `WALK_BACKWARD` el golpe entra "chip": daño /4 (mín 1), medio
  retroceso, SIN pose de HURT ni splash ni puntos, hit-freeze corto y sonido "land". Aplica
  igual a melee y proyectiles. El chip PUEDE noquear (KO clásico por chip).
- **🆕 FUENTE arcade del HUD (2026-07-10d):** `SfTheme.letterFont` expone el abecedario A-Z +
  dígitos que ya venían dentro de `hud.png` (filas score del StatusBar.js). Con
  `drawFontText` se dibujan los TAGS de nombre (por `SfFighterId.shortName`, campo nuevo),
  los marcadores P1/P2 y el **"<PERSONAJE> WINS" de CUALQUIER peleador** — `winnerText.png` y
  los campos `nameTags`/`winnerRows` del tema se RETIRARON (el png se BORRÓ de assets el 2026-07-16).
- **🆕 Anchura por sección (2026-07-10d):** `place()` ganó `stretch_x`; el idle de Rey Grupero
  (1.25) y Señor Tienda (1.15) se ensancha porque su hoja fuente los dibuja más grandes y al
  normalizar por altura quedaban flacos respecto a su caminata.
- **🆕 PAUSA AUTOMÁTICA (2026-07-10c):** al bloquear el celular/minimizar (`ON_PAUSE` del
  lifecycle, patrón de WorldMapScreen), el VM hace `forcePause()` (nunca des-pausa; no aplica en
  selector/fin de pelea) y la música se pausa (`ON_RESUME` la reanuda). Overlay "PAUSA" con
  botón Continuar (`togglePause`). El reloj de juego virtual ya se detenía solo.
- **Quirks del JS portados a propósito:** el chequeo de hitbox SALE al primer hurtbox que no traslapa;
  los ataques ligeros se re-disparan desde el frame 2; LEGS cae a estados de cabeza; empate del timer lo
  gana el jugador (>=).
- **🆕 ASSETS COMPARTIDOS CON EL MUNDO — hojas armadas EN RUNTIME (2026-07-15d/16):** el roster
  subió a **14** (12 sin Modo Dev) y **8 peleadores ya NO tienen sprite sheet propio en el
  APK**: su hoja se ARMA EN RUNTIME desde los MISMOS assets que usa el mundo abierto
  (`SPRITES/PLAYER/` y `SPRITES/NPC/`) — un solo juego de sprites alimenta AMBAS modalidades.
  Compartidos: **Paparazzi 5**, **Lázaro, Estudiante (escomboy), Estudianta (escomgirl), Robot,
  Policía CDMX, Granadero y Paramédico** (nuevos). Piezas:
  - `SfFighterId.sharedSet: SfSharedSet(basePath, folder, prefix, flip)` — misma convención
    que `PlayerSkin`; `flip=true` en lázaro/escomboy (dibujados a la IZQUIERDA; SF exige DERECHA).
    Su `jsonAsset` apunta al TEMPLATE `sf_template.json` y su `spriteAsset` es VIRTUAL `RUNTIME/<X>.png`
    (solo key del mapa de imágenes — NUNCA abrirlo como asset).
  - **`data/SfSharedSheets.kt` (NUEVO):** port Kotlin de `gen_sf_frames_from_npc.py` +
    `pack_sf_character.py` — recorta cada cuadro a su bbox, **normaliza los LIENZOS
    HETEROGÉNEOS por código** 🆕 **POR ANIMACIÓN (2026-07-16)**: cada animación se mide
    (mediana de alturas de sus cuadros) y se escala a 100 px — antes la escala era única por
    personaje (medida del idle) y si el set traía otra acción dibujada a otra escala (lázaro
    idle 338×422 vs run 256², escomgirl run 542×681…) la figura CRECÍA/ENCOGÍA al caminar/
    correr/atacar; ahora mide lo MISMO en todas las acciones (`normalizeAnim`; el rebote
    natural DENTRO de una animación se conserva). Aproxima las 77 poses (rotaciones/
    aplastados, ALPHA) y pega la hoja 2560×2048 (misma RAM que decodificar el PNG que había).
    Cache LRU 3. Preview del selector = 1er cuadro del Idle del set del mundo (barato).
  - `SfFrameCatalog`: para compartidos parsea `ryu.json` y REMAPEA `src` a la rejilla runtime
    (`templateFrameOrder` fija el layout; cajas/timings de Ryu se conservan, igual que el packer).
  - **Quedan EMPAQUETADOS Ryu, Ken** (clon original/debug), **Prankedy y Señor de la Tienda**
    (19 hojas croma cada uno + `proj-*`; escala común 100 px y KO con `flipX` automático).
  - **Rey de las Bromas y Pepe NO entran** (no jugables por diseño; comentados en `PlayerSkin`).
  - **Se BORRARON** los 11 sheets+JSON duplicados originales; Señor de la Tienda volvió después
    con arte dedicado completo. `STREETFIGHTER/GEN/` siempre sale del APK. Los tools offline siguen sirviendo para personajes
    con ARTE PROPIO (hoja de referencia → `pack_sf_character.py`, como Prankedy);
    `gen_sf_frames_from_npc.py` ganó `PLAYER:<skin>` y flag `flip` por si se quiere volver a
    empaquetar offline.
- **🆕 RONDAS ESTILO SF — MEJOR DE 3 (2026-07-16):** cada combate son hasta 3 rondas; gana
  quien tome 2 (`ROUNDS_TO_WIN`). Una ronda termina por KO o por TIMEOUT (más vida gana;
  **EMPATE exacto → AZAR**: offline `Random` real; online azar DETERMINISTA con semilla
  compartida `roundNumber+wins` para que ambos lados sorteen al MISMO ganador sin mensajes).
  Entre rondas: "X WINS" congelado ~3.5 s → reset de ronda (HP/posiciones/timer frescos,
  mismos peleadores y mapa) → banner **"RONDA N / PELEA"** (~1.8 s, input y timer congelados,
  fuente arcade → strings `sf_round_banner`/`sf_fight_banner` SIN acentos). HUD: cuadritos
  dorados bajo cada nombre = rondas ganadas. El menú de fin SOLO al decidirse el combate.
  ONLINE/BT/LAN: nuevo mensaje **`ROUND_ENDED{winner}`** (relay puro, NO toca la fase de la
  sala) reconcilia las rondas intermedias; `MATCH_ENDED` queda solo para el combate decidido;
  gracia post-reset (`ROUND_GRACE_MS`) ignora snapshots/daño en vuelo de la ronda anterior.
  ⚠️ Requiere REDEPLOY de `MultiplayerSF/` (case nuevo). El quirk viejo "empate del timer lo
  gana el jugador (>=)" quedó SUSTITUIDO por el azar.
- **🆕 SERVIDOR LOCAL LAN — el jugador HOSTEA su sala (2026-07-16):** tercera vía de
  multijugador, estilo LAN party y SIN servidor: sección **"SERVIDOR LOCAL (misma red
  Wi-Fi)"** en el menú 🌐 — **CREAR SERVIDOR** (abre un `ServerSocket` TCP en el puerto fijo
  `SF_LAN_PORT=47645` y muestra TU IP para compartir) / **UNIRSE** (tecleas la IP del host;
  botón habilitado con IPv4 completa). Piezas: base común **`data/SfStreamPeer.kt`** (extraída
  de SfBtClient para no duplicar: sesión de stream, handshake HELLO→WELCOME verificado,
  heartbeat 10 s, "server" local del host, reintentos) + **`data/SfLanClient.kt`** (TCP,
  `tcpNoDelay`, `localIpAddress()` sin permisos vía `NetworkInterface`). Reusa TODO el flujo
  del VM (`roomCode="LAN"`, `lanMode/lanLocalIp/lanHostAddress` en el estado) y el overlay de
  REINTENTAR de BT (hint propio `sf_lan_error_hint`). **⚠️ PLAY STORE: cero permisos nuevos
  (solo INTERNET ya declarado), sin foreground service, tráfico device-to-device efímero → NO
  cambia Data Safety ni formularios.** Requisito de red: misma Wi-Fi o hotspot de uno de los
  dos (el aislamiento AP de algunas redes públicas puede bloquearlo → hint del overlay).
- **🆕 BT CONFIABLE E INTUITIVO (2026-07-16, fix del crash de permisos):** en dispositivo,
  ANFITRIÓN crasheaba con `Need android.permission.BLUETOOTH_SCAN … cancelDiscovery()`: ese
  flujo solo pide CONNECT+ADVERTISE y `cancelDiscovery()` EXIGE SCAN en Android 12+ → ahora
  TODA llamada a `cancelDiscovery()` es best-effort (`runCatching`, 3 sitios). Además el flujo
  BT se rediseñó para ser a prueba de dudas: (1) **HANDSHAKE de verificación** `BT_HELLO` →
  `BT_WELCOME` — `ROOM_JOINED`/`OPPONENT_JOINED` solo se entregan con la conexión VERIFICADA
  en ambos sentidos (watchdog de 6 s si el host no contesta; sockets "a medias" ya no crean
  sala); (2) el connect del invitado **reintenta ×3** (el 1º suele morir con el diálogo de
  emparejamiento); (3) UI por etapas: "Conectando…" → "verificando la conexión…"
  (`BT_HANDSHAKE`/`btHandshaking`); (4) **si BT falla, JAMÁS se cae en silencio al selector
  offline** (regla: elegiste BT → nada de acabar peleando vs la IA): overlay BLOQUEANTE
  `BtRetryOverlay` (consume los toques) con el error claro + **REINTENTAR** (repite ANFITRIÓN
  o la conexión al mismo rival, RE-PIDIENDO permisos si hace falta) + Cancelar como única
  salida explícita. VM: `btError/btRetryAddress/btHandshaking` + `onBtFailed`/`dismissBtError`;
  el host ignora intentos de conexión muertos sin handshake (sigue aceptando). (5) **BT
  apagado → SIEMPRE se pide encenderlo** (`ACTION_REQUEST_ENABLE`, diálogo del sistema) en la
  cadena `withBtPerms → whenBtEnabled → acción` de la Screen — cubre ANFITRIÓN, BUSCAR RIVAL
  y REINTENTAR; si el jugador lo niega, el siguiente toque lo vuelve a pedir (igual que los
  permisos). Gotcha del launcher: capturar y LIMPIAR `pendingBtAction` ANTES de invocarla
  (la acción re-encola el paso "encender BT"; un null posterior rompía la cadena).
- **🆕 COPYRIGHT: RYU/KEN SOLO EN BUILDS DEBUG (2026-07-15e):** sus assets (`Ryu.png`,
  `Ken.png`, `ryu.json`, `ken.json` + `kens-theme.ogg` sin uso) se MOVIERON al source set
  **`app/src/debug/assets/STREETFIGHTER/`** → el build por CABLE (Android Studio, debug) los
  tiene y funcionan como siempre; el **bundle de Play Store (release) NO los incluye**.
  Cierres para que release no crashee: (1) `SF_CLASSIC_THEME.imageFiles` ya NO precarga
  Ryu.png/Ken.png (las hojas las resuelve `SfSharedSheets` por peleador); (2) el default de la
  CPU en `StreetFighterState` pasó de KEN a REY_GRUPERO (el default se decodifica al abrir el
  modo); (3) `classicFightersUnlocked` exige **`BuildConfig.DEBUG` + Modo Desarrollador** (en
  release ni el dev mode los muestra); (4) los COMPARTIDOS usan el template
  **`DATA/sf_template.json`** (copia de cajas/timings que SÍ viaja en release; `ryu.json` ya
  no se toca en release); (5) `sanitizeNetFighter` (VM): si un rival con build de cable elige
  Ryu/Ken online/BT, el lado release lo pinta como PRANKEDY (mismatch visual entre lados,
  aceptado). Sigue PENDIENTE del clon en release: hud.png (fuente/barras), sonidos, sombra,
  splashes, fireball y kenstage (fallback) — ver `ASSETS_STREETFIGHTER_MIGRACION.md`.
- **🆕 GATE INVERTIDO (2026-07-15c):** el modo ya es **PÚBLICO** (botón del menú principal
  SIEMPRE visible, `MainMenuScreen` sin `developerMode`); ahora el **Modo Desarrollador solo
  desbloquea a RYU y KEN** (los del clon original) en el selector:
  `StreetFighterViewModel.selectableFighters` (snapshot al crear el VM, scope NavBackStackEntry
  → se relee al re-entrar al modo) y `classicFightersUnlocked` para el rival default offline
  (sin dev: Prankedy, o Rey Grupero si eliges a Prankedy — nunca Ryu/Ken). Los 3
  `CharacterSelectOverlay` reciben `fighters` del VM. Online, si un jugador CON dev elige a
  Ryu/Ken, el rival sin dev lo VE igual (los assets van en el APK; solo se bloquea elegirlos).
- **🆕 BT ROBUSTECIDO (2026-07-15c, "persistencia"):** `SfBtClient` ganó **HEARTBEAT cada 10 s**
  (el receptor lo ignora; NO sube al VM) + **cierre del socket al fallar una ESCRITURA** (destraba
  el readLoop de inmediato → el abandono se detecta en segundos aunque el stack BT no reporte);
  `onPeerConnected` resetea TODA la "sala" local (incl. `char1` — un rival nuevo ya no dispara
  CHARACTERS_SELECTED con la selección vieja del host); visibilidad del host 120→**300 s**; y el
  VM al recibir `OPPONENT_JOINED` con `battleEnded`/pelea corrida hace **reset limpio** a
  SELECTING (como REMATCH_ACCEPTED) — antes quedaba el selector sobre el fin de pelea.
- **🆕 SESIÓN 4 — pulido de red + HUD (2026-07-16b; detalle: `AUDIT_SF_MULTIPLAYER.md`
  banner "SESIÓN 4"):** (1) **BUGFIX server:** el relay de PLAYER_STATE devolvía el type
  equivocado (`{ type: 'OPPONENT_STATE', ...msg }` — el spread DESPUÉS pisaba el type) → el
  rival se veía CONGELADO en online; BT/LAN no lo sufrían. Corregido (spread PRIMERO); sale
  en el 1er deploy. (2) **Interpolación del rival:** lerp exponencial de x/y por tick
  (`NET_LERP_RATE=14`, snap a >80 px = reset de ronda/teleport); proyectiles remotos
  EXTRAPOLADOS por la edad del snapshot (tope 0.25 s). (3) **Sincronía del timer:** campo
  `timer` en PLAYER_STATE (interfaz + WS + SfStreamPeer→BT/LAN); solo lo manda el HOST y el
  invitado re-ancla si drift ≥2 s, gateado por la gracia de ronda. (4) **Fireball-vs-fireball**
  (`collideFireballPairs`: dos ACTIVE de dueños opuestos se revientan; offline y online
  simétrico). (5) **Roll-up del HUD** (`displayHp0/1`: la barra drena a 200 HP/s hacia el HP
  real; subir instantáneo → el reset de ronda rellena solo; `drawHud` pinta con estos).
- **Pendiente:** reconexión a sala tras caída, espectadores y anti-cheat (conscientes, ver
  AUDIT §4). *(i18n ✅ 2026-07-11; abrirlo sin dev ✅ 2026-07-15; fireball-vs-fireball y
  roll-up del HUD ✅ 2026-07-16 SESIÓN 4.)*

---

## Modo Historia / Campaña (`features/main_menu/`)

**ES:** Pantalla de campaña accesible desde **"MODO HISTORIA"** (ruta `story_mode`). Muestra el **prólogo**
(brote del Politécnico: Prankedy crea por accidente una sustancia corrosiva en la ENCB), un **selector de
escuela** y **"CARGAR PARTIDA"** (habilitado solo si hay una partida guardada). "COMENZAR" pasa por la
**intro** (`story_intro/{schoolId}`, "Listo para Iniciar") antes de entrar al mundo.
**EN:** Campaign screen from **"STORY MODE"** (`story_mode`): prologue + school picker + **"LOAD GAME"**
(enabled only if a save exists). "START" goes through the **intro** (`story_intro/{schoolId}`) first.

### `viewmodel/StoryModeViewModel.kt` + `StoryModeState.kt`
- Alcance **NavBackStackEntry** (se instancia con `viewModel(factory = Factory(context))`, así re-lee el
  guardado cada vez que se entra). `StoryModeState`: `selectedSchoolId` + `hasSave`/`savedSchoolId`/`savedAt`.
- API: `selectSchool(id)` (ignora escuelas no disponibles), `selectedSchool()`, `savedSchool(): CampaignSchool?`.
- Lee la partida de **`data/repository/CampaignRepository.kt`** (SharedPreferences `pow_campaign`:
  `saveCampaign(schoolId)`/`hasSave`/`getSavedSchoolId`/`getSavedAt`/`clearCampaign`).

### 🆕 Sistema de guardado COMPLETO en JSON con SLOTS — `data/repository/SaveGameRepository.kt`
**ES:** `CampaignRepository` (prefs) solo dice QUÉ escuela. El **estado completo** se guarda en **JSON con
hasta `SLOT_COUNT`=5 SLOTS** (`filesDir/pow_campaign_save_<n>.json`):
`GameSaveData(schoolId, lat, lon, health, wantedLevel, isDriving, isDrivingPoliceCar, vehicleModel,
vehicleColor, skin, nearbyNpcs: List<SavedNpc>, objectiveId, objectiveDone, interiorRoomId,
inventoryKeys: List<String>, lab1KeyFound, saveType, savedAt)` — `inventoryKeys`/`lab1KeyFound`
persisten el INVENTARIO y el progreso del puzzle de llave de ENCB_lab1 (puente
`WorldMapViewModel.currentInteriorInventory/…Lab1KeyFound`, restaurado en `ZombieGameViewModel`). API del repo:
`save(slot,data)`, `load(slot)`, `hasSave(slot)`, `anySave()`, `firstEmptySlot()`, `summaries(): List<SaveSlotSummary>`, `clear(slot)`.
- **Selector de slots:** `features/main_menu/ui/SaveSlotsDialog.kt` (`SaveSlotsMode.LOAD`/`SAVE`) muestra los 5
  slots con escuela + fecha. Lo hospeda **`MainActivity`** a nivel de Activity (un diálogo de GUARDAR común a
  mapa e interiores; otro de CARGAR en `story_mode`).
- **Guardado MANUAL** desde el ítem **"Guardar partida"** — disponible en el menú de Opciones del **mapa
  global Y en interiores** (`ZombieGameScreen`); ambos llaman `onRequestSaveGame` → abre el selector y eliges
  slot. **AUTO-GUARDADO** al salir/cerrar escribe en el **slot activo** (`campaignSlot`), solo si `inCampaign`.
- **API del VM (extensiones en `viewmodel/WorldMapSaveGame.kt`):** `saveGame(context, slot)`,
  `loadGame(context, slot): Boolean`, `buildSaveData(schoolId)`, `restoreSaveData(data)`,
  `setCampaignObjective(obj)`, `checkObjectiveProgress(loc)`. Campos del VM: `campaignSchoolId`, `inCampaign`,
  `campaignSlot` (los fija `MainActivity`).
- **"COMENZAR"** ocupa el **primer slot vacío** (no pisa otras partidas), fija el objetivo de la Misión 1 y
  spawnea en ESCOM. **"CARGAR PARTIDA"** abre el selector de slots y restaura el estado completo del slot
  elegido (posición/vida/buscado/vehículo/skin/objetivo + NPCs cercanos).

### 🆕 Intro como CÓMIC + Objetivos (Misión 1) 
**ES:** `StoryIntroScreen` ahora es un **visor de cómic**: muestra los 8 paneles de `StoryComicCatalog`
(`assets/STORY/INTRO/IntroPOW1..8.webp`, imágenes **HORIZONTALES** → la pantalla **fuerza orientación
landscape** mientras dura la intro vía `requestedOrientation` y la restaura al salir; `MainActivity` declara
`configChanges` para que el giro no recree la Activity). Tienen un **recuadro blanco** donde el código dibuja
el `text` de cada panel. Navegas tocando la mitad derecha (siguiente) / izquierda (anterior). **🆕 (2026-06-22) NO
hay botón "Saltar"** (`story_intro_skip` quedó sin uso): el pill superior-derecha **solo aparece en el ÚLTIMO panel**
como **INICIAR** (no se puede saltar el cómic; para avanzar se toca rápido). En el último panel (IntroPOW8), tocar → **INICIAR**: guarda la partida, fija spawn ESCOM + objetivo, y
**transiciona al primer interior JUGABLE de la campaña: el Lobby de la ENCB** (ruta `encb_lobby`), en vez
de ir directo al mundo. El lobby **reusa el motor de salas** (`ZombieGameScreen` con
`startRoom=ZombieRoomCatalog.ENCB_LOBBY_ID`, ver 05): mismos controles/cámara/colisiones/aura que el lobby
de ESCOM, pero sala `LOBBY` **sin zombis, sin mano zombi y sin puertas/waypoints** (`doors=emptyList()`),
con el banner **"Objetivo: Investiga qué pasó"** superpuesto. El lobby es la entrada de una **cadena LINEAL de
4 salas** `encb_lobby → encb_salon1 → encb_lab1 → encb_lab2` (todas LOBBY, fondos `INTERIORS/ENCB/*.webp`): cada
una tiene UNA puerta de AVANCE (waypoint X → `goToRoom(next)`), ninguna tiene salida al mapa entre medias (flujo
"atrapado"), y el banner de objetivo se mantiene en las 4 (`ZombieRoomCatalog.ENCB_STORY_ROOM_IDS`). Las
transiciones internas ocurren en el mismo `ZombieGameScreen`/VM. La navegación a la intro usa
`popUpTo("main_menu") { inclusive = true }`, lo que **destruye `StoryIntroScreen` y libera los bitmaps
IntroPOW1..8**. Si una imagen falta, se muestra un panel oscuro con el texto (no crashea).
- **🆕 OUTRO / 2ª parte de la intro (`ENCB_OUTRO`):** el **waypoint final de `encb_lab2`** (X) cierra la
  exploración y reanuda la narrativa: `goToRoom(EXIT_TO_STORY_OUTRO)` → `onPlayStoryOutro` → ruta `story_outro`,
  que **reusa `StoryIntroScreen`** con `sequenceId = StoryComicCatalog.ENCB_OUTRO_ID` (paneles
  `STORY/INTRO/IntroPOW9..11.webp`, vía la nueva `StoryComicCatalog.sequence(id)`). Al ser otra pantalla, la
  **UI de juego (joysticks/indicadores/objetivo) queda oculta**. Al terminar `IntroPOW11` (o "Saltar"/"Volver")
  **`MainActivity` llama `setStorySpawn(MissionCatalog.MISSION1_SPAWN_LAT, _LON)` (19.50102, -99.14421 =
  CHECKPOINT de la Misión 1, donde se entra al mapa global; antes eran las coords de la ENCB)** y
  entra al mundo: `navigate("world_map") { popUpTo("story_outro"){inclusive=true} }`. `setStorySpawn` activa
  `inCampaign=true`. El **MUNDO LIBRE** del menú NO se altera: `onNavigateToMap` fuerza `inCampaign=false` y
  `fetchCurrentLocation`→`updateInitialLocation(SPAWN_ESCOM_LAT/LON)` (spawn ESCOM canónico intacto).
- **🆕 Prankedy se TELETRANSPORTA contigo:** si usas el teletransporte (`teleportTo`), el acompañante (fase
  `HIRED`) se reubica en tu nueva posición vía `WorldMapPrankedy.warpPrankedyCompanionTo` →
  `PrankedyManager.warpTo` (su `location` tiene private set). Sin esto se quedaba caminando media ciudad.
- **🆕 REINTENTAR MISIÓN (pantalla "MISIÓN FALLIDA"):** ya no vuelve sola al menú; muestra **"REINTENTAR
  MISIÓN"** (recarga el slot activo vía `retryCampaignMission` → `loadGame`, re-arma policía y limpia el
  estado) y **"Salir al menú"**. Al reintentar, **Prankedy vuelve contigo** (`respawnPrankedyCompanionHere`,
  no depende del gate de vecindario ENCB del game loop). Callback `onRetryMission` (WorldMapScreen→MainActivity).
- **🆕 MORIR en una misión = MISIÓN FALLIDA (checkpoint):** si te matan estando en una misión de campaña
  (`inCampaign` && objetivo `ESCOLTAR_PRANKEDY`/`INGRESAR_ESCOM`/cualquier `m2_*`), `triggerWastedSequence`
  NO hace el respawn normal cerca del lugar de muerte: tras un WASTED breve pone `showMissionFailed = true`
  → reintentas desde el **último checkpoint** (mismo botón REINTENTAR). Fuera de misión, respawn normal a ~77 m.
- **🆕 Persecución final de la Misión 1 = HUIDA de Prankedy (⚠️ RENOMBRADA `mission2*`→`mission1Chase*`,
  2026-07-03):** al arrancar la persecución, Prankedy ya **no te sigue**: `runMission1ChasePrankedyEscape`
  (en vez de `runPrankedyTick`) lo hace **CORRER hacia la puerta de la ESCOM** (reusa `tickFollow` con la
  puerta como objetivo); la **policía lo persigue** (en `runMission1ChaseTick`, `target` = Prankedy mientras
  está vivo; aparecen por el **lado contrario a la puerta**) y la **multitud sale** de la puerta. Al llegar
  (~6.6 m, `MISSION1_CHASE_PRANKEDY_ENTER_DEG`) **ENTRA** (desaparece) con el diálogo **"Ahí nos vemos"** y
  `mission1ChasePrankedyEntered=true` (deja de animarse; la policía pasa a perseguir al jugador). Renombres:
  `startMission1Chase`, `isMission1ChaseActive`, `consumePendingMission1ChaseIntro`, estado
  `pendingMission1ChaseIntro`, crowd `mission1ChaseCrowd`, cómic `MISSION1_CHASE_INTRO_ID`, ruta nav
  `story_mission1_chase`.
- **🆕 REGISTRO/SELECTOR DE MISIONES (2026-07-04, estilo Witcher):** el diálogo R7 tras la escolta
  se ELIMINÓ (la escolta encadena directo con el cómic + chase). El jugador SIEMPRE está en mundo
  libre; sigue/pausa misiones desde **Opciones → "Misiones"** (`MissionLogDialog`; estados
  bloqueada/disponible/activa/completada; `completedMissions` persistida). Las misiones 2 y 3 ya
  NO arrancan solas. Ver `CAMPAIGN/00_OVERVIEW` y 09.
  - **🆕 (2026-07-04b):** "Misiones" también en el menú de Opciones de **INTERIORES** — el diálogo
    se hospeda a nivel `AppNavGraph` (`MissionLogHost`, patrón SaveSlotsDialog, VM del mundo
    Activity-scoped). Las ✔ completadas ganan **REJUGAR** (sin tocar el progreso guardado:
    `replayingMissionId` transitorio + clamp de fases en `buildSaveData`). Con Modo Desarrollador:
    seguir misiones 🔒 y **"TP al objetivo"**. Además, "Elegir personaje" del MAPA GLOBAL ahora es
    solo de Modo Desarrollador (el de interiores se queda). Detalle en 09.
- **🆕 MISIÓN 3 · "Regreso a la ENCB" (2026-07-04):** cordón de granaderos con sigilo + asalto
  interior ENCB con zombis + evidencia 🧪 → recompensa **primera ARMA DE FUEGO** (`hasFirearm`,
  desbloquea RANGED en campaña). La mochila de la M2 ahora **desbloquea los 4 slots** del
  inventario. Ver `CAMPAIGN/03_MISSION_3.md`.
- **🆕 MISIÓN 2 · "El rumor" (2026-07-03):** campaña REAL post-Misión 1, 5 fases sobre el campus ESCOM:
  esconderse de la policía de búsqueda → rumor zombie (2 estudiantes, conversación con subtítulos que se
  PAUSA si te alejas) → primer brote público (conversión + sometimiento + radio "refuerzos en la ENCB") →
  plática con Prankedy (REY GRUPERO + mochila) → salón `escom_salon_m2` con LATA APESTOSA y mochila 🎒.
  Fase persistida en `GameSaveData.mission2Phase` (JSON). Guion/constantes: `mission2/Mission2.kt`; tick:
  `WorldMapMission2.kt`; subtítulos: `WorldMapState.storyConvoSpeaker/Text` (overlay en
  `WorldMapScreenOverlays`). Detalle completo: `CAMPAIGN/02_MISSION_2.md`.
- **🆕 Controles EN VIVO:** al **Guardar** D-pad/joystick (escala/swap) en Ajustes, un `LaunchedEffect` de
  `MainActivity` (key = settings COMMITTEADOS) llama `updateControlSettings`, así el cambio se aplica sin salir
  al menú y volver a entrar.
- **🆕 Audio: la música se CORTA al volver a un menú.** El listener de orientación de `MainActivity`
  (`OnDestinationChangedListener`), al entrar a una ruta de menú (`main_menu`/`story_mode`/`settings`/
  `collectibles`), llama `stopInvestigarMusic`/`stopLugarSeguroMusic`/`stopMainMusic`/`stopPrankedyRemixMusic`/
  `stopAllStorySounds`, así ningún MediaPlayer de fondo sigue sonando al salir del juego/cómic.
- **🆕 Prankedy ACOMPAÑANTE (solo campaña ENCB):** al entrar al mundo en la ENCB,
  `WorldMapPrankedy.maybeSpawnPrankedyCompanion` (en el game loop, gateado por `inCampaign` && vecindario ENCB,
  bandera `prankedyCompanionActivated` re-armada por `setStorySpawn`) enciende a Prankedy en fase **`HIRED`**
  (`spawnCompanion`): te **sigue** con animaciones `p_walk`/`p_run` (sin atacarte) y fija el objetivo
  **`MissionCatalog.ESCOLTAR_PRANKEDY`** → el widget muestra **"Lleva a un lugar seguro a Prankedy"**. En
  MUNDO LIBRE no aparece (sigue el Prankedy hostil manual del menú de Opciones). Ver 03 (fase HIRED) y 04.
- **🆕 Línea GPS de campaña (ENCB → ESCOM):** al encender el acompañante, `maybeSpawnPrankedyCompanion`
  calcula con **A*** (`findRoadRoute`, sobre la red vial) una ruta de la ENCB a la ESCOM ("lugar seguro") y la
  guarda en **`WorldMapState.campaignRouteWaypoints`**. Se dibuja como **línea VERDE VIVO (`#00E676`) sólida y gruesa** por encima de las
  teselas y **por debajo de personajes/HUD**: en OSM nativo es un `Polyline` rojo (tag `route_overlay_tag+900`,
  `overlays.add(0,…)`); en web/Leaflet la función JS **`updateCampaignRoute`** (`WorldMapLeafletHtml`) dibuja un
  `L.polyline` rojo en el overlayPane. **Desaparece** cuando el jugador entra a ~100 m de la ESCOM
  (`maybeHideCampaignRouteNearEscom` vacía la lista en el game loop). Solo en campaña.
- **🆕 Editor in-game del cuadro de texto:** como el recuadro blanco está a distinta altura por panel, el botón
  **"Editar"** activa un editor para **mover** (arrastrar o Subir/Bajar), **redimensionar** (Alto ±) y cambiar
  el **tamaño de letra** (Letra ±) del cuadro, **por panel**. Se persiste en
  **`data/repository/StoryLayoutRepository.kt`** (`StoryBoxLayout(topFrac, heightFrac, fontSp)` en
  SharedPreferences `pow_story_layout`); "Guardar" guarda ese panel, "Todas" aplica a todos. También ajusta
  el **ancho** (`Ancho ±`, `boxWidthFrac`) — el cuadro va centrado. Los defaults viven en `ComicPanel`
  (`boxTopFrac/boxHeightFrac/boxWidthFrac/fontSp`). **⚠️ El ajuste se guarda SOLO en el dispositivo**
  (SharedPreferences), no en el repo: por eso hay un botón **"Exportar"** que vuelca TODOS los paneles a un
  **JSON** (vía selector de archivo) y además escribe en **Logcat** (tag `STORY_LAYOUT`) las líneas
  `ComicPanel(...)` listas para PEGAR como defaults en `StoryComicCatalog.kt` (así la config queda en el código).
- **Objetivos:** `domain/models/campaign/CampaignObjective.kt` (+ `mission1/Mission1.kt`; `MissionCatalog.first = ir_encb`).
  `title`/`description` son **`@StringRes`** (`titleRes`/`descriptionRes`, F 2026-06-23; resueltos en ObjectivesWidget/VM). Al COMENZAR se fija el objetivo; el **game loop** (`checkObjectiveProgress`, solo si
  `inCampaign`) lo marca cumplido al entrar en `arriveRadiusMeters` del destino. **Widget de Objetivos
  SIEMPRE visible** (`ui/components/ObjectivesWidget.kt`, HUD **arriba-centro** con título + distancia; se
  dibuja UNA sola vez — antes había un duplicado arriba-izquierda, ya eliminado). El
  objetivo se guarda/restaura en `GameSaveData`. ⚠️ Las coords de la ENCB en `MissionCatalog` son aproximadas.

### `ui/StoryModeScreen.kt` + `ui/StoryIntroScreen.kt`
- `StoryModeScreen`: prólogo + tarjetas de escuela (`SchoolCard`) + "CARGAR PARTIDA" (on solo con guardado;
  reanuda en la escuela guardada vía `onLoadCampaign`) + "COMENZAR" (`onStartCampaign` → navega a la intro) +
  "VOLVER". Usa `windowInsetsPadding(WindowInsets.systemBars)` para no chocar con la barra de navegación.
- `StoryIntroScreen` ("Listo para Iniciar"): **placeholder** narrativo (futuros banners/sprites del prólogo).
  Al **INICIAR** (`onBegin`) `MainActivity` **guarda** la partida (`campaignRepository.saveCampaign(school.id)`),
  fija el spawn (`setStorySpawn`) + objetivo y navega a **`encb_lobby`** (Lobby ENCB) con `popUpTo("main_menu")
  { inclusive = true }`; el lobby sale a `world_map`. "CARGAR PARTIDA" entra directo a `world_map` **sin** guardar de nuevo.
- El guardado lo escribe **`MainActivity`** (punto de DI), no las Views. La partida ligera (escuela) va a
  `CampaignRepository`; el **estado completo** (posición/vida/buscado/vehículo/skin/NPCs) va al JSON de
  `SaveGameRepository` (ver "Sistema de guardado COMPLETO" arriba).

### `domain/models/SchoolCatalog.kt`
`CampaignSchool(id, displayName, latitude, longitude, available)` + `object SchoolCatalog.schools`.
Solo la 1ª está `available = true` (= `TeleportCatalog.zones[0]`); las otras quedan en desarrollo
(`available = false`, deshabilitadas en la UI). `displayName` es nombre propio (no se traduce).
> **🆕 Nombres visibles = institución:** `displayName` ahora muestra la institución: **`escom` → "IPN"**
> y **`fes_aragon` → "UNAM"** (UAM se queda como "UAM"). Los **`id` NO cambian** (`"escom"`/`"fes_aragon"`
> alimentan spawn/guardado); solo cambia el texto que ve el jugador (`SchoolCard` usa `school.displayName`).
> El botón **"COMENZAR" → "NUEVA PARTIDA"** (`R.string.story_start`, es/en).

### `WorldMapViewModel.setStorySpawn(lat, lon)` (miembro)
Fuerza el punto de aparición de la campaña y re-arma las compuertas de carga
(`isMapReady`/`isRoadNetworkReady`/`npcsWarmedUp = false`) para descargar el mundo alrededor de la escuela
elegida. A diferencia de `updateInitialLocation` (gateada por `isLoadingLocation`, ya consumida en
`MainActivity.onCreate`), **no** está gateada. Sin gemelo de extensión.

---

## Coleccionables / Collectibles (`features/main_menu/`)

- **`viewmodel/CollectiblesViewModel.kt`** (Activity-scoped, `Factory(context)`): lee
  `CollectibleRepository.allCollectiblesFlow` → inventario reactivo.
- **`ui/CollectiblesScreen.kt`**: pantalla de inventario (ruta `collectibles`). El botón inferior dice
  **"VOLVER"** (`R.string.menu_back`, antes `menu_return` = "VOLVER AL MENÚ"); diseño `Button` rojo
  (`0xFF6B1C3A`) con `CutCornerShape(16,16)` — el MISMO que reusa el botón "VOLVER" de Ajustes.
- **Lógica de spawn/recogida** está en el open world: `WorldMapCollectiblesLogic.kt` (ver 04). 6
  coleccionables de lore sembrados en Room (`CollectibleRepository`). Una **Mano Zombi** especial solo
  aparece dentro del bounding box de ESCOM y dispara la cinemática al minijuego.
- En el open world: spawn 1 cada ~1 s a 300–600 m (snapeado a calle), prompt a 15 m, **X** recoge,
  diálogo temático (`CollectibleClaimDialog.kt`).

---

## Ajustes / Settings (`features/settings/`)

### Modelos
```kotlin
enum class ControlType { DPAD, JOYSTICK }                 // models/ControlType.kt
enum class SettingsCategory { ... }                       // models/SettingsCategory.kt (pestañas)
```

> **🆕 Default = `JOYSTICK`:** el tipo de control por defecto es **JOYSTICK** (antes DPAD). Lo fija
> `SettingsRepository.getControlType()` (default JOYSTICK) y se replica en los defaults de los `*State`
> (`WorldMapState`, `SettingsState.controlType`/`tempControlType`; los interiores ya eran JOYSTICK). Las
> "flechitas" (DPAD) siguen disponibles en Ajustes → Controles.

### `viewmodel/SettingsViewModel.kt` + `SettingsState.kt`
**ES:** Pestañas: Mapa / Controles / Gameplay / Interfaz / **🆕 Audio**. **Los controles son "staged":** los cambios
viven en campos `temp*` y **solo se aplican al pulsar GUARDAR**; salir descarta.
**EN:** Tabs: Map / Controls / Gameplay / Interface. **Controls are staged:** changes live in `temp*`
fields and **only apply on SAVE**; leaving discards.

- `selectCategory(category)`, `changeMapProvider(provider)`, `toggleCacheWidget/FpsWidget(enabled)`,
  `toggleZoomWidget(enabled)` y `toggleSpeedometer(enabled)` (ambos persisten; pestaña Interfaz:
  widget de nivel de zoom en vivo + velocímetro km/h visible solo al conducir, default activado).
- `toggleCoordsWidget(enabled)` (persiste; pestaña Interfaz: **widget de coordenadas X/Y/Z**, default
  oculto). Mismo patrón que zoom/velocímetro: `SettingsRepository.get/saveShowCoordsWidget`, se empuja al
  mapa en vivo desde `MainActivity` (`worldMapViewModel.toggleCoordsWidget`) y los interiores lo leen al
  entrar (`ZombieGameState`/`InteriorState.showCoordsWidget` desde el repo). Render → ver 04/06.
- **🆕 Audio (pestaña nueva):** `changeMusicVolume(v)` / `changeSfxVolume(v)` (0f..1f, persisten al instante en
  `SettingsRepository.get/saveMusicVolume`/`SfxVolume`). `SettingsState.musicVolume`/`sfxVolume` (default 1.0).
  La UI son 2 sliders (`AudioSettings` en `SettingsScreen`). `MainActivity` los empuja en vivo al
  **`SoundManager`** (`setMusicVolume`/`setSfxVolume`); además `SoundManager` los lee del repo en su `init`
  (se aplican al arrancar). Música = `MediaPlayer.setVolume`; efectos = se multiplican en cada `play()` del
  `SoundPool` y se ajustan en vivo los streams en loop. Nueva categoría `SettingsCategory.Audio`.
- **🆕 Fix "siempre se escuchan pasos" (2026-06-22):** `SoundPool.load()` es ASÍNCRONO; reproducir un loop antes
  de que el sample cargue devolvía 0 y en algunos equipos dejaba un **loop HUÉRFANO** (sin `streamId` capturado)
  que `stopWalk()` no podía parar. Además `SoundManager` es **singleton** compartido por el mapa exterior (game
  loop, `Dispatchers.Default`) y los interiores (drivers en coroutines), así que `play`/`stop` se entrelazaban.
  Fix: (a) `setOnLoadCompleteListener` → set `loadedSounds`; `playWalk/Run/Car` SOLO reproducen si su sample ya
  cargó (se acabó el reintento que generaba huérfanos); (b) `@Synchronized` en `playWalk/stopWalk/playRun/stopRun/
  playCar/stopCar`. Ver 09.
- Staged: `changeControlType(type)`, `changeControlsScale(scale)`, `toggleSwapControls(swap)` → escriben
  `tempControlType/tempControlsScale/tempSwapControls`.
- `saveControlsSettings()` (commit + persiste vía `SettingsRepository` + empuja al mapa),
  `discardControlsChanges()` (al salir).
- **🆕 TUTORIAL DE CONTROLES optativo (2026-07-11, `settings/ui/ControlsTutorial.kt`):** overlay
  paginado (páginas con el botón A/B/X/Y dibujado con su color real + título + explicación) en dos
  variantes: `exteriorTutorialPages()` (mover/correr/X interactuar/B golpe/Y coche/Y-hold teleport)
  e `interiorTutorialPages()` (sin conducción; B ataque, Y-hold menú de golpe+inventario).
  `ControlsTutorialFirstRun(interior)` se monta al final del Box raíz de `WorldMapScreen` (gated a
  mundo cargado) y de `ZombieGameScreen`: OFRECE el tutorial UNA vez por mundo (diálogo "¿Ver
  tutorial?"; flags `TUTORIAL_EXTERIOR/INTERIOR_SEEN` en `SettingsRepository`, se marcan acepte o
  no). En **Ajustes → Controles** hay sección "Referencia y tutorial" con 2 botones que lo re-abren
  cuando se quiera (en un `Dialog` fullscreen). Strings `tutorial_*`/`settings_tutorial_*` (ES+EN).
  Composables SIN VM (overlays puros); la lectura puntual de prefs sigue el patrón MissionLogDialog.
- `toggleRoadNetwork(show)`. `Factory(context)`.
- **Jugabilidad / Gameplay:** `changeNpcDensity(v: Float)` (0.4–1.6, persiste al instante),
  `toggleNpcEmojiLod(b)` y `toggleNpcFullEmoji(b)`. La pestaña **Jugabilidad** tiene:
  - **"Cantidad de NPCs"** (slider) → `SettingsState.npcDensity` → `WorldMapViewModel.setNpcDensity` →
    `NpcAiManager.userPopulationFactor` (se combina con gama del teléfono + densidad urbana, ver 03).
  - **"Optimizar dibujado de NPCs"** (switch; antes se llamaba "Optimizar para gama baja") → `npcEmojiLod`
    → `WorldMapState.npcEmojiLod` → render LOD de emojis (NPCs lejanos como 🧍🚗🧟, ver 04).
    Default = `isLowRamDevice` (`SettingsRepository`).
  - **"Optimizar para gama baja"** (switch NUEVO) → `npcFullEmoji` → `WorldMapState.npcFullEmoji` →
    TODOS los NPCs como emoji 🧍🚗🧟👮 sin importar distancia, en los TRES renderers (OSM nativo,
    Google nativo y web). Default = false.
  - Todos persisten en `SettingsRepository` (`getNpcDensity`/`getNpcEmojiLod`/`getNpcFullEmoji`) y se
    aplican **en vivo** al mapa desde `MainActivity` (llaman a `settingsViewModel` + `worldMapViewModel`).

> **🆕 Pestaña "Cuenta" (`SettingsCategory.Account`):** `AccountSettings` en `SettingsScreen` — inicio de
> sesión con Google (Firebase Auth), mostrar la sesión actual, cerrar sesión y **"Eliminar mi cuenta y
> datos"** (con diálogo de confirmación). Recibe `authManager` + `onAccountDeleted` desde `MainActivity`.
> El borrado elimina la cuenta en Firebase (`AuthManager.deleteAccount`) y los datos locales (slots de
> `SaveGameRepository` + `CampaignRepository`). El multijugador exige sesión; el juego local no. Ver 09.

### `ui/SettingsScreen.kt` (+ `ui/SettingsSections.kt`)
**2026-06-23 (E):** las 7 secciones (`GameplaySettings`/`MapProviderSetting`/`ControlsSettingsConfig`/`LanguageSetting`/
`AudioSettings`/`AccountSettings`/`DiagnosticWidgetsSetting`) se extrajeron a `SettingsSections.kt` (`internal`, mismo paquete);
`SettingsScreen` quedó con la raíz + `CategoryItem*` + `SettingsContent`. Pestañas + sliders. Escala adaptativa 60%–140% (cap 100% en portrait), swap de zurdos, botones A/B/X/Y.
`GameplaySettings` (Jugabilidad): slider de cantidad de NPCs + switches "Optimizar dibujado de NPCs"
(LOD) y "Optimizar para gama baja" (emoji total). `DiagnosticWidgetsSetting` (Interfaz): widgets de
caché, FPS, **zoom** (nivel de zoom actual en vivo) y **velocímetro** (km/h al conducir).
> **🆕 Botón "VOLVER" (antes "SALIR AL MENÚ"):** el botón inferior de salida (portrait y landscape) ahora
> dice **"VOLVER"** (`R.string.menu_back`) con el MISMO diseño que coleccionables: `Button` relleno rojo
> `0xFF6B1C3A`, `CutCornerShape(16,16)`, `height 56.dp` + `shadow`. Su acción NO cambió (`onExitToMainMenu`
> → vuelve al menú principal). `R.string.settings_exit_to_menu` queda sin uso.
> **🆕 Descripciones justificadas:** en `DiagnosticWidgetsSetting` cada fila envuelve título+descripción en
> `Column(Modifier.weight(1f))` y la descripción usa `TextAlign.Justify`; así los textos de 2+ líneas (p. ej.
> Coordenadas X/Y/Z) se reparten a lo ancho (la última línea queda a la izquierda → en 1 línea no se nota).
> Patrón a reusar en cualquier widget que crezca a 2+ líneas.
> **🆕 Modo Desarrollador (`developerMode`, Interfaz, default oculto):** switch nuevo arriba de la pestaña
> Interfaz. Mismo patrón que zoom/velocímetro: `SettingsRepository.get/saveDeveloperMode`,
> `SettingsState.developerMode`, `SettingsViewModel.toggleDeveloperMode`, wired en `MainActivity`
> (`onDeveloperModeToggled`). Pensado para ocultar botones de prueba en la versión final: las pantallas que
> tengan esos botones deben observar `developerMode` para mostrarlos/ocultarlos (aún por cablear caso por caso).
> Strings `settings_developer_mode`/`_desc` (es+en).
> **🆕 Ajustes desde el JUEGO se mantiene HORIZONTAL:** la ruta de Ajustes acepta el arg `fromGame`
> (`settings?fromGame={fromGame}`, BoolType default false). Las navegaciones in-game (WorldMapScreen, ZombieHud,
> encb_lobby) llaman `navigate("settings?fromGame=true")`; las del menú principal siguen en `settings`. El
> listener de orientación de `MainActivity` lee el `fromGame` del Bundle del destino: si es `true`, Ajustes NO
> cuenta como menú vertical y queda `SENSOR_LANDSCAPE`. Sigue la convención del 09 (orientación SOLO por ruta;
> las pantallas no la fijan).

---

## ShineCTO easter egg (`features/interiores/shinecto/`)

> **🆕 Reestructura:** antes `features/shinecto/`; ahora **`features/interiores/shinecto/`** (subpaquete de
> la umbrella `interiores`). Usa `PlayerView`/`PlayerHealthBarFixed` desde **`interiores.core.ui`** (antes de
> `zombie_minigame`). Su asset es `PLACES/shine_cto/` (antes `LUGARES/shineCTO/`). / Now under the `interiores`
> umbrella; shared player views come from `interiores.core.ui`.

**ES:** Interior easter-egg accesible al acercarse a `ShineCTOLocation` (lat 19.459049, lon -99.163251,
`TRIGGER_RADIUS` 0.00015). Mini-juego social de bebidas.
**EN:** Easter-egg interior reached by approaching `ShineCTOLocation`. Social drinks mini-game.

### `viewmodel/ShineCTOViewModel.kt` + `ShineCTOState.kt`
```kotlin
data class ShineCTOState( ... )
data class ActiveDrink( ... )
enum class ShineCTOInteractable(val label: String) { ... }
```
- Movimiento: `moveByAngle`, `moveDirection`, `applyMovement`, `effectiveStep(s)`, `setRunning`, `setSpecial`.
- Interacción: `updateNearbyInteractable(px, py)`, `onInteract(): Boolean`, `consumeDrink()`,
  `claimShineCollectible()`, `dismissShineClaimedPopup()`. Spawns: `spawnInitialDrinks`,
  `randomDrinkPosition(currentDrinks)`. `Factory(...)`.

### UI
- `ui/ShineCTOScreen.kt` — interior (ruta `shinecto_interior`).
- `ui/EasterEggDiscoveryDialog.kt` — diálogo de descubrimiento (disparado por `showShineCTODiscovery`
  en `WorldMapState`).

---

## Tema / Theme (`ui/theme/`)
`Color.kt`, `Theme.kt`, `Type.kt` — Material 3. Sin lógica de negocio.
