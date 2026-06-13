package ovh.gabrielhuav.pow.domain.models

// Estructura que define cómo es un punto de teletransporte
data class TeleportZone(
    val name: String,
    val latitude: Double,
    val longitude: Double
)

// Catálogo global. Aquí agregarás todos tus nuevos destinos.
object TeleportCatalog {
    val zones = listOf(
        TeleportZone("ESCOM", 19.504694, -99.146633),
        TeleportZone("Plaza Torres", 19.506550, -99.144139),
        TeleportZone("FES ARAGON", 19.474867, -99.043343),
        TeleportZone("Coyote de Nezahualcóyotl", 19.399791, -99.028970),
        TeleportZone("Shine CTO", 19.459038, -99.163328),
        TeleportZone("Deportivo - Campo Béisbol", 19.494200, -99.129200),
        TeleportZone("Deportivo - Campo Fútbol", 19.492800, -99.127800),
        TeleportZone("Estadio Azteca", 19.302889, -99.150460),
        TeleportZone("CECyT 9 Bátiz (Voca 9)", 19.453533, -99.175314)
    )
}