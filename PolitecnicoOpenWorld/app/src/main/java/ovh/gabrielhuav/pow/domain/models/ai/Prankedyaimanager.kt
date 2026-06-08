package ovh.gabrielhuav.pow.domain.models.ai

import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.Npc
import ovh.gabrielhuav.pow.domain.models.NpcType
import ovh.gabrielhuav.pow.domain.models.PrankedyPhase
import ovh.gabrielhuav.pow.domain.models.PrankedyProjectile
import ovh.gabrielhuav.pow.domain.models.PrankedyState
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Motor de IA del compañero Prankedy. Se invoca desde el game loop del
 * WorldMapViewModel (cada 3 ticks, igual que NpcAiManager).
 *
 * Responsabilidades:
 *  1. Máquina de estados (follow / sprint / aggro / attack / death / roaming).
 *  2. Detección de enemigos y selección de objetivo.
 *  3. Generación y avance de proyectiles.
 *  4. Spawn / respawn / cooldown.
 */
class PrankedyAiManager {

    // Proyectiles vivos (el render los consume para dibujarlos).
    val projectiles = CopyOnWriteArrayList<PrankedyProjectile>()

    // ── TICK PRINCIPAL ──────────────────────────────────────────────────────

    /**
     * Avanza un tick de la IA de Prankedy. Devuelve el nuevo estado.
     *
     * @param state         estado actual de Prankedy
     * @param playerLat     latitud del jugador
     * @param playerLon     longitud del jugador
     * @param playerDriving si el jugador está conduciendo
     * @param nearbyNpcs    NPCs civiles/policías dentro del radio de detección
     * @param now           System.currentTimeMillis()
     * @param snap          función para ajustar un punto a la calle más cercana
     */
    fun update(
        state: PrankedyState,
        playerLat: Double,
        playerLon: Double,
        playerDriving: Boolean,
        nearbyNpcs: List<Npc>,
        now: Long,
        snap: (GeoPoint) -> GeoPoint
    ): PrankedyState {
        if (!state.spawned) return state

        // ── Avanzar animación ────────────────────────────────────────────
        val advanceFrame = now - state.lastFrameAdvanceMs >= PrankedyState.FRAME_INTERVAL_MS
        val nextFrame = if (advanceFrame) state.frameIndex + 1 else state.frameIndex
        val nextFrameMs = if (advanceFrame) now else state.lastFrameAdvanceMs

        var s = state.copy(frameIndex = nextFrame, lastFrameAdvanceMs = nextFrameMs)

        // ── Avanzar proyectiles ──────────────────────────────────────────
        advanceProjectiles(now, nearbyNpcs)

        // ── Máquina de estados ───────────────────────────────────────────
        s = when (s.phase) {
            PrankedyPhase.IDLE_WILD, PrankedyPhase.AVAILABLE -> tickIdle(s, playerLat, playerLon, now)
            PrankedyPhase.FOLLOW -> tickFollow(s, playerLat, playerLon, playerDriving, nearbyNpcs, now, snap)
            PrankedyPhase.SPRINT -> tickSprint(s, playerLat, playerLon, playerDriving, nearbyNpcs, now, snap)
            PrankedyPhase.AGGRO_RUN -> tickAggroRun(s, playerLat, playerLon, nearbyNpcs, now, snap)
            PrankedyPhase.ATTACK -> tickAttack(s, playerLat, playerLon, nearbyNpcs, now)
            PrankedyPhase.IN_VEHICLE -> tickInVehicle(s, playerLat, playerLon, playerDriving, now)
            PrankedyPhase.DEAD -> tickDead(s, playerLat, playerLon, now, snap)
            PrankedyPhase.ROAMING -> tickRoaming(s, playerLat, playerLon, now, snap)
        }

        return s
    }

    // ── ESTADOS ─────────────────────────────────────────────────────────────

