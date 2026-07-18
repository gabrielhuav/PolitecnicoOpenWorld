package ovh.gabrielhuav.pow.domain.models.ai

import com.google.gson.annotations.SerializedName

// Coordenada local dentro del asset.
data class LocalNode(
    @SerializedName("id") val id: Int,
    @SerializedName("localX") val localX: Float,
    @SerializedName("localY") val localY: Float,
    @SerializedName("isParkingSlot") val isParkingSlot: Boolean = false,
    @SerializedName("description") val description: String? = null,
    // Si es true, los NPCs peatonales se detienen aquí un tiempo (bancas, cafetería, palapas…)
    // antes de continuar su ruta. El tiempo es aleatorio entre STOP_MIN_MS y STOP_MAX_MS.
    @SerializedName("isStopPoint") val isStopPoint: Boolean = false
)

data class LocalWay(
    @SerializedName("id") val id: Int,
    @SerializedName("nodes") val nodes: List<LocalNode>,
    @SerializedName("isForCars") val isForCars: Boolean = true,
    @SerializedName("isForPeople") val isForPeople: Boolean = false
)

data class LandmarkNavGraph(
    @SerializedName("landmarkId") val landmarkId: String? = null,
    @SerializedName("entryWays") val entryWays: List<Int> = emptyList(), // JSON usa entryWays
    @SerializedName("ways") val ways: List<LocalWay> = emptyList()
)