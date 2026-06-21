# Politécnico Open World (POW) — ALPHA

> 🎮 Juego Android 2D *top-down* de mundo abierto sobre mapas reales (Kotlin + Jetpack Compose).
> **Estado: ALPHA · v1.0.0.6**
>
> 🇪🇸 Español (abajo 🇬🇧 English)

---

## 🇪🇸 Versión en español

**Politécnico Open World** es un juego de mundo abierto 2D ambientado en mapas reales (OpenStreetMap / Google),
con un Modo Historia (campaña), un minijuego de supervivencia zombi en interiores, vehículos, NPCs con IA,
coleccionables y multijugador en línea.

### ✨ Características
- 🗺️ **Mundo abierto** sobre mapas reales con 8 proveedores de mapa intercambiables.
- 📖 **Modo Historia**: campaña con misiones, cómic de intro/outro, escolta de Prankedy y huida.
- 🧟 **Minijuego zombi** en interiores (lobby + edificios) con IA de zombis autoritativa por sala.
- 🚗 Conducción de vehículos, 🎒 coleccionables de lore, 🧍 NPCs con personalidad y tráfico.
- 🌐 **Multijugador** en línea (mundo e interiores) con dos servidores Node.js.
- 🔐 **Inicio de sesión con Google** (Firebase) para el multijugador; el juego local no requiere cuenta.
- ⚙️ Ajustes: control D-pad/joystick, audio, idioma (ES/EN), widgets de interfaz y más.

### 🛠️ Stack
Kotlin · Jetpack Compose · Material 3 · Navigation Compose · Room · OkHttp (WebSocket) · osmdroid +
Google Maps + Leaflet/WebView · Firebase Authentication · Node.js + Express + `ws` (servidores, en Render).

### ▶️ Compilar y correr (contribuidores)
1. Clona el repo y ábrelo en **Android Studio** (versión reciente).
2. **Build → Rebuild Project** y ejecuta en un dispositivo/emulador.

> **No necesitas `google-services.json`.** El plugin de Firebase se aplica **solo si ese archivo existe**, así
> que el proyecto compila y corre sin él. En ese caso el **inicio de sesión con Google** queda deshabilitado,
> pero todo lo demás (un jugador, Modo Historia, mapa, interiores) funciona, e incluso el **multijugador entra
> en modo anónimo** mientras los servidores estén en modo suave. El mantenedor agrega el `google-services.json`
> para habilitar la identidad por cuenta.

### 🌐 Multijugador / Firebase (solo mantenedor)
- Coloca `app/google-services.json` (Firebase) — está en `.gitignore`, **no se sube**.
- Habilita el proveedor **Google** en Firebase Authentication y registra las huellas **SHA-1/SHA-256**
  (debug, subida y *Play App Signing*).
- **Servidores (Render):** define la variable `FIREBASE_SERVICE_ACCOUNT` (JSON del service account) en cada
  servicio. `AUTH_REQUIRED=true` (opcional) exige token; sin definirla, modo suave (acepta anónimos).
  Plantillas en `Multiplayer/.env.example` y `MultiplayerInteriores/.env.example`.

### 🔒 Privacidad y cuenta
- Política de privacidad: **`policy_en_es.html`** (publicada en GitHub Pages).
- El correo se recopila **indirectamente vía Google Sign-In**, solo al usar funciones en línea; opcional y no
  compartido con terceros.
- **Eliminar cuenta y datos:** dentro de la app, en **Ajustes → Cuenta → "Eliminar mi cuenta y datos"**.

### 🤝 Contribuir
- Haz tu PR desde una rama; **no necesitas secretos** para compilar.
- No subas `google-services.json`, `*.jks`, el service account ni `secrets.properties` (ya están en `.gitignore`).
- Sigue el estilo del repo (comentarios y textos de UI en español, MVVM por *feature*).

### 📄 Licencia
Ver [`LICENSE`](LICENSE).

---

## 🇬🇧 English version

**Politécnico Open World** is a 2D open-world game set on real maps (OpenStreetMap / Google), featuring a Story
campaign, an indoor zombie-survival minigame, vehicles, AI NPCs, collectibles and online multiplayer.

### ✨ Features
- 🗺️ **Open world** on real maps with 8 swappable map providers.
- 📖 **Story Mode** campaign with missions, intro/outro comic, escort & escape.
- 🧟 Indoor **zombie minigame** (lobby + buildings) with per-room authoritative zombie AI.
- 🚗 Vehicle driving, 🎒 lore collectibles, 🧍 AI NPCs with personality and traffic.
- 🌐 Online **multiplayer** (world & interiors) via two Node.js servers.
- 🔐 **Google Sign-In** (Firebase) for multiplayer; local play needs no account.
- ⚙️ Settings: D-pad/joystick controls, audio, language (ES/EN), HUD widgets and more.

### ▶️ Build & run (contributors)
1. Clone and open in **Android Studio**.
2. **Build → Rebuild Project** and run.

> **No `google-services.json` required.** The Firebase plugin is applied **only if that file exists**, so the
> project builds and runs without it. Google sign-in is then disabled, but everything else works — and
> multiplayer even connects **anonymously** while the servers run in soft mode. The maintainer adds
> `google-services.json` to enable account identity.

### 🌐 Multiplayer / Firebase (maintainer only)
- Put `app/google-services.json` (git-ignored, never committed).
- Enable the **Google** provider in Firebase Auth and register **SHA-1/SHA-256** (debug, upload, Play App Signing).
- **Servers (Render):** set `FIREBASE_SERVICE_ACCOUNT` (service-account JSON) on each service. `AUTH_REQUIRED=true`
  enforces tokens; unset = soft mode (anonymous allowed). See `*/.env.example`.

### 🔒 Privacy & account
- Privacy policy: **`policy_en_es.html`**. Email is collected **indirectly via Google Sign-In**, only for online
  features; optional and not shared. **Delete account & data** in-app: **Settings → Account**.

### 🤝 Contributing
Open a PR from a branch — **no secrets needed to build**. Don't commit `google-services.json`, `*.jks`, the
service account or `secrets.properties` (already git-ignored).

### 📄 License
See [`LICENSE`](LICENSE).
