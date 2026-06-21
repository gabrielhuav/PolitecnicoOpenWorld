// domain/models/zombie/ZombieRoomCatalog.kt
package ovh.gabrielhuav.pow.domain.models.zombie

object ZombieRoomCatalog {

    const val LOBBY_ID = "lobby_campus"
    const val EXIT_TO_WORLD = "__WORLD__"
    // MODO HISTORIA: targetRoomId "sentinela" — una puerta con este destino NO carga otra
    // sala, sino que dispara la salida del motor de interiores hacia la narrativa (cómic
    // ENCB_OUTRO). Lo intercepta ZombieInteriorViewModel.goToRoom (igual que EXIT_TO_WORLD).
    const val EXIT_TO_STORY_OUTRO = "__STORY_OUTRO__"

    // Sala de INTERIORES de FES Aragón. Es una sala independiente del anillo de ESCOM:
    // se entra desde la puerta "Entrada FES Aragón" del open world (ruta
    // interiores_zombies?startRoom=fes_interior). Tipo LOBBY = zona segura sin zombis
    // (el servidor sólo siembra zombis en salas BUILDING) y con puerta de salida al mapa.
    const val FES_ID = "fes_interior"

    // MODO HISTORIA: cadena LINEAL de salas de la ENCB (tras la intro IntroPOW8).
    // Todas son salas INDEPENDIENTES del anillo de ESCOM, tipo LOBBY = zona segura SIN
    // zombis y SIN mano zombi (gateada a LOBBY_ID). El flujo es:
    //   encb_lobby → encb_salon1 → encb_lab1 → encb_lab2  (sin salida al mapa entre medias;
    //   navegación "atrapada" en el flujo interno). Cada sala tiene UNA puerta de AVANCE a la
    //   siguiente (waypoint interactivo, tecla X); la última (encb_lab2) no tiene puertas.
    const val ENCB_LOBBY_ID = "encb_lobby"
    const val ENCB_SALON1_ID = "encb_salon1"
    const val ENCB_LAB1_ID = "encb_lab1"
    const val ENCB_LAB2_ID = "encb_lab2"

    // Salas del Modo Historia de la ENCB. ZombieGameScreen pinta el banner de objetivo
    // ("Objetivo: Investiga qué pasó") cuando la sala actual pertenece a este conjunto.
    val ENCB_STORY_ROOM_IDS = setOf(ENCB_LOBBY_ID, ENCB_SALON1_ID, ENCB_LAB1_ID, ENCB_LAB2_ID)

    private val buildingOrder = listOf(
        "za_auditorio", "za_biblioteca", "za_cafeteria",
        "za_edificio", "za_estacionamiento", "za_palapas", "za_canchas_futbol"
    )

    // ─── MATRICES DE COLISIÓN POR DEFECTO ─────────────────────────────────────
    // IMPORTANTE: estas son sólo un PUNTO DE PARTIDA NEUTRO — borde de pared,
    // interior caminable. NO intentan reproducir el dibujo real de cada cuarto.
    // Cada lugar debe pintar su matriz real con el MODO DISEÑADOR (botón con el
    // icono de escuadra, arriba a la derecha) y guardarla; eso la persiste en
    // collision_matrices.json bajo su roomId y la aplica al instante.
    //
    // Deben mantenerse IDÉNTICAS (mismas filas/columnas) a las del servidor
    // (MultiplayerInteriores/server.js → BUILDING_MATRIX / LOBBY_MATRIX) mientras no
    // se sustituyan por una matriz exportada desde la app.
    //
    // '#' = pared (no caminable), '.' = caminable.
    //
    // EDIFICIO: 30 col × 20 fil, sólo borde.
    val BUILDING_MATRIX = CollisionMatrix(borderOnly(cols = 30, rows = 20))

    // LOBBY: 30 col × 30 fil, sólo borde.
    val LOBBY_MATRIX = CollisionMatrix(borderOnly(cols = 30, rows = 30))

    private fun borderOnly(cols: Int, rows: Int): List<String> =
        (0 until rows).map { r ->
            buildString {
                for (c in 0 until cols) {
                    val border = r == 0 || r == rows - 1 || c == 0 || c == cols - 1
                    append(if (border) '#' else '.')
                }
            }
        }

