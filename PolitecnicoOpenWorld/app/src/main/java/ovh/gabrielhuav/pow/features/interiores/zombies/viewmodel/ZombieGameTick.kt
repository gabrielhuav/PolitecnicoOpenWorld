package ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ovh.gabrielhuav.pow.data.network.WebSocketManager
import ovh.gabrielhuav.pow.data.repository.CollisionMatrixRepository
import ovh.gabrielhuav.pow.data.repository.SettingsRepository
import ovh.gabrielhuav.pow.domain.models.zombie.ActiveEffect
import ovh.gabrielhuav.pow.domain.models.zombie.CollisionMatrix
import ovh.gabrielhuav.pow.domain.models.zombie.CombatMode
import ovh.gabrielhuav.pow.domain.models.zombie.Projectile
import ovh.gabrielhuav.pow.domain.models.zombie.SkillEffect
import ovh.gabrielhuav.pow.domain.models.zombie.SkillItem
import ovh.gabrielhuav.pow.domain.models.zombie.ZombieEntity
import ovh.gabrielhuav.pow.domain.models.zombie.ZombieRoom
import ovh.gabrielhuav.pow.domain.models.zombie.ZombieRoomCatalog
import ovh.gabrielhuav.pow.domain.models.zombie.ZombieType
import ovh.gabrielhuav.pow.domain.models.zombie.ZoneType
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.Direction
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

internal fun ZombieGameViewModel.tick() {
        val s = _state.value
        val now = System.currentTimeMillis()

        // El envío de posición va SIEMPRE primero (igual que antes).
        sendPlayerUpdate(now)

        // Pantallas bloqueantes / modo diseñador: no simular.
        if (s.showVictoryScreen || s.showWastedScreen || s.isExitingToWorld ||
            s.showExitToLobbyDialog || s.designerMode) {
            soundManager.stopWalk()
            soundManager.stopRun()
            return
        }

        when (s.playerAction) {
            PlayerAction.WALK -> { soundManager.playWalk(); soundManager.stopRun() }
            PlayerAction.RUN -> { soundManager.playRun(); soundManager.stopWalk() }
            else -> { soundManager.stopWalk(); soundManager.stopRun() }
        }

        val zombieNear = s.zombies.any { !it.isDying && hypot(it.x - s.playerX, it.y - s.playerY) < 300f }
        if (zombieNear && now - lastZombieSoundMs > 5000L) {
            soundManager.playZombieNear()
            lastZombieSoundMs = now
        }

        if (isMultiplayer) tickOnline(s, now) else tickOffline(s, now)
    }

