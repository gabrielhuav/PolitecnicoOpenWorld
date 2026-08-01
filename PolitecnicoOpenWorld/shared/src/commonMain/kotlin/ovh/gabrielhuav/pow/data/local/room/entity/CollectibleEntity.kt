package ovh.gabrielhuav.pow.data.local.room.entity


import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "collectibles")
data class CollectibleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val assetPath: String, // Ejemplo: "COLLECTIBLES/chilaquil.webp"
    val isCollected: Boolean = false
)