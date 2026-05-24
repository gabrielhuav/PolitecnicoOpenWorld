// domain/models/zombie/ZombieRoomCatalog.kt
package ovh.gabrielhuav.pow.domain.models.zombie

import ovh.gabrielhuav.pow.features.interior.viewmodel.CollisionGrid

/**
 * Topología hub-and-spoke:
 *
 *      [Auditorio]   [Biblioteca]   [Cafetería]
 *            \            |            /
 *             \           |           /
 *              +------- LOBBY -------+   ──► (salir al mapa principal)
 *             /           |           \
 *            /            |            \
 *   [Estacionamiento]  [Edificio]   [Palapas]
 *
 * - Desde el LOBBY (croquis del campus building_escom.webp) entras a cualquier
 *   edificio cruzando su puerta, o sales al mapa con la puerta "__WORLD__".
 * - Desde cada EDIFICIO, su única puerta te devuelve al LOBBY.
 * - Nunca te quedas atorado: toda zona tiene al menos una puerta de retorno.
 */
object ZombieRoomCatalog {

    const val LOBBY_ID = "lobby_campus"
    const val EXIT_TO_WORLD = "__WORLD__"

    // ─── PUERTAS DEL LOBBY ─────────────────────────────────
    // Posiciones normalizadas sobre el croquis building_escom.webp. El edificio
    // principal forma una "H/cruz" vertical en el centro; estas hitboxes caen
    // sobre las distintas alas. Ajusta con debugHitboxes=true.
    private val lobbyDoors = listOf(
        ZoneDoor(NormRect(0.34f, 0.13f, 0.50f, 0.23f), "za_auditorio",       "Auditorio"),
        ZoneDoor(NormRect(0.22f, 0.38f, 0.34f, 0.50f), "za_biblioteca",      "Biblioteca"),
        ZoneDoor(NormRect(0.50f, 0.38f, 0.62f, 0.50f), "za_cafeteria",       "Cafetería"),
        ZoneDoor(NormRect(0.38f, 0.55f, 0.54f, 0.68f), "za_edificio",        "Edificio Principal"),
        ZoneDoor(NormRect(0.22f, 0.62f, 0.34f, 0.74f), "za_estacionamiento", "Estacionamiento"),
        ZoneDoor(NormRect(0.50f, 0.62f, 0.62f, 0.74f), "za_palapas",         "Palapas"),
        // Puerta de salida al mapa principal (borde inferior, sobre la entrada
        // "CAMPUS ESCOM (IPN)" del croquis)
        ZoneDoor(NormRect(0.40f, 0.90f, 0.60f, 0.98f), EXIT_TO_WORLD,        "Salir al mapa")
    )

    // Hitbox estándar de regreso al lobby para los edificios
    private val backToLobbyDoor = ZoneDoor(
        NormRect(0.82f, 0.40f, 0.98f, 0.60f), LOBBY_ID, "Volver al Lobby"
    )

