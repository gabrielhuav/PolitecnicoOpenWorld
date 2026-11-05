const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

// Execute the real shared Leaflet provider function with a minimal event-driven layer.
const html = fs.readFileSync(path.resolve(__dirname,
    '../../shared/src/commonMain/kotlin/ovh/gabrielhuav/pow/features/map_exterior/ui/WorldMapLeafletHtml.kt'), 'utf8');
const source = html.slice(html.indexOf('var requestedTileUrl ='), html.indexOf('function setRoadNetworkReady'));
function scene() {
    const layers = [];
    const map = { removeLayer(layer) { layer.removed = true; } };
    const L = { tileLayer(url, options) {
        const layer = { _url: url, options, events: {},
            on(event, fn) { this.events[event] = fn; return this; },
            addTo() { layers.push(this); return this; } };
        return layer;
    } };
    const context = { map, L, currentTileLayer: L.tileLayer('initial', {}) };
    vm.createContext(context); vm.runInContext(source, context);
    return { context, layers, change: (url, zoom) => context.changeTileUrl(url, zoom) };
}

test('failed provider falls back once and survives subsequent UI frame updates', () => {
    const s = scene();
    s.change('https://example.org/{z}/{x}/{y}.png', 17);
    const failed = s.context.currentTileLayer;
    failed.events.tileerror();
    const fallback = s.context.currentTileLayer;
    assert.match(fallback._url, /tile.openstreetmap.org/);
    assert.equal(fallback.options.maxNativeZoom, 19);
    assert.match(fallback.options.attribution, /OpenStreetMap/);
    assert.ok(failed.removed);
    for (let i = 0; i < 60; i++) s.change('https://example.org/{z}/{x}/{y}.png', 17);
    failed.events.tileerror();
    assert.equal(s.context.currentTileLayer, fallback);
    assert.equal(s.layers.length, 2);
});

test('late errors from old layers cannot replace a newer provider', () => {
    const s = scene(); s.change('https://old.example/{z}/{x}/{y}', 17);
    const old = s.context.currentTileLayer;
    s.change('https://new.example/{z}/{x}/{y}', 19);
    const current = s.context.currentTileLayer; old.events.tileerror();
    assert.equal(s.context.currentTileLayer, current);
});

test('OSM errors do not recursively recreate the fallback', () => {
    const s = scene(); s.change('https://tile.openstreetmap.org/{z}/{x}/{y}.png', 19);
    const layer = s.context.currentTileLayer; layer.events.tileerror();
    assert.equal(s.context.currentTileLayer, layer);
    assert.equal(s.layers.length, 1);
});
