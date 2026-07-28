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

**Última actualización:** 2026-07-28 · Opus 5 (Windows) · rama `fase0-auditoria-kmp` · *purgar §3bis el 07-30*

> ➡️ **AHORA:** 1.0.0.14 en revisión en Play. KMP: **Fases 0-4 completas; la 5 EN MARCHA (216
> tests).** 🍏 iOS = solo modo pelea (§3octies) · Compose MP con su **checkpoint verificado en el
> Mac: el framework enlaza** · gráficos, assets y **audio** ya multiplataforma (§3nonies).
> **Siguiente: `PLAN_SF_EN_iOS.md` §4 paso 4 — bajar la UI a `commonMain`, que es el bulto.**

## 🖥️ Rutas por PC

| PC | Raíz del PROYECTO (aquí están `gradlew` y `tools/`) |
|---|---|
| **Laptop** (referencia) | `C:\Users\gabri\AndroidStudioProjects\PolitecnicoOpenWorld\PolitecnicoOpenWorld` |
| **Escritorio** | ⚠️ **DESCONOCIDA — la 1ª IA que trabaje ahí debe ESCRIBIRLA AQUÍ** |
| **🍏 Mac** (iOS) | `/Users/gabrielhuav/Documents/GitHub/PolitecnicoOpenWorld/PolitecnicoOpenWorld` |

⚠️ **La carpeta es doble**: el proyecto Gradle está DENTRO del repo, en `PolitecnicoOpenWorld/`; las
rutas de los docs son **relativas a la raíz**. El GEN de sprites vive FUERA, en `..\newSFAssets\GEN_*`.
🆕 **PC nueva → `SETUP_PC_NUEVA.md`.** ⚠️ `gradle-wrapper.jar` NO viaja por git y sin él `gradlew` ni
arranca; `secrets.properties` tampoco (vacío vale). detekt-cli SÍ está versionado.

## 1. Organización y delegación

Mapa en **`00_INDEX.md`**. No te saltes **`10_ARQUITECTURA_SEPARACION.md`** (dónde vive cada cosa)
ni **`09_CONVENTIONS_GOTCHAS.md`**; y **`PLAYSTORE_formulario_seguridad_datos.md`** antes de tocar
la ficha. `SF/` = peleas · `MUNDO/` = mundo libre · `_ARCHIVO/` = histórico, NO tareas.
**Delegar:** alta dificultad → **Sol 5.6 / Fable 5** · media → **Opus 4.8/5** · baja → **Gemini 3.6**.
Pásales rutas absolutas, comando exacto, cómo se verifica, qué NO tocar y el `10_...` para orientarse.

## 3bis. Fases 0-4 COMPLETAS (detalle en `PLAN_MIGRACION_KMP.md` y `git log`)

`:shared`; `GeoPoint` propio (osmdroid: 51 → 7 archivos); Gson fuera con `PowJson`; Fase 4 =
`multiplatform-settings` + Room 2.8.4 KMP + Ktor. **Trampas VIVAS:**
- ⚠️ **NO cambies la ruta de la BD** (`filesDir/databases/pow_roads.db`) ni el driver de Android
  (el bundled es solo para iOS): se perdería caché y landmarks del Diseñador.
- ⚠️ **`PowJson` imita a Gson a propósito**; tocarlo rompe saves y clientes viejos EN SILENCIO.
  **`SfArcadeRepository`** es el ÚNICO con migración real de datos (`putStringSet` → JSON `_V2`).
- ⚠️ **`GeoPoint` = port LITERAL de osmdroid**: `x*x` ≠ `.pow(2)`. **Ktor sin `HttpTimeout`** y ping
  25s/20s: evitan que se caiga la partida. **`add("kspAndroid"…)` va DESPUÉS de `kotlin { }`**.
- 🔴 **Dos bugs que el compilador NO ve:** `PowJson.encodeToString(x)` COMPILA y **PETA EN RUNTIME**
  si `x` no es serializable → con mapas usa `jsonOf`/`jsonArrayOf`; y **kotlinx PETA si falta un
  campo sin default**. Los fijan `PayloadsSeSerializanEnRuntimeTest`/`ModelosToleranJsonIncompleto`.

## 3ter. iOS arrancó (Mac) — el mapa Leaflet corre en el simulador

