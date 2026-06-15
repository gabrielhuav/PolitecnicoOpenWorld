package ovh.gabrielhuav.pow.features.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log

class SoundManager private constructor(context: Context) {
    private var soundPool: SoundPool? = null
    
    private var walkSoundId = -1
    private var runSoundId = -1
    private var carSoundId = -1
    private var shootSoundId = -1
    private var punchSoundId = -1
    private var itemSoundId = -1
    private var zombieSoundId = -1

    private var walkStreamId = -1
    private var runStreamId = -1
    private var carStreamId = -1

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
            walkSoundId = soundPool?.load(assetManager.openFd("sonidos/caminar.mpeg"), 1) ?: -1
            runSoundId = soundPool?.load(assetManager.openFd("sonidos/correr.mpeg"), 1) ?: -1
            carSoundId = soundPool?.load(assetManager.openFd("sonidos/carro.mpeg"), 1) ?: -1
            shootSoundId = soundPool?.load(assetManager.openFd("sonidos/disparo.mp3"), 1) ?: -1
            punchSoundId = soundPool?.load(assetManager.openFd("sonidos/golpemano.mp3"), 1) ?: -1
            itemSoundId = soundPool?.load(assetManager.openFd("sonidos/items.mpeg"), 1) ?: -1
            zombieSoundId = soundPool?.load(assetManager.openFd("sonidos/zombie.mpeg"), 1) ?: -1
        } catch (e: Exception) {
            Log.e("SoundManager", "Error loading sounds", e)
        }
    }

    fun playWalk() {
        if (walkStreamId == -1) {
            walkStreamId = soundPool?.play(walkSoundId, 1f, 1f, 1, -1, 1f) ?: -1
        }
    }

    fun stopWalk() {
        if (walkStreamId != -1) {
            soundPool?.stop(walkStreamId)
            walkStreamId = -1
        }
    }

    fun playRun() {
        if (runStreamId == -1) {
            runStreamId = soundPool?.play(runSoundId, 1f, 1f, 1, -1, 1f) ?: -1
        }
    }

    fun stopRun() {
        if (runStreamId != -1) {
            soundPool?.stop(runStreamId)
            runStreamId = -1
        }
    }

    fun playCar() {
        if (carStreamId == -1) {
            carStreamId = soundPool?.play(carSoundId, 0.5f, 0.5f, 1, -1, 1f) ?: -1
        }
    }

    fun stopCar() {
        if (carStreamId != -1) {
            soundPool?.stop(carStreamId)
            carStreamId = -1
        }
    }

    fun playShoot() {
        soundPool?.play(shootSoundId, 1f, 1f, 1, 0, 1f)
    }

    fun playPunch() {
        soundPool?.play(punchSoundId, 1f, 1f, 1, 0, 1f)
    }

    fun playItem() {
        soundPool?.play(itemSoundId, 1f, 1f, 1, 0, 1f)
    }

    fun playZombieNear() {
        soundPool?.play(zombieSoundId, 1f, 1f, 1, 0, 1f)
    }

    fun release() {
        soundPool?.release()
        soundPool = null
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
