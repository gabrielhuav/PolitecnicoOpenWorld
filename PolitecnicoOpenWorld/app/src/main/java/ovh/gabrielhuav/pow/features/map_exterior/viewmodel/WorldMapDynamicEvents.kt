package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import kotlinx.coroutines.flow.update
import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.map.CharacterVisualConfig
import ovh.gabrielhuav.pow.domain.models.map.Npc
import ovh.gabrielhuav.pow.domain.models.map.NpcType
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// ─────────────────────────────────────────────────────────────────────────────
// EVENTOS DINÁMICOS del mundo abierto ("vida urbana") — extensiones del VM, SIN gemelo miembro.
//
// Cada 40–80 s aparece UNA escena ambiental cerca del jugador (anclada a la calle más
// cercana), coherente con la fantasía de infección (regla rectora, CAMPAIGN/00):
//   1 CONVERSACIÓN: 2 peatones se detienen a platicar (burbujas 💬 alternadas).
//   2 PERSECUCIÓN:  un policía persigue a un sospechoso a pie (cruzan la zona y se van).
//   3 MINI-BROTE:   un civil SE CONVIERTE, ataca, llegan 2 policías, lo someten y se lo
//                   llevan (versión ambiental condensada del brote de la Misión 2).
// De NOCHE (nightAlpha alto) y con la Misión 3 completada, el MINI-BROTE es más probable
// (la infección escala). Corre en MUNDO LIBRE y en campaña (fuera de escenas de misión).
//
// Actores en dynamicEventNpcs (prefijo DYN_): lista propia fusionada en uiState.npcs por
// updateNpcsState (como mission2Npcs) — NO son atacables ni tocan la IA/red. Movimiento
// scriptado beeline (máx 5 NPCs, trigonometría por tick: coste despreciable, gama baja OK).
// Los excluye buildSaveData (prefijo DYN_) y NO se persisten (efímeros por diseño).
// ─────────────────────────────────────────────────────────────────────────────

// Tipos de evento (dynamicEventType; 0 = ninguno).
internal const val DYN_EVT_NONE = 0
internal const val DYN_EVT_CONVO = 1
internal const val DYN_EVT_CHASE = 2
internal const val DYN_EVT_BROTE = 3

// Cadencia y duraciones.
private const val DYN_NEXT_MIN_MS = 40_000L
private const val DYN_NEXT_MAX_MS = 80_000L
private const val DYN_CONVO_MS = 22_000L
private const val DYN_CHASE_MAX_MS = 18_000L
private const val DYN_BROTE_ATTACK_MS = 5_000L
private const val DYN_STAGE_TIMEOUT_MS = 12_000L
// Velocidades (grados/tick, escala de las de la Misión 2).
private const val DYN_RUN_SPEED = 0.0000110
private const val DYN_ZOMBIE_SPEED = 0.0000042
private const val DYN_FLEE_SPEED = 0.0000050

private fun dynDist(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
    val dLat = aLat - bLat
    val dLon = aLon - bLon
    return sqrt(dLat * dLat + dLon * dLon)
}

// ── TICK PRINCIPAL (lo llama el game loop MIEMBRO; early-outs baratos) ──
internal fun WorldMapViewModel.runDynamicEventsTick(playerLoc: GeoPoint) {
    // No competir con el apocalipsis ni con escenas scriptadas de misión; ni en interiores.
    if (_uiState.value.globalZombieMode) {
        if (dynamicEventType != DYN_EVT_NONE) clearDynamicEvent()
        return
    }
    if (currentInteriorRoomId != null) return
    if (isCampaignEscortActive() || isMission1ChaseActive() ||
        isMission2StoryActive() || isMission3StoryActive()) {
        if (dynamicEventType != DYN_EVT_NONE) clearDynamicEvent()
        return
    }
    val now = System.currentTimeMillis()

    if (dynamicEventType == DYN_EVT_NONE) {
        if (nextDynamicEventMs == 0L) {
            nextDynamicEventMs = now + DYN_NEXT_MIN_MS +
                (Math.random() * (DYN_NEXT_MAX_MS - DYN_NEXT_MIN_MS)).toLong()
            return
        }
        if (now < nextDynamicEventMs) return
        startDynamicEvent(playerLoc, now)
        return
    }

    // Si el jugador se TELETRANSPORTÓ lejos, la escena ya no tiene público: se cancela.
    if (dynDist(playerLoc.latitude, playerLoc.longitude, dynamicEventLat, dynamicEventLon) > 0.0030) {
        clearDynamicEvent()
        return
    }

    when (dynamicEventType) {
        DYN_EVT_CONVO -> tickDynConvo(now)
        DYN_EVT_CHASE -> tickDynChase(now)
        DYN_EVT_BROTE -> tickDynBrote(now)
    }
    updateNpcsState()
}

