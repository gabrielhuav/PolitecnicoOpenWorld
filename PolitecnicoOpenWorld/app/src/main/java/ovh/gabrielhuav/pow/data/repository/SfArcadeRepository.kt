package ovh.gabrielhuav.pow.data.repository

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterId
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfStageCatalog

// Persistencia del MODO ARCADE de HUELUM VS. GOYA (SF POW). LOCAL, con SharedPreferences,
// igual que CampaignRepository/SettingsRepository (no hay base de datos ni Firestore: el
// proyecto solo tiene Firebase Auth). Guarda MÍNIMO: qué peleadores y qué mapas ha
// desbloqueado el jugador + hasta qué escalón de la escalera llegó. Diseño DATA-DRIVEN:
// guarda ids/archivos como texto, así el roster puede crecer sin migraciones.
//
// 🆕 (2026-07-18) SESIÓN EN CURSO: al pausar/minimizar se guarda un snapshot LIGERO
// (solo ids + marcador de rondas, un putString) para retomar sin perder progreso.
// NO se guarda cada frame (evitar lag en gama baja): solo en forcePause / salida.
//
// 🆕 (2026-07-18h) PERSONAJE ↔ MAPA: al desbloquear un peleadór se desbloquean las 3
// luces de su escenario hogar (día/noche/apocalipsis) para usarlo en práctica y
// multiplayer (Render / BT / LAN). Ver SfStageCatalog.homeStage.
//
// Defaults: 3 estudiantes ESCOM + mapas ESCOM (día/noche/apocalipsis).
class SfArcadeRepository(context: Context) {

