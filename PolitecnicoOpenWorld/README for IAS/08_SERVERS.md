# 08 · Servidores Node.js + Protocolo de red / Node.js servers + Wire protocol

**ES:** Dos servidores **independientes** `Node.js 18 + Express + ws`, ambos dockerizados, ambos
escuchan en contenedor **`:8080`** (`GET /status`, `WS /`). Comparten el mismo `WebSocketManager` en el
cliente. Comentarios en español (mantener). Validar: `node --check server.js`.
**EN:** Two **independent** `Node.js 18 + Express + ws` servers, both dockerized, both listen on
container **`:8080`** (`GET /status`, `WS /`). Both use the same client `WebSocketManager`. Spanish
comments (keep). Validate: `node --check server.js`.

```bash
cd Multiplayer       && docker compose up -d   # host :8080
cd MultiplayerInteriores && docker compose up -d   # host :8081 → contenedor :8080
```
URLs inyectadas / injected: `BuildConfig.MULTIPLAYER_SERVER_URL`, `BuildConfig.INTERIORS_SERVER_URL`.

---

> **🔀 INSTANCING (Normal vs Apocalipsis):** el mundo abierto está **sharded por `ws.instance`**
> (`"normal"` / `"apocalipsis"`). Los clientes de una instancia **no ven** a los de la otra ni sus
> NPCs/zombis. **Todo el relay y el roster van acotados por instancia:** `broadcastToOthers`,
> `broadcastToNearby` (AOI) y `broadcastToInstance(inst, msg)` filtran por `instOf(client)`; cada NPC se
> etiqueta con `instance`; el AOI/GC/cap/`SYNC_ALL_NPCS`/`MASTER_SYNC_CHECK` y la elección de Host se
> hacen **por instancia**. `broadcastAll` queda solo como compat (sin uso por-instancia). El cliente
> entra en `"normal"`; el toggle del apocalipsis envía `JOIN_INSTANCE "apocalipsis"` (y al salir
> `"normal"`), limpiando `remoteEntities` para no arrastrar el otro mundo (`WorldMapViewModel.setZombieInstance`).
> Las **hordas migratorias** las calcula el **Host** (en `NpcAiManager`, ya simula la IA), no el servidor;
> los zombis de la oleada se replican por `NPC_BATCH_UPDATE` como cualquier otro (el servidor solo relaya).

## 1) Open-world server — `Multiplayer/server.js` (~490 líneas)

**ES:** Patrón **Zone Host**: distribuye autoridad. **NO simula NPCs** (no hay pathfinding aquí); la IA
del open world vive en el cliente (`NpcAiManager`). El servidor **arbitra el rol de Host y RELAYA**.
**EN:** **Zone Host** pattern: distributes authority. It does **NOT simulate NPCs** (no pathfinding
here); open-world AI lives in the client (`NpcAiManager`). The server **arbitrates the Host role and
RELAYS**.

> **Importante — está en v3 (más allá del README):** el servidor es ahora el **DUEÑO del roster
> persistente**. Los NPCs civiles **ya no se borran** cuando su Host se desconecta: se marcan
> **huérfanos** (`ownerId=null`) pero se conservan y se reenvían (`SYNC_ALL_NPCS`) para que el Host más
> cercano los **adopte**. El GC periódico ya **no** borra por antigüedad; solo aplica un **cap duro**
> (`MAX_SERVER_NPCS=150`, evict del más viejo priorizando huérfanos). / **It's v3 (beyond the README):**
> the server now **owns a persistent roster**; civilian NPCs are **not deleted** when their Host leaves —
> they're orphaned (`ownerId=null`), kept, and re-broadcast so the nearest Host adopts them. Periodic GC
> no longer deletes by age; it only enforces a hard cap.

**Constantes / constants:**
```
PORT=8080  HOST_RADIUS=0.004  AOI_RADIUS=0.02  HOST_EVAL_MS=200
MAX_MSGS_PER_SEC=80  MAX_MSG_BYTES=8192  MAX_DAMAGE=100  MAX_SERVER_NPCS=150
```

**Funciones / functions:** `safeCoord`, `safeNum`, `safeDamage` (saneamiento), `broadcastToOthers`,
`broadcastAll`, `broadcastToNearby(senderWs, msg, ox, oy, radius)` (AOI), `enforceNpcCap`,
`orphanNpcsOf(ownerId)`, `rateLimited(ws, now)`.

**Endurecimiento v2 / v2 hardening:**
- **AOI:** `NPC_SPAWN/UPDATE/BATCH` solo a clientes dentro de `AOI_RADIUS` (~2 km) del Host emisor;
  los globales (`PLAYER_UPDATE`, `PLAYER_DAMAGE`, `NPC_DESTROY`, `DISCONNECT`, sync) van a todos.
- **Elección de Host con throttle** (`HOST_EVAL_MS` = cada 200 ms/cliente).
- **Rate-limit por socket** (ventana 1 s, `MAX_MSGS_PER_SEC`) + `MAX_MSG_BYTES`.
- **Saneamiento:** coords finitas, daño finito y acotado (`MAX_DAMAGE`). Colores como ARGB Int.
- **GC de jugadores fantasma** periódico (no solo al cerrar socket).

