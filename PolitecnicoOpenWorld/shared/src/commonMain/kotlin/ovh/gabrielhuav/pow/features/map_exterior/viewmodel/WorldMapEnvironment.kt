package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

/**
 * 🌎 LO QUE EL MUNDO ABIERTO NECESITA DE LA PLATAFORMA, Y NADA MÁS.
 *
 * Es el patrón **Environment** de `10 §2bis` (mecanismo 3), el mismo que ya usa el modo pelea con
 * `StreetFighterEnvironment`. La idea: en vez de que `WorldMapViewModel` arrastre un `Context` de
 * Android por 43 archivos, declara **la lista corta de cosas que de verdad no puede hacer solo** y
 * cada plataforma la implementa.
 *
 * ## Por qué esta interfaz es tan pequeña
 *
 * Al medirlo (2026-08-20), el acoplamiento del VM con Android era mucho menor de lo que parecía:
 * de los 16 usos de `Context`, **la mayoría eran `context.assets.open(...)`** y ya existía
 * `PowAssets` en `commonMain`. Lo que queda aquí es lo que no tiene equivalente compartido:
 *
 * | Miembro | Por qué no puede ser común |
 * |---|---|
 * | [factorDeGama] | Mide la RAM del aparato (`ActivityManager` en Android) |
 * | [avisar] | Un `Toast` de Android; en iOS no existe nada igual |
 * | [guardarTexto] / [leerTexto] | El selector de archivos del sistema (SAF en Android) |
 *
 * ⚠️ **No metas aquí nada que ya sepa hacer `commonMain`.** La tentación al portar es declarar un
 * método por cada llamada que dé error de compilación; el resultado es una interfaz que copia la
 * API de Android y no comparte nada. Antes de añadir un miembro, mira si existe ya en
 * `PowAssets`, `PowImagen`, `PowJson`, `PowAudio` o `ahoraMs()`.
 */
interface WorldMapEnvironment {

    /**
     * Multiplicador de población de NPCs según lo que aguante el aparato.
     *
     * ⚠️ **NO es un porcentaje**: va de **0,6** (gama baja / Android Go) a **1,5** (gama alta), y
     * 1,0 es lo normal. En un aparato potente pide MÁS NPCs, no menos. En Android sale de la RAM
     * total que declara `ActivityManager`; en iOS devuelve 1, porque no hay una medida equivalente
     * que valga la pena y cualquier iPhone con iOS 16+ va sobrado para esto.
     */
    fun factorDeGama(): Float

    /**
     * Un aviso efímero para el jugador, del tipo "no se pudo leer el navgraph".
     *
     * ⚠️ **Es para errores y para el Modo Desarrollador, NO para los avisos del juego.** Lo que el
     * jugador tiene que leer mientras juega va en `interactionPrompt` del estado, que se pinta
     * dentro del mundo y funciona igual en las dos plataformas.
     */
    fun avisar(texto: String)

    /**
     * Escribe [contenido] en un destino que eligió el usuario. Es el Modo Diseñador exportando
     * sus landmarks.
     *
     * [destino] es opaco a propósito: en Android es el `Uri` del Storage Access Framework
     * serializado a texto, y quien lo entiende es la implementación de cada plataforma. Meter
     * `android.net.Uri` en la firma volvería a atar `commonMain` a Android, que es justo lo que
     * este archivo evita.
     *
     * @return `true` si se pudo escribir.
     */
    fun guardarTexto(destino: String, contenido: String): Boolean

    /** Lee un origen que eligió el usuario. `null` si no se pudo. Ver [guardarTexto]. */
    fun leerTexto(origen: String): String?
}

/**
 * El entorno de mentira, para pruebas y para las pantallas que todavía no tienen uno de verdad.
 *
 * No hace nada y **no falla**: un mundo sin `Toast` se juega igual, y una exportación que devuelve
 * `false` se ve como "no se guardó" en vez de tirar la app.
 */
object WorldMapEnvironmentInerte : WorldMapEnvironment {
    override fun factorDeGama(): Float = 1f  // población normal
    override fun avisar(texto: String) = Unit
    override fun guardarTexto(destino: String, contenido: String): Boolean = false
    override fun leerTexto(origen: String): String? = null
}
