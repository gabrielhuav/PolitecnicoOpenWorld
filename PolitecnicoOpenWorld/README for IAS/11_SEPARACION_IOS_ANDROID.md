# 🧭 iOS y Android: dónde va cada cosa

**Actualizado:** 2026-07-30 · Todos los números de este documento están **medidos**, no estimados.

> **Para quién es:** para ti, que acabas de entrar al equipo y tienes que tocar código que corre en
> dos plataformas sin romper ninguna. No hace falta que sepas Kotlin Multiplatform: hace falta que
> sepas **en qué carpeta escribir**. Eso es lo que explica este documento.

---

## 1. La idea en treinta segundos

El proyecto tiene **dos módulos**:

| Módulo | Qué contiene | Corre en |
|---|---|---|
| **`:shared`** | El juego: lógica, pantallas, datos | Android **y** iOS |
| **`:app`** | Lo que solo existe en Android | Android |

Dentro de `:shared` hay tres carpetas, y **la regla es una sola**:

```
shared/src/
├── commonMain/   ← escribe AQUÍ por defecto. Compila para las dos plataformas.
├── androidMain/  ← solo si necesitas algo que únicamente existe en Android
└── iosMain/      ← su equivalente en iOS
```

**Medido hoy:** 128 archivos en `commonMain` (23 622 líneas), 16 en `androidMain` (2 543),
14 en `iosMain` (**695 líneas en total** — iOS es fino a propósito) y 180 en `:app` (39 905).
**En `commonMain` hay 0 imports de `android.*`** — y así tiene que seguir.

> 📐 **Que `iosMain` sean 695 líneas es el indicador de que esto va bien.** Si empieza a engordar,
> es que alguien está copiando lógica en vez de compartirla. Ahí hay que parar y volver a §2.

> 💡 **La regla práctica:** escribe en `commonMain`. Si no compila porque falta algo de la
> plataforma, entonces —y solo entonces— abre una costura. Nunca al revés.

---

## 2. ¿Cómo sé dónde escribir? (árbol de decisión)

```
¿Lo que voy a escribir usa android.* / Context / Hilt / osmdroid?
│
├── NO  ──────────────────────────────► commonMain.  Fin.
│
└── SÍ
    │
    ├── ¿Es una función SUELTA y pequeña, con equivalente claro en iOS?
    │     (leer un archivo, reproducir un sonido, decodificar una imagen)
    │        └──► COSTURA A: expect/actual.  Ver §3.
    │
    ├── ¿Es un DATO o una ACCIÓN que la pantalla necesita y cada
    │   plataforma consigue a su manera?
    │     (¿es gama baja?, ¿guardar la partida?, ¿vibrar?)
    │        └──► COSTURA B: métodos del Controller.  Ver §4.  ⭐ Prefiere esta.
    │
    └── ¿Es una PANTALLA o feature que en iOS NO existe?
          (multijugador Bluetooth, mundo abierto)
             └──► Se queda en :app y se esconde del menú.  Ver §5.
```

---

## 3. Costura A — `expect` / `actual`

Sirve para funciones sueltas cuyo equivalente en iOS es evidente. Se declara **qué** hace falta en
`commonMain` y se implementa **cómo** en cada plataforma.

```kotlin
// commonMain/…/PowAssets.kt        ← el CONTRATO
expect fun fuentePorDefecto(): PowAssetsFuente

// androidMain/…/PowAssets.android.kt
actual fun fuentePorDefecto(): PowAssetsFuente = …   // AssetManager

// iosMain/…/PowAssets.ios.kt
actual fun fuentePorDefecto(): PowAssetsFuente = …   // NSBundle
```

### Las 11 costuras que existen hoy — y punto

Si necesitas algo de plataforma, **mira primero si ya está aquí**. Casi siempre lo está.

