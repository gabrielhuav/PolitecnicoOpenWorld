package ovh.gabrielhuav.pow.platform.audio

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.AVFAudio.setActive
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL

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
        val player = AVAudioPlayer(NSURL.fileURLWithPath(absoluta), null) ?: return null
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

    /** No hay nada global que soltar: cada clip se libera solo. */
    override fun liberarTodo() = Unit
}

private class ClipAVAudio(private val player: AVAudioPlayer) : PowClip {

    private var liberado = false

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
    }

    override val reproduciendo: Boolean get() = !liberado && player.playing
}

/** En iOS sí hay fuente por defecto: el bundle es global y no hace falta inyectar nada. */
actual fun fuenteAudioPorDefecto(): PowAudioFuente = AudioDeAVFoundation()
