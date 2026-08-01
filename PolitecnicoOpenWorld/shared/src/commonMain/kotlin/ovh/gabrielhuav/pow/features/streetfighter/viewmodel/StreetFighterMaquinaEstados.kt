package ovh.gabrielhuav.pow.features.streetfighter.viewmodel

import ovh.gabrielhuav.pow.domain.models.streetfighter.SF_HURT_STATES
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfStateMachine
import ovh.gabrielhuav.pow.domain.models.streetfighter.sfUsableBonusPowerCount
import ovh.gabrielhuav.pow.domain.models.streetfighter.SF_DOWNED_STATES
import ovh.gabrielhuav.pow.domain.models.streetfighter.SF_NEW_MOVE_STATES
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfAttackStrength
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfConstants
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfDirection
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterId
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterState
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFireball
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFireballState
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfInput
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfProjectileEvent
import ovh.gabrielhuav.pow.domain.models.streetfighter.bonusPowerIndex
import ovh.gabrielhuav.pow.domain.models.streetfighter.sfBonusPowerState
import kotlin.math.abs

// ────────────────────────────────────────────────────────────────────────────
// PARCIAL de StreetFighterViewModel: 🎮 MAQUINA DE ESTADOS del peleador: que puede hacer en cada momento
//
// El corazon del moveset: runStateHandler decide, para el estado ACTUAL, que entradas se aceptan
// y a que estado se pasa. Debajo estan los try* (super, fatality, agarre, ataques altos/bajos/
// aereos, chain cancels, especiales, poderes) y las metamorfosis.
// ATENCION: runStateHandler tiene un when EXHAUSTIVO sin else. Todo estado NUEVO necesita rama
// aqui, ademas de entrar en VALID_FROM (SfStateMachine, modulo shared) o no compila. Ver 09 seccion 12.
// Los estados viajan por red como enum.name y el parse remoto es defensivo: anade los estados
// nuevos AL FINAL del enum o romperas la compatibilidad con clientes viejos.
//
// ⚠️ Son EXTENSIONES del VM, no miembros: el estado sigue viviendo en la clase. NO recrees
// estas funciones como miembros — quedarían gemelas y ganaría el miembro EN SILENCIO
// (ver 09 §0, el gotcha miembro-vs-extensión que ya costó caro en este repo).
// ────────────────────────────────────────────────────────────────────────────

