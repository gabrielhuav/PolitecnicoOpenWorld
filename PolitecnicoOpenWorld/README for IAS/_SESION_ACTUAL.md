# 🧠 MEMORIA COMPARTIDA ENTRE IAs — estado vivo del trabajo

> **Qué es esto.** Varias IAs distintas trabajan aquí y **ninguna recuerda nada**. Este archivo es
> el ÚNICO traspaso: lo que esté aquí lo sabrá la siguiente; lo que no, se pierde o se alucina.
>
> **Reglas duras:** ventana de **2 días** (lo más viejo se purga a `_ARCHIVO/`) · techo de **200
> líneas** (se poda, no se justifica) · aquí va **estado medido, lo que está a medias y las
> trampas caras**, NO diseño ni historia (eso va al doc del área) · **marca lo MEDIDO vs lo
> SUPUESTO** (este repo ya tuvo docs que mentían: "0 smells", "47 tests").
>
> **Al cerrar sesión:** purga lo viejo · reescribe tu sección (qué cambió, qué se verificó, **qué
> falta**) · actualiza PENDIENTE · comprueba las 200 líneas. Si te quedas sin tokens a media
> tarea, **actualiza ESTE archivo ANTES de parar**.

**Última actualización:** 2026-07-28 · Codex 5.6 (Windows) · rama `fase0-auditoria-kmp` · *purgar §3bis el 07-30*

> ➡️ **AHORA:** 1.0.0.14 en revisión en Play. **🥊🍏 LA PELEA DE SF CORRE, SE JUEGA Y SE GUARDA EN
> iOS** — verificada en el simulador (§3terdecies): ronda completa, persistencia al minimizar y al
> cerrar/reabrir. **218 tests, 0 fallos** en las dos plataformas. Falta: **que alguien la OIGA**,
> `MainMenuScreen` a `commonMain` (iOS aún no tiene menú) y la Fase 6.

## 🖥️ Rutas por PC

| PC | Raíz del PROYECTO (aquí están `gradlew` y `tools/`) |
|---|---|
| **Laptop** (referencia) | `C:\Users\gabri\AndroidStudioProjects\PolitecnicoOpenWorld\PolitecnicoOpenWorld` |
| **Escritorio** (MEDIDO 07-28) | `C:\Users\gabri\Documents\GitHub Desktop\PolitecnicoOpenWorld\PolitecnicoOpenWorld` |
| **🍏 Mac** (iOS) | `/Users/gabrielhuav/Documents/GitHub/PolitecnicoOpenWorld/PolitecnicoOpenWorld` |

⚠️ **La carpeta es doble**: el proyecto Gradle está DENTRO del repo, en `PolitecnicoOpenWorld/`; las
rutas de los docs son **relativas a la raíz**. El GEN de sprites vive FUERA, en `..\newSFAssets\GEN_*`.
🆕 **PC nueva → `SETUP_PC_NUEVA.md`.** ⚠️ `gradle-wrapper.jar` NO viaja por git y sin él `gradlew` ni
arranca; `secrets.properties` tampoco. ⚠️ **La ruta del escritorio lleva ESPACIO**: entrecomilla siempre.

## 1. Organización y delegación

Mapa en **`00_INDEX.md`**. No te saltes **`10_ARQUITECTURA_SEPARACION.md`** (dónde vive cada cosa)
ni **`09_CONVENTIONS_GOTCHAS.md`**; y **`PLAYSTORE_formulario_seguridad_datos.md`** antes de tocar
la ficha. `SF/` = peleas · `MUNDO/` = mundo libre · `_ARCHIVO/` = histórico, NO tareas.
**Delegar:** alta dificultad → **Sol 5.6 / Fable 5** · media → **Opus 4.8/5** · baja → **Gemini 3.6**.
Pásales rutas absolutas, comando exacto, cómo se verifica, qué NO tocar y el `10_...` para orientarse.

## 3bis. Fases 0-4 — trampas VIVAS (detalle en `PLAN_MIGRACION_KMP.md` y `git log`)

- ⚠️ **NO cambies la ruta de la BD** (`filesDir/databases/pow_roads.db`) ni el driver de Android:
  se perdería caché y landmarks del Diseñador.
- ⚠️ **`PowJson` imita a Gson a propósito**; tocarlo rompe saves y clientes viejos EN SILENCIO.
  **`SfArcadeRepository`** es el ÚNICO con migración real de datos (`putStringSet` → JSON `_V2`).
- ⚠️ **`GeoPoint` = port LITERAL de osmdroid**: `x*x` ≠ `.pow(2)`, no lo "simplifiques". Y **Ktor
  sin `HttpTimeout`** con ping 25s/20s: evitan que se caiga la partida.
