# 🤝 TRASPASO a Sol 5.6 — sacar el release HOY y bajar el tamaño

**Creado:** 2026-07-31 · Opus 5 (Windows, PC de escritorio) · Rama `perf-gama-baja-coleccionables`
**Todo va en ESA rama.** No abras otra.

> **Lo que tienes que saber en diez segundos:** la rama lleva 82 commits de migración a Kotlin
> Multiplatform. **Android ya está verificado en emulador** (lo hice yo hoy) y **arreglado**: había
> 3 regresiones jugables, están corregidas y medidas. Queda **una cuarta sin arreglar** que es
> decisión del dueño, y queda **publicar**. iOS va por la mitad y **NO bloquea nada**: el release
> es de Android.

---

## 0. La máquina: rutas y comandos que YA funcionan aquí

No los adivines, están medidos hoy en esta PC.

| Qué | Dónde |
|---|---|
| Raíz del proyecto (`gradlew`, `tools/`) | `C:\Users\gabri\Documents\GitHub Desktop\PolitecnicoOpenWorld\PolitecnicoOpenWorld` |
| Raíz del **repo git** y de `.github/` | `C:\Users\gabri\Documents\GitHub Desktop\PolitecnicoOpenWorld` |
| SDK de Android | `D:\Android\Sdk` |
| **AVDs** (¡no están en `~/.android`!) | `D:\Android\avd` — `ANDROID_AVD_HOME=D:\Android\avd` |
| adb | `D:/Android/Sdk/platform-tools/adb.exe` |
| emulator | `D:/Android/Sdk/emulator/emulator.exe` |
| JDK | Corretto 21.0.5 en el PATH |

⚠️ **La carpeta es doble y la ruta lleva un espacio: entrecomíllala siempre.**

### Emulador — la receta exacta

```bash
"D:/Android/Sdk/emulator/emulator.exe" -list-avds
```

Hay tres: `Medium_Phone_API_35` (el bueno, 2 GB RAM), `Small_Phone_API_35` (1 GB — sirve para
probar gama baja) y `Nexus`. Los tres son API 35 `google_apis_playstore`, x86_64.

```bash
"D:/Android/Sdk/emulator/emulator.exe" -avd Medium_Phone_API_35 -no-snapshot-load -netdelay none -netspeed full
```

Lánzalo **en segundo plano** (tarda ~2 min en arrancar) y espera a que `sys.boot_completed` sea 1:

```bash
"D:/Android/Sdk/platform-tools/adb.exe" -s emulator-5554 shell getprop sys.boot_completed
```

> ⚠️ **YA LE SUBÍ EL DISCO A 24 GB.** El APK son 426 MB y con los 6 GB de fábrica el install
> fallaba con `INSTALL_FAILED_INSUFFICIENT_STORAGE`. Está en
> `D:\Android\avd\Medium_Phone_API_35.avd\config.ini` (`disk.dataPartition.size=25769803776`), con
> copia del original en `config.ini.bak-claude`. **Si usas `Small_Phone_API_35`, tendrás que
> hacerle lo mismo** y arrancarlo con `-wipe-data` para que el cambio prenda.

### Trampas de Git Bash en esta PC (me costaron dos intentos)

- **Las rutas del emulador se mangonean.** `adb shell ... /sdcard/x.mp4` se convierte en
  `C:/Program Files/Git/sdcard/x.mp4`. Solución: `export MSYS_NO_PATHCONV=1`.
- **`adb shell "df -h /data"`**: entrecomilla el comando entero o pasa lo mismo.
- `screenrecord` **muere si el shell que lo lanzó termina**. Lánzalo como proceso de fondo de
  verdad y toca la pantalla desde OTRA llamada; si no, te salen vídeos de 1,6 s.

### Manejar la app sin manos

