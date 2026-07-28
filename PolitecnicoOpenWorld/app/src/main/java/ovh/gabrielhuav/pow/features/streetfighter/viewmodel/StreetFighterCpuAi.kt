package ovh.gabrielhuav.pow.features.streetfighter.viewmodel

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import ovh.gabrielhuav.pow.domain.models.streetfighter.SF_HURT_STATES
import ovh.gabrielhuav.pow.domain.models.streetfighter.SF_BONUS_POWER_STATES
import ovh.gabrielhuav.pow.domain.models.streetfighter.SF_BLOCK_STATES
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfAnimation
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfDamage
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfPhysics
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfStateMachine
import ovh.gabrielhuav.pow.domain.models.streetfighter.sfUsableBonusPowerCount
import ovh.gabrielhuav.pow.domain.models.streetfighter.SF_DOWNED_STATES
import ovh.gabrielhuav.pow.domain.models.streetfighter.SF_NEW_ATTACK_STATES
import ovh.gabrielhuav.pow.domain.models.streetfighter.SF_NEW_MOVE_STATES
import ovh.gabrielhuav.pow.domain.models.streetfighter.SF_PARRY_STATES
import ovh.gabrielhuav.pow.features.streetfighter.data.SfCombo
import ovh.gabrielhuav.pow.features.streetfighter.data.SfComboAction
import ovh.gabrielhuav.pow.features.streetfighter.data.SfCombos
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfArcadeLadder
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfAttackStrength
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfAttackType
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfBox
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfConstants
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfCpuDifficulty
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfDirection
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighter
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterData
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterId
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterState
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFireball
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFireballState
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfHitSplash
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfHurtArea
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfInput
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfProjectileEvent
import ovh.gabrielhuav.pow.domain.models.streetfighter.bonusPowerIndex
import ovh.gabrielhuav.pow.domain.models.streetfighter.sfBonusPowerState
import ovh.gabrielhuav.pow.BuildConfig
import ovh.gabrielhuav.pow.data.auth.AuthManager
import ovh.gabrielhuav.pow.data.repository.SettingsRepository
import ovh.gabrielhuav.pow.data.repository.SfArcadeRepository
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfStageCatalog
import ovh.gabrielhuav.pow.features.streetfighter.data.SF_CLASSIC_THEME
import ovh.gabrielhuav.pow.features.streetfighter.data.SfBtClient
import ovh.gabrielhuav.pow.features.streetfighter.data.SfFrameCatalog
import ovh.gabrielhuav.pow.features.streetfighter.data.SfLanClient
import ovh.gabrielhuav.pow.features.streetfighter.data.SfLanDiscovery
import ovh.gabrielhuav.pow.features.streetfighter.data.SfLanGame
import ovh.gabrielhuav.pow.features.streetfighter.data.SfMatchClient
import ovh.gabrielhuav.pow.features.streetfighter.data.SfNetFireball
import ovh.gabrielhuav.pow.features.streetfighter.data.SfNetMsg
import ovh.gabrielhuav.pow.features.streetfighter.data.SfNetTransport
import ovh.gabrielhuav.pow.features.streetfighter.data.SfWebRtcClient
import ovh.gabrielhuav.pow.features.streetfighter.data.isSfLowEnd
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import kotlin.math.abs
import kotlin.random.Random

// ─────────────────────────────────────────────────────────────────────────────
// PARCIAL de StreetFighterViewModel: 🤖 IA DE LA CPU (decisión, rangos, combos).
//
// Extraído de StreetFighterViewModel.kt en el refactor de tamaño de la Fase 5. Aquí vive cómo
// DECIDE la máquina: lectura de distancia (clinch/melee/mid/far), aproximación y retirada,
// antiaéreos, reacción a proyectiles, los 3 estilos por dificultad y la ejecución de combos
// del catálogo (`assets/DATA/combos.json`).
//
// ⚠️ Son EXTENSIONES del VM, no miembros. Los CAMPOS de estado de la IA (`cpuComboQueue`,
// `cpuAttackHistory`, `comboCatalog`…) siguen en la clase a propósito: aquí solo vive la LÓGICA.
// NO recrees estas funciones como miembros — quedarían gemelas y ganaría el miembro en silencio
// (ver 09 §0).
// ─────────────────────────────────────────────────────────────────────────────

/**
 * IA de UN peleador (`selfIndex` 0 o 1). En VS normal solo se llama con 1 (CPU);
 * en IA vs IA se llama para 0 y 1. Cadencia e intención sostenida son POR índice.
 *
 * 2026-07-18i: rangos SF (clinch/melee/mid/far), aproximación en **mundo** (no solo
 * “forward” de la cara), separación al pegarse, desync IA vs IA, watchdog ofensivo.
 */