| Costura | Para qué | Android | iOS |
|---|---|---|---|
| `PowAssets` | leer `assets/` | `AssetManager` | `NSBundle` |
| `PowAudio` | sonido | `SoundPool` + `MediaPlayer` | `AVAudioPlayer` |
| `decodificarReducido` | decodificar imagen a menor resolución | `inSampleSize` | decodificar y escalar |
| `PowCerrojo` | exclusión mutua | `synchronized` | `NSRecursiveLock` |
| `powLog` | traza de depuración | `android.util.Log` | `println` (consola de Xcode) |
| `PowViewModel` | clase base de ViewModel | `androidx.lifecycle.ViewModel` | clase con su scope |
| `plataformaActual` | en qué plataforma estoy | `ANDROID` | `IOS` |
| `crearPowDatabaseBuilder` | abrir la BD | driver del sistema | driver empaquetado |
| `PowDatabaseConstructor` | lo exige Room en KMP | generado | generado |
| `SfLifecycleEffect` | pausar al minimizar | `Lifecycle` | notificaciones de `UIApplication` |
| `rememberInputFeedback` | vibración / háptica | `Vibrator` | `UIFeedbackGenerator` |

⚠️ **Antes de añadir la número 11, pregúntate si no es más bien una costura B.** Cada `expect`
nuevo es un archivo más que mantener en dos sitios, para siempre.

### Y las que NO son costuras: la familia `Pow*` de concurrencia, tiempo y color

Ninguna de estas es un `expect/actual`: son clases normales de `commonMain` que sustituyen a algo
de la JVM. **Míralas antes de abrir una costura nueva.**

| En vez de | Usa | Ojo con |
|---|---|---|
| `ConcurrentHashMap` | `PowMapaConcurrente` | `.remove()` → `.quitar()`, etc. (abajo) |
| `CopyOnWriteArrayList` | `PowListaConcurrente` | `iterator()` da una COPIA; para leer-y-vaciar, `drenar()` |
| `ConcurrentHashMap.newKeySet()` | `PowConjuntoConcurrente` | — |
| `AtomicReference` | `PowRef` | `get`/`set` igual; sin `compareAndSet` |
| `System.currentTimeMillis()` | `ahoraMs()` | **de época a propósito** (ver 09) |
| `android.graphics.Color.rgb` | `colorArgb` | mismos bits ARGB |

#### El caso que mejor explica el patrón: `PowMapaConcurrente`

`java.util.concurrent.ConcurrentHashMap` no existe en Kotlin/Native. **No lo envuelvas en un
`expect`**: hay una clase normal en `commonMain` que hace el trabajo, `PowMapaConcurrente` (mapa
corriente + `PowCerrojo`), y migrar es cambiar el tipo y cuatro nombres de método:

```
.values → .valores   ·   .keys → .claves   ·   .remove() → .quitar()
.clear() → .limpiar()   ·   .isEmpty() → .estaVacio()   ·   mapa[k] igual
```

⚠️ **Dos diferencias que hay que conocer:** usa **un solo cerrojo** (perfecto para decenas de
entradas, no para millones) y **`valores`/`claves` devuelven una COPIA** — lo cual es *más* seguro,
porque recorrerlas nunca lanza `ConcurrentModificationException`.

---

## 4. Costura B — métodos del Controller ⭐

**Es la que hay que preferir**, y la que usa el modo pelea.

La idea: la pantalla vive en `commonMain` y **no sabe** en qué plataforma corre. Lo que necesita se
lo pide a una **interfaz**; cada plataforma trae su implementación.

```kotlin
// commonMain — el contrato que la pantalla usa
interface StreetFighterController {
    fun isLowEndDevice(): Boolean
    fun onAttackPressed(strength: SfAttackStrength, type: SfAttackType)
    …
}

// commonMain — la pantalla, que solo pregunta
val lowEnd = remember { controller.isLowEndDevice() }

// :app — Android responde usando ActivityManager
override val lowEndDevice: Boolean = context.isSfLowEnd()
```

### Por qué es mejor que `expect/actual`

- **Se puede testear.** En un test le pasas un controlador de mentira; con `expect/actual` tendrías
  que compilar para la plataforma.
- **No obliga a tocar tres archivos** cada vez que añades un dato.
- **La dependencia es explícita**: se ve en la firma quién necesita qué.

### Los controladores que existen — son cuatro

| Controller | Pantalla | Android | iOS |
|---|---|---|---|
| `StreetFighterController` | la pelea | `AndroidStreetFighterController` (Hilt) | `OfflineStreetFighterController` |
| `SettingsController` | ajustes | `SettingsViewModel` | `SettingsViewModel` (el mismo) |
| `CollectiblesController` | coleccionables | `AndroidCollectiblesViewModel` | `CollectiblesViewModel` |
| `MainMenuController` | menú principal | `AndroidMainMenuController` | `IosMainMenuController` |

