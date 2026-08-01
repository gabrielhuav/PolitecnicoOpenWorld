package ovh.gabrielhuav.pow.data.local.room

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

/**
 * 🍏 Construcción de la BD en ANDROID (Fase 4 de `PLAN_MIGRACION_KMP.md`).
 *
 * ⚠️⚠️ **LA RUTA DEL FICHERO ES SAGRADA.** `filesDir/databases/pow_roads.db` es donde ya vive la
 * base de datos en los teléfonos que tienen la 1.0.0.14 instalada. Si se cambia (aunque sea a la
 * ruta "estándar" de Room), la app crearía una BD VACÍA al lado y el jugador perdería la caché de
 * calles y teselas y **los landmarks que haya movido en el Modo Diseñador**. Se copió tal cual del
 * `buildDatabase` anterior a la migración.
 *
 * Se sigue usando el driver de SQLite del SISTEMA (el de siempre en Android), no el empaquetado:
 * así el fichero se abre exactamente igual que antes. El `sqlite-bundled` es solo para iOS.
 */
internal actual fun crearPowDatabaseBuilder(contexto: Any?): RoomDatabase.Builder<PowDatabase> {
    val context = contexto as Context
    val dbDir = File(context.filesDir, "databases").also { it.mkdirs() }
    val dbFile = File(dbDir, "pow_roads.db")
    return Room.databaseBuilder<PowDatabase>(
        context = context.applicationContext,
        name = dbFile.absolutePath,
    )
}
