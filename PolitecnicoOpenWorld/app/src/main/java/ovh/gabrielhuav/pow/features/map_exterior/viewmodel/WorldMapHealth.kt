package ovh.gabrielhuav.pow.features.map_exterior.viewmodel


// ─────────────────────────────────────────────────────────────────────────────
// Salud del jugador (daño/curación/barra de vida) extraída de WorldMapViewModel.kt.
// El ESTADO (playerHealth, showHealthBar, healthBarJob, respawnImmunityUntilMs…) sigue
// en el ViewModel; estas extensiones solo lo leen/actualizan. fireImpactEffect /
// startHealthBarTimer / triggerWastedSequence se resuelven a sus extensiones/miembros.
// ─────────────────────────────────────────────────────────────────────────────

internal fun WorldMapViewModel.takeDamage(amount: Float) {
        // Inmunidad post-respawn / post-teletransporte: ignorar el daño durante los primeros
        // segundos tras reaaparecer para que ningún policía/NPC con aggro residual dispare
        // la animación de golpe de forma inesperada.
        if (System.currentTimeMillis() < respawnImmunityUntilMs) return
        // Si YA estás muerto o en la pantalla WASTED, ignora el daño: si no, los zombis cercanos
        // al cuerpo seguían llamando takeDamage durante el WASTED y el 💥 aparecía "a cada rato"
        // al morir (y re-disparaban la secuencia).
        if (_uiState.value.showWastedScreen || playerHealth <= 0f) return
        playerHealth = (playerHealth - amount).coerceAtLeast(0f)
        damagePulseTrigger++
        if (playerHealth > 0f) fireImpactEffect() // 💥 solo si SOBREVIVES (no en el golpe mortal)
        showHealthBar = true
        if (playerHealth > 30f) {
            startHealthBarTimer(3000L)
        } else {
            healthBarJob?.cancel()
        }
        if (playerHealth <= 0f) {
            triggerWastedSequence()
        }
        // Notificar a Prankedy para que active su búsqueda de agresor
        if (prankedyManager.phase == ovh.gabrielhuav.pow.domain.models.ai.PrankedyPhase.HIRED) {
            prankedyManager.onPlayerDamaged()
        }
    }

internal fun WorldMapViewModel.heal(amount: Float) {
        playerHealth = (playerHealth + amount).coerceAtMost(maxPlayerHealth)
        showHealthBar = true
        if (playerHealth > 30f) {
            startHealthBarTimer(3000L)
        } else {
            healthBarJob?.cancel()
        }
    }

internal fun WorldMapViewModel.showInitialHealthBar() {
        showHealthBar = true
        startHealthBarTimer(4000L)
    }

