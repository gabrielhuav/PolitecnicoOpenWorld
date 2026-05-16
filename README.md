# Politécnico Open World (POW)

> 🇬🇧 **English version below** · 🇪🇸 [Saltar a la versión en español](#-versión-en-español)

---

## 🇬🇧 English Version

**Politécnico Open World (POW)** is an Android 2D top-down exploration app built on top of real-world maps. The player navigates the actual streets of their surroundings (with initial focus on the ESCOM / Zacatenco area in Mexico City) using **OpenStreetMap** cartographic data, sharing the world with procedural NPCs (pedestrians and vehicles) and with other players connected to a real-time server.

The project is built entirely with **Kotlin + Jetpack Compose**, follows an **MVVM** pattern organized by *features*, and delegates persistent-world logic to a standalone Node.js server.

### ⚙️ Architecture

The repository contains two complementary projects:

```text
.
├── PolitecnicoOpenWorld/   # Android client (Kotlin + Compose)
└── Multiplayer/            # Game server (Node.js + WebSocket, dockerized)
```

#### Android client — feature-based organization

```text
app/src/main/java/ovh/gabrielhuav/pow/
│
├── data/
│   ├── cache/              # RoadNetworkCache, TileCache (LRU over Room)
│   ├── local/room/         # PowDatabase, DAOs and entities (zones, ways, nodes, tiles)
│   ├── network/            # WebSocketManager (OkHttp)
│   └── repository/         # OverpassRepository, SettingsRepository
│
├── domain/
│   └── models/             # Npc, MapWay, MapNode, CharacterVisualConfig, CarModel
│       └── ai/             # NpcAiManager (spawn, despawn, movement over the road network)
│
├── features/
│   ├── main_menu/          # Main menu and multiplayer connection dialog
│   ├── map_exterior/       # Game core: map, NPCs, player, controls, HUD
│   │   ├── ui/             # WorldMapScreen, CachingWebViewClient, sprites, controllers
│   │   └── viewmodel/      # WorldMapViewModel, WorldMapState
│   └── settings/           # Settings screen (map, controls, interface)
│
├── ui/theme/               # Material 3 theme
└── MainActivity.kt         # Single-Activity with Compose NavHost
```

The client follows a **Single-Activity** architecture with Compose-based navigation (`NavHost`) and three destinations: `main_menu`, `world_map`, and `settings`.

### 🗺️ Map System

POW supports **eight map providers** that can be hot-swapped from the Settings screen:

| Provider | Mode | Notes |
|---|---|---|
| OSMDroid (Native) | Native render | Only mode supporting zoom > 19 (up to 21) |
| OpenStreetMap (Web) | WebView + Leaflet | |
| Google Maps (Web) | WebView + Leaflet | |
| CartoDB Dark / Light | WebView + Leaflet | Video-game aesthetic |
| Esri World Street | WebView + Leaflet | |
| Esri Satellite | WebView + Leaflet | Real aerial view |
| OpenTopoMap | WebView + Leaflet | Terrain and contour lines |

Web modes render through **Leaflet** inside a `WebView` and are intercepted by a `CachingWebViewClient` that caches each tile in Room (`MapTileEntity`) using the normalized URL as key (stripping load-balancing subdomains and volatile parameters before hashing with SHA-256). This allows offline play in any previously visited area.

#### Road network cache

The road network needed to anchor movement and NPCs is fetched from the **Overpass API** and persisted in Room with the following strategy:

- **Cell-based granularity:** the world surface is divided into ~2 km × 2 km cells. Each downloaded cell covers a ~2 km radius around the player.
- **7-day TTL** before marking a cell as expired.
- **LRU of 20 cells** maximum: when full, the oldest cell is dropped.
- **Atomic transaction** (`@Transaction`) when inserting zone + ways + nodes, preventing corrupt states if the process dies mid-write.
- **Re-fetch cooldown:** once a zone is downloaded, Overpass won't be queried again for 5 minutes even if the player keeps crossing it.

The player cannot step off the road network: every movement is validated against a **spatial grid index** (`Seg` + `HashMap<cell, segments>`) that runs *snap-to-road* in O(nearby candidates) instead of O(n) over all streets.

### 🚶 NPCs and Vehicles

`NpcAiManager` maintains a population of up to **40 NPCs** around the player, split into two types:

- **Pedestrians:** walk on pedestrian ways (`footway`, `pedestrian`, `path`, `residential`...). Each one is assembled at runtime by combining a base body, a hair sprite (`hair_1`...`hair_4`), and three random colors (hair, shirt, pants) using a **smart per-pixel tinting** technique that preserves skin tones (saturation filter) and separates shirt from pants by luminance.
- **Vehicles:** 6 models (`SEDAN`, `SPORT`, `SUPERCAR`, `SUV`, `VAN`, `WAGON`), each with 48 rotation frames (one every 7.5°). Color is also applied per-pixel, preserving headlights, turn signals, and rims via saturation + luminance analysis.

Behavior includes proximity-based spawning, distance-based despawning (>35 m equivalent), node-to-node navigation with smooth transitions between connected ways, and an **adoption** system: if an NPC enters the zone without an assigned street (because it was inherited from the server), it is automatically snapped to the nearest way before being moved.

#### Entering and exiting vehicles

Pressing the **X** button near a vehicle "takes it": the NPC disappears from the list and the player is rendered as that car, with free 360° rotation based on movement angle. Pressing X again leaves the car parked as an NPC at the current position.

### 🌐 Real-Time Multiplayer

The server (`Multiplayer/server.js`) is a **Node.js + Express + ws** process that holds player and NPC state in memory and ships as a Docker image. The public instance used by the Android client is hosted on **[Render](https://render.com/)**, which builds the container straight from the repository and exposes a public WebSocket endpoint that gets injected into the build via `BuildConfig.MULTIPLAYER_SERVER_URL`.

#### Authority model: *Zone Host*

Instead of centralizing all simulation on the server (slow) or leaving it 100% to the client (incoherent), POW distributes authority via the **Zone Host** pattern:

- Every newly connected client is **Host by default** within a ~400 m radius.
- If two hosts enter the same radius, the one with the lower `sessionId` yields authority. The server notifies the change with a `ROLE_UPDATE` message.
- Only the host runs the NPC AI in its zone and publishes `NPC_BATCH_UPDATE` every 100 ms. Other clients receive them passively.
- When a host yields or disconnects, its NPCs are **not destroyed**: they remain on the server with their last position, and the next host **adopts** them, re-attaching them to the local road network.

#### Robustness

- **Heartbeat:** ping every 30 s; after 6 consecutive failures (3 minutes of silence) the connection is terminated.
- **Orphan NPC garbage collector:** NPCs not updated in >15 s are deleted and announced to all clients.
- **Periodic master sync:** every 5 s the server broadcasts the list of live NPC IDs so clients can reconcile local state and clean up ghosts.
- **Colors serialized as ARGB Int** (not as the internal `ULong` of `Compose Color`) to avoid corrupted `ColorSpace` when rehydrating remote NPCs.

The client uses **OkHttp WebSocket** with `readTimeout`/`writeTimeout` set to 0 (no limit) and a `pingInterval` of 25 s to survive mobile networks with aggressive NAT.

### 🎮 Controls and UI

- **Two movement styles:** classic D-Pad or **virtual joystick** with free 360° rotation, switchable from Settings.
- **Adaptive scaling:** controls resize between 60% and 140%, with a dynamic ceiling of 100% in portrait mode to avoid eating the screen.
- **Left-handed mode:** movement and action controls can be swapped sides.
- **Gamepad-style action buttons** (A, B, X, Y): run, special attack, interact (enter/exit vehicles), and a reserved slot.
- **Optional diagnostic HUD:**
  - **Cache widget:** indicates whether streets come from the network, from Room, or are still loading; same for tiles.
  - **FPS widget:** real-time graphics performance meter.
- Preferences (control type, scale, swap) persist in `SharedPreferences` via `SettingsRepository`.

### 🚀 Tech Stack

| Layer | Technology |
|---|---|
| UI | Jetpack Compose, Material 3, Navigation Compose |
| Native map | osmdroid |
| Web map | WebView + Leaflet 1.9.4 |
| Persistence | Room (v4, `fallbackToDestructiveMigration`) |
| Network | OkHttp (WebSocket), HttpURLConnection (Overpass + tiles) |
| Geolocation | Google Play Services — Fused Location Provider |
| Concurrency | Coroutines + Flow / SharedFlow |
| Serialization | Gson |
| Server | Node.js 18, Express, ws, Docker |
| Hosting | [Render](https://render.com/) (auto-deploy from Dockerfile) |

### 🐳 Deploying the multiplayer server

There are two ways to run the server:

**Local (Docker, for development):**

```bash
cd Multiplayer
docker compose up -d
```

**Production:** the canonical instance is deployed on **[Render](https://render.com/)** as a Web Service that auto-builds from the `Multiplayer/Dockerfile` on every push to the main branch. Render handles TLS termination, public DNS, and exposes the WebSocket endpoint over `wss://`.

In either case, the server listens on port **8080** with two endpoints:

- `GET /status` — live status (connected players, active NPCs, timestamp).
- `WS /` — game WebSocket channel.

The server URL is injected into the Android client at compile time via `BuildConfig.MULTIPLAYER_SERVER_URL` (Gradle variable).

### 📍 Current Status

What **works today** in this repository: OSM navigation with snap-to-road, persistent street and tile caching, procedural NPCs (pedestrians and 6 car models), multiplayer with zone-delegated authority, vehicle driving, configurable controls, and 8 map providers.

What is **not yet** implemented (despite appearing in earlier plans): matrix-based interior maps, embedded minigames (Pacman, survival, etc.), local Bluetooth, and A* pathfinding. These are the next block of work.

---
---

## 🇪🇸 Versión en Español

**Politécnico Open World (POW)** es una aplicación Android de exploración 2D con vista *top-down* construida sobre mapas del mundo real. El jugador se desplaza por las calles reales de su ubicación (con foco inicial en la zona ESCOM / Zacatenco) usando datos cartográficos de **OpenStreetMap**, comparte el mundo con NPCs procedurales (peatones y vehículos) y con otros jugadores conectados a un servidor en tiempo real.

El proyecto está construido íntegramente en **Kotlin + Jetpack Compose**, sigue un patrón **MVVM** organizado por *features* y delega la lógica de mundo persistente a un servidor Node.js independiente.

### ⚙️ Arquitectura

El repositorio contiene dos proyectos complementarios:

```text
.
├── PolitecnicoOpenWorld/   # Cliente Android (Kotlin + Compose)
└── Multiplayer/            # Servidor de juego (Node.js + WebSocket, dockerizado)
```

#### Cliente Android — organización por *features*

```text
app/src/main/java/ovh/gabrielhuav/pow/
│
├── data/
│   ├── cache/              # RoadNetworkCache, TileCache (LRU sobre Room)
│   ├── local/room/         # PowDatabase, DAOs y entidades (zonas, ways, nodos, tiles)
│   ├── network/            # WebSocketManager (OkHttp)
│   └── repository/         # OverpassRepository, SettingsRepository
│
├── domain/
│   └── models/             # Npc, MapWay, MapNode, CharacterVisualConfig, CarModel
│       └── ai/             # NpcAiManager (spawn, despawn, movimiento sobre la red de calles)
│
├── features/
│   ├── main_menu/          # Menú principal y diálogo de conexión multijugador
│   ├── map_exterior/       # Núcleo del juego: mapa, NPCs, jugador, controles, HUD
│   │   ├── ui/             # WorldMapScreen, CachingWebViewClient, sprites, controles
│   │   └── viewmodel/      # WorldMapViewModel, WorldMapState
│   └── settings/           # Pantalla de ajustes (mapa, controles, interfaz)
│
├── ui/theme/               # Tema Material 3
└── MainActivity.kt         # Single-Activity con NavHost de Compose
```

El cliente sigue una arquitectura **Single-Activity** con navegación basada en `NavHost` de Compose y tres destinos: `main_menu`, `world_map` y `settings`.

### 🗺️ Sistema de Mapas

POW soporta **ocho proveedores de mapas** intercambiables en caliente desde Ajustes:

| Proveedor | Modo | Notas |
|---|---|---|
| OSMDroid (Nativo) | Render nativo | Único modo con zoom > 19 (hasta 21) |
| OpenStreetMap (Web) | WebView + Leaflet | |
| Google Maps (Web) | WebView + Leaflet | |
| CartoDB Oscuro / Claro | WebView + Leaflet | Estética de videojuego |
| Esri World Street | WebView + Leaflet | |
| Esri Satélite | WebView + Leaflet | Vista aérea real |
| OpenTopoMap | WebView + Leaflet | Relieve y curvas de nivel |

Los modos Web se renderizan con **Leaflet** dentro de un `WebView` y son interceptados por un `CachingWebViewClient` que cachea cada tile en Room (`MapTileEntity`) usando la URL normalizada como clave (eliminando subdominios de balanceo y parámetros volátiles antes de hashear con SHA-256). Esto permite jugar sin conexión cualquier zona previamente visitada.

#### Caché de red de calles

La red de calles necesaria para anclar movimiento y NPCs se obtiene de la **Overpass API** y se persiste en Room con la siguiente estrategia:

- **Granularidad por celda:** la superficie del mundo se divide en celdas de ~2 km × 2 km. Cada celda descargada cubre un radio de ~2 km alrededor del jugador.
- **TTL de 7 días** antes de marcar la celda como expirada.
- **LRU de 20 celdas** máximo: al llenarse, se descarta la más antigua.
- **Transacción atómica** (`@Transaction`) al insertar zona + ways + nodos para evitar estados corruptos si el proceso muere a media escritura.
- **Re-fetch cooldown:** una vez descargada una zona, no se vuelve a pedir a Overpass durante 5 minutos aunque el jugador la siga cruzando.

El jugador no puede salirse de las vías: cada movimiento es validado contra un **índice espacial en grid** (`Seg` + `HashMap<celda, segmentos>`) que ejecuta *snap-to-road* en O(candidatos cercanos) en lugar de O(n) sobre todas las calles.

### 🚶 NPCs y Vehículos

`NpcAiManager` mantiene una población de hasta **40 NPCs** alrededor del jugador, repartidos en dos tipos:

- **Peatones:** caminan sobre vías peatonales (`footway`, `pedestrian`, `path`, `residential`...). Cada uno se ensambla en tiempo real combinando un cuerpo base, un sprite de cabello (`hair_1`...`hair_4`) y tres colores aleatorios (cabello, playera, pantalón) usando una técnica de **tintado inteligente por píxel** que respeta la piel (filtro por saturación) y separa playera/pantalón por luminancia.
- **Vehículos:** 6 modelos (`SEDAN`, `SPORT`, `SUPERCAR`, `SUV`, `VAN`, `WAGON`) con 48 frames de rotación cada uno (uno cada 7.5°). El color se aplica también por píxel preservando luces, intermitentes y rines mediante un análisis de saturación + luminancia.

El comportamiento incluye spawn por proximidad, despawn por distancia (>35 m equivalentes), navegación nodo-a-nodo con transición suave entre ways conectadas, y un sistema de **adopción**: si un NPC entra a la zona sin tener calle asignada (porque fue heredado del servidor), se le engancha automáticamente a la vía más cercana antes de moverlo.

#### Subirse y bajarse de vehículos

Pulsando el botón **X** cerca de un vehículo, el jugador lo "toma": el NPC desaparece de la lista y el jugador pasa a renderizarse como ese coche, con rotación libre en 360° según el ángulo de movimiento. Volver a pulsar X deja el coche estacionado como NPC en la posición actual.

### 🌐 Multijugador en Tiempo Real

El servidor (`Multiplayer/server.js`) es un proceso **Node.js + Express + ws** que mantiene en memoria el estado de jugadores y NPCs y se distribuye como imagen Docker. La instancia pública que usa el cliente Android está hospedada en **[Render](https://render.com/)**, que construye el contenedor directamente desde el repositorio y expone un endpoint WebSocket público que se inyecta en el build mediante `BuildConfig.MULTIPLAYER_SERVER_URL`.

#### Modelo de autoridad: *Zone Host*

En lugar de centralizar toda la simulación en el servidor (lento) o dejarla 100% al cliente (incoherente), POW reparte la autoridad mediante el patrón **Host de Zona**:

- Cada cliente recién conectado es **Host por defecto** dentro de un radio de ~400 m.
- Si dos hosts entran en el mismo radio, el de menor `sessionId` cede la autoridad. El servidor notifica el cambio con un mensaje `ROLE_UPDATE`.
- Solo el host ejecuta la IA de los NPCs en su zona y publica `NPC_BATCH_UPDATE` cada 100 ms. Los demás clientes los reciben pasivamente.
- Cuando un host cede o se desconecta, sus NPCs **no se destruyen**: quedan en el servidor con su última posición y el siguiente host los **adopta**, reenganchándolos a la red de calles local.

#### Robustez

- **Heartbeat:** ping cada 30 s; tras 6 fallos consecutivos (3 minutos sin respuesta) se termina la conexión.
- **Garbage collector de NPCs huérfanos:** NPCs sin actualizar en >15 s se eliminan y se anuncian a todos los clientes.
- **Master sync periódico:** cada 5 s el servidor difunde la lista de IDs de NPCs vivos para que los clientes reconcilien su estado local y limpien fantasmas.
- **Colores serializados como Int ARGB** (no como `ULong` interno de `Compose Color`) para evitar `ColorSpace` corrupto al rehidratar NPCs remotos.

El cliente usa **OkHttp WebSocket** con `readTimeout`/`writeTimeout` en 0 (sin límite) y `pingInterval` de 25 s para sobrevivir a redes móviles con NAT agresivo.

### 🎮 Controles e Interfaz

- **Dos estilos de movimiento:** D-Pad clásico o **Joystick virtual** con rotación libre 360°, configurables desde Ajustes.
- **Escala adaptativa:** los controles se redimensionan entre 60% y 140%, con un límite dinámico de 100% en modo retrato para no comerse la pantalla.
- **Modo zurdo:** los controles de movimiento y acción se pueden intercambiar de lado.
- **Botones de acción** estilo gamepad (A, B, X, Y): correr, ataque especial, interactuar (subir/bajar de vehículos) y un slot reservado.
- **HUD de diagnóstico opcional:**
  - **Widget de caché:** indica si las calles vienen de la red, de Room o si están cargando; idem para los tiles.
  - **Widget de FPS:** medidor de rendimiento gráfico en tiempo real.
- Las preferencias (tipo de control, escala, swap) se persisten en `SharedPreferences` vía `SettingsRepository`.

### 🚀 Stack Tecnológico

| Capa | Tecnología |
|---|---|
| UI | Jetpack Compose, Material 3, Navigation Compose |
| Mapa nativo | osmdroid |
| Mapa web | WebView + Leaflet 1.9.4 |
| Persistencia | Room (v4, `fallbackToDestructiveMigration`) |
| Red | OkHttp (WebSocket), HttpURLConnection (Overpass + tiles) |
| Geolocalización | Google Play Services — Fused Location Provider |
| Concurrencia | Coroutines + Flow / SharedFlow |
| Serialización | Gson |
| Servidor | Node.js 18, Express, ws, Docker |
| Hosting | [Render](https://render.com/) (auto-deploy desde Dockerfile) |

### 🐳 Desplegar el servidor multijugador

Hay dos formas de levantar el servidor:

**Local (Docker, para desarrollo):**

```bash
cd Multiplayer
docker compose up -d
```

**Producción:** la instancia canónica está desplegada en **[Render](https://render.com/)** como un Web Service que se reconstruye automáticamente desde el `Multiplayer/Dockerfile` en cada push a la rama principal. Render se encarga del TLS, el DNS público y expone el endpoint WebSocket por `wss://`.

En ambos casos el servidor escucha en el puerto **8080** con dos endpoints:

- `GET /status` — estado en vivo (jugadores conectados, NPCs activos, timestamp).
- `WS /` — canal WebSocket del juego.

La URL del servidor se inyecta al cliente Android en tiempo de compilación mediante `BuildConfig.MULTIPLAYER_SERVER_URL` (variable de Gradle).

### 📍 Estado actual

Lo que **funciona hoy** en el código de este repositorio: navegación sobre OSM con snap-to-road, caché persistente de calles y tiles, NPCs procedurales (peatones y 6 modelos de coches), multijugador con autoridad delegada por zona, conducción de vehículos, controles configurables y 8 proveedores de mapas.

Lo que **no** está implementado todavía (a pesar de aparecer en planes anteriores): mapas de interiores con renderizado por matrices, minijuegos embebidos (Pacman, supervivencia, etc.), Bluetooth local y pathfinding A*. Son el siguiente bloque de trabajo.