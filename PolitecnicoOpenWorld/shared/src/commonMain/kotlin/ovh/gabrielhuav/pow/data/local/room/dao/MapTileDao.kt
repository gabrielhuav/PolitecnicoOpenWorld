package ovh.gabrielhuav.pow.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import ovh.gabrielhuav.pow.data.local.room.entity.MapTileEntity

/**
 * ⚠️ **TODO ESTE DAO ES `suspend` POR OBLIGACIÓN DE ROOM, NO POR DISEÑO.**
 *
 * Room solo permite DAOs bloqueantes cuando el source set es de Android. Al vivir en `commonMain`
 * y compilarse también para iOS, el compilador de Room (KSP) corta con:
 * *"Only suspend functions are allowed in DAOs declared in source sets targeting non-Android
 * platforms"*. Fue el PRIMER fallo real de la migración a iOS (Mac, 2026-07-27), y no lo vio nadie
 * en Windows porque allí los targets iOS ni se compilan.
 *
 * Estas consultas se llaman desde dos callbacks SÍNCRONOS de framework que no se pueden volver
 * `suspend` (`WebViewClient.shouldInterceptRequest` y `MapTileModuleProviderBase.loadTile`). El
 * puente vive en `TileCache` (módulo `:app`), que mantiene su API bloqueante con `runBlocking`.
 * Si tocas las firmas de aquí, mira ahí primero.
 */
@Dao
interface MapTileDao {
    @Query("SELECT data FROM map_tiles WHERE provider = :provider AND urlKey = :urlKey")
    suspend fun getTileData(provider: String, urlKey: String): ByteArray?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTile(tile: MapTileEntity)

    @Query("SELECT COUNT(*) FROM map_tiles WHERE provider = :provider")
    suspend fun getCount(provider: String): Long

    @Query("DELETE FROM map_tiles WHERE rowid IN (SELECT rowid FROM map_tiles WHERE provider = :provider ORDER BY createdAtMs ASC LIMIT :limit)")
    suspend fun deleteOldestTiles(provider: String, limit: Int)

    /**
     * Inserta un tile de forma atómica: en una sola transacción cuenta los tiles
     * del provider, hace evict (borra los más viejos) si excede el máximo, e inserta
     * el tile nuevo. Evita corrupción si el proceso muere a media escritura.
     */
    @Transaction
    suspend fun putTileAtomic(tile: MapTileEntity, maxTilesPerProvider: Int, evictBatch: Int) {
        val count = getCount(tile.provider)
        if (count >= maxTilesPerProvider) {
            deleteOldestTiles(tile.provider, evictBatch)
        }
        insertTile(tile)
    }
}