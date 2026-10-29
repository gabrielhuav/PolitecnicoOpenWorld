package ovh.gabrielhuav.pow.features.multimedia

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryCatalogTest {
    @Test
    fun everyCategoryHasTwoDistinctLocalImages() {
        GalleryCategory.entries.forEach { category ->
            val items = GalleryCatalog.itemsFor(category)
            assertEquals(category.name, 2, items.size)
            assertTrue(items.all { it.category == category })
        }
        assertEquals(6, GalleryCatalog.items.map { it.id }.distinct().size)
        assertEquals(6, GalleryCatalog.items.map { it.assetPath }.distinct().size)
    }

    @Test
    fun everyCatalogAssetExistsInTheAndroidProject() {
        val assets = sequenceOf(
            File("src/main/assets"),
            File("app/src/main/assets"),
            File("PolitecnicoOpenWorld/app/src/main/assets")
        ).firstOrNull { it.isDirectory }
        requireNotNull(assets) { "Android assets directory was not found" }
        GalleryCatalog.items.forEach { item ->
            assertTrue("Missing or empty gallery image: ${item.assetPath}", File(assets, item.assetPath).length() > 0)
        }
    }

    @Test
    fun largeImagesAreDownsampledInBothOrientations() {
        assertEquals(1, gallerySampleSize(256, 512))
        assertEquals(4, gallerySampleSize(2048, 1024))
        assertEquals(8, gallerySampleSize(512, 4096))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidImageLimitIsRejected() {
        gallerySampleSize(512, 512, 0)
    }
}
