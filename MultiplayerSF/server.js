// MultiplayerSF/server.js
// Servidor WebSocket del modo MULTIJUGADOR 1v1 de Street Fighter.
//
// Patrón de RELAY PURO: el servidor NO simula la pelea. Cada cliente simula
// SU propio peleador y envía su estado (~10 Hz). El daño lo aplica quien lo
// RECIBE (autoridad del receptor para el HP). El servidor solo sincroniza:
// selección de personaje+mapa, countdown 3-2-1, fin de pelea, revancha y
// abandono (victoria por desconexión).
//
// Salas por código de 4 letras (crear/unir), máx 2 jugadores.
// Heartbeat + limpieza de salas muertas.

const express = require('express');
const http = require('http');
const WebSocket = require('ws');
const cors = require('cors');
const { v4: uuidv4 } = require('uuid');
const { initFirebaseAuth, verifyClient } = require('./auth');

initFirebaseAuth();

const app = express();
app.use(cors());

const PORT = process.env.PORT || 8080;
const server = http.createServer(app);

// Endpoint de salud para el warmup de Render y health checks.
app.get('/status', (req, res) => {
    res.status(200).json({ status: 'ok', rooms: rooms.size });
});

// ─── ESTADO DEL SERVIDOR ──────────────────────────────────────────────────────
// rooms: Map<code, { p1: ws|null, p2: ws|null, char1: string|null, char2: string|null,
//                   map: string|null, phase: 'waiting'|'selecting'|'countdown'|'fighting'|'ended',
//                   countdownMs: number, winner: string|null, lastActivityMs: number }>
const rooms = new Map();
const wsToRoom = new Map(); // ws -> roomCode
// Lista de espera de SALA PÚBLICA (quick match): al haber 2, se emparejan solos
let publicQueue = [];

const MAX_ROOM_AGE_MS = 5 * 60 * 1000; // 5 minutos sin actividad -> limpieza
const HEARTBEAT_INTERVAL_MS = 30000;   // 30 segundos

// Genera un código de 4 letras mayúsculas
function generateRoomCode() {
    const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; // sin I, O, 0, 1 para evitar confusión
    let code = '';
    for (let i = 0; i < 4; i++) {
        code += chars.charAt(Math.floor(Math.random() * chars.length));
    }
    return code;
}

function getOrCreateRoom(code) {
    if (!rooms.has(code)) {
        rooms.set(code, {
            p1: null, p2: null,
            char1: null, char2: null,
            map: null,
            phase: 'waiting',
            countdownMs: 0,
            winner: null,
            lastActivityMs: Date.now()
        });
    }
    return rooms.get(code);
}

function broadcastToRoom(room, msg, excludeWs = null) {
    const payload = JSON.stringify(msg);
    if (room.p1 && room.p1 !== excludeWs && room.p1.readyState === WebSocket.OPEN) {
        room.p1.send(payload);
    }
    if (room.p2 && room.p2 !== excludeWs && room.p2.readyState === WebSocket.OPEN) {
        room.p2.send(payload);
    }
}

function cleanupDeadRooms() {
    const now = Date.now();
    for (const [code, room] of rooms.entries()) {
        if (now - room.lastActivityMs > MAX_ROOM_AGE_MS) {
            if (room.p1) {
                room.p1.close(1000, 'Room expired');
                wsToRoom.delete(room.p1);
            }
            if (room.p2) {
                room.p2.close(1000, 'Room expired');
                wsToRoom.delete(room.p2);
            }
            rooms.delete(code);
            console.log(`[SF] Sala expirada limpiada: ${code}`);
        }
    }
}

setInterval(cleanupDeadRooms, 60000); // cada 1 minuto

// ─── WEBSOCKET SERVER ─────────────────────────────────────────────────────────
const wss = new WebSocket.Server({ server, verifyClient });

