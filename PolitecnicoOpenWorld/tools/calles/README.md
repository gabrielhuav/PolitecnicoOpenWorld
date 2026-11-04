# Calles estáticas de CDMX

Implementación inicial de Claude en PR #182 (`e66cf334`), integrada al PR #158
por solicitud del dueño. La app usa Room, luego archivos estáticos y finalmente Overpass.

## Generar y probar

```sh
pip install -r PolitecnicoOpenWorld/tools/calles/requirements.txt
python -m unittest discover -s PolitecnicoOpenWorld/tools/calles/tests
python PolitecnicoOpenWorld/tools/calles/generar_celdas.py calles.osm.pbf salida
```

El PBF puede ser el recorte de calles de Geofabrik que prepara `calles-cdmx.yml`.
Para otra fuente, pasar `--fuente` y un `--bbox` que esté realmente cubierto por ella.
No anunciar cobertura de todo CDMX si solo se dispone de un recorte parcial.
La salida incluye atribución OSM/ODbL y su procedencia en `v1/indice.json`.

La clave `floor(lat/0.02)_floor(lon/0.02)` coincide con `celdaDeCalles` en Kotlin.
Cada archivo contiene su celda más 0,01° de margen y todos los nodos de sus ways.
El test de segmento largo protege calles que cruzan celdas sin tener un nodo dentro.

## Publicación y operación

`calles-cdmx.yml` corre los tests en PR. La generación/publicación solo corre en el
repo `gabrielhuav/PolitecnicoOpenWorld` por ejecución manual o el día 3 de cada mes.
El botón manual y el horario necesitan que el workflow esté en la rama por defecto.
Publica `datos-calles`, sin tocar `gh-pages`, que sirve las políticas de Play.

El 2026-11-04 se publicó una cobertura inicial de **117 celdas**, incluido ESCOM,
con el extracto [BBBike MexicoCity](https://download.bbbike.org/osm/bbbike/MexicoCity/).
Su índice identifica esa fuente; el workflow mensual ampliará la cobertura usando
[Geofabrik México](https://download.geofabrik.de/north-america/mexico.html).
Fuera de las celdas publicadas la app sigue consultando Overpass.

Validar antes de publicar: al menos 50 celdas; archivo `975_-4958.json` con calles;
todos los nodos referenciados existen; highway coincide con la lista del repositorio.
Después de publicar, comprobar HTTP 200 del índice y de la celda de ESCOM.

La app limita la petición estática a 8 segundos y rechaza cuerpos HTML, JSON sin
`elements` y errores de servidor antes de pasar al respaldo. Las cancelaciones se propagan.
La implementación conserva el parser multiplataforma y la caché existentes.

Datos © OpenStreetMap contributors, [ODbL 1.0](https://www.openstreetmap.org/copyright).
