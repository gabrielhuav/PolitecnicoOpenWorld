# 🍏 PROMPT Mac — verificar la pelea real de SF en iOS

Sigo con POW. Repo:
`/Users/gabrielhuav/Documents/GitHub/PolitecnicoOpenWorld/PolitecnicoOpenWorld`

Rama `fase0-auditoria-kmp`. Haz `git pull` antes de nada y lee
`README for IAS/_SESION_ACTUAL.md`, sobre todo §3duodecies y PENDIENTE.

Windows ya bajó `StreetFighterViewModel` y sus 7 parciales offline a `commonMain`. La capa
BT/LAN/WebRTC sigue Android-only en `shared/androidMain`. Hay un controlador offline real para
iOS y `SfEscaparate` muestra una quinta entrada: **PELEA REAL (motor commonMain)**. No es un
controlador de muestra.

## No tocar

- No quites el `dependsOn("assemble*MainResources")` de `shared/build.gradle.kts`.
- No cambies la fase `rsync`, el `Info.plist`, la ruta `<bundle>/assets/STREETFIGHTER/` ni los M4A.
- Kotlin se queda en 2.3.21; `iosX64` sigue fuera.
- No portes BT/LAN/WebRTC a iOS.

## Verificación en el Mac

1. Genera y enlaza el framework como indica `iosApp/README.md`; abre Xcode después.
2. Ejecuta la app en el simulador y entra a `ESCAPARATE SF · iOS` →
   `5 · PELEA REAL (motor commonMain)`.
3. Elige personaje y práctica o arcade. Comprueba:
   - carga de escenario y sprites;
   - controles, daño, cámara y audio;
   - pausa y continuación;
   - una ronda completa hasta victoria/derrota.
4. En arcade, empieza una pelea, manda la app al fondo, vuelve y comprueba que aparece
   `PAUSED / Continue`. Después cierra y abre la app para confirmar que el snapshot de
   `NSUserDefaults` ofrece retomar.
5. Si se cierra sin log, mira `~/Library/Logs/DiagnosticReports/*.ips` antes de tocar recursos.

Ejecuta además:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug :app:testDebugUnitTest :shared:testAndroidHostTest
./gradlew :shared:iosSimulatorArm64Test :shared:linkDebugFrameworkIosSimulatorArm64
bash tools/check_kmp_test_names.sh
```

Esperado: 114 + 104 = 218 tests, 0 fallos.

Al terminar actualiza `_SESION_ACTUAL.md` (máximo 200 líneas) con lo medido. Si todo está verde,
marca la pelea iOS como verificada; si no, deja pantalla, acción exacta y `.ips`/log. Haz
`git pull` inmediatamente antes del push.
