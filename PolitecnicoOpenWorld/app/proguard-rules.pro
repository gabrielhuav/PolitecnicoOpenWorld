# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ─────────────────────────────────────────────────────────────────────────────
# R8 ACTIVADO (isMinifyEnabled + isShrinkResources, 1.0.0.16) — recomendacion de
# Play Console "Mejora la memoria y el rendimiento de tu app con la optimizacion de R8".
#
# Estas reglas son las MINIMAS que hacen falta: cada una tapa una referencia que NO
# existe en el bytecode, asi que R8 no puede verla y borraria la clase. El resto de
# librerias (kotlinx.serialization, Room, Hilt, Firebase, play-services) ya traen sus
# propias reglas de consumidor dentro del artefacto y NO hay que repetirlas aqui.
#
# ⚠️ Si agregas una regla nueva, escribe QUE se rompe sin ella. Un `-keep` de mas
# devuelve al AAB el peso que R8 acaba de quitar.
# ─────────────────────────────────────────────────────────────────────────────

# ── Ktor: el motor HTTP se elige por ServiceLoader, no por una llamada ───────
# `WebSocketManager` y `SfMatchClient` construyen `HttpClient { }` SIN motor explicito
# (es codigo commonMain de :shared, compartido con iOS). En Android eso resuelve el motor
# leyendo META-INF/services/io.ktor.client.HttpClientEngineContainer, una referencia que
# el bytecode no contiene: R8 borra el contenedor de OkHttp y TODO el multijugador
# (mundo abierto, interiores y peleas 1v1) muere al conectar con
# "Failed to find HTTP client engine implementation".
-keep class io.ktor.client.HttpClientEngineContainer
-keep class * implements io.ktor.client.HttpClientEngineContainer { *; }