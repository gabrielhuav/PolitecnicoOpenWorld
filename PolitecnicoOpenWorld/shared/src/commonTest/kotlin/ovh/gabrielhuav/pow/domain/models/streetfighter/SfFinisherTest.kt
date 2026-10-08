package ovh.gabrielhuav.pow.domain.models.streetfighter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SfFinisherTest {

    private val tzitzi = SfFinisherCatalog.forFighter(SfFighterId.LA_TZITZIMIME)!!.command // ↓ ↓ ← + B

    private val neutral = SfInput()
    private val down = SfInput(down = true)
    private val back = SfInput(backward = true)
    private val downBack = SfInput(down = true, backward = true)

    /** Alimenta una secuencia de inputs, un tick cada [stepMs]; devuelve si ALGÚN tick completó. */
    private fun SfFinisherInputTracker.play(cmd: SfFinisherCommand, vararg inputs: SfInput, stepMs: Long = 60L): Boolean {
        var t = 1000L
        var done = false
        for (i in inputs) {
            if (feed(i, t, cmd)) done = true
            t += stepMs
        }
        return done
    }

    @Test
    fun `el comando exacto de la Tzitzimime se reconoce`() {
        val tr = SfFinisherInputTracker()
        val ok = tr.play(tzitzi, down, neutral, down, neutral, back, SfInput(backward = true, heavyPunch = true))
        assertTrue(ok, "↓ ↓ ← + B debería completar el remate")
    }

    @Test
    fun `mantener abajo cuenta como UNA sola direccion`() {
        val tr = SfFinisherInputTracker()
        val ok = tr.play(tzitzi, down, down, down, back, SfInput(heavyPunch = true))
        assertFalse(ok, "sostener ↓ no son dos toques")
    }

    @Test
    fun `la diagonal tactil entre abajo y atras no rompe el comando`() {
        val tr = SfFinisherInputTracker()
        // ↓, suelta, ↓, se desliza por ↙ hasta ←, B
        val ok = tr.play(tzitzi, down, neutral, down, downBack, back, SfInput(heavyPunch = true))
        assertTrue(ok, "la diagonal no emite token y no debería estorbar")
    }

    @Test
    fun `boton equivocado o fuera de tiempo no remata`() {
        val tr = SfFinisherInputTracker()
        assertFalse(tr.play(tzitzi, down, neutral, down, neutral, back, SfInput(lightPunch = true)), "X no es B")
        tr.reset()
        // Paso de 700 ms entre toques: el primer ↓ ya salió de la ventana de 1800 ms
        assertFalse(
            tr.play(tzitzi, down, neutral, down, neutral, back, SfInput(heavyPunch = true), stepMs = 700L),
            "demasiado lento",
        )
    }

    @Test
    fun `el hadouken detectado sigue contando como puno de su fuerza`() {
        val tr = SfFinisherInputTracker()
        val ok = tr.play(tzitzi, down, neutral, down, neutral, back, SfInput(special = SfAttackStrength.HEAVY))
        assertTrue(ok, "special HEAVY = botón B")
    }

    @Test
    fun `cualquier patada sirve para la Llorona`() {
        val llorona = SfFinisherCatalog.forFighter(SfFighterId.LA_LLORONA)!!.command // ← → ← + A
        val tr = SfFinisherInputTracker()
        val fwd = SfInput(forward = true)
        assertTrue(tr.play(llorona, back, neutral, fwd, neutral, back, SfInput(backward = true, heavyKick = true)), "patada fuerte")
        tr.reset()
        assertTrue(tr.play(llorona, back, neutral, fwd, neutral, back, SfInput(lightKick = true)), "patada ligera")
    }

    @Test
    fun `al completarse se consume y no se repite en el siguiente tick`() {
        val tr = SfFinisherInputTracker()
        val fin = SfInput(heavyPunch = true)
        assertTrue(tr.play(tzitzi, down, neutral, down, neutral, back, fin), "primera vez")
        assertFalse(tr.feed(fin, 9999L, tzitzi), "historial consumido")
    }

    @Test
    fun `subsecuencia en orden con huecos`() {
        val d = SfFinisherToken.DOWN
        val b = SfFinisherToken.BACK
        val f = SfFinisherToken.FORWARD
        assertTrue(SfFinisherInputTracker.containsInOrder(listOf(d, f, d, b), listOf(d, d, b)), "con ruido")
        assertFalse(SfFinisherInputTracker.containsInOrder(listOf(b, d, d), listOf(d, d, b)), "orden importa")
    }

    @Test
    fun `rangos de distancia`() {
        assertTrue(SfFinisherRange.CERCA.contains(60f), "60 px es cerca")
        assertFalse(SfFinisherRange.CERCA.contains(170f), "170 px no es cerca")
        assertTrue(SfFinisherRange.LEJOS.contains(176f), "la distancia inicial ya es lejos")
        assertFalse(SfFinisherRange.LEJOS.contains(90f), "90 px no es lejos")
        assertTrue(SfFinisherRange.CUALQUIERA.contains(0f), "cualquiera")
    }

    @Test
    fun `pista del HUD solo usa caracteres de la fuente arcade`() {
        for (def in SfFinisherCatalog.all) {
            for (en in listOf(false, true)) {
                val hint = def.command.hudHint(en)
                assertTrue(hint.all { it in 'A'..'Z' || it in '0'..'9' || it == ' ' }, "pista inválida: $hint")
            }
            assertTrue(def.nameEs.all { it in 'A'..'Z' || it == ' ' }, "nombre con caracteres fuera de la fuente: ${def.nameEs}")
        }
        assertEquals("ABAJO ABAJO ATRAS B CERCA", tzitzi.hudHint(english = false))
    }

    @Test
    fun `catalogo solo para los personajes con remate`() {
        assertNotNull(SfFinisherCatalog.forFighter(SfFighterId.LA_TZITZIMIME))
        assertNotNull(SfFinisherCatalog.forFighter(SfFighterId.LA_LLORONA))
        assertNotNull(SfFinisherCatalog.forFighter(SfFighterId.CHARRO_NEGRO))
        assertNull(SfFinisherCatalog.forFighter(SfFighterId.PRANKEDY), "sin remate → KO clásico")
    }

    @Test
    fun `cada cinematica termina con la victima desaparecida y el nombre en pantalla`() {
        for (def in SfFinisherCatalog.all) {
            val start = SfFinisherVisuals.cinematic(def, 1, 0L, "MOVIMIENTO FINAL", def.nameEs)
            val end = SfFinisherVisuals.cinematic(def, 1, def.durationMs, "MOVIMIENTO FINAL", def.nameEs)
            assertEquals(1f, start.victimAlpha, "${def.fighter}: al inicio se ve")
            assertEquals("", start.headline, "${def.fighter}: el nombre no se adelanta")
            assertEquals(0f, end.victimAlpha, "${def.fighter}: al final ya no está")
            assertEquals(def.nameEs, end.subline, "${def.fighter}: nombre al final")
            assertTrue(end.darkness in 0f..1f, "${def.fighter}: oscuridad válida")
        }
    }

    @Test
    fun `la Tzitzimime levanta a la victima y la encoge hasta cero`() {
        val def = SfFinisherCatalog.forFighter(SfFighterId.LA_TZITZIMIME)!!
        val mid = SfFinisherVisuals.cinematic(def, 1, 3000L, "", "")
        assertEquals(48f, mid.victimLiftPx, "levita 48 px")
        val end = SfFinisherVisuals.cinematic(def, 1, 4300L, "", "")
        assertEquals(0f, end.victimScale, "devorada por las estrellas")
    }

    @Test
    fun `la CPU mas dificil siempre remata`() {
        assertEquals(1f, SfFinisherCpu.chance(SfCpuDifficulty.PESADILLA))
        assertTrue(SfFinisherCpu.chance(SfCpuDifficulty.BASICA) < SfFinisherCpu.chance(SfCpuDifficulty.NORMAL))
    }

    // ── 🆕 EXAMEN EXTRAORDINARIO ──

    @Test
    fun `las flechas del panel se voltean segun el lado`() {
        // Tzitzimime: ABAJO ABAJO ATRAS + B
        assertEquals(listOf("↓", "↓", "←", "B"), tzitzi.physicalSteps(facingRight = true), "mirando a la derecha")
        assertEquals(listOf("↓", "↓", "→", "B"), tzitzi.physicalSteps(facingRight = false), "mirando a la izquierda")
        val charro = SfFinisherCatalog.forFighter(SfFighterId.CHARRO_NEGRO)!!.command // → ↓ → + Y
        assertEquals(listOf("←", "↓", "←", "Y"), charro.physicalSteps(facingRight = false), "adelante = hacia el rival")
    }

    @Test
    fun `la jerga de CERCA queda pegada al rival y del lado del atacante`() {
        val z = SfFinisherRange.CERCA.zone(victimX = 300f, attackerOnLeft = true, stageMin = 50f, stageMax = 700f)
        assertEquals(300f - 105f, z.minX, "borde lejano = 105 px")
        assertEquals(300f - SfFinisherPractice.MIN_GAP_PX, z.maxX, "no se mete debajo del rival")
        val zr = SfFinisherRange.CERCA.zone(victimX = 300f, attackerOnLeft = false, stageMin = 50f, stageMax = 700f)
        assertTrue(zr.minX > 300f && zr.maxX > zr.minX, "del lado derecho también")
    }

    @Test
    fun `la jerga de LEJOS se recorta al escenario`() {
        val z = SfFinisherRange.LEJOS.zone(victimX = 300f, attackerOnLeft = true, stageMin = 50f, stageMax = 700f)
        assertEquals(50f, z.minX, "hasta la pared")
        assertEquals(300f - 145f, z.maxX, "empieza a 145 px")
        // Rival pegado a la pared izquierda: no hay espacio de ese lado → zona vacía (ancho 0)
        val pegado = SfFinisherRange.LEJOS.zone(victimX = 100f, attackerOnLeft = true, stageMin = 50f, stageMax = 700f)
        assertEquals(0f, pegado.width, "sin espacio no se tiende jerga")
    }

    @Test
    fun `el progreso palomea las direcciones en orden`() {
        val tr = SfFinisherInputTracker()
        assertEquals(0, tr.progress(tzitzi, 1000L), "nada todavía")
        tr.feed(down, 1000L, tzitzi)
        assertEquals(1, tr.progress(tzitzi, 1000L), "primer ↓")
        tr.feed(neutral, 1060L, tzitzi)
        tr.feed(back, 1120L, tzitzi) // ← antes del segundo ↓: no avanza
        assertEquals(1, tr.progress(tzitzi, 1120L), "orden incorrecto")
        tr.feed(neutral, 1180L, tzitzi)
        tr.feed(down, 1240L, tzitzi)
        tr.feed(neutral, 1300L, tzitzi)
        tr.feed(back, 1360L, tzitzi)
        assertEquals(3, tr.progress(tzitzi, 1360L), "direcciones completas, falta el botón")
        assertEquals(0, tr.progress(tzitzi, 9999L), "fuera de la ventana se pierde")
    }

    @Test
    fun `ningun Extraordinario usa TALK`() {
        // Los cuadros de TALK traen el origen en y=128 en lugar de y=224 (en los 18 packs):
        // el peleador se dibuja hundido 96 px en el piso. Hallazgo H-1 del QA.
        for (def in SfFinisherCatalog.all) {
            for (beat in def.beats) {
                assertTrue(beat.attackerState != SfFighterState.TALK, "${def.fighter}: TALK en el guion")
                assertTrue(beat.victimState != SfFighterState.TALK, "${def.fighter}: TALK en la víctima")
            }
        }
    }
}
