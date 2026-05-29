// MultiplayerZombie/server.js
// Servidor del MINIJUEGO DE ZOMBIS — FASE 1: zombis AUTORITATIVOS en el servidor.
//
// Cambios respecto a la Fase 0 (que solo relayaba jugadores por sala):
//   1. El servidor SIMULA los zombis de cada edificio (spawn, movimiento, vida,
//      muerte y drop de items). Persigue SIEMPRE al jugador más cercano de la
//      sala y respeta la MISMA matriz de colisión que el cliente.
//   2. La matriz de colisión vive aquí en coordenadas fraccionarias [0,1],
//      idéntica a la del catálogo Kotlin (ZombieRoomCatalog.kt). Como es
//      fraccionaria, no importa que cada cliente decodifique la imagen de fondo
//      con dimensiones distintas. Si existe `collision_matrices.json` (exportado
//      desde la app por el Modo Diseñador), sus matrices SOBREESCRIBEN a las de
//      abajo, manteniendo cliente y servidor sincronizados.
//   3. Matar un zombi se refleja para TODOS: el cliente solo PIDE daño
//      (ZOMBIE_DAMAGE) y el servidor decide vida/muerte y lo difunde a la sala.
//
// IMPORTANTE: todas las coordenadas en el cable (jugadores, zombis e items) son
// FRACCIONARIAS [0,1]. El cliente convierte a píxeles con su worldWidth/Height.

const express = require('express');
const http = require('http');
const WebSocket = require('ws');
const cors = require('cors');
const fs = require('fs');
const path = require('path');
const { v4: uuidv4 } = require('uuid');

const app = express();
app.use(cors());

const PORT = process.env.PORT || 8080;
const server = http.createServer(app);

const LOBBY_ID = 'lobby_campus';

// ─── MATRICES DE COLISIÓN (por defecto, NEUTRAS) ──────────────────────────────
// '#' = pared (bloqueado), '.' = caminable. Sólo borde: punto de partida neutro,
// idéntico a ZombieRoomCatalog.kt. La matriz REAL de cada cuarto se pinta en la
// app (Modo Diseñador), se exporta y se copia a collision_matrices.json, que
// SOBREESCRIBE estas por roomId. Mapeo: col = floor(fx*cols), row = floor(fy*rows).
function borderOnly(cols, rows) {
    const out = [];
    for (let r = 0; r < rows; r++) {
        let line = '';
        for (let c = 0; c < cols; c++) {
            const border = (r === 0 || r === rows - 1 || c === 0 || c === cols - 1);
            line += border ? '#' : '.';
        }
        out.push(line);
    }
    return out;
}

const BUILDING_MATRIX = borderOnly(30, 20); // 30 col × 20 fil
const LOBBY_MATRIX = borderOnly(30, 30);    // 30 col × 30 fil

// ─── OVERRIDES desde collision_matrices.json (Modo Diseñador) ─────────────────
function loadMatrixOverrides() {
    try {
        const p = path.join(__dirname, 'collision_matrices.json');
        if (!fs.existsSync(p)) return {};
        const j = JSON.parse(fs.readFileSync(p, 'utf8'));
        const rooms = (j && j.rooms) ? j.rooms : (j || {});
        console.log(`[Z] collision_matrices.json cargado (${Object.keys(rooms).length} salas).`);
        return rooms;
    } catch (e) {
        console.error('[Z] No se pudo leer collision_matrices.json:', e.message);
        return {};
    }
}

// ─── CATÁLOGO DE SALAS (replica del lado servidor) ────────────────────────────
// Orden idéntico a ZombieRoomCatalog.buildingOrder. zombieCount = 4 + (i % 3).
const BUILDING_ORDER = [
    "za_auditorio", "za_biblioteca", "za_cafeteria",
    "za_edificio", "za_estacionamiento", "za_palapas", "za_canchas_futbol"
];

const ROOMS = (() => {
    const ov = loadMatrixOverrides();
    const map = {};
    map[LOBBY_ID] = {
        id: LOBBY_ID, type: 'LOBBY',
        matrix: ov[LOBBY_ID] || LOBBY_MATRIX,
        zombieCount: 0, spawn: { x: 0.50, y: 0.86 }
    };
    BUILDING_ORDER.forEach((id, i) => {
        map[id] = {
            id, type: 'BUILDING',
            matrix: ov[id] || BUILDING_MATRIX,
            zombieCount: 4 + (i % 3), spawn: { x: 0.50, y: 0.55 }
        };
    });
    return map;
})();

