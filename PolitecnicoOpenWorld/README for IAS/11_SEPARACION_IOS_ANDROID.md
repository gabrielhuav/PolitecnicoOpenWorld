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

**Medido hoy:** 101 archivos en `commonMain`, 16 en `androidMain`, 12 en `iosMain`.
**En `commonMain` hay 0 imports de `android.*`** — y así tiene que seguir.

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

### Las 10 costuras que existen hoy — y punto

Si necesitas algo de plataforma, **mira primero si ya está aquí**. Casi siempre lo está.

| Costura | Para qué | Android | iOS |
|---|---|---|---|
| `PowAssets` | leer `assets/` | `AssetManager` | `NSBundle` |
| `PowAudio` | sonido | `SoundPool` + `MediaPlayer` | `AVAudioPlayer` |
| `decodificarReducido` | decodificar imagen a menor resolución | `inSampleSize` | decodificar y escalar |
| `PowCerrojo` | exclusión mutua | `synchronized` | `NSRecursiveLock` |
| `PowViewModel` | clase base de ViewModel | `androidx.lifecycle.ViewModel` | clase con su scope |
| `plataformaActual` | en qué plataforma estoy | `ANDROID` | `IOS` |
| `crearPowDatabaseBuilder` | abrir la BD | driver del sistema | driver empaquetado |
| `PowDatabaseConstructor` | lo exige Room en KMP | generado | generado |
| `SfLifecycleEffect` | pausar al minimizar | `Lifecycle` | notificaciones de `UIApplication` |
| `rememberInputFeedback` | vibración / háptica | `Vibrator` | `UIFeedbackGenerator` |

⚠️ **Antes de añadir la número 11, pregúntate si no es más bien una costura B.** Cada `expect`
nuevo es un archivo más que mantener en dos sitios, para siempre.

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

### Los controladores que existen

`StreetFighterController` (pelea) · `SettingsController` (ajustes) ·
`CollectiblesController` (coleccionables) · `MainMenuController` (menú).

---

## 5. Lo que NO existe en iOS

En iOS el juego arranca **solo con el modo pelea**. El menú muestra únicamente **Ajustes**,
**Coleccionables** y **Huelum vs. Goya**.

La lista **no se decide en la UI**: vive en `PowModos.kt` (`:shared`), y la pantalla solo pregunta:

```kotlin
if (PowModo.MUNDO_LIBRE.disponible()) { … }
```

⚠️ **Añadir un modo a `modosDe(IOS)` NO lo porta.** Solo deja de esconder el botón. `PowModosTest`
se pone rojo a propósito cuando alguien cambia esa lista: es para obligar a confirmar que el modo
**funciona en el simulador** antes de mostrarlo.

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

```bash
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :shared:testAndroidHostTest
```
Hoy: **228 tests** (114 `:app` + 114 `:shared`), 0 fallos.

```bash
.\gradlew.bat :shared:compileKotlinIosSimulatorArm64
```
Esto **sí funciona en Windows** y comprueba el código de iOS entero, cinterops de Apple incluidos.

⚠️ **`linkDebugFrameworkIosSimulatorArm64` NO sirve de prueba fuera de un Mac**: dice
`BUILD SUCCESSFUL` y no produce ningún archivo.

```bash
bash tools/check_kmp_test_names.sh
```
Los nombres de test con `(`, `)` o `,` compilan en la JVM y **rompen iOS**. Ha pasado tres veces.

⚠️ **Y si tocaste cómo se pinta, se carga o suena algo: ábrelo en el emulador.** Los tests no ven
un sprite mal anclado ni una imagen que no aparece.

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

## 9. Deuda conocida (no la descubras otra vez)

- **La UI compartida de SF tiene ~12 literales en español** sin pasar por `composeResources`: con la
  app en inglés, el diálogo "Continuar pelea" sale en español.
- **`SfOnlineOverlays.kt` (736 líneas) no tiene equivalente en iOS** y no lo tendrá: allí el modo
  pelea es sin multijugador.
