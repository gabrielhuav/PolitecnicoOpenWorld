// MultiplayerZombie/server.js
// Servidor del MINIJUEGO DE ZOMBIS — FASE 1: zombis AUTORITATIVOS en el servidor.
//
// IA v2 (este archivo):
//   - CAMPO DE FLUJO (mapa de Dijkstra) compartido: en vez de correr un A* por
//     cada zombi, calculamos UNA distancia-al-jugador sobre toda la matriz (por
//     celda objetivo) y TODOS los zombis que persiguen a ese jugador la
//     reutilizan siguiendo el gradiente cuesta abajo. Coste: O(nº jugadores)
//     campos por sala (no O(nº zombis) busquedas). Cada campo se cachea ~250 ms.
//   - LINEA DE VISTA (LOS): si el zombi ve al jugador sin paredes en medio, va en
//     linea recta (mas fluido y sin tocar el campo). Solo usa el campo cuando hay
//     un obstaculo de por medio.
//   - SEPARACION (steering): los zombis se empujan suavemente entre si para no
//     apilarse en un mismo punto; resulta en una "horda" mas natural.
//   - FALLBACK: si el zombi esta en una zona desconectada (sin distancia finita
//     en el campo) y sin LOS, deambula para no congelarse.
//
// La estructura del JSON de ZOMBIE_STATE NO cambia: todo lo de IA vive en campos
// internos del zombi / de la sala y nunca se serializa.
//
// Coordenadas en el cable: FRACCIONARIAS [0,1]. El cliente convierte a pixeles.

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

// ─── MATRICES DE COLISION (por defecto, NEUTRAS) ──────────────────────────────
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

const BUILDING_MATRIX = borderOnly(30, 20); // 30 col x 20 fil
const LOBBY_MATRIX = borderOnly(30, 30);    // 30 col x 30 fil

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

const BUILDING_ORDER = [
    "za_auditorio", "za_biblioteca", "za_cafeteria",
    "za_edificio", "za_estacionamiento", "za_palapas", "za_canchas_futbol"
];

// Cada def precalcula cols/rows una sola vez (optimizacion: evita recomputar
// matrix[0].length en cada tick / cada zombi).
function makeRoomDef(id, type, matrix, zombieCount, spawn) {
    return {
        id, type, matrix, zombieCount, spawn,
        cols: matrix[0].length,
        rows: matrix.length
    };
}

const ROOMS = (() => {
    const ov = loadMatrixOverrides();
    const map = {};
    map[LOBBY_ID] = makeRoomDef(LOBBY_ID, 'LOBBY', ov[LOBBY_ID] || LOBBY_MATRIX, 0, { x: 0.50, y: 0.86 });
    BUILDING_ORDER.forEach((id, i) => {
        map[id] = makeRoomDef(id, 'BUILDING', ov[id] || BUILDING_MATRIX, 4 + (i % 3), { x: 0.50, y: 0.55 });
    });
    return map;
})();

// ─── CONSTANTES DE SIMULACION ─────────────────────────────────────────────────
const TICK_MS = 66;
const ZOMBIE_SPEED_FRAC = 0.006;
const CONTACT_FRAC = 0.03;
const DYING_MS = 1000;
const SKILL_DROP_CHANCE = 0.45;
const SPAWN_MIN = 0.12, SPAWN_MAX = 0.30;
const MARGIN = 0.03;

// ─── CONSTANTES DE IA ─────────────────────────────────────────────────────────
const FIELD_TTL_MS = 250;        // un campo de flujo se reutiliza hasta 250 ms
const FIELD_PRUNE_MS = 1000;     // descartar campos sin uso despues de 1 s
const WANDER_RESET_MS = 1200;    // cada cuanto el fallback re-elige rumbo
const SEPARATION_FRAC = CONTACT_FRAC * 1.15; // radio de separacion entre zombis
const SEPARATION_PUSH = ZOMBIE_SPEED_FRAC * 0.5;

const EFFECTS = [
    "CURA_TOTAL", "RELOJ_ARENA", "ADRENALINA_ZOMBI",
    "FURIA_ZOMBI", "DEBILIDAD_ZOMBI", "FUERZA_BRUTA"
];

