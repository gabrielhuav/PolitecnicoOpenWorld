package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ovh.gabrielhuav.pow.domain.models.geo.GeoPoint
import ovh.gabrielhuav.pow.domain.models.campaign.MissionCatalog
import ovh.gabrielhuav.pow.domain.models.campaign.mission2.Mission2
import ovh.gabrielhuav.pow.domain.models.map.CharacterVisualConfig
import ovh.gabrielhuav.pow.domain.models.map.Npc
import ovh.gabrielhuav.pow.domain.models.map.NpcType
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// ─────────────────────────────────────────────────────────────────────────────
// MODO HISTORIA · MISIÓN 2 "El rumor" (extensiones del WorldMapViewModel, sin gemelo miembro).
//
// Máquina de FASES sobre el CAMPUS de la ESCOM (zona libre del mapa global):
//   1 ESCONDERSE  → se juega DENTRO del lobby de la ESCOM (motor de interiores): policías
//                   patrullan la sala; evítalos hasta que se rindan. La completa/falla el
//                   interior vía completeMission2Hide()/failMission2Hide() (AppNavGraph).
//                   Aquí el tick NO hace nada (el 🎯 exterior te guía a ENTRAR a la ESCOM).
//   2 RUMOR       → 2 estudiantes platican el rumor zombie; quédate a escucharlo COMPLETO.
//   3 BROTE       → un NPC se CONVIERTE y ataca; la policía lo somete, la radio pide refuerzos
//                   en la ENCB y se lo llevan.
//   4 PLÁTICA     → Prankedy (estático) te cuenta lo del REY GRUPERO y la mochila del salón.
//   5 MOCHILA     → en el INTERIOR (salón escom_salon_m2): lata apestosa + mochila. La completa
//                   completeMission2Backpack() (callback desde AppNavGraph/ZombieGameScreen).
//
// ESTADO: la fase vive en el VM (mission2Phase, PERSISTIDA en GameSaveData.mission2Phase); los
// NPCs de misión en mission2Npcs (se fusionan en uiState.npcs vía updateNpcsState, como la
// multitud del chase). Los ticks son IDEMPOTENTES: si los actores de la fase no existen (p. ej.
// tras CARGAR partida), se re-spawnean solos. Guion y constantes: mission2/Mission2.kt.
// ─────────────────────────────────────────────────────────────────────────────

// Distancia euclidiana simple en grados (suficiente a escala de campus).
private fun m2Dist(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
    val dLat = aLat - bLat
    val dLon = aLon - bLon
    return sqrt(dLat * dLat + dLon * dLon)
}

// ACTIVA = la fase corre Y el jugador la está SIGUIENDO (objetivo m2_* activo). Si dejas de
// seguirla desde el registro de misiones, el tick se PAUSA (los actores se limpian en el game
// loop) y se reanuda al volver a seguirla (resumeMission2Objective re-fija el objetivo de la fase).
internal fun WorldMapViewModel.isMission2StoryActive(): Boolean =
    inCampaign && mission2Phase >= Mission2.PHASE_HIDE && mission2Phase <= Mission2.PHASE_BACKPACK &&
        _uiState.value.currentObjective?.id?.startsWith(Mission2.OBJECTIVE_ID_PREFIX) == true

// ── ARRANQUE (desde el REGISTRO DE MISIONES): fija la fase 1 y su objetivo. ──
// Requiere la Misión 1 completada (lo valida selectCampaignMission). La fase 1 se juega DENTRO
// del lobby de la ESCOM: si estás fuera, el 🎯 (puerta de la ESCOM) te guía a ENTRAR; si la
// sigues desde el propio lobby, los policías spawnean ahí mismo (AppNavGraph → mission2Hide).
internal fun WorldMapViewModel.startMission2Story() {
    clearCampaignPolice()
    clearMission2Story()
    mission2Phase = Mission2.PHASE_HIDE
    setCampaignObjective(MissionCatalog.M2_ESCONDERSE_POLICIA)
    _uiState.update { it.copy(
        interactionPrompt = "🚨 ¡La policía entró a la ESCOM a buscarlos! Escóndete DENTRO del lobby"
    ) }
    android.util.Log.d("POW_DBG", "MISIÓN 2: seguida desde el registro (fase ESCONDERSE, en el lobby)")
}

