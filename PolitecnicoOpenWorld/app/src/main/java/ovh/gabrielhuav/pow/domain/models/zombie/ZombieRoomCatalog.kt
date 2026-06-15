// domain/models/zombie/ZombieRoomCatalog.kt
package ovh.gabrielhuav.pow.domain.models.zombie

object ZombieRoomCatalog {

    const val LOBBY_ID = "lobby_campus"
    const val V9_LOBBY_ID = "voca9"
    const val EXIT_TO_WORLD = "__WORLD__"
    // MODO HISTORIA: targetRoomId "sentinela" — una puerta con este destino NO carga otra
    // sala, sino que dispara la salida del motor de interiores hacia la narrativa (cómic
    // ENCB_OUTRO). Lo intercepta ZombieGameViewModel.goToRoom (igual que EXIT_TO_WORLD).
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

    private val escomBuildingOrder = listOf(
        "za_auditorio", "za_biblioteca", "za_cafeteria",
        "za_edificio", "za_estacionamiento", "za_palapas", "za_canchas_futbol"
    )

    // Ajustado a las fotos que tienes en tus capturas:
    private val v9BuildingOrder = listOf(
        "v9_edificio_a", "v9a_cafeteria", "v9a_gimnasio"
    )

    val BUILDING_MATRIX = CollisionMatrix(borderOnly(cols = 30, rows = 20))
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

    private val escomLobbyDoors = listOf(
        ZoneDoor(NormRect(0.34f, 0.13f, 0.50f, 0.23f), "za_auditorio", "Auditorio", DoorKind.TO_BUILDING),
        ZoneDoor(NormRect(0.34f, 0.25f, 0.50f, 0.35f), "za_biblioteca", "Biblioteca", DoorKind.TO_BUILDING),
        ZoneDoor(NormRect(0.22f, 0.62f, 0.34f, 0.74f), "za_cafeteria", "Cafetería", DoorKind.TO_BUILDING),
        ZoneDoor(NormRect(0.38f, 0.55f, 0.54f, 0.68f), "za_edificio", "Edificio Principal", DoorKind.TO_BUILDING),
        ZoneDoor(NormRect(0.57f, 0.14f, 0.67f, 0.22f), "za_estacionamiento", "Estacionamiento", DoorKind.TO_BUILDING),
        ZoneDoor(NormRect(0.18f, 0.14f, 0.28f, 0.22f), "za_palapas", "Palapas", DoorKind.TO_BUILDING),
        ZoneDoor(NormRect(0.64f, 0.38f, 0.76f, 0.50f), "za_canchas_futbol", "Canchas de Futbol", DoorKind.TO_BUILDING),
        ZoneDoor(NormRect(0.55f, 0.24f, 0.69f, 0.32f), EXIT_TO_WORLD, "Salir al mapa", DoorKind.TO_WORLD)
    )

    private val v9LobbyDoors = listOf(
        ZoneDoor(NormRect(0.10f, 0.10f, 0.25f, 0.25f), "v9_edificio_a", "Edificio A", DoorKind.TO_BUILDING),
        ZoneDoor(NormRect(0.30f, 0.10f, 0.45f, 0.25f), "v9a_cafeteria", "Cafetería V9", DoorKind.TO_BUILDING),
        ZoneDoor(NormRect(0.10f, 0.40f, 0.25f, 0.55f), "v9a_gimnasio", "Gimnasio", DoorKind.TO_BUILDING),
        ZoneDoor(NormRect(0.40f, 0.80f, 0.60f, 0.95f), EXIT_TO_WORLD, "Regresar a la calle", DoorKind.TO_WORLD)
    )

    private fun buildingDoors(id: String, order: List<String>, lobbyId: String): List<ZoneDoor> {
        val index = order.indexOf(id)
        if (index == -1) return emptyList()
        val next = order[(index + 1) % order.size]
        val prev = order[(index - 1 + order.size) % order.size]
        return listOf(
            ZoneDoor(NormRect(0.40f, 0.86f, 0.60f, 0.99f), lobbyId, "Volver al Patio", DoorKind.GENERIC),
            ZoneDoor(NormRect(0.90f, 0.38f, 0.99f, 0.56f), next, "SIGUIENTE →", DoorKind.EXIT_NEXT),
            ZoneDoor(NormRect(0.01f, 0.38f, 0.10f, 0.56f), prev, "← ANTERIOR", DoorKind.EXIT_PREV)
        )
    }

    private fun buildingDisplayName(id: String) = when (id) {
        "voca9" -> "Plaza Principal V9"
        "v9_edificio_a" -> "Edificio A"
        "v9a_cafeteria" -> "Cafetería V9"
        "v9a_gimnasio" -> "Gimnasio"
        "za_auditorio" -> "Auditorio"
        "za_biblioteca" -> "Biblioteca"
        "za_cafeteria" -> "Cafetería ESCOM"
        "za_edificio" -> "Edificio Principal"
        "za_estacionamiento" -> "Estacionamiento"
        "za_canchas_futbol" -> "Canchas de Futbol"
        else -> "Lugar Desconocido"
    }

