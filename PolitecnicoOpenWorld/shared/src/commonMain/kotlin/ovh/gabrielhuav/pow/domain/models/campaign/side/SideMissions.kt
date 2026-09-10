package ovh.gabrielhuav.pow.domain.models.campaign.side

import org.jetbrains.compose.resources.StringResource
import ovh.gabrielhuav.pow.shared.recursos.Res
import ovh.gabrielhuav.pow.shared.recursos.obj_s1_entregar_desc
import ovh.gabrielhuav.pow.shared.recursos.obj_s1_entregar_title
import ovh.gabrielhuav.pow.shared.recursos.obj_s1_recoger_desc
import ovh.gabrielhuav.pow.shared.recursos.obj_s1_recoger_title
import ovh.gabrielhuav.pow.shared.recursos.obj_s2_eliminar_desc
import ovh.gabrielhuav.pow.shared.recursos.obj_s2_eliminar_title
import ovh.gabrielhuav.pow.shared.recursos.obj_s2_ir_desc
import ovh.gabrielhuav.pow.shared.recursos.obj_s2_ir_title
import ovh.gabrielhuav.pow.domain.models.campaign.CampaignObjective

/**
 * MISIONES SECUNDARIAS del Modo Historia (CampaignMissionInfo.side = true). Se siguen desde el
 * REGISTRO de misiones (Opciones → "Misiones") como las principales, pero son cortas, sin fases
 * persistidas (el id del objetivo activo ES el estado) y dan DINERO como recompensa.
 * Lógica viva: WorldMapSideMissions.kt. Recompensas: MissionRewards (WorldMapEconomy.kt).
 *
 *  side1 "Suministros médicos" (requiere Misión 2 — ya sabes del brote): recoge un botiquín
 *        (llegada) y entrégaselo al paramédico (llegada). Ambos objetivos con radio > 0:
 *        los cumple checkObjectiveProgress; el tick solo encadena y ambienta.
 *  side2 "Contención en Zacatenco" (requiere Misión 3 — la infección se extiende): ve a la zona
 *        del reporte y ELIMINA a los infectados (zombis REALES en remoteEntities, prefijo
 *        NpcAiManager.SIDE_ZOMBIE_PREFIX → mover zombi activo sin apocalipsis; ver 09).
 */
object SideMissions {

    // Prefijos de ids de objetivo (los usan MissionCatalog.missionIdForObjective y los gates).
    const val S1_PREFIX = "s1_"
    const val S2_PREFIX = "s2_"

    // ─── COORDS FIJAS (X=lon, Y=lat), calles de Zacatenco alrededor del campus ───
    // side1: botiquín (recoger) al noroeste; paramédico (entregar) al sureste (~1 km, invita a ir en coche).
    const val S1_PICKUP_LAT = 19.50840
    const val S1_PICKUP_LON = -99.14930
    const val S1_DELIVER_LAT = 19.49890
    const val S1_DELIVER_LON = -99.13980
    // side2: zona del reporte de infectados, al oeste del campus.
    const val S2_ZONE_LAT = 19.50230
    const val S2_ZONE_LON = -99.15120

    // ─── PARÁMETROS ───
    const val S2_ZOMBIE_COUNT = 5          // infectados a eliminar
    const val S2_ZOMBIE_HEALTH = 35f       // ~4 golpes (PLAYER_PUNCH_DAMAGE=10)
    const val S2_ZOMBIE_SPEED = 0.0000042  // como el zombie del brote de la Misión 2
    const val TRANSITION_MS = 2000L        // pausa entre objetivo cumplido y el siguiente

    // ─── OBJETIVOS ───
    // side1: ambos por LLEGADA (radio > 0) → los completa checkObjectiveProgress solo.
    val S1_RECOGER = CampaignObjective(
        id = "s1_recoger_botiquin",
        titleRes = Res.string.obj_s1_recoger_title,
        descriptionRes = Res.string.obj_s1_recoger_desc,
        targetLat = S1_PICKUP_LAT,
        targetLon = S1_PICKUP_LON,
        arriveRadiusMeters = 25.0
    )
    val S1_ENTREGAR = CampaignObjective(
        id = "s1_entregar_botiquin",
        titleRes = Res.string.obj_s1_entregar_title,
        descriptionRes = Res.string.obj_s1_entregar_desc,
        targetLat = S1_DELIVER_LAT,
        targetLon = S1_DELIVER_LON,
        arriveRadiusMeters = 25.0
    )
    // side2: llegar a la zona (radio > 0) y luego ELIMINAR (radio 0 = narrativo, lo cierra el tick).
    val S2_IR = CampaignObjective(
        id = "s2_ir_zona",
        titleRes = Res.string.obj_s2_ir_title,
        descriptionRes = Res.string.obj_s2_ir_desc,
        targetLat = S2_ZONE_LAT,
        targetLon = S2_ZONE_LON,
        arriveRadiusMeters = 35.0
    )
    val S2_ELIMINAR = CampaignObjective(
        id = "s2_eliminar_infectados",
        titleRes = Res.string.obj_s2_eliminar_title,
        descriptionRes = Res.string.obj_s2_eliminar_desc,
        targetLat = S2_ZONE_LAT,
        targetLon = S2_ZONE_LON,
        arriveRadiusMeters = 0.0
    )

    // Todos los objetivos secundarios (MissionCatalog.all los necesita para byId/restauración).
    val objectives: List<CampaignObjective> = listOf(S1_RECOGER, S1_ENTREGAR, S2_IR, S2_ELIMINAR)
}
