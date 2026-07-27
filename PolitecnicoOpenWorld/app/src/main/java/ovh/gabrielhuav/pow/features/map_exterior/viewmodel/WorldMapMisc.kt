package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ovh.gabrielhuav.pow.domain.models.geo.GeoPoint
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction

internal fun WorldMapViewModel.startMovementAction(isMovingRight: Boolean? = null) {
        idleJob?.cancel()
        val newFacingRight = isMovingRight ?: _uiState.value.isPlayerFacingRight
        val currentAction = if (_uiState.value.isRunning) PlayerAction.RUN else PlayerAction.WALK
        if (_uiState.value.playerAction != PlayerAction.SPECIAL) {
            if (_uiState.value.playerAction != currentAction || _uiState.value.isPlayerFacingRight != newFacingRight) {
                _uiState.update { it.copy(playerAction = currentAction, isPlayerFacingRight = newFacingRight) }
            }
        }
        if (_uiState.value.playerAction != PlayerAction.SPECIAL) {
            idleJob = viewModelScope.launch {
                delay(150)
                if (_uiState.value.playerAction != PlayerAction.SPECIAL) {
                    _uiState.update { it.copy(playerAction = PlayerAction.IDLE) }
                }
            }
        }
    }

internal fun WorldMapViewModel.startHealthBarTimer(delayMillis: Long) {
        healthBarJob?.cancel()
        healthBarJob = viewModelScope.launch {
            delay(delayMillis)
            showHealthBar = false
        }
    }

internal fun WorldMapViewModel.triggerWastedSequence() {
        // DE-DUP (2026-06-21, par 3): sincronizado al MIEMBRO canónico de WorldMapViewModel.kt antes
        // de borrarlo. El miembro DIVERGÍA de esta extensión vieja: (a) respawn normal = TELETRANSPORTE
        // a la ESCOM (coords fijas), NO ~80 m del lugar de muerte; (b) resetea wantedLevel + carjackStartTime
        // (modo retirada de la policía). Cascada verificada SEGURA: el único call externo, clearCampaignPolice(),
        // tiene UNA sola definición (ext WorldMapCampaignPolice.kt) — sin gemelo divergente.
        viewModelScope.launch(Dispatchers.Main) {
            // MISIÓN 1 (escolta/ingreso): la policía te ARRESTA, no te mata → la pantalla
            // dice "BUSTED" (azul) en vez de "WASTED". Mismo flujo de misión fallida.
            val missionObj = _uiState.value.currentObjective
            val busted = inCampaign && (
                missionObj?.id == ovh.gabrielhuav.pow.domain.models.campaign.MissionCatalog.ESCOLTAR_PRANKEDY.id ||
                missionObj?.id == ovh.gabrielhuav.pow.domain.models.campaign.MissionCatalog.INGRESAR_ESCOM.id)
            // Al morir te bajas del coche (no se respawnea conduciendo) y se quita el pánico de la zona.
            _uiState.update {
                it.copy(
                    showWastedScreen = true,
                    wastedIsBusted = busted,
                    isDriving = false,
                    currentVehicleModel = null,
                    currentVehicleColor = null,
                    vehicleSpeed = 0.0
                )
            }
            // MODO HISTORIA: morir DURANTE una misión de campaña = MISIÓN FALLIDA (reinicia desde el
            // último checkpoint con "REINTENTAR MISIÓN"), NO el respawn normal — si no, el jugador podría
            // dejarse matar para SALTARSE todo el trayecto de la escolta de Prankedy.
            val inMission = inCampaign && (
                missionObj?.id == ovh.gabrielhuav.pow.domain.models.campaign.MissionCatalog.ESCOLTAR_PRANKEDY.id ||
                missionObj?.id == ovh.gabrielhuav.pow.domain.models.campaign.MissionCatalog.INGRESAR_ESCOM.id ||
                // MISIONES 2/3: morir en cualquiera de sus fases = misión fallida.
                missionObj?.id?.startsWith(
                    ovh.gabrielhuav.pow.domain.models.campaign.mission2.Mission2.OBJECTIVE_ID_PREFIX) == true ||
                missionObj?.id?.startsWith(
                    ovh.gabrielhuav.pow.domain.models.campaign.mission3.Mission3.OBJECTIVE_ID_PREFIX) == true)
            if (inMission) {
                delay(2500L)
                relentlessNpcs.clear(); npcHitStreak.clear(); npcContactCooldowns.clear()
                clearCampaignPolice()
                clearMission2Story()
                clearMission3Story()
                // wantedLevel/carjackWarning + timer de carjack los POSEE WantedManager.
                wantedManager.clearWanted()
                playerHealth = maxPlayerHealth
                damagePulseTrigger = 0
                impactEffectTrigger = 0
                _uiState.update { it.copy(isDrivingPoliceCar = false, showWastedScreen = false, wastedIsBusted = false, showMissionFailed = true) }
                return@launch
            }
            delay(4000L)
            // Limpiar el estado de combate (rachas / NPCs implacables / cooldowns) para no revivir perseguido.
            relentlessNpcs.clear(); npcHitStreak.clear(); npcContactCooldowns.clear()
            // Al morir se pierde el nivel de búsqueda, pero la policía NO desaparece de golpe: con
            // wantedLevel = 0 entra en modo retirada (se aleja hasta despawnear). clearWanted resetea
            // wantedLevel + carjackWarning + el timer de carjack (lo posee WantedManager).
            wantedManager.clearWanted()
            // RESPAWN EN ESCOM: Al morir, el jugador es llevado de vuelta a la ESCOM.
            val respawn = GeoPoint(19.504603, -99.145985)
            _uiState.update { it.copy(currentLocation = respawn, showWastedScreen = false, wastedIsBusted = false) }
            playerHealth = maxPlayerHealth
            // Reiniciar contadores de animación y activar inmunidad temporal (2 s) para que ningún
            // policía/NPC con aggro residual dispare la animación de daño justo al reaaparecer.
            damagePulseTrigger = 0
            impactEffectTrigger = 0
            respawnImmunityUntilMs = System.currentTimeMillis() + 2000L
        }
    }
