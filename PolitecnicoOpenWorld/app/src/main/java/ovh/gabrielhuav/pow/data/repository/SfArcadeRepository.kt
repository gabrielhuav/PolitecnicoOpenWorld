package ovh.gabrielhuav.pow.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.russhwolf.settings.SharedPreferencesSettings
import com.russhwolf.settings.Settings
import kotlinx.serialization.encodeToString
import ovh.gabrielhuav.pow.data.json.PowJson
import ovh.gabrielhuav.pow.data.json.getInt
import ovh.gabrielhuav.pow.data.json.getString
import ovh.gabrielhuav.pow.data.json.jsonOf
import ovh.gabrielhuav.pow.data.json.optBoolean
import ovh.gabrielhuav.pow.data.json.optInt
import ovh.gabrielhuav.pow.data.json.optJSONArray
import ovh.gabrielhuav.pow.data.json.optString
import ovh.gabrielhuav.pow.data.json.powJsonObjeto
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterId
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfStageCatalog

// Persistencia del MODO ARCADE de TITULACIÓN POR COMBATE (SF POW). LOCAL, con SharedPreferences,
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
        // 🍏 Fase 4: multiplatform-settings NO tiene conjuntos, así que los desbloqueos pasan a
        // guardarse como ARRAY JSON en claves nuevas. Las viejas (StringSet) se leen UNA vez para
        // migrarlas: sin eso el jugador perdería peleadores y mapas ya ganados.
        private const val KEY_FIGHTERS_V2 = "UNLOCKED_FIGHTERS_V2"
        private const val KEY_MAPS_V2 = "UNLOCKED_MAPS_V2"

        /** Peleadores desbloqueados de arranque: los 3 estudiantes (el jugador elige uno). */
        val DEFAULT_FIGHTERS = setOf("ESCOMBOY", "ESCOMGIRL", "ROBOT")
        /** Mapa desbloqueado de arranque (hogar de los starters = ESCOM día). */
        const val DEFAULT_MAP = "fondo_escom_anim.webp"

        /**
         * Escribe exactamente el snapshot que producía `JSONObject`: conserva el orden y omite
         * `mapFile` cuando es null. `jsonOf` es obligatorio aquí porque un `Map<String, Any?>`
         * pasado a `PowJson.encodeToString` compila pero falla en runtime.
         */
        internal fun encodeSession(session: ArcadeSession): String = jsonOf(
            "v" to 2,
            "playerId" to session.playerId,
            "step" to session.step,
            "total" to session.total,
            "mapFile" to session.mapFile?.let(SfStageCatalog::normalizeFile),
            "playerRoundWins" to session.playerRoundWins,
            "cpuRoundWins" to session.cpuRoundWins,
            "difficulty" to session.difficulty,
            "baseDifficulty" to session.baseDifficulty,
            "paused" to session.paused,
            "ladderRivals" to session.ladderRivals,
        )

        /**
         * Lee snapshots de todas las versiones con la semántica tolerante de `org.json`.
         * Los tres campos obligatorios siguen lanzando dentro del `runCatching`: un save truncado
         * no se convierte silenciosamente en una sesión distinta.
         */
        internal fun decodeSession(raw: String): ArcadeSession? = runCatching {
            val o = powJsonObjeto(raw)
            val rivals = o.optJSONArray("ladderRivals")
                ?.let { arr -> List(arr.size) { index -> arr.getString(index) } }
                .orEmpty()
            ArcadeSession(
                playerId = o.getString("playerId"),
                step = o.getInt("step"),
                total = o.getInt("total"),
                ladderRivals = rivals,
                // Algunas versiones antiguas guardaron el texto literal "null": se conserva.
                mapFile = o.optString("mapFile")
                    .takeIf { it.isNotEmpty() && it != "null" }
                    ?.let(SfStageCatalog::normalizeFile),
                playerRoundWins = o.optInt("playerRoundWins", 0),
                cpuRoundWins = o.optInt("cpuRoundWins", 0),
                difficulty = o.optString("difficulty", "NORMAL"),
                baseDifficulty = o.optString("baseDifficulty", ""),
                paused = o.optBoolean("paused", true),
            )
        }.getOrNull()
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val settings: Settings = SharedPreferencesSettings(prefs)

    /**
     * Lee un conjunto de la clave NUEVA (array JSON). Si aún no existe, lo migra desde el
     * `StringSet` ANTIGUO y lo reescribe en el formato nuevo. Devuelve null si no hay ninguno
     * de los dos (jugador nuevo) para que el llamador aplique sus defaults.
     *
     * ⚠️ `prefs` se conserva SOLO por esta migración (es lo único que queda atado a Android en
     * esta clase). Cuando ya no queden instalaciones viejas, se puede borrar el fallback.
     */
    private fun leerConjunto(claveNueva: String, claveVieja: String): Set<String>? {
        settings.getStringOrNull(claveNueva)?.let { json ->
            runCatching { PowJson.decodeFromString<List<String>>(json).toSet() }
                .getOrNull()?.let { return it }
        }
        val viejo = prefs.getStringSet(claveVieja, null)?.toSet() ?: return null
        guardarConjunto(claveNueva, viejo)
        return viejo
    }

    private fun guardarConjunto(clave: String, valor: Set<String>) {
        settings.putString(clave, PowJson.encodeToString(valor.toList().sorted()))
    }

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
        // 🆕 (2026-07-20) Dificultad BASE elegida (Fácil/Medio/Difícil). Antes solo se
        // guardaba la de la PELEA (sube en jefes) y al retomar se INFERÍA la base con
        // pérdida → cambiaban reglas de desbloqueo e iluminación. "" = sesión vieja
        // (el VM cae a la inferencia legacy).
        val baseDifficulty: String = "",
        val paused: Boolean = true,
    )

    // ── Peleadores ────────────────────────────────────────────────────────────

    /** Ids (SfFighterId.name) desbloqueados; siempre incluye los defaults de arranque. */
    fun unlockedFighters(): Set<String> {
        val raw = leerConjunto(KEY_FIGHTERS_V2, KEY_FIGHTERS) ?: DEFAULT_FIGHTERS
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
            guardarConjunto(KEY_FIGHTERS_V2, current + id)
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
        val stored = leerConjunto(KEY_MAPS_V2, KEY_MAPS)
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
        val current = leerConjunto(KEY_MAPS_V2, KEY_MAPS)
            ?.mapTo(mutableSetOf(), SfStageCatalog::normalizeFile)
            .orEmpty()
        if (family.all { it in current }) return false
        guardarConjunto(KEY_MAPS_V2, current + family)
        return true
    }

    /** Desbloquea las 3 luces del mapa hogar del peleadór (por nombre de enum). */
    fun unlockMapsForFighterName(idName: String) {
        val id = runCatching { SfFighterId.valueOf(idName) }.getOrNull() ?: return
        val maps = SfStageCatalog.unlockableMapsForFighter(id)
        val current = leerConjunto(KEY_MAPS_V2, KEY_MAPS)
            ?.mapTo(mutableSetOf(), SfStageCatalog::normalizeFile)
            .orEmpty()
        if (maps.all { it in current }) return
        guardarConjunto(KEY_MAPS_V2, current + maps)
    }

    /**
     * One-shot / lazy: todo peleadór ya desbloqueado aporta su mapa al set de mapas
     * (partidas viejas que solo guardaban el fighter).
     */
    private fun ensureMapsSyncedFromFighters() {
        val fighters = unlockedFighters()
        val storedMaps = leerConjunto(KEY_MAPS_V2, KEY_MAPS).orEmpty()
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
        if (dirty || !settings.getBoolean(KEY_MAPS_MIGRATED, false)) {
            guardarConjunto(KEY_MAPS_V2, maps)
            settings.putBoolean(KEY_MAPS_MIGRATED, true)
        }
    }

    // ── Progreso de la escalera ─────────────────────────────────────────────────

    /** Escalón más lejano alcanzado (0 = aún no empieza). */
    fun ladderStep(): Int = settings.getInt(KEY_LADDER_STEP, 0)

    /** Guarda el escalón alcanzado si supera al anterior (no retrocede el récord). */
    fun setLadderStep(step: Int) {
        if (step > ladderStep()) settings.putInt(KEY_LADDER_STEP, step)
    }

    /** Borra TODO el progreso del arcade (vuelve a los defaults). */
    fun reset() {
        // `settings.clear()` vacía el MISMO fichero, así que se lleva por delante tanto las claves
        // nuevas (_V2) como las viejas — que es justo lo que debe hacer un reset.
        settings.clear()
    }

    // ── Sesión en curso (pausa / minimizar) ────────────────────────────────────

    /**
     * Guarda la pelea arcade a medias. Usa apply() (async): no bloquea el hilo UI.
     * Llamar SOLO desde forcePause / salir — nunca por tick.
     */
    fun saveSession(session: ArcadeSession) {
        settings.putString(KEY_SESSION, encodeSession(session))
    }

    /** Lee la sesión en curso; null si no hay o el JSON es inválido. */
    fun loadSession(): ArcadeSession? {
        val raw = settings.getStringOrNull(KEY_SESSION) ?: return null
        return decodeSession(raw)
    }

    fun hasSession(): Boolean = !settings.getStringOrNull(KEY_SESSION).isNullOrEmpty()

    /** Borra la sesión (al terminar pelea / abandonar / retomar y acabar). */
    fun clearSession() {
        settings.remove(KEY_SESSION)
    }
}
