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
        TeleportZone("ESCOM", 19.504505, -99.146911),
        TeleportZone("Plaza Torres", 19.506750, -99.144139),
        TeleportZone("FES ARAGON", 19.475167, -99.047444),
        TeleportZone("Coyote de Nezahualcóyotl", 19.399806, -99.028167),
        TeleportZone("Shine CTO", 19.459049, -99.163251)
    )
}