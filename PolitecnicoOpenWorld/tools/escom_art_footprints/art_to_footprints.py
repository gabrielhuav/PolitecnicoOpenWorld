"""REGENERA la colisión del edificio ESCOM a partir de la IMAGEN del campus (BUILDINGS/IPN/building_escom.webp).

Pasos:
  1. Con el juego abierto (build debug) y el WebView inspeccionable:
       adb forward tcp:9222 localabstract:webview_devtools_remote_<pid>
     evalúa `extract_polys.js` por DevTools (Runtime.evaluate) y guarda el resultado en `polys.json`
     (techos naranjas + paneles como POLÍGONOS DE N LADOS en coordenadas normalizadas de la imagen).
  2. python art_to_footprints.py          -> prueba en seco (valida contra el navGraph)
     python art_to_footprints.py apply    -> escribe app/src/main/assets/CONFIG/escom_osm_footprints.json

⚠️ La imagen se DIBUJA con la rotación contraria a `Landmark.toGlobalGeoPoint` (verificado comparando las esquinas
reales del <img> en el DOM con latLngToContainerPoint), pero las vías del navGraph se dibujaron con el diseñador
en el sistema de `toGlobalGeoPoint`. Por eso los techos (espacio de imagen) usan -rot y los nodos del navGraph +rot.
Validación esperada: 0 nodos peatonales y 0 de coches dentro de los techos.

`escom_osm_original.json` son las huellas de OpenStreetMap (ODbL) sin tocar; se descartan los edificios OSM cuyo
centroide cae dentro de la imagen del campus y se añaden los techos de la imagen (tags.source = "art").
"""
import json
import math
import os
import sys

D = os.path.dirname(os.path.abspath(__file__))
CFG = os.path.normpath(os.path.join(D, "..", "..", "app", "src", "main", "assets", "CONFIG"))
APPLY = len(sys.argv) > 1 and sys.argv[1] == "apply"

# default_landmarks.json (id 1) + buildings_catalog.json (escom_main)
LAT0, LON0, ROT, SC = 19.504694288807826, -99.14663364364556, 25.819448, 0.9854224
BW, BH = 212.7, 263.0
MIN_BUILDING_AREA_M2 = 1000.0  # descarta canchas, bancas y manchas naranjas sueltas


def to_global(lx, ly, rot=ROT):
    dx = (lx - 0.5) * BW * SC
    dy = (0.5 - ly) * BH * SC
    a = math.radians(rot)
    rx = dx * math.cos(a) - dy * math.sin(a)
    ry = dx * math.sin(a) + dy * math.cos(a)
    lat = LAT0 + math.degrees(ry / 6378137.0)
    lon = LON0 + math.degrees(rx / (6378137.0 * math.cos(math.radians(LAT0))))
    return lat, lon


def inside(lat, lon, pts):
    ins = False
    j = len(pts) - 1
    for i in range(len(pts)):
        yi, xi = pts[i]
        yj, xj = pts[j]
        if (yi > lat) != (yj > lat) and lon < (xj - xi) * (lat - yi) / (yj - yi) + xi:
            ins = not ins
        j = i
    return ins


MX, MY = BW * SC, BH * SC
polys = json.load(open(os.path.join(D, "polys.json")))["polys"]
art = [p["pts"] for p in polys if p["areaM2"] >= MIN_BUILDING_AREA_M2]
print("polígonos del edificio:", len(art), "de", len(polys), "| vértices:", [len(p) for p in art])

IMG_ROT = -ROT
art_polys = []
for k, pts in enumerate(art):
    art_polys.append((k, [[round(v, 7) for v in to_global(x, y, IMG_ROT)] for x, y in pts]))

nav = json.load(open(os.path.join(CFG, "navgraphs", "escom_navgraph.json"), encoding="utf-8"))


def count_inside(min_id, max_id):
    total = hit = 0
    for w in nav["ways"]:
        if min_id <= w["id"] < max_id:
            for nd in w["nodes"]:
                total += 1
                lat, lon = to_global(nd["localX"], nd["localY"])  # sistema del navGraph: +rot
                if any(inside(lat, lon, p) for _, p in art_polys):
                    hit += 1
    return hit, total


print("nodos peatonales dentro de los techos: %d de %d" % count_inside(200, 10**9))
print("nodos de coche dentro de los techos: %d de %d" % count_inside(0, 200))

fp = json.load(open(os.path.join(D, "escom_osm_original.json"), encoding="utf-8"))
image_poly = [list(to_global(x, y, IMG_ROT)) for x, y in ((0, 0), (1, 0), (1, 1), (0, 1))]
kept, dropped = [], []
for w in fp["ways"]:
    if w["tags"].get("building") not in (None, "roof"):
        cy = sum(p[0] for p in w["pts"]) / len(w["pts"])
        cx = sum(p[1] for p in w["pts"]) / len(w["pts"])
        if inside(cy, cx, image_poly):
            dropped.append(w)
            continue
    kept.append(w)
print("edificios OSM descartados (dentro de la imagen):", len(dropped))

new_ways = [
    {"id": 9000000 + k, "tags": {"building": "university", "name": "ESCOM techo %d" % (k + 1), "source": "art"}, "pts": pts + [pts[0]]}
    for k, pts in art_polys
]
out = {
    "source": "OpenStreetMap contributors (ODbL) + techos del campus extraídos de BUILDINGS/IPN/building_escom.webp (source=art)",
    "bbox": fp.get("bbox"),
    "ways": kept + new_ways,
}
print("huellas finales:", len(out["ways"]), "(OSM %d + imagen %d)" % (len(kept), len(new_ways)))
if APPLY:
    path = os.path.join(CFG, "escom_osm_footprints.json")
    json.dump(out, open(path, "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    print("escrito", path)
