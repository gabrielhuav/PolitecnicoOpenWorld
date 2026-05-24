// domain/models/zombie/ZombieModels.kt
package ovh.gabrielhuav.pow.domain.models.zombie

import java.util.UUID

/**
 * Coordenadas en PÍXELES DEL MUNDO (de la imagen del mapa a tamaño base, sin
 * zoom). Esto permite que la cámara, el clamp y el zoom funcionen de forma
 * uniforme: la capa del mundo se escala y traslada como un todo.
 */
data class ZombieEntity(
    val id: String = UUID.randomUUID().toString(),
    val x: Float,                 // px mundo
    val y: Float,                 // px mundo
    val health: Float = 100f,
    val maxHealth: Float = 100f,
    val facingRight: Boolean = true,
    val frameIndex: Int = 0,
    val isDying: Boolean = false,
    val lastFrameAdvanceMs: Long = 0L,
    // Cooldown de daño POR ZOMBI: timestamp del último golpe que dio al jugador
    val lastDamageToPlayerMs: Long = 0L,
    // Loot: si es true, al morir suelta un InteractableItem
    val isLootCarrier: Boolean = false
)

/** Objeto en el suelo, soltado por un zombi portador de loot. */
data class InteractableItem(
    val id: String = UUID.randomUUID().toString(),
    val x: Float,                 // px mundo (coordenada exacta de la muerte)
    val y: Float,                 // px mundo
    val assetPath: String,
    val name: String,
    val collected: Boolean = false
)

enum class ZoneType { LOBBY, BUILDING }

/**
 * Zona del minijuego. Topología hub-and-spoke:
 *  - LOBBY: croquis del campus, con 6 puertas a edificios + 1 puerta al mapa.
 *  - BUILDING: dos puertas "EXIT" (siguiente / anterior) + zombis.
 *
 * worldWidth/worldHeight definen el tamaño base de la imagen en px de mundo.
 * Las hitboxes y spawns se expresan como fracción [0,1] y se convierten a px
 * al cargar, para no acoplar el catálogo a una resolución concreta.
 */
data class ZombieRoom(
    val id: String,
    val type: ZoneType,
    val backgroundAsset: String,
    val displayName: String,
    val worldWidth: Float = 2000f,
    val worldHeight: Float = 2000f,
    val playerSpawnFrac: NormPoint = NormPoint(0.5f, 0.85f),
    val doors: List<ZoneDoor> = emptyList(),
    val zombieCount: Int = 0,          // cuántos zombis spawnean por radio
    val collisionGridFrac: List<NormRect> = emptyList() // zonas NO caminables (frac)
)

/** Puerta interactiva. targetRoomId == EXIT_TO_WORLD sale al mapa principal. */
data class ZoneDoor(
    val hitboxFrac: NormRect,
    val targetRoomId: String,
    val label: String = "",
    val kind: DoorKind = DoorKind.GENERIC
)

enum class DoorKind { GENERIC, EXIT_NEXT, EXIT_PREV, TO_BUILDING, TO_WORLD }

data class NormPoint(val x: Float, val y: Float)

data class NormRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun toWorldRect(w: Float, h: Float) =
        WorldRect(left * w, top * h, right * w, bottom * h)
    fun centerXFrac() = (left + right) / 2f
    fun centerYFrac() = (top + bottom) / 2f
}

data class WorldRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun contains(px: Float, py: Float) = px in left..right && py in top..bottom
    fun centerX() = (left + right) / 2f
    fun centerY() = (top + bottom) / 2f
}