```bash
A="D:/Android/Sdk/platform-tools/adb.exe"
$A -s emulator-5554 install -r -t -d app/build/outputs/apk/debug/app-debug.apk   # ~60 s
$A -s emulator-5554 shell monkey -p ovh.gabrielhuav.pow -c android.intent.category.LAUNCHER 1
$A -s emulator-5554 shell input tap <x> <y>
$A -s emulator-5554 exec-out screencap -p > pantalla.png
$A -s emulator-5554 shell "run-as ovh.gabrielhuav.pow cat /data/data/ovh.gabrielhuav.pow/shared_prefs/pow_game_settings.xml"
```

⚠️ **`install -r` a secas falló con un mensaje VACÍO.** Con `-d` (permitir “downgrade”, aquí ambos
son versionCode 12) entra a la primera. `-r` conserva los datos: es lo que hace falta para probar
migración.

⚠️ El emulador **se pone en horizontal solo** al entrar a Huelum vs. Goya. Las coordenadas de los
taps cambian de 1080×2400 a 2400×1080. No te vuelvas loco.

### La red de seguridad (córrela antes y después de tocar nada)

```bash
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :shared:testAndroidHostTest
```

**274 tests = 119 `:app` + 155 `:shared`, 0 fallos.** Medido hoy, dos veces (antes y después de mis
arreglos). Y el guard, que es barato y ha salvado el culo tres veces:

```bash
bash tools/check_kmp_test_names.sh
```

iOS **se compila desde Windows** y merece la pena aunque no vayas a tocar iOS:

```bash
./gradlew.bat :shared:compileKotlinIosSimulatorArm64      # BUILD SUCCESSFUL hoy
```

---

## 1. Lo que hice hoy (para que no lo repitas)

### ✅ Verificación de Android, con la prueba que importa

**Instalé primero un build de `main`** (el commit publicado, `1.0.0.14`) en un worktree
—`C:\Users\gabri\AppData\Local\Temp\pow-main`, todavía está ahí con su APK compilado—, **jugué para
generar partida** y **actualicé encima** con el de la rama. Instalar limpio no prueba nada de la
migración de datos; esto sí.

> ℹ️ No usé el APK literal de Play: instalarlo exige iniciar sesión con la cuenta de Google del
> dueño en el emulador y yo no meto credenciales. Un build de `main` prueba exactamente lo mismo,
> porque lo que importa es que los datos los **escriba el código viejo** y los **lea el nuevo**.

Sobrevivió todo lo del §1 del prompt anterior:

| Qué | Resultado |
|---|---|
| `pow_game_settings.xml` con `APP_LANGUAGE=es` y `DEVELOPER_MODE=true` | ✅ intacto, mismo archivo y mismas claves |
| Sesión de arcade a medias (`pow_sf_arcade.xml`, JSON `_V2`) | ✅ **byte a byte idéntica**, y sale el diálogo “Continuar pelea” |
| `files/databases/pow_roads.db` (+`-shm`/`-wal`) | ✅ en su sitio, no se rehizo |
| Menú principal | ✅ los 6 botones, insignias PRE-ALPHA/BETA, **ningún EN OBRAS** |
| Ajustes | ✅ las 6 categorías; Modo Desarrollador desbloquea el roster entero |
| Minimizar y volver en plena pelea | ✅ vuelve en PAUSA, sin crash |

### ✅ Tres regresiones encontradas y ARREGLADAS — commit `016e7406`

Las tres las reportó el dueño jugando, las tres eran de la migración KMP, las tres están
verificadas en emulador. El commit las explica con detalle; el resumen:

1. **La música del SF arrancaba y volvía a arrancar.** `LifecycleRegistry.addObserver` pone al día
   al observador nuevo, así que `onResume` disparaba solo por montar la pantalla. Antes era
   inofensivo (`MediaPlayer.start()` sobre algo que ya suena es un no-op); `PowClip.reproducir`
   **rebobina por contrato**, así que pasó a cortar la pista. Ahora reanudar se empareja con una
   pausa previa.
