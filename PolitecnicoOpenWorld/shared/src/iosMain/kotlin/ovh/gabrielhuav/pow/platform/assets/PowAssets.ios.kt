package ovh.gabrielhuav.pow.platform.assets

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy

/**
 * Implementación de iOS: los assets viajan DENTRO DEL BUNDLE de la app.
 *
 * ⚠️ CÓMO ENTRAN EN EL BUNDLE (esto hay que hacerlo en Xcode, no basta con este archivo): la
 * carpeta `app/src/main/assets` se añade al target como **"folder reference"** (la carpeta AZUL,
 * no la amarilla de "group"). La azul conserva la jerarquía de subcarpetas; la amarilla APLASTA
 * todo a un solo nivel y entonces `STREETFIGHTER/DATA/x.json` deja de existir aunque el archivo
 * esté ahí. Es el error clásico y no da ningún aviso.
 *
 * Con la carpeta añadida como `assets`, la ruta real es `<bundle>/assets/<ruta>` — de ahí [raiz].
 */
@OptIn(ExperimentalForeignApi::class)
class AssetsDeBundle(
    /** Subcarpeta del bundle donde quedaron los assets. Vacío = raíz del bundle. */
    private val raiz: String = "assets",
) : PowAssetsFuente {

    private val base: String =
        (NSBundle.mainBundle.resourcePath ?: "") + if (raiz.isEmpty()) "" else "/$raiz"

    private fun rutaAbsoluta(ruta: String) = "$base/${ruta.trimStart('/')}"

    override fun bytes(ruta: String): ByteArray {
        val data: NSData = NSData.dataWithContentsOfFile(rutaAbsoluta(ruta))
            ?: throw PowAssetNoEncontrado(ruta)
        return data.aByteArray()
    }

    override fun listar(dir: String): List<String> =
        // Mismo contrato que Android: si el directorio no existe, lista vacía en vez de excepción.
        NSFileManager.defaultManager
            .contentsOfDirectoryAtPath(rutaAbsoluta(dir), null)
            ?.filterIsInstance<String>()
            .orEmpty()

    override fun existe(ruta: String): Boolean =
        NSFileManager.defaultManager.fileExistsAtPath(rutaAbsoluta(ruta))
}

/**
 * Copia un [NSData] a un [ByteArray].
 *
 * ⚠️ El `if` de vacío NO es decorativo: `addressOf(0)` sobre un array de tamaño 0 no es válido, y
 * sin la guarda esto reventaría justo con los archivos vacíos.
 */
@OptIn(ExperimentalForeignApi::class)
private fun NSData.aByteArray(): ByteArray {
    val n = length.toInt()
    if (n == 0) return ByteArray(0)
    val destino = ByteArray(n)
    destino.usePinned { fijado ->
        memcpy(fijado.addressOf(0), this.bytes, length)
    }
    return destino
}

/** En iOS sí hay fuente por defecto: el bundle es global y no necesita que nadie lo inyecte. */
actual fun fuentePorDefecto(): PowAssetsFuente = AssetsDeBundle()
