package ovh.gabrielhuav.pow.data.local.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Representa una zona descargada de Overpass.
 * La clave es la celda de cuadrícula (~2km x 2km) que identifica la zona.
 *
 * filesDir/databases/pow_roads.db → nunca borrado por Android ni por limpiadores.
 */
@Entity(tableName = "road_zones")
data class RoadZoneEntity(
    // Clave: "cellLat_cellLon", ej: "975_-4958" para la zona de ESCOM
    @PrimaryKey val cellKey: String,
    // Timestamp de cuándo se descargó — para saber si expiró (TTL 7 días)
    val downloadedAtMs: Long,
    // Número de ways en esta zona (para logs/diagnóstico)
    val wayCount: Int
)

/**
 * Un segmento de calle (way de OSM).
 * Separado de los nodos para normalizar y ahorrar espacio.
 */
@Entity(
    tableName = "road_ways",
    foreignKeys = [ForeignKey(
        entity = RoadZoneEntity::class,
        parentColumns = ["cellKey"],
        childColumns = ["cellKey"],
        onDelete = ForeignKey.CASCADE  // Al borrar una zona, se borran sus ways
    )],
    indices = [Index("cellKey")]
)
data class RoadWayEntity(
    @PrimaryKey val wayId: Long,
    val cellKey: String,
    val isForCars: Boolean,
    val isForPeople: Boolean
)

/**
 * Un nodo de un way. Guardamos lat/lon como Long (×1_000_000) para precisión
 * y eficiencia — evita el overhead de columnas REAL en SQLite.
 *
 * Ejemplo: lat 19.504512 → latInt 19504512
 */
@Entity(
    tableName = "road_nodes",
    foreignKeys = [ForeignKey(
        entity = RoadWayEntity::class,
        parentColumns = ["wayId"],
        childColumns = ["wayId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("wayId")]
)
data class RoadNodeEntity(
    // PK compuesta: wayId + posición en la secuencia del way
    @PrimaryKey(autoGenerate = true) val nodeRowId: Long = 0,
    val wayId: Long,
    val nodeId: Long,
    val position: Int,      // Orden del nodo dentro del way
    val latInt: Long,       // lat × 1_000_000
    val lonInt: Long        // lon × 1_000_000
)