internal fun StreetFighterViewModel.buildCpuInput(now: Long, sim: StreetFighterViewModel.Sim, selfIndex: Int): SfInput {
    if (sim.battleEnded) return SfInput()
    // 🆕 (2026-07-21) TUTORIAL: el rival es un MUÑECO inerte — no ataca ni se mueve, para
    // que el jugador practique la ejecución sin interrupciones.
    if (inTutorial) return SfInput()
    val i = selfIndex.coerceIn(0, 1)
    repairFacing(sim, i, now)
    if (now < cpuNextDecisionMs[i]) return cpuHold[i]

    val aiVs = _state.value.aiVsAi
    // 🆕 (2026-07-18n) En IA vs IA cada índice puede tener su PROPIA dificultad (desnivel
    // aleatorio de startAiVsAi) para que la pelea se resuelva; fuera de IA vs IA = la global.
    val difficulty = if (aiVs) cpuDiffOverride[i] ?: _state.value.cpuDifficulty
    else _state.value.cpuDifficulty
    // Desync en IA vs IA: P1 piensa un poco desfasado → no se copian el espejo eterno
    val desync = if (aiVs) (i * 17L) else 0L
    val baseDelay = when (difficulty) {
        SfCpuDifficulty.BASICA -> Random.nextLong(650L, 1100L)
        SfCpuDifficulty.NORMAL -> Random.nextLong(160L, 340L)
        SfCpuDifficulty.AVANZADA -> Random.nextLong(55L, 120L)
        SfCpuDifficulty.PESADILLA -> Random.nextLong(35L, 75L)
    }
    cpuNextDecisionMs[i] = now + desync +
        (baseDelay * (1f - 0.42f * cpuIntensity)).toLong().coerceAtLeast(30L)

    var decision = when (difficulty) {
        SfCpuDifficulty.BASICA -> basicCpuDecision(sim, i)
        SfCpuDifficulty.NORMAL -> normalCpuDecision(sim, i, now)
        SfCpuDifficulty.AVANZADA -> smartCpuDecision(sim, i, now, nightmare = false)
        SfCpuDifficulty.PESADILLA -> smartCpuDecision(sim, i, now, nightmare = true)
    }

    val me = sim.fighter(i)
    val foe = sim.fighter(1 - i)
    val dist = abs(me.x - foe.x)

    // 🆕 (2026-07-25) Con un FATALITY comprometido (dash → RUN → súper), el run-up NO lleva
    // ataque, así que el force-engage / watchdog / anti-walk-loop lo abortarían. Los saltamos
    // mientras la intención esté activa: la propia `maybeFatalityInput` decide y se auto-cancela.
    val fatalityCommitted = now < cpuFatalityUntilMs[i]

    if (now < cpuForceEngageUntilMs[i] && !me.isAirborne && !fatalityCommitted) {
        decision = if (dist > StreetFighterViewModel.CPU_MELEE_DIST * 0.75f) {
            cpuApproach(me, foe)
        } else {
            variedCpuAttack(i)
        }
    }

    // 🆕 (2026-07-18j) Ofensiva REAL: el reloj del watchdog se alimenta del ESTADO del
    // peleadór (está atacando de verdad), no solo de la intención. Antes un ataque decidido
    // pero DESCARTADO (cooldown de special, validFrom, HURT en curso) contaba como ofensiva
    // → pasividad larga sin corrección ("se quedan quietos").
    if (me.state in attackMeta || me.state in SF_BONUS_POWER_STATES) cpuLastOffenseMs[i] = now

    // Watchdog: si lleva demasiado tiempo SIN ofensiva → forzar acción.
    // 🆕 (fix 2026-07-18) Antes solo actuaba a < 150 px; en IA vs IA ambos se quedaban
    // CAMINANDO / mirándose a media distancia sin que saltara nunca. Ahora cubre CUALQUIER
    // distancia: si están LEJOS obliga a CERRAR distancia (approach), y en rango de golpe
    // fuerza ataque o clinch break. Así nunca se estancan sin pelear.
    val watchdogLimit = when {
        difficulty == SfCpuDifficulty.BASICA && aiVs -> 3500L
        difficulty == SfCpuDifficulty.BASICA -> null
        aiVs -> 420L
        else -> 700L
    }
    if (watchdogLimit != null && !fatalityCommitted) {
        val staleMs = now - cpuLastOffenseMs[i]
        if (staleMs > watchdogLimit && !decision.hasAttackOrSpecial()) {
            // 🆕 (2026-07-18j) Con pasividad extrema (>2×limit) el golpe es OBLIGATORIO en
            // rango de pelea: garantiza que NUNCA pasen ~2 s sin acción estando cerca.
            val forceHit = staleMs > watchdogLimit * 2
            val attack = if (difficulty == SfCpuDifficulty.BASICA) {
                cpuAttack(SfAttackStrength.LIGHT, punch = Random.nextBoolean())
            } else {
                variedCpuAttack(i)
            }
            decision = when {
                dist < StreetFighterViewModel.CPU_CLINCH_DIST -> cpuClinchBreak(me, foe, now, i)
                dist < 150f -> if (forceHit || Random.nextFloat() < 0.75f) attack else cpuJumpIn(me, foe)
                else -> cpuApproach(me, foe) // pasivo demasiado tiempo y lejos → acercarse YA
            }
        }
    }

    // Anti-walk-loop: caminar hacia el rival sin golpear de cerca
    val onlyWalkIn = decision.isOnlyWalkToward(me, foe)
    if (onlyWalkIn && dist < 120f && !fatalityCommitted) {
        cpuStaleApproach[i]++
        if (cpuStaleApproach[i] >= 2) {
            decision = if (dist < StreetFighterViewModel.CPU_CLINCH_DIST) {
                cpuClinchBreak(me, foe, now, i)
            } else {
                variedCpuAttack(i)
            }
            cpuStaleApproach[i] = 0
        }
    } else if (decision.hasAttackOrSpecial()) {
        cpuStaleApproach[i] = 0
    }

    cpuHold[i] = decision

    // Bonus powers (show en IA vs IA; raros vs humano)
    val bonusCount = usableBonusPowerCount(me.id)
    val bonusChance = when {
        difficulty == SfCpuDifficulty.BASICA -> 0f
        dist > 170f -> if (aiVs) 0.04f else 0.02f
        me.id == SfFighterId.LA_PRESIDENTA && !aiVs -> 0.035f
        aiVs -> 0.07f
        else -> 0.045f
    }
    val bonusStateReady = bonusCount > 0 && me.state in attackValidFrom && !me.metamorphosing
    val bonusPositionReady = !isNearStageCorner(me.x) && dist in 70f..200f
    val bonusCooldownReady = now >= specialCooldownUntil[i]
    if (bonusStateReady && bonusPositionReady && bonusCooldownReady &&
        Random.nextFloat() < bonusChance) {
        cpuHold[i] = SfInput(bonusPower = Random.nextInt(1, bonusCount + 1))
    }

    val oneShot = cpuHold[i]
    // Sostener direcciones (presión / walk-back); botones y salto = un tick
    cpuHold[i] = oneShot.copy(
        lightPunch = false, mediumPunch = false, heavyPunch = false,
        lightKick = false, mediumKick = false, heavyKick = false,
        special = null, bonusPower = null, up = false,
    )
    return oneShot
}


