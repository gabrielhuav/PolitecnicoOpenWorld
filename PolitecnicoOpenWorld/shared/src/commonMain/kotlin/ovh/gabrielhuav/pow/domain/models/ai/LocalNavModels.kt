package ovh.gabrielhuav.pow.domain.models.ai

import kotlinx.serialization.Serializable

import kotlinx.serialization.SerialName

// Coordenada local dentro del asset.
@Serializable
data class LocalNode(
    @SerialName("id") val id: Int = 0,
    @SerialName("localX") val localX: Float = 0f,
    @SerialName("localY") val localY: Float = 0f,
    @SerialName("isParkingSlot") val isParkingSlot: Boolean = false,
    @SerialName("description") val description: String? = null,
    // Si es true, los NPCs peatonales se detienen aquí un tiempo (bancas, cafetería, palapas…)
    // antes de continuar su ruta. El tiempo es aleatorio entre STOP_MIN_MS y STOP_MAX_MS.
    @SerialName("isStopPoint") val isStopPoint: Boolean = false
)

@Serializable
data class LocalWay(
    @SerialName("id") val id: Int = 0,
    @SerialName("nodes") val nodes: List<LocalNode> = emptyList(),
    @SerialName("isForCars") val isForCars: Boolean = true,
    @SerialName("isForPeople") val isForPeople: Boolean = false
)

@Serializable
data class LandmarkNavGraph(
    @SerialName("landmarkId") val landmarkId: String? = null,
    @SerialName("entryWays") val entryWays: List<Int> = emptyList(), // JSON usa entryWays
    @SerialName("ways") val ways: List<LocalWay> = emptyList()
)