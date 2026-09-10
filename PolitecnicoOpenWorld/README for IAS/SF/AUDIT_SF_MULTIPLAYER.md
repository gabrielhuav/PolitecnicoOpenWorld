# AUDIT · Multijugador 1v1 del modo pelea "TITULACIÓN POR COMBATE" (2026-07-11)

> **🆕 2026-07-26d (Opus 4.8) — LAN funciona ✅ + AUTODESCUBRIMIENTO (punto ④, dueño):** con el fix
> del hilo (26c) el Wi-Fi 1v1 ya conecta y arranca. Nuevo: **`SfLanDiscovery`** — el ANFITRIÓN emite
> una baliza por **UDP broadcast** (`255.255.255.255:47646`, cada 1.5 s) y el INVITADO la escucha
> (con `MulticastLock`) → ve las **partidas de la MISMA red como tarjetas tocables**, sin teclear IP
> (se une por la IP origen del paquete). La baliza para al entrar el rival (`OPPONENT_JOINED`). VM:
> `startLanDiscovery`/`stopLanDiscovery` + estado `lanDiscovered`; el menú online escucha mientras
> está abierto (`DisposableEffect`). Se conserva el campo de IP manual como respaldo. Permiso nuevo:
> **`CHANGE_WIFI_MULTICAST_STATE`** (NORMAL, sin impacto en Play, como `WAKE_LOCK`). ⚠️ Falta prueba
> en 2 dispositivos (algunos routers bloquean broadcast → el campo de IP queda de fallback).


> **🆕 2026-07-26c (Opus 4.8) — LAN "muere al elegir peleador": CAUSA REAL (logcat definitivo):**
> `escritura falló (SELECT_CHARACTER) → NetworkOnMainThreadException`. **NO era power-save, ni idle,
> ni Wi-Fi.** El VM llama `transport.selectCharacter/sendPlayerState/…` desde el **HILO PRINCIPAL**, y
> Android PROHÍBE I/O de red (TCP) en Main → lanza `NetworkOnMainThreadException`; `sendRaw` la
> atrapaba y **CERRABA el socket** (el enlace estaba sano — los heartbeats fluían cada 1 s en ambos
> lados). **BT sí funcionaba porque los sockets Bluetooth están EXENTOS** de esa regla de StrictMode;
> los TCP no. **FIX:** en `SfStreamPeer` todas las escrituras van por un **`writeExecutor`
> (single-thread)** → fuera de Main y serializadas. Se apaga en `close()`. Las hipótesis previas
> (WifiLock/WAKE_LOCK, heartbeat frecuente) NO eran la causa; se conservan como red de seguridad
> menor (heartbeat 4 s + WifiLock para el idle real de partidas largas). Logs de diagnóstico
> (`heartbeat →` por-latido) retirados; se conservan `readLoop: EOF/excepción` y `escritura falló`.


> **🆕 2026-07-26 (Opus 4.8) — LAN "muere tras elegir peleador": CAUSA CONFIRMADA por logcat:**
> El logcat del HOST mostró: handshake OK (BT_HELLO→WELCOME→OPPONENT_JOINED) y **reconexión a los ~8 s**
> SIN "escritura falló" en el host → el **GUEST cerraba su socket durante el IDLE** (mientras se elige
> peleador), **ANTES del primer heartbeat (10 s)**. **Causa:** el socket TCP de LAN queda idle sin
> tráfico ~6-8 s y Android/Wi-Fi lo MATA antes del heartbeat. BT no lo sufre (RFCOMM no idle-killea).
> - **✅ FIX principal:** heartbeat FRECUENTE (`HEARTBEAT_FIRST_MS=1500`, `HEARTBEAT_MS=3000`, antes
>   10 s) en `SfStreamPeer` → tráfico cada 3 s mantiene el socket vivo Y el Wi-Fi despierto durante
>   la selección de peleador. Aplica a BT/LAN (a BT no le estorba).
> - **✅ (complementario) WifiLock** (`WIFI_MODE_FULL_HIGH_PERF`) en `SfLanClient` (toma `Context`) —
>   evita el power-save del Wi-Fi. Sin permiso nuevo. El WifiLock SOLO no bastó (el idle-kill del
>   socket ocurre igual); el heartbeat frecuente es lo que lo cierra.
> - **🐛 Mensaje equivocado corregido:** salía "SIN CONEXIÓN BLUETOOTH" en WiFi. `BtRetryOverlay`
>   ahora recibe `titleRes` → "SIN CONEXIÓN WI-FI" (`sf_lan_error_title`) cuando `lanMode`.
> - **🔎** `sendRaw` loguea (tag `SF-NET`) si una escritura falla.
> - **⏭️ SIGUIENTE (pedido del dueño):** autodescubrimiento LAN por UDP broadcast (punto ④ de
>   `_ARCHIVO/PENDIENTES_SF_2026-07-16.md`) — tras confirmar que ya no muere.