/** ¿Borde del stage? */
internal fun StreetFighterViewModel.isNearStageCorner(x: Float): Boolean =
    x <= StreetFighterViewModel.STAGE_X_MIN + 52f || x >= StreetFighterViewModel.STAGE_X_MAX - 52f

/** Flags de input para moverse HACIA el rival en coordenadas del mundo (corrige cara invertida). */
internal fun StreetFighterViewModel.cpuMoveTowardFlags(me: SfFighter, foe: SfFighter): SfInput {
    val wantRight = foe.x > me.x
    val faceRight = me.direction == SfDirection.RIGHT
    return if (wantRight == faceRight) SfInput(forward = true) else SfInput(backward = true)
}

/** Alejarse del rival (crear espacio / clinch break). */
internal fun StreetFighterViewModel.cpuRetreatFlags(me: SfFighter, foe: SfFighter): SfInput {
    val wantRight = foe.x > me.x
    val faceRight = me.direction == SfDirection.RIGHT
    // Invertir “hacia”
    return if (wantRight == faceRight) SfInput(backward = true) else SfInput(forward = true)
}

internal fun StreetFighterViewModel.cpuApproach(me: SfFighter, foe: SfFighter): SfInput {
    if (isNearStageCorner(me.x) && abs(me.x - foe.x) > 40f) {
        // Salir de esquina hacia el centro/rival
        return cpuMoveTowardFlags(me, foe)
    }
    return cpuMoveTowardFlags(me, foe)
}

internal fun StreetFighterViewModel.cpuJumpIn(me: SfFighter, foe: SfFighter): SfInput {
    val t = cpuMoveTowardFlags(me, foe)
    return t.copy(up = true)
}

internal fun StreetFighterViewModel.cpuJumpBack(me: SfFighter, foe: SfFighter): SfInput {
    val t = cpuRetreatFlags(me, foe)
    return t.copy(up = true)
}

/** Muy pegados: NO seguir caminando adentro — retroceder, golpear o brincar fuera. */
internal fun StreetFighterViewModel.cpuClinchBreak(me: SfFighter, foe: SfFighter, now: Long, selfIndex: Int): SfInput {
    val roll = Random.nextFloat()
    val aiVs = _state.value.aiVsAi
    cpuWantsSpaceUntilMs[selfIndex] = now + if (aiVs) {
        Random.nextLong(160L, 300L)
    } else {
        Random.nextLong(280L, 520L)
    }
    if (aiVs) {
        // 🆕 (2026-07-18j) ROLES ASIMÉTRICOS: antes ambos índices rodaban la MISMA tabla y
        // solían decidir lo mismo (los dos retro o los dos golpe ligero) → se quedaban
        // "pegados" sin resolverse. Ahora se alterna por índice y tiempo: uno GOLPEA
        // (variado) mientras el otro SE SEPARA (retro/salto) — el clinch siempre termina
        // en acción visible.
        val attackerTurn = ((now / 900L).toInt() + selfIndex) % 2 == 0
        return when {
            attackerTurn && roll < 0.70f -> variedCpuAttack(selfIndex)
            attackerTurn -> cpuJumpIn(me, foe) // cross-up por encima
            roll < 0.42f -> cpuRetreatFlags(me, foe)
            roll < 0.68f -> cpuJumpBack(me, foe)
            else -> variedCpuAttack(selfIndex)
        }
    }
    return when {
        roll < 0.28f -> cpuRetreatFlags(me, foe)
        roll < 0.48f -> cpuJumpBack(me, foe)
        roll < 0.72f -> cpuAttack(SfAttackStrength.LIGHT, punch = Random.nextBoolean())
        roll < 0.88f -> cpuAttack(SfAttackStrength.MEDIUM, punch = true)
        else -> cpuJumpIn(me, foe) // cross-up / saltar por encima
    }
}

internal fun StreetFighterViewModel.hasIncomingFireball(sim: StreetFighterViewModel.Sim, me: SfFighter, selfIndex: Int, range: Float): Boolean {
    val opp = 1 - selfIndex
    return sim.fireballs.any { fb ->
        fb.ownerIndex == opp && fb.state == SfFireballState.ACTIVE &&
            abs(fb.x - me.x) < range && (me.x - fb.x) * fb.direction.sign > 0f
    }
}

internal fun StreetFighterViewModel.ownFireballActive(sim: StreetFighterViewModel.Sim, selfIndex: Int): Boolean =
    sim.fireballs.any { it.ownerIndex == selfIndex && it.state == SfFireballState.ACTIVE }

/**
 * Ajuste ligero por personaje sobre el mismo motor: zoners priorizan poderes; rushers
 * presión. 🆕 (2026-07-21) `comboBias` = cuánto le gusta encadenar RUTAS de combo.
 */
// `internal` y no `private`: lo devuelven funciones `internal` de este mismo parcial.
internal data class CpuStyle(
    val specialBias: Float,
    val pressureBias: Float,
    val comboBias: Float = 1f,
)

/**
 * 🆕 (2026-07-21) PERFIL ESCALADO POR NIVEL. La IA es compartida, pero el perfil de
 * CADA personaje se ACENTÚA conforme avanzas la escalera: `cpuIntensity` sube 0.20→1.0
 * con el escalón, así que un zoner lanza cada vez más poderes y un rusher presiona cada
 * vez más, además de encadenar combos más seguido. En VS (`cpuIntensity` = 0) devuelve
 * prácticamente el perfil base, así que las peleas sueltas no cambian.
 */
internal fun StreetFighterViewModel.cpuStyleForLevel(id: SfFighterId): CpuStyle {
    val base = cpuStyle(id)
    val k = 0.6f + 0.8f * cpuIntensity          // 0.6 al principio → 1.4 en la final
    return CpuStyle(
        specialBias = 1f + (base.specialBias - 1f) * k,
        pressureBias = 1f + (base.pressureBias - 1f) * k,
        comboBias = base.comboBias * (0.7f + 0.8f * cpuIntensity),
    )
}

