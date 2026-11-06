package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import android.util.Log
import kotlinx.coroutines.flow.update
import ovh.gabrielhuav.pow.BuildConfig
import ovh.gabrielhuav.pow.domain.models.geo.GeoPoint
import ovh.gabrielhuav.pow.domain.models.map.CarModel
import ovh.gabrielhuav.pow.domain.models.map.Npc
import ovh.gabrielhuav.pow.domain.models.map.NpcType
import ovh.gabrielhuav.pow.domain.models.map.VehicleFootprint
import ovh.gabrielhuav.pow.shared.recursos.Res
import ovh.gabrielhuav.pow.shared.recursos.wm_vehicle_exit_blocked
import java.util.UUID

/** Mantiene coche y avatar en posiciones separadas al abandonar un vehículo. */
internal fun WorldMapViewModel.exitVehicle(forced: Boolean): Boolean {
    val state = _uiState.value
    if (!state.isDriving) return false
    val vehicleCenter = state.currentLocation ?: return false
    val vehicleModel = state.currentVehicleModel ?: CarModel.SEDAN
    val vehicle = VehicleFootprint(vehicleCenter, state.vehicleRotation, vehicleModel.collisionDimensions)
    val candidates = if (forced) {
        forcedVehicleExitCandidates(vehicleCenter, state.vehicleRotation, vehicleModel.collisionDimensions)
    } else {
        vehicleExitCandidates(vehicleCenter, state.vehicleRotation, vehicleModel.collisionDimensions)
    }
    val exitPoint = selectVehicleExit(candidates) { candidate ->
        isVehicleExitWalkable(candidate, vehicleCenter, vehicle)
    }
    if (exitPoint == null) {
        logVehicleCollision("exit-blocked")
        if (!forced) {
            avisarConTitulo("", Res.string.wm_vehicle_exit_blocked)
        }
        return false
    }

    val abandonedCar = Npc(
        id = UUID.randomUUID().toString(),
        type = NpcType.CAR,
        location = vehicleCenter,
        rotationAngle = (state.vehicleRotation + 270f) % 360f,
        speed = 0.0,
        isMoving = false,
        carModel = vehicleModel,
        carColor = state.currentVehicleColor ?: 0xFFFFFFFF.toInt(),
        isFirstTimeBoarded = state.vehicleIsFirstTimeBoarded,
        isPoliceSkin = state.isDrivingPoliceCar,
        navState = if (isInsideEscom(vehicleCenter.latitude, vehicleCenter.longitude)) {
            ovh.gabrielhuav.pow.domain.models.map.NpcNavState.PARKED
        } else {
            ovh.gabrielhuav.pow.domain.models.map.NpcNavState.MACRO_OSM
        },
    )
    remoteEntities[abandonedCar.id] = abandonedCar
    _uiState.update {
        it.copy(
            currentLocation = exitPoint,
            isDriving = false,
            currentVehicleModel = null,
            currentVehicleColor = null,
            vehicleSpeed = 0.0,
            vehicleIsFirstTimeBoarded = true,
            isDrivingPoliceCar = false,
            prankedyBoarding = false,
        )
    }
    logVehicleVisualTransition(if (forced) "forced-exit" else "exit-vehicle")
    prankedyManager.onVehicleInteraction()
    updateNpcsState()
    return true
}

private fun WorldMapViewModel.isVehicleExitWalkable(
    candidate: GeoPoint,
    vehicleCenter: GeoPoint,
    vehicle: VehicleFootprint,
): Boolean {
    if (vehicle.contains(candidate)) return false
    if (isCollisionDetected(vehicleCenter.latitude, vehicleCenter.longitude, candidate.latitude, candidate.longitude)) return false
    return worldVehicleColliders().none { collider ->
        collider.footprint.intersectsCircle(candidate, PLAYER_FOOT_RADIUS_METERS)
    }
}

internal fun WorldMapViewModel.logVehicleCollision(reason: String) {
    if (!BuildConfig.DEBUG) return
    val state = _uiState.value
    Log.d("VehicleCollision", "reason=$reason loc=${state.currentLocation} rotation=${state.vehicleRotation}")
}