> **🆕 2026-07-25b (Opus 4.8) — LAN "muere tras elegir peleador" + mensaje equivocado (dueño):**
> - **✅ BT ahora arranca SINCRONIZADO** entre gamas distintas (la barrera "ambos listos" funcionó).
> - **🐛 Mensaje equivocado:** al fallar el WiFi salía "SIN CONEXIÓN BLUETOOTH". `BtRetryOverlay`
>   tenía el TÍTULO fijo a `sf_bt_error_title`; ahora recibe `titleRes` y usa `sf_lan_error_title`
>   ("SIN CONEXIÓN WI-FI") cuando `lanMode`. (El hint ya cambiaba bien.)
> - **🔴 Hipótesis inicial (power-save):** WifiLock añadido; NO bastó (ver banner de arriba: era el
>   idle-kill del socket, resuelto con el heartbeat frecuente).

> **🆕 2026-07-25 (Opus 4.8) — BARRERA "AMBOS LISTOS" + endurecimiento LAN (⚠️ SIN COMPILAR aquí:
> falta el gradle-wrapper.jar y Gradle 9.5; Rebuild + 2 dispositivos pendientes):**
> - **🟢 Arranque sincronizado (punto 2 del dueño):** nuevo mensaje **`PLAYER_READY`** (relay puro,
>   los 3 transportes). Tras `FIGHT_START` cada teléfono decodifica sus atlas y AVISA cuando terminó;
>   el que ya cargó ESPERA al rival (overlay "ESPERANDO AL RIVAL") para arrancar la ronda juntos —
>   antes el de gama baja empezaba tarde. Fallback por **timeout ~8 s** si el rival/server no lo
>   soporta (cliente viejo / server sin redeploy) → degrada al arranque de siempre. También en las
>   rondas 2/3. Server: `case 'PLAYER_READY'` (⚠️ **requiere redeploy**; sin él, timeout).
> - **🔵 LAN "conecta pero nunca empieza" (punto 1):** el transporte y el wiring eran idénticos a BT
>   (que sí funciona) → fallo de red/entorno, no de lógica. Fixes: **SO_REUSEADDR + bind explícito**
>   (re-hospedar tras cierre sucio ya no tira "Address already in use"), **keepAlive** en los sockets,
>   **watchdog de handshake TAMBIÉN en el HOST** (un enlace TCP asimétrico dejaba al host a medio
>   abrir), **mostrar TODAS las IPv4** del host (Wi-Fi/hotspot/…) para descartar la interfaz
>   equivocada, y **logs `SF-NET`** en cada paso (HELLO/WELCOME/MAP_SELECTED/FIGHT_START/PLAYER_READY)
>   para pinpointear el atasco en el próximo test de 2 equipos. La barrera de arriba también aplica.
> - **🟡 Punto 3 (Render→matchmaking + host real vía Cloudflare Tunnel): SOLO DOCUMENTADO** en
>   `DISENO_MATCHMAKING_P2P.md` (decisión del dueño). Recomendación Play-safe: **Cloudflare Workers +
>   Durable Objects** (edge, sin cold start, sin foreground service, sin cambios de ficha). El túnel
>   en el teléfono se descartó por riesgo de Play (FGS + binario nativo).
> - Fuera de red (mismo día): **la barra de PODER (súper) PERSISTE entre rondas** y el **GRADO de
>   victoria** (PERFECT/COMBO/SUPER/TIME) se pinta bajo la barra del ganador en la ronda siguiente
>   (detalle en `DISENO_ARCADE_SF_POW.md`).


