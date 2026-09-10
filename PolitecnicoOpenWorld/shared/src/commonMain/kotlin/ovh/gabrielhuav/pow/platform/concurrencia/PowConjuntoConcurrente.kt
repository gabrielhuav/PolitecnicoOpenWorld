package ovh.gabrielhuav.pow.platform.concurrencia

/**
 * Sustituto de `ConcurrentHashMap.newKeySet()`, que no existe en Kotlin/Native.
 *
 * Tercer hermano de [PowMapaConcurrente] y [PowListaConcurrente]: conjunto normal + **un solo
 * cerrojo**. Los nombres son los de `MutableSet` (`add`/`remove`/`contains`) para que sustituirlo
 * no toque ningún call-site.
 */
class PowConjuntoConcurrente<T> {

    private val cerrojo = PowCerrojo()
    private val conjunto = mutableSetOf<T>()

    fun add(elemento: T): Boolean = cerrojo.ejecutar { conjunto.add(elemento) }
    fun remove(elemento: T): Boolean = cerrojo.ejecutar { conjunto.remove(elemento) }
    fun contains(elemento: T): Boolean = cerrojo.ejecutar { conjunto.contains(elemento) }
    fun clear() { cerrojo.ejecutar { conjunto.clear() } }

    val tamano: Int get() = cerrojo.ejecutar { conjunto.size }

    /** Copia, como en los hermanos: recorrerla nunca choca con una escritura. */
    fun copia(): Set<T> = cerrojo.ejecutar { conjunto.toSet() }
}
