# 🧠 MEMORIA COMPARTIDA ENTRE IAs — estado vivo del trabajo

> ## Qué es este archivo
>
> El proyecto lo trabajan **varias IAs distintas** (Opus, Fable, Sol, Gemini…) que **no comparten
> memoria entre sí ni entre sesiones**. Cada una empieza de cero. Este archivo es el **único punto
> de traspaso**: lo que esté aquí es lo que sabrá la siguiente; lo que no, se pierde y se reinventa
> (o se alucina). Es **memoria operativa**, no un changelog: cuenta **en qué estado quedó todo y
> qué sigue**.
>
> ## Reglas de mantenimiento (obligatorias)
>
> 1. **Ventana de 2 DÍAS como máximo.** Solo vive aquí el trabajo de la sesión actual y, como
>    mucho, el de la anterior si sigue siendo relevante. **Todo lo que pase de 2 días se PURGA**
>    a `_ARCHIVO/HISTORIAL_sesiones_<AAAA-MM-DD>.md`, con un enlace desde aquí si hace falta.
> 2. **Techo de 200 líneas.** Es lo primero que se lee en CADA sesión: cada KB de más se paga en
>    tokens siempre. Si crece, se poda — no se justifica, se poda.
> 3. **Qué NO va aquí:** detalle de diseño (→ `SF/DISENO_ARCADE_SF_POW.md` o el doc del área),
>    historia de cómo se llegó a algo, ni nada que el código o `git log` ya digan.
> 4. **Qué SÍ va aquí:** estado real medido, lo que está a medias, lo que está BLOQUEADO y en
>    quién, las trampas que costaron caro, y los datos que contradicen a otros docs.
> 5. **Marca lo MEDIDO vs lo SUPUESTO.** Varios docs de este repo afirmaban cosas falsas
>    ("0 smells", "47 tests", "AAB 434 MiB"). Si lo verificaste, dilo; si no, dilo también.
>
> ## Cómo cerrar una sesión (ANTES de quedarte sin tokens)
>
> Purga a `_ARCHIVO/` lo que pasó de la ventana · reescribe tu sección (qué cambió, qué se
> verificó, **qué falta**) · actualiza **PENDIENTE por prioridad** · comprueba las 200 líneas.
> **Regla de oro:** si te quedas sin tokens a media tarea, actualiza ESTE archivo ANTES de parar.

**Última actualización:** 2026-07-27 · Opus 5 · rama `fase0-auditoria-kmp`
**Ventana viva:** 2026-07-26 → 2026-07-27 · *purgar a `_ARCHIVO/` a partir del 2026-07-28*

> ➡️ **AHORA:** 1.0.0.14 en revisión en Play. KMP: **Fases 0-4 COMPLETAS y verdes.**
> 🍏 **iOS ARRANCA: 49 tests en verde + FASE 1.5 HECHA** — el mapa Leaflet del juego corre en el
> simulador (visto en pantalla, 2026-07-27). Ver §3ter. Siguiente: **Fase 5** (Compose MP).

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

**Qué se hizo** (detalle en `PLAN_MIGRACION_KMP.md` y `git log`): `:shared` con `androidTarget` + 3
targets iOS; `GeoPoint` propio (osmdroid: de 51 archivos a 7); Gson fuera de producción con
`PowJson`; Fase 4 = `multiplatform-settings` + Room 2.8.4 KMP + Ktor. **DECISIONES:** juego ENTERO,
mapa Opción A, **sin subir Kotlin** hasta la Fase 5. **Las trampas que siguen vivas:**
- ⚠️ **NO cambies la ruta de la BD** (`filesDir/databases/pow_roads.db`) ni el driver de Android
  (SQLite del sistema; el bundled es solo para iOS): se perdería caché y landmarks del Diseñador.
- ⚠️ **`PowJson` imita a Gson a propósito** (`ignoreUnknownKeys`/`encodeDefaults`/`explicitNulls=
  false`/`coerceInputValues`); tocarlo rompe saves y clientes viejos EN SILENCIO.
- ⚠️ **`SfArcadeRepository` es el ÚNICO con migración real de datos** (`putStringSet` → array JSON
  en `_V2`). Sin ella, todos pierden peleadores y mapas.
- ⚠️ **Fórmulas de `GeoPoint` = port LITERAL de osmdroid**: `x*x` ≠ `.pow(2)` (2 ULP), no lo
  "simplifiques". ⚠️ **Ktor sin `HttpTimeout`** y ping 25s/20s: evitan que se caiga la partida.
- ⚠️ **`dependencies { add("kspAndroid"…) }` va DESPUÉS de `kotlin { }`** en `shared/build.gradle.kts`.

