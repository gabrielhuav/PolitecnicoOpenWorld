// Servidor de juego multijugador con gestion de NPCs y roles de zona (Host)
// Basado en Express, HTTP y WebSockets (ws)
//
// NOTA SOBRE LA "IA":
//   La IA de NPCs del mundo abierto vive en el CLIENTE (NpcAiManager.kt). Este
//   servidor NO simula NPCs: arbitra el rol de Host de zona y RELAYA mensajes.
//   Por eso aqui no hay pathfinding (meterlo exigiria mover la autoridad de NPCs
//   al servidor, un rediseno mayor). Lo que se optimiza/endurece aqui es el relay.
//
// MEJORAS v2:
//   - AREA DE INTERES (AOI) para NPCs: los NPC_SPAWN/UPDATE/BATCH solo se reenvian
//     a clientes cercanos al Host emisor, en vez de a TODOS.
//   - ELECCION DE HOST CON THROTTLE: el rol Host se reevalua como mucho cada
//     HOST_EVAL_MS por cliente.
//   - RATE LIMIT por socket (ventana deslizante de 1 s) + tamano maximo de mensaje.
//   - SANEAMIENTO: coordenadas finitas, dano finito y acotado, reenvio saneado.
//
// MEJORAS v3 (POBLACION PERSISTENTE — el servidor es el DUENO del roster):
//   - Los NPCs civiles YA NO se borran cuando su Host se desconecta. Antes el GC
//     de huerfanos los eliminaba a los 15 s, dejando el mundo vacio con pocos
//     jugadores. Ahora, al irse un Host, sus NPCs se marcan HUERFANOS (ownerId=null)
//     pero SE CONSERVAN; el servidor los reenvia (SYNC_ALL_NPCS) a los clientes que
//     quedan para que el Host mas cercano los ADOPTE y siga simulando su IA. Si no
//     hay ningun Host, los NPCs quedan "congelados" en su ultima posicion hasta que
//     vuelva alguien (el mundo nunca queda vacio).
//   - RE-DELEGACION: como el cliente ya adopta cualquier NPC remoto cuyo ownerId no
//     sea el suyo (NpcAiManager.moveNpc reconstruye la ruta), basta con marcar
//     huerfano + reenviar para que el nuevo Host tome el control en el siguiente tick.
//   - CAP DURO de poblacion en el servidor (MAX_SERVER_NPCS): si se excede, se hace
//     evict del mas viejo (priorizando huerfanos) para no crecer sin limite.
//   - El GC periodico ya NO borra NPCs por antiguedad; solo aplica el cap.
//
// NOTA SOBRE FEAR/CHARLAS/TRAFICO: estos comportamientos los corre el Host en el
// cliente (NpcAiManager) y se manifiestan como movimiento/rotacion, que se relayan
// en NPC_BATCH_UPDATE sin cambiar el formato del cable. El servidor no necesita
// logica extra para ellos: PLAYER_DAMAGE ya es global y cada Host dispersa sus NPCs.
//
// MEJORAS v3.1 (IA estilo GTA — atropello / agresividad / doble sentido):
//   Toda la IA NUEVA (personalidad, embestida, atropello, tráfico en doble sentido)
//   también vive en el Host (cliente) y se ve a traves del MOVIMIENTO ya relayado.
//   Lo UNICO nuevo en el cable son tres campos opcionales por NPC en NPC_BATCH_UPDATE:
//     - health    (0..100): para que TODOS los clientes pinten la barra de vida del NPC.
//     - isDying   (bool): para mostrar el NPC "muriendo" (atropello/golpes) en todos.
//     - aggroUntil(ms): marca de tiempo hasta la que el NPC esta EMBISTIENDO; permite que
//       cualquier cliente sepa que ese NPC ataca y aplique el dano por contacto a SU
//       propio jugador (no solo el Host de zona).
//   El servidor los CONSERVA (spread `...npc`) y, ademas, sanea `health` al rango
//   valido. Las muertes siguen llegando como NPC_DESTROY (global). El contraataque
//   (golpe de vuelta / NPC implacable) lo resuelve el cliente que ataco contra SU propia
//   vida; la vida del jugador ya viaja en PLAYER_UPDATE. No hace falta logica extra aqui.