internal fun StreetFighterViewModel.runStateHandler(sim: StreetFighterViewModel.Sim, idx: Int, input: SfInput, now: Long, dt: Float) {
    val f = sim.fighter(idx)
    when (f.state) {
        SfFighterState.IDLE -> {
            if (f.victory) { changeState(sim, idx, SfFighterState.VICTORY, now); return }
            if (!handleCommonNeutral(sim, idx, input, now)) {
                // getDirection: encararse al rival (solo en idle/crouch, como el JS)
                maybeTurn(sim, idx, SfFighterState.IDLE_TURN, now)
            }
        }
        SfFighterState.WALK_FORWARD -> {
            if (tryPowerInput(sim, idx, input, now)) return
            if (tryGroundUtilityInput(sim, idx, input, now)) return
            when {
                input.up -> changeState(sim, idx, SfFighterState.JUMP_FORWARD, now)
                input.down && tryCrouchAttackFromStanding(sim, idx, input, now) -> Unit
                input.down -> changeState(sim, idx, SfFighterState.CROUCH_DOWN, now)
                // 🆕 (2026-07-22) Caminando adelante YA cuenta como "adelante + ataque":
                // medio = OVERHEAD, patada fuerte = PATADA LARGA. Antes solo salían pulsando
                // →+ataque en el MISMO frame desde IDLE (casi imposible) — por eso el Overhead
                // del tutorial (lección 17) no se podía hacer. Si el peleador no los tiene,
                // changeState devuelve false y cae al golpe normal.
                input.mediumPunch && changeState(sim, idx, SfFighterState.OVERHEAD, now) -> Unit
                input.heavyKick && changeState(sim, idx, SfFighterState.LONG_KICK, now) -> Unit
                tryAttacks(sim, idx, input, now) -> Unit
                !input.forward -> changeState(sim, idx, SfFighterState.IDLE, now)
            }
        }
        SfFighterState.WALK_BACKWARD -> {
            if (tryPowerInput(sim, idx, input, now)) return
            if (tryGroundUtilityInput(sim, idx, input, now)) return
            when {
                input.up -> changeState(sim, idx, SfFighterState.JUMP_BACKWARD, now)
                input.down && tryCrouchAttackFromStanding(sim, idx, input, now) -> Unit
                input.down -> changeState(sim, idx, SfFighterState.CROUCH_DOWN, now)
                tryAttacks(sim, idx, input, now) -> Unit
                !input.backward -> changeState(sim, idx, SfFighterState.IDLE, now)
            }
        }
        SfFighterState.JUMP_START -> {
            if (isAnimationCompleted(f)) {
                when {
                    input.backward -> changeState(sim, idx, SfFighterState.JUMP_BACKWARD, now)
                    input.forward -> changeState(sim, idx, SfFighterState.JUMP_FORWARD, now)
                    else -> changeState(sim, idx, SfFighterState.JUMP_UP, now)
                }
            }
        }
        SfFighterState.JUMP_UP, SfFighterState.JUMP_FORWARD, SfFighterState.JUMP_BACKWARD -> {
            // 🆕 (2026-07-21) Ataque AÉREO en pleno salto (abre combos al aterrizar).
            if (tryAirAttacks(sim, idx, input, now)) return
            // handleJump: gravedad + aterrizaje
            val nf = f.copy(velocityY = f.velocityY + SfConstants.GRAVITY * dt)
            sim.setFighter(idx, nf)
            // El clamp del tick anterior deja `y` EXACTAMENTE en el piso. Con `>` nunca
            // aterrizaba: conservaba JUMP_* para siempre aunque ya estuviera abajo.
            if (nf.y >= SfConstants.STAGE_FLOOR && nf.velocityY >= 0f) {
                sim.setFighter(idx, nf.copy(y = SfConstants.STAGE_FLOOR))
                changeState(sim, idx, SfFighterState.JUMP_LAND, now)
                // 🆕 CROSS-UP: al aterrizar, ENCARA de inmediato al rival (sin animación de giro).
                // Si brincaste por encima quedabas viendo al lado contrario y "adelante" apuntaba
                // LEJOS del rival → no le podías pegar. Orientar al tocar piso lo arregla.
                val landed = sim.fighter(idx)
                val opp = sim.fighter(1 - idx)
                val facing = if (landed.x <= opp.x) SfDirection.RIGHT else SfDirection.LEFT
                if (facing != landed.direction) sim.setFighter(idx, landed.copy(direction = facing))
                _soundEvents.tryEmit("land")
            }
        }
        SfFighterState.JUMP_LAND -> {
            if (f.animationFrame > 0) {
                if (!handleCommonNeutral(sim, idx, input, now) && isAnimationCompleted(sim.fighter(idx))) {
                    changeState(sim, idx, SfFighterState.IDLE, now)
                }
            }
        }
        SfFighterState.CROUCH_DOWN -> {
            if (tryPowerInput(sim, idx, input, now)) return
            if (tryCrouchAttacks(sim, idx, input, now)) return
            if (isAnimationCompleted(f)) {
                changeState(sim, idx, SfFighterState.CROUCH, now)
            } else if (!input.down) {
                // Quirk del JS: aborta la bajada arrancando la subida sin validFrom
                var nf = f.copy(state = SfFighterState.CROUCH_UP)
                nf = withAnimationFrame(nf, 0, now)
                sim.setFighter(idx, nf)
            }
        }
        SfFighterState.CROUCH -> {
            if (tryPowerInput(sim, idx, input, now)) return
            // 🆕 (2026-07-21) Arsenal AGACHADO: parry bajo + los 4 golpes bajos.
            // La barrida (patada fuerte) es el remate que derriba.
            if (input.parry && changeState(sim, idx, SfFighterState.PARRY_LOW, now)) return
            if (tryCrouchAttacks(sim, idx, input, now)) return
            if (!input.down) changeState(sim, idx, SfFighterState.CROUCH_UP, now)
            else maybeTurn(sim, idx, SfFighterState.CROUCH_TURN, now)
        }
        SfFighterState.CROUCH_UP -> {
            if (tryPowerInput(sim, idx, input, now)) return
            if (tryGroundUtilityInput(sim, idx, input, now)) return
            if (tryAttacks(sim, idx, input, now)) return
            if (isAnimationCompleted(f)) changeState(sim, idx, SfFighterState.IDLE, now)
        }
        SfFighterState.IDLE_TURN -> {
            // Cancelar giro con input (jugador + IA). Si el input es ataque, puede salir
            // directo al golpe (validFrom incluye IDLE_TURN) sin pasar por IDLE.
            val wantsMove = input.forward || input.backward || input.up || input.down
            if (tryPowerInput(sim, idx, input, now)) return
            if (tryGroundUtilityInput(sim, idx, input, now)) return
            val wantsOffense = input.lightPunch || input.mediumPunch || input.heavyPunch ||
                input.lightKick || input.mediumKick || input.heavyKick ||
                input.special != null || input.bonusPower != null || input.superArt
            when {
                wantsOffense -> {
                    if (tryAttacks(sim, idx, input, now)) return
                    if (changeState(sim, idx, SfFighterState.IDLE, now)) {
                        handleCommonNeutral(sim, idx, input, now)
                    }
                }
                wantsMove -> {
                    if (changeState(sim, idx, SfFighterState.IDLE, now)) {
                        handleCommonNeutral(sim, idx, input, now)
                    }
                }
                isAnimationCompleted(f) -> changeState(sim, idx, SfFighterState.IDLE, now)
            }
        }
        SfFighterState.CROUCH_TURN -> {
            if (tryPowerInput(sim, idx, input, now)) return
            if (tryCrouchAttacks(sim, idx, input, now)) return
            val wantsAction = !input.down
            when {
                wantsAction && !input.down -> changeState(sim, idx, SfFighterState.CROUCH_UP, now)
                isAnimationCompleted(f) -> changeState(sim, idx, SfFighterState.CROUCH, now)
            }
        }

        SfFighterState.LIGHT_PUNCH, SfFighterState.LIGHT_KICK -> {
            // Los ataques ligeros se pueden re-disparar desde el frame 2 (JS)
            if (f.animationFrame < 2) return
            val retap = (f.state == SfFighterState.LIGHT_PUNCH && input.lightPunch) ||
                (f.state == SfFighterState.LIGHT_KICK && input.lightKick)
            if (retap) {
                var nf = withAnimationFrame(f, 0, now).copy(attackStruck = false)
                sim.setFighter(idx, nf)
                _soundEvents.tryEmit("light-attack")
                return
            }
            // 🆕 (2026-07-20) chain cancel ligero→medio (o especial) si CONECTÓ
            if (tryChainCancel(sim, idx, input, now)) return
            if (isAnimationCompleted(f)) changeState(sim, idx, SfFighterState.IDLE, now)
        }
        SfFighterState.MEDIUM_PUNCH, SfFighterState.HEAVY_PUNCH,
        SfFighterState.MEDIUM_KICK, SfFighterState.HEAVY_KICK,
        -> {
            // 🆕 (2026-07-20) chain cancel medio→fuerte / cualquier golpe→especial si CONECTÓ
            if (tryChainCancel(sim, idx, input, now)) return
            if (isAnimationCompleted(f)) changeState(sim, idx, SfFighterState.IDLE, now)
        }

        SfFighterState.HURT_HEAD_LIGHT, SfFighterState.HURT_HEAD_MEDIUM, SfFighterState.HURT_HEAD_HEAVY,
        SfFighterState.HURT_BODY_LIGHT, SfFighterState.HURT_BODY_MEDIUM, SfFighterState.HURT_BODY_HEAVY,
        -> {
            if (isAnimationCompleted(f)) {
                val opp = sim.fighter(1 - idx)
                sim.setFighter(1 - idx, opp.copy(attackStruck = false))
                changeState(sim, idx, SfFighterState.IDLE, now)
            }
        }

        SfFighterState.SPECIAL_1_LIGHT, SfFighterState.SPECIAL_1_MEDIUM, SfFighterState.SPECIAL_1_HEAVY -> {
            val meta = attackMeta.getValue(f.state)
            val event = dataFor(f).projectileEvents[meta.strength] ?: SfProjectileEvent()
            // Cada hoja dedicada marca el cuadro exacto donde el objeto emite su efecto.
            if (f.animationFrame == event.animationFrame && !f.fireballFired) {
                sim.setFighter(idx, f.copy(fireballFired = true))
                sim.fireballs.add(
                    SfFireball(
                        ownerIndex = idx,
                        x = f.x + event.offsetX * f.direction.sign,
                        y = f.y + event.offsetY,
                        direction = f.direction,
                        strength = meta.strength,
                        velocity = meta.strength.fireballVelocity,
                        animationTimerMs = now,
                    ),
                )
            }
            if (isAnimationCompleted(sim.fighter(idx))) {
                sim.setFighter(idx, sim.fighter(idx).copy(fireballFired = false))
                changeState(sim, idx, SfFighterState.IDLE, now)
            }
        }

        SfFighterState.BONUS_POWER_1, SfFighterState.BONUS_POWER_2, SfFighterState.BONUS_POWER_3,
        SfFighterState.BONUS_POWER_4, SfFighterState.BONUS_POWER_5, SfFighterState.BONUS_POWER_6,
        SfFighterState.BONUS_POWER_7, SfFighterState.BONUS_POWER_8, SfFighterState.BONUS_POWER_9,
        SfFighterState.BONUS_POWER_10, SfFighterState.BONUS_POWER_11,
        -> {
            // 🆕 (2026-07-25) BONUS_POWER_10 de YOALLI = metamorfosis PRINCIPAL (jefe FINAL del
            // arcade): al terminar la anim el id pasa a LA PRESIDENTA con VIDA LLENA y se QUEDA.
            if (f.id == SfFighterId.YOALLI_EHECATL && f.state == SfFighterState.BONUS_POWER_10) {
                if (isAnimationCompleted(f)) {
                    completeYoalliMetamorphosis(sim, idx, now)
                }
                return
            }
            // Dirección opuesta (histórica, hoy inactiva en gameplay: P11 no es lanzable y no hay
            // disparo automático): La Presidenta → Yoalli. Se conserva por simetría/animación.
            if (f.id == SfFighterId.LA_PRESIDENTA && f.state == SfFighterState.BONUS_POWER_11) {
                if (isAnimationCompleted(f)) {
                    completePresidentaMetamorphosis(sim, idx, now)
                }
                return
            }
            // Otros bonus: proyectil en cuadro central (Yoalli HEAVY; resto MEDIUM).
            val strength = if (f.id == SfFighterId.YOALLI_EHECATL) {
                SfAttackStrength.HEAVY
            } else {
                SfAttackStrength.MEDIUM
            }
            val event = dataFor(f).projectileEvents[strength] ?: SfProjectileEvent()
            // 🆕 (2026-07-21) El proyectil sale al ENTRAR al último cuadro del personaje
            // (los poderes de proyectil solo tienen 2 poses propias) y viaja marcado con
            // su índice de poder para dibujarse con SU efecto (bonus-N-2/3/4).
            if (f.animationFrame == 1 && !f.fireballFired) {
                sim.setFighter(idx, f.copy(fireballFired = true))
                sim.fireballs.add(
                    SfFireball(
                        ownerIndex = idx,
                        x = f.x + event.offsetX * f.direction.sign,
                        y = f.y + event.offsetY,
                        direction = f.direction,
                        strength = strength,
                        velocity = strength.fireballVelocity,
                        animationTimerMs = now,
                        bonusPower = f.state.bonusPowerIndex() ?: 0,
                    ),
                )
            }
            if (isAnimationCompleted(sim.fighter(idx))) {
                sim.setFighter(idx, sim.fighter(idx).copy(fireballFired = false))
                changeState(sim, idx, SfFighterState.IDLE, now)
            }
        }

        // ── 🆕 (2026-07-21) MOVESET 3rd Strike ──
        // Movilidad: el dash termina con su animación y frena en seco. 🆕 Si al acabar
        // se sigue sosteniendo ADELANTE, encadena a CARRERA (dash-run de 3rd Strike).
        SfFighterState.DASH_FORWARD, SfFighterState.DASH_BACKWARD -> {
            if (isAnimationCompleted(f)) {
                if (f.state == SfFighterState.DASH_FORWARD && input.forward &&
                    changeState(sim, idx, SfFighterState.RUN, now)
                ) {
                    return
                }
                sim.setFighter(idx, f.copy(velocityX = 0f))
                changeState(sim, idx, SfFighterState.IDLE, now)
            }
        }
        // 🆕 CARRERA: se mantiene mientras se sostenga adelante; se puede saltar o
        // atacar desde ella (por eso vale la pena correr).
        SfFighterState.RUN -> {
            when {
                // 🆕 FATALITY: súper EN CARRERA con el medidor lleno (su comando propio)
                input.superArt && tryFatality(sim, idx, now) -> Unit
                input.up -> changeState(sim, idx, SfFighterState.JUMP_START, now)
                input.forward && input.heavyKick &&
                    changeState(sim, idx, SfFighterState.LONG_KICK, now) -> Unit
                input.forward && input.mediumPunch &&
                    changeState(sim, idx, SfFighterState.OVERHEAD, now) -> Unit
                tryAttacks(sim, idx, input, now) -> Unit
                !input.forward -> {
                    sim.setFighter(idx, f.copy(velocityX = 0f))
                    changeState(sim, idx, SfFighterState.IDLE, now)
                }
            }
        }
        // 🆕 FATALITY: al terminar la cinemática el atacante CRUZA al otro lado del
        // rival (con giro), que es el remate espectacular pedido.
        SfFighterState.FATALITY -> if (isAnimationCompleted(f)) {
            crossToOtherSide(sim, idx, now)
        }
        // Poses de intro/burla: terminan y vuelven a guardia.
        SfFighterState.IDLE_RELAXED, SfFighterState.TALK ->
            if (isAnimationCompleted(f)) changeState(sim, idx, SfFighterState.IDLE, now)
        // Defensa: el bloqueo se sostiene mientras se siga cubriendo.
        // 🆕 (2026-07-26) BLOQUEO RESPONSIVO: antes exigían soltar atrás Y que la animación
        // terminara para salir, y NO aceptaban ninguna otra acción → al cubrirse quedabas
        // "atrapado" y los controles "no respondían". Ahora reaccionan al INSTANTE como
        // WALK_BACKWARD/CROUCH (el blockstun real lo da hurtFreezeUntilMs durante el golpe, no
        // esta animación). Sostener la dirección sigue cubriendo; cualquier otra intención sale ya.
        // Terminado el hit-freeze del golpe bloqueado, la guardia REBOTA al instante al estado
        // neutro correspondiente (WALK_BACKWARD alto / CROUCH bajo) — que son totalmente
        // responsivos y VUELVEN a bloquear si les pegan otra vez. Así se acaba el "quedarse
        // atrapado" en la pose de bloqueo (antes exigía terminar la animación).
        SfFighterState.BLOCK_HIGH -> when {
            !input.backward -> changeState(sim, idx, SfFighterState.IDLE, now)
            input.down -> changeState(sim, idx, SfFighterState.CROUCH_DOWN, now)
            else -> changeState(sim, idx, SfFighterState.WALK_BACKWARD, now)
        }
        SfFighterState.BLOCK_LOW -> when {
            !input.down -> changeState(sim, idx, SfFighterState.CROUCH_UP, now)
            else -> changeState(sim, idx, SfFighterState.CROUCH, now)
        }
        SfFighterState.PARRY_HIGH -> if (isAnimationCompleted(f)) {
            changeState(sim, idx, SfFighterState.IDLE, now)
        }
        SfFighterState.PARRY_LOW -> if (isAnimationCompleted(f)) {
            changeState(sim, idx, SfFighterState.CROUCH, now)
        }
        // Ataques agachado: encadenan entre sí (cancel) y vuelven a cuclillas.
        SfFighterState.CROUCH_PUNCH, SfFighterState.CROUCH_KICK,
        SfFighterState.CROUCH_HEAVY_PUNCH,
        -> {
            if (tryCrouchChainCancel(sim, idx, input, now)) return
            if (isAnimationCompleted(f)) changeState(sim, idx, SfFighterState.CROUCH, now)
        }
        // La barrida NO cancela: es el final de la cadena baja (derriba).
        SfFighterState.SWEEP -> if (isAnimationCompleted(f)) {
            changeState(sim, idx, SfFighterState.CROUCH, now)
        }
        // Aéreos: siguen cayendo; al tocar el suelo aterrizan como un salto normal.
        SfFighterState.AIR_PUNCH, SfFighterState.AIR_KICK -> {
            val nf = f.copy(velocityY = f.velocityY + SfConstants.GRAVITY * dt)
            sim.setFighter(idx, nf)
            if (nf.y >= SfConstants.STAGE_FLOOR && nf.velocityY >= 0f) {
                sim.setFighter(idx, nf.copy(y = SfConstants.STAGE_FLOOR, velocityX = 0f))
                forceState(sim, idx, SfFighterState.JUMP_LAND, now)
                _soundEvents.tryEmit("land")
            }
        }
        SfFighterState.LONG_KICK, SfFighterState.OVERHEAD ->
            if (isAnimationCompleted(f)) changeState(sim, idx, SfFighterState.IDLE, now)
        // Agarre: si conectó (attackStruck) pasa al lanzamiento; si no, recupera.
        SfFighterState.GRAB -> {
            if (isAnimationCompleted(f)) changeState(sim, idx, SfFighterState.IDLE, now)
        }
        SfFighterState.THROW -> if (isAnimationCompleted(f)) {
            changeState(sim, idx, SfFighterState.IDLE, now)
        }
        SfFighterState.TAUNT -> if (isAnimationCompleted(f)) {
            changeState(sim, idx, SfFighterState.IDLE, now)
        }
        SfFighterState.SUPER_ART -> if (isAnimationCompleted(f)) {
            changeState(sim, idx, SfFighterState.IDLE, now)
        }
        SfFighterState.HURT_CROUCH -> if (isAnimationCompleted(f)) {
            val opp = sim.fighter(1 - idx)
            sim.setFighter(1 - idx, opp.copy(attackStruck = false))
            changeState(sim, idx, SfFighterState.CROUCH, now)
        }
        // Derribo: cae, se queda un momento y se levanta solo (wake-up).
        SfFighterState.THROWN -> if (isAnimationCompleted(f)) {
            if (!forceState(sim, idx, SfFighterState.GET_UP, now)) {
                forceState(sim, idx, SfFighterState.IDLE, now)
            }
        }
        SfFighterState.GET_UP -> if (isAnimationCompleted(f)) {
            sim.setFighter(idx, sim.fighter(idx).copy(downed = false))
            forceState(sim, idx, SfFighterState.IDLE, now)
        }

        SfFighterState.KO -> {
            // handleFallBack: cae hasta el piso en el frame 2 (fall-2 = FREEZE)
            if (f.animationFrame == 2) {
                if (f.y >= SfConstants.STAGE_FLOOR) {
                    var nf = withAnimationFrame(f, 3, now)
                    nf = nf.copy(velocityY = 0f, y = SfConstants.STAGE_FLOOR)
                    sim.setFighter(idx, nf)
                } else {
                    sim.setFighter(idx, f.copy(velocityY = 120f))
                }
            }
        }
        // 🆕 (2026-07-22) MAREADO: ignora TODOS los inputs; sale solo (o antes, si un
        // golpe lo mete a HURT_*: STUN está en SF_HURT_STATES).
        SfFighterState.STUN -> {
            if (now >= stunUntilMs[idx.coerceIn(0, 1)]) {
                forceState(sim, idx, SfFighterState.IDLE, now)
            }
        }
        SfFighterState.VICTORY -> Unit
    }
}