// ─── ESTADO ───────────────────────────────────────────────────────────────────
const players = new Map();
const roomState = new Map();  // roomId -> { zombies, items, cleared, total, fields:Map }

function zoneOf(roomId) { return roomId === LOBBY_ID ? 'LOBBY' : 'INTERIOR'; }
function clampFrac(v) { return Math.max(MARGIN, Math.min(1 - MARGIN, v)); }

function safeFrac(v, fallback) {
    return (typeof v === 'number' && isFinite(v)) ? clampFrac(v) : fallback;
}
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

// ═══════════════════════════════════════════════════════════════════════════════
// ─── NUCLEO DE IA: GRID, CAMPO DE FLUJO (DIJKSTRA), LOS, GRADIENTE ──────────────
// ═══════════════════════════════════════════════════════════════════════════════

// Min-heap binario reutilizado por Dijkstra. Nodos {id, f}.
class MinHeap {
    constructor() { this.a = []; }
    get size() { return this.a.length; }
    push(node) {
        const a = this.a; a.push(node);
        let i = a.length - 1;
        while (i > 0) {
            const p = (i - 1) >> 1;
            if (a[p].f <= a[i].f) break;
            const t = a[p]; a[p] = a[i]; a[i] = t; i = p;
        }
    }
    pop() {
        const a = this.a;
        const top = a[0];
        const last = a.pop();
        if (a.length > 0) {
            a[0] = last;
            let i = 0; const n = a.length;
            for (;;) {
                const l = 2 * i + 1, r = 2 * i + 2;
                let s = i;
                if (l < n && a[l].f < a[s].f) s = l;
                if (r < n && a[r].f < a[s].f) s = r;
                if (s === i) break;
                const t = a[s]; a[s] = a[i]; a[i] = t; i = s;
            }
        }
        return top;
    }
}

function cellOf(def, fx, fy) {
    let c = Math.floor(fx * def.cols);
    let r = Math.floor(fy * def.rows);
    if (c < 0) c = 0; if (c >= def.cols) c = def.cols - 1;
    if (r < 0) r = 0; if (r >= def.rows) r = def.rows - 1;
    return { c, r };
}

function cellCenterFrac(c, r, cols, rows) {
    return { x: (c + 0.5) / cols, y: (r + 0.5) / rows };
}

function isCellBlocked(matrix, c, r) {
    if (r < 0 || r >= matrix.length) return true;
    const row = matrix[r];
    if (c < 0 || c >= row.length) return true;
    return row[c] === '#';
}

// Celda caminable mas cercana (BFS 4-conn) si la dada esta en una pared.
function nearestWalkable(def, cell) {
    const matrix = def.matrix;
    if (!isCellBlocked(matrix, cell.c, cell.r)) return cell;
    const cols = def.cols, rows = def.rows;
    const visited = new Set([cell.r * cols + cell.c]);
    const q = [cell];
    let head = 0;
    while (head < q.length) {
        const cur = q[head++];
        if (!isCellBlocked(matrix, cur.c, cur.r)) return cur;
        const adj = [[1, 0], [-1, 0], [0, 1], [0, -1]];
        for (const [dc, dr] of adj) {
            const nc = cur.c + dc, nr = cur.r + dr;
            if (nc < 0 || nr < 0 || nc >= cols || nr >= rows) continue;
            const k = nr * cols + nc;
            if (visited.has(k)) continue;
            visited.add(k); q.push({ c: nc, r: nr });
        }
    }
    return null;
}

const FIELD_NEIGH = [
    [1, 0, 1], [-1, 0, 1], [0, 1, 1], [0, -1, 1],
    [1, 1, Math.SQRT2], [1, -1, Math.SQRT2],
    [-1, 1, Math.SQRT2], [-1, -1, Math.SQRT2]
];

