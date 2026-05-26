package ovh.gabrielhuav.pow.domain.models.ai

// Coordenada local dentro del asset.
// localX va de 0.0 (izquierda) a 1.0 (derecha)
// localY va de 0.0 (arriba) a 1.0 (abajo)
data class LocalNode(
    val id: Int,
    val localX: Float,
    val localY: Float,
    val isParkingSlot: Boolean = false // Define si aquí un auto debe detenerse y estacionarse
)

data class LocalWay(
    val id: Int,
    val nodes: List<LocalNode>,
    val isForCars: Boolean,
    val isForPeople: Boolean
)

data class LandmarkNavGraph(
    val ways: List<LocalWay>,
    val entryNodes: List<Int> // IDs de los LocalNodes que sirven de puerta entre OSM y este edificio
)