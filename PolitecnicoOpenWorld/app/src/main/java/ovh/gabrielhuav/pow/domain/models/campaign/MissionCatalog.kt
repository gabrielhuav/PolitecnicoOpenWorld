package ovh.gabrielhuav.pow.domain.models.campaign

import ovh.gabrielhuav.pow.domain.models.campaign.mission1.Mission1
import ovh.gabrielhuav.pow.domain.models.campaign.mission2.Mission2

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
    private val all: List<CampaignObjective> = Mission1.objectives + Mission2.objectives

    // Objetivo con el que arranca una campaña nueva.
    val first: CampaignObjective = Mission1.IR_ENCB

    fun byId(id: String?): CampaignObjective? = all.firstOrNull { it.id == id }
}
