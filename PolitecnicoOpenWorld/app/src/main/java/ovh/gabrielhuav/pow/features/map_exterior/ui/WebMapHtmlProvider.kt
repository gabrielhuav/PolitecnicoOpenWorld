package ovh.gabrielhuav.pow.features.map_exterior.ui

internal data class NpcWebPayload(
    val id: String,
    val lat: Double,
    val lng: Double,
    val rot: Float,
    val type: String,
    val imageKey: String? = null,
    val drawable: String? = null,
    val flip: Int? = null,
    val name: String? = null,
    val width: Float? = null,
    val height: Float? = null
)

internal fun buildWebMapHtml(lat: Double, lng: Double, zoom: Int): String = """
<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
    <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
    <style>
        body { margin: 0; padding: 0; background: #aad3df; overflow: hidden; }
        #map-wrapper { position: absolute; top: -50%; left: -50%; width: 200vw; height: 200vh; transform-origin: center center; }
        #map { width: 100%; height: 100%; background: transparent; }
        .leaflet-marker-icon { background: none !important; border: none !important; }
        .npc-c { pointer-events: none; display: flex; align-items: center; justify-content: center; }
    </style>
</head>
<body>
    <div id="map-wrapper"><div id="map"></div></div>
    <script>
        var map = L.map('map', { zoomControl: false, attributionControl: false, dragging: true, maxZoom: 22 }).setView([$lat, $lng], $zoom);
        var currentTileLayer = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{ maxZoom: 22, maxNativeZoom: 18 }).addTo(map);
        var npcMarkers = {};
        var isZooming = false;
        map.on('zoomstart', function() { isZooming = true; });
        map.on('zoomend', function() { isZooming = false; });
        function updateMapView(lat, lng, z) { if (!isZooming) map.setView([lat, lng], z, { animate: false }); }
        function setMapRotation(deg) { var wrapper = document.getElementById('map-wrapper'); if (wrapper) wrapper.style.transform = 'rotate(' + deg + 'deg)'; }
        function changeTileUrl(url) { if (currentTileLayer) currentTileLayer.setUrl(url); }
        function setRoadNetworkReady(ready) { window.roadNetworkReady = ready; }
        function escapeHtml(value) { return String(value).replace(/[&<>"']/g, function(c){ return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]||c; }); }
        var collectibleMarkers = {};

        function updateCollectibles(jsonStr) {
            var data = JSON.parse(jsonStr);
            for (var key in collectibleMarkers) { map.removeLayer(collectibleMarkers[key]); }
            collectibleMarkers = {};
            data.forEach(function(col) {
                var pUrl = 'file:///android_asset/' + col.assetPath;
                var containerSize = 20;
                var iconSize = 14;
                var html = '<div style="position:relative;width:'+containerSize+'px;height:'+containerSize+'px;display:flex;justify-content:center;align-items:center;">' +
                    '<div style="position:absolute;width:100%;height:100%;background:radial-gradient(circle, rgba(255,235,59,0.5) 0%, rgba(255,235,59,0) 60%);border-radius:50%;"></div>' +
                    '<img src="'+pUrl+'" style="position:relative;width:'+iconSize+'px;height:'+iconSize+'px;object-fit:contain;image-rendering:pixelated;">' +
                    '</div>';
                var icon = L.divIcon({ html: html, className: '', iconSize: [containerSize, containerSize], iconAnchor: [containerSize/2, containerSize/2] });
                collectibleMarkers[col.id] = L.marker([col.latitude, col.longitude], { icon: icon, interactive: false }).addTo(map);
            });
        }

        function updateNpcs(data) {
            if (isZooming) return;
            var currentZoom = map.getZoom();
            var isZoomedIn = currentZoom >= 16.5;
            var ids = new Set();
            if (isZoomedIn) ids = new Set(data.map(function(n){ return n.id; }));
            for (var id in npcMarkers) if (!ids.has(id)) { map.removeLayer(npcMarkers[id]); delete npcMarkers[id]; }
            if (!isZoomedIn) return;
            var dynamicScale = Math.max(0.2, Math.min(1.4 * Math.pow(2, currentZoom - 19), 1.4));
            data.forEach(function(npc) {
                var finalW, finalH;
                if (npc.type === 'CAR') { finalW = Math.round(npc.width * dynamicScale); finalH = Math.round(npc.height * dynamicScale); }
                else if (npc.type === 'MODULAR') { var sz = Math.max(16, Math.min(24.0 + ((currentZoom - 18.0) * 8.0), 40)); finalW = sz; finalH = sz; }
                else { finalW = 24; finalH = 24; }
                var nameTagHtml = '';
                if (npc.name) {
                    var safeName = escapeHtml(npc.name);
                    nameTagHtml = '<div style="position:absolute;top:-28px;left:50%;transform:translateX(-50%);color:#D4AF37;background:rgba(0,0,0,0.65);padding:2px 6px;border-radius:4px;font-size:16px;font-weight:bold;white-space:nowrap;text-shadow:1px 1px 0 #000;z-index:100;">'+safeName+'</div>';
                }
                if (npcMarkers[npc.id]) {
                    npcMarkers[npc.id].setLatLng([npc.lat, npc.lng]);
                    var el = npcMarkers[npc.id].getElement();
                    if (el) {
                        var wrapper = el.querySelector('.npc-c');
                        var img = el.querySelector('img');
                        if ((npc.type === 'CAR' || npc.type === 'MODULAR') && img && wrapper) {
                            var cachedImg = window.imgCache ? window.imgCache[npc.imageKey] : '';
                            if (!cachedImg) return;
                            if (img.src !== cachedImg) img.src = cachedImg;
                            wrapper.style.width = finalW + 'px';
                            wrapper.style.height = finalH + 'px';
                            if (npc.flip !== undefined) img.style.transform = 'scaleX(' + npc.flip + ')';
                        } else if (wrapper && npc.type !== 'CAR' && npc.type !== 'MODULAR') {
                            wrapper.style.transform = 'translate(-50%, -50%) rotate(' + npc.rot + 'deg)';
                        }
                    }
                } else {
                    var html = '';
                    if (npc.type === 'CAR' || npc.type === 'MODULAR') {
                        var cachedImg = window.imgCache ? window.imgCache[npc.imageKey] : '';
                        if (!cachedImg) return;
                        var flipStyle = (npc.flip !== undefined) ? 'transform: scaleX(' + npc.flip + ');' : '';
                        html = '<div class="npc-c" style="position:absolute;transform:translate(-50%,-50%);width:'+finalW+'px;height:'+finalH+'px;">'+nameTagHtml+'<img src="'+cachedImg+'" style="width:100%;height:100%;display:block;'+flipStyle+'"></div>';
                    } else {
                        var pUrl = 'file:///android_asset/' + npc.drawable + '.svg';
                        html = '<div class="npc-c" style="position:absolute;transform:translate(-50%,-50%) rotate('+npc.rot+'deg);width:24px;height:24px;">'+nameTagHtml+'<img src="'+pUrl+'" style="width:100%;height:100%;display:block;"></div>';
                    }
                    var icon = L.divIcon({ html: html, className: '', iconSize: [0, 0] });
                    npcMarkers[npc.id] = L.marker([npc.lat, npc.lng], { icon: icon }).addTo(map);
                }
            });
        }
    </script>
</body>
</html>
""".trimIndent()
