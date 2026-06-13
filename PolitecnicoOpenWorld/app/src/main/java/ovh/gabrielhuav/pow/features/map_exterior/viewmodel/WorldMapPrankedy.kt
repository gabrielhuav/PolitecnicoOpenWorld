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
    val pm = prankedyManager

    // Ejecutar la IA
    val result = pm.tick(
        playerLoc  = playerLoc,
        npcs       = remoteEntities.values.toList(),
        isDriving  = _uiState.value.isDriving,
        now        = now,
        roadNetwork = roadNetwork
    )

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

    // Detectar si el jugador está cerca de Prankedy (para hint de interacción)
    val prankedyLoc = pm.location
    val nearby = prankedyLoc != null &&
        pm.phase == PrankedyPhase.NOT_HIRED &&
        !_uiState.value.isDriving &&
        distance(playerLoc, prankedyLoc) <= PrankedyManager.PRANKEDY_INTERACT_RADIUS

    // Sincronizar WorldMapState con el estado de PrankedyManager
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
            prankedyNearby           = nearby,
            prankedyDialogue         = if (pm.currentDialogue != null && now < pm.dialogueUntil)
                                          pm.currentDialogue else null,
            // Sólo actualizar interactionPrompt si Prankedy tiene prioridad y no hay otro prompt activo
            interactionPrompt = when {
                nearby && st.interactionPrompt == null ->
                    "PRESIONA X PARA HABLAR CON PRANKEDY 🎭"
                !nearby && st.interactionPrompt == "PRESIONA X PARA HABLAR CON PRANKEDY 🎭" ->
                    null
                else -> st.interactionPrompt
            }
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
