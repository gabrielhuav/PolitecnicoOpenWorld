# 01 · Arquitectura / Architecture

## Qué es / What it is

**ES:** POW es un juego 2D *top-down* sobre **mapas reales** (OpenStreetMap). El jugador
camina/conduce por calles reales (foco: ESCOM / Zacatenco, CDMX), comparte el mundo con NPCs
procedurales (peatones + vehículos) y con otros jugadores vía servidor en tiempo real. El campus
de ESCOM aloja un **minijuego de supervivencia zombi** con interiores y combate melee/ranged.
Además trae **"Huelum vs. Goya"**, un modo de pelea 1v1 estilo arcade.

**EN:** POW is a 2D top-down game over **real-world maps** (OpenStreetMap). The player
walks/drives real streets (focus: ESCOM / Zacatenco, Mexico City), shares the world with
procedural NPCs (pedestrians + vehicles) and other players over a real-time server. The ESCOM
campus hosts a **zombie survival minigame** with interiors and melee/ranged combat. It also ships
**"Huelum vs. Goya"**, an arcade-style 1v1 fighting mode.

> 🍏 **Ya no es "un juego Android".** Desde 2026-07-30 el modo pelea (con menú, Ajustes y
> Coleccionables) **corre también en iOS**, desde el mismo código Kotlin. El mundo abierto sigue
> siendo solo-Android y es trabajo futuro. Qué corre dónde: `00_INDEX.md` → "Qué corre en cada
> plataforma". Dónde tocar cada cosa: **`11_SEPARACION_IOS_ANDROID.md`**.

## Los módulos y los proyectos hermanos

```text
PolitecnicoOpenWorld/        ── este repo
├── shared/                  # 🔷 Kotlin Multiplatform: el juego. Android + iOS
│   └── src/{commonMain,androidMain,iosMain}/
├── app/                     # 🤖 La app Android (consume :shared)
└── iosApp/                  # 🍏 El proyecto Xcode (consume :shared como framework)

Multiplayer/                 # Servidor open world (Node.js + ws, v3, dockerizado)
MultiplayerInteriores/       # Servidor minijuego zombi (zombis autoritativos, dockerizado)
MultiplayerSF/               # Servidor del multijugador de pelea
```

> Los directorios de servidor son **hermanos** del proyecto, no submódulos; pueden no estar en todos
> los checkouts. / The server dirs are siblings of the project; may be absent in some checkouts.
>
> ⚠️ **`shared/` y `app/` no son intercambiables.** La regla de qué va en cada uno está en
> `10_ARQUITECTURA_SEPARACION.md` §2 y, con árbol de decisión, en `11_SEPARACION_IOS_ANDROID.md` §2.

## MVVM (contrato — síguelo al añadir código / contract — follow when adding code)

Cada *feature* se divide en 3 capas / Every feature splits into 3 layers:

- **Model** (`domain/models/`): `data class` inmutables + lógica pura (`NpcAiManager`,
  `PoliceManager`). **Sin imports de Android, sin UI.** / Immutable data classes + pure logic. No Android, no UI.
- **ViewModel** (`features/<name>/viewmodel/`): UN `MutableStateFlow<State>` expuesto como
  `StateFlow` de solo lectura; corre los game loops con coroutines; orquesta repositorios. El
  estado es un `data class` inmutable actualizado con `_state.update { it.copy(...) }`. / ONE
  `MutableStateFlow<State>` exposed read-only; drives game loops; orchestrates repos. Immutable state via `copy`.
- **🆕 Managers con sub-estado + fachada `combine` (WorldMapViewModel, Etapa 3 calidad senior):** para no
  tener un god-object, el estado UI de `WorldMapViewModel` se reparte en **6 managers** propios
  (`viewmodel/DesignerManager`, `CollectiblesManager`, `CombatManager`, `WantedManager`,
  `TransitTeleportManager`, `CampaignManager`), cada uno con su `MutableStateFlow<XSubState>` y lógica pura
  (testeable en JVM). El VM COMPONE el `uiState` con `combine(_uiState, …managers…) { base.copy(campos) }`
  (anidado: >5 flows) para que las Views sigan viendo UN `WorldMapState`. Combat usa `mutableStateOf` delegado
  en vez del combine (estado Compose-directo). Los campos poseídos por managers van anotados ⚠️ en
  `WorldMapState.kt` (no escribirlos con `_uiState.update` → la fachada los sobreescribe; ver 09 §1). El
  estado de FASE de misión se queda en el VM (corte limpio, entrelazado con el game loop). / Managers own
  sub-state; VM composes `uiState` via `combine`; see 09 §1.
