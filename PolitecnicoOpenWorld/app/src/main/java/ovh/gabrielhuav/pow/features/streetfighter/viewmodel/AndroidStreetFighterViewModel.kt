package ovh.gabrielhuav.pow.features.streetfighter.viewmodel

import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import ovh.gabrielhuav.pow.data.repository.CollectibleRepository
import ovh.gabrielhuav.pow.data.repository.SettingsRepository
import ovh.gabrielhuav.pow.data.repository.SfArcadeRepository
import ovh.gabrielhuav.pow.BuildConfig
import ovh.gabrielhuav.pow.features.streetfighter.data.isSfLowEnd
import java.io.File
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class AndroidStreetFighterViewModel @Inject constructor(
    @ApplicationContext context: Context,
    collectibleRepository: CollectibleRepository,
) : StreetFighterViewModel(AndroidStreetFighterEnvironment(context, collectibleRepository)) {
    override fun platformApplyRemoteSnapshot(sim: Sim, now: Long, dt: Float) =
        androidApplyRemoteSnapshot(sim, now, dt)

    override fun platformProcessNetDamage(sim: Sim, now: Long) =
        androidProcessNetDamage(sim, now)

    override fun platformAppendRemoteFireballs(sim: Sim, now: Long) =
        androidAppendRemoteFireballs(sim, now)

    override fun platformSendNetState(sim: Sim, now: Long) =
        androidSendNetState(sim, now)

    override fun platformCancelOnline(errorMsg: String?) = androidCancelOnline(errorMsg)
    override fun platformOnLocalLinkFailed(reason: String?) = androidOnLocalLinkFailed(reason)
    override fun platformStopBtScan() = androidStopBtScan()
}

private class AndroidStreetFighterEnvironment(
    private val context: Context,
    private val collectibleRepository: CollectibleRepository,
) : StreetFighterEnvironment {
    private val settings = SettingsRepository(context)
    private val repository = SfArcadeRepository(context)

    override val platformContext: Any = context
    override val lowEndDevice: Boolean = context.isSfLowEnd()
    override val developerMode: Boolean get() = settings.getDeveloperMode()
    override val showHitboxes: Boolean get() = settings.getShowHitboxes()
    override val showSfFps: Boolean get() = settings.getShowSfFps()
    override val showVoiceSubtitles: Boolean get() = settings.getShowVoiceSubtitles()
    override val languageTag: String get() = Locale.getDefault().language
    override val serverUrl: String get() = BuildConfig.SF_SERVER_URL
    override val arcade: SfArcadeStore = AndroidSfArcadeStore(repository)

    override suspend fun unlockFighterCollectible(id: String) {
        collectibleRepository.unlockFighterCollectible(id)
    }

    override fun writeGauntletReport(fileName: String, contents: String): String? = runCatching {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        File(dir, fileName).also { it.writeText(contents) }.absolutePath
    }.getOrNull()
}

private class AndroidSfArcadeStore(
    private val repository: SfArcadeRepository,
) : SfArcadeStore {
    override fun unlockedFighters() = repository.unlockedFighters()
    override fun unlockFighter(id: String) = repository.unlockFighter(id)
    override fun unlockedMaps() = repository.unlockedMaps()
    override fun unlockMap(file: String) = repository.unlockMap(file)
    override fun setLadderStep(step: Int) = repository.setLadderStep(step)
    override fun hasSession() = repository.hasSession()
    override fun clearSession() = repository.clearSession()

    override fun saveSession(session: SfArcadeSession) = repository.saveSession(
        SfArcadeRepository.ArcadeSession(
            playerId = session.playerId,
            step = session.step,
            total = session.total,
            ladderRivals = session.ladderRivals,
            mapFile = session.mapFile,
            playerRoundWins = session.playerRoundWins,
            cpuRoundWins = session.cpuRoundWins,
            difficulty = session.difficulty,
            baseDifficulty = session.baseDifficulty,
            paused = session.paused,
        ),
    )

    override fun loadSession(): SfArcadeSession? = repository.loadSession()?.let {
        SfArcadeSession(
            playerId = it.playerId,
            step = it.step,
            total = it.total,
            ladderRivals = it.ladderRivals,
            mapFile = it.mapFile,
            playerRoundWins = it.playerRoundWins,
            cpuRoundWins = it.cpuRoundWins,
            difficulty = it.difficulty,
            baseDifficulty = it.baseDifficulty,
            paused = it.paused,
        )
    }
}
