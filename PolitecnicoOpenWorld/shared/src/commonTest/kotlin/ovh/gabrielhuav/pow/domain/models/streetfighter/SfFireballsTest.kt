package ovh.gabrielhuav.pow.domain.models.streetfighter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SfFireballsTest {
    private fun projectile(x: Float = 400f, owner: Int = 0) = SfFireball(
        ownerIndex = owner,
        x = x,
        y = 100f,
        direction = SfDirection.RIGHT,
        strength = SfAttackStrength.LIGHT,
        velocity = 100f,
        animationTimerMs = 1000L,
    )

    @Test
    fun `el impacto que limpia proyectiles al abrir remate no aborta ni los resucita`() {
        val fireballs = mutableListOf(projectile(), projectile(x = 500f, owner = 1))
        var impacts = 0
        SfFireballs.advance(fireballs, now = 100L, dt = 0.1f, camX = 256f, maxFireballs = 4,
            hitArea = { SfHurtArea.BODY },
            onHit = { fb, area ->
                impacts++
                assertEquals(SfHurtArea.BODY, area)
                assertEquals(SfFireballState.COLLIDED, fb.state)
                assertSame(fb, fireballs.first())
                // Reproduce el callback de applyAttackHit → endRound → openRemateWindow.
                fireballs.clear()
            },
        )
        assertEquals(1, impacts, "la lista invalidada no debe seguir causando impactos")
        assertTrue(fireballs.isEmpty(), "no debe reinsertar el proyectil que el remate eliminó")
    }

    @Test
    fun `si el impacto reemplaza la lista los nuevos proyectiles esperan al siguiente tick`() {
        val fireballs = mutableListOf(projectile(), projectile(x = 500f, owner = 1))
        val replacement = listOf(projectile(x = 450f), projectile(x = 550f, owner = 1))
        var impacts = 0
        SfFireballs.advance(fireballs, now = 100L, dt = 0.1f, camX = 256f, maxFireballs = 4,
            hitArea = { SfHurtArea.BODY },
            onHit = { _, _ ->
                impacts++
                fireballs.clear()
                fireballs.addAll(replacement)
            },
        )
        assertEquals(1, impacts)
        assertEquals(replacement, fireballs, "no debe avanzar ni sobrescribir una lista reiniciada")
    }

    @Test
    fun `retirar un proyectil agotado o fuera de pantalla no salta el siguiente`() {
        val expired = projectile().copy(state = SfFireballState.COLLIDED, animationFrame = 2, animationTimerMs = 0L)
        val outside = projectile(x = 1000f)
        val moving = projectile(x = 500f).copy(animationTimerMs = 0L)
        val fireballs = mutableListOf(expired, outside, moving)
        var collisionChecks = 0
        SfFireballs.advance(fireballs, now = 100L, dt = 0.1f, camX = 256f, maxFireballs = 4,
            hitArea = { collisionChecks++; null },
            onHit = { _, _ -> error("sin cajas solapadas no debe aplicar daño") },
        )
        assertEquals(1, fireballs.size)
        assertEquals(510f, fireballs.single().x)
        assertEquals(1, fireballs.single().animationFrame)
        assertEquals(1, collisionChecks)
    }

    @Test
    fun `un impacto normal conserva el proyectil colisionado con su velocidad reducida`() {
        val fireballs = mutableListOf(projectile())
        var impacts = 0
        SfFireballs.advance(fireballs, now = 100L, dt = 0.1f, camX = 256f, maxFireballs = 4,
            hitArea = { SfHurtArea.BODY },
            onHit = { _, _ -> impacts++ },
        )
        assertEquals(1, impacts)
        val result = fireballs.single()
        assertEquals(SfFireballState.COLLIDED, result.state)
        assertEquals(410f, result.x)
        assertEquals(33f, result.velocity, 0.001f)
        assertEquals(0, result.animationFrame)
        assertEquals(100L + (13 * SfConstants.FRAME_TIME_MS).toLong(), result.animationTimerMs)
    }
}