`WorldMapLeafletHtml.kt` en `:shared`, que produce el framework `Shared`; la app iOS está en
`iosApp/` (SwiftUI + `WKWebView`).
- ⚠️ **El runtime del simulador lo instala XCODE** (no un DMG a mano: queda en cuarentena y los
  tests mueren con `Abort trap` 134). **Genera el framework ANTES de abrir Xcode** (en el Mac) o
  sale `No such module 'Shared'`. Y `gradle-wrapper.jar` no viaja por git → `SETUP_PC_NUEVA.md`.
- 🔴 **Deuda:** los assets aún no se empaquetan en el bundle → el handler de iOS devuelve 404.

## 3quater-septies. Kotlin 2.3.21 · AGP 9 · refactor · motor compartido (cerrados)

Detalle en `10_ARQUITECTURA_SEPARACION.md` y `git log`. **Trampas que siguen VIVAS:**
- ⚠️⚠️ **NO SE PUEDE SUBIR A KOTLIN 2.4: KSP NO EXISTE para 2.4** (medido: 0 versiones) y aquí es
  obligatorio (Hilt + Room). **2.3.21 es el techo.** No pierdas una sesión intentándolo.
- ⚠️ **AGP 9 obliga a `android.builtInKotlin=true`** (`:app` ya NO aplica `kotlin-android`;
  `kotlinOptions` → `compilerOptions`) y a `com.android.kotlin.multiplatform.library` en `:shared`.
  Y **los tests de `:shared` son `testAndroidHostTest`**, no `testDebugUnitTest`.
- ⚠️ **Patrón PARCIAL:** los CAMPOS se quedan en la clase; **NUNCA recrees en la clase una función
  de un parcial** (gana la clase EN SILENCIO). Trampas del extractor en `10` §8.
- ⚠️ **DOS constantes inventadas que habrían cambiado el juego EN SILENCIO**: `DRAIN_PER_SEC` es
  **200f** y el tope de cámara lleva **`+ STAGE_PADDING`**. Ahora las fijan tests.
- 🆕 **GUARDA mecánica** `tools/check_kmp_test_names.sh` en el `pr-quality-gate`: los nombres de
  test con `(` `)` `,` compilan en la JVM y rompen Native. **Documentarlo NO bastó: pasó 3 veces.**

## 3octies. 🍏 iOS = SOLO el modo pelea (decisión del dueño, 07-27)

En iOS el menú muestra **únicamente AJUSTES, COLECCIONABLES y HUELUM VS. GOYA**; Mundo Libre, Modo
Historia y Multijugador se **esconden**. **PROBADO: el menú de Android sigue idéntico.**
- El catálogo vive en **`PowModos.kt`** (`:shared`). **Lo único `expect/actual` es
  `plataformaActual`**; qué modos hay es lógica PURA → **la regla de iOS se testea desde Android**.
  `PowModosTest` se pone rojo si alguien añade un modo a iOS: obliga a confirmar que FUNCIONA en el
  simulador, no solo que el botón aparece.
- ⚠️ **Esconder el botón NO porta el modo.** Que el menú de iOS liste "Huelum vs. Goya" no lo hace
  jugable allí todavía: falta la UI (~14 000 líneas) y los 107 MB de assets.
- 🆕 Desglose y orden de ataque en **`PLAN_SF_EN_iOS.md`**.

## 3nonies. 🍏🥊 Fase 5 EN MARCHA — Compose MP y el muro de `android.graphics`, cruzado

**⚡ Kotlin/Native SÍ compila los klibs de iOS DESDE WINDOWS.** `compileKotlinIosSimulatorArm64` y
`compileTestKotlin...` pasan aquí, con los cinterops de Apple resueltos. **El type-check de iOS ya
no exige Mac.** ⚠️ Pero **`linkDebugFrameworkIosSimulatorArm64` MIENTE**: fuera de un Mac dice BUILD
SUCCESSFUL y **no crea nada**. NO lo uses como prueba.

**MEDIDO: `:app` 112 + `:shared` 104 = 216 tests, 0 fallos; detekt exit 0.** **PROBADO EN EL
EMULADOR** dos veces: los sprites (pelea con peleadores de set COMPARTIDO, cuyas hojas arma en
runtime el código portado) y el AUDIO (§3decies). Nuevo en `:shared`: `PowImagen`, `PowAudio`,
`PowAssets`, `PowViewModel`, `PowCerrojo`, `SfVocesReglas`, `SfSharedSheets`, `SfFrameCatalog`.
**`android.graphics` y `android.media` han DESAPARECIDO de `features/streetfighter`** (medido: 0).
- 🎁 **Los gráficos NO necesitaron `expect/actual`**: `ImageBitmap`/`Canvas`/`readPixels` ya son
  multiplataforma; el "muro" se cruza TRADUCIENDO llamadas (tabla en `PowImagen`).
