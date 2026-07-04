package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
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
//   1 ESCONDERSE  → policías de búsqueda en la entrada; aléjate o te reconocen (misión fallida).
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

internal fun WorldMapViewModel.isMission2StoryActive(): Boolean =
    inCampaign && mission2Phase >= Mission2.PHASE_HIDE && mission2Phase <= Mission2.PHASE_BACKPACK

// ── ARRANQUE: al VOLVER al campus (mapa global) con "Ingresa a la ESCOM" cumplida ──
// Idempotente y barato (la llama el game loop en cada tick de campaña). Espera a que el REMATE
// de la persecución de la Misión 1 termine (los 6 policías entran/se van) para que la escena
// quede limpia antes de que aparezcan los policías DE BÚSQUEDA.
internal fun WorldMapViewModel.maybeStartMission2Story(playerLoc: GeoPoint) {
    if (!inCampaign || mission2Phase != Mission2.PHASE_NONE) return
    if (currentInteriorRoomId != null) return   // sigue DENTRO del interior de la ESCOM
    val s = _uiState.value
    if (s.currentObjective?.id != MissionCatalog.INGRESAR_ESCOM.id || !s.objectiveDone) return
    if (campaignEscortPolice.isActive()) return // deja terminar el remate del chase
    clearCampaignPolice()
    clearMission2Story()
    mission2Phase = Mission2.PHASE_HIDE
    setCampaignObjective(MissionCatalog.M2_ESCONDERSE_POLICIA)
    _uiState.update { it.copy(
        interactionPrompt = "🚨 ¡La policía los busca a ti y a Prankedy! Aléjate de la entrada"
    ) }
    android.util.Log.d("POW_DBG", "MISIÓN 2: arranca (fase ESCONDERSE)")
}

// ── TICK PRINCIPAL (lo llama el game loop MIEMBRO cuando isMission2StoryActive()) ──
internal fun WorldMapViewModel.runMission2StoryTick(playerLoc: GeoPoint) {
    if (currentInteriorRoomId != null) return   // dentro de un interior: la fase 5 corre allá
    val now = System.currentTimeMillis()
    when (mission2Phase) {
        Mission2.PHASE_HIDE -> tickM2Hide(playerLoc, now)
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
    _uiState.update { it.copy(
        objectiveDone = true,
        interactionPrompt = "✅ Objetivo cumplido: ${getLocalizedString(obj.titleRes)}"
    ) }
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
        }
    }
    android.util.Log.d("POW_DBG", "MISIÓN 2: avanza a fase $mission2Phase")
}

