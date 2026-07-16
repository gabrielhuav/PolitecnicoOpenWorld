# AUDIT · Multijugador 1v1 del modo pelea "HUELUM VS. GOYA" (2026-07-11)

> **✅ ESTADO ACTUAL (2026-07-16) — esto MANDA sobre los banners de abajo:** TODO lo de las
> sesiones 1–3c (server, cliente WS, sala pública, lobby con aprobación, Bluetooth con
> handshake/reintentos/encendido de BT, gate Ryu/Ken) está **implementado, COMPILADO y
> probado por el dueño** (BT mejorado el 2026-07-16). Los banners de abajo son REGISTRO, no
> tareas. **Pendientes REALES:** (1) deploy de `MultiplayerSF/` en Render + `SF_SERVER_URL`
> real (placeholder hasta el 1er deploy) y prueba online en 2 dispositivos; (2) lo consciente
> del §4 (interpolación del rival, reconexión a sala, espectadores, sincronía del timer) —
> opcional, no bloquea.

> **🆕 SESIÓN 2 (mismo día) — mejoras hechas y PENDIENTES para QWEN/CODEX:**
>
> **HECHO:**
> - **Renombre user-facing:** el modo se llama **"HUELUM VS. GOYA"** (`menu_street_fighter`
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
| PLAYER_STATE → OPPONENT_STATE | C→S→C | `x,y,state,frame,dir,hp,fireballs[]` cada ~66 ms |
| PLAYER_DAMAGE | C→S→C(rival) | `damage, strength, atkType` |
| MATCH_ENDED | C→S→C | `winner` ("p1"/"p2") |
| REQUEST_REMATCH → REMATCH_REQUESTED / REMATCH_ACCEPTED | C→S→C | acepta cuando lo piden AMBOS |
| HEARTBEAT / ERROR / SESSION_INIT | varios | keep-alive de sala / `message` / `sessionId` |
| 🆕 REQUEST_JOIN → JOIN_REQUESTED | C→S→host | `code`: solicitud de unión (lobby con aprobación) |
| 🆕 RESPOND_JOIN | host→S | `accept`: true = mete al pendiente (ROOM_JOINED/OPPONENT_JOINED) |
| 🆕 JOIN_REJECTED / JOIN_REQUEST_CANCELLED | S→C / S→host | `message` (soft-reject; el invitado re-encola) / el solicitante se fue |

> **Bluetooth (SfBtClient):** mismos mensajes SIN server — el host agrega SELECT_CHARACTER/
> REQUEST_REMATCH del invitado y emite CHARACTERS_SELECTED/MAP_SELECTED/FIGHT_START/
> REMATCH_ACCEPTED; PLAYER_STATE entrante se convierte a OPPONENT_STATE en el receptor.

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

Interpolación del rival (llega "crudo" a 15 Hz), reconexión a la sala tras caída (se pierde
la pelea), espectadores, matchmaking (solo código compartido), validación anti-cheat (relay
confía en los clientes — aceptable entre amigos), sincronía del timer (cada quien corre el
suyo; reconciliado por MATCH_ENDED).

## 5. Cómo probar / desplegar (GRATIS)

1. **Local:** `cd MultiplayerSF && node --check server.js && docker compose up -d` (puerto
   8082→8080 o el que defina el compose). 2 emuladores con `SF_SERVER_URL = ws://10.0.2.2:8082`.
2. **Render (free):** New → Web Service → repo, root `MultiplayerSF/`, runtime Docker, plan
   **Free**. Sin env vars obligatorias (`AUTH_REQUIRED` sin definir = modo suave). Copiar la
   URL (p. ej. `pow-sf.onrender.com`) → `SF_SERVER_URL = "wss://pow-sf.onrender.com"` en
   `app/build.gradle.kts` (debug y release) → Rebuild.
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
