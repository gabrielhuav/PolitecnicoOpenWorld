import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * 🍏 MÓDULO COMPARTIDO KMP — Fase 1 de "README for IAS/PLAN_MIGRACION_KMP.md".
 *
 * POR QUÉ EXISTE: es el andamiaje mínimo para demostrar que KMP funciona en este proyecto SIN
 * tocar nada delicado. Hoy solo contiene el dominio PURO de "Huelum vs. Goya" (sin una sola
 * referencia a Android) y sus tests. `:app` lo consume como dependencia normal.
 *
 * ⚠️ REGLA: en `commonMain` NO entra NADA de Android (ni `android.*`, ni `androidx.*`, ni Gson,
 * ni osmdroid). Si algo necesita plataforma, va por `expect/actual`, no aquí.
 *
 * ⚠️ NO se sube la versión de Kotlin en esta fase (decisión del dueño, 2026-07-27): el módulo usa
 * el MISMO Kotlin 2.2.10 y AGP que el resto del proyecto. KMP no necesita Kotlin 2.4 para esto;
 * subirlo tocaría AGP/KSP/Hilt/Compose justo después de publicar la 1.0.0.14.
 */
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
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
