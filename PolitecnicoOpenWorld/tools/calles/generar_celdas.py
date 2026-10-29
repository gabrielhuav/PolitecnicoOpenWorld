#!/usr/bin/env python3
"""
Genera las celdas de calles de la CDMX que la app descarga de la rama `datos-calles`.

Por qué existe: la app pedía las calles a Overpass en vivo y, en horas pico, TODOS los servidores
públicos respondían 502/504 ("at capacity"); una instalación nueva se quedaba en el 25 % de la
carga del mundo abierto. Overpass pide que las apps no usen su endpoint público. Estas celdas se
generan una vez al mes (GitHub Action `calles-cdmx.yml`) y se sirven como archivos estáticos.

Contrato con la app (no romper):
  - Celda = (floor(lat / 0.02), floor(lon / 0.02)), archivo `v1/celdas/<a>_<b>.json`. Es la misma
    clave que `celdaDeCalles()` en RoadNetworkCache.kt.
  - Formato = el JSON de Overpass (`elements` con nodes y ways con `tags.highway` y `nodes`), para
    que `OverpassRepository.parseOverpassJson` lo lea sin cambios.
  - Mismos tipos de `highway` que la consulta a Overpass de OverpassRepository.kt.

Uso:  python3 generar_celdas.py <calles.osm.pbf> <carpeta_salida> [--bbox oeste,sur,este,norte]
Requiere: pip install osmium
"""
import argparse
import datetime
import json
import math
import os
import sys

import osmium

CELDA_DEG = 0.02
# Margen alrededor de la celda: la consulta a Overpass bajaba 2 km a la redonda del jugador;
# 0.01° (~1.1 km) asegura calles al cruzar el borde mientras llega la celda vecina.
MARGEN_DEG = 0.01
HIGHWAY = {
    "primary", "secondary", "tertiary", "residential", "unclassified",
    "footway", "pedestrian", "path", "living_street", "service",
}
BBOX_CDMX = (-99.40, 19.04, -98.90, 19.70)  # oeste, sur, este, norte
MIN_CELDAS = 50  # menos que esto = extracto roto: mejor fallar que publicar basura


def celda(lat, lon):
    return math.floor(lat / CELDA_DEG), math.floor(lon / CELDA_DEG)


class LectorCalles(osmium.SimpleHandler):
    def __init__(self):
        super().__init__()
        self.ways = []  # (id, highway, [(ref, lat, lon), ...])

    def way(self, w):
        hw = w.tags.get("highway")
        if hw not in HIGHWAY:
            return
        pts = [(n.ref, round(n.location.lat, 7), round(n.location.lon, 7))
               for n in w.nodes if n.location.valid()]
        if len(pts) > 1:
            self.ways.append((w.id, hw, pts))


def celdas_de_way(pts, bbox_celdas):
    """Celdas cuya zona (celda + margen) toca algún nodo del way."""
    a0, a1, b0, b1 = bbox_celdas
    out = set()
    for _, lat, lon in pts:
        for a in range(math.floor((lat - MARGEN_DEG) / CELDA_DEG), math.floor((lat + MARGEN_DEG) / CELDA_DEG) + 1):
            if not a0 <= a <= a1:
                continue
            for b in range(math.floor((lon - MARGEN_DEG) / CELDA_DEG), math.floor((lon + MARGEN_DEG) / CELDA_DEG) + 1):
                if b0 <= b <= b1:
                    out.add((a, b))
    return out


def generar(pbf, salida, bbox):
    lector = LectorCalles()
    lector.apply_file(pbf, locations=True)
    oeste, sur, este, norte = bbox
    a0, b0 = celda(sur, oeste)
    a1, b1 = celda(norte, este)
    por_celda = {}
    for i, (_, _, pts) in enumerate(lector.ways):
        for c in celdas_de_way(pts, (a0, a1, b0, b1)):
            por_celda.setdefault(c, []).append(i)

    dir_celdas = os.path.join(salida, "v1", "celdas")
    os.makedirs(dir_celdas, exist_ok=True)
    total_bytes = 0
    for (a, b), idx in sorted(por_celda.items()):
        nodos, ways = {}, []
        for i in idx:
            wid, hw, pts = lector.ways[i]
            for ref, lat, lon in pts:
                nodos[ref] = (lat, lon)
            ways.append({"type": "way", "id": wid, "nodes": [p[0] for p in pts], "tags": {"highway": hw}})
        elementos = [{"type": "node", "id": r, "lat": la, "lon": lo} for r, (la, lo) in nodos.items()] + ways
        doc = {"version": 0.6, "generator": "POW generar_celdas.py",
               "osm3s": {"copyright": "© OpenStreetMap contributors, ODbL 1.0"}, "elements": elementos}
        ruta = os.path.join(dir_celdas, f"{a}_{b}.json")
        with open(ruta, "w", encoding="utf-8") as f:
            json.dump(doc, f, separators=(",", ":"))
        total_bytes += os.path.getsize(ruta)

    indice = {
        "version": 1,
        "generado": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "fuente": "Geofabrik mexico-latest.osm.pbf (© OpenStreetMap contributors, ODbL 1.0)",
        "bbox": {"oeste": oeste, "sur": sur, "este": este, "norte": norte},
        "celdaDeg": CELDA_DEG, "margenDeg": MARGEN_DEG,
        "highway": sorted(HIGHWAY),
        "celdas": sorted(f"{a}_{b}" for a, b in por_celda),
    }
    with open(os.path.join(salida, "v1", "indice.json"), "w", encoding="utf-8") as f:
        json.dump(indice, f, ensure_ascii=False, indent=1)
    return len(lector.ways), len(por_celda), total_bytes


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("pbf")
    ap.add_argument("salida")
    ap.add_argument("--bbox", default=",".join(map(str, BBOX_CDMX)))
    ap.add_argument("--min-celdas", type=int, default=MIN_CELDAS)
    args = ap.parse_args()
    bbox = tuple(float(x) for x in args.bbox.split(","))
    n_ways, n_celdas, total = generar(args.pbf, args.salida, bbox)
    print(f"ways={n_ways} celdas={n_celdas} total={total / 1e6:.1f} MB")
    if n_celdas < args.min_celdas:
        print(f"ERROR: solo {n_celdas} celdas (< {args.min_celdas}); el extracto parece roto.", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
