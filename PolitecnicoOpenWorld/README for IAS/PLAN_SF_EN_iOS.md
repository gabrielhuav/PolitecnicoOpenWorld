# 🥊🍏 "Huelum vs. Goya" en iOS — estado y guion

**Actualizado:** 2026-07-28 · **Rama:** `fase0-auditoria-kmp` · **Contexto:** `PLAN_MIGRACION_KMP.md`

> **Objetivo:** que el modo pelea (y solo ese) funcione en iOS, con el menú principal mostrando
> únicamente **AJUSTES**, **COLECCIONABLES** y **HUELUM VS. GOYA**.

---

## 0. ⚡ LO PRIMERO: SÍ SE PUEDE VERIFICAR iOS DESDE WINDOWS

Esto se descubrió el 2026-07-28 y **cambia cómo se reparte el trabajo**. Kotlin/Native compila los
klibs de iOS en Windows: no hacen falta un Mac ni Xcode para type-checkear código de iOS.

| Comando | En Windows | Qué prueba |
|---|---|---|
| `:shared:compileKotlinIosSimulatorArm64` | ✅ **funciona** | Todo `commonMain` + `iosMain`, cinterops de Apple incluidos |
| `:shared:compileTestKotlinIosSimulatorArm64` | ✅ **funciona** | Que los tests de iOS compilan |
| `:shared:compileIosMainKotlinMetadata` | ✅ **funciona** | Resuelve `NSBundle`, `AVAudioPlayer`, `UIScreen`… |
| `:shared:linkDebugFrameworkIosSimulatorArm64` | ❌ **MIENTE** | Dice BUILD SUCCESSFUL y **no produce nada** |
| Correr los tests / el simulador | ❌ | Solo Mac |

Comprobado que no es un falso positivo: al invocar un método inexistente de `NSBundle` sale
`Unresolved reference` en 4 segundos.

⚠️ **NO uses la tarea de `link` como prueba de nada**: en un host que no es Mac se salta en silencio
y devuelve éxito. El directorio de salida ni se crea.

---

## 1. Lo que YA está hecho y verificado

| Pieza | Estado |
|---|---|
| `:shared` con targets iOS | ✅ 66 tests verdes en el simulador (Mac). Hoy son **78** — los 12 nuevos aún no se han corrido en iOS |
| Dominio puro de SF + motor (física, cámara, barra de vida) | ✅ en `:shared` con tests |
| BD (Room), ajustes, JSON, WebSocket (Ktor) | ✅ multiplataforma |
| **Catálogo de modos por plataforma** (`PowModos`) | ✅ el menú ya esconde lo que no corre en iOS |
| **Compose Multiplatform 1.11.1** en `:shared` | ✅ compila para iOS y Android |
| **`PowViewModel`** (base de VM multiplataforma) | ✅ |
| **`PowAssets`** (AssetManager ↔ NSBundle) | ✅ con tests de contrato |
| **`PowImagen`** (todo `android.graphics`) | ✅ |
| **`PowAudio`** (SoundPool/MediaPlayer ↔ AVAudioPlayer) | ✅ |
| **`PowCerrojo`** (sustituye a `@Synchronized`) | ✅ |
| **`SfSharedSheets` + `SfFrameCatalog`** en `commonMain` | ✅ **y probados en el emulador** |
| App iOS mínima (`iosApp/`) + framework `Shared` | ✅ arranca y pinta el mapa |

**La prueba que más vale:** en el emulador corre una pelea **ROBOT vs PARAMED CR**, y los dos son
peleadores de *set compartido* — o sea que sus hojas de sprites las **arma en runtime** el código
recién portado a Compose. Se ven bien, la patada queda bien anclada y sin espejar, y hay daño. Es la
señal de que el port de gráficos es correcto, no solo compilable.

---

## 2. Tres decisiones que conviene no revisar desde cero

### 2.1 `iosX64` está FUERA, y hay que dejarlo fuera
Compose Multiplatform 1.11.1 **no publica** para ese target (medido: `runtime-iosx64` → HTTP 404,
mientras `iosarm64` y `iossimulatorarm64` dan 200). Con él declarado, **todos** los source sets
compartidos fallan con `Unresolved platforms: [iosX64]` — un error que no menciona a Compose por
ningún lado y que cuesta media hora localizar. `iosX64` es solo el simulador de los Mac **Intel**.

