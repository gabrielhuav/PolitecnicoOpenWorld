package ovh.gabrielhuav.pow.domain.usecases

import kotlin.math.cos
import kotlin.math.sin

/**
 * Coordenada en el plano cartesiano local [0.0, 1.0] de la textura de un edificio.
 */
data class LocalCoordinate(
    val x: Float,
    val y: Float
) {
    /**
     * ¿La coordenada cae dentro de la textura (con tolerancia)?
     * @param margin tolerancia (0.15f = 15%) para puntear orillas un poco fuera del polígono
     *               estricto, compensando la distorsión curva del mapa (paridad con el legacy ±0.15).
     */
    fun isValid(margin: Float = 0.15f): Boolean {
        val min = -margin
        val max = 1.0f + margin
        return x in min..max && y in min..max
    }
}

/**
 * Caso de uso **PURO** (sin dependencias de Android / OSMDroid): convierte un punto GPS global a la
 * coordenada local normalizada de un edificio.
 *
 * Math: proyección esférica→cartesiana con radio ecuatorial de la Tierra (6 378 137 m), compensación
 * por coseno en la longitud (corrige la convergencia de meridianos), rotación inversa para alinear con
 * la textura, y normalización por `baseWidthMeters*scaleX` / `baseHeightMeters*scaleY`. Es EXACTAMENTE
 * la misma fórmula que `domain.models.map.Landmark.toLocalCoordinates`, extraída aquí para desacoplarla
 * de OSMDroid y poder probarla con números puros. (Se usan parámetros primitivos en vez de duplicar los
 * modelos `GeoPoint`/`Landmark` que ya existen en el proyecto.)
 *
 * ⚠️ NO alterar la matemática: cualquier cambio aquí mueve TODOS los nodos ya capturados/exportados.
 */
class CalculateLocalCoordinatesUseCase {

    operator fun invoke(
        // Punto GPS a convertir (p. ej. la ubicación del jugador/cursor)
        pointLat: Double,
        pointLon: Double,
        // Centro del edificio de referencia + su orientación y tamaño en metros
        centerLat: Double,
        centerLon: Double,
        rotationAngle: Float,
        baseWidthMeters: Float,
        baseHeightMeters: Float,
        scaleX: Float = 1.0f,
        scaleY: Float = 1.0f
    ): LocalCoordinate {
        val earthRadius = 6378137.0 // radio ecuatorial de la Tierra (m)

        // 1. Diferencial en grados respecto al centro del edificio
        val dLat = pointLat - centerLat
        val dLon = pointLon - centerLon

        // 2. Proyección grados→metros (compensación por coseno en la longitud)
        val latRad = centerLat * (Math.PI / 180.0)
        val rotatedDy = dLat * (Math.PI / 180.0) * earthRadius
        val rotatedDx = dLon * (Math.PI / 180.0) * (earthRadius * cos(latRad))

        // 3. Rotación inversa (alinear el eje con la textura base)
        val angleRad = Math.toRadians(-rotationAngle.toDouble())
        val dxMeters = rotatedDx * cos(angleRad) - rotatedDy * sin(angleRad)
        val dyMeters = rotatedDx * sin(angleRad) + rotatedDy * cos(angleRad)

        // 4. Normalización [0,1] + desplazamiento de origen al top-left.
        //    El eje Y se invierte (Norte = +Y geográfico, pero Y=0 está ARRIBA en la textura 2D).
        val localX = (dxMeters / (baseWidthMeters * scaleX)) + 0.5
        val localY = 0.5 - (dyMeters / (baseHeightMeters * scaleY))

        return LocalCoordinate(localX.toFloat(), localY.toFloat())
    }
}
