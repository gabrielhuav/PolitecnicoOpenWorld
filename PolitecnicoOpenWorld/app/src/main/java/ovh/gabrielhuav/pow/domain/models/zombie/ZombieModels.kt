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
    // assets/INTERIORS/ESCOM_APOCALYPSE/interactuables/int_{assetKey}.webp
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

/**
 * Matriz de colisión por sala, en coordenadas FRACCIONARIAS [0,1].
 *
 *  - '#' = no caminable (pared / obstáculo)
 *  - '.' = caminable
 *
 * Se define fraccionaria a propósito: es independiente del tamaño real de la
 * imagen de fondo (que cada dispositivo decodifica con sus propias dimensiones)
 * y coincide carácter por carácter con la matriz replicada en el servidor
 * (server.js). Tanto el jugador local como los remotos y los zombis
 * (autoritativos en el servidor) la respetan.
 *
 * Mapeo: col = floor(fx * numCols), fila = floor(fy * numRows).
 */
class CollisionMatrix(val rows: List<String>) {
    val numRows: Int = rows.size
    val numCols: Int = if (rows.isEmpty()) 0 else rows[0].length

    fun isBlockedFrac(fx: Float, fy: Float): Boolean {
        if (numRows == 0 || numCols == 0) return false
        val c = (fx * numCols).toInt().coerceIn(0, numCols - 1)
        val r = (fy * numRows).toInt().coerceIn(0, numRows - 1)
        return rows[r][c] == '#'
    }
}

data class ZombieRoom(
    val id: String,
    val type: ZoneType,
    val backgroundAsset: String,
    val displayName: String,
    @Volatile var worldWidth: Float = 2000f,
    @Volatile var worldHeight: Float = 2000f,
    val zoom: Float = 2.2f,
    val playerSpawnFrac: NormPoint = NormPoint(0.5f, 0.85f),
    // Puertas/waypoints de la sala. Es VAR para poder sobreescribirlas en
    // caliente desde el Modo Diseñador (igual que la matriz de colisión) y al
    // cargar waypoints.json.
    @Volatile var doors: List<ZoneDoor> = emptyList(),
    val zombieCount: Int = 0,
    // Nº de columnas DESEADO para la rejilla de colisión por defecto de esta
    // sala. Las filas se calculan automáticamente desde el aspect ratio del
    // asset (worldHeight/worldWidth) para que cada celda quede ~cuadrada.
    //  - Valor pequeño  → celdas grandes (trazo grueso, menos precisión).
    //  - Valor grande   → celdas pequeñas (trazo fino, más precisión).
    // null = usar el valor por defecto global.
    val gridCols: Int? = null,
    // Multiplicador de tamaño del SPRITE DEL JUGADOR para esta sala. Algunos fondos
    // (p. ej. ENCB_salon1) están dibujados a una escala que hace ver al jugador
    // diminuto; con este factor se agranda SOLO en esa sala sin tocar el resto.
    // 1f = tamaño normal.
    val playerScaleMul: Float = 1f,
    val collisionGridFrac: List<NormRect> = emptyList(),
    // Matriz de colisión de la sala (lobby/edificio). Es VAR para poder
    // sobreescribirla en caliente desde el Modo Diseñador. null = sin colisiones.
    @Volatile var collisionMatrix: CollisionMatrix? = null,
    @Volatile var dimensionsLoaded: Boolean = false,
    @Volatile var initAttempted: Boolean = false
) {
    /** ¿La celda fraccionaria (fx,fy) está bloqueada por la matriz de la sala? */
    fun isBlockedFrac(fx: Float, fy: Float): Boolean =
        collisionMatrix?.isBlockedFrac(fx, fy) ?: false

    /** ¿El píxel de mundo (x,y) está bloqueado? Convierte a fracción con las dims actuales. */
    fun isBlockedPixel(x: Float, y: Float): Boolean {
        if (worldWidth <= 0f || worldHeight <= 0f) return false
        return isBlockedFrac(x / worldWidth, y / worldHeight)
    }
}

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