### 2.2 El ViewModel NO usa la librería oficial
`org.jetbrains.androidx.lifecycle:lifecycle-viewmodel` sería lo natural, pero la única versión con
artefactos de iOS es la **2.11.0** (2.10.0 y 2.9.4 → 404) y esa **exige `compileSdk 37`**. Subir el
compileSdk de una app en producción para compartir una clase base no compensa: `PowViewModel` cuesta
20 líneas y en Android **es** un `androidx.lifecycle.ViewModel`, así que Hilt no se entera.

### 2.3 Los gráficos NO necesitaron `expect/actual`
`ImageBitmap`, `Canvas`, `CanvasDrawScope` y `readPixels` **ya son multiplataforma**. El "muro de
`android.graphics`" se cruza traduciendo llamadas — la tabla está en `PowImagen`. La **única**
excepción es `decodificarReducido`, porque el decodificador común no tiene nada como `inSampleSize`
y perderlo sería una regresión de memoria en Android (los atlas croma son 2560×7168).

---

## 3. Lo que FALTA — medido

`features/streetfighter/` sigue teniendo **~14 000 líneas** en `:app`.

| Bloqueador | Archivos | Nota |
|---|---:|---|
| **`org.json`** (`JSONObject`, `optString`…) | 3 | `SfCombos`, `SfSpecialPhrases`, `SfVoicePhrases`. **Ya lo intenté y lo revertí**: es una reescritura a kotlinx, no un cambio de import. Son pequeños y **no dependen de nada más**: es el mejor punto de entrada. |
| **UI de Compose atada a `Context`** | ~8 | `StreetFighterScreen` (1902), `SfSceneRenderer` (1122), `SfMenuOverlays` (749)… El bulto. |
| **ViewModel** | 4 | Ya existe `PowViewModel`; falta que `StreetFighterViewModel` lo herede y cambiar `viewModelScope` → `scope`. |
| **Hilt** | 1 | Solo el VM. En iOS se construye a mano. |
| **BT + WebRTC** | 3 | ⚠️ **NO se portan**: en iOS el modo pelea sale **sin multijugador**. |

**Y aparte:** los **107 MB** de `assets/STREETFIGHTER` tienen que entrar en el bundle de iOS.

⚠️ **CÓMO SE METEN LOS ASSETS EN EL BUNDLE** (esto es Xcode, no código): `app/src/main/assets` se
añade al target como **"folder reference" — la carpeta AZUL, no la amarilla**. La azul conserva las
subcarpetas; la amarilla APLASTA todo a un nivel y entonces `STREETFIGHTER/DATA/x.json` deja de
existir aunque el archivo esté ahí. No avisa de nada. `AssetsDeBundle` espera la carpeta en `assets`.

---

## 4. Orden propuesto (cada paso deja Android verde)

1. **Los 3 archivos de `org.json` → kotlinx.** Pequeños, aislados, y no hace falta Mac.
2. **`StreetFighterViewModel` hereda de `PowViewModel`** (`viewModelScope` → `scope`).
3. **Cambiar los usos de audio a `PowAudio`** en `StreetFighterScreen`.
4. **Bajar la UI a `commonMain`**, empezando por lo pequeño (`SfBitmapText`, `SfComboSheetOverlay`)
   y dejando `SfSceneRenderer` y `StreetFighterScreen` para el final.
5. **Assets al bundle** y arrancar una pelea en el simulador.

---

## 5. Reglas que NO se negocian

- **Android no se rompe.** Tras cada paso: `:app:assembleDebug` + los tests + `detekt`.
  Y si tocas cómo se pintan o cargan los sprites, **pruébalo en el emulador**: los tests no ven eso.
- **Nada de Android en `commonMain`.** Si algo lo necesita, `expect/actual`.
- **`bash tools/check_kmp_test_names.sh`** antes de commitear tests: `(`, `)` y `,` en los backticks
  compilan en la JVM y rompen Kotlin/Native. Ya pasó tres veces.
- **Un modo NO se añade al catálogo de iOS** (`modosDe`) hasta que funcione **en el simulador**.
  `PowModosTest` se pone rojo a propósito para forzar esa confirmación.
- ⚠️ **Esconder el botón no porta el modo.** Que el menú de iOS liste "Huelum vs. Goya" no lo hace
  jugable todavía.
