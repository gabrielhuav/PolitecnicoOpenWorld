package ovh.gabrielhuav.pow.domain.models.ai

import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.Npc
import ovh.gabrielhuav.pow.domain.models.NpcType
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * POLICÍA DE LA CAMPAÑA (Modo Historia · Misión 1, escolta a la ESCOM).
 *
 * Kotlin puro (como [PoliceManager] y [PrankedyManager]). Es una clase **independiente** del
 * sistema de nivel de búsqueda del MUNDO LIBRE ([PoliceManager] / `runPoliceTick`) para que sus
 * comportamientos NO choquen: aquí no hay estrellas dinámicas, ni patrullas, ni disparos, ni
 * carjack. Son **2 policías a PIE** que **SIGUEN al jugador a una distancia considerable y
 * DESPACIO** (escolta narrativa). No atacan; solo acompañan/persiguen a pie.
 *
 * Todo su movimiento se ajusta (snap) a la calle vía el lambda `snap` que pasa el ViewModel,
 * igual que el resto de entidades, así no atraviesan edificios.
 */
class CampaignEscortPolice {

    companion object {
        const val COP_COUNT = 2
        const val SPAWN_BEHIND = 0.0006     // ~65 m: aparecen DETRÁS del jugador
        const val FOLLOW_DISTANCE = 0.00045 // ~50 m: distancia que MANTIENEN (no se acercan más)
        const val RESUME_BAND = 0.00012     // ~13 m de histéresis: evita el tembleque en el borde
        const val COP_SPEED = 0.0000020     // por tick: LENTOS (no te alcanzan a pie)
    }

    // IDs FIJOS → como mucho 2 unidades; nunca se multiplican.
    private val units = ConcurrentHashMap<String, Npc>()

    fun activeUnits(): List<Npc> = units.values.toList()
    fun isActive(): Boolean = units.isNotEmpty()
    fun clear() { units.clear() }

    /** Crea los 2 policías DETRÁS del jugador (en abanico), sobre la calle si hay `snap`. */
    fun spawn(playerLat: Double, playerLon: Double, snap: ((GeoPoint) -> GeoPoint)? = null) {
        units.clear()
        for (i in 0 until COP_COUNT) {
            // Detrás del jugador (dirección PI), abierto en abanico para que no se solapen.
            val ang = Math.PI + (if (i == 0) -0.30 else 0.30)
            val raw = GeoPoint(
                playerLat + sin(ang) * SPAWN_BEHIND,
                playerLon + cos(ang) * SPAWN_BEHIND
            )
            val placed = snap?.invoke(raw) ?: raw
            val id = "CAMPAIGN_COP_$i"
            units[id] = Npc(
                id = id,
                type = NpcType.POLICE_COP,
                location = placed,
                speed = COP_SPEED,
                isRemote = false,
                isMoving = true,
                policeDisembarked = true,   // ya está "a pie" (no pertenece a ninguna patrulla)
                policeCanShoot = false       // NUNCA dispara: es escolta narrativa
            )
        }
    }

    /**
     * Un ciclo de seguimiento. Cada policía:
     *  - si está MÁS LEJOS que [FOLLOW_DISTANCE]+banda → avanza DESPACIO hacia el jugador (snap a calle);
     *  - si ya está dentro de la distancia → se queda quieto (MANTIENE la distancia considerable).
     */
    fun tick(playerLat: Double, playerLon: Double, snap: ((GeoPoint) -> GeoPoint)? = null) {
        for (u in units.values.toList()) {
            val d = dist(u.location.latitude, u.location.longitude, playerLat, playerLon)
            if (d > FOLLOW_DISTANCE + RESUME_BAND) {
                val a = atan2(playerLat - u.location.latitude, playerLon - u.location.longitude)
                val raw = GeoPoint(
                    u.location.latitude + sin(a) * COP_SPEED,
                    u.location.longitude + cos(a) * COP_SPEED
                )
                val placed = snap?.invoke(raw) ?: raw
                val mLat = placed.latitude - u.location.latitude
                val mLon = placed.longitude - u.location.longitude
                val moved = mLat * mLat + mLon * mLon > 1e-14
                val rot = if (moved) (-Math.toDegrees(atan2(mLat, mLon)).toFloat() + 360f) % 360f
                          else (-Math.toDegrees(a).toFloat() + 360f) % 360f
                val facing = if (moved) mLon >= 0 else cos(a) >= 0
                units[u.id] = u.copy(location = placed, rotationAngle = rot, facingRight = facing, isMoving = true)
            } else if (u.isMoving) {
                units[u.id] = u.copy(isMoving = false)
            }
        }
    }

    private fun dist(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = lat1 - lat2; val dLon = lon1 - lon2
        return sqrt(dLat * dLat + dLon * dLon)
    }
}
