package ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import ovh.gabrielhuav.pow.domain.models.zombie.ActiveEffect
import ovh.gabrielhuav.pow.domain.models.zombie.Projectile
import ovh.gabrielhuav.pow.domain.models.zombie.SkillEffect
import ovh.gabrielhuav.pow.domain.models.zombie.SkillItem
import ovh.gabrielhuav.pow.domain.models.zombie.ZombieEntity
import ovh.gabrielhuav.pow.domain.models.zombie.ZombieRoomCatalog
import ovh.gabrielhuav.pow.domain.models.zombie.ZombieType
import ovh.gabrielhuav.pow.domain.models.zombie.ZoneType
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction
import kotlin.math.hypot
import kotlin.random.Random

/**
 * CAPA DE ZOMBIS — combate del interior de supervivencia (extraído de ZombieInteriorViewModel
 * para separar la lógica de ZOMBIS de la de INTERIOR; mismo paquete `viewmodel`). Melee, disparo,
 * muerte de zombi (drop + victoria), efectos/skills, victoria y sincronía de zombis con el servidor.
 * Son EXTENSIONES del VM: solo tocan miembros `internal`/`public` (`_state`, `soundManager`,
 * `idleJob`, `lastPlayerAttackMs`, `lastRangedShotMs`, `attackAnimUntilMs`, `wsManager`, `gson`,
 * `currentRoom()`, `isWalkable()`, `knockbackZombie()`). La base de INTERIOR (salas, movimiento,
 * puertas, puzzle de llave, red de jugadores) se queda en ZombieInteriorViewModel; la simulación
 * de zombis en ZombieGameTick.kt.
 */

// ─── COMBATE CUERPO A CUERPO ───────────────────────────
fun ZombieInteriorViewModel.performPlayerAttack() {
    val now = System.currentTimeMillis()
    if (now - lastPlayerAttackMs < PLAYER_ATTACK_COOLDOWN_MS) return
    soundManager.playPunch()
    lastPlayerAttackMs = now

    val s = _state.value
    val target = s.zombies
        .filter { !it.isDying && hypot(it.x - s.playerX, it.y - s.playerY) <= PLAYER_ATTACK_RADIUS }
        .minByOrNull { hypot(it.x - s.playerX, it.y - s.playerY) } ?: return

    if (isMultiplayer) {
        sendZombieDamage(target.id, PLAYER_PUNCH_DAMAGE * playerDamageFactor())
        return
    }

    val room = currentRoom()
    val (kx, ky) = knockbackZombie(target.x, target.y, s.playerX, s.playerY, room, MELEE_KNOCKBACK)
    val newHealth = target.health - PLAYER_PUNCH_DAMAGE * playerDamageFactor()
    if (newHealth <= 0f) {
        _state.update { cur ->
            cur.copy(zombies = cur.zombies.map {
                if (it.id == target.id) it.copy(health = 0f, isDying = true, x = kx, y = ky) else it
            })
        }
        viewModelScope.launch { delay(1000L); onZombieDeath(target) }
    } else {
        _state.update { cur ->
            cur.copy(zombies = cur.zombies.map {
                if (it.id == target.id) it.copy(health = newHealth, x = kx, y = ky) else it
            })
        }
    }
}

// ─── COMBATE A DISTANCIA ───────────────────────────────
internal fun ZombieInteriorViewModel.fireProjectile() {
    if (_state.value.showWastedScreen) return // muerto: no dispara
    soundManager.playShoot()
    val now = System.currentTimeMillis()
    if (now - lastRangedShotMs < RANGED_COOLDOWN_MS) return
    lastRangedShotMs = now
    attackAnimUntilMs = now + ATTACK_ANIM_MS

    val s = _state.value
    var dx = s.aimDirX
    var dy = s.aimDirY
    if (dx == 0f && dy == 0f) { dx = if (s.isPlayerFacingRight) 1f else -1f; dy = 0f }

    val p = Projectile(x = s.playerX, y = s.playerY, dirX = dx, dirY = dy, bornAtMs = now)

    // Recoil: empujar al jugador hacia atrás (opuesto a la mira), con corrección
    // de posición por eje para no atravesar colisiones.
    val rbX = s.playerX - dx * PLAYER_RECOIL
    val rbY = s.playerY - dy * PLAYER_RECOIL
    val (recoilX, recoilY) = when {
        isWalkable(rbX, rbY) -> rbX to rbY
        isWalkable(rbX, s.playerY) -> rbX to s.playerY
        isWalkable(s.playerX, rbY) -> s.playerX to rbY
        else -> s.playerX to s.playerY
    }

    _state.update {
        it.copy(
            projectiles = it.projectiles + p,
            playerAction = PlayerAction.SPECIAL,
            playerX = recoilX,
            playerY = recoilY
        )
    }
    idleJob?.cancel()
    idleJob = viewModelScope.launch {
        delay(ATTACK_ANIM_MS)
        // Solo resetea si la ventana de ataque ya venció (no se re-disparó) y seguimos en SPECIAL.
        if (System.currentTimeMillis() >= attackAnimUntilMs) {
            _state.update { if (it.playerAction == PlayerAction.SPECIAL) it.copy(playerAction = PlayerAction.IDLE) else it }
        }
    }
}

