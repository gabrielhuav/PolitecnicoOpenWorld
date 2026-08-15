package ovh.gabrielhuav.pow.domain.models.campaign

import org.jetbrains.compose.resources.StringResource

// Objetivo de la campaña (Modo Historia). El widget de Objetivos muestra `title` y la
// distancia al destino; al entrar en `arriveRadiusMeters` del destino, el objetivo se
// marca como cumplido (ver WorldMapViewModel.checkObjectiveProgress).
// REFACTOR: extraído de domain/models/CampaignMission.kt a domain/models/campaign/ para
// organizar la campaña por carpetas (objetivos por misión en subcarpetas mission1/, …).
// 🍏 2026-08-14: de `@StringRes Int` a `StringResource` al bajar a `:shared` — `R` es de
// Android. En un Composable se resuelve con `stringResource(obj.titleRes)` igual que antes;
// FUERA de la composición es `getString(...)`, que es **suspend** (ver PowTextos).
data class CampaignObjective(
    val id: String,
    val titleRes: StringResource,
    val descriptionRes: StringResource,
    val targetLat: Double,
    val targetLon: Double,
    val arriveRadiusMeters: Double = 60.0
)