> **✅ AUDIT 2026-07-18m — lo NUEVO funciona igual en los 3 transportes (Render/BT/LAN):**
> Verificado por lectura de código que las features de 2026-07-18 son AGNÓSTICAS al transporte
> (el VM habla solo con `SfNetTransport`; BT/LAN pasan por `SfStreamPeer`, online por WebSocket,
> mismos `SfNetMsg`).
> - **Mapas nuevos + framing panorámico + parallax de salto:** el mapa lo elige el HOST y viaja
>   por `selectMap → MAP_SELECTED` (idéntico en Render y en `SfStreamPeer.selectMap`, que además
>   corre el countdown local). `effectiveBgFile` usa `onlineMapFile`; `framingForBg()` y el
>   `jumpFrac` son 100% render del cliente (mismos assets en el APK) → se ven igual en MP.
> - **Música por progresión:** `musicFileForState` en MP cae a la rama de batalla con
>   `cpuDifficulty` (online = NORMAL por default de `startOnlineBattle`) → siempre suena una
>   pista de batalla estable; lobby en `SELECTING`. Sin recargas raras a mitad de combate
>   (las claves del `remember` no cambian en MP).
> - **Desbloqueo por dificultad:** SOLO arcade (`arcadeActive`); MP no lo toca. Sin impacto.
> - **Flechas/recuadro P1-P2:** la selección online es a CIEGAS por protocolo (el rival solo se
>   conoce en `CHARACTERS_SELECTED`, tras elegir ambos). Por eso el overlay de `SELECTING` NO
>   pasa `allyId/showPickArrow` → sin flechas en MP (correcto, no hay regresión). Las flechas
>   aplican a los modos LOCALES (Práctica, IA vs IA). **Pendiente futuro** (si se quiere el
>   indicador de rival en MP): relay del pick en vivo (cambio de server) o un banner de matchup
>   en `WAITING_MAP/COUNTDOWN` (ahí `oppOnlineChar` ya se conoce).
> - **Autojuego/Showcase dev-only:** viven en el menú OFFLINE; MP entra por otro botón. Sin impacto.
>
> Transportes sanos (sin cambios en esta pasada): `SfMatchClient` (WS/Render), `SfBtClient`
> (RFCOMM), `SfLanClient` (TCP) — los dos últimos comparten framing en `SfStreamPeer`.


> **✅ DEPLOY DEL SERVIDOR SF — HECHO (2026-07-18):** `MultiplayerSF/` YA está vivo en Render en
> `https://politecnicoopenworld-2.onrender.com` (Docker, plan Free, root dir `MultiplayerSF`).
> `/status` → `{"status":"ok","rooms":0}`. Coincide con `SF_SERVER_URL` de gradle (debug+release).
> **Los 3 servidores de Render** (`Multiplayer` = open world, `MultiplayerInteriores`, `MultiplayerSF`)
> comparten un `auth.js` IDÉNTICO y las MISMAS env vars. Config del SF:
> - `FIREBASE_SERVICE_ACCOUNT` = el JSON del service account como string (MISMO valor que los otros 2).
>   Log de arranque confirma: `[auth] firebase-admin inicializado. AUTH_REQUIRED=false`.
> - ⚠️ **`AUTH_REQUIRED` NO se define (modo suave) a propósito** aunque los otros servers lo tengan
>   en `true`: el cliente SF (`SfMatchClient`) NO envía token de Firebase → con `AUTH_REQUIRED=true`
>   `verifyClient` rechazaría TODAS las conexiones y se caería el online de pelea. Con service account
>   presente igual inicializa firebase-admin (podría verificar tokens si el cliente los enviara algún
>   día) pero sin rechazar a nadie. **No poner AUTH_REQUIRED=true hasta que el cliente SF mande token.**
> - `PORT` lo inyecta Render (server usa `process.env.PORT`; en el log salió 10000). Health check: `/status`.
>
> **✅ ESTADO ACTUAL (2026-07-16) — esto MANDA sobre los banners de abajo:** TODO lo de las
> sesiones 1–3c (server, cliente WS, sala pública, lobby con aprobación, Bluetooth con
> handshake/reintentos/encendido de BT, gate Ryu/Ken) está **implementado, COMPILADO y
> probado por el dueño** (BT mejorado el 2026-07-16). Los banners de abajo son REGISTRO, no
> tareas. **Pendientes REALES:** ~~(1) deploy de `MultiplayerSF/` en Render~~ **✅ HECHO 2026-07-18
> (ver banner arriba)**; falta la prueba online en 2 dispositivos con el servicio ya vivo. La
> `SF_SERVER_URL` YA está en gradle (`wss://politecnicoopenworld-2.onrender.com`, debug+release); (2) del §4
> quedan reconexión a sala, espectadores y anti-cheat (conscientes, no bloquean) — interpolación,
> sincronía del timer, fireball-vs-fireball y roll-up del HUD ✅ SESIÓN 4; (3) **🆕 reportados
> por el dueño el 2026-07-16 (diseño de fix en `PENDIENTES_SF_2026-07-16.md`):** STUN-LOCK
> online/BT (machacar un botón mata sin defensa), sincronización imperfecta de la REVANCHA, y
> SERVIDOR LOCAL con autodescubrimiento (sala visible sola + código, sin teclear IP).

