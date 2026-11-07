package ovh.gabrielhuav.pow.features.main_menu.gallery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GalleryCatalogTest {
    @Test
    fun stageAtlasUsesExistingThumbnailWithoutDuplicatingItsCard() {
        val atlas = "STREETFIGHTER/IMAGES/fondo_escom_noche_2_anim.webp"
        val thumb = atlas.removeSuffix(".webp") + "_thumb.webp"
        val item = GalleryCatalog.fromPaths(listOf(atlas, thumb)).single()
        assertEquals("ESCOM", item.title)
        assertEquals(GalleryLighting.APOCALYPSE, item.lighting)
        assertEquals(atlas, item.images.single().assetPath)
        assertEquals(thumb, item.images.single().previewPath)
    }

    @Test
    fun discoversNewMapsWithoutAddingUnrelatedCharacters() {
        val paths = listOf(
            "SPRITES/NPC/NewCharacter/Walk/n_w_10.webp",
            "SPRITES/NPC/NewCharacter/Idle/n_i_1.webp",
            "SPRITES/NPC/NewCharacter/Walk/n_w_2.webp",
            "STREETFIGHTER/IMAGES/fondo_new_stage_anim_thumb.webp",
            "INTERIORS/NEW/room.webp",
        )
        val items = GalleryCatalog.fromPaths(paths)
        assertEquals(2, items.size)
        assertTrue(items.all { it.category == GalleryCategory.MAPS })
        assertEquals(paths.takeLast(2).toSet(), items.flatMap { it.images }.map { it.assetPath }.toSet())
    }

    @Test
    fun sharedCombatFightersKeepTheirFramesInNaturalOrder() {
        val paths = listOf(
            "SPRITES/PLAYER/lazaroWalk/lazaro_w_10.webp",
            "SPRITES/PLAYER/lazaroIdle/lazaro_i_1.webp",
            "SPRITES/PLAYER/lazaroWalk/lazaro_w_2.webp",
            "SPRITES/PLAYER/escomgirlIdle/escomgirl_i_1.webp",
        )
        val item = GalleryCatalog.fromPaths(paths).single()
        assertEquals("fighter:LAZARO", item.id)
        assertEquals(3, item.images.size)
        assertEquals(listOf(paths[1], paths[2], paths[0]), item.images.map { it.assetPath })
        assertEquals(GalleryCategory.FIGHTERS, item.category)
    }

    @Test
    fun onlyFightersAndMapsAreListedWithStableIdsAndOrdering() {
        val paths = listOf(
            "BUILDINGS/IPN/cic.webp", "ENTITIES/cat.webp", "TRANSIT/METRO/map.webp", "AUDIO/clip.mp3",
            "SPRITES/ZOMBIE/zombie.webp", "STREETFIGHTER/IMAGES/Prankedy.webp", "TRANSIT/METRO/train.webp",
        )
        val items = GalleryCatalog.fromPaths(paths + paths)
        assertEquals(items, GalleryCatalog.fromPaths(paths.reversed()))
        assertEquals(items.size, items.map { it.id }.toSet().size)
        assertEquals(2, items.size)
        assertEquals(setOf(GalleryCategory.FIGHTERS, GalleryCategory.MAPS), items.map { it.category }.toSet())
        assertEquals(256, items.single { it.category == GalleryCategory.FIGHTERS }.images.single().portraitCellSize)
        assertTrue(items.all { it.images.isNotEmpty() })
    }

    @Test
    fun thumbnailsAreBoundedEvenForOddDimensions() {
        assertEquals(1, gallerySampleSize(256, 512, 512))
        assertEquals(4, gallerySampleSize(2048, 1024, 512))
        assertEquals(8, gallerySampleSize(512, 4096, 512))
        assertEquals(2, gallerySampleSize(513, 511, 512))
        assertFailsWith<IllegalArgumentException> { gallerySampleSize(0, 512, 512) }
        assertFailsWith<IllegalArgumentException> { gallerySampleSize(512, 512, 0) }
    }

    @Test
    fun detailResolutionPreservesMoreFramesWithoutUnboundedBitmapMemory() {
        assertEquals(2, gallerySampleSize(2560, 7680, 4096))
        assertEquals(2, gallerySampleSize(4096, 4096, 4096))
        assertEquals(4, gallerySampleSize(8192, 8192, 4096))
    }
}