2. **Las previews del selector, borrosas.** En `main` el recorte salía de
   `BitmapRegionDecoder.decodeRegion()` (resolución completa). En KMP no hay region decoder → se
   pasó a “decodifica el atlas entero reducido ×4 y recorta”, o sea **1/16 de los píxeles**. Ahora
   el muestreo depende de la gama: **normal → 1** (idéntico a lo publicado), **baja → 4** (sin
   tocar). Es la misma regla que la pelea ya usa en producción.
3. **El `"?"` al elegir peleador.** La caché lleva `animate` en la clave, así que enfocar una card
   es un fallo de caché y la preview se iba a `null` ~300 ms. Ahora se rellena con la otra variante
   del mismo peleador, que ya está en memoria.

### ✅ Release preparado (falta apretar el botón)

- `versionName` **1.0.0.14 → 1.0.0.15** en `app/build.gradle.kts:26`.
- Notas ES + EN reescritas para que hablen de lo que de verdad cambia para el jugador.
- Comprobado a mano lo que exige la puerta de cumplimiento: la rama **`gh-pages` existe** y los dos
  `whatsnew` están y no están vacíos.

---

## 2. 🔴 LO PRIMERO: la cuarta regresión, que NO arreglé

**El selector de idioma dejó de funcionar en todo lo que se movió a `:shared`.**

Medido hoy: con `APP_LANGUAGE=es` guardado, el **menú principal sale en español** (es de `:app`,
usa `R.string`) pero **Ajustes, Coleccionables y Huelum vs. Goya salen en el idioma del SISTEMA**
(inglés en el emulador): “CHOOSE YOUR FIGHTER”, “Confirm”, “PAUSED”. En `main` esas pantallas
estaban en `:app` y salían en español. Son **3 de los 6 botones del menú**.

**La causa.** `MainActivity.attachBaseContext` aplica el idioma con
`LocaleHelper.wrap` (`createConfigurationContext` + `Locale.setDefault`). Eso arregla los
`R.string`, pero **Compose Resources no lo mira**: resuelve por `Locale.current`, que en Android
sale de `LocaleList.getAdjustedDefault()` — la lista de idiomas **del sistema**, que ni
`Locale.setDefault` ni un `Context` envuelto cambian. Es el mismo muro que en iOS se resolvió
escribiendo `AppleLanguages` (ver `11_SEPARACION_IOS_ANDROID.md` §8bis punto 1); **en Android nadie
lo comprobó**, que es exactamente el hueco que dejó no haber jugado Android.

**Qué tan grave es.** No es crash ni pérdida de datos. Al público mayoritario (teléfono en español)
no le cambia nada: `composeResources/values/` **es español**, así que con el sistema en español ven
español igual. Lo que se rompe es **elegir un idioma distinto al del teléfono**, en las 3 pantallas
compartidas.

**Por dónde atacarlo** (no lo hagas a ciegas, mide primero):

1. El experimento barato: en `LocaleHelper.wrap`, además de lo de ahora, prueba
   `LocaleList.setDefault(LocaleList(locale))` y vuelve a mirar el SF. Si `Locale.current` lo
   sigue, se acabó en tres líneas.
2. Si no: la vía buena de Android es el **idioma por app del sistema** —
   `AppCompatDelegate.setApplicationLocales()` (mete AppCompat, y el proyecto va a propósito **sin
   AppCompat**, solo `ComponentActivity` + Compose) o, API 33+, `LocaleManager.setApplicationLocales()`
   \+ `android:localeConfig` en el manifest. Eso sí lo ve `getAdjustedDefault()` y deja a `R.string`
   y a `composeResources` de acuerdo. Es la solución correcta y la que hay que documentar.
3. Salida de emergencia si hay prisa: envolver `PowApp` en un
   `CompositionLocalProvider(LocalLocaleList / LocalConfiguration)` con el idioma elegido. Compruébalo
   en pantalla, porque Compose Resources ha cambiado de dónde lee entre versiones.

**Decisión del dueño, no tuya:** publicar hoy con esto conocido, o retrasar. Está preguntado.

---

## 3. 📦 Publicar — el CI/CD, revisado hoy

