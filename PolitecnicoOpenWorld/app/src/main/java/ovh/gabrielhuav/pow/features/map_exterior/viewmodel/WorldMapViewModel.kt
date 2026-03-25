package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.osmdroid.util.GeoPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class Direction { UP, DOWN, LEFT, RIGHT }
enum class GameAction { A, B, X, Y }

class WorldMapViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(WorldMapState())
    val uiState: StateFlow<WorldMapState> = _uiState.asStateFlow()

    // Variable para controlar el ciclo del juego y evitar fugas de memoria
    private var metabolismJob: Job? = null

    init {
        startMetabolismLoop() // Arranca el reloj en cuanto el ViewModel nace
    }

    companion object {
        // Configuraciones de "Metabolismo" del jugador (Fáciles de modificar)
        private const val METABOLISM_TICK_MS = 5000L // El tiempo pasa cada 5 segundos
        private const val HUNGER_TIME_COST = 0.0005f    // Pierde 0.05% de hambre por tiempo

        // NUEVO: Velocidad normal de caminata
        private const val BASE_MOVEMENT_STEP = 0.000015
        // NUEVO: Daño por inanición (Pierde 0.5% de vida por cada "golpe" de hambre)
        private const val STARVATION_DAMAGE = 0.005f
    }


    fun updateInitialLocation(latitude: Double, longitude: Double) {
        if (_uiState.value.isLoadingLocation) {
            _uiState.value = _uiState.value.copy(
                currentLocation = GeoPoint(latitude, longitude),
                isLoadingLocation = false
            )
        }
    }

    // Responsabilidad: Modificar el estado matemático y aplicar reglas de negocio
    private fun depleteHunger(amount: Float) {
        _uiState.update { currentState ->
            val newHunger = (currentState.hunger - amount).coerceIn(0f, 1f)

            // Si el hambre llega a cero, empezamos a restar salud
            val newHealth = if (newHunger == 0f) {
                (currentState.health - STARVATION_DAMAGE).coerceIn(0f, 1f)
            } else {
                currentState.health // Si aún tiene hambre, la salud se queda igual
            }

            currentState.copy(
                hunger = newHunger,
                health = newHealth
            )
        }
    }

    fun moveCharacter(direction: Direction) {
        val current = _uiState.value.currentLocation ?: return
        val currentHunger = _uiState.value.hunger // Leemos cuánta hambre tiene

        // Calculamos la velocidad: Si tiene hambre, camina a la mitad de velocidad
        val step = if (currentHunger <= 0.2f) {
            BASE_MOVEMENT_STEP / 2.0 // Fatigado
        } else {
            BASE_MOVEMENT_STEP       // Normal
        }

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

    fun setMapProvider(provider: MapProvider) {
        _uiState.value = _uiState.value.copy(
            mapProvider = provider
        )
    }

    fun zoomIn() {
        val currentZoom = _uiState.value.zoomLevel
        if (currentZoom < 22.0) {
            _uiState.value = _uiState.value.copy(zoomLevel = currentZoom + 1.0)
        }
    }

    fun zoomOut() {
        val currentZoom = _uiState.value.zoomLevel
        if (currentZoom > 2.0) {
            _uiState.value = _uiState.value.copy(zoomLevel = currentZoom - 1.0)
        }
    }
    // Se agregan 3 funciones para la vida y habre de las barras.
    fun takeDamage(amount: Float) {
        _uiState.update { currentState ->
            currentState.copy(health = (currentState.health - amount).coerceIn(0f, 1f))
        }
    }

    fun consumeEnergy(amount: Float) {
        _uiState.update { currentState ->
            currentState.copy(hunger = (currentState.hunger - amount).coerceIn(0f, 1f))
        }
    }

    fun eatFood() {
        // Por ejemplo, recuperar 30% de hambre al comer unas gorditas afuera de ESCOM
        _uiState.update { currentState ->
            currentState.copy(hunger = (currentState.hunger + 0.3f).coerceIn(0f, 1f))
        }
    }

    // Responsabilidad: Mantener el ciclo de tiempo corriendo de forma segura
    private fun startMetabolismLoop() {
        // Si ya hay un reloj corriendo, no hacemos nada (evita duplicados)
        if (metabolismJob?.isActive == true) return

        metabolismJob = viewModelScope.launch {
            while (isActive) { // Mientras el ViewModel exista y la app esté abierta
                delay(METABOLISM_TICK_MS)
                depleteHunger(HUNGER_TIME_COST)
            }
        }
    }

}