    val rooms: List<ZombieRoom> = listOf(
        // ─── EL LOBBY (HUB) ─────────────────────────────────
        ZombieRoom(
            id = LOBBY_ID,
            type = ZoneType.LOBBY,
            backgroundAsset = "ZOMBIS_MOD/interiores/building_escom.webp",
            displayName = "Campus ESCOM",
            playerSpawn = NormPoint(0.50f, 0.86f),  // cerca de la entrada inferior
            doors = lobbyDoors,
            zombieSpawns = emptyList()              // el hub es seguro
        ),

        // ─── EDIFICIOS (SPOKES) ─────────────────────────────
        ZombieRoom(
            id = "za_auditorio",
            type = ZoneType.BUILDING,
            backgroundAsset = "ZOMBIS_MOD/interiores/za_auditorio.webp",
            displayName = "Auditorio",
            playerSpawn = NormPoint(0.15f, 0.50f),
            doors = listOf(backToLobbyDoor),
            zombieSpawns = listOf(NormPoint(0.6f, 0.3f), NormPoint(0.7f, 0.7f), NormPoint(0.5f, 0.5f))
        ),
        ZombieRoom(
            id = "za_biblioteca",
            type = ZoneType.BUILDING,
            backgroundAsset = "ZOMBIS_MOD/interiores/za_biblioteca.webp",
            displayName = "Biblioteca",
            playerSpawn = NormPoint(0.15f, 0.50f),
            doors = listOf(backToLobbyDoor),
            zombieSpawns = listOf(NormPoint(0.55f, 0.35f), NormPoint(0.75f, 0.55f), NormPoint(0.4f, 0.7f))
        ),
        ZombieRoom(
            id = "za_cafeteria",
            type = ZoneType.BUILDING,
            backgroundAsset = "ZOMBIS_MOD/interiores/za_cafeteria.webp",
            displayName = "Cafetería",
            playerSpawn = NormPoint(0.15f, 0.50f),
            doors = listOf(backToLobbyDoor),
            zombieSpawns = listOf(NormPoint(0.6f, 0.4f), NormPoint(0.5f, 0.6f))
        ),
        ZombieRoom(
            id = "za_edificio",
            type = ZoneType.BUILDING,
            backgroundAsset = "ZOMBIS_MOD/interiores/za_edificio.webp",
            displayName = "Edificio Principal",
            playerSpawn = NormPoint(0.15f, 0.50f),
            doors = listOf(backToLobbyDoor),
            zombieSpawns = listOf(NormPoint(0.6f, 0.3f), NormPoint(0.7f, 0.6f), NormPoint(0.45f, 0.5f), NormPoint(0.55f, 0.75f))
        ),
        ZombieRoom(
            id = "za_estacionamiento",
            type = ZoneType.BUILDING,
            backgroundAsset = "ZOMBIS_MOD/interiores/za_estacionamiento.webp",
            displayName = "Estacionamiento",
            playerSpawn = NormPoint(0.15f, 0.50f),
            doors = listOf(backToLobbyDoor),
            zombieSpawns = listOf(NormPoint(0.6f, 0.4f), NormPoint(0.75f, 0.7f))
        ),
        ZombieRoom(
            id = "za_palapas",
            type = ZoneType.BUILDING,
            backgroundAsset = "ZOMBIS_MOD/interiores/za_palapas.webp",
            displayName = "Palapas",
            playerSpawn = NormPoint(0.15f, 0.50f),
            doors = listOf(backToLobbyDoor),
            zombieSpawns = listOf(NormPoint(0.6f, 0.5f), NormPoint(0.5f, 0.3f))
        )
    )

    private val roomsById: Map<String, ZombieRoom> = rooms.associateBy { it.id }
    fun roomById(id: String): ZombieRoom? = roomsById[id]
    fun indexOfRoom(id: String): Int = rooms.indexOfFirst { it.id == id }

    /**
     * Matriz de colisión por zona (mismo orden que [rooms]).
     * - El lobby es totalmente caminable (sin borde) para poder llegar a las
     *   puertas en cualquier extremo del croquis.
     * - Los edificios usan borde de paredes con boquete abierto sobre cada puerta.
     */
    val collisionGrids: List<CollisionGrid> = rooms.map { room ->
        when (room.type) {
            ZoneType.LOBBY -> fullyWalkableGrid()
            ZoneType.BUILDING -> gridWithDoorOpenings(room.doors.map { it.hitbox })
        }
    }

    private fun fullyWalkableGrid(): CollisionGrid {
        val g = Array(CollisionGrid.ROWS) { IntArray(CollisionGrid.COLS) { 1 } }
        return CollisionGrid(g)
    }

    private fun gridWithDoorOpenings(doorHitboxes: List<NormRect>): CollisionGrid {
        val rows = CollisionGrid.ROWS
        val cols = CollisionGrid.COLS
        val g = Array(rows) { row ->
            IntArray(cols) { col ->
                if (row == 0 || row == rows - 1 || col == 0 || col == cols - 1) 0 else 1
            }
        }
        // Abrir boquete caminable sobre cada puerta para poder alcanzarla
        for (hb in doorHitboxes) {
            val cStart = (hb.left * cols).toInt().coerceIn(0, cols - 1)
            val cEnd = (hb.right * cols).toInt().coerceIn(0, cols - 1)
            val rStart = (hb.top * rows).toInt().coerceIn(0, rows - 1)
            val rEnd = (hb.bottom * rows).toInt().coerceIn(0, rows - 1)
            for (r in rStart..rEnd) for (c in cStart..cEnd) g[r][c] = 1
        }
        return CollisionGrid(g)
    }
}