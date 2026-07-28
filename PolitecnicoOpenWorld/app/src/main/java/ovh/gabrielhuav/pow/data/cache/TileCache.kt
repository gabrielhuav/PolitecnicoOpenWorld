package ovh.gabrielhuav.pow.data.cache

import android.util.Log
import kotlinx.coroutines.runBlocking
import ovh.gabrielhuav.pow.data.local.room.dao.MapTileDao
import ovh.gabrielhuav.pow.data.local.room.entity.MapTileEntity

/**
 * ⚠️ **AQUÍ VIVE EL PUENTE `suspend` → BLOQUEANTE, Y ES DELIBERADO.**
 *
 * `MapTileDao` tuvo que volverse `suspend` al compilar `:shared` para iOS: Room prohíbe DAOs
 * bloqueantes fuera de Android (ver el comentario del propio DAO). Pero los dos consumidores de
 * esta caché son callbacks SÍNCRONOS de framework, que deben devolver los bytes del tile en el
 * mismo hilo y no admiten `suspend`:
 *   - `CachingWebViewClient.shouldInterceptRequest` (el mapa Leaflet en WebView, que es el
 *     renderer POR DEFECTO del juego).
 *   - `RoomTileModuleProvider.loadTile` (el proveedor de tiles de osmdroid).
 *
 * Por eso el `runBlocking` se concentra AQUÍ y la API pública de `TileCache` no cambia: así los
 * 4 llamadores siguen intactos y el arreglo de iOS no se filtra al resto de `:app`.
 *
 * ⚠️ **No es un `runBlocking` en el hilo principal.** Ambos callbacks ya corren en hilos de
 * fondo y ya bloquean ahí para bajar el tile por red (`HttpURLConnection` síncrono), así que esto
 * no introduce bloqueo de UI nuevo. Si algún día se llama a `TileCache` desde el hilo de UI, hay
 * que cambiar el llamador a corrutinas, NO quitar el `suspend` del DAO (rompería iOS).
 */
class TileCache(private val mapTileDao: MapTileDao) {

    private val TAG = "TileDebug_Cache"

    companion object {
        private const val MAX_TILES_PER_PROVIDER = 8_000
    }

    fun getTileByUrl(provider: String, urlKey: String): ByteArray? {
        return try {
            Log.d(TAG, "Consultando Room para provider=$provider, hash=$urlKey...")
            val data = runBlocking { mapTileDao.getTileData(provider, urlKey) }
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
            Log.d(TAG, "Intentando guardar en Room provider=$provider, hash=$urlKey, bytes=${data.size}")

            val entity = MapTileEntity(
                urlKey = urlKey,
                provider = provider,
                data = data,
                createdAtMs = System.currentTimeMillis()
            )
            // Escritura atómica: contar + evict (LRU) + insertar en una sola
            // transacción de Room, evitando estados corruptos a media escritura.
            runBlocking {
                mapTileDao.putTileAtomic(
                    tile = entity,
                    maxTilesPerProvider = MAX_TILES_PER_PROVIDER,
                    evictBatch = MAX_TILES_PER_PROVIDER / 10
                )
            }
            Log.d(TAG, "¡Guardado exitoso (atómico) en Room para $urlKey!")
        } catch (e: Exception) {
            Log.e(TAG, "Excepción al escribir en Room (putTileByUrl): ${e.stackTraceToString()}")
        }
    }

    fun getStats(provider: String): String {
        return try {
            val count = runBlocking { mapTileDao.getCount(provider) }
            "$provider: $count tiles en caché"
        } catch (e: Exception) { "error" }
    }

    fun closeAll() {}
}