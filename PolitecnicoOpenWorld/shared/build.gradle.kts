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
}

kotlin {
    // El target de Android ya NO se declara con `androidTarget()` + un bloque `android { }`
    // aparte: el plugin de KMP de AGP lo configura todo aquí dentro.
    android {
        namespace = "ovh.gabrielhuav.pow.shared"
        compileSdk = 36
        minSdk = 24

        // Los tests de `commonTest` que corren en la JVM del host (los 49 de siempre).
        withHostTestBuilder {}.configure {}

        compilerOptions {
            // Mismo jvmTarget que `:app` (11). Si divergen, el consumo desde app falla.
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    // Los 3 targets de iOS. En Windows solo se CONFIGURAN (Kotlin/Native no compila iOS allí);
    // en el Mac compilan de verdad — verificado el 2026-07-27, con los 49 tests pasando.
    // 🍏 Fase 1.5: además del .klib, cada target produce un FRAMEWORK que Xcode pueda importar.
    // Sin este bloque `binaries.framework` no hay nada que enlazar desde la app iOS.
    listOf(
        iosArm64(),          // dispositivo real
        iosSimulatorArm64(), // simulador en Mac con Apple Silicon
        iosX64(),            // simulador en Mac Intel
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
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
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

// ⚠️ ESTE BLOQUE VA AL FINAL, DESPUÉS de `kotlin { }`, y no es cosmético: las configuraciones
// `kspAndroid`/`kspIosArm64`/… las CREA el plugin al declarar cada target. Si este bloque va
// arriba, el build falla con "Configuration with name 'kspAndroid' not found".
dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
    add("kspIosX64", libs.androidx.room.compiler)
}
