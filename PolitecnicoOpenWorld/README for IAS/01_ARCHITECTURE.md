# 01 · Arquitectura / Architecture

## Qué es / What it is

**ES:** POW es un juego Android 2D *top-down* sobre **mapas reales** (OpenStreetMap). El jugador
camina/conduce por calles reales (foco: ESCOM / Zacatenco, CDMX), comparte el mundo con NPCs
procedurales (peatones + vehículos) y con otros jugadores vía servidor en tiempo real. El campus
de ESCOM aloja un **minijuego de supervivencia zombi** con interiores y combate melee/ranged.

**EN:** POW is an Android 2D top-down game over **real-world maps** (OpenStreetMap). The player
walks/drives real streets (focus: ESCOM / Zacatenco, Mexico City), shares the world with
procedural NPCs (pedestrians + vehicles) and other players over a real-time server. The ESCOM
campus hosts a **zombie survival minigame** with interiors and melee/ranged combat.

## Tres proyectos / Three projects

```text
.
├── PolitecnicoOpenWorld/   # Cliente Android (Kotlin + Compose)  ── este repo
├── Multiplayer/            # Servidor open world (Node.js + ws, v3, dockerizado)
└── MultiplayerInteriores/      # Servidor minijuego zombi (zombis autoritativos, dockerizado)
```

> Los dos directorios de servidor son hermanos del proyecto Android; pueden no estar en todos los
> checkouts. / The two server dirs are siblings of the Android project; may be absent in some checkouts.

## MVVM (contrato — síguelo al añadir código / contract — follow when adding code)

Cada *feature* se divide en 3 capas / Every feature splits into 3 layers:

- **Model** (`domain/models/`): `data class` inmutables + lógica pura (`NpcAiManager`,
  `PoliceManager`). **Sin imports de Android, sin UI.** / Immutable data classes + pure logic. No Android, no UI.
- **ViewModel** (`features/<name>/viewmodel/`): UN `MutableStateFlow<State>` expuesto como
  `StateFlow` de solo lectura; corre los game loops con coroutines; orquesta repositorios. El
  estado es un `data class` inmutable actualizado con `_state.update { it.copy(...) }`. / ONE
  `MutableStateFlow<State>` exposed read-only; drives game loops; orchestrates repos. Immutable state via `copy`.
- **View** (`features/<name>/ui/`): Compose puro; observa con `collectAsState()`; solo emite
  intenciones al ViewModel. **Nunca toca repos/DAOs.** / Pure Compose; observes via `collectAsState()`; emits intents only. Never touches repos/DAOs.

**Scoping / Alcance de ViewModels:**

| ViewModel | Scope | Nota |
|---|---|---|
| `WorldMapViewModel`, `SettingsViewModel`, `CollectiblesViewModel` | **Activity** | Sobreviven a la navegación / survive navigation |
| `InteriorViewModel`, `MetroInteriorViewModel`, `ZombieGameViewModel`, `ShineCTOViewModel` | **NavBackStackEntry** | Se reinician al salir / reset on leave |

**DI / Inyección:** manual, vía `ViewModelProvider.Factory` co-localizada con cada ViewModel
(p. ej. `WorldMapViewModel.Factory(context)`). / Manual DI via co-located factories.

## Árbol del cliente / Client tree

```text
app/src/main/java/ovh/gabrielhuav/pow/
├── data/                # Capa de datos: Room, cachés, red, repos  → ver 02
├── domain/models/       # Modelos puros + IA                       → ver 03
├── features/            # Módulos por feature: <name>/ui + <name>/viewmodel
│   ├── main_menu/        → ver 07
│   ├── map_exterior/     → ver 04  (núcleo del open world / EXTERIORES)
│   ├── interiores/       # UMBRELLA del modo Interiores (expandible a más universidades)
│   │   ├── core/          # COMPARTIDO: DesignerTarget+CameraTransform (viewmodel),
│   │   │                  #   PlayerView/PlayerHealthBarFixed/RemotePlayerView + designer layers (ui)
│   │   ├── escom/         → ver 06  (interiores simples ESCOM + metro; antes features/interior/)
│   │   ├── zombies/       → ver 05  (capa de zombis; antes features/zombie_minigame/)
│   │   └── shinecto/      → ver 07  (easter egg; antes features/shinecto/)
│   └── settings/         → ver 07
├── ui/theme/            # Tema Material 3 (Color.kt, Theme.kt, Type.kt)
└── MainActivity.kt      # Single-Activity + Compose NavHost
```

## Navegación / Navigation — `MainActivity.kt` (Single-Activity, Compose `NavHost`)

`startDestination = "main_menu"`. Rutas / Routes:

