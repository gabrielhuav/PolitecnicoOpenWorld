package ovh.gabrielhuav.pow.features.interior.viewmodel

import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction
import ovh.gabrielhuav.pow.features.settings.models.ControlType

/**
 * Estado de una screen de interior. Mucho más pequeño que WorldMapState porque
 * solo manejamos: posición del personaje dentro de la imagen, dirección,
 * animación y preferencias de control (heredadas de Settings).
 *
 * Las coordenadas (playerX, playerY) son normalizadas en [0, 1] respecto al
 * tamaño de la imagen de fondo. Esto independiza el sistema de coordenadas del
 * tamaño real de pantalla del dispositivo.
 */
data class InteriorState(
    val playerX: Float = 0.5f,
    val playerY: Float = 0.5f,
    val playerAction: PlayerAction = PlayerAction.IDLE,
    val isFacingRight: Boolean = true,
    val isRunning: Boolean = false,
    val controlType: ControlType = ControlType.JOYSTICK,
    val controlsScale: Float = 1.0f,
    val swapControls: Boolean = false,
    val isLoading: Boolean = true
)