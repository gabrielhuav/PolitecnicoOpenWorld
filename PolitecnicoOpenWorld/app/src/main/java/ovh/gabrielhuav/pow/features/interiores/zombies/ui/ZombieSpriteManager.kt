// features/interiores/zombies/ui/ZombieSpriteManager.kt
package ovh.gabrielhuav.pow.features.interiores.zombies.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import ovh.gabrielhuav.pow.domain.models.zombie.ZombieType

object ZombieSpriteManager {
    private const val TAG = "ZombieSpriteMgr"
    private const val NORMAL_FRAME_COUNT = 9
    private const val STALKER_WALK_COUNT = 4
    private const val STALKER_ATTACK_COUNT = 4

    private val cache = LruCache<String, ImageBitmap>(32)
    private var loggedMissing = false

    @Synchronized
    fun getFrame(
        context: Context,
        type: ZombieType,
        isAttacking: Boolean,
        frameIndexZeroBased: Int
    ): ImageBitmap? {
        val assetPath = when (type) {
            ZombieType.NORMAL -> {
                val i = (frameIndexZeroBased % NORMAL_FRAME_COUNT) + 1
                "ZOMBIES_MOD/z_walk/z_walk_$i.webp"
            }
            ZombieType.STALKER -> {
                if (isAttacking) {
                    val suffix = when (frameIndexZeroBased % STALKER_ATTACK_COUNT) {
                        0 -> "A"; 1 -> "B"; 2 -> "C"; else -> "D"
                    }
                    "ZOMBIES_MOD/z_walk/Z5$suffix.png"
                } else {
                    val i = (frameIndexZeroBased % STALKER_WALK_COUNT) + 1
                    "ZOMBIES_MOD/z_walk/Z$i.png"
                }
            }
        }

        cache.get(assetPath)?.let { return it }
        return try {
            context.assets.open(assetPath).use {
                val bmp = BitmapFactory.decodeStream(it)?.asImageBitmap()
                if (bmp != null) cache.put(assetPath, bmp)
                bmp
            }
        } catch (e: Exception) {
            if (!loggedMissing) {
                Log.e(TAG, "No se pudo cargar $assetPath — revisa la ruta del asset. ${e.message}")
                loggedMissing = true
            }
            null
        }
    }

    // OPT memoria gama baja (≤2 GB): libera los frames de zombi cacheados bajo presión de
    // memoria (MainActivity.onTrimMemory). Se vuelven a cargar del asset bajo demanda.
    @Synchronized
    fun clearCaches() {
        cache.evictAll()
    }
}
