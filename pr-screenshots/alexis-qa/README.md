# QA de Alexis Syntaxis y calles estáticas — 2026-11-04

## Actualización 2026-11-05: regresión reportada en combate

El QA anterior no cubría suficientemente ataques aéreos, derribo, recuperación y KO.
El alto vertical por sí solo tampoco garantiza proporciones iguales en una pose doblada.
La receta calibra esas poses por cuerpo/cabeza y longitud horizontal; fall pasa de
137–191 px de ancho a 105–110 px. AIR/THROWN/GET_UP ya no se inflan a altura erguida.
HURT conserva el dibujo natural calibrado sin cambiar hurtScale; daño de pie mide
82–95 px (pose comprimida), stun sigue en 100 px. Las 258 claves y cajas se conservan.

- [Comparativa actualizada](postures-before-after.gif): idle, atlas previo y atlas nuevo,
  a la misma escala. Es una vista de sprites, no una grabación del emulador.
- [Medidas de todos los cuadros actuales](postures-alpha16.tsv), alfa >16.
- Regresión de física: HURT, THROWN, KO y neutral interrumpidos en el aire aterrizan
  en menos de 1,5 segundos simulados, con vy inicial -420/0/120. AIR conserva una sola
  integración de gravedad; un lanzamiento extremo deja de acumular subida en el techo.
- Mapas: CARTO/Google sin credenciales migran a OSM; opciones sin clave en Ajustes,
  URL/zoom únicos para precarga y vista, respaldo a OSM ante error de capa.
- [Probes HTTP reales](map-tile-probes.json) y [teselas revisadas](map-provider-probes.png):
  OSM, Esri calles/satélite y OpenTopo devolvieron imágenes 256², HTTP 200, sin aviso de clave.
- Tres pruebas de JS ejecutan el código Leaflet real con capas/eventos simulados:
  fallback único, ausencia de reintento por frame y errores tardíos de la capa anterior.

