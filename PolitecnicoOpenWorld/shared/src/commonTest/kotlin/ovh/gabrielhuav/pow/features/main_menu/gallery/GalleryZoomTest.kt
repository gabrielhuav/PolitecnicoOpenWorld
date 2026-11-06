package ovh.gabrielhuav.pow.features.main_menu.gallery

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals

class GalleryZoomTest {
    @Test
    fun pinchKeepsTheTouchedFrameUnderTheFingers() {
        val viewport = IntSize(400, 400)
        val zoom = transformGalleryZoom(GalleryZoom(), Offset(300f, 300f), Offset.Zero, 2f, viewport, viewport)
        assertEquals(2f, zoom.scale)
        assertEquals(Offset(-100f, -100f), zoom.offset)
        // El punto situado 100 px a la derecha/abajo del centro sigue en el mismo sitio.
        assertEquals(Offset(100f, 100f), Offset(100f, 100f) * zoom.scale + zoom.offset)
    }

    @Test
    fun tallSheetsStayCenteredUntilTheyFillTheWidthAndCannotLeaveTheViewport() {
        val viewport = IntSize(400, 400)
        val image = IntSize(200, 800)
        assertEquals(Offset(0f, 200f), constrainGalleryOffset(Offset(900f, 900f), 2f, viewport, image))
        assertEquals(Offset(-100f, -1000f), constrainGalleryOffset(Offset(-900f, -9000f), 6f, viewport, image))
    }

    @Test
    fun zoomLimitsAndFitRestoreTheWholeImage() {
        val viewport = IntSize(400, 400)
        val enlarged = transformGalleryZoom(GalleryZoom(), Offset.Zero, Offset(900f, 900f), 100f, viewport, viewport)
        assertEquals(GALLERY_MAX_ZOOM, enlarged.scale)
        val fitted = transformGalleryZoom(enlarged, Offset.Zero, Offset.Zero, 0.001f, viewport, viewport)
        assertEquals(GalleryZoom(), fitted)
        assertEquals(Offset.Zero, constrainGalleryOffset(Offset(100f, 100f), 5f, IntSize.Zero, viewport))
    }
}