/** Transiciones comunes de estados neutros (handleIdle del JS): salto/agacharse/caminar/ataques. */
internal fun StreetFighterViewModel.handleCommonNeutral(sim: StreetFighterViewModel.Sim, idx: Int, input: SfInput, now: Long): Boolean {
    if (tryPowerInput(sim, idx, input, now)) return true
    if (tryGroundUtilityInput(sim, idx, input, now)) return true
    return when {
        input.up -> changeState(sim, idx, SfFighterState.JUMP_START, now)
        // Botones de la CPU y de la UI duran un pulso. Resolver ↓+golpe en el mismo tick
        // evita perderlo durante CROUCH_DOWN (la tabla permite el segundo salto de estado).
        input.down && tryCrouchAttackFromStanding(sim, idx, input, now) -> true
        input.down -> changeState(sim, idx, SfFighterState.CROUCH_DOWN, now)
        // 🆕 Normales con DIRECCIÓN (3rd Strike): adelante+fuerte = patada larga,
        // adelante+medio = overhead (rompe guardia baja). Si el peleador no los tiene,
        // changeState devuelve false y sigue el camino normal (caminar/golpe suelto).
        input.forward && input.heavyKick &&
            changeState(sim, idx, SfFighterState.LONG_KICK, now) -> true
        input.forward && input.mediumPunch &&
            changeState(sim, idx, SfFighterState.OVERHEAD, now) -> true
        input.forward -> changeState(sim, idx, SfFighterState.WALK_FORWARD, now)
        input.backward -> changeState(sim, idx, SfFighterState.WALK_BACKWARD, now)
        else -> tryAttacks(sim, idx, input, now)
    }
}

