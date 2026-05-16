package ovh.gabrielhuav.pow.data.local.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "landmarks")
data class LandmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,         // ej. "ESCOM"
    val latitude: Double,
    val longitude: Double,
    val assetPath: String,    // ej. "BUILDINGS/IPN/building_escom.webp"
    val width: Float = 1f,    // Escala del edificio a lo ancho
    val height: Float = 1f    // Escala del edificio a lo alto
)