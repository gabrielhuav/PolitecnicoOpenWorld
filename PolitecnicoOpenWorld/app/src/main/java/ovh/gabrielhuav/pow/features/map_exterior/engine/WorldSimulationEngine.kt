package ovh.gabrielhuav.pow.features.map_exterior.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ovh.gabrielhuav.pow.domain.models.CarNpc
import ovh.gabrielhuav.pow.domain.models.MapLocation
import ovh.gabrielhuav.pow.domain.models.Npc
import ovh.gabrielhuav.pow.features.map_exterior.data.RoutingRepository
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

data class SimulationState(
    val npcs: List<Npc> = emptyList(),
    val cars: List<CarNpc> = emptyList()
)

class WorldSimulationEngine(
    private val scope: CoroutineScope,
    private val repository: RoutingRepository
) {
    private val _simulationState = MutableStateFlow(SimulationState())
    val simulationState: StateFlow<SimulationState> = _simulationState.asStateFlow()

    private var isRunning = false

    fun spawnEntitiesNearPlayer(lat: Double, lon: Double) {
        val newNpcs = (1..NPC_COUNT).map { i ->
            Npc(
                id = "npc_$i",
                name = "Estudiante $i",
                currentLocation = MapLocation(
                    lat + (Math.random() - 0.5) * SPAWN_RADIUS_NPC,
                    lon + (Math.random() - 0.5) * SPAWN_RADIUS_NPC
                )
            )
        }

        val newCars = (1..CAR_COUNT).map { i ->
            CarNpc(
                id = "car_$i",
                name = "Auto $i",
                currentLocation = MapLocation(
                    lat + (Math.random() - 0.5) * SPAWN_RADIUS_CAR,
                    lon + (Math.random() - 0.5) * SPAWN_RADIUS_CAR
                )
            )
        }

        _simulationState.update { it.copy(npcs = newNpcs, cars = newCars) }
    }

    fun startLoop() {
        if (isRunning) return
        isRunning = true
        scope.launch {
            while (isActive) {
                delay(TICK_RATE_MS)
                updateNpcsPosition()
                updateCarsPosition()
            }
        }
    }

    private fun updateNpcsPosition() {
        val updatedNpcs = _simulationState.value.npcs.map { npc ->
            if ((npc.path.isEmpty() || npc.currentPathIndex >= npc.path.size) && !npc.isPlanningRoute) {
                val thinkingNpc = npc.copy(isPlanningRoute = true)
                scope.launch {
                    val destination = getRandomNearbyPoint(thinkingNpc.currentLocation, WANDER_RADIUS_NPC)
                    val newPath = repository.fetchPedestrianRoute(thinkingNpc.currentLocation, destination)
                    updateNpcInState(thinkingNpc.id, newPath)
                }
                return@map thinkingNpc
            }

            if (npc.isPlanningRoute || npc.path.isEmpty()) return@map npc
            moveEntity(npc.currentLocation, npc.path, npc.currentPathIndex, npc.speed)?.let { (newLoc, newIndex) ->
                return@map npc.copy(currentLocation = newLoc, currentPathIndex = newIndex)
            } ?: npc
        }
        _simulationState.update { it.copy(npcs = updatedNpcs) }
    }

    private fun updateCarsPosition() {
        val updatedCars = _simulationState.value.cars.map { car ->
            if ((car.path.isEmpty() || car.currentPathIndex >= car.path.size) && !car.isPlanningRoute) {
                val thinkingCar = car.copy(isPlanningRoute = true)
                scope.launch {
                    val destination = getRandomNearbyPoint(thinkingCar.currentLocation, WANDER_RADIUS_CAR)
                    val newPath = repository.fetchDrivingRoute(thinkingCar.currentLocation, destination)
                    updateCarInState(thinkingCar.id, newPath)
                }
                return@map thinkingCar
            }

            if (car.isPlanningRoute || car.path.isEmpty()) return@map car
            moveEntity(car.currentLocation, car.path, car.currentPathIndex, car.speed)?.let { (newLoc, newIndex) ->
                return@map car.copy(currentLocation = newLoc, currentPathIndex = newIndex)
            } ?: car
        }
        _simulationState.update { it.copy(cars = updatedCars) }
    }

    private fun moveEntity(current: MapLocation, path: List<MapLocation>, index: Int, speed: Double): Pair<MapLocation, Int>? {
        if (index >= path.size) return null
        val target = path[index]
        val dx = target.longitude - current.longitude
        val dy = target.latitude - current.latitude

        if (hypot(dx, dy) < speed) return Pair(current, index + 1)

        val angle = atan2(dy, dx)
        return Pair(MapLocation(current.latitude + sin(angle) * speed, current.longitude + cos(angle) * speed), index)
    }

    private fun updateNpcInState(id: String, newPath: List<MapLocation>) {
        _simulationState.update { state ->
            state.copy(npcs = state.npcs.map { if (it.id == id) it.copy(path = newPath, currentPathIndex = 0, isPlanningRoute = false) else it })
        }
    }

    private fun updateCarInState(id: String, newPath: List<MapLocation>) {
        _simulationState.update { state ->
            state.copy(cars = state.cars.map { if (it.id == id) it.copy(path = newPath, currentPathIndex = 0, isPlanningRoute = false) else it })
        }
    }

    private fun getRandomNearbyPoint(origin: MapLocation, radius: Double): MapLocation {
        return MapLocation(
            origin.latitude + Random.nextDouble(-radius, radius),
            origin.longitude + Random.nextDouble(-radius, radius)
        )
    }

    companion object {
        const val TICK_RATE_MS = 33L // ~30 FPS
        const val NPC_COUNT = 8
        const val CAR_COUNT = 5
        const val SPAWN_RADIUS_NPC = 0.002
        const val SPAWN_RADIUS_CAR = 0.004
        const val WANDER_RADIUS_NPC = 0.0015
        const val WANDER_RADIUS_CAR = 0.004
    }
}