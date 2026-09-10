package ovh.gabrielhuav.pow.platform.concurrencia

/**
 * Sustituto de `java.util.concurrent.CopyOnWriteArrayList`, que no existe en Kotlin/Native.
 *
 * Es la lista hermana de [PowMapaConcurrente]: una lista normal + **un solo cerrojo**. Implementa
 * `MutableList` para que migrar sea cambiar el tipo y **ningún call-site** — `NpcAiManager` la usa
 * en ~60 sitios con toda la API (`indices`, `lista[i] = x`, `addAll`, `removeAll`, `filter`…).
 *
 * ⚠️ **`iterator()` recorre una COPIA**, igual que `CopyOnWriteArrayList`. Es lo que hace que un
 * `for (n in serverNpcs)` no reviente cuando el game loop añade un NPC desde otro hilo. El precio
 * es el mismo que allí: lo que recorres es la foto del momento en que empezaste, no la lista viva.
 * `MutableIterator.remove()` **lanza**, otra vez como en `CopyOnWriteArrayList`: quitar sobre la
 * copia no tocaría la lista real, y fallar en voz alta es mejor que un borrado que no ocurre.
 *
 * ⚠️ **Cada operación es atómica; una SECUENCIA no lo es.** `lista[i] = lista[i].copy(...)` tiene
 * una ventana entre la lectura y la escritura — exactamente igual que antes con
 * `CopyOnWriteArrayList`, así que la migración no empeora nada. No lo "arregles" con un cerrojo
 * más grande sin medir: el patrón de `NpcAiManager` es un único hilo escribiendo por tick.
 *
 * A diferencia de `CopyOnWriteArrayList`, escribir **no copia el arreglo entero**, así que las
 * escrituras son más baratas y las lecturas por índice algo más caras (entran al cerrojo). Para
 * decenas de NPCs, irrelevante.
 */
class PowListaConcurrente<T>() : MutableList<T> {

    private val cerrojo = PowCerrojo()
    private val lista = mutableListOf<T>()

    constructor(inicial: Collection<T>) : this() {
        lista.addAll(inicial)
    }

    /** La foto que recorren [iterator] y compañía. */
    fun copia(): List<T> = cerrojo.ejecutar { lista.toList() }

    /**
     * Devuelve una copia **y vacía la lista sin soltar el cerrojo**.
     *
     * ⚠️ Existe porque "leer y luego limpiar" en dos pasos **pierde elementos**: lo que se añada
     * entre medias se borra sin haberse leído. Es exactamente el patrón de
     * `NpcAiManager.pendingDespawns`, que antes se protegía con un `synchronized(lista)` desde
     * `:app` — un candado que ya NO sirve, porque no excluye al cerrojo interno de esta clase.
     * Un despawn perdido no se ve aquí: se ve en los OTROS clientes, con un NPC fantasma que ya
     * no existe para nadie más.
     */
    fun drenar(): List<T> = cerrojo.ejecutar {
        val foto = lista.toList()
        lista.clear()
        foto
    }

    override val size: Int get() = cerrojo.ejecutar { lista.size }
    override fun isEmpty(): Boolean = cerrojo.ejecutar { lista.isEmpty() }
    override fun contains(element: T): Boolean = cerrojo.ejecutar { lista.contains(element) }
    override fun containsAll(elements: Collection<T>): Boolean = cerrojo.ejecutar { lista.containsAll(elements) }
    override fun get(index: Int): T = cerrojo.ejecutar { lista[index] }
    override fun indexOf(element: T): Int = cerrojo.ejecutar { lista.indexOf(element) }
    override fun lastIndexOf(element: T): Int = cerrojo.ejecutar { lista.lastIndexOf(element) }

    override fun add(element: T): Boolean = cerrojo.ejecutar { lista.add(element) }
    override fun add(index: Int, element: T) { cerrojo.ejecutar { lista.add(index, element) } }
    override fun addAll(elements: Collection<T>): Boolean = cerrojo.ejecutar { lista.addAll(elements) }
    override fun addAll(index: Int, elements: Collection<T>): Boolean = cerrojo.ejecutar { lista.addAll(index, elements) }
    override fun clear() { cerrojo.ejecutar { lista.clear() } }
    override fun remove(element: T): Boolean = cerrojo.ejecutar { lista.remove(element) }
    override fun removeAll(elements: Collection<T>): Boolean = cerrojo.ejecutar { lista.removeAll(elements) }
    override fun removeAt(index: Int): T = cerrojo.ejecutar { lista.removeAt(index) }
    override fun retainAll(elements: Collection<T>): Boolean = cerrojo.ejecutar { lista.retainAll(elements) }
    override fun set(index: Int, element: T): T = cerrojo.ejecutar { lista.set(index, element) }

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<T> =
        cerrojo.ejecutar { lista.subList(fromIndex, toIndex).toMutableList() }

    override fun iterator(): MutableIterator<T> = iteradorDeCopia(copia().toMutableList())
    override fun listIterator(): MutableListIterator<T> = copia().toMutableList().listIterator()
    override fun listIterator(index: Int): MutableListIterator<T> = copia().toMutableList().listIterator(index)

    private fun iteradorDeCopia(copia: MutableList<T>): MutableIterator<T> {
        val base = copia.iterator()
        return object : MutableIterator<T> {
            override fun hasNext(): Boolean = base.hasNext()
            override fun next(): T = base.next()
            override fun remove(): Unit =
                throw UnsupportedOperationException(
                    "PowListaConcurrente.iterator() recorre una COPIA: remove() no tocaría la lista real. " +
                        "Usa removeAll/removeAt sobre la lista."
                )
        }
    }
}
