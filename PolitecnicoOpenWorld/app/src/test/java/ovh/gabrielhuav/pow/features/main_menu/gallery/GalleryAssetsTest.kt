package ovh.gabrielhuav.pow.features.main_menu.gallery

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterId
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfStageCatalog
import ovh.gabrielhuav.pow.platform.assets.PowAssetsFuente

class GalleryAssetsTest {
    private val root = sequenceOf(File("src/main/assets"), File("app/src/main/assets"))
        .first { it.isDirectory }
    private val assets = object : PowAssetsFuente {
        override fun listar(dir: String) = File(root, dir).list()?.toList().orEmpty()
        override fun existe(ruta: String) = File(root, ruta).isFile
        override fun bytes(ruta: String): ByteArray = error("Catalog discovery must not decode images")
    }

    @Test
    fun everyMapIsReachableAndEveryFighterPreviewExists() {
        val catalog = GalleryCatalog.load(assets)
        val expected = root.walkTopDown().filter {
            it.isFile && it.extension.lowercase() in setOf("png", "webp", "jpg", "jpeg")
        }.map { it.relativeTo(root).invariantSeparatorsPath }.filter { path ->
            path.startsWith("STREETFIGHTER/IMAGES/fondo_") || path.startsWith("INTERIORS/") ||
                (path.startsWith("TRANSIT/") && path.substringAfterLast('/') in setOf("map.webp", "mapa.webp", "inside.webp"))
        }.toSet()
        val represented = catalog.flatMap { it.images }.flatMap { listOf(it.assetPath, it.previewPath) }.toSet()
        val maps = catalog.filter { it.category == GalleryCategory.MAPS }.flatMap { it.images }
            .flatMap { listOf(it.assetPath, it.previewPath) }.toSet()
        assertEquals(expected, maps)
        assertEquals(setOf(GalleryCategory.FIGHTERS, GalleryCategory.MAPS), catalog.map { it.category }.toSet())
        assertEquals(catalog.size, catalog.map { it.id }.distinct().size)
        represented.forEach { path -> assertTrue("Missing or empty image: $path", File(root, path).length() > 0) }
        println("Gallery coverage: ${represented.size} images, ${catalog.size} cards; " +
            GalleryCategory.entries.joinToString { category -> "$category=${catalog.count { it.category == category }}" })
    }

    @Test
    fun everyFighterAndEveryStageLightingIsIncluded() {
        val catalog = GalleryCatalog.load(assets)
        val fighters = catalog.filter { it.source == GallerySource.FIGHTERS }
        assertEquals(SfFighterId.entries.map { "fighter:${it.name}" }.toSet(), fighters.map { it.id }.toSet())
        val maps = catalog.filter { it.source == GallerySource.STAGES }.flatMap { it.images }.map { it.assetPath }.toSet()
        SfStageCatalog.allBackgroundFiles().forEach { file ->
            assertTrue("Stage variant missing: $file", "STREETFIGHTER/IMAGES/$file" in maps)
        }
        fighters.filter { it.images.first().portraitCellSize != null }.forEach {
            assertEquals(256, it.images.first().portraitCellSize)
        }
        SfFighterId.entries.filter { it.sharedSet != null }.forEach { fighter ->
            assertTrue("Shared fighter has no animation frames: $fighter", fighters.single { it.id == "fighter:${fighter.name}" }.images.size > 1)
        }
    }
}
