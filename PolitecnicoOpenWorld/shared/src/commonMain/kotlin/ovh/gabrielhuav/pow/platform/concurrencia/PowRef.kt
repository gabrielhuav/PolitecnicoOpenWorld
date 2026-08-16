package ovh.gabrielhuav.pow.platform.concurrencia

/**
 * Sustituto de `java.util.concurrent.atomic.AtomicReference`, que no existe en Kotlin/Native.
 *
 * Hermano de [PowMapaConcurrente]: **un solo cerrojo**, misma filosofía. Los métodos se llaman
 * `get`/`set` a propósito, para que sustituirlo sea cambiar el tipo y **ni un call-site**.
 *
 * Para lo que se usa en POW —cachés de solo-lectura que se reemplazan enteras de vez en cuando
 * (la red de calles, los landmarks, el índice nodo→vías)— esto es exactamente igual de correcto
 * que el atómico: la referencia se publica de golpe bajo cerrojo y quien lee ve la vieja o la
 * nueva, nunca una a medias.
 *
 * ⚠️ **Lo que NO da: composición atómica.** `ref.set(ref.get() + x)` son dos operaciones con una
 * ventana en medio, igual que con `AtomicReference` sin `compareAndSet`. Si algún día hace falta
 * de verdad, añade aquí un `actualizar { viejo -> nuevo }` que haga el ciclo DENTRO del cerrojo,
 * en vez de resolverlo en el call-site.
 */
class PowRef<T>(inicial: T) {

    private val cerrojo = PowCerrojo()
    private var valor: T = inicial

    fun get(): T = cerrojo.ejecutar { valor }

    fun set(nuevo: T) {
        cerrojo.ejecutar { valor = nuevo }
    }

    /** Lee, transforma y publica **sin soltar el cerrojo**. Devuelve el valor nuevo. */
    fun actualizar(transformar: (T) -> T): T = cerrojo.ejecutar {
        valor = transformar(valor)
        valor
    }
}
