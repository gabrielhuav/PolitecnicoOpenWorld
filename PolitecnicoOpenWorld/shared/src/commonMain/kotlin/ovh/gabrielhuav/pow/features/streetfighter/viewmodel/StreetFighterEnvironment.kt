package ovh.gabrielhuav.pow.features.streetfighter.viewmodel

import kotlinx.serialization.Serializable

interface StreetFighterEnvironment {
    val platformContext: Any? get() = null
    val lowEndDevice: Boolean get() = false
    val developerMode: Boolean get() = false
    val showHitboxes: Boolean get() = false
    val showSfFps: Boolean get() = false
    val showVoiceSubtitles: Boolean get() = false
    val languageTag: String get() = "es"
    val serverUrl: String get() = ""
    val arcade: SfArcadeStore

    suspend fun unlockFighterCollectible(id: String) = Unit
    fun writeGauntletReport(fileName: String, contents: String): String? = null
}

interface SfArcadeStore {
    fun unlockedFighters(): Set<String>
    fun unlockFighter(id: String): Boolean
    fun unlockedMaps(): Set<String>
    fun unlockMap(file: String): Boolean
    fun setLadderStep(step: Int)
    fun saveSession(session: SfArcadeSession)
    fun loadSession(): SfArcadeSession?
    fun hasSession(): Boolean
    fun clearSession()
}

@Serializable
data class SfArcadeSession(
    val playerId: String,
    val step: Int,
    val total: Int,
    val ladderRivals: List<String>,
    val mapFile: String?,
    val playerRoundWins: Int,
    val cpuRoundWins: Int,
    val difficulty: String,
    val baseDifficulty: String = "",
    val paused: Boolean = true,
)

private object EmptySfArcadeStore : SfArcadeStore {
    override fun unlockedFighters() = setOf("ESCOMBOY", "ESCOMGIRL", "ROBOT")
    override fun unlockFighter(id: String) = false
    override fun unlockedMaps() = setOf("fondo_escom_anim.webp")
    override fun unlockMap(file: String) = false
    override fun setLadderStep(step: Int) = Unit
    override fun saveSession(session: SfArcadeSession) = Unit
    override fun loadSession(): SfArcadeSession? = null
    override fun hasSession() = false
    override fun clearSession() = Unit
}

object DefaultStreetFighterEnvironment : StreetFighterEnvironment {
    override val arcade: SfArcadeStore = EmptySfArcadeStore
}