**Policía v3.2:** `POLICE_BATCH_UPDATE` (AOI) y `POLICE_DESTROY` (global) se **relayan sin** meterse al
roster (la policía es transitoria y por-jugador; cada cliente la purga por *staleness*).

### Protocolo open world / open-world wire protocol

| Mensaje / Message | Dir | Ámbito / Scope | Significado |
|---|---|---|---|
| `SESSION_INIT` | S→C | — | El servidor asigna `sessionId` al conectar |
| `ROLE_UPDATE` | S→C | — | Cambio de rol de Host de zona |
| `PLAYER_UPDATE` | C↔S | **global** | Pose/estado de un jugador (`MultiplayerPlayer`) |
| `PLAYER_DAMAGE` | C↔S | **global** | Daño aplicado a un jugador |
| `NPC_SPAWN` / `NPC_UPDATE` | C↔S | AOI | NPC individual del Host |
| `NPC_BATCH_UPDATE` | C↔S | AOI | Lote de NPCs activos del Host (`MultiplayerNpc[]`, incl. health/isDying/aggroUntil) |
| `NPC_DESTROY` | C↔S | **global** | NPC eliminado (despawn/muerte) |
| `SYNC_ALL_NPCS` | S→C | global | Roster completo (reenvío para adopción) |
| `MASTER_SYNC_CHECK` | C↔S | — | Sincronización maestra periódica |
| `DISCONNECT` | C↔S | global | Jugador se va |
| `POLICE_BATCH_UPDATE` | C↔S | AOI | Patrullas/cops del jugador buscado |
| `POLICE_DESTROY` | C↔S | global | Unidad de policía eliminada |
| `JOIN_INSTANCE` | C→S | — | **Instancing:** el jugador cambia de mundo (`{instance:"normal"\|"apocalipsis"}`). El servidor lo mueve de instancia, avisa `DISCONNECT` a la vieja y le manda `SYNC_ALL_NPCS` de la nueva. **Reemplaza a `ZOMBIE_MODE_SET`** (el apocalipsis ya NO es un flag global; es la instancia en la que estás). |
| `NPC_MARKER`, `POLICE_WAYPOINT`, `POLICE_CLEAN_ALLD` | C↔S | — | Usados por el cliente (marcadores/limpieza) |

> **ES:** `MultiplayerNpc` lleva `health`, `isDying`, `aggroUntil` para que **todos** pinten barra de
> vida, muerte y sepan que el NPC embiste (y cada cliente aplique daño por contacto a SU jugador). La
> vida del jugador viaja en `PLAYER_UPDATE`. / `MultiplayerNpc` carries `health`/`isDying`/`aggroUntil`
> so every client renders health/death and applies contact damage locally; player HP rides `PLAYER_UPDATE`.

---

## 2) Zombie-minigame server — `MultiplayerInteriores/server.js` (~740 líneas)

> ⚠️ **NO confundir con el Modo Zombi Global** (apocalipsis sobre el mapa): ese vive en el servidor del
> **mundo abierto** (`Multiplayer/server.js`, instancia `"apocalipsis"` vía `JOIN_INSTANCE`, zombis como
> `NpcType.ZOMBIE` en `NPC_BATCH_UPDATE`, **hordas** calculadas por el Host). **Este** servidor
> (`MultiplayerInteriores/`) es SOLO el **minijuego de interiores** (lobby + edificios), con su propia
> conexión `INTERIORS_SERVER_URL`. Son dos sistemas separados.

**ES:** Corre la **simulación de zombis de forma autoritativa por sala** y difunde `ZOMBIE_STATE`.
Coordenadas en el cable **fraccionarias `[0,1]`** (el cliente convierte a píxeles).

> **NPCs civiles + coords server-authoritative (nuevo):** cada sala BUILDING spawnea ~6 **civiles**
> (`spawnRoom` → `st.npcs`) que **deambulan** (`fallbackWander`) y **huyen** del zombi más cercano
> (`moveToward` en dirección opuesta); si un zombi los toca (`CONTACT_FRAC`) **se convierten en zombi**
> (`st.total++`). Viajan en `ZOMBIE_STATE.npcs` (`NetInteriorNpc`); el cliente los renderiza con
> `RemotePlayerView` (figura humana, `ZombieGameState.interiorNpcs`). Además el **movimiento del jugador
> se valida en el servidor**: en `PLAYER_UPDATE`, si la posición cae en pared (`isBlocked`) se rechaza y
> se manda `PLAYER_CORRECT`. Los interiores ya estaban **instanciados por `roomId`** (separación nativa).
**EN:** Runs the **zombie simulation authoritatively per room** and broadcasts `ZOMBIE_STATE`. Wire
coords are **fractional `[0,1]`** (client converts to pixels).

