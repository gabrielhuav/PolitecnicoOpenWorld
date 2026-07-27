package ovh.gabrielhuav.pow.data.local.room

import androidx.room.RoomDatabase

/**
 * 🍏 Punto ÚNICO de creación de la BD (Fase 4 de `PLAN_MIGRACION_KMP.md`).
 *
 * El `expect` cubre lo que de verdad cambia por plataforma: la RUTA del fichero y **el driver de
 * SQLite**. Las migraciones se aplican aquí, en común, para que Android e iOS no diverjan.
 *
 * ⚠️ **El driver lo elige cada plataforma A PROPÓSITO.** Android se queda con el de siempre (el
 * del sistema): esa BD ya existe en los teléfonos y el objetivo es abrirla EXACTAMENTE igual que
 * antes. iOS usa `BundledSQLiteDriver`, porque allí no hay SQLite de sistema que Room pueda usar.
 *
 * ⚠️ `fallbackToDestructiveMigration` se conserva del código original: si algún día falta una
 * migración, la BD se recrea en vez de crashear. Es caché + landmarks, no la partida guardada
 * (esa vive en JSON), así que el destrozo es asumible — pero NO lo quites sin añadir la migración.
 */
internal expect fun crearPowDatabaseBuilder(contexto: Any?): RoomDatabase.Builder<PowDatabase>

/**
 * Crea la BD ya configurada. `contexto` es el `Context` en Android y se ignora en iOS
 * (se tipa como `Any?` para que la firma sea común: `Context` no existe en `commonMain`).
 */
fun crearPowDatabase(contexto: Any? = null): PowDatabase =
    crearPowDatabaseBuilder(contexto)
        .addMigrations(PowDatabase.MIGRATION_7_8, PowDatabase.MIGRATION_8_9)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
