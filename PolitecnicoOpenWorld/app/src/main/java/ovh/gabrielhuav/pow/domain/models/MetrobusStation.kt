package ovh.gabrielhuav.pow.domain.models

import org.osmdroid.util.GeoPoint

data class MetrobusStation(
    val name: String,
    val routes: List<String>,
    val location: GeoPoint
)