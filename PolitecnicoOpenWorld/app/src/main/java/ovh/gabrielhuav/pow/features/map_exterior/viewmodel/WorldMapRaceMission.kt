package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.ActiveCollectible
import ovh.gabrielhuav.pow.domain.models.EntrenadorLocation
import ovh.gabrielhuav.pow.domain.models.Race
import ovh.gabrielhuav.pow.domain.models.RaceCatalog
import kotlin.math.sqrt

// ─────────────────────────────────────────────────────────────────────────────
// MISIÓN SECUNDARIA: Carrera del Politécnico
// Extensiones del WorldMapViewModel. El estado persistente (raceTimerJob,
// raceRewardAlreadyGiven) vive en WorldMapViewModel.kt.
// ─────────────────────────────────────────────────────────────────────────────

/** Coloca el marker del Entrenador en activeCollectibles si aún no está. */
internal fun WorldMapViewModel.spawnEntrenadorMarker() {
    if (_uiState.value.activeCollectibles.any { it.id == EntrenadorLocation.MARKER_ID }) return
    val marker = ActiveCollectible(
        id          = EntrenadorLocation.MARKER_ID,
        name        = EntrenadorLocation.MARKER_NAME,
        description = "NPC especial: Entrenador Politécnico",
        assetPath   = EntrenadorLocation.MARKER_ASSET,
        latitude    = EntrenadorLocation.LAT,
        longitude   = EntrenadorLocation.LON
    )
    _uiState.update { it.copy(activeCollectibles = it.activeCollectibles + marker) }
}

/** Llamado desde handleInteraction() cuando el jugador pulsa X cerca del Entrenador. */
internal fun WorldMapViewModel.onInteractEntrenador() {
    if (_uiState.value.isRaceActive) return // ya hay carrera en curso
    _uiState.update { it.copy(navigateToRaceMission = true) }
}

/** Consume el flag de navegación a la pantalla de briefing. */
fun WorldMapViewModel.consumeNavigateToRaceMission() {
    _uiState.update { it.copy(navigateToRaceMission = false) }
}

/**
 * Inicia la carrera: arranca el timer de 60 s, coloca el marker de la meta en el mapa
 * y asegura que el coleccionable de trofeo esté sembrado en la BD.
 * Llamado desde RaceMissionScreen (vía MainActivity) cuando el jugador acepta y el
 * countdown termina.
 */
fun WorldMapViewModel.startRace(race: Race = RaceCatalog.default) {
    if (_uiState.value.isRaceActive) return

    // Guarda la carrera activa para que checkRaceFinishLine la pueda leer
    activeRace = race

    // Teletransporte suave al punto de salida (junto al Entrenador) sin recargar calles/mapa.
    _uiState.update { it.copy(
        currentLocation = GeoPoint(EntrenadorLocation.LAT, EntrenadorLocation.LON + 0.00006)
    )}

    // Inicializa el coleccionable trofeo si no existe todavía (operación IO, no bloquea)
    viewModelScope.launch(Dispatchers.IO) {
        collectibleRepository.ensureRaceCollectible()
    }

    // Agrega marker de la meta al mapa
    val finishMarker = ActiveCollectible(
        id          = EntrenadorLocation.FINISH_MARKER_ID,
        name        = EntrenadorLocation.FINISH_MARKER_NAME,
        description = "¡Llega aquí antes de que se acabe el tiempo! (${race.name})",
        assetPath   = EntrenadorLocation.FINISH_MARKER_ASSET,
        latitude    = race.finishLat,
        longitude   = race.finishLon
    )

    _uiState.update {
        it.copy(
            isRaceActive        = true,
            raceTimeLeftSec     = race.timeLimitSec,
            racePenaltyTotalSec = 0,
            showRaceVictory     = false,
            showRaceTimeout     = false,
            raceFinishedTimeSec = 0,
            activeCollectibles  = (it.activeCollectibles
                .filter { c -> c.id != EntrenadorLocation.FINISH_MARKER_ID }) + finishMarker
        )
    }

    // Waypoint en la meta para que el jugador vea la línea guía azul
    val finishGeo = GeoPoint(race.finishLat, race.finishLon)
    _uiState.update { it.copy(destinationMarker = finishGeo) }

    // Timer coroutine: descuenta segundo a segundo leyendo siempre el estado actual,
    // para que las penalizaciones (addRacePenalty) sean respetadas correctamente.
    raceTimerJob?.cancel()
    raceTimerJob = viewModelScope.launch(Dispatchers.Default) {
        while (isActive) {
            delay(1000L)
            if (!isActive) break
            val current = _uiState.value.raceTimeLeftSec
            if (current <= 0) {
                if (_uiState.value.isRaceActive) {
                    withContext(Dispatchers.Main) { onRaceTimeout() }
                }
                break
            }
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(raceTimeLeftSec = it.raceTimeLeftSec - 1) }
            }
        }
    }
}

