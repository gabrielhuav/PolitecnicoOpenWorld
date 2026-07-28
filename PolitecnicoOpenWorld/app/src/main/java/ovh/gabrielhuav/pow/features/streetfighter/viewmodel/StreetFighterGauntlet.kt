package ovh.gabrielhuav.pow.features.streetfighter.viewmodel

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import ovh.gabrielhuav.pow.domain.models.streetfighter.SF_HURT_STATES
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfArcadeLadder
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfAttackStrength
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfConstants
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfCpuDifficulty
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighter
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterId
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterState
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfInput
import ovh.gabrielhuav.pow.domain.models.streetfighter.bonusPowerIndex
import ovh.gabrielhuav.pow.data.repository.SfArcadeRepository
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfStageCatalog
import ovh.gabrielhuav.pow.features.streetfighter.data.SF_CLASSIC_THEME
import ovh.gabrielhuav.pow.features.streetfighter.data.SfFrameCatalog
import kotlin.random.Random

// ────────────────────────────────────────────────────────────────────────────
// PARCIAL de StreetFighterViewModel: 🧪 IA VS IA, GAUNTLET, SHOWCASE y AUDITORÍA de assets (herramientas de QA)
//
// Extraído de StreetFighterViewModel.kt en el refactor de tamaño de la Fase 5. Esto NO es
// gameplay del jugador: es el instrumental con el que se prueba el modo pelea — peleas
// automáticas IA vs IA, el round-robin del gauntlet, el escaparate de animaciones y las
// auditorías que detectan assets o sonidos que faltan.
// El campo showcaseExtraStates se queda en la clase (es estado, no lógica).
//
// ⚠️ Son EXTENSIONES del VM, no miembros: el estado sigue viviendo en la clase. NO recrees
// estas funciones como miembros — quedarían gemelas y ganaría el miembro EN SILENCIO
// (ver 09 §0, el gotcha miembro-vs-extensión que ya costó caro en este repo).
// ────────────────────────────────────────────────────────────────────────────

fun StreetFighterViewModel.startAiVsAi(
    a: SfFighterId,
    b: SfFighterId,
    difficulty: SfCpuDifficulty = SfCpuDifficulty.PESADILLA,
    intensity: Float = 1f,
) {
    resetInternals()
    // Intensidad al máximo (igual que la final del arcade); resetInternals la deja en 0
    cpuIntensity = intensity.coerceIn(0f, 1f)
    // 🆕 (2026-07-18n) DESNIVEL ALEATORIO: dos peleadores iguales se esquivan sin fin y nadie
    // gana. Se le baja la dificultad a UNO al azar (1–2 escalones) → el otro conecta y gana.
    // Se re-aleatoriza en cada pelea (revancha/gauntlet) para que el ganador varíe.
    val strongIndex: Int
    if (gauntletCampaignMode) {
        // Auditoría de campañas: P0 (Jugador) gana SIEMPRE para avanzar la escalera.
        cpuDiffOverride[0] = SfCpuDifficulty.PESADILLA
        cpuDiffOverride[1] = SfCpuDifficulty.BASICA
        strongIndex = 0
    } else {
        val weak = Random.nextInt(2)
        val steps = 1 + Random.nextInt(2)
        val weakDiff = SfCpuDifficulty.entries[(difficulty.ordinal - steps).coerceAtLeast(0)]
        cpuDiffOverride[weak] = weakDiff
        cpuDiffOverride[1 - weak] = difficulty
        strongIndex = 1 - weak
    }
    // 🆕 (2026-07-22) El escenario es el HOGAR del peleador con la IA MÁS avanzada (no ESCOM).
    val strongFighter = if (strongIndex == 0) a else b
    roundIntroUntilMs = StreetFighterViewModel.ROUND_INTRO_MS // banner "RONDA 1 / PELEA" (gameNow arranca en 0)
    val base = StreetFighterState()
    _state.value = base.copy(
        player = base.player.copy(id = a),
        cpu = base.cpu.copy(id = b),
        inCharacterSelect = false,
        showRoundIntro = true,
        cpuDifficulty = difficulty,
        aiVsAi = true,
        arcadeMapFile = SfStageCatalog.mapForRival(strongFighter, difficulty),
    )
}

