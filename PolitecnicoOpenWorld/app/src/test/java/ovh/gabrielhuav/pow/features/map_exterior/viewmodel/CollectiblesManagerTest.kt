package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ovh.gabrielhuav.pow.domain.models.map.ActiveCollectible

/**
 * Tests del SEGUNDO manager con sub-estado propio (Etapa 3, manager 2/6 —
 * ver CHECKPOINT_SENIOR_refactor.md). Lógica pura sin Android; fija el contrato del
 * sub-estado UI de COLECCIONABLES que la fachada combine vuelca en WorldMapState.
 */
class CollectiblesManagerTest {

    private fun item(id: String) = ActiveCollectible(
        id = id,
        name = "N-$id",
        description = "d",
        assetPath = "",
        latitude = 0.0,
        longitude = 0.0
    )

    @Test
    fun initial_substate_is_empty() {
        val m = CollectiblesManager()
        assertTrue(m.state.value.activeCollectibles.isEmpty())
        assertNull(m.state.value.nearbyCollectible)
        assertNull(m.state.value.showClaimedPopupFor)
    }

    @Test
    fun set_active_replaces_the_list() {
        val m = CollectiblesManager()
        m.setActive(listOf(item("a"), item("b")))
        assertEquals(2, m.state.value.activeCollectibles.size)
        m.setActive(listOf(item("c")))
        assertEquals(listOf("c"), m.state.value.activeCollectibles.map { it.id })
    }

    @Test
    fun add_active_keeps_previous_items() {
        val m = CollectiblesManager()
        m.setActive(listOf(item("a")))
        m.addActive(item("shine"))
        assertEquals(listOf("a", "shine"), m.state.value.activeCollectibles.map { it.id })
    }

    @Test
    fun clear_active_empties_only_the_list() {
        val m = CollectiblesManager()
        m.setActive(listOf(item("a")))
        m.setNearby(item("a"))
        m.clearActive()
        assertTrue(m.state.value.activeCollectibles.isEmpty())
        // clearActive NO toca el radar cercano.
        assertEquals("a", m.state.value.nearbyCollectible?.id)
    }

    @Test
    fun set_and_clear_nearby() {
        val m = CollectiblesManager()
        m.setNearby(item("x"))
        assertEquals("x", m.state.value.nearbyCollectible?.id)
        m.clearNearby()
        assertNull(m.state.value.nearbyCollectible)
    }

    @Test
    fun claim_empties_active_and_nearby_and_sets_popup_atomically() {
        val m = CollectiblesManager()
        m.setActive(listOf(item("a")))
        m.setNearby(item("a"))
        m.claim(item("a"))
        assertTrue(m.state.value.activeCollectibles.isEmpty())
        assertNull(m.state.value.nearbyCollectible)
        assertEquals("a", m.state.value.showClaimedPopupFor?.id)
    }

    @Test
    fun dismiss_claimed_popup_clears_only_the_popup() {
        val m = CollectiblesManager()
        m.claim(item("a"))
        m.dismissClaimedPopup()
        assertNull(m.state.value.showClaimedPopupFor)
    }
}
