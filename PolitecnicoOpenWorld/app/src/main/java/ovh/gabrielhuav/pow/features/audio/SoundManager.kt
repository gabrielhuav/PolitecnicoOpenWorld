package ovh.gabrielhuav.pow.features.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.util.Log

class SoundManager private constructor(context: Context) {
    private var soundPool: SoundPool? = null

    // ─── Volumen (Ajustes → Audio), 0f..1f. Persistido en SettingsRepository. ─────────────
    //   sfxVolume   = efectos (SoundPool): se multiplica en cada play() y se aplica en vivo
    //                 a los streams en loop (caminar/correr/carro/…) vía setSfxVolume.
    //   musicVolume = música de fondo (MediaPlayer): se aplica con setVolume a cada pista.
    @Volatile private var sfxVolume = 1f
    @Volatile private var musicVolume = 1f

    // Background Music MediaPlayers
    private var investigarMediaPlayer: MediaPlayer? = null
    private var lugarSeguroMediaPlayer: MediaPlayer? = null
    private var mainSoundMediaPlayer: MediaPlayer? = null
    // Música de fondo del CÓMIC de la intro (IntroPOW1..8 del Modo Historia).
    private var prankedyRemixMediaPlayer: MediaPlayer? = null
    
    private var walkSoundId = -1
    private var runSoundId = -1
    private var carSoundId = -1
    private var shootSoundId = -1
    private var punchSoundId = -1
    private var itemSoundId = -1
    private var zombieSoundId = -1

    // Story Sounds
    private var flashSoundId = -1
    private var runningSoundId = -1
    private var crystalSoundId = -1
    private var hitSoundId = -1
    private var police1SoundId = -1
    private var police2SoundId = -1
    private var bottleSoundId = -1
    private var doorOpenSoundId = -1
    private var scareSoundId = -1
    private var queTeTraesSoundId = -1
    private var contestameSoundId = -1
    private var paraleSoundId = -1
    private var puerquitoSoundId = -1        // SFX en IntroPOW5 del cómic
    private var misionCumplidaSoundId = -1   // jingle al cumplir la misión
    private var zombiesAreComingSoundId = -1 // SFX "los zombis vienen" en IntroPOW8 del cómic

    private var walkStreamId = -1
    private var runStreamId = -1
    private var carStreamId = -1
    
    // Story Streams
    private var storyStreamIds = mutableListOf<Int>()
    private var storyRunningStreamId = -1
    private var police2StreamId = -1

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
            
        soundPool = SoundPool.Builder()
            .setMaxStreams(10)
            .setAudioAttributes(audioAttributes)
            .build()

