package ovh.gabrielhuav.pow.data.cache

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import org.osmdroid.tileprovider.modules.MapTileModuleProviderBase
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.util.MapTileIndex
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Módulo de tiles de osmdroid que usa la MISMA caché Room (offline) que las
 * versiones Web.
 *
 * Por qué existe: el descargador interno de osmdroid usa `userAgentValue =
 * packageName`, que el servidor público de OSM (tile.openstreetmap.org)
 * estrangula/rechaza — por eso el mapa NATIVO "no cargaba" zonas nuevas mientras
 * que las versiones Web (que descargan con User-Agent de navegador a través de
 * `CachingWebViewClient` y persisten en Room) sí funcionaban. Este módulo unifica
 * ambos caminos: lee primero de Room; si falta, descarga con UA de navegador y
 * persiste el tile, de modo que tras visitar una zona se puede jugar 100% offline.
 *
 * Clave de caché: bucket "osm" (no colisiona con "osm_web" ni los demás
 * proveedores Web) y hash = sha256 de la URL canónica de OSM, exactamente el mismo
 * esquema que `CachingWebViewClient` para que ambos compartan entradas.
 */
class RoomTileModuleProvider(
    appContext: Context,
    private val tileCache: TileCache,
    private val onTileServed: (fromCache: Boolean) -> Unit = {}
) : MapTileModuleProviderBase(2 /* hilos */, 40 /* cola pendiente */) {

    private val res = appContext.applicationContext.resources
    private val providerKey = "osm"

    override fun getName(): String = "RoomTileModuleProvider"
    override fun getThreadGroupName(): String = "roomtile"
    override fun getUsesDataConnection(): Boolean = true
    override fun getMinimumZoomLevel(): Int = 0
    override fun getMaximumZoomLevel(): Int = 19
    override fun setTileSource(tileSource: ITileSource?) { /* fijo a OSM Mapnik */ }

    override fun getTileLoader(): TileLoader = object : TileLoader() {
        override fun loadTile(pMapTileIndex: Long): Drawable? {
            val z = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            val canonicalUrl = "https://tile.openstreetmap.org/$z/$x/$y.png"
            val key = sha256(canonicalUrl)

            // 1) Room (offline-first)
            val cached = tileCache.getTileByUrl(providerKey, key)
            if (cached != null && cached.isNotEmpty()) {
                onTileServed(true)
                return decode(cached)
            }

            // 2) Red con UA de navegador + persistir en Room
            val downloaded = download(canonicalUrl)
            return if (downloaded != null && downloaded.isNotEmpty()) {
                tileCache.putTileByUrl(providerKey, key, downloaded)
                onTileServed(false)
                decode(downloaded)
            } else {
                onTileServed(false)
                null // tile no disponible (sin red y no cacheado)
            }
        }
    }

    private fun decode(data: ByteArray): Drawable? = try {
        BitmapFactory.decodeByteArray(data, 0, data.size)?.let { BitmapDrawable(res, it) }
    } catch (e: Exception) {
        null
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
            Log.e("RoomTileModule", "download fail ${e.message}")
            null
        } finally {
            c?.disconnect()
        }
    }

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
