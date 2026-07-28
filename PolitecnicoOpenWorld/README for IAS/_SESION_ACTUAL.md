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

> ➡️ **AHORA:** 1.0.0.14 en revisión en Play. KMP: **Fases 0-4 completas; la 5 EN MARCHA (190
> tests).** 🍏 iOS = solo modo pelea (§3octies) · Compose MP portado y **su CHECKPOINT YA VERIFICADO
> EN EL MAC: el framework enlaza de verdad** (§3nonies). **Siguiente: `PLAN_SF_EN_iOS.md` §4 paso 1
> (`org.json` → kotlinx), que se hace desde Windows.**

## 🖥️ Rutas por PC

| PC | Raíz del PROYECTO (aquí están `gradlew` y `tools/`) |
|---|---|
| **Laptop** (referencia) | `C:\Users\gabri\AndroidStudioProjects\PolitecnicoOpenWorld\PolitecnicoOpenWorld` |
| **Escritorio** | *distinta — COMPLETAR con la real* |
| **🍏 Mac** (iOS) | `/Users/gabrielhuav/Documents/GitHub/PolitecnicoOpenWorld/PolitecnicoOpenWorld` |

⚠️ **La carpeta es doble**: el proyecto Gradle está DENTRO del repo, en `PolitecnicoOpenWorld/`.
Solo cambia el prefijo: las rutas de los docs son **relativas a la raíz**. El GEN de sprites vive
FUERA del repo en `..\newSFAssets\GEN_*`.

## 1. Organización

Mapa completo en **`00_INDEX.md`**. No te saltes **`10_ARQUITECTURA_SEPARACION.md`** (dónde vive
cada cosa) ni **`09_CONVENTIONS_GOTCHAS.md`**; y **`PLAYSTORE_formulario_seguridad_datos.md`**
antes de tocar la ficha. `SF/` = peleas · `MUNDO/` = mundo libre · `_ARCHIVO/` = histórico, NO tareas.

## 2. A quién delegar

**Alta** (refactors grandes, lo que ya falló dos veces) → **Sol 5.6 / Fable 5** · **Media** (feature
acotada, un módulo) → **Opus 4.8/5** · **Baja** (pipeline existente, repetitivo) → **Gemini 3.6**.
**Antes de delegar:** rutas absolutas, comando exacto, cómo se verifica, qué NO tocar y, si es una IA
pequeña, **`10_ARQUITECTURA_SEPARACION.md`** para que se oriente sola.

## 3bis. Fases 0-4 COMPLETAS (detalle en `PLAN_MIGRACION_KMP.md` y `git log`)

`:shared`; `GeoPoint` propio (osmdroid: 51 → 7 archivos); Gson fuera con `PowJson`; Fase 4 =
`multiplatform-settings` + Room 2.8.4 KMP + Ktor. **Trampas VIVAS:**
- ⚠️ **NO cambies la ruta de la BD** (`filesDir/databases/pow_roads.db`) ni el driver de Android
  (el bundled es solo para iOS): se perdería caché y landmarks del Diseñador.
- ⚠️ **`PowJson` imita a Gson a propósito**; tocarlo rompe saves y clientes viejos EN SILENCIO.
  **`SfArcadeRepository`** es el ÚNICO con migración real de datos (`putStringSet` → JSON `_V2`).
- ⚠️ **`GeoPoint` = port LITERAL de osmdroid**: `x*x` ≠ `.pow(2)`, no lo "simplifiques". Y **Ktor
  sin `HttpTimeout`** con ping 25s/20s: evitan que se caiga la partida. Y **`dependencies
  { add("kspAndroid"…) }` va DESPUÉS de `kotlin { }`** en `shared/build.gradle.kts`.
- 🔴 **Dos bugs que el compilador NO ve:** `PowJson.encodeToString(x)` COMPILA y **PETA EN RUNTIME**
  si `x` no es serializable → con mapas usa `jsonOf`/`jsonArrayOf`; y **kotlinx PETA si falta un
  campo sin default** → todo campo nuevo lleva default. Los fijan
  `PayloadsSeSerializanEnRuntimeTest` y `ModelosToleranJsonIncompletoTest`.

## 3ter. iOS arrancó (Mac) — el mapa Leaflet corre en el simulador

