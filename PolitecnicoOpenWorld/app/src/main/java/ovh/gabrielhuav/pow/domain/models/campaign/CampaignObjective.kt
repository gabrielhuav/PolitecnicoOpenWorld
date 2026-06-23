package ovh.gabrielhuav.pow.domain.models.campaign

import androidx.annotation.StringRes

// Objetivo de la campaña (Modo Historia). El widget de Objetivos muestra `title` y la
// distancia al destino; al entrar en `arriveRadiusMeters` del destino, el objetivo se
// marca como cumplido (ver WorldMapViewModel.checkObjectiveProgress).
// REFACTOR: extraído de domain/models/CampaignMission.kt a domain/models/campaign/ para
// organizar la campaña por carpetas (objetivos por misión en subcarpetas mission1/, …).
data class CampaignObjective(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val targetLat: Double,
    val targetLon: Double,
    val arriveRadiusMeters: Double = 60.0
)
