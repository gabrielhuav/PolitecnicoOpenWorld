package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.map.MetroStation
import ovh.gabrielhuav.pow.domain.models.map.MetrobusStation

/**
 * Tests del QUINTO manager con sub-estado propio (Etapa 3, manager 5/6 —
 * ver CHECKPOINT_SENIOR_refactor.md). Lógica pura sin Android: fija el contrato de las
 * transiciones de pantalla (menú de teletransporte, estaciones + fades de metro/metrobús,
 * puerta ESCOM) que la fachada combine vuelca en WorldMapState.
 */
class TransitTeleportManagerTest {

    private fun metro(name: String) = MetroStation(name, listOf("1"), GeoPoint(19.5, -99.1))
    private fun metrobus(name: String) = MetrobusStation(name, listOf("1"), GeoPoint(19.5, -99.1))

    @Test
    fun initial_substate_is_empty() {
        val m = TransitTeleportManager()
        val s = m.state.value
        assertFalse(s.showTeleportMenu)
        assertTrue(s.metroStations.isEmpty())
        assertNull(s.nearbyMetroStation)
        assertFalse(s.showMetroFade)
        assertNull(s.metroFadeCompleteStation)
        assertTrue(s.metrobusStations.isEmpty())
        assertFalse(s.showEscomDoorFade)
        assertFalse(s.escomDoorFadeComplete)
        assertNull(s.pendingDoorDestination)
    }

    @Test
    fun teleport_menu_toggle() {
        val m = TransitTeleportManager()
        m.setTeleportMenu(true)
        assertTrue(m.state.value.showTeleportMenu)
        m.setTeleportMenu(false)
        assertFalse(m.state.value.showTeleportMenu)
    }

    @Test
    fun set_station_catalogs() {
        val m = TransitTeleportManager()
        m.setMetroStations(listOf(metro("Zapata"), metro("Balderas")))
        m.setMetrobusStations(listOf(metrobus("Insurgentes")))
        assertEquals(2, m.state.value.metroStations.size)
        assertEquals(listOf("Insurgentes"), m.state.value.metrobusStations.map { it.name })
    }

    @Test
    fun set_and_clear_nearby_stations() {
        val m = TransitTeleportManager()
        m.setNearbyMetro(metro("Zapata"))
        assertEquals("Zapata", m.state.value.nearbyMetroStation?.name)
        m.setNearbyMetro(null)
        assertNull(m.state.value.nearbyMetroStation)
    }

    @Test
    fun metro_fade_flow_completes_only_with_nearby() {
        val m = TransitTeleportManager()
        // Sin estación cercana: onMetroFadeComplete no dispara.
        assertFalse(m.onMetroFadeComplete())
        // Con estación cercana: se arma el fade, completa y navega.
        m.setNearbyMetro(metro("Zapata"))
        m.beginMetroFade()
        assertTrue(m.state.value.showMetroFade)
        assertTrue(m.onMetroFadeComplete())
        assertFalse(m.state.value.showMetroFade)
        assertEquals("Zapata", m.state.value.metroFadeCompleteStation?.name)
        assertNull(m.state.value.nearbyMetroStation)   // el radar se limpió
        // Consumir la navegación limpia la estación destino.
        m.consumeMetroFadeComplete()
        assertNull(m.state.value.metroFadeCompleteStation)
    }

    @Test
    fun metrobus_fade_flow_completes_only_with_nearby() {
        val m = TransitTeleportManager()
        assertFalse(m.onMetrobusFadeComplete())
        m.setNearbyMetrobus(metrobus("Insurgentes"))
        m.beginMetrobusFade()
        assertTrue(m.onMetrobusFadeComplete())
        assertEquals("Insurgentes", m.state.value.metrobusFadeCompleteStation?.name)
        assertNull(m.state.value.nearbyMetrobusStation)
        m.consumeMetrobusFadeComplete()
        assertNull(m.state.value.metrobusFadeCompleteStation)
    }

    @Test
    fun escom_door_fade_flow() {
        val m = TransitTeleportManager()
        m.beginEscomDoorFade("interiores_zombies?startRoom=escom_lobby")
        assertTrue(m.state.value.showEscomDoorFade)
        assertEquals("interiores_zombies?startRoom=escom_lobby", m.state.value.pendingDoorDestination)
        m.onEscomDoorFadeComplete()
        assertFalse(m.state.value.showEscomDoorFade)
        assertTrue(m.state.value.escomDoorFadeComplete)
        // Consumir devuelve el destino y limpia el flag + destino.
        assertEquals("interiores_zombies?startRoom=escom_lobby", m.consumeEscomDoorNavigation())
        assertFalse(m.state.value.escomDoorFadeComplete)
        assertNull(m.state.value.pendingDoorDestination)
    }

    @Test
    fun clear_on_teleport_wipes_menu_and_fades_but_keeps_catalogs_and_door() {
        val m = TransitTeleportManager()
        m.setMetroStations(listOf(metro("Zapata")))
        m.setMetrobusStations(listOf(metrobus("Insurgentes")))
        m.setTeleportMenu(true)
        m.setNearbyMetro(metro("Zapata"))
        m.beginMetroFade()
        m.setNearbyMetrobus(metrobus("Insurgentes"))
        m.beginEscomDoorFade("ruta")   // la puerta ESCOM NO se toca en el TP
        m.clearTransitOnTeleport()
        val s = m.state.value
        assertFalse(s.showTeleportMenu)
        assertFalse(s.showMetroFade)
        assertNull(s.nearbyMetroStation)
        assertNull(s.metroFadeCompleteStation)
        assertFalse(s.showMetrobusFade)
        assertNull(s.nearbyMetrobusStation)
        // Los catálogos persisten (no se recargan en cada TP).
        assertEquals(1, s.metroStations.size)
        assertEquals(1, s.metrobusStations.size)
        // La transición de puerta ESCOM sobrevive al TP.
        assertTrue(s.showEscomDoorFade)
        assertEquals("ruta", s.pendingDoorDestination)
    }
}
