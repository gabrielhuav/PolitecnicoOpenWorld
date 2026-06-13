package ovh.gabrielhuav.pow.features.map_exterior.ui

import android.webkit.JavascriptInterface
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.WorldMapViewModel
// REFACTOR: estas funciones ahora son extensiones (WorldMapDesigner.kt) → requieren import.
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.moveLandmarkTo
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.selectLandmark
internal class MapJsBridge(private val vm: WorldMapViewModel) {
    @JavascriptInterface fun notifyMapPanStart() { vm.onMapPanStart() }
    @JavascriptInterface fun notifyMapPanEnd() { vm.onMapPanEnd() }
    @JavascriptInterface fun notifyMapZoom(zoom: Double) { vm.onMapZoomChanged(zoom) }
    @JavascriptInterface fun notifyMapClick(latitude: Double, longitude: Double) {
        vm.placeDestinationMarker(latitude, longitude)
    }
    @JavascriptInterface fun notifyCenterForWaypoint(latitude: Double, longitude: Double) {
        vm.placeDestinationMarker(latitude, longitude)
    }
    // ─── MODO DISEÑADOR (lápiz en el renderer web) ───────────────────────────
    @JavascriptInterface fun notifyLandmarkSelected(id: String) {
        id.toLongOrNull()?.let { vm.selectLandmark(it) }
    }
    @JavascriptInterface fun notifyLandmarkMoved(id: String, latitude: Double, longitude: Double) {
        id.toLongOrNull()?.let { vm.moveLandmarkTo(it, latitude, longitude) }
    }
}
