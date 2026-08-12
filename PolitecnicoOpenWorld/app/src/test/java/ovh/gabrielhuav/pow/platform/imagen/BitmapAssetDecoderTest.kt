package ovh.gabrielhuav.pow.platform.imagen

import org.junit.Assert.assertEquals
import org.junit.Test

class BitmapAssetDecoderTest {

    @Test
    fun `large square icon is sampled near marker size`() {
        assertEquals(8, calculateInSampleSize(800, 800, 72, 72))
    }

    @Test
    fun `sample never makes either dimension smaller than request`() {
        assertEquals(4, calculateInSampleSize(592, 422, 66, 66))
    }

    @Test
    fun `large hand texture is reduced without losing requested detail`() {
        assertEquals(4, calculateInSampleSize(1536, 1024, 256, 256))
    }

    @Test
    fun `source smaller than request stays at original size`() {
        assertEquals(1, calculateInSampleSize(64, 64, 128, 128))
    }

    @Test
    fun `invalid dimensions fall back safely`() {
        assertEquals(1, calculateInSampleSize(0, 800, 72, 72))
        assertEquals(1, calculateInSampleSize(800, 800, 0, 72))
    }
}