    private val lobbyDoors = listOf(
        ZoneDoor(NormRect(0.34f, 0.13f, 0.50f, 0.23f), "za_auditorio", "Auditorio", DoorKind.TO_BUILDING),
        ZoneDoor(NormRect(0.34f, 0.25f, 0.50f, 0.35f), "za_biblioteca", "Biblioteca", DoorKind.TO_BUILDING),
        ZoneDoor(NormRect(0.22f, 0.62f, 0.34f, 0.74f), "za_cafeteria", "Cafetería", DoorKind.TO_BUILDING),
        ZoneDoor(NormRect(0.38f, 0.55f, 0.54f, 0.68f), "za_edificio", "Edificio Principal", DoorKind.TO_BUILDING),
        ZoneDoor(NormRect(0.57f, 0.14f, 0.67f, 0.22f), "za_estacionamiento", "Estacionamiento", DoorKind.TO_BUILDING),
        ZoneDoor(NormRect(0.18f, 0.14f, 0.28f, 0.22f), "za_palapas", "Palapas", DoorKind.TO_BUILDING),
        ZoneDoor(NormRect(0.64f, 0.38f, 0.76f, 0.50f), "za_canchas_futbol", "Canchas de Futbol", DoorKind.TO_BUILDING),
        ZoneDoor(NormRect(0.55f, 0.24f, 0.69f, 0.32f), EXIT_TO_WORLD, "Salir al mapa", DoorKind.TO_WORLD)
    )

    private fun buildingDoors(index: Int): List<ZoneDoor> {

        val next = buildingOrder[(index + 1) % buildingOrder.size]
        val prev = buildingOrder[(index - 1 + buildingOrder.size) % buildingOrder.size]
        return listOf(
            ZoneDoor(NormRect(0.40f, 0.86f, 0.60f, 0.99f), LOBBY_ID, "Volver al Lobby", DoorKind.GENERIC),
            ZoneDoor(NormRect(0.90f, 0.38f, 0.99f, 0.56f), next, "EXIT →", DoorKind.EXIT_NEXT),
            ZoneDoor(NormRect(0.01f, 0.38f, 0.10f, 0.56f), prev, "← EXIT", DoorKind.EXIT_PREV)
        )
    }

    private fun buildingDisplayName(id: String) = when (id) {
        "za_auditorio" -> "Auditorio"
        "za_biblioteca" -> "Biblioteca"
        "za_cafeteria" -> "Cafetería"
        "za_edificio" -> "Edificio Principal"
        "za_estacionamiento" -> "Estacionamiento"
        "za_canchas_futbol" -> "Canchas de Futbol"
        else -> "Palapas"
    }

