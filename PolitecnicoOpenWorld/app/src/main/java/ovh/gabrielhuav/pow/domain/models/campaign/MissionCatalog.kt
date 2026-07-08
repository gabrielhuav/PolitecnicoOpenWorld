package ovh.gabrielhuav.pow.domain.models.campaign

import androidx.annotation.StringRes
import ovh.gabrielhuav.pow.R
import ovh.gabrielhuav.pow.domain.models.campaign.MissionCatalog.all
import ovh.gabrielhuav.pow.domain.models.campaign.mission1.Mission1
import ovh.gabrielhuav.pow.domain.models.campaign.mission2.Mission2
import ovh.gabrielhuav.pow.domain.models.campaign.mission3.Mission3
import ovh.gabrielhuav.pow.domain.models.campaign.side.SideMissions

/**
 * Ficha de una MISIÓN para el SELECTOR de misiones (estilo Witcher: elige qué misión seguir,
 * ve cuáles ya hiciste). `side=true` para futuras misiones SECUNDARIAS. El estado
 * (bloqueada/disponible/activa/completada) NO vive aquí: lo derivan el VM y la UI a partir de
 * `completedMissions` + el objetivo activo (ver WorldMapMissionLog.kt / MissionLogDialog).
 */
data class CampaignMissionInfo(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    // Misión que debe estar COMPLETADA para desbloquear esta (null = disponible desde el inicio).
    val requiresMissionId: String? = null,
    val side: Boolean = false
)

/**
 * Catálogo AGREGADOR de misiones/objetivos de la campaña (Modo Historia). Es la API pública
 * que usa el resto del código (`MissionCatalog.X`); los objetivos y constantes concretos
 * viven por misión en subcarpetas (`mission1/Mission1.kt`, `mission2/Mission2.kt`…). Al añadir
 * una misión: define su objeto Mission#, agrégalo a [all] y re-expón lo que el código externo
 * necesite. REFACTOR: antes todo estaba en domain/models/CampaignMission.kt.
 */
object MissionCatalog {
    // ── Re-exposición de objetivos (Misión 1) para no cambiar los call-sites `MissionCatalog.X` ──
    val IR_ENCB = Mission1.IR_ENCB
    val ESCOLTAR_PRANKEDY = Mission1.ESCOLTAR_PRANKEDY
    val INGRESAR_ESCOM = Mission1.INGRESAR_ESCOM
    val BUSCAR_PISTAS_ESCOM = Mission1.BUSCAR_PISTAS_ESCOM

    // ── Misión 2 · "El rumor" (esconderse / rumor / brote / plática / mochila) ──
    val M2_ESCONDERSE_POLICIA = Mission2.ESCONDERSE_POLICIA
    val M2_PISTA_RUMOR = Mission2.PISTA_RUMOR
    val M2_PISTA_BROTE = Mission2.PISTA_BROTE
    val M2_HABLAR_PRANKEDY = Mission2.HABLAR_PRANKEDY
    val M2_RECUPERAR_MOCHILA = Mission2.RECUPERAR_MOCHILA

    // ── Misión 3 · "Regreso a la ENCB" (viaje / infiltración / asalto interior) ──
    val M3_IR_ENCB = Mission3.IR_ENCB
    val M3_INFILTRARSE = Mission3.INFILTRARSE
    val M3_RECUPERAR_EVIDENCIA = Mission3.RECUPERAR_EVIDENCIA

    // ── SELECTOR DE MISIONES (ids estables; alimentan completedMissions del guardado) ──
    const val MISSION_1_ID = "mission1"
    const val MISSION_2_ID = "mission2"
    const val MISSION_3_ID = "mission3"
    // Misiones SECUNDARIAS (side=true; objetivos/coords en side/SideMissions.kt).
    const val SIDE_1_ID = "side1"
    const val SIDE_2_ID = "side2"
    val missions: List<CampaignMissionInfo> = listOf(
        CampaignMissionInfo(MISSION_1_ID, R.string.mission1_title, R.string.mission1_desc),
        CampaignMissionInfo(MISSION_2_ID, R.string.mission2_title, R.string.mission2_desc, requiresMissionId = MISSION_1_ID),
        CampaignMissionInfo(MISSION_3_ID, R.string.mission3_title, R.string.mission3_desc, requiresMissionId = MISSION_2_ID),
        CampaignMissionInfo(SIDE_1_ID, R.string.side1_title, R.string.side1_desc, requiresMissionId = MISSION_2_ID, side = true),
        CampaignMissionInfo(SIDE_2_ID, R.string.side2_title, R.string.side2_desc, requiresMissionId = MISSION_3_ID, side = true)
    )

    /** Primer objetivo de una misión (para el "TP al objetivo" del Modo Desarrollador). */
    fun firstObjectiveOf(missionId: String): CampaignObjective? = when (missionId) {
        MISSION_1_ID -> Mission1.objectives.firstOrNull()
        MISSION_2_ID -> Mission2.objectives.firstOrNull()
        MISSION_3_ID -> Mission3.objectives.firstOrNull()
        SIDE_1_ID -> SideMissions.S1_RECOGER
        SIDE_2_ID -> SideMissions.S2_IR
        else -> null
    }

    /** Misión (id del selector) a la que pertenece un objetivo activo, o null. */
    fun missionIdForObjective(objectiveId: String?): String? = when {
        objectiveId == null -> null
        objectiveId.startsWith(Mission2.OBJECTIVE_ID_PREFIX) -> MISSION_2_ID
        objectiveId.startsWith(Mission3.OBJECTIVE_ID_PREFIX) -> MISSION_3_ID
        objectiveId.startsWith(SideMissions.S1_PREFIX) -> SIDE_1_ID
        objectiveId.startsWith(SideMissions.S2_PREFIX) -> SIDE_2_ID
        Mission1.objectives.any { it.id == objectiveId } -> MISSION_1_ID
        else -> null
    }

    // ── Re-exposición de constantes (deben seguir siendo const: hay call-sites que las usan
    //    para inicializar otros `const val`, p. ej. WorldMapPrankedy.ESCOM_LAT) ──
    const val ESCOM_DOOR_LAT = Mission1.ESCOM_DOOR_LAT
    const val ESCOM_DOOR_LON = Mission1.ESCOM_DOOR_LON
    const val MISSION1_SPAWN_LAT = Mission1.MISSION1_SPAWN_LAT
    const val MISSION1_SPAWN_LON = Mission1.MISSION1_SPAWN_LON
    const val ESCOM_FORCEWALK_LAT = Mission1.ESCOM_FORCEWALK_LAT
    const val ESCOM_FORCEWALK_LON = Mission1.ESCOM_FORCEWALK_LON
    const val ESCOM_FORCEWALK_RADIUS_M = Mission1.ESCOM_FORCEWALK_RADIUS_M

    // Todos los objetivos de todas las misiones (al añadir misiones, concatena sus listas).
    // Incluye las SECUNDARIAS: byId debe resolver s1_/s2_ al restaurar un guardado.
    private val all: List<CampaignObjective> =
        Mission1.objectives + Mission2.objectives + Mission3.objectives + SideMissions.objectives

    // Objetivo con el que arranca una campaña nueva.
    val first: CampaignObjective = Mission1.IR_ENCB

    fun byId(id: String?): CampaignObjective? = all.firstOrNull { it.id == id }
}
