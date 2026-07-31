# 🧠 MEMORIA COMPARTIDA ENTRE IAs — estado vivo

> Único traspaso entre IAs. Ventana de 2 días; máximo 200 líneas. Aquí va estado medido,
> trabajo abierto y trampas caras. Diseño e historia viven en los documentos de cada área.

**Última actualización:** 2026-07-30 · Opus 5 (Mac) · rama `perf-gama-baja-coleccionables`

> ➡️ **AHORA:** **iOS está COMPLETO para lo que se prometió** — menú real, Ajustes,
> Coleccionables y Huelum vs. Goya, con idioma, modo desarrollador y guardado. Los ocho pasos de
> `PROMPT_MAC_navegacion_iOS.md` pasaron en simulador y el andamio de diagnóstico ya no existe.
> **Siguiente paso:** verificar en Windows que Android sigue igual (§6) y decidir firma/App Store.
> Mundo abierto y multijugador siguen fuera de iOS a propósito. 228 tests.

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

- Leer `00_INDEX.md`, `09_CONVENTIONS_GOTCHAS.md` y
  `10_ARQUITECTURA_SEPARACION.md` antes de mover código entre plataformas.
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
  ganaría la clase en silencio. Ver `10_ARQUITECTURA_SEPARACION.md` §8.

## 2. iOS — estado medido

- Assets en el bundle: `assets/STREETFIGHTER/` y `assets/SPRITES/COLLECTIBLES/`. **Del resto de
  `SPRITES/` (104 MB) no entra nada**: es del mundo abierto, que en iOS no existe.
- No quitar el `dependsOn("assemble*MainResources")` de `shared/build.gradle.kts`: sin él faltan
  `composeResources` de Main y la app se cierra desde una corrutina.
- No tocar `Info.plist`, fase `rsync`, `ENABLE_USER_SCRIPT_SANDBOXING = NO` ni la ruta del bundle.
- `CADisableMinimumFrameDurationOnPhone` debe seguir en `Info.plist`.
- Pelea real verificada en simulador: selección, ronda completa, audio, pausa, minimizar,
  cerrar/reabrir y reanudar snapshot de `NSUserDefaults`.
- Offline común; BT/LAN/WebRTC siguen Android-only. iOS solo ofrece Ajustes, Coleccionables y SF;
  `PowModos.kt` es la fuente de verdad.
- **Sin insignias PREALPHA/BETA**: `IosMainMenuController` devuelve `mostrarInsignias = false` y
  `versionName = null` porque la App Store las rechaza. En Android se quedan.

## 2bis. 🍏 07-30 — navegación iOS completa y verificada (Mac)

