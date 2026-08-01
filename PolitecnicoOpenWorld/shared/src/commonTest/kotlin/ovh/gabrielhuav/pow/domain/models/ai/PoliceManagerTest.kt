package ovh.gabrielhuav.pow.domain.models.ai

import ovh.gabrielhuav.pow.domain.models.geo.GeoPoint
import ovh.gabrielhuav.pow.domain.models.map.NpcType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 🚓 Los tests que `PoliceManager` NO tenía cuando se migró a `commonMain`.
 *
 * ## Por qué existen
 *
 * Este archivo tenía **11 `ConcurrentHashMap`** que hubo que cambiar por `PowMapaConcurrente`
 * para que compilara en Kotlin/Native. Un cambio así no lo caza el compilador si se rompe la
 * semántica: el síntoma en el juego sería una patrulla que desaparece o un policía clavado, a los
 * diez minutos de partida y sin nada en el log.
 *
 * Estos tests **fijan el comportamiento observable** —lo que el ViewModel del mundo consume— para
 * que la próxima persona que toque estos mapas se entere en 2 segundos si lo rompió.
 *
 * ⚠️ **No prueban la persecución**: `update()` mueve unidades sobre una red de calles y su
 * resultado depende de la geometría. Eso se juega, no se testea. Aquí está lo que SÍ es
 * determinista: alta y baja de unidades, daño, subirse a la patrulla y limpiar.
 */
class PoliceManagerTest {

    private val ESCOM = GeoPoint(19.504603, -99.145985)

    /** Un `update` con 1 estrella crea la primera patrulla. */
    private fun conUnaPatrulla(): PoliceManager {
        val pm = PoliceManager()
        pm.update(
            playerLat = ESCOM.latitude, playerLon = ESCOM.longitude,
            roadNetwork = emptyList(), wantedLevel = 1, canShoot = false,
            playerInVehicle = false, now = 10_000L,
        )
        return pm
    }

    @Test
    fun `sin estrellas no hay policia`() {
        val pm = PoliceManager()
        pm.update(ESCOM.latitude, ESCOM.longitude, emptyList(), 0, false, false, 1_000L)
        assertTrue(pm.activeUnits().isEmpty(), "con wantedLevel 0 no debe aparecer nadie")
    }

    @Test
    fun `con una estrella aparece una patrulla`() {
        val unidades = conUnaPatrulla().activeUnits()
        assertEquals(1, unidades.size)
        assertEquals(NpcType.POLICE_CAR, unidades.first().type)
    }

    @Test
    fun `el catalogo de patrullas por estrellas es el pactado`() {
        // Esta tabla la consulta el HUD y el multijugador. Si cambia, cambia el juego.
        assertEquals(0, PoliceManager.desiredCarsFor(0))
        assertEquals(1, PoliceManager.desiredCarsFor(1))
        assertEquals(2, PoliceManager.desiredCarsFor(2))
        assertEquals(4, PoliceManager.desiredCarsFor(3))
        assertEquals(6, PoliceManager.desiredCarsFor(4))
        assertEquals(PoliceManager.MAX_CARS, PoliceManager.desiredCarsFor(5))
    }

    // ── Subirse a la patrulla ─────────────────────────────────────────────────────────────────

    @Test
    fun `subirse a una patrulla la saca de las unidades activas`() {
        val pm = conUnaPatrulla()
        val patrulla = pm.activeUnits().first()
        val subida = pm.boardPatrol(patrulla.id)
        assertNotNull(subida, "boardPatrol debe devolver la patrulla")
        assertEquals(patrulla.id, subida.id)
        assertTrue(pm.activeUnits().none { it.id == patrulla.id }, "ya no puede seguir activa")
    }

    @Test
    fun `subirse a un id que no existe devuelve null`() {
        assertNull(conUnaPatrulla().boardPatrol("NO_EXISTE"))
    }

    // ── Golpear policías ──────────────────────────────────────────────────────────────────────

    @Test
    fun `golpear no destruye PATRULLAS - solo policias a pie`() {
        val pm = conUnaPatrulla()
        val caidos = pm.playerHitPolice(ESCOM.latitude, ESCOM.longitude, radius = 1.0, damage = 999f)
        assertTrue(caidos.isEmpty(), "un coche no se destruye a puñetazos")
        assertEquals(1, pm.activeUnits().size)
    }

    @Test
    fun `golpear lejos no hace nada`() {
        val pm = conUnaPatrulla()
        // A un grado de latitud: ~111 km.
        val caidos = pm.playerHitPolice(ESCOM.latitude + 1.0, ESCOM.longitude, 0.0001, 999f)
        assertTrue(caidos.isEmpty())
    }

    // ── Limpiar ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `clearAll devuelve los ids que quita y deja el gestor vacio`() {
        val pm = conUnaPatrulla()
        val activos = pm.activeUnits().map { it.id }.toSet()
        val quitados = pm.clearAll().toSet()
        assertEquals(activos, quitados, "clearAll debe informar de TODO lo que quitó")
        assertTrue(pm.activeUnits().isEmpty())
    }

    @Test
    fun `clearAll dos veces seguidas no revienta`() {
        val pm = conUnaPatrulla()
        pm.clearAll()
        assertTrue(pm.clearAll().isEmpty())
    }

    @Test
    fun `tras clearAll se puede volver a generar policia`() {
        // Regresión: si `clearAll` dejara basura en los mapas de ruta, la siguiente
        // persecución arrancaría con estado del anterior.
        val pm = conUnaPatrulla()
        pm.clearAll()
        pm.update(ESCOM.latitude, ESCOM.longitude, emptyList(), 1, false, false, 99_000L)
        assertEquals(1, pm.activeUnits().size)
    }
}
