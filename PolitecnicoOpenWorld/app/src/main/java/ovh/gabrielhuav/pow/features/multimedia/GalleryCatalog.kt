package ovh.gabrielhuav.pow.features.multimedia

import androidx.annotation.StringRes
import ovh.gabrielhuav.pow.R

enum class GalleryCategory(@StringRes val label: Int) {
    CHARACTERS(R.string.gallery_characters),
    MAPS(R.string.gallery_maps),
    DESIGNS(R.string.gallery_designs)
}

data class GalleryItem(
    val id: String,
    val category: GalleryCategory,
    val assetPath: String,
    @StringRes val title: Int,
    @StringRes val description: Int
)

object GalleryCatalog {
    // Solo reutilizamos recursos del juego; no se descargan imágenes ni se duplican los atlas.
    val items: List<GalleryItem> = listOf(
        GalleryItem(
            "prankedy", GalleryCategory.CHARACTERS,
            "SPRITES/NPC/Prankedy/p_idle/p_idle_1.webp",
            R.string.gallery_prankedy, R.string.gallery_prankedy_description
        ),
        GalleryItem(
            "charro_negro", GalleryCategory.CHARACTERS,
            "SPRITES/NPC/CharroNegro/Idle/cn_i_1.webp",
            R.string.gallery_charro, R.string.gallery_charro_description
        ),
        GalleryItem(
            "escom_stage", GalleryCategory.MAPS,
            "STREETFIGHTER/IMAGES/fondo_escom_anim_thumb.webp",
            R.string.gallery_escom_stage, R.string.gallery_escom_stage_description
        ),
        GalleryItem(
            "cecyt9_stage", GalleryCategory.MAPS,
            "STREETFIGHTER/IMAGES/fondo_cecyt_9_anim_thumb.webp",
            R.string.gallery_cecyt9_stage, R.string.gallery_cecyt9_stage_description
        ),
        GalleryItem(
            "escom_building", GalleryCategory.DESIGNS,
            "BUILDINGS/IPN/building_escom.webp",
            R.string.gallery_escom_building, R.string.gallery_escom_building_description
        ),
        GalleryItem(
            "cic_building", GalleryCategory.DESIGNS,
            "BUILDINGS/IPN/cic_edificios.webp",
            R.string.gallery_cic_building, R.string.gallery_cic_building_description
        )
    )

    fun itemsFor(category: GalleryCategory): List<GalleryItem> =
        items.filter { it.category == category }
}

/** Reduce las imágenes grandes antes de decodificarlas para limitar la memoria de la galería. */
internal fun gallerySampleSize(width: Int, height: Int, maxDimension: Int = 512): Int {
    require(maxDimension > 0) { "maxDimension must be positive" }
    var sampleSize = 1
    while (width / sampleSize > maxDimension || height / sampleSize > maxDimension) {
        sampleSize *= 2
    }
    return sampleSize
}
