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

    /**
     * Instala el aviso de FIN de clip. Pasa `null` para quitarlo.
     *
     * POR QUÉ HACE FALTA: `playSfSpecial` lleva un mapa de voces sonando (`activePlayers`) y decide
     * qué se puede interrumpir mirando **quién sigue ahí dentro**. Sin un aviso de fin, ese mapa no
     * se vacía nunca: las voces terminadas seguirían contando como "sonando" y las reglas de
     * interrupción se irían degradando poco a poco — el peleador dejaría de poder gritar porque su
     * intro, que acabó hace un minuto, sigue apuntada como en curso.
     *
     * ⚠️ **SE AVISA IGUAL SI TERMINA BIEN QUE SI FALLA**, y es deliberado: en Android el código
     * tenía `setOnCompletionListener` y `setOnErrorListener` haciendo **exactamente lo mismo**
     * (quitarse del mapa y liberarse). Un solo gancho evita que una implementación futura avise en
     * un caso y no en el otro, que es como se cuela una fuga silenciosa. Si algún día hace falta
     * distinguirlos, hay que añadir el parámetro AQUÍ y en las dos implementaciones a la vez.
     *
     * ⚠️ NO se garantiza en qué hilo llega. Quien lo use no debe tocar UI directamente desde dentro.
     *
     * ⚠️ En bucle ([reproducir] con `bucle = true`) esto **no se dispara nunca**: el clip no termina.
     */
    fun alTerminar(accion: (() -> Unit)?)
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

    /**
     * Duración de un clip en milisegundos, SIN reproducirlo. `null` si no se puede averiguar.
     *
     * POR QUE ESTA AQUI y no en `PowClip`: es un dato del ARCHIVO, no de una reproducción en curso.
     * El juego lo usa para cuadrar cuánto tiempo deja un subtítulo en pantalla, y para eso hay que
     * saberlo ANTES de que suene nada.
     *
     * ⚠️ Devuelve `null` en vez de 0 a proposito: quien llama tiene un valor de respaldo (el
     * `subtitleMs` de la frase) y un 0 lo haria parpadear en vez de usar el respaldo.
     */
    fun duracionMs(ruta: String): Long?

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
    override fun duracionMs(ruta: String): Long? = activa().duracionMs(ruta)
    override fun liberarTodo() = activa().liberarTodo()
}

/**
 * La fuente que se usa si nadie instaló ninguna: en iOS, `AVAudioPlayer`; en Android **falla con un
 * mensaje que dice la línea que falta**, porque sin `Context` no hay forma de abrir un asset y un
 * silencio inexplicable es mucho peor que un error claro al arrancar.
 */
expect fun fuenteAudioPorDefecto(): PowAudioFuente
