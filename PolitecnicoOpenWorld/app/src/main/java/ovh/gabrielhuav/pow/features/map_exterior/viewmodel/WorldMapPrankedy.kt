package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import kotlinx.coroutines.flow.update
import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.ai.PrankedyManager
import ovh.gabrielhuav.pow.domain.models.ai.PrankedyPhase

/**
 * Extensiones del [WorldMapViewModel] que encapsulan toda la lógica del
 * compañero Prankedy: tick de IA, spawn inicial, interacciones del jugador
 * y actualización del [WorldMapState].
 *
 * Patrón: idéntico a WorldMapWanted.kt (extensiones internas sobre el VM).
 */

// ─── MODO HISTORIA: PRANKEDY COMO ACOMPAÑANTE (campaña ENCB) ──────────────────

// Coordenadas de la ENCB y radio del "vecindario": el acompañante SOLO se enciende aquí.
private const val ENCB_LAT = 19.5001588
private const val ENCB_LON = -99.1450298
// Radio "ajustado": ~220 m. Incluye el spawn EXACTO de la ENCB (dist≈0, tras el outro) pero
// EXCLUYE a ESCOM (~505 m del spawn de la intro), para que el acompañante NO se encienda
// prematuramente si el game loop del mundo corre con location=ESCOM antes del outro.
private const val ENCB_NEIGHBORHOOD_DEG = 0.002

// Destino "lugar seguro" de la misión = ESCOM. La línea GPS roja va de la ENCB a aquí.
private const val ESCOM_LAT = 19.504603
private const val ESCOM_LON = -99.145985
private const val ESCOM_ARRIVE_DEG = 0.0009   // ~100 m: al entrar, la línea GPS desaparece

/**
 * Enciende a Prankedy en modo ACOMPAÑANTE (fase HIRED) UNA sola vez, y SOLO si:
 *  - estamos en una sesión de campaña (`inCampaign == true`), y
 *  - el jugador está en el vecindario de la ENCB.
 * Fija el objetivo "Lleva a un lugar seguro a Prankedy". En MUNDO LIBRE (inCampaign=false)
 * o fuera de la ENCB no hace NADA (no interfiere con el Prankedy hostil del menú de Opciones
 * ni con otros mapas). La bandera `prankedyCompanionActivated` evita re-spawns cada tick;
 * `setStorySpawn` la re-arma en cada entrada de campaña.
 */
internal fun WorldMapViewModel.maybeSpawnPrankedyCompanion(
    playerLoc: GeoPoint,
    now: Long = System.currentTimeMillis()
) {
    if (!inCampaign || prankedyCompanionActivated) return
    val dLat = playerLoc.latitude - ENCB_LAT
    val dLon = playerLoc.longitude - ENCB_LON
    if (kotlin.math.sqrt(dLat * dLat + dLon * dLon) > ENCB_NEIGHBORHOOD_DEG) return

    prankedyCompanionActivated = true
    prankedyManager.spawnCompanion(playerLoc, roadNetwork, now)
    setCampaignObjective(ovh.gabrielhuav.pow.domain.models.MissionCatalog.ESCOLTAR_PRANKEDY)

    // LÍNEA GPS de campaña: ruta A* (sobre la red vial) desde la ENCB hasta la ESCOM (lugar
    // seguro). findRoadRoute garantiza una ruta CONECTADA POR CALLES; si no hay grafo, cae a
    // recta como último recurso. Se calcula UNA vez aquí (al encender el acompañante).
    val gpsRoute = findRoadRoute(playerLoc, GeoPoint(ESCOM_LAT, ESCOM_LON))
    _uiState.update {
        it.copy(
            prankedyEnabled = true,
            prankedyLocation = prankedyManager.location,
            prankedyVisible  = prankedyManager.location != null && !it.isDriving,
            prankedyPhase    = prankedyManager.phase,
            prankedyAnimState = prankedyManager.animState,
            campaignRouteWaypoints = gpsRoute
        )
    }
}

/**
 * MODO HISTORIA: oculta la línea GPS roja cuando el jugador llega al "lugar seguro" (ESCOM).
 * Se llama cada tick del game loop (solo en campaña). Limpieza idempotente.
 */
internal fun WorldMapViewModel.maybeHideCampaignRouteNearEscom(playerLoc: GeoPoint) {
    if (_uiState.value.campaignRouteWaypoints.isEmpty()) return
    val dLat = playerLoc.latitude - ESCOM_LAT
    val dLon = playerLoc.longitude - ESCOM_LON
    if (kotlin.math.sqrt(dLat * dLat + dLon * dLon) <= ESCOM_ARRIVE_DEG) {
        _uiState.update { it.copy(campaignRouteWaypoints = emptyList()) }
    }
}

// ─── TICK PRINCIPAL ──────────────────────────────────────────────────────────

// ─── SPAWN Y CONTROL ─────────────────────────────────────────────────────────

/**
 * Verifica si Prankedy debe aparecer en el mapa. Se llama cuando la posición
 * inicial del jugador es conocida o tras un respawn.
 */
internal fun WorldMapViewModel.checkPrankedySpawn(playerLoc: GeoPoint, now: Long = System.currentTimeMillis()) {
    if (!_uiState.value.prankedyEnabled) return
    val pm = prankedyManager
    if (pm.location == null && pm.phase != PrankedyPhase.DEAD) {
        android.util.Log.d("Prankedy", "Forzando spawn en $playerLoc (RoadsReady=${_uiState.value.isRoadNetworkReady})")
        pm.spawn(playerLoc, roadNetwork, now)
        
        // Reflejar inmediatamente en el estado para que NativeOsmMap lo vea
        val spawnedLoc = pm.location
        if (spawnedLoc != null) {
            android.util.Log.d("Prankedy", "Spawn EXITOSO en $spawnedLoc")
            _uiState.update { it.copy(
                prankedyLocation = spawnedLoc,
                prankedyVisible = true,
                prankedyPhase = pm.phase
            ) }
        } else {
            android.util.Log.e("Prankedy", "ERROR: pm.spawn devolvió ubicación nula")
        }
    }
}