- ⚠️ **`dependencies { add("kspAndroid"…) }` va DESPUÉS de `kotlin { }`** en `shared/build.gradle.kts`.
- 🔴 **Dos bugs que el compilador NO ve:** `PowJson.encodeToString(x)` **PETA EN RUNTIME** si `x` no
  es serializable → con mapas usa `jsonOf`; y **kotlinx PETA si falta un campo sin default**.

## 3ter. iOS arrancó (Mac) — el mapa Leaflet corre en el simulador

`WorldMapLeafletHtml.kt` en `:shared`, que produce el framework `Shared`; la app iOS está en
`iosApp/` (SwiftUI + `WKWebView`).
- ⚠️ **El runtime del simulador lo instala XCODE** (no un DMG a mano: queda en cuarentena y los
  tests mueren con `Abort trap` 134). **Genera el framework ANTES de abrir Xcode** (en el Mac) o
  sale `No such module 'Shared'`. Y `gradle-wrapper.jar` no viaja por git → `SETUP_PC_NUEVA.md`.
- ✅ La deuda de assets que decía esta sección **ya NO existe**: están en el bundle (§3undecies).

## 3quater-septies. Kotlin 2.3.21 · AGP 9 · refactor · motor (cerrados; trampas VIVAS)

- ⚠️⚠️ **NO SE PUEDE SUBIR A KOTLIN 2.4: KSP NO EXISTE para 2.4** y aquí es obligatorio (Hilt +
  Room). **2.3.21 es el techo.** No pierdas una sesión intentándolo.
- ⚠️ **AGP 9 obliga a `android.builtInKotlin=true`** (`:app` ya no aplica `kotlin-android`) y a
  `com.android.kotlin.multiplatform.library` en `:shared`. Y los tests de `:shared` son
  **`testAndroidHostTest`**, no `testDebugUnitTest`.
- ⚠️ **Patrón PARCIAL:** los CAMPOS se quedan en la clase; **NUNCA recrees ahí una función de un
  parcial** (gana la clase EN SILENCIO) — `10_ARQUITECTURA_SEPARACION.md` §8. Y **dos constantes
  inventadas** (`DRAIN_PER_SEC`=200f, `+ STAGE_PADDING`) ya las fijan tests.
- 🆕 **GUARDA** `tools/check_kmp_test_names.sh` en el `pr-quality-gate`: los nombres de test con
  `(` `)` `,` compilan en la JVM y rompen Native. **Documentarlo NO bastó: pasó 3 veces.**

## 3octies. 🍏 iOS = SOLO el modo pelea (decisión del dueño, 07-27)

En iOS el menú muestra **únicamente AJUSTES, COLECCIONABLES y HUELUM VS. GOYA**; Mundo Libre, Modo
Historia y Multijugador se **esconden**. **PROBADO: el menú de Android sigue idéntico.**
- El catálogo vive en **`PowModos.kt`** (`:shared`); lo único `expect/actual` es `plataformaActual`.
  `PowModosTest` se pone rojo si alguien añade un modo a iOS: obliga a confirmar que FUNCIONA en el
  simulador, no solo que el botón aparece.
- ⚠️ **Esconder el botón NO porta el modo:** falta la UI (~14 000 líneas). Orden en `PLAN_SF_EN_iOS.md`.

## 3nonies. Fase 5 — Compose MP y el muro de `android.graphics` (cerrado; trampas VIVAS)

**⚡ Kotlin/Native SÍ compila los klibs de iOS DESDE WINDOWS**: el type-check de iOS no exige Mac.
⚠️ Pero **`linkDebugFrameworkIosSimulatorArm64` MIENTE** fuera de un Mac: dice BUILD SUCCESSFUL y no
crea nada. Nuevo en `:shared`: `PowImagen`, `PowAudio`, `PowAssets`, `PowViewModel`, `PowCerrojo`,
`SfVocesReglas`, `SfSharedSheets`, `SfFrameCatalog`.
- 🎁 **Los gráficos NO necesitaron `expect/actual`** (tabla en `PowImagen`). ⚠️ Única excepción,
  **`decodificarReducido`**: sin `inSampleSize` sería REGRESIÓN DE MEMORIA en Android.
- ⚠️⚠️ **`iosX64` SE RETIRÓ y debe seguir fuera**: Compose MP no publica para ese target y TODOS los
  source sets fallan con `Unresolved platforms: [iosX64]`, error que no menciona a Compose.
- ⚠️ **NO se usó el `lifecycle-viewmodel` KMP oficial**: su única versión con iOS exige
  `compileSdk 37`. ⚠️ `@Synchronized`/`LruCache` no existen en común → `PowCerrojo` y LRU a mano.

