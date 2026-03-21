package ovh.gabrielhuav.pow.features.map_exterior.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.CarNpc
import ovh.gabrielhuav.pow.domain.models.Npc
import ovh.gabrielhuav.pow.features.map_exterior.data.RoutingRepository
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

// Clase para empaquetar las listas de entidades que se enviarán al ViewModel
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
        val newNpcs = (1..8).map { i ->
            Npc(
                id = "npc_$i", name = "Estudiante $i",
                currentLocation = GeoPoint(lat + (Math.random() - 0.5) * 0.002, lon + (Math.random() - 0.5) * 0.002),
                speed = 0.000003
            )
        }

        val newCars = (1..5).map { i ->
            CarNpc(
                id = "car_$i", name = "Auto $i",
                currentLocation = GeoPoint(lat + (Math.random() - 0.5) * 0.004, lon + (Math.random() - 0.5) * 0.004),
                speed = 0.000015
            )
        }

        _simulationState.update { it.copy(npcs = newNpcs, cars = newCars) }
    }

    fun startLoop() {
        if (isRunning) return
        isRunning = true
        scope.launch {
            while (isActive) {
                delay(33) // ~30 FPS
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
                    val destination = getRandomNearbyPoint(thinkingNpc.currentLocation, 0.0015)
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
                    val destination = getRandomNearbyPoint(thinkingCar.currentLocation, 0.004)
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

    // Matemáticas de movimiento genéricas
    private fun moveEntity(current: GeoPoint, path: List<GeoPoint>, index: Int, speed: Double): Pair<GeoPoint, Int>? {
        if (index >= path.size) return null
        val target = path[index]
        val dx = target.longitude - current.longitude
        val dy = target.latitude - current.latitude

        if (hypot(dx, dy) < speed) return Pair(current, index + 1)

        val angle = atan2(dy, dx)
        return Pair(GeoPoint(current.latitude + sin(angle) * speed, current.longitude + cos(angle) * speed), index)
    }

    private fun updateNpcInState(id: String, newPath: List<GeoPoint>) {
        _simulationState.update { state ->
            state.copy(npcs = state.npcs.map { if (it.id == id) it.copy(path = newPath, currentPathIndex = 0, isPlanningRoute = false) else it })
        }
    }

    private fun updateCarInState(id: String, newPath: List<GeoPoint>) {
        _simulationState.update { state ->
            state.copy(cars = state.cars.map { if (it.id == id) it.copy(path = newPath, currentPathIndex = 0, isPlanningRoute = false) else it })
        }
    }

    private fun getRandomNearbyPoint(origin: GeoPoint, radius: Double): GeoPoint {
        return GeoPoint(
            origin.latitude + Random.nextDouble(-radius, radius),
            origin.longitude + Random.nextDouble(-radius, radius)
        )
    }
}