// MEJORAS v3.2 (NIVEL DE BUSQUEDA / POLICIA estilo GTA):
//   Al golpear NPCs subes de "estrellas" y aparecen patrullas que te persiguen, sueltan
//   policias (emoji) que te golpean/disparan, hacen persecuciones en auto y pueden bajarte
//   del vehiculo. TODA esa logica vive en el cliente (PoliceManager.kt) y la SIMULA el
//   jugador buscado (no el Host de zona), porque la policia debe perseguirlo a EL.
//   En el cable hay dos mensajes NUEVOS, que el servidor solo RELAYA (no toca el roster):
//     - POLICE_BATCH_UPDATE { npcs:[{id,x,y,rotation,npcType}] }: posiciones de mis
//       patrullas/policias, reenviadas con AOI para que los demas clientes las VEAN.
//     - POLICE_DESTROY { npcId }: una unidad dejo de existir (se subio a su patrulla,
//       murio, o bajaste de estrellas). Global.
//   La policia NO se persiste en el roster (es transitoria y por-jugador): cada cliente
//   la purga por "staleness" si su dueno deja de emitir, asi que no necesita GC propio.

const express = require('express');
const http = require('http');
const WebSocket = require('ws');
const cors = require('cors');
const { v4: uuidv4 } = require('uuid');

const app = express();
app.use(cors());

const PORT = process.env.PORT || 8080;
const server = http.createServer(app);

const players = new Map();
const npcs = new Map();

// Radio de autoridad del Host (~400 m en coordenadas arbitrarias).
const HOST_RADIUS = 0.004;
// Radio de Area de Interes para reenvio de NPCs (~2 km).
const AOI_RADIUS = 0.02;
// El rol de Host se reevalua como mucho cada esto (ms).
const HOST_EVAL_MS = 200;
// Limite anti-flood por socket.
const MAX_MSGS_PER_SEC = 80;
// Tamano maximo de mensaje aceptado (bytes).
const MAX_MSG_BYTES = 8192;
// Dano maximo aceptado del cliente (anti-cheat basico).
const MAX_DAMAGE = 100;
// CAP DURO de NPCs que el servidor mantiene en su roster persistente. Si se excede,
// se hace evict (primero huerfanos, luego los mas viejos). Generoso para varias
// zonas activas a la vez, pero acotado para no crecer indefinidamente.
const MAX_SERVER_NPCS = 150;

// ─── SANEAMIENTO ───────────────────────────────────────────────────────────────
function safeCoord(v, fallback) {
    return (typeof v === 'number' && isFinite(v)) ? v : fallback;
}
function safeNum(v, fallback) {
    return (typeof v === 'number' && isFinite(v)) ? v : fallback;
}
function safeDamage(v) {
    if (typeof v !== 'number' || !isFinite(v) || v <= 0) return 0;
    return Math.min(v, MAX_DAMAGE);
}

app.get('/status', (req, res) => {
    res.json({
        estado: 'Online (v3: roster persistente)',
        jugadoresConectados: players.size,
        npcsActivos: npcs.size,
        npcsHuerfanos: Array.from(npcs.values()).filter(n => !n.ownerId).length,
        jugadores: Array.from(players.values()),
        timestamp: new Date().toISOString()
    });
});

const wss = new WebSocket.Server({ server });

function broadcastToOthers(senderWs, messageAsString) {
    wss.clients.forEach((client) => {
        if (client !== senderWs && client.readyState === WebSocket.OPEN) {
            client.send(messageAsString.toString());
        }
    });
}

function broadcastAll(messageAsString) {
    wss.clients.forEach((client) => {
        if (client.readyState === WebSocket.OPEN) {
            client.send(messageAsString.toString());
        }
    });
}

// Reenvio con Area de Interes.
function broadcastToNearby(senderWs, messageAsString, ox, oy, radius) {
    const msg = messageAsString.toString();
    const hasOrigin = (typeof ox === 'number' && isFinite(ox) && typeof oy === 'number' && isFinite(oy));
    wss.clients.forEach((client) => {
        if (client === senderWs || client.readyState !== WebSocket.OPEN) return;
        if (hasOrigin && typeof client.x === 'number' && isFinite(client.x)) {
            const d = Math.hypot(client.x - ox, client.y - oy);
            if (d > radius) return;
        }
        client.send(msg);
    });
}

// --- Heartbeat ---
const heartbeatInterval = setInterval(() => {
    wss.clients.forEach((ws) => {
        if (ws.missedPings === undefined) ws.missedPings = 0;
        if (ws.isAlive === false) {
            ws.missedPings++;
            if (ws.missedPings >= 6) return ws.terminate();
        } else {
            ws.missedPings = 0;
        }
        ws.isAlive = false;
        ws.ping();
    });
}, 30000);

