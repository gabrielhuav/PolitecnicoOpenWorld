# 🥊 PROMPT para Sol 5.6 (Windows escritorio) — paso 4 de SF en iOS + el audio

**Creado:** 2026-07-28 · Por: Opus 5 desde el **Mac** · Rama: `fase0-auditoria-kmp`

> **Cómo usar este archivo.** Todo lo que hay debajo de la línea es el prompt: cópialo entero.
> Lo de arriba es contexto para el dueño.

**Por qué se delega:** lo que queda es volumen mecánico y repetible (≈19 000 líneas entre UI y
widgets, más convertir 82 audios). Windows **sí** puede type-checkear iOS
(`:shared:compileKotlinIosSimulatorArm64`) y tiene emulador Android para verificar de oído. Lo único
que necesita Mac es **ver** las pantallas y **oír** iOS, y eso se hace después, de una pasada.

---

Sigo con POW. Repo (¡carpeta doble!):
`C:\Users\gabri\Documents\GitHub Desktop\PolitecnicoOpenWorld\PolitecnicoOpenWorld`
Rama `fase0-auditoria-kmp`. **`git pull` antes de nada**: viene trabajo del Mac.

Lee: `README for IAS/_SESION_ACTUAL.md` (§3undecies y PENDIENTE) y
`README for IAS/PLAN_SF_EN_iOS.md`.

## LO QUE YA ESTÁ VERIFICADO EN EL SIMULADOR — no lo rehagas ni lo dudes

- **Compose Multiplatform CORRE en iOS.** Hay una pestaña "SF" en `iosApp` con un escaparate
  (`shared/src/iosMain/.../SfEscaparate.kt`) donde se ven las pantallas ya portadas.
- **Los 107 MB de assets están en el bundle** y se leen: `SfBitmapText` pinta la fuente arcade.
- **La ruta del bundle es correcta y está confirmada:** `AssetsDeBundle` y `AudioDeAVFoundation`
  resuelven `<bundle>/assets/<ruta>`, y la fase de `rsync` del proyecto Xcode deja ahí
  `STREETFIGHTER/`. **No toques nada de rutas de assets: funcionan.**
- **`R.string` ya NO es un bloqueador**: hay 196 claves en
  `shared/src/commonMain/composeResources/values{,-en}/strings.xml` y `Res` se genera público en
  `ovh.gabrielhuav.pow.shared.recursos`.
- Van **2 de 7** pantallas (`SfBitmapText`, `SfComboSheetOverlay`) + `PowButton`.

## TAREA 1 — EL AUDIO NO SUENA EN iOS. Es de formato, no de ruta.

**MEDIDO en el simulador el 2026-07-28**, pulsando los dos botones del escaparate:

| Fichero | Resultado |
|---|---|
| `STREETFIGHTER/SOUNDS/light-attack.ogg` | ❌ `cargarEfecto` devuelve `null` |
| `STREETFIGHTER/SOUNDS/prankedy_lobby.mp3` | ✅ carga y **suena** |

O sea: **AVAudioPlayer no abre Ogg Vorbis.** Inventario de `assets/STREETFIGHTER` (17 MB en
`SOUNDS`): **82 `.ogg`** y solo **6 `.mp3`**. En iOS la pelea saldría MUDA.

**Lo que hay que hacer:** convertir los 82 `.ogg` a un formato que soporten **las dos** plataformas.
Recomiendo **`.m4a` (AAC)**: Android lo reproduce igual de bien y así queda **un solo formato y un
solo fichero por sonido**, sin bifurcar el código ni duplicar assets.

```bash
# Uno por uno, conservando el nombre base:
ffmpeg -i entrada.ogg -c:a aac -b:a 128k salida.m4a
```

⚠️ **Cuidado con esto, que es lo que puede romper Android:**
- Las claves de sonido del juego son **el nombre base sin extensión** (así las usa el VM). Revisa
  quién concatena la extensión antes de renombrar nada.
- **El volumen percibido debe quedar igual.** El P1 de audio del proyecto lleva medidas de LUFS;
  no rehagas ese trabajo con una conversión descuidada.
- **Verifica DE OÍDO en el emulador**: una pelea entera con golpes, música e intro. Los tests no
  oyen nada.
- Si prefieres **no** tocar los `.ogg` de Android, la alternativa es generar los `.m4a` al lado y
  que `PowAudio` elija extensión por plataforma. Es más código y dos copias de 17 MB; decide tú,
  pero **déjalo escrito en `_SESION_ACTUAL.md`**.

