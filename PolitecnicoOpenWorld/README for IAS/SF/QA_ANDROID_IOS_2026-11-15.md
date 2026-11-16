# QA Android + iOS — Titulación por Combate

**Medido:** 2026-11-15 · rama `pr/184` · código `34876266`.
Prueba de humo del checkout actual, sin modificar código, assets ni firma del proyecto.
No sustituye la matriz de los 18 peleadores, dispositivos físicos o multijugador.

## Compilación y tests

Desde la raíz Gradle, con `JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home`:

```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 :shared:linkDebugFrameworkIosArm64
./gradlew :app:assembleDebug :app:testDebugUnitTest :shared:testAndroidHostTest
./gradlew :shared:iosSimulatorArm64Test
```

- Ambos frameworks: `BUILD SUCCESSFUL` (caché vigente); existen los dos archivos `Shared.framework/Shared`.
- APK y tests JVM: `BUILD SUCCESSFUL`.
- Tests iOS: `BUILD SUCCESSFUL`; 710 ejecuciones en total, contadas desde los XML:
  **134 app + 288 shared JVM + 288 shared iOS**, cero fallos, errores u omitidos.
- Xcode: esquema comprobado con `xcodebuild -list`: `iosApp.POW`.
  Build Debug del simulador: `BUILD SUCCEEDED`, siguiendo guía 14 §3.4.
  `DerivedData` fuera del repo: `/tmp/pow-qa-2026-11-15/derived-data`.
- Se conservan warnings de interoperabilidad del header generado e ICU compilado para
  iOS 18.5 frente al deployment target 16.0; no impidieron compilar ni ejecutar en iOS 27.0.
  No se probó la compatibilidad real con iOS 16.

## Control y juego observado

| Comprobación | Android | iOS |
|---|---|---|
| Dispositivo | Pixel_9_API_36, Android 16/API 36, arm64 | iPhone 17, iOS 27.0, arm64 |
| Instalar y abrir build nuevo | Sí, por adb | Sí, por simctl |
| Nombre completo del modo | Sí, menú en inglés | Sí, menú en español |
| Práctica | Alexis vs Estudiante, básica, ESCOM | Estudiante vs Estudianta, básica, ESCOM |
| Ataque y daño | Pulsaciones, ataque visible, vida y puntuación cambian | X/B repetidos: P1=300 y vida rival reducida |
| Movimiento y salto | Joystick y salto observados | Joystick y salto observados; pausa capturada en el aire |
| Pausa y navegación | Pausa, reanudación, cambiar personaje y salida | Pausa, reanudación y salida al menú principal verificadas |
| Arcade | Estudiante → Fácil → Paramédico CR en UNAM CU día; pelea cargada y salida al menú | Estudiante vs Paramédico, fácil, escenario automático; combate y pantalla PERDISTE |
| IA vs IA | Alexis vs Estudiante: partida completa, dos rondas para Estudiante, KO y ESCOMBOY WINS; controles ocultos | No ejecutado en esta sesión |

El simulador iOS se controló mediante CUA sobre el panel de simulador **ya abierto en Claude**;
no se enviaron mensajes a otro asistente. Se giró manualmente a horizontal para observar Arcade.
No se abrió Device Hub ni se aceptó ninguna ventana de contraseña o Touch ID.
Las capturas se guardaron con `xcrun simctl io booted screenshot`, según la guía 14.

RSS puntual del proceso iOS, no pico ni memoria de un iPhone real: **692288 KiB en Arcade**
y **756896 KiB durante práctica**. No se midió La Llorona ni jetsam.
No se observó cierre en los recorridos iOS. No se obtuvo un registro completo de crash de iOS.
En Android el PID de POW permaneció vivo y su logcat no contiene FATAL EXCEPTION, Fatal signal,
OutOfMemoryError ni ANR; `exit-info.txt` también se conservó. Android queda en el menú principal.
PSS/RSS puntuales Android durante IA vs IA: **473011/577120 KiB**; no son un perfil de fugas.
`sfLog()` es un no-op: los logs del motor no prueban por sí solos daño o KO; se usó el HUD.

## Conflicto de plugins Android Studio

El IDE 2026.2.2 Patch 1 no cargaba KMP `.143-AS` ni Toolchain `.245` porque ambos declaraban
`intellij.amper.ios`. Se revisaron los descriptores de los ZIP oficiales de JetBrains:
KMP **262.10968.183-AS** elimina esa duplicidad y declara soporte Xcode 27.2.
Se reemplazó KMP con el IDE cerrado, se conservó Toolchain **262.10968.245** y se reinició.

**Resultado comprobado:** ambos aparecen habilitados en Plugins sin aviso rojo; el nuevo arranque
registra ambos en `Loaded custom plugins` y no contiene `conflicts with the same id`.
Backup de KMP anterior: `/tmp/pow-qa-2026-11-15/plugins/kmm-plugin-262.10968.143-AS-backup`.
La guía 14 §1/§7 documenta la pareja compatible y el cambio de soporte del editor Swift.

## Evidencia y límites

Todo el material temporal está **fuera del repo**, en `/tmp/pow-qa-2026-11-15/`:

- `ios/frameworks.log`, `ios/xcodebuild.log`, `ios/tests.log`, `android-build-tests.log`.
- `ios/arcade-fight.png`, `ios/arcade-defeat.png`, `ios/arcade-control.mp4`.
- `ios/practice-after-attacks.png`, `ios/practice-paused.png`, `ios/practice-control.mp4`.
- `android/QA_ANDROID.md`: detalle Android. `19-practice-combat.png`, `27-ai-current.png`
  (KO final y ESCOMBOY WINS), `ai-vs-ai.mp4`, `31-arcade-ladder.png`, `pow-logcat.txt`.
- `plugins/ide-after-update.log`, ZIP oficial y copia del plugin anterior.

`/tmp` es evidencia local temporal, no un archivo permanente compartido con el repo.
No se probó audio por escucha, iPhone físico, multijugador, partida completa de Arcade,
fuente grande/pantalla SE, todos los especiales o todos los peleadores. No se modificó la sesión
Ambient de iOS. No se hizo commit, push ni merge.
