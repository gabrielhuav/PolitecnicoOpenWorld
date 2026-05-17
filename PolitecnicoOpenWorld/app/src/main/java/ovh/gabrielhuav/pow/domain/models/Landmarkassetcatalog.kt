package ovh.gabrielhuav.pow.domain.models

/**
 * Entrada del catálogo de assets disponibles en el modo diseñador.
 * Agrega más entradas a [AVAILABLE_ASSETS] cuando metas nuevos WEBP a /assets.
 */
data class LandmarkAssetTemplate(
    val displayName: String,       // Lo que ve el usuario en el selector
    val assetPath: String,         // Ruta relativa dentro de /assets
    val defaultScale: Float = 0.15f
)

object LandmarkAssetCatalog {
    val AVAILABLE_ASSETS: List<LandmarkAssetTemplate> = listOf(
        LandmarkAssetTemplate(
            displayName = "ESCOM",
            assetPath = "BUILDINGS/IPN/building_escom.webp",
            defaultScale = 0.15f
        )
        // Agregar aquí nuevos edificios cuando estén disponibles, por ejemplo:
        // LandmarkAssetTemplate("ESIA", "BUILDINGS/IPN/building_esia.webp", 0.15f),
        // LandmarkAssetTemplate("ESFM", "BUILDINGS/IPN/building_esfm.webp", 0.15f),
    )
}