// ── ARRANQUE: elige tipo (ponderado por noche/progreso) y ancla la escena a una calle. ──
private fun WorldMapViewModel.startDynamicEvent(playerLoc: GeoPoint, now: Long) {
    // Punto a 45–90 m del jugador (al borde del fog: se VE llegar), SNAPEADO a la calle
    // más cercana para que la escena no caiga dentro de un edificio.
    val ang = Math.random() * 2.0 * Math.PI
    val r = 0.00040 + Math.random() * 0.00040
    val raw = GeoPoint(playerLoc.latitude + sin(ang) * r, playerLoc.longitude + cos(ang) * r)
    val anchor = getNearestPointOnNetwork(raw)
    dynamicEventLat = anchor.latitude
    dynamicEventLon = anchor.longitude

    // Pesos: la noche y el avance de la infección (M3 completada) suben el MINI-BROTE.
    val night = _uiState.value.nightAlpha / MAX_NIGHT_ALPHA          // 0..1
    val m3Done = campaignManager.isCompleted(
        ovh.gabrielhuav.pow.domain.models.campaign.MissionCatalog.MISSION_3_ID)
    val broteW = 0.25 + 0.30 * night + (if (m3Done) 0.15 else 0.0)
    val roll = Math.random() * (0.45 + 0.30 + broteW)
    dynamicEventType = when {
        roll < 0.45 -> DYN_EVT_CONVO
        roll < 0.75 -> DYN_EVT_CHASE
        else -> DYN_EVT_BROTE
    }
    dynamicEventStage = 0
    dynamicEventStageMs = now
    dynEvtAngle = Math.random() * 2.0 * Math.PI   // dirección de la persecución
    android.util.Log.d("POW_DBG", "EVENTO DINÁMICO: tipo=$dynamicEventType en ($dynamicEventLat,$dynamicEventLon)")
}

// ── FIN de la escena: limpiar y programar la siguiente. ──
internal fun WorldMapViewModel.clearDynamicEvent() {
    dynamicEventNpcs.clear()
    dynamicEventType = DYN_EVT_NONE
    dynamicEventStage = 0
    dynamicEventStageMs = 0L
    nextDynamicEventMs = System.currentTimeMillis() + DYN_NEXT_MIN_MS +
        (Math.random() * (DYN_NEXT_MAX_MS - DYN_NEXT_MIN_MS)).toLong()
}

// ── 1 · CONVERSACIÓN: 2 peatones frente a frente, burbujas 💬 alternadas. ──
private fun WorldMapViewModel.tickDynConvo(now: Long) {
    if (dynamicEventNpcs.isEmpty()) {
        dynamicEventNpcs["DYN_CONVO_A"] = Npc(
            id = "DYN_CONVO_A", type = NpcType.PERSON,
            location = GeoPoint(dynamicEventLat, dynamicEventLon - 0.00002),
            speed = 0.0, isRemote = false, isMoving = false,
            facingRight = true, visualConfig = dynRandomCivilVisual()
        )
        dynamicEventNpcs["DYN_CONVO_B"] = Npc(
            id = "DYN_CONVO_B", type = NpcType.PERSON,
            location = GeoPoint(dynamicEventLat, dynamicEventLon + 0.00002),
            speed = 0.0, isRemote = false, isMoving = false,
            facingRight = false, visualConfig = dynRandomCivilVisual()
        )
    }
    // Burbuja del que "habla" (alterna cada ~3 s).
    val talker = if ((now / 3000L) % 2L == 0L) "DYN_CONVO_A" else "DYN_CONVO_B"
    dynamicEventNpcs[talker]?.let {
        if (it.talkingUntil < now + 400) dynamicEventNpcs[talker] = it.copy(talkingUntil = now + 900)
    }
    if (now - dynamicEventStageMs > DYN_CONVO_MS) clearDynamicEvent()
}