    val rooms: List<ZombieRoom> = buildList {
        add(
            ZombieRoom(
                id = LOBBY_ID,
                type = ZoneType.LOBBY,
                backgroundAsset = "BUILDINGS/IPN/building_escom.webp",
                displayName = "Campus ESCOM",
                worldWidth = 1700f,
                worldHeight = 2100f,
                zoom = 2.2f,
                playerSpawnFrac = NormPoint(0.62f, 0.28f),
                doors = lobbyDoors,
                zombieCount = 0,
                gridCols = 30,
                collisionMatrix = LOBBY_MATRIX
            )
        )
        buildingOrder.forEachIndexed { i, id ->
            add(
                ZombieRoom(
                    id = id,
                    type = ZoneType.BUILDING,
                    backgroundAsset = "INTERIORS/ESCOM_APOCALYPSE/$id.webp",
                    displayName = buildingDisplayName(id),
                    worldWidth = 1920f,
                    worldHeight = 1080f,
                    zoom = 1.0f,
                    playerSpawnFrac = NormPoint(0.50f, 0.55f),
                    doors = buildingDoors(i),
                    zombieCount = 4 + (i % 3),
                    gridCols = 40,
                    collisionMatrix = BUILDING_MATRIX
                )
            )
        }
        // ─── CAMPUS FES ARAGÓN (Interiores expandible) ───────────────────────
        // FES se añade como un CAMPUS aparte (lobby propio + sus edificios) usando el
        // helper genérico `campusRooms`. Para sumar otra universidad (p. ej. UAM) basta
        // con OTRO addAll(campusRooms(...)) — sin tocar el anillo bespoke de ESCOM.
        // El "Edificio Principal" de FES reusa TEMPORALMENTE el fondo del de ESCOM.
        addAll(
            campusRooms(
                lobbyId = FES_ID,
                lobbyDisplayName = "FES Aragón",
                lobbyBackground = "BUILDINGS/FES_Ar/FES_Arg_int.webp",
                buildings = listOf(
                    BuildingSpec(
                        id = "fes_edificio",
                        displayName = "Edificio Principal",
                        backgroundAsset = "INTERIORS/ESCOM_APOCALYPSE/za_edificio.webp",
                        zombieCount = 4
                    )
                )
            )
        )
        // ─── CADENA LINEAL DEL MODO HISTORIA (ENCB) ──────────────────────────
        // Salas STANDALONE (no es un campus con edificios), tipo LOBBY = zona segura sin
        // zombis. La mano zombi y el fondo apocalíptico están gateados a LOBBY_ID, así que
        // aquí no aplican. Cada sala tiene UNA puerta de AVANCE a la siguiente (waypoint X);
        // NINGUNA tiene puerta de salida al mapa (flujo interno "atrapado"). La ÚLTIMA
        // (encb_lab2) tiene un waypoint final que sale a la narrativa (cómic ENCB_OUTRO)
        // vía EXIT_TO_STORY_OUTRO. worldWidth/Height se sobrescriben en init() con las
        // dimensiones reales del asset.
        // Cada sala lleva un waypoint de AVANCE (→ siguiente) y, salvo el lobby (entrada),
        // un waypoint de RETROCESO (← anterior), para poder ir y venir por la cadena.
        // salon1/lab1/lab2: sus fondos dejan ver al jugador diminuto → playerScaleMul = 3f.
        add(encbStoryRoom(ENCB_LOBBY_ID,  "Lobby ENCB",  "INTERIORS/ENCB/ENCB_lobby.webp",  nextTargetId = ENCB_SALON1_ID))
        add(encbStoryRoom(ENCB_SALON1_ID, "Salón ENCB",  "INTERIORS/ENCB/ENCB_salon1.webp", nextTargetId = ENCB_LAB1_ID, prevTargetId = ENCB_LOBBY_ID, playerScaleMul = 3f))
        add(encbStoryRoom(ENCB_LAB1_ID,   "Lab. ENCB 1", "INTERIORS/ENCB/ENCB_lab1.webp",   nextTargetId = ENCB_LAB2_ID, prevTargetId = ENCB_SALON1_ID, playerScaleMul = 3f))
        add(encbStoryRoom(ENCB_LAB2_ID,   "Lab. ENCB 2", "INTERIORS/ENCB/ENCB_lab2.webp",   nextTargetId = EXIT_TO_STORY_OUTRO, prevTargetId = ENCB_LAB1_ID, playerScaleMul = 3f))
    }

    // ─── MODO HISTORIA ENCB: fábrica de una sala de la cadena lineal ──────────
    // Sala tipo LOBBY (zona segura, sin zombis). Waypoints (puertas interactivas, tecla X):
    //  - `nextTargetId != null` → puerta de AVANCE (→) a la derecha hacia la SIGUIENTE sala
    //    (carga jugable) o el sentinela EXIT_TO_STORY_OUTRO (sale al cómic; lo intercepta goToRoom).
    //  - `prevTargetId != null` → puerta de RETROCESO (←) a la izquierda hacia la sala ANTERIOR.
    // Así se puede ir Y volver por la cadena (el lobby, entrada de la narrativa, NO lleva
    // retroceso). NUNCA se añade una puerta TO_WORLD (sin marcadores de escape al mapa).
    // `playerScaleMul` agranda el sprite del jugador SOLO en esta sala (ENCB_salon1 lo necesita).
    private fun encbStoryRoom(
        id: String,
        displayName: String,
        backgroundAsset: String,
        nextTargetId: String?,
        prevTargetId: String? = null,
        playerScaleMul: Float = 1f
    ): ZombieRoom = ZombieRoom(
        id = id,
        type = ZoneType.LOBBY,
        backgroundAsset = backgroundAsset,
        displayName = displayName,
        worldWidth = 1920f,
        worldHeight = 1080f,
        zoom = 1.0f,
        playerSpawnFrac = NormPoint(0.50f, 0.62f),
        doors = buildList {
            // Avance (→) a la derecha.
            if (nextTargetId != null) {
                add(
                    ZoneDoor(
                        NormRect(0.82f, 0.40f, 0.97f, 0.60f),
                        nextTargetId,
                        "Continuar →",
                        DoorKind.EXIT_NEXT
                    )
                )
            }
            // Retroceso (←) a la izquierda.
            if (prevTargetId != null) {
                add(
                    ZoneDoor(
                        NormRect(0.03f, 0.40f, 0.18f, 0.60f),
                        prevTargetId,
                        "← Regresar",
                        DoorKind.EXIT_PREV
                    )
                )
            }
        },
        zombieCount = 0,
        gridCols = 30,
        playerScaleMul = playerScaleMul,
        collisionMatrix = LOBBY_MATRIX
    )