internal fun ZombieGameViewModel.tickOffline(s: ZombieGameState, now: Long) {
        val room = ZombieRoomCatalog.rooms[s.currentRoomIndex]

        val stillActive = if (s.activeEffects.isEmpty()) s.activeEffects
                          else s.activeEffects.filter { it.expiresAtMs > now }
        val effectsChanged = stillActive.size != s.activeEffects.size

        val speedFactor = if (stillActive.isEmpty()) 1f else run {
            var f = 1f
            if (stillActive.any { it.effect == SkillEffect.RELOJ_ARENA }) f *= SLOW_ZOMBIE_FACTOR
            if (stillActive.any { it.effect == SkillEffect.ADRENALINA_ZOMBI }) f *= FAST_ZOMBIE_FACTOR
            f
        }
        val dmgFactor = if (stillActive.isEmpty()) 1f else run {
            var f = 1f
            if (stillActive.any { it.effect == SkillEffect.FURIA_ZOMBI }) f *= ZOMBIE_DMG_FURY_FACTOR
            if (stillActive.any { it.effect == SkillEffect.DEBILIDAD_ZOMBI }) f *= ZOMBIE_DMG_WEAK_FACTOR
            f
        }

        var newHealth = s.playerHealth
        var pulse = s.damagePulseTrigger

        var workingZombies = s.zombies.map { z ->
            if (z.isDying) return@map z
            val moved = moveZombie(z, s.playerX, s.playerY, now, room, speedFactor)
            val dist = hypot(moved.x - s.playerX, moved.y - s.playerY)
            if (dist <= CONTACT_DIST && now - moved.lastDamageToPlayerMs >= ZOMBIE_DAMAGE_COOLDOWN_MS) {
                newHealth -= ZOMBIE_DAMAGE * dmgFactor
                pulse += 1
                moved.copy(lastDamageToPlayerMs = now)
            } else moved
        }

        val deadZombieIds = mutableListOf<String>()
        val survivingProjectiles = mutableListOf<Projectile>()
        for (p in s.projectiles) {
            if (now - p.bornAtMs > PROJECTILE_LIFETIME_MS) continue
            val nx = p.x + p.dirX * PROJECTILE_SPEED
            val ny = p.y + p.dirY * PROJECTILE_SPEED
            if (nx < 0f || ny < 0f || nx > room.worldWidth || ny > room.worldHeight) continue
            // La bala RESPETA la matriz de colisiones: si el siguiente punto cae en PARED, se detiene
            // ahí (no atraviesa muros ni mata zombis al otro lado), igual que el movimiento.
            if (!isWalkable(nx, ny)) continue
            val hit = workingZombies.firstOrNull {
                !it.isDying && hypot(it.x - nx, it.y - ny) <= PROJECTILE_HIT_RADIUS
            }
            if (hit != null) {
                val newHp = hit.health - PROJECTILE_DAMAGE * playerDamageFactor()
                // Knockback en la dirección de viaje del proyectil (desde su origen).
                val (kx, ky) = knockbackZombie(hit.x, hit.y, p.x, p.y, room, PROJECTILE_KNOCKBACK)
                workingZombies = workingZombies.map { z ->
                    if (z.id == hit.id) {
                        if (newHp <= 0f) { deadZombieIds.add(z.id); z.copy(health = 0f, isDying = true, x = kx, y = ky) }
                        else z.copy(health = newHp, x = kx, y = ky)
                    } else z
                }
            } else survivingProjectiles.add(p.copy(x = nx, y = ny))
        }

        if (newHealth <= 0f) { triggerWastedSequence(); return }

        val nearItem = s.items.firstOrNull {
            !it.collected && hypot(it.x - s.playerX, it.y - s.playerY) <= ITEM_PICKUP_DIST
        }
        // PUZZLE de llaves (ENCB_lab1): ¿el jugador está SOBRE una llave?
        val nearKey = s.keys.firstOrNull {
            hypot(it.x - s.playerX, it.y - s.playerY) <= ITEM_PICKUP_DIST
        }

        _state.update {
            it.copy(
                zombies = workingZombies,
                projectiles = survivingProjectiles,
                playerHealth = newHealth.coerceIn(0f, 100f),
                damagePulseTrigger = pulse,
                zombiesRemaining = workingZombies.count { z -> !z.isDying },
                nearbyItemId = nearItem?.id,
                nearbyKeyId = nearKey?.id,
                activeEffects = if (effectsChanged) stillActive else it.activeEffects
            )
        }

        deadZombieIds.forEach { id ->
            val deadZombie = workingZombies.firstOrNull { it.id == id }
            if (deadZombie != null) {
                viewModelScope.launch { delay(1000L); onZombieDeath(deadZombie) }
            }
        }
    }

internal fun ZombieGameViewModel.tickOnline(s: ZombieGameState, now: Long) {
        val room = ZombieRoomCatalog.rooms[s.currentRoomIndex]

        val stillActive = if (s.activeEffects.isEmpty()) s.activeEffects
                          else s.activeEffects.filter { it.expiresAtMs > now }
        val effectsChanged = stillActive.size != s.activeEffects.size
        val dmgFactor = if (stillActive.isEmpty()) 1f else run {
            var f = 1f
            if (stillActive.any { it.effect == SkillEffect.FURIA_ZOMBI }) f *= ZOMBIE_DMG_FURY_FACTOR
            if (stillActive.any { it.effect == SkillEffect.DEBILIDAD_ZOMBI }) f *= ZOMBIE_DMG_WEAK_FACTOR
            f
        }

        var newHealth = s.playerHealth
        var pulse = s.damagePulseTrigger

        // Proyectiles: al impactar a un zombi del servidor, PEDIMOS daño.
        val survivingProjectiles = mutableListOf<Projectile>()
        for (p in s.projectiles) {
            if (now - p.bornAtMs > PROJECTILE_LIFETIME_MS) continue
            val nx = p.x + p.dirX * PROJECTILE_SPEED
            val ny = p.y + p.dirY * PROJECTILE_SPEED
            if (nx < 0f || ny < 0f || nx > room.worldWidth || ny > room.worldHeight) continue
            // La bala RESPETA la matriz de colisiones (no atraviesa paredes).
            if (!isWalkable(nx, ny)) continue
            val hit = s.zombies.firstOrNull {
                !it.isDying && hypot(it.x - nx, it.y - ny) <= PROJECTILE_HIT_RADIUS
            }
            if (hit != null) {
                sendZombieDamage(hit.id, PROJECTILE_DAMAGE * playerDamageFactor())
                // proyectil consumido
            } else survivingProjectiles.add(p.copy(x = nx, y = ny))
        }

        // Daño de contacto al jugador local (su vida sigue siendo local).
        s.zombies.forEach { z ->
            if (z.isDying) return@forEach
            val dist = hypot(z.x - s.playerX, z.y - s.playerY)
            if (dist <= CONTACT_DIST) {
                val last = contactCooldown[z.id] ?: 0L
                if (now - last >= ZOMBIE_DAMAGE_COOLDOWN_MS) {
                    newHealth -= ZOMBIE_DAMAGE * dmgFactor
                    pulse += 1
                    contactCooldown[z.id] = now
                }
            }
        }

        if (newHealth <= 0f) { triggerWastedSequence(); return }

        // Regeneración gradual de vida en el lobby (zona segura).
        if (room.id == ZombieRoomCatalog.LOBBY_ID && newHealth < 100f) {
            newHealth = (newHealth + LOBBY_REGEN_PER_TICK).coerceAtMost(100f)
        }

        val nearItem = s.items.firstOrNull {
            hypot(it.x - s.playerX, it.y - s.playerY) <= ITEM_PICKUP_DIST
        }

        // NO tocar zombies/items: son autoritativos del servidor.
        _state.update {
            it.copy(
                projectiles = survivingProjectiles,
                playerHealth = newHealth.coerceIn(0f, 100f),
                damagePulseTrigger = pulse,
                nearbyItemId = nearItem?.id,
                activeEffects = if (effectsChanged) stillActive else it.activeEffects
            )
        }
    }

