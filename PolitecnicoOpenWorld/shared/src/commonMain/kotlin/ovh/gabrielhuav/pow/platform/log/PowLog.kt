package ovh.gabrielhuav.pow.platform.log

/**
 * Traza de depuración para las dos plataformas: `android.util.Log` no existe en Kotlin/Native.
 *
 * `expect`/`actual` en vez de un `println` común porque en Android el sitio donde se buscan estas
 * líneas es **logcat filtrando por etiqueta**, y un `println` sin etiqueta se pierde entre el ruido
 * del sistema. En iOS `println` sí sale en la consola de Xcode, que es donde se mira.
 *
 * ⚠️ Es para DEPURAR, no para el camino caliente. Las llamadas que hay hoy están en el spawn de
 * estacionamientos (`NpcAiManager`), que corre cada ~900 ms, no por frame. **No lo metas en el
 * game loop**: formatear la cadena cuesta aunque el log luego se descarte.
 */
expect fun powLog(etiqueta: String, mensaje: String)
