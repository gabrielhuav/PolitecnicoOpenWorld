package ovh.gabrielhuav.pow.features.streetfighter.viewmodel

import kotlinx.coroutines.flow.update
import ovh.gabrielhuav.pow.domain.models.streetfighter.SF_BLOCK_STATES
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfDamage
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfPhysics
import ovh.gabrielhuav.pow.domain.models.streetfighter.SF_DOWNED_STATES
import ovh.gabrielhuav.pow.domain.models.streetfighter.SF_PARRY_STATES
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfAttackStrength
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfAttackType
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfBox
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfConstants
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfDirection
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighter
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterId
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterState
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfHitSplash
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfHurtArea
import kotlin.random.Random

// ────────────────────────────────────────────────────────────────────────────
// PARCIAL de StreetFighterViewModel: 💥 COMBATE: colisiones, impactos, daño y empuje contra los bordes del escenario
//
// Aquí se resuelve el CHOQUE: qué caja golpea a cuál, cuánto daño hace, el agarre, el empuje de
// pushboxes contra los límites del escenario y las metamorfosis que dispara recibir daño.
// ⚠️ `applyAttackHit` es la función MÁS delicada del modo: decide bloqueo, chip damage, combo,
// parry, carga de súper y KO. Cambiarla afecta a TODAS las peleas — hay tests de
// caracterización del daño en `SfDamageTest` (módulo `:shared`).
//
// ⚠️ Son EXTENSIONES del VM, no miembros: el estado sigue viviendo en la clase. NO recrees
// estas funciones como miembros — quedarían gemelas y ganaría el miembro EN SILENCIO
// (ver 09 §0, el gotcha miembro-vs-extensión que ya costó caro en este repo).
// ────────────────────────────────────────────────────────────────────────────

internal fun StreetFighterViewModel.frameDef(f: SfFighter) = dataFor(f).frames.getValue(animOf(f)[f.animationFrame.coerceIn(0, animOf(f).size - 1)].frameKey)

internal fun StreetFighterViewModel.pushBoxWorld(f: SfFighter): SfBox = SfBox.fromList(frameDef(f).push).toWorld(f.x, f.y, f.direction)

internal fun StreetFighterViewModel.updateStageConstraints(sim: StreetFighterViewModel.Sim, idx: Int, dt: Float) {
    var f = sim.fighter(idx)
    val push = SfBox.fromList(frameDef(f).push)

    // 1) Clamp AL ESCENARIO MUNDO (nunca fuera del stage — evita “desaparecer”
    // en IA vs IA cuando el empuje/slide los lanza fuera de cámara).
    f = clampFighterToStage(f)

    // 2) Límites del viewport (como el JS, contra la cámara)
    val margin = SfConstants.FIGHTER_DEFAULT_WIDTH
    if (f.x - sim.camX + margin > SfConstants.SCENE_WIDTH) {
        f = f.copy(x = sim.camX + SfConstants.SCENE_WIDTH - margin)
    } else if (f.x - sim.camX - margin < 0f) {
        f = f.copy(x = sim.camX + margin)
    }
    f = clampFighterToStage(f)
    sim.setFighter(idx, f)

    // Empuje al traslaparse los pushbox (updateStageConstraints del JS)
    var opp = sim.fighter(1 - idx)
    if (!pushBoxWorld(f).overlaps(pushBoxWorld(opp))) {
        // Aun sin overlap, re-asegura al rival (el otro update lo hará también)
        return
    }

    // Incluye caminar: si no, al chocar en WALK se “congelan” empujándose sin resolverse.
    val pushableStates = setOf(
        SfFighterState.IDLE, SfFighterState.CROUCH, SfFighterState.JUMP_UP,
        SfFighterState.JUMP_BACKWARD, SfFighterState.JUMP_FORWARD,
        SfFighterState.WALK_FORWARD, SfFighterState.WALK_BACKWARD,
        SfFighterState.IDLE_TURN, SfFighterState.CROUCH_TURN,
    )
    if (f.x <= opp.x) {
        val nx = opp.x + SfBox.fromList(frameDef(opp).push).x - (push.x + push.width)
        f = f.copy(x = nx.coerceIn(StreetFighterViewModel.STAGE_X_MIN, StreetFighterViewModel.STAGE_X_MAX))
        if (opp.state in pushableStates) {
            opp = clampFighterToStage(
                opp.copy(x = opp.x + SfConstants.FIGHTER_PUSH_FRICTION * dt),
            )
        }
    } else {
        val nx = minOf(
            sim.camX + SfConstants.SCENE_WIDTH - push.width.coerceAtLeast(1f),
            opp.x + SfBox.fromList(frameDef(opp).push).width,
        )
        f = f.copy(x = nx.coerceIn(StreetFighterViewModel.STAGE_X_MIN, StreetFighterViewModel.STAGE_X_MAX))
        if (opp.state in pushableStates) {
            opp = clampFighterToStage(
                opp.copy(x = opp.x - SfConstants.FIGHTER_PUSH_FRICTION * dt),
            )
        }
    }
    sim.setFighter(idx, clampFighterToStage(f))
    sim.setFighter(1 - idx, clampFighterToStage(opp))
}

