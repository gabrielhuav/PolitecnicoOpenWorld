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

**Última actualización:** 2026-07-27 · Opus 5 · rama `fase0-auditoria-kmp`
**Ventana viva:** 2026-07-26 → 2026-07-27 · *purgar a `_ARCHIVO/` a partir del 2026-07-28*

> ➡️ **AHORA:** 1.0.0.14 en revisión en Play. KMP: **Fases 0-4 COMPLETAS y verdes.**
> 🍏 **iOS ARRANCA** (49 tests + mapa Leaflet en el simulador, §3ter) y **Kotlin ya está en 2.3.21**
> (§3quater). Siguiente: **Fase 5 propiamente dicha** — Compose MP y la UI a `commonMain`.

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

El mapa completo de `README for IAS/` está en **`00_INDEX.md`**. Lo que no puedes saltarte:
**`09_CONVENTIONS_GOTCHAS.md`** (OBLIGATORIO antes de tocar código) y
**`PLAYSTORE_formulario_seguridad_datos.md`** (ANTES de tocar la ficha o subir versión).
`SF/` = "Huelum vs. Goya" (empieza por `SF/00_SF_INDEX.md`) · `MUNDO/` = mundo libre ·
`_ARCHIVO/` = histórico YA EJECUTADO, referencia y **NO** tareas.

## 2. A quién delegar

| Dificultad | IA | Cuándo |
|---|---|---|
| **Alta** | **Sol 5.6** · **Fable 5** | Refactors grandes, varios módulos, sistemas nuevos, algo que ya falló dos veces, slicer/packer. |
| **Media** | **Opus 4.8 / 5** | Features acotadas, auditorías, `tools/`, bug localizado, un solo módulo. |
| **Baja** | **Gemini 3.6** | Regenerar assets con pipeline existente, recortes de audio con instrucciones exactas, aplicar CSV, tareas repetitivas. |

**Antes de delegar:** rutas absolutas, comando exacto, cómo se verifica, y qué NO tocar.

## 3. Sesión 2026-07-26 — PURGADA (salió en la 1.0.0.14)

Detalle en `_ARCHIVO/HISTORIAL_sesiones_2026-07-26.md`. ⚠️ Lo único VIVO de ahí está en los P0 de
§4 (probar multijugador + redeploy Render).

## 3bis. Sesión 2026-07-27 (Opus 5) — KMP/iOS: Fases 0-4 COMPLETAS + auditoría

**Qué se hizo** (detalle en `PLAN_MIGRACION_KMP.md` y `git log`): `:shared`; `GeoPoint` propio
(osmdroid: de 51 archivos a 7); Gson fuera con `PowJson`; Fase 4 = `multiplatform-settings` +
Room 2.8.4 KMP + Ktor. **DECISIONES:** juego ENTERO, mapa Opción A. **Trampas VIVAS:**
- ⚠️ **NO cambies la ruta de la BD** (`filesDir/databases/pow_roads.db`) ni el driver de Android
  (el bundled es solo para iOS): se perdería caché y landmarks del Diseñador.
- ⚠️ **`PowJson` imita a Gson a propósito**; tocarlo rompe saves y clientes viejos EN SILENCIO.
  **`SfArcadeRepository`** es el ÚNICO con migración real de datos (`putStringSet` → JSON `_V2`).
- ⚠️ **`GeoPoint` = port LITERAL de osmdroid**: `x*x` ≠ `.pow(2)`, no lo "simplifiques".
  **Ktor sin `HttpTimeout`** y ping 25s/20s: evitan que se caiga la partida.
- ⚠️ **`dependencies { add("kspAndroid"…) }` va DESPUÉS de `kotlin { }`** en `shared/build.gradle.kts`.

### 🔴 AUDITORÍA de las fases 1-4 — 12 bugs REALES (corregidos)
Dos clases que **el compilador NO ve**: (1) `PowJson.encodeToString(x)` COMPILA y **PETA EN
RUNTIME** si `x` no es serializable → con mapas usa `jsonOf`/`jsonArrayOf`, **NUNCA**
`encodeToString`; (2) **kotlinx PETA si falta un campo sin default** → **todo campo nuevo lleva
default**. Los fijan `PayloadsSeSerializanEnRuntimeTest` y `ModelosToleranJsonIncompletoTest`.

## 3ter. Sesión 2026-07-27 (Opus 5, EN EL MAC) — 🍏 1ª compilación iOS de la historia

**✅ MEDIDO: `:shared` compila, enlaza y sus 49 tests PASAN en el simulador** (`tests=49 failures=0
errors=0 skipped=0`, contado en `shared/build/test-results/iosSimulatorArm64Test`). Klib real
(`native_targets=ios_simulator_arm64`, `compiler_version=2.2.10`); Room generó su `actual` de
`PowDatabaseConstructor` y los 5 DAO `_Impl` sin tocar nada.
**Android sigue verde: 112 + 49 = 161 tests, 0 fallos, `assembleDebug` OK.**

