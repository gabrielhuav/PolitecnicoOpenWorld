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

    /**
     * "Titulación por Combate", el modo pelea. El PRIMERO que llega a iOS.
     *
     * ⚠️ El nombre de CARA AL JUGADOR vive en `menu_street_fighter` (strings), no aquí: ya cambió
     * una vez ("Huelum vs. Goya" → "Titulación por Combate") y esta constante se llama como el
     * código (`street_fighter`/`Sf*`) justamente para no tener que renombrarla en la siguiente.
     */
    STREET_FIGHTER,
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
        PowModo.STREET_FIGHTER,
    )
}

/** En qué plataforma se está ejecutando. Es lo ÚNICO que cambia por plataforma. */
expect val plataformaActual: PowPlataforma

/** Atajo: ¿está disponible este modo AQUÍ? O sea: ¿se puede JUGAR? */
fun PowModo.disponible(): Boolean = this in modosDe(plataformaActual)

// ─────────────────────────────────────────────────────────────────────────────────────────────
// 🚧 MODOS EN OBRAS — la vitrina de iOS
// ─────────────────────────────────────────────────────────────────────────────────────────────

/**
 * # ⚠️⚠️ EL INTERRUPTOR DE LA APP STORE ⚠️⚠️
 *
 * **Ponlo en `false` ANTES de firmar y subir la build de iOS. Es lo único que hay que tocar.**
 *
 * Con `true`, el menú de iOS pinta también MUNDO LIBRE, MODO HISTORIA y MULTIJUGADOR, marcados
 * como **EN OBRAS**: no llevan a ninguna parte, solo abren un aviso. Sirve para comparar los dos
 * menús lado a lado mientras se porta el mundo abierto.
 *
 * **Por qué hay que apagarlo para publicar:** Apple rechaza apps con funciones anunciadas que no
 * funcionan (App Store Review Guideline 2.1 — *"apps that are not yet complete"*). Un botón que
 * abre un cartel de "en obras" es exactamente eso.
 *
 * En Android no cambia NADA: allí los tres modos están de verdad y `disponible()` ya es `true`.
 */
const val MODOS_EN_OBRAS_VISIBLES: Boolean = true

/**
 * Modos que [plataforma] **enseña sin poder jugarlos todavía**.
 *
 * ⚠️ **Esto NO es lo mismo que [modosDe].** Un modo en obras se PINTA pero no se JUGA: la pantalla
 * no navega, avisa. Se mantienen separados a propósito, porque `disponible()` es lo que consulta,
 * por ejemplo, Ajustes para decidir si enseña las opciones de mapa y controles — y esas siguen sin
 * tener sentido en iOS.
 */
fun modosEnObrasDe(plataforma: PowPlataforma): Set<PowModo> = when {
    !MODOS_EN_OBRAS_VISIBLES -> emptySet()
    plataforma == PowPlataforma.IOS -> setOf(
        PowModo.MUNDO_LIBRE,
        PowModo.MODO_HISTORIA,
        PowModo.MULTIJUGADOR,
    )
    // En Android no hay nada en obras: lo que se pinta, se juega.
    else -> emptySet()
}

/** ¿Se pinta el botón pero NO se puede jugar? */
fun PowModo.enObras(): Boolean = !disponible() && this in modosEnObrasDe(plataformaActual)

/**
 * ¿Se pinta el botón de este modo en el menú?
 *
 * Es `disponible() || enObras()`. **Úsalo solo para PINTAR.** Para decidir si algo funciona de
 * verdad —navegar, guardar, abrir una sección de Ajustes— la pregunta sigue siendo [disponible].
 */
fun PowModo.sePinta(): Boolean = disponible() || enObras()
