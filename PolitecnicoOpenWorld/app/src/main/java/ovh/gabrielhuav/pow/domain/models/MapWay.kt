package ovh.gabrielhuav.pow.domain.models
data class MapWay(
    val id: Long,
    val nodes: List<MapNode>,
    val isForCars: Boolean,
    val isForPeople: Boolean
)