package ovh.gabrielhuav.pow.features.streetfighter.ui

import ovh.gabrielhuav.pow.domain.models.streetfighter.SfConstants

/**
 * El fondo cubre el Canvas real, aunque la escena de 382×224 deje márgenes en una pantalla ancha.
 * Se conserva el zoom de cada mapa como mínimo; los actores siguen usando la escala de escena.
 * Son cálculos escalares para no añadir objetos a cada cuadro de dibujo.
 */
internal fun sfBackgroundScale(
    canvasWidth: Float,
    canvasHeight: Float,
    frameWidth: Int,
    frameHeight: Int,
    sceneScale: Float,
    zoom: Float,
): Float = maxOf(
    SfConstants.SCENE_HEIGHT * sceneScale / frameHeight * zoom,
    canvasWidth / frameWidth,
    canvasHeight / frameHeight,
)

/**
 * Limita el paneo, salto o sacudida para que un fondo ya dimensionado con COVER no exponga negro.
 * Truncar un offset negativo hacia cero mantiene cubierto el último píxel del borde contrario.
 */
internal fun sfBackgroundOffset(
    canvasSize: Float,
    backgroundSize: Int,
    desiredOffset: Float,
): Int = desiredOffset.coerceIn((canvasSize - backgroundSize).coerceAtMost(0f), 0f).toInt()