`WorldMapLeafletHtml.kt` en `:shared`, que produce un framework (`baseName="Shared"`); la app iOS
está en `iosApp/` (SwiftUI + `WKWebView`).
- ⚠️ **El runtime del simulador lo instala XCODE** (`xcodebuild -downloadPlatform iOS`), no un DMG a
  mano: queda en cuarentena y los tests mueren con `Abort trap` (134).
- ⚠️ **Genera el framework ANTES de abrir Xcode** (`:shared:linkDebugFrameworkIosSimulatorArm64`,
  **en el Mac**) o sale `No such module 'Shared'`. Y **`gradle-wrapper.jar` está en `.gitignore`**:
  no viene por git; al regenerarlo, revierte `gradlew`/`.bat`/`.properties` y deja solo el `.jar`.
- 🔴 **Deuda:** los assets aún no se empaquetan en el bundle → el handler de iOS devuelve 404.

## 3quater. ⬆️ Kotlin 2.3.21 + DSL de AGP 9 (cerrado; trampas que siguen VIVAS)

Kotlin **2.2.10 → 2.3.21** · KSP **2.3.10** · Hilt **2.60.1** · Ktor **3.5.1** · Compose BOM **2026.06**.
- ⚠️⚠️ **NO SE PUEDE SUBIR A KOTLIN 2.4: KSP NO EXISTE para 2.4** (medido: 0 versiones). Y aquí KSP
  es obligatorio (Hilt + Room). **2.3.21 es el techo real.** No pierdas una sesión intentándolo.
- ⚠️ **Lo caro fue el DSL de AGP 9**, no Kotlin. Única vía: **`android.builtInKotlin=true`** (`:app`
  ya NO aplica `kotlin-android`; `kotlinOptions` → `compilerOptions`), y `:shared` con
  **`com.android.kotlin.multiplatform.library`** (AGP 9 ya no admite `com.android.library` con KMP).
- ⚠️⚠️ **Los tests de `:shared` son `testAndroidHostTest`**, NO `testDebugUnitTest` (cambió con AGP 9).

## 3quinquies-septies. Refactor de tamaño · motor compartido · auditoría en Mac (cerrados)

Detalle en `10_ARQUITECTURA_SEPARACION.md` y `git log`. **Trampas que siguen VIVAS:**
- ⚠️ **Patrón PARCIAL:** los CAMPOS se quedan en la clase; **NUNCA recrees en la clase una función
  de un parcial** (gana la clase EN SILENCIO). Ni muevas una extensión sobre otro tipo declarada
  dentro de la clase (doble receptor). Trampas del extractor en `10` §8.
- ⚠️ **DOS constantes inventadas que habrían cambiado el juego EN SILENCIO**: `DRAIN_PER_SEC` es
  **200f** y el tope de cámara lleva **`+ STAGE_PADDING`**. Ahora las fijan tests.
- 🆕 **GUARDA mecánica** `tools/check_kmp_test_names.sh` en el `pr-quality-gate`: los nombres de
  test con `(` `)` `,` compilan en la JVM y rompen Native. **Documentarlo NO bastó: pasó 3 veces.**

## 3octies. 🍏 iOS = SOLO el modo pelea (decisión del dueño, 07-27)

En iOS el menú principal muestra **únicamente AJUSTES, COLECCIONABLES y HUELUM VS. GOYA**. Mundo
Libre, Modo Historia y Multijugador se **esconden**. **MEDIDO: `:app` 112 + `:shared` 72 = 184
tests, 0 fallos; detekt exit 0; PROBADO en el emulador — el menú de Android sigue idéntico.**
- El catálogo vive en **`PowModos.kt`** (`:shared`). **Lo único `expect/actual` es
  `plataformaActual`**; qué modos hay es lógica PURA → **la regla de iOS se testea desde Android**.
  `PowModosTest` se pone rojo si alguien añade un modo a iOS: obliga a confirmar que FUNCIONA en el
  simulador, no solo que el botón aparece. La UI solo pregunta `PowModo.X.disponible()`.
- ⚠️ **Esconder el botón NO porta el modo.** Que el menú de iOS liste "Huelum vs. Goya" no lo hace
  jugable allí: **SF son 14 618 líneas y siguen atadas a Android** (9 archivos `context.assets`,
  6 `android.graphics`, 3 `android.media`, 4 ViewModel, +107 MB de assets sin empaquetar).
