package ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ovh.gabrielhuav.pow.domain.models.zombie.Projectile
import ovh.gabrielhuav.pow.domain.models.zombie.SkillEffect
import ovh.gabrielhuav.pow.domain.models.zombie.ZombieEntity
import ovh.gabrielhuav.pow.domain.models.zombie.ZombieRoom
import ovh.gabrielhuav.pow.domain.models.zombie.ZombieRoomCatalog
import ovh.gabrielhuav.pow.domain.models.zombie.ZombieType
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction
import kotlin.math.abs
import kotlin.math.hypot

internal fun ZombieInteriorViewModel.tick() {
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

internal fun ZombieInteriorViewModel.tickOffline(s: ZombieGameState, now: Long) {
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

        // MISIÓN 2 · FASE 1 "ESCONDERSE" (lobby): los policías m2cop_* viven dentro de
        // ambientNpcs pero se simulan APARTE (patrulla + barridos hacia el jugador); los
        // estudiantes siguen con su vida universitaria normal alrededor.
        val m2c = ovh.gabrielhuav.pow.domain.models.campaign.mission2.Mission2
        val hideCops0 = if (s.mission2HideActive)
            s.ambientNpcs.filter { it.id.startsWith(M2COP_PREFIX) } else emptyList()
        val students0 = if (hideCops0.isEmpty()) s.ambientNpcs
            else s.ambientNpcs.filterNot { it.id.startsWith(M2COP_PREFIX) }

        var hideCops = hideCops0
        var hideFailed = s.mission2HideFailed
        var hideCompleted = s.mission2HideCompleted
        var hideRemaining: Int? = s.mission2HideRemainingSec
        if (s.mission2HideActive && !hideFailed && !hideCompleted) {
            val elapsed = now - mission2HideStartMs
            if (elapsed >= m2c.HIDE_DURATION_MS) {
                // Se RINDIERON: corren a la puerta y desaparecen (reusa la evacuación). Cuando
                // sale el último, la fase queda CUMPLIDA (ZombieGameScreen avisa al mundo).
                hideCops = evacuateAmbientNpcs(hideCops0, room)
                hideRemaining = 0
                if (hideCops.isEmpty()) hideCompleted = true
            } else if (hideCops0.isNotEmpty()) {
                hideCops = stepMission2HideCops(hideCops0, room, s.playerX, s.playerY, now)
                hideRemaining = (((m2c.HIDE_DURATION_MS - elapsed) / 1000L) + 1L).toInt()
                // ¿Te está VIENDO alguno? Sostenido HIDE_DETECT_MS → te reconoció (fallo).
                val nearest = hideCops.minOf { hypot(it.x - s.playerX, it.y - s.playerY) }
                if (nearest < m2c.HIDE_DETECT_PX) {
                    if (mission2HideDetectSinceMs == 0L) mission2HideDetectSinceMs = now
                    if (now - mission2HideDetectSinceMs > m2c.HIDE_DETECT_MS) hideFailed = true
                } else {
                    mission2HideDetectSinceMs = 0L
                }
            }
        }

        // MISIÓN 2 · SALÓN DE LA MOCHILA: tras la lata apestosa los alumnos EVACÚAN (corren a la
        // puerta y desaparecen); con el salón vacío APARECE la mochila junto al escritorio.
        val inM2Salon = room.id == ZombieRoomCatalog.ESCOM_SALON_M2_ID
        val steppedAmbient = (if (inM2Salon && s.mission2StinkThrown)
            evacuateAmbientNpcs(students0, room)
        else
            stepAmbientNpcs(students0, room, now)) + hideCops
        val spawnBackpack = inM2Salon && s.mission2StinkThrown && steppedAmbient.isEmpty() &&
            s.mission2BackpackX == null && !s.mission2BackpackTaken
        val bpX = if (spawnBackpack) room.worldWidth * 0.50f else s.mission2BackpackX
        val bpY = if (spawnBackpack) room.worldHeight * 0.42f else s.mission2BackpackY
        val bpNear = bpX != null && bpY != null && !s.mission2BackpackTaken &&
            hypot(bpX - s.playerX, bpY - s.playerY) <= ITEM_PICKUP_DIST * 1.6f

        // MISIÓN 3 · asalto ENCB: ¿el jugador está sobre la EVIDENCIA 🧪 (encb_lab1)?
        val evX = s.mission3EvidenceX
        val evY = s.mission3EvidenceY
        val evNear = evX != null && evY != null && !s.mission3EvidenceTaken &&
            hypot(evX - s.playerX, evY - s.playerY) <= ITEM_PICKUP_DIST * 1.6f

        _state.update {
            it.copy(
                zombies = workingZombies,
                projectiles = survivingProjectiles,
                playerHealth = newHealth.coerceIn(0f, 100f),
                damagePulseTrigger = pulse,
                zombiesRemaining = workingZombies.count { z -> !z.isDying },
                nearbyItemId = nearItem?.id,
                nearbyKeyId = nearKey?.id,
                activeEffects = if (effectsChanged) stillActive else it.activeEffects,
                ambientNpcs = if (hideFailed) steppedAmbient.filterNot { n ->
                    n.id.startsWith(M2COP_PREFIX) } else steppedAmbient,
                // MISIÓN 2 · fase ESCONDERSE: desenlace (los flags los consume ZombieGameScreen).
                mission2HideActive = it.mission2HideActive && !hideCompleted && !hideFailed,
                mission2HideRemainingSec = if (hideCompleted || hideFailed) null else hideRemaining,
                mission2HideCompleted = hideCompleted,
                mission2HideFailed = hideFailed,
                mission2BackpackX = bpX,
                mission2BackpackY = bpY,
                mission2BackpackNearby = bpNear,
                mission3EvidenceNearby = evNear
            )
        }
        if (spawnBackpack) {
            soundManager.playItem()
            showKeyMessage("🎒 ¡El salón quedó vacío! Ahí está la mochila de Prankedy.")
        }

        deadZombieIds.forEach { id ->
            val deadZombie = workingZombies.firstOrNull { it.id == id }
            if (deadZombie != null) {
                viewModelScope.launch { delay(1000L); onZombieDeath(deadZombie) }
            }
        }
    }

internal fun ZombieInteriorViewModel.tickOnline(s: ZombieGameState, now: Long) {
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

internal fun ZombieInteriorViewModel.moveZombie(
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

internal fun ZombieInteriorViewModel.knockbackZombie(
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