- **View** (`features/<name>/ui/`): Compose puro; observa con `collectAsState()`; solo emite
  intenciones al ViewModel. **Nunca toca repos/DAOs.** / Pure Compose; observes via `collectAsState()`; emits intents only. Never touches repos/DAOs.

**Scoping / Alcance de ViewModels:**

| ViewModel | Scope | Nota |
|---|---|---|
| `WorldMapViewModel`, `SettingsViewModel`, `CollectiblesViewModel` | **Activity** | Sobreviven a la navegación / survive navigation |
| `InteriorViewModel`, `TransitInteriorViewModel`, `ZombieInteriorViewModel`, `ShineCTOViewModel` | **NavBackStackEntry** | Se reinician al salir / reset on leave |

**DI / Inyección: 🆕 Hilt (Etapa 4 calidad senior; antes `ViewModelProvider.Factory` manual).** `@HiltAndroidApp`
en `PowApplication`, `@AndroidEntryPoint` en `MainActivity`. Las 9 VMs son `@HiltViewModel @Inject`; las que
reciben args de navegación (`InteriorViewModel`/`TransitInteriorViewModel`/`ZombieInteriorViewModel`) usan
`@AssistedInject` + `@AssistedFactory` (`hiltViewModel(creationCallback)`). Las deps (BD Room, cachés,
repos) las provee `di/AppModule.kt` (`@InstallIn(SingletonComponent)`). El scope se preserva: WorldMap/
Settings/Collectibles con `by viewModels()` (Activity), el resto con `hiltViewModel()` (NavBackStackEntry).
El compilador va por **KSP** (no kapt). Ver `_ARCHIVO/PLAN_DI_hilt.md` (histórico). / Hilt DI (KSP); assisted-inject for nav-arg VMs.

## Árbol del cliente / Client tree

```text
app/src/main/java/ovh/gabrielhuav/pow/
├── di/                  # 🆕 Hilt: AppModule (@InstallIn SingletonComponent) → BD/cachés/repos
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
│   ├── streetfighter/    → ver 07  (🆕 STREET FIGHTER: minijuego 1v1 dev-gated; ui + viewmodel + data; modelos puros en domain/models/streetfighter/; assets en assets/STREETFIGHTER/)
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
| `street_fighter` | 🆕 `StreetFighterScreen` (`features/streetfighter/`): minijuego **STREET FIGHTER** (port fiel del clon JS StreetFighter-main: Ryu vs Ken CPU, sprites/sonidos originales en `assets/STREETFIGHTER/`). Botón del menú principal visible SOLO con Modo Desarrollador. Landscape (no está en `portraitRoutes`). Ver 07 |

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
| Auth / Autenticación | Firebase Auth (Google Sign-In) + play-services-auth; plugin `google-services` (requiere `google-services.json`). Servidores: `firebase-admin`. |
| Servidores / Servers | Node.js 18, Express, ws, Docker |
| Hosting | Render (auto-deploy desde Dockerfile / from Dockerfile) |

## Build / run

- **Cliente:** abrir en Android Studio → **Build → Rebuild Project**.
- **🆕 Firebase es OPCIONAL para compilar (contribuidores / PRs):** el plugin `google-services` se aplica
  **solo si existe `app/google-services.json`** (condicional al final de `app/build.gradle.kts`). Ese archivo
  está en `.gitignore`, así que cualquiera puede clonar y compilar/correr SIN él. Sin el json: el **login
  con Google** queda deshabilitado (`AuthManager.isAvailable()=false`, degrada sin crashear), pero el
  **multijugador SÍ funciona en modo ANÓNIMO** — el gate de MULTIJUGADOR detecta que Firebase no está
  disponible y conecta sin token; los servidores en **modo suave** (`AUTH_REQUIRED` no = true) aceptan la
  conexión. Con el json, el multijugador exige sesión de Google. El maintainer agrega el json (y configura
  `FIREBASE_SERVICE_ACCOUNT` en los servidores) para habilitar la identidad por cuenta.
- **URLs de servidor / Server URLs** inyectadas vía Gradle → `BuildConfig.MULTIPLAYER_SERVER_URL`
  (open world), `BuildConfig.INTERIORS_SERVER_URL` (zombi) y `BuildConfig.SF_SERVER_URL` (modo
  pelea). Versión en menú: `BuildConfig.VERSION_NAME`.
- **🆕 Assets por VARIANTE (2026-07-15):** `app/src/debug/assets/STREETFIGHTER/` lleva los
  assets del clon SF (Ryu/Ken) SOLO en builds debug (cable); el bundle de Play (release) no
  los incluye — copyright. Reglas y cierres en 09 §12 y 07 §STREET FIGHTER. / Debug-only
  source-set assets for the SF clone (Ryu/Ken); release/Play ships without them.
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
