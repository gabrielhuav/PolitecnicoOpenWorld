package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import android.content.Context
import kotlinx.coroutines.flow.update
import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.data.repository.CampaignRepository
import ovh.gabrielhuav.pow.data.repository.GameSaveData
import ovh.gabrielhuav.pow.data.repository.SaveGameRepository
import ovh.gabrielhuav.pow.data.repository.SavedNpc
import ovh.gabrielhuav.pow.domain.models.CarModel
import ovh.gabrielhuav.pow.domain.models.Npc
import ovh.gabrielhuav.pow.domain.models.NpcType
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerSkin

// ─── GUARDADO / CARGA DE LA PARTIDA (MODO HISTORIA) ───────────────────────────
// Extensiones del WorldMapViewModel (sin gemelo miembro: estas funciones son nuevas,
// así que la extensión es la ÚNICA implementación). Construyen/aplican el snapshot
// completo de la sesión (posición, vida, nivel de búsqueda, vehículo, skin y NPCs
// cercanos) usando SaveGameRepository (JSON en almacenamiento interno). El estado
// inmutable se actualiza con _uiState.update { it.copy(...) }; las Views solo emiten
// la intención de guardar/cargar (botón en Opciones / "CARGAR PARTIDA"). Ver doc 02/07.

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
        savedAt = System.currentTimeMillis()
    )
}

// Guarda la partida COMPLETA (JSON) + la partida ligera (escuela, SharedPreferences)
// que habilita "CARGAR PARTIDA" en el menú. Es el guardado MANUAL (botón en Opciones)
// y el AUTO-GUARDADO al salir del mapa.
fun WorldMapViewModel.saveGame(context: Context) {
    val schoolId = campaignSchoolId
    SaveGameRepository(context).save(buildSaveData(schoolId))
    CampaignRepository(context).saveCampaign(schoolId)
}

// Restaura el estado guardado SOBRE el mundo ya en carga (setStorySpawn fija la
// posición/escuela y re-arma las compuertas). Aquí solo restauramos vida/estado/
// vehículo/skin y re-inyectamos los NPCs cercanos como entidades remotas para que
// reaparezcan al instante (la IA los adopta y vuelve a simularlos).
fun WorldMapViewModel.restoreSaveData(data: GameSaveData) {
    playerHealth = data.health.coerceIn(1f, 100f)
    val skin = PlayerSkin.entries.firstOrNull { it.name == data.skin } ?: PlayerSkin.LAZARO
    val model = data.vehicleModel?.let { name -> CarModel.entries.firstOrNull { it.name == name } }
    _uiState.update {
        it.copy(
            wantedLevel = data.wantedLevel,
            isDriving = data.isDriving,
            isDrivingPoliceCar = data.isDrivingPoliceCar,
            currentVehicleModel = model,
            currentVehicleColor = data.vehicleColor,
            selectedSkin = skin
        )
    }
    // Re-inyecta los NPCs guardados como remotos sin dueño (la IA del Host los adopta).
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

// "CARGAR PARTIDA": lee el JSON; si existe, fija el spawn en la posición guardada
// (re-armando las compuertas de carga, como un teletransporte) y restaura el estado.
// Devuelve true si cargó una partida completa; false si no había (el menú hace
// fallback al spawn por defecto de la escuela).
fun WorldMapViewModel.loadGame(context: Context): Boolean {
    val data = SaveGameRepository(context).load() ?: return false
    campaignSchoolId = data.schoolId
    setStorySpawn(data.lat, data.lon)   // re-descarga el mundo en la posición guardada
    restoreSaveData(data)
    return true
}
