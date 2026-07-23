package ovh.gabrielhuav.pow.domain.models.streetfighter

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests de CARACTERIZACIÓN de la cinemática de un tick (Fase 1 del refactor). Fija cómo avanzan
 * posición y `slide` en un paso, de forma determinista.
 */
class SfPhysicsTest {

    private fun fighter(
        x: Float = 0f,
        y: Float = SfConstants.STAGE_FLOOR,
        vx: Float = 0f,
        vy: Float = 0f,
        slide: Float = 0f,
        friction: Float = 0f,
        dir: SfDirection = SfDirection.RIGHT,
    ): SfFighter = SfFighter(
        id = SfFighterId.ESCOMBOY, playerIndex = 0, x = x, direction = dir,
    ).copy(y = y, velocityX = vx, velocityY = vy, slideVelocity = slide, slideFriction = friction)

    @Test
    fun `la posicion X avanza por la velocidad, segun el encaramiento`() {
        assertEquals(100f, SfPhysics.step(fighter(x = 0f, vx = 100f, dir = SfDirection.RIGHT), 1f).x, 0.01f)
        assertEquals(-100f, SfPhysics.step(fighter(x = 0f, vx = 100f, dir = SfDirection.LEFT), 1f).x, 0.01f)
    }

    @Test
    fun `la posicion Y avanza por la velocidad vertical`() {
        assertEquals(50f, SfPhysics.step(fighter(y = 100f, vy = -50f), 1f).y, 0.01f)
    }

    @Test
    fun `el slide RESTA al avance y DECAE por su friccion`() {
        val r = SfPhysics.step(fighter(x = 0f, vx = 100f, slide = 40f, friction = 10f), 1f)
        assertEquals("avance = (vx - slide)", 60f, r.x, 0.01f)
        assertEquals("slide decae friccion*dt", 30f, r.slideVelocity, 0.01f)
        assertEquals("friccion se mantiene mientras hay slide", 10f, r.slideFriction, 0.01f)
    }

    @Test
    fun `al agotarse el slide, la friccion se pone a cero`() {
        val r = SfPhysics.step(fighter(slide = 5f, friction = 10f), 1f)
        assertEquals(0f, r.slideVelocity, 0.01f)
        assertEquals(0f, r.slideFriction, 0.01f)
    }
}