// ------------------------------------------------------------------
// 🆕 AUTOJUEGO (gauntlet): recorre muchas peleas IA vs IA seguidas para PROBAR a todos los
// peleadores y volcar un reporte de assets rotos (atascos/estancamientos detectados por
// watchStuck/watchStalemate). Al terminar escribe un .txt y muestra el reporte en pantalla.
// ------------------------------------------------------------------

/** Bot 1: TODOS contra TODOS (round-robin). ~N² peleas — déjalo corriendo/grabando. */
fun StreetFighterViewModel.startGauntletRoundRobin() {
    showcaseMode = false
    gauntletCampaignMode = false
    val roster = SfArcadeLadder.ALL_PARTICIPANTS
    val q = ArrayDeque<StreetFighterViewModel.GauntletFight>()
    val lightings = SfStageCatalog.Lighting.entries
    var mapIndex = 0
    for (a in roster) {
        for (b in roster) {
            if (a != b) {
                val stage = SfStageCatalog.ALL_STAGES[mapIndex % SfStageCatalog.ALL_STAGES.size]
                val lighting = lightings[(mapIndex / SfStageCatalog.ALL_STAGES.size) % lightings.size]
                val mapFile = stage.file(lighting)
                mapIndex++
                q.add(StreetFighterViewModel.GauntletFight(a, b, mapFile = mapFile))
            }
        }
    }
    beginGauntlet(q)
}

/**
 * Bot 3: SHOWCASE de assets — cada peleador recorre TODAS sus animaciones (caminar, saltar,
 * agacharse, giros, los 6 golpes, especial L/M/F, poderes y 🆕 2026-07-18j: también los
 * HURT_*, KO, VICTORY y la metamorfosis de La Presidenta) reproduciendo sus sonidos, para
 * verlos/oírlos y detectar los rotos. Además corre una AUDITORÍA ESTÁTICA por peleadór
 * (animaciones faltantes/vacías, frames rotos, .ogg del special y SFX del tema).
 * Recorre TODOS los peleadores. (No es pelea real.)
 */
fun StreetFighterViewModel.startShowcase() {
    showcaseMode = true
    gauntletCampaignMode = false
    showcaseSpeed = 1f
    val q = ArrayDeque<StreetFighterViewModel.GauntletFight>()
    SfArcadeLadder.ALL_PARTICIPANTS.forEach {
        q.add(StreetFighterViewModel.GauntletFight(it, it)) // Espejo: se ve la animación en ambos.
    }
    beginGauntlet(q)
}

/** Bot 2: las 9 campañas completas (3 protagonistas × 3 dificultades), 135 peleas reales. */
fun StreetFighterViewModel.startGauntletArcade() {
    showcaseMode = false
    gauntletCampaignMode = true
    val difficulties = listOf(
        SfCpuDifficulty.BASICA,
        SfCpuDifficulty.NORMAL,
        SfCpuDifficulty.AVANZADA,
    )
    val q = ArrayDeque<StreetFighterViewModel.GauntletFight>()
    SfArcadeLadder.STARTERS.forEach { player ->
        difficulties.forEach { difficulty ->
            val ladder = SfArcadeLadder.build(player, difficulty)
            ladder.forEach { step ->
                q.add(
                    StreetFighterViewModel.GauntletFight(
                        player = player,
                        rival = step.rival,
                        difficulty = SfArcadeLadder.difficultyForStep(difficulty, step),
                        intensity = SfArcadeLadder.intensityForStep(step.index, ladder.size),
                        mapFile = step.mapFile,
                    ),
                )
            }
        }
    }
    beginGauntlet(q)
}