| Ruta / Route | Pantalla / Screen |
|---|---|
| `main_menu` | `MainMenuScreen` |
| `story_mode` | `StoryModeScreen` (Modo Historia / Campaña: prólogo + elegir escuela + cargar partida) |
| `story_intro/{schoolId}` | `StoryIntroScreen` (intro cómic; al INICIAR/último panel guarda partida y entra a `encb_lobby`) |
| `encb_lobby` | `ZombieGameScreen` (motor de Interiores; `startRoom=encb_lobby`). Entrada a la **cadena LINEAL del Modo Historia**: `encb_lobby → encb_salon1 → encb_lab1 → encb_lab2` (salas LOBBY, fondos `INTERIORS/ENCB/*.webp`, sin zombis/mano; puerta de AVANCE con X en cada una, sin salida al mapa entre medias). Transiciones internas en el mismo VM. El waypoint final de `encb_lab2` (X) sale a la narrativa → ruta `story_outro` (sentinela `EXIT_TO_STORY_OUTRO`). Objetivo superpuesto |
| `story_outro` | `StoryIntroScreen` (visor de cómic con `sequenceId = StoryComicCatalog.ENCB_OUTRO_ID`: 2ª parte de la intro, `IntroPOW9..11.webp`). Oculta la UI de juego; al terminar/saltar → `world_map` (mundo de la campaña) |
| `settings` | `SettingsScreen` |
| `world_map` | `WorldMapScreen` (open world) |
| `collectibles` | `CollectiblesScreen` |
| `interior_auditorio` … `interior_canchas_futbol` | 7 interiores ESCOM (`InteriorScreenBase`, paquete `interiores.escom.ui`) |
| `interior_deportivo_beis`, `interior_deportivo_futbol` | Interiores deportivos (`interiores.escom.ui`) |
| `interior_fes` | Interior FES Aragón simple (`FesInteriorScreen`, `interiores.escom.ui`). **Existe pero la puerta YA NO lo usa**: la puerta "Entrada FES Aragón" entra al motor de Interiores en su sala propia (`interiores_zombies?startRoom=fes_interior`). Reservado. |
| `metro_station_interior/{stationName}?spawnX={spawnX}&spawnY={spawnY}` | `MetroStationInteriorScreen` (`interiores.escom.ui`, ruta parametrizada) |
| `interiores_zombies?startRoom={startRoom}` | `ZombieGameScreen` (motor de Interiores; `startRoom` = sala inicial, default `lobby_campus`; la puerta FES pasa `fes_interior`) |
| `shinecto_interior` | `ShineCTOScreen` (`interiores.shinecto.ui`, easter egg) |

**MainActivity** también: configura osmdroid (`configureOsmdroid`), pide permisos y obtiene la
ubicación con Fused Location Provider (`checkPermissionsAndFetchLocation`, `fetchCurrentLocation`),
y libera cachés de sprites en `onTrimMemory` (ver 09). / Also configures osmdroid, requests
permissions + Fused Location, and frees sprite caches on `onTrimMemory` (see 09).

## Stack técnico / Tech stack

| Capa / Layer | Tecnología / Technology |
|---|---|
| UI | Jetpack Compose, Material 3, Navigation Compose |
| Mapa nativo / Native map | osmdroid + Google Maps Compose |
| Mapa web / Web map | WebView + Leaflet 1.9.4 |
| Persistencia / Persistence | Room (v8, `MIGRATION_7_8` + `fallbackToDestructiveMigration`) |
| Red / Network | OkHttp (WebSocket), HttpURLConnection (Overpass + tiles) |
| Geolocalización / Geolocation | Google Play Services — Fused Location Provider |
| Concurrencia / Concurrency | Coroutines + Flow / SharedFlow / StateFlow |
| Serialización / Serialization | Gson |
| Servidores / Servers | Node.js 18, Express, ws, Docker |
| Hosting | Render (auto-deploy desde Dockerfile / from Dockerfile) |

## Build / run

- **Cliente:** abrir en Android Studio → **Build → Rebuild Project**.
- **URLs de servidor / Server URLs** inyectadas vía Gradle → `BuildConfig.MULTIPLAYER_SERVER_URL`
  (open world) y `BuildConfig.INTERIORS_SERVER_URL` (zombi). Versión en menú: `BuildConfig.VERSION_NAME`.
- **Servidores (separados, ambos escuchan en contenedor `:8080`, `GET /status`, `WS /`):**
  - Open world: `cd Multiplayer && docker compose up -d` (host `:8080`).
  - Zombi: `cd MultiplayerInteriores && docker compose up -d` (host `:8081` → contenedor `:8080`).
- Los `server.js` son Node puro (sin build); validar con `node --check server.js`.

## Nota para IA / Note for AI agents

**ES:** Si tras un merge aparecen errores en cascada *"Unresolved reference"*, sospecha de **un
único archivo estructuralmente roto** (llave/paréntesis de más o de menos), no de muchos símbolos
faltantes: arreglar la clase rota suele limpiar todo. Verifica el balance de llaves/paréntesis por
archivo antes de asumir que un símbolo falta.

**EN:** If a merge produces cascading *"Unresolved reference"* errors, suspect **one
structurally-broken file** (missing/extra brace or paren), not many missing symbols: fixing the
broken class usually clears all of them. Check brace/paren balance per file before assuming a
symbol is genuinely missing.