## 3decies. 🔊 El audio de SF, multiplataforma — y las reglas de voz con red

`android.media` fuera de SF. Faltaba el **aviso de fin de clip** (`PowClip.alTerminar`): sin él el
mapa de voces no se vacía y el peleador acaba mudo.
- ⚠️⚠️ **El `delegate` de `AVAudioPlayer` es una referencia DÉBIL:** si no se guarda en un campo del
  clip, el aviso **no llega nunca**. ⚠️ **`SoundPool.play` abre un flujo NUEVO cada vez** y
  **`AVAudioPlayer.duration` viene en SEGUNDOS** (Android da ms): sin ×1000, subtítulos de 1 ms.
- 🎁 **Las 4 reglas de interrupción salieron a `SfVocesReglas` con 14 tests.** ✅ **`SfArcadeRepository`
  ya no usa `org.json`**, con 2 tests que fijan el snapshot V1 y el `"null"` literal.

## 3undecies. 🍏 Compose MP corre en iOS · assets en el bundle · strings desbloqueados

- ✅ **107 MB en `<bundle>/assets/STREETFIGHTER/`** con las subcarpetas intactas. ⚠️ **NO por
  "folder reference" azul:** el proyecto usa `PBXFileSystemSynchronizedRootGroup` (Xcode 16), que
  APLASTA subcarpetas. Va por **fase de script `rsync`**, que además evita duplicar 106 MB. Exige
  **`ENABLE_USER_SCRIPT_SANDBOXING = NO`**.
- ⚠️⚠️ **Compose MP ABORTA (SIGABRT) si falta `CADisableMinimumFrameDurationOnPhone` en el
  Info.plist**, y el crash NO nombra la clave. Encadena 3 trampas más: `INFOPLIST_KEY_…` no sirve,
  el plist no puede vivir en `iosApp/POW/`, y a mano hay que reponer `CFBundleIdentifier`.
  Todo en `iosApp/README.md`.
- ✅ **`R.string` desbloqueado:** 196 claves en `commonMain/composeResources/values{,-en}` y `Res`
  público en `ovh.gabrielhuav.pow.shared.recursos`. Patrón: `R.string.x` → `Res.string.x` con el
  `stringResource` de `org.jetbrains.compose.resources`.
- 📏 **Coste:** framework de 255 MB → **66 MB** de app enlazada. 🔴 **DECISIÓN PENDIENTE (Fase 6):**
  el `deployment target` 16.0 se queda corto — la ICU de Compose MP es para **iOS 18.5**.

## 3duodecies. 🍏 Las 5 pantallas SE VEN en el simulador (Mac, 07-28)

**VISTAS Y CORRECTAS:** Tutorial · Selector de escenario **con las miniaturas reales de ESCOM** ·
Menú de modos · Overlay de carga con la fuente arcade.

- ⚠️⚠️ **LOS `composeResources` NO IBAN AL BUNDLE → la app se CERRABA al abrir cualquier pantalla.**
  Compose los carga en una **corrutina**, así que la excepción no la recoge nadie: sin error y sin
  log, solo un `.ips`. **La trampa de fondo:** `link*Framework*` genera los recursos de **TEST pero
  no los de Main**. Arreglado con un `dependsOn` en `shared/build.gradle.kts` + la copia en la fase
  de Xcode. ⚠️ **NO llamando a Gradle desde Xcode**: allí no hereda `JAVA_HOME`.
- 🆕 `SfEscaparate` es un índice navegable: detector de assets, audio y recursos.

## 3terdecies. 🥊🍏 LA PELEA CORRE EN iOS — verificada en el simulador (Mac, 07-28)

**El modo pelea es JUGABLE en iOS.** Motor offline (`StreetFighterViewModel` + 7 parciales) en
`commonMain`; BT/LAN/WebRTC siguen Android-only. **MEDIDO: 218 tests (114+104), 0 fallos, en las
dos plataformas; guarda de nombres KMP verde.**

**VISTO en el simulador, paso a paso:** selección de peleador con sprites y el candado del
bloqueado → dificultad → **pelea real** (escenario del IPN, sprites, barras, KO, contador de
golpes, daño y animación de derribo) → **ronda completa hasta `PERDISTE` con REINTENTAR/SALIR**.
- ✅ **PERSISTENCIA COMPLETA, la cadena entera:** al mandar la app al fondo escribe el snapshot en
  `NSUserDefaults` (`playerId`, paso 1/15, escalera de 15 rivales, `mapFile`, `cpuRoundWins`,
  `paused:true`); al volver sale **PAUSA + Continuar** con el marcador intacto; y tras **cerrar y
  reabrir** ofrece *"Hay una pelea de arcade a medias, ¿retomar?"* → CONTINUAR reanuda de verdad.