// --- GC de jugadores fantasma ---
const playerGcInterval = setInterval(() => {
    const now = Date.now();
    for (const [id, p] of players.entries()) {
        if (now - (p.lastUpdated || now) > 15000) {
            players.delete(id);
            // Al reapar un jugador fantasma, huerfanamos sus NPCs (no los borramos).
            orphanNpcsOf(id);
            broadcastAll(JSON.stringify({ type: "DISCONNECT", id }));
        }
    }
}, 5000);

// --- Mantenimiento del roster persistente de NPCs (v3) ---
// Ya NO borra NPCs por antiguedad: el servidor es el dueno y los conserva aunque su
// Host se haya ido (quedan congelados hasta que otro Host los adopte). Lo unico que
// hace es aplicar el CAP DURO de poblacion para no crecer sin limite, priorizando
// el evict de NPCs huerfanos y, dentro de esos, de los mas viejos.
const npcRosterInterval = setInterval(() => {
    enforceNpcCap();
}, 5000);

function enforceNpcCap() {
    if (npcs.size <= MAX_SERVER_NPCS) return;
    const excess = npcs.size - MAX_SERVER_NPCS;
    // Orden de sacrificio: primero huerfanos (sin owner), luego por mas antiguos.
    const candidates = Array.from(npcs.values()).sort((a, b) => {
        const ao = a.ownerId ? 1 : 0;
        const bo = b.ownerId ? 1 : 0;
        if (ao !== bo) return ao - bo;                 // huerfanos primero
        return (a.lastUpdated || 0) - (b.lastUpdated || 0); // mas viejos primero
    });
    const toDelete = candidates.slice(0, excess).map(n => n.id);
    toDelete.forEach(id => npcs.delete(id));
    if (toDelete.length > 0) {
        toDelete.forEach(id => broadcastAll(JSON.stringify({ type: "NPC_DESTROY", npcId: id })));
    }
}

// Marca como huerfanos (ownerId=null) todos los NPCs cuyo dueno era 'ownerId'. NO los
// borra. Reenvia el lote a los clientes que quedan para que el Host mas cercano los
// adopte (el cliente adopta cualquier NPC remoto con ownerId != el suyo). Asi el
// mundo no se vacia al desconectarse un Host.
function orphanNpcsOf(ownerId) {
    const now = Date.now();
    const orphaned = [];
    for (const [npcId, npcData] of npcs.entries()) {
        if (npcData.ownerId === ownerId) {
            npcData.ownerId = null;
            npcData.orphanedAt = now;
            orphaned.push(npcData);
        }
    }
    if (orphaned.length > 0) {
        // Reenviar como SYNC para que los clientes que ya los tenian no los limpien
        // (MASTER_SYNC_CHECK los conserva porque siguen en el roster) y los que esten
        // cerca empiecen a simularlos. ownerId=null => cualquier Host los adopta.
        broadcastAll(JSON.stringify({ type: "SYNC_ALL_NPCS", npcs: orphaned }));
        console.log(`[Roster] ${orphaned.length} NPC(s) huerfanos tras irse ${ownerId}; conservados para re-delegacion.`);
    }
}

// --- Sincronizacion maestra periodica ---
const masterSyncInterval = setInterval(() => {
    if (wss.clients.size > 0) {
        broadcastAll(JSON.stringify({
            type: "MASTER_SYNC_CHECK",
            activeNpcIds: Array.from(npcs.keys())
        }));
    }
}, 5000);

function rateLimited(ws, now) {
    if (!ws.rlWindowStart || now - ws.rlWindowStart >= 1000) {
        ws.rlWindowStart = now;
        ws.rlCount = 0;
    }
    ws.rlCount++;
    return ws.rlCount > MAX_MSGS_PER_SEC;
}

