package ovh.gabrielhuav.pow.data.local.room

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/**
 * 🍏 Construcción de la BD en iOS (Fase 4 de `PLAN_MIGRACION_KMP.md`).
 *
 * Vive en el directorio Documents del sandbox de la app, que es el equivalente de `filesDir`.
 * Aquí NO hay datos previos que conservar (iOS es instalación nueva), así que la única regla es
 * usar siempre la misma ruta.
 *
 * ⚠️ **SIN VERIFICAR TODAVÍA**: este archivo no se ha compilado nunca — Kotlin/Native no compila
 * targets iOS en Windows, que es donde se escribió. Es lo primero que hay que probar en el Mac.
 */
internal actual fun crearPowDatabaseBuilder(contexto: Any?): RoomDatabase.Builder<PowDatabase> {
    val documentos = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    val ruta = requireNotNull(documentos?.path) { "No se pudo resolver el directorio Documents" }
    return Room.databaseBuilder<PowDatabase>(name = "$ruta/pow_roads.db")
        // En iOS no hay SQLite de sistema para Room: se empaqueta el driver.
        .setDriver(BundledSQLiteDriver())
}
