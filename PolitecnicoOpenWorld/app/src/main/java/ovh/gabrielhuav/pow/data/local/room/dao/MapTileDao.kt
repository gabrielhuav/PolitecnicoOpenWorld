package ovh.gabrielhuav.pow.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    /**
     * Inserta un tile de forma atómica: en una sola transacción cuenta los tiles
     * del provider, hace evict (borra los más viejos) si excede el máximo, e inserta
     * el tile nuevo. Evita corrupción si el proceso muere a media escritura.
     */
    @Transaction
    fun putTileAtomic(tile: MapTileEntity, maxTilesPerProvider: Int, evictBatch: Int) {
        val count = getCount(tile.provider)
        if (count >= maxTilesPerProvider) {
            deleteOldestTiles(tile.provider, evictBatch)
        }
        insertTile(tile)
    }
}