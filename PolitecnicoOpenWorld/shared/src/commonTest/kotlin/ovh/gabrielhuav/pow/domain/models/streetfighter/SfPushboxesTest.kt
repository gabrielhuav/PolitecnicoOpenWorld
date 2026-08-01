package ovh.gabrielhuav.pow.domain.models.streetfighter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 🥊 EMPUJE DE PUSHBOXES — Fase 2c del refactor del motor compartido.
 *
 * POR QUÉ ESTE TEST EXISTE: hasta ahora el empuje vivía dentro del `StreetFighterViewModel`, que
 * es un `ViewModel` de Android con `Context`. Eso significaba **cero tests** (haría falta un
 * dispositivo) y **cero iOS**. Al mover el núcleo a `SfPhysics`, la misma lógica corre en las dos
 * plataformas y se puede fijar aquí.
 *
 * ⚠️ Estos tests corren en Android **y en el simulador de iOS** (`commonTest`). Si alguien devuelve
 * la lógica al ViewModel "para verla junta", este archivo deja de proteger nada.
 */
class SfPushboxesTest {

    private val piso = SfConstants.STAGE_FLOOR
    private val medio = SfConstants.STAGE_MID_POINT

    private fun peleador(
        x: Float,
        estado: SfFighterState = SfFighterState.IDLE,
        dir: SfDirection = SfDirection.RIGHT,
    ) = SfFighter(
        id = SfFighterId.entries.first(), playerIndex = 0,
        x = x, y = piso, direction = dir, state = estado,
    )

    /** Pushbox típica: centrada en el peleador, 30 de ancho. */
    private val caja = SfBox(-15f, -60f, 30f, 60f)

    @Test
    fun `sin solape no mueve al rival`() {
        val yo = peleador(medio - 100f)
        val rival = peleador(medio + 100f)
        val r = SfPhysics.resolvePushboxes(yo, rival, caja, caja, camX = 0f, dt = 0.016f, overlapping = false)
        assertEquals(rival, r.opponent, "sin solape el rival NO se toca")
    }

    @Test
    fun `con solape se separan y el rival cede`() {
        val yo = peleador(medio - 5f)
        val rival = peleador(medio + 5f)
        val r = SfPhysics.resolvePushboxes(yo, rival, caja, caja, camX = 0f, dt = 0.016f, overlapping = true)
        assertTrue(r.self.x < yo.x || r.opponent.x > rival.x, "alguno de los dos tuvo que moverse")
        assertTrue(r.opponent.x >= rival.x, "el rival de la derecha se aparta hacia la derecha")
    }

    @Test
    fun `un rival en estado NO empujable no se mueve`() {
        // Golpeando, en KO o aturdido no se le empuja: solo los estados de PUSHABLE_STATES ceden.
        val yo = peleador(medio - 5f)
        val rival = peleador(medio + 5f, estado = SfFighterState.KO)
        val r = SfPhysics.resolvePushboxes(yo, rival, caja, caja, camX = 0f, dt = 0.016f, overlapping = true)
        assertEquals(rival.x, r.opponent.x, "un KO no se empuja")
    }

    @Test
    fun `caminar SI es empujable - si no se congelan al chocar`() {
        // Regresión del bug documentado: si WALK no fuese empujable, dos peleadores que chocan
        // caminando se quedarían trabados empujándose sin resolver el solape.
        assertTrue(SfFighterState.WALK_FORWARD in SfPhysics.PUSHABLE_STATES)
        assertTrue(SfFighterState.WALK_BACKWARD in SfPhysics.PUSHABLE_STATES)
    }

    @Test
    fun `nadie sale NUNCA del escenario pase lo que pase`() {
        // Empujes degenerados: posiciones absurdas deben quedar dentro de los límites.
        val yo = peleador(SfConstants.STAGE_X_MAX + 5000f)
        val rival = peleador(SfConstants.STAGE_X_MIN - 5000f)
        val r = SfPhysics.resolvePushboxes(yo, rival, caja, caja, camX = 0f, dt = 0.016f, overlapping = true)
        for (f in listOf(r.self, r.opponent)) {
            assertTrue(
                f.x >= SfConstants.STAGE_X_MIN && f.x <= SfConstants.STAGE_X_MAX,
                "x=${f.x} se salió del escenario",
            )
        }
    }

    @Test
    fun `el viewport no deja al peleador fuera de camara`() {
        val camX = 500f
        val margen = SfConstants.FIGHTER_DEFAULT_WIDTH
        // Muy a la derecha del viewport
        val derecha = SfPhysics.clampToViewport(peleador(camX + SfConstants.SCENE_WIDTH + 999f), camX)
        assertTrue(derecha.x - camX + margen <= SfConstants.SCENE_WIDTH + 0.01f)
        // Muy a la izquierda
        val izquierda = SfPhysics.clampToViewport(peleador(camX - 999f), camX)
        assertTrue(izquierda.x - camX - margen >= -0.01f || izquierda.x == SfConstants.STAGE_X_MIN)
    }

    @Test
    fun `coordenadas NaN no rompen el empuje`() {
        // clampToStage rescata NaN/infinito; se comprueba que el empuje no lo deshace.
        val yo = peleador(Float.NaN)
        val rival = peleador(medio)
        val r = SfPhysics.resolvePushboxes(yo, rival, caja, caja, camX = 0f, dt = 0.016f, overlapping = true)
        assertTrue(!r.self.x.isNaN(), "el NaN debe quedar rescatado")
        assertTrue(!r.opponent.x.isNaN())
    }
}
