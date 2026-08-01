package ovh.gabrielhuav.pow.platform.concurrencia

/**
 * En la JVM el `synchronized` del lenguaje ya es reentrante, que es exactamente lo que daba
 * `@Synchronized` antes de la Fase 5: comportamiento idéntico al de la app en producción.
 */
actual class PowCerrojo actual constructor() {

    private val monitor = Any()

    actual fun <T> ejecutar(bloque: () -> T): T = synchronized(monitor) { bloque() }
}