internal fun StreetFighterViewModel.beginGauntlet(q: ArrayDeque<StreetFighterViewModel.GauntletFight>) {
    assetIssues.clear()
    gauntletQueue.clear()
    gauntletQueue.addAll(q)
    gauntletTotal = q.size
    gauntletDone = 0
    gauntletKoRounds = 0
    gauntletTimeoutRounds = 0
    gauntletActive = true
    _state.update { it.copy(gauntletFinished = false, gauntletReport = emptyList(), gauntletReportPath = null) }
    if (showcaseMode) auditThemeSounds() // 🆕 SFX compartidos del tema (una vez por corrida)
    startNextGauntletFight()
}

internal fun StreetFighterViewModel.startNextGauntletFight() {
    val next = gauntletQueue.removeFirstOrNull()
    if (next == null) {
        finishGauntlet()
        return
    }
    gauntletDone++
    startAiVsAi(next.player, next.rival, next.difficulty, next.intensity)
    gauntletActive = true
    if (showcaseMode) {
        showcaseStep = -1 // el primer tick lo sube a 0 (paso "caminar")
        showcaseStepUntilMs = 0L
        showcaseFiredStep = -2
        showcaseForcedState = null
        // Cap POR PASOS: el guion completo (con extras/metamorfosis) supera los 60 s fijos
        gauntletFightCapCurMs = (showcaseTotalSteps(next.player) + 3L) * showcaseStepMs + 4000L
        // 🆕 Auditoría estática del peleadór (anims + frames + special_<id>.ogg)
        auditFighterAssets(next.player)
    } else {
        gauntletFightCapCurMs = gauntletFightCapMs
    }
    // 🆕 (2026-07-18k) Cada pelea del autojuego usa el MAPA HOGAR del peleadór en turno:
    // showcase = hogar de DÍA del peleadór mostrado (se ve claro para QA); gauntlets IA vs IA
    // = hogar del rival en su variante APOCALIPSIS (acorde a PESADILLA). Así el bot también
    // recorre/prueba los fondos.
    val mapFile = if (showcaseMode) {
        SfStageCatalog.homeStage(next.player).file(SfStageCatalog.Lighting.DAY)
    } else {
        next.mapFile ?: SfStageCatalog.mapForRival(next.rival, next.difficulty)
    }
    _state.update {
        it.copy(
            gauntletRunning = true,
            gauntletProgress = "$gauntletDone/$gauntletTotal",
            showcaseRunning = showcaseMode,
            showcaseSpeed = showcaseSpeed,
            gauntletMapFile = mapFile,
        )
    }
}

/**
 * SALTAR (View): da por terminado el bloque del peleador actual y avanza en el siguiente
 * tick. No basta con cortar la pose: el tope temporal también debe quedar satisfecho para
 * que el showcase no espere el resto del guion con el personaje inmóvil.
 */
fun StreetFighterViewModel.skipShowcaseFighter() {
    if (!gauntletActive || !showcaseMode) return
    showcaseForcedState = null
    showcaseStep = showcaseTotalSteps(_state.value.player.id) + 1
    showcaseFiredStep = showcaseStep
    showcaseStepUntilMs = _state.value.gameTimeMs
}

/** Termina solo la animación actual y conserva al mismo peleador para el paso siguiente. */
fun StreetFighterViewModel.skipToNextShowcaseAnimation() {
    if (!gauntletActive || !showcaseMode) return
    val now = _state.value.gameTimeMs
    showcaseForcedState = null
    showcaseFiredStep = -2
    showcaseStepUntilMs = now
    _state.update {
        it.copy(
            player = resetShowcaseFighter(it.player, now),
            cpu = resetShowcaseFighter(it.cpu, now),
        )
    }
}

/** Retrocede a la animación anterior en el showcase. */
fun StreetFighterViewModel.goToPreviousShowcaseAnimation() {
    if (!gauntletActive || !showcaseMode) return
    val now = _state.value.gameTimeMs
    showcaseForcedState = null
    showcaseFiredStep = -2
    // Restamos 2 porque la simulación incrementará 1 de forma inmediata
    showcaseStep = (showcaseStep - 2).coerceAtLeast(-1)
    showcaseStepUntilMs = now
    _state.update {
        it.copy(
            player = resetShowcaseFighter(it.player, now),
            cpu = resetShowcaseFighter(it.cpu, now),
        )
    }
}

