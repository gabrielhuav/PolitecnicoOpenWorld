# datos-calles

Calles de la Ciudad de México para **Politécnico Open World**, en celdas de ~2 km.

> ⚠️ Rama **generada automáticamente** por `.github/workflows/calles-cdmx.yml` (rama `main`) y
> **reemplazada en cada corrida**. No hagas commits aquí: se pierden.

- `v1/indice.json`: fecha de generación, área cubierta, tamaño de celda y lista de celdas.
- `v1/celdas/<a>_<b>.json`: calles de la celda `a = floor(lat / 0.02)`, `b = floor(lon / 0.02)`,
  más un margen de 0.01°. Formato JSON de Overpass (`elements`: nodes + ways con `tags.highway`).

Consulta directa, por ejemplo la celda de ESCOM:
`https://raw.githubusercontent.com/gabrielhuav/PolitecnicoOpenWorld/datos-calles/v1/celdas/975_-4958.json`

Datos © OpenStreetMap contributors, disponibles bajo la Open Database License (ODbL 1.0).
Fuente: extracto de México de Geofabrik.
