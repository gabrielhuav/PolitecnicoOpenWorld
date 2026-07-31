# 🧠 MEMORIA COMPARTIDA ENTRE IAs — estado vivo

> Único traspaso entre IAs. Ventana de 2 días; máximo 200 líneas. Aquí va estado medido,
> trabajo abierto y trampas caras. Diseño e historia viven en los documentos de cada área.

**Última actualización:** 2026-07-30 · Opus 5 (Mac) · rama `perf-gama-baja-coleccionables`

> ➡️ **AHORA:** **SF corre entero en iOS** (menú, Ajustes, Coleccionables, pelea, idioma, modo
> desarrollador, guardado) y el **mundo abierto va por la fase 2 de 8** (§2ter, plan en el doc 12).
> **Siguiente:** verificar Android en Windows (§5) y **decidir On-Demand Resources** — con el mundo
> el bundle iOS pasaría de 196 a 399 MB contra el límite de 200 MB de Apple. **234 tests.**

## 🖥️ Rutas por PC

| PC | Raíz del proyecto (`gradlew` y `tools/`) |
|---|---|
| Laptop | `C:\Users\gabri\AndroidStudioProjects\PolitecnicoOpenWorld\PolitecnicoOpenWorld` |
| Escritorio (MEDIDO 07-29) | `C:\Users\gabri\Documents\GitHub Desktop\PolitecnicoOpenWorld\PolitecnicoOpenWorld` |
| Mac | `/Users/gabrielhuav/Documents/GitHub/PolitecnicoOpenWorld/PolitecnicoOpenWorld` |

La carpeta es doble. Los docs usan rutas relativas a esta raíz. El GEN de sprites vive fuera,
en `..\newSFAssets\GEN_*`. En Windows la ruta del escritorio lleva espacio: entrecomillarla.
PC nueva: `SETUP_PC_NUEVA.md`; `gradle-wrapper.jar` y `secrets.properties` no viajan por Git.

## 1. Reglas vivas

- Leer `00_INDEX.md`, `09_CONVENTIONS_GOTCHAS.md` y `10_ARQUITECTURA_SEPARACION.md`.
  **Si el cambio toca las DOS plataformas, `11_SEPARACION_IOS_ANDROID.md` es obligatorio** —
  ahí están el árbol de decisión, las 10 costuras y las trampas que solo se ven en el simulador.
- `PowJson` imita Gson a propósito. `encodeToString(Map)` compila y falla en runtime: usar
  `jsonOf`/`jsonArrayOf`. No cambiar saves ni la ruta Android de la BD.
- `SfArcadeRepository` conserva la migración `putStringSet` → JSON `_V2`, el `"null"` literal
  de `mapFile` y el borrado de clave al escribir null. Ya no usa `org.json`; tiene tests V1.
- Kotlin queda en **2.3.21**: KSP no existe para 2.4. AGP 9 usa
  `android.builtInKotlin=true`; tests shared = `testAndroidHostTest`.
- `iosX64` queda fuera: Compose MP 1.11.1 no publica ese target.
- Los nombres de tests de `commonTest` no pueden contener `(`, `)` o `,`; ejecutar
  `bash tools/check_kmp_test_names.sh`.
- En parciales, los campos viven en la clase y nunca se repite allí una función del parcial:
  ganaría la clase en silencio. Ver `10_ARQUITECTURA_SEPARACION.md` **§4**.

## 2. iOS — estado medido

- Assets en el bundle: `assets/STREETFIGHTER/` y `assets/SPRITES/COLLECTIBLES/`. **Del resto de
  `SPRITES/` (104 MB) no entra nada**: es del mundo abierto, que en iOS no existe.
- No quitar el `dependsOn("assemble*MainResources")` de `shared/build.gradle.kts`: sin él faltan
  `composeResources` de Main y la app se cierra desde una corrutina.
- No tocar `Info.plist`, fase `rsync`, `ENABLE_USER_SCRIPT_SANDBOXING = NO` ni la ruta del bundle.
- `CADisableMinimumFrameDurationOnPhone` debe seguir en `Info.plist`.
- Pelea real verificada en simulador: selección, ronda completa, audio, pausa, minimizar,
  cerrar/reabrir y reanudar snapshot de `NSUserDefaults`.
- Offline común; BT/LAN/WebRTC siguen Android-only. iOS **JUEGA** Ajustes, Coleccionables y SF, y
  desde el 07-30 **pinta** además los 3 modos del mundo marcados EN OBRAS. `PowModos.kt` manda:
  `disponible()` = se juega · `sePinta()` = aparece el botón. No los confundas.
- **Sin insignias PREALPHA/BETA**: `IosMainMenuController` devuelve `mostrarInsignias = false` y
  `versionName = null` porque la App Store las rechaza. En Android se quedan.

## 2bis. 🍏 07-30 — navegación iOS completa y verificada (Mac)

