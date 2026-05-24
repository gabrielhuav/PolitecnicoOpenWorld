// features/zombie_minigame/ui/ZombieSpriteManager.kt
package ovh.gabrielhuav.pow.features.zombie_minigame.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

object ZombieSpriteManager {
    private const val TAG = "ZombieSpriteMgr"
    private const val FRAME_COUNT = 9
    private val cache = LruCache<Int, ImageBitmap>(FRAME_COUNT + 2)
    private var loggedMissing = false

    @Synchronized
    fun getFrame(context: Context, frameIndexZeroBased: Int): ImageBitmap? {
        val i = (frameIndexZeroBased % FRAME_COUNT) + 1
        cache.get(i)?.let { return it }
        return try {
            context.assets.open("ZOMBIS_MOD/z_walk/z_walk_$i.webp").use {
                val bmp = BitmapFactory.decodeStream(it)?.asImageBitmap()
                if (bmp != null) cache.put(i, bmp)
                bmp
            }
        } catch (e: Exception) {
            if (!loggedMissing) {
                Log.e(TAG, "No se pudo cargar ZOMBIS_MOD/z_walk/z_walk_$i.webp — revisa la ruta del asset. ${e.message}")
                loggedMissing = true
            }
            null  // ZombieView dibujará un fallback visible
        }
    }
}