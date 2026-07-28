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
            setOf(PowModo.AJUSTES, PowModo.COLECCIONABLES, PowModo.HUELUM_VS_GOYA),
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
        // Es el objetivo de esta fase: que Huelum vs. Goya sea el primero en cruzar.
        for (p in PowPlataforma.entries) {
            assertTrue(PowModo.HUELUM_VS_GOYA in modosDe(p), "falta en $p")
        }
    }

    @Test
    fun `iOS es un SUBCONJUNTO de Android y nunca al reves`() {
        // Regla de sanidad: iOS no puede ofrecer algo que Android no tenga.
        assertTrue(modosDe(PowPlataforma.IOS).all { it in modosDe(PowPlataforma.ANDROID) })
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
