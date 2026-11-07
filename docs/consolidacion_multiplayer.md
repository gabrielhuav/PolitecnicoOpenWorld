# Consolidación de validación multijugador

Fecha: 2026-11-06. Integración en el PR #143, conservando La Planchada y ESM.

## Cambios incorporados

- [#167](https://github.com/gabrielhuav/PolitecnicoOpenWorld/pull/167), fuente `3207af6b`: `PlayerNameRules` compartido, nombres de 3–16 unidades UTF-16 salvo el nombre vacío con respaldo `Jugador_XXXX`, normalización de espacios/controles, contador y errores en español e inglés. Se normaliza el nombre guardado y se revalida antes de guardar o navegar. Los nombres remotos se acotan sin partir pares sustitutos; la ruta de NPCs se conserva.
- [#172](https://github.com/gabrielhuav/PolitecnicoOpenWorld/pull/172), fuente `0a368b69`, y [#176](https://github.com/gabrielhuav/PolitecnicoOpenWorld/pull/176), fuente `5e376faf`: una sola regla `SfLanIp` en `shared`, con filtro de entrada, IPv4 decimal de cuatro octetos 0–255 sin ceros iniciales y error localizado al completar una entrada inválida. La UI valida tanto al habilitar Unirse como al ejecutar su callback. Cancelar queda fuera del contenido desplazable. Código de sala y descubrimiento LAN conservan sus rutas.
- [#182](https://github.com/gabrielhuav/PolitecnicoOpenWorld/pull/182): su implementación ya está en `main` mediante #158 (`48c3f6ee`). Se verificó la equivalencia de sus siete archivos: tres idénticos y cuatro con mejoras posteriores. No se vuelve a aplicar su rama antigua. La rama `datos-calles` ya contiene 117 celdas; la celda de ESCOM `975_-4958` contiene 3427 vías con referencias válidas.

Los helpers y pruebas de nombres/IP se adaptaron a la estructura actual. No se mantienen dos validadores LAN ni se modifica la copia histórica comentada de la UI Android.

## Verificación

Desde `PolitecnicoOpenWorld/`:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :shared:testAndroidHostTest
```

Resultado: compilación de ambos APK aprobada; **485 pruebas unitarias** aprobadas (**155 app + 330 shared**), sin fallos, errores ni omitidas. El total incluye 32 pruebas de nombres y 12 de IPv4. Detekt con configuración/baseline del proyecto y la guarda de nombres Kotlin/Native también pasan.

Las **5 pruebas instrumentadas** de `SfLanJoinValidationTest` pasaron en Android 15/API 35 usando la Activity real del juego. Comprueban clic táctil válido, rechazo de un callback previamente habilitado al cambiar a una IP inválida, mensajes en español/inglés, Cancelar completamente visible con fuente 1.3 y regresión de sala/descubrimiento. [Registro final](../pr-screenshots/multiplayer-validation/lan-instrumentation.txt). Los callbacks de prueba no abren una conexión real con otro dispositivo.

La suite completa del generador OSM no se volvió a ejecutar: esta consolidación no cambia los archivos ya incorporados de #182.
