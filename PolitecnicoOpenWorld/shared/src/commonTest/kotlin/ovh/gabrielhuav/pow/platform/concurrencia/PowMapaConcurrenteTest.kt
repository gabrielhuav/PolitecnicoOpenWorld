package ovh.gabrielhuav.pow.platform.concurrencia

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 🔐 Los tests que faltaban ANTES de tocar los gestores del mundo abierto.
 *
 * `PowMapaConcurrente` sustituye a `ConcurrentHashMap` en `PoliceManager` y compañía, que se
 * llaman desde tres dispatchers a la vez. Si esta clase se equivoca, el fallo NO sale al compilar
 * ni en un test de lógica: sale como un tirón o un crash a media partida.
 *
 * ⚠️ **Aquí se prueba la SEMÁNTICA** (atomicidad, copias, reentrancia), porque `commonTest` solo
 * tiene `kotlin.test` y no merece la pena meter una dependencia nueva —recuérdese lo que costó la
 * ABI de las klibs de Ktor—. **Las carreras con hilos de verdad se prueban en
 * `:app` → `PowMapaConcurrenteHilosTest`**, donde sí hay JVM.
 */
class PowMapaConcurrenteTest {

    // ── Lo básico: que se comporte como un mapa ────────────────────────────────────────────────

    @Test
    fun `guarda y devuelve`() {
        val m = PowMapaConcurrente<String, Int>()
        m["a"] = 1
        assertEquals(1, m["a"])
        assertNull(m["b"])
    }

    @Test
    fun `poner devuelve el valor anterior como Map punto put`() {
        val m = PowMapaConcurrente<String, Int>()
        assertNull(m.poner("a", 1))
        assertEquals(1, m.poner("a", 2))
        assertEquals(2, m["a"])
    }

    @Test
    fun `quitar devuelve lo que habia y deja de estar`() {
        val m = PowMapaConcurrente<String, Int>()
        m["a"] = 1
        assertEquals(1, m.quitar("a"))
        assertNull(m.quitar("a"))
        assertFalse(m.contiene("a"))
    }

    @Test
    fun `tamano y vacio`() {
        val m = PowMapaConcurrente<String, Int>()
        assertTrue(m.estaVacio())
        m["a"] = 1
        m["b"] = 2
        assertEquals(2, m.tamano)
        m.limpiar()
        assertTrue(m.estaVacio())
    }

    // ── Lo que de verdad importa: atomicidad ───────────────────────────────────────────────────

    @Test
    fun `obtenerOPoner calcula UNA sola vez`() {
        val m = PowMapaConcurrente<String, Int>()
        var veces = 0
        repeat(5) { m.obtenerOPoner("a") { veces++; 42 } }
        assertEquals(42, m["a"])
        assertEquals(1, veces, "el valor por defecto se calculo de mas")
    }

    @Test
    fun `calcular ve el valor anterior`() {
        val m = PowMapaConcurrente<String, Int>()
        m["a"] = 1
        assertEquals(2, m.calcular("a") { (it ?: 0) + 1 })
        assertEquals(2, m["a"])
    }

    @Test
    fun `calcular devolviendo null BORRA la entrada`() {
        val m = PowMapaConcurrente<String, Int>()
        m["a"] = 1
        assertNull(m.calcular("a") { null })
        assertFalse(m.contiene("a"), "devolver null tiene que borrar, como Map.compute")
    }

    @Test
    fun `quitarSi borra solo lo que cumple y dice cuales`() {
        val m = PowMapaConcurrente<String, Int>()
        m["a"] = 1; m["b"] = 2; m["c"] = 3
        val fuera = m.quitarSi { _, v -> v % 2 == 1 }
        assertEquals(setOf("a", "c"), fuera.toSet())
        assertEquals(listOf(2), m.valores)
    }

    // ── Copias instantaneas: la diferencia con ConcurrentHashMap ───────────────────────────────

    @Test
    fun `valores es una COPIA - escribir despues no la cambia`() {
        val m = PowMapaConcurrente<String, Int>()
        m["a"] = 1
        val copia = m.valores
        m["b"] = 2
        assertEquals(listOf(1), copia, "la copia no puede seguir viva")
        assertEquals(2, m.valores.size)
    }

    @Test
    fun `el cerrojo es REENTRANTE - se puede tocar el mapa dentro de la lambda`() {
        // PoliceManager hace cosas asi al limpiar rutas de un coche que acaba de morir.
        // Con un cerrojo no reentrante esto se quedaria colgado para siempre.
        val m = PowMapaConcurrente<String, Int>()
        m["a"] = 1
        val r = m.obtenerOPoner("b") { (m["a"] ?: 0) + 10 }
        assertEquals(11, r)
    }
}
