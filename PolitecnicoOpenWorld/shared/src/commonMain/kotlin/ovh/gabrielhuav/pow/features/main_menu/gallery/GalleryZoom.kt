package ovh.gabrielhuav.pow.features.main_menu.gallery

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize

internal const val GALLERY_MAX_ZOOM = 12f

internal data class GalleryZoom(val scale: Float = 1f, val offset: Offset = Offset.Zero)

/** Mantiene el punto bajo los dedos y evita arrastrar toda la imagen fuera de la ventana. */
internal fun transformGalleryZoom(
    current: GalleryZoom,
    centroid: Offset,
    pan: Offset,
    zoomChange: Float,
    viewport: IntSize,
    image: IntSize,
): GalleryZoom {
    val scale = (current.scale * zoomChange).coerceIn(1f, GALLERY_MAX_ZOOM)
    val anchor = centroid - Offset(viewport.width / 2f, viewport.height / 2f)
    val offset = anchor - (anchor - current.offset) * (scale / current.scale) + pan
    return GalleryZoom(scale, constrainGalleryOffset(offset, scale, viewport, image))
}

internal fun constrainGalleryOffset(offset: Offset, scale: Float, viewport: IntSize, image: IntSize): Offset {
    if (viewport.width <= 0 || viewport.height <= 0 || image.width <= 0 || image.height <= 0) return Offset.Zero
    val fit = minOf(viewport.width.toFloat() / image.width, viewport.height.toFloat() / image.height)
    val horizontal = ((image.width * fit * scale - viewport.width) / 2f).coerceAtLeast(0f)
    val vertical = ((image.height * fit * scale - viewport.height) / 2f).coerceAtLeast(0f)
    return Offset(offset.x.coerceIn(-horizontal, horizontal), offset.y.coerceIn(-vertical, vertical))
}
