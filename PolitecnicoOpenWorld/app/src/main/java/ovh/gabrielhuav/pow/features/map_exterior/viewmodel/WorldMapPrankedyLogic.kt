package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.NpcType
import ovh.gabrielhuav.pow.domain.models.Npc
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt


// ═══════════════════════════════════════════════════════════════════════════
//  PRANKEDY — NPC ESPECIAL (compañero contratado)
// ═══════════════════════════════════════════════════════════════════════════

/** Fase de comportamiento de Prankedy. */
enum class PrankedyPhase {
    DESPAWNED,          // No existe en el mapa
    IDLE_WAITING,       // Quieto esperando ser descubierto
    FOLLOWING_WALK,     // Caminando detrás del jugador
    FOLLOWING_RUN,      // Corriendo para alcanzar al jugador
    CHARGING_TARGET,    // Corriendo con tanque hacia un NPC enemigo
    THROWING,           // Animación de lanzamiento de objeto
    RIDING,             // Oculto mientras el jugador conduce
    DYING,              // Muriendo
    ROAMING             // Post-muerte: camina por las calles sin poder ser contratado
}

/** Un proyectil (objeto) lanzado por Prankedy. */
data class PrankedyProjectile(
    val id: String,
    val from: GeoPoint,
    val to: GeoPoint,
    val targetNpcId: String,
    val startTime: Long,
    val durationMs: Long = 520L
)

// ─── CONSTANTES ──────────────────────────────────────────────────────────

internal val PRANKEDY_WALK_SPEED     = 0.0000025   // velocidad de caminata
internal val PRANKEDY_RUN_SPEED      = 0.0000055   // velocidad de carrera
internal val PRANKEDY_FOLLOW_MIN     = 0.00012     // distancia mínima al jugador (~13 m)
internal val PRANKEDY_RUN_THRESHOLD  = 0.00035     // a partir de aquí corre (~39 m)
internal val PRANKEDY_ATTACK_RANGE   = 0.00025     // rango para lanzar (~28 m)
internal val PRANKEDY_DETECT_RANGE   = 0.00045     // radio de detección de NPCs (~50 m)
internal val PRANKEDY_ATTACK_COOLDOWN = 2800L       // ms entre ataques
internal val PRANKEDY_PROJECTILE_DMG = 12f          // daño del proyectil
internal val PRANKEDY_MAX_HEALTH     = 80f
internal val PRANKEDY_SPAWN_RADIUS   = 0.0004      // ~44 m del jugador al aparecer
internal val PRANKEDY_RESPAWN_MS     = 45_000L      // 45 s para reaparecer tras morir
internal val PRANKEDY_ROAM_BEFORE_HIRE_MS = 20_000L // 20 s caminando antes de poder ser contratado
internal val PRANKEDY_BUBBLE_RANGE   = 0.00028      // ~31 m para mostrar globo de diálogo
internal val PRANKEDY_THROW_ANIM_MS  = 720L         // duración de la animación de lanzamiento

internal val PRANKEDY_DIALOGS = listOf(
    "¿Qué onda, carnal? 😏",
    "¿Buscas problemas? Yo los fabrico.",
    "Soy el rey del trolleo. Contrátame.",
    "Las bromas no se cuentan, se lanzan.",
    "¿Quieres ver caos? Yo soy tu tipo.",
    "Nadie me ha atrapado... todavía.",
    "¿Tú otra vez? Esto es el destino. 🎯",
    "Mi especialidad: el caos calculado."
)

// ─── PROPIEDADES (viven en el ViewModel, se inicializan en init o lazy) ──

// NOTA: estas propiedades se declaran en WorldMapViewModel.kt (ver sección 6).
// Aquí las USAMOS vía las funciones de extensión.

// ─── TICK PRINCIPAL ──────────────────────────────────────────────────────

/**
 * Llamado cada 2 ticks del game loop (~66 ms). Orquesta toda la lógica
 * de Prankedy: spawn, seguimiento, ataque, proyectiles, muerte.
 */
