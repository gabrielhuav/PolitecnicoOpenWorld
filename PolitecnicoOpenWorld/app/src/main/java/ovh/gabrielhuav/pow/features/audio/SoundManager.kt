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
            walkSoundId = soundPool?.load(assetManager.openFd("sonidos/caminar.mpeg"), 1) ?: -1
            runSoundId = soundPool?.load(assetManager.openFd("sonidos/correr.mpeg"), 1) ?: -1
            carSoundId = soundPool?.load(assetManager.openFd("sonidos/carro.mpeg"), 1) ?: -1
            shootSoundId = soundPool?.load(assetManager.openFd("sonidos/disparo.mp3"), 1) ?: -1
            punchSoundId = soundPool?.load(assetManager.openFd("sonidos/golpemano.mp3"), 1) ?: -1
            itemSoundId = soundPool?.load(assetManager.openFd("sonidos/items.mpeg"), 1) ?: -1
            zombieSoundId = soundPool?.load(assetManager.openFd("sonidos/zombie.mpeg"), 1) ?: -1

            // Load Story Sounds
            flashSoundId = soundPool?.load(assetManager.openFd("sonidos/AudiosViñetas/CamaraFlash.mp3"), 1) ?: -1
            runningSoundId = soundPool?.load(assetManager.openFd("sonidos/AudiosViñetas/running.mp3"), 1) ?: -1
            crystalSoundId = soundPool?.load(assetManager.openFd("sonidos/AudiosViñetas/Cristal.mp3"), 1) ?: -1
            hitSoundId = soundPool?.load(assetManager.openFd("sonidos/AudiosViñetas/golpe.mp3"), 1) ?: -1
            police1SoundId = soundPool?.load(assetManager.openFd("sonidos/AudiosViñetas/policia1.mp3"), 1) ?: -1
            police2SoundId = soundPool?.load(assetManager.openFd("sonidos/AudiosViñetas/polocia2.mp3"), 1) ?: -1
            bottleSoundId = soundPool?.load(assetManager.openFd("sonidos/AudiosViñetas/botella cayendo.mp3"), 1) ?: -1
            doorOpenSoundId = soundPool?.load(assetManager.openFd("sonidos/AudiosViñetas/puertaabriendose.mp3"), 1) ?: -1
            scareSoundId = soundPool?.load(assetManager.openFd("sonidos/AudiosViñetas/susto.mp3"), 1) ?: -1
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

    // Story Methods
    fun playFlash() {
        soundPool?.play(flashSoundId, 1f, 1f, 1, 0, 1f)
    }

    fun playStoryRunning(loop: Boolean = false) {
        if (loop && storyRunningStreamId != -1) return // already playing loop
        val streamId = soundPool?.play(runningSoundId, 1f, 1f, 1, if (loop) -1 else 0, 1f) ?: -1
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
        val streamId = soundPool?.play(crystalSoundId, 1f, 1f, 1, 0, 1f) ?: -1
        if (streamId != -1) storyStreamIds.add(streamId)
    }

    fun playHitWall() {
        soundPool?.play(hitSoundId, 1f, 1f, 1, 0, 1f)
    }

    fun playPolice1() {
        val streamId = soundPool?.play(police1SoundId, 1f, 1f, 1, 0, 1f) ?: -1
        if (streamId != -1) storyStreamIds.add(streamId)
    }

    fun playPolice2(loop: Boolean = false) {
        if (loop && police2StreamId != -1) return // already playing loop
        val streamId = soundPool?.play(police2SoundId, 0.7f, 0.7f, 1, if (loop) -1 else 0, 1f) ?: -1
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
        soundPool?.play(bottleSoundId, 1f, 1f, 1, 0, 1f)
    }

    fun playDoorOpen() {
        soundPool?.play(doorOpenSoundId, 1f, 1f, 1, 0, 1f)
    }

    fun playScare() {
        soundPool?.play(scareSoundId, 1f, 1f, 1, 0, 1f)
    }

    fun stopAllStorySounds() {
        storyStreamIds.forEach { soundPool?.stop(it) }
        storyStreamIds.clear()
        storyRunningStreamId = -1
        police2StreamId = -1
        stopWalk()
        stopRun()
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
