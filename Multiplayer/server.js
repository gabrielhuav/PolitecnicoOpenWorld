const express = require('express');
const http = require('http');
const WebSocket = require('ws');
const cors = require('cors');
const { v4: uuidv4 } = require('uuid'); // Para generar IDs únicos si el cliente no lo envía

const app = express();
app.use(cors());

const PORT = process.env.PORT || 8080;
const server = http.createServer(app);

// Estado en memoria de todos los jugadores conectados
// Estructura esperada: { "idJugador": { x: 0, y: 0, rotation: 0, action: "IDLE" ... } }
const players = new Map();

// Endpoint de prueba y monitoreo
app.get('/status', (req, res) => {
    res.json({
        estado: 'Online',
        jugadoresConectados: players.size,
        jugadores: Array.from(players.values()), // Muestra la data de los jugadores
        timestamp: new Date().toISOString()
    });
});

const wss = new WebSocket.Server({ server });

wss.on('connection', (ws, req) => {
    // Generamos un ID de sesión temporal por si acaso, aunque el cliente debe enviar el suyo
    ws.sessionId = uuidv4();
    console.log(`[+] Cliente conectado. ID Sesión: ${ws.sessionId} | Total clientes TCP: ${wss.clients.size}`);

    ws.on('message', (messageAsString) => {
        try {
            // Intentamos interpretar el mensaje como JSON
            const data = JSON.parse(messageAsString);

            // Verificamos si es un mensaje de actualización de posición válido
            if (data && data.id) {
                // Guardamos el ID del jugador asociado a este WebSocket para saber quién es cuando se desconecte
                ws.playerId = data.id;

                // Actualizamos el estado del jugador en la memoria del servidor
                players.set(data.id, data);

                // Reenviamos el JSON exactamente como llegó a todos los DEMÁS clientes
                wss.clients.forEach((client) => {
                    if (client !== ws && client.readyState === WebSocket.OPEN) {
                        client.send(messageAsString.toString());
                    }
                });
            } else {
                 console.log("Mensaje recibido sin ID de jugador válido:", data);
            }

        } catch (e) {
            console.error("Error al procesar mensaje (no es JSON válido):", messageAsString.toString(), e);
        }
    });

    ws.on('close', () => {
        console.log(`[-] Cliente desconectado. ID Sesión: ${ws.sessionId}`);
        
        // Si sabíamos qué jugador era este WebSocket, lo eliminamos y avisamos a los demás
        if (ws.playerId) {
            console.log(`El jugador ${ws.playerId} ha abandonado el juego.`);
            players.delete(ws.playerId);

            // Opcional: Podrías enviar un mensaje JSON especial indicando que este ID se desconectó
            // para que los clientes en Android borren el NPC.
            const disconnectMessage = JSON.stringify({
                 type: "DISCONNECT",
                 id: ws.playerId
            });

             wss.clients.forEach((client) => {
                if (client.readyState === WebSocket.OPEN) {
                    client.send(disconnectMessage);
                }
            });
        }
        console.log(`Total clientes TCP restantes: ${wss.clients.size}`);
    });
});

server.listen(PORT, () => {
    console.log(`=========================================`);
    console.log(`🚀 Servidor POW escuchando en el puerto ${PORT}`);
    console.log(`🔍 Monitoreo disponible en: http://localhost:${PORT}/status`);
    console.log(`=========================================`);
});