- 🆕 **`PLAN_SF_EN_iOS.md`** = el desglose medido y el orden de ataque. ⚠️ **Su §4 decía que todo
  exige Mac y eso quedó DESMENTIDO** por §3nonies: los pasos 1-3 se hacen y se type-checkean desde
  Windows; solo el simulador y el link del framework necesitan Mac.

## 3nonies. 🍏🥊 Fase 5 EN MARCHA — Compose MP y el muro de `android.graphics`, cruzado

**⚡ LO MÁS ÚTIL DE HOY: Kotlin/Native SÍ compila los klibs de iOS DESDE WINDOWS.**
`:shared:compileKotlinIosSimulatorArm64` y `compileTestKotlin...` pasan aquí, con los cinterops de
Apple resueltos (`NSBundle`, `AVAudioPlayer`). **El type-check de iOS ya no exige Mac.**
- ⚠️ **`linkDebugFrameworkIosSimulatorArm64` MIENTE**: en un host que no es Mac dice BUILD
  SUCCESSFUL y **no crea nada** (ni el directorio de salida). NO lo uses como prueba.

**MEDIDO: `:app` 112 + `:shared` 78 = 190 tests, 0 fallos; detekt exit 0.** Y **PROBADO EN EL
EMULADOR**: pelea ROBOT vs PARAMED CR, ambos de set COMPARTIDO (hojas armadas en runtime por el
código recién portado): bien ancladas, sin espejar, con daño y sin errores en logcat.

Nuevo en `:shared`: `PowImagen` (todo `android.graphics`), `PowAudio`, `PowAssets`, `PowViewModel`,
`PowCerrojo`; y `SfSharedSheets` + `SfFrameCatalog` ya viven en `commonMain`.
- 🎁 **Los gráficos NO necesitaron `expect/actual`**: `ImageBitmap`/`Canvas`/`readPixels` ya son
  multiplataforma. El "muro" se cruza TRADUCIENDO llamadas (tabla en `PowImagen`).
- ⚠️ **La única excepción es `decodificarReducido`**: el decodificador común no tiene nada como
  `inSampleSize`, y perderlo sería REGRESIÓN DE MEMORIA en Android (atlas croma de 2560×7168).
- ⚠️⚠️ **`iosX64` SE RETIRÓ y debe seguir fuera**: Compose MP 1.11.1 no publica para ese target
  (404). Con él, TODOS los source sets fallan con `Unresolved platforms: [iosX64]`, un error que no
  menciona a Compose. Es solo el simulador de Mac **Intel**.
- ⚠️ **NO se usó el `lifecycle-viewmodel` KMP oficial**: su única versión con iOS (2.11.0) exige
  `compileSdk 37`. `PowViewModel` cuesta 20 líneas y en Android ES un ViewModel de androidx.
- ⚠️ `@Synchronized` y `LruCache` no existen en común → `PowCerrojo` (REENTRANTE: `load` llama a
  `template` sobre el mismo cerrojo) y una LRU a mano. `recycle()` se BORRA, no se sustituye.
- 🔴 **Revertido a propósito:** `SfCombos`/`SfSpecialPhrases`/`SfVoicePhrases` usan `org.json`, que
  es reescritura a kotlinx, no cambio de import. **Son el mejor punto de entrada siguiente y NO
  hacen falta ni Mac ni Xcode.**

### ✅ CHECKPOINT DE COMPOSE MP — VERIFICADO EN EL MAC (07-28). El plan es viable.
**El framework ENLAZA de verdad con Compose dentro** (lo que en Windows solo fingía): 251 MB de
archivo estático con **153 682 símbolos de Compose**; ya enlazado, la app pesa **66 MB** (debug, sin
strip). **78 tests en el simulador, 0 fallos** — `PowModosTest` (6) y `PowAssetsTest` (6) corrieron
en iOS por 1ª vez. `iosApp` arranca y sigue pintando el mapa. Enlazar tarda ~3 min y come 4 GB.
- ⚠️ **HALLAZGO: el `deployment target` de 16.0 se queda corto.** El linker avisa de que la ICU que
  trae Compose MP **está compilada para iOS 18.5**. En el simulador (26.5) es solo un warning, pero
  fija el **mínimo real de iOS del juego muy por encima de 16** — decisión de la Fase 6, no mía.

## 4. PENDIENTE — por prioridad

