package ovh.gabrielhuav.pow.domain.models.streetfighter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 🥊 SIMULACIÓN PURA (cámara, chispazos, barra de vida) — Fase 2c del motor compartido.
 *
 * Estas tres piezas vivían dentro del `StreetFighterViewModel`, o sea sin tests y sin iOS.
 * Aquí quedan fijadas, y estos tests corren en Android **y en el simulador de iOS**.
 *
 * ⚠️ Varios de estos tests fijan CONSTANTES a propósito (la velocidad de drenaje, los topes de
 * cámara). No es rigidez: al mover el código me equivoqué en dos de ellas y el compilador no
 * podía avisar — solo cambiaba cómo se siente la pelea.
 */
class SfSimulationTest {

    private fun peleador(x: Float, y: Float = SfConstants.STAGE_FLOOR) = SfFighter(
        id = SfFighterId.entries.first(), playerIndex = 0,
        x = x, y = y, direction = SfDirection.RIGHT,
    )

    private fun chispazo(animationTimerMs: Long) = SfHitSplash(
        x = 0f, y = 0f, playerId = 0, strength = SfAttackStrength.LIGHT,
        animationFrame = 0, animationTimerMs = animationTimerMs,
    )

    // ── Barra de vida ────────────────────────────────────────────────────────

    @Test
    fun `la barra BAJA gradualmente, no de golpe`() {
        // Con dt = 0.1 s solo puede drenar DRAIN_PER_SEC * 0.1 puntos.
        val r = SfHealthBar.rollUp(mostrado = 100f, objetivo = 0, dt = 0.1f)
        assertEquals(100f - SfHealthBar.DRAIN_PER_SEC * 0.1f, r, 0.001f)
        assertTrue(r > 0f, "no debe llegar al objetivo en un solo paso")
    }

    @Test
    fun `la barra SUBE de golpe (curarse o empezar ronda no se anima)`() {
        assertEquals(100f, SfHealthBar.rollUp(mostrado = 10f, objetivo = 100, dt = 0.016f))
    }

    @Test
    fun `nunca drena POR DEBAJO del objetivo`() {
        val r = SfHealthBar.rollUp(mostrado = 5f, objetivo = 0, dt = 10f) // dt enorme
        assertEquals(0f, r, "se para en el objetivo, no lo cruza")
    }

    @Test
    fun `la velocidad de drenaje es la del juego`() {
        // ⚠️ Al extraer esto puse 60f de mi cosecha. El valor real es 200f.
        assertEquals(200f, SfHealthBar.DRAIN_PER_SEC)
    }

    // ── Cámara ───────────────────────────────────────────────────────────────

    @Test
    fun `con los peleadores MUY separados la camara se centra entre ambos`() {
        val izq = peleador(SfConstants.STAGE_PADDING + 30f)
        val der = peleador(SfConstants.STAGE_PADDING + 700f)
        val pos = SfCamera.follow(izq, der, camX = SfConstants.STAGE_PADDING)
        val centro = (izq.x + der.x) / 2f
        // El centro de la vista debe quedar cerca del punto medio entre los dos.
        assertTrue(
            kotlin.math.abs((pos.x + SfConstants.SCENE_WIDTH / 2f) - centro) < 1f,
            "la camara deberia centrarse; dio x=${pos.x}",
        )
    }

    @Test
    fun `la camara NUNCA se sale del escenario`() {
        val minimo = SfConstants.STAGE_PADDING
        val maximo = SfConstants.STAGE_WIDTH + SfConstants.STAGE_PADDING - SfConstants.SCENE_WIDTH
        for (x in listOf(-9999f, 0f, 400f, 900f, 99999f)) {
            val pos = SfCamera.follow(peleador(x), peleador(x + 20f), camX = x)
            assertTrue(pos.x in minimo..maximo, "camX=${pos.x} fuera de [$minimo,$maximo]")
        }
    }

    @Test
    fun `la camara sube al saltar y nunca sale del fondo`() {
        val suelo = SfCamera.follow(peleador(500f), peleador(560f), camX = 400f)
        val salto = SfCamera.follow(
            peleador(500f, SfConstants.STAGE_FLOOR - 150f), peleador(560f), camX = 400f,
        )
        assertTrue(salto.y <= suelo.y, "al saltar la camara no baja")
        for (p in listOf(suelo, salto)) {
            assertTrue(
                p.y in 0f..(SfConstants.STAGE_HEIGHT - SfConstants.SCENE_HEIGHT),
                "camY=${p.y} fuera del fondo",
            )
        }
    }

    // ── Chispazos ────────────────────────────────────────────────────────────

    @Test
    fun `un chispazo avanza de cuadro y acaba retirandose`() {
        val paso = (4 * SfConstants.FRAME_TIME_MS).toLong()
        val lista = mutableListOf(chispazo(animationTimerMs = 0L))
        var now = paso
        repeat(SfSplashes.FRAMES) {
            SfSplashes.advance(lista, now)
            now += paso
        }
        assertTrue(lista.isEmpty(), "tras ${SfSplashes.FRAMES} cuadros debe desaparecer")
    }

    @Test
    fun `un chispazo NO avanza antes de que toque`() {
        val lista = mutableListOf(chispazo(animationTimerMs = 1000L))
        SfSplashes.advance(lista, now = 1001L) // aun no ha pasado el paso
        assertEquals(1, lista.size)
        assertEquals(0, lista[0].animationFrame, "no debe avanzar de cuadro todavia")
    }

    @Test
    fun `lista vacia no revienta`() {
        val lista = mutableListOf<SfHitSplash>()
        SfSplashes.advance(lista, now = 123L)
        assertTrue(lista.isEmpty())
    }
}
