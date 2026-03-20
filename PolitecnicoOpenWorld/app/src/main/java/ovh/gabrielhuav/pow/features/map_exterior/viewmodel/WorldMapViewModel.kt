package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.osmdroid.util.GeoPoint

enum class Direction { UP, DOWN, LEFT, RIGHT }
enum class GameAction { A, B, X, Y } // Nuevos botones

class WorldMapViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(WorldMapState())
    val uiState: StateFlow<WorldMapState> = _uiState.asStateFlow()

    fun updateInitialLocation(latitude: Double, longitude: Double) {
        if (_uiState.value.isLoadingLocation) {
            _uiState.value = _uiState.value.copy(
                currentLocation = GeoPoint(latitude, longitude),
                isLoadingLocation = false
            )
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

    // Función para generar policías en puntos aleatorios cerca del jugador
    private fun spawnRandomPolice(center: GeoPoint) {
        val newPolice = (1..5).map { i ->
            PoliceNPC(
                id = "POLICE_$i",
                location = GeoPoint(
                    center.latitude + (Math.random() - 0.5) * 0.005,
                    center.longitude + (Math.random() - 0.5) * 0.005
                )
            )
        }
        _uiState.value = _uiState.value.copy(policeNPCs = newPolice)
    }

    // Maneja el incremento de estrellas (30s de visión)
    private fun incrementSearchTimer(limit: Int) {
        val currentState = _uiState.value

        // Si es la primera vez que nos ven (nivel 0), subimos a nivel 1 inmediatamente
        if (currentState.searchLevel == 0) {
            _uiState.value = currentState.copy(
                searchLevel = 1,
                timerSeconds = 0
            )
            return
        }

        // Para niveles superiores al 1, mantenemos la acumulación de tiempo
        val currentTimer = currentState.timerSeconds + 1
        if (currentTimer >= limit) {
            val newLevel = (currentState.searchLevel + 1).coerceAtMost(5)
            _uiState.value = currentState.copy(
                searchLevel = newLevel,
                timerSeconds = 0
            )
        } else {
            _uiState.value = currentState.copy(timerSeconds = currentTimer)
        }
    }

    // Maneja el enfriamiento (60s fuera de visión)
    private fun decrementSearchTimer(limit: Int) {
        val currentTimer = _uiState.value.timerSeconds + 1
        if (currentTimer >= limit) {
            _uiState.value = _uiState.value.copy(
                searchLevel = 0, // Se pierden todas las estrellas
                timerSeconds = 0
            )
        } else {
            _uiState.value = _uiState.value.copy(timerSeconds = currentTimer)
        }
    }

    // Lógica principal que debes llamar desde un Game Loop o un LaunchedEffect
    fun updatePoliceSystem() {
        val playerLoc = _uiState.value.currentLocation ?: return
        val inVehicle = _uiState.value.isInVehicle

        // 1. Generar si no existen
        if (_uiState.value.policeNPCs.isEmpty()) {
            spawnRandomPolice(playerLoc)
        }

        // 2. Actualizar cada policía
        val updatedPolice = _uiState.value.policeNPCs.map { police ->
            val distance = police.location.distanceToAsDouble(playerLoc)
            val isVisible = distance < 70.0 // Rango de visión de 70 metros

            if (inVehicle && isVisible) {
                // LÓGICA DE PERSECUCIÓN: Se mueve hacia el jugador
                val newLoc = moveTowards(police.location, playerLoc, 0.0001) // Velocidad de persecución
                police.copy(location = newLoc, isChasing = true)
            } else {
                // MOVIMIENTO CONSTANTE (Patrullaje aleatorio suave)
                val idleLoc = GeoPoint(
                    police.location.latitude + (Math.random() - 0.5) * 0.00005,
                    police.location.longitude + (Math.random() - 0.5) * 0.00005
                )
                police.copy(location = idleLoc, isChasing = false)
            }
        }

        // 3. Actualizar temporizadores de estrellas
        val anyPoliceChasing = updatedPolice.any { it.isChasing }
        if (anyPoliceChasing) {
            incrementSearchTimer(30)
        } else if (_uiState.value.searchLevel > 0) {
            decrementSearchTimer(60)
        }

        _uiState.value = _uiState.value.copy(policeNPCs = updatedPolice)
    }

    // Función auxiliar para calcular el movimiento hacia el objetivo
    private fun moveTowards(current: GeoPoint, target: GeoPoint, speed: Double): GeoPoint {
        val latDiff = target.latitude - current.latitude
        val lonDiff = target.longitude - current.longitude
        val angle = Math.atan2(latDiff, lonDiff)

        return GeoPoint(
            current.latitude + Math.sin(angle) * speed,
            current.longitude + Math.cos(angle) * speed
        )
    }
}