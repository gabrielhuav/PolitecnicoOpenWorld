package ovh.gabrielhuav.pow.domain.streetfighter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 🔊 LAS REGLAS DE INTERRUPCIÓN DE VOCES.
 *
 * Estas reglas se afinaron **de oído** y hasta ahora **no las cubría ningún test**: si alguien las
 * cambiaba, el juego sonaba raro y no se ponía rojo nada. Este archivo cierra ese agujero.
 *
 * Cada test lleva el porqué de la regla, porque son decisiones de diseño de sonido, no
 * implementación: quien las lea dentro de seis meses tiene que poder distinguir "esto es a
 * propósito" de "esto es un bug".
 */
class SfVocesReglasTest {

    private val VOZ = "STREETFIGHTER/SOUNDS/"

    // ═══════════════════════════ regla 1 · el hurt no lo corta un ataque ═══════════════════════

    @Test
    fun `un HURT en curso NO lo corta un ATAQUE del mismo peleador`() {
        // 07-19b: el contraataque al recuperarse cortaba su propio quejido y el hurt no se oía.
        val d = SfVocesReglas.decidir("${VOZ}goya_attack_1.ogg", setOf("${VOZ}goya_hurt_1.ogg"))
        assertFalse(d.saltar)
        assertEquals(emptySet(), d.aDetener, "el ataque no debe callar el quejido")
    }

    @Test
    fun `otro HURT si reinicia el hurt en curso`() {
        // Te han vuelto a pegar: el quejido nuevo manda.
        val d = SfVocesReglas.decidir("${VOZ}goya_hurt_2.ogg", setOf("${VOZ}goya_hurt_1.ogg"))
        assertEquals(setOf("${VOZ}goya_hurt_1.ogg"), d.aDetener)
    }

    @Test
    fun `el hurt de OTRO peleador no se toca`() {
        // Solo se calla al mismo peleador; si no, pegar a uno silenciaría al otro.
        val d = SfVocesReglas.decidir("${VOZ}goya_hurt_1.ogg", setOf("${VOZ}huelum_hurt_1.ogg"))
        assertEquals(emptySet(), d.aDetener)
    }

    // ═══════════════════════════ regla 2 · el especial no se detiene ═══════════════════════════

    @Test
    fun `un ataque ESPECIAL en curso no lo detiene nada`() {
        // 07-19c.
        val d = SfVocesReglas.decidir("${VOZ}goya_attack_1.ogg", setOf("${VOZ}goya_power_1.ogg"))
        assertEquals(emptySet(), d.aDetener)
    }

    @Test
    fun `la voz de VICTORIA tambien esta protegida`() {
        // 07-19: `win` se excluyó de los interrumpibles para que los gritos y los golpes no la
        // cortaran. Cuenta como "especial" aunque no lleve la palabra power.
        assertTrue(SfVocesReglas.esAudioDePoderEspecial("goya_win_1.ogg"))
        val d = SfVocesReglas.decidir("${VOZ}goya_attack_1.ogg", setOf("${VOZ}goya_win_1.ogg"))
        assertEquals(emptySet(), d.aDetener)
    }

    // ═════════════════════════════════ regla 3 · la intro manda ════════════════════════════════

    @Test
    fun `mientras suena la INTRO las demas voces de ese peleador se SALTAN`() {
        // 07-22: la intro dura ~15 s y tiene que oírse entera, sin que se le encime el grito.
        val d = SfVocesReglas.decidir("${VOZ}goya_attack_1.ogg", setOf("${VOZ}goya_intro_1.ogg"))
        assertTrue(d.saltar, "la voz nueva debe saltarse, no encimarse")
        assertEquals(emptySet(), d.aDetener, "y sobre todo NO debe cortar la intro")
    }

    @Test
    fun `la MISMA intro re-disparada si se reinicia - ronda nueva`() {
        // Este test empezó afirmando otra cosa y FALLÓ, lo cual resultó útil. El comentario del
        // código original dice "otro clip de intro sí la reemplaza", y suena a que una intro
        // DISTINTA cortaría a la que suena. **No es lo que hace el código**: el filtro excluye
        // todo lo que contenga "_intro", así que dos intros distintas del mismo peleador se
        // ENCIMARÍAN. Lo que realmente reemplaza es el re-disparo del MISMO archivo (regla 4).
        //
        // Y no es un bug latente: hoy existe **un solo archivo de intro** en todo el juego
        // (`special_pol_h_intro.ogg`), así que el caso de dos intros distintas no puede darse. Se
        // deja fijado aquí para que quien añada una segunda intro vea que tiene que decidirlo.
        val intro = "${VOZ}special_pol_h_intro.ogg"
        val d = SfVocesReglas.decidir(intro, setOf(intro))
        assertFalse(d.saltar)
        assertEquals(setOf(intro), d.aDetener)
    }

