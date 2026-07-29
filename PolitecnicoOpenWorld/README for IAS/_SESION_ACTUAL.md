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

**Última actualización:** 2026-07-28 · Opus 5 (Mac) · rama `fase0-auditoria-kmp` · *purgar §3bis el 07-30*

> ➡️ **AHORA:** 1.0.0.14 en revisión en Play. KMP: Fases 0-4 completas; **la 5 EN MARCHA, 218
> tests**. 🍏 **Compose MP YA CORRE en el simulador y los 107 MB de assets están en el bundle**
> (§3undecies); del paso 4 va **1 de 7** pantallas.
> **Siguiente y BLOQUEANTE: migrar los strings a `composeResources`** — las 6 pantallas que faltan
> usan `R.string`, que no existe en `:shared`. Sin eso el paso 4 no avanza.

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

## 3nonies. 🍏🥊 Fase 5 EN MARCHA — Compose MP y el muro de `android.graphics`, cruzado

**⚡ Kotlin/Native SÍ compila los klibs de iOS DESDE WINDOWS** (`compileKotlinIosSimulatorArm64`),
con los cinterops de Apple resueltos: **el type-check de iOS ya no exige Mac**. ⚠️ Pero
**`linkDebugFrameworkIosSimulatorArm64` MIENTE** fuera de un Mac: dice BUILD SUCCESSFUL y no crea
nada. NO lo uses como prueba.

**PROBADO EN EL EMULADOR** dos veces: sprites y audio. Nuevo en `:shared`: `PowImagen`, `PowAudio`,
`PowAssets`, `PowViewModel`, `PowCerrojo`, `SfVocesReglas`, `SfSharedSheets`, `SfFrameCatalog`.
**`android.graphics` y `android.media` han DESAPARECIDO de `features/streetfighter`** (medido: 0).
- 🎁 **Los gráficos NO necesitaron `expect/actual`**: `ImageBitmap`/`Canvas`/`readPixels` ya son
  multiplataforma; el "muro" se cruza TRADUCIENDO llamadas (tabla en `PowImagen`). ⚠️ Única
  excepción, **`decodificarReducido`**: sin `inSampleSize` sería REGRESIÓN DE MEMORIA en Android.
- ⚠️⚠️ **`iosX64` SE RETIRÓ y debe seguir fuera**: Compose MP 1.11.1 no publica para ese target y
  TODOS los source sets fallan con `Unresolved platforms: [iosX64]`, error que no menciona a Compose.
- ⚠️ **NO se usó el `lifecycle-viewmodel` KMP oficial**: su única versión con iOS exige
  `compileSdk 37`. ⚠️ `@Synchronized`/`LruCache` no existen en común → `PowCerrojo` (REENTRANTE) y
  LRU a mano; `recycle()` se BORRA, no se sustituye.

- 📏 **Coste:** framework de 251 MB (153 682 símbolos de Compose) → **66 MB** de app enlazada;
  enlazar tarda ~3 min y come 4 GB. 🔴 **DECISIÓN PENDIENTE (Fase 6):** el `deployment target` 16.0
  se queda corto — la ICU de Compose MP está compilada para **iOS 18.5**.

## 3decies. 🔊 El audio de SF, multiplataforma — y las reglas de voz por fin con red

`android.media` fuera de SF. Lo que faltaba en la API era el **aviso de fin de clip**
(`PowClip.alTerminar`): sin él el mapa de voces no se vacía y el peleador acaba mudo porque su
intro, terminada hace rato, sigue apuntada como en curso.
- ⚠️⚠️ **El `delegate` de `AVAudioPlayer` es una referencia DÉBIL:** si no se guarda en un campo del
  clip, el aviso **no llega nunca**, sin error y sin log. ⚠️ **`SoundPool.play` abre un flujo NUEVO
  cada vez** (eco si no paras el previo), y **`AVAudioPlayer.duration` viene en SEGUNDOS** (Android
  da ms): sin ×1000 los subtítulos de iOS durarían 1 ms.
- 🎁 **Las 4 reglas de interrupción salieron a `SfVocesReglas` con 14 tests** (estaban afinadas de
  oído). ✅ **`SfArcadeRepository` ya no usa `org.json`**, con 2 tests que fijan el snapshot V1 y el
  `"null"` literal; probado en el emulador minimizando y reanudando.

## 3undecies. 🍏 Compose MP CORRIENDO en iOS + los assets en el bundle (Mac, 07-28)

