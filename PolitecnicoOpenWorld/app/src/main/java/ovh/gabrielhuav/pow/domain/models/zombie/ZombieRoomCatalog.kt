// domain/models/zombie/ZombieRoomCatalog.kt
package ovh.gabrielhuav.pow.domain.models.zombie

object ZombieRoomCatalog {

    const val LOBBY_ID = "lobby_campus"
    const val EXIT_TO_WORLD = "__WORLD__"

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
                    backgroundAsset = "ZOMBIS_MOD/interiores/$id.webp",
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