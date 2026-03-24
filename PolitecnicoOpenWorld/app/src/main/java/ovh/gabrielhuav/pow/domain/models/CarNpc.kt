package ovh.gabrielhuav.pow.domain.models

data class CarNpc(
    val id: String,
    val name: String,
    val currentLocation: MapLocation,
    val path: List<MapLocation> = emptyList(),
    val currentPathIndex: Int = 0,
    val speed: Double = DEFAULT_CAR_SPEED,
    val isPlanningRoute: Boolean = false
) {
    companion object {
        const val DEFAULT_CAR_SPEED = 0.000015
    }
}