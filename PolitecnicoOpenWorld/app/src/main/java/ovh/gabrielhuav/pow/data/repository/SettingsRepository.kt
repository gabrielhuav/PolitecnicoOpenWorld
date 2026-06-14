package ovh.gabrielhuav.pow.data.repository

import android.content.Context
import android.content.SharedPreferences
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerSkin
import ovh.gabrielhuav.pow.features.settings.models.ControlType

class SettingsRepository(context: Context) {

    companion object {
        private const val PREFS_NAME = "pow_game_settings"
        private const val KEY_CONTROL_TYPE = "CONTROL_TYPE"
        private const val KEY_CONTROLS_SCALE = "CONTROLS_SCALE"
        private const val KEY_SWAP_CONTROLS = "SWAP_CONTROLS"
        private const val KEY_PLAYER_SKIN = "PLAYER_SKIN"

        private const val KEY_SHOW_ROAD_NETWORK = "SHOW_ROAD_NETWORK"
        private const val SCALE_MIN = 0.6f
        private const val SCALE_MAX = 1.4f

        // ─── Jugabilidad: población de NPCs ──────────────────────────────────
        private const val KEY_NPC_DENSITY = "NPC_DENSITY"        // multiplicador 0.4–1.6
        private const val KEY_NPC_EMOJI_LOD = "NPC_EMOJI_LOD"    // NPCs lejanos como emoji (optimizar dibujado)
        private const val KEY_NPC_FULL_EMOJI = "NPC_FULL_EMOJI"  // TODOS los NPCs como emoji (gama baja)
        private const val KEY_SHOW_ZOOM_WIDGET = "SHOW_ZOOM_WIDGET" // widget de nivel de zoom (Interfaz)
        private const val KEY_SHOW_SPEEDOMETER = "SHOW_SPEEDOMETER"  // widget velocímetro al conducir (Interfaz)
        private const val KEY_LANGUAGE = "APP_LANGUAGE"             // idioma de la UI (BCP-47; "" = sistema)
        const val NPC_DENSITY_MIN = 0.4f
        const val NPC_DENSITY_MAX = 1.6f
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Por defecto el LOD de emojis se activa SOLO en gama baja (se puede cambiar en Ajustes).
    private val lowRamDefault: Boolean = try {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager).isLowRamDevice
    } catch (e: Exception) { false }

    // ─── Controles ───────────────────────────────────────────────────────

    fun saveControlsSettings(type: ControlType, scale: Float, swap: Boolean) {
        val clampedScale = scale.coerceIn(SCALE_MIN, SCALE_MAX)
        prefs.edit().apply {
            putString(KEY_CONTROL_TYPE, type.name)
            putFloat(KEY_CONTROLS_SCALE, clampedScale)
            putBoolean(KEY_SWAP_CONTROLS, swap)
            apply()
        }
    }

    fun getControlType(): ControlType {
        val defaultType = ControlType.DPAD
        val saved = prefs.getString(KEY_CONTROL_TYPE, defaultType.name) ?: defaultType.name
        return runCatching { ControlType.valueOf(saved) }.getOrElse {
            prefs.edit().putString(KEY_CONTROL_TYPE, defaultType.name).apply()
            defaultType
        }
    }

    fun getControlsScale(): Float = prefs.getFloat(KEY_CONTROLS_SCALE, 1.0f).coerceIn(SCALE_MIN, SCALE_MAX)

    fun getSwapControls(): Boolean = prefs.getBoolean(KEY_SWAP_CONTROLS, false)

    // ─── Skin del jugador ────────────────────────────────────────────────

    /** Persiste la skin elegida entre sesiones. */
    fun savePlayerSkin(skin: PlayerSkin) {
        prefs.edit().putString(KEY_PLAYER_SKIN, skin.name).apply()
    }

    /** Devuelve la skin guardada, o LAZARO si no hay ninguna o es inválida. */
    fun getPlayerSkin(): PlayerSkin {
        val saved = prefs.getString(KEY_PLAYER_SKIN, PlayerSkin.LAZARO.name)
            ?: PlayerSkin.LAZARO.name
        return runCatching { PlayerSkin.valueOf(saved) }.getOrElse { PlayerSkin.LAZARO }
    }

    // ─── Red vial ────────────────────────────────────────────────────────

    fun saveShowRoadNetwork(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_ROAD_NETWORK, show).apply()
    }

    fun getShowRoadNetwork(): Boolean = prefs.getBoolean(KEY_SHOW_ROAD_NETWORK, true)

    // ─── Jugabilidad: población de NPCs ──────────────────────────────────────

    /** Multiplicador de densidad de NPCs elegido por el usuario (se combina con gama/ciudad). */
    fun getNpcDensity(): Float = prefs.getFloat(KEY_NPC_DENSITY, 1.0f).coerceIn(NPC_DENSITY_MIN, NPC_DENSITY_MAX)
    fun saveNpcDensity(v: Float) {
        prefs.edit().putFloat(KEY_NPC_DENSITY, v.coerceIn(NPC_DENSITY_MIN, NPC_DENSITY_MAX)).apply()
    }

    /** ¿Dibujar los NPCs lejanos como emoji ("Optimizar dibujado de NPCs")? Default = isLowRamDevice. */
    fun getNpcEmojiLod(): Boolean = prefs.getBoolean(KEY_NPC_EMOJI_LOD, lowRamDefault)
    fun saveNpcEmojiLod(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NPC_EMOJI_LOD, enabled).apply()
    }

    /** ¿Dibujar TODOS los NPCs como emoji ("Optimizar para gama baja")? Default = false. */
    fun getNpcFullEmoji(): Boolean = prefs.getBoolean(KEY_NPC_FULL_EMOJI, false)
    fun saveNpcFullEmoji(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NPC_FULL_EMOJI, enabled).apply()
    }

    // ─── Interfaz: widget de nivel de zoom ───────────────────────────────────

    fun getShowZoomWidget(): Boolean = prefs.getBoolean(KEY_SHOW_ZOOM_WIDGET, false)
    fun saveShowZoomWidget(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_ZOOM_WIDGET, show).apply()
    }

    // ─── Interfaz: velocímetro (visible solo al conducir). Default = activado. ──

    fun getShowSpeedometer(): Boolean = prefs.getBoolean(KEY_SHOW_SPEEDOMETER, true)
    fun saveShowSpeedometer(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_SPEEDOMETER, show).apply()
    }

    // ─── Idioma / i18n ───────────────────────────────────────────────────────
    // Etiqueta BCP-47 del idioma de la UI ("es", "en", …). "" = seguir el idioma
    // del sistema. Se aplica envolviendo el Context en MainActivity.attachBaseContext
    // (ver i18n/LocaleHelper.kt); al cambiarlo se recrea la Activity.

    fun getLanguage(): String = prefs.getString(KEY_LANGUAGE, "") ?: ""
    fun saveLanguage(tag: String) {
        prefs.edit().putString(KEY_LANGUAGE, tag).apply()
    }
}
