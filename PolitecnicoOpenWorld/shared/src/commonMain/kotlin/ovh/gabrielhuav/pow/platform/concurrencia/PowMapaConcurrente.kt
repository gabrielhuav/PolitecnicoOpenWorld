package ovh.gabrielhuav.pow.platform.concurrencia

/**
 * 🔐 MAPA COMPARTIDO ENTRE HILOS, MULTIPLATAFORMA.
 *
 * ## Por qué existe
 *
 * Los gestores del mundo abierto (`PoliceManager`, `NpcAiManager`, `CampaignEscortPolice`…) guardan
 * su estado en `java.util.concurrent.ConcurrentHashMap`, **que no existe en Kotlin/Native**. Y no
 * es un adorno que se pueda quitar: MEDIDO el 2026-07-30, a `PoliceManager` se le llama desde
 * **tres dispatchers distintos** —`Default` (el bucle de juego), `IO` (la red) y `Main` (cuando el
 * jugador toca la pantalla)—, así que las carreras son reales.
 *
 * ## Por qué es un reemplazo y no un "envuélvelo tú en cada sitio"
 *
 * Cambiar cada acceso a mano por `cerrojo.ejecutar { ... }` obliga a acertar en ~50 sitios por
 * archivo, y **el compilador no te avisa si te dejas uno**: el fallo aparece como un tirón raro a
 * los diez minutos de partida. Con esta clase la migración es **un cambio de tipo**, la semántica
 * se conserva por construcción, y no hay ni una decisión que tomar por cada uso.
 *
 * ## En qué SÍ se diferencia de `ConcurrentHashMap` (léelo antes de usarla)
 *
 * | | `ConcurrentHashMap` | Esta |
 * |---|---|---|
 * | Bloqueo | por franjas | **uno solo para todo el mapa** |
 * | [valores] / [claves] / [entradas] | vista viva y débilmente consistente | **copia instantánea** |
 *
 * - **El cerrojo único** está bien aquí porque estos mapas guardan unidades de policía y NPCs
 *   cercanos: decenas de entradas, no millones. Si algún día uno crece mucho y se nota, hay que
 *   medirlo antes de complicarlo.
 * - **La copia instantánea es más segura, no menos:** recorrer el resultado nunca lanza
 *   `ConcurrentModificationException` aunque otro hilo escriba a la vez. El código del mundo ya
 *   hacía `.values.toList()` en varios sitios justamente por eso.
 *
 * ⚠️ **El cerrojo es REENTRANTE**, así que las lambdas de [obtenerOPoner] y [calcular] pueden volver
 * a tocar el mismo mapa sin bloquearse. Aun así, **no llames dentro a OTRO mapa con cerrojo**: dos
 * cerrojos tomados en distinto orden por dos hilos es un interbloqueo de manual.
 */
class PowMapaConcurrente<K, V> {

    private val cerrojo = PowCerrojo()
    private val mapa = mutableMapOf<K, V>()

    /** El valor de [clave], o `null`. */
    operator fun get(clave: K): V? = cerrojo.ejecutar { mapa[clave] }

    /** Guarda [valor] en [clave]. Devuelve el que hubiera antes. */
    operator fun set(clave: K, valor: V) {
        cerrojo.ejecutar { mapa[clave] = valor }
    }

    /** Igual que [set], pero devolviendo el valor anterior, como `Map.put`. */
    fun poner(clave: K, valor: V): V? = cerrojo.ejecutar { mapa.put(clave, valor) }

    /** Quita [clave] y devuelve lo que había. */
    fun quitar(clave: K): V? = cerrojo.ejecutar { mapa.remove(clave) }

    /** Vacía el mapa. */
    fun limpiar() {
        cerrojo.ejecutar { mapa.clear() }
    }

    /** ¿Está [clave]? */
    fun contiene(clave: K): Boolean = cerrojo.ejecutar { clave in mapa }

    /** Cuántas entradas hay. */
    val tamano: Int get() = cerrojo.ejecutar { mapa.size }

    /** ¿Está vacío? */
    fun estaVacio(): Boolean = cerrojo.ejecutar { mapa.isEmpty() }

    /**
     * El valor de [clave]; si no está, calcula uno con [porDefecto], lo guarda y lo devuelve.
     *
     * ⚠️ **Es ATÓMICO**, que es justo lo que no sería `if (x == null) mapa[k] = f()` suelto: entre
     * la comprobación y la escritura otro hilo puede colarse.
     */
    fun obtenerOPoner(clave: K, porDefecto: () -> V): V = cerrojo.ejecutar {
        mapa.getOrPut(clave, porDefecto)
    }

    /**
     * Cambia el valor de [clave] en función del que ya hubiera, **sin que nadie se cuele en medio**.
     * Si [transformar] devuelve `null`, la entrada se borra.
     */
    fun calcular(clave: K, transformar: (V?) -> V?): V? = cerrojo.ejecutar {
        val nuevo = transformar(mapa[clave])
        if (nuevo == null) mapa.remove(clave) else mapa[clave] = nuevo
        nuevo
    }

    /** Quita todas las entradas que cumplan [condicion]. Devuelve las claves quitadas. */
    fun quitarSi(condicion: (K, V) -> Boolean): List<K> = cerrojo.ejecutar {
        val fuera = mapa.filter { (k, v) -> condicion(k, v) }.keys.toList()
        fuera.forEach { mapa.remove(it) }
        fuera
    }

    /** Copia instantánea de los valores. Recorrerla es seguro aunque otro hilo escriba. */
    val valores: List<V> get() = cerrojo.ejecutar { mapa.values.toList() }

    /** Copia instantánea de las claves. */
    val claves: List<K> get() = cerrojo.ejecutar { mapa.keys.toList() }

    /** Copia instantánea de las entradas. */
    val entradas: List<Pair<K, V>> get() = cerrojo.ejecutar { mapa.map { it.key to it.value } }

    /** Copia instantánea como `Map` normal, para lo que necesite uno de verdad. */
    fun copia(): Map<K, V> = cerrojo.ejecutar { mapa.toMap() }
}
