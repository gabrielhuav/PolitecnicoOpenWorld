package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import kotlinx.coroutines.flow.update
import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.ai.NpcAiManager
import ovh.gabrielhuav.pow.domain.models.campaign.MissionCatalog
import ovh.gabrielhuav.pow.domain.models.campaign.side.SideMissions
import ovh.gabrielhuav.pow.domain.models.map.CharacterVisualConfig
import ovh.gabrielhuav.pow.domain.models.map.Npc
import ovh.gabrielhuav.pow.domain.models.map.NpcTrait
import ovh.gabrielhuav.pow.domain.models.map.NpcType
import kotlin.math.cos
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────────
// MISIONES SECUNDARIAS (side1/side2) — extensiones del WorldMapViewModel, SIN gemelo miembro.
//
// Diseño (ver SideMissions.kt y CAMPAIGN/04_SIDE_MISSIONS.md):
//  - SIN fases persistidas: el ID DEL OBJETIVO ACTIVO es el estado. Si se deja de seguir,
//    la secundaria se REINICIA al volver a seguirla (son cortas; documentado).
//  - side1 ENTREGA: ambos objetivos por LLEGADA (radio > 0) → los cumple
//    checkObjectiveProgress; este tick solo ENCADENA (recoger → entregar) y ambienta
//    (el paramédico que espera, con burbuja 💬).
//  - side2 CONTENCIÓN: los infectados son zombis REALES en remoteEntities (prefijo
//    NpcAiManager.SIDE_ZOMBIE_PREFIX): el mover zombi los simula SIN apocalipsis (gate
//    ampliado en NpcAiManager) y performPlayerAttack puede matarlos. El tick cuenta vivos.
//  - Sin "MISIÓN FALLIDA": morir respawnea normal y la secundaria continúa.
//  - Recompensa en DINERO al completar (markMissionCompleted → MissionRewards, 1 sola vez).
// ─────────────────────────────────────────────────────────────────────────────

// ACTIVA = objetivo secundario activo (s1_/s2_) en sesión de campaña.
internal fun WorldMapViewModel.isSideMissionStoryActive(): Boolean {
    if (!inCampaign) return false
    val id = _uiState.value.currentObjective?.id ?: return false
    return id.startsWith(SideMissions.S1_PREFIX) || id.startsWith(SideMissions.S2_PREFIX)
}

// ── ARRANQUE desde el registro de misiones (selectCampaignMission / replay). ──
internal fun WorldMapViewModel.startSideMission1() {
    clearSideMissions()
    setCampaignObjective(SideMissions.S1_RECOGER)
    _uiState.update { it.copy(interactionPrompt = "📦 Recoge el botiquín de suministros (sigue el 🎯)") }
    android.util.Log.d("POW_DBG", "SECUNDARIA 1 (suministros): seguida desde el registro")
}

internal fun WorldMapViewModel.startSideMission2() {
    clearSideMissions()
    setCampaignObjective(SideMissions.S2_IR)
    _uiState.update { it.copy(interactionPrompt = "🚨 Reportan infectados sueltos. Ve a la zona (sigue el 🎯)") }
    android.util.Log.d("POW_DBG", "SECUNDARIA 2 (contención): seguida desde el registro")
}

// ── TICK PRINCIPAL (lo llama el game loop MIEMBRO cuando isSideMissionStoryActive()) ──
internal fun WorldMapViewModel.runSideMissionTick(playerLoc: GeoPoint) {
    if (currentInteriorRoomId != null) return
    val now = System.currentTimeMillis()
    val obj = _uiState.value.currentObjective ?: return
    when (obj.id) {
        SideMissions.S1_RECOGER.id -> tickS1Recoger(now)
        SideMissions.S1_ENTREGAR.id -> tickS1Entregar(now)
        SideMissions.S2_IR.id -> tickS2Ir(now)
        SideMissions.S2_ELIMINAR.id -> tickS2Eliminar(playerLoc, now)
    }
    updateNpcsState()
}

