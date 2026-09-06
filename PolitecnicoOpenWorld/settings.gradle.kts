pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PolitecnicoOpenWorld"
include(":app")
// 🍏 Módulo KMP compartido (Fase 1 de "README for IAS/PLAN_MIGRACION_KMP.md").
// Hoy solo contiene el dominio PURO de "Titulación por Combate"; `:app` lo consume.
include(":shared")
 