// Mapa de Dijkstra: distancia (Float64) desde la celda objetivo a cada celda
// caminable. Infinity = inalcanzable. Se calcula UNA vez y lo comparten todos
// los zombis que persiguen a ese jugador.
function buildFlowField(def, goalCell) {
    const matrix = def.matrix;
    const cols = def.cols, rows = def.rows;
    const g = nearestWalkable(def, goalCell);
    if (!g) return null;

    const dist = new Float64Array(cols * rows).fill(Infinity);
    const gi = g.r * cols + g.c;
    dist[gi] = 0;

    const heap = new MinHeap();
    heap.push({ id: gi, f: 0 });

    while (heap.size > 0) {
        const cur = heap.pop();
        if (cur.f > dist[cur.id]) continue; // entrada obsoleta (lazy)
        const cc = cur.id % cols;
        const cr = (cur.id / cols) | 0;
        const base = dist[cur.id];
        for (const [dc, dr, cost] of FIELD_NEIGH) {
            const nc = cc + dc, nr = cr + dr;
            if (isCellBlocked(matrix, nc, nr)) continue;
            if (dc !== 0 && dr !== 0) {
                if (isCellBlocked(matrix, cc + dc, cr) || isCellBlocked(matrix, cc, cr + dr)) continue;
            }
            const nId = nr * cols + nc;
            const nd = base + cost;
            if (nd < dist[nId]) { dist[nId] = nd; heap.push({ id: nId, f: nd }); }
        }
    }
    return dist;
}

// Cache + throttle de campos por sala (clave = celda objetivo).
function getField(st, def, goalCell, now) {
    const key = goalCell.r * def.cols + goalCell.c;
    const cached = st.fields.get(key);
    if (cached && now - cached.ms < FIELD_TTL_MS) { cached.usedMs = now; return cached.dist; }
    const dist = buildFlowField(def, goalCell);
    st.fields.set(key, { dist, ms: now, usedMs: now });
    return dist;
}

// Siguiente celda cuesta abajo del campo (8-conn, sin corte de esquina). Devuelve
// el centro de esa celda, o null si no hay mejora (zombi ya en el objetivo o en
// zona inalcanzable -> el caller decide LOS/fallback).
function gradientTarget(field, def, zCell, targetX, targetY) {
    const matrix = def.matrix;
    const cols = def.cols, rows = def.rows;
    const here = zCell.r * cols + zCell.c;
    const hereDist = field[here];
    if (!isFinite(hereDist)) return null;

    let best = hereDist;
    let bestC = -1, bestR = -1, bestTie = Infinity;
    for (const [dc, dr] of FIELD_NEIGH) {
        const nc = zCell.c + dc, nr = zCell.r + dr;
        if (isCellBlocked(matrix, nc, nr)) continue;
        if (dc !== 0 && dr !== 0) {
            if (isCellBlocked(matrix, zCell.c + dc, zCell.r) || isCellBlocked(matrix, zCell.c, zCell.r + dr)) continue;
        }
        const d = field[nr * cols + nc];
        if (d > best) continue;
        // Desempate: ante igual distancia, preferimos la celda mas alineada
        // hacia el jugador (centro de celda mas cercano), evitando zigzag.
        const ctr = cellCenterFrac(nc, nr, cols, rows);
        const tie = Math.hypot(ctr.x - targetX, ctr.y - targetY);
        if (d < best || tie < bestTie) {
            best = d; bestC = nc; bestR = nr; bestTie = tie;
        }
    }
    if (bestC < 0) return null;
    return cellCenterFrac(bestC, bestR, cols, rows);
}

// Hay vista directa entre dos puntos fraccionarios (sin paredes en medio)?
// Muestreo simple sobre la rejilla; suficiente para matrices <= 30x30.
function hasLineOfSight(def, ax, ay, bx, by) {
    const cols = def.cols, rows = def.rows;
    const x0 = ax * cols, y0 = ay * rows;
    const x1 = bx * cols, y1 = by * rows;
    const steps = Math.max(1, Math.ceil(Math.hypot(x1 - x0, y1 - y0) * 2));
    for (let i = 0; i <= steps; i++) {
        const t = i / steps;
        const cx = Math.floor(x0 + (x1 - x0) * t);
        const cy = Math.floor(y0 + (y1 - y0) * t);
        if (isCellBlocked(def.matrix, cx, cy)) return false;
    }
    return true;
}

// ─── MOVIMIENTO ───────────────────────────────────────────────────────────────