    private fun tickIdle(s: PrankedyState, pLat: Double, pLon: Double, now: Long): PrankedyState {
        val dist = dist(s.latitude, s.longitude, pLat, pLon)
        // Mostrar globo de texto si el jugador está cerca.
        val showBubble = dist <= PrankedyState.SPEECH_RADIUS
        val text = if (showBubble && !s.showSpeechBubble) PrankedyState.SPEECH_LINES.random() else s.speechText
        // IDLE_WILD / AVAILABLE: siempre quieto, sin moverse (usa p_idle assets).
        // frameIndex se avanza arriba; PrankedySpriteManager mapea estas fases a p_idle.
        return s.copy(
            showSpeechBubble = showBubble,
            speechText = text,
            isMoving = false    // NUNCA se mueve en idle
        )
    }

    private fun tickFollow(
        s: PrankedyState, pLat: Double, pLon: Double, driving: Boolean,
        npcs: List<Npc>, now: Long, snap: (GeoPoint) -> GeoPoint
    ): PrankedyState {
        // Jugador subió al coche → esconderse.
        if (driving) return s.copy(
            phase = PrankedyPhase.IN_VEHICLE, isMoving = false,
            showSpeechBubble = false
        )

        // ¿Detecta un enemigo?
        val enemy = findEnemy(s, npcs)
        if (enemy != null) {
            return s.copy(
                phase = PrankedyPhase.AGGRO_RUN,
                aggroTargetId = enemy.id,
                showSpeechBubble = false
            )
        }

        // ── DIÁLOGOS ALEATORIOS MIENTRAS SIGUE AL JUGADOR ──────────────
        var dialogueState = s
        dialogueState = tickHiredDialogue(dialogueState, now)

        val dist = dist(dialogueState.latitude, dialogueState.longitude, pLat, pLon)

        // ¿Demasiado lejos? → sprint (ocultar diálogo para no distraer).
        if (dist > PrankedyState.SPRINT_THRESHOLD) {
            return dialogueState.copy(phase = PrankedyPhase.SPRINT, showSpeechBubble = false)
        }

        // ¿Ya suficientemente cerca? → quedarse quieto mirando al jugador.
        if (dist <= PrankedyState.FOLLOW_OFFSET) {
            val facing = cos(atan2(pLat - dialogueState.latitude, pLon - dialogueState.longitude)) >= 0
            return dialogueState.copy(isMoving = false, facingRight = facing)
        }

        // Caminar hacia el jugador.
        return moveToward(dialogueState, pLat, pLon, PrankedyState.FOLLOW_SPEED, snap, moving = true)
    }

    /**
     * Gestiona los diálogos periódicos de Prankedy mientras está contratado.
     * Programa un momento futuro para el próximo diálogo, lo muestra, y lo
     * oculta cuando expira. Se llama desde tickFollow cada tick.
     */
    private fun tickHiredDialogue(s: PrankedyState, now: Long): PrankedyState {
        // ¿El globo actual ya expiró?
        if (s.showSpeechBubble && s.dialogueExpiresMs > 0L && now >= s.dialogueExpiresMs) {
            return s.copy(showSpeechBubble = false, speechText = "")
        }

        // ¿Toca soltar una nueva frase?
        if (!s.showSpeechBubble && now >= s.nextDialogueMs) {
            val nextDelay = PrankedyState.HIRED_DIALOGUE_MIN_MS +
                    (Math.random() * (PrankedyState.HIRED_DIALOGUE_MAX_MS - PrankedyState.HIRED_DIALOGUE_MIN_MS)).toLong()
            return s.copy(
                showSpeechBubble = true,
                speechText = PrankedyState.HIRED_SPEECH_LINES.random(),
                dialogueExpiresMs = now + PrankedyState.HIRED_DIALOGUE_DURATION_MS,
                nextDialogueMs = now + PrankedyState.HIRED_DIALOGUE_DURATION_MS + nextDelay
            )
        }

        return s
    }

    private fun tickSprint(
        s: PrankedyState, pLat: Double, pLon: Double, driving: Boolean,
        npcs: List<Npc>, now: Long, snap: (GeoPoint) -> GeoPoint
    ): PrankedyState {
        if (driving) return s.copy(phase = PrankedyPhase.IN_VEHICLE, isMoving = false, showSpeechBubble = false)

        val dist = dist(s.latitude, s.longitude, pLat, pLon)
        if (dist <= PrankedyState.FOLLOW_OFFSET * 1.5) {
            return s.copy(phase = PrankedyPhase.FOLLOW)
        }

        // Mientras corre, también escanea enemigos.
        val enemy = findEnemy(s, npcs)
        if (enemy != null) {
            return s.copy(phase = PrankedyPhase.AGGRO_RUN, aggroTargetId = enemy.id)
        }

        return moveToward(s, pLat, pLon, PrankedyState.SPRINT_SPEED, snap, moving = true)
    }

