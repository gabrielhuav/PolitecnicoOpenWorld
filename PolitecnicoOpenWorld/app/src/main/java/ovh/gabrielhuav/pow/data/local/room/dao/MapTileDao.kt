package ovh.gabrielhuav.pow.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ovh.gabrielhuav.pow.data.local.room.entity.MapTileEntity

@Dao
interface MapTileDao {
    @Query("SELECT data FROM map_tiles WHERE provider = :provider AND urlKey = :urlKey")
    fun getTileData(provider: String, urlKey: String): ByteArray?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTile(tile: MapTileEntity)

    @Query("SELECT COUNT(*) FROM map_tiles WHERE provider = :provider")
    fun getCount(provider: String): Long

    @Query("DELETE FROM map_tiles WHERE rowid IN (SELECT rowid FROM map_tiles WHERE provider = :provider ORDER BY createdAtMs ASC LIMIT :limit)")
    fun deleteOldestTiles(provider: String, limit: Int)
}