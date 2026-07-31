package ovh.gabrielhuav.pow.domain.models.campaign

// Escuela seleccionable en el Modo Historia / Campaña.
// El punto de aparición de la campaña usa estas coordenadas. `available`
// marca si la escuela ya es jugable (ESCOM) o sigue en desarrollo (FES Aragón, UAM).
// `displayName` es un nombre propio (no se traduce, ver convención i18n del archivo 09).
data class CampaignSchool(
    val id: String,
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val available: Boolean
)

// Catálogo de escuelas de la campaña. Al habilitar una escuela nueva, basta con
// poner `available = true` (sus coordenadas ya alimentan el spawn del Modo Historia).
object SchoolCatalog {
    val schools = listOf(
        // IPN (ESCOM, Zacatenco): única jugable por ahora (= TeleportCatalog.zones[0]).
        // El `id` se mantiene ("escom") porque alimenta el spawn y el guardado; solo cambia el nombre visible.
        CampaignSchool("escom", "IPN", 19.504603, -99.145985, available = true),
        // En desarrollo: coordenadas listas, pendientes de contenido propio.
        CampaignSchool("fes_aragon", "UNAM", 19.475167, -99.047444, available = false),
        CampaignSchool("uam", "UAM", 19.360139, -99.073389, available = false)
    )

    // Escuela por defecto de la campaña (la primera disponible).
    val default: CampaignSchool = schools.first { it.available }
}