    private fun tickAggroRun(
        s: PrankedyState, pLat: Double, pLon: Double,
        npcs: List<Npc>, now: Long, snap: (GeoPoint) -> GeoPoint
    ): PrankedyState {
        val target = npcs.firstOrNull { it.id == s.aggroTargetId && !it.isDying }
        if (target == null) {
            // Objetivo muerto/despawneado → volver a seguir.
            return s.copy(phase = PrankedyPhase.FOLLOW, aggroTargetId = null)
        }

        val distToTarget = dist(s.latitude, s.longitude, target.location.latitude, target.location.longitude)
        if (distToTarget <= PrankedyState.ATTACK_RANGE) {
            return s.copy(phase = PrankedyPhase.ATTACK, isMoving = false)
        }

        // No alejarse demasiado del jugador (máximo ~120 m).
        val distToPlayer = dist(s.latitude, s.longitude, pLat, pLon)
        if (distToPlayer > 0.0012) {
            return s.copy(phase = PrankedyPhase.FOLLOW, aggroTargetId = null)
        }

        return moveToward(s, target.location.latitude, target.location.longitude,
            PrankedyState.AGGRO_SPEED, snap, moving = true)
    }

    private fun tickAttack(
        s: PrankedyState, pLat: Double, pLon: Double,
        npcs: List<Npc>, now: Long
    ): PrankedyState {
        val target = npcs.firstOrNull { it.id == s.aggroTargetId && !it.isDying }
        if (target == null) {
            return s.copy(phase = PrankedyPhase.FOLLOW, aggroTargetId = null)
        }

        val distToTarget = dist(s.latitude, s.longitude, target.location.latitude, target.location.longitude)

        // Si se alejó, volver a perseguir.
        if (distToTarget > PrankedyState.ATTACK_RANGE * 1.5) {
            return s.copy(phase = PrankedyPhase.AGGRO_RUN)
        }

        // Mirar al objetivo.
        val angle = atan2(target.location.latitude - s.latitude, target.location.longitude - s.longitude)
        val facing = cos(angle) >= 0

        // Disparar si el cooldown pasó.
        if (now - s.lastAttackMs >= PrankedyState.ATTACK_COOLDOWN_MS) {
            fireProjectile(s, target, now)
            return s.copy(lastAttackMs = now, facingRight = facing, frameIndex = 0)
        }

        return s.copy(facingRight = facing)
    }

    private fun tickInVehicle(
        s: PrankedyState, pLat: Double, pLon: Double,
        driving: Boolean, now: Long
    ): PrankedyState {
        if (!driving) {
            // Jugador se bajó → reaparecer a su lado.
            val ang = Random.nextDouble(0.0, 2.0 * Math.PI)
            val offLat = pLat + sin(ang) * PrankedyState.FOLLOW_OFFSET
            val offLon = pLon + cos(ang) * PrankedyState.FOLLOW_OFFSET
            return s.copy(
                phase = PrankedyPhase.FOLLOW,
                latitude = offLat,
                longitude = offLon,
                isMoving = false
            )
        }
        // Mientras conduce, sigue la posición del jugador (invisible).
        return s.copy(latitude = pLat, longitude = pLon)
    }

    private fun tickDead(
        s: PrankedyState, pLat: Double, pLon: Double,
        now: Long, snap: (GeoPoint) -> GeoPoint
    ): PrankedyState {
        if (now - s.deathTimeMs < PrankedyState.DEATH_COOLDOWN_MS) return s

        // Respawn cerca del jugador → fase ROAMING (pierde el contrato).
        val ang = Random.nextDouble(0.0, 2.0 * Math.PI)
        val spawnLat = pLat + sin(ang) * PrankedyState.SPAWN_DISTANCE
        val spawnLon = pLon + cos(ang) * PrankedyState.SPAWN_DISTANCE
        val snapped = snap(GeoPoint(spawnLat, spawnLon))
        return s.copy(
            phase = PrankedyPhase.ROAMING,
            hired = false,
            health = s.maxHealth,
            latitude = snapped.latitude,
            longitude = snapped.longitude,
            roamingStartMs = now,
            isMoving = true,
            showSpeechBubble = false,
            aggroTargetId = null
        )
    }