/** Alterna 1x → 2x → 4x para acelerar el showcase visual o autojuego en curso. */
fun StreetFighterViewModel.cycleShowcaseSpeed() {
    if (!gauntletActive) return
    val index = SHOWCASE_SPEEDS.indexOf(showcaseSpeed).coerceAtLeast(0)
    showcaseSpeed = SHOWCASE_SPEEDS[(index + 1) % SHOWCASE_SPEEDS.size]
    _state.update { it.copy(showcaseSpeed = showcaseSpeed) }
}

internal fun StreetFighterViewModel.resetShowcaseFighter(fighter: SfFighter, now: Long): SfFighter =
    withAnimationFrame(
        fighter.copy(
            state = SfFighterState.IDLE,
            velocityX = 0f,
            velocityY = 0f,
            slideVelocity = 0f,
            slideFriction = 0f,
            attackStruck = false,
            fireballFired = false,
            y = SfConstants.STAGE_FLOOR,
        ),
        frame = 0,
        now = now,
    )

/** Se llama al inicio del tick: encadena la siguiente pelea al terminar el combate o al vencer el tope. */
internal fun StreetFighterViewModel.maybeAdvanceGauntlet(now: Long): Boolean {
    val showcaseDone = showcaseMode && showcaseStep > showcaseTotalSteps(_state.value.player.id)
    val ended = matchOver && now >= endMenuAtMs
    val timedOut = now >= gauntletFightCapCurMs
    if (!showcaseDone && !ended && !timedOut) return false
    if (timedOut && !ended && !showcaseDone) {
        val s = _state.value
        logAssetIssue(
            "TIMEOUT ${s.player.id.name} vs ${s.cpu.id.name}: la pelea no terminó en " +
                "${gauntletFightCapCurMs / 1000}s (posible atasco/estancamiento)",
        )
    }
    startNextGauntletFight()
    return true
}

internal fun StreetFighterViewModel.finishGauntlet() {
    gauntletActive = false
    showcaseMode = false
    gauntletCampaignMode = false
    val issues = assetIssues.toList()
    val path = writeGauntletReport(issues)
    _state.update {
        it.copy(
            gauntletRunning = false,
            showcaseRunning = false,
            gauntletMapFile = null,
            gauntletFinished = true,
            gauntletReport = issues,
            gauntletReportPath = path,
            inCharacterSelect = true, // al terminar, vuelve al selector (con el reporte encima)
        )
    }
}

internal fun StreetFighterViewModel.writeGauntletReport(issues: List<String>): String? = runCatching {
    val dir = appContext.getExternalFilesDir(null) ?: appContext.filesDir
    val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
    val file = java.io.File(dir, "sf_diagnostico_$stamp.txt")
    val header = "POW — Diagnóstico IA vs IA (autojuego)\n" +
        "Peleas: $gauntletDone/$gauntletTotal\n" +
        "Rondas por KO: $gauntletKoRounds\n" +
        "Rondas por tiempo: $gauntletTimeoutRounds\n" +
        "Problemas: ${issues.size}\n\n"
    file.writeText(header + if (issues.isEmpty()) "Sin problemas detectados." else issues.joinToString("\n"))
    file.absolutePath
}.getOrNull()

/** Detiene el gauntlet en curso y muestra el reporte con lo detectado hasta ahora. */
fun StreetFighterViewModel.stopGauntlet() {
    if (!gauntletActive) return
    gauntletQueue.clear()
    finishGauntlet()
}

/** Cierra el overlay del reporte del gauntlet. */
fun StreetFighterViewModel.dismissGauntletReport() {
    _state.update { it.copy(gauntletFinished = false) }
}



