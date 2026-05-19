package ovh.gabrielhuav.pow.data.cache

import android.util.Log
import ovh.gabrielhuav.pow.data.local.room.dao.MapTileDao
import ovh.gabrielhuav.pow.data.local.room.entity.MapTileEntity
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class TileCache(private val mapTileDao: MapTileDao) {

    private val TAG = "TileDebug_Cache"

    companion object {
        private const val MAX_TILES_PER_PROVIDER = 8_000
    }

    // Contador en memoria por proveedor: evita un COUNT(*) de Room en cada guardado
    private val countByProvider = ConcurrentHashMap<String, AtomicLong>()

    private fun getCount(provider: String): Long =
        countByProvider.getOrPut(provider) { AtomicLong(mapTileDao.getCount(provider)) }.get()

    fun getTileByUrl(provider: String, urlKey: String): ByteArray? {
        return try {
            Log.d(TAG, "Consultando Room para provider=$provider, hash=$urlKey...")
            val data = mapTileDao.getTileData(provider, urlKey)
            if (data != null) {
                Log.d(TAG, "¡HIT en Room! Encontrados ${data.size} bytes para $urlKey")
            } else {
                Log.d(TAG, "MISS en Room para $urlKey")
            }
            data
        } catch (e: Exception) {
            Log.e(TAG, "Excepción al leer de Room (getTileByUrl): ${e.stackTraceToString()}")
            null
        }
    }

    fun putTileByUrl(provider: String, urlKey: String, data: ByteArray) {
        try {
            val counter = countByProvider.getOrPut(provider) { AtomicLong(mapTileDao.getCount(provider)) }
            val count = counter.get()

            if (count >= MAX_TILES_PER_PROVIDER) {
                val evict = MAX_TILES_PER_PROVIDER / 10
                mapTileDao.deleteOldestTiles(provider, evict)
                counter.addAndGet(-evict.toLong())
                Log.d(TAG, "LRU: $evict tiles eliminados para $provider")
            }

            val entity = MapTileEntity(
                urlKey = urlKey,
                provider = provider,
                data = data,
                createdAtMs = System.currentTimeMillis()
            )
            mapTileDao.insertTile(entity)
            counter.incrementAndGet()
            Log.d(TAG, "¡Guardado exitoso en Room para $urlKey!")
        } catch (e: Exception) {
            Log.e(TAG, "Excepción al escribir en Room (putTileByUrl): ${e.stackTraceToString()}")
        }
    }

    fun getStats(provider: String): String {
        return try {
            val count = mapTileDao.getCount(provider)
            "$provider: $count tiles en caché"
        } catch (e: Exception) { "error" }
    }

    fun closeAll() {}
}