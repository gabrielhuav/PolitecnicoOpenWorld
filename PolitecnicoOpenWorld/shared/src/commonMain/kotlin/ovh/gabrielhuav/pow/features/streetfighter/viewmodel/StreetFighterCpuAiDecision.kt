package ovh.gabrielhuav.pow.features.streetfighter.viewmodel

import ovh.gabrielhuav.pow.domain.models.streetfighter.SF_BLOCK_STATES
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfAttackStrength
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfConstants
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfCpuDifficulty
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfDirection
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighter
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterId
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterState
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFireballState
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfInput
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfStateMachine
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfSuperArt
import kotlin.math.abs
import kotlin.random.Random

// ─────────────────────────────────────────────────────────────────────────────
// PARCIAL de StreetFighterViewModel: 🤖 IA DE LA CPU — decisión, espaciado y personalidad.
//
// Qué hace la CPU en cada tick: súper, aproximación/retirada, antiaéreos, lectura de
// proyectiles, el perfil por personaje y las tres decisiones por dificultad.
//
// Separado de StreetFighterCpuAi.kt (2026-09-23): aquel archivo llegó a 1094 líneas y 09 §0
// pide partir por encima de 1000. SOLO se movieron líneas; ninguna función cambió.
//
// ⚠️ Son EXTENSIONES del VM, no miembros. Los CAMPOS de estado de la IA (`cpuComboQueue`,
// `cpuAttackHistory`, `comboCatalog`…) siguen en la clase a propósito: aquí solo vive la LÓGICA.
// NO recrees estas funciones como miembros — quedarían gemelas y ganaría el miembro en silencio
// (ver 09 §0).
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Garantia de gasto del medidor para cualquier dificultad. La CPU puede usarlo antes por su
 * decision normal (Avanzada/Pesadilla), pero nunca lo retiene indefinidamente por mala suerte.
 */