        try {
            val assetManager = context.assets
            walkSoundId = soundPool?.load(assetManager.openFd("AUDIO/caminar.mpeg"), 1) ?: -1
            runSoundId = soundPool?.load(assetManager.openFd("AUDIO/correr.mpeg"), 1) ?: -1
            carSoundId = soundPool?.load(assetManager.openFd("AUDIO/carro.mpeg"), 1) ?: -1
            shootSoundId = soundPool?.load(assetManager.openFd("AUDIO/disparo.mp3"), 1) ?: -1
            punchSoundId = soundPool?.load(assetManager.openFd("AUDIO/golpemano.mp3"), 1) ?: -1
            itemSoundId = soundPool?.load(assetManager.openFd("AUDIO/items.mpeg"), 1) ?: -1
            zombieSoundId = soundPool?.load(assetManager.openFd("AUDIO/zombie.mpeg"), 1) ?: -1

            // Load Story Sounds
            flashSoundId = soundPool?.load(assetManager.openFd("AUDIO/COMIC/CamaraFlash.mp3"), 1) ?: -1
            runningSoundId = soundPool?.load(assetManager.openFd("AUDIO/COMIC/running.mp3"), 1) ?: -1
            crystalSoundId = soundPool?.load(assetManager.openFd("AUDIO/COMIC/Cristal.mp3"), 1) ?: -1
            hitSoundId = soundPool?.load(assetManager.openFd("AUDIO/COMIC/golpe.mp3"), 1) ?: -1
            police1SoundId = soundPool?.load(assetManager.openFd("AUDIO/COMIC/policia1.mp3"), 1) ?: -1
            police2SoundId = soundPool?.load(assetManager.openFd("AUDIO/COMIC/policia2.mp3"), 1) ?: -1
            bottleSoundId = soundPool?.load(assetManager.openFd("AUDIO/COMIC/botella_cayendo.mp3"), 1) ?: -1
            doorOpenSoundId = soundPool?.load(assetManager.openFd("AUDIO/COMIC/puertaabriendose.mp3"), 1) ?: -1
            scareSoundId = soundPool?.load(assetManager.openFd("AUDIO/COMIC/susto.mp3"), 1) ?: -1
            
            // Mission 1 Sounds
            queTeTraesSoundId = soundPool?.load(assetManager.openFd("AUDIO/que_te_traes.m4a"), 1) ?: -1
            contestameSoundId = soundPool?.load(assetManager.openFd("AUDIO/contestame.m4a"), 1) ?: -1
            paraleSoundId = soundPool?.load(assetManager.openFd("AUDIO/parale.m4a"), 1) ?: -1
            // SFX del cómic (IntroPOW5) y jingle de misión cumplida.
            puerquitoSoundId = soundPool?.load(assetManager.openFd("AUDIO/COMIC/Puerquito.mp3"), 1) ?: -1
            misionCumplidaSoundId = soundPool?.load(assetManager.openFd("AUDIO/MisionCumplida.mp3"), 1) ?: -1
            zombiesAreComingSoundId = soundPool?.load(assetManager.openFd("AUDIO/zombiesAreComing.mp3"), 1) ?: -1
        } catch (e: Exception) {
            Log.e("SoundManager", "Error loading sounds", e)
        }

        try {
            context.assets.openFd("AUDIO/BGM/investigar.wav").use { afd ->
                investigarMediaPlayer = MediaPlayer().apply {
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    prepare()
                    isLooping = true
                }
            }
            context.assets.openFd("AUDIO/BGM/lugarseguro.wav").use { afd ->
                lugarSeguroMediaPlayer = MediaPlayer().apply {
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    prepare()
                    isLooping = true
                }
            }
            context.assets.openFd("AUDIO/BGM/mainsound.wav").use { afd ->
                mainSoundMediaPlayer = MediaPlayer().apply {
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    prepare()
                    isLooping = true
                }
            }
        } catch (e: Exception) {
            Log.e("SoundManager", "Error loading background music", e)
        }

        // Música del cómic de la intro (en su propio try: si el archivo aún no existe, el
        // resto del audio sigue funcionando). Colócalo en:
        //   app/src/main/assets/AUDIO/BGM/musicaPrankedyRemix.mp3
        try {
            context.assets.openFd("AUDIO/BGM/musicaPrankedyRemix.mp3").use { afd ->
                prankedyRemixMediaPlayer = MediaPlayer().apply {
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    prepare()
                    isLooping = true
                }
            }
        } catch (e: Exception) {
            Log.e("SoundManager", "Error loading musicaPrankedyRemix.mp3 (¿falta el archivo en assets/AUDIO/BGM/?)", e)
        }