// ── REANUDAR (registro de misiones): re-fija el objetivo de la FASE actual (no reinicia). ──
internal fun WorldMapViewModel.resumeMission2Objective() {
    val obj = when (mission2Phase) {
        Mission2.PHASE_HIDE -> MissionCatalog.M2_ESCONDERSE_POLICIA
        Mission2.PHASE_RUMOR -> MissionCatalog.M2_PISTA_RUMOR
        Mission2.PHASE_BROTE -> MissionCatalog.M2_PISTA_BROTE
        Mission2.PHASE_TALK -> MissionCatalog.M2_HABLAR_PRANKEDY
        Mission2.PHASE_BACKPACK -> MissionCatalog.M2_RECUPERAR_MOCHILA
        else -> return
    }
    setCampaignObjective(obj)
}

// ── TICK PRINCIPAL (lo llama el game loop MIEMBRO cuando isMission2StoryActive()) ──
internal fun WorldMapViewModel.runMission2StoryTick(playerLoc: GeoPoint) {
    if (currentInteriorRoomId != null) return   // dentro de un interior: las fases 1 y 5 corren allá
    val now = System.currentTimeMillis()
    when (mission2Phase) {
        Mission2.PHASE_HIDE -> { /* se juega DENTRO del lobby (completeMission2Hide/failMission2Hide) */ }
        Mission2.PHASE_RUMOR -> tickM2Rumor(playerLoc, now)
        Mission2.PHASE_BROTE -> tickM2Brote(playerLoc, now)
        Mission2.PHASE_TALK -> tickM2Talk(playerLoc, now)
        Mission2.PHASE_BACKPACK -> { /* la acción ocurre en el salón (interior) */ }
    }
    updateNpcsState()
}

// Marca el objetivo de la fase actual como CUMPLIDO (jingle + aviso) y programa la transición.
private fun WorldMapViewModel.m2MarkObjectiveDone(now: Long) {
    val obj = _uiState.value.currentObjective ?: return
    _uiState.update { it.copy(objectiveDone = true) }
    avisarConTitulo("✅ Objetivo cumplido: ", obj.titleRes)
    soundManager.playMisionCumplida()
    mission2PhaseTransitionMs = now
}

// ¿Toca avanzar de fase? (pausa breve tras el "cumplido" para que respire la escena).
private fun WorldMapViewModel.m2MaybeAdvancePhase(now: Long): Boolean {
    if (!_uiState.value.objectiveDone) return false
    if (now - mission2PhaseTransitionMs >= Mission2.PHASE_TRANSITION_MS) advanceM2Phase()
    return true
}

private fun WorldMapViewModel.advanceM2Phase() {
    mission2Npcs.clear()
    mission2ConvoIndex = 0; mission2ConvoNextMs = 0L
    mission2EventStage = 0; mission2EventStageMs = 0L
    mission2DetectSinceMs = 0L; mission2SafeSinceMs = 0L
    m2ClearConvo()
    when (mission2Phase) {
        Mission2.PHASE_HIDE -> {
            mission2Phase = Mission2.PHASE_RUMOR
            setCampaignObjective(MissionCatalog.M2_PISTA_RUMOR)
        }
        Mission2.PHASE_RUMOR -> {
            mission2Phase = Mission2.PHASE_BROTE
            setCampaignObjective(MissionCatalog.M2_PISTA_BROTE)
        }
        Mission2.PHASE_BROTE -> {
            mission2Phase = Mission2.PHASE_TALK
            setCampaignObjective(MissionCatalog.M2_HABLAR_PRANKEDY)
            spawnM2Prankedy()
        }
        Mission2.PHASE_TALK -> {
            mission2Phase = Mission2.PHASE_BACKPACK
            setCampaignObjective(MissionCatalog.M2_RECUPERAR_MOCHILA)
            // 🥫 Prankedy te DA la lata apestosa: va al INVENTARIO (slot 2) y se ve con su asset;
            // se consume al lanzarla en el salón. Idempotente (replay/TP no la duplican).
            grantMission2StinkCan()
            // Guía explícita + CÓMIC "La mochila" (AppNavGraph lo reproduce al ver la bandera): sin
            // esto no queda claro que hay que ENTRAR a la ESCOM (lobby → Edificio Principal → salón)
            // y usar la lata apestosa.
            _uiState.update { it.copy(
                interactionPrompt = "🎒 Ve a la ESCOM: la mochila está en un salón EN CLASES del Edificio Principal. Lanza la LATA APESTOSA (X) para vaciarlo.",
                pendingMission2BackpackComic = true
            ) }
        }
    }
    android.util.Log.d("POW_DBG", "MISIÓN 2: avanza a fase $mission2Phase")
}

