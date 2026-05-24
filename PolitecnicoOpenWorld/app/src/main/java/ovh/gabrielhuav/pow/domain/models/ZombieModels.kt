// domain/models/zombie/ZombieModels.kt
package ovh.gabrielhuav.pow.domain.models.zombie

import java.util.UUID

data class ZombieEntity(
    val id: String = UUID.randomUUID().toString(),
    val x: Float,
    val y: Float,
    val health: Float = 100f,
    val facingRight: Boolean = true,
    val frameIndex: Int = 0,
    val isDying: Boolean = false,
    val lastFrameAdvanceMs: Long = 0L
)

/** Tipo de zona: el lobby (croquis del campus) o un edificio jugable. */
enum class ZoneType { LOBBY, BUILDING }

/**
 * Una zona del minijuego. La topología es hub-and-spoke:
 *  - El LOBBY tiene varias hitboxes (puertas), cada una apunta a un edificio.
 *  - Cada BUILDING tiene UNA hitbox de salida que devuelve al lobby.
 * Así nunca te quedas atorado: cada zona sabe a dónde regresar.
 */
data class ZombieRoom(
    val id: String,
    val type: ZoneType,
    val backgroundAsset: String,
    val displayName: String,
    // Punto de aparición del jugador al entrar a esta zona (normalizado)
    val playerSpawn: NormPoint = NormPoint(0.5f, 0.85f),
    // Puertas: cada hitbox lleva al id de otra zona. En un building suele haber
    // una sola (de regreso al lobby). En el lobby, una por edificio.
    val doors: List<ZoneDoor> = emptyList(),
    // Zombis iniciales de esta zona (el lobby normalmente no tiene)
    val zombieSpawns: List<NormPoint> = emptyList()
)

/** Una puerta: región interactiva que transporta a la zona [targetRoomId]. */
data class ZoneDoor(
    val hitbox: NormRect,
    val targetRoomId: String,
    val label: String = ""   // ej "Auditorio", "Salir al mapa"
)

data class NormPoint(val x: Float, val y: Float)

data class NormRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun contains(px: Float, py: Float): Boolean =
        px in left..right && py in top..bottom

    fun centerX(): Float = (left + right) / 2f
    fun centerY(): Float = (top + bottom) / 2f
}