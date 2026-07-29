# 🍏 Mensaje para continuar SF en el Mac

Sigo con POW. Repo en el Mac (carpeta doble):

`/Users/gabrielhuav/Documents/GitHub/PolitecnicoOpenWorld/PolitecnicoOpenWorld`

Rama: `fase0-auditoria-kmp`. Haz `git pull` antes de nada. El trabajo de Windows termina en
`f7cd2c77` y ya está en `origin`.

Lee primero:

- `README for IAS/_SESION_ACTUAL.md`, especialmente §3undecies y PENDIENTE.
- `README for IAS/PLAN_SF_EN_iOS.md`.
- `iosApp/README.md`.

## Lo que ya está hecho y no debes rehacer

- Los 107 MB de assets funcionan en iOS en `<bundle>/assets/STREETFIGHTER/`.
- **No toques** la fase `rsync`, el proyecto Xcode, `Info.plist` ni las rutas de assets.
- Los 82 OGG fueron sustituidos por 82 M4A/AAC. Android ya reproduce los M4A.
- Las 7 pantallas de SF están en `shared/commonMain`: las cinco nuevas son
  `SfTutorialOverlay`, `SfStageSelectOverlay`, `SfMenuOverlays`, `SfSceneRenderer` y
  `StreetFighterScreen`.
- `SfOnlineOverlays.kt` sigue Android-only a propósito: iOS no tiene multijugador.
- Windows dejó verdes 218 tests, Kotlin/Native, detekt y el gate de nombres.

## 1. Compilar y abrir en el simulador

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
cd "/Users/gabrielhuav/Documents/GitHub/PolitecnicoOpenWorld/PolitecnicoOpenWorld"
chmod +x gradlew
./gradlew :shared:compileKotlinIosSimulatorArm64
./gradlew :shared:iosSimulatorArm64Test
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
open "iosApp/POW.xcodeproj"
```

Ejecuta el target POW con ⌘R. Si sale `No such module 'Shared'`, no se generó el framework en
`shared/build/bin/iosSimulatorArm64/debugFramework/Shared.framework`.

## 2. Probar primero el audio

En la pestaña SF, pulsa `light-attack.m4a` y `prankedy_lobby.mp3`.

- Ambos deben devolver clip no nulo.
- Ambos deben sonar.
- Si M4A falla, comprueba el archivo dentro del bundle; **no cambies la ruta ni vuelvas a OGG**.
- Después prueba música, intro, golpes y voces de una pelea. No basta con que el botón diga
  `reproduciendo=true`: hay que oírlo.

## 3. Revisar visualmente las cinco pantallas

Amplía temporalmente `SfEscaparate.kt` para poder abrir, una por una:

1. `SfTutorialOverlay`
2. `SfStageSelectOverlay`
3. `SfMenuOverlays`
4. `SfSceneRenderer`
5. `StreetFighterScreenCommon`

Compara con Android: textos, proporciones, recortes de sprites, fondos, controles y orientación.
Corrige solamente incompatibilidades iOS en `shared`/`iosMain`; no cambies la UI Android ni los
assets para esconder un fallo del simulador.

## ⚠️ Bloqueador real de la pelea completa

`StreetFighterScreenCommon` recibe un `StreetFighterController`. Android tiene
`AndroidStreetFighterController`, que adapta `StreetFighterViewModel` + Hilt, pero todavía **no
existe un controlador iOS** y el ViewModel de combate sigue en `:app`.

Por tanto:

- Un controlador de muestra sirve para revisar layouts, pero **no cuenta como pelea portada**.
- Para arrancar una pelea real hay que implementar el controlador/estado iOS reutilizando el motor
  compartido o bajar la lógica restante del ViewModel a `shared`.
- No copies la lógica de combate a `iosMain` y no inventes un segundo motor.
- Si esa bajada resulta mayor que esta sesión, deja medido el primer import/dependencia Android que
  bloquea, actualiza `_SESION_ACTUAL.md` y prepara el siguiente prompt. No marques SF como terminado.

## 4. Verificación obligatoria

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :shared:testAndroidHostTest
./gradlew :shared:compileKotlinIosSimulatorArm64
./gradlew :shared:iosSimulatorArm64Test
bash tools/check_kmp_test_names.sh
```

Además, en el simulador:

- Ver las cinco pantallas.
- Oír M4A y MP3.
- Si ya existe controlador real: empezar una pelea, atacar, minimizar, volver, pulsar Continue y
  comprobar que música/efectos/estado se reanudan.

Al terminar, actualiza `_SESION_ACTUAL.md` sin superar 200 líneas y di exactamente qué falta.
Un commit por bloque lógico. Misma rama compartida: `git pull` inmediatamente antes de cada push.
