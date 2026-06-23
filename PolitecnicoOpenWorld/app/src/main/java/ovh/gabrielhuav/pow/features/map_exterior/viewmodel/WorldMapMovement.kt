package ovh.gabrielhuav.pow.features.map_exterior.viewmodel


import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.osmdroid.util.GeoPoint
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import ovh.gabrielhuav.pow.data.cache.RoadNetworkCache
import ovh.gabrielhuav.pow.data.cache.TileCache
import ovh.gabrielhuav.pow.data.local.room.PowDatabase
import ovh.gabrielhuav.pow.data.network.WebSocketManager
import ovh.gabrielhuav.pow.data.repository.OverpassRepository
import ovh.gabrielhuav.pow.data.repository.SettingsRepository
import ovh.gabrielhuav.pow.domain.models.map.CarModel
import ovh.gabrielhuav.pow.domain.models.map.InteriorBuilding
import ovh.gabrielhuav.pow.domain.models.map.MapWay
import ovh.gabrielhuav.pow.domain.models.map.Npc
import ovh.gabrielhuav.pow.domain.models.map.NpcType
import ovh.gabrielhuav.pow.domain.models.ai.NpcAiManager
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction
import ovh.gabrielhuav.pow.features.settings.models.ControlType
import ovh.gabrielhuav.pow.data.local.room.entity.LandmarkEntity
import ovh.gabrielhuav.pow.domain.models.map.Landmark
import ovh.gabrielhuav.pow.domain.models.map.LandmarkCatalogManager
import ovh.gabrielhuav.pow.domain.models.map.LandmarkAssetTemplate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.abs
import ovh.gabrielhuav.pow.data.repository.CollectibleRepository
import ovh.gabrielhuav.pow.domain.models.map.ActiveCollectible
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.io.InputStreamReader
import ovh.gabrielhuav.pow.domain.models.ai.LandmarkNavGraph
import ovh.gabrielhuav.pow.domain.models.map.ShineCTOLocation
import ovh.gabrielhuav.pow.domain.models.map.ExteriorCollisionsConfig

// ─────────────────────────────────────────────────────────────────────────────
// Movimiento del JUGADOR a pie/joystick + aduana de colisión, extraído del VM.
// snap-to-road, zona libre (ESCOM/ENCB), rescate anti-atasco tras TP. El ESTADO
// (exteriorCollisions, RESCUE_MAX_DIST_DEG, MAX_SNAP_DISTANCE_DEG, roadNetwork…) sigue
// en el ViewModel. startMovementAction/getNearestPointOnNetwork/centerOnPlayer son extensiones.
// ─────────────────────────────────────────────────────────────────────────────

internal fun WorldMapViewModel.isCollisionDetected(oldLat: Double, oldLon: Double, newLat: Double, newLon: Double): Boolean {
        val config = exteriorCollisions ?: return false
        // A) Revisar si pisa un edificio
        for (poly in config.polygons) {
            if (poly.contains(newLat, newLon)) return true
        }
        // B) Revisar si choca con una barda
        for (wall in config.walls) {
            if (wall.didHitWall(oldLat, oldLon, newLat, newLon)) return true
        }
        return false
    }

internal fun WorldMapViewModel.moveCharacter(direction: Direction) {
        if (_uiState.value.showWastedScreen || _uiState.value.showMissionFailed) return // muerto/misión fallida: sin movimiento
        // Si el mapa está descentrado (exploración), el primer toque de los controles
        // de movimiento (izquierda) recentra en el jugador (SIN cambiar el zoom) en vez
        // de moverlo a ciegas fuera de cuadro.
        if (_uiState.value.isUserPanningMap) { centerOnPlayer(); return }
        val loc = _uiState.value.currentLocation ?: return
        if (!_uiState.value.isRoadNetworkReady || roadNetwork.isEmpty()) return
        val isMovingRight = when (direction) {
            Direction.RIGHT -> true
            Direction.LEFT -> false
            else -> null
        }
        startMovementAction(isMovingRight)

        val step = if (_uiState.value.isRunning) 0.000006 else 0.000003

        val temp = when (direction) {
            Direction.UP    -> GeoPoint(loc.latitude + step, loc.longitude)
            Direction.DOWN  -> GeoPoint(loc.latitude - step, loc.longitude)
            Direction.LEFT  -> GeoPoint(loc.latitude, loc.longitude - step)
            Direction.RIGHT -> GeoPoint(loc.latitude, loc.longitude + step)
        }

        // ADUANA DE CHOQUE A PIE
        if (isCollisionDetected(loc.latitude, loc.longitude, temp.latitude, temp.longitude)) {
            return // CHOCÓ: Rompemos la función y el jugador no avanza
        }

        // ZONA LIBRE (ESCOM / ENCB) o SOBRE UN ASSET/LANDMARK: se suspende la malla vial → el
        // JUGADOR se mueve libre en (x,y). (isOnLandmark es solo para ti; las calles siguen
        // visibles y los NPCs siguen atados a la malla.)
        if (isFreeMovementZone(temp.latitude, temp.longitude) || isOnLandmark(temp.latitude, temp.longitude)) {
            _uiState.update { it.copy(currentLocation = temp) }
            return
        }

        val nearest = getNearestPointOnNetwork(temp)
        val dist    = distance(temp, nearest)
        val radius  = 0.000012
        if (dist <= radius) {
            _uiState.update { it.copy(currentLocation = temp) }
        } else if (dist > MAX_SNAP_DISTANCE_DEG) {
            // FIX TP aleatorio: si la calle "más cercana" está LEJOS (la red recién
            // recargada aún no cubre esta zona, o candidates() cayó al fallback de
            // TODOS los segmentos), NO teletransportamos al jugador hacia ella.
            // Mejor no moverse este tick que aparecer en una calle al azar.
            // EXCEPCIÓN — RESCATE ANTI-ATASCO: si la posición ACTUAL del jugador TAMBIÉN está lejos de
            // toda calle, no es un glitch de recarga: está ATRAPADO fuera de la red (p. ej. TP a una
            // estación de metro que cae sobre un edificio). En vez de dejarlo inmóvil tras una "pared
            // invisible", al moverse lo ARRASTRAMOS hacia la calle más cercana hasta engancharlo.
            rescueIfStuckOffNetwork(loc, step)
            return
        } else {
            val angle = atan2(temp.latitude - nearest.latitude, temp.longitude - nearest.longitude)
            _uiState.update { it.copy(currentLocation = GeoPoint(
                nearest.latitude  + sin(angle) * radius,
                nearest.longitude + cos(angle) * radius
            ))}
        }
    }

    // RESCATE ANTI-ATASCO: si la posición ACTUAL está claramente fuera de la red vial (> MAX_SNAP),
    // mueve al jugador un paso (ampliado) HACIA la calle más cercana, o lo engancha si ya está a un
    // paso. Si NO está atorado (estaba sobre la calle, el guard saltó por un glitch), no hace nada.
    // Llamado desde moveCharacter*/sólo en la rama del guard anti-TP. Miembro único (sin gemelo).
    //
    // FIX "VOLAR" tras TP: justo tras teletransportarse, la red de la nueva zona aún se está descargando
    // y puede estar INCOMPLETA aunque `isRoadNetworkReady` ya sea true; entonces la "calle más cercana"
    // puede quedar a cientos de metros y el jugador PLANEABA hacia ella (volar). Por eso el rescate solo
    // actúa si esa calle está dentro de un radio RAZONABLE (`RESCUE_MAX_DIST_DEG` ~130 m): una estación
    // sobre un edificio SIEMPRE tiene calle cerca. Si está más lejos, NO movemos (esperamos a que cargue
    // la red); así nunca se vuela sobre una red a medio cargar.