Los **ocho pasos** de `_ARCHIVO/PROMPT_MAC_navegacion_iOS.md` pasaron en el simulador (iPhone 17 Pro,
iOS 18.2). Lo que se arregló para llegar ahí, con la trampa de cada uno:

- 🔴 **Idioma no cambiaba.** El desplegable decía "English", `APP_LANGUAGE` se guardaba y los textos
  seguían en español. Compose Resources 1.11.1 **no expone ninguna API para forzar el locale**
  (comprobado en la klib: `filterByLocale` y compañía son internos). Su `DefaultComposeEnvironment`
  lee `androidx.compose.ui.text.intl.Locale.current`, que en iOS sale de `NSLocale`. Solución:
  `aplicarIdiomaIos()` escribe la clave estándar **`AppleLanguages`** de `NSUserDefaults`, y
  `PowAppIos` envuelve el árbol en `key(generacion)` para rehacerlo. Cambia **en caliente** y
  persiste tras cerrar. Es el equivalente del `activity.recreate()` de Android.
  ⚠️ Se guardan DOS claves y **no es redundante**: `APP_LANGUAGE` es la del juego (la que pinta el
  desplegable y comparte con Android); `AppleLanguages` es la que mira el sistema.
- 🔴 **Coleccionables sin arte.** La estampa salía como círculo gris aunque el objeto estuviera
  conseguido: la fase `rsync` **solo copiaba `STREETFIGHTER`**. Se añadió un bloque aditivo para
  `SPRITES/COLLECTIBLES` (736 KB). El mismo síntoma que el bug de rutas muertas del 07-30 (ya en `_ARCHIVO/`),
  pero causa distinta — mirar siempre primero si el fichero está en el bundle.
- 🔴 **Miniatura de peleador ilegible.** `CollectibleCard` pintaba el ATLAS ENTERO (2560×7680)
  encogido a 64 dp: un cuadro de puntos. Ahora reutiliza `FighterPortrait`, que recorta la celda 0.
  ⚠️ **Un peleador nunca se pinta con `Image(atlas)` a secas.** Afectaba también a Android.
- 🔴 **`\'` literal en inglés.** Se veía `each fighter\'s`: Compose Resources **no des-escapa `\'`**
  (eso lo hacía el aapt, y estos recursos no pasan por él). Quitadas las 5 barras de `values-en`.
- 🔴 **✕ de salir bajo la barra de estado** al pasar a pantalla completa. `systemBarsPadding()` va
  **solo en ese botón**, no en la pantalla: el combate se dibuja a sangre a propósito.
- 🧹 **Andamio borrado:** `SfEscaparate.kt` y las pestañas de diagnóstico ya no existen.
  `ContentView.swift` es ahora un único `PowAppTab` a pantalla completa. El puente del mapa
  (`WKWebView` + esquema `pow-asset`) se movió a **`MapaWeb.swift`**, que **nadie usa hoy**: se
  conserva porque ya está verificado y el mundo abierto lo necesitará.

### 📚 Documentación puesta al día en la misma sesión

- **`iosApp/README.md` REESCRITO**: describía una app de una pantalla con solo el mapa. Ahora: qué
  entra en el bundle, qué ajustes de Xcode no se tocan y **que para añadir una pantalla a iOS se
  toca `PowAppIos.kt`, no el proyecto Xcode**.
- **`11`**: números remedidos, los 4 controllers, navegación por plataforma, insignias, y **§8bis:
  las 5 trampas que solo se ven abriendo el simulador**. **`01`** ya no dice "juego Android".
  **`07`**: menú, Ajustes y Coleccionables marcados como multiplataforma, con sus trampas.
- **`00_INDEX.md`**: tabla "qué corre en cada plataforma" + **las rutas de los 13 docs de trabajo**,
  que estaban listados sin carpeta y **ninguno estaba en la raíz** (viven en `SF/`, `MUNDO/`).
- **Archivados con cabecera ✅**: `ARRANQUE_MAC_iOS.md`, `PLAN_SF_EN_iOS.md`,
  `PROMPT_MAC_navegacion_iOS.md`. Raíz de `README for IAS`: 6215 → 6082 líneas.

## 2ter. 🌎 07-30 — Mundo abierto a iOS: fases 1 y 2 (Mac)

Plan completo, medido, en **`12_PLAN_MUNDO_ABIERTO_iOS.md`**. Resumen:

- ✅ **Fase 1 — menús idénticos.** iOS pinta ya los 6 botones; MUNDO LIBRE / MODO HISTORIA /
  MULTIJUGADOR salen **EN OBRAS** y avisan en vez de navegar. Verificado en simulador.
  ⚠️ **`MODOS_EN_OBRAS_VISIBLES = false` en `PowModos.kt` antes de firmar para la App Store**
  (Apple rechaza funciones anunciadas que no funcionan). Es lo único que hay que tocar; 7 tests
  nuevos lo fijan, uno comprueba el propio interruptor.
  ⚠️ **`disponible()` NO cambió**: sigue siendo "¿se puede jugar?" y es lo que gatea Ajustes.
  Lo nuevo son `enObras()` y `sePinta()`, y `sePinta()` es **solo para pintar**.
