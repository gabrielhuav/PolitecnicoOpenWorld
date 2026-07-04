package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import kotlinx.coroutines.flow.update
import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.campaign.MissionCatalog
import ovh.gabrielhuav.pow.domain.models.campaign.mission3.Mission3
import ovh.gabrielhuav.pow.domain.models.map.Npc
import ovh.gabrielhuav.pow.domain.models.map.NpcType
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// ─────────────────────────────────────────────────────────────────────────────
// MODO HISTORIA · MISIÓN 3 "Regreso a la ENCB" (extensiones del WorldMapViewModel, sin gemelo).
//
// Fases (mission3Phase, persistida en GameSaveData.mission3Phase):
//   1 VIAJE       → ve a la ENCB (🎯). Al acercarte (~100 m) se ARMA el cordón → fase 2.
//   2 INFILTRACIÓN→ 6 granaderos patrullan un ANILLO alrededor de la ENCB; 3 paparazzi
//                   merodean afuera (escenografía + burbujas). SIGILO: a <~21 m de un granadero
//                   por >2.5 s te DETECTAN → MISIÓN FALLIDA. Cruza el cordón y llega al centro
//                   (~15 m) → entras al interior (mission3EnterEncb=true; navega la View) → fase 3.
//   3 ASALTO      → INTERIOR: cadena ENCB con ZOMBIS; recuperar la EVIDENCIA 🧪 en encb_lab1.
//                   La completa completeMission3Evidence() (callback desde ZombieGameScreen).
//   4 DONE        → recompensa: PRIMERA ARMA DE FUEGO (hasFirearm=true → modo RANGED en campaña).
//
// Igual que la Misión 2: NPCs propios en mission3Npcs (merge en uiState.npcs), ticks idempotentes
// (re-spawnean actores tras CARGAR partida), y "activa" = fase en curso Y objetivo m3_* SEGUIDO
// (registro de misiones). NOTA render: los "granaderos" usan NpcType.POLICE_COP (sprite 👮) —
// la skin GRANADERO es de interiores; la ruta de render premade en exterior sigue pendiente.
// ─────────────────────────────────────────────────────────────────────────────

private fun m3Dist(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
    val dLat = aLat - bLat
    val dLon = aLon - bLon
    return sqrt(dLat * dLat + dLon * dLon)
}

internal fun WorldMapViewModel.isMission3StoryActive(): Boolean =
    inCampaign && mission3Phase >= Mission3.PHASE_TRAVEL && mission3Phase <= Mission3.PHASE_ASSAULT &&
        _uiState.value.currentObjective?.id?.startsWith(Mission3.OBJECTIVE_ID_PREFIX) == true

// ── ARRANQUE (desde el REGISTRO DE MISIONES). Requiere Misión 2 completada (lo valida el selector). ──
internal fun WorldMapViewModel.startMission3Story() {
    clearMission3Story()
    mission3Phase = Mission3.PHASE_TRAVEL
    setCampaignObjective(MissionCatalog.M3_IR_ENCB)
    _uiState.update { it.copy(
        interactionPrompt = "🚧 La ENCB está en CUARENTENA. Vuelve allá y descubre qué esconden"
    ) }
    android.util.Log.d("POW_DBG", "MISIÓN 3: seguida desde el registro (fase VIAJE)")
}

// ── REANUDAR (registro): re-fija el objetivo de la fase actual sin reiniciar. ──
internal fun WorldMapViewModel.resumeMission3Objective() {
    val obj = when (mission3Phase) {
        Mission3.PHASE_TRAVEL -> MissionCatalog.M3_IR_ENCB
        Mission3.PHASE_INFILTRATE -> MissionCatalog.M3_INFILTRARSE
        Mission3.PHASE_ASSAULT -> MissionCatalog.M3_RECUPERAR_EVIDENCIA
        else -> return
    }
    setCampaignObjective(obj)
}