internal fun WorldMapViewModel.rescueIfStuckOffNetwork(loc: GeoPoint, step: Double) {
        val curNearest = getNearestPointOnNetwork(loc)
        val d = distance(loc, curNearest)
        if (d <= MAX_SNAP_DISTANCE_DEG) return          // no está atorado: glitch → no mover
        if (d > RESCUE_MAX_DIST_DEG) return              // calle "cercana" demasiado lejos = red a medio cargar → esperar (no volar)
        val rescueStep = step * 4
        val landed = if (d <= rescueStep) curNearest else {
            val ang = atan2(curNearest.latitude - loc.latitude, curNearest.longitude - loc.longitude)
            GeoPoint(loc.latitude + sin(ang) * rescueStep, loc.longitude + cos(ang) * rescueStep)
        }
        _uiState.update { it.copy(currentLocation = landed) }
    }

internal fun WorldMapViewModel.moveCharacterByAngle(angleRad: Double) {
        if (_uiState.value.showWastedScreen || _uiState.value.showMissionFailed) return // muerto/misión fallida
        // Igual que moveCharacter: con el mapa descentrado, recentrar en el jugador (sin zoom).
        if (_uiState.value.isUserPanningMap) { centerOnPlayer(); return }
        val loc = _uiState.value.currentLocation ?: return
        if (!_uiState.value.isRoadNetworkReady || roadNetwork.isEmpty()) return

        val dx = cos(angleRad)
        val isMovingRight = if (abs(dx) > 0.01) dx > 0 else null
        startMovementAction(isMovingRight)

        val step = if (_uiState.value.isRunning) 0.000006 else 0.000003

        val temp = GeoPoint(
            loc.latitude + sin(angleRad) * step,
            loc.longitude + cos(angleRad) * step
        )

        // ADUANA DE CHOQUE JOYSTICK
        if (isCollisionDetected(loc.latitude, loc.longitude, temp.latitude, temp.longitude)) {
            return // CHOCÓ: Rompemos la función
        }

        // ZONA LIBRE (ESCOM / ENCB) o SOBRE UN ASSET/LANDMARK: se suspende la malla vial → el
        // JUGADOR se mueve libre en (x,y) (solo tú; las calles siguen visibles y los NPCs atados).
        if (isFreeMovementZone(temp.latitude, temp.longitude) || isOnLandmark(temp.latitude, temp.longitude)) {
            _uiState.update { it.copy(currentLocation = temp) }
            return
        }

        val nearest = getNearestPointOnNetwork(temp)
        val dist = distance(temp, nearest)
        val radius = 0.000012

        if (dist <= radius) {
            _uiState.update { it.copy(currentLocation = temp) }
        } else if (dist > MAX_SNAP_DISTANCE_DEG) {
            // FIX TP aleatorio (ver moveCharacter): no saltar a una calle lejana.
            // RESCATE ANTI-ATASCO (ver moveCharacter): si está ATRAPADO fuera de la red (TP de metro
            // sobre un edificio), al moverse lo arrastramos hacia la calle más cercana.
            rescueIfStuckOffNetwork(loc, step)
            return
        } else {
            val angle = atan2(temp.latitude - nearest.latitude, temp.longitude - nearest.longitude)
            _uiState.update { it.copy(currentLocation = GeoPoint(
                nearest.latitude + sin(angle) * radius,
                nearest.longitude + cos(angle) * radius
            ))}
        }
    }

