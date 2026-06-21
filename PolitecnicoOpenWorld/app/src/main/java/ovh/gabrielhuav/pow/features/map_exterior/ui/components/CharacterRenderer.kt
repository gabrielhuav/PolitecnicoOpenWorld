package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import ovh.gabrielhuav.pow.domain.models.map.CharacterVisualConfig

fun DrawScope.drawDynamicCharacter(
    context: Context, screenX: Float, screenY: Float,
    isMoving: Boolean, facingRight: Boolean, timeMs: Long, visualConfig: CharacterVisualConfig
) {
    val bitmap = CharacterSpriteManager.generateAssembledBitmap(context, visualConfig, isMoving, timeMs) ?: return
    val imageBitmap = bitmap.asImageBitmap()

    drawContext.canvas.save()
    translate(screenX, screenY) {
        val mirrorScale = if (facingRight) 1f else -1f
        scale(scaleX = mirrorScale, scaleY = 1f, pivot = Offset(imageBitmap.width / 2f, imageBitmap.height / 2f)) {
            // Ya no usamos colorFilter aquí, el Bitmap ya viene perfecto
            drawImage(image = imageBitmap)
        }
    }
    drawContext.canvas.restore()
}