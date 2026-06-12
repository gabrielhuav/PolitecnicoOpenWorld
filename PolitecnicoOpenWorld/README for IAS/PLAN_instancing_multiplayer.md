# PLAN — Instancing multijugador (Normal vs Apocalipsis) + rename + interiores

> Objetivo: separar a los jugadores en **instancias paralelas** para que los del modo **normal** no se
> vean afectados por el **apocalipsis zombi**, en los dos servidores. Sigue MVVM y las convenciones de `09`.

---

## 0. Resumen / TL;DR

1. **Renombrar `MultiplayerInteriores/` → `MultiplayerInteriores/`** (es el servidor del minijuego de
   interiores, no "de zombis"). Mecánico.
2. **Mundo abierto (`Multiplayer/`): añadir INSTANCIAS** (`"normal"` / `"apocalipsis"`). Hoy NO las tiene
   (todos en un mundo global; `ZOMBIE_MODE_SET` se difunde a todos = mal). Se replica el patrón que los
   **interiores ya usan** (`roomId`): relay + roster + Host + AOI **acotados por instancia**.
3. **Interiores (`MultiplayerInteriores/`): ya está instanciado por sala.** Falta: **coords/colisión de
   jugador gestionadas por el servidor** y **NPCs civiles dentro de los interiores** (server-side).

> **Descubrimiento:** el servidor de interiores YA separa por `roomId` (`broadcastToRoom`,
> `playersInRoom`, estado de zombis por sala). El del mundo abierto NO. El instancing del mundo abierto
> = darle el mismo "canal/roomId" que ya tienen los interiores.

---

## 1. Rename `MultiplayerInteriores/` → `MultiplayerInteriores/`

**ES:** El directorio es independiente (hermano del proyecto Android). El cliente usa la URL desplegada
`BuildConfig.INTERIORS_SERVER_URL` (no el nombre del dir), así que el rename del directorio solo afecta dev
local (ruta de `docker compose`) y docs.

- `mv MultiplayerInteriores MultiplayerInteriores` (o hacerlo desde el IDE/Explorador).
- Revisar dentro: `Dockerfile`, `docker-compose.yml`, `package.json` (suelen ser agnósticos a la ruta).
- **Opcional** (rename más profundo): `BuildConfig.INTERIORS_SERVER_URL` → `INTERIORS_SERVER_URL` en Gradle
  + sus usos en el cliente (`MainActivity`, `ZombieGameViewModel`). Mayor churn; se puede dejar para después.
- Actualizar docs (`01`, `08`, `09`, este plan): "servidor zombi" → "servidor de interiores".

> El **modo apocalipsis del mapa global NO se mueve** a este servidor: sigue en el del mundo abierto (ver
> turno anterior / `09`). Este servidor es solo interiores.

---

## 2. Mundo abierto — INSTANCIAS (el núcleo) `Multiplayer/server.js`

Hoy hay UNA sola realidad. Añadimos un **canal/instancia** por cliente, igual que `roomId` en interiores.

### Servidor
- Cada `ws` tiene `ws.instance` (default `"normal"`). Valores v1: `"normal"`, `"apocalipsis"`.
- **Relay acotado por instancia:** `broadcastToOthers`, `broadcastAll`, `broadcastToNearby` solo entregan
  a clientes con el **mismo `ws.instance`**. (Añadir el filtro `client.instance === senderWs.instance`.)
- **Roster de NPCs por instancia:** cada NPC guarda su `instance` (al recibir `NPC_*` se etiqueta con
  `ws.instance`). El AOI, el `SYNC_ALL_NPCS`, el GC y el cap (`MAX_SERVER_NPCS`) filtran por instancia.
- **Elección de Host por instancia:** el Host se calcula solo entre jugadores de la **misma** instancia
  (los rangos `HOST_RADIUS`/`AOI_RADIUS` se evalúan dentro de la instancia).