// 🆕 (2026-07-22, Fase 2b) Extraído a SfPhysics.clampToStage (puro); el VM solo delega.
internal fun StreetFighterViewModel.clampFighterToStage(f: SfFighter): SfFighter = SfPhysics.clampToStage(f)

internal fun StreetFighterViewModel.updateAttackBoxCollided(sim: StreetFighterViewModel.Sim, idx: Int, now: Long) {
    val attacker = sim.fighter(idx)
    val meta = attackMeta[attacker.state] ?: return
    if (attacker.attackStruck) return
    val hit = frameDef(attacker).hit ?: return
    if (hit[2] == 0 || hit[3] == 0) return
    val actualHit = SfBox.fromList(hit).toWorld(attacker.x, attacker.y, attacker.direction)

    val defender = sim.fighter(1 - idx)
    val hurtRows = frameDef(defender).hurt ?: return
    for ((i, area) in SfHurtArea.entries.withIndex()) {
        val hurtBox = SfBox.fromList(hurtRows.getOrNull(i)).toWorld(defender.x, defender.y, defender.direction)
        // Un golpe puede tocar cuerpo o piernas sin tocar cabeza. Salir aquí descartaba
        // las zonas posteriores y volvía inofensivos muchos ataques válidos.
        if (!actualHit.overlaps(hurtBox)) continue

        var hitX = (actualHit.x + actualHit.width / 2f + hurtBox.x + hurtBox.width / 2f) / 2f
        var hitY = (actualHit.y + hurtBox.y + actualHit.height / 2f + hurtBox.width / 2f) / 2f
        hitX += 4f - Random.nextFloat() * SfConstants.HIT_SPLASH_RANDOMNESS
        hitY += 4f - Random.nextFloat() * SfConstants.HIT_SPLASH_RANDOMNESS

        applyAttackHit(sim, idx, meta.strength, meta.type, area, hitX to hitY, now)
        return
    }
}

/**
 * 🆕 (2026-07-21) Daño REAL de un ataque. Los movimientos nuevos no siempre usan el
 * daño de su fuerza base: la súper, el fatality y el agarre tienen el suyo. Se usa
 * tanto offline como al avisar por RED (sendDamage), para que en línea peguen igual.
 */
internal fun StreetFighterViewModel.damageForAttack(attacker: SfFighter, strength: SfAttackStrength): Int =
    SfDamage.forAttack(attacker.state, strength)

/** 🆕 (2026-07-21) Suma al medidor de súper con tope en el máximo. */
internal fun StreetFighterViewModel.chargeSuper(f: SfFighter, amount: Int): Int =
    (f.superMeter + amount).coerceIn(0, SfConstants.SUPER_METER_MAX)

/**
 * 🆕 (2026-07-21) LANZAMIENTO: el agarre conectó. Daño fijo, el atacante ejecuta THROW
 * y el rival sale despedido y queda DERRIBADO (THROWN → GET_UP). Sin pose de HURT: el
 * lanzamiento tiene su propia animación de recibirlo.
 */
