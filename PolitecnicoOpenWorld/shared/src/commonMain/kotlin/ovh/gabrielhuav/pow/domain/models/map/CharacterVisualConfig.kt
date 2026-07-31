package ovh.gabrielhuav.pow.domain.models.map

import androidx.compose.ui.graphics.Color

/**
 * Configuración visual que define cómo se "arma" un personaje en tiempo real.
 */

data class CharacterVisualConfig(
    val bodyFolder: String,
    val bodyPrefix: String,
    val hairId: Int,
    val hairColor: Color,    // Color del cabello
    val shirtColor: Color,   // Color de la playera
    val pantsColor: Color    // Color de los pantalones
)