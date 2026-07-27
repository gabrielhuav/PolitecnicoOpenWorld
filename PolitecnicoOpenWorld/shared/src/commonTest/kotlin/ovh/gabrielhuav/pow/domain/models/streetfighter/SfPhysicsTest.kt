package ovh.gabrielhuav.pow.domain.models.streetfighter

import kotlin.test.assertEquals
import kotlin.test.Test

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
        assertEquals(60f, r.x, 0.01f, "avance = (vx - slide)")
        assertEquals(30f, r.slideVelocity, 0.01f, "slide decae friccion*dt")
        assertEquals(10f, r.slideFriction, 0.01f, "friccion se mantiene mientras hay slide")
    }

    @Test
    fun `al agotarse el slide, la friccion se pone a cero`() {
        val r = SfPhysics.step(fighter(slide = 5f, friction = 10f), 1f)
        assertEquals(0f, r.slideVelocity, 0.01f)
        assertEquals(0f, r.slideFriction, 0.01f)
    }

    // ── 🆕 (Fase 2b) clampToStage: límites del mundo + rescate de coordenadas rotas ──

    @Test
    fun `clamp - X queda dentro de los limites del stage`() {
        assertEquals(SfConstants.STAGE_X_MIN, SfPhysics.clampToStage(fighter(x = -500f)).x, 0.01f)
        assertEquals(SfConstants.STAGE_X_MAX, SfPhysics.clampToStage(fighter(x = 99999f)).x, 0.01f)
    }

    @Test
    fun `clamp - Y nunca bajo el piso ni sobre el tope de aire`() {
        assertEquals(
            SfConstants.STAGE_FLOOR,
            SfPhysics.clampToStage(fighter(y = SfConstants.STAGE_FLOOR + 50f)).y,
            0.01f,
        )
        assertEquals(
            SfConstants.STAGE_FLOOR - SfPhysics.STAGE_AIR_CEILING,
            SfPhysics.clampToStage(fighter(y = -99999f)).y,
            0.01f,
        )
    }

    @Test
    fun `clamp - rescata NaN e infinito al centro del stage y al piso`() {
        val roto = fighter(x = Float.NaN, y = Float.POSITIVE_INFINITY)
        val r = SfPhysics.clampToStage(roto)
        assertEquals(SfConstants.STAGE_MID_POINT + SfConstants.STAGE_PADDING, r.x, 0.01f)
        assertEquals(SfConstants.STAGE_FLOOR, r.y, 0.01f)
    }

    @Test
    fun `clamp - un peleador dentro del stage no cambia`() {
        val ok = fighter(x = SfConstants.STAGE_MID_POINT + SfConstants.STAGE_PADDING)
        val r = SfPhysics.clampToStage(ok)
        assertEquals(ok.x, r.x, 0f)
        assertEquals(ok.y, r.y, 0f)
    }
}
