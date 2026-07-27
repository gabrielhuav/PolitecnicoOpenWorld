package ovh.gabrielhuav.pow.data.local.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import ovh.gabrielhuav.pow.data.local.room.entity.LandmarkEntity

@Dao
interface LandmarkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLandmarks(landmarks: List<LandmarkEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLandmark(landmark: LandmarkEntity): Long

    @Update
    suspend fun updateLandmark(landmark: LandmarkEntity)

    @Delete
    suspend fun deleteLandmark(landmark: LandmarkEntity)

    @Query("SELECT * FROM landmarks")
    suspend fun getAllLandmarks(): List<LandmarkEntity>

    @Query("SELECT * FROM landmarks WHERE id = :id")
    suspend fun getLandmarkById(id: Long): LandmarkEntity?

    @Query("SELECT * FROM landmarks WHERE latitude BETWEEN :minLat AND :maxLat AND longitude BETWEEN :minLon AND :maxLon")
    suspend fun getLandmarksInRegion(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<LandmarkEntity>

    @Query("SELECT COUNT(*) FROM landmarks")
    suspend fun count(): Int
}