package ovh.gabrielhuav.pow.features.map_exterior.ui.components

/**
 * Define cada skin disponible para el jugador principal.
 *
 * Convención de assets (todos en assets/SPRITES/PLAYER/):
 *   <skinFolder>Idle/   →  <skinPrefix>i_<frame>.webp   (frames 1–idleFrames)
 *   <skinFolder>Walk/   →  <skinPrefix>w_<frame>.webp   (frames 1–walkFrames)
 *   <skinFolder>Run/    →  <skinPrefix>r_<frame>.webp   (frames 1–runFrames)
 *   <skinFolder>Special/→  <skinPrefix>s_<frame>.webp   (frames 1–specialFrames)
 *
 * Para agregar una skin nueva:
 *   1. Coloca los sprites en assets/SPRITES/PLAYER/<skinFolder>{Idle|Walk|Run|Special}/
 *   2. Agrega una entrada al enum con sus valores correspondientes.
 */
enum class PlayerSkin(
    val displayName: String,
    /** Subcarpeta dentro de SPRITES/PLAYER/, sin barra final */
    val skinFolder: String,
    /** Prefijo del archivo antes de la letra de acción, p.ej. "lazaro_" */
    val skinPrefix: String,
    val idleFrames: Int = 25,
    val walkFrames: Int = 25,
    val runFrames: Int = 25,
    val specialFrames: Int = 25,
    /** Ruta al sprite de previsualización (idle frame 1) */
    val previewAsset: String = "SPRITES/PLAYER/${skinFolder}Idle/${skinPrefix}i_1.webp",
    /**
     * Sufijo del cómic del Modo Historia para los paneles que cambian según la skin
     * (IntroPOW9/10/11/15). "" = panel por defecto (Lázaro/hombre). Ej.: con "Girl" se usa
     * `IntroPOW9Girl.webp`; con "Robot" se usa `IntroPOW9Robot.webp`. Ver StoryIntroScreen.
     */
    val comicSuffix: String = "",
    /**
     * Factor de escala visual del sprite a pie (mapa exterior). Compensa skins cuyo
     * personaje ocupa poca fraccion del lienzo (mucho margen transparente) y por eso
     * se veian MAS PEQUENAS que el resto. 1f = sin cambio.
     */
    val renderScale: Float = 1f,
    /**
     * Fracción vertical opaca (alto del personaje / alto del lienzo) del frame 1 de CAMINAR,
     * PRECALCULADA. La usan el INTERIOR (InteriorPlayerViews) y el selector de personaje
     * (NewGameCharacterDialog) como referencia de tamaño. En el mapa EXTERIOR ahora se usan las
     * fracciones POR ACCIÓN de abajo. Si cambias el arte de caminar, vuelve a medirla.
     */
    val walkBodyFraction: Float = 0.6f,
    /**
     * Fracciones opacas PRECALCULADAS por animación (alto del personaje / alto del lienzo, promedio
     * de la animación). Con ellas el CUERPO mide SIEMPRE lo mismo en pantalla (PLAYER_BODY_STANDARD_DP)
     * sin importar la skin ni la animación. Antes el exterior medía la fracción en runtime (async, con
     * fallback 0.6 y desfase de un frame): Lázaro/Robot —que varían MUCHO entre idle/caminar/correr—
     * se "encogían" al correr. Estas son estáticas y deterministas.
     * ⚠️ Los assets NO son uniformes (lienzos 256² / 338×422 / 362×640 / 542×681…): por eso hay que
     * medir cada animación por separado. Lo ideal sería re-exportar TODOS con un lienzo y una fracción
     * de cuerpo comunes (ver 09 §5). <=0f ⇒ usa walkBodyFraction como respaldo.
     */
    val idleBodyFraction: Float = -1f,
    val runBodyFraction: Float = -1f,
    val specialBodyFraction: Float = -1f
) {
    LAZARO(
        displayName = "Lázaro",
        skinFolder  = "lazaro",
        skinPrefix  = "lazaro_",
        idleFrames   = 6,   // tienes 6
        walkFrames   = 6,   // tienes 6
        specialFrames = 8,  // tienes 8
        comicSuffix = "",   // panel por defecto (hombre)
        walkBodyFraction = 0.61f,
        idleBodyFraction = 0.583f, runBodyFraction = 0.473f, specialBodyFraction = 0.544f
    ),

    escomgirl(
        displayName = "Estudianta",
        skinFolder  = "escomgirl",
        skinPrefix  = "escomgirl_",
        idleFrames   = 6,   // tienes 6
        walkFrames   = 5,   // tienes 5
        runFrames    = 4,   // tienes 4
        specialFrames = 6,  // tienes 6
        comicSuffix = "Girl",
        walkBodyFraction = 0.94f,
        idleBodyFraction = 0.953f, runBodyFraction = 0.932f, specialBodyFraction = 0.909f
    ),
    robot(
        displayName = "Robot Estudiantx",
        skinFolder  = "robot",
        skinPrefix  = "robot_",
        comicSuffix = "Robot",
        walkBodyFraction = 0.62f,
        idleBodyFraction = 0.710f, runBodyFraction = 0.679f, specialBodyFraction = 0.650f
    ),
    escomboy(
        displayName = "Estudiante",
        skinFolder  = "escomboy",
        skinPrefix  = "escomboy_",
        idleFrames   = 16,  // tienes 16
        walkFrames   = 25,  // tienes 25
        runFrames    = 16,  // tienes 16
        specialFrames = 16, // tienes 16
        comicSuffix = "Boy", // sin assets IntroPOW*Boy → cae al panel por defecto (hombre)
        renderScale = 1.8f,  // (legado) ya NO se usa para el cuerpo en exterior; ahora normaliza el estándar
        walkBodyFraction = 0.41f,
        idleBodyFraction = 0.406f, runBodyFraction = 0.407f, specialBodyFraction = 0.412f
    ),
    // ── Agrega aquí nuevas skins ──────────────────────────────────────────
    // Ejemplo con una skin "Ana":
    //
    // ANA(
    //     displayName = "Ana",
    //     skinFolder  = "ana",
    //     skinPrefix  = "ana_"
    // ),
    // ─────────────────────────────────────────────────────────────────────
    ;

    fun idlePath(frame: Int)   = "SPRITES/PLAYER/${skinFolder}Idle/${skinPrefix}i_$frame.webp"
    fun walkPath(frame: Int)   = "SPRITES/PLAYER/${skinFolder}Walk/${skinPrefix}w_$frame.webp"
    fun runPath(frame: Int)    = "SPRITES/PLAYER/${skinFolder}Run/${skinPrefix}r_$frame.webp"
    fun specialPath(frame: Int)= "SPRITES/PLAYER/${skinFolder}Special/${skinPrefix}s_$frame.webp"

    /** Fracción opaca PRECALCULADA de esta acción (respaldo: walkBodyFraction si es <=0f). */
    fun bodyFraction(action: PlayerAction): Float = when (action) {
        PlayerAction.IDLE    -> idleBodyFraction
        PlayerAction.WALK    -> walkBodyFraction
        PlayerAction.RUN     -> runBodyFraction
        PlayerAction.SPECIAL -> specialBodyFraction
    }.let { if (it > 0f) it else walkBodyFraction }

    companion object {
        /**
         * Estándar ÚNICO de tamaño del jugador a pie (mapa exterior): alto en pantalla, en dp, que
         * debe ocupar el CUERPO (parte opaca) del personaje, IGUAL para TODAS las skins y TODAS las
         * animaciones. Referencia = hombre/robot (coincide con la normalización del interior, ver 09 §5).
         * Sube/baja este único valor para agrandar/encoger a TODOS por igual.
         */
        const val PLAYER_BODY_STANDARD_DP = 23.5f
    }
}