internal fun WorldMapViewModel.updatePrankedy(playerLoc: GeoPoint) {
    val state = _uiState.value
    val now = System.currentTimeMillis()

    // ── SPAWN / RESPAWN ──────────────────────────────────────────────
    if (state.prankedyPhase == PrankedyPhase.DESPAWNED) {
        if (now >= prankedyRespawnTime && state.isRoadNetworkReady && roadNetwork.isNotEmpty()) {
            spawnPrankedy(playerLoc)
        }
        return
    }

    val prankedy = state.prankedyNpc ?: return

    // ── MURIENDO ─────────────────────────────────────────────────────
    if (state.prankedyPhase == PrankedyPhase.DYING) return // la corutina de muerte se encarga

    // ── CONDUCIENDO → ocultar ────────────────────────────────────────
    if (state.isDriving && state.isPrankedyHired && state.prankedyPhase != PrankedyPhase.RIDING) {
        _uiState.update { it.copy(prankedyPhase = PrankedyPhase.RIDING, prankedyBubbleText = null) }
        return
    }
    if (state.isDriving && state.prankedyPhase == PrankedyPhase.RIDING) return
    // Si ACABAMOS de bajarnos, reaparecer junto al jugador.
    if (!state.isDriving && state.prankedyPhase == PrankedyPhase.RIDING) {
        val nearPlayer = offsetPoint(playerLoc, PRANKEDY_FOLLOW_MIN * 1.2)
        _uiState.update {
            it.copy(
                prankedyNpc = prankedy.copy(location = nearPlayer),
                prankedyPhase = PrankedyPhase.FOLLOWING_WALK
            )
        }
        return
    }

    // ── GLOBO DE DIÁLOGO (no contratado) ─────────────────────────────
    val distToPlayer = distance(playerLoc, prankedy.location)
    if (!state.isPrankedyHired && state.prankedyPhase != PrankedyPhase.ROAMING) {
        if (distToPlayer < PRANKEDY_BUBBLE_RANGE) {
            if (state.prankedyBubbleText == null) {
                _uiState.update { it.copy(prankedyBubbleText = PRANKEDY_DIALOGS.random()) }
            }
        } else if (state.prankedyBubbleText != null) {
            _uiState.update { it.copy(prankedyBubbleText = null) }
        }
    }

    // ── ROAMING (post-muerte, no contratado) ─────────────────────────
    if (state.prankedyPhase == PrankedyPhase.ROAMING) {
        roamPrankedy(prankedy, playerLoc, now)
        // ¿Ya pasó el tiempo de enfriamiento para poder ser contratado?
        if (now >= prankedyHireAvailableTime) {
            prankedyHireAvailable = true
            // Bubble cuando el jugador se acerca
            if (distToPlayer < PRANKEDY_BUBBLE_RANGE && state.prankedyBubbleText == null) {
                _uiState.update { it.copy(prankedyBubbleText = PRANKEDY_DIALOGS.random()) }
            }
        }
        return
    }

    // ── IDLE ESPERANDO ───────────────────────────────────────────────
    if (state.prankedyPhase == PrankedyPhase.IDLE_WAITING) return // Quieto

    // ═══ CONTRATADO: lógica de compañero ═════════════════════════════
    if (!state.isPrankedyHired) return

    // ── LANZAMIENTO EN CURSO ─────────────────────────────────────────
    if (state.prankedyPhase == PrankedyPhase.THROWING) {
        if (now - prankedyThrowStartTime >= PRANKEDY_THROW_ANIM_MS) {
            _uiState.update { it.copy(prankedyPhase = PrankedyPhase.FOLLOWING_WALK) }
        }
        updatePrankedyProjectiles(now)
        return
    }

    // ── BUSCAR ENEMIGOS ──────────────────────────────────────────────
    // Prioridades: 1) NPCs que dañan al jugador (aggroUntil > now)
    //              2) Cualquier NPC cercano
    val target = findPrankedyTarget(playerLoc, now)

    if (target != null) {
        val distToTarget = distance(prankedy.location, target.location)
        if (distToTarget <= PRANKEDY_ATTACK_RANGE && now - prankedyLastAttackTime >= PRANKEDY_ATTACK_COOLDOWN) {
            // Lanzar ataque
            prankedyLastAttackTime = now
            prankedyThrowStartTime = now
            val facingTarget = target.location.longitude >= prankedy.location.longitude
            _uiState.update {
                it.copy(
                    prankedyNpc = prankedy.copy(facingRight = facingTarget),
                    prankedyPhase = PrankedyPhase.THROWING
                )
            }
            launchPrankedyProjectile(prankedy.location, target)
            return
        }
        // Correr hacia el objetivo (con tanque)
        val moved = movePrankedyToward(prankedy, target.location, PRANKEDY_RUN_SPEED)
        _uiState.update { it.copy(prankedyNpc = moved, prankedyPhase = PrankedyPhase.CHARGING_TARGET) }
    } else {
        // ── SEGUIR AL JUGADOR ────────────────────────────────────────
        if (distToPlayer > PRANKEDY_RUN_THRESHOLD) {
            val moved = movePrankedyToward(prankedy, playerLoc, PRANKEDY_RUN_SPEED)
            _uiState.update { it.copy(prankedyNpc = moved, prankedyPhase = PrankedyPhase.FOLLOWING_RUN) }
        } else if (distToPlayer > PRANKEDY_FOLLOW_MIN) {
            val moved = movePrankedyToward(prankedy, playerLoc, PRANKEDY_WALK_SPEED)
            _uiState.update { it.copy(prankedyNpc = moved, prankedyPhase = PrankedyPhase.FOLLOWING_WALK) }
        } else {
            // Cerca del jugador: idle con fase de follow para que no se quede en CHARGING
            _uiState.update { it.copy(prankedyPhase = PrankedyPhase.FOLLOWING_WALK) }
        }
    }

    updatePrankedyProjectiles(now)
}

