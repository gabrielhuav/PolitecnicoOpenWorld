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
        TeleportZone("ESCOM", 19.504603, -99.145985),
        TeleportZone("Plaza Torres", 19.506750, -99.144139),
        TeleportZone("FES ARAGON", 19.475167, -99.047444),
        TeleportZone("Coyote de Nezahualcóyotl", 19.399806, -99.028167),
        TeleportZone("Shine CTO", 19.459049, -99.163251),
        TeleportZone("Deportivo - Campo Béisbol", 19.494200, -99.129200),
        TeleportZone("Deportivo - Campo Fútbol", 19.492800, -99.127800),
        TeleportZone("Estadio Azteca", 19.302889, -99.150460)
    )
}