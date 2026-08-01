# 🧠 MEMORIA COMPARTIDA ENTRE IAs — estado vivo

> Único traspaso entre IAs. Ventana de 2 días; máximo 200 líneas. Aquí va estado medido,
> trabajo abierto y trampas caras. Diseño e historia viven en los documentos de cada área.

**Última actualización:** 2026-08-01 · Sol 5.6 (Windows, escritorio) · rama `perf-gama-baja-coleccionables`

> ➡️ **Release Android 1.0.0.15 listo para PR/merge a `main`.** El merge dispara
> `.github/workflows/android-release.yml` y sube a Play `alpha` (prueba cerrada).
>
> **Android YA está verificado en emulador y arreglado.** Se probó ACTUALIZANDO encima de un build
> de `main` con partida hecha, que es la prueba que vale: **saves, ajustes, idioma, sesión de
> arcade y la BD sobreviven intactos**. Había **3 regresiones jugables** (música que rebobina,
> preview borrosa, el `"?"` al elegir peleador): corregidas y medidas en `016e7406`.
>
> ✅ Corregidos idioma Android 13+ para `:shared`, el `"?"` del selector y la IA de SF: en IA vs IA
> la barra llena fuerza SUPER ART; ahora impacta, daña, vacía la barra y dura ~1.8 s.
> ✅ 142 PNG → WebP: 87.3 MB → 40.8 MB (ahorro 46.5 MB / 53.2 %); AAB local 353.15 MB.
>
> Release preparado: `versionName` **1.0.0.15**, notas ES+EN reescritas, `gh-pages` viva.
> iOS **no bloquea**: el release es de Android. **285 tests = 119 app + 166 shared**, 0 fallos.

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

## 3bis. ✅ Android verificado en emulador (07-31) — MEDIDO, no estimado

Detalle completo y rutas de la máquina: **`PROMPT_SOL_release_hoy_y_webp.md`**.

**Cómo se probó:** build de `main` instalado primero → partida hecha (idioma, Modo Dev, arcade a
medias) → `adb install -r -d` con el de la rama **encima**. Instalar limpio no prueba migración.

| Riesgo | Resultado |
|---|---|
| Prefs (`pow_game_settings`, `APP_LANGUAGE`, `DEVELOPER_MODE`) | ✅ mismo archivo, mismas claves |
| Sesión de arcade `_V2` (`pow_sf_arcade.xml`) | ✅ byte a byte; sale “Continuar pelea” |
| `files/databases/pow_roads.db` | ✅ intacta, no se rehizo |
| Menú (6 botones, insignias, **ningún EN OBRAS**) · Ajustes (6 categorías, Modo Dev) | ✅ |
| Minimizar/volver en pelea | ✅ vuelve en PAUSA, sin crash |

**3 regresiones jugables, arregladas en `016e7406`** (el commit las explica con su medida):
música que **rebobinaba** por el catch-up de `ON_RESUME` de `LifecycleRegistry` + `reproducir()`
que rebobina por contrato · **preview a 1/16 de píxeles** por perder `BitmapRegionDecoder` al
portar (ahora el muestreo depende de la gama: normal 1, baja 4) · el **`"?"`** de ~300 ms porque
`animate` está en la clave de la caché (ahora se rellena con la otra variante).

✅ **Idioma de `:shared` corregido en Android 13+:** `LocaleManager.applicationLocales` +
`android:localeConfig` alimentan a la vez `R.string` y Compose Resources. API 24-32 conserva el
`Context` envuelto anterior. La lógica de SF/ajustes/coleccionables sigue en `commonMain`; iOS
mantiene su costura `AppleLanguages` y reconstrucción del árbol.

**Aceptado por el dueño para release:** la validación jugable se hizo manualmente antes del último
ajuste de SUPER ART; su impacto/daño/tiempo quedó cubierto por pruebas KMP puras y se confirmará
visualmente en la pista cerrada. Siguen como deuda la media hora de mundo y audio/interiores.

**Release preparado:** `versionName` **1.0.0.15**, notas ES+EN, `gh-pages` viva y workflow revisado.
Los 142 PNG restantes se convirtieron y sus referencias se actualizaron: 87,279,165 → 40,806,326
bytes. `bundleRelease` pasó; AAB local **353,150,996 bytes** (353.15 MB), bajo 450/500 MB.
`tools/.local/` quedó ignorado: el codec `cwebp` no se versiona.

## 4. PENDIENTE — prioridad

### 🔴 P0

1. **Publicar 1.0.0.15:** crear PR `perf-gama-baja-coleccionables` → `main` y fusionarlo. Revisar
   `play-compliance`, AAB ~353 MB y `playstore-closed-testing` en track `alpha`.
   ✅ Lo de §2bis ya se vio en Android: los tres arreglos heredados de `commonMain` (retrato en la
   tarjeta, `\'` en inglés y `systemBarsPadding` en la ✕) están bien en el emulador.
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
Esperado Android: **285 = 119 app + 166 shared**, 0 fallos (medido en Windows 2026-08-01).
En Mac conservar además `:shared:iosSimulatorArm64Test`. Si se toca `commonTest`, ejecutar el guard.

Detekt CI desde la raíz exterior:

```bash
./detekt-cli-1.23.8/bin/detekt-cli --config PolitecnicoOpenWorld/config/detekt/detekt.yml --build-upon-default-config --input PolitecnicoOpenWorld/app/src/main/java,PolitecnicoOpenWorld/shared/src/commonMain/kotlin --baseline PolitecnicoOpenWorld/config/detekt/baseline.xml
```

Debe dar exit 0. El input incluye `shared`. Antes de push: `git status`, actualizar este archivo
y hacer `git pull` inmediatamente antes del push.
