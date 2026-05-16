package ovh.gabrielhuav.pow.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ovh.gabrielhuav.pow.data.local.room.entity.LandmarkEntity

@Dao
interface LandmarkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLandmarks(landmarks: List<LandmarkEntity>)

    // Obtener todos los edificios (útil si son pocos)
    @Query("SELECT * FROM landmarks")
    suspend fun getAllLandmarks(): List<LandmarkEntity>

    // Obtener edificios cercanos basados en un bounding box (caja de colisión de coordenadas)
    @Query("SELECT * FROM landmarks WHERE latitude BETWEEN :minLat AND :maxLat AND longitude BETWEEN :minLon AND :maxLon")
    suspend fun getLandmarksInRegion(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<LandmarkEntity>
}