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
        /* OPT FPS (gama baja): el mapa Leaflet ocupa el tamaño del wrapper. Antes era
           300vw×300vh (≈9× el área de pantalla) SIEMPRE, así que el WebView renderizaba y
           RE-COMPONÍA ~9× las teselas — el costo dominante al moverse/conducir en web. Ahora a
           pie es del tamaño EXACTO de la pantalla (100vw×100vh) y solo al CONDUCIR se agranda a
           un cuadrado 150vmax (cuyo círculo inscrito cubre la diagonal en cualquier rotación),
           vía setMapOversize(). transform-origin centrado para que rotate() gire sobre el centro. */
        #map-wrapper { position: absolute; top: 0; left: 0; width: 100vw; height: 100vh; transform-origin: center center; }
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
        /* NEBLINA (fog of war): overlay dentro del wrapper para que rote junto al
           mapa y se posicione en coordenadas del contenedor (= posición del jugador). */
        #fog { position:absolute; top:0; left:0; width:100%; height:100%; pointer-events:none; z-index:650; }
    </style>
</head>
<body>
    <div id="map-wrapper"><div id="map"></div><div id="fog"></div></div>
    <script>
        var map = L.map('map', { 
            zoomControl: false,
            attributionControl: false,
            dragging: true,   // arrastre SIEMPRE disponible: paridad con los mapas nativos
            touchZoom: true,  // pinch (dos dedos) SIEMPRE disponible: paridad con nativo
            doubleClickZoom: false,
            scrollWheelZoom: false,
            boxZoom: false,
            keyboard: false,
            maxZoom: 22 
        }).setView([$lat, $lng], $zoom);
        // keepBuffer: mantener más anillos de teselas alrededor del viewport (más "radio"
        //   cargado → menos teselas grises al moverte). updateWhenIdle:false → cargar teselas
        //   MIENTRAS te mueves (no solo al detenerte, que es el default en móvil y causaba el
        //   tirón al parar). updateWhenZooming:false evita churn de teselas a mitad de zoom.
        var currentTileLayer = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{ maxZoom: 22, maxNativeZoom: 18, keepBuffer: 3, updateWhenIdle: false, updateWhenZooming: false }).addTo(map);
        map.createPane('landmarkPane');
        map.getPane('landmarkPane').style.zIndex = 300;
        map.createPane('doorPane');
        map.getPane('doorPane').style.zIndex = 450;
        map.getPane('doorPane').style.pointerEvents = 'none';

        
        var npcMarkers = {};
        var collectibleMarkers = {};
        var landmarkMarkers = {};
        var policeWpMarkers = {};   // 🚓 de patrullas fuera de la neblina (paridad con OSM nativo)
        var policeWpLines = {};     // líneas punteadas jugador → patrulla
        var zombieWpMarkers = {};   // 🧟 de zombis fuera del fog (modo apocalipsis, paridad con OSM nativo)
        var zombieWpLines = {};     // líneas ROJAS punteadas jugador → zombi

        var isZooming = false;
        var isExplorationMode = false;

        // ─── DETECCIÓN DE GESTO DEL USUARIO ──────────────────────────────────
        // Distingue el zoom por PINCH del usuario (dedo en pantalla) del zoom
        // PROGRAMÁTICO (botones del menú / seguimiento del jugador). Solo el
        // primero debe entrar en modo exploración; de lo contrario el zoom por
        // botón ocultaría al jugador y rompería el auto-seguimiento.
        var pointerDown = false;
        (function() {
            var el = document.getElementById('map');
            if (!el) return;
            var down = function() { pointerDown = true; };
            var up = function() { pointerDown = false; };
            el.addEventListener('touchstart', down, { passive: true });
            el.addEventListener('touchend', up, { passive: true });
            el.addEventListener('touchcancel', up, { passive: true });
            el.addEventListener('mousedown', down);
            window.addEventListener('mouseup', up);
        })();

        map.on('zoomstart', function() {
            isZooming = true;
            // Pinch del usuario: anclar al jugador a su posición geográfica real
            // (igual que el arrastre) para que NO se mueva con el gesto ni rebote.
            if (pointerDown && !isExplorationMode) {
                isExplorationMode = true;
                if (window.Android && window.Android.notifyMapPanStart) window.Android.notifyMapPanStart();
            }
        });
        map.on('zoomend', function() {
            isZooming = false;
            // Propaga el zoom por gesto (pinch) de vuelta a la app para que no rebote.
            if (window.Android && window.Android.notifyMapZoom) window.Android.notifyMapZoom(map.getZoom());
        });
        map.on('zoom', function() { resizeLandmarks(); });

        map.on('dragstart', function() {
            isExplorationMode = true;
            if (window.Android && window.Android.notifyMapPanStart) window.Android.notifyMapPanStart();
        });
        map.on('dragend', function() {
            if (window.Android && window.Android.notifyMapPanEnd) window.Android.notifyMapPanEnd();
        });
        
        // OPT FPS (web, gama baja): seguir al jugador con panBy (translación por transform,
        // barata) en vez de setView, que hacía un _resetView COMPLETO reposicionando TODAS las
        // teselas y marcadores cada frame — la causa principal del bajón de FPS al moverse.
        // setView solo se usa al cambiar el ZOOM o en saltos grandes (teletransporte). El delta
        // se recalcula cada frame contra el centro actual, así que es auto-corrector (sin deriva).
        function updateMapView(lat, lng, z) {
            if (isZooming || isExplorationMode) return;
            if (map.getZoom() !== z) { map.setView([lat, lng], z, { animate: false }); return; }
            var target = map.latLngToContainerPoint([lat, lng]);
            var center = map.latLngToContainerPoint(map.getCenter());
            var dx = target.x - center.x, dy = target.y - center.y;
            if (dx === 0 && dy === 0) return;
            if (Math.abs(dx) > 800 || Math.abs(dy) > 800) { map.setView([lat, lng], z, { animate: false }); return; }
            map.panBy([dx, dy], { animate: false, noMoveStart: true });
        }
        
        function setDesignerMode(isDesigner) {
            // Arrastre (pan) y pinch-zoom SIEMPRE activos para igualar a los mapas nativos.
            // El Modo Diseñador solo añade además el zoom por rueda (escritorio).
            map.dragging.enable();
            map.touchZoom.enable();
            if (isDesigner) {
                map.scrollWheelZoom.enable();
            } else {
                map.scrollWheelZoom.disable();
            }
        }
        
        function setMapRotation(deg) { var wrapper = document.getElementById('map-wrapper'); if (wrapper) wrapper.style.transform = 'rotate(' + deg + 'deg)'; }
        // OPT FPS (gama baja): a pie el wrapper es del tamaño de la pantalla (≈45 teselas en vez
        // de ~350). Solo al CONDUCIR se agranda a un cuadrado del tamaño de la DIAGONAL de la
        // pantalla (en px), centrado, cuyo círculo inscrito cubre las esquinas en cualquier
        // rotación. invalidateSize() recalcula el mapa al cambiar de tamaño (poco frecuente: solo
        // al subir/bajar del vehículo). _driving evita trabajo redundante en llamadas repetidas.
        function setMapOversize(driving) {
            var w = document.getElementById('map-wrapper'); if (!w) return;
            if (w._driving === driving) return; w._driving = driving;
            if (driving) {
                // Cuadrado del tamaño de la DIAGONAL de la pantalla, en PÍXELES (no vmax/calc, que
                // no son fiables en WebViews viejas de Android 7-9 → de ahí los bordes negros al
                // rotar). Centrado: su círculo inscrito (lado/2 = diagonal/2) cubre las esquinas
                // de la pantalla en CUALQUIER ángulo de rotación, sin huecos.
                var vw = window.innerWidth, vh = window.innerHeight;
                var d = Math.ceil(Math.sqrt(vw * vw + vh * vh)) + 8;
                w.style.width = d + 'px'; w.style.height = d + 'px';
                w.style.top = Math.round((vh - d) / 2) + 'px';
                w.style.left = Math.round((vw - d) / 2) + 'px';
            } else {
                w.style.width = '100vw'; w.style.height = '100vh';
                w.style.top = '0'; w.style.left = '0';
            }
            if (typeof map !== 'undefined' && map) map.invalidateSize(false);
        }
        function changeTileUrl(url) { if (currentTileLayer) currentTileLayer.setUrl(url); }
        function setRoadNetworkReady(ready) { window.roadNetworkReady = ready; }
        function exitExplorationMode() { isExplorationMode = false; }

        // ─── NEBLINA (fog of war) ANCLADA AL JUGADOR ─────────────────────────
        // Se redibuja en cada evento 'move'/'zoom' de Leaflet (que disparan de forma
        // continua durante el arrastre y el pinch), por lo que la zona despejada
        // sigue al jugador en su posición geográfica real en vez de quedarse pegada
        // al centro de la pantalla.
        var fogLat = $lat, fogLng = $lng, fogEnabled = true;
        function setFogEnabled(on) { fogEnabled = on; drawFog(); }
        function setPlayerFog(lat, lng) {
            if (lat === null || lng === null) return;
            fogLat = lat; fogLng = lng; drawFog();
        }
        function drawFog() {
            var el = document.getElementById('fog');
            if (!el) return;
            if (!fogEnabled) { if (el._bg !== 'none') { el._bg = 'none'; el.style.background = 'none'; } return; }
            var pt = map.latLngToContainerPoint([fogLat, fogLng]);
            var zoom = map.getZoom();
            var ppm = (256 * Math.pow(2, zoom)) / (40075016 * Math.cos(fogLat * Math.PI / 180));
            var reveal = 70 * ppm; // NPC_FOG_VISION_METERS = 70 m
            var maxReveal = Math.min(window.innerWidth, window.innerHeight) * 0.40;
            reveal = Math.max(40, Math.min(reveal, maxReveal));
            var outer = reveal * 1.8;
            // OPT FPS (gama baja): drawFog corre en CADA evento 'move' (= cada frame al andar).
            // Pero siguiendo al jugador, éste está SIEMPRE centrado → el degradado es idéntico.
            // Cacheamos el string y solo reasignamos el background (que re-rasteriza un radial-
            // gradient a pantalla completa, caro) cuando realmente cambia (exploración o zoom).
            var bg = 'radial-gradient(circle at ' + Math.round(pt.x) + 'px ' + Math.round(pt.y) + 'px, ' +
                'rgba(0,0,0,0) 0px, rgba(0,0,0,0) ' + Math.round(reveal) + 'px, rgba(34,42,51,0.5) ' + Math.round(outer) + 'px)';
            if (el._bg === bg) return;
            el._bg = bg;
            el.style.background = bg;
        }
        map.on('move', drawFog);
        map.on('zoom', drawFog);
        map.on('resize', drawFog);
        drawFog();
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
            data.forEach(function(npc) {
                // TAMAÑO BASADO EN METROS REALES (paridad EXACTA con OSM Nativo):
                // peatón ≈ 0.9 m, coche ≈ 2.5 m, manteniendo la proporción del sprite.
                // Antes el web usaba una fórmula por zoom y por eso los NPCs salían de
                // tamaño distinto al nativo.
                var ppm = (256 * Math.pow(2, currentZoom)) / (40075016 * Math.cos((npc.lat || 19.5) * Math.PI / 180));
                var finalW, finalH;
                if (npc.type === 'CAR') {
                    var carPx = Math.max(16, 4.0 * ppm);
                    var ratio = (npc.width && npc.height) ? (npc.width / npc.height) : 1;
                    if (ratio > 1) { finalW = Math.round(carPx); finalH = Math.round(carPx / ratio); }
                    else { finalH = Math.round(carPx); finalW = Math.round(carPx * ratio); }
                } else if (npc.type === 'MODULAR') {
                    var pedPx = Math.round(Math.max(12, 1.3 * ppm));
                    finalW = pedPx; finalH = pedPx;
                } else { finalW = 24; finalH = 24; }
                var nameTagHtml = '';
                if (npc.name) {
                    var safeName = escapeHtml(npc.name);
                    nameTagHtml = '<div style="position:absolute; top:-28px; left:50%; transform:translateX(-50%); color:#D4AF37; background:rgba(0,0,0,0.65); padding:2px 6px; border-radius:4px; font-size:16px; font-weight:bold; white-space:nowrap; text-shadow:1px 1px 0 #000; z-index:100;">' + safeName + '</div>';
                }
                // Barra de vida del NPC (paridad con los mapas nativos): contenedor siempre
                // presente; se muestra solo si el NPC tiene vida < 100 y no está muriendo.
                var hp = (npc.health === undefined || npc.health === null) ? 100 : npc.health;
                var dying = !!npc.isDying;
                var showHb = hp < 100 && !dying;
                var hbPct = Math.max(0, Math.min(100, hp));
                var hbColor = hp > 60 ? '#4CAF50' : (hp > 30 ? '#FFEB3B' : '#F44336');
                var hbHtml = '<div class="npc-hb" style="position:absolute; top:-16px; left:50%; transform:translateX(-50%); width:36px; height:8px; background:rgba(0,0,0,0.5); border-radius:4px; overflow:hidden; z-index:120; display:' + (showHb ? 'block' : 'none') + ';"><div class="npc-hb-fill" style="width:' + hbPct + '%; height:100%; background:' + hbColor + ';"></div></div>';
                if (npcMarkers[npc.id]) {
                    npcMarkers[npc.id].setLatLng([npc.lat, npc.lng]);
                    var el = npcMarkers[npc.id].getElement();
                    if (el) {
                        var wrapper = el.querySelector('.npc-c');
                        var img = el.querySelector('img');
                        var hb = el.querySelector('.npc-hb');
                        if (hb) {
                            hb.style.display = showHb ? 'block' : 'none';
                            var fill = hb.querySelector('.npc-hb-fill');
                            if (fill) { fill.style.width = hbPct + '%'; fill.style.background = hbColor; }
                        }
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
                        html = '<div class="npc-c" style="position:absolute; transform: translate(-50%, -50%); width:'+finalW+'px; height:'+finalH+'px;">' + nameTagHtml + hbHtml + '<img src="'+cachedImg+'" style="width:100%; height:100%; display:block; ' + flipStyle + '"></div>';
                    } else {
                        var pUrl = 'file:///android_asset/' + npc.drawable + '.svg';
                        html = '<div class="npc-c" style="position:absolute; transform: translate(-50%, -50%) rotate(0deg); width:24px; height:24px;">' + nameTagHtml + hbHtml + '<img src="'+pUrl+'" style="width:100%; height:100%; display:block;"></div>';
                    }
                    var icon = L.divIcon({ html: html, className: '', iconSize: [0, 0] });
                    npcMarkers[npc.id] = L.marker([npc.lat, npc.lng], { icon: icon, zIndexOffset: 1000 }).addTo(map);
                }
            });
        }
        // ─── WAYPOINTS DE PATRULLAS (fuera de la neblina) ───────────────────────────
        // Paridad con OSM nativo: mientras una patrulla está FUERA de tu campo de visión y
        // te buscan, se marca con un 🚓 y una línea punteada desde el jugador hasta ella.
        function updatePolice(playerLat, playerLng, data) {
            var ids = new Set(data.map(function(p){ return p.id; }));
            for (var id in policeWpMarkers) if (!ids.has(id)) { map.removeLayer(policeWpMarkers[id]); delete policeWpMarkers[id]; }
            for (var id in policeWpLines) if (!ids.has(id)) { map.removeLayer(policeWpLines[id]); delete policeWpLines[id]; }
            data.forEach(function(p) {
                if (policeWpMarkers[p.id]) {
                    policeWpMarkers[p.id].setLatLng([p.lat, p.lng]);
                } else {
                    var icon = L.divIcon({ html: '<div style="font-size:26px; transform:translate(-50%,-50%);">🚓</div>', className: '', iconSize: [0,0] });
                    policeWpMarkers[p.id] = L.marker([p.lat, p.lng], { icon: icon, interactive: false, zIndexOffset: 800 }).addTo(map);
                }
                var pts = [[playerLat, playerLng], [p.lat, p.lng]];
                if (policeWpLines[p.id]) {
                    policeWpLines[p.id].setLatLngs(pts);
                } else {
                    policeWpLines[p.id] = L.polyline(pts, { color: '#005AFF', weight: 3, opacity: 0.47, dashArray: '18, 14', interactive: false }).addTo(map);
                }
            });
        }
        // ─── WAYPOINTS DE ZOMBIS (fuera del fog, modo apocalipsis) ──────────────────
        // Paridad con OSM nativo: cada zombi FUERA de tu campo de visión se marca con un
        // 🧟 y una línea ROJA punteada desde el jugador, para saber de dónde vienen.
        function updateZombies(playerLat, playerLng, data) {
            var ids = new Set(data.map(function(z){ return z.id; }));
            for (var id in zombieWpMarkers) if (!ids.has(id)) { map.removeLayer(zombieWpMarkers[id]); delete zombieWpMarkers[id]; }
            for (var id in zombieWpLines) if (!ids.has(id)) { map.removeLayer(zombieWpLines[id]); delete zombieWpLines[id]; }
            data.forEach(function(z) {
                if (zombieWpMarkers[z.id]) {
                    zombieWpMarkers[z.id].setLatLng([z.lat, z.lng]);
                } else {
                    var icon = L.divIcon({ html: '<div style="font-size:26px; transform:translate(-50%,-50%);">🧟</div>', className: '', iconSize: [0,0] });
                    zombieWpMarkers[z.id] = L.marker([z.lat, z.lng], { icon: icon, interactive: false, zIndexOffset: 800 }).addTo(map);
                }
                var pts = [[playerLat, playerLng], [z.lat, z.lng]];
                if (zombieWpLines[z.id]) {
                    zombieWpLines[z.id].setLatLngs(pts);
                } else {
                    zombieWpLines[z.id] = L.polyline(pts, { color: '#E53935', weight: 3, opacity: 0.5, dashArray: '18, 14', interactive: false }).addTo(map);
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
