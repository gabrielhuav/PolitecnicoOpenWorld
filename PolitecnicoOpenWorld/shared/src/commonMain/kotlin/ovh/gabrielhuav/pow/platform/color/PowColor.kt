package ovh.gabrielhuav.pow.platform.color

/**
 * Equivalente exacto de `android.graphics.Color.rgb(r, g, b)`: empaqueta un color opaco en el
 * `Int` ARGB de siempre (`0xAARRGGBB`).
 *
 * Se escribe a mano en vez de tirar de `androidx.compose.ui.graphics.Color(...).toArgb()` porque
 * el valor **viaja en el modelo** (`Npc.carColor` es un `Int`) y lo consumen los tres renderers de
 * Android tal cual. Manteniendo el tipo y los bits, la migración de `NpcAiManager` a `commonMain`
 * no cambia ni un píxel del color de los coches.
 */
fun colorArgb(rojo: Int, verde: Int, azul: Int): Int =
    (0xFF shl 24) or ((rojo and 0xFF) shl 16) or ((verde and 0xFF) shl 8) or (azul and 0xFF)
