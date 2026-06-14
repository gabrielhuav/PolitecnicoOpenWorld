package ovh.gabrielhuav.pow.features.interiores.core.viewmodel

// Tipos COMPARTIDOS del modo Interiores (umbrella `features.interiores`).
// Extraídos de ZombieGameState.kt para DESACOPLAR el minijuego de zombis
// (`interiores.zombies`) de los demás interiores: el metro (`interiores.escom`) y
// el diseñador (`interiores.core.ui`) ya NO dependen del paquete de zombis.
// Reutilizables por cualquier universidad/interior futuro.

// Objetivo activo del Modo Diseñador: la matriz de colisión o los waypoints (puertas).
enum class DesignerTarget { MATRIX, WAYPOINTS }

// Transformación de cámara consciente del zoom (equivalente a ContentScale.Crop):
// desplazamiento + escala aplicados al render del interior.
data class CameraTransform(
    val offsetX: Float,
    val offsetY: Float,
    val scale: Float
)