    private fun tickRoaming(
        s: PrankedyState, pLat: Double, pLon: Double,
        now: Long, snap: (GeoPoint) -> GeoPoint
    ): PrankedyState {
        if (now - s.roamingStartMs >= PrankedyState.ROAMING_DURATION_MS) {
            // Penalización cumplida → disponible para re-contratar.
            return s.copy(phase = PrankedyPhase.AVAILABLE, isMoving = false)
        }

        // Deambular en dirección aleatoria (cambia cada ~3 s).
        val wanderAngle = ((now / 3000L) % 8) * (Math.PI / 4.0)
        val targetLat = s.latitude + sin(wanderAngle) * PrankedyState.FOLLOW_SPEED * 2
        val targetLon = s.longitude + cos(wanderAngle) * PrankedyState.FOLLOW_SPEED * 2
        return moveToward(s, targetLat, targetLon, PrankedyState.FOLLOW_SPEED * 0.7, snap, moving = true)
    }

    // ── COMBATE ─────────────────────────────────────────────────────────────

    /** Busca el NPC hostil más cercano a Prankedy dentro del radio de detección. */
    private fun findEnemy(s: PrankedyState, npcs: List<Npc>): Npc? {
        return npcs.filter { npc ->
            !npc.isDying &&
                    npc.displayName.isNullOrEmpty() && // no es un jugador remoto
                    (npc.type == NpcType.PERSON || npc.type == NpcType.POLICE_COP) &&
                    // Solo ataca a los que están agrediendo (aggro > 0) o son policías persiguiendo.
                    (npc.aggroUntil > System.currentTimeMillis() || npc.type == NpcType.POLICE_COP)
        }.minByOrNull { dist(s.latitude, s.longitude, it.location.latitude, it.location.longitude) }
            ?.takeIf { dist(s.latitude, s.longitude, it.location.latitude, it.location.longitude) <= PrankedyState.AGGRO_DETECT_RADIUS }
    }

    /** Crea un proyectil dirigido al objetivo. */
    private fun fireProjectile(s: PrankedyState, target: Npc, now: Long) {
        val dLat = target.location.latitude - s.latitude
        val dLon = target.location.longitude - s.longitude
        val d = sqrt(dLat * dLat + dLon * dLon)
        if (d < 1e-9) return
        val nLat = dLat / d
        val nLon = dLon / d

        projectiles.add(
            PrankedyProjectile(
                latitude = s.latitude,
                longitude = s.longitude,
                targetLat = target.location.latitude,
                targetLon = target.location.longitude,
                dirLat = nLat,
                dirLon = nLon,
                bornAtMs = now
            )
        )
    }