### 🔴 P0 · Antes/durante el release
1. **Probar el multijugador en dispositivo:** que los 2 jugadores **oigan** lo mismo (tag
   `SF-NET`). BT y LAN son lo que hay que validar sí o sí.
2. **Redeploy de `MultiplayerSF/` en Render** para activar el P2P (no bloquea el release).
   Con 2 teléfonos en redes distintas, buscar en logcat `SF-RTC`: `DataChannel → OPEN`.
3. **🍏 FASE 5 — que el modo pelea CORRA en iOS. Guion en `PLAN_SF_EN_iOS.md` §4.**
   Hechos ya: catálogo de modos, Compose MP, assets/audio/gráficos/ViewModel/cerrojo y el port de
   `SfSharedSheets`+`SfFrameCatalog`. Falta: los 3 de `org.json`, que el VM herede de
   `PowViewModel`, bajar la UI a `commonMain` (~14 000 líneas) y meter los 107 MB en el bundle.

### 🟠 P1 · AUDIO (activo) — ver `SF/PROMPT_traspaso_audio_subtitulos.md`
**29 clips demasiado largos** y **5 fuera de −16 ±2 LUFS** → **Gemini 3.6** (con los segundos del
dueño). **Faltan `attack`/`hurt`** en 6 peleadores → el dueño graba. ⚠️ **No repitas** el resumen
que dice "100 % normalizados y sin faltantes": está medido y es **falso** (de los 80 `.ogg` solo
**69 son voz**; 2 no se normalizan sin comprimir).

### 🟢 P2 · Animaciones congeladas (el arte se repite, **no es bug de código**)
`stun-1==stun-2==stun-3` en los 18; `bonus-7/8/9/10` estáticos en `lapresidenta`; `run-4==run-5` en
4; `forwards-3==forwards-4` en 3; `throw-2==throw-3` en 3; `super-4==super-5` en 2.

### 🔵 P2b · Motor compartido — sigue la fase 2c
`SfEngine` como esqueleto y los modos como estrategia. Plan: `SF/PLAN_refactor_motor_compartido.md`.

### ⚪ P3 · Bloqueado en el dueño / deuda conocida
- **Mapas UAM Azcapotzalco y Cuajimalpa:** faltan vídeos nuevos → `tools/build_map_backgrounds.py`.
- **Arte V2 de La Presidenta + metamorfosis nuevas:** croma en `tools\_para_corregir\` sin importar.
- **detekt NO está a 0:** 5 smells preexistentes (`CachingWebViewClient`, `NpcAiManager`,
  `RoadRouter`, `CatSpriteManager` ×2). Varios docs dicen "0 smells" y es **falso**.
- `07_OTHER_FEATURES.md` (87 KB) mezcla menú/ajustes con SF; su parte de SF debería migrar a `SF/`.
- **Paparazzi 5** con audio de Paparazzi 1; **Señor de la tienda** con un tramo de voz de Prankedy;
  **La Tzitzimime** mal recortada. Los 3 requieren al dueño.

## 5. Verificación antes de cerrar CUALQUIER sesión

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :shared:testAndroidHostTest
```
En Windows `.\gradlew.bat`. En el Mac, antes: `export JAVA_HOME="/Applications/Android
Studio.app/Contents/jbr/Contents/Home"`. ⚠️ La tarea de `:shared` es **`testAndroidHostTest`**
(cambió con AGP 9). ⚠️ **Son 190: 112 `:app` + 78 `:shared`**; los "184"/"178"/"131"/"47" de docs
viejos son **stale**. ⚠️ Si tocas `commonTest`, pasa **`bash tools/check_kmp_test_names.sh`**.

**detekt — EXACTAMENTE la invocación de CI** (desde la raíz del repo):
```bash
./detekt-cli-1.23.8/bin/detekt-cli --config PolitecnicoOpenWorld/config/detekt/detekt.yml --build-upon-default-config --input PolitecnicoOpenWorld/app/src/main/java,PolitecnicoOpenWorld/shared/src/commonMain/kotlin --baseline PolitecnicoOpenWorld/config/detekt/baseline.xml
```
Debe salir **exit 0**. ⚠️ Un doc viejo decía "NO uses `--build-upon-default-config`": era de antes
del baseline. **CI SÍ lo usa.** ⚠️ El input incluye `:shared`, o no se analiza el módulo compartido.

`git status` debe mostrar **solo** lo que tocaste. Y **actualiza este archivo** antes de terminar.
