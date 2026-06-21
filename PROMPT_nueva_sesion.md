# Mensaje para la nueva conversación (cópialo tal cual)

Proyecto "Politécnico Open World" (POW) — juego Android 2D top-down sobre mapas reales
(Kotlin + Jetpack Compose + MVVM estricto por feature). Proyecto en:
`C:\Users\gabri\AndroidStudioProjects\PolitecnicoOpenWorld\`

CONTEXTO (léelo PRIMERO): la carpeta `PolitecnicoOpenWorld\README for IAS` (archivos 00–09) es el
contexto COMPLETO del proyecto y reemplaza al código. Úsala para saber qué `.kt` tocar sin leer todo
el repo (tablas "Key files" en 04/05). Si necesitas un `.kt` no documentado, pídemelo; no inventes su
contenido.

TAREA: seguir separando/optimizando clases largas (>1000 líneas) para reducir tokens (pensando en un
LLM gratis), manteniendo CAMPAÑA separada de MUNDO abierto (pero ambas funcionan juntas: campaña
DEPENDE del mundo abierto, NO al revés) y la lógica de ZOMBIS separada de la de INTERIORES.

═══════════════════════════════════════════════════════════════════════════
⚠️ REGLAS CRÍTICAS DEL ENTORNO (aprendidas a la mala — respétalas SIEMPRE)
═══════════════════════════════════════════════════════════════════════════
1. **CIERRA Android Studio mientras la IA edita.** Si AS guarda en paralelo, se produce una CARRERA
   que ya corrompió un archivo. Ábrelo solo para compilar cuando la IA te lo pida.
2. **Gotcha de truncado del shell:** el bash a veces sirve COPIAS TRUNCADAS de los `.kt` grandes
   (p. ej. ve 154 líneas de un archivo de 222). Por eso:
   - NUNCA uses `sed -i` ni escrituras bash sobre archivos `.kt` (escriben de vuelta la versión
     truncada y CORROMPEN el archivo). Solo herramientas de archivo: Read / Edit / Write.
   - Verifica integridad/balance SOLO con `Read`, nunca con `wc -l`/`grep` de bash (ven la copia
     truncada). `mv` (renombrar) sí es seguro (no lee contenido).
3. **CRLF:** los archivos NUEVOS hechos con Write quedan en LF. NO los conviertas a CRLF con sed
   (corrompe). Normaliza el fin de línea en Android Studio (Edit → Convert line separators) o deja LF
   (compila igual). Al EDITAR archivos existentes con Edit se conserva su CRLF.
4. **git:** el índice quedó corrupto y aparece `.git\index.lock`. Si GitHub Desktop se queja:
   cierra AS+GitHub Desktop, borra `...\PolitecnicoOpenWorld\.git\index.lock`; si dice "corrupt index",
   borra `.git\index` y corre `git -C "...\PolitecnicoOpenWorld" reset`. El repo está en la carpeta
   EXTERNA `PolitecnicoOpenWorld\`.
5. **Assets fallan en RUNTIME, no en compilación.** Tras tocar rutas de assets hay que PROBAR en la app
   (un "Rebuild" verde no garantiza que el asset exista).

═══════════════════════════════════════════════════════════════════════════
✅ YA HECHO (NO lo rehagas)
═══════════════════════════════════════════════════════════════════════════
- `WorldMapViewModel.kt` ~3050→~2503: parciales `WorldMapCombat/Campaign/Teleport/ShineCTO/CameraUi/
  Settings.kt` (extensiones).
- `WorldMapScreen.kt` ~2470→~2264: `WorldMapScreenOverlays.kt` (overlays) + `WorldMapScreenControls.kt`
  (`BoxScope.WorldMapControls`).
- `NativeOsmMap.kt` ~1615→~1461: `NativeOsmMapPrankedy.kt` + `NativeOsmMapFog.kt`.
- `NpcAiManager.kt` ~1619→~1419: `NpcAiManagerMovement.kt` (movers zombi/policía, aggro, distancia).
- `ZombieGameViewModel`→**`ZombieInteriorViewModel`** (renombrado; clase+archivo+refs). VM ~996→~934,
  SOLO interior (salas/movimiento/puertas/llaves/red de jugadores). Capa zombi separada:
  `ZombieCombat.kt` (combate+efectos+red zombi) y `ZombieGameTick.kt` (simulación). Diseñador en
  `ZombieGameDesigner.kt`. (OJO: `ZombieCombat.kt` quedó en LF tras una recuperación — normalízalo.)
- `InteriorEntryCatalog.kt` (puerta→ruta data-driven) + `handleInteraction` lo usa.
- Campaña reorganizada: `features/campaign/{ui,viewmodel}` (StoryMode) + `domain/models/campaign/`
  (CampaignObjective, MissionCatalog [agregador], SchoolCatalog, StoryComicCatalog) +
  `campaign/mission1/Mission1.kt`. Repos siguen en `data/repository`.
- Assets reorganizados: `AUDIO/ SPRITES/ TRANSIT/ CONFIG/ INTERIORS/ VIDEO/ BUILDINGS/ DOORS/ PLACES/
  STORY/ CAMPAIGN/` (+ iconos en `SPRITES/ICONS/`). Huérfanos en `app/src/main/_ORPHAN_ASSETS/`.
  3 audios con espacios/typo renombrados. Ver `PROPUESTA_reorg_assets.md`.
- **`NpcAiManager.kt` ~1419→~882 (<1000 ✅):** `moveNpc` (~382) + `moveLocalNpc` (~159) → `NpcAiManagerTraffic.kt`
  (extensiones). ~15 `private`→`internal` (cachedNavLandmarks, nodeToWays, exteriorCollisions, parkedTimers/
  parkingCooldowns/carExitCooldowns/landmarkEntranceCooldowns, carSpeed, PARKING_WAKE_*, TRAFFIC_AVOID_* x5,
  isNativeWayOverlappingCustom). Companion cualificado (NpcAiManager.LANE_OFFSET/FEAR_SPEED_MULT). Miembros
  eliminados (sin gemelo). Archivo nuevo en LF — normalizar en Android Studio o dejar LF.

═══════════════════════════════════════════════════════════════════════════
🎯 PENDIENTE (en este orden)
═══════════════════════════════════════════════════════════════════════════
1. **(OPCIONAL) Más `NpcAiManager.kt`** (~882, ya <1000): si se quiere reducir más, extraer el cluster de
   spawn (`spawnNpcOnRoad`/`spawnParkedCar`/`spawnCampusPedestrian`/`getAvailableParkingSlots`) y/o
   `updateNpcs` (~370) a `NpcAiManager*.kt` (extensiones). Pasar a `internal` los `private` que toquen.
   Verificar gemelos miembro/extensión.
2. **Reorg `domain/models/`**: agrupar los ~15 archivos planos en `domain/models/map/` (Landmark,
   MapNode, MapWay, Npc, NpcType, ExteriorCollisionsConfig, TeleportCatalog, Metro/MetrobusStation,
   ActiveCollectible, ShineCTOLocation, EscomBuildings, Landmarkassetcatalog, CharacterVisualConfig,
   InteriorEntryCatalog), manteniendo `ai/ campaign/ zombie/`. Es package-move: cambiar `package` +
   actualizar TODOS los imports/FQN; verificar 0 refs viejas.
3. **Más `WorldMapScreen.kt`** (~2264, es UN composable gigante): extraer por sección las 3 ramas de
   render (OSM/Google/Web), el builder del menú de Opciones y los widgets de HUD a composables del
   mismo paquete `ui`. Atención al acoplamiento con cachés locales (`nativeDrawableCache`, `webViewRef`,
   `base64Cache`…): pásalos como parámetros.
4. (Opcional) Otros >900: `MainActivity.kt` (~961), `ZombieGameScreen.kt` (~948).

NO recomendado: partir `ZombieGameState` en InteriorState+ZombieState (≈166 sitios de acceso, `copy`
anidados verbosos, beneficio cosmético; la lógica YA está separada). De-dup de gemelos en
`WorldMapViewModel` (startGameLoop/handleMultiplayerMessage/updateVisibleRoads/…): solo CON compilador.

═══════════════════════════════════════════════════════════════════════════
REGLAS DE CÓDIGO (archivo 09)
═══════════════════════════════════════════════════════════════════════════
- MVVM estricto. Estado inmutable: `_state.update { it.copy(...) }`. Views solo `collectAsState()` +
  intenciones; nunca tocan repos/DAOs.
- Comentarios y strings de UI en español.
- Gotcha "miembro vs extensión": si una función existe como miembro privado del VM Y como extensión
  homónima, GANA el miembro; la extensión queda muerta. Edita el miembro y verifica ambos.
- Las extensiones solo ven miembros `internal`/`public` (no `private`). Para extraer a extensión, pasa
  a `internal` los `private` que necesite (cambio seguro, sin cambio de comportamiento).
- `InteriorViewModel` YA EXISTE (motor de salas simples de ESCOM, sin zombis); NO renombres nada a ese
  nombre. El de supervivencia es `ZombieInteriorViewModel`.
- Verifica balance de llaves/paréntesis por archivo (con Read). Pasos pequeños, UNO POR ARCHIVO.
- No puedes compilar tú: entrega cambios listos para "Rebuild Project"; yo compilo y te confirmo.
- AL TERMINAR cada cambio de comportamiento/estructura: PROTOCOLO DE DOCS del archivo 09 — actualiza
  `README.md` (bilingüe EN+ES), `plan.artifact.md` y los docs 00–09.

ESTILO: conciso y directo, sin relleno. AUTONOMÍA: avanza con TODO lo que consideres prudente sin
pedirme permiso; toma defaults sensatos, hazlo y documéntalo. Solo detente si algo es genuinamente
ambiguo a nivel de producto o destructivo/irreversible. Al final dame un resumen de lo hecho y lo
pendiente.
