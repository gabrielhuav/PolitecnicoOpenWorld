package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import android.content.Context
import kotlinx.coroutines.flow.update
import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.data.repository.CampaignRepository
import ovh.gabrielhuav.pow.data.repository.GameSaveData
import ovh.gabrielhuav.pow.data.repository.SaveGameRepository
import ovh.gabrielhuav.pow.data.repository.SavedNpc
import ovh.gabrielhuav.pow.domain.models.CarModel
import ovh.gabrielhuav.pow.domain.models.MissionCatalog
import ovh.gabrielhuav.pow.domain.models.Npc
import ovh.gabrielhuav.pow.domain.models.NpcType
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerSkin

// ─── GUARDADO / CARGA DE LA PARTIDA (MODO HISTORIA, CON SLOTS) ────────────────
// Extensiones del WorldMapViewModel (sin gemelo miembro). Construyen/aplican el snapshot
// completo de la sesión (posición, vida, nivel de búsqueda, vehículo, skin, NPCs cercanos
// y el objetivo de campaña) usando SaveGameRepository (un JSON por slot). El estado
// inmutable se actualiza con _uiState.update { it.copy(...) }. Ver doc 02/07.

// Radio (grados ≈ 130 m) alrededor del jugador para considerar un NPC "cercano".
private const val SAVE_NPC_RADIUS_DEG = 0.0012

// Construye el snapshot de la sesión actual.
fun WorldMapViewModel.buildSaveData(schoolId: String): GameSaveData {
    val s = _uiState.value
    val loc = s.currentLocation ?: GeoPoint(19.504603, -99.145985) // fallback: ESCOM
    val nearby = s.npcs
        .filter {
            kotlin.math.abs(it.location.latitude - loc.latitude) < SAVE_NPC_RADIUS_DEG &&
                kotlin.math.abs(it.location.longitude - loc.longitude) < SAVE_NPC_RADIUS_DEG
        }
        .take(40)
        .map {
            SavedNpc(
                id = it.id,
                type = it.type.name,
                lat = it.location.latitude,
                lon = it.location.longitude,
                health = it.health,
                rotation = it.rotationAngle
            )
        }
    return GameSaveData(
        schoolId = schoolId,
        lat = loc.latitude,
        lon = loc.longitude,
        health = playerHealth,
        wantedLevel = s.wantedLevel,
        isDriving = s.isDriving,
        isDrivingPoliceCar = s.isDrivingPoliceCar,
        vehicleModel = s.currentVehicleModel?.name,
        vehicleColor = s.currentVehicleColor,
        skin = s.selectedSkin.name,
        nearbyNpcs = nearby,
        objectiveId = s.currentObjective?.id,
        objectiveDone = s.objectiveDone,
        savedAt = System.currentTimeMillis()
    )
}

// Guarda la partida COMPLETA en el SLOT indicado (JSON) + la partida ligera (escuela,
// SharedPreferences) que habilita "CARGAR PARTIDA". Fija el slot como activo (auto-guardado).
fun WorldMapViewModel.saveGame(context: Context, slot: Int) {
    campaignSlot = slot
    SaveGameRepository(context).save(slot, buildSaveData(campaignSchoolId))
    CampaignRepository(context).saveCampaign(campaignSchoolId)
}

// Restaura el estado guardado SOBRE el mundo ya en carga (setStorySpawn fija posición y
// re-arma las compuertas). Restaura vida/estado/vehículo/skin/objetivo y re-inyecta los
// NPCs cercanos como entidades remotas para que reaparezcan al instante (la IA los adopta).
fun WorldMapViewModel.restoreSaveData(data: GameSaveData) {
    playerHealth = data.health.coerceIn(1f, 100f)
    val skin = PlayerSkin.entries.firstOrNull { it.name == data.skin } ?: PlayerSkin.LAZARO
    val model = data.vehicleModel?.let { name -> CarModel.entries.firstOrNull { it.name == name } }
    val objective = MissionCatalog.byId(data.objectiveId)
    _uiState.update {
        it.copy(
            wantedLevel = data.wantedLevel,
            isDriving = data.isDriving,
            isDrivingPoliceCar = data.isDrivingPoliceCar,
            currentVehicleModel = model,
            currentVehicleColor = data.vehicleColor,
            selectedSkin = skin,
            currentObjective = objective,
            objectiveDone = data.objectiveDone
        )
    }
    data.nearbyNpcs.forEach { sn ->
        val type = NpcType.entries.firstOrNull { it.name == sn.type } ?: NpcType.PERSON
        remoteEntities[sn.id] = Npc(
            id = sn.id,
            type = type,
            location = GeoPoint(sn.lat, sn.lon),
            speed = 0.0,
            health = sn.health,
            rotationAngle = sn.rotation
        )
    }
}

// "CARGAR PARTIDA": lee el slot; si existe, fija el spawn en la posición guardada
// (re-armando las compuertas como un teletransporte) y restaura el estado. Devuelve true
// si cargó. El menú hace fallback al spawn de la escuela si el slot está vacío.
fun WorldMapViewModel.loadGame(context: Context, slot: Int): Boolean {
    val data = SaveGameRepository(context).load(slot) ?: return false
    campaignSlot = slot
    campaignSchoolId = data.schoolId
    setStorySpawn(data.lat, data.lon)   // re-descarga el mundo en la posición guardada
    restoreSaveData(data)
    return true
}

// ─── OBJETIVOS DE CAMPAÑA ─────────────────────────────────────────────────────
// Fija el objetivo activo (lo llama MainActivity al COMENZAR una campaña nueva).
fun WorldMapViewModel.setCampaignObjective(objective: ovh.gabrielhuav.pow.domain.models.CampaignObjective?) {
    _uiState.update { it.copy(currentObjective = objective, objectiveDone = false) }
}

// Comprueba si el jugador llegó al objetivo (lo llama el game loop). Al entrar en el radio
// de llegada, marca el objetivo como cumplido y avisa por el HUD.
fun WorldMapViewModel.checkObjectiveProgress(location: GeoPoint) {
    val s = _uiState.value
    val obj = s.currentObjective ?: return
    if (s.objectiveDone) return
    val dLat = location.latitude - obj.targetLat
    val dLon = location.longitude - obj.targetLon
    // Conversión grados→metros aprox. (1° lat ≈ 111_320 m; lon corregido por latitud).
    val mLat = dLat * 111_320.0
    val mLon = dLon * 111_320.0 * kotlin.math.cos(Math.toRadians(obj.targetLat))
    val dist = kotlin.math.sqrt(mLat * mLat + mLon * mLon)
    if (dist <= obj.arriveRadiusMeters) {
        _uiState.update { it.copy(objectiveDone = true, interactionPrompt = "✅ Objetivo cumplido: ${obj.title}") }
    }
}