// Avance continuo hacia (targetX,targetY) con deslizamiento por eje. true si se movio.
function moveToward(z, def, targetX, targetY, distToPlayer) {
    const dx = targetX - z.x, dy = targetY - z.y;
    const d = Math.hypot(dx, dy);
    if (d < 1e-6) return false;

    const step = (distToPlayer > CONTACT_FRAC * 0.7) ? ZOMBIE_SPEED_FRAC : 0;
    if (step === 0) {
        z.facingRight = Math.abs(dx) > 0.01 ? dx >= 0 : z.facingRight;
        return false;
    }
    const nx = dx / d, ny = dy / d;
    const tx = clampFrac(z.x + nx * step);
    const ty = clampFrac(z.y + ny * step);

    let moved = false;
    if (!isBlocked(def.matrix, tx, ty)) { z.x = tx; z.y = ty; moved = true; }
    else if (!isBlocked(def.matrix, tx, z.y)) { z.x = tx; moved = true; }
    else if (!isBlocked(def.matrix, z.x, ty)) { z.y = ty; moved = true; }

    z.facingRight = Math.abs(nx) > 0.01 ? nx >= 0 : z.facingRight;
    return moved;
}

function fallbackWander(z, def, now) {
    if (!z.wanderSetMs || now - z.wanderSetMs > WANDER_RESET_MS) {
        z.wanderAngle = Math.random() * 2 * Math.PI;
        z.wanderSetMs = now;
    }
    const wx = clampFrac(z.x + Math.cos(z.wanderAngle) * ZOMBIE_SPEED_FRAC);
    const wy = clampFrac(z.y + Math.sin(z.wanderAngle) * ZOMBIE_SPEED_FRAC);
    if (!isBlocked(def.matrix, wx, wy)) {
        z.x = wx; z.y = wy;
        z.facingRight = Math.cos(z.wanderAngle) >= 0;
    } else {
        z.wanderSetMs = 0; // rumbo a pared -> renovar al proximo tick
    }
}

// Empujon de separacion para que los zombis no se apilen en el mismo pixel.
function separationNudge(z, st, def) {
    let sx = 0, sy = 0, count = 0;
    for (const o of st.zombies) {
        if (o === z || o.isDying) continue;
        const dx = z.x - o.x, dy = z.y - o.y;
        const d = Math.hypot(dx, dy);
        if (d > 1e-5 && d < SEPARATION_FRAC) {
            const w = (SEPARATION_FRAC - d) / SEPARATION_FRAC;
            sx += (dx / d) * w; sy += (dy / d) * w; count++;
        }
    }
    if (count === 0) return;
    const mag = Math.hypot(sx, sy);
    if (mag < 1e-6) return;
    const push = Math.min(SEPARATION_PUSH, mag * SEPARATION_PUSH);
    const nx = clampFrac(z.x + (sx / mag) * push);
    const ny = clampFrac(z.y + (sy / mag) * push);
    if (!isBlocked(def.matrix, nx, ny)) { z.x = nx; z.y = ny; }
    else if (!isBlocked(def.matrix, nx, z.y)) { z.x = nx; }
    else if (!isBlocked(def.matrix, z.x, ny)) { z.y = ny; }
}

// Paso de un zombi: LOS directo -> si no, gradiente del campo -> si no, deambular.
function stepZombie(z, target, def, st, now) {
    const distToPlayer = Math.hypot(target.x - z.x, target.y - z.y);

    let moved = false;
    if (hasLineOfSight(def, z.x, z.y, target.x, target.y)) {
        moved = moveToward(z, def, target.x, target.y, distToPlayer);
    } else {
        const goalCell = cellOf(def, target.x, target.y);
        const field = getField(st, def, goalCell, now);
        if (field) {
            const zCell = cellOf(def, z.x, z.y);
            const g = gradientTarget(field, def, zCell, target.x, target.y);
            if (g) moved = moveToward(z, def, g.x, g.y, distToPlayer);
        }
    }

    if (!moved && distToPlayer > CONTACT_FRAC * 0.7) {
        fallbackWander(z, def, now);
    }

    // Separacion solo mientras no esten ya pegados al jugador.
    if (distToPlayer > CONTACT_FRAC) separationNudge(z, st, def);
}

// ═══════════════════════════════════════════════════════════════════════════════

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