/**
 * Ejecuta un ciclo de IA de Prankedy y refleja los cambios en el UI state.
 * Debe llamarse cada tick del game loop (33 ms).
 */
internal fun WorldMapViewModel.runPrankedyTick(playerLoc: GeoPoint, now: Long) {
    if (!_uiState.value.prankedyEnabled) return
    val pm = prankedyManager

    // ZONA LIBRE: ¿el jugador está dentro del campus de la ENCB (o ESCOM)? Si sí, Prankedy
    // deja de calcular ruta por nodos de calle y persigue en línea recta (steer-to-target).
    val freeZone = isFreeMovementZone(playerLoc.latitude, playerLoc.longitude)

    // Ejecutar la IA
    val allNpcs = remoteEntities.values.toList() + policeManager.activeUnits()
    val result = pm.tick(
        playerLoc  = playerLoc,
        npcs       = allNpcs,
        isDriving  = _uiState.value.isDriving,
        now        = now,
        roadNetwork = roadNetwork,
        // ZONA LIBRE (ESCOM / ENCB): si el jugador está en el campus, se APAGA el snap a calles
        // → Prankedy persigue en LÍNEA RECTA (steer-to-target) por explanadas/áreas verdes. Fuera
        // del campus vuelve a mantenerse SOBRE las calles (mismo índice que usa el jugador).
        snapToRoad = { p ->
            if (!freeZone && _uiState.value.isRoadNetworkReady) getNearestPointOnNetwork(p) else p
        },
        // Acompañante (HIRED): iguala la velocidad del jugador (corre si el jugador corre).
        playerRunning = _uiState.value.isRunning
    )

    // Si Prankedy murió este tick, avisar al jugador (location ya es null → el render lo oculta).
    if (result.justDied) {
        _uiState.update { it.copy(
            interactionPrompt = getLocalizedString(ovh.gabrielhuav.pow.R.string.wm_prankedy_down),
            showPrankedyHireDialog = false
        ) }
    }

    // Si el proyectil golpeó AL JUGADOR, bajarle vida (Prankedy es hostil).
    if (result.hitPlayer) {
        takeDamage(result.projectileDamage)
    }

    // Aplicar resultado: si el proyectil impactó a un NPC, aplicar daño
    result.hitNpcId?.let { npcId ->
        val target = remoteEntities[npcId]
        if (target != null) {
            val newHp = (target.health - result.projectileDamage).coerceAtLeast(0f)
            val dying = newHp <= 0f
            remoteEntities[npcId] = target.copy(health = newHp, isDying = dying)
            if (dying) {
                // Dejar que la IA gestione el despawn con el flag isDying
            }
        }
    }

    // Sincronizar WorldMapState con el estado de PrankedyManager
    val prankedyLoc = pm.location
    val proj = pm.projectileActive
    _uiState.update { st ->
        st.copy(
            prankedyLocation         = prankedyLoc,
            prankedyAnimState        = pm.animState,
            prankedyFacingRight      = pm.facingRight,
            prankedyVisible          = prankedyLoc != null && !_uiState.value.isDriving,
            prankedyHealth           = pm.health,
            prankedyProjectileActive = proj,
            prankedyProjectileStart  = if (proj) pm.projectileStart else null,
            prankedyProjectileTarget = if (proj) pm.projectileTarget else null,
            prankedyProjectileProgress = pm.projectileProgress,
            prankedyPhase            = pm.phase,
            prankedyNearby           = false,
            prankedyDialogue         = if (pm.currentDialogue != null && now < pm.dialogueUntil)
                                          pm.currentDialogue else null
        )
    }
}

// ─── INTERACCIÓN DEL JUGADOR ─────────────────────────────────────────────────

/**
 * Llama al pulsar X cuando Prankedy está cerca (NOT_HIRED).
 * Muestra el modal de contratación con el estado correcto de penalización.
 */
internal fun WorldMapViewModel.onPrankedyInteract(now: Long = System.currentTimeMillis()) {
    val pm = prankedyManager
    val isHireable = pm.isHireable(now)
    val secs = pm.hireableInSeconds(now)
    _uiState.update {
        it.copy(
            showPrankedyHireDialog  = true,
            prankedyIsHireable      = isHireable,
            prankedyHireableInSeconds = secs,
            interactionPrompt       = null
        )
    }
}

/** El jugador acepta contratar a Prankedy desde el modal. */
fun WorldMapViewModel.onHirePrankedy() {
    prankedyManager.hire()
    _uiState.update {
        it.copy(
            showPrankedyHireDialog = false,
            interactionPrompt = null
        )
    }
}

/** Cierra el modal de contratación sin contratar. */
fun WorldMapViewModel.dismissPrankedyDialog() {
    _uiState.update { it.copy(showPrankedyHireDialog = false) }
}

/** Activa o desactiva a Prankedy (NPC hostil) desde el menú de Opciones. */
fun WorldMapViewModel.togglePrankedy() {
    val enabled = !_uiState.value.prankedyEnabled
    if (enabled) {
        _uiState.update { it.copy(prankedyEnabled = true) }
        _uiState.value.currentLocation?.let { checkPrankedySpawn(it) }
    } else {
        prankedyManager.deactivate()
        _uiState.update {
            it.copy(
                prankedyEnabled = false,
                prankedyVisible = false,
                prankedyLocation = null,
                prankedyProjectileActive = false
            )
        }
    }
}
