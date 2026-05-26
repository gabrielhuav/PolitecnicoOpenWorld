package ovh.gabrielhuav.pow.domain.models

import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.ai.LandmarkNavGraph
import kotlin.math.cos
import kotlin.math.sin

data class Landmark(
    val id: Long,
    val name: String,
    val location: GeoPoint, // Centro del asset
    val rotationAngle: Float = 0f,
    val assetPath: String,
    val scaleFactor: Float = 1.0f,
    val baseWidthMeters: Float,
    val baseHeightMeters: Float,
    val navGraph: LandmarkNavGraph? = null // ¡El nuevo NavMesh!
) {
    /**
     * Convierte un punto [0..1] del asset a coordenadas geográficas globales,
     * aplicando la escala y la rotación del edificio.
     */
    fun toGlobalGeoPoint(localX: Float, localY: Float): GeoPoint {
        // 1. Calcular distancia en metros desde el centro (0.5, 0.5)
        val dxMeters = (localX - 0.5f) * baseWidthMeters * scaleFactor
        val dyMeters = (0.5f - localY) * baseHeightMeters * scaleFactor // Y invertido para plano cartesiano normal

        // 2. Aplicar rotación
        val angleRad = Math.toRadians(rotationAngle.toDouble())
        val rotatedDx = dxMeters * cos(angleRad) - dyMeters * sin(angleRad)
        val rotatedDy = dxMeters * sin(angleRad) + dyMeters * cos(angleRad)

        // 3. Convertir metros a grados (Aproximación geométrica)
        val earthRadius = 6378137.0
        val dLat = (rotatedDy / earthRadius) * (180.0 / Math.PI)
        val dLon = (rotatedDx / (earthRadius * cos(Math.PI * location.latitude / 180.0))) * (180.0 / Math.PI)

        return GeoPoint(location.latitude + dLat, location.longitude + dLon)
    }
}