wss.on('connection', (ws) => {
    ws.sessionId = uuidv4();
    ws.isAlive = true;
    ws.missedPings = 0;
    ws.isHost = true;
    ws.lastHostEvalMs = 0;
    ws.rlWindowStart = 0;
    ws.rlCount = 0;

    console.log(`[+] Cliente conectado. ID: ${ws.sessionId}`);
    ws.send(JSON.stringify({ type: 'SESSION_INIT', sessionId: ws.sessionId }));
    ws.send(JSON.stringify({ type: 'ROLE_UPDATE', isZoneHost: ws.isHost }));

    ws.on('pong', () => { ws.isAlive = true; });

    // El recien llegado recibe TODO el roster persistente (incluye huerfanos), para
    // que pueda renderizarlos y, si es Host cercano, adoptarlos.
    const existingNpcs = Array.from(npcs.values());
    if (existingNpcs.length > 0) {
        ws.send(JSON.stringify({ type: "SYNC_ALL_NPCS", npcs: existingNpcs }));
    }

    ws.on('message', (messageAsString) => {
        try {
            const now = Date.now();
            if (rateLimited(ws, now)) return;
            if (messageAsString && messageAsString.length > MAX_MSG_BYTES) return;

            const data = JSON.parse(messageAsString);

            if (data && (!data.type || data.type === "PLAYER_UPDATE")) {
                const prev = players.get(ws.sessionId);
                const px = safeCoord(data.x, prev ? prev.x : 0);
                const py = safeCoord(data.y, prev ? prev.y : 0);
                ws.x = px; ws.y = py;

                if (now - ws.lastHostEvalMs >= HOST_EVAL_MS) {
                    ws.lastHostEvalMs = now;
                    let isNowHost = ws.isHost;
                    if (!ws.isHost) {
                        let nearbyHost = false;
                        for (const other of players.values()) {
                            if (other.isHost && other.id !== ws.sessionId) {
                                const dist = Math.sqrt(Math.pow(other.y - py, 2) + Math.pow(other.x - px, 2));
                                if (dist < HOST_RADIUS) { nearbyHost = true; break; }
                            }
                        }
                        if (!nearbyHost) isNowHost = true;
                    } else {
                        for (const other of players.values()) {
                            if (other.isHost && other.id !== ws.sessionId) {
                                const dist = Math.sqrt(Math.pow(other.y - py, 2) + Math.pow(other.x - px, 2));
                                if (dist < HOST_RADIUS) {
                                    if (ws.sessionId > other.id) { isNowHost = false; break; }
                                }
                            }
                        }
                    }
                    if (ws.isHost !== isNowHost) {
                        ws.isHost = isNowHost;
                        ws.send(JSON.stringify({ type: "ROLE_UPDATE", isZoneHost: isNowHost }));
                        console.log(`[Zonas] ${ws.sessionId} ${isNowHost ? 'retomo' : 'cedio'} autoridad de Host.`);
                    }
                }

                const stored = {
                    id: ws.sessionId,
                    displayName: typeof data.displayName === 'string' ? data.displayName : '',
                    x: px, y: py,
                    action: typeof data.action === 'string' ? data.action : '',
                    facingRight: typeof data.facingRight === 'boolean' ? data.facingRight : true,
                    isHost: ws.isHost,
                    isDriving: typeof data.isDriving === 'boolean' ? data.isDriving : false,
                    carModel: typeof data.carModel === 'string' ? data.carModel : null,
                    carColor: typeof data.carColor === 'number' ? data.carColor : null,
                    vehicleRotation: safeNum(data.vehicleRotation, 0),
                    health: (typeof data.health === 'number' && isFinite(data.health))
                        ? Math.max(0, Math.min(100, data.health)) : 100,
                    lastUpdated: now
                };
                players.set(ws.sessionId, stored);

                broadcastToOthers(ws, JSON.stringify({
                    type: "PLAYER_UPDATE",
                    id: ws.sessionId,
                    displayName: stored.displayName,
                    x: stored.x, y: stored.y,
                    action: stored.action,
                    facingRight: stored.facingRight,
                    isDriving: stored.isDriving,
                    carModel: stored.carModel,
                    carColor: stored.carColor,
                    vehicleRotation: stored.vehicleRotation,
                    health: stored.health
                }));
            }
            else if (data && (data.type === "NPC_SPAWN" || data.type === "NPC_UPDATE")) {
                if (data.npc && typeof data.npc.id === 'string') {
                    const n = data.npc;
                    const nx = safeCoord(n.x, null);
                    const ny = safeCoord(n.y, null);
                    if (nx === null || ny === null) return;
                    const sanitized = { ...n, x: nx, y: ny, ownerId: ws.sessionId, lastUpdated: now };
                    npcs.set(n.id, sanitized);
                    enforceNpcCap();
                    broadcastToNearby(ws, JSON.stringify({ ...data, npc: sanitized }), ws.x, ws.y, AOI_RADIUS);
                }
            }
            else if (data && data.type === "NPC_BATCH_UPDATE") {
                if (data.npcs && Array.isArray(data.npcs)) {
                    const accepted = [];
                    data.npcs.forEach(npc => {
                        if (!npc || typeof npc.id !== 'string') return;
                        const nx = safeCoord(npc.x, null);
                        const ny = safeCoord(npc.y, null);
                        if (nx === null || ny === null) return;
                        // Al actualizar, este Host se convierte en el dueno (re-delegacion
                        // automatica: un Host que adopta un huerfano lo reclama aqui).
                        // v3.1: saneamos health (0..100) si viene; el resto pasa por spread.
                        const hp = (typeof npc.health === 'number' && isFinite(npc.health))
                            ? Math.max(0, Math.min(100, npc.health)) : npc.health;
                        const sanitized = { ...npc, x: nx, y: ny, health: hp, ownerId: ws.sessionId, lastUpdated: now };
                        npcs.set(npc.id, sanitized);
                        accepted.push(sanitized);
                    });
                    if (accepted.length > 0) {
                        enforceNpcCap();
                        broadcastToNearby(ws, JSON.stringify({ type: "NPC_BATCH_UPDATE", npcs: accepted }), ws.x, ws.y, AOI_RADIUS);
                    }
                }
            }
            else if (data && data.type === "PLAYER_DAMAGE") {
                // Global: debe llegar al objetivo este donde este. Dano acotado.
                // (Cada Host, al recibirlo, dispersa sus propios NPCs cercanos al objetivo.)
                if (typeof data.targetId === 'string') {
                    const dmg = safeDamage(data.damage);
                    if (dmg > 0) {
                        broadcastToOthers(ws, JSON.stringify({
                            type: "PLAYER_DAMAGE", targetId: data.targetId, damage: dmg
                        }));
                    }
                }
            }
            else if (data && data.type === "NPC_DESTROY") {
                // Global: evita NPCs muertos "fantasma" en clientes que ya lo tenian.
                // Esto es una muerte REAL (combate / despawn por poblacion del Host), no
                // una desconexion: aqui SI se borra del roster.
                if (typeof data.npcId === 'string') {
                    npcs.delete(data.npcId);
                    broadcastToOthers(ws, JSON.stringify({ type: "NPC_DESTROY", npcId: data.npcId }));
                }
            }
            else if (data && data.type === "POLICE_BATCH_UPDATE") {
                // POLICIA (nivel de busqueda estilo GTA). A diferencia de los NPCs civiles,
                // la policia la SIMULA Y POSEE el jugador buscado (debe perseguirlo a EL).
                // El servidor solo la RELAYA con AOI; NO la guarda en el roster persistente
                // porque es transitoria y por-jugador: los clientes la purgan solos por
                // "staleness" si su dueno deja de enviarla (o al recibir POLICE_DESTROY).
                if (data.npcs && Array.isArray(data.npcs)) {
                    const accepted = [];
                    data.npcs.forEach(p => {
                        if (!p || typeof p.id !== 'string') return;
                        const nx = safeCoord(p.x, null);
                        const ny = safeCoord(p.y, null);
                        if (nx === null || ny === null) return;
                        accepted.push({ ...p, x: nx, y: ny, ownerId: ws.sessionId });
                    });
                    if (accepted.length > 0) {
                        broadcastToNearby(ws, JSON.stringify({ type: "POLICE_BATCH_UPDATE", npcs: accepted }), ws.x, ws.y, AOI_RADIUS);
                    }
                }
            }
            else if (data && data.type === "POLICE_DESTROY") {
                // Una unidad de policia dejo de existir (se subio a su patrulla, murio, o
                // bajo el nivel de busqueda). Global, para que todos la quiten al instante.
                if (typeof data.npcId === 'string') {
                    broadcastToOthers(ws, JSON.stringify({ type: "POLICE_DESTROY", npcId: data.npcId }));
                }
            }
        } catch (e) {
            console.error("Error al procesar mensaje:", e);
        }
    });

    ws.on('close', () => {
        console.log(`[-] Cliente desconectado. ID: ${ws.sessionId}`);
        players.delete(ws.sessionId);
        // v3: NO borramos sus NPCs. Los huerfanamos y reenviamos para que otro Host
        // los adopte; si no hay nadie, quedan congelados (mundo nunca vacio).
        orphanNpcsOf(ws.sessionId);
        broadcastAll(JSON.stringify({ type: "DISCONNECT", id: ws.sessionId }));
    });
});

server.on('close', () => {
    clearInterval(heartbeatInterval);
    clearInterval(playerGcInterval);
    clearInterval(npcRosterInterval);
    clearInterval(masterSyncInterval);
});

server.listen(PORT, () => {
    console.log(`Servidor POW Multi-Zonas (v3: roster persistente + AOI + host-throttle + rate-limit) en el puerto ${PORT}`);
});
