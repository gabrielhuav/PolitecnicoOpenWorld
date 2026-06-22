# Modo Diseñador: motor de precisión para rutas/estacionamientos

## Resumen
Reimplementa la captura de coordenadas del **Modo Diseñador** sobre el refactor MVVM
actual. El flujo viejo calculaba el punto local y lo **volcaba a Logcat**; ahora se
acumula en memoria de forma reactiva y se **exporta a un `.json` real** vía SAF.
La matemática GPS→local es idéntica a la anterior (no se mueve ningún nodo ya capturado),
pero queda extraída en un caso de uso **puro y testeable**.

## Qué cambia
- **`CalculateLocalCoordinatesUseCase`** (nuevo, `domain/usecases/`): caso de uso PURO
  (sin Android/OSMDroid) que convierte un punto GPS global a coordenada local `[0,1]`
  de la textura de un edificio. Misma fórmula que `Landmark.toLocalCoordinates`
  (proyección esférica, compensación por coseno en longitud, rotación inversa,
  normalización por `baseWidthMeters*scaleX` / `baseHeightMeters*scaleY`). Expone
  `LocalCoordinate(x, y).isValid(margin = 0.15f)` para validar el overscan legacy.
- **`DesignerViewModel`** (nuevo, `features/map_exterior/viewmodel/`): clase plana con
  `StateFlow<DesignerState>` (id de carril, modo estacionamiento, nodos capturados).
  Intenciones: `toggleParkingMode`, `startNewLane`, `onCaptureClicked(landmark, loc)`
  y `serializeNodesToJson()` (mismo formato JSON que el flujo legacy).
- **`WorldMapScreen`**: sostiene el `DesignerViewModel` con `remember`, observa su estado
  con `collectAsState()` y añade un `routeExportLauncher`
  (`ActivityResultContracts.CreateDocument("application/json")`) que escribe el JSON al
  `uri` elegido por el usuario. Cablea los callbacks del panel (CAPTURAR usa el caso de
  uso; al exportar limpia las migas y avanza de carril).
- **`DesignerPanel`**: nuevo botón **«EXP. RUTA»** + parámetro `onExportRoute`.
- **`WorldMapViewModel` / `WorldMapState`**: se elimina la lógica de depuración vieja
  (`debugPlayerLocalCoordinates`, `startNewWay`, `toggleParkingMode`, `debugNodeIdCounter`,
  `isParkingSlotMode`, `currentWayId`). Se conserva solo el rastro visual en el mapa con
  `addRouteBreadcrumb` / `clearRouteBreadcrumbs` sobre `routeDebugWaypoints`.

## Por qué
- **Testeable**: la matemática vive en una función pura, sin dependencias de Android.
- **MVVM estricto**: la View solo observa estado inmutable y emite intenciones; el cálculo
  y la acumulación quedan fuera de la UI y del `WorldMapViewModel`.
- **Usable**: exporta un archivo de verdad (SAF) en lugar de obligar a leer Logcat.

## Cómo probar
1. Activar Modo Diseñador y seleccionar un edificio.
2. (Opcional) marcar «Estacionamiento» para que los nodos sean cajones.
3. **CAPTURAR** sobre puntos dentro del edificio → Toast «Nodo N capturado»
   (fuera del edificio → «Estás fuera del edificio»).
4. **EXP. RUTA** → elegir destino → se guarda `carril_<id>.json`; el carril se reinicia.
5. **NUEVO** empieza otro carril y limpia las migas del anterior.

## Notas
- No requiere migración de datos: el formato JSON exportado es el mismo de antes.
- `Rebuild Project` en Android Studio (no se puede compilar desde aquí).

## Docs a actualizar (protocolo 09)
- **Doc 04/05 «Key files»**: añadir `CalculateLocalCoordinatesUseCase.kt` y
  `DesignerViewModel.kt`; quitar referencias al flujo `debugPlayerLocalCoordinates`.
- **Doc 09 (gotchas)**: nota de que `WorldMapState` ya no tiene `isParkingSlotMode` ni
  `currentWayId`.
- **README.md** (bilingüe): el Modo Diseñador es herramienta de desarrollo, no user-facing,
  así que no requiere entrada salvo que quieras documentar el export `.json`.