/**
 * Último índice de paso del showcase para [id]: 0..12 = moves por input, 13.. = poderes,
 * luego [showcaseExtraStates] forzados y — SOLO Yoalli Ehécatl — la metamorfosis final
 * (BONUS_POWER_10 → termina convertida en La Presidenta).
 */
internal fun StreetFighterViewModel.showcaseTotalSteps(id: SfFighterId): Int =
    12 + usableBonusPowerCount(id) + showcaseExtraStates.size +
        // 🆕 (2026-07-25) el paso extra de metamorfosis ahora es de Yoalli (→ La Presidenta)
        (if (id == SfFighterId.YOALLI_EHECATL) 1 else 0)

/**
 * Input SCRIPTED del showcase: avanza un paso cada [showcaseStepMs] y ejecuta la animación
 * correspondiente (una vez por paso). Los botones son de un tick (fireNow); las direcciones se
 * sostienen. Los pasos EXTRA no emiten input: dejan el estado en [showcaseForcedState] y el
 * tick lo aplica con [forceShowcaseState]. watchStuck detecta las animaciones que no terminan.
 */
internal fun StreetFighterViewModel.showcaseInput(now: Long, f: SfFighter): SfInput {
    val id = f.id
    if (now >= showcaseStepUntilMs) {
        showcaseStep++
        showcaseStepUntilMs = now + showcaseStepMs
    }
    val step = showcaseStep
    // 🆕 (2026-07-18k) Los pasos de UN toque (salto/golpes/specials/poderes) esperan a que
    // el peleadór esté en IDLE para disparar: p. ej. el SALTO se PERDÍA porque el input caía
    // mientras aún subía del agachado (CROUCH→CROUCH_UP) y JUMP_START no es válido desde ahí.
    // Los pasos sostenidos (0..2) y los forzados (extras) disparan de inmediato.
    val oneShotStep = step in 3..(12 + usableBonusPowerCount(id))
    val fireNow = step != showcaseFiredStep &&
        (!oneShotStep || f.state == SfFighterState.IDLE)
    if (fireNow) showcaseFiredStep = step
    return when (step) {
        0 -> SfInput(forward = true)
        1 -> SfInput(backward = true)
        2 -> SfInput(down = true)
        3 -> if (fireNow) SfInput(up = true) else SfInput()
        4 -> if (fireNow) SfInput(lightPunch = true) else SfInput()
        5 -> if (fireNow) SfInput(mediumPunch = true) else SfInput()
        6 -> if (fireNow) SfInput(heavyPunch = true) else SfInput()
        7 -> if (fireNow) SfInput(lightKick = true) else SfInput()
        8 -> if (fireNow) SfInput(mediumKick = true) else SfInput()
        9 -> if (fireNow) SfInput(heavyKick = true) else SfInput()
        10 -> if (fireNow) SfInput(special = SfAttackStrength.LIGHT) else SfInput()
        11 -> if (fireNow) SfInput(special = SfAttackStrength.MEDIUM) else SfInput()
        12 -> if (fireNow) SfInput(special = SfAttackStrength.HEAVY) else SfInput()
        else -> {
            val usable = usableBonusPowerCount(id)
            val bp = step - 12 // paso 13 → poder 1
            if (bp in 1..usable) {
                if (fireNow) SfInput(bonusPower = bp) else SfInput()
            } else {
                // 🆕 Pasos FORZADOS: giros, HURT_*, KO, VICTORY y metamorfosis (Yoalli→Presidenta)
                if (fireNow) {
                    val extraIdx = step - 13 - usable
                    showcaseForcedState = when {
                        extraIdx in showcaseExtraStates.indices -> showcaseExtraStates[extraIdx]
                        extraIdx == showcaseExtraStates.size &&
                            id == SfFighterId.YOALLI_EHECATL -> SfFighterState.BONUS_POWER_10
                        else -> null
                    }
                }
                SfInput()
            }
        }
    }
}