/** Botones de utilidad válidos en todos los estados de NEUTRAL_GROUND. */
internal fun StreetFighterViewModel.tryGroundUtilityInput(
    sim: StreetFighterViewModel.Sim,
    idx: Int,
    input: SfInput,
    now: Long,
): Boolean {
    if (input.parry && changeState(sim, idx, SfFighterState.PARRY_HIGH, now)) return true
    if (input.grab && tryGrab(sim, idx, now)) return true
    if (input.dashForward && changeState(sim, idx, SfFighterState.DASH_FORWARD, now)) return true
    if (input.dashBackward && changeState(sim, idx, SfFighterState.DASH_BACKWARD, now)) return true
    return input.taunt && changeState(sim, idx, SfFighterState.TAUNT, now)
}

/** Poderes aceptados por todos los orígenes de SPECIAL_VALID_FROM. */
internal fun StreetFighterViewModel.tryPowerInput(
    sim: StreetFighterViewModel.Sim,
    idx: Int,
    input: SfInput,
    now: Long,
): Boolean {
    if (input.bonusPower?.let { tryBonusPower(sim, idx, it, now) } == true) return true
    if (input.superArt && trySuperArt(sim, idx, now)) return true
    return input.special?.let { trySpecial(sim, idx, it, now) } == true
}

