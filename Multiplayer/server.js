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
//     a clientes cercanos al Host emisor, en vez de a TODOS. En un mapa de ciudad
//     esto recorta drasticamente el ancho de banda y el trabajo de serializacion
//     cuando hay jugadores dispersos. Los mensajes que deben llegar siempre
//     (PLAYER_UPDATE, PLAYER_DAMAGE, NPC_DESTROY, DISCONNECT, sync) se mantienen
//     globales para no dejar entidades fantasma. Compromiso: un NPC muy lejano
//     puede "congelarse" para clientes fuera del radio, pero ahi no se renderiza.
//   - ELECCION DE HOST CON THROTTLE: el rol Host se reevalua como mucho cada
//     HOST_EVAL_MS por cliente, no en cada PLAYER_UPDATE (baja CPU; el rol no
//     necesita cambiar al instante).
//   - RATE LIMIT por socket: descarta mensajes si un cliente inunda (proteccion
//     basica de CPU / anti-flood), con ventana deslizante de 1 s.
//   - SANEAMIENTO: coordenadas finitas, dano finito y acotado, reenvio saneado.
//   - GC de jugadores fantasma (no solo al cerrar el socket).

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
// Radio de Area de Interes para reenvio de NPCs (~2 km). Generoso para que los
// NPCs no "aparezcan de golpe" cerca del jugador.
const AOI_RADIUS = 0.02;
// El rol de Host se reevalua como mucho cada esto (ms).
const HOST_EVAL_MS = 200;
// Limite anti-flood por socket.
const MAX_MSGS_PER_SEC = 80;
// Tamano maximo de mensaje aceptado (bytes); evita payloads abusivos.
const MAX_MSG_BYTES = 8192;
// Dano maximo aceptado del cliente (anti-cheat basico).
const MAX_DAMAGE = 100;

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
        estado: 'Online (v2)',
        jugadoresConectados: players.size,
        npcsActivos: npcs.size,
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

// Reenvio con Area de Interes: solo a clientes cuyo ultima posicion conocida este
// dentro de 'radius' del origen (ox, oy). Clientes sin posicion conocida reciben
// igual (no los penalizamos por recien conectados).
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
            broadcastAll(JSON.stringify({ type: "DISCONNECT", id }));
        }
    }
}, 5000);

// --- GC de NPCs huerfanos ---
const npcGcInterval = setInterval(() => {
    const now = Date.now();
    const npcsToDelete = [];
    for (const [npcId, npcData] of npcs.entries()) {
        if (now - (npcData.lastUpdated || now) > 15000) {
            npcsToDelete.push(npcId);
            npcs.delete(npcId);
        }
    }
    if (npcsToDelete.length > 0) {
        broadcastAll(JSON.stringify({ type: "DISCONNECT", orphanedNpcs: npcsToDelete }));
    }
}, 5000);

// --- Sincronizacion maestra periodica ---
const masterSyncInterval = setInterval(() => {
    if (wss.clients.size > 0) {
        broadcastAll(JSON.stringify({
            type: "MASTER_SYNC_CHECK",
            activeNpcIds: Array.from(npcs.keys())
        }));
    }
}, 5000);

// Limita la frecuencia de mensajes por socket (ventana deslizante de 1 s).
// Devuelve true si el mensaje debe descartarse.
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
                // Posicion conocida del socket (para AOI de NPCs).
                ws.x = px; ws.y = py;

                // Eleccion de Host con throttle: solo reevaluamos cada HOST_EVAL_MS.
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

                // Los jugadores se difunden SIEMPRE (global): el cliente no tiene
                // GC temporal de avatares remotos, asi que filtrarlos por AOI los
                // dejaria congelados. Solo saneamos los campos.
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
                    // AOI: solo a clientes cercanos al Host emisor.
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
                        const sanitized = { ...npc, x: nx, y: ny, ownerId: ws.sessionId, lastUpdated: now };
                        npcs.set(npc.id, sanitized);
                        accepted.push(sanitized);
                    });
                    if (accepted.length > 0) {
                        broadcastToNearby(ws, JSON.stringify({ type: "NPC_BATCH_UPDATE", npcs: accepted }), ws.x, ws.y, AOI_RADIUS);
                    }
                }
            }
            else if (data && data.type === "PLAYER_DAMAGE") {
                // Global: debe llegar al objetivo este donde este. Dano acotado.
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
                if (typeof data.npcId === 'string') {
                    npcs.delete(data.npcId);
                    broadcastToOthers(ws, JSON.stringify({ type: "NPC_DESTROY", npcId: data.npcId }));
                }
            }
        } catch (e) {
            console.error("Error al procesar mensaje:", e);
        }
    });

    ws.on('close', () => {
        console.log(`[-] Cliente desconectado. ID: ${ws.sessionId}`);
        players.delete(ws.sessionId);
        broadcastAll(JSON.stringify({ type: "DISCONNECT", id: ws.sessionId }));
        // Los NPCs del cliente se quedan: el nuevo Host los adopta; si nadie lo
        // hace, el GC los borra a los 15 s.
    });
});

server.on('close', () => {
    clearInterval(heartbeatInterval);
    clearInterval(playerGcInterval);
    clearInterval(npcGcInterval);
    clearInterval(masterSyncInterval);
});

server.listen(PORT, () => {
    console.log(`Servidor POW Multi-Zonas (v2: AOI + host-throttle + rate-limit) en el puerto ${PORT}`);
});