// ── side1 · fase RECOGER: checkObjectiveProgress la cumple; aquí solo se encadena. ──
private fun WorldMapViewModel.tickS1Recoger(now: Long) {
    if (!_uiState.value.objectiveDone) { sideMissionTransitionMs = 0L; return }
    if (sideMissionTransitionMs == 0L) sideMissionTransitionMs = now
    if (now - sideMissionTransitionMs < SideMissions.TRANSITION_MS) return
    sideMissionTransitionMs = 0L
    setCampaignObjective(SideMissions.S1_ENTREGAR)
    _uiState.update { it.copy(interactionPrompt = "📦 Botiquín recogido. Llévaselo al paramédico (🎯)") }
}

// ── side1 · fase ENTREGAR: el paramédico espera en el destino (escenografía + cierre). ──
private fun WorldMapViewModel.tickS1Entregar(now: Long) {
    // Paramédico esperando (idempotente; re-arma tras CARGAR partida). Sin asset premade
    // exterior aún (misma limitación que los granaderos de la M3): civil con look "bata blanca".
    if (!sideMissionNpcs.containsKey("SM_PARAMEDICO")) {
        sideMissionNpcs["SM_PARAMEDICO"] = Npc(
            id = "SM_PARAMEDICO", type = NpcType.PERSON,
            location = GeoPoint(SideMissions.S1_DELIVER_LAT, SideMissions.S1_DELIVER_LON),
            speed = 0.0, isRemote = false, isMoving = false,
            visualConfig = smParamedicVisual()
        )
    }
    // Burbuja 💬 intermitente para ubicarlo de lejos.
    sideMissionNpcs["SM_PARAMEDICO"]?.let {
        if (it.talkingUntil < now && (now / 4000L) % 2L == 0L) {
            sideMissionNpcs["SM_PARAMEDICO"] = it.copy(talkingUntil = now + 1200)
        }
    }
    if (!_uiState.value.objectiveDone) { sideMissionTransitionMs = 0L; return }
    // Entregado → misión completada (dinero vía markMissionCompleted, una sola vez).
    markMissionCompleted(MissionCatalog.SIDE_1_ID)
    _uiState.update { it.copy(
        currentObjective = null, objectiveDone = false,
        interactionPrompt = "✅ Suministros entregados. ¡Gracias! (+$" + MissionRewards.moneyFor(MissionCatalog.SIDE_1_ID) + ")"
    ) }
    soundManager.playMisionCumplida()
    clearSideMissions()
    android.util.Log.d("POW_DBG", "SECUNDARIA 1: COMPLETADA")
}

// ── side2 · fase IR: al llegar (objectiveDone) se pasa a ELIMINAR y se siembran los zombis. ──
private fun WorldMapViewModel.tickS2Ir(now: Long) {
    if (!_uiState.value.objectiveDone) { sideMissionTransitionMs = 0L; return }
    if (sideMissionTransitionMs == 0L) sideMissionTransitionMs = now
    if (now - sideMissionTransitionMs < SideMissions.TRANSITION_MS) return
    sideMissionTransitionMs = 0L
    setCampaignObjective(SideMissions.S2_ELIMINAR)
    spawnSide2Zombies()
    _uiState.update { it.copy(interactionPrompt = "🧟 ¡Ahí están! Elimínalos a todos (golpea con B)") }
}

