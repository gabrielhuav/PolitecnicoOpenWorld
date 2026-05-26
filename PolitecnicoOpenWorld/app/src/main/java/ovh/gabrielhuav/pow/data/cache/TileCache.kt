package ovh.gabrielhuav.pow.data.cache

import android.util.Log
import ovh.gabrielhuav.pow.data.local.room.dao.MapTileDao
import ovh.gabrielhuav.pow.data.local.room.entity.MapTileEntity

class TileCache(private val mapTileDao: MapTileDao) {

    private val TAG = "TileDebug_Cache"

    companion object {
        private const val MAX_TILES_PER_PROVIDER = 8_000L
    }

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
            Log.d(TAG, "Guardando en Room provider=$provider, hash=$urlKey, bytes=${data.size}")

            val entity = MapTileEntity(
                urlKey      = urlKey,
                provider    = provider,
                data        = data,
                createdAtMs = System.currentTimeMillis()
            )

            // BUG FIX: antes se hacían 3 operaciones separadas (getCount, deleteOldestTiles,
            // insertTile) sin transacción. Si el proceso moría entre el delete y el insert
            // el caché quedaba en estado inconsistente (tiles eliminados pero nuevo no guardado).
            // Ahora se usa insertTileWithEvict() que envuelve todo en @Transaction.
            mapTileDao.insertTileWithEvict(entity, MAX_TILES_PER_PROVIDER)

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
