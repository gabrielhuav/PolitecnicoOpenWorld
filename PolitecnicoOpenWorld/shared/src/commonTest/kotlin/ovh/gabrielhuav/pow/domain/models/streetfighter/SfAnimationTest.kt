package ovh.gabrielhuav.pow.domain.models.streetfighter

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Tests de CARACTERIZACIÓN del avance de animación (Fase 2a del refactor). Fijan la semántica
 * heredada del clon JS: wrap al fijar más allá del final, delay<=0 = FREEZE/TRANSITION (no
 * avanza sola), y "completa" en el frame -1 O en el último (fix de hojas sin terminador).
 */
class SfAnimationTest {

    private fun anim(vararg delays: Int): List<SfAnimFrame> =
        delays.mapIndexed { i, d -> SfAnimFrame(frameKey = "f-$i", delay = d) }

    @Test
    fun `fijar un frame mas alla del final hace WRAP a 0 (bucle)`() {
        val a = anim(4, 4, 4)
        assertEquals(0, SfAnimation.frameIndex(a, 3))
        assertEquals(0, SfAnimation.frameIndex(a, 7))
        assertEquals(2, SfAnimation.frameIndex(a, 2))
        assertEquals(0, SfAnimation.frameIndex(a, 0))
    }

    @Test
    fun `el timer del frame es ahora mas delay por FRAME_TIME_MS`() {
        val a = anim(4, 6)
        val now = 1000L
        assertEquals(now + (4 * SfConstants.FRAME_TIME_MS).toLong(), SfAnimation.frameTimerMs(a, 0, now))
        assertEquals(now + (6 * SfConstants.FRAME_TIME_MS).toLong(), SfAnimation.frameTimerMs(a, 1, now))
    }

    @Test
    fun `avanza solo con delay positivo y timer vencido`() {
        val a = anim(4, 4)
        assertTrue(SfAnimation.shouldAdvance(a, frame = 0, timerMs = 100L, now = 101L))
        assertFalse(SfAnimation.shouldAdvance(a, frame = 0, timerMs = 100L, now = 100L), "timer aun no vence")
        assertFalse(SfAnimation.shouldAdvance(a, frame = 0, timerMs = 100L, now = 50L), "timer en el futuro")
    }

    @Test
    fun `TRANSITION (0) y FREEZE (-1) nunca avanzan solos`() {
        assertFalse(SfAnimation.shouldAdvance(anim(0, 4), frame = 0, timerMs = 0L, now = 9999L))
        assertFalse(SfAnimation.shouldAdvance(anim(-1, 4), frame = 0, timerMs = 0L, now = 9999L))
    }

    @Test
    fun `un frame fuera de rango se coerce para leer su delay`() {
        // frame 9 en una anim de 2: se lee el delay del último (4 > 0) → puede avanzar
        assertTrue(SfAnimation.shouldAdvance(anim(4, 4), frame = 9, timerMs = 0L, now = 1L))
    }

    @Test
    fun `completa en el frame TERMINADOR -1 aunque no sea el ultimo`() {
        assertTrue(SfAnimation.isCompleted(anim(4, -1, 4), frame = 1))
    }

    @Test
    fun `completa en el ULTIMO frame aunque no haya -1 (fix hojas compartidas)`() {
        assertTrue(SfAnimation.isCompleted(anim(4, 4, 4), frame = 2))
        assertFalse(SfAnimation.isCompleted(anim(4, 4, 4), frame = 1))
    }

    @Test
    fun `una animacion de UN frame siempre esta completa`() {
        assertTrue(SfAnimation.isCompleted(anim(4), frame = 0))
    }
}