internal fun StreetFighterViewModel.applyThrow(sim: StreetFighterViewModel.Sim, attackerIdx: Int, defenderIdx: Int, now: Long) {
    val attacker = sim.fighter(attackerIdx)
    var defender = sim.fighter(defenderIdx)
    _soundEvents.tryEmit("heavy-punch-hit")
    defender = defender.copy(
        hitPoints = (defender.hitPoints - SfConstants.THROW_DAMAGE).coerceAtLeast(0),
        slideVelocity = SfConstants.THROW_PUSH_VELOCITY,
        slideFriction = SfAttackStrength.HEAVY.slideFriction,
        direction = attacker.direction.opposite(),
        superMeter = chargeSuper(defender, SfConstants.SUPER_METER_ON_TAKE),
    )
    sim.setFighter(defenderIdx, defender)
    sim.setFighter(
        attackerIdx,
        attacker.copy(superMeter = chargeSuper(attacker, SfConstants.SUPER_METER_ON_HIT)),
    )
    superKeepMs[attackerIdx] = now // 🆕 conectó el agarre: su súper no decae aún
    if (attackerIdx == 0) sim.score0 += SfAttackStrength.HEAVY.score
    else sim.score1 += SfAttackStrength.HEAVY.score
    // El atacante pasa a la animación de lanzar (si la tiene)
    changeState(sim, attackerIdx, SfFighterState.THROW, now)
    if (defender.hitPoints <= 0) {
        changeState(sim, defenderIdx, SfFighterState.KO, now)
        sim.setFighter(attackerIdx, sim.fighter(attackerIdx).copy(victory = true))
        if (gauntletActive && !showcaseMode) gauntletKoRounds++
        endRound(sim, attackerIdx, now, computeRoundOutcome(sim, attackerIdx, SfFighterState.THROW, byTime = false))
    } else if (!forceState(sim, defenderIdx, SfFighterState.THROWN, now)) {
        // Sin arte de "ser lanzado": al menos reacciona con el daño clásico
        changeState(sim, defenderIdx, SfFighterState.HURT_BODY_HEAVY, now)
    }
    withNetAudioCapture(defenderIdx) { emitHurtVoice(defender.id, defenderIdx, defender.hitPoints, now) }
    hurtFreezeUntilMs =
        now + (SfConstants.FIGHTER_STRUCK_DELAY * SfConstants.FRAME_TIME_MS).toLong()
}

