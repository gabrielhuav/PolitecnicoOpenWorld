# DISEÑO · Matchmaking + host real (evolución del multijugador online SF)

> **Estado: DOCUMENTO DE DISEÑO (2026-07-25). NO implementado.** Decisión del dueño: en esta
> sesión el punto 3 (Render → solo matchmaking + host real, "una tecnología como Cloudflare
> Tunnel") **SOLO se documenta**. Este archivo compara las opciones, deja el análisis de política
> de Play Store y recomienda el camino Play-safe. Lo demás de la sesión (barrera "ambos listos",
> endurecimiento de LAN, súper que persiste entre rondas, grado de victoria) SÍ se implementó.

## 1. El problema

El multijugador **online** de "HUELUM VS. GOYA" usa `MultiplayerSF/` (Node + `ws`) desplegado en
**Render, plan Free**. Arquitectura actual = **relay puro** (el server NO simula la pelea; solo
reenvía `SfNetMsg` entre los 2 clientes; autoridad del receptor sobre su HP — ver
`AUDIT_SF_MULTIPLAYER.md §3`).

Síntoma del dueño: *"el modo online va fatal por las limitaciones de la capa gratuita de Render"*.
Causas reales del free tier de Render:

- **Cold start ~1 min**: el servicio se duerme sin tráfico; la 1ª conexión paga el `warmupBlocking`.
- **Instancia única, región fija (Oregon por defecto)**: 2 jugadores en México pagan ida y vuelta
  a EE. UU. por CADA `PLAYER_STATE` (~10-15 Hz) → latencia alta y variable.
- **CPU/red compartida y con límites**: picos de jitter en el peor momento (durante la pelea).

La idea del dueño: que **Render (o quien sea) solo empareje** y que **el host del juego lo haga un
jugador real** (P2P), exponiendo su servicio local a Internet con algo **como Cloudflare Tunnel**.

## 2. Las tres opciones

### A) Host en el teléfono + Cloudflare Tunnel (lo que pidió el dueño, textual)

Un jugador corre el "server" en su teléfono (ya existe: `SfStreamPeer`/`SfLanClient` hacen de host
en LAN) y lo expone a Internet con `cloudflared` (túnel). El matchmaker solo intercambia la URL del
túnel entre los 2 jugadores.

- ✅ Cero relay de gameplay en la nube (el free tier deja de ser el cuello de botella).
- ❌ **Requiere ejecutar un binario nativo (`cloudflared`, Go) EN Android** → empaquetarlo (ABI
  arm64/x86), extraerlo y `exec`-utarlo. Cae bajo escrutinio de Play *Device and Network Abuse*
  (apps que descargan/ejecutan código o abren túneles).
- ❌ Para que el túnel + server sigan vivos con la pantalla apagada durante la pelea hace falta un
  **foreground service persistente** → con `targetSdk 34`, **declaración de FGS en Play Console**
  (tipo `connectedDevice`/`dataSync`) que Google revisa. **Esto choca con la regla del proyecto
  "PROHIBIDO foreground service"** (`AUDIT_SF_MULTIPLAYER.md §SESIÓN 3b`) que se respetó a propósito
  en BT/LAN para no tocar la ficha.
- ❌ **TryCloudflare** (túnel efímero sin cuenta) tiene límites y no es para producción; un túnel
  con cuenta requiere credenciales embebidas (riesgo) o login del usuario (fricción).
- ❌ Exponer una URL pública que reenvía gameplay ≠ el tráfico efímero dispositivo-a-dispositivo de
  BT/LAN (donde Data Safety no cambia): perfil de riesgo distinto.
- ⚖️ **Veredicto: sí añade riesgo real de Play y es frágil en Android. NO recomendado.**

### B) Matchmaker + relay ligero en Cloudflare Workers + Durable Objects (RECOMENDADO)

Reemplazar/duplicar `MultiplayerSF/` por un **Worker de Cloudflare con Durable Objects** (una DO por
sala; el WebSocket vive en el edge más cercano a los jugadores). El **teléfono no expone nada**.

- ✅ **Sin cold start** (Workers arrancan en ms) → se acaba el warmup de ~1 min.
- ✅ **Edge global**: la sala corre cerca de los jugadores (baja latencia real vs. Oregon).
- ✅ **Capa gratuita generosa** (Workers Free + Durable Objects) para el tráfico de relay 1v1.
- ✅ **Play-safe**: nada de binarios nativos, nada de foreground service, **cero cambios en la ficha
  de Play** ni en Data Safety (mismo modelo relay que hoy; el cliente ya habla WebSocket con
  `SfMatchClient`).
- ✅ Reusa el protocolo `SfNetMsg` TAL CUAL: es el MISMO relay puro, solo cambia el `SF_SERVER_URL`
  (de `wss://…onrender.com` a `wss://…workers.dev`). El motor y la autoridad del receptor no cambian.
- ⚠️ Reescribir `server.js` como Worker (el estado de sala vive en la DO, no en un `Map` global):
  `getWebSocketPair`, `state.acceptWebSocket`, hibernación de la DO entre mensajes.
- ⚖️ **Veredicto: mejor relación beneficio/riesgo; honra "una tecnología como Cloudflare" SIN los
  problemas de Play. Es la evolución natural del relay actual.**

> Nota: "matchmaker" y "relay" pueden ser la MISMA DO por sala. La cola pública (`QUICK_MATCH`) y el
> lobby con aprobación ya existen en `server.js`; se portan a un Worker "lobby" + una DO por partida.

### C) P2P real por Internet (NAT traversal con STUN/ICE, TURN de respaldo)

Conexión directa cliente-cliente con hole punching (STUN) y un TURN de respaldo cuando el NAT es
simétrico.

- ✅ Latencia mínima cuando el hole punching funciona.
- ❌ Complejidad alta (ICE/STUN/TURN, WebRTC DataChannel o UDP propio) y **~20-30 % de redes
  necesitan TURN** (relay que hay que pagar/host-ear → vuelve el problema del server).
- ❌ Mucho código nuevo; difícil de depurar sin 2 dispositivos en redes reales.
- ⚖️ **Veredicto: sobre-ingeniería para 1v1 casual; dejar como futurible si B no basta.**

## 3. Recomendación

1. **Corto plazo (ya HECHO en esta sesión):** la **barrera "ambos listos"** (`PLAYER_READY`) quita el
   arranque desincronizado, y el HUD roll-up/interpolación existentes suavizan el jitter. Esto mejora
   la sensación online sin tocar infra. Ver `AUDIT_SF_MULTIPLAYER.md`.
2. **Medio plazo (opción B):** portar `MultiplayerSF/server.js` a **Cloudflare Workers + Durable
   Objects**, apuntar `SF_SERVER_URL` al Worker, y **retirar Render** para SF. Riesgo de Play = 0.
   El resto del cliente no cambia (mismo `SfMatchClient`, mismo `SfNetMsg`).
3. **Descartado:** host-en-el-teléfono con Cloudflare Tunnel (opción A) por el riesgo de política de
   Play (FGS + binario nativo) y la fragilidad en Android. Si algún día se quiere P2P real, evaluar C.

## 4. Bosquejo de portado a Workers + DO (para la sesión que lo implemente)

- **Worker `lobby`** (stateless salvo enrutado): `/status` (health), `CREATE_ROOM`/`JOIN_ROOM`/
  `QUICK_MATCH`/`LIST_ROOMS`/lobby-con-aprobación → asigna/consulta una **Durable Object `Room`** por
  código de 4 letras (`idFromName(code)`), y hace el `fetch` con `Upgrade: websocket` hacia la DO.
- **Durable Object `Room`**: mantiene `p1`/`p2` (WebSockets aceptados con `state.acceptWebSocket`),
  `char1/2`, `map`, `phase`, `rematch1/2`, `pendingJoin`. Reenvía EXACTAMENTE los mismos `SfNetMsg`
  que `server.js` hoy (incluye `PLAYER_READY` y `ROUND_ENDED{outcome}` añadidos en esta sesión).
  Usa **WebSocket Hibernation** para no consumir CPU entre mensajes (clave para el free tier).
- **Auth**: igual que hoy en modo suave (`AUTH_REQUIRED` off) porque el cliente SF no manda token;
  si se activa, verificar el ID token de Firebase en el `fetch` del Worker.
- **Cliente**: solo cambia `BuildConfig.SF_SERVER_URL` (debug+release) a `wss://<worker>.workers.dev`.
  `SfMatchClient.warmupBlocking` puede quedarse (será casi instantáneo) o retirarse.

## 5. Docs relacionados

- `AUDIT_SF_MULTIPLAYER.md` — protocolo `SfNetMsg`, server actual, BT/LAN, barrera "ambos listos".
- `MUNDO/08_SERVERS.md` — los 3 servidores Node en Render (patrón que este diseño evolucionaría).
