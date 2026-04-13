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

class CachingWebViewClient(
    private val tileCache: TileCache,
    private val getCurrentProvider: () -> MapProvider,
    private val onTileServed: (fromCache: Boolean) -> Unit
) : WebViewClient() {

    private val TAG = "TileDebug_WebView"

    private fun normalizeTileUrl(url: String): String {
        // 1. Quitar parámetros de consulta (ej. ?v=123) que cambian dinámicamente
        val withoutQuery = url.substringBefore("?")

        // 2. Eliminar subdominios de balanceo de carga (a, b, c, d)
        // Ejemplo: https://a.tile.openstreetmap.org/... -> https://tile.openstreetmap.org/...
        // Esto asegura que el Hash sea idéntico sin importar de qué servidor rotativo venga
        return withoutQuery.replace(Regex("://[a-d]\\."), "://")
    }

    override fun shouldInterceptRequest(
        view: android.webkit.WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val rawUrl = request?.url?.toString() ?: return null
        val provider = getCurrentProvider()

        if (provider == MapProvider.OSM || request.method != "GET") return null

        // USAR LA NUEVA FUNCIÓN AQUÍ
        val normalizedUrl = normalizeTileUrl(rawUrl)

        if (!looksLikeTile(normalizedUrl)) {
            return null
        }

        val providerKey = provider.name.lowercase()
        val urlKey = sha256(normalizedUrl) // Generamos el hash con la URL purificada

        // 1. Buscar en Caché Local
        val cached = tileCache.getTileByUrl(providerKey, urlKey)
        if (cached != null && cached.isNotEmpty()) {
            Log.d(TAG, "🟢 HIT (Caché): $normalizedUrl")
            onTileServed(true)
            return buildResponse(guessMimeType(normalizedUrl), cached)
        }

        // 2. Descargar de la red (usando la URL original para que la red funcione)
        Log.d(TAG, "🔴 MISS (Red): $normalizedUrl")
        val downloaded = downloadTile(rawUrl)

        if (downloaded != null && downloaded.isNotEmpty()) {
            tileCache.putTileByUrl(providerKey, urlKey, downloaded)
            onTileServed(false)
            return buildResponse(guessMimeType(normalizedUrl), downloaded)
        }

        onTileServed(false)
        return null
    }

    private fun buildResponse(mimeType: String, data: ByteArray): WebResourceResponse {
        val headers = mapOf(
            "Access-Control-Allow-Origin"  to "*",
            "Access-Control-Allow-Methods" to "GET",
            "Cache-Control"                to "max-age=2592000"
        )
        return WebResourceResponse(mimeType, "UTF-8", 200, "OK", headers, ByteArrayInputStream(data))
    }

    private fun downloadTile(url: String): ByteArray? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout    = 20_000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android) PolitecnicoOpenWorld/1.0")
                setRequestProperty("Accept", "image/png,image/webp,image/*,*/*")
                setRequestProperty("Referer", "https://leafletjs.com/")
            }

            val code = connection.responseCode
            Log.d(TAG, "Código HTTP $code al descargar $url")

            if (code == HttpURLConnection.HTTP_OK) {
                connection.inputStream.readBytes()
            } else {
                Log.e(TAG, "Error HTTP $code - No se pudo descargar la imagen")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción al descargar tile: ${e.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun looksLikeTile(url: String): Boolean {
        // Ignoramos expresamente recursos estáticos del HTML/JS
        if (url.contains(".js") || url.contains(".css") || url.contains(".html") || url.contains(".json") || url.contains(".ico")) return false

        // Si tiene extensión de imagen, seguro es tile
        if (url.contains(".png") || url.contains(".jpg") || url.contains(".jpeg") || url.contains(".webp") || url.contains(".gif")) return true

        // Cadenas comunes en los subdominios de proveedores de mapas
        if (url.contains("tile") || url.contains("/vt/") || url.contains("MapServer") || url.contains("cartocdn") || url.contains("arcgisonline") || url.contains("opentopomap")) return true

        // En caso de duda (por ej: un proveedor extraño que usa URLs limpias),
        // podrías cambiar esto a true temporalmente para probar, pero generaría falsos positivos.
        return false
    }

    private fun guessMimeType(url: String): String = when {
        url.contains(".jpg") || url.contains(".jpeg") -> "image/jpeg"
        url.contains(".webp") -> "image/webp"
        url.contains(".gif") -> "image/gif"
        else -> "image/png"
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}