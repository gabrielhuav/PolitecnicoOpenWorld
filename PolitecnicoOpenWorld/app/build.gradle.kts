plugins {
    alias(libs.plugins.android.application)
    // 🍏 Fase 5: `kotlin-android` YA NO se aplica — AGP 9 trae Kotlin integrado
    // (`android.builtInKotlin=true`). Aplicarlo aqui rompe con el DSL nuevo de AGP.
    alias(libs.plugins.kotlin.compose)
    // 🍏 Fase 3: hace falta AQUÍ además de en `:shared` porque hay clases `@Serializable` en
    // ambos módulos (el plugin genera el serializador al compilar cada uno).
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
    // NOTA: el plugin google-services NO se aplica aquí. Se aplica condicionalmente al final
    // de este archivo SOLO si existe app/google-services.json, para que el proyecto compile
    // sin ese archivo (contribuidores / PRs). El classpath del plugin lo declara el build.gradle.kts raíz.
}

android {
    namespace = "ovh.gabrielhuav.pow"
    compileSdk = 36

    defaultConfig {
        applicationId = "ovh.gabrielhuav.pow"
        minSdk = 24
        targetSdk = 36
        versionCode = System.getenv("APP_VERSION_CODE")?.toIntOrNull() ?: 12
        versionName = "1.0.0.17"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Firma de RELEASE para Play Store. Lee keystore + credenciales de VARIABLES DE ENTORNO
    // (en CI = GitHub Secrets); la .jks y las contraseñas NUNCA se commitean. Si las env NO
    // están (build local de un contribuidor), la keystore no se asigna y el release queda SIN
    // firmar (debug y el resto del proyecto compilan igual).
    signingConfigs {
        create("release") {
            val ksPath = System.getenv("RELEASE_KEYSTORE_PATH")
            if (!ksPath.isNullOrEmpty() && file(ksPath).exists()) {
                storeFile = file(ksPath)
                storePassword = System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Servidor del mundo abierto (open world)
            buildConfigField("String", "MULTIPLAYER_SERVER_URL", "\"wss://politecnicoopenworld.onrender.com\"")
            // Servidor del minijuego de INTERIORES (lobby + edificios ESCOM; instancia separada en Render)
            buildConfigField("String", "INTERIORS_SERVER_URL", "\"wss://politecnicoopenworld-1.onrender.com\"")
            // Servidor del modo PELEA 1v1 (MultiplayerSF/; 3a instancia GRATIS en Render —
            // ajusta la URL al nombre real del servicio tras el primer deploy)
            buildConfigField("String", "SF_SERVER_URL", "\"wss://politecnicoopenworld-2.onrender.com\"")
        }
        release {
            // Play Console: habilita R8 para eliminar y optimizar codigo no usado en el AAB.
            isMinifyEnabled = true
            // AGP 9 integra la reduccion optimizada de recursos con el grafo de R8.
            isShrinkResources = true
            // Solo firma si CI proporcionó la keystore (env); local sin env → release sin firmar.
            if (!System.getenv("RELEASE_KEYSTORE_PATH").isNullOrEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "MULTIPLAYER_SERVER_URL", "\"wss://politecnicoopenworld.onrender.com\"")
            buildConfigField("String", "INTERIORS_SERVER_URL", "\"wss://politecnicoopenworld-1.onrender.com\"")
            buildConfigField("String", "SF_SERVER_URL", "\"wss://politecnicoopenworld-2.onrender.com\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    // 🍏 Fase 5: `kotlinOptions` quedo deprecado en Kotlin 2.3 -> DSL de `compilerOptions`.
    // Sigue siendo JVM 11, el MISMO que `:shared`: si divergen, el consumo entre modulos falla.
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // 🍏 Módulo KMP compartido (Fase 1 de "README for IAS/PLAN_MIGRACION_KMP.md"). Contiene el
    // dominio PURO de "Titulación por Combate" (SfStateMachine/SfDamage/SfPhysics/SfAnimation/SfModels…).
    // Mantiene el MISMO paquete que tenía en `:app` a propósito → cero imports que cambiar aquí.
    implementation(project(":shared"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.compose.icons.extended)

    // OSMDroid
    implementation("org.osmdroid:osmdroid-android:6.1.18")
    // GPS
    implementation("com.google.android.gms:play-services-location:21.2.0")
    // ViewModel + Navigation
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    //Google Maps SDK
    implementation("com.google.maps.android:maps-compose:4.4.1")

    implementation("androidx.compose.material:material-icons-extended:1.6.0")

    // Room
    // Room: el runtime lo aporta `:shared` (api). room-ktx aporta las extensiones de corrutinas.
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.compose.foundation)
    // 🍏 Fase 4: el compilador de Room ya NO corre aqui — la BD vive en `:shared`.

    // Hilt (DI) — el compilador va por KSP (NO kapt) para no duplicar procesadores.
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.androidx.preference.ktx)

    // 🍏 Fase 4: OkHttp ya NO se usa directamente — el WebSocket va por Ktor (`:shared`), que en
    // Android usa OkHttp como MOTOR (lo arrastra `ktor-client-okhttp`) y en iOS usa Darwin.
    // ⚠️ No vuelvas a declararlo aquí: el codigo que lo importaba ya no existe.
    // 🍏 Gson ya NO va en producción (Fase 3): usa reflexión de la JVM y no existe en iOS. Se
    // queda SOLO en tests, que es donde `GameSaveCompatGsonTest` y `JsonObjectCompatGsonTest`
    // comparan el JSON nuevo contra el que producía Gson (partidas guardadas y formato de cable).
    // ⚠️ NO lo devuelvas a `implementation`: si vuelve, vuelve el bloqueo de iOS.
    testImplementation("com.google.code.gson:gson:2.10.1")
    // Firebase Authentication (Google Sign-In) — la BOM fija versiones compatibles.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.play.services.auth)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

secrets {
    // Optionally specify a different file name containing your secrets.
    // The plugin defaults to "local.properties"
    propertiesFileName = "secrets.properties"

    // A properties file containing default secret values. This file can be
    // checked in version control.
    defaultPropertiesFileName = "local.defaults.properties"

    // Configure which keys should be ignored by the plugin by providing regular expressions.
    // "sdk.dir" is ignored by default.
    ignoreList.add("sdk.*")       // Ignore all keys matching the regexp "sdk.*"
}

// ─── Firebase (opcional para contribuidores) ──────────────────────────────────
// El plugin google-services SOLO se aplica si existe app/google-services.json. Así
// cualquiera puede clonar el repo y compilar/correr el juego SIN el archivo (que está
// en .gitignore). En ese caso, el login con Google / multijugador queda DESHABILITADO
// en tiempo de ejecución (AuthManager degrada sin crashear), pero todo lo demás (un
// jugador, Modo Historia, mapa, interiores) funciona. El maintainer agrega el json para
// habilitar el multijugador.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    logger.lifecycle("google-services.json encontrado → Firebase Auth HABILITADO.")
} else {
    logger.warn("google-services.json NO encontrado → Firebase Auth deshabilitado en este build (el multijugador no funcionará). Es normal en clones/PRs sin el archivo.")
}