Los **ocho pasos** de `PROMPT_MAC_navegacion_iOS.md` pasaron en el simulador (iPhone 17 Pro,
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
  `SPRITES/COLLECTIBLES` (736 KB). El mismo síntoma que el bug de rutas muertas de §3undecies,
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

## 3. 07-29 — Ajustes y Coleccionables a `commonMain` (Windows)

**Implementado:**

- `SettingsRepository` común sobre `multiplatform-settings`; fábrica Android conserva
  exactamente `pow_game_settings`, fábrica iOS usa `NSUserDefaults.standardUserDefaults`.
- La clave sigue siendo `DEVELOPER_MODE`; no se añadió lógica de desbloqueo.
- `SettingsViewModel`, estado, categorías, tutorial y UI están en común mediante
  `SettingsController`. Account y recreación Android entran por slot/callback.
- `SettingsSections.kt` (742 líneas) se sustituyó por once ficheros de sección; ninguno supera
  331 líneas. Opciones de mundo se gatean con `PowModo.MUNDO_LIBRE.disponible()`.
- `CollectibleRepository`, `CollectiblesViewModel`, pantalla y diálogo están en común.
  Imágenes usan `PowAssets`/`PowImagen`; Android conserva adaptadores Hilt pequeños.
- Strings ES/EN migrados a `composeResources` sin segunda fuente en `app`.

**Medido en AVD `Nexus`:** las 6 categorías abren con textos completos; ES→EN recrea la Activity
y carga ambos catálogos; `DEVELOPER_MODE` persiste en el mismo XML y gatea los 18 peleadores;
Coleccionables abre ITEMS/FIGHTERS; los porcentajes muestran `100%`, no `100%%`.

## 3undecies. 🐢 Revisión de gama baja del PR 40d532b5 (laptop, 07-30)

Rama **`perf-gama-baja-coleccionables`** (PR abierto). El refactor de Ajustes/Coleccionables está
bien hecho; esto es deuda que arrastraba el código original y que al bajar a `commonMain` heredaría
también iOS. **MEDIDO: `:app` 114 + `:shared` 114 = 228 tests, 0 fallos; detekt exit 0; probado de
punta a punta en el emulador.**
- 🔴 **BUG REAL, visible y silencioso:** el arte de los coleccionables estaba ROTO en partidas
  viejas. El sembrado solo corría con la tabla VACÍA, así que al renombrarse la carpeta
  (`coleccionables/` → `SPRITES/COLLECTIBLES/`, PR #126) esos jugadores quedaron con rutas muertas:
  coleccionable **desbloqueado, con nombre y borde dorado, y círculo gris**. Sin error ni log.
  Reproducido con una BD real y arreglado conservando `isCollected`.
  ⚠️ **NO lo "simplifiques" a `@Insert(REPLACE)`**: eso borra el progreso del jugador. Hay 5 tests.
- 🐢 **Selector de peleadores:** `rememberFighterPreview` armaba la vista previa dentro de
  `remember` (18 peleadores contra una caché de 3 hojas → reconstrucción constante en el hilo de
  UI). Ahora `SfPreviewCache` la construye en `Dispatchers.Default` y cachea (LRU 8). El código de
  construcción se movió TAL CUAL: los cuadros y tiempos tienen que salir idénticos.
  **Probado:** roster recorrido entero ida y vuelta, todas las previews cargan, 0 errores, 0 OOM.
- 🧹 **`PowCaches.liberarTodo()` es el ÚNICO punto** para soltar memoria reciclable de `:shared`.
  Si añades una caché, súmala ahí y no toques `MainActivity`. Razón en su KDoc.
- 📘 **`11_SEPARACION_IOS_ANDROID.md`** (nuevo): dónde va cada cosa, escrito para Jr Devs.
  Árbol de decisión, las 10 costuras, qué se queda en `:app` y por qué, y las 5 trampas caras.
- 🐢 `CollectibleCard` decodificaba en el hilo de composición y LazyGrid lo repetía al hacer scroll.
  MEDIDO: ~600×420 pintados a **64 dp** → ~1 MB cada uno, **6,7 MB los siete**. Ahora
  `PowImagenCache` (LRU acotada, como `nativeDrawableCache`) + `rememberImagenDeAsset`
  (`Dispatchers.Default`), con `reduccion = 2` → ~1,7 MB. Colgada de `onTrimMemory`.
- 🔴 **Deuda anotada:** la UI compartida de SF tiene ~12 literales en español sin pasar por
  `composeResources` (el diálogo "Continuar pelea" sale en español con la app en inglés).
- ⚠️ **Gotcha nuevo:** en Kotlin los comentarios de bloque **se anidan**, así que una ruta con
  comodín dentro de un KDoc abre un comentario que nunca cierra (`Unclosed comment`).

## 4. PENDIENTE — prioridad

### 🔴 P0

1. **Windows:** confirmar en emulador que Android sigue igual tras los arreglos de §2bis. Tocan
   `commonMain`, así que Android hereda los tres: retrato del peleador en la tarjeta, `\'` en
   inglés y `systemBarsPadding` en la ✕. **Ninguno se ha visto en Android todavía.**
2. Probar multijugador en dos dispositivos: ambos deben oír lo mismo (`SF-NET`).
3. Redeploy `MultiplayerSF/` en Render; con redes distintas buscar `SF-RTC: DataChannel → OPEN`.

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