💡 **Fíjate en que dos de ellos comparten implementación.** Un controller no obliga a escribir dos
clases: obliga a que la pantalla no sepa cuál le tocó.

---

## 4ter. 🍏 Meter una vista NATIVA de iOS dentro de Compose

A veces no hay Composable que valga: un `WKWebView`, un reproductor, un mapa nativo. Para eso está
**`UIKitView`** de `androidx.compose.ui.viewinterop` — no hace falta bajarse a SwiftUI ni tocar el
proyecto Xcode.

```kotlin
UIKitView(
    factory = { WKWebView(frame = CGRectZero.readValue(), configuration = config) },
    modifier = Modifier.fillMaxSize(),
)
```

Lo usa `MapaMundoIos.kt` para el mapa Leaflet del mundo abierto.

⚠️ **Dos trampas, las dos ya pagadas:**
1. **`@OptIn(ExperimentalForeignApi::class)`** en la función, y **`import kotlinx.cinterop.readValue`**.
   Sin eso, `CGRectZero.readValue()` no compila — y el error no dice que falte el import.
2. Lo que dibuje la vista nativa **queda por debajo de los Composables** que pongas después en el
   mismo `Box`. Es lo que permite el cartel y el botón VOLVER encima del mapa.

---

## 4quater. 🔄🍏 Forzar la ORIENTACIÓN en iOS (y por qué media costura es Swift)

Android tiene una sola regla, en `AppNavGraph.kt`: **el juego va en horizontal; solo los menús
(`main_menu`, `story_mode`, `settings`, `collectibles`) permiten vertical**, con el interior de
Metrobús como excepción vertical. Desde el 2026-08-17 iOS hace lo mismo en el mundo abierto.

| Mitad | Dónde | Qué hace |
|---|---|---|
| **DECLARAR** qué orientaciones valen | `iosApp/POW/POWApp.swift` (`PowAppDelegate`) | Responde `application(_:supportedInterfaceOrientationsFor:)` leyendo `OrientacionPow.esHorizontal` |
| **PEDIR** el giro ya | `platform/orientacion/OrientacionIos.kt` | `requestGeometryUpdate` + `setNeedsUpdateOfSupportedInterfaceOrientations()` |
| **Usarlo** desde una pantalla | `ForzarHorizontal()` en `MapaMundoIos` | `DisposableEffect`: fija al entrar, libera al salir |

⚠️ **Con una sola mitad no funciona:** sin el delegado la pantalla gira y el usuario puede volver a
girarla; sin la parte de Kotlin no gira hasta que el usuario mueva el teléfono.

⚠️ **La mitad de Swift NO es pereza, es obligación** (09 §KMP nº5): `supportedInterfaceOrientations`
vive en una categoría de ObjC y Kotlin/Native la expone como extensión → `overrides nothing`. Y va
en el AppDelegate y no en un `UIViewController` propio porque con SwiftUI el raíz es un
`UIHostingController`, que **no** consulta a sus hijos.

⚠️ **Va en `POWApp.swift`, un archivo que YA existía**: añadir un `.swift` nuevo obliga a tocar el
`project.pbxproj`, que es justo lo que este proyecto evita.

📐 **Lo que esto compra:** el HUD del mundo en iOS usa `ControllerBaseSize` (180 dp) igual que
Android y que el modo pelea. **Los controles son los mismos en las dos plataformas, siempre.**

---

## 4bis. 🍏 Dónde vive la navegación de iOS

Todo el juego en iOS es **un solo `ComposeUIViewController`**, y su interior es un `when`:

```kotlin
// shared/src/iosMain/…/PowAppIos.kt        ← 122 líneas, y ahí cabe la app entera
private enum class Pantalla { MENU, AJUSTES, COLECCIONABLES, PELEA }
```

> **Para añadir una pantalla a iOS se toca ESE archivo, no el proyecto Xcode.**
> Swift solo abre la ventana; ver [`iosApp/README.md`](../iosApp/README.md).