// ─── CONSTANTES DE SIMULACIÓN ─────────────────────────────────────────────────
const TICK_MS = 66;                 // ~15 Hz de simulación + difusión
const ZOMBIE_SPEED_FRAC = 0.006;    // paso por tick en fracción de mundo
const CONTACT_FRAC = 0.03;          // distancia de contacto en fracción
const DYING_MS = 1000;              // tiempo de animación de muerte antes de borrar
const SKILL_DROP_CHANCE = 0.45;     // probabilidad de soltar item al morir
const SPAWN_MIN = 0.12, SPAWN_MAX = 0.30;
const MARGIN = 0.03;

const EFFECTS = [
    "CURA_TOTAL", "RELOJ_ARENA", "ADRENALINA_ZOMBI",
    "FURIA_ZOMBI", "DEBILIDAD_ZOMBI", "FUERZA_BRUTA"
];

// ─── ESTADO ───────────────────────────────────────────────────────────────────
const players = new Map();    // sessionId -> { id, displayName, roomId, zone, x, y, action, facingRight, health, lastUpdated }
const roomState = new Map();  // roomId(edificio) -> { zombies:[...], items:[...], cleared, total }

function zoneOf(roomId) { return roomId === LOBBY_ID ? 'LOBBY' : 'INTERIOR'; }
function clampFrac(v) { return Math.max(MARGIN, Math.min(1 - MARGIN, v)); }

// Coordenada fraccionaria SEGURA desde la red: acepta sólo números finitos
// (typeof NaN === 'number' e Infinity pasarían el typeof, por eso isFinite) y
// la clampa al área jugable. Si no es válida, usa fallback.
function safeFrac(v, fallback) {
    return (typeof v === 'number' && isFinite(v)) ? clampFrac(v) : fallback;
}

// Daño SEGURO: número finito y nunca negativo (evita "curar" zombis con < 0).
function safeDamage(v) {
    return (typeof v === 'number' && isFinite(v) && v > 0) ? v : 0;
}

function isBlocked(matrix, fx, fy) {
    if (!matrix || matrix.length === 0) return false;
    const rows = matrix.length;
    const cols = matrix[0].length;
    let c = Math.floor(fx * cols);
    let r = Math.floor(fy * rows);
    if (c < 0) c = 0; if (c >= cols) c = cols - 1;
    if (r < 0) r = 0; if (r >= rows) r = rows - 1;
    return matrix[r][c] === '#';
}

function playersInRoom(roomId) {
    const res = [];
    for (const p of players.values()) if (p.roomId === roomId) res.push(p);
    return res;
}

function randomEffect() { return EFFECTS[Math.floor(Math.random() * EFFECTS.length)]; }

app.get('/status', (req, res) => {
    const porSala = {};
    for (const p of players.values()) porSala[p.roomId] = (porSala[p.roomId] || 0) + 1;
    const zombisPorSala = {};
    for (const [rid, st] of roomState.entries()) zombisPorSala[rid] = st.zombies.length;
    res.json({
        estado: 'Online (Zombie F1)',
        modo: 'ZOMBIE',
        jugadoresConectados: players.size,
        salas: porSala,
        zombisActivos: zombisPorSala,
        timestamp: new Date().toISOString()
    });
});

const wss = new WebSocket.Server({ server });

function broadcastToRoom(roomId, senderWs, messageAsString) {
    wss.clients.forEach((client) => {
        if (client.readyState === WebSocket.OPEN &&
            client.roomId === roomId &&
            client !== senderWs) {
            client.send(messageAsString.toString());
        }
    });
}

function sendRoomSnapshot(ws) {
    const others = [];
    for (const p of players.values()) {
        if (p.id !== ws.sessionId && p.roomId === ws.roomId) others.push(p);
    }
    ws.send(JSON.stringify({
        type: 'ROOM_SNAPSHOT',
        roomId: ws.roomId,
        zone: zoneOf(ws.roomId),
        players: others
    }));
}

// ─── ZOMBIS: SPAWN / MOVIMIENTO / DIFUSIÓN ────────────────────────────────────
function spawnAround(def) {
    for (let k = 0; k < 25; k++) {
        const ang = Math.random() * 2 * Math.PI;
        const rad = SPAWN_MIN + Math.random() * (SPAWN_MAX - SPAWN_MIN);
        const x = clampFrac(def.spawn.x + Math.cos(ang) * rad);
        const y = clampFrac(def.spawn.y + Math.sin(ang) * rad);
        if (!isBlocked(def.matrix, x, y)) return { x, y };
    }
    return { x: clampFrac(def.spawn.x + SPAWN_MIN), y: def.spawn.y };
}

function spawnRoom(def) {
    const zombies = [];
    const lootIndex = Math.floor(Math.random() * Math.max(1, def.zombieCount));
    for (let i = 0; i < def.zombieCount; i++) {
        const pos = spawnAround(def);
        zombies.push({
            id: uuidv4(), x: pos.x, y: pos.y,
            health: 100, maxHealth: 100, facingRight: true, frameIndex: 0,
            isDying: false, dyingSince: 0, isLootCarrier: (i === lootIndex)
        });
    }
    return { zombies, items: [], cleared: false, total: def.zombieCount };
}

