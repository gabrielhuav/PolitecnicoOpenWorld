package ovh.gabrielhuav.pow.domain.platform

/**
 * 🍏 QUÉ MODOS OFRECE CADA PLATAFORMA.
 *
 * POR QUÉ EXISTE: iOS ofrece **AJUSTES, COLECCIONABLES y el modo pelea**, y nada más. El mundo
 * abierto, la campaña y el multijugador dependen de cosas que hoy NO existen allí (osmdroid/WebView
 * con datos, Bluetooth RFCOMM, WebRTC de Android). En vez de esparcir `if (esAndroid)` por la UI,
 * el catálogo de modos vive aquí, en un solo sitio, y la pantalla solo pregunta.
 *
 * ⚠️ Nota de diseño: lo ÚNICO que se resuelve por `expect/actual` es **en qué plataforma estoy**.
 * La decisión de qué modos hay es LÓGICA PURA ([modosDe]), así que se puede testear el
 * comportamiento de iOS **desde Android**, sin un Mac. Si esto fuese un `expect` entero, la regla
 * de iOS solo se podría comprobar compilando para iOS.
 */
enum class PowModo {
    /** Mundo abierto sin campaña. Necesita mapa con datos, NPCs y red. */
    MUNDO_LIBRE,

    /** Campaña con prólogo, escuelas y partidas guardadas. Vive sobre el mundo abierto. */
    MODO_HISTORIA,

    /** Multijugador del mundo abierto. */
    MULTIJUGADOR,

    /** Ajustes. Solo lee/escribe preferencias: multiplataforma desde la Fase 4. */
    AJUSTES,

    /** Coleccionables. Solo lee la base de datos: multiplataforma desde la Fase 4. */
    COLECCIONABLES,

    /** "Huelum vs. Goya", el modo pelea. El PRIMERO que llega a iOS. */
    HUELUM_VS_GOYA,
}

/** Plataformas en las que corre POW. */
enum class PowPlataforma { ANDROID, IOS }

/**
 * Los modos que se ofrecen en [plataforma].
 *
 * ⚠️ En iOS es una lista CORTA A PROPÓSITO, no una limitación temporal disfrazada: cada modo que
 * falta necesita trabajo real que aún no está hecho (ver `PLAN_MIGRACION_KMP.md`). Antes de añadir
 * uno aquí, comprueba que de verdad funciona en el simulador — este `Set` no habilita nada por sí
 * solo, solo deja de esconder el botón.
 */
fun modosDe(plataforma: PowPlataforma): Set<PowModo> = when (plataforma) {
    PowPlataforma.ANDROID -> PowModo.entries.toSet()
    PowPlataforma.IOS -> setOf(
        PowModo.AJUSTES,
        PowModo.COLECCIONABLES,
        PowModo.HUELUM_VS_GOYA,
    )
}

/** En qué plataforma se está ejecutando. Es lo ÚNICO que cambia por plataforma. */
expect val plataformaActual: PowPlataforma

/** Atajo: ¿está disponible este modo AQUÍ? */
fun PowModo.disponible(): Boolean = this in modosDe(plataformaActual)