internal fun StreetFighterViewModel.cpuStyle(id: SfFighterId): CpuStyle = when (id) {
    SfFighterId.ROBOT,
    SfFighterId.CHARRO_NEGRO,
    SfFighterId.LA_LLORONA,
    SfFighterId.LA_TZITZIMIME,
    SfFighterId.YOALLI_EHECATL,
    SfFighterId.LA_PRESIDENTA,
    // Zoners: mucho poder a distancia, menos presión y menos combos largos.
    -> CpuStyle(specialBias = 1.25f, pressureBias = 0.90f, comboBias = 0.85f)

    SfFighterId.ESCOMBOY,
    SfFighterId.ESCOMGIRL,
    SfFighterId.POLICIA_CDMX_HOMBRE,
    SfFighterId.POLICIA_CDMX,
    SfFighterId.POLICIA_GRANADERO_HOMBRE,
    SfFighterId.POLICIA_GRANADERO_MUJER,
    // Rushers: se pegan, encadenan combos y usan menos poderes.
    -> CpuStyle(specialBias = 0.80f, pressureBias = 1.12f, comboBias = 1.25f)

    else -> CpuStyle(specialBias = 1f, pressureBias = 1f)
}

// ------------------------------------------------------------------
// Decisiones por dificultad
// ------------------------------------------------------------------

/** BÁSICA — aprendible: lenta, pocos golpes, sin poderes. */
internal fun StreetFighterViewModel.basicCpuDecision(sim: StreetFighterViewModel.Sim, selfIndex: Int): SfInput {
    val me = sim.fighter(selfIndex)
    val foe = sim.fighter(1 - selfIndex)
    val dist = abs(me.x - foe.x)
    val roll = Random.nextFloat()
    if (dist < StreetFighterViewModel.CPU_CLINCH_DIST && roll < 0.55f) return cpuRetreatFlags(me, foe)
    return when {
        dist > 190f -> if (roll < 0.7f) cpuApproach(me, foe) else SfInput()
        dist > 95f -> when {
            roll < 0.5f -> cpuApproach(me, foe)
            roll < 0.78f -> SfInput()
            else -> cpuRetreatFlags(me, foe)
        }
        else -> when {
            roll < 0.28f -> cpuAttack(SfAttackStrength.LIGHT, punch = Random.nextBoolean())
            roll < 0.55f -> cpuRetreatFlags(me, foe)
            else -> SfInput()
        }
    }
}

/** NORMAL — pelea real: acerca, golpea, special raro, clinch break. */
internal fun StreetFighterViewModel.normalCpuDecision(sim: StreetFighterViewModel.Sim, selfIndex: Int, now: Long): SfInput {
    val me = sim.fighter(selfIndex)
    val foe = sim.fighter(1 - selfIndex)
    val dist = abs(me.x - foe.x)
    val roll = Random.nextFloat()
    val corner = isNearStageCorner(me.x)
    val specialBias = cpuStyle(me.id).specialBias

    if (now < cpuWantsSpaceUntilMs[selfIndex] && dist < StreetFighterViewModel.CPU_MELEE_DIST) {
        return if (roll < 0.7f) cpuRetreatFlags(me, foe) else variedCpuAttack(selfIndex)
    }
    if (corner && dist > 50f) return cpuApproach(me, foe)
    if (dist < StreetFighterViewModel.CPU_CLINCH_DIST) return cpuClinchBreak(me, foe, now, selfIndex)

    if (hasIncomingFireball(sim, me, selfIndex, 240f) && !me.isAirborne) {
        return if (roll < 0.7f) cpuJumpIn(me, foe) else cpuRetreatFlags(me, foe)
    }
    if (foe.state in cpuThreatStates && dist < 140f) {
        return when {
            roll < 0.42f -> cpuRetreatFlags(me, foe)
            dist < 95f && roll < 0.72f -> cpuAttack(SfAttackStrength.LIGHT, punch = true)
            else -> cpuJumpBack(me, foe)
        }
    }
    if (foe.isAirborne && dist < 130f) {
        return cpuAttack(SfAttackStrength.HEAVY, punch = true)
    }
    if (foe.state in cpuPunishStates && dist < StreetFighterViewModel.CPU_MELEE_DIST) {
        return cpuAttack(SfAttackStrength.MEDIUM, punch = Random.nextBoolean())
    }

    return when {
        dist > StreetFighterViewModel.CPU_MID_DIST -> when {
            !ownFireballActive(sim, selfIndex) && roll < 0.16f ->
                SfInput(special = SfAttackStrength.LIGHT)
            roll < 0.30f -> cpuJumpIn(me, foe)
            else -> cpuApproach(me, foe)
        }
        dist > StreetFighterViewModel.CPU_MELEE_DIST -> when {
            roll < 0.58f -> cpuApproach(me, foe)
            !ownFireballActive(sim, selfIndex) &&
                now >= specialCooldownUntil[selfIndex] &&
                roll < 0.58f + 0.10f * specialBias -> SfInput(special = SfAttackStrength.LIGHT)
            roll < 0.88f -> cpuJumpIn(me, foe)
            else -> cpuRetreatFlags(me, foe)
        }
        else -> when { // melee
            roll < 0.72f + 0.12f * cpuIntensity -> variedCpuAttack(selfIndex)
            roll < 0.88f -> cpuRetreatFlags(me, foe) // micro-spacing
            else -> cpuJumpIn(me, foe)
        }
    }
}

/**
 * AVANZADA + PESADILLA — núcleo SF:
 * defense (fireball/anti-air/block) → punish → clinch/spacing → pressure por rango.
 * @param nightmare más agresivo (PESADILLA / IA vs IA show).
 */