function ensureRoomState(roomId) {
    const def = ROOMS[roomId];
    if (!def || def.type !== 'BUILDING') return;
    if (roomState.has(roomId)) return;
    if (playersInRoom(roomId).length === 0) return;
    roomState.set(roomId, spawnRoom(def));
}

function nearestPlayer(z, present) {
    let best = null, bestD = Infinity;
    for (const p of present) {
        const d = Math.hypot(p.x - z.x, p.y - z.y);
        if (d < bestD) { bestD = d; best = p; }
    }
    return best;
}

function stepZombie(z, target, def) {
    const dx = target.x - z.x, dy = target.y - z.y;
    const d = Math.hypot(dx, dy);
    let nx = 0, ny = 0;
    if (d > 0.0001) { nx = dx / d; ny = dy / d; }
    const step = (d > CONTACT_FRAC * 0.7) ? ZOMBIE_SPEED_FRAC : 0;

    const tx = clampFrac(z.x + nx * step);
    const ty = clampFrac(z.y + ny * step);

    if (!isBlocked(def.matrix, tx, ty)) { z.x = tx; z.y = ty; }
    else if (!isBlocked(def.matrix, tx, z.y)) { z.x = tx; }
    else if (!isBlocked(def.matrix, z.x, ty)) { z.y = ty; }
    else {
        // Desatasco: empujón perpendicular para no quedarse pegado a una pared.
        const ux = clampFrac(z.x - ny * step);
        const uy = clampFrac(z.y + nx * step);
        if (!isBlocked(def.matrix, ux, uy)) { z.x = ux; z.y = uy; }
    }
    z.facingRight = Math.abs(nx) > 0.01 ? nx >= 0 : z.facingRight;
}

function buildStatePayload(roomId, st) {
    return JSON.stringify({
        type: 'ZOMBIE_STATE',
        roomId,
        totalZombies: st.total,
        zombies: st.zombies.map(z => ({
            id: z.id, x: z.x, y: z.y, health: z.health, maxHealth: z.maxHealth,
            facingRight: z.facingRight, frameIndex: z.frameIndex,
            isDying: z.isDying, isLootCarrier: z.isLootCarrier
        })),
        items: st.items.map(i => ({ id: i.id, x: i.x, y: i.y, effect: i.effect }))
    });
}

function broadcastRoomState(roomId) {
    const st = roomState.get(roomId);
    if (!st) return;
    broadcastToRoom(roomId, null, buildStatePayload(roomId, st));
}

function zombieTick() {
    const now = Date.now();
    for (const roomId of Array.from(roomState.keys())) {
        const def = ROOMS[roomId];
        const present = playersInRoom(roomId);
        if (!def || present.length === 0) { roomState.delete(roomId); continue; }

        const st = roomState.get(roomId);

        // 1. Mover / animar zombis vivos hacia el jugador más cercano.
        for (const z of st.zombies) {
            if (z.isDying) continue;
            const target = nearestPlayer(z, present);
            if (target) stepZombie(z, target, def);
            z.frameIndex = (z.frameIndex + 1) % 9;
        }

        // 2. Remover los que terminaron su animación de muerte + soltar item.
        const remaining = [];
        for (const z of st.zombies) {
            if (z.isDying && now - z.dyingSince > DYING_MS) {
                if (z.isLootCarrier || Math.random() < SKILL_DROP_CHANCE) {
                    st.items.push({ id: uuidv4(), x: z.x, y: z.y, effect: randomEffect() });
                }
            } else {
                remaining.push(z);
            }
        }
        st.zombies = remaining;

        // 3. Victoria: edificio despejado (para TODOS los de la sala).
        const alive = st.zombies.filter(z => !z.isDying).length;
        if (st.total > 0 && alive === 0 && !st.cleared) {
            st.cleared = true;
            broadcastToRoom(roomId, null, JSON.stringify({ type: 'ROOM_CLEARED', roomId }));
        }

        // 4. Difundir estado autoritativo.
        broadcastRoomState(roomId);
    }
}
const zombieSimInterval = setInterval(zombieTick, TICK_MS);

// ─── HEARTBEAT ────────────────────────────────────────────────────────────────
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

