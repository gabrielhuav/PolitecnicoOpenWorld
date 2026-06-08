package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.LruCache
import ovh.gabrielhuav.pow.domain.models.PrankedyPhase
import kotlin.math.roundToInt

/**
 * Gestor de sprites de Prankedy. Mismo patrón que CharacterSpriteManager /
 * VehicleSpriteManager: cachea frames base + variantes escaladas en LruCache.
 *
 * Estructura de assets esperada en assets/assetsNPC/Prankedy/:
 * p_walk/p_walk_1.webp ... p_walk_N.webp
 * p_run/p_run_1.webp ... p_run_N.webp
 * p_run_tanque/p_run_tanque_1.webp ... p_run_tanque_N.webp
 * p_atack/p_atack_1.webp ... p_atack_N.webp
 * p_objeto/p_ob_1.webp ... p_ob_N.webp   (frames del proyectil)
 */
object PrankedySpriteManager {
    private const val TAG = "PrankedySpriteMgr"
    private const val BASE_DIR = "assetsNPC/Prankedy"

    // Frames base por animación (se decodifican una sola vez).
    private val idleFrames = arrayOfNulls<Bitmap>(12)   // p_idle
    private val walkFrames = arrayOfNulls<Bitmap>(12)
    private val runFrames = arrayOfNulls<Bitmap>(12)
    private val aggroFrames = arrayOfNulls<Bitmap>(12)  // run_tanque
    private val attackFrames = arrayOfNulls<Bitmap>(12)
    private val projectileFrames = arrayOfNulls<Bitmap>(8)

    // Contadores reales de frames disponibles (se descubren al cargar).
    @Volatile private var idleCount = 0
    @Volatile private var walkCount = 0
    @Volatile private var runCount = 0
    @Volatile private var aggroCount = 0
    @Volatile private var attackCount = 0
    @Volatile private var projCount = 0
    @Volatile private var scanned = false

    // Caché de drawables escalados (clave: animación + frame + escala discreta).
    private val drawableCache: LruCache<String, BitmapDrawable> = LruCache(96)

    /** Escanea los assets una sola vez para saber cuántos frames tiene cada anim. */
    @Synchronized
    private fun scanFrames(context: Context) {
        if (scanned) return

        // La carpeta de idle está directamente en assets/Prankedy/p_idle (NO dentro de assetsNPC).
        // Probamos ambas rutas por compatibilidad.
        // Aseguramos compatibilidad absoluta de rutas en minúsculas/mayúsculas dentro de los assets
        idleCount = probeFrameCount(context, "Prankedy/p_idle", "p_idle_")
        if (idleCount == 0) idleCount = probeFrameCount(context, "assetsNPC/Prankedy/p_idle", "p_idle_")
        if (idleCount == 0) idleCount = probeFrameCount(context, "assetsNPC/Prankedy/p_walk", "p_walk_") // Fallback de seguridad

        walkCount = probeFrameCount(context, "assetsNPC/Prankedy/p_walk", "p_walk_")
        if (walkCount == 0) walkCount = 12 // Forzar un conteo mínimo por defecto si el scan de assets asíncrono se retrasa

        runCount = probeFrameCount(context, "assetsNPC/Prankedy/p_run", "p_run_")
        if (runCount == 0) runCount = 12

        aggroCount = probeFrameCount(context, "assetsNPC/Prankedy/p_run_tanque", "p_run_tanque_")
        attackCount = probeFrameCount(context, "assetsNPC/Prankedy/p_atack", "p_atack_")
        projCount = probeFrameCount(context, "assetsNPC/Prankedy/p_objeto", "p_ob_")
        runCount = probeFrameCount(context, "$BASE_DIR/p_run", "p_run_")
        aggroCount = probeFrameCount(context, "$BASE_DIR/p_run_tanque", "p_run_tanque_")
        attackCount = probeFrameCount(context, "$BASE_DIR/p_atack", "p_atack_")
        projCount = probeFrameCount(context, "$BASE_DIR/p_objeto", "p_ob_")

        scanned = true
        Log.d(TAG, "Frames: idle=$idleCount walk=$walkCount run=$runCount aggro=$aggroCount attack=$attackCount proj=$projCount")
    }

    private fun probeFrameCount(context: Context, dir: String, prefix: String): Int {
        var count = 0
        for (i in 1..20) {
            try {
                context.assets.open("$dir/${prefix}$i.webp").close()
                count = i
            } catch (_: Exception) { break }
        }
        return count
    }

    // ── API PÚBLICA ─────────────────────────────────────────────────────────

    fun frameCount(phase: PrankedyPhase): Int {
        return when (phase) {
            // IDLE_WILD y AVAILABLE usan p_idle (quieto); si no hay idle, fallback a walk.
            PrankedyPhase.IDLE_WILD, PrankedyPhase.AVAILABLE ->
                if (idleCount > 0) idleCount else walkCount.coerceAtLeast(1)
            PrankedyPhase.FOLLOW, PrankedyPhase.ROAMING -> walkCount.coerceAtLeast(1)
            PrankedyPhase.SPRINT -> runCount.coerceAtLeast(1)
            PrankedyPhase.AGGRO_RUN -> aggroCount.coerceAtLeast(1)
            PrankedyPhase.ATTACK -> attackCount.coerceAtLeast(1)
            else -> walkCount.coerceAtLeast(1)
        }
    }

    fun projectileFrameCount(): Int = projCount.coerceAtLeast(1)