internal fun ZombieGameViewModel.moveZombie(
        z: ZombieEntity, px: Float, py: Float, now: Long,
        room: ZombieRoom, speedFactor: Float
    ): ZombieEntity {
        val dx = px - z.x
        val dy = py - z.y
        val dist = hypot(dx, dy)
        val (nx, ny) = if (dist > 0.01f) (dx / dist) to (dy / dist) else 0f to 0f
        val step = if (dist > CONTACT_DIST * 0.7f) ZOMBIE_SPEED * speedFactor else 0f

        val targetX = (z.x + nx * step).coerceIn(ZOMBIE_RADIUS, room.worldWidth - ZOMBIE_RADIUS)
        val targetY = (z.y + ny * step).coerceIn(ZOMBIE_RADIUS, room.worldHeight - ZOMBIE_RADIUS)

        var rx = z.x; var ry = z.y
        when {
            !room.isBlockedPixel(targetX, targetY) -> { rx = targetX; ry = targetY }
            !room.isBlockedPixel(targetX, z.y) -> rx = targetX
            !room.isBlockedPixel(z.x, targetY) -> ry = targetY
        }

        val isStalker = z.type == ZombieType.STALKER
        val shouldAttack = isStalker && dist < STALKER_ATTACK_DIST

        var nextFrame = z.frameIndex
        if (shouldAttack != z.isAttacking) nextFrame = 0

        val frameCount = when {
            isStalker && shouldAttack -> STALKER_ATTACK_FRAME_COUNT
            isStalker -> STALKER_WALK_FRAME_COUNT
            else -> ZOMBIE_FRAME_COUNT
        }

        val advance = now - z.lastFrameAdvanceMs >= ZOMBIE_FRAME_INTERVAL_MS
        return z.copy(
            x = rx, y = ry,
            facingRight = if (abs(nx) > 0.01f) nx >= 0f else z.facingRight,
            frameIndex = if (advance) (nextFrame + 1) % frameCount else nextFrame,
            lastFrameAdvanceMs = if (advance) now else z.lastFrameAdvanceMs,
            isAttacking = shouldAttack
        )
    }

internal fun ZombieGameViewModel.knockbackZombie(
        zx: Float, zy: Float, fromX: Float, fromY: Float, room: ZombieRoom, dist: Float
    ): Pair<Float, Float> {
        val dx = zx - fromX; val dy = zy - fromY
        val d = hypot(dx, dy)
        if (d < 0.01f) return zx to zy
        val nx = dx / d; val ny = dy / d
        val tx = (zx + nx * dist).coerceIn(ZOMBIE_RADIUS, room.worldWidth - ZOMBIE_RADIUS)
        val ty = (zy + ny * dist).coerceIn(ZOMBIE_RADIUS, room.worldHeight - ZOMBIE_RADIUS)
        return when {
            !room.isBlockedPixel(tx, ty) -> tx to ty
            !room.isBlockedPixel(tx, zy) -> tx to zy
            !room.isBlockedPixel(zx, ty) -> zx to ty
            else -> zx to zy
        }
    }