internal fun StreetFighterViewModel.maybeCpuSuperArtInput(
    me: SfFighter,
    foe: SfFighter,
    dist: Float,
    now: Long,
    selfIndex: Int,
    difficulty: SfCpuDifficulty,
    aiVs: Boolean,
): SfInput? {
    val i = selfIndex.coerceIn(0, 1)
    if (!me.superReady || !hasAnim(me, SfFighterState.SUPER_ART)) {
        cpuSuperReadySinceMs[i] = 0L
        return null
    }
    if (cpuSuperReadySinceMs[i] == 0L) {
        cpuSuperReadySinceMs[i] = now.coerceAtLeast(1L)
        return null
    }
    val maxWaitMs = cpuSuperCommitDelayMs(difficulty, aiVs)
    if (now - cpuSuperReadySinceMs[i] < maxWaitMs) return null
    // Una vez comprometida, no empieza otra acción durante recovery/aire/daño: deja que ese
    // estado termine y reintenta en el siguiente tick en vez de volver a distraerse.
    if (cpuIsInterrupted(me.state, me.isAirborne, me.downed, me.metamorphosing)) return SfInput()
    if (me.state !in SfStateMachine.SPECIAL_VALID_FROM) return SfInput()
    // Un normal solo puede cancelar al conectar. Si todavía va al aire, se reintenta en la
    // siguiente decisión sin dar la barra por gastada.
    if (me.state in attackMeta && !me.attackStruck) return SfInput()
    if (dist > SfSuperArt.HIT_RANGE) return cpuApproach(me, foe)
    cpuFatalityUntilMs[i] = 0L
    // No se confirma aquí: el timer se limpia cuando el motor realmente consume el medidor.
    // Así un input rechazado por transición/animación vuelve a intentarse en vez de perderse.
    return SfInput(superArt = true)
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

/** BÁSICA — aprendible: lenta y con pocos golpes; la súper cargada no se desperdicia. */
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
    // Una ruta ya confirmada conserva prioridad para que los cancels no pierdan su ventana.
    nextComboInput(selfIndex, me, now)?.let { return it }
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
        if (queueCombo(selfIndex, me, now, SfCpuDifficulty.NORMAL)) {
            nextComboInput(selfIndex, me, now)?.let { return it }
        }
        return cpuAttack(SfAttackStrength.MEDIUM, punch = Random.nextBoolean())
    }

    val comboChance = cpuComboStartChance(
        difficulty = SfCpuDifficulty.NORMAL,
        intensity = cpuIntensity,
        styleBias = cpuStyleForLevel(me.id).comboBias,
    )
    if (dist < StreetFighterViewModel.CPU_MELEE_DIST && !me.isAirborne && roll < comboChance &&
        queueCombo(selfIndex, me, now, SfCpuDifficulty.NORMAL)
    ) {
        nextComboInput(selfIndex, me, now)?.let { return it }
    }

    return when {
        dist > StreetFighterViewModel.CPU_MID_DIST -> when {
            !ownFireballActive(sim, selfIndex) && roll < 0.22f ->
                SfInput(special = SfAttackStrength.LIGHT)
            roll < 0.38f -> cpuJumpIn(me, foe)
            else -> cpuApproach(me, foe)
        }
        dist > StreetFighterViewModel.CPU_MELEE_DIST -> when {
            roll < 0.50f -> cpuApproach(me, foe)
            !ownFireballActive(sim, selfIndex) &&
                now >= specialCooldownUntil[selfIndex] &&
                roll < 0.50f + 0.16f * specialBias -> SfInput(special = SfAttackStrength.LIGHT)
            roll < 0.90f -> cpuJumpIn(me, foe)
            else -> cpuRetreatFlags(me, foe)
        }
        else -> when { // melee
            roll < 0.80f + 0.10f * cpuIntensity -> variedCpuAttack(selfIndex)
            roll < 0.92f -> cpuRetreatFlags(me, foe) // micro-spacing
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
    if (cpuIsInterrupted(me.state, me.isAirborne, me.downed, isMetamorphosing(me))) {
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

    // La SUPER ART normal tiene prioridad absoluta al llenarse el medidor. La ruta de fatality
    // (dash -> RUN -> super) puede fallar si la carrera se interrumpe; si se evalua primero se
    // apropia de la barra indefinidamente y la IA parece no usar nunca su poder cargado.
    val normalSuperAvailable = me.superReady && hasAnim(me, SfFighterState.SUPER_ART)
    maybeCpuSuperArtInput(
        me = me,
        foe = foe,
        dist = dist,
        now = now,
        selfIndex = selfIndex,
        difficulty = if (nightmare) SfCpuDifficulty.PESADILLA else SfCpuDifficulty.AVANZADA,
        aiVs = aiVs,
    )?.let { return it }
    // Mientras exista una SUPER ART normal disponible, ni siquiera se abre una intención de
    // fatality: aunque la garantía todavía esté midiendo su plazo, esa ruta no puede apropiarse
    // de la barra. El fatality queda como respaldo para un moveset sin SUPER_ART utilizable.
    if (!normalSuperAvailable) {
        maybeFatalityInput(me, foe, dist, now, selfIndex, nightmare)?.let { return it }
    }

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
    val blockChance = if (nightmare) 0.82f else 0.76f + 0.1f * cpuIntensity
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
    nextComboInput(selfIndex, me, now)?.let { return it }

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
        if (dist in 95f..190f) {
            return if (roll < blockChance) cpuRetreatFlags(me, foe) else cpuJumpBack(me, foe)
        }
        if (dist < 95f) {
            val parryChance = if (nightmare) 0.38f else 0.20f + 0.10f * cpuIntensity
            // 🆕 (2026-08-29) CONTRAATAQUE: la apuesta arriesgada de la defensa (ventana más
            // corta que el parry, pero premia con daño real) — bastante MENOS frecuente que el
            // parry, que sigue siendo el default seguro de la IA.
            val counterChance = parryChance * 0.4f
            return when {
                hasAnim(me, SfFighterState.PARRY_HIGH) && roll < parryChance -> SfInput(parry = true)
                hasAnim(me, SfFighterState.COUNTER) && roll < parryChance + counterChance ->
                    SfInput(counter = true)
                roll < (if (nightmare) 0.76f else 0.64f) -> cpuRetreatFlags(me, foe)
                else -> cpuAttack(SfAttackStrength.LIGHT, punch = true)
            }
        }
    }

    // Castigo recovery
    if (foe.state in cpuPunishStates && dist < (if (nightmare) 145f else 125f)) {
        val difficulty = if (nightmare) SfCpuDifficulty.PESADILLA else SfCpuDifficulty.AVANZADA
        if (dist < StreetFighterViewModel.CPU_MELEE_DIST &&
            queueCombo(selfIndex, me, now, difficulty)
        ) {
            nextComboInput(selfIndex, me, now)?.let { return it }
        }
        return cpuAttack(
            if (nightmare || roll < 0.55f) SfAttackStrength.HEAVY else SfAttackStrength.MEDIUM,
            punch = Random.nextBoolean(),
        )
    }

    // Abrir una ruta solo después de resolver amenazas, antiaéreos y castigos. Así la IA no
    // sacrifica defensa por azar, pero en neutro cercano encadena con mucha más constancia.
    val difficulty = if (nightmare) SfCpuDifficulty.PESADILLA else SfCpuDifficulty.AVANZADA
    val comboChance = cpuComboStartChance(difficulty, cpuIntensity, style.comboBias)
    if (dist < StreetFighterViewModel.CPU_MELEE_DIST && !me.isAirborne && roll < comboChance &&
        queueCombo(selfIndex, me, now, difficulty)
    ) {
        nextComboInput(selfIndex, me, now)?.let { return it }
    }

    // La CPU usa el moveset nuevo cuando no está ejecutando una ruta del catálogo.
    cpuNewMove(me, foe, dist, roll, nightmare)?.let { return it }

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
 * baja → patada larga a media distancia → dash para cerrar hueco. El parry reactivo se
 * resuelve antes, junto con el bloqueo de amenazas, para no competir con decisiones ofensivas.
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
    // 🆕 (2026-08-29) DERRIBO CON PODER: con medidor de sobra (y sin guardarlo para la
    // súper/fatality, que ya se evaluaron antes en smartCpuDecision), prefiere el agarre CARO
    // sobre el gratis — mismo gatillo que el AGARRE de abajo, pero con más daño y empuje.
    val powerGrabReady = hasAnim(me, SfFighterState.POWER_GRAB) &&
        me.superMeter >= SfConstants.POWER_THROW_METER_COST && !me.superReady
    val foeTurtling = foe.state in SF_BLOCK_STATES || foe.state == SfFighterState.WALK_BACKWARD
    if (dist < SfConstants.GRAB_RANGE && powerGrabReady && foeTurtling &&
        roll < (if (aggressive) 0.6f else 0.35f)
    ) {
        return SfInput(powerThrow = true)
    }
    // AGARRE a quien se cubre (el bloqueo no salva del lanzamiento)
    if (dist < SfConstants.GRAB_RANGE && hasAnim(me, SfFighterState.GRAB) && foeTurtling &&
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
    // DASH para cerrar distancia rápido
    if (dist > StreetFighterViewModel.CPU_MID_DIST && hasAnim(me, SfFighterState.DASH_FORWARD) &&
        roll < (if (aggressive) 0.30f else 0.16f)
    ) {
        return SfInput(dashForward = true)
    }
    return null
}
