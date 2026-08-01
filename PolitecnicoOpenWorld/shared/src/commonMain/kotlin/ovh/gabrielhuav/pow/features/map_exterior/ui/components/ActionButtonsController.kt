package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.GameAction
import ovh.gabrielhuav.pow.ui.components.ActionButton
import ovh.gabrielhuav.pow.ui.components.ControllerBaseSize

/**
 * 🎮 EL DIAMANTE A/B/X/Y DEL MUNDO ABIERTO — compartido por Android e iOS.
 *
 * Disposición **Xbox**: Y arriba · X izquierda · B derecha · A abajo. Los mismos colores, letras y
 * tamaños que usa el modo conducción y que ya usaba el modo pelea, para que el jugador no tenga que
 * reaprender nada al cambiar de modo.
 *
 * ⚠️ **Este composable NO sabe qué hace cada botón.** Solo avisa de qué se pulsó y si está
 * mantenido; quién lo interpreta es el ViewModel, que cambia el significado según vayas a pie o
 * conduciendo. Por eso puede vivir en `commonMain` aunque el ViewModel siga en `:app`.
 *
 * *(Se movió de `:app` el 2026-07-31, al portar los controles a iOS. De paso se quitó
 * `onClaimCollectiblePressed`, que estaba declarado y no lo usaba nadie.)*
 */
@Composable
fun ActionButtonsController(
    modifier: Modifier = Modifier,
    backgroundAlpha: Float = 0.6f,
    /**
     * Diámetro REAL del diamante. Ver la nota de `JoystickController.tamano`: se cambia el tamaño
     * de verdad y **nunca con `Modifier.scale`**, que dejaría el área táctil donde ya no se ve.
     */
    tamano: Dp = ControllerBaseSize,
    onActionChanged: (GameAction, Boolean) -> Unit,
) {
    Box(
        modifier = modifier
            .size(tamano)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = backgroundAlpha.coerceIn(0f, 1f))),
        contentAlignment = Alignment.Center
    ) {
        // Los botones y el hueco del centro guardan la MISMA proporción que en Android
        // (48 dp de botón sobre 180 dp de diamante), así que encogerlo no cambia la disposición.
        val boton = tamano * PROPORCION_BOTON
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Y - Amarillo
            ActionButton(
                text = "Y",
                color = Color(0xFFF1C40F),
                tamano = boton,
                onHoldEvent = { isPressed -> onActionChanged(GameAction.Y, isPressed) }
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                // X - Azul
                ActionButton(
                    text = "X",
                    color = Color(0xFF3498DB),
                    tamano = boton,
                    onHoldEvent = { isPressed ->
                        // El ViewModel ya centraliza TODA la lógica de interacción (coches, gatos,
                        // vendedores y coleccionables) al recibir GameAction.X.
                        onActionChanged(GameAction.X, isPressed)
                    }
                )

                Spacer(modifier = Modifier.size(boton))

                // B - Rojo (Ataque Especial)
                ActionButton(
                    text = "B",
                    color = Color(0xFFE74C3C),
                    tamano = boton,
                    onHoldEvent = { isPressed -> onActionChanged(GameAction.B, isPressed) }
                )
            }

            // A - Verde (Correr)
            ActionButton(
                text = "A",
                color = Color(0xFF2ECC71),
                tamano = boton,
                onHoldEvent = { isPressed -> onActionChanged(GameAction.A, isPressed) }
            )
        }
    }
}

/** 48 dp de botón sobre 180 dp de diamante: la proporción de Android. */
private const val PROPORCION_BOTON = 48f / 180f
