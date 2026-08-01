package ovh.gabrielhuav.pow.i18n

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * i18n del juego sin AppCompat (solo ComponentActivity + Compose).
 *
 * Aplica el idioma elegido por el usuario envolviendo el Context base de la Activity
 * en [MainActivity.attachBaseContext]; así Android resuelve los `R.string.*` contra
 * `res/values-<idioma>/strings.xml` (base = español en `res/values/`).
 *
 * Idiomas soportados hoy: español (base) e inglés (`values-en/`). Para añadir otro
 * (p. ej. ruso) basta con crear `res/values-ru/strings.xml` y sumar su etiqueta a la
 * lista del selector de Ajustes; este helper NO necesita cambios.
 */
object LocaleHelper {

    /** Etiquetas BCP-47 ofrecidas en el selector. "" = seguir el idioma del sistema. */
    val SUPPORTED = listOf("" to "Sistema", "es" to "Español", "en" to "English")

    /** Envuelve [base] con el [languageTag] elegido. "" o inválido → sin cambios (sistema). */
    fun wrap(base: Context, languageTag: String): Context {
        if (languageTag.isBlank()) return base
        val locale = Locale.forLanguageTag(languageTag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    /**
     * Sincroniza `APP_LANGUAGE` con el idioma por aplicacion que Android 13+ expone al sistema.
     * Compose Multiplatform consulta la lista ajustada de locales, no solo el Context envuelto;
     * por eso este paso hace que `composeResources` (Ajustes, Coleccionables y SF) coincida con
     * los `R.string` de Android. En API 24-32 [wrap] sigue siendo la compatibilidad disponible.
     *
     * @return `true` cuando Android administra el cambio y recrea la Activity por configuracion.
     */
    fun applyApplicationLocale(context: Context, languageTag: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val locales = if (languageTag.isBlank()) {
            LocaleList.getEmptyLocaleList()
        } else {
            LocaleList.forLanguageTags(languageTag)
        }
        context.getSystemService(LocaleManager::class.java).applicationLocales = locales
        return true
    }
}
