package ovh.gabrielhuav.pow.domain.models.map

import ovh.gabrielhuav.pow.data.json.PowJson

import kotlinx.serialization.Serializable

import android.content.Context
import android.util.Log

// 1. El modelo de datos actualizado con ancho y alto
@Serializable
data class LandmarkAssetTemplate(
    val id: String = "",
    val displayName: String = "",
    val assetPath: String = "",
    val defaultScale: Float = 0.15f,
    val baseWidthMeters: Float = 0f,
    val baseHeightMeters: Float = 0f,
)

// 2. El Gestor que lee el JSON
object LandmarkCatalogManager {
    // Aquí se guardará la lista de edificios disponibles en memoria
    var availableAssets: List<LandmarkAssetTemplate> = emptyList()
        private set
    @Volatile
    private var isLoaded: Boolean = false

    // Esta función lee el archivo JSON y lo convierte a la lista de Kotlin
    @Synchronized
    fun loadCatalog(context: Context) {
        // Si ya intentó cargarse, no volvemos a leer el archivo
        if (isLoaded) return

        try {
            // Abrimos y leemos el archivo que creaste en la carpeta assets/
            val jsonString = context.assets.open("CONFIG/buildings_catalog.json").bufferedReader().use { it.readText() }

            // kotlinx.serialization resuelve el tipo genérico en COMPILACIÓN: ya no hace falta el
            // TypeToken de Gson (que existía solo para esquivar el borrado de tipos de la JVM).
            availableAssets = PowJson.decodeFromString<List<LandmarkAssetTemplate>>(jsonString)

            Log.d("CatalogManager", "Catálogo cargado exitosamente con ${availableAssets.size} edificios.")
        } catch (e: Exception) {
            Log.e("CatalogManager", "Error al leer buildings_catalog.json", e)
            availableAssets = emptyList()
        } finally {
            isLoaded = true
        }
    }
}
