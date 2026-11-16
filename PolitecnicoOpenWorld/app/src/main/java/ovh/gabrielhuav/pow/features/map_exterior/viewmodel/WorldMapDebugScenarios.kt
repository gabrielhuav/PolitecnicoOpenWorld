package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.update
import ovh.gabrielhuav.pow.BuildConfig
import ovh.gabrielhuav.pow.domain.models.geo.GeoPoint
import ovh.gabrielhuav.pow.domain.models.map.CarModel
import ovh.gabrielhuav.pow.domain.models.map.Npc
import ovh.gabrielhuav.pow.domain.models.map.NpcNavState
import ovh.gabrielhuav.pow.domain.models.map.NpcType
import ovh.gabrielhuav.pow.domain.models.map.distanceMetersTo
import ovh.gabrielhuav.pow.domain.models.map.offsetMeters

// Escenarios SOLO de depuración (build debug) para probar el juego sin multitoque: `adb shell am broadcast
// -a ovh.gabrielhuav.pow.DEBUG_SCENARIO --es name <escenario> [--es gas 1 --es steer right ...]`.
// Se aplican con el mundo ya abierto. En release no hacen nada. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

private const val TAG = "DebugScenario"
private const val METERS_PER_DEGREE = 111_320.0

// Punto junto al lado este del techo central de ESCOM, de cara a él (DEV-08), y su rumbo.
private const val BUILDING_SPOT_LAT = 19.504536
private const val BUILDING_SPOT_LON = -99.146601
private const val BUILDING_SPOT_HEADING = 271.59583f

/** Aplica el escenario [name]; devuelve un texto con lo ocurrido (también va al logcat con la etiqueta DebugScenario). */
internal fun WorldMapViewModel.applyDebugScenario(name: String, extras: Map<String, String>): String {
    if (!BuildConfig.DEBUG) return "ignorado: solo build debug"
    val result = when (name) {
        "controls" -> "controles actualizados"
        "drive_open" -> debugDriveOpen(extras)
        "board_facing_building" -> debugBoardFacingBuilding()
        "steal_parked" -> debugStealParked()
        "overlay" -> debugOverlay(extras)
        else -> "escenario desconocido: $name (controls, drive_open, board_facing_building, steal_parked, overlay)"
    }
    applyDebugControls(extras)
    extras["trace"]?.toIntOrNull()?.let { traceDriving(it) }
    Log.d(TAG, "$name -> $result | gas=$isGasPressed steer=${steerLabel()} brake=$isBrakePressed driving=${_uiState.value.isDriving} rot=${_uiState.value.vehicleRotation} carjackText='$textoCarjack'")
    return result
}

private fun WorldMapViewModel.steerLabel(): String = when {
    isSteeringLeftPressed -> "left"
    isSteeringRightPressed -> "right"
    else -> "none"
}

/** gas=1|0, brake=1|0, steer=left|right|none: mantienen el control pulsado hasta que se cambie. */
private fun WorldMapViewModel.applyDebugControls(extras: Map<String, String>) {
    extras["gas"]?.let { isGasPressed = it == "1" }
    extras["brake"]?.let { isBrakePressed = it == "1" }
    extras["steer"]?.let {
        isSteeringLeftPressed = it == "left"
        isSteeringRightPressed = it == "right"
    }
}

private fun WorldMapViewModel.debugDriveOpen(extras: Map<String, String>): String {
    val spawn = _uiState.value.currentLocation ?: GeoPoint(BUILDING_SPOT_LAT, BUILDING_SPOT_LON)
    val lat = extras["lat"]?.toDoubleOrNull() ?: spawn.latitude
    val lon = extras["lon"]?.toDoubleOrNull() ?: spawn.longitude
    val heading = extras["heading"]?.toFloatOrNull() ?: 0f
    val speedPct = (extras["speedPct"]?.toDoubleOrNull() ?: 0.0).coerceIn(0.0, 100.0)
    _uiState.update {
        it.copy(
            currentLocation = GeoPoint(lat, lon),
            isLoadingLocation = false,
            isDriving = true,
            currentVehicleModel = CarModel.SEDAN,
            currentVehicleColor = 0xFFE53935.toInt(),
            vehicleRotation = heading,
            vehicleSpeed = MAX_SPEED * speedPct / 100.0,
            vehicleIsFirstTimeBoarded = false,
            isDrivingPoliceCar = false,
        )
    }
    return "conduciendo en ($lat, $lon) rumbo $heading velocidad $speedPct %"
}

/** Coloca un coche aparcado de cara al edificio y sube a él: comprueba el rumbo corregido al abordar (DEV-08). */
private fun WorldMapViewModel.debugBoardFacingBuilding(): String {
    val spot = GeoPoint(BUILDING_SPOT_LAT, BUILDING_SPOT_LON)
    val carRotation = (BUILDING_SPOT_HEADING - 90f + 360f) % 360f // al subir se le suma 90°
    val car = Npc(
        type = NpcType.CAR,
        location = spot,
        rotationAngle = carRotation,
        speed = 0.0,
        carModel = CarModel.SEDAN,
        navState = NpcNavState.PARKED,
    )
    remoteEntities[car.id] = car
    _uiState.update { it.copy(currentLocation = spot, isLoadingLocation = false, isDriving = false) }
    onInteractButtonPressed()
    return "abordado un coche aparcado de cara al edificio; rumbo de entrada $BUILDING_SPOT_HEADING, " +
        "rumbo resultante ${_uiState.value.vehicleRotation}"
}

/** Lleva al jugador junto al coche aparcado más cercano y lo roba (estrella y conductor desalojado). */
private fun WorldMapViewModel.debugStealParked(): String {
    val player = _uiState.value.currentLocation ?: return "sin ubicación del jugador"
    val target = remoteEntities.values
        .filter { it.type == NpcType.CAR && it.navState == NpcNavState.PARKED }
        .minByOrNull { it.location.distanceMetersTo(player) }
        ?: return "no hay coches aparcados cerca"
    _uiState.update {
        it.copy(currentLocation = target.location.offsetMeters(north = 1.5, east = 0.0), isDriving = false)
    }
    onInteractButtonPressed()
    return "robado el coche aparcado ${target.id}"
}

/** Activa el overlay "Debug Interiores" (formas de colisión) y, si hay lat/lon, mueve ahí al jugador a pie. */
private fun WorldMapViewModel.debugOverlay(extras: Map<String, String>): String {
    val lat = extras["lat"]?.toDoubleOrNull()
    val lon = extras["lon"]?.toDoubleOrNull()
    if (lat != null && lon != null) {
        _uiState.update { it.copy(currentLocation = GeoPoint(lat, lon), isLoadingLocation = false, isDriving = false) }
    }
    toggleInteriorDebugOverlay(extras["on"] != "0")
    return "overlay de colisiones ${if (extras["on"] != "0") "activado" else "desactivado"}"
}

/** trace=<segundos>: registra cada 250 ms posición, velocidad, rumbo y metros a la vía de coches más cercana. */
private fun WorldMapViewModel.traceDriving(seconds: Int) {
    viewModelScope.launch {
        repeat(seconds.coerceIn(1, 30) * 4) { i ->
            val s = _uiState.value
            val loc = s.currentLocation
            val roadM = loc?.let { distance(it, getNearestCarRoadPoint(it)) * METERS_PER_DEGREE }
            Log.d(TAG, "trace t=${i * 250}ms loc=${loc?.latitude},${loc?.longitude} speed=${s.vehicleSpeed} rot=${s.vehicleRotation} roadM=$roadM")
            delay(250)
        }
    }
}
