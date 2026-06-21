package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import ovh.gabrielhuav.pow.domain.models.map.Npc

/**
 * Gestor de sprites de zombis para el mapa global (open world).
 * Carga los assets de SPRITES/ZOMBIE y los cachea para el renderizado de NPCs.
 */
object MapZombieSpriteManager {
    private const val WALK_FRAME_COUNT = 9
    private val drawableCache = object : LruCache<String, BitmapDrawable>(32) {
        override fun sizeOf(key: String, value: BitmapDrawable): Int = value.bitmap.allocationByteCount / 1024
    }

    /**
     * Obtiene un Drawable para el zombi en el mapa global.
     * @param context Contexto de Android.
     * @param npc El NPC zombi (se usa para determinar si se está moviendo y la dirección).
     * @param timeMs Tiempo actual en milisegundos para la animación.
     * @param scale Factor de escala para el sprite.
     */
    @Synchronized
    fun getZombieDrawable(
        context: Context,
        npc: Npc,
        timeMs: Long,
        scale: Float
    ): BitmapDrawable? {
        val isMoving = npc.speed > 0 || npc.isMoving
        val frameIndex = if (isMoving) {
            ((timeMs / 220L) % WALK_FRAME_COUNT).toInt()
        } else {
            0 // Frame estático cuando no se mueve
        }

        val assetPath = "SPRITES/ZOMBIE/z_walk_${frameIndex + 1}.webp"
        val roundedScale = Math.round(scale * 20f) / 20f
        // El ROL entra en la clave: cada rol es un palette swap (tinte) distinto del MISMO asset,
        // así no gastamos RAM en sprites nuevos y cada tinte se cachea por separado.
        val cacheKey = "${assetPath}_${npc.facingRight}_${roundedScale}_${npc.zombieRole.name}"

        drawableCache.get(cacheKey)?.let { return it }

        return try {
            context.assets.open(assetPath).use { inputStream ->
                val originalBmp = BitmapFactory.decodeStream(inputStream) ?: return null

                // Escala + espejeo
                val matrix = android.graphics.Matrix()
                matrix.postScale(roundedScale, roundedScale)
                if (!npc.facingRight) {
                    matrix.postScale(-1f, 1f)
                }
                val scaledBmp = Bitmap.createBitmap(
                    originalBmp, 0, 0, originalBmp.width, originalBmp.height, matrix, true
                )

                // PALETTE SWAP por rol: tinte vía ColorMatrix (multiplica canales RGB) sobre el
                // sprite. NORMAL no se tiñe.
                val cm = colorMatrixFor(npc.zombieRole)
                val finalBmp = if (cm == null) scaledBmp else {
                    val tinted = Bitmap.createBitmap(scaledBmp.width, scaledBmp.height, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(tinted)
                    val paint = android.graphics.Paint().apply {
                        colorFilter = android.graphics.ColorMatrixColorFilter(cm)
                        isAntiAlias = true
                    }
                    canvas.drawBitmap(scaledBmp, 0f, 0f, paint)
                    tinted
                }

                val drawable = BitmapDrawable(context.resources, finalBmp)
                drawableCache.put(cacheKey, drawable)
                drawable
            }
        } catch (e: Exception) {
            null
        }
    }

    // Matriz de color (multiplica R,G,B) por rol. null = sin tinte (NORMAL).
    private fun colorMatrixFor(role: ovh.gabrielhuav.pow.domain.models.map.ZombieRole): android.graphics.ColorMatrix? {
        fun scaleMatrix(r: Float, g: Float, b: Float) = android.graphics.ColorMatrix(floatArrayOf(
            r, 0f, 0f, 0f, 0f,
            0f, g, 0f, 0f, 0f,
            0f, 0f, b, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        return when (role) {
            ovh.gabrielhuav.pow.domain.models.map.ZombieRole.RUNNER -> scaleMatrix(1.5f, 0.55f, 0.55f) // rojizo
            ovh.gabrielhuav.pow.domain.models.map.ZombieRole.TANK   -> scaleMatrix(0.45f, 0.85f, 0.45f) // verdoso oscuro
            ovh.gabrielhuav.pow.domain.models.map.ZombieRole.SCOUT  -> scaleMatrix(1.4f, 1.25f, 0.35f) // amarillento
            else -> null
        }
    }

    fun clearCaches() {
        drawableCache.evictAll()
    }
}