// ─── GC de jugadores fantasma ─────────────────────────────────────────────────
const playerGcInterval = setInterval(() => {
    const now = Date.now();
    for (const [id, p] of players.entries()) {
        if (now - (p.lastUpdated || now) > 15000) {
            players.delete(id);
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
    ws.roomId = null;

    console.log(`[+Z] Cliente conectado. ID: ${ws.sessionId}`);
    ws.send(JSON.stringify({ type: 'SESSION_INIT', sessionId: ws.sessionId }));

    ws.on('pong', () => { ws.isAlive = true; });

    ws.on('message', (raw) => {
        try {
            const data = JSON.parse(raw);

            switch (data.type) {
                case 'JOIN_ROOM': {
                    const oldRoom = ws.roomId;
                    ws.roomId = typeof data.roomId === 'string' ? data.roomId : LOBBY_ID;

                    if (oldRoom && oldRoom !== ws.roomId) {
                        broadcastToRoom(oldRoom, ws,
                            JSON.stringify({ type: 'PLAYER_LEFT_ROOM', id: ws.sessionId }));
                    }

                    const joinX = safeFrac(data.x, 0.5);
                    const joinY = safeFrac(data.y, 0.5);

                    players.set(ws.sessionId, {
                        id: ws.sessionId,
                        displayName: data.displayName || '',
                        roomId: ws.roomId,
                        zone: zoneOf(ws.roomId),
                        x: joinX,
                        y: joinY,
                        action: 'IDLE',
                        facingRight: true,
                        health: 100,
                        lastUpdated: Date.now()
                    });

                    console.log(`[Z] ${ws.sessionId} entró a ${ws.roomId} (${zoneOf(ws.roomId)})`);

                    sendRoomSnapshot(ws);

                    broadcastToRoom(ws.roomId, ws, JSON.stringify({
                        type: 'PLAYER_UPDATE',
                        id: ws.sessionId,
                        displayName: data.displayName || '',
                        x: joinX,
                        y: joinY,
                        action: 'IDLE',
                        facingRight: true,
                        health: 100
                    }));

                    // Crear/obtener los zombis del edificio y mandar el estado actual.
                    ensureRoomState(ws.roomId);
                    broadcastRoomState(ws.roomId);
                    break;
                }

                case 'PLAYER_UPDATE': {
                    if (!ws.roomId) break;
                    const prev = players.get(ws.sessionId) || {};
                    const px = safeFrac(data.x, (prev.x ?? 0.5));
                    const py = safeFrac(data.y, (prev.y ?? 0.5));
                    // El cliente no es autoridad de su propia vida: además de isFinite,
                    // acotamos a [0,100] (maxHealth) antes de almacenar/difundir, igual que
                    // safeFrac/safeDamage sanean coordenadas y daño.
                    const health = (typeof data.health === 'number' && isFinite(data.health))
                        ? Math.max(0, Math.min(100, data.health)) : (prev.health ?? 100);
                    players.set(ws.sessionId, {
                        ...prev,
                        id: ws.sessionId,
                        displayName: data.displayName ?? prev.displayName ?? '',
                        roomId: ws.roomId,
                        zone: zoneOf(ws.roomId),
                        x: px,
                        y: py,
                        action: data.action || 'IDLE',
                        facingRight: data.facingRight === true,
                        health: health,
                        lastUpdated: Date.now()
                    });
                    // Reenvía las coordenadas YA saneadas (no el data crudo), para
                    // no propagar NaN/Infinity/fuera de rango a los demás clientes.
                    broadcastToRoom(ws.roomId, ws, JSON.stringify({
                        type: 'PLAYER_UPDATE',
                        id: ws.sessionId,
                        displayName: data.displayName ?? prev.displayName ?? '',
                        x: px,
                        y: py,
                        action: data.action || 'IDLE',
                        facingRight: data.facingRight === true,
                        health: health
                    }));
                    break;
                }

                // El cliente PIDE daño a un zombi; el servidor decide vida/muerte.
                case 'ZOMBIE_DAMAGE': {
                    const st = roomState.get(ws.roomId);
                    if (!st) break;
                    const z = st.zombies.find(z => z.id === data.zombieId);
                    if (z && !z.isDying) {
                        z.health -= safeDamage(data.damage);
                        if (z.health <= 0) {
                            z.health = 0; z.isDying = true; z.dyingSince = Date.now();
                        }
                        broadcastRoomState(ws.roomId); // feedback inmediato
                    }
                    break;
                }

                // Recoger un item: se concede al primer solicitante y desaparece para todos.
                case 'ITEM_PICKUP': {
                    const st = roomState.get(ws.roomId);
                    if (!st) break;
                    const idx = st.items.findIndex(i => i.id === data.itemId);
                    if (idx >= 0) {
                        const it = st.items[idx];
                        st.items.splice(idx, 1);
                        ws.send(JSON.stringify({ type: 'ITEM_GRANTED', effect: it.effect }));
                        broadcastRoomState(ws.roomId);
                    }
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
    clearInterval(zombieSimInterval);
});

server.listen(PORT, () => {
    console.log(`Servidor POW ZOMBIE (Fase 1: zombis autoritativos) en el puerto ${PORT}`);
});