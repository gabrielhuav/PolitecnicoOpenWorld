package ovh.gabrielhuav.pow.features.streetfighter.data

import android.app.ActivityManager
import android.content.Context

/**
 * Tier de dispositivo para HUELUM VS. GOYA. Las optimizaciones de RAM/CPU se aplican
 * en GAMA BAJA; en media/alta se prioriza calidad (pero el selector sigue animando
 * solo el focused: es gratis y más limpio en todos los equipos).
 *
 * Criterio alineado con el mapa exterior (WorldMapViewModel): isLowRamDevice o ≤2.2 GB.
 */
enum class SfDeviceTier {
    LOW,    // ≤2 GB / Android Go / isLowRamDevice
    MID,    // ≤4 GB
    HIGH,   // resto
}

fun sfDeviceTier(context: Context): SfDeviceTier {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val mi = ActivityManager.MemoryInfo()
    am.getMemoryInfo(mi)
    val gb = mi.totalMem / (1024.0 * 1024.0 * 1024.0)
    return when {
        am.isLowRamDevice || gb <= 2.2 -> SfDeviceTier.LOW
        gb <= 4.2 -> SfDeviceTier.MID
        else -> SfDeviceTier.HIGH
    }
}

fun Context.isSfLowEnd(): Boolean = sfDeviceTier(this) == SfDeviceTier.LOW
