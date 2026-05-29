package ovh.gabrielhuav.pow.domain.models.zombie

import java.util.UUID

enum class ZombieType { NORMAL, STALKER }

data class ZombieEntity(
    val id: String = UUID.randomUUID().toString(),
    val x: Float,
    val y: Float,
    val health: Float = 100f,
    val maxHealth: Float = 100f,
    val facingRight: Boolean = true,
    val frameIndex: Int = 0,
    val isDying: Boolean = false,
    val lastFrameAdvanceMs: Long = 0L,
    val lastDamageToPlayerMs: Long = 0L,
    val isLootCarrier: Boolean = false,
    val type: ZombieType = ZombieType.NORMAL,
    val isAttacking: Boolean = false
)

/**
 * Tipos de efecto que puede soltar un zombi al morir.
 * BUFFS (ayudan al jugador) y DEBUFFS/TRAMPAS (perjudican).
 */
enum class SkillEffect(
    val displayName: String,
    val isTrap: Boolean,
    val durationMs: Long,
    // Nombre usado para construir la ruta del asset:
    // assets/ZOMBIS_MOD/interactuables/int_{assetKey}.webp
    val assetKey: String
) {
    CURA_TOTAL("Cura Total", isTrap = false, durationMs = 0L, assetKey = "cura_total"),
    RELOJ_ARENA("Reloj de Arena", isTrap = false, durationMs = 8000L, assetKey = "reloj_arena"),
    ADRENALINA_ZOMBI("Adrenalina Zombi", isTrap = true, durationMs = 7000L, assetKey = "adrenalina_zombi"),
    FURIA_ZOMBI("Furia Zombi", isTrap = true, durationMs = 7000L, assetKey = "furia_zombi"),
    DEBILIDAD_ZOMBI("Debilidad Zombi", isTrap = false, durationMs = 8000L, assetKey = "debilidad_zombi"),
    FUERZA_BRUTA("Fuerza Bruta", isTrap = false, durationMs = 9000L, assetKey = "fuerza_bruta")
}

/**
 * Objeto en el suelo soltado por un zombi. Ahora opcionalmente lleva un
 * SkillEffect. Si effect == null se comporta como loot decorativo antiguo.
 */
data class SkillItem(
    val id: String = UUID.randomUUID().toString(),
    val x: Float,
    val y: Float,
    val effect: SkillEffect,
    val collected: Boolean = false
)

/** Efecto activo con su instante de expiración (para el game loop). */
data class ActiveEffect(
    val effect: SkillEffect,
    val expiresAtMs: Long
)

data class Projectile(
    val id: String = UUID.randomUUID().toString(),
    val x: Float,
    val y: Float,
    val dirX: Float,
    val dirY: Float,
    val bornAtMs: Long
)

enum class CombatMode { MELEE, RANGED }

enum class ZoneType { LOBBY, BUILDING }

data class ZombieRoom(
    val id: String,
    val type: ZoneType,
    val backgroundAsset: String,
    val displayName: String,
    @Volatile var worldWidth: Float = 2000f,
    @Volatile var worldHeight: Float = 2000f,
    val zoom: Float = 2.2f,
    val playerSpawnFrac: NormPoint = NormPoint(0.5f, 0.85f),
    val doors: List<ZoneDoor> = emptyList(),
    val zombieCount: Int = 0,
    val collisionGridFrac: List<NormRect> = emptyList(),
    @Volatile var dimensionsLoaded: Boolean = false,
    @Volatile var initAttempted: Boolean = false
)

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