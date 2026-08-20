package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.widget.Toast

/**
 * 🤖 La mitad Android de [WorldMapEnvironment].
 *
 * Es el ÚNICO sitio del mundo abierto donde queda un `Context`. Todo lo demás que antes lo pedía
 * (leer assets, sobre todo) pasó a `PowAssets`, que ya era común.
 */
class AndroidWorldMapEnvironment(private val context: Context) : WorldMapEnvironment {

    /**
     * El MISMO cálculo que tenía `computeDeviceTierFactor` dentro del ViewModel, movido tal cual.
     *
     * ⚠️ Ojo con el nombre: **no va de 0 a 1**, va de 0,6 a 1,5 — es un multiplicador de población
     * de NPCs, no un porcentaje. En gama alta pide MÁS NPCs, no menos.
     *
     * ⚠️ Se conserva el `try` de siempre. En algunos aparatos `getSystemService` devuelve algo
     * inesperado y **una excepción aquí tiraría la partida al entrar al mundo**; quedarse en 1
     * solo significa una población normal.
     */
    override fun factorDeGama(): Float = try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val gb = mi.totalMem / (1024.0 * 1024.0 * 1024.0)
        when {
            am.isLowRamDevice || gb <= GB_GAMA_BAJA -> 0.6f
            gb <= GB_GAMA_MEDIA -> 1.0f
            gb <= GB_GAMA_ALTA -> 1.3f
            else -> 1.5f
        }
    } catch (e: Exception) {
        android.util.Log.e("WorldMapEnvironment", "No se pudo leer la gama del aparato", e)
        1.0f
    }

    override fun avisar(texto: String) {
        Toast.makeText(context, texto, Toast.LENGTH_SHORT).show()
    }

    override fun guardarTexto(destino: String, contenido: String): Boolean = try {
        context.contentResolver.openOutputStream(Uri.parse(destino))?.use {
            it.write(contenido.toByteArray())
        } != null
    } catch (e: Exception) {
        android.util.Log.e("WorldMapEnvironment", "No se pudo escribir en $destino", e)
        false
    }

    override fun leerTexto(origen: String): String? = try {
        context.contentResolver.openInputStream(Uri.parse(origen))?.use {
            it.reader().readText()
        }
    } catch (e: Exception) {
        android.util.Log.e("WorldMapEnvironment", "No se pudo leer $origen", e)
        null
    }

    private companion object {
        // Los mismos escalones de RAM que usaba `computeDeviceTierFactor` en el ViewModel.
        const val GB_GAMA_BAJA = 2.2   // ≤2 GB / Android Go
        const val GB_GAMA_MEDIA = 4.2  // ≤4 GB
        const val GB_GAMA_ALTA = 6.2   // ≤6 GB
    }
}
