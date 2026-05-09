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

// --- SISTEMA DE LATIDOS (TOLERANCIA DE 3 MINUTOS) ---
const interval = setInterval(() => {
    wss.clients.forEach((ws) => {
        if (ws.missedPings === undefined) ws.missedPings = 0;

        if (ws.isAlive === false) {
            ws.missedPings++;
            if (ws.missedPings >= 6) {
                console.log(`[!] Cliente inactivo por 3 minutos. Forzando cierre...`);
                return ws.terminate();
            }
        } else {
            ws.missedPings = 0; 
        }

        ws.isAlive = false; 
        ws.ping(); 
    });
}, 30000); 

// --- NUEVO: SISTEMA DE LIMPIEZA ABSOLUTA (MASTER SYNC) ---
// Cada 5 segundos, el servidor envía la lista OFICIAL de IDs a todos.
setInterval(() => {
    if (wss.clients.size > 0) {
        const masterList = Array.from(npcs.keys());
        const syncMsg = JSON.stringify({
            type: "MASTER_SYNC_CHECK",
            activeNpcIds: masterList
        });
        broadcastAll(syncMsg);
    }
}, 5000);

wss.on('connection', (ws, req) => {
    ws.sessionId = uuidv4();
    ws.isAlive = true;
    ws.missedPings = 0;

    console.log(`[+] Cliente conectado. ID Sesión: ${ws.sessionId}`);

    // Inform the client of its server-assigned session ID so it can use it
    // as the authoritative player identifier (prevents collisions from
    // two clients choosing the same display name).
    ws.send(JSON.stringify({ type: 'SESSION_INIT', sessionId: ws.sessionId }));

    ws.on('pong', () => {
        ws.isAlive = true;
    });

    const existingNpcs = Array.from(npcs.values());
    if (existingNpcs.length > 0) {
        ws.send(JSON.stringify({ 
            type: "SYNC_ALL_NPCS", 
            npcs: existingNpcs 
        }));
    }

    ws.on('message', (messageAsString) => {
        try {
            const data = JSON.parse(messageAsString);

            if (data && (!data.type || data.type === "PLAYER_UPDATE")) {
                // Use the server-generated sessionId as the canonical key so that
                // two clients with identical display names cannot overwrite each other.
                ws.playerId = ws.sessionId;
                // Only store explicitly allowed fields to prevent clients from injecting
                // arbitrary properties into the server's player state.
                players.set(ws.sessionId, {
                    id: ws.sessionId,
                    displayName: typeof data.displayName === 'string' ? data.displayName : '',
                    x: typeof data.x === 'number' ? data.x : 0,
                    y: typeof data.y === 'number' ? data.y : 0,
                    action: typeof data.action === 'string' ? data.action : '',
                    facingRight: typeof data.facingRight === 'boolean' ? data.facingRight : true
                });
                // Broadcast with the authoritative session ID so other clients can
                // use it for entity ownership checks.
                broadcastToOthers(ws, JSON.stringify({ ...data, id: ws.sessionId }));
            } 
            else if (data && (data.type === "NPC_SPAWN" || data.type === "NPC_UPDATE")) {
                if (data.npc && data.npc.id) {
                    npcs.set(data.npc.id, { ...data.npc, ownerId: ws.sessionId });
                    broadcastToOthers(ws, messageAsString);
                }
            } 
            else if (data && data.type === "NPC_BATCH_UPDATE") {
                if (data.npcs && Array.isArray(data.npcs)) {
                    data.npcs.forEach(npc => {
                        if (npc.id) {
                            npcs.set(npc.id, { ...npc, ownerId: ws.sessionId });
                        }
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
        console.log(`[-] Cliente desconectado. ID Sesión: ${ws.sessionId}`);
        players.delete(ws.sessionId);
        const npcsToDelete = [];
        for (const [npcId, npcData] of npcs.entries()) {
            if (npcData.ownerId === ws.sessionId) {
                npcsToDelete.push(npcId);
                npcs.delete(npcId);
            }
        }

        const disconnectMessage = JSON.stringify({
             type: "DISCONNECT",
             id: ws.sessionId,
             orphanedNpcs: npcsToDelete
        });
        broadcastAll(disconnectMessage);
    });
});

server.on('close', () => {
    clearInterval(interval);
});

server.listen(PORT, () => {
    console.log(`=========================================`);
    console.log(`🚀 Servidor POW escuchando en el puerto ${PORT}`);
    console.log(`=========================================`);
});