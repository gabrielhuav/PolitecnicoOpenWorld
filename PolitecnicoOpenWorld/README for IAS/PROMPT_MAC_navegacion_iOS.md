# 🍏 PROMPT Mac — navegación real de iOS

Sigo con POW. Repo (carpeta doble):
`/Users/gabrielhuav/Documents/GitHub/PolitecnicoOpenWorld/PolitecnicoOpenWorld`

Rama `fase0-auditoria-kmp`. Haz `git pull` antes de nada y lee completos:

- `README for IAS/_SESION_ACTUAL.md`
- `README for IAS/10_ARQUITECTURA_SEPARACION.md` §2bis
- `iosApp/README.md`

## Estado ya verificado; no lo rehagas

- La pelea corre, se juega, suena y guarda en iOS.
- Los assets viven en `<bundle>/assets/STREETFIGHTER/` y funcionan.
- `MainMenuScreen`, Ajustes y Coleccionables ya viven en `commonMain`.
- Windows verificó Ajustes/Coleccionables en el AVD Nexus, incluidos Español/English,
  persistencia y `DEVELOPER_MODE` desbloqueando/bloqueando peleadores.
- 228 tests (114 app + 114 shared), 0 fallos; Android e iOS compilan.

## No tocar

- No cambies Kotlin 2.3.21 ni añadas `iosX64`.
- No quites el `dependsOn("assemble*MainResources")` de `shared/build.gradle.kts`.
- No cambies `Info.plist`, la fase `rsync`, la ruta del bundle ni los M4A.
- No portes Mundo Libre, Historia, BT, LAN o WebRTC a iOS.
- No escribas lógica nueva de desbloqueo: Ajustes y SF ya comparten `DEVELOPER_MODE`.

## Tarea

Sustituye el `TabView` de mapa/escaparate por la navegación real:

```text
MainMenuScreen
 ├─ SETTINGS       → SettingsScreen
 ├─ COLLECTIBLES   → CollectiblesScreen
 └─ HUELUM VS GOYA → StreetFighterScreenCommon
```

El menú es la raíz y solo muestra esos tres modos en iOS; `PowModos.kt` es la fuente de verdad.
Usa un solo `ComposeUIViewController` y estado de navegación Compose. No dupliques pantallas en
SwiftUI.

### Construcción de dependencias

- Ajustes:
  `SettingsViewModel(iosSettingsRepository())`.
  `iosSettingsRepository()` usa `NSUserDefaults.standardUserDefaults`, la misma suite que
  `IosStreetFighterEnvironment`.
- Coleccionables:
  `val db = crearPowDatabase()` y
  `CollectiblesViewModel(CollectibleRepository(db.collectibleDao()))`.
  Recuerda cerrar la BD al destruir el host si introduces un dueño de ciclo de vida.
- Pelea:
  conserva `OfflineStreetFighterController(StreetFighterViewModel(IosStreetFighterEnvironment()))`.
- Menú:
  implementa un `IosMainMenuController` pequeño para `MainMenuController`; copia la forma del
  adaptador Android y deja vacíos/ocultos Auth, cuenta, Mundo Libre, Historia y Multijugador.
  No copies `MainMenuScreen`.

En iOS, `SettingsScreen` debe omitir Cuenta y las opciones exclusivas del mundo; esto ya está
gated por slots y `PowModo.MUNDO_LIBRE.disponible()`. Para idioma no existe `Activity.recreate()`:
aplica el locale con el mecanismo iOS ya usado por Compose o recrea el host Compose de manera
controlada, sin bifurcar la pantalla.

## Verificación visual obligatoria en el simulador

1. Debe arrancar en el menú real, no en el escaparate ni en el mapa.
2. Abre Ajustes: recorre Interface y Audio; no deben aparecer Map, Controls, Gameplay ni Account.
3. Cambia Español → English y vuelve a entrar.
4. Activa Modo Desarrollador, entra a SF y confirma que todos los peleadores están desbloqueados.
5. Desactívalo y confirma que vuelven los candados.
6. Abre Coleccionables: cambia ITEMS/FIGHTERS y revisa imágenes/progreso.
7. Entra a SF, juega, vuelve al menú y reentra; no debe quedar un segundo host Compose encima.
8. Minimiza/cierra/reabre: idioma, Modo Desarrollador y partida deben persistir.

Solo cuando los ocho pasos pasen, borra:

- `shared/src/iosMain/.../features/streetfighter/ui/SfEscaparate.kt`
- el `SfEscaparateTab` y el `MapaTab` de prueba de `iosApp/POW/ContentView.swift`

No borres antes el escaparate: sigue siendo el banco de diagnóstico si la navegación falla.

## Comandos

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug :app:testDebugUnitTest :shared:testAndroidHostTest
./gradlew :shared:iosSimulatorArm64Test :shared:linkDebugFrameworkIosSimulatorArm64
bash tools/check_kmp_test_names.sh
```

Esperado: 228 tests, 0 fallos. Abre Xcode solo después de generar el framework. Si una pantalla
se cierra sin log, revisa primero `~/Library/Logs/DiagnosticReports/*.ips`.

Al terminar actualiza `_SESION_ACTUAL.md` (máximo 200 líneas), haz `git pull` inmediatamente antes
del push y deja medido qué se vio. Di exactamente qué falta.
