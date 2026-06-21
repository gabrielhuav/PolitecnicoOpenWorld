package ovh.gabrielhuav.pow.features.map_exterior.ui

import android.content.Context
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PrankedySpriteManager
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.WorldMapState
import kotlin.math.pow

/**
 * Render del NPC especial Prankedy sobre el mapa OSM nativo (extraído de NativeOsmMap.kt
 * para reducir su tamaño; mismo paquete `ui`). Coloca/actualiza los markers de Prankedy
 * (peatón en metros reales), el indicador 🎭 (deshabilitado) y su proyectil, reusando la
 * `nativeDrawableCache` por firma visual. Solo lee `uiState` (MVVM). Llamado por el bloque
 * `update` del osmdroid en `NativeOsmMap`.
 */
internal fun renderPrankedyOnMap(
    view: MapView,
    uiState: WorldMapState,
    context: Context,
    nativeDrawableCache: MutableMap<String, android.graphics.drawable.Drawable>,
    screenDensity: Float,
    timeMs: Long
) {
    val prankedyTag = ovh.gabrielhuav.pow.R.id.route_overlay_tag + 700
    val prankedyProjTag = ovh.gabrielhuav.pow.R.id.route_overlay_tag + 701
    val prankedyIndicatorTag = ovh.gabrielhuav.pow.R.id.route_overlay_tag + 702

    val prankedyLoc = uiState.prankedyLocation

    // Mostramos a Prankedy si tiene ubicación y el jugador NO va en coche (al conducir el
    // compañero "sube" → su asset se oculta; reaparece al bajarse). Esto cubre las fases
    // NOT_HIRED (vagabundo) y HIRED (compañero); en fase DEAD location es null → oculto.
    val shouldShow = prankedyLoc != null && !uiState.isDriving

    val prankedyMarker = (view.getTag(prankedyTag) as? Marker) ?: Marker(view).apply {
        title = "PRANKEDY_MARKER"
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        setInfoWindow(null)
        view.setTag(prankedyTag, this)
        view.overlays.add(this) // al final de overlays (arriba de todo)
    }

    val prankedyIndicatorMarker = (view.getTag(prankedyIndicatorTag) as? Marker) ?: Marker(view).apply {
        title = "PRANKEDY_INDICATOR"
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM) // Flota arriba
        setInfoWindow(null)
        view.setTag(prankedyIndicatorTag, this)
        view.overlays.add(this)
    }

    if (shouldShow) {
        // Moverlo al final siempre (encima de fog y otros markers)
        view.overlays.remove(prankedyMarker)
        view.overlays.add(prankedyMarker)
        view.overlays.remove(prankedyIndicatorMarker)
        view.overlays.add(prankedyIndicatorMarker)

        val metersPerPixel = (40075016.686 * kotlin.math.cos(Math.toRadians(prankedyLoc!!.latitude))) /
                (256.0 * 2.0.pow(view.zoomLevelDouble))
        // Tamaño en METROS reales = peatón (~1.3 m), para igualar a los NPCs normales.
        val exactPixels = ((1.3 / metersPerPixel) * screenDensity).toInt().coerceAtLeast(24)
        // Frame 0-based respetando el intervalo por animación (IDLE va más lento) → la clave
        // de caché coincide con el frame real que devuelve getDrawable (sin regenerar de más).
        val pkFrameIdx = PrankedySpriteManager.currentFrameIndex0(uiState.prankedyAnimState, timeMs)
        val cacheKey = "PRANKEDY_${uiState.prankedyAnimState}_${uiState.prankedyFacingRight}_${pkFrameIdx}_${exactPixels}"

        val icon = nativeDrawableCache.getOrPut(cacheKey) {
            var baseDrawable: android.graphics.drawable.Drawable? = PrankedySpriteManager.getDrawable(context, uiState.prankedyAnimState, timeMs, screenDensity, uiState.prankedyFacingRight)
            baseDrawable = drawHealthBarOnDrawable(context, baseDrawable, uiState.prankedyHealth, false, ovh.gabrielhuav.pow.domain.models.ai.PrankedyManager.MAX_HEALTH)
            baseDrawable?.let { ExactSizeDrawable(it, exactPixels, exactPixels) }
                ?: emojiToDrawable(context, "🎭", exactPixels) // Fallback visible si falla asset
        }
        prankedyMarker.icon = icon
        prankedyMarker.position = prankedyLoc
        prankedyMarker.isEnabled = true
        prankedyMarker.setAlpha(1f)

        // Indicador flotante (🎭) - Aumentado a 80dp
        val indicatorSize = (80 * screenDensity).toInt()
        val indicatorIcon = nativeDrawableCache.getOrPut("PR_IND_$indicatorSize") {
            emojiToDrawable(context, "🎭", indicatorSize)
        }
        prankedyIndicatorMarker.icon = indicatorIcon
        prankedyIndicatorMarker.position = prankedyLoc
        // Indicador 🎭 flotante DESHABILITADO: que Prankedy se vea como un peatón más
        // (mismo tamaño, sin emoji gigante encima).
        prankedyIndicatorMarker.isEnabled = false
        prankedyIndicatorMarker.setAlpha(0f)

    } else {
        prankedyMarker.isEnabled = false
        prankedyMarker.setAlpha(0f)
        prankedyIndicatorMarker.isEnabled = false
        prankedyIndicatorMarker.setAlpha(0f)
    }

    // Proyectil
    val projMarker = (view.getTag(prankedyProjTag) as? Marker) ?: Marker(view).apply {
        title = "PRANKEDY_PROJ_MARKER"
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        setInfoWindow(null)
        view.setTag(prankedyProjTag, this)
        view.overlays.add(this)
    }

    if (uiState.prankedyProjectileActive && uiState.prankedyProjectileStart != null && uiState.prankedyProjectileTarget != null) {
        view.overlays.remove(projMarker)
        view.overlays.add(projMarker)

        val start = uiState.prankedyProjectileStart
        val end = uiState.prankedyProjectileTarget
        val p = uiState.prankedyProjectileProgress

        val currentLat = start.latitude + (end.latitude - start.latitude) * p
        val currentLon = start.longitude + (end.longitude - start.longitude) * p
        projMarker.position = GeoPoint(currentLat, currentLon)

        val mpp = (40075016.686 * kotlin.math.cos(Math.toRadians(currentLat))) /
                (256.0 * 2.0.pow(view.zoomLevelDouble))
        val exactPixels = ((0.6 / mpp) * screenDensity).toInt().coerceAtLeast(8)
        val cacheKey = "PRANKEDY_PROJ_${(timeMs / 100L) % 4}_${exactPixels}"

        val icon = nativeDrawableCache.getOrPut(cacheKey) {
            PrankedySpriteManager.getProjectileDrawable(context, timeMs, screenDensity)
                ?.let { ExactSizeDrawable(it, exactPixels, exactPixels) }
                ?: emojiToDrawable(context, "📦", exactPixels) // Fallback visible
        }
        projMarker.icon = icon
        projMarker.isEnabled = true
        projMarker.setAlpha(1f)
    } else {
        projMarker.isEnabled = false
        projMarker.setAlpha(0f)
    }
}