**Ya arreglado por mí, no lo revierta:** `AudioDeAVFoundation.crear()` y `duracionMs()` envuelven el
constructor de `AVAudioPlayer` en `runCatching`. Sin eso, un formato no soportado **cerraba la app**
con SIGABRT: `initWithContentsOfURL:error:` lleva un out-param `NSError**` y Kotlin/Native lo mapea
como función que LANZA.

## TAREA 2 — bajar la UI de SF a `commonMain` (paso 4, van 2/7)

Orden, de menos a más riesgo. **UN COMMIT POR PANTALLA**:

| # | Fichero | Líneas | Strings | Refs a `Context` |
|---|---|---:|---:|---:|
| 3 | `SfTutorialOverlay.kt` | 260 | 8 | 0 |
| 4 | `SfStageSelectOverlay.kt` | 388 | 5 | 4 |
| 5 | `SfMenuOverlays.kt` | 749 | 55 | 0 |
| 6 | `SfSceneRenderer.kt` | 1126 | 3 | 3 |
| 7 | `StreetFighterScreen.kt` | 1819 | 52 | 3 |

**El patrón de los strings ya está fijado y es mecánico** (mira `SfComboSheetOverlay.kt` en
`:shared`, que ya está hecho):

```
androidx.compose.ui.res.stringResource  →  org.jetbrains.compose.resources.stringResource
import ovh.gabrielhuav.pow.R            →  import ovh.gabrielhuav.pow.shared.recursos.Res
                                            import ovh.gabrielhuav.pow.shared.recursos.*
R.string.loQueSea                       →  Res.string.loQueSea
```

Si te falta una clave, **añádela a los dos** `composeResources/values/strings.xml` y
`values-en/strings.xml`, y **bórrala de `app/src/main/res/values*`**: no puede haber dos fuentes de
verdad para el mismo texto.

**Mueve los ficheros con `git mv` CONSERVANDO EL PAQUETE.** Es lo que hace que `:app` no tenga que
cambiar ni un `import`.

⚠️ **LA CASCADA, que no estaba en ningún plan:** portar una pantalla arrastra los widgets de
`app/src/main/java/ovh/gabrielhuav/pow/ui/components/` — **27 ficheros, 4617 líneas, 19 con
ataduras a Android**. Baja solo los que haga falta, y por el mismo procedimiento.

Usa lo que ya existe, **no lo reinventes**: `PowAssets` (nada de `context.assets`), `PowImagen`
(nada de `android.graphics`; `recycle()` se BORRA), `PowAudio`, `PowViewModel.scope` (nada de
`viewModelScope`), `PowCerrojo`, `PowJsonLectura`.

**EXCLUYE de iOS `SfOnlineOverlays.kt`**: allí no hay multijugador.

## TAREA 3 (si llegas) — `MainMenuScreen` a `commonMain`

632 líneas, 26 strings, **cero `Context`**. Es la pantalla grande más barata y la que el dueño
quiere ver: en iOS el menú debe salir **idéntico al de Android** salvo que Mundo Libre, Modo
Historia y Multijugador aparecen **desactivados** — eso ya lo decide `PowModos.disponible()`, no
inventes otra regla.

## CÓMO SE VERIFICA (los tres, siempre)

```bash
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :shared:testAndroidHostTest
.\gradlew.bat :shared:compileKotlinIosSimulatorArm64
bash tools/check_kmp_test_names.sh
```
Esperado hoy: **114 + 104 = 218 tests, 0 fallos**.

⚠️ **NO uses `:shared:linkDebugFrameworkIosSimulatorArm64` como prueba de nada**: en Windows dice
BUILD SUCCESSFUL y no produce nada. Solo vale en el Mac.
⚠️ Si tocas audio o sprites, **pruébalo en el emulador**: los tests no ven ni oyen eso.

## LO QUE NO SE TOCA

- Kotlin se queda en **2.3.21** (KSP no existe para 2.4). `iosX64` sigue fuera.
- La ruta de la BD, las opciones de `PowJson`, `SfArcadeRepository`, `SfVocesReglas`.
- **Nada de `iosApp/`, del `Info.plist` ni del proyecto Xcode**: eso es del Mac y está afinado
  (Compose aborta si falta `CADisableMinimumFrameDurationOnPhone`).

Al terminar: actualiza `_SESION_ACTUAL.md` (**techo 200 líneas**), deja **medido** qué pasó, y di
exactamente qué falta. `git pull` antes de cada push.
