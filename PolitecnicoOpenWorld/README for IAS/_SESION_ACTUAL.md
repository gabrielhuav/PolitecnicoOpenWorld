# 🧠 MEMORIA COMPARTIDA ENTRE IAs — estado vivo

> Único traspaso entre IAs. Ventana de 2 días; máximo 200 líneas. Aquí va estado medido,
> trabajo abierto y trampas caras. Diseño e historia viven en los documentos de cada área.

**Última actualización:** 2026-07-30 · Opus 5 (Mac) · rama `perf-gama-baja-coleccionables`

> ➡️ **AHORA LE TOCA A WINDOWS: verificar Android y publicar** →
> **`PROMPT_WINDOWS_verificar_android_y_release.md`** (§3bis).
> Esta rama lleva **80 commits** sobre `main` —la migración KMP entera— y **nadie ha jugado Android
> desde que empezó**: en el Mac no hay AVD.
>
> En iOS: **SF corre entero** y en el mundo abierto **ya se camina por el mapa**, con el HUD de
> Android y las bardas frenando. Faltan NPCs y coleccionables (los alimenta el `WorldMapViewModel`).
> Después de Android, la **fase 4** (42 strings), que **bloquea la 5**. **429 tests.**

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

## 2bis. 🍏 Trampas de iOS que solo se ven en el simulador

Las cinco del 07-30 (idioma, assets fuera del bundle, atlas mal pintado, `\'` sin des-escapar,
`systemBarsPadding`) están explicadas con su causa medida en **`11_SEPARACION_IOS_ANDROID.md` §8bis**.
El detalle de aquella sesión se purgó a `_ARCHIVO/HISTORIAL_sesiones_2026-07-30.md`.

## 2ter. 🌎 Mundo abierto a iOS — fases 1, 2, 3 (parcial), 5 (parcial) y 6

**Plan, medidas y orden de ataque: `12_PLAN_MUNDO_ABIERTO_iOS.md`.** Aquí solo lo que hay que
saber sin abrirlo:

- ✅ **1 · Menús idénticos.** iOS pinta los 6 botones; los 3 del mundo salen **EN OBRAS**.
  ⚠️ **`MODOS_EN_OBRAS_VISIBLES = false` antes de firmar para la App Store.** 7 tests lo fijan.
  ⚠️ `disponible()` NO cambió (= "¿se juega?"). Lo nuevo es `sePinta()`, **solo para pintar**.
- ✅ **2 · Dominio puro a `commonMain`**: 22 archivos. El paquete es idéntico → ningún import cambió.
  Patrón a repetir: `java.lang.Math` → `kotlin.math`, `Context` → `PowAssets`, `UUID` → `kotlin.uuid`.
- 🟡 **3 · Gestores de IA: 3 de 6.** La clave fue **`PowMapaConcurrente`** (mapa + `PowCerrojo`),
  que sustituye a `ConcurrentHashMap` **conservando la semántica**: migrar es cambiar el tipo y 4
  nombres, no rehacer ~50 accesos donde el compilador no avisa si te dejas uno. 14 tests.
  🐞 Al tipar la API salió un **crash latente**: `ConcurrentHashMap.get(null)` lanza NPE y se
  buscaba con `policeCarId` (`String?`). Corregido.
  ⚠️ Falta `NpcAiManager` (988) + 2 parciales: **van juntos** y usan `CopyOnWriteArrayList`.
- 🟡 **5 · Solo el HUD.** `JoystickController` + `ActionButtonsController` de `commonMain`: **los
  MISMOS controles que Android**. A/B/X/Y avisan "pendiente del ViewModel".
  🔴 ⚠️ **`Modifier.scale()` NO encoge un control: los botones dejan de responder.** Transforma el
  dibujo, no el área táctil. Los controles aceptan `tamano` real (iOS: 100 dp) y son proporcionales.
  🔗 **El VM está BLOQUEADO por la fase 4**: `WorldMapState` → `CampaignObjective` →
  `@StringRes Int` → 42 strings sin migrar. Cadena y orden de ataque en el doc 12.
- 🟡 **6 · El mapa se ve, se camina y las bardas frenan.** `UIKitView` mete el `WKWebView` en
  Compose; `PuenteMapaIos` (Kotlin → JS) llama a las mismas funciones que Android.
  ⚠️ `UIKitView` exige `@OptIn(ExperimentalForeignApi)` **y** `import kotlinx.cinterop.readValue`.
  ⚠️ Cada llamada JS va con `if (typeof f === 'function')`: si el HTML aún no cargó, `WKWebView`
  **se traga el ReferenceError sin log** y el mapa se queda quieto sin que nadie sepa por qué.
  ⚠️ Falta la **VUELTA** del puente (JS → Kotlin): haría falta `WKScriptMessageHandler`.

## 3bis. 📦 Traspaso a Windows (07-31)

**`PROMPT_WINDOWS_verificar_android_y_release.md`** — verificar Android y sacar el release.
Lo que más riesgo tiene, por orden:

1. 🔴 **Datos guardados.** Gson→kotlinx, SharedPreferences→multiplatform-settings, Room KMP,
   OkHttp→Ktor. ⚠️ **Hay que probar ACTUALIZANDO sobre una instalación con partida**, no limpia:
   instalar de cero no prueba nada de esto.
2. 🔴 **Los 3 gestores de IA con `PowMapaConcurrente`.** 14 tests, pero la concurrencia real solo
   se ve **jugando media hora** con nivel de búsqueda alto: tirones, policías clavados, patrullas
   que desaparecen.
3. 🟠 SF completo con el **audio nuevo** (82 `.ogg`→`.m4a`, BGM 24→16 bits) e interiores
   (se quitó `onClaimCollectiblePressed` de 5 llamadas).
4. 🟢 El menú de Android debe salir **igual que siempre**: si aparece un botón EN OBRAS, es bug.

Release: subir `versionName` (hoy `1.0.0.14`), notas ES+EN, y el AAB va por **402 MB de los 500**.
El adelgazamiento (PNG→WebP ~60 MB, BGM→Ogg ~23 MB) **necesita Windows**: en el Mac no hay
`ffmpeg` ni `cwebp`. Script listo: `tools/optimizar_assets_produccion.sh --dry-run`.

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