/**
 * 🆕 (2026-07-25) FATALITY comprometido de la IA. El fatality es "SÚPER EN CARRERA": hay que
 * llegar a [SfFighterState.RUN] y soltar el súper. Antes la IA "nunca" lo hacía porque, al
 * correr hacia el rival, entraba en rango de CLINCH y abortaba (o quedaba fuera del rango de
 * dash). Aquí, una vez COMPROMETIDA (medidor lleno; rival aturdido = garantizado), mantiene la
 * intención [StreetFighterViewModel.FATALITY_INTENT_MS] y la completa (dash → RUN → súper) sobreponiéndose a todo.
 * Devuelve null si no aplica (deja seguir a la IA normal).
 */
internal fun StreetFighterViewModel.maybeFatalityInput(
    me: SfFighter,
    foe: SfFighter,
    dist: Float,
    now: Long,
    i: Int,
    nightmare: Boolean,
): SfInput? {
    val idx = i.coerceIn(0, 1)
    // Necesita las hojas de FATALITY y de RUN (el comando es súper en carrera).
    if (!hasAnim(me, SfFighterState.FATALITY) || !hasAnim(me, SfFighterState.RUN)) {
        cpuFatalityUntilMs[idx] = 0L
        return null
    }
    val committed = now < cpuFatalityUntilMs[idx]
    val foeStunned = foe.state == SfFighterState.STUN
    if (!committed) {
        if (!me.superReady) return null // solo con el medidor LLENO
        // Comprometerse: rival ATURDIDO = sí o sí; si no, azar que ESCALA con la dificultad y
        // desde un rango con pista para correr (ni pegado ni lejísimos).
        val diff = if (nightmare) 1f else cpuIntensity
        val chance = (0.5f + 0.45f * diff).coerceIn(0.5f, 0.95f)
        val commit = foeStunned ||
            (dist in 60f..(StreetFighterViewModel.CPU_MID_DIST + 40f) && Random.nextFloat() < chance)
        if (!commit) return null
        cpuFatalityUntilMs[idx] = now + StreetFighterViewModel.FATALITY_INTENT_MS
    }
    // Intención ACTIVA. Si ya se gastó el medidor (lo soltó) o murió → cancelar.
    if (!me.superReady) { cpuFatalityUntilMs[idx] = 0L; return null }
    // Interrumpido (golpeado/aéreo/derribado/metamorfosis): espera SIN gastar el medidor.
    if (me.isAirborne || me.downed || isMetamorphosing(me) || me.state in SF_HURT_STATES) {
        return SfInput()
    }
    return when (me.state) {
        SfFighterState.RUN -> { cpuFatalityUntilMs[idx] = 0L; SfInput(forward = true, superArt = true) }
        SfFighterState.DASH_FORWARD -> SfInput(forward = true) // el dash ya arrancó → mantener → RUN
        else -> SfInput(dashForward = true, forward = true)    // arrancar el dash hacia la carrera
    }
}

