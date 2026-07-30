# 🧠 MEMORIA COMPARTIDA ENTRE IAs — estado vivo

> Único traspaso entre IAs. Ventana de 2 días; máximo 200 líneas. Aquí va estado medido,
> trabajo abierto y trampas caras. Diseño e historia viven en los documentos de cada área.

**Última actualización:** 2026-07-30 · Opus 5 (laptop) · ramas `fase0-auditoria-kmp` + `perf-gama-baja-coleccionables`

> ➡️ **AHORA:** la pelea de SF corre, suena, se juega y guarda en iOS. Menú, Ajustes y
> Coleccionables ya están en `commonMain`. **Siguiente paso:** Mac enlaza la navegación real
> menú → Ajustes/Coleccionables/SF y elimina `SfEscaparate` solo después de verla completa.
> Guion: `PROMPT_MAC_navegacion_iOS.md`. **En paralelo hay un PR de gama baja abierto** (§3undecies):
> arregla el arte roto de coleccionables en partidas viejas. 228 tests.

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

- Assets SF: `<bundle>/assets/STREETFIGHTER/`; sprites, escenarios y audio M4A funcionan.
- No quitar el `dependsOn("assemble*MainResources")` de `shared/build.gradle.kts`: sin él faltan
  `composeResources` de Main y la app se cierra desde una corrutina.
- No tocar `Info.plist`, fase `rsync`, `ENABLE_USER_SCRIPT_SANDBOXING = NO` ni la ruta del bundle.
- `CADisableMinimumFrameDurationOnPhone` debe seguir en `Info.plist`.
- Pelea real verificada en simulador: selección, ronda completa, audio, pausa, minimizar,
  cerrar/reabrir y reanudar snapshot de `NSUserDefaults`.
- Offline común; BT/LAN/WebRTC siguen Android-only. iOS solo ofrece Ajustes, Coleccionables y SF;
  `PowModos.kt` es la fuente de verdad.

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
- 🐢 `CollectibleCard` decodificaba en el hilo de composición y LazyGrid lo repetía al hacer scroll.
  MEDIDO: ~600×420 pintados a **64 dp** → ~1 MB cada uno, **6,7 MB los siete**. Ahora
  `PowImagenCache` (LRU acotada, como `nativeDrawableCache`) + `rememberImagenDeAsset`
  (`Dispatchers.Default`), con `reduccion = 2` → ~1,7 MB. Colgada de `onTrimMemory`.
- ⚠️ **Gotcha nuevo:** en Kotlin los comentarios de bloque **se anidan**, así que una ruta con
  comodín dentro de un KDoc abre un comentario que nunca cierra (`Unclosed comment`).

## 4. PENDIENTE — prioridad

### 🔴 P0

1. **Mac:** ejecutar `PROMPT_MAC_navegacion_iOS.md`. Enlazar el menú real a Ajustes,
   Coleccionables y SF; verificar los ocho pasos en simulador; luego borrar `SfEscaparate`,
   `MapaTab` y el tab de escaparate.
2. Probar multijugador en dos dispositivos: ambos deben oír lo mismo (`SF-NET`).
3. Redeploy `MultiplayerSF/` en Render; con redes distintas buscar `SF-RTC: DataChannel → OPEN`.

### 🟠 P1 · 🐢 gama baja: el selector de peleadores arma sprites en el hilo de UI

`rememberFighterPreview` (`SfCharacterCard.kt`) llama a `SfSharedSheets.sheetFor` **dentro de
`remember`**, o sea durante la composición — y su propio KDoc dice "llamar fuera del hilo de dibujo
si se puede". Se usa en la rejilla del selector (`SfMenuOverlays.kt:470`): con 18 peleadores y una
caché de **3** entradas, recorrer el roster reconstruye hojas una y otra vez en el hilo de UI.
**Es anterior al PR 40d532b5.** La receta ya existe: `rememberImagenDeAsset` + `PowImagenCache`
(rama `perf-gama-baja-coleccionables`). Verificar en el emulador, no solo compilar.

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
Esperado: **218 = 114 app + 104 shared**, 0 fallos. Si se toca `commonTest`, ejecutar el guard.

Detekt CI desde la raíz exterior:

```bash
./detekt-cli-1.23.8/bin/detekt-cli --config PolitecnicoOpenWorld/config/detekt/detekt.yml --build-upon-default-config --input PolitecnicoOpenWorld/app/src/main/java,PolitecnicoOpenWorld/shared/src/commonMain/kotlin --baseline PolitecnicoOpenWorld/config/detekt/baseline.xml
```

Debe dar exit 0. El input incluye `shared`. Antes de push: `git status`, actualizar este archivo
y hacer `git pull` inmediatamente antes del push.