// ── TICK PRINCIPAL (game loop MIEMBRO, cuando isMission3StoryActive()) ──
internal fun WorldMapViewModel.runMission3StoryTick(playerLoc: GeoPoint) {
    if (currentInteriorRoomId != null) return   // dentro de un interior: la fase 3 corre allá
    val now = System.currentTimeMillis()
    when (mission3Phase) {
        Mission3.PHASE_TRAVEL -> {
            // Al ACERCARTE a la ENCB se arma el cordón y arranca la infiltración.
            if (m3Dist(playerLoc.latitude, playerLoc.longitude, Mission3.ENCB_LAT, Mission3.ENCB_LON)
                <= Mission3.APPROACH_DEG) {
                mission3Phase = Mission3.PHASE_INFILTRATE
                mission3DetectSinceMs = 0L
                setCampaignObjective(MissionCatalog.M3_INFILTRARSE)
                _uiState.update { it.copy(
                    interactionPrompt = "🚧 Cordón de granaderos: NO dejes que te vean. Llega a la entrada"
                ) }
            }
        }
        Mission3.PHASE_INFILTRATE -> tickM3Infiltrate(playerLoc, now)
        Mission3.PHASE_ASSAULT -> {
            // La acción ocurre en el INTERIOR. Si el jugador SALIÓ sin la evidencia, puede
            // RE-ENTRAR volviendo al centro de la ENCB (sin re-hacer el sigilo). HISTÉRESIS:
            // primero hay que ALEJARSE (>2× ENTER) — evita el bucle de navegación en la puerta.
            val d = m3Dist(playerLoc.latitude, playerLoc.longitude, Mission3.ENCB_LAT, Mission3.ENCB_LON)
            if (d > Mission3.ENTER_DEG * 2) mission3ReentryArmed = true
            if (mission3ReentryArmed && !_uiState.value.mission3EnterEncb && d <= Mission3.ENTER_DEG) {
                mission3ReentryArmed = false
                _uiState.update { it.copy(mission3EnterEncb = true) }
            }
        }
    }
    updateNpcsState()
}

// ── FASE 2 · INFILTRACIÓN: cordón de granaderos + paparazzi + sigilo ──
private fun WorldMapViewModel.tickM3Infiltrate(playerLoc: GeoPoint, now: Long) {
    // Spawn perezoso e idempotente (también re-arma tras CARGAR partida en esta fase).
    if (mission3Npcs.isEmpty()) {
        for (i in 0 until Mission3.CORDON_COUNT) {
            val ang = 2.0 * Math.PI * i / Mission3.CORDON_COUNT
            val id = "M3_GRANADERO_$i"
            mission3Npcs[id] = Npc(
                id = id,
                type = NpcType.POLICE_COP,
                location = GeoPoint(
                    Mission3.ENCB_LAT + sin(ang) * Mission3.CORDON_RING_DEG,
                    Mission3.ENCB_LON + cos(ang) * Mission3.CORDON_RING_DEG
                ),
                speed = Mission3.CORDON_SPEED,
                isRemote = false,
                isMoving = true,
                policeDisembarked = true,
                policeCanShoot = false
            )
        }
        for (i in 0 until Mission3.PAPARAZZI_COUNT) {
            val ang = 2.0 * Math.PI * i / Mission3.PAPARAZZI_COUNT + 0.5
            val id = "M3_PAPARAZZI_$i"
            mission3Npcs[id] = Npc(
                id = id,
                type = NpcType.PERSON,
                location = GeoPoint(
                    Mission3.ENCB_LAT + sin(ang) * Mission3.PAPARAZZI_RING_DEG,
                    Mission3.ENCB_LON + cos(ang) * Mission3.PAPARAZZI_RING_DEG
                ),
                speed = 0.0,
                isRemote = false,
                isMoving = false,
                visualConfig = m3PaparazziVisual()
            )
        }
    }

    // PATRULLA del cordón: cada granadero orbita su PUESTO del anillo (arco corto, determinista
    // por cubeta de tiempo — mismo truco sin estado que la Misión 2). Los paparazzi "reportean"
    // (burbuja 💬 intermitente), quietos.
    var nearest = Double.MAX_VALUE
    val bucket = now / 5000L
    for (npc in mission3Npcs.values.toList()) {
        if (npc.id.startsWith("M3_GRANADERO_")) {
            val i = npc.id.removePrefix("M3_GRANADERO_").toIntOrNull() ?: 0
            val baseAng = 2.0 * Math.PI * i / Mission3.CORDON_COUNT
            val rnd = java.util.Random(npc.id.hashCode() * 31L + bucket)
            val ang = baseAng + (rnd.nextDouble() - 0.5) * 0.9   // arco ±~26° alrededor del puesto
            val tLat = Mission3.ENCB_LAT + sin(ang) * (Mission3.CORDON_RING_DEG + (rnd.nextDouble() - 0.5) * Mission3.CORDON_PATROL_DEG)
            val tLon = Mission3.ENCB_LON + cos(ang) * (Mission3.CORDON_RING_DEG + (rnd.nextDouble() - 0.5) * Mission3.CORDON_PATROL_DEG)
            val a = kotlin.math.atan2(tLat - npc.location.latitude, tLon - npc.location.longitude)
            val arrived = m3Dist(npc.location.latitude, npc.location.longitude, tLat, tLon) < 0.00004
            val moved = if (arrived) npc.location else GeoPoint(
                npc.location.latitude + sin(a) * Mission3.CORDON_SPEED,
                npc.location.longitude + cos(a) * Mission3.CORDON_SPEED
            )
            mission3Npcs[npc.id] = npc.copy(location = moved, isMoving = !arrived, facingRight = cos(a) >= 0)
            val d = m3Dist(playerLoc.latitude, playerLoc.longitude, moved.latitude, moved.longitude)
            if (d < nearest) nearest = d
        } else if (npc.id.startsWith("M3_PAPARAZZI_")) {
            // Burbuja intermitente: "cazando la nota" afuera del cordón.
            if ((now / 4000L + npc.id.hashCode()) % 3L == 0L && npc.talkingUntil < now) {
                mission3Npcs[npc.id] = npc.copy(talkingUntil = now + 1200)
            }
        }
    }

    // SIGILO: ¿un granadero te tiene demasiado cerca demasiado tiempo?
    if (nearest < Mission3.DETECT_DEG) {
        if (mission3DetectSinceMs == 0L) mission3DetectSinceMs = now
        _uiState.update { it.copy(interactionPrompt = "⚠️ ¡Un granadero te está viendo! ESCÓNDETE") }
        if (now - mission3DetectSinceMs > Mission3.DETECT_MS) {
            android.util.Log.d("POW_DBG", "MISIÓN 3: DETECTADO por el cordón → misión fallida")
            mission3Npcs.clear()
            _uiState.update { it.copy(showMissionFailed = true) }
            return
        }
    } else {
        mission3DetectSinceMs = 0L
    }

    // ENTRADA: cruzaste el cordón y llegaste al centro de la ENCB → asalto interior.
    if (m3Dist(playerLoc.latitude, playerLoc.longitude, Mission3.ENCB_LAT, Mission3.ENCB_LON)
        <= Mission3.ENTER_DEG) {
        mission3Phase = Mission3.PHASE_ASSAULT
        mission3Npcs.clear()
        setCampaignObjective(MissionCatalog.M3_RECUPERAR_EVIDENCIA)
        soundManager.playMisionCumplida()   // jingle: superaste el cordón
        _uiState.update { it.copy(
            mission3EnterEncb = true,
            interactionPrompt = "🧟 Dentro hay INFECTADOS. Recupera la evidencia del laboratorio"
        ) }
        android.util.Log.d("POW_DBG", "MISIÓN 3: cordón superado → ASALTO interior ENCB")
    }
}