// ─── SPAWN ───────────────────────────────────────────────────────────────

internal fun WorldMapViewModel.spawnPrankedy(playerLoc: GeoPoint) {
    val angle = Math.random() * 2.0 * Math.PI
    // Spawn más cerca del jugador (~25 m) para que sea fácil de encontrar
    val spawnRadius = 0.00023
    val candidate = GeoPoint(
        playerLoc.latitude + sin(angle) * spawnRadius,
        playerLoc.longitude + cos(angle) * spawnRadius
    )
    val spawnLoc = if (roadNetwork.isNotEmpty()) getNearestPointOnNetwork(candidate) else candidate
    val facingPlayer = playerLoc.longitude >= spawnLoc.longitude

    val npc = Npc(
        id = "PRANKEDY_SPECIAL",
        type = NpcType.PRANKEDY,
        location = spawnLoc,
        speed = 0.0,
        isMoving = false,
        facingRight = facingPlayer,
        health = PRANKEDY_MAX_HEALTH,
        maxHealth = PRANKEDY_MAX_HEALTH
    )

    val phase = if (prankedyHireAvailable) PrankedyPhase.IDLE_WAITING else PrankedyPhase.ROAMING

    _uiState.update {
        it.copy(
            prankedyNpc = npc,
            prankedyPhase = phase,
            prankedyBubbleText = null,
            prankedyProjectiles = emptyList()
        )
    }
    android.util.Log.d("Prankedy", "SPAWNED at ${spawnLoc.latitude}, ${spawnLoc.longitude} phase=$phase dist=${distance(playerLoc, spawnLoc)}")
}

// ─── MOVIMIENTO ──────────────────────────────────────────────────────────

private fun WorldMapViewModel.movePrankedyToward(prankedy: Npc, target: GeoPoint, speed: Double): Npc {
    val dLat = target.latitude - prankedy.location.latitude
    val dLon = target.longitude - prankedy.location.longitude
    val dist = sqrt(dLat * dLat + dLon * dLon)
    if (dist < speed) return prankedy // Ya llegó

    val newLoc = GeoPoint(
        prankedy.location.latitude + (dLat / dist) * speed,
        prankedy.location.longitude + (dLon / dist) * speed
    )
    val facingRight = dLon >= 0
    return prankedy.copy(location = newLoc, facingRight = facingRight, isMoving = true)
}

// ─── ROAMING (post-muerte) ───────────────────────────────────────────────

