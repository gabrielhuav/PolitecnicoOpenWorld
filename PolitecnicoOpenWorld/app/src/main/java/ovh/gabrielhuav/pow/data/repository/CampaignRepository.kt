package ovh.gabrielhuav.pow.data.repository

import android.content.Context
import android.content.SharedPreferences

// Persistencia de la partida del MODO HISTORIA (campaña). Usa SharedPreferences,
// igual que SettingsRepository (las prefs van por SharedPreferences, no por Room).
// Por ahora la partida guarda solo la ESCUELA de inicio + la fecha de guardado;
// cuando exista progreso de misión (Misión 1) se añadirán más campos aquí.
class CampaignRepository(context: Context) {

    companion object {
        private const val PREFS_NAME = "pow_campaign"
        private const val KEY_HAS_SAVE = "HAS_SAVE"
        private const val KEY_SCHOOL_ID = "SCHOOL_ID"
        private const val KEY_SAVED_AT = "SAVED_AT"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Guarda (o sobrescribe) la partida de campaña con la escuela elegida. */
    fun saveCampaign(schoolId: String) {
        prefs.edit().apply {
            putBoolean(KEY_HAS_SAVE, true)
            putString(KEY_SCHOOL_ID, schoolId)
            putLong(KEY_SAVED_AT, System.currentTimeMillis())
            apply()
        }
    }

    /** ¿Hay una partida guardada? (habilita "CARGAR PARTIDA" en el menú de campaña). */
    fun hasSave(): Boolean = prefs.getBoolean(KEY_HAS_SAVE, false)

    /** Id de la escuela de la partida guardada, o null si no hay partida. */
    fun getSavedSchoolId(): String? =
        if (hasSave()) prefs.getString(KEY_SCHOOL_ID, null) else null

    /** Marca de tiempo (epoch ms) del último guardado, o 0 si no hay partida. */
    fun getSavedAt(): Long = prefs.getLong(KEY_SAVED_AT, 0L)

    /** Borra la partida guardada. */
    fun clearCampaign() {
        prefs.edit().clear().apply()
    }
}
