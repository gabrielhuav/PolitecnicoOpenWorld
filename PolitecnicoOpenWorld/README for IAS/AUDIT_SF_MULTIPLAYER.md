# AUDIT · Multijugador 1v1 del modo pelea "HUELUM VS. GOYA" (2026-07-11)

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
>   el VM lo condensa en `state.activeRoomsInfo` ("Salas activas: N · En espera: M") y se
>   muestra bajo el subtítulo de la lista de espera.
> - **Botones estilo POW:** composable `PowButton` en la Screen (CutCornerShape topStart/
>   bottomEnd + vino #6B1C3A + texto bold espaciado, como `MenuButton` del menú principal);
>   ya usado en menú online, unirse, fin de pelea, pausa y botón 🌐.
> - **Rival elegible OFFLINE:** el flujo pre-pelea offline ahora es de 3 pasos: TU peleador →
>   el RIVAL (2º `CharacterSelectOverlay` con subtítulo `sf_choose_rival`) → mapa.
>   `selectCharacter(id, rivalId?)` en el VM (rivalId null = Ken/Ryu de antes; online lo ignora).
>
> **PENDIENTE (para QWEN/CODEX, en orden):**
> 1. `CANCEL_QUEUE` del cliente: `cancelOnline()` cierra el WS (el server limpia la cola en
>    close, así que FUNCIONA), pero lo fino es mandar `CANCEL_QUEUE` antes de cerrar.
> 2. Lista de salas RICA: mostrar `rooms[]` como tarjetas tocables (unirse a una sala en fase
>    'waiting' directamente) en `OnlineMenuOverlay` — hoy solo hay contadores.
> 3. Refrescar `LIST_ROOMS` periódicamente en la lista de espera (hoy solo se pide 1 vez).
> 4. `PowButton` podría moverse a `map_exterior/ui/components/` si otros modos lo quieren.
> 5. Sweep visual: quedan `TextButton` (Cancelar/diálogos) y `OutlinedTextField` con estilo
>    M3 default; alinear al tema vino/dorado si se quiere paridad total.
> 6. Strings nuevos ya en ES+EN: `sf_mp_public`, `sf_mp_queue_title`, `sf_mp_queue_sub`,
>    `sf_choose_rival` (revisar paridad si añades más).

> Servidor base generado por QWEN (interrumpido), **auditado y corregido**; cliente Kotlin
> implementado desde cero por la IA principal. NADA compilado aún (Rebuild pendiente).

## 1. Archivos

| Archivo | Qué hace |
|---|---|
| `MultiplayerSF/server.js` (~330 líneas) | Relay puro 1v1: salas de 4 letras, selección, countdown, estado, daño, fin, revancha. **Fixes sobre lo de QWEN:** PLAYER_DAMAGE ahora va SIEMPRE al rival del emisor (antes comparaba un `targetId` inexistente), JOIN rechaza salas sin anfitrión, revancha requiere a LOS DOS (`rematch1/2` + `REMATCH_REQUESTED`), cualquier mensaje en sala refresca `lastActivityMs` (una pelea >5 min ya no expira) |
| `MultiplayerSF/{package.json, Dockerfile, docker-compose.yml, auth.js}` | Patrón de MultiplayerInteriores; `auth.js` modo suave (`AUTH_REQUIRED`), `GET /status` para warmup |
| `app/build.gradle.kts` | `BuildConfig.SF_SERVER_URL` (debug+release) → **ajustar al nombre real del servicio tras el 1er deploy** |
| `features/streetfighter/data/SfMatchClient.kt` (NUEVO) | WebSocket OkHttp (ping 20 s + HEARTBEAT 45 s), `SfNetMsg` laxo (Gson), helpers de envío y `warmupBlocking()` (GET /status con reintentos ≤90 s para despertar el free tier) |
| `viewmodel/StreetFighterState.kt` | `SfOnlineStatus` (OFF/CONNECTING/WAITING_OPPONENT/SELECTING/WAITING_MAP/COUNTDOWN/FIGHTING/OPPONENT_LEFT) + campos `roomCode/isHost/onlineCountdown/onlineError/onlineMapFile/opponentWantsRematch` |
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