- ⚠️ **Única excepción, `decodificarReducido`**: el decodificador común no tiene `inSampleSize`, y
  perderlo sería REGRESIÓN DE MEMORIA en Android (atlas croma de 2560×7168).
- ⚠️⚠️ **`iosX64` SE RETIRÓ y debe seguir fuera**: Compose MP 1.11.1 no publica para ese target
  (404). Con él, TODOS los source sets fallan con `Unresolved platforms: [iosX64]`, un error que no
  menciona a Compose. Es solo el simulador de Mac **Intel**.
- ⚠️ **NO se usó el `lifecycle-viewmodel` KMP oficial**: su única versión con iOS (2.11.0) exige
  `compileSdk 37`. `PowViewModel` cuesta 20 líneas y en Android ES un ViewModel de androidx.
- ⚠️ `@Synchronized` y `LruCache` no existen en común → `PowCerrojo` (REENTRANTE: `load` llama a
  `template` sobre el mismo cerrojo) y LRU a mano. `recycle()` se BORRA, no se sustituye.

### ✅ CHECKPOINT DE COMPOSE MP — VERIFICADO EN EL MAC (07-28). El plan es viable.
**El framework ENLAZA de verdad con Compose dentro** (lo que en Windows solo fingía): 251 MB de
archivo estático con **153 682 símbolos de Compose**; ya enlazado, la app pesa **66 MB** (debug, sin
strip). **78 tests en el simulador, 0 fallos** — `PowModosTest` (6) y `PowAssetsTest` (6) corrieron
en iOS por 1ª vez. `iosApp` arranca y sigue pintando el mapa. Enlazar tarda ~3 min y come 4 GB.
- ⚠️ **HALLAZGO: el `deployment target` de 16.0 se queda corto.** El linker avisa de que la ICU que
  trae Compose MP **está compilada para iOS 18.5**. En el simulador (26.5) es solo un warning, pero
  fija el **mínimo real de iOS del juego muy por encima de 16** — decisión de la Fase 6, no mía.

## 3decies. 🔊 El audio de SF, multiplataforma — y las reglas de voz por fin con red

`android.media` fuera de SF. Lo que faltaba en la API era el **aviso de fin de clip**
(`PowClip.alTerminar`): sin él, el mapa de voces sonando no se vacía nunca y el peleador acaba sin
poder gritar porque su intro, terminada hace rato, sigue apuntada como en curso.
- ⚠️⚠️ **El `delegate` de `AVAudioPlayer` es una referencia DÉBIL.** Si el delegado no se guarda en
  un campo del clip, el recolector se lo lleva y el aviso **no llega nunca**: sin error, sin log.
- 🎁 **Las 4 reglas de interrupción salieron a `SfVocesReglas` (`:shared`) con 14 tests.** Estaban
  afinadas DE OÍDO y sin cobertura; no son audio, son decisiones sobre nombres de archivo.
- ⚠️ El test cazó que el comentario original MIENTE: dice "otro clip de intro sí la reemplaza" y el
  código **no hace eso** (excluye todo `_intro`). Hoy da igual: solo hay UN archivo de intro.
- ⚠️ **`SoundPool.play` abre un flujo NUEVO cada vez.** Sin parar el previo, dos golpes iguales
  seguidos se solapan y suena a eco; `reproducir()` ya reinicia. Y `AVAudioPlayer.duration` viene en
  **SEGUNDOS** (Android da ms): sin el ×1000 los subtítulos de iOS durarían 1 ms.
- 🔴 **`SfArcadeRepository` NO se tocó, a propósito.** El encargo lo daba por "solo cambiar el
  import" y no lo es: también **escribe** JSON (necesita `jsonOf`, no `PowJsonLectura`) y es el
  único con migración real de datos. Hay saves viejos con la cadena literal `"null"` en `mapFile`
  — lo delata el `it != "null"` al leer. Eso se toca con calma, no al final de una sesión.

## 4. PENDIENTE — por prioridad

### 🔴 P0 · Antes/durante el release
1. **Probar el multijugador en dispositivo:** que los 2 jugadores **oigan** lo mismo (tag
   `SF-NET`). BT y LAN son lo que hay que validar sí o sí.
2. **Redeploy de `MultiplayerSF/` en Render** para activar el P2P (no bloquea el release).
   Con 2 teléfonos en redes distintas, buscar en logcat `SF-RTC`: `DataChannel → OPEN`.
