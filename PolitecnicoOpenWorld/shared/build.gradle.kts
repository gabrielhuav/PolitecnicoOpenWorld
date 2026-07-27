import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * 🍏 MÓDULO COMPARTIDO KMP — Fases 1 a 4 de "README for IAS/PLAN_MIGRACION_KMP.md".
 *
 * QUÉ VIVE AQUÍ: el dominio PURO de "Huelum vs. Goya", el punto lat/lon (`GeoPoint`), el JSON
 * (`PowJson`/`jsonOf`) y, desde la Fase 4, la BASE DE DATOS (Room), los ajustes
 * (multiplatform-settings) y el cliente HTTP/WebSocket (Ktor).
 *
 * ⚠️ REGLA: en `commonMain` NO entra NADA de Android (ni `android.*`, ni `androidx.*` que no sea
 * multiplataforma, ni osmdroid). Si algo necesita plataforma, va por `expect/actual`.
 *
 * ⚠️ NO se sube la versión de Kotlin (decisión del dueño, 2026-07-27): el módulo usa el MISMO
 * Kotlin 2.2.10 y AGP que el resto del proyecto.
 */
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    // 🍏 Fase 4: Room en un módulo KMP necesita su plugin + KSP.
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    androidTarget {
        compilerOptions {
            // Mismo jvmTarget que `:app` (11). Si divergen, el consumo desde app falla.
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    // Los 3 targets de iOS. OJO: Kotlin/Native NO compila iOS en Windows — en este PC solo se
    // CONFIGURAN (la compilación real de iOS exige macOS). Se declaran desde ya porque el dueño
    // sí tiene Mac, y así el módulo está listo cuando se trabaje allí.
    iosArm64()          // dispositivo real
    iosSimulatorArm64() // simulador en Mac con Apple Silicon
    iosX64()            // simulador en Mac Intel

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

android {
    namespace = "ovh.gabrielhuav.pow.shared"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
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