En Android el equivalente es `AppNavGraph.kt` (1167 líneas), que además tiene el mundo abierto, el
Modo Historia y el multijugador. **No son el mismo archivo y no tienen por qué parecerse**: cada
plataforma navega con lo suyo, y lo que se comparte son las pantallas.

---

## 5. Lo que NO existe en iOS

En iOS el juego arranca en el **menú principal real**, igual que en Android. Desde el 07-30 ese
menú **pinta los mismos seis botones** que Android, pero solo tres se pueden jugar: **Ajustes**,
**Coleccionables** y **Huelum vs. Goya**.

La lista **no se decide en la UI**: vive en `PowModos.kt` (`:shared`), y hay **tres preguntas**
que no significan lo mismo:

```kotlin
PowModo.MUNDO_LIBRE.disponible()   // ¿se puede JUGAR aquí?      → en iOS: false
PowModo.MUNDO_LIBRE.enObras()      // ¿se pinta pero no se juega? → en iOS: true
PowModo.MUNDO_LIBRE.sePinta()      // ¿aparece el botón?          → en iOS: true
```

| Pregunta | Úsala para | ⚠️ NO la uses para |
|---|---|---|
| `disponible()` | navegar, guardar, gatear secciones de Ajustes | decidir si pintas un botón |
| `sePinta()` | **solo** pintar el botón en el menú | dar por hecho que el modo funciona |

> ### 🚧 El interruptor de la App Store
>
> ```kotlin
> const val MODOS_EN_OBRAS_VISIBLES = true   // PowModos.kt
> ```
>
> **Ponlo en `false` antes de firmar para iOS.** Apple rechaza funciones anunciadas que no
> funcionan (Guideline 2.1), y un botón que abre un cartel de "en obras" es exactamente eso.
> Es lo ÚNICO que hay que tocar: los tres botones desaparecen y el menú queda como estaba.
> En Android no cambia nada — allí los tres modos están de verdad.

⚠️ **Añadir un modo a `modosDe(IOS)` NO lo porta.** Solo deja de esconder el botón. `PowModosTest`
se pone rojo a propósito cuando alguien cambia esa lista: es para obligar a confirmar que el modo
**funciona en el simulador** antes de mostrarlo.

📘 El plan completo del mundo abierto —fases, bloqueadores medidos y el problema de los assets—
está en **[`12_PLAN_MUNDO_ABIERTO_iOS.md`](12_PLAN_MUNDO_ABIERTO_iOS.md)**.

### Qué se queda en `:app` y por qué — SF completo

El modo pelea son **12 411 líneas en `:shared`** y **1 037 en `:app`** (92 % compartido). Lo que
queda en `:app` está ahí por un motivo concreto, no por falta de tiempo:

| Archivo | Líneas | Por qué no puede bajar |
|---|---:|---|
| `SfOnlineOverlays.kt` | 736 | Multijugador Bluetooth. **RFCOMM no existe en iOS.** |
| `AndroidStreetFighterController.kt` | 136 | Implementación Android del contrato de §4 |
| `AndroidStreetFighterViewModel.kt` | 104 | Hilt (`@HiltViewModel`) es solo de Android |
| `SfDeviceTier.kt` | 31 | `ActivityManager.isLowRamDevice` |
| `StreetFighterScreenAndroid.kt` | 30 | Punto de entrada que arma lo anterior |

### Diferencias de PRODUCTO, no de código

Hay dos cosas que iOS hace distinto **porque la App Store lo exige**, no porque falte trabajo. Las
decide `IosMainMenuController`, y en Android se quedan como están:

| | Android | iOS | Por qué |
|---|---|---|---|
| Insignia PREALPHA / BETA | se muestra | **no** | La App Store rechaza apps que se anuncian como beta fuera de TestFlight. |
| Número de versión en el menú | se muestra | **no** | Va con lo anterior. |

⚠️ **No "arregles" esto añadiéndolo a iOS.** Está puesto a mano y a propósito.

---

## 6. Rendimiento en gama baja

El juego tiene que correr en teléfonos de ≤2 GB. Hay dos mecanismos y conviene no confundirlos.

### 6.1 El interruptor `lowEnd`