// ── MODO DEV · "TP al objetivo" (2026-07-13b): CUMPLE la fase ACTUAL y arma la SIGUIENTE. ──
// Petición del dueño: cada TP debe completar el objetivo en curso (p. ej. perder a la policía)
// y llevarte al punto de la siguiente fase. Reusa advanceM2Phase (limpia actores, fija el
// objetivo siguiente, spawnea a Prankedy en TALK, prompt de la mochila en BACKPACK). La última
// fase (MOCHILA) NO se completa desde aquí: la misión NUNCA se completa por TP (bug 2026-07-12).
internal fun WorldMapViewModel.devCompleteMission2Phase() {
    if (mission2Phase < Mission2.PHASE_HIDE || mission2Phase > Mission2.PHASE_TALK) return
    advanceM2Phase()
    // El cómic de la mochila (TALK→BACKPACK) navegaría al visor y CHOCARÍA con la navegación del
    // propio TP (devTpRoute); se consume aquí (el cómic se ve jugando la plática normal).
    if (_uiState.value.pendingMission2BackpackComic) consumeMission2BackpackComic()
    soundManager.playMisionCumplida()
    android.util.Log.d("POW_DBG", "MISIÓN 2 (dev TP): fase cumplida → $mission2Phase")
}

// ── FASE 1 · ESCONDERSE (INTERIOR): la juega el motor de interiores en el lobby de la ESCOM
//    (policías con skin POLICIA_CDMX patrullando la sala; ver ZombieAmbientNpcs/ZombieGameTick).
//    El interior avisa el desenlace por callback (AppNavGraph) a estas dos funciones. ──

/** El jugador AGUANTÓ la búsqueda en el lobby: los policías se rindieron → avanza a la fase 2. */
fun WorldMapViewModel.completeMission2Hide() {
    if (mission2Phase != Mission2.PHASE_HIDE) return
    mission2Phase = Mission2.PHASE_RUMOR
    clearMission2Story()
    setCampaignObjective(MissionCatalog.M2_PISTA_RUMOR)
    _uiState.update { it.copy(
        interactionPrompt = "✅ ¡Los perdiste! Busca a los estudiantes platicando en el lobby para escuchar el rumor"
    ) }
    soundManager.playMisionCumplida()
    android.util.Log.d("POW_DBG", "MISIÓN 2: fase ESCONDERSE cumplida (lobby) → RUMOR")
}

/** La conversación del rumor en el lobby de la ESCOM terminó → avanza a la fase 3 (brote). */
fun WorldMapViewModel.completeMission2Rumor() {
    if (mission2Phase != Mission2.PHASE_RUMOR) return
    mission2Phase = Mission2.PHASE_BROTE
    clearMission2Story()
    setCampaignObjective(MissionCatalog.M2_PISTA_BROTE)
    _uiState.update { it.copy(
        interactionPrompt = "✅ ¡Rumor escuchado! Sal al campus a investigar el brote"
    ) }
    soundManager.playMisionCumplida()
    android.util.Log.d("POW_DBG", "MISIÓN 2: fase RUMOR cumplida (lobby) → BROTE")
}