private fun WorldMapViewModel.roamPrankedy(prankedy: Npc, playerLoc: GeoPoint, now: Long) {
    // Caminar en una dirección semi-aleatoria, cambiando cada ~5 s.
    if (now - prankedyRoamDirChangeTime > 5000L) {
        prankedyRoamDirChangeTime = now
        prankedyRoamAngle = Math.random() * 2.0 * Math.PI
    }
    val newLoc = GeoPoint(
        prankedy.location.latitude + sin(prankedyRoamAngle) * PRANKEDY_WALK_SPEED,
        prankedy.location.longitude + cos(prankedyRoamAngle) * PRANKEDY_WALK_SPEED
    )
    val snapped = if (roadNetwork.isNotEmpty()) getNearestPointOnNetwork(newLoc) else newLoc
    val facingRight = cos(prankedyRoamAngle) >= 0
    _uiState.update {
        it.copy(prankedyNpc = prankedy.copy(location = snapped, facingRight = facingRight, isMoving = true))
    }
}

// ─── DETECCIÓN DE OBJETIVOS ──────────────────────────────────────────────

private fun WorldMapViewModel.findPrankedyTarget(playerLoc: GeoPoint, now: Long): Npc? {
    val prankedy = _uiState.value.prankedyNpc ?: return null
    val center = prankedy.location

    // 1) NPCs que están atacando al jugador (prioridad máxima)
    val aggressors = remoteEntities.values.filter { npc ->
        npc.type == NpcType.PERSON &&
                !npc.isDying &&
                npc.aggroUntil > now &&
                distance(center, npc.location) <= PRANKEDY_DETECT_RANGE
    }
    if (aggressors.isNotEmpty()) return aggressors.minByOrNull { distance(center, it.location) }

    // 2) Zombis cercanos
    val zombies = remoteEntities.values.filter { npc ->
        npc.type == NpcType.ZOMBIE &&
                !npc.isDying &&
                npc.health > 0f &&
                distance(center, npc.location) <= PRANKEDY_DETECT_RANGE
    }
    if (zombies.isNotEmpty()) return zombies.minByOrNull { distance(center, it.location) }

    // 3) Cualquier NPC persona (para las bromas) — solo si no hay peligro
    val bystanders = remoteEntities.values.filter { npc ->
        npc.type == NpcType.PERSON &&
                !npc.isDying &&
                npc.displayName.isNullOrBlank() && // no jugadores remotos
                distance(center, npc.location) <= PRANKEDY_DETECT_RANGE * 0.6
    }
    return bystanders.minByOrNull { distance(center, it.location) }
}

// ─── PROYECTIL ───────────────────────────────────────────────────────────

private fun WorldMapViewModel.launchPrankedyProjectile(from: GeoPoint, target: Npc) {
    val proj = PrankedyProjectile(
        id = "proj_${System.currentTimeMillis()}",
        from = from,
        to = target.location,
        targetNpcId = target.id,
        startTime = System.currentTimeMillis()
    )
    _uiState.update { it.copy(prankedyProjectiles = it.prankedyProjectiles + proj) }
}

internal fun WorldMapViewModel.updatePrankedyProjectiles(now: Long) {
    val projectiles = _uiState.value.prankedyProjectiles
    if (projectiles.isEmpty()) return

    val (alive, finished) = projectiles.partition { now - it.startTime < it.durationMs }

    // Aplicar daño por los que impactaron
    finished.forEach { proj ->
        val target = remoteEntities[proj.targetNpcId]
        if (target != null && !target.isDying) {
            val newHealth = (target.health - PRANKEDY_PROJECTILE_DMG).coerceAtLeast(0f)
            if (newHealth <= 0f) {
                remoteEntities[proj.targetNpcId] = target.copy(health = 0f, isDying = true)
                viewModelScope.launch {
                    delay(1000L)
                    remoteEntities.remove(proj.targetNpcId)
                    updateNpcsState()
                }
            } else {
                remoteEntities[proj.targetNpcId] = target.copy(health = newHealth)
            }
            updateNpcsState()
        }
    }

    if (alive.size != projectiles.size) {
        _uiState.update { it.copy(prankedyProjectiles = alive) }
    }
}

// ─── DAÑO A PRANKEDY ─────────────────────────────────────────────────────

