package ovh.gabrielhuav.pow.data.local.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import ovh.gabrielhuav.pow.data.local.room.entity.WaypointEntity

@Dao
interface WaypointDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaypoint(waypoint: WaypointEntity): Long

    @Update
    suspend fun updateWaypoint(waypoint: WaypointEntity)

    @Delete
    suspend fun deleteWaypoint(waypoint: WaypointEntity)

    @Query("SELECT * FROM waypoints ORDER BY createdAt DESC")
    suspend fun getAllWaypoints(): List<WaypointEntity>

    @Query("DELETE FROM waypoints WHERE id = :id")
    suspend fun deleteWaypointById(id: Long)
}
