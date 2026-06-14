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

    // Ejecutar la IA
    val allNpcs = remoteEntities.values.toList() + policeManager.activeUnits()
    val result = pm.tick(
        playerLoc  = playerLoc,
        npcs       = allNpcs,
        isDriving  = _uiState.value.isDriving,
        now        = now,
        roadNetwork = roadNetwork,
        // Mantiene a Prankedy SOBRE las calles (mismo índice espacial que usa el jugador).
        snapToRoad = { p -> if (_uiState.value.isRoadNetworkReady) getNearestPointOnNetwork(p) else p }
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