/**
 * 🆕 (2026-07-18j) Fuerza un estado del guion del showcase saltándose validFrom (QA de
 * assets, no gameplay). Si la animación NO existe en el JSON del peleadór, lo reporta y
 * no fuerza nada (evita el crash de animOf con getValue).
 */
internal fun StreetFighterViewModel.forceShowcaseState(sim: StreetFighterViewModel.Sim, idx: Int, st: SfFighterState, now: Long) {
    val f = sim.fighter(idx)
    if (dataFor(f).animations[st.jsKey].isNullOrEmpty()) {
        logAssetIssue("FALTA ANIM ${f.id.name}: ${st.jsKey}")
        return
    }
    var nf = f.copy(
        state = st, velocityX = 0f, velocityY = 0f,
        slideVelocity = 0f, slideFriction = 0f,
        attackStruck = false, fireballFired = false,
        y = SfConstants.STAGE_FLOOR,
    )
    nf = withAnimationFrame(nf, 0, now)
    sim.setFighter(idx, nf)
    // 🆕 (2026-07-18k) AUDIO del guion: los estados forzados NO pasan por applyAttackHit/
    // changeState, así que su sonido se emite aquí reutilizando los .ogg correctos del tema
    // (pedido del dueño: mejor repetir un audio correcto que dejar la animación muda).
    // Solo idx 0: el guion es espejo y emitir dos veces duplicaba el volumen.
    // 🆕 (2026-07-18o/p) Voz de DAÑO del pack en el showcase (bypass del cooldown, es demo).
    if (idx == 0 && st in SF_HURT_STATES) {
        emitVoiceLines(sfVoicePacks[nf.id]?.hurt.orEmpty(), now)
    }
    if (idx == 0) when (st) {
        SfFighterState.HURT_HEAD_LIGHT, SfFighterState.HURT_BODY_LIGHT ->
            _soundEvents.tryEmit("light-punch-hit")
        SfFighterState.HURT_HEAD_MEDIUM, SfFighterState.HURT_BODY_MEDIUM ->
            _soundEvents.tryEmit("medium-punch-hit")
        SfFighterState.HURT_HEAD_HEAVY, SfFighterState.HURT_BODY_HEAVY ->
            _soundEvents.tryEmit("heavy-punch-hit")
        SfFighterState.KO -> {
            _soundEvents.tryEmit("heavy-kick-hit") // golpe final (thud)
            emitLossVoice(nf.id, now) // 🆕 voz de derrota del perdedor
        }
        SfFighterState.VICTORY -> emitWinVoice(nf.id, now) // su voz al celebrar (policía = WIN)
        SfFighterState.BONUS_POWER_11 -> emitSpecialVoice(nf.id, now) // metamorfosis
        else -> Unit // giros: sin SFX (tampoco lo tienen en pelea real)
    }
}

/**
 * 🆕 AUDITORÍA ESTÁTICA por peleadór (2026-07-18j): recorre TODAS las claves de animación
 * esperadas (SfFighterState.jsKey, poderes solo hasta su bonusPowerCount) y reporta las que
 * FALTEN o estén vacías, y las que referencien frames inexistentes; además verifica la voz
 * de su special (special_<id>.ogg). Corre al armar cada peleadór del showcase.
 */