Android lo calcula (`isSfLowEnd()`); llega a las pantallas por el controlador (§4) y baja como
parámetro. Con él se reduce la resolución de los fondos, se deja de animar el roster y se saltan
medidas caras.

### 6.2 Nunca decodifiques imágenes durante la composición

⚠️ **Este es el error que más caro sale, y ya se cometió dos veces.**

```kotlin
// ❌ MAL: decodifica en el hilo de UI, y LazyGrid lo repite en cada scroll
val bmp = remember(ruta) { PowImagen.deAsset(ruta) }

// ✅ BIEN: decodifica en segundo plano y queda cacheado
val bmp = rememberImagenDeAsset(ruta, reduccion = 2)
```

Y **pide solo los píxeles que vas a pintar**: los coleccionables son de ~600×420 y se dibujan a
64 dp — a tamaño completo son ~1 MB cada uno (6,7 MB los siete); con `reduccion = 2`, 1,7 MB.

### 6.3 Toda caché lleva tope

`PowImagenCache` (8-12 entradas) y `SfPreviewCache` (8) desalojan por uso, como el
`nativeDrawableCache` del mapa. **Nunca las cambies por un mapa sin límite:** las claves llevan
parámetros, así que crecen hasta el OOM.

Para soltarlas hay **un solo sitio**:

```kotlin
PowCaches.liberarTodo()      // lo llama MainActivity.onTrimMemory
```

⚠️ **Si añades una caché en `:shared`, súmala a `PowCaches.liberarTodo()` y no toques nada más.**
Hacerla pública y llamarla desde `MainActivity` funciona hoy, y falla el día que alguien añada la
siguiente y se olvide.

---

## 7. Cómo compruebo que no rompí nada

### En Windows / Linux

```bash
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :shared:testAndroidHostTest
```
Hoy: **319 tests** (125 `:app` + 194 `:shared`), 0 fallos. Medido el **2026-08-16 en Windows**
(leído de los XML de `test-results`, no del log); los 194 de `:shared` son los MISMOS en
`testAndroidHostTest` y en `iosSimulatorArm64Test`.

```bash
.\gradlew.bat :shared:compileKotlinIosSimulatorArm64
```
Esto **sí funciona en Windows** y comprueba el código de iOS entero, cinterops de Apple incluidos.

✅ **RE-MEDIDO el 2026-08-16 en Windows, porque `PLAN_MIGRACION_KMP.md` §12 decía lo contrario**
(que Gradle desactiva los targets de Apple aquí). **Manda esta sección; aquel punto ya está
corregido.** La prueba de que compila de verdad y no se salta el target: `BUILD SUCCESSFUL` **sin**
el aviso `targets cannot be built on this machine`, con `kspKotlinIosSimulatorArm64` ejecutado,
warnings de archivos de **`iosMain`** y **217 archivos / 7,7 MB de klib** en
`shared/build/classes/kotlin/iosSimulatorArm64/`.

⚠️ **`linkDebugFrameworkIosSimulatorArm64` NO sirve de prueba fuera de un Mac**: dice
`BUILD SUCCESSFUL` y no produce ningún archivo. **Compilar iOS: sí desde Windows. Enlazarlo y
abrirlo: solo en el Mac.**

```bash
bash tools/check_kmp_test_names.sh
```
Los nombres de test con `(`, `)` o `,` compilan en la JVM y **rompen iOS**. Ha pasado tres veces.