/** Ejecuta el gesto ↓+golpe pasando por CROUCH_DOWN sin perder el pulso del botón. */
internal fun StreetFighterViewModel.tryCrouchAttackFromStanding(
    sim: StreetFighterViewModel.Sim,
    idx: Int,
    input: SfInput,
    now: Long,
): Boolean {
    val hasAttack = input.lightPunch || input.mediumPunch || input.heavyPunch ||
        input.lightKick || input.mediumKick || input.heavyKick
    if (!hasAttack || !changeState(sim, idx, SfFighterState.CROUCH_DOWN, now)) return false
    return tryCrouchAttacks(sim, idx, input, now)
}

/** 🆕 (2026-07-21) SUPER ART: exige medidor lleno + arte propia. */
internal fun StreetFighterViewModel.trySuperArt(sim: StreetFighterViewModel.Sim, idx: Int, now: Long): Boolean {
    val f = sim.fighter(idx)
    if (!f.superReady) return false
    return changeState(sim, idx, SfFighterState.SUPER_ART, now)
}

/**
 * 🆕 (2026-07-21) FATALITY ("poder súper especial"): comando propio = **súper EN
 * CARRERA** con el medidor lleno. Es el movimiento más devastador del peleador y
 * termina con el atacante CRUZANDO al otro lado del rival.
 */
