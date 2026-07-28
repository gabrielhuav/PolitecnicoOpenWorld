# 🥊🍏 PLAN · "Huelum vs. Goya" corriendo en iOS

**Creado:** 2026-07-27 · **Rama:** `fase0-auditoria-kmp` · **Contexto:** `PLAN_MIGRACION_KMP.md`

> **Objetivo:** que el modo pelea (y solo ese) funcione completo en iOS, con el menú principal
> mostrando únicamente **AJUSTES**, **COLECCIONABLES** y **HUELUM VS. GOYA**.
>
> **⚠️ ESTE PLAN SE EJECUTA EN EL MAC.** Todo lo que queda toca Compose Multiplatform, assets y
> audio de iOS: **no se puede verificar desde Windows**, donde Kotlin/Native ni siquiera compila.
> Escribirlo a ciegas desde Windows sería producir miles de líneas que nadie ha ejecutado.

---

## 1. Lo que YA está hecho y verificado

| | Estado |
|---|---|
| Módulo `:shared` con los 3 targets iOS | ✅ 66 tests verdes en el simulador (Mac). Hoy son 72: **los 6 nuevos de `PowModosTest` aún no se han corrido en iOS** |
| Dominio puro de SF (`SfStateMachine`, `SfDamage`, `SfPhysics`, `SfCombos`…) | ✅ en `:shared` |
| Motor: empuje de pushboxes, cámara, chispazos, barra de vida | ✅ en `:shared` con tests |
| BD (Room), ajustes (Settings), JSON, WebSocket (Ktor) | ✅ multiplataforma |
| **Catálogo de modos por plataforma** (`PowModo` / `modosDe`) | ✅ el menú ya esconde lo que no corre en iOS |
| App iOS mínima (`iosApp/`) + framework `Shared` | ✅ arranca y pinta el mapa |

---

## 2. Lo que FALTA — medido, archivo por archivo

`features/streetfighter/` son **14.618 líneas**. Esto es lo que las ata a Android:

| Bloqueador | Archivos | Qué hay que hacer |
|---|---:|---|
| **Assets** (`context.assets`) | **9** | `expect fun leerAssetBytes(ruta): ByteArray` + `leerAssetTexto`. En Android delega en `AssetManager`; en iOS, en `NSBundle`. |
| **Gráficos** (`android.graphics`) | **6** | `Bitmap`/`BitmapFactory` → `ImageBitmap` de Compose MP. Es el recorte del atlas: `Bitmap.createBitmap(src, x, y, w, h)`. |
| **Audio** (`android.media`) | **3** | `expect` de reproducción. Android: `SoundPool`/`MediaPlayer`. iOS: `AVAudioPlayer`. |
| **ViewModel** | **4** | `androidx.lifecycle.ViewModel` → `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel` **2.11.0** (verificado que existe). |
| **Hilt** | **1** | Solo `StreetFighterViewModel`. En iOS se construye a mano; Android sigue con Hilt. |
| **BT + WebRTC** | **3** | ⚠️ **NO se portan.** En iOS el modo pelea sale **sin multijugador** (`SfBtClient`, `SfWebRtcClient` se quedan en `androidMain`). |

**Y aparte:** los **107 MB** de `assets/STREETFIGHTER` tienen que entrar en el bundle de iOS.

---

## 3. Orden propuesto (cada paso deja Android verde y es verificable en el simulador)

### Paso 1 · Compose Multiplatform en `:shared`
Añadir el plugin `org.jetbrains.compose` **1.11.1** (tiene artefactos iOS: verificado HTTP 200) y
`compose.runtime` / `compose.foundation` / `compose.material3` a `commonMain`.
✅ **Verifica:** que `:shared` sigue compilando para iOS y Android **sin mover una sola línea de UI**.
⚠️ Si las klibs de Compose dan el error de ABI que dio Ktor, **para aquí** y dilo: significaría que
CMP 1.11.1 no cuadra con Kotlin 2.3.21, y como **KSP no existe para 2.4** no se puede subir Kotlin.

### Paso 2 · Los assets, que es lo que más archivos toca
`expect`/`actual` para leer un asset. Es el paso que **desbloquea 9 archivos** y no necesita Compose.
Se puede hacer y testear antes que la UI.
✅ **Verifica:** un test en `commonTest` que lea un asset conocido en las dos plataformas.

### Paso 3 · Audio
`expect` de reproducción de un clip. Empieza por lo mínimo: reproducir y parar.
✅ **Verifica:** que suena un golpe en el simulador.

### Paso 4 · El ViewModel
Cambiar a `lifecycle-viewmodel` multiplataforma. Android no debería notarlo.
✅ **Verifica:** los 112 tests de `:app` siguen verdes.

### Paso 5 · La UI (lo más grande)
Mover `features/streetfighter/ui/` a `commonMain`. El renderer (`SfSceneRenderer`, 1225 líneas) es
el que más pelea dará: es Canvas + `ImageBitmap`.
⚠️ **Excluye de iOS los overlays de multijugador** (`SfOnlineOverlays.kt`): en iOS ese modo no existe.

### Paso 6 · Assets en el bundle y arranque
Meter `STREETFIGHTER/` (107 MB) en el bundle y que el menú de iOS llegue a una pelea.

---

## 4. Reglas que NO se negocian

- **Android no se rompe.** Tras cada paso: `:app:assembleDebug` + los tests + `detekt`.
- **Nada de Android en `commonMain`.** Si algo lo necesita, `expect/actual`.
- **`bash tools/check_kmp_test_names.sh`** antes de commitear tests: `(`, `)` y `,` en los backticks
  compilan en la JVM y rompen Kotlin/Native. Ya pasó tres veces.
- **Un modo NO se añade al catálogo de iOS** (`modosDe`) hasta que funcione **en el simulador**.
  `PowModosTest` se pone rojo a propósito para forzar esa confirmación.

---

## 5. Expectativa honesta

Esto **no es una tarde**. Son ~14.600 líneas con seis frentes distintos, y el paso 5 es el que en
`PLAN_MIGRACION_KMP.md` se estimó en 4-6 sesiones. El orden de arriba está pensado para que **cada
paso deje algo funcionando y verificado**, en vez de un big-bang que solo se puede probar al final.

Si hay que recortar: **los pasos 1-4 son los que valen aunque el 5 se retrase**, porque desbloquean
assets, audio y ViewModel para TODO el proyecto, no solo para el modo pelea.
