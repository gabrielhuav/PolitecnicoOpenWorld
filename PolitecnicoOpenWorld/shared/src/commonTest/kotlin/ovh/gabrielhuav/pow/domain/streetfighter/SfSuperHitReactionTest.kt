package ovh.gabrielhuav.pow.domain.streetfighter

import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SfSuperHitReactionTest {

    @Test
    fun `suena cuando un super conecta sin bloqueo y paso el cooldown`() {
        assertTrue(SfSuperHitReaction.deberiaSonar(esGolpeEspecial = true, bloqueado = false, ahora = 10_000L, ultimaVezMs = 0L))
    }

    @Test
    fun `no suena si el golpe fue bloqueado`() {
        assertFalse(SfSuperHitReaction.deberiaSonar(esGolpeEspecial = true, bloqueado = true, ahora = 10_000L, ultimaVezMs = 0L))
    }

    @Test
    fun `no suena si el golpe no es especial`() {
        assertFalse(SfSuperHitReaction.deberiaSonar(esGolpeEspecial = false, bloqueado = false, ahora = 10_000L, ultimaVezMs = 0L))
    }

    @Test
    fun `no suena de nuevo antes de que pase el cooldown`() {
        assertFalse(SfSuperHitReaction.deberiaSonar(esGolpeEspecial = true, bloqueado = false, ahora = 1_000L, ultimaVezMs = 0L))
    }

    @Test
    fun `suena otra vez justo al cumplirse el cooldown`() {
        assertTrue(SfSuperHitReaction.deberiaSonar(esGolpeEspecial = true, bloqueado = false, ahora = SfSuperHitReaction.COOLDOWN_MS, ultimaVezMs = 0L))
    }

    @Test
    fun `super art y fatality cuentan como golpe especial`() {
        assertTrue(SfSuperHitReaction.esEstadoEspecial(SfFighterState.SUPER_ART))
        assertTrue(SfSuperHitReaction.esEstadoEspecial(SfFighterState.FATALITY))
    }

    @Test
    fun `un golpe normal no cuenta como golpe especial`() {
        assertFalse(SfSuperHitReaction.esEstadoEspecial(SfFighterState.CROUCH))
    }

    // Regresion del bug reportado por @TheMike54 en la revision del PR #154:
    // el reloj de juego vuelve a 0 en cada pelea, pero la marca de la ultima
    // reaccion se conservaba, y la reaccion quedaba muda el resto de la pelea.
    @Test
    fun `suena en una pelea nueva aunque el reloj se haya reiniciado`() {
        assertTrue(SfSuperHitReaction.deberiaSonar(esGolpeEspecial = true, bloqueado = false, ahora = 0L, ultimaVezMs = 87_600L))
    }
}