/** Un policía te RECONOCIÓ dentro del lobby → MISIÓN FALLIDA (REINTENTAR re-arma desde fase 1). */
fun WorldMapViewModel.failMission2Hide() {
    if (mission2Phase != Mission2.PHASE_HIDE) return
    clearMission2Story()
    _uiState.update { it.copy(showMissionFailed = true) }
    android.util.Log.d("POW_DBG", "MISIÓN 2: la policía te RECONOCIÓ en el lobby → misión fallida")
}

// ── FASE 2 · RUMOR: 2 estudiantes platican; quédate cerca hasta oírlo COMPLETO ──
private fun WorldMapViewModel.tickM2Rumor(playerLoc: GeoPoint, now: Long) {
    if (m2MaybeAdvancePhase(now)) return

    if (mission2Npcs.isEmpty()) {
        // Frente a frente, separados ~4 m. isMoving=false → animación idle.
        mission2Npcs["M2_RUMOR_A"] = Npc(
            id = "M2_RUMOR_A", type = NpcType.PERSON,
            location = GeoPoint(Mission2.RUMOR_LAT, Mission2.RUMOR_LON - 0.00002),
            speed = 0.0, isRemote = false, isMoving = false,
            facingRight = true, visualConfig = m2RandomCivilVisual()
        )
        mission2Npcs["M2_RUMOR_B"] = Npc(
            id = "M2_RUMOR_B", type = NpcType.PERSON,
            location = GeoPoint(Mission2.RUMOR_LAT, Mission2.RUMOR_LON + 0.00002),
            speed = 0.0, isRemote = false, isMoving = false,
            facingRight = false, visualConfig = m2RandomCivilVisual()
        )
    }

    // Se les ve PLATICANDO desde lejos (burbuja 💬 alternada, sin subtítulos): así el jugador
    // identifica la escena del rumor al acercarse al 🎯. El subtítulo solo corre DENTRO del radio.
    run {
        val visualTalker = if ((now / Mission2.CONVO_LINE_MS) % 2L == 0L) "M2_RUMOR_A" else "M2_RUMOR_B"
        mission2Npcs[visualTalker]?.let {
            if (it.talkingUntil < now + 400) mission2Npcs[visualTalker] = it.copy(talkingUntil = now + 900)
        }
    }

    val dist = m2Dist(playerLoc.latitude, playerLoc.longitude, Mission2.RUMOR_LAT, Mission2.RUMOR_LON)
    if (dist <= Mission2.LISTEN_DEG) {
        if (mission2ConvoNextMs == 0L || now >= mission2ConvoNextMs) {
            if (mission2ConvoIndex >= Mission2.RUMOR_LINES.size) {
                // Escuchaste el rumor COMPLETO → pista registrada.
                m2ClearConvo()
                m2MarkObjectiveDone(now)
                return
            }
            val (speaker, text) = Mission2.RUMOR_LINES[mission2ConvoIndex]
            _uiState.update { it.copy(storyConvoSpeaker = speaker, storyConvoText = text) }
            // Burbuja 💬 sobre el que habla (A = líneas pares, B = impares).
            val talkingId = if (mission2ConvoIndex % 2 == 0) "M2_RUMOR_A" else "M2_RUMOR_B"
            mission2Npcs[talkingId]?.let {
                mission2Npcs[talkingId] = it.copy(talkingUntil = now + Mission2.CONVO_LINE_MS)
            }
            mission2ConvoIndex++
            mission2ConvoNextMs = now + Mission2.CONVO_LINE_MS
        }
    } else {
        // Te alejaste: el rumor se PAUSA (se retoma en la línea donde iba al volver).
        if (_uiState.value.storyConvoText != null) m2ClearConvo()
        mission2ConvoNextMs = 0L
    }
}