internal fun StreetFighterViewModel.smartCpuDecision(sim: StreetFighterViewModel.Sim, selfIndex: Int, now: Long, nightmare: Boolean): SfInput {
    val me = sim.fighter(selfIndex)
    val foe = sim.fighter(1 - selfIndex)
    val dist = abs(me.x - foe.x)
    val roll = Random.nextFloat()
    val corner = isNearStageCorner(me.x)
    val aiVs = _state.value.aiVsAi
    val ownFb = ownFireballActive(sim, selfIndex)
    // 🆕 (2026-07-21) Perfil ESCALADO por escalón: el mismo personaje se vuelve más
    // fiel a su estilo (y más peligroso) conforme avanzas la escalera.
    val style = cpuStyleForLevel(me.id)

    // 🆕 (2026-07-25) FATALITY comprometido: se evalúa ANTES del clinch/space/watchdog para que,
    // una vez decidido, la IA lo COMPLETE (corra y suelte el súper) en vez de abortarlo al cerrar
    // distancia. Rival ATURDIDO + medidor lleno = garantizado (sí o sí).
    maybeFatalityInput(me, foe, dist, now, selfIndex, nightmare)?.let { return it }

    val specialFarBase = when {
        nightmare && aiVs -> 0.24f
        nightmare -> 0.18f
        else -> 0.14f + 0.08f * cpuIntensity
    }
    val specialMidBase = when {
        nightmare && aiVs -> 0.16f
        nightmare -> 0.11f
        else -> 0.08f + 0.05f * cpuIntensity
    }
    val specialFar = (specialFarBase * style.specialBias).coerceAtMost(0.34f)
    val specialMid = (specialMidBase * style.specialBias).coerceAtMost(0.24f)
    val blockChance = if (nightmare) 0.55f else 0.72f + 0.1f * cpuIntensity
    val attackMelee = ((if (nightmare) 0.78f else 0.70f) * style.pressureBias)
        .coerceIn(0.62f, 0.88f)

    // Espacio pedido tras clinch
    if (now < cpuWantsSpaceUntilMs[selfIndex] && dist < StreetFighterViewModel.CPU_MID_DIST) {
        return when {
            roll < 0.55f -> cpuRetreatFlags(me, foe)
            roll < 0.78f -> variedCpuAttack(selfIndex)
            else -> cpuJumpIn(me, foe)
        }
    }

    // Esquina: salir hacia el rival (nunca spamear desde el borde)
    if (corner && dist > 45f) return cpuApproach(me, foe)

    // Clinch / “pegaditos”
    if (dist < StreetFighterViewModel.CPU_CLINCH_DIST) return cpuClinchBreak(me, foe, now, selfIndex)

    // Fireball entrante
    if (hasIncomingFireball(sim, me, selfIndex, if (nightmare) 300f else 260f) && !me.isAirborne) {
        return when {
            roll < 0.50f -> cpuJumpIn(me, foe)
            roll < 0.78f -> SfInput(up = true) // jump neutral
            else -> cpuRetreatFlags(me, foe) // block / walk-back
        }
    }

    // 🆕 (2026-07-21) RUTA DE COMBO en curso: sigue encadenando los pasos pendientes.
    nextComboInput(selfIndex, now)?.let { return it }
    // Si el rival está a tiro y en desventaja, ARRANCA una ruta del catálogo. La
    // probabilidad sube con el escalón y con el gusto por combos del personaje.
    val comboChance = (if (nightmare) 0.45f else 0.22f + 0.20f * cpuIntensity) *
        cpuStyleForLevel(me.id).comboBias
    if (dist < StreetFighterViewModel.CPU_MELEE_DIST && !me.isAirborne && roll < comboChance &&
        queueCombo(selfIndex, me, now)
    ) {
        nextComboInput(selfIndex, now)?.let { return it }
    }

    // 🆕 (2026-07-21) La CPU usa el MOVESET nuevo cuando el peleador lo tiene.
    cpuNewMove(me, foe, dist, roll, nightmare)?.let { return it }

    // Anti-aéreo
    if (foe.isAirborne && dist < (if (nightmare) 170f else 145f)) {
        // Con arte propia, el antiaéreo correcto es el puño fuerte AGACHADO
        if (hasAnim(me, SfFighterState.CROUCH_HEAVY_PUNCH) && roll < 0.6f) {
            return SfInput(down = true, heavyPunch = true)
        }
        return cpuAttack(SfAttackStrength.HEAVY, punch = true)
    }

    // Bloqueo ante amenaza (mid); de cerca tradea
    if (foe.state in cpuThreatStates) {
        when {
            dist in 95f..190f && roll < blockChance -> return cpuRetreatFlags(me, foe) // block walk-back
            dist < 95f && roll < 0.28f -> return cpuRetreatFlags(me, foe)
            dist < 95f && roll < 0.68f -> return cpuAttack(SfAttackStrength.LIGHT, punch = true)
        }
    }

    // Castigo recovery
    if (foe.state in cpuPunishStates && dist < (if (nightmare) 145f else 125f)) {
        return cpuAttack(
            if (nightmare || roll < 0.55f) SfAttackStrength.HEAVY else SfAttackStrength.MEDIUM,
            punch = Random.nextBoolean(),
        )
    }

    // Footsies / presión por rango (🆕 2026-07-18j: golpes con memoria anti-repetición y
    // fuerza del special al azar — la pelea se ve VARIADA, no el mismo ataque en bucle)
    return when {
        dist > StreetFighterViewModel.CPU_MID_DIST -> when {
            !corner && !ownFb && now >= specialCooldownUntil[selfIndex] && roll < specialFar ->
                SfInput(special = SfAttackStrength.entries.random())
            aiVs && roll < specialFar + 0.22f -> cpuJumpIn(me, foe)
            !aiVs && roll < 0.28f -> cpuJumpIn(me, foe)
            !aiVs && roll < 0.38f -> cpuRetreatFlags(me, foe) // baitear
            else -> cpuApproach(me, foe)
        }
        dist > StreetFighterViewModel.CPU_MELEE_DIST -> when {
            !ownFb && now >= specialCooldownUntil[selfIndex] && roll < specialMid ->
                SfInput(special = SfAttackStrength.MEDIUM)
            aiVs && roll < 0.76f -> cpuApproach(me, foe)
            aiVs && roll < 0.90f -> cpuJumpIn(me, foe)
            aiVs -> cpuApproach(me, foe)
            roll < 0.55f -> cpuApproach(me, foe)
            roll < 0.72f -> cpuJumpIn(me, foe)
            roll < 0.86f -> cpuRetreatFlags(me, foe)
            else -> cpuApproach(me, foe)
        }
        else -> when { // melee range (no clinch)
            roll < attackMelee + 0.1f * cpuIntensity -> variedCpuAttack(selfIndex)
            roll < 0.90f -> cpuRetreatFlags(me, foe) // tick throw-ish spacing
            else -> cpuJumpIn(me, foe)
        }
    }
}

/**
 * 🆕 (2026-07-21) Decisiones del MOVESET nuevo para la CPU. Devuelve null si el
 * peleador no tiene esas hojas o si no toca usarlas: así la IA de siempre sigue
 * intacta para quien no tenga el arte.
 *
 * Prioridades (de más específica a más oportunista): súper cargada de cerca →
 * castigo con barrida → agarre a quien se cubre mucho → overhead contra guardia
 * baja → patada larga a media distancia → parry defensivo → dash para cerrar hueco.
 */
