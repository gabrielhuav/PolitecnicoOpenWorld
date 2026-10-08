package ovh.gabrielhuav.pow.features.streetfighter.records

import androidx.compose.runtime.Composable
import com.russhwolf.settings.Settings

/**
 * 🆕 (issue #150) Guarda y lee los récords en el almacenamiento local del dispositivo
 * (SharedPreferences en Android, NSUserDefaults en iOS) usando multiplatform-settings.
 */
object FighterRecordsStore {
    private const val KEY = "sf_fighter_records"

    fun load(settings: Settings): RecordsLoadResult =
        FighterRecordsCodec.decode(settings.getStringOrNull(KEY))

    fun reset(settings: Settings) {
        settings.remove(KEY)
    }

    /** Suma una victoria o derrota. Si el dato guardado está dañado, NO lo sobrescribe. */
    fun register(settings: Settings, fighterId: String, playerWon: Boolean): RecordsLoadResult {
        val current = when (val loaded = load(settings)) {
            is RecordsLoadResult.Loaded -> loaded.records
            RecordsLoadResult.Empty -> emptyMap()
            RecordsLoadResult.Corrupt -> return RecordsLoadResult.Corrupt
        }
        val updated = FighterRecordsCodec.registerResult(current, fighterId, playerWon)
        settings.putString(KEY, FighterRecordsCodec.encode(updated))
        return RecordsLoadResult.Loaded(updated)
    }
}

/** Cada plataforma (Android / iOS) da su propio almacenamiento. */
@Composable
expect fun rememberFighterRecordsSettings(): Settings