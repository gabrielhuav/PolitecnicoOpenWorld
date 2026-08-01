import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * 🍏 MÓDULO COMPARTIDO KMP — Fases 1 a 5 de "README for IAS/PLAN_MIGRACION_KMP.md".
 *
 * QUÉ VIVE AQUÍ: el dominio PURO de "Huelum vs. Goya", el punto lat/lon (`GeoPoint`), el JSON
 * (`PowJson`/`jsonOf`), la BASE DE DATOS (Room), los ajustes (multiplatform-settings), el cliente
 * WebSocket (Ktor) y el generador del mapa Leaflet.
 *
 * ⚠️ REGLA: en `commonMain` NO entra NADA de Android (ni `android.*`, ni `androidx.*` que no sea
 * multiplataforma, ni osmdroid). Si algo necesita plataforma, va por `expect/actual`.
 */
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    // ⚠️ `com.android.kotlin.multiplatform.library`, NO `com.android.library`: desde AGP 9 esta
    // última es INCOMPATIBLE con el plugin de KMP y el build falla al aplicarla.
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
    // Room en un módulo KMP necesita su plugin + KSP.
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    // 🍏🥊 Fase 5: la UI del modo pelea baja a `commonMain`. Hacen falta LOS DOS plugins —
    // `compose` aporta las dependencias con klibs de iOS y `kotlin.plugin.compose` es el
    // COMPILADOR (va clavado a la versión de Kotlin). Sin el segundo, `@Composable` no compila.
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    // El target de Android ya NO se declara con `androidTarget()` + un bloque `android { }`
    // aparte: el plugin de KMP de AGP lo configura todo aquí dentro.
    android {
        namespace = "ovh.gabrielhuav.pow.shared"
        compileSdk = 36
        minSdk = 24
        // AGP 9 no empaqueta los composeResources de un androidLibrary KMP si no se habilita.
        // Sin esto compila, pero stringResource/painterResource fallan en runtime.
        androidResources.enable = true

        // Los tests de `commonTest` que corren en la JVM del host (los 49 de siempre).
        withHostTestBuilder {}.configure {}

        compilerOptions {
            // Mismo jvmTarget que `:app` (11). Si divergen, el consumo desde app falla.
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    // Los targets de iOS. 🍏 Fase 1.5: además del .klib, cada uno produce un FRAMEWORK que Xcode
    // pueda importar. Sin `binaries.framework` no hay nada que enlazar desde la app iOS.
    //
    // ⚠️ SORPRESA MEDIDA (Windows, 2026-07-28): **Kotlin/Native SÍ compila los klibs de iOS desde
    // Windows** (`compileKotlinIosSimulatorArm64` pasa). Lo que NO funciona aquí es el LINK del
    // framework: esa tarea se salta EN SILENCIO y devuelve BUILD SUCCESSFUL sin producir nada.
    // O sea: el type-check completo de iOS se verifica desde Windows; el binario, solo en el Mac.
    //
    // ⚠️⚠️ **`iosX64` FUERA desde la Fase 5**: Compose Multiplatform 1.11.1 ya NO publica para ese
    // target (medido: `runtime-iosx64` → HTTP 404, mientras arm64 y simulatorArm64 dan 200).
    // Dejarlo declarado rompe la resolución de TODOS los source sets compartidos con
    // "Unresolved platforms: [iosX64]". `iosX64` es solo el simulador de los Mac **Intel**; el Mac
    // del proyecto es Apple Silicon y corre `iosSimulatorArm64`, así que no se pierde nada real.
    // Si algún día hace falta un Mac Intel, hay que bajar Compose o esperar a que lo repongan.
    listOf(
        iosArm64(),          // dispositivo real
        iosSimulatorArm64(), // simulador en Mac con Apple Silicon
    ).forEach { target ->
        target.binaries.framework {
            // `Shared` es el nombre con el que se importa desde Swift: `import Shared`.
            baseName = "Shared"
            // Estático: mete el código dentro del binario de la app y evita tener que firmar y
            // empaquetar un framework dinámico aparte. Para una librería de este tamaño, sobra.
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // `api` y no `implementation`: `:app` usa estos tipos directamente en su código.
            api(libs.kotlinx.serialization.json)
            // Ajustes multiplataforma: en Android envuelve el SharedPreferences EXISTENTE
            // (cero migración de datos); en iOS usa NSUserDefaults.
            api(libs.multiplatform.settings)
            // WebSocket multiplataforma (sustituye a OkHttp).
            api(libs.ktor.client.core)
            api(libs.ktor.client.websockets)
            // Room multiplataforma: entidades, DAOs y la BD viven aquí desde la Fase 4.
            api(libs.androidx.room.runtime)
            api(libs.androidx.sqlite.bundled)

            // 🍏🥊 UI multiplataforma. `api` porque `:app` compone estas pantallas dentro de las
            // suyas y necesita ver los tipos de Compose.
            // ⚠️ En Android estos artefactos se REDIRIGEN a los `androidx.compose.*` de siempre
            // (lo hace el plugin), así que la app de Android sigue con su mismo runtime: no hay
            // dos Composes conviviendo ni cambia el tamaño del APK.
            api(compose.runtime)
            api(compose.foundation)
            api(compose.ui)
            api(compose.material3)
            api(compose.materialIconsExtended)
            // Carga de imágenes/fuentes empaquetadas por el propio Compose.
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            api(compose.components.resources)

            // ⚠️ NO se añade `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel` a propósito.
            // Es la vía "oficial" para un ViewModel en `commonMain`, PERO la única versión con
            // artefactos de iOS es la 2.11.0 (medido: 2.10.0 y 2.9.4 dan 404), y esa arrastra
            // `androidx.lifecycle:*:2.11.0` en Android, que EXIGE `compileSdk 37`:
            //     Dependency 'androidx.lifecycle:lifecycle-viewmodel-compose-android:2.11.0'
            //     requires ... compile against version 37 or later
            // Subir el compileSdk de una app EN PRODUCCIÓN para poder compartir una clase base es
            // un precio absurdo. En su lugar hay `PowViewModel` (expect/actual), que en Android ES
            // un `androidx.lifecycle.ViewModel` de los de siempre — Hilt y el ciclo de vida no se
            // enteran — y en iOS es una clase normal con su propio scope. Cero dependencias nuevas.
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            // WebRTC sigue Android-only: SfWebRtcClient vive ahora junto al VM en androidMain.
            // No pongas abiFilters: el AVD "Nexus" necesita x86_64.
            implementation("io.github.webrtc-sdk:android:144.7559.09")
            // Aporta `androidx.lifecycle.ViewModel` + `viewModelScope` al `actual` de PowViewModel.
            api(libs.androidx.lifecycle.viewmodel.ktx)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            // kotlin.test: las aserciones que SÍ existen en las dos plataformas.
            // (En `:app` los tests siguen siendo JUnit4; aquí no puede serlo — no hay JVM en iOS.)
            implementation(kotlin("test"))
        }
    }
}

// Room exige un directorio de esquemas cuando se usa su plugin.
room { schemaDirectory("$projectDir/schemas") }

/*
 * 🍏 Al ENLAZAR el framework de iOS, generar también los `composeResources` de `Main`.
 *
 * ⚠️ NO es cosmético, y costó una sesión encontrarlo: la tarea de link genera los recursos de TEST
 * pero **no los de Main**. Sin ellos la app COMPILA, ARRANCA y luego **se cierra en cuanto una
 * pantalla llama a `stringResource`** — Compose los carga en una corrutina y la excepción no la
 * recoge nadie, así que no hay ni error ni log: solo el `.ips` en DiagnosticReports.
 *
 * Se engancha aquí, y no en una fase de Xcode que llame a Gradle, porque Xcode no hereda el
 * `JAVA_HOME` del proyecto y el wrapper no arranca desde allí. Así el flujo de siempre
 * ("genera el framework antes de abrir Xcode") ya deja los recursos listos, y la fase de Xcode se
 * limita a COPIARLOS al bundle.
 */
listOf("IosSimulatorArm64", "IosArm64").forEach { destino ->
    listOf("Debug", "Release").forEach { variante ->
        tasks.matching { it.name == "link${variante}Framework$destino" }.configureEach {
            dependsOn("assemble${destino}MainResources")
        }
    }
}

/*
 * 🍏 STRINGS COMPARTIDOS — el reemplazo de `R.string` para la UI que vive en `commonMain`.
 *
 * ⚠️ Esto era EL BLOQUEADOR del paso 4 de `PLAN_SF_EN_iOS.md`: la clase `R` la genera AGP para
 * `:app` y NO existe en `:shared`, asi que ninguna pantalla con textos podia bajar. Los .xml de
 * `commonMain/composeResources/values*` los lee este plugin y genera `Res.string.*`.
 *
 * `publicResClass = true` porque `:app` tambien consume estas pantallas y necesita ver la clase;
 * con el `internal` por defecto no compilaria desde el modulo de la app.
 */
compose.resources {
    publicResClass = true
    packageOfResClass = "ovh.gabrielhuav.pow.shared.recursos"
    generateResClass = auto
}

// ⚠️ ESTE BLOQUE VA AL FINAL, DESPUÉS de `kotlin { }`, y no es cosmético: las configuraciones
// `kspAndroid`/`kspIosArm64`/… las CREA el plugin al declarar cada target. Si este bloque va
// arriba, el build falla con "Configuration with name 'kspAndroid' not found".
dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
    // (ya no hay `kspIosX64`: ese target se retiró — ver la nota junto a los targets)
}
