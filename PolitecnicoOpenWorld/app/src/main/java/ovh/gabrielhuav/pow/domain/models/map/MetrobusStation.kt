package ovh.gabrielhuav.pow.domain.models.map

import org.osmdroid.util.GeoPoint

data class MetrobusStation(
    val name: String,
    val routes: List<String>,
    val location: GeoPoint
)