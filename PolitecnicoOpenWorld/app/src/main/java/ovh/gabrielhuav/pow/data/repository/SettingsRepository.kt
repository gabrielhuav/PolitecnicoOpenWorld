package ovh.gabrielhuav.pow.data.repository

import android.content.Context
import android.content.SharedPreferences
import ovh.gabrielhuav.pow.features.settings.models.ControlType

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("pow_game_settings", Context.MODE_PRIVATE)

    fun saveControlsSettings(type: ControlType, scale: Float, swap: Boolean) {
        prefs.edit().apply {
            putString("CONTROL_TYPE", type.name)
            putFloat("CONTROLS_SCALE", scale)
            putBoolean("SWAP_CONTROLS", swap)
            apply()
        }
    }

    fun getControlType(): ControlType {
        val savedType = prefs.getString("CONTROL_TYPE", ControlType.DPAD.name) ?: ControlType.DPAD.name
        return ControlType.valueOf(savedType)
    }

    fun getControlsScale(): Float = prefs.getFloat("CONTROLS_SCALE", 1.0f)

    fun getSwapControls(): Boolean = prefs.getBoolean("SWAP_CONTROLS", false)
}