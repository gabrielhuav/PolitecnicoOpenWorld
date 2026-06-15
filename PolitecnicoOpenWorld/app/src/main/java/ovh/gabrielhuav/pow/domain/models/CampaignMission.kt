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

    // Escolta: Prankedy te acompaña (fase HIRED) y debes llevarlo a un lugar seguro. Solo se
    // activa en campaña y en el vecindario de la ENCB (ver WorldMapPrankedy.maybeSpawnPrankedyCompanion).
    // arriveRadiusMeters=0 → el widget NO lo auto-marca como cumplido (cierre narrativo, no por llegada).
    val ESCOLTAR_PRANKEDY = CampaignObjective(
        id = "escoltar_prankedy",
        title = "Lleva a un lugar seguro a Prankedy",
        description = "Protege a Prankedy y llévalo a un lugar seguro.",
        targetLat = 19.5001588,
        targetLon = -99.1450298,
        arriveRadiusMeters = 0.0
    )

    private val all = listOf(IR_ENCB, ESCOLTAR_PRANKEDY)

    // Objetivo con el que arranca una campaña nueva.
    val first: CampaignObjective = IR_ENCB

    fun byId(id: String?): CampaignObjective? = all.firstOrNull { it.id == id }
}
