package ovh.gabrielhuav.pow.features.map_exterior.ui
internal fun buildHtml(lat: Double, lng: Double, zoom: Int): String = """
<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
    <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
    <style>
        body { margin: 0; padding: 0; background: #0D0D11; overflow: hidden; }
        /* Wrapper sobredimensionado (300vw x 300vh) y centrado: su círculo inscrito
           cubre la diagonal de la pantalla en cualquier ángulo de rotación, evitando
           ver "huecos"/artefactos al rotar el mapa en pantallas alargadas. */
        #map-wrapper { position: absolute; top: -100%; left: -100%; width: 300vw; height: 300vh; transform-origin: center center; }
        #map { width: 100%; height: 100%; background: transparent; }
        .leaflet-marker-icon { background: none !important; border: none !important; }
        .leaflet-div-icon { background: transparent !important; border: none !important; }
        .lm-c { background: transparent !important; }
        .npc-c { pointer-events: none; display: flex; align-items: center; justify-content: center; }
        @keyframes neonPulse{0%,100%{filter:drop-shadow(0 0 4px gold) drop-shadow(0 0 10px rgba(255,165,0,.45));}50%{filter:drop-shadow(0 0 14px gold) drop-shadow(0 0 28px orange);}}
        @keyframes shimmerSlide{0%{left:-45%;}100%{left:135%;}}
        .lm-door-wrap{overflow:hidden;}
        .lm-door-img{animation:neonPulse 1.1s ease-in-out infinite;}
        .lm-shimmer{position:absolute;top:0;left:-45%;width:35%;height:100%;background:linear-gradient(105deg,transparent,rgba(255,225,70,.65),transparent);animation:shimmerSlide 2.2s linear infinite;pointer-events:none;}
    </style>
</head>
<body>
    <div id="map-wrapper"><div id="map"></div></div>
    <script>
        var map = L.map('map', { 
            zoomControl: false, 
            attributionControl: false, 
            dragging: false, 
            touchZoom: false,
            doubleClickZoom: false,
            scrollWheelZoom: false,
            boxZoom: false,
            keyboard: false,
            maxZoom: 22 
        }).setView([$lat, $lng], $zoom);
        var currentTileLayer = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{ maxZoom: 22, maxNativeZoom: 18 }).addTo(map);
        map.createPane('landmarkPane');
        map.getPane('landmarkPane').style.zIndex = 300;
        map.createPane('doorPane');
        map.getPane('doorPane').style.zIndex = 450;
        map.getPane('doorPane').style.pointerEvents = 'none';

        
        var npcMarkers = {};
        var collectibleMarkers = {};
        var landmarkMarkers = {};

        var isZooming = false;
        var isExplorationMode = false;
        
        map.on('zoomstart', function() { isZooming = true; });
        map.on('zoomend', function() { isZooming = false; });
        map.on('zoom', function() { resizeLandmarks(); });

        map.on('dragstart', function() {
            isExplorationMode = true;
            if (window.Android && window.Android.notifyMapPanStart) window.Android.notifyMapPanStart();
        });
        map.on('dragend', function() {
            if (window.Android && window.Android.notifyMapPanEnd) window.Android.notifyMapPanEnd();
        });
        
        function updateMapView(lat, lng, z) { if (!isZooming && !isExplorationMode) map.setView([lat, lng], z, { animate: false }); }
        
        function setDesignerMode(isDesigner) {
            if (isDesigner) {
                map.dragging.enable();
                map.touchZoom.enable();
                map.scrollWheelZoom.enable();
            } else {
                map.dragging.disable();
                map.touchZoom.disable();
                map.scrollWheelZoom.disable();
            }
        }
        
        function setMapRotation(deg) { var wrapper = document.getElementById('map-wrapper'); if (wrapper) wrapper.style.transform = 'rotate(' + deg + 'deg)'; }
        function changeTileUrl(url) { if (currentTileLayer) currentTileLayer.setUrl(url); }
        function setRoadNetworkReady(ready) { window.roadNetworkReady = ready; }
        function exitExplorationMode() { isExplorationMode = false; }
        function escapeHtml(value) { return String(value).replace(/[&<>"']/g, function(c){ return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]||c; }); }

        function updateLandmarks(jsonStr) {
            var data = JSON.parse(jsonStr);
            var currentIds = new Set(data.map(function(l){ return String(l.id); })); 

            for (var id in landmarkMarkers) {
                if (!currentIds.has(id)) {
                    map.removeLayer(landmarkMarkers[id]);
                    delete landmarkMarkers[id];
                }
            }

            data.forEach(function(lm) {
                var pUrl = 'file:///android_asset/' + lm.assetPath;
                var exactWidthMeters = lm.widthMeters * lm.scale;
                var exactHeightMeters = lm.heightMeters * lm.scale;

                var isDoor = lm.assetPath.indexOf('DOORS/') >= 0;
                var existingPane = landmarkMarkers[lm.id] ? landmarkMarkers[lm.id].options.pane : null;
                var expectedPane = isDoor ? 'doorPane' : 'landmarkPane';
                if (existingPane && existingPane !== expectedPane) {
                    map.removeLayer(landmarkMarkers[lm.id]);
                    delete landmarkMarkers[lm.id];
                }
                if (landmarkMarkers[lm.id]) {
                    landmarkMarkers[lm.id].setLatLng([lm.lat, lm.lng]);
                    var el = landmarkMarkers[lm.id].getElement();
                    if (el) {
                        var wrapper = el.querySelector('.lm-c');
                        if (wrapper) {
                            wrapper.dataset.wMeters = exactWidthMeters;
                            wrapper.dataset.hMeters = exactHeightMeters;
                            wrapper.dataset.rot = lm.rotation;
                            wrapper.dataset.lat = lm.lat;
                        }
                    }
                } else {
                    var html = '<div class="lm-c' + (isDoor ? ' lm-door-wrap' : '') + '" ' +
                               'data-w-meters="' + exactWidthMeters + '" ' +
                               'data-h-meters="' + exactHeightMeters + '" ' +
                               'data-rot="' + lm.rotation + '" ' +
                               'data-lat="' + lm.lat + '" ' +
                               'style="position:absolute; transform: translate(-50%, -50%) rotate('+lm.rotation+'deg); pointer-events: none; z-index: -100;">' +
                               '<img src="'+pUrl+'"' + (isDoor ? ' class="lm-door-img"' : '') + ' style="width:100%; height:100%; display:block; object-fit:fill;">' +
                               (isDoor ? '<div class="lm-shimmer"></div>' : '') +
                               '</div>';

                    var icon = L.divIcon({ html: html, className: '', iconSize: [0,0] });
                    
                    var marker = L.marker([lm.lat, lm.lng], { icon: icon, pane: isDoor ? 'doorPane' : 'landmarkPane', interactive: false }).addTo(map);
                    landmarkMarkers[lm.id] = marker;
                }
            });
            resizeLandmarks();
        }

        function resizeLandmarks() {
            var zoom = map.getZoom();
            var elements = document.querySelectorAll('.lm-c');
            
            for (var i = 0; i < elements.length; i++) {
                var wrapper = elements[i];
                var wMeters = parseFloat(wrapper.dataset.wMeters);
                var hMeters = parseFloat(wrapper.dataset.hMeters);
                var lat = parseFloat(wrapper.dataset.lat);
                var rot = parseFloat(wrapper.dataset.rot);

                var pixelsPerMeter = (256 * Math.pow(2, zoom)) / (40075016 * Math.cos(lat * Math.PI / 180));
                
                var wPx = wMeters * pixelsPerMeter;
                var hPx = hMeters * pixelsPerMeter;

                wrapper.style.width = wPx + 'px';
                wrapper.style.height = hPx + 'px';
                wrapper.style.transform = 'translate(-50%, -50%) rotate(' + rot + 'deg)';
            }
        }

        function updateCollectibles(jsonStr) {
            var data = JSON.parse(jsonStr);
            for (var key in collectibleMarkers) { map.removeLayer(collectibleMarkers[key]); }
            collectibleMarkers = {};

            data.forEach(function(col) {
                var pUrl = 'file:///android_asset/' + col.assetPath;
                var containerSize = 20;
                var iconSize = 14;
                var html = '<div style="position:relative; width:' + containerSize + 'px; height:' + containerSize + 'px; display:flex; justify-content:center; align-items:center;">' +
                    '<div style="position:absolute; width:100%; height:100%; background:radial-gradient(circle, rgba(255,235,59,0.5) 0%, rgba(255,235,59,0) 60%); border-radius:50%;"></div>' +
                    '<img src="' + pUrl + '" style="position:relative; width:' + iconSize + 'px; height:' + iconSize + 'px; object-fit:contain; image-rendering:pixelated;">' +
                '</div>';
                var icon = L.divIcon({ html: html, className: '', iconSize: [containerSize, containerSize], iconAnchor: [containerSize/2, containerSize/2] });
                collectibleMarkers[col.id] = L.marker([col.latitude, col.longitude], { icon: icon, interactive: false, zIndexOffset: 500 }).addTo(map);
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
                    nameTagHtml = '<div style="position:absolute; top:-28px; left:50%; transform:translateX(-50%); color:#D4AF37; background:rgba(0,0,0,0.65); padding:2px 6px; border-radius:4px; font-size:16px; font-weight:bold; white-space:nowrap; text-shadow:1px 1px 0 #000; z-index:100;">' + safeName + '</div>';
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
                            wrapper.style.transform = 'translate(-50%, -50%) rotate(0deg)';
                        }
                    }
                } else {
                    var html = '';
                    if (npc.type === 'CAR' || npc.type === 'MODULAR') {
                        var cachedImg = window.imgCache ? window.imgCache[npc.imageKey] : '';
                        if (!cachedImg) return;
                        var flipStyle = (npc.flip !== undefined) ? 'transform: scaleX(' + npc.flip + ');' : '';
                        html = '<div class="npc-c" style="position:absolute; transform: translate(-50%, -50%); width:'+finalW+'px; height:'+finalH+'px;">' + nameTagHtml + '<img src="'+cachedImg+'" style="width:100%; height:100%; display:block; ' + flipStyle + '"></div>';
                    } else {
                        var pUrl = 'file:///android_asset/' + npc.drawable + '.svg';
                        html = '<div class="npc-c" style="position:absolute; transform: translate(-50%, -50%) rotate(0deg); width:24px; height:24px;">' + nameTagHtml + '<img src="'+pUrl+'" style="width:100%; height:100%; display:block;"></div>';
                    }
                    var icon = L.divIcon({ html: html, className: '', iconSize: [0, 0] });
                    npcMarkers[npc.id] = L.marker([npc.lat, npc.lng], { icon: icon, zIndexOffset: 1000 }).addTo(map);
                }
            });
        }
        var playerMarker = null;
        function updatePlayerMarker(lat, lng, isInFreeNavigation) {
            if (!isInFreeNavigation) {
                if (playerMarker) { map.removeLayer(playerMarker); playerMarker = null; }
                return;
            }
            if (lat === null || lng === null) return;
            if (!playerMarker) {
                var html = '<div style="position:relative; width:40px; height:40px; display:flex; justify-content:center; align-items:center;">' +
                    '<div style="width:20px; height:20px; background:radial-gradient(circle at 30% 30%, #4CAF50, #2E7D32); border-radius:50%; border:3px solid #FFF; box-shadow: 0 2px 8px rgba(0,0,0,0.4);"></div>' +
                    '</div>';
                var icon = L.divIcon({ html: html, className: '', iconSize: [40, 40], iconAnchor: [20, 20] });
                playerMarker = L.marker([lat, lng], { icon: icon, interactive: false, zIndexOffset: 1000 }).addTo(map);
            } else {
                playerMarker.setLatLng([lat, lng]);
            }
        }
        var destinationMarker = null;
        var destinationRoute = null;
        var isPlacingDestinationMarker = false;
        function updateDestinationPlacingMode(isPlacing) {
            isPlacingDestinationMarker = isPlacing;
            var mapElement = document.getElementById('map');
            if (mapElement) {
                mapElement.style.cursor = isPlacing ? 'crosshair' : 'grab';
            }
        }
        function updateDestinationMarker(lat, lng) {
            if (!destinationMarker) {
                var html = '<div style="position:relative; width:32px; height:40px; display:flex; justify-content:center; align-items:flex-start;">' +
                    '<svg width="32" height="40" viewBox="0 0 32 40" xmlns="http://www.w3.org/2000/svg" style="filter: drop-shadow(0px 2px 4px rgba(0,0,0,0.3));">' +
                    '<path d="M16 0C9.4 0 4 5.4 4 12c0 7 12 25 12 25s12-18 12-25c0-6.6-5.4-12-12-12z" fill="#F44336"/>' +
                    '<circle cx="16" cy="12" r="5" fill="#FFF"/>' +
                    '</svg></div>';
                var icon = L.divIcon({ html: html, className: '', iconSize: [32, 40], iconAnchor: [16, 40] });
                destinationMarker = L.marker([lat, lng], { icon: icon, draggable: false, zIndexOffset: 900 }).addTo(map);
            } else {
                destinationMarker.setLatLng([lat, lng]);
            }
        }
        function updateDestinationRoute(playerLat, playerLng, routePoints, showRoute) {
            if (destinationRoute) {
                map.removeLayer(destinationRoute);
                destinationRoute = null;
            }
            if (showRoute && routePoints && routePoints.length > 0) {
                var points = [];
                for (var i = 0; i < routePoints.length; i++) {
                    var pt = routePoints[i];
                    if (pt && typeof pt.lat !== 'undefined' && typeof pt.lng !== 'undefined') {
                        points.push([pt.lat, pt.lng]);
                    }
                }
                if (points.length > 1) {
                    destinationRoute = L.polyline(points, {
                        color: '#2196F3',
                        weight: 3,
                        opacity: 0.7,
                        dashArray: '5, 5',
                        lineCap: 'round',
                        lineJoin: 'round'
                    }).addTo(map);
                }
            }
        }
        function clearDestinationMarker() {
            if (destinationMarker) {
                map.removeLayer(destinationMarker);
                destinationMarker = null;
            }
            if (destinationRoute) {
                map.removeLayer(destinationRoute);
                destinationRoute = null;
            }
            isPlacingDestinationMarker = false;
            updateDestinationPlacingMode(false);
        }
        map.on('click', function(e) {
            if (isPlacingDestinationMarker && window.Android && window.Android.notifyMapClick) {
                window.Android.notifyMapClick(e.latlng.lat, e.latlng.lng);
                isPlacingDestinationMarker = false;
                updateDestinationPlacingMode(false);
            }
        });
        
        var roadLayers = {};
        function updateRoads(jsonStr) {
            var data = JSON.parse(jsonStr);
            var currentIds = new Set(data.map(function(w){ return String(w.id); }));
            for (var id in roadLayers) {
                if (!currentIds.has(id)) {
                    map.removeLayer(roadLayers[id]);
                    delete roadLayers[id];
                }
            }
            data.forEach(function(way) {
                var latlngs = way.nodes.map(function(n){ return [n.lat, n.lon]; });
                if (roadLayers[way.id]) {
                    roadLayers[way.id].setLatLngs(latlngs);
                    roadLayers[way.id].bringToFront();
                } else {
                    var color = way.isForCars ? '#FFD700' : '#82C8FF';
                    var weight = way.isForCars ? 4 : 3;
                    roadLayers[way.id] = L.polyline(latlngs, {
                        color: color, weight: weight, opacity: 0.85,
                        lineCap: 'round', lineJoin: 'round', interactive: false
                    }).addTo(map);
                    roadLayers[way.id].bringToFront();
                }
            });
        }
    </script>
</body>
</html>
""".trimIndent()
