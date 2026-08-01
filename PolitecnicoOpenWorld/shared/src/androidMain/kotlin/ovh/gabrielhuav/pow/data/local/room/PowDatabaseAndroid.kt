package ovh.gabrielhuav.pow.data.local.room

import android.content.Context

/**
 * Singleton de la BD en Android — conserva la API `PowDatabase.getInstance(context)` que ya usaban
 * los 7 call-sites de `:app`, para que la migración a KMP (Fase 4) no obligara a tocarlos.
 *
 * Es una extensión del companion, no un miembro, porque `PowDatabase` vive ahora en `commonMain`
 * y `Context` no existe allí.
 */
@Volatile
private var INSTANCIA: PowDatabase? = null

fun PowDatabase.Companion.getInstance(context: Context): PowDatabase =
    INSTANCIA ?: synchronized(this) {
        INSTANCIA ?: crearPowDatabase(context.applicationContext).also { INSTANCIA = it }
    }
