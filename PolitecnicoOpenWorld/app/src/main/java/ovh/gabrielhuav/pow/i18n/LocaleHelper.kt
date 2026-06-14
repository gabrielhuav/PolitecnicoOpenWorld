package ovh.gabrielhuav.pow.i18n

import android.content.Context
import android.content.res.Configuration
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
}