    companion object {
        private const val PREFS_NAME = "pow_sf_arcade"
        private const val KEY_FIGHTERS = "UNLOCKED_FIGHTERS" // StringSet de SfFighterId.name
        private const val KEY_MAPS = "UNLOCKED_MAPS"          // StringSet de nombres de archivo
        private const val KEY_LADDER_STEP = "LADDER_STEP"     // escalón alcanzado (0 = ninguno)
        private const val KEY_SESSION = "ARCADE_SESSION_JSON" // snapshot pelea en curso (o null)
        private const val KEY_MAPS_MIGRATED = "MAPS_FROM_FIGHTERS_V1" // migración one-shot

        /** Peleadores desbloqueados de arranque: los 3 estudiantes (el jugador elige uno). */
        val DEFAULT_FIGHTERS = setOf("ESCOMBOY", "ESCOMGIRL", "ROBOT")
        /** Mapa desbloqueado de arranque (hogar de los starters = ESCOM día). */
        const val DEFAULT_MAP = "fondo_escom_anim.webp"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Snapshot mínimo de una pelea arcade a medias (para retomar tras salir/minimizar).
     * Sin bitmaps ni frames: solo strings/ints → write barato, sin lag.
     */
    data class ArcadeSession(
        val playerId: String,
        val step: Int,
        val total: Int,
        val ladderRivals: List<String>, // SfFighterId.name en orden
        val mapFile: String?,
        val playerRoundWins: Int,
        val cpuRoundWins: Int,
        val difficulty: String,
        val paused: Boolean = true,
    )

    // ── Peleadores ────────────────────────────────────────────────────────────

    /** Ids (SfFighterId.name) desbloqueados; siempre incluye los defaults de arranque. */
    fun unlockedFighters(): Set<String> {
        val raw = prefs.getStringSet(KEY_FIGHTERS, null)?.toSet() ?: DEFAULT_FIGHTERS
        return raw + DEFAULT_FIGHTERS
    }

    fun isFighterUnlocked(id: String): Boolean = id in unlockedFighters()

    /**
     * Marca un peleador como desbloqueado (idempotente).
     * **También desbloquea su mapa hogar** (día + noche + apocalipsis) para práctica/MP.
     * Devuelve true si el peleadór era nuevo.
     */
    fun unlockFighter(id: String): Boolean {
        val current = unlockedFighters()
        val isNew = id !in current
        if (isNew) {
            prefs.edit().putStringSet(KEY_FIGHTERS, current + id).apply()
        }
        // Siempre asegurar mapas del peleadór (migración / re-sync)
        unlockMapsForFighterName(id)
        return isNew
    }

    // ── Mapas ─────────────────────────────────────────────────────────────────

    /**
     * Archivos de mapa desbloqueados (SharedPreferences).
     * Incluye defaults + mapas de TODOS los peleadors desbloqueados (migración lazy).
     * Usado en práctica, arcade (selector no), multiplayer host (Render/BT/LAN).
     */
    fun unlockedMaps(): Set<String> {
        ensureMapsSyncedFromFighters()
        val stored = prefs.getStringSet(KEY_MAPS, null)
            ?.mapTo(mutableSetOf(), SfStageCatalog::normalizeFile)
            .orEmpty()
        val starterMaps = SfStageCatalog.filesForStage(SfStageCatalog.ESCOM)
        return stored + starterMaps + setOf(DEFAULT_MAP)
    }

    fun isMapUnlocked(file: String): Boolean = SfStageCatalog.normalizeFile(file) in unlockedMaps()

    /**
     * Desbloquea un archivo de mapa y **toda su familia** (día/noche/apocalipsis del mismo
     * escenario). Así, al ganar en arcade con un fondo de noche, el día también queda usable.
     */
    fun unlockMap(file: String): Boolean {
        val normalized = SfStageCatalog.normalizeFile(file)
        val family = SfStageCatalog.stageForFile(normalized)?.let { SfStageCatalog.filesForStage(it) }
            ?: setOf(normalized)
        val current = prefs.getStringSet(KEY_MAPS, null)
            ?.mapTo(mutableSetOf(), SfStageCatalog::normalizeFile)
            .orEmpty()
        if (family.all { it in current }) return false
        prefs.edit().putStringSet(KEY_MAPS, current + family).apply()
        return true
    }

    /** Desbloquea las 3 luces del mapa hogar del peleadór (por nombre de enum). */
    fun unlockMapsForFighterName(idName: String) {
        val id = runCatching { SfFighterId.valueOf(idName) }.getOrNull() ?: return
        val maps = SfStageCatalog.unlockableMapsForFighter(id)
        val current = prefs.getStringSet(KEY_MAPS, null)
            ?.mapTo(mutableSetOf(), SfStageCatalog::normalizeFile)
            .orEmpty()
        if (maps.all { it in current }) return
        prefs.edit().putStringSet(KEY_MAPS, current + maps).apply()
    }

    /**
     * One-shot / lazy: todo peleadór ya desbloqueado aporta su mapa al set de mapas
     * (partidas viejas que solo guardaban el fighter).
     */
    private fun ensureMapsSyncedFromFighters() {
        val fighters = unlockedFighters()
        val storedMaps = prefs.getStringSet(KEY_MAPS, null)?.toSet().orEmpty()
        var maps = storedMaps.mapTo(mutableSetOf(), SfStageCatalog::normalizeFile).toSet()
        var dirty = maps != storedMaps
        for (name in fighters) {
            val id = runCatching { SfFighterId.valueOf(name) }.getOrNull() ?: continue
            val family = SfStageCatalog.unlockableMapsForFighter(id)
            if (!family.all { it in maps }) {
                maps = maps + family
                dirty = true
            }
        }
        val starter = SfStageCatalog.filesForStage(SfStageCatalog.ESCOM)
        if (!starter.all { it in maps }) {
            maps = maps + starter
            dirty = true
        }
        if (dirty || !prefs.getBoolean(KEY_MAPS_MIGRATED, false)) {
            prefs.edit()
                .putStringSet(KEY_MAPS, maps)
                .putBoolean(KEY_MAPS_MIGRATED, true)
                .apply()
        }
    }

    // ── Progreso de la escalera ─────────────────────────────────────────────────

    /** Escalón más lejano alcanzado (0 = aún no empieza). */
    fun ladderStep(): Int = prefs.getInt(KEY_LADDER_STEP, 0)

    /** Guarda el escalón alcanzado si supera al anterior (no retrocede el récord). */
    fun setLadderStep(step: Int) {
        if (step > ladderStep()) prefs.edit().putInt(KEY_LADDER_STEP, step).apply()
    }

    /** Borra TODO el progreso del arcade (vuelve a los defaults). */
    fun reset() {
        prefs.edit().clear().apply()
    }

    // ── Sesión en curso (pausa / minimizar) ────────────────────────────────────

    /**
     * Guarda la pelea arcade a medias. Usa apply() (async): no bloquea el hilo UI.
     * Llamar SOLO desde forcePause / salir — nunca por tick.
     */
    fun saveSession(session: ArcadeSession) {
        val o = JSONObject()
            .put("playerId", session.playerId)
            .put("step", session.step)
            .put("total", session.total)
            .put("mapFile", session.mapFile?.let(SfStageCatalog::normalizeFile))
            .put("playerRoundWins", session.playerRoundWins)
            .put("cpuRoundWins", session.cpuRoundWins)
            .put("difficulty", session.difficulty)
            .put("paused", session.paused)
        val arr = JSONArray()
        session.ladderRivals.forEach { arr.put(it) }
        o.put("ladderRivals", arr)
        prefs.edit().putString(KEY_SESSION, o.toString()).apply()
    }

    /** Lee la sesión en curso; null si no hay o el JSON es inválido. */
    fun loadSession(): ArcadeSession? {
        val raw = prefs.getString(KEY_SESSION, null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            val arr = o.optJSONArray("ladderRivals") ?: JSONArray()
            val rivals = buildList {
                for (i in 0 until arr.length()) add(arr.getString(i))
            }
            ArcadeSession(
                playerId = o.getString("playerId"),
                step = o.getInt("step"),
                total = o.getInt("total"),
                ladderRivals = rivals,
                mapFile = (o.opt("mapFile") as? String)
                    ?.takeIf { it.isNotEmpty() && it != "null" }
                    ?.let(SfStageCatalog::normalizeFile),
                playerRoundWins = o.optInt("playerRoundWins", 0),
                cpuRoundWins = o.optInt("cpuRoundWins", 0),
                difficulty = o.optString("difficulty", "NORMAL"),
                paused = o.optBoolean("paused", true),
            )
        }.getOrNull()
    }

    fun hasSession(): Boolean = !prefs.getString(KEY_SESSION, null).isNullOrEmpty()

    /** Borra la sesión (al terminar pelea / abandonar / retomar y acabar). */
    fun clearSession() {
        prefs.edit().remove(KEY_SESSION).apply()
    }
}