wss.on('connection', (ws, req) => {
    const sessionId = req.firebaseUid || uuidv4();
    ws.sessionId = sessionId;
    ws.lastHeartbeat = Date.now();
    console.log(`[SF] Conexión nueva: ${sessionId}`);

    ws.send(JSON.stringify({ type: 'SESSION_INIT', sessionId }));

    ws.on('message', (data) => {
        let msg;
        try {
            msg = JSON.parse(data.toString());
        } catch (e) {
            return;
        }

        const roomCode = wsToRoom.get(ws);
        const room = roomCode ? rooms.get(roomCode) : null;
        // Cualquier mensaje de un jugador EN SALA cuenta como actividad (si no, una
        // pelea larga con puros PLAYER_STATE expiraba la sala a los 5 minutos).
        if (room) room.lastActivityMs = Date.now();

        switch (msg.type) {
            case 'HEARTBEAT':
                ws.lastHeartbeat = Date.now();
                if (room) room.lastActivityMs = Date.now();
                break;

            case 'CREATE_ROOM': {
                if (room) break; // ya está en una sala
                let code = msg.code || generateRoomCode();
                // Validar que sea 4 caracteres alfanuméricos
                if (!/^[A-Z0-9]{4}$/.test(code)) {
                    code = generateRoomCode();
                }
                const newRoom = getOrCreateRoom(code);
                if (newRoom.p1 !== null) {
                    // Sala llena o código colisionado, generar otro
                    code = generateRoomCode();
                    const anotherRoom = getOrCreateRoom(code);
                    anotherRoom.p1 = ws;
                    wsToRoom.set(ws, code);
                    ws.send(JSON.stringify({ type: 'ROOM_CREATED', code, playerIndex: 1 }));
                } else {
                    newRoom.p1 = ws;
                    wsToRoom.set(ws, code);
                    ws.send(JSON.stringify({ type: 'ROOM_CREATED', code, playerIndex: 1 }));
                }
                break;
            }

            case 'JOIN_ROOM': {
                if (room) break;
                const code = (msg.code || '').toUpperCase();
                const targetRoom = rooms.get(code);
                // También rechaza salas SIN anfitrión (p1 se fue antes de que llegara el p2)
                if (!targetRoom || targetRoom.p1 === null || targetRoom.p2 !== null) {
                    ws.send(JSON.stringify({ type: 'ERROR', message: 'Sala no encontrada o llena' }));
                    break;
                }
                targetRoom.p2 = ws;
                targetRoom.phase = 'selecting';
                targetRoom.lastActivityMs = Date.now();
                wsToRoom.set(ws, code);
                ws.send(JSON.stringify({ type: 'ROOM_JOINED', code, playerIndex: 2 }));
                // Notificar al p1 que alguien se unió
                if (targetRoom.p1 && targetRoom.p1.readyState === WebSocket.OPEN) {
                    targetRoom.p1.send(JSON.stringify({ type: 'OPPONENT_JOINED', playerIndex: 2 }));
                }
                break;
            }

            case 'LEAVE_ROOM': {
                if (!room) break;
                handlePlayerLeave(ws, room, roomCode);
                break;
            }

            // ─── SALA PÚBLICA (lista de espera) ───
            case 'QUICK_MATCH': {
                if (room) break;
                publicQueue = publicQueue.filter(w => w.readyState === WebSocket.OPEN && w !== ws);
                const waiter = publicQueue.shift();
                if (waiter) {
                    // Empareja: el que esperaba es el ANFITRIÓN (p1)
                    const code = generateRoomCode();
                    const newRoom = getOrCreateRoom(code);
                    newRoom.p1 = waiter;
                    newRoom.p2 = ws;
                    newRoom.phase = 'selecting';
                    wsToRoom.set(waiter, code);
                    wsToRoom.set(ws, code);
                    waiter.send(JSON.stringify({ type: 'ROOM_CREATED', code, playerIndex: 1 }));
                    waiter.send(JSON.stringify({ type: 'OPPONENT_JOINED', playerIndex: 2 }));
                    ws.send(JSON.stringify({ type: 'ROOM_JOINED', code, playerIndex: 2 }));
                } else {
                    publicQueue.push(ws);
                    ws.send(JSON.stringify({ type: 'QUEUED', position: publicQueue.length }));
                }
                break;
            }

            case 'CANCEL_QUEUE': {
                publicQueue = publicQueue.filter(w => w !== ws);
                break;
            }

            // ─── Resumen de partidas activas ───
            case 'LIST_ROOMS': {
                const list = [];
                for (const [code, r] of rooms.entries()) {
                    list.push({
                        code,
                        players: (r.p1 ? 1 : 0) + (r.p2 ? 1 : 0),
                        phase: r.phase,
                    });
                }
                ws.send(JSON.stringify({ type: 'ROOMS_LIST', rooms: list, queue: publicQueue.length }));
                break;
            }

            case 'SELECT_CHARACTER': {
                if (!room) break;
                room.lastActivityMs = Date.now();
                if (ws === room.p1) room.char1 = msg.character;
                else if (ws === room.p2) room.char2 = msg.character;

                if (room.char1 && room.char2) {
                    room.phase = 'selecting_map';
                    broadcastToRoom(room, {
                        type: 'CHARACTERS_SELECTED',
                        char1: room.char1,
                        char2: room.char2
                    });
                }
                break;
            }

            case 'SELECT_MAP': {
                if (!room) break;
                room.lastActivityMs = Date.now();
                // El host (p1) decide el mapa, o el primero que lo manda
                if (!room.map) {
                    room.map = msg.map;
                    room.phase = 'countdown';
                    room.countdownMs = 3000; // 3 segundos
                    broadcastToRoom(room, {
                        type: 'MAP_SELECTED',
                        map: room.map,
                        countdownMs: room.countdownMs
                    });
                    // Iniciar countdown en el servidor (solo para sincronizar el inicio)
                    startCountdown(room, roomCode);
                }
                break;
            }

            case 'PLAYER_STATE': {
                if (!room || room.phase !== 'fighting' && room.phase !== 'countdown') break;
                // Relay puro al oponente
                const payload = { type: 'OPPONENT_STATE', ...msg };
                const target = (ws === room.p1) ? room.p2 : room.p1;
                if (target && target.readyState === WebSocket.OPEN) {
                    target.send(JSON.stringify(payload));
                }
                break;
            }

            case 'PLAYER_DAMAGE': {
                if (!room) break;
                // El daño SIEMPRE va al RIVAL del que lo manda (el receptor lo aplica a su
                // propio HP; autoridad del receptor). Se relaya el payload completo
                // (damage, strength, type, area...) para que el receptor decida bloqueo/pose.
                const target = (ws === room.p1) ? room.p2 : room.p1;
                if (target && target.readyState === WebSocket.OPEN) {
                    target.send(JSON.stringify({ ...msg, type: 'PLAYER_DAMAGE' }));
                }
                break;
            }

            case 'MATCH_ENDED': {
                if (!room) break;
                room.phase = 'ended';
                room.winner = msg.winner; // 'p1', 'p2', o 'abandon'
                room.lastActivityMs = Date.now();
                broadcastToRoom(room, { type: 'MATCH_ENDED', winner: msg.winner });
                break;
            }

            case 'REQUEST_REMATCH': {
                if (!room) break;
                // La revancha requiere que la pidan LOS DOS (si no, uno reseteaba la sala solo)
                if (ws === room.p1) room.rematch1 = true;
                else if (ws === room.p2) room.rematch2 = true;
                broadcastToRoom(room, { type: 'REMATCH_REQUESTED' }, ws); // avisa al rival
                if (room.rematch1 && room.rematch2) {
                    room.phase = 'selecting';
                    room.char1 = null;
                    room.char2 = null;
                    room.map = null;
                    room.winner = null;
                    room.rematch1 = false;
                    room.rematch2 = false;
                    broadcastToRoom(room, { type: 'REMATCH_ACCEPTED' });
                }
                break;
            }
        }
    });

    ws.on('close', () => {
        console.log(`[SF] Conexión cerrada: ${ws.sessionId}`);
        publicQueue = publicQueue.filter(w => w !== ws);
        const code = wsToRoom.get(ws);
        if (code) {
            const room = rooms.get(code);
            if (room) {
                // Si se cae durante la pelea, el otro gana por abandono
                if (room.phase === 'fighting' || room.phase === 'countdown') {
                    const winner = (ws === room.p1) ? 'p2' : 'p1';
                    room.phase = 'ended';
                    room.winner = 'abandon';
                    broadcastToRoom(room, { type: 'OPPONENT_DISCONNECTED', winner });
                }
                handlePlayerLeave(ws, room, code);
            }
        }
    });

    ws.on('error', (err) => {
        console.error(`[SF] Error en ws ${ws.sessionId}:`, err.message);
    });
});