/** handleAttackHit del JS + BattleScene.handleAttackHit (daño, score, KO, splash, hit-freeze). */
internal fun StreetFighterViewModel.applyAttackHit(
    sim: StreetFighterViewModel.Sim,
    attackerIdx: Int,
    strength: SfAttackStrength,
    type: SfAttackType,
    area: SfHurtArea,
    hitPos: Pair<Float, Float>?,
    now: Long,
) {
    val defenderIdx = 1 - attackerIdx
    var attacker = sim.fighter(attackerIdx)
    var defender = sim.fighter(defenderIdx)

    // 🆕 (2026-07-18j) SHOWCASE: los golpes/proyectiles espejados NO restan vida ni cambian
    // el estado (el guion controla las poses; antes los 10 poderes de La Presidenta sumaban
    // 200 de daño → KO y el combate se cortaba a media pasarela). Solo suenan y hacen splash
    // (de paso es el QA de los .ogg de impacto).
    if (showcaseMode) {
        _soundEvents.tryEmit("${strength.name.lowercase()}-${type.name.lowercase()}-hit")
        sim.setFighter(attackerIdx, attacker.copy(attackStruck = true))
        hitPos?.let { (x, y) ->
            sim.splashes.add(SfHitSplash(x = x, y = y, playerId = attackerIdx, strength = strength, animationTimerMs = now))
        }
        return
    }

    // Metamorfosis en curso: invulnerable (no se puede “matar” a media anim)
    if (isMetamorphosing(defender)) {
        sim.setFighter(attackerIdx, attacker.copy(attackStruck = true))
        return
    }

    // ONLINE: el HP del RIVAL es suyo (autoridad del receptor). Si MI golpe/proyectil
    // conecta con él, solo AVISO (PLAYER_DAMAGE) + efectos optimistas locales; su HP y
    // su pose de daño llegarán en su siguiente OPPONENT_STATE.
    if (inOnlineFight && defenderIdx == 1) {
        _soundEvents.tryEmit("${strength.name.lowercase()}-${type.name.lowercase()}-hit")
        sim.setFighter(attackerIdx, attacker.copy(attackStruck = true))
        // 🆕 (2026-07-21) ONLINE: hay que avisar el daño REAL del movimiento. Súper,
        // fatality y agarre NO usan el daño de su fuerza base; sin esto, en línea un
        // fatality pegaba como un golpe fuerte normal (28 en vez de 70).
        transport?.sendDamage(damageForAttack(attacker, strength), strength.name, type.name)
        hitPos?.let { (x, y) ->
            sim.splashes.add(SfHitSplash(x = x, y = y, playerId = attackerIdx, strength = strength, animationTimerMs = now))
        }
        hurtFreezeUntilMs = now + (SfConstants.FIGHTER_STRUCK_DELAY * SfConstants.FRAME_TIME_MS).toLong() / 2
        return
    }

    // Ventana de salida tras una cadena rápida: el siguiente impacto no vuelve a encerrar
    // al defensor en HURT. El atacante consume su golpe para que no reintente cada frame.
    if (now < comboEscapeUntilMs[defenderIdx]) {
        sim.setFighter(attackerIdx, attacker.copy(attackStruck = true))
        return
    }

    // 🆕 (2026-07-21) TUTORIAL: el muñeco NO pierde vida (la lección no debe acabarse por
    // KO) pero sí reacciona y suena, para que se vea que el golpe conectó.
    if (inTutorial && defenderIdx == 1) {
        _soundEvents.tryEmit("${strength.name.lowercase()}-${type.name.lowercase()}-hit")
        sim.setFighter(attackerIdx, attacker.copy(attackStruck = true))
        hitPos?.let { (x, y) ->
            sim.splashes.add(
                SfHitSplash(x = x, y = y, playerId = attackerIdx, strength = strength, animationTimerMs = now),
            )
        }
        // El jugador sí carga medidor: así puede practicar la SÚPER del catálogo.
        sim.setFighter(
            attackerIdx,
            sim.fighter(attackerIdx)
                .copy(superMeter = chargeSuper(sim.fighter(attackerIdx), SfConstants.SUPER_METER_ON_HIT)),
        )
        superKeepMs[attackerIdx] = now // 🆕 en tutorial también refresca la gracia
        hurtFreezeUntilMs = now + (SfConstants.FIGHTER_STRUCK_DELAY * SfConstants.FRAME_TIME_MS).toLong() / 2
        return
    }

    // 🆕 (2026-07-21) DERRIBADO = INVULNERABLE (como en el arcade): no se puede seguir
    // golpeando a quien está en el suelo o levantándose.
    if (defender.state in SF_DOWNED_STATES) {
        sim.setFighter(attackerIdx, attacker.copy(attackStruck = true))
        return
    }

    // 🆕 (2026-07-21) PARRY (la firma de 3rd Strike): dentro de su ventana ACTIVA el
    // golpe se anula ENTERO — cero daño, sin pose de daño — y el ATACANTE se queda
    // vendido un momento (castigo). Es la recompensa por leer el golpe.
    if (defender.state in SF_PARRY_STATES && now < parryActiveUntilMs[defenderIdx]) {
        _soundEvents.tryEmit("land") // chasquido seco del desvío
        // 🆕 (2026-07-26) Este SFX sí VIAJA (no se deriva): el parry lo resuelve solo quien
        // se defiende, y el estado PARRY_* no basta para deducirlo — pararse sin desviar
        // nada NO suena. Sin esto, el atacante no oía que le habían leído el golpe.
        if (defenderIdx == 0 && inOnlineFight) queueNetAudio("land")
        if (defenderIdx == 0 && gradeTrackingOn) gradeParries++ // 🆕 calificación: parry logrado
        parryStunUntilMs[attackerIdx] = now + SfConstants.PARRY_ADVANTAGE_MS
        sim.setFighter(attackerIdx, attacker.copy(attackStruck = true))
        sim.setFighter(
            defenderIdx,
            defender.copy(superMeter = chargeSuper(defender, SfConstants.SUPER_METER_ON_HIT)),
        )
        superKeepMs[defenderIdx] = now // 🆕 el parry exitoso también "acierta"
        hurtFreezeUntilMs = now + (SfConstants.FIGHTER_STRUCK_DELAY * SfConstants.FRAME_TIME_MS).toLong() / 2
        return
    }

    // 🆕 (2026-07-21) AGARRE que conecta -> LANZAMIENTO: daño fijo, el rival sale
    // volando y queda DERRIBADO (luego se levanta solo). No usa pose de HURT.
    if (attacker.state == SfFighterState.GRAB) {
        sim.setFighter(attackerIdx, attacker.copy(attackStruck = true))
        applyThrow(sim, attackerIdx, defenderIdx, now)
        return
    }

    // BLOQUEO (estilo SF): caminar HACIA ATRÁS = cubrirse. El golpe entra "chip":
    // daño /4 (mínimo 1), medio retroceso, sin pose de HURT, sin splash ni puntos.
    // 🆕 (2026-07-21) También cubre AGACHADO (atrás+abajo) y sostener la guardia; y el
    // OVERHEAD ROMPE la guardia baja (por eso existe), como en el arcade.
    val crouchGuard = defender.state in setOf(
        SfFighterState.CROUCH, SfFighterState.CROUCH_DOWN, SfFighterState.BLOCK_LOW,
    )
    val overheadBreaks = attacker.state == SfFighterState.OVERHEAD && crouchGuard
    val blocked = !overheadBreaks && (
        defender.state == SfFighterState.WALK_BACKWARD ||
            defender.state in SF_BLOCK_STATES ||
            (crouchGuard && defenderBlockingLow[defenderIdx])
        )
    // 🆕 (2026-07-20) COMBO (3rd Strike): golpe limpio dentro de la ventana = encadena;
    // el daño escala hacia abajo (-10% por golpe encadenado, piso 50%).
    val chainHit = !blocked && now - lastHitTakenMs[defenderIdx] <= StreetFighterViewModel.RAPID_HIT_WINDOW_MS
    if (!blocked) {
        comboHits[attackerIdx] = if (chainHit) comboHits[attackerIdx] + 1 else 1
        comboLastHitMs[attackerIdx] = now
    }
    // 🆕 (2026-07-22, Fase 1) daño base + resolución (bloqueo/chip/combo) en SfDamage (puro).
    val baseDamage = damageForAttack(attacker, strength)
    val chipAttack = attacker.state in SfDamage.CHIP_ATTACK_STATES
    val damage = SfDamage.resolvedDamage(baseDamage, blocked, chipAttack, comboHits[attackerIdx])

    // 🆕 (2026-07-25) CALIFICACIÓN (E..MS): mide el desempeño del JUGADOR (índice 0) en el combate.
    if (gradeTrackingOn && !blocked) {
        if (attackerIdx == 0) {
            gradeDealt += damage
            gradeHits++
            if (comboHits[0] > gradeMaxCombo) gradeMaxCombo = comboHits[0]
            gradeMoves.add(attacker.state)
            if (attacker.state == SfFighterState.SUPER_ART || attacker.state == SfFighterState.FATALITY) {
                gradeSupers++
            }
        }
        if (defenderIdx == 0) gradeTaken += damage
    }

    _soundEvents.tryEmit(
        if (blocked) "land" // golpe amortiguado (thud)
        else "${strength.name.lowercase()}-${type.name.lowercase()}-hit"
    )

    // 🆕 (2026-07-21) MEDIDOR DE SÚPER: carga al pegar y al recibir (el que va perdiendo
    // también acumula, como en 3rd Strike). El bloqueo carga menos.
    attacker = attacker.copy(
        attackStruck = true,
        superMeter = chargeSuper(
            attacker,
            if (blocked) SfConstants.SUPER_METER_ON_BLOCK else SfConstants.SUPER_METER_ON_HIT,
        ),
    )
    superKeepMs[attackerIdx] = now // 🆕 (2026-07-22) conectó: su súper no decae aún
    defender = defender.copy(
        slideVelocity = strength.slideVelocity * (if (blocked) 0.5f else 1f),
        slideFriction = strength.slideFriction,
        hitPoints = (defender.hitPoints - damage).coerceAtLeast(0),
        direction = attacker.direction.opposite(), // BattleScene: el golpeado queda de frente
        superMeter = chargeSuper(
            defender,
            if (blocked) SfConstants.SUPER_METER_ON_BLOCK else SfConstants.SUPER_METER_ON_TAKE,
        ),
        // 🆕 (2026-07-22) MAREO: sube al RECIBIR (proporcional al daño, con tope por
        // golpe). Bloqueado NO marea. Al llenarse, applyMeterDecay dispara el STUN.
        dizzyMeter = if (blocked) {
            defender.dizzyMeter
        } else {
            (defender.dizzyMeter + minOf(damage, SfConstants.DIZZY_HIT_CAP))
                .coerceAtMost(SfConstants.DIZZY_METER_MAX)
        },
    )
    if (!blocked) {
        // 🆕 (2026-07-25) Combo NUEVO (no encadenado) → el wall splat vuelve a estar disponible.
        if (!chainHit) wallSplatUsed[defenderIdx] = false
        rapidHitsTaken[defenderIdx] = if (chainHit) rapidHitsTaken[defenderIdx] + 1 else 1
        lastHitTakenMs[defenderIdx] = now

        // 🆕 (2026-07-25) WALL SPLAT: golpe PESADO (o barrida) con el rival EMPUJADO contra la
        // pared → se despega REBOTANDO al centro, quedando a rango para CONTINUAR el combo (ruta
        // nueva). UNA sola vez por combo (justo para ambos, sin infinitos). Simétrico por lado.
        val heavyEnough = strength == SfAttackStrength.HEAVY || attacker.state == SfFighterState.SWEEP
        val atRightWall = attacker.direction == SfDirection.RIGHT &&
            defender.x >= SfConstants.STAGE_X_MAX - StreetFighterViewModel.WALL_SPLAT_ZONE
        val atLeftWall = attacker.direction == SfDirection.LEFT &&
            defender.x <= SfConstants.STAGE_X_MIN + StreetFighterViewModel.WALL_SPLAT_ZONE
        if (heavyEnough && (atRightWall || atLeftWall) && !wallSplatUsed[defenderIdx] && defender.hitPoints > 0) {
            wallSplatUsed[defenderIdx] = true
            // Rebote al centro: el defensor se DESPEGA de la pared y vuelve hacia el atacante,
            // que queda a rango para CONTINUAR el combo (la ruta nueva). El hit-stun normal de
            // más abajo da la ventana; el rebote es la posición.
            val bouncedX = if (atRightWall) defender.x - StreetFighterViewModel.WALL_SPLAT_BOUNCE_PX else defender.x + StreetFighterViewModel.WALL_SPLAT_BOUNCE_PX
            defender = defender.copy(x = bouncedX, slideVelocity = 0f)
            _soundEvents.tryEmit("heavy-punch-hit") // golpe seco del aplastón
        }

        if (rapidHitsTaken[defenderIdx] >= StreetFighterViewModel.RAPID_HITS_BEFORE_ESCAPE && defender.hitPoints > 0) {
            comboEscapeUntilMs[defenderIdx] = now + StreetFighterViewModel.COMBO_ESCAPE_MS
            rapidHitsTaken[defenderIdx] = 0
            wallSplatUsed[defenderIdx] = false // el combo terminó → wall splat disponible otra vez
            defender = defender.copy(
                slideVelocity = maxOf(defender.slideVelocity, strength.slideVelocity * 1.35f),
            )
            // 🆕 (2026-07-25) ANTI-"TRABE" en la esquina: el empuje del escape se lo comía la
            // pared, así que el defensor quedaba atrapado. Ahora, si está contra la pared, se
            // EMPUJA AL ATACANTE hacia atrás (crea ESPACIO real, sin meter al defensor en overlap).
            if (isNearStageCorner(defender.x)) {
                attacker = attacker.copy(x = attacker.x - StreetFighterViewModel.WALL_SPLAT_BOUNCE_PX * attacker.direction.sign)
            }
        }
    }
    if (!blocked) {
        if (attackerIdx == 0) sim.score0 += strength.score else sim.score1 += strength.score
    }
    sim.setFighter(attackerIdx, attacker)
    sim.setFighter(defenderIdx, defender)

    if (!blocked) hitPos?.let { (x, y) ->
        sim.splashes.add(SfHitSplash(x = x, y = y, playerId = attackerIdx, strength = strength, animationTimerMs = now))
    }

    if (blocked && defender.hitPoints > 0) {
        // 🆕 (2026-07-21) Bloqueado: ahora se VE la pose de guardia (alta o baja según
        // cómo se estuviera cubriendo). Si el peleador no tiene esas hojas, se queda
        // como antes (sin cambio de estado) — nunca se rompe.
        val guard = if (crouchGuard) SfFighterState.BLOCK_LOW else SfFighterState.BLOCK_HIGH
        changeState(sim, defenderIdx, guard, now)
        hurtFreezeUntilMs = now + (SfConstants.FIGHTER_STRUCK_DELAY * SfConstants.FRAME_TIME_MS).toLong() / 2
        return
    }

    // 🆕 YOALLI EHÉCATL (jefe FINAL) no “pierde” al KO: a ≤1/4 de vida (o daño letal) se
    // metamorfosea en LA PRESIDENTA con la VIDA LLENA (una sola vez). Invulnerable en la anim.
    if (tryYoalliMetamorphosis(sim, defenderIdx, attackerIdx, now)) {
        hurtFreezeUntilMs = now + (SfConstants.FIGHTER_STRUCK_DELAY * SfConstants.FRAME_TIME_MS).toLong()
        return
    }

    if (defender.hitPoints <= 0) {
        changeState(sim, defenderIdx, SfFighterState.KO, now)
        sim.setFighter(attackerIdx, sim.fighter(attackerIdx).copy(victory = true))
        // 🆕 KO = fin de RONDA (mejor de 3); endRound decide si el combate terminó
        if (gauntletActive && !showcaseMode) gauntletKoRounds++
        endRound(sim, attackerIdx, now, computeRoundOutcome(sim, attackerIdx, attacker.state, byTime = false))
    } else if (attacker.state in knockdownStates &&
        forceState(sim, defenderIdx, SfFighterState.THROWN, now)
    ) {
        // 🆕 (2026-07-21) DERRIBO: la barrida y la súper tumban al rival, que cae y se
        // levanta solo (invulnerable mientras esté en el suelo). Si no tiene el arte,
        // `forceState` devuelve false y cae al camino de HURT normal de abajo.
        sim.setFighter(
            defenderIdx,
            sim.fighter(defenderIdx).copy(
                slideVelocity = SfConstants.THROW_PUSH_VELOCITY * 0.5f,
                slideFriction = SfAttackStrength.HEAVY.slideFriction,
            ),
        )
        withNetAudioCapture(defenderIdx) { emitHurtVoice(defender.id, defenderIdx, defender.hitPoints, now) }
    } else {
        // 🆕 (2026-07-21) Golpe recibido EN CUCLILLAS: pose de daño agachado propia.
        if (crouchGuard && changeState(sim, defenderIdx, SfFighterState.HURT_CROUCH, now)) {
            withNetAudioCapture(defenderIdx) { emitHurtVoice(defender.id, defenderIdx, defender.hitPoints, now) }
            hurtFreezeUntilMs =
                now + (SfConstants.FIGHTER_STRUCK_DELAY * SfConstants.FRAME_TIME_MS).toLong()
            return
        }
        val hurtState = when (area) {
            SfHurtArea.BODY -> when (strength) {
                SfAttackStrength.LIGHT -> SfFighterState.HURT_BODY_LIGHT
                SfAttackStrength.MEDIUM -> SfFighterState.HURT_BODY_MEDIUM
                SfAttackStrength.HEAVY -> SfFighterState.HURT_BODY_HEAVY
            }
            else -> when (strength) { // HEAD y LEGS caen a cabeza (default del JS)
                SfAttackStrength.LIGHT -> SfFighterState.HURT_HEAD_LIGHT
                SfAttackStrength.MEDIUM -> SfFighterState.HURT_HEAD_MEDIUM
                SfAttackStrength.HEAVY -> SfFighterState.HURT_HEAD_HEAVY
            }
        }
        changeState(sim, defenderIdx, hurtState, now)
        withNetAudioCapture(defenderIdx) { emitHurtVoice(defender.id, defenderIdx, defender.hitPoints, now) } // 🆕 voz de daño (con cooldown / lowHp)
    }
    hurtFreezeUntilMs = now + (SfConstants.FIGHTER_STRUCK_DELAY * SfConstants.FRAME_TIME_MS).toLong()
}

