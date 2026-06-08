package ovh.gabrielhuav.pow.data.local.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "landmarks")
data class LandmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,           // ej. "ESCOM"
    val latitude: Double,
    val longitude: Double,
    val assetPath: String,      // ej. "BUILDINGS/IPN/building_escom.webp"
    val scaleFactor: Float = 1.0f,  // Mantenido para compatibilidad con datos previos
    val rotationAngle: Float = 0f,  // Rotación persistida (grados, 0-360)
    val scaleX: Float = 1.0f,       // Escala horizontal independiente
    val scaleY: Float = 1.0f        // Escala vertical independiente
)