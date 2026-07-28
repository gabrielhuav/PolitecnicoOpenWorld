package ovh.gabrielhuav.pow.platform.concurrencia

import platform.Foundation.NSRecursiveLock

/**
 * `NSRecursiveLock`, NO `NSLock`: ver la nota de reentrancia en el `expect`. Con `NSLock`, la
 * llamada anidada `SfFrameCatalog.load` → `template()` se quedaría colgada para siempre.
 */
actual class PowCerrojo actual constructor() {

    private val cerrojo = NSRecursiveLock()

    actual fun <T> ejecutar(bloque: () -> T): T {
        cerrojo.lock()
        try {
            return bloque()
        } finally {
            // En `finally` obligatoriamente: si [bloque] lanza, un cerrojo sin soltar cuelga la app.
            cerrojo.unlock()
        }
    }
}
