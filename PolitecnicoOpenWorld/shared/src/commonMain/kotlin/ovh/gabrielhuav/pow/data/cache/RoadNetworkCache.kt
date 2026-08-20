package ovh.gabrielhuav.pow.data.cache

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ovh.gabrielhuav.pow.data.local.room.dao.RoadNetworkDao
import ovh.gabrielhuav.pow.data.local.room.entity.RoadNodeEntity
import ovh.gabrielhuav.pow.data.local.room.entity.RoadWayEntity
import ovh.gabrielhuav.pow.data.local.room.entity.RoadZoneEntity
import ovh.gabrielhuav.pow.domain.models.map.MapNode
import ovh.gabrielhuav.pow.domain.models.map.MapWay
import ovh.gabrielhuav.pow.platform.log.powLog
import ovh.gabrielhuav.pow.platform.tiempo.ahoraMs
import kotlin.math.floor

/**
 * 🗺️ LA CACHÉ DE CALLES DE OVERPASS, IGUAL EN ANDROID Y EN iOS.
 *
 * ## Por qué importa que sea compartida
 *
 * **Overpass limita por IP y devuelve 429 en cuanto le pides seguido.** Android casi no lo notaba
 * porque cacheaba en Room desde el principio; iOS no tenía esta clase y pedía la red entera **en
 * cada arranque**, así que el 429 era lo normal y el mundo salía sin NPCs (`updateNpcs` sale por
 * `if (!networkIsReady) return`, o sea que sin calles no hay nadie). Bajarla a `commonMain` es lo
 * que le da a iOS el mismo comportamiento sin escribir una segunda caché que se desincronice.
 *
 * La red se guarda por **celdas de ~2 km** con un TTL de 7 días y un LRU de [MAX_ZONES] zonas.
 *
 * ⚠️ Al bajarla se cambiaron las TRES cosas que no cruzan (y son justo las de `09 §KMP`):
 * `android.util.Log` → [powLog]; `Dispatchers.IO` → `Dispatchers.Default`, porque el primero no
 * existe en Kotlin/Native; y `System.currentTimeMillis()` → [ahoraMs], que da el MISMO valor de
 * época en las dos plataformas (un reloj monótono aquí rompería el TTL al cruzar de módulo).
 */
class RoadNetworkCache(private val dao: RoadNetworkDao) {

    private val TAG = "RoadNetworkCache"

    companion object {
        private const val CACHE_TTL_MS    = 1000L * 60 * 60 * 24 * 7  // 7 días
        private const val CELL_SIZE_DEG   = 0.02                        // ~2km x 2km
        private const val MAX_ZONES       = 20
    }

    // ─── GET ──────────────────────────────────────────────────────────────────────

    suspend fun get(lat: Double, lon: Double): List<MapWay>? = withContext(Dispatchers.Default) {
        val key = cellKey(lat, lon)

        val zone = dao.getZone(key)
        if (zone == null) {
            powLog(TAG, "MISS (no existe): $key")
            return@withContext null
        }

        val ageMs = ahoraMs() - zone.downloadedAtMs
        if (ageMs > CACHE_TTL_MS) {
            powLog(TAG, "MISS (expirada ${ageMs / 3_600_000}h): $key")
            dao.deleteExpiredZones(ahoraMs() - CACHE_TTL_MS)
            return@withContext null
        }

        val wayEntities  = dao.getWaysForZone(key)
        val nodeEntities = dao.getNodesForZone(key)

        if (wayEntities.isEmpty()) {
            powLog(TAG, "Zona $key sin ways — escritura incompleta, se re-descargará")
            return@withContext null
        }

        val result = reconstituteNetwork(wayEntities, nodeEntities)
        if (result.isEmpty()) {
            powLog(TAG, "Zona $key sin nodos válidos — se re-descargará")
            return@withContext null
        }
        powLog(TAG, "HIT: $key → ${result.size} ways (${ageMs / 3_600_000}h de antigüedad)")
        result
    }

    // ─── PUT ──────────────────────────────────────────────────────────────────────

    suspend fun put(lat: Double, lon: Double, ways: List<MapWay>) = withContext(Dispatchers.Default) {
        if (ways.isEmpty()) return@withContext

        val key = cellKey(lat, lon)
        val now = ahoraMs()

        // LRU: liberar espacio si es necesario
        if (dao.getZoneCount() >= MAX_ZONES) {
            dao.deleteOldestZone()
            powLog(TAG, "LRU evict para hacer espacio")
        }

        val zoneEntity = RoadZoneEntity(
            cellKey        = key,
            downloadedAtMs = now,
            wayCount       = ways.size
        )
        val wayEntities = ways.map {
            RoadWayEntity(wayId = it.id, cellKey = key,
                isForCars = it.isForCars, isForPeople = it.isForPeople)
        }
        val nodeEntities = ArrayList<RoadNodeEntity>(ways.sumOf { it.nodes.size })
        for (way in ways) {
            for ((pos, node) in way.nodes.withIndex()) {
                nodeEntities.add(RoadNodeEntity(
                    wayId    = way.id,
                    cellKey  = key,
                    nodeId   = node.id,
                    position = pos,
                    latInt   = (node.lat * 1_000_000).toLong(),
                    lonInt   = (node.lon * 1_000_000).toLong()
                ))
            }
        }

        // UNA SOLA TRANSACCIÓN ATÓMICA — clave para que Room realmente persista
        try {
            dao.insertZoneWithData(zoneEntity, wayEntities, nodeEntities)
            powLog(TAG, "GUARDADO OK: $key → ${ways.size} ways, ${nodeEntities.size} nodos")
        } catch (e: Exception) {
            powLog(TAG, "Error guardando $key: ${e.message}")
        }
    }

    suspend fun getStats(): String = withContext(Dispatchers.Default) {
        val zones = dao.getZoneCount()
        val ways  = dao.getTotalWayCount() ?: 0
        "Room: $zones/$MAX_ZONES zonas, $ways ways"
    }

    // ─── RECONSTRUCCIÓN ───────────────────────────────────────────────────────────

    private fun reconstituteNetwork(
        wayEntities: List<RoadWayEntity>,
        nodeEntities: List<RoadNodeEntity>
    ): List<MapWay> {
        val nodesByWay = HashMap<Long, MutableList<MapNode>>(wayEntities.size)
        for (node in nodeEntities) {
            nodesByWay.getOrPut(node.wayId) { mutableListOf() }.add(
                MapNode(node.nodeId, node.latInt / 1_000_000.0, node.lonInt / 1_000_000.0)
            )
        }
        return wayEntities.mapNotNull { wayEntity ->
            val nodes = nodesByWay[wayEntity.wayId]
            if (nodes != null && nodes.size > 1)
                MapWay(wayEntity.wayId, nodes, wayEntity.isForCars, wayEntity.isForPeople)
            else null
        }
    }

    private fun cellKey(lat: Double, lon: Double): String {
        val cLat = floor(lat / CELL_SIZE_DEG).toInt()
        val cLon = floor(lon / CELL_SIZE_DEG).toInt()
        return "${cLat}_${cLon}"
    }
}