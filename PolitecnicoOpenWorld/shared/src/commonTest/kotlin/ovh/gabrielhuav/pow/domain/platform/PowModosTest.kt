package ovh.gabrielhuav.pow.domain.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 🍏 QUÉ MODOS SE OFRECEN EN CADA PLATAFORMA.
 *
 * Lo valioso de estos tests: **comprueban la regla de iOS corriendo en Android**. Como `modosDe()`
 * es lógica pura y solo la DETECCIÓN de plataforma es `expect/actual`, no hace falta un Mac para
 * verificar que en iOS el menú esconde el mundo abierto, la campaña y el multijugador.
 *
 * ⚠️ Si algún día añades un modo a la lista de iOS, este archivo se pone rojo. Eso es
 * intencionado: obliga a que alguien confirme que ese modo FUNCIONA de verdad en el simulador,
 * no solo que el botón aparece.
 */
class PowModosTest {

    @Test
    fun `en Android estan TODOS los modos`() {
        assertEquals(PowModo.entries.toSet(), modosDe(PowPlataforma.ANDROID))
    }

    @Test
    fun `en iOS SOLO ajustes - coleccionables - y el modo pelea`() {
        assertEquals(
            setOf(PowModo.AJUSTES, PowModo.COLECCIONABLES, PowModo.STREET_FIGHTER),
            modosDe(PowPlataforma.IOS),
        )
    }

    @Test
    fun `en iOS NO se ofrece lo que aun no funciona alli`() {
        val ios = modosDe(PowPlataforma.IOS)
        // Mundo abierto y campaña: necesitan el mapa con datos inyectados (hoy el handler de
        // assets de iOS devuelve 404). Multijugador: Bluetooth RFCOMM no existe en iOS.
        assertFalse(PowModo.MUNDO_LIBRE in ios, "el mundo abierto aun no corre en iOS")
        assertFalse(PowModo.MODO_HISTORIA in ios, "la campana vive sobre el mundo abierto")
        assertFalse(PowModo.MULTIJUGADOR in ios, "el multijugador del mundo abierto no esta portado")
    }

    @Test
    fun `el modo pelea esta en LAS DOS plataformas`() {
        // Es el objetivo de esta fase: que Titulación por Combate sea el primero en cruzar.
        for (p in PowPlataforma.entries) {
            assertTrue(PowModo.STREET_FIGHTER in modosDe(p), "falta en $p")
        }
    }

    @Test
    fun `iOS es un SUBCONJUNTO de Android y nunca al reves`() {
        // Regla de sanidad: iOS no puede ofrecer algo que Android no tenga.
        assertTrue(modosDe(PowPlataforma.IOS).all { it in modosDe(PowPlataforma.ANDROID) })
    }

    // ── 🚧 Modos EN OBRAS: se pintan pero no se juegan ─────────────────────────────────────────

    @Test
    fun `un modo en obras NO cuenta como disponible`() {
        // Esta es LA regla que protege todo lo demás. `disponible()` responde "¿se puede jugar?",
        // y eso es lo que consultan Ajustes y la navegación. Si un modo en obras contase como
        // disponible, iOS enseñaria las opciones de mapa y controles, que allí no hacen nada.
        val enObras = modosEnObrasDe(PowPlataforma.IOS)
        val jugables = modosDe(PowPlataforma.IOS)
        for (m in enObras) {
            assertFalse(m in jugables, "$m esta en obras y NO puede estar en modosDe(IOS)")
        }
    }

    @Test
    fun `en Android no hay nada en obras - lo que se pinta se juega`() {
        assertEquals(emptySet(), modosEnObrasDe(PowPlataforma.ANDROID))
    }

    @Test
    fun `el interruptor de la App Store apaga TODAS las obras`() {
        // Si esto se pone rojo, alguien rompió el interruptor: es lo único que hay que tocar antes
        // de firmar para iOS, y tiene que dejar el menú exactamente como estaba.
        if (MODOS_EN_OBRAS_VISIBLES) {
            assertTrue(
                modosEnObrasDe(PowPlataforma.IOS).isNotEmpty(),
                "con el interruptor encendido iOS deberia enseñar los modos en obras",
            )
        } else {
            assertEquals(
                emptySet(),
                modosEnObrasDe(PowPlataforma.IOS),
                "apagado, iOS no puede pintar NINGUN modo en obras",
            )
        }
    }

    @Test
    fun `con las obras encendidas los dos menus pintan lo mismo`() {
        // El objetivo de la fase 1: menús idénticos. Lo que se PINTA en iOS = lo que se pinta en
        // Android; lo que cambia es que en iOS tres de ellos avisan en vez de navegar.
        if (!MODOS_EN_OBRAS_VISIBLES) return
        val pintadosIos = modosDe(PowPlataforma.IOS) + modosEnObrasDe(PowPlataforma.IOS)
        assertEquals(modosDe(PowPlataforma.ANDROID), pintadosIos)
    }

    @Test
    fun `sePinta concuerda con disponible mas enObras`() {
        for (m in PowModo.entries) {
            assertEquals(m.disponible() || m.enObras(), m.sePinta(), "desacuerdo en $m")
        }
    }

    @Test
    fun `ningun modo esta a la vez jugable y en obras`() {
        for (m in PowModo.entries) {
            assertFalse(m.disponible() && m.enObras(), "$m no puede ser las dos cosas")
        }
    }

    @Test
    fun `la plataforma actual se resuelve y es coherente`() {
        // No se afirma CUÁL es (depende de dónde corra el test), sino que resuelve y que el
        // atajo `disponible()` concuerda con el catálogo.
        val modos = modosDe(plataformaActual)
        for (m in PowModo.entries) {
            assertEquals(m in modos, m.disponible(), "desacuerdo en $m para $plataformaActual")
        }
    }
}