⚠️ **El runtime del simulador lo instala XCODE, no un DMG a mano** (`xcodebuild -downloadPlatform
iOS`; quedó iOS 26.5). Un DMG bajado con Safari queda en cuarentena y los tests mueren con
`dyld_sim mmap() of segment failed` + `Abort trap` (134), que NO parece un problema de permisos.

**4 arreglos de código, mínimos. El PORQUÉ de cada uno en `09_CONVENTIONS_GOTCHAS.md` §🍏 KMP/iOS:**
`MapTileDao` → `suspend` (Room lo exige fuera de Android) con el `runBlocking` en `TileCache`;
`import kotlin.concurrent.Volatile`; `@OptIn(ExperimentalForeignApi::class)`; y 21 nombres de test sin `(`, `)` ni `,`.

✅ **Ktor: el freno de la ABI YA NO EXISTE** — al subir Kotlin a 2.3.21 (§3quater) volvió a 3.5.1.

⚠️ **`gradle-wrapper.jar` está en `.gitignore` → NO viene por git** y `./gradlew` muere con
`ClassNotFoundException: GradleWrapperMain`. Se regenera con el Gradle cacheado en
`~/.gradle/wrapper/dists/`: `.../bin/gradle wrapper --gradle-version 9.5.0`. OJO: eso reescribe
`gradlew`/`.bat`/`.properties` → **revierte esos 3, quédate solo con el .jar**.

### ✅ FASE 1.5 HECHA — el mapa del juego CORRE EN iOS (visto en el simulador)
`WorldMapLeafletHtml.kt` movido a `:shared` con `git mv` **conservando el paquete** → `:app` no
cambió ni un import; solo pasó de `internal` a público. `:shared` produce ahora un **framework**
(`baseName="Shared"`). App iOS en **`iosApp/`** (SwiftUI + `WKWebView`), salida de la plantilla de
Xcode y renombrada — **no** se escribió el `.pbxproj` a mano.
**MEDIDO: compila, arranca, pinta el mapa y responde a pinch-zoom. Android sigue en 161 tests, 0
fallos.** ⚠️ El framework hay que generarlo ANTES de abrir Xcode
(`:shared:linkDebugFrameworkIosSimulatorArm64`) o sale `No such module 'Shared'`.
🔴 **Deuda ya conocida:** el HTML tiene 5 rutas `file:///android_asset/…` cableadas que **en iOS no
existen**. Hoy no se nota porque la app no inyecta datos; al inyectarlos saldrán imágenes rotas.

❌ **DESMENTIDO del guion:** `URLForDirectory(...)` estaba BIEN (solo faltaba el opt-in), y en
`iosMain` hay UN fichero → el motor Darwin de Ktor **sigue sin ejercitarse**. ⚠️ **Qué NO prueba
este hito:** los 49 tests son **dominio puro**; NO tocan Room, Ktor ni Settings en iOS.

## 3quater. Sesión 2026-07-27 (Opus 5, Windows) — ⬆️ Kotlin 2.3.21 + DSL de AGP 9

**MEDIDO: `:app` 112 + `:shared` 49 = 161 tests, 0 fallos; `assembleDebug` y
`compileReleaseKotlin` OK; detekt exit 0.**
Kotlin **2.2.10 → 2.3.21** · KSP 2.3.2 → **2.3.10** · Hilt 2.57.1 → **2.60.1** ·
Ktor 3.3.3 → **3.5.1** (desbloqueado) · serialization → 1.11.0 · Compose BOM 2024.09 → **2026.06**.
- ⚠️⚠️ **NO SE PUEDE SUBIR A KOTLIN 2.4: KSP NO EXISTE para 2.4** (medido: 0 versiones en esa
  línea; la última, 2.3.10, se construye contra 2.3.20). Y aquí KSP es obligatorio (Hilt + Room).
  **2.3.21 es el techo real.** No pierdas una sesión intentándolo.
- ⚠️ **Lo caro no fue Kotlin, fue el DSL de AGP 9.** Con Kotlin 2.3 lo que era aviso pasa a ERROR:
  con `newDsl=false` el script NO compila; con `newDsl=true` `kotlin-android` es incompatible.
  Única vía: **Kotlin INTEGRADO** (`android.builtInKotlin=true`, `:app` ya NO aplica
  `kotlin-android`, `kotlinOptions` → `compilerOptions`). Y `:shared` pasa de
  `com.android.library` a **`com.android.kotlin.multiplatform.library`** (AGP 9 ya no admite la
  primera junto a KMP), con el target dentro de `kotlin { android { … } }`.