### En el Mac

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 :shared:iosSimulatorArm64Test
```

⚠️ **`JAVA_HOME` no es opcional:** el Mac no trae JDK propio y `./gradlew` falla con
*"Unable to locate a Java Runtime"*. El JDK que hay es el JBR de Android Studio.

Después, en Xcode: ⌘R. **Repite el `link…` cada vez que cambies Kotlin.**

### Y luego ábrelo

⚠️ **Si tocaste cómo se pinta, se carga o suena algo: ábrelo en el emulador Y en el simulador.**
Los tests no ven un sprite mal anclado, una imagen que no aparece ni un botón bajo la barra de
estado. **Los SEIS defectos de §8bis pasaron todos los tests sin despeinarse.**

---

## 8. Las cinco trampas que más caro han salido

1. **`@Insert(REPLACE)` sobre datos del jugador borra su progreso.** Pasó con los coleccionables.
   Si solo quieres corregir una columna, actualiza esa columna.
2. **En Kotlin los comentarios de bloque se anidan.** Una ruta con comodín dentro de un KDoc abre
   un comentario que nunca cierra: `Unclosed comment`.
3. **`PowJson.encodeToString(unMapa)` compila y PETA EN RUNTIME.** Con mapas usa `jsonOf` /
   `jsonArrayOf`.
4. **`Dispatchers.IO` no existe en Kotlin/Native.** Usa `Dispatchers.Default`.
5. **`@Synchronized` y `LruCache` son solo de la JVM.** Usa `PowCerrojo` y una LRU a mano.

---

## 8bis. 🍏 Las seis que solo se ven abriendo el simulador

Salieron verificando iOS el 2026-07-30 y 07-31. **Ninguna la caza un test**, y varias afectaban
también a Android sin que nadie lo hubiera notado.

1. **Compose Resources NO tiene API para forzar el idioma.** Se comprobó leyendo la klib: solo hay
   símbolos internos. Su entorno por defecto lee `Locale.current`, que en iOS sale de `NSLocale`.
   El equivalente del `activity.recreate()` de Android es escribir la clave estándar
   **`AppleLanguages`** en `NSUserDefaults` (`IdiomaIos.kt`) y **rehacer el árbol de Compose** con
   `key(generacion)`.
   ⚠️ Se guardan **dos** claves y no es redundante: `APP_LANGUAGE` es la del juego (la que pinta el
   desplegable y comparte con Android), `AppleLanguages` es la que mira el sistema.
2. **Compose Resources NO des-escapa `\'`.** En Android eso lo hacía `aapt`, y estos recursos no
   pasan por él: en pantalla se leía `fighter\'s`. En `composeResources` **el apóstrofe va suelto**.
3. **Si una imagen sale gris, mira primero si está en el bundle.** No todo `assets/` viaja a iOS
   —lo elige el `rsync` del proyecto Xcode— y `PowAssets` no distingue "no existe" de "no se copió".
4. **Un atlas de combate NUNCA se pinta con `Image(atlas)`.** Es una rejilla de hasta 2560×7680;
   encogida a una miniatura da un cuadro de puntos. Hay que recortar la celda (`FighterPortrait`).
5. **`Modifier.scale()` NO encoge un control táctil: encoge el DIBUJO.** El área táctil se queda
   donde estaba, así que el botón deja de responder donde se ve. Pasó al meter el HUD del mundo en
   vertical en iOS. **Un control que deba cambiar de tamaño acepta un parámetro `tamano: Dp`** y
   calcula sus partes en proporción — así se puede encoger sin cambiar el tacto.
6. **`systemBarsPadding()` va en el WIDGET, no en la pantalla.** La pelea se dibuja a sangre a
   propósito; meter el inset arriba la encogería. Sin él, la ✕ de salir y el contador de FPS se
   colaban bajo la barra de estado en iOS.
   🆕 **MEDIDO en el Mac (2026-08-14):** en iOS ese modificador **sí** aporta el inset del indicador
   de inicio — la etiqueta de versión del menú cae a **54 pt del borde** en un iPhone 17 Pro (34 pt
   de área segura + los 16 dp de `padding`), y el degradado sigue llegando al borde. ⚠️ Pero en el
   MENÚ eso hoy **no se ve**: las dos esquinas de abajo están vacías en iOS (`versionName` es `null`
   por §5 y nadie pasa `chipDeCuenta`). El inset de las esquinas es para Android; en iOS solo
   importará el día que se pinte algo ahí.

---

## 9. Deuda conocida (no la descubras otra vez)

- **La UI compartida de SF tiene ~12 literales en español** sin pasar por `composeResources`: con la
  app en inglés, el diálogo "Continuar pelea" sale en español.
- **`SfOnlineOverlays.kt` (736 líneas) no tiene equivalente en iOS** y no lo tendrá: allí el modo
  pelea es sin multijugador.
- **El bundle de iOS va por 196 MB** solo con SF, contra el límite de **200 MB por datos móviles**
  de Apple. Cualquier asset nuevo que se sume al `rsync` hay que pesarlo antes.
