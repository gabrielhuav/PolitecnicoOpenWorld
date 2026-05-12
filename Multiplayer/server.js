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

// Endpoint HTTP para obtener estado del servidor (útil para monitoreo)
app.get('/status', (req, res) => {
    res.json({
        estado: 'Online',
        jugadoresConectados: players.size,
        npcsActivos: npcs.size,
        jugadores: Array.from(players.values()), // lista completa de jugadores
        timestamp: new Date().toISOString()
    });
});

const wss = new WebSocket.Server({ server });

// Envía un mensaje a todos los clientes excepto al emisor
function broadcastToOthers(senderWs, messageAsString) {
    wss.clients.forEach((client) => {
        if (client !== senderWs && client.readyState === WebSocket.OPEN) {
            client.send(messageAsString.toString());
        }
    });
}

// Envía un mensaje a todos los clientes conectados
function broadcastAll(messageAsString) {
    wss.clients.forEach((client) => {
        if (client.readyState === WebSocket.OPEN) {
            client.send(messageAsString.toString());
        }
    });
}

// --- Mecanismo de latidos (heartbeat) para detectar clientes caídos ---
// Cada 30 segundos se envía un ping a cada cliente.
// Si falla el pong o se superan 6 fallos (3 minutos), se termina la conexión.
setInterval(() => {
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
// Cada 5 segundos se revisan los NPCs. Si un NPC lleva más de 15 segundos
// sin actualizarse (lastUpdated), se elimina y se notifica a todos.
setInterval(() => {
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
// Cada 5 segundos se envía a todos los clientes la lista de IDs de NPCs activos.
// Esto ayuda a los clientes a reconciliar su estado local con el servidor.
setInterval(() => {
    if (wss.clients.size > 0) {
        broadcastAll(JSON.stringify({
            type: "MASTER_SYNC_CHECK",
            activeNpcIds: Array.from(npcs.keys())
        }));
    }
}, 5000);

// --- Manejo de nuevas conexiones WebSocket ---
wss.on('connection', (ws) => {
    // Asignar ID único de sesión
    ws.sessionId = uuidv4();
    ws.isAlive = true;
    ws.missedPings = 0;
    ws.isHost = true;    // Por defecto, un nuevo cliente es Host en su zona

    console.log(`[+] Cliente conectado. ID: ${ws.sessionId}`);
    ws.send(JSON.stringify({ type: 'SESSION_INIT', sessionId: ws.sessionId }));

    // Responder al ping del servidor para mantener la conexión activa
    ws.on('pong', () => { ws.isAlive = true; });

    // Enviar al cliente recién conectado la lista de todos los NPCs existentes
    const existingNpcs = Array.from(npcs.values());
    if (existingNpcs.length > 0) {
        ws.send(JSON.stringify({ type: "SYNC_ALL_NPCS", npcs: existingNpcs }));
    }

    // Procesar mensajes entrantes del cliente
    ws.on('message', (messageAsString) => {
        try {
            const data = JSON.parse(messageAsString);

            // --- Actualización del jugador (PLAYER_UPDATE) o movimiento genérico ---
            if (data && (!data.type || data.type === "PLAYER_UPDATE")) {
                let isNowHost = ws.isHost;

                // Lógica para determinar si este cliente debe ser el Host (autoridad en la zona)
                if (!ws.isHost) {
                    // Si no es Host, verifica si hay otro Host cerca
                    let nearbyHost = false;
                    for (const other of players.values()) {
                        if (other.isHost && other.id !== ws.sessionId) {
                            const dist = Math.sqrt(Math.pow(other.y - data.y, 2) + Math.pow(other.x - data.x, 2));
                            if (dist < HOST_RADIUS) { nearbyHost = true; break; }
                        }
                    }
                    if (!nearbyHost) isNowHost = true;   // No hay Host cerca -> asume el rol
                } else {
                    // Si ya es Host, comprueba si hay otro Host con menor ID (para resolver conflictos)
                    for (const other of players.values()) {
                        if (other.isHost && other.id !== ws.sessionId) {
                            const dist = Math.sqrt(Math.pow(other.y - data.y, 2) + Math.pow(other.x - data.x, 2));
                            if (dist < HOST_RADIUS) {
                                if (ws.sessionId > other.id) { isNowHost = false; break; }
                            }
                        }
                    }
                }

                // Si cambió el rol de Host, se notifica al cliente
                if (ws.isHost !== isNowHost) {
                    ws.isHost = isNowHost;
                    ws.send(JSON.stringify({ type: "ROLE_UPDATE", isZoneHost: isNowHost }));

                    if (!isNowHost) {
                        console.log(`[Zonas] ${ws.sessionId} cedió su autoridad.`);
                        // Nota: los NPCs que este cliente controlaba no se eliminan; pasan a ser propiedad del servidor temporalmente.
                    } else {
                        console.log(`[Zonas] ${ws.sessionId} retomó autoridad como Host y adoptará a los NPCs.`);
                    }
                }

                // Guardar o actualizar los datos del jugador en el mapa `players`
                players.set(ws.sessionId, {
                    id: ws.sessionId,
                    displayName: typeof data.displayName === 'string' ? data.displayName : '',
                    x: typeof data.x === 'number' ? data.x : 0,
                    y: typeof data.y === 'number' ? data.y : 0,
                    action: typeof data.action === 'string' ? data.action : '',
                    facingRight: typeof data.facingRight === 'boolean' ? data.facingRight : true,
                    isHost: ws.isHost
                });

                // Reenviar la actualización a los demás clientes
                broadcastToOthers(ws, JSON.stringify({ ...data, id: ws.sessionId }));
            }
            // --- Creación o actualización de un NPC individual ---
            else if (data && (data.type === "NPC_SPAWN" || data.type === "NPC_UPDATE")) {
                if (data.npc && data.npc.id) {
                    // Se añade el NPC con marca de tiempo (lastUpdated) y el ID del propietario
                    npcs.set(data.npc.id, { ...data.npc, ownerId: ws.sessionId, lastUpdated: Date.now() });
                    broadcastToOthers(ws, messageAsString);
                }
            }
            // --- Actualización por lotes de múltiples NPCs ---
            else if (data && data.type === "NPC_BATCH_UPDATE") {
                if (data.npcs && Array.isArray(data.npcs)) {
                    data.npcs.forEach(npc => {
                        if (npc.id) npcs.set(npc.id, { ...npc, ownerId: ws.sessionId, lastUpdated: Date.now() });
                    });
                    broadcastToOthers(ws, messageAsString);
                }
            }
            // --- Eliminación de un NPC ---
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

    // Manejo de desconexión del cliente
    ws.on('close', () => {
        console.log(`[-] Cliente desconectado. ID: ${ws.sessionId}`);
        players.delete(ws.sessionId);
        
        broadcastAll(JSON.stringify({ type: "DISCONNECT", id: ws.sessionId }));

        // Los NPCs que pertenecían a este cliente se quedan en el mapa (con su lastUpdated).
        // Si otro jugador se acerca, los adoptará en su próximo ciclo.
        // Si nadie los adopta, el recolector de basura los eliminará tras 15 segundos.
    });
});

// Limpieza del intervalo principal al cerrar el servidor (precaución)
server.on('close', () => clearInterval(interval));

// Iniciar el servidor
server.listen(PORT, () => {
    console.log(`Servidor POW Multi-Zonas Persistente (Adopción) en el puerto ${PORT}`);
});