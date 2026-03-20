package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.NpcModel
import ovh.gabrielhuav.pow.domain.NpcType
import java.net.URL
import kotlin.math.atan2

enum class Direction { UP, DOWN, LEFT, RIGHT }
enum class GameAction { A, B, X, Y }

class WorldMapViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(WorldMapState())
    val uiState: StateFlow<WorldMapState> = _uiState.asStateFlow()

    fun updateInitialLocation(latitude: Double, longitude: Double) {
        if (_uiState.value.isLoadingLocation) {
            val startPoint = GeoPoint(latitude, longitude)
            _uiState.value = _uiState.value.copy(
                currentLocation = startPoint,
                isLoadingLocation = false
            )
            initNpcs(startPoint)
        }
    }

    fun moveCharacter(direction: Direction) {
        val current = _uiState.value.currentLocation ?: return
        val step = 0.000015

        val newLocation = when (direction) {
            Direction.UP -> GeoPoint(current.latitude + step, current.longitude)
            Direction.DOWN -> GeoPoint(current.latitude - step, current.longitude)
            Direction.LEFT -> GeoPoint(current.latitude, current.longitude - step)
            Direction.RIGHT -> GeoPoint(current.latitude, current.longitude + step)
        }
        _uiState.value = _uiState.value.copy(currentLocation = newLocation)
    }

    fun executeAction(action: GameAction) {
        println("Acción ejecutada: $action")
    }

    private fun initNpcs(centerLocation: GeoPoint) {
        // Ya NO agregamos los autos al estado aquí.
        // Solo definimos de dónde a dónde queremos que vayan y qué dibujo usarán.

        // Auto 1: Lejos al Norte (Usa imagen 1)
        val start1 = GeoPoint(centerLocation.latitude + 0.002, centerLocation.longitude + 0.002)
        val end1 = GeoPoint(centerLocation.latitude + 0.005, centerLocation.longitude + 0.004)
        setupCarRoute("car_1", 1, start1, end1)

        // Auto 2: Lejos al Sur (Usa imagen 2)
        val start2 = GeoPoint(centerLocation.latitude - 0.003, centerLocation.longitude - 0.001)
        val end2 = GeoPoint(centerLocation.latitude - 0.006, centerLocation.longitude - 0.002)
        setupCarRoute("car_2", 2, start2, end2)

        // Auto 3: Lejos al Oeste (Usa imagen 1)
        val start3 = GeoPoint(centerLocation.latitude + 0.001, centerLocation.longitude - 0.004)
        val end3 = GeoPoint(centerLocation.latitude - 0.001, centerLocation.longitude - 0.007)
        setupCarRoute("car_3", 1, start3, end3)
    }

    private fun setupCarRoute(carId: String, spriteType: Int, startPoint: GeoPoint, endPoint: GeoPoint) {
        viewModelScope.launch {
            val route = fetchRouteFromOSRM(startPoint, endPoint)

            // Solo si OSRM encontró una calle real, hacemos aparecer el auto
            if (route.isNotEmpty()) {
                // 1. El auto nace EXACTAMENTE en el asfalto (route[0])
                val newCar = NpcModel(
                    id = carId,
                    position = route[0],
                    type = NpcType.CAR,
                    rotation = 0f,
                    spriteType = spriteType
                )

                // 2. Lo agregamos al mapa
                val currentList = _uiState.value.npcs.toMutableList()
                currentList.add(newCar)
                _uiState.value = _uiState.value.copy(npcs = currentList)

                // 3. Empieza a moverse
                startCarMovement(carId, route)
            }
        }
    }

    private fun startCarMovement(npcId: String, routePoints: List<GeoPoint>) {
        if (routePoints.size < 2) return

        viewModelScope.launch {
            var currentTargetIndex = 1

            while (true) {
                val targetPoint = routePoints[currentTargetIndex]
                val currentNpcs = _uiState.value.npcs.toMutableList()
                val npcIndex = currentNpcs.indexOfFirst { it.id == npcId }

                if (npcIndex != -1) {
                    val currentPos = currentNpcs[npcIndex].position

                    // === MAGIA DE ROTACIÓN NATIVA ===
                    // osmdroid calcula la brújula perfecta hacia el destino
                    val trueBearing = currentPos.bearingTo(targetPoint).toFloat()

                    // Ajuste visual: Si tu dibujo del coche apunta hacia la DERECHA,
                    // déjalo en + 90f. Si apunta hacia ARRIBA, ponle + 0f.
                    val angleOffset = + 90f
                    val finalAngle = trueBearing + angleOffset

                    // === MOVIMIENTO ===
                    val fraction = 0.02
                    val newLat = currentPos.latitude + (targetPoint.latitude - currentPos.latitude) * fraction
                    val newLon = currentPos.longitude + (targetPoint.longitude - currentPos.longitude) * fraction
                    val newPosition = GeoPoint(newLat, newLon)

                    currentNpcs[npcIndex] = currentNpcs[npcIndex].copy(
                        position = newPosition,
                        rotation = finalAngle
                    )

                    _uiState.value = _uiState.value.copy(npcs = currentNpcs)

                    // === DETECTAR LLEGADA ===
                    val latDiff = Math.abs(targetPoint.latitude - newLat)
                    val lonDiff = Math.abs(targetPoint.longitude - newLon)
                    if (latDiff < 0.00005 && lonDiff < 0.00005) {
                        currentTargetIndex++
                        if (currentTargetIndex >= routePoints.size) {
                            delay(1000) // Pausa al final de la calle

                            // Teletransporte silencioso al inicio
                            val startPoint = routePoints[0]
                            val resetNpcs = _uiState.value.npcs.toMutableList()
                            val rIndex = resetNpcs.indexOfFirst { it.id == npcId }
                            if (rIndex != -1) {
                                resetNpcs[rIndex] = resetNpcs[rIndex].copy(position = startPoint)
                                _uiState.value = _uiState.value.copy(npcs = resetNpcs)
                            }
                            currentTargetIndex = 1
                            delay(1000)
                            continue
                        }
                    }
                }
                delay(50)
            }
        }
    }

    private suspend fun fetchRouteFromOSRM(start: GeoPoint, end: GeoPoint): List<GeoPoint> {
        return withContext(Dispatchers.IO) {
            val urlString = "https://router.project-osrm.org/route/v1/driving/${start.longitude},${start.latitude};${end.longitude},${end.latitude}?overview=full&geometries=geojson"
            val result = mutableListOf<GeoPoint>()

            try {
                val response = URL(urlString).readText()
                val jsonResponse = JSONObject(response)
                val routes = jsonResponse.getJSONArray("routes")

                if (routes.length() > 0) {
                    val geometry = routes.getJSONObject(0).getJSONObject("geometry")
                    val coordinates = geometry.getJSONArray("coordinates")

                    for (i in 0 until coordinates.length()) {
                        val coord = coordinates.getJSONArray(i)
                        val lon = coord.getDouble(0)
                        val lat = coord.getDouble(1)
                        result.add(GeoPoint(lat, lon))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                println("Error al obtener la ruta de OSM")
            }
            result
        }
    }
}