### 🔴 AUDITORÍA de las fases 1-4 — 12 bugs REALES (ya corregidos)
Dos clases de bug que **el compilador NO ve** y que habrían salido en producción:
1. **`PowJson.encodeToString(x)` COMPILA y PETA EN RUNTIME** si `x` no es serializable. Afectaba a
   los 3 transportes de SF y a 6 payloads del mapa WEB → con mapas usa `jsonOf`/`jsonArrayOf`,
   **NUNCA `encodeToString`**.
2. **kotlinx PETA si falta un campo sin default** (Gson lo dejaba en null/0) → un cliente viejo
   crasheaba al rival. **Todo campo nuevo lleva default.** Los fija
   `PayloadsSeSerializanEnRuntimeTest` y `ModelosToleranJsonIncompletoTest`.

## 3ter. Sesión 2026-07-27 (Opus 5, EN EL MAC) — 🍏 1ª compilación iOS de la historia

**✅ MEDIDO: `:shared` compila, enlaza y sus 49 tests PASAN en el simulador** (`tests=49 failures=0
errors=0 skipped=0`, contado en `shared/build/test-results/iosSimulatorArm64Test`). Klib real
(`native_targets=ios_simulator_arm64`, `compiler_version=2.2.10`); Room generó su `actual` de
`PowDatabaseConstructor` y los 5 DAO `_Impl` sin tocar nada.
**Android sigue verde: 112 + 49 = 161 tests, 0 fallos, `assembleDebug` OK.**

⚠️ **El runtime del simulador lo instala XCODE, no un DMG a mano.** Costó media sesión: un
`simctl runtime add` desde un DMG bajado con Safari queda en cuarentena en `/private/tmp` y los
tests mueren con `dyld_sim mmap() of segment failed` + `Abort trap` (134), que NO parece permisos.
Arreglo: `xcodebuild -downloadPlatform iOS` (quedó **iOS 26.5**) + `xcrun simctl runtime delete`.

**4 arreglos de código, mínimos. El PORQUÉ de cada uno en `09_CONVENTIONS_GOTCHAS.md` §🍏 KMP/iOS:**
`MapTileDao` → `suspend` (Room lo exige fuera de Android) con el `runBlocking` en `TileCache`;
`import kotlin.concurrent.Volatile`; `@OptIn(ExperimentalForeignApi::class)`; y 21 nombres de test sin `(`, `)` ni `,`.

⚠️ **Ktor 3.5.1 → 3.3.3, y NO lo subas con Kotlin en 2.2.10.** Las klibs de Ktor ≥3.4.0 son ABI
2.3.0 y nuestro compilador no las lee. El error MIENTE: dice `KLIB resolver: Could not find
"...klib"` y el fichero está ahí. Room, sqlite, serialization y settings sí son compatibles.

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

❌ **DESMENTIDO del guion:** la firma de `URLForDirectory(...)` en `PowDatabase.ios.kt` estaba BIEN;
solo faltaba el opt-in. Y en `iosMain` hay **UN** fichero: el motor Darwin de Ktor es una línea de
dependencia, no código → sigue **sin ejercitarse**. ⚠️ **Qué NO prueba este hito:** los 49 tests son
**dominio puro** (GeoPoint + 6 de SF), no tocan Room ni Ktor ni Settings. Prueban que el módulo
compila, enlaza y que el dominio se comporta igual — **no** que la BD o la red funcionen en iOS.
Eso no se sabrá hasta la Fase 1.5.

## 4. PENDIENTE — por prioridad

### 🔴 P0 · Antes/durante el release
1. **Probar el multijugador en dispositivo:** que los 2 jugadores **oigan** lo mismo (tag
   `SF-NET`). BT y LAN son lo que hay que validar sí o sí.
2. **Redeploy de `MultiplayerSF/` en Render** para activar el P2P (no bloquea el release).
   Con 2 teléfonos en redes distintas, buscar en logcat `SF-RTC`: `DataChannel → OPEN`.
3. **🍏 FASE 5 — UI compartida (Compose Multiplatform).** Es la fase GRANDE y la única que queda
   entre el mapa y un juego jugable en iOS. **Exige subir Kotlin 2.2.10 → ~2.4.x** moviendo AGP,
   KSP, Hilt y Compose **en su propio commit**. ⚠️ Al subir Kotlin, **sube también Ktor** (hoy
   clavado en 3.3.3 por la ABI de las klibs, §3ter).
   ⚠️ Antes de eso hay 2 deudas de la Fase 1.5, más baratas: las rutas `file:///android_asset/`
   del mapa (rompen los overlays en iOS) y el proyecto Xcode, que hoy solo apunta al framework de
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
./gradlew :app:assembleDebug :app:testDebugUnitTest :shared:testDebugUnitTest
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
