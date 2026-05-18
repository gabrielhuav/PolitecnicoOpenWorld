package ovh.gabrielhuav.pow.data.local.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ovh.gabrielhuav.pow.data.local.room.dao.MapTileDao
import ovh.gabrielhuav.pow.data.local.room.dao.RoadNetworkDao
import ovh.gabrielhuav.pow.data.local.room.dao.LandmarkDao
import ovh.gabrielhuav.pow.data.local.room.entity.MapTileEntity
import ovh.gabrielhuav.pow.data.local.room.entity.RoadNodeEntity
import ovh.gabrielhuav.pow.data.local.room.entity.RoadWayEntity
import ovh.gabrielhuav.pow.data.local.room.entity.RoadZoneEntity
import ovh.gabrielhuav.pow.data.local.room.entity.LandmarkEntity
import ovh.gabrielhuav.pow.data.local.room.dao.CollectibleDao
import ovh.gabrielhuav.pow.data.local.room.entity.CollectibleEntity
import java.io.File

@Database(
    entities = [
        RoadZoneEntity::class,
        RoadWayEntity::class,
        RoadNodeEntity::class,
        MapTileEntity::class,
        LandmarkEntity::class,
        CollectibleEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class PowDatabase : RoomDatabase() {

    abstract fun roadNetworkDao(): RoadNetworkDao
    abstract fun mapTileDao(): MapTileDao
    abstract fun landmarkDao(): LandmarkDao

    abstract fun collectibleDao(): CollectibleDao

    companion object {
        @Volatile
        private var INSTANCE: PowDatabase? = null

        fun getInstance(context: Context): PowDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `collectibles` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `assetPath` TEXT NOT NULL,
                        `isCollected` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`)
                    )"""
                )
            }
        }

        private fun buildDatabase(context: Context): PowDatabase {
            val dbDir  = File(context.filesDir, "databases").also { it.mkdirs() }
            val dbFile = File(dbDir, "pow_roads.db")

            return Room.databaseBuilder(
                context.applicationContext,
                PowDatabase::class.java,
                dbFile.absolutePath
            ).addMigrations(MIGRATION_7_8).build()
        }
    }
}