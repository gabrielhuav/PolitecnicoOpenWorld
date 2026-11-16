package ovh.gabrielhuav.pow.features.streetfighter.ui

import ovh.gabrielhuav.pow.domain.models.streetfighter.SfConstants
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SfBackgroundGeometryTest {
    @Test
    fun `el fondo cubre el iPhone en ambos extremos de camara sin la franja de 272 px`() {
        val width = 2556f
        val height = 1179f
        val scale = sfBackgroundScale(width, height, 480, 270, height / SfConstants.SCENE_HEIGHT, 1.3f)
        val backgroundWidth = ceil(480 * scale).toInt()
        assertEquals(2725, backgroundWidth)
        assertEquals(0, sfBackgroundOffset(width, backgroundWidth, 0f))
        val rightAligned = sfBackgroundOffset(width, backgroundWidth, width - backgroundWidth)
        assertEquals(-169, rightAligned)
        assertEquals(width, rightAligned + backgroundWidth.toFloat())
    }

    @Test
    fun `el mapa sin zoom tambien cubre el ancho real en lugar de medir 2096 px`() {
        val scale = sfBackgroundScale(2556f, 1179f, 480, 270, 1179f / SfConstants.SCENE_HEIGHT, 1f)
        assertEquals(2556, ceil(480 * scale).toInt())
        assertTrue(270 * scale >= 1179f)
    }

    @Test
    fun `la cobertura resiste aspecto tamano submuestreo paneo salto y sacudida`() {
        // Se comprueban los cuatro bordes del rectángulo resultante, no una copia de la fórmula.
        val canvases = listOf(2556f to 1178f, 2556f to 1179f, 2400f to 1080f, 1024f to 768f, 1179f to 2556f)
        for ((width, height) in canvases) {
            val sceneScale = minOf(width / SfConstants.SCENE_WIDTH, height / SfConstants.SCENE_HEIGHT)
            val sceneHeight = SfConstants.SCENE_HEIGHT * sceneScale
            val originY = (height - sceneHeight) / 2f
            for ((frameWidth, frameHeight) in listOf(480 to 270, 240 to 135)) {
                for (zoom in listOf(1f, 1.3f, 1.35f)) {
                    val scale = sfBackgroundScale(width, height, frameWidth, frameHeight, sceneScale, zoom)
                    val drawnWidth = ceil(frameWidth * scale).toInt()
                    val drawnHeight = ceil(frameHeight * scale).toInt()
                    for (progress in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
                        for (shake in listOf(-6f * sceneScale, 0f, 6f * sceneScale)) {
                            val x = sfBackgroundOffset(width, drawnWidth, -(drawnWidth - width) * progress + shake)
                            assertTrue(x <= 0 && x + drawnWidth >= width, "Hueco lateral en $width × $height")
                        }
                    }
                    for (jump in listOf(0f, 0.5f, 1f)) {
                        for (offset in listOf(-20f, 0f, 20f)) {
                            val desiredY = originY + sceneHeight - drawnHeight + offset * sceneScale +
                                jump * (drawnHeight - sceneHeight).coerceAtLeast(0f)
                            val y = sfBackgroundOffset(height, drawnHeight, desiredY)
                            assertTrue(y <= 0 && y + drawnHeight >= height, "Hueco vertical en $width × $height")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `el anclaje al piso y el recorrido de salto se conservan si ya caben en el Canvas`() {
        val height = 1179f
        val backgroundHeight = 1533
        val headroom = backgroundHeight - height
        assertEquals(-354, sfBackgroundOffset(height, backgroundHeight, -headroom))
        assertEquals(-177, sfBackgroundOffset(height, backgroundHeight, -headroom * 0.5f))
        assertEquals(0, sfBackgroundOffset(height, backgroundHeight, 0f))
    }

    @Test
    fun `un offset fraccionario no abre una linea en el borde opuesto`() {
        val offset = sfBackgroundOffset(2556.5f, 2725, -168.5f)
        assertTrue(offset <= 0)
        assertTrue(offset + 2725 >= 2556.5f)
    }
}