// ── FASE 3 · BROTE: conversión pública + policía que somete + radio + se lo llevan ──
private fun WorldMapViewModel.tickM2Brote(playerLoc: GeoPoint, now: Long) {
    if (m2MaybeAdvancePhase(now)) return

    // Actores base (idempotente): 3 civiles + el "por convertirse" (aún se ve normal).
    if (mission2Npcs.isEmpty()) {
        mission2EventStage = 0
        for (i in 0 until 3) {
            val ang = 2.0 * Math.PI * i / 3.0
            mission2Npcs["M2_CIV_$i"] = Npc(
                id = "M2_CIV_$i", type = NpcType.PERSON,
                location = GeoPoint(
                    Mission2.BROTE_LAT + sin(ang) * 0.00006,
                    Mission2.BROTE_LON + cos(ang) * 0.00006
                ),
                speed = 0.0, isRemote = false, isMoving = false,
                visualConfig = m2RandomCivilVisual()
            )
        }
        mission2Npcs["M2_ZOMBIE"] = Npc(
            id = "M2_ZOMBIE", type = NpcType.PERSON,
            location = GeoPoint(Mission2.BROTE_LAT, Mission2.BROTE_LON),
            speed = 0.0, isRemote = false, isMoving = false,
            visualConfig = m2RandomCivilVisual()
        )
    }

    when (mission2EventStage) {
        // Etapa 0: ARMADO — espera a que el jugador se acerque a la escena.
        0 -> {
            val d = m2Dist(playerLoc.latitude, playerLoc.longitude, Mission2.BROTE_LAT, Mission2.BROTE_LON)
            if (d <= Mission2.BROTE_TRIGGER_DEG) {
                // CONVERSIÓN: el NPC se vuelve ZOMBIE. ⚠️ visualConfig = null (gotcha de render:
                // un zombi CON visualConfig se dibuja como humano en los 3 renderers). Usa el arte
                // propio del "estudiante zombi" (SPRITES/ZOMBIE/ESTUDIANTE) en vez del genérico.
                mission2Npcs["M2_ZOMBIE"]?.let {
                    mission2Npcs["M2_ZOMBIE"] = it.copy(
                        type = NpcType.ZOMBIE, visualConfig = null,
                        zombieSpriteSet = "SPRITES/ZOMBIE/ESTUDIANTE",
                        speed = Mission2.BROTE_ZOMBIE_SPEED, isMoving = true
                    )
                }
                _uiState.update { it.copy(
                    storyConvoSpeaker = Mission2.BROTE_SCREAM_SPEAKER,
                    storyConvoText = Mission2.BROTE_SCREAM_TEXT
                ) }
                mission2EventStage = 1
                mission2EventStageMs = now
            }
        }
        // Etapa 1: ATAQUE — el zombie persigue civiles; los civiles HUYEN.
        1 -> {
            m2ZombieChaseAndCivsFlee(now)
            if (now - mission2EventStageMs >= Mission2.BROTE_ATTACK_MS) {
                // Llega la policía (desde la entrada del campus, corriendo).
                for (i in 1..2) {
                    val id = "M2_EVENT_COP_$i"
                    mission2Npcs[id] = Npc(
                        id = id, type = NpcType.POLICE_COP,
                        location = GeoPoint(
                            Mission2.POLICE_SEARCH_LAT + (i - 1) * 0.00004,
                            Mission2.POLICE_SEARCH_LON + (i - 1) * 0.00004
                        ),
                        speed = Mission2.BROTE_COP_SPEED, isRemote = false, isMoving = true,
                        policeDisembarked = true, policeCanShoot = false
                    )
                }
                m2ClearConvo()
                mission2EventStage = 2
                mission2EventStageMs = now
            }
        }
        // Etapa 2: la policía CORRE hacia el zombie y lo SOMETE al alcanzarlo.
        2 -> {
            m2ZombieChaseAndCivsFlee(now)
            val zombie = mission2Npcs["M2_ZOMBIE"] ?: run { mission2EventStage = 4; return }
            var allClose = true
            for (i in 1..2) {
                val cop = mission2Npcs["M2_EVENT_COP_$i"] ?: continue
                val d = m2Dist(cop.location.latitude, cop.location.longitude,
                    zombie.location.latitude, zombie.location.longitude)
                if (d > Mission2.BROTE_SUBDUE_DEG) {
                    allClose = false
                    val a = atan2(zombie.location.latitude - cop.location.latitude,
                        zombie.location.longitude - cop.location.longitude)
                    mission2Npcs[cop.id] = cop.copy(
                        location = GeoPoint(
                            cop.location.latitude + sin(a) * Mission2.BROTE_COP_SPEED,
                            cop.location.longitude + cos(a) * Mission2.BROTE_COP_SPEED
                        ),
                        isMoving = true, facingRight = cos(a) >= 0
                    )
                } else if (cop.isMoving) {
                    mission2Npcs[cop.id] = cop.copy(isMoving = false)
                }
            }
            if (allClose) {
                // SOMETIDO: el zombie deja de moverse; los policías "hablan por radio" (burbuja).
                mission2Npcs["M2_ZOMBIE"] = zombie.copy(isMoving = false, speed = 0.0)
                for (i in 1..2) mission2Npcs["M2_EVENT_COP_$i"]?.let {
                    mission2Npcs[it.id] = it.copy(isMoving = false,
                        talkingUntil = now + Mission2.RADIO_LINES.size * Mission2.CONVO_LINE_MS)
                }
                mission2ConvoIndex = 0
                mission2ConvoNextMs = 0L
                mission2EventStage = 3
                mission2EventStageMs = now
            }
        }
        // Etapa 3: RADIO — se oyen las líneas ("refuerzos en la ENCB… TODO el personal").
        3 -> {
            if (mission2ConvoNextMs == 0L || now >= mission2ConvoNextMs) {
                if (mission2ConvoIndex >= Mission2.RADIO_LINES.size) {
                    m2ClearConvo()
                    mission2EventStage = 4
                    mission2EventStageMs = now
                } else {
                    val (speaker, text) = Mission2.RADIO_LINES[mission2ConvoIndex]
                    _uiState.update { it.copy(storyConvoSpeaker = speaker, storyConvoText = text) }
                    mission2ConvoIndex++
                    mission2ConvoNextMs = now + Mission2.CONVO_LINE_MS
                }
            }
        }
        // Etapa 4: SE LO LLEVAN — policías + zombie caminan a la entrada y desaparecen.
        4 -> {
            var anyLeft = false
            for (id in listOf("M2_EVENT_COP_1", "M2_EVENT_COP_2", "M2_ZOMBIE")) {
                val npc = mission2Npcs[id] ?: continue
                val d = m2Dist(npc.location.latitude, npc.location.longitude,
                    Mission2.POLICE_SEARCH_LAT, Mission2.POLICE_SEARCH_LON)
                if (d <= 0.0001) { mission2Npcs.remove(id); continue }
                anyLeft = true
                val a = atan2(Mission2.POLICE_SEARCH_LAT - npc.location.latitude,
                    Mission2.POLICE_SEARCH_LON - npc.location.longitude)
                mission2Npcs[id] = npc.copy(
                    location = GeoPoint(
                        npc.location.latitude + sin(a) * Mission2.BROTE_COP_SPEED * 1.6,
                        npc.location.longitude + cos(a) * Mission2.BROTE_COP_SPEED * 1.6
                    ),
                    isMoving = true, facingRight = cos(a) >= 0
                )
            }
            // Tope de seguridad: aunque alguno quede atorado, la escena cierra a los ~12 s.
            if (!anyLeft || now - mission2EventStageMs > 12_000L) {
                mission2Npcs.clear()   // también los civiles que quedaran huyendo
                m2MarkObjectiveDone(now)
            }
        }
    }
}

