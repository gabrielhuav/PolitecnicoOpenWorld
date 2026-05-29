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

    /**
     * Verifica si una coordenada GPS global cae estrictamente
     * dentro de los límites exactos de la imagen renderizada.
     */
    fun contains(point: GeoPoint): Boolean {
        // 1. Diferencia en grados globales
        val dLat = point.latitude - location.latitude
        val dLon = point.longitude - location.longitude

        // 2. Convertir grados globales a metros físicos
        val earthRadius = 6378137.0
        val dyGlobalMeters = dLat * (Math.PI / 180.0) * earthRadius
        val dxGlobalMeters = dLon * (Math.PI / 180.0) * (earthRadius * Math.cos(Math.PI * location.latitude / 180.0))

        // 3. Deshacer la rotación del asset (Rotar en sentido inverso)
        val angleRad = Math.toRadians(-rotationAngle.toDouble())
        val dxLocalMeters = dxGlobalMeters * Math.cos(angleRad) - dyGlobalMeters * Math.sin(angleRad)
        val dyLocalMeters = dxGlobalMeters * Math.sin(angleRad) + dyGlobalMeters * Math.cos(angleRad)

        // 4. Convertir los metros locales a coordenadas normalizadas [0.0 a 1.0]
        val actualWidth = baseWidthMeters * scaleFactor
        val actualHeight = baseHeightMeters * scaleFactor

        val localX = (dxLocalMeters / actualWidth) + 0.5
        val localY = 0.5 - (dyLocalMeters / actualHeight) // Y se invierte porque en mapas el Norte (arriba) es positivo

        // 5. Si localX y localY están entre 0.0 y 1.0, estás pisando exactamente un píxel de la imagen
        return localX in 0.0..1.0 && localY in 0.0..1.0
    }
    /**
     * Convierte un GeoPoint global (ej. posición del jugador) a coordenadas
     * locales [0..1] relativas a este edificio.
     */
    fun toLocalCoordinates(globalPoint: GeoPoint): Pair<Float, Float> {
        val earthRadius = 6378137.0

        // 1. Calcular la diferencia en lat/lon respecto al centro del edificio
        val dLat = globalPoint.latitude - location.latitude
        val dLon = globalPoint.longitude - location.longitude

        // 2. Convertir grados a metros basándonos en la aproximación de la Tierra
        val rotatedDy = dLat * (Math.PI / 180.0) * earthRadius
        val rotatedDx = dLon * (Math.PI / 180.0) * (earthRadius * cos(Math.PI * location.latitude / 180.0))

        // 3. Deshacer la rotación (usando el ángulo negativo)
        val angleRad = Math.toRadians(-rotationAngle.toDouble())
        val dxMeters = rotatedDx * cos(angleRad) - rotatedDy * sin(angleRad)
        val dyMeters = rotatedDx * sin(angleRad) + rotatedDy * cos(angleRad)

        // 4. Deshacer la escala y centrar de 0 a 1
        val localX = (dxMeters / (baseWidthMeters * scaleFactor)) + 0.5
        val localY = 0.5 - (dyMeters / (baseHeightMeters * scaleFactor))

        return Pair(localX.toFloat(), localY.toFloat())
    }
}