internal fun StreetFighterViewModel.tryFatality(sim: StreetFighterViewModel.Sim, idx: Int, now: Long): Boolean {
    val f = sim.fighter(idx)
    if (!f.superReady) return false
    return changeState(sim, idx, SfFighterState.FATALITY, now)
}

/**
 * 🆕 (2026-07-21) Remate del fatality: el atacante aparece AL OTRO LADO del rival y
 * queda encarándolo (el giro lo da la propia animación de la cinemática). Se respeta el
 * límite del escenario para no dejarlo fuera de pantalla.
 */
internal fun StreetFighterViewModel.crossToOtherSide(sim: StreetFighterViewModel.Sim, idx: Int, now: Long) {
    val me = sim.fighter(idx)
    val foe = sim.fighter(1 - idx)
    val wasLeft = me.x <= foe.x
    val target = if (wasLeft) {
        foe.x + SfConstants.FATALITY_CROSS_OFFSET
    } else {
        foe.x - SfConstants.FATALITY_CROSS_OFFSET
    }
    sim.setFighter(
        idx,
        me.copy(
            x = target.coerceIn(StreetFighterViewModel.STAGE_X_MIN, StreetFighterViewModel.STAGE_X_MAX),
            velocityX = 0f,
            direction = if (wasLeft) SfDirection.LEFT else SfDirection.RIGHT,
        ),
    )
    // El rival también queda encarando al atacante tras el cruce
    sim.setFighter(1 - idx, foe.copy(direction = if (wasLeft) SfDirection.RIGHT else SfDirection.LEFT))
    forceState(sim, idx, SfFighterState.IDLE, now)
}

/** 🆕 (2026-07-21) AGARRE: solo tiene sentido pegado al rival (como en el arcade). */
internal fun StreetFighterViewModel.tryGrab(sim: StreetFighterViewModel.Sim, idx: Int, now: Long): Boolean {
    val me = sim.fighter(idx)
    val foe = sim.fighter(1 - idx)
    if (abs(me.x - foe.x) > SfConstants.GRAB_RANGE) return false
    // No se puede agarrar a quien está en el aire ni derribado
    if (foe.isAirborne || foe.state in SF_DOWNED_STATES) return false
    return changeState(sim, idx, SfFighterState.GRAB, now)
}

/**
 * 🆕 (2026-07-21) Golpes AGACHADO (3rd Strike): ligero = jab/patadita bajos,
 * puño fuerte = antiaéreo, patada fuerte = BARRIDA (derriba).
 */
internal fun StreetFighterViewModel.tryCrouchAttacks(sim: StreetFighterViewModel.Sim, idx: Int, input: SfInput, now: Long): Boolean = when {
    input.heavyKick -> changeState(sim, idx, SfFighterState.SWEEP, now)
    input.heavyPunch -> changeState(sim, idx, SfFighterState.CROUCH_HEAVY_PUNCH, now)
    input.lightPunch || input.mediumPunch ->
        changeState(sim, idx, SfFighterState.CROUCH_PUNCH, now)
    input.lightKick || input.mediumKick ->
        changeState(sim, idx, SfFighterState.CROUCH_KICK, now)
    else -> false
}

/**
 * 🆕 (2026-07-21) Ataques AÉREOS: UNO por salto (`airAttackUsed`), como en el arcade;
 * si no, se podían encadenar patadas infinitas en el mismo brinco.
 */
internal fun StreetFighterViewModel.tryAirAttacks(sim: StreetFighterViewModel.Sim, idx: Int, input: SfInput, now: Long): Boolean {
    if (airAttackUsed[idx.coerceIn(0, 1)]) return false
    val wantsPunch = input.lightPunch || input.mediumPunch || input.heavyPunch
    val wantsKick = input.lightKick || input.mediumKick || input.heavyKick
    val state = when {
        wantsKick -> SfFighterState.AIR_KICK
        wantsPunch -> SfFighterState.AIR_PUNCH
        else -> return false
    }
    if (!changeState(sim, idx, state, now)) return false
    airAttackUsed[idx.coerceIn(0, 1)] = true
    return true
}

internal fun StreetFighterViewModel.tryAttacks(sim: StreetFighterViewModel.Sim, idx: Int, input: SfInput, now: Long): Boolean = when {
    input.lightPunch -> changeState(sim, idx, SfFighterState.LIGHT_PUNCH, now)
    input.mediumPunch -> changeState(sim, idx, SfFighterState.MEDIUM_PUNCH, now)
    input.heavyPunch -> changeState(sim, idx, SfFighterState.HEAVY_PUNCH, now)
    input.lightKick -> changeState(sim, idx, SfFighterState.LIGHT_KICK, now)
    input.mediumKick -> changeState(sim, idx, SfFighterState.MEDIUM_KICK, now)
    input.heavyKick -> changeState(sim, idx, SfFighterState.HEAVY_KICK, now)
    else -> false
}

/**
 * 🆕 (2026-07-21) Fuerza un estado saltándose `validFrom` (derribos, aterrizajes y
 * levantadas: los dispara la LÓGICA, no una transición de input). Respeta `hasAnim`:
 * si el peleador no tiene ese arte, devuelve false y el llamador decide el fallback.
 */
