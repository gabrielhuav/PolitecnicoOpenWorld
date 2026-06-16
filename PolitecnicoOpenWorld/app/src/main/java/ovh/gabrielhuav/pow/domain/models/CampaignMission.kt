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

    // Escolta: Prankedy te acompaña (fase HIRED) y debes llevarlo a la ESCOM (el "lugar seguro").
    // Solo se activa en campaña y en el vecindario de la ENCB (ver WorldMapPrankedy.maybeSpawnPrankedyCompanion).
    // El destino es la ESCOM (mismo punto donde se oculta la línea GPS y para la música); al
    // entrar en arriveRadiusMeters se marca cumplido.
    val ESCOLTAR_PRANKEDY = CampaignObjective(
        id = "escoltar_prankedy",
        title = "Lleva a Prankedy a la ESCOM",
        description = "Protege a Prankedy y escóltalo hasta la ESCOM (lugar seguro).",
        targetLat = 19.504603,
        targetLon = -99.145985,
        arriveRadiusMeters = 80.0
    )

    private val all = listOf(IR_ENCB, ESCOLTAR_PRANKEDY)

    // Objetivo con el que arranca una campaña nueva.
    val first: CampaignObjective = IR_ENCB

    fun byId(id: String?): CampaignObjective? = all.firstOrNull { it.id == id }
}
