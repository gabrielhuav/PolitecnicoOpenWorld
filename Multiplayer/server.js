// Servidor de juego multijugador con gestión de NPCs y roles de zona (Host)
// Basado en Express, HTTP y WebSockets (ws)

const express = require('express');
const http = require('http');
const WebSocket = require('ws');
const cors = require('cors');
const { v4: uuidv4 } = require('uuid');

const app = express();
app.use(cors());

const PORT = process.env.PORT || 8080;
const server = http.createServer(app);

// Almacenamiento de jugadores y NPCs en memoria
const players = new Map();   // clave: sessionId, valor: datos del jugador
const npcs = new Map();      // clave: npc.id, valor: objeto NPC

// Radio de autoridad del Host (aproximadamente 400 metros en coordenadas arbitrarias)
const HOST_RADIUS = 0.004;
const HOST_RADIUS_SQ = HOST_RADIUS * HOST_RADIUS;

// Intervalos y umbrales operacionales
const HEARTBEAT_INTERVAL_MS = 30000;
const NPC_GC_INTERVAL_MS = 5000;
const NPC_ORPHAN_TIMEOUT_MS = 15000;
const MASTER_SYNC_INTERVAL_MS = 5000;
const MAX_MISSED_PINGS = 6;

// Tipos de mensaje del protocolo WebSocket
const MSG = Object.freeze({
    SESSION_INIT: 'SESSION_INIT',
    ROLE_UPDATE: 'ROLE_UPDATE',
    PLAYER_UPDATE: 'PLAYER_UPDATE',
    NPC_SPAWN: 'NPC_SPAWN',
    NPC_UPDATE: 'NPC_UPDATE',
    NPC_BATCH_UPDATE: 'NPC_BATCH_UPDATE',
    NPC_DESTROY: 'NPC_DESTROY',
    DISCONNECT: 'DISCONNECT',
    MASTER_SYNC_CHECK: 'MASTER_SYNC_CHECK',
    SYNC_ALL_NPCS: 'SYNC_ALL_NPCS',
});

function squaredDistance(a, b) {
    const dx = a.x - b.x;
    const dy = a.y - b.y;
    return dx * dx + dy * dy;
}

function sanitizePlayerData(data, sessionId, isHost) {
    const asType = (v, type, fallback) => (typeof v === type ? v : fallback);
    return {
        id: sessionId,
        displayName: asType(data.displayName, 'string', ''),
        x: asType(data.x, 'number', 0),
        y: asType(data.y, 'number', 0),
        action: asType(data.action, 'string', ''),
        facingRight: asType(data.facingRight, 'boolean', true),
        isHost,
        isDriving: asType(data.isDriving, 'boolean', false),
        carModel: asType(data.carModel, 'string', null),
        carColor: asType(data.carColor, 'number', null),
        vehicleRotation: asType(data.vehicleRotation, 'number', 0),
    };
}

// Decide si `ws` debe ser host tras un PLAYER_UPDATE.
// - Si no era host: promueve a host si no hay otro host dentro del radio.
// - Si era host: cede sólo si hay otro host cercano con sessionId menor (tie-break determinista).
function electHost(ws, data, players) {
    const nearbyOtherHosts = [];
    for (const other of players.values()) {
        if (other.isHost && other.id !== ws.sessionId &&
            squaredDistance(other, data) < HOST_RADIUS_SQ) {
            nearbyOtherHosts.push(other);
        }
    }
    if (!ws.isHost) {
        return nearbyOtherHosts.length === 0;
    }
    for (const other of nearbyOtherHosts) {
        if (ws.sessionId > other.id) return false;
    }
    return true;
}

app.get('/status', (req, res) => {
    res.json({
        estado: 'Online',
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
            client.send(messageAsString);
        }
    });
}

function broadcastAll(messageAsString) {
    wss.clients.forEach((client) => {
        if (client.readyState === WebSocket.OPEN) {
            client.send(messageAsString);
        }
    });
}

// --- Mecanismo de latidos (heartbeat) para detectar clientes caídos ---
const heartbeatInterval = setInterval(() => {
    wss.clients.forEach((ws) => {
        if (ws.isAlive === false) {
            ws.missedPings++;
            if (ws.missedPings >= MAX_MISSED_PINGS) return ws.terminate();
        } else {
            ws.missedPings = 0;
        }
        ws.isAlive = false;
        ws.ping();
    });
}, HEARTBEAT_INTERVAL_MS);

// --- Recolector de basura (Garbage Collector) para NPCs huérfanos ---
const npcGcInterval = setInterval(() => {
    const now = Date.now();
    const npcsToDelete = [];
    for (const [npcId, npcData] of npcs.entries()) {
        if (now - (npcData.lastUpdated || now) > NPC_ORPHAN_TIMEOUT_MS) {
            npcsToDelete.push(npcId);
            npcs.delete(npcId);
        }
    }
    if (npcsToDelete.length > 0) {
        broadcastAll(JSON.stringify({ type: MSG.DISCONNECT, orphanedNpcs: npcsToDelete }));
    }
}, NPC_GC_INTERVAL_MS);

