# 🍏 ARRANQUE EN EL MAC — primera compilación iOS de POW

**Creado:** 2026-07-27 · **Rama:** `fase0-auditoria-kmp` · **Contexto:** `PLAN_MIGRACION_KMP.md`

> # ✅ EJECUTADO Y COMPLETADO — 2026-07-27
>
> **Este guion ya se cumplió.** `:shared` compila, enlaza y **pasa sus 49 tests en el simulador**
> (medido: `tests=49 failures=0 errors=0`). Se conserva como referencia histórica; **el estado
> actual y el siguiente paso están en `_SESION_ACTUAL.md` §3ter**, no aquí.
>
> **Lo que este documento predijo MAL** (corregido abajo en cada punto):
> - La firma de `URLForDirectory(...)` estaba **BIEN**; solo faltaba `@OptIn(ExperimentalForeignApi)`.
> - `PowDatabaseConstructor` **no dio ningún problema**: Room lo generó a la primera.
> - Ktor Darwin no es código en `iosMain` (solo una dependencia) → **sigue sin ejercitarse**.
> - Los 4 fallos REALES no estaban en la lista: DAOs de Room que deben ser `suspend`, la **ABI de
>   las klibs de Ktor**, `@Volatile` sin import, y los nombres de test con `(`/`)`/`,`.
>   Todos explicados en `09_CONVENTIONS_GOTCHAS.md` §🍏 KMP/iOS.

> **Para qué era este archivo.** Las Fases 1-4 se hicieron en **Windows**, donde Kotlin/Native
> **no puede compilar para iOS** (Gradle desactiva los targets y lo dice en cada build). Así que
> había código iOS en el repo que **NUNCA se había compilado**.

---

## 0. Rutas y requisitos

| Cosa | Valor en el Mac |
|---|---|
| Repo | `/Users/gabrielhuav/Documents/GitHub/PolitecnicoOpenWorld` |
| Proyecto Gradle (aquí está `gradlew`) | `/Users/gabrielhuav/Documents/GitHub/PolitecnicoOpenWorld/PolitecnicoOpenWorld` |
| JDK | el JBR de Android Studio: `/Applications/Android Studio.app/Contents/jbr/Contents/Home` |
| Rama a usar | `fase0-auditoria-kmp` |

⚠️ **Ojo con la ruta doble**: el proyecto Gradle está en `PolitecnicoOpenWorld/PolitecnicoOpenWorld`,
una carpeta *dentro* del repo. Es el error más fácil de cometer.

---

## 1. Traer la rama

En **GitHub Desktop**: `Fetch origin` → selector de ramas → **`fase0-auditoria-kmp`**.
(O por terminal: `git fetch origin && git switch fase0-auditoria-kmp`.)

---

## 2. Preparar el entorno (una vez por terminal)

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
cd /Users/gabrielhuav/Documents/GitHub/PolitecnicoOpenWorld/PolitecnicoOpenWorld
chmod +x gradlew    # en Windows pierde el bit de ejecutable
```

Comprueba que Xcode está seleccionado (Kotlin/Native lo necesita para enlazar):

```bash
xcode-select -p
```

Si contesta `/Library/Developer/CommandLineTools`, apúntalo a Xcode:

```bash
sudo xcode-select -s /Applications/Xcode.app/Contents/Developer
```

---

## 3. La red de seguridad ANTES de tocar nada: que Android siga verde

Esto ya pasaba en Windows. Si en el Mac no pasa, el problema es del entorno, **no** del código:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :shared:testDebugUnitTest
```

**Esperado: BUILD SUCCESSFUL · `:app` 112 + `:shared` 49 = 161 tests, 0 fallos.**

---

## 4. 🍏 EL HITO: primera compilación de `:shared` para iOS

Este es el comando que **nunca se ha podido ejecutar**:

```bash
./gradlew :shared:compileKotlinIosSimulatorArm64
```

Y después, los 49 tests del módulo corriendo **en iOS**:

```bash
./gradlew :shared:iosSimulatorArm64Test
```

> **Cuenta con que algo falle aquí, y es normal.** Es la primera vez que ese código ve un
> compilador de iOS. Lo que falle es INFORMACIÓN, no un desastre.

### Dónde va a fallar, por probabilidad

1. **`shared/src/iosMain/.../PowDatabase.ios.kt`** — el candidato nº1. Usa
   `NSFileManager.URLForDirectory(...)` para resolver la carpeta *Documents*, y esa firma se
   escribió **sin compilador delante**. Si se queja, la alternativa habitual es
   `NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first()`.
   Lo único que importa es que devuelva una ruta estable y siempre la misma.
2. **`PowDatabaseConstructor`** (el `expect object` de Room) — si Room no genera el `actual`,
   revisa que las 4 líneas `add("kspIosArm64", ...)` de `shared/build.gradle.kts` estén y que el
   bloque `dependencies { }` siga **DESPUÉS** de `kotlin { }` (si sube, falla con
   *"Configuration with name 'kspAndroid' not found"*).
3. **Ktor Darwin** — `iosMain` usa `ktor-client-darwin`. Si se queja de motores, es ahí.

**Cuando el paso 4 esté en verde, la migración deja de ser teoría por primera vez.**

---

## 5. Lo siguiente, ya con el compilador de iOS de tu lado

| | Qué | Por qué ahora sí |
|---|---|---|
| **Fase 1.5** | App iOS mínima + el mapa **Leaflet en `WKWebView`** | Es la suposición MÁS CARA de todo el plan: que iOS hereda el mapa gratis (`WorldMapLeafletHtml.kt`, 929 líneas, cero imports). Media sesión y queda demostrada o desmentida. |
| **Fase 5** | UI compartida (Compose Multiplatform) | Exige subir **Kotlin 2.2.10 → ~2.4.x** (se pospuso en la Fase 1 a propósito). Aquí ya se puede verificar en el simulador. |
| **Fase 6** | App iOS, entrega de assets, firma, TestFlight | ⚠️ **358 MB de assets** contra el límite de **200 MB por datos móviles** → hace falta On-Demand Resources. La cuenta de Apple Developer solo hace falta para TestFlight/App Store. |

### 🆕 Claude Code en el Mac puede compilar y lanzar el simulador

La beta de soporte de simulador iOS (macOS + Xcode) sirve justo para las Fases 5 y 6: permite
construir, lanzar y **verificar** en el simulador sin salir de la sesión.

⚠️ **Pero necesita que EXISTA una app iOS, y hoy no existe.** No hay proyecto Xcode ni target de
app: las Fases 1-4 solo produjeron una **librería** compartida. Por eso el orden es: primero el
paso 4 de este documento, luego la app mínima de la Fase 1.5, y a partir de ahí el simulador ya
tiene algo que lanzar.

---

## 6. Lo que NO hay que hacer

- **No borres los targets iOS** de `shared/build.gradle.kts` si algo falla. El aviso
  *"targets cannot be built on this machine"* es de **Windows**; en el Mac deben compilar.
- **No cambies la ruta del fichero de la BD en Android** (`filesDir/databases/pow_roads.db`):
  los jugadores perderían caché y landmarks. Ver el aviso en `PowDatabase.android.kt`.
- **No "simplifiques"** `jsonOf`/`jsonArrayOf` a `PowJson.encodeToString`: compila y revienta en
  runtime. Hay un test que lo fija (`PayloadsSeSerializanEnRuntimeTest`).
- **No subas Kotlin** hasta llegar de verdad a la Fase 5, y cuando lo hagas, hazlo en su propio
  commit: mueve AGP, KSP, Hilt y Compose de golpe.
