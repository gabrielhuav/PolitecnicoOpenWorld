package ovh.gabrielhuav.pow.features.streetfighter.viewmodel

import kotlinx.serialization.encodeToString
import ovh.gabrielhuav.pow.data.json.PowJson
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterId
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfStageCatalog
import platform.Foundation.NSUserDefaults

class IosStreetFighterEnvironment : StreetFighterEnvironment {
    private val defaults = NSUserDefaults.standardUserDefaults

    override val developerMode: Boolean get() = defaults.boolForKey("DEVELOPER_MODE")
    override val showHitboxes: Boolean get() = defaults.boolForKey("SHOW_HITBOXES")
    override val showSfFps: Boolean get() = defaults.boolForKey("SHOW_SF_FPS")
    override val showVoiceSubtitles: Boolean get() = defaults.boolForKey("SHOW_VOICE_SUBTITLES")
    override val arcade: SfArcadeStore = IosSfArcadeStore(defaults)
}

private class IosSfArcadeStore(
    private val defaults: NSUserDefaults,
) : SfArcadeStore {
    private companion object {
        const val FIGHTERS = "POW_SF_UNLOCKED_FIGHTERS_V2"
        const val MAPS = "POW_SF_UNLOCKED_MAPS_V2"
        const val STEP = "POW_SF_LADDER_STEP"
        const val SESSION = "POW_SF_ARCADE_SESSION_JSON"
        val DEFAULT_FIGHTERS = setOf("ESCOMBOY", "ESCOMGIRL", "ROBOT")
    }

    private fun readSet(key: String): Set<String> =
        defaults.stringForKey(key)
            ?.let { runCatching { PowJson.decodeFromString<List<String>>(it).toSet() }.getOrNull() }
            .orEmpty()

    private fun writeSet(key: String, values: Set<String>) {
        defaults.setObject(PowJson.encodeToString(values.toList().sorted()), forKey = key)
    }

    override fun unlockedFighters(): Set<String> = readSet(FIGHTERS) + DEFAULT_FIGHTERS

    override fun unlockFighter(id: String): Boolean {
        val current = unlockedFighters()
        val added = id !in current
        if (added) writeSet(FIGHTERS, current + id)
        runCatching { SfFighterId.valueOf(id) }.getOrNull()?.let { fighter ->
            val maps = SfStageCatalog.unlockableMapsForFighter(fighter)
            writeSet(MAPS, readSet(MAPS) + maps)
        }
        return added
    }

    override fun unlockedMaps(): Set<String> =
        readSet(MAPS) + SfStageCatalog.filesForStage(SfStageCatalog.ESCOM) +
            setOf("fondo_escom_anim.webp")

    override fun unlockMap(file: String): Boolean {
        val normalized = SfStageCatalog.normalizeFile(file)
        val family = SfStageCatalog.stageForFile(normalized)
            ?.let(SfStageCatalog::filesForStage)
            ?: setOf(normalized)
        val current = readSet(MAPS)
        if (family.all { it in current }) return false
        writeSet(MAPS, current + family)
        return true
    }

    override fun setLadderStep(step: Int) {
        if (step > defaults.integerForKey(STEP).toInt()) defaults.setInteger(step.toLong(), forKey = STEP)
    }

    override fun saveSession(session: SfArcadeSession) {
        defaults.setObject(PowJson.encodeToString(session), forKey = SESSION)
    }

    override fun loadSession(): SfArcadeSession? = defaults.stringForKey(SESSION)
        ?.let { runCatching { PowJson.decodeFromString<SfArcadeSession>(it) }.getOrNull() }

    override fun hasSession(): Boolean = !defaults.stringForKey(SESSION).isNullOrEmpty()
    override fun clearSession() = defaults.removeObjectForKey(SESSION)
}
