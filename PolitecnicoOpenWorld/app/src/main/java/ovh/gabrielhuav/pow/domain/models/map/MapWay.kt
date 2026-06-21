package ovh.gabrielhuav.pow.domain.models.map
data class MapWay(
    val id: Long,
    val nodes: List<MapNode>,
    val isForCars: Boolean,
    val isForPeople: Boolean
)