internal fun WorldMapViewModel.damagePrankedy(amount: Float) {
    val prankedy = _uiState.value.prankedyNpc ?: return
    if (_uiState.value.prankedyPhase == PrankedyPhase.DYING) return

    val newHealth = (prankedy.health - amount).coerceAtLeast(0f)
    if (newHealth <= 0f) {
        // Muerte
        _uiState.update {
            it.copy(
                prankedyNpc = prankedy.copy(health = 0f, isDying = true),
                prankedyPhase = PrankedyPhase.DYING,
                isPrankedyHired = false,
                prankedyBubbleText = null,
                prankedyProjectiles = emptyList()
            )
        }
        prankedyHireAvailable = false
        viewModelScope.launch {
            delay(1200L) // animación de muerte
            _uiState.update {
                it.copy(prankedyNpc = null, prankedyPhase = PrankedyPhase.DESPAWNED)
            }
            // Programar respawn
            prankedyRespawnTime = System.currentTimeMillis() + PRANKEDY_RESPAWN_MS
            prankedyHireAvailableTime = prankedyRespawnTime + PRANKEDY_ROAM_BEFORE_HIRE_MS
        }
    } else {
        _uiState.update { it.copy(prankedyNpc = prankedy.copy(health = newHealth)) }
    }
}

// ─── INTERACCIÓN DEL JUGADOR (botón X) ───────────────────────────────────

internal fun WorldMapViewModel.tryInteractWithPrankedy(): Boolean {
    val state = _uiState.value
    val prankedy = state.prankedyNpc ?: return false
    val playerLoc = state.currentLocation ?: return false
    if (prankedy.isDying) return false
    if (state.prankedyPhase == PrankedyPhase.RIDING) return false

    val dist = distance(playerLoc, prankedy.location)
    if (dist > PRANKEDY_BUBBLE_RANGE) return false

    // Si ya está contratado, no hacer nada extra
    if (state.isPrankedyHired) return false

    // Si puede ser contratado, mostrar diálogo
    if (prankedyHireAvailable || state.prankedyPhase == PrankedyPhase.IDLE_WAITING) {
        _uiState.update { it.copy(showPrankedyDialog = true) }
        return true
    }

    return false
}

internal fun WorldMapViewModel.hirePrankedy() {
    _uiState.update {
        it.copy(
            isPrankedyHired = true,
            showPrankedyDialog = false,
            prankedyPhase = PrankedyPhase.FOLLOWING_WALK,
            prankedyBubbleText = null
        )
    }
    prankedyFirstMeet = false
}

internal fun WorldMapViewModel.dismissPrankedyDialog() {
    _uiState.update { it.copy(showPrankedyDialog = false) }
}

// ─── HELPERS ─────────────────────────────────────────────────────────────

private fun offsetPoint(center: GeoPoint, offset: Double): GeoPoint {
    val angle = Math.random() * 2.0 * Math.PI
    return GeoPoint(
        center.latitude + sin(angle) * offset,
        center.longitude + cos(angle) * offset
    )
}

/**
 * Comprueba si algún NPC está atacando a Prankedy (para que él se defienda)
 * y aplica daño de contacto si un NPC agresivo está junto a Prankedy.
 */
internal fun WorldMapViewModel.checkPrankedyDamage() {
    val prankedy = _uiState.value.prankedyNpc ?: return
    if (!_uiState.value.isPrankedyHired) return
    if (_uiState.value.prankedyPhase == PrankedyPhase.DYING || _uiState.value.prankedyPhase == PrankedyPhase.RIDING) return

    val now = System.currentTimeMillis()
    for ((_, npc) in remoteEntities) {
        if (npc.isDying) continue
        val isAggro = (npc.type == NpcType.PERSON && npc.aggroUntil > now) ||
                (npc.type == NpcType.ZOMBIE && npc.health > 0f)
        if (!isAggro) continue
        if (distance(prankedy.location, npc.location) > 0.00006) continue // ~6.6 m
        // Cooldown global para no drenar vida de Prankedy
        if (now - prankedyLastDamagedTime < 1200L) continue
        prankedyLastDamagedTime = now
        damagePrankedy(8f)
        return
    }
}