app.get('/status', (req, res) => {
    const porSala = {};
    for (const p of players.values()) porSala[p.roomId] = (porSala[p.roomId] || 0) + 1;
    const zombisPorSala = {};
    for (const [rid, st] of roomState.entries()) zombisPorSala[rid] = st.zombies.length;
    res.json({
        estado: 'Online (Zombie F1 - IA v2)',
        modo: 'ZOMBIE',
        jugadoresConectados: players.size,
        salas: porSala,
        zombisActivos: zombisPorSala,
        timestamp: new Date().toISOString()
    });
});

// ─── ZOMBIS: SPAWN / DIFUSION ─────────────────────────────────────────────────
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
            isDying: false, dyingSince: 0, isLootCarrier: (i === lootIndex),
            // Campos de IA (no se serializan):
            wanderAngle: 0, wanderSetMs: 0
        });
    }
    return { zombies, items: [], cleared: false, total: def.zombieCount, fields: new Map() };
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

        // Purga de campos de flujo sin uso (evita que el cache crezca al moverse
        // el jugador por muchas celdas).
        for (const [k, f] of st.fields) {
            if (now - f.usedMs > FIELD_PRUNE_MS) st.fields.delete(k);
        }

        // 1. Mover/animar zombis vivos con la IA v2 (LOS -> campo -> fallback).
        for (const z of st.zombies) {
            if (z.isDying) continue;
            const target = nearestPlayer(z, present);
            if (target) stepZombie(z, target, def, st, now);
            z.frameIndex = (z.frameIndex + 1) % 9;
        }

        // 2. Remover los que terminaron su animacion de muerte + soltar item.
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

        // 3. Victoria: edificio despejado.
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
                        x: joinX, y: joinY,
                        action: 'IDLE', facingRight: true,
                        health: 100, lastUpdated: Date.now()
                    });

                    console.log(`[Z] ${ws.sessionId} entro a ${ws.roomId} (${zoneOf(ws.roomId)})`);

                    sendRoomSnapshot(ws);

                    broadcastToRoom(ws.roomId, ws, JSON.stringify({
                        type: 'PLAYER_UPDATE',
                        id: ws.sessionId,
                        displayName: data.displayName || '',
                        x: joinX, y: joinY,
                        action: 'IDLE', facingRight: true, health: 100
                    }));

                    ensureRoomState(ws.roomId);
                    broadcastRoomState(ws.roomId);
                    break;
                }

                case 'PLAYER_UPDATE': {
                    if (!ws.roomId) break;
                    const prev = players.get(ws.sessionId) || {};
                    const px = safeFrac(data.x, (prev.x ?? 0.5));
                    const py = safeFrac(data.y, (prev.y ?? 0.5));
                    const health = (typeof data.health === 'number' && isFinite(data.health))
                        ? Math.max(0, Math.min(100, data.health)) : (prev.health ?? 100);
                    players.set(ws.sessionId, {
                        ...prev,
                        id: ws.sessionId,
                        displayName: data.displayName ?? prev.displayName ?? '',
                        roomId: ws.roomId,
                        zone: zoneOf(ws.roomId),
                        x: px, y: py,
                        action: data.action || 'IDLE',
                        facingRight: data.facingRight === true,
                        health: health,
                        lastUpdated: Date.now()
                    });
                    broadcastToRoom(ws.roomId, ws, JSON.stringify({
                        type: 'PLAYER_UPDATE',
                        id: ws.sessionId,
                        displayName: data.displayName ?? prev.displayName ?? '',
                        x: px, y: py,
                        action: data.action || 'IDLE',
                        facingRight: data.facingRight === true,
                        health: health
                    }));
                    break;
                }

                case 'ZOMBIE_DAMAGE': {
                    const st = roomState.get(ws.roomId);
                    if (!st) break;
                    const z = st.zombies.find(z => z.id === data.zombieId);
                    if (z && !z.isDying) {
                        z.health -= safeDamage(data.damage);
                        if (z.health <= 0) {
                            z.health = 0; z.isDying = true; z.dyingSince = Date.now();
                        }
                        broadcastRoomState(ws.roomId);
                    }
                    break;
                }

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
    console.log(`Servidor POW ZOMBIE (Fase 1 - IA v2: campo de flujo + LOS + separacion) en el puerto ${PORT}`);
});
