package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

// ─────────────────────────────────────────────────────────────────────────────
// PARCIAL del WorldMapViewModel: NIVEL DE BÚSQUEDA + POLICÍA PROPIA + CARJACK.
// Extraído de WorldMapViewModel.kt en el refactor de tamaño. El estado
// (policeManager, lastCrimeTime, carjackStartTime, remotePolice…) sigue en el
// ViewModel; aquí solo hay lógica. NO duplicar estos nombres como miembros.
// ─────────────────────────────────────────────────────────────────────────────

import android.util.Log
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.CarModel
import ovh.gabrielhuav.pow.domain.models.Npc
import ovh.gabrielhuav.pow.domain.models.NpcType
import java.util.UUID

// Sube el nivel de búsqueda (con tope) y reinicia el contador de impunidad.
internal fun WorldMapViewModel.raiseWantedLevel(amount: Int = 1) {
    // En el APOCALIPSIS no aplica el sistema de delitos: pegarle a un zombi (o a un civil que
    // huye) NO debe invocar a la policía. La policía del apocalipsis es OTRA cosa (ayuda a los
    // civiles contra los zombis) — ver runPoliceTick.
    if (_uiState.value.globalZombieMode) return
    lastCrimeTime = System.currentTimeMillis()
    val current = _uiState.value.wantedLevel
    if (current < MAX_WANTED_LEVEL) {
        _uiState.update { it.copy(wantedLevel = (current + amount).coerceAtMost(MAX_WANTED_LEVEL)) }
    }
}

// Baja el nivel de búsqueda gradualmente cuando dejas de delinquir.
internal fun WorldMapViewModel.tickWantedDecay(now: Long) {
    val level = _uiState.value.wantedLevel
    if (level <= 0) return
    if (now - lastCrimeTime < WANTED_DECAY_GRACE_MS) return
    // Cuantas MÁS estrellas tengas, MÁS tarda en bajar cada una (el paso escala con el
    // nivel actual): 1★ baja en ~1×base, 5★ tarda ~5×base por estrella.
    if (now - lastWantedDecayTime < WANTED_DECAY_STEP_MS * level) return
    lastWantedDecayTime = now
    _uiState.update { it.copy(wantedLevel = (it.wantedLevel - 1).coerceAtLeast(0)) }
}

// ¿Hay algún NPC AGRESIVO (en embestida) pegado a tu coche?
internal fun WorldMapViewModel.anyAggressorAdjacent(location: GeoPoint, now: Long): Boolean {
    for (npc in remoteEntities.values) {
        if (npc.type != NpcType.PERSON) continue
        if (npc.aggroUntil <= now) continue
        if (distance(location, npc.location) <= CARJACK_ADJ_RADIUS) return true
    }
    return false
}

// Gestiona el aviso y el descenso forzado del vehículo.
internal fun WorldMapViewModel.handleCarjack(driving: Boolean, aggressorAdjacent: Boolean, now: Long) {
    if (!driving || !aggressorAdjacent) {
        if (carjackStartTime != 0L) {
            carjackStartTime = 0L
            if (_uiState.value.carjackWarning != null) {
                _uiState.update { it.copy(carjackWarning = null) }
            }
        }
        return
    }
    // Si vas acelerando (te mueves), no pueden bajarte: reinicia el contador.
    val movingFast = kotlin.math.abs(_uiState.value.vehicleSpeed) > MAX_SPEED * 0.25
    if (movingFast) {
        if (carjackStartTime != 0L) {
            carjackStartTime = 0L
            _uiState.update { it.copy(carjackWarning = null) }
        }
        return
    }
    if (carjackStartTime == 0L) carjackStartTime = now
    _uiState.update { it.copy(carjackWarning = "¡Te van a bajar del auto! ¡Acelera!") }
    if (now - carjackStartTime >= CARJACK_MS) {
        carjackStartTime = 0L
        _uiState.update { it.copy(carjackWarning = null) }
        viewModelScope.launch(Dispatchers.Main) { forceExitVehicle() }
    }
}

// Baja al jugador del coche a la fuerza (deja el auto abandonado en su sitio).
internal fun WorldMapViewModel.forceExitVehicle() {
    if (!_uiState.value.isDriving) return
    val loc = _uiState.value.currentLocation ?: return
    val abandonedCar = Npc(
        id = UUID.randomUUID().toString(),
        type = NpcType.CAR,
        location = loc,
        rotationAngle = (_uiState.value.vehicleRotation + 270f) % 360f,
        speed = 0.0,
        isMoving = false,
        carModel = _uiState.value.currentVehicleModel ?: CarModel.SEDAN,
        carColor = _uiState.value.currentVehicleColor ?: 0xFFFFFFFF.toInt(),
        isFirstTimeBoarded = _uiState.value.vehicleIsFirstTimeBoarded
    )
    remoteEntities[abandonedCar.id] = abandonedCar
    _uiState.update { it.copy(isDriving = false, currentVehicleModel = null, currentVehicleColor = null, vehicleSpeed = 0.0, vehicleIsFirstTimeBoarded = true) }
    updateNpcsState()
}

