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

// --- Mecanismo de latidos (heartbeat) para detectar clientes caídos ---
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

// --- Recolector de basura (Garbage Collector) para NPCs huérfanos ---
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

// --- Sincronización maestra periódica ---
const masterSyncInterval = setInterval(() => {
    if (wss.clients.size > 0) {
        broadcastAll(JSON.stringify({
            type: "MASTER_SYNC_CHECK",
            activeNpcIds: Array.from(npcs.keys())
        }));
    }
}, 5000);

wss.on('connection', (ws) => {
    ws.sessionId = uuidv4();
    ws.isAlive = true;
    ws.missedPings = 0;
    ws.isHost = true;    // Por defecto, un nuevo cliente es Host en su zona

    console.log(`[+] Cliente conectado. ID: ${ws.sessionId}`);
    ws.send(JSON.stringify({ type: 'SESSION_INIT', sessionId: ws.sessionId }));

    // Notificación de rol inicial.
    // ws.isHost se inicializa en true, pero el cliente nunca lo sabría sin esto:
    // los ROLE_UPDATE posteriores solo se envían cuando el rol CAMBIA, así que
    // sin este mensaje inicial el cliente jamás activaría isServerDelegatedHost
    // y nadie spawnearía NPCs.
    ws.send(JSON.stringify({ type: 'ROLE_UPDATE', isZoneHost: ws.isHost }));

    ws.on('pong', () => { ws.isAlive = true; });

    const existingNpcs = Array.from(npcs.values());
    if (existingNpcs.length > 0) {
        ws.send(JSON.stringify({ type: "SYNC_ALL_NPCS", npcs: existingNpcs }));
    }

    ws.on('message', (messageAsString) => {
        try {
            const data = JSON.parse(messageAsString);

            if (data && (!data.type || data.type === "PLAYER_UPDATE")) {
                let isNowHost = ws.isHost;

                if (!ws.isHost) {
                    let nearbyHost = false;
                    for (const other of players.values()) {
                        if (other.isHost && other.id !== ws.sessionId) {
                            const dist = Math.sqrt(Math.pow(other.y - data.y, 2) + Math.pow(other.x - data.x, 2));
                            if (dist < HOST_RADIUS) { nearbyHost = true; break; }
                        }
                    }
                    if (!nearbyHost) isNowHost = true;
                } else {
                    for (const other of players.values()) {
                        if (other.isHost && other.id !== ws.sessionId) {
                            const dist = Math.sqrt(Math.pow(other.y - data.y, 2) + Math.pow(other.x - data.x, 2));
                            if (dist < HOST_RADIUS) {
                                if (ws.sessionId > other.id) { isNowHost = false; break; }
                            }
                        }
                    }
                }

                if (ws.isHost !== isNowHost) {
                    ws.isHost = isNowHost;
                    ws.send(JSON.stringify({ type: "ROLE_UPDATE", isZoneHost: isNowHost }));

                    if (!isNowHost) {
                        console.log(`[Zonas] ${ws.sessionId} cedió su autoridad.`);
                    } else {
                        console.log(`[Zonas] ${ws.sessionId} retomó autoridad como Host y adoptará a los NPCs.`);
                    }
                }

                players.set(ws.sessionId, {
                    id: ws.sessionId,
                    displayName: typeof data.displayName === 'string' ? data.displayName : '',
                    x: typeof data.x === 'number' ? data.x : 0,
                    y: typeof data.y === 'number' ? data.y : 0,
                    action: typeof data.action === 'string' ? data.action : '',
                    facingRight: typeof data.facingRight === 'boolean' ? data.facingRight : true,
                    isHost: ws.isHost,
                    // Agrega los nuevos atributos del vehículo:
                    isDriving: typeof data.isDriving === 'boolean' ? data.isDriving : false,
                    carModel: typeof data.carModel === 'string' ? data.carModel : null,
                    carColor: typeof data.carColor === 'number' ? data.carColor : null,
                    vehicleRotation: typeof data.vehicleRotation === 'number' ? data.vehicleRotation : 0
                });

                broadcastToOthers(ws, JSON.stringify({ ...data, id: ws.sessionId }));
            }
            else if (data && (data.type === "NPC_SPAWN" || data.type === "NPC_UPDATE")) {
                if (data.npc && data.npc.id) {
                    npcs.set(data.npc.id, { ...data.npc, ownerId: ws.sessionId, lastUpdated: Date.now() });
                    broadcastToOthers(ws, messageAsString);
                }
            }
            else if (data && data.type === "NPC_BATCH_UPDATE") {
                if (data.npcs && Array.isArray(data.npcs)) {
                    data.npcs.forEach(npc => {
                        if (npc.id) npcs.set(npc.id, { ...npc, ownerId: ws.sessionId, lastUpdated: Date.now() });
                    });
                    broadcastToOthers(ws, messageAsString);
                }
            }
            else if (data && data.type === "NPC_DESTROY") {
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

        broadcastAll(JSON.stringify({ type: "DISCONNECT", id: ws.sessionId }));

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