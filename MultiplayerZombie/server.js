// MultiplayerZombie/server.js
// Servidor del MINIJUEGO DE ZOMBIS. Fase 0: relay de jugadores por sala.
//
// Distingue la "zona" dentro del modo zombie a partir del roomId:
//   - lobby_campus  -> zona LOBBY    (el croquis / mapa global del campus)
//   - za_*          -> zona INTERIOR (auditorio, biblioteca, cafetería, ...)
//
// Solo empareja a jugadores que comparten exactamente el mismo roomId, de modo
// que quien está en un interior NO ve a quien está en el lobby ni en otro
// interior. Los zombis todavía NO son autoritativos aquí: eso llega en la Fase 1.

const express = require('express');
const http = require('http');
const WebSocket = require('ws');
const cors = require('cors');
const { v4: uuidv4 } = require('uuid');

const app = express();
app.use(cors());

const PORT = process.env.PORT || 8080;
const server = http.createServer(app);

const LOBBY_ID = 'lobby_campus';

// players: sessionId -> { id, displayName, roomId, zone, x, y, action, facingRight, health, lastUpdated }
const players = new Map();

// Clasifica una sala como LOBBY o INTERIOR.
function zoneOf(roomId) {
    return roomId === LOBBY_ID ? 'LOBBY' : 'INTERIOR';
}

app.get('/status', (req, res) => {
    const porSala = {};
    for (const p of players.values()) {
        porSala[p.roomId] = (porSala[p.roomId] || 0) + 1;
    }
    res.json({
        estado: 'Online (Zombie)',
        modo: 'ZOMBIE',
        jugadoresConectados: players.size,
        salas: porSala,
        timestamp: new Date().toISOString()
    });
});

const wss = new WebSocket.Server({ server });

// Reenvía SOLO a clientes que están en la misma sala que el emisor.
function broadcastToRoom(roomId, senderWs, messageAsString) {
    wss.clients.forEach((client) => {
        if (client !== senderWs &&
            client.readyState === WebSocket.OPEN &&
            client.roomId === roomId) {
            client.send(messageAsString.toString());
        }
    });
}

// Envía al emisor la lista de quién más está en su sala (para pintarlos al entrar).
function sendRoomSnapshot(ws) {
    const others = [];
    for (const p of players.values()) {
        if (p.id !== ws.sessionId && p.roomId === ws.roomId) {
            others.push(p);
        }
    }
    ws.send(JSON.stringify({
        type: 'ROOM_SNAPSHOT',
        roomId: ws.roomId,
        zone: zoneOf(ws.roomId),
        players: others
    }));
}

// --- Heartbeat (igual que el server de open world) ---
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

// --- GC de jugadores fantasma (sin actualizar en >15s) ---
const playerGcInterval = setInterval(() => {
    const now = Date.now();
    for (const [id, p] of players.entries()) {
        if (now - (p.lastUpdated || now) > 15000) {
            players.delete(id);
            // Avisar a la sala donde estaba
            wss.clients.forEach((client) => {
                if (client.readyState === WebSocket.OPEN && client.roomId === p.roomId) {
                    client.send(JSON.stringify({ type: 'PLAYER_LEFT_ROOM', id }));
                }
            });
        }
    }
}, 5000);

wss.on('connection', (ws) => {
    ws.sessionId = uuidv4();
    ws.isAlive = true;
    ws.missedPings = 0;
    ws.roomId = null; // aún no ha entrado a ninguna sala

    console.log(`[+Z] Cliente conectado. ID: ${ws.sessionId}`);
    ws.send(JSON.stringify({ type: 'SESSION_INIT', sessionId: ws.sessionId }));

    ws.on('pong', () => { ws.isAlive = true; });

    ws.on('message', (raw) => {
        try {
            const data = JSON.parse(raw);

            switch (data.type) {
                // El cliente anuncia en qué sala/edificio está. Se llama al entrar
                // al minijuego y cada vez que cruza una puerta.
                case 'JOIN_ROOM': {
                    const oldRoom = ws.roomId;
                    ws.roomId = typeof data.roomId === 'string' ? data.roomId : LOBBY_ID;

                    // Avisar a la sala vieja que este jugador se fue
                    if (oldRoom && oldRoom !== ws.roomId) {
                        broadcastToRoom(oldRoom, ws,
                            JSON.stringify({ type: 'PLAYER_LEFT_ROOM', id: ws.sessionId }));
                    }

                    players.set(ws.sessionId, {
                        id: ws.sessionId,
                        displayName: data.displayName || '',
                        roomId: ws.roomId,
                        zone: zoneOf(ws.roomId),
                        x: typeof data.x === 'number' ? data.x : 0,
                        y: typeof data.y === 'number' ? data.y : 0,
                        action: 'IDLE',
                        facingRight: true,
                        health: 100,
                        lastUpdated: Date.now()
                    });

                    console.log(`[Z] ${ws.sessionId} entró a ${ws.roomId} (${zoneOf(ws.roomId)})`);

                    // 1. Mandarle a ESTE cliente quién más hay en su sala.
                    sendRoomSnapshot(ws);

                    // 2. Avisar a la sala nueva que llegó alguien (con su estado inicial).
                    broadcastToRoom(ws.roomId, ws, JSON.stringify({
                        type: 'PLAYER_UPDATE',
                        id: ws.sessionId,
                        displayName: data.displayName || '',
                        x: data.x || 0,
                        y: data.y || 0,
                        action: 'IDLE',
                        facingRight: true,
                        health: 100
                    }));
                    break;
                }

                // Actualización de posición del jugador dentro de su sala.
                case 'PLAYER_UPDATE': {
                    if (!ws.roomId) break;
                    const prev = players.get(ws.sessionId) || {};
                    players.set(ws.sessionId, {
                        ...prev,
                        id: ws.sessionId,
                        displayName: data.displayName ?? prev.displayName ?? '',
                        roomId: ws.roomId,
                        zone: zoneOf(ws.roomId),
                        x: typeof data.x === 'number' ? data.x : 0,
                        y: typeof data.y === 'number' ? data.y : 0,
                        action: data.action || 'IDLE',
                        facingRight: data.facingRight === true,
                        health: typeof data.health === 'number' ? data.health : (prev.health ?? 100),
                        lastUpdated: Date.now()
                    });
                    broadcastToRoom(ws.roomId, ws,
                        JSON.stringify({ ...data, type: 'PLAYER_UPDATE', id: ws.sessionId }));
                    break;
                }
            }
        } catch (e) {
            console.error("[Z] Error al procesar mensaje:", e);
        }
    });

    ws.on('close', () => {
        console.log(`[-Z] Cliente desconectado. ID: ${ws.sessionId}`);
        const room = ws.roomId;
        players.delete(ws.sessionId);
        if (room) {
            broadcastToRoom(room, ws,
                JSON.stringify({ type: 'PLAYER_LEFT_ROOM', id: ws.sessionId }));
        }
    });
});

server.on('close', () => {
    clearInterval(heartbeatInterval);
    clearInterval(playerGcInterval);
});

server.listen(PORT, () => {
    console.log(`Servidor POW ZOMBIE (Fase 0: relay por sala) en el puerto ${PORT}`);
});