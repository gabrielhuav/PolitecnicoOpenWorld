package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import ovh.gabrielhuav.pow.domain.models.ai.PrankedyAnimState

/**
 * Gestor de sprites para el NPC compañero Prankedy.
 *
 * Patrón: idéntico a [MapZombieSpriteManager].
 * - Carga frames .webp desde assets/assetsNPC/Prankedy/<carpeta>/<prefijo>#.webp
 * - Indexación sin ceros: p_idle_1.webp, p_idle_2.webp, … p_idle_10.webp
 * - Caché LRU de BitmapDrawable para evitar recargas innecesarias.
 * - Fallback: si un asset falta → null (el caller usa el indicador de emergencia).
 */
object PrankedySpriteManager {

    // ── Definición de animaciones ─────────────────────────────────────────────
    private data class AnimDef(val folder: String, val prefix: String, val frameCount: Int)

    private val ANIM_MAP = mapOf(
        PrankedyAnimState.IDLE       to AnimDef("p_idle",      "p_idle_",       3),
        PrankedyAnimState.WALK       to AnimDef("p_walk",      "p_walk_",       9),
        PrankedyAnimState.RUN        to AnimDef("p_run",       "p_run_",        8),
        PrankedyAnimState.RUN_TANQUE to AnimDef("p_run_tanque","p_run_tanque_", 9),
        PrankedyAnimState.ATTACK     to AnimDef("p_attack",     "p_attack_",      5),
    )

    // ── Caché de Bitmaps crudos (carpeta/frame → Bitmap) ─────────────────────
    // 24 entradas: suficiente para las 5 animaciones × frames típicos
    private val bitmapCache = LruCache<String, Bitmap?>(24)

    // ── Caché de Drawables finales (incluye escala y orientación) ────────────
    // Clave: "<animState>_<frame>_<roundedScale>_<facingRight>"
    private val drawableCache = LruCache<String, BitmapDrawable?>(48)

    // ── Caché del proyectil ───────────────────────────────────────────────────
    // Frames en assets/assetsNPC/Prankedy/p_objeto/p_ob_#.webp
    private val PROJ_FOLDER = "p_objeto"
    private val PROJ_PREFIX = "p_obj_"
    private val PROJ_FRAMES = 3
    private val projDrawableCache = LruCache<String, BitmapDrawable?>(8)

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Obtiene el drawable del sprite de Prankedy para el frame actual.
     *
     * @param animState     Estado de animación a mostrar.
     * @param timeMs        Timestamp actual (usado para calcular el frame).
     * @param scale         Factor de escala (densidad de pantalla / metro a píxel).
     * @param facingRight   Orientación del sprite.
     * @return [BitmapDrawable] listo para usar como icono de marcador osmdroid, o null si
     *         los assets no se cargaron (el caller debe dibujar el indicador de emergencia).
     */
    @Synchronized
    fun getDrawable(
        context: Context,
        animState: PrankedyAnimState,
        timeMs: Long,
        scale: Float,
        facingRight: Boolean
    ): BitmapDrawable? {
        val def = ANIM_MAP[animState] ?: ANIM_MAP[PrankedyAnimState.IDLE]!!
        val frameIndex = ((timeMs / 200L) % def.frameCount).toInt() + 1  // 1-based, sin ceros
        val roundedScale = roundScale(scale)
        val cacheKey = "${animState.name}_${frameIndex}_${roundedScale}_$facingRight"

        drawableCache.get(cacheKey)?.let { return it }

        val bitmap = loadFrame(context, def.folder, def.prefix, frameIndex)
            ?: return null  // asset no encontrado → caller usará indicador de emergencia

        val drawable = buildDrawable(context, bitmap, roundedScale, facingRight)
        drawableCache.put(cacheKey, drawable)
        return drawable
    }

    /**
     * Obtiene el drawable del proyectil animado.
     */
    @Synchronized
    fun getProjectileDrawable(
        context: Context,
        timeMs: Long,
        scale: Float
    ): BitmapDrawable? {
        val frameIndex = ((timeMs / 150L) % PROJ_FRAMES).toInt() + 1
        val roundedScale = roundScale(scale)
        val cacheKey = "PROJ_${frameIndex}_$roundedScale"

        projDrawableCache.get(cacheKey)?.let { return it }

        val bitmap = loadFrame(context, PROJ_FOLDER, PROJ_PREFIX, frameIndex)
            ?: return null

        val drawable = buildDrawable(context, bitmap, roundedScale, facingRight = true)
        projDrawableCache.put(cacheKey, drawable)
        return drawable
    }

    /** Vacía todas las cachés (llamar desde onTrimMemory). */
    @Synchronized
    fun clearCaches() {
        bitmapCache.evictAll()
        drawableCache.evictAll()
        projDrawableCache.evictAll()
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    /** Carga un frame desde assets, con caché interna. */
    private fun loadFrame(context: Context, folder: String, prefix: String, index: Int): Bitmap? {
        val key = "${folder}_${prefix}_$index"
        val cached = bitmapCache.get(key)
        if (cached != null) return cached
        // bitmapCache.get devuelve null tanto si no existe como si el value es null,
        // pero queremos distinguirlos → si ya intentamos y fue null, la clave no estará.
        return try {
            val path = "assetsNPC/Prankedy/$folder/$prefix$index.webp"
            val bmp = context.assets.open(path).use { BitmapFactory.decodeStream(it) }
            bitmapCache.put(key, bmp)  // null si decode falló, igual lo guardamos para no reintentar
            bmp
        } catch (e: Exception) {
            bitmapCache.put(key, null)  // marca como "intentado y fallido"
            null
        }
    }

    /** Construye el BitmapDrawable escalado y orientado. */
    private fun buildDrawable(
        context: Context,
        bitmap: Bitmap,
        roundedScale: Float,
        facingRight: Boolean
    ): BitmapDrawable {
        val matrix = Matrix()
        matrix.postScale(roundedScale, roundedScale)
        if (!facingRight) {
            // Voltear horizontalmente
            matrix.postScale(-1f, 1f, bitmap.width * roundedScale / 2f, 0f)
        }
        val scaled = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        return BitmapDrawable(context.resources, scaled)
    }

    /** Redondea la escala al múltiplo de 0.05 más cercano (reduce variantes de caché). */
    private fun roundScale(scale: Float): Float =
        (Math.round(scale * 20f) / 20f).toFloat()
}