/**
 * 🆕 (2026-07-25) Si la defensora es YOALLI EHÉCATL (jefe FINAL) sin haber metamorfoseado y el
 * golpe la deja en ≤25% HP (o la mataría), lanza BONUS_POWER_10 y NO aplica KO.
 * Al terminar la anim (ver handler BONUS_POWER_*), el id pasa a LA PRESIDENTA con VIDA LLENA.
 * @return true si se consumió el golpe como metamorfosis (el caller no hace KO/hurt).
 */
internal fun StreetFighterViewModel.tryYoalliMetamorphosis(
    sim: StreetFighterViewModel.Sim,
    defenderIdx: Int,
    attackerIdx: Int,
    now: Long,
): Boolean {
    val d = sim.fighter(defenderIdx)
    // 🆕 (2026-07-25, decisión del dueño) INVERTIDA: ahora es YOALLI EHÉCATL (jefe FINAL del
    // arcade) quien a ≤1/4 de vida se metamorfosea en LA PRESIDENTA (antes era al revés).
    if (d.id != SfFighterId.YOALLI_EHECATL || d.metamorphosed || d.metamorphosing) return false
    // 🆕 (2026-07-22, decisión del dueño) La metamorfosis automática SOLO ocurre en el
    // ROUND 1. Si sobrevivió el round 1 sin transformarse, ya no se transforma.
    if (_state.value.roundNumber != 1) return false
    val maxHp = SfConstants.HEALTH_MAX_HIT_POINTS
    val threshold = maxHp / 4 // 50 de 200
    if (d.hitPoints > threshold) return false
    // Ya está en ≤1/4 (el HP se restó arriba). Arranca anim de metamorfosis (Yoalli→Presidenta).
    // FORZAR estado: puede venir de HURT (validFrom de BONUS_POWER no lo incluye).
    val pinnedHp = d.hitPoints.coerceIn(1, threshold)
    var nf = d.copy(
        state = SfFighterState.BONUS_POWER_10,
        hitPoints = pinnedHp,
        metamorphosing = true,
        metamorphosed = false,
        velocityX = 0f,
        velocityY = 0f,
        slideVelocity = 0f,
        slideFriction = 0f,
        attackStruck = false,
        fireballFired = false,
        y = SfConstants.STAGE_FLOOR,
    )
    nf = withAnimationFrame(nf, 0, now)
    sim.setFighter(defenderIdx, clampFighterToStage(nf))
    sim.setFighter(attackerIdx, sim.fighter(attackerIdx).copy(attackStruck = true))
    // metamorfosis Yoalli → grito + subtítulo (viaja si es MI peleadora: el rival la oye)
    withNetAudioCapture(defenderIdx) { emitSpecialVoice(d.id, now) }
    return true
}

/** Invulnerable durante cualquiera de las dos direcciones de la metamorfosis. */
internal fun StreetFighterViewModel.isMetamorphosing(f: SfFighter): Boolean =
    f.metamorphosing ||
        (f.id == SfFighterId.LA_PRESIDENTA && f.state == SfFighterState.BONUS_POWER_11 && !f.metamorphosed) ||
        (f.id == SfFighterId.YOALLI_EHECATL && f.state == SfFighterState.BONUS_POWER_10)

// ------------------------------------------------------------------
// Fireballs (Fireball.js) — animación, movimiento, colisión
// ------------------------------------------------------------------

// Delays de la animación del fireball (frames del JS); los recortes viven en la View

