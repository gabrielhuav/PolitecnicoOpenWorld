package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlayerSkinTest {

    @Test
    fun calvoConCapaSkinEstaRegistradaYEsValida() {
        val skin = PlayerSkin.valueOf("CALVO_CON_CAPA")
        assertNotNull(skin)
        assertEquals("Calvo con Capa", skin.displayName)
        assertEquals("CalvoConCapa/", skin.skinFolder)
        assertEquals("ccc_", skin.skinPrefix)
        assertEquals("SPRITES/NPC/", skin.basePath)
        assertEquals(6, skin.idleFrames)
        assertEquals(6, skin.walkFrames)
        assertEquals(8, skin.runFrames)
        assertEquals(5, skin.specialFrames)
        assertTrue(skin.uniform512Canvas)
        assertEquals("SPRITES/NPC/CalvoConCapa/Idle/ccc_i_1.webp", skin.previewAsset)
    }

    @Test
    fun skinInvalidaOCorruptaRetornaFallbackSeguro() {
        val invalidSkinName = "INVALID_CORRUPTED_SKIN_123"
        val resolvedSkin = runCatching { PlayerSkin.valueOf(invalidSkinName) }.getOrElse { PlayerSkin.LAZARO }
        assertEquals(PlayerSkin.LAZARO, resolvedSkin, "Un identificador corrupto debe retornar LAZARO como fallback seguro")
    }

    @Test
    fun calvoConCapaFighterEstaRegistradoEnModoCombate() {
        val fighter = ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterId.valueOf("CALVO_CON_CAPA")
        assertNotNull(fighter)
        assertEquals("El Calvo con Capa", fighter.displayName)
        assertEquals("CALVO", fighter.shortName)
        assertNotNull(fighter.sharedSet)
        assertEquals("CalvoConCapa/", fighter.sharedSet.folder)
        assertEquals("ccc_", fighter.sharedSet.prefix)
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