/** Reinicia la carrera activa (retry) usando la misma Race que se seleccionó. */
fun WorldMapViewModel.retryRace() {
    val race = activeRace ?: RaceCatalog.default
    startRace(race)
}

/**
 * Penaliza al jugador con +5 segundos al contador efectivo.
 * Llamado desde WorldMapViewModel cuando el jugador golpea un NPC durante la carrera.
 */
fun WorldMapViewModel.addRacePenalty() {
    if (!_uiState.value.isRaceActive) return
    val newPenalty = _uiState.value.racePenaltyTotalSec + 5
    // El tiempo restante visible también sube (sigue subiendo, no puede pasar de 60)
    val newTimeLeft = (_uiState.value.raceTimeLeftSec + 5).coerceAtMost(99)
    _uiState.update { it.copy(racePenaltyTotalSec = newPenalty, raceTimeLeftSec = newTimeLeft) }
}

/**
 * Chequea cada tick si el jugador llegó a la meta.
 * Llamado desde WorldMapGameLoop cada frame.
 */
internal fun WorldMapViewModel.checkRaceFinishLine(location: GeoPoint) {
    if (!_uiState.value.isRaceActive) return
    val race = activeRace ?: return
    val dLat = location.latitude  - race.finishLat
    val dLon = location.longitude - race.finishLon
    val dist = sqrt(dLat * dLat + dLon * dLon)
    if (dist <= race.finishRadiusDeg) {
        onRaceVictory()
    }
}

/** El jugador llegó a la meta antes de que se agotara el tiempo. */
private fun WorldMapViewModel.onRaceVictory() {
    if (!_uiState.value.isRaceActive) return
    raceTimerJob?.cancel()
    raceTimerJob = null

    // Guardamos el tiempo sobrante (segundos que le quedaban al llegar a la meta)
    val timeRemaining = _uiState.value.raceTimeLeftSec
    val isFirstTime = !raceRewardAlreadyGiven

    cleanupRaceMarkers()

    _uiState.update {
        it.copy(
            isRaceActive        = false,
            showRaceVictory     = true,
            raceFinishedTimeSec = timeRemaining,
            raceFirstTimeWin    = isFirstTime,
            destinationMarker   = null
        )
    }

    if (isFirstTime) {
        raceRewardAlreadyGiven = true
        viewModelScope.launch(Dispatchers.IO) {
            collectibleRepository.claimCollectible(EntrenadorLocation.REWARD_COLLECTIBLE_ID)
        }
    }
}

/** El timer llegó a 0 sin que el jugador llegara. */
private fun WorldMapViewModel.onRaceTimeout() {
    if (!_uiState.value.isRaceActive) return
    raceTimerJob?.cancel()
    raceTimerJob = null
    cleanupRaceMarkers()
    _uiState.update {
        it.copy(
            isRaceActive      = false,
            showRaceTimeout   = true,
            destinationMarker = null
        )
    }
}

/** El jugador cierra el resultado (victoria o timeout) y reinicia la UI. */
fun WorldMapViewModel.dismissRaceResult() {
    _uiState.update { it.copy(showRaceVictory = false, showRaceTimeout = false) }
    // Re-spawneamos el Entrenador para que se pueda volver a intentar
    spawnEntrenadorMarker()
}

/** Cancela la carrera en curso (por salir al menú, etc.). */
fun WorldMapViewModel.cancelRace() {
    raceTimerJob?.cancel()
    raceTimerJob = null
    cleanupRaceMarkers()
    _uiState.update {
        it.copy(
            isRaceActive      = false,
            showRaceVictory   = false,
            showRaceTimeout   = false,
            destinationMarker = null
        )
    }
    spawnEntrenadorMarker()
}

private fun WorldMapViewModel.cleanupRaceMarkers() {
    _uiState.update { s ->
        s.copy(
            activeCollectibles = s.activeCollectibles.filter { c ->
                c.id != EntrenadorLocation.FINISH_MARKER_ID
            }
        )
    }
}