// OFFLINE: muerte + drop + victoria (online lo decide el servidor).
internal fun ZombieInteriorViewModel.onZombieDeath(dead: ZombieEntity) {
    _state.update { cur ->
        val remaining = cur.zombies.filter { it.id != dead.id }
        val newItems = if (dead.isLootCarrier || Random.nextFloat() < SKILL_DROP_CHANCE) {
            val effect = SkillEffect.entries.random()
            cur.items + SkillItem(x = dead.x, y = dead.y, effect = effect)
        } else cur.items

        val alive = remaining.count { !it.isDying }
        val room = ZombieRoomCatalog.rooms[cur.currentRoomIndex]
        val won = room.type == ZoneType.BUILDING && alive == 0 && cur.totalZombies > 0
        cur.copy(zombies = remaining, items = newItems, zombiesRemaining = alive, showVictoryScreen = won)
    }

    if (_state.value.showVictoryScreen) {
        viewModelScope.launch {
            delay(3000L)
            _state.update { it.copy(showVictoryScreen = false) }
        }
    }
}

// ─── APLICAR UN EFECTO RECOGIDO ────────────────────────
internal fun ZombieInteriorViewModel.applyEffect(effect: SkillEffect) {
    when (effect) {
        SkillEffect.CURA_TOTAL -> {
            _state.update { it.copy(playerHealth = 100f) }
        }
        else -> {
            val now = System.currentTimeMillis()
            _state.update { cur ->
                val withoutSame = cur.activeEffects.filter { it.effect != effect }
                cur.copy(activeEffects = withoutSame + ActiveEffect(effect, now + effect.durationMs))
            }
        }
    }
    _state.update { it.copy(effectToast = effect.displayName) }
    viewModelScope.launch {
        delay(2000L)
        _state.update { it.copy(effectToast = null) }
    }
}

// ─── EFECTOS: getters ──────────────────────────────────
internal fun ZombieInteriorViewModel.hasEffect(e: SkillEffect): Boolean =
    _state.value.activeEffects.any { it.effect == e }

internal fun ZombieInteriorViewModel.playerDamageFactor(): Float =
    if (hasEffect(SkillEffect.FUERZA_BRUTA)) PLAYER_DMG_BRUTE_FACTOR else 1f

internal fun ZombieInteriorViewModel.effectFromName(name: String?): SkillEffect =
    runCatching { SkillEffect.valueOf(name ?: "") }.getOrDefault(SkillEffect.CURA_TOTAL)

internal fun ZombieInteriorViewModel.applyEffectByName(name: String) = applyEffect(effectFromName(name))

// ─── VICTORIA (offline) ────────────────────────────────
internal fun ZombieInteriorViewModel.showVictory() {
    if (currentRoom().type != ZoneType.BUILDING) return
    if (_state.value.showVictoryScreen) return
    _state.update { it.copy(showVictoryScreen = true) }
    viewModelScope.launch {
        delay(3000L)
        _state.update { it.copy(showVictoryScreen = false) }
    }
}

// ─── RED: daño a zombi + estado autoritativo del servidor ──────────────────
internal fun ZombieInteriorViewModel.sendZombieDamage(zombieId: String, damage: Float) {
    if (!isMultiplayer) return
    wsManager?.sendMessage(
        gson.toJson(mapOf("type" to "ZOMBIE_DAMAGE", "zombieId" to zombieId, "damage" to damage))
    )
}

// Reemplaza zombis e items locales con el estado autoritativo del servidor.
// Convierte fracción [0,1] → píxeles con las dimensiones de la sala actual.
internal fun ZombieInteriorViewModel.applyServerZombieState(msg: ZombieServerMessage) {
    val room = currentRoom()
    val w = room.worldWidth; val h = room.worldHeight
    val zs = msg.zombies?.map { nz ->
        ZombieEntity(
            id = nz.id, x = nz.x * w, y = nz.y * h,
            health = nz.health, maxHealth = nz.maxHealth,
            facingRight = nz.facingRight, frameIndex = nz.frameIndex,
            isDying = nz.isDying, isLootCarrier = nz.isLootCarrier,
            type = ZombieType.NORMAL
        )
    } ?: emptyList()
    val its = msg.items?.map { ni ->
        SkillItem(id = ni.id, x = ni.x * w, y = ni.y * h, effect = effectFromName(ni.effect))
    } ?: emptyList()
    // NPCs civiles (autoritativos): se renderizan como figuras humanas (RemotePlayerView).
    val civs = msg.npcs?.map { nn ->
        RemoteZombiePlayer(
            id = nn.id, displayName = "",
            x = nn.x * w, y = nn.y * h,
            action = PlayerAction.WALK,
            facingRight = nn.facingRight, health = 100f
        )
    } ?: emptyList()
    _state.update {
        it.copy(
            zombies = zs,
            items = its,
            interiorNpcs = civs,
            totalZombies = msg.totalZombies ?: it.totalZombies,
            zombiesRemaining = zs.count { z -> !z.isDying }
        )
    }
}