// 🆕 (2026-07-22) Firma acotada a lo que usa: `sim`, `selfIndex` y `now` no se usaban aquí
// (detekt UnusedParameter). La decisión de la IA nueva depende solo de los peleadores,
// la distancia, el azar y la dificultad.
@Suppress("ReturnCount")
internal fun StreetFighterViewModel.cpuNewMove(
    me: SfFighter,
    foe: SfFighter,
    dist: Float,
    roll: Float,
    nightmare: Boolean,
): SfInput? {
    if (!hasAnim(me, SfFighterState.PARRY_HIGH)) return null // sin moveset nuevo
    val aggressive = nightmare || cpuIntensity > 0.5f

    // 🆕 (2026-07-25) El FATALITY (súper en carrera) lo maneja `maybeFatalityInput` ANTES del
    // clinch (intención comprometida); si se llega hasta aquí es que NO hay fatality en curso.
    // Queda el SÚPER normal como respaldo a rango de golpe (peleadores sin RUN, o cuando el
    // fatality no se comprometió). Rival aturdido = garantizado.
    val diff = if (nightmare) 1f else cpuIntensity
    val superChance = (0.35f + 0.40f * diff).coerceIn(0.35f, 0.90f)
    val foeStunned = foe.state == SfFighterState.STUN
    if (me.superReady && dist < StreetFighterViewModel.CPU_MELEE_DIST && hasAnim(me, SfFighterState.SUPER_ART) &&
        (foeStunned || roll < superChance)
    ) {
        return SfInput(superArt = true)
    }
    // BARRIDA para castigar recuperación (derriba y da espacio)
    if (foe.state in cpuPunishStates && dist < 110f &&
        hasAnim(me, SfFighterState.SWEEP) && roll < 0.45f
    ) {
        return SfInput(down = true, heavyKick = true)
    }
    // AGARRE a quien se cubre (el bloqueo no salva del lanzamiento)
    if (dist < SfConstants.GRAB_RANGE && hasAnim(me, SfFighterState.GRAB) &&
        (foe.state in SF_BLOCK_STATES || foe.state == SfFighterState.WALK_BACKWARD) &&
        roll < (if (aggressive) 0.6f else 0.35f)
    ) {
        return SfInput(grab = true)
    }
    // OVERHEAD contra guardia BAJA (para eso existe: la rompe)
    if (dist < 95f && hasAnim(me, SfFighterState.OVERHEAD) &&
        foe.state in setOf(SfFighterState.CROUCH, SfFighterState.BLOCK_LOW) && roll < 0.5f
    ) {
        return SfInput(forward = true, mediumPunch = true)
    }
    // PATADA LARGA: su normal de mayor alcance, ideal en footsies
    if (dist in 100f..165f && hasAnim(me, SfFighterState.LONG_KICK) &&
        roll < (if (aggressive) 0.42f else 0.24f)
    ) {
        return SfInput(forward = true, heavyKick = true)
    }
    // PARRY: leer el golpe entrante (solo dificultades altas: es la jugada experta)
    if (aggressive && foe.state in cpuThreatStates && dist < 120f && roll < 0.22f) {
        return SfInput(parry = true)
    }
    // DASH para cerrar distancia rápido
    if (dist > StreetFighterViewModel.CPU_MID_DIST && hasAnim(me, SfFighterState.DASH_FORWARD) &&
        roll < (if (aggressive) 0.30f else 0.16f)
    ) {
        return SfInput(dashForward = true)
    }
    return null
}



/**
 * Traduce una acción del catálogo al `SfInput` que la dispara. Es la ÚNICA fuente de
 * verdad de "cómo se hace" cada movimiento: la usan la IA y el tutorial.
 */
internal fun StreetFighterViewModel.inputForAction(action: SfComboAction): SfInput = when (action) {
    SfComboAction.LIGHT_PUNCH -> SfInput(lightPunch = true)
    SfComboAction.MEDIUM_PUNCH -> SfInput(mediumPunch = true)
    SfComboAction.HEAVY_PUNCH -> SfInput(heavyPunch = true)
    SfComboAction.LIGHT_KICK -> SfInput(lightKick = true)
    SfComboAction.MEDIUM_KICK -> SfInput(mediumKick = true)
    SfComboAction.HEAVY_KICK -> SfInput(heavyKick = true)
    SfComboAction.CROUCH_PUNCH -> SfInput(down = true, lightPunch = true)
    SfComboAction.CROUCH_KICK -> SfInput(down = true, lightKick = true)
    SfComboAction.CROUCH_HEAVY_PUNCH -> SfInput(down = true, heavyPunch = true)
    SfComboAction.SWEEP -> SfInput(down = true, heavyKick = true)
    SfComboAction.LONG_KICK -> SfInput(forward = true, heavyKick = true)
    SfComboAction.OVERHEAD -> SfInput(forward = true, mediumPunch = true)
    SfComboAction.AIR_PUNCH -> SfInput(mediumPunch = true)
    SfComboAction.AIR_KICK -> SfInput(mediumKick = true)
    SfComboAction.DASH_FORWARD -> SfInput(dashForward = true)
    SfComboAction.DASH_BACKWARD -> SfInput(dashBackward = true)
    SfComboAction.PARRY -> SfInput(parry = true)
    SfComboAction.GRAB -> SfInput(grab = true)
    SfComboAction.TAUNT -> SfInput(taunt = true)
    SfComboAction.SPECIAL -> SfInput(special = SfAttackStrength.MEDIUM)
    SfComboAction.SUPER_ART -> SfInput(superArt = true)
    SfComboAction.JUMP -> SfInput(up = true)
    SfComboAction.CROUCH -> SfInput(down = true)
    SfComboAction.WALK_FORWARD -> SfInput(forward = true)
    SfComboAction.RUN -> SfInput(forward = true)
    SfComboAction.BLOCK_HIGH -> SfInput(backward = true)
    // El fatality se pide EN CARRERA: adelante sostenido + súper.
    SfComboAction.FATALITY -> SfInput(forward = true, superArt = true)
}

