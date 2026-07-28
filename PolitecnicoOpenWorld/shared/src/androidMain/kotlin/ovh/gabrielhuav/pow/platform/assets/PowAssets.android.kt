package ovh.gabrielhuav.pow.platform.assets

import android.content.Context
import android.content.res.AssetManager
import java.io.FileNotFoundException

/**
 * Implementación de Android: el `AssetManager` de siempre. Es EXACTAMENTE lo que hacían los nueve
 * archivos de SF antes de la Fase 5, así que el comportamiento no cambia.
 *
 * Guarda el `applicationContext` a propósito: es un singleton de proceso y así no se retiene una
 * Activity por accidente.
 */
class AssetsDeAndroid(context: Context) : PowAssetsFuente {

    private val assets: AssetManager = context.applicationContext.assets

    override fun bytes(ruta: String): ByteArray =
        try {
            assets.open(ruta).use { it.readBytes() }
        } catch (e: FileNotFoundException) {
            throw PowAssetNoEncontrado(ruta, e)
        }

    override fun listar(dir: String): List<String> =
        // `list` devuelve null si el directorio no existe; el código viejo ya lo trataba como
        // "vacío" (con runCatching), y se conserva ese contrato.
        runCatching { assets.list(dir)?.toList() }.getOrNull().orEmpty()

    override fun existe(ruta: String): Boolean =
        runCatching { assets.open(ruta).close() }.isSuccess
}

/**
 * En Android NO hay fuente por defecto: leer assets exige un `Context` y `:shared` no tiene ninguno.
 * Se falla con un mensaje que dice la línea exacta que falta, en vez de devolver un objeto vacío que
 * dejaría el juego sin sprites sin explicar nada.
 */
actual fun fuentePorDefecto(): PowAssetsFuente =
    error(
        "PowAssets no está instalado. En Android añade esto a Application.onCreate():\n" +
            "    PowAssets.instalar(AssetsDeAndroid(this))",
    )
