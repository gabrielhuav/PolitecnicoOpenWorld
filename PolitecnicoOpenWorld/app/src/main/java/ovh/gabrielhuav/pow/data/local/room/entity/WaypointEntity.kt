package ovh.gabrielhuav.pow.data.local.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "waypoints")
data class WaypointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val createdAt: Long = System.currentTimeMillis()
)