/** Estado en el que DEBE entrar el peleador si la acción salió bien (validación). */
internal fun StreetFighterViewModel.stateForAction(action: SfComboAction): Set<SfFighterState> = when (action) {
    SfComboAction.LIGHT_PUNCH -> setOf(SfFighterState.LIGHT_PUNCH)
    SfComboAction.MEDIUM_PUNCH -> setOf(SfFighterState.MEDIUM_PUNCH)
    SfComboAction.HEAVY_PUNCH -> setOf(SfFighterState.HEAVY_PUNCH)
    SfComboAction.LIGHT_KICK -> setOf(SfFighterState.LIGHT_KICK)
    SfComboAction.MEDIUM_KICK -> setOf(SfFighterState.MEDIUM_KICK)
    SfComboAction.HEAVY_KICK -> setOf(SfFighterState.HEAVY_KICK)
    SfComboAction.CROUCH_PUNCH -> setOf(SfFighterState.CROUCH_PUNCH)
    SfComboAction.CROUCH_KICK -> setOf(SfFighterState.CROUCH_KICK)
    SfComboAction.CROUCH_HEAVY_PUNCH -> setOf(SfFighterState.CROUCH_HEAVY_PUNCH)
    SfComboAction.SWEEP -> setOf(SfFighterState.SWEEP)
    SfComboAction.LONG_KICK -> setOf(SfFighterState.LONG_KICK)
    SfComboAction.OVERHEAD -> setOf(SfFighterState.OVERHEAD)
    SfComboAction.AIR_PUNCH -> setOf(SfFighterState.AIR_PUNCH)
    SfComboAction.AIR_KICK -> setOf(SfFighterState.AIR_KICK)
    SfComboAction.DASH_FORWARD -> setOf(SfFighterState.DASH_FORWARD)
    SfComboAction.DASH_BACKWARD -> setOf(SfFighterState.DASH_BACKWARD)
    SfComboAction.PARRY -> SF_PARRY_STATES
    SfComboAction.GRAB -> setOf(SfFighterState.GRAB, SfFighterState.THROW)
    SfComboAction.TAUNT -> setOf(SfFighterState.TAUNT)
    SfComboAction.SPECIAL -> setOf(
        SfFighterState.SPECIAL_1_LIGHT, SfFighterState.SPECIAL_1_MEDIUM,
        SfFighterState.SPECIAL_1_HEAVY,
    )
    SfComboAction.SUPER_ART -> setOf(SfFighterState.SUPER_ART)
    SfComboAction.JUMP -> setOf(
        SfFighterState.JUMP_START, SfFighterState.JUMP_UP,
        SfFighterState.JUMP_FORWARD, SfFighterState.JUMP_BACKWARD,
    )
    SfComboAction.CROUCH -> setOf(SfFighterState.CROUCH, SfFighterState.CROUCH_DOWN)
    SfComboAction.WALK_FORWARD -> setOf(SfFighterState.WALK_FORWARD)
    SfComboAction.RUN -> setOf(SfFighterState.RUN)
    SfComboAction.BLOCK_HIGH -> setOf(
        SfFighterState.BLOCK_HIGH, SfFighterState.BLOCK_LOW, SfFighterState.WALK_BACKWARD,
    )
    SfComboAction.FATALITY -> setOf(SfFighterState.FATALITY)
}

/**
 * ¿El peleador tiene ARTE para esta acción? (independiente de recursos como el medidor).
 * Es lo que decide si una lección/combo se puede ENSEÑAR: el medidor se llena durante
 * la propia lección pegándole al muñeco.
 */
internal fun StreetFighterViewModel.hasArtFor(f: SfFighter, action: SfComboAction): Boolean {
    val states = stateForAction(action)
    return states.none { it in SF_NEW_MOVE_STATES } || states.any { hasAnim(f, it) }
}

/**
 * ¿Puede ejecutarla AHORA MISMO? Añade el requisito de RECURSO (medidor lleno para la
 * súper y el fatality). Lo usa la IA al elegir una ruta; el tutorial NO, porque si no
 * las lecciones de súper/fatality se filtraban al arrancar con el medidor a cero y
 * nunca se enseñaban.
 */
internal fun StreetFighterViewModel.canPerform(f: SfFighter, action: SfComboAction): Boolean {
    val needsMeter = action == SfComboAction.SUPER_ART || action == SfComboAction.FATALITY
    if (needsMeter && !f.superReady) return false
    return hasArtFor(f, action)
}

/**
 * 🆕 Elige una RUTA de combo ejecutable y la encola. La IA prefiere el combo de FIRMA
 * del peleador y, si no puede, uno universal de su nivel de dificultad hacia abajo.
 */
internal fun StreetFighterViewModel.queueCombo(selfIndex: Int, me: SfFighter, now: Long): Boolean {
    val i = selfIndex.coerceIn(0, 1)
    if (cpuComboQueue[i].isNotEmpty()) return false
    val maxLevel = when {
        cpuIntensity > 0.66f -> 4
        cpuIntensity > 0.33f -> 3
        else -> 2
    }
    val options = buildList {
        signatureCombo(me.id)?.let { add(it) }
        addAll(comboCatalog.filter { it.level <= maxLevel })
    }.filter { combo -> combo.steps.all { canPerform(me, it) } }
    val chosen = options.randomOrNull() ?: return false
    cpuComboQueue[i].addAll(chosen.steps)
    cpuComboUntilMs[i] = now + StreetFighterViewModel.COMBO_ROUTE_TIMEOUT_MS
    return true
}

/** Siguiente paso de la ruta encolada (null si no hay o si expiró). */
internal fun StreetFighterViewModel.nextComboInput(selfIndex: Int, now: Long): SfInput? {
    val i = selfIndex.coerceIn(0, 1)
    if (cpuComboQueue[i].isEmpty()) return null
    if (now > cpuComboUntilMs[i]) { cpuComboQueue[i].clear(); return null }
    return inputForAction(cpuComboQueue[i].removeFirst())
}

/** Arma un SfInput de golpe (puño o patada) de la fuerza pedida. */
internal fun StreetFighterViewModel.cpuAttack(strength: SfAttackStrength, punch: Boolean): SfInput = if (punch) {
    when (strength) {
        SfAttackStrength.LIGHT -> SfInput(lightPunch = true)
        SfAttackStrength.MEDIUM -> SfInput(mediumPunch = true)
        SfAttackStrength.HEAVY -> SfInput(heavyPunch = true)
    }
} else {
    when (strength) {
        SfAttackStrength.LIGHT -> SfInput(lightKick = true)
        SfAttackStrength.MEDIUM -> SfInput(mediumKick = true)
        SfAttackStrength.HEAVY -> SfInput(heavyKick = true)
    }
}

/**
 * 🆕 (2026-07-18j) Golpe al azar SIN repetir el último (firma fuerza×tipo por peleadór).
 * Sustituye a randomCpuAttack(): con 6 combos y memoria de 1, la IA mezcla puños/patadas
 * y fuerzas en vez de encadenar el MISMO ataque una y otra vez.
 */
internal fun StreetFighterViewModel.variedCpuAttack(selfIndex: Int): SfInput {
    val i = selfIndex.coerceIn(0, 1)
    val history = cpuAttackHistory[i]
    val sig = (0 until 6).filterNot(history::contains).ifEmpty { (0 until 6).toList() }.random()
    history.addLast(sig)
    while (history.size > 3) history.removeFirst()
    return cpuAttack(SfAttackStrength.entries[sig / 2], punch = sig % 2 == 0)
}