// ── FASE 1 · ESCONDERSE: policías de búsqueda patrullando la entrada ──
private fun WorldMapViewModel.tickM2Hide(playerLoc: GeoPoint, now: Long) {
    if (m2MaybeAdvancePhase(now)) return

    // Spawn perezoso e idempotente (también re-arma la fase tras CARGAR una partida).
    if (mission2Npcs.isEmpty()) {
        for (i in 0 until Mission2.HIDE_COP_COUNT) {
            val ang = 2.0 * Math.PI * i / Mission2.HIDE_COP_COUNT
            val id = "M2_SEARCH_COP_$i"
            mission2Npcs[id] = Npc(
                id = id,
                type = NpcType.POLICE_COP,
                location = GeoPoint(
                    Mission2.POLICE_SEARCH_LAT + sin(ang) * 0.00008,
                    Mission2.POLICE_SEARCH_LON + cos(ang) * 0.00008
                ),
                speed = Mission2.HIDE_COP_SPEED,
                isRemote = false,
                isMoving = true,
                policeDisembarked = true,
                policeCanShoot = false
            )
        }
    }

    // Patrulla SIN estado extra: cada policía camina hacia un waypoint pseudoaleatorio DETERMINISTA
    // por "cubeta" de tiempo (~6 s), alrededor de la entrada. Beeline: el campus es zona libre.
    val bucket = now / 6000L
    var nearest = Double.MAX_VALUE
    for (cop in mission2Npcs.values.toList()) {
        if (!cop.id.startsWith("M2_SEARCH_COP_")) continue
        val rnd = java.util.Random(cop.id.hashCode() * 31L + bucket)
        val ang = rnd.nextDouble() * 2.0 * Math.PI
        val radius = Mission2.HIDE_PATROL_DEG * (0.25 + 0.75 * rnd.nextDouble())
        val tLat = Mission2.POLICE_SEARCH_LAT + sin(ang) * radius
        val tLon = Mission2.POLICE_SEARCH_LON + cos(ang) * radius
        val a = atan2(tLat - cop.location.latitude, tLon - cop.location.longitude)
        val step = Mission2.HIDE_COP_SPEED
        val moved = GeoPoint(cop.location.latitude + sin(a) * step, cop.location.longitude + cos(a) * step)
        val arrived = m2Dist(cop.location.latitude, cop.location.longitude, tLat, tLon) < 0.00005
        mission2Npcs[cop.id] = cop.copy(
            location = if (arrived) cop.location else moved,
            isMoving = !arrived,
            facingRight = cos(a) >= 0
        )
        val d = m2Dist(playerLoc.latitude, playerLoc.longitude, moved.latitude, moved.longitude)
        if (d < nearest) nearest = d
    }

    // ¿Te van a RECONOCER? (muy cerca demasiado tiempo → MISIÓN FALLIDA).
    if (nearest < Mission2.HIDE_DETECT_DEG) {
        if (mission2DetectSinceMs == 0L) mission2DetectSinceMs = now
        _uiState.update { it.copy(interactionPrompt = "⚠️ ¡Te van a reconocer! ALÉJATE de la policía") }
        if (now - mission2DetectSinceMs > Mission2.HIDE_DETECT_MS) {
            android.util.Log.d("POW_DBG", "MISIÓN 2: la policía te RECONOCIÓ → misión fallida")
            mission2Npcs.clear()
            _uiState.update { it.copy(showMissionFailed = true) }
            return
        }
    } else {
        mission2DetectSinceMs = 0L
    }

    // ¿Los PERDISTE? (todos lejos, sostenido).
    if (nearest > Mission2.HIDE_SAFE_DEG) {
        if (mission2SafeSinceMs == 0L) mission2SafeSinceMs = now
        if (now - mission2SafeSinceMs > Mission2.HIDE_SAFE_MS) {
            mission2Npcs.clear()   // los policías se rinden y se retiran
            m2MarkObjectiveDone(now)
        }
    } else {
        mission2SafeSinceMs = 0L
    }
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
                // un zombi CON visualConfig se dibuja como humano en los 3 renderers).
                mission2Npcs["M2_ZOMBIE"]?.let {
                    mission2Npcs["M2_ZOMBIE"] = it.copy(
                        type = NpcType.ZOMBIE, visualConfig = null,
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
                        npc.location.latitude + sin(a) * Mission2.HIDE_COP_SPEED * 1.6,
                        npc.location.longitude + cos(a) * Mission2.HIDE_COP_SPEED * 1.6
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

// ── FASE 5 · MOCHILA: la completa el INTERIOR (salón) vía este callback (AppNavGraph) ──
fun WorldMapViewModel.completeMission2Backpack() {
    if (mission2Phase != Mission2.PHASE_BACKPACK) return
    mission2Phase = Mission2.PHASE_DONE
    _uiState.update { it.copy(
        currentObjective = MissionCatalog.M2_RECUPERAR_MOCHILA,
        objectiveDone = true,
        interactionPrompt = "🎒 ¡MISIÓN 2 COMPLETADA! Recuperaste la mochila de Prankedy"
    ) }
    soundManager.playMisionCumplida()
    android.util.Log.d("POW_DBG", "MISIÓN 2: COMPLETADA (mochila recuperada)")
}

// ── LIMPIEZA (idempotente). NO toca mission2Phase: eso lo decide quien llama
//    (setStorySpawn la resetea; el retry re-arma vía maybeStartMission2Story). ──
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