// ── 2 · PERSECUCIÓN: un policía corre tras un sospechoso; cruzan la zona y se van. ──
private fun WorldMapViewModel.tickDynChase(now: Long) {
    if (dynamicEventNpcs.isEmpty()) {
        dynamicEventNpcs["DYN_CHASE_CIV"] = Npc(
            id = "DYN_CHASE_CIV", type = NpcType.PERSON,
            location = GeoPoint(dynamicEventLat, dynamicEventLon),
            speed = DYN_RUN_SPEED, isRemote = false, isMoving = true,
            visualConfig = dynRandomCivilVisual()
        )
        dynamicEventNpcs["DYN_CHASE_COP"] = Npc(
            id = "DYN_CHASE_COP", type = NpcType.POLICE_COP,
            location = GeoPoint(
                dynamicEventLat - sin(dynEvtAngle) * 0.00008,
                dynamicEventLon - cos(dynEvtAngle) * 0.00008
            ),
            speed = DYN_RUN_SPEED, isRemote = false, isMoving = true,
            policeDisembarked = true, policeCanShoot = false
        )
    }
    // El civil huye en línea recta (dirección fija); el policía lo sigue ~8 m atrás.
    dynamicEventNpcs["DYN_CHASE_CIV"]?.let { civ ->
        dynamicEventNpcs["DYN_CHASE_CIV"] = civ.copy(
            location = GeoPoint(
                civ.location.latitude + sin(dynEvtAngle) * DYN_RUN_SPEED,
                civ.location.longitude + cos(dynEvtAngle) * DYN_RUN_SPEED
            ),
            isMoving = true, facingRight = cos(dynEvtAngle) >= 0
        )
    }
    val civ = dynamicEventNpcs["DYN_CHASE_CIV"]
    dynamicEventNpcs["DYN_CHASE_COP"]?.let { cop ->
        if (civ != null) {
            val a = atan2(civ.location.latitude - cop.location.latitude,
                civ.location.longitude - cop.location.longitude)
            dynamicEventNpcs["DYN_CHASE_COP"] = cop.copy(
                location = GeoPoint(
                    cop.location.latitude + sin(a) * DYN_RUN_SPEED * 0.98,
                    cop.location.longitude + cos(a) * DYN_RUN_SPEED * 0.98
                ),
                isMoving = true, facingRight = cos(a) >= 0
            )
        }
    }
    // Se van de la escena (o tope de tiempo) → fin.
    val gone = civ == null ||
        dynDist(civ.location.latitude, civ.location.longitude, dynamicEventLat, dynamicEventLon) > 0.0018
    if (gone || now - dynamicEventStageMs > DYN_CHASE_MAX_MS) clearDynamicEvent()
}

// ── 3 · MINI-BROTE ambiental (condensado de la Misión 2): conversión → ataque →
//        llegan 2 policías → someten → se lo llevan. ──
private fun WorldMapViewModel.tickDynBrote(now: Long) {
    if (dynamicEventNpcs.isEmpty() && dynamicEventStage == 0) {
        for (i in 0 until 2) {
            dynamicEventNpcs["DYN_CIV_$i"] = Npc(
                id = "DYN_CIV_$i", type = NpcType.PERSON,
                location = GeoPoint(
                    dynamicEventLat + (if (i == 0) 0.00005 else -0.00005),
                    dynamicEventLon + (if (i == 0) -0.00004 else 0.00005)
                ),
                speed = 0.0, isRemote = false, isMoving = false,
                visualConfig = dynRandomCivilVisual()
            )
        }
        dynamicEventNpcs["DYN_ZOMBIE"] = Npc(
            id = "DYN_ZOMBIE", type = NpcType.PERSON,
            location = GeoPoint(dynamicEventLat, dynamicEventLon),
            speed = 0.0, isRemote = false, isMoving = false,
            visualConfig = dynRandomCivilVisual()
        )
    }
    when (dynamicEventStage) {
        // Etapa 0: tras ~1.5 s, la CONVERSIÓN. ⚠️ visualConfig = null (gotcha de render).
        0 -> if (now - dynamicEventStageMs > 1500L) {
            dynamicEventNpcs["DYN_ZOMBIE"]?.let {
                dynamicEventNpcs["DYN_ZOMBIE"] = it.copy(
                    type = NpcType.ZOMBIE, visualConfig = null,
                    speed = DYN_ZOMBIE_SPEED, isMoving = true
                )
            }
            _uiState.update { it.copy(interactionPrompt = "🚨 ¡Un infectado atacó a alguien en la calle!") }
            dynamicEventStage = 1
            dynamicEventStageMs = now
        }
        // Etapa 1: el zombie persigue; los civiles huyen. Luego llegan 2 policías corriendo.
        1 -> {
            dynZombieChaseAndFlee()
            if (now - dynamicEventStageMs > DYN_BROTE_ATTACK_MS) {
                for (i in 1..2) {
                    dynamicEventNpcs["DYN_COP_$i"] = Npc(
                        id = "DYN_COP_$i", type = NpcType.POLICE_COP,
                        location = GeoPoint(
                            dynamicEventLat - 0.00060 - (i - 1) * 0.00005,
                            dynamicEventLon - 0.00060 + (i - 1) * 0.00005
                        ),
                        speed = DYN_RUN_SPEED, isRemote = false, isMoving = true,
                        policeDisembarked = true, policeCanShoot = false
                    )
                }
                dynamicEventStage = 2
                dynamicEventStageMs = now
            }
        }
        // Etapa 2: los policías alcanzan al zombie y lo SOMETEN.
        2 -> {
            dynZombieChaseAndFlee()
            val zombie = dynamicEventNpcs["DYN_ZOMBIE"] ?: run { dynamicEventStage = 3; dynamicEventStageMs = now; return }
            var allClose = true
            for (i in 1..2) {
                val cop = dynamicEventNpcs["DYN_COP_$i"] ?: continue
                val d = dynDist(cop.location.latitude, cop.location.longitude,
                    zombie.location.latitude, zombie.location.longitude)
                if (d > 0.00005) {
                    allClose = false
                    val a = atan2(zombie.location.latitude - cop.location.latitude,
                        zombie.location.longitude - cop.location.longitude)
                    dynamicEventNpcs[cop.id] = cop.copy(
                        location = GeoPoint(
                            cop.location.latitude + sin(a) * DYN_RUN_SPEED,
                            cop.location.longitude + cos(a) * DYN_RUN_SPEED
                        ),
                        isMoving = true, facingRight = cos(a) >= 0
                    )
                } else if (cop.isMoving) {
                    dynamicEventNpcs[cop.id] = cop.copy(isMoving = false)
                }
            }
            if (allClose || now - dynamicEventStageMs > DYN_STAGE_TIMEOUT_MS) {
                dynamicEventNpcs["DYN_ZOMBIE"] = zombie.copy(isMoving = false, speed = 0.0)
                dynamicEventStage = 3
                dynamicEventStageMs = now
            }
        }
        // Etapa 3: SE LO LLEVAN (caminan alejándose) y la escena se desvanece.
        3 -> {
            var anyLeft = false
            for (id in listOf("DYN_COP_1", "DYN_COP_2", "DYN_ZOMBIE")) {
                val npc = dynamicEventNpcs[id] ?: continue
                val d = dynDist(npc.location.latitude, npc.location.longitude, dynamicEventLat, dynamicEventLon)
                if (d > 0.0010) { dynamicEventNpcs.remove(id); continue }
                anyLeft = true
                dynamicEventNpcs[id] = npc.copy(
                    location = GeoPoint(
                        npc.location.latitude - sin(dynEvtAngle) * DYN_FLEE_SPEED,
                        npc.location.longitude - cos(dynEvtAngle) * DYN_FLEE_SPEED
                    ),
                    isMoving = true
                )
            }
            if (!anyLeft || now - dynamicEventStageMs > DYN_STAGE_TIMEOUT_MS) clearDynamicEvent()
        }
    }
}