> **🆕 SESIÓN 2 (mismo día) — mejoras hechas y PENDIENTES para QWEN/CODEX:**
>
> **HECHO:**
> - **Renombre user-facing:** el modo se llama **"TITULACIÓN POR COMBATE"** (`menu_street_fighter`
>   ES+EN). Los ids internos (`street_fighter`, `features/streetfighter/`, `SfFighterId`…)
>   NO cambian — solo strings visibles. Si tocas más pantallas, usa el nombre nuevo.
> - **SALA PÚBLICA (quick match):** server: `publicQueue` + mensajes `QUICK_MATCH` (empareja
>   al llegar el 2º; el que ESPERABA es p1/anfitrión), `CANCEL_QUEUE`, `QUEUED{position}`;
>   se limpia la cola en `close`. Cliente: botón "SALA PÚBLICA" en `OnlineMenuOverlay` →
>   `startOnlineQuick()`; `WAITING_OPPONENT` con `roomCode == null` = "LISTA DE ESPERA".
> - **Resumen de partidas:** `LIST_ROOMS` → `ROOMS_LIST{rooms:[{code,players,phase}],queue}`;
>   el VM lo condensaba en `state.activeRoomsInfo` ("Salas activas: N · En espera: M").
>   *(SESIÓN 3: reemplazado por `activeRooms`/`queueCount` — el texto lo arma la VIEW con
>   string resource y las salas se pintan como tarjetas, ver banner de abajo.)*
> - **Botones estilo POW:** composable `PowButton` en la Screen (CutCornerShape topStart/
>   bottomEnd + vino #6B1C3A + texto bold espaciado, como `MenuButton` del menú principal);
>   ya usado en menú online, unirse, fin de pelea, pausa y botón 🌐.
> - **Rival elegible OFFLINE:** el flujo pre-pelea offline ahora es de 3 pasos: TU peleador →
>   el RIVAL (2º `CharacterSelectOverlay` con subtítulo `sf_choose_rival`) → mapa.
>   `selectCharacter(id, rivalId?)` en el VM (rivalId null = Ken/Ryu de antes; online lo ignora).
>
> **✅ SESIÓN 3 (2026-07-15) — los 6 PENDIENTES de arriba quedaron RESUELTOS** (sin compilar;
> Rebuild pendiente):
> 1. ✅ `cancelOnline()` manda `CANCEL_QUEUE` + `LEAVE_ROOM` ANTES de cerrar el WS (el server
>    ignora el que no aplique) y cancela `roomsRefreshJob`.
> 2. ✅ Lista de salas RICA: la LISTA DE ESPERA pública es ahora `PublicQueueOverlay` (Screen):
>    resumen (`sf_mp_rooms_summary`, arma el texto la VIEW — se retiró el string hardcodeado
>    `activeRoomsInfo` del VM) + `rooms[]` como tarjetas (`RoomCard`); las salas en 'waiting'
>    con hueco son TOCABLES → `joinRoomFromQueue(code)` en el VM (`CANCEL_QUEUE` + `JOIN_ROOM`).
>    Estado: `activeRooms: List<SfRoomSummary>` + `queueCount: Int?` (null = sin datos).
>    NOTA: se puso en la lista de espera y NO en `OnlineMenuOverlay` como decía el pendiente:
>    ese menú se muestra con el WS AÚN SIN CONECTAR (status OFF) — no hay de dónde pedir
>    `LIST_ROOMS` sin pagar el warmup (~1 min) solo por abrir el menú.
>    Server endurecido: `JOIN_ROOM`/`CREATE_ROOM` sacan al ws de `publicQueue` y el matchmaker
>    poda a quien ya está en sala (`!wsToRoom.has(w)`) → sin doble emparejamiento. `node --check` OK.
> 3. ✅ `startRoomsRefresh()` (VM): en lista de espera re-pide `LIST_ROOMS` cada
>    `ROOMS_REFRESH_MS=5000`; se auto-detiene al emparejarte/unirte/cancelar.
> 4. ✅ `PowButton` movido a `map_exterior/ui/components/PowButton.kt` (público, junto a
>    ActionButton/JoystickController); la Screen lo importa (se borró el privado — gana nada:
>    no quedó gemelo).
> 5. ✅ Sweep visual: `OutlinedTextField` con colores vino/dorado (`OutlinedTextFieldDefaults.colors`),
>    los `TextButton` de Cancelar/volver en dorado `#D4AF37` y el `AlertDialog` de salida con
>    fondo oscuro `#1A1016` + botones al tema.
> 6. ✅ Strings nuevos ES+EN (paridad): `sf_mp_rooms_summary`, `sf_mp_room_waiting`, `sf_mp_room_busy`.
>
> **🆕 SESIÓN 3b (2026-07-15, mismo día) — LOBBY CON APROBACIÓN + BLUETOOTH:**
> - **Lobby estilo AoE2 (online):** tocar una tarjeta de sala ya NO une directo: manda
>   `REQUEST_JOIN{code}` → el server avisa al host (`JOIN_REQUESTED`) → el host ve un diálogo
>   ACEPTAR/RECHAZAR (`RESPOND_JOIN{accept}`) → aceptar = `ROOM_JOINED`+`OPPONENT_JOINED`
>   (flujo existente); rechazar/sala llena = **`JOIN_REJECTED{message}`** (soft, NO `ERROR`: el
>   invitado re-entra a la cola con `QUICK_MATCH` y ve el aviso `queueNotice`). El server
>   guarda `room.pendingJoin` (UNA solicitud a la vez); si el solicitante se desconecta →
>   `JOIN_REQUEST_CANCELLED` al host; si el host se va → se rechaza al pendiente. UNIRSE por
>   CÓDIGO sigue siendo directo (el código ES la invitación). Estado: `joinRequestPending`
>   (host) / `awaitingJoinOk` + `queueNotice` (invitado); intents `requestJoinRoom`/`respondJoin`.
> - **BLUETOOTH local (sin internet):** interfaz común **`SfNetTransport`** (Listener + mismos
>   mensajes `SfNetMsg`); `SfMatchClient` la implementa y el VM solo habla con `transport`.
>   **`SfBtClient`** = RFCOMM con **UUID fijo** (`SF_BT_UUID`), JSON por líneas; el **HOST hace
>   de server** (accept + genera localmente lo que en online manda el relay: OPPONENT_JOINED,
>   CHARACTERS_SELECTED, MAP_SELECTED+countdown, FIGHT_START, REMATCH_ACCEPTED bilateral);
>   `PLAYER_STATE` entrante → `OPPONENT_STATE` en el receptor (misma autoridad del receptor).
>   Caída del socket → `OPPONENT_DISCONNECTED` (abandono); el host vuelve a aceptar.
>   UI: sección "BLUETOOTH (sin internet)" en el menú 🌐 (ANFITRIÓN = visible+accept /
>   BUSCAR RIVAL = emparejados+discovery con selector). `roomCode="BT"` + `state.btMode`.
> - **Permisos (Manifest, ya aprobado en Play):** `BLUETOOTH`/`BLUETOOTH_ADMIN` con
>   `maxSdkVersion=30`; `BLUETOOTH_CONNECT`/`BLUETOOTH_ADVERTISE`/`BLUETOOTH_SCAN`, este último
>   con **`usesPermissionFlags="neverForLocation"` (CRÍTICO:** sin él el escaneo cuenta como
>   ubicación; con él, el `ACCESS_FINE_LOCATION` existente se justifica SOLO por los mapas).
>   Runtime SOLO al tocar la sección BT (Android 12+: CONNECT+ADVERTISE al hospedar,
>   SCAN+CONNECT al buscar); en ≤11 no se pide nada (el discovery usa la ubicación que la app
>   ya tiene por el mapa). **PROHIBIDO foreground service** (targetSdk 34 obligaría declaración
>   con video en Play Console): la conexión vive con la Activity y `close()` al salir.
>   Play Console: Data Safety NO cambia (tráfico dispositivo-a-dispositivo efímero ≠
>   recopilación); no hay formulario de declaración para permisos BT; solo mencionar la
>   función en las notas de versión.
>
> **🆕 SESIÓN 3c (2026-07-15, mismo día) — BT PERSISTENTE + MODO PÚBLICO:**
> - **Robustez/persistencia BT (`SfBtClient`):** HEARTBEAT cada 10 s (Timer; el receptor lo
>   ignora, NO sube al VM) + si una ESCRITURA falla se cierra el socket → el readLoop destraba
>   YA y notifica el abandono (sin esto una caída silenciosa colgaba la pelea hasta que el
>   stack BT reportara); `onPeerConnected` resetea TODA la sala local (incl. `char1`);
>   visibilidad del host 120→300 s; timer cancelado en pérdida de peer y en `close()`.
> - **Re-entrada limpia:** VM `OPPONENT_JOINED` con `battleEnded`/pelea corrida → reset a
>   SELECTING como REMATCH_ACCEPTED (aplica a BT —el host re-acepta tras un abandono— y al
>   online cuando un nuevo rival entra a la sala tras una pelea).
> - **GATE INVERTIDO:** el modo es PÚBLICO (botón del menú siempre visible); el Modo
>   Desarrollador ahora solo desbloquea a **RYU/KEN** en el selector
>   (`selectableFighters`/`classicFightersUnlocked` en el VM; rival default offline sin dev =
>   Prankedy/Rey Grupero). Ver 07 §STREET FIGHTER.

> **🆕 SESIÓN 4 (2026-07-16b) — 4 opcionales del §4 + BUGFIX del server (sin compilar;
> Rebuild pendiente):**
> - **🐛 BUGFIX server (CRÍTICO para online):** el relay de PLAYER_STATE hacía
>   `{ type: 'OPPONENT_STATE', ...msg }` — el spread iba DESPUÉS y `msg.type` ('PLAYER_STATE')
>   SOBRESCRIBÍA el type: el cliente recibía "PLAYER_STATE" (sin case en `handleNetMessage`)
>   y el rival se veía CONGELADO en online. Fix: `{ ...msg, type: 'OPPONENT_STATE' }`. BT/LAN
>   nunca lo sufrieron (`SfStreamPeer` usa `msg.copy(type=…)`) — por eso las pruebas locales
>   pasaban. **Regla: en relays con spread, el type nuevo va SIEMPRE al FINAL.** Sale en el
>   1er deploy (aún no hay ninguno).
> - **Interpolación del rival:** `applyRemoteSnapshot(sim, now, dt)` ALISA x/y con lerp
>   exponencial (`NET_LERP_RATE=14`); >`NET_SNAP_DIST=80` px snapea (reset de ronda/teleport).
>   Pose/frame/dir/HP directos. Campos `lastSeenSnapshot`/`remoteSnapshotAtMs` (identidad →
>   edad del snapshot; se resetean en `resetInternals` Y `resetRound`).
> - **Proyectiles remotos extrapolados:** `appendRemoteFireballs(sim, now)` avanza los ACTIVE
>   a su velocidad nominal por la edad del snapshot (tope `NET_FB_MAX_AGE_S=0.25 s`).
> - **Sincronía del timer:** PLAYER_STATE ganó campo opcional `timer` (SfNetMsg +
>   `sendPlayerState(..., timer: Int?, ...)` en la interfaz, SfMatchClient y SfStreamPeer —
>   cubre WS/BT/LAN; el server lo relaya por el spread sin cambios). SOLO lo manda el HOST;
>   el invitado lo ADOPTA si el drift es ≥ `TIMER_RESYNC_DIFF=2`, **gateado por
>   `roundGraceUntilMs`** (un timer viejo en vuelo no pisa el 99 de la ronda recién reseteada).
> - **Fireball-vs-fireball:** `collideFireballPairs` — dos ACTIVE de dueños opuestos que
>   traslapan REVIENTAN (COLLIDED ×0.33 + sonido). Offline: tras `updateFireballs`. Online:
>   tras `appendRemoteFireballs` y ANTES de `sendNetState`; revienta MI copia y el rival hace
>   lo simétrico (~66 ms, parpadeo aceptado).
> - **Roll-up del HUD:** `displayHp0/1` (Float) en el estado; el VM drena `dispHp0/1` a
>   `HP_DRAIN_PER_SEC=200` hacia el HP real (SUBIR es instantáneo → el reset de ronda/revancha
>   rellena solo, sin tocar resets). `drawHud` pinta las barras con `displayHp*`.

> Servidor base generado por QWEN (interrumpido), **auditado y corregido**; cliente Kotlin
> implementado desde cero por la IA principal. NADA compilado aún (Rebuild pendiente).

## 1. Archivos

| Archivo | Qué hace |
|---|---|
| `MultiplayerSF/server.js` (~330 líneas) | Relay puro 1v1: salas de 4 letras, selección, countdown, estado, daño, fin, revancha. **Fixes sobre lo de QWEN:** PLAYER_DAMAGE ahora va SIEMPRE al rival del emisor (antes comparaba un `targetId` inexistente), JOIN rechaza salas sin anfitrión, revancha requiere a LOS DOS (`rematch1/2` + `REMATCH_REQUESTED`), cualquier mensaje en sala refresca `lastActivityMs` (una pelea >5 min ya no expira) |
| `MultiplayerSF/{package.json, Dockerfile, docker-compose.yml, auth.js}` | Patrón de MultiplayerInteriores; `auth.js` modo suave (`AUTH_REQUIRED`), `GET /status` para warmup |
| `app/build.gradle.kts` | `BuildConfig.SF_SERVER_URL` (debug+release) → **ajustar al nombre real del servicio tras el 1er deploy** |
| `features/streetfighter/data/SfMatchClient.kt` (NUEVO) | WebSocket OkHttp (ping 20 s + HEARTBEAT 45 s), `SfNetMsg` laxo (Gson), helpers de envío y `warmupBlocking()` (GET /status con reintentos ≤90 s para despertar el free tier). 🆕 SESIÓN 3b: implementa `SfNetTransport` + `requestJoin`/`respondJoin` |
| 🆕 `features/streetfighter/data/SfNetTransport.kt` (SESIÓN 3b) | Interfaz común del transporte (Listener + senders); el VM solo habla con ella |
| 🆕 `features/streetfighter/data/SfBtClient.kt` (SESIÓN 3b) | Multijugador LOCAL por Bluetooth RFCOMM (UUID fijo, JSON por líneas); el HOST genera localmente los mensajes del relay; `startScan/stopScan` (emparejados + discovery). Sin foreground service |
| 🆕 `app/src/main/AndroidManifest.xml` (SESIÓN 3b) | Permisos BT: legacy con `maxSdkVersion=30` + CONNECT/ADVERTISE/SCAN (`neverForLocation`) |
| `viewmodel/StreetFighterState.kt` | `SfOnlineStatus` (OFF/CONNECTING/WAITING_OPPONENT/SELECTING/WAITING_MAP/COUNTDOWN/FIGHTING/OPPONENT_LEFT) + campos `roomCode/isHost/onlineCountdown/onlineError/onlineMapFile/opponentWantsRematch` + 🆕 `activeRooms: List<SfRoomSummary>` y `queueCount: Int?` (resumen LIST_ROOMS; null = sin datos) |
| `viewmodel/StreetFighterViewModel.kt` | Integración online: ver §3 |
| `ui/StreetFighterScreen.kt` | Botón 🌐 en el selector, `OnlineMenuOverlay` (crear/unir con código), `OnlineInfoOverlay` (conectando/esperando), countdown gigante, mapa del anfitrión por red, menú de fin online (revancha bilateral + salir de sala) |
| `strings.xml` ES+EN | 15 strings `sf_mp_*` (paridad) |

## 2. Protocolo (C→S / S→C)

| Mensaje | Dir | Payload |
|---|---|---|
| CREATE_ROOM / JOIN_ROOM | C→S | `code?` / `code` |
| ROOM_CREATED / ROOM_JOINED | S→C | `code, playerIndex` (1=anfitrión) |
| OPPONENT_JOINED / OPPONENT_LEFT / OPPONENT_DISCONNECTED | S→C | — / — / `winner` |
| SELECT_CHARACTER → CHARACTERS_SELECTED | C→S→C | `character` → `char1, char2` (nombres de `SfFighterId`) |
| SELECT_MAP → MAP_SELECTED | C→S→C | `map` (archivo del fondo) → `map, countdownMs` |
| FIGHT_START | S→C | tras 3 s de countdown del server |
| PLAYER_STATE → OPPONENT_STATE | C→S→C | `x,y,state,frame,dir,hp,timer?,fireballs[],meter` cada ~66 ms (🆕 `timer` solo lo manda el HOST — sincronía del reloj, SESIÓN 4) |
| 🆕 PLAYER_READY (2026-07-25) | C→S→C(rival) | — : "ya cargué mis atlas". Barrera "ambos listos": el rival lo espera para arrancar la ronda sincronizada (fallback timeout ~8 s). ⚠️ Requiere redeploy |
| PLAYER_DAMAGE | C→S→C(rival) | `damage, strength, atkType` |
| MATCH_ENDED | C→S→C | `winner` ("p1"/"p2") |
| REQUEST_REMATCH → REMATCH_REQUESTED / REMATCH_ACCEPTED | C→S→C | acepta cuando lo piden AMBOS |
| HEARTBEAT / ERROR / SESSION_INIT | varios | keep-alive de sala / `message` / `sessionId` |
| 🆕 ROUND_ENDED (2026-07-16) | C→S→AMBOS | `winner` ("p1"/"p2") + 🆕 `outcome` (2026-07-25: PERFECT/COMBO/SUPER/TIME/NORMAL para la etiqueta bajo la barra del ganador): fin de RONDA intermedia (mejor de 3); NO toca la fase de la sala — MATCH_ENDED queda solo para el combate decidido. ⚠️ Requiere redeploy |
| 🆕 REQUEST_JOIN → JOIN_REQUESTED | C→S→host | `code`: solicitud de unión (lobby con aprobación) |
| 🆕 RESPOND_JOIN | host→S | `accept`: true = mete al pendiente (ROOM_JOINED/OPPONENT_JOINED) |
| 🆕 JOIN_REJECTED / JOIN_REQUEST_CANCELLED | S→C / S→host | `message` (soft-reject; el invitado re-encola) / el solicitante se fue |

> **Bluetooth (SfBtClient) y 🆕 Servidor LAN (SfLanClient, 2026-07-16):** mismos mensajes SIN
> server — la base común `SfStreamPeer` (host agrega SELECT_CHARACTER/REQUEST_REMATCH del
> invitado y emite CHARACTERS_SELECTED/MAP_SELECTED/FIGHT_START/REMATCH_ACCEPTED/ROUND_ENDED;
> PLAYER_STATE entrante → OPPONENT_STATE; handshake HELLO→WELCOME; heartbeat). LAN = TCP en
> puerto fijo 47645, unirse por IP del host (se muestra en su pantalla); cero permisos nuevos.

## 3. Decisiones (qué es autoritativo y por qué)

- **Relay puro, autoridad del RECEPTOR sobre su propio HP**: cada cliente simula SOLO a su
  peleador (índice 0). El rival (índice 1) NO se simula: se pinta con su último
  `OPPONENT_STATE`. Si MI golpe/proyectil conecta (contra su snapshot), mando
  `PLAYER_DAMAGE` + efectos optimistas; ÉL decide bloqueo (con su estado real), aplica el
  daño y su HP viaja de regreso en su estado. Cero lógica de pelea en el server (free tier).
- **Proyectiles**: los míos se simulan/colisionan localmente; los suyos son render-only.
- **Anfitrión = p1**, pelea a la IZQUIERDA y elige el mapa. Fin de pelea: quien muere manda
  `MATCH_ENDED` (con adelanto local si el snapshot del rival trae hp≤0, guard `onlineEndSent`);
  timeout: ambos lo calculan, el guard evita doble envío y `endFromNet` reconcilia.
- **Threading**: mensajes llegan en el hilo de OkHttp → se relanzan a Main (serializados con
  el tick); `remoteSnapshot` es `@Volatile` y el daño entrante va en `ConcurrentLinkedQueue`.
- **Free tier**: `warmupBlocking` (≤90 s de GET /status) antes de conectar, ping WS 20 s,
  HEARTBEAT 45 s para que la sala no expire en las antesalas.

## 4. NO implementado (consciente)

Reconexión a la sala tras caída (se pierde la pelea), espectadores y validación anti-cheat
(relay confía en los clientes — aceptable entre amigos). *(La interpolación del rival, la
sincronía del timer, el fireball-vs-fireball y el roll-up del HUD se implementaron en la
SESIÓN 4 — ver banner. El matchmaking existe desde la SESIÓN 2: sala pública QUICK_MATCH.)*

## 5. Cómo probar / desplegar (GRATIS)

1. **Local:** `cd MultiplayerSF && node --check server.js && docker compose up -d` (puerto
   8082→8080 o el que defina el compose). 2 emuladores con `SF_SERVER_URL = ws://10.0.2.2:8082`.
2. **Render (free) — ✅ YA DESPLEGADO (2026-07-18):** New → Web Service → repo, root
   `MultiplayerSF/`, runtime Docker, plan **Free**, nombre `politecnicoopenworld-2` (para que
   coincida con `SF_SERVER_URL` de gradle). Env var: `FIREBASE_SERVICE_ACCOUNT` = MISMO JSON que
   los otros 2 servers; **NO** poner `AUTH_REQUIRED` (el cliente SF no manda token → modo suave
   obligatorio, ver banner arriba). `PORT` lo inyecta Render. URL viva:
   `wss://politecnicoopenworld-2.onrender.com` (ya cableada en `app/build.gradle.kts` debug+release).
3. **2 dispositivos:** A: modo pelea → 🌐 MULTIJUGADOR → CREAR SALA (espera ~1 min si el
   server dormía) → comparte el código. B: UNIRSE + código. Ambos eligen peleador; A elige
   mapa; 3-2-1; pelear. Verificar: golpes/bloqueo/proyectiles en ambos lados, KO → ganador
   correcto en ambos, revancha (la piden los 2), salir a media pelea → el otro gana por
   abandono, y bloquear el celular (pausa local; el rival te ve congelado).

## 6. Docs actualizados

07 (bullet multijugador), 08 (sección MultiplayerSF), 01 (tabla build/URLs implícita en 08),
README público EN+ES (Recent Changes). Este audit = registro de la sesión.

## 7. Riesgos / vigilar en el Rebuild

- `SfOnlineStatus` se importa en la Screen (paquete viewmodel) — verificar import.
- `OutlinedTextField` (M3) añadido a imports de la Screen.
- El overlay de selección ahora tiene un `when` por `onlineStatus`: probar que el flujo
  OFFLINE quedó idéntico (2 pasos, fondo elegible) — era el riesgo #1 del cambio.
- Pausa local en online NO pausa al rival (su juego sigue); decisión aceptada.
- Si Render tarda >90 s en despertar, `startOnline` muestra error y se reintenta a mano.
