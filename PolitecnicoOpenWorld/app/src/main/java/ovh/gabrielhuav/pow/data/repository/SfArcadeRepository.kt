package ovh.gabrielhuav.pow.data.repository

import android.content.Context
import android.content.SharedPreferences

// Persistencia del MODO ARCADE de HUELUM VS. GOYA (SF POW). LOCAL, con SharedPreferences,
// igual que CampaignRepository/SettingsRepository (no hay base de datos ni Firestore: el
// proyecto solo tiene Firebase Auth). Guarda MÍNIMO: qué peleadores y qué mapas ha
// desbloqueado el jugador + hasta qué escalón de la escalera llegó. Diseño DATA-DRIVEN:
// guarda ids/archivos como texto, así el roster puede crecer sin migraciones.
//
// Defaults (arranque limpio): desbloqueados SOLO los 3 estudiantes ESCOMBOY/ESCOMGIRL/ROBOT
// (el jugador elige uno de ellos) y solo el mapa "Queso IPN" (fondo_IPN_QUESO_1.png). El
// resto del roster y los mapas se ganan en la escalera; el jefe FINAL es Prankedy y la
// semifinal Rey Grupero. Ver README for IAS/DISENO_ARCADE_SF_POW.md (decisiones + escalera).
class SfArcadeRepository(context: Context) {

    companion object {
        private const val PREFS_NAME = "pow_sf_arcade"
        private const val KEY_FIGHTERS = "UNLOCKED_FIGHTERS" // StringSet de SfFighterId.name
        private const val KEY_MAPS = "UNLOCKED_MAPS"          // StringSet de nombres de archivo
        private const val KEY_LADDER_STEP = "LADDER_STEP"     // escalón alcanzado (0 = ninguno)

        /** Peleadores desbloqueados de arranque: los 3 estudiantes (el jugador elige uno). */
        val DEFAULT_FIGHTERS = setOf("ESCOMBOY", "ESCOMGIRL", "ROBOT")
        /** Mapa desbloqueado de arranque (primera pelea del arcade). */
        const val DEFAULT_MAP = "fondo_IPN_QUESO_1.png"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Peleadores ────────────────────────────────────────────────────────────

    /** Ids (SfFighterId.name) desbloqueados; siempre incluye los defaults de arranque. */
    fun unlockedFighters(): Set<String> =
        prefs.getStringSet(KEY_FIGHTERS, null)?.toSet() ?: DEFAULT_FIGHTERS

    fun isFighterUnlocked(id: String): Boolean = id in unlockedFighters()

    /** Marca un peleador como desbloqueado (idempotente). Devuelve true si era nuevo. */
    fun unlockFighter(id: String): Boolean {
        val current = unlockedFighters()
        if (id in current) return false
        prefs.edit().putStringSet(KEY_FIGHTERS, current + id).apply()
        return true
    }

    // ── Mapas ─────────────────────────────────────────────────────────────────

    /** Archivos de mapa desbloqueados; siempre incluye el mapa de arranque. */
    fun unlockedMaps(): Set<String> =
        prefs.getStringSet(KEY_MAPS, null)?.toSet() ?: setOf(DEFAULT_MAP)

    fun isMapUnlocked(file: String): Boolean = file in unlockedMaps()

    /** Marca un mapa como desbloqueado (idempotente). Devuelve true si era nuevo. */
    fun unlockMap(file: String): Boolean {
        val current = unlockedMaps()
        if (file in current) return false
        prefs.edit().putStringSet(KEY_MAPS, current + file).apply()
        return true
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
}