        // Carga el volumen guardado (Ajustes → Audio) y lo aplica a la música ya cargada.
        try {
            val repo = ovh.gabrielhuav.pow.data.repository.SettingsRepository(context)
            sfxVolume = repo.getSfxVolume()
            musicVolume = repo.getMusicVolume()
            applyMusicVolume()
        } catch (e: Exception) {
            Log.e("SoundManager", "Error loading saved volumes", e)
        }
    }

    // ─── Volumen: API para Ajustes → Audio ────────────────────────────────────
    private fun applyMusicVolume() {
        listOfNotNull(investigarMediaPlayer, lugarSeguroMediaPlayer, mainSoundMediaPlayer, prankedyRemixMediaPlayer).forEach { mp ->
            try { mp.setVolume(musicVolume, musicVolume) } catch (e: Exception) { /* ignora */ }
        }
    }

    /** Volumen de la MÚSICA de fondo (0f..1f). Se aplica a todas las pistas en vivo. */
    fun setMusicVolume(v: Float) {
        musicVolume = v.coerceIn(0f, 1f)
        applyMusicVolume()
    }

    /** Volumen de los EFECTOS (0f..1f). Afecta los nuevos play() y los streams en loop activos. */
    fun setSfxVolume(v: Float) {
        sfxVolume = v.coerceIn(0f, 1f)
        // Actualiza en vivo los streams en loop (respetando su volumen base relativo).
        if (walkStreamId != -1) soundPool?.setVolume(walkStreamId, sfxVolume, sfxVolume)
        if (runStreamId != -1) soundPool?.setVolume(runStreamId, sfxVolume, sfxVolume)
        if (carStreamId != -1) soundPool?.setVolume(carStreamId, 0.5f * sfxVolume, 0.5f * sfxVolume)
        if (storyRunningStreamId != -1) soundPool?.setVolume(storyRunningStreamId, sfxVolume, sfxVolume)
        if (police2StreamId != -1) soundPool?.setVolume(police2StreamId, 0.7f * sfxVolume, 0.7f * sfxVolume)
    }

    fun playWalk() {
        // FIX (2026-06-21): SoundPool.play() devuelve 0 si el sample AÚN NO terminó de cargar (la carga
        // es ASÍNCRONA y no hay listener de "ready"). El guard viejo (== -1) latcheaba ese 0 y nunca
        // reintentaba → al caminar al spawn (antes de que caminar.mpeg cargara) el sonido quedaba MUDO
        // para siempre (el coche se salvaba porque conduces segundos después, ya cargado). Ahora solo
        // guardamos un streamId VÁLIDO (>0) y reintentamos mientras play() devuelva 0.
        if (walkStreamId <= 0) {
            val sid = soundPool?.play(walkSoundId, sfxVolume, sfxVolume, 1, -1, 1f) ?: 0
            if (sid > 0) walkStreamId = sid
        }
    }

    fun stopWalk() {
        if (walkStreamId != -1) {
            soundPool?.stop(walkStreamId)
            walkStreamId = -1
        }
    }

    fun playRun() {
        // Mismo fix que playWalk: solo latchea un streamId válido (>0); reintenta mientras play()=0.
        if (runStreamId <= 0) {
            val sid = soundPool?.play(runSoundId, sfxVolume, sfxVolume, 1, -1, 1f) ?: 0
            if (sid > 0) runStreamId = sid
        }
    }

    fun stopRun() {
        if (runStreamId != -1) {
            soundPool?.stop(runStreamId)
            runStreamId = -1
        }
    }

    fun playCar() {
        // Mismo fix que playWalk (robusto ante la carga async; el coche ya sonaba pero por coherencia).
        if (carStreamId <= 0) {
            val sid = soundPool?.play(carSoundId, 0.5f * sfxVolume, 0.5f * sfxVolume, 1, -1, 1f) ?: 0
            if (sid > 0) carStreamId = sid
        }
    }

    fun stopCar() {
        if (carStreamId != -1) {
            soundPool?.stop(carStreamId)
            carStreamId = -1
        }
    }

    fun playShoot() {
        soundPool?.play(shootSoundId, sfxVolume, sfxVolume, 1, 0, 1f)
    }

    fun playPunch() {
        soundPool?.play(punchSoundId, sfxVolume, sfxVolume, 1, 0, 1f)
    }

    fun playItem() {
        soundPool?.play(itemSoundId, sfxVolume, sfxVolume, 1, 0, 1f)
    }

    fun playZombieNear() {
        soundPool?.play(zombieSoundId, sfxVolume, sfxVolume, 1, 0, 1f)
    }

    // Story Methods
    fun playFlash() {
        soundPool?.play(flashSoundId, sfxVolume, sfxVolume, 1, 0, 1f)
    }

    fun playStoryRunning(loop: Boolean = false) {
        if (loop && storyRunningStreamId != -1) return // already playing loop
        val streamId = soundPool?.play(runningSoundId, sfxVolume, sfxVolume, 1, if (loop) -1 else 0, 1f) ?: -1
        if (streamId != -1) {
            storyStreamIds.add(streamId)
            if (loop) storyRunningStreamId = streamId
        }
    }

    fun stopStoryRunning() {
        if (storyRunningStreamId != -1) {
            soundPool?.stop(storyRunningStreamId)
            storyStreamIds.remove(storyRunningStreamId)
            storyRunningStreamId = -1
        }
    }

    fun playCrystal() {
        val streamId = soundPool?.play(crystalSoundId, sfxVolume, sfxVolume, 1, 0, 1f) ?: -1
        if (streamId != -1) storyStreamIds.add(streamId)
    }

    fun playHitWall() {
        soundPool?.play(hitSoundId, sfxVolume, sfxVolume, 1, 0, 1f)
    }

    fun playPolice1() {
        val streamId = soundPool?.play(police1SoundId, sfxVolume, sfxVolume, 1, 0, 1f) ?: -1
        if (streamId != -1) storyStreamIds.add(streamId)
    }

    fun playPolice2(loop: Boolean = false) {
        if (loop && police2StreamId != -1) return // already playing loop
        val streamId = soundPool?.play(police2SoundId, 0.7f * sfxVolume, 0.7f * sfxVolume, 1, if (loop) -1 else 0, 1f) ?: -1
        if (streamId != -1) {
            storyStreamIds.add(streamId)
            if (loop) police2StreamId = streamId
        }
    }

    fun stopPolice2() {
        if (police2StreamId != -1) {
            soundPool?.stop(police2StreamId)
            storyStreamIds.remove(police2StreamId)
            police2StreamId = -1
        }
    }

    fun playBottleFalling() {
        soundPool?.play(bottleSoundId, sfxVolume, sfxVolume, 1, 0, 1f)
    }

    fun playDoorOpen() {
        soundPool?.play(doorOpenSoundId, sfxVolume, sfxVolume, 1, 0, 1f)
    }

    fun playScare() {
        soundPool?.play(scareSoundId, sfxVolume, sfxVolume, 1, 0, 1f)
    }

    /** SFX "Zombies Are Coming" — suena en el panel IntroPOW8 del cómic de la intro. */
    fun playZombiesAreComing() {
        val streamId = soundPool?.play(zombiesAreComingSoundId, sfxVolume, sfxVolume, 1, 0, 1f) ?: -1
        if (streamId != -1) storyStreamIds.add(streamId)
    }

    // Mission 1 Methods
    fun playQueTeTraes() {
        val streamId = soundPool?.play(queTeTraesSoundId, sfxVolume, sfxVolume, 1, 0, 1f) ?: -1
        if (streamId != -1) storyStreamIds.add(streamId)
    }

    fun playContestame() {
        val streamId = soundPool?.play(contestameSoundId, sfxVolume, sfxVolume, 1, 0, 1f) ?: -1
        if (streamId != -1) storyStreamIds.add(streamId)
    }

    fun playParale() {
        val streamId = soundPool?.play(paraleSoundId, sfxVolume, sfxVolume, 1, 0, 1f) ?: -1
        if (streamId != -1) storyStreamIds.add(streamId)
    }

    // SFX del panel IntroPOW5 del cómic (se detiene con stopAllStorySounds al cambiar de panel).
    fun playPuerquito() {
        val streamId = soundPool?.play(puerquitoSoundId, sfxVolume, sfxVolume, 1, 0, 1f) ?: -1
        if (streamId != -1) storyStreamIds.add(streamId)
    }

    // Jingle al CUMPLIR la misión. Se REGISTRA en storyStreamIds para que `stopAllStorySounds`
    // lo corte cuando arranca el cómic (si no, seguía sonando durante toda la secuencia).
    fun playMisionCumplida() {
        val streamId = soundPool?.play(misionCumplidaSoundId, sfxVolume, sfxVolume, 1, 0, 1f) ?: -1
        if (streamId != -1) storyStreamIds.add(streamId)
    }

    fun stopAllStorySounds() {
        storyStreamIds.forEach { soundPool?.stop(it) }
        storyStreamIds.clear()
        storyRunningStreamId = -1
        police2StreamId = -1
        stopWalk()
        stopRun()
    }

    // Background Music Methods
    fun playInvestigarMusic() {
        investigarMediaPlayer?.let { if (!it.isPlaying) it.start() }
    }
    
    fun stopInvestigarMusic() {
        investigarMediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                it.seekTo(0)
            }
        }
    }

    fun playLugarSeguroMusic() {
        lugarSeguroMediaPlayer?.let { if (!it.isPlaying) it.start() }
    }
    
    fun stopLugarSeguroMusic() {
        lugarSeguroMediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                it.seekTo(0)
            }
        }
    }

    fun playMainMusic() {
        mainSoundMediaPlayer?.let { if (!it.isPlaying) it.start() }
    }

    fun stopMainMusic() {
        mainSoundMediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                it.seekTo(0)
            }
        }
    }

    // Música de fondo del cómic de la intro (IntroPOW1..8). Suena en bucle mientras se ve la
    // secuencia y se detiene al salir de ella.
    fun playPrankedyRemixMusic() {
        prankedyRemixMediaPlayer?.let { if (!it.isPlaying) it.start() }
    }

    fun stopPrankedyRemixMusic() {
        prankedyRemixMediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                it.seekTo(0)
            }
        }
    }

    // ── Pausa/Reanudación al salir/volver de la app (Activity.onPause/onResume) ──
    // Pistas de fondo (MediaPlayer) que estaban sonando al irse a segundo plano, para
    // reanudar exactamente las mismas al volver.
    private val pausedForBackground = mutableListOf<MediaPlayer>()

    /**
     * Pausa TODO el audio porque la app pasa a segundo plano. Antes, la música del
     * Modo Historia (MediaPlayer en loop) seguía sonando aunque salieras de la app.
     * Recuerda qué pistas estaban activas para reanudarlas en [resumeAllFromBackground].
     */
    fun pauseAllForBackground() {
        // SFX/streams (caminar, correr, sonidos de historia en loop, etc.)
        soundPool?.autoPause()
        // Música de fondo
        pausedForBackground.clear()
        listOfNotNull(investigarMediaPlayer, lugarSeguroMediaPlayer, mainSoundMediaPlayer, prankedyRemixMediaPlayer).forEach { mp ->
            try {
                if (mp.isPlaying) {
                    mp.pause()
                    pausedForBackground.add(mp)
                }
            } catch (e: Exception) {
                Log.e("SoundManager", "Error pausing music for background", e)
            }
        }
    }

    /** Reanuda el audio que se pausó al ir a segundo plano (Activity.onResume). */
    fun resumeAllFromBackground() {
        soundPool?.autoResume()
        pausedForBackground.forEach { mp ->
            try { mp.start() } catch (e: Exception) { Log.e("SoundManager", "Error resuming music", e) }
        }
        pausedForBackground.clear()
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        investigarMediaPlayer?.release()
        investigarMediaPlayer = null
        lugarSeguroMediaPlayer?.release()
        lugarSeguroMediaPlayer = null
        mainSoundMediaPlayer?.release()
        mainSoundMediaPlayer = null
        prankedyRemixMediaPlayer?.release()
        prankedyRemixMediaPlayer = null
    }

    companion object {
        @Volatile
        private var INSTANCE: SoundManager? = null

        fun getInstance(context: Context): SoundManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SoundManager(context).also { INSTANCE = it }
            }
        }
    }
}