    @Test
    fun `dos intros DISTINTAS del mismo peleador se encimarian - documentado a proposito`() {
        // Lo contrario de lo que sugiere el comentario original. Si algún día se añade una segunda
        // intro y esto molesta, hay que quitar la exclusión `!nombre.contains("_intro")` para el
        // caso en que la voz nueva TAMBIÉN sea intro. Mientras tanto, queda registrado.
        val d = SfVocesReglas.decidir("${VOZ}goya_intro_2.ogg", setOf("${VOZ}goya_intro_1.ogg"))
        assertFalse(d.saltar)
        assertEquals(emptySet(), d.aDetener, "hoy NO se corta; ver el comentario")
    }

    @Test
    fun `la intro de un peleador NO silencia al otro`() {
        // Solo se salta la voz del peleador cuya intro suena.
        val d = SfVocesReglas.decidir("${VOZ}huelum_attack_1.ogg", setOf("${VOZ}goya_intro_1.ogg"))
        assertFalse(d.saltar)
    }

    // ═══════════════════════════════ regla 4 · el re-disparo ═══════════════════════════════════

    @Test
    fun `el MISMO clip re-disparado se corta y se relanza`() {
        // 07-18r: antes se ignoraba mientras sonaba y no se repetía al volver a atacar.
        val ruta = "${VOZ}goya_attack_1.ogg"
        val d = SfVocesReglas.decidir(ruta, setOf(ruta))
        assertFalse(d.saltar)
        assertEquals(setOf(ruta), d.aDetener)
    }

    @Test
    fun `el re-disparo gana INCLUSO sobre la proteccion del especial`() {
        // Sutil y fácil de romper al portar: la regla 2 protege al especial de OTRAS voces, pero
        // re-lanzar ESE MISMO especial sí lo reinicia. En el código original eran dos bloques
        // separados y el segundo no miraba si el primero lo había excluido.
        val ruta = "${VOZ}goya_power_1.ogg"
        val d = SfVocesReglas.decidir(ruta, setOf(ruta))
        assertEquals(setOf(ruta), d.aDetener)
    }

    // ══════════════════════════════ los dos helpers de nombres ═════════════════════════════════

    @Test
    fun `el prefijo del peleador ignora numero y accion`() {
        for (clave in listOf("goya_hurt_1.ogg", "goya_attack_2.ogg", "goya_intro.ogg", "goya_win_3.ogg")) {
            assertEquals("goya", SfVocesReglas.prefijoDePeleador(clave), "fallo con $clave")
        }
    }

    @Test
    fun `los digitos se quitan ANTES que el sufijo`() {
        // Si se hiciera al revés, `goya_hurt_2` no terminaría en "hurt" y el prefijo saldría mal
        // -> el peleador se partiría en dos identidades y las interrupciones dejarían de aplicarse.
        assertEquals("goya", SfVocesReglas.prefijoDePeleador("goya_hurt_2.ogg"))
    }

    @Test
    fun `solo se recorta UN sufijo de accion`() {
        // Hay un `break` a propósito en el original.
        assertEquals("goya_win", SfVocesReglas.prefijoDePeleador("goya_win_hurt.ogg"))
    }

    @Test
    fun `hurt attack e intro son interrumpibles y power no`() {
        assertFalse(SfVocesReglas.esAudioDePoderEspecial("goya_hurt_1.ogg"))
        assertFalse(SfVocesReglas.esAudioDePoderEspecial("goya_attack_1.ogg"))
        assertFalse(SfVocesReglas.esAudioDePoderEspecial("goya_intro.ogg"))
        assertTrue(SfVocesReglas.esAudioDePoderEspecial("goya_power_1.ogg"))
        assertTrue(SfVocesReglas.esAudioDePoderEspecial("goya_electricity.ogg"))
    }
}
