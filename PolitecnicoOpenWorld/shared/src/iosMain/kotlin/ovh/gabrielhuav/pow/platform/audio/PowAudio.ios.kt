package ovh.gabrielhuav.pow.platform.audio

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.AVFAudio.setActive
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.darwin.NSObject

/**
 * Implementación de iOS con `AVAudioPlayer`.
 *
 * ⚠️ SIN CONFIGURAR LA SESIÓN DE AUDIO, EN UN iPHONE REAL NO SUENA NADA cuando el interruptor de
 * silencio está puesto — y el simulador NO reproduce ese comportamiento, así que es un fallo que
 * solo aparece en el dispositivo. Se usa la categoría **Ambient**: el juego respeta el interruptor
 * de silencio y NO corta la música que el jugador tenga puesta, que es lo que se espera de un juego
 * casual. (Con `Playback` sonaría por encima de Spotify y encima ignorando el silencio.)
 *
 * @param raiz misma subcarpeta del bundle que usa `AssetsDeBundle`.
 */
@OptIn(ExperimentalForeignApi::class)
class AudioDeAVFoundation(private val raiz: String = "assets") : PowAudioFuente {

    init {
        runCatching {
            val sesion = AVAudioSession.sharedInstance()
            sesion.setCategory(AVAudioSessionCategoryAmbient, null)
            sesion.setActive(true, null)
        }
    }

    private val base: String =
        (NSBundle.mainBundle.resourcePath ?: "") + if (raiz.isEmpty()) "" else "/$raiz"

    private fun crear(ruta: String): PowClip? {
        val absoluta = "$base/${ruta.trimStart('/')}"
        // Se comprueba antes de construir: el constructor de AVAudioPlayer con un archivo que no
        // existe devuelve un objeto inservible en vez de fallar de forma clara.
        if (!NSFileManager.defaultManager.fileExistsAtPath(absoluta)) return null
        // ⚠️⚠️ EL `runCatching` NO ES DEFENSIVO DE ADORNO — SIN ÉL LA APP SE CIERRA.
        // `initWithContentsOfURL:error:` lleva un out-param `NSError**`, así que Kotlin/Native lo
        // mapea como función que LANZA. Si el formato no se soporta, la excepción sube sin que
        // nadie la recoja y el proceso muere con SIGABRT (medido en el simulador, 2026-07-28).
        // El contrato de `PowAudioFuente` dice "devuelve null si el asset no está": un sonido que
        // no suena NUNCA debe tumbar una pelea.
        val player = runCatching { AVAudioPlayer(NSURL.fileURLWithPath(absoluta), null) }
            .getOrNull() ?: return null
        // Pre-decodifica: sin esto, el primer golpe llega tarde.
        player.prepareToPlay()
        return ClipAVAudio(player)
    }

    /**
     * En iOS los dos caminos son el mismo `AVAudioPlayer`. La distinción efecto/pista existe por
     * Android (SoundPool vs MediaPlayer); aquí se mantiene la firma y ya.
     */
    override fun cargarEfecto(ruta: String): PowClip? = crear(ruta)

    override fun cargarPista(ruta: String): PowClip? = crear(ruta)

    /**
     * En iOS se abre un `AVAudioPlayer` solo para leer su `duration` y se descarta. No se reproduce
     * nada: el reproductor se crea, se pregunta y se suelta.
     *
     * ⚠️ `duration` viene en SEGUNDOS (Double), no en milisegundos como en Android — de ahí el ×1000.
     * Sin esa conversion los subtitulos de iOS durarian un milisegundo.
     */
    override fun duracionMs(ruta: String): Long? {
        val absoluta = "$base/${ruta.trimStart('/')}"
        if (!NSFileManager.defaultManager.fileExistsAtPath(absoluta)) return null
        // Mismo motivo que en `crear()`: sin el `runCatching`, un formato no soportado tumba la app.
        val player = runCatching { AVAudioPlayer(NSURL.fileURLWithPath(absoluta), null) }
            .getOrNull() ?: return null
        val segundos = player.duration
        return if (segundos > 0.0) (segundos * 1000.0).toLong() else null
    }

    /** No hay nada global que soltar: cada clip se libera solo. */
    override fun liberarTodo() = Unit
}

private class ClipAVAudio(private val player: AVAudioPlayer) : PowClip {

    private var liberado = false

    /**
     * ⚠️⚠️ ESTE CAMPO ES LA RAZÓN DE QUE EL AVISO DE FIN FUNCIONE. `AVAudioPlayer.delegate` es una
     * referencia **DÉBIL** (como casi todos los delegados de Cocoa). Si el objeto delegado solo
     * viviera dentro de [alTerminar], nadie lo retendría, el recolector se lo llevaría y el aviso
     * **no llegaría nunca** — sin error, sin log, sin nada: las voces se quedarían apuntadas como
     * "sonando" para siempre y el peleador dejaría de poder gritar. Guardarlo aquí lo mantiene vivo
     * tanto como el clip. **NO conviertas esto en una variable local.**
     */
    private var delegado: DelegadoDeFin? = null

    override fun alTerminar(accion: (() -> Unit)?) {
        if (liberado) return
        if (accion == null) {
            player.delegate = null
            delegado = null
            return
        }
        val nuevo = DelegadoDeFin(accion)
        delegado = nuevo
        player.delegate = nuevo
    }

    override fun reproducir(volumen: Float, bucle: Boolean) {
        if (liberado) return
        // -1 = repetir indefinidamente; 0 = sonar una vez. Es el equivalente al `isLooping`.
        player.numberOfLoops = if (bucle) -1 else 0
        player.volume = volumen
        // Rebobina: el contrato dice que `reproducir` siempre empieza desde el principio.
        player.currentTime = 0.0
        player.play()
    }

    override fun detener() {
        if (liberado) return
        player.stop()
        player.currentTime = 0.0
    }

    override fun liberar() {
        if (liberado) return
        liberado = true
        player.stop()
        // Se suelta el delegado ANTES de dar el clip por muerto: si no, un aviso tardío llamaría a
        // una acción que ya no tiene sentido (quitar del mapa algo que ya se quitó).
        player.delegate = null
        delegado = null
    }

    override val reproduciendo: Boolean get() = !liberado && player.playing
}

/** En iOS sí hay fuente por defecto: el bundle es global y no hace falta inyectar nada. */
actual fun fuenteAudioPorDefecto(): PowAudioFuente = AudioDeAVFoundation()

/**
 * Delegado de `AVAudioPlayer` que traduce el aviso de fin de Cocoa a la lambda común.
 *
 * ⚠️ Se avisa **igual si terminó bien que si falló al decodificar**, que es lo que promete
 * `PowClip.alTerminar`: los dos casos significan "este clip ya no suena, sácalo del mapa".
 *
 * ⚠️ `[accion]` se llama UNA sola vez por clip: `AVAudioPlayer` no reenvía el aviso, pero la guarda
 * lo deja explícito por si alguien reutiliza el delegado.
 */
@OptIn(ExperimentalForeignApi::class)
private class DelegadoDeFin(private val accion: () -> Unit) : NSObject(), AVAudioPlayerDelegateProtocol {

    private var avisado = false

    private fun avisaUnaVez() {
        if (avisado) return
        avisado = true
        accion()
    }

    override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) = avisaUnaVez()

    override fun audioPlayerDecodeErrorDidOccur(player: AVAudioPlayer, error: NSError?) = avisaUnaVez()
}