    val rooms: List<ZombieRoom> = buildList {
        // --- ESCOM ---
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
                doors = escomLobbyDoors,
                zombieCount = 0,
                gridCols = 30,
                collisionMatrix = LOBBY_MATRIX
            )
        )
        escomBuildingOrder.forEach { id ->
            add(
                ZombieRoom(
                    id = id,
                    type = ZoneType.BUILDING,
                    backgroundAsset = "ZOMBIS_MOD/interiores/$id.webp",
                    displayName = buildingDisplayName(id),
                    worldWidth = 1920f,
                    worldHeight = 1080f,
                    zoom = 1.0f,
                    playerSpawnFrac = NormPoint(0.50f, 0.55f),
                    doors = buildingDoors(id, escomBuildingOrder, LOBBY_ID),
                    zombieCount = 5,
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
                        backgroundAsset = "ZOMBIES_MOD/interiors/za_edificio.webp",
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
        add(encbStoryRoom(ENCB_LOBBY_ID,  "Lobby ENCB",  "INTERIORS/ENCB/ENCB_lobby.webp",  ENCB_SALON1_ID))
        add(encbStoryRoom(ENCB_SALON1_ID, "Salón ENCB",  "INTERIORS/ENCB/ENCB_salon1.webp", ENCB_LAB1_ID))
        add(encbStoryRoom(ENCB_LAB1_ID,   "Lab. ENCB 1", "INTERIORS/ENCB/ENCB_lab1.webp",   ENCB_LAB2_ID))
        add(encbStoryRoom(ENCB_LAB2_ID,   "Lab. ENCB 2", "INTERIORS/ENCB/ENCB_lab2.webp",   EXIT_TO_STORY_OUTRO))

        add(
            ZombieRoom(
                id = V9_LOBBY_ID,
                type = ZoneType.LOBBY,
                backgroundAsset = "BUILDINGS/IPN/building_voca9.webp",
                displayName = "Plaza Lázaro Cárdenas - V9",
                worldWidth = 1460f,
                worldHeight = 1600f,
                zoom = 2.0f,
                playerSpawnFrac = NormPoint(0.50f, 0.80f),
                doors = v9LobbyDoors,
                zombieCount = 0,
                gridCols = 30,
                collisionMatrix = LOBBY_MATRIX
            )
        )
        v9BuildingOrder.forEach { id ->
            add(
                ZombieRoom(
                    id = id,
                    type = ZoneType.BUILDING,
                    backgroundAsset = "ZOMBIS_MOD/interiores/$id.webp",
                    displayName = buildingDisplayName(id),
                    worldWidth = 1920f,
                    worldHeight = 1080f,
                    zoom = 1.0f,
                    playerSpawnFrac = NormPoint(0.50f, 0.55f),
                    doors = buildingDoors(id, v9BuildingOrder, V9_LOBBY_ID),
                    zombieCount = 6,
                    gridCols = 40,
                    collisionMatrix = BUILDING_MATRIX
                )
            )
        }
    }

    // ─── MODO HISTORIA ENCB: fábrica de una sala de la cadena lineal ──────────
    // Sala tipo LOBBY (zona segura, sin zombis). Si `nextTargetId != null` agrega UNA puerta
    // de AVANCE (waypoint interactivo, tecla X) centrada arriba cuyo `targetRoomId` es
    // `nextTargetId`: puede ser la SIGUIENTE sala (carga jugable) o el sentinela
    // EXIT_TO_STORY_OUTRO (sale al cómic; lo intercepta goToRoom). Si es null, la sala queda
    // SIN puertas. No se añade ninguna puerta TO_WORLD (sin marcadores de escape al mapa).
    private fun encbStoryRoom(
        id: String,
        displayName: String,
        backgroundAsset: String,
        nextTargetId: String?
    ): ZombieRoom = ZombieRoom(
        id = id,
        type = ZoneType.LOBBY,
        backgroundAsset = backgroundAsset,
        displayName = displayName,
        worldWidth = 1920f,
        worldHeight = 1080f,
        zoom = 1.0f,
        playerSpawnFrac = NormPoint(0.50f, 0.62f),
        doors = if (nextTargetId == null) emptyList()
        else listOf(
            ZoneDoor(
                NormRect(0.42f, 0.12f, 0.58f, 0.26f),
                nextTargetId,
                "Continuar",
                DoorKind.TO_BUILDING
            )
        ),
        zombieCount = 0,
        gridCols = 30,
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
                    gridCols = 30,
                    collisionMatrix = BUILDING_MATRIX
                )
            )
        }
    }

    private val byId = rooms.associateBy { it.id }
    fun roomById(id: String) = byId[id]
    fun indexOfRoom(id: String) = rooms.indexOfFirst { it.id == id }

    fun getLobbyForRoom(roomId: String): String {
        return if (v9BuildingOrder.contains(roomId) || roomId == V9_LOBBY_ID) V9_LOBBY_ID
        else LOBBY_ID
    }

    @Synchronized
    fun init(context: android.content.Context) {
        rooms.forEach { room ->
            if (room.initAttempted) return@forEach
            room.initAttempted = true
            try {
                android.util.Log.d("ZombieRoomCatalog", "Iniciando sala: ${room.id} con asset: ${room.backgroundAsset}")
                context.assets.open(room.backgroundAsset).use { inputStream ->
                    val options = android.graphics.BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    android.graphics.BitmapFactory.decodeStream(inputStream, null, options)
                    if (options.outWidth > 0 && options.outHeight > 0) {
                        room.worldWidth = options.outWidth.toFloat()
                        room.worldHeight = options.outHeight.toFloat()
                        room.dimensionsLoaded = true
                        android.util.Log.d("ZombieRoomCatalog", "Resolución cargada: ${room.worldWidth}x${room.worldHeight}")
                    } else {
                        android.util.Log.w("ZombieRoomCatalog", "Fondo ${room.backgroundAsset} devolvió dimensiones inválidas. Usando fallback 1920x1080.")
                        room.worldWidth = 1920f
                        room.worldHeight = 1080f
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ZombieRoomCatalog", "No se pudo leer la resolución del fondo ${room.backgroundAsset}. Usando fallback 1920x1080.", e)
                room.worldWidth = 1920f
                room.worldHeight = 1080f
            }
        }
    }
}