- ⚠️ **Lo que NO puedo verificar yo: OÍR.** El audio carga (`.m4a` y `.mp3` dan clip no nulo y
  `reproduciendo=true`), pero que suene bien —mezcla, cortes de voz, música— **lo tiene que
  escuchar una persona**. Es lo único del guion que queda sin firmar.
- 📐 **Observación:** la pelea gira a **horizontal** y los overlays de pausa/resultado vuelven a
  vertical. Funciona, pero conviene comparar con Android antes de darlo por bueno.

## 4. PENDIENTE — por prioridad

### 🔴 P0 · Antes/durante el release
1. **Probar el multijugador en dispositivo:** que los 2 jugadores **oigan** lo mismo (tag
   `SF-NET`). BT y LAN son lo que hay que validar sí o sí.
2. **Redeploy de `MultiplayerSF/` en Render** para activar el P2P (no bloquea el release).
   Con 2 teléfonos en redes distintas, buscar en logcat `SF-RTC`: `DataChannel → OPEN`.
3. **🍏 ✅ La pelea en iOS YA ESTÁ VERIFICADA (§3terdecies).** Queda: **(a) que una persona OIGA**
   una pelea en el simulador (yo no puedo); **(b) `MainMenuScreen` a `commonMain`** — hoy iOS no
   tiene menú, solo el escaparate; **(c)** comparar con Android la rotación pelea/overlays.

### 🟠 P1 · AUDIO (activo) — ver `SF/PROMPT_traspaso_audio_subtitulos.md`
**29 clips demasiado largos** y **5 fuera de −16 ±2 LUFS** → **Gemini 3.6** (con los segundos del
dueño). **Faltan `attack`/`hurt`** en 6 peleadores → el dueño graba. ⚠️ **No repitas** el resumen
que dice "100 % normalizados y sin faltantes": está medido y es **falso** (de los 82 `.m4a` solo
**69 son voz**; los SFX globales no se normalizan como voces).

### 🟢 P2 · Animaciones congeladas (el arte se repite, **no es bug de código**) · 🔵 P2b motor
`stun-1==stun-2==stun-3` en los 18; `bonus-7/8/9/10` estáticos en `lapresidenta`; `run-4==run-5` en
4; `forwards-3==forwards-4` y `throw-2==throw-3` en 3; `super-4==super-5` en 2.
**P2b:** sigue la fase 2c (`SfEngine` + modos como estrategia), en `SF/PLAN_refactor_motor_compartido.md`.

### ⚪ P3 · Bloqueado en el dueño / deuda conocida
- **Mapas UAM Azcapotzalco y Cuajimalpa:** faltan vídeos → `tools/build_map_backgrounds.py`. Y el
  **arte V2 de La Presidenta + metamorfosis**: croma en `tools\_para_corregir\` sin importar.
- **detekt NO está a 0:** 5 smells preexistentes. Varios docs dicen "0 smells" y es **falso**.
- **Paparazzi 5** con audio de Paparazzi 1; **Señor de la tienda** con un tramo de Prankedy;
  **La Tzitzimime** mal recortada. Los 3 requieren al dueño.

## 5. Verificación antes de cerrar CUALQUIER sesión

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :shared:testAndroidHostTest
```
En Windows `.\gradlew.bat`. En el Mac, antes: `export JAVA_HOME="/Applications/Android
Studio.app/Contents/jbr/Contents/Home"`, y además `:shared:iosSimulatorArm64Test` +
`:shared:linkDebugFrameworkIosSimulatorArm64`. ⚠️ La tarea de `:shared` es **`testAndroidHostTest`**
(cambió con AGP 9). ⚠️ **Son 218: 114 `:app` + 104 `:shared`**; los "190"/"184"/"178"/"47" de
docs viejos son **stale**. ⚠️ Si tocas `commonTest`, pasa **`bash tools/check_kmp_test_names.sh`**.

**detekt — EXACTAMENTE la invocación de CI** (desde la raíz del repo):
```bash
./detekt-cli-1.23.8/bin/detekt-cli --config PolitecnicoOpenWorld/config/detekt/detekt.yml --build-upon-default-config --input PolitecnicoOpenWorld/app/src/main/java,PolitecnicoOpenWorld/shared/src/commonMain/kotlin --baseline PolitecnicoOpenWorld/config/detekt/baseline.xml
```
Debe salir **exit 0**. ⚠️ Un doc viejo decía "NO uses `--build-upon-default-config`": era de antes
del baseline. **CI SÍ lo usa.** ⚠️ El input incluye `:shared`, o no se analiza el módulo compartido.
`git status` debe mostrar **solo** lo que tocaste. Y **actualiza este archivo** antes de terminar.
