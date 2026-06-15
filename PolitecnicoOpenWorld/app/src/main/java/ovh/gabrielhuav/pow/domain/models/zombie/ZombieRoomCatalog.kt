// domain/models/zombie/ZombieRoomCatalog.kt
package ovh.gabrielhuav.pow.domain.models.zombie

object ZombieRoomCatalog {

    const val LOBBY_ID = "lobby_campus"
    const val V9_LOBBY_ID = "voca9"
    const val EXIT_TO_WORLD = "__WORLD__"

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