    // ─── INTERIORES EXPANDIBLE: definición de un campus ───────────────────────
    // Un edificio de un campus nuevo. (id estable, nombre visible, fondo, nº de zombis.)
    data class BuildingSpec(
        val id: String,
        val displayName: String,
        val backgroundAsset: String,
        val zombieCount: Int
    )

    // Genera las salas de un CAMPUS de Interiores: 1 lobby (zona segura, sin zombis) +
    // N edificios (con zombis), con las puertas YA cableadas. Reutilizable para FES, UAM…
    // El lobby tiene una puerta por edificio (auto-distribuidas arriba) + salida al mapa;
    // cada edificio tiene una puerta de regreso a SU lobby (campus-agnóstico). ESCOM NO usa
    // este helper (mantiene su anillo bespoke). Las matrices son border-only y deben
    // coincidir con el servidor (LOBBY 30×30, BUILDING 30×20).
    private fun campusRooms(
        lobbyId: String,
        lobbyDisplayName: String,
        lobbyBackground: String,
        buildings: List<BuildingSpec>
    ): List<ZombieRoom> = buildList {
        val lobbyDoors = buildList {
            buildings.forEachIndexed { i, b ->
                val left = (0.18f + i * 0.22f).coerceAtMost(0.78f)
                add(ZoneDoor(NormRect(left, 0.12f, left + 0.16f, 0.24f), b.id, b.displayName, DoorKind.TO_BUILDING))
            }
            add(ZoneDoor(NormRect(0.42f, 0.86f, 0.58f, 0.98f), EXIT_TO_WORLD, "Salir al mapa", DoorKind.TO_WORLD))
        }
        add(
            ZombieRoom(
                id = lobbyId,
                type = ZoneType.LOBBY,
                backgroundAsset = lobbyBackground,
                displayName = lobbyDisplayName,
                worldWidth = 1920f,
                worldHeight = 1080f,
                zoom = 1.0f,
                playerSpawnFrac = NormPoint(0.50f, 0.55f),
                doors = lobbyDoors,
                zombieCount = 0,
                gridCols = 30,
                collisionMatrix = LOBBY_MATRIX
            )
        )
        buildings.forEach { b ->
            add(
                ZombieRoom(
                    id = b.id,
                    type = ZoneType.BUILDING,
                    backgroundAsset = b.backgroundAsset,
                    displayName = b.displayName,
                    worldWidth = 1920f,
                    worldHeight = 1080f,
                    zoom = 1.0f,
                    playerSpawnFrac = NormPoint(0.50f, 0.55f),
                    doors = listOf(
                        ZoneDoor(NormRect(0.40f, 0.86f, 0.60f, 0.99f), lobbyId, "Volver al Lobby", DoorKind.GENERIC)
                    ),
                    zombieCount = b.zombieCount,
                    gridCols = 40,
                    collisionMatrix = BUILDING_MATRIX
                )
            )
        }
    }

    private val byId = rooms.associateBy { it.id }
    fun roomById(id: String) = byId[id]
    fun indexOfRoom(id: String) = rooms.indexOfFirst { it.id == id }

    @Synchronized
    fun init(context: android.content.Context) {
        rooms.forEach { room ->
            if (room.initAttempted) return@forEach
            room.initAttempted = true
            try {
                context.assets.open(room.backgroundAsset).use { inputStream ->
                    val options = android.graphics.BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    android.graphics.BitmapFactory.decodeStream(inputStream, null, options)
                    if (options.outWidth > 0 && options.outHeight > 0) {
                        room.worldWidth = options.outWidth.toFloat()
                        room.worldHeight = options.outHeight.toFloat()
                        room.dimensionsLoaded = true
                    } else {
                        android.util.Log.w("ZombieRoomCatalog", "Fondo ${room.backgroundAsset} devolvió dimensiones inválidas (W:${options.outWidth}, H:${options.outHeight}). Se usará fallback.")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ZombieRoomCatalog", "No se pudo leer la resolución del fondo ${room.backgroundAsset}. Se usará fallback.", e)
            }
        }
    }
}