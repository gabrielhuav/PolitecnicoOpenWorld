package ovh.gabrielhuav.pow.platform.concurrencia

/**
 * 🍏 CERROJO REENTRANTE MULTIPLATAFORMA.
 *
 * POR QUÉ EXISTE: varios cachés de "Titulación por Combate" (hojas de sprites, frame data) usaban
 * `@Synchronized`, que **solo existe en la JVM** — en `commonMain` ni siquiera compila. Y no son
 * cachés de un solo hilo: armar una hoja compartida cuesta decenas de ms y el propio código dice
 * "llamar fuera del hilo de dibujo si se puede".
 *
 * ⚠️ REENTRANTE A PROPÓSITO: `SfFrameCatalog.load` llama a `template()`, y las dos cierran sobre el
 * MISMO cerrojo. Con uno no reentrante eso sería un INTERBLOQUEO — y del tipo que no aparece hasta
 * que alguien elige un peleador compartido.
 */
expect class PowCerrojo() {

    /** Ejecuta [bloque] en exclusión mutua y devuelve su resultado. */
    fun <T> ejecutar(bloque: () -> T): T
}
