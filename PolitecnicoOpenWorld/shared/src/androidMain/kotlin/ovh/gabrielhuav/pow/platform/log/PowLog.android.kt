package ovh.gabrielhuav.pow.platform.log

import android.util.Log

/** En Android va a logcat con su etiqueta, que es como se ha buscado siempre (`POW_DBG`). */
actual fun powLog(etiqueta: String, mensaje: String) {
    Log.d(etiqueta, mensaje)
}
