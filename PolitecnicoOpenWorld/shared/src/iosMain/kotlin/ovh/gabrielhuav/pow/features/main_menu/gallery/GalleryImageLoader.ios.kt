package ovh.gabrielhuav.pow.features.main_menu.gallery

import androidx.compose.ui.graphics.ImageBitmap
import org.jetbrains.skia.Image
import ovh.gabrielhuav.pow.platform.imagen.PowImagen
import ovh.gabrielhuav.pow.platform.imagen.decodificarReducido

internal actual fun decodeGalleryImage(bytes: ByteArray, maxDimension: Int, portraitCellSize: Int?): ImageBitmap {
    val encoded = Image.makeFromEncoded(bytes)
    val reduction = try {
        if (portraitCellSize != null) 4 else gallerySampleSize(encoded.width, encoded.height, maxDimension)
    } finally {
        encoded.close()
    }
    // Conserva el decodificador Skia del proyecto y su liberación determinista del buffer nativo.
    val bitmap = decodificarReducido(bytes, reduction)
    return if (portraitCellSize == null) bitmap else PowImagen.recortar(
        bitmap, 0, 0, minOf(portraitCellSize / reduction, bitmap.width), minOf(portraitCellSize / reduction, bitmap.height),
    )
}
