package ovh.gabrielhuav.pow.domain.models

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// 1. El modelo de datos actualizado con ancho y alto
data class LandmarkAssetTemplate(
    val id: String,
    val displayName: String,
    val assetPath: String,
    val defaultScale: Float = 0.15f,
    val baseWidthMeters: Float,
    val baseHeightMeters: Float
)

// 2. El Gestor que lee el JSON
object LandmarkCatalogManager {
    // Aquí se guardará la lista de edificios disponibles en memoria
    var availableAssets: List<LandmarkAssetTemplate> = emptyList()
        private set

    // Esta función lee el archivo JSON y lo convierte a la lista de Kotlin
    fun loadCatalog(context: Context) {
        // Si ya tiene datos, no volvemos a leer el archivo para ahorrar memoria
        if (availableAssets.isNotEmpty()) return

        try {
            // Abrimos y leemos el archivo que creaste en la carpeta assets/
            val jsonString = context.assets.open("buildings_catalog.json").bufferedReader().use { it.readText() }

            // Usamos Gson para convertir el texto JSON a una lista de objetos LandmarkAssetTemplate
            val listType = object : TypeToken<List<LandmarkAssetTemplate>>() {}.type
            availableAssets = Gson().fromJson(jsonString, listType)

            Log.d("CatalogManager", "Catálogo cargado exitosamente con ${availableAssets.size} edificios.")
        } catch (e: Exception) {
            Log.e("CatalogManager", "Error al leer buildings_catalog.json", e)
            availableAssets = emptyList()
        }
    }
}