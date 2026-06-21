plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
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
        versionCode = 9
        versionName = "1.0.0.9"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Servidor del mundo abierto (open world)
            buildConfigField("String", "MULTIPLAYER_SERVER_URL", "\"wss://politecnicoopenworld.onrender.com\"")
            // Servidor del minijuego de INTERIORES (lobby + edificios ESCOM; instancia separada en Render)
            buildConfigField("String", "INTERIORS_SERVER_URL", "\"wss://politecnicoopenworld-1.onrender.com\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "MULTIPLAYER_SERVER_URL", "\"wss://politecnicoopenworld.onrender.com\"")
            buildConfigField("String", "INTERIORS_SERVER_URL", "\"wss://politecnicoopenworld-1.onrender.com\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
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
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.compose.foundation)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.preference.ktx)

    // Dependencias para Multijugador
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

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