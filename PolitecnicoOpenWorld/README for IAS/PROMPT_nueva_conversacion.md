Proyecto "Politécnico Open World" (POW) — juego Android 2D top-down sobre mapas reales (Kotlin + Jetpack
Compose + MVVM estricto por feature). Proyecto en: `C:\Users\gabri\AndroidStudioProjects\PolitecnicoOpenWorld\`

CONTEXTO (léelo PRIMERO): la carpeta `PolitecnicoOpenWorld\README for IAS` (archivos 00–09 + planes) es el
contexto COMPLETO del proyecto y reemplaza al código. Úsala para saber qué `.kt` tocar sin leer todo el
repo (tablas "Key files" en 04/05). Si necesitas un `.kt` no documentado, pídemelo; no inventes su
contenido. NOTA: el repo real está anidado en `PolitecnicoOpenWorld\PolitecnicoOpenWorld\` (el módulo
`app/` y `README for IAS/` cuelgan de ahí).

═══════════════════════════════════════════════════════════════════════════
🎯 QUÉ SE BUSCA EN ESTA CONVERSACIÓN (léelo y respétalo — NO hagas trabajo de más)
═══════════════════════════════════════════════════════════════════════════
La separación y la optimización SEGURA YA ESTÁN HECHAS. Tras la última sesión, de TODO el módulo solo UN
archivo pasa de 1500 líneas: `WorldMapViewModel.kt` (~2467). `NativeOsmMap.kt` (~1460) y `WorldMapScreen.kt`
(~1325) están BAJO 1500 y ya muy separados; el resto está bajo 1000. La pasada de rendimiento gama baja
(sin cambio de comportamiento) también está hecha y verificada (ver 09 §6).

POR LO TANTO, el objetivo de ESTA conversación es UNO de estos (yo te diré cuál; si no, asume el 1):

1. **(PRINCIPAL, OPCIONAL) Terminar la de-dup de gemelos miembro/extensión de `WorldMapViewModel.kt`** para
   que sea el último archivo en bajar de 1500. Es trabajo CON COMPILADOR, UN PAR POR CICLO (ver §12 y el
   PENDIENTE 1). Si NO quieres tocar el VM (es riesgoso), el código YA ESTÁ BIEN como está: no hay nada más
   que separar.
2. **Una FUNCIÓN/FIX/feature nueva que yo te pida** (ej. un bug, una mejora de UI, una misión). En ese caso,
   sigue las convenciones de 09 y el protocolo de docs, y NO te pongas a re-separar archivos que ya están bien.

LO QUE **NO** HAY QUE HACER (evita el over-engineering):
- NO partir archivos solo porque pasan de 1000 líneas: el objetivo real es <1500, y ya se cumple salvo el VM.
  `NativeOsmMap` (1460) y `WorldMapScreen` (1325) NO necesitan más cortes (más indirección = peor).
- NO meter "optimizaciones" que cambien comportamiento ni que requieran probar a ciegas. La superficie segura
  ya se exprimió. Solo optimiza MÁS si YO reporto un lag concreto, y aun así solo cambios sin comportamiento.
- NO re-hacer separaciones ya hechas (ver YA HECHO).

Mantén SIEMPRE: CAMPAÑA separada de MUNDO abierto (campaña DEPENDE del mundo abierto, NO al revés) y la
lógica de ZOMBIS separada de la de INTERIORES.

═══════════════════════════════════════════════════════════════════════════
⚠️ REGLAS CRÍTICAS DEL ENTORNO (aprendidas a la mala — respétalas SIEMPRE)
═══════════════════════════════════════════════════════════════════════════
1. CIERRA Android Studio mientras la IA edita. Si AS guarda en paralelo, se produce una CARRERA que ya
   corrompió un archivo. Ábrelo solo para compilar cuando la IA te lo pida.
2. Gotcha de truncado del shell: el bash del sandbox a veces sirve COPIAS TRUNCADAS o DUPLICADAS de los
   `.kt` grandes (p. ej. vio 8115 líneas de un archivo de 1462). Por eso:
   * NUNCA uses `sed -i` ni escrituras bash sobre archivos `.kt` (CORROMPEN el archivo). Solo herramientas
     de archivo: Read / Edit / Write.
   * Verifica integridad/balance de llaves SOLO leyendo con `Read`. `wc -l` por bash dio bien la última vez,
     pero ante la duda confirma con `Read`. `mv` (renombrar) sí es seguro (no lee contenido).
3. CRLF: los archivos NUEVOS hechos con Write quedan en LF. NO los conviertas a CRLF con sed (corrompe).
   Normaliza en Android Studio (Edit → Convert line separators → CRLF) o déjalos en LF (compila igual). Al
   EDITAR archivos existentes con Edit se conserva su CRLF.
4. git: el índice quedó corrupto y aparece `.git\index.lock`. Si GitHub Desktop se queja: cierra
   AS+GitHub Desktop, borra `...\PolitecnicoOpenWorld\.git\index.lock`; si dice "corrupt index", borra
   `.git\index` y corre `git -C "...\PolitecnicoOpenWorld" reset`. El repo está en la carpeta EXTERNA.
5. Assets fallan en RUNTIME, no en compilación. Tras tocar rutas de assets hay que PROBAR en la app (un
   "Rebuild" verde no garantiza que el asset exista).
6. NO PUEDES COMPILAR tú: entrega cambios listos para "Rebuild Project"; yo compilo y te confirmo. Para la
   de-dup de gemelos del VM: UN PAR POR CICLO (yo compilo y pruebo entre cada uno).
7. PACKAGE-MOVE / reorg de carpetas: hazlo SIEMPRE con el refactor **"Move" de Android Studio** (lo ejecuto
   yo), NUNCA a mano (cambiar `package` + cientos de FQN/imports a ciegas rompe el build — son ~308 refs).
   ⚠️ El Move de AS **SE SALTA los archivos ABIERTOS/sin guardar** en el editor → CIERRA/guarda todo antes.
   Tras el Move, compila y arregla cualquier `Unresolved reference` restante (mismo patrón: añadir el
   subpaquete, p. ej. `.map`, RESPETANDO `ai.`/`campaign.`/`zombie.`). Ya pasó: `WorldMapState.kt` estaba
   abierto → quedó sin actualizar y rompió `MainActivity`.

═══════════════════════════════════════════════════════════════════════════
✅ YA HECHO (NO lo rehagas) — actualizado 2026-06-21
═══════════════════════════════════════════════════════════════════════════
* `WorldMapViewModel.kt` ~3050→~2467: parciales `WorldMapCombat/Campaign/Teleport/ShineCTO/CameraUi/
  Settings/Providers/Designer/Wanted/SaveGame.kt` (extensiones).
* `WorldMapScreen.kt` ~2470→**~1325**: `WorldMapScreenOverlays.kt` (overlays) + `WorldMapScreenControls.kt`
  (`BoxScope.WorldMapControls`) + **`WorldMapScreenGoogle.kt`** (`GoogleMapLayer`, rama Google) +
  **`WorldMapScreenWeb.kt`** (`WebMapLayer`, rama WEB/Leaflet). Las 3 ramas del `when(mapProvider)` ahora
  delegan: `NativeOsmMap` / `GoogleMapLayer` / `WebMapLayer`.
* `NativeOsmMap.kt` ~1615→~1460: `NativeOsmMapPrankedy.kt` + `NativeOsmMapFog.kt`.
* `NpcAiManager.kt` ~1619→**~882**: `NpcAiManagerMovement.kt` (movers zombi/policía, aggro, distancia) +
  **`NpcAiManagerTraffic.kt`** (`moveNpc`/`moveLocalNpc`: tráfico/calles/campus). Extensiones.
* `ZombieGameViewModel`→`ZombieInteriorViewModel` (renombrado). VM ~996→~934, SOLO interior. Capa zombi
  separada (`ZombieCombat.kt`, `ZombieGameTick.kt`, `ZombieGameDesigner.kt`).
* Campaña reorganizada (`features/campaign/`, `domain/models/campaign/`). Assets reorganizados (AUDIO/
  SPRITES/TRANSIT/…). `InteriorEntryCatalog.kt` (puerta→ruta data-driven).
* **Pasada de rendimiento gama baja (SIN cambio de comportamiento) HECHA:** `FogOverlay.draw` reutiliza los
  arrays del gradiente (no asigna por frame ~30 Hz); `NativeOsmMap.update` usa `screenDensity` cacheado
  (quitadas 4 re-lecturas de `displayMetrics`, una sombreaba); `NpcAiManager` precomputa `cachedWaysFiltered`
  (quita el `.map { it.way }` por tick en los 3 chequeos de spawn). Verificado que el render nativo y
  `PlayerCharacter` ya no asignan por frame. Lo descartado-por-inseguro (template literals JS, ref-cache de
  filtros con predicado temporal, `configureOsmdroid` a background) está listado en 09 §6. Ver 09 §6.
* **De-dup de gemelos del VM (EN CURSO, 2 pares hechos):** `startHealthBarTimer` (idénticos → miembro
  borrado) y `applyRoadNetwork` (DIVERGÍAN: el miembro construía el grafo A* `buildRoadGraph` y pintaba
  calles con la ubicación snapeada; se SINCRONIZÓ la extensión al miembro y se borró el miembro). Ver 09 §12.
* **Configuración en pantalla pequeña (horizontal) ARREGLADO:** `SettingsScreen.kt` tiene modo COMPACTO
  (`compactLand = !isPortrait && screenHeightDp <= 380`) que reduce paddings/spacers/fuentes del cromo y de
  `CategoryItem` (param `compact`). Vertical y horizontal grande quedan idénticos (`if (compactLand) … else
  <original>`, `TextUnit.Unspecified` como default). El contenido ya era scrollable.
* **Preset "Optimizar para mi dispositivo" HECHO:** botón de un toque en Ajustes→Jugabilidad
  (`SettingsScreen.GameplaySettings`, callback `onOptimizeForDevice` cableado en `MainActivity`) que pone la
  densidad de NPCs al mínimo + ambos emoji-toggles ON (persiste en Ajustes Y aplica en vivo). Strings
  `settings_optimize_device/_desc/_applied` en ES+EN.
* **Reorg `domain/models/` HECHA (AS Move → `domain.models.map`):** los 15 archivos PLANOS (Npc/NpcType/
  CarModel, MapWay/MapNode, Landmark, MetroStation/MetrobusStation, ActiveCollectible,
  ExteriorCollisionsConfig+CollisionWall/Polygon, EscomBuildings+InteriorBuilding, CharacterVisualConfig,
  TeleportCatalog, ShineCTOLocation, InteriorEntryCatalog, Landmarkassetcatalog) ahora en paquete
  **`domain.models.map`** (carpeta `domain/models/map/`). Subpaquetes `ai/ campaign/ zombie/` SIN cambio.
  Ya NO quedan clases sueltas en `domain/models/`.

═══════════════════════════════════════════════════════════════════════════
🎯 PENDIENTE (en este orden)
═══════════════════════════════════════════════════════════════════════════
1. **TERMINAR la de-dup de gemelos de `WorldMapViewModel.kt`** (~2467, ÚNICO archivo >1500). El MIEMBRO es
   el canónico; su gemelo de extensión en los parciales está MUERTO y puede haber DIVERGIDO. PROCESO POR PAR
   (obligatorio): (1) leer miembro + extensión, (2) DIFERENCIAR firma y cuerpo, (3) si divergen, reescribir
   la extensión para REPRODUCIR el miembro EXACTO antes de borrarlo, (4) borrar el miembro, (5) yo COMPILO +
   PRUEBO en la app. NO agrupar varios pares sin compilar (no se podría bisecar una regresión). Pares
   pendientes: `updateVisibleRoads` (⚠️ firma divergida: miembro `playerLoc` vs ext `location` → revisar
   call-sites con args nombrados), `updateDestinationRoute`, `triggerWastedSequence`, `spawnOustedDriver`,
   `maybeRefetchRoadNetwork`, `addRemoteEntity`, y los GIGANTES `handleMultiplayerMessage` (~165) y
   `startGameLoop` (~440, máxima divergencia esperada — fue el bug del daño del game loop). Objetivo: VM <1500.
2. **(BAJA PRIORIDAD, opcional) Repasar el resto de docs de `README for IAS`** (00–08 + README bilingüe
   EN+ES) por si algún dato quedó desfasado. Los que toqué (04, 09 §0/§6/§12, plan.artifact, este prompt)
   YA están al día. No reescribas docs correctos.
3. **NADA MÁS de separación ni de optimización por defecto.** Ya se cumplió el objetivo de tamaño (<1500,
   salvo el VM) y la pasada de perf segura. NO partas `NativeOsmMap` (~1460), `WorldMapScreen` (~1325),
   `MainActivity` (~961), `ZombieGameScreen` (~948) ni otros — están bien. Solo si TÚ reportas un lag
   concreto al probar la app: aplicar micro-opts ADICIONALES y SEGURAS (sin cambio de comportamiento); lo
   ya descartado-por-inseguro está en 09 §6 (no re-proponerlo).

═══════════════════════════════════════════════════════════════════════════
REGLAS DE CÓDIGO (archivo 09)
═══════════════════════════════════════════════════════════════════════════
* MVVM estricto. Estado inmutable: `_state.update { it.copy(...) }`. Views solo `collectAsState()` +
  intenciones; nunca tocan repos/DAOs.
* Comentarios y strings de UI en español.
* Gotcha "miembro vs extensión": si una función existe como miembro privado del VM Y como extensión
  homónima, GANA el miembro; la extensión queda muerta. Edita el MIEMBRO y verifica ambos. (La de-dup
  consiste precisamente en sincronizar la extensión al miembro y borrar el miembro — ver PENDIENTE 1.)
* Las extensiones solo ven miembros `internal`/`public` (no `private`). Para extraer a extensión, pasa a
  `internal` los `private` que necesite (cambio seguro, sin cambio de comportamiento).
* Al extraer un composable/rama de Compose, las cachés/holders locales (`nativeDrawableCache`, `webViewRef`,
  `base64Cache`, los `lastWeb*`…) se pasan POR PARÁMETRO (no se recrean), para no romper su estado entre
  frames. Los helpers/consts del mismo paquete `ui` se ven sin import; las extensiones del VM usadas SÍ se
  importan. OJO con imports de coroutines (`kotlinx.coroutines.launch`) en archivos nuevos.
* `InteriorViewModel` YA EXISTE (motor de salas simples de ESCOM, sin zombis); NO renombres nada a ese
  nombre. El de supervivencia es `ZombieInteriorViewModel`.
* Verifica balance de llaves/paréntesis por archivo (con Read). Pasos pequeños, UNO POR ARCHIVO.
* **ORGANIZACIÓN POR CARPETAS/PAQUETES (mantenla):** el código se agrupa por dominio/feature, NO se dejan
  clases sueltas. Convención actual: `features/<feature>/{ui,viewmodel,models}`, `domain/models/{map,ai,
  campaign,zombie}`, `data/{repository,cache,auth}`. Si creas o mueves clases, colócalas en el paquete que
  les toca; si detectas clases sueltas o mal ubicadas, agrúpalas — pero los **package-moves** SIEMPRE con el
  refactor Move de Android Studio (ver regla 7 del entorno), nunca a mano. `package` == carpeta.
* Verifica que cada cambio NO rompa la separación CAMPAÑA⇄MUNDO ni ZOMBIS⇄INTERIORES.
* AL TERMINAR cada cambio de comportamiento/estructura: PROTOCOLO DE DOCS del archivo 09 — actualiza
  `README.md` (bilingüe EN+ES), `plan.artifact.md` y los docs 00–09.

═══════════════════════════════════════════════════════════════════════════
💡 SUGERENCIAS DE FEATURES (candidatas para la "opción 2" — solo si te las pido)
═══════════════════════════════════════════════════════════════════════════
Ideas que ENCAJAN con el andamiaje actual (no son obligatorias; el dueño decide). Ordenadas por valor/esfuerzo:
BAJO ESFUERZO (la arquitectura YA lo soporta):
1. **Misión 2 de campaña** — ya están `MissionCatalog` (agregador), `campaign/mission1/Mission1.kt`,
   `CampaignObjective`. Crear `campaign/mission2/Mission2.kt` + extender el catálogo. Continúa tras
   "Busca pistas en la ESCOM".
2. **Habilitar más campus (FES Aragón / UAM)** — el motor de interiores es campus-agnóstico
   (`ZombieRoomCatalog.campusRooms`, `lobbyForBuilding`); `SchoolCatalog` ya los tiene con `available=false`.
   Activar + sembrar salas.
3. **Preset "Optimizar para mi dispositivo" (un toque)** — ya existen los toggles sueltos (densidad NPC,
   emoji LOD, full-emoji, proveedor de mapa). Un botón que los active juntos (o autodetecte por RAM con
   `deviceTierFactor`).
4. **Terminar i18n** — `strings.xml` ya migrado en `main_menu`/`settings`; faltan WorldMap/HUD/campaña/
   interiores. Externalizar el resto + sumar idiomas.
ESFUERZO MEDIO:
5. **Minimapa / brújula de objetivo** (ya hay waypoint 🎯 + línea al objetivo).
6. **Ciclo día/noche** — tintar mapa/fog según la hora (el fog ya es overlay por renderer → costo bajo).
7. **Pulido del apocalipsis global** — contador de zombis/jugadores vivos, oleadas con dificultad creciente
   (ya hay constantes `HORDE_*`), recompensas.

ESTILO: respuestas concisas y directas, sin relleno. AUTONOMÍA: avanza con TODO lo que consideres prudente
sin pedirme permiso; toma defaults sensatos, hazlo y documéntalo. Solo detente si algo es genuinamente
ambiguo a nivel de producto o destructivo/irreversible (o si necesitas que compile un par de la de-dup del
VM). Al final dame un resumen de lo hecho y lo pendiente.
