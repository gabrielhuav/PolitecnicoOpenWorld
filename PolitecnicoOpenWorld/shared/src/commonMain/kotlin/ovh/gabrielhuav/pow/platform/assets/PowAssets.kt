package ovh.gabrielhuav.pow.platform.assets

/**
 * 🍏 LECTURA DE ASSETS, IGUAL EN ANDROID Y EN iOS.
 *
 * POR QUÉ EXISTE: el modo pelea lee de `assets/` los atlas, los sprites, los fondos, los JSON de
 * combos y las frases. En Android eso es `context.assets.open(...)`; en iOS es el **bundle**
 * (`NSBundle`), que no tiene ni `Context` ni `InputStream`. Nueve archivos de SF dependían de esto,
 * así que es la pieza que más desbloquea.
 *
 * ⚠️ LAS RUTAS SON LAS MISMAS EN LAS DOS PLATAFORMAS: relativas y sin barra inicial, tal cual se
 * escriben hoy (`"STREETFIGHTER/DATA/huelum.json"`). El que traduce a la convención de cada
 * plataforma es el [PowAssetsFuente] de turno, no quien llama.
 */
interface PowAssetsFuente {

    /** Contenido crudo. Lanza [PowAssetNoEncontrado] si no existe. */
    fun bytes(ruta: String): ByteArray

    /** Contenido como texto UTF-8. Lanza [PowAssetNoEncontrado] si no existe. */
    fun texto(ruta: String): String = bytes(ruta).decodeToString()

    /**
     * Nombres de archivo (NO rutas completas) dentro de [dir], sin recursión.
     * Devuelve lista vacía si el directorio no existe: es lo que hacía el código de Android.
     */
    fun listar(dir: String): List<String>

    /** `true` si el asset se puede abrir. No lanza. */
    fun existe(ruta: String): Boolean
}

/** El asset no está donde se dijo. Mensaje con la ruta, que es lo único útil para depurar esto. */
class PowAssetNoEncontrado(ruta: String, causa: Throwable? = null) :
    RuntimeException("No se encontró el asset '$ruta'", causa)

/**
 * Punto de acceso único. Se usa como `PowAssets.texto("...")` desde código común.
 *
 * ⚠️ En **Android hay que instalar la fuente al arrancar** (necesita el `Context`, y este módulo no
 * lo tiene): `PowAssets.instalar(AssetsDeAndroid(context))` en `Application.onCreate`. En **iOS no
 * hace falta**: el bundle es global y [fuentePorDefecto] lo resuelve solo.
 */
object PowAssets : PowAssetsFuente {

    private var fuente: PowAssetsFuente? = null

    /** Instala la fuente real. Idempotente: llamarlo dos veces con lo mismo no molesta. */
    fun instalar(nueva: PowAssetsFuente) {
        fuente = nueva
    }

    /**
     * Deja el objeto sin fuente. Solo para tests: sin esto, un test que instale un doble
     * contaminaría a los siguientes.
     */
    fun desinstalar() {
        fuente = null
    }

    private fun activa(): PowAssetsFuente =
        fuente ?: fuentePorDefecto().also { fuente = it }

    override fun bytes(ruta: String): ByteArray = activa().bytes(ruta)
    override fun texto(ruta: String): String = activa().texto(ruta)
    override fun listar(dir: String): List<String> = activa().listar(dir)
    override fun existe(ruta: String): Boolean = activa().existe(ruta)
}

/**
 * La fuente que se usa si nadie instaló ninguna.
 *
 * En iOS devuelve la del bundle. En Android **falla a propósito con un mensaje que dice qué
 * hacer**: no hay forma de conseguir un `Context` desde aquí, y fallar claro al arrancar es mucho
 * mejor que devolver algo vacío y que el juego salga sin sprites sin explicar por qué.
 */
expect fun fuentePorDefecto(): PowAssetsFuente
