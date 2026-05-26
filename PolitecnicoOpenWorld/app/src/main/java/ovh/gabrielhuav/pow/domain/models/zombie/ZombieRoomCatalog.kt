// domain/models/zombie/ZombieRoomCatalog.kt
package ovh.gabrielhuav.pow.domain.models.zombie

object ZombieRoomCatalog {

    const val LOBBY_ID = "lobby_campus"
    const val EXIT_TO_WORLD = "__WORLD__"

    private val buildingOrder = listOf(
        "za_auditorio", "za_biblioteca", "za_cafeteria",
        "za_edificio", "za_estacionamiento", "za_palapas"
    )


    // Posiciones ORIGINALES (antes del intercambio):
    //   auditorio       (0.34, 0.13, 0.50, 0.23)
    //   biblioteca      (0.22, 0.38, 0.34, 0.50)
    //   cafeteria       (0.50, 0.38, 0.62, 0.50)
    //   edificio        (0.38, 0.55, 0.54, 0.68)
    //   estacionamiento (0.22, 0.62, 0.34, 0.74)
    //   palapas         (0.50, 0.62, 0.62, 0.74)
    //
    // Intercambios pedidos:
    //   • cafeteria  ↔ estacionamiento
    //   • biblioteca ↔ palapas, y luego biblioteca se acerca al auditorio.
    private val lobbyDoors = listOf(
        ZoneDoor(NormRect(0.34f, 0.13f, 0.50f, 0.23f), "za_auditorio", "Auditorio", DoorKind.TO_BUILDING),

        // BIBLIOTECA: Misma columna que el Auditorio (X: 0.34 a 0.50),
        // pero desplazada hacia abajo (Y: 0.25 a 0.35).
        ZoneDoor(NormRect(0.34f, 0.25f, 0.50f, 0.35f), "za_biblioteca", "Biblioteca", DoorKind.TO_BUILDING),

        // CAFETERIA: toma la posición que tenía ESTACIONAMIENTO.
        ZoneDoor(NormRect(0.22f, 0.62f, 0.34f, 0.74f), "za_cafeteria", "Cafetería", DoorKind.TO_BUILDING),

        ZoneDoor(NormRect(0.38f, 0.55f, 0.54f, 0.68f), "za_edificio", "Edificio Principal", DoorKind.TO_BUILDING),

        // ESTACIONAMIENTO: Ubicado en el camión blanco (Derecha superior). Movido aún más arriba.
        ZoneDoor(NormRect(0.57f, 0.14f, 0.67f, 0.22f), "za_estacionamiento", "Estacionamiento", DoorKind.TO_BUILDING),

        // PALAPAS: Ubicado en las palapas (Izquierda superior). Movido aún más arriba.
        ZoneDoor(NormRect(0.18f, 0.14f, 0.28f, 0.22f), "za_palapas", "Palapas", DoorKind.TO_BUILDING),

        ZoneDoor(NormRect(0.40f, 0.90f, 0.60f, 0.98f), EXIT_TO_WORLD, "Salir al mapa", DoorKind.TO_WORLD)
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
                playerSpawnFrac = NormPoint(0.50f, 0.86f),
                doors = lobbyDoors,
                zombieCount = 0
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
                    // ── REQUERIMIENTO 3: zoom reducido 1.3f → 1.0f ──
                    // 1.0f = el "cover" llena la pantalla sin recorte extra,
                    // dando el máximo campo de visión sin deformar la imagen.
                    zoom = 1.0f,
                    // Spawn movido lejos de la puerta-lobby (que está en y≈0.86-0.99)
                    // para que el jugador NO aparezca encima de la salida. Esto,
                    // junto al diálogo de confirmación, elimina las salidas accidentales.
                    playerSpawnFrac = NormPoint(0.50f, 0.55f),
                    doors = buildingDoors(i),
                    zombieCount = 4 + (i % 3)
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