    /**
     * Avanza los proyectiles y devuelve los IDs de los NPCs golpeados
     * (para que el ViewModel les aplique daño).
     */
    fun advanceProjectiles(now: Long, npcs: List<Npc>): List<Pair<String, Float>> {
        val hits = mutableListOf<Pair<String, Float>>()
        val iterator = projectiles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            // Expirado.
            if (now - p.bornAtMs > PrankedyState.PROJECTILE_LIFETIME_MS) {
                iterator.remove(); continue
            }
            // Mover.
            val newLat = p.latitude + p.dirLat * PrankedyState.PROJECTILE_SPEED
            val newLon = p.longitude + p.dirLon * PrankedyState.PROJECTILE_SPEED

            // Avanzar frame de animación.
            val advFrame = now - p.lastFrameMs >= 120L
            val newFrame = if (advFrame) p.frameIndex + 1 else p.frameIndex
            val newFrameMs = if (advFrame) now else p.lastFrameMs

            // Colisión con NPCs.
            val hit = npcs.firstOrNull { npc ->
                !npc.isDying &&
                        dist(newLat, newLon, npc.location.latitude, npc.location.longitude) <= PrankedyState.PROJECTILE_HIT_RADIUS
            }
            if (hit != null) {
                hits.add(hit.id to PrankedyState.PROJECTILE_DAMAGE)
                iterator.remove()
            } else {
                // Actualizar posición in-place (CopyOnWriteArrayList permite set).
                val idx = projectiles.indexOf(p)
                if (idx >= 0) {
                    projectiles[idx] = p.copy(
                        latitude = newLat, longitude = newLon,
                        frameIndex = newFrame, lastFrameMs = newFrameMs
                    )
                }
            }
        }
        return hits
    }

    /** Daña a Prankedy. Devuelve el estado actualizado (o fase DEAD si muere). */
    fun takeDamage(s: PrankedyState, amount: Float, now: Long): PrankedyState {
        val newHealth = (s.health - amount).coerceAtLeast(0f)
        if (newHealth <= 0f) {
            projectiles.clear()
            return s.copy(
                phase = PrankedyPhase.DEAD,
                health = 0f,
                deathTimeMs = now,
                spawned = true, // sigue "existiendo" en cooldown
                isMoving = false,
                showSpeechBubble = false,
                aggroTargetId = null
            )
        }
        return s.copy(health = newHealth)
    }

    /** Fuerza el aggro de Prankedy hacia el atacante del jugador. */
    fun setAggroTarget(s: PrankedyState, targetId: String): PrankedyState {
        if (!s.hired || s.phase == PrankedyPhase.DEAD || s.phase == PrankedyPhase.IN_VEHICLE) return s
        return s.copy(
            phase = PrankedyPhase.AGGRO_RUN,
            aggroTargetId = targetId
        )
    }

    // ── SPAWN INICIAL ───────────────────────────────────────────────────────

    fun spawnNear(playerLat: Double, playerLon: Double, snap: (GeoPoint) -> GeoPoint): PrankedyState {
        val ang = Random.nextDouble(0.0, 2.0 * Math.PI)
        val lat = playerLat + sin(ang) * PrankedyState.SPAWN_DISTANCE
        val lon = playerLon + cos(ang) * PrankedyState.SPAWN_DISTANCE
        val snapped = snap(GeoPoint(lat, lon))
        return PrankedyState(
            phase = PrankedyPhase.IDLE_WILD,
            hired = false,
            latitude = snapped.latitude,
            longitude = snapped.longitude,
            spawned = true,
            health = PrankedyState().maxHealth,
            speechText = PrankedyState.SPEECH_LINES.random()
        )
    }

    /** Contrata a Prankedy (el jugador aceptó en el modal). */
    fun hire(s: PrankedyState): PrankedyState {
        val now = System.currentTimeMillis()
        // Primer diálogo contratado: entre 5-10 s después de aceptar.
        val firstDialogue = now + 5_000L + (Math.random() * 5_000L).toLong()
        return s.copy(
            phase = PrankedyPhase.FOLLOW,
            hired = true,
            showSpeechBubble = false,
            speechText = "",
            nextDialogueMs = firstDialogue,
            dialogueExpiresMs = 0L
        )
    }

    // ── UTILIDADES ──────────────────────────────────────────────────────────

    private fun moveToward(
        s: PrankedyState, tLat: Double, tLon: Double, speed: Double,
        snap: (GeoPoint) -> GeoPoint, moving: Boolean
    ): PrankedyState {
        val dLat = tLat - s.latitude
        val dLon = tLon - s.longitude
        val d = sqrt(dLat * dLat + dLon * dLon)
        if (d < 1e-9) return s.copy(isMoving = false)
        val angle = atan2(dLat, dLon)
        val newLat = s.latitude + sin(angle) * speed
        val newLon = s.longitude + cos(angle) * speed
        val snapped = snap(GeoPoint(newLat, newLon))
        val facing = cos(angle) >= 0
        val rot = (-Math.toDegrees(angle).toFloat() + 360f) % 360f
        return s.copy(
            latitude = snapped.latitude,
            longitude = snapped.longitude,
            rotationAngle = rot,
            facingRight = facing,
            isMoving = moving
        )
    }

    private fun dist(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = lat1 - lat2
        val dLon = (lon1 - lon2) * cos(lat1 * Math.PI / 180.0)
        return sqrt(dLat * dLat + dLon * dLon)
    }
}