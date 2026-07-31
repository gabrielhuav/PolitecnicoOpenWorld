package ovh.gabrielhuav.pow.features.settings

import platform.Foundation.NSUserDefaults

/**
 * 🍏 Aplica el idioma elegido en Ajustes al resto de la app en iOS.
 *
 * ## Por qué hace falta esto
 *
 * En Android el idioma se aplica con `activity.recreate()`: al recrearse, el sistema vuelve a
 * resolver los recursos con el locale nuevo. **En iOS no existe ese `recreate()`**, y Compose
 * Resources 1.11.1 **no expone ninguna API para forzar el locale** (se comprobó en la klib: solo
 * hay `filterByLocale` y compañía, todo interno). Resuelve siempre desde el sistema.
 *
 * La vía que sí entiende iOS es la clave estándar **`AppleLanguages`** de `NSUserDefaults`: es el
 * mecanismo con el que una app fija su idioma por encima del que tenga el teléfono.
 *
 * ⚠️ **Se guardan DOS claves y no es redundante.** `APP_LANGUAGE` es la del juego (la que lee
 * Ajustes para pintar el desplegable y la que comparte con Android); `AppleLanguages` es la que
 * mira el sistema para elegir el `values-en` frente al `values`. Si solo se guardara la primera,
 * el desplegable diría "English" y los textos seguirían en español — que es exactamente el fallo
 * que se vio en el simulador el 2026-07-30.
 *
 * @param tag `"es"`, `"en"`, o **vacío para volver al idioma del sistema** (se borra la clave).
 */
fun aplicarIdiomaIos(tag: String) {
    val defaults = NSUserDefaults.standardUserDefaults
    if (tag.isBlank()) {
        // "Sistema (predeterminado)": se quita el override y manda el idioma del teléfono.
        defaults.removeObjectForKey(CLAVE_APPLE_LANGUAGES)
    } else {
        defaults.setObject(listOf(tag), CLAVE_APPLE_LANGUAGES)
    }
    defaults.synchronize()
}

/** La clave que iOS reserva para el idioma preferido de la app. El nombre lo fija Apple. */
private const val CLAVE_APPLE_LANGUAGES = "AppleLanguages"
