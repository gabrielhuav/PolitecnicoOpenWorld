package ovh.gabrielhuav.pow.platform.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.SoundPool

/**
 * Implementación de Android. Es EXACTAMENTE lo que ya hacía `StreetFighterScreen`: SoundPool para
 * los efectos y MediaPlayer para las pistas, con los mismos `AudioAttributes`. No se cambia el
 * comportamiento de la app que está en producción; solo se le pone una interfaz delante.
 *
 * @param maxFlujos golpes que pueden solaparse. 8 es lo que usaba el juego.
 */
class AudioDeAndroid(context: Context, maxFlujos: Int = 8) : PowAudioFuente {

    private val app = context.applicationContext

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(maxFlujos)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    override fun cargarEfecto(ruta: String): PowClip? = runCatching {
        app.assets.openFd(ruta).use { fd ->
            EfectoSoundPool(pool, pool.load(fd, 1))
        }
    }.getOrNull()

    override fun cargarPista(ruta: String): PowClip? = runCatching {
        val player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            app.assets.openFd(ruta).use { fd ->
                setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            }
            prepare()
        }
        PistaMediaPlayer(player)
    }.getOrNull()

    /**
     * `MediaMetadataRetriever` es lo que ya usaba el juego. ⚠️ El `release()` va en `finally`: si se
     * escapa una excepción sin soltarlo, se filtra un recurso nativo en cada consulta.
     */
    override fun duracionMs(ruta: String): Long? = runCatching {
        val lector = MediaMetadataRetriever()
        try {
            app.assets.openFd(ruta).use { fd ->
                lector.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            }
            lector.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } finally {
            lector.release()
        }
    }.getOrNull()

    override fun liberarTodo() {
        runCatching { pool.release() }
    }
}

/**
 * ⚠️ `SoundPool.load` es ASÍNCRONO: devuelve el id al instante pero el sonido no está listo hasta
 * que termina de decodificar. Si se llama a `play` antes, **no suena y no avisa de nada**. El
 * código original convivía con eso porque cargaba todo al entrar a la pantalla, mucho antes del
 * primer golpe; aquí se conserva el mismo comportamiento (un `play` demasiado pronto se pierde en
 * silencio) para no cambiar la app que ya funciona.
 */
private class EfectoSoundPool(private val pool: SoundPool, private val id: Int) : PowClip {

    private var flujo: Int = 0

    override fun reproducir(volumen: Float, bucle: Boolean) {
        // ⚠️ PARAR EL FLUJO ANTERIOR NO ES OPCIONAL. `SoundPool.play` abre un flujo NUEVO cada vez,
        // así que sin esto dos golpes seguidos del mismo sonido se SOLAPARÍAN y sonaría a eco. El
        // código original lo hacía a mano (`activeStreams[key]` → `soundPool.stop(...)` antes de
        // cada `play`); aquí se cumple el contrato de `reproducir`: siempre reinicia.
        detener()
        // El 4º parámetro es prioridad y el 5º el número de REPETICIONES: -1 = infinito, 0 = una vez.
        flujo = pool.play(id, volumen, volumen, 1, if (bucle) -1 else 0, 1f)
    }

    override fun detener() {
        if (flujo != 0) {
            pool.stop(flujo)
            flujo = 0
        }
    }

    override fun liberar() {
        detener()
        runCatching { pool.unload(id) }
    }

    /**
     * SoundPool NO expone si un flujo sigue sonando. Se devuelve `false` en vez de mentir: para un
     * efecto de golpe nadie consulta esto, y quien necesite saberlo debe usar una pista.
     */
    override val reproduciendo: Boolean get() = false

    /**
     * ⚠️ SoundPool **no avisa del fin de un flujo**: no hay ningún listener equivalente. Se ignora
     * en vez de simular el aviso con un temporizador basado en la duración, que quedaría desfasado
     * en cuanto alguien tocara la velocidad de reproducción.
     *
     * No es una limitación que moleste: el gancho existe para las VOCES, y las voces son pistas
     * ([cargarPista]), no efectos — precisamente porque SoundPool trunca los archivos largos.
     */
    override fun alTerminar(accion: (() -> Unit)?) = Unit
}

private class PistaMediaPlayer(private val player: MediaPlayer) : PowClip {

    private var liberado = false

    override fun alTerminar(accion: (() -> Unit)?) {
        if (liberado) return
        if (accion == null) {
            player.setOnCompletionListener(null)
            player.setOnErrorListener(null)
            return
        }
        player.setOnCompletionListener { accion() }
        // El MISMO gancho para el error, que es lo que ya hacía `playSfSpecial`: si un clip
        // revienta a mitad, hay que sacarlo del mapa igual que si hubiera acabado bien; si no, se
        // queda ahí bloqueando las interrupciones para siempre.
        // `true` = "el error queda atendido"; con `false`, MediaPlayer lo reenviaría como fatal.
        player.setOnErrorListener { _, _, _ ->
            accion()
            true
        }
    }

    override fun reproducir(volumen: Float, bucle: Boolean) {
        if (liberado) return
        runCatching {
            player.isLooping = bucle
            player.setVolume(volumen, volumen)
            // Rebobina: el contrato dice que `reproducir` siempre empieza desde el principio.
            player.seekTo(0)
            player.start()
        }
    }

    override fun detener() {
        if (liberado) return
        runCatching {
            if (player.isPlaying) player.pause()
            player.seekTo(0)
        }
    }

    override fun liberar() {
        if (liberado) return
        liberado = true
        runCatching { player.release() }
    }

    override val reproduciendo: Boolean
        get() = !liberado && runCatching { player.isPlaying }.getOrDefault(false)
}

/** En Android no hay fuente por defecto: hace falta un `Context` para abrir los assets. */
actual fun fuenteAudioPorDefecto(): PowAudioFuente =
    error(
        "PowAudio no está instalado. En Android añade esto a Application.onCreate():\n" +
            "    PowAudio.instalar(AudioDeAndroid(this))",
    )