**MEDIDO EN EL SIMULADOR: `SfBitmapText` pinta "HUELUM VS GOYA" con la fuente arcade** → prueba la
cadena entera: Compose MP vivo en iOS → `PowAssets` leyendo del bundle → `PowImagen` → `@Composable`
de `commonMain` dibujando. Android intacto: **114 + 104 = 218 tests, 0 fallos.**
- 🆕 **`SfEscaparate.kt` (`iosMain`) + pestaña SF en `iosApp`**: el hueco donde MIRAR cada pantalla
  portada. Antes no existía → "portado" solo podía significar "compila".
- ⚠️⚠️ **Compose MP ABORTA (SIGABRT) si falta `CADisableMinimumFrameDurationOnPhone` en el
  Info.plist**, y el crash NO nombra la clave (hay que abrir el `.ips` de `~/Library/Logs/
  DiagnosticReports`). Encadena 3 trampas: `INFOPLIST_KEY_…` **no sirve** (solo vale para claves que
  Xcode conoce) → hace falta un `Info.plist` real; **no puede vivir en `iosApp/POW/`** (va a Copy
  Bundle Resources → "Multiple commands produce"); y a mano hay que reponer `CFBundleIdentifier` o
  la app **ni se instala**.
- ✅ **Assets HECHOS: 107 MB en `<bundle>/assets/STREETFIGHTER/`** con `DATA`/`IMAGES`/`SOUNDS`
  intactas. ⚠️ **NO por "folder reference" azul como decía el plan:** el proyecto usa
  `PBXFileSystemSynchronizedRootGroup` (Xcode 16), que APLASTA subcarpetas. Se hace con una **fase
  de script `rsync`** a `${BUILT_PRODUCTS_DIR}/${UNLOCALIZED_RESOURCES_FOLDER_PATH}/assets/`, que
  además evita duplicar 106 MB en `iosApp/`. Exige **`ENABLE_USER_SCRIPT_SANDBOXING = NO`** (si no:
  `Sandbox: rsync deny(1) file-write-create`).

### 🔴 BLOQUEADOR del paso 4: `R.string` — lo usan las 6 pantallas que faltan
`SfBitmapText` (1/7) pudo bajar porque **no tenía ni un string**. Las otras seis usan **145 claves**
vía `import ovh.gabrielhuav.pow.R`, y esa `R` la genera AGP para `:app`: **no existe en `:shared`**
(`SfMenuOverlays` 57 · `StreetFighterScreen` 56 · `SfComboSheetOverlay` 19 · `SfTutorialOverlay` 9 ·
`SfStageSelectOverlay` 6 · `SfSceneRenderer` 4). → Hay que **migrar los strings a `composeResources`**
(`Res.string.*`), que hoy NO existe en el repo (`:app` tiene 701 en `values/` y 700 en `values-en/`).
Es infraestructura, no un port más, y el plan no lo contemplaba.

## 4. PENDIENTE — por prioridad

### 🔴 P0 · Antes/durante el release
1. **Probar el multijugador en dispositivo:** que los 2 jugadores **oigan** lo mismo (tag
   `SF-NET`). BT y LAN son lo que hay que validar sí o sí.
2. **Redeploy de `MultiplayerSF/` en Render** para activar el P2P (no bloquea el release).
   Con 2 teléfonos en redes distintas, buscar en logcat `SF-RTC`: `DataChannel → OPEN`.
3. **🍏 FASE 5 — que el modo pelea CORRA en iOS. Guion en `PLAN_SF_EN_iOS.md` §4.**
   Hechos: catálogo de modos, Compose MP corriendo en el simulador, **assets en el bundle**,
   gráficos, audio, ViewModel, cerrojo, `org.json` fuera, `SfSharedSheets`/`SfFrameCatalog` y
   **1/7 del paso 4** (`SfBitmapText`).
   **SIGUIENTE, y es un prerrequisito, no una pantalla más: migrar los strings a
   `composeResources`** (§3undecies). Sin eso las 6 pantallas restantes NO pueden bajar.
   Después, en orden: `SfComboSheetOverlay` → `SfTutorialOverlay` → `SfStageSelectOverlay` →
   `SfMenuOverlays` → `SfSceneRenderer` → `StreetFighterScreen`.
   ⚠️ **EN EL MAC**: cada pantalla hay que VERLA en la pestaña SF del escaparate.
### 🟠 P1 · AUDIO (activo) — ver `SF/PROMPT_traspaso_audio_subtitulos.md`
**29 clips demasiado largos** y **5 fuera de −16 ±2 LUFS** → **Gemini 3.6** (con los segundos del
dueño). **Faltan `attack`/`hurt`** en 6 peleadores → el dueño graba. ⚠️ **No repitas** el resumen
que dice "100 % normalizados y sin faltantes": está medido y es **falso** (de los 80 `.ogg` solo
**69 son voz**; 2 no se normalizan sin comprimir).

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