internal fun StreetFighterViewModel.auditFighterAssets(id: SfFighterId) {
    val data = runCatching { SfFrameCatalog.load(id) }.getOrElse {
        logAssetIssue("JSON ILEGIBLE ${id.name} (${id.jsonAsset}): ${it.message}")
        return
    }
    for (st in SfFighterState.entries) {
        val bonusIdx = st.bonusPowerIndex()
        if (bonusIdx != null && bonusIdx > id.bonusPowerCount) continue // poderes que no tiene
        val anim = data.animations[st.jsKey]
        if (anim.isNullOrEmpty()) {
            logAssetIssue("FALTA ANIM ${id.name}: ${st.jsKey}")
            continue
        }
        anim.forEach { fr ->
            if (fr.frameKey !in data.frames) {
                logAssetIssue("FRAME ROTO ${id.name}: ${st.jsKey} usa '${fr.frameKey}' y no existe")
            }
        }
        val visibleSteps = anim.filter { it.delay >= 0 }
        val uniqueSources = visibleSteps.mapNotNull { frame ->
            data.frames[frame.frameKey]?.src
        }.distinct()
        if (visibleSteps.size >= 3 && uniqueSources.size == 1) {
            logAssetIssue(
                "ANIM RELLENO ${id.name}: ${st.jsKey} repite una sola pose " +
                    "en ${visibleSteps.size} pasos",
            )
        }
    }
    // 🆕 (2026-07-18o/p/q) Voz del peleadór: si tiene PACK, verifica cada LÍNEA (archivo .ogg)
    // de todos sus eventos; si no, su special_<id>.ogg.
    val pack = sfVoicePacks[id]
    if (pack != null) {
        (pack.intro + pack.attack + pack.hurt + pack.power + pack.win).forEach { line ->
            if (!sfAssetExists("STREETFIGHTER/SOUNDS/${line.file}.ogg")) {
                logAssetIssue("FALTA VOZ ${line.file}.ogg (${id.name})")
            }
        }
    } else if (id !in sfVoicelessFighters &&
        !sfAssetExists("STREETFIGHTER/SOUNDS/${specialSfxKey(id)}.ogg")
    ) {
        logAssetIssue("FALTA SONIDO ${specialSfxKey(id)}.ogg (${id.name})")
    }
}

/** 🆕 Verifica los .ogg COMPARTIDOS del tema (golpes/impactos/land/hadouken) + música. */
internal fun StreetFighterViewModel.auditThemeSounds() {
    SF_CLASSIC_THEME.soundKeys.forEach { key ->
        if (!sfAssetExists("${SF_CLASSIC_THEME.soundsDir}$key.ogg")) {
            logAssetIssue("FALTA SFX DEL TEMA: $key.ogg")
        }
    }
    if (!sfAssetExists(SF_CLASSIC_THEME.soundsDir + SF_CLASSIC_THEME.musicFile)) {
        logAssetIssue("FALTA MUSICA DEL TEMA: ${SF_CLASSIC_THEME.musicFile}")
    }
    // 🆕 (2026-07-18k) Música por progresión (lobby + pistas de batalla de Prankedy)
    (listOfNotNull(SF_CLASSIC_THEME.lobbyMusic.takeIf { it.isNotBlank() }) +
        SF_CLASSIC_THEME.battleMusic).forEach { m ->
        if (!sfAssetExists(SF_CLASSIC_THEME.soundsDir + m)) {
            logAssetIssue("FALTA MUSICA (progresión): $m")
        }
    }
    // 🆕 (2026-07-18u) Grito de ataque masculino por defecto (pendiente: "ZA ZA" seg 8-10).
    if (!sfAssetExists("STREETFIGHTER/SOUNDS/$maleGruntClip.ogg")) {
        logAssetIssue("FALTA (opcional) $maleGruntClip.ogg — grito de ataque masculino por defecto")
    }
}

/** ¿Existe el asset? (open+close barato; solo se usa en auditorías puntuales). */
internal fun StreetFighterViewModel.sfAssetExists(path: String): Boolean =
    runCatching { appContext.assets.open(path).close() }.isSuccess

// ------------------------------------------------------------------
// 🆕 MODO ARCADE (escalera de 11 peleas, OFFLINE). Ver SfArcadeLadder + SfArcadeRepository.
// Todos los personajes/mapas empiezan bloqueados; se desbloquean derrotando rivales.
// ------------------------------------------------------------------

/**
 * Arranca el arcade: peleadór + dificultad **Fácil / Medio / Difícil**.
 * - Fácil (BASICA) → mapas de **día** del rival
 * - Medio (NORMAL) → mapas de **noche**
 * - Difícil (AVANZADA/PESADILLA) → **noche apocalíptica**
 * La IA base es la elegida; en jefes/final sube un escalón (cap PESADILLA).
 */

