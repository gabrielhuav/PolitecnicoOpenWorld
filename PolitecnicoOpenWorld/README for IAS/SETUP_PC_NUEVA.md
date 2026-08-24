# 🖥️ Poner POW a compilar en una PC Windows nueva

**Creado:** 2026-07-28 (al mudarnos de la laptop al escritorio) · Medido en la laptop de referencia.

> Para qué sirve: que una PC que **nunca ha compilado este repo** llegue a `BUILD SUCCESSFUL` sin
> ir descubriendo los bloqueos de uno en uno. Los cuatro archivos del §2 **no viajan por git**, y el
> primero de ellos hace que `gradlew` ni arranque.

---

## 0. ⚠️ La ruta del escritorio lleva un ESPACIO

```
C:\Users\gabri\Documents\GitHub Desktop\PolitecnicoOpenWorld\PolitecnicoOpenWorld
```

El `GitHub Desktop` de en medio rompe cualquier comando sin comillas:

```bash
cd "C:\Users\gabri\Documents\GitHub Desktop\PolitecnicoOpenWorld\PolitecnicoOpenWorld"
```

⚠️ **NO está medido si Kotlin/Native aguanta bien el espacio** (en la laptop nunca hizo falta). Si
`compileKotlinIosSimulatorArm64` falla con algo raro de rutas, la salida sensata es **clonar en una
ruta sin espacios** (p. ej. `C:\pow\PolitecnicoOpenWorld`) en vez de pelearse con el escapado.

---

## 1. Lo que hay que tener instalado

| Cosa | Versión | Cómo se comprueba |
|---|---|---|
| **JDK 21** | 21.x | `java -version` → `openjdk version "21..."` |
| **Android Studio** | reciente | trae el JBR y el SDK |
| **Android SDK Platform 36** | API 36 | `compileSdk = 36` y `targetSdk = 36` |
| **Git Bash** | el de Git para Windows | hace falta para `tools/*.sh` |
| **Un AVD** | llamado `Nexus` | `emulator -list-avds` |

Gradle **no** hay que instalarlo: lo baja el wrapper (9.5.0). Pero ver §2.1.

⚠️ **JAVA_HOME**: si `java -version` no da 21, usa el JBR de Android Studio:
`C:\Program Files\Android\Android Studio\jbr`

---

## 2. Los cuatro archivos que NO vienen por git

Están en `.gitignore` **a propósito** (claves y binarios). Hay que crearlos a mano.

### 2.1 ⚠️ `gradle/wrapper/gradle-wrapper.jar` — ESTE BLOQUEA TODO

Es el único que impide **cualquier** comando. Sin él:

```
Error: Could not find or load main class org.gradle.wrapper.GradleWrapperMain
```

**La forma más segura es COPIARLO de la otra PC** (o del Mac), a
`PolitecnicoOpenWorld/gradle/wrapper/gradle-wrapper.jar`.

Si no hay de dónde copiar, se regenera con un Gradle ya instalado:

```bash
gradle wrapper --gradle-version 9.5.0
```

⚠️ **Ese comando REESCRIBE `gradlew`, `gradlew.bat` y `gradle-wrapper.properties`**, que sí están
versionados y afinados. Después de regenerar: **revierte esos tres y quédate solo con el `.jar`**.

```bash
git checkout -- gradlew gradlew.bat gradle/wrapper/gradle-wrapper.properties
```

### 2.2 `secrets.properties` (en `PolitecnicoOpenWorld/`)

Clave de Google Maps. Sin una clave real, usa el marcador no secreto que ya trae
`local.defaults.properties`:

```
MAPS_API_KEY=DEFAULT_API_KEY
```

⚠️ **MEDIDO en el escritorio (07-28):** dejarla vacía genera
`public static final String MAPS_API_KEY = ;` y rompe `compileDebugJavaWithJavac`.

### 2.3 `google-services.json` — OPCIONAL

Sin él, el plugin `google-services` **no se aplica** y el juego compila y corre en modo local (sin
Firebase). CI lo omite a propósito. Solo hace falta para probar el login de Google.

### 2.4 `local.properties`

Lo genera Android Studio al abrir el proyecto (apunta al SDK). Si compilas solo por consola:

```
sdk.dir=C\:\\Users\\<usuario>\\AppData\\Local\\Android\\Sdk
```

---

## 3. La prueba de humo (en este orden)

Desde `<repo>/PolitecnicoOpenWorld`:

```bash
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :shared:testAndroidHostTest
```

Esperado hoy: **319 tests, 0 fallos** (125 `:app` + 194 `:shared`).

Luego el type-check de iOS, que **también corre en Windows** (se descubrió el 07-28):

```bash
.\gradlew.bat :shared:compileKotlinIosSimulatorArm64
```

⚠️ La primera vez baja el Kotlin/Native de ~2 GB a `~/.konan`. Tarda; no es que se haya colgado.

⚠️ **NO uses `linkDebugFrameworkIosSimulatorArm64` como prueba de nada**: fuera de un Mac dice
`BUILD SUCCESSFUL` y **no produce ningún archivo**. Solo sirve en el Mac.

Y la guarda de nombres de test (Git Bash):

```bash
bash tools/check_kmp_test_names.sh
```

---

## 4. detekt — SÍ viaja por git

`detekt-cli-1.23.8/` está versionado (jar incluido), así que no hay que descargar nada. La
invocación es **exactamente la de CI**, desde la **raíz del repo** (no desde `PolitecnicoOpenWorld/`):

```bash
./detekt-cli-1.23.8/bin/detekt-cli --config PolitecnicoOpenWorld/config/detekt/detekt.yml --build-upon-default-config --input PolitecnicoOpenWorld/app/src/main/java,PolitecnicoOpenWorld/shared/src/commonMain/kotlin --baseline PolitecnicoOpenWorld/config/detekt/baseline.xml
```

Debe salir **exit 0**.

---

## 5. Probar en el emulador

Varias cosas de POW **los tests no las ven**: cómo se pintan los sprites y si el audio suena. Si
tocas eso, hay que abrir el emulador.

```bash
emulator -avd Nexus -no-snapshot-load
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n ovh.gabrielhuav.pow/.MainActivity
```

Truco útil cuando no puedes oír el audio: mirar el servidor de audio del sistema. Con la pelea en
marcha, los reproductores activos suben al golpear y **vuelven a bajar** al terminar el clip.

```bash
adb shell dumpsys audio
```

---

## 6. Reglas del repo que no se negocian

- **`main` está protegida.** Rama + PR, siempre (`git switch -c <rama>`, `git push -u origin <rama>`).
- **Android no se rompe.** Es lo que juega la gente hoy.
- **MEDIR, no suponer.** Si escribes un número, que salga de un comando que ejecutaste. Este repo ya
  tuvo docs que mentían ("0 smells", "47 tests").
- **Comentarios en español**, explicando el PORQUÉ, no el qué.
- **El `.jks` y sus contraseñas NUNCA se comitean** (van por variables de entorno / GitHub Secrets).
- **`_SESION_ACTUAL.md` tiene techo de 200 líneas** y se actualiza ANTES de quedarse sin contexto.
