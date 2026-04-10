package ovh.gabrielhuav.pow.data.local.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import ovh.gabrielhuav.pow.data.local.room.dao.MapTileDao
import ovh.gabrielhuav.pow.data.local.room.dao.RoadNetworkDao
import ovh.gabrielhuav.pow.data.local.room.entity.MapTileEntity
import ovh.gabrielhuav.pow.data.local.room.entity.RoadNodeEntity
import ovh.gabrielhuav.pow.data.local.room.entity.RoadWayEntity
import ovh.gabrielhuav.pow.data.local.room.entity.RoadZoneEntity
import java.io.File

@Database(
    entities = [
        RoadZoneEntity::class,
        RoadWayEntity::class,
        RoadNodeEntity::class,
        MapTileEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class PowDatabase : RoomDatabase() {

    abstract fun roadNetworkDao(): RoadNetworkDao
    abstract fun mapTileDao(): MapTileDao

    companion object {
        @Volatile
        private var INSTANCE: PowDatabase? = null

        fun getInstance(context: Context): PowDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): PowDatabase {
            val dbDir  = File(context.filesDir, "databases").also { it.mkdirs() }
            val dbFile = File(dbDir, "pow_roads.db")

            return Room.databaseBuilder(
                context.applicationContext,
                PowDatabase::class.java,
                dbFile.absolutePath
            )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
    }
}