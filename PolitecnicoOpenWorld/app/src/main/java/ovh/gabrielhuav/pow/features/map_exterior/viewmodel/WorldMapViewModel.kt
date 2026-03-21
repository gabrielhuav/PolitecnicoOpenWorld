package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.Npc
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

enum class Direction { UP, DOWN, LEFT, RIGHT }
enum class GameAction { A, B, X, Y }

class WorldMapViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(WorldMapState())
    val uiState: StateFlow<WorldMapState> = _uiState.asStateFlow()

    private var isGameLoopRunning = false

    fun updateInitialLocation(latitude: Double, longitude: Double) {
        if (_uiState.value.isLoadingLocation) {
            _uiState.value = _uiState.value.copy(
                currentLocation = GeoPoint(latitude, longitude),
                isLoadingLocation = false
            )

            // 1. Spawneamos al NPC cerca de tu ubicación real (no en ESCOM)
            spawnNpcsNearPlayer(latitude, longitude)

            // 2. Iniciamos el motor de movimiento
            if (!isGameLoopRunning) {
                startGameLoop()
                isGameLoopRunning = true
            }
        }
    }

    private fun spawnNpcsNearPlayer(lat: Double, lon: Double) {
        val numberOfNpcs = 8 // CAMBIAR PARA NUMERO DE NPCS DESEADOS
        val newNpcs = mutableListOf<Npc>()

        for (i in 1..numberOfNpcs) {
            // Calculamos un desfase aleatorio para que aparezcan esparcidos a tu alrededor
            // 0.002 grados son aprox 200 metros.
            val randomLat = lat + (Math.random() - 0.5) * 0.002
            val randomLon = lon + (Math.random() - 0.5) * 0.002

            newNpcs.add(
                Npc(
                    id = "npc_$i",
                    name = "Estudiante $i",
                    currentLocation = GeoPoint(randomLat, randomLon),
                    path = emptyList(),
                    currentPathIndex = 0,
                    speed = 0.000003, // Caminata lenta
                    isPlanningRoute = false
                )
            )
        }

        // Actualizamos la lista completa en el estado
        _uiState.update { it.copy(npcs = newNpcs) }
    }

    private fun startGameLoop() {
        viewModelScope.launch {
            // Se ejecuta a ~30 FPS para interpolación fluida
            while (isActive) {
                delay(33)
                updateNpcsPosition()
            }
        }
    }

    private fun updateNpcsPosition() {
        val currentState = _uiState.value
        val updatedNpcs = currentState.npcs.map { npc ->

            // 1. IA: Si el NPC no tiene ruta o ya la terminó, pedir una nueva a OSRM
            if ((npc.path.isEmpty() || npc.currentPathIndex >= npc.path.size) && !npc.isPlanningRoute) {
                val thinkingNpc = npc.copy(isPlanningRoute = true)

                viewModelScope.launch {
                    val randomDestination = getRandomNearbyPoint(thinkingNpc.currentLocation)
                    val newPath = fetchPedestrianRoute(thinkingNpc.currentLocation, randomDestination)

                    _uiState.update { state ->
                        val newNpcs = state.npcs.map { n ->
                            if (n.id == thinkingNpc.id) {
                                n.copy(
                                    path = newPath,
                                    currentPathIndex = 0,
                                    isPlanningRoute = false
                                )
                            } else n
                        }
                        state.copy(npcs = newNpcs)
                    }
                }
                return@map thinkingNpc // Se queda quieto mientras la red responde
            }

            // 2. Si está "pensando" (esperando internet), no se mueve
            if (npc.isPlanningRoute || npc.path.isEmpty()) return@map npc

            // 3. Movimiento matemático fluido hacia el siguiente nodo
            val targetNode = npc.path[npc.currentPathIndex]
            val dx = targetNode.longitude - npc.currentLocation.longitude
            val dy = targetNode.latitude - npc.currentLocation.latitude
            val distance = hypot(dx, dy)

            // Si llegó al nodo actual, pasa al siguiente nodo de la banqueta
            if (distance < npc.speed) {
                return@map npc.copy(currentPathIndex = npc.currentPathIndex + 1)
            }

            // Caminar en línea recta hacia el nodo
            val angle = atan2(dy, dx)
            val newLat = npc.currentLocation.latitude + (sin(angle) * npc.speed)
            val newLon = npc.currentLocation.longitude + (cos(angle) * npc.speed)

            npc.copy(currentLocation = GeoPoint(newLat, newLon))
        }

        _uiState.value = currentState.copy(npcs = updatedNpcs)
    }

    // --- LÓGICA DE INTELIGENCIA ARTIFICIAL (IA) DEL NPC ---

    private suspend fun fetchPedestrianRoute(start: GeoPoint, end: GeoPoint): List<GeoPoint> {
        return withContext(Dispatchers.IO) {
            val path = mutableListOf<GeoPoint>()
            try {
                // OSRM: longitud,latitud
                val urlString = "https://router.project-osrm.org/route/v1/foot/${start.longitude},${start.latitude};${end.longitude},${end.latitude}?overview=full&geometries=geojson"
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val jsonObject = JSONObject(response)
                    val routes = jsonObject.getJSONArray("routes")

                    if (routes.length() > 0) {
                        val geometry = routes.getJSONObject(0).getJSONObject("geometry")
                        val coordinates = geometry.getJSONArray("coordinates")

                        for (i in 0 until coordinates.length()) {
                            val coord = coordinates.getJSONArray(i)
                            val lon = coord.getDouble(0)
                            val lat = coord.getDouble(1)
                            path.add(GeoPoint(lat, lon))
                        }
                    }
                }
            } catch (e: Exception) {
                println("Error al obtener la ruta del NPC: ${e.message}")
            }
            path
        }
    }

    private fun getRandomNearbyPoint(origin: GeoPoint, radiusInDegrees: Double = 0.0015): GeoPoint {
        val randomLat = origin.latitude + Random.nextDouble(-radiusInDegrees, radiusInDegrees)
        val randomLon = origin.longitude + Random.nextDouble(-radiusInDegrees, radiusInDegrees)
        return GeoPoint(randomLat, randomLon)
    }

    // --- CONTROLES DEL JUGADOR ---

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

    fun toggleSettingsDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showSettingsDialog = show)
    }

    fun toggleMapProvider() {
        val currentProvider = _uiState.value.mapProvider
        val newProvider = if (currentProvider == MapProvider.OSM) MapProvider.GOOGLE else MapProvider.OSM
        _uiState.value = _uiState.value.copy(mapProvider = newProvider)
    }
}
