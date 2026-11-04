# QA de Alexis Syntaxis y calles estáticas — 2026-11-04

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
