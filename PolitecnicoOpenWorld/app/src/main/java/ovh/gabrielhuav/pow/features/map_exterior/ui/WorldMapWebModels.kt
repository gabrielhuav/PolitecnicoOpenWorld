package ovh.gabrielhuav.pow.features.map_exterior.ui
internal data class NpcWebPayload(val id: String, val lat: Double, val lng: Double, val rot: Float, val type: String, val imageKey: String? = null, val drawable: String? = null, val flip: Int? = null, val name: String? = null, val width: Float? = null, val height: Float? = null, val health: Float = 100f, val isDying: Boolean = false)

internal data class LandmarkWebPayload(
    val id: String,
    val lat: Double,
    val lng: Double,
    val rotation: Float,
    val widthMeters: Float,
    val heightMeters: Float,
    val scale: Float,
    val assetPath: String
)