**Constantes / constants:**
```
PORT=8080  LOBBY_ID='lobby_campus'  BUILDING_MATRIX=borderOnly(30,20)  LOBBY_MATRIX=borderOnly(30,30)
TICK_MS=66  ZOMBIE_SPEED_FRAC=0.006  CONTACT_FRAC=0.03  DYING_MS=1000  SKILL_DROP_CHANCE=0.45
SPAWN_MIN=0.12  SPAWN_MAX=0.30  MARGIN=0.03
FIELD_TTL_MS=250  FIELD_PRUNE_MS=1000  WANDER_RESET_MS=1200
SEPARATION_FRAC=CONTACT_FRAC*1.15  SEPARATION_PUSH=ZOMBIE_SPEED_FRAC*0.5
BUILDING_ORDER=[...7 edificios...]   EFFECTS=[...6 SkillEffect...]
```
> Las matrices por defecto son **border-only** y deben coincidir filas/cols con las del cliente
> (`ZombieRoomCatalog`) hasta reemplazarse por `collision_matrices.json` (cargado con
> `loadMatrixOverrides`). / Default matrices are border-only and must match client rows/cols.

### IA de zombis v2 / zombie AI v2

**ES:** Por cada jugador objetivo se computa **UN campo de flujo de Dijkstra** (distancia-al-jugador
sobre toda la matriz) que **comparten todos** los zombis que persiguen a ese jugador, cacheado
`FIELD_TTL_MS` (250 ms) con un `MinHeap` binario. Cada zombi:
**EN:** Per target player, **ONE Dijkstra flow-field** (distance-to-player over the whole matrix) is
computed and **shared by all** zombies chasing that player, cached `FIELD_TTL_MS` via a binary
`MinHeap`. Each zombie:

```
stepZombie(z, target, def, st, now):
  1. LOS directo (hasLineOfSight) → moveToward en línea recta (suave, sin lookup de campo).
  2. si no → gradiente del campo: getField(...) → gradientTarget(...) → moveToward al centro de la celda cuesta abajo.
  3. si no hay campo finito ni LOS → fallbackWander (deambular, re-elige rumbo cada WANDER_RESET_MS).
  + separationNudge: empuja zombis cercanos (<SEPARATION_FRAC) para que no se apilen.
```
Funciones núcleo / core: `borderOnly`, `loadMatrixOverrides`, `makeRoomDef`, `zoneOf`, `clampFrac`,
`safeFrac`, `safeDamage`, `isBlocked`, `isCellBlocked`, `cellOf`, `cellCenterFrac`, `nearestWalkable`,
`class MinHeap`, `buildFlowField(def, goalCell)` (Dijkstra 8-conn sin corte de esquina),
`getField(st, def, goalCell, now)` (cache+TTL), `gradientTarget(field, def, zCell, tx, ty)`,
`hasLineOfSight(def, ax, ay, bx, by)`, `moveToward`, `fallbackWander`, `separationNudge`, `stepZombie`,
`nearestPlayer`, `spawnAround`, `spawnRoom`, `ensureRoomState`, `buildStatePayload`, `broadcastRoomState`,
`zombieTick` (loop cada `TICK_MS`), `sendRoomSnapshot`, `broadcastToRoom`.

**ES:** El estado de IA (wander, etc.) vive en **campos NO serializados**; el formato `ZOMBIE_STATE` en
el cable **no cambia**. / AI state lives in non-serialized fields; the `ZOMBIE_STATE` wire format is unchanged.

### Protocolo zombi / zombie wire protocol

| Mensaje / Message | Dir | Significado |
|---|---|---|
| `SESSION_INIT` | S→C | Asigna `sessionId` |
| `JOIN_ROOM` | C→S | Entrar a una sala (`roomId`) |
| `ROOM_SNAPSHOT` | S→C | Estado inicial de la sala al unirse |
| `PLAYER_UPDATE` | C↔S | Pose del jugador (x,y **fraccionarios**, action, facing, health) |
| `PLAYER_LEFT_ROOM` | S→C | Otro jugador salió |
| `ZOMBIE_STATE` | S→C | **Autoritativo:** lista de `NetZombie` + `NetItem` + **`NetInteriorNpc[]` (civiles)** + `totalZombies` |
| `PLAYER_CORRECT` | S→C | **Coords server-authoritative:** el servidor rechazó una posición dentro de pared (validó contra la matriz de la sala con `isBlocked`) y manda la posición válida `{x,y}` (fracción) para que el cliente ajuste al jugador |
| `ZOMBIE_DAMAGE` | C→S | El cliente PIDE daño a un zombi (`zombieId`, `damage`) |
| `ITEM_PICKUP` | C→S | El cliente PIDE recoger un item (`itemId`) |
| `ITEM_GRANTED` | S→C | Item concedido (`effect`) |
| `ROOM_CLEARED` | S→C | Edificio despejado (`cleared`) |

> Coordenadas de jugador y zombi/item son **fraccionarias [0,1]** en este servidor. El cliente las
> convierte a píxeles de la `ZombieRoom` actual. / Player and zombie/item coords are fractional here;
> client co