// --- Sincronización maestra periódica ---
const masterSyncInterval = setInterval(() => {
    if (wss.clients.size > 0) {
        broadcastAll(JSON.stringify({
            type: MSG.MASTER_SYNC_CHECK,
            activeNpcIds: Array.from(npcs.keys())
        }));
    }
}, MASTER_SYNC_INTERVAL_MS);

wss.on('connection', (ws) => {
    ws.sessionId = uuidv4();
    ws.isAlive = true;
    ws.missedPings = 0;
    ws.isHost = true;    // Por defecto, un nuevo cliente es Host en su zona

    console.log(`[+] Cliente conectado. ID: ${ws.sessionId}`);
    ws.send(JSON.stringify({ type: MSG.SESSION_INIT, sessionId: ws.sessionId }));

    // Notificación de rol inicial.
    // ws.isHost se inicializa en true, pero el cliente nunca lo sabría sin esto:
    // los ROLE_UPDATE posteriores solo se envían cuando el rol CAMBIA, así que
    // sin este mensaje inicial el cliente jamás activaría isServerDelegatedHost
    // y nadie spawnearía NPCs.
    ws.send(JSON.stringify({ type: MSG.ROLE_UPDATE, isZoneHost: ws.isHost }));

    ws.on('pong', () => { ws.isAlive = true; });

    const existingNpcs = Array.from(npcs.values());
    if (existingNpcs.length > 0) {
        ws.send(JSON.stringify({ type: MSG.SYNC_ALL_NPCS, npcs: existingNpcs }));
    }

    ws.on('message', (messageAsString) => {
        try {
            const data = JSON.parse(messageAsString);

            if (data && (!data.type || data.type === MSG.PLAYER_UPDATE)) {
                const isNowHost = electHost(ws, data, players);

                if (ws.isHost !== isNowHost) {
                    ws.isHost = isNowHost;
                    ws.send(JSON.stringify({ type: MSG.ROLE_UPDATE, isZoneHost: isNowHost }));

                    if (!isNowHost) {
                        console.log(`[Zonas] ${ws.sessionId} cedió su autoridad.`);
                    } else {
                        console.log(`[Zonas] ${ws.sessionId} retomó autoridad como Host y adoptará a los NPCs.`);
                    }
                }

                players.set(ws.sessionId, sanitizePlayerData(data, ws.sessionId, ws.isHost));

                broadcastToOthers(ws, JSON.stringify({ ...data, id: ws.sessionId }));
            }
            else if (data && (data.type === MSG.NPC_SPAWN || data.type === MSG.NPC_UPDATE)) {
                if (data.npc && data.npc.id) {
                    npcs.set(data.npc.id, { ...data.npc, ownerId: ws.sessionId, lastUpdated: Date.now() });
                    broadcastToOthers(ws, messageAsString);
                }
            }
            else if (data && data.type === MSG.NPC_BATCH_UPDATE) {
                if (data.npcs && Array.isArray(data.npcs)) {
                    data.npcs.forEach(npc => {
                        if (npc.id) npcs.set(npc.id, { ...npc, ownerId: ws.sessionId, lastUpdated: Date.now() });
                    });
                    broadcastToOthers(ws, messageAsString);
                }
            }
            else if (data && data.type === MSG.NPC_DESTROY) {
                if (data.npcId) {
                    npcs.delete(data.npcId);
                    broadcastToOthers(ws, messageAsString);
                }
            }
        } catch (e) {
            console.error("Error al procesar mensaje:", e);
        }
    });

    ws.on('close', () => {
        console.log(`[-] Cliente desconectado. ID: ${ws.sessionId}`);
        players.delete(ws.sessionId);

        broadcastAll(JSON.stringify({ type: MSG.DISCONNECT, id: ws.sessionId }));

        // Los NPCs del cliente desconectado se quedan en el mapa.
        // El nuevo host de la zona los adoptará en su próximo ciclo.
        // Si nadie los adopta, el GC los borra a los 15 segundos.
    });
});

// Limpieza de los 3 intervalos al cerrar el servidor.
// La versión anterior solo llamaba clearInterval(interval) con una variable
// que ya no existía, lo que causaba un ReferenceError.
server.on('close', () => {
    clearInterval(heartbeatInterval);
    clearInterval(npcGcInterval);
    clearInterval(masterSyncInterval);
});

server.listen(PORT, () => {
    console.log(`Servidor POW Multi-Zonas Persistente (Adopción) en el puerto ${PORT}`);
});