3. **🍏 FASE 5 — que el modo pelea CORRA en iOS. Guion en `PLAN_SF_EN_iOS.md` §4.**
   Hechos: catálogo de modos, Compose MP (con el framework enlazando), assets, gráficos, audio,
   ViewModel, cerrojo, `org.json` fuera y el port de `SfSharedSheets`/`SfFrameCatalog`.
   **Falta el bulto: bajar la UI a `commonMain` (~14 000 líneas) y meter los 107 MB en el bundle.**
   ⚠️ **Eso se hace EN EL MAC**: cada pantalla portada hay que VERLA en el simulador; en Windows
   solo se type-checkea.
4. **🖥️ Lo ÚNICO que le queda a Windows: `SfArcadeRepository`** (§3decies). Necesita emulador
   Android para probar el ida y vuelta de guardar/cargar, y en el Mac **no hay AVD**. Se puede hacer
   EN PARALELO con el Mac: son archivos distintos. ⚠️ Misma rama → `git pull` antes de cada push.

### 🟠 P1 · AUDIO (activo) — ver `SF/PROMPT_traspaso_audio_subtitulos.md`
**29 clips demasiado largos** y **5 fuera de −16 ±2 LUFS** → **Gemini 3.6** (con los segundos del
dueño). **Faltan `attack`/`hurt`** en 6 peleadores → el dueño graba. ⚠️ **No repitas** el resumen
que dice "100 % normalizados y sin faltantes": está medido y es **falso** (de los 80 `.ogg` solo
**69 son voz**; 2 no se normalizan sin comprimir).

### 🟢 P2 · Animaciones congeladas (el arte se repite, **no es bug de código**)
`stun-1==stun-2==stun-3` en los 18; `bonus-7/8/9/10` estáticos en `lapresidenta`; `run-4==run-5` en
4; `forwards-3==forwards-4` y `throw-2==throw-3` en 3; `super-4==super-5` en 2.

### 🔵 P2b · Motor compartido — sigue la fase 2c
`SfEngine` como esqueleto y los modos como estrategia. Plan: `SF/PLAN_refactor_motor_compartido.md`.

### ⚪ P3 · Bloqueado en el dueño / deuda conocida
- **Mapas UAM Azcapotzalco y Cuajimalpa:** faltan vídeos → `tools/build_map_backgrounds.py`. Y el
  **arte V2 de La Presidenta + metamorfosis**: croma en `tools\_para_corregir\` sin importar.
- **detekt NO está a 0:** 5 smells preexistentes (`CachingWebViewClient`, `NpcAiManager`,
  `RoadRouter`, `CatSpriteManager` ×2). Varios docs dicen "0 smells" y es **falso**. Y
  `07_OTHER_FEATURES.md` (87 KB) mezcla menú/ajustes con SF; esa parte debería migrar a `SF/`.
- **Paparazzi 5** con audio de Paparazzi 1; **Señor de la tienda** con un tramo de voz de Prankedy;
  **La Tzitzimime** mal recortada. Los 3 requieren al dueño.

## 5. Verificación antes de cerrar CUALQUIER sesión

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :shared:testAndroidHostTest
```
En Windows `.\gradlew.bat`. En el Mac, antes: `export JAVA_HOME="/Applications/Android
Studio.app/Contents/jbr/Contents/Home"`. ⚠️ La tarea de `:shared` es **`testAndroidHostTest`**
(cambió con AGP 9). ⚠️ **Son 216: 112 `:app` + 104 `:shared`**; los "190"/"184"/"178"/"47" de
docs viejos son **stale**. ⚠️ Si tocas `commonTest`, pasa **`bash tools/check_kmp_test_names.sh`**.

**detekt — EXACTAMENTE la invocación de CI** (desde la raíz del repo):
```bash
./detekt-cli-1.23.8/bin/detekt-cli --config PolitecnicoOpenWorld/config/detekt/detekt.yml --build-upon-default-config --input PolitecnicoOpenWorld/app/src/main/java,PolitecnicoOpenWorld/shared/src/commonMain/kotlin --baseline PolitecnicoOpenWorld/config/detekt/baseline.xml
```
Debe salir **exit 0**. ⚠️ Un doc viejo decía "NO uses `--build-upon-default-config`": era de antes
del baseline. **CI SÍ lo usa.** ⚠️ El input incluye `:shared`, o no se analiza el módulo compartido.

`git status` debe mostrar **solo** lo que tocaste. Y **actualiza este archivo** antes de terminar.
