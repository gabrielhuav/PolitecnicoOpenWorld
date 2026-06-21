package ovh.gabrielhuav.pow.domain.models.map

data class ActiveCollectible(
    val id: String,
    val name: String,
    val description: String,
    val assetPath: String,
    val latitude: Double,
    val longitude: Double
)