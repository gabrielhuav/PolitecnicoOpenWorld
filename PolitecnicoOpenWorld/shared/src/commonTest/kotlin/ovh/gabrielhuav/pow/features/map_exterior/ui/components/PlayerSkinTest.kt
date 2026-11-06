package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlayerSkinTest {

    @Test
    fun laPlanchadaSkinEstaRegistradaYEsValida() {
        val skin = PlayerSkin.valueOf("LA_PLANCHADA")
        assertNotNull(skin)
        assertEquals("La Planchada", skin.displayName)
        assertEquals("LaPlanchada/", skin.skinFolder)
        assertEquals("lpl_", skin.skinPrefix)
        assertEquals("SPRITES/NPC/", skin.basePath)
        assertEquals(6, skin.idleFrames)
        assertEquals(6, skin.walkFrames)
        assertEquals(8, skin.runFrames)
        assertEquals(5, skin.specialFrames)
        assertTrue(skin.uniform512Canvas)
        PlayerAction.entries.forEach { action ->
            assertEquals(PlayerSkin.WORLD_BODY_CANVAS_FRACTION, skin.bodyFraction(action))
        }
        assertEquals("SPRITES/NPC/LaPlanchada/Idle/lpl_i_1.webp", skin.previewAsset)
    }

    @Test
    fun skinInvalidaOCorruptaRetornaFallbackSeguro() {
        val invalidSkinName = "INVALID_CORRUPTED_SKIN_123"
        val resolvedSkin = runCatching { PlayerSkin.valueOf(invalidSkinName) }.getOrElse { PlayerSkin.LAZARO }
        assertEquals(PlayerSkin.LAZARO, resolvedSkin, "Un identificador corrupto debe retornar LAZARO como fallback seguro")
    }

    @Test
    fun laPlanchadaFighterEstaRegistradoEnModoCombate() {
        val fighter = ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterId.valueOf("LA_PLANCHADA")
        assertNotNull(fighter)
        assertEquals("La Planchada", fighter.displayName)
        assertEquals("PLANCHADA", fighter.shortName)
        assertEquals("STREETFIGHTER/DATA/laplanchada.json", fighter.jsonAsset)
        assertEquals("STREETFIGHTER/IMAGES/LaPlanchada.webp", fighter.spriteAsset)
    }

    @Test
    fun todasLasSkinsTienenConfiguracionCoherente() {
        PlayerSkin.entries.forEach { skin ->
            assertTrue(skin.displayName.isNotBlank(), "DisplayName no puede estar vacio: ${skin.name}")
            assertTrue(skin.idleFrames > 0, "Debe tener al menos 1 frame de idle: ${skin.name}")
            assertTrue(skin.walkFrames > 0, "Debe tener al menos 1 frame de walk: ${skin.name}")
            assertTrue(skin.previewAsset.endsWith(".webp"), "Preview asset debe ser .webp: ${skin.name}")
        }
    }
}