// El zombie persigue al civil MÁS CERCANO; los civiles huyen de él (beeline, zona libre).
private fun WorldMapViewModel.m2ZombieChaseAndCivsFlee(now: Long) {
    val zombie = mission2Npcs["M2_ZOMBIE"] ?: return
    var targetCiv: Npc? = null
    var best = Double.MAX_VALUE
    for (npc in mission2Npcs.values) {
        if (!npc.id.startsWith("M2_CIV_")) continue
        val d = m2Dist(zombie.location.latitude, zombie.location.longitude,
            npc.location.latitude, npc.location.longitude)
        if (d < best) { best = d; targetCiv = npc }
    }
    // Zombie → civil más cercano (si no queda ninguno, se queda gruñendo en el lugar).
    if (targetCiv != null && best > 0.00003) {
        val a = atan2(targetCiv.location.latitude - zombie.location.latitude,
            targetCiv.location.longitude - zombie.location.longitude)
        mission2Npcs["M2_ZOMBIE"] = zombie.copy(
            location = GeoPoint(
                zombie.location.latitude + sin(a) * Mission2.BROTE_ZOMBIE_SPEED,
                zombie.location.longitude + cos(a) * Mission2.BROTE_ZOMBIE_SPEED
            ),
            isMoving = true, facingRight = cos(a) >= 0
        )
    }
    // Civiles: huyen del zombie (los muy lejanos se despawnean — salieron de la escena).
    for (civ in mission2Npcs.values.toList()) {
        if (!civ.id.startsWith("M2_CIV_")) continue
        val dLat = civ.location.latitude - zombie.location.latitude
        val dLon = civ.location.longitude - zombie.location.longitude
        val d = sqrt(dLat * dLat + dLon * dLon)
        if (d > 0.0009) { mission2Npcs.remove(civ.id); continue }
        val a = if (d > 1e-9) atan2(dLat, dLon) else Math.random() * 2.0 * Math.PI
        mission2Npcs[civ.id] = civ.copy(
            location = GeoPoint(
                civ.location.latitude + sin(a) * Mission2.BROTE_CIV_FLEE_SPEED,
                civ.location.longitude + cos(a) * Mission2.BROTE_CIV_FLEE_SPEED
            ),
            isMoving = true, facingRight = cos(a) >= 0
        )
    }
}

