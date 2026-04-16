package ovh.gabrielhuav.pow.data.repository

import android.content.Context
import android.content.SharedPreferences
import ovh.gabrielhuav.pow.features.settings.models.ControlType

class SettingsRepository(context: Context) {

    companion object {
        private const val PREFS_NAME = "pow_game_settings"
        private const val KEY_CONTROL_TYPE = "CONTROL_TYPE"
        private const val KEY_CONTROLS_SCALE = "CONTROLS_SCALE"
        private const val KEY_SWAP_CONTROLS = "SWAP_CONTROLS"
        private const val SCALE_MIN = 0.6f
        private const val SCALE_MAX = 1.4f
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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
        val savedType = prefs.getString(KEY_CONTROL_TYPE, defaultType.name) ?: defaultType.name

        return runCatching { ControlType.valueOf(savedType) }
            .getOrElse {
                prefs.edit().putString(KEY_CONTROL_TYPE, defaultType.name).apply()
                defaultType
            }
    }

    fun getControlsScale(): Float = prefs.getFloat(KEY_CONTROLS_SCALE, 1.0f).coerceIn(SCALE_MIN, SCALE_MAX)

    fun getSwapControls(): Boolean = prefs.getBoolean(KEY_SWAP_CONTROLS, false)
}