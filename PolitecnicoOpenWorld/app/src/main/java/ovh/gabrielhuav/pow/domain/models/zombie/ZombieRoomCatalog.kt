// domain/models/zombie/ZombieRoomCatalog.kt
package ovh.gabrielhuav.pow.domain.models.zombie

/**
 * Lobby (croquis del campus) + 6 edificios encadenados.
 * El minijuego arranca SIEMPRE en el lobby. Desde el lobby entras a un edificio,
 * y desde cada edificio puedes ir al siguiente, al anterior, o volver al lobby.
 */
object ZombieRoomCatalog {

    const val LOBBY_ID = "lobby_campus"
    const val EXIT_TO_WORLD = "__WORLD__"

    private val buildingOrder = listOf(
        "za_auditorio", "za_biblioteca", "za_cafeteria",
        "za_edificio", "za_estacionamiento", "za_palapas"
    )

    private val lobbyDoors = listOf(
        ZoneDoor(NormRect(0.34f, 0.13f, 0.50f, 0.23f), "za_auditorio", "Auditorio", DoorKind.TO_BUILDING),
        ZoneDoor(NormRect(0.22f, 0.38f, 0.34f, 0.50f), "za_biblioteca", "Biblioteca", DoorKind.TO_BUILDING),
        ZoneDoor(NormRect(0.50f, 0.38f, 0.62f, 0.50f), "za_cafeteria", "Cafetería", DoorKind.TO_BUILDING),
        ZoneDoor(NormRect(0.38f, 0.55f, 0.54f, 0.68f), "za_edificio", "Edificio Principal", DoorKind.TO_BUILDING),
        ZoneDoor(NormRect(0.22f, 0.62f, 0.34f, 0.74f), "za_estacionamiento", "Estacionamiento", DoorKind.TO_BUILDING),
        ZoneDoor(NormRect(0.50f, 0.62f, 0.62f, 0.74f), "za_palapas", "Palapas", DoorKind.TO_BUILDING),
        ZoneDoor(NormRect(0.40f, 0.90f, 0.60f, 0.98f), EXIT_TO_WORLD, "Salir al mapa", DoorKind.TO_WORLD)
    )

    private fun buildingDoors(index: Int): List<ZoneDoor> {
        val next = buildingOrder[(index + 1) % buildingOrder.size]
        val prev = buildingOrder[(index - 1 + buildingOrder.size) % buildingOrder.size]
        return listOf(
            ZoneDoor(NormRect(0.88f, 0.42f, 0.99f, 0.58f), next, "EXIT →", DoorKind.EXIT_NEXT),
            ZoneDoor(NormRect(0.01f, 0.42f, 0.12f, 0.58f), prev, "← EXIT", DoorKind.EXIT_PREV),
            ZoneDoor(NormRect(0.42f, 0.90f, 0.58f, 0.99f), LOBBY_ID, "Lobby", DoorKind.GENERIC)
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
                // ── RUTA CORRECTA DEL CROQUIS ──
                backgroundAsset = "BUILDINGS/IPN/building_escom.webp",
                displayName = "Campus ESCOM",
                worldWidth = 1700f,
                worldHeight = 2100f,
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
                    playerSpawnFrac = NormPoint(0.50f, 0.80f),
                    doors = buildingDoors(i),
                    zombieCount = 4 + (i % 3)
                )
            )
        }
    }

    private val byId = rooms.associateBy { it.id }
    fun roomById(id: String) = byId[id]
    fun indexOfRoom(id: String) = rooms.indexOfFirst { it.id == id }
}