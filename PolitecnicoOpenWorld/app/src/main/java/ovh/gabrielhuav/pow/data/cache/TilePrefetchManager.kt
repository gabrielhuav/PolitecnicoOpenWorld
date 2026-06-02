package ovh.gabrielhuav.pow.data.cache

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/**
 * Pre-descarga proactiva de tiles OSM para la zona actual (~2 km) hacia la caché
 * Room (bucket "osm"), para que el proveedor NATIVO OSM se pueda jugar 100%
 * offline tras visitar. Es NO bloqueante: el jugador puede moverse mientras
 * descarga; si algún tile falla (sin red), se reporta para avisar que la zona
 * quedó incompleta.
 *
 * Usa exactamente el mismo esquema de URL/clave que [RoomTileModuleProvider] y
 * [ovh.gabrielhuav.pow.features.map_exterior.ui.CachingWebViewClient], de modo
 * que las entradas se comparten (no se descarga dos veces lo mismo).
 */
class TilePrefetchManager(private val tileCache: TileCache) {

    @Volatile private var running = false
    fun isRunning(): Boolean = running

    private data class Tile(val z: Int, val x: Int, val y: Int)

    suspend fun prefetchOsmZone(
        centerLat: Double,
        centerLon: Double,
        radiusMeters: Double = 1000.0,
        zooms: IntRange = 16..18,
        onProgress: (Float) -> Unit = {},
        onDone: (offlineComplete: Boolean) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        if (running) return@withContext
        running = true
        var anyFail = false
        try {
            // bbox aproximado a partir del radio en metros
            val dLat = radiusMeters / 111_320.0
            val dLon = radiusMeters / (111_320.0 * cos(Math.toRadians(centerLat)).coerceAtLeast(1e-6))
            val north = centerLat + dLat
            val south = centerLat - dLat
            val west = centerLon - dLon
            val east = centerLon + dLon

            val tiles = ArrayList<Tile>()
            for (z in zooms) {
                val (x0, y0) = lonLatToTile(west, north, z)
                val (x1, y1) = lonLatToTile(east, south, z)
                val xMin = min(x0, x1); val xMax = max(x0, x1)
                val yMin = min(y0, y1); val yMax = max(y0, y1)
                for (x in xMin..xMax) for (y in yMin..yMax) tiles.add(Tile(z, x, y))
            }

            val total = tiles.size.coerceAtLeast(1)
            var done = 0
            for (t in tiles) {
                val url = "https://tile.openstreetmap.org/${t.z}/${t.x}/${t.y}.png"
                val key = sha256(url)
                if (tileCache.getTileByUrl("osm", key) == null) {
                    val data = download(url)
                    if (data != null && data.isNotEmpty()) {
                        tileCache.putTileByUrl("osm", key, data)
                    } else {
                        anyFail = true
                    }
                }
                done++
                if (done % 4 == 0 || done == total) onProgress(done.toFloat() / total)
            }
            onProgress(1f)
            onDone(!anyFail)
        } catch (e: Exception) {
            Log.e("TilePrefetch", "error ${e.message}")
            onDone(false)
        } finally {
            running = false
        }
    }

    private fun lonLatToTile(lon: Double, lat: Double, z: Int): Pair<Int, Int> {
        val n = 1 shl z
        val x = floor((lon + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
        val latRad = Math.toRadians(lat)
        val y = floor((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n)
            .toInt().coerceIn(0, n - 1)
        return x to y
    }

    private fun download(url: String): ByteArray? {
        var c: HttpURLConnection? = null
        return try {
            c = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 20_000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android) PolitecnicoOpenWorld/1.0")
                setRequestProperty("Accept", "image/png,image/webp,image/*,*/*")
                setRequestProperty("Referer", "https://www.openstreetmap.org/")
            }
            if (c.responseCode == HttpURLConnection.HTTP_OK) c.inputStream.readBytes() else null
        } catch (e: Exception) {
            null
        } finally {
            c?.disconnect()
        }
    }

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