Los workflows están en la **raíz EXTERNA**: `.github/workflows/android-release.yml`
(y `pr-quality-gate.yml`). Revisado entero; no hace falta tocarlo. Cómo funciona:

**Se dispara** con `workflow_dispatch` (pestaña Actions → “Run workflow”) **o** al **cerrar un PR
mergeado contra `main`** que toque `PolitecnicoOpenWorld/**`.

**Cuatro jobs, en este orden:**

1. **`play-compliance`** — la red que evita repetir los rechazos del 2026-07-22. Comprueba que
   existe `gh-pages` (sin ella las dos URLs dan 404 y Play rechaza), que la política de privacidad
   y la página de borrado de cuenta responden **200** con `gabrielhuav@gmail.com` y sin el correo
   equivocado, y que los dos `whatsnew` existen y no están vacíos. **Hoy todo eso está OK.**
2. **`release`** — APK debug + GitHub Release con tag `debug-latest`.
3. **`playstore-closed-testing`** — `needs: [play-compliance]`. Verifica el keystore ANTES de
   compilar, calcula `versionCode = 1000 + GITHUB_RUN_NUMBER` (**no lo toques a mano**), compila
   `bundleRelease` firmado, **valida el tamaño** y sube al track **`alpha`** (prueba cerrada) con
   `status: completed`.
4. **`bump-version`** — solo en merge real: sube el patch en `build.gradle.kts` y en los dos
   `whatsnew`, y lo commitea a `main`. **Por eso no vuelvas a subir el `versionName` a mano si
   publicas por merge: ya lo subí a 1.0.0.15 para ESTE release y el bot dejará `main` en .16.**

**El tamaño:** avisa a 450 MB, **falla a 500 MB**. Última medida del propio CI: **416 MB**
(STREETFIGHTER 112 + SPRITES 106 = 52 % del peso). El resumen del run publica el desglose por
carpeta; míralo, para eso está.

### Secrets que tienen que existir

`MAPS_API_KEY` · `RELEASE_KEYSTORE_BASE64` · `RELEASE_STORE_PASSWORD` · `RELEASE_KEY_ALIAS` ·
`RELEASE_KEY_PASSWORD` · `PLAY_SERVICE_ACCOUNT_JSON` · opcionales `GOOGLE_SERVICES_JSON`
(sin él no hay Firebase → sin multijugador) y `RELEASE_PAT` (si `main` está protegida, sin él
`bump-version` muere con 403).

### Dos avisos que valen dinero

- ⚠️ Sube a la pista **`alpha`** (prueba cerrada), **no a producción**. Pasar a producción es a mano
  en Play Console. Que nadie diga “ya está publicado” al ver el job en verde.
- ⚠️ La etiqueta `manual-play-upload` en el PR **salta la subida** y deja solo el AAB como
  artefacto. Útil si quieres mirar el AAB antes de que salga.
- ⚠️ El texto del GitHub Release está **escrito a mano en el YAML** (líneas 156-166) y todavía habla
  del lanzamiento de Huelum vs. Goya. No rompe nada, pero si quieres que diga la verdad, ahí se
  cambia.

### El orden que yo seguiría

1. Cerrar lo del idioma (arreglarlo o que el dueño lo acepte por escrito).
2. `git pull` y **PR de `perf-gama-baja-coleccionables` → `main`**, y mergear: dispara todo el
   flujo y deja el `bump-version` hecho. (Con `workflow_dispatch` desde la rama también sale el
   AAB, pero no se auto-incrementa la versión y te tocará acordarte la próxima vez.)
3. Mirar el resumen del run: **tamaño** y la puerta de cumplimiento.
4. Play Console → promover de `alpha` a donde toque, y **repasar el formulario de Seguridad de los
   datos** (`PLAYSTORE_formulario_seguridad_datos.md` §6). Eso NO lo automatiza el CI y es el
   tercer motivo de rechazo de julio.

---

## 4. 🗜️ Los PNG → WebP (lo del tamaño)