function handlePlayerLeave(ws, room, code) {
    if (room.p1 === ws) room.p1 = null;
    if (room.p2 === ws) room.p2 = null;
    wsToRoom.delete(ws);

    // Si la sala queda vacía, se marca para limpieza (o se limpia ya)
    if (!room.p1 && !room.p2) {
        rooms.delete(code);
        console.log(`[SF] Sala vacía eliminada: ${code}`);
    } else {
        // Notificar al que queda que el oponente se fue
        const remaining = room.p1 || room.p2;
        if (remaining && remaining.readyState === WebSocket.OPEN) {
            remaining.send(JSON.stringify({ type: 'OPPONENT_LEFT' }));
        }
    }
}

function startCountdown(room, code) {
    let remaining = room.countdownMs;
    const interval = setInterval(() => {
        const currentRoom = rooms.get(code);
        if (!currentRoom || currentRoom.phase !== 'countdown') {
            clearInterval(interval);
            return;
        }
        remaining -= 1000;
        if (remaining <= 0) {
            currentRoom.phase = 'fighting';
            broadcastToRoom(currentRoom, { type: 'FIGHT_START' });
            clearInterval(interval);
        }
    }, 1000);
}

server.listen(PORT, () => {
    console.log(`[SF] Servidor Street Fighter escuchando en puerto ${PORT}`);
});