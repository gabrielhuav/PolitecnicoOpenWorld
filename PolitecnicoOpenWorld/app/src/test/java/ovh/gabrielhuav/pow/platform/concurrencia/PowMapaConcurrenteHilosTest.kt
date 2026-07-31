package ovh.gabrielhuav.pow.platform.concurrencia

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 🔐 `PowMapaConcurrente` bajo HILOS DE VERDAD.
 *
 * ## Por qué este test vive en `:app` y no en `commonTest`
 *
 * `commonTest` solo tiene `kotlin.test`: no hay hilos ni `runBlocking`. Meter
 * `kotlinx-coroutines-test` solo para esto añadiría una dependencia multiplataforma más, y la ABI
 * de las klibs ya nos costó una tarde con Ktor. Aquí hay JVM, así que aquí van las carreras.
 *
 * La semántica (atomicidad, copias, reentrancia) se prueba en las DOS plataformas, en
 * `shared/commonTest` → `PowMapaConcurrenteTest`.
 *
 * ## Qué se está protegiendo
 *
 * MEDIDO el 2026-07-30: a `PoliceManager` se le llama desde `Dispatchers.Default` (bucle de
 * juego), `Dispatchers.IO` (red) y `Dispatchers.Main` (toques del jugador). Si esta clase pierde
 * escrituras, el síntoma en el juego es una patrulla que desaparece o un policía que se queda
 * clavado — nunca una excepción que alguien vea en el log.
 */
class PowMapaConcurrenteHilosTest {

    private val HILOS = 8
    private val VUELTAS = 500

    /** Lanza [HILOS] hilos a la vez —de verdad a la vez— y espera a que terminen. */
    private fun enParalelo(bloque: (Int) -> Unit) {
        val pool = Executors.newFixedThreadPool(HILOS)
        // La barrera es lo que hace que el test sirva: sin ella los hilos arrancan escalonados
        // y casi nunca se pisan, así que una carrera real pasaría desapercibida.
        val salida = CountDownLatch(1)
        val fin = CountDownLatch(HILOS)
        repeat(HILOS) { h ->
            pool.execute {
                salida.await()
                try { bloque(h) } finally { fin.countDown() }
            }
        }
        salida.countDown()
        assertEquals(true, fin.await(30, TimeUnit.SECONDS))
        pool.shutdown()
    }

    @Test
    fun `escrituras en paralelo - no se pierde ninguna`() {
        val m = PowMapaConcurrente<Int, Int>()
        enParalelo { h -> repeat(VUELTAS) { i -> m[h * VUELTAS + i] = i } }
        assertEquals(HILOS * VUELTAS, m.tamano)
    }

    @Test
    fun `incrementar la MISMA clave en paralelo no pierde cuentas`() {
        // ESTE es el test que falla si `calcular` no fuese atómico: leer-sumar-escribir desde
        // ocho hilos se pisa y el total sale corto.
        val m = PowMapaConcurrente<String, Int>()
        m["n"] = 0
        enParalelo { repeat(VUELTAS) { m.calcular("n") { v -> (v ?: 0) + 1 } } }
        assertEquals(HILOS * VUELTAS, m["n"])
    }

    @Test
    fun `obtenerOPoner en paralelo calcula el valor UNA sola vez`() {
        val m = PowMapaConcurrente<String, Int>()
        val veces = java.util.concurrent.atomic.AtomicInteger(0)
        enParalelo { repeat(VUELTAS) { m.obtenerOPoner("k") { veces.incrementAndGet(); 7 } } }
        assertEquals(7, m["k"])
        assertEquals("el valor por defecto se calculó más de una vez", 1, veces.get())
    }

    @Test
    fun `leer mientras se escribe NO lanza ConcurrentModificationException`() {
        // Es la razón de que `valores` devuelva una copia. Con un mapa normal esto revienta.
        val m = PowMapaConcurrente<Int, Int>()
        repeat(200) { m[it] = it }
        enParalelo { h ->
            if (h % 2 == 0) repeat(VUELTAS) { m[1000 + h * VUELTAS + it] = it }
            else repeat(VUELTAS) { m.valores.size; m.claves.size; m.entradas.size }
        }
        // Si llegó hasta aquí sin excepción, el contrato se cumple.
        assertEquals(200 + (HILOS / 2) * VUELTAS, m.tamano)
    }

    @Test
    fun `quitar y poner en paralelo deja el mapa coherente`() {
        val m = PowMapaConcurrente<Int, Int>()
        enParalelo { h ->
            repeat(VUELTAS) { i ->
                val k = (h * VUELTAS + i) % 100
                if (i % 2 == 0) m[k] = i else m.quitar(k)
            }
        }
        // No se afirma CUÁNTAS quedan (depende del entrelazado), sino que el mapa sigue sano:
        // el tamaño concuerda con lo que devuelven las copias.
        assertEquals(m.tamano, m.claves.size)
        assertEquals(m.tamano, m.valores.size)
        assertEquals(m.tamano, m.copia().size)
    }
}