- ✅ **Fase 2 — dominio puro a `commonMain`**: 19 archivos, ~1 100 líneas. El paquete es idéntico
  en los dos módulos, así que **ningún import cambió**. Dos arreglos que son el patrón a repetir:
  `java.lang.Math` → `kotlin.math` (mismos valores) y `loadCalibration(Context)` → `PowAssets`
  (⚠️ tenía una llamada en `ZombieGameScreen.kt`). `java.util.UUID` → `kotlin.uuid.Uuid`.
- 🛑 **Fase 3 (gestores de IA, ~3 200 líneas) NO se hizo, y a propósito.** Cambia concurrencia de
  código de juego vivo (tráfico, policía, peatones), **tiene 0 tests** y en el Mac **no hay AVD**
  para jugarlo. Hacerla donde haya emulador, escribiendo tests ANTES.
- 🔴 **El bloqueador real no es código: los assets.** Bundle iOS hoy 196 MB; con el mundo, **399 MB**
  contra el límite de **200 MB por datos móviles** de Apple. Decidir On-Demand Resources antes de
  seguir portando.

**AAB de Android MEDIDO: 416 MB** (tope CI 500 → quedan 84). El workflow ahora **avisa a 450 MB** y
publica el desglose por carpeta en el resumen del run. Mover código a `:shared` no engorda el AAB
(el dex entero son 6,8 MB); lo que engordaría es **duplicar assets** — se quedan en `app/`.

## 4. PENDIENTE — prioridad

### 🔴 P0

1. **Windows:** confirmar en emulador que Android sigue igual tras los arreglos de §2bis. Tocan
   `commonMain`, así que Android hereda los tres: retrato del peleador en la tarjeta, `\'` en
   inglés y `systemBarsPadding` en la ✕. **Ninguno se ha visto en Android todavía.**
2. **Decisión del dueño — On-Demand Resources sí o no.** Bloquea la fase 4 en adelante del mundo
   abierto en iOS: con el mundo el bundle son **399 MB** contra los **200 MB por datos móviles** de
   Apple. Sin ODR, la app solo se baja por Wi-Fi. Datos en el doc 12 §0.
3. **Fase 3 del mundo (gestores de IA).** Hacerla **en una máquina con emulador**: cambia
   concurrencia de juego vivo, tiene 0 tests y en el Mac no hay AVD. Receta en el doc 12 §2.
4. Probar multijugador en dos dispositivos: ambos deben oír lo mismo (`SF-NET`).
5. Redeploy `MultiplayerSF/` en Render; con redes distintas buscar `SF-RTC: DataChannel → OPEN`.

### 🟠 P1 · audio

`SF/PROMPT_traspaso_audio_subtitulos.md`: 29 clips largos y 5 fuera de −16 ±2 LUFS;
faltan `attack`/`hurt` en 6 peleadores. Los SFX globales no se normalizan como voces.

### 🟢 P2 · motor y arte

- Continuar fase 2c (`SfEngine` + modos como estrategia):
  `SF/PLAN_refactor_motor_compartido.md`.
- Arte diferido de La Presidenta (fatality V2 y metamorfosis): requiere importar fuentes de
  `tools/_para_corregir/`, extender packer y reempacar solo a ella.
- Animaciones congeladas: `stun-1==2==3` en 18; otras repeticiones están medidas y requieren arte.

### ⚪ P3 · dueño/deuda

- Mapas UAM Azcapotzalco/Cuajimalpa: faltan vídeos.
- Paparazzi 5 tiene audio de Paparazzi 1; Señor tienda contiene un tramo de Prankedy;
  Tzitzimime requiere recorte humano. Ver prompt Gemini.
- detekt mantiene 5 smells preexistentes; cualquier doc que diga 0 está desactualizado.

## 5. Verificación al cerrar

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :shared:testAndroidHostTest
```

Windows: `.\gradlew.bat`. Mac: fijar el JBR de Android Studio y añadir
`:shared:iosSimulatorArm64Test :shared:linkDebugFrameworkIosSimulatorArm64`.
Esperado: **228 = 114 app + 114 shared**, 0 fallos (MEDIDO en Mac el 07-30 con
`:app:testDebugUnitTest` + `:shared:iosSimulatorArm64Test`). Si se toca `commonTest`, ejecutar
el guard de nombres.

Detekt CI desde la raíz exterior:

```bash
./detekt-cli-1.23.8/bin/detekt-cli --config PolitecnicoOpenWorld/config/detekt/detekt.yml --build-upon-default-config --input PolitecnicoOpenWorld/app/src/main/java,PolitecnicoOpenWorld/shared/src/commonMain/kotlin --baseline PolitecnicoOpenWorld/config/detekt/baseline.xml
```

Debe dar exit 0. El input incluye `shared`. Antes de push: `git status`, actualizar este archivo
y hacer `git pull` inmediatamente antes del push.