// La View consumió la navegación al interior (evita re-disparar al recomponer).
internal fun WorldMapViewModel.consumeMission3EnterEncb() {
    _uiState.update { it.copy(mission3EnterEncb = false) }
}

// ── FASE 3 · EVIDENCIA: la completa el INTERIOR (encb_lab1) vía este callback (AppNavGraph) ──
fun WorldMapViewModel.completeMission3Evidence() {
    if (mission3Phase != Mission3.PHASE_ASSAULT) return
    mission3Phase = Mission3.PHASE_DONE
    hasFirearm = true   // 🔫 RECOMPENSA: primera arma de fuego (persistida en el guardado)
    markMissionCompleted(MissionCatalog.MISSION_3_ID)
    _uiState.update { it.copy(
        currentObjective = MissionCatalog.M3_RECUPERAR_EVIDENCIA,
        objectiveDone = true,
        interactionPrompt = "🔫 ¡MISIÓN 3 COMPLETADA! Conseguiste tu primera ARMA DE FUEGO"
    ) }
    soundManager.playMisionCumplida()
    android.util.Log.d("POW_DBG", "MISIÓN 3: COMPLETADA (evidencia + arma de fuego)")
}

// ── LIMPIEZA (idempotente; NO toca mission3Phase — eso lo decide quien llama) ──
internal fun WorldMapViewModel.clearMission3Story() {
    mission3Npcs.clear()
    mission3DetectSinceMs = 0L
    mission3ReentryArmed = false
    if (_uiState.value.mission3EnterEncb) consumeMission3EnterEncb()
}

// Look civil del paparazzi (chaleco caqui + cámara imaginaria; render modular del exterior).
private fun m3PaparazziVisual(): ovh.gabrielhuav.pow.domain.models.map.CharacterVisualConfig =
    ovh.gabrielhuav.pow.domain.models.map.CharacterVisualConfig(
        bodyFolder = "npc_walk_1",
        bodyPrefix = "npc_walk_1_",
        hairId = (1..5).random(),
        hairColor = androidx.compose.ui.graphics.Color.DarkGray,
        shirtColor = androidx.compose.ui.graphics.Color(0xFFC2B280),
        pantsColor = androidx.compose.ui.graphics.Color(0xFF37474F)
    )
