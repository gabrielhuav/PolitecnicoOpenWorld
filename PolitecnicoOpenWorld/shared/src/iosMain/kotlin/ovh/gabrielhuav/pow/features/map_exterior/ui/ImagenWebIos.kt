package ovh.gabrielhuav.pow.features.map_exterior.ui

import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorInfo
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import ovh.gabrielhuav.pow.platform.assets.PowAssets
import ovh.gabrielhuav.pow.platform.imagen.PowImagen
import ovh.gabrielhuav.pow.platform.imagen.decodificarReducido

/**
 * 🖼️🍏 ENTRAR Y SALIR DE PÍXELES, para lo que el mapa sirve por `pow-asset://`.
 *
 * Lo usan [TintadoWebIos] (coches) y [PersonajeWebIos] (peatones): los dos hacen lo mismo —
 * decodificar un asset, tocar sus píxeles con un algoritmo de `commonMain` y devolver un PNG— y
 * solo cambia el algoritmo de en medio.
 *
 * ⚠️ **En Android este archivo no existe y no debe existir**: allí el sprite se compone con
 * `Bitmap`/`Canvas` y viaja al WebView como base64, porque Android no puede servirle ficheros. La
 * diferencia está en el transporte, no en el cálculo — el cálculo es el mismo en las dos.
 */
internal object ImagenWebIos {

    /** Un mapa de bits ya en ARGB, listo para que lo toque un algoritmo compartido. */
    data class Lienzo(val pixeles: IntArray, val ancho: Int, val alto: Int) {
        // `IntArray` compara por referencia; `data class` genera `equals`/`hashCode` que lo usan y
        // detekt se queja con razón. No se comparan lienzos en ninguna parte, así que se declara
        // explícitamente en vez de fingir una igualdad estructural que nadie necesita.
        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = pixeles.hashCode() * 31 + ancho * 31 + alto
    }

    /**
     * Decodifica un asset del bundle y devuelve sus píxeles ARGB sin premultiplicar.
     *
     * @param reduccion divisor de tamaño al decodificar. `1` = tal cual.
     *
     * ⚠️ **Reducir NO es una optimización opcional para los peatones.** Sus sprites son de 512×512
     * y en el mapa se pintan a ~12 px: sin reducir, cada combinación de ropa genera un PNG de
     * medio megapíxel que nadie llega a ver, y son ~8 fotogramas × cada atuendo. Eso sí, **el
     * cuerpo y el pelo tienen que reducirse IGUAL** o dejan de encajar (ver [componerEncima]).
     */
    fun pixeles(ruta: String, reduccion: Int = 1): Lienzo {
        val imagen = if (reduccion <= 1) {
            PowImagen.deAsset(ruta)
        } else {
            decodificarReducido(PowAssets.bytes(ruta), reduccion)
        }
        val pixeles = IntArray(imagen.width * imagen.height)
        imagen.readPixels(pixeles, 0, 0, imagen.width, imagen.height)
        return Lienzo(pixeles, imagen.width, imagen.height)
    }

    /**
     * Empaqueta píxeles ARGB como PNG.
     *
     * ⚠️ **Se pide `RGBA_8888` explícito y NO el `N32` de Skia.** `N32` es el formato nativo de la
     * plataforma y en Apple es BGRA, así que dejarlo a `N32` intercambia el rojo y el azul — un
     * coche rojo saldría azul, sin ningún error por ninguna parte.
     *
     * ⚠️ Y `UNPREMUL`, porque `readPixels` de Compose entrega el alfa SIN premultiplicar (igual que
     * el `getPixels` de Android). Marcarlo como premultiplicado oscurece los bordes del sprite.
     */
    fun aPng(pixeles: IntArray, ancho: Int, alto: Int): ByteArray {
        val bytes = ByteArray(pixeles.size * 4)
        for (i in pixeles.indices) {
            val p = pixeles[i]
            val j = i * 4
            bytes[j] = ((p ushr 16) and 0xFF).toByte()     // R
            bytes[j + 1] = ((p ushr 8) and 0xFF).toByte()  // G
            bytes[j + 2] = (p and 0xFF).toByte()           // B
            bytes[j + 3] = ((p ushr 24) and 0xFF).toByte() // A
        }
        val info = ImageInfo(
            colorInfo = ColorInfo(ColorType.RGBA_8888, ColorAlphaType.UNPREMUL, null),
            width = ancho,
            height = alto,
        )
        val imagen = Image.makeRaster(info, bytes, ancho * 4)
        val datos = imagen.encodeToData(EncodedImageFormat.PNG)
        imagen.close()
        return datos?.bytes ?: error("Skia no pudo codificar el PNG de ${ancho}x$alto")
    }
}
