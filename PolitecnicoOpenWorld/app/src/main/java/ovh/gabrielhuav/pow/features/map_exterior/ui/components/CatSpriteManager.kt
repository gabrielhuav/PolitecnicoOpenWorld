package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable

/**
 * Gestor de sprites para los gatos del campus (CAT).
 *
 * Spritesheet: assets/ENTITIES/cat.png
 * Dimensiones: 576 x 384 px → 12 columnas × 8 filas → frame = 48×48 px
 *
 * Formato RPG Maker MV (cada personaje ocupa 3 cols × 4 filas):
 *   Fila 0: mirando abajo   (cols 0-2)
 *   Fila 1: mirando izquierda (cols 0-2)
 *   Fila 2: mirando derecha (cols 0-2)
 *   Fila 3: mirando arriba  (cols 0-2)
 *
 * Animación de caminado (cols por fila): izquierda=0, centro=1, derecha=2
 * Ciclo: 0→1→2→1 (4 pasos)
 */
object CatSpriteManager {

    // Grilla del spritesheet
    private const val SHEET_COLS = 12
    private const val SHEET_ROWS = 8
    private const val FRAME_W = 48   // 576 / 12
    private const val FRAME_H = 48   // 384 / 8

    @Volatile private var spriteSheet: Bitmap? = null
    private val lock = Object()

    // Cache: "row_frameCol_targetPx" → Bitmap escalado listo para usar
    private val frameCache = HashMap<String, Bitmap>(32)

    private fun getSheet(context: Context): Bitmap? {
        if (spriteSheet != null) return spriteSheet
        synchronized(lock) {
            if (spriteSheet != null) return spriteSheet
            return try {
                val stream = context.assets.open("ENTITIES/cat.png")
                val bmp = BitmapFactory.decodeStream(stream)
                stream.close()
                spriteSheet = bmp
                bmp
            } catch (e: Exception) {
                android.util.Log.e("CatSpriteManager", "Error cargando cat.png: ${e.message}")
                null
            }
        }
    }

    /**
     * Devuelve un Bitmap escalado al tamaño [targetPx]×[targetPx] listo para usar como marker.
     * Devuelve null si el spritesheet no pudo cargarse.
     */
    fun getFrameBitmap(context: Context, isMoving: Boolean, timeMs: Long, isFacingRight: Boolean, targetPx: Int): Bitmap? {
        val sheet = getSheet(context) ?: return null

        // Fila: 2=derecha, 1=izquierda
        val row = if (isFacingRight) 2 else 1

        // Columna de animación (ciclo 0-1-2-1)
        val animCycle = intArrayOf(1, 0, 1, 2)
        val frameCol = if (isMoving) animCycle[((timeMs / 200L) % 4).toInt()] else 1

        val safePx = targetPx.coerceIn(16, 120)
        val key = "${row}_${frameCol}_$safePx"

        frameCache[key]?.let { return it }

        return try {
            // 1. Recortar el frame exacto del spritesheet
            val srcBmp = Bitmap.createBitmap(sheet, frameCol * FRAME_W, row * FRAME_H, FRAME_W, FRAME_H)

            // 2. Escalar al tamaño objetivo usando Bitmap.createScaledBitmap (robusto, no usa Matrix)
            val scaled = Bitmap.createScaledBitmap(srcBmp, safePx, safePx, true)
            if (srcBmp != scaled) srcBmp.recycle()

            frameCache[key] = scaled
            scaled
        } catch (e: Exception) {
            android.util.Log.e("CatSpriteManager", "Error extrayendo frame row=$row col=$frameCol: ${e.message}")
            null
        }
    }

    /**
     * Devuelve un BitmapDrawable para usar con OSMDroid Marker.
     * El drawable tiene dimensiones exactas [targetPx]×[targetPx].
     */
    fun getDrawableForMarker(context: Context, isMoving: Boolean, timeMs: Long, isFacingRight: Boolean, targetPx: Int): BitmapDrawable? {
        val bmp = getFrameBitmap(context, isMoving, timeMs, isFacingRight, targetPx) ?: return null
        // Crear un bitmap nuevo con las dimensiones exactas para evitar problemas de densidad
        val output = Bitmap.createBitmap(targetPx, targetPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawBitmap(bmp, 0f, 0f, null)
        val drawable = BitmapDrawable(context.resources, output)
        // CRÍTICO: setear bounds explícitamente para que OSMDroid lo renderice correctamente
        drawable.setBounds(0, 0, targetPx, targetPx)
        return drawable
    }

    /** Libera el spritesheet de memoria (llamar en onTrimMemory) */
    fun clearCache() {
        frameCache.values.forEach { it.recycle() }
        frameCache.clear()
        spriteSheet?.recycle()
        spriteSheet = null
    }
}
