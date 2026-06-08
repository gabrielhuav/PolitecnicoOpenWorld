package ovh.gabrielhuav.pow.features.map_exterior.ui

import android.webkit.JavascriptInterface
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.WorldMapViewModel

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
    @JavascriptInterface fun notifyLandmarkClick(id: String) {
        try {
            vm.selectLandmark(id.toLong())
        } catch (e: Exception) {
            android.util.Log.e("MapJsBridge", "Error seleccionando landmark: " + id)
        }
    }
}