internal fun StreetFighterViewModel.forceState(sim: StreetFighterViewModel.Sim, idx: Int, newState: SfFighterState, now: Long): Boolean {
    val f = sim.fighter(idx)
    if (newState in SF_NEW_MOVE_STATES && !hasAnim(f, newState)) return false
    var nf = f.copy(state = newState, velocityX = 0f)
    nf = withAnimationFrame(nf, 0, now)
    if (newState == SfFighterState.THROWN) nf = nf.copy(downed = true)
    if (newState == SfFighterState.IDLE) nf = nf.copy(downed = false, velocityY = 0f)
    sim.setFighter(idx, nf)
    return true
}

/**
 * 🆕 (2026-07-21) Cadena BAJA (3rd Strike): un golpe agachado que CONECTÓ encadena al
 * siguiente. El remate natural es la BARRIDA, que derriba. Mismo criterio que arriba:
 * sin `attackStruck` no hay cancel (en fallo se paga la recuperación completa).
 */
internal fun StreetFighterViewModel.tryCrouchChainCancel(sim: StreetFighterViewModel.Sim, idx: Int, input: SfInput, now: Long): Boolean {
    val f = sim.fighter(idx)
    if (!f.attackStruck) return false
    return when {
        input.heavyKick -> changeState(sim, idx, SfFighterState.SWEEP, now)
        input.heavyPunch && f.state != SfFighterState.CROUCH_HEAVY_PUNCH ->
            changeState(sim, idx, SfFighterState.CROUCH_HEAVY_PUNCH, now)
        (input.lightPunch || input.mediumPunch) && f.state != SfFighterState.CROUCH_PUNCH ->
            changeState(sim, idx, SfFighterState.CROUCH_PUNCH, now)
        (input.lightKick || input.mediumKick) && f.state != SfFighterState.CROUCH_KICK ->
            changeState(sim, idx, SfFighterState.CROUCH_KICK, now)
        else -> false
    }
}

/**
 * 🆕 (2026-07-20) CHAIN CANCEL estilo SF III 3rd Strike: un golpe normal que CONECTÓ
 * (attackStruck) puede cancelarse ANTES de terminar en el siguiente golpe de mayor
 * fuerza (ligero→medio→fuerte, puño o patada) o en el ESPECIAL (special cancel; respeta
 * el cooldown y el tope de proyectiles de trySpecial). En fallo (whiff) NO hay cancel:
 * se sufre la recuperación completa, como en el arcade original.
 */
internal fun StreetFighterViewModel.tryChainCancel(sim: StreetFighterViewModel.Sim, idx: Int, input: SfInput, now: Long): Boolean {
    val f = sim.fighter(idx)
    if (!f.attackStruck) return false
    if (tryPowerInput(sim, idx, input, now)) return true
    val next = when (attackMeta[f.state]?.strength) {
        SfAttackStrength.LIGHT -> when {
            input.mediumPunch -> SfFighterState.MEDIUM_PUNCH
            input.mediumKick -> SfFighterState.MEDIUM_KICK
            else -> null
        }
        SfAttackStrength.MEDIUM -> when {
            input.heavyPunch -> SfFighterState.HEAVY_PUNCH
            input.heavyKick -> SfFighterState.HEAVY_KICK
            else -> null
        }
        else -> null
    } ?: return false
    return changeState(sim, idx, next, now)
}

internal fun StreetFighterViewModel.trySpecial(sim: StreetFighterViewModel.Sim, idx: Int, strength: SfAttackStrength, now: Long): Boolean {
    // Cooldown + tope de proyectiles propios activos (evita muro de hadoukens)
    if (now < specialCooldownUntil[idx.coerceIn(0, 1)]) return false
    val ownBalls = sim.fireballs.count {
        it.ownerIndex == idx && it.state == SfFireballState.ACTIVE
    }
    if (ownBalls >= StreetFighterViewModel.MAX_ACTIVE_FIREBALLS_PER_FIGHTER) return false
    val state = when (strength) {
        SfAttackStrength.LIGHT -> SfFighterState.SPECIAL_1_LIGHT
        SfAttackStrength.MEDIUM -> SfFighterState.SPECIAL_1_MEDIUM
        SfAttackStrength.HEAVY -> SfFighterState.SPECIAL_1_HEAVY
    }
    val ok = changeState(sim, idx, state, now)
    if (ok) {
        // IA vs IA / PESADILLA: cooldown más largo para que el rival pueda reaccionar
        val aiVs = _state.value.aiVsAi
        specialCooldownUntil[idx.coerceIn(0, 1)] = now + if (aiVs) StreetFighterViewModel.SPECIAL_COOLDOWN_AIVSAI_MS
        else StreetFighterViewModel.SPECIAL_COOLDOWN_MS
    }
    return ok
}

internal fun StreetFighterViewModel.tryBonusPower(sim: StreetFighterViewModel.Sim, idx: Int, power: Int, now: Long): Boolean {
    if (now < specialCooldownUntil[idx.coerceIn(0, 1)]) return false
    val fighter = sim.fighter(idx)
    // La Presidenta: P1..P10 son poderes; P11 es SOLO la metamorfosis automática (no se elige).
    val maxUsable = usableBonusPowerCount(fighter.id)
    if (power !in 1..maxUsable) return false
    val state = sfBonusPowerState(power) ?: return false
    if (dataFor(fighter).animations[state.jsKey].isNullOrEmpty()) return false
    val ok = changeState(sim, idx, state, now)
    if (ok) {
        specialCooldownUntil[idx.coerceIn(0, 1)] = now + if (_state.value.aiVsAi) {
            StreetFighterViewModel.BONUS_COOLDOWN_AIVSAI_MS
        } else {
            StreetFighterViewModel.BONUS_COOLDOWN_MS
        }
    }
    return ok
}