Validación del código/assets `269e7a864b0bd61b3bbfe8fdd6dbb482b29b1367`:
APK debug compilado; 129 tests app + 279 shared sin fallos, incluido el audit Arcade.
12 tests Python de assets/caché y 3 de JS, detekt y guard Native en verde.
[Resumen actualizado](test-summary-nov5.json).
Los tres workflows completaron en verde:
[calidad](https://github.com/gabrielhuav/PolitecnicoOpenWorld/actions/runs/41985641876),
[recortes](https://github.com/gabrielhuav/PolitecnicoOpenWorld/actions/runs/41985641893),
[calles](https://github.com/gabrielhuav/PolitecnicoOpenWorld/actions/runs/41985641874).

Comprobación real en `Small_Phone_API_35` (Android 35, 1280×720), con el APK nuevo:
Alexis vs Paramédico Cruz Roja, dificultad media, escenario UNAM CU nocturno.
[Daño y KO](emulator-nov5-damage-ko.mp4), [puño/patada aéreos y vuelta al suelo](emulator-nov5-air.mp4),
[captura KO](emulator-nov5-ko.png). Son dos extractos de grabaciones reales, sin interpolar
la escala del sprite. Revisados daño, salto con ataque, interrupción aérea y KO: no se
observó el crecimiento anterior ni la suspensión prolongada. Las pruebas puras cubren
las combinaciones de velocidad inicial y estados no recorridas en esos extractos.
No equivale a la matriz completa de dispositivos ni a QA de iOS.
El primer arranque del emulador tuvo ANR de System UI; se reinició en frío sin snapshot
(`-gpu swiftshader`, 4 GB) antes de grabar estas evidencias. Se guardaron los ajustes
originales y se habilitó Developer Mode solo para acceder al roster de prueba.

Mundo Libre completó la carga en el mismo APK: [mapa sin aviso de clave](emulator-nov5-map.png).
El [log de teselas](map-nov5-log.txt) confirma URLs `tile.openstreetmap.org`, HTTP 200.
Los cuatro servicios Web ofrecidos se revisaron también con teselas reales en la tabla
anterior; la migración de las selecciones antiguas está cubierta por las pruebas Kotlin.
No hubo errores fatales de AndroidRuntime durante estas pruebas. Al terminar se
restauró el XML original de ajustes del emulador, incluido Developer Mode desactivado.

La evidencia y los números del 4 de noviembre que siguen son históricos.


Código y assets probados: `282b69083c5961fc852d80327ab6b44a602683b9`.
La actualización posterior añade evidencias, comentarios y protección `pipefail`
al publicador de datos; no cambia el renderer ni los movimientos del combate.

| Verificación | Resultado |
|---|---|
| APK debug + tests app/shared | 129 + 273 tests; cero fallos |
| `SfArcadeCampaignAuditTest` | 3 tests; cero fallos |
| detekt / nombres Kotlin-Native | OK |
| Regresiones del gate de sprites / generador de calles | 4 / 4 tests OK |
| Reconstrucción de atlas/JSON | Píxeles y JSON idénticos al material versionado |
| Gate GitHub del código probado | Los tres workflows terminaron en verde |
| Emulador | Small_Phone_API_35, Android API 35, 720×1280 / paisaje 1280×720 |
| Combate | Estudiante vs Alexis, práctica, dificultad básica, escenario ESCOM |
| Mundo abierto | Carga en frío sin caché de calles; fuente estática confirmada por log |

## Evidencias

- [Alturas de TODOS los cuadros](alturas-alpha16.txt), medidas con alfa >16.
  `idle-1..4`, `hit-face-1..4`, `hit-stomach-1..4` y `stun-1..3`: **100 px**.
- [Comparativa antes/después del atlas](atlas-hurt-before-after.gif): alterna idle con
  cada uno de los 11 cuadros. Es una visualización del atlas, **no video del emulador**.
- [Video real del emulador](emulator-damage.mp4): extracto de 16 segundos del combate,
  con reacciones de daño representativas y tamaño corporal estable; sin errores fatales
  de la app observados durante la grabación.
- [Captura del combate](emulator-fight.png), [hoja completa](alexis_syntaxis-contact.png)
  e [informe geométrico](alexis_syntaxis-report.json).
- [Log de carga en frío](world-load-log.txt): `Calles estáticas OK (975_-4958): 3427 ways`.
  Se preservó la base anterior del emulador como `pow_roads-before-cold-qa.db` antes de
  arrancar con una base nueva. La descarga ocurre al crear el VM, incluso antes de
  entrar al mundo desde el menú.
- [Carga por encima del 25%](world-cold-load-progress.png) y
  [mundo jugable después de esa carga](world-cold-loaded.png).
- [Resumen de tests local](test-summary.json).

Ejecuciones del commit de código/assets:
[PR Quality Gate](https://github.com/gabrielhuav/PolitecnicoOpenWorld/actions/runs/41900233529),
[SF Fighter Assets](https://github.com/gabrielhuav/PolitecnicoOpenWorld/actions/runs/41900233797),
[Calles CDMX](https://github.com/gabrielhuav/PolitecnicoOpenWorld/actions/runs/41900233566).

## Alcance

Los src, push/hurt boxes, tiempos y 258 claves de cuadro se conservan. Solo `talk-*`
cambia su origen de busto centrado a cuerpo con pies en `[128,224]`.
El video no demuestra por sí solo todas las variantes de golpe, combos o estados;
los 11 cuadros sí están medidos y visibles en la comparativa completa.
No se presenta como una prueba de iOS, equipos físicos de gama baja ni revisión de derechos.
`qa-pending` se conserva para esa revisión ampliada. `manual-play-upload` está aplicado.

La cobertura estática inicial contiene 117 celdas del extracto BBBike MexicoCity;
fuera de ellas se mantiene el respaldo Overpass. El índice de `datos-calles` indica
procedencia y cobertura. La actualización mensual completa necesita que el workflow
se integre en la rama por defecto; no se ha mergeado ni publicado una versión de la app.