// ── FASE 4 · PLÁTICA con Prankedy (estático; el game loop NO corre runPrankedyTick aquí) ──
private fun WorldMapViewModel.spawnM2Prankedy() {
    val loc = GeoPoint(Mission2.PRANKEDY_LAT, Mission2.PRANKEDY_LON)
    prankedyCompanionActivated = true
    prankedyManager.spawnCompanion(loc, roadNetwork)
    prankedyManager.warpTo(loc)
    _uiState.update { it.copy(
        prankedyEnabled = true,
        prankedyLocation = prankedyManager.location,
        prankedyVisible = true,
        prankedyHealth = prankedyManager.health,
        prankedyPhase = prankedyManager.phase,
        prankedyAnimState = prankedyManager.animState
    ) }
}

private fun WorldMapViewModel.tickM2Talk(playerLoc: GeoPoint, now: Long) {
    if (m2MaybeAdvancePhase(now)) return

    // Re-arma a Prankedy si no está (p. ej. tras CARGAR una partida en esta fase).
    if (prankedyManager.location == null) spawnM2Prankedy()
    val pk = prankedyManager.location ?: return

    val dist = m2Dist(playerLoc.latitude, playerLoc.longitude, pk.latitude, pk.longitude)
    if (dist <= Mission2.TALK_DEG) {
        if (mission2ConvoNextMs == 0L || now >= mission2ConvoNextMs) {
            if (mission2ConvoIndex >= Mission2.PRANKEDY_LINES.size) {
                // Plática terminada: Prankedy se despide y se ESCONDE (deja de renderizarse).
                m2ClearConvo()
                _uiState.update { it.copy(prankedyDialogue = "¡Suerte! Yo me escondo por aquí…") }
                viewModelScope.launch {
                    kotlinx.coroutines.delay(2000)
                    prankedyManager.deactivate()
                    _uiState.update { it.copy(
                        prankedyEnabled = false, prankedyVisible = false,
                        prankedyLocation = null, prankedyDialogue = null
                    ) }
                }
                m2MarkObjectiveDone(now)
                return
            }
            val (speaker, text) = Mission2.PRANKEDY_LINES[mission2ConvoIndex]
            _uiState.update { it.copy(storyConvoSpeaker = speaker, storyConvoText = text) }
            mission2ConvoIndex++
            mission2ConvoNextMs = now + Mission2.CONVO_LINE_MS
        }
    } else {
        if (_uiState.value.storyConvoText != null) m2ClearConvo()
        mission2ConvoNextMs = 0L
    }
}

