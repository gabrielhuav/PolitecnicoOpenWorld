package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.NpcModel
import ovh.gabrielhuav.pow.domain.NpcType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

enum class Direction { UP, DOWN, LEFT, RIGHT }
enum class GameAction { A, B, X, Y } // Nuevos botones

class WorldMapViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(WorldMapState())
    val uiState: StateFlow<WorldMapState> = _uiState.asStateFlow()

    fun updateInitialLocation(latitude: Double, longitude: Double) {
        if (_uiState.value.isLoadingLocation) {
            val startPoint = GeoPoint(latitude, longitude)
            _uiState.value = _uiState.value.copy(
                currentLocation = GeoPoint(latitude, longitude),
                isLoadingLocation = false
            )

            initNpcs(startPoint)
        }
    }

    fun moveCharacter(direction: Direction) {
        val current = _uiState.value.currentLocation ?: return

        // Reducimos el paso para simular caminar suavemente
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
        // Por ahora solo imprimimos en consola, aquí irá la lógica de interactuar, correr, etc.
        println("Acción ejecutada: $action")
    }


    private fun initNpcs(centerLocation: GeoPoint) {
        val car1 = NpcModel(
            id = "car_1",
            position = GeoPoint(centerLocation.latitude + 0.0002, centerLocation.longitude + 0.0002),
            type = NpcType.CAR
        )

        _uiState.value = _uiState.value.copy(npcs = listOf(car1))

        // Iniciamos el proceso en segundo plano
        viewModelScope.launch {
            // Definimos un Punto A (cerca del jugador) y un Punto B (un poco más lejos)
            val startPoint = GeoPoint(centerLocation.latitude + 0.0002, centerLocation.longitude + 0.0002)
            val endPoint = GeoPoint(centerLocation.latitude + 0.003, centerLocation.longitude - 0.002)

            // 1. Pedimos a la API que calcule las calles reales entre esos dos puntos
            val realStreetRoute = fetchRouteFromOSRM(startPoint, endPoint)

            // 2. Si encontró una ruta válida, hacemos que el auto comience a moverse por ahí
            if (realStreetRoute.isNotEmpty()) {
                startCarMovement(car1.id, realStreetRoute)
            } else {
                println("No se encontró una calle para moverse")
            }
        }
    }

    private fun startCarMovement(npcId: String, routePoints: List<GeoPoint>) {
        // Iniciamos un proceso en segundo plano (Corrutina)
        viewModelScope.launch {
            var currentTargetIndex = 0

            // Bucle infinito para que el auto patrulle la ruta
            while (true) {
                val targetPoint = routePoints[currentTargetIndex]

                // 1. Encontrar el auto actual en nuestro estado
                val currentNpcs = _uiState.value.npcs.toMutableList()
                val npcIndex = currentNpcs.indexOfFirst { it.id == npcId }

                if (npcIndex != -1) {
                    val currentPos = currentNpcs[npcIndex].position

                    // 2. Mover el auto hacia el objetivo (Matemática simple de interpolación)
                    // Calculamos una fracción de la distancia para que el movimiento sea suave
                    val fraction = 0.1 // Mueve el 10% del camino por cada "tick"
                    val newLat = currentPos.latitude + (targetPoint.latitude - currentPos.latitude) * fraction
                    val newLon = currentPos.longitude + (targetPoint.longitude - currentPos.longitude) * fraction

                    val newPosition = GeoPoint(newLat, newLon)

                    // Actualizamos la posición del auto en la lista
                    currentNpcs[npcIndex] = currentNpcs[npcIndex].copy(position = newPosition)

                    // Guardamos la nueva lista en el estado para que la pantalla se redibuje
                    _uiState.value = _uiState.value.copy(npcs = currentNpcs)

                    // 3. Comprobar si ya llegamos al punto destino
                    // Si estamos muy cerca, pasamos al siguiente punto de la calle
                    val latDiff = Math.abs(targetPoint.latitude - newLat)
                    val lonDiff = Math.abs(targetPoint.longitude - newLon)
                    if (latDiff < 0.00005 && lonDiff < 0.00005) {
                        currentTargetIndex = (currentTargetIndex + 1) % routePoints.size
                    }
                }

                // Esperamos 50 milisegundos antes de dar el siguiente "paso" (esto equivale a ~20 FPS)
                delay(50)
            }
        }

    }
    private suspend fun fetchRouteFromOSRM(start: GeoPoint, end: GeoPoint): List<GeoPoint> {
        return withContext(Dispatchers.IO) {
            // Construimos la URL con las coordenadas (Ojo: OSRM pide primero Longitud y luego Latitud)
            val urlString = "https://router.project-osrm.org/route/v1/driving/${start.longitude},${start.latitude};${end.longitude},${end.latitude}?overview=full&geometries=geojson"
            val result = mutableListOf<GeoPoint>()

            try {
                // Descargamos el JSON de la ruta
                val response = URL(urlString).readText()
                val jsonResponse = JSONObject(response)
                val routes = jsonResponse.getJSONArray("routes")

                if (routes.length() > 0) {
                    val geometry = routes.getJSONObject(0).getJSONObject("geometry")
                    val coordinates = geometry.getJSONArray("coordinates")

                    // Extraemos cada punto de la calle
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