// El zombie del mini-brote persigue al civil más cercano; los civiles huyen.
private fun WorldMapViewModel.dynZombieChaseAndFlee() {
    val zombie = dynamicEventNpcs["DYN_ZOMBIE"] ?: return
    var target: Npc? = null
    var best = Double.MAX_VALUE
    for (npc in dynamicEventNpcs.values) {
        if (!npc.id.startsWith("DYN_CIV_")) continue
        val d = dynDist(zombie.location.latitude, zombie.location.longitude,
            npc.location.latitude, npc.location.longitude)
        if (d < best) { best = d; target = npc }
    }
    if (target != null && best > 0.00003) {
        val a = atan2(target.location.latitude - zombie.location.latitude,
            target.location.longitude - zombie.location.longitude)
        dynamicEventNpcs["DYN_ZOMBIE"] = zombie.copy(
            location = GeoPoint(
                zombie.location.latitude + sin(a) * DYN_ZOMBIE_SPEED,
                zombie.location.longitude + cos(a) * DYN_ZOMBIE_SPEED
            ),
            isMoving = true, facingRight = cos(a) >= 0
        )
    }
    for (civ in dynamicEventNpcs.values.toList()) {
        if (!civ.id.startsWith("DYN_CIV_")) continue
        val dLat = civ.location.latitude - zombie.location.latitude
        val dLon = civ.location.longitude - zombie.location.longitude
        val d = sqrt(dLat * dLat + dLon * dLon)
        if (d > 0.0009) { dynamicEventNpcs.remove(civ.id); continue }
        val a = if (d > 1e-9) atan2(dLat, dLon) else Math.random() * 2.0 * Math.PI
        dynamicEventNpcs[civ.id] = civ.copy(
            location = GeoPoint(
                civ.location.latitude + sin(a) * DYN_FLEE_SPEED,
                civ.location.longitude + cos(a) * DYN_FLEE_SPEED
            ),
            isMoving = true, facingRight = cos(a) >= 0
        )
    }
}

// Look civil aleatorio (copia local del patrón de la Misión 2; el original es private allá).
private fun dynRandomCivilVisual(): CharacterVisualConfig {
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