    /**
     * Devuelve el drawable de Prankedy para el estado y frame dados, escalado al
     * zoom actual. Misma mecánica que VehicleSpriteManager.
     */
    @Synchronized
    fun getFrame(
        context: Context,
        phase: PrankedyPhase,
        frameIndex: Int,
        facingRight: Boolean,
        zoomScale: Float
    ): Drawable? {
        scanFrames(context)
        // Pasamos el context a animInfo
        val (frames, dir, prefix, count) = animInfo(context, phase)

        if (count <= 0) return null

        val idx = frameIndex % count
        val discreteZoom = (zoomScale / 0.05f).roundToInt()
        val cacheKey = "${phase.name}_${idx}_${facingRight}_$discreteZoom"

        drawableCache.get(cacheKey)?.let { return it }

        // Cargar frame base si no existe.
        if (frames[idx] == null) {
            val fileName = "$dir/${prefix}${idx + 1}.webp"
            try {
                context.assets.open(fileName).use { stream ->
                    frames[idx] = BitmapFactory.decodeStream(stream)
                }
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo cargar $fileName: ${e.message}")
                return null
            }
        }
        val base = frames[idx] ?: return null

        // Escalar + espejear.
        val w = (base.width * zoomScale).roundToInt().coerceAtLeast(1)
        val h = (base.height * zoomScale).roundToInt().coerceAtLeast(1)
        val matrix = android.graphics.Matrix().apply {
            postScale(zoomScale, zoomScale)
            if (!facingRight) postScale(-1f, 1f, w / 2f, h / 2f)
        }
        val scaled = Bitmap.createBitmap(base, 0, 0, base.width, base.height, matrix, true)
        val result = BitmapDrawable(context.resources, scaled)
        drawableCache.put(cacheKey, result)

        return result
    }

    /** Drawable del proyectil (broma en el aire). */
    @Synchronized
    fun getProjectileFrame(context: Context, frameIndex: Int, zoomScale: Float): Drawable? {
        scanFrames(context)
        if (projCount <= 0) return null

        val idx = frameIndex % projCount
        val discreteZoom = (zoomScale / 0.05f).roundToInt()
        val cacheKey = "PROJ_${idx}_$discreteZoom"

        drawableCache.get(cacheKey)?.let { return it }

        if (projectileFrames[idx] == null) {
            val fileName = "$BASE_DIR/p_objeto/p_ob_${idx + 1}.webp"
            try {
                context.assets.open(fileName).use { stream ->
                    projectileFrames[idx] = BitmapFactory.decodeStream(stream)
                }
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo cargar proyectil $fileName: ${e.message}")
                return null
            }
        }
        val base = projectileFrames[idx] ?: return null
        val w = (base.width * zoomScale).roundToInt().coerceAtLeast(1)
        val h = (base.height * zoomScale).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(base, w, h, false)
        val result = BitmapDrawable(context.resources, scaled)
        drawableCache.put(cacheKey, result)

        return result
    }

    @Synchronized
    fun clearCaches() {
        drawableCache.evictAll()
        idleFrames.fill(null)
        walkFrames.fill(null)
        runFrames.fill(null)
        aggroFrames.fill(null)
        attackFrames.fill(null)
        projectileFrames.fill(null)
        scanned = false
    }

    // ── INTERNOS ────────────────────────────────────────────────────────────

    private data class AnimInfo(
        val frames: Array<Bitmap?>,
        val dir: String,
        val prefix: String,
        val count: Int
    )

    // Agregamos Context como parámetro para poder pasárselo a probeExists
    private fun animInfo(context: Context, phase: PrankedyPhase): AnimInfo = when (phase) {
        // ── IDLE: Prankedy quieto. Assets exclusivos en Prankedy/p_idle. ──
        PrankedyPhase.IDLE_WILD, PrankedyPhase.AVAILABLE -> {
            if (idleCount > 0) {
                // Pasamos el context de manera segura
                val dir = if (probeExists(context, "Prankedy/p_idle")) "Prankedy/p_idle" else "$BASE_DIR/p_idle"
                AnimInfo(idleFrames, dir, "p_idle_", idleCount)
            } else {
                // Fallback: si no hay idle, usa walk pero SIN moverse.
                AnimInfo(walkFrames, "$BASE_DIR/p_walk", "p_walk_", walkCount)
            }
        }
        PrankedyPhase.FOLLOW, PrankedyPhase.ROAMING ->
            AnimInfo(walkFrames, "$BASE_DIR/p_walk", "p_walk_", walkCount)
        PrankedyPhase.SPRINT ->
            AnimInfo(runFrames, "$BASE_DIR/p_run", "p_run_", runCount)
        PrankedyPhase.AGGRO_RUN ->
            AnimInfo(aggroFrames, "$BASE_DIR/p_run_tanque", "p_run_tanque_", aggroCount)
        PrankedyPhase.ATTACK ->
            AnimInfo(attackFrames, "$BASE_DIR/p_atack", "p_atack_", attackCount)
        else ->
            AnimInfo(walkFrames, "$BASE_DIR/p_walk", "p_walk_", walkCount)
    }

    // Ya no usamos variables globales, recibimos el context inyectado cuando se necesita
    private fun probeExists(context: Context, dir: String): Boolean {
        return try {
            val list = context.assets.list(dir)
            list != null && list.isNotEmpty()
        } catch (_: Exception) { false }
    }
}