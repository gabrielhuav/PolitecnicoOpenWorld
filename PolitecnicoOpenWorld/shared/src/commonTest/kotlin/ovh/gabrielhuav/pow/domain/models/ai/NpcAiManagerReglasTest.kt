package ovh.gabrielhuav.pow.domain.models.ai

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ovh.gabrielhuav.pow.platform.tiempo.ahoraMs
import ovh.gabrielhuav.pow.domain.models.geo.GeoPoint
import ovh.gabrielhuav.pow.domain.models.map.MapNode
import ovh.gabrielhuav.pow.domain.models.map.MapWay
import ovh.gabrielhuav.pow.domain.models.map.Npc
import ovh.gabrielhuav.pow.domain.models.map.NpcTrait
import ovh.gabrielhuav.pow.domain.models.map.NpcType

/**
 * RED DE SEGURIDAD antes de bajar `NpcAiManager` a `commonMain` (doc 12 §fase 3: "escribe tests de
 * sus reglas ANTES de tocarlo"). Fija el comportamiento OBSERVABLE de hoy —el que tiene que seguir
 * igual cuando `CopyOnWriteArrayList`, `AtomicReference` y `System.currentTimeMillis` se cambien
 * por sus equivalentes multiplataforma— no la implementación.
 *
 * ⚠️ Se escribieron primero en `:app` para correrlos contra la versión Android... y NO CORRIERON:
 * `ExceptionInInitializerError`. `NpcAiManager` tenía `android.graphics.Color.rgb(...)` en el
 * inicializador de `CAR_COLORS`, y en un test JVM sin Robolectric eso lanza. **Por eso esta clase
 * tenía 0 tests.** Al sustituirlo por `colorArgb` la clase por fin se puede construir en un test,
 * así que la red de seguridad existe DESDE la migración, no antes. Es lo que hay: no había forma
 * de tener una foto del "antes" sin cambiar la configuración global de tests de `:app`.
 */
class NpcAiManagerReglasTest {

    /**
     * ⚠️ `updateNpcs` **no hace NADA** si no se ha llamado antes a [NpcAiManager.updateRoadNetwork]
     * con una red no vacía: sale por `if (!networkIsReady …) return`. Es deliberado (sin calles no
     * hay dónde simular), y es la primera piedra con la que tropieza quien escribe un test aquí.
     */
    private fun conCalles() = NpcAiManager().apply {
        updateRoadNetwork(listOf(via(listOf(19.4990 to -99.1410, 19.5010 to -99.1390))))
    }

    private fun peaton(id: String, lat: Double, lon: Double, trait: NpcTrait = NpcTrait.PASSIVE) = Npc(
        id = id, type = NpcType.PERSON, location = GeoPoint(lat, lon),
        speed = NpcAiManager.PERSON_SPEED, isRemote = false, trait = trait
    )

    @Test
    fun `setServerNpcs reemplaza la lista entera`() {
        val m = NpcAiManager()
        m.setServerNpcs(listOf(peaton("a", 19.5, -99.14), peaton("b", 19.5, -99.14)))
        m.setServerNpcs(listOf(peaton("c", 19.5, -99.14)))
        assertEquals(listOf("c"), m.getServerNpcs().map { it.id })
    }

    @Test
    fun `addServerNpcs conserva los que ya estaban y deduplica por id`() {
        val m = NpcAiManager()
        m.setServerNpcs(listOf(peaton("a", 19.5, -99.14)))
        m.addServerNpcs(listOf(peaton("a", 19.6, -99.15), peaton("b", 19.5, -99.14)))
        assertEquals(listOf("a", "b"), m.getServerNpcs().map { it.id }.sorted())
        // El duplicado NO pisa al original: se ignora entero.
        assertEquals(19.5, m.getServerNpcs().first { it.id == "a" }.location.latitude, 1e-9)
    }

    @Test
    fun `addServerNpcs con lista vacia no toca nada`() {
        val m = NpcAiManager()
        m.setServerNpcs(listOf(peaton("a", 19.5, -99.14)))
        m.addServerNpcs(emptyList())
        assertEquals(1, m.getServerNpcs().size)
    }

    /**
     * El miedo se aplica en el tick, no al dispararlo. Fija las TRES reglas de `applyPendingFear`:
     * radio, exención de los AGGRESSIVE y exención de los que llevan `displayName` (jugadores).
     */
    @Test
    fun `triggerFear marca a los cercanos y respeta agresivos y jugadores`() = runBlocking<Unit> {
        val m = conCalles()
        val cerca = peaton("cerca", 19.5000, -99.1400)
        // ⚠️ FUERA del radio de miedo (0.0018) pero DENTRO de `despawnDistance` (0.0028): a 19.6
        // el tick lo despawnea por lejanía y `porId["lejos"]` sale null en vez de "sin miedo".
        val lejos = peaton("lejos", 19.5000 + 0.0023, -99.1400)
        val bravo = peaton("bravo", 19.5000, -99.1400, trait = NpcTrait.AGGRESSIVE)
        val jugador = peaton("jugador", 19.5000, -99.1400).copy(displayName = "Otro")
        m.setServerNpcs(listOf(cerca, lejos, bravo, jugador))

        m.triggerFear(19.5000, -99.1400)
        m.updateNpcs(GeoPoint(19.5000, -99.1400), amIHost = true)

        val porId = m.getServerNpcs().associateBy { it.id }
        assertTrue((porId["cerca"]?.fearUntil ?: 0L) > 0L, "el de al lado tiene que asustarse")
        assertEquals(0L, porId["lejos"]?.fearUntil, "fuera del radio, nada")
        assertEquals(0L, porId["bravo"]?.fearUntil, "los AGGRESSIVE son inmunes al miedo")
        assertEquals(0L, porId["jugador"]?.fearUntil, "un jugador remoto no es un NPC al que asustar")
    }

    /**
     * `fearUntil` es una marca de tiempo que CRUZA la frontera del módulo: la escribe el manager y
     * la compara el `WorldMapViewModel` contra su propio reloj. Este test fija que sea de ÉPOCA
     * (comparable con `ahoraMs()`), que es justo lo que rompería un reloj monótono.
     */
    @Test
    fun `fearUntil queda en el futuro cercano del reloj de epoca`() = runBlocking<Unit> {
        val m = conCalles()
        m.setServerNpcs(listOf(peaton("x", 19.5, -99.14)))
        val antes = ahoraMs()
        m.triggerFear(19.5, -99.14)
        m.updateNpcs(GeoPoint(19.5, -99.14), amIHost = true)
        val fear = m.getServerNpcs().first().fearUntil

        assertTrue(fear > antes, "debe estar por delante de ahora")
        assertTrue(
            fear <= ahoraMs() + NpcAiManager.FEAR_DURATION_MS,
            "y como mucho a FEAR_DURATION_MS de distancia (si sale un valor absurdo, hay dos relojes)"
        )
    }

    @Test
    fun `sin ser host el tick no simula`() = runBlocking<Unit> {
        val m = conCalles()
        m.setServerNpcs(listOf(peaton("a", 19.5, -99.14)))
        m.triggerFear(19.5, -99.14)
        m.updateNpcs(GeoPoint(19.5, -99.14), amIHost = false)
        assertEquals(0L, m.getServerNpcs().first().fearUntil, "un cliente no-host no aplica miedo")
    }

    /** Una vía cuenta como "solapada" con un landmark cuando MÁS del 45 % de sus nodos caen dentro. */
    @Test
    fun `isNativeWayOverlappingCustom exige mas del 45 por ciento de nodos dentro`() {
        val m = NpcAiManager()
        val lm = landmarkCuadrado(19.5, -99.14, medioLado = 0.001)

        val casiToda = via(listOf(19.5000 to -99.1400, 19.5002 to -99.1400, 19.4998 to -99.1400, 19.6 to -99.14))
        val soloUna = via(listOf(19.5000 to -99.1400, 19.6 to -99.14, 19.7 to -99.14, 19.8 to -99.14))

        assertTrue(m.isNativeWayOverlappingCustom(casiToda, listOf(lm)))
        assertTrue(!m.isNativeWayOverlappingCustom(soloUna, listOf(lm)))
        assertTrue(!m.isNativeWayOverlappingCustom(casiToda, emptyList()), "sin landmarks no hay solape posible")
    }

    private fun via(puntos: List<Pair<Double, Double>>) = MapWay(
        id = 1L,
        nodes = puntos.mapIndexed { i, (la, lo) -> MapNode(i.toLong(), la, lo) },
        isForCars = true,
        isForPeople = true
    )

    /** `contains` mira una caja centrada en `location` de `baseWidth/HeightMeters`. ~0.001° ≈ 111 m. */
    private fun landmarkCuadrado(lat: Double, lon: Double, medioLado: Double) =
        ovh.gabrielhuav.pow.domain.models.map.Landmark(
            id = 1L, name = "LM",
            location = GeoPoint(lat, lon),
            assetPath = "NADA",
            baseWidthMeters = (medioLado * 2 * 111_320.0).toFloat(),
            baseHeightMeters = (medioLado * 2 * 111_320.0).toFloat()
        )
}
