// Verificacion de ID tokens de Firebase para las conexiones WebSocket.
//
// MODO SUAVE por defecto: si llega token se verifica; si NO llega (o no hay
// credenciales de firebase-admin) se PERMITE la conexion. Pon la variable de
// entorno AUTH_REQUIRED=true (y configura las credenciales) para RECHAZAR las
// conexiones sin un token valido. Asi no se caen los servidores actuales ni los
// clientes viejos mientras se hace el rollout.
//
// Credenciales (elige UNA):
//   - FIREBASE_SERVICE_ACCOUNT = el JSON del service account, como string.
//   - GOOGLE_APPLICATION_CREDENTIALS = ruta al archivo del service account.

let admin = null;
let authReady = false;
const AUTH_REQUIRED = process.env.AUTH_REQUIRED === 'true';

function initFirebaseAuth() {
    if (authReady) return;
    try {
        admin = require('firebase-admin');
        let credential = null;
        if (process.env.FIREBASE_SERVICE_ACCOUNT) {
            credential = admin.credential.cert(JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT));
        } else if (process.env.GOOGLE_APPLICATION_CREDENTIALS) {
            credential = admin.credential.applicationDefault();
        }
        if (credential) {
            admin.initializeApp({ credential });
            authReady = true;
            console.log('[auth] firebase-admin inicializado. AUTH_REQUIRED=' + AUTH_REQUIRED);
        } else {
            console.warn('[auth] Sin credenciales firebase-admin: verificacion DESHABILITADA (modo suave).');
        }
    } catch (e) {
        console.error('[auth] No se pudo inicializar firebase-admin: ' + e.message);
    }
}

// Saca el token del header Authorization: Bearer <token> o del query ?token=.
function extractToken(req) {
    const hdr = (req.headers['authorization'] || '');
    if (hdr.startsWith('Bearer ')) return hdr.slice(7).trim();
    try {
        const url = new URL(req.url, 'http://localhost');
        return url.searchParams.get('token');
    } catch (e) {
        return null;
    }
}

// Compatible con la opcion verifyClient de ws: (info, cb) => cb(ok, code, message).
// Si verifica con exito, adjunta el uid en info.req.firebaseUid para el handler de conexion.
function verifyClient(info, cb) {
    const token = extractToken(info.req);
    if (!authReady || !token) {
        if (AUTH_REQUIRED && authReady) return cb(false, 401, 'Falta token de autenticacion');
        return cb(true); // modo suave: permitir
    }
    admin.auth().verifyIdToken(token)
        .then((decoded) => { info.req.firebaseUid = decoded.uid; cb(true); })
        .catch((err) => {
            console.warn('[auth] Token invalido: ' + err.message);
            if (AUTH_REQUIRED) return cb(false, 401, 'Token invalido');
            cb(true); // modo suave: permitir aunque no se haya podido verificar
        });
}

module.exports = { initFirebaseAuth, verifyClient, isAuthReady: () => authReady, AUTH_REQUIRED };
