package ovh.gabrielhuav.pow

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.WorldMapViewModel
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.applyDebugScenario

// Receptor SOLO de depuración: permite probar el juego por adb sin multitoque. En release no se registra.
// Uso: adb shell am broadcast -a ovh.gabrielhuav.pow.DEBUG_SCENARIO --es name drive_open --es gas 1
internal object DebugScenarioReceiver {
    const val ACTION = "ovh.gabrielhuav.pow.DEBUG_SCENARIO"

    /** Registra el receptor y devuelve cómo anularlo, o null en release. */
    fun register(context: Context, viewModel: WorldMapViewModel): (() -> Unit)? {
        if (!BuildConfig.DEBUG) return null
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val extras = intent.extras?.keySet()?.associateWith { intent.getStringExtra(it).orEmpty() }.orEmpty()
                viewModel.applyDebugScenario(extras["name"].orEmpty(), extras)
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(ACTION), ContextCompat.RECEIVER_EXPORTED)
        return { context.unregisterReceiver(receiver) }
    }
}
