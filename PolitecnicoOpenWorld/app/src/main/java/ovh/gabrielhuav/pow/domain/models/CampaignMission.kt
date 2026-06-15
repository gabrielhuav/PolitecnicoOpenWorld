package ovh.gabrielhuav.pow.domain.models

// Objetivo de la campaña (Modo Historia). El widget de Objetivos muestra `title` y la
// distancia al destino; al entrar en `arriveRadiusMeters` del destino, el objetivo se
// marca como cumplido (ver WorldMapViewModel.checkObjectiveProgress).
data class CampaignObjective(
    val id: String,
    val title: String,
    val description: String,
    val targetLat: Double,
    val targetLon: Double,
    val arriveRadiusMeters: Double = 60.0
)

// Catálogo de misiones/objetivos de la campaña. Por ahora solo la Misión 1.
object MissionCatalog {
    // Misión 1: ir de ESCOM a la ENCB.
    // ⚠️ Coordenadas APROXIMADAS de la ENCB — ajústalas al punto exacto que quieras.
    val IR_ENCB = CampaignObjective(
        id = "ir_encb",
        title = "Ve a la ENCB",
        description = "Dirígete a la ENCB para investigar el origen del brote.",
        targetLat = 19.498600,
        targetLon = -99.148900
    )

    private val all = listOf(IR_ENCB)

    // Objetivo con el que arranca una campaña nueva.
    val first: CampaignObjective = IR_ENCB

    fun byId(id: String?): CampaignObjective? = all.firstOrNull { it.id == id }
}
