package ovh.gabrielhuav.pow.platform.audio

/**
 * 🍏 SONIDO, IGUAL EN ANDROID Y EN iOS.
 *
 * POR QUÉ HAY DOS TIPOS DE CARGA Y NO UNA: en Android el juego usa **SoundPool** para los golpes y
 * **MediaPlayer** para música y voces, y eso NO es capricho. SoundPool tiene latencia baja (un
 * golpe tiene que sonar YA) pero trunca los archivos largos; MediaPlayer aguanta piezas largas pero
 * tarda en arrancar. Si esta capa ofreciera un solo `cargar()`, habría que elegir uno de los dos
 * para todo y **la app de Android que hoy funciona sonaría peor**. Por eso el contrato distingue
 * [PowAudioFuente.cargarEfecto] de [PowAudioFuente.cargarPista].
 *
 * En iOS los dos caminos acaban en `AVAudioPlayer`, que sirve para ambos casos a esta escala.
 */
interface PowClip {

    /**
     * Suena desde el principio. [volumen] va de 0 a 1.
     *
     * ⚠️ Volver a llamarlo mientras suena REINICIA el clip; es lo que ya hacía el juego con los
     * golpes repetidos.
     */
    fun reproducir(volumen: Float = 1f, bucle: Boolean = false)

    /** Para y rebobina. */
    fun detener()

    /** Suelta los recursos. Después de esto el clip no vuelve a sonar. */
    fun liberar()

    /** `true` mientras el clip está sonando. */
    val reproduciendo: Boolean
}

/** De dónde salen los clips. Una implementación por plataforma. */
interface PowAudioFuente {

    /**
     * Efecto CORTO y de baja latencia (golpes, UI). Devuelve `null` si el asset no está: el juego
     * ya trataba un sonido que falta como "no suena", nunca como un fallo que corte la pelea.
     */
    fun cargarEfecto(ruta: String): PowClip?

    /** Pieza LARGA (música, voces). Devuelve `null` si el asset no está. */
    fun cargarPista(ruta: String): PowClip?

    /** Suelta todo lo que la fuente tenga abierto (SoundPool, sesiones de audio…). */
    fun liberarTodo()
}

/**
 * Punto de acceso único, mismo patrón que `PowAssets`.
 *
 * ⚠️ En **Android hay que instalarlo al arrancar** (necesita `Context`):
 * `PowAudio.instalar(AudioDeAndroid(context))`. En **iOS no hace falta**.
 */
object PowAudio : PowAudioFuente {

    private var fuente: PowAudioFuente? = null

    fun instalar(nueva: PowAudioFuente) {
        fuente = nueva
    }

    /** Solo para tests: sin esto un doble instalado contaminaría a los tests siguientes. */
    fun desinstalar() {
        fuente = null
    }

    private fun activa(): PowAudioFuente = fuente ?: fuenteAudioPorDefecto().also { fuente = it }

    override fun cargarEfecto(ruta: String): PowClip? = activa().cargarEfecto(ruta)
    override fun cargarPista(ruta: String): PowClip? = activa().cargarPista(ruta)
    override fun liberarTodo() = activa().liberarTodo()
}

/**
 * La fuente que se usa si nadie instaló ninguna: en iOS, `AVAudioPlayer`; en Android **falla con un
 * mensaje que dice la línea que falta**, porque sin `Context` no hay forma de abrir un asset y un
 * silencio inexplicable es mucho peor que un error claro al arrancar.
 */
expect fun fuenteAudioPorDefecto(): PowAudioFuente
