package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.NpcType

// ─── SALUD DEL JUGADOR ────────────────────────────────────────────────────────

fun WorldMapViewModel.takeDamage(amount: Float) {
    playerHealth = (playerHealth - amount).coerceAtLeast(0f)
    damagePulseTrigger++
    showHealthBar = true
    if (playerHealth > 30f) {
        startHealthBarTimer(3000L)
    } else {
        healthBarJob?.cancel()
    }
    if (playerHealth <= 0f) triggerWastedSequence()
}

fun WorldMapViewModel.heal(amount: Float) {
    playerHealth = (playerHealth + amount).coerceAtMost(maxPlayerHealth)
    showHealthBar = true
    if (playerHealth > 30f) {
        startHealthBarTimer(3000L)
    } else {
        healthBarJob?.cancel()
    }
}

fun WorldMapViewModel.showInitialHealthBar() {
    showHealthBar = true
    startHealthBarTimer(4000L)
}

internal fun WorldMapViewModel.startHealthBarTimer(delayMillis: Long) {
    healthBarJob?.cancel()
    healthBarJob = viewModelScope.launch {
        delay(delayMillis)
        showHealthBar = false
    }
}

internal fun WorldMapViewModel.triggerWastedSequence() {
    viewModelScope.launch(Dispatchers.Main) {
        _uiState.update { it.copy(showWastedScreen = true) }
        delay(4000L)
        val deathLoc      = _uiState.value.currentLocation ?: GeoPoint(19.504505, -99.146911)
        val nearestHospital = hospitalRespawnPoints.minByOrNull { distance(deathLoc, it) }
            ?: hospitalRespawnPoints.first()
        _uiState.update { it.copy(currentLocation = nearestHospital, showWastedScreen = false) }
        playerHealth = maxPlayerHealth
    }
}

// ─── COMBATE CONTRA NPCs ──────────────────────────────────────────────────────

fun WorldMapViewModel.performPlayerAttack() {
    val now = System.currentTimeMillis()
    if (now - lastAttackTime < ATTACK_COOLDOWN_MS) return
    lastAttackTime = now
    viewModelScope.launch(Dispatchers.Default) {
        delay(300L)
        val playerLoc = _uiState.value.currentLocation ?: return@launch
        val targetNpcEntry = remoteEntities.entries
            .filter {
                !it.value.isDying &&
                        it.value.type == NpcType.PERSON &&
                        distance(playerLoc, it.value.location) <= ATTACK_RADIUS
            }
            .minByOrNull { distance(playerLoc, it.value.location) }

        if (targetNpcEntry != null) {
            val npcId      = targetNpcEntry.key
            val currentNpc = targetNpcEntry.value
            val isRemotePlayer = !currentNpc.displayName.isNullOrBlank()

            if (isRemotePlayer) {
                try {
                    webSocketManager?.sendMessage(
                        gson.toJson(mapOf(
                            "type"     to "PLAYER_DAMAGE",
                            "targetId" to npcId,
                            "damage"   to PLAYER_PUNCH_DAMAGE
                        ))
                    )
                } catch (e: Exception) {
                    Log.e("Combat", "Error enviando PLAYER_DAMAGE: ${e.message}")
                }
            } else {
                val newHealth = (currentNpc.health - PLAYER_PUNCH_DAMAGE).coerceAtLeast(0f)
                if (newHealth <= 0f) {
                    remoteEntities[npcId] = currentNpc.copy(health = 0f, isDying = true)
                    updateNpcsState()
                    delay(1000L)
                    remoteEntities.remove(npcId)
                    try {
                        webSocketManager?.sendMessage(
                            gson.toJson(mapOf("type" to "NPC_DESTROY", "npcId" to npcId))
                        )
                    } catch (e: Exception) {
                        Log.e("Combat", "Error enviando NPC_DESTROY para npcId=$npcId", e)
                    }
                    updateNpcsState()
                } else {
                    remoteEntities[npcId] = currentNpc.copy(health = newHealth)
                    updateNpcsState()
                }
            }
        }
    }
}
