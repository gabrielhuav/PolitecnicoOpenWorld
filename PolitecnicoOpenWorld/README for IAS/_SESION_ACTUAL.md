# Memoria compartida — estado vivo

Actualizado: 2026-11-04 · rama `pr/158` · PR #158.
El historial anterior completo está en `_ARCHIVO/SESION_previa_2026-11-04.md`.

## Alcance autorizado

El dueño pidió corregir Alexis Syntaxis, agregar CI/tutorial de peleadores, simplificar
el repo e integrar la alternativa al bloqueo de calles al 25%. Autorizó commits y push
en el mismo PR y continuar sin confirmaciones adicionales. No se ha autorizado merge.

## Alexis Syntaxis

- Fuente importada de PR #158 y JSON preservados en `newSFAssets/AlexisSyntaxis/`.
- 14 poses regeneradas con imagegen: cuatro talk de cuerpo completo y diez cuadros
  cuyos efectos/cables estaban cortados. Prompt exacto en `GENERACION.md`.
- 258 cuadros / 58 animaciones; daño y stun normalizados a 100 px, pies y=224.
- No se cambia renderer, `hurtScale`, hitboxes, tiempos ni layout. Solo `talk-*` cambia
  su origen de busto [128,128] a pies [128,224]. Proyectiles conservan [128,128].
- Receta reproducible: `tools/sf_fighters/alexis_syntaxis.json`.
- Tutorial: `SF/CI_CD_agregar_peleador.md`. Workflow `SF Fighter Assets`.
- QA geométrico sin errores, cuatro tests Python de regresión del gate en verde.
- Falta cerrar la prueba visual en emulador y el gate completo del commit final.
- PR etiquetado `manual-play-upload` y `qa-pending`; no subir a Play automáticamente.
- R-5: personaje ficticio de parodia; renombrarlo no prueba ausencia de parecido con
  personas reales. Se documenta la limitación; no se presenta como revisión jurídica.

## Calles estáticas / bloqueo al 25%

- Integrado el cambio de Claude del PR #182, commit fuente `e66cf334`, en este checkout.
- Orden: caché Room → celda estática de `datos-calles` → Overpass con respaldos.
- La rama `datos-calles` no existía. Se generaron y publicaron 117 celdas iniciales
  (incluido ESCOM), desde BBBike MexicoCity 2026-11-01, con atribución OSM/ODbL.
- Índice público: raw.githubusercontent.com/gabrielhuav/PolitecnicoOpenWorld/datos-calles/v1/indice.json.
- Bootstrap limitado al centro/norte cubierto por el extracto; fuera de esa cobertura
  se conserva Overpass. El workflow mensual usa el extracto completo de Geofabrik.
- El generador ahora cubre segmentos que cruzan una celda sin nodos dentro: cuatro
  tests de geometría/formato en verde. Acepta --fuente para procedencia correcta.
- Corregida la aceptación de HTML/objetos sin elements como calles vacías. Era un
  fallo reproducido del test existente de Overpass. El mismo filtro se usa al leer estáticas.
- Timeout estático 8 s, conexión 5 s; conserva propagación de cancelación.
- Workflow generador prueba en PR; solo publica desde el repo principal en dispatch/schedule.

## Limpieza ejecutada

- Retirado únicamente `detekt-cli.zip` de raíz (61,37 MiB); CLI extraída conservada.
- Nuevo ignore de ese ZIP. Sesión antigua archivada íntegra, sin perder gotchas.
- `PROPUESTA_LIMPIEZA_2026-11-04.md` detalla medición SHA-256 y qué se conservó.
- No borrar GEN canónico ni las carpetas antiguas enteras: contienen variantes únicas.
- `PR_DESCRIPTION.md` ya estaba sin seguimiento al arrancar: no mezclar su texto viejo.

## Verificación en curso

- Kotlin/Native test names: OK.
- Build Android + tests app/shared: en ejecución; primer intento detectó el fallo HTML
  de Overpass, ya corregido. Confirmar XML y resultado de la última ejecución.
- detekt completo con baseline: en ejecución.
- Emulador Small_Phone_API_35 arrancado; falta instalar APK y grabar evidencia.
