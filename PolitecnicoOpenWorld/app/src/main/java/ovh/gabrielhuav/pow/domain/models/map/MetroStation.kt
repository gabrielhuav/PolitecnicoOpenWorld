package ovh.gabrielhuav.pow.domain.models.map

import ovh.gabrielhuav.pow.domain.models.geo.GeoPoint

data class MetroStation(
    override val name: String,
    override val routes: List<String>,
    override val location: GeoPoint
) : TransitStation
