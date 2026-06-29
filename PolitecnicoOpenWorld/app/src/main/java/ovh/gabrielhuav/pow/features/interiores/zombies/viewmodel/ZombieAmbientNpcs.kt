package ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel

import ovh.gabrielhuav.pow.domain.models.zombie.ZombieRoom
import ovh.gabrielhuav.pow.domain.models.zombie.ZombieRoomCatalog
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerSkin
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

// NPCs AMBIENTALES de interiores (Modo Historia): estudiantes/docentes que deambulan por las
// salas de la ESCOM como escenografia viva. Son LOCALES (offline): se simulan en el cliente, no
// vienen del servidor (eso es `interiorNpcs`). Cada uno tiene su SKIN (sprite completo) y un
// objetivo de caminata; al llegar (o por timeout) elige otro. Sin combate ni colision con el jugador.
// FASE 1: usan skins de estudiante ya existentes para validar el sistema; al tener listos los
// sprites de docentes/IPN se cambia AMBIENT_SKILLS por sala. Ver tambien stepAmbientNpcs.
data class AmbientNpc(
    val id: String,
    val x: Float,
    val y: Float,
    val skin: PlayerSkin,
    val facingRight: Boolean = true,
    val action: PlayerAction = PlayerAction.WALK,
    val targetX: Float = x,
    val targetY: Float = y,
    val retargetAtMs: Long = 0L
)

// Salas donde aparecen NPCs ambientales (de momento, el lobby de la ESCOM = building_escom.webp).
// Ampliable: agrega ids de salas de INTERIORS/ESCOM.
private val AMBIENT_ROOM_IDS: Set<String> = setOf(ZombieRoomCatalog.LOBBY_ID)

// Skins usadas como MODELO de NPC (FASE 1 = estudiantes existentes). Cada NPC toma una al azar.
private val AMBIENT_SKINS: List<PlayerSkin> =
    // Pool del interior de la ESCOM: estudiantes IPN + 1 docente + NPC generico.
    listOf(
        PlayerSkin.IPN_1, PlayerSkin.IPN_2, PlayerSkin.IPN_3, PlayerSkin.IPN_4, PlayerSkin.IPN_5, PlayerSkin.IPN_6,
        PlayerSkin.RND_1, PlayerSkin.DOC_1, PlayerSkin.EST_H1, PlayerSkin.EST_M1
    )

private const val AMBIENT_COUNT = 7
private const val AMBIENT_SPEED = 3.0f          // px por tick (caminar tranquilo)
private const val AMBIENT_RADIUS = 28f          // margen a paredes/bordes
private const val AMBIENT_ARRIVE = 18f          // distancia para considerar "llego" al objetivo
private const val AMBIENT_WAIT_MS = 600L       // pausa (idle) al llegar antes de re-elegir

/** Es caminable el pixel (x,y) en la sala (dentro de bordes y fuera de la matriz de colision)? */
private fun walkable(room: ZombieRoom, x: Float, y: Float): Boolean {
    if (x < AMBIENT_RADIUS || y < AMBIENT_RADIUS ||
        x > room.worldWidth - AMBIENT_RADIUS || y > room.worldHeight - AMBIENT_RADIUS) return false
    return !room.isBlockedPixel(x, y)
}

/** Punto aleatorio caminable de la sala (o null si no encuentra en N intentos). */
private fun randomWalkable(room: ZombieRoom): Pair<Float, Float>? {
    repeat(40) {
        val x = AMBIENT_RADIUS + Random.nextFloat() * (room.worldWidth - 2f * AMBIENT_RADIUS)
        val y = AMBIENT_RADIUS + Random.nextFloat() * (room.worldHeight - 2f * AMBIENT_RADIUS)
        if (walkable(room, x, y)) return x to y
    }
    return null
}

/** Crea los NPCs ambientales de la sala (vacio si no aplica o si es multijugador). */
internal fun ZombieInteriorViewModel.spawnAmbientNpcs(room: ZombieRoom): List<AmbientNpc> {
    if (isMultiplayer || room.id !in AMBIENT_ROOM_IDS || AMBIENT_SKINS.isEmpty()) return emptyList()
    val list = ArrayList<AmbientNpc>(AMBIENT_COUNT)
    repeat(AMBIENT_COUNT) { i ->
        val p = randomWalkable(room) ?: return@repeat
        val t = randomWalkable(room) ?: p
        list.add(
            AmbientNpc(
                id = "amb_$i",
                x = p.first, y = p.second,
                skin = AMBIENT_SKINS[Random.nextInt(AMBIENT_SKINS.size)],
                facingRight = Random.nextBoolean(),
                action = PlayerAction.WALK,
                targetX = t.first, targetY = t.second,
                retargetAtMs = 0L
            )
        )
    }
    return list
}

/** Un paso de simulacion (deambular) de los NPCs ambientales. Devuelve la nueva lista. */
internal fun ZombieInteriorViewModel.stepAmbientNpcs(
    npcs: List<AmbientNpc>, room: ZombieRoom, now: Long
): List<AmbientNpc> {
    if (npcs.isEmpty()) return npcs
    return npcs.map { npc ->
        // Esperando (idle) tras llegar? mantener idle hasta retargetAtMs.
        if (npc.action == PlayerAction.IDLE && now < npc.retargetAtMs) return@map npc
        // Toca elegir nuevo objetivo? (idle terminado, o ya llego al objetivo)
        val distT = hypot(npc.targetX - npc.x, npc.targetY - npc.y)
        if (npc.action == PlayerAction.IDLE || distT <= AMBIENT_ARRIVE) {
            val t = randomWalkable(room)
            return@map if (t == null) npc.copy(action = PlayerAction.IDLE, retargetAtMs = now + AMBIENT_WAIT_MS)
                else npc.copy(action = PlayerAction.WALK, targetX = t.first, targetY = t.second)
        }
        // Mover hacia el objetivo con deslizamiento por eje (como los zombis).
        val dx = npc.targetX - npc.x; val dy = npc.targetY - npc.y
        val d = hypot(dx, dy)
        val nx = if (d > 0.01f) dx / d else 0f
        val ny = if (d > 0.01f) dy / d else 0f
        val tx = npc.x + nx * AMBIENT_SPEED
        val ty = npc.y + ny * AMBIENT_SPEED
        var rx = npc.x; var ry = npc.y
        when {
            walkable(room, tx, ty) -> { rx = tx; ry = ty }
            walkable(room, tx, npc.y) -> rx = tx
            walkable(room, npc.x, ty) -> ry = ty
            else -> return@map npc.copy(action = PlayerAction.IDLE, retargetAtMs = now + AMBIENT_WAIT_MS)
        }
        val arrived = hypot(npc.targetX - rx, npc.targetY - ry) <= AMBIENT_ARRIVE
        npc.copy(
            x = rx, y = ry,
            facingRight = if (abs(nx) > 0.01f) nx >= 0f else npc.facingRight,
            action = if (arrived) PlayerAction.IDLE else PlayerAction.WALK,
            retargetAtMs = if (arrived) now + AMBIENT_WAIT_MS else npc.retargetAtMs
        )
    }
}
