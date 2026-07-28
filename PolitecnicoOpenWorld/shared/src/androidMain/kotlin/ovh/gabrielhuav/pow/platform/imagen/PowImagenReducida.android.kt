package ovh.gabrielhuav.pow.platform.imagen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Android usa `inSampleSize`, que es lo que ya hacía el juego: el decodificador **nunca llega a
 * materializar el bitmap grande**, así que no hay pico de memoria. Es justo la propiedad que se
 * necesita en gama baja y la razón de que esta función exista.
 */
actual fun decodificarReducido(bytes: ByteArray, reduccion: Int): ImageBitmap {
    val opciones = BitmapFactory.Options().apply {
        inSampleSize = reduccion.coerceAtLeast(1)
        // ARGB_8888 obligatorio: los sprites necesitan canal alfa.
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opciones)
        ?: error("No se pudo decodificar la imagen (${bytes.size} bytes)")
    return bitmap.asImageBitmap()
}