- ⚠️⚠️ **LA TAREA DE TESTS DE `:shared` CAMBIÓ DE NOMBRE:** `testDebugUnitTest` →
  **`testAndroidHostTest`**. Con el viejo, Gradle dice 'task not found'. Ya corregido en CI.
- ⚠️ **iOS NO se verificó aquí** (Windows no compila Kotlin/Native): la Mac debe re-correr
  `:shared:iosSimulatorArm64Test` y el framework, porque el plugin de Android del módulo cambió.

## 4. PENDIENTE — por prioridad

### 🔴 P0 · Antes/durante el release
1. **Probar el multijugador en dispositivo:** que los 2 jugadores **oigan** lo mismo (tag
   `SF-NET`). BT y LAN son lo que hay que validar sí o sí.
2. **Redeploy de `MultiplayerSF/` en Render** para activar el P2P (no bloquea el release).
   Con 2 teléfonos en redes distintas, buscar en logcat `SF-RTC`: `DataChannel → OPEN`.
3. **🍏 FASE 5 — UI compartida (Compose Multiplatform).** Lo único entre el mapa y un juego
   jugable en iOS. ✅ **La subida de Kotlin YA ESTÁ HECHA** (§3quater: 2.3.21, y 2.4 es imposible
   por KSP). ⚠️ **Primero la Mac debe re-verificar iOS**: el plugin de Android de `:shared` cambió.
   ⚠️ Antes hay 2 deudas baratas de la Fase 1.5: las rutas `file:///android_asset/` del mapa
   (rompen los overlays en iOS) y el proyecto Xcode, que hoy solo apunta al framework de
   **simulador**. Detalle en `iosApp/README.md`.

### 🟠 P1 · AUDIO (trabajo activo) — ver `SF/PROMPT_traspaso_audio_subtitulos.md`
**29 clips demasiado largos** para su evento y **5 fuera de −16 ±2 LUFS** → **Gemini 3.6** (con los
segundos exactos del dueño). **Faltan `attack`/`hurt`** en 6 peleadores → el dueño graba.
⚠️ **No repitas** el resumen que dice "100 % normalizados y sin faltantes": está medido y es
**falso**. De los 80 `.ogg` solo **69 son voz**; 2 no pueden normalizarse sin comprimir.

### 🟢 P2 · Animaciones congeladas (el arte se repite, **no es bug de código**)
`stun-1==stun-2==stun-3` en los 18; `bonus-7/8/9/10` estáticos en `lapresidenta`; `run-4==run-5` en
4; `forwards-3==forwards-4` en 3; `throw-2==throw-3` en 3; `super-4==super-5` en `charronegro` y
`senortienda`.

### 🔵 P2b · Motor compartido — Fases 1, 2a y 2b hechas; sigue **2c**
Siguiente: núcleo de `updateStageConstraints` (empuje de pushboxes) → `SfPhysics`; luego el
esqueleto `SfEngine` y modos como estrategia. **Exige sesión CON compilador.** Plan:
`SF/PLAN_refactor_motor_compartido.md`.

### ⚪ P3 · Bloqueado en el dueño / deuda conocida
- **Mapas UAM Azcapotzalco y Cuajimalpa:** faltan vídeos nuevos → `tools/build_map_backgrounds.py`.
- **Arte V2 de La Presidenta + metamorfosis nuevas:** croma en `tools\_para_corregir\` sin importar.
- **detekt NO está a 0:** 5 smells preexistentes (`CachingWebViewClient`, `NpcAiManager`,
  `RoadRouter`, `CatSpriteManager` ×2). Varios docs dicen "0 smells" y es **falso**.
- `07_OTHER_FEATURES.md` (87 KB) mezcla menú/ajustes con SF; su parte de SF debería migrar a `SF/`.
- **Paparazzi 5** tiene audio de Paparazzi 1; **Señor de la tienda** tiene un tramo con voz de
  Prankedy; **La Tzitzimime** mal recortada. Los 3 requieren al dueño.

## 5. Verificación antes de cerrar CUALQUIER sesión

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :shared:testAndroidHostTest
```

(En Windows, `.\gradlew.bat`. En el Mac, antes:
`export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.)

```bash
..\detekt-cli-1.23.8\bin\detekt-cli.bat --config "config\detekt\detekt.yml" --input "app\src\main\java"
```

⚠️ **NO** uses `--build-upon-default-config` en detekt: sube el conteo a 16 con reglas que el repo
no adoptó. El baseline correcto son **5 smells preexistentes**.
⚠️ **Son 161 tests: 112 en `:app` + 49 en `:shared`** (MEDIDO en el Mac 2026-07-27 contando los XML
de `build/test-results`). Los "131" y los "47" de docs viejos son **stale**.

`git status` debe mostrar **solo** lo que tocaste. Y **actualiza este archivo** antes de terminar.