// Siembra los infectados como zombis REALES en remoteEntities (fuente de verdad de la IA):
// el prefijo SIDE_ZOMBIE_PREFIX activa su mover zombi sin apocalipsis y los hace golpeables.
// ⚠️ visualConfig = null (gotcha de render: un ZOMBIE con visualConfig se dibuja humano).
private fun WorldMapViewModel.spawnSide2Zombies() {
    for (i in 0 until SideMissions.S2_ZOMBIE_COUNT) {
        val ang = 2.0 * Math.PI * i / SideMissions.S2_ZOMBIE_COUNT
        val id = NpcAiManager.SIDE_ZOMBIE_PREFIX + i
        remoteEntities[id] = Npc(
            id = id,
            type = NpcType.ZOMBIE,
            location = GeoPoint(
                SideMissions.S2_ZONE_LAT + sin(ang) * 0.00018,
                SideMissions.S2_ZONE_LON + cos(ang) * 0.00018
            ),
            speed = SideMissions.S2_ZOMBIE_SPEED,
            isRemote = false,
            isMoving = true,
            visualConfig = null,
            trait = NpcTrait.AGGRESSIVE,
            health = SideMissions.S2_ZOMBIE_HEALTH,
            maxHealth = SideMissions.S2_ZOMBIE_HEALTH
        )
    }
    side2Spawned = true
}

// ── side2 · fase ELIMINAR: contar vivos; 0 vivos → completada. ──
private fun WorldMapViewModel.tickS2Eliminar(playerLoc: GeoPoint, now: Long) {
    // Idempotente: tras CARGAR una partida en esta fase, re-siembra a los que faltan.
    if (!side2Spawned) { spawnSide2Zombies(); return }
    val alive = remoteEntities.entries.count {
        it.key.startsWith(NpcAiManager.SIDE_ZOMBIE_PREFIX) && it.value.health > 0f && !it.value.isDying
    }
    if (alive > 0) {
        // Progreso por el HUD (~cada 4 s, sin pisar otros avisos más de lo necesario). Si el
        // jugador se ALEJÓ de la pelea (>~200 m del infectado más cercano), recuérdale volver.
        if (now - side2LastPromptMs > 4000L) {
            side2LastPromptMs = now
            var nearest = Double.MAX_VALUE
            for ((id, z) in remoteEntities) {
                if (!id.startsWith(NpcAiManager.SIDE_ZOMBIE_PREFIX) || z.health <= 0f || z.isDying) continue
                val dLat = z.location.latitude - playerLoc.latitude
                val dLon = z.location.longitude - playerLoc.longitude
                val d = kotlin.math.sqrt(dLat * dLat + dLon * dLon)
                if (d < nearest) nearest = d
            }
            val msg = if (nearest > 0.0018) "🧟 Quedan $alive infectado(s) — vuelve a la zona (🎯)"
                      else "🧟 Quedan $alive infectado(s)"
            _uiState.update { it.copy(interactionPrompt = msg) }
        }
        return
    }
    // Todos eliminados → misión completada (el dinero lo paga markMissionCompleted, 1 vez).
    markMissionCompleted(MissionCatalog.SIDE_2_ID)
    _uiState.update { it.copy(
        currentObjective = null, objectiveDone = false,
        interactionPrompt = "✅ Zona contenida. Zacatenco respira… por ahora (+$" + MissionRewards.moneyFor(MissionCatalog.SIDE_2_ID) + ")"
    ) }
    soundManager.playMisionCumplida()
    clearSideMissions()
    android.util.Log.d("POW_DBG", "SECUNDARIA 2: COMPLETADA")
}

// ── LIMPIEZA (idempotente): actores propios + zombis SMZ_ de remoteEntities + timers. ──
internal fun WorldMapViewModel.clearSideMissions() {
    sideMissionNpcs.clear()
    remoteEntities.keys.filter { it.startsWith(NpcAiManager.SIDE_ZOMBIE_PREFIX) }
        .forEach { remoteEntities.remove(it) }
    side2Spawned = false
    sideMissionTransitionMs = 0L
    side2LastPromptMs = 0L
}

// Look de "paramédico" improvisado (bata blanca) mientras no exista asset premade exterior.
private fun smParamedicVisual(): CharacterVisualConfig = CharacterVisualConfig(
    bodyFolder = "npc_walk_1",
    bodyPrefix = "npc_walk_1_",
    hairId = 2,
    hairColor = androidx.compose.ui.graphics.Color.Black,
    shirtColor = androidx.compose.ui.graphics.Color.White,
    pantsColor = androidx.compose.ui.graphics.Color(0xFFB71C1C)
)