**MEDIDO HOY**, no estimado:

```
142 PNG en app/src/main/assets = 87,3 MB
  SPRITES        50,4 MB
  BUILDINGS      18,2 MB
  TRANSIT        12,5 MB
  STREETFIGHTER   4,3 MB
  CAMPAIGN        1,8 MB
```

Los más gordos: `BUILDINGS/UAM_Azc/UAM_Azc_D.png` y `_Iz.png` (4,4 MB cada uno),
`TRANSIT/METROBUS/mapa.png` (4,3), `TRANSIT/METRO/inside.png` (3,6). El proyecto **ya usa WebP en
1789 archivos**; estos 142 son los que se quedaron fuera. A `-q 90` el ahorro esperado ronda los
**60 MB de AAB**.

El script es `tools/optimizar_assets_produccion.sh` (`--dry-run` primero, siempre).

### ⚠️ Tres cosas antes de correrlo

1. **`cwebp` NO está instalado en esta PC** — y el script lo exige y se salta el paso entero sin
   hacer nada si falta. `ffmpeg` **sí** está (`C:\ProgramData\chocolatey\bin`, con `libwebp`), y
   Pillow también sabe escribir WebP. Lo más limpio:
   ```bash
   choco install webp
   ```
   (El doc `13_ASSETS_Y_TAMANO.md` dice que en el Mac no había “ni ffmpeg ni cwebp” y que por eso
   tocaba aquí; **aquí falta cwebp igual**. Corrígelo en el doc cuando lo instales.)
2. 🔴 **El script BORRA el `.png` y deja el `.webp`, pero NO toca el código.** Hay **81 referencias
   a `.png` en 26 archivos** de Kotlin/JS/HTML. Si conviertes y no las cambias, esos assets
   **desaparecen en runtime** y **ningún test lo caza** — `PowAssets` no distingue “no existe” de
   “no se copió”. Ojo también con las que NO hay que tocar: hay `.png` que son **URLs de teselas de
   OpenStreetMap** (`RoomTileModuleProvider.kt`, `TilePrefetchManager.kt`), no assets.
3. **Esto NO va en el release de hoy.** Es un cambio de 87 MB de assets sin red de tests, y el
   release ya está listo. Hazlo después, en su propio commit, y **juega los interiores** (metro,
   metrobús, ShineCTO, minijuego zombi) porque ahí están `mapa.png` e `inside.png`.

---

## 5. Lo que queda pendiente de verificar (yo no llegué)

Lo digo claro para que no lo des por hecho:

- 🟠 **El mundo abierto media hora seguida.** Es el §2 del prompt anterior y **no lo hice**. Los 3
  gestores de IA (`PoliceManager`, `CampaignEscortPolice`, `PrankedyManager`) cambiaron
  `ConcurrentHashMap` por `PowMapaConcurrente`; hay 14 tests, pero la concurrencia real solo se ve
  jugando: sube el nivel de búsqueda, deja que te persigan varias patrullas, súbete a una, bájate,
  aléjate. Busca **tirones, policías clavados y patrullas que desaparecen**.
- 🟠 **Los interiores.** Se quitó `onClaimCollectiblePressed` de 5 llamadas. Comprobar que el botón
  de interactuar sigue vivo en metro, metrobús, ShineCTO y el minijuego zombi.
- 🟠 **El audio, oyéndolo.** 82 `.ogg` → `.m4a` y la BGM de 24 a 16 bits. Yo no tengo oídos: el
  emulador no me devuelve sonido. **Hay que escuchar voces, golpes y el bucle de la música.**
  El arreglo de la música (§1.1) también hay que **oírlo**, aunque el mecanismo esté probado.
- 🟢 Coleccionables con su arte en las dos pestañas: no los abrí.

---

## 6. Al cerrar

Actualiza `_SESION_ACTUAL.md` (techo 200 líneas, purga a `_ARCHIVO/` lo que pase de 2 días) con qué
probaste, qué falló y qué versión se publicó. **`git pull` justo antes de cada push.**
