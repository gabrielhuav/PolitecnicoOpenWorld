package ovh.gabrielhuav.pow.platform.imagen

import androidx.compose.ui.graphics.ImageBitmap

/**
 * En iOS se decodifica entero y luego se escala.
 *
 * ⚠️ DIFERENCIA REAL CON ANDROID, no un detalle de implementación: el `inSampleSize` de Android
 * evita que el bitmap grande llegue a existir; esto NO. Aquí hay un **pico de memoria** con el
 * atlas a tamaño completo antes de reducirlo, aunque lo que quede retenido sea ya lo pequeño.
 *
 * Se acepta porque el motivo de `sampleSize` era la gama baja de Android, y el iPhone más modesto
 * que corre esto tiene de sobra para el pico. Si algún día aparece un OOM en un iPad viejo, la
 * salida es Skia (`Image.makeFromEncoded` + `scalePixels`), que sí decodifica progresivo — pero eso
 * ata este archivo a la API de Skia, y no compensa hasta que el problema exista de verdad.
 */
actual fun decodificarReducido(bytes: ByteArray, reduccion: Int): ImageBitmap {
    val completo = PowImagen.decodificar(bytes)
    val factor = reduccion.coerceAtLeast(1)
    if (factor == 1) return completo
    return PowImagen.escalar(completo, completo.width / factor, completo.height / factor)
}