- **Nuevo mensaje `JOIN_INSTANCE { instance }`:** el servidor cambia `ws.instance`, avisa a la instancia
  vieja que el jugador se fue (`DISCONNECT` allí) y manda al jugador un `SYNC_ALL_NPCS` **filtrado a la
  nueva instancia** (mundo limpio).
- **Eliminar `ZOMBIE_MODE_SET` global:** el apocalipsis deja de ser un flag de mundo difundido a todos;
  pasa a ser **"en qué instancia estás"**. (Quitar el `broadcastAll(ZOMBIE_MODE_SET)`.)

### Cliente (`WorldMapViewModel`)
- Estado: `currentInstance: String` ("normal"/"apocalipsis").
- `toggleGlobalZombieMode()` →
  - Activar: `sendMessage(JOIN_INSTANCE "apocalipsis")`, **limpiar `remoteEntities`** (no arrastrar el
    mundo normal), `globalZombieMode = true`, `npcAiManager.globalZombieMode = true`.
  - Desactivar: `JOIN_INSTANCE "normal"`, limpiar `remoteEntities`, `globalZombieMode = false`, despawn
    zombis, `npcAiManager.globalZombieMode = false`.
- `handleMultiplayerMessage`: **quitar** la rama `ZOMBIE_MODE_SET` (ya no hay flag global). El apocalipsis
  es local + instancia.
- El Host sigue simulando zombis exactamente igual; ahora solo se replican **dentro de la instancia
  apocalipsis** (gracias al relay acotado). Los de instancia normal nunca reciben zombis.

> **Resultado:** un jugador en "normal" no ve zombis ni jugadores de apocalipsis. Los de "apocalipsis"
> comparten un mundo paralelo con zombis. Es el "clon del modo normal con zombis" que pediste.

### Single-player
Sin servidor, `globalZombieMode` sigue siendo un flag local (el toggle no manda red). Igual que hoy.

---

## 3. Interiores — coords server + NPCs `MultiplayerInteriores/server.js`

Ya está instanciado por `roomId` (lobby + 7 edificios). Falta:

- **Coords/colisión de jugador gestionadas por el servidor:** hoy el cliente manda `PLAYER_UPDATE`
  (fraccionario) y el servidor lo relaya sin validar. Para "manejado por el servidor": validar el
  movimiento contra la **matriz de colisión de la sala** en el servidor (rechazar/recortar posiciones
  dentro de paredes) y devolver la posición saneada. Reusa `isBlocked(matrix, fx, fy)` que ya existe.
- **NPCs civiles en interiores:** spawnear/simular NPCs civiles **por sala** (server-authoritative, como
  los zombis): un `npcs` por `roomState`, movimiento simple (wander/patrulla por celdas libres), y
  difundirlos en el `ROOM_SNAPSHOT`/estado de sala. El cliente los renderiza como los zombis del minijuego.

> Esta parte (3) es una **expansión** del servidor de interiores, más independiente del instancing (2).
> Se puede hacer después del núcleo.

---

## 4. Orden sugerido / order

1. **Rename** (1) — mecánico, base limpia.
2. **Instancing del mundo abierto** (2) — servidor + cliente. Es lo que de verdad separa normal/apocalipsis.
3. **Interiores: NPCs + coords server** (3) — expansión.
4. **Actualizar docs** (`01`, `04`, `08`, `09`) en el mismo cambio (def. de hecho, `09` §13).

## 5. Decisiones abiertas / open decisions

- **Granularidad de la instancia apocalipsis:** ¿una sola instancia compartida (todos los de apocalipsis
  juntos) o privada por jugador/grupo? **Recomendado v1: una sola `"apocalipsis"` compartida** (simple y
  encaja con "los de apocalipsis están juntos, separados de los normales").
- **`INTERIORS_SERVER_URL` → `INTERIORS_SERVER_URL`:** ¿renombrar también la BuildConfig (más churn) o dejarla?
- **Interiores (parte 3):** ¿se hace en esta tanda o después del instancing del mundo abierto?