// Simula y difunde la policía PROPIA (el dueño del nivel de búsqueda). Se llama cada
// tick del game loop. También purga la policía remota que dejó de actualizarse.
internal fun WorldMapViewModel.runPoliceTick(location: GeoPoint) {
    val now = System.currentTimeMillis()
    tickWantedDecay(now)

    val wanted = _uiState.value.wantedLevel
    val driving = _uiState.value.isDriving
    val tick = policeManager.update(
        playerLat = location.latitude,
        playerLon = location.longitude,
        roadNetwork = roadNetwork,
        wantedLevel = wanted,
        canShoot = wanted >= 3,   // solo disparan con 3+ estrellas
        playerInVehicle = driving,
        now = now,
        snap = { gp -> getNearestPointOnNetwork(gp) },
        pathfind = { from, to -> findRoadRoute(from, to) }   // A* real por calles
    )

    // BALAS VISIBLES: guardamos los disparos nuevos con su timestamp y purgamos los
    // viejos (>280 ms), para dibujar un trazo breve desde el policía hacia ti.
    val prevShots = _uiState.value.policeShots
    val freshShots = if (tick.shots.isNotEmpty())
        tick.shots.map { PoliceShot(it.first, it.second, now) } else emptyList()
    if (freshShots.isNotEmpty() || prevShots.isNotEmpty()) {
        val kept = (prevShots + freshShots).filter { now - it.at <= 450L }
        if (kept != prevShots) _uiState.update { it.copy(policeShots = kept) }
    }

    // Daño que los policías te hacen (golpes/disparos). En coche NO te hacen daño
    // directo: te persiguen y, si te detienes, te bajan del vehículo (carjack).
    if (tick.damage > 0f && !driving) {
        viewModelScope.launch(Dispatchers.Main) { takeDamage(tick.damage) }
    }

    // CARJACK: si conduces y un perseguidor te alcanza, te avisa; si no aceleras (te
    // quedas casi quieto) durante CARJACK_MS, te bajan del coche. Aplica también a los
    // NPCs agresivos pegados a tu coche.
    val aggressorAdjacent = tick.adjacentThreat || (driving && anyAggressorAdjacent(location, now))
    handleCarjack(driving, aggressorAdjacent, now)

    // Purga de policía remota obsoleta (su dueño se alejó/desconectó).
    val staleCutoff = now - REMOTE_POLICE_STALE_MS
    val staleIds = remotePoliceSeen.filterValues { it < staleCutoff }.keys
    if (staleIds.isNotEmpty()) {
        staleIds.forEach { remotePolice.remove(it); remotePoliceSeen.remove(it) }
    }

    updateNpcsState()

    // Difusión a los demás clientes (para que vean mis patrullas/policías).
    // Throttle: los destroys siempre salen; el batch de posiciones, a ~8 Hz.
    val doBroadcastBatch = now - lastPoliceBroadcast >= POLICE_BROADCAST_MS
    if (doBroadcastBatch) lastPoliceBroadcast = now
    if (tick.destroyedIds.isEmpty() && (!doBroadcastBatch || tick.units.isEmpty())) return
    webSocketManager?.let { ws ->
        viewModelScope.launch(Dispatchers.IO) {
            try {
                tick.destroyedIds.forEach { pid ->
                    ws.sendMessage(gson.toJson(mapOf("type" to "POLICE_DESTROY", "npcId" to pid)))
                }
                if (doBroadcastBatch && tick.units.isNotEmpty()) {
                    val batch = tick.units.map { u ->
                        MultiplayerNpc(
                            id = u.id,
                            x = u.location.longitude,
                            y = u.location.latitude,
                            rotation = u.rotationAngle,
                            npcType = u.type.name,
                            ownerId = myPlayerUUID
                        )
                    }
                    ws.sendMessage(gson.toJson(mapOf("type" to "POLICE_BATCH_UPDATE", "npcs" to batch)))
                }
            } catch (e: Exception) {
                Log.e("Police", "Error difundiendo policía: ${e.message}")
            }
        }
    }
}