/** Poderes Grok “lanzables” (excluye las metamorfosis automáticas: P11 Presidenta, P10 Yoalli). */
internal fun StreetFighterViewModel.usableBonusPowerCount(id: SfFighterId): Int = sfUsableBonusPowerCount(id)

/**
 * Fin de BONUS_POWER_11 de La Presidenta → Yoalli Ehécatl con VIDA LLENA. El cambio de id es
 * PERMANENTE. ⚠️ 🆕 (2026-07-25) Dirección HISTÓRICA/inactiva en gameplay: la metamorfosis
 * automática del arcade ahora es la INVERSA (Yoalli→Presidenta, ver [tryYoalliMetamorphosis] y
 * [completeYoalliMetamorphosis]). Se conserva por simetría (animación disponible).
 */
internal fun StreetFighterViewModel.completePresidentaMetamorphosis(sim: StreetFighterViewModel.Sim, idx: Int, now: Long) {
    val f = sim.fighter(idx)
    if (f.id != SfFighterId.LA_PRESIDENTA) {
        changeState(sim, idx, SfFighterState.IDLE, now)
        return
    }
    // 🆕 (2026-07-22, decisión del dueño) Al completar la metamorfosis arranca con la
    // VIDA LLENA otra vez (antes 50%): es su "segunda vida" del round 1.
    completeMetamorphosis(sim, idx, SfFighterId.YOALLI_EHECATL, SfConstants.HEALTH_MAX_HIT_POINTS, now)
}

/**
 * Fin de BONUS_POWER_10 de Yoalli: se convierte en LA PRESIDENTA con la VIDA LLENA (su
 * "segunda vida" del round 1). 🆕 (2026-07-25) Antes conservaba la vida (~1/4); ahora es la
 * metamorfosis PRINCIPAL del arcade (Yoalli jefe FINAL → Presidenta), espejo de lo que hacía
 * La Presidenta. El cambio de id es PERMANENTE (persiste entre rondas).
 */
internal fun StreetFighterViewModel.completeYoalliMetamorphosis(sim: StreetFighterViewModel.Sim, idx: Int, now: Long) {
    val f = sim.fighter(idx)
    if (f.id != SfFighterId.YOALLI_EHECATL) {
        changeState(sim, idx, SfFighterState.IDLE, now)
        return
    }
    completeMetamorphosis(sim, idx, SfFighterId.LA_PRESIDENTA, SfConstants.HEALTH_MAX_HIT_POINTS, now)
}

internal fun StreetFighterViewModel.completeMetamorphosis(
    sim: StreetFighterViewModel.Sim,
    idx: Int,
    targetId: SfFighterId,
    targetHitPoints: Int,
    now: Long,
) {
    val transformed = clampFighterToStage(
        sim.fighter(idx).copy(
            id = targetId,
            hitPoints = targetHitPoints,
            metamorphosing = false,
            metamorphosed = true,
            fireballFired = false,
            attackStruck = false,
            velocityX = 0f,
            velocityY = 0f,
            slideVelocity = 0f,
            slideFriction = 0f,
            y = SfConstants.STAGE_FLOOR,
        ),
    )
    sim.setFighter(idx, transformed)
    changeState(sim, idx, SfFighterState.IDLE, now)
    val after = sim.fighter(idx)
    sim.setFighter(
        idx,
        clampFighterToStage(
            after.copy(
                id = targetId,
                hitPoints = targetHitPoints,
                metamorphosed = true,
                metamorphosing = false,
            ),
        ),
    )
    // HUD: forzar roll-up hacia la vida resultante de la transformación.
    if (idx == 0) dispHp0 = targetHitPoints.toFloat() else dispHp1 = targetHitPoints.toFloat()
}

internal fun StreetFighterViewModel.maybeTurn(sim: StreetFighterViewModel.Sim, idx: Int, turnState: SfFighterState, now: Long) {
    val f = sim.fighter(idx)
    val opp = sim.fighter(1 - idx)
    val facing = if (f.x <= opp.x) SfDirection.RIGHT else SfDirection.LEFT
    if (facing != f.direction) {
        sim.setFighter(idx, f.copy(direction = facing))
        changeState(sim, idx, turnState, now)
    }
}

/**
 * Corrige el encaramiento antes de interpretar `forward/backward`. Un cross-up o un empuje
 * puede intercambiar los lados mientras sigue caminando; con la cara vieja, la siguiente orden
 * de acercarse se convierte en alejarse. 🆕 (2026-07-22) Se usa para la CPU **y el jugador**
 * (antes solo la CPU se auto-encaraba; al jugador le tocaba soltar todo y quedar quieto para
 * girar, lo que se sentía "muy complicado" tras cruzar de lado).
 */
internal fun StreetFighterViewModel.repairFacing(sim: StreetFighterViewModel.Sim, idx: Int, now: Long) {
    val fighter = sim.fighter(idx)
    val opponent = sim.fighter(1 - idx)
    val expected = if (fighter.x <= opponent.x) SfDirection.RIGHT else SfDirection.LEFT
    if (fighter.direction == expected || fighter.isAirborne ||
        fighter.state == SfFighterState.KO || fighter.state == SfFighterState.VICTORY ||
        fighter.metamorphosing
    ) return

    sim.setFighter(idx, fighter.copy(direction = expected))
    if (fighter.state == SfFighterState.WALK_FORWARD ||
        fighter.state == SfFighterState.WALK_BACKWARD ||
        fighter.state == SfFighterState.IDLE_TURN
    ) {
        changeState(sim, idx, SfFighterState.IDLE, now)
    }
}

// ------------------------------------------------------------------
// Cajas por frame + constraints + colisión de ataque
// ------------------------------------------------------------------


