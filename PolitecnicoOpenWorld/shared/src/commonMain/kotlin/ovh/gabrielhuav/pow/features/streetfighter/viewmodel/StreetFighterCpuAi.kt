package ovh.gabrielhuav.pow.features.streetfighter.viewmodel

import ovh.gabrielhuav.pow.domain.models.streetfighter.SF_BONUS_POWER_STATES
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfAttackStrength
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfCpuDifficulty
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterId
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfInput
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
// 🆕 (2026-09-23) PARTIDO EN CUATRO al pasar de 1000 líneas (09 §0). Aquí queda solo el
// ORQUESTADOR (`buildCpuInput`); el resto vive en archivos hermanos del MISMO paquete, así que
// se siguen viendo sin imports:
//   · StreetFighterCpuAiPolitica.kt — reglas puras de dificultad y defensa contra la súper.
//   · StreetFighterCpuAiDecision.kt — decisión por tick, espaciado y perfil por personaje.
//   · StreetFighterCpuAiCombos.kt   — acción→input, validación y cola de rutas.
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
    val aiVs = _state.value.aiVsAi
    val me = sim.fighter(i)
    val difficulty = if (aiVs) cpuDiffOverride[i] ?: _state.value.cpuDifficulty
    else _state.value.cpuDifficulty

    // Contrato del modo espectador IA vs IA: barra llena = SUPER ART en el primer tick en que
    // la máquina de estados pueda aceptarla. Se evalúa ANTES de la cadencia aleatoria y se vuelve
    // a emitir mientras el medidor siga lleno; así una animación/recovery que rechace un pulso no
    // puede hacer que la CPU conserve la barra. También abandona fatality/combos pendientes.
    forcedAiVsAiSuperInput(aiVs, me.superReady)?.let { forced ->
        cpuFatalityUntilMs[i] = 0L
        cpuComboQueue[i].clear()
        cpuComboAwaiting[i] = null
        cpuHold[i] = SfInput()
        cpuNextDecisionMs[i] = now
        return forced
    }

    // La SUPER ART rival tiene arranque visible: una CPU competente debe poder saltar,
    // retroceder o interrumpirla antes de que llegue su cuadro de impacto.
    val foe = sim.fighter(1 - i)
    cpuDefenseAgainstSuper(me, foe, difficulty)?.let { defense ->
        cpuComboQueue[i].clear()
        cpuComboAwaiting[i] = null
        cpuHold[i] = defense.withCpuOneShotsReleased()
        cpuNextDecisionMs[i] = now
        return defense
    }

    // Fuera del show IA vs IA también hay garantía: al vencer el plazo, la CPU abandona su
    // intención anterior, cierra distancia y reintenta la SUPER ART cada tick válido.
    if (!aiVs) {
        maybeCpuSuperArtInput(me, foe, abs(me.x - foe.x), now, i, difficulty, aiVs)?.let { committed ->
            cpuFatalityUntilMs[i] = 0L
            cpuComboQueue[i].clear()
            cpuComboAwaiting[i] = null
            cpuHold[i] = committed.withCpuOneShotsReleased()
            cpuNextDecisionMs[i] = now
            return committed
        }
    }
    if (now < cpuNextDecisionMs[i]) return cpuHold[i]

    // 🆕 (2026-07-18n) En IA vs IA cada índice puede tener su PROPIA dificultad (desnivel
    // aleatorio de startAiVsAi) para que la pelea se resuelva; fuera de IA vs IA = la global.
    // Desync en IA vs IA: P1 piensa un poco desfasado → no se copian el espejo eterno
    val desync = if (aiVs) (i * 17L) else 0L
    val baseDelay = when (difficulty) {
        SfCpuDifficulty.BASICA -> Random.nextLong(650L, 1100L)
        SfCpuDifficulty.NORMAL -> Random.nextLong(90L, 200L)
        SfCpuDifficulty.AVANZADA -> Random.nextLong(45L, 95L)
        SfCpuDifficulty.PESADILLA -> Random.nextLong(28L, 60L)
    }
    cpuNextDecisionMs[i] = now + desync +
        (baseDelay * (1f - 0.42f * cpuIntensity)).toLong().coerceAtLeast(30L)

    val dist = abs(me.x - foe.x)
    var decision = when (difficulty) {
        SfCpuDifficulty.BASICA -> basicCpuDecision(sim, i)
        SfCpuDifficulty.NORMAL -> normalCpuDecision(sim, i, now)
        SfCpuDifficulty.AVANZADA -> smartCpuDecision(sim, i, now, nightmare = false)
        SfCpuDifficulty.PESADILLA -> smartCpuDecision(sim, i, now, nightmare = true)
    }

    // 🆕 (2026-07-25) Con un FATALITY comprometido (dash → RUN → súper), el run-up NO lleva
    // ataque, así que el force-engage / watchdog / anti-walk-loop lo abortarían. Los saltamos
    // mientras la intención esté activa: la propia `maybeFatalityInput` decide y se auto-cancela.
    val fatalityCommitted = now < cpuFatalityUntilMs[i]

    if (now < cpuForceEngageUntilMs[i] && !me.isAirborne && !fatalityCommitted &&
        !decision.hasAttackOrSpecial()
    ) {
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
        difficulty == SfCpuDifficulty.BASICA && aiVs -> 900L
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
    if (!cpuHold[i].hasAttackOrSpecial() && bonusStateReady && bonusPositionReady && bonusCooldownReady &&
        Random.nextFloat() < bonusChance) {
        cpuHold[i] = SfInput(bonusPower = Random.nextInt(1, bonusCount + 1))
    }

    val oneShot = cpuHold[i]
    // Sostener direcciones (presión / walk-back); botones y salto = un tick
    cpuHold[i] = oneShot.withCpuOneShotsReleased()
    return oneShot
}
