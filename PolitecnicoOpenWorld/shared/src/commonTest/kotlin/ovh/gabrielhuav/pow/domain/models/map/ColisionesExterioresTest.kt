package ovh.gabrielhuav.pow.domain.models.map

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 🧱 Las colisiones del exterior, la regla que impide atravesar bardas.
 *
 * `chocaAlMoverse` la usan **las dos plataformas**: Android por `WorldMapMovement.kt` y iOS por
 * `MapaMundoIos`. Si se rompe, el síntoma es que el jugador atraviesa el campus — algo que ningún
 * test de compilación ve y que en el juego se nota enseguida.
 *
 * Los números son coordenadas reales de ESCOM para que los casos sean del tamaño que tienen las
 * cosas de verdad (décimas de milésima de grado ≈ decenas de metros).
 */
class ColisionesExterioresTest {

    /** Un muro corto de este a oeste, justo al norte del jugador. */
    private val muro = CollisionWall(
        name = "BARDA",
        lat1 = 19.5050, lon1 = -99.1470,
        lat2 = 19.5050, lon2 = -99.1450,
    )

    /** Un patio cerrado al que no se debe poder entrar. */
    private val patio = CollisionPolygon(
        name = "PATIO",
        nodes = listOf(
            GeoNode(19.5040, -99.1470),
            GeoNode(19.5040, -99.1450),
            GeoNode(19.5030, -99.1450),
            GeoNode(19.5030, -99.1470),
        ),
    )

    private val config = ExteriorCollisionsConfig(polygons = listOf(patio), walls = listOf(muro))

    // ── Muros ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `cruzar un muro CHOCA`() {
        // De sur a norte, atravesando la barda.
        assertTrue(config.chocaAlMoverse(19.5045, -99.1460, 19.5055, -99.1460))
    }

    @Test
    fun `caminar en paralelo al muro NO choca`() {
        assertFalse(config.chocaAlMoverse(19.5045, -99.1470, 19.5045, -99.1450))
    }

    @Test
    fun `rodear el muro por fuera de su extremo NO choca`() {
        // El muro acaba en lon -99.1450; se cruza su latitud más al este, donde ya no hay barda.
        assertFalse(config.chocaAlMoverse(19.5045, -99.1440, 19.5055, -99.1440))
    }

    @Test
    fun `EL TRAYECTO IMPORTA - un paso largo no puede saltarse la barda`() {
        // Esta es LA razón de comprobar el trayecto y no solo el destino: los dos extremos están
        // fuera del muro, pero el camino entre ellos lo cruza. Mirando solo el destino, el
        // jugador aparecería al otro lado.
        val desdeLat = 19.5040
        val hastaLat = 19.5060
        assertFalse(muro.contieneLatitud(desdeLat), "el origen no está sobre el muro")
        assertFalse(muro.contieneLatitud(hastaLat), "el destino tampoco")
        assertTrue(config.chocaAlMoverse(desdeLat, -99.1460, hastaLat, -99.1460))
    }

    // ── Polígonos ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `entrar en un poligono bloqueado CHOCA`() {
        assertTrue(config.chocaAlMoverse(19.5045, -99.1460, 19.5035, -99.1460))
    }

    @Test
    fun `caminar fuera del poligono NO choca`() {
        assertFalse(config.chocaAlMoverse(19.5020, -99.1460, 19.5021, -99.1460))
    }

    @Test
    fun `el ray-casting acierta dentro y fuera`() {
        assertTrue(patio.contains(19.5035, -99.1460), "el centro está dentro")
        assertFalse(patio.contains(19.5000, -99.1460), "muy al sur, fuera")
        assertFalse(patio.contains(19.5035, -99.1400), "muy al este, fuera")
    }

    // ── Sin configuración ─────────────────────────────────────────────────────────────────────

    @Test
    fun `una configuracion VACIA no bloquea nada`() {
        // Es el modo degradado: si el JSON no está en el bundle, se puede recorrer el mundo.
        // Preferimos un mundo sin bardas a un crash al entrar al mapa.
        val vacia = ExteriorCollisionsConfig()
        assertFalse(vacia.chocaAlMoverse(19.5045, -99.1460, 19.5055, -99.1460))
    }

    /** Ayuda de lectura: ¿esta latitud cae sobre la línea del muro? */
    private fun CollisionWall.contieneLatitud(lat: Double): Boolean = lat == lat1 || lat == lat2
}
