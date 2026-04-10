package ovh.gabrielhuav.pow.features.map_exterior.ui

import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebViewClient
import ovh.gabrielhuav.pow.data.cache.TileCache
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.MapProvider
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * WebViewClient que intercepta tiles del mapa WebView y los cachea en TileCache (SQLite/filesDir).
 *
 * ESTRATEGIA SIMPLIFICADA:
 * En lugar de intentar parsear z/x/y de la URL (que falla con distintos formatos),
 * usamos la URL completa como clave de caché. Esto es más robusto y funciona con
 * cualquier proveedor sin necesidad de regex específicos por proveedor.
 *
 * CLAVE DE CACHÉ = hash de la URL completa del tile.
 */
class CachingWebViewClient(
    private val tileCache: TileCache,
    private val getCurrentProvider: () -> MapProvider,
    private val onTileServed: (fromCache: Boolean) -> Unit
) : WebViewClient() {

    private val TAG = "CachingWebViewClient"

    override fun shouldInterceptRequest(
        view: android.webkit.WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val url      = request?.url?.toString() ?: return null
        val provider = getCurrentProvider()

        // Solo interceptar para proveedores web (no OSM nativo que usa osmdroid)
        if (provider == MapProvider.OSM) return null

        // Filtro: solo interceptar peticiones que parecen tiles de imagen
        if (!looksLikeTile(url)) {
            Log.v(TAG, "Ignorando (no tile): $url")
            return null
        }

        Log.d(TAG, "Interceptando tile: $url")

        val providerKey = provider.name.lowercase()
        // SHA-256 del URL completo como clave — sin colisiones prácticas vs. hashCode()
        val urlKey = sha256(url)

        // 1. Buscar en caché local
        val cached = tileCache.getTileByUrl(providerKey, urlKey)
        if (cached != null) {
            Log.d(TAG, "CACHE HIT: $url")
            onTileServed(true)
            return buildResponse(guessMimeType(url), cached)
        }

        // 2. Descargar de la red y guardar en caché
        Log.d(TAG, "CACHE MISS — descargando: $url")
        val downloaded = downloadTile(url)
        if (downloaded != null) {
            tileCache.putTileByUrl(providerKey, urlKey, downloaded)
            onTileServed(false)
            return buildResponse(guessMimeType(url), downloaded)
        }

        // 3. Descarga falló — dejar que el WebView lo intente por su cuenta
        Log.w(TAG, "Descarga fallida, dejando pasar: $url")
        onTileServed(false)
        return null
    }

    /** Construye un WebResourceResponse con cabeceras CORS para que Leaflet acepte el tile. */
    private fun buildResponse(mimeType: String, data: ByteArray): WebResourceResponse {
        val headers = mapOf(
            "Access-Control-Allow-Origin"  to "*",
            "Access-Control-Allow-Methods" to "GET",
            "Cache-Control"                to "max-age=2592000"  // 30 días
        )
        return WebResourceResponse(mimeType, "UTF-8", 200, "OK", headers,
            ByteArrayInputStream(data))
    }

    private fun downloadTile(url: String): ByteArray? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout    = 20_000
                // Headers necesarios para algunos proveedores (ESRI, CartoDB)
                setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Android) PolitecnicoOpenWorld/1.0")
                setRequestProperty("Accept", "image/png,image/webp,image/*,*/*")
                setRequestProperty("Referer", "https://leafletjs.com/")
            }
            val code = connection.responseCode
            Log.d(TAG, "HTTP $code para: $url")
            if (code == HttpURLConnection.HTTP_OK) {
                connection.inputStream.readBytes()
            } else {
                Log.w(TAG, "HTTP $code al descargar tile")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error descargando: ${e.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Determina si una URL parece ser un tile de mapa.
     * Enfoque permisivo: mejor interceptar de más que de menos.
     */
    private fun looksLikeTile(url: String): Boolean {
        // Descartar claramente no-tiles
        if (url.contains(".js"))   return false
        if (url.contains(".css"))  return false
        if (url.contains(".html")) return false
        if (url.contains(".json")) return false
        if (url.contains(".ico"))  return false
        if (url.contains(".woff")) return false

        // Aceptar si contiene extensiones de imagen conocidas
        if (url.contains(".png"))  return true
        if (url.contains(".jpg"))  return true
        if (url.contains(".jpeg")) return true
        if (url.contains(".webp")) return true

        // Aceptar URLs de proveedores conocidos sin extensión explícita
        if (url.contains("tile"))         return true
        if (url.contains("/vt/"))         return true
        if (url.contains("MapServer"))    return true
        if (url.contains("cartocdn"))     return true
        if (url.contains("arcgisonline")) return true
        if (url.contains("opentopomap")) return true

        return false
    }

    private fun guessMimeType(url: String): String = when {
        url.contains(".jpg") || url.contains(".jpeg") -> "image/jpeg"
        url.contains(".webp")                          -> "image/webp"
        else                                           -> "image/png"
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}