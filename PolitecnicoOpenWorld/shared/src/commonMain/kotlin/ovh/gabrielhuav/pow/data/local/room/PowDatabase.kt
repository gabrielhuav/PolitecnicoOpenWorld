package ovh.gabrielhuav.pow.data.local.room

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import ovh.gabrielhuav.pow.data.local.room.dao.CollectibleDao
import ovh.gabrielhuav.pow.data.local.room.dao.LandmarkDao
import ovh.gabrielhuav.pow.data.local.room.dao.MapTileDao
import ovh.gabrielhuav.pow.data.local.room.dao.RoadNetworkDao
import ovh.gabrielhuav.pow.data.local.room.entity.CollectibleEntity
import ovh.gabrielhuav.pow.data.local.room.entity.LandmarkEntity
import ovh.gabrielhuav.pow.data.local.room.entity.MapTileEntity
import ovh.gabrielhuav.pow.data.local.room.entity.RoadNodeEntity
import ovh.gabrielhuav.pow.data.local.room.entity.RoadWayEntity
import ovh.gabrielhuav.pow.data.local.room.entity.RoadZoneEntity

/**
 * 🍏 Base de datos del juego, ahora MULTIPLATAFORMA (Fase 4 de `PLAN_MIGRACION_KMP.md`).
 *
 * ⚠️ **LA VERSIÓN Y LAS MIGRACIONES NO SE TOCAN.** Sigue siendo la v9 y conserva
 * `MIGRATION_7_8` y `MIGRATION_8_9` con el MISMO SQL: en Android esta BD ya existe en los
 * teléfonos de los jugadores (caché de teselas y de calles, landmarks del Modo Diseñador,
 * coleccionables). Cambiar la versión o el esquema aquí obliga a una migración nueva.
 *
 * ⚠️ **EL FICHERO ES EL MISMO** (`filesDir/databases/pow_roads.db`): lo decide el `actual` de
 * Android, que conserva la ruta exacta de antes. Si esa ruta cambia, los jugadores "pierden" su
 * caché y su mapa editado (la app crearía una BD vacía al lado).
 *
 * Lo único que cambió al migrar:
 * - Las migraciones usan `SQLiteConnection` (API KMP) en vez de `SupportSQLiteDatabase` (Android).
 *   El SQL es idéntico, carácter por carácter.
 * - Hace falta `@ConstructedBy` + un `RoomDatabaseConstructor` `expect`: es como Room genera la
 *   implementación sin reflexión (en iOS no la hay).
 */
@Database(
    entities = [
        RoadZoneEntity::class,
        RoadWayEntity::class,
        RoadNodeEntity::class,
        MapTileEntity::class,
        LandmarkEntity::class,
        CollectibleEntity::class,
    ],
    version = 9,
    exportSchema = false,
)
@ConstructedBy(PowDatabaseConstructor::class)
abstract class PowDatabase : RoomDatabase() {

    abstract fun roadNetworkDao(): RoadNetworkDao
    abstract fun mapTileDao(): MapTileDao
    abstract fun landmarkDao(): LandmarkDao
    abstract fun collectibleDao(): CollectibleDao

    companion object {
        /** Añade la tabla de coleccionables (v7 → v8). SQL IDÉNTICO al de antes de la migración KMP. */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """CREATE TABLE IF NOT EXISTS `collectibles` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `assetPath` TEXT NOT NULL,
                        `isCollected` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`)
                    )""",
                )
            }
        }

        /** Escala por eje en landmarks (v8 → v9), derivada del `scaleFactor` que ya hubiera. */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE `landmarks` ADD COLUMN `scaleX` REAL NOT NULL DEFAULT 1.0")
                connection.execSQL("ALTER TABLE `landmarks` ADD COLUMN `scaleY` REAL NOT NULL DEFAULT 1.0")
                // Migrar el scaleFactor existente a scaleX y scaleY
                connection.execSQL("UPDATE `landmarks` SET `scaleX` = `scaleFactor`, `scaleY` = `scaleFactor`")
            }
        }
    }
}

/**
 * Room genera la implementación de esto en cada plataforma. Va vacío a propósito: es un contrato,
 * no código. (`expect object` + `@ConstructedBy` es el patrón oficial de Room KMP.)
 */
@Suppress("NO_ACTUAL_FOR_EXPECT", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object PowDatabaseConstructor : RoomDatabaseConstructor<PowDatabase> {
    override fun initialize(): PowDatabase
}