// La LATA APESTOSA de Prankedy como ítem de inventario (idempotente). El VM de interiores la
// consume al lanzarla (onInteract) y el cambio regresa vía onInteriorProgress.
internal fun WorldMapViewModel.grantMission2StinkCan() {
    val kd = ovh.gabrielhuav.pow.domain.models.zombie.KeyDrop
    val entry = kd.inventoryEntry(kd.MISSION_2, kd.M2_STINK_CAN)
    if (entry !in currentInteriorInventory) {
        currentInteriorInventory = currentInteriorInventory + entry
    }
}

// ── FASE 5 · MOCHILA: la completa el INTERIOR (salón) vía este callback (AppNavGraph) ──
fun WorldMapViewModel.completeMission2Backpack() {
    if (mission2Phase != Mission2.PHASE_BACKPACK) return
    mission2Phase = Mission2.PHASE_DONE
    markMissionCompleted(MissionCatalog.MISSION_2_ID)
    _uiState.update { it.copy(
        currentObjective = MissionCatalog.M2_RECUPERAR_MOCHILA,
        objectiveDone = true,
        interactionPrompt = "🎒 ¡MISIÓN 2 COMPLETADA! Nueva misión disponible en Opciones → Misiones",
        // CÓMIC "Regreso a la ENCB": AppNavGraph lo reproduce al volver al mapa (arranca la M3).
        pendingMission3IntroComic = true
    ) }
    soundManager.playMisionCumplida()
    android.util.Log.d("POW_DBG", "MISIÓN 2: COMPLETADA (mochila recuperada)")
}

// Consumo de las banderas de cómic (AppNavGraph las apaga tras navegar al visor).
internal fun WorldMapViewModel.consumeMission2BackpackComic() {
    _uiState.update { it.copy(pendingMission2BackpackComic = false) }
}

internal fun WorldMapViewModel.consumeMission3IntroComic() {
    _uiState.update { it.copy(pendingMission3IntroComic = false) }
}

// ── LIMPIEZA (idempotente). NO toca mission2Phase: eso lo decide quien llama
//    (setStorySpawn la resetea; el retry re-arma vía startMission2Story). ──
internal fun WorldMapViewModel.clearMission2Story() {
    mission2Npcs.clear()
    mission2DetectSinceMs = 0L
    mission2SafeSinceMs = 0L
    mission2ConvoIndex = 0
    mission2ConvoNextMs = 0L
    mission2EventStage = 0
    mission2EventStageMs = 0L
    mission2PhaseTransitionMs = 0L
    if (_uiState.value.storyConvoText != null || _uiState.value.storyConvoSpeaker != null) m2ClearConvo()
}

private fun WorldMapViewModel.m2ClearConvo() {
    _uiState.update { it.copy(storyConvoSpeaker = null, storyConvoText = null) }
}

// Look civil aleatorio (mismo estilo que la multitud del chase; copia local porque el original
// es private de WorldMapCampaignPolice.kt).
private fun m2RandomCivilVisual(): CharacterVisualConfig {
    val hairColors = listOf(
        androidx.compose.ui.graphics.Color.Black,
        androidx.compose.ui.graphics.Color.DarkGray,
        androidx.compose.ui.graphics.Color(0xFF8B4513),
        androidx.compose.ui.graphics.Color(0xFFDAA520)
    )
    val shirtColors = listOf(
        androidx.compose.ui.graphics.Color.White, androidx.compose.ui.graphics.Color.Red,
        androidx.compose.ui.graphics.Color.Blue, androidx.compose.ui.graphics.Color.Green,
        androidx.compose.ui.graphics.Color(0xFF9C27B0), androidx.compose.ui.graphics.Color(0xFFFF9800)
    )
    return CharacterVisualConfig(
        bodyFolder = "npc_walk_1",
        bodyPrefix = "npc_walk_1_",
        hairId = (1..5).random(),
        hairColor = hairColors.random(),
        shirtColor = shirtColors.random(),
        pantsColor = androidx.compose.ui.graphics.Color(0xFF37474F)
    )
}
