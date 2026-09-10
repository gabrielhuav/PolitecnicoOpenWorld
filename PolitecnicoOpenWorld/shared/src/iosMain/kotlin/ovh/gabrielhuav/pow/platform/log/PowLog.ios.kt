package ovh.gabrielhuav.pow.platform.log

/**
 * En iOS `println` sale en la consola de Xcode. Se antepone la etiqueta a mano para poder filtrar
 * igual que en logcat.
 */
actual fun powLog(etiqueta: String, mensaje: String) {
    println